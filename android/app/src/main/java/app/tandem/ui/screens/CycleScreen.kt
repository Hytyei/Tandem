package app.tandem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val PERIOD_RED = Color(0xFFF2476F)
private val FERTILE_TEAL = Color(0xFF14B8A6)

/** Holds the Cycle tab's little bits of UI state, so the sheets can live outside the list. */
class CycleUi {
    var month by mutableStateOf(YearMonth.now())
    var dayDialog by mutableStateOf<LocalDate?>(null)
    var periodSheet by mutableStateOf<PeriodEntry?>(null)
    var addingPeriod by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var showPartner by mutableStateOf(false)
}

@Composable
fun rememberCycleUi() = remember { CycleUi() }

private fun d(s: String?): LocalDate? = s?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
private val shortDay = DateTimeFormatter.ofPattern("EEE, d MMM")

/** The "Cycle" part of the Calendar tab: your period tracker, and your partner's too (both of you can see both). */
fun LazyListScope.cycleSection(vm: AppViewModel, data: SyncResponse, ui: CycleUi) {
    val resp = vm.period
    if (resp == null) {
        item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = T.colors.accent) } }
        return
    }
    val partner = data.couple.partner
    val partnerData = resp.partner
    val viewing = ui.showPartner && partnerData != null
    val shown = if (viewing) partnerData!! else resp.mine
    val today = LocalDate.now()
    val pred = CycleMath.predict(shown, today)
    val ownerName = if (viewing) (partner?.displayName?.substringBefore(' ') ?: "Partner") else "You"

    if (partnerData != null) item {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Segmented(listOf("Me", partner?.displayName?.substringBefore(' ') ?: "Partner"), if (viewing) 1 else 0, { ui.showPartner = it == 1 })
        }
    }

    // ---------- today
    item { StatusCard(vm, shown, pred, today, ownerName, viewing, ui) }

    // ---------- month
    item { CycleMonthCard(shown, pred, today, ui, viewing) }
    item { Legend() }

    // ---------- history
    val periods = shown.periods
    if (periods.isNotEmpty()) {
        item { SectionHeader("Past periods") }
        val sorted = periods.sortedByDescending { it.startDate }
        items(sorted.size.coerceAtMost(8), key = { "p-" + sorted[it].id }) { i ->
            val p = sorted[i]
            val c = T.colors
            val s = d(p.startDate); val e = d(p.endDate)
            val prev = sorted.getOrNull(i + 1)?.let { d(it.startDate) }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card)
                    .let { if (!viewing) it.pressable { ui.periodSheet = p } else it }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(PERIOD_RED.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text("🩸", fontSize = 18.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: p.startDate, style = Type.headline, color = c.label)
                    val len = if (s != null && e != null) "${ChronoUnit.DAYS.between(s, e) + 1} days" else "ongoing"
                    val gap = if (s != null && prev != null) " · ${ChronoUnit.DAYS.between(prev, s)}-day cycle" else ""
                    Text(len + gap, style = Type.caption, color = c.secondary)
                }
                if (!viewing) Text("Edit", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
            }
        }
    }

    // ---------- actions
    if (!viewing) item {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SoftButton("Add a past period", onClick = { ui.addingPeriod = true }, modifier = Modifier.fillMaxWidth())
            SoftButton("Cycle settings", onClick = { ui.settings = true }, modifier = Modifier.fillMaxWidth())
        }
    } else if (partnerData != null) item {
        Text("This is $ownerName's cycle. Only they can change it.", style = Type.footnote, color = T.colors.secondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), textAlign = TextAlign.Center)
    }
    if (!viewing && partner != null) item {
        Text("${partner.displayName.substringBefore(' ')} can see your cycle too. Use the switch at the top to see theirs.", style = Type.footnote, color = T.colors.secondary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 2.dp).fillMaxWidth(), textAlign = TextAlign.Center)
    }
    item {
        Text(
            "Predictions are estimates from the dates you log. This is not a medical tool and not a way to prevent pregnancy.",
            style = Type.caption, color = T.colors.tertiary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp).fillMaxWidth(),
        )
    }
}

@Composable
private fun StatusCard(vm: AppViewModel, data: PeriodData, pred: CyclePrediction, today: LocalDate, owner: String, viewing: Boolean, ui: CycleUi) {
    val c = T.colors
    val ongoing = pred.ongoing
    val last = pred.lastStart
    val next = pred.nextStart
    val daysToNext = next?.let { ChronoUnit.DAYS.between(today, it).toInt() }
    val kindToday = CycleMath.kindOf(today, data, pred, today)

    val headline: String
    val sub: String
    when {
        ongoing != null -> {
            val day = ChronoUnit.DAYS.between(d(ongoing.startDate) ?: today, today).toInt() + 1
            headline = "Period · day $day"
            sub = if (viewing) "$owner is on their period" else "Tap \"Period ended\" when it's over"
        }
        last == null -> { headline = "No periods logged yet"; sub = if (viewing) "$owner hasn't logged anything yet" else "Log the day your next one starts and Tandem learns your cycle" }
        daysToNext != null && daysToNext > 0 -> {
            headline = "Next period in $daysToNext ${if (daysToNext == 1) "day" else "days"}"
            sub = "Around ${next!!.format(shortDay)} · cycle day ${ChronoUnit.DAYS.between(last, today) + 1}"
        }
        daysToNext == 0 -> { headline = "Expected today"; sub = "Cycle day ${ChronoUnit.DAYS.between(last, today) + 1}" }
        else -> {
            val late = -(daysToNext ?: 0)
            headline = "$late ${if (late == 1) "day" else "days"} later than expected"
            sub = "Cycles vary. Log the start when it comes."
        }
    }
    val chip = when (kindToday) {
        CycleDayKind.Ovulation -> "Ovulation likely today"
        CycleDayKind.Fertile -> "Fertile window"
        else -> null
    }
    SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(PERIOD_RED.copy(alpha = .16f)), contentAlignment = Alignment.Center) { Text("🩸", fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(headline, style = Type.headline, color = c.label)
                Text(sub, style = Type.footnote, color = c.secondary)
            }
        }
        if (chip != null) Text(
            chip, style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = FERTILE_TEAL,
            modifier = Modifier.padding(top = 10.dp).clip(RoundedCornerShape(50)).background(FERTILE_TEAL.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        if (pred.learned) Text("Average cycle ${pred.avgCycle} days · period ${pred.avgPeriod} days", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 8.dp))
        if (!viewing) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ongoing != null) {
                    Box(Modifier.weight(1f)) {
                        PrimaryButton("Period ended", onClick = { vm.savePeriod(ongoing.id, PeriodBody(ongoing.startDate, today.toString(), ongoing.note)) }, loading = vm.busy)
                    }
                } else {
                    Box(Modifier.weight(1f)) {
                        PrimaryButton("Period started today", onClick = { vm.savePeriod(null, PeriodBody(today.toString())) }, loading = vm.busy)
                    }
                }
                Box(Modifier.weight(1f)) { SoftButton("Log today", onClick = { ui.dayDialog = today }, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun CycleMonthCard(data: PeriodData, pred: CyclePrediction, today: LocalDate, ui: CycleUi, viewing: Boolean) {
    val c = T.colors
    val ym = ui.month
    val offset = ym.atDay(1).dayOfWeek.value - 1
    val rows = (offset + ym.lengthOfMonth() + 6) / 7
    val logged = remember(data.days) { data.days.associateBy { it.date } }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.card).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${ym.month.getDisplayName(JTextStyle.FULL, Locale.getDefault())} ${ym.year}", style = Type.title, color = c.label, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Box(Modifier.size(40.dp).pressable { ui.month = ym.minusMonths(1) }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = c.accent) }
            Box(Modifier.size(40.dp).pressable { ui.month = ym.plusMonths(1) }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = c.accent) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) Text(java.time.DayOfWeek.MONDAY.plus(i.toLong()).getDisplayName(JTextStyle.NARROW, Locale.getDefault()), style = Type.caption, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (r in 0 until rows) Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val n = r * 7 + col - offset + 1
                    Box(Modifier.weight(1f).height(46.dp), contentAlignment = Alignment.Center) {
                        if (n in 1..ym.lengthOfMonth()) {
                            val date = ym.atDay(n)
                            val kind = CycleMath.kindOf(date, data, pred, today)
                            val isToday = date == today
                            val log = logged[date.toString()]
                            val bg = when (kind) {
                                CycleDayKind.Period -> PERIOD_RED
                                CycleDayKind.PredictedPeriod -> PERIOD_RED.copy(alpha = .14f)
                                CycleDayKind.Fertile -> FERTILE_TEAL.copy(alpha = .20f)
                                CycleDayKind.Ovulation -> FERTILE_TEAL
                                CycleDayKind.None -> Color.Transparent
                            }
                            val fg = if (kind == CycleDayKind.Period || kind == CycleDayKind.Ovulation) Color.White else c.label
                            Column(
                                Modifier.size(42.dp).clip(CircleShape).background(bg)
                                    .let { m -> if (kind == CycleDayKind.PredictedPeriod) m.border(1.5.dp, PERIOD_RED.copy(alpha = .6f), CircleShape) else m }
                                    .let { m -> if (isToday) m.border(2.dp, c.label, CircleShape) else m }
                                    .let { m -> if (!viewing && !date.isAfter(today)) m.pressable(haptic = false) { ui.dayDialog = date } else m },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$n", style = Type.callout.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal), color = fg)
                                Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(if (log != null) (if (kind == CycleDayKind.Period) Color.White else c.accent) else Color.Transparent))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend() {
    val c = T.colors
    @Composable fun Item(color: Color, text: String, ring: Boolean = false) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(if (ring) color.copy(alpha = .14f) else color).let { if (ring) it.border(1.dp, color, CircleShape) else it })
            Spacer(Modifier.width(5.dp))
            Text(text, style = Type.caption, color = c.secondary)
        }
    }
    FlowRow(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Item(PERIOD_RED, "Period"); Item(PERIOD_RED, "Expected", ring = true); Item(FERTILE_TEAL.copy(alpha = .5f), "Fertile window"); Item(FERTILE_TEAL, "Ovulation")
        Item(c.accent, "Logged")
    }
}

// ------------------------------------------------------------------ sheets

/** All the sheets and dialogs of the Cycle tab. Call this once from CalendarScreen. */
@Composable
fun CycleSheets(vm: AppViewModel, ui: CycleUi) {
    ui.dayDialog?.let { day -> DayLogSheet(vm, day) { ui.dayDialog = null } }
    if (ui.addingPeriod) PeriodSheet(vm, null) { ui.addingPeriod = false }
    ui.periodSheet?.let { p -> PeriodSheet(vm, p) { ui.periodSheet = null } }
    if (ui.settings) CycleSettingsSheet(vm) { ui.settings = false }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DayLogSheet(vm: AppViewModel, day: LocalDate, onDismiss: () -> Unit) {
    val c = T.colors
    val existing = vm.period?.mine?.days?.firstOrNull { it.date == day.toString() }
    var flow by remember { mutableStateOf(existing?.flow) }
    var symptoms by remember { mutableStateOf(existing?.symptoms?.toSet() ?: emptySet()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = Type.title, color = c.label)
            Text("How is today? Your partner can see this too.", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            Text("FLOW", style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Pair<Int?, String>>(null to "None", 1 to "Light", 2 to "Medium", 3 to "Heavy").forEach { (v, label) ->
                    val on = flow == v
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(14.dp)).background(if (on) PERIOD_RED.copy(alpha = .2f) else c.card)
                            .border(if (on) 1.5.dp else 1.dp, if (on) PERIOD_RED else c.separator, RoundedCornerShape(14.dp)).pressable(haptic = false) { flow = v },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = Type.callout.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = c.label) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("SYMPTOMS", style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Symptom.entries.forEach { sy ->
                    val on = sy.key in symptoms
                    Text(
                        "${sy.emoji} ${sy.label}", style = Type.callout, color = c.label,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.accentSoft else c.card)
                            .border(if (on) 1.5.dp else 1.dp, if (on) c.accent else c.separator, RoundedCornerShape(50))
                            .pressable(haptic = false) { symptoms = if (on) symptoms - sy.key else symptoms + sy.key }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                note, { if (it.length <= 300) note = it }, label = { Text("A note (optional)") }, maxLines = 3, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Save", onClick = { vm.savePeriodDay(day.toString(), PeriodDayBody(flow, symptoms.toList(), note.trim()), onDismiss) }, loading = vm.busy)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSheet(vm: AppViewModel, existing: PeriodEntry?, onDismiss: () -> Unit) {
    val c = T.colors
    val today = LocalDate.now()
    var start by remember { mutableStateOf(d(existing?.startDate) ?: today.minusDays(5)) }
    var end by remember { mutableStateOf(d(existing?.endDate)) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var pick by remember { mutableStateOf<String?>(null) }       // "start" | "end"
    var confirmDelete by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bad = end != null && end!!.isBefore(start)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(if (existing == null) "Add a past period" else "Edit period", style = Type.title, color = c.label)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card)) {
                DateRow("First day", start.format(DateTimeFormatter.ofPattern("d MMM yyyy"))) { pick = "start" }
                DateRow("Last day", end?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "Still going") { pick = "end" }
            }
            if (end != null) Text("Clear last day (still going)", style = Type.callout, color = c.secondary, modifier = Modifier.pressable(haptic = false) { end = null }.padding(vertical = 10.dp, horizontal = 4.dp))
            if (bad) Text("The last day can't be before the first day.", style = Type.footnote, color = c.danger, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                note, { if (it.length <= 300) note = it }, label = { Text("A note (optional)") }, maxLines = 3, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Save", onClick = { vm.savePeriod(existing?.id, PeriodBody(start.toString(), end?.toString(), note.trim()), onDismiss) }, enabled = !bad, loading = vm.busy)
            if (existing != null) {
                Spacer(Modifier.height(10.dp))
                Text("Delete", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.danger, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().pressable { confirmDelete = true }.padding(12.dp))
            }
        }
    }
    pick?.let { which ->
        val initial = (if (which == "start") start else end ?: start).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = initial,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { pick = null },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let {
                    val dt = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    if (which == "start") start = dt else end = dt
                }
                pick = null
            }) { Text("OK", color = c.accent) } },
            dismissButton = { TextButton(onClick = { pick = null }) { Text("Cancel", color = c.secondary) } },
        ) { DatePicker(state = state) }
    }
    if (confirmDelete && existing != null) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = c.sheet,
        title = { Text("Delete this period?") }, text = { Text("It is removed from your history and your predictions.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deletePeriod(existing.id, onDismiss) }) { Text("Delete", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = c.secondary) } },
    )
}

@Composable
private fun DateRow(label: String, value: String, onClick: () -> Unit) {
    val c = T.colors
    Row(Modifier.fillMaxWidth().pressable(haptic = false, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.body, color = c.label, modifier = Modifier.weight(1f))
        Text(value, style = Type.body, color = c.accent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CycleSettingsSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val c = T.colors
    val cur = vm.period?.mine?.settings ?: PeriodSettings()
    var cycle by remember { mutableIntStateOf(cur.cycleLength) }
    var length by remember { mutableIntStateOf(cur.periodLength) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Cycle settings", style = Type.title, color = c.label)
            Text("Used for predictions until you have logged a couple of periods. After that Tandem learns from your own dates.", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            Stepper("Cycle length", "$cycle days", { if (cycle > 20) cycle-- }, { if (cycle < 45) cycle++ })
            Spacer(Modifier.height(8.dp))
            Stepper("Period length", "$length days", { if (length > 2) length-- }, { if (length < 10) length++ })
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Save", onClick = { vm.savePeriodSettings(PeriodSettingsBody(cycle, length), onDismiss) }, loading = vm.busy)
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = T.colors
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.body, color = c.label, modifier = Modifier.weight(1f))
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.accentSoft).pressable(onClick = onMinus), contentAlignment = Alignment.Center) { Text("−", style = Type.title, color = c.accent) }
        Text(value, style = Type.headline, color = c.label, textAlign = TextAlign.Center, modifier = Modifier.width(84.dp))
        Box(Modifier.size(38.dp).clip(CircleShape).background(c.accentSoft).pressable(onClick = onPlus), contentAlignment = Alignment.Center) { Text("+", style = Type.title, color = c.accent) }
    }
}
