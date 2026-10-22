package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/**
 * What the home card shows. Slice 2 of the home redesign: presentation only, fed by mock data until it is mapped from
 * [MainStatus] by [MainViewModel] in slice 3.
 */
enum class HomeCardStatus {
    /** No server is selected. */
    Empty,
    Disconnected,
    Connecting,
    Connected,

    /** A start the user asked for failed. */
    Error,

    /** It was running and stopped without the user asking; the user is never reconnected automatically. */
    DisconnectedUnexpected,
}

@Immutable
data class HomeCardDetails(
    val pingMillis: Int,
    val duration: String,
    val download: String,
    val upload: String,
)

@Immutable
data class HomeCardModel(
    val status: HomeCardStatus,
    /** The remark of the selected profile; shown as stored. */
    val serverName: String? = null,
    /** Shown only while [HomeCardStatus.Connected]. */
    val details: HomeCardDetails? = null,
)

/**
 * The centered connection card: a round connect button, the state in words, and the one action the state offers.
 * It owns no state beyond what it is given; every user operation leaves through a callback.
 */
@Composable
fun HomeConnectCard(
    model: HomeCardModel,
    onConnectClick: () -> Unit,
    onStatusActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = homeCardColors(model.status)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Card(
            shape = RoundedCornerShape(40.dp),
            colors = CardDefaults.cardColors(
                containerColor = colors.container,
                contentColor = colors.content,
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = if (landscape) 720.dp else 480.dp)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeCardMain(model, colors, onConnectClick, onStatusActionClick, modifier = Modifier.weight(1f))
                    if (model.status == HomeCardStatus.Connected && model.details != null) {
                        HomeCardDetailsBlock(model.details, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    HomeCardMain(model, colors, onConnectClick, onStatusActionClick)
                    if (model.status == HomeCardStatus.Connected && model.details != null) {
                        HomeCardDetailsBlock(model.details)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCardMain(
    model: HomeCardModel,
    colors: HomeCardColors,
    onConnectClick: () -> Unit,
    onStatusActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HomeConnectButton(model.status, colors, onConnectClick)
        Text(
            text = homeStatusText(model.status),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (model.serverName != null && model.status != HomeCardStatus.Empty) {
            Text(
                text = model.serverName,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        HomeStatusAction(model.status, onStatusActionClick)
    }
}

@Composable
private fun HomeConnectButton(status: HomeCardStatus, colors: HomeCardColors, onClick: () -> Unit) {
    val running = status == HomeCardStatus.Connected
    val busy = status == HomeCardStatus.Connecting
    val label = stringResource(if (running) R.string.acc_stop else R.string.acc_start)
    Surface(
        onClick = onClick,
        enabled = !busy && status != HomeCardStatus.Empty,
        shape = homeConnectShape(if (running) HomeConnectShape.Active else HomeConnectShape.Idle),
        color = colors.button,
        contentColor = colors.onButton,
        modifier = Modifier
            .size(168.dp)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = colors.onButton,
                )
            } else {
                Icon(
                    painter = painterResource(if (running) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun HomeStatusAction(status: HomeCardStatus, onClick: () -> Unit) {
    when (status) {
        HomeCardStatus.Empty -> Button(onClick = onClick) {
            Text(stringResource(R.string.home_action_choose_server))
        }

        HomeCardStatus.Error -> Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(stringResource(R.string.home_action_retry))
        }

        HomeCardStatus.DisconnectedUnexpected -> OutlinedButton(onClick = onClick) {
            Text(stringResource(R.string.home_action_reconnect))
        }

        HomeCardStatus.Disconnected, HomeCardStatus.Connecting, HomeCardStatus.Connected -> Unit
    }
}

@Composable
private fun HomeCardDetailsBlock(details: HomeCardDetails, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeDetailTile(
                label = stringResource(R.string.home_details_ping),
                value = stringResource(R.string.server_test_delay_value, details.pingMillis),
                modifier = Modifier.weight(1f),
            )
            HomeDetailTile(
                label = stringResource(R.string.home_details_duration),
                value = details.duration,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeDetailTile(
                label = stringResource(R.string.home_details_download),
                value = details.download,
                modifier = Modifier.weight(1f),
            )
            HomeDetailTile(
                label = stringResource(R.string.home_details_upload),
                value = details.upload,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One read-only figure; it is a single semantic node, so it is spoken as "label, value". */
@Composable
private fun HomeDetailTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.28f),
        contentColor = LocalContentColor.current,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun homeStatusText(status: HomeCardStatus): String = stringResource(
    when (status) {
        HomeCardStatus.Empty -> R.string.home_empty_title
        HomeCardStatus.Disconnected -> R.string.connection_not_connected
        HomeCardStatus.Connecting -> R.string.home_status_connecting
        HomeCardStatus.Connected -> R.string.home_status_connected
        HomeCardStatus.Error -> R.string.home_status_error
        HomeCardStatus.DisconnectedUnexpected -> R.string.home_status_unexpected
    }
)

private data class HomeCardColors(
    val container: Color,
    val content: Color,
    val button: Color,
    val onButton: Color,
)

@Composable
private fun homeCardColors(status: HomeCardStatus): HomeCardColors {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        HomeCardStatus.Connected, HomeCardStatus.Connecting -> HomeCardColors(
            scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary, scheme.onPrimary
        )

        HomeCardStatus.Error -> HomeCardColors(
            scheme.errorContainer, scheme.onErrorContainer, scheme.error, scheme.onError
        )

        HomeCardStatus.DisconnectedUnexpected -> HomeCardColors(
            scheme.tertiaryContainer, scheme.onTertiaryContainer, scheme.tertiary, scheme.onTertiary
        )

        HomeCardStatus.Empty, HomeCardStatus.Disconnected -> HomeCardColors(
            scheme.surfaceContainerHigh, scheme.onSurface, scheme.secondaryContainer, scheme.onSecondaryContainer
        )
    }
}
