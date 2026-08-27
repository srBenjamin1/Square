package dev.lelonio.square.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import dev.lelonio.square.R
import dev.lelonio.square.SquareApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Fetches the new version while the listener does something else.
 *
 * A twenty-megabyte download tied to a screen is a download that dies when the
 * screen does: the old one lived in the composition, so leaving the app — or
 * merely opening the player — took it with it, and there was nothing to say how
 * far it had got. A foreground service is what Android gives an app for work it
 * must be allowed to finish, and the notification is not decoration but the
 * price of the permission: it says what is happening and how far along it is.
 *
 * The service does the fetching and nothing else. Installing is still the
 * system's dialog and still cannot be skipped, which is the right shape for a
 * thing that replaces the app.
 */
class UpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        val version = intent?.getStringExtra(EXTRA_VERSION).orEmpty()
        val bytes = intent?.getLongExtra(EXTRA_BYTES, 0L) ?: 0L

        if (url.isNullOrEmpty() || running) return START_NOT_STICKY
        running = true

        channel()
        startForeground(NOTIFICATION_ID, notification(version, null))

        val updater = (applicationContext as SquareApplication).updater
        scope.launch {
            updater.fetchAndInstall(url, version, bytes) { progress ->
                notify(version, progress)
            }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(version: String, progress: Float?) {
        manager().notify(NOTIFICATION_ID, notification(version, progress))
    }

    private fun notification(version: String, progress: Float?): Notification {
        val percent = progress?.let { (it * 100).toInt().coerceIn(0, 100) }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.update_downloading))
            .setContentText(
                listOfNotNull(version.takeIf { it.isNotEmpty() }, percent?.let { "$it%" })
                    .joinToString(" · "),
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            // Indeterminate until the server says how much there is to fetch.
            .setProgress(100, percent ?: 0, percent == null)
            .build()
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.update_channel),
            // Low: this is a progress bar, not news. It belongs in the shade
            // without a sound or a heads-up card over what the listener is
            // doing.
            NotificationManager.IMPORTANCE_LOW,
        )
        manager().createNotificationChannel(channel)
    }

    private fun manager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "square_update"
        private const val NOTIFICATION_ID = 4_201

        private const val EXTRA_URL = "url"
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_BYTES = "bytes"

        /** Starts the download; safe to call twice, the second is ignored. */
        fun start(context: Context, update: Updater.State.Available) {
            val intent = Intent(context, UpdateService::class.java)
                .putExtra(EXTRA_URL, update.url)
                .putExtra(EXTRA_VERSION, update.version)
                .putExtra(EXTRA_BYTES, update.bytes)
            context.startForegroundService(intent)
        }
    }
}
