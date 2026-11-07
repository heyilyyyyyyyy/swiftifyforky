package org.swiftify.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Material 3 Expressive Wavy Progress Slider.
 * Emulates Swiftify Desktop's `wavy_progress.frag` and `SeekBar.qml`.
 * Waves oscillate dynamically when music is playing, and flattens out or pauses smoothly.
 */
@Composable
fun WavyProgressSlider(
    progressMs: Long,
    totalMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    activeColor: Color = Color(0xFF1ED760),
    inactiveColor: Color = Color(0x33FFFFFF),
    waveAmplitude: Dp = 3.dp,
    waveLength: Dp = 22.dp,
    strokeWidth: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val duration = totalMs.coerceAtLeast(1L).toFloat()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    // Wave phase animation for playing state
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
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
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val activeWidth = width * currentFraction
        val ampPx = waveAmplitude.toPx()
        val lambdaPx = waveLength.toPx()
        val strokePx = strokeWidth.toPx()

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
            val path = Path()
            var x = 0f
            var first = true

            while (x <= activeWidth) {
                // If not playing or dragging, reduce amplitude smoothly
                val currentAmp = if (isPlaying && !isDragging) ampPx else 0f
                val y = midY + sin((x / lambdaPx) * 2f * PI.toFloat() + phase) * currentAmp

                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                x += 3f
            }

            drawPath(
                path = path,
                color = activeColor,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 3. Draw Thumb (Pill / Circle with glow)
            val thumbX = activeWidth
            val currentAmp = if (isPlaying && !isDragging) ampPx else 0f
            val thumbY = midY + sin((thumbX / lambdaPx) * 2f * PI.toFloat() + phase) * currentAmp
            val thumbRadius = if (isDragging) 8.dp.toPx() else 6.dp.toPx()

            // Outer soft glow
            drawCircle(
                color = activeColor.copy(alpha = 0.28f),
                radius = thumbRadius + 5.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(thumbX, thumbY)
            )
            // Inner solid thumb
            drawCircle(
                color = activeColor,
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
