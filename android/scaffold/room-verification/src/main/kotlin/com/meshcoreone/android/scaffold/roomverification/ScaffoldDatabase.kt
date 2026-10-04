// AndroidOnly: WP-002 Unpackaged generator fixture; not a production store, DTO or backup schema.
package com.meshcoreone.android.scaffold.roomverification

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "scaffold_rows", primaryKeys = ["partition", "id"])
internal data class ScaffoldRow(val partition: String, val id: String, val payload: String)

@Dao
internal interface ScaffoldDao {
    @Upsert
    suspend fun upsert(row: ScaffoldRow)

    @Query("SELECT * FROM scaffold_rows WHERE partition = :partition ORDER BY id")
    fun observe(partition: String): Flow<List<ScaffoldRow>>

    @Query("SELECT COUNT(*) FROM scaffold_rows")
    suspend fun count(): Int
}

@Database(entities = [ScaffoldRow::class], version = 1, exportSchema = true)
internal abstract class ScaffoldDatabase : RoomDatabase() {
    abstract fun rows(): ScaffoldDao
}
