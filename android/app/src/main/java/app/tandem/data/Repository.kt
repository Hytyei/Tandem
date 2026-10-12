package app.tandem.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import app.tandem.BuildConfig
import app.tandem.widget.WidgetUpdater
import app.tandem.work.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/** Thrown with a message that is safe to show to the person. */
class AppException(message: String) : Exception(message)

class Repository(private val context: Context) {
    val prefs = Prefs(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    @Volatile private var token: String? = null
    @Volatile private var baseUrl: String = BuildConfig.API_URL
    @Volatile private var apiCache: Pair<String, TandemApi>? = null

    val http: OkHttpClient = ApiFactory.client { token }

    private val _state = MutableStateFlow<SyncResponse?>(null)
    /** Latest known snapshot of everything (also persisted so the app and widgets work offline). */
    val state: StateFlow<SyncResponse?> = _state.asStateFlow()

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    init {
        // One-time blocking read so the first frame already knows whether we're signed in.
        runBlocking {
            token = prefs.token.firstOrNull()
            prefs.baseUrl.firstOrNull()?.let { baseUrl = it }
            prefs.cache.firstOrNull()?.let { raw ->
                runCatching { AppJson.decodeFromString(SyncResponse.serializer(), raw) }.getOrNull()?.let { _state.value = it }
            }
        }
        _loggedIn.value = token != null
    }

    val currentBaseUrl: String get() = baseUrl

    private val api: TandemApi
        get() {
            val cached = apiCache
            if (cached != null && cached.first == baseUrl) return cached.second
            val created = ApiFactory.create(baseUrl, http)
            apiCache = baseUrl to created
            return created
        }

    suspend fun setBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        baseUrl = if (clean.isBlank()) BuildConfig.API_URL else clean
        prefs.setBaseUrl(if (clean.isBlank()) null else clean)
    }

    fun avatarUrl(user: User): String? =
        if (user.hasAvatar) "${baseUrl.trimEnd('/')}/avatar/${user.id}?v=${user.avatarVersion}" else null

    // ------------------------------------------------------------ error mapping
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string().orEmpty()
        val msg = runCatching { AppJson.decodeFromString(ErrorBody.serializer(), body).error }.getOrNull()
        if (e.code() == 401 && token != null && msg?.contains("sign in") == true) logout()
        throw AppException(msg?.takeIf { it.isNotBlank() } ?: "Server error (${e.code()})")
    } catch (e: IOException) {
        throw AppException("Can't reach the server. Check your connection and server address.")
    }

    // ------------------------------------------------------------ auth
    suspend fun register(username: String, password: String, displayName: String) = call {
        val r = api.register(RegisterBody(username.trim(), password, displayName.trim().ifBlank { username.trim() }))
        onAuth(r)
    }

    suspend fun login(username: String, password: String) = call {
        onAuth(api.login(LoginBody(username.trim(), password)))
    }

    private suspend fun onAuth(r: AuthResponse) {
        token = r.token
        prefs.setToken(r.token)
        _loggedIn.value = true
        refresh()
    }

    suspend fun logout() {
        token = null
        prefs.setToken(null)
        prefs.setCache(null)
        _state.value = null
        _loggedIn.value = false
        ReminderScheduler.cancelAll(context)
        WidgetUpdater.updateAll(context)
    }

    // ------------------------------------------------------------ sync
    /** Pulls everything from the server, persists it, reschedules reminders and refreshes widgets. */
    suspend fun refresh(): SyncResponse = syncMutex.withLock {
        val data = call { api.sync() }
        _state.value = data
        prefs.setCache(AppJson.encodeToString(SyncResponse.serializer(), data))
        ReminderScheduler.reschedule(context, data.events)
        WidgetUpdater.updateAll(context)
        data
    }

    // ------------------------------------------------------------ couple
    suspend fun createInvite(): CoupleInfo = call { api.createCouple() }.also { refresh() }
    suspend fun joinWithCode(code: String): CoupleInfo = call { api.joinCouple(JoinBody(code.trim())) }.also { refresh() }
    suspend fun setAnniversary(date: String) { call { api.setAnniversary(AnniversaryBody(date)) }; refresh() }

    // ------------------------------------------------------------ content
    suspend fun shareMood(mood: Mood, activity: Activity?, note: String) {
        call { api.postMood(MoodBody(mood.key, activity?.key, note.trim())) }
        refresh()
    }

    suspend fun addDiary(text: String, mood: Mood?) {
        call { api.postDiary(DiaryBody(text.trim(), mood?.key)) }
        refresh()
    }

    suspend fun deleteDiary(id: String) { call { api.deleteDiary(id) }; refresh() }

    suspend fun saveEvent(existingId: String?, e: EventBody) {
        call { if (existingId == null) api.addEvent(e) else api.updateEvent(existingId, e) }
        refresh()
    }

    suspend fun deleteEvent(id: String) { call { api.deleteEvent(id) }; refresh() }

    suspend fun updateProfile(displayName: String, bio: String) {
        call { api.updateProfile(ProfileBody(displayName.trim(), bio.trim())) }
        refresh()
    }

    /** Downscales the picked image to a 384px square JPEG so the upload stays tiny. */
    suspend fun uploadAvatar(uri: Uri) {
        val bytes = withContext(Dispatchers.IO) {
            val src = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                ?: throw AppException("Couldn't read that image")
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, 384, 384, true)
            ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
        }
        call { api.uploadAvatar(bytes.toRequestBody("image/jpeg".toMediaType())) }
        refresh()
    }

    // ------------------------------------------------------------ widget helpers
    /** Downloads (or reuses) a circular avatar bitmap for widgets. Returns null if the user has no picture. */
    suspend fun widgetAvatar(user: User, sizePx: Int = 120): Bitmap? = withContext(Dispatchers.IO) {
        val url = avatarUrl(user) ?: return@withContext null
        val file = File(context.cacheDir, "wavatar_${user.id}_${user.avatarVersion}_$sizePx.png")
        if (file.exists()) return@withContext BitmapFactory.decodeFile(file.path)
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val raw = resp.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: return@withContext null
                val round = circleCrop(raw, sizePx)
                file.outputStream().use { round.compress(Bitmap.CompressFormat.PNG, 100, it) }
                round
            }
        } catch (_: IOException) { null }
    }

    private fun circleCrop(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val side = minOf(src.width, src.height)
        val srcRect = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        canvas.drawBitmap(src, srcRect, Rect(0, 0, size, size), paint)
        return out
    }

    fun launch(block: suspend () -> Unit) { scope.launch { runCatching { block() } } }
}
