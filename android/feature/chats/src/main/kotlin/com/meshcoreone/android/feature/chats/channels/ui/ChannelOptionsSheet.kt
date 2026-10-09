// PortedFrom: MC1/Views/Chats/Sheets/ChannelOptionsSheet.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.meshcoreone.android.core.l10n.generated.AppChatsStrings
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.ui.sharedTouchTarget
import com.meshcoreone.android.feature.chats.channels.ChannelOption
import com.meshcoreone.android.feature.chats.channels.ChannelOptionsFooter
import com.meshcoreone.android.feature.chats.channels.ChannelOptionsStateHolder
import androidx.compose.ui.draw.alpha

private data class OptionCopy(val title: Int, val description: Int)

private fun ChannelOption.copy() = when (this) {
    ChannelOption.CREATE_PRIVATE -> OptionCopy(
        AppChatsStrings.chatsChannelOptionsCreatePrivateTitle, AppChatsStrings.chatsChannelOptionsCreatePrivateDescription,
    )
    ChannelOption.JOIN_PRIVATE -> OptionCopy(
        AppChatsStrings.chatsChannelOptionsJoinPrivateTitle, AppChatsStrings.chatsChannelOptionsJoinPrivateDescription,
    )
    ChannelOption.SCAN_QR -> OptionCopy(
        AppChatsStrings.chatsChannelOptionsScanQRTitle, AppChatsStrings.chatsChannelOptionsScanQRDescription,
    )
    ChannelOption.JOIN_PUBLIC -> OptionCopy(
        AppChatsStrings.chatsChannelOptionsJoinPublicTitle, AppChatsStrings.chatsChannelOptionsJoinPublicDescription,
    )
    ChannelOption.JOIN_HASHTAG -> OptionCopy(
        AppChatsStrings.chatsChannelOptionsJoinHashtagTitle, AppChatsStrings.chatsChannelOptionsJoinHashtagDescription,
    )
}

/**
 * Channel creation/join options. Choosing an option calls [onSelect]; the host presents the matching
 * form (the sheets in this package) and calls [onChannelCreated] when one completes with a channel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelOptionsSheet(
    holder: ChannelOptionsStateHolder,
    onSelect: (ChannelOption) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by holder.state.collectAsState()
    LaunchedEffect(holder) { holder.load() }
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(AppChatsStrings.chatsChannelOptionsTitle), style = MaterialTheme.typography.titleLarge)
                TextButton(onDismiss, Modifier.sharedTouchTarget()) { Text(stringResource(AppChatsStrings.chatsCommonCancel)) }
            }
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                Text(stringResource(AppChatsStrings.chatsChannelOptionsLoading))
            } else {
                Section(AppChatsStrings.chatsChannelOptionsSectionPrivate) {
                    listOf(ChannelOption.CREATE_PRIVATE, ChannelOption.JOIN_PRIVATE, ChannelOption.SCAN_QR).forEach {
                        OptionRow(it, state.isEnabled(it), onSelect)
                    }
                }
                Section(AppChatsStrings.chatsChannelOptionsSectionPublic) {
                    listOf(ChannelOption.JOIN_PUBLIC, ChannelOption.JOIN_HASHTAG).forEach {
                        OptionRow(it, state.isEnabled(it), onSelect)
                    }
                }
                when (state.footer) {
                    ChannelOptionsFooter.NO_SLOTS -> Footer(AppChatsStrings.chatsChannelOptionsFooterNoSlots)
                    ChannelOptionsFooter.HAS_PUBLIC -> Footer(AppChatsStrings.chatsChannelOptionsFooterHasPublic)
                    ChannelOptionsFooter.NONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    content()
}

@Composable
private fun Footer(text: Int) =
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun OptionRow(option: ChannelOption, enabled: Boolean, onSelect: (ChannelOption) -> Unit) {
    val copy = option.copy()
    Column(
        Modifier.fillMaxWidth().sharedTouchTarget().alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, role = Role.Button) { onSelect(option) }.padding(vertical = 6.dp),
    ) {
        Text(stringResource(copy.title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(copy.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Completion hook the host uses for every channel form: `null` channel means the form closed without one. */
typealias ChannelFormCompletion = (ChannelDTO?) -> Unit
