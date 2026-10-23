package com.v2ray.ang.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

class HomeConnectShapeTest {

    @Test
    fun theOutlineTouchesTheBoxAtTheTipsOfTheLobes() {
        HomeConnectShape.entries.forEach { kind ->
            assertEquals(1.0, cookieRadius(cookieSpec(kind), 0.0), 1e-9)
        }
    }

    @Test
    fun theOutlineStaysInsideTheBoxAndSwellsAsDeepAsItsDepth() {
        HomeConnectShape.entries.forEach { kind ->
            val spec = cookieSpec(kind)
            val radii = (0 until 720).map { cookieRadius(spec, 2 * PI * it / 720) }
            assertTrue(radii.all { it <= 1.0 + 1e-9 })
            val shallowest = (1.0 - spec.depth) / (1.0 + spec.depth)
            assertEquals(shallowest, radii.min(), 1e-3)
        }
    }

    @Test
    fun thePointsFollowTheRadiusAndFillTheSamples() {
        val spec = cookieSpec(HomeConnectShape.Idle)
        val points = cookiePoints(spec, samples = 90)
        assertEquals(90, points.size)
        points.forEachIndexed { i, (x, y) ->
            val expected = cookieRadius(spec, 2 * PI * i / 90)
            assertEquals(expected, hypot(x.toDouble(), y.toDouble()), 1e-5)
        }
    }

    @Test
    fun theTwoLooksDiffer() {
        assertTrue(cookieSpec(HomeConnectShape.Idle) != cookieSpec(HomeConnectShape.Active))
        assertTrue(abs(cookieSpec(HomeConnectShape.Idle).lobes - cookieSpec(HomeConnectShape.Active).lobes) > 0)
    }
}
