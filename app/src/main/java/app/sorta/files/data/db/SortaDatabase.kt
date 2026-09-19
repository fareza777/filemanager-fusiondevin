package app.sorta.files.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteFolder::class,
        InboxSource::class,
        InboxState::class,
        TrashEntry::class,
        HistoryEntry::class,
        SortRule::class,
        RecentLocation::class,
        FolderPref::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class SortaDatabase : RoomDatabase() {
    abstract fun favoriteFolderDao(): FavoriteFolderDao
    abstract fun inboxSourceDao(): InboxSourceDao
    abstract fun inboxStateDao(): InboxStateDao
    abstract fun trashDao(): TrashDao
    abstract fun historyDao(): HistoryDao
    abstract fun sortRuleDao(): SortRuleDao
    abstract fun sortRuleCrudDao(): SortRuleCrudDao
    abstract fun recentLocationDao(): RecentLocationDao
    abstract fun folderPrefDao(): FolderPrefDao

    companion object {
        @Volatile private var instance: SortaDatabase? = null

        fun get(context: Context): SortaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, SortaDatabase::class.java, "sorta.db"
                ).build().also { instance = it }
            }
    }
}
