// PortedFrom: MC1Services/Tests/MC1ServicesTests/Services/MessageServiceACKTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.services.messaging

import com.meshcoreone.android.core.contracts.domain.*
import com.meshcoreone.android.core.contracts.domain.errors.MessageServiceException
import com.meshcoreone.android.core.model.*
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.event.MessageSentInfo
import kotlin.test.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SourceAckTest {
    @TestFactory fun acknowledgements() = listOf(
        original("MessageServiceACKTests", "isAckExpiryCheckingActive toggles correctly") {
            val h = Harness(this); h.start(); assertFalse(h.service.isAckExpiryCheckingActive)
            h.service.startAckExpiryChecking(); assertTrue(h.service.isAckExpiryCheckingActive)
            h.service.stopAckExpiryChecking(); assertFalse(h.service.isAckExpiryCheckingActive); h.close()
        },
        original("MessageServiceACKTests", "stopAckExpiryChecking cancels the background task") {
            val h = Harness(this); h.start(); h.service.startAckExpiryChecking(); h.service.stopAckExpiryChecking()
            assertFalse(h.service.isAckExpiryCheckingActive)
            h.service.startAckExpiryChecking(); assertTrue(h.service.isAckExpiryCheckingActive); h.close()
        },
        original("MessageServiceACKTests", "checkExpiredAcks marks expired ACK as failed") {
            val h = Harness(this); h.start(); val m = h.message(); val events = h.service.statusEvents()
            h.service.installPendingAck(h.tracking(m, age = 60)); h.service.checkExpiredAcks()
            assertEquals(MessageStatus.FAILED, h.stored(m.id).status)
            assertEquals(listOf(MessageStatusEvent.Failed(m.id)), statuses(h.service, events)); h.close()
        },
        original("MessageServiceACKTests", "ACK timeout keeps message .sent through the grace window so a late ACK can still reconcile") {
            val h = Harness(this, MessageServiceConfig(ackGiveUpWindow = 45.0)); h.start(); val m = h.message()
            val events = h.service.statusEvents(); h.service.installPendingAck(h.tracking(m, age = 31))
            h.service.checkExpiredAcks(); assertEquals(MessageStatus.SENT, h.stored(m.id).status)
            assertEquals(1, h.service.pendingAckCount); assertTrue(statuses(h.service, events).isEmpty()); h.close()
        },
        original("MessageServiceACKTests", "checkExpiredAcks preserves non-expired ACK") {
            val h = Harness(this); h.start(); val m = h.message(); h.service.installPendingAck(h.tracking(m))
            h.service.checkExpiredAcks(); assertEquals(1, h.service.pendingAckCount); h.close()
        },
        original("MessageServiceACKTests", "checkExpiredAcks skips already-delivered ACK") {
            val h = Harness(this); h.start(); val m = h.message(); h.service.installPendingAck(h.tracking(m, age = 60, delivered = true))
            h.service.checkExpiredAcks(); assertEquals(1, h.service.pendingAckCount); h.close()
        },
        original("MessageServiceACKTests", "checkExpiredAcks does not broadcast failure when DB stays delivered") {
            val h = Harness(this); h.start(); val m = h.message(MessageStatus.DELIVERED); val events = h.service.statusEvents()
            h.service.installPendingAck(h.tracking(m, age = 60)); h.service.checkExpiredAcks()
            assertEquals(MessageStatus.DELIVERED, h.stored(m.id).status); assertTrue(statuses(h.service, events).isEmpty()); h.close()
        },
        original("MessageServiceACKTests", "checkExpiredAcks fails a DM after max(ackGiveUpWindow, per-attempt timeout); the window is the floor") {
            val h = Harness(this, MessageServiceConfig(ackGiveUpWindow = 20.0)); h.start()
            val live = h.message(); val expired = h.message()
            h.service.installPendingAck(h.tracking(live, age = 15, timeout = 5.0))
            h.service.installPendingAck(h.tracking(expired, Bytes.of(5, 6, 7, 8), age = 25, timeout = 5.0))
            h.service.checkExpiredAcks(); assertEquals(MessageStatus.SENT, h.stored(live.id).status)
            assertEquals(MessageStatus.FAILED, h.stored(expired.id).status); h.close()
        },
        original("MessageServiceACKTests", "checkExpiredAcks honors a per-attempt timeout longer than the give-up window (slow preset)") {
            val h = Harness(this, MessageServiceConfig(ackGiveUpWindow = 20.0)); h.start()
            val live = h.message(); val expired = h.message()
            h.service.installPendingAck(h.tracking(live, age = 30, timeout = 60.0))
            h.service.installPendingAck(h.tracking(expired, Bytes.of(5, 6, 7, 8), age = 70, timeout = 60.0))
            h.service.checkExpiredAcks(); assertEquals(MessageStatus.SENT, h.stored(live.id).status)
            assertEquals(MessageStatus.FAILED, h.stored(expired.id).status); h.close()
        },
        original("MessageServiceACKTests", "stopAckExpiryChecking leaves in-flight DMs .sent instead of failing them") {
            val h = Harness(this); h.start(); val m = h.message(); val events = h.service.statusEvents()
            h.service.installPendingAck(h.tracking(m)); h.service.startAckExpiryChecking(); h.service.stopAckExpiryChecking()
            assertEquals(MessageStatus.SENT, h.stored(m.id).status); assertEquals(1, h.service.pendingAckCount)
            assertTrue(statuses(h.service, events).isEmpty()); h.close()
        },
        original("MessageServiceACKTests", "failAllPendingMessages fails all non-delivered and broadcasts .failed") {
            val h = Harness(this); h.start(); val a = h.message(); val b = h.message(); val events = h.service.statusEvents()
            h.service.installPendingAck(h.tracking(a)); h.service.installPendingAck(h.tracking(b, Bytes.of(5, 6, 7, 8)))
            h.service.failAllPendingMessages()
            assertEquals(MessageStatus.FAILED, h.stored(a.id).status); assertEquals(MessageStatus.FAILED, h.stored(b.id).status)
            assertEquals(setOf(a.id, b.id), statuses(h.service, events).filterIsInstance<MessageStatusEvent.Failed>().map { it.messageID }.toSet()); h.close()
        },
        original("MessageServiceACKTests", "failAllPendingMessages does not downgrade or notify on a delivered DB row") {
            val h = Harness(this); h.start(); val m = h.message(MessageStatus.DELIVERED); val events = h.service.statusEvents()
            h.service.installPendingAck(h.tracking(m)); h.service.failAllPendingMessages()
            assertEquals(MessageStatus.DELIVERED, h.stored(m.id).status); assertTrue(statuses(h.service, events).isEmpty()); h.close()
        },
        original("MessageServiceACKTests", "stopAndFailAllPending stops checking and fails all pending") {
            val h = Harness(this); h.start(); val m = h.message(); h.service.installPendingAck(h.tracking(m))
            h.service.startAckExpiryChecking(); h.service.stopAndFailAllPending()
            assertFalse(h.service.isAckExpiryCheckingActive); assertEquals(MessageStatus.FAILED, h.stored(m.id).status); h.close()
        },
        original("MessageServiceACKTests", "handleAcknowledgement uses firmware tripTime when provided") {
            val h = Harness(this); h.start(); val m = h.message(); val tracking = h.tracking(m, age = 10)
            h.service.installPendingAck(tracking); h.service.handleAcknowledgement(tracking.ackCodes.first(), 250u)
            assertEquals(MessageStatus.DELIVERED, h.stored(m.id).status); assertEquals(250u, h.stored(m.id).roundTripTime); h.close()
        },
        original("MessageServiceACKTests", "handleAcknowledgement leaves roundTripTime nil when firmware does not supply tripTime") {
            val h = Harness(this); h.start(); val m = h.message(); val tracking = h.tracking(m, age = 2)
            h.service.installPendingAck(tracking); h.service.handleAcknowledgement(tracking.ackCodes.first(), null)
            assertEquals(MessageStatus.DELIVERED, h.stored(m.id).status); assertNull(h.stored(m.id).roundTripTime); h.close()
        },
        original("MessageServiceACKTests", "handleAcknowledgement matches any CRC accumulated across retry attempts") {
            val h = Harness(this); h.start(); val m = h.message(MessageStatus.PENDING)
            val first = Bytes.of(0xAA, 0xAA, 0xAA, 0xAA)
            h.service.installPendingAck(h.tracking(m).copy(ackCodes = SnapshotSet(listOf(first, Bytes.of(0xBB, 0xBB, 0xBB, 0xBB), Bytes.of(0xCC, 0xCC, 0xCC, 0xCC)))))
            h.service.handleAcknowledgement(first, 500u); assertEquals(MessageStatus.DELIVERED, h.stored(m.id).status)
            assertEquals(500u, h.stored(m.id).roundTripTime); assertEquals(0, h.service.pendingAckCount); h.close()
        },
        original("MessageServiceACKTests", "handleAcknowledgement updates contact lastMessageDate on late ACK") {
            val h = Harness(this); h.start(); val m = h.message(); val tracking = h.tracking(m)
            val before = h.clock.wallClock.instant(); h.service.installPendingAck(tracking)
            h.service.handleAcknowledgement(tracking.ackCodes.first(), 500u)
            assertTrue(assertNotNull(h.store.contacts[EntityKey(RADIO, CONTACT)]?.lastMessageDate) >= before); h.close()
        },
        original("MessageServiceACKTests", "trackPendingAck on retry resets sentAt so checkExpiredAcks preserves a retrying message") {
            val h = Harness(this); h.start(); val m = h.message(); h.service.installPendingAck(h.tracking(m, age = 60))
            h.service.trackPendingAck(m.id, CONTACT, Bytes.of(1, 2, 3, 4), 30.0)
            h.service.checkExpiredAcks(); assertEquals(1, h.service.pendingAckCount); assertNotEquals(MessageStatus.FAILED, h.stored(m.id).status); h.close()
        },
        original("MessageServiceACKTests", "finalizeSend preserves roundTripTime after listener-won delivery") {
            val h = Harness(this); h.start(); val m = h.message(); val t = h.tracking(m)
            h.service.installPendingAck(t); h.service.handleAcknowledgement(t.ackCodes.first(), 500u)
            h.service.finalizeSend(m.id, CONTACT, TARGET, MessageSentInfo(0u, t.ackCodes.first(), 5000u), 0u)
            assertEquals(MessageStatus.DELIVERED, h.stored(m.id).status); assertEquals(500u, h.stored(m.id).roundTripTime); h.close()
        },
        original("MessageServiceACKTests", "finalizeSend broadcasts .statusResolved when in-loop waitForEvent wins the ACK race") {
            val h = Harness(this); h.start(); val m = h.message(); val t = h.tracking(m); val events = h.service.statusEvents()
            h.service.installPendingAck(t); h.service.finalizeSend(m.id, CONTACT, TARGET, MessageSentInfo(0u, t.ackCodes.first(), 5000u), 0u)
            assertEquals(listOf(MessageStatusEvent.StatusResolved(m.id, MessageStatus.DELIVERED, null)), statuses(h.service, events)); h.close()
        },
        original("MessageServiceACKTests", "handleAcknowledgement is a no-op when no entry matches the ackCode") {
            val h = Harness(this); h.start(); val m = h.message()
            h.service.handleAcknowledgement(Bytes.of(0xDE, 0xAD, 0xBE, 0xEF), 100u)
            assertEquals(MessageStatus.SENT, h.stored(m.id).status); assertEquals(0, h.service.pendingAckCount); h.close()
        },
        original("MessageServiceACKTests", "failMessageAndRethrow removes pendingAcks entry and rethrows") {
            val h = Harness(this); h.start(); val m = h.message(MessageStatus.PENDING); h.service.installPendingAck(h.tracking(m))
            assertFailsWith<MessageServiceException> { h.service.failMessageAndRethrow(MeshCoreException.NotConnected(), m.id) }
            assertEquals(0, h.service.pendingAckCount); assertEquals(MessageStatus.FAILED, h.stored(m.id).status); h.close()
        },
        original("MessageServiceACKTests", "pendingAckCount reflects count correctly") {
            val h = Harness(this); h.start(); assertEquals(0, h.service.pendingAckCount)
            val a = h.message(); val b = h.message(); h.service.installPendingAck(h.tracking(a)); assertEquals(1, h.service.pendingAckCount)
            h.service.installPendingAck(h.tracking(b, Bytes.of(5, 6, 7, 8))); assertEquals(2, h.service.pendingAckCount); h.close()
        },
        original("MessageServiceACKTests", "ACK within grace window reconciles .sent \u2192 .delivered via the in-memory pending entry") {
            val h = Harness(this, MessageServiceConfig(ackGiveUpWindow = 45.0)); h.start(); val m = h.message(); val t = h.tracking(m, age = 31)
            h.service.installPendingAck(t); h.service.checkExpiredAcks(); assertEquals(MessageStatus.SENT, h.stored(m.id).status)
            h.service.handleAcknowledgement(t.ackCodes.first(), 99u); assertEquals(MessageStatus.DELIVERED, h.stored(m.id).status); h.close()
        },
        original("MessageServiceACKTests", "finalizeSend delivered branch writes .delivered, updates contact, yields .statusResolved, removes entry") {
            val h = Harness(this); h.start(); val m = h.message(); val t = h.tracking(m); val events = h.service.statusEvents()
            h.service.installPendingAck(t); h.service.finalizeSend(m.id, CONTACT, TARGET, MessageSentInfo(0u, t.ackCodes.first(), 5000u), 0u)
            assertEquals(MessageStatus.DELIVERED, h.stored(m.id).status); assertNotNull(h.store.contacts[EntityKey(RADIO, CONTACT)]?.lastMessageDate)
            assertEquals(0, h.service.pendingAckCount); assertEquals(1, statuses(h.service, events).size); h.close()
        },
        original("MessageServiceACKTests", "finalizeSend nil branch skips the DB write when the listener already delivered") {
            val h = Harness(this); h.start(); val m = h.message(MessageStatus.DELIVERED); val events = h.service.statusEvents()
            h.service.installPendingAck(h.tracking(m, delivered = true)); h.service.finalizeSend(m.id, CONTACT, TARGET, null, 0u)
            assertEquals(MessageStatus.DELIVERED, h.stored(m.id).status); assertTrue(statuses(h.service, events).isEmpty()); h.close()
        },
        original("MessageServiceACKTests", "finalizeSend nil branch does not re-stamp the pending entry's sentAt") {
            val h = Harness(this); h.start(); val m = h.message(); val t = h.tracking(m, age = 12)
            h.service.installPendingAck(t); h.service.finalizeSend(m.id, CONTACT, TARGET, null, 0u)
            assertEquals(t.sentAt, h.service.pendingAck(m.id)?.sentAt); h.close()
        },
        original("MessageServiceACKTests", "finalizeSend nil branch over an already-.failed row leaves it .failed") {
            val h = Harness(this); h.start(); val m = h.message(MessageStatus.FAILED); val events = h.service.statusEvents()
            h.service.installPendingAck(h.tracking(m)); h.service.finalizeSend(m.id, CONTACT, TARGET, null, 0u)
            assertEquals(MessageStatus.FAILED, h.stored(m.id).status); assertTrue(statuses(h.service, events).isEmpty()); h.close()
        },
        original("MessageServiceACKTests", "finalizeSend nil branch yields .statusResolved(.sent) from a non-terminal row and gates off on a terminal one") {
            val h = Harness(this); h.start(); val a = h.message(MessageStatus.RETRYING); val b = h.message(MessageStatus.FAILED)
            val events = h.service.statusEvents()
            h.service.installPendingAck(h.tracking(a)); h.service.finalizeSend(a.id, CONTACT, TARGET, null, 0u)
            h.service.installPendingAck(h.tracking(b, Bytes.of(5, 6, 7, 8))); h.service.finalizeSend(b.id, CONTACT, TARGET, null, 0u)
            assertEquals(MessageStatus.SENT, h.stored(a.id).status); assertEquals(MessageStatus.FAILED, h.stored(b.id).status)
            assertEquals(listOf(MessageStatusEvent.StatusResolved(a.id, MessageStatus.SENT, null)), statuses(h.service, events)); h.close()
        },
        original("MessageServiceACKTests", "late ACK landing in the checker's await-gap cannot flip a just-failed row to .delivered") {
            val h = Harness(this); h.start(); val m = h.message(); val t = h.tracking(m); h.service.installPendingAck(t)
            h.store.updateMessageStatusUnlessDelivered(EntityKey(RADIO, m.id), MessageStatus.FAILED)
            h.service.handleAcknowledgement(t.ackCodes.first(), 200u); assertEquals(MessageStatus.FAILED, h.stored(m.id).status); h.close()
        },
        original("MessageServiceACKTests", "late ACK after the row is already .failed with no live entry stays .failed") {
            val h = Harness(this); h.start(); val m = h.message(MessageStatus.FAILED)
            h.service.handleAcknowledgement(Bytes.of(1, 0xEF, 0xCD, 0xAB), 300u)
            assertEquals(MessageStatus.FAILED, h.stored(m.id).status); assertEquals(0, h.service.pendingAckCount); h.close()
        },
    )
}
