package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vrpano
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.GraphicsQuality
import com.example.model.UserSettings
import com.example.model.VillageLocation
import com.example.model.VillageProject
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.SkyBlueAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    activeVillage: VillageProject?,
    locations: List<VillageLocation>,
    settings: UserSettings,
    userEmail: String?,
    onExploreClick: () -> Unit,
    onCreateVillageClick: () -> Unit,
    onScanLocationClick: () -> Unit,
    onSavedLocationsClick: () -> Unit,
    onGraphicsSettingsClick: () -> Unit,
    onCharacterSettingsClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heroPulse")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartBeat"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Cinematic village background
        Image(
            painter = painterResource(id = R.drawable.img_hero_murgi_tola_1795586109583),
            contentDescription = "Murgi Tola cinematic village road",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBg.copy(alpha = 0.62f),
                            ObsidianBg.copy(alpha = 0.75f),
                            ObsidianBg.copy(alpha = 0.96f)
                        )
                    )
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val isWide = maxWidth >= 720.dp
            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        TopStatusStrip(
                            activeVillage = activeVillage,
                            locations = locations,
                            settings = settings,
                            userEmail = userEmail,
                            onSignOutClick = onSignOutClick
                        )
                        HeroBannerContent(heartScale = heartScale, activeVillage = activeVillage, locations = locations)
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyColumn(
                        modifier = Modifier
                            .weight(1.05f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            PrimaryExploreCard(
                                onClick = onExploreClick,
                                locationCount = locations.size
                            )
                        }
                        item {
                            MenuActionGrid(
                                onCreateVillageClick = onCreateVillageClick,
                                onScanLocationClick = onScanLocationClick,
                                onSavedLocationsClick = onSavedLocationsClick,
                                onGraphicsSettingsClick = onGraphicsSettingsClick,
                                onCharacterSettingsClick = onCharacterSettingsClick
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        TopStatusStrip(
                            activeVillage = activeVillage,
                            locations = locations,
                            settings = settings,
                            userEmail = userEmail,
                            onSignOutClick = onSignOutClick
                        )
                    }
                    item {
                        Spacer(Modifier.height(6.dp))
                        HeroBannerContent(
                            heartScale = heartScale,
                            activeVillage = activeVillage,
                            locations = locations
                        )
                    }
                    item {
                        PrimaryExploreCard(
                            onClick = onExploreClick,
                            locationCount = locations.size
                        )
                    }
                    item {
                        MenuActionGrid(
                            onCreateVillageClick = onCreateVillageClick,
                            onScanLocationClick = onScanLocationClick,
                            onSavedLocationsClick = onSavedLocationsClick,
                            onGraphicsSettingsClick = onGraphicsSettingsClick,
                            onCharacterSettingsClick = onCharacterSettingsClick
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TopStatusStrip(
    activeVillage: VillageProject?,
    locations: List<VillageLocation>,
    settings: UserSettings,
    userEmail: String?,
    onSignOutClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = ObsidianSurface.copy(alpha = 0.88f),
            shape = CircleShape,
            modifier = Modifier.border(1.dp, ObsidianBorder, CircleShape)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(EmeraldVibrant)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${activeVillage?.name ?: "Murgi Tola"} • ${locations.size} SPOTS • ${GraphicsQuality.fromRaw(settings.graphicsQuality).label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            color = ObsidianSurface.copy(alpha = 0.88f),
            shape = CircleShape,
            modifier = Modifier.border(1.dp, ObsidianBorder, CircleShape)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
            ) {
                Text(
                    text = userEmail?.substringBefore("@") ?: settings.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp)
                )
                IconButton(
                    onClick = onSignOutClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Sign out",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBannerContent(
    heartScale: Float,
    activeVillage: VillageProject?,
    locations: List<VillageLocation>
) {
    val exploredCount = locations.count { it.explored }
    val houseCount = locations.count { it.markedAsHouse }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = CrimsonHeart.copy(alpha = 0.18f),
                shape = CircleShape,
                modifier = Modifier
                    .scale(heartScale)
                    .border(1.dp, CrimsonHeart.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Heart emblem",
                    tint = CrimsonHeart,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "REAL-WORLD 3D VILLAGE EDITION",
                style = MaterialTheme.typography.labelLarge,
                color = AmberGold
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "I LOVE MURGI TOLA ❤️",
            style = MaterialTheme.typography.displayLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = activeVillage?.description?.ifBlank {
                "Walk, sprint, and explore scanned 360° corners of Murgi Tola in open-world 3D."
            } ?: "Walk, sprint, and explore scanned 360° corners of Murgi Tola in open-world 3D.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatChip("EXPLORED", "$exploredCount/${locations.size}")
            StatChip("MY HOUSE", if (houseCount > 0) "MARKED" else "NOT SET")
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        color = ObsidianSurface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.border(1.dp, ObsidianBorder, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = AmberGold,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PrimaryExploreCard(
    onClick: () -> Unit,
    locationCount: Int
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(AmberGold, Color(0xFFFF7A18))
                )
            )
            .clickable(onClick = onClick)
            .testTag("btn_explore_murgi_tola")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "1. EXPLORE MURGI TOLA",
                    style = MaterialTheme.typography.headlineMedium,
                    color = ObsidianBg,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Third-person 3D world & $locationCount connected 360° portals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ObsidianBg.copy(alpha = 0.85f)
                )
            }
            Surface(
                color = ObsidianBg.copy(alpha = 0.2f),
                shape = CircleShape,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Explore,
                        contentDescription = "Explore Murgi Tola",
                        tint = ObsidianBg,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuActionGrid(
    onCreateVillageClick: () -> Unit,
    onScanLocationClick: () -> Unit,
    onSavedLocationsClick: () -> Unit,
    onGraphicsSettingsClick: () -> Unit,
    onCharacterSettingsClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MenuTile(
                title = "2. CREATE MY VILLAGE",
                subtitle = "Projects & links",
                icon = Icons.Filled.AddBusiness,
                accent = EmeraldVibrant,
                testTag = "btn_create_my_village",
                onClick = onCreateVillageClick,
                modifier = Modifier.weight(1f)
            )
            MenuTile(
                title = "3. SCAN 360° LOCATION",
                subtitle = "Camera & AI 3D",
                icon = Icons.Filled.CameraAlt,
                accent = CrimsonHeart,
                testTag = "btn_scan_360_location",
                onClick = onScanLocationClick,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MenuTile(
                title = "4. MY SAVED LOCATIONS",
                subtitle = "Village map & pins",
                icon = Icons.Filled.Map,
                accent = SkyBlueAccent,
                testTag = "btn_saved_locations",
                onClick = onSavedLocationsClick,
                modifier = Modifier.weight(1f)
            )
            MenuTile(
                title = "5. GRAPHICS SETTINGS",
                subtitle = "LOW to ULTRA & sky",
                icon = Icons.Filled.Tune,
                accent = AmberGold,
                testTag = "btn_graphics_settings",
                onClick = onGraphicsSettingsClick,
                modifier = Modifier.weight(1f)
            )
        }
        MenuTile(
            title = "6. CHARACTER SETTINGS",
            subtitle = "Customize outfit, speed & 360° camera sensitivity",
            icon = Icons.Filled.Person,
            accent = EmeraldVibrant,
            testTag = "btn_character_settings",
            onClick = onCharacterSettingsClick,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = Icons.Filled.Vrpano
        )
    }
}

@Composable
private fun MenuTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null
) {
    Surface(
        color = ObsidianSurface.copy(alpha = 0.90f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (trailingIcon != null) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = ObsidianSurfaceHigh,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
