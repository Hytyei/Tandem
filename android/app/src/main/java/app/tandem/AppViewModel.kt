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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    fun addDiary(
        text: String, mood: Mood?, date: java.time.LocalDate?, rating: Int?, dateType: DateType?, placeId: String?,
        occasion: Occasion?, images: List<Uri>, done: () -> Unit,
    ) = busyRun(done) {
        val r = repo.addDiary(text, mood, date?.toString(), rating, dateType, placeId, occasion, images)
        val failed = r.failedPictures
        if (failed > 0) toast(if (failed == 1) "Note pinned, but one picture couldn't be uploaded" else "Note pinned, but $failed pictures couldn't be uploaded")
        else if (r.occasionFailed) toast("Note pinned, but the calendar occasion couldn't be added")
    }
    fun updateDiary(
        id: String, text: String, mood: Mood?, date: java.time.LocalDate?, rating: Int?, dateType: DateType?, placeId: String?,
        removeImages: List<String>, newImages: List<Uri>, done: () -> Unit,
    ) = busyRun(done) {
        val failed = repo.updateDiary(id, text, mood, date?.toString(), rating, dateType, placeId, removeImages, newImages)
        if (failed > 0) toast(if (failed == 1) "Saved, but one picture couldn't be uploaded" else "Saved, but $failed pictures couldn't be uploaded")
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
            try {
                val s = repo.statsDetail()
                stats = s
                announceAchievements(s)
            } catch (e: AppException) { toast(e.message ?: "Couldn't load stats") } finally { statsLoading = false }
        }
    }

    /** Toasts newly unlocked achievements (the first load for a partner just records what's already unlocked). */
    private suspend fun announceAchievements(s: StatsDetail) {
        val st = state.value ?: return
        val pid = st.couple.partner?.id ?: return
        val unlocked = unlockedAchievements(Achievements.context(daysTogether(st.couple.anniversary) ?: 0L, s)).map { "$pid:${it.id}" }.toSet()
        val seen = repo.prefs.achievementsSeen()
        if ("$pid:init" in seen) {
            val fresh = unlocked - seen
            Achievements.all.filter { "$pid:${it.id}" in fresh }.forEach { toast("🏆 Achievement unlocked: ${it.title}") }
        }
        repo.prefs.setAchievementsSeen(seen + unlocked + "$pid:init")
    }

    // ---- personal page
    fun saveProfileDetails(p: ProfileDetails, done: () -> Unit) = busyRun(done) { repo.saveProfileDetails(p) }
    fun addBirthdayToCalendar(name: String, date: String) = busyRun {
        repo.saveEvent(null, EventBody("$name's birthday", SpecialCategory.Birthday.emoji, date, null, true, "", kind = "special", category = SpecialCategory.Birthday.key))
        toast("Added to your calendar")
    }
    /** Poster / cover search for the "right now" fields. Throws AppException with a friendly message. */
    suspend fun lookupMedia(kind: String, query: String): List<MediaInfo> = repo.lookupMedia(kind, query)

    // ---- places (Heatmap)
    var places by mutableStateOf<List<Place>?>(null); private set
    fun loadPlaces() {
        viewModelScope.launch {
            try { places = repo.places() } catch (e: AppException) { if (places == null) toast(e.message ?: "Couldn't load places") }
        }
    }
    private fun replace(p: Place) { places = places.orEmpty().let { l -> if (l.any { it.id == p.id }) l.map { if (it.id == p.id) p else it } else listOf(p) + l } }
    fun addPlace(b: PlaceBody, done: () -> Unit) = busyRun(done) { replace(repo.addPlace(b)) }
    fun editPlace(id: String, b: PlaceBody, done: () -> Unit) = busyRun(done) { replace(repo.editPlace(id, b)) }
    fun visitPlace(id: String) = busyRun { replace(repo.visitPlace(id)) }
    fun reviewPlace(id: String, rating: Int?, comment: String, done: () -> Unit) = busyRun(done) { replace(repo.reviewPlace(id, rating, comment)) }
    fun deletePlace(id: String, done: () -> Unit) = busyRun(done) { repo.deletePlace(id); places = places.orEmpty().filter { it.id != id } }
    suspend fun searchPlaces(q: String): List<GeoResult> = repo.searchPlaces(q)
    fun addPlaceImages(placeId: String, uris: List<Uri>) = busyRun {
        val failed = repo.addPlaceImages(placeId, uris)
        places = repo.places()
        if (failed > 0) toast(if (failed == 1) "One picture couldn't be uploaded" else "$failed pictures couldn't be uploaded")
    }
    fun deletePlaceImage(id: String) = busyRun { repo.deletePlaceImage(id); places = repo.places() }

    // ---- saved addresses (home etc.)
    var addresses by mutableStateOf<List<SavedAddress>?>(null); private set
    fun loadAddresses() {
        viewModelScope.launch { try { addresses = repo.addresses() } catch (e: AppException) { if (addresses == null) toast(e.message ?: "Couldn't load addresses") } }
    }
    fun saveAddress(existingId: String?, b: AddressBody, done: () -> Unit) = busyRun(done) {
        repo.saveAddress(existingId, b); addresses = repo.addresses()
    }
    fun deleteAddress(id: String) = busyRun { repo.deleteAddress(id); addresses = addresses.orEmpty().filter { it.id != id } }

    // ---- period tracker (reloads the whole thing after each change: it is small, and the partner's view stays right)
    var period by mutableStateOf<PeriodResponse?>(null); private set
    fun loadPeriod() {
        viewModelScope.launch { try { period = repo.period() } catch (e: AppException) { if (period == null) toast(e.message ?: "Couldn't load the cycle tracker") } }
    }
    private fun periodChange(done: () -> Unit = {}, block: suspend () -> Unit) = busyRun(done) { block(); period = repo.period() }
    fun savePeriod(existingId: String?, b: PeriodBody, done: () -> Unit = {}) = periodChange(done) { repo.savePeriod(existingId, b) }
    fun deletePeriod(id: String, done: () -> Unit = {}) = periodChange(done) { repo.deletePeriod(id) }
    fun savePeriodDay(date: String, b: PeriodDayBody, done: () -> Unit = {}) = periodChange(done) { repo.savePeriodDay(date, b) }
    fun savePeriodSettings(b: PeriodSettingsBody, done: () -> Unit = {}) = periodChange(done) { repo.savePeriodSettings(b) }

    // ---- shared notes + checklists
    var notes by mutableStateOf<List<Note>?>(null); private set
    fun loadNotes() {
        viewModelScope.launch { try { notes = repo.notes() } catch (e: AppException) { if (notes == null) toast(e.message ?: "Couldn't load notes") } }
    }
    private fun putNote(n: Note) {
        val l = notes.orEmpty()
        notes = (if (l.any { it.id == n.id }) l.map { if (it.id == n.id) n else it } else listOf(n) + l).sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    }
    fun saveNote(existingId: String?, b: NoteBody, done: () -> Unit) = busyRun(done) { putNote(repo.saveNote(existingId, b)) }
    fun deleteNote(id: String) = busyRun { repo.deleteNote(id); notes = notes.orEmpty().filter { it.id != id } }
    /** Ticks / unticks right away (optimistic) and settles with the server's answer; a failure puts it back. */
    fun setItemDone(note: Note, item: NoteItem, done: Boolean) {
        putNote(note.copy(items = note.items.map { if (it.id == item.id) it.copy(done = done, doneBy = if (done) state.value?.me?.id else null) else it }))
        viewModelScope.launch {
            try { putNote(repo.setItemDone(item.id, done)) } catch (e: AppException) { putNote(note); toast(e.message ?: "Couldn't update the list") }
        }
    }
    fun addNoteItem(noteId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { try { putNote(repo.addNoteItem(noteId, text)) } catch (e: AppException) { toast(e.message ?: "Couldn't add the item") } }
    }
    fun editNoteItem(itemId: String, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { try { putNote(repo.setItemText(itemId, text)) } catch (e: AppException) { toast(e.message ?: "Couldn't change the item") } }
    }
    fun deleteNoteItem(itemId: String) {
        viewModelScope.launch { try { putNote(repo.deleteNoteItem(itemId)) } catch (e: AppException) { toast(e.message ?: "Couldn't remove the item") } }
    }

    // ---- live-tracked dates
    var trips by mutableStateOf<TripsResponse?>(null); private set
    var tripDetail by mutableStateOf<TripDetail?>(null); private set
    fun loadTrips() {
        viewModelScope.launch { try { trips = repo.trips() } catch (e: AppException) { if (trips == null) toast(e.message ?: "Couldn't load trips") } }
    }
    fun openTrip(id: String) {
        viewModelScope.launch { try { tripDetail = repo.tripDetail(id) } catch (e: AppException) { toast(e.message ?: "Couldn't load that trip") } }
    }
    fun closeTrip() { tripDetail = null }
    fun startTrip(title: String, done: (Trip) -> Unit) {
        viewModelScope.launch {
            busy = true
            try { val t = repo.startTrip(title); loadTrips(); done(t) } catch (e: AppException) { toast(e.message ?: "Couldn't start the date") } finally { busy = false }
        }
    }
    fun joinTrip(id: String, done: (Trip) -> Unit) {
        viewModelScope.launch {
            busy = true
            try { val t = repo.joinTrip(id); loadTrips(); done(t) } catch (e: AppException) { toast(e.message ?: "Couldn't join the date") } finally { busy = false }
        }
    }
    fun leaveTrip(id: String) = busyRun { repo.leaveTrip(id); loadTrips() }
    fun endTrip(id: String, done: () -> Unit = {}) = busyRun(done) { repo.endTrip(id); loadTrips() }
    fun renameTrip(id: String, title: String) = busyRun { repo.renameTrip(id, title); loadTrips(); tripDetail = tripDetail?.let { d -> if (d.trip.id == id) repo.tripDetail(id) else d } }
    fun deleteTrip(id: String) = busyRun { repo.deleteTrip(id); tripDetail = null; loadTrips() }

    // ---- reactions
    /** Reacts to a partner's post. No busy spinner: the sync that follows brings the new chip. */
    fun react(type: String, id: String, emoji: String?) {
        viewModelScope.launch { try { repo.react(type, id, emoji) } catch (e: AppException) { toast(e.message ?: "Couldn't react") } }
    }

    // ---- minigames
    var games by mutableStateOf<GamesResponse?>(null); private set
    fun loadGames() {
        viewModelScope.launch {
            try { games = repo.games() } catch (e: AppException) { if (games == null) toast(e.message ?: "Couldn't load games") }
        }
    }
    private fun replaceMatch(m: Match) {
        val g = games ?: GamesResponse()
        games = g.copy(matches = if (g.matches.any { it.id == m.id }) g.matches.map { if (it.id == m.id) m else it } else listOf(m) + g.matches)
    }
    fun startMatch(game: MiniGame, rounds: Int, done: (Match) -> Unit) {
        viewModelScope.launch {
            busy = true
            try { val m = repo.createMatch(game.key, rounds); replaceMatch(m); done(m) }
            catch (e: AppException) { toast(e.message ?: "Couldn't start the match") } finally { busy = false }
        }
    }
    /** Sends a finished round. The refreshed match (and goals/stats) come back through [loadGames]. */
    fun submitScore(matchId: String, round: Int, score: Int, perf: PerfBody?, done: (Match?) -> Unit) {
        viewModelScope.launch {
            busy = true
            var result: Match? = null
            try { result = repo.submitScore(matchId, round, score, perf); replaceMatch(result); loadGames() }
            catch (e: AppException) { toast(e.message ?: "Couldn't save your score") } finally { busy = false }
            done(result)
        }
    }
    fun cancelMatch(id: String) = busyRun { repo.cancelMatch(id); games = games?.copy(matches = games!!.matches.filter { it.id != id }) }
    fun addGoal(title: String, metric: String, target: Int, done: () -> Unit) = busyRun(done) { repo.addGoal(title, metric, target); games = repo.games() }
    fun settleGoal(id: String) = busyRun { repo.settleGoal(id); games = repo.games() }
    fun deleteGoal(id: String) = busyRun { repo.deleteGoal(id); games = repo.games() }

    var memories by mutableStateOf<Memories?>(null); private set
    fun loadMemories() {
        viewModelScope.launch {
            try { memories = repo.memories() } catch (_: AppException) { /* keep what we had; the section just stays quiet */ }
        }
    }

    /** Patch notes: shown once per version (and always available from Profile). */
    val notesSeen = repo.prefs.notesSeen.stateIn(viewModelScope, SharingStarted.Eagerly, "?" as String?)
    fun markNotesSeen(version: String) { viewModelScope.launch { repo.prefs.setNotesSeen(version) } }
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

    // A different partner (or signing out) means different data: forget what was loaded for the previous pair.
    // (Declared last so every property above is initialised before this starts collecting.)
    init {
        viewModelScope.launch {
            state.map { it?.couple?.partner?.id }.distinctUntilChanged().collect {
                places = null; memories = null; stats = null; games = null
                addresses = null; notes = null; period = null; trips = null; tripDetail = null
            }
        }
    }
}
