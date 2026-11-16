package my.feeder.app.data

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
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import my.feeder.app.MainActivity

/**
 * Foreground Service for active departure alarms.
 *
 * Keeps live bus tracking running continuously in background even when device
 * enters Doze mode or aggressive OEM task killers (MIUI / HyperOS, OneUI) are active.
 */
class DepartureAlarmService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trackingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.cancel(NOTIFICATION_ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        stopSelf()
        return START_NOT_STICKY
    }

    private fun startTrackingLoop() {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                try {
                    DepartureAlarmStore.init(applicationContext)
                    AlertScheduleManager.init(applicationContext)

                    val alarms = DepartureAlarmStore.load()
                    if (alarms.isEmpty()) {
                        stopSelf()
                        break
                    }

                    if (AlertScheduleManager.isWithinActiveWindow()) {
                        GtfsRepository.loadCatalog(applicationContext)
                        GtfsRepository.refreshLiveBuses()

                        val announcements = DepartureAlarmEngine.evaluate()
                        if (announcements.isNotEmpty()) {
                            DepartureAlarmEngine.announce(applicationContext, announcements)
                        }

                        DepartureAlarmStore.pruneCompleted()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Tracking loop error: ${e.message}")
                }
                delay(CADENCE_MS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CatchIt Background Bus Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active status while departure alarms are monitoring live buses"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            5002,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("CatchIt Live Tracking Active")
            .setContentText("Monitoring live feeder buses in background")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        trackingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DepartureAlarmService"
        private const val CHANNEL_ID = "catchit_live_tracker_channel"
        private const val NOTIFICATION_ID = 4001
        private const val CADENCE_MS = 30_000L

        fun start(context: Context) {
            // Keep background work silent: do not show a foreground notification.
            stop(context)
        }

        fun stop(context: Context) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.cancel(NOTIFICATION_ID)
                context.stopService(Intent(context, DepartureAlarmService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Cannot stop foreground service: ${e.message}")
            }
        }
    }
}
