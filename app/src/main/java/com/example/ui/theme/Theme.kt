package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = StudioBlue,
    onPrimary = TextPrimary,
    primaryContainer = StudioBlueMuted,
    onPrimaryContainer = TextPrimary,
    secondary = AudioGreen,
    onSecondary = TrackWorkspaceBg,
    secondaryContainer = ElevatedRack,
    onSecondaryContainer = TextPrimary,
    tertiary = VioletMidi,
    onTertiary = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ConsoleSurface,
    onSurface = TextPrimary,
    surfaceVariant = PanelSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = PanelSurface,
    surfaceContainerHigh = ElevatedRack,
    outline = ConsoleBorder,
    error = CrimsonClip,
    onError = TextPrimary
)

private val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun TrueworldStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = StudioTypography,
        shapes = StudioShapes,
        content = content
    )
}
