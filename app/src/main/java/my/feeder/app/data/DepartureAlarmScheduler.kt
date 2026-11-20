package my.feeder.app.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import my.feeder.app.MainActivity

/**
 * Uses Android AlarmManager to wake the device from Doze mode at precise 30-second
 * intervals while departure alarms are armed.
 *
 * Uses setAlarmClock() to completely bypass Doze mode 15-minute batching restrictions
 * without needing ongoing persistent foreground notifications.
 */
object DepartureAlarmScheduler {
    private const val TAG = "DepartureAlarmScheduler"
    private const val WAKEUP_INTERVAL_MS = 30_000L // 30 seconds cadence

    fun schedule(context: Context, delayMs: Long = 0L) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DepartureAlarmReceiver::class.java).apply {
            action = "my.feeder.app.ALARM_TICK"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            5001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtElapsed = SystemClock.elapsedRealtime() + delayMs
        val triggerAtWall = System.currentTimeMillis() + delayMs

        try {
            // AlarmClockInfo bypasses Android Doze 9-15 min throttling completely
            val showIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerAtWall, showIntent),
                pendingIntent
            )
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pendingIntent)
                } else {
                    am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pendingIntent)
                }
            } catch (se: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, pendingIntent)
                }
            }
        }
    }

    fun scheduleNext(context: Context) {
        schedule(context, WAKEUP_INTERVAL_MS)
    }

    fun cancel(context: Context) {
        DepartureAlarmService.stop(context)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, DepartureAlarmReceiver::class.java).apply {
            action = "my.feeder.app.ALARM_TICK"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            5001,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            am.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
