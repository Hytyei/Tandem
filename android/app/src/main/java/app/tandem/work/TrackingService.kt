package app.tandem.work

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.tandem.MainActivity
import app.tandem.R
import app.tandem.TandemApp
import app.tandem.data.AppException
import app.tandem.data.FixBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the UI shows about the recording that is running on THIS phone (the server keeps the authoritative totals). */
object TrackingState {
    private val _tripId = MutableStateFlow<String?>(null)
    val tripId: StateFlow<String?> = _tripId.asStateFlow()
    private val _meters = MutableStateFlow(0.0)
    val meters: StateFlow<Double> = _meters.asStateFlow()
    private val _fixes = MutableStateFlow(0)
    val fixes: StateFlow<Int> = _fixes.asStateFlow()
    internal fun begin(id: String) { _tripId.value = id; _meters.value = 0.0; _fixes.value = 0 }
    internal fun end() { _tripId.value = null }
    internal fun update(meters: Double, fixes: Int) { _meters.value = meters; _fixes.value = fixes }
}

/**
 * Records this phone's position for a live-tracked date, only after the person started or joined one.
 * It is a foreground service: a notification is always visible while it runs, with a Stop button. Fixes are sent to the Tandem
 * server in small batches and nowhere else. It stops by itself when the date ends, when you stop sharing, or after 14 hours.
 */
class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lm: LocationManager? = null
    private var tripId: String? = null
    private var flusher: Job? = null
    private val buffer = ArrayList<FixBody>()
    private val lock = Any()
    private var last: Location? = null
    private var meters = 0.0
    private var fixes = 0
    private var lastNotifyAt = 0L
    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) = onFix(loc)
        @Deprecated("Needed on Android 10 and older") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSharing(); return START_NOT_STICKY }
        val id = intent?.getStringExtra(EXTRA_TRIP)
        if (id == null) { stopSelf(); return START_NOT_STICKY }
        if (running) { if (id != tripId) { stopSelf(); } ; return START_NOT_STICKY }
        tripId = id
        createNotificationChannels(this)
        val n = buildNotification("Recording your date…")
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(NOTIF_ID, n)
        } catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (!hasLocationPermission() || !startUpdates()) { stopSelf(); return START_NOT_STICKY }
        running = true
        TrackingState.begin(id)
        flusher = scope.launch {
            val deadline = System.currentTimeMillis() + 14 * 3600_000L
            while (true) {
                delay(30_000)
                if (!flush()) break
                if (System.currentTimeMillis() > deadline) break
            }
            ContextCompat.getMainExecutor(this@TrackingService).execute { stopSelf() }
        }
        return START_NOT_STICKY
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun startUpdates(): Boolean {
        val m = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        lm = m
        var any = false
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (runCatching { m.isProviderEnabled(p) }.getOrDefault(false)) {
                any = runCatching { m.requestLocationUpdates(p, 5_000L, 4f, listener, Looper.getMainLooper()); true }.getOrDefault(false) || any
            }
        }
        return any
    }

    private fun onFix(loc: Location) {
        // Ignore poor fixes (usually indoors / cold start) so the line doesn't wander.
        if (loc.hasAccuracy() && loc.accuracy > 50f) return
        val prev = last
        if (prev != null) {
            val d = prev.distanceTo(loc).toDouble()
            val dt = ((loc.time - prev.time) / 1000.0).coerceAtLeast(1.0)
            if (d < 4.0 || d / dt > 70.0) return   // standing still, or a GPS jump
            meters += d
        }
        last = loc
        fixes++
        synchronized(lock) {
            buffer += FixBody(loc.time, loc.latitude, loc.longitude)
            if (buffer.size > 600) buffer.removeAt(0)
        }
        TrackingState.update(meters, fixes)
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt > 20_000) {
            lastNotifyAt = now
            val text = "Recording your date · " + (if (meters >= 1000) "%.2f km".format(meters / 1000) else "${meters.toInt()} m")
            runCatching { getSystemService(android.app.NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text)) }
        }
        if (synchronized(lock) { buffer.size } >= 20) scope.launch { flush() }
    }

    /** Sends what is buffered. Returns false when the trip is over / we left it, so the service should stop. */
    private suspend fun flush(): Boolean {
        val id = tripId ?: return false
        val batch = synchronized(lock) { buffer.take(100).also { repeat(it.size) { buffer.removeAt(0) } } }
        if (batch.isEmpty()) return true
        val repo = (application as TandemApp).repo
        return try {
            repo.sendTripPoints(id, batch); true
        } catch (e: AppException) {
            val m = e.message.orEmpty()
            if (m.contains("over", true) || m.contains("Join the trip", true) || m.contains("not found", true)) false
            else { synchronized(lock) { buffer.addAll(0, batch) }; true }     // network trouble: keep them for the next round
        }
    }

    private fun stopSharing() {
        val id = tripId
        scope.launch {
            runCatching { flush() }
            if (id != null) runCatching { (application as TandemApp).repo.leaveTrip(id) }
            ContextCompat.getMainExecutor(this@TrackingService).execute { stopSelf() }
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).putExtra(EXTRA_TAB, "Heatmap").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TrackingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_TRACKING)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Tandem is recording your date").setContentText(text)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Stop sharing my location", stop).build()
    }

    override fun onDestroy() {
        runCatching { lm?.removeUpdates(listener) }
        flusher?.cancel()
        // Whatever is left in the buffer is sent one last time on a short-lived scope.
        val id = tripId
        if (id != null && synchronized(lock) { buffer.isNotEmpty() }) {
            val repo = (application as TandemApp).repo
            val rest = synchronized(lock) { buffer.toList().also { buffer.clear() } }
            repo.launch { runCatching { repo.sendTripPoints(id, rest.take(100)) } }
        }
        TrackingState.end()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 2001
        const val ACTION_STOP = "app.tandem.action.STOP_TRACKING"
        const val EXTRA_TRIP = "trip"

        fun start(context: Context, tripId: String) {
            ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java).putExtra(EXTRA_TRIP, tripId))
        }
        fun stop(context: Context) { context.stopService(Intent(context, TrackingService::class.java)) }
    }
}
