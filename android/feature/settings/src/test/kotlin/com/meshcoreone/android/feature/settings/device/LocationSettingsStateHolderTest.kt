// AndroidOnly: WP-317 Behavior tests for the location section and the preset-location session (expectations follow LocationSettingsSection.swift / PresetLocationSession.swift).
package com.meshcoreone.android.feature.settings.device

import com.meshcoreone.android.core.contracts.domain.errors.SettingsServiceError
import com.meshcoreone.android.core.contracts.domain.errors.SettingsServiceException
import com.meshcoreone.android.core.l10n.generated.AppSettingsStrings
import com.meshcoreone.android.core.model.RegionSelection
import com.meshcoreone.android.core.ui.UiText
import com.meshcoreone.android.feature.settings.device.support.FakeConnection
import com.meshcoreone.android.feature.settings.device.support.FakeDevicePreferences
import com.meshcoreone.android.feature.settings.device.support.FakeLocation
import com.meshcoreone.android.feature.settings.device.support.FakeRegions
import com.meshcoreone.android.feature.settings.device.support.FakeSettingsPort
import com.meshcoreone.android.feature.settings.device.support.TestScheduler
import com.meshcoreone.android.feature.settings.device.support.device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LocationSettingsStateHolderTest {
    private val scheduler = TestScheduler()
    private val dev = device()
    private val connection = FakeConnection(dev)
    private val service = FakeSettingsPort().apply { gpsState = DeviceGpsState(isSupported = true, isEnabled = false) }
    private val prefs = FakeDevicePreferences()
    private val location = FakeLocation()
    private val holder = LocationSettingsStateHolder(scheduler.environment(), connection, { service }, prefs, location)
    private val state get() = holder.state.value

    private fun started(): LocationSettingsStateHolder { holder.start(); scheduler.runCurrent(); return holder }

    @Test
    fun `loads preferences and the radio's gps capability`() {
        prefs.auto[dev.id] = true
        started()
        assertTrue(state.didLoad && state.autoUpdateLocation && state.deviceHasGps)
        assertFalse(state.deviceGpsEnabled)
        assertEquals(GpsSource.PHONE, state.gpsSource)
    }

    @Test
    fun `an already enabled radio gps defaults the source to device once and refreshes device info`() {
        service.gpsState = DeviceGpsState(true, true)
        started()
        assertEquals(GpsSource.DEVICE, state.gpsSource)
        assertEquals(GpsSource.DEVICE, prefs.source[dev.id])
        assertTrue(service.calls.contains("refreshDeviceInfo"))
    }

    @Test
    fun `a failed gps read means the radio has no gps`() {
        service.failNext("getDeviceGPSState", IllegalStateException("x"))
        started()
        assertFalse(state.deviceHasGps)
    }

    @Test
    fun `startup reads wait for the sync gate`() {
        connection.startupFlow.value = false
        started()
        assertTrue(state.didLoad)
        assertFalse(service.calls.contains("getDeviceGPSState"))
        connection.startupFlow.value = true
        scheduler.runCurrent()
        assertTrue(service.calls.contains("getDeviceGPSState"))
    }

    @Test
    fun `sharing from a phone source writes the stored-location policy and sharing off writes none`() {
        started()
        holder.onShareToggled(true)
        scheduler.runCurrent()
        assertEquals("setOtherParamsVerified(auto=null,tel=null,policy=PREFS)", service.calls.last())
        connection.deviceFlow.value = dev.copy(advertLocationPolicy = 2u)
        scheduler.runCurrent()
        holder.onShareToggled(false)
        scheduler.runCurrent()
        assertEquals("setOtherParamsVerified(auto=null,tel=null,policy=NONE)", service.calls.last())
    }

    @Test
    fun `sharing with the radio gps live writes the share policy`() {
        service.gpsState = DeviceGpsState(true, true)
        prefs.auto[dev.id] = true
        started()
        holder.onShareToggled(true)
        scheduler.runCurrent()
        assertEquals("setOtherParamsVerified(auto=null,tel=null,policy=SHARE)", service.calls.last())
    }

    @Test
    fun `a share toggle that already matches the radio writes nothing`() {
        connection.deviceFlow.value = dev.copy(advertLocationPolicy = 2u)
        started()
        val before = service.calls.size
        holder.onShareToggled(true)
        scheduler.runCurrent()
        assertEquals(before, service.calls.size)
    }

    @Test
    fun `a failed share write reverts the switch`() {
        started()
        service.failNext("setOtherParamsVerified", SettingsServiceException(SettingsServiceError.InvalidResponse))
        holder.onShareToggled(true)
        scheduler.runCurrent()
        assertFalse(state.shareLocation)
        assertTrue(state.errorMessage != null)
        assertFalse(state.isSaving)
        service.failNext("setOtherParamsVerified", SettingsServiceException(SettingsServiceError.SendFailed))
        holder.onShareToggled(true)
        scheduler.runCurrent()
        assertFalse(state.shareLocation)
        assertTrue(holder.retryAlertState.value.isPresented)
    }

    @Test
    fun `phone auto update with a denied permission reverts and asks to open settings`() {
        started()
        location.authorizationFlow.value = LocationAuthorization.DENIED
        holder.onAutoUpdateToggled(true)
        assertFalse(state.autoUpdateLocation)
        assertTrue(state.showLocationDeniedAlert)
        assertNull(prefs.auto[dev.id])
    }

    @Test
    fun `phone auto update persists and requests permission`() {
        started()
        holder.onAutoUpdateToggled(true)
        assertTrue(state.autoUpdateLocation)
        assertEquals(true, prefs.auto[dev.id])
        assertEquals(1, location.permissionRequests)
    }

    @Test
    fun `device source auto update turns the radio gps on and a failure undoes the preference`() {
        prefs.source[dev.id] = GpsSource.DEVICE
        started()
        holder.onAutoUpdateToggled(true)
        scheduler.runCurrent()
        assertTrue(service.calls.contains("setDeviceGPSEnabledVerified(true)"))
        assertTrue(state.deviceGpsEnabled)
    }

    @Test
    fun `a failed radio gps enable undoes the auto update preference`() {
        prefs.source[dev.id] = GpsSource.DEVICE
        started()
        service.failNext("setDeviceGPSEnabledVerified", SettingsServiceException(SettingsServiceError.InvalidResponse))
        holder.onAutoUpdateToggled(true)
        scheduler.runCurrent()
        assertFalse(state.autoUpdateLocation)
        assertEquals(false, prefs.auto[dev.id])
        assertFalse(state.deviceGpsEnabled)
    }

    @Test
    fun `switching to the phone source turns the radio gps off and a denied permission keeps the old source`() {
        service.gpsState = DeviceGpsState(true, true)
        started()
        location.authorizationFlow.value = LocationAuthorization.RESTRICTED
        holder.onGpsSourceSelected(GpsSource.PHONE)
        assertEquals(GpsSource.DEVICE, state.gpsSource)
        assertTrue(state.showLocationDeniedAlert)
        location.authorizationFlow.value = LocationAuthorization.AUTHORIZED
        holder.onGpsSourceSelected(GpsSource.PHONE)
        scheduler.runCurrent()
        assertEquals(GpsSource.PHONE, prefs.source[dev.id])
        assertTrue(service.calls.contains("setDeviceGPSEnabledVerified(false)"))
    }

    @Test
    fun `switching the source to device with auto update on enables gps and reverts on failure`() {
        prefs.auto[dev.id] = true
        started()
        service.failNext("setDeviceGPSEnabledVerified", SettingsServiceException(SettingsServiceError.InvalidResponse))
        holder.onGpsSourceSelected(GpsSource.DEVICE)
        scheduler.runCurrent()
        assertEquals(GpsSource.PHONE, state.gpsSource)
        assertEquals(GpsSource.PHONE, prefs.source[dev.id])
    }

    @Test
    fun `turning the radio gps off while it drove auto update stops auto update and downgrades the share policy`() {
        service.gpsState = DeviceGpsState(true, true)
        prefs.auto[dev.id] = true
        connection.deviceFlow.value = dev.copy(advertLocationPolicy = 1u)
        started()
        assertTrue(state.shareLocation && state.autoUpdateLocation)
        holder.onDeviceGpsToggled(false)
        scheduler.runCurrent()
        assertFalse(state.autoUpdateLocation)
        assertEquals(false, prefs.auto[dev.id])
        assertEquals("setOtherParamsVerified(auto=null,tel=null,policy=PREFS)", service.calls.last())
    }

    @Test
    fun `polling refreshes device info every three seconds until a fix arrives`() {
        service.gpsState = DeviceGpsState(true, true)
        prefs.auto[dev.id] = true
        started()
        service.calls.clear()
        scheduler.advanceBy(3.seconds)
        assertEquals(1, service.calls.count { it == "refreshDeviceInfo" })
        scheduler.advanceBy(3.seconds)
        assertEquals(2, service.calls.count { it == "refreshDeviceInfo" })
        connection.deviceFlow.value = dev.copy(latitude = 40.0, longitude = -100.0)
        scheduler.advanceBy(3.seconds)
        scheduler.advanceBy(3.seconds)
        assertEquals(2, service.calls.count { it == "refreshDeviceInfo" })
    }

    @Test
    fun `location row detail and enablement`() {
        started()
        assertFalse(state.isLocationSet)
        assertTrue(state.setLocationEnabled)
        connection.deviceFlow.value = dev.copy(latitude = 1.0)
        scheduler.runCurrent()
        assertTrue(state.isLocationSet)
        holder.onAutoUpdateToggled(true)
        assertFalse(state.setLocationEnabled)
    }
}

class PresetLocationSessionTest {
    private val scheduler = TestScheduler()
    private val location = FakeLocation()
    private val regions = FakeRegions()
    private var lookupResult: RegionSelection? = RegionSelection("US", RegionSelection.Source.LOCATION, administrativeAreaCode = "US-CA")
    private var lookups = 0
    private val session = PresetLocationSession(scheduler.environment(), location, regions, { lookups++; lookupResult })
    private val state get() = session.state.value

    @Test
    fun `appear resolves only when authorized and not manual and commits the result`() {
        session.resolveOnAppear()
        scheduler.runCurrent()
        assertEquals(RegionSelection("US", RegionSelection.Source.LOCATION, administrativeAreaCode = "US-CA"), regions.flow.value)
        assertFalse(state.isResolving)
    }

    @Test
    fun `appear leaves a manual choice alone and unauthorized does nothing`() {
        regions.flow.value = RegionSelection("PT", RegionSelection.Source.MANUAL)
        session.resolveOnAppear()
        location.authorizationFlow.value = LocationAuthorization.DENIED
        regions.flow.value = null
        session.resolveOnAppear()
        scheduler.runCurrent()
        assertEquals(0, lookups)
    }

    @Test
    fun `use my location replaces a manual choice and a miss reports the failure copy only for the user`() {
        regions.flow.value = RegionSelection("PT", RegionSelection.Source.MANUAL)
        lookupResult = null
        session.useMyLocation()
        scheduler.runCurrent()
        assertEquals(UiText.Resource(AppSettingsStrings.radioPresetLocationUseMyLocationFailure), state.errorMessage)
        assertEquals(RegionSelection("PT", RegionSelection.Source.MANUAL), regions.flow.value)
        session.dismissError()
        lookupResult = RegionSelection("US", RegionSelection.Source.LOCATION)
        session.useMyLocation()
        scheduler.runCurrent()
        assertEquals("US", regions.flow.value?.countryCode)
    }

    @Test
    fun `a denied permission asks to open settings and resolves nothing`() {
        location.authorizationFlow.value = LocationAuthorization.DENIED
        session.useMyLocation()
        scheduler.runCurrent()
        assertTrue(state.showOpenSettingsAlert)
        assertEquals(0, lookups)
    }

    @Test
    fun `an undetermined permission waits for the answer then resolves or opens settings`() {
        location.authorizationFlow.value = LocationAuthorization.NOT_DETERMINED
        location.onRequestCurrentLocation = { location.authorizationFlow.value = LocationAuthorization.AUTHORIZED }
        session.useMyLocation()
        scheduler.runCurrent()
        assertEquals(1, location.currentLocationRequests)
        assertEquals(1, lookups)
        location.authorizationFlow.value = LocationAuthorization.NOT_DETERMINED
        location.onRequestCurrentLocation = { location.authorizationFlow.value = LocationAuthorization.DENIED }
        session.useMyLocation()
        scheduler.runCurrent()
        assertTrue(state.showOpenSettingsAlert)
        assertEquals(1, lookups)
        location.authorizationFlow.value = LocationAuthorization.NOT_DETERMINED
        location.onRequestCurrentLocation = { throw IllegalStateException("timeout") }
        session.dismissOpenSettingsAlert()
        session.useMyLocation()
        scheduler.runCurrent()
        assertFalse(state.showOpenSettingsAlert)
    }

    @Test
    fun `a request already in flight blocks resolving after the ten second wait`() {
        location.requestingFlow.value = true
        session.useMyLocation()
        scheduler.advanceBy(11.seconds)
        assertEquals(0, lookups)
        assertNull(state.errorMessage)
        assertFalse(state.isResolving)
    }

    @Test
    fun `the slot freeing within the wait lets the lookup run`() {
        location.requestingFlow.value = true
        session.useMyLocation()
        scheduler.advanceBy(1.seconds)
        location.requestingFlow.value = false
        scheduler.advanceBy(1.seconds)
        assertEquals(1, lookups)
    }

    @Test
    fun `an automatic request is dropped while one is in flight but a user request queues behind it`() {
        location.requestingFlow.value = true
        session.useMyLocation()
        scheduler.runCurrent()
        session.resolveOnAppear()
        session.useMyLocation()
        location.requestingFlow.value = false
        scheduler.advanceBy(1.seconds)
        // The user request that was in flight resolves first, then the queued one.
        assertEquals(2, lookups)
        assertFalse(state.isResolving)
    }
}
