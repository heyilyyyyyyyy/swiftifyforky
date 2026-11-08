package org.swiftify.app

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat

object MaterialSymbols {
    const val Shuffle = "\ue043"
    const val Repeat = "\ue040"
    const val RepeatOne = "\ue041"
    const val SkipPrevious = "\ue045"
    const val SkipNext = "\ue044"
    const val PlayArrow = "\ue037"
    const val Pause = "\ue034"
    const val ArrowBack = "\ue5c4"
    const val KeyboardArrowDown = "\ue313"
    const val MoreVert = "\ue5d4"
    const val Favorite = "\ue87d"
    const val FavoriteBorder = "\ue87e"
    const val Bedtime = "\uef3e"
    const val VolumeUp = "\ue050"
    const val QueueMusic = "\ue03d"
    const val PlaylistAdd = "\ue03b"
}

/**
 * Renders an authentic Material Symbols Rounded icon glyph loaded directly from
 * material_symbols_rounded.ttf (the identical desktop font file).
 *
 * Uses Paint.getTextBounds() to calculate the exact geometric visual bounding box
 * of the glyph and centers it mathematically at (width/2, height/2). This guarantees
 * 100% dead-center alignment inside any enclosing shape or box, completely bypassing
 * Android font baseline and ascent/descent vertical shifts.
 */
@Composable
fun MaterialSymbol(
    symbol: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    val context = LocalContext.current
    val typeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.material_symbols_rounded)
    }
    val bounds = remember { Rect() }
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            isSubpixelText = true
        }
    }

    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        this.role = Role.Image
                    }
                } else Modifier
            )
    ) {
        if (typeface == null) return@Canvas
        val pxSize = size.toPx()
        paint.typeface = typeface
        paint.textSize = pxSize
        paint.color = tint.toArgb()

        paint.getTextBounds(symbol, 0, symbol.length, bounds)

        // Mathematical visual center:
        // bounds.exactCenterX() is the horizontal center of the glyph relative to the drawing origin.
        // bounds.exactCenterY() is the vertical center of the glyph relative to the drawing origin (baseline).
        // By subtracting these from canvas (center.x, center.y), the visual glyph bounding box
        // is placed symmetrically at the exact dead center of this Composable canvas.
        val drawX = center.x - bounds.exactCenterX()
        val drawY = center.y - bounds.exactCenterY()

        drawContext.canvas.nativeCanvas.drawText(symbol, drawX, drawY, paint)
    }
}
