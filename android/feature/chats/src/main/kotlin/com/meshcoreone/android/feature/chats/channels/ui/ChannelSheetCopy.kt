// AndroidOnly: WP-310 Resource-backed copy for the channel sheets (the Swift views read L10n inline).
package com.meshcoreone.android.feature.chats.channels.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meshcoreone.android.core.l10n.generated.AppChatsStrings
import com.meshcoreone.android.feature.chats.channels.ChannelSheetError
import com.meshcoreone.android.feature.chats.channels.ChannelTypeLabel
import com.meshcoreone.android.feature.chats.channels.RegionDiscoveryMessage
import com.meshcoreone.android.feature.chats.channels.ScanChannelQrError

@Composable
internal fun ChannelSheetError.copy(): String = when (this) {
    ChannelSheetError.NoDeviceConnected -> stringResource(AppChatsStrings.chatsErrorNoDeviceConnected)
    ChannelSheetError.ServicesUnavailable -> stringResource(AppChatsStrings.chatsErrorServicesUnavailable)
    ChannelSheetError.InvalidFormat -> stringResource(AppChatsStrings.chatsJoinPrivateErrorInvalidFormat)
    ChannelSheetError.NoSlots -> stringResource(AppChatsStrings.chatsJoinFromMessageErrorNoSlots)
    ChannelSheetError.InvalidName -> stringResource(AppChatsStrings.chatsJoinFromMessageErrorInvalidName)
    ChannelSheetError.LoadFailed -> stringResource(AppChatsStrings.chatsJoinFromMessageErrorLoadFailed)
    is ChannelSheetError.Failure -> message ?: stringResource(AppChatsStrings.chatsErrorServicesUnavailable)
}

@Composable
internal fun ScanChannelQrError.copy(): String = when (this) {
    ScanChannelQrError.InvalidFormat -> stringResource(AppChatsStrings.chatsScanQRErrorInvalidFormat)
    is ScanChannelQrError.Join -> error.copy()
}

@Composable
internal fun ChannelTypeLabel.copy(): String = stringResource(
    when (this) {
        ChannelTypeLabel.PUBLIC -> AppChatsStrings.chatsChannelInfoChannelTypePublic
        ChannelTypeLabel.HASHTAG -> AppChatsStrings.chatsChannelInfoChannelTypeHashtag
        ChannelTypeLabel.PRIVATE -> AppChatsStrings.chatsChannelInfoChannelTypePrivate
    },
)

@Composable
internal fun RegionDiscoveryMessage.copy(): String = stringResource(
    when (this) {
        RegionDiscoveryMessage.NO_NEW_REGIONS -> AppChatsStrings.chatsChannelInfoRegionNoNewRegions
        RegionDiscoveryMessage.NO_REPEATERS_RESPONDED -> AppChatsStrings.chatsChannelInfoRegionNoRepeatersResponded
        RegionDiscoveryMessage.ERROR_LOADING_REPEATERS -> AppChatsStrings.chatsChannelInfoRegionErrLoadingRepeaters
        RegionDiscoveryMessage.RADIO_CONTACTS_FULL -> AppChatsStrings.chatsChannelInfoRegionErrRadioContactsFull
    },
)
