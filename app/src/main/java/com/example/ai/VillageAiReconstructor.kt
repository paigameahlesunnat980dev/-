package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.model.LocationCategory
import com.example.model.SceneReconstructionResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "VillageAiReconstructor"

class VillageAiReconstructor {

    private val sceneSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(
                description = "Factual 1-2 sentence description of only what is visibly seen in the village photograph(s), without inventing unseen buildings."
            ),
            "buildingsCount" to Schema.integer(
                description = "Number of distinct houses or shops clearly visible in the photo(s), 0 to 12."
            ),
            "treesCount" to Schema.integer(
                description = "Number of distinct trees or palms clearly visible, 0 to 20."
            ),
            "polesCount" to Schema.integer(
                description = "Number of electric poles or street lights clearly visible, 0 to 12."
            ),
            "roadSurface" to Schema.string(
                description = "Short description of the ground/road visible (e.g., dirt lane, paved road, brick path, courtyard)."
            ),
            "lightingCondition" to Schema.string(
                description = "Short description of lighting (e.g., bright midday sun, golden hour, dusk, indoor/shaded)."
            ),
            "recommendedPanoramaOnly" to Schema.boolean(
                description = "True if only a single viewpoint is provided or geometry is ambiguous; false if multi-angle cues clearly define a walkable 3D block."
            ),
            "suggestedCategory" to Schema.enumeration(
                values = listOf("HOUSE", "ROAD", "SHOP", "FIELD", "LANDMARK"),
                description = "Best matching category for the photographed place."
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = sceneSchema
            }
        )
    }

    suspend fun analyzeVillagePhotos(
        locationName: String,
        bitmaps: List<Bitmap>,
        isPanoramaPhoto: Boolean
    ): Result<SceneReconstructionResult> {
        if (bitmaps.isEmpty()) {
            return Result.failure(IllegalArgumentException("At least one photo is required for AI scene analysis."))
        }
        return try {
            val scaledBitmaps = bitmaps.take(4).map { downscaleForAi(it, maxDim = 1024) }
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        text(
                            "You are a 3D photogrammetry & depth-estimation assistant for the village app 'Murgi Tola'. " +
                                "Analyze ONLY what is genuinely visible in the provided photograph(s) for location '$locationName'. " +
                                "Do NOT invent buildings, landmarks, or street names that are not visible in the image(s). " +
                                "Photo count: ${scaledBitmaps.size}. Single 360 mode hint: $isPanoramaPhoto."
                        )
                        scaledBitmaps.forEach { bmp ->
                            image(bmp)
                        }
                    }
                )
            }
            val rawJson = response.text
                ?: return Result.failure(IllegalStateException("AI returned an empty reconstruction response."))
            val json = JSONObject(rawJson)
            val result = SceneReconstructionResult(
                summary = json.optString("summary", "Captured village scene ready for exploration.")
                    .take(900),
                buildingsCount = json.optInt("buildingsCount", 1).coerceIn(0, 50),
                treesCount = json.optInt("treesCount", 2).coerceIn(0, 100),
                polesCount = json.optInt("polesCount", 1).coerceIn(0, 50),
                roadSurface = json.optString("roadSurface", "Village lane").take(90),
                lightingCondition = json.optString("lightingCondition", "Natural daylight").take(90),
                recommendedPanoramaOnly = json.optBoolean("recommendedPanoramaOnly", scaledBitmaps.size <= 1),
                suggestedCategory = LocationCategory.fromRaw(json.optString("suggestedCategory"))
            )
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI scene analysis timed out", e)
            Result.failure(Exception("AI scene analysis timed out after 60 seconds. You can retry or save as 360° panorama.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI scene analysis failed", e)
            Result.failure(e)
        }
    }

    private fun downscaleForAi(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = if (w >= h) maxDim.toFloat() / w else maxDim.toFloat() / h
        val targetW = (w * ratio).toInt().coerceAtLeast(1)
        val targetH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
