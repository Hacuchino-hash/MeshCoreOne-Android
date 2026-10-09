// PortedFrom: MC1/Views/Map/MapViewModel.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.app.container

import com.meshcoreone.android.core.maps.ContactMapType
import com.meshcoreone.android.core.maps.GeoPoint
import com.meshcoreone.android.core.maps.MapMarker
import com.meshcoreone.android.core.maps.MapPinStyle
import com.meshcoreone.android.core.protocol.model.ContactType
import com.meshcoreone.android.feature.map.MapFeatureContent
import com.meshcoreone.android.feature.map.MapFeatureDataSource
import com.meshcoreone.android.feature.map.MapFeatureDependencies
import com.meshcoreone.android.feature.map.MapFeatureMarker

internal fun AppContainer.createMapFeatureDependencies(): MapFeatureDependencies =
    MapFeatureDependencies(
        dataSource = MapFeatureDataSource {
            val radioId = appState.currentRadioId ?: return@MapFeatureDataSource MapFeatureContent()
            val store = appState.offlineDataStore ?: return@MapFeatureDataSource MapFeatureContent()
            val contacts = store.fetchContacts(radioId)
            val contactKeys = contacts.mapTo(mutableSetOf()) { it.publicKey }
            val contactMarkers = contacts.filter { it.hasLocation }.map { contact ->
                MapFeatureMarker(
                    marker = MapMarker(
                        id = contact.id,
                        position = GeoPoint(contact.latitude, contact.longitude),
                        style = contact.type.pinStyle,
                        label = contact.nickname?.takeIf(String::isNotBlank) ?: contact.name,
                    ),
                    type = contact.type.mapType,
                    isFavorite = contact.isFavorite,
                    isDiscovered = false,
                )
            }
            val discoveredMarkers = store.fetchDiscoveredNodes(radioId)
                .filter { it.hasLocation && it.publicKey !in contactKeys }
                .map { node ->
                    MapFeatureMarker(
                        marker = MapMarker(
                            id = node.id,
                            position = GeoPoint(node.latitude, node.longitude),
                            style = node.nodeType.pinStyle,
                            label = node.name,
                        ),
                        type = node.nodeType.mapType,
                        isFavorite = false,
                        isDiscovered = true,
                    )
                }
            MapFeatureContent(markers = contactMarkers + discoveredMarkers)
        },
        offlineMaps = offlineMaps,
    )

private val ContactType.mapType: ContactMapType
    get() = when (this) {
        ContactType.CHAT -> ContactMapType.CHAT
        ContactType.REPEATER -> ContactMapType.REPEATER
        ContactType.ROOM -> ContactMapType.ROOM
    }

private val ContactType.pinStyle: MapPinStyle
    get() = when (this) {
        ContactType.CHAT -> MapPinStyle.CONTACT_CHAT
        ContactType.REPEATER -> MapPinStyle.CONTACT_REPEATER
        ContactType.ROOM -> MapPinStyle.CONTACT_ROOM
    }
