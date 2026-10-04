// PortedFrom: MeshCore/Sources/MeshCore/Session/OtherParamsConfig.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.protocol.config

import com.meshcoreone.android.core.protocol.model.SelfInfo

data class OtherParamsConfig(
    val manualAddContacts: Boolean = false,
    val telemetryModeBase: UByte = 0u,
    val telemetryModeLocation: UByte = 0u,
    val telemetryModeEnvironment: UByte = 0u,
    val advertisementLocationPolicy: UByte = 0u,
    val multiAcks: UByte = 0u,
) {
    constructor(info: SelfInfo) : this(
        info.manualAddContacts,
        info.telemetryModeBase,
        info.telemetryModeLocation,
        info.telemetryModeEnvironment,
        info.advertisementLocationPolicy,
        info.multiAcks,
    )
}
