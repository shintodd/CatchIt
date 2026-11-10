package my.feeder.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import my.feeder.app.EXTRA_OPEN_ALERMS
import my.feeder.app.EXTRA_ROUTE_CODE
import my.feeder.app.MainActivity
import my.feeder.app.R

/**
 * Posts the two departure notifications.
 *
 * Stage 1 is "Leave now" and stage 2 is "At the curb" — the plain-language
 * wording the product spec asks for, not "stage 1" and "stage 2".
 */
object DepartureAlarmNotifier {

    const val CHANNEL_ID = "catchit_departure_alarms"

    private const val NOTIFICATION_ID_STAGE1 = 5001
    private const val NOTIFICATION_ID_STAGE2 = 5002

    /** True when the user has granted the notification permission. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * Posts one stage of a departure alarm.
     *
     * @return true when the notification was posted.
     */
    fun post(context: Context, a: DepartureAlarmEngine.Announcement): Boolean {
        if (!canNotify(context)) return false

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        // Stage 2 says "go to your waiting spot" when the two stages use
        // different stops, and "be at the curb" when they share one.
        val title = when {
            a.stage == 1 -> "Leave now"
            a.sameStop -> "Be at the curb"
            else -> "Go to your waiting spot"
        }
        val body = buildString {
            append(a.routeCode)
            append(" · ")
            append(a.stopName)
            append(" · arriving now")
        }
        val detail = buildString {
            append("Bus ")
            append(a.plate)
            if (a.speedKmh > 0) {
                append(" · ")
                append(a.speedKmh)
                append(" km/h")
            }
            append("\n")
            append(
                when {
                    a.stage == 1 -> "The bus has reached " + a.stopName + ". Start moving now."
                    a.sameStop -> "The bus has reached " + a.stopName + ". Get to the bay now."
                    else -> "The bus has reached " + a.stopName + ". Head to your waiting spot now."
                }
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_catchit_notification)
            // Do not leave the header logo to the system's cached copy of the
            // app icon - it can be stale and show the previous mark.
            .setLargeIcon(BrandIcon.notificationIcon(context))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openApp(context, a))
            .build()

        val notificationId = (a.stage * 10_000) + (("${a.routeCode}_${a.stopId}").hashCode() and 0x1FFF)
        return try {
            manager.notify(notificationId, notification)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Departure alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Tells you when to leave and when to be at the bus stop"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    /** Opens the app on the route the alarm is for. */
    private fun openApp(context: Context, a: DepartureAlarmEngine.Announcement): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_ALERMS, true)
            putExtra(EXTRA_ROUTE_CODE, a.routeCode)
        }
        return PendingIntent.getActivity(
            context,
            a.routeCode.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
