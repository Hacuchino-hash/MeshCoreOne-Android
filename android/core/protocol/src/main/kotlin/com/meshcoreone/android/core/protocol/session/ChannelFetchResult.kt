// PortedFrom: MeshCore/Sources/MeshCore/Session/MeshCoreSession+Channels.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.session

import com.meshcoreone.android.core.protocol.event.ChannelInfo
import com.meshcoreone.android.core.protocol.event.EventList

data class ChannelFetchResult(val received: EventList<ChannelInfo>, val missing: EventList<UByte>) {
    constructor(received: Collection<ChannelInfo>, missing: Collection<UByte>) : this(EventList(received), EventList(missing))
}
