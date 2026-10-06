// AndroidOnly: WP-218 real LocationManager-backed LocationProducing native adapter test.
// Robolectric's ShadowLocationManager.getCurrentLocation(...) shadows exactly the same 4-arg
// overload (`provider, CancellationSignal, Executor, Consumer<Location>`) this adapter calls, so
// `simulateLocation(Location)` genuinely exercises the real suspendCancellableCoroutine wiring
// below -- not a hand-mocked substitute for it.
package com.meshcoreone.android.app.content

import android.location.Location
import android.location.LocationManager
import com.meshcoreone.android.core.services.content.LocationAuthorizationStatus
import com.meshcoreone.android.core.services.content.LocationServiceError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class LocationManagerLocationProducingTest {
    private val context = org.robolectric.RuntimeEnvironment.getApplication()
    private val locationManager = context.getSystemService(LocationManager::class.java)!!

    @Test
    fun `requestLocation resolves to the real coordinate delivered by the platform fix`() = runTest {
        shadowOf(locationManager).setProviderEnabled(LocationManager.FUSED_PROVIDER, true)
        shadowOf(context as android.app.Application).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val adapter = LocationManagerLocationProducing(context)

        val deferred = async { adapter.requestLocation() }
        // `runTest`'s StandardTestDispatcher only *queues* `async`'s body rather than running it
        // inline, so without draining the scheduler here, `simulateLocation` below would fire
        // before `requestLocation()` ever reaches its real `getCurrentLocation` registration --
        // the fix would be delivered to no pending consumer and `deferred.await()` would hang.
        // `runCurrent()` genuinely executes the adapter up to its real suspension point first.
        testScheduler.runCurrent()
        val fix = Location(LocationManager.FUSED_PROVIDER).apply {
            latitude = 37.7749
            longitude = -122.4194
        }
        shadowOf(locationManager).simulateLocation(fix)
        val coordinate = deferred.await()

        assertEquals(37.7749, coordinate.latitude)
        assertEquals(-122.4194, coordinate.longitude)
    }

    @Test
    fun `requestLocation fails with a typed error when the provider is disabled`() = runTest {
        shadowOf(locationManager).setProviderEnabled(LocationManager.FUSED_PROVIDER, false)
        shadowOf(context as android.app.Application).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val adapter = LocationManagerLocationProducing(context)

        assertFailsWith<LocationServiceError.RequestFailed> { adapter.requestLocation() }
    }

    @Test
    fun `requestLocation fails with NotAuthorized when no location permission is granted`() = runTest {
        shadowOf(locationManager).setProviderEnabled(LocationManager.FUSED_PROVIDER, true)
        val adapter = LocationManagerLocationProducing(context)

        val error = assertFailsWith<LocationServiceError.NotAuthorized> { adapter.requestLocation() }

        assertEquals(LocationAuthorizationStatus.DENIED, error.status)
        assertEquals(LocationAuthorizationStatus.DENIED, adapter.authorizationStatus.value)
    }

    @Test
    fun `authorizationStatus reports NOT_DETERMINED when no location permission is granted`() {
        val adapter = LocationManagerLocationProducing(context)

        assertEquals(LocationAuthorizationStatus.NOT_DETERMINED, adapter.authorizationStatus.value)
    }

    @Test
    fun `authorizationStatus reports AUTHORIZED_ALWAYS once fine location is granted`() {
        // Simulates the real OS grant state the way Robolectric's shadow Application allows a
        // test to represent an already-granted permission, without going through an Activity
        // prompt -- distinct from this adapter's own disclosed requestPermissionIfNeeded gap.
        shadowOf(context as android.app.Application).grantPermissions(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val adapter = LocationManagerLocationProducing(context)

        assertEquals(LocationAuthorizationStatus.AUTHORIZED_ALWAYS, adapter.authorizationStatus.value)
    }

    @Test
    fun `reportPermissionResult(false) surfaces DENIED when still not granted`() {
        val adapter = LocationManagerLocationProducing(context)

        adapter.reportPermissionResult(granted = false)

        assertEquals(LocationAuthorizationStatus.DENIED, adapter.authorizationStatus.value)
    }
}
