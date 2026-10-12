package app.tandem.work

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.tandem.MainActivity
import app.tandem.R
import app.tandem.TandemApp
import app.tandem.data.CalendarEvent
import app.tandem.data.nextDate
import app.tandem.data.parseTime
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

const val CHANNEL_EVENTS = "events"
const val CHANNEL_UPDATES = "updates"

fun createNotificationChannels(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL_EVENTS, "Event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Reminders for your shared events and anniversaries"
    })
    nm.createNotificationChannel(NotificationChannel(CHANNEL_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "New versions of Tandem"
    })
}

/**
 * Schedules local reminders (no push server needed): the evening before at 20:00 and
 * at the event time (or 09:00 for all-day events). Rebuilt after every sync and on boot.
 */
object ReminderScheduler {
    private const val PREFS = "reminders"
    private const val KEY_CODES = "codes"
    private const val HORIZON_DAYS = 45L

    fun reschedule(context: Context, events: List<CalendarEvent>) {
        cancelAll(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val codes = mutableSetOf<String>()

        fun schedule(code: Int, at: LocalDateTime, title: String, text: String) {
            if (!at.isAfter(now)) return
            val intent = Intent(context, ReminderReceiver::class.java)
                .putExtra("title", title).putExtra("text", text).putExtra("code", code)
            val pi = PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(zone).toInstant().toEpochMilli(), pi)
            codes += code.toString()
        }

        for (e in events) {
            val day = e.nextDate(now.toLocalDate()) ?: continue
            if (day.isAfter(LocalDate.now().plusDays(HORIZON_DAYS))) continue
            val time = parseTime(e.time)
            val startAt = LocalDateTime.of(day, time ?: LocalTime.of(9, 0))
            val label = "${e.emoji} ${e.title}"
            schedule(("${e.id}:day").hashCode(), startAt, label, if (time != null) "Starting now" else "It's today!")
            schedule(("${e.id}:eve").hashCode(), LocalDateTime.of(day.minusDays(1), LocalTime.of(20, 0)), label, "Coming up tomorrow")
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_CODES, codes).apply()
    }

    fun cancelAll(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val am = context.getSystemService(AlarmManager::class.java)
        for (c in sp.getStringSet(KEY_CODES, emptySet()).orEmpty()) {
            val code = c.toIntOrNull() ?: continue
            val pi = PendingIntent.getBroadcast(
                context, code, Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) am.cancel(pi)
        }
        sp.edit().remove(KEY_CODES).apply()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: return
        val text = intent.getStringExtra("text") ?: ""
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(intent.getIntExtra("code", 0), n)
        } catch (_: SecurityException) { /* notification permission not granted */ }
    }
}

/** Alarms are wiped on reboot, so rebuild them from the cached snapshot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val repo = (context.applicationContext as TandemApp).repo
        repo.state.value?.let { ReminderScheduler.reschedule(context, it.events) }
        SyncWorker.schedule(context)
    }
}
