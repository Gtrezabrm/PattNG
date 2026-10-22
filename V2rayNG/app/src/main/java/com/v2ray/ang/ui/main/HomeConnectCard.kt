package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R

/** What the home card shows; [HomeConnection.cardStatus] maps the state of the main screen to it. */
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

/** The figures of a running connection that the state has; [download] and [upload] stay null until it has them. */
@Immutable
data class HomeCardDetails(
    val pingMillis: Int?,
    val duration: String,
    val download: String? = null,
    val upload: String? = null,
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
 * It owns no state beyond what it is given and whether its details are open; every user operation leaves through a
 * callback. Colors, size and the button spring from one state to the next.
 */
@Composable
fun HomeConnectCard(
    model: HomeCardModel,
    onConnectClick: () -> Unit,
    onStatusActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = homeCardColors(model.status)
    val colorSpec = spring<Color>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    val colors = HomeCardColors(
        container = animateColorAsState(target.container, colorSpec, label = "home-card-container").value,
        content = animateColorAsState(target.content, colorSpec, label = "home-card-content").value,
        button = animateColorAsState(target.button, colorSpec, label = "home-card-button").value,
        onButton = animateColorAsState(target.onButton, colorSpec, label = "home-card-on-button").value,
    )
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    val showDetails = model.status == HomeCardStatus.Connected && model.details != null

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
                        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeCardMain(model, colors, onConnectClick, onStatusActionClick, modifier = Modifier.weight(1f))
                    if (showDetails) {
                        HomeCardDetailsSection(
                            details = model.details!!,
                            open = detailsOpen,
                            onToggle = { detailsOpen = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    HomeCardMain(model, colors, onConnectClick, onStatusActionClick)
                    if (showDetails) {
                        HomeCardDetailsSection(
                            details = model.details!!,
                            open = detailsOpen,
                            onToggle = { detailsOpen = it },
                        )
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
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "home-button-scale",
    )
    Surface(
        onClick = onClick,
        enabled = !busy && status != HomeCardStatus.Empty,
        shape = homeConnectShape(if (running) HomeConnectShape.Active else HomeConnectShape.Idle),
        color = colors.button,
        contentColor = colors.onButton,
        interactionSource = interaction,
        modifier = Modifier
            .size(168.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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

/** The header that opens and closes the figures, and the figures; open only on the user's say, and only while connected. */
@Composable
private fun HomeCardDetailsSection(
    details: HomeCardDetails,
    open: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (open) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "home-details-arrow",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = open, role = Role.Button, onValueChange = onToggle)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.home_details_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Icon(
                painter = painterResource(R.drawable.ic_expand_more_24dp),
                contentDescription = null,
                modifier = Modifier.graphicsLayer { rotationZ = rotation },
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
        ) {
            HomeCardDetailsBlock(details)
        }
    }
}

@Composable
private fun HomeCardDetailsBlock(details: HomeCardDetails, modifier: Modifier = Modifier) {
    val pingText = details.pingMillis?.let { stringResource(R.string.server_test_delay_value, it) }
        ?: stringResource(R.string.value_unknown)
    val tiles = buildList {
        add(stringResource(R.string.home_details_ping) to pingText)
        add(stringResource(R.string.home_details_duration) to details.duration)
        details.download?.let { add(stringResource(R.string.home_details_download) to it) }
        details.upload?.let { add(stringResource(R.string.home_details_upload) to it) }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { (label, value) ->
                    HomeDetailTile(label = label, value = value, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Box(modifier = Modifier.weight(1f))
            }
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
