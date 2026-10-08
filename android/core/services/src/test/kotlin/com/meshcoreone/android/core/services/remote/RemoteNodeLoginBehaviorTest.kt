// AndroidOnly: WP-210 Native login, keep-alive and reconnection behavior plus cancellation/ownership cases the Swift suites leave to the actor runtime.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.event.LoginInfo
import com.meshcoreone.android.core.protocol.event.MeshEvent
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RemoteNodeLoginBehaviorTest {
    private val publicKey = remoteCoreKey(0xCC)
    private val otherKey = remoteCoreKey(0xDD)

    private fun loginSuccess(key: Bytes, permissions: UByte, admin: Boolean) =
        MeshEvent.LoginSuccess(LoginInfo(permissions, admin, key.prefix(6)))

    private fun RemoteCoreHarness.saveDirectContact(key: Bytes = publicKey) =
        store.saveContact(remoteCoreContact(radioId, key, outPathLength = 1u, outPath = Bytes.of(0x0A)))

    @TestFactory
    fun loginCases(): List<DynamicTest> = listOf(
        remoteCoreNative("login resolves on loginSuccess, persists the permission and broadcasts connected") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey))
                startMonitoring()
                session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
                val firstEvent = service.events().let { stream -> async { stream.first() } }
                val login = async { service.login(key, "pw") }
                remoteCoreAwait("login never sent") { session.sendLoginInvocations.size == 1 }
                assertEquals("pw", session.sendLoginInvocations.single().password)

                session.yieldEvent(loginSuccess(publicKey, 2u, admin = true))
                val result = login.await()
                assertTrue(result.success)
                assertEquals(RoomPermissionLevel.ADMIN, result.permissionLevel)
                assertEquals(RemoteNodeEvent.SessionStateChanged(key, true), firstEvent.await())
                assertEquals(true, store.session(key)?.isConnected)
                assertEquals(RoomPermissionLevel.ADMIN, store.session(key)?.permissionLevel)
                assertEquals(0, service.pendingLoginCount)
                assertEquals(RemoteNodeService.DEFAULT_KEEP_ALIVE_INTERVAL, synchronized(service.lock) { service.keepAliveIntervals[key] })
            }
        },
        remoteCoreNative("login retransmits at the firmware interval and times out at the policy deadline") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey))
                // 4000 ms suggestion: timeout = min(max(8 s, 5 s), 20 s) = 8 s; retransmit every max(1 s, 4 s).
                session.setSendLoginResults(List(2) { Result.success(RemoteCoreFakeSession.sentInfo(4000u)) })
                val known = mutableListOf<Long>()
                val login = async { runCatching { service.login(key, "pw", 0u) { known += it } } }
                remoteCoreAwait("login never sent") { session.sendLoginInvocations.size == 1 }
                remoteCoreSettle()
                assertEquals(listOf(8L), known)

                clock.advance(4.seconds)
                remoteCoreAwait("no retransmit after one interval") { session.sendLoginInvocations.size == 2 }
                assertFalse(login.isCompleted)
                clock.advance(4.seconds)
                assertIs<RemoteNodeError.Timeout>(login.await().exceptionOrNull())
                assertEquals(2, session.sendLoginInvocations.size)
                assertEquals(0, service.pendingLoginCount)
            }
        },
        remoteCoreNative("loginFailed push fails the caller with loginFailed") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey))
                startMonitoring()
                session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
                val login = async { runCatching { service.login(key, "bad") } }
                remoteCoreAwait("login never sent") { session.sendLoginInvocations.size == 1 }
                session.yieldEvent(MeshEvent.LoginFailed(publicKey.prefix(6)))
                val error = assertIs<RemoteNodeError.LoginFailed>(login.await().exceptionOrNull())
                assertEquals("Login failed: authentication failed", error.message)
                assertEquals(false, store.session(key)?.isConnected)
            }
        },
        remoteCoreNative("an overlapping login for the same prefix cancels the first caller; the second resolves") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey))
                startMonitoring()
                session.setSendLoginResults(List(2) { Result.success(RemoteCoreFakeSession.sentInfo(1000u)) })
                val first = async { runCatching { service.login(key, "pw") } }
                remoteCoreAwait("first login never sent") { session.sendLoginInvocations.size == 1 }
                val second = async { service.login(key, "pw") }
                assertIs<RemoteNodeError.Cancelled>(first.await().exceptionOrNull())
                remoteCoreAwait("second login never sent") { session.sendLoginInvocations.size == 2 }
                session.yieldEvent(loginSuccess(publicKey, 1u, admin = false))
                assertEquals(RoomPermissionLevel.READ_WRITE, second.await().permissionLevel)
            }
        },
        remoteCoreNative("cancelling the login caller withdraws its registration; a late result is ignored") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey))
                startMonitoring()
                session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
                val login = async { service.login(key, "pw") }
                remoteCoreAwait("login never sent") { session.sendLoginInvocations.size == 1 }
                login.cancelAndJoin()
                assertEquals(0, service.pendingLoginCount)
                session.yieldEvent(loginSuccess(publicKey, 2u, admin = true))
                remoteCoreSettle()
                assertEquals(false, store.session(key)?.isConnected)
            }
        },
        remoteCoreNative("login uses the stored password and reports passwordNotFound without one") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey))
                assertFailsWith<RemoteNodeError.PasswordNotFound> { service.login(key) }
                assertFailsWith<RemoteNodeError.SessionNotFound> { service.login(EntityKey(radioId, java.util.UUID.randomUUID()), "pw") }
                passwords.storePassword("secret", publicKey)
                session.setSendLoginResults(listOf(Result.failure(MeshCoreException.NotConnected())))
                val error = assertFailsWith<RemoteNodeError.SessionError> { service.login(key) }
                assertIs<MeshCoreException.NotConnected>(error.error)
                assertEquals("secret", session.sendLoginInvocations.single().password)
            }
        },
        remoteCoreNative("close fails a parked login with cancelled") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey))
                session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
                val login = async { runCatching { service.login(key, "pw") } }
                remoteCoreAwait("login never sent") { session.sendLoginInvocations.size == 1 }
                service.close()
                assertIs<RemoteNodeError.Cancelled>(login.await().exceptionOrNull())
                assertEquals(0, service.pendingLoginCount)
            }
        },
    )

    @TestFactory
    fun keepAliveCases(): List<DynamicTest> = listOf(
        remoteCoreNative("keep-alive sends at once with sync_since, then once per interval until stopped") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey, lastSyncTimestamp = 42u))
                saveDirectContact()
                session.sendKeepAliveResult = Result.success(RemoteCoreFakeSession.sentInfo())
                service.startSessionKeepAlive(key, publicKey)
                remoteCoreAwait("no immediate keep-alive") { session.sendKeepAliveInvocations.size == 1 }
                assertEquals(42u, session.sendKeepAliveInvocations.single().syncSince)

                clock.advance(89.seconds)
                remoteCoreSettle()
                assertEquals(1, session.sendKeepAliveInvocations.size)
                clock.advance(1.seconds)
                remoteCoreAwait("no keep-alive after one interval") { session.sendKeepAliveInvocations.size == 2 }

                service.stopSessionKeepAlive(key)
                remoteCoreSettle()
                clock.advance(RemoteNodeService.DEFAULT_KEEP_ALIVE_INTERVAL)
                remoteCoreSettle()
                assertEquals(2, session.sendKeepAliveInvocations.size)
                assertEquals(0, clock.sleeperCount)
            }
        },
        remoteCoreNative("two consecutive transient keep-alive failures disconnect the session and end the loop") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey, isConnected = true))
                saveDirectContact()
                session.sendKeepAliveResult = Result.failure(MeshCoreException.Timeout())
                val event = service.events().let { stream -> async { stream.first() } }
                service.startSessionKeepAlive(key, publicKey)
                remoteCoreAwait("no first keep-alive") { session.sendKeepAliveInvocations.size == 1 }
                remoteCoreSettle()
                assertEquals(true, store.session(key)?.isConnected)

                clock.advance(RemoteNodeService.DEFAULT_KEEP_ALIVE_INTERVAL)
                assertEquals(RemoteNodeEvent.SessionStateChanged(key, false), event.await())
                assertEquals(false, store.session(key)?.isConnected)
                remoteCoreSettle()
                assertEquals(0, clock.sleeperCount)
                assertEquals(2, session.sendKeepAliveInvocations.size)
            }
        },
        remoteCoreNative("a missing contact disconnects at once without sending") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey, isConnected = true))
                val event = service.events().let { stream -> async { stream.first() } }
                service.startSessionKeepAlive(key, publicKey)
                assertEquals(RemoteNodeEvent.SessionStateChanged(key, false), event.await())
                assertTrue(session.sendKeepAliveInvocations.isEmpty())
            }
        },
    )

    @TestFactory
    fun reconnectionCases(): List<DynamicTest> = listOf(
        remoteCoreNative("link loss marks connected sessions disconnected, stops keep-alives and returns their keys") {
            withRemoteCoreHarness {
                val first = addSession(remoteCoreSession(radioId, publicKey, isConnected = true))
                val second = addSession(remoteCoreSession(radioId, otherKey, isConnected = true))
                addSession(remoteCoreSession(radioId, remoteCoreKey(0xEE)))
                saveDirectContact()
                session.sendKeepAliveResult = Result.success(RemoteCoreFakeSession.sentInfo())
                service.startSessionKeepAlive(first, publicKey)
                remoteCoreAwait("keep-alive never parked") { clock.sleeperCount == 1 }
                val events = service.events().let { stream -> async { stream.take(2).toList() } }

                assertEquals(setOf(first, second), service.handleBLEDisconnection(radioId))
                assertEquals(setOf(first, second), events.await().map { (it as RemoteNodeEvent.SessionStateChanged).session }.toSet())
                assertFalse(store.session(first)?.isConnected ?: true)
                assertFalse(store.session(second)?.isConnected ?: true)
                remoteCoreSettle()
                assertEquals(0, clock.sleeperCount)
            }
        },
        remoteCoreNative("reconnection re-authenticates each session and disconnects a downgraded one") {
            withRemoteCoreHarness {
                val admin = addSession(remoteCoreSession(radioId, publicKey, permissionLevel = RoomPermissionLevel.ADMIN))
                val member = addSession(remoteCoreSession(radioId, otherKey, permissionLevel = RoomPermissionLevel.READ_WRITE))
                passwords.storePassword("a", publicKey)
                passwords.storePassword("b", otherKey)
                startMonitoring()
                session.setSendLoginResults(List(2) { Result.success(RemoteCoreFakeSession.sentInfo(1000u)) })
                val reauth = async { service.handleBLEReconnection(setOf(admin, member)) }
                remoteCoreAwait("re-auth logins never sent") { session.sendLoginInvocations.size == 2 }
                // A concurrent re-auth is skipped while one is in progress.
                service.handleBLEReconnection(setOf(admin))
                assertEquals(2, session.sendLoginInvocations.size)

                session.yieldEvent(loginSuccess(publicKey, 1u, admin = false))
                session.yieldEvent(loginSuccess(otherKey, 1u, admin = false))
                reauth.await()
                assertEquals(false, store.session(admin)?.isConnected)
                assertEquals(true, store.session(member)?.isConnected)
                assertFalse(synchronized(service.lock) { service.isReauthenticating })
            }
        },
        remoteCoreNative("a failed re-auth marks the session disconnected") {
            withRemoteCoreHarness {
                val key = addSession(remoteCoreSession(radioId, publicKey, isConnected = true))
                session.setSendLoginResults(listOf(Result.failure(MeshCoreException.NotConnected())))
                passwords.storePassword("a", publicKey)
                val event = service.events().let { stream -> async { stream.first() } }
                service.handleBLEReconnection(setOf(key))
                assertEquals(RemoteNodeEvent.SessionStateChanged(key, false), event.await())
                assertEquals(false, store.session(key)?.isConnected)
            }
        },
    )
}
