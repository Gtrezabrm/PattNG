package com.v2ray.ang.ui.mtu

import android.app.Application
import com.v2ray.ang.core.MtuConfidence
import com.v2ray.ang.core.MtuOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class MtuFinderViewModelTest {

    private val recommended = MtuOutcome.Recommended(1272, MtuConfidence.HIGH, true, 1264, emptyList())

    private class Fake(var outcome: MtuOutcome, var gate: CompletableDeferred<Unit>? = null) {
        var mtu = 1500
        val applied = mutableListOf<Int>()
        val copied = mutableListOf<String>()
        var runs = 0

        fun environment() = MtuFinderEnvironment(
            run = { onProgress ->
                runs++
                onProgress(40)
                gate?.await()
                outcome
            },
            currentMtu = { mtu },
            applyMtu = { applied += it; mtu = it },
            copy = { copied += it },
        )
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(fake: Fake) = MtuFinderViewModel(mock<Application>(), fake.environment())

    @Test
    fun `the initial state shows the current MTU and no result`() {
        val state = viewModel(Fake(recommended)).state.value

        assertEquals(MtuPhase.IDLE, state.phase)
        assertEquals(1500, state.currentMtu)
        assertNull(state.outcome)
        assertFalse(state.applied)
    }

    @Test
    fun `a finished run shows its outcome`() {
        val model = viewModel(Fake(recommended))

        model.onAction(MtuFinderAction.Start)

        assertEquals(MtuPhase.DONE, model.state.value.phase)
        assertEquals(recommended, model.state.value.outcome)
        assertEquals(100, model.state.value.progress)
    }

    @Test
    fun `progress is shown while the run lasts and a second start is ignored`() {
        val fake = Fake(recommended, CompletableDeferred())
        val model = viewModel(fake)

        model.onAction(MtuFinderAction.Start)
        model.onAction(MtuFinderAction.Start)

        assertEquals(MtuPhase.RUNNING, model.state.value.phase)
        assertEquals(40, model.state.value.progress)
        assertEquals(1, fake.runs)
        fake.gate!!.complete(Unit)
        assertEquals(MtuPhase.DONE, model.state.value.phase)
    }

    @Test
    fun `cancel ends the run without an outcome`() {
        val fake = Fake(recommended, CompletableDeferred())
        val model = viewModel(fake)

        model.onAction(MtuFinderAction.Start)
        model.onAction(MtuFinderAction.Cancel)

        assertEquals(MtuPhase.IDLE, model.state.value.phase)
        assertNull(model.state.value.outcome)
        assertEquals(0, model.state.value.progress)
    }

    @Test
    fun `apply writes only a measured recommendation`() {
        val fake = Fake(recommended)
        val model = viewModel(fake)
        model.onAction(MtuFinderAction.Apply)
        assertTrue(fake.applied.isEmpty())

        model.onAction(MtuFinderAction.Start)
        model.onAction(MtuFinderAction.Apply)

        assertEquals(listOf(1272), fake.applied)
        assertEquals(1272, model.state.value.currentMtu)
        assertTrue(model.state.value.applied)
    }

    @Test
    fun `no failure outcome can be applied or copied`() {
        for (outcome in listOf(MtuOutcome.NoBaseline, MtuOutcome.BelowSweep, MtuOutcome.UdpUnsupported, MtuOutcome.TunnelDown, MtuOutcome.Busy, MtuOutcome.NotAether)) {
            val fake = Fake(outcome)
            val model = viewModel(fake)

            model.onAction(MtuFinderAction.Start)
            model.onAction(MtuFinderAction.Apply)
            model.onAction(MtuFinderAction.Copy)

            assertEquals(outcome, model.state.value.outcome)
            assertTrue(fake.applied.isEmpty())
            assertTrue(fake.copied.isEmpty())
        }
    }

    @Test
    fun `a failing run ends as TunnelDown instead of crashing`() {
        val logged = mutableListOf<Pair<String, Throwable>>()
        val model = MtuFinderViewModel(
            mock<Application>(),
            MtuFinderEnvironment(
                run = { error("boom") },
                currentMtu = { 1500 },
                applyMtu = {},
                copy = {},
                logFailure = { message, e -> logged += message to e },
            ),
        )

        model.onAction(MtuFinderAction.Start)

        assertEquals(MtuPhase.DONE, model.state.value.phase)
        assertEquals(MtuOutcome.TunnelDown, model.state.value.outcome)
        assertEquals(1, logged.size)
        assertEquals("boom", logged.single().second.message)
    }

    @Test
    fun `copy gives the recommended number`() {
        val fake = Fake(recommended)
        val model = viewModel(fake)

        model.onAction(MtuFinderAction.Start)
        model.onAction(MtuFinderAction.Copy)

        assertEquals(listOf("1272"), fake.copied)
    }

    @Test
    fun `a new run clears the earlier outcome and the applied flag`() {
        val fake = Fake(recommended)
        val model = viewModel(fake)
        model.onAction(MtuFinderAction.Start)
        model.onAction(MtuFinderAction.Apply)

        fake.gate = CompletableDeferred()
        model.onAction(MtuFinderAction.Start)

        assertNull(model.state.value.outcome)
        assertFalse(model.state.value.applied)
        assertEquals(MtuPhase.RUNNING, model.state.value.phase)
    }
}
