package app.tandem.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.data.Meter
import app.tandem.data.User
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlin.math.abs

private val UP_GREEN = Color(0xFF22C55E)
private val DOWN_RED = Color(0xFFEF4444)

/** The change since last time: a small ▲ / ▼ pill that flashes green (up) or red (down) a few times when it appears. */
@Composable
fun DeltaBadge(delta: Int, flashKey: Any, modifier: Modifier = Modifier) {
    if (delta == 0) return
    val tone = if (delta > 0) UP_GREEN else DOWN_RED
    val flash = remember(flashKey, delta) { Animatable(0f) }
    LaunchedEffect(flashKey, delta) {
        repeat(3) { flash.animateTo(1f, tween(260)); flash.animateTo(0f, tween(420)) }
    }
    Text(
        (if (delta > 0) "▲ +" else "▼ −") + abs(delta),
        style = Type.caption.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
        color = lerp(tone, Color.White, flash.value * 0.9f),
        modifier = modifier.clip(RoundedCornerShape(50)).background(tone.copy(alpha = .14f + .62f * flash.value)).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Change of [key] since the last time that person changed their meters, or 0 when there is nothing to compare. */
fun meterDelta(user: User, key: String): Int {
    val now = user.profile.meters[key] ?: return 0
    val before = user.profile.prevMeters[key] ?: return 0
    return now - before
}

/** The playful meters of both of you, head to head: yours grows to the left, your partner's to the right, with ▲/▼ since last time. */
@Composable
fun MetersVs(me: User, partner: User, modifier: Modifier = Modifier) {
    val c = T.colors
    val meters = Meter.entries.filter { it.key in me.profile.meters || it.key in partner.profile.meters }
    if (meters.isEmpty()) return
    val pName = partner.displayName.substringBefore(' ')
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Avatar(me, 22.dp, ring = c.me); Spacer(Modifier.width(6.dp))
                Text("You", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.me)
            }
            Text("VS", style = Type.caption.copy(fontWeight = FontWeight.Black), color = c.tertiary)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(pName, style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.partner, maxLines = 1)
                Spacer(Modifier.width(6.dp)); Avatar(partner, 22.dp, ring = c.partner)
            }
        }
        meters.forEach { m -> MeterVsRow(m, me, partner) }
    }
}

@Composable
private fun MeterVsRow(m: Meter, me: User, partner: User) {
    val c = T.colors
    val mine = me.profile.meters[m.key]
    val theirs = partner.profile.meters[m.key]
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(mine?.let { "$it%" } ?: "–", style = Type.callout.copy(fontWeight = FontWeight.Bold), color = c.me)
                Spacer(Modifier.width(5.dp))
                DeltaBadge(meterDelta(me, m.key), flashKey = "me-${m.key}-${me.profile.metersAt}")
            }
            Text("${m.emoji} ${m.label.substringBefore(' ')}", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label, textAlign = TextAlign.Center, maxLines = 1)
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                DeltaBadge(meterDelta(partner, m.key), flashKey = "p-${m.key}-${partner.profile.metersAt}")
                Spacer(Modifier.width(5.dp))
                Text(theirs?.let { "$it%" } ?: "–", style = Type.callout.copy(fontWeight = FontWeight.Bold), color = c.partner)
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            HalfBar(Modifier.weight(1f), mine, c.me, fromRight = true)
            HalfBar(Modifier.weight(1f), theirs, c.partner, fromRight = false)
        }
    }
}

@Composable
private fun HalfBar(modifier: Modifier, value: Int?, color: Color, fromRight: Boolean) {
    val c = T.colors
    val f by animateFloatAsState(((value ?: 0) / 100f).coerceIn(0f, 1f), tween(900), label = "vsBar")
    val shape = if (fromRight) RoundedCornerShape(topStart = 50, bottomStart = 50, topEnd = 4, bottomEnd = 4) else RoundedCornerShape(topEnd = 50, bottomEnd = 50, topStart = 4, bottomStart = 4)
    Box(modifier.fillMaxHeight().clip(shape).background(color.copy(alpha = .16f))) {
        if (value != null) Box(
            Modifier.fillMaxHeight().fillMaxWidth(f.coerceAtLeast(0.03f)).align(if (fromRight) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(shape).background(color),
        )
    }
}
