// PortedFrom: MC1/Views/Chats/CreatePrivateChannelView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Sheets/JoinPrivateChannelView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Sheets/JoinPublicChannelView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Sheets/JoinHashtagChannelView.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.meshcoreone.android.core.l10n.generated.AppChatsStrings
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.uppercaseHexString
import com.meshcoreone.android.core.ui.sharedTouchTarget
import com.meshcoreone.android.feature.chats.channels.ChannelSheetError
import com.meshcoreone.android.feature.chats.channels.ChannelSheetResult
import com.meshcoreone.android.feature.chats.channels.CreatePrivateChannelStateHolder
import com.meshcoreone.android.feature.chats.channels.JoinHashtagChannelStateHolder
import com.meshcoreone.android.feature.chats.channels.JoinPrivateChannelStateHolder
import com.meshcoreone.android.feature.chats.channels.JoinPublicChannelStateHolder
import kotlinx.coroutines.launch

@Composable
private fun FormColumn(modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content()
    }
}

@Composable
private fun ErrorLine(error: ChannelSheetError?) {
    if (error != null) Text(error.copy(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ActionButton(label: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().sharedTouchTarget(), enabled = enabled) {
        if (busy) CircularProgressIndicator(Modifier.padding(2.dp)) else Text(label)
    }
}

/** Create-private-channel form that turns into the share content (QR + secret) once created. */
@Composable
fun CreatePrivateChannelForm(
    holder: CreatePrivateChannelStateHolder,
    onComplete: (ChannelDTO?) -> Unit,
    modifier: Modifier = Modifier,
    qrContent: @Composable (uri: String) -> Unit = {},
) {
    val state by holder.state.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(holder) { holder.start() }
    FormColumn(modifier) {
        val secretHex = state.secret?.uppercaseHexString().orEmpty()
        if (!state.isCreated) {
            Text(stringResource(AppChatsStrings.chatsCreatePrivateTitleCreate), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                state.channelName, holder::setChannelName, Modifier.fillMaxWidth().sharedTouchTarget(), singleLine = true,
                label = { Text(stringResource(AppChatsStrings.chatsCreatePrivateChannelName)) },
            )
            Text(stringResource(AppChatsStrings.chatsChannelInfoSecretKey), style = MaterialTheme.typography.labelMedium)
            Text(secretHex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Text(stringResource(AppChatsStrings.chatsCreatePrivateSecretFooter), style = MaterialTheme.typography.bodySmall)
            ErrorLine(state.error)
            ActionButton(stringResource(AppChatsStrings.chatsCreatePrivateCreateButton), state.isCreating, state.canCreate) {
                scope.launch { holder.create() }
            }
        } else {
            Text(stringResource(AppChatsStrings.chatsCreatePrivateTitleShare), style = MaterialTheme.typography.titleLarge)
            holder.shareUri()?.let { qrContent(it) }
            Text(state.channelName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(AppChatsStrings.chatsChannelInfoScanToJoin), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(AppChatsStrings.chatsCreatePrivateSectionShareManually), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                SelectionContainer { Text(secretHex, fontFamily = FontFamily.Monospace) }
                TextButton({ clipboard.setText(AnnotatedString(secretHex)) }, Modifier.sharedTouchTarget()) {
                    Text(stringResource(AppChatsStrings.chatsChannelInfoCopy))
                }
            }
            Text(stringResource(AppChatsStrings.chatsCreatePrivateShareManuallyFooter), style = MaterialTheme.typography.bodySmall)
            TextButton({ onComplete(state.createdChannel) }, Modifier.sharedTouchTarget()) {
                Text(stringResource(AppChatsStrings.chatsCommonDone))
            }
        }
    }
}

/** Join-private-channel form: name plus 32-hex-digit secret. */
@Composable
fun JoinPrivateChannelForm(holder: JoinPrivateChannelStateHolder, onComplete: (ChannelDTO?) -> Unit, modifier: Modifier = Modifier) {
    val state by holder.state.collectAsState()
    val scope = rememberCoroutineScope()
    FormColumn(modifier) {
        Text(stringResource(AppChatsStrings.chatsJoinPrivateTitle), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            state.channelName, holder::setChannelName, Modifier.fillMaxWidth().sharedTouchTarget(), singleLine = true,
            label = { Text(stringResource(AppChatsStrings.chatsCreatePrivateChannelName)) },
        )
        OutlinedTextField(
            state.secretKeyHex, holder::setSecretKeyHex, Modifier.fillMaxWidth().sharedTouchTarget(), singleLine = true,
            label = { Text(stringResource(AppChatsStrings.chatsJoinPrivateSecretKeyPlaceholder)) },
        )
        if (state.showsInvalidSecretFooter) {
            Text(stringResource(AppChatsStrings.chatsJoinPrivateErrorInvalidSecret), color = MaterialTheme.colorScheme.error)
        } else {
            Text(stringResource(AppChatsStrings.chatsJoinPrivateFooter), style = MaterialTheme.typography.bodySmall)
        }
        ErrorLine(state.error)
        ActionButton(stringResource(AppChatsStrings.chatsJoinPrivateJoinButton), state.isJoining, state.canJoin) {
            scope.launch { (holder.join() as? ChannelSheetResult.Completed)?.let { onComplete(it.channel) } }
        }
    }
}

/** Re-adds the public channel on slot 0. */
@Composable
fun JoinPublicChannelForm(holder: JoinPublicChannelStateHolder, onComplete: (ChannelDTO?) -> Unit, modifier: Modifier = Modifier) {
    val state by holder.state.collectAsState()
    val scope = rememberCoroutineScope()
    FormColumn(modifier) {
        Text(stringResource(AppChatsStrings.chatsJoinPublicTitle), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(AppChatsStrings.chatsJoinPublicChannelName), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(AppChatsStrings.chatsJoinPublicDescription), style = MaterialTheme.typography.bodyMedium)
        ErrorLine(state.error)
        ActionButton(stringResource(AppChatsStrings.chatsJoinPublicAddButton), state.isJoining, !state.isJoining) {
            scope.launch { (holder.join() as? ChannelSheetResult.Completed)?.let { onComplete(it.channel) } }
        }
    }
}

/** Joins a hashtag channel, or goes to it when it is already joined. */
@Composable
fun JoinHashtagChannelForm(holder: JoinHashtagChannelStateHolder, onComplete: (ChannelDTO?) -> Unit, modifier: Modifier = Modifier) {
    val state by holder.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(holder) { holder.loadExistingChannels() }
    val existing = state.existingChannel
    FormColumn(modifier) {
        Text(stringResource(AppChatsStrings.chatsJoinHashtagTitle), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            state.channelName, holder::setChannelName, Modifier.fillMaxWidth().sharedTouchTarget(), singleLine = true,
            prefix = { Text("#") },
            placeholder = { Text(stringResource(AppChatsStrings.chatsJoinHashtagPlaceholder)) },
            label = { Text(stringResource(AppChatsStrings.chatsJoinHashtagSectionHeader)) },
        )
        Text(stringResource(AppChatsStrings.chatsJoinHashtagFooter), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(AppChatsStrings.chatsJoinHashtagEncryptionDescription), style = MaterialTheme.typography.bodySmall)
        ErrorLine(state.error)
        val label = if (existing != null) AppChatsStrings.chatsJoinHashtagGoToButton(androidx.compose.ui.platform.LocalContext.current.resources, state.channelName)
        else AppChatsStrings.chatsJoinHashtagJoinButton(androidx.compose.ui.platform.LocalContext.current.resources, state.channelName)
        ActionButton(label, state.isJoining && existing == null, state.isActionEnabled) {
            scope.launch { (holder.performPrimaryAction() as? ChannelSheetResult.Completed)?.let { onComplete(it.channel) } }
        }
        if (existing != null) Text(stringResource(AppChatsStrings.chatsJoinHashtagAlreadyJoined), style = MaterialTheme.typography.bodySmall)
    }
}
