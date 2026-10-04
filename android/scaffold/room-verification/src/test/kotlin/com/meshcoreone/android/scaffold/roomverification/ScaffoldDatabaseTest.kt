// AndroidOnly: WP-002 Actual Room partition/upsert/Flow/rollback assertions on simulated SDK 31.
package com.meshcoreone.android.scaffold.roomverification

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ScaffoldDatabaseTest {
    private lateinit var database: ScaffoldDatabase

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ScaffoldDatabase::class.java,
        ).build()
    }

    @After
    fun close() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun equalIdsAndUpsertRemainPartitionedInObservedRows() = runTest {
        val rows = database.rows()
        rows.upsert(ScaffoldRow("radio-a", "same-id", "first"))
        rows.upsert(ScaffoldRow("radio-b", "same-id", "other"))
        assertEquals(listOf(ScaffoldRow("radio-a", "same-id", "first")), rows.observe("radio-a").first())
        assertEquals(listOf(ScaffoldRow("radio-b", "same-id", "other")), rows.observe("radio-b").first())
        rows.upsert(ScaffoldRow("radio-a", "same-id", "updated"))
        assertEquals(listOf(ScaffoldRow("radio-a", "same-id", "updated")), rows.observe("radio-a").first())
        assertEquals(listOf(ScaffoldRow("radio-b", "same-id", "other")), rows.observe("radio-b").first())
        assertEquals(2, rows.count())
    }

    @Test
    fun failedTransactionRollsBackWithoutRemovingPreviouslyCommittedRows() = runTest {
        val rows = database.rows()
        rows.upsert(ScaffoldRow("radio-a", "committed", "keep"))
        assertFailsWith<FixtureRollback> {
            database.withTransaction {
                rows.upsert(ScaffoldRow("radio-a", "uncommitted", "discard"))
                rows.upsert(ScaffoldRow("radio-b", "uncommitted", "discard"))
                throw FixtureRollback()
            }
        }
        assertEquals(1, rows.count())
        assertEquals(listOf(ScaffoldRow("radio-a", "committed", "keep")), rows.observe("radio-a").first())
        assertEquals(emptyList(), rows.observe("radio-b").first())
    }

    private class FixtureRollback : Exception("Deliberate verification-fixture transaction failure")
}
