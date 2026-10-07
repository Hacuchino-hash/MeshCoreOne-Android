// AndroidOnly: WP-210 Native RoomServerService posting/retry cases (Swift has no unit tests for it): permissions, ACK/no-ACK/failure outcomes, event order, retry guards, cancellation and error text.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.MessageStatus
import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.model.RoomMessageDTO
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.event.MessageSentInfo
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomServerPostingTest {
    private val publicKey = remoteCoreKey(0xC1)
    private val epochSeconds = 1_786_722_487u

    private suspend fun RemoteAdminHarness.room(
        permission: RoomPermissionLevel = RoomPermissionLevel.READ_WRITE,
    ): EntityKey = core.addSession(remoteCoreSession(core.radioId, publicKey, RemoteNodeRole.ROOM_SERVER, permission, true))

    private fun RemoteAdminHarness.messageKey(message: RoomMessageDTO) = EntityKey(core.radioId, message.id)

    /** Collects every room event into a list until [RoomServerService.finishEvents] or cancellation. */
    private fun CoroutineScope.collectEvents(service: RoomServerService): Pair<MutableList<RoomServerEvent>, Job> {
        val seen = java.util.Collections.synchronizedList(mutableListOf<RoomServerEvent>())
        val stream = service.events()
        return seen to launch(start = CoroutineStart.UNDISPATCHED) { stream.collect { seen += it } }
    }

    private suspend fun RemoteAdminHarness.failedMessage(
        session: EntityKey, retryAttempt: Long = 1, text: String = "again", status: MessageStatus = MessageStatus.FAILED,
    ) = RoomMessageDTO(
        sessionID = session.id, authorKeyPrefix = Bytes.of(0, 0, 0, 0), authorName = "Me", text = text,
        timestamp = 1u, isFromSelf = true, statusRawValue = status.rawValue, retryAttempt = retryAttempt,
        maxRetryAttempts = 5,
    ).also { store.saveRoomMessage(core.radioId, it) }

    @TestFactory
    fun postingCases(): List<DynamicTest> = listOf(
        remoteCoreNative("postMessage returns the pending self message at once, then sends with the full key and Swift retry defaults") {
            withRemoteAdminHarness {
                val key = room()
                rooms.setSelfPublicKeyPrefix(Bytes.of(0xDE, 0xAD, 0xBE, 0xEF, 0x01, 0x02))
                val gate = CompletableDeferred<MessageSentInfo?>()
                core.session.sendMessageWithRetryHandler = { gate.await() }
                val (events, collector) = collectEvents(rooms)

                val posted = rooms.postMessage(key, "hello")
                assertEquals(MessageStatus.PENDING, posted.status)
                assertEquals(key.id, posted.sessionID)
                assertEquals(Bytes.of(0xDE, 0xAD, 0xBE, 0xEF), posted.authorKeyPrefix)
                assertEquals("Me", posted.authorName)
                assertEquals(epochSeconds, posted.timestamp)
                assertTrue(posted.isFromSelf)
                assertEquals(5L, posted.maxRetryAttempts)
                assertEquals(0L, posted.retryAttempt)
                assertEquals("1786722487-DEADBEEF-2CF24DBA", posted.deduplicationKey)
                assertEquals(posted, store.message(messageKey(posted)))
                assertTrue(core.session.sendMessageWithRetryInvocations.isEmpty(), "send must run after postMessage returns")
                assertEquals(listOf("roomPosted ${publicKey.prefix(6).hexString} 5"), audit.entries)

                remoteCoreAwait("send never started") { core.session.sendMessageWithRetryInvocations.size == 1 }
                assertEquals(
                    RemoteCoreFakeSession.SendMessageWithRetryInvocation(
                        publicKey, "hello", Instant.ofEpochSecond(epochSeconds.toLong()), 5, 4, 1, null,
                    ),
                    core.session.sendMessageWithRetryInvocations.single(),
                )
                gate.complete(RemoteCoreFakeSession.sentInfo(2500u))
                remoteCoreAwait("delivered event never arrived") { events.size == 1 }
                assertEquals(listOf<RoomServerEvent>(RoomServerEvent.StatusUpdated(messageKey(posted), MessageStatus.DELIVERED)), events)
                val stored = store.message(messageKey(posted))
                assertEquals(MessageStatus.DELIVERED, stored?.status)
                assertEquals(0x04030201u, stored?.ackCode)
                assertEquals(2500u, stored?.roundTripTime)
                remoteCoreAwait("activity never updated") { "activity:nil" in store.operations }
                assertEquals(listOf("save:0", "status:DELIVERED", "activity:nil"), store.operations)
                collector.cancel()
            }
        },
        remoteCoreNative("postMessage uses a zero author prefix when SelfInfo has not arrived") {
            withRemoteAdminHarness {
                val key = room()
                core.session.sendMessageWithRetryHandler = { null }
                assertEquals(Bytes.of(0, 0, 0, 0), rooms.postMessage(key, "x").authorKeyPrefix)
            }
        },
        remoteCoreNative("postMessage permission: missing room is sessionNotFound; guests and repeaters are denied; members and admins post") {
            withRemoteAdminHarness {
                core.session.sendMessageWithRetryHandler = { null }
                assertFailsWith<RoomServerError.SessionNotFound> { rooms.postMessage(EntityKey(core.radioId, UUID.randomUUID()), "x") }
                val guest = core.addSession(remoteCoreSession(core.radioId, remoteCoreKey(0xC2), RemoteNodeRole.ROOM_SERVER, RoomPermissionLevel.GUEST))
                assertEquals("Permission denied.", assertFailsWith<RoomServerError.PermissionDenied> { rooms.postMessage(guest, "x") }.message)
                val repeater = core.addSession(remoteCoreSession(core.radioId, remoteCoreKey(0xC3), RemoteNodeRole.REPEATER, RoomPermissionLevel.ADMIN))
                assertFailsWith<RoomServerError.PermissionDenied> { rooms.postMessage(repeater, "x") }
                assertEquals(0, store.messageCount)
                assertTrue(audit.entries.isEmpty())
                rooms.postMessage(room(), "member")
                rooms.postMessage(core.addSession(remoteCoreSession(core.radioId, remoteCoreKey(0xC4), RemoteNodeRole.ROOM_SERVER, RoomPermissionLevel.ADMIN)), "admin")
                assertEquals(2, store.messageCount)
            }
        },
        remoteCoreNative("exhausted retries without an ACK mark the post sent with no ack or RTT, and still update activity") {
            withRemoteAdminHarness {
                val key = room()
                core.session.sendMessageWithRetryHandler = { null }
                val (events, collector) = collectEvents(rooms)
                val posted = rooms.postMessage(key, "no ack")
                remoteCoreAwait("sent event never arrived") { events.size == 1 }
                assertEquals(RoomServerEvent.StatusUpdated(messageKey(posted), MessageStatus.SENT), events.single())
                assertEquals(MessageStatus.SENT, store.message(messageKey(posted))?.status)
                assertNull(store.message(messageKey(posted))?.ackCode)
                assertNull(store.message(messageKey(posted))?.roundTripTime)
                remoteCoreAwait("activity never updated") { store.operations.size == 3 }
                assertEquals(listOf("save:0", "status:SENT", "activity:nil"), store.operations)
                collector.cancel()
            }
        },
        remoteCoreNative("a send error marks the post failed, broadcasts failed and skips the activity update") {
            withRemoteAdminHarness {
                val key = room()
                core.session.sendMessageWithRetryHandler = { throw MeshCoreException.Timeout() }
                val (events, collector) = collectEvents(rooms)
                val posted = rooms.postMessage(key, "lost")
                remoteCoreAwait("failed event never arrived") { events.size == 1 }
                remoteCoreSettle()
                assertEquals(RoomServerEvent.StatusUpdated(messageKey(posted), MessageStatus.FAILED), events.single())
                assertEquals(MessageStatus.FAILED, store.message(messageKey(posted))?.status)
                assertEquals(listOf("save:0", "status:FAILED"), store.operations)
                assertTrue(core.activeServiceJobs.isEmpty())
                collector.cancel()
            }
        },
        remoteCoreNative("status-update storage failures are logged and the outcome is still broadcast; activity failures are ignored") {
            withRemoteAdminHarness {
                val key = room()
                store.updateStatusError = IllegalStateException("disk full")
                store.updateActivityError = IllegalStateException("disk full")
                core.session.sendMessageWithRetryHandler = { RemoteCoreFakeSession.sentInfo(100u) }
                val (events, collector) = collectEvents(rooms)
                val posted = rooms.postMessage(key, "x")
                remoteCoreAwait("event never arrived") { events.size == 1 }
                remoteCoreSettle()
                assertEquals(RoomServerEvent.StatusUpdated(messageKey(posted), MessageStatus.DELIVERED), events.single())
                assertEquals(MessageStatus.PENDING, store.message(messageKey(posted))?.status)
                assertEquals(listOf("save:0", "status:DELIVERED", "activity:nil"), store.operations)
                assertTrue(core.activeServiceJobs.isEmpty())
                collector.cancel()
            }
        },
        remoteCoreNative("the status is persisted before the event is broadcast") {
            withRemoteAdminHarness {
                val key = room()
                store.updateStatusGate = CompletableDeferred()
                core.session.sendMessageWithRetryHandler = { RemoteCoreFakeSession.sentInfo(100u) }
                val (events, collector) = collectEvents(rooms)
                val posted = rooms.postMessage(key, "x")
                remoteCoreAwait("send never ran") { core.session.sendMessageWithRetryInvocations.size == 1 }
                remoteCoreSettle()
                assertTrue(events.isEmpty())
                assertEquals(MessageStatus.PENDING, store.message(messageKey(posted))?.status)
                store.updateStatusGate?.complete(Unit)
                remoteCoreAwait("event never arrived") { events.size == 1 }
                assertEquals(MessageStatus.DELIVERED, store.message(messageKey(posted))?.status)
                collector.cancel()
            }
        },
        remoteCoreNative("the posted audit length counts Swift Characters (grapheme clusters), not UTF-16 units") {
            withRemoteAdminHarness {
                val key = room()
                core.session.sendMessageWithRetryHandler = { null }
                val text = "é👍🏽🇺🇸!"
                assertEquals(11, text.length)
                rooms.postMessage(key, text)
                assertEquals(listOf("roomPosted ${publicKey.prefix(6).hexString} 4"), audit.entries)
            }
        },
        remoteCoreNative("a custom retry config reaches the send and the stored maxRetryAttempts; maxAttempts above 5 is rejected") {
            withRemoteAdminHarness {
                val key = room()
                val custom = RoomServerService(
                    core.session, core.service, store, core.radioId, core.scope, RoomMessageRetryConfig(3, 2, 1),
                )
                core.session.sendMessageWithRetryHandler = { null }
                val posted = custom.postMessage(key, "x")
                assertEquals(3L, posted.maxRetryAttempts)
                remoteCoreAwait("send never ran") { core.session.sendMessageWithRetryInvocations.size == 1 }
                val sent = core.session.sendMessageWithRetryInvocations.single()
                assertEquals(Triple(3L, 2L, 1L), Triple(sent.maxAttempts, sent.floodAfter, sent.maxFloodAttempts))
                assertEquals(RoomMessageRetryConfig(5, 4, 1), RoomMessageRetryConfig())
                assertFailsWith<IllegalArgumentException> { RoomMessageRetryConfig(maxAttempts = 6) }
            }
        },
        remoteCoreNative("cancelling the owning scope mid-send records failed, broadcasts it and propagates cancellation") {
            withRemoteAdminHarness {
                val key = room()
                val dispatcher = checkNotNull(coroutineContext[ContinuationInterceptor])
                val ownScope = CoroutineScope(SupervisorJob() + dispatcher)
                val service = RoomServerService(core.session, core.service, store, core.radioId, ownScope)
                core.session.sendMessageWithRetryHandler = { CompletableDeferred<MessageSentInfo?>().await() }
                val (events, collector) = collectEvents(service)
                val posted = service.postMessage(key, "x")
                remoteCoreAwait("send never ran") { core.session.sendMessageWithRetryInvocations.size == 1 }
                ownScope.cancel()
                remoteCoreAwait("failed event never arrived") { events.size == 1 }
                assertEquals(RoomServerEvent.StatusUpdated(EntityKey(core.radioId, posted.id), MessageStatus.FAILED), events.single())
                assertEquals(MessageStatus.FAILED, store.message(EntityKey(core.radioId, posted.id))?.status)
                assertEquals(listOf("save:0", "status:FAILED"), store.operations)
                collector.cancel()
            }
        },
        remoteCoreNative("cancelling the owning scope before the send starts still records failed, never leaving it pending") {
            withRemoteAdminHarness {
                val key = room()
                val dispatcher = checkNotNull(coroutineContext[ContinuationInterceptor])
                val ownScope = CoroutineScope(SupervisorJob() + dispatcher)
                val service = RoomServerService(core.session, core.service, store, core.radioId, ownScope)
                core.session.sendMessageWithRetryHandler = { CompletableDeferred<MessageSentInfo?>().await() }
                val posted = service.postMessage(key, "x")
                // Single-threaded dispatcher: the send coroutine has not run yet.
                ownScope.cancel()
                remoteCoreAwait("pending row never resolved") {
                    store.message(EntityKey(core.radioId, posted.id))?.status == MessageStatus.FAILED
                }
            }
        },
        remoteCoreNative("events reach every subscriber; finishEvents ends them and later subscriptions are empty") {
            withRemoteAdminHarness {
                val key = room()
                core.session.sendMessageWithRetryHandler = { null }
                val first = rooms.events().let { stream -> async { stream.toList() } }
                val second = rooms.events().let { stream -> async { stream.take(1).toList() } }
                val posted = rooms.postMessage(key, "x")
                remoteCoreAwait("second subscriber never got the event") { second.isCompleted }
                rooms.finishEvents()
                val expected = listOf<RoomServerEvent>(RoomServerEvent.StatusUpdated(messageKey(posted), MessageStatus.SENT))
                assertEquals(expected, first.await())
                assertEquals(expected, second.await())
                assertEquals(emptyList(), rooms.events().toList())
                rooms.postMessage(key, "after finish")
                remoteCoreAwait("post after finish never sent") { core.session.sendMessageWithRetryInvocations.size == 2 }
            }
        },
    )

    @TestFactory
    fun retryCases(): List<DynamicTest> = listOf(
        remoteCoreNative("retryMessage marks pending with the next attempt, resends now and returns the delivered row") {
            withRemoteAdminHarness {
                val key = room()
                val failed = failedMessage(key, retryAttempt = 2)
                core.clock.advance(10.seconds)
                core.session.sendMessageWithRetryHandler = { RemoteCoreFakeSession.sentInfo(900u) }
                val (events, collector) = collectEvents(rooms)
                val updated = rooms.retryMessage(messageKey(failed))
                assertEquals(MessageStatus.DELIVERED, updated.status)
                assertEquals(3L, updated.retryAttempt)
                assertEquals(5L, updated.maxRetryAttempts)
                assertEquals(0x04030201u, updated.ackCode)
                assertEquals(900u, updated.roundTripTime)
                assertEquals(
                    RemoteCoreFakeSession.SendMessageWithRetryInvocation(
                        publicKey, "again", Instant.ofEpochSecond(epochSeconds.toLong() + 10), 5, 4, 1, null,
                    ),
                    core.session.sendMessageWithRetryInvocations.single(),
                )
                assertEquals(listOf("save:4", "retryStatus:PENDING:3:5", "status:DELIVERED", "activity:nil"), store.operations)
                remoteCoreAwait("events never arrived") { events.size == 2 }
                assertEquals(
                    listOf<RoomServerEvent>(
                        RoomServerEvent.StatusUpdated(messageKey(failed), MessageStatus.PENDING),
                        RoomServerEvent.StatusUpdated(messageKey(failed), MessageStatus.DELIVERED),
                    ),
                    events,
                )
                collector.cancel()
            }
        },
        remoteCoreNative("retryMessage outcomes: no ACK returns sent; a send error returns failed without an activity update") {
            withRemoteAdminHarness {
                val key = room()
                val failed = failedMessage(key)
                core.session.sendMessageWithRetryHandler = { null }
                assertEquals(MessageStatus.SENT, rooms.retryMessage(messageKey(failed)).status)
                val second = failedMessage(key, text = "second")
                core.session.sendMessageWithRetryHandler = { throw MeshCoreException.DeviceError(3u) }
                val result = rooms.retryMessage(messageKey(second))
                assertEquals(MessageStatus.FAILED, result.status)
                assertEquals(2L, result.retryAttempt)
                assertEquals(
                    listOf("save:4", "retryStatus:PENDING:2:5", "status:SENT", "activity:nil", "save:4", "retryStatus:PENDING:2:5", "status:FAILED"),
                    store.operations,
                )
            }
        },
        remoteCoreNative("retryMessage guards: unknown message, non-failed message, missing session, vanished row") {
            withRemoteAdminHarness {
                val key = room()
                val unknown = assertFailsWith<RoomServerError.SendFailed> { rooms.retryMessage(EntityKey(core.radioId, UUID.randomUUID())) }
                assertEquals("Send failed: Message not found", unknown.message)
                listOf(MessageStatus.DELIVERED, MessageStatus.PENDING, MessageStatus.SENT).forEach { status ->
                    val notFailed = failedMessage(key, text = "$status", status = status)
                    val error = assertFailsWith<RoomServerError.SendFailed> { rooms.retryMessage(messageKey(notFailed)) }
                    assertEquals("Message is not in failed state", error.reason)
                    assertEquals("Send failed: Message is not in failed state", error.message)
                }
                val orphan = failedMessage(EntityKey(core.radioId, UUID.randomUUID()), text = "orphan")
                assertEquals("Room session not found.", assertFailsWith<RoomServerError.SessionNotFound> { rooms.retryMessage(messageKey(orphan)) }.message)
                assertTrue(core.session.sendMessageWithRetryInvocations.isEmpty())

                val vanishing = failedMessage(key, text = "vanishing")
                core.session.sendMessageWithRetryHandler = { store.removeMessage(messageKey(vanishing)); null }
                val gone = assertFailsWith<RoomServerError.SendFailed> { rooms.retryMessage(messageKey(vanishing)) }
                assertEquals("Send failed: Failed to fetch message after retry", gone.message)
                // Every guard released the in-flight slot: the same key fails the same way, not "already in progress".
                assertEquals("Message not found", assertFailsWith<RoomServerError.SendFailed> { rooms.retryMessage(messageKey(vanishing)) }.reason)
            }
        },
        remoteCoreNative("a concurrent retry of the same message is rejected while the first is in flight") {
            withRemoteAdminHarness {
                val key = room()
                val failed = failedMessage(key)
                val other = failedMessage(key, text = "other")
                val gate = CompletableDeferred<MessageSentInfo?>()
                core.session.sendMessageWithRetryHandler = { gate.await() }
                val first = async { rooms.retryMessage(messageKey(failed)) }
                remoteCoreAwait("first retry never sent") { core.session.sendMessageWithRetryInvocations.size == 1 }
                val duplicate = assertFailsWith<RoomServerError.SendFailed> { rooms.retryMessage(messageKey(failed)) }
                assertEquals("Send failed: Retry already in progress", duplicate.message)
                assertEquals("RoomServerError.sendFailed(\"Retry already in progress\")", duplicate.toString())
                val parallel = async { rooms.retryMessage(messageKey(other)) }
                remoteCoreAwait("other retry never sent") { core.session.sendMessageWithRetryInvocations.size == 2 }
                gate.complete(null)
                assertEquals(MessageStatus.SENT, first.await().status)
                assertEquals(MessageStatus.SENT, parallel.await().status)
            }
        },
        remoteCoreNative("cancelling a retry mid-send records failed, broadcasts it, releases the slot and rethrows") {
            withRemoteAdminHarness {
                val key = room()
                val failed = failedMessage(key)
                core.session.sendMessageWithRetryHandler = { CompletableDeferred<MessageSentInfo?>().await() }
                val (events, collector) = collectEvents(rooms)
                val retry = async { rooms.retryMessage(messageKey(failed)) }
                remoteCoreAwait("retry never sent") { core.session.sendMessageWithRetryInvocations.size == 1 }
                retry.cancel()
                assertFailsWith<CancellationException> { retry.await() }
                assertEquals(MessageStatus.FAILED, store.message(messageKey(failed))?.status)
                remoteCoreAwait("events never arrived") { events.size == 2 }
                assertEquals(MessageStatus.FAILED, (events.last() as RoomServerEvent.StatusUpdated).status)
                core.session.sendMessageWithRetryHandler = { null }
                assertEquals(MessageStatus.SENT, rooms.retryMessage(messageKey(failed)).status)
                collector.cancel()
            }
        },
    )

    @TestFactory
    fun errorCases(): List<DynamicTest> = listOf(
        remoteCoreNative("RoomServerError descriptions and debug names match the Swift cases") {
            val mesh = MeshCoreException.DeviceError(3u)
            val cases = listOf(
                RoomServerError.NotConnected() to "Not connected to device.",
                RoomServerError.SessionNotFound() to "Room session not found.",
                RoomServerError.SendFailed("radio busy") to "Send failed: radio busy",
                RoomServerError.PermissionDenied() to "Permission denied.",
                RoomServerError.InvalidResponse() to "Invalid response from device.",
                RoomServerError.SessionError(mesh) to "Device returned error code 3",
            )
            cases.forEach { (error, text) ->
                assertEquals(text, error.message)
                assertEquals(text, error.errorDescription)
            }
            assertIs<MeshCoreException.DeviceError>(RoomServerError.SessionError(mesh).cause)
            assertEquals("RoomServerError.notConnected", RoomServerError.NotConnected().toString())
            assertEquals("RoomServerError.sessionError(DeviceError)", RoomServerError.SessionError(mesh).toString())
        },
    )
}
