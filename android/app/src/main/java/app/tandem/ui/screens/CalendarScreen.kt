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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.R
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

    val ym = YearMonth.parse(month)
    val selDate = LocalDate.parse(selected)
    val upcomingList = remember(data.events) { upcoming(data.events) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 150.dp)) {
            item { LargeTitle("Calendar", subtitle = "Our moments") }

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
            if (upcomingList.isEmpty()) item { EmptyState(R.drawable.hero_together, "No events yet", "Add anniversaries, trips, dates — we'll count down together.") }
            items(upcomingList.take(12), key = { "u-${it.first.id}" }) { (e, d) ->
                EventRow(e, d, Modifier.padding(horizontal = 16.dp, vertical = 4.dp).staggerIn(upcomingList.indexOfFirst { it.first.id == e.id }), showCountdown = true) { editing = e }
            }
        }

        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 112.dp).size(60.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.accent, Color(0xFFFF6A5E)))).pressable { creating = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = "Add event", tint = Color.White, modifier = Modifier.size(30.dp)) }
    }

    if (creating) EventSheet(vm, null, selDate, onDismiss = { creating = false })
    editing?.let { EventSheet(vm, it, selDate, onDismiss = { editing = null }) }
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
                            val has = events.any { it.occursOn(date) }
                            val bg by animateColorAsState(if (isSel) c.accent else Color.Transparent, tween(180), label = "sel")
                            val scale by animateFloatAsState(if (isSel) 1f else 0.92f, spring(0.5f, Spring.StiffnessMedium), label = "selScale")
                            Column(
                                Modifier.size(42.dp).graphicsScale(scale).clip(CircleShape).background(bg)
                                    .let { if (isToday && !isSel) it.border(1.5.dp, c.accent, CircleShape) else it }
                                    .pressable(haptic = false) { onPick(date) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$dayNum", style = Type.callout.copy(fontWeight = if (isToday || isSel) FontWeight.Bold else FontWeight.Normal), color = if (isSel) Color.White else c.label)
                                Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(if (has) (if (isSel) Color.White else c.accent) else Color.Transparent))
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
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).pressable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) { Text(e.emoji, fontSize = 24.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = Type.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = buildString {
                append(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")))
                e.time?.let { append(" · $it") }
                if (e.repeatYearly) append(" · every year")
            }
            Text(sub, style = Type.footnote, color = c.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showCountdown) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(c.accentSoft).padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (days > 1) {
                    Text("$days", style = Type.title, color = c.accent)
                    Text("days", style = Type.caption.copy(fontSize = 10.sp), color = c.accent)
                } else Text(countdownLabel(days), style = Type.callout.copy(fontWeight = FontWeight.Bold), color = c.accent)
            }
        }
    }
}

private val EmojiChoices = listOf("❤️", "🎂", "💍", "✈️", "🍽️", "🎬", "🏖️", "🎁", "🥂", "🏠", "🎶", "⭐")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventSheet(vm: AppViewModel, existing: CalendarEvent?, initialDate: LocalDate, onDismiss: () -> Unit) {
    val c = T.colors
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var emoji by remember { mutableStateOf(existing?.emoji ?: "❤️") }
    var date by remember { mutableStateOf(parseDate(existing?.date) ?: initialDate) }
    var time by remember { mutableStateOf(parseTime(existing?.time)) }
    var yearly by remember { mutableStateOf(existing?.repeatYearly ?: false) }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(if (existing == null) "New event" else "Edit event", style = Type.title, color = c.label)
            Spacer(Modifier.height(14.dp))
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
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                if (existing == null) "Add event" else "Save", enabled = title.isNotBlank(), loading = vm.busy,
                onClick = {
                    vm.saveEvent(existing?.id, EventBody(title.trim(), emoji, date.toString(), time?.format(DateTimeFormatter.ofPattern("HH:mm")), yearly, notes.trim()), onDismiss)
                },
            )
            if (existing != null) {
                Spacer(Modifier.height(10.dp))
                Text("Delete event", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.danger, textAlign = TextAlign.Center,
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
