package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Vrpano
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.audio.VillageSoundEngine
import com.example.engine.EngineTelemetry
import com.example.engine.ExplorerRenderMode
import com.example.engine.VillageGLSurfaceView
import com.example.model.CharacterPreset
import com.example.model.GraphicsQuality
import com.example.model.UserSettings
import com.example.model.VillageLocation
import com.example.model.WeatherMode
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SkyBlueAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ExploreScreen(
    locations: List<VillageLocation>,
    initialLocationId: String?,
    settings: UserSettings,
    onSettingsUpdate: (UserSettings) -> Unit,
    onLocationExplored: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val soundEngine = remember { VillageSoundEngine() }

    var renderMode by remember {
        mutableStateOf(
            if (initialLocationId != null && locations.firstOrNull { it.id == initialLocationId }?.panoramaOnly == true) {
                ExplorerRenderMode.PANORAMA_360
            } else {
                ExplorerRenderMode.WALK_3D
            }
        )
    }
    var activePanoLocationId by remember {
        mutableStateOf(initialLocationId ?: locations.firstOrNull()?.id)
    }
    var telemetry by remember { mutableStateOf(EngineTelemetry()) }
    var isSprinting by remember { mutableStateOf(false) }
    var isMoving by remember { mutableStateOf(false) }

    val activeWeather = WeatherMode.fromRaw(settings.weatherMode)
    val glView = remember {
        VillageGLSurfaceView(context) { newTelemetry ->
            telemetry = newTelemetry
        }
    }

    // Sync GL renderer configuration whenever settings or locations change
    LaunchedEffect(locations, settings, renderMode, activePanoLocationId) {
        val r = glView.renderer
        r.mode = renderMode
        r.graphicsQuality = GraphicsQuality.fromRaw(settings.graphicsQuality)
        r.lodEnabled = settings.lodEnabled
        r.occlusionCulling = settings.occlusionCulling
        r.dynamicResolution = settings.dynamicResolution
        r.weatherMode = WeatherMode.fromRaw(settings.weatherMode)
        r.timeOfDay = settings.timeOfDay.toFloat()
        r.characterPreset = CharacterPreset.fromRaw(settings.characterPreset)
        r.characterAccentHex = settings.characterAccentHex
        r.walkSpeedMultiplier = settings.walkSpeedMultiplier.toFloat()
        r.cameraSensitivity = settings.cameraSensitivity.toFloat()
        r.invertCameraY = settings.invertCameraY
        r.setLocations(locations)

        val panoLoc = locations.firstOrNull { it.id == activePanoLocationId } ?: locations.firstOrNull()
        r.setActivePanorama(panoLoc?.localImagePath)
    }

    LaunchedEffect(initialLocationId) {
        val target = locations.firstOrNull { it.id == initialLocationId }
        if (target != null) {
            glView.renderer.teleportTo(target.posX.toFloat(), target.posZ.toFloat())
            activePanoLocationId = target.id
            if (!target.explored) onLocationExplored(target.id)
        }
    }

    LaunchedEffect(settings.soundEnabled, activeWeather, isMoving, isSprinting) {
        soundEngine.setEnabled(settings.soundEnabled)
        soundEngine.updateState(activeWeather, isMoving, isSprinting)
    }

    DisposableEffect(Unit) {
        glView.onResume()
        onDispose {
            glView.onPause()
            soundEngine.stop()
        }
    }

    val currentPanoLocation = locations.firstOrNull { it.id == activePanoLocationId }
    val connectedLocations = remember(currentPanoLocation, locations) {
        if (currentPanoLocation == null) emptyList()
        else locations.filter { it.id in currentPanoLocation.connectedIds || currentPanoLocation.id in it.connectedIds }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // 1. OpenGL ES 3.0 3D / 360 Surface
        AndroidView(
            factory = { glView },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Full-screen touch camera look layer (right side in 3D walk, whole screen in 360 mode)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(renderMode) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        glView.renderer.adjustCameraLook(dragAmount.x, dragAmount.y)
                    }
                }
        )

        // 3. Top Tactical HUD Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = ObsidianSurface.copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier.border(1.dp, ObsidianBorder, CircleShape)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("explore_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home",
                                tint = TextPrimary
                            )
                        }
                    }

                    Surface(
                        color = ObsidianSurface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text(
                                text = if (renderMode == ExplorerRenderMode.PANORAMA_360) {
                                    "360° VIEW • ${currentPanoLocation?.name ?: "Murgi Tola"}"
                                } else {
                                    "3D WORLD • MURGI TOLA"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = AmberGold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${telemetry.fps} FPS • ${GraphicsQuality.fromRaw(settings.graphicsQuality).label} • ${telemetry.activeDrawCalls} DC",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Sound toggle
                    Surface(
                        color = ObsidianSurface.copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier.border(1.dp, ObsidianBorder, CircleShape)
                    ) {
                        IconButton(
                            onClick = {
                                onSettingsUpdate(settings.copy(soundEnabled = !settings.soundEnabled))
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("explore_sound_toggle")
                        ) {
                            Icon(
                                imageVector = if (settings.soundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = "Toggle village sound",
                                tint = if (settings.soundEnabled) EmeraldVibrant else TextSecondary
                            )
                        }
                    }

                    // Weather / Day-Night cycle switcher
                    Surface(
                        color = ObsidianSurface.copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier.border(1.dp, ObsidianBorder, CircleShape)
                    ) {
                        IconButton(
                            onClick = {
                                val next = when (activeWeather) {
                                    WeatherMode.SUNNY -> WeatherMode.GOLDEN_HOUR
                                    WeatherMode.GOLDEN_HOUR -> WeatherMode.NIGHT
                                    WeatherMode.NIGHT -> WeatherMode.MONSOON
                                    WeatherMode.MONSOON -> WeatherMode.SUNNY
                                }
                                val nextTod = when (next) {
                                    WeatherMode.SUNNY -> 0.50
                                    WeatherMode.GOLDEN_HOUR -> 0.74
                                    WeatherMode.NIGHT -> 0.08
                                    WeatherMode.MONSOON -> 0.42
                                }
                                onSettingsUpdate(settings.copy(weatherMode = next.name, timeOfDay = nextTod))
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("explore_weather_toggle")
                        ) {
                            val wIcon = when (activeWeather) {
                                WeatherMode.SUNNY -> Icons.Filled.WbSunny
                                WeatherMode.GOLDEN_HOUR -> Icons.Filled.WbTwilight
                                WeatherMode.NIGHT -> Icons.Filled.NightsStay
                                WeatherMode.MONSOON -> Icons.Filled.WaterDrop
                            }
                            Icon(
                                imageVector = wIcon,
                                contentDescription = "Cycle weather and day/night",
                                tint = AmberGold
                            )
                        }
                    }
                }
            }

            // Minimap + Quick Location Portals row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (renderMode == ExplorerRenderMode.WALK_3D) {
                    VillageMinimapRadar(
                        locations = locations,
                        playerX = telemetry.playerX,
                        playerZ = telemetry.playerZ,
                        cameraYawDeg = telemetry.cameraYawDeg
                    )
                } else {
                    Spacer(Modifier.width(4.dp))
                }

                // Mode Switch Chip
                Surface(
                    color = ObsidianSurface.copy(alpha = 0.90f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, AmberGold.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .clickable {
                            if (renderMode == ExplorerRenderMode.WALK_3D) {
                                activePanoLocationId = telemetry.nearbyLocationId ?: activePanoLocationId ?: locations.firstOrNull()?.id
                                renderMode = ExplorerRenderMode.PANORAMA_360
                            } else {
                                renderMode = ExplorerRenderMode.WALK_3D
                            }
                        }
                        .testTag("toggle_3d_360_mode")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (renderMode == ExplorerRenderMode.WALK_3D) Icons.Filled.Vrpano else Icons.AutoMirrored.Filled.DirectionsWalk,
                            contentDescription = null,
                            tint = AmberGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (renderMode == ExplorerRenderMode.WALK_3D) "360° VIEW" else "WALK 3D",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // 4. Bottom Gameplay Controls (3D Joystick + Sprint/Jump OR 360 Connected Portals + Zoom)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            if (renderMode == ExplorerRenderMode.WALK_3D) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Left Thumb Virtual Joystick
                    VirtualJoystick(
                        onMove = { jx, jy ->
                            glView.renderer.joystickX = jx
                            glView.renderer.joystickY = jy
                            isMoving = sqrt(jx * jx + jy * jy) > 0.1f
                        }
                    )

                    // Center Portal Enter Prompt if near a scanned spot
                    if (telemetry.nearbyLocationId != null) {
                        Button(
                            onClick = {
                                val nearId = telemetry.nearbyLocationId!!
                                activePanoLocationId = nearId
                                onLocationExplored(nearId)
                                renderMode = ExplorerRenderMode.PANORAMA_360
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmberGold,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .height(48.dp)
                                .testTag("enter_nearby_360_button")
                        ) {
                            Icon(Icons.Filled.Vrpano, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "ENTER ${telemetry.nearbyLocationName?.take(14)?.uppercase() ?: "360°"}",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    // Right Action Buttons: Sprint + Jump
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = if (isSprinting) AmberGold else ObsidianSurface.copy(alpha = 0.88f),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, if (isSprinting) AmberGold else ObsidianBorder, CircleShape)
                                .clickable {
                                    isSprinting = !isSprinting
                                    glView.renderer.isSprinting = isSprinting
                                }
                                .testTag("sprint_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Bolt,
                                    contentDescription = "Toggle sprint",
                                    tint = if (isSprinting) ObsidianBg else AmberGold,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Surface(
                            color = EmeraldVibrant.copy(alpha = 0.9f),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .clickable { glView.renderer.triggerJump() }
                                .testTag("jump_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowUpward,
                                    contentDescription = "Jump",
                                    tint = ObsidianBg,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // 360° Panorama Controls: Zoom + Connected Portals
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = ObsidianSurface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = "Drag to look 360° • ${currentPanoLocation?.aiRoadSurface ?: "Panorama"}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = ObsidianSurface.copy(alpha = 0.85f),
                                shape = CircleShape,
                                modifier = Modifier.border(1.dp, ObsidianBorder, CircleShape)
                            ) {
                                IconButton(
                                    onClick = { glView.renderer.adjustPanoZoom(-10f) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in", tint = TextPrimary)
                                }
                            }
                            Surface(
                                color = ObsidianSurface.copy(alpha = 0.85f),
                                shape = CircleShape,
                                modifier = Modifier.border(1.dp, ObsidianBorder, CircleShape)
                            ) {
                                IconButton(
                                    onClick = { glView.renderer.adjustPanoZoom(10f) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out", tint = TextPrimary)
                                }
                            }
                        }
                    }

                    val portalChoices = connectedLocations.ifEmpty { locations.filter { it.id != currentPanoLocation?.id } }
                    if (portalChoices.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(portalChoices, key = { it.id }) { target ->
                                Surface(
                                    color = ObsidianSurface.copy(alpha = 0.90f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, SkyBlueAccent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            activePanoLocationId = target.id
                                            glView.renderer.teleportTo(target.posX.toFloat(), target.posZ.toFloat())
                                            if (!target.explored) onLocationExplored(target.id)
                                        }
                                ) {
                                    Text(
                                        text = "Portal → ${target.name}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SkyBlueAccent,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualJoystick(
    onMove: (Float, Float) -> Unit
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val maxRadiusPx = 95f

    Box(
        modifier = Modifier
            .size(126.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onMove(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val candidate = knobOffset + dragAmount
                    val len = sqrt(candidate.x * candidate.x + candidate.y * candidate.y)
                    knobOffset = if (len > maxRadiusPx) {
                        Offset(candidate.x / len * maxRadiusPx, candidate.y / len * maxRadiusPx)
                    } else {
                        candidate
                    }
                    // Screen up (negative Y) is forward (+1f)
                    onMove(knobOffset.x / maxRadiusPx, -knobOffset.y / maxRadiusPx)
                }
            }
            .testTag("virtual_joystick")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerR = size.minDimension / 2f - 4f
            drawCircle(
                color = ObsidianSurface.copy(alpha = 0.65f),
                radius = outerR,
                center = center
            )
            drawCircle(
                color = AmberGold.copy(alpha = 0.45f),
                radius = outerR,
                center = center,
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = AmberGold.copy(alpha = 0.92f),
                radius = outerR * 0.38f,
                center = center + knobOffset
            )
        }
    }
}

@Composable
private fun VillageMinimapRadar(
    locations: List<VillageLocation>,
    playerX: Float,
    playerZ: Float,
    cameraYawDeg: Float
) {
    Surface(
        color = ObsidianSurface.copy(alpha = 0.85f),
        shape = CircleShape,
        modifier = Modifier
            .size(92.dp)
            .border(1.5.dp, ObsidianBorder, CircleShape)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val scale = 0.48f
            drawCircle(color = ObsidianBg, radius = size.minDimension / 2f)
            drawCircle(color = ObsidianBorder, radius = size.minDimension / 4f, style = Stroke(1.5f))

            locations.forEach { loc ->
                val dx = (loc.posX.toFloat() - playerX) * scale
                val dz = (loc.posZ.toFloat() - playerZ) * scale
                val pt = Offset(
                    (center.x + dx).coerceIn(6f, size.width - 6f),
                    (center.y + dz).coerceIn(6f, size.height - 6f)
                )
                drawCircle(
                    color = if (loc.markedAsHouse) CrimsonHeart else SkyBlueAccent,
                    radius = if (loc.markedAsHouse) 5.5f else 4f,
                    center = pt
                )
            }

            // Player dot + heading line
            val rad = cameraYawDeg * Math.PI.toFloat() / 180f
            val tip = Offset(center.x + sin(rad) * 14f, center.y + cos(rad) * 14f)
            drawLine(color = AmberGold, start = center, end = tip, strokeWidth = 3f)
            drawCircle(color = AmberGold, radius = 5f, center = center)
        }
    }
}
