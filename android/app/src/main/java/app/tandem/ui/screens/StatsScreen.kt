package app.tandem.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

/**
 * Your story in numbers. The days-together constellation leads; everything under it is quiet: six figures,
 * a mirrored month of moods (you above the line, your partner below), how your moods are mixed, and what
 * you've done together as ranked lists.
 */
@Composable
fun StatsScreen(vm: AppViewModel, onBack: () -> Unit, openAchievements: () -> Unit) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val detail = vm.stats
    LaunchedEffect(Unit) { vm.loadStats() }

    val me = data.me
    val partner = data.couple.partner
    val pName = partner?.displayName?.substringBefore(' ') ?: "Your partner"
    val days = daysTogether(data.couple.anniversary)
    val st = data.stats ?: Stats()

    Box(Modifier.fillMaxSize().background(c.background)) {
        DreamBackground {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = 40.dp)) {
                Row(Modifier.padding(start = 14.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BackDisc(onBack)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Stats", style = Type.title, color = c.label)
                        Text("You and $pName", style = Type.footnote, color = c.secondary)
                    }
                }

                // ---------- days together, the one bright thing
                GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().staggerIn(0), padding = 20.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (days != null) {
                            ConstellationDays(days, Modifier.fillMaxWidth(), height = 230.dp)
                            Text(if (days == 1L) "day together" else "days together", style = Type.title, color = c.label, modifier = Modifier.padding(top = 6.dp))
                            parseDate(data.couple.anniversary)?.let { since ->
                                val p = Period.between(since, LocalDate.now())
                                val parts = listOfNotNull(
                                    p.years.takeIf { it > 0 }?.let { "$it ${if (it == 1) "year" else "years"}" },
                                    p.months.takeIf { it > 0 }?.let { "$it ${if (it == 1) "month" else "months"}" },
                                ).joinToString(", ")
                                Text("Since " + since.format(DateTimeFormatter.ofPattern("d MMMM yyyy")) + if (parts.isNotEmpty()) " · $parts" else "", style = Type.footnote, color = c.secondary)
                            }
                            NextMilestone(days)
                        } else Text("Set your anniversary in Profile to start counting", style = Type.callout, color = c.secondary)
                    }
                }

                // ---------- six figures, quietly
                GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().staggerIn(1), padding = 0.dp) {
                    val cells = listOf(
                        st.moods.toLong() to "moods shared", st.diary.toLong() to "diary notes", st.activeDays.toLong() to "active days",
                        (detail?.streak ?: 0).toLong() to "day streak", (detail?.bestStreak ?: 0).toLong() to "best streak", (detail?.photos ?: 0).toLong() to "pictures",
                    )
                    cells.chunked(3).forEachIndexed { r, row ->
                        if (r > 0) HorizontalDivider(color = c.label.copy(alpha = .06f))
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            row.forEachIndexed { i, (v, label) ->
                                if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(c.label.copy(alpha = .06f)))
                                Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 16.dp)) {
                                    CountUp(v, Type.title.copy(fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum"), c.label)
                                    Text(label, style = Type.footnote, color = c.secondary, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // ---------- achievements, one line
                if (detail != null) {
                    val ach = Achievements.context(days ?: 0L, detail)
                    val done = Achievements.all.count { it.unlocked(ach) }
                    val latest = Achievements.all.filter { it.unlocked(ach) }.takeLast(3)
                    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().staggerIn(2).pressable(onClick = openAchievements), padding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(if (latest.isEmpty()) 44.dp else (44 + (latest.size - 1) * 22).dp).height(44.dp)) {
                                if (latest.isEmpty()) Medal(Achievements.all.first().icon, false, 0f, size = 44.dp)
                                latest.forEachIndexed { i, a -> Medal(a.icon, true, 1f, Modifier.offset(x = (i * 22).dp), size = 44.dp) }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Achievements", style = Type.headline, color = c.label)
                                Text("$done of ${Achievements.all.size} earned", style = Type.footnote, color = c.secondary)
                            }
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.tertiary)
                        }
                    }
                }

                // ---------- the last 30 days, mirrored
                SectionHeader("Last 30 days")
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(3)) {
                    if (detail == null) Text("Loading…", style = Type.callout, color = c.secondary)
                    else {
                        MirroredMonth(detail.daily, me.id)
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ChartLegend("You" to c.me, pName to c.partner)
                            Spacer(Modifier.weight(1f))
                            Text("mood shares a day", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
                        }
                    }
                }

                // ---------- how your moods are mixed
                SectionHeader("Mood mix")
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(4)) {
                    if (detail == null) Text("Loading…", style = Type.callout, color = c.secondary)
                    else {
                        MoodMix("You", me.id, detail.moodCounts)
                        Spacer(Modifier.height(20.dp))
                        MoodMix(pName, partner?.id ?: "", detail.moodCounts)
                    }
                }

                // ---------- what you've done together
                SectionHeader("Things you did together")
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(5)) {
                    val types = detail?.dateTypeCounts.orEmpty().mapNotNull { x -> DateType.from(x.dateType)?.let { it.label to x.n } }.sortedByDescending { it.second }.take(8)
                    when {
                        detail == null -> Text("Loading…", style = Type.callout, color = c.secondary)
                        types.isEmpty() -> Text("Tag diary notes and plans with what you did, like a date night or a movie night, and they'll be counted here", style = Type.callout, color = c.secondary)
                        else -> RankedBars(types)
                    }
                }

                SectionHeader("What you're up to most")
                GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(6)) {
                    val top = detail?.activityCounts.orEmpty().groupBy { it.activity }.map { (k, v) -> (Activity.from(k)?.label ?: k) to v.sumOf { it.n } }.sortedByDescending { it.second }.take(6)
                    when {
                        detail == null -> Text("Loading…", style = Type.callout, color = c.secondary)
                        top.isEmpty() -> Text("Share what you're up to with your mood and your favourites will show up here", style = Type.callout, color = c.secondary)
                        else -> RankedBars(top)
                    }
                }
            }
        }
    }
}

@Composable
private fun NextMilestone(days: Long) {
    val c = T.colors
    val milestones = listOf(50L, 100, 200, 300, 365, 500, 730, 1000, 1500, 2000, 2500, 3000, 3650, 5000, 7300, 10000)
    val next = milestones.firstOrNull { it > days } ?: return
    val prev = milestones.lastOrNull { it <= days } ?: 0L
    val g = grow(((days - prev).toFloat() / (next - prev)).coerceIn(0f, 1f), 1100)
    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$prev", style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace), color = c.tertiary)
        Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .08f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(g).clip(RoundedCornerShape(2.dp)).background(LitIvory))
        }
        Text("$next", style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace), color = c.tertiary)
    }
    Text("${next - days} ${if (next - days == 1L) "day" else "days"} to $next", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 6.dp))
}

/** 30 days, today on the right: your shares rise above the line, your partner's hang below it. */
@Composable
private fun MirroredMonth(daily: List<DailyCount>, meId: String) {
    val c = T.colors
    val today = LocalDate.now()
    val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = daily.groupBy { it.day }
    val mine = days.map { d -> byDay[d.toString()]?.filter { it.userId == meId }?.sumOf { it.n } ?: 0 }
    val theirs = days.map { d -> byDay[d.toString()]?.filter { it.userId != meId }?.sumOf { it.n } ?: 0 }
    val max = maxOf(1, (mine + theirs).max())
    val g = grow(1f, 900)
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val mid = size.height / 2
        val slot = size.width / days.size
        val w = slot * .32f
        drawLine(Color.White.copy(alpha = .14f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
        days.indices.forEach { i ->
            val x = i * slot + (slot - w) / 2
            val a = (mid - 3.dp.toPx()) * mine[i] / max * g
            val b = (mid - 3.dp.toPx()) * theirs[i] / max * g
            if (a > 0f) drawRoundRect(c.me, Offset(x, mid - 2.dp.toPx() - a), Size(w, a), CornerRadius(w / 2))
            if (b > 0f) drawRoundRect(c.partner, Offset(x, mid + 2.dp.toPx()), Size(w, b), CornerRadius(w / 2))
            if (mine[i] == 0 && theirs[i] == 0) drawCircle(Color.White.copy(alpha = .12f), 1.5.dp.toPx(), Offset(x + w / 2, mid))
        }
        // week marks under the chart
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 10.dp.toPx(); color = c.tertiary.toArgb(); typeface = android.graphics.Typeface.MONOSPACE; textAlign = android.graphics.Paint.Align.CENTER }
        listOf(0, 14, 29).forEach { i ->
            val label = if (i == 29) "today" else days[i].format(DateTimeFormatter.ofPattern("d MMM"))
            drawContext.canvas.nativeCanvas.drawText(label, (i * slot + slot / 2).coerceIn(18.dp.toPx(), size.width - 18.dp.toPx()), size.height, paint)
        }
    }
}

/** One person's moods as a single stacked bar, the three biggest named under it. */
@Composable
private fun MoodMix(name: String, userId: String, counts: List<MoodCount>) {
    val c = T.colors
    val mine = counts.filter { it.userId == userId }
    val total = mine.sumOf { it.n }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = Type.callout, color = c.label, modifier = Modifier.weight(1f))
        Text("$total ${if (total == 1) "share" else "shares"}", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.tertiary)
    }
    if (total == 0) { Text("Nothing shared yet", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 6.dp)); return }
    val sorted = Mood.entries.map { m -> m to (mine.firstOrNull { it.mood == m.key }?.n ?: 0) }.filter { it.second > 0 }.sortedByDescending { it.second }
    val g = grow(1f, 900)
    Row(Modifier.padding(top = 8.dp).fillMaxWidth(g.coerceAtLeast(.02f)).height(10.dp).clip(RoundedCornerShape(5.dp)), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        sorted.forEach { (m, n) -> Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(m.tint)) }
    }
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        sorted.take(3).forEach { (m, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(m.tint))
                Spacer(Modifier.width(6.dp))
                Text("${m.label} ${(n * 100f / total).toInt()}%", style = Type.footnote, color = c.secondary)
            }
        }
    }
}
