// PortedFrom: MC1Services/Sources/MC1Services/Services/PersistenceStore.swift@db14559b39d32322b06477c6ae676112f583db50
// PortedFrom: MC1Services/Sources/MC1Services/Services/PersistenceStore+Diagnostics.swift@db14559b39d32322b06477c6ae676112f583db50
package com.meshcoreone.android.core.data.repository

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.withTransaction
import com.meshcoreone.android.core.contracts.domain.EntityKey
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreError
import com.meshcoreone.android.core.contracts.domain.PersistenceStoreException
import com.meshcoreone.android.core.contracts.domain.RxLogRetention
import com.meshcoreone.android.core.database.DatabaseValueException
import com.meshcoreone.android.core.database.MeshCoreDatabase
import com.meshcoreone.android.core.database.RxLogEntryEntity
import com.meshcoreone.android.core.model.RadioId
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface RepositoryIssueReporter {
    fun report(operation: String, failure: PersistenceStoreException)
}

object AndroidRepositoryIssueReporter : RepositoryIssueReporter {
    override fun report(operation: String, failure: PersistenceStoreException) {
        Log.e("PersistenceStore", "$operation: ${failure.error.javaClass.simpleName}")
    }
}

internal class RepositoryTransaction(
    val database: MeshCoreDatabase,
    val clock: Clock,
    pending: Map<EntityKey, RxLogEntryEntity>,
) {
    val pendingRx = LinkedHashMap(pending)
    var saves = false
        private set
    private val committed = mutableListOf<() -> Unit>()

    fun save() { saves = true }
    fun afterCommit(action: () -> Unit) { committed += action }
    fun complete() { committed.forEach { it() } }

    suspend fun rxRows(radioId: RadioId): List<RxLogEntryEntity> {
        val rows = database.rxLogs().newest(radioId.value, -1).associateByTo(LinkedHashMap()) { it.id }
        for ((key, row) in pendingRx) if (key.radioId == radioId) rows[row.id] = row
        return rows.values.toList()
    }

    suspend fun updateRx(row: RxLogEntryEntity) {
        val key = EntityKey(RadioId(row.radioId), row.id)
        if (key in pendingRx) pendingRx[key] = row else database.rxLogs().upsert(row)
    }
}

internal class RoomRepositoryContext(
    val database: MeshCoreDatabase,
    val clock: Clock,
    parentScope: CoroutineScope,
    val reporter: RepositoryIssueReporter,
) {
    private val mutex = Mutex()
    private val flushOwner = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + flushOwner)
    private val pendingRx = LinkedHashMap<EntityKey, RxLogEntryEntity>()
    private val rxCounts = LinkedHashMap<RadioId, Long>()
    private var rxInsertsSinceFlush = 0L
    private var flushTask: Job? = null
    private val closedSignal = CompletableDeferred<Unit>()
    @Volatile private var closed = false
    internal var rxInitiatedSaveCount = 0L
        private set

    suspend fun <T> read(operation: String, block: suspend RepositoryTransaction.() -> T): T =
        access(operation, writing = false) {
            mutex.withLock {
                ensureOpen()
                database.withTransaction { RepositoryTransaction(database, clock, pendingRx).block() }
            }
        }

    suspend fun <T> write(
        operation: String,
        flushBeforeRollback: Boolean = false,
        block: suspend RepositoryTransaction.() -> T,
    ): T = access(operation, writing = true) {
        mutex.withLock {
            ensureOpen()
            if (flushBeforeRollback) flushRxLocked()
            val transaction = RepositoryTransaction(database, clock, pendingRx)
            val result = database.withTransaction {
                val value = transaction.block()
                if (transaction.saves) database.rxLogs().upsert(transaction.pendingRx.values.toList())
                value
            }
            if (transaction.saves) pendingRx.clear()
            transaction.complete()
            result
        }
    }

    suspend fun <T> guardedWrite(
        operation: String,
        unchanged: T,
        guard: suspend RepositoryTransaction.() -> Boolean,
        block: suspend RepositoryTransaction.() -> T,
    ): T = access(operation, writing = true) {
        mutex.withLock {
            ensureOpen()
            val eligible = database.withTransaction { RepositoryTransaction(database, clock, pendingRx).guard() }
            if (!eligible) return@withLock unchanged
            flushRxLocked()
            val transaction = RepositoryTransaction(database, clock, pendingRx)
            val result = database.withTransaction {
                if (!transaction.guard()) return@withTransaction unchanged
                val value = transaction.block()
                if (transaction.saves) database.rxLogs().upsert(transaction.pendingRx.values.toList())
                value
            }
            if (transaction.saves) pendingRx.clear()
            transaction.complete()
            result
        }
    }

    suspend fun saveRx(row: RxLogEntryEntity) = access("saveRxLogEntry", writing = true) {
        mutex.withLock {
            ensureOpen()
            val radio = RadioId(row.radioId)
            val count = rxCounts[radio] ?: database.withTransaction {
                RepositoryTransaction(database, clock, pendingRx).rxRows(radio).size.toLong()
            }
            val nextCount = checkedIncrement(count)
            val nextDirty = checkedIncrement(rxInsertsSinceFlush)
            rxCounts[radio] = nextCount
            pendingRx[EntityKey(radio, row.id)] = row
            rxInsertsSinceFlush = nextDirty
            if (rxInsertsSinceFlush >= RxLogRetention.BATCH_SIZE) flushRxLocked() else scheduleFlush()
        }
    }

    suspend fun flushRx() = access("flushPendingRxLogEntries", writing = true) {
        mutex.withLock { ensureOpen(); flushRxLocked() }
    }

    suspend fun close() {
        access("close", writing = true) {
            mutex.withLock {
                if (!closed) {
                    flushRxLocked()
                    closed = true
                    closedSignal.complete(Unit)
                    flushTask?.cancel()
                    flushTask = null
                    flushOwner.cancel()
                }
            }
        }
    }

    fun forgetRxCount(radioId: RadioId) { rxCounts.remove(radioId) }
    fun setRxCount(radioId: RadioId, count: Long) { rxCounts[radioId] = count }

    fun <Row, Value> observe(
        operation: String,
        rows: Flow<List<Row>>,
        projection: (List<Row>) -> Value,
    ): Flow<Value> = channelFlow {
        ensureOpen()
        val collector = launch {
            rows.collect { values ->
                ensureOpen()
                send(access(operation, writing = false) { projection(values) })
            }
        }
        val completion = launch {
            closedSignal.await()
            collector.cancel()
            channel.close()
        }
        awaitClose { collector.cancel(); completion.cancel() }
    }

    private fun ensureOpen() {
        if (closed) throw PersistenceStoreException(
            PersistenceStoreError.FetchFailed("PersistenceStore is closed"),
            IllegalStateException("Closed process repository"),
        )
    }

    private fun scheduleFlush() {
        if (flushTask != null) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            delay(RxLogRetention.flushInterval.toMillis())
            try {
                flushRx()
            } catch (cause: PersistenceStoreException) {
                // access() already reported the typed failure; retain the batch for explicit retry.
                mutex.withLock { flushTask = null }
            }
        }
        flushTask = job
        job.start()
    }

    private suspend fun flushRxLocked() {
        if (rxInsertsSinceFlush == 0L) return
        val caller = currentCoroutineContext()[Job]
        if (flushTask != caller) flushTask?.cancel()
        flushTask = null
        val pruned = LinkedHashMap<RadioId, Long>()
        database.withTransaction {
            database.rxLogs().upsert(pendingRx.values.toList())
            for (radio in rxCounts.keys) {
                val count = database.rxLogs().count(radio.value)
                if (count > checkedAdd(RxLogRetention.KEEP_COUNT, RxLogRetention.PRUNE_THRESHOLD)) {
                    for (row in database.rxLogs().oldest(radio.value, count - RxLogRetention.KEEP_COUNT)) {
                        database.rxLogs().delete(radio.value, row.id)
                    }
                    pruned[radio] = RxLogRetention.KEEP_COUNT
                } else {
                    pruned[radio] = count
                }
            }
        }
        pendingRx.clear()
        rxInsertsSinceFlush = 0
        rxInitiatedSaveCount = checkedIncrement(rxInitiatedSaveCount)
        rxCounts.putAll(pruned)
    }

    private suspend fun <T> access(operation: String, writing: Boolean, block: suspend () -> T): T = try {
        block()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: DatabaseValueException) {
        fail(operation, writing, cause)
    } catch (cause: SQLiteException) {
        fail(operation, writing, cause)
    } catch (cause: PersistenceStoreException) {
        reporter.report(operation, cause)
        throw cause
    }

    private fun fail(operation: String, writing: Boolean, cause: Throwable): Nothing {
        val reason = if (cause is DatabaseValueException) "Invalid stored ${cause.field}" else operation
        val failure = PersistenceStoreException(
            if (writing) PersistenceStoreError.SaveFailed(reason) else PersistenceStoreError.FetchFailed(reason),
            cause,
        )
        reporter.report(operation, failure)
        throw failure
    }
}
