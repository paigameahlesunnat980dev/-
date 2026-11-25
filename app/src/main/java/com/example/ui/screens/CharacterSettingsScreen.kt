package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.ExplorerRenderMode
import com.example.engine.VillageGLSurfaceView
import com.example.model.CharacterPreset
import com.example.model.UserSettings
import com.example.ui.theme.AmberGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CharacterSettingsScreen(
    settings: UserSettings,
    onUpdateSettings: (UserSettings, String?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activePreset = CharacterPreset.fromRaw(settings.characterPreset)
    var explorerName by remember(settings.displayName) { mutableStateOf(settings.displayName) }

    val previewGlView = remember {
        VillageGLSurfaceView(context) { _ -> }.apply {
            renderer.mode = ExplorerRenderMode.CHARACTER_PREVIEW
        }
    }

    LaunchedEffect(settings) {
        previewGlView.renderer.characterPreset = CharacterPreset.fromRaw(settings.characterPreset)
        previewGlView.renderer.characterAccentHex = settings.characterAccentHex
    }

    DisposableEffect(Unit) {
        previewGlView.onResume()
        onDispose { previewGlView.onPause() }
    }

    val accentSwatches = listOf("#FFB020", "#10B981", "#FF3B5C", "#38BDF8", "#F472B6")

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
                    .testTag("character_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "CHARACTER SETTINGS",
                    style = MaterialTheme.typography.headlineMedium,
                    color = AmberGold
                )
                Text(
                    text = "Live 3D third-person avatar & camera controls",
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
            // Live 3D Rotating Character Preview
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, ObsidianBorder, RoundedCornerShape(20.dp))
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AndroidView(
                            factory = { previewGlView },
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = ObsidianBg.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "${activePreset.label} • ${activePreset.subtitle}",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberGold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Explorer Identity & Outfit
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
                        Text("EXPLORER PROFILE & OUTFIT", style = MaterialTheme.typography.labelLarge, color = AmberGold)

                        OutlinedTextField(
                            value = explorerName,
                            onValueChange = {
                                explorerName = it
                                if (it.isNotBlank()) {
                                    onUpdateSettings(settings.copy(displayName = it.take(60)), null)
                                }
                            },
                            label = { Text("Explorer Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CharacterPreset.entries.forEach { preset ->
                                val selected = activePreset == preset
                                Surface(
                                    color = if (selected) AmberGold.copy(alpha = 0.16f) else ObsidianBg,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            1.dp,
                                            if (selected) AmberGold else ObsidianBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            onUpdateSettings(
                                                settings.copy(characterPreset = preset.name),
                                                "Equipped ${preset.label}"
                                            )
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(preset.label, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                            Text(preset.subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(preset.torsoHex)))
                                        )
                                    }
                                }
                            }
                        }

                        Text("Accent Gear Color", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            accentSwatches.forEach { hex ->
                                val selected = settings.characterAccentHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) Color.White else ObsidianBorder,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            onUpdateSettings(settings.copy(characterAccentHex = hex), null)
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // Movement & Camera Tuning
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
                        Text("MOVEMENT & CAMERA CONTROLS", style = MaterialTheme.typography.labelLarge, color = AmberGold)

                        Text(
                            text = "Walk & Sprint Speed: ${String.format("%.1fx", settings.walkSpeedMultiplier)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Slider(
                            value = settings.walkSpeedMultiplier.toFloat(),
                            onValueChange = { v ->
                                onUpdateSettings(settings.copy(walkSpeedMultiplier = v.toDouble()), null)
                            },
                            valueRange = 0.6f..2.2f
                        )

                        Text(
                            text = "360° Camera Sensitivity: ${String.format("%.1fx", settings.cameraSensitivity)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Slider(
                            value = settings.cameraSensitivity.toFloat(),
                            onValueChange = { v ->
                                onUpdateSettings(settings.copy(cameraSensitivity = v.toDouble()), null)
                            },
                            valueRange = 0.4f..2.5f
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Invert Camera Look Y-Axis", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                            Switch(
                                checked = settings.invertCameraY,
                                onCheckedChange = { onUpdateSettings(settings.copy(invertCameraY = it), null) }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
