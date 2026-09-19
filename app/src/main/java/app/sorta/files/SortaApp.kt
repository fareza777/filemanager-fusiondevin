package app.sorta.files

import android.app.Application
import app.sorta.files.core.ops.OperationController
import app.sorta.files.core.trash.TrashManager
import app.sorta.files.data.db.SortaDatabase
import app.sorta.files.data.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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

    /** New-inbox-file count for the nav badge; InboxScreen keeps it updated. */
    val inboxBadgeCount = kotlinx.coroutines.flow.MutableStateFlow(0)

    lateinit var billing: app.sorta.files.core.billing.BillingManager
}

class SortaApp : Application(), coil.ImageLoaderFactory {
    lateinit var container: AppContainer
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            try {
                val days = container.prefs.purgeDays.first()
                container.trashManager.purgeExpired(days)
            } catch (_: Exception) {}
        }
        container.billing = app.sorta.files.core.billing.BillingManager(
            this, container.prefs, appScope)
        container.billing.start()
        // Seed default inbox sources
        appScope.launch {
            val dao = container.db.inboxSourceDao()
            if (dao.count() == 0) {
                val dl = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS)
                dao.upsert(app.sorta.files.data.db.InboxSource(dl.absolutePath, "Downloads"))
                listOf(
                    java.io.File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_PICTURES), "Screenshots") to "Screenshots",
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOCUMENTS) to "Documents",
                    java.io.File(dl.parentFile, "Telegram") to "Telegram",
                    java.io.File(dl.parentFile, "WhatsApp/Media/WhatsApp Documents") to "WhatsApp",
                    java.io.File(dl.parentFile, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents") to "WhatsApp",
                ).forEach { (f, label) ->
                    if (f.isDirectory) dao.upsert(
                        app.sorta.files.data.db.InboxSource(f.absolutePath, label))
                }
            }
        }
        // AdMob init (test ids)
        try {
            com.google.android.gms.ads.MobileAds.initialize(this) {}
        } catch (_: Throwable) {}
    }

    override fun newImageLoader(): coil.ImageLoader =
        coil.ImageLoader.Builder(this)
            .components {
                add(coil.decode.VideoFrameDecoder.Factory())
                add(app.sorta.files.core.fs.PdfThumbnailFetcher.Factory())
                add(app.sorta.files.core.fs.ApkIconFetcher.Factory())
            }
            .crossfade(true)
            .build()
}
