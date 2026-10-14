package app.tandem.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.graphics.vector.ImageVector
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

/** The single stats page: days together as a node graph, key numbers with flat icons, and a few small charts. */
@Composable
fun StatsScreen(vm: AppViewModel, onBack: () -> Unit, openAchievements: () -> Unit) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val detail = vm.stats
    LaunchedEffect(Unit) { vm.loadStats() }

    val me = data.me
    val partner = data.couple.partner
    val days = daysTogether(data.couple.anniversary)
    val st = data.stats ?: Stats()

    Box(Modifier.fillMaxSize().background(c.background)) {
        DreamBackground {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = 32.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.card).pressable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.label)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("OUR STORY IN NUMBERS", style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary)
                        Text("Stats", style = Type.largeTitle, color = c.label)
                    }
                }

                // ---------- Days together (node graph)
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(0), padding = 20.dp) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (days != null) {
                            ConstellationDays(days, Modifier.fillMaxWidth(), height = 250.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(if (days == 1L) "day together" else "days together", style = Type.title, color = c.label)
                            parseDate(data.couple.anniversary)?.let { since ->
                                val p = Period.between(since, LocalDate.now())
                                val parts = listOfNotNull(
                                    p.years.takeIf { it > 0 }?.let { "$it ${if (it == 1) "year" else "years"}" },
                                    p.months.takeIf { it > 0 }?.let { "$it ${if (it == 1) "month" else "months"}" },
                                ).joinToString(", ")
                                Text(
                                    "Since " + since.format(DateTimeFormatter.ofPattern("d MMMM yyyy")) + if (parts.isNotEmpty()) "  ·  $parts" else "",
                                    style = Type.footnote, color = c.secondary,
                                )
                            }
                            MilestoneBar(days)
                        } else {
                            Text("Set your anniversary in Profile to start counting", style = Type.callout, color = c.secondary)
                        }
                    }
                }

                // ---------- Key numbers (flat, solid-colour icons)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(Modifier.weight(1f).staggerIn(1), Icons.Filled.EmojiEmotions, "Moods shared", st.moods.toLong(), c.accent)
                    Metric(Modifier.weight(1f).staggerIn(2), Icons.Filled.Edit, "Diary notes", st.diary.toLong(), c.me)
                    Metric(Modifier.weight(1f).staggerIn(3), Icons.Filled.CalendarMonth, "Active days", st.activeDays.toLong(), c.special)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric(Modifier.weight(1f).staggerIn(4), Icons.Filled.LocalFireDepartment, "Day streak", (detail?.streak ?: 0).toLong(), Color(0xFFFF7043))
                    Metric(Modifier.weight(1f).staggerIn(5), Icons.Filled.EmojiEvents, "Best streak", (detail?.bestStreak ?: 0).toLong(), Color(0xFF26A69A))
                    Metric(Modifier.weight(1f).staggerIn(6), Icons.Filled.PhotoLibrary, "Photos", (detail?.photos ?: 0).toLong(), Color(0xFF5C6BC0))
                }

                // ---------- Achievements summary
                if (detail != null) {
                    val ach = Achievements.context(days ?: 0L, detail)
                    val done = Achievements.all.filter { it.unlocked(ach) }
                    Spacer(Modifier.height(12.dp))
                    SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(7).pressable(onClick = openAchievements)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.special.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.EmojiEvents, null, tint = c.special, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Achievements", style = Type.headline, color = c.label)
                                Text("${done.size} of ${Achievements.all.size} unlocked", style = Type.footnote, color = c.secondary)
                            }
                            Text("View all ›", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                        }
                        Spacer(Modifier.height(12.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.label.copy(alpha = .10f))) {
                            Box(Modifier.fillMaxWidth(done.size.toFloat() / Achievements.all.size).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c.special))
                        }
                        if (done.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                done.takeLast(6).forEach { a ->
                                    Box(Modifier.size(34.dp).clip(CircleShape).background(a.group.tint.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                                        Icon(a.icon, a.title, tint = a.group.tint, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // ---------- Last 30 days
                SectionHeader("Last 30 days")
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(7)) {
                    if (detail == null) Text("Loading…", style = Type.callout, color = c.secondary)
                    else {
                        DailyBars(detail.daily, me.id)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Legend(c.me, "You"); Legend(c.partner, partner?.displayName?.substringBefore(' ') ?: "Partner")
                            Spacer(Modifier.weight(1f))
                            Text("mood updates per day", style = Type.caption, color = c.secondary)
                        }
                    }
                }

                // ---------- Mood mix
                SectionHeader("Mood mix")
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(8)) {
                    if (detail == null) Text("Loading…", style = Type.callout, color = c.secondary)
                    else {
                        MoodMix("You", me.id, detail.moodCounts, c.me)
                        Spacer(Modifier.height(16.dp))
                        MoodMix(partner?.displayName?.substringBefore(' ') ?: "Partner", partner?.id ?: "", detail.moodCounts, c.partner)
                    }
                }

                // ---------- Things we did (date types)
                SectionHeader("Things we did together")
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(9)) {
                    val types = detail?.dateTypeCounts.orEmpty().mapNotNull { x -> DateType.from(x.dateType)?.let { it to x.n } }.sortedByDescending { it.second }.take(8)
                    if (detail == null) Text("Loading…", style = Type.callout, color = c.secondary)
                    else if (types.isEmpty()) Text("Tag diary notes and calendar events with what you did — date night, movie night, gaming session — and they'll be counted here.", style = Type.callout, color = c.secondary)
                    else {
                        val max = types.first().second.toFloat()
                        types.forEachIndexed { i, (t, n) ->
                            BarRow("${t.emoji} ${t.label}", n, n / max, c.accent, labelWidth = 150.dp)
                            if (i < types.lastIndex) Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                // ---------- Favourite activities
                SectionHeader("Favourite activities")
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(9)) {
                    val top = detail?.activityCounts.orEmpty().groupBy { it.activity }
                        .map { (k, v) -> k to v.sumOf { it.n } }.sortedByDescending { it.second }.take(5)
                    if (detail == null) Text("Loading…", style = Type.callout, color = c.secondary)
                    else if (top.isEmpty()) Text("Share what you're up to with your mood and your favourites will show up here.", style = Type.callout, color = c.secondary)
                    else {
                        val max = top.first().second.toFloat()
                        top.forEachIndexed { i, (key, n) ->
                            val a = Activity.from(key)
                            BarRow(a?.label ?: key, n, n / max, a?.tint ?: c.accent)
                            if (i < top.lastIndex) Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MilestoneBar(days: Long) {
    val c = T.colors
    val milestones = listOf(50L, 100, 200, 300, 365, 500, 730, 1000, 1500, 2000, 2500, 3000, 3650, 5000, 7300, 10000)
    val next = milestones.firstOrNull { it > days } ?: return
    val prev = milestones.lastOrNull { it <= days } ?: 0L
    val frac = ((days - prev).toFloat() / (next - prev)).coerceIn(0f, 1f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(frac) { anim.animateTo(frac, tween(1100, easing = FastOutSlowInEasing)) }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.label.copy(alpha = .10f))) {
        Box(Modifier.fillMaxWidth(anim.value).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(c.accent, c.me))))
    }
    Spacer(Modifier.height(6.dp))
    Text("${next - days} ${if (next - days == 1L) "day" else "days"} to $next", style = Type.caption, color = c.secondary)
}

@Composable
private fun Metric(modifier: Modifier, icon: ImageVector, label: String, value: Long, tint: Color) {
    val c = T.colors
    SoftCard(modifier, padding = 12.dp) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        CountUp(value, Type.title, c.label)
        Text(label, style = Type.caption, color = c.secondary, maxLines = 1)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Type.caption, color = T.colors.label)
    }
}

/** 30 slim bars: yours (left of each pair) and your partner's (right), today at the right edge. */
@Composable
private fun DailyBars(daily: List<DailyCount>, meId: String) {
    val c = T.colors
    val today = LocalDate.now()
    val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
    val byDay = daily.groupBy { it.day }
    val mine = days.map { d -> byDay[d.toString()]?.filter { it.userId == meId }?.sumOf { it.n } ?: 0 }
    val theirs = days.map { d -> byDay[d.toString()]?.filter { it.userId != meId }?.sumOf { it.n } ?: 0 }
    val max = maxOf(1, (mine + theirs).max())
    val grow = remember { Animatable(0f) }
    LaunchedEffect(daily) { grow.snapTo(0f); grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val grid = c.label.copy(alpha = .08f)
    Canvas(Modifier.fillMaxWidth().height(110.dp)) {
        val slot = size.width / days.size
        val barW = slot * 0.36f
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 2f)
        drawLine(grid, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f)
        days.indices.forEach { i ->
            val x = i * slot + slot * 0.12f
            val h1 = size.height * (mine[i].toFloat() / max) * grow.value
            val h2 = size.height * (theirs[i].toFloat() / max) * grow.value
            if (h1 > 0f) drawRoundRect(c.me, Offset(x, size.height - h1), Size(barW, h1), CornerRadius(barW / 2))
            if (h2 > 0f) drawRoundRect(c.partner, Offset(x + barW + 1f, size.height - h2), Size(barW, h2), CornerRadius(barW / 2))
        }
    }
}

@Composable
private fun MoodMix(name: String, userId: String, counts: List<MoodCount>, accent: Color) {
    val c = T.colors
    val mine = counts.filter { it.userId == userId }
    val total = mine.sumOf { it.n }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(name, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.weight(1f))
        Text("$total ${if (total == 1) "update" else "updates"}", style = Type.caption, color = c.secondary)
    }
    Spacer(Modifier.height(8.dp))
    if (total == 0) {
        Text("Nothing shared yet", style = Type.footnote, color = c.secondary)
        return
    }
    val sorted = Mood.entries.map { m -> m to (mine.firstOrNull { it.mood == m.key }?.n ?: 0) }.filter { it.second > 0 }.sortedByDescending { it.second }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(total) { grow.snapTo(0f); grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
        sorted.forEach { (m, n) ->
            Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(m.tint.copy(alpha = .35f + .65f * grow.value)))
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        sorted.take(3).forEach { (m, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(m.tint))
                Spacer(Modifier.width(5.dp))
                Text("${m.label} ${(n * 100f / total).toInt()}%", style = Type.caption, color = c.label)
            }
        }
    }
}

@Composable
private fun BarRow(label: String, n: Int, frac: Float, tint: Color, labelWidth: androidx.compose.ui.unit.Dp = 96.dp) {
    val c = T.colors
    val grow = remember { Animatable(0f) }
    LaunchedEffect(frac) { grow.animateTo(frac, tween(900, easing = FastOutSlowInEasing)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.footnote, color = c.label, modifier = Modifier.width(labelWidth), maxLines = 1)
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.label.copy(alpha = .08f))) {
            Box(Modifier.fillMaxWidth(grow.value.coerceAtLeast(0.02f)).fillMaxHeight().clip(RoundedCornerShape(5.dp)).background(tint))
        }
        Spacer(Modifier.width(10.dp))
        Text("$n", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.width(28.dp))
    }
}
