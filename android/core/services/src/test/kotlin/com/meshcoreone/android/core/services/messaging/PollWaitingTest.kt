// AndroidOnly: WP-208 PR47 success/failure catch-up and current auto-fetch intent over real raw pushes.
package com.meshcoreone.android.core.services.messaging

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.contracts.domain.errors.MessagePollingException
import com.meshcoreone.android.core.model.DeliveryContext
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.CommandCode
import com.meshcoreone.android.core.protocol.model.ResponseCode
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PollWaitingTest {
    @TestFactory fun pollWaiting() = listOf(
        native("a messagesWaiting push after a manual poll's final NoMore is still drained") {
            val h = Harness(this); h.start(); val p = h.poller(); val received = mutableListOf<String>()
            p.setContactMessageHandler { message, _, context ->
                assertEquals(DeliveryContext.Live, context); received += message.text
            }
            p.startAutoFetch(RADIO); runCurrent()
            h.transport.afterNoMore = {
                h.transport.incomingMessages += reviewContactPacket("late")
                h.transport.mock.simulateReceive(Bytes.of(ResponseCode.MESSAGES_WAITING.rawValue.toInt()))
            }
            assertEquals(0L, p.pollAllMessages()); advanceUntilIdle()
            assertEquals(listOf("late"), received); assertTrue(h.transport.incomingMessages.isEmpty())
            assertEquals(0, p.undeliveredCount); p.close(); h.close()
        },
        native("a poll with auto-fetch disabled does not start a follow-up drain for a push") {
            val h = Harness(this); h.start(); val p = h.poller(); val received = mutableListOf<String>()
            p.setContactMessageHandler { message, _, _ -> received += message.text }
            h.transport.afterNoMore = {
                h.transport.incomingMessages += reviewContactPacket("left for the next poll")
                h.transport.mock.simulateReceive(Bytes.of(ResponseCode.MESSAGES_WAITING.rawValue.toInt()))
            }
            assertEquals(0L, p.pollAllMessages()); advanceUntilIdle()
            assertTrue(received.isEmpty()); assertEquals(1, h.transport.incomingMessages.size)
            assertEquals(1, h.sends(CommandCode.GET_MESSAGE).size); p.close(); h.close()
        },
        native("a messagesWaiting push during a failed manual drain still has a live catch-up") {
            val h = Harness(this); h.start(); val p = h.poller(); val received = mutableListOf<String>()
            p.setContactMessageHandler { message, _, context ->
                assertEquals(DeliveryContext.Live, context); received += message.text
            }
            p.startAutoFetch(RADIO); runCurrent(); var failFirst = true
            h.transport.beforeReply = { packet ->
                if (packet[0] == CommandCode.GET_MESSAGE.rawValue) {
                    h.transport.holdGetReplies = failFirst
                    if (failFirst) {
                        failFirst = false
                        h.transport.incomingMessages += reviewContactPacket("after failure")
                        h.transport.mock.simulateError(7u)
                        h.transport.mock.simulateReceive(Bytes.of(ResponseCode.MESSAGES_WAITING.rawValue.toInt()))
                    }
                }
            }
            assertFailsWith<MessagePollingException> { p.pollAllMessages() }; advanceUntilIdle()
            assertEquals(listOf("after failure"), received); assertTrue(h.transport.incomingMessages.isEmpty())
            p.close(); h.close()
        },
        native("the newest user pause is preserved instead of starting catch-up after NoMore") {
            val h = Harness(this); h.start(); val p = h.poller(); var received = 0
            p.setContactMessageHandler { _, _, _ -> received++ }; p.startAutoFetch(RADIO); runCurrent()
            h.transport.afterNoMore = {
                p.pauseAutoFetch()
                h.transport.incomingMessages += reviewContactPacket("paused")
                h.transport.mock.simulateReceive(Bytes.of(ResponseCode.MESSAGES_WAITING.rawValue.toInt()))
            }
            assertEquals(0L, p.pollAllMessages()); advanceUntilIdle()
            assertEquals(0, received); assertEquals(1, h.transport.incomingMessages.size)
            assertEquals(1, h.sends(CommandCode.GET_MESSAGE).size); p.close(); h.close()
        },
        native("a successor cannot inherit the catch-up work of a retired manual poll") {
            val h = Harness(this); h.start(); val p = h.poller(); var received = 0
            p.setContactMessageHandler { _, _, _ -> received++ }; p.startAutoFetch(RADIO); runCurrent()
            h.transport.afterNoMore = {
                h.signals.set(token(generation = 2), DeviceConnectionState.READY)
                h.transport.incomingMessages += reviewContactPacket("successor only")
            }
            assertFailsWith<MessagePollingException> { p.pollAllMessages() }; runCurrent()
            assertEquals(0, received); assertEquals(1, h.sends(CommandCode.GET_MESSAGE).size)
            assertEquals(1, h.transport.incomingMessages.size); p.close(); h.close()
        },
        native("overlapping manual completion cannot discard a request behind an already running catch-up") {
            val h = Harness(this); h.start(); val p = h.poller(); val received = mutableListOf<String>()
            p.setContactMessageHandler { message, _, _ -> received += message.text }; p.startAutoFetch(RADIO); runCurrent()
            h.transport.afterNoMore = { h.transport.holdGetReplies = true }
            assertEquals(0L, p.pollAllMessages()); runCurrent()
            assertEquals(2, h.sends(CommandCode.GET_MESSAGE).size)
            val manual = backgroundScope.async { p.pollAllMessages() }; runCurrent()
            h.transport.mock.simulateReceive(Bytes.of(ResponseCode.NO_MORE_MESSAGES.rawValue.toInt()))
            h.transport.incomingMessages += reviewContactPacket("after shared NoMore"); h.transport.holdGetReplies = false
            assertEquals(0L, manual.await()); advanceUntilIdle()
            assertEquals(listOf("after shared NoMore"), received)
            assertTrue(h.transport.incomingMessages.isEmpty()); p.close(); h.close()
        },
    )
}
