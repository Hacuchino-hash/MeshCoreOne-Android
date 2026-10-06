// AndroidOnly: WP-208 Review regression: a MESSAGES_WAITING push during a manual poll's paused auto-fetch is still drained.
package com.meshcoreone.android.core.services.messaging

import com.meshcoreone.android.core.protocol.bytes.ByteWriter
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.ResponseCode
import kotlin.test.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.TestFactory

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PollWaitingTest {
    @TestFactory fun pollWaiting() = listOf(
        native("a messagesWaiting push after a manual poll's final NoMore is still drained") {
            val h = Harness(this); h.start(); val p = h.poller()
            val received = mutableListOf<String>()
            p.setContactMessageHandler { message, _, _ -> received += message.text }
            p.startMessageEventMonitoring(); p.startAutoFetch(RADIO); runCurrent()
            h.transport.afterNoMore = {
                h.transport.incomingMessages += contactPacket("late")
                h.transport.mock.simulateReceive(Bytes.of(ResponseCode.MESSAGES_WAITING.rawValue.toInt()))
            }
            assertEquals(0L, p.pollAllMessages())
            advanceUntilIdle()
            assertEquals(listOf("late"), received, "The message the radio announced during the poll must not sit on the radio")
            assertTrue(h.transport.incomingMessages.isEmpty())
            p.close(); h.close()
        },
        native("a poll with auto-fetch disabled does not start a follow-up drain for a push") {
            val h = Harness(this); h.start(); val p = h.poller()
            val received = mutableListOf<String>()
            p.setContactMessageHandler { message, _, _ -> received += message.text }
            h.transport.afterNoMore = {
                h.transport.incomingMessages += contactPacket("left for the next poll")
                h.transport.mock.simulateReceive(Bytes.of(ResponseCode.MESSAGES_WAITING.rawValue.toInt()))
            }
            assertEquals(0L, p.pollAllMessages())
            advanceUntilIdle()
            assertTrue(received.isEmpty(), "Without auto-fetch the source would not drain on a push either")
            assertEquals(1, h.transport.incomingMessages.size)
            p.close(); h.close()
        },
    )

    private fun contactPacket(text: String): Bytes = ByteWriter().appendUInt8(ResponseCode.CONTACT_MESSAGE_RECEIVED.rawValue)
        .append(TARGET.prefix(6)).appendUInt8(0u).appendUInt8(0u).appendUInt32LE(UInt.MAX_VALUE)
        .append(Bytes.utf8(text)).toBytes()
}
