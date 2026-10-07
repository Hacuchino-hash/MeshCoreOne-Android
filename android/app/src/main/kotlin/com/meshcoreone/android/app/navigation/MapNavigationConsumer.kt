// PortedFrom: MC1/Views/Chats/ViewModel/ChatViewModel.swift@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation: Extracted map-callback forwarding only, not a ChatViewModel or completed chat feature.
package com.meshcoreone.android.app.navigation

enum class MapNavigationOutcome { FORWARDED, UNAVAILABLE }

class MapNavigationConsumer {
    var onNavigateToMap: ((MapFocusRequest) -> Unit)? = null

    fun navigateToMap(coordinate: MapFocusRequest): MapNavigationOutcome {
        val sink = onNavigateToMap ?: return MapNavigationOutcome.UNAVAILABLE
        sink(coordinate)
        return MapNavigationOutcome.FORWARDED
    }
}
