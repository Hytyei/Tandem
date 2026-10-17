package app.tandem.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.tandem.AppViewModel
import app.tandem.TandemApp
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import app.tandem.work.TrackingService
import app.tandem.work.TrackingState
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun fmtDistance(m: Long): String = if (m < 1000) "$m m" else "%.1f km".format(m / 1000.0)
internal fun fmtDuration(ms: Long): String {
    val min = (ms / 60_000).coerceAtLeast(0)
    return if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
}
private fun fmtWhen(t: Long): String = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))

// ------------------------------------------------------------------ pictures of a place

/** The pictures of one place: a row of thumbnails (tap to enlarge) with an add button. Either of you can add or remove. */
@Composable
internal fun PlacePhotosSection(vm: AppViewModel, place: Place) {
    val c = T.colors
    val repo = (LocalContext.current.applicationContext as TandemApp).repo
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris -> if (uris.isNotEmpty()) vm.addPlaceImages(place.id, uris) }
    var open by remember { mutableStateOf<String?>(null) }
    var askDelete by remember { mutableStateOf<String?>(null) }
    Text("PICTURES", style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.secondary, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(
                Modifier.size(92.dp).clip(RoundedCornerShape(18.dp)).background(c.accent.copy(alpha = .12f))
                    .pressable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center,
            ) {
                if (vm.busy) CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.dp)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Add, "Add pictures", tint = c.accent)
                    Text("Add", style = Type.caption, color = c.accent)
                }
            }
        }
        items(place.images, key = { it }) { id ->
            AsyncImage(
                model = repo.placeImageUrl(id), contentDescription = "Picture of ${place.name}", contentScale = ContentScale.Crop,
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(18.dp)).background(c.separator).pressable(haptic = false) { open = id },
            )
        }
    }
    if (place.images.isEmpty()) Text("Add pictures from this place. You can both see them.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp))

    open?.let { id ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { open = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f)).pressable(haptic = false) { open = null }, contentAlignment = Alignment.Center) {
                AsyncImage(repo.placeImageUrl(id), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .18f)).pressable { askDelete = id }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text("Delete", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
                    }
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = .18f)).pressable { open = null }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Close, "Close", tint = Color.White)
                    }
                }
            }
        }
    }
    askDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { askDelete = null }, containerColor = c.sheet,
            title = { Text("Delete this picture?") }, text = { Text("It is removed for you both.") },
            confirmButton = { TextButton(onClick = { askDelete = null; open = null; vm.deletePlaceImage(id) }) { Text("Delete", color = c.danger) } },
            dismissButton = { TextButton(onClick = { askDelete = null }) { Text("Keep", color = c.secondary) } },
        )
    }
}

// ------------------------------------------------------------------ saved addresses

/** Your saved addresses and your partner's. Yours can be added, edited and removed; theirs are read-only. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AddressesSheet(vm: AppViewModel, mapCenter: Pair<Double, Double>, onDismiss: () -> Unit, onShow: (SavedAddress) -> Unit) {
    val c = T.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by vm.state.collectAsState()
    val data = s ?: return
    val me = data.me
    val pName = data.couple.partner?.displayName?.substringBefore(' ') ?: "Partner"
    val all = vm.addresses.orEmpty()
    val mine = all.filter { it.userId == me.id }
    val theirs = all.filter { it.userId != me.id }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var form by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var label by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AddressKind.Home) }
    var line by remember { mutableStateOf("") }
    var loc by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<SavedAddress?>(null) }

    LaunchedEffect(Unit) { vm.loadAddresses() }

    fun startForm(a: SavedAddress?) {
        editId = a?.id; label = a?.label ?: ""; kind = AddressKind.from(a?.kind); line = a?.address ?: ""
        loc = a?.let { it.lat to it.lon }; query = ""; results = emptyList(); hint = null; form = true
    }
    fun useMyLocation() {
        val l = lastKnownLocation(ctx)
        if (l != null) { loc = l; hint = null } else hint = "Couldn't get a location fix. Try the search instead."
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) useMyLocation() else hint = "Location permission was declined. You can still search."
    }
    fun search() {
        if (query.isBlank() || searching) return
        scope.launch {
            searching = true; hint = null
            try { results = vm.searchPlaces(query.trim()); if (results.isEmpty()) hint = "Nothing found. Try a different spelling." }
            catch (e: AppException) { hint = e.message } finally { searching = false }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            if (!form) {
                Text("Our addresses", style = Type.title, color = c.label)
                Text("Saved places like home show on the map. $pName sees yours too.", style = Type.callout, color = c.secondary, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                AddressGroup("Yours", mine, c.me, onTap = { onShow(it) }, onEdit = { startForm(it) }, onDelete = { confirmDelete = it })
                AddressGroup("$pName's", theirs, c.partner, onTap = { onShow(it) }, onEdit = null, onDelete = null)
                if (all.isEmpty() && vm.addresses != null) Text("Nothing saved yet.", style = Type.callout, color = c.tertiary, modifier = Modifier.padding(vertical = 8.dp))
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Add an address", onClick = { startForm(null) }, enabled = mine.size < 8)
                if (mine.size >= 8) Text("You can save up to 8.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(if (editId != null) "Edit address" else "New address", style = Type.title, color = c.label)
                Spacer(Modifier.height(12.dp))
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent,
                    focusedContainerColor = c.card, unfocusedContainerColor = c.card,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddressKind.entries.forEach { k ->
                        val on = k == kind
                        val shape = RoundedCornerShape(50)
                        Row(
                            Modifier.clip(shape).background(if (on) c.accent.copy(alpha = .16f) else c.card).border(1.dp, if (on) c.accent else c.separator, shape)
                                .pressable { kind = k; if (label.isBlank() || AddressKind.entries.any { it.label == label }) label = k.label }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Text(k.emoji); Spacer(Modifier.width(6.dp)); Text(k.label, style = Type.footnote, color = c.label) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(label, { if (it.length <= 40) label = it }, label = { Text("Name (e.g. My home)") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        query, { query = it }, placeholder = { Text("Search the address", color = c.tertiary) }, singleLine = true,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f), colors = fieldColors,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(50.dp).clip(CircleShape).background(c.accent).pressable { search() }, contentAlignment = Alignment.Center) {
                        if (searching) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Filled.Search, "Search", tint = Color.White)
                    }
                }
                Text("The search text is sent to OpenStreetMap (Nominatim), not to the Tandem server.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 4.dp))
                results.forEach { r ->
                    Column(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(c.card)
                            .pressable(haptic = false) { loc = r.lat to r.lon; line = (r.name + if (r.detail.isNotBlank()) ", ${r.detail}" else "").take(160); if (label.isBlank()) label = kind.label; results = emptyList(); query = "" }.padding(12.dp),
                    ) {
                        Text(r.name, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
                        if (r.detail.isNotBlank()) Text(r.detail, style = Type.caption, color = c.secondary, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Use my location", onClick = {
                        val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (has) useMyLocation() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    })
                    SoftButton("Use map centre", onClick = { loc = mapCenter; hint = null })
                }
                hint?.let { Text(it, style = Type.footnote, color = c.danger, modifier = Modifier.padding(top = 6.dp)) }
                Text(
                    loc?.let { (if (line.isNotBlank()) "📍 $line\n" else "📍 ") + "%.5f, %.5f".format(it.first, it.second) } ?: "No location chosen yet.",
                    style = Type.callout, color = if (loc != null) c.label else c.tertiary, modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    if (editId != null) "Save changes" else "Save address",
                    enabled = label.isNotBlank() && loc != null, loading = vm.busy,
                    onClick = {
                        val l = loc ?: return@PrimaryButton
                        vm.saveAddress(editId, AddressBody(label.trim(), kind.key, line.trim(), l.first, l.second)) { form = false }
                    },
                )
                Spacer(Modifier.height(8.dp))
                SoftButton("Back", onClick = { form = false }, color = c.secondary, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    confirmDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null }, containerColor = c.sheet,
            title = { Text("Remove ${a.label}?") }, text = { Text("It disappears from the map for you both.") },
            confirmButton = { TextButton(onClick = { confirmDelete = null; vm.deleteAddress(a.id) }) { Text("Remove", color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep", color = c.secondary) } },
        )
    }
}

@Composable
private fun AddressGroup(title: String, list: List<SavedAddress>, tint: Color, onTap: (SavedAddress) -> Unit, onEdit: ((SavedAddress) -> Unit)?, onDelete: ((SavedAddress) -> Unit)?) {
    val c = T.colors
    if (list.isEmpty()) return
    Text(title.uppercase(), style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.secondary, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    list.forEach { a ->
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(18.dp)).background(c.card).pressable(haptic = false) { onTap(a) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = .25f)), contentAlignment = Alignment.Center) { Text(AddressKind.from(a.kind).emoji) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.label, style = Type.headline, color = c.label, maxLines = 1)
                if (a.address.isNotBlank()) Text(a.address, style = Type.caption, color = c.secondary, maxLines = 2)
            }
            if (onEdit != null) Text("Edit", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.accent, modifier = Modifier.pressable { onEdit(a) }.padding(8.dp))
            if (onDelete != null) Icon(Icons.Filled.Close, "Remove", tint = c.secondary, modifier = Modifier.size(34.dp).pressable { onDelete(a) }.padding(8.dp))
        }
    }
}

// ------------------------------------------------------------------ live-tracked dates

/** Starts / joins / stops recording, including the permission prompts. Create it once per screen. */
internal class TripActions(
    val start: (title: String) -> Unit,
    val join: (id: String) -> Unit,
    val stopSharing: (id: String) -> Unit,
    val end: (id: String) -> Unit,
)

@Composable
internal fun rememberTripActions(vm: AppViewModel): TripActions {
    val ctx = LocalContext.current
    // what to do once the permission has been given
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val fine = res[Manifest.permission.ACCESS_FINE_LOCATION] == true || res[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val p = pending; pending = null
        if (fine) p?.invoke() else vm.toast("Location permission is needed to record a date")
    }
    fun withPermission(then: () -> Unit) {
        val has = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (has) { then(); return }
        pending = then
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        launcher.launch(list.toTypedArray())
    }
    return remember(vm, ctx) {
        TripActions(
            start = { title -> withPermission { vm.startTrip(title) { t -> TrackingService.start(ctx, t.id) } } },
            join = { id -> withPermission { vm.joinTrip(id) { t -> TrackingService.start(ctx, t.id) } } },
            stopSharing = { id -> TrackingService.stop(ctx); vm.leaveTrip(id) },
            end = { id -> TrackingService.stop(ctx); vm.endTrip(id) },
        )
    }
}

/** "We're on a date": start / join / stop, the live distance, and the list of earlier dates with totals. */
@Composable
internal fun TripsCard(vm: AppViewModel, actions: TripActions, onOpen: (Trip) -> Unit) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val me = data.me
    val pName = data.couple.partner?.displayName?.substringBefore(' ') ?: "Partner"
    val resp = vm.trips
    val active = data.trip ?: resp?.active
    val myPart = active?.member(me.id)
    val recording = myPart?.recording == true
    val localId by TrackingState.tripId.collectAsState()
    val localMeters by TrackingState.meters.collectAsState()
    var naming by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var confirmEnd by remember { mutableStateOf(false) }
    // keep the list and the live numbers fresh
    LaunchedEffect(active?.id, recording) { vm.loadTrips() }

    SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Text("📍  Dates we've tracked", style = Type.headline, color = c.label)
        Text("Record where you go together and see how far you walked. Only while a date is running, and only if you both choose to.", style = Type.caption, color = c.secondary, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        if (active != null) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.accent.copy(alpha = .10f)).padding(14.dp)) {
                Text(active.title, style = Type.headline, color = c.label)
                val meters = if (recording && localId == active.id && localMeters > 0) maxOf(localMeters.toLong(), myPart?.distanceM ?: 0) else (myPart?.distanceM ?: 0)
                Text(
                    when {
                        recording -> "Recording you · ${fmtDistance(meters)} so far"
                        myPart != null -> "You stopped sharing"
                        else -> "$pName started a date"
                    },
                    style = Type.callout, color = c.secondary, modifier = Modifier.padding(top = 2.dp),
                )
                active.members.filter { it.userId != me.id }.forEach { m ->
                    Text(if (m.recording) "$pName is recording · ${fmtDistance(m.distanceM)}" else "$pName isn't recording", style = Type.caption, color = c.secondary)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (recording) SoftButton("Stop sharing", onClick = { actions.stopSharing(active.id) }, color = c.secondary)
                    else SoftButton(if (myPart == null) "Join" else "Rejoin", onClick = { actions.join(active.id) })
                    SoftButton("End date", onClick = { confirmEnd = true }, color = c.danger)
                }
            }
        } else if (!naming) {
            PrimaryButton("We're on a date", onClick = { title = ""; naming = true })
        } else {
            OutlinedTextField(
                title, { if (it.length <= 60) title = it }, label = { Text("Name it (optional)") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card),
            )
            Text("A notification stays visible while your phone records your position. $pName can join from their phone.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { PrimaryButton("Start recording", loading = vm.busy, onClick = { naming = false; actions.start(title) }) }
                SoftButton("Cancel", onClick = { naming = false }, color = c.secondary)
            }
        }

        val past = resp?.trips.orEmpty().filter { !it.isActive }
        val tot = resp?.totals
        if (tot != null && tot.trips > 0) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripStat(Modifier.weight(1f), "${tot.trips}", if (tot.trips == 1) "date" else "dates")
                TripStat(Modifier.weight(1f), fmtDistance(tot.distanceM), "together")
                TripStat(Modifier.weight(1f), fmtDistance(tot.longestM), "longest")
            }
        }
        past.take(6).forEach { t ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(c.sheet.copy(alpha = .6f)).pressable { onOpen(t) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
                    Text(fmtWhen(t.startedAt), style = Type.caption, color = c.secondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(fmtDistance(t.distanceM), style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                    Text(fmtDuration(t.durationMs), style = Type.caption, color = c.secondary)
                }
            }
        }
    }
    if (confirmEnd && active != null) AlertDialog(
        onDismissRequest = { confirmEnd = false }, containerColor = c.sheet,
        title = { Text("End this date?") }, text = { Text("Recording stops for you both and the route is saved.") },
        confirmButton = { TextButton(onClick = { confirmEnd = false; actions.end(active.id) }) { Text("End date", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going", color = c.secondary) } },
    )
}

@Composable
private fun TripStat(modifier: Modifier, value: String, label: String) {
    val c = T.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.accent.copy(alpha = .10f)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Type.headline, color = c.accent, maxLines = 1)
        Text(label, style = Type.caption, color = c.secondary)
    }
}

/** One finished date: the route of each of you on a map, and the numbers. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TripDetailSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val d = vm.tripDetail ?: return
    val me = data.me
    val pName = data.couple.partner?.displayName?.substringBefore(' ') ?: "Partner"
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val camera = remember { MapCamera() }
    val paths = remember(d) {
        d.paths.map { p -> MapPath(if (p.userId == me.id) c.me else c.partner, p.points.mapNotNull { if (it.size >= 2) it[0] to it[1] else null }) }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var newTitle by remember(d.trip.title) { mutableStateOf(d.trip.title) }
    LaunchedEffect(d) { kotlinx.coroutines.delay(150); camera.fitPoints(paths.flatMap { it.points }, animated = false) }

    ModalBottomSheet(onDismissRequest = { vm.closeTrip(); onDismiss() }, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(d.trip.title, style = Type.title, color = c.label)
            Text(fmtWhen(d.trip.startedAt), style = Type.callout, color = c.secondary)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(22.dp))) {
                PlacesMap(places = emptyList(), camera = camera, modifier = Modifier.fillMaxSize(), heat = false, paths = paths)
                if (paths.all { it.points.size < 2 }) Text(
                    "No route was recorded for this date.", style = Type.footnote, color = c.label,
                    modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(14.dp)).background(c.sheet.copy(alpha = .9f)).padding(12.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TripStat(Modifier.weight(1f), fmtDistance(d.trip.distanceM), "distance")
                TripStat(Modifier.weight(1f), fmtDuration(d.trip.durationMs), "duration")
                val avg = if (d.trip.durationMs > 60_000) "%.1f km/h".format(d.trip.distanceM / 1000.0 / (d.trip.durationMs / 3_600_000.0)) else "–"
                TripStat(Modifier.weight(1f), avg, "average")
            }
            d.trip.members.forEach { m ->
                val mine = m.userId == me.id
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (mine) c.me else c.partner))
                    Spacer(Modifier.width(8.dp))
                    Text(if (mine) "You" else pName, style = Type.callout, color = c.label, modifier = Modifier.weight(1f))
                    Text("${fmtDistance(m.distanceM)} · ${m.points} points", style = Type.caption, color = c.secondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (renaming) {
                OutlinedTextField(newTitle, { if (it.length <= 60) newTitle = it }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("Save name", onClick = { if (newTitle.isNotBlank()) { vm.renameTrip(d.trip.id, newTitle); renaming = false } })
                    SoftButton("Cancel", onClick = { renaming = false }, color = c.secondary)
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Rename", onClick = { renaming = true })
                SoftButton("Delete", onClick = { confirmDelete = true }, color = c.danger)
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = c.sheet,
        title = { Text("Delete this date?") }, text = { Text("The recorded route is removed for you both.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteTrip(d.trip.id); onDismiss() }) { Text("Delete", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = c.secondary) } },
    )
}
