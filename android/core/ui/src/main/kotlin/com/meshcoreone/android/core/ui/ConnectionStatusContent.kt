// PortedFrom: MC1/Views/Components/BLEStatusIndicatorView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Components/BLEStatusToolbarItem.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Components/SignalBars.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Components/SyncingPillView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Components/SyncingPillOverlay.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.designsystem.LocalMeshTheme
import com.meshcoreone.android.core.designsystem.MeshSymbol
import com.meshcoreone.android.core.designsystem.RadioColorRole
import com.meshcoreone.android.core.designsystem.toComposeColor
import com.meshcoreone.android.core.l10n.generated.AppLocalizableStrings as L
import com.meshcoreone.android.core.l10n.generated.AppSettingsStrings as S
import com.meshcoreone.android.core.model.DeviceDTO
import com.meshcoreone.android.core.protocol.model.BatteryInfo
import java.text.NumberFormat
import androidx.compose.ui.platform.LocalContext

data class RadioStatusState(
    val connection: DeviceConnectionState,
    val device: DeviceDTO?,
    val battery: BatteryInfo? = null,
    val isSendingAdvert: Boolean = false,
)

data class RadioStatusActions(
    val selectDevice: () -> Unit,
    val disconnect: () -> Unit,
    val sendAdvert: (flood: Boolean) -> Unit,
    val openAdvancedSettings: () -> Unit,
)

val DeviceConnectionState.statusResource: Int get() = when (this) {
    DeviceConnectionState.DISCONNECTED -> S.bleStatusStatusDisconnected
    DeviceConnectionState.CONNECTING -> S.bleStatusStatusConnecting
    DeviceConnectionState.CONNECTED -> S.bleStatusStatusConnected
    DeviceConnectionState.SYNCING -> S.bleStatusStatusSyncing
    DeviceConnectionState.READY -> S.bleStatusStatusReady
}

@Composable
fun BLEStatusIndicatorView(state: RadioStatusState, actions: RadioStatusActions, modifier: Modifier = Modifier) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tokens = LocalMeshTheme.current
    val role = if (state.device?.clientRepeat == true) RadioColorRole.REPEAT else when (state.connection) {
        DeviceConnectionState.DISCONNECTED -> RadioColorRole.DISCONNECTED
        DeviceConnectionState.CONNECTING -> RadioColorRole.CONNECTING
        DeviceConnectionState.CONNECTED -> RadioColorRole.CONNECTED
        DeviceConnectionState.SYNCING -> RadioColorRole.SYNCING
        DeviceConnectionState.READY -> RadioColorRole.READY
    }
    val status = uiString(UiText.Resource(state.connection.statusResource))
    val label = uiString(UiText.Resource(S.bleStatusAccessibilityLabel))
    val hint = uiString(UiText.Resource(if (state.device != null) S.bleStatusAccessibilityHintConnected
        else S.bleStatusAccessibilityHintDisconnected))
    val zeroHopHint = uiString(UiText.Resource(S.bleStatusSendZeroHopAdvertHint))
    val floodHint = uiString(UiText.Resource(S.bleStatusSendFloodAdvertHint))
    val color = tokens.roles.radioColor(role).toComposeColor()
    Box(modifier) {
        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.sharedTouchTarget().semantics {
            stateDescription = status
            onClick(label = hint, action = null)
        }) { RadioConnectionIcon(state.connection, color, label) }
        DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
            val device = state.device
            if (device == null) {
                DropdownMenuItem(
                    text = { Text(uiString(UiText.Resource(S.deviceConnect))) },
                    onClick = { menuExpanded = false; actions.selectDevice() },
                    modifier = Modifier.sharedTouchTarget(),
                )
            } else {
                if (device.clientRepeat) Text(uiString(UiText.Resource(S.bleStatusRepeatModeActive)),
                    Modifier.padding(16.dp), color = color)
                Text(device.nodeName, Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium)
                state.battery?.let { battery ->
                    val resources = LocalContext.current.resources
                    val number = NumberFormat.getNumberInstance(resourceLocale(resources)).also {
                        it.minimumFractionDigits = 2; it.maximumFractionDigits = 2
                    }
                    Text("${battery.percentage(device.activeOCVArray)}% (${number.format(battery.voltage)}v)",
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                DropdownMenuItem(text = { Text(uiString(UiText.Resource(S.bleStatusChangeDevice))) },
                    onClick = { menuExpanded = false; actions.selectDevice() }, modifier = Modifier.sharedTouchTarget())
                DropdownMenuItem(text = {
                    Text(uiString(UiText.Resource(S.bleStatusDisconnect)), color = sharedErrorForeground(sharedPaintedSurface()))
                }, onClick = { menuExpanded = false; actions.disconnect() }, modifier = Modifier.sharedTouchTarget())
                HorizontalDivider()
                DropdownMenuItem(text = { Text(uiString(UiText.Resource(S.bleStatusSendZeroHopAdvert))) },
                    onClick = { menuExpanded = false; actions.sendAdvert(false) },
                    enabled = radioActionEnabled(state.connection, state.isSendingAdvert),
                    modifier = Modifier.sharedTouchTarget().radioDisabledHint(state.connection)
                        .semantics { onClick(label = zeroHopHint, action = null) })
                DropdownMenuItem(text = { Text(uiString(UiText.Resource(S.bleStatusSendFloodAdvert))) },
                    onClick = { menuExpanded = false; actions.sendAdvert(true) },
                    enabled = radioActionEnabled(state.connection, state.isSendingAdvert),
                    modifier = Modifier.sharedTouchTarget().radioDisabledHint(state.connection)
                        .semantics { onClick(label = floodHint, action = null) })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(uiString(UiText.Resource(S.advancedSettingsTitle))) },
                    onClick = { menuExpanded = false; actions.openAdvancedSettings() }, modifier = Modifier.sharedTouchTarget())
            }
        }
    }
}

@Composable
internal fun RadioConnectionIcon(
    connection: DeviceConnectionState,
    tint: Color,
    label: String,
    modifier: Modifier = Modifier,
) {
    Icon(MeshSymbol.RADIO.vector, label, modifier.drawWithContent {
        drawContent()
        if (connection == DeviceConnectionState.DISCONNECTED) {
            drawLine(tint, Offset(size.width / 6, size.height / 6),
                Offset(size.width * 5 / 6, size.height * 5 / 6),
                strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
        }
    }, tint = tint)
}

@Composable
fun BLEStatusToolbarItem(state: RadioStatusState, actions: RadioStatusActions, modifier: Modifier = Modifier) =
    BLEStatusIndicatorView(state, actions, modifier)

@Composable
fun SignalBars(
    tier: RSSITuning.SignalTier,
    modifier: Modifier = Modifier,
    accessibilityLabel: UiText? = null,
    paintedSurface: androidx.compose.ui.graphics.Color = sharedPaintedSurface(),
) {
    val color = statusForeground(tier.colorRole, MaterialTheme.colorScheme.onSurface, paintedSurface,
        LocalMeshTheme.current.frame.highContrast)
    val description = accessibilityLabel?.let { uiString(it) }
    Canvas(modifier.size(24.dp).semantics {
        sharedPaintedSurface = paintedSurface
        sharedEmittedForeground = color
        if (description != null) contentDescription = description
    }) {
        val count = tier.rawValue.toInt() + 1
        for (index in 0..<3) {
            val height = size.height * (index + 1) / 3
            drawRect(color.copy(alpha = if (index < count) 1f else 0.2f),
                topLeft = Offset(size.width * index / 3, size.height - height),
                size = Size(size.width / 4, height))
        }
    }
}

sealed interface StatusPillState {
    data object Hidden : StatusPillState
    data object Connecting : StatusPillState
    data object Syncing : StatusPillState
    data object Ready : StatusPillState
    data object Disconnected : StatusPillState
    data class Failed(val message: UiText) : StatusPillState
    val isFailure: Boolean get() = this is Failed
    val text: UiText get() = when (this) {
        Hidden -> UiText.Verbatim("")
        Connecting -> UiText.Resource(L.commonStatusConnecting)
        Syncing -> UiText.Resource(L.commonStatusSyncing)
        Ready -> UiText.Resource(L.commonStatusReady)
        Disconnected -> UiText.Resource(L.commonStatusDisconnected)
        is Failed -> message
    }
    val symbol: MeshSymbol? get() = when (this) {
        Hidden -> null
        Connecting, Syncing -> MeshSymbol.SYNC
        Ready -> MeshSymbol.READY
        Disconnected -> MeshSymbol.WARNING
        is Failed -> MeshSymbol.ERROR
    }
}

data class SyncingPillActions(val onDisconnectedTap: (() -> Unit)?)

@Composable
fun SyncingPillView(state: StatusPillState, actions: SyncingPillActions, modifier: Modifier = Modifier) =
    SyncingPillView(state, modifier, actions.onDisconnectedTap)

@Composable
fun SyncingPillView(
    state: StatusPillState,
    modifier: Modifier = Modifier,
    onDisconnectedTap: (() -> Unit)? = null,
) {
    if (state == StatusPillState.Hidden) return
    val message = uiString(state.text)
    val background = if (state.isFailure) MaterialTheme.colorScheme.errorContainer else sharedPaintedSurface()
    val foreground = statusForeground(state.textColorRole, MaterialTheme.colorScheme.onSurface,
        background, LocalMeshTheme.current.frame.highContrast)
    val iconForeground = statusForeground(state.iconColorRole, MaterialTheme.colorScheme.onSurface,
        background, LocalMeshTheme.current.frame.highContrast)
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).sharedTouchTarget().semantics {
            stateDescription = message
            if (state == StatusPillState.Connecting || state == StatusPillState.Syncing) {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            }
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.symbol?.let { Icon(it.vector, null, tint = iconForeground) }
            Text(message, style = MaterialTheme.typography.bodyMedium,
                color = foreground,
                fontWeight = if (state.isFailure) androidx.compose.ui.text.font.FontWeight.Bold else null)
        }
    }
    val colors = modifier.semantics {
        sharedPaintedSurface = background
        sharedEmittedForeground = foreground
        sharedEmittedIcon = iconForeground
    }
    if (state == StatusPillState.Disconnected && onDisconnectedTap != null) {
        Surface(onClick = onDisconnectedTap, modifier = colors, shape = RoundedCornerShape(24.dp),
            color = background, contentColor = foreground, content = content)
    } else Surface(colors, shape = RoundedCornerShape(24.dp), color = background,
        contentColor = foreground, content = content)
}

@Composable
fun SyncingPillOverlay(
    state: StatusPillState,
    onDisconnectedTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var displayedState by remember { mutableStateOf<StatusPillState>(StatusPillState.Hidden) }
    val contentState = if (state == StatusPillState.Hidden) displayedState else state
    SideEffect { if (state != StatusPillState.Hidden) displayedState = state }
    val duration = LocalMeshTheme.current.duration(300)
    Box(modifier) {
        content()
        AnimatedVisibility(state != StatusPillState.Hidden, Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            enter = fadeIn(tween(duration)), exit = fadeOut(tween(duration))) {
            SyncingPillView(contentState,
                modifier = if (state == StatusPillState.Hidden) Modifier.clearAndSetSemantics {} else Modifier,
                onDisconnectedTap = if (state == StatusPillState.Hidden) null else onDisconnectedTap)
        }
    }
}
