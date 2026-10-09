// PortedFrom: MC1/Views/PathEditing/HopPickerSource.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1/Views/PathEditing/AddHopPickerView.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.feature.nodes.path

import com.meshcoreone.android.core.l10n.R
import com.meshcoreone.android.core.model.ContactDTO
import com.meshcoreone.android.core.model.DiscoveredNodeDTO
import com.meshcoreone.android.core.model.RepeaterResolvable
import com.meshcoreone.android.core.protocol.bytes.Bytes
import com.meshcoreone.android.feature.nodes.deps.NodesMessage
import com.meshcoreone.android.feature.nodes.text.SwiftText
import java.util.Locale

/**
 * Data source for the shared Add-Hop picker: the contact path editor (hop-capped) and the trace path
 * builder (uncapped) both drive the same picker through it.
 */
interface HopPickerSource {
    val availableRepeaters: List<ContactDTO>
    val availableRooms: List<ContactDTO>
    val discoveredRepeaters: List<DiscoveredNodeDTO>
    val recentPublicKeys: List<Bytes>

    /** Hops currently in the path being built. */
    val currentHopCount: Int

    /** Maximum hops the path can hold, or null when unlimited (trace). */
    val hopLimit: Int?

    /** No further hops can be added; a path with no limit is never full. */
    val isPathFull: Boolean get() = hopLimit?.let { currentHopCount >= it } ?: false

    fun appendHop(node: RepeaterResolvable)
    fun addCodes(input: String): CodeInputResult
    fun classifyCodes(input: String): List<HopCodeClassification>

    /** Adopt a hash size inferred from a bulk paste; fixed-width sources ignore it. */
    fun adoptHashSize(forPastedCodes: String) = Unit
}

/** The five picker sections, built once per render so rows and the empty state agree. */
data class PickerResults(
    val recent: List<PickerNode> = emptyList(),
    val favorites: List<PickerNode> = emptyList(),
    val contacts: List<PickerNode> = emptyList(),
    val discovered: List<PickerNode> = emptyList(),
    val rooms: List<PickerNode> = emptyList(),
) {
    val isEmpty: Boolean get() = recent.isEmpty() && favorites.isEmpty() && contacts.isEmpty() && discovered.isEmpty() && rooms.isEmpty()
}

/** Non-visual logic of `AddHopPickerView`. */
object AddHopPicker {
    /** A comma in the query switches the picker to bulk code entry. */
    fun isBulkMode(searchText: String): Boolean = "," in SwiftText.characters(searchText)

    /** Shared subtitle and row-tap announcement text. */
    fun bannerText(source: HopPickerSource, intent: AddHopIntent): NodesMessage {
        if (source.isPathFull) return NodesMessage.res(R.string.l10n_app_contacts_contacts_pathedit_maxhops_reached)
        return when (intent) {
            AddHopIntent.APPEND -> NodesMessage.res(R.string.l10n_app_contacts_contacts_pathedit_positionappend, source.currentHopCount + 1)
        }
    }

    /** Bulk-add button label: the addable codes, or the empty label when none. */
    fun bulkAddLabel(classifications: List<HopCodeClassification>): NodesMessage {
        val addable = classifications.filter { it.willBeAdded }.map { it.code }
        return if (addable.isEmpty()) NodesMessage.res(R.string.l10n_app_contacts_contacts_pathedit_bulkadd_empty)
        else NodesMessage.res(R.string.l10n_app_contacts_contacts_pathedit_bulkadd_action, addable.joinToString(", "))
    }

    /** Empty-state copy for the active filter. */
    fun emptyStateTitle(filter: AddHopFilter): Int = when (filter) {
        AddHopFilter.FAVORITES -> R.string.l10n_app_contacts_contacts_pathedit_nofavorites_title
        AddHopFilter.RECENT -> R.string.l10n_app_contacts_contacts_pathedit_norecent_title
        AddHopFilter.ALL, AddHopFilter.DISCOVERED -> R.string.l10n_app_contacts_contacts_pathedit_norepeaters_title
    }

    /**
     * Section results for [filter] and [searchText]. [sessionRecentKeys] is the recents snapshot taken
     * at presentation so a just-added row keeps its place. Cross-section de-duplication applies only in
     * [AddHopFilter.ALL], where every section is visible.
     */
    fun buildResults(
        source: HopPickerSource,
        filter: AddHopFilter,
        sessionRecentKeys: List<Bytes>,
        searchText: String,
        locale: Locale,
    ): PickerResults {
        val unfiltered = filter == AddHopFilter.ALL
        val recentKeys = if (unfiltered) sessionRecentKeys.toSet() else emptySet()
        val contactKeys = if (unfiltered) source.availableRepeaters.mapTo(HashSet()) { it.publicKey } else emptySet()
        val byName = SwiftText.localizedComparator(locale, caseInsensitive = true)
        fun contacts(nodes: List<ContactDTO>) = HopNodeMatching.filtered(
            nodes.sortedWith { left, right -> byName.compare(left.displayName, right.displayName) }.map { PickerNode.Contact(it) },
            searchText,
        )
        val showsRecent = unfiltered || filter == AddHopFilter.RECENT
        val showsFavorites = unfiltered || filter == AddHopFilter.FAVORITES
        val showsDiscovered = unfiltered || filter == AddHopFilter.DISCOVERED
        return PickerResults(
            recent = if (showsRecent) recentResults(source, sessionRecentKeys, searchText) else emptyList(),
            favorites = if (showsFavorites) contacts(source.availableRepeaters.filter { it.isFavorite && it.publicKey !in recentKeys }) else emptyList(),
            contacts = if (unfiltered) contacts(source.availableRepeaters.filter { !it.isFavorite && it.publicKey !in recentKeys }) else emptyList(),
            discovered = if (showsDiscovered) {
                HopNodeMatching.filtered(
                    source.discoveredRepeaters
                        .filter { it.publicKey !in contactKeys && it.publicKey !in recentKeys }
                        .sortedWith { left, right -> byName.compare(left.resolvableName, right.resolvableName) }
                        .map { PickerNode.Discovered(it) },
                    searchText,
                )
            } else {
                emptyList()
            },
            rooms = if (unfiltered) contacts(source.availableRooms.filter { it.publicKey !in recentKeys }) else emptyList(),
        )
    }

    /** Recent keys resolved against contacts, then discovered nodes, preserving LRU order. */
    private fun recentResults(source: HopPickerSource, keys: List<Bytes>, searchText: String): List<PickerNode> {
        val resolved = keys.mapNotNull { key ->
            source.availableRepeaters.firstOrNull { it.publicKey == key }?.let { PickerNode.Contact(it) }
                ?: source.discoveredRepeaters.firstOrNull { it.publicKey == key }?.let { PickerNode.Discovered(it) }
        }
        return HopNodeMatching.filtered(resolved, searchText)
    }
}
