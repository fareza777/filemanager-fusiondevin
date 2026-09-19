package app.sorta.files.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SortRuleCrudDao {
    @Query("SELECT * FROM sort_rules ORDER BY createdAt")
    fun observeAll(): Flow<List<SortRule>>

    @Query("SELECT * FROM sort_rules ORDER BY createdAt")
    suspend fun all(): List<SortRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: SortRule): Long

    @Update
    suspend fun update(r: SortRule)

    @Delete
    suspend fun delete(r: SortRule)
}
