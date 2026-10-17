package app.tandem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The "Notes" part of the Calendar tab: notes and checklists you both can read and tick off. */
fun LazyListScope.notesSection(
    vm: AppViewModel, data: SyncResponse, onNew: (kind: String) -> Unit, onOpen: (Note) -> Unit,
) {
    val notes = vm.notes
    item {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { PrimaryButton("New checklist", onClick = { onNew("list") }) }
            Box(Modifier.weight(1f)) { PrimaryButton("New note", onClick = { onNew("note") }) }
        }
    }
    if (notes == null) {
        item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = T.colors.accent) } }
        return
    }
    if (notes.isEmpty()) {
        item { EmptyState("Nothing here yet", "Shopping lists, packing lists, things to remember: write them once and you both see them. Tick items off together.") }
        return
    }
    items(notes.size, key = { notes[it].id }) { i ->
        NoteCard(notes[i], data, vm, onOpen = { onOpen(notes[i]) })
    }
}

@Composable
private fun NoteCard(note: Note, data: SyncResponse, vm: AppViewModel, onOpen: () -> Unit) {
    val c = T.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(shape).background(c.card)
            .border(1.dp, if (note.pinned) c.accent.copy(alpha = .5f) else if (c.isDark) androidx.compose.ui.graphics.Color.White.copy(alpha = .10f) else androidx.compose.ui.graphics.Color.White.copy(alpha = .7f), shape)
            .pressable(haptic = false, onClick = onOpen).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (note.isList) "☑️" else "📝", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(note.title, style = Type.headline, color = c.label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (note.pinned) Text("📌", fontSize = 14.sp)
        }
        val meta = buildList {
            if (note.isList && note.items.isNotEmpty()) add("${note.doneCount}/${note.items.size} done")
            note.date?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull()?.let { add(it.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))) } }
            val by = if (note.updatedBy == data.me.id) "you" else data.couple.partner?.displayName
            if (by != null && note.updatedAt > 0) add("edited by $by")
        }
        if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = Type.caption, color = c.secondary, modifier = Modifier.padding(top = 2.dp, start = 30.dp))
        if (note.isList) {
            Spacer(Modifier.height(8.dp))
            if (note.items.isNotEmpty()) LinearProgressIndicator(
                progress = { note.doneCount.toFloat() / note.items.size }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                color = c.accent, trackColor = c.separator,
            )
            val shown = note.items.sortedBy { it.done }.take(6)
            shown.forEach { it2 ->
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = it2.done, onCheckedChange = { vm.setItemDone(note, it2, it) },
                        colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.tertiary),
                    )
                    Text(
                        it2.text, style = Type.body, color = if (it2.done) c.tertiary else c.label,
                        textDecoration = if (it2.done) TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (note.items.size > shown.size) Text("+ ${note.items.size - shown.size} more", style = Type.caption, color = c.secondary, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
        } else if (note.body.isNotBlank()) {
            Text(note.body, style = Type.callout, color = c.label, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Create or edit a note / checklist. [existing] == null creates one of [newKind]. Checklist items of an existing list are edited live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteSheet(vm: AppViewModel, existing: Note?, newKind: String, onDismiss: () -> Unit) {
    val c = T.colors
    val live = existing?.let { e -> vm.notes?.firstOrNull { it.id == e.id } ?: e }       // always the freshest copy (ticks arrive live)
    val kind = existing?.kind ?: newKind
    val isList = kind == "list"
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var pinned by remember { mutableStateOf(existing?.pinned ?: false) }
    var date by remember { mutableStateOf(existing?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) }
    var pickDate by remember { mutableStateOf(false) }
    var newItem by remember { mutableStateOf("") }
    var draftItems by remember { mutableStateOf(listOf<String>()) }       // only used while creating a list
    var confirmDelete by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label,
        cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card,
    )

    fun addItem() {
        val t = newItem.trim()
        if (t.isEmpty()) return
        if (existing != null) vm.addNoteItem(existing.id, t) else draftItems = draftItems + t
        newItem = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(
                when { existing == null && isList -> "New checklist"; existing == null -> "New note"; isList -> "Checklist"; else -> "Note" },
                style = Type.title, color = c.label,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                title, { if (it.length <= 80) title = it }, label = { Text("Title") }, singleLine = true,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors,
            )
            Spacer(Modifier.height(10.dp))
            if (!isList) {
                OutlinedTextField(
                    body, { if (it.length <= 4000) body = it }, label = { Text("Write something…") }, minLines = 4, maxLines = 12,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors,
                )
            } else {
                val items: List<Pair<NoteItem?, String>> = if (existing != null) live!!.items.map { it to it.text } else draftItems.map { null to it }
                items.forEachIndexed { idx, (item, text) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (item != null) Checkbox(
                            checked = item.done, onCheckedChange = { vm.setItemDone(live!!, item, it) },
                            colors = CheckboxDefaults.colors(checkedColor = c.accent, uncheckedColor = c.tertiary),
                        ) else Spacer(Modifier.width(14.dp))
                        Text(
                            text, style = Type.body, color = if (item?.done == true) c.tertiary else c.label,
                            textDecoration = if (item?.done == true) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f),
                        )
                        Text(
                            "✕", style = Type.body, color = c.tertiary,
                            modifier = Modifier.pressable(haptic = false) {
                                if (item != null) vm.deleteNoteItem(item.id) else draftItems = draftItems.filterIndexed { i, _ -> i != idx }
                            }.padding(10.dp),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        newItem, { if (it.length <= 200) newItem = it }, placeholder = { Text("Add an item") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f), colors = fieldColors,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { addItem() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { addItem() }, enabled = newItem.isNotBlank()) { Text("Add", color = c.accent) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card), verticalAlignment = Alignment.CenterVertically) {
                Text("Pin to the top", style = Type.body, color = c.label, modifier = Modifier.weight(1f).padding(start = 16.dp))
                Switch(pinned, { pinned = it }, modifier = Modifier.padding(end = 12.dp), colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.card).pressable(haptic = false) { pickDate = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Date (optional)", style = Type.body, color = c.label, modifier = Modifier.weight(1f))
                Text(date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: "None", style = Type.body, color = c.accent)
                if (date != null) Text("  ✕", style = Type.body, color = c.tertiary, modifier = Modifier.pressable(haptic = false) { date = null })
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                if (existing == null) "Save" else "Save changes",
                onClick = {
                    // a half-typed item is not lost when you tap Save
                    val pending = newItem.trim()
                    val all = if (existing == null && pending.isNotEmpty()) draftItems + pending else draftItems
                    if (existing != null && pending.isNotEmpty()) vm.addNoteItem(existing.id, pending)
                    vm.saveNote(existing?.id, NoteBody(title.trim(), body.trim(), kind, date?.toString(), pinned, if (existing == null) all else emptyList()), onDismiss)
                },
                enabled = title.isNotBlank(), loading = vm.busy,
            )
            if (existing != null) {
                Spacer(Modifier.height(10.dp))
                Text("Delete", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.danger, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().pressable { confirmDelete = true }.padding(12.dp))
            }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                pickDate = false
            }) { Text("OK", color = c.accent) } },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel", color = c.secondary) } },
        ) { DatePicker(state = state) }
    }
    if (confirmDelete && existing != null) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = c.sheet,
        title = { Text("Delete this ${if (isList) "checklist" else "note"}?") },
        text = { Text("It disappears for both of you.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteNote(existing.id); onDismiss() }) { Text("Delete", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = c.secondary) } },
    )
}
