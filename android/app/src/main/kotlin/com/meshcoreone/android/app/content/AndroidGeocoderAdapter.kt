// PortedFrom: MC1/Services/Geocoder.swift@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation (many-to-many port): core:services/content.Geocoder is the pure-JVM typed
// contract (android.location.Geocoder is not a pure-JVM API); this file is the real Android
// runtime reverse-geocoding producer the coordinator admitted into app/content. This header
// names the actual production Swift source this file's behavior derives from; it does NOT
// claim this file carries any of WP-218's 154 original test-assertion credit -- that credit is
// claimed by core:services' own Geocoder role/GeocodeResult port and RegionResolver's tests
// (pure-JVM, run against a fake Geocoder), which this adapter supplements rather than
// duplicates.
//
// Android's `android.location.Geocoder.getFromLocation(lat, lng, maxResults): List<Address>?`
// (the only form available below API 33) is a *blocking* call -- unlike `CLGeocoder`'s inherently
// async `reverseGeocodeLocation`, so it is run on `Dispatchers.IO` here, never the caller's
// dispatcher. API 33+ additionally exposes a non-blocking `getFromLocation(..., GeocodeListener)`
// overload, used when available. Mirrors the source's own doc-comment-disclosed "a fresh
// CLGeocoder per call would make [cancelGeocode] a no-op": this adapter likewise constructs a
// new `android.location.Geocoder` per [reverseGeocode] call (Android's Geocoder constructor does
// no network I/O itself, so this is cheap), so [cancelGeocode] is correspondingly a documented
// no-op here too -- not a silently-dropped feature, the exact same disclosed shape as the pure
// role's own doc comment already describes.
package com.meshcoreone.android.app.content

import android.content.Context
import android.location.Address
import android.location.Geocoder as AndroidGeocoder
import android.os.Build
import com.meshcoreone.android.core.services.content.GeoCoordinate
import com.meshcoreone.android.core.services.content.Geocoder
import com.meshcoreone.android.core.services.content.GeocodeResult
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Real [Geocoder] producer using [android.location.Geocoder]. Field mapping to [GeocodeResult]
 * mirrors the source's `CLPlacemark` field selection exactly: `countryCode` <- `Address
 * .countryCode`, `administrativeArea` <- `Address.adminArea`, `subAdministrativeArea` <-
 * `Address.subAdminArea`.
 */
class AndroidGeocoderAdapter(
    private val context: Context,
    /**
     * Constructs the underlying [AndroidGeocoder] for one [reverseGeocode] call. Defaults to a
     * real `android.location.Geocoder(context, locale)` in production; overridable only so tests
     * can hand back a Robolectric-shadowed instance they've pre-seeded via
     * `ShadowGeocoder.setFromLocation` (an instance-scoped shadow method, not a static one) --
     * this does not change production behavior, which always takes this default.
     */
    private val geocoderFactory: (Locale) -> AndroidGeocoder = { locale -> AndroidGeocoder(context, locale) },
) : Geocoder {
    override suspend fun reverseGeocode(coordinate: GeoCoordinate, preferredLocale: Locale?): GeocodeResult? {
        val locale = preferredLocale ?: Locale.getDefault()
        if (!AndroidGeocoder.isPresent()) return null
        val geocoder = geocoderFactory(locale)
        val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocodeAsync(geocoder, coordinate)
        } else {
            geocodeBlocking(geocoder, coordinate)
        }
        return addresses?.firstOrNull()?.let { address ->
            GeocodeResult(
                countryCode = address.countryCode,
                administrativeArea = address.adminArea,
                subAdministrativeArea = address.subAdminArea,
            )
        }
    }

    /** Documented no-op: see the file header's "fresh Geocoder per call" disclosure. */
    override fun cancelGeocode() {
        // Intentionally empty.
    }

    private suspend fun geocodeBlocking(geocoder: AndroidGeocoder, coordinate: GeoCoordinate): List<Address>? =
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(coordinate.latitude, coordinate.longitude, 1)
            } catch (error: java.io.IOException) {
                null
            }
        }

    private suspend fun geocodeAsync(geocoder: AndroidGeocoder, coordinate: GeoCoordinate): List<Address>? =
        suspendCancellableCoroutine { continuation ->
            geocoder.getFromLocation(
                coordinate.latitude,
                coordinate.longitude,
                1,
                object : AndroidGeocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resumeWith(Result.success(addresses))
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resumeWith(Result.success(null))
                    }
                },
            )
        }
}
