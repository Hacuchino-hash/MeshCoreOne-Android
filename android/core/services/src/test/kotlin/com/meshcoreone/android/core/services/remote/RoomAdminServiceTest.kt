// AndroidOnly: WP-210 Native RoomAdminService cases (Swift has no unit tests for it): pass-throughs, admin-only CLI, room session queries and ROOM-targeted handler audits.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.event.ContactMessage
import com.meshcoreone.android.core.protocol.event.TelemetryResponse
import com.meshcoreone.android.core.protocol.model.ContactType
import java.util.UUID
import kotlinx.coroutines.async
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RoomAdminServiceTest {
    private val publicKey = remoteCoreKey(0xB1)

    private suspend fun RemoteAdminHarness.roomSession(
        permission: RoomPermissionLevel = RoomPermissionLevel.ADMIN, connected: Boolean = true, key: Bytes = publicKey,
    ): EntityKey = core.addSession(remoteCoreSession(core.radioId, key, RemoteNodeRole.ROOM_SERVER, permission, connected))

    @TestFactory
    fun roomAdminCases(): List<DynamicTest> = listOf(
        remoteCoreNative("room status uses the room-server layout; telemetry and errors pass through RemoteNodeService") {
            withRemoteAdminHarness {
                val key = roomSession()
                val status = remoteAdminStatus(publicKey.prefix(6))
                core.session.requestStatusResult = Result.success(status)
                assertSame(status, roomAdmin.requestStatus(key))
                assertEquals(listOf(publicKey to ContactType.ROOM), core.session.requestStatusInvocations)
                val telemetry = TelemetryResponse(publicKey.prefix(6), null, Bytes.EMPTY)
                core.session.requestTelemetryResult = Result.success(telemetry)
                assertSame(telemetry, roomAdmin.requestTelemetry(key))
                core.session.requestTelemetryResult = Result.failure(MeshCoreException.DeviceError(2u))
                assertFailsWith<RemoteNodeError.SessionError> { roomAdmin.requestTelemetry(key) }
                assertFailsWith<RemoteNodeError.SessionNotFound> { roomAdmin.requestStatus(EntityKey(core.radioId, UUID.randomUUID())) }
            }
        },
        remoteCoreNative("room CLI commands are admin-only: read-write members are denied, admins get the reply") {
            withRemoteAdminHarness {
                core.startMonitoring()
                val member = roomSession(RoomPermissionLevel.READ_WRITE, key = remoteCoreKey(0xB2))
                assertFailsWith<RemoteNodeError.PermissionDenied> { roomAdmin.sendCommand(member, "ver") }
                assertFailsWith<RemoteNodeError.PermissionDenied> { roomAdmin.sendRawCommand(member, "ver") }
                assertTrue(core.session.sendCommandInvocations.isEmpty())
                val key = roomSession()
                val reply = async { roomAdmin.sendCommand(key, "get name") }
                remoteCoreAwait("command never sent") { core.session.sendCommandInvocations.size == 1 }
                core.yieldReply("00|> Lobby", publicKey)
                assertEquals("> Lobby", reply.await())
                val raw = async { roomAdmin.sendRawCommand(key, "ver") }
                remoteCoreAwait("raw command never sent") { core.session.sendCommandInvocations.size == 2 }
                core.yieldReply("01|v1.9.0", publicKey)
                assertEquals("v1.9.0", raw.await())
            }
        },
        remoteCoreNative("fetchRoomAdminSessions keeps rooms on the radio; getConnectedSession needs a connected room") {
            withRemoteAdminHarness {
                val key = roomSession()
                roomSession(connected = false, key = remoteCoreKey(0xB3))
                core.addSession(remoteCoreSession(core.radioId, remoteCoreKey(0xB4), RemoteNodeRole.REPEATER, isConnected = true))
                core.addSession(remoteCoreSession(RadioId(UUID.randomUUID()), remoteCoreKey(0xB5)))
                assertEquals(2, roomAdmin.fetchRoomAdminSessions(core.radioId).size)
                assertTrue(roomAdmin.fetchRoomAdminSessions(core.radioId).all { it.isRoom })
                assertEquals(key.id, roomAdmin.getConnectedSession(core.radioId, publicKey.prefix(6))?.id)
                assertNull(roomAdmin.getConnectedSession(core.radioId, remoteCoreKey(0xB3).prefix(6)))
                assertNull(roomAdmin.getConnectedSession(core.radioId, remoteCoreKey(0xB4).prefix(6)))
                assertNull(roomAdmin.getConnectedSession(RadioId(UUID.randomUUID()), publicKey.prefix(6)))
            }
        },
        remoteCoreNative("room handlers audit with the ROOM target before delivery; clearStatusHandlers keeps the CLI handler") {
            withRemoteAdminHarness {
                val prefix = publicKey.prefix(6)
                val contact = remoteCoreContact(core.radioId, publicKey, ContactType.ROOM.rawValue)
                val message = ContactMessage(prefix, 0u, 1u, core.clock.wallClock.instant(), null, "> ok", null)
                val seen = mutableListOf<String>()
                roomAdmin.setStatusHandler { seen += "status ${audit.entries.size}" }
                roomAdmin.setTelemetryHandler { seen += "telemetry ${audit.entries.size}" }
                roomAdmin.setCLIHandler { m, _ -> seen += "cli ${m.text} ${audit.entries.size}" }
                roomAdmin.invokeStatusHandler(remoteAdminStatus(prefix, battery = -5, uptime = 7u))
                roomAdmin.invokeTelemetryHandler(TelemetryResponse(prefix, null, Bytes.EMPTY))
                roomAdmin.invokeCLIHandler(message, contact)
                assertEquals(listOf("status 1", "telemetry 2", "cli > ok 3"), seen)
                assertEquals(
                    listOf(
                        "statusResponse ROOM ${prefix.hexString} 0 7",
                        "telemetryResponse ROOM ${prefix.hexString} 0",
                        "cliResponse ${publicKey.hexString} > ok",
                    ),
                    audit.entries,
                )

                roomAdmin.clearStatusHandlers()
                roomAdmin.invokeStatusHandler(remoteAdminStatus(prefix))
                roomAdmin.invokeTelemetryHandler(TelemetryResponse(prefix, null, Bytes.EMPTY))
                roomAdmin.invokeCLIHandler(message, contact)
                assertEquals(listOf("status 1", "telemetry 2", "cli > ok 3", "cli > ok 6"), seen)
                roomAdmin.clearHandlers()
                roomAdmin.invokeCLIHandler(message, contact)
                assertEquals(4, seen.size)
                assertEquals(7, audit.entries.size)
            }
        },
    )
}
