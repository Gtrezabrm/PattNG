package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class MtuScoringTest {

    /** Samples where every size up to [limit] is delivered and every size above it is lost, on both destinations. */
    private fun cliffAt(limit: Int, sizes: List<Int> = MtuScoring.defaultSizes()): List<MtuSample> = buildList {
        for (resolver in 0 until MtuScoring.RESOLVER_COUNT) {
            repeat(MtuScoring.ROUNDS) {
                sizes.forEach { add(MtuSample(it, resolver, it <= limit)) }
            }
        }
    }

    @Test
    fun `a sharp cliff gives the largest passing size plus headers minus the margin`() {
        val outcome = MtuScoring.score(cliffAt(1264), ipv6 = false)

        val recommended = assertInstanceOf(MtuOutcome.Recommended::class.java, outcome)
        assertEquals(1264, recommended.maxPassingPayload)
        assertEquals(1272, recommended.mtu) // 1264 + 28 - 20, a multiple of 4
        assertEquals(MtuConfidence.HIGH, recommended.confidence)
        assertTrue(recommended.belowIpv6Minimum)
    }

    @Test
    fun `an IPv6 tunnel counts the longer headers`() {
        val recommended = assertInstanceOf(MtuOutcome.Recommended::class.java, MtuScoring.score(cliffAt(1296), ipv6 = true))

        assertEquals(1324, recommended.mtu) // 1296 + 48 - 20
        assertFalse(recommended.belowIpv6Minimum)
    }

    @Test
    fun `no size is lost gives AllPassed and no number`() {
        val outcome = MtuScoring.score(cliffAt(Int.MAX_VALUE), ipv6 = false)

        assertEquals(1472, assertInstanceOf(MtuOutcome.AllPassed::class.java, outcome).maxTested)
    }

    @Test
    fun `an answer above a cliff does not lift the limit`() {
        val samples = cliffAt(1264).map { if (it.size == 1456) it.copy(delivered = true) else it }

        val recommended = assertInstanceOf(MtuOutcome.Recommended::class.java, MtuScoring.score(samples, ipv6 = false))

        assertEquals(1264, recommended.maxPassingPayload)
    }

    @Test
    fun `one destination that drops big queries is not a limit`() {
        val samples = cliffAt(Int.MAX_VALUE).map { if (it.resolver == 1 && it.size > 1200) it.copy(delivered = false) else it }

        assertInstanceOf(MtuOutcome.AllPassed::class.java, MtuScoring.score(samples, ipv6 = false))
    }

    @Test
    fun `a lone lost probe in a round still passes`() {
        val samples = cliffAt(1264).toMutableList()
        for (resolver in 0 until MtuScoring.RESOLVER_COUNT) {
            val first = samples.indexOfFirst { it.size == 1232 && it.resolver == resolver }
            samples[first] = samples[first].copy(delivered = false)
        }

        val recommended = assertInstanceOf(MtuOutcome.Recommended::class.java, MtuScoring.score(samples, ipv6 = false))

        assertEquals(1264, recommended.maxPassingPayload)
    }

    @Test
    fun `a gradual loss gives medium confidence`() {
        val samples = cliffAt(1264).toMutableList()
        // Half of the probes of the next size get through on the better destination.
        var seen = 0
        for (i in samples.indices) {
            if (samples[i].size == 1296 && samples[i].resolver == 0 && seen++ < 2) samples[i] = samples[i].copy(delivered = true)
        }

        val recommended = assertInstanceOf(MtuOutcome.Recommended::class.java, MtuScoring.score(samples, ipv6 = false))

        assertEquals(MtuConfidence.MEDIUM, recommended.confidence)
    }

    @Test
    fun `a baseline that answers nothing gives NoBaseline`() {
        val samples = cliffAt(1264).map { if (it.size == MtuScoring.BASELINE_SIZE) it.copy(delivered = false) else it }

        assertEquals(MtuOutcome.NoBaseline, MtuScoring.score(samples, ipv6 = false))
    }

    @Test
    fun `no samples give NoBaseline`() {
        assertEquals(MtuOutcome.NoBaseline, MtuScoring.score(emptyList(), ipv6 = false))
    }

    @Test
    fun `a lossy baseline lowers the confidence`() {
        // One of the four baseline probes is lost on both destinations.
        val seen = mutableMapOf<Int, Int>()
        val samples = cliffAt(1264).map {
            if (it.size == MtuScoring.BASELINE_SIZE && seen.merge(it.resolver, 1, Int::plus) == 1) it.copy(delivered = false) else it
        }

        val recommended = assertInstanceOf(MtuOutcome.Recommended::class.java, MtuScoring.score(samples, ipv6 = false))

        assertEquals(MtuConfidence.LOW, recommended.confidence)
    }

    @Test
    fun `the smallest sweep size failing gives BelowSweep and no number`() {
        assertEquals(MtuOutcome.BelowSweep, MtuScoring.score(cliffAt(100), ipv6 = false))
    }

    @Test
    fun `the recommended MTU is a multiple of four inside the allowed range`() {
        for (payload in 576..1472 step 7) {
            for (ipv6 in listOf(false, true)) {
                val mtu = MtuScoring.recommendedMtu(payload, ipv6)
                assertEquals(0, mtu % 4)
                assertTrue(mtu in 576..1500)
            }
        }
        assertEquals(1500, MtuScoring.recommendedMtu(1472, ipv6 = true))
    }

    @Test
    fun `the schedule visits every size once per round and destination`() {
        val sizes = MtuScoring.defaultSizes()
        val steps = MtuScoring.schedule(sizes, rounds = 3, resolvers = 2, random = Random(7))

        assertEquals(sizes.size * 3 * 2, steps.size)
        for (resolver in 0..1) {
            assertEquals(3, steps.count { it.size == 1264 && it.resolver == resolver })
        }
        // One shuffled pass is not the sorted order.
        assertFalse(steps.take(sizes.size).map { it.size } == sizes)
    }

    @Test
    fun `the default sizes start at the baseline and end below the Ethernet payload`() {
        val sizes = MtuScoring.defaultSizes()

        assertEquals(MtuScoring.BASELINE_SIZE, sizes.first())
        assertEquals(1472, sizes.last())
        assertEquals(sizes.sorted(), sizes)
    }
}
