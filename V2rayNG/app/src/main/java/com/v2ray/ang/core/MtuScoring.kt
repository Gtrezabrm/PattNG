package com.v2ray.ang.core

import kotlin.random.Random

/** One UDP probe: [size] is the UDP payload in bytes, [resolver] the index of the destination it went to. */
data class MtuSample(val size: Int, val resolver: Int, val delivered: Boolean)

/** One step of the sweep, in the order it is sent. */
data class MtuProbeStep(val size: Int, val resolver: Int)

/** What the probes of one payload [size] came to, on the destination that carried them best. */
data class MtuSizeResult(val size: Int, val sent: Int, val lost: Int) {
    val loss: Double get() = if (sent == 0) 1.0 else lost.toDouble() / sent
}

enum class MtuConfidence { HIGH, MEDIUM, LOW }

/** How an MTU measurement ended. Nothing here is ever a guessed number: without a measurement there is no [Recommended]. */
sealed interface MtuOutcome {
    val perSize: List<MtuSizeResult> get() = emptyList()

    /** [mtu] fits the largest payload that got through, [maxPassingPayload]; [belowIpv6Minimum] when that is under 1280. */
    data class Recommended(
        val mtu: Int,
        val confidence: MtuConfidence,
        val belowIpv6Minimum: Boolean,
        val maxPassingPayload: Int,
        override val perSize: List<MtuSizeResult>,
    ) : MtuOutcome

    /** Every size up to [maxTested] got through: the tunnel shows no limit, so there is no number to recommend. */
    data class AllPassed(val maxTested: Int, override val perSize: List<MtuSizeResult>) : MtuOutcome

    /** Not even a small packet was answered. */
    data object NoBaseline : MtuOutcome

    /** The core refused UDP on its SOCKS port. */
    data object UdpUnsupported : MtuOutcome

    /** The Aether tunnel did not come up, or its SOCKS port could not be reached. */
    data object TunnelDown : MtuOutcome

    /** Only packets under the smallest sweep size got through: the limit is somewhere below 576 bytes, not measured. */
    data object BelowSweep : MtuOutcome

    /** Another Aether session holds the core or is still connecting. */
    data object Busy : MtuOutcome

    /** The selected profile is not an Aether profile. */
    data object NotAether : MtuOutcome
}

/**
 * The pure part of the MTU search: which sizes to send, and what the answers say. The probes go through the Aether
 * SOCKS port, so they measure the path inside the tunnel, which is what a tun packet has to fit through.
 */
object MtuScoring {
    /** A small query that any working path answers; the loss of the others is judged against it. */
    const val BASELINE_SIZE = 100

    const val RESOLVER_COUNT = 2
    const val ROUNDS = 4

    /** The IPv4 and IPv6 headers plus the UDP header that a tun packet carries around the payload. */
    private const val IPV4_UDP_OVERHEAD = 28
    private const val IPV6_UDP_OVERHEAD = 48

    private const val MARGIN = 20
    private const val IPV6_MINIMUM_MTU = 1280
    private const val MAX_MTU = 1500
    private const val MIN_MTU = 576

    /** Per destination, one lost probe in [ROUNDS] still counts as through. */
    private const val PASS_LOSS = 0.25
    private const val MAX_ALLOWED_LOSS = 0.5
    private const val NO_BASELINE_LOSS = 0.5
    private const val SHARP_CLIFF_LOSS = 0.75

    fun defaultSizes(): List<Int> =
        listOf(BASELINE_SIZE, 576, 800, 1000, 1100) + (1200..1456 step 32) + 1472

    /**
     * The probes in sending order: every round visits every destination with every size once, in a shuffled order, so
     * a stretch of bad network is not mistaken for a size limit.
     */
    fun schedule(sizes: List<Int>, rounds: Int, resolvers: Int, random: Random): List<MtuProbeStep> =
        buildList {
            repeat(rounds) {
                for (resolver in 0 until resolvers) {
                    sizes.shuffled(random).forEach { add(MtuProbeStep(it, resolver)) }
                }
            }
        }

    /** Per size, the figures of the destination that lost the least: one destination that drops big queries is no limit. */
    fun summarize(samples: List<MtuSample>): List<MtuSizeResult> =
        samples.groupBy { it.size }.map { (size, ofSize) ->
            ofSize.groupBy { it.resolver }.values
                .map { MtuSizeResult(size, it.size, it.count { sample -> !sample.delivered }) }
                .minBy { it.loss }
        }.sortedBy { it.size }

    fun score(samples: List<MtuSample>, ipv6: Boolean): MtuOutcome {
        val results = summarize(samples)
        val baseline = results.firstOrNull { it.size == BASELINE_SIZE }
        if (baseline == null || baseline.loss >= NO_BASELINE_LOSS) return MtuOutcome.NoBaseline

        val allowed = minOf(PASS_LOSS + baseline.loss, MAX_ALLOWED_LOSS)
        val sweep = results.filter { it.size != BASELINE_SIZE }
        if (sweep.isEmpty()) return MtuOutcome.NoBaseline

        // The largest size every smaller size also reached: one lucky answer above a cliff is not a pass.
        var best: MtuSizeResult? = null
        for (result in sweep) {
            if (result.loss <= allowed) best = result else break
        }
        if (best == null) return MtuOutcome.BelowSweep
        if (best.size == sweep.last().size) return MtuOutcome.AllPassed(best.size, results)

        val next = sweep[sweep.indexOf(best) + 1]
        val confidence = when {
            baseline.loss > 0.0 -> MtuConfidence.LOW
            next.loss >= SHARP_CLIFF_LOSS && best.loss == 0.0 -> MtuConfidence.HIGH
            else -> MtuConfidence.MEDIUM
        }
        val mtu = recommendedMtu(best.size, ipv6)
        return MtuOutcome.Recommended(mtu, confidence, mtu < IPV6_MINIMUM_MTU, best.size, results)
    }

    /** The tun MTU that keeps a [payload]-byte UDP packet, with its headers and a margin, inside what got through. */
    fun recommendedMtu(payload: Int, ipv6: Boolean): Int {
        val overhead = if (ipv6) IPV6_UDP_OVERHEAD else IPV4_UDP_OVERHEAD
        val raw = (payload + overhead - MARGIN).coerceIn(MIN_MTU, MAX_MTU)
        return raw - raw % 4
    }
}
