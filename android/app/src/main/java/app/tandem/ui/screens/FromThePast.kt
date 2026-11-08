package app.tandem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.TandemApp
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One note from the past for the diary to bring back, and how to say when it was. */
class PastPick(val entry: DiaryEntry, val label: String)

/**
 * Picks one note from before: this day in an earlier year first (from the server's "on this day", which
 * reaches back furthest), then a note from around this date a year or more ago, then a month ago, then any
 * note older than two weeks, chosen the same way all day so it doesn't change every time you look.
 */
fun pickFromThePast(memories: Memories?, diary: List<DiaryEntry>, today: LocalDate = LocalDate.now()): PastPick? {
    memories?.onThisDay?.firstOrNull { it.diary != null }?.let { m ->
        return PastPick(m.diary!!, when (m.yearsAgo) { 1 -> "This day last year"; in 2..99 -> "This day, ${m.yearsAgo} years ago"; else -> "On this day" })
    }
    val old = diary.filter { ChronoUnit.DAYS.between(it.day(), today) >= 14 }
    if (old.isEmpty()) return null
    // around this date in an earlier year (within a week)
    old.filter { it.day().year < today.year }.minByOrNull { e ->
        val d = e.day().withYear(today.year)
        abs(ChronoUnit.DAYS.between(d, today))
    }?.let { e ->
        val gap = abs(ChronoUnit.DAYS.between(e.day().withYear(today.year), today))
        if (gap <= 7) {
            val years = today.year - e.day().year
            return PastPick(e, if (years == 1) "This time last year" else "This time, $years years ago")
        }
    }
    // about a month ago
    old.minByOrNull { abs(ChronoUnit.DAYS.between(it.day(), today.minusMonths(1))) }?.let { e ->
        if (abs(ChronoUnit.DAYS.between(e.day(), today.minusMonths(1))) <= 3) return PastPick(e, "A month ago")
    }
    // otherwise one from before, the same one all day
    val e = old[(today.toEpochDay() % old.size).toInt()]
    return PastPick(e, "From " + e.day().format(DateTimeFormatter.ofPattern(if (e.day().year == today.year) "d MMMM" else "d MMMM yyyy")))
}

/** The diary's one memory: a quiet card with the picture (if there is one), the note and when it was. */
@Composable
fun FromThePastCard(vm: AppViewModel, data: SyncResponse, modifier: Modifier = Modifier) {
    val c = T.colors
    LaunchedEffect(Unit) { vm.loadMemories() }
    val pick = remember(vm.memories, data.diary) { pickFromThePast(vm.memories, data.diary) } ?: return
    val e = pick.entry
    val repo = (LocalContext.current.applicationContext as TandemApp).repo
    val who = if (e.userId == data.me.id) data.me else data.couple.partner
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.clip(shape).background(c.card).border(1.dp, c.special.copy(alpha = .22f), shape)) {
        if (e.images.isNotEmpty()) Box {
            AsyncImage(repo.diaryImageUrl(e.images.first()), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(c.separator))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99120A3A)))))
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, tint = c.special, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(pick.label, style = Type.footnote, color = c.special, modifier = Modifier.weight(1f))
                Text(e.day().format(DateTimeFormatter.ofPattern("d MMM yyyy")), style = Type.footnote, color = c.tertiary)
            }
            Text(e.text, style = Type.title.copy(fontSize = 18.sp, lineHeight = 25.sp), color = c.label, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(who, 22.dp, ring = if (e.userId == data.me.id) c.me else c.partner)
                Spacer(Modifier.width(8.dp))
                Text(if (e.userId == data.me.id) "You wrote this" else "${who?.displayName?.substringBefore(' ') ?: "Your partner"} wrote this", style = Type.footnote, color = c.secondary, modifier = Modifier.weight(1f))
                if (e.rating != null) HeartRating(e.rating, size = 14.dp)
            }
        }
    }
}

