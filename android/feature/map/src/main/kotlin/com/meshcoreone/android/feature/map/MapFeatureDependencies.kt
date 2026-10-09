// AndroidOnly: WP-312 App-bound immutable map data seam; feature code never constructs persistence services.
package com.meshcoreone.android.feature.map

import com.meshcoreone.android.core.maps.ContactMapType
import com.meshcoreone.android.core.maps.MapLine
import com.meshcoreone.android.core.maps.MapMarker
import com.meshcoreone.android.core.maps.OfflineMapController

data class MapFeatureMarker(
    val marker: MapMarker,
    val type: ContactMapType,
    val isFavorite: Boolean,
    val isDiscovered: Boolean,
)

data class MapFeatureContent(
    val markers: List<MapFeatureMarker> = emptyList(),
    val lines: List<MapLine> = emptyList(),
)

fun interface MapFeatureDataSource {
    suspend fun load(): MapFeatureContent
}

data class MapFeatureDependencies(
    val dataSource: MapFeatureDataSource,
    val offlineMaps: OfflineMapController,
)
