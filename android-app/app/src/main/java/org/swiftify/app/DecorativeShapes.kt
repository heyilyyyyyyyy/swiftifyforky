package org.swiftify.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.swiftify.app.design.SwiftifyColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WavyLine(
    modifier: Modifier = Modifier,
    color: Color = SwiftifyColors.Primary.copy(alpha = 0.25f),
    wavelength: Float = 14.2f,
    amplitude: Float = 1.3f,
    strokeWidth: Float = 2.0f
) {
    Canvas(modifier = modifier.height((amplitude * 2 + strokeWidth * 2 + 4f).dp)) {
        val width = size.width
        val midY = size.height / 2f
        val wavelengthPx = wavelength.dp.toPx()
        val ampPx = amplitude.dp.toPx()
        val strokePx = strokeWidth.dp.toPx()

        val path = Path()
        var first = true
        val step = 1.5f
        var x = 0f
        while (x <= width) {
            val y = midY + sin((x / wavelengthPx) * 2f * PI.toFloat()) * ampPx
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            x += step
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun Cookie12Border(
    modifier: Modifier = Modifier,
    color: Color = SwiftifyColors.Primary.copy(alpha = 0.4f),
    lobes: Int = 12,
    strokeWidth: Dp = 1.6.dp
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = (minOf(size.width, size.height) / 2f) - strokeWidth.toPx()
        val amp = maxR * 0.08f
        val baseR = maxR - amp
        val path = Path()
        val steps = 180
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * (2f * PI.toFloat())
            val r = baseR + amp * cos(lobes * theta)
            val x = cx + r * cos(theta)
            val y = cy + r * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
