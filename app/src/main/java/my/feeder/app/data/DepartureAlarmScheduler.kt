package my.feeder.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * Uses Android AlarmManager to wake the device from Doze mode at precise 30-second
 * intervals while departure alarms are armed.
 *
 * This prevents JobScheduler / WorkManager background deferrals where notifications
 * can be delayed by 5-15 minutes when the phone screen is off.
 */
object DepartureAlarmScheduler {
    private const val TAG = "DepartureAlarmScheduler"
    private const val WAKEUP_INTERVAL_MS = 30_000L // 30 seconds cadence

    fun schedule(context: Context, delayMs: Long = 0L) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DepartureAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            5001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = SystemClock.elapsedRealtime() + delayMs
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (se: SecurityException) {
            // Android 12+ fallback when exact alarm permission is denied
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
        }
    }

    fun scheduleNext(context: Context) {
        schedule(context, WAKEUP_INTERVAL_MS)
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DepartureAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            5001,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            am.cancel(pendingIntent)
        }
    }
}
