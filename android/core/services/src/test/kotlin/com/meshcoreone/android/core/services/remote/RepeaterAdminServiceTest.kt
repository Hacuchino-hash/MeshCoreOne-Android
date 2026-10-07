// AndroidOnly: WP-210 Native RepeaterAdminService cases (Swift has no unit tests for it): admin connect, neighbours, pass-throughs, permissions, handlers and cancellation.
package com.meshcoreone.android.core.services.remote

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.RemoteNodeRole
import com.meshcoreone.android.core.model.RoomPermissionLevel
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.event.ContactMessage
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.event.Neighbour
import com.meshcoreone.android.core.protocol.event.NeighboursResponse
import com.meshcoreone.android.core.protocol.event.OwnerInfoResponse
import com.meshcoreone.android.core.protocol.event.TelemetryResponse
import com.meshcoreone.android.core.protocol.model.ContactType
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RepeaterAdminServiceTest {
    private val publicKey = remoteCoreKey(0xA1)

    private fun neighbours(count: Int, from: Int, total: Long, tag: Bytes = Bytes.of(9, 9, 9, 9)) = NeighboursResponse(
        publicKey.prefix(6), tag, total,
        (from until from + count).map { Neighbour(Bytes(ByteArray(6) { _ -> it.toByte() }), it.toLong(), it / 4.0) },
    )

    private suspend fun RemoteAdminHarness.repeaterSession(
        permission: RoomPermissionLevel = RoomPermissionLevel.ADMIN, connected: Boolean = true, key: Bytes = publicKey,
    ): EntityKey = core.addSession(remoteCoreSession(core.radioId, key, RemoteNodeRole.REPEATER, permission, connected))

    @TestFactory
    fun connectionCases(): List<DynamicTest> = listOf(
        remoteCoreNative("connectAsAdmin creates the session, logs in, stores the password and returns the refreshed row") {
            withRemoteAdminHarness {
                core.startMonitoring()
                core.session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
                val contact = remoteCoreContact(core.radioId, publicKey, ContactType.REPEATER.rawValue)
                val announced = mutableListOf<Long>()
                val connect = async {
                    repeater.connectAsAdmin(core.radioId, contact, "secret", pathLength = 2u, onTimeoutKnown = { announced += it })
                }
                remoteAdminAnswerLogin(publicKey, 2u, admin = true)
                val session = connect.await()
                assertEquals(RemoteNodeRole.REPEATER, session.role)
                assertTrue(session.isConnected)
                assertEquals(RoomPermissionLevel.ADMIN, session.permissionLevel)
                assertEquals("secret", core.passwords.stored(publicKey))
                assertEquals(listOf("secret"), core.session.sendLoginInvocations.map { it.password })
                // 2 hops: 5 s direct + 2 x 10 s = 25 s, capped at the 20 s login maximum.
                assertEquals(listOf(20L), announced)
            }
        },
        remoteCoreNative("connectAsAdmin does not store the password when rememberPassword is false or the password is stored") {
            withRemoteAdminHarness {
                core.startMonitoring()
                val contact = remoteCoreContact(core.radioId, publicKey, ContactType.REPEATER.rawValue)
                core.session.setSendLoginResults(List(2) { Result.success(RemoteCoreFakeSession.sentInfo(1000u)) })
                val first = async { repeater.connectAsAdmin(core.radioId, contact, "typed", rememberPassword = false) }
                remoteAdminAnswerLogin(publicKey, 0u, admin = false)
                assertEquals(RoomPermissionLevel.GUEST, first.await().permissionLevel)
                assertNull(core.passwords.stored(publicKey))

                core.passwords.storePassword("saved", publicKey)
                val second = async { repeater.connectAsAdmin(core.radioId, contact, null) }
                remoteCoreAwait("second login never sent") { core.session.sendLoginInvocations.size == 2 }
                core.session.yieldEvent(MeshEvent.LoginSuccess(com.meshcoreone.android.core.protocol.event.LoginInfo(2u, true, publicKey.prefix(6))))
                second.await()
                assertEquals(listOf("typed", "saved"), core.session.sendLoginInvocations.map { it.password })
                assertEquals("saved", core.passwords.stored(publicKey))
            }
        },
        remoteCoreNative("connectAsAdmin propagates a failed login without storing the password") {
            withRemoteAdminHarness {
                core.startMonitoring()
                core.session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
                val contact = remoteCoreContact(core.radioId, publicKey, ContactType.REPEATER.rawValue)
                val connect = async { runCatching { repeater.connectAsAdmin(core.radioId, contact, "wrong") } }
                remoteCoreAwait("login never sent") { core.session.sendLoginInvocations.size == 1 }
                core.session.yieldEvent(MeshEvent.LoginFailed(publicKey.prefix(6)))
                val error = assertIs<RemoteNodeError.LoginFailed>(connect.await().exceptionOrNull())
                assertEquals("Login failed: authentication failed", error.message)
                assertNull(core.passwords.stored(publicKey))
            }
        },
        remoteCoreNative("connectAsAdmin rejects non-remote contacts before any login") {
            withRemoteAdminHarness {
                val chat = remoteCoreContact(core.radioId, publicKey, ContactType.CHAT.rawValue)
                assertFailsWith<RemoteNodeError.InvalidResponse> { repeater.connectAsAdmin(core.radioId, chat, "pw") }
                assertEquals(0, core.store.sessionCount)
                assertTrue(core.session.sendLoginInvocations.isEmpty())
                assertNull(core.passwords.stored(publicKey))
            }
        },
        remoteCoreNative("connectAsAdmin throws sessionNotFound when the row vanishes after login, after storing the password") {
            withRemoteAdminHarness {
                core.startMonitoring()
                core.session.setSendLoginResults(listOf(Result.success(RemoteCoreFakeSession.sentInfo(1000u))))
                val contact = remoteCoreContact(core.radioId, publicKey, ContactType.REPEATER.rawValue)
                val connect = async { runCatching { repeater.connectAsAdmin(core.radioId, contact, "pw") } }
                remoteCoreAwait("login never sent") { core.session.sendLoginInvocations.size == 1 }
                val created = core.store.fetchRemoteNodeSessions(core.radioId).single()
                store.vanishedSessions += EntityKey(core.radioId, created.id)
                core.session.yieldEvent(MeshEvent.LoginSuccess(com.meshcoreone.android.core.protocol.event.LoginInfo(2u, true, publicKey.prefix(6))))
                assertIs<RemoteNodeError.SessionNotFound>(connect.await().exceptionOrNull())
                assertEquals("pw", core.passwords.stored(publicKey))
            }
        },
        remoteCoreNative("disconnect sends logout, broadcasts disconnected, then deletes the row and password") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                core.passwords.storePassword("pw", publicKey)
                val event = core.service.events().let { stream -> async { stream.first() } }
                repeater.disconnect(key, publicKey)
                assertEquals(RemoteNodeEvent.SessionStateChanged(key, false), event.await())
                assertEquals(listOf(publicKey), core.session.sendLogoutInvocations)
                assertNull(core.store.session(key))
                assertNull(core.passwords.stored(publicKey))
            }
        },
        remoteCoreNative("disconnect of an unknown session throws sessionNotFound and sends nothing") {
            withRemoteAdminHarness {
                assertFailsWith<RemoteNodeError.SessionNotFound> {
                    repeater.disconnect(EntityKey(core.radioId, UUID.randomUUID()), publicKey)
                }
                assertTrue(core.session.sendLogoutInvocations.isEmpty())
            }
        },
    )

    @TestFactory
    fun neighbourCases(): List<DynamicTest> = listOf(
        remoteCoreNative("requestNeighbors sends the Swift defaults (20, 0, newestFirst, 6) and audits the request first") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                val response = neighbours(3, 0, 3)
                core.session.requestNeighboursHandler = { response }
                assertSame(response, repeater.requestNeighbors(key))
                assertEquals(
                    listOf(RemoteCoreFakeSession.RequestNeighboursInvocation(publicKey, 20u, 0u, 0u, 6u)),
                    core.session.requestNeighboursInvocations,
                )
                assertEquals(listOf("neighborsRequest ${publicKey.prefix(6).hexString} 20 0"), audit.entries)
            }
        },
        remoteCoreNative("requestNeighbors passes custom count, offset, sort order raw value and prefix length") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                core.session.requestNeighboursHandler = { neighbours(0, 0, 0) }
                repeater.requestNeighbors(key, 7u, 300u, NeighborSortOrder.STRONGEST_FIRST, 4u)
                repeater.requestNeighbors(key, orderBy = NeighborSortOrder.WEAKEST_FIRST)
                repeater.requestNeighbors(key, orderBy = NeighborSortOrder.OLDEST_FIRST)
                assertEquals(
                    listOf(
                        RemoteCoreFakeSession.RequestNeighboursInvocation(publicKey, 7u, 300u, 2u, 4u),
                        RemoteCoreFakeSession.RequestNeighboursInvocation(publicKey, 20u, 0u, 3u, 6u),
                        RemoteCoreFakeSession.RequestNeighboursInvocation(publicKey, 20u, 0u, 1u, 6u),
                    ),
                    core.session.requestNeighboursInvocations,
                )
                assertEquals("neighborsRequest ${publicKey.prefix(6).hexString} 7 300", audit.entries.first())
            }
        },
        remoteCoreNative("requestNeighbors requires an existing repeater session (rooms and missing rows are sessionNotFound)") {
            withRemoteAdminHarness {
                core.session.requestNeighboursHandler = { neighbours(0, 0, 0) }
                assertFailsWith<RemoteNodeError.SessionNotFound> {
                    repeater.requestNeighbors(EntityKey(core.radioId, UUID.randomUUID()))
                }
                val room = core.addSession(remoteCoreSession(core.radioId, remoteCoreKey(0xB2), RemoteNodeRole.ROOM_SERVER))
                assertFailsWith<RemoteNodeError.SessionNotFound> { repeater.requestNeighbors(room) }
                assertTrue(core.session.requestNeighboursInvocations.isEmpty())
                assertTrue(audit.entries.isEmpty())
            }
        },
        remoteCoreNative("requestNeighbors maps mesh timeout to timeout, other mesh errors to sessionError, and passes the rest through") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                core.session.requestNeighboursHandler = { throw MeshCoreException.Timeout() }
                assertFailsWith<RemoteNodeError.Timeout> { repeater.requestNeighbors(key) }
                val device = MeshCoreException.DeviceError(3u)
                core.session.requestNeighboursHandler = { throw device }
                assertSame(device, assertFailsWith<RemoteNodeError.SessionError> { repeater.requestNeighbors(key) }.error)
                val other = IllegalStateException("boom")
                core.session.requestNeighboursHandler = { throw other }
                assertSame(other, assertFailsWith<IllegalStateException> { repeater.requestNeighbors(key) })
            }
        },
        remoteCoreNative("requestNeighbors times out on the injected clock at the requested deadline") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                core.session.requestNeighboursHandler = { CompletableDeferred<NeighboursResponse>().await() }
                val request = async { runCatching { repeater.requestNeighbors(key, timeout = 3.seconds) } }
                remoteCoreAwait("request never sent") { core.session.requestNeighboursInvocations.size == 1 }
                remoteCoreSettle()
                core.clock.advance(2999.milliseconds)
                remoteCoreSettle()
                assertFalse(request.isCompleted)
                core.clock.advance(1.milliseconds)
                assertIs<RemoteNodeError.Timeout>(request.await().exceptionOrNull())
                assertEquals(0, core.clock.sleeperCount)
            }
        },
        remoteCoreNative("requestNeighbors defaults to the 45 s binary maximum") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                core.session.requestNeighboursHandler = { CompletableDeferred<NeighboursResponse>().await() }
                val request = async { runCatching { repeater.requestNeighbors(key) } }
                remoteCoreAwait("request never sent") { core.session.requestNeighboursInvocations.size == 1 }
                remoteCoreSettle()
                core.clock.advance(44.seconds)
                remoteCoreSettle()
                assertFalse(request.isCompleted)
                core.clock.advance(1.seconds)
                assertIs<RemoteNodeError.Timeout>(request.await().exceptionOrNull())
            }
        },
        remoteCoreNative("cancelling a neighbours request cancels the radio wait and its deadline") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                val radioWait = CompletableDeferred<Throwable?>()
                core.session.requestNeighboursHandler = {
                    try {
                        CompletableDeferred<NeighboursResponse>().await()
                    } catch (cancellation: CancellationException) {
                        radioWait.complete(cancellation)
                        throw cancellation
                    }
                }
                val request = async { repeater.requestNeighbors(key) }
                remoteCoreAwait("request never sent") { core.session.requestNeighboursInvocations.size == 1 }
                remoteCoreSettle()
                assertEquals(1, core.clock.sleeperCount)
                request.cancel()
                assertFailsWith<CancellationException> { request.await() }
                assertIs<CancellationException>(radioWait.await())
                assertEquals(0, core.clock.sleeperCount)
            }
        },
        remoteCoreNative("fetchAllNeighbors pages 255 at a time, waits 1 s between pages and merges under the first page's header") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                val pages = ArrayDeque(listOf(neighbours(120, 0, 300, Bytes.of(1, 1, 1, 1)), neighbours(120, 120, 300), neighbours(60, 240, 300)))
                core.session.requestNeighboursHandler = { pages.removeFirst() }
                val all = async { repeater.fetchAllNeighbors(key, NeighborSortOrder.STRONGEST_FIRST) }
                remoteCoreAwait("first page never requested") { core.session.requestNeighboursInvocations.size == 1 }
                remoteCoreSettle()
                core.clock.advance(999.milliseconds)
                remoteCoreSettle()
                assertEquals(1, core.session.requestNeighboursInvocations.size)
                core.clock.advance(1.milliseconds)
                remoteCoreAwait("second page never requested") { core.session.requestNeighboursInvocations.size == 2 }
                remoteCoreAdvanceUntil(core.clock, "third page never requested", step = 1.seconds) {
                    core.session.requestNeighboursInvocations.size == 3
                }
                val merged = all.await()
                assertEquals(300, merged.neighbours.size)
                assertEquals(300L, merged.totalCount)
                assertEquals(Bytes.of(1, 1, 1, 1), merged.tag)
                assertEquals((0 until 300).map { it.toLong() }, merged.neighbours.map { it.secondsAgo })
                assertEquals(
                    listOf<UShort>(0u, 120u, 240u),
                    core.session.requestNeighboursInvocations.map { it.offset },
                )
                assertTrue(core.session.requestNeighboursInvocations.all { it.count == 255.toUByte() && it.orderBy == 2.toUByte() && it.pubkeyPrefixLength == 6.toUByte() })
                assertEquals(
                    listOf("0", "120", "240").map { "neighborsRequest ${publicKey.prefix(6).hexString} 255 $it" },
                    audit.entries,
                )
            }
        },
        remoteCoreNative("fetchAllNeighbors stops at an empty page and returns the partial list") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                val pages = ArrayDeque(listOf(neighbours(10, 0, 50), neighbours(0, 10, 50)))
                core.session.requestNeighboursHandler = { pages.removeFirst() }
                val all = async { repeater.fetchAllNeighbors(key) }
                remoteCoreAdvanceUntil(core.clock, "fetch never finished", step = 1.seconds) { all.isCompleted }
                val partial = all.await()
                assertEquals(10, partial.neighbours.size)
                assertEquals(50L, partial.totalCount)
                assertEquals(2, core.session.requestNeighboursInvocations.size)
            }
        },
        remoteCoreNative("fetchAllNeighbors propagates a later page's timeout") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                val pages = ArrayDeque<suspend () -> NeighboursResponse>(
                    listOf({ neighbours(10, 0, 50) }, { throw MeshCoreException.Timeout() }),
                )
                core.session.requestNeighboursHandler = { pages.removeFirst()() }
                val all = async { runCatching { repeater.fetchAllNeighbors(key) } }
                remoteCoreAdvanceUntil(core.clock, "fetch never finished", step = 1.seconds) { all.isCompleted }
                assertIs<RemoteNodeError.Timeout>(all.await().exceptionOrNull())
            }
        },
        remoteCoreNative("NeighborSortOrder raw values match the firmware order byte") {
            assertEquals(listOf<UByte>(0u, 1u, 2u, 3u), NeighborSortOrder.entries.map { it.rawValue })
            assertEquals(NeighborSortOrder.WEAKEST_FIRST, NeighborSortOrder.fromRawValue(3u))
            assertNull(NeighborSortOrder.fromRawValue(4u))
            assertEquals(6.toUByte(), RepeaterAdminService.DEFAULT_PUBKEY_PREFIX_LENGTH)
        },
    )

    @TestFactory
    fun passThroughCases(): List<DynamicTest> = listOf(
        remoteCoreNative("status, telemetry and owner info delegate to RemoteNodeService with the repeater layout") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                val status = remoteAdminStatus(publicKey.prefix(6))
                core.session.requestStatusResult = Result.success(status)
                assertSame(status, repeater.requestStatus(key))
                assertEquals(listOf(publicKey to ContactType.REPEATER), core.session.requestStatusInvocations)
                val telemetry = TelemetryResponse(publicKey.prefix(6), null, Bytes.of(1, 0x67, 0, 0xFA))
                core.session.requestTelemetryResult = Result.success(telemetry)
                assertSame(telemetry, repeater.requestTelemetry(key))
                val owner = OwnerInfoResponse("v1.9", "Hilltop", "ops@example")
                core.session.requestOwnerInfoResult = Result.success(owner)
                assertSame(owner, repeater.requestOwnerInfo(key))
                core.session.requestOwnerInfoResult = Result.failure(MeshCoreException.Timeout())
                assertFailsWith<RemoteNodeError.Timeout> { repeater.requestOwnerInfo(key) }
                assertFailsWith<RemoteNodeError.SessionNotFound> { repeater.requestStatus(EntityKey(core.radioId, UUID.randomUUID())) }
            }
        },
        remoteCoreNative("sendCommand is admin-only and returns the correlated reply body") {
            withRemoteAdminHarness {
                core.startMonitoring()
                val guest = repeaterSession(RoomPermissionLevel.GUEST, key = remoteCoreKey(0xA2))
                assertFailsWith<RemoteNodeError.PermissionDenied> { repeater.sendCommand(guest, "ver") }
                assertFailsWith<RemoteNodeError.PermissionDenied> { repeater.sendRawCommand(guest, "ver") }
                val key = repeaterSession()
                val reply = async { repeater.sendCommand(key, "get name") }
                remoteCoreAwait("command never sent") { core.session.sendCommandInvocations.size == 1 }
                assertEquals("00|get name", core.session.sendCommandInvocations.single().command)
                core.yieldReply("00|> Hilltop", publicKey)
                assertEquals("> Hilltop", reply.await())
                val raw = async { repeater.sendRawCommand(key, "neighbors") }
                remoteCoreAwait("raw command never sent") { core.session.sendCommandInvocations.size == 2 }
                assertEquals("01|neighbors", core.session.sendCommandInvocations[1].command)
                core.yieldReply("anything at all", publicKey)
                assertEquals("anything at all", raw.await())
            }
        },
        remoteCoreNative("sendCommand times out after the requested CLI timeout") {
            withRemoteAdminHarness {
                core.startMonitoring()
                val key = repeaterSession()
                val reply = async { runCatching { repeater.sendCommand(key, "get name", 2.seconds) } }
                remoteCoreAdvanceUntil(core.clock, "command never timed out", step = 500.milliseconds) { reply.isCompleted }
                assertIs<RemoteNodeError.Timeout>(reply.await().exceptionOrNull())
            }
        },
    )

    @TestFactory
    fun sessionQueryCases(): List<DynamicTest> = listOf(
        remoteCoreNative("fetchRepeaterSessions keeps only this radio's repeaters") {
            withRemoteAdminHarness {
                val a = repeaterSession()
                repeaterSession(key = remoteCoreKey(0xA3))
                core.addSession(remoteCoreSession(core.radioId, remoteCoreKey(0xA4), RemoteNodeRole.ROOM_SERVER))
                core.addSession(remoteCoreSession(RadioId(UUID.randomUUID()), remoteCoreKey(0xA5), RemoteNodeRole.REPEATER))
                val sessions = repeater.fetchRepeaterSessions(core.radioId)
                assertEquals(2, sessions.size)
                assertTrue(sessions.all { it.isRepeater && it.radioId == core.radioId })
                assertTrue(sessions.any { it.id == a.id })
            }
        },
        remoteCoreNative("getConnectedSession requires a connected repeater matching the 6-byte prefix on the radio") {
            withRemoteAdminHarness {
                val key = repeaterSession()
                assertEquals(key.id, repeater.getConnectedSession(core.radioId, publicKey.prefix(6))?.id)
                assertNull(repeater.getConnectedSession(RadioId(UUID.randomUUID()), publicKey.prefix(6)))
                assertNull(repeater.getConnectedSession(core.radioId, remoteCoreKey(0x00).prefix(6)))
                val offline = repeaterSession(connected = false, key = remoteCoreKey(0xA6))
                assertNull(repeater.getConnectedSession(core.radioId, remoteCoreKey(0xA6).prefix(6)))
                assertEquals(offline.id, store.fetchRemoteNodeSessionByPrefix(core.radioId, remoteCoreKey(0xA6).prefix(6))?.id)
                core.addSession(remoteCoreSession(core.radioId, remoteCoreKey(0xA7), RemoteNodeRole.ROOM_SERVER, isConnected = true))
                assertNull(repeater.getConnectedSession(core.radioId, remoteCoreKey(0xA7).prefix(6)))
            }
        },
    )

    @TestFactory
    fun handlerCases(): List<DynamicTest> = listOf(
        remoteCoreNative("each invoke audits the response first, then delivers it to the registered handler") {
            withRemoteAdminHarness {
                val seen = mutableListOf<String>()
                repeater.setStatusHandler { seen += "status ${audit.entries.size}" }
                repeater.setNeighboursHandler { seen += "neighbours ${it.neighbours.size} ${audit.entries.size}" }
                repeater.setTelemetryHandler { seen += "telemetry ${it.dataPoints.size} ${audit.entries.size}" }
                repeater.setCLIHandler { message, contact -> seen += "cli ${message.text} ${contact.name} ${audit.entries.size}" }
                val prefix = publicKey.prefix(6)
                repeater.invokeStatusHandler(remoteAdminStatus(prefix, battery = 70_000, uptime = 99u))
                repeater.invokeNeighboursHandler(neighbours(2, 0, 9))
                repeater.invokeTelemetryHandler(TelemetryResponse(prefix, null, Bytes.of(1, 0x67, 0x00, 0xFA)))
                val contact = remoteCoreContact(core.radioId, publicKey, ContactType.REPEATER.rawValue)
                val message = ContactMessage(prefix, 0u, 1u, core.clock.wallClock.instant(), null, "> ok", null)
                repeater.invokeCLIHandler(message, contact)
                assertEquals(listOf("status 1", "neighbours 2 2", "telemetry 1 3", "cli > ok TestContact 4"), seen)
                assertEquals(
                    listOf(
                        "statusResponse REPEATER ${prefix.hexString} 65535 99",
                        "neighborsResponse ${prefix.hexString} 9 2",
                        "telemetryResponse REPEATER ${prefix.hexString} 1",
                        "cliResponse ${publicKey.hexString} > ok",
                    ),
                    audit.entries,
                )
            }
        },
        remoteCoreNative("without handlers invokes still audit; clearStatusHandlers keeps only the CLI handler") {
            withRemoteAdminHarness {
                val prefix = publicKey.prefix(6)
                val contact = remoteCoreContact(core.radioId, publicKey, ContactType.REPEATER.rawValue)
                val message = ContactMessage(prefix, 0u, 1u, core.clock.wallClock.instant(), null, "x", null)
                repeater.invokeStatusHandler(remoteAdminStatus(prefix))
                repeater.invokeCLIHandler(message, contact)
                assertEquals(2, audit.entries.size)

                var calls = 0
                repeater.setStatusHandler { calls += 1 }
                repeater.setNeighboursHandler { calls += 1 }
                repeater.setTelemetryHandler { calls += 1 }
                repeater.setCLIHandler { _, _ -> calls += 100 }
                repeater.clearStatusHandlers()
                assertNull(repeater.statusResponseHandler)
                assertNull(repeater.neighboursResponseHandler)
                assertNull(repeater.telemetryResponseHandler)
                repeater.invokeStatusHandler(remoteAdminStatus(prefix))
                repeater.invokeNeighboursHandler(neighbours(1, 0, 1))
                repeater.invokeTelemetryHandler(TelemetryResponse(prefix, null, Bytes.EMPTY))
                repeater.invokeCLIHandler(message, contact)
                assertEquals(100, calls)
                repeater.clearHandlers()
                assertNull(repeater.cliResponseHandler)
                repeater.invokeCLIHandler(message, contact)
                assertEquals(100, calls)
            }
        },
    )
}
