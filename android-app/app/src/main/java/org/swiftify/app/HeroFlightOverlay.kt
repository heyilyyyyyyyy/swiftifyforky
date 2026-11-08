package org.swiftify.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun HeroFlightOverlay(
    flightData: HeroFlightData,
    targetRect: Rect?,
    onFlightFinished: () -> Unit
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(flightData) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.74f,
                stiffness = 380f
            )
        )
        onFlightFinished()
    }

    val currentProgress = progress.value.coerceIn(0f, 1f)
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val targetSizePx = with(density) { 150.dp.toPx() }
    val defaultTargetY = with(density) { 145.dp.toPx() }

    val destX = targetRect?.left ?: ((screenWidthPx - targetSizePx) / 2f)
    val destY = targetRect?.top ?: defaultTargetY
    val destSize = targetRect?.width ?: targetSizePx

    val currentX = flightData.fromRect.left + (destX - flightData.fromRect.left) * currentProgress
    val currentY = flightData.fromRect.top + (destY - flightData.fromRect.top) * currentProgress
    val currentSize = flightData.fromRect.width + (destSize - flightData.fromRect.width) * currentProgress
    val currentSizeDp = with(density) { currentSize.toDp() }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                .size(currentSizeDp)
                .shadow(
                    elevation = (14 * (1f - currentProgress * 0.35f)).dp,
                    shape = CircleShape,
                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                )
        ) {
            SquiggleAvatar(
                imageUrl = flightData.imageUrl,
                contentDescription = flightData.name,
                modifier = Modifier.fillMaxSize(),
                ringColor = MaterialTheme.colorScheme.primary,
                waves = 20,
                amplitude = (1.3 + (1.4 - 1.3) * currentProgress).dp,
                strokeWidth = (2.0 + (2.4 - 2.0) * currentProgress).dp,
                gap = (4.0 + (4.2 - 4.0) * currentProgress).dp
            )
        }
    }
}
