// AndroidOnly: WP-317 Reboot confirmation flow of the device-actions section.
package com.meshcoreone.android.feature.settings.device

import com.meshcoreone.android.core.ui.UiText
import com.meshcoreone.android.feature.settings.device.support.FakeSettingsPort
import com.meshcoreone.android.feature.settings.device.support.TestScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceActionsStateHolderTest {
    private val scheduler = TestScheduler()
    private val service = FakeSettingsPort()
    private val holder = DeviceActionsStateHolder(scheduler.environment()) { service }

    @Test
    fun `reboot needs the confirmation dialog first`() {
        holder.requestReboot()
        assertTrue(holder.state.value.showingRebootAlert)
        assertTrue(service.calls.isEmpty())
        holder.confirmReboot()
        scheduler.runCurrent()
        assertEquals(listOf("reboot"), service.calls)
        assertFalse(holder.state.value.showingRebootAlert)
        assertFalse(holder.state.value.isRebooting)
    }

    @Test
    fun `the link-drop timeout is the expected outcome of a reboot`() {
        service.failNext("reboot", SettingsOperationTimeoutException())
        holder.confirmReboot()
        scheduler.runCurrent()
        assertNull(holder.state.value.errorMessage)
    }

    @Test
    fun `other reboot failures surface as an error`() {
        service.failNext("reboot", IllegalStateException("no link"))
        holder.confirmReboot()
        scheduler.runCurrent()
        assertEquals(UiText.Verbatim("no link"), holder.state.value.errorMessage)
        assertFalse(holder.state.value.isRebooting)
    }

    @Test
    fun `reboot without a service does nothing`() {
        DeviceActionsStateHolder(scheduler.environment()) { null }.confirmReboot()
        scheduler.runCurrent()
        assertTrue(service.calls.isEmpty())
    }
}
