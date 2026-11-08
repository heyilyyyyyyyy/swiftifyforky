package org.swiftify.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.core.graphics.PathParser
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.*

// Material 3 Expressive shapes are drawn from the same cubic outlines as the
// desktop shape atlas (ShapeOutlines.kt). Names follow the desktop library;
// arrowLeft/arrowRight/arrowDown are the base "arrow" shape turned in place,
// exactly as TransportButton does with spinAngle on the desktop.
private val shapePathCache = HashMap<String, android.graphics.Path>()

private fun baseShapeName(shape: String): String = when (shape) {
    "arrowLeft", "arrowRight", "arrowDown", "arrowUp" -> "arrow"
    else -> shape
}

private fun shapeRotationDegrees(shape: String): Float = when (shape) {
    "arrowLeft", "arrow" -> -90f
    "arrowRight" -> 90f
    "arrowDown" -> 180f
    else -> 0f
}

private fun baseOutline(shape: String): android.graphics.Path? {
    val name = baseShapeName(shape)
    shapePathCache[name]?.let { return it }
    val data = MATERIAL_SHAPE_OUTLINES[name] ?: return null
    val parsed = PathParser.createPathFromPathData(data) ?: return null
    shapePathCache[name] = parsed
    return parsed
}

/// Radius at [theta] as a fraction of the unit-square side, from the same
/// sampled outlines the desktop morphs between.
private fun shapeRadiusFraction(shape: String, theta: Float): Float? {
    val radii = MATERIAL_SHAPE_RADII[shape] ?: return null
    val tau = 2f * PI.toFloat()
    var angle = theta % tau
    if (angle < 0f) angle += tau
    val pos = angle / tau * radii.size
    val index = pos.toInt() % radii.size
    val next = (index + 1) % radii.size
    val t = pos - pos.toInt()
    return radii[index] * (1f - t) + radii[next] * t
}

/// Polar fallback for the few app-only shapes that have no cubic outline.
private fun polarRadiusFraction(shape: String, theta: Float): Float = when (shape) {
    "squircle" -> {
        val c = abs(cos(theta)).pow(4f)
        val s = abs(sin(theta)).pow(4f)
        0.5f / (c + s).pow(0.25f)
    }
    else -> 0.5f
}

private fun radiusFraction(shape: String, theta: Float): Float =
    shapeRadiusFraction(shape, theta) ?: polarRadiusFraction(shape, theta)

/**
 * Builds an authentic Material 3 Expressive shape from its normalized cubic
 * outline. The outline is scaled to [extent] of the shortest side and centred,
 * so it matches MaterialShapeItem's `side = extent * (shortest - 1.5)`.
 */
fun buildExpressivePath(shape: String, width: Float, height: Float, extent: Float = 0.95f): Path {
    val side = (min(width, height) - 1.5f).coerceAtLeast(0f) * extent
    val cx = width / 2f
    val cy = height / 2f
    val base = baseOutline(shape)
    if (base != null) {
        val matrix = android.graphics.Matrix()
        matrix.setScale(side, side)
        val rotation = shapeRotationDegrees(shape)
        if (rotation != 0f) matrix.postRotate(rotation, side / 2f, side / 2f)
        matrix.postTranslate(cx - side / 2f, cy - side / 2f)
        val transformed = android.graphics.Path(base)
        transformed.transform(matrix)
        return transformed.asComposePath()
    }

    val path = Path()
    val steps = 360
    for (i in 0..steps) {
        val theta = (i.toFloat() / steps) * (2f * PI.toFloat())
        val r = radiusFraction(shape, theta) * side
        val x = cx + r * cos(theta)
        val y = cy + r * sin(theta)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * Morphs between two shapes by interpolating their radius profiles, which is
 * how the desktop's shape distance fields blend: the pause cookie grows its
 * scallops into the playing cookie without changing its centre.
 */
fun buildMorphedExpressivePath(
    fromShape: String,
    toShape: String,
    morphProgress: Float,
    width: Float,
    height: Float,
    extent: Float = 0.95f
): Path {
    val side = (min(width, height) - 1.5f).coerceAtLeast(0f) * extent
    val cx = width / 2f
    val cy = height / 2f
    val progress = morphProgress.coerceIn(0f, 1f)

    val path = Path()
    // 2° steps are already smooth at button sizes and halve the trig work
    // that used to run on the UI thread while the morph animates
    val steps = 180
    for (i in 0..steps) {
        val theta = (i.toFloat() / steps) * (2f * PI.toFloat())
        val from = radiusFraction(fromShape, theta)
        val to = radiusFraction(toShape, theta)
        val r = (from + (to - from) * progress) * side
        val x = cx + r * cos(theta)
        val y = cy + r * sin(theta)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private data class BurstSpec(
    val dx: Float,
    val dy: Float,
    val reach: Float,
    val size: Float,
    val shape: String,
    val toneIndex: Int,
    val turnDegrees: Float
)

@Composable
fun ShapeBurstEffect(
    triggerKey: Long,
    primaryColor: Color,
    secondaryColor: Color,
    tertiaryColor: Color,
    modifier: Modifier = Modifier,
    count: Int = 18
) {
    if (triggerKey == 0L) return

    val anim = remember(triggerKey) { Animatable(0f) }
    LaunchedEffect(triggerKey) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 850, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
        )
    }

    val t = anim.value
    if (t in 0.001f..0.999f) {
        val particles = remember(triggerKey) {
            val shapes = listOf("cookie9", "sunny", "cookie6", "softBurst", "heart", "clover4", "cookie4")
            val rnd = java.util.Random(triggerKey)
            List(count) { index ->
                val angle = (2f * PI.toFloat() * (index + 0.6f * rnd.nextFloat()) / count) - (PI.toFloat() / 2f)
                BurstSpec(
                    dx = cos(angle),
                    dy = sin(angle),
                    reach = 140f + 180f * rnd.nextFloat(),
                    size = 28f + 24f * rnd.nextFloat(),
                    shape = shapes[rnd.nextInt(shapes.size)],
                    toneIndex = rnd.nextInt(3),
                    turnDegrees = (if (rnd.nextBoolean()) -1f else 1f) * (200f + 400f * rnd.nextFloat())
                )
            }
        }

        val tones = listOf(primaryColor, tertiaryColor, secondaryColor)
        val travel = 1f - (1f - t).pow(2.5f)
        val scale = if (t < 0.22f) (0.3f + 0.7f * (t / 0.22f)) else (1f - 0.40f * ((t - 0.22f) / 0.78f))
        val alpha = if (t < 0.50f) 1f else (1f - (t - 0.50f) / 0.50f)

        Canvas(modifier = modifier) {
            val cx = size.width / 2f
            val cy = size.height / 2f

            for (p in particles) {
                val px = cx + p.dx * p.reach * travel
                val py = cy + p.dy * p.reach * travel + (24f * t * t)
                val pSize = p.size * scale
                val color = tones[p.toneIndex].copy(alpha = alpha.coerceIn(0f, 1f))

                val pPath = buildExpressivePath(p.shape, pSize, pSize)
                rotate(p.turnDegrees * t, pivot = Offset(px, py)) {
                    translate(left = px - pSize / 2f, top = py - pSize / 2f) {
                        drawPath(pPath, color = color, style = Fill)
                    }
                }
            }
        }
    }
}

/**
 * Authentic Desktop Swiftify Play Button:
 * Paused: soft 5-scallop cookie (softCookie).
 * Playing: springs & morphs into a 9-scallop cookie (cookie9) spinning continuously.
 * Glyph pops on every toggle, and starts throw out a material shape cookie burst!
 */
@Composable
fun SwiftifyPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 78.dp,
    iconSize: Dp = 40.dp,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onPrimaryColor: Color = MaterialTheme.colorScheme.onPrimary,
    secondaryColor: Color = MaterialTheme.colorScheme.secondary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val morphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 340f),
        label = "morphProgress"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "pressScale"
    )

    val shouldSpin = isPlaying || morphProgress > 0.05f
    val spinAngle = if (shouldSpin) {
        val infiniteTransition = rememberInfiniteTransition(label = "playSpin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spinAngle"
        )
        angle
    } else {
        0f
    }

    val glyphPop = remember { Animatable(1f) }
    var lastIsPlaying by remember { mutableStateOf(isPlaying) }
    var burstTrigger by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying) {
        if (lastIsPlaying != isPlaying) {
            lastIsPlaying = isPlaying
            if (isPlaying) {
                burstTrigger = System.currentTimeMillis()
            }
            glyphPop.snapTo(0.55f)
            glyphPop.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f)
            )
        }
    }

    Box(
        modifier = modifier
            .size(buttonSize)
            .scale(pressScale),
        contentAlignment = Alignment.Center
    ) {
        // Confetti shape burst on play with wide flight arena
        ShapeBurstEffect(
            triggerKey = burstTrigger,
            primaryColor = primaryColor,
            secondaryColor = secondaryColor,
            tertiaryColor = tertiaryColor,
            modifier = Modifier.size(buttonSize * 3.5f)
        )

        // Morphing & spinning cookie button body
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = spinAngle
                }
                // The glyph spins all the time while playing; building the
                // 360-point morph outline inside the draw lambda rebuilt it on
                // every frame. drawWithCache keeps the outline and only
                // rebuilds it when the morph or the size actually changes,
                // while the spin stays a cheap layer rotation.
                .drawWithCache {
                    val path = buildMorphedExpressivePath(
                        fromShape = "softCookie",
                        toShape = "cookie9",
                        morphProgress = morphProgress,
                        width = size.width,
                        height = size.height
                    )
                    onDrawBehind {
                        drawPath(path = path, color = primaryColor, style = Fill)
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                )
        ) {}

        // Inner popping Play/Pause glyph
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = onPrimaryColor,
            modifier = Modifier
                .size(iconSize)
                .scale(glyphPop.value)
        )
    }
}

@Composable
fun MaterialExpressiveShape(
    shape: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    spinAngleRad: Float = 0f,
    content: (@Composable () -> Unit)? = null
) {
    val resolvedColor = if (color.isSpecified) color else MaterialTheme.colorScheme.primary
    var cachedPath by remember { mutableStateOf<Path?>(null) }
    var lastWidth by remember { mutableFloatStateOf(0f) }
    var lastHeight by remember { mutableFloatStateOf(0f) }
    var lastShape by remember { mutableStateOf("") }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (cachedPath == null || lastWidth != size.width || lastHeight != size.height || lastShape != shape) {
                lastWidth = size.width
                lastHeight = size.height
                lastShape = shape
                cachedPath = buildExpressivePath(shape, size.width, size.height)
            }

            val p = cachedPath ?: return@Canvas
            if (spinAngleRad != 0f) {
                val degrees = (spinAngleRad * (180f / PI.toFloat()))
                rotate(degrees, pivot = center) {
                    drawPath(path = p, color = resolvedColor, style = Fill)
                }
            } else {
                drawPath(path = p, color = resolvedColor, style = Fill)
            }
        }

        content?.invoke()
    }
}

@Composable
fun MorphedExpressiveShape(
    fromShape: String,
    toShape: String,
    morphProgress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val path = buildMorphedExpressivePath(
            fromShape = fromShape,
            toShape = toShape,
            morphProgress = morphProgress,
            width = size.width,
            height = size.height
        )
        drawPath(path = path, color = color, style = androidx.compose.ui.graphics.drawscope.Fill)
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
    color: Color = Color.Unspecified,
    waves: Int = 20,
    amplitude: Dp = 2.2.dp,
    strokeWidth: Dp = 2.5.dp,
    phase: Float = 0f,
    continuousRotation: Boolean = true
) {
    val resolvedColor = if (color.isSpecified) color else MaterialTheme.colorScheme.primary
    val ringAngle = if (continuousRotation) {
        val infiniteTransition = rememberInfiniteTransition(label = "squiggleRingSpin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ringAngle"
        )
        angle
    } else {
        0f
    }

    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val ampPx = with(LocalDensity.current) { amplitude.toPx() }

    Canvas(
        modifier = modifier.graphicsLayer {
            if (continuousRotation) {
                rotationZ = ringAngle
            }
        }
    ) {
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
            color = resolvedColor,
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
    ringColor: Color = Color.Unspecified,
    waves: Int = 20,
    amplitude: Dp = 2.0.dp,
    strokeWidth: Dp = 2.4.dp,
    gap: Dp = 3.2.dp,
    phase: Float = 0f,
    continuousRotation: Boolean = true
) {
    val resolvedRingColor = if (ringColor.isSpecified) ringColor else MaterialTheme.colorScheme.primary
    val ringAngle = if (continuousRotation) {
        val infiniteTransition = rememberInfiniteTransition(label = "squiggleSpin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ringAngle"
        )
        angle
    } else {
        0f
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer Squiggle Ring (rotates with ringAngle)
        SquiggleAvatarRing(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (continuousRotation) rotationZ = ringAngle
                },
            color = resolvedRingColor,
            waves = waves,
            amplitude = amplitude,
            strokeWidth = strokeWidth,
            phase = phase,
            continuousRotation = false
        )

        // Inner Avatar: container rotates with ringAngle (so its SquiggleShape wavy dimples rotate in perfect sync with the ring)
        val innerOffset = -(strokeWidth.value + gap.value)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (continuousRotation) rotationZ = ringAngle
                }
                .clip(
                    SquiggleShape(
                        waves = waves,
                        amplitudeDp = amplitude.value,
                        offsetDp = innerOffset,
                        phase = phase
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl.isNotEmpty()) {
                coil.compose.AsyncImage(
                    model = imageUrl,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(1.1f)
                        .graphicsLayer {
                            if (continuousRotation) rotationZ = -ringAngle
                        },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = contentDescription,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
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
    tint: Color = Color.Unspecified
) {
    val resolvedTint = if (tint.isSpecified) tint else MaterialTheme.colorScheme.primary
    MaterialExpressiveShape(
        shape = shape,
        color = resolvedTint.copy(alpha = shapeAlpha),
        modifier = modifier.size(size)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = resolvedTint.copy(alpha = glyphAlpha),
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
    amplitude: Float = 1.8f,
    wavelength: Float = 15f,
    phase: Float = 0f,
    cornerRadius: Float = Float.MAX_VALUE
): Path {
    val path = Path()
    if (width <= 0f || height <= 0f) return path

    val margin = amplitude + 1.5f
    val halfW = width / 2f - margin
    val halfH = height / 2f - margin
    if (halfW <= 0f || halfH <= 0f) return path

    val maxR = min(halfW, halfH)
    val radius = min(maxR, max(0f, cornerRadius - margin))
    val sx = halfW - radius
    val sy = halfH - radius

    val quarter = 0.5f * PI.toFloat() * radius
    val perimeter = 4f * (sx + sy) + 4f * quarter
    if (perimeter <= 0f) return path

    val scallops = max(1f, kotlin.math.round(perimeter / max(wavelength, 1f)))
    val totalSteps = (perimeter / 2f).toInt().coerceIn(160, 480)

    val cx = width / 2f
    val cy = height / 2f

    val s1 = 2f * sx
    val s2 = s1 + quarter
    val s3 = s2 + 2f * sy
    val s4 = s3 + quarter
    val s5 = s4 + 2f * sx
    val s6 = s5 + quarter
    val s7 = s6 + 2f * sy

    for (step in 0..totalSteps) {
        val s = (step.toFloat() / totalSteps) * perimeter
        val wave = amplitude * (0.5f + 0.5f * cos(2f * PI.toFloat() * scallops * (s / perimeter) - phase))

        val px: Float
        val py: Float

        if (s <= s1 && sx > 0f) {
            val u = s / (2f * sx)
            px = cx - sx + u * 2f * sx
            py = cy - sy - radius - wave
        } else if (s <= s2 && quarter > 0f) {
            val th = -0.5f * PI.toFloat() + ((s - s1) / quarter) * (0.5f * PI.toFloat())
            px = cx + sx + (radius + wave) * cos(th)
            py = cy - sy + (radius + wave) * sin(th)
        } else if (s <= s3 && sy > 0f) {
            val u = (s - s2) / (2f * sy)
            px = cx + sx + radius + wave
            py = cy - sy + u * 2f * sy
        } else if (s <= s4 && quarter > 0f) {
            val th = 0f + ((s - s3) / quarter) * (0.5f * PI.toFloat())
            px = cx + sx + (radius + wave) * cos(th)
            py = cy + sy + (radius + wave) * sin(th)
        } else if (s <= s5 && sx > 0f) {
            val u = (s - s4) / (2f * sx)
            px = cx + sx - u * 2f * sx
            py = cy + sy + radius + wave
        } else if (s <= s6 && quarter > 0f) {
            val th = 0.5f * PI.toFloat() + ((s - s5) / quarter) * (0.5f * PI.toFloat())
            px = cx - sx + (radius + wave) * cos(th)
            py = cy + sy + (radius + wave) * sin(th)
        } else if (s <= s7 && sy > 0f) {
            val u = (s - s6) / (2f * sy)
            px = cx - sx - radius - wave
            py = cy + sy - u * 2f * sy
        } else {
            val th = PI.toFloat() + ((s - s7) / max(quarter, 0.001f)) * (0.5f * PI.toFloat())
            px = cx - sx + (radius + wave) * cos(th)
            py = cy - sy + (radius + wave) * sin(th)
        }

        if (step == 0) {
            path.moveTo(px, py)
        } else {
            path.lineTo(px, py)
        }
    }

    path.close()
    return path
}

class WavyPillShape(
    private val amplitudeDp: Float = 2.8f,
    private val wavelengthDp: Float = 14f,
    private val phase: Float = 0f,
    private val cornerRadiusDp: Float = 32f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val ampPx = with(density) { amplitudeDp.dp.toPx() }
        val wavePx = with(density) { wavelengthDp.dp.toPx() }
        val crPx = with(density) { cornerRadiusDp.dp.toPx() }
        val path = buildWavyPillPath(size.width, size.height, ampPx, wavePx, phase, crPx)
        return Outline.Generic(path)
    }
}

@Composable
fun WavyPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    contentColor: Color = Color(0xFF000000),
    enabled: Boolean = true,
    amplitudeDp: Float = 1.8f,
    wavelengthDp: Float = 15f
) {
    val resolvedBgColor = if (backgroundColor.isSpecified) backgroundColor else MaterialTheme.colorScheme.primary
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
                    color = if (enabled) resolvedBgColor else resolvedBgColor.copy(alpha = 0.4f)
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
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = { currentAction() }
            ),
        contentAlignment = Alignment.Center
    ) {
        MaterialExpressiveShape(
            shape = "arrow",
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxSize(0.9f)
        )
        MaterialSymbol(
            symbol = MaterialSymbols.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface,
            size = 20.dp
        )
    }
}
