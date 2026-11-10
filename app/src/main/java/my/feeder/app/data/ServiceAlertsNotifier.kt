package my.feeder.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import my.feeder.app.EXTRA_OPEN_NOTIFICATIONS
import my.feeder.app.MainActivity
import my.feeder.app.R

/**
 * Raises a notification when the operator publishes a new service notice.
 *
 * This is the piece that matches MyRapid PULSE: PULSE pushes "Alerts and
 * Service Status" to the phone the moment a disruption is posted, and so does
 * CatchIt — the poll notices an alert that was not there before and raises a
 * heads-up notification for it.
 *
 * The channel is IMPORTANCE_HIGH so it appears the way a disruption should:
 * immediately, and with the sound.
 */
object ServiceAlertsNotifier {

    const val CHANNEL_ID = "catchit_service_updates"

    private const val GROUP_KEY = "catchit_service_alerts"
    private const val SUMMARY_ID = 9001

    /** True when the user has granted the notification permission. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Posts one notification per new alert.
     *
     * @return how many notifications were posted.
     */
    fun notifyNew(context: Context, alerts: List<ServiceAlertsRepository.ServiceAlert>): Int {
        if (alerts.isEmpty()) return 0
        if (!canNotify(context)) return 0
        AlertScheduleManager.init(context)
        if (!AlertScheduleManager.isWithinActiveWindow()) return 0

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val posted = alerts.take(MAX_NOTIFICATIONS).count { alert ->
            val notification = build(context, alert)
            val id = NOTIFICATION_ID_BASE + (alert.url.hashCode() and 0x0000FFFF)
            try {
                manager.notify(id, notification)
                true
            } catch (e: Exception) {
                false
            }
        }

        if (alerts.size > 1) {
            try {
                manager.notify(SUMMARY_ID, buildSummary(context, alerts))
            } catch (e: Exception) {
                // A missing summary is not worth failing the whole batch over.
            }
        }
        return posted
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Service updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Disruptions, diversions and delays published by Prasarana"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 150, 300)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun build(context: Context, alert: ServiceAlertsRepository.ServiceAlert) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_catchit_notification)
            // Do not leave the header logo to the system's cached copy of the
            // app icon - it can be stale and show the previous mark.
            .setLargeIcon(BrandIcon.notificationIcon(context))
            .setContentTitle(alert.title)
            .setContentText(summaryLine(alert))
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText(alert)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

    private fun buildSummary(context: Context, alerts: List<ServiceAlertsRepository.ServiceAlert>) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_catchit_notification)
            // Do not leave the header logo to the system's cached copy of the
            // app icon - it can be stale and show the previous mark.
            .setLargeIcon(BrandIcon.notificationIcon(context))
            .setContentTitle("${alerts.size} new service updates")
            .setContentText(alerts.first().title)
            .setStyle(
                NotificationCompat.InboxStyle().also { style ->
                    alerts.take(MAX_NOTIFICATIONS).forEach { style.addLine(it.title) }
                }
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()

    /** One line for the collapsed notification. */
    private fun summaryLine(alert: ServiceAlertsRepository.ServiceAlert): String {
        val codes = alert.routeCodes.take(4).joinToString(", ")
        return when {
            codes.isNotEmpty() -> "Routes $codes"
            alert.excerpt.isNotBlank() -> alert.excerpt.take(120)
            else -> alert.title
        }
    }

    /** The expanded text: title, affected routes, and the operator's excerpt. */
    private fun bigText(alert: ServiceAlertsRepository.ServiceAlert): String {
        val parts = mutableListOf<String>()
        if (alert.routeCodes.isNotEmpty()) {
            parts += "Routes affected: " + alert.routeCodes.joinToString(", ")
        }
        if (alert.excerpt.isNotBlank()) parts += alert.excerpt
        if (alert.published.isNotBlank()) parts += "Published ${alert.published}"
        return if (parts.isEmpty()) alert.title else parts.joinToString("\n\n")
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOTIFICATIONS, true)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private const val MAX_NOTIFICATIONS = 5
    private const val NOTIFICATION_ID_BASE = 9100
}
