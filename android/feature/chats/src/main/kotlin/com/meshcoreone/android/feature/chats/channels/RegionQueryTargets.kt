// PortedFrom: MC1Services/Sources/MC1Services/Services/RegionDiscoveryService.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.chats.channels

import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DiscoveredNodeDTO
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.core.protocol.model.ContactType

/** One repeater the region query is sent to (the routing data the request needs). */
data class RegionQueryTarget(
    val publicKey: Bytes,
    val advertisedName: String,
    val outPathLength: UByte,
    val outPath: Bytes,
)

/** Builds the region query pool from the repeaters that answered the discover probe. */
object RegionQueryTargets {
    /**
     * Prefers contact records (they carry direct routing data) and fills in from the discovered-nodes
     * table. Non-contact responders are only included when [supportsAdHocRequest]: older firmware
     * rejects an anonymous request to a key it does not hold as a contact.
     */
    fun build(
        responders: Set<Bytes>,
        contacts: List<ContactDTO>,
        discoveredNodes: List<DiscoveredNodeDTO>,
        supportsAdHocRequest: Boolean,
    ): List<RegionQueryTarget> {
        val byKey = LinkedHashMap<String, RegionQueryTarget>()
        val responderKeys = responders.mapTo(HashSet()) { it.hexString }
        for (contact in contacts) {
            if (contact.type == ContactType.REPEATER && contact.publicKey.hexString in responderKeys) {
                byKey[contact.publicKey.hexString] =
                    RegionQueryTarget(contact.publicKey, contact.name, contact.outPathLength, contact.outPath)
            }
        }
        if (!supportsAdHocRequest) return byKey.values.toList()
        for (node in discoveredNodes) {
            val key = node.publicKey.hexString
            if (node.nodeType == ContactType.REPEATER && key in responderKeys && key !in byKey) {
                byKey[key] = RegionQueryTarget(node.publicKey, node.name, node.outPathLength, node.outPath)
            }
        }
        return byKey.values.toList()
    }
}
