package org.swiftify.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Builds a wavy scalloped pill path matching Swiftify Desktop's `wavy_pill.frag`.
 * The base pill is inset by `amplitude + 1f` so the waves can fully expand to the edges without clipping.
 */
fun buildWavyPillPath(
    width: Float,
    height: Float,
    amplitude: Float = 3.0f,
    wavelength: Float = 15f,
    phase: Float = 0f
): Path {
    val path = Path()
    if (width <= 0f || height <= 0f) return path

    val margin = amplitude + 1f
    val availH = (height - 2f * margin).coerceAtLeast(0f)
    val r = availH / 2f
    val availW = (width - 2f * margin).coerceAtLeast(0f)
    val straightW = (availW - 2f * r).coerceAtLeast(0f)

    if (availH <= 0f || availW <= 0f) return path

    // Symmetrical scallop allocation:
    // Ensure an integer number of wave cycles on top/bottom straight edges and left/right curved ends.
    // Each section starts and ends at wave = 0 with zero derivative, completely eliminating any cut-offs or seams!
    val nStraight = max(1, kotlin.math.round(straightW / max(wavelength, 1f)).toInt())
    val nCap = max(1, kotlin.math.round((PI.toFloat() * r) / max(wavelength, 1f)).toInt())

    val stepsPerSection = 64

    // 1. Top straight edge: left to right (y = margin, wave goes upwards: -ny)
    for (i in 0..stepsPerSection) {
        val u = i.toFloat() / stepsPerSection
        val s = u * straightW
        val wave = amplitude * (0.5f - 0.5f * cos(2f * PI.toFloat() * nStraight * u - phase))
        val px = margin + r + s
        val py = margin - wave
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }

    // 2. Right cap: top to bottom (-PI/2 to PI/2)
    for (i in 0..stepsPerSection) {
        val v = i.toFloat() / stepsPerSection
        val angle = -PI.toFloat() / 2f + v * PI.toFloat()
        val wave = amplitude * (0.5f - 0.5f * cos(2f * PI.toFloat() * nCap * v - phase))
        val px = (width - margin - r) + (r + wave) * cos(angle)
        val py = (margin + r) + (r + wave) * sin(angle)
        path.lineTo(px, py)
    }

    // 3. Bottom straight edge: right to left (y = height - margin, wave goes downwards: +ny)
    for (i in 0..stepsPerSection) {
        val u = i.toFloat() / stepsPerSection
        val s = u * straightW
        val wave = amplitude * (0.5f - 0.5f * cos(2f * PI.toFloat() * nStraight * u - phase))
        val px = (width - margin - r) - s
        val py = (height - margin) + wave
        path.lineTo(px, py)
    }

    // 4. Left cap: bottom to top (PI/2 to 3*PI/2)
    for (i in 0..stepsPerSection) {
        val v = i.toFloat() / stepsPerSection
        val angle = PI.toFloat() / 2f + v * PI.toFloat()
        val wave = amplitude * (0.5f - 0.5f * cos(2f * PI.toFloat() * nCap * v - phase))
        val px = (margin + r) + (r + wave) * cos(angle)
        val py = (margin + r) + (r + wave) * sin(angle)
        path.lineTo(px, py)
    }

    path.close()
    return path
}

class WavyPillShape(
    private val amplitudeDp: Float = 2.8f,
    private val wavelengthDp: Float = 14f,
    private val phase: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val ampPx = with(density) { amplitudeDp.dp.toPx() }
        val wavePx = with(density) { wavelengthDp.dp.toPx() }
        val path = buildWavyPillPath(size.width, size.height, ampPx, wavePx, phase)
        return Outline.Generic(path)
    }
}

@Composable
fun WavyPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFFF2A385),
    contentColor: Color = Color(0xFF13100E),
    enabled: Boolean = true,
    amplitudeDp: Float = 2.8f,
    wavelengthDp: Float = 14f
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "wavyButtonScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .drawBehind {
                val effectiveAmp = if (isPressed) amplitudeDp * 1.4f else amplitudeDp
                val ampPx = effectiveAmp.dp.toPx()
                val wavePx = wavelengthDp.dp.toPx()
                val path = buildWavyPillPath(
                    width = size.width,
                    height = size.height,
                    amplitude = ampPx,
                    wavelength = wavePx,
                    phase = 0f
                )
                drawPath(
                    path = path,
                    color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.4f)
                )
            }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StyledBackButton(
    onBack: () -> Unit = {},
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val action = onClick ?: onBack
    val currentAction by androidx.compose.runtime.rememberUpdatedState(action)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "styledBackScale"
    )

    Box(
        modifier = modifier
            .size(38.dp)
            .scale(scale)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(Color(0xFF261F1C))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = { currentAction() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
