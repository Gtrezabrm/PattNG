package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val ipAddress: String? = null,
    val countryCode: String? = null,
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
    /**
     * Whether the service already runs while [HomeCardStatus.Connecting], so that the button stops it. An Aether
     * profile can take minutes to connect, and the user must be able to give up on it.
     */
    val cancellable: Boolean = false,
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
    onRefreshPingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = homeCardColors(model.status)
    val colorSpec = spring<Color>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
    val colors = HomeCardColors(
        container = animateColorAsState(target.container, colorSpec, label = "home-card-container").value,
        containerEnd = animateColorAsState(target.containerEnd, colorSpec, label = "home-card-container-end").value,
        content = animateColorAsState(target.content, colorSpec, label = "home-card-content").value,
        button = animateColorAsState(target.button, colorSpec, label = "home-card-button").value,
        onButton = animateColorAsState(target.onButton, colorSpec, label = "home-card-on-button").value,
    )
    val showDetails = model.status == HomeCardStatus.Connected && model.details != null
    // Keep the last figures while the details fold away, so that they do not blank out during the exit animation.
    var lastDetails by remember { mutableStateOf<HomeCardDetails?>(null) }
    LaunchedEffect(model.details) { if (model.details != null) lastDetails = model.details }
    val shownDetails = model.details ?: lastDetails
    val detailsEnter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(350))
    val detailsExit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(200))

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val cardShape = RoundedCornerShape(40.dp)
        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent,
                contentColor = colors.content,
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = if (landscape) 720.dp else 480.dp)
                .padding(16.dp)
                .background(Brush.verticalGradient(listOf(colors.container, colors.containerEnd)), cardShape)
                .verticalScroll(rememberScrollState()),
        ) {
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow))
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeCardMain(model, colors, onConnectClick, onStatusActionClick, modifier = Modifier.weight(1f))
                    AnimatedVisibility(
                        visible = showDetails && shownDetails != null,
                        enter = detailsEnter,
                        exit = detailsExit,
                        modifier = Modifier.weight(1f),
                    ) {
                        shownDetails?.let { HomeCardDetailsSection(it, onRefreshPingClick) }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    HomeCardMain(model, colors, onConnectClick, onStatusActionClick)
                    AnimatedVisibility(
                        visible = showDetails && shownDetails != null,
                        enter = detailsEnter,
                        exit = detailsExit,
                    ) {
                        shownDetails?.let { HomeCardDetailsSection(it, onRefreshPingClick) }
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
        HomeConnectButton(model.status, model.cancellable, colors, onConnectClick)
        AnimatedContent(
            targetState = model.status,
            transitionSpec = { fadeIn(tween(300, delayMillis = 90)) togetherWith fadeOut(tween(150)) },
            label = "home-status-text",
        ) { status ->
            Text(
                text = homeStatusText(status),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
        }
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
private fun HomeConnectButton(
    status: HomeCardStatus,
    cancellable: Boolean,
    colors: HomeCardColors,
    onClick: () -> Unit,
) {
    val running = status == HomeCardStatus.Connected
    val busy = status == HomeCardStatus.Connecting
    val label = stringResource(if (running || (busy && cancellable)) R.string.acc_stop else R.string.acc_start)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "home-button-scale",
    )
    // The outline morphs between the idle and the running cookie, and the whole button breathes and turns gently.
    val morph by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "home-button-morph",
    )
    val alive = running || busy
    // The endless motion exists only while the connection is alive; an idle screen draws no frames of its own.
    val motion = if (alive) rememberHomeButtonMotion(busy) else null
    val haloAlpha by animateFloatAsState(
        targetValue = if (alive) 1f else 0f,
        animationSpec = tween(500),
        label = "home-button-halo",
    )
    val shape = remember(morph) { homeConnectMorphShape(morph) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(200.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        // Soft halo behind the button; drawn in the layer so that the pulse does not recompose anything.
        Box(
            modifier = Modifier
                .size(168.dp)
                .graphicsLayer {
                    val pulse = motion?.pulse?.value ?: 0f
                    val grow = 1.08f + 0.14f * pulse
                    scaleX = grow
                    scaleY = grow
                    alpha = haloAlpha * (0.30f - 0.18f * pulse)
                }
                .background(colors.button, CircleShape),
        )
        Box(
            modifier = Modifier
                .size(168.dp)
                .graphicsLayer {
                    rotationZ = motion?.turn?.value ?: 0f
                    val breathe = 1f + 0.025f * (motion?.pulse?.value ?: 0f)
                    scaleX = breathe
                    scaleY = breathe
                }
                .background(colors.button, shape),
        )
        Surface(
            onClick = onClick,
            enabled = (!busy || cancellable) && status != HomeCardStatus.Empty,
            shape = shape,
            color = Color.Transparent,
            contentColor = colors.onButton,
            interactionSource = interaction,
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
}

private class HomeButtonMotion(val turn: State<Float>, val pulse: State<Float>)

@Composable
private fun rememberHomeButtonMotion(busy: Boolean): HomeButtonMotion {
    val transition = rememberInfiniteTransition(label = "home-button-motion")
    val turn = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (busy) 6_000 else 24_000, easing = LinearEasing)),
        label = "home-button-turn",
    )
    val pulse = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (busy) 900 else 2_600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "home-button-pulse",
    )
    return HomeButtonMotion(turn, pulse)
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

/** Connection details appear automatically while connected. */
@Composable
private fun HomeCardDetailsSection(
    details: HomeCardDetails,
    onRefreshPingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.home_details_title), style = MaterialTheme.typography.titleMedium)
        HomeCardDetailsBlock(details, onRefreshPingClick)
    }
}

@Composable
private fun HomeCardDetailsBlock(
    details: HomeCardDetails,
    onRefreshPingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val measuring = stringResource(R.string.connection_test_testing)
    val pingText = details.pingMillis?.let { stringResource(R.string.server_test_delay_value, it) } ?: measuring
    val tiles = buildList {
        add(stringResource(R.string.home_details_ping) to pingText)
        add(stringResource(R.string.home_details_duration) to details.duration)
        details.download?.let { add(stringResource(R.string.home_details_download) to it) }
        details.upload?.let { add(stringResource(R.string.home_details_upload) to it) }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { (label, value) ->
                    HomeDetailTile(label = label, value = value, pending = value == measuring, modifier = Modifier.weight(1f))
                }
            }
        }
        HomeIpDetailTile(
            label = stringResource(R.string.home_details_ip),
            ipAddress = details.ipAddress?.takeIf { it.isNotBlank() } ?: measuring,
            countryCode = details.countryCode,
        )
        OutlinedButton(onClick = onRefreshPingClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_details_test_ping))
        }
    }
}

/** One read-only figure; it is a single semantic node, so it is spoken as "label, value". */
@Composable
private fun HomeIpDetailTile(label: String, ipAddress: String, countryCode: String?, modifier: Modifier = Modifier) {
    val normalizedCountry = normalizeCountryCode(countryCode)
    val flag = countryFlagEmoji(countryCode)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
        contentColor = LocalContentColor.current,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.42f)),
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (normalizedCountry == "IR") {
                    Image(
                        painter = painterResource(R.drawable.ic_flag_iran_lion_sun),
                        contentDescription = null,
                        modifier = Modifier.size(width = 32.dp, height = 18.dp).clip(RoundedCornerShape(3.dp)),
                    )
                } else if (flag.isNotEmpty()) {
                    Text(text = flag, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = ipAddress,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HomeDetailTile(label: String, value: String, pending: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.38f),
        contentColor = LocalContentColor.current,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.42f)),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            // One line of a fixed height, so that the tile keeps its size when the figure replaces "Testing…".
            Box(modifier = Modifier.height(32.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = value,
                    style = if (pending) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    val containerEnd: Color,
    val content: Color,
    val button: Color,
    val onButton: Color,
)

@Composable
private fun homeCardColors(status: HomeCardStatus): HomeCardColors {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        HomeCardStatus.Connected, HomeCardStatus.Connecting -> HomeCardColors(
            scheme.primaryContainer, scheme.tertiaryContainer, scheme.onPrimaryContainer, scheme.primary, scheme.onPrimary
        )

        HomeCardStatus.Error -> HomeCardColors(
            scheme.errorContainer, scheme.surfaceContainerHigh, scheme.onErrorContainer, scheme.error, scheme.onError
        )

        HomeCardStatus.DisconnectedUnexpected -> HomeCardColors(
            scheme.tertiaryContainer, scheme.surfaceContainerHigh, scheme.onTertiaryContainer, scheme.tertiary, scheme.onTertiary
        )

        HomeCardStatus.Empty, HomeCardStatus.Disconnected -> HomeCardColors(
            scheme.surfaceContainerHigh, scheme.surfaceContainerHighest, scheme.onSurface, scheme.secondaryContainer, scheme.onSecondaryContainer
        )
    }
}
