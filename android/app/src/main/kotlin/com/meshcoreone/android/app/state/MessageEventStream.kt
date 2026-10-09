// PortedFrom: MC1/State/MessageEventStream.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.state

import com.meshcoreone.android.core.runtime.EventBroadcaster
import kotlinx.coroutines.flow.Flow

/**
 * Distributes already-resolved [MessageEvent] values to chat and room consumers. Registration happens when
 * [events] is called (not when the flow is collected), so events sent after the call are never dropped. Each
 * returned flow is single-collection; collection end or cancellation unregisters it immediately.
 */
class MessageEventStream {
    private val broadcaster = EventBroadcaster<MessageEvent>()

    /** Live subscriptions (Swift DEBUG `subscriberCount()`). */
    val subscriberCount: Int get() = broadcaster.subscriberCount

    fun events(): Flow<MessageEvent> = broadcaster.subscribe().events

    fun send(event: MessageEvent) = broadcaster.yield(event)
}
