package my.feeder.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Keeps the departure alarms honest while the app is closed.
 *
 * The worker refreshes the live bus feed, asks [DepartureAlarmEngine] whether
 * any armed alarm has reached its moment, posts the notification, and then
 * schedules itself again a minute later.
 *
 * It is a one-time worker that re-arms itself rather than a periodic one,
 * because WorkManager's periodic floor is fifteen minutes — far too coarse
 * for a "be at the curb in three minutes" warning. Android still batches
 * background work when the app is idle, so the timing is best effort in the
 * background; while the app is open the foreground loop checks every thirty
 * seconds and is exact.
 */
class DepartureAlarmWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            DepartureAlarmStore.init(applicationContext)

            // Refresh the fleet so the ETA is against real positions, not the
            // last thing the app happened to see.
            GtfsRepository.loadCatalog(applicationContext)
            GtfsRepository.refreshLiveBuses()

            val announcements = DepartureAlarmEngine.evaluate()
            if (announcements.isNotEmpty()) {
                DepartureAlarmEngine.announce(applicationContext, announcements)
            }

            // Alarms that have announced both stages have done their job.
            DepartureAlarmStore.pruneCompleted()

            // Keep going while anything is still armed.
            if (DepartureAlarmStore.load().isNotEmpty()) {
                scheduleNext(applicationContext)
            }
            Result.success()
        } catch (e: Exception) {
            // A failed check is not a failure of the work: reschedule and succeed so WorkManager doesn't duplicate.
            if (DepartureAlarmStore.load().isNotEmpty()) {
                scheduleNext(applicationContext)
                Result.success()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        private const val UNIQUE_NAME = "catchit_departure_alarm_check"

        /** How often the alarm is checked while the app is closed. */
        private const val INTERVAL_SECONDS = 60L

        /** Starts (or restarts) the alarm check loop. Safe to call repeatedly. */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<DepartureAlarmWorker>()
                .setInitialDelay(INTERVAL_SECONDS, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /** Stops the alarm check loop. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
        }

        private fun scheduleNext(context: Context) {
            val request = OneTimeWorkRequestBuilder<DepartureAlarmWorker>()
                .setInitialDelay(INTERVAL_SECONDS, TimeUnit.SECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
