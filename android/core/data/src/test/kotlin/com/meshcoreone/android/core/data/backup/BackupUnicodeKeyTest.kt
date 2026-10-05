// AndroidOnly: WP-203 Frozen Swift canonical String equivalence without mutating stored/wire strings or hashed bytes.
package com.meshcoreone.android.core.data.backup

import com.meshcoreone.android.core.database.*
import com.meshcoreone.android.core.model.*
import com.meshcoreone.android.core.protocol.bytes.Bytes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BackupUnicodeKeyTest : BackupRoomTest() {
    @Test fun reactionComposedSenderAliasSkipsAndKeepsOriginalStoredString() = runBlocking {
        seed(envelope(messages = listOf(message())))
        val local = reaction().copy(senderName = "\u00E9")
        db.reactions().insert(local.toEntity())
        val incoming = local.copy(id = id(60), senderName = "e\u0301")
        val result = service.importBackup(envelope(messages = listOf(message()), reactions = listOf(incoming)), store)
        assertEquals(0L, result.count(BackupModelKind.REACTIONS).inserted)
        assertEquals(1L, result.count(BackupModelKind.REACTIONS).skipped)
        assertEquals(local, db.reactions().backupAll().single().toDTO())
        assertEquals("\u00E9", db.reactions().backupAll().single().senderName)
    }

    @Test fun reactionComposedEmojiAliasSkipsAndKeepsOriginalRawEmoji() = runBlocking {
        seed(envelope(messages = listOf(message())))
        val local = reaction().copy(emoji = "\u00E9")
        db.reactions().insert(local.toEntity())
        val result = service.importBackup(envelope(messages = listOf(message()),
            reactions = listOf(local.copy(id = id(60), emoji = "e\u0301"))), store)
        assertEquals(0L, result.count(BackupModelKind.REACTIONS).inserted)
        assertEquals(1L, result.count(BackupModelKind.REACTIONS).skipped)
        assertEquals(local, db.reactions().backupAll().single().toDTO())
    }

    @Test fun distinctSendersWithEquivalentEmojiShareOneSourceSummaryBucket() = runBlocking {
        seed(envelope(messages = listOf(message())))
        val local = reaction().copy(emoji = "\u00E9", senderName = "Alice")
        db.reactions().insert(local.toEntity())
        val result = service.importBackup(envelope(messages = listOf(message()),
            reactions = listOf(local.copy(id = id(60), emoji = "e\u0301", senderName = "Bob"))), store)
        assertEquals(1L, result.count(BackupModelKind.REACTIONS).inserted)
        assertEquals(2, db.reactions().backupAll().size)
        val summary = requireNotNull(db.messages().byId(RADIO.value, id(4))).reactionSummary
        assertNotNull(summary)
        assertFalse(requireNotNull(summary).contains(","))
        assertTrue(summary.endsWith(":2"))
        assertEquals(setOf("\u00E9", "e\u0301"), db.reactions().backupAll().map { it.emoji }.toSet())
    }

    @Test fun incomingStoredDedupAliasesMatchWithoutChangingEitherTextOrRawKey() = runBlocking {
        val local = message(direction = MessageDirection.INCOMING, contactID = null, index = 0u).copy(
            senderNodeName = "\u00E9", deduplicationKey = "ch-0-1700000000-\u00E9-AABBCCDD")
        seed(envelope(messages = listOf(local)))
        val result = service.importBackup(envelope(messages = listOf(local.copy(id = id(40),
            senderNodeName = "e\u0301", deduplicationKey = "ch-0-1700000000-e\u0301-AABBCCDD"))), store)
        assertEquals(0L, result.count(BackupModelKind.MESSAGES).inserted)
        assertEquals(1L, result.count(BackupModelKind.MESSAGES).skipped)
        assertEquals(local, db.messages().byId(RADIO.value, local.id)?.toDTO())
    }

    @Test fun sourceContentHashStillDistinguishesCanonicalEquivalentTextBytes() = runBlocking {
        val first = message(direction = MessageDirection.INCOMING, text = "\u00E9")
        val second = first.copy(id = id(40), text = "e\u0301")
        assertNotEquals(Bytes.utf8(first.text), Bytes.utf8(second.text))
        val result = service.importBackup(envelope(messages = listOf(first, second)), store)
        assertEquals(2L, result.count(BackupModelKind.MESSAGES).inserted)
        assertEquals(0L, result.count(BackupModelKind.MESSAGES).skipped)
        assertEquals(first.text, db.messages().byId(RADIO.value, first.id)?.text)
        assertEquals(second.text, db.messages().byId(RADIO.value, second.id)?.text)
    }

    @Test fun blockedSenderAliasesSkipOnlyWithinTheSameRadio() = runBlocking {
        val local = BlockedChannelSenderDTO(id(11), "\u00E9", RADIO, AT)
        db.blockedSenders().insert(local.toEntity())
        val result = service.importBackup(envelope(blocked = listOf(local.copy(id = id(110), name = "e\u0301"),
            local.copy(id = id(111), radioId = OTHER_RADIO, name = "e\u0301"))), store)
        assertEquals(1L, result.count(BackupModelKind.BLOCKED_CHANNEL_SENDERS).skipped)
        assertEquals(1L, result.count(BackupModelKind.BLOCKED_CHANNEL_SENDERS).inserted)
        assertEquals(local, db.blockedSenders().forRadio(RADIO.value).single().toDTO())
        assertEquals("e\u0301", db.blockedSenders().forRadio(OTHER_RADIO.value).single().name)
    }

    @Test fun roomStoredDedupAliasesPreserveTheOriginalSourceIdentity() = runBlocking {
        seed(envelope(sessions = listOf(session())))
        val local = roomMessage().copy(deduplicationKey = "stored-\u00E9")
        db.roomMessages().insert(local.toEntity(RADIO))
        val result = service.importBackup(envelope(sessions = listOf(session()), roomMessages = listOf(
            local.copy(id = id(70), deduplicationKey = "stored-e\u0301"))), store)
        assertEquals(0L, result.count(BackupModelKind.ROOM_MESSAGES).inserted)
        assertEquals(1L, result.count(BackupModelKind.ROOM_MESSAGES).skipped)
        assertEquals(local, db.roomMessages().forSession(RADIO.value, id(8)).single().toDTO())
    }
}
