package my.feeder.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Polls the operator's service-alert page in the background, so a newly
 * published disruption still raises a notification when the app is closed.
 *
 * This is what makes CatchIt's alerts behave like MyRapid PULSE's, which
 * pushes to the phone from the operator's own backend. CatchIt has no backend,
 * so it asks the same source on a schedule instead.
 *
 * The interval is WorkManager's floor of 15 minutes for periodic work. While
 * the app is open it polls faster (every 2 minutes), so the background worker
 * is the safety net rather than the main path.
 */
class ServiceAlertsWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ServiceAlertsRepository.init(applicationContext)
        try {
            // The worker has no collector on the repository's new-alert flow,
            // so it raises the notifications itself.
            val fresh = ServiceAlertsRepository.refreshBlocking()
            if (fresh.isNotEmpty()) {
                ServiceAlertsNotifier.notifyNew(applicationContext, fresh)
            }
            Result.success()
        } catch (e: Exception) {
            // A failed poll is not a failure of the work: try again next tick.
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "catchit_service_alerts_poll"

        /** Starts the background poll. Safe to call repeatedly. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ServiceAlertsWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
