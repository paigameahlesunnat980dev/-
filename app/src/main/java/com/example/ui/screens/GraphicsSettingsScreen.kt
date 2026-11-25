package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.GraphicsQuality
import com.example.model.UserSettings
import com.example.model.WeatherMode
import com.example.ui.theme.AmberGold
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GraphicsSettingsScreen(
    settings: UserSettings,
    onUpdateSettings: (UserSettings, String?) -> Unit,
    onBack: () -> Unit
) {
    val activeQuality = GraphicsQuality.fromRaw(settings.graphicsQuality)
    val activeWeather = WeatherMode.fromRaw(settings.weatherMode)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("graphics_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "GRAPHICS SETTINGS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AmberGold
                )
                Text(
                    text = "Optimize 3D rendering, LOD, culling & atmospheric lighting",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Graphics Presets
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("QUALITY PRESET", style = MaterialTheme.typography.labelLarge, color = AmberGold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GraphicsQuality.entries.forEach { preset ->
                                val selected = activeQuality == preset
                                Surface(
                                    color = if (selected) AmberGold else ObsidianBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            1.dp,
                                            if (selected) AmberGold else ObsidianBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            onUpdateSettings(
                                                settings.copy(graphicsQuality = preset.name),
                                                "Graphics set to ${preset.label}"
                                            )
                                        }
                                        .testTag("preset_${preset.name.lowercase()}")
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (selected) ObsidianBg else TextPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "Resolution Scale: ${(activeQuality.resolutionScale * 100).toInt()}% • Max Texture: ${activeQuality.maxTextureSize}px • Draw Dist: ${activeQuality.lodDistance.toInt()}m",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldVibrant
                        )
                    }
                }
            }

            // Engine Optimization Switches
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("ANDROID ENGINE OPTIMIZATIONS", style = MaterialTheme.typography.labelLarge, color = AmberGold)

                        SettingSwitchRow(
                            title = "Automatic Graphics Adjustment",
                            subtitle = "Balance GPU thermal load and frame pacing",
                            checked = settings.autoGraphics,
                            onCheckedChange = { onUpdateSettings(settings.copy(autoGraphics = it), null) }
                        )
                        SettingSwitchRow(
                            title = "LOD System (Level of Detail)",
                            subtitle = "Simplify distant village buildings & trees",
                            checked = settings.lodEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(lodEnabled = it), null) }
                        )
                        SettingSwitchRow(
                            title = "Occlusion & Frustum Culling",
                            subtitle = "Skip rendering objects behind camera",
                            checked = settings.occlusionCulling,
                            onCheckedChange = { onUpdateSettings(settings.copy(occlusionCulling = it), null) }
                        )
                        SettingSwitchRow(
                            title = "Dynamic Resolution Scaling",
                            subtitle = "Scale OpenGL ES viewport for smooth FPS",
                            checked = settings.dynamicResolution,
                            onCheckedChange = { onUpdateSettings(settings.copy(dynamicResolution = it), null) }
                        )
                        SettingSwitchRow(
                            title = "Village Ambient Sound",
                            subtitle = "Birds, wind, rain, crickets & footsteps",
                            checked = settings.soundEnabled,
                            onCheckedChange = { onUpdateSettings(settings.copy(soundEnabled = it), null) }
                        )
                    }
                }
            }

            // Day/Night Cycle & Cinematic Weather
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("DAY / NIGHT CYCLE & CINEMATIC WEATHER", style = MaterialTheme.typography.labelLarge, color = AmberGold)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WeatherMode.entries.forEach { w ->
                                FilterChip(
                                    selected = activeWeather == w,
                                    onClick = {
                                        onUpdateSettings(settings.copy(weatherMode = w.name), "Weather: ${w.label}")
                                    },
                                    label = { Text(w.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AmberGold,
                                        selectedLabelColor = ObsidianBg
                                    )
                                )
                            }
                        }

                        Text(
                            text = "Sun Position / Time of Day (${(settings.timeOfDay * 24).toInt()}:00)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Slider(
                            value = settings.timeOfDay.toFloat(),
                            onValueChange = { v ->
                                onUpdateSettings(settings.copy(timeOfDay = v.toDouble()), null)
                            },
                            valueRange = 0.0f..1.0f
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
