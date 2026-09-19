package app.sorta.files.core.ops

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.sorta.files.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FileOperationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundWith(NOTIF_ID, buildNotification(null, 0, 0))
        val controller = OperationController.instance ?: return
        scope.launch {
            controller.engine?.progress?.collectLatest { p ->
                if (p != null && !p.finished) {
                    val pct = if (p.totalBytes > 0) (p.doneBytes * 100 / p.totalBytes).toInt() else 0
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIF_ID, buildNotification(p.currentItem, pct, p.doneItems))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            OperationController.instance?.cancel()
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWith(id: Int, n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else startForeground(id, n)
    }

    private fun createChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_ops),
                NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String?, pct: Int, items: Int): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FileOperationService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(getString(R.string.notif_working))
            .setContentText(text ?: getString(R.string.items_count, items))
            .setProgress(100, pct, pct == 0)
            .setOngoing(true)
            .addAction(0, getString(R.string.notif_cancel), cancelIntent)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "file_ops"
        const val NOTIF_ID = 42
        const val ACTION_CANCEL = "app.sorta.files.action.CANCEL_OP"

        fun start(context: Context) {
            val i = Intent(context, FileOperationService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i)
            else context.startService(i)
        }
    }
}
