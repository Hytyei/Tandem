package app.tandem.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import app.tandem.BuildConfig
import app.tandem.widget.WidgetUpdater
import app.tandem.work.Notifier
import app.tandem.work.ReminderScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    val http: OkHttpClient = ApiFactory.client({ token }, { baseUrl.toHttpUrlOrNull()?.host })

    /** Partner activity seen while the app is open: shown as an in-app banner instead of a system notification. */
    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val incoming: SharedFlow<String> = _incoming

    private val _state = MutableStateFlow<SyncResponse?>(null)
    /** Latest known snapshot of everything (also persisted so the app and widgets work offline). */
    val state: StateFlow<SyncResponse?> = _state.asStateFlow()

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    init {
        // One-time blocking read so the first frame already knows whether we're signed in.
        runBlocking {
            prefs.migrateSecrets()
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
        if (clean.isNotBlank()) {
            val parsed = clean.toHttpUrlOrNull()
            if (parsed == null || !parsed.isHttps) throw AppException("The server address must start with https://")
        }
        baseUrl = if (clean.isBlank()) BuildConfig.API_URL else clean
        prefs.setBaseUrl(if (clean.isBlank()) null else clean)
    }

    fun avatarUrl(user: User): String? =
        if (user.hasAvatar) "${baseUrl.trimEnd('/')}/avatar/${user.id}?v=${user.avatarVersion}" else null

    /** Pictures attached to diary notes (only your partner and you can load them; the token is added automatically). */
    fun diaryImageUrl(id: String): String = "${baseUrl.trimEnd('/')}/diary/image/$id"

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
    } catch (e: kotlinx.serialization.SerializationException) {
        throw AppException("The server sent something unexpected. Make sure the latest backend is deployed.")
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
        prefs.clearSeen()
        prefs.clearAchievementsSeen()
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
        runCatching { ReminderScheduler.reschedule(context, data.events) }
        runCatching { WidgetUpdater.updateAll(context) }
        runCatching { Notifier.onSync(context, prefs, data, appInForeground) { _incoming.tryEmit(it) } }
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

    /** Pins a note (optionally dated for another day, with up to 4 pictures). Returns how many pictures failed to upload. */
    suspend fun addDiary(text: String, mood: Mood?, entryDate: String?, rating: Int?, dateType: DateType?, images: List<Uri>): Int {
        val entry = call { api.postDiary(DiaryBody(text.trim(), mood?.key, entryDate, rating, dateType?.key)) }
        val failed = uploadPictures(entry.id, images)
        refresh()
        return failed
    }

    /** Edits one of your own notes: new text/mood/date/rating/type, pictures removed, pictures added. Returns failed uploads. */
    suspend fun updateDiary(
        id: String, text: String, mood: Mood?, entryDate: String?, rating: Int?, dateType: DateType?,
        removeImages: List<String>, newImages: List<Uri>,
    ): Int {
        call { api.updateDiary(id, DiaryBody(text.trim(), mood?.key, entryDate, rating, dateType?.key)) }
        for (img in removeImages) try { call { api.deleteDiaryImage(img) } } catch (_: AppException) { }
        val failed = uploadPictures(id, newImages)
        refresh()
        return failed
    }

    private suspend fun uploadPictures(entryId: String, images: List<Uri>): Int {
        var failed = 0
        for (uri in images.take(4)) {
            try {
                val bytes = encodePhoto(uri)
                call { api.uploadDiaryImage(entryId, bytes.toRequestBody("image/jpeg".toMediaType())) }
            } catch (e: Exception) { failed++ }
        }
        return failed
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
            val src = decodeScaled(uri, 1024)
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, 384, 384, true)
            ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
        }
        call { api.uploadAvatar(bytes.toRequestBody("image/jpeg".toMediaType())) }
        refresh()
    }

    // ------------------------------------------------------------ partner, account, stats
    /** Ends the relationship. Nothing is deleted on the server: the history is kept for the pair. */
    suspend fun leaveCouple() {
        call { api.leaveCouple() }
        prefs.clearSeen()
        refresh()
    }

    /** Permanently deletes your account and your own notes, moods and pictures. */
    suspend fun deleteAccount(password: String) {
        call { api.deleteAccount(DeleteBody(password)) }
        logout()
    }

    suspend fun statsDetail(): StatsDetail = call { api.statsDetail(utcOffsetMinutes()) }
    suspend fun memories(): Memories = call { api.memories(java.time.LocalDate.now().toString()) }

    // ------------------------------------------------------------ images
    /** Decodes with the right rotation and a maximum side length. Re-encoding also strips EXIF (GPS, camera info). */
    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap {
        val resolver = context.contentResolver
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxSide) {
                        val f = maxSide.toFloat() / longest
                        decoder.setTargetSize((info.size.width * f).toInt().coerceAtLeast(1), (info.size.height * f).toInt().coerceAtLeast(1))
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: throw AppException("Couldn't read that image")
            val orientation = resolver.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            } ?: android.media.ExifInterface.ORIENTATION_NORMAL
            val m = Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            }
            val longest = maxOf(raw.width, raw.height)
            if (longest > maxSide) { val f = maxSide.toFloat() / longest; m.postScale(f, f) }
            return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        } catch (e: AppException) { throw e } catch (e: Exception) { throw AppException("Couldn't read that image") }
    }

    /** A diary photo: max 1280px, JPEG, squeezed under ~400 KB (the server accepts up to 450 KB). */
    private suspend fun encodePhoto(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        var bmp = decodeScaled(uri, 1280)
        var quality = 82
        var out: ByteArray
        do {
            out = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
            if (out.size > 400_000) {
                if (quality > 55) quality -= 9
                else bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * 0.85f).toInt(), (bmp.height * 0.85f).toInt(), true)
            }
        } while (out.size > 400_000)
        out
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

    /** Set by MainActivity so partner updates arrive as in-app banners while you're looking at the app. */
    @Volatile var appInForeground: Boolean = false

    fun launch(block: suspend () -> Unit) { scope.launch { runCatching { block() } } }
}
