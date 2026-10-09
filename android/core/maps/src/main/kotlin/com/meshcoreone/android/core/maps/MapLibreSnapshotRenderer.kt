// AndroidOnly: WP-312 MapLibre Native snapshot adapter with provider attribution enabled.
package com.meshcoreone.android.core.maps

import android.content.Context
import android.graphics.Bitmap
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.snapshotter.MapSnapshotter

class MapLibreSnapshotRenderer(
    context: Context,
    private val widthPixels: Int,
    private val heightPixels: Int,
) : MapSnapshotRenderer<Bitmap> {
    private val appContext = context.applicationContext

    init {
        require(widthPixels > 0 && heightPixels > 0)
        MapLibre.getInstance(appContext)
    }

    override suspend fun render(request: MapSnapshotRequest): Bitmap? {
        if (request.isOffline) return null
        return suspendCancellableCoroutine { continuation ->
        val options = MapSnapshotter.Options(widthPixels, heightPixels)
            .withStyle(MapLibreOpenFreeMap.STYLE_URI)
            .withCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(request.latitude, request.longitude))
                    .zoom(12.0)
                    .build(),
            )
            .withLogo(false)
            .withAttribution(true)
        val snapshotter = MapSnapshotter(appContext, options)
        continuation.invokeOnCancellation { snapshotter.cancel() }
        snapshotter.start(
            { snapshot -> if (continuation.isActive) continuation.resume(snapshot.bitmap) },
            { _ -> if (continuation.isActive) continuation.resume(null) },
        )
        }
    }
}
