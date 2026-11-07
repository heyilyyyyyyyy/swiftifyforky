package org.swiftify.app.design

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object SwiftifyColors {
    // Spotify Default Palette (Desktop parity)
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF121212)
    val SurfaceContainerLowest = Color(0xFF000000)
    val SurfaceContainerLow = Color(0xFF121212)
    val SurfaceContainer = Color(0xFF181818)
    val SurfaceContainerHigh = Color(0xFF1F1F1F)
    val SurfaceContainerHighest = Color(0xFF2A2A2A)

    val OnSurface = Color(0xFFFFFFFF)
    val OnBackground = Color(0xFFFFFFFF)
    val OnSurfaceVariant = Color(0xFFB3B3B3)

    val Outline = Color(0xFF7C7C7C)
    val OutlineVariant = Color(0xFF3E3E3E)

    val Primary = Color(0xFF1ED760) // Spotify Green
    val OnPrimary = Color(0xFF000000)
    val PrimaryContainer = Color(0xFF164D29)
    val OnPrimaryContainer = Color(0xFF86F9A5)

    val Error = Color(0xFFF3727F)
    val OnError = Color(0xFF000000)

    val DarkColorScheme = darkColorScheme(
        primary = Primary,
        onPrimary = OnPrimary,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnPrimaryContainer,
        background = Background,
        onBackground = OnBackground,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = SurfaceContainerHigh,
        onSurfaceVariant = OnSurfaceVariant,
        outline = Outline,
        outlineVariant = OutlineVariant,
        error = Error,
        onError = OnError,
        surfaceContainerLowest = SurfaceContainerLowest,
        surfaceContainerLow = SurfaceContainerLow,
        surfaceContainer = SurfaceContainer,
        surfaceContainerHigh = SurfaceContainerHigh,
        surfaceContainerHighest = SurfaceContainerHighest
    )
}
