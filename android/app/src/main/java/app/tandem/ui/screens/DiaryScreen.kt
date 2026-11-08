package app.tandem.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandem.AppViewModel
import app.tandem.R
import app.tandem.TandemApp
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The diary as a social-style feed: newest day first, each note a post with the author, date, pictures and text. */
@Composable
fun DiaryScreen(vm: AppViewModel, switcher: @Composable () -> Unit = {}) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    var compose by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var deleting by remember { mutableStateOf<DiaryEntry?>(null) }
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
            item { LargeTitle("Diary", subtitle = "Our feed") }
        item { switcher() }

            // the composer: a quiet card that invites a note
            item {
                val shape = RoundedCornerShape(24.dp)
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(shape).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), shape)
                        .pressable { compose = true }.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(data.me, 36.dp, ring = c.me)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Write a note", style = Type.title.copy(fontSize = 18.sp), color = c.label)
                        Text("A memory, a thought, a thank you", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary)
                    }
                    Icon(Icons.Filled.AddPhotoAlternate, "Add photos", tint = c.secondary, modifier = Modifier.size(22.dp))
                }
            }

            // one note from the past: this day last year, a month ago, or something from before
            item { FromThePastCard(vm, data, Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) }

            if (data.diary.isEmpty()) item {
                EmptyState("Nothing here yet", "Write the first note: a memory, a thought, a thank you. You can add photos and pick any date.")
            }
            // notes under the day they belong to, newest first
            val byDay = data.diary.groupBy { it.day() }
            byDay.forEach { (day, notes) ->
                item(key = "d-$day") { FeedDay(day) }
                items(notes, key = { it.id }) { e ->
                    val mine = e.userId == data.me.id
                    PostCard(
                        e, if (mine) data.me else data.couple.partner, mine, data.me.id,
                        onReact = { emoji -> vm.react("diary", e.id, emoji) },
                        onEdit = { editing = e },
                        onDelete = { deleting = e },
                        onOpenImage = { i -> viewer = e.images to i },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 112.dp).size(58.dp).clip(CircleShape)
                .background(Color(0xFFF6F2FF)).pressable { compose = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = "New note", tint = Color(0xFF1A1240), modifier = Modifier.size(28.dp)) }
    }

    if (compose) DiarySheet(vm, existing = null, onDismiss = { compose = false })
    editing?.let { e -> DiarySheet(vm, existing = e, onDismiss = { editing = null }) }

    viewer?.let { (ids, start) -> ImageViewer(ids, start, onClose = { viewer = null }) }

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this note?") },
            text = { Text(if (entry.images.isEmpty()) "It will disappear from the feed for both of you." else "The note and its ${entry.images.size} picture${if (entry.images.size == 1) "" else "s"} will disappear for both of you.") },
            confirmButton = { TextButton(onClick = { vm.deleteDiary(entry.id); deleting = null }) { Text("Delete", color = c.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = c.secondary) } },
            containerColor = c.sheet,
        )
    }
}

/** The day a group of notes belongs to: the date large in serif, the weekday and month beside it. */
@Composable
private fun FeedDay(day: LocalDate) {
    val c = T.colors
    val today = LocalDate.now()
    val label = when (day) { today -> "Today"; today.minusDays(1) -> "Yesterday"; else -> day.format(DateTimeFormatter.ofPattern("EEEE")) }
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${day.dayOfMonth}", style = Type.numberHuge.copy(fontSize = 34.sp, lineHeight = 36.sp), color = c.label)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.label)
            Text(day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "MMMM" else "MMMM yyyy")), style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary)
        }
    }
}

/** A quiet glass label (a place, what you did). */
@Composable
private fun NoteTag(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, tint: Color? = null) {
    val c = T.colors
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .06f)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(50)).padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = tint ?: c.secondary, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(5.dp)) }
        Text(text, style = Type.caption.copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 1)
    }
}

/** The pictures of a note as a mosaic: one wide, two side by side, or one large with two stacked beside it. */
@Composable
private fun PhotoMosaic(ids: List<String>, onOpen: (Int) -> Unit) {
    val c = T.colors
    val repo = (LocalContext.current.applicationContext as TandemApp).repo
    val r = RoundedCornerShape(16.dp)
    @Composable
    fun Pic(i: Int, m: Modifier, more: Int = 0) {
        Box(m.clip(r).background(c.separator).pressable(haptic = false) { onOpen(i) }) {
            AsyncImage(model = repo.diaryImageUrl(ids[i]), contentDescription = "Photo ${i + 1}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (more > 0) Box(Modifier.fillMaxSize().background(Color(0x99120D34)), contentAlignment = Alignment.Center) {
                Text("+$more", style = Type.title, color = Color.White)
            }
        }
    }
    when (ids.size) {
        1 -> Pic(0, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        2 -> Row(Modifier.fillMaxWidth().aspectRatio(2f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Pic(0, Modifier.weight(1f).fillMaxHeight()); Pic(1, Modifier.weight(1f).fillMaxHeight())
        }
        else -> Row(Modifier.fillMaxWidth().aspectRatio(1.5f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Pic(0, Modifier.weight(2f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Pic(1, Modifier.weight(1f).fillMaxWidth())
                Pic(2, Modifier.weight(1f).fillMaxWidth(), more = ids.size - 3)
            }
        }
    }
}

@Composable
private fun PostCard(
    e: DiaryEntry, author: User?, mine: Boolean, meId: String, onReact: (String?) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onOpenImage: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    val c = T.colors
    val shape = RoundedCornerShape(24.dp)
    val day = remember(e) { e.day() }
    val written = remember(e.createdAt) { Instant.ofEpochMilli(e.createdAt).atZone(java.time.ZoneId.systemDefault()) }
    var menu by remember { mutableStateOf(false) }
    val mood = Mood.from(e.mood)
    val type = DateType.from(e.dateType)
    // a short note reads like a quote; a long one like a letter
    val short = e.text.length <= 140

    Column(
        modifier.fillMaxWidth().clip(shape).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), shape).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(author, 32.dp, ring = if (mine) c.me else c.partner)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(author?.displayName?.substringBefore(' ') ?: "", style = Type.callout.copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 1)
                Text(
                    (if (day != written.toLocalDate()) "Written ${timeAgo(e.createdAt)}" else written.format(DateTimeFormatter.ofPattern("HH:mm"))) + if (e.updatedAt > 0) " · edited" else "",
                    style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"), color = c.tertiary,
                )
            }
            if (mood != null) { Art(mood.art, 30.dp); Spacer(Modifier.width(4.dp)) }
            if (mine) Box {
                Box(Modifier.size(36.dp).pressable(haptic = false) { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreHoriz, "More", tint = c.secondary)
                }
                DropdownMenu(menu, { menu = false }, containerColor = c.sheet) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null, tint = c.secondary) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete", color = c.danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }

        if (e.images.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            PhotoMosaic(e.images, onOpenImage)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            e.text,
            style = if (short) Type.title.copy(fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal) else Type.body,
            color = c.label,
        )

        if (e.rating != null || mood != null || type != null || e.placeName != null) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (e.rating != null) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                    HeartRating(e.rating, size = 15.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(ratingLabel(e.rating), style = Type.caption.copy(fontWeight = FontWeight.Medium), color = c.accent)
                }
                e.placeName?.let { NoteTag(it, Icons.Filled.Place, c.special) }
                type?.let { NoteTag(it.label) }
                mood?.let { NoteTag("Feeling ${it.label.lowercase()}") }
            }
        }
        ReactionBar(e.reactions, meId, canReact = !mine, onReact = onReact, modifier = Modifier.padding(top = 10.dp))
    }
}

/** Full-screen swipeable photo viewer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageViewer(ids: List<String>, start: Int, onClose: () -> Unit) {
    val repo = (LocalContext.current.applicationContext as TandemApp).repo
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (ids.size - 1).coerceAtLeast(0))) { ids.size }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                AsyncImage(repo.diaryImageUrl(ids[page]), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp).size(42.dp).clip(CircleShape).background(Color(0x66000000)).pressable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, "Close", tint = Color.White) }
            if (ids.size > 1) Text(
                "${pager.currentPage + 1} / ${ids.size}", color = Color.White, style = Type.callout,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),
            )
        }
    }
}

/** New note, or (with [existing]) edit one of your own notes: text, date, pictures, rating, what you did, mood. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiarySheet(vm: AppViewModel, existing: DiaryEntry?, onDismiss: () -> Unit) {
    val c = T.colors
    val repo = (LocalContext.current.applicationContext as TandemApp).repo
    val editing = existing != null
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var mood by remember { mutableStateOf(Mood.from(existing?.mood)) }
    var date by remember { mutableStateOf(existing?.day() ?: LocalDate.now()) }
    var rating by remember { mutableStateOf(existing?.rating) }
    var dateType by remember { mutableStateOf(DateType.from(existing?.dateType)) }
    var pickDate by remember { mutableStateOf(false) }
    var placeId by remember { mutableStateOf(existing?.placeId) }
    var pickPlace by remember { mutableStateOf(false) }
    var asOccasion by remember { mutableStateOf(false) }
    var occTitle by remember { mutableStateOf("") }
    var occYearly by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (vm.places == null) vm.loadPlaces() }
    val placeName = vm.places?.firstOrNull { it.id == placeId }?.name ?: existing?.takeIf { it.placeId == placeId }?.placeName
    var keep by remember { mutableStateOf(existing?.images ?: emptyList()) }   // pictures already on the server
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }          // newly picked pictures
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val room = 4 - keep.size

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        photos = (photos + uris).distinct().take(room)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(if (editing) "Edit note" else "New note", style = Type.title, color = c.label)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                text, { if (it.length <= 2000) text = it },
                placeholder = { Text("What's on your mind?", color = c.tertiary) },
                shape = RoundedCornerShape(20.dp), minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent,
                    focusedContainerColor = c.card, unfocusedContainerColor = c.card),
            )
            Spacer(Modifier.height(12.dp))

            // date
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).pressable(haptic = false) { pickDate = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = c.accent)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Date of this memory", style = Type.footnote, color = c.secondary)
                    Text(if (date == LocalDate.now()) "Today" else date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), style = Type.body, color = c.label)
                }
                Text("Change", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
            }
            Spacer(Modifier.height(14.dp))

            // rating
            Text("How special was it? (optional)", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeartRating(rating, onChange = { rating = it })
                Spacer(Modifier.width(8.dp))
                Text(if (rating == null) "Tap a heart" else ratingLabel(rating), style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = if (rating == null) c.tertiary else c.accent)
            }
            Spacer(Modifier.height(12.dp))

            // what you did
            Text("What did you do? (optional)", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(6.dp))
            DateTypeChips(dateType, { dateType = it })
            Spacer(Modifier.height(14.dp))

            // place
            Text("Where? (optional)", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).pressable(haptic = false) { pickPlace = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Place, null, tint = c.special)
                Spacer(Modifier.width(12.dp))
                Text(placeName ?: "Pick a place from the Heatmap", style = Type.body, color = if (placeName != null) c.label else c.tertiary, modifier = Modifier.weight(1f), maxLines = 1)
                if (placeId != null) Box(Modifier.size(32.dp).pressable(haptic = false) { placeId = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Close, "Remove place", tint = c.secondary, modifier = Modifier.size(18.dp))
                } else Text("Choose", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
            }
            Spacer(Modifier.height(14.dp))

            // photos
            Text("Photos (up to 4)", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                keep.forEach { id ->
                    Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp))) {
                        AsyncImage(repo.diaryImageUrl(id), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(c.separator))
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(Color(0xAA000000)).pressable(haptic = false) { keep = keep - id },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
                photos.forEach { uri ->
                    Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp))) {
                        AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(Color(0xAA000000)).pressable(haptic = false) { photos = photos - uri },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
                if (keep.size + photos.size < 4) Box(
                    Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(c.card).border(1.dp, c.separator, RoundedCornerShape(16.dp))
                        .pressable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.AddPhotoAlternate, "Add photos", tint = c.accent, modifier = Modifier.size(30.dp)) }
            }

            Spacer(Modifier.height(14.dp))
            Text("Feeling (optional)", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(6.dp))
            MoodGrid(mood, { mood = it })

            if (!editing) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Event, null, tint = c.special)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Add to calendar as an occasion", style = Type.body, color = c.label)
                            Text("Pins ${if (date == LocalDate.now()) "today" else "this date"} as a special day", style = Type.footnote, color = c.secondary)
                        }
                        Switch(asOccasion, {
                            asOccasion = it
                            if (it && occTitle.isBlank()) occTitle = text.lineSequence().firstOrNull().orEmpty().trim().take(40)
                        }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
                    }
                    if (asOccasion) {
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            occTitle, { if (it.length <= 60) occTitle = it }, label = { Text("Occasion name") }, singleLine = true,
                            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Repeat every year", style = Type.callout, color = c.label, modifier = Modifier.weight(1f))
                            Switch(occYearly, { occYearly = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            val label = when {
                editing -> "Save changes"
                photos.isEmpty() -> "Post"
                else -> "Post with ${photos.size} photo${if (photos.size == 1) "" else "s"}"
            }
            PrimaryButton(
                label,
                onClick = {
                    if (existing != null) vm.updateDiary(existing.id, text, mood, date, rating, dateType, placeId, existing.images - keep.toSet(), photos, onDismiss)
                    else vm.addDiary(
                        text, mood, date, rating, dateType, placeId,
                        if (asOccasion) Occasion(occTitle.trim(), occYearly) else null, photos, onDismiss,
                    )
                },
                enabled = text.isNotBlank() && (!asOccasion || editing || occTitle.isNotBlank()), loading = vm.busy,
            )
        }
    }

    if (pickPlace) {
        val list = vm.places
        AlertDialog(
            onDismissRequest = { pickPlace = false }, containerColor = c.sheet,
            title = { Text("Where was this?") },
            text = {
                when {
                    list == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
                    list.isEmpty() -> Text("You haven't added any places yet. Add some in the Heatmap tab, then pick them here.", color = c.secondary)
                    else -> Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                        list.sortedBy { it.name.lowercase() }.forEach { p ->
                            val cat = PlaceCategory.from(p.category)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable(haptic = false) { placeId = p.id; pickPlace = false }.padding(horizontal = 6.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(cat.emoji, style = Type.body)
                                Spacer(Modifier.width(10.dp))
                                Text(p.name, style = Type.body, color = if (p.id == placeId) c.accent else c.label, maxLines = 1, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickPlace = false }) { Text("Close", color = c.secondary) } },
        )
    }

    if (pickDate) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                pickDate = false
            }) { Text("OK", color = c.accent) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel", color = c.secondary) } },
        ) { DatePicker(state = state) }
    }
}
