package org.swiftify.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private const val TAU = 2f * PI.toFloat()

/**
 * Material 3 Expressive Wavy Progress Slider.
 * Emulates Swiftify Desktop's `wavy_progress.frag` and `SeekBar.qml`.
 * Waves oscillate dynamically when music is playing, and flatten out or pauses smoothly.
 */
@Composable
fun WavyProgressSlider(
    progressMs: Long,
    totalMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    activeColor: Color = Color.Unspecified,
    inactiveColor: Color = Color(0x33FFFFFF),
    waveAmplitude: Dp = 3.dp,
    waveLength: Dp = 22.dp,
    strokeWidth: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val resolvedActiveColor = if (activeColor.isSpecified) activeColor else MaterialTheme.colorScheme.primary
    val duration = totalMs.coerceAtLeast(1L).toFloat()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // The wave phase drives a translation of a pre-built path, so it is only
    // read inside the draw block: a paused or dragged slider never redraws,
    // and while playing each frame costs a cheap path translation instead of
    // rebuilding the whole sine path on the UI thread.
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val animatedPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = TAU,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        (progressMs.toFloat() / duration).coerceIn(0f, 1f)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clipToBounds()
            .pointerInput(totalMs) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((frac * duration).toLong())
                }
            }
            .pointerInput(totalMs) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek((dragFraction * duration).toLong())
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            }
            .drawWithCache {
                val lambdaPx = waveLength.toPx()
                val ampPx = waveAmplitude.toPx()
                val midY = size.height / 2f
                // one extra wavelength so the path stays covered while the
                // phase slides it left
                val span = size.width + lambdaPx
                val step = (lambdaPx / 16f).coerceAtLeast(3f)
                val wavePath = Path()
                var x = 0f
                while (x <= span) {
                    val y = midY + sin((x / lambdaPx) * TAU) * ampPx
                    if (x == 0f) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                    x += step
                }
                onDrawBehind {
                    val width = size.width
                    val height = size.height
                    val midY = height / 2f
                    val activeWidth = width * currentFraction
                    val strokePx = strokeWidth.toPx()
                    val lambdaPx = waveLength.toPx()
                    val ampPx = waveAmplitude.toPx()
                    val wavy = isPlaying && !isDragging
                    val phase = if (wavy) animatedPhase else 0f

                    // 1. Draw inactive line (unplayed part) with soft rounded cap
                    if (activeWidth < width) {
                        drawLine(
                            color = inactiveColor,
                            start = androidx.compose.ui.geometry.Offset(activeWidth, midY),
                            end = androidx.compose.ui.geometry.Offset(width, midY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                    }

                    // 2. Draw active wavy path (played part)
                    if (activeWidth > 0f) {
                        clipRect(right = activeWidth) {
                            if (wavy) {
                                val offset = -(phase / TAU) * lambdaPx
                                translate(left = offset) {
                                    drawPath(
                                        path = wavePath,
                                        color = resolvedActiveColor,
                                        style = Stroke(
                                            width = strokePx,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            } else {
                                drawLine(
                                    color = resolvedActiveColor,
                                    start = androidx.compose.ui.geometry.Offset(0f, midY),
                                    end = androidx.compose.ui.geometry.Offset(activeWidth, midY),
                                    strokeWidth = strokePx,
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        // 3. Draw Thumb (Pill / Circle with glow)
                        val thumbX = activeWidth
                        val thumbY = midY + if (wavy) {
                            sin((thumbX / lambdaPx) * TAU + phase) * ampPx
                        } else {
                            0f
                        }
                        val thumbRadius = if (isDragging) 8.dp.toPx() else 6.dp.toPx()

                        // Outer soft glow
                        drawCircle(
                            color = resolvedActiveColor.copy(alpha = 0.28f),
                            radius = thumbRadius + 5.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(thumbX, thumbY)
                        )
                        // Inner solid thumb
                        drawCircle(
                            color = resolvedActiveColor,
                            radius = thumbRadius,
                            center = androidx.compose.ui.geometry.Offset(thumbX, thumbY)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = thumbRadius * 0.45f,
                            center = androidx.compose.ui.geometry.Offset(thumbX, thumbY)
                        )
                    }
                }
            }
    ) {}
}
