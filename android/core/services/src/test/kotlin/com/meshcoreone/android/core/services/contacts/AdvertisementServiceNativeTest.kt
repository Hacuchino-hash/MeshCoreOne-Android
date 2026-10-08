// AndroidOnly: WP-209 native cancellation, concurrency and boundary regressions for the coroutine port of AdvertisementService.
package com.meshcoreone.android.core.services.contacts

import com.meshcoreone.android.core.model.VContactIdentity
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.config.MeshCoreException
import com.meshcoreone.android.core.protocol.event.MeshEvent
import com.meshcoreone.android.core.protocol.event.ParsedRxLogData
import com.meshcoreone.android.core.protocol.event.PayloadType
import com.meshcoreone.android.core.protocol.event.RouteType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.yield
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class AdvertisementServiceNativeTest {
    @TestFactory
    fun nativeCases(): List<DynamicTest> = listOf(
        advertNativeCase("stop cancels a parked debounce round without invoking the handler") {
            val store = makeStore()
            val clock = AdvertManualClock()
            val service = makeService(store, debounce = 5.seconds, clock = clock)
            val recorder = AdvertHandlerRecorder(store, radioId)
            installHandler(service, recorder)
            val key = advertPublicKey(0x11)
            store.saveContact(radioId, advertContactFrame(key))
            startMonitoring(service)
            session.yieldEvent(MeshEvent.Advertisement(key))
            eventually("round parked on debounce") { clock.sleeperDeadlines.isNotEmpty() }
            val round = assertNotNull(service.deltaSyncTask)

            service.stopEventMonitoring()
            round.join()
            assertTrue(round.isCancelled)
            assertTrue(clock.sleeperDeadlines.isEmpty(), "cancelled sleeper must unregister")
            clock.advance(10.seconds)
            assertNull(service.deltaSyncTask)
            assertEquals(0, recorder.callCount)
        },

        advertNativeCase("concurrent adverts on the shared pool coalesce without losing keys or overlapping handlers", multiThreaded = true) {
            val store = makeStore()
            val service = makeService(store)
            val keys = (0 until 64).map { advertPublicKey(it * 3) }
            keys.forEach { store.saveContact(radioId, advertContactFrame(it)) }
            val active = AtomicInteger()
            val maxActive = AtomicInteger()
            val calls = AtomicInteger()
            service.setDeltaSyncHandler {
                maxActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                calls.incrementAndGet()
                yield()
                active.decrementAndGet()
                AdvertContactSyncOutcome.SYNCED
            }
            startMonitoring(service)
            keys.forEach { session.yieldEvent(MeshEvent.Advertisement(it)) }
            eventually("every advert handled") { session.handledEvents >= keys.size }
            eventually("drained") { service.pendingAdvertKeys.isEmpty() && service.deltaSyncTask == null }
            assertEquals(1, maxActive.get(), "delta-sync rounds must never overlap")
            assertTrue(calls.get() in 1..keys.size)
            assertTrue(keys.all { (store.fetchContact(radioId, it)?.lastHeardTimestamp ?: 0u) > 0u }, "every advert was touched")
            service.stopEventMonitoring()
        },

        advertNativeCase("handler cancelled by teardown still rolls back and stamps") {
            val store = makeStore()
            val service = makeService(store)
            val ghostKey = advertPublicKey(0x21)
            val heardKey = advertPublicKey(0x22)
            val hold = AdvertHandlerHold()
            val committed = AdvertCommitMarker()
            service.setDeltaSyncHandler {
                hold.waitUntilReleased()
                // Commit both rows (heardKey starts with lastHeard 0), then block cancellably.
                store.saveContact(radioId, advertContactFrame(heardKey, name = "Heard"))
                store.saveContact(radioId, advertContactFrame(ghostKey, name = "Ghost"))
                committed.markCommitted()
                awaitCancellation()
            }
            startMonitoring(service)
            session.yieldEvent(MeshEvent.Advertisement(heardKey))
            session.yieldEvent(MeshEvent.Advertisement(ghostKey))
            eventually("handler waiting") { hold.isWaiting }
            // 0x8F before the commit: no row yet, so only the rollback tombstone records it.
            session.yieldEvent(MeshEvent.ContactDeleted(ghostKey))
            awaitHandled(3)
            hold.release()
            eventually("committed") { committed.committed && store.fetchContact(radioId, ghostKey) != null }
            val round = assertNotNull(service.deltaSyncTask)

            service.stopEventMonitoring()
            round.join()
            assertNull(store.fetchContact(radioId, ghostKey), "rollback must run although the handler threw CancellationException")
            assertTrue((store.fetchContact(radioId, heardKey)?.lastHeardTimestamp ?: 0u) > 0u, "cancel path stamps drained keys")
        },

        advertNativeCase("handler throwing a non-cancellation exception counts as a failed round") {
            val store = makeStore()
            val service = makeService(store)
            val key = advertPublicKey(0x31)
            store.saveContact(radioId, advertContactFrame(key))
            val calls = AtomicInteger()
            service.setDeltaSyncHandler {
                if (calls.incrementAndGet() == 1) throw IllegalStateException("radio exchange broke")
                AdvertContactSyncOutcome.SYNCED
            }
            startMonitoring(service)
            session.yieldEvent(MeshEvent.Advertisement(key))
            eventually("retried after the throw") { calls.get() >= 2 }
            awaitIdle(service)
            assertEquals(2, calls.get())
            assertEquals(0, service.consecutiveDeltaSyncFailures)
            assertTrue(service.pendingAdvertKeys.isEmpty())
            service.stopEventMonitoring()
        },

        advertNativeCase("handler cancellation raised outside the round counts as a failed round and requeues") {
            val store = makeStore()
            val service = makeService(store)
            val key = advertPublicKey(0x32)
            store.saveContact(radioId, advertContactFrame(key))
            val calls = AtomicInteger()
            service.setDeltaSyncHandler {
                // A handler-internal timeout or a foreign Job's cancellation, while the round itself is live.
                if (calls.incrementAndGet() == 1) throw CancellationException("handler-internal timeout")
                AdvertContactSyncOutcome.SYNCED
            }
            startMonitoring(service)
            session.yieldEvent(MeshEvent.Advertisement(key))
            eventually("drained keys were requeued and retried") { calls.get() >= 2 }
            awaitIdle(service)
            assertEquals(2, calls.get())
            assertEquals(0, service.consecutiveDeltaSyncFailures)
            assertTrue(service.pendingAdvertKeys.isEmpty())
            service.stopEventMonitoring()
        },

        advertNativeCase("events registers before collection and finishEvents ends every subscriber") {
            val service = makeService(makeStore())
            val early = service.events()
            service.eventBroadcaster.yield(AdvertisementEvent.ContactUpdated)
            service.eventBroadcaster.yield(AdvertisementEvent.NodeStorageFullChanged(true))
            val collected = async { early.toList() }
            yield()
            service.finishEvents()
            assertEquals(
                listOf(AdvertisementEvent.ContactUpdated, AdvertisementEvent.NodeStorageFullChanged(true)),
                collected.await(),
            )
            assertTrue(service.events().toList().isEmpty(), "a subscription after finishEvents completes empty")
        },

        advertNativeCase("stopEventMonitoring completes teardown when its caller is cancelled") {
            val store = makeMockStore()
            val scene = AdvertFakeAppState(isInForeground = false)
            val service = makeService(store, appStateProvider = scene)
            val key = advertPublicKey(0x41)
            store.saveContact(radioId, advertContactFrame(key))
            startMonitoring(service)
            session.yieldEvent(MeshEvent.ContactDeleted(key))
            eventually("queued") { key in service.pendingDeletedKeys }
            store.holdNextDeleteContacts()
            scene.setIsInForeground(true)
            val flushed = async { service.handleReturnToForeground() }
            eventually("held") { store.isDeleteContactsHeld }

            val stopping = async { service.stopEventMonitoring() }
            // No handler is installed here, so only the monitor teardown proves stop is running.
            eventually("stop started") { session.eventSubscriptionCount == 0 }
            stopping.cancel()
            store.releaseDeleteContacts()
            flushed.await()
            stopping.join()
            eventually("teardown finished despite caller cancellation") { service.currentRadioId == null }
            assertNull(store.fetchContact(radioId, key))
        },

        advertNativeCase("self advertisement wraps MeshCoreException and passes other failures through") {
            val service = makeService(makeStore())
            val timeout = MeshCoreException.Timeout()
            session.advertisementFailure = timeout
            val wrapped = assertFailsWith<AdvertisementError.SessionError> { service.sendSelfAdvertisement(flood = true) }
            assertSame(timeout, wrapped.error)
            session.advertisementFailure = IllegalStateException("not a session error")
            assertFailsWith<IllegalStateException> { service.sendSelfAdvertisement(flood = false) }
            session.advertisementFailure = null
            service.sendSelfAdvertisement(flood = false)
        },

        advertNativeCase("trace rx log yields traceSnrObserved with remote SNR from the last path node") {
            val service = makeService(makeStore())
            val listener = listen(service)
            startMonitoring(service)
            val payload = Bytes.of(0x78, 0x56, 0x34, 0x12, 0x00)
            val log = ParsedRxLogData(
                snr = 7.5, rssi = -90, rawPayload = payload, routeType = RouteType.DIRECT, payloadType = PayloadType.TRACE,
                payloadVersion = 0u, payloadTypeBits = PayloadType.TRACE.rawValue, transportCode = null, pathLength = 2u,
                pathNodes = listOf<UByte>(0x10u, 0xF8u), packetPayload = payload,
            )
            session.yieldEvent(MeshEvent.RxLogData(log))
            awaitHandled(1)
            service.stopEventMonitoring()
            service.finishEvents()
            listener.join()
            val observed = listener.counter.traceObservations.single()
            assertEquals(0x12345678u, observed.tag)
            assertEquals(7.5, observed.localSnr)
            assertEquals(-2.0, observed.remoteSnr)
        },

        advertNativeCase("V-contact 0x8F preserves the local row and storage-full state") {
            val store = makeStore()
            val service = makeService(store)
            val device = assertNotNull(store.fetchDevice(radioId))
            val vKey = assertNotNull(VContactIdentity.publicKey(device.publicKey))
            store.saveContact(radioId, advertContactFrame(vKey, name = "V"))
            val listener = listen(service)
            startMonitoring(service)
            session.yieldEvent(MeshEvent.ContactDeleted(vKey))
            awaitHandled(1)
            listener.sync()
            assertNotNull(store.fetchContact(radioId, vKey))
            assertFalse(vKey in service.pendingDeletedKeys)
            assertFalse(vKey in service.contactsDeletedDuringSync)
            assertEquals(0, listener.counter.nodeStorageFullChangedCount)
            service.stopEventMonitoring()
            service.finishEvents()
            listener.join()
        },

        advertNativeCase("restarting monitoring replaces the previous subscription") {
            val service = makeService(makeStore())
            startMonitoring(service)
            service.startEventMonitoring(radioId)
            eventually("single live subscription") { session.eventSubscriptionCount == 1 }
            service.stopEventMonitoring()
            eventually("no subscription after stop") { session.eventSubscriptionCount == 0 }
            assertNull(service.currentRadioId)
        },
    )
}
