package app.tandem.ui.screens

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.R
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(vm: AppViewModel) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    var compose by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<DiaryEntry?>(null) }
    val haptic = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize()) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp),
            verticalItemSpacing = 12.dp, horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) { LargeTitle("Diary wall", subtitle = "Notes for each other") }
            if (data.diary.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                EmptyState(R.drawable.act_photography, "A blank wall", "Pin the first note — a memory, a thought, a thank you.")
            }
            itemsIndexed(data.diary, key = { _, e -> e.id }) { i, e ->
                val mine = e.userId == data.me.id
                val author = if (mine) data.me else data.couple.partner
                NoteCard(e, author, mine, i, onLongPress = {
                    if (mine) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); deleting = e }
                })
            }
        }

        // Floating add button
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 112.dp).size(60.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.accent, Color(0xFFFF6A5E)))).pressable { compose = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = "New note", tint = Color.White, modifier = Modifier.size(30.dp)) }
    }

    if (compose) DiarySheet(vm, onDismiss = { compose = false })

    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this note?") },
            text = { Text("It will disappear from the wall for both of you.") },
            confirmButton = { TextButton(onClick = { vm.deleteDiary(entry.id); deleting = null }) { Text("Delete", color = c.danger) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
            containerColor = c.sheet,
        )
    }
}

private val NotePalettesLight = listOf(Color(0xFFFFE1EA), Color(0xFFE7E1FF), Color(0xFFFFEBD6), Color(0xFFDDF2FF))
private val NotePalettesDark = listOf(Color(0x47FF8FA3), Color(0x479C8CFF), Color(0x47FFC48A), Color(0x477FD0F0))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(e: DiaryEntry, author: User?, mine: Boolean, index: Int, onLongPress: () -> Unit) {
    val c = T.colors
    val palette = if (c.isDark) NotePalettesDark else NotePalettesLight
    val bg = palette[(e.id.hashCode().let { if (it < 0) -it else it } + index) % palette.size]
    val tilt = remember(e.id) { (((e.id.hashCode() % 5) - 2) * 0.5f) }
    val date = remember(e.createdAt) {
        Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm"))
    }
    Column(
        Modifier.fillMaxWidth().staggerIn(index).rotate(tilt).clip(RoundedCornerShape(22.dp)).background(bg)
            .combinedClickable(onClick = {}, onLongClick = onLongPress).padding(14.dp),
    ) {
        Mood.from(e.mood)?.let { Art(it.art, 34.dp); Spacer(Modifier.height(6.dp)) }
        Text(e.text, style = Type.callout, color = c.label)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(author, 20.dp, ring = if (mine) c.me else c.partner)
            Spacer(Modifier.width(6.dp))
            Column {
                Text(author?.displayName?.substringBefore(' ') ?: "", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                Text(date, style = Type.caption.copy(fontSize = 10.sp), color = c.secondary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiarySheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val c = T.colors
    var text by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf<Mood?>(null) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("New note", style = Type.title, color = c.label)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                text, { if (it.length <= 2000) text = it },
                placeholder = { Text("What's on your mind?", color = c.tertiary) },
                shape = RoundedCornerShape(20.dp), minLines = 5, maxLines = 9, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent,
                    focusedContainerColor = c.card, unfocusedContainerColor = c.card),
            )
            Spacer(Modifier.height(12.dp))
            Text("Feeling (optional)", style = Type.footnote, color = c.secondary)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Mood.entries.forEach { m ->
                    val on = mood == m
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (on) m.tint.copy(alpha = .2f) else c.card).pressable { mood = if (on) null else m }.padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Art(m.art, 32.dp) }
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Pin to wall", onClick = { vm.addDiary(text, mood, onDismiss) }, enabled = text.isNotBlank(), loading = vm.busy)
        }
    }
}
