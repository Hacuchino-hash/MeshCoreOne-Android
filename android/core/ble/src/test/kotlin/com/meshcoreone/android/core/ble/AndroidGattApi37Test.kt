// AndroidOnly: WP-205 Actual API37 framework/settings/shadow execution; no physical-radio certification.
package com.meshcoreone.android.core.ble

import android.bluetooth.BluetoothDevice
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], manifest = Config.NONE, shadows = [ControlledGattShadow::class, Api37DeviceShadow::class])
class AndroidGattApi37Test : AndroidGattAdapterTest() {
    @Test fun `API37 native settings use explicit LE connect and verified explicit MTU`() = runTest {
        val fixture = AndroidGattFixture()
        fixture.transport.connect()
        val shadow = Shadow.extract<Api37DeviceShadow>(fixture.device)
        val settings = assertNotNull(shadow.settings)
        assertEquals(BluetoothDevice.TRANSPORT_LE, settings.transport)
        assertFalse(settings.isAutoConnectEnabled)
        assertFalse(settings.isAutomaticMtuEnabled)
        assertFalse(settings.isOpportunisticEnabled)
        assertNotNull(shadow.callbackExecutor)
        assertEquals(517, fixture.transport.diagnostics.value.actualMtu)
        fixture.transport.disconnect()
    }
}
