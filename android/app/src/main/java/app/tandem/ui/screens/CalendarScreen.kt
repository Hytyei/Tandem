package app.tandem.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.R
import app.tandem.TandemApp
import coil.compose.AsyncImage
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

@Composable
fun CalendarScreen(vm: AppViewModel) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var direction by remember { mutableIntStateOf(1) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableIntStateOf(0) }            // 0 = calendar, 1 = special days, 2 = memories
    var preset by rememberSaveable { mutableStateOf<String?>(null) } // special-day category chosen from the chips

    val ym = YearMonth.parse(month)
    val selDate = LocalDate.parse(selected)
    val upcomingList = remember(data.events) { upcoming(data.events.filter { !it.isSpecial }) }
    val specialList = remember(data.events) { upcomingSpecial(data.events) }
    val memories = vm.memories
    // "Remember when" is loaded from the server (it can reach back further than the synced feed) and refreshed when notes/events change.
    LaunchedEffect(data.diary.size, data.events.size, data.diary.firstOrNull()?.updatedAt) { vm.loadMemories() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
            item {
                when (mode) {
                    0 -> LargeTitle("Calendar", subtitle = "Our moments")
                    1 -> LargeTitle("Special days", subtitle = "Dates worth celebrating")
                    else -> LargeTitle("Memories", subtitle = "Remember when")
                }
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    Segmented(listOf("Calendar", "Special days", "Memories"), mode, { mode = it })
                }
            }

            if (mode == 2) {
                memoriesSection(memories, c, data, onAddNote = { mode = 0 })
            } else if (mode == 1) {
                specialDaysSection(
                    specialList, c, data,
                    onAdd = { cat -> preset = cat; creating = true },
                    onOpen = { editing = it },
                )
            } else {

            // ---------- "On this day" teaser (opens the Memories view)
            memories?.onThisDay?.firstOrNull()?.let { m ->
                item {
                    val title = m.diary?.text ?: m.event?.title ?: ""
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(c.accent.copy(alpha = .12f)).pressable { mode = 2 }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(m.diary?.let { DateType.from(it.dateType)?.emoji } ?: m.event?.emoji ?: "💭", fontSize = 26.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("On this day · ${yearsAgoText(m.yearsAgo)}", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                            Text(title, style = Type.callout, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text("›", style = Type.title, color = c.accent)
                    }
                }
            }

            // ---------- Month card
            item {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedContent(ym, label = "month", transitionSpec = {
                            (slideInHorizontally { w -> direction * w / 3 } + fadeIn(tween(180))) togetherWith (slideOutHorizontally { w -> -direction * w / 3 } + fadeOut(tween(120)))
                        }, modifier = Modifier.weight(1f)) { m ->
                            Text("${m.month.getDisplayName(JTextStyle.FULL, Locale.getDefault())} ${m.year}", style = Type.title, color = c.label, modifier = Modifier.padding(start = 6.dp))
                        }
                        IconBtn(true) { direction = -1; month = ym.minusMonths(1).toString() }
                        IconBtn(false) { direction = 1; month = ym.plusMonths(1).toString() }
                    }
                    Spacer(Modifier.height(8.dp))
                    val first = DayOfWeek.MONDAY
                    Row(Modifier.fillMaxWidth()) {
                        for (i in 0 until 7) {
                            Text(first.plus(i.toLong()).getDisplayName(JTextStyle.NARROW, Locale.getDefault()), style = Type.caption, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    AnimatedContent(ym, label = "grid", transitionSpec = {
                        (slideInHorizontally { w -> direction * w / 4 } + fadeIn(tween(200))) togetherWith (slideOutHorizontally { w -> -direction * w / 4 } + fadeOut(tween(120)))
                    }) { m ->
                        MonthGrid(m, selDate, data.events) { selected = it.toString() }
                    }
                }
            }

            // ---------- Selected day
            item {
                SectionHeader(if (selDate == LocalDate.now()) "Today" else selDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")))
            }
            val dayEvents = data.events.filter { it.occursOn(selDate) }
            if (dayEvents.isEmpty()) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).pressable { creating = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Nothing planned — tap to add something", style = Type.callout, color = c.secondary, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Add, null, tint = c.accent)
                    }
                }
            } else items(dayEvents, key = { "d-${it.id}" }) { e -> EventRow(e, selDate, Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { editing = e } }

            // ---------- Upcoming with countdowns
            item { SectionHeader("Upcoming") }
            if (upcomingList.isEmpty()) item { EmptyState("No events yet", "Add anniversaries, trips, dates — we'll count down together.") }
            items(upcomingList.take(12), key = { "u-${it.first.id}" }) { (e, d) ->
                EventRow(e, d, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).staggerIn(upcomingList.indexOfFirst { it.first.id == e.id }), showCountdown = true) { editing = e }
            }
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 112.dp).size(60.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.accent, Color(0xFFFF6A5E)))).pressable { preset = if (mode == 1) "custom" else null; creating = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = "Add event", tint = Color.White, modifier = Modifier.size(30.dp)) }
    }

    if (creating) EventSheet(vm, null, selDate, special = preset, onDismiss = { creating = false; preset = null })
    editing?.let { EventSheet(vm, it, selDate, special = if (it.isSpecial) (it.category ?: "custom") else null, onDismiss = { editing = null }) }
}

@Composable
private fun IconBtn(left: Boolean, onClick: () -> Unit) {
    val c = T.colors
    Box(Modifier.size(40.dp).pressable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(if (left) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.accent)
    }
}

@Composable
private fun MonthGrid(ym: YearMonth, selected: LocalDate, events: List<CalendarEvent>, onPick: (LocalDate) -> Unit) {
    val c = T.colors
    val today = LocalDate.now()
    val offset = ym.atDay(1).dayOfWeek.value - 1 // Monday-first
    val cells = offset + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = r * 7 + col - offset + 1
                    Box(Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..ym.lengthOfMonth()) {
                            val date = ym.atDay(dayNum)
                            val isSel = date == selected
                            val isToday = date == today
                            val onDay = events.filter { it.occursOn(date) }
                            val hasEvent = onDay.any { !it.isSpecial }
                            val hasSpecial = onDay.any { it.isSpecial }
                            val bg by animateColorAsState(if (isSel) c.accent else Color.Transparent, tween(180), label = "sel")
                            val scale by animateFloatAsState(if (isSel) 1f else 0.92f, spring(0.5f, Spring.StiffnessMedium), label = "selScale")
                            Column(
                                Modifier.size(42.dp).graphicsScale(scale).clip(CircleShape).background(bg)
                                    .let { if (isToday && !isSel) it.border(1.5.dp, c.accent, CircleShape) else it }
                                    .pressable(haptic = false) { onPick(date) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$dayNum", style = Type.callout.copy(fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal), color = if (isSel) Color.White else c.label)
                                Row(Modifier.padding(top = 2.dp).height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (hasEvent) Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSel) Color.White else c.accent))
                                    if (hasSpecial) Box(Modifier.size(5.dp).clip(CircleShape).background(if (isSel) Color(0xFFFFE08A) else c.special))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.graphicsScale(s: Float): Modifier = this.then(Modifier.graphicsLayer { scaleX = s; scaleY = s })

@Composable
private fun EventRow(e: CalendarEvent, date: LocalDate, modifier: Modifier = Modifier, showCountdown: Boolean = false, onClick: () -> Unit) {
    val c = T.colors
    val days = daysUntil(date)
    val tone = if (e.isSpecial) c.special else c.accent
    val cat = if (e.isSpecial) SpecialCategory.from(e.category) else null
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card)
        .let { if (e.isSpecial) it.border(1.dp, c.special.copy(alpha = .45f), RoundedCornerShape(22.dp)) else it }
        .pressable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(tone.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text(e.emoji, fontSize = 24.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = Type.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                append(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")))
                e.time?.let { append(" · $it") }
                cat?.let { append(" · ${it.label}") }
                DateType.from(e.dateType)?.let { append(" · ${it.emoji} ${it.label}") }
                if (e.repeatYearly) append(" · every year")
            }
            Text(sub, style = Type.footnote, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showCountdown) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(tone.copy(alpha = .16f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (days > 1) {
                    Text("$days", style = Type.title, color = tone)
                    Text("days", style = Type.caption.copy(fontSize = 10.sp), color = tone)
                } else Text(countdownLabel(days), style = Type.callout.copy(fontWeight = FontWeight.Bold), color = tone)
            }
        }
    }
}

private val EmojiChoices = listOf("❤️", "🎂", "💍", "✈️", "🍽️", "🎬", "🏖️", "🎁", "🥂", "🏠", "🎶", "⭐")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EventSheet(vm: AppViewModel, existing: CalendarEvent?, initialDate: LocalDate, special: String?, onDismiss: () -> Unit) {
    val c = T.colors
    val isSpecial = special != null
    var category by remember { mutableStateOf(SpecialCategory.from(special)) }
    fun defaultDate(cat: SpecialCategory?): LocalDate? = if (cat == SpecialCategory.Valentine) {
        LocalDate.now().let { t -> LocalDate.of(t.year, 2, 14).let { d -> if (d.isBefore(t)) d.plusYears(1) else d } }
    } else null
    var title by remember { mutableStateOf(existing?.title ?: category?.takeIf { it != SpecialCategory.Custom }?.label ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: category?.emoji ?: "❤️") }
    var date by remember { mutableStateOf(parseDate(existing?.date) ?: (if (existing == null) defaultDate(category) else null) ?: initialDate) }
    var time by remember { mutableStateOf(parseTime(existing?.time)) }
    var yearly by remember { mutableStateOf(existing?.repeatYearly ?: isSpecial) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var dateType by remember { mutableStateOf(DateType.from(existing?.dateType)) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(if (existing == null) (if (isSpecial) "New special day" else "New event") else (if (isSpecial) "Edit special day" else "Edit event"), style = Type.title, color = c.label)
            Spacer(Modifier.height(14.dp))
            if (isSpecial) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpecialCategory.entries.forEach { cat ->
                        val on = category == cat
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(if (on) c.special.copy(alpha = .22f) else c.card)
                                .border(if (on) 1.5.dp else 0.dp, if (on) c.special else Color.Transparent, RoundedCornerShape(50))
                                .pressable {
                                    val previousAuto = category?.takeIf { it != SpecialCategory.Custom }?.label
                                    category = cat; emoji = cat.emoji
                                    if (title.isBlank() || title == previousAuto) title = if (cat == SpecialCategory.Custom) "" else cat.label
                                    if (existing == null) defaultDate(cat)?.let { date = it }
                                }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Text(cat.emoji, fontSize = 16.sp); Spacer(Modifier.width(6.dp)); Text(cat.label, style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.label) }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EmojiChoices.take(6).forEach { EmojiChip(it, it == emoji) { emoji = it } }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EmojiChoices.drop(6).forEach { EmojiChip(it, it == emoji) { emoji = it } }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                title, { if (it.length <= 80) title = it }, placeholder = { Text("Title", color = c.tertiary) }, singleLine = true,
                shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card),
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card)) {
                SheetRow("Date", date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))) { pickDate = true }
                HorizontalDivider(color = c.separator, modifier = Modifier.padding(start = 16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("All day", style = Type.body, color = c.label, modifier = Modifier.weight(1f))
                    Switch(checked = time == null, onCheckedChange = { time = if (it) null else LocalTime.of(19, 0) }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
                }
                AnimatedVisibility(time != null) {
                    Column {
                        HorizontalDivider(color = c.separator, modifier = Modifier.padding(start = 16.dp))
                        SheetRow("Time", time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "") { pickTime = true }
                    }
                }
                HorizontalDivider(color = c.separator, modifier = Modifier.padding(start = 16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeat every year", style = Type.body, color = c.label, modifier = Modifier.weight(1f))
                    Switch(checked = yearly, onCheckedChange = { yearly = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                notes, { if (it.length <= 500) notes = it }, placeholder = { Text("Notes (optional)", color = c.tertiary) }, minLines = 2, maxLines = 4,
                shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card),
            )
            Spacer(Modifier.height(14.dp))
            Text("What kind of date is it? (optional)", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(6.dp))
            DateTypeChips(dateType, { dateType = it })
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                if (existing == null) (if (isSpecial) "Add special day" else "Add event") else "Save", enabled = title.isNotBlank(), loading = vm.busy,
                onClick = {
                    vm.saveEvent(existing?.id, EventBody(
                        title.trim(), emoji, date.toString(), time?.format(DateTimeFormatter.ofPattern("HH:mm")), yearly, notes.trim(),
                        kind = if (isSpecial) "special" else "event", category = if (isSpecial) (category ?: SpecialCategory.Custom).key else null,
                        dateType = dateType?.key,
                    ), onDismiss)
                },
            )
            if (existing != null) {
                Spacer(Modifier.height(10.dp))
                Text(if (isSpecial) "Delete special day" else "Delete event", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.danger, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().pressable { vm.deleteEvent(existing.id, onDismiss) }.padding(12.dp))
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                pickDate = false
            }) { Text("OK", color = c.accent) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel", color = c.secondary) } },
        ) { DatePicker(state = state) }
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = time?.hour ?: 19, initialMinute = time?.minute ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false }, containerColor = c.sheet,
            confirmButton = { TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); pickTime = false }) { Text("OK", color = c.accent) } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel", color = c.secondary) } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun RowScope.EmojiChip(emoji: String, on: Boolean, onClick: () -> Unit) {
    val c = T.colors
    Box(
        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.accentSoft else c.card)
            .border(if (on) 2.dp else 0.dp, if (on) c.accent else Color.Transparent, RoundedCornerShape(14.dp)).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 22.sp) }
}

@Composable
private fun SheetRow(label: String, value: String, onClick: () -> Unit) {
    val c = T.colors
    Row(Modifier.fillMaxWidth().pressable(haptic = false, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.body, color = c.label, modifier = Modifier.weight(1f))
        Text(value, style = Type.body, color = c.accent)
    }
}

// ---------------------------------------------------------------- special days tab
private fun androidx.compose.foundation.lazy.LazyListScope.specialDaysSection(
    list: List<Pair<CalendarEvent, LocalDate>>, c: app.tandem.ui.theme.TandemColors, data: SyncResponse,
    onAdd: (String) -> Unit, onOpen: (CalendarEvent) -> Unit,
) {
    val next = list.firstOrNull()
    item {
        if (next != null) {
            val (e, d) = next
            val days = daysUntil(d)
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(c.special.copy(alpha = .30f), c.accent.copy(alpha = .18f))))
                    .border(1.dp, c.special.copy(alpha = .5f), RoundedCornerShape(28.dp))
                    .pressable { onOpen(e) }.padding(20.dp),
            ) {
                Text("NEXT SPECIAL DAY", style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.emoji, fontSize = 40.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = Type.title, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = Type.footnote, color = c.secondary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (days > 1) {
                            Text("$days", style = Type.numberHuge.copy(fontSize = 44.sp, lineHeight = 46.sp), color = c.special)
                            Text("days", style = Type.caption, color = c.secondary)
                        } else Text(countdownLabel(days), style = Type.title, color = c.special)
                    }
                }
            }
        }
    }
    item { SectionHeader("Add a special day") }
    item {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpecialCategory.entries.forEach { cat ->
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(c.card).border(1.dp, c.special.copy(alpha = .35f), RoundedCornerShape(50))
                        .pressable { onAdd(cat.key) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text(cat.emoji, fontSize = 16.sp); Spacer(Modifier.width(6.dp)); Text(cat.label, style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.label) }
            }
        }
    }
    item { SectionHeader("Coming up") }
    if (list.isEmpty()) item {
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("✨", fontSize = 44.sp)
            Spacer(Modifier.height(8.dp))
            Text("No special days yet", style = Type.headline, color = c.label)
            Spacer(Modifier.height(4.dp))
            Text("Birthdays, Valentine's Day, your first date… pick one above and we'll count down and remind you.", style = Type.callout, color = c.secondary, textAlign = TextAlign.Center)
        }
    }
    items(list, key = { "s-${it.first.id}" }) { (e, d) ->
        EventRow(e, d, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), showCountdown = true) { onOpen(e) }
    }
}


// ---------------------------------------------------------------- memories ("remember when")
private fun yearsAgoText(n: Int) = when { n <= 0 -> "earlier"; n == 1 -> "1 year ago"; else -> "$n years ago" }

private fun androidx.compose.foundation.lazy.LazyListScope.memoriesSection(
    m: Memories?, c: app.tandem.ui.theme.TandemColors, data: SyncResponse, onAddNote: () -> Unit,
) {
    if (m == null) {
        item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }
        return
    }
    if (m.isEmpty) {
        item {
            EmptyState("No memories yet", "As you add diary notes and events, this page brings them back: this day in earlier years, last month's highlights and your best-rated moments. Rate notes with hearts to build your best-of list.")
        }
        return
    }
    fun section(title: String, subtitle: String?, key: String, list: List<Memory>, showYears: Boolean) {
        if (list.isEmpty()) return
        item(key = "h-$key") {
            Column {
                SectionHeader(title)
                if (subtitle != null) Text(subtitle, style = Type.footnote, color = c.secondary, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 4.dp))
            }
        }
        items(list.size, key = { "m-$key-$it" }) { i ->
            MemoryCard(list[i], data, showYears, Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
        }
    }
    section("On this day", "Moments from this date in earlier years", "day", m.onThisDay, true)
    section("Best of this month", "Your highest-rated notes so far", "best-month", m.bestOfMonth, false)
    section("Last month", "Highlights from the month before", "last", m.lastMonth, false)
    section("Best memories ever", "Everything you rated 4 hearts or more", "ever", m.bestEver, true)
}

@Composable
private fun MemoryCard(m: Memory, data: SyncResponse, showYears: Boolean, modifier: Modifier = Modifier) {
    val c = T.colors
    val repo = (LocalContext.current.applicationContext as TandemApp).repo
    val shape = RoundedCornerShape(24.dp)
    val date = remember(m.date) { parseDate(m.date) }
    val dateText = date?.format(DateTimeFormatter.ofPattern(if (showYears && m.yearsAgo > 0) "d MMMM yyyy" else "EEE, d MMMM")) ?: m.date
    val d = m.diary
    val ev = m.event
    Column(
        modifier.fillMaxWidth().clip(shape).background(c.card)
            .border(1.dp, if (c.isDark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .7f), shape),
    ) {
        if (d != null && d.images.isNotEmpty()) {
            AsyncImage(
                model = repo.diaryImageUrl(d.images.first()), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(c.separator),
            )
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateText, style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.secondary, modifier = Modifier.weight(1f))
                if (showYears && m.yearsAgo > 0) Text(yearsAgoText(m.yearsAgo), style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
            }
            Spacer(Modifier.height(6.dp))
            if (d != null) {
                val who = if (d.userId == data.me.id) "You" else data.couple.partner?.displayName?.substringBefore(' ') ?: "Partner"
                if (d.rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeartRating(d.rating, size = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(ratingLabel(d.rating), style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(d.text, style = Type.body, color = c.label, maxLines = 5, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                val type = DateType.from(d.dateType)
                Text(
                    buildString { append("Diary · $who"); type?.let { append(" · ${it.emoji} ${it.label}") } },
                    style = Type.footnote, color = c.secondary,
                )
            } else if (ev != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ev.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(ev.title, style = Type.headline, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = buildString {
                            append(if (ev.isSpecial) "Special day" else "Event")
                            DateType.from(ev.dateType)?.let { append(" · ${it.emoji} ${it.label}") }
                        }
                        Text(sub, style = Type.footnote, color = c.secondary)
                    }
                }
                if (ev.notes.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(ev.notes, style = Type.callout, color = c.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
