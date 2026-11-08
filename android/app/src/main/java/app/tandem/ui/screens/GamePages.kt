package app.tandem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
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
            if (subtitle != null) Text(subtitle, style = Type.footnote, color = c.secondary, maxLines = 1)
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

        // ---------- play: the game's mark, lit in its colour, and the one action
        item {
            val shape = RoundedCornerShape(24.dp)
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(shape).background(c.card).gameGlow(game, 1.6f)
                    .border(1.dp, game.light.copy(alpha = .22f), shape).padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GameMark(game, 64.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(game.blurb.replaceFirstChar { it.uppercase() }, style = Type.callout.copy(fontWeight = FontWeight.Medium), color = c.label)
                        Text(
                            if (rulesOpen) "Hide the rules" else "How to play",
                            style = Type.footnote, color = game.light,
                            modifier = Modifier.padding(top = 4.dp).pressable(haptic = false) { rulesOpen = !rulesOpen },
                        )
                    }
                }
                if (rulesOpen) Text(game.rules, style = Type.callout, color = c.secondary, modifier = Modifier.padding(top = 12.dp))
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Challenge $pName", onClick = { vm.startMatch(game, 1) { nav.started(it) } }, loading = vm.busy, color = Color(0xFFF6F2FF))
                Text("You play first, then $pName plays the very same round. The higher score wins.", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 8.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }

        // ---------- running matches of this game
        if (active.isNotEmpty()) {
            item { SectionHeader("In progress") }
            items(active.size, key = { active[it].id }) { i ->
                MatchCard(active[i], me, partner, onPlay = { r -> nav.play(active[i], r) }, onCancel = { nav.cancel(active[i]) }, showGame = false)
            }
        }

        // ---------- head to head in this game
        item { SectionHeader("Head to head") }
        item {
            GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                val lead = when { mw > tw -> "You lead by ${mw - tw}"; tw > mw -> "$pName leads by ${tw - mw}"; mw + tw == 0 -> "No finished matches yet"; else -> "All square" }
                VersusHero(me, partner, mw, tw, 0, "match wins", footer = lead)
            }
        }
        item {
            GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), title = "Side by side", sub = "You on the left, $pName on the right") {
                fun f(v: Int?) = v?.toFloat() ?: 0f
                val avgMe = mb?.takeIf { it.plays > 0 }?.let { it.total / it.plays }
                val avgThem = tb?.takeIf { it.plays > 0 }?.let { it.total / it.plays }
                DuelBar("best score", f(mb?.best), f(tb?.best), maxOf(f(mb?.best), f(tb?.best)), c.me, c.partner, mb?.best?.let { fmt(it) } ?: "–", tb?.best?.let { fmt(it) } ?: "–")
                DuelBar("average score", f(avgMe), f(avgThem), maxOf(f(avgMe), f(avgThem)), c.me, c.partner, avgMe?.let { fmt(it) } ?: "–", avgThem?.let { fmt(it) } ?: "–")
                DuelBar("rounds played", f(mb?.plays), f(tb?.plays), maxOf(f(mb?.plays), f(tb?.plays)), c.me, c.partner, "${mb?.plays ?: 0}", "${tb?.plays ?: 0}")
                if ((mp?.n ?: 0) > 0 || (tp?.n ?: 0) > 0) {
                    val m1 = listOfNotNull(mp?.bestMs, tp?.bestMs, mp?.avgMs, tp?.avgMs).maxOrNull()?.toFloat() ?: 1f
                    DuelBar("fastest touch", f(mp?.bestMs), f(tp?.bestMs), m1, c.me, c.partner, ms(mp?.bestMs), ms(tp?.bestMs))
                    DuelBar("average reaction", f(mp?.avgMs), f(tp?.avgMs), m1, c.me, c.partner, ms(mp?.avgMs), ms(tp?.avgMs))
                }
                fun accF(p: PerfRow?) = if (p == null || p.hits + p.misses == 0) 0f else p.hits.toFloat() / (p.hits + p.misses)
                if ((mp?.hits ?: 0) + (mp?.misses ?: 0) > 0 || (tp?.hits ?: 0) + (tp?.misses ?: 0) > 0) DuelBar("accuracy", accF(mp), accF(tp), 1f, c.me, c.partner, acc(mp), acc(tp))
            }
        }

        // ---------- your scores in this game, match by match
        val history = done.sortedBy { it.finishedAt }.takeLast(20)
        if (history.size >= 2) item {
            GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), title = "Scores over time", sub = "Your last ${history.size} matches of ${game.label}") {
                DuoLines(history.map { m -> m.scoreOf(1, me.id)?.toFloat() }, history.map { m -> m.scoreOf(1, partner.id)?.toFloat() }, c.me, c.partner)
                Spacer(Modifier.height(10.dp))
                ChartLegend("You" to c.me, pName to c.partner)
            }
        }

        // ---------- hall of fame: the top three get a podium
        if (highs.isNotEmpty()) {
            item { SectionHeader("Hall of fame") }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()) {
                    Podium(highs.take(3).map { Triple(if (it.userId == me.id) me else partner, it.score, it.at) }, me)
                    highs.drop(3).forEachIndexed { k, h ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${k + 4}", style = Type.footnote.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = c.tertiary, modifier = Modifier.width(28.dp))
                            Avatar(if (h.userId == me.id) me else partner, 24.dp, ring = if (h.userId == me.id) c.me else c.partner)
                            Spacer(Modifier.width(10.dp))
                            Text(if (h.userId == me.id) "You" else pName, style = Type.callout, color = c.secondary, modifier = Modifier.weight(1f))
                            Text(day(h.at), style = Type.footnote, color = c.tertiary)
                            Spacer(Modifier.width(12.dp))
                            Text(fmt(h.score), style = Type.callout.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = c.label)
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
    val c = T.colors
    val goals = g?.goals.orEmpty()
    val live = goals.filter { it.status != "settled" }.sortedBy { if (it.status == "won") 0 else 1 }
    val settled = goals.filter { it.status == "settled" }.sortedByDescending { it.settledAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item {
            PageHeader("Goals", "Something on the line", nav.back) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.card).pressable(onClick = nav.newGoal), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, "New goal", tint = c.label, modifier = Modifier.size(22.dp))
                }
            }
        }
        if (g == null) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = c.accent) } }
        else if (goals.isEmpty()) item {
            GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), padding = 20.dp) {
                Text("A race with stakes", style = Type.title, color = c.label)
                Text("Pick what the loser owes, like dinner or the dishes, and how many match wins or points it takes. Every game you play counts toward it.", style = Type.callout, color = c.secondary, modifier = Modifier.padding(top = 6.dp, bottom = 18.dp))
                RaceTrack(me, partner, 2, 1, 3)
                Spacer(Modifier.height(20.dp))
                PrimaryButton("Set your first goal", onClick = nav.newGoal, color = LitIvory)
            }
        }
        items(live.size, key = { live[it].id }) { i -> GoalCard(live[i], me, partner, vm) }
        if (settled.isNotEmpty()) {
            item { SectionHeader("Settled") }
            item {
                GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), padding = 16.dp) {
                    val pName = partner.displayName.substringBefore(' ')
                    settled.forEachIndexed { k, gl ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(if (gl.winnerId == me.id) me else partner, 26.dp, ring = if (gl.winnerId == me.id) c.me else c.partner)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(gl.title, style = Type.callout, color = c.label, maxLines = 1)
                                Text("${if (gl.winnerId == me.id) "You" else pName} won · paid ${day(gl.settledAt)}", style = Type.footnote, color = c.tertiary)
                            }
                        }
                        if (k < settled.lastIndex) androidx.compose.material3.HorizontalDivider(color = c.label.copy(alpha = .06f), modifier = Modifier.padding(start = 38.dp))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ overall stats
@Composable
private fun ChartCard(title: String, sub: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = T.colors
    val shape = RoundedCornerShape(24.dp)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(shape).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), shape).padding(18.dp)) {
        Text(title, style = Type.headline, color = c.label)
        if (sub != null) Text(sub, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
internal fun StatsPage(me: User, partner: User, stats: GameStats, g: GamesResponse?, nav: PlayNav) {
    val c = T.colors
    val pName = partner.displayName.substringBefore(' ')
    val my = stats.players.firstOrNull { it.userId == me.id } ?: PlayerStat(me.id)
    val th = stats.players.firstOrNull { it.userId == partner.id } ?: PlayerStat(partner.id)
    val done = g?.matches.orEmpty().filter { it.isDone }.sortedBy { it.finishedAt }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 130.dp)) {
        item { PageHeader("Stats", "You and $pName", nav.back) }

        // ---------- who's winning
        item {
            GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().staggerIn(0), padding = 20.dp) {
                val streak = when {
                    my.streak >= 2 -> "You have won ${my.streak} in a row"
                    th.streak >= 2 -> "$pName has won ${th.streak} in a row"
                    else -> "${stats.done} matches played" + if (stats.draws > 0) ", ${stats.draws} drawn" else ""
                }
                VersusHero(me, partner, my.wins, th.wins, stats.draws, "match wins", footer = streak)
                Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
                    Figure("${my.bestStreak}", "your best streak", Modifier.weight(1f))
                    Figure("${th.bestStreak}", "best streak for $pName", Modifier.weight(1f), align = Alignment.End)
                }
            }
        }

        // ---------- the race over time
        if (done.size >= 2) item {
            var a = 0f; var b = 0f
            val mine = done.map { m -> if (m.winnerId == me.id) a++; a }
            val theirs = done.map { m -> if (m.winnerId == partner.id) b++; b }
            GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().staggerIn(1), title = "The race", sub = "Match wins added up over your last ${done.size} matches") {
                DuoLines(mine, theirs, c.me, c.partner)
                Spacer(Modifier.height(10.dp))
                ChartLegend("You" to c.me, pName to c.partner)
            }
        }

        // ---------- every comparison in one language
        val pa = stats.perf.filter { it.userId == me.id }; val pb = stats.perf.filter { it.userId == partner.id }
        item {
            fun fastest(rows: List<PerfRow>) = rows.filter { it.bestMs > 0 }.minOfOrNull { it.bestMs }
            fun avg(rows: List<PerfRow>): Int? { val n = rows.sumOf { it.n }; return if (n == 0) null else (rows.sumOf { it.avgMs.toLong() * it.n } / n).toInt() }
            fun accuracy(rows: List<PerfRow>): Float? { val h = rows.sumOf { it.hits }; val m = rows.sumOf { it.misses }; return if (h + m == 0) null else h.toFloat() / (h + m) }
            GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().staggerIn(2), title = "Side by side", sub = "Shorter is faster for reaction times") {
                DuelBar("points", my.points.toFloat(), th.points.toFloat(), maxOf(my.points, th.points).toFloat(), c.me, c.partner, fmt(my.points), fmt(th.points))
                DuelBar("rounds played", my.rounds.toFloat(), th.rounds.toFloat(), maxOf(my.rounds, th.rounds).toFloat(), c.me, c.partner, "${my.rounds}", "${th.rounds}")
                if (pa.isNotEmpty() || pb.isNotEmpty()) {
                    val fa = fastest(pa); val fb = fastest(pb); val aa = avg(pa); val ab = avg(pb)
                    val maxMs = listOfNotNull(fa, fb, aa, ab).maxOrNull()?.toFloat() ?: 1f
                    DuelBar("fastest touch", (fa ?: 0).toFloat(), (fb ?: 0).toFloat(), maxMs, c.me, c.partner, ms(fa), ms(fb))
                    DuelBar("average reaction", (aa ?: 0).toFloat(), (ab ?: 0).toFloat(), maxMs, c.me, c.partner, ms(aa), ms(ab))
                    val xa = accuracy(pa); val xb = accuracy(pb)
                    DuelBar("accuracy", xa ?: 0f, xb ?: 0f, 1f, c.me, c.partner, xa?.let { "${(it * 100).toInt()}%" } ?: "–", xb?.let { "${(it * 100).toInt()}%" } ?: "–")
                }
            }
        }

        // ---------- by game: who wins what
        val played = MiniGame.playable.filter { gm -> stats.bests.any { it.game == gm.key } }
        item { SectionHeader("By game") }
        item {
            GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().staggerIn(3), padding = 16.dp) {
                if (played.isEmpty()) Text("Play a game to see it here", style = Type.callout, color = c.secondary)
                val wins = played.map { gm ->
                    Triple(gm, stats.gameWins.firstOrNull { it.game == gm.key && it.userId == me.id }?.n ?: 0, stats.gameWins.firstOrNull { it.game == gm.key && it.userId == partner.id }?.n ?: 0)
                }.sortedByDescending { it.second + it.third }
                wins.forEachIndexed { k, (gm, a, b) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable(haptic = false) { nav.open("game:${gm.key}") }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        GameMark(gm, 30.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(gm.label, style = Type.callout, color = c.label, maxLines = 1)
                            SplitBar(a, b, 0, c.me, c.partner, Modifier.padding(top = 6.dp), height = 4.dp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Text("$a – $b", style = Type.footnote.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = c.secondary, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
                    }
                    if (k < wins.lastIndex) androidx.compose.material3.HorizontalDivider(color = c.label.copy(alpha = .06f), modifier = Modifier.padding(start = 42.dp))
                }
            }
        }
    }
}

/** The top three scores standing on a small podium: first in the middle and tallest, lit gold. */
@Composable
internal fun Podium(top: List<Triple<User, Int, Long>>, me: User) {
    val c = T.colors
    val order = listOf(1, 0, 2).filter { it < top.size }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        order.forEach { i ->
            val (u, score, at) = top[i]
            val h = when (i) { 0 -> 92.dp; 1 -> 66.dp; else -> 50.dp }
            val g = grow(1f, 700, i * 90)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(u, if (i == 0) 40.dp else 32.dp, ring = if (u.id == me.id) c.me else c.partner)
                Text(fmt(score), style = Type.headline.copy(fontFeatureSettings = "tnum"), color = if (i == 0) c.special else c.label, modifier = Modifier.padding(top = 6.dp))
                Text(day(at), style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
                Box(
                    Modifier.padding(top = 8.dp).fillMaxWidth().height(h * g).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf((if (i == 0) c.special else Color.White).copy(alpha = if (i == 0) .30f else .10f), Color.Transparent))),
                    contentAlignment = Alignment.TopCenter,
                ) { Text("${i + 1}", style = Type.title, color = if (i == 0) c.special else c.secondary, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

@Composable
private fun StatFigure(modifier: Modifier, value: String, label: String, color: Color) {
    val c = T.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Type.title.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"), color = color, maxLines = 1)
        Text(label, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, maxLines = 1)
    }
}
