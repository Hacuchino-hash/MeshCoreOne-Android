// AndroidOnly: WP-317 Behavior tests for the contacts (auto-add), telemetry and direct-message sections (expectations follow the Swift sections).
package com.meshcoreone.android.feature.settings.device

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.contracts.domain.errors.SettingsServiceError
import com.meshcoreone.android.core.contracts.domain.errors.SettingsServiceException
import com.meshcoreone.android.core.l10n.generated.AppSettingsStrings
import com.meshcoreone.android.core.model.AutoAddMode
import com.meshcoreone.android.feature.settings.device.support.FakeConnection
import com.meshcoreone.android.feature.settings.device.support.FakeSettingsPort
import com.meshcoreone.android.feature.settings.device.support.TestScheduler
import com.meshcoreone.android.feature.settings.device.support.device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ContactsSettingsStateHolderTest {
    private val scheduler = TestScheduler()
    private val connection = FakeConnection(device(firmwareVersionString = "v1.14.0", manualAddContacts = true))
    private val service = FakeSettingsPort()
    private val holder = ContactsSettingsStateHolder(scheduler.environment(), connection, { service })
    private val state get() = holder.state.value

    private fun started(): ContactsSettingsStateHolder { holder.start(); scheduler.runCurrent(); return holder }

    @Test
    fun `new firmware offers selected types and loads the device's mode`() {
        started()
        assertEquals(AutoAddMode.MANUAL, state.autoAddMode)
        assertEquals(AutoAddMode.entries, state.availableModes)
        assertFalse(state.settingsModified)
        assertFalse(state.canApply)
    }

    @Test
    fun `old firmware offers manual and all only and maps the legacy flag`() {
        connection.deviceFlow.value = device(firmwareVersionString = "v1.11.0", manualAddContacts = false)
        started()
        assertEquals(listOf(AutoAddMode.MANUAL, AutoAddMode.ALL), state.availableModes)
        assertEquals(AutoAddMode.ALL, state.autoAddMode)
        assertFalse(state.supportsAutoAddConfig)
    }

    @Test
    fun `selected types writes the manual flag then the config bitmask with the hop limit`() {
        started()
        holder.onModeSelected(AutoAddMode.SELECTED_TYPES)
        holder.onContactsToggled(true)
        holder.onRoomServersToggled(true)
        holder.onOverwriteOldestToggled(true)
        holder.onMaxHopsSelected(3u)
        assertTrue(state.canApply)
        holder.apply()
        scheduler.runCurrent()
        // overwrite 0x01 + contacts 0x02 + room servers 0x08
        assertEquals(listOf("setOtherParamsVerified(auto=false,tel=null,policy=null)", "setAutoAddConfigVerified(11,3)"), service.calls)
        assertTrue(state.showSuccess)
        scheduler.advanceBy(1500.milliseconds)
        assertFalse(state.showSuccess)
    }

    @Test
    fun `all mode clears the manual flag and ignores the type toggles`() {
        started()
        holder.onContactsToggled(true)
        holder.onModeSelected(AutoAddMode.ALL)
        holder.apply()
        scheduler.runCurrent()
        assertEquals("setOtherParamsVerified(auto=true,tel=null,policy=null)", service.calls[0])
        assertEquals("setAutoAddConfigVerified(0,0)", service.calls[1])
    }

    @Test
    fun `old firmware writes only the manual flag`() {
        connection.deviceFlow.value = device(firmwareVersionString = "v1.11.0", manualAddContacts = true)
        started()
        holder.onModeSelected(AutoAddMode.ALL)
        holder.apply()
        scheduler.runCurrent()
        assertEquals(listOf("setOtherParamsVerified(auto=true,tel=null,policy=null)"), service.calls)
    }

    @Test
    fun `a failure reloads the device values and shows the error or the retry alert`() {
        started()
        holder.onModeSelected(AutoAddMode.ALL)
        service.failNext("setOtherParamsVerified", SettingsServiceException(SettingsServiceError.SendFailed))
        holder.apply()
        scheduler.runCurrent()
        assertEquals(AutoAddMode.MANUAL, state.autoAddMode)
        assertTrue(holder.retryAlertState.value.isPresented)
        holder.onModeSelected(AutoAddMode.ALL)
        service.failNext("setOtherParamsVerified", SettingsServiceException(SettingsServiceError.InvalidResponse))
        holder.apply()
        scheduler.runCurrent()
        assertTrue(state.errorMessage != null)
        assertFalse(state.isApplying)
    }

    @Test
    fun `footer and hop menu follow the mode`() {
        started()
        assertEquals(listOf(AppSettingsStrings.nodesAutoAddModeManualDescription), state.footerIds)
        holder.onModeSelected(AutoAddMode.ALL)
        holder.onMaxHopsSelected(2u)
        assertTrue(state.showsMaxHops)
        assertEquals(listOf(AppSettingsStrings.nodesAutoAddModeAllDescription, AppSettingsStrings.nodesMaxHopsFooterActive), state.footerIds)
        assertEquals(listOf<UByte>(0u, 1u, 2u, 3u, 4u, 5u, 6u, 7u), MaxHopsOptions.map { it.value })
        assertEquals(listOf<Int?>(null, null, null, 2, 3, 4, 5, 6), MaxHopsOptions.map { it.hops })
    }

    @Test
    fun `apply is blocked while disconnected and while already applying`() {
        started()
        holder.onModeSelected(AutoAddMode.ALL)
        connection.stateFlow.value = DeviceConnectionState.CONNECTED
        scheduler.runCurrent()
        assertFalse(state.canApply)
    }
}

class TelemetrySettingsStateHolderTest {
    private val scheduler = TestScheduler()
    private val connection = FakeConnection(device(telemetryBase = 0u))
    private val service = FakeSettingsPort()
    private val holder = TelemetrySettingsStateHolder(scheduler.environment(), connection, { service })
    private val state get() = holder.state.value

    private fun started(): TelemetrySettingsStateHolder { holder.start(); scheduler.runCurrent(); return holder }

    @Test
    fun `enabling telemetry writes everyone and detail switches appear only once it is on`() {
        started()
        assertFalse(state.showsDetails)
        holder.onTelemetryToggled(true)
        scheduler.runCurrent()
        assertEquals("setOtherParamsVerified(auto=null,tel=2/0/0,policy=null)", service.calls.last())
        connection.deviceFlow.value = device(telemetryBase = 2u)
        scheduler.runCurrent()
        assertTrue(state.showsDetails)
    }

    @Test
    fun `with trusted-only active enabling a switch keeps trusted-only`() {
        connection.deviceFlow.value = device(telemetryBase = 1u)
        started()
        assertTrue(state.filterByTrusted && state.showsManageTrusted)
        holder.onLocationToggled(true)
        scheduler.runCurrent()
        assertEquals("setOtherParamsVerified(auto=null,tel=1/1/0,policy=null)", service.calls.last())
    }

    @Test
    fun `disabling telemetry writes off for the base only`() {
        connection.deviceFlow.value = device(telemetryBase = 2u, telemetryLoc = 2u)
        started()
        holder.onTelemetryToggled(false)
        scheduler.runCurrent()
        assertEquals("setOtherParamsVerified(auto=null,tel=0/2/0,policy=null)", service.calls.last())
    }

    @Test
    fun `the trusted-only switch rewrites only the enabled modes`() {
        connection.deviceFlow.value = device(telemetryBase = 2u, telemetryLoc = 0u, telemetryEnv = 2u)
        started()
        holder.onFilterByTrustedToggled(true)
        scheduler.runCurrent()
        assertEquals("setOtherParamsVerified(auto=null,tel=1/0/1,policy=null)", service.calls.last())
        connection.deviceFlow.value = device(telemetryBase = 1u, telemetryLoc = 0u, telemetryEnv = 1u)
        scheduler.runCurrent()
        holder.onFilterByTrustedToggled(false)
        scheduler.runCurrent()
        assertEquals("setOtherParamsVerified(auto=null,tel=2/0/2,policy=null)", service.calls.last())
    }

    @Test
    fun `failures route to the retry alert or the error and always release saving`() {
        started()
        service.failNext("setOtherParamsVerified", SettingsServiceException(SettingsServiceError.NotConnected))
        holder.onTelemetryToggled(true)
        scheduler.runCurrent()
        assertTrue(holder.retryAlertState.value.isPresented)
        assertFalse(state.isSaving)
        service.failNext("setOtherParamsVerified", SettingsServiceException(SettingsServiceError.InvalidResponse))
        holder.onTelemetryToggled(true)
        scheduler.runCurrent()
        assertTrue(state.errorMessage != null)
    }

    @Test
    fun `nothing is written without a device or a service`() {
        val orphan = TelemetrySettingsStateHolder(scheduler.environment(), FakeConnection(null), { service })
        orphan.start(); scheduler.runCurrent()
        orphan.onTelemetryToggled(true)
        scheduler.runCurrent()
        assertTrue(service.calls.isEmpty())
    }
}

class DirectMessagesStateHolderTest {
    private val scheduler = TestScheduler()
    private val connection = FakeConnection(device())
    private val service = FakeSettingsPort()
    private val holder = DirectMessagesStateHolder(scheduler.environment(), connection, { service })

    @Test
    fun `acknowledgments are the stored byte plus one and picking writes the byte`() {
        holder.start(); scheduler.runCurrent()
        assertEquals(3, holder.state.value.acknowledgments)
        holder.onAcknowledgmentsSelected(1)
        scheduler.runCurrent()
        assertEquals("setOtherParamsVerified(auto=null,tel=null,policy=null,acks=0)", service.calls.single())
        assertNull(holder.state.value.errorMessage)
    }

    @Test
    fun `only one or two acknowledgments are valid`() {
        holder.start(); scheduler.runCurrent()
        for (bad in listOf(0, 3)) {
            try { holder.onAcknowledgmentsSelected(bad); error("accepted $bad") } catch (expected: IllegalArgumentException) { }
        }
    }
}
