package app.tandem.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.data.Meter
import app.tandem.data.User
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlin.math.abs
import kotlin.math.sin

private val UP_GREEN = Color(0xFF6FD39A)
private val DOWN_ROSE = Color(0xFFFF8FA3)
private val FULL_GOLD = Color(0xFFFFD58A)

/** The change since last time: a small ▲ / ▼ pill that flashes green (up) or rose (down) a few times when it appears. */
@Composable
fun DeltaBadge(delta: Int, flashKey: Any, modifier: Modifier = Modifier) {
    if (delta == 0) return
    val tone = if (delta > 0) UP_GREEN else DOWN_ROSE
    val flash = remember(flashKey, delta) { Animatable(0f) }
    LaunchedEffect(flashKey, delta) {
        repeat(3) { flash.animateTo(1f, tween(260)); flash.animateTo(0f, tween(420)) }
    }
    Text(
        (if (delta > 0) "▲ " else "▼ ") + abs(delta),
        style = Type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, fontFeatureSettings = "tnum"),
        color = lerp(tone, Color.White, flash.value * 0.9f),
        modifier = modifier.clip(RoundedCornerShape(50)).background(tone.copy(alpha = .12f + .5f * flash.value)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Change of [key] since the last time that person changed their meters, or 0 when there is nothing to compare. */
fun meterDelta(user: User, key: String): Int {
    val now = user.profile.meters[key] ?: return 0
    val before = user.profile.prevMeters[key] ?: return 0
    return now - before
}

/** At full capacity: the meter sat at 100 for two updates in a row (now, and last time too). */
fun meterMaxed(user: User, key: String): Boolean = user.profile.meters[key] == 100 && user.profile.prevMeters[key] == 100

/** "Full" next to a value at full capacity: a small gold mark with a spark. */
@Composable
fun FullMark(modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(FULL_GOLD.copy(alpha = .16f)).padding(start = 5.dp, end = 7.dp, top = 1.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawPath(spark(center, size.minDimension / 2), FULL_GOLD) }
        Spacer(Modifier.width(3.dp))
        Text("Full", style = Type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp), color = FULL_GOLD)
    }
}

private fun spark(c: Offset, r: Float) = Path().apply {
    moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r)
    quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r); close()
}

/**
 * One meter as a bar of light: a quiet track, a fill in the meter's colour that brightens toward its end, and a lit
 * cap where it stops. [fromEnd] grows it from the right (your partner's side of a duel). At full capacity the
 * bar overflows: light sweeps along it, the cap pulses, and a few sparks rise off the end.
 */
@Composable
fun MeterTrack(value: Int?, tint: Color, maxed: Boolean, modifier: Modifier = Modifier, fromEnd: Boolean = false, height: Dp = 12.dp) {
    val f by animateFloatAsState(((value ?: 0) / 100f).coerceIn(0f, 1f), tween(900), label = "meter")
    val t = if (maxed) {
        val tr = rememberInfiniteTransition(label = "full")
        val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "sweep")
        v
    } else 0f
    Canvas(modifier.fillMaxWidth().height(height + if (maxed) 10.dp else 0.dp)) {
        val h = height.toPx()
        val top = size.height - h
        val r = CornerRadius(h / 2)
        drawRoundRect(tint.copy(alpha = .14f), Offset(0f, top), Size(size.width, h), r)
        if (value == null) return@Canvas
        val w = (size.width * f).coerceAtLeast(h)
        val x0 = if (fromEnd) size.width - w else 0f
        val brush = if (fromEnd) Brush.horizontalGradient(listOf(lerp(tint, Color.White, .35f), tint.copy(alpha = .7f)), startX = x0, endX = size.width)
        else Brush.horizontalGradient(listOf(tint.copy(alpha = .7f), lerp(tint, Color.White, .35f)), startX = 0f, endX = w)
        drawRoundRect(brush, Offset(x0, top), Size(w, h), r)
        val cap = Offset(if (fromEnd) x0 + h / 2 else w - h / 2, top + h / 2)
        val pulse = if (maxed) .5f + .5f * sin(t * 6.283f * 2) else 0f
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .55f + .35f * pulse), Color.Transparent), center = cap, radius = h * (1.6f + 1.2f * pulse)), h * (1.6f + 1.2f * pulse), cap)
        drawCircle(Color.White.copy(alpha = .9f), h * .22f, cap)
        if (maxed) {
            // a band of light running along the full bar
            clipRect(x0, top, x0 + w, top + h) {
                val sx = x0 + (if (fromEnd) 1f - t else t) * (w + h * 6) - h * 3
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .55f), Color.Transparent), startX = sx - h * 3, endX = sx + h * 3), Offset(x0, top), Size(w, h))
            }
            sparks(cap, h, t, tint)
        }
    }
}

private fun DrawScope.sparks(cap: Offset, h: Float, t: Float, tint: Color) {
    for (i in 0 until 4) {
        val k = (t + i / 4f) % 1f
        val p = cap + Offset(sin((i * 2.1f + k * 3f)) * h * .6f, -k * h * 2.2f)
        val a = (1f - k) * .9f
        drawCircle(Brush.radialGradient(listOf(lerp(tint, FULL_GOLD, .5f).copy(alpha = a * .6f), Color.Transparent), center = p, radius = h * .8f), h * .8f, p)
        drawPath(spark(p, h * (.28f + .12f * (1f - k))), Color.White.copy(alpha = a))
    }
}

/**
 * The playful meters of both of you, head to head: yours grows to the left of the middle, your partner's to the
 * right, each with how it reads ("Snack time") and the change since last time.
 */
@Composable
fun MetersVs(me: User, partner: User, modifier: Modifier = Modifier) {
    val meters = Meter.entries.filter { it.key in me.profile.meters || it.key in partner.profile.meters }
    if (meters.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        meters.forEach { m -> MeterDuel(m, me, partner) }
    }
}

@Composable
private fun MeterDuel(m: Meter, me: User, partner: User) {
    val c = T.colors
    val mine = me.profile.meters[m.key]
    val theirs = partner.profile.meters[m.key]
    val myFull = meterMaxed(me, m.key)
    val theirFull = meterMaxed(partner, m.key)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(mine?.toString() ?: "–", style = Type.title.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"), color = if (myFull) FULL_GOLD else c.label)
                Spacer(Modifier.width(6.dp))
                if (myFull) FullMark() else DeltaBadge(meterDelta(me, m.key), flashKey = "me-${m.key}-${me.profile.metersAt}")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(8.dp)) { drawCircle(m.tint) }
                Spacer(Modifier.width(6.dp))
                Text(m.label, style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.secondary, maxLines = 1)
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                if (theirFull) FullMark() else DeltaBadge(meterDelta(partner, m.key), flashKey = "p-${m.key}-${partner.profile.metersAt}")
                Spacer(Modifier.width(6.dp))
                Text(theirs?.toString() ?: "–", style = Type.title.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"), color = if (theirFull) FULL_GOLD else c.label)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            MeterTrack(mine, lerp(m.tint, c.me, .35f), myFull, Modifier.weight(1f), fromEnd = true)
            MeterTrack(theirs, lerp(m.tint, c.partner, .35f), theirFull, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(mine?.let { m.caption(it) } ?: "", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.weight(1f), maxLines = 1)
            Text(theirs?.let { m.caption(it) } ?: "", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
        }
    }
}
