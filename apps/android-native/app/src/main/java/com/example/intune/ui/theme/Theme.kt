package com.example.intune.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val IntuneColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = CardWhite,
    primaryContainer = LavenderTint,
    onPrimaryContainer = Plum,
    secondary = CoralBadge,
    onSecondary = CardWhite,
    secondaryContainer = PeachTint,
    onSecondaryContainer = Plum,
    tertiary = CoralBadge,
    onTertiary = CardWhite,
    tertiaryContainer = PeachTint,
    onTertiaryContainer = Plum,
    background = Cream,
    onBackground = Plum,
    surface = CardWhite,
    onSurface = Plum,
    surfaceVariant = LavenderTint,
    onSurfaceVariant = WarmGray,
    surfaceTint = CardWhite,
    inverseSurface = Plum,
    inverseOnSurface = CardWhite,
    inversePrimary = LavenderTint,
    outline = LavenderBadge,
    outlineVariant = LavenderTint,
    scrim = Plum,
    error = CoralBadge,
    onError = CardWhite,
    errorContainer = Cream,
    onErrorContainer = Plum,
    surfaceDim = Cream,
    surfaceBright = CardWhite,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = Cream,
    surfaceContainerHighest = Cream,
    primaryFixed = LavenderTint,
    primaryFixedDim = LavenderBadge,
    onPrimaryFixed = Plum,
    onPrimaryFixedVariant = Plum,
    secondaryFixed = PeachTint,
    secondaryFixedDim = CoralBadge,
    onSecondaryFixed = Plum,
    onSecondaryFixedVariant = Plum,
    tertiaryFixed = PeachTint,
    tertiaryFixedDim = CoralBadge,
    onTertiaryFixed = Plum,
    onTertiaryFixedVariant = Plum,
)

private val IntuneShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(20.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
