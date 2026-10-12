package app.tandem.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.tandem.MainActivity
import app.tandem.R
import app.tandem.data.*
import app.tandem.update.UpdateInfo
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter

const val CHANNEL_EVENTS = "events"
const val CHANNEL_PARTNER = "partner"
const val CHANNEL_UPDATES = "updates"
const val EXTRA_TAB = "tab"

fun createNotificationChannels(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel(CHANNEL_EVENTS, "Event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Reminders for your shared events, special days and anniversaries"
    })
    nm.createNotificationChannel(NotificationChannel(CHANNEL_PARTNER, "Partner activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "When your partner shares a mood, pins a diary note or adds/changes an event"
    })
    nm.createNotificationChannel(NotificationChannel(CHANNEL_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "New versions of Tandem"
    })
}

/**
 * Turns "what changed since last time" into notifications. There is no push server (no Firebase), so changes are
 * noticed whenever the app syncs: every ~30 s while it's open, every 15 min in the background, and on widget refresh.
 * Only the partner's activity is announced, never your own.
 */
object Notifier {
    private const val ID_MOOD = 1001
    private const val ID_DIARY = 1002
    private const val ID_EVENT = 1003
    private const val ID_ENDED = 1004
    private const val ID_UPDATE = 1005
    private const val ID_TEST = 1099

    fun enabled(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(context: Context, channel: String, id: Int, title: String, text: String, tab: String? = null) {
        if (!enabled(context)) return
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // On the lock screen only a generic line is shown (content appears after unlocking).
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Tandem").setContentText("New activity").build()
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true).setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) { /* permission revoked */ }
    }

    fun test(context: Context) =
        post(context, CHANNEL_PARTNER, ID_TEST, "Notifications work 🎉", "You'll see updates from your partner here.", null)

    fun notifyUpdate(context: Context, info: UpdateInfo) =
        post(context, CHANNEL_UPDATES, ID_UPDATE, "Tandem ${info.version} is available", "Tap to open the app and install the update.", "Profile")

    private data class Note(val id: Int, val title: String, val text: String, val tab: String)

    suspend fun onSync(context: Context, prefs: Prefs, data: SyncResponse, foreground: Boolean, inApp: (String) -> Unit) {
        val me = data.me.id
        val partner = data.couple.partner
        val seen = prefs.seen()

        if (!data.couple.paired || partner == null) {
            if (seen.initialised && seen.partnerId.isNotEmpty()) {
                // The partner ended the connection (when *we* do it, the seen-state is cleared first, so no notice).
                val note = Note(ID_ENDED, "Connection ended", "Your partner disconnected. You can connect with someone new from the Tandem app.", "Today")
                if (foreground) inApp(note.title) else post(context, CHANNEL_PARTNER, note.id, note.title, note.text, note.tab)
            }
            prefs.setSeen(SeenState(initialised = true, partnerId = ""))
            return
        }

        val partnerMoods = data.moods.filter { it.userId != me }
        val newestMood = partnerMoods.maxOfOrNull { it.createdAt } ?: 0L
        val currentEvents = data.events.associate { it.id to it.updatedAt }

        // First sync (or a new partner): remember the current state without announcing the existing history.
        if (!seen.initialised || seen.partnerId != partner.id) {
            prefs.setSeen(SeenState(true, partner.id, newestMood, data.diary.map { it.id }.toSet(), currentEvents))
            return
        }

        val name = partner.displayName.substringBefore(' ')
        val notes = mutableListOf<Note>()
        val fmt = DateTimeFormatter.ofPattern("EEE, d MMM")

        if (prefs.notifyMoods.first()) {
            partnerMoods.filter { it.createdAt > seen.lastMoodAt }.maxByOrNull { it.createdAt }?.let { m ->
                val feeling = Mood.from(m.mood)?.label
                val act = Activity.from(m.activity)?.label
                val text = when {
                    act != null && feeling != null -> "$act · feeling $feeling"
                    act != null -> act
                    feeling != null -> "Feeling $feeling"
                    else -> "Shared a mood"
                } + if (m.note.isNotBlank()) " — “${m.note}”" else ""
                notes += Note(ID_MOOD, name, text, "Timeline")
            }
        }
        if (prefs.notifyDiary.first()) {
            val fresh = data.diary.filter { it.userId != me && it.id !in seen.diaryIds }
            if (fresh.isNotEmpty()) {
                val photos = fresh.sumOf { it.images.size }
                val text = (if (fresh.size == 1) "Added a diary note" else "Added ${fresh.size} diary notes") + if (photos > 0) " with ${if (photos == 1) "a photo" else "$photos photos"} 📷" else ""
                notes += Note(ID_DIARY, name, text, "Diary")
            }
        }
        if (prefs.notifyEvents.first()) {
            for (e in data.events) {
                val before = seen.eventStamps[e.id]
                val label = (if (e.isSpecial) (SpecialCategory.from(e.category)?.emoji ?: "⭐") else e.emoji) + " " + e.title
                val day = e.nextDate()?.format(fmt) ?: parseDate(e.date)?.format(fmt) ?: e.date
                val kind = if (e.isSpecial) "special day" else "event"
                if (before == null && e.createdBy != me) notes += Note(ID_EVENT, name, "Added a $kind: $label · $day", "Calendar")
                else if (before != null && e.updatedAt > before && e.updatedBy != null && e.updatedBy != me)
                    notes += Note(ID_EVENT, name, "Updated a $kind: $label · $day", "Calendar")
            }
        }

        for (n in notes) {
            if (foreground) inApp("${n.title}: ${n.text}") else post(context, CHANNEL_PARTNER, n.id, n.title, n.text, n.tab)
        }
        prefs.setSeen(SeenState(
            true, partner.id, maxOf(seen.lastMoodAt, newestMood),
            (seen.diaryIds + data.diary.map { it.id }).toList().takeLast(400).toSet(), currentEvents,
        ))
    }
}
