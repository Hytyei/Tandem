package app.tandem.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private sealed interface TlRow {
    data class Header(val date: LocalDate, val both: Boolean) : TlRow
    data class Item(val entry: MoodEntry, val last: Boolean) : TlRow
}

private val Mono = FontFamily.Monospace
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private const val SPAN_DAYS = 14

private fun MoodEntry.day(): LocalDate = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dayTitle(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
}

private fun daySub(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern(if (d.year == LocalDate.now().year) "MMMM" else "MMMM yyyy"))

/**
 * Moods as two orbits: a 14-day chart where each of you is a lane of coloured moons (the colour of that day's mood),
 * then the days themselves, newest first. Tapping a day in the chart jumps to it.
 */
@Composable
fun TimelineScreen(vm: AppViewModel, switcher: @Composable () -> Unit = {}) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val meId = data.me.id
    val partner = data.couple.partner
    val pName = partner?.displayName?.substringBefore(' ') ?: "Partner"
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 both, 1 me, 2 partner
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val rows = remember(data.moods, filter, meId) {
        val list = data.moods.filter { filter == 0 || (filter == 1 && it.userId == meId) || (filter == 2 && it.userId != meId) }
        val byDay = list.groupBy { it.day() }
        val out = mutableListOf<TlRow>()
        for ((d, entries) in byDay) {
            val all = data.moods.filter { it.day() == d }
            out += TlRow.Header(d, all.any { it.userId == meId } && all.any { it.userId != meId })
            entries.forEachIndexed { i, e -> out += TlRow.Item(e, i == entries.lastIndex) }
        }
        out
    }
    // header rows are offset by the fixed items above the feed (title, switcher, chart, filter)
    val leading = 4
    fun jumpTo(d: LocalDate) {
        val i = rows.indexOfFirst { it is TlRow.Header && it.date == d }
        if (i >= 0) scope.launch { listState.animateScrollToItem(i + leading) }
        else vm.toast("No moods on ${d.format(DateTimeFormatter.ofPattern("d MMMM"))}")
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 130.dp)) {
        item { LargeTitle("Moods", subtitle = "You & $pName") }
        item { switcher() }
        item { OrbitChart(data.moods, meId, data.me, partner, pName, onDay = ::jumpTo) }
        item {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                Segmented(listOf("Both", "Me", pName), filter, { filter = it })
            }
        }
        if (rows.isEmpty()) {
            item { EmptyState("Nothing here yet", "Share your first mood from the Today tab") }
        }
        items(rows, key = { r -> when (r) { is TlRow.Header -> "h-${r.date}"; is TlRow.Item -> r.entry.id } }) { r ->
            when (r) {
                is TlRow.Header -> DayHeader(r.date, r.both)
                is TlRow.Item -> MoodRow(
                    r.entry, mine = r.entry.userId == meId, last = r.last, meId = meId,
                    who = if (r.entry.userId == meId) data.me else partner,
                    onReact = { emoji -> vm.react("mood", r.entry.id, emoji) },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ the 14-day orbit chart

/** What one lane shows for one day: the last mood's colour and how many moods there were. */
private class DayMark(val tint: Color, val count: Int)

@Composable
private fun OrbitChart(moods: List<MoodEntry>, meId: String, me: User, partner: User?, pName: String, onDay: (LocalDate) -> Unit) {
    val c = T.colors
    val today = remember { LocalDate.now() }
    val days = remember(today) { List(SPAN_DAYS) { today.minusDays((SPAN_DAYS - 1 - it).toLong()) } }
    val (mine, theirs) = remember(moods, meId, today) {
        val inSpan = moods.filter { !it.day().isBefore(days.first()) }
        fun lane(own: Boolean): Map<LocalDate, DayMark> = inSpan.filter { (it.userId == meId) == own }.groupBy { it.day() }
            .mapValues { (_, l) -> DayMark(l.maxBy { it.createdAt }.tint(), l.size) }
        lane(true) to lane(false)
    }
    val shared = days.count { it in mine && it in theirs }
    // the moons rise in once, left to right
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(700, easing = EaseOut)) }
    val laneColor = c.label.copy(alpha = .10f)
    val todayFill = c.label.copy(alpha = .06f)

    SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), padding = 20.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Last two weeks", style = Type.footnote, color = c.secondary)
                Text(
                    when (shared) {
                        0 -> "No shared days yet"
                        SPAN_DAYS -> "You both checked in every day"
                        else -> "Both of you on $shared of $SPAN_DAYS days"
                    },
                    style = Type.headline, color = c.label,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(32.dp).height(112.dp), verticalArrangement = Arrangement.SpaceAround) {
                Avatar(me, 24.dp, ring = c.me)
                Avatar(partner, 24.dp, ring = c.partner)
            }
            Spacer(Modifier.width(4.dp))
            Canvas(
                Modifier.weight(1f).height(112.dp).pointerInput(days) {
                    detectTapGestures { o ->
                        val col = (o.x / (size.width / SPAN_DAYS.toFloat())).toInt().coerceIn(0, SPAN_DAYS - 1)
                        onDay(days[col])
                    }
                },
            ) {
                val colW = size.width / SPAN_DAYS
                val yMe = size.height * .26f
                val yThem = size.height * .74f
                val r = (colW * .34f).coerceAtMost(11.dp.toPx())
                // today's column sits on a slightly raised strip
                drawRoundRect(todayFill, Offset(colW * (SPAN_DAYS - 1), 0f), androidx.compose.ui.geometry.Size(colW, size.height), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                val dash = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
                drawLine(laneColor, Offset(0f, yMe), Offset(size.width, yMe), 1.dp.toPx(), pathEffect = dash)
                drawLine(laneColor, Offset(0f, yThem), Offset(size.width, yThem), 1.dp.toPx(), pathEffect = dash)
                days.forEachIndexed { i, d ->
                    val p = ((rise.value * (SPAN_DAYS + 4) - i) / 4f).coerceIn(0f, 1f)
                    if (p <= 0f) return@forEachIndexed
                    val x = colW * (i + .5f)
                    val a = mine[d]; val b = theirs[d]
                    if (a != null && b != null) {
                        // a tether on days you both shared
                        drawLine(Brush.verticalGradient(listOf(c.me.copy(alpha = .55f * p), c.partner.copy(alpha = .55f * p)), yMe, yThem), Offset(x, yMe + r), Offset(x, yThem - r), 1.5.dp.toPx())
                    }
                    fun moon(m: DayMark?, y: Float, ownColor: Color) {
                        val lift = (1f - p) * 10.dp.toPx()
                        if (m == null) { drawCircle(ownColor.copy(alpha = .22f * p), 2.dp.toPx(), Offset(x, y)); return }
                        val rr = r * (0.82f + 0.09f * (m.count - 1).coerceAtMost(2)) * (0.6f + 0.4f * p)
                        val tint = lerp(m.tint, Color.White, .18f)
                        val o = Offset(x, y + lift)
                        drawCircle(tint.copy(alpha = p), rr, o)
                        // a crescent of shade gives each moon a little volume
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .38f * p), Color.Transparent), center = o + Offset(-rr * .35f, -rr * .35f), radius = rr * 1.1f), rr, o)
                        drawCircle(ownColor.copy(alpha = .9f * p), rr + 1.5.dp.toPx(), o, style = Stroke(1.5.dp.toPx()))
                    }
                    moon(a, yMe, c.me)
                    moon(b, yThem, c.partner)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.padding(start = 36.dp)) {
            days.forEachIndexed { i, d ->
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = Type.caption.copy(fontFamily = Mono, fontSize = 10.sp, fontWeight = if (i == SPAN_DAYS - 1) FontWeight.SemiBold else FontWeight.Normal),
                    color = if (i == SPAN_DAYS - 1) c.label else c.tertiary,
                    modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Each moon is that day's last mood", style = Type.caption, color = c.tertiary, modifier = Modifier.weight(1f))
            Text("$pName below", style = Type.caption, color = c.tertiary)
        }
    }
}

// ------------------------------------------------------------------ the feed

@Composable
private fun DayHeader(d: LocalDate, both: Boolean) {
    val c = T.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "%02d".format(d.dayOfMonth),
            style = Type.numberHuge.copy(fontSize = 34.sp, lineHeight = 36.sp, fontFeatureSettings = "tnum"),
            color = c.label, modifier = Modifier.width(52.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(dayTitle(d), style = Type.headline, color = c.label)
            Text(daySub(d), style = Type.footnote, color = c.secondary)
        }
        if (both) BinaryStar()
    }
}

/** The signature: two little stars circling each other, shown on days you both shared a mood. */
@Composable
private fun BinaryStar() {
    val c = T.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 18.dp, height = 12.dp)) {
            val r = 3.5.dp.toPx()
            drawCircle(c.me, r, Offset(size.width * .32f, size.height / 2))
            drawCircle(c.partner, r, Offset(size.width * .68f, size.height / 2))
        }
        Spacer(Modifier.width(6.dp))
        Text("Both", style = Type.caption, color = c.secondary)
    }
}

/** Mood/activity tints are mid-tones: lighten them so they stay readable on the dark cards. */
private fun readable(tint: Color): Color = lerp(tint, Color.White, .55f)

@Composable
private fun MoodRow(e: MoodEntry, mine: Boolean, last: Boolean, meId: String, who: User?, onReact: (String?) -> Unit) {
    val c = T.colors
    val own = if (mine) c.me else c.partner
    val time = remember(e.createdAt) { Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(IntrinsicSize.Min)) {
        // time gutter with a thread in the author's colour
        Column(Modifier.width(52.dp).fillMaxHeight()) {
            Text(time, style = Type.caption.copy(fontFamily = Mono, fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 16.dp))
        }
        Box(Modifier.width(16.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!last) Box(Modifier.padding(top = 22.dp).fillMaxHeight().width(1.dp).background(c.label.copy(alpha = .10f)))
            Box(Modifier.padding(top = 18.dp).size(9.dp).clip(CircleShape).background(own))
        }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.weight(1f).padding(bottom = 12.dp).clip(RoundedCornerShape(20.dp)).background(c.card)
                .border(1.dp, c.label.copy(alpha = .08f), RoundedCornerShape(20.dp)).animateContentSize().padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(e.artRes(), 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.headline(), style = Type.callout.copy(fontWeight = FontWeight.Medium), color = readable(e.tint()), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (mine) "You" else who?.displayName?.substringBefore(' ') ?: "Partner", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary)
                }
                Avatar(who, 24.dp, ring = own)
            }
            if (e.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(e.note, style = Type.callout, color = c.label.copy(alpha = .9f))
            }
            if (e.reactions.isNotEmpty() || !mine) {
                Spacer(Modifier.height(8.dp))
                ReactionBar(e.reactions, meId, canReact = !mine, onReact = onReact)
            }
        }
    }
}
