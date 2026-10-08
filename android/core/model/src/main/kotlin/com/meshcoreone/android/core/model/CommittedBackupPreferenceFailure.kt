// PortedFrom: MC1Services/Sources/MC1Services/Services/AppBackupService.swift@db14559b39d32322b06477c6ae676112f583db50
// Native adaptation: a committed database receipt crosses the model boundary; only missing preferences may be completed.
package com.meshcoreone.android.core.model

data class CommittedBackupCounts(
    val inserted: Long,
    val merged: Long,
    val skipped: Long,
    val dropped: Long,
) {
    init { require(listOf(inserted, merged, skipped, dropped).all { it >= 0 }) }
}

class CommittedBackupReceipt(
    counts: Map<String, CommittedBackupCounts>,
    val userDefaultsRestored: Boolean,
    channelSlotsAffectedByImport: Map<RadioId, Set<UByte>>,
) {
    val counts: SnapshotMap<String, CommittedBackupCounts> = counts.snapshotMap()
    val channelSlotsAffectedByImport: SnapshotMap<RadioId, SnapshotSet<UByte>> =
        channelSlotsAffectedByImport.mapValues { it.value.snapshotSet() }.snapshotMap()

    init { require(this.counts.keys == BackupContract.modelArrayKeys.toSet()) { "A committed receipt must include all twelve source kinds" } }

    override fun equals(other: Any?): Boolean = other is CommittedBackupReceipt &&
        counts == other.counts && userDefaultsRestored == other.userDefaultsRestored &&
        channelSlotsAffectedByImport == other.channelSlotsAffectedByImport
    override fun hashCode(): Int = 31 * (31 * counts.hashCode() + userDefaultsRestored.hashCode()) +
        channelSlotsAffectedByImport.hashCode()
}

interface CommittedBackupPreferenceFailure {
    val committedReceipt: CommittedBackupReceipt
    val preferenceFailure: Throwable
}
