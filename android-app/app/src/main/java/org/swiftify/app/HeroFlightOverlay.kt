package org.swiftify.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

@Composable
fun HeroFlightOverlay(
    flightData: HeroFlightData,
    targetRect: Rect?,
    clipBottomPx: Float = Float.MAX_VALUE,
    onFlightFinished: () -> Unit
) {
    val progress = remember(flightData) { Animatable(0f) }
    val latestTargetRect by rememberUpdatedState(targetRect)

    LaunchedEffect(flightData) {
        // the artist page reports its hero a frame or two after it enters;
        // waiting for it keeps the flyer from jumping to the fallback spot
        withTimeoutOrNull(250) {
            snapshotFlow { latestTargetRect }.filterNotNull().first()
        }
        progress.animateTo(
            targetValue = 1f,
            // springs past the hero's spot and back, like the desktop flight.
            // The default visibility threshold (1% of the travel) is ~14px
            // here, so the spring would stop visibly short of the hero and the
            // handover would snap. 0.05% lands it within a pixel.
            animationSpec = spring(
                dampingRatio = 0.60f,
                stiffness = 300f,
                visibilityThreshold = 0.0005f
            )
        )
        onFlightFinished()
    }

    // let the spring overshoot land and settle, instead of clamping it flat
    val currentProgress = progress.value
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (clipBottomPx != Float.MAX_VALUE) {
                    Modifier
                        .height(with(density) { clipBottomPx.toDp() })
                        .clipToBounds()
                } else {
                    Modifier
                }
            )
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                .size(currentSizeDp)
        ) {
            SquiggleAvatar(
                imageUrl = flightData.imageUrl,
                contentDescription = flightData.name,
                modifier = Modifier.fillMaxSize(),
                ringColor = MaterialTheme.colorScheme.primary,
                waves = 20,
                amplitude = (1.3 + (1.4 - 1.3) * currentProgress).dp,
                strokeWidth = (2.0 + (2.4 - 2.0) * currentProgress).dp,
                gap = (4.0 + (4.2 - 4.0) * currentProgress).dp,
                continuousRotation = false
            )
        }
    }
}
