// PortedFrom: MC1Services/Sources/MC1Services/Models/RegionScopeSemantics.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Models/RegionSelection.swift@db14559b39d32322b06477c6ae676112f583db50
// RegionLabel is a neutral matched-result projection; WP-103 owns the protocol region resolver.
package com.meshcoreone.android.core.model

import java.math.BigInteger
import java.text.Collator
import java.util.Locale

sealed interface RegionLabel {
    data object None : RegionLabel
    data class Unique(val name: String) : RegionLabel
    data class Ambiguous(val names: SnapshotList<String>) : RegionLabel {
        constructor(names: Iterable<String>) : this(names.snapshot())
    }
}

data class RegionStorageFields(val regionScope: String?, val regionScopeMatches: SnapshotList<String>)

object RegionScopeSemantics {
    const val CHIP_NAME_SEPARATOR = " / "

    fun storageFields(match: RegionLabel, locale: Locale = Locale.getDefault()): RegionStorageFields {
        val names = when (match) {
            RegionLabel.None -> emptyList()
            is RegionLabel.Unique -> listOf(match.name)
            is RegionLabel.Ambiguous -> match.names
        }
        val filtered = filteredSortedNames(names, locale)
        return RegionStorageFields(filtered.singleOrNull(), filtered)
    }

    fun coalesce(scope: String?, matches: Iterable<String>, locale: Locale = Locale.getDefault()): RegionLabel {
        val filtered = filteredSortedNames(matches, locale)
        return when (filtered.size) {
            0 -> scope?.trim()?.takeIf { it.isNotEmpty() }?.let(RegionLabel::Unique) ?: RegionLabel.None
            1 -> RegionLabel.Unique(filtered[0])
            else -> RegionLabel.Ambiguous(filtered)
        }
    }

    fun chipLabel(match: RegionLabel): String? = when (match) {
        RegionLabel.None -> null
        is RegionLabel.Unique -> match.name
        is RegionLabel.Ambiguous -> match.names.joinToString(CHIP_NAME_SEPARATOR)
    }

    fun matchNames(match: RegionLabel): SnapshotList<String> = when (match) {
        RegionLabel.None -> SnapshotList.empty()
        is RegionLabel.Unique -> SnapshotList.of(match.name)
        is RegionLabel.Ambiguous -> match.names
    }

    private fun filteredSortedNames(names: Iterable<String>, locale: Locale): SnapshotList<String> {
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
        val tokens = Regex("[0-9]+|[^0-9]+")
        val comparator = Comparator<String> { first, second ->
            val left = tokens.findAll(first).map { it.value }.toList()
            val right = tokens.findAll(second).map { it.value }.toList()
            var result = 0
            for (index in 0 until minOf(left.size, right.size)) {
                val a = left[index]
                val b = right[index]
                result = if (a[0].isDigit() && b[0].isDigit()) BigInteger(a).compareTo(BigInteger(b))
                else collator.compare(a, b)
                if (result != 0) break
            }
            if (result == 0) left.size.compareTo(right.size) else result
        }
        return names.map(String::trim).filter(String::isNotEmpty).distinct().sortedWith(comparator).snapshot()
    }
}

data class RegionSelection(
    val countryCode: String,
    val source: Source,
    val administrativeAreaCode: String? = null,
    val countyKey: String? = null,
) {
    enum class Source(val rawValue: String) { LOCATION("location"), MANUAL("manual") }
    companion object {
        fun afterChoosingCountry(newCountry: String, current: RegionSelection?): RegionSelection? =
            if (current?.countryCode == newCountry) null else RegionSelection(newCountry, Source.MANUAL)
        fun afterChoosingSubdivision(newSubdivision: String, current: RegionSelection?): RegionSelection? =
            if (current == null || current.administrativeAreaCode == newSubdivision) null
            else RegionSelection(current.countryCode, Source.MANUAL, newSubdivision)
    }
}
