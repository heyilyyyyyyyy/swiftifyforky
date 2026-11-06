package org.swiftify.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrackThemePalette(
    val dominant: Color = Color(0xFF221A17),
    val secondary: Color = Color(0xFF15100E),
    val accent: Color = Color(0xFFF2A385),
    val onAccent: Color = Color(0xFF1B110E),
    val surface: Color = Color(0x33FFFFFF)
)

val LocalTrackPalette = compositionLocalOf { TrackThemePalette() }

@Composable
fun rememberTrackPalette(context: Context, artworkUrl: String?): TrackThemePalette {
    var palette by remember(artworkUrl) { mutableStateOf(TrackThemePalette()) }

    LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .allowHardware(false) // required to access Bitmap pixels for Palette
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        val p = Palette.from(bitmap).generate()
                        val dominantRgb = p.getDominantColor(0xFF221A17.toInt())
                        val vibrantRgb = p.getVibrantColor(0xFFF2A385.toInt())
                        val mutedDarkRgb = p.getDarkMutedColor(0xFF15100E.toInt())

                        // Muted dark background tone
                        val bgDominant = Color(dominantRgb).copy(alpha = 1f)
                        val bgSecondary = Color(mutedDarkRgb).copy(alpha = 1f)
                        val accentCol = Color(vibrantRgb).copy(alpha = 1f)

                        withContext(Dispatchers.Main) {
                            palette = TrackThemePalette(
                                dominant = bgDominant,
                                secondary = bgSecondary,
                                accent = if (accentCol == bgDominant) Color(0xFFF2A385) else accentCol,
                                onAccent = Color(0xFF120E0D),
                                surface = Color(0x22FFFFFF)
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Smoothly animate colors when track changes
    val animatedDominant by animateColorAsState(palette.dominant, animationSpec = tween(600), label = "dom")
    val animatedSecondary by animateColorAsState(palette.secondary, animationSpec = tween(600), label = "sec")
    val animatedAccent by animateColorAsState(palette.accent, animationSpec = tween(600), label = "acc")

    return TrackThemePalette(
        dominant = animatedDominant,
        secondary = animatedSecondary,
        accent = animatedAccent,
        onAccent = palette.onAccent,
        surface = palette.surface
    )
}
