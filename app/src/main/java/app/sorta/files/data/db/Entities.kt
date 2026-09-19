package app.sorta.files.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_folders")
data class FavoriteFolder(
    @PrimaryKey val path: String,
    val label: String,
    val addedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
)

@Entity(tableName = "inbox_sources")
data class InboxSource(
    @PrimaryKey val path: String,
    val label: String,
    val recursive: Boolean = false,
    val enabled: Boolean = true,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "inbox_state")
data class InboxState(
    @PrimaryKey val path: String,
    val state: String, // UNTIDIED | TIDIED
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "trash")
data class TrashEntry(
    @PrimaryKey val trashPath: String,
    val originalPath: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val deletedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opType: String,
    val itemCount: Int,
    val succeeded: Int,
    val failed: Int,
    val skipped: Int,
    val detail: String, // per-item summary lines
    val at: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sort_rules")
data class SortRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val matchType: String, // EXT | NAME_CONTAINS | NAME_REGEX
    val matchValue: String,
    val targetFolder: String,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recent_locations")
data class RecentLocation(
    @PrimaryKey val path: String,
    val label: String,
    val openedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "folder_prefs")
data class FolderPref(
    @PrimaryKey val path: String,
    val sortField: String = "NAME",
    val ascending: Boolean = true,
    val grid: Boolean = false,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)
