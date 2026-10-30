package app.tandem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val GOAL_PRESETS = listOf(
    "Loser buys lunch 🥪", "Loser buys dinner 🍽️", "Loser buys coffee ☕", "Loser buys dessert 🍦",
    "Loser picks the movie 🎬", "Loser does the dishes 🧽", "Winner gets a massage 💆", "Loser plans the next date 🌹",
)

internal fun fmt(n: Int) = "%,d".format(n)
internal fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM"))

@Composable
internal fun GameTile(game: MiniGame, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(game.tint.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
        Text(game.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
internal fun BestChip(modifier: Modifier, label: String, value: Int?, tint: androidx.compose.ui.graphics.Color) {
    val c = T.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .12f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Type.caption, color = c.secondary, maxLines = 1)
        Text(value?.let { fmt(it) } ?: "–", style = Type.title, color = tint)
    }
}

/**
 * One match as a card. When it is your turn and your partner already played, it is highlighted (accent border and tint)
 * and says what they scored. [onOpen] (optional) opens the game's own page when the card is tapped.
 */
@Composable
internal fun MatchCard(
    m: Match, me: User, partner: User, onPlay: (Int) -> Unit, onCancel: () -> Unit,
    onOpen: (() -> Unit)? = null, showGame: Boolean = true,
) {
    val c = T.colors
    val game = MiniGame.from(m.game) ?: return
    val pName = partner.displayName.substringBefore(' ')
    val turn = m.nextTurn(me.id, partner.id)
    val theirScore = turn?.let { m.scoreOf(it, partner.id) }
    val hot = turn != null && theirScore != null
    val shape = RoundedCornerShape(24.dp)
    SoftCard(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .then(if (hot) Modifier.border(1.5.dp, c.accent, shape) else Modifier)
            .then(if (onOpen != null) Modifier.pressable(haptic = false, onClick = onOpen) else Modifier),
        shape = shape, color = if (hot) c.accent.copy(alpha = .14f) else c.card,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showGame) { GameTile(game, 46.dp); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                val title = (if (showGame) game.label else "Match") + if (m.rounds == 1) "" else " · best of ${m.rounds}"
                Text(title, style = Type.headline, color = c.label)
                val status = when {
                    m.isDone -> {
                        val a = m.roundWins(me.id, partner.id); val b = m.roundWins(partner.id, me.id)
                        when (m.winnerId) {
                            me.id -> "You won${if (m.rounds > 1) " $a–$b" else ""} · ${day(m.finishedAt)}"
                            partner.id -> "$pName won${if (m.rounds > 1) " $b–$a" else ""} · ${day(m.finishedAt)}"
                            else -> "Draw · ${day(m.finishedAt)}"
                        }
                    }
                    hot -> "Your turn · $pName scored $theirScore" + if (m.rounds > 1) " · round $turn of ${m.rounds}" else ""
                    turn != null -> "Your turn · you go first" + if (m.rounds > 1) " · round $turn of ${m.rounds}" else ""
                    else -> "Waiting for $pName" + if (m.rounds > 1) " · round ${m.currentRound()} of ${m.rounds}" else ""
                }
                Text(status, style = Type.callout.copy(fontWeight = if (turn != null) FontWeight.SemiBold else FontWeight.Normal), color = if (turn != null) c.accent else c.secondary)
            }
            if (turn != null) SoftButton("Play", onClick = { onPlay(turn) })
        }
        // round by round: yours : theirs
        Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 1..m.rounds) {
                val a = m.scoreOf(r, me.id); val b = m.scoreOf(r, partner.id)
                Row(Modifier.clip(RoundedCornerShape(50)).background(c.separator.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (m.rounds > 1) Text("R$r  ", style = Type.caption, color = c.tertiary)
                    Text(a?.toString() ?: "–", style = Type.caption.copy(fontWeight = FontWeight.Bold), color = c.me)
                    Text("  :  ", style = Type.caption, color = c.tertiary)
                    Text(b?.toString() ?: "–", style = Type.caption.copy(fontWeight = FontWeight.Bold), color = c.partner)
                }
            }
        }
        if (!m.isDone) TextButton(onClick = onCancel, contentPadding = PaddingValues(0.dp)) { Text("Cancel match", style = Type.caption, color = c.tertiary) }
    }
}

@Composable
internal fun GoalCard(goal: GameGoal, me: User, partner: User, vm: AppViewModel) {
    val c = T.colors
    val pName = partner.displayName.substringBefore(' ')
    val unit = if (goal.metric == "wins") "match wins" else "points"
    val dim = goal.status == "settled"
    var confirmDelete by remember { mutableStateOf(false) }
    SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (goal.status == "active") "🎯" else if (goal.status == "won") "🏆" else "✅", fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.title, style = Type.headline, color = if (dim) c.secondary else c.label)
                Text("First to ${fmt(goal.target)} $unit", style = Type.caption, color = c.secondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        listOf(me to c.me, partner to c.partner).forEach { (u, color) ->
            val p = goal.progress[u.id] ?: 0
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (u.id == me.id) "You" else pName, style = Type.caption, color = c.secondary, modifier = Modifier.width(56.dp), maxLines = 1)
                LinearProgressIndicator(
                    progress = { (p.toFloat() / goal.target).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)), color = color, trackColor = c.separator,
                )
                Text("${fmt(p)}", style = Type.caption.copy(fontWeight = FontWeight.Bold), color = color, modifier = Modifier.width(60.dp), textAlign = TextAlign.End)
            }
        }
        when (goal.status) {
            "won" -> Text("${if (goal.winnerId == me.id) "You" else pName} won. Pay up!", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.special, modifier = Modifier.padding(top = 8.dp))
            "settled" -> Text("${if (goal.winnerId == me.id) "You" else pName} won · settled ${day(goal.settledAt)}", style = Type.footnote, color = c.tertiary, modifier = Modifier.padding(top = 8.dp))
            else -> Text("Counting since ${day(goal.createdAt)}", style = Type.footnote, color = c.tertiary, modifier = Modifier.padding(top = 8.dp))
        }
        Row {
            if (goal.status == "won") SoftButton("Paid up ✓", onClick = { vm.settleGoal(goal.id) }, color = c.special)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { confirmDelete = true }, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Delete", style = Type.caption, color = c.tertiary) }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = c.sheet,
        title = { Text("Delete this goal?") },
        confirmButton = { TextButton(onClick = { vm.deleteGoal(goal.id); confirmDelete = false }) { Text("Delete", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = c.secondary) } },
    )
}

@Composable
internal fun StatRow(label: String, mine: String, theirs: String) {
    val c = T.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(mine, style = Type.headline, color = c.me, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        Text(label, style = Type.footnote, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f))
        Text(theirs, style = Type.headline, color = c.partner, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

internal fun ms(v: Int?) = if (v == null || v <= 0) "–" else "$v ms"
internal fun acc(p: PerfRow?): String = if (p == null || p.hits + p.misses == 0) "–" else "${p.hits * 100 / (p.hits + p.misses)}%"

/** How fast each of you plays: fastest touch, average reaction and accuracy over every timed game. */
@Composable
internal fun SpeedCard(stats: GameStats, me: User, partner: User) {
    val c = T.colors
    val pName = partner.displayName.substringBefore(' ')
    fun mine(id: String) = stats.perf.filter { it.userId == id }
    val a = mine(me.id); val b = mine(partner.id)
    if (a.isEmpty() && b.isEmpty()) return
    fun fastest(rows: List<PerfRow>): PerfRow? = rows.filter { it.bestMs > 0 }.minByOrNull { it.bestMs }
    fun avg(rows: List<PerfRow>): Int? { val n = rows.sumOf { it.n }; return if (n == 0) null else (rows.sumOf { it.avgMs.toLong() * it.n } / n).toInt() }
    fun accuracy(rows: List<PerfRow>): String { val h = rows.sumOf { it.hits }; val m = rows.sumOf { it.misses }; return if (h + m == 0) "–" else "${h * 100 / (h + m)}%" }
    val fa = fastest(a); val fb = fastest(b)
    SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡", fontSize = 26.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Speed & reflexes", style = Type.headline, color = c.label)
                Text("measured as you play", style = Type.caption, color = c.secondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("You", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.me, modifier = Modifier.weight(1f))
            Text(pName, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.partner, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
        }
        StatRow("fastest touch", ms(fa?.bestMs), ms(fb?.bestMs))
        StatRow("…in", fa?.let { g -> MiniGame.from(g.game)?.label } ?: "–", fb?.let { g -> MiniGame.from(g.game)?.label } ?: "–")
        StatRow("average reaction", ms(avg(a)), ms(avg(b)))
        StatRow("accuracy", accuracy(a), accuracy(b))
        StatRow("timed touches", "${a.sumOf { it.n }}", "${b.sumOf { it.n }}")
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NewGoalSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val c = T.colors
    var title by remember { mutableStateOf(GOAL_PRESETS[1]) }
    var metric by remember { mutableIntStateOf(0) }   // 0 wins, 1 points
    var target by remember { mutableIntStateOf(3) }
    val step = if (metric == 0) 1 else 100
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("New goal", style = Type.title, color = c.label)
            Text("Put something on the line. When one of you reaches the goal first, the other one pays. Counting starts now.", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GOAL_PRESETS.forEach { p ->
                    val on = p == title
                    val shape = RoundedCornerShape(50)
                    Text(
                        p, style = Type.footnote.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = c.label,
                        modifier = Modifier.clip(shape).background(if (on) c.accent.copy(alpha = .18f) else c.card).border(1.dp, if (on) c.accent else c.separator, shape)
                            .pressable { title = p }.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                title, { if (it.length <= 60) title = it }, label = { Text("The stakes") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent),
            )
            Spacer(Modifier.height(14.dp))
            Segmented(listOf("Match wins", "Points"), metric, { metric = it; target = if (it == 0) 3 else 500 })
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("First to", style = Type.body, color = c.label, modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.card).pressable { target = (target - step).coerceAtLeast(step) }, contentAlignment = Alignment.Center) { Text("−", style = Type.title, color = c.accent) }
                Text(fmt(target), style = Type.title, color = c.label, modifier = Modifier.widthIn(min = 84.dp), textAlign = TextAlign.Center)
                Box(Modifier.size(40.dp).clip(CircleShape).background(c.card).pressable { target = (target + step).coerceAtMost(if (metric == 0) 50 else 100000) }, contentAlignment = Alignment.Center) { Text("+", style = Type.title, color = c.accent) }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Set goal", onClick = { vm.addGoal(title, if (metric == 0) "wins" else "points", target, onDismiss) }, enabled = title.isNotBlank(), loading = vm.busy)
        }
    }
}
