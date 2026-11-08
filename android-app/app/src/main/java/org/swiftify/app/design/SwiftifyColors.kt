package org.swiftify.app.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

enum class SwiftifyThemeMode(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val primaryColor: Color,
    val surfaceColor: Color,
    val backgroundColor: Color
) {
    SPOTIFY(
        id = "spotify",
        displayName = "Spotify",
        subtitle = "Classic Green & Black",
        primaryColor = Color(0xFF1ED760),
        surfaceColor = Color(0xFF121212),
        backgroundColor = Color(0xFF000000)
    ),
    SWIFTIFY(
        id = "swiftify",
        displayName = "Swiftify Pink",
        subtitle = "Signature Pink & Charcoal",
        primaryColor = Color(0xFFFF3D8A),
        surfaceColor = Color(0xFF19191B),
        backgroundColor = Color(0xFF0C0C0D)
    ),
    HEYILY(
        id = "heyily",
        displayName = "heyily<3 style",
        subtitle = "PC Matugen Colors",
        primaryColor = Color(0xFFDFBFBA),
        surfaceColor = Color(0xFF161312),
        backgroundColor = Color(0xFF161312)
    ),
    CATPPUCCIN(
        id = "catppuccin",
        displayName = "Catppuccin Mocha",
        subtitle = "Pastel Mauve & Dark Mantle",
        primaryColor = Color(0xFFCBA6F7),
        surfaceColor = Color(0xFF181825),
        backgroundColor = Color(0xFF11111B)
    ),
    ROSE_PINE(
        id = "rosepine",
        displayName = "Rosé Pine",
        subtitle = "Warm Rose & Pine Base",
        primaryColor = Color(0xFFEBBCBA),
        surfaceColor = Color(0xFF1F1D2E),
        backgroundColor = Color(0xFF191724)
    ),
    GRUVBOX(
        id = "gruvbox",
        displayName = "Gruvbox Dark",
        subtitle = "Retro Orange & Warm Dark",
        primaryColor = Color(0xFFFE8019),
        surfaceColor = Color(0xFF282828),
        backgroundColor = Color(0xFF1D2021)
    ),
    NORD(
        id = "nord",
        displayName = "Nord",
        subtitle = "Arctic Cyan & Polar Night",
        primaryColor = Color(0xFF88C0D0),
        surfaceColor = Color(0xFF2E3440),
        backgroundColor = Color(0xFF242933)
    ),
    TOKYO_NIGHT(
        id = "tokyonight",
        displayName = "Tokyo Night",
        subtitle = "Neon Blue & Stormy Indigo",
        primaryColor = Color(0xFF7AA2F7),
        surfaceColor = Color(0xFF1A1B26),
        backgroundColor = Color(0xFF16161E)
    ),
    OLED(
        id = "oled",
        displayName = "OLED Pure Black",
        subtitle = "AMOLED Pitch Black",
        primaryColor = Color(0xFF1ED760),
        surfaceColor = Color(0xFF0A0A0A),
        backgroundColor = Color(0xFF000000)
    );

    companion object {
        fun fromId(id: String?): SwiftifyThemeMode {
            if (id == null) return SPOTIFY
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: SPOTIFY
        }
    }
}

object SwiftifyColors {
    val Primary = Color(0xFF1ED760)
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF121212)

    // 1. Spotify (Default)
    val SpotifyColorScheme = darkColorScheme(
        primary = Color(0xFF1ED760),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF164D29),
        onPrimaryContainer = Color(0xFF86F9A5),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF121212),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF1F1F1F),
        onSurfaceVariant = Color(0xFFB3B3B3),
        outline = Color(0xFF7C7C7C),
        outlineVariant = Color(0xFF3E3E3E),
        error = Color(0xFFF3727F),
        onError = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF121212),
        surfaceContainer = Color(0xFF181818),
        surfaceContainerHigh = Color(0xFF1F1F1F),
        surfaceContainerHighest = Color(0xFF2A2A2A)
    )

    // 2. Swiftify Pink (Brand Default)
    val SwiftifyPinkColorScheme = darkColorScheme(
        primary = Color(0xFFFF3D8A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF4A1528),
        onPrimaryContainer = Color(0xFFFFB3CD),
        background = Color(0xFF0C0C0D),
        onBackground = Color(0xFFECECEE),
        surface = Color(0xFF19191B),
        onSurface = Color(0xFFECECEE),
        surfaceVariant = Color(0xFF232326),
        onSurfaceVariant = Color(0xFFA9A9AE),
        outline = Color(0xFF7C7C82),
        outlineVariant = Color(0xFF3E3E44),
        error = Color(0xFFEF5350),
        onError = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF070708),
        surfaceContainerLow = Color(0xFF131315),
        surfaceContainer = Color(0xFF19191B),
        surfaceContainerHigh = Color(0xFF232326),
        surfaceContainerHighest = Color(0xFF2E2E31)
    )

    // 3. Catppuccin Mocha
    val CatppuccinColorScheme = darkColorScheme(
        primary = Color(0xFFCBA6F7),
        onPrimary = Color(0xFF11111B),
        primaryContainer = Color(0xFF3D2D54),
        onPrimaryContainer = Color(0xFFEEDBFF),
        background = Color(0xFF11111B),
        onBackground = Color(0xFFCDD6F4),
        surface = Color(0xFF181825),
        onSurface = Color(0xFFCDD6F4),
        surfaceVariant = Color(0xFF1E1E2E),
        onSurfaceVariant = Color(0xFFA6ADC8),
        outline = Color(0xFF7F849C),
        outlineVariant = Color(0xFF45475A),
        error = Color(0xFFF38BA8),
        onError = Color(0xFF11111B),
        surfaceContainerLowest = Color(0xFF0D0D15),
        surfaceContainerLow = Color(0xFF141420),
        surfaceContainer = Color(0xFF181825),
        surfaceContainerHigh = Color(0xFF1E1E2E),
        surfaceContainerHighest = Color(0xFF313244)
    )

    // 4. Rosé Pine
    val RosePineColorScheme = darkColorScheme(
        primary = Color(0xFFEBBCBA),
        onPrimary = Color(0xFF191724),
        primaryContainer = Color(0xFF3E2D3B),
        onPrimaryContainer = Color(0xFFFCE1E0),
        background = Color(0xFF191724),
        onBackground = Color(0xFFE0DEF4),
        surface = Color(0xFF1F1D2E),
        onSurface = Color(0xFFE0DEF4),
        surfaceVariant = Color(0xFF26233A),
        onSurfaceVariant = Color(0xFF908CAA),
        outline = Color(0xFF6E6A86),
        outlineVariant = Color(0xFF524F67),
        error = Color(0xFFEB6F92),
        onError = Color(0xFF191724),
        surfaceContainerLowest = Color(0xFF13111C),
        surfaceContainerLow = Color(0xFF1A1827),
        surfaceContainer = Color(0xFF1F1D2E),
        surfaceContainerHigh = Color(0xFF26233A),
        surfaceContainerHighest = Color(0xFF403D52)
    )

    // 5. Gruvbox Dark
    val GruvboxColorScheme = darkColorScheme(
        primary = Color(0xFFFE8019),
        onPrimary = Color(0xFF1D2021),
        primaryContainer = Color(0xFF4E260A),
        onPrimaryContainer = Color(0xFFFFD0A6),
        background = Color(0xFF1D2021),
        onBackground = Color(0xFFEBDBB2),
        surface = Color(0xFF282828),
        onSurface = Color(0xFFEBDBB2),
        surfaceVariant = Color(0xFF3C3836),
        onSurfaceVariant = Color(0xFFA89984),
        outline = Color(0xFF928374),
        outlineVariant = Color(0xFF665C54),
        error = Color(0xFFFB4934),
        onError = Color(0xFF1D2021),
        surfaceContainerLowest = Color(0xFF141617),
        surfaceContainerLow = Color(0xFF202223),
        surfaceContainer = Color(0xFF282828),
        surfaceContainerHigh = Color(0xFF3C3836),
        surfaceContainerHighest = Color(0xFF504945)
    )

    // 6. Nord
    val NordColorScheme = darkColorScheme(
        primary = Color(0xFF88C0D0),
        onPrimary = Color(0xFF242933),
        primaryContainer = Color(0xFF253D47),
        onPrimaryContainer = Color(0xFFC5EFFC),
        background = Color(0xFF242933),
        onBackground = Color(0xFFECEFF4),
        surface = Color(0xFF2E3440),
        onSurface = Color(0xFFECEFF4),
        surfaceVariant = Color(0xFF3B4252),
        onSurfaceVariant = Color(0xFFD8DEE9),
        outline = Color(0xFF4C566A),
        outlineVariant = Color(0xFF3B4252),
        error = Color(0xFFBF616A),
        onError = Color(0xFF242933),
        surfaceContainerLowest = Color(0xFF1C2028),
        surfaceContainerLow = Color(0xFF272D37),
        surfaceContainer = Color(0xFF2E3440),
        surfaceContainerHigh = Color(0xFF3B4252),
        surfaceContainerHighest = Color(0xFF434C5E)
    )

    // 7. Tokyo Night
    val TokyoNightColorScheme = darkColorScheme(
        primary = Color(0xFF7AA2F7),
        onPrimary = Color(0xFF16161E),
        primaryContainer = Color(0xFF1E3158),
        onPrimaryContainer = Color(0xFFC4D8FF),
        background = Color(0xFF16161E),
        onBackground = Color(0xFFC0CAF5),
        surface = Color(0xFF1A1B26),
        onSurface = Color(0xFFC0CAF5),
        surfaceVariant = Color(0xFF24283B),
        onSurfaceVariant = Color(0xFF9AA5CE),
        outline = Color(0xFF565F89),
        outlineVariant = Color(0xFF414868),
        error = Color(0xFFF7768E),
        onError = Color(0xFF16161E),
        surfaceContainerLowest = Color(0xFF101016),
        surfaceContainerLow = Color(0xFF161720),
        surfaceContainer = Color(0xFF1A1B26),
        surfaceContainerHigh = Color(0xFF24283B),
        surfaceContainerHighest = Color(0xFF2F354F)
    )

    // 8. OLED Pure Black
    val OledColorScheme = darkColorScheme(
        primary = Color(0xFF1ED760),
        onPrimary = Color(0xFF000000),
        primaryContainer = Color(0xFF164D29),
        onPrimaryContainer = Color(0xFF86F9A5),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF0A0A0A),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF141414),
        onSurfaceVariant = Color(0xFFAAAAAA),
        outline = Color(0xFF666666),
        outlineVariant = Color(0xFF333333),
        error = Color(0xFFF3727F),
        onError = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF050505),
        surfaceContainer = Color(0xFF0A0A0A),
        surfaceContainerHigh = Color(0xFF141414),
        surfaceContainerHighest = Color(0xFF1E1E1E)
    )

    // 9. heyily<3 style (PC Matugen Desktop Theme - 1:1 Parity)
    val HeyilyColorScheme = darkColorScheme(
        primary = Color(0xFFDFBFBA),
        onPrimary = Color(0xFF402B28),
        primaryContainer = Color(0xFF58413E),
        onPrimaryContainer = Color(0xFFFDDBD5),
        inversePrimary = Color(0xFF715954),
        secondary = Color(0xFFD8C2BE),
        onSecondary = Color(0xFF3B2D2B),
        secondaryContainer = Color(0xFF534341),
        onSecondaryContainer = Color(0xFFF5DDDA),
        tertiary = Color(0xFFE7BDB6),
        onTertiary = Color(0xFF442925),
        tertiaryContainer = Color(0xFF5D3F3A),
        onTertiaryContainer = Color(0xFFFFDAD4),
        background = Color(0xFF161312),
        onBackground = Color(0xFFE9E1DF),
        surface = Color(0xFF161312),
        onSurface = Color(0xFFE9E1DF),
        surfaceVariant = Color(0xFF4A4645),
        onSurfaceVariant = Color(0xFFCCC5C3),
        surfaceTint = Color(0xFFDFBFBA),
        inverseSurface = Color(0xFFE9E1DF),
        inverseOnSurface = Color(0xFF332F2F),
        outline = Color(0xFF968F8E),
        outlineVariant = Color(0xFF4A4645),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        surfaceContainerLowest = Color(0xFF100D0D),
        surfaceContainerLow = Color(0xFF1E1B1A),
        surfaceContainer = Color(0xFF221F1E),
        surfaceContainerHigh = Color(0xFF2D2928),
        surfaceContainerHighest = Color(0xFF383433)
    )

    fun colorSchemeFor(themeMode: SwiftifyThemeMode): ColorScheme {
        return when (themeMode) {
            SwiftifyThemeMode.SPOTIFY -> SpotifyColorScheme
            SwiftifyThemeMode.SWIFTIFY -> SwiftifyPinkColorScheme
            SwiftifyThemeMode.HEYILY -> HeyilyColorScheme
            SwiftifyThemeMode.CATPPUCCIN -> CatppuccinColorScheme
            SwiftifyThemeMode.ROSE_PINE -> RosePineColorScheme
            SwiftifyThemeMode.GRUVBOX -> GruvboxColorScheme
            SwiftifyThemeMode.NORD -> NordColorScheme
            SwiftifyThemeMode.TOKYO_NIGHT -> TokyoNightColorScheme
            SwiftifyThemeMode.OLED -> OledColorScheme
        }
    }

    // Default backward compatibility
    val DarkColorScheme = SpotifyColorScheme
}
