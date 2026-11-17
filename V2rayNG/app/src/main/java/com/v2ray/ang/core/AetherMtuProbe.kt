package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.AppConfig
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.random.Random

/**
 * Measures which UDP payload sizes get through the Aether tunnel: padded DNS queries to two public resolvers over a
 * SOCKS5 UDP association with the core, shuffled across sizes and rounds, scored by [MtuScoring].
 *
 * It runs through [AetherDelayTester.measureVia], so it uses the live session when that runs this core and a test
 * tunnel of its own when no session runs on Aether, and it never touches the VPN service. This process is
 * outside the VPN, which is why the probe goes through the core's SOCKS port and not through the tun.
 */
object AetherMtuProbe {
    private val RESOLVERS = listOf(byteArrayOf(1, 1, 1, 1), byteArrayOf(8, 8, 8, 8))
    private const val DNS_PORT = 53
    private const val QUERY_NAME = "example.com"
    private const val PROBE_TIMEOUT_MS = 1500
    private const val PACING_MS = 50L
    private const val PRECHECK_TRIES = 3

    /** Runs the whole sweep; [onProgress] gets 0..100 from the thread that sends. */
    suspend fun run(context: Context, onProgress: (Int) -> Unit): MtuOutcome {
        val guid = MmkvManager.getSelectServer() ?: return MtuOutcome.NotAether
        val profile = MmkvManager.decodeServerConfig(guid) ?: return MtuOutcome.NotAether
        if (profile.configType != EConfigType.AETHER) return MtuOutcome.NotAether
        val ipv6 = MmkvManager.decodeSettingsBool(AppConfig.PREF_IPV6_ENABLED, AppConfig.DEFAULT_IPV6_ENABLED)

        var outcome: MtuOutcome? = null
        val measured = AetherDelayTester.measureVia(context, guid, AetherCore.of(profile)) { port, _ ->
            // The test budget of the other probes is 12 seconds; a sweep takes longer and keeps its own pace.
            outcome = withContext(Dispatchers.IO) { sweep(port, ipv6, onProgress) }
            1L
        }
        return outcome ?: if (measured == AetherDelayTester.UNTESTED) MtuOutcome.Busy else MtuOutcome.TunnelDown
    }

    internal suspend fun sweep(port: Int, ipv6: Boolean, onProgress: (Int) -> Unit): MtuOutcome {
        val socks = try {
            Socks5Udp.open(port)
        } catch (e: Socks5UdpException) {
            LogUtil.w(AppConfig.TAG, "MtuProbe: the SOCKS UDP association was refused, port=$port", e)
            return if (e.unsupported) MtuOutcome.UdpUnsupported else MtuOutcome.TunnelDown
        } catch (e: IOException) {
            LogUtil.w(AppConfig.TAG, "MtuProbe: the SOCKS port could not be reached, port=$port", e)
            return MtuOutcome.TunnelDown
        }
        try {
            socks.use { session ->
                var nextId = 0
                fun answered(step: MtuProbeStep): Boolean = probe(session, step, nextId++ and 0xFFFF)

                // A path that answers nothing small would make the whole sweep wait out its timeouts for no result.
                var alive = false
                precheck@ for (attempt in 0 until PRECHECK_TRIES) {
                    for (resolver in RESOLVERS.indices) {
                        currentCoroutineContext().ensureActive()
                        if (answered(MtuProbeStep(MtuScoring.BASELINE_SIZE, resolver))) {
                            alive = true
                            break@precheck
                        }
                    }
                }
                if (!alive) return MtuOutcome.NoBaseline

                val steps = MtuScoring.schedule(MtuScoring.defaultSizes(), MtuScoring.ROUNDS, RESOLVERS.size, Random(System.nanoTime()))
                val samples = ArrayList<MtuSample>(steps.size)
                steps.forEachIndexed { index, step ->
                    currentCoroutineContext().ensureActive()
                    samples += MtuSample(step.size, step.resolver, answered(step))
                    onProgress((index + 1) * 100 / steps.size)
                    delay(PACING_MS)
                }
                return MtuScoring.score(samples, ipv6)
            }
        } catch (e: IOException) {
            // A send that fails means the association is gone; scoring its silence would read as a size limit.
            LogUtil.w(AppConfig.TAG, "MtuProbe: the SOCKS UDP association failed during the sweep, port=$port", e)
            return MtuOutcome.TunnelDown
        }
    }

    /** True when the answer to a query of [step]'s size, with [id], came back in time. A late answer to an earlier probe has another id. */
    private fun probe(socks: Socks5Udp, step: MtuProbeStep, id: Int): Boolean {
        socks.send(RESOLVERS[step.resolver], DNS_PORT, DnsPacket.buildQuery(id, QUERY_NAME, step.size))
        val deadline = System.nanoTime() + PROBE_TIMEOUT_MS * 1_000_000L
        while (true) {
            val leftMs = (deadline - System.nanoTime()) / 1_000_000L
            if (leftMs <= 0) return false
            val data = socks.receive(leftMs.toInt()) ?: return false
            if (DnsPacket.parseResponse(data, id) != null) return true
        }
    }
}
