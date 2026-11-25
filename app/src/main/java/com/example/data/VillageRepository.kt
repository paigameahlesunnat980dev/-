package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.CharacterPreset
import com.example.model.GraphicsQuality
import com.example.model.LocationCategory
import com.example.model.MediaType
import com.example.model.UserSettings
import com.example.model.VillageLocation
import com.example.model.VillageProject
import com.example.model.WeatherMode
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val TAG = "VillageRepository"

class VillageRepository(
    private val db: FirebaseFirestore,
    private val appContext: Context,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        appContext = context.applicationContext
    )

    private val scansDir: File by lazy {
        File(appContext.filesDir, "murgi_tola_scans").apply { mkdirs() }
    }

    private val localProjectsFile: File by lazy {
        File(appContext.filesDir, "murgi_tola_local_backup.json")
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeUserSettings(userId: String): Flow<UserSettings> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObject(
                    UserSettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserSettings(
                    userId = userId,
                    displayName = auth.currentUser?.displayName?.take(50) ?: "Murgi Tola Explorer"
                )
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val toLog = fsErr ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeVillages(userId: String): Flow<List<VillageProject>> {
        val path = "users/$userId/villages"
        return db.collection("users").document(userId).collection("villages")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    VillageProject::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val toLog = fsErr ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeLocations(userId: String): Flow<List<VillageLocation>> {
        val path = "users/$userId/locations"
        return db.collection("users").document(userId).collection("locations")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    VillageLocation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val toLog = fsErr ?: (error as? Exception)
                if (toLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(toLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun ensureBootstrapData(userId: String, displayName: String): Result<Unit> {
        var docPath = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user mismatch" }

            // 1. Ensure user settings doc exists
            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            if (!userSnap.exists()) {
                val safeName = displayName.trim().ifBlank { "Murgi Tola Explorer" }.take(60)
                val settingsPayload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "characterPreset" to CharacterPreset.EXPLORER.name,
                    "characterAccentHex" to "#FFB020",
                    "walkSpeedMultiplier" to 1.0,
                    "cameraSensitivity" to 1.0,
                    "invertCameraY" to false,
                    "graphicsQuality" to GraphicsQuality.HIGH.name,
                    "autoGraphics" to true,
                    "lodEnabled" to true,
                    "occlusionCulling" to true,
                    "dynamicResolution" to true,
                    "soundEnabled" to true,
                    "weatherMode" to WeatherMode.GOLDEN_HOUR.name,
                    "timeOfDay" to 0.72,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(settingsPayload).await()
            }

            // 2. Ensure default "Murgi Tola" village project exists
            val villagesCol = userRef.collection("villages")
            val villagesSnap = villagesCol.get().await()
            if (villagesSnap.isEmpty) {
                val defaultVillageId = "murgi_tola_main"
                docPath = "users/$uid/villages/$defaultVillageId"
                villagesCol.document(defaultVillageId).set(
                    mapOf(
                        "userId" to uid,
                        "name" to "Murgi Tola",
                        "description" to "Real-world 360° & 3D village explorer project",
                        "lastLocationId" to "starter_entrance",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                // Starter 360 sample portal clearly labeled as a starter sample until user scans real spots
                docPath = "users/$uid/locations/starter_entrance"
                userRef.collection("locations").document("starter_entrance").set(
                    mapOf(
                        "userId" to uid,
                        "villageId" to defaultVillageId,
                        "name" to "Village Entrance (Sample 360°)",
                        "category" to LocationCategory.ROAD.name,
                        "notes" to "Starter sample panorama. Use Scan 360° Location to replace or connect real photos of Murgi Tola.",
                        "markedAsHouse" to false,
                        "favorite" to false,
                        "explored" to true,
                        "panoramaOnly" to true,
                        "posX" to 0.0,
                        "posZ" to 0.0,
                        "connectedIds" to emptyList<String>(),
                        "mediaType" to MediaType.SAMPLE_360.name,
                        "localImagePath" to "",
                        "aiSummary" to "Starter panoramic demo node. Scan real Murgi Tola photos to reconstruct your actual village.",
                        "aiBuildingsCount" to 0L,
                        "aiTreesCount" to 2L,
                        "aiPolesCount" to 1L,
                        "aiRoadSurface" to "Starter village lane",
                        "aiLighting" to "Golden daylight",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveUserSettings(settings: UserSettings): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid"
        return try {
            val safeName = settings.displayName.trim().ifBlank { "Explorer" }.take(60)
            val updates = mapOf(
                "displayName" to safeName,
                "characterPreset" to CharacterPreset.fromRaw(settings.characterPreset).name,
                "characterAccentHex" to settings.characterAccentHex.take(16),
                "walkSpeedMultiplier" to settings.walkSpeedMultiplier.coerceIn(0.5, 2.5),
                "cameraSensitivity" to settings.cameraSensitivity.coerceIn(0.2, 3.0),
                "invertCameraY" to settings.invertCameraY,
                "graphicsQuality" to GraphicsQuality.fromRaw(settings.graphicsQuality).name,
                "autoGraphics" to settings.autoGraphics,
                "lodEnabled" to settings.lodEnabled,
                "occlusionCulling" to settings.occlusionCulling,
                "dynamicResolution" to settings.dynamicResolution,
                "soundEnabled" to settings.soundEnabled,
                "weatherMode" to WeatherMode.fromRaw(settings.weatherMode).name,
                "timeOfDay" to settings.timeOfDay.coerceIn(0.0, 1.0),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createVillageProject(name: String, description: String): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanName = name.trim().take(80)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Village name cannot be empty"))
        }
        val cleanDesc = description.trim().take(400)
        val projectId = "v_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docPath = "users/$uid/villages/$projectId"
        return try {
            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "description" to cleanDesc,
                "lastLocationId" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("villages")
                .document(projectId).set(payload).await()
            Result.success(projectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateVillageProject(projectId: String, name: String, description: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanName = name.trim().take(80)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Village name cannot be empty"))
        }
        val docPath = "users/$uid/villages/$projectId"
        return try {
            db.collection("users").document(uid).collection("villages")
                .document(projectId)
                .update(
                    mapOf(
                        "name" to cleanName,
                        "description" to description.trim().take(400),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveLocation(location: VillageLocation): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanName = location.name.trim().take(80)
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Location name is required"))
        }
        val locationId = location.id.ifBlank {
            "loc_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        }
        val docPath = "users/$uid/locations/$locationId"
        return try {
            val payload = mapOf(
                "userId" to uid,
                "villageId" to location.villageId.ifBlank { "murgi_tola_main" }.take(128),
                "name" to cleanName,
                "category" to LocationCategory.fromRaw(location.category).name,
                "notes" to location.notes.trim().take(500),
                "markedAsHouse" to location.markedAsHouse,
                "favorite" to location.favorite,
                "explored" to location.explored,
                "panoramaOnly" to location.panoramaOnly,
                "posX" to location.posX.coerceIn(-1900.0, 1900.0),
                "posZ" to location.posZ.coerceIn(-1900.0, 1900.0),
                "connectedIds" to location.connectedIds.take(20),
                "mediaType" to MediaType.fromRaw(location.mediaType).name,
                "localImagePath" to location.localImagePath.take(512),
                "aiSummary" to location.aiSummary.take(1000),
                "aiBuildingsCount" to location.aiBuildingsCount.coerceIn(0L, 50L),
                "aiTreesCount" to location.aiTreesCount.coerceIn(0L, 100L),
                "aiPolesCount" to location.aiPolesCount.coerceIn(0L, 50L),
                "aiRoadSurface" to location.aiRoadSurface.take(100),
                "aiLighting" to location.aiLighting.take(100),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).collection("locations")
                .document(locationId).set(payload).await()
            Result.success(locationId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateLocationFlags(
        locationId: String,
        markedAsHouse: Boolean? = null,
        favorite: Boolean? = null,
        explored: Boolean? = null,
        connectedIds: List<String>? = null
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid/locations/$locationId"
        return try {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (markedAsHouse != null) updates["markedAsHouse"] = markedAsHouse
            if (favorite != null) updates["favorite"] = favorite
            if (explored != null) updates["explored"] = explored
            if (connectedIds != null) updates["connectedIds"] = connectedIds.distinct().take(20)
            db.collection("users").document(uid).collection("locations")
                .document(locationId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteLocation(locationId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val docPath = "users/$uid/locations/$locationId"
        return try {
            db.collection("users").document(uid).collection("locations")
                .document(locationId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun persistBitmapToLocalStorage(bitmap: Bitmap, maxDim: Int = 2048): String =
        withContext(Dispatchers.IO) {
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val ratio = if (bitmap.width >= bitmap.height) {
                    maxDim.toFloat() / bitmap.width
                } else {
                    maxDim.toFloat() / bitmap.height
                }
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val file = File(scansDir, "scan_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file.absolutePath
        }

    suspend fun decodeUriBitmap(uri: Uri, maxDim: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    val original = BitmapFactory.decodeStream(stream) ?: return@use null
                    if (original.width <= maxDim && original.height <= maxDim) {
                        original
                    } else {
                        val ratio = if (original.width >= original.height) {
                            maxDim.toFloat() / original.width
                        } else {
                            maxDim.toFloat() / original.height
                        }
                        Bitmap.createScaledBitmap(
                            original,
                            (original.width * ratio).toInt().coerceAtLeast(1),
                            (original.height * ratio).toInt().coerceAtLeast(1),
                            true
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode URI bitmap: $uri", e)
                null
            }
        }

    suspend fun extractVideoFrames(uri: Uri, count: Int = 3): List<Bitmap> =
        withContext(Dispatchers.IO) {
            val frames = mutableListOf<Bitmap>()
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(appContext, uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 3000L
                val step = (durationMs / (count + 1)).coerceAtLeast(300L)
                for (i in 1..count) {
                    val timeUs = (i * step).coerceAtMost(durationMs) * 1000L
                    val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (frame != null) frames.add(frame)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract video frames", e)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
            frames
        }

    suspend fun exportLocalBackupSnapshot(
        projects: List<VillageProject>,
        locations: List<VillageLocation>
    ) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject().apply {
                put("savedAtMillis", System.currentTimeMillis())
                put("projects", JSONArray().apply {
                    projects.forEach { p ->
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("description", p.description)
                        })
                    }
                })
                put("locations", JSONArray().apply {
                    locations.forEach { loc ->
                        put(JSONObject().apply {
                            put("id", loc.id)
                            put("villageId", loc.villageId)
                            put("name", loc.name)
                            put("category", loc.category)
                            put("posX", loc.posX)
                            put("posZ", loc.posZ)
                            put("markedAsHouse", loc.markedAsHouse)
                            put("favorite", loc.favorite)
                            put("localImagePath", loc.localImagePath)
                        })
                    }
                })
            }
            localProjectsFile.writeText(root.toString(2))
        } catch (e: Exception) {
            Log.w(TAG, "Local backup write skipped: ${e.message}")
        }
    }
}
