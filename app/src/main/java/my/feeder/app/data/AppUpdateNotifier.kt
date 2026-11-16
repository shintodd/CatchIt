package my.feeder.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import my.feeder.app.MainActivity
import my.feeder.app.R
import java.io.File

/**
 * Raises high-priority system "Heads-Up" notifications when a new CatchIt app
 * update is available on GitHub Releases, during download, and when ready to install.
 */
object AppUpdateNotifier {

    const val CHANNEL_ID = "catchit_app_updates"
    private const val PREFS_NAME = "catchit_update_prefs"
    private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"

    private const val NOTIFICATION_ID_UPDATE_AVAILABLE = 8801
    private const val NOTIFICATION_ID_DOWNLOADING = 8802
    private const val NOTIFICATION_ID_READY_TO_INSTALL = 8803

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up notices and installation progress for new CatchIt versions"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Raises a heads-up notification alerting the user that a new version of CatchIt is available.
     * Guaranteed to notify at most once per version release unless forced.
     */
    fun notifyUpdateAvailable(context: Context, update: AppUpdate, force: Boolean = false) {
        if (!canNotify(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastNotified = prefs.getString(KEY_LAST_NOTIFIED_VERSION, null)
        if (!force && lastNotified == update.versionName) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_OPEN_UPDATE", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val detail = if (update.releaseNotes.isNotBlank()) {
            "CatchIt v${update.versionName} is ready to install.\n\n${update.releaseNotes.take(300)}"
        } else {
            "CatchIt v${update.versionName} is ready to install with live AVL REST endpoints and background autostart."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_catchit_notification)
            .setLargeIcon(BrandIcon.notificationIcon(context))
            .setContentTitle("HEADS UP · Update v${update.versionName} Available")
            .setContentText("Tap to download & install the new CatchIt update.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            manager.notify(NOTIFICATION_ID_UPDATE_AVAILABLE, notification)
            prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, update.versionName).apply()
        } catch (_: Exception) {}
    }

    /**
     * Updates download progress in the notification shade.
     */
    fun notifyDownloadProgress(context: Context, update: AppUpdate, progressPercent: Int) {
        if (!canNotify(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_catchit_notification)
            .setContentTitle("Downloading CatchIt v${update.versionName}")
            .setContentText("$progressPercent% completed")
            .setProgress(100, progressPercent, progressPercent <= 0)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            manager.notify(NOTIFICATION_ID_DOWNLOADING, notification)
        } catch (_: Exception) {}
    }

    /**
     * Raises a heads-up notification that the APK is downloaded and ready for 1-tap installation.
     */
    fun notifyReadyToInstall(context: Context, update: AppUpdate, file: File) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_DOWNLOADING)

        if (!canNotify(context)) return
        ensureChannel(manager)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_catchit_notification)
            .setLargeIcon(BrandIcon.notificationIcon(context))
            .setContentTitle("HEADS UP · Update v${update.versionName} Ready")
            .setContentText("Tap to complete installation of CatchIt v${update.versionName}.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            manager.notify(NOTIFICATION_ID_READY_TO_INSTALL, notification)
        } catch (_: Exception) {}
    }

    fun clearAll(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.cancel(NOTIFICATION_ID_UPDATE_AVAILABLE)
            manager.cancel(NOTIFICATION_ID_DOWNLOADING)
            manager.cancel(NOTIFICATION_ID_READY_TO_INSTALL)
        } catch (_: Exception) {}
    }
}
