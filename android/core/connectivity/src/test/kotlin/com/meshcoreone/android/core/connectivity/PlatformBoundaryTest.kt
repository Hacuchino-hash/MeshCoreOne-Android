// AndroidOnly: WP-206 Bonding/PIN, permission denial/revocation/Location Services/pre-unlock, connectedDevice FGS, presence and process-death boundaries.
package com.meshcoreone.android.core.connectivity

import com.meshcoreone.android.core.ble.BleError
import com.meshcoreone.android.core.ble.BleTransportException
import com.meshcoreone.android.core.ble.BondState
import com.meshcoreone.android.core.ble.BluetoothAvailability
import com.meshcoreone.android.core.connectivity.ble.BleLinkInspector
import com.meshcoreone.android.core.connectivity.ble.LinkFailureKind
import com.meshcoreone.android.core.connectivity.bond.BondEvent
import com.meshcoreone.android.core.connectivity.bond.BondFailure
import com.meshcoreone.android.core.connectivity.bond.BondGateway
import com.meshcoreone.android.core.connectivity.bond.BondProgress
import com.meshcoreone.android.core.connectivity.bond.BondingCoordinator
import com.meshcoreone.android.core.connectivity.permissions.Advisory
import com.meshcoreone.android.core.connectivity.permissions.ConnectivityFeature
import com.meshcoreone.android.core.connectivity.permissions.ConnectivityPermission
import com.meshcoreone.android.core.connectivity.permissions.ConnectivityPermissionPolicy
import com.meshcoreone.android.core.connectivity.permissions.FeatureReadiness
import com.meshcoreone.android.core.connectivity.permissions.PairingMode
import com.meshcoreone.android.core.connectivity.permissions.PermissionSnapshot
import com.meshcoreone.android.core.connectivity.presence.CompanionPresenceDispatcher
import com.meshcoreone.android.core.connectivity.presence.IgnoreReason
import com.meshcoreone.android.core.connectivity.presence.PresenceAction
import com.meshcoreone.android.core.connectivity.presence.PresenceContext
import com.meshcoreone.android.core.connectivity.presence.PresenceEvent
import com.meshcoreone.android.core.connectivity.presence.PresenceEventSink
import com.meshcoreone.android.core.connectivity.presence.PresenceReconnectPolicy
import com.meshcoreone.android.core.connectivity.service.ConnectedDeviceHostingController
import com.meshcoreone.android.core.connectivity.service.ConnectedDeviceHostingPolicy
import com.meshcoreone.android.core.connectivity.service.DeferReason
import com.meshcoreone.android.core.connectivity.service.ForegroundServiceStarter
import com.meshcoreone.android.core.connectivity.service.HostingDecision
import com.meshcoreone.android.core.connectivity.service.HostingInput
import com.meshcoreone.android.core.connectivity.service.StartExemption
import com.meshcoreone.android.core.connectivity.service.StartOutcome
import com.meshcoreone.android.core.connectivity.support.scenario
import com.meshcoreone.android.core.connectivity.support.settle
import com.meshcoreone.android.core.contracts.domain.Capability
import com.meshcoreone.android.core.contracts.domain.ConnectionIntent
import com.meshcoreone.android.core.contracts.domain.ConnectionIssue
import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import org.junit.After
import org.junit.Test

class PlatformBoundaryTest {
    private class FakeBondGateway(var state: BondState = BondState.None, var accept: Boolean = true) : BondGateway {
        var creates = 0
        val listeners = mutableListOf<(BondEvent) -> Unit>()
        var closed = 0
        override fun bondState(address: String): BondState = state
        override fun createBond(address: String): Boolean { creates++; return accept }
        override fun register(listener: (BondEvent) -> Unit): AutoCloseable {
            listeners += listener
            return AutoCloseable { closed++; listeners.remove(listener) }
        }
        fun emit(event: BondEvent) = listeners.toList().forEach { it(event) }
    }

    private val address = "C4:DE:E2:11:22:33"

    @After fun resetDispatcher() = CompanionPresenceDispatcher.resetForTest()

    // Bonding / PIN

    @Test fun `already bonded devices are not re-bonded`() = scenario {
        val gateway = FakeBondGateway(BondState.Bonded)
        BondingCoordinator(gateway, clock).ensureBonded(address)
        assertEquals(0, gateway.creates)
    }

    @Test fun `bonding records system PIN entry and completes when bonded`() = scenario {
        val gateway = FakeBondGateway()
        val bonding = BondingCoordinator(gateway, clock)
        val task = scope.async { bonding.ensureBonded(address.lowercase()) }
        settle()
        assertEquals(1, gateway.creates)
        gateway.emit(BondEvent.PairingRequested(address, variant = 1))
        assertEquals(BondProgress.PinEntry(address, 1), bonding.progress.value)
        gateway.emit(BondEvent.StateChanged("11:22:33:44:55:66", BondState.Bonded, BondState.Bonding, null))
        settle()
        assertFalse(task.isCompleted, "Another device's broadcast is ignored")
        gateway.emit(BondEvent.StateChanged(address, BondState.Bonded, BondState.Bonding, null))
        task.await()
        assertEquals(BondProgress.Bonded(address), bonding.progress.value)
        assertEquals(1, gateway.closed, "Receiver unregistered")
    }

    @Test fun `wrong PIN fails as an authentication failure classified for guided recovery`() = scenario {
        val gateway = FakeBondGateway()
        val bonding = BondingCoordinator(gateway, clock)
        val task = scope.async { runCatching { bonding.ensureBonded(address) } }
        settle()
        gateway.emit(BondEvent.StateChanged(address, BondState.None, BondState.Bonding, reason = 1))
        val failure = assertIs<BondFailure>(task.await().exceptionOrNull())
        assertTrue(failure.authentication)
        assertEquals(LinkFailureKind.AuthenticationFailed, BleLinkInspector.classify(failure))
        assertIs<BondProgress.Failed>(bonding.progress.value)
    }

    @Test fun `cancelled PIN dialog and rejected createBond are not authentication failures`() = scenario {
        val gateway = FakeBondGateway()
        val bonding = BondingCoordinator(gateway, clock)
        val task = scope.async { runCatching { bonding.ensureBonded(address) } }
        settle()
        gateway.emit(BondEvent.StateChanged(address, BondState.None, BondState.Bonding, reason = 3))
        val canceled = assertIs<BondFailure>(task.await().exceptionOrNull())
        assertEquals(BondFailure.Reason.Canceled, canceled.reason)
        assertFalse(canceled.authentication)
        val rejected = runCatching { BondingCoordinator(FakeBondGateway(accept = false), clock).ensureBonded(address) }
        assertEquals(BondFailure.Reason.CreateBondRejected, assertIs<BondFailure>(rejected.exceptionOrNull()).reason)
    }

    @Test fun `bonding times out on the injected clock and cancellation unregisters`() = scenario {
        val gateway = FakeBondGateway()
        val bonding = BondingCoordinator(gateway, clock, timeout = 30.seconds)
        val timed = scope.async { runCatching { bonding.ensureBonded(address) } }
        settle()
        clock.advanceBy(30.seconds)
        assertEquals(BondFailure.Reason.TimedOut, assertIs<BondFailure>(timed.await().exceptionOrNull()).reason)
        val cancelled = scope.async { bonding.ensureBonded(address) }
        settle()
        cancelled.cancel()
        assertIs<CancellationException>(runCatching { cancelled.await() }.exceptionOrNull())
        assertTrue(gateway.listeners.isEmpty())
    }

    @Test fun `an in-progress system bond is awaited rather than restarted`() = scenario {
        val gateway = FakeBondGateway(BondState.Bonding)
        val task = scope.async { BondingCoordinator(gateway, clock).ensureBonded(address) }
        settle()
        gateway.emit(BondEvent.StateChanged(address, BondState.Bonded, BondState.Bonding, null))
        task.await()
        assertEquals(0, gateway.creates)
    }

    // Permissions, capability, Location Services, denial, revocation, pre-unlock

    private fun snapshot(vararg granted: ConnectivityPermission, sdk: Int = 34) = PermissionSnapshot(sdk, granted.toSet())

    @Test fun `companion pairing needs only BLUETOOTH_CONNECT while the scan fallback also needs BLUETOOTH_SCAN`() {
        assertEquals(setOf(ConnectivityPermission.BLUETOOTH_CONNECT),
            ConnectivityPermissionPolicy.required(ConnectivityFeature.BlePairing, 31, PairingMode.Companion))
        assertEquals(setOf(ConnectivityPermission.BLUETOOTH_SCAN, ConnectivityPermission.BLUETOOTH_CONNECT),
            ConnectivityPermissionPolicy.required(ConnectivityFeature.BlePairing, 31, PairingMode.ScanFallback))
    }

    @Test fun `denied BLUETOOTH_CONNECT is a typed permission issue`() {
        val readiness = ConnectivityPermissionPolicy.evaluate(ConnectivityFeature.BleConnection, snapshot(), PairingMode.Companion)
        assertEquals(FeatureReadiness.Denied(setOf(ConnectivityPermission.BLUETOOTH_CONNECT)), readiness)
        assertEquals(ConnectionIssue.PermissionDenied(Capability.BLUETOOTH_CONNECT), ConnectivityPermissionPolicy.issueFor(readiness))
    }

    @Test fun `missing Bluetooth hardware and missing CDM feature are unsupported capabilities`() {
        val noRadio = snapshot(ConnectivityPermission.BLUETOOTH_CONNECT).copy(bluetoothAdapterPresent = false)
        assertEquals(FeatureReadiness.Unsupported(Capability.BLUETOOTH_CONNECT),
            ConnectivityPermissionPolicy.evaluate(ConnectivityFeature.BleConnection, noRadio, PairingMode.Companion))
        val noCdm = snapshot(ConnectivityPermission.BLUETOOTH_CONNECT).copy(companionDeviceSetupSupported = false)
        val readiness = ConnectivityPermissionPolicy.evaluate(ConnectivityFeature.BlePairing, noCdm, PairingMode.Companion)
        assertEquals(ConnectionIssue.Unsupported(Capability.COMPANION_ASSOCIATION), ConnectivityPermissionPolicy.issueFor(readiness))
    }

    @Test fun `Location Services off is only an advisory for the neverForLocation scan fallback`() {
        val off = snapshot(ConnectivityPermission.BLUETOOTH_CONNECT, ConnectivityPermission.BLUETOOTH_SCAN).copy(locationServicesEnabled = false)
        assertEquals(FeatureReadiness.Ready(setOf(Advisory.LocationServicesOff)),
            ConnectivityPermissionPolicy.evaluate(ConnectivityFeature.BlePairing, off, PairingMode.ScanFallback))
        assertEquals(FeatureReadiness.Ready(),
            ConnectivityPermissionPolicy.evaluate(ConnectivityFeature.BlePairing, off, PairingMode.Companion))
    }

    @Test fun `Bluetooth off and pre-unlock are typed and never success-shaped`() {
        val granted = snapshot(ConnectivityPermission.BLUETOOTH_CONNECT)
        assertEquals(FeatureReadiness.BluetoothOff, ConnectivityPermissionPolicy.evaluate(
            ConnectivityFeature.BleConnection, granted.copy(bluetoothEnabled = false), PairingMode.Companion))
        assertEquals(ConnectionIssue.BluetoothOff, ConnectivityPermissionPolicy.issueFor(FeatureReadiness.BluetoothOff))
        assertEquals(FeatureReadiness.UserLocked, ConnectivityPermissionPolicy.evaluate(
            ConnectivityFeature.BleConnection, granted.copy(userUnlocked = false), PairingMode.Companion))
    }

    @Test fun `notification permission is optional and LAN permission applies only from API 37`() {
        assertEquals(FeatureReadiness.Ready(setOf(Advisory.NotificationsHidden)),
            ConnectivityPermissionPolicy.evaluate(ConnectivityFeature.ConnectionNotification, snapshot(sdk = 33), PairingMode.Companion))
        assertEquals(FeatureReadiness.Ready(),
            ConnectivityPermissionPolicy.evaluate(ConnectivityFeature.ConnectionNotification, snapshot(sdk = 32), PairingMode.Companion))
        assertEquals(FeatureReadiness.Ready(),
            ConnectivityPermissionPolicy.evaluate(ConnectivityFeature.LanConnection, snapshot(sdk = 36), PairingMode.Companion))
        assertEquals(FeatureReadiness.Denied(setOf(ConnectivityPermission.ACCESS_LOCAL_NETWORK)),
            ConnectivityPermissionPolicy.evaluate(ConnectivityFeature.LanConnection, snapshot(sdk = 37), PairingMode.Companion))
        // LAN denial does not affect BLE.
        assertEquals(FeatureReadiness.Ready(), ConnectivityPermissionPolicy.evaluate(
            ConnectivityFeature.BleConnection, snapshot(ConnectivityPermission.BLUETOOTH_CONNECT, sdk = 37), PairingMode.Companion))
    }

    @Test fun `revocation is the set granted before and missing now`() {
        val before = snapshot(ConnectivityPermission.BLUETOOTH_CONNECT, ConnectivityPermission.POST_NOTIFICATIONS)
        val after = snapshot(ConnectivityPermission.POST_NOTIFICATIONS)
        assertEquals(setOf(ConnectivityPermission.BLUETOOTH_CONNECT), ConnectivityPermissionPolicy.revoked(before, after))
        assertTrue(ConnectivityPermissionPolicy.revoked(after, before).isEmpty())
        assertEquals(LinkFailureKind.BluetoothUnauthorized, BleLinkInspector.classify(SecurityException("revoked")))
    }

    @Test fun `availability mapping keeps transient adapter states ready`() {
        assertEquals(BluetoothAvailability.PoweredOff, BleLinkInspector.bluetoothAvailability(true, 10, true))
        assertEquals(BluetoothAvailability.PoweredOff, BleLinkInspector.bluetoothAvailability(true, 13, true))
        assertEquals(BluetoothAvailability.Ready, BleLinkInspector.bluetoothAvailability(true, 11, true))
        assertEquals(BluetoothAvailability.Ready, BleLinkInspector.bluetoothAvailability(true, 12, true))
        assertEquals(BluetoothAvailability.Unauthorized, BleLinkInspector.bluetoothAvailability(true, 12, false))
        assertEquals(BluetoothAvailability.Unavailable, BleLinkInspector.bluetoothAvailability(false, 12, true))
    }

    @Test fun `core ble failures classify into typed link-failure roles`() {
        assertEquals(LinkFailureKind.AuthenticationFailed, BleLinkInspector.classify(BleTransportException(BleError.AuthenticationFailed)))
        assertEquals(LinkFailureKind.BluetoothPoweredOff, BleLinkInspector.classify(BleTransportException(BleError.BluetoothPoweredOff)))
        assertEquals(LinkFailureKind.ConnectionTimeout,
            BleLinkInspector.classify(IllegalStateException("wrapped", BleTransportException(BleError.ConnectionTimeout))))
        assertEquals(LinkFailureKind.ConnectionFailed, BleLinkInspector.classify(BleTransportException(BleError.ConnectionFailed("x"))))
        assertTrue(BleLinkInspector.isDeviceNotFoundError(BleTransportException(BleError.DeviceNotFound)))
        assertNull(BleLinkInspector.classify(IllegalStateException("unrelated")))
    }

    // connectedDevice foreground service

    private class FakeStarter(var outcome: StartOutcome = StartOutcome.Started) : ForegroundServiceStarter {
        var starts = 0
        var stops = 0
        override fun start(): StartOutcome { starts++; return outcome }
        override fun stop() { stops++ }
    }

    private fun input(
        intent: ConnectionIntent = ConnectionIntent.WantsConnection(),
        state: DeviceConnectionState = DeviceConnectionState.READY,
        exemption: StartExemption? = StartExemption.Foreground,
        granted: Boolean = true,
        reconnecting: Boolean = false,
    ) = HostingInput(intent, state, reconnecting, exemption, granted)

    @Test fun `exactly one service is held while a wanted link is live and released on user disconnect`() {
        val starter = FakeStarter()
        val controller = ConnectedDeviceHostingController(starter)
        assertEquals(HostingDecision.Hold, controller.update(input(state = DeviceConnectionState.CONNECTING)))
        assertEquals(HostingDecision.Hold, controller.update(input()))
        assertEquals(HostingDecision.Hold, controller.update(input(exemption = null)))
        assertEquals(1, starter.starts, "Never a second start while held")
        assertEquals(HostingDecision.Release, controller.update(input(intent = ConnectionIntent.UserDisconnected)))
        assertEquals(1, starter.stops)
        assertEquals(HostingDecision.Release, controller.update(input(intent = ConnectionIntent.UserDisconnected)))
        assertEquals(1, starter.stops)
    }

    @Test fun `disconnected idle intent releases the service unless a reconnect is in progress`() {
        assertEquals(HostingDecision.Release, ConnectedDeviceHostingPolicy.decide(input(state = DeviceConnectionState.DISCONNECTED), true))
        assertEquals(HostingDecision.Hold, ConnectedDeviceHostingPolicy.decide(
            input(state = DeviceConnectionState.DISCONNECTED, reconnecting = true), true))
    }

    @Test fun `a companion association is not a background-start exemption`() {
        val starter = FakeStarter()
        val decision = ConnectedDeviceHostingController(starter).update(input(exemption = null))
        assertEquals(HostingDecision.Defer(DeferReason.BackgroundStartRestricted), decision)
        assertEquals(0, starter.starts)
    }

    @Test fun `background start refusal and missing type prerequisite are typed deferrals`() {
        val refused = ConnectedDeviceHostingController(FakeStarter(StartOutcome.BackgroundStartNotAllowed))
        assertEquals(HostingDecision.Defer(DeferReason.BackgroundStartRestricted),
            refused.update(input(exemption = StartExemption.CompanionPresenceCallback)))
        assertFalse(refused.isHeld)
        val typed = ConnectedDeviceHostingController(FakeStarter(StartOutcome.TypeNotPermitted(SecurityException("type"))))
        assertEquals(HostingDecision.Defer(DeferReason.MissingTypePrerequisite), typed.update(input()))
        assertEquals(HostingDecision.Defer(DeferReason.MissingTypePrerequisite),
            ConnectedDeviceHostingPolicy.decide(input(granted = false), false))
        assertEquals(HostingDecision.Hold,
            ConnectedDeviceHostingPolicy.decide(input(granted = false).copy(lanOnly = true), false))
    }

    @Test fun `a system-stopped service is restarted by the next qualifying update`() {
        val starter = FakeStarter()
        val controller = ConnectedDeviceHostingController(starter)
        controller.update(input())
        controller.onServiceStopped()
        assertFalse(controller.isHeld)
        controller.update(input())
        assertEquals(2, starter.starts)
        assertEquals(0, starter.stops)
    }

    // Presence, pre-unlock and process death

    private val device = UUID.randomUUID()

    private fun context(
        last: UUID? = device,
        state: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
        unlocked: Boolean = true,
        connect: Boolean = true,
        enabled: Boolean = true,
        userDisconnected: Boolean = false,
    ) = PresenceContext(ConnectionIntent.WantsConnection(), userDisconnected, last, state, false, unlocked, connect, enabled)

    @Test fun `presence of the last radio reconnects only when disconnected`() {
        assertEquals(PresenceAction.Reconnect(device), PresenceReconnectPolicy.decide(PresenceEvent.Appeared(device), context()))
        assertEquals(PresenceAction.Ignore(IgnoreReason.AlreadyConnected),
            PresenceReconnectPolicy.decide(PresenceEvent.Appeared(device), context(state = DeviceConnectionState.READY)))
        assertEquals(PresenceAction.Ignore(IgnoreReason.NotLastConnected),
            PresenceReconnectPolicy.decide(PresenceEvent.Appeared(UUID.randomUUID()), context()))
        assertEquals(PresenceAction.Ignore(IgnoreReason.UserDisconnected),
            PresenceReconnectPolicy.decide(PresenceEvent.Appeared(device), context(userDisconnected = true)))
    }

    @Test fun `presence before first unlock defers and revoked permission is reported`() {
        assertEquals(PresenceAction.DeferUntilUnlock(device),
            PresenceReconnectPolicy.decide(PresenceEvent.Appeared(device), context(unlocked = false)))
        assertEquals(PresenceAction.PermissionRevoked(Capability.BLUETOOTH_CONNECT),
            PresenceReconnectPolicy.decide(PresenceEvent.Appeared(device), context(connect = false)))
        assertEquals(PresenceAction.Ignore(IgnoreReason.BluetoothOff),
            PresenceReconnectPolicy.decide(PresenceEvent.Appeared(device), context(enabled = false)))
    }

    @Test fun `disappearance is not link loss and association removal forgets`() {
        assertEquals(PresenceAction.Ignore(IgnoreReason.DisappearanceIsNotLinkLoss),
            PresenceReconnectPolicy.decide(PresenceEvent.Disappeared(device), context(state = DeviceConnectionState.READY)))
        assertEquals(PresenceAction.ForgetAssociation(device),
            PresenceReconnectPolicy.decide(PresenceEvent.AssociationRemoved(device), context()))
    }

    @Test fun `events delivered before the host attaches after process death are replayed latest-per-device`() {
        val other = UUID.randomUUID()
        CompanionPresenceDispatcher.dispatch(PresenceEvent.Appeared(device))
        CompanionPresenceDispatcher.dispatch(PresenceEvent.Disappeared(device))
        CompanionPresenceDispatcher.dispatch(PresenceEvent.Appeared(other))
        val received = mutableListOf<PresenceEvent>()
        val sink = PresenceEventSink { received += it }
        CompanionPresenceDispatcher.attach(sink)
        assertEquals(listOf(PresenceEvent.Disappeared(device), PresenceEvent.Appeared(other)), received)
        CompanionPresenceDispatcher.dispatch(PresenceEvent.Appeared(device))
        assertEquals(PresenceEvent.Appeared(device), received.last())
        CompanionPresenceDispatcher.detach(sink)
        CompanionPresenceDispatcher.dispatch(PresenceEvent.Appeared(other))
        assertEquals(3, received.size, "Detached sink receives nothing")
    }
}
