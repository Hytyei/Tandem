package app.tandem.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.pets.rememberReducedMotion
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type

/**
 * The Play page's head to head, as a tug of war: the two of you on either end of a rope, the knot pulled
 * toward whoever has won more. The win counts sit large in the middle, each of you has a row of recent form
 * underneath, and the shared Tandem score closes it quietly.
 */
@Composable
internal fun HeadToHeadCard(me: User, partner: User, stats: GameStats, matches: List<Match>, modifier: Modifier = Modifier) {
    val c = T.colors
    val pName = partner.displayName.substringBefore(' ')
    val my = stats.players.firstOrNull { it.userId == me.id } ?: PlayerStat(me.id)
    val th = stats.players.firstOrNull { it.userId == partner.id } ?: PlayerStat(partner.id)
    val total = my.wins + th.wins
    val reduced = rememberReducedMotion()

    // the knot: 0 is the middle, -1 all the way to you, 1 all the way to your partner
    val pull = if (total == 0) 0f else (th.wins - my.wins).toFloat() / total
    val knot = remember { Animatable(0f) }
    LaunchedEffect(pull) { if (reduced) knot.snapTo(pull) else knot.animateTo(pull, spring(dampingRatio = .55f, stiffness = Spring.StiffnessVeryLow)) }

    val recent = matches.filter { it.isDone }.sortedBy { it.finishedAt }.takeLast(5)
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.clip(shape).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), shape).padding(20.dp)) {
        Text("Head to head", style = Type.footnote, color = c.secondary)

        // ---------- the two of you and the score
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Side(me, "You", c.me, my.wins >= th.wins && total > 0)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${my.wins}", style = Type.numberHuge.copy(fontSize = 52.sp, lineHeight = 56.sp, fontFeatureSettings = "tnum"), color = if (my.wins >= th.wins) c.label else c.secondary)
                    Text("–", style = Type.title, color = c.tertiary, modifier = Modifier.padding(horizontal = 12.dp))
                    Text("${th.wins}", style = Type.numberHuge.copy(fontSize = 52.sp, lineHeight = 56.sp, fontFeatureSettings = "tnum"), color = if (th.wins >= my.wins) c.label else c.secondary)
                }
                Text(
                    "match wins" + if (stats.draws > 0) " · ${stats.draws} ${if (stats.draws == 1) "draw" else "draws"}" else "",
                    style = Type.footnote, color = c.tertiary,
                )
            }
            Side(partner, pName, c.partner, th.wins >= my.wins && total > 0)
        }

        // ---------- the rope
        Canvas(Modifier.fillMaxWidth().height(30.dp).padding(top = 8.dp)) {
            val y = size.height / 2
            val x0 = 26.dp.toPx(); val x1 = size.width - 26.dp.toPx()
            val mid = (x0 + x1) / 2
            val k = mid + knot.value * (x1 - x0) * .42f
            // your half and theirs, meeting at the knot
            drawLine(Brush.horizontalGradient(listOf(c.me.copy(alpha = .35f), c.me), startX = x0, endX = k), Offset(x0, y), Offset(k, y), 3.dp.toPx(), StrokeCap.Round)
            drawLine(Brush.horizontalGradient(listOf(c.partner, c.partner.copy(alpha = .35f)), startX = k, endX = x1), Offset(k, y), Offset(x1, y), 3.dp.toPx(), StrokeCap.Round)
            // the middle line it's pulled across
            drawLine(Color.White.copy(alpha = .25f), Offset(mid, y - 9.dp.toPx()), Offset(mid, y + 9.dp.toPx()), 1.dp.toPx())
            // the knot, glowing
            drawCircle(Brush.radialGradient(listOf(LitIvory.copy(alpha = .5f), Color.Transparent), center = Offset(k, y), radius = 14.dp.toPx()), 14.dp.toPx(), Offset(k, y))
            drawCircle(LitIvory, 5.dp.toPx(), Offset(k, y))
            drawCircle(Color.White.copy(alpha = .9f), 5.dp.toPx(), Offset(k, y), style = Stroke(1.dp.toPx()))
        }

        // ---------- recent form
        if (recent.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Form(recent, me.id, c.me)
                Text("last ${recent.size}", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Form(recent, partner.id, c.partner)
            }
        }

        // ---------- the line under it all
        Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(1.dp).background(c.label.copy(alpha = .06f)))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    my.streak >= 2 -> "You've won ${my.streak} in a row"
                    th.streak >= 2 -> "$pName has won ${th.streak} in a row"
                    total == 0 -> "Play a match to start the tug of war"
                    my.wins > th.wins -> "You lead by ${my.wins - th.wins}"
                    th.wins > my.wins -> "$pName leads by ${th.wins - my.wins}"
                    else -> "All square"
                },
                style = Type.footnote, color = c.secondary, modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.AutoAwesome, null, tint = c.special, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tandem score ", style = Type.footnote, color = c.secondary)
            Text(fmt(stats.tandemScore), style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.label)
        }
    }
}

@Composable
private fun Side(u: User, name: String, color: Color, leading: Boolean) {
    val c = T.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (leading) Canvas(Modifier.size(64.dp)) { drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .45f), Color.Transparent))) }
            Avatar(u, 48.dp, ring = color)
        }
        Text(name, style = Type.footnote, color = if (leading) c.label else c.secondary, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Five little marks, oldest first: a lit dot for a win, a ring for a loss, a dash for a draw. */
@Composable
private fun Form(recent: List<Match>, userId: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        recent.forEach { m ->
            when (m.winnerId) {
                userId -> Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                null -> Box(Modifier.width(8.dp).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = .3f)))
                else -> Box(Modifier.size(8.dp).clip(CircleShape).border(1.dp, color.copy(alpha = .45f), CircleShape))
            }
        }
    }
}
