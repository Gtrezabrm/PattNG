package com.v2ray.ang.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.v2ray.ang.AppConfig
import com.v2ray.ang.extension.delay
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Watches the network that carries the tunnel and reports topology changes.
 *
 * Cellular -> Wi-Fi is a make-before-break handover: the new network is announced while the old one
 * is still connected, so the socket to the server is never reset and the core keeps using a dead
 * connection. Deciding that a handover happened is what this class is for, acting on it is not.
 *
 * Only used from Android P and above, see CoreServiceManager.startNetworkMonitor().
 * [onHandover] is invoked on a background thread after the debounce window and may block. It is
 * passed this monitor, and claims its work with [whileRegistered], so that none begins once
 * [unregister] has returned. [describe] names the run mode and the profile of the service it
 * watches, for its failure logs.
 */
class NetworkMonitor(
    private val connectivity: ConnectivityManager,
    private val onUnderlyingNetworksChanged: (Array<Network>?) -> Unit,
    private val onHandover: (NetworkMonitor) -> Unit,
    private val describe: () -> String,
) {
    private companion object {
        const val HANDOVER_DEBOUNCE_MS = 1000L
    }

    // PattNG: the network callbacks run on a thread of ConnectivityManager, register and unregister on the main
    // thread, so the state below is read and written under this lock.
    private val lock = Any()
    private var upstream: Network? = null
    private var handoverJob: Job? = null
    private var registered = false

    /** PattNG: lets a handover begin from register() until unregister(), see [whileRegistered]. */
    private val gate = HandoverGate()

    /** PattNG: owns the handover work, which [unregister] cancels. */
    private val handoverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Unfortunately registerDefaultNetworkCallback is going to return our VPN interface:
     * https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    private val request by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = synchronized(lock) { upstream.also { upstream = network } }
            onUnderlyingNetworksChanged(arrayOf(network))
            if (previous != null && previous != network) {
                scheduleHandover(network)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            // it's a good idea to refresh capabilities
            onUnderlyingNetworksChanged(arrayOf(network))
        }

        override fun onLost(network: Network) {
            onUnderlyingNetworksChanged(null)
        }
    }

    /**
     * Starts watching. Safe to call more than once, only the first call registers.
     */
    fun register() {
        synchronized(lock) {
            if (registered) return
            try {
                connectivity.requestNetwork(request, callback)
                registered = true
                gate.open()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "NetworkMonitor: ${describe()}, register: requesting the network failed", e)
            }
        }
    }

    /**
     * Stops watching and drops the tracked state. Safe to call more than once. No handover begins once it
     * has returned: a handover claims its work with [whileRegistered], which this closes first.
     */
    fun unregister() {
        gate.close()
        synchronized(lock) {
            handoverJob?.cancel()
            handoverJob = null
            handoverScope.coroutineContext.cancelChildren()
            upstream = null
            if (!registered) return
            registered = false
        }
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "NetworkMonitor: ${describe()}, unregister: unregistering the callback failed", e)
        }
    }

    /**
     * PattNG: [claim], run while this monitor is registered, and what it returns; null once [unregister] has run. A
     * handover handler claims its work this way: unregister() waits for a claim under way, and none succeeds once it
     * has returned. The claim holds the lock unregister() takes on the main thread, so it has to be short; the work it
     * lets through runs after it.
     */
    fun <T> whileRegistered(claim: () -> T): T? = gate.whileOpen(claim)

    private fun scheduleHandover(network: Network) {
        LogUtil.i(AppConfig.TAG, "NetworkMonitor: Upstream is now $network")
        synchronized(lock) {
            // PattNG: a network callback that arrives after unregister() schedules nothing.
            if (!registered) return
            handoverJob?.cancel()
            handoverJob = handoverScope.launch {
                try {
                    delay(HANDOVER_DEBOUNCE_MS)
                    // PattNG: cancelling ends the wait above, not a handover that has just come out of it. An
                    // unregistered monitor calls nothing, and the handler claims its work with whileRegistered.
                    if (!gate.isOpen) return@launch
                    onHandover(this@NetworkMonitor)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "NetworkMonitor: ${describe()}, handover: handling the upstream change failed", e)
                }
            }
        }
    }
}
