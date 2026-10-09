// PortedFrom: MC1/Views/Chats/Sheets/ChannelInfoSheet.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.meshcoreone.android.core.l10n.generated.AppChatsStrings
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.ChannelFloodScope
import com.meshcoreone.android.core.model.uppercaseHexString
import com.meshcoreone.android.core.ui.sharedTouchTarget
import com.meshcoreone.android.feature.chats.channels.ChannelInfoOutcome
import com.meshcoreone.android.feature.chats.channels.ChannelInfoStateHolder
import kotlinx.coroutines.launch

/**
 * Channel info: header, favorite, flood scope, share QR/secret and the clear/delete actions. The QR image
 * is drawn by [qrContent] (renderer owned by the app layer). Region discovery/management screens are
 * not part of this composable (see docs/android/deviations/WP-310.md).
 */
@Composable
fun ChannelInfoContent(
    channel: ChannelDTO,
    holder: ChannelInfoStateHolder,
    onClearMessages: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    qrContent: @Composable (uri: String) -> Unit = {},
) {
    val state by holder.state.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(channel.name, style = MaterialTheme.typography.headlineSmall)
        Text(holder.typeLabel.copy(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(AppChatsStrings.chatsRowFavorite))
            Switch(state.isFavorite, { scope.launch { holder.setFavorite(it) } })
        }
        Text(stringResource(AppChatsStrings.chatsChannelInfoRegion), style = MaterialTheme.typography.titleSmall)
        val options = buildList<Pair<ChannelFloodScope, String>> {
            add(ChannelFloodScope.Inherit to (holder.deviceDefaultFloodScopeName ?: stringResource(AppChatsStrings.chatsChannelInfoRegionNotConfigured)))
            add(ChannelFloodScope.AllRegions to stringResource(AppChatsStrings.chatsChannelInfoRegionAllRegions))
            holder.knownRegions.forEach { add(ChannelFloodScope.Region(it) to it) }
        }
        options.forEach { (option, label) ->
            Row(
                Modifier.fillMaxWidth().sharedTouchTarget().clickable(role = Role.RadioButton) { scope.launch { holder.selectFloodScope(option) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(state.selectedFloodScope == option, null)
                Text(label, Modifier.padding(start = 8.dp))
            }
        }
        if (holder.showsShareSections) {
            Text(stringResource(AppChatsStrings.chatsChannelInfoShareChannel), style = MaterialTheme.typography.titleSmall)
            qrContent(holder.qrUri())
            Text(stringResource(AppChatsStrings.chatsChannelInfoScanToJoin), style = MaterialTheme.typography.bodySmall)
            val hex = channel.secret.uppercaseHexString()
            Text(stringResource(AppChatsStrings.chatsChannelInfoSecretKey), style = MaterialTheme.typography.labelMedium)
            Text(hex, fontFamily = FontFamily.Monospace)
            TextButton({ clipboard.setText(AnnotatedString(hex)) }, Modifier.sharedTouchTarget()) {
                Text(stringResource(AppChatsStrings.chatsChannelInfoCopy))
            }
            Text(stringResource(AppChatsStrings.chatsChannelInfoManualSharingFooter), style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let { Text(it.copy(), color = MaterialTheme.colorScheme.error) }
        TextButton({ confirmClear = true }, Modifier.sharedTouchTarget(), enabled = !state.isActionInProgress) {
            Text(stringResource(AppChatsStrings.chatsChannelInfoClearMessagesButton))
        }
        TextButton({ confirmDelete = true }, Modifier.sharedTouchTarget(), enabled = !state.isActionInProgress) {
            Text(stringResource(AppChatsStrings.chatsChannelInfoDeleteButton), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmClear) {
        ConfirmDialog(
            AppChatsStrings.chatsChannelInfoClearMessagesConfirmTitle, AppChatsStrings.chatsChannelInfoClearMessagesConfirmMessage,
            AppChatsStrings.chatsChannelInfoClearMessagesButton, { confirmClear = false },
        ) {
            confirmClear = false
            scope.launch { if (holder.clearMessages() == ChannelInfoOutcome.MESSAGES_CLEARED) onClearMessages() }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            AppChatsStrings.chatsChannelInfoDeleteConfirmTitle, AppChatsStrings.chatsChannelInfoDeleteConfirmMessage,
            AppChatsStrings.chatsChannelInfoDeleteButton, { confirmDelete = false },
        ) {
            confirmDelete = false
            scope.launch { if (holder.deleteChannel() == ChannelInfoOutcome.DELETED) onDelete() }
        }
    }
}

@Composable
private fun ConfirmDialog(title: Int, message: Int, confirm: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onConfirm) { Text(stringResource(confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onCancel) { Text(stringResource(AppChatsStrings.chatsCommonCancel)) } },
    )
}
