package app.tandem

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.tandem.data.*
import app.tandem.update.UpdateInfo
import app.tandem.update.Updater
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import app.tandem.work.Notifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo: Repository = (app as TandemApp).repo
    val state = repo.state
    val loggedIn = repo.loggedIn
    val theme = repo.prefs.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)

    var busy by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var update by mutableStateOf<UpdateInfo?>(null); private set
    var updateProgress by mutableStateOf<Float?>(null); private set
    var checkingUpdate by mutableStateOf(false); private set

    /** Tab requested by tapping a notification (AppTab name), consumed by the shell. */
    var requestedTab by mutableStateOf<String?>(null)
    var stats by mutableStateOf<StatsDetail?>(null); private set
    var statsLoading by mutableStateOf(false); private set

    val notifyMoods = repo.prefs.notifyMoods.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifyDiary = repo.prefs.notifyDiary.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val notifyEvents = repo.prefs.notifyEvents.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val dismissedUpdate = repo.prefs.dismissedUpdate.stateIn(viewModelScope, SharingStarted.Eagerly, null as String?)

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts

    init {
        // Partner activity noticed while the app is open becomes an in-app banner.
        viewModelScope.launch { repo.incoming.collect { toast(it) } }
    }

    // While the app is on screen, sync every 30 s so partner updates show up (and are announced) quickly.
    private var pollJob: Job? = null
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (repo.loggedIn.value) try { repo.refresh() } catch (_: AppException) { }
            }
        }
    }
    fun stopPolling() { pollJob?.cancel(); pollJob = null }

    fun toast(msg: String) { _toasts.tryEmit(msg) }

    /** Runs [block] with the busy flag set; errors become toasts. [onSuccess] runs only if it worked. */
    fun busyRun(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            try { block(); onSuccess() }
            catch (e: AppException) { toast(e.message ?: "Something went wrong") }
            finally { busy = false }
        }
    }

    fun refresh(silent: Boolean = true) {
        viewModelScope.launch {
            refreshing = true
            try { repo.refresh() }
            catch (e: AppException) { if (!silent) toast(e.message ?: "Couldn't refresh") }
            finally { refreshing = false }
        }
    }

    fun login(u: String, p: String) = busyRun { repo.login(u, p) }
    fun register(u: String, p: String, name: String) = busyRun { repo.register(u, p, name) }
    fun logout() { viewModelScope.launch { repo.logout() } }
    fun createInvite() = busyRun { repo.createInvite() }
    fun join(code: String) = busyRun { repo.joinWithCode(code) }
    fun shareMood(m: Mood, a: Activity?, note: String, done: () -> Unit) = busyRun(done) { repo.shareMood(m, a, note) }
    fun addDiary(text: String, mood: Mood?, date: java.time.LocalDate?, images: List<Uri>, done: () -> Unit) = busyRun(done) {
        val failed = repo.addDiary(text, mood, date?.toString(), images)
        if (failed > 0) toast(if (failed == 1) "Note pinned, but one picture couldn't be uploaded" else "Note pinned, but $failed pictures couldn't be uploaded")
    }
    fun leaveCouple(done: () -> Unit) = busyRun(done) { repo.leaveCouple() }
    fun deleteAccount(password: String, done: () -> Unit) = busyRun(done) { repo.deleteAccount(password) }
    fun setNotifyMoods(v: Boolean) { viewModelScope.launch { repo.prefs.setNotifyMoods(v) } }
    fun setNotifyDiary(v: Boolean) { viewModelScope.launch { repo.prefs.setNotifyDiary(v) } }
    fun setNotifyEvents(v: Boolean) { viewModelScope.launch { repo.prefs.setNotifyEvents(v) } }
    fun testNotification() {
        val ctx = getApplication<Application>()
        if (!Notifier.enabled(ctx)) toast("Notifications are turned off for Tandem in your phone's settings")
        else Notifier.test(ctx)
    }

    fun loadStats() {
        viewModelScope.launch {
            statsLoading = true
            try { stats = repo.statsDetail() } catch (e: AppException) { toast(e.message ?: "Couldn't load stats") } finally { statsLoading = false }
        }
    }
    fun deleteDiary(id: String) = busyRun { repo.deleteDiary(id) }
    fun saveEvent(id: String?, e: EventBody, done: () -> Unit) = busyRun(done) { repo.saveEvent(id, e) }
    fun deleteEvent(id: String, done: () -> Unit) = busyRun(done) { repo.deleteEvent(id) }
    fun saveProfile(name: String, bio: String, done: () -> Unit) = busyRun(done) { repo.updateProfile(name, bio) }
    fun setAnniversary(date: String) = busyRun { repo.setAnniversary(date) }
    fun uploadAvatar(uri: Uri) = busyRun { repo.uploadAvatar(uri) }
    fun setTheme(mode: ThemeMode) { viewModelScope.launch { repo.prefs.setTheme(mode) } }
    fun setServer(url: String, done: () -> Unit) = busyRun(done) { repo.setBaseUrl(url) }

    /** [manual] shows feedback even if there is nothing new. */
    fun checkForUpdate(manual: Boolean) {
        viewModelScope.launch {
            checkingUpdate = true
            try {
                val info = Updater.check()
                update = info
                if (manual) toast(if (info == null) "You're on the latest version" else "Version ${info.version} is available")
            } catch (e: Exception) {
                if (manual) toast("Couldn't check for updates")
            } finally { checkingUpdate = false }
        }
    }

    fun installUpdate() {
        val info = update ?: return
        val ctx = getApplication<Application>()
        if (!Updater.canInstall(ctx)) {
            toast("Allow Tandem to install apps, then tap Install again")
            Updater.openInstallSettings(ctx)
            return
        }
        viewModelScope.launch {
            updateProgress = 0f
            try {
                val file = Updater.download(ctx, info) { updateProgress = it }
                Updater.install(ctx, file)
            } catch (e: Exception) {
                toast("Download failed. Try again in a moment.")
            } finally { updateProgress = null }
        }
    }

    private var lastAutoCheck = 0L

    /** Looks for a new GitHub release when the app opens (and at most every 30 min after). */
    fun autoCheckForUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastAutoCheck < 30 * 60_000L) return
        lastAutoCheck = now
        viewModelScope.launch { try { update = Updater.check() } catch (_: Exception) { } }
    }

    /** "Later" on the update dialog: don't pop it up again for this version (the banner and Profile badge stay). */
    fun dismissUpdateDialog() {
        val v = update?.version ?: return
        viewModelScope.launch { repo.prefs.setDismissedUpdate(v) }
    }
}
