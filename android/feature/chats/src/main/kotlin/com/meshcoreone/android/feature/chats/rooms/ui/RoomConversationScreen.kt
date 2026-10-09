// PortedFrom: MC1/Views/RemoteNodes/Rooms/RoomConversationView.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/RemoteNodes/Rooms/RoomMessageBubble.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/Room/RoomAuthenticationSheet.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.rooms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshcoreone.android.core.l10n.generated.AppChatsStrings
import com.meshcoreone.android.core.l10n.generated.AppRemoteNodesStrings
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.RemoteNodeSessionDTO
import com.meshcoreone.android.core.ui.sharedTouchTarget
import com.meshcoreone.android.feature.chats.rooms.RoomAuthenticationContent
import com.meshcoreone.android.feature.chats.rooms.RoomAuthenticationStateHolder
import com.meshcoreone.android.feature.chats.rooms.RoomConversationStateHolder
import com.meshcoreone.android.feature.chats.rooms.RoomMessageStatusText
import com.meshcoreone.android.feature.chats.rooms.RoomTiledRow
import com.meshcoreone.android.feature.chats.rooms.statusText
import kotlinx.coroutines.launch

@Composable
private fun RoomMessageStatusText.label(): String = stringResource(
    when (this) {
        RoomMessageStatusText.SENDING -> AppChatsStrings.chatsMessageStatusSending
        RoomMessageStatusText.SENT -> AppChatsStrings.chatsMessageStatusSent
        RoomMessageStatusText.DELIVERED -> AppChatsStrings.chatsMessageStatusDelivered
        RoomMessageStatusText.FAILED -> AppChatsStrings.chatsMessageStatusFailed
        RoomMessageStatusText.RETRYING -> AppChatsStrings.chatsMessageStatusRetrying
    },
)

/** Room conversation: timeline plus a composer that only posts when the session may post. */
@Composable
fun RoomConversationScreen(
    session: RemoteNodeSessionDTO,
    holder: RoomConversationStateHolder,
    modifier: Modifier = Modifier,
) {
    val state by holder.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(session.id) { holder.loadMessages(session) }
    val canPost = (state.session ?: session).canPost
    Column(modifier.fillMaxSize()) {
        state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        when {
            !state.hasLoadedOnce -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            state.tiledRows.isEmpty() -> Text(stringResource(AppRemoteNodesStrings.remoteNodesRoomNoMessagesYet), Modifier.padding(16.dp))
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.tiledRows, key = { it.id }) { row -> RoomMessageRow(row, onRetry = { scope.launch { holder.retryMessage(row.id) } }) }
            }
        }
        if (canPost) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.composingText, holder::setComposingText, Modifier.weight(1f).sharedTouchTarget())
                Button({ scope.launch { holder.sendMessage(state.composingText) } }, Modifier.sharedTouchTarget(), enabled = !state.isSending && state.composingText.isNotEmpty()) {
                    Text(stringResource(AppRemoteNodesStrings.remoteNodesRoomPublicMessage))
                }
            }
        } else {
            Text(stringResource(AppRemoteNodesStrings.remoteNodesRoomViewOnlyBanner), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RoomMessageRow(row: RoomTiledRow, onRetry: () -> Unit) {
    val message = row.message
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalAlignment = if (message.isFromSelf) Alignment.End else Alignment.Start) {
        if (row.showSenderName) Text(message.authorDisplayName, style = MaterialTheme.typography.labelMedium)
        Text(message.text, style = MaterialTheme.typography.bodyLarge)
        if (message.isFromSelf) Text(message.statusText.label(), style = MaterialTheme.typography.labelSmall)
        if (message.isFromSelf && message.statusText == RoomMessageStatusText.FAILED) {
            Button(onRetry, Modifier.sharedTouchTarget()) { Text(stringResource(AppChatsStrings.chatsMessageStatusRetry)) }
        }
    }
}

/**
 * Room login: resolves the room's contact, then hosts [authenticationContent] (the shared node login
 * sheet owned by the remote-nodes feature, supplied by the app layer).
 */
@Composable
fun RoomAuthenticationScreen(
    session: RemoteNodeSessionDTO,
    holder: RoomAuthenticationStateHolder,
    authenticationContent: @Composable (ContactDTO) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content by holder.content.collectAsState()
    LaunchedEffect(session.id) { holder.load(session) }
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        when (val current = content) {
            RoomAuthenticationContent.Loading -> CircularProgressIndicator()
            is RoomAuthenticationContent.Ready -> authenticationContent(current.contact)
            RoomAuthenticationContent.NotFound -> {
                Text(stringResource(AppChatsStrings.chatsRoomAuthNotFoundTitle), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(AppChatsStrings.chatsRoomAuthNotFoundDescription))
            }
        }
    }
}
