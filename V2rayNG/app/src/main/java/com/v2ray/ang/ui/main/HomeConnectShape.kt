package com.v2ray.ang.ui.main

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Shape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The look of the round connect button on the home card. */
enum class HomeConnectShape {
    /** Not running, or still coming up. */
    Idle,

    /** Running. */
    Active,
}

/**
 * A scalloped ("cookie") outline: a circle whose radius swells and shrinks [lobes] times round, by [depth] of itself.
 * Drawn from this formula with the stable [GenericShape], so that no experimental Material 3 Expressive API and no
 * further dependency is needed.
 */
internal data class CookieSpec(val lobes: Int, val depth: Double)

internal fun cookieSpec(kind: HomeConnectShape): CookieSpec = when (kind) {
    HomeConnectShape.Idle -> CookieSpec(lobes = 9, depth = 0.07)
    HomeConnectShape.Active -> CookieSpec(lobes = 12, depth = 0.09)
}

/** The radius of the outline at [angle], in radians, as a part of the radius of the box: 1 at the tips of the lobes. */
internal fun cookieRadius(spec: CookieSpec, angle: Double): Double =
    (1.0 + spec.depth * cos(spec.lobes * angle)) / (1.0 + spec.depth)

/** [samples] points of the outline round the origin, in the unit circle; enough of them make the outline smooth. */
internal fun cookiePoints(spec: CookieSpec, samples: Int = COOKIE_SAMPLES): List<Pair<Float, Float>> =
    List(samples) { i ->
        val angle = 2 * PI * i / samples
        val r = cookieRadius(spec, angle)
        Pair((r * cos(angle)).toFloat(), (r * sin(angle)).toFloat())
    }

private const val COOKIE_SAMPLES = 360

private fun cookieShape(spec: CookieSpec): Shape {
    val points = cookiePoints(spec)
    return GenericShape { size, _ ->
        val radius = min(size.width, size.height) / 2f
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        points.forEachIndexed { index, (x, y) ->
            val px = centerX + x * radius
            val py = centerY + y * radius
            if (index == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
}

private val idleShape: Shape = cookieShape(cookieSpec(HomeConnectShape.Idle))
private val activeShape: Shape = cookieShape(cookieSpec(HomeConnectShape.Active))

fun homeConnectShape(kind: HomeConnectShape): Shape = when (kind) {
    HomeConnectShape.Idle -> idleShape
    HomeConnectShape.Active -> activeShape
}
