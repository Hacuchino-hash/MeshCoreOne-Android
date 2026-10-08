// AndroidOnly: WP-218 real android.location.Geocoder-backed Geocoder native adapter test.
// SDK31 exercises the owned blocking-call bridge; SDK37 exercises the native listener overload.
// Controlled native shadows expose cancellation and late callbacks without real geocoding I/O.
package com.meshcoreone.android.app.content

import android.location.Address
import android.os.Build
import android.location.Geocoder as AndroidGeocoder
import com.meshcoreone.android.core.services.content.GeoCoordinate
import com.meshcoreone.android.core.services.content.GeocoderError
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowGeocoder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 37])
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
        AndroidGeocoderAdapter(context, geocoderFactory = { geocoder }, dispatcher = Dispatchers.Unconfined).use { adapter ->
            val result = adapter.reverseGeocode(GeoCoordinate(37.7749, -122.4194), preferredLocale = Locale.US)
            assertEquals("US", result?.countryCode)
            assertEquals("California", result?.administrativeArea)
            assertEquals("San Francisco County", result?.subAdministrativeArea)
        }
    }

    @Test
    fun `reverseGeocode returns null when the platform finds no placemark`() = runTest {
        val geocoder = AndroidGeocoder(context, Locale.getDefault())
        shadowOf(geocoder).setFromLocation(emptyList())
        AndroidGeocoderAdapter(context, geocoderFactory = { geocoder }, dispatcher = Dispatchers.Unconfined).use { adapter ->
            assertNull(adapter.reverseGeocode(GeoCoordinate(0.0, 0.0), preferredLocale = null))
        }
    }

    @Test
    fun `closed geocoder fails explicitly and repeated teardown does not reopen it`() = runTest {
        val adapter = AndroidGeocoderAdapter(context)
        adapter.close()
        adapter.close()

        assertFailsWith<GeocoderError.Closed> {
            adapter.reverseGeocode(GeoCoordinate(1.0, 2.0), null)
        }
    }

    @Test
    fun `unavailable platform geocoder is a typed failure rather than a fake empty placemark`() = runTest {
        org.robolectric.shadows.ShadowGeocoder.setIsPresent(false)
        AndroidGeocoderAdapter(context).use { adapter ->
            assertFailsWith<GeocoderError.Unavailable> { adapter.reverseGeocode(GeoCoordinate(1.0, 2.0), null) }
        }
    }

    @Test
    fun `factory failure surfaces a typed cause and frees the next request`() = runTest {
        val cause = IllegalArgumentException("fixture failure")
        AndroidGeocoderAdapter(context, geocoderFactory = { throw cause }).use { adapter ->
            val first = assertFailsWith<GeocoderError.RequestFailed> {
                adapter.reverseGeocode(GeoCoordinate(1.0, 2.0), null)
            }
            assertTrue(generateSequence<Throwable>(first) { it.cause }.any { it === cause })
            assertFailsWith<GeocoderError.RequestFailed> {
                adapter.reverseGeocode(GeoCoordinate(1.0, 2.0), null)
            }
        }
    }

    @Config(shadows = [DeferredGeocoderShadow::class])
    @Test
    fun `real platform cancellation rejects stale callbacks and a successor receives only its own result`() = runTest {
        val geocoder = AndroidGeocoder(context, Locale.US)
        val queue = QueuedDispatcher()
        val address = Address(Locale.US).apply { countryCode = "US" }
        Shadow.extract<DeferredGeocoderShadow>(geocoder).addresses = listOf(address)
        AndroidGeocoderAdapter(context, geocoderFactory = { geocoder }, dispatcher = queue).use { adapter ->
            val first = async { adapter.reverseGeocode(GeoCoordinate(1.0, 2.0), Locale.US) }
            testScheduler.runCurrent()
            adapter.cancelGeocode()
            assertFailsWith<CancellationException> { first.await() }
            val next = async { adapter.reverseGeocode(GeoCoordinate(3.0, 4.0), Locale.US) }
            testScheduler.runCurrent()
            assertTrue(!next.isCompleted)
            if (Build.VERSION.SDK_INT >= 33) {
                val shadow = Shadow.extract<DeferredGeocoderShadow>(geocoder)
                shadow.listeners[0].onGeocode(mutableListOf(Address(Locale.US).apply { countryCode = "OLD" }))
                assertTrue(!next.isCompleted)
                shadow.listeners[1].onGeocode(mutableListOf(address))
                shadow.listeners[1].onError("late duplicate")
            } else {
                queue.runAll()
            }
            assertEquals("US", next.await()?.countryCode)
        }
    }

    @Config(shadows = [DeferredGeocoderShadow::class])
    @Test
    fun `an occupied geocoder rejects a second request and teardown cancels exactly the active one`() = runTest {
        val geocoder = AndroidGeocoder(context, Locale.US)
        val queue = QueuedDispatcher()
        val adapter = AndroidGeocoderAdapter(context, geocoderFactory = { geocoder }, dispatcher = queue)
        supervisorScope {
            val pending = async { adapter.reverseGeocode(GeoCoordinate(1.0, 2.0), null) }
            testScheduler.runCurrent()
            assertFailsWith<GeocoderError.RequestInProgress> {
                adapter.reverseGeocode(GeoCoordinate(3.0, 4.0), null)
            }
            adapter.close()
            assertFailsWith<CancellationException> { pending.await() }
        }
        queue.runAll()
        assertFailsWith<GeocoderError.Closed> { adapter.reverseGeocode(GeoCoordinate(1.0, 2.0), null) }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }
}

@Implements(AndroidGeocoder::class)
class DeferredGeocoderShadow {
    var addresses: List<Address> = emptyList()
    val listeners = mutableListOf<AndroidGeocoder.GeocodeListener>()
    @Implementation
    fun getFromLocation(latitude: Double, longitude: Double, maxResults: Int): List<Address> = addresses
    @Implementation(minSdk = 33)
    fun getFromLocation(latitude: Double, longitude: Double, maxResults: Int, listener: AndroidGeocoder.GeocodeListener) {
        listeners.add(listener)
    }

    companion object {
        @JvmStatic
        @Implementation
        fun isPresent(): Boolean = true
    }
}
