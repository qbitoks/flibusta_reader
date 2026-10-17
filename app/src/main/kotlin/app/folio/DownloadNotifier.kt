package app.folio

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.folio.core.data.catalog.DownloadItem
import app.folio.core.data.catalog.DownloadManager
import app.folio.core.data.db.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

/**
 * Catalog downloads in the notification shade: one progress notification while any book is queued or running,
 * then one per book that finished («Читать») or failed. Plain notifications, not a foreground service: WorkManager
 * and Range resume already carry the transfer. Without the permission nothing is posted.
 */
@OptIn(FlowPreview::class)
class DownloadNotifier(private val context: Context, private val downloads: DownloadManager) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun start(scope: CoroutineScope) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_LOW),
        )
        scope.launch {
            // States seen in this process: a row first seen as finished is history, not news.
            var seen: Map<String, DownloadState>? = null
            downloads.downloads.sample(500).collect { items ->
                val before = seen
                seen = items.associate { it.id to it.state }
                if (!allowed()) return@collect
                showProgress(items.filter { it.state == DownloadState.Queued || it.state == DownloadState.Running })
                if (before == null) return@collect
                for (item in items) {
                    val was = before[item.id] ?: continue
                    if (was == item.state) continue
                    when (item.state) {
                        DownloadState.Done -> if (item.readable) showDone(item)
                        DownloadState.Failed -> showFailed(item)
                        else -> {}
                    }
                }
            }
        }
    }

    private fun allowed() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun showProgress(active: List<DownloadItem>) {
        if (active.isEmpty()) return manager.cancel(PROGRESS_ID)
        val first = active.first()
        val fraction = first.fraction
        val title = if (active.size == 1) first.entity.title else context.getString(R.string.downloads_many, active.size)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(
                when {
                    first.state == DownloadState.Queued -> context.getString(R.string.downloads_waiting)
                    fraction != null -> context.getString(R.string.downloads_percent, (fraction * 100).toInt())
                    else -> context.getString(R.string.downloads_running)
                },
            )
            .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open(null, PROGRESS_ID))
            .build()
        manager.notify(PROGRESS_ID, notification)
    }

    private fun showDone(item: DownloadItem) {
        val bookId = item.entity.bookId ?: return
        val id = item.id.hashCode()
        val read = open(bookId, id)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.downloads_done, item.entity.title))
            .setContentText(item.entity.author)
            .setContentIntent(read)
            .addAction(0, context.getString(R.string.downloads_read), read)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    private fun showFailed(item: DownloadItem) {
        val id = item.id.hashCode()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.downloads_failed))
            .setContentText(item.entity.title)
            .setContentIntent(open(null, id))
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    /** Opens Folio, and the book when [bookId] is set. */
    private fun open(bookId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (bookId != null) intent.putExtra(MainActivity.EXTRA_BOOK_ID, bookId)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val PROGRESS_ID = 4100
    }
}
