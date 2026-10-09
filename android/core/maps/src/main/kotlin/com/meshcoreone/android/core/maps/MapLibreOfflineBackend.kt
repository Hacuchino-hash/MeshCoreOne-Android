// AndroidOnly: WP-312 MapLibre OfflineManager region lifecycle for an admitted style endpoint.
package com.meshcoreone.android.core.maps

import android.content.Context
import android.util.DisplayMetrics
import java.time.Instant
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

class MapLibreOfflineBackend(
    context: Context,
    private val styleUri: String,
    private val scope: CoroutineScope,
) : OfflineMapBackend {
    private val appContext = context.applicationContext
    private val manager: OfflineManager
    private val regions = mutableMapOf<UUID, OfflineRegion>()
    private val _packs = MutableStateFlow<List<OfflinePack>>(emptyList())
    override val packs: StateFlow<List<OfflinePack>> = _packs.asStateFlow()

    init {
        require(styleUri.startsWith("https://") || styleUri.startsWith("http://") || styleUri.startsWith("file://"))
        MapLibre.getInstance(appContext)
        manager = OfflineManager.getInstance(appContext)
        scope.launch { refresh() }
    }

    override suspend fun createAndStart(request: OfflinePackRequest) {
        val definition = OfflineTilePyramidRegionDefinition(
            styleUri,
            request.bounds.toLatLngBounds(),
            request.minZoom.toDouble(),
            request.maxZoom.toDouble(),
            appContext.resources.displayMetrics.density.coerceAtLeast(DisplayMetrics.DENSITY_DEFAULT / 160f),
        )
        val region = suspendCancellableCoroutine { continuation ->
            manager.createOfflineRegion(
                definition,
                request.toMetadata(),
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        if (continuation.isActive) continuation.resume(offlineRegion)
                        else offlineRegion.delete(ignoringDeleteCallback)
                    }

                    override fun onError(error: String) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                    }
                },
            )
        }
        synchronized(regions) { regions[request.id] = region }
        observe(request, region)
        publish(request.toPack(OfflinePackState.DOWNLOADING, 0f))
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    override suspend fun delete(packId: UUID) {
        val region = synchronized(regions) { regions[packId] } ?: return
        suspendCancellableCoroutine { continuation ->
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() {
                    synchronized(regions) { regions.remove(packId) }
                    _packs.update { current -> current.filterNot { it.id == packId } }
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                }
            })
        }
    }

    override suspend fun pause(packId: UUID) {
        val region = synchronized(regions) { regions[packId] } ?: return
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        mutate(packId) { it.copy(state = OfflinePackState.PAUSED) }
    }

    override suspend fun resume(packId: UUID) {
        val region = synchronized(regions) { regions[packId] } ?: return
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        mutate(packId) { it.copy(state = OfflinePackState.DOWNLOADING) }
    }

    private suspend fun refresh() {
        val listed = suspendCancellableCoroutine { continuation ->
            manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    if (continuation.isActive) continuation.resume(offlineRegions.orEmpty().toList())
                }

                override fun onError(error: String) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error))
                }
            })
        }
        val decoded = listed.mapNotNull { region ->
            decodeRequest(region.metadata)?.also { request ->
                synchronized(regions) { regions[request.id] = region }
                observe(request, region)
            }?.let { request -> request to region }
        }
        val initial = decoded.map { (request, region) ->
            val status = region.status()
            request.toPack(status.toPackState(), status.completedFraction())
        }
        _packs.value = initial.sortedBy { it.createdAt }
    }

    private fun observe(request: OfflinePackRequest, region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                publish(request.toPack(status.toPackState(), status.completedFraction()))
            }

            override fun onError(error: OfflineRegionError) {
                mutate(request.id) { it.copy(state = OfflinePackState.FAILED) }
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                mutate(request.id) { it.copy(state = OfflinePackState.FAILED) }
            }
        })
    }

    private fun publish(pack: OfflinePack) {
        _packs.update { current -> (current.filterNot { it.id == pack.id } + pack).sortedBy { it.createdAt } }
    }

    private fun mutate(id: UUID, transform: (OfflinePack) -> OfflinePack) {
        _packs.update { current -> current.map { if (it.id == id) transform(it) else it } }
    }

    private suspend fun OfflineRegion.status(): OfflineRegionStatus = suspendCancellableCoroutine { continuation ->
        getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
            override fun onStatus(status: OfflineRegionStatus?) {
                if (status != null && continuation.isActive) continuation.resume(status)
                else if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Missing offline status"))
            }

            override fun onError(error: String?) {
                if (continuation.isActive) continuation.resumeWithException(IllegalStateException(error ?: "Offline status failed"))
            }
        })
    }

    private fun OfflineRegionStatus.toPackState(): OfflinePackState = when {
        isComplete -> OfflinePackState.COMPLETE
        downloadState == OfflineRegion.STATE_ACTIVE -> OfflinePackState.DOWNLOADING
        else -> OfflinePackState.PAUSED
    }

    private fun OfflineRegionStatus.completedFraction(): Float = when {
        isComplete -> 1f
        !isRequiredResourceCountPrecise || requiredResourceCount <= 0L -> 0f
        else -> (completedResourceCount.toDouble() / requiredResourceCount.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    private fun OfflinePackRequest.toMetadata(): ByteArray = JSONObject()
        .put("schema", 1)
        .put("id", id.toString())
        .put("name", name)
        .put("layer", layer.name)
        .put("north", bounds.northeast.latitude)
        .put("east", bounds.northeast.longitude)
        .put("south", bounds.southwest.latitude)
        .put("west", bounds.southwest.longitude)
        .put("minZoom", minZoom)
        .put("maxZoom", maxZoom)
        .put("createdAt", createdAt.epochSecond)
        .toString()
        .toByteArray(Charsets.UTF_8)

    private fun OfflinePackRequest.toPack(state: OfflinePackState, fraction: Float) =
        OfflinePack(id, name, layer, bounds, minZoom, maxZoom, createdAt, state, fraction)

    private fun GeoBounds.toLatLngBounds(): LatLngBounds =
        LatLngBounds.from(northeast.latitude, northeast.longitude, southwest.latitude, southwest.longitude)

    private companion object {
        val ignoringDeleteCallback = object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() = Unit
            override fun onError(error: String) = Unit
        }

        fun decodeRequest(bytes: ByteArray): OfflinePackRequest? = runCatching {
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            require(json.getInt("schema") == 1)
            OfflinePackRequest(
                id = UUID.fromString(json.getString("id")),
                name = json.getString("name"),
                layer = OfflineLayer.valueOf(json.getString("layer")),
                bounds = GeoBounds(
                    southwest = GeoPoint(json.getDouble("south"), json.getDouble("west")),
                    northeast = GeoPoint(json.getDouble("north"), json.getDouble("east")),
                ),
                minZoom = json.getInt("minZoom"),
                maxZoom = json.getInt("maxZoom"),
                createdAt = Instant.ofEpochSecond(json.getLong("createdAt")),
            )
        }.getOrNull()
    }
}
