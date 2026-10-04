// AndroidOnly: WP-103 Compose actual WP-108 framing/transport with the real parser and WP-106 filtered multicast.
package com.meshcoreone.android.core.protocol.parser

import com.meshcoreone.android.core.protocol.bytes.ByteWriter
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.event.ChannelDatagram
import com.meshcoreone.android.core.protocol.event.EventDispatcher
import com.meshcoreone.android.core.protocol.event.EventFilter
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.event.TelemetryResponse
import com.meshcoreone.android.core.protocol.transport.mock.MockTransport
import com.meshcoreone.android.core.protocol.transport.tcp.WiFiFrameDecoder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransportParserIntegrationTest {
    private val packets = listOf(
        hex("82deadbeeff4010000"),
        hex("8b00aabbccddeeff016700fa02ff"),
        hex("1b80000003ff3412030080ff"),
        hex("fe0102"),
        Bytes.EMPTY,
    )
    private val expected = listOf(
        MeshEvent.Acknowledgement(requestTag, 500u),
        MeshEvent.TelemetryResponse(TelemetryResponse(nodePrefix, null, hex("016700fa02ff"))),
        MeshEvent.ChannelDataReceived(ChannelDatagram(3u, 0xffu, 0x1234u, hex("0080ff"), -32.0)),
        MeshEvent.ParseFailure(hex("fe0102"), "Unknown response code: 0xFE"),
        MeshEvent.ParseFailure(Bytes.EMPTY, "Empty packet"),
    )

    @Test
    fun splitAndCoalescedTcpFramesRouteEveryPacketInOrder() {
        val wire = packets.fold(Bytes.EMPTY) { data, packet ->
            data + ByteWriter().appendUInt8(0x3eu).appendUInt16LE(packet.size.toUShort()).append(packet).toBytes()
        }
        for (split in 0..wire.size) {
            val decoder = WiFiFrameDecoder()
            val decoded = decoder.decode(wire.prefix(split)) + decoder.decode(wire.slice(split, wire.size))
            decoder.finish()
            assertEquals(packets, decoded, "split=$split")
            assertEquals(expected, decoded.map { PacketParser.parse(it) }, "split=$split")
            assertEquals(0, decoder.bufferedByteCount); assertEquals(0L, decoder.discardedByteCount)
        }
        val decoder = WiFiFrameDecoder()
        val decoded = wire.flatMap { decoder.decode(Bytes.of(it.toInt())) }
        decoder.finish()
        assertEquals(expected, decoded.map { PacketParser.parse(it) })
    }

    @Test
    fun actualTransportStreamFeedsFilteredMulticastAndTerminatesAcrossReconnect() = runTest {
        val transport = MockTransport()
        transport.connect()
        val incoming = transport.receivedData()
        val dispatcher = EventDispatcher { error("The five-packet pipeline must not drop events: $it") }
        val first = dispatcher.subscribe()
        val second = dispatcher.subscribe()
        val acknowledgements = dispatcher.subscribe(EventFilter.acknowledgement(requestTag))
        val firstResult = async(start = CoroutineStart.UNDISPATCHED) { first.toList() }
        val secondResult = async(start = CoroutineStart.UNDISPATCHED) { second.toList() }
        val ackResult = async(start = CoroutineStart.UNDISPATCHED) { acknowledgements.toList() }
        val ingestion = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                incoming.collect { dispatcher.dispatch(PacketParser.parse(it)) }
            } finally {
                dispatcher.finishAllSubscriptions()
            }
        }
        packets.forEach { transport.simulateReceive(it) }
        transport.disconnect()
        ingestion.join()
        assertEquals(expected, firstResult.await()); assertEquals(expected, secondResult.await())
        assertEquals(listOf(MeshEvent.Acknowledgement(requestTag, 500u)), ackResult.await())
        assertEquals(0L, dispatcher.droppedEventCount); assertEquals(0, dispatcher.subscriberCount)
        assertTrue(firstResult.isCompleted && secondResult.isCompleted && ackResult.isCompleted)
        assertFalse(transport.isConnected())

        transport.connect()
        val newIncoming = transport.receivedData()
        val reconnected = async(start = CoroutineStart.UNDISPATCHED) {
            newIncoming.map { PacketParser.parse(it) }.toList()
        }
        transport.simulateReceive(hex("01ff"))
        transport.disconnect()
        assertEquals(listOf(MeshEvent.Error(255u)), reconnected.await())
    }
}
