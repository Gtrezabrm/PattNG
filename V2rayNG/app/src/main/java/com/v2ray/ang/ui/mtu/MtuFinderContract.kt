package com.v2ray.ang.ui.mtu

import com.v2ray.ang.core.MtuOutcome

enum class MtuPhase { IDLE, RUNNING, DONE }

/** What the MTU finder screen shows. [outcome] is null until a run has ended. */
data class MtuFinderState(
    val phase: MtuPhase = MtuPhase.IDLE,
    val progress: Int = 0,
    val currentMtu: Int,
    val outcome: MtuOutcome? = null,
    val applied: Boolean = false,
)

sealed interface MtuFinderAction {
    data object Start : MtuFinderAction
    data object Cancel : MtuFinderAction
    data object Copy : MtuFinderAction
    data object Apply : MtuFinderAction
}
