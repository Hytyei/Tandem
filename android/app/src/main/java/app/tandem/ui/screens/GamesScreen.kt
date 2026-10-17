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

private val GOAL_PRESETS = listOf(
    "Loser buys lunch 🥪", "Loser buys dinner 🍽️", "Loser buys coffee ☕", "Loser buys dessert 🍦",
    "Loser picks the movie 🎬", "Loser does the dishes 🧽", "Winner gets a massage 💆", "Loser plans the next date 🌹",
)

private fun fmt(n: Int) = "%,d".format(n)
private fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM"))

/** The Play tab: turn-based, timed minigames for two, with high scores, a shared "Tandem score", goals with stakes, and stats. */
@Composable
fun GamesScreen(vm: AppViewModel) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val me = data.me
    val partner = data.couple.partner ?: return
    val pName = partner.displayName.substringBefore(' ')
    val g = vm.games
    val stats = g?.stats ?: GameStats()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var newMatch by remember { mutableStateOf(false) }
    var newGoal by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var cancelling by remember { mutableStateOf<Match?>(null) }

    LaunchedEffect(Unit) { while (true) { vm.loadGames(); delay(20_000) } }

    val myStat = stats.players.firstOrNull { it.userId == me.id } ?: PlayerStat(me.id)
    val theirStat = stats.players.firstOrNull { it.userId == partner.id } ?: PlayerStat(partner.id)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item { LargeTitle("Play", subtitle = "Turn-based minigames") }

        // ---------- Scoreboard
        item {
            SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(0), shape = RoundedCornerShape(30.dp), padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(me, 54.dp, ring = c.me)
                        Text("You", style = Type.footnote, color = c.secondary)
                        Text("${myStat.wins}", style = Type.numberHuge.copy(fontSize = 44.sp, lineHeight = 48.sp), color = c.me)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("match wins", style = Type.caption, color = c.secondary)
                        Text("–", style = Type.title, color = c.tertiary)
                        Text("${stats.draws} draw${if (stats.draws == 1) "" else "s"}", style = Type.caption, color = c.tertiary)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Avatar(partner, 54.dp, ring = c.partner)
                        Text(pName, style = Type.footnote, color = c.secondary, maxLines = 1)
                        Text("${theirStat.wins}", style = Type.numberHuge.copy(fontSize = 44.sp, lineHeight = 48.sp), color = c.partner)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.special.copy(alpha = .14f)).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🏅", fontSize = 22.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tandem score", style = Type.footnote, color = c.secondary)
                        Text("every point either of you has scored", style = Type.caption, color = c.tertiary)
                    }
                    Text(fmt(stats.tandemScore), style = Type.title, color = c.special)
                }
                val hot = if (myStat.streak >= 2) "🔥 You're on a ${myStat.streak}-match winning streak" else if (theirStat.streak >= 2) "🔥 $pName is on a ${theirStat.streak}-match winning streak" else null
                if (hot != null) Text(hot, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.padding(top = 10.dp))
            }
        }

        // ---------- Won goals waiting to be paid
        val won = g?.goals.orEmpty().filter { it.status == "won" }
        items(won.size) { i ->
            val goal = won[i]
            val winner = if (goal.winnerId == me.id) "You" else pName
            val loser = if (goal.winnerId == me.id) pName else "You"
            SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), color = c.special.copy(alpha = .16f)) {
                Text("🏆 $winner won the goal!", style = Type.headline, color = c.label)
                Text("${goal.title} — time for $loser to pay up.", style = Type.callout, color = c.secondary, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(8.dp))
                SoftButton("Paid up ✓", onClick = { vm.settleGoal(goal.id) }, color = c.special)
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Segmented(listOf("Matches", "Scores", "Goals", "Stats"), tab, { tab = it })
            }
        }

        when (tab) {
            // ---------- Matches
            0 -> {
                item { Box(Modifier.padding(horizontal = 16.dp)) { PrimaryButton("New match", onClick = { newMatch = true }) } }
                val active = g?.matches.orEmpty().filter { !it.isDone }
                val done = g?.matches.orEmpty().filter { it.isDone }
                if (g == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }
                else if (active.isEmpty() && done.isEmpty()) item { EmptyState("No matches yet", "Start one: you each play the same timed round, one after the other, and the higher score wins.") }
                if (active.isNotEmpty()) {
                    item { SectionHeader("In progress") }
                    items(active.size, key = { active[it].id }) { i ->
                        MatchCard(active[i], me, partner, onPlay = { r -> playing = active[i].id to r }, onCancel = { cancelling = active[i] })
                    }
                }
                if (done.isNotEmpty()) {
                    item { SectionHeader("History") }
                    items(done.size, key = { done[it].id }) { i -> MatchCard(done[i], me, partner, onPlay = {}, onCancel = {}) }
                }
            }
            // ---------- High scores
            1 -> {
                items(MiniGame.entries.size) { i ->
                    val game = MiniGame.entries[i]
                    Column {
                    if (i == 0 || MiniGame.entries[i - 1].brain != game.brain) SectionHeader(if (game.brain) "Brain teasers" else "Quick reflexes")
                    val mine = stats.bests.firstOrNull { it.game == game.key && it.userId == me.id }
                    val theirs = stats.bests.firstOrNull { it.game == game.key && it.userId == partner.id }
                    val highs = stats.highs.filter { it.game == game.key }
                    SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GameTile(game, 44.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(game.label, style = Type.headline, color = c.label)
                                Text(game.blurb, style = Type.caption, color = c.secondary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BestChip(Modifier.weight(1f), "You", mine?.best, c.me)
                            BestChip(Modifier.weight(1f), pName, theirs?.best, c.partner)
                            BestChip(Modifier.weight(1f), "Together", if (mine == null && theirs == null) null else (mine?.best ?: 0) + (theirs?.best ?: 0), c.special)
                        }
                        if (highs.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("HALL OF FAME", style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary)
                            highs.forEachIndexed { k, h ->
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${k + 1}", style = Type.callout.copy(fontWeight = FontWeight.Bold), color = c.tertiary, modifier = Modifier.width(22.dp))
                                    Avatar(if (h.userId == me.id) me else partner, 24.dp, ring = if (h.userId == me.id) c.me else c.partner)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (h.userId == me.id) "You" else pName, style = Type.callout, color = c.label, modifier = Modifier.weight(1f))
                                    Text(day(h.at), style = Type.caption, color = c.tertiary)
                                    Spacer(Modifier.width(10.dp))
                                    Text("${h.score}", style = Type.callout.copy(fontWeight = FontWeight.Bold), color = c.accent)
                                }
                            }
                        } else Text("Not played yet", style = Type.footnote, color = c.tertiary, modifier = Modifier.padding(top = 10.dp))
                    }
                    }
                }
            }
            // ---------- Goals
            2 -> {
                item { Box(Modifier.padding(horizontal = 16.dp)) { PrimaryButton("New goal", onClick = { newGoal = true }) } }
                val goals = g?.goals.orEmpty()
                if (goals.isEmpty()) item { EmptyState("No goals yet", "Put something on the line: the first one to 3 match wins gets lunch paid by the other.") }
                items(goals.size, key = { goals[it].id }) { i -> GoalCard(goals[i], me, partner, vm) }
            }
            // ---------- Stats
            else -> {
                item { StatsTab(stats, me, partner) }
            }
        }
    }

    if (newMatch) NewMatchSheet(vm, onDismiss = { newMatch = false }, onStarted = { m -> newMatch = false; playing = m.id to 1 })
    if (newGoal) NewGoalSheet(vm, onDismiss = { newGoal = false })

    playing?.let { (mid, round) ->
        val m = g?.matches?.firstOrNull { it.id == mid }
        val game = MiniGame.from(m?.game)
        if (m != null && game != null) RoundRunnerDialog(
            game = game, seed = m.seed + round * 7919L, round = round, rounds = m.rounds, partnerName = pName,
            beat = m.scoreOf(round, partner.id), sending = vm.busy,
            onFinished = { score ->
                vm.submitScore(m.id, round, score) { res ->
                    if (res != null) {
                        playing = null
                        vm.toast(
                            if (!res.isDone) "Score sent. Now it's $pName's turn."
                            else {
                                val a = res.roundWins(me.id, partner.id); val b = res.roundWins(partner.id, me.id)
                                when (res.winnerId) { me.id -> "You won the match $a–$b! 🏆"; partner.id -> "$pName won the match $b–$a"; else -> "It's a draw!" }
                            },
                        )
                    }
                }
            },
            onClose = { playing = null },
        )
    }

    cancelling?.let { m ->
        AlertDialog(
            onDismissRequest = { cancelling = null }, containerColor = c.sheet,
            title = { Text("Cancel this match?") },
            text = { Text("The scores played so far in it are removed for both of you.") },
            confirmButton = { TextButton(onClick = { vm.cancelMatch(m.id); cancelling = null }) { Text("Cancel match", color = c.danger) } },
            dismissButton = { TextButton(onClick = { cancelling = null }) { Text("Keep", color = c.secondary) } },
        )
    }
}

@Composable
private fun GameTile(game: MiniGame, size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(game.tint.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
        Text(game.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

@Composable
private fun BestChip(modifier: Modifier, label: String, value: Int?, tint: androidx.compose.ui.graphics.Color) {
    val c = T.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .12f)).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Type.caption, color = c.secondary, maxLines = 1)
        Text(value?.let { fmt(it) } ?: "–", style = Type.title, color = tint)
    }
}

@Composable
private fun MatchCard(m: Match, me: User, partner: User, onPlay: (Int) -> Unit, onCancel: () -> Unit) {
    val c = T.colors
    val game = MiniGame.from(m.game) ?: return
    val pName = partner.displayName.substringBefore(' ')
    val turn = m.nextTurn(me.id, partner.id)
    SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameTile(game, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${game.label} · ${if (m.rounds == 1) "single round" else "best of ${m.rounds}"}", style = Type.headline, color = c.label)
                val status = when {
                    m.isDone -> {
                        val a = m.roundWins(me.id, partner.id); val b = m.roundWins(partner.id, me.id)
                        when (m.winnerId) {
                            me.id -> "You won${if (m.rounds > 1) " $a–$b" else ""} · ${day(m.finishedAt)}"
                            partner.id -> "$pName won${if (m.rounds > 1) " $b–$a" else ""} · ${day(m.finishedAt)}"
                            else -> "Draw · ${day(m.finishedAt)}"
                        }
                    }
                    turn != null -> "Your turn" + if (m.rounds > 1) " · round $turn of ${m.rounds}" else ""
                    else -> "Waiting for $pName" + if (m.rounds > 1) " · round ${m.currentRound()} of ${m.rounds}" else ""
                }
                Text(status, style = Type.callout, color = if (turn != null) c.accent else c.secondary)
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
private fun GoalCard(goal: GameGoal, me: User, partner: User, vm: AppViewModel) {
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
private fun StatRow(label: String, mine: String, theirs: String) {
    val c = T.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(mine, style = Type.headline, color = c.me, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
        Text(label, style = Type.footnote, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f))
        Text(theirs, style = Type.headline, color = c.partner, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
private fun StatsTab(stats: GameStats, me: User, partner: User) {
    val c = T.colors
    val pName = partner.displayName.substringBefore(' ')
    val my = stats.players.firstOrNull { it.userId == me.id } ?: PlayerStat(me.id)
    val th = stats.players.firstOrNull { it.userId == partner.id } ?: PlayerStat(partner.id)
    Column {
        SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("You", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.me, modifier = Modifier.weight(1f))
                Text("Head to head", style = Type.footnote, color = c.secondary)
                Text(pName, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.partner, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            val a = my.wins; val b = th.wins
            Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(c.separator.copy(alpha = .15f))) {
                if (a + b == 0) Spacer(Modifier.weight(1f))
                else {
                    if (a > 0) Box(Modifier.weight(a.toFloat()).fillMaxHeight().background(c.me))
                    if (b > 0) Box(Modifier.weight(b.toFloat()).fillMaxHeight().background(c.partner))
                }
            }
            Spacer(Modifier.height(8.dp))
            StatRow("match wins", "$a", "$b")
            StatRow("matches played", "${stats.done}", "${stats.done}")
            StatRow("rounds played", "${my.rounds}", "${th.rounds}")
            StatRow("points scored", fmt(my.points), fmt(th.points))
            StatRow("current streak", "${my.streak}", "${th.streak}")
            StatRow("best streak", "${my.bestStreak}", "${th.bestStreak}")
        }
        MiniGame.entries.forEach { game ->
            val mb = stats.bests.firstOrNull { it.game == game.key && it.userId == me.id }
            val tb = stats.bests.firstOrNull { it.game == game.key && it.userId == partner.id }
            val mw = stats.gameWins.firstOrNull { it.game == game.key && it.userId == me.id }?.n ?: 0
            val tw = stats.gameWins.firstOrNull { it.game == game.key && it.userId == partner.id }?.n ?: 0
            SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameTile(game, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(game.label, style = Type.headline, color = c.label)
                }
                Spacer(Modifier.height(6.dp))
                StatRow("match wins", "$mw", "$tw")
                StatRow("rounds played", "${mb?.plays ?: 0}", "${tb?.plays ?: 0}")
                StatRow("best", mb?.best?.toString() ?: "–", tb?.best?.toString() ?: "–")
                StatRow("average", if (mb != null && mb.plays > 0) "${mb.total / mb.plays}" else "–", if (tb != null && tb.plays > 0) "${tb.total / tb.plays}" else "–")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewMatchSheet(vm: AppViewModel, onDismiss: () -> Unit, onStarted: (Match) -> Unit) {
    val c = T.colors
    var game by remember { mutableStateOf(MiniGame.Heart) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("New match", style = Type.title, color = c.label)
            Text("You each play the same round, one after the other. The higher score wins.", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            MiniGame.entries.forEach { gm ->
                if (gm == MiniGame.entries.first() || gm == MiniGame.entries.first { it.brain })
                    Text(if (gm.brain) "BRAIN TEASERS" else "QUICK REFLEXES", style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                val on = gm == game
                val shape = RoundedCornerShape(20.dp)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape).background(if (on) gm.tint.copy(alpha = .16f) else c.card)
                        .border(if (on) 1.5.dp else 1.dp, if (on) gm.tint else c.separator, shape).pressable { game = gm }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameTile(gm, 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(gm.label, style = Type.headline, color = c.label)
                        Text(gm.blurb, style = Type.footnote, color = c.secondary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // every new match is a single round (older best-of-3/5 matches that are still running play out as before)
            PrimaryButton("Start and play", onClick = { vm.startMatch(game, 1, onStarted) }, loading = vm.busy)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewGoalSheet(vm: AppViewModel, onDismiss: () -> Unit) {
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
