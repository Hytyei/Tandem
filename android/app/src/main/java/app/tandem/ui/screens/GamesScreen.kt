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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.SportsEsports
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
            r == "stats" -> StatsPage(me, partner, stats, g, nav)
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
                                when (res.winnerId) { me.id -> "You won the match $a–$b"; partner.id -> "$pName won the match $b–$a"; else -> "It's a draw" }
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
                if (i == 0) TurnHero(m, me, partner, onPlay = { r -> nav.play(m, r) }, onOpen = { nav.open("game:${m.game}") })
                else TurnRow(m, me, partner, onPlay = { r -> nav.play(m, r) }, onOpen = { nav.open("game:${m.game}") })
            }
        } else if (g != null) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card).padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (waiting.isEmpty()) Icons.Outlined.SportsEsports else Icons.Outlined.HourglassEmpty, null, tint = c.secondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (waiting.isEmpty()) "No matches running" else "You're all caught up", style = Type.headline, color = c.label)
                        Text(if (waiting.isEmpty()) "Pick a game below to challenge $pName" else "Waiting for $pName to play", style = Type.caption, color = c.secondary)
                    }
                }
            }
        }

        // ---------- head to head
        item { HeadToHeadCard(me, partner, stats, g?.matches.orEmpty(), Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) }

        // ---------- won goals waiting to be paid
        val won = g?.goals.orEmpty().filter { it.status == "won" }
        items(won.size) { i ->
            val goal = won[i]
            val winner = if (goal.winnerId == me.id) "You" else pName
            val loser = if (goal.winnerId == me.id) pName else "You"
            SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), color = c.special.copy(alpha = .16f)) {
                Text("$winner won the goal", style = Type.headline, color = c.label)
                Text("${goal.title} — time for $loser to pay up.", style = Type.callout, color = c.secondary, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(8.dp))
                SoftButton("Paid up", onClick = { vm.settleGoal(goal.id) }, color = c.special)
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
                    GameMark(game, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.label, style = Type.callout.copy(fontWeight = FontWeight.Medium), color = c.label)
                        Text(if (mineScore != null) "You scored $mineScore" else "Not started", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary)
                    }
                    Text("Their turn", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
                }
            }
        }

        // ---------- shortcuts
        item {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LinkTile(Modifier.weight(1f), Icons.Outlined.History, "Recent", if (doneCount == 0) "results" else "$doneCount finished") { nav.open("recent") }
                LinkTile(Modifier.weight(1f), Icons.Outlined.Flag, "Goals", if (goalsActive == 0) "stakes" else "$goalsActive active") { nav.open("goals") }
                LinkTile(Modifier.weight(1f), Icons.Outlined.BarChart, "Stats", "speed & wins") { nav.open("stats") }
            }
        }

        // ---------- a tile for every game
        MiniGame.playable.groupBy { it.grp }.forEach { (grp, list) ->
            item { SectionHeader(grp.label) }
            // an odd-sized group opens with one wide tile, so the grid has a rhythm instead of an orphan at the end
            val wide = if (list.size % 2 == 1) list.first() else null
            val rest = if (wide != null) list.drop(1) else list
            if (wide != null) item {
                val best = stats.bests.firstOrNull { it.game == wide.key && it.userId == me.id }?.best
                GameTileCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), wide, turnsByGame[wide.key] ?: 0, best, wide = true) { nav.open("game:${wide.key}") }
            }
            val rows = rest.chunked(2)
            items(rows.size) { i ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rows[i].forEach { gm ->
                        val best = stats.bests.firstOrNull { it.game == gm.key && it.userId == me.id }?.best
                        GameTileCard(Modifier.weight(1f), gm, turnsByGame[gm.key] ?: 0, best) { nav.open("game:${gm.key}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnHeader(count: Int) {
    val c = T.colors
    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(c.accent))
        Spacer(Modifier.width(8.dp))
        Text("Your turn", style = Type.headline, color = c.label, modifier = Modifier.weight(1f))
        Text(if (count == 1) "1 game waiting" else "$count games waiting", style = Type.footnote, color = c.secondary)
    }
}

private val Ivory = Color(0xFFF6F2FF)

/** The first match waiting for you: the biggest thing on the page, lit by its game's colour, with one action. */
@Composable
private fun TurnHero(m: Match, me: User, partner: User, onPlay: (Int) -> Unit, onOpen: () -> Unit) {
    val c = T.colors
    val game = MiniGame.from(m.game) ?: return
    val pName = partner.displayName.substringBefore(' ')
    val round = m.nextTurn(me.id, partner.id) ?: return
    val theirs = m.scoreOf(round, partner.id)
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(shape).background(c.card).gameGlow(game, 1.6f)
            .border(1.dp, game.light.copy(alpha = .28f), shape).pressable(haptic = false, onClick = onOpen).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GameMark(game, 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(game.label, style = Type.title, color = c.label, maxLines = 1)
                Text(if (theirs != null) "$pName scored $theirs. Beat it" else "You go first", style = Type.callout, color = c.secondary, maxLines = 1)
                if (m.rounds > 1) Text("Round $round of ${m.rounds}", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Play", onClick = { onPlay(round) }, color = Ivory)
    }
}

/** Any other match waiting for you: one quiet row. */
@Composable
private fun TurnRow(m: Match, me: User, partner: User, onPlay: (Int) -> Unit, onOpen: () -> Unit) {
    val c = T.colors
    val game = MiniGame.from(m.game) ?: return
    val pName = partner.displayName.substringBefore(' ')
    val round = m.nextTurn(me.id, partner.id) ?: return
    val theirs = m.scoreOf(round, partner.id)
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.card)
            .border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(20.dp)).pressable(haptic = false, onClick = onOpen).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameMark(game, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(game.label, style = Type.callout.copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 1)
            Text(if (theirs != null) "$pName scored $theirs" else "You go first", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, maxLines = 1)
        }
        Box(Modifier.clip(RoundedCornerShape(50)).background(Ivory).pressable(haptic = false) { onPlay(round) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Play", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF1A1240))
        }
    }
}

@Composable
private fun LinkTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = T.colors
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(c.card).border(1.dp, c.label.copy(alpha = .08f), RoundedCornerShape(16.dp))
            .pressable(haptic = false, onClick = onClick).padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Icon(icon, null, tint = c.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, style = Type.callout.copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 1)
        Text(sub, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, maxLines = 1)
    }
}

/** A game as a glass tile: its mark, lit faintly in its own colour, its name, and either your best or a "your turn" mark. */
@Composable
private fun GameTileCard(modifier: Modifier, game: MiniGame, turns: Int, best: Int?, wide: Boolean = false, onClick: () -> Unit) {
    val c = T.colors
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.height(if (wide) 104.dp else 148.dp).clip(shape).background(c.card).gameGlow(game)
            .border(1.dp, if (turns > 0) game.light.copy(alpha = .5f) else Color.White.copy(alpha = .07f), shape)
            .pressable(haptic = false, onClick = onClick),
    ) {
        val blurb = game.blurb.substringBefore(" · ").replaceFirstChar { it.uppercase() }
        if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GameMark(game, 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(game.label, style = Type.title, color = c.label, maxLines = 1)
                Text(blurb, style = Type.footnote, color = c.secondary, maxLines = 1)
            }
        } else Column(Modifier.fillMaxSize().padding(16.dp)) {
            GameMark(game, 44.dp)
            Spacer(Modifier.weight(1f))
            Text(game.label, style = Type.headline, color = c.label, maxLines = 1)
            Text(blurb, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, maxLines = 1)
        }
        if (turns > 0) {
            Row(Modifier.align(Alignment.TopEnd).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(game.light))
                Spacer(Modifier.width(6.dp))
                Text(if (turns == 1) "Your turn" else "$turns waiting", style = Type.caption, color = c.label)
            }
        } else if (best != null) {
            Text("Best ${fmt(best)}", style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontFeatureSettings = "tnum"), color = c.secondary, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
        }
    }
}
