// PortedFrom: MC1/Views/Chats/Sheets/JoinChannelConfirmationSheet.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/Chats/ScanChannelQRView.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.meshcoreone.android.core.l10n.generated.AppChatsStrings
import com.meshcoreone.android.core.model.ChannelDTO
import com.meshcoreone.android.core.model.uppercaseHexString
import com.meshcoreone.android.core.ui.sharedTouchTarget
import com.meshcoreone.android.feature.chats.channels.ChannelJoinConfirmationStateHolder
import com.meshcoreone.android.feature.chats.channels.ChannelJoinContent
import com.meshcoreone.android.feature.chats.channels.ChannelSheetResult
import com.meshcoreone.android.feature.chats.channels.ScanChannelQrStateHolder
import com.meshcoreone.android.feature.chats.channels.content
import com.meshcoreone.android.feature.chats.list.ChannelLinkResult
import kotlinx.coroutines.launch

/** Confirmation shown for a `meshcore://channel/add` link tapped in a message. */
@Composable
fun ChannelJoinConfirmationContent(
    holder: ChannelJoinConfirmationStateHolder,
    link: ChannelLinkResult,
    onComplete: (ChannelDTO?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by holder.state.collectAsState()
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    LaunchedEffect(holder) { holder.loadAvailableSlots() }
    Column(modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(AppChatsStrings.chatsJoinFromMessageTitle), style = MaterialTheme.typography.titleLarge)
        when (state.content) {
            ChannelJoinContent.LOADING -> {
                CircularProgressIndicator()
                Text(stringResource(AppChatsStrings.chatsJoinFromMessageLoading))
            }
            ChannelJoinContent.MISSING_DEVICE -> {
                Text(stringResource(AppChatsStrings.chatsJoinFromMessageNoDeviceTitle), style = MaterialTheme.typography.titleMedium)
                Text(AppChatsStrings.chatsJoinFromMessageNoDeviceDescription(resources, link.name))
                TextButton({ onComplete(null) }, Modifier.sharedTouchTarget()) { Text(stringResource(AppChatsStrings.chatsCommonOk)) }
            }
            ChannelJoinContent.NO_SLOTS -> {
                Text(stringResource(AppChatsStrings.chatsJoinFromMessageNoSlotsTitle), style = MaterialTheme.typography.titleMedium)
                Text(AppChatsStrings.chatsJoinFromMessageNoSlotsDescription(resources, link.name))
                TextButton({ onComplete(null) }, Modifier.sharedTouchTarget()) { Text(stringResource(AppChatsStrings.chatsCommonOk)) }
            }
            ChannelJoinContent.CONFIRM -> {
                Text(link.name, style = MaterialTheme.typography.headlineSmall)
                Text(holder.truncatedSecret, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                link.regionScope?.let { Text(AppChatsStrings.chatsJoinFromMessageRegionScope(resources, it)) }
                if (holder.hasHashtagSecretMismatch) {
                    Text(stringResource(AppChatsStrings.chatsJoinFromMessageHashtagSecretMismatch), style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let { Text(it.copy(), color = MaterialTheme.colorScheme.error) }
                Button(
                    { scope.launch { (holder.join() as? ChannelSheetResult.Completed)?.let { onComplete(it.channel) } } },
                    Modifier.fillMaxWidth().sharedTouchTarget(), enabled = !state.isJoining,
                ) {
                    if (state.isJoining) CircularProgressIndicator() else Text(stringResource(AppChatsStrings.chatsJoinPrivateJoinButton))
                }
            }
        }
    }
}

/**
 * Scan-QR-to-join. [scanner] is the platform camera preview (it calls the supplied callbacks); it is
 * owned by the app layer because the camera/barcode stack is not part of this module.
 */
@Composable
fun ScanChannelQrContent(
    holder: ScanChannelQrStateHolder,
    onComplete: (ChannelDTO?) -> Unit,
    scanner: @Composable (onScan: (String) -> Unit, onPermissionDenied: () -> Unit) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by holder.state.collectAsState()
    val scope = rememberCoroutineScope()
    val scanned = state.scannedChannel
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(AppChatsStrings.chatsScanQRTitle), style = MaterialTheme.typography.titleLarge)
        when {
            scanned != null -> {
                Text(scanned.name, style = MaterialTheme.typography.titleMedium)
                Text(scanned.secret.uppercaseHexString(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                scanned.regionScope?.let { Text(it) }
                state.error?.let { Text(it.copy(), color = MaterialTheme.colorScheme.error) }
                Button(
                    { scope.launch { (holder.join() as? ChannelSheetResult.Completed)?.let { onComplete(it.channel) } } },
                    Modifier.fillMaxWidth().sharedTouchTarget(), enabled = !state.isJoining,
                ) { Text(stringResource(AppChatsStrings.chatsJoinPrivateJoinButton)) }
                TextButton(holder::scanAgain, Modifier.sharedTouchTarget()) { Text(stringResource(AppChatsStrings.chatsScanQRScanAgain)) }
            }
            state.cameraPermissionDenied -> {
                Text(stringResource(AppChatsStrings.chatsScanQRPermissionDeniedTitle), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(AppChatsStrings.chatsScanQRPermissionDeniedMessage))
                Button(onOpenSettings, Modifier.sharedTouchTarget()) { Text(stringResource(AppChatsStrings.chatsScanQROpenSettings)) }
            }
            else -> {
                scanner(holder::onScanResult, holder::onCameraPermissionDenied)
                Text(stringResource(AppChatsStrings.chatsScanQRInstruction), style = MaterialTheme.typography.bodyMedium)
                state.error?.let { Text(it.copy(), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
