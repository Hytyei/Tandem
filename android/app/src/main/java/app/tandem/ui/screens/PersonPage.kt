package app.tandem.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * "My page" / "<partner>'s page": full name, birthday, favourite colours, hobbies, what they're watching and playing right now,
 * favourites, and a few playful meters. The partner's page is read-only; your own can be edited.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonPage(vm: AppViewModel, mine: Boolean, onBack: () -> Unit) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val user = (if (mine) data.me else data.couple.partner) ?: return
    val p = user.profile
    val first = user.displayName.substringBefore(' ')
    var edit by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        DreamBackground {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = 32.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.card).pressable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.label)
                    }
                }

                // ---------- header
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(user, 104.dp, ring = if (mine) c.me else c.partner)
                    Spacer(Modifier.height(10.dp))
                    Text(if (p.fullName.isNotBlank()) p.fullName else user.displayName, style = Type.largeTitle, color = c.label, textAlign = TextAlign.Center)
                    Text("@${user.username}", style = Type.callout, color = c.secondary)
                    if (user.bio.isNotBlank()) Text(user.bio, style = Type.callout, color = c.label, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    if (mine) {
                        Spacer(Modifier.height(10.dp))
                        SoftButton(if (p.isEmpty) "Fill in my page" else "Edit my page", onClick = { edit = true })
                    }
                }

                if (p.isEmpty) {
                    SoftCard(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(
                            if (mine) "Tell $first's other half more about you: your birthday, favourite colours, what you're watching right now… and how horny, hungry or chaotic you are today."
                            else "$first hasn't filled in their page yet. Maybe nudge them? 😉",
                            style = Type.callout, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ---------- about
                val bd = parseDate(p.birthday)
                val love = LoveLanguage.from(p.loveLanguage)
                if (p.fullName.isNotBlank() || bd != null || love != null) {
                    SectionHeader("About")
                    SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        if (p.fullName.isNotBlank()) InfoRow("🪪", "Full name", p.fullName)
                        if (bd != null) {
                            val today = LocalDate.now()
                            val age = Period.between(bd, today).years
                            InfoRow("🎂", "Birthday", "${bd.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))} · $age")
                            InfoRow("✨", "Star sign", zodiacOf(bd.monthValue, bd.dayOfMonth))
                            val next = bd.withYear(today.year).let { if (it.isBefore(today)) it.withYear(today.year + 1) else it }
                            val d = daysUntil(next)
                            InfoRow("🎈", "Next birthday", if (d == 0L) "Today! 🎉" else "in $d day${if (d == 1L) "" else "s"} · turning ${age + (if (next.year > today.year || d > 0) 1 else 0)}")
                            // Birthdays are added to the calendar automatically; the button is only a fallback if the partner's one isn't there yet.
                            val inCalendar = data.events.any { it.category == SpecialCategory.Birthday.key && parseDate(it.date)?.let { d -> d.monthValue == bd.monthValue && d.dayOfMonth == bd.dayOfMonth } == true }
                            if (!mine && !inCalendar) {
                                Spacer(Modifier.height(6.dp))
                                SoftButton("Add birthday to our calendar", onClick = { vm.addBirthdayToCalendar(first, p.birthday) })
                            }
                        }
                        if (love != null) InfoRow(love.emoji, "Love language", love.label)
                    }
                }

                // ---------- favourites
                val colors = p.colors.mapNotNull { ProfileColor.from(it) }
                if (colors.isNotEmpty() || p.hobbies.isNotEmpty() || p.sport.isNotBlank() || p.food.isNotBlank() || p.music.isNotBlank()) {
                    SectionHeader("Favourites")
                    SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        if (colors.isNotEmpty()) {
                            Text("Colours", style = Type.footnote, color = c.secondary)
                            Row(Modifier.padding(top = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                colors.forEach { pc ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(Modifier.size(38.dp).clip(CircleShape).background(pc.color).border(1.5.dp, c.separator, CircleShape))
                                        Text(pc.label, style = Type.caption, color = c.secondary, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                            }
                        }
                        if (p.hobbies.isNotEmpty()) {
                            Text("Hobbies", style = Type.footnote, color = c.secondary)
                            FlowRow(Modifier.padding(top = 6.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                p.hobbies.forEach { h ->
                                    Text(h, style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.label,
                                        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 6.dp))
                                }
                            }
                        }
                        if (p.sport.isNotBlank()) InfoRow("🏅", "Sport", p.sport)
                        if (p.food.isNotBlank()) InfoRow("🍜", "Food", p.food)
                        if (p.music.isNotBlank()) InfoRow("🎵", "Music", p.music)
                    }
                }

                // ---------- right now
                if (p.hasNow) {
                    SectionHeader("Right now")
                    SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        NowRow("📺", "Watching", p.watching, p.mediaFor("watching"))
                        NowRow("🎮", "Playing", p.playing, p.mediaFor("playing"))
                        NowRow("🎧", "Listening to", p.listening, p.mediaFor("listening"))
                        NowRow("📖", "Reading", p.reading, p.mediaFor("reading"))
                        if (p.nowAt > 0) Text("updated ${timeAgo(p.nowAt)}", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp))
                    }
                }

                // ---------- meters
                val shown = Meter.entries.filter { it.key in p.meters }
                if (shown.isNotEmpty()) {
                    SectionHeader("Today's meters")
                    SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        shown.forEach { m -> MeterBar(m, p.meters[m.key] ?: 0) }
                        if (p.metersAt > 0) Text("updated ${timeAgo(p.metersAt)}", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }

    if (edit) EditPageSheet(vm, data.me.profile, onDismiss = { edit = false })
}

@Composable
private fun InfoRow(emoji: String, label: String, value: String) {
    val c = T.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 20.sp, modifier = Modifier.width(34.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.caption, color = c.secondary)
            Text(value, style = Type.body, color = c.label)
        }
    }
}

/** One "right now" line. With a looked-up title it becomes a little card: poster or cover, title, what it is, and a short description. */
@Composable
internal fun NowRow(emoji: String, label: String, text: String, info: MediaInfo?) {
    val c = T.colors
    if (text.isBlank()) return
    if (info == null || info.title.isBlank()) { InfoRow(emoji, label, text); return }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        if (info.img.isNotBlank()) AsyncImage(
            model = info.img, contentDescription = info.title, contentScale = ContentScale.Crop,
            modifier = Modifier.width(72.dp).height(104.dp).clip(RoundedCornerShape(12.dp)).background(c.separator),
        ) else Box(Modifier.width(72.dp).height(104.dp).clip(RoundedCornerShape(12.dp)).background(c.accent.copy(alpha = .12f)), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 28.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$emoji  $label", style = Type.caption, color = c.secondary)
            Text(info.title, style = Type.headline, color = c.label, maxLines = 2)
            if (info.sub.isNotBlank()) Text(info.sub, style = Type.footnote, color = c.accent, maxLines = 2)
            if (info.blurb.isNotBlank()) Text(info.blurb, style = Type.footnote, color = c.secondary, maxLines = 4, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun MeterBar(m: Meter, value: Int) {
    val c = T.colors
    val f by animateFloatAsState(value / 100f, tween(900), label = "meter")
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.emoji, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(m.label, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.weight(1f))
            Text("$value%", style = Type.callout.copy(fontWeight = FontWeight.Bold), color = m.tint)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)).background(m.tint.copy(alpha = .16f))) {
            Box(Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(m.tint.copy(alpha = .65f), m.tint))))
        }
        Text(m.caption(value), style = Type.caption, color = c.secondary, modifier = Modifier.padding(top = 3.dp))
    }
}

// ------------------------------------------------------------------ editing your own page

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EditPageSheet(vm: AppViewModel, current: ProfileDetails, onDismiss: () -> Unit) {
    val c = T.colors
    var fullName by remember { mutableStateOf(current.fullName) }
    var birthday by remember { mutableStateOf(parseDate(current.birthday)) }
    var colors by remember { mutableStateOf(current.colors) }
    var hobbies by remember { mutableStateOf(current.hobbies.joinToString(", ")) }
    var sport by remember { mutableStateOf(current.sport) }
    var food by remember { mutableStateOf(current.food) }
    var music by remember { mutableStateOf(current.music) }
    var love by remember { mutableStateOf(current.loveLanguage) }
    var watching by remember { mutableStateOf(current.watching) }
    var playing by remember { mutableStateOf(current.playing) }
    var listening by remember { mutableStateOf(current.listening) }
    var reading by remember { mutableStateOf(current.reading) }
    val media = remember { mutableStateMapOf<String, MediaInfo>().apply { putAll(current.media) } }
    var searchKind by remember { mutableStateOf<String?>(null) }
    var metersOn by remember { mutableStateOf(current.meters.isNotEmpty()) }
    val meters = remember { mutableStateMapOf<String, Int>().apply { putAll(current.meters) } }
    var pickDate by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent,
        focusedContainerColor = c.card, unfocusedContainerColor = c.card,
    )

    @Composable
    fun field(label: String, value: String, max: Int, onChange: (String) -> Unit) {
        OutlinedTextField(
            value, { if (it.length <= max) onChange(it) }, label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = fieldColors,
        )
    }

    /** A "right now" field with a search button that finds the poster / cover and a short description. */
    @Composable
    fun nowField(kind: String, label: String, value: String, onChange: (String) -> Unit) {
        val info = media[kind]?.takeIf { it.title.isNotBlank() }
        OutlinedTextField(
            value, { if (it.length <= 60) { onChange(it); if (info != null && it.trim() != info.title) media.remove(kind) } },
            label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = if (info != null) 4.dp else 8.dp), colors = fieldColors,
            trailingIcon = {
                Icon(Icons.Filled.Search, "Find poster", tint = if (value.trim().length >= 2) c.accent else c.tertiary,
                    modifier = Modifier.size(40.dp).pressable(enabled = value.trim().length >= 2) { searchKind = kind }.padding(9.dp))
            },
        )
        if (info != null) Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(c.card).padding(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (info.img.isNotBlank()) AsyncImage(info.img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 36.dp, height = 52.dp).clip(RoundedCornerShape(8.dp)).background(c.separator))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(info.title, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
                if (info.sub.isNotBlank()) Text(info.sub, style = Type.caption, color = c.secondary, maxLines = 1)
            }
            Text("Remove", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.secondary, modifier = Modifier.pressable { media.remove(kind) }.padding(8.dp))
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("My page", style = Type.title, color = c.label)
            Text("Your partner can see everything here. Leave anything blank to hide it.", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))

            field("Full name", fullName, 60) { fullName = it }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp)).background(c.card).pressable(haptic = false) { pickDate = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Birthday", style = Type.footnote, color = c.secondary)
                    Text(birthday?.format(DateTimeFormatter.ofPattern("d MMMM yyyy")) ?: "Not set", style = Type.body, color = if (birthday != null) c.label else c.tertiary)
                }
                if (birthday != null) Text("Clear  ", style = Type.callout, color = c.secondary, modifier = Modifier.pressable(haptic = false) { birthday = null }.padding(8.dp))
                Text(if (birthday == null) "Set" else "Change", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
            }

            Text("Favourite colours (up to 3)", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 6.dp))
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileColor.entries.forEach { pc ->
                    val on = pc.key in colors
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(pc.color).border(if (on) 3.dp else 1.dp, if (on) c.accent else c.separator, CircleShape)
                            .pressable { colors = if (on) colors - pc.key else if (colors.size < 3) colors + pc.key else colors },
                        contentAlignment = Alignment.Center,
                    ) { if (on) Icon(Icons.Filled.Check, null, tint = if (pc == ProfileColor.White || pc == ProfileColor.Yellow) Color.Black else Color.White, modifier = Modifier.size(20.dp)) }
                }
            }

            OutlinedTextField(
                hobbies, { if (it.length <= 200) hobbies = it }, label = { Text("Hobbies (separate with commas)") }, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), maxLines = 3, colors = fieldColors,
            )
            field("Favourite sport", sport, 40) { sport = it }
            field("Favourite food", food, 40) { food = it }
            field("Favourite music", music, 40) { music = it }

            Text("Love language", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 4.dp))
            FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LoveLanguage.entries.forEach { l ->
                    val on = love == l.key
                    val shape = RoundedCornerShape(50)
                    Row(
                        Modifier.clip(shape).background(if (on) c.accent.copy(alpha = .18f) else c.card).border(1.dp, if (on) c.accent else c.separator, shape)
                            .pressable { love = if (on) "" else l.key }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(l.emoji); Spacer(Modifier.width(6.dp)); Text(l.label, style = Type.footnote, color = c.label) }
                }
            }

            Text("Right now", style = Type.headline, color = c.label, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
            Text("Tap the magnifier to find the poster or cover and a short description. The text you search for goes to Wikipedia, Apple iTunes Search or Open Library, never to the Tandem server.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(bottom = 8.dp))
            nowField("watching", "📺 Watching", watching) { watching = it }
            nowField("playing", "🎮 Playing", playing) { playing = it }
            nowField("listening", "🎧 Listening to", listening) { listening = it }
            nowField("reading", "📖 Reading", reading) { reading = it }

            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Today's meters", style = Type.headline, color = c.label)
                    Text("Playful sliders your partner can see, like the horniness meter 😏", style = Type.footnote, color = c.secondary)
                }
                Switch(metersOn, { metersOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
            }
            if (metersOn) Meter.entries.forEach { m ->
                val v = meters[m.key] ?: 0
                Column(Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${m.emoji}  ${m.label}", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.weight(1f))
                        Text("$v%", style = Type.callout, color = m.tint)
                    }
                    Slider(
                        v.toFloat(), { meters[m.key] = it.toInt() }, valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = m.tint, activeTrackColor = m.tint, inactiveTrackColor = m.tint.copy(alpha = .2f)),
                    )
                    Text(m.caption(v), style = Type.caption, color = c.secondary)
                }
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton("Save", loading = vm.busy, onClick = {
                val list = hobbies.split(',', '\n').map { it.trim().take(24) }.filter { it.isNotEmpty() }.distinct().take(8)
                vm.saveProfileDetails(
                    current.copy(
                        fullName = fullName.trim(), birthday = birthday?.toString() ?: "", colors = colors, hobbies = list,
                        sport = sport.trim(), food = food.trim(), music = music.trim(), loveLanguage = love,
                        watching = watching.trim(), playing = playing.trim(), listening = listening.trim(), reading = reading.trim(),
                        media = media.filter { (k, v) -> v.title.isNotBlank() && when (k) { "watching" -> watching; "playing" -> playing; "listening" -> listening; "reading" -> reading; else -> "" }.isNotBlank() },
                        meters = if (metersOn) Meter.entries.associate { it.key to (meters[it.key] ?: 0) } else emptyMap(),
                    ),
                    onDismiss,
                )
            })
        }
    }

    searchKind?.let { kind ->
        val q = when (kind) { "watching" -> watching; "playing" -> playing; "listening" -> listening; else -> reading }.trim()
        MediaSearchDialog(vm, kind, q, onDismiss = { searchKind = null }) { info ->
            media[kind] = info
            when (kind) { "watching" -> watching = info.title.take(60); "playing" -> playing = info.title.take(60); "listening" -> listening = info.title.take(60); else -> reading = info.title.take(60) }
            searchKind = null
        }
    }

    if (pickDate) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (birthday ?: LocalDate.now().minusYears(25)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1920..LocalDate.now().year,
            selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis < todayUtc },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { birthday = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; pickDate = false }) { Text("OK", color = c.accent) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel", color = c.secondary) } },
        ) { DatePicker(state = state) }
    }
}

/** Shows what the lookup found for [query]; picking one fills the field and keeps its poster and description. */
@Composable
private fun MediaSearchDialog(vm: AppViewModel, kind: String, query: String, onDismiss: () -> Unit, onPick: (MediaInfo) -> Unit) {
    val c = T.colors
    var results by remember { mutableStateOf<List<MediaInfo>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(kind, query) {
        try { results = vm.lookupMedia(kind, query) } catch (e: AppException) { error = e.message ?: "Couldn't search right now" }
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.sheet,
        title = { Text("Results for “$query”") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                when {
                    error != null -> Text(error!!, style = Type.callout, color = c.danger)
                    results == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
                    results!!.isEmpty() -> Text("Nothing found. Try the full title, or keep your own text.", style = Type.callout, color = c.secondary)
                    else -> results!!.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(c.card).pressable(haptic = false) { onPick(r) }.padding(8.dp), verticalAlignment = Alignment.Top) {
                            if (r.img.isNotBlank()) AsyncImage(r.img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 48.dp, height = 70.dp).clip(RoundedCornerShape(8.dp)).background(c.separator))
                            else Box(Modifier.size(width = 48.dp, height = 70.dp).clip(RoundedCornerShape(8.dp)).background(c.accent.copy(alpha = .12f)))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 2)
                                if (r.sub.isNotBlank()) Text(r.sub, style = Type.caption, color = c.accent, maxLines = 2)
                                if (r.blurb.isNotBlank()) Text(r.blurb, style = Type.caption, color = c.secondary, maxLines = 3)
                            }
                        }
                    }
                }
                Text("Source: ${MediaLookup.sourceName(kind)}", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.secondary) } },
    )
}
