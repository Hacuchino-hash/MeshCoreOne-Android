// AndroidOnly: WP-206 Stale-bond recovery routing after an authentication failure (removeBond exists only from API 36).
package com.meshcoreone.android.core.connectivity

import com.meshcoreone.android.core.ble.BleError
import com.meshcoreone.android.core.ble.BleTransportException
import com.meshcoreone.android.core.connectivity.bond.BondInspector
import com.meshcoreone.android.core.connectivity.pairing.PairingError
import com.meshcoreone.android.core.connectivity.pairing.PairingRecovery
import com.meshcoreone.android.core.connectivity.support.FakeRadio
import com.meshcoreone.android.core.connectivity.support.RuntimeHarness
import com.meshcoreone.android.core.connectivity.support.runtimeScenario
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Test

class PairingRecoveryTest {
    private class StubBonds(override val canRemoveBonds: Boolean, private val bonded: Boolean) : BondInspector {
        override fun isBonded(deviceId: UUID): Boolean = bonded
    }

    private suspend fun RuntimeHarness.pairFailing(error: BleError, bonds: BondInspector?): PairingError.ConnectionFailed {
        val id = UUID.randomUUID()
        companion.setPickerResult(Result.success(id))
        companion.isSessionActive = false
        createRadio = { FakeRadio().also { it.connectFailure = BleTransportException(error, status = 5) } }
        val pairing = coordinator(bonds).apply { otherAppWaitStrategy = { false } }
        val result = CompletableDeferred<Result<Unit>>()
        scenario.scope.launch { result.complete(runCatching { pairing.pairNewDevice() }) }
        assertTrue(scenario.awaitCondition { result.isCompleted }, "Retries advance on the virtual clock")
        return assertIs<PairingError.ConnectionFailed>(result.await().exceptionOrNull())
    }

    @Test fun `auth failure with a bond the app cannot remove routes to Bluetooth settings`() = runtimeScenario {
        val failure = pairFailing(BleError.AuthenticationFailed, StubBonds(canRemoveBonds = false, bonded = true))
        assertEquals(PairingRecovery.ForgetInBluetoothSettings, failure.recovery)
    }

    @Test fun `auth failure where the bond can be removed or is absent offers remove and retry`() = runtimeScenario {
        assertEquals(PairingRecovery.RemoveAndRetry,
            pairFailing(BleError.AuthenticationFailed, StubBonds(canRemoveBonds = true, bonded = true)).recovery)
        assertEquals(PairingRecovery.RemoveAndRetry,
            pairFailing(BleError.AuthenticationFailed, StubBonds(canRemoveBonds = false, bonded = false)).recovery)
        assertEquals(PairingRecovery.RemoveAndRetry, pairFailing(BleError.AuthenticationFailed, null).recovery)
    }

    @Test fun `non-auth failures offer no bond recovery`() = runtimeScenario {
        val failure = pairFailing(BleError.ConnectionTimeout, StubBonds(canRemoveBonds = false, bonded = true))
        assertEquals(PairingRecovery.None, failure.recovery)
    }
}
