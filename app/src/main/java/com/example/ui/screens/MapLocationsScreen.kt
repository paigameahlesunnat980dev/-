package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.LocationCategory
import com.example.model.VillageLocation
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SkyBlueAccent
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.io.File

@Composable
fun MapLocationsScreen(
    locations: List<VillageLocation>,
    onExploreLocation: (VillageLocation) -> Unit,
    onToggleHouse: (VillageLocation) -> Unit,
    onToggleFavorite: (VillageLocation) -> Unit,
    onDeleteLocation: (VillageLocation) -> Unit,
    onBack: () -> Unit
) {
    var showMapTab by remember { mutableStateOf(true) }
    var selectedLoc by remember(locations) { mutableStateOf(locations.firstOrNull()) }
    var mapZoom by remember { mutableFloatStateOf(1.0f) }
    var mapPan by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        text = "MURGI TOLA MAP & SPOTS",
                        style = MaterialTheme.typography.headlineMedium,
                        color = AmberGold
                    )
                    Text(
                        text = "${locations.count { it.explored }}/${locations.size} explored • Pinch/drag map",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Surface(
                color = ObsidianSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                    .clickable { showMapTab = !showMapTab }
                    .testTag("toggle_map_list_view")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (showMapTab) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.Map,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (showMapTab) "LIST" else "MAP",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )
                }
            }
        }

        if (showMapTab) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Interactive Tactical Map Canvas
                Surface(
                    color = Color(0xFF0A1410),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, ObsidianBorder, RoundedCornerShape(20.dp))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                mapZoom = (mapZoom * zoom).coerceIn(0.6f, 2.8f)
                                mapPan += pan
                            }
                        }
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f) + mapPan
                            val scale = 5.2f * mapZoom

                            // Grid lines (explored vs unexplored feel)
                            val gridStep = 40f * mapZoom
                            for (gx in -10..10) {
                                val x = center.x + gx * gridStep
                                drawLine(
                                    color = ObsidianBorder.copy(alpha = 0.35f),
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 1f
                                )
                            }
                            for (gy in -10..10) {
                                val y = center.y + gy * gridStep
                                drawLine(
                                    color = ObsidianBorder.copy(alpha = 0.35f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1f
                                )
                            }

                            // Explored aura circles around explored locations
                            locations.filter { it.explored }.forEach { loc ->
                                val pt = Offset(center.x + loc.posX.toFloat() * scale, center.y + loc.posZ.toFloat() * scale)
                                drawCircle(
                                    color = EmeraldVibrant.copy(alpha = 0.14f),
                                    radius = 18f * scale * 0.25f,
                                    center = pt
                                )
                            }

                            // Connecting road paths
                            val locMap = locations.associateBy { it.id }
                            locations.forEach { loc ->
                                val p1 = Offset(center.x + loc.posX.toFloat() * scale, center.y + loc.posZ.toFloat() * scale)
                                loc.connectedIds.forEach { targetId ->
                                    val t = locMap[targetId]
                                    if (t != null) {
                                        val p2 = Offset(center.x + t.posX.toFloat() * scale, center.y + t.posZ.toFloat() * scale)
                                        drawLine(
                                            color = AmberGold.copy(alpha = 0.65f),
                                            start = p1,
                                            end = p2,
                                            strokeWidth = 4f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                        )
                                    }
                                }
                            }

                            // Location pins
                            locations.forEach { loc ->
                                val pt = Offset(center.x + loc.posX.toFloat() * scale, center.y + loc.posZ.toFloat() * scale)
                                val pinColor = when {
                                    loc.markedAsHouse -> CrimsonHeart
                                    loc.favorite -> AmberGold
                                    loc.explored -> EmeraldVibrant
                                    else -> TextMuted
                                }
                                if (selectedLoc?.id == loc.id) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = 20f,
                                        center = pt,
                                        style = Stroke(width = 3f)
                                    )
                                }
                                drawCircle(color = pinColor, radius = 13f, center = pt)
                                drawCircle(color = ObsidianBg, radius = 5f, center = pt)
                            }
                        }

                        // Map Legend overlay
                        Surface(
                            color = ObsidianSurface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                                .border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LegendDot(CrimsonHeart, "My House")
                                LegendDot(AmberGold, "Favorite")
                                LegendDot(EmeraldVibrant, "Explored")
                            }
                        }

                        // Quick select chips along bottom of map
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            locations.take(4).forEach { loc ->
                                Surface(
                                    color = if (selectedLoc?.id == loc.id) AmberGold else ObsidianSurface.copy(alpha = 0.9f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { selectedLoc = loc }
                                ) {
                                    Text(
                                        text = loc.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedLoc?.id == loc.id) ObsidianBg else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected Location Inspection Card
                selectedLoc?.let { loc ->
                    LocationDetailCard(
                        loc = loc,
                        onExplore = { onExploreLocation(loc) },
                        onToggleHouse = { onToggleHouse(loc) },
                        onToggleFavorite = { onToggleFavorite(loc) },
                        onDelete = { onDeleteLocation(loc) }
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        } else {
            // Full List of Saved Locations
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .align(Alignment.CenterHorizontally),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(locations, key = { it.id }) { loc ->
                    LocationDetailCard(
                        loc = loc,
                        onExplore = { onExploreLocation(loc) },
                        onToggleHouse = { onToggleHouse(loc) },
                        onToggleFavorite = { onToggleFavorite(loc) },
                        onDelete = { onDeleteLocation(loc) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun LocationDetailCard(
    loc: VillageLocation,
    onExplore: () -> Unit,
    onToggleHouse: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val localBmp = remember(loc.localImagePath) {
        val file = File(loc.localImagePath)
        if (loc.localImagePath.isNotBlank() && file.exists()) {
            BitmapFactory.decodeFile(loc.localImagePath)
        } else null
    }

    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianBorder, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (localBmp != null) {
                    Image(
                        bitmap = localBmp.asImageBitmap(),
                        contentDescription = loc.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(68.dp).clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_village_panorama_sample_1795586109597),
                        contentDescription = loc.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(68.dp).clip(RoundedCornerShape(12.dp))
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = loc.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${LocationCategory.fromRaw(loc.category).label} • ${if (loc.explored) "Explored" else "Unexplored"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (loc.markedAsHouse) CrimsonHeart else AmberGold
                    )
                    if (loc.aiSummary.isNotBlank()) {
                        Text(
                            text = loc.aiSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onToggleHouse, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Home,
                            contentDescription = "Mark as My House",
                            tint = if (loc.markedAsHouse) CrimsonHeart else TextSecondary
                        )
                    }
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (loc.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Favorite location",
                            tint = if (loc.favorite) AmberGold else TextSecondary
                        )
                    }
                    if (loc.id != "starter_entrance") {
                        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete location",
                                tint = TextMuted
                            )
                        }
                    }
                }

                Button(
                    onClick = onExplore,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("TELEPORT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
