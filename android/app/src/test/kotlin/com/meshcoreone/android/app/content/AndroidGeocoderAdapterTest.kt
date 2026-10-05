// AndroidOnly: WP-218 real android.location.Geocoder-backed Geocoder native adapter test.
// Runs at sdk=31 (below API 33's GeocodeListener overload), so these cases exercise the real
// `Dispatchers.IO`-wrapped blocking `getFromLocation(lat, lng, maxResults)` branch; Robolectric's
// ShadowGeocoder.setFromLocation(List<Address>) stubs the platform's own result, not this
// adapter's mapping logic, so the field-mapping assertions below are genuine.
package com.meshcoreone.android.app.content

import android.location.Address
import android.location.Geocoder as AndroidGeocoder
import com.meshcoreone.android.core.services.content.GeoCoordinate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class AndroidGeocoderAdapterTest {
    private val context = org.robolectric.RuntimeEnvironment.getApplication()

    @Test
    fun `reverseGeocode maps the first platform Address to a GeocodeResult`() = runTest {
        val address = Address(Locale.US).apply {
            countryCode = "US"
            adminArea = "California"
            subAdminArea = "San Francisco County"
        }
        val geocoder = AndroidGeocoder(context, Locale.US)
        shadowOf(geocoder).setFromLocation(listOf(address))
        val adapter = AndroidGeocoderAdapter(context, geocoderFactory = { geocoder })

        val result = adapter.reverseGeocode(GeoCoordinate(37.7749, -122.4194), preferredLocale = Locale.US)

        assertEquals("US", result?.countryCode)
        assertEquals("California", result?.administrativeArea)
        assertEquals("San Francisco County", result?.subAdministrativeArea)
    }

    @Test
    fun `reverseGeocode returns null when the platform finds no placemark`() = runTest {
        val geocoder = AndroidGeocoder(context, Locale.getDefault())
        shadowOf(geocoder).setFromLocation(emptyList())
        val adapter = AndroidGeocoderAdapter(context, geocoderFactory = { geocoder })

        val result = adapter.reverseGeocode(GeoCoordinate(0.0, 0.0), preferredLocale = null)

        assertNull(result)
    }

    @Test
    fun `cancelGeocode is a documented no-op consistent with the fresh-instance-per-call design`() {
        val adapter = AndroidGeocoderAdapter(context)

        // Must not throw; there is no in-flight geocoder instance to cancel (see the adapter's
        // file header disclosure).
        adapter.cancelGeocode()
    }
}
