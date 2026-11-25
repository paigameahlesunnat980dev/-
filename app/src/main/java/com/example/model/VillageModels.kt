package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class GraphicsQuality(
    val label: String,
    val resolutionScale: Float,
    val maxTextureSize: Int,
    val shadowDetail: Int,
    val lodDistance: Float
) {
    LOW("LOW", 0.65f, 512, 0, 55f),
    MEDIUM("MEDIUM", 0.80f, 1024, 1, 85f),
    HIGH("HIGH", 0.92f, 2048, 2, 125f),
    ULTRA("ULTRA", 1.0f, 4096, 3, 180f);

    companion object {
        fun fromRaw(raw: String?): GraphicsQuality =
            entries.firstOrNull { it.name == raw } ?: HIGH
    }
}

enum class WeatherMode(val label: String) {
    SUNNY("Clear Sunny"),
    GOLDEN_HOUR("Golden Hour"),
    NIGHT("Starry Night"),
    MONSOON("Monsoon Rain");

    companion object {
        fun fromRaw(raw: String?): WeatherMode =
            entries.firstOrNull { it.name == raw } ?: GOLDEN_HOUR
    }
}

enum class CharacterPreset(
    val label: String,
    val subtitle: String,
    val torsoHex: String,
    val legsHex: String
) {
    EXPLORER("Village Explorer", "Agile khaki & amber jacket", "#D97706", "#2B3A35"),
    RANGER("Forest Ranger", "Emerald tactical vest & boots", "#10B981", "#1F2937"),
    FESTIVE("Festive Kurta", "Warm saffron & ivory attire", "#F59E0B", "#E5E7EB"),
    TACTICAL("Night Runner", "Stealth slate & crimson trim", "#334155", "#0F172A");

    companion object {
        fun fromRaw(raw: String?): CharacterPreset =
            entries.firstOrNull { it.name == raw } ?: EXPLORER
    }
}

enum class LocationCategory(val label: String) {
    HOUSE("House"),
    ROAD("Village Road"),
    SHOP("Shop / Market"),
    FIELD("Field / Open Space"),
    LANDMARK("Landmark");

    companion object {
        fun fromRaw(raw: String?): LocationCategory =
            entries.firstOrNull { it.name == raw } ?: LANDMARK
    }
}

enum class MediaType(val label: String) {
    PANORAMA_360("360° Photo"),
    MULTI_PHOTO("Multi-Angle 3D"),
    PANORAMA_VIDEO("Panoramic Video"),
    SAMPLE_360("Starter 360°");

    companion object {
        fun fromRaw(raw: String?): MediaType =
            entries.firstOrNull { it.name == raw } ?: PANORAMA_360
    }
}

@Keep
@IgnoreExtraProperties
data class UserSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Murgi Tola Explorer",
    val characterPreset: String = CharacterPreset.EXPLORER.name,
    val characterAccentHex: String = "#FFB020",
    val walkSpeedMultiplier: Double = 1.0,
    val cameraSensitivity: Double = 1.0,
    val invertCameraY: Boolean = false,
    val graphicsQuality: String = GraphicsQuality.HIGH.name,
    val autoGraphics: Boolean = true,
    val lodEnabled: Boolean = true,
    val occlusionCulling: Boolean = true,
    val dynamicResolution: Boolean = true,
    val soundEnabled: Boolean = true,
    val weatherMode: String = WeatherMode.GOLDEN_HOUR.name,
    val timeOfDay: Double = 0.72,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VillageProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "Murgi Tola",
    val description: String = "My scanned 3D Murgi Tola village world",
    val lastLocationId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class VillageLocation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val villageId: String = "",
    val name: String = "",
    val category: String = LocationCategory.LANDMARK.name,
    val notes: String = "",
    val markedAsHouse: Boolean = false,
    val favorite: Boolean = false,
    val explored: Boolean = false,
    val panoramaOnly: Boolean = true,
    val posX: Double = 0.0,
    val posZ: Double = 0.0,
    val connectedIds: List<String> = emptyList(),
    val mediaType: String = MediaType.PANORAMA_360.name,
    val localImagePath: String = "",
    val aiSummary: String = "",
    val aiBuildingsCount: Long = 0L,
    val aiTreesCount: Long = 0L,
    val aiPolesCount: Long = 0L,
    val aiRoadSurface: String = "",
    val aiLighting: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class SceneReconstructionResult(
    val summary: String,
    val buildingsCount: Int,
    val treesCount: Int,
    val polesCount: Int,
    val roadSurface: String,
    val lightingCondition: String,
    val recommendedPanoramaOnly: Boolean,
    val suggestedCategory: LocationCategory
)
