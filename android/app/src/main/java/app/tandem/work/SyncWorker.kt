package app.tandem.work

import android.content.Context
import androidx.work.*
import app.tandem.TandemApp
import app.tandem.data.AppException
import app.tandem.update.Updater
import java.util.concurrent.TimeUnit

/** Periodic background sync: keeps widgets fresh and reschedules reminders. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as TandemApp).repo
        if (!repo.loggedIn.value) return Result.success()
        return try {
            repo.refresh()
            checkForUpdate(repo)
            Result.success()
        } catch (e: AppException) {
            Result.retry()
        }
    }

    /** At most every 6 hours: look for a new release and tell the person once per version. */
    private suspend fun checkForUpdate(repo: app.tandem.data.Repository) {
        val now = System.currentTimeMillis()
        if (now - repo.prefs.lastUpdateCheck() < 6 * 3600_000L) return
        repo.prefs.setLastUpdateCheck(now)
        val info = runCatching { Updater.check() }.getOrNull() ?: return
        if (repo.prefs.notifiedUpdate() != info.version) {
            Notifier.notifyUpdate(applicationContext, info)
            repo.prefs.setNotifiedUpdate(info.version)
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
