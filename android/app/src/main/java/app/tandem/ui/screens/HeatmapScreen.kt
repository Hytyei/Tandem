package app.tandem.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private fun shortDate(s: String?): String? = parseDate(s)?.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
private fun one(d: Double) = "%.1f".format(d)

/** The Heatmap tab: a map of the places you've been, glowing where you go most, plus rankings by your two reviews. */
@Composable
fun HeatmapScreen(vm: AppViewModel, switcher: @Composable () -> Unit = {}) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val me = data.me
    val partner = data.couple.partner
    val pName = partner?.displayName?.substringBefore(' ') ?: "Partner"
    val places = vm.places
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val camera = remember { MapCamera() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var heat by rememberSaveable { mutableStateOf(true) }
    var showDates by rememberSaveable { mutableStateOf(true) }
    var styleName by rememberSaveable { mutableStateOf(MapStyle.Chart.name) }
    val mapStyle = MapStyle.valueOf(styleName)
    var catFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var addAt by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var editing by remember { mutableStateOf<Place?>(null) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var selectedAddrId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddresses by remember { mutableStateOf(false) }
    val tripActions = rememberTripActions(vm)
    val addresses = vm.addresses.orEmpty()
    val selectedAddr = addresses.firstOrNull { it.id == selectedAddrId }

    LaunchedEffect(Unit) { vm.loadPlaces(); vm.loadAddresses(); vm.loadTrips() }
    LaunchedEffect(vm.trips) { vm.loadTripRoutes() }
    // every tracked date: its routes as trails (in each walker's colour) and a flag where it began
    val routes = remember(vm.tripRoutes) { vm.tripRoutes.values.sortedByDescending { it.trip.startedAt } }
    val trails = remember(routes, showDates) {
        if (!showDates) emptyList() else routes.flatMap { d ->
            d.paths.map { p -> MapPath(if (p.userId == me.id) c.me else c.partner, p.points.mapNotNull { if (it.size >= 2) it[0] to it[1] else null }) }
        }
    }
    val dateMarks = remember(routes, showDates) {
        if (!showDates) emptyList() else routes.mapNotNull { d ->
            val first = d.paths.flatMap { it.points }.filter { it.size >= 3 }.minByOrNull { it[2] } ?: d.paths.firstOrNull()?.points?.firstOrNull() ?: return@mapNotNull null
            DateMark(first[0], first[1], Instant.ofEpochMilli(d.trip.startedAt).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM")), c.special)
        }
    }
    LaunchedEffect(places) {
        if (!fitted && places != null) {
            fitted = true
            if (places.isNotEmpty()) { delay(120); camera.fit(places, animated = false) }
        }
    }

    fun locate() {
        val l = lastKnownLocation(ctx)
        if (l != null) scope.launch { camera.animateTo(l.first, l.second, 15.0) } else vm.toast("Couldn't find your location yet. Try again in a moment.")
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) locate() else vm.toast("Location permission is needed to centre the map on you")
    }
    fun locateWithPermission() {
        val has = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (has) locate() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    val list = places.orEmpty()
    val selected = list.firstOrNull { it.id == selectedId }
    val reviewed = list.filter { it.count > 0 }
    val overall = if (reviewed.isEmpty()) null else reviewed.sumOf { it.avg ?: 0.0 } / reviewed.size

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item {
            LargeTitle("Heatmap", subtitle = "Places we've been", trailing = {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.accent).pressable { addAt = null; editing = null; showAdd = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, "Add a place", tint = Color.White) }
            })
        }
        item { switcher() }

        // ---------- the map
        item {
            val shape = RoundedCornerShape(28.dp)
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(380.dp).clip(shape)
                    .border(1.dp, if (c.isDark) Color.White.copy(alpha = .12f) else Color.White.copy(alpha = .8f), shape),
            ) {
                PlacesMap(
                    places = list, camera = camera, modifier = Modifier.fillMaxSize(), selectedId = selectedId, heat = heat,
                    style = mapStyle, trails = trails, dateMarks = dateMarks,
                    pending = addAt.takeIf { showAdd },
                    onSelect = { selectedId = it },
                    addresses = addresses, meId = me.id, selectedAddressId = selectedAddrId, onSelectAddress = { selectedAddrId = it },
                    onLongPress = { lat, lon -> addAt = lat to lon; editing = null; showAdd = true },
                )
                // controls
                Column(Modifier.align(Alignment.TopEnd).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MapButton(Icons.Filled.Add, "Zoom in") { scope.launch { camera.zoomBy(1.0) } }
                    MapButton(Icons.Filled.Remove, "Zoom out") { scope.launch { camera.zoomBy(-1.0) } }
                    MapButton(Icons.Filled.CropFree, "Show all places") { scope.launch { if (list.isNotEmpty()) camera.fit(list) } }
                    MapButton(Icons.Filled.MyLocation, "My location") { locateWithPermission() }
                    MapButton(Icons.Filled.Home, "Saved addresses") { showAddresses = true }
                    MapButton(Icons.Filled.Whatshot, "Heat", on = heat) { heat = !heat }
                    MapButton(Icons.Filled.Route, "Tracked dates", on = showDates) { showDates = !showDates }
                }
                if (places != null && list.isEmpty()) Text(
                    "Tap + or long-press the map to add the first place you've been together",
                    style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.label, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp).clip(RoundedCornerShape(16.dp)).background(c.sheet.copy(alpha = .85f)).padding(14.dp),
                )
                if (places == null) CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.accent)
                // selected address
                if (selectedAddr != null && selected == null) {
                    val mineAddr = selectedAddr.userId == me.id
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = 26.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(c.sheet.copy(alpha = .96f)).pressable { showAddresses = true }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background((if (mineAddr) c.me else c.partner).copy(alpha = .3f)), contentAlignment = Alignment.Center) {
                            Text(AddressKind.from(selectedAddr.kind).emoji, fontSize = 21.sp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selectedAddr.label, style = Type.headline, color = c.label, maxLines = 1)
                            Text((if (mineAddr) "Yours" else "$pName's") + if (selectedAddr.address.isNotBlank()) " · ${selectedAddr.address}" else "", style = Type.caption, color = c.secondary, maxLines = 1)
                        }
                        Box(Modifier.size(28.dp).pressable(haptic = false) { selectedAddrId = null }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Close", tint = c.secondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                // selected place
                if (selected != null) {
                    val cat = PlaceCategory.from(selected.category)
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(start = 10.dp, end = 10.dp, bottom = 26.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(c.sheet.copy(alpha = .96f)).pressable { detailId = selected.id }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryTile(cat, 42.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(selected.name, style = Type.headline, color = c.label, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (selected.count > 0) { StarRating(selected.avg, size = 14.dp); Spacer(Modifier.width(6.dp)); Text(one(selected.avg ?: 0.0), style = Type.caption, color = c.secondary) }
                                else Text("Not rated yet", style = Type.caption, color = c.tertiary)
                                Text("  ·  ${selected.visits}×", style = Type.caption, color = c.secondary)
                            }
                        }
                        Text("Details ›", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(28.dp).pressable(haptic = false) { selectedId = null }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, "Close", tint = c.secondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // ---------- map look
        item {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MapStyle.entries.forEach { st ->
                    val on = st == mapStyle
                    Row(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) c.label.copy(alpha = .12f) else c.card)
                            .border(1.dp, if (on) c.label.copy(alpha = .3f) else c.label.copy(alpha = .06f), RoundedCornerShape(12.dp))
                            .pressable(haptic = false) { styleName = st.name }.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(st.background).border(1.dp, c.label.copy(alpha = .3f), CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text(st.label.substringBefore(' '), style = Type.caption, color = if (on) c.label else c.secondary, maxLines = 1)
                    }
                }
            }
        }

        // ---------- the numbers, and where your visits go
        if (list.isNotEmpty()) item {
            val shape = RoundedCornerShape(24.dp)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().clip(shape).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), shape).padding(18.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Figure(Modifier.weight(1f), "${list.size}", if (list.size == 1) "place" else "places")
                    Figure(Modifier.weight(1f), "${list.sumOf { it.visits }}", "visits")
                    Figure(Modifier.weight(1f), overall?.let { one(it) } ?: "–", "avg rating")
                }
                val byCat = PlaceCategory.entries.map { cat -> cat to list.filter { PlaceCategory.from(it.category) == cat }.sumOf { it.visits } }.filter { it.second > 0 }.sortedByDescending { it.second }
                val total = byCat.sumOf { it.second }.coerceAtLeast(1)
                Text("Where you go", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    byCat.forEach { (cat, n) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(cat.tint)) }
                }
                FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    byCat.take(6).forEach { (cat, n) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(cat.tint))
                            Spacer(Modifier.width(6.dp))
                            Text("${cat.label} ${n * 100 / total}%", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary)
                        }
                    }
                }
            }
        }

        // ---------- live-tracked dates
        item { TripsCard(vm, tripActions) { t -> vm.openTrip(t.id) } }

        // ---------- your favourites: the best rated, large
        val ranked = list.filter { it.count > 0 }.sortedWith(compareByDescending<Place> { it.avg ?: 0.0 }.thenByDescending { it.count }.thenByDescending { it.visits })
        if (ranked.isNotEmpty()) {
            item { SectionHeader("Your favourites") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ranked.take(5).size) { i -> FavouriteCard(ranked[i], i + 1) { detailId = ranked[i].id } }
                }
            }
        }

        // ---------- every place, as tiles you can filter by kind
        if (list.isNotEmpty()) {
            val cats = PlaceCategory.entries.filter { cat -> list.any { PlaceCategory.from(it.category) == cat } }
            item { SectionHeader("All places") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterPill("All", null, catFilter == null) { catFilter = null } }
                    items(cats.size) { i -> val cat = cats[i]; FilterPill(cat.label, cat.tint, catFilter == cat.key) { catFilter = if (catFilter == cat.key) null else cat.key } }
                }
            }
            val shown = list.filter { catFilter == null || PlaceCategory.from(it.category).key == catFilter }
                .sortedWith(compareByDescending<Place> { it.avg ?: -1.0 }.thenByDescending { it.visits })
            val rows = shown.chunked(2)
            items(rows.size, key = { "pr-" + rows[it].first().id }) { r ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows[r].forEach { p -> PlaceTile(Modifier.weight(1f), p) { detailId = p.id } }
                    if (rows[r].size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (showAdd) AddPlaceSheet(
        vm, editing, addAt, mapCenter = camera.lat() to camera.lon(),
        onDismiss = { showAdd = false; addAt = null; editing = null },
        onSaved = { lat, lon -> scope.launch { camera.animateTo(lat, lon, 15.0) } },
    )
    if (showAddresses) AddressesSheet(
        vm, mapCenter = camera.lat() to camera.lon(), onDismiss = { showAddresses = false },
        onShow = { a -> showAddresses = false; selectedAddrId = a.id; selectedId = null; scope.launch { camera.animateTo(a.lat, a.lon, 16.0) } },
    )
    if (vm.tripDetail != null) TripDetailSheet(vm, onDismiss = { vm.closeTrip() })
    detailId?.let { id ->
        PlaceSheet(
            vm, id, onDismiss = { detailId = null },
            onEdit = { p -> detailId = null; editing = p; addAt = null; showAdd = true },
            onShowOnMap = { p -> detailId = null; selectedId = p.id; scope.launch { camera.animateTo(p.lat, p.lon, 15.0) } },
        )
    }
}

@Composable
private fun MapButton(icon: ImageVector, desc: String, on: Boolean = false, onClick: () -> Unit) {
    val c = T.colors
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(if (on) c.accent else c.sheet.copy(alpha = .94f)).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (on) Color.White else c.label, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun Figure(modifier: Modifier, value: String, label: String) {
    val c = T.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Type.title.copy(fontSize = 24.sp, fontFeatureSettings = "tnum"), color = c.label)
        Text(label, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary)
    }
}

@Composable
private fun FilterPill(label: String, tint: Color?, on: Boolean, onClick: () -> Unit) {
    val c = T.colors
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White.copy(alpha = .14f) else c.card)
            .border(1.dp, if (on) Color.White.copy(alpha = .3f) else Color.White.copy(alpha = .07f), RoundedCornerShape(50))
            .pressable(haptic = false, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tint != null) { Box(Modifier.size(8.dp).clip(CircleShape).background(tint)); Spacer(Modifier.width(7.dp)) }
        Text(label, style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = if (on) c.label else c.secondary, maxLines = 1)
    }
}

/** A favourite place, large: its rank in serif, lit in its category's colour, with the rating and how often you went. */
@Composable
private fun FavouriteCard(p: Place, rank: Int, onClick: () -> Unit) {
    val c = T.colors
    val cat = PlaceCategory.from(p.category)
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.width(168.dp).height(196.dp).clip(shape).background(c.card)
            .drawBehind { drawCircle(Brush.radialGradient(listOf(cat.tint.copy(alpha = .38f), Color.Transparent), center = Offset(size.width * .85f, 0f), radius = size.width), size.width, Offset(size.width * .85f, 0f)) }
            .border(1.dp, cat.tint.copy(alpha = .25f), shape).pressable(haptic = false, onClick = onClick).padding(16.dp),
    ) {
        Text("$rank", style = Type.numberHuge.copy(fontSize = 44.sp, lineHeight = 46.sp), color = if (rank == 1) Color(0xFFFFD58A) else c.label)
        Spacer(Modifier.weight(1f))
        Text(cat.label, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, maxLines = 1)
        Text(p.name, style = Type.title.copy(fontSize = 18.sp, lineHeight = 22.sp), color = c.label, maxLines = 2)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StarRating(p.avg, size = 12.dp)
            Spacer(Modifier.width(6.dp))
            Text(one(p.avg ?: 0.0) + " · ${p.visits}×", style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"), color = c.secondary)
        }
    }
}

/** One place in the grid: a glass tile with a faint glow in its category's colour. */
@Composable
private fun PlaceTile(modifier: Modifier, p: Place, onClick: () -> Unit) {
    val c = T.colors
    val cat = PlaceCategory.from(p.category)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.height(132.dp).clip(shape).background(c.card)
            .drawBehind { drawCircle(Brush.radialGradient(listOf(cat.tint.copy(alpha = .22f), Color.Transparent), center = Offset(0f, 0f), radius = size.width * .9f), size.width * .9f, Offset.Zero) }
            .border(1.dp, Color.White.copy(alpha = .07f), shape).pressable(haptic = false, onClick = onClick).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(cat.tint))
            Spacer(Modifier.width(6.dp))
            Text(cat.label, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Text(p.name, style = Type.callout.copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 2)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.count > 0) { StarRating(p.avg, size = 11.dp); Spacer(Modifier.width(6.dp)); Text(one(p.avg ?: 0.0), style = Type.caption.copy(fontFeatureSettings = "tnum"), color = c.label) }
            else Text("Not rated", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
            Spacer(Modifier.weight(1f))
            Text("${p.visits}×", style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"), color = c.secondary)
        }
    }
}

@Composable
private fun CategoryTile(cat: PlaceCategory, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(cat.tint.copy(alpha = .20f)), contentAlignment = Alignment.Center) {
        Text(cat.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

// ------------------------------------------------------------------ add / edit a place

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddPlaceSheet(
    vm: AppViewModel, editing: Place?, initial: Pair<Double, Double>?, mapCenter: Pair<Double, Double>,
    onDismiss: () -> Unit, onSaved: (Double, Double) -> Unit,
) {
    val c = T.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var category by remember { mutableStateOf(if (editing != null) PlaceCategory.from(editing.category) else PlaceCategory.Restaurant) }
    var loc by remember { mutableStateOf(editing?.let { it.lat to it.lon } ?: initial) }
    var note by remember { mutableStateOf(editing?.note ?: "") }
    var visitsText by remember { mutableStateOf((editing?.visits ?: 1).toString()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pickDate by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun useMyLocation() {
        val l = lastKnownLocation(ctx)
        if (l != null) { loc = l; hint = null } else hint = "Couldn't get a location fix. Try the search instead."
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) useMyLocation() else hint = "Location permission was declined. You can still search or use the map."
    }
    fun search() {
        if (query.isBlank() || searching) return
        scope.launch {
            searching = true; hint = null
            try {
                results = vm.searchPlaces(query.trim())
                if (results.isEmpty()) hint = "Nothing found. Try a different spelling, or long-press the map instead."
            } catch (e: AppException) { hint = e.message } finally { searching = false }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(if (editing != null) "Edit place" else "Add a place", style = Type.title, color = c.label)
            Spacer(Modifier.height(12.dp))
            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent,
                focusedContainerColor = c.card, unfocusedContainerColor = c.card,
            )
            OutlinedTextField(name, { if (it.length <= 80) name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors)
            Spacer(Modifier.height(14.dp))

            Text("What kind of place?", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceCategory.entries.forEach { cat ->
                    val on = cat == category
                    val shape = RoundedCornerShape(50)
                    Row(
                        Modifier.clip(shape).background(if (on) cat.tint.copy(alpha = .20f) else c.card).border(1.dp, if (on) cat.tint else c.separator, shape)
                            .pressable { category = cat }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(cat.emoji, style = Type.callout); Spacer(Modifier.width(6.dp))
                        Text(cat.label, style = Type.footnote.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = c.label)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            Text("Where is it?", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Search a place or address", color = c.tertiary) }, singleLine = true,
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
                        .pressable(haptic = false) { loc = r.lat to r.lon; if (name.isBlank()) name = r.name.take(80); results = emptyList(); query = "" }.padding(12.dp),
                ) {
                    Text(r.name, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
                    if (r.detail.isNotBlank()) Text(r.detail, style = Type.caption, color = c.secondary, maxLines = 2)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Use my location", onClick = {
                    val has = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                        androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (has) useMyLocation() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                })
                SoftButton("Use map centre", onClick = { loc = mapCenter; hint = null })
            }
            hint?.let { Text(it, style = Type.footnote, color = c.danger, modifier = Modifier.padding(top = 6.dp)) }
            Text(
                loc?.let { "📍 ${"%.5f".format(it.first)}, ${"%.5f".format(it.second)}" } ?: "No location chosen yet. You can also long-press the map.",
                style = Type.callout, color = if (loc != null) c.label else c.tertiary, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(note, { if (it.length <= 200) note = it }, label = { Text("A note (optional)") }, maxLines = 3, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors)

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                visitsText, { v -> if (v.length <= 3 && v.all { it.isDigit() }) visitsText = v },
                label = { Text(if (editing == null) "Times you've been here (including before)" else "Times visited") },
                supportingText = { Text("Enter a number, 1 to 999") },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            )
            if (editing == null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).pressable(haptic = false) { pickDate = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("When was the latest time?", style = Type.footnote, color = c.secondary)
                        Text(if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")), style = Type.body, color = c.label)
                    }
                    Text("Change", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                if (editing != null) "Save changes" else "Add to our map",
                enabled = name.isNotBlank() && loc != null, loading = vm.busy,
                onClick = {
                    val l = loc ?: return@PrimaryButton
                    val visits = (visitsText.toIntOrNull() ?: 1).coerceIn(1, 999)
                    val body = PlaceBody(name.trim(), category.key, l.first, l.second, note.trim(), if (editing == null) date.toString() else null, visits)
                    if (editing != null) vm.editPlace(editing.id, body) { onSaved(l.first, l.second); onDismiss() }
                    else vm.addPlace(body) { onSaved(l.first, l.second); onDismiss() }
                },
            )
        }
    }

    if (pickDate) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; pickDate = false }) { Text("OK", color = c.accent) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel", color = c.secondary) } },
        ) { DatePicker(state = state) }
    }
}

// ------------------------------------------------------------------ a place: reviews, visits, notes that mention it

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceSheet(vm: AppViewModel, placeId: String, onDismiss: () -> Unit, onEdit: (Place) -> Unit, onShowOnMap: (Place) -> Unit) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val me = data.me
    val partner = data.couple.partner
    val pName = partner?.displayName?.substringBefore(' ') ?: "Partner"
    val place = vm.places?.firstOrNull { it.id == placeId }
    LaunchedEffect(place == null) { if (place == null && vm.places != null) onDismiss() }
    if (place == null) return
    val cat = PlaceCategory.from(place.category)
    val mine = place.reviewOf(me.id)
    val theirs = partner?.let { place.reviewOf(it.id) }
    var myRating by remember(place.id, mine?.updatedAt) { mutableStateOf(mine?.rating) }
    var myComment by remember(place.id, mine?.updatedAt) { mutableStateOf(mine?.comment ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val posts = remember(data.diary, place.id) { data.diary.filter { it.placeId == place.id }.take(5) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryTile(cat, 54.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = Type.title, color = c.label)
                    Text(cat.label, style = Type.callout, color = c.secondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (place.count > 0) {
                    StarRating(place.avg, size = 22.dp); Spacer(Modifier.width(8.dp))
                    Text("${one(place.avg ?: 0.0)} · ${place.count} review${if (place.count == 1) "" else "s"}", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                } else Text("No reviews yet", style = Type.callout, color = c.tertiary)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Visited ${place.visits} time${if (place.visits == 1) "" else "s"}", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                    val range = listOfNotNull(shortDate(place.firstVisited)?.let { "first $it" }, shortDate(place.lastVisited)?.takeIf { place.visits > 1 }?.let { "last $it" }).joinToString(" · ")
                    if (range.isNotEmpty()) Text(range, style = Type.caption, color = c.secondary)
                }
                SoftButton("We went again", onClick = { vm.visitPlace(place.id) })
            }
            if (place.note.isNotBlank()) Text("“${place.note}”", style = Type.callout, color = c.label, modifier = Modifier.padding(top = 10.dp))

            PlacePhotosSection(vm, place)

            // ----- reviews
            SectionTitle("Your review")
            StarRating(myRating?.toDouble(), size = 34.dp, onChange = { myRating = it })
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                myComment, { if (it.length <= 200) myComment = it }, placeholder = { Text("What did you think? (optional)", color = c.tertiary) }, maxLines = 3,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card),
            )
            Spacer(Modifier.height(8.dp))
            val changed = myRating != mine?.rating || myComment != (mine?.comment ?: "")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    PrimaryButton(if (mine == null) "Save my review" else "Update my review", enabled = myRating != null && changed, loading = vm.busy, onClick = { vm.reviewPlace(place.id, myRating, myComment) {} })
                }
                if (mine != null) SoftButton("Remove", color = c.danger, onClick = { vm.reviewPlace(place.id, null, "") {} })
            }

            SectionTitle("$pName's review")
            if (theirs != null) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(12.dp), verticalAlignment = Alignment.Top) {
                    Avatar(partner, 36.dp, ring = c.partner)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        StarRating(theirs.rating.toDouble(), size = 18.dp)
                        if (theirs.comment.isNotBlank()) Text(theirs.comment, style = Type.callout, color = c.label, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            } else Text("$pName hasn't reviewed this place yet.", style = Type.callout, color = c.tertiary)

            if (posts.isNotEmpty()) {
                SectionTitle("Diary notes from here")
                posts.forEach { d ->
                    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(14.dp)).background(c.card).padding(12.dp)) {
                        Text(d.day().format(DateTimeFormatter.ofPattern("d MMM yyyy")) + " · " + (if (d.userId == me.id) "You" else pName), style = Type.caption, color = c.secondary)
                        Text(d.text, style = Type.callout, color = c.label, maxLines = 3)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SoftButton("Show on map", onClick = { onShowOnMap(place) })
                SoftButton("Edit", onClick = { onEdit(place) })
                SoftButton("Delete", color = c.danger, onClick = { confirmDelete = true })
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = c.sheet,
        title = { Text("Delete ${place.name}?") },
        text = { Text("The place and both reviews are removed for you both. Diary notes that mention it stay, they just lose the place tag.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deletePlace(place.id) { onDismiss() } }) { Text("Delete", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = c.secondary) } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = Type.footnote.copy(letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium), color = T.colors.secondary, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
}
