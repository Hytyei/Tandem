package app.tandem

import android.app.Application
import app.tandem.data.Repository
import app.tandem.work.SyncWorker
import app.tandem.work.createNotificationChannels
import coil.ImageLoader
import coil.ImageLoaderFactory

class TandemApp : Application(), ImageLoaderFactory {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
        createNotificationChannels(this)
        SyncWorker.schedule(this)
    }

    /** Coil uses our authenticated client so avatar URLs just work. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).okHttpClient(repo.http).crossfade(true).build()
}
