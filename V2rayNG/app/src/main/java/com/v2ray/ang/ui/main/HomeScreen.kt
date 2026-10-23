package com.v2ray.ang.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.AppTopBar
import kotlinx.coroutines.delay

/**
 * The home screen: the connection card over the selector of the server. The list of servers is a screen of its own,
 * which [onOpenServerList] opens. State and operations belong to [mainViewModel] and [onAction]; the only state here
 * is the ticking of the duration, which is presentation.
 */
@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    onAction: (MainAction) -> Unit,
    onMenuClick: () -> Unit,
    onOpenServerList: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val serverName by mainViewModel.selectedServerName.collectAsStateWithLifecycle()
    val status = HomeConnection.cardStatus(uiState)

    // Test the active route once when it reaches Connected; never poll in a loop.
    LaunchedEffect(status, uiState.connectionPingMillis) {
        if (status == HomeCardStatus.Connected && uiState.connectionPingMillis == null) {
            onAction(MainAction.TestCurrentServer)
        }
    }

    val since = uiState.connectedSinceNanos
    val duration by produceState(initialValue = HomeConnection.formatDuration(0), since) {
        if (since == null) {
            value = HomeConnection.formatDuration(0)
            return@produceState
        }
        while (true) {
            value = HomeConnection.formatDuration((System.nanoTime() - since) / NANOS_PER_SECOND)
            delay(1_000)
        }
    }

    val model = HomeCardModel(
        status = status,
        serverName = serverName,
        details = if (status == HomeCardStatus.Connected) {
            HomeCardDetails(
                pingMillis = uiState.connectionPingMillis?.toInt(),
                duration = duration,
                ipAddress = uiState.connectionIpAddress,
            )
        } else {
            null
        },
        cancellable = uiState.isRunning,
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.app_name),
                onBackClick = {},
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            painterResource(R.drawable.ic_menu_24dp),
                            contentDescription = stringResource(R.string.acc_open_menu),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HomeConnectCard(
                model = model,
                onConnectClick = { onAction(MainAction.ToggleService) },
                onStatusActionClick = {
                    // Only the user starts the service: a failed or dropped connection is not retried by itself.
                    if (status == HomeCardStatus.Empty) onOpenServerList() else onAction(MainAction.ToggleService)
                },
                onRefreshPingClick = { onAction(MainAction.TestCurrentServer) },
                modifier = Modifier.weight(1f),
            )
            HomeServerSelector(
                serverName = serverName,
                onClick = onOpenServerList,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            HomeQuickActions(
                onOpenServerList = onOpenServerList,
                onNavigate = onNavigate,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000L

/** One row that opens the list of servers; it is one semantic node, spoken with the server it would change. */
@Composable
private fun HomeServerSelector(serverName: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = serverName ?: stringResource(R.string.home_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.home_change_server),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}


@Composable
private fun HomeQuickActions(
    onOpenServerList: () -> Unit,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onOpenServerList) {
            Icon(painterResource(R.drawable.ic_subscriptions_24dp), contentDescription = null)
            Text(stringResource(R.string.home_quick_servers), modifier = Modifier.padding(start = 6.dp))
        }
        TextButton(onClick = { onNavigate(MainDestination.Subscriptions) }) {
            Icon(painterResource(R.drawable.ic_subscriptions_24dp), contentDescription = null)
            Text(stringResource(R.string.home_quick_subscriptions), modifier = Modifier.padding(start = 6.dp))
        }
        TextButton(onClick = { onNavigate(MainDestination.Settings) }) {
            Icon(painterResource(R.drawable.ic_settings_24dp), contentDescription = null)
            Text(stringResource(R.string.home_quick_settings), modifier = Modifier.padding(start = 6.dp))
        }
    }
}
