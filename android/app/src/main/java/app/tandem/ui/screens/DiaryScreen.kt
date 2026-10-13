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
fun DiaryScreen(vm: AppViewModel) {
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

            // "What's on your mind?" composer card
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.card)
                        .pressable { compose = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(data.me, 38.dp, ring = c.me)
                    Spacer(Modifier.width(12.dp))
                    Text("What's on your mind?", style = Type.callout, color = c.secondary, modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.AddPhotoAlternate, null, tint = c.accent)
                }
            }

            if (data.diary.isEmpty()) item {
                EmptyState("Nothing here yet", "Write the first note — a memory, a thought, a thank you. You can add photos and pick any date.")
            }
            items(data.diary, key = { it.id }) { e ->
                val mine = e.userId == data.me.id
                PostCard(
                    e, if (mine) data.me else data.couple.partner, mine,
                    onEdit = { editing = e },
                    onDelete = { deleting = e },
                    onOpenImage = { i -> viewer = e.images to i },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 112.dp).size(60.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.accent, Color(0xFFFF6A5E)))).pressable { compose = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = "New note", tint = Color.White, modifier = Modifier.size(30.dp)) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostCard(
    e: DiaryEntry, author: User?, mine: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onOpenImage: (Int) -> Unit, modifier: Modifier = Modifier,
) {
    val c = T.colors
    val repo = (LocalContext.current.applicationContext as TandemApp).repo
    val shape = RoundedCornerShape(26.dp)
    val day = remember(e) { e.day() }
    val written = remember(e.createdAt) { Instant.ofEpochMilli(e.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
    val dayText = remember(day) {
        val today = LocalDate.now()
        when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEEE, d MMMM" else "EEEE, d MMMM yyyy"))
        }
    }
    var menu by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxWidth().clip(shape).background(c.card)
            .border(1.dp, if (c.isDark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .7f), shape).padding(bottom = 14.dp),
    ) {
        // header
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(author, 42.dp, ring = if (mine) c.me else c.partner)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(author?.displayName ?: "", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, null, tint = c.secondary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(dayText, style = Type.caption, color = c.secondary)
                    if (day != written) Text("  ·  written ${timeAgo(e.createdAt)}", style = Type.caption, color = c.tertiary)
                    if (e.updatedAt > 0) Text("  ·  edited", style = Type.caption, color = c.tertiary)
                }
            }
            if (mine) Box {
                Box(Modifier.size(40.dp).pressable(haptic = false) { menu = true }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MoreHoriz, "More", tint = c.secondary)
                }
                DropdownMenu(menu, { menu = false }, containerColor = c.sheet) {
                    DropdownMenuItem(
                        text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null, tint = c.secondary) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(text = { Text("Delete", color = c.danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }

        // pictures
        if (e.images.isNotEmpty()) {
            val pager = rememberPagerState { e.images.size }
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
                HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 4.dp) { page ->
                    AsyncImage(
                        model = repo.diaryImageUrl(e.images[page]), contentDescription = "Photo ${page + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(c.separator).pressable(haptic = false) { onOpenImage(page) },
                    )
                }
                if (e.images.size > 1) {
                    Text(
                        "${pager.currentPage + 1}/${e.images.size}", style = Type.caption, color = Color.White,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0x99000000)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(e.images.size) { i ->
                            Box(Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else .55f)))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // rating
        if (e.rating != null) {
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeartRating(e.rating, size = 18.dp)
                Spacer(Modifier.width(8.dp))
                Text(ratingLabel(e.rating), style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
            }
        }

        // text + chips (mood, what you did)
        Text(e.text, style = Type.body, color = c.label, modifier = Modifier.padding(horizontal = 16.dp))
        val mood = Mood.from(e.mood)
        val type = DateType.from(e.dateType)
        if (mood != null || type != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                type?.let { t ->
                    Row(Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.emoji, style = Type.caption)
                        Spacer(Modifier.width(6.dp))
                        Text(t.label, style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                    }
                }
                mood?.let { m ->
                    Row(Modifier.clip(RoundedCornerShape(50)).background(m.tint.copy(alpha = .16f)).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Art(m.art, 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Feeling ${m.label.lowercase()}", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                    }
                }
            }
        }
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
            Spacer(Modifier.height(18.dp))
            val label = when {
                editing -> "Save changes"
                photos.isEmpty() -> "Post"
                else -> "Post with ${photos.size} photo${if (photos.size == 1) "" else "s"}"
            }
            PrimaryButton(
                label,
                onClick = {
                    if (existing != null) vm.updateDiary(existing.id, text, mood, date, rating, dateType, existing.images - keep.toSet(), photos, onDismiss)
                    else vm.addDiary(text, mood, date, rating, dateType, photos, onDismiss)
                },
                enabled = text.isNotBlank(), loading = vm.busy,
            )
        }
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
