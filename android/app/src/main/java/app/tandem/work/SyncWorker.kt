package app.tandem.work

import android.content.Context
import androidx.work.*
import app.tandem.TandemApp
import app.tandem.data.AppException
import java.util.concurrent.TimeUnit

/** Periodic background sync: keeps widgets fresh and reschedules reminders. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as TandemApp).repo
        if (!repo.loggedIn.value) return Result.success()
        return try {
            repo.refresh()
            Result.success()
        } catch (e: AppException) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "tandem-sync"
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
