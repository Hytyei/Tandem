package app.tandem.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("tandem")

enum class ThemeMode { System, Light, Dark }

/** What we've already told the person about, so each partner update produces exactly one notification. */
data class SeenState(
    val initialised: Boolean = false,
    val partnerId: String = "",
    val lastMoodAt: Long = 0,
    val diaryIds: Set<String> = emptySet(),
    val eventStamps: Map<String, Long> = emptyMap(),
)

class Prefs(private val context: Context) {
    private val kToken = stringPreferencesKey("token")
    private val kBase = stringPreferencesKey("base_url")
    private val kTheme = stringPreferencesKey("theme")
    private val kCache = stringPreferencesKey("sync_cache")
    private val kSeenUpdate = stringPreferencesKey("seen_update")
    private val kDismissedUpdate = stringPreferencesKey("dismissed_update")
    private val kLastUpdateCheck = longPreferencesKey("last_update_check")
    private val kNotifiedUpdate = stringPreferencesKey("notified_update")
    private val kNotifyMoods = booleanPreferencesKey("notify_moods")
    private val kNotifyDiary = booleanPreferencesKey("notify_diary")
    private val kNotifyEvents = booleanPreferencesKey("notify_events")
    private val kNotifyGames = booleanPreferencesKey("notify_games")
    private val kSeenInit = booleanPreferencesKey("seen_init")
    private val kSeenPartner = stringPreferencesKey("seen_partner")
    private val kSeenMoodAt = longPreferencesKey("seen_mood_at")
    private val kSeenDiary = stringSetPreferencesKey("seen_diary")
    private val kSeenEvents = stringSetPreferencesKey("seen_events")
    private val kNotesSeen = stringPreferencesKey("patch_notes_seen")
    private val kAchSeen = stringSetPreferencesKey("achievements_seen")

    // Token and cached data are encrypted with a Keystore key (see SecureBox).
    val token: Flow<String?> = context.store.data.map { it[kToken]?.let(SecureBox::decrypt) }
    val baseUrl: Flow<String?> = context.store.data.map { it[kBase] }
    val theme: Flow<ThemeMode> = context.store.data.map {
        runCatching { ThemeMode.valueOf(it[kTheme] ?: "System") }.getOrDefault(ThemeMode.System)
    }
    val cache: Flow<String?> = context.store.data.map { it[kCache]?.let(SecureBox::decrypt) }
    val seenUpdate: Flow<String?> = context.store.data.map { it[kSeenUpdate] }
    val dismissedUpdate: Flow<String?> = context.store.data.map { it[kDismissedUpdate] }

    val notifyMoods: Flow<Boolean> = context.store.data.map { it[kNotifyMoods] ?: true }
    val notifyDiary: Flow<Boolean> = context.store.data.map { it[kNotifyDiary] ?: true }
    val notifyEvents: Flow<Boolean> = context.store.data.map { it[kNotifyEvents] ?: true }
    val notifyGames: Flow<Boolean> = context.store.data.map { it[kNotifyGames] ?: true }

    suspend fun setToken(v: String?) = context.store.edit { prefs -> if (v == null) { prefs.remove(kToken) } else { prefs[kToken] = SecureBox.encrypt(v) } }
    suspend fun setBaseUrl(v: String?) = context.store.edit { prefs -> if (v.isNullOrBlank()) { prefs.remove(kBase) } else { prefs[kBase] = v.trim() } }
    suspend fun setTheme(v: ThemeMode) = context.store.edit { it[kTheme] = v.name }
    suspend fun setCache(v: String?) = context.store.edit { prefs -> if (v == null) { prefs.remove(kCache) } else { prefs[kCache] = SecureBox.encrypt(v) } }
    suspend fun setSeenUpdate(v: String) = context.store.edit { it[kSeenUpdate] = v }
    suspend fun setDismissedUpdate(v: String) = context.store.edit { it[kDismissedUpdate] = v }
    suspend fun setNotifyMoods(v: Boolean) = context.store.edit { it[kNotifyMoods] = v }
    suspend fun setNotifyDiary(v: Boolean) = context.store.edit { it[kNotifyDiary] = v }
    suspend fun setNotifyEvents(v: Boolean) = context.store.edit { it[kNotifyEvents] = v }
    suspend fun setNotifyGames(v: Boolean) = context.store.edit { it[kNotifyGames] = v }

    /** Version whose patch notes the person has already seen. */
    val notesSeen: Flow<String?> = context.store.data.map { it[kNotesSeen] }
    suspend fun setNotesSeen(v: String) = context.store.edit { it[kNotesSeen] = v }

    /** Unlocked achievements already announced ("partnerId:achievementId", plus "partnerId:init" once the baseline is set). */
    suspend fun achievementsSeen(): Set<String> = context.store.data.first()[kAchSeen] ?: emptySet()
    suspend fun setAchievementsSeen(v: Set<String>) = context.store.edit { it[kAchSeen] = v }
    suspend fun clearAchievementsSeen() = context.store.edit { it.remove(kAchSeen) }

    suspend fun lastUpdateCheck(): Long = context.store.data.first()[kLastUpdateCheck] ?: 0L
    suspend fun setLastUpdateCheck(v: Long) = context.store.edit { it[kLastUpdateCheck] = v }
    suspend fun notifiedUpdate(): String? = context.store.data.first()[kNotifiedUpdate]
    suspend fun setNotifiedUpdate(v: String) = context.store.edit { it[kNotifiedUpdate] = v }

    suspend fun seen(): SeenState {
        val p = context.store.data.first()
        return SeenState(
            initialised = p[kSeenInit] ?: false,
            partnerId = p[kSeenPartner] ?: "",
            lastMoodAt = p[kSeenMoodAt] ?: 0L,
            diaryIds = p[kSeenDiary] ?: emptySet(),
            eventStamps = (p[kSeenEvents] ?: emptySet()).mapNotNull {
                val i = it.lastIndexOf(':'); if (i <= 0) null else it.substring(0, i) to (it.substring(i + 1).toLongOrNull() ?: 0L)
            }.toMap(),
        )
    }

    suspend fun setSeen(s: SeenState) = context.store.edit {
        it[kSeenInit] = s.initialised
        it[kSeenPartner] = s.partnerId
        it[kSeenMoodAt] = s.lastMoodAt
        it[kSeenDiary] = s.diaryIds
        it[kSeenEvents] = s.eventStamps.map { (k, v) -> "$k:$v" }.toSet()
    }

    suspend fun clearSeen() = setSeen(SeenState())

    /** One-time upgrade: older versions stored the token/cache as plain text. */
    suspend fun migrateSecrets() = context.store.edit { prefs ->
        prefs[kToken]?.let { if (!SecureBox.isEncrypted(it)) prefs[kToken] = SecureBox.encrypt(it) }
        prefs[kCache]?.let { if (!SecureBox.isEncrypted(it)) prefs[kCache] = SecureBox.encrypt(it) }
    }
}
