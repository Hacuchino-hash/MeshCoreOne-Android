// AndroidOnly: WP-303 Native tests: the Bluetooth RuntimeLink over BleTransport (events, bond refresh, registration, factory).
package com.meshcoreone.android.app.container

import com.meshcoreone.android.core.ble.BleConnectMode
import com.meshcoreone.android.core.ble.BleDeviceHandle
import com.meshcoreone.android.core.ble.BleError
import com.meshcoreone.android.core.ble.BleLinkDiagnostics
import com.meshcoreone.android.core.ble.BlePhase
import com.meshcoreone.android.core.ble.BleTransport
import com.meshcoreone.android.core.ble.BleTransportException
import com.meshcoreone.android.core.ble.BluetoothAvailability
import com.meshcoreone.android.core.ble.BondState
import com.meshcoreone.android.core.ble.GattCharacteristic
import com.meshcoreone.android.core.ble.GattConnection
import com.meshcoreone.android.core.ble.GattDescriptor
import com.meshcoreone.android.core.ble.GattEvents
import com.meshcoreone.android.core.ble.GattFacade
import com.meshcoreone.android.core.ble.GattOperation
import com.meshcoreone.android.core.ble.GattProperty
import com.meshcoreone.android.core.ble.GattReply
import com.meshcoreone.android.core.ble.GattService
import com.meshcoreone.android.core.ble.NusUuid
import com.meshcoreone.android.core.contracts.domain.BluetoothAddress
import com.meshcoreone.android.core.contracts.domain.BluetoothPairingHandle
import com.meshcoreone.android.core.contracts.domain.ConnectionTarget
import com.meshcoreone.android.core.contracts.domain.Generation
import com.meshcoreone.android.core.contracts.domain.ProcessEpoch
import com.meshcoreone.android.core.contracts.domain.SessionToken
import com.meshcoreone.android.core.connectivity.ble.BleLinkInspector
import com.meshcoreone.android.core.connectivity.ble.LinkFailureKind
import com.meshcoreone.android.core.model.DevicePlatform
import com.meshcoreone.android.core.model.RadioId
import com.meshcoreone.android.core.model.TransportType
import com.meshcoreone.android.core.protocol.model.DeviceCapabilities
import com.meshcoreone.android.core.runtime.LinkCallbacks
import com.meshcoreone.android.core.runtime.LinkFailure
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BleRuntimeLinkTest {
    private class FakeConnection(val events: GattEvents, val script: FakeFacade) : GattConnection {
        override fun submit(operation: GattOperation) {
            val failure = script.failOn?.takeIf { it.first == operation.kind }?.second
            if (failure != null) { events.onFailure(this, operation.key, failure); return }
            val reply = when (operation) {
                is GattOperation.Connect -> GattReply.Connected(script.bond)
                is GattOperation.DiscoverServices -> GattReply.Services(listOf(script.service))
                is GattOperation.Mtu -> GattReply.Mtu(517)
                is GattOperation.Subscribe -> GattReply.Subscribed
                is GattOperation.Write -> GattReply.Written
                is GattOperation.Rssi -> GattReply.Rssi(-50, 0)
            }
            events.onReply(this, operation.key, reply)
        }
        override fun close(): Deferred<Unit> = CompletableDeferred(Unit)
    }

    private class FakeFacade(override val handle: BleDeviceHandle = BleDeviceHandle(ADDRESS)) : GattFacade {
        var bond = BondState.Bonded
        var failOn: Pair<com.meshcoreone.android.core.ble.GattOperationKind, BleTransportException>? = null
        val connections = mutableListOf<FakeConnection>()
        private val cccd = GattDescriptor(NusUuid.CCCD)
        val service = GattService(
            NusUuid.SERVICE,
            listOf(
                GattCharacteristic(NusUuid.TX, setOf(GattProperty.Write), emptyList()),
                GattCharacteristic(NusUuid.RX, setOf(GattProperty.Notify), listOf(cccd)),
            ),
        )
        override fun availability() = BluetoothAvailability.Ready
        override fun create(events: GattEvents): GattConnection = FakeConnection(events, this).also { connections += it }
    }

    private class Recorder {
        val log = java.util.Collections.synchronizedList(mutableListOf<String>())
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable?>())
        var throwOnDisconnect = false
        val callbacks = LinkCallbacks(
            onDisconnected = { cause -> log += "disconnected"; failures += cause; if (throwOnDisconnect) error("boom") },
            onAutoReconnecting = { details -> log += "autoReconnecting:$details" },
            onReconnected = { log += "reconnected" },
            onBondRefreshed = { log += "bondRefreshed" },
        )
    }

    private val deviceId = UUID.randomUUID()
    private val token = SessionToken(ProcessEpoch(UUID.randomUUID()), Generation(1), RadioId(UUID.randomUUID()))
    private val capabilities = DeviceCapabilities(9u, 100, 8, 123456u, "b", "T-Echo", "1.0")

    private class Rig(val facade: FakeFacade, val ble: BleTransport, val link: BleRuntimeLink, val memory: BleReconnectMemory)

    private fun rig(deviceId: UUID, diagnostics: MutableStateFlow<BleLinkDiagnostics>? = null): Rig {
        val facade = FakeFacade()
        val ble = BleTransport(facade)
        val memory = BleReconnectMemory()
        val link = BleRuntimeLink(ble, deviceId, memory, diagnostics ?: ble.diagnostics, Dispatchers.Unconfined)
        return Rig(facade, ble, link, memory)
    }

    private fun snapshot(phase: BlePhase, issue: BleError? = null) = BleLinkDiagnostics(
        BluetoothAvailability.Ready, phase, 1, null, null, null, null, false, false, false, 0, null, issue,
    )

    @Test fun connectSuccessReachesConnectedAndConfiguresFrameCapacity() = runBlocking<Unit> {
        val rig = rig(deviceId)
        val recorder = Recorder()
        rig.link.register(recorder.callbacks).use {
            assertEquals(TransportType.BLUETOOTH, rig.link.type)
            rig.link.transport.connect()
            assertTrue(rig.link.transport.isConnected())
            assertEquals(BlePhase.Connected, rig.ble.diagnostics.value.phase)
            assertEquals(20, rig.ble.diagnostics.value.maximumCommandBytes)
            rig.link.configure(capabilities, DevicePlatform.detect(capabilities.model))
            assertEquals(172, rig.ble.diagnostics.value.maximumCommandBytes)
            assertTrue(rig.ble.diagnostics.value.firmwareVerified)
            rig.link.setSessionLive(token)
            rig.link.setSessionLive(null)
            assertTrue(recorder.log.isEmpty())
        }
    }

    @Test fun authenticationFailureDuringConnectClassifiesAsAuthentication() = runBlocking<Unit> {
        val rig = rig(deviceId)
        rig.facade.failOn = com.meshcoreone.android.core.ble.GattOperationKind.Connect to BleTransportException(BleError.AuthenticationFailed)
        val recorder = Recorder()
        rig.link.register(recorder.callbacks).use {
            val failure = assertFailsWith<BleTransportException> { rig.link.transport.connect() }
            assertEquals(LinkFailureKind.AuthenticationFailed, BleLinkInspector.classify(failure))
            assertTrue(recorder.log.isEmpty(), "a failed connect is the caller's error, not a link loss")
        }
    }

    @Test fun lostBondAfterConnectReportsAuthenticationLinkFailure() = runBlocking<Unit> {
        val rig = rig(deviceId)
        val recorder = Recorder()
        rig.link.register(recorder.callbacks).use {
            rig.link.transport.connect()
            val connection = rig.facade.connections.single()
            connection.events.onBondChanged(connection, BondState.None)
            assertEquals(listOf("disconnected"), recorder.log)
            assertIs<LinkFailure.AuthenticationFailed>(recorder.failures.single())
        }
    }

    @Test fun linkLossReportsDisconnectOnceAndNextLinkReconnects() = runBlocking<Unit> {
        val rig = rig(deviceId)
        val recorder = Recorder()
        rig.link.register(recorder.callbacks).use {
            rig.link.transport.connect()
            assertEquals(BleConnectMode.Initial, rig.memory.modeFor(deviceId))
            val connection = rig.facade.connections.single()
            connection.events.onDisconnected(connection, 8)
            connection.events.onDisconnected(connection, 8)
            assertEquals(listOf("disconnected"), recorder.log)
            assertTrue(recorder.failures.single() != null)
            assertEquals(BleConnectMode.Reconnect, rig.memory.modeFor(deviceId))
        }
    }

    @Test fun cleanRemoteDisconnectReportsNullCause() = runBlocking<Unit> {
        val rig = rig(deviceId)
        val recorder = Recorder()
        rig.link.register(recorder.callbacks).use {
            rig.link.transport.connect()
            val connection = rig.facade.connections.single()
            connection.events.onDisconnected(connection, 0)
            assertEquals(listOf("disconnected"), recorder.log)
            assertNull(recorder.failures.single())
        }
    }

    @Test fun userDisconnectIsNotReportedAndClearsReconnectMemory() = runBlocking<Unit> {
        val rig = rig(deviceId)
        val recorder = Recorder()
        rig.memory.markLost(deviceId)
        rig.link.register(recorder.callbacks).use {
            rig.link.transport.connect()
            rig.link.transport.disconnect()
            assertTrue(recorder.log.isEmpty())
            assertEquals(BleConnectMode.Initial, rig.memory.modeFor(deviceId))
        }
    }

    @Test fun autoReconnectCallbacksFireInOrderThenLoss() = runBlocking<Unit> {
        val states = MutableStateFlow(snapshot(BlePhase.Idle))
        val rig = rig(deviceId, states)
        val recorder = Recorder()
        rig.link.register(recorder.callbacks).use {
            states.value = snapshot(BlePhase.Connected)
            states.value = snapshot(BlePhase.AutoReconnecting)
            states.value = snapshot(BlePhase.Connected)
            states.value = snapshot(BlePhase.AutoReconnecting)
            states.value = snapshot(BlePhase.Idle, BleError.ConnectionTimeout)
            assertEquals(
                listOf("autoReconnecting:phase=autoReconnecting", "reconnected", "autoReconnecting:phase=autoReconnecting", "disconnected"),
                recorder.log,
            )
            assertIs<LinkFailure.ConnectionTimeout>(recorder.failures.single())
        }
    }

    @Test fun bondRefreshCallbackFiresOnlyWhileTheVerificationIsCurrent() = runBlocking<Unit> {
        val rig = rig(deviceId)
        val recorder = Recorder()
        rig.link.register(recorder.callbacks).use {
            rig.link.transport.connect()
            assertFalse(rig.link.mayRefreshBond(deviceId))
            rig.link.recordBondVerification(deviceId, Instant.ofEpochSecond(1))
            rig.link.setSessionLive(token)
            rig.ble.readRssi()
            assertEquals(listOf("bondRefreshed"), recorder.log)
            assertTrue(rig.link.mayRefreshBond(deviceId))
            rig.link.clearBondVerification(deviceId)
            assertFalse(rig.link.mayRefreshBond(deviceId))
        }
    }

    @Test fun closeUnregistersHandlersAndLeavesNoCollector() = runBlocking<Unit> {
        val states = MutableStateFlow(snapshot(BlePhase.Idle))
        val rig = rig(deviceId, states)
        val recorder = Recorder()
        val registration = rig.link.register(recorder.callbacks)
        assertEquals(1, states.subscriptionCount.value)
        registration.close()
        registration.close()
        assertEquals(0, states.subscriptionCount.value)
        states.value = snapshot(BlePhase.Connected)
        states.value = snapshot(BlePhase.Idle)
        assertTrue(recorder.log.isEmpty())

        val live = rig(deviceId)
        val second = Recorder()
        live.link.register(second.callbacks).close()
        live.link.transport.connect()
        live.link.recordBondVerification(deviceId, Instant.ofEpochSecond(1))
        live.link.setSessionLive(token)
        live.ble.readRssi()
        assertTrue(second.log.isEmpty(), "bond handler must be cleared on close")
    }

    @Test fun reRegisteringReplacesTheCollectorInsteadOfAccumulating() = runBlocking<Unit> {
        val states = MutableStateFlow(snapshot(BlePhase.Idle))
        val rig = rig(deviceId, states)
        val first = Recorder()
        val second = Recorder()
        rig.link.register(first.callbacks)
        val current = rig.link.register(second.callbacks)
        assertEquals(1, states.subscriptionCount.value)
        states.value = snapshot(BlePhase.Connected)
        states.value = snapshot(BlePhase.Idle)
        assertTrue(first.log.isEmpty())
        assertEquals(listOf("disconnected"), second.log)
        current.close()
        assertEquals(0, states.subscriptionCount.value)
    }

    @Test fun throwingCallbackIsContainedAndLaterEventsStillDeliver() = runBlocking<Unit> {
        val states = MutableStateFlow(snapshot(BlePhase.Idle))
        val rig = rig(deviceId, states)
        val recorder = Recorder().also { it.throwOnDisconnect = true }
        rig.link.register(recorder.callbacks).use {
            states.value = snapshot(BlePhase.Connected)
            states.value = snapshot(BlePhase.Idle)
            states.value = snapshot(BlePhase.Connected)
            states.value = snapshot(BlePhase.AutoReconnecting)
            assertEquals(listOf("disconnected", "autoReconnecting:phase=autoReconnecting"), recorder.log)
        }
    }

    @Test fun factoryBuildsWifiAndBluetoothLinksAndNoLongerRefusesBluetooth() {
        val requested = mutableListOf<BleDeviceHandle>()
        val facade = FakeFacade()
        val factory = AndroidRuntimeLinkFactory({ handle -> requested += handle; facade }, linkDispatcher = Dispatchers.Unconfined)
        assertEquals(null, factory.currentBleLink())

        val wifi = factory.create(ConnectionTarget.WiFi("192.168.1.5", 5000u))
        assertIs<WifiRuntimeLink>(wifi)
        assertEquals(TransportType.WIFI, wifi.type)

        val target = ConnectionTarget.Bluetooth(BluetoothPairingHandle(BluetoothAddress(ADDRESS.lowercase()), null), deviceId)
        val ble = factory.create(target)
        assertIs<BleRuntimeLink>(ble)
        assertEquals(TransportType.BLUETOOTH, ble.type)
        assertEquals(listOf(BleDeviceHandle(ADDRESS)), requested)
        assertEquals(deviceId, factory.currentBleLink()?.deviceId)
        assertEquals(BlePhase.Idle, factory.currentBleLink()?.diagnostics?.phase)
    }

    private companion object { const val ADDRESS = "02:00:00:00:00:01" }
}
