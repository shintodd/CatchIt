package my.feeder.app.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by [DepartureAlarmScheduler] even during deep Doze.
 * Runs evaluate() and dispatches timely notifications without multi-minute lag.
 */
class DepartureAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DepartureAlarmStore.init(context)
                AlertScheduleManager.init(context)

                val alarms = DepartureAlarmStore.load()
                if (alarms.isEmpty()) {
                    DepartureAlarmScheduler.cancel(context)
                    return@launch
                }

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
                if (DepartureAlarmStore.load().isNotEmpty()) {
                    DepartureAlarmScheduler.scheduleNext(context)
                }
            } catch (e: Exception) {
                Log.w("DepartureAlarmReceiver", "Check failed: ${e.message}")
                if (DepartureAlarmStore.load().isNotEmpty()) {
                    DepartureAlarmScheduler.scheduleNext(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
