package org.swiftify.app.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

object SwiftifyMotion {
    // Desktop parity: Appearance.curves
    val ExpressiveFastSpatial = CubicBezierEasing(0.42f, 1.67f, 0.21f, 0.90f)
    val ExpressiveDefaultSpatial = CubicBezierEasing(0.38f, 1.21f, 0.22f, 1.00f)
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    // Qt's Easing.OutCubic, used by PageHost's page fade
    val OutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

    // Durations
    const val DurationColorMs = 120
    const val DurationMotionFastMs = 200
    const val DurationMotionDefaultMs = 320
    const val DurationPaletteMs = 450

    fun <T> colorSpec() = tween<T>(durationMillis = DurationColorMs, easing = EmphasizedDecel)
    fun <T> spatialSpec() = tween<T>(durationMillis = DurationMotionDefaultMs, easing = ExpressiveDefaultSpatial)
    fun <T> fastSpatialSpec() = tween<T>(durationMillis = DurationMotionFastMs, easing = ExpressiveFastSpatial)
}
