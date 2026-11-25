package com.example.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.VillageAiReconstructor
import com.example.data.VillageRepository
import com.example.model.GraphicsQuality
import com.example.model.LocationCategory
import com.example.model.MediaType
import com.example.model.SceneReconstructionResult
import com.example.model.UiState
import com.example.model.UserSettings
import com.example.model.VillageLocation
import com.example.model.VillageProject
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "MurgiTolaVM"

data class ScannerUiState(
    val selectedBitmaps: List<Bitmap> = emptyList(),
    val mediaType: MediaType = MediaType.PANORAMA_360,
    val isAnalyzing: Boolean = false,
    val isSaving: Boolean = false,
    val aiResult: SceneReconstructionResult? = null,
    val errorMessage: String? = null,
    val savedSuccessName: String? = null
)

class MurgiTolaViewModel(
    private val repository: VillageRepository,
    private val aiReconstructor: VillageAiReconstructor,
    private val currentUserId: String,
    private val userDisplayName: String
) : ViewModel() {

    private val _activeVillageId = MutableStateFlow("murgi_tola_main")
    val activeVillageId: StateFlow<String> = _activeVillageId.asStateFlow()

    private val _selectedExploreLocationId = MutableStateFlow<String?>(null)
    val selectedExploreLocationId: StateFlow<String?> = _selectedExploreLocationId.asStateFlow()

    private val _scannerState = MutableStateFlow(ScannerUiState())
    val scannerState: StateFlow<ScannerUiState> = _scannerState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val userSettingsState: StateFlow<UiState<UserSettings>> = repository.observeUserSettings(currentUserId)
        .map<UserSettings, UiState<UserSettings>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing user settings", root)
            emit(UiState.Error(root.message ?: "Failed to load user settings"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val villagesState: StateFlow<UiState<List<VillageProject>>> = repository.observeVillages(currentUserId)
        .map<List<VillageProject>, UiState<List<VillageProject>>> { list ->
            if (list.isNotEmpty() && list.none { it.id == _activeVillageId.value }) {
                _activeVillageId.value = list.first().id
            }
            UiState.Success(list)
        }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing villages", root)
            emit(UiState.Error(root.message ?: "Failed to load village projects"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val allLocationsState: StateFlow<UiState<List<VillageLocation>>> = repository.observeLocations(currentUserId)
        .map<List<VillageLocation>, UiState<List<VillageLocation>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing locations", root)
            emit(UiState.Error(root.message ?: "Failed to load scanned locations"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val activeLocations: StateFlow<List<VillageLocation>> = combine(
        allLocationsState,
        _activeVillageId
    ) { locState, villageId ->
        when (locState) {
            is UiState.Success -> {
                val filtered = locState.data.filter { it.villageId == villageId }
                filtered.ifEmpty { locState.data }
            }
            else -> emptyList()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            val res = repository.ensureBootstrapData(currentUserId, userDisplayName)
            if (res.isFailure) {
                _actionMessage.value = res.exceptionOrNull()?.localizedMessage
                    ?: "Failed to initialize village profile"
            }
        }
        viewModelScope.launch {
            combine(villagesState, allLocationsState) { vState, lState ->
                Pair(vState, lState)
            }.collect { (vState, lState) ->
                if (vState is UiState.Success && lState is UiState.Success) {
                    repository.exportLocalBackupSnapshot(vState.data, lState.data)
                }
            }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    fun selectVillage(villageId: String) {
        _activeVillageId.value = villageId
        _actionMessage.value = "Opened village project"
    }

    fun setExploreLocation(locationId: String?) {
        _selectedExploreLocationId.value = locationId
    }

    fun createVillage(name: String, description: String) {
        viewModelScope.launch {
            val res = repository.createVillageProject(name, description)
            res.onSuccess { id ->
                _activeVillageId.value = id
                _actionMessage.value = "Created village '$name'"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Could not create village"
            }
        }
    }

    fun renameVillage(projectId: String, name: String, description: String) {
        viewModelScope.launch {
            val res = repository.updateVillageProject(projectId, name, description)
            res.onSuccess {
                _actionMessage.value = "Updated village '$name'"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Could not update village"
            }
        }
    }

    fun updateSettings(settings: UserSettings, toastText: String? = null) {
        viewModelScope.launch {
            val res = repository.saveUserSettings(settings)
            res.onSuccess {
                if (toastText != null) _actionMessage.value = toastText
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Could not save settings"
            }
        }
    }

    fun onPhotoUrisSelected(uris: List<Uri>, mediaType: MediaType, maxDim: Int) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val bitmaps = if (mediaType == MediaType.PANORAMA_VIDEO) {
                repository.extractVideoFrames(uris.first(), count = 3)
            } else {
                uris.take(4).mapNotNull { repository.decodeUriBitmap(it, maxDim) }
            }
            if (bitmaps.isEmpty()) {
                _scannerState.value = _scannerState.value.copy(
                    errorMessage = "Could not decode selected media. Please pick a standard JPEG/PNG photo or MP4 video."
                )
            } else {
                _scannerState.value = _scannerState.value.copy(
                    selectedBitmaps = bitmaps,
                    mediaType = mediaType,
                    aiResult = null,
                    errorMessage = null,
                    savedSuccessName = null
                )
            }
        }
    }

    fun onCapturedCameraBitmap(bitmap: Bitmap) {
        val current = _scannerState.value.selectedBitmaps.toMutableList()
        current.add(bitmap)
        val trimmed = current.takeLast(4)
        _scannerState.value = _scannerState.value.copy(
            selectedBitmaps = trimmed,
            mediaType = if (trimmed.size > 1) MediaType.MULTI_PHOTO else MediaType.PANORAMA_360,
            aiResult = null,
            errorMessage = null,
            savedSuccessName = null
        )
    }

    fun clearScannerMedia() {
        _scannerState.value = ScannerUiState()
    }

    fun runAiSceneReconstruction(locationName: String) {
        val state = _scannerState.value
        if (state.selectedBitmaps.isEmpty()) {
            _scannerState.value = state.copy(errorMessage = "Capture or upload at least 1 photo first.")
            return
        }
        viewModelScope.launch {
            _scannerState.value = _scannerState.value.copy(isAnalyzing = true, errorMessage = null)
            val isPano = state.mediaType == MediaType.PANORAMA_360 || state.selectedBitmaps.size == 1
            val res = aiReconstructor.analyzeVillagePhotos(
                locationName = locationName.ifBlank { "Murgi Tola Spot" },
                bitmaps = state.selectedBitmaps,
                isPanoramaPhoto = isPano
            )
            res.onSuccess { ai ->
                _scannerState.value = _scannerState.value.copy(
                    isAnalyzing = false,
                    aiResult = ai,
                    errorMessage = null
                )
            }.onFailure { err ->
                _scannerState.value = _scannerState.value.copy(
                    isAnalyzing = false,
                    errorMessage = err.localizedMessage ?: "AI photogrammetry analysis failed."
                )
            }
        }
    }

    fun saveScannedLocation(
        name: String,
        category: LocationCategory,
        notes: String,
        markedAsHouse: Boolean,
        favorite: Boolean,
        panoramaOnly: Boolean,
        connectToLocationId: String?
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _scannerState.value = _scannerState.value.copy(errorMessage = "Please enter a location name.")
            return
        }
        val state = _scannerState.value
        if (state.selectedBitmaps.isEmpty()) {
            _scannerState.value = state.copy(errorMessage = "Please capture or upload a 360° photo or village photos first.")
            return
        }

        viewModelScope.launch {
            _scannerState.value = _scannerState.value.copy(isSaving = true, errorMessage = null)
            try {
                val maxTex = (userSettingsState.value as? UiState.Success)?.data?.graphicsQuality
                    ?.let { GraphicsQuality.fromRaw(it).maxTextureSize } ?: 2048
                val localPath = repository.persistBitmapToLocalStorage(state.selectedBitmaps.first(), maxTex.coerceAtMost(2048))

                // Compute clean non-overlapping coordinate along expanding Murgi Tola village layout
                val existing = activeLocations.value
                val idx = existing.size
                val radius = 16.0 + (idx * 9.0)
                val angle = idx * 1.15
                val posX = (cos(angle) * radius).let { kotlin.math.round(it * 10.0) / 10.0 }
                val posZ = (sin(angle) * radius).let { kotlin.math.round(it * 10.0) / 10.0 }

                val ai = state.aiResult
                val links = if (!connectToLocationId.isNullOrBlank()) listOf(connectToLocationId) else emptyList()

                val newLoc = VillageLocation(
                    userId = currentUserId,
                    villageId = _activeVillageId.value,
                    name = cleanName,
                    category = category.name,
                    notes = notes,
                    markedAsHouse = markedAsHouse,
                    favorite = favorite,
                    explored = true,
                    panoramaOnly = panoramaOnly,
                    posX = posX,
                    posZ = posZ,
                    connectedIds = links,
                    mediaType = state.mediaType.name,
                    localImagePath = localPath,
                    aiSummary = ai?.summary ?: "Scanned 360° location in Murgi Tola.",
                    aiBuildingsCount = (ai?.buildingsCount ?: 1).toLong(),
                    aiTreesCount = (ai?.treesCount ?: 2).toLong(),
                    aiPolesCount = (ai?.polesCount ?: 1).toLong(),
                    aiRoadSurface = ai?.roadSurface ?: "Village road",
                    aiLighting = ai?.lightingCondition ?: "Natural light"
                )
                val saveRes = repository.saveLocation(newLoc)
                saveRes.onSuccess { newId ->
                    // Also add reverse link on connected location so both ends connect in 3D & Map
                    if (!connectToLocationId.isNullOrBlank()) {
                        val target = existing.firstOrNull { it.id == connectToLocationId }
                        if (target != null) {
                            repository.updateLocationFlags(
                                locationId = target.id,
                                connectedIds = (target.connectedIds + newId).distinct()
                            )
                        }
                    }
                    _scannerState.value = ScannerUiState(savedSuccessName = cleanName)
                    _actionMessage.value = "Saved '$cleanName' to Murgi Tola!"
                }.onFailure { err ->
                    _scannerState.value = _scannerState.value.copy(
                        isSaving = false,
                        errorMessage = err.localizedMessage ?: "Failed to save scanned location."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _scannerState.value = _scannerState.value.copy(
                    isSaving = false,
                    errorMessage = e.localizedMessage ?: "Failed to save scanned media."
                )
            }
        }
    }

    fun toggleHouseMark(location: VillageLocation) {
        viewModelScope.launch {
            val res = repository.updateLocationFlags(
                locationId = location.id,
                markedAsHouse = !location.markedAsHouse
            )
            res.onSuccess {
                _actionMessage.value = if (!location.markedAsHouse) {
                    "Marked '${location.name}' as My House ❤️"
                } else {
                    "Unmarked '${location.name}'"
                }
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Could not update location"
            }
        }
    }

    fun toggleFavorite(location: VillageLocation) {
        viewModelScope.launch {
            val res = repository.updateLocationFlags(
                locationId = location.id,
                favorite = !location.favorite
            )
            res.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Could not update favorite"
            }
        }
    }

    fun markExplored(locationId: String) {
        viewModelScope.launch {
            repository.updateLocationFlags(locationId = locationId, explored = true)
        }
    }

    fun connectLocations(locA: VillageLocation, locB: VillageLocation) {
        if (locA.id == locB.id) return
        viewModelScope.launch {
            val resA = repository.updateLocationFlags(
                locationId = locA.id,
                connectedIds = (locA.connectedIds + locB.id).distinct()
            )
            val resB = repository.updateLocationFlags(
                locationId = locB.id,
                connectedIds = (locB.connectedIds + locA.id).distinct()
            )
            if (resA.isSuccess && resB.isSuccess) {
                _actionMessage.value = "Connected '${locA.name}' ↔ '${locB.name}'"
            } else {
                _actionMessage.value = "Could not connect locations"
            }
        }
    }

    fun deleteLocation(locationId: String) {
        viewModelScope.launch {
            val res = repository.deleteLocation(locationId)
            res.onSuccess {
                _actionMessage.value = "Removed location"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to delete location"
            }
        }
    }
}
