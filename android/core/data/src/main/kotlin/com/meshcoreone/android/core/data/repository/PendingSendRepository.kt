// PortedFrom: MC1Services/Sources/MC1Services/Protocols/Persistence/MessagePersisting.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Services/PersistenceStore+PendingSends.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.data.repository

import com.meshcoreone.android.core.contracts.domain.*
import com.meshcoreone.android.core.database.*
import com.meshcoreone.android.core.model.*
import java.util.UUID

internal class PendingSendRepository(private val context: RoomRepositoryContext) : PendingSendPersisting {
    override suspend fun upsertPendingSend(dto: PendingSendDTO) = context.write("upsertPendingSend") {
        database.pendingSends().upsert(dto.toEntity())
        save()
    }

    override suspend fun insertPendingSendAssigningSequence(dto: PendingSendDTO): Long =
        context.write("insertPendingSendAssigningSequence") {
            val next = checkedIncrement(database.pendingSends().maximumSequence(dto.radioId.value) ?: 0)
            database.pendingSends().upsert(dto.copy(sequence = next).toEntity())
            save()
            next
        }

    override suspend fun replacePendingSendForRetry(messageID: UUID, dto: PendingSendDTO): Long =
        context.write("replacePendingSendForRetry") {
            database.pendingSends().deleteForMessage(dto.radioId.value, messageID)
            database.messages().setStatusUnlessDelivered(dto.radioId.value, messageID, MessageStatus.PENDING.rawValue)
            val next = checkedIncrement(database.pendingSends().maximumSequence(dto.radioId.value) ?: 0)
            database.pendingSends().upsert(dto.copy(sequence = next).toEntity())
            save()
            next
        }

    override suspend fun fetchPendingSends(radioId: RadioId): SnapshotList<PendingSendDTO> =
        context.read("fetchPendingSends") { projectPending(database.pendingSends().forRadio(radioId.value)) }

    override suspend fun fetchPendingSendsForMessage(key: EntityKey): SnapshotList<PendingSendDTO> =
        context.read("fetchPendingSendsForMessage") {
            projectPending(database.pendingSends().forMessage(key.radioId.value, key.id))
        }

    private fun projectPending(rows: List<PendingSendEntity>): SnapshotList<PendingSendDTO> =
        rows.mapNotNull { row ->
            if (PendingSendKind.fromRawValue(row.kindRawValue) == null) {
                context.reporter.report(
                    "unknownPendingSendKind",
                    PersistenceStoreException(PersistenceStoreError.InvalidData,
                        DatabaseValueException("pendingSend.kind", "Unknown raw value ${row.kindRawValue}")),
                )
                null
            } else {
                row.toDTO()
            }
        }.snapshot()

    override suspend fun deletePendingSend(key: EntityKey) = context.write("deletePendingSend") {
        if (database.pendingSends().delete(key.radioId.value, key.id) > 0) save()
    }

    override suspend fun deletePendingSendsForMessage(key: EntityKey) =
        context.write("deletePendingSendsForMessage") {
            if (database.pendingSends().deleteForMessage(key.radioId.value, key.id) > 0) save()
        }

    override suspend fun hasPendingSend(key: EntityKey): Boolean = context.read("hasPendingSend") {
        database.pendingSends().forMessage(key.radioId.value, key.id).isNotEmpty()
    }

    override suspend fun incrementPendingSendAttemptCount(key: EntityKey): Long? =
        context.write("incrementPendingSendAttemptCount") {
            val row = database.pendingSends().forMessage(key.radioId.value, key.id).firstOrNull()
                ?: return@write null
            val next = checkedIncrement(row.attemptCount ?: 0)
            database.pendingSends().setAttemptCount(key.radioId.value, row.id, next)
            save()
            next
        }

    override suspend fun purgeOrphanPendingSends(): Long = context.write("purgeOrphanPendingSends") {
        val rows = database.pendingSends().orphanedRadioRows()
        for (row in rows) database.pendingSends().delete(row.radioId, row.id)
        if (rows.isNotEmpty()) save()
        rows.size.toLong()
    }

    override suspend fun purgeLegacyAttemptCountRows(): Long = context.write("purgeLegacyAttemptCountRows") {
        val rows = database.pendingSends().legacyAttemptRows()
        for (row in rows) database.pendingSends().delete(row.radioId, row.id)
        if (rows.isNotEmpty()) save()
        rows.size.toLong()
    }
}
