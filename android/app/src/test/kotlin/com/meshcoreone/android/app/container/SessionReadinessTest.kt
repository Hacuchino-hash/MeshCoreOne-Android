// AndroidOnly: WP-303 Native proof: readiness is truthful, cold-start pending sends drain only at READY, and resync exhaustion never claims READY.
package com.meshcoreone.android.app.container

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DeviceDTO
import com.meshcoreone.android.core.model.MessageDTO
import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.PendingSendDTO
import com.meshcoreone.android.core.model.PendingSendKind
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.ui.StatusPillState
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SessionReadinessTest : RoomProcessTest() {
    private val radio = RadioId(UUID.fromString("447175B7-557D-4F48-B671-4B2CEE2DC932"))
    private val peerKey = Bytes(ByteArray(32) { 3 })

    private fun isSend(frame: Bytes) = (frame[0].toInt() and 255) == 0x02

    /** A radio persisted from an earlier run with one DM queued but never sent. */
    private suspend fun seedQueuedDirectMessage(): MessageDTO {
        store.saveDevice(DeviceDTO(radioId = radio, publicKey = key(7), nodeName = "Restored", isActive = false))
        val contact = ContactDTO(radioId = radio, publicKey = peerKey, name = "Peer", lastHeardTimestamp = null)
        store.saveContact(contact)
        val message = MessageDTO(
            radioId = radio, contactID = contact.id, text = "queued before the process died",
            timestamp = 1_704_067_200u, createdAt = EPOCH, status = MessageStatus.PENDING,
        )
        store.saveMessage(message)
        store.insertPendingSendAssigningSequence(
            PendingSendDTO(UUID.randomUUID(), radio, message.id, PendingSendKind.DM, contact.id, null, false,
                message.text, message.timestamp, null, 0, EPOCH, attemptCount = 0),
        )
        return message
    }

    @Test
    fun coldStartPendingSendWaitsForReadyThenDrainsExactlyOnce() = runTest {
        val message = seedQueuedDirectMessage()
        val h = ContainerHarness(this, store)
        h.links.configureRadio = { fake ->
            fake.knownContacts += contactFrame(peerKey, "Peer")
            // Hold the contact list so the sync cannot finish: the connection must sit in SYNCING.
            val original = fake.respond
            fake.respond = { frame ->
                when {
                    (frame[0].toInt() and 255) == 0x04 -> emptyList()
                    isSend(frame) -> sentAndDelivered(frame, key(7))
                    else -> original(frame)
                }
            }
        }
        try {
            val connect = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { h.manager.connect(h.target()) }
            h.eventually("syncing") { h.manager.connectionState == DeviceConnectionState.SYNCING }
            h.settle()
            val radioLink = h.links.radios.single()
            assertEquals(DeviceConnectionState.SYNCING, h.manager.connectionState)
            assertTrue(radioLink.sent.none(::isSend), "nothing is transmitted before READY")
            assertEquals(1, store.fetchPendingSends(radio).size, "the queued row survives until it is sent")
            assertEquals(StatusPillState.Syncing, h.appState.statusPillState, "the pill never claims ready while syncing")

            // The radio finishes its contact list: sync completes and the runtime promotes to READY.
            radioLink.push(Bytes.of(2) + le32(1))
            radioLink.push(contactFrame(peerKey, "Peer"))
            radioLink.push(Bytes.of(4) + le32(0))
            h.eventually("ready") { h.manager.connectionState == DeviceConnectionState.READY }
            connect.join()
            try { h.eventually("the queued DM to be transmitted") { radioLink.sent.any(::isSend) } } catch (e: AssertionError) {
                throw AssertionError("${e.message} sent=${radioLink.sent.map { it.hexString.take(12) }} pending=${kotlinx.coroutines.runBlocking { store.fetchPendingSends(radio).size }} diag=${h.diagnostics} messaging=${h.messagingDiagnostics} status=${kotlinx.coroutines.runBlocking { store.fetchMessage(EntityKey(radio, message.id))?.status }}", e)
            }
            try {
                h.eventually("the pending row to clear", virtualBudgetMillis = 20_000, virtualStepMillis = 50) { kotlinx.coroutines.runBlocking { store.fetchPendingSends(radio).isEmpty() } }
            } catch (e: AssertionError) {
                throw AssertionError("${e.message} status=${kotlinx.coroutines.runBlocking { store.fetchMessage(EntityKey(radio, message.id))?.status }} messaging=${h.messagingDiagnostics} sent=${radioLink.sent.map { it.hexString }}", e)
            }
            assertEquals(1, radioLink.sent.count(::isSend), "exactly one transmission")
            val sent = radioLink.sent.single(::isSend)
            assertTrue(sent.hexString.contains(Bytes.utf8(message.text).hexString), "the persisted text goes on air")
            val stored = store.fetchMessage(EntityKey(radio, message.id))!!
            assertTrue(stored.status != MessageStatus.PENDING && stored.status != MessageStatus.FAILED, "status=${stored.status}")
        } finally { h.container.close() }
    }

    @Test
    fun aDisconnectBeforeReadyKeepsTheQueuedSendForTheNextGraph() = runTest {
        seedQueuedDirectMessage()
        val h = ContainerHarness(this, store)
        h.links.configureRadio = { fake ->
            fake.knownContacts += contactFrame(peerKey, "Peer")
            val original = fake.respond
            fake.respond = { frame -> if ((frame[0].toInt() and 255) == 0x04) emptyList() else original(frame) }
        }
        try {
            val connect = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { runCatching { h.manager.connect(h.target()) } }
            h.eventually("syncing") { h.manager.connectionState == DeviceConnectionState.SYNCING }
            h.manager.disconnect(com.meshcoreone.android.core.runtime.RuntimeDisconnectReason.USER_INITIATED)
            connect.join()
            h.settle()
            assertTrue(h.links.radios.all { radio -> radio.sent.none(::isSend) })
            assertEquals(1, store.fetchPendingSends(radio).size, "the unsent row is still durable")
            assertEquals(0, h.sessions.outstanding)
        } finally { h.container.close() }
    }

    @Test
    fun exhaustedResyncNeverClaimsReadyAndDisconnectsWithTheSyncFailedPill() = runTest {
        val h = ContainerHarness(this, store)
        h.links.configureRadio = { fake ->
            val original = fake.respond
            // The radio answers every contact request with a firmware error.
            fake.respond = { frame -> if ((frame[0].toInt() and 255) == 0x04) listOf(Bytes.of(1, 2)) else original(frame) }
        }
        val states = mutableListOf<DeviceConnectionState>()
        try {
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { h.manager.snapshot.collect { states += it.state } }
            val pills = mutableListOf<StatusPillState>()
            val connect = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { runCatching { h.manager.connect(h.target()) } }
            try {
                h.eventually("sync failed pill", virtualBudgetMillis = 30_000, virtualStepMillis = 50) {
                    h.appState.statusPillState.also { pills += it }.isFailure
                }
            } catch (e: AssertionError) {
                throw AssertionError("${e.message} states=$states diag=${h.diagnostics} radioSent=${h.links.radios.map { r -> r.sent.map { it.hexString.take(4) } }} resync=${h.sessions.current?.hasResyncLoop}", e)
            }
            connect.join()
            h.eventually("the failed resync to disconnect the radio") {
                h.manager.connectionState == DeviceConnectionState.DISCONNECTED && h.sessions.outstanding == 0
            }
            assertFalse(DeviceConnectionState.READY in states, "READY was never published: $states")
            assertTrue(pills.any { it.isFailure }, "the user was told the sync failed")
            assertNull(h.sessions.current)
        } finally { h.container.close() }
    }
}
