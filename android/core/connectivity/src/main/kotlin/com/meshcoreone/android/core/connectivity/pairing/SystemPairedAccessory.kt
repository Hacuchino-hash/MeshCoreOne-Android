// PortedFrom: MC1/State/SystemPairedAccessory.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/State/SystemPairingSetupPrompt.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.connectivity.pairing

import java.util.UUID

/** A companion association that has no MeshCore One `Device` row. */
data class SystemPairedAccessory(val id: UUID, val name: String)

/** Forget prompt for companion associations that have no `Device` row; identity is per prompt. */
class SystemPairingSetupPrompt(
    val accessories: List<SystemPairedAccessory>,
    val id: UUID = UUID.randomUUID(),
) {
    override fun equals(other: Any?): Boolean =
        other is SystemPairingSetupPrompt && id == other.id && accessories == other.accessories
    override fun hashCode(): Int = 31 * id.hashCode() + accessories.hashCode()
}
