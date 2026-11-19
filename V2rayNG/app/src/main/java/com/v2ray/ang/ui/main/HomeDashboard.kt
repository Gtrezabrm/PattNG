package com.v2ray.ang.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalReduceMotion
import kotlinx.coroutines.launch

/**
 * The dashboard look of the home screen: a card for the current server, the figures of the connection, and a wide
 * control at the bottom that connects when it is slid or tapped. It draws what it is given and owns no state; the
 * user's operations leave through the callbacks, the same ones the classic card uses.
 */
@Composable
fun HomeDashboard(
    model: HomeCardModel,
    onServerClick: () -> Unit,
    onConnectClick: () -> Unit,
    onRefreshPingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = model.status == HomeCardStatus.Connected
    Column(modifier = modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DashboardServerCard(model, onServerClick)
            if (connected && model.details != null) {
                HomeCardDetailsSection(model.details, onRefreshPingClick, showTitle = false)
            }
        }
        SlideToConnect(
            status = model.status,
            onToggle = onConnectClick,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun DashboardServerCard(model: HomeCardModel, onClick: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.home_change_server),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = model.serverName ?: stringResource(R.string.home_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = homeStatusText(model.status),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back_24dp),
                contentDescription = null,
                // The arrow of the back button points away from the next screen; turn it to point at it.
                modifier = Modifier.graphicsLayer { rotationZ = if (rtl) 0f else 180f },
            )
        }
    }
}

/**
 * A wide control: the knob is slid to the far end, or the whole control is tapped, to connect or disconnect; the tap
 * is what a screen reader, a keyboard or a switch uses. Nothing here animates except the knob returning to its place,
 * and that is a jump when the animations are reduced.
 */
@Composable
private fun SlideToConnect(status: HomeCardStatus, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val scope = rememberCoroutineScope()
    val active = status == HomeCardStatus.Connected || status == HomeCardStatus.Connecting
    val label = if (active) stringResource(R.string.home_slide_to_disconnect) else stringResource(R.string.home_slide_to_connect)
    val statusText = homeStatusText(status)
    val trackColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val onTrack = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val knobColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val onKnob = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
    val knobSize = 60.dp
    val inset = 8.dp
    val trackHeight = knobSize + inset * 2
    // The knob moves from the left to the right in every language, so that the gesture is the same everywhere.
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(
            modifier = modifier
                .height(trackHeight)
                .background(trackColor, RoundedCornerShape(trackHeight / 2))
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    stateDescription = statusText
                }
                .clickableAsButton(onToggle),
        ) {
            val density = LocalDensity.current
            val travel = with(density) { (maxWidth - knobSize - inset * 2).toPx() }.coerceAtLeast(1f)
            val offset = remember { Animatable(0f) }
            Row(
                modifier = Modifier.fillMaxWidth().height(trackHeight).padding(start = knobSize + inset * 2, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = statusText, color = onTrack, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.size(8.dp))
                Text(text = label, color = onTrack.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier
                    .padding(inset)
                    .offset { androidx.compose.ui.unit.IntOffset(offset.value.toInt(), 0) }
                    .size(knobSize)
                    .background(knobColor, CircleShape)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, travel)) } },
                        onDragStopped = {
                            val done = offset.value >= travel * 0.7f
                            scope.launch {
                                if (reduceMotion) offset.snapTo(0f) else offset.animateTo(0f, spring())
                            }
                            if (done) onToggle()
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painter = painterResource(if (active) R.drawable.ic_stop_24dp else R.drawable.ic_play_24dp), contentDescription = null, tint = onKnob)
            }
        }
    }
}

private fun Modifier.clickableAsButton(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(role = Role.Button, onClick = onClick))
