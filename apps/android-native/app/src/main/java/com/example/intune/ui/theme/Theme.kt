package com.example.intune.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = SageLight,
    secondary = Coral,
    tertiary = Amber,
    background = Night,
    surface = NightSurface,
    surfaceVariant = Sage,
    primaryContainer = Sage,
    secondaryContainer = CoralLight,
    tertiaryContainer = AmberLight,
    onPrimary = Night,
    onSecondary = Ink,
    onTertiary = Ink,
    onBackground = SageLight,
    onSurface = SageLight,
    onSurfaceVariant = SageLight,
    onPrimaryContainer = Night,
    onSecondaryContainer = Ink,
    onTertiaryContainer = Ink,
    outline = SageLight,
)

private val LightColorScheme = lightColorScheme(
    primary = Sage,
    secondary = Coral,
    tertiary = Amber,
    background = WarmOffWhite,
    surface = WhiteSurface,
    surfaceVariant = SageLight,
    primaryContainer = SageLight,
    secondaryContainer = CoralLight,
    tertiaryContainer = AmberLight,
    onPrimary = WhiteSurface,
    onSecondary = Ink,
    onTertiary = Ink,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = MutedInk,
    onPrimaryContainer = Ink,
    onSecondaryContainer = Ink,
    onTertiaryContainer = Ink,
    outline = MutedInk,
)

private val IntuneShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun IntuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = IntuneShapes,
        content = content
    )
}
