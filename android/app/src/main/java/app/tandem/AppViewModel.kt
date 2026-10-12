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

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts

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
    fun addDiary(text: String, mood: Mood?, done: () -> Unit) = busyRun(done) { repo.addDiary(text, mood) }
    fun deleteDiary(id: String) = busyRun { repo.deleteDiary(id) }
    fun saveEvent(id: String?, e: EventBody, done: () -> Unit) = busyRun(done) { repo.saveEvent(id, e) }
    fun deleteEvent(id: String, done: () -> Unit) = busyRun(done) { repo.deleteEvent(id) }
    fun saveProfile(name: String, bio: String, done: () -> Unit) = busyRun(done) { repo.updateProfile(name, bio) }
    fun setAnniversary(date: String) = busyRun { repo.setAnniversary(date) }
    fun uploadAvatar(uri: Uri) = busyRun { repo.uploadAvatar(uri) }
    fun setTheme(mode: ThemeMode) { viewModelScope.launch { repo.prefs.setTheme(mode) } }
    fun setServer(url: String) { viewModelScope.launch { repo.setBaseUrl(url) } }

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

    suspend fun shouldAutoCheck(): Boolean {
        // Check at most once per app launch; prefs track the last version we already told the person about.
        return repo.prefs.seenUpdate.first() != "checked-${java.time.LocalDate.now()}"
    }

    fun autoCheckOncePerDay() {
        viewModelScope.launch {
            if (!shouldAutoCheck()) return@launch
            repo.prefs.setSeenUpdate("checked-${java.time.LocalDate.now()}")
            try { update = Updater.check() } catch (_: Exception) { }
        }
    }
}
