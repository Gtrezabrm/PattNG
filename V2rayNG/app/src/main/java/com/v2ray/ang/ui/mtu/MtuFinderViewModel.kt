package com.v2ray.ang.ui.mtu

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.AetherMtuProbe
import com.v2ray.ang.core.MtuOutcome
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.ui.base.BaseViewModel
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the MTU finder reaches outside itself, so that its decisions can be tested without Android. */
class MtuFinderEnvironment(
    val run: suspend (onProgress: (Int) -> Unit) -> MtuOutcome,
    val currentMtu: () -> Int,
    val applyMtu: (Int) -> Unit,
    val copy: (String) -> Unit,
    // LogUtil reads MMKV, which a JVM test does not have; a test passes a log of its own.
    val logFailure: (String, Throwable) -> Unit = { message, e -> LogUtil.e(AppConfig.TAG, message, e) },
) {
    companion object {
        fun default(app: Application) = MtuFinderEnvironment(
            run = { onProgress -> AetherMtuProbe.run(app, onProgress) },
            currentMtu = { SettingsManager.getVpnMtu() },
            // The setting is stored as text, as the settings screen stores it; the VPN reads it when it next starts.
            applyMtu = { MmkvManager.encodeSettings(AppConfig.PREF_VPN_MTU, it.toString()) },
            copy = { Utils.setClipboard(app, it) },
        )
    }
}

class MtuFinderViewModel(
    application: Application,
    private val environment: MtuFinderEnvironment,
) : BaseViewModel(application) {

    constructor(application: Application) : this(application, MtuFinderEnvironment.default(application))

    private val _state = MutableStateFlow(MtuFinderState(currentMtu = environment.currentMtu()))
    val state: StateFlow<MtuFinderState> = _state.asStateFlow()

    private var job: Job? = null

    /** Numbers each run, so that a cancelled run that is still unwinding cannot write into the run that followed it. */
    private var run = 0

    fun onAction(action: MtuFinderAction) {
        when (action) {
            MtuFinderAction.Start -> start()
            MtuFinderAction.Cancel -> cancel()
            MtuFinderAction.Copy -> recommended()?.let { environment.copy(it.mtu.toString()) }
            MtuFinderAction.Apply -> apply()
        }
    }

    private fun recommended(): MtuOutcome.Recommended? = _state.value.outcome as? MtuOutcome.Recommended

    private fun start() {
        if (_state.value.phase == MtuPhase.RUNNING) return
        val mine = ++run
        _state.update { it.copy(phase = MtuPhase.RUNNING, progress = 0, outcome = null, applied = false) }
        job = viewModelScope.launch {
            val outcome = try {
                environment.run { progress ->
                    if (run == mine) _state.update { it.copy(progress = progress) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                environment.logFailure("MtuFinder: the measurement failed", e)
                MtuOutcome.TunnelDown
            }
            if (run == mine) _state.update { it.copy(phase = MtuPhase.DONE, progress = 100, outcome = outcome) }
        }
    }

    /** The screen is idle at once; the cancelled run may take a moment to unwind, and its late writes are ignored. */
    private fun cancel() {
        if (_state.value.phase != MtuPhase.RUNNING) return
        run++
        job?.cancel()
        _state.update { it.copy(phase = MtuPhase.IDLE, progress = 0, outcome = null) }
    }

    /** Only a measured recommendation is ever written. */
    private fun apply() {
        val result = recommended() ?: return
        environment.applyMtu(result.mtu)
        _state.update { it.copy(currentMtu = result.mtu, applied = true) }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
