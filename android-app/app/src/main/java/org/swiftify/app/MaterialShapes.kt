package org.swiftify.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.*

private val heartRadii = floatArrayOf(0.735f, 0.695f, 0.64f, 0.615f, 0.61f, 0.585f, 0.57f, 0.575f, 0.565f, 0.555f, 0.565f, 0.58f, 0.59f, 0.61f, 0.65f, 0.675f, 0.745f, 0.795f, 0.87f, 0.805f, 0.75f, 0.715f, 0.675f, 0.64f, 0.615f, 0.6f, 0.59f, 0.58f, 0.58f, 0.58f, 0.585f, 0.595f, 0.615f, 0.635f, 0.665f, 0.715f, 0.75f, 0.815f, 0.905f, 0.965f, 1.035f, 1.075f, 1.1f, 1.115f, 1.11f, 1.12f, 1.085f, 1.04f, 0.96f, 0.875f, 0.76f, 0.635f, 0.54f, 0.475f, 0.455f, 0.495f, 0.56f, 0.655f, 0.78f, 0.895f, 0.985f, 1.04f, 1.075f, 1.09f, 1.105f, 1.1f, 1.1f, 1.05f, 1.015f, 0.945f, 0.885f, 0.795f)

private val bunRadii = floatArrayOf(0.615f, 0.735f, 0.845f, 0.925f, 0.99f, 1.05f, 1.1f, 1.14f, 1.165f, 1.175f, 1.165f, 1.16f, 1.125f, 1.095f, 1.055f, 1.025f, 1.01f, 0.995f, 0.99f, 0.995f, 1.01f, 1.025f, 1.055f, 1.095f, 1.145f, 1.16f, 1.17f, 1.175f, 1.17f, 1.16f, 1.125f, 1.075f, 1.015f, 0.945f, 0.885f, 0.755f, 0.63f, 0.735f, 0.845f, 0.945f, 1.015f, 1.075f, 1.125f, 1.16f, 1.19f, 0.9f, 1.19f, 1.185f, 1.145f, 1.115f, 1.075f, 1.05f, 1.03f, 1.015f, 1.015f, 1.015f, 1.03f, 1.05f, 1.075f, 1.115f, 1.145f, 1.17f, 1.19f, 1.175f, 1.165f, 1.14f, 1.1f, 1.05f, 0.99f, 0.925f, 0.825f, 0.715f)

private val clamShellRadii = floatArrayOf(0.975f, 0.975f, 0.945f, 0.905f, 0.905f, 0.895f, 0.89f, 0.895f, 0.89f, 0.865f, 0.85f, 0.82f, 0.775f, 0.74f, 0.715f, 0.695f, 0.685f, 0.675f, 0.67f, 0.675f, 0.685f, 0.695f, 0.715f, 0.74f, 0.775f, 0.82f, 0.85f, 0.895f, 0.905f, 0.92f, 0.905f, 0.92f, 0.91f, 0.925f, 0.965f, 0.995f, 0.99f, 0.995f, 0.965f, 0.945f, 0.93f, 0.92f, 0.915f, 0.92f, 0.92f, 0.895f, 0.875f, 0.845f, 0.8f, 0.765f, 0.735f, 0.715f, 0.705f, 0.695f, 0.695f, 0.695f, 0.705f, 0.715f, 0.735f, 0.765f, 0.8f, 0.845f, 0.875f, 0.895f, 0.905f, 0.895f, 0.89f, 0.895f, 0.905f, 0.925f, 0.945f, 0.975f)

private val cookie12Radii = floatArrayOf(0.975f, 0.955f, 0.905f, 0.88f, 0.905f, 0.96f, 0.96f, 0.96f, 0.905f, 0.895f, 0.905f, 0.96f, 0.96f, 0.96f, 0.905f, 0.88f, 0.905f, 0.955f, 0.975f, 0.955f, 0.905f, 0.88f, 0.905f, 0.96f, 0.985f, 0.965f, 0.92f, 0.895f, 0.92f, 0.96f, 0.985f, 0.985f, 0.93f, 0.905f, 0.925f, 0.975f, 0.99f, 0.975f, 0.925f, 0.905f, 0.93f, 0.985f, 0.985f, 0.99f, 0.93f, 0.92f, 0.93f, 0.99f, 0.985f, 0.985f, 0.93f, 0.905f, 0.925f, 0.975f, 0.99f, 0.975f, 0.925f, 0.905f, 0.93f, 0.985f, 0.985f, 0.96f, 0.92f, 0.895f, 0.92f, 0.965f, 0.985f, 0.96f, 0.905f, 0.88f, 0.905f, 0.955f)

private val cookie9Radii = floatArrayOf(0.935f, 0.895f, 0.885f, 0.905f, 0.93f, 0.96f, 0.96f, 0.96f, 0.95f, 0.895f, 0.89f, 0.895f, 0.94f, 0.96f, 0.97f, 0.965f, 0.945f, 0.915f, 0.895f, 0.915f, 0.945f, 0.965f, 0.99f, 0.975f, 0.94f, 0.92f, 0.905f, 0.92f, 0.95f, 0.965f, 0.985f, 0.985f, 0.95f, 0.925f, 0.905f, 0.915f, 0.95f, 0.975f, 0.985f, 0.97f, 0.95f, 0.92f, 0.89f, 0.92f, 0.955f, 0.98f, 0.98f, 0.965f, 0.94f, 0.895f, 0.885f, 0.905f, 0.945f, 0.975f, 0.975f, 0.955f, 0.925f, 0.89f, 0.885f, 0.895f, 0.94f, 0.96f, 0.955f, 0.95f, 0.93f, 0.895f, 0.87f, 0.895f, 0.93f, 0.965f, 0.965f, 0.955f)


private val softCookieRadii = floatArrayOf(
    0.99f, 0.995f, 0.98f, 0.965f, 0.95f, 0.94f, 0.94f, 0.925f, 0.93f, 0.92f, 0.95f, 0.96f, 0.985f, 0.985f, 0.99f, 0.985f, 0.985f, 0.975f, 0.95f, 0.955f, 0.945f, 0.945f, 0.93f, 0.94f, 0.945f, 0.965f, 0.98f, 0.98f, 1.01f, 0.995f, 1.005f, 0.985f, 0.99f, 0.965f, 0.965f, 0.955f, 0.95f, 0.955f, 0.965f, 0.965f, 0.99f, 1.005f, 1.005f, 1.015f, 1.015f, 1.005f, 0.985f, 0.99f, 0.96f, 0.96f, 0.95f, 0.965f, 0.965f, 0.975f, 0.975f, 0.995f, 1.01f, 1.005f, 1.015f, 1.005f, 0.985f, 0.965f, 0.955f, 0.95f, 0.95f, 0.94f, 0.94f, 0.94f, 0.965f, 0.97f, 0.985f, 0.995f
)



private val clover4Radii = floatArrayOf(
    0.815f, 0.855f, 0.905f, 0.945f, 0.99f, 1.03f, 1.055f, 1.065f, 1.085f, 1.09f, 1.085f, 1.065f, 1.055f, 1.03f, 0.99f, 0.945f, 0.905f, 0.855f, 0.815f, 0.855f, 0.905f, 0.945f, 0.99f, 1.03f, 1.055f, 1.09f, 1.085f, 1.09f, 1.105f, 1.09f, 1.075f, 1.05f, 1.015f, 0.965f, 0.925f, 0.875f, 0.83f, 0.875f, 0.925f, 0.965f, 1.015f, 1.05f, 1.075f, 1.1f, 1.11f, 1.12f, 1.11f, 1.1f, 1.075f, 1.05f, 1.015f, 0.965f, 0.925f, 0.875f, 0.83f, 0.875f, 0.925f, 0.965f, 1.015f, 1.05f, 1.075f, 1.09f, 1.105f, 1.09f, 1.085f, 1.09f, 1.055f, 1.03f, 0.99f, 0.945f, 0.905f, 0.855f
)

private val cookie4Radii = floatArrayOf(
    0.75f, 0.775f, 0.805f, 0.86f, 0.905f, 0.96f, 0.985f, 1.015f, 1.035f, 1.035f, 1.035f, 1.015f, 0.985f, 0.96f, 0.905f, 0.86f, 0.805f, 0.775f, 0.75f, 0.775f, 0.805f, 0.84f, 0.905f, 0.96f, 1.005f, 1.03f, 1.045f, 1.065f, 1.045f, 1.04f, 1.005f, 0.985f, 0.93f, 0.88f, 0.825f, 0.795f, 0.775f, 0.795f, 0.805f, 0.86f, 0.93f, 0.985f, 1.02f, 1.04f, 1.06f, 1.065f, 1.06f, 1.04f, 1.02f, 0.985f, 0.93f, 0.86f, 0.805f, 0.795f, 0.775f, 0.795f, 0.825f, 0.88f, 0.93f, 0.985f, 1.005f, 1.04f, 1.045f, 1.065f, 1.045f, 1.03f, 1.005f, 0.96f, 0.905f, 0.84f, 0.805f, 0.775f
)

private val cookie6Radii = floatArrayOf(
    0.855f, 0.875f, 0.885f, 0.925f, 0.93f, 0.94f, 0.94f, 0.94f, 0.93f, 0.92f, 0.89f, 0.87f, 0.845f, 0.85f, 0.885f, 0.905f, 0.925f, 0.935f, 0.95f, 0.935f, 0.945f, 0.925f, 0.905f, 0.875f, 0.86f, 0.87f, 0.905f, 0.92f, 0.95f, 0.96f, 0.96f, 0.96f, 0.95f, 0.945f, 0.905f, 0.895f, 0.87f, 0.875f, 0.905f, 0.945f, 0.95f, 0.96f, 0.985f, 0.965f, 0.955f, 0.95f, 0.92f, 0.895f, 0.87f, 0.88f, 0.91f, 0.945f, 0.965f, 0.955f, 0.975f, 0.955f, 0.945f, 0.925f, 0.905f, 0.875f, 0.87f, 0.89f, 0.905f, 0.92f, 0.95f, 0.96f, 0.96f, 0.94f, 0.93f, 0.925f, 0.885f, 0.855f
)

private val arrowRadii = floatArrayOf(
    0.775f, 0.835f, 0.885f, 0.945f, 1.015f, 1.05f, 1.075f, 1.09f, 1.085f, 1.065f, 1.035f, 0.965f, 0.89f, 0.83f, 0.78f, 0.76f, 0.725f, 0.695f, 0.695f, 0.715f, 0.725f, 0.76f, 0.8f, 0.85f, 0.915f, 0.965f, 1.01f, 1.035f, 1.045f, 1.04f, 1.02f, 0.985f, 0.95f, 0.88f, 0.805f, 0.755f, 0.71f, 0.675f, 0.665f, 0.635f, 0.63f, 0.61f, 0.615f, 0.615f, 0.615f, 0.64f, 0.64f, 0.675f, 0.7f, 0.72f, 0.76f, 0.8f, 0.825f, 0.855f, 0.87f, 0.875f, 0.865f, 0.84f, 0.8f, 0.785f, 0.745f, 0.725f, 0.705f, 0.695f, 0.67f, 0.675f, 0.665f, 0.675f, 0.675f, 0.695f, 0.725f, 0.755f
)

private fun sampleRadii(radii: FloatArray, angleRad: Float): Float {
    val tau = 2f * PI.toFloat()
    var normAngle = angleRad % tau
    if (normAngle < 0f) normAngle += tau
    val fraction = normAngle / tau * radii.size
    val idx0 = fraction.toInt() % radii.size
    val idx1 = (idx0 + 1) % radii.size
    val t = fraction - fraction.toInt()
    return radii[idx0] * (1f - t) + radii[idx1] * t
}

@Composable
fun MaterialExpressiveShape(
    shape: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF2A385),
    spinAngleRad: Float = 0f,
    content: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val reach = min(size.width, size.height) / 2f * 0.95f

            val path = Path()
            val steps = 180
            var first = true

            for (i in 0..steps) {
                val theta = (i.toFloat() / steps) * (2f * PI.toFloat())
                val effTheta = theta - spinAngleRad

                val r = when (shape) {
                    "softCookie" -> reach * sampleRadii(softCookieRadii, effTheta)
                    "cookie9" -> reach * sampleRadii(cookie9Radii, effTheta)
                    "clover4" -> reach * sampleRadii(clover4Radii, effTheta)
                    "cookie4" -> reach * sampleRadii(cookie4Radii, effTheta)
                    "cookie6" -> reach * sampleRadii(cookie6Radii, effTheta)
                    "arrowRight" -> reach * sampleRadii(arrowRadii, effTheta)
                    "arrowLeft" -> reach * sampleRadii(arrowRadii, effTheta + PI.toFloat())
                    "heart" -> reach * sampleRadii(heartRadii, effTheta)
                    "bun" -> reach * sampleRadii(bunRadii, effTheta)
                    "clamShell" -> reach * sampleRadii(clamShellRadii, effTheta)
                    "cookie12" -> reach * sampleRadii(cookie12Radii, effTheta)
                    "sunny" -> {
                        val lobe = (0.5f + 0.5f * cos(8f * effTheta)).pow(2.2f)
                        reach * (1f - 0.14f + 0.14f * lobe)
                    }
                    "softBurst" -> {
                        val lobe = (0.5f + 0.5f * cos(10f * effTheta)).pow(1.5f)
                        reach * (1f - 0.16f + 0.16f * lobe)
                    }
                    "flower" -> {
                        val lobe = (0.5f + 0.5f * cos(8f * effTheta)).pow(0.45f)
                        reach * (1f - 0.32f + 0.32f * lobe)
                    }
                    "ghostish" -> {
                        val lobe = (0.5f + 0.5f * cos(3f * (effTheta - PI.toFloat() / 2f))).pow(0.7f)
                        val rScale = if (sin(effTheta) < 0f) 0.95f else (0.78f + 0.22f * lobe)
                        reach * rScale
                    }
                    else -> reach
                }

                val x = cx + r * cos(theta)
                val y = cy + r * sin(theta)

                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
            }
            path.close()

            drawPath(path = path, color = color, style = Fill)
        }

        content?.invoke()
    }
}

/**
 * Creates a wavy "cloud-like" path matching rounded_image.frag on Desktop:
 * curve = middle + amplitude * sin(waves * turn - phase)
 */
fun buildSquigglePath(
    width: Float,
    height: Float,
    waves: Int = 20,
    amplitude: Float,
    offsetRadius: Float = 0f,
    phase: Float = 0f
): Path {
    val cx = width / 2f
    val cy = height / 2f
    val middle = min(width, height) / 2f - amplitude - 2f + offsetRadius

    val path = Path()
    val steps = 360
    var first = true

    for (i in 0..steps) {
        val turn = (i.toFloat() / steps) * (2f * PI.toFloat())
        // Gentle sinusoidal wave (soft cloud scallops)
        val curve = middle + amplitude * sin(waves * turn - phase)
        val x = cx + curve * cos(turn)
        val y = cy + curve * sin(turn)

        if (first) {
            path.moveTo(x, y)
            first = false
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    return path
}

/**
 * Artist Squiggle Avatar Ring matching Swiftify Desktop:
 * A gentle, cloud-like wavy outer stroke (waves = 20, amplitude = 2.0.dp),
 * with the image clipped inside along the exact same wave path!
 */
@Composable
fun SquiggleAvatarRing(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFF2A385),
    waves: Int = 20,
    amplitude: Dp = 2.2.dp,
    strokeWidth: Dp = 2.5.dp,
    phase: Float = 0f
) {
    Canvas(modifier = modifier) {
        val strokePx = strokeWidth.toPx()
        val ampPx = amplitude.toPx()
        val path = buildSquigglePath(
            width = size.width,
            height = size.height,
            waves = waves,
            amplitude = ampPx,
            offsetRadius = -strokePx / 2f,
            phase = phase
        )

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

class SquiggleShape(
    private val waves: Int = 20,
    private val amplitudeDp: Float = 2.0f,
    private val offsetDp: Float = 0f,
    private val phase: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val ampPx = with(density) { amplitudeDp.dp.toPx() }
        val offPx = with(density) { offsetDp.dp.toPx() }
        val path = buildSquigglePath(
            width = size.width,
            height = size.height,
            waves = waves,
            amplitude = ampPx,
            offsetRadius = offPx,
            phase = phase
        )
        return Outline.Generic(path)
    }
}

/**
 * Complete Squiggle Artist Avatar matching Desktop:
 * Renders the wavy outer stroke and clips the inner avatar image to the exact
 * same cloud-like wave shape, leaving the authentic gap between the stroke and image.
 */
@Composable
fun SquiggleAvatar(
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    ringColor: Color = Color(0xFFF2A385),
    waves: Int = 20,
    amplitude: Dp = 2.0.dp,
    strokeWidth: Dp = 2.4.dp,
    gap: Dp = 3.2.dp,
    phase: Float = 0f
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer Squiggle Ring
        SquiggleAvatarRing(
            modifier = Modifier.fillMaxSize(),
            color = ringColor,
            waves = waves,
            amplitude = amplitude,
            strokeWidth = strokeWidth,
            phase = phase
        )

        // Inner Avatar image clipped to the exact wavy cloud outline
        val innerOffset = -(strokeWidth.value + gap.value)
        coil.compose.AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .clip(
                    SquiggleShape(
                        waves = waves,
                        amplitudeDp = amplitude.value,
                        offsetDp = innerOffset,
                        phase = phase
                    )
                ),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Desktop-authentic ShapeBadge (MaterialShapeWrappedMaterialSymbol):
 * Organic Material 3 Expressive shape with monochrome vector glyph inside.
 */
@Composable
fun ShapeBadge(
    shape: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    shapeAlpha: Float = 0.28f,
    glyphAlpha: Float = 0.90f,
    tint: Color = Color(0xFFF2A385)
) {
    MaterialExpressiveShape(
        shape = shape,
        color = tint.copy(alpha = shapeAlpha),
        modifier = modifier.size(size)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint.copy(alpha = glyphAlpha),
            modifier = Modifier.size(iconSize)
        )
    }
}
