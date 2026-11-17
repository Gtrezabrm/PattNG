package com.v2ray.ang.ui.mtu

import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.core.MtuConfidence
import com.v2ray.ang.core.MtuOutcome
import com.v2ray.ang.core.MtuSizeResult
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import kotlin.math.roundToInt

class MtuFinderActivity : BaseComponentActivity() {
    private val viewModel: MtuFinderViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        MtuFinderScreen(viewModel = viewModel, onBackClick = { finish() })
    }
}

@Composable
private fun MtuFinderScreen(viewModel: MtuFinderViewModel, onBackClick: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val running = state.phase == MtuPhase.RUNNING

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_mtu_finder),
                onBackClick = onBackClick,
                isLoading = running,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.mtu_finder_intro), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.mtu_current, state.currentMtu),
                style = MaterialTheme.typography.titleMedium,
            )

            if (running) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.mtu_progress, state.progress))
                OutlinedButton(
                    onClick = { viewModel.onAction(MtuFinderAction.Cancel) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.mtu_cancel)) }
            } else {
                Button(
                    onClick = { viewModel.onAction(MtuFinderAction.Start) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.mtu_start)) }
            }

            state.outcome?.let { outcome ->
                OutcomeContent(
                    outcome = outcome,
                    applied = state.applied,
                    onCopy = { viewModel.onAction(MtuFinderAction.Copy) },
                    onApply = { viewModel.onAction(MtuFinderAction.Apply) },
                )
            }
        }
    }
}

@Composable
private fun OutcomeContent(outcome: MtuOutcome, applied: Boolean, onCopy: () -> Unit, onApply: () -> Unit) {
    when (outcome) {
        is MtuOutcome.Recommended -> {
            Text(
                stringResource(R.string.mtu_recommended, outcome.mtu),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(
                    when (outcome.confidence) {
                        MtuConfidence.HIGH -> R.string.mtu_confidence_high
                        MtuConfidence.MEDIUM -> R.string.mtu_confidence_medium
                        MtuConfidence.LOW -> R.string.mtu_confidence_low
                    },
                ),
            )
            if (outcome.belowIpv6Minimum) {
                Text(stringResource(R.string.mtu_below_min_warning), color = MaterialTheme.colorScheme.error)
            }
            LossChart(outcome.perSize)
            Text(stringResource(R.string.mtu_note_inner), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCopy) { Text(stringResource(R.string.mtu_copy)) }
                Button(onClick = onApply, enabled = !applied) { Text(stringResource(R.string.mtu_apply)) }
            }
            if (applied) Text(stringResource(R.string.mtu_applied))
        }

        is MtuOutcome.AllPassed -> {
            Text(stringResource(R.string.mtu_all_passed, outcome.maxTested))
            LossChart(outcome.perSize)
            Text(stringResource(R.string.mtu_note_inner), style = MaterialTheme.typography.bodySmall)
        }

        MtuOutcome.NoBaseline -> ErrorText(R.string.mtu_err_no_baseline)
        MtuOutcome.UdpUnsupported -> ErrorText(R.string.mtu_err_udp)
        MtuOutcome.BelowSweep -> ErrorText(R.string.mtu_err_below_sweep)
        MtuOutcome.TunnelDown -> ErrorText(R.string.mtu_err_tunnel)
        MtuOutcome.Busy -> ErrorText(R.string.mtu_err_busy)
        MtuOutcome.NotAether -> ErrorText(R.string.mtu_err_not_aether)
    }
}

@Composable
private fun ErrorText(resId: Int) {
    Text(stringResource(resId), color = MaterialTheme.colorScheme.error)
}

/** Bars of the loss per payload size; the same figures are in the description, as the chart alone says nothing to a screen reader. */
@Composable
private fun LossChart(results: List<MtuSizeResult>) {
    if (results.isEmpty()) return
    val caption = stringResource(R.string.mtu_chart_caption)
    val figures = results.joinToString { "${it.size}: ${(it.loss * 100).roundToInt()}%" }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val good = MaterialTheme.colorScheme.primary
    val bad = MaterialTheme.colorScheme.error

    Text(caption, style = MaterialTheme.typography.labelMedium)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = "$caption. $figures" },
    ) {
        val slot = size.width / results.size
        results.forEachIndexed { index, result ->
            val left = index * slot + slot * 0.1f
            val width = slot * 0.8f
            drawRect(track, topLeft = Offset(left, 0f), size = Size(width, size.height))
            val barHeight = maxOf(size.height * result.loss.toFloat().coerceIn(0f, 1f), 2f)
            drawRect(
                if (result.loss > 0.0) bad else good,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(width, barHeight),
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(results.first().size.toString(), style = MaterialTheme.typography.labelSmall)
        Text(results.last().size.toString(), style = MaterialTheme.typography.labelSmall)
    }
}
