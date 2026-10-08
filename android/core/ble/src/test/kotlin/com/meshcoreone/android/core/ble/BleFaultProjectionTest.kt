// AndroidOnly: WP-205 Paired real BLE projection assertions for the authorized WP-304 consumer seam.
package com.meshcoreone.android.core.ble

import com.meshcoreone.android.core.contracts.domain.errors.*
import java.io.IOException
import kotlin.test.*
import org.junit.Test

class BleFaultProjectionTest {
    private val reason = String(charArrayOf('r', 'e', 'a', 's', 'o', 'n'))
    private val cases: List<Pair<BleError, BleFault>> = listOf(
        BleError.BluetoothUnavailable to BleFault.BluetoothUnavailable,
        BleError.BluetoothUnauthorized to BleFault.BluetoothUnauthorized,
        BleError.BluetoothPoweredOff to BleFault.BluetoothPoweredOff,
        BleError.DeviceNotFound to BleFault.DeviceNotFound,
        BleError.ConnectionFailed(reason) to BleFault.ConnectionFailed(reason),
        BleError.ConnectionTimeout to BleFault.ConnectionTimeout,
        BleError.NotConnected to BleFault.NotConnected,
        BleError.CharacteristicNotFound to BleFault.CharacteristicNotFound,
        BleError.WriteError(reason) to BleFault.WriteError(reason),
        BleError.InvalidResponse to BleFault.InvalidResponse,
        BleError.OperationTimeout to BleFault.OperationTimeout,
        BleError.AuthenticationFailed to BleFault.AuthenticationFailed,
        BleError.PairingFailed(reason) to BleFault.PairingFailed(reason),
        BleError.DeviceConnectedToOtherApp to BleFault.DeviceConnectedToOtherApp,
        BleError.ServiceNotFound to BleFault.ServiceNotFound,
        BleError.DescriptorNotFound to BleFault.DescriptorNotFound,
        BleError.CharacteristicPropertyMissing(GattProperty.Notify) to BleFault.CharacteristicPropertyMissing(BleFaultProperty.Notify),
        BleError.BondRequired(BondState.Bonding) to BleFault.BondRequired(BleFaultBondState.Bonding),
        BleError.GattRejected(GattOperationKind.Write, null) to BleFault.GattRejected(BleFaultOperation.Write, null),
        BleError.GattOperationInProgress(GattOperationKind.Subscribe) to BleFault.GattOperationInProgress(BleFaultOperation.Subscribe),
        BleError.PlatformApiUnavailable(37, 31) to BleFault.PlatformApiUnavailable(37, 31),
        BleError.MtuTooSmall(22, 23) to BleFault.MtuTooSmall(22, 23),
        BleError.InvalidMtu(Int.MIN_VALUE) to BleFault.InvalidMtu(Int.MIN_VALUE),
        BleError.FrameTooLarge(Int.MAX_VALUE, 20) to BleFault.FrameTooLarge(Int.MAX_VALUE, 20),
        BleError.FirmwareCapabilityUnverified(21, 20) to BleFault.FirmwareCapabilityUnverified(21, 20),
        BleError.FirmwareCapabilitiesAlreadyVerified to BleFault.FirmwareCapabilitiesAlreadyVerified,
        BleError.StaleGeneration(Long.MAX_VALUE, Long.MIN_VALUE) to BleFault.StaleGeneration(Long.MAX_VALUE, Long.MIN_VALUE),
        BleError.MultipleReceivers to BleFault.MultipleReceivers,
        BleError.NotificationDeliveryFailed to BleFault.NotificationDeliveryFailed,
        BleError.RssiReadFailed(Int.MAX_VALUE) to BleFault.RssiReadFailed(Int.MAX_VALUE),
        BleError.CleanupFailed(reason) to BleFault.CleanupFailed(reason),
        BleError.AbortedOperation(GattOperationKind.Connect) to BleFault.AbortedOperation(BleFaultOperation.Connect),
    )

    @Test fun allSourceAndNativeCasesKeepTheirPayloadCauseRecoveryAndDiagnostics() {
        assertEquals(32, cases.size)
        val cause = IOException("synthetic original cause")
        for ((error, expected) in cases) {
            val failure = BleTransportException(error, cause = cause)
            val carrier: SourceServiceFaultCarrier = failure
            val fault = assertIs<BleTransportFault>(carrier.sourceServiceFault)
            assertEquals(expected, fault.error)
            assertEquals(fault, failure.sourceServiceFault)
            assertSame(error, failure.error)
            assertSame(cause, failure.cause)
            assertEquals("ble.${error.javaClass.simpleName}", failure.message)
            assertNull(fault.operation); assertNull(fault.status); assertNull(fault.statusDomain)
            val expectedRecovery = when (error) {
                BleError.BluetoothUnavailable, is BleError.PlatformApiUnavailable -> BleRecovery.HostConfiguration
                BleError.BluetoothUnauthorized -> BleRecovery.GrantBluetoothConnect
                BleError.BluetoothPoweredOff -> BleRecovery.EnableBluetooth
                BleError.DeviceNotFound -> BleRecovery.SelectDevice
                BleError.AuthenticationFailed, is BleError.PairingFailed, is BleError.BondRequired -> BleRecovery.PairInSystem
                is BleError.FirmwareCapabilityUnverified -> BleRecovery.VerifyFirmwareCapabilities
                is BleError.MtuTooSmall -> BleRecovery.IncreaseMtu
                is BleError.FrameTooLarge -> BleRecovery.ReduceFrame
                BleError.MultipleReceivers -> BleRecovery.UseSingleReceiver
                else -> BleRecovery.RetryWithNewConnection
            }
            assertEquals(expectedRecovery, failure.recovery)
            assertEquals(expectedRecovery.name, fault.recovery.name)
            when (val payload = fault.error) {
                is BleFault.ConnectionFailed -> assertSame(reason, payload.reason)
                is BleFault.WriteError -> assertSame(reason, payload.reason)
                is BleFault.PairingFailed -> assertSame(reason, payload.reason)
                is BleFault.CleanupFailed -> assertSame(reason, payload.stage)
                else -> Unit
            }
        }
    }

    @Test fun everyMetadataEnumAndNullableOrExtremeStatusProjectsWithoutOrdinalCoercion() {
        val operations = mapOf(
            GattOperationKind.Connect to BleFaultOperation.Connect,
            GattOperationKind.DiscoverServices to BleFaultOperation.DiscoverServices,
            GattOperationKind.Mtu to BleFaultOperation.Mtu,
            GattOperationKind.Subscribe to BleFaultOperation.Subscribe,
            GattOperationKind.Write to BleFaultOperation.Write,
            GattOperationKind.Rssi to BleFaultOperation.Rssi,
        )
        val domains = mapOf(
            GattStatusDomain.Att to BleFaultStatusDomain.Att,
            GattStatusDomain.ConnectionState to BleFaultStatusDomain.ConnectionState,
            GattStatusDomain.PlatformStart to BleFaultStatusDomain.PlatformStart,
            GattStatusDomain.RadioMeasurement to BleFaultStatusDomain.RadioMeasurement,
        )
        assertEquals(GattOperationKind.entries.toSet(), operations.keys)
        assertEquals(GattStatusDomain.entries.toSet(), domains.keys)
        for ((operation, projected) in operations) for ((domain, projectedDomain) in domains) {
            for (status in listOf(null, Int.MIN_VALUE, 0, Int.MAX_VALUE)) {
                val error = BleError.GattRejected(operation, status)
                val failure = BleTransportException(error, operation, status, statusDomain = domain)
                assertEquals(BleFault.GattRejected(projected, status), failure.sourceServiceFault.error)
                assertEquals(projected, failure.sourceServiceFault.operation)
                assertEquals(status, failure.sourceServiceFault.status)
                assertEquals(projectedDomain, failure.sourceServiceFault.statusDomain)
            }
            assertEquals(BleFault.GattOperationInProgress(projected),
                BleTransportException(BleError.GattOperationInProgress(operation)).sourceServiceFault.error)
            assertEquals(BleFault.AbortedOperation(projected),
                BleTransportException(BleError.AbortedOperation(operation)).sourceServiceFault.error)
        }
        for ((property, projected) in listOf(
            GattProperty.Write to BleFaultProperty.Write,
            GattProperty.WriteWithoutResponse to BleFaultProperty.WriteWithoutResponse,
            GattProperty.Notify to BleFaultProperty.Notify,
        )) assertEquals(BleFault.CharacteristicPropertyMissing(projected),
            BleTransportException(BleError.CharacteristicPropertyMissing(property)).sourceServiceFault.error)
        for ((state, projected) in listOf(
            BondState.None to BleFaultBondState.None,
            BondState.Bonding to BleFaultBondState.Bonding,
            BondState.Bonded to BleFaultBondState.Bonded,
        )) assertEquals(BleFault.BondRequired(projected),
            BleTransportException(BleError.BondRequired(state)).sourceServiceFault.error)
    }

    @Test fun actualGattThrowSitesStillDistinguishAttAndConnectionStateStatusEight() {
        val att = gattFailure(GattOperationKind.Connect, 8)
        val connection = connectionStateFailure(GattOperationKind.Connect, 8)
        assertEquals(BleError.AuthenticationFailed, att.error)
        assertEquals(BleFault.AuthenticationFailed, att.sourceServiceFault.error)
        assertEquals(BleFaultStatusDomain.Att, att.sourceServiceFault.statusDomain)
        assertEquals(BleFaultRecovery.PairInSystem, att.sourceServiceFault.recovery)
        assertEquals(BleError.ConnectionTimeout, connection.error)
        assertEquals(BleFault.ConnectionTimeout, connection.sourceServiceFault.error)
        assertEquals(BleFaultStatusDomain.ConnectionState, connection.sourceServiceFault.statusDomain)
        assertEquals(BleFaultRecovery.RetryWithNewConnection, connection.sourceServiceFault.recovery)
        assertEquals(8, att.sourceServiceFault.status)
        assertEquals(8, connection.sourceServiceFault.status)
    }

    @Test fun sourceAndNeutralCasesAreExactlyOneToOneAndAllRecoveryValuesAreRepresented() {
        assertEquals(cases.map { it.first.javaClass }.toSet(), BleError::class.java.permittedSubclasses.toSet())
        assertEquals(cases.map { it.second.javaClass }.toSet(), BleFault::class.java.permittedSubclasses.toSet())
        assertEquals(BleRecovery.entries.map { it.name }.toSet(), BleFaultRecovery.entries.map { it.name }.toSet())
        assertEquals(BleRecovery.entries.toSet(), cases.map { BleTransportException(it.first).recovery }.toSet())
    }
}
