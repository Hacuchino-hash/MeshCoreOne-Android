// PortedFrom: MC1/State/NavigationCoordinator.swift@db14559b39d32322b06477c6ae676112f583db50
// AndroidOnly: WP-302 Read-only adapter; the injected process store is never created or closed by navigation.
package com.meshcoreone.android.app.navigation

import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreProtocol
import com.meshcoreone.android.core.model.RadioId

class PersistenceNavigationLookup(private val store: PersistenceStoreProtocol) : NavigationLookup {
    override suspend fun contact(key: EntityKey) = store.fetchContact(key)
    override suspend fun channel(radioId: RadioId, index: UByte) = store.fetchChannel(radioId, index)
    override suspend fun room(key: EntityKey) = store.fetchRemoteNodeSession(key)
}
