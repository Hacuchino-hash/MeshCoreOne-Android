// AndroidOnly: WP-401 Compile-only checks. The locked classpath gives androidTest no JUnit (module build file is outside
// WP-401 write paths), so these are dependency-free cases a later runner can invoke via [cases]; no execution is claimed.
package com.meshcoreone.android.platform.notifications.messaging

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.notifications.NotificationAction
import com.meshcoreone.android.core.contracts.notifications.NotificationAuthorizationStatus
import com.meshcoreone.android.core.contracts.notifications.NotificationCategory
import com.meshcoreone.android.core.contracts.notifications.NotificationPayload
import com.meshcoreone.android.core.contracts.notifications.NotificationResponse
import com.meshcoreone.android.core.model.RadioId
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

class MessagingNotificationTests {
    private fun assertEquals(expected: Any?, actual: Any?) = check(expected == actual) { "expected <$expected> but was <$actual>" }
    private fun assertNull(actual: Any?) = check(actual == null) { "expected null but was <$actual>" }
    private fun assertTrue(actual: Boolean) = check(actual) { "expected true" }

    private val radioA = RadioId(UUID.fromString("00000000-0000-0000-0000-00000000000a"))
    private val radioB = RadioId(UUID.fromString("00000000-0000-0000-0000-00000000000b"))
    private val contactId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val messageId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val direct = NotificationPayload.DirectMessage(EntityKey(radioA, contactId), messageId)
    private val channel = NotificationPayload.ChannelMessage(radioA, 255u, messageId)

    private fun <T> runNow(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return outcome!!.getOrThrow()
    }

    fun payloadsRoundTripWithTheirRadio() {
        val payloads = listOf(
            direct, channel,
            NotificationPayload.RoomMessage("Room", EntityKey(radioB, contactId), messageId),
            NotificationPayload.NewContact(EntityKey(radioA, contactId)),
            NotificationPayload.Reaction(messageId, EntityKey(radioA, contactId), null, null),
            NotificationPayload.Reaction(messageId, null, 3u, radioB),
            NotificationPayload.LowBattery(15),
            NotificationPayload.QuickReplyFailed(EntityKey(radioA, contactId)),
            NotificationPayload.ChannelQuickReplyFailed(radioB, 0u),
        )
        payloads.forEach { assertEquals(it, NotificationPayloadCodec.decode(NotificationPayloadCodec.encode(it))) }
    }

    fun malformedPayloadsDecodeToNull() {
        val good = NotificationPayloadCodec.encode(direct)
        assertNull(NotificationPayloadCodec.decode(good - "message"))
        assertNull(NotificationPayloadCodec.decode(good + ("contact" to "1-1-1-1-1")))
        assertNull(NotificationPayloadCodec.decode(good + ("type" to "unknown")))
        assertNull(NotificationPayloadCodec.decode(NotificationPayloadCodec.encode(channel) + ("channel" to "256")))
        assertNull(NotificationPayloadCodec.decode(emptyMap()))
    }

    fun coldStartQueuesReplyThenDrainsInOrder() {
        val router = MessagingResponseRouter(clock = { 0L })
        val reply = NotificationResponse(direct, NotificationAction.REPLY, "hi")
        val read = NotificationResponse(channel, NotificationAction.MARK_READ, null)
        assertEquals(ResponseDisposition.QUEUED_COLD_START, runNow { router.dispatch(reply) })
        assertEquals(ResponseDisposition.QUEUED_COLD_START, runNow { router.dispatch(read) })
        val seen = ArrayList<NotificationResponse>()
        val drained = runNow { router.attach { seen.add(it) } }
        assertEquals(listOf(reply, read), seen)
        assertEquals(listOf(ResponseDisposition.DELIVERED, ResponseDisposition.DELIVERED), drained)
        assertEquals(0, router.queuedCount)
    }

    fun staleMarkReadExpiresButStaleReplyIsNeverDropped() {
        var now = 0L
        val router = MessagingResponseRouter(clock = { now }, markReadTtlMillis = 100)
        runNow { router.dispatch(NotificationResponse(direct, NotificationAction.REPLY, "keep me")) }
        runNow { router.dispatch(NotificationResponse(direct, NotificationAction.MARK_READ, null)) }
        now = 1_000
        val seen = ArrayList<NotificationResponse>()
        val drained = runNow { router.attach { seen.add(it) } }
        assertEquals(listOf(ResponseDisposition.DELIVERED, ResponseDisposition.EXPIRED), drained)
        assertEquals("keep me", seen.single().userText)
    }

    fun otherRadioIsClassifiedStaleButStillDelivered() {
        val router = MessagingResponseRouter(currentRadio = { radioB })
        val seen = ArrayList<NotificationResponse>()
        runNow { router.attach { seen.add(it) } }
        val reply = NotificationResponse(direct, NotificationAction.REPLY, "text")
        assertEquals(ResponseDisposition.DELIVERED_STALE_RADIO, runNow { router.dispatch(reply) })
        assertEquals(listOf(reply), seen)
    }

    fun overflowEvictsMarkReadBeforeReply() {
        val router = MessagingResponseRouter(capacity = 2)
        val reply = NotificationResponse(direct, NotificationAction.REPLY, "a")
        runNow { router.dispatch(reply) }
        runNow { router.dispatch(NotificationResponse(direct, NotificationAction.MARK_READ, null)) }
        runNow { router.dispatch(NotificationResponse(channel, NotificationAction.REPLY, "b")) }
        val seen = ArrayList<NotificationResponse>()
        runNow { router.attach { seen.add(it) } }
        assertEquals(listOf("a", "b"), seen.map { it.userText })
    }

    fun nonActionableResponsesAreRejected() {
        val router = MessagingResponseRouter()
        assertEquals(ResponseDisposition.REJECTED, runNow { router.dispatch(NotificationResponse(direct, NotificationAction.REPLY, "  ")) })
        assertEquals(ResponseDisposition.REJECTED, runNow { router.dispatch(NotificationResponse(direct, NotificationAction.REPLY, null)) })
        assertEquals(ResponseDisposition.REJECTED, runNow { router.dispatch(NotificationResponse(direct, NotificationAction.DISMISS, null)) })
        assertEquals(
            ResponseDisposition.REJECTED,
            runNow { router.dispatch(NotificationResponse(NotificationPayload.LowBattery(5), NotificationAction.MARK_READ, null)) },
        )
        assertEquals(0, router.queuedCount)
    }

    fun denialMapsToStatusWithoutThrowing() {
        assertEquals(NotificationAuthorizationStatus.AUTHORIZED, MessagingAuthorization.status(true, true, true))
        assertEquals(NotificationAuthorizationStatus.NOT_DETERMINED, MessagingAuthorization.status(false, true, false))
        assertEquals(NotificationAuthorizationStatus.DENIED, MessagingAuthorization.status(false, true, true))
        assertEquals(NotificationAuthorizationStatus.DENIED, MessagingAuthorization.status(true, false, true))
    }

    fun categoriesMapToDistinctChannels() {
        val channels = NotificationCategory.entries.map { MessagingNotificationContract.channelFor(it) }
        assertTrue(channels.toSet().size >= 4)
        assertEquals(MessagingNotificationContract.CHANNEL_ALERTS, MessagingNotificationContract.channelFor(null))
    }

    /** Every case by name, for a future instrumented/JVM runner. */
    val cases: Map<String, () -> Unit> = mapOf(
        "payloadsRoundTripWithTheirRadio" to ::payloadsRoundTripWithTheirRadio,
        "malformedPayloadsDecodeToNull" to ::malformedPayloadsDecodeToNull,
        "coldStartQueuesReplyThenDrainsInOrder" to ::coldStartQueuesReplyThenDrainsInOrder,
        "staleMarkReadExpiresButStaleReplyIsNeverDropped" to ::staleMarkReadExpiresButStaleReplyIsNeverDropped,
        "otherRadioIsClassifiedStaleButStillDelivered" to ::otherRadioIsClassifiedStaleButStillDelivered,
        "overflowEvictsMarkReadBeforeReply" to ::overflowEvictsMarkReadBeforeReply,
        "nonActionableResponsesAreRejected" to ::nonActionableResponsesAreRejected,
        "denialMapsToStatusWithoutThrowing" to ::denialMapsToStatusWithoutThrowing,
        "categoriesMapToDistinctChannels" to ::categoriesMapToDistinctChannels,
    )
}
