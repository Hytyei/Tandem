package app.tandem.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.data.User
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/*
 * Shared pieces for the stats, achievements and goals pages. One visual language: quiet glass cards,
 * ivory for "the thing that matters", your colour and your partner's colour only where they mean you two,
 * gold only for something earned.
 */

val LitIvory = Color(0xFFF6F2FF)
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** 0 → [target] once, when it first appears (and again when [target] changes). */
@Composable
fun grow(target: Float, ms: Int = 900, delayMs: Int = 0): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { kotlinx.coroutines.delay(delayMs.toLong()); a.animateTo(target, tween(ms, easing = EaseOut)) }
    return a.value
}

/** A glass card with an optional title and a line under it. */
@Composable
fun GlassCard(modifier: Modifier = Modifier, title: String? = null, sub: String? = null, trailing: (@Composable () -> Unit)? = null, padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    val c = T.colors
    val shape = RoundedCornerShape(24.dp)
    Column(modifier.clip(shape).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), shape).padding(padding)) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = Type.headline, color = c.label)
                    if (sub != null) Text(sub, style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 2.dp))
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(16.dp))
        }
        content()
    }
}

/** A figure: a number set large in the serif, a quiet label under it. */
@Composable
fun Figure(value: String, label: String, modifier: Modifier = Modifier, color: Color = T.colors.label, align: Alignment.Horizontal = Alignment.Start) {
    val c = T.colors
    Column(modifier, horizontalAlignment = align) {
        Text(value, style = Type.title.copy(fontSize = 26.sp, lineHeight = 30.sp, fontFeatureSettings = "tnum"), color = color, maxLines = 1)
        Text(label, style = Type.footnote, color = c.secondary, maxLines = 1)
    }
}

/** A ranked list: label, a thin bar, the count. The first row leads in ivory, the rest sit back. */
@Composable
fun RankedBars(rows: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val c = T.colors
    val max = (rows.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1).toFloat()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEachIndexed { i, (label, n) ->
            val g = grow(n / max, delayMs = i * 40)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = Type.callout, color = if (i == 0) c.label else c.secondary, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("$n", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.secondary)
                }
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .07f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(g.coerceAtLeast(.015f)).clip(RoundedCornerShape(2.dp)).background(if (i == 0) LitIvory else LitIvory.copy(alpha = .45f)))
                }
            }
        }
    }
}

/** One bar shared between you two: your part from the left in your colour, theirs from the right, draws in between. */
@Composable
fun SplitBar(mine: Int, theirs: Int, draws: Int, mineColor: Color, theirColor: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val total = (mine + theirs + draws).coerceAtLeast(1).toFloat()
    val g = grow(1f)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color.White.copy(alpha = .07f), cornerRadius = r)
        if (mine + theirs + draws == 0) return@Canvas
        val gap = 3.dp.toPx()
        val a = size.width * mine / total * g; val b = size.width * theirs / total * g
        if (a > 0f) drawRoundRect(mineColor, Offset.Zero, Size((a - if (theirs + draws > 0) gap / 2 else 0f).coerceAtLeast(size.height), size.height), r)
        if (b > 0f) drawRoundRect(theirColor, Offset(size.width - b + if (mine + draws > 0) gap / 2 else 0f, 0f), Size((b - if (mine + draws > 0) gap / 2 else 0f).coerceAtLeast(size.height), size.height), r)
    }
}

/**
 * The two of you facing each other: avatars and names on the outside, a big number each, a line in the middle,
 * and one bar that shows the split. The one ahead is lit; the other sits back a little.
 */
@Composable
fun VersusHero(me: User, partner: User, mine: Int, theirs: Int, draws: Int, middle: String, modifier: Modifier = Modifier, footer: String? = null) {
    val c = T.colors
    val pName = partner.displayName.substringBefore(' ')
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(me, 34.dp, ring = c.me)
            Spacer(Modifier.width(10.dp))
            Text("You", style = Type.callout, color = c.secondary, modifier = Modifier.weight(1f))
            Text(pName, style = Type.callout, color = c.secondary, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Avatar(partner, 34.dp, ring = c.partner)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$mine", style = Type.numberHuge.copy(fontFeatureSettings = "tnum"), color = if (mine >= theirs) c.label else c.secondary)
            Text(middle, style = Type.footnote, color = c.tertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("$theirs", style = Type.numberHuge.copy(fontFeatureSettings = "tnum"), color = if (theirs >= mine) c.label else c.secondary)
        }
        SplitBar(mine, theirs, draws, c.me, c.partner, Modifier.padding(top = 10.dp))
        if (footer != null) Text(footer, style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 12.dp))
    }
}

/**
 * A race to [target]: one lane each, a marker with your face on it moving toward the finish. The leader's
 * lane is lit; a finished race shows the winner at the line.
 */
@Composable
fun RaceTrack(me: User, partner: User, mine: Int, theirs: Int, target: Int, modifier: Modifier = Modifier, format: (Int) -> String = { "$it" }) {
    val c = T.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(Triple(me, mine, c.me), Triple(partner, theirs, c.partner)).forEachIndexed { i, (u, v, col) ->
            val frac = (v.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
            val g = grow(frac, 1000, i * 80)
            val lead = v > 0 && v >= (if (i == 0) theirs else mine)
            BoxWithConstraints(Modifier.fillMaxWidth().height(34.dp)) {
                val track = maxWidth - 34.dp - 52.dp
                Canvas(Modifier.fillMaxSize()) {
                    val y = size.height / 2; val x0 = 17.dp.toPx(); val x1 = x0 + track.toPx()
                    drawLine(Color.White.copy(alpha = .08f), Offset(x0, y), Offset(x1, y), 4.dp.toPx(), StrokeCap.Round)
                    drawLine(col.copy(alpha = if (lead) 1f else .55f), Offset(x0, y), Offset(x0 + (x1 - x0) * g, y), 4.dp.toPx(), StrokeCap.Round)
                    // the finish: a short dashed post
                    drawLine(LitIvory.copy(alpha = .5f), Offset(x1 + 6.dp.toPx(), y - 11.dp.toPx()), Offset(x1 + 6.dp.toPx(), y + 11.dp.toPx()), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                }
                Box(Modifier.offset(x = track * g).align(Alignment.CenterStart)) { Avatar(u, 34.dp, ring = col) }
                Text(
                    "${format(v)}/${format(target)}", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = if (lead) c.label else c.secondary,
                    textAlign = TextAlign.End, modifier = Modifier.align(Alignment.CenterEnd).width(52.dp), maxLines = 1,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ achievements

/** A medal: earned ones glow warm gold; the rest are an outline with how far you've come around the edge. */
@Composable
fun Medal(icon: ImageVector, unlocked: Boolean, progress: Float, modifier: Modifier = Modifier, size: Dp = 60.dp) {
    val c = T.colors
    val gold = c.special
    val p = grow(progress.coerceIn(0f, 1f))
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2
            if (unlocked) {
                drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = .35f), Color.Transparent), center = center, radius = r * 1.25f), r * 1.25f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1CF), gold, Color(0xFFB9813A)), center = center + Offset(-r * .3f, -r * .35f), radius = r * 1.5f), r * .86f)
                drawCircle(Color.White.copy(alpha = .55f), r * .86f, style = Stroke(1.dp.toPx()))
            } else {
                drawCircle(Color.White.copy(alpha = .05f), r * .86f)
                drawCircle(Color.White.copy(alpha = .10f), r * .86f, style = Stroke(1.5.dp.toPx()))
                if (p > 0f) drawArc(LitIvory.copy(alpha = .8f), -90f, 360f * p, false, Offset(r * .14f, r * .14f), Size(r * 1.72f, r * 1.72f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
            }
        }
        Icon(icon, null, tint = if (unlocked) Color(0xFF5A3A12) else c.tertiary, modifier = Modifier.size(size * .4f))
    }
}

/** One star in the achievements sky. */
class SkyStar(val group: Int, val lit: Boolean)

/**
 * The achievements as a night sky: each group a small constellation in its own patch of sky, stars lit
 * where you've earned them and joined by a line. It twinkles slowly (still when animations are off).
 */
@Composable
fun AchievementSky(groups: List<Pair<String, List<Boolean>>>, modifier: Modifier = Modifier, height: Dp = (130 * ((groups.size + 2) / 3)).dp, still: Boolean = false) {
    val c = T.colors
    val gold = c.special
    val tw = rememberInfiniteTransition(label = "sky")
    val t by tw.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "twinkle")
    val layout = remember(groups.map { it.second.size }) {
        val rnd = Random(7)
        val rows = (groups.size + 2) / 3
        groups.mapIndexed { gi, (_, stars) ->
            val col = gi % 3; val row = gi / 3
            List(stars.size) { k ->
                // a gentle wandering path through the group's patch
                val u = (k + .5f) / stars.size
                Offset(col / 3f + .03f + .26f * u + rnd.nextFloat() * .03f, (row + .14f + .6f * (.5f + .5f * sin(u * 4.2f + gi * 1.7f)) + rnd.nextFloat() * .14f) / rows)
            }
        }
    }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val phase = if (still) 0f else t
            // faint far stars, so the patches between constellations aren't empty
            val far = Random(11)
            repeat(70) { drawCircle(Color.White.copy(alpha = .06f + far.nextFloat() * .12f), (.6f + far.nextFloat()).dp.toPx(), Offset(far.nextFloat() * size.width, far.nextFloat() * size.height)) }
            groups.forEachIndexed { gi, (_, stars) ->
                val pts = layout[gi].map { Offset(it.x * size.width, it.y * size.height) }
                for (k in 1 until pts.size) {
                    val both = stars[k] && stars[k - 1]
                    drawLine(if (both) gold.copy(alpha = .55f) else Color.White.copy(alpha = .16f), pts[k - 1], pts[k], if (both) 1.4.dp.toPx() else 1.dp.toPx(),
                        pathEffect = if (both) null else PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())))
                }
                pts.forEachIndexed { k, p ->
                    if (stars[k]) {
                        val tw2 = .8f + .2f * sin((phase + k * .13f + gi * .31f) * 2f * PI.toFloat())
                        drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = .45f * tw2), Color.Transparent), center = p, radius = 12.dp.toPx()), 12.dp.toPx(), p)
                        star(p, 4.5.dp.toPx() * tw2, Color(0xFFFFF4DA))
                    } else { drawCircle(Color.White.copy(alpha = .38f), 2.2.dp.toPx(), p); drawCircle(Color.White.copy(alpha = .08f), 5.dp.toPx(), p) }
                }
            }
        }
        groups.chunked(3).forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth().padding(top = if (i > 0) 4.dp else 0.dp)) {
                row.forEach { (name, s) -> SkyLabel(name, s, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SkyLabel(name: String, stars: List<Boolean>, modifier: Modifier) {
    val c = T.colors
    Column(modifier.padding(horizontal = 4.dp)) {
        Text(name, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, maxLines = 1)
        Text("${stars.count { it }} of ${stars.size}", style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace), color = c.tertiary)
    }
}

private fun DrawScope.star(p: Offset, r: Float, col: Color) {
    val path = Path().apply {
        moveTo(p.x, p.y - r); quadraticTo(p.x, p.y, p.x + r, p.y); quadraticTo(p.x, p.y, p.x, p.y + r)
        quadraticTo(p.x, p.y, p.x - r, p.y); quadraticTo(p.x, p.y, p.x, p.y - r); close()
    }
    drawPath(path, col)
    drawCircle(Color.White, r * .28f, p)
}

/** A round glass back button, the same on every page. */
@Composable
fun BackDisc(onBack: () -> Unit) {
    val c = T.colors
    Box(Modifier.size(42.dp).clip(CircleShape).background(c.card).pressable(haptic = false, onClick = onBack), contentAlignment = Alignment.Center) {
        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.label, modifier = Modifier.size(22.dp))
    }
}
