// PortedFrom: MC1Services/Tests/MC1ServicesTests/Transport/BLEStateMachineDisconnectionMappingTests.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Tests/MC1ServicesTests/Transport/BLEStateMachineRestorationAndTeardownTests.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.ble

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class BleGattStatusTest(
    private val kind: GattOperationKind,
    private val status: Int,
    private val authentication: Boolean,
) {
    @Test fun `ATT status maps to source typed errors and retains operation metadata`() {
        val failure = gattFailure(kind, status)
        val expected = when {
            authentication -> BleError.AuthenticationFailed
            kind == GattOperationKind.Write -> BleError.WriteError("gatt.status.$status")
            else -> BleError.ConnectionFailed("gatt.status.$status")
        }
        assertEquals(expected, failure.error)
        assertEquals(kind, failure.operation)
        assertEquals(status, failure.status)
        if (!authentication) assertFalse(failure.error == BleError.DeviceConnectedToOtherApp)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}-status{1}")
        fun statuses(): List<Array<Any>> = GattOperationKind.entries.flatMap { kind ->
            listOf(
                arrayOf<Any>(kind, 5, true), arrayOf<Any>(kind, 8, true), arrayOf<Any>(kind, 12, true), arrayOf<Any>(kind, 15, true),
                arrayOf<Any>(kind, 3, false), arrayOf<Any>(kind, 6, false), arrayOf<Any>(kind, 133, false),
            )
        }
    }
}
