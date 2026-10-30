package app.tandem.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/**
 * The Play tab. It is a small set of pages: the home (your turn, a tile for every game, links), one page per game
 * (play, stats, hall of fame, history), Recent results, Goals and overall Stats. [route] is "home", "recent", "goals", "stats" or "game:<key>".
 */
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
    var route by rememberSaveable { mutableStateOf("home") }
    var newGoal by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var cancelling by remember { mutableStateOf<Match?>(null) }

    LaunchedEffect(Unit) { while (true) { vm.loadGames(); delay(20_000) } }
    BackHandler(route != "home") { route = "home" }

    val nav = PlayNav(
        open = { route = it },
        back = { route = "home" },
        play = { m, r -> playing = m.id to r },
        cancel = { cancelling = it },
        newGoal = { newGoal = true },
        started = { m -> playing = m.id to 1 },
    )

    AnimatedContent(
        route, label = "playRoute",
        transitionSpec = {
            val deeper = targetState != "home"
            (slideInHorizontally(tween(260)) { if (deeper) it / 6 else -it / 6 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(tween(200)) { if (deeper) -it / 6 else it / 6 } + fadeOut(tween(140)))
        },
    ) { r ->
        when {
            r == "recent" -> RecentPage(me, partner, g, nav)
            r == "goals" -> GoalsPage(vm, me, partner, g, nav)
            r == "stats" -> StatsPage(me, partner, stats, nav)
            r.startsWith("game:") -> {
                val game = MiniGame.from(r.removePrefix("game:"))
                if (game != null) GamePage(vm, game, me, partner, g, stats, nav) else PlayHome(vm, me, partner, g, stats, nav)
            }
            else -> PlayHome(vm, me, partner, g, stats, nav)
        }
    }

    if (newGoal) NewGoalSheet(vm, onDismiss = { newGoal = false })

    playing?.let { (mid, round) ->
        val m = g?.matches?.firstOrNull { it.id == mid }
        val game = MiniGame.from(m?.game)
        if (m != null && game != null) RoundRunnerDialog(
            game = game, seed = m.seed + round * 7919L, round = round, rounds = m.rounds, partnerName = pName,
            beat = m.scoreOf(round, partner.id), sending = vm.busy,
            onFinished = { score, perf ->
                vm.submitScore(m.id, round, score, perf) { res ->
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

/** What the pages can ask the Play tab to do. */
internal class PlayNav(
    val open: (String) -> Unit,
    val back: () -> Unit,
    val play: (Match, Int) -> Unit,
    val cancel: (Match) -> Unit,
    val newGoal: () -> Unit,
    val started: (Match) -> Unit,
)

// ------------------------------------------------------------------ home
@Composable
private fun PlayHome(vm: AppViewModel, me: User, partner: User, g: GamesResponse?, stats: GameStats, nav: PlayNav) {
    val c = T.colors
    val pName = partner.displayName.substringBefore(' ')
    val myStat = stats.players.firstOrNull { it.userId == me.id } ?: PlayerStat(me.id)
    val theirStat = stats.players.firstOrNull { it.userId == partner.id } ?: PlayerStat(partner.id)
    val active = g?.matches.orEmpty().filter { !it.isDone }
    val mine = active.filter { it.nextTurn(me.id, partner.id) != null }
        .sortedByDescending { m -> m.scoreOf(m.nextTurn(me.id, partner.id) ?: 1, partner.id) != null }   // the ones your partner already played come first
    val waiting = active.filter { it.nextTurn(me.id, partner.id) == null }
    val doneCount = g?.matches.orEmpty().count { it.isDone }
    val goalsActive = g?.goals.orEmpty().count { it.status == "active" }
    val turnsByGame = mine.groupingBy { it.game }.eachCount()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item { LargeTitle("Play", subtitle = "Games for two") }

        // ---------- Your turn: the most important thing on this page
        if (mine.isNotEmpty()) {
            item { TurnHeader(mine.size) }
            items(mine.size, key = { "turn-" + mine[it].id }) { i ->
                val m = mine[i]
                TurnCard(m, me, partner, onPlay = { r -> nav.play(m, r) }, onOpen = { nav.open("game:${m.game}") })
            }
        } else if (g != null) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (waiting.isEmpty()) "🎮" else "✅", fontSize = 24.sp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (waiting.isEmpty()) "No matches running" else "You're all caught up", style = Type.headline, color = c.label)
                        Text(if (waiting.isEmpty()) "Pick a game below to challenge $pName." else "Waiting for $pName to play.", style = Type.caption, color = c.secondary)
                    }
                }
            }
        }

        // ---------- scoreboard
        item {
            SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), shape = RoundedCornerShape(26.dp), padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(me, 40.dp, ring = c.me)
                    Spacer(Modifier.width(10.dp))
                    Text("${myStat.wins}", style = Type.numberHuge.copy(fontSize = 34.sp, lineHeight = 38.sp), color = c.me)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("match wins", style = Type.caption, color = c.secondary)
                        Text("${stats.draws} draw${if (stats.draws == 1) "" else "s"}", style = Type.caption, color = c.tertiary)
                    }
                    Text("${theirStat.wins}", style = Type.numberHuge.copy(fontSize = 34.sp, lineHeight = 38.sp), color = c.partner)
                    Spacer(Modifier.width(10.dp))
                    Avatar(partner, 40.dp, ring = c.partner)
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.special.copy(alpha = .14f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🏅", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Tandem score", style = Type.footnote, color = c.secondary, modifier = Modifier.weight(1f))
                    Text(fmt(stats.tandemScore), style = Type.headline, color = c.special)
                }
                val hot = if (myStat.streak >= 2) "🔥 You're on a ${myStat.streak}-match winning streak" else if (theirStat.streak >= 2) "🔥 $pName is on a ${theirStat.streak}-match winning streak" else null
                if (hot != null) Text(hot, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.padding(top = 8.dp))
            }
        }

        // ---------- won goals waiting to be paid
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

        // ---------- waiting for the partner
        if (waiting.isNotEmpty()) {
            item { SectionHeader("Waiting for $pName") }
            items(waiting.size, key = { "wait-" + waiting[it].id }) { i ->
                val m = waiting[i]
                val game = MiniGame.from(m.game) ?: return@items
                val mineScore = m.scoreOf(m.currentRound(), me.id)
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card)
                        .pressable(haptic = false) { nav.open("game:${m.game}") }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GameTile(game, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.label, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                        Text(if (mineScore != null) "You scored $mineScore" else "Not started", style = Type.caption, color = c.secondary)
                    }
                    Text("⏳", fontSize = 18.sp)
                }
            }
        }

        // ---------- shortcuts
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinkTile(Modifier.weight(1f), "🕘", "Recent", if (doneCount == 0) "results" else "$doneCount finished") { nav.open("recent") }
                LinkTile(Modifier.weight(1f), "🎯", "Goals", if (goalsActive == 0) "stakes" else "$goalsActive active") { nav.open("goals") }
                LinkTile(Modifier.weight(1f), "📊", "Stats", "speed & wins") { nav.open("stats") }
            }
        }

        // ---------- a tile for every game
        MiniGame.playable.groupBy { it.grp }.forEach { (grp, list) ->
            item { SectionHeader(grp.label) }
            val rows = list.chunked(2)
            items(rows.size) { i ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rows[i].forEach { gm ->
                        val best = stats.bests.firstOrNull { it.game == gm.key && it.userId == me.id }?.best
                        GameTileCard(Modifier.weight(1f), gm, turnsByGame[gm.key] ?: 0, best) { nav.open("game:${gm.key}") }
                    }
                    if (rows[i].size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TurnHeader(count: Int) {
    val c = T.colors
    val pulse by rememberInfiniteTransition(label = "turnPulse").animateFloat(
        .55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse",
    )
    Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = pulse)))
        Spacer(Modifier.width(8.dp))
        Text("YOUR TURN", style = Type.footnote.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp), color = c.accent, modifier = Modifier.weight(1f))
        Text(if (count == 1) "1 game is waiting for you" else "$count games are waiting for you", style = Type.caption, color = c.secondary)
    }
}

/** The big, glowing card for a match that is waiting for you. */
@Composable
private fun TurnCard(m: Match, me: User, partner: User, onPlay: (Int) -> Unit, onOpen: () -> Unit) {
    val c = T.colors
    val game = MiniGame.from(m.game) ?: return
    val pName = partner.displayName.substringBefore(' ')
    val round = m.nextTurn(me.id, partner.id) ?: return
    val theirs = m.scoreOf(round, partner.id)
    val pulse by rememberInfiniteTransition(label = "turnGlow").animateFloat(
        .35f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "glow",
    )
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(game.tint.copy(alpha = .55f), game.tint.copy(alpha = .22f))))
            .border(if (theirs != null) 2.dp else 1.dp, if (theirs != null) c.accent.copy(alpha = pulse) else Color.White.copy(alpha = .18f), shape)
            .pressable(haptic = false) { onPlay(round) }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Text(game.emoji, fontSize = 30.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(game.label, style = Type.headline, color = Color.White)
            Text(
                if (theirs != null) "$pName scored $theirs. Beat it!" else "You go first",
                style = Type.callout, color = Color.White.copy(alpha = .9f),
            )
            if (m.rounds > 1) Text("Round $round of ${m.rounds}", style = Type.caption, color = Color.White.copy(alpha = .7f))
        }
        Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text("Play", style = Type.callout.copy(fontWeight = FontWeight.Bold), color = c.accent)
        }
    }
}

@Composable
private fun LinkTile(modifier: Modifier, emoji: String, title: String, sub: String, onClick: () -> Unit) {
    val c = T.colors
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(c.card).pressable(haptic = false, onClick = onClick).padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(title, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
        Text(sub, style = Type.caption, color = c.secondary, maxLines = 1)
    }
}

/** A game as a tile: colour, big emoji, name, a one-line hint and either your best score or a "Your turn" tag. */
@Composable
private fun GameTileCard(modifier: Modifier, game: MiniGame, turns: Int, best: Int?, onClick: () -> Unit) {
    val c = T.colors
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.height(134.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(game.tint.copy(alpha = .36f), game.tint.copy(alpha = .12f))))
            .border(if (turns > 0) 1.5.dp else 1.dp, if (turns > 0) c.accent else game.tint.copy(alpha = .35f), shape)
            .pressable(haptic = false, onClick = onClick),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Text(game.emoji, fontSize = 34.sp)
            Spacer(Modifier.weight(1f))
            Text(game.label, style = Type.headline, color = c.label, maxLines = 1)
            Text(game.blurb.substringBefore(" · "), style = Type.caption, color = c.secondary, maxLines = 1)
        }
        if (turns > 0) {
            Text(
                if (turns == 1) "Your turn" else "$turns waiting", style = Type.caption.copy(fontWeight = FontWeight.Bold), color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(50)).background(c.accent).padding(horizontal = 9.dp, vertical = 3.dp),
            )
        } else if (best != null) {
            Text("★ $best", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.label.copy(alpha = .85f), modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
    }
}
