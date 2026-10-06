// AndroidOnly: WP-208 Review regressions: a terminal ACK reconcile failure must not stall expiry or end the ACK monitor.
package com.meshcoreone.android.core.services.messaging

import com.meshcoreone.android.core.contracts.domain.*
import com.meshcoreone.android.core.model.*
import com.meshcoreone.android.core.protocol.bytes.Bytes
import kotlin.test.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AckResilienceTest {
    private val other = Bytes(ByteArray(32) { 0x33 })

    @TestFactory fun ackResilience() = listOf(
        native("an ACK for a deleted DM retires its entry and later DMs still expire") {
            val h = Harness(this); h.start()
            val deleted = h.message(); val later = h.message()
            val codeA = Bytes.of(1, 2, 3, 4); val codeB = Bytes.of(5, 6, 7, 8)
            h.service.installPendingAck(h.tracking(deleted, codeA))
            h.service.installPendingAck(h.tracking(later, codeB, age = 60))
            h.store.deleteMessage(EntityKey(RADIO, deleted.id))
            assertFails { h.service.handleAcknowledgement(codeA, null) }
            assertNull(h.service.pendingAck(deleted.id), "A deleted message can never be delivered; its entry must be retired")
            h.service.checkExpiredAcks()
            assertEquals(MessageStatus.FAILED, h.stored(later.id).status)
            assertEquals(0, h.service.pendingAckCount); h.close()
        },
        native("the expiry sweep isolates an entry whose contact identity changed and still fails later DMs") {
            val h = Harness(this); h.start()
            val stuck = h.message(); val later = h.message()
            val codeA = Bytes.of(1, 2, 3, 4)
            h.service.installPendingAck(h.tracking(stuck, codeA).copy(
                publicKey = other, isDelivered = true, acknowledgement = MeshAcknowledgement(codeA, null),
            ))
            h.service.installPendingAck(h.tracking(later, Bytes.of(5, 6, 7, 8), age = 60))
            h.service.checkExpiredAcks()
            assertEquals(MessageStatus.FAILED, h.stored(later.id).status)
            assertNull(h.service.pendingAck(stuck.id))
            assertEquals(0, h.service.pendingAckCount); h.close()
        },
        native("the ACK monitor survives a non-persistence reconcile failure and delivers the next ACK") {
            val h = Harness(this); h.start(); h.service.startEventMonitoring()
            val first = h.message(); val second = h.message()
            val codeA = Bytes.of(1, 2, 3, 4); val codeB = Bytes.of(5, 6, 7, 8)
            h.service.installPendingAck(h.tracking(first, codeA).copy(publicKey = other))
            h.transport.pushAck(codeA); runCurrent()
            assertEquals(MessageStatus.SENT, h.stored(first.id).status)
            h.service.installPendingAck(h.tracking(second, codeB))
            h.transport.pushAck(codeB, 250u); runCurrent()
            assertEquals(MessageStatus.DELIVERED, h.stored(second.id).status, "A later ACK must still be delivered")
            h.close()
        },
    )
}
