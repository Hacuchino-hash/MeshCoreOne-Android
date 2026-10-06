// PortedFrom: MC1Services/Sources/MC1Services/Utilities/ChannelRXCorrelation.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.messaging

import com.meshcoreone.android.core.model.DeduplicationKey
import com.meshcoreone.android.core.model.DecryptStatus
import com.meshcoreone.android.core.model.RxLogEntryDTO
import com.meshcoreone.android.core.model.snapshot
import com.meshcoreone.android.core.model.SnapshotList
import com.meshcoreone.android.core.protocol.event.PayloadType

object ChannelRXCorrelation {
    fun matching(entries: List<RxLogEntryDTO>, deduplicationKey: String?): SnapshotList<RxLogEntryDTO> {
        if (deduplicationKey == null) return SnapshotList.empty()
        return entries.filter { entry ->
            if (entry.payloadType != PayloadType.GROUP_TEXT || entry.decryptStatus != DecryptStatus.SUCCESS) return@filter false
            val parsed = entry.decodedText?.let(ChannelMessageFormat::parse) ?: return@filter false
            val channel = entry.channelIndex ?: return@filter false
            val timestamp = entry.senderTimestamp ?: return@filter false
            DeduplicationKey.contentBased(null, channel, parsed.senderName, timestamp, parsed.messageText) == deduplicationKey
        }.sortedBy { it.receivedAt }.snapshot()
    }
}
