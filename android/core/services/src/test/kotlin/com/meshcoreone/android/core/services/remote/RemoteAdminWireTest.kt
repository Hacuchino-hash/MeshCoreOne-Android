// AndroidOnly: WP-210 Real MeshCoreSession over MockTransport: exact request bytes and response decoding for the binary protocol and repeater neighbour paths, including the timeout path-reset retry.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.ByteWriter
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.command.PacketBuilder
import com.meshcoreone.android.core.protocol.config.SessionConfiguration
import com.meshcoreone.android.core.protocol.event.ACLEntry
import com.meshcoreone.android.core.protocol.event.ACLResponse
import com.meshcoreone.android.core.protocol.event.Neighbour
import com.meshcoreone.android.core.protocol.event.NeighboursResponse
import com.meshcoreone.android.core.protocol.event.TelemetryResponse
import com.meshcoreone.android.core.protocol.model.BinaryRequestType
import com.meshcoreone.android.core.protocol.session.MeshCoreSession
import com.meshcoreone.android.core.protocol.transport.mock.MockTransport
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RemoteAdminWireTest {
    private val publicKey = remoteCoreKey(0x5A)
    private val tag = Bytes.of(0xAA, 0xBB, 0xCC, 0xDD)

    private fun frame(code: Int, payload: Bytes = Bytes.EMPTY): Bytes = Bytes.of(code) + payload
    private fun le32(value: Long): Bytes = ByteWriter().appendUInt32LE(value.toUInt()).toBytes()
    private fun le16(value: Int): Bytes = Bytes.of(value and 0xFF, (value shr 8) and 0xFF)
    private fun selfInfoFrame(): Bytes = frame(
        0x05, Bytes.of(1, 22, 22) + Bytes(ByteArray(32) { 1 }) + le32(0) + le32(0) + Bytes.of(0, 0, 0, 0) +
            le32(915_000) + le32(125_000) + Bytes.of(7, 5) + Bytes.utf8("Test"),
    )
    private fun sentFrame(timeoutMs: Long, ack: Bytes = tag): Bytes = frame(0x06, Bytes.of(0) + ack + le32(timeoutMs))
    private fun binaryResponseFrame(data: Bytes, ack: Bytes = tag): Bytes = frame(0x8C, Bytes.of(0) + ack + data)

    private class Wire(val transport: MockTransport, val session: MeshCoreSession, val clock: RemoteCoreTestClock, val scope: CoroutineScope)

    /** Starts a real session over MockTransport on the test dispatcher and virtual clock. */
    private suspend fun CoroutineScope.withWire(configuration: SessionConfiguration, block: suspend Wire.() -> Unit) {
        val dispatcher = checkNotNull(coroutineContext[ContinuationInterceptor])
        val clock = RemoteCoreTestClock()
        val transport = MockTransport()
        val session = MeshCoreSession(transport, configuration, clock, dispatcher)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val started = async { session.start() }
            remoteCoreAwait("appStart never sent") { transport.sentData.size == 1 }
            transport.simulateReceive(selfInfoFrame())
            started.await()
            Wire(transport, session, clock, scope).block()
        } finally {
            scope.cancel()
            session.stop()
        }
    }

    private val defaultConfig = SessionConfiguration(defaultTimeout = 30.0, clientIdentifier = "MCTst")

    @TestFactory
    fun wireCases(): List<DynamicTest> = listOf(
        remoteCoreNative("requestACL sends 0x32+key+0x05+0000 and decodes 7-byte records, skipping the all-zero key") {
            withWire(defaultConfig) {
                val service = BinaryProtocolService(session.asBinaryProtocolSessionPort(), scope)
                val acl = async { service.requestACL(publicKey) }
                remoteCoreAwait("ACL request never sent") { transport.sentData.size == 2 }
                assertEquals(frame(0x32, publicKey + Bytes.of(0x05, 0x00, 0x00)), transport.sentData[1])
                assertEquals(PacketBuilder.binaryRequest(publicKey, BinaryRequestType.ACL, Bytes.of(0, 0)), transport.sentData[1])
                transport.simulateReceive(sentFrame(1000))
                remoteCoreSettle()
                transport.simulateReceive(
                    binaryResponseFrame(Bytes.of(1, 2, 3, 4, 5, 6, 0x03) + Bytes.of(0, 0, 0, 0, 0, 0, 0x01) + Bytes.of(9, 8, 7, 6, 5, 4, 0x00)),
                )
                assertEquals(
                    ACLResponse(publicKey.prefix(6), tag, listOf(ACLEntry(Bytes.of(1, 2, 3, 4, 5, 6), 3u), ACLEntry(Bytes.of(9, 8, 7, 6, 5, 4), 0u))),
                    acl.await(),
                )
                service.close()
            }
        },
        remoteCoreNative("a binary timeout sends RESET_PATH (0x0D+key), then resends the identical request and returns its answer") {
            withWire(defaultConfig.copy(binaryRequestOverallTimeout = 5.0, binaryRequestRetransmitInterval = null)) {
                val service = BinaryProtocolService(session.asBinaryProtocolSessionPort(), scope)
                val acl = async { service.requestACL(publicKey) }
                remoteCoreAwait("ACL request never sent") { transport.sentData.size == 2 }
                transport.simulateReceive(sentFrame(1000))
                remoteCoreSettle()
                clock.advance(4.seconds)
                remoteCoreSettle()
                assertEquals(2, transport.sentData.size)
                clock.advance(1.seconds)
                remoteCoreAwait("path reset never sent") { transport.sentData.size == 3 }
                assertEquals(frame(0x0D, publicKey), transport.sentData[2])
                transport.simulateOK()
                remoteCoreAwait("retry never sent") { transport.sentData.size == 4 }
                assertEquals(transport.sentData[1], transport.sentData[3])
                assertFalse(acl.isCompleted)
                // The timed-out exchange retired its tag; the firmware tags the resend afresh.
                val retryTag = Bytes.of(0x11, 0x22, 0x33, 0x44)
                transport.simulateReceive(sentFrame(1000, retryTag))
                remoteCoreSettle()
                transport.simulateReceive(binaryResponseFrame(Bytes.of(1, 1, 1, 1, 1, 1, 0x02), retryTag))
                val answer = acl.await()
                assertEquals(retryTag, answer.tag)
                assertEquals(listOf(ACLEntry(Bytes.of(1, 1, 1, 1, 1, 1), 2u)), answer.entries.toList())
                service.close()
            }
        },
        remoteCoreNative("repeater requestNeighbors encodes count, LE offset, order and prefix length and decodes total, entries and SNR/4") {
            withWire(defaultConfig) {
                val core = RemoteCoreHarness(checkNotNull(coroutineContext[ContinuationInterceptor]))
                try {
                    val store = RemoteAdminFakeStore(core.store, core.clock)
                    val dto = remoteCoreSession(core.radioId, publicKey, RemoteNodeRole.REPEATER, RoomPermissionLevel.ADMIN, true)
                    val key = core.addSession(dto)
                    val repeater = RepeaterAdminService(session, core.service, store, clock)
                    val request = async { repeater.requestNeighbors(key, count = 20u, offset = 0x0102u, orderBy = NeighborSortOrder.STRONGEST_FIRST) }
                    remoteCoreAwait("neighbours request never sent") { transport.sentData.size == 2 }
                    val sent = transport.sentData[1]
                    assertEquals(1 + 32 + 1 + 10, sent.size)
                    assertEquals(frame(0x32, publicKey + Bytes.of(0x06, 0x00, 20, 0x02, 0x01, 0x02, 0x06)), sent.prefix(40))
                    transport.simulateReceive(sentFrame(1000))
                    remoteCoreSettle()
                    val entry1 = Bytes.of(1, 2, 3, 4, 5, 6) + le32(120) + Bytes.of(0xF6)
                    val entry2 = Bytes.of(6, 5, 4, 3, 2, 1) + le32(7) + Bytes.of(0x1A)
                    transport.simulateReceive(binaryResponseFrame(le16(9) + le16(2) + entry1 + entry2))
                    assertEquals(
                        NeighboursResponse(
                            publicKey.prefix(6), tag, 9,
                            listOf(Neighbour(Bytes.of(1, 2, 3, 4, 5, 6), 120, -2.5), Neighbour(Bytes.of(6, 5, 4, 3, 2, 1), 7, 6.5)),
                        ),
                        request.await(),
                    )
                } finally {
                    core.close()
                }
            }
        },
        remoteCoreNative("a TELEMETRY_RESPONSE push (0x8B) reaches the monitoring BinaryProtocolService handler decoded") {
            withWire(defaultConfig) {
                val service = BinaryProtocolService(session.asBinaryProtocolSessionPort(), scope)
                val seen = mutableListOf<TelemetryResponse>()
                service.setTelemetryResponseHandler { seen += it }
                service.startEventMonitoring()
                transport.simulateReceive(frame(0x8B, Bytes.of(0) + publicKey.prefix(6) + Bytes.of(1, 0x67, 0x00, 0xFA)))
                remoteCoreAwait("telemetry push never delivered") { seen.size == 1 }
                assertEquals(TelemetryResponse(publicKey.prefix(6), null, Bytes.of(1, 0x67, 0x00, 0xFA)), seen.single())
                assertEquals(1, seen.single().dataPoints.size)
                service.close()
            }
        },
    )
}
