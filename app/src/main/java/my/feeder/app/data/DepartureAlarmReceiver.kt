package my.feeder.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by [DepartureAlarmScheduler] even during deep Doze,
 * as well as OS boot completed and app update events.
 *
 * Ensures background tracking and service alerts auto-start across device restarts
 * and app updates.
 */
class DepartureAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isBootOrUpdate = action == Intent.ACTION_BOOT_COMPLETED ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON"

        Log.d(TAG, "onReceive action=$action isBootOrUpdate=$isBootOrUpdate")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DepartureAlarmStore.init(context)
                AlertScheduleManager.init(context)

                // 1. Reschedule background service alerts polling on boot or update
                if (isBootOrUpdate) {
                    try {
                        ServiceAlertsWorker.schedule(context)
                    } catch (e: Exception) {
                        Log.w(TAG, "ServiceAlertsWorker reschedule error: ${e.message}")
                    }
                }

                val alarms = DepartureAlarmStore.load()
                if (alarms.isEmpty()) {
                    if (!isBootOrUpdate) {
                        DepartureAlarmScheduler.cancel(context)
                        DepartureAlarmService.stop(context)
                    }
                    return@launch
                }

                // 2. On boot or update, immediately re-arm alarms in background
                if (isBootOrUpdate) {
                    DepartureAlarmScheduler.schedule(context, delayMs = 3000L)
                    DepartureAlarmWorker.schedule(context)
                    return@launch
                }

                // 3. Regular alarm tick
                // If outside active commute window, reschedule for later without polling network
                if (!AlertScheduleManager.isWithinActiveWindow()) {
                    DepartureAlarmScheduler.schedule(context, 60_000L)
                    return@launch
                }

                GtfsRepository.loadCatalog(context)
                GtfsRepository.refreshLiveBuses()

                val announcements = DepartureAlarmEngine.evaluate()
                if (announcements.isNotEmpty()) {
                    DepartureAlarmEngine.announce(context, announcements)
                }

                DepartureAlarmStore.pruneCompleted()

                // Keep scheduling while alarms are armed
                val remaining = DepartureAlarmStore.load()
                if (remaining.isNotEmpty()) {
                    DepartureAlarmScheduler.scheduleNext(context)
                } else {
                    DepartureAlarmScheduler.cancel(context)
                    DepartureAlarmService.stop(context)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Check failed: ${e.message}")
                if (DepartureAlarmStore.load().isNotEmpty()) {
                    DepartureAlarmScheduler.scheduleNext(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DepartureAlarmReceiver"
    }
}
