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
                    }
                }
                Phase.Done -> Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    Text(game.emoji, fontSize = 56.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Time!", style = Type.title, color = c.secondary)
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
