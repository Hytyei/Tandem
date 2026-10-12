package app.tandem.ui.screens

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.R
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed interface TlRow {
    data class Header(val label: String, val key: String) : TlRow
    data class Item(val entry: MoodEntry) : TlRow
}

private fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "EEEE, d MMMM" else "d MMMM yyyy"))
    }
}

@Composable
fun TimelineScreen(vm: AppViewModel) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val meId = data.me.id
    val partner = data.couple.partner
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 both, 1 me, 2 partner

    val rows = remember(data.moods, filter) {
        val list = data.moods.filter { filter == 0 || (filter == 1 && it.userId == meId) || (filter == 2 && it.userId != meId) }
        val out = mutableListOf<TlRow>()
        var lastDay: LocalDate? = null
        for (m in list) {
            val d = Instant.ofEpochMilli(m.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            if (d != lastDay) { out += TlRow.Header(dayLabel(d), d.toString()); lastDay = d }
            out += TlRow.Item(m)
        }
        out
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item { LargeTitle("Mood timeline", subtitle = "You & ${partner?.displayName?.substringBefore(' ') ?: "partner"}") }
        item {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Segmented(listOf("Both", "Me", partner?.displayName?.substringBefore(' ') ?: "Partner"), filter, { filter = it })
            }
        }
        item {
            // Legend
            Row(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(data.me, 22.dp, ring = c.me); Text("You", style = Type.footnote, color = c.secondary)
                Avatar(partner, 22.dp, ring = c.partner); Text(partner?.displayName?.substringBefore(' ') ?: "Partner", style = Type.footnote, color = c.secondary)
            }
        }
        if (rows.isEmpty()) {
            item { EmptyState(R.drawable.mood_calm, "Nothing here yet", "Share your first mood from the Today tab.") }
        }
        items(rows, key = { r -> when (r) { is TlRow.Header -> "h-${r.key}"; is TlRow.Item -> r.entry.id } }) { r ->
            when (r) {
                is TlRow.Header -> Text(r.label, style = Type.headline, color = c.label, modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp))
                is TlRow.Item -> TimelineRow(r.entry, mine = r.entry.userId == meId, user = if (r.entry.userId == meId) data.me else partner)
            }
        }
    }
}

/** Mood/activity tints are mid-tones: lighten them on dark cards, deepen them on light cards. */
private fun readable(tint: Color, dark: Boolean): Color = if (dark) lerp(tint, Color.White, .6f) else lerp(tint, Color.Black, .3f)

@Composable
private fun TimelineRow(e: MoodEntry, mine: Boolean, user: User?) {
    val c = T.colors
    val accent = if (mine) c.me else c.partner
    val time = remember(e.createdAt) { Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(IntrinsicSize.Min).animateContentSize(), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f).padding(vertical = 5.dp), contentAlignment = Alignment.CenterEnd) {
            if (mine) EntryCard(e, time, accent, alignEnd = true)
        }
        // Rail with dot
        Box(Modifier.width(28.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxHeight().width(2.dp).background(if (c.isDark) Color.White.copy(alpha = .30f) else c.separator))
            Box(Modifier.padding(top = 24.dp).size(15.dp).clip(CircleShape).background(accent).border(2.5.dp, c.sheet, CircleShape))
        }
        Box(Modifier.weight(1f).padding(vertical = 5.dp), contentAlignment = Alignment.CenterStart) {
            if (!mine) EntryCard(e, time, accent, alignEnd = false)
        }
    }
}

@Composable
private fun EntryCard(e: MoodEntry, time: String, accent: Color, alignEnd: Boolean) {
    val c = T.colors
    // Dark mode: a lighter card with a hairline border, and tints lightened so they stay readable on it.
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().staggerIn(0, 0).clip(shape)
            .background(if (c.isDark) Color.White.copy(alpha = .14f) else c.card)
            .border(1.dp, if (c.isDark) Color.White.copy(alpha = .16f) else Color.White.copy(alpha = .7f), shape)
            .padding(12.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(e.artRes(), 46.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(e.headline(), style = Type.callout.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp), color = readable(e.tint(), c.isDark), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(time, style = Type.caption, color = c.secondary)
            }
        }
        if (e.note.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(e.note, style = Type.footnote, color = c.label.copy(alpha = .88f))
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(3.dp).width(28.dp).clip(RoundedCornerShape(2.dp)).background(accent))
    }
}
