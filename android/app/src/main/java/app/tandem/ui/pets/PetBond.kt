package app.tandem.ui.pets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandem.data.Bond
import app.tandem.data.Pet
import app.tandem.data.stageName
import app.tandem.ui.components.SoftCard
import app.tandem.ui.screens.EaseOutStrong
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFmt = DateTimeFormatter.ofPattern("d MMM")
private fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(dayFmt)

/** Growing up: where the pet is on the way from baby to grown, and what it still needs. */
@Composable
fun GrowthCard(pet: Pet, modifier: Modifier = Modifier) {
    val c = T.colors
    val days = ((System.currentTimeMillis() - pet.born) / 86_400_000L).toInt().coerceAtLeast(0)
    val g by animateFloatAsState(pet.growth.toFloat().coerceIn(0f, 1f), tween(700, easing = EaseOutStrong), label = "growth")
    SoftCard(modifier, padding = 18.dp) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Growing up", style = Type.footnote, color = c.secondary)
                Text(stageName(pet.species, pet.stage), style = Type.title, color = c.label)
            }
            Text("${app.tandem.data.ageText(days)} old", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.tertiary)
        }
        // three stages on one line: a dot for each, the line lit as far as it has grown
        Canvas(Modifier.fillMaxWidth().height(28.dp).padding(top = 12.dp)) {
            val y = size.height / 2; val x0 = 6.dp.toPx(); val x1 = size.width - 6.dp.toPx()
            drawLine(Color.White.copy(alpha = .10f), Offset(x0, y), Offset(x1, y), 4.dp.toPx(), StrokeCap.Round)
            drawLine(c.special, Offset(x0, y), Offset(x0 + (x1 - x0) * g, y), 4.dp.toPx(), StrokeCap.Round)
            for (k in 0..2) {
                val x = x0 + (x1 - x0) * k / 2f
                val reached = g >= k / 2f - .001f
                drawCircle(if (reached) c.special else Color(0xFF2A2160), 6.dp.toPx(), Offset(x, y))
                drawCircle(if (reached) Color.White.copy(alpha = .8f) else Color.White.copy(alpha = .2f), 6.dp.toPx(), Offset(x, y), style = Stroke(1.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (k in 0..2) Text(
                stageName(pet.species, k), style = Type.caption.copy(fontWeight = FontWeight.Normal),
                color = if (pet.stage >= k) c.label else c.tertiary, modifier = Modifier.weight(1f),
                textAlign = when (k) { 0 -> androidx.compose.ui.text.style.TextAlign.Start; 1 -> androidx.compose.ui.text.style.TextAlign.Center; else -> androidx.compose.ui.text.style.TextAlign.End },
            )
        }
        val nx = pet.next
        val needXp = nx?.let { (it.xp - pet.xp).coerceAtLeast(0) } ?: 0
        val needDays = nx?.let { (it.days - days).coerceAtLeast(0) } ?: 0
        Text(
            when {
                nx == null -> "All grown up. It still loves being looked after"
                needDays > 0 && needXp > 0 -> "Grows up in about ${app.tandem.data.ageText(needDays)}, with $needXp more care points along the way"
                needDays > 0 -> "Grows up in about ${app.tandem.data.ageText(needDays)}. It's had all the care it needs for now"
                needXp > 0 -> "Old enough. $needXp more care points and it grows up"
                else -> "Growing up any moment now"
            },
            style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 10.dp),
        )
        val need = nx
        if (need != null) Text("Pets grow on a real clock, like real ones. Every feed, drink, cuddle and pat counts, with a little extra on days you both look after it", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
    }
}

/** The bond with each of you: level and how far to the next, care streak, what each of you has done, and the perks. */
@Composable
fun BondCard(pet: Pet, meId: String, themId: String, pName: String, modifier: Modifier = Modifier) {
    val c = T.colors
    SoftCard(modifier, padding = 18.dp) {
        Text("Bond", style = Type.footnote, color = c.secondary)
        BondPerson("You", pet, meId, c.me, true)
        HorizontalDivider(color = c.label.copy(alpha = .06f), modifier = Modifier.padding(vertical = 14.dp))
        BondPerson(pName, pet, themId, c.partner, false)
        Text(
            "Bonds grow fastest with a little care every day and a mix of food, water, cuddles and pats. The deeper a bond, the more slowly it fades",
            style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun BondPerson(name: String, pet: Pet, id: String, color: Color, mine: Boolean) {
    val c = T.colors
    val b = pet.bondWith(id)
    val lv = Bond.level(b)
    val prog by animateFloatAsState(Bond.progress(b), tween(600, easing = EaseOutStrong), label = "bondProg")
    val care = pet.care[id]
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // a ring of how far to the next level, the level number inside
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val st = 4.dp.toPx(); val inset = st / 2 + 1.dp.toPx()
                drawArc(Color.White.copy(alpha = .1f), 0f, 360f, false, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(st))
                drawArc(color, -90f, 360f * prog, false, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(st, cap = StrokeCap.Round))
            }
            Text("${lv + 1}", style = Type.headline, color = c.label)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (mine) "To you: ${Bond.levels[lv].second}" else "To $name: ${Bond.levels[lv].second}", style = Type.headline, color = c.label, maxLines = 1)
            Text(
                if (lv < Bond.levels.lastIndex) "${b.toInt()} · next: ${Bond.levels[lv + 1].second} at ${Bond.levels[lv + 1].first}" else "${b.toInt()} · as close as it gets",
                style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.secondary,
            )
        }
        if (care != null && care.streak >= 2) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.LocalFireDepartment, null, tint = c.special, modifier = Modifier.size(16.dp))
            Text("${care.streak}", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.special)
        }
    }
    Text(Bond.perks[lv], style = Type.footnote, color = c.label, modifier = Modifier.padding(top = 10.dp))
    if (lv < Bond.levels.lastIndex) Text("Next: ${Bond.perks[lv + 1].replaceFirstChar { it.lowercase() }}", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
    if (care != null) Text(
        "Fed ${care.feed} · water ${care.water} · cuddles ${care.cuddle} · pats ${care.pat}",
        style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace), color = c.tertiary, modifier = Modifier.padding(top = 6.dp),
    )
}

/** The pet's small history: coming home, growing up, bonds deepening, gifts. */
@Composable
fun MomentsCard(pet: Pet, meId: String, myName: String, pName: String, modifier: Modifier = Modifier) {
    val c = T.colors
    if (pet.moments.isEmpty()) return
    SoftCard(modifier, padding = 18.dp) {
        Text("Moments", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(bottom = 6.dp))
        pet.moments.take(8).forEach { m ->
            val who = m.user?.let { if (it == meId) "you" else pName }
            val text = when (m.kind) {
                "bond" -> "Now ${m.text.lowercase()} with $who"
                "gift" -> if (who == "you") "Brought you a gift" else "Brought $pName a gift"
                else -> m.text
            }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(when (m.kind) { "grow" -> c.special; "bond" -> if (m.user == meId) c.me else c.partner; "gift" -> c.accent; else -> c.secondary }))
                Spacer(Modifier.width(12.dp))
                Text(text, style = Type.callout, color = c.label, modifier = Modifier.weight(1f), maxLines = 2)
                Text(day(m.at), style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace), color = c.tertiary)
            }
        }
    }
}
