package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val MurgiTolaColorScheme = darkColorScheme(
    primary = AmberGold,
    onPrimary = ObsidianBg,
    primaryContainer = Color(0xFF3B2805),
    onPrimaryContainer = AmberGold,
    secondary = EmeraldVibrant,
    onSecondary = ObsidianBg,
    secondaryContainer = EmeraldForest,
    onSecondaryContainer = EmeraldVibrant,
    tertiary = CrimsonHeart,
    onTertiary = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder,
    error = ErrorRed,
    onError = Color.White
)

private val MurgiTolaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MurgiTolaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MurgiTolaColorScheme,
        typography = AppTypography,
        shapes = MurgiTolaShapes,
        content = content
    )
}
