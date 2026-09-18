package com.example.intune.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val IntuneColorScheme = lightColorScheme(
    primary = Sage,
    onPrimary = OffWhite,
    primaryContainer = Sage,
    onPrimaryContainer = OffWhite,
    secondary = Terracotta,
    onSecondary = OffWhite,
    secondaryContainer = Terracotta,
    onSecondaryContainer = OffWhite,
    tertiary = Gold,
    onTertiary = BrownBlack,
    tertiaryContainer = Gold,
    onTertiaryContainer = BrownBlack,
    background = Cream,
    onBackground = BrownBlack,
    surface = OffWhite,
    onSurface = BrownBlack,
    surfaceVariant = Cream,
    onSurfaceVariant = WarmGray,
    surfaceTint = OffWhite,
    inverseSurface = BrownBlack,
    inverseOnSurface = OffWhite,
    inversePrimary = Sage,
    outline = Sage,
    outlineVariant = Terracotta,
    scrim = BrownBlack,
    error = Terracotta,
    onError = OffWhite,
    errorContainer = Cream,
    onErrorContainer = BrownBlack,
    surfaceDim = Cream,
    surfaceBright = OffWhite,
    surfaceContainerLowest = OffWhite,
    surfaceContainerLow = OffWhite,
    surfaceContainer = OffWhite,
    surfaceContainerHigh = Cream,
    surfaceContainerHighest = Cream,
    primaryFixed = Sage,
    primaryFixedDim = Sage,
    onPrimaryFixed = OffWhite,
    onPrimaryFixedVariant = OffWhite,
    secondaryFixed = Terracotta,
    secondaryFixedDim = Terracotta,
    onSecondaryFixed = OffWhite,
    onSecondaryFixedVariant = OffWhite,
    tertiaryFixed = Gold,
    tertiaryFixedDim = Gold,
    onTertiaryFixed = BrownBlack,
    onTertiaryFixedVariant = BrownBlack,
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
