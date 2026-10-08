// AndroidOnly: WP-210 Native RoomServerService join/reconnect/leave and history-sync cases (Swift has no unit tests for it), including path-discovery timing on the virtual clock and cancellation.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.model.ContactType
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomServerJoinTest {
    private val roomKey = remoteCoreKey(0xE1)

    private fun RemoteAdminHarness.roomContact(direct: Boolean) = if (direct) {
        remoteCoreContact(core.radioId, roomKey, ContactType.ROOM.rawValue, outPathLength = 1u, outPath = Bytes.of(0x0A))
    } else {
        remoteCoreContact(core.radioId, roomKey, ContactType.ROOM.rawValue)
    }

    /** Starts monitoring, saves the contact (unless [saveContact] is false) and scripts one accepted login send. */
    private suspend fun RemoteAdminHarness.prepareJoin(direct: Boolean, saveContact: Boolean = true) = roomContact(direct).also {
        core.startMonitoring()
        if (saveContact) core.store.saveContact(it)
        core.session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
    }

    /** Lets the discovery loop register its next 500 ms sleep, then advances past it. */
    private suspend fun RemoteAdminHarness.pollOnce() {
        remoteCoreSettle()
        core.clock.advance(500.milliseconds)
        remoteCoreSettle()
    }

    @TestFactory
    fun joinCases(): List<DynamicTest> = listOf(
        remoteCoreNative("joinRoom logs in, stores the password and syncs history over the advert path") {
            withRemoteAdminHarness {
                val contact = prepareJoin(direct = true)
                core.session.requestStatusResult = Result.success(remoteAdminStatus(roomKey.prefix(6)))
                val join = async { rooms.joinRoom(core.radioId, contact, "pw", pathLength = 1u) }
                remoteAdminAnswerLogin(roomKey, 1u, admin = false)
                val session = join.await()
                assertTrue(session.isConnected)
                assertEquals(RoomPermissionLevel.READ_WRITE, session.permissionLevel)
                assertTrue(session.canPost)
                assertEquals("pw", core.passwords.stored(roomKey))
                assertEquals(listOf(roomKey to ContactType.ROOM), core.session.requestStatusInvocations)
                assertTrue(core.session.sendPathDiscoveryInvocations.isEmpty())
            }
        },
        remoteCoreNative("a flood-routed room triggers path discovery and syncs once a 500 ms poll sees a direct path") {
            withRemoteAdminHarness {
                val contact = prepareJoin(direct = false)
                core.session.sendPathDiscoveryHandler = { RemoteCoreFakeSession.sentInfo() }
                core.session.requestStatusResult = Result.success(remoteAdminStatus(roomKey.prefix(6)))
                val join = async { rooms.joinRoom(core.radioId, contact, "pw", rememberPassword = false) }
                remoteAdminAnswerLogin(roomKey, 2u, admin = true)
                remoteCoreAwait("path discovery never sent") { core.session.sendPathDiscoveryInvocations.size == 1 }
                assertEquals(listOf(roomKey), core.session.sendPathDiscoveryInvocations)
                pollOnce()
                assertFalse(join.isCompleted)
                core.store.saveContact(roomContact(direct = true))
                remoteCoreSettle()
                assertTrue(core.session.requestStatusInvocations.isEmpty(), "polls only on the 500 ms cadence")
                pollOnce()
                assertEquals(RoomPermissionLevel.ADMIN, join.await().permissionLevel)
                assertEquals(1, core.session.requestStatusInvocations.size)
                assertNull(core.passwords.stored(roomKey))
            }
        },
        remoteCoreNative("path discovery gives up after exactly 10 s (20 polls) and the join still succeeds without a sync") {
            withRemoteAdminHarness {
                val contact = prepareJoin(direct = false)
                core.session.sendPathDiscoveryHandler = { RemoteCoreFakeSession.sentInfo() }
                val join = async { rooms.joinRoom(core.radioId, contact, "pw") }
                remoteAdminAnswerLogin(roomKey, 1u, admin = false)
                remoteCoreAwait("path discovery never sent") { core.session.sendPathDiscoveryInvocations.size == 1 }
                repeat(19) { pollOnce() }
                assertFalse(join.isCompleted, "still polling at 9.5 s")
                pollOnce()
                assertTrue(join.await().isConnected)
                assertTrue(core.session.requestStatusInvocations.isEmpty())
                assertEquals(0, core.clock.sleeperCount)
            }
        },
        remoteCoreNative("a failed advert-path sync falls back to discovery, which sees the direct path at once and syncs again") {
            withRemoteAdminHarness {
                val contact = prepareJoin(direct = true)
                core.session.setRequestStatusResults(
                    listOf(Result.failure(MeshCoreException.Timeout()), Result.success(remoteAdminStatus(roomKey.prefix(6)))),
                )
                val join = async { rooms.joinRoom(core.radioId, contact, "pw") }
                remoteAdminAnswerLogin(roomKey, 1u, admin = false)
                join.await()
                assertEquals(2, core.session.requestStatusInvocations.size)
                assertTrue(core.session.sendPathDiscoveryInvocations.isEmpty())
            }
        },
        remoteCoreNative("history-sync failures never fail the join: discovery send error, missing contact, store error, failed sync") {
            withRemoteAdminHarness {
                val contact = prepareJoin(direct = false)
                core.session.sendPathDiscoveryHandler = { throw MeshCoreException.NotConnected() }
                val first = async { rooms.joinRoom(core.radioId, contact, "pw") }
                remoteAdminAnswerLogin(roomKey, 1u, admin = false)
                assertTrue(first.await().isConnected)
                assertTrue(core.session.requestStatusInvocations.isEmpty())

                store.findContactError = IllegalStateException("db closed")
                core.session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
                val second = async { rooms.joinRoom(core.radioId, contact, "pw") }
                remoteCoreAwait("second login never sent") { core.session.sendLoginInvocations.size == 2 }
                core.session.yieldEvent(MeshEvent.LoginSuccess(com.meshcoreone.android.core.protocol.event.LoginInfo(1u, false, roomKey.prefix(6))))
                assertTrue(second.await().isConnected)
                store.findContactError = null

                core.session.sendPathDiscoveryHandler = { RemoteCoreFakeSession.sentInfo() }
                core.session.requestStatusResult = Result.failure(MeshCoreException.DeviceError(1u))
                core.session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
                val third = async { rooms.joinRoom(core.radioId, contact, "pw") }
                remoteCoreAwait("third login never sent") { core.session.sendLoginInvocations.size == 3 }
                core.session.yieldEvent(MeshEvent.LoginSuccess(com.meshcoreone.android.core.protocol.event.LoginInfo(1u, false, roomKey.prefix(6))))
                remoteCoreAwait("discovery never sent") { core.session.sendPathDiscoveryInvocations.size == 2 }
                core.store.saveContact(roomContact(direct = true))
                pollOnce()
                assertTrue(third.await().isConnected)
                assertEquals(1, core.session.requestStatusInvocations.size)
            }
        },
        remoteCoreNative("joinRoom without a saved contact skips history sync entirely") {
            withRemoteAdminHarness {
                val contact = prepareJoin(direct = true, saveContact = false)
                val join = async { rooms.joinRoom(core.radioId, contact, "pw") }
                remoteAdminAnswerLogin(roomKey, 1u, admin = false)
                assertTrue(join.await().isConnected)
                assertTrue(core.session.requestStatusInvocations.isEmpty())
                assertTrue(core.session.sendPathDiscoveryInvocations.isEmpty())
            }
        },
        remoteCoreNative("a rejected login fails the join before storing the password or syncing") {
            withRemoteAdminHarness {
                val contact = prepareJoin(direct = true)
                val join = async { runCatching { rooms.joinRoom(core.radioId, contact, "bad") } }
                remoteCoreAwait("login never sent") { core.session.sendLoginInvocations.size == 1 }
                core.session.yieldEvent(MeshEvent.LoginFailed(roomKey.prefix(6)))
                assertIs<RemoteNodeError.LoginFailed>(join.await().exceptionOrNull())
                assertNull(core.passwords.stored(roomKey))
                assertTrue(core.session.requestStatusInvocations.isEmpty())
            }
        },
        remoteCoreNative("cancelling a join during the discovery wait propagates and stops polling (Swift would swallow it)") {
            withRemoteAdminHarness {
                val contact = prepareJoin(direct = false)
                core.session.sendPathDiscoveryHandler = { RemoteCoreFakeSession.sentInfo() }
                val join = async { rooms.joinRoom(core.radioId, contact, "pw") }
                remoteAdminAnswerLogin(roomKey, 1u, admin = false)
                remoteCoreAwait("path discovery never sent") { core.session.sendPathDiscoveryInvocations.size == 1 }
                pollOnce()
                remoteCoreSettle()
                assertEquals(1, core.clock.sleeperCount)
                join.cancel()
                assertFailsWith<CancellationException> { join.await() }
                assertEquals(0, core.clock.sleeperCount)
                core.store.saveContact(roomContact(direct = true))
                pollOnce()
                assertTrue(core.session.requestStatusInvocations.isEmpty())
                assertEquals("pw", core.passwords.stored(roomKey))
            }
        },
    )

    @TestFactory
    fun reconnectAndLeaveCases(): List<DynamicTest> = listOf(
        remoteCoreNative("reconnectRoom re-authenticates with the stored password and syncs history") {
            withRemoteAdminHarness {
                core.startMonitoring()
                core.store.saveContact(roomContact(direct = true))
                val key = core.addSession(remoteCoreSession(core.radioId, roomKey, RemoteNodeRole.ROOM_SERVER))
                core.passwords.storePassword("saved", roomKey)
                core.session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
                core.session.requestStatusResult = Result.success(remoteAdminStatus(roomKey.prefix(6)))
                val reconnect = async { rooms.reconnectRoom(key, pathLength = 3u) }
                remoteAdminAnswerLogin(roomKey, 1u, admin = false)
                val session = reconnect.await()
                assertEquals(key.id, session.id)
                assertTrue(session.isConnected)
                assertEquals(listOf("saved"), core.session.sendLoginInvocations.map { it.password })
                assertEquals(1, core.session.requestStatusInvocations.size)
            }
        },
        remoteCoreNative("reconnectRoom: missing session is sessionNotFound, a repeater is invalidResponse, no password is passwordNotFound") {
            withRemoteAdminHarness {
                assertFailsWith<RemoteNodeError.SessionNotFound> { rooms.reconnectRoom(EntityKey(core.radioId, UUID.randomUUID())) }
                val repeater = core.addSession(remoteCoreSession(core.radioId, remoteCoreKey(0xE2), RemoteNodeRole.REPEATER))
                assertFailsWith<RemoteNodeError.InvalidResponse> { rooms.reconnectRoom(repeater) }
                val room = core.addSession(remoteCoreSession(core.radioId, roomKey, RemoteNodeRole.ROOM_SERVER))
                assertFailsWith<RemoteNodeError.PasswordNotFound> { rooms.reconnectRoom(room) }
                assertTrue(core.session.sendLoginInvocations.isEmpty())
            }
        },
        remoteCoreNative("leaveRoom logs out, broadcasts disconnected and removes the session and its password") {
            withRemoteAdminHarness {
                val key = core.addSession(remoteCoreSession(core.radioId, roomKey, RemoteNodeRole.ROOM_SERVER, isConnected = true))
                core.passwords.storePassword("pw", roomKey)
                val event = core.service.events().let { stream -> async { stream.first() } }
                rooms.leaveRoom(key, roomKey)
                assertEquals(RemoteNodeEvent.SessionStateChanged(key, false), event.await())
                assertEquals(listOf(roomKey), core.session.sendLogoutInvocations)
                assertNull(core.store.session(key))
                assertNull(core.passwords.stored(roomKey))
                assertFailsWith<RemoteNodeError.SessionNotFound> { rooms.leaveRoom(key, roomKey) }
            }
        },
    )
}
