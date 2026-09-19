package app.sorta.files

import android.app.Application
import app.sorta.files.core.ops.OperationController
import app.sorta.files.core.trash.TrashManager
import app.sorta.files.data.db.SortaDatabase
import app.sorta.files.data.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(app: Application) {
    val db = SortaDatabase.get(app)
    val prefs = UserPrefs(app)
    val trashManager = TrashManager(db.trashDao())
    val operations = OperationController(
        appContext = app,
        trashDao = db.trashDao(),
        historyDao = db.historyDao(),
        trashRoot = { f -> trashManager.trashRootFor(f) },
    ).also { OperationController.instance = it }
}

class SortaApp : Application() {
    lateinit var container: AppContainer
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            try { container.trashManager.purgeExpired() } catch (_: Exception) {}
        }
        // Seed default inbox sources
        appScope.launch {
            val dao = container.db.inboxSourceDao()
            if (dao.count() == 0) {
                val dl = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS)
                dao.upsert(app.sorta.files.data.db.InboxSource(dl.absolutePath, "Downloads"))
                val shots = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_PICTURES), "Screenshots")
                if (shots.isDirectory) {
                    dao.upsert(app.sorta.files.data.db.InboxSource(shots.absolutePath, "Screenshots"))
                }
            }
        }
        // AdMob init (test ids)
        try {
            com.google.android.gms.ads.MobileAds.initialize(this) {}
        } catch (_: Throwable) {}
    }
}
