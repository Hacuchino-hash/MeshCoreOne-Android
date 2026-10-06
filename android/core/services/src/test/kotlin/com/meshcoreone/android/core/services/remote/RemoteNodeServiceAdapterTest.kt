// AndroidOnly: WP-210 Real MeshCoreSession wiring over MockTransport, event broadcasting, session management and response-compatibility cases.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.ByteWriter
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.command.PacketBuilder
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.config.SessionConfiguration
import com.meshcoreone.android.core.protocol.event.StatusResponse
import com.meshcoreone.android.core.protocol.model.ContactType
import com.meshcoreone.android.core.protocol.session.MeshCoreSession
import com.meshcoreone.android.core.protocol.transport.mock.MockTransport
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteNodeServiceAdapterTest {
    private val publicKey = remoteCoreKey(0xC7)

    private fun frame(code: Int, payload: Bytes = Bytes.EMPTY): Bytes = Bytes.of(code) + payload
    private fun le32(value: Long): Bytes = ByteWriter().appendUInt32LE(value.toUInt()).toBytes()
    private fun selfInfoFrame(): Bytes = frame(
        0x05, Bytes.of(1, 22, 22) + Bytes(ByteArray(32) { 1 }) + le32(0) + le32(0) + Bytes.of(0, 0, 0, 0) +
            le32(915_000) + le32(125_000) + Bytes.of(7, 5) + Bytes.utf8("Test"),
    )
    private fun sentFrame(timeoutMs: Long): Bytes = frame(0x06, Bytes.of(0, 0xAA, 0xBB, 0xCC, 0xDD) + le32(timeoutMs))
    private fun cliReplyFrame(text: String): Bytes =
        frame(0x10, Bytes.of(40, 0, 0) + publicKey.prefix(6) + Bytes.of(0, 1) + le32(1_786_722_487) + Bytes.utf8(text))

    @TestFactory
    fun adapterCases(): List<DynamicTest> = listOf(
        remoteCoreNative("a real MeshCoreSession drives login and a prefixed CLI exchange over MockTransport") {
            val dispatcher = checkNotNull(coroutineContext[ContinuationInterceptor])
            val clock = RemoteCoreTestClock()
            val transport = MockTransport()
            val meshSession = MeshCoreSession(
                transport, SessionConfiguration(defaultTimeout = 30.0, clientIdentifier = "MCTst"), clock, dispatcher,
            )
            val serviceScope = CoroutineScope(SupervisorJob() + dispatcher)
            try {
                val started = async { meshSession.start() }
                remoteCoreAwait("appStart never sent") { transport.sentData.size == 1 }
                transport.simulateReceive(selfInfoFrame())
                started.await()

                val store = RemoteCoreFakeStore()
                val remote = remoteCoreSession(com.meshcoreone.android.core.model.RadioId(java.util.UUID.randomUUID()), publicKey)
                store.saveRemoteNodeSessionDTO(remote)
                val key = EntityKey(remote.radioId, remote.id)
                val service = RemoteNodeService(
                    meshSession.asRemoteNodeSessionPort(), store, RemoteCoreFakePasswordStore(), serviceScope, clock,
                )
                service.startEventMonitoring()

                val login = async { service.login(key, "pw") }
                remoteCoreAwait("login frame never sent") { transport.sentData.size == 2 }
                assertEquals(PacketBuilder.sendLogin(publicKey, "pw"), transport.sentData[1])
                transport.simulateReceive(sentFrame(1000))
                remoteCoreSettle()
                transport.simulateReceive(frame(0x85, Bytes.of(1) + publicKey.prefix(6)))
                assertEquals(RoomPermissionLevel.ADMIN, login.await().permissionLevel)
                assertEquals(RoomPermissionLevel.ADMIN, store.session(key)?.permissionLevel)

                val command = async { service.sendCLICommand(key, "get tx") }
                remoteCoreAwait("command frame never sent") { transport.sentData.size == 3 }
                assertEquals(PacketBuilder.sendCommand(publicKey, "00|get tx", clock.wallClock.instant()), transport.sentData[2])
                transport.simulateReceive(sentFrame(100))
                remoteCoreAwait("reply poll never sent") { transport.sentData.size == 4 }
                assertEquals(PacketBuilder.getMessage(), transport.sentData[3])
                transport.simulateReceive(cliReplyFrame("00|> 22"))
                assertEquals("> 22", command.await())
                service.close()
            } finally {
                serviceScope.cancel()
                meshSession.stop()
            }
        },
        remoteCoreNative("events reach every subscriber registered before the emission; finishEvents ends collection") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey, isConnected = true))
                val first = service.events().let { stream -> async { stream.toList() } }
                val second = service.events().let { stream -> async { stream.toList() } }
                service.disconnect(key)
                service.finishEvents()
                val expected = listOf(RemoteNodeEvent.SessionStateChanged(key, false))
                assertEquals(expected, first.await())
                assertEquals(expected, second.await())
                assertEquals(emptyList(), service.events().toList())
                assertEquals(false, store.session(key)?.isConnected)
            }
        },
    )

    @TestFactory
    fun sessionCases(): List<DynamicTest> = listOf(
        remoteCoreNative("createSession reuses the existing row, preserves its state and validates role and key") {
            withRemoteCoreHarness {
                val contact = remoteCoreContact(radioId, publicKey, ContactType.REPEATER.rawValue)
                val created = service.createSession(radioId, contact)
                assertEquals(RemoteNodeRole.REPEATER, created.role)
                store.updateRemoteNodeSessionConnection(EntityKey(radioId, created.id), true, RoomPermissionLevel.ADMIN)
                val reused = service.createSession(radioId, contact)
                assertEquals(created.id, reused.id)
                assertFalse(reused.isConnected)
                assertEquals(RoomPermissionLevel.ADMIN, reused.permissionLevel)
                assertEquals(1, store.sessionCount)

                assertFailsWith<RemoteNodeError.InvalidResponse> {
                    service.createSession(radioId, remoteCoreContact(radioId, remoteCoreKey(0x01)))
                }
                val shortKey = remoteCoreContact(radioId, Bytes.of(1, 2, 3), ContactType.ROOM.rawValue)
                val error = assertFailsWith<RemoteNodeError.LoginFailed> { service.createSession(radioId, shortKey) }
                assertEquals("Login failed: Invalid public key length: expected 32 bytes, got 3", error.message)
            }
        },
        remoteCoreNative("logout ignores a failed send, resets to guest and broadcasts disconnected") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey, permissionLevel = RoomPermissionLevel.ADMIN, isConnected = true))
                session.sendLogoutError = MeshCoreException.Timeout()
                val event = service.events().let { stream -> async { stream.first() } }
                service.logout(key)
                assertEquals(RemoteNodeEvent.SessionStateChanged(key, false), event.await())
                assertEquals(listOf(publicKey), session.sendLogoutInvocations)
                assertEquals(RoomPermissionLevel.GUEST, store.session(key)?.permissionLevel)
                assertEquals(false, store.session(key)?.isConnected)
            }
        },
        remoteCoreNative("history sync requires a direct-routed room and requests room status") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey))
                assertFailsWith<RemoteNodeError.ContactNotFound> { service.requestHistorySync(key) }
                store.saveContact(remoteCoreContact(radioId, publicKey, ContactType.ROOM.rawValue))
                assertFailsWith<RemoteNodeError.FloodRouted> { service.requestHistorySync(key) }
                store.saveContact(remoteCoreContact(radioId, publicKey, ContactType.ROOM.rawValue, 1u, Bytes.of(0x0A)))
                session.requestStatusResult = Result.failure(MeshCoreException.DeviceError(4u))
                assertFailsWith<RemoteNodeError.SessionError> { service.requestHistorySync(key) }
                assertEquals(listOf(publicKey to ContactType.ROOM), session.requestStatusInvocations)
                val repeater = addSession(remoteCoreSession(radioId, remoteCoreKey(0x0B), RemoteNodeRole.REPEATER))
                assertFailsWith<RemoteNodeError.InvalidResponse> { service.requestHistorySync(repeater) }
            }
        },
        remoteCoreNative("removeSession deletes the stored password and the row") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey))
                service.storePassword("pw", publicKey)
                val contact = remoteCoreContact(radioId, publicKey)
                assertTrue(service.hasPassword(contact))
                assertEquals("pw", service.retrievePassword(contact))
                service.removeSession(key, publicKey)
                assertNull(store.session(key))
                assertFalse(service.hasPassword(contact))
            }
        },
    )

    @TestFactory
    fun responseCases(): List<DynamicTest> = listOf(
        remoteCoreNative("the outer status deadline runs on the injected clock and maps to timeout") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey))
                session.requestStatusGate = CompletableDeferred()
                val status = async { runCatching { service.requestStatus(key, 5.seconds) } }
                remoteCoreAwait("status never requested") { session.requestStatusInvocations.size == 1 }
                remoteCoreSettle()
                clock.advance(4.seconds)
                remoteCoreSettle()
                assertFalse(status.isCompleted)
                clock.advance(1.seconds)
                assertTrue(status.await().exceptionOrNull() is RemoteNodeError.Timeout)
            }
        },
        remoteCoreNative("StatusResponse compatibility clamps battery and RSSI and converts SNR") {
            val response = StatusResponse(
                publicKeyPrefix = publicKey.prefix(6), battery = 70_000, txQueueLength = 0, noiseFloor = -100,
                lastRSSI = -40_000, packetsReceived = 0u, packetsSent = 0u, airtime = 0u, uptime = 3600u,
                sentFlood = 0u, sentDirect = 0u, receivedFlood = 0u, receivedDirect = 0u, fullEvents = 0,
                lastSNR = 9.25, directDuplicates = 0, floodDuplicates = 0, rxAirtime = 12u,
            )
            assertEquals(UShort.MAX_VALUE, response.batteryMillivolts)
            assertEquals(Short.MIN_VALUE, response.lastRssi)
            assertEquals(9.25f, response.lastSnr)
            assertEquals(3600u, response.uptimeSeconds)
            assertEquals(12u, response.repeaterRxAirtimeSeconds)
            assertEquals(4100.toUShort(), response.copy(battery = 4100).batteryMillivolts)
        },
    )
}
