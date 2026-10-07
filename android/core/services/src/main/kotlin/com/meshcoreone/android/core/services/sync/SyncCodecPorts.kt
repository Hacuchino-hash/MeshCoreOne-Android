// AndroidOnly: WP-214 Ports for the Swift static text helpers sync calls (owned by WPs 208/210/213/216).
package com.meshcoreone.android.core.services.sync

import com.meshcoreone.android.core.model.RxLogEntryDTO
import java.util.UUID

/** WP-216 `reactions.ParsedMCOReaction`. */
data class SyncParsedMCOReaction(val emoji: String, val dartHash: String)

/** WP-216 `reactions.ParsedMCOReactionV1`. */
data class SyncParsedMCOReactionV1(
    val emoji: String,
    val timestampSeconds: UInt,
    val senderNameHash: UInt,
    val textHash: UInt,
) {
    /** Reconstructs the original v1 messageId, used as the opaque reaction hash for dedup. */
    val messageIdHash: String get() = "${timestampSeconds}_${senderNameHash}_$textHash"
}

/** `ReactionParser` statics (WP-216). Future implementer: `reactions.ReactionParser`. */
interface SyncReactionParsing {
    fun parseDM(text: String): SyncParsedDMReaction?
    fun isReactionText(text: String, isDM: Boolean): Boolean
}

/** `MeshCoreOpenReactionParser` statics (WP-216). Future implementer: `reactions.MeshCoreOpenReactionParser`. */
interface SyncMeshCoreOpenReactionParsing {
    fun parse(text: String): SyncParsedMCOReaction?
    fun parseV1(text: String): SyncParsedMCOReactionV1?
    fun computeReactionHash(timestamp: UInt, senderName: String?, text: String): String
    fun dartStringHash(string: String): UInt
}

/** `DeduplicationKey.contentBased` (WP-208). Future implementer: WP-208's `DeduplicationKey`. */
fun interface SyncDeduplicationKeying {
    fun contentBased(contactID: UUID?, channelIndex: UByte?, senderNodeName: String?, timestamp: UInt, content: String): String
}

/** `ChannelRXCorrelation.matching` (WP-208). Future implementer: `messaging.ChannelRXCorrelation`. */
fun interface SyncChannelRXCorrelating {
    fun matching(entries: List<RxLogEntryDTO>, deduplicationKey: String?): List<RxLogEntryDTO>
}

/** `MentionUtilities.containsSelfMention` (WP-213). Future implementer: `rendering.MentionUtilities`. */
fun interface SyncMentionDetecting {
    fun containsSelfMention(text: String, selfName: String): Boolean
}

/**
 * `CLIResponse.splitEchoedPrefix` (WP-210). Future implementer: `remote.CLIResponse.splitEchoedPrefix`,
 * returning its `body` (null when the text carries no echoed wire prefix).
 */
fun interface SyncCLIEchoParsing {
    fun echoedBody(text: String): String?
}

/** The Swift static helpers sync uses, bundled so one connection's [SyncDependencies] carries them. */
data class SyncMessageCodecs(
    val reactionParser: SyncReactionParsing,
    val meshCoreOpenParser: SyncMeshCoreOpenReactionParsing,
    val deduplicationKey: SyncDeduplicationKeying,
    val channelRXCorrelation: SyncChannelRXCorrelating,
    val mentions: SyncMentionDetecting,
    val cliEcho: SyncCLIEchoParsing,
)
