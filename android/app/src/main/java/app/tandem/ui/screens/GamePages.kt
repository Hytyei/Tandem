package app.tandem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Top of every Play sub-page: a back button, a title and an optional line under it. */
@Composable
internal fun PageHeader(title: String, subtitle: String?, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val c = T.colors
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(c.card).pressable(haptic = false, onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.label, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle.uppercase(), style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary, maxLines = 1)
            Text(title, style = Type.title, color = c.label, maxLines = 1)
        }
        trailing?.invoke()
    }
}

// ------------------------------------------------------------------ one page per game
@Composable
internal fun GamePage(vm: AppViewModel, game: MiniGame, me: User, partner: User, g: GamesResponse?, stats: GameStats, nav: PlayNav) {
    val c = T.colors
    val pName = partner.displayName.substringBefore(' ')
    val all = g?.matches.orEmpty().filter { it.game == game.key }
    val active = all.filter { !it.isDone }
    val done = all.filter { it.isDone }
    val mb = stats.bests.firstOrNull { it.game == game.key && it.userId == me.id }
    val tb = stats.bests.firstOrNull { it.game == game.key && it.userId == partner.id }
    val mw = stats.gameWins.firstOrNull { it.game == game.key && it.userId == me.id }?.n ?: 0
    val tw = stats.gameWins.firstOrNull { it.game == game.key && it.userId == partner.id }?.n ?: 0
    val mp = stats.perf.firstOrNull { it.game == game.key && it.userId == me.id }
    val tp = stats.perf.firstOrNull { it.game == game.key && it.userId == partner.id }
    val highs = stats.highs.filter { it.game == game.key }
    var rulesOpen by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item { PageHeader(game.label, game.grp.label, nav.back) }

        // ---------- play
        item {
            SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), padding = 18.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameTile(game, 64.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.blurb, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label)
                        Text(
                            if (rulesOpen) "Hide the rules" else "How to play",
                            style = Type.footnote, color = c.accent,
                            modifier = Modifier.padding(top = 4.dp).pressable(haptic = false) { rulesOpen = !rulesOpen },
                        )
                    }
                }
                if (rulesOpen) Text(game.rules, style = Type.callout, color = c.secondary, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Challenge $pName", onClick = { vm.startMatch(game, 1) { nav.started(it) } }, loading = vm.busy)
                Text("You play first, then $pName plays the very same round. The higher score wins.", style = Type.caption, color = c.tertiary, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
            }
        }

        // ---------- running matches of this game
        if (active.isNotEmpty()) {
            item { SectionHeader("In progress") }
            items(active.size, key = { active[it].id }) { i ->
                MatchCard(active[i], me, partner, onPlay = { r -> nav.play(active[i], r) }, onCancel = { nav.cancel(active[i]) }, showGame = false)
            }
        }

        // ---------- stats
        item { SectionHeader("Stats") }
        item {
            SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BestChip(Modifier.weight(1f), "Your best", mb?.best, c.me)
                    BestChip(Modifier.weight(1f), "$pName's best", tb?.best, c.partner)
                    BestChip(Modifier.weight(1f), "Together", if (mb == null && tb == null) null else (mb?.best ?: 0) + (tb?.best ?: 0), c.special)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text("You", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.me, modifier = Modifier.weight(1f))
                    Text(pName, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.partner, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
                }
                StatRow("match wins", "$mw", "$tw")
                StatRow("rounds played", "${mb?.plays ?: 0}", "${tb?.plays ?: 0}")
                StatRow("average score", if (mb != null && mb.plays > 0) "${mb.total / mb.plays}" else "–", if (tb != null && tb.plays > 0) "${tb.total / tb.plays}" else "–")
                if ((mp?.n ?: 0) > 0 || (tp?.n ?: 0) > 0) {
                    StatRow("fastest touch", ms(mp?.bestMs), ms(tp?.bestMs))
                    StatRow("average reaction", ms(mp?.avgMs), ms(tp?.avgMs))
                }
                if ((mp?.hits ?: 0) + (mp?.misses ?: 0) > 0 || (tp?.hits ?: 0) + (tp?.misses ?: 0) > 0) StatRow("accuracy", acc(mp), acc(tp))
            }
        }

        // ---------- hall of fame
        if (highs.isNotEmpty()) {
            item { SectionHeader("Hall of fame") }
            item {
                SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                    highs.forEachIndexed { k, h ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(listOf("🥇", "🥈", "🥉").getOrElse(k) { "${k + 1}" }, style = Type.callout, modifier = Modifier.width(30.dp))
                            Avatar(if (h.userId == me.id) me else partner, 26.dp, ring = if (h.userId == me.id) c.me else c.partner)
                            Spacer(Modifier.width(8.dp))
                            Text(if (h.userId == me.id) "You" else pName, style = Type.callout, color = c.label, modifier = Modifier.weight(1f))
                            Text(day(h.at), style = Type.caption, color = c.tertiary)
                            Spacer(Modifier.width(10.dp))
                            Text(fmt(h.score), style = Type.callout.copy(fontWeight = FontWeight.Bold), color = c.accent)
                        }
                    }
                }
            }
        }

        // ---------- history
        item { SectionHeader("Past matches") }
        if (done.isEmpty()) item { Text("No finished matches yet. Your results will show up here.", style = Type.callout, color = c.secondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
        items(done.size, key = { done[it].id }) { i -> MatchCard(done[i], me, partner, onPlay = {}, onCancel = {}, showGame = false) }
    }
}

// ------------------------------------------------------------------ recent results
private fun dayLabel(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(DateTimeFormatter.ofPattern("EEEE, d MMM"))
    }
}

@Composable
internal fun RecentPage(me: User, partner: User, g: GamesResponse?, nav: PlayNav) {
    val c = T.colors
    val pName = partner.displayName.substringBefore(' ')
    val active = g?.matches.orEmpty().filter { !it.isDone }
    val done = g?.matches.orEmpty().filter { it.isDone }.sortedByDescending { it.finishedAt }
    val groups = done.groupBy { dayLabel(it.finishedAt) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item { PageHeader("Recent games", "Results & running matches", nav.back) }
        if (g == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = c.accent) } }
        else if (active.isEmpty() && done.isEmpty()) item { EmptyState("Nothing here yet", "Open a game from the Play page and challenge $pName. Your results will show up here.") }

        if (done.isNotEmpty()) {
            val mine = done.count { it.winnerId == me.id }; val theirs = done.count { it.winnerId == partner.id }; val draws = done.count { it.winnerId == null }
            item {
                SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()) {
                    Text("Last ${done.size} finished", style = Type.footnote, color = c.secondary)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("You $mine", style = Type.title, color = c.me, modifier = Modifier.weight(1f))
                        Text("$draws draw${if (draws == 1) "" else "s"}", style = Type.caption, color = c.tertiary)
                        Text("$pName $theirs", style = Type.title, color = c.partner, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
                    }
                }
            }
        }
        if (active.isNotEmpty()) {
            item { SectionHeader("In progress") }
            items(active.size, key = { "a-" + active[it].id }) { i ->
                MatchCard(active[i], me, partner, onPlay = { r -> nav.play(active[i], r) }, onCancel = { nav.cancel(active[i]) }, onOpen = { nav.open("game:${active[i].game}") })
            }
        }
        groups.forEach { (label, list) ->
            item { SectionHeader(label) }
            items(list.size, key = { "d-" + list[it].id }) { i ->
                MatchCard(list[i], me, partner, onPlay = {}, onCancel = {}, onOpen = { nav.open("game:${list[i].game}") })
            }
        }
    }
}

// ------------------------------------------------------------------ goals
@Composable
internal fun GoalsPage(vm: AppViewModel, me: User, partner: User, g: GamesResponse?, nav: PlayNav) {
    val goals = g?.goals.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item { PageHeader("Goals", "Put something on the line", nav.back) }
        item { Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) { PrimaryButton("New goal", onClick = nav.newGoal) } }
        if (goals.isEmpty()) item { EmptyState("No goals yet", "Put something on the line: the first one to 3 match wins gets lunch paid by the other.") }
        items(goals.size, key = { goals[it].id }) { i -> GoalCard(goals[i], me, partner, vm) }
    }
}

// ------------------------------------------------------------------ overall stats
@Composable
internal fun StatsPage(me: User, partner: User, stats: GameStats, nav: PlayNav) {
    val c = T.colors
    val pName = partner.displayName.substringBefore(' ')
    val my = stats.players.firstOrNull { it.userId == me.id } ?: PlayerStat(me.id)
    val th = stats.players.firstOrNull { it.userId == partner.id } ?: PlayerStat(partner.id)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item { PageHeader("Stats", "You & $pName", nav.back) }
        item {
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
        }
        item { SpeedCard(stats, me, partner) }
        item { SectionHeader("Wins by game") }
        val played = MiniGame.playable.filter { gm -> stats.bests.any { it.game == gm.key } }
        if (played.isEmpty()) item { Text("Play a game to see it here.", style = Type.callout, color = c.secondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)) }
        items(played.size) { i ->
            val gm = played[i]
            val mw = stats.gameWins.firstOrNull { it.game == gm.key && it.userId == me.id }?.n ?: 0
            val tw = stats.gameWins.firstOrNull { it.game == gm.key && it.userId == partner.id }?.n ?: 0
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.card)
                    .pressable(haptic = false) { nav.open("game:${gm.key}") }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameTile(gm, 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(gm.label, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.weight(1f))
                Text("$mw", style = Type.headline, color = c.me)
                Text("  –  ", style = Type.callout, color = c.tertiary)
                Text("$tw", style = Type.headline, color = c.partner)
            }
        }
    }
}
