package app.sorta.files.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteFolderDao {
    @Query("SELECT * FROM favorite_folders ORDER BY sortOrder, addedAt")
    fun observeAll(): Flow<List<FavoriteFolder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(f: FavoriteFolder)

    @Query("DELETE FROM favorite_folders WHERE path = :path")
    suspend fun delete(path: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_folders WHERE path = :path)")
    suspend fun exists(path: String): Boolean
}

@Dao
interface InboxSourceDao {
    @Query("SELECT * FROM inbox_sources WHERE enabled = 1")
    fun observeEnabled(): Flow<List<InboxSource>>

    @Query("SELECT * FROM inbox_sources WHERE enabled = 1")
    suspend fun enabled(): List<InboxSource>

    @Query("SELECT * FROM inbox_sources")
    fun observeAll(): Flow<List<InboxSource>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: InboxSource)

    @Query("DELETE FROM inbox_sources WHERE path = :path")
    suspend fun delete(path: String)

    @Query("SELECT COUNT(*) FROM inbox_sources")
    suspend fun count(): Int
}

@Dao
interface InboxStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: InboxState)

    @Query("SELECT * FROM inbox_state WHERE path = :path")
    suspend fun get(path: String): InboxState?

    @Query("DELETE FROM inbox_state WHERE path = :path")
    suspend fun delete(path: String)
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: TrashEntry)

    @Query("DELETE FROM trash WHERE trashPath = :trashPath")
    suspend fun delete(trashPath: String)

    @Query("SELECT * FROM trash WHERE deletedAt < :before")
    suspend fun olderThan(before: Long): List<TrashEntry>

    @Query("SELECT * FROM trash WHERE trashPath = :trashPath")
    suspend fun get(trashPath: String): TrashEntry?
}

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: HistoryEntry): Long

    @Query("SELECT * FROM history ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<HistoryEntry>>

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface SortRuleDao {
    @Query("SELECT * FROM sort_rules WHERE enabled = 1")
    fun observeEnabled(): Flow<List<SortRule>>
}

@Dao
interface RecentLocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(l: RecentLocation)

    @Query("SELECT * FROM recent_locations ORDER BY openedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 5): Flow<List<RecentLocation>>
}

@Dao
interface FolderPrefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: FolderPref)

    @Query("SELECT * FROM folder_prefs WHERE path = :path")
    suspend fun get(path: String): FolderPref?
}
