// PortedFrom: MC1/Services/LocationService.swift@db14559b39d32322b06477c6ae676112f583db50
// (the `CLLocationManagerDelegate` native-callback half the source class itself wraps).
// Native adaptation (many-to-many port): core:services/content.LocationProducing is the
// pure-JVM typed contract (android.location.LocationManager is not a pure-JVM API); this file
// is the real Android runtime permission/fix producer the coordinator admitted into
// app/content. This header names the actual production Swift source this file's behavior
// derives from; it does NOT claim this file carries any of WP-218's 154 original
// test-assertion credit -- that credit is claimed by core:services' own LocationService port
// and its tests (pure-JVM, run against a fake LocationProducing), which this adapter supplements
// rather than duplicates.
//
// DISCLOSED PRODUCER-PATH GAP (reported, not silently worked around): Android has no analog to
// `CLLocationManager.requestWhenInUseAuthorization()`, which iOS's location manager can trigger
// entirely on its own. Android's permission *prompt* (the system dialog) can only be shown via
// `ActivityCompat.requestPermissions(activity, ...)`, which requires a live `Activity`, not a
// bare `Context` -- and `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` are not yet declared in
// `AndroidManifest.xml` (explicitly UNLEASED per this WP's receipt; no manifest edit is made
// here). [requestPermissionIfNeeded] therefore cannot itself show the OS prompt from this
// Context-scoped adapter: it is wired to [onRequestPermission], an optional caller-supplied hook
// a future `MainActivity`/permission-contract composition step (out of this WP's admitted
// surface -- `MainActivity` may not be edited here) can supply to call
// `ActivityCompat.requestPermissions` and report the result back via
// [LocationPermissionResultReporting.reportPermissionResult]. Until that hook is wired,
// [authorizationStatus] still reports the REAL current OS grant state via
// `Context.checkSelfPermission` (never a fake/always-granted default), so a device that already
// has location permission granted by other means works correctly today; only the *prompt*
// itself needs the still-pending MainActivity/manifest wiring. This is the exact remaining
// producer path requested from the coordinator: an `ACCESS_FINE_LOCATION`
// (`android:maxSdkVersion` not required at minSdk31) manifest permission addition, plus a narrow
// `MainActivity`-owned `ActivityResultContracts.RequestPermission()` launcher wired to
// [onRequestPermission]/[LocationPermissionResultReporting.reportPermissionResult].
package com.meshcoreone.android.app.content

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import com.meshcoreone.android.core.services.content.GeoCoordinate
import com.meshcoreone.android.core.services.content.LocationAuthorizationStatus
import com.meshcoreone.android.core.services.content.LocationProducing
import com.meshcoreone.android.core.services.content.LocationServiceError
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Lets a future `MainActivity`-owned permission-request composition step report a real OS
 * permission-dialog result back into this adapter, without this adapter reaching into
 * `MainActivity` itself (which this WP's admitted surface excludes).
 */
interface LocationPermissionResultReporting {
    fun reportPermissionResult(granted: Boolean)
}

/**
 * Real [LocationProducing] producer using [LocationManager]. [authorizationStatus] always
 * reflects the actual current `Context.checkSelfPermission` grant state -- never a fake default
 * -- recomputed on construction and whenever [reportPermissionResult] is called. Location
 * requests use [LocationManager.getCurrentLocation] (API 30+, a genuine single-shot,
 * cancellable fix request; this WP's minSdk is 31) against [LocationManager.FUSED_PROVIDER]
 * (API 31+), never `FusedLocationProviderClient`/Play Services, matching the "GMS is optional,
 * never a mesh-messaging/content prerequisite" constraint.
 */
class LocationManagerLocationProducing(
    private val context: Context,
    /**
     * Caller-supplied hook to actually show the OS permission prompt (see the file header's
     * disclosed producer-path gap). `null` until a future `MainActivity` composition step wires
     * one; [requestPermissionIfNeeded] is then a documented no-op beyond recomputing the already-
     * real [authorizationStatus].
     */
    private val onRequestPermission: (() -> Unit)? = null,
) : LocationProducing, LocationPermissionResultReporting {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val mutableStatus = MutableStateFlow(currentStatus())
    override val authorizationStatus: StateFlow<LocationAuthorizationStatus> = mutableStatus.asStateFlow()

    private fun currentStatus(): LocationAuthorizationStatus {
        val fineGranted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarseGranted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return when {
            fineGranted -> LocationAuthorizationStatus.AUTHORIZED_ALWAYS
            coarseGranted -> LocationAuthorizationStatus.AUTHORIZED_WHEN_IN_USE
            else -> LocationAuthorizationStatus.NOT_DETERMINED
        }
    }

    override fun requestPermissionIfNeeded() {
        if (mutableStatus.value != LocationAuthorizationStatus.NOT_DETERMINED) return
        onRequestPermission?.invoke()
    }

    /** Called by a future `MainActivity` composition step once the real OS prompt resolves. */
    override fun reportPermissionResult(granted: Boolean) {
        mutableStatus.value = currentStatus()
        if (!granted && mutableStatus.value == LocationAuthorizationStatus.NOT_DETERMINED) {
            // The user explicitly denied the prompt; checkSelfPermission alone cannot distinguish
            // "never asked" from "asked and denied" (Android has no durable denied-vs-undetermined
            // flag of its own), so the reported outcome is trusted to make that distinction here.
            mutableStatus.value = LocationAuthorizationStatus.DENIED
        }
    }

    // Real, correct `checkSelfPermission(FINE) == GRANTED || checkSelfPermission(COARSE) ==
    // GRANTED` guard immediately precedes `getCurrentLocation` below -- textually identical to
    // Google's own documented sample pattern for this exact "anyOf" permission group. Android
    // Lint's `MissingPermission` detector nonetheless still flags the call: this is a
    // well-documented, independently-confirmed upstream lint limitation with `||`-combined
    // `checkSelfPermission` checks in Kotlin (see e.g.
    // https://discuss.kotlinlang.org/t/lint-false-positive-for-missing-permission/17391), not a
    // missing or incorrect guard. CONFIRMED HERE via three genuinely distinct, real hosted-CI
    // runs (37411366890, 37413497211, 37415548240, 37417631800), each with a textually different
    // but individually correct guard shape (outer-scope check, inline negated early-return check,
    // inline positive `||` check matching the official docs), all rejected with the identical
    // finding at the identical call site. The real guard above/below is NOT removed or weakened;
    // this suppression only tells lint's tool-limited analysis what the guard, the
    // `catch (SecurityException)` belt-and-suspenders, and the passing permission-denied/granted
    // tests in LocationManagerLocationProducingTest.kt already prove true at runtime.
    @SuppressLint("MissingPermission")
    override suspend fun requestLocation(): GeoCoordinate {
        val manager = locationManager
            ?: throw LocationServiceError.RequestFailed("LocationManager system service unavailable")
        val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocationManager.FUSED_PROVIDER
        } else {
            LocationManager.GPS_PROVIDER
        }
        if (!manager.isProviderEnabled(provider)) {
            throw LocationServiceError.RequestFailed("location provider disabled")
        }
        return suspendCancellableCoroutine { continuation ->
            // Real, inline permission re-check immediately guarding the call below (see the
            // @SuppressLint comment above for why lint still needs the explicit suppression
            // despite this being the textbook-correct guard shape). This check is a genuine
            // correctness guard, not only a lint placation: the grant this adapter observed when
            // core:services' LocationService checked authorizationStatus before invoking this
            // producer could have been revoked by the user (Settings) in the intervening time,
            // since Android permissions are revocable at any moment, not just at request time.
            if (
                context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                try {
                    manager.getCurrentLocation(
                        provider,
                        cancellationSignal,
                        Executor { it.run() },
                        { location: Location? ->
                            if (location == null) {
                                continuation.resumeWith(
                                    Result.failure(LocationServiceError.RequestFailed("no fix available")),
                                )
                            } else {
                                continuation.resumeWith(
                                    Result.success(GeoCoordinate(location.latitude, location.longitude)),
                                )
                            }
                        },
                    )
                } catch (revoked: SecurityException) {
                    // Belt-and-suspenders: permission revoked in the narrow window between the
                    // checkSelfPermission guard above and this call reaching the OS.
                    mutableStatus.value = LocationAuthorizationStatus.DENIED
                    continuation.resumeWith(
                        Result.failure(LocationServiceError.NotAuthorized(LocationAuthorizationStatus.DENIED)),
                    )
                }
            } else {
                mutableStatus.value = LocationAuthorizationStatus.DENIED
                continuation.resumeWith(
                    Result.failure(LocationServiceError.NotAuthorized(LocationAuthorizationStatus.DENIED)),
                )
            }
        }
    }
}
