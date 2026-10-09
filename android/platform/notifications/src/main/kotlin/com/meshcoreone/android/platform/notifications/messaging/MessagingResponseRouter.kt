// AndroidOnly: WP-401 Cold-start buffering and stale-radio/readiness guards between the receiver and the WP-215 policy.
package com.meshcoreone.android.platform.notifications.messaging

import com.meshcoreone.android.core.contracts.notifications.NotificationAction
import com.meshcoreone.android.core.contracts.notifications.NotificationPayload
import com.meshcoreone.android.core.contracts.notifications.NotificationResponse
import com.meshcoreone.android.core.model.RadioId

/** The WP-215 entry point, normally `NotificationService.didReceive`; wired by the app layer (WP-303). */
fun interface NotificationResponseSink {
    suspend fun handle(response: NotificationResponse)
}

/** What the router did with one response. */
enum class ResponseDisposition {
    /** Handed to the sink now. */
    DELIVERED,

    /** Handed to the sink now, but its radio is not the radio session the app currently serves. */
    DELIVERED_STALE_RADIO,

    /** No sink yet (process cold-started by the action): kept until [MessagingResponseRouter.attach]. */
    QUEUED_COLD_START,

    /** Not an actionable response (blank reply, dismiss, or a payload that offers no such action). */
    REJECTED,

    /** A queued mark-read that waited past its time-to-live, or was evicted from a full queue. */
    EXPIRED,
}

/**
 * Receiver-side guard. Rules, all fail-safe for message text:
 *  - A reply is never silently discarded. Cold start queues it; once a sink exists it is always
 *    delivered, because the policy handler turns a not-ready or other-radio reply into a saved draft
 *    plus a failure notification instead of transmitting from the wrong radio.
 *  - A mark-read is delivered even for another radio (the handler writes only to the action's own
 *    radio rows) but expires after [markReadTtlMillis] in the cold-start queue, so a stale tap cannot
 *    clear unread state long after the user acted.
 *  - The queue is bounded; overflow evicts the oldest mark-read first, then the oldest reply.
 * [currentRadio] only classifies staleness; it never decides whether text is kept.
 */
class MessagingResponseRouter(
    private val clock: () -> Long = System::currentTimeMillis,
    private val currentRadio: () -> RadioId? = { null },
    private val markReadTtlMillis: Long = DEFAULT_MARK_READ_TTL_MILLIS,
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    private class Queued(val response: NotificationResponse, val enqueuedAt: Long)

    private val lock = Any()
    private var sink: NotificationResponseSink? = null
    private val queue = ArrayList<Queued>()

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    val queuedCount: Int get() = synchronized(lock) { queue.size }

    suspend fun dispatch(response: NotificationResponse): ResponseDisposition {
        if (!isActionable(response)) return ResponseDisposition.REJECTED
        val target = synchronized(lock) {
            sink ?: run {
                enqueueLocked(response)
                return ResponseDisposition.QUEUED_COLD_START
            }
        }
        target.handle(response)
        return if (isStale(response)) ResponseDisposition.DELIVERED_STALE_RADIO else ResponseDisposition.DELIVERED
    }

    /** Installs the sink and drains everything queued during cold start, oldest first. Returns the drain outcomes. */
    suspend fun attach(newSink: NotificationResponseSink): List<ResponseDisposition> {
        val pending = synchronized(lock) {
            sink = newSink
            val drained = ArrayList(queue)
            queue.clear()
            drained
        }
        val now = clock()
        return pending.map { queued ->
            if (isExpired(queued, now)) {
                ResponseDisposition.EXPIRED
            } else {
                newSink.handle(queued.response)
                if (isStale(queued.response)) ResponseDisposition.DELIVERED_STALE_RADIO else ResponseDisposition.DELIVERED
            }
        }
    }

    /** Removes the sink (service teardown); later responses queue again so none are lost across a restart. */
    fun detach(oldSink: NotificationResponseSink) {
        synchronized(lock) { if (sink === oldSink) sink = null }
    }

    private fun enqueueLocked(response: NotificationResponse) {
        if (queue.size >= capacity) {
            val victim = queue.indexOfFirst { it.response.action == NotificationAction.MARK_READ }.takeIf { it >= 0 } ?: 0
            queue.removeAt(victim)
        }
        queue.add(Queued(response, clock()))
    }

    private fun isExpired(queued: Queued, now: Long): Boolean =
        queued.response.action == NotificationAction.MARK_READ && now - queued.enqueuedAt > markReadTtlMillis

    private fun isStale(response: NotificationResponse): Boolean {
        val active = currentRadio() ?: return false
        val radio = payloadRadio(response.payload) ?: return false
        return radio != active
    }

    companion object {
        const val DEFAULT_MARK_READ_TTL_MILLIS: Long = 120_000L
        const val DEFAULT_CAPACITY: Int = 16

        /** Reply needs non-blank text and a reply-capable payload; mark-read needs a message-bearing payload. */
        fun isActionable(response: NotificationResponse): Boolean = when (response.action) {
            NotificationAction.REPLY ->
                !response.userText.isNullOrBlank() &&
                    (response.payload is NotificationPayload.DirectMessage || response.payload is NotificationPayload.ChannelMessage)
            NotificationAction.MARK_READ ->
                MessagingNotificationContract.isConversationMessage(response.payload)
            NotificationAction.DISMISS -> false
            null -> true
        }

        fun payloadRadio(payload: NotificationPayload): RadioId? = when (payload) {
            is NotificationPayload.DirectMessage -> payload.contact.radioId
            is NotificationPayload.ChannelMessage -> payload.radioId
            is NotificationPayload.RoomMessage -> payload.session.radioId
            is NotificationPayload.NewContact -> payload.contact.radioId
            is NotificationPayload.Reaction -> payload.contact?.radioId ?: payload.radioId
            is NotificationPayload.QuickReplyFailed -> payload.contact.radioId
            is NotificationPayload.ChannelQuickReplyFailed -> payload.radioId
            is NotificationPayload.LowBattery -> null
        }
    }
}
