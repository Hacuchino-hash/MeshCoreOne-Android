// AndroidOnly: WP-303 Region catalog, region port and preset-apply outcome mapping against fakes.
package com.meshcoreone.android.app.onboarding

import com.meshcoreone.android.core.contracts.domain.DeviceConnectionState
import com.meshcoreone.android.core.model.RegionSelection
import com.meshcoreone.android.core.services.device.SettingsServiceError
import com.meshcoreone.android.core.services.device.SettingsServiceException
import com.meshcoreone.android.core.ui.UiText
import com.meshcoreone.android.feature.onboarding.AdministrativeAreaKind
import com.meshcoreone.android.feature.onboarding.OnboardingPresetOutcome as Outcome
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingRegionPresetAdapterTest {
    private val catalog = ServicesRegionCatalog { Locale.US }
    private val us = RegionSelection("US", RegionSelection.Source.MANUAL, "US-CA")

    private fun TestScope.presetPort(radio: FakeRadio) =
        AppOnboardingPresetPort(radio, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
    private fun anyPresetId() = catalog.presets(us).first().id

    @Test fun `catalog lists countries, subdivisions and region presets sorted by name`() {
        assertTrue(catalog.countries().any { it.code == "US" && it.displayName == "United States" })
        assertTrue(catalog.showsSubdivisionPicker("US"))
        assertTrue(catalog.subdivisions("US").any { it.code == "US-CA" })
        assertEquals(AdministrativeAreaKind.PROVINCE, catalog.administrativeAreaKind("CA"))
        assertEquals(AdministrativeAreaKind.STATE, catalog.administrativeAreaKind("US"))
        val names = catalog.presets(us).map { it.name }
        assertTrue(names.isNotEmpty())
        assertEquals(names.sortedWith(java.text.Collator.getInstance(Locale.US)), names)
        assertTrue(catalog.presets(null).isNotEmpty())
    }

    @Test fun `region port stores through persist and resolver failures are null but cancellation propagates`() = runTest {
        var stored: RegionSelection? = null
        val selection = MutableStateFlow<RegionSelection?>(null)
        val port = AppOnboardingRegionPort(catalog, selection, { stored = it }, { us })
        port.setSelection(us)
        assertEquals(us, stored)
        assertEquals(us, port.resolveRegion())
        assertNull(AppOnboardingRegionPort(catalog, selection, {}, null).resolveRegion())
        assertNull(AppOnboardingRegionPort(catalog, selection, {}, { error("geocoder") }).resolveRegion())
        try {
            AppOnboardingRegionPort(catalog, selection, {}, { throw CancellationException("x") }).resolveRegion()
            fail("cancellation must propagate")
        } catch (expected: CancellationException) { }
    }

    @Test fun `applied preset reaches the radio and canApply follows the connection`() = runTest {
        val radio = FakeRadio()
        val port = presetPort(radio)
        assertTrue(port.canApply.value)
        assertEquals(Outcome.Applied, port.apply(anyPresetId()))
        assertEquals(anyPresetId(), radio.applied.single().id)
        radio.state.value = DeviceConnectionState.DISCONNECTED
        assertFalse(port.canApply.value)
    }

    @Test fun `demo radio and unknown preset do not touch the radio`() = runTest {
        val demo = FakeRadio(demo = true)
        assertEquals(Outcome.NoRadioToConfigure, presetPort(demo).apply(anyPresetId()))
        val radio = FakeRadio()
        assertTrue(presetPort(radio).apply("no-such-preset") is Outcome.Failed)
        assertTrue(radio.applied.isEmpty())
    }

    @Test fun `settings errors map to not connected, retryable and failed`() = runTest {
        val radio = FakeRadio()
        val port = presetPort(radio)
        radio.failure = SettingsServiceException(SettingsServiceError.NotConnected)
        assertEquals(Outcome.NotConnected, port.apply(anyPresetId()))
        radio.failure = SettingsServiceException(SettingsServiceError.SendFailed)
        assertTrue(port.apply(anyPresetId()) is Outcome.Retryable)
        radio.failure = SettingsServiceException(SettingsServiceError.InvalidResponse)
        assertTrue(port.apply(anyPresetId()) is Outcome.Failed)
        radio.failure = IllegalStateException("boom")
        assertEquals(Outcome.Failed(UiText.Verbatim("boom")), port.apply(anyPresetId()))
        radio.failure = CancellationException("stop")
        try { port.apply(anyPresetId()); fail("cancellation must propagate") } catch (expected: CancellationException) { }
    }
}
