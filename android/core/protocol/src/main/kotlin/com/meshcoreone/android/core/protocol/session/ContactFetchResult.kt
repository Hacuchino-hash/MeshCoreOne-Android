// PortedFrom: MeshCore/Sources/MeshCore/Session/ContactFetchResult.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.event.EventList
import com.meshcoreone.android.core.protocol.model.MeshContact

data class ContactFetchResult(val contacts: EventList<MeshContact>, val reportedTotal: Long?) {
    constructor(contacts: Collection<MeshContact>, reportedTotal: Long?) : this(EventList(contacts), reportedTotal)
}
