package app.tandem.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("tandem")

enum class ThemeMode { System, Light, Dark }

class Prefs(private val context: Context) {
    private val kToken = stringPreferencesKey("token")
    private val kBase = stringPreferencesKey("base_url")
    private val kTheme = stringPreferencesKey("theme")
    private val kCache = stringPreferencesKey("sync_cache")
    private val kSeenUpdate = stringPreferencesKey("seen_update")

    val token: Flow<String?> = context.store.data.map { it[kToken] }
    val baseUrl: Flow<String?> = context.store.data.map { it[kBase] }
    val theme: Flow<ThemeMode> = context.store.data.map {
        runCatching { ThemeMode.valueOf(it[kTheme] ?: "System") }.getOrDefault(ThemeMode.System)
    }
    val cache: Flow<String?> = context.store.data.map { it[kCache] }
    val seenUpdate: Flow<String?> = context.store.data.map { it[kSeenUpdate] }

    suspend fun setToken(v: String?) = context.store.edit { prefs -> if (v == null) { prefs.remove(kToken) } else { prefs[kToken] = v } }
    suspend fun setBaseUrl(v: String?) = context.store.edit { prefs -> if (v.isNullOrBlank()) { prefs.remove(kBase) } else { prefs[kBase] = v.trim() } }
    suspend fun setTheme(v: ThemeMode) = context.store.edit { it[kTheme] = v.name }
    suspend fun setCache(v: String?) = context.store.edit { prefs -> if (v == null) { prefs.remove(kCache) } else { prefs[kCache] = v } }
    suspend fun setSeenUpdate(v: String) = context.store.edit { it[kSeenUpdate] = v }
}
