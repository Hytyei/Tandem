package app.tandem.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandem.data.MiniGame
import app.tandem.ui.components.PrimaryButton
import app.tandem.ui.components.pressable
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Where a round is: read the rules, 3-2-1, play, then send the score. */
private enum class Phase { Intro, Count, Play, Done }

/**
 * One timed round of a minigame, full screen. Both partners get the same [seed], so they play the exact same round.
 * [onFinished] is called once, with the score, when the player taps "Send score". Leaving mid-round counts as 0.
 * [beat] is the partner's score for this round if they already played it.
 */
@Composable
fun RoundRunnerDialog(
    game: MiniGame, seed: Long, round: Int, rounds: Int, partnerName: String, beat: Int?, sending: Boolean,
    onFinished: (Int) -> Unit, onClose: () -> Unit,
) {
    val c = T.colors
    var phase by remember { mutableStateOf(Phase.Intro) }
    var score by remember { mutableIntStateOf(0) }
    var askQuit by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            when (phase) {
                Phase.Play, Phase.Count -> askQuit = true
                Phase.Intro -> onClose()
                Phase.Done -> if (!sending) onFinished(score)
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(c.sky)).systemBarsPadding()) {
            when (phase) {
                Phase.Intro -> Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    Text(game.emoji, fontSize = 72.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(game.label, style = Type.largeTitle, color = c.label, textAlign = TextAlign.Center)
                    Text(if (rounds > 1) "Round $round of $rounds" else "Single round", style = Type.callout, color = c.secondary)
                    Spacer(Modifier.height(18.dp))
                    Text(game.rules, style = Type.body, color = c.label, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (beat != null) "$partnerName scored $beat. Beat it!" else "You go first this round.",
                        style = Type.headline, color = c.accent, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    PrimaryButton("Ready", onClick = { phase = Phase.Count })
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onClose) { Text("Not now", color = c.secondary) }
                }
                Phase.Count -> Countdown { phase = Phase.Play }
                Phase.Play -> {
                    val finish: (Int) -> Unit = { s -> score = s; phase = Phase.Done }
                    when (game) {
                        MiniGame.Heart -> HeartCatchGame(seed, finish)
                        MiniGame.Memory -> MemoryGame(seed, finish)
                        MiniGame.Sums -> MathGame(seed, finish)
                        MiniGame.Draw -> QuickDrawGame(seed, finish)
                        MiniGame.Sequence -> SequenceGame(seed, finish)
                        MiniGame.Code -> CodeBreakerGame(seed, finish)
                        MiniGame.Lights -> LightsOutGame(seed, finish)
                        MiniGame.Slide -> SlidePuzzleGame(seed, finish)
                    }
                }
                Phase.Done -> Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    Text(game.emoji, fontSize = 56.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(game.doneLabel, style = Type.title, color = c.secondary)
                    Text("$score", style = Type.numberHuge, color = c.accent)
                    Text(game.unit, style = Type.callout, color = c.secondary)
                    if (beat != null) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            when {
                                score > beat -> "That beats $partnerName's $beat"
                                score == beat -> "A tie with $partnerName's $beat"
                                else -> "$partnerName scored $beat"
                            },
                            style = Type.headline, color = c.label, textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    PrimaryButton("Send score", onClick = { onFinished(score) }, loading = sending)
                }
            }
        }
    }

    if (askQuit) AlertDialog(
        onDismissRequest = { askQuit = false }, containerColor = c.sheet,
        title = { Text("Quit this round?") },
        text = { Text("Leaving now counts as a score of 0 for this round.") },
        confirmButton = { TextButton(onClick = { askQuit = false; score = 0; phase = Phase.Done }) { Text("Quit", color = c.danger) } },
        dismissButton = { TextButton(onClick = { askQuit = false }) { Text("Keep playing", color = c.secondary) } },
    )
}

@Composable
private fun Countdown(onDone: () -> Unit) {
    val c = T.colors
    var n by remember { mutableIntStateOf(3) }
    val up by rememberUpdatedState(onDone)
    LaunchedEffect(Unit) { for (i in 3 downTo 1) { n = i; delay(800) }; up() }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$n", style = Type.numberHuge.copy(fontSize = 120.sp, lineHeight = 120.sp), color = c.accent)
    }
}

/** Milliseconds since the round started, ticking every frame while [running]; [onTimeUp] fires once at [limitMs]. */
@Composable
private fun rememberClock(running: Boolean, limitMs: Long, onTimeUp: () -> Unit): Long {
    var elapsed by remember { mutableLongStateOf(0L) }
    val up by rememberUpdatedState(onTimeUp)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var start = -1L
        while (true) {
            val t = withFrameMillis { it }
            if (start < 0) start = t
            elapsed = t - start
            if (elapsed >= limitMs) { elapsed = limitMs; up(); break }
        }
    }
    return elapsed
}

@Composable
private fun Hud(leftMs: Long, totalMs: Long, right: String) {
    val c = T.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⏱ ${(leftMs + 999) / 1000}s", style = Type.headline, color = c.label, modifier = Modifier.weight(1f))
            Text(right, style = Type.headline, color = c.accent)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (leftMs.toFloat() / totalMs).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = c.accent, trackColor = c.separator,
        )
    }
}

// ------------------------------------------------------------------ Heart Catch
private class HeartSpawn(val t: Long, val x: Float, val y: Float, val life: Long)

@Composable
private fun HeartCatchGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 30_000L
    val spawns = remember(seed) {
        val rnd = Random(seed)
        buildList {
            var t = 700L
            while (t < total - 500) {
                add(HeartSpawn(t, .08f + rnd.nextFloat() * .84f, .06f + rnd.nextFloat() * .88f, (1300 - t / 30).coerceAtLeast(850)))
                t += (780 - t / 60).coerceAtLeast(420)
            }
        }
    }
    var hits by remember { mutableStateOf(setOf<Int>()) }
    val elapsed = rememberClock(true, total) { onDone(hits.size) }
    val c = T.colors
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, "💗 ${hits.size}")
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            spawns.forEachIndexed { i, sp ->
                if (i !in hits && elapsed >= sp.t && elapsed < sp.t + sp.life) {
                    val age = elapsed - sp.t
                    val pop = (0.5f + age / 150f).coerceAtMost(1f)
                    val fade = ((sp.t + sp.life - elapsed) / 250f).coerceIn(0f, 1f)
                    Box(
                        Modifier.offset(x = maxWidth * sp.x - 32.dp, y = maxHeight * sp.y - 32.dp).size(64.dp)
                            .graphicsLayer { scaleX = pop; scaleY = pop; alpha = fade }
                            .pointerInput(i) { detectTapGestures(onPress = { hits = hits + i }) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Favorite, "Heart", tint = c.accent, modifier = Modifier.size(56.dp)) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Memory Match
private val MEMORY_FACES = listOf("🍓", "🌙", "🎈", "🐙", "🍕", "🦋", "🌵", "🎧", "🔮", "🍩", "🐳", "⭐")

@Composable
private fun MemoryGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 60_000L
    val faces = remember(seed) {
        val r = Random(seed)
        val six = MEMORY_FACES.shuffled(r).take(6)
        (six + six).shuffled(r)
    }
    var open by remember { mutableStateOf(listOf<Int>()) }
    var matched by remember { mutableStateOf(setOf<Int>()) }
    var mistakes by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    fun scoreFor(leftMs: Long): Int {
        val bonus = if (matched.size == faces.size) (leftMs / 1000).toInt() * 5 else 0
        return (matched.size / 2 * 100 + bonus - mistakes * 10).coerceAtLeast(0)
    }
    val elapsed = rememberClock(!finished, total) { finished = true; onDone(scoreFor(0)) }
    LaunchedEffect(open) {
        if (open.size == 2) {
            delay(550)
            if (faces[open[0]] == faces[open[1]]) matched = matched + open else mistakes++
            open = emptyList()
        }
    }
    LaunchedEffect(matched.size) {
        if (matched.size == faces.size && !finished) { finished = true; delay(450); onDone(scoreFor(total - elapsed)) }
    }
    val c = T.colors
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, "${matched.size / 2}/6 pairs · ✗ $mistakes")
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            faces.indices.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { i ->
                        val shown = i in open || i in matched
                        val shape = RoundedCornerShape(18.dp)
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(shape)
                                .then(
                                    if (i in matched) Modifier.background(c.accent.copy(alpha = .16f))
                                    else if (shown) Modifier.background(c.cardRaised)
                                    else Modifier.background(Brush.linearGradient(listOf(c.me, c.accent)))
                                )
                                .border(1.dp, if (i in matched) c.accent else Color.Transparent, shape)
                                .clickable(remember { MutableInteractionSource() }, indication = null) {
                                    if (!finished && !shown && open.size < 2) open = open + i
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (shown) faces[i] else "?", fontSize = if (shown) 44.sp else 30.sp, color = Color.White) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Math Sprint
private class Sum(val text: String, val options: List<Int>, val answerIdx: Int)

private fun makeSums(seed: Long): List<Sum> {
    val rnd = Random(seed)
    return List(140) {
        val (text, answer) = when (rnd.nextInt(3)) {
            0 -> { val a = rnd.nextInt(2, 45); val b = rnd.nextInt(2, 45); "$a + $b" to a + b }
            1 -> { val a = rnd.nextInt(20, 90); val b = rnd.nextInt(2, a); "$a − $b" to a - b }
            else -> { val a = rnd.nextInt(2, 13); val b = rnd.nextInt(2, 13); "$a × $b" to a * b }
        }
        val opts = mutableSetOf(answer)
        while (opts.size < 4) { val d = answer + rnd.nextInt(-10, 11); if (d >= 0) opts += d }
        val list = opts.toList().shuffled(rnd)
        Sum(text, list, list.indexOf(answer))
    }
}

@Composable
private fun MathGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 45_000L
    val sums = remember(seed) { makeSums(seed) }
    var idx by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf<Boolean?>(null) }
    fun score() = (right * 10 - wrong * 5).coerceAtLeast(0)
    val elapsed = rememberClock(true, total) { onDone(score()) }
    val c = T.colors
    val sum = sums[idx.coerceAtMost(sums.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, "${score()} pts")
        Spacer(Modifier.weight(1f))
        Text(sum.text, style = Type.numberHuge.copy(fontSize = 64.sp), color = c.label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(
            when (last) { true -> "✓ Nice"; false -> "✗ Oops"; null -> " " },
            style = Type.headline, color = if (last == true) c.accent else c.danger, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sum.options.chunked(2).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { k, v ->
                        val optIdx = r * 2 + k
                        Box(
                            Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(22.dp)).background(c.cardRaised)
                                .border(1.dp, c.separator, RoundedCornerShape(22.dp))
                                .pressable(haptic = false) {
                                    if (optIdx == sum.answerIdx) { right++; last = true } else { wrong++; last = false }
                                    idx++
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text("$v", style = Type.title.copy(fontSize = 30.sp), color = c.label) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Quick Draw
private enum class DrawState { Waiting, Ready, Early, Result }

@Composable
private fun QuickDrawGame(seed: Long, onDone: (Int) -> Unit) {
    val delays = remember(seed) { val r = Random(seed); List(5) { 1400L + r.nextInt(0, 3200) } }
    var tryIdx by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf(DrawState.Waiting) }
    var readyAt by remember { mutableLongStateOf(0L) }
    var lastMs by remember { mutableIntStateOf(0) }
    var scores by remember { mutableStateOf(listOf<Int>()) }
    val up by rememberUpdatedState(onDone)

    LaunchedEffect(tryIdx, state) {
        when (state) {
            DrawState.Waiting -> { delay(delays[tryIdx]); readyAt = SystemClock.elapsedRealtime(); state = DrawState.Ready }
            DrawState.Early, DrawState.Result -> {
                delay(1100)
                if (scores.size >= 5) up(scores.sum()) else { tryIdx++; state = DrawState.Waiting }
            }
            DrawState.Ready -> {}
        }
    }
    fun tap() {
        when (state) {
            DrawState.Waiting -> { scores = scores + 0; state = DrawState.Early }
            DrawState.Ready -> {
                lastMs = (SystemClock.elapsedRealtime() - readyAt).toInt()
                scores = scores + (600 - lastMs).coerceAtLeast(0)
                state = DrawState.Result
            }
            else -> {}
        }
    }
    val c = T.colors
    val bg by animateFloatAsState(if (state == DrawState.Ready) 1f else 0f, label = "drawBg")
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.lerp(c.sheet, Color(0xFFFFC857), bg))
            .pointerInput(Unit) { detectTapGestures(onPress = { tap() }) },
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Try ${(tryIdx + 1).coerceAtMost(5)} of 5", style = Type.headline, color = if (state == DrawState.Ready) Color(0xFF3A2A00) else c.label, modifier = Modifier.padding(top = 24.dp))
            Spacer(Modifier.weight(1f))
            Text(
                when (state) {
                    DrawState.Waiting -> "Wait for gold…"
                    DrawState.Ready -> "TAP!"
                    DrawState.Early -> "Too early! 0 points"
                    DrawState.Result -> "$lastMs ms"
                },
                style = Type.largeTitle.copy(fontSize = 40.sp, lineHeight = 46.sp), textAlign = TextAlign.Center,
                color = if (state == DrawState.Ready) Color(0xFF3A2A00) else c.label,
            )
            if (state == DrawState.Result) Text("+${scores.lastOrNull() ?: 0}", style = Type.title, color = Color(0xFF3A2A00))
            Spacer(Modifier.weight(1f))
            Text("Total ${scores.sum()}", style = Type.headline, color = if (state == DrawState.Ready) Color(0xFF3A2A00) else c.accent, modifier = Modifier.padding(bottom = 32.dp))
        }
    }
}


// ------------------------------------------------------------------ Next Number
private class SeqPuzzle(val shown: List<Int>, val options: List<Int>, val answerIdx: Int)

private fun makeSequences(seed: Long): List<SeqPuzzle> {
    val rnd = Random(seed)
    return List(60) { n ->
        // later puzzles draw from the harder rules
        val kind = rnd.nextInt(if (n < 4) 3 else if (n < 10) 6 else 9)
        val seq: List<Int> = when (kind) {
            0 -> { val a = rnd.nextInt(1, 20); val d = rnd.nextInt(2, 9); List(6) { a + d * it } }
            1 -> { val a = rnd.nextInt(1, 5); val r = rnd.nextInt(2, 4); List(6) { var v = a; repeat(it) { v *= r }; v } }
            2 -> { val a = rnd.nextInt(30, 90); val d = rnd.nextInt(3, 10); List(6) { a - d * it } }
            3 -> { val o = rnd.nextInt(1, 6); List(6) { (it + o) * (it + o) } }
            4 -> { val a = rnd.nextInt(1, 6); val b = rnd.nextInt(1, 6); val l = mutableListOf(a, b); while (l.size < 6) l += l[l.size - 1] + l[l.size - 2]; l }
            5 -> { val a = rnd.nextInt(1, 10); val d1 = rnd.nextInt(2, 6); val d2 = rnd.nextInt(1, 5); val l = mutableListOf(a); for (i in 1 until 6) l += l.last() + if (i % 2 == 1) d1 else -d2; l }
            6 -> { val a = rnd.nextInt(1, 8); var d = rnd.nextInt(1, 4); val st = rnd.nextInt(1, 3); val l = mutableListOf(a); for (i in 1 until 6) { l += l.last() + d; d += st }; l }
            7 -> { val a = rnd.nextInt(2, 9); val m = rnd.nextInt(2, 4); val k = rnd.nextInt(1, 6); val l = mutableListOf(a); for (i in 1 until 6) l += l.last() * m - k; l }
            else -> { val o = rnd.nextInt(1, 5); List(6) { val x = it + o; x * x * x } }
        }
        val answer = seq[5]
        val opts = mutableSetOf(answer)
        while (opts.size < 4) {
            val d = answer + (rnd.nextInt(1, 9) * if (rnd.nextBoolean()) 1 else -1)
            if (d > 0 && d != answer) opts += d
        }
        val list = opts.toList().shuffled(rnd)
        SeqPuzzle(seq.take(5), list, list.indexOf(answer))
    }
}

@Composable
private fun SequenceGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 60_000L
    val puzzles = remember(seed) { makeSequences(seed) }
    var idx by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf<Boolean?>(null) }
    fun score() = (right * 10 - wrong * 5).coerceIn(0, 800)
    val elapsed = rememberClock(true, total) { onDone(score()) }
    val c = T.colors
    val pz = puzzles[idx.coerceAtMost(puzzles.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, "${score()} pts")
        Spacer(Modifier.weight(1f))
        Text("What comes next?", style = Type.callout, color = c.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            pz.shown.joinToString("  ·  ") + "  ·  ?", style = Type.title.copy(fontSize = 28.sp), color = c.label,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), textAlign = TextAlign.Center,
        )
        Text(
            when (last) { true -> "✓ Yes"; false -> "✗ Not that one"; null -> " " },
            style = Type.headline, color = if (last == true) c.accent else c.danger, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            pz.options.chunked(2).forEachIndexed { r, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEachIndexed { k, v ->
                        val optIdx = r * 2 + k
                        Box(
                            Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(22.dp)).background(c.cardRaised)
                                .border(1.dp, c.separator, RoundedCornerShape(22.dp))
                                .pressable(haptic = false) {
                                    if (optIdx == pz.answerIdx) { right++; last = true } else { wrong++; last = false }
                                    idx++
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text("$v", style = Type.title.copy(fontSize = 30.sp), color = c.label) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Code Breaker
private val CODE_COLORS = listOf(Color(0xFFF2476F), Color(0xFFF59E0B), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFF14B8A6))

private class CodeGuess(val pegs: List<Int>, val exact: Int, val near: Int)

@Composable
private fun CodeBreakerGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 150_000L
    val maxGuesses = 8
    val secret = remember(seed) { val r = Random(seed); List(4) { r.nextInt(CODE_COLORS.size) } }
    var current by remember { mutableStateOf(listOf<Int>()) }
    var guesses by remember { mutableStateOf(listOf<CodeGuess>()) }
    var finished by remember { mutableStateOf(false) }
    fun scoreFor(leftMs: Long, solved: Boolean): Int {
        val best = guesses.maxOfOrNull { it.exact } ?: 0
        return if (solved) (400 + (maxGuesses - guesses.size) * 50 + (leftMs / 1000) * 1.5).toInt().coerceAtMost(1000) else best * 40
    }
    val elapsed = rememberClock(!finished, total) { finished = true; onDone(scoreFor(0, false)) }
    val c = T.colors

    fun submit() {
        if (current.size != 4 || finished) return
        val exact = current.indices.count { current[it] == secret[it] }
        var common = 0
        for (col in CODE_COLORS.indices) common += minOf(current.count { it == col }, secret.count { it == col })
        guesses = guesses + CodeGuess(current, exact, common - exact)
        current = emptyList()
    }
    val solved = guesses.lastOrNull()?.exact == 4
    LaunchedEffect(guesses.size) {
        if (finished) return@LaunchedEffect
        if (solved || guesses.size >= maxGuesses) { finished = true; delay(700); onDone(scoreFor(total - elapsed, solved)) }
    }

    @Composable
    fun Peg(col: Int?, size: Int, onClick: (() -> Unit)? = null) {
        Box(
            Modifier.size(size.dp).clip(RoundedCornerShape(50))
                .background(if (col == null) c.separator.copy(alpha = .5f) else CODE_COLORS[col])
                .then(if (onClick != null) Modifier.pressable(haptic = false) { onClick() } else Modifier),
        )
    }

    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, "${guesses.size}/$maxGuesses guesses")
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(maxGuesses) { row ->
                val g = guesses.getOrNull(row)
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${row + 1}", style = Type.caption, color = c.secondary, modifier = Modifier.width(16.dp))
                    repeat(4) { k -> Peg(g?.pegs?.get(k), 34) }
                    Spacer(Modifier.weight(1f))
                    if (g != null) Text("● ${g.exact}   ○ ${g.near}", style = Type.headline, color = c.label)
                }
            }
        }
        Text("● right colour, right place   ○ right colour, wrong place", style = Type.caption, color = c.secondary, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { k -> Peg(current.getOrNull(k), 46) { if (current.size > k) current = current.filterIndexed { i, _ -> i != k } } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            CODE_COLORS.indices.forEach { col -> Peg(col, 46) { if (!finished && current.size < 4) current = current + col } }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { current = emptyList() }, modifier = Modifier.weight(1f)) { Text("Clear", color = c.secondary) }
            Box(Modifier.weight(2f)) { PrimaryButton("Guess", onClick = { submit() }, enabled = current.size == 4 && !finished) }
        }
    }
}

// ------------------------------------------------------------------ Lights Out
@Composable
private fun LightsOutGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 90_000L
    val n = 4
    val start = remember(seed) {
        val r = Random(seed)
        val b = BooleanArray(n * n)
        fun press(i: Int, a: BooleanArray) {
            val x = i % n; val y = i / n
            a[i] = !a[i]
            if (x > 0) a[i - 1] = !a[i - 1]; if (x < n - 1) a[i + 1] = !a[i + 1]
            if (y > 0) a[i - n] = !a[i - n]; if (y < n - 1) a[i + n] = !a[i + n]
        }
        // scramble with real presses, so it is always solvable; keep going until enough lights are on
        var presses = 0
        while (presses < 6 || b.count { it } < 5) { press(r.nextInt(n * n), b); presses++ }
        b.toList()
    }
    var lights by remember { mutableStateOf(start) }
    var moves by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    fun scoreFor(leftMs: Long): Int {
        val off = lights.count { !it }
        return if (off == n * n) (300 + (leftMs / 1000) * 5 + maxOf(0, 12 - moves) * 20).toInt().coerceAtMost(1000) else off * 10
    }
    val elapsed = rememberClock(!finished, total) { finished = true; onDone(scoreFor(0)) }
    val c = T.colors
    fun tap(i: Int) {
        if (finished) return
        val x = i % n; val y = i / n
        val a = lights.toMutableList()
        fun flip(j: Int) { a[j] = !a[j] }
        flip(i); if (x > 0) flip(i - 1); if (x < n - 1) flip(i + 1); if (y > 0) flip(i - n); if (y < n - 1) flip(i + n)
        lights = a; moves++
        if (a.none { it }) { finished = true }
    }
    LaunchedEffect(finished, lights) {
        if (finished && lights.none { it }) { delay(600); onDone(scoreFor(total - elapsed)) }
    }
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, "$moves taps · ${lights.count { it }} on")
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(n) { y ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(n) { x ->
                        val i = y * n + x
                        val on = lights[i]
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(18.dp))
                                .background(if (on) Brush.linearGradient(listOf(Color(0xFFFFE08A), Color(0xFFF5B301))) else Brush.linearGradient(listOf(c.cardRaised, c.cardRaised)))
                                .border(1.dp, if (on) Color(0xFFF5B301) else c.separator, RoundedCornerShape(18.dp))
                                .pressable(haptic = false) { tap(i) },
                            contentAlignment = Alignment.Center,
                        ) { if (on) Text("💡", fontSize = 28.sp) }
                    }
                }
            }
        }
        Text("Tap a light: it and its neighbours flip", style = Type.callout, color = c.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
    }
}

// ------------------------------------------------------------------ Slide Puzzle
@Composable
private fun SlidePuzzleGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 120_000L
    val n = 3
    val start = remember(seed) {
        val r = Random(seed)
        val t = IntArray(n * n) { (it + 1) % (n * n) }          // 1..8 and 0 (the gap) last
        var gap = n * n - 1
        var prev = -1
        var steps = 0
        // shuffle by legal moves only, so it can always be solved
        while (steps < 70 || t.indices.all { t[it] == (it + 1) % (n * n) }) {
            val x = gap % n; val y = gap / n
            val nb = buildList { if (x > 0) add(gap - 1); if (x < n - 1) add(gap + 1); if (y > 0) add(gap - n); if (y < n - 1) add(gap + n) }.filter { it != prev }
            val to = nb[r.nextInt(nb.size)]
            t[gap] = t[to]; t[to] = 0; prev = gap; gap = to; steps++
        }
        t.toList()
    }
    var tiles by remember { mutableStateOf(start) }
    var moves by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    fun inPlace(a: List<Int>) = a.indices.count { a[it] != 0 && a[it] == it + 1 }
    fun solved(a: List<Int>) = inPlace(a) == n * n - 1
    fun scoreFor(leftMs: Long): Int =
        if (solved(tiles)) (300 + (leftMs / 1000) * 4 + maxOf(0, 60 - moves) * 3).toInt().coerceAtMost(1000) else inPlace(tiles) * 15
    val elapsed = rememberClock(!finished, total) { finished = true; onDone(scoreFor(0)) }
    val c = T.colors
    fun tap(i: Int) {
        if (finished) return
        val g = tiles.indexOf(0)
        val adjacent = (i / n == g / n && kotlin.math.abs(i - g) == 1) || (i % n == g % n && kotlin.math.abs(i - g) == n)
        if (!adjacent) return
        val a = tiles.toMutableList(); a[g] = a[i]; a[i] = 0
        tiles = a; moves++
        if (solved(a)) finished = true
    }
    LaunchedEffect(finished, tiles) {
        if (finished && solved(tiles)) { delay(600); onDone(scoreFor(total - elapsed)) }
    }
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, "$moves moves · ${inPlace(tiles)}/8 placed")
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(n) { y ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(n) { x ->
                        val i = y * n + x
                        val v = tiles[i]
                        val ok = v != 0 && v == i + 1
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(18.dp))
                                .then(
                                    if (v == 0) Modifier.background(c.separator.copy(alpha = .25f))
                                    else Modifier.background(if (ok) Brush.linearGradient(listOf(c.accent.copy(alpha = .85f), c.me)) else Brush.linearGradient(listOf(c.cardRaised, c.cardRaised)))
                                        .border(1.dp, if (ok) c.accent else c.separator, RoundedCornerShape(18.dp))
                                        .pressable(haptic = false) { tap(i) }
                                ),
                            contentAlignment = Alignment.Center,
                        ) { if (v != 0) Text("$v", style = Type.title.copy(fontSize = 34.sp), color = if (ok) Color.White else c.label) }
                    }
                }
            }
        }
        Text("Slide the tiles into order: 1 2 3 / 4 5 6 / 7 8", style = Type.callout, color = c.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
    }
}
