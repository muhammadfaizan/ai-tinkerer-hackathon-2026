package com.example.intune.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val IntuneColorScheme = lightColorScheme(
    primary = Pink,
    onPrimary = Charcoal,
    primaryContainer = Pink,
    onPrimaryContainer = Charcoal,
    secondary = Violet,
    onSecondary = WhiteSurface,
    secondaryContainer = Violet,
    onSecondaryContainer = WhiteSurface,
    tertiary = Gold,
    onTertiary = Charcoal,
    tertiaryContainer = Gold,
    onTertiaryContainer = Charcoal,
    background = Violet,
    onBackground = WhiteSurface,
    surface = WhiteSurface,
    onSurface = Charcoal,
    surfaceVariant = NearWhiteSurface,
    onSurfaceVariant = MutedCharcoal,
    surfaceTint = WhiteSurface,
    inverseSurface = Charcoal,
    inverseOnSurface = WhiteSurface,
    inversePrimary = Pink,
    outline = Violet,
    outlineVariant = Pink,
    scrim = Charcoal,
    error = Pink,
    onError = Charcoal,
    errorContainer = NearWhiteSurface,
    onErrorContainer = Charcoal,
    surfaceDim = NearWhiteSurface,
    surfaceBright = WhiteSurface,
    surfaceContainerLowest = WhiteSurface,
    surfaceContainerLow = WhiteSurface,
    surfaceContainer = WhiteSurface,
    surfaceContainerHigh = NearWhiteSurface,
    surfaceContainerHighest = NearWhiteSurface,
    primaryFixed = Pink,
    primaryFixedDim = Pink,
    onPrimaryFixed = Charcoal,
    onPrimaryFixedVariant = Charcoal,
    secondaryFixed = Violet,
    secondaryFixedDim = Violet,
    onSecondaryFixed = WhiteSurface,
    onSecondaryFixedVariant = WhiteSurface,
    tertiaryFixed = Gold,
    tertiaryFixedDim = Gold,
    onTertiaryFixed = Charcoal,
    onTertiaryFixedVariant = Charcoal,
)

private val IntuneShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun IntuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IntuneColorScheme,
        typography = Typography,
        shapes = IntuneShapes,
        content = content,
    )
}
