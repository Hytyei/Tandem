package app.tandem.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.tandem.data.MiniGame
import app.tandem.data.PerfBody
import app.tandem.ui.components.FxKind
import app.tandem.ui.components.FxLayer
import app.tandem.ui.components.LocalFx
import app.tandem.ui.components.PrimaryButton
import app.tandem.ui.components.pressable
import app.tandem.ui.components.rememberFx
import app.tandem.ui.components.touchFx
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/** Where a round is: read the rules, 3-2-1, play, then send the score. */
private enum class Phase { Intro, Count, Play, Done }

internal val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val Ivory = Color(0xFFF6F2FF)

/**
 * One timed round of a minigame, full screen. Both partners get the same [seed], so they play the exact same round.
 * [onFinished] is called once, with the score, when the player taps "Send score". Leaving mid-round counts as 0.
 * [beat] is the partner's score for this round if they already played it.
 */
@Composable
fun RoundRunnerDialog(
    game: MiniGame, seed: Long, round: Int, rounds: Int, partnerName: String, beat: Int?, sending: Boolean,
    onFinished: (Int, PerfBody?) -> Unit, onClose: () -> Unit,
) {
    val c = T.colors
    val fx = rememberFx()
    fx.tint = game.light
    val perf = remember { PerfLog() }
    var phase by remember { mutableStateOf(Phase.Intro) }
    var score by remember { mutableIntStateOf(0) }
    var askQuit by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = {
            when (phase) {
                Phase.Play, Phase.Count -> askQuit = true
                Phase.Intro -> onClose()
                Phase.Done -> if (!sending) onFinished(score, perf.toBody())
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false),
    ) {
        WithSkin(game) {
        val c = T.colors
        RoundBackdrop(game, fx, Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().systemBarsPadding().graphicsLayer { translationX = fx.shakeX; translationY = fx.shakeY }) {
            when (phase) {
                Phase.Intro -> Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.weight(1f))
                    GameMark(game, 96.dp, Modifier.enterRise(0))
                    Spacer(Modifier.height(24.dp))
                    Text(game.label, style = Type.largeTitle, color = c.label, textAlign = TextAlign.Center, modifier = Modifier.enterRise(1))
                    Text(if (rounds > 1) "Round $round of $rounds" else "One round", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 4.dp).enterRise(1))
                    Spacer(Modifier.height(24.dp))
                    Text(game.rules, style = Type.callout, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.enterRise(2))
                    Spacer(Modifier.height(24.dp))
                    Text(
                        if (beat != null) "$partnerName scored $beat. Beat it" else "You go first this round",
                        style = Type.headline, color = c.accent, textAlign = TextAlign.Center, modifier = Modifier.enterRise(3),
                    )
                    Spacer(Modifier.weight(1f))
                    PrimaryButton("Ready", onClick = { phase = Phase.Count }, color = Ivory)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onClose) { Text("Not now", color = c.secondary) }
                }
                Phase.Count -> Countdown { fx.burstAt(FxKind.Ring, fx.w / 2f, fx.h / 2f); phase = Phase.Play }
                Phase.Play -> {
                    val finish: (Int) -> Unit = { s -> score = s; phase = Phase.Done }
                    CompositionLocalProvider(LocalFx provides fx, LocalPerf provides perf, LocalBeat provides beat, LocalRival provides partnerName) {
                    Box(Modifier.fillMaxSize().touchFx(fx)) {
                    when (game) {
                        MiniGame.Heart -> HeartCatchGame(seed, finish)
                        MiniGame.Memory -> MemoryGame(seed, finish)
                        MiniGame.Sums -> MathGame(seed, finish)
                        MiniGame.Draw -> QuickDrawGame(seed, finish)
                        MiniGame.Sequence -> SequenceGame(seed, finish)
                        MiniGame.Odd -> OddOneOutGame(seed, finish)
                        MiniGame.Target -> TargetGame(seed, finish)
                        MiniGame.Echo -> EchoGame(seed, finish)
                        MiniGame.Code -> CodeBreakerGame(seed, finish)
                        MiniGame.Lights -> LightsOutGame(seed, finish)
                        MiniGame.Slide -> SlidePuzzleGame(seed, finish)
                        MiniGame.Mole -> WhackGame(seed, finish)
                        MiniGame.Stack -> StackGame(seed, finish)
                        MiniGame.Flap -> FlappyGame(seed, finish)
                        MiniGame.Stroop -> StroopGame(seed, finish)
                        MiniGame.Word -> WordGame(seed, finish)
                        MiniGame.Blackjack -> BlackjackGame(seed, finish)
                    }
                    }
                    }
                }
                Phase.Done -> DoneScreen(fx, game, score, beat, partnerName, perf, sending) { onFinished(score, perf.toBody()) }
            }
        }
        // the light sits above everything, so bursts never hide under a button
        FxLayer(fx, Modifier.fillMaxSize())
        }
        }
    }

    if (askQuit) AlertDialog(
        onDismissRequest = { askQuit = false }, containerColor = c.sheet,
        title = { Text("Quit this round?") },
        text = { Text("Leaving now counts as a score of 0 for this round.") },
        confirmButton = { TextButton(onClick = { askQuit = false; score = 0; perf.clear(); phase = Phase.Done }) { Text("Quit", color = c.danger) } },
        dismissButton = { TextButton(onClick = { askQuit = false }) { Text("Keep playing", color = c.secondary) } },
    )
}

@Composable
private fun DoneScreen(fx: app.tandem.ui.components.FxState, game: MiniGame, score: Int, beat: Int?, partnerName: String, perf: PerfLog, sending: Boolean, onSend: () -> Unit) {
    val c = T.colors
    val won = beat != null && score > beat
    // one burst when the result lands, gold when you beat your partner
    LaunchedEffect(Unit) { delay(650); if (score > 0) fx.burstAt(if (won) FxKind.Coins else FxKind.Confetti, fx.w / 2f, fx.h * .4f) }
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text(game.doneLabel, style = Type.title, color = c.secondary, modifier = Modifier.enterRise(0))
        TweenNumber(score, Type.numberHuge.copy(fontSize = 96.sp, lineHeight = 100.sp, fontFeatureSettings = "tnum"), c.label)
        Text(game.unit, style = Type.callout, color = c.secondary)
        if (beat != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    score > beat -> "That beats $partnerName's $beat"
                    score == beat -> "A tie with $partnerName's $beat"
                    else -> "$partnerName scored $beat"
                },
                style = Type.headline, color = if (won) Color(0xFFFFD58A) else c.label, textAlign = TextAlign.Center, modifier = Modifier.enterRise(2),
            )
        }
        if (perf.samples > 0 || perf.hits + perf.misses > 0) {
            Spacer(Modifier.height(24.dp))
            Row(Modifier.enterRise(3).clip(RoundedCornerShape(16.dp)).background(c.card).border(1.dp, c.label.copy(alpha = .08f), RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (perf.samples > 0) {
                    PerfStat("Fastest", "${perf.best} ms")
                    PerfStat("Average", "${perf.avg} ms")
                }
                if (perf.hits + perf.misses > 0) PerfStat("Accuracy", "${perf.hits * 100 / (perf.hits + perf.misses)}%")
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Send score", onClick = onSend, loading = sending, color = Ivory)
    }
}

@Composable
private fun PerfStat(label: String, value: String) {
    val c = T.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Type.headline.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"), color = c.label)
        Text(label, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary)
    }
}

/** 3, 2, 1: each number comes into focus out of a soft blur, holds, and drifts away. */
@Composable
private fun Countdown(onDone: () -> Unit) {
    val c = T.colors
    var n by remember { mutableIntStateOf(3) }
    val up by rememberUpdatedState(onDone)
    val beat = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        for (i in 3 downTo 1) {
            n = i; beat.snapTo(0f)
            beat.animateTo(1f, tween(720, easing = LinearEasing))
        }
        up()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val p = beat.value
        val inP = (p / .3f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) * (1f - it) }
        val outP = ((p - .7f) / .3f).coerceIn(0f, 1f)
        Canvas(Modifier.size(260.dp)) { glow(center, size.minDimension * .5f, c.accent, .28f * inP * (1f - outP)) }
        Text(
            "$n", style = Type.numberHuge.copy(fontSize = 120.sp, lineHeight = 120.sp), color = c.label,
            modifier = Modifier
                .blur(((1f - inP) * 10f + outP * 8f).dp)
                .graphicsLayer {
                    val sc = .92f + .08f * inP + .05f * outP
                    scaleX = sc; scaleY = sc; alpha = inP * (1f - outP)
                },
        )
    }
}

/** A number that counts up to [value] once, for the result screen. */
@Composable
private fun TweenNumber(value: Int, style: TextStyle, color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(900, easing = EaseOutStrong)) }
    Text("${anim.value.toInt()}", style = style, color = color)
}

/** One short fade-and-rise for intro content, staggered by [index]. */
@Composable
internal fun Modifier.enterRise(index: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        p.animateTo(1f, tween(420, easing = EaseOutStrong))
    }
    return graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 16.dp.toPx() }
}

/** Milliseconds since the round started, ticking every frame while [running]; [onTimeUp] fires once at [limitMs]. */
@Composable
internal fun rememberClock(running: Boolean, limitMs: Long, onTimeUp: () -> Unit): Long {
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

// ------------------------------------------------------------------ the top of a round

/**
 * The quiet bar over every round: a hairline of time left (when the round is timed), a small figure on the left,
 * your score large in the middle, and the number to beat on the right. The score gives a little pulse when it grows.
 */
@Composable
internal fun RoundHud(score: Int, leftLabel: String, leftValue: String, progress: Float? = null, low: Boolean = false, detail: String? = null) {
    val c = T.colors
    val beat = LocalBeat.current
    val rival = LocalRival.current
    val pulse = remember { Animatable(1f) }
    var last by remember { mutableIntStateOf(score) }
    LaunchedEffect(score) {
        if (score > last) { pulse.snapTo(1.14f); pulse.animateTo(1f, tween(220, easing = EaseOutStrong)) }
        last = score
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp)) {
        if (progress != null) {
            val t by rememberUpdatedState(progress)
            Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                val y = size.height / 2
                drawLine(Color.White.copy(alpha = .12f), Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
                val col = if (low) lerp(Color(0xFFFF9DB2), Color.White, .3f + .3f * sin(SystemClock.uptimeMillis() / 120f)) else Color(0xFFE9E2FF)
                if (t > 0f) drawLine(col, Offset(0f, y), Offset(size.width * t.coerceIn(0f, 1f), y), size.height, StrokeCap.Round)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
            HudFigure(leftLabel, leftValue, Alignment.Start, Modifier.weight(1f), if (low) Color(0xFFFFB3C2) else c.label)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$score", style = Type.numberHuge.copy(fontSize = 52.sp, lineHeight = 56.sp, fontFeatureSettings = "tnum"), color = c.label,
                    modifier = Modifier.graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
                )
                if (detail != null) Text(detail, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, maxLines = 1)
            }
            HudFigure(if (beat != null) (rival ?: "To beat") else "", beat?.toString() ?: "", Alignment.End, Modifier.weight(1f), c.label)
        }
    }
}

@Composable
private fun HudFigure(label: String, value: String, align: Alignment.Horizontal, modifier: Modifier, color: Color) {
    val c = T.colors
    Column(modifier, horizontalAlignment = align) {
        Text(label, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, maxLines = 1)
        Text(value, style = Type.callout.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"), color = color, maxLines = 1)
    }
}

/** [RoundHud] for a round against the clock. */
@Composable
internal fun Hud(leftMs: Long, totalMs: Long, score: Int, detail: String? = null) {
    val secs = (leftMs + 999) / 1000
    RoundHud(score, "Time", "%d:%02d".format(secs / 60, secs % 60), leftMs.toFloat() / totalMs, secs <= 5, detail)
}

/** How an answer button looks for a moment after a tap: lit for right, rose for wrong. */
internal enum class Lit { Idle, On, Good, Bad }

/**
 * A glass button for answers and tiles. It presses in under the finger and briefly lights up in the game's
 * colour (or rose) when [lit] says so.
 */
@Composable
internal fun GlassKey(modifier: Modifier, lit: Lit = Lit.Idle, radius: Dp = 20.dp, onTap: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val c = T.colors
    val rose = Color(0xFFFF7D96)
    val fill by animateColorAsState(
        when (lit) { Lit.Good -> c.accent.copy(alpha = .34f); Lit.On -> c.accent.copy(alpha = .22f); Lit.Bad -> rose.copy(alpha = .26f); Lit.Idle -> c.card },
        tween(if (lit == Lit.Idle) 260 else 60), label = "keyFill",
    )
    val edge by animateColorAsState(
        when (lit) { Lit.Good, Lit.On -> c.accent; Lit.Bad -> rose; Lit.Idle -> Color.White.copy(alpha = .10f) },
        tween(if (lit == Lit.Idle) 260 else 60), label = "keyEdge",
    )
    val shape = RoundedCornerShape(radius)
    Box(
        modifier.clip(shape).background(fill).border(1.dp, edge, shape).pressable(haptic = false, onClick = onTap),
        contentAlignment = Alignment.Center, content = content,
    )
}

/** Remembers which key was tapped and whether it was right, and clears it after a moment. */
@Stable
internal class KeyFlash {
    var idx by mutableIntStateOf(-1); private set
    var ok by mutableStateOf(true); private set
    var n by mutableIntStateOf(0); private set
    fun show(i: Int, right: Boolean) { idx = i; ok = right; n++ }
    fun clear() { idx = -1 }
    fun of(i: Int): Lit = if (i != idx) Lit.Idle else if (ok) Lit.Good else Lit.Bad
}

@Composable
internal fun rememberKeyFlash(): KeyFlash {
    val f = remember { KeyFlash() }
    LaunchedEffect(f.n) { if (f.n > 0) { delay(260); f.clear() } }
    return f
}

/** The question of an answer game. A new one slides up into place, so you can feel each answer land. */
@Composable
private fun Question(key: Any, content: @Composable () -> Unit) {
    AnimatedContent(
        key, label = "question",
        transitionSpec = {
            (fadeIn(tween(180)) + slideInVertically(tween(220, easing = EaseOutStrong)) { it / 4 }) togetherWith
                (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 5 })
        },
        modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center,
    ) { _ -> content() }
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
    var hits by remember { mutableStateOf(mapOf<Int, Long>()) }        // heart -> when it was caught (it swells and fades out)
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val elapsed = rememberClock(true, total) {
        val seen = spawns.count { it.t + 300 < total }
        repeat((seen - hits.size).coerceAtLeast(0)) { perf.miss() }
        onDone(hits.size)
    }
    val nowMs by rememberUpdatedState(elapsed)
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, hits.size, "hearts")
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            spawns.forEachIndexed { i, sp ->
                val caught = hits[i]
                val alive = caught == null && elapsed >= sp.t && elapsed < sp.t + sp.life
                val popping = caught != null && elapsed - caught < 260
                if (alive || popping) {
                    val age = elapsed - sp.t
                    val inP = (age / 260f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) * (1f - it) }
                    val left = sp.t + sp.life - elapsed
                    val k = if (caught != null) (elapsed - caught) / 260f else 0f
                    val alpha = if (caught != null) 1f - k else inP * (left / 300f).coerceIn(0f, 1f)
                    val sink = if (caught == null && left < 300) (1f - left / 300f) * 10f else 0f
                    Box(
                        // the whole heart always stays inside the play area
                        Modifier.offset(x = (maxWidth - 88.dp) * sp.x, y = (maxHeight - 88.dp) * sp.y).size(88.dp)
                            .graphicsLayer {
                                val sc = (.85f + .15f * inP) * (1f + .4f * k)
                                scaleX = sc; scaleY = sc; translationY = sink * density
                                rotationZ = 7f * sin((age + i * 377) / 600f)
                            }
                            .then(if (alive) Modifier.pointerInput(i) {
                                detectTapGestures(onPress = {
                                    if (i !in hits) {
                                        hits = hits + (i to nowMs); perf.hit(); perf.react((nowMs - sp.t).toInt())
                                        fx.burst(FxKind.Hearts, "+1")
                                    }
                                })
                            } else Modifier),
                    ) {
                        Canvas(Modifier.fillMaxSize()) { softHeart(center, size.minDimension * .6f, alpha = alpha) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Memory Match
/** Twelve faces, each a small lit shape in its own colour. Six are dealt, twice. */
private val MEMORY_COLORS = listOf(
    Color(0xFFFFD58A), Color(0xFFFFE9A8), Color(0xFFFF8FA3), Color(0xFF8DB6FF), Color(0xFF9BE3A8), Color(0xFFFFB86B),
    Color(0xFFC2A3FF), Color(0xFF8FE0E8), Color(0xFFFF9EC4), Color(0xFFFFD66E), Color(0xFF7FD8C8), Color(0xFFE79BFF),
)

private fun DrawScope.memoryFace(kind: Int, c: Offset, s: Float) {
    val col = MEMORY_COLORS[kind]
    glow(c, s * 1.5f, col, .35f)
    val line = Stroke(s * .16f, cap = StrokeCap.Round)
    when (kind) {
        0 -> { drawCircle(col, s * .55f, c); drawCircle(Color(0xFF2A2170), s * .5f, c + Offset(s * .26f, -s * .16f)) }          // moon
        1 -> drawPath(sparklePath(c, s * .7f), col)                                                                                 // star
        2 -> drawPath(heartPath(c + Offset(0f, s * .04f), s * .62f), col)                                                         // heart
        3 -> drawPath(Path().apply { moveTo(c.x, c.y - s * .7f); cubicTo(c.x + s * .6f, c.y, c.x + s * .5f, c.y + s * .6f, c.x, c.y + s * .6f); cubicTo(c.x - s * .5f, c.y + s * .6f, c.x - s * .6f, c.y, c.x, c.y - s * .7f); close() }, col) // drop
        4 -> drawPath(Path().apply { moveTo(c.x - s * .55f, c.y + s * .55f); quadraticTo(c.x - s * .55f, c.y - s * .55f, c.x + s * .55f, c.y - s * .55f); quadraticTo(c.x + s * .55f, c.y + s * .55f, c.x - s * .55f, c.y + s * .55f); close() }, col) // leaf
        5 -> { drawCircle(col, s * .32f, c); for (k in 0 until 8) { val a = k / 8f * TAU; drawLine(col, c + Offset(kotlin.math.cos(a), sin(a)) * s * .48f, c + Offset(kotlin.math.cos(a), sin(a)) * s * .66f, s * .1f, StrokeCap.Round) } } // sun
        6 -> { drawCircle(col, s * .36f, c); drawOval(col, c - Offset(s * .72f, s * .14f), Size(s * 1.44f, s * .28f), style = Stroke(s * .08f)) }                  // planet
        7 -> drawPath(Path().apply { moveTo(c.x, c.y - s * .62f); lineTo(c.x + s * .48f, c.y); lineTo(c.x, c.y + s * .62f); lineTo(c.x - s * .48f, c.y); close() }, col)  // diamond
        8 -> for (k in 0 until 5) { val a = k / 5f * TAU - 1.57f; drawCircle(col, s * .24f, c + Offset(kotlin.math.cos(a), sin(a)) * s * .32f) }                      // flower
        9 -> drawPath(Path().apply { moveTo(c.x + s * .12f, c.y - s * .68f); lineTo(c.x - s * .36f, c.y + s * .08f); lineTo(c.x - s * .02f, c.y + s * .08f); lineTo(c.x - s * .14f, c.y + s * .68f); lineTo(c.x + s * .38f, c.y - s * .12f); lineTo(c.x + s * .04f, c.y - s * .12f); close() }, col) // bolt
        10 -> { val p = Path(); var x = -s * .62f; p.moveTo(c.x + x, c.y); while (x < s * .62f) { x += s * .04f; p.lineTo(c.x + x, c.y + sin(x / s * 7f) * s * .26f) }; drawPath(p, col, style = line) } // wave
        else -> drawPath(Path().apply { moveTo(c.x, c.y - s * .6f); lineTo(c.x + s * .58f, c.y + s * .44f); lineTo(c.x - s * .58f, c.y + s * .44f); close() }, col) // peak
    }
}

@Composable
private fun MemoryGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 60_000L
    val faces = remember(seed) {
        val r = Random(seed)
        val six = (0 until 12).shuffled(r).take(6)
        (six + six).shuffled(r)
    }
    var open by remember { mutableStateOf(listOf<Int>()) }
    var matched by remember { mutableStateOf(setOf<Int>()) }
    var mistakes by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(setOf<Int>()) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    fun scoreFor(leftMs: Long): Int {
        val bonus = if (matched.size == faces.size) (leftMs / 1000).toInt() * 5 else 0
        return (matched.size / 2 * 100 + bonus - mistakes * 10).coerceAtLeast(0)
    }
    val elapsed = rememberClock(!finished, total) { finished = true; onDone(scoreFor(0)) }
    LaunchedEffect(open) {
        if (open.size == 2) {
            delay(520)
            if (faces[open[0]] == faces[open[1]]) { matched = matched + open; perf.hit(); fx.burst(FxKind.Stars, "Pair") }
            else { mistakes++; perf.miss(); wrong = open.toSet(); fx.burst(FxKind.Bad) }
            open = emptyList()
            delay(240); wrong = emptySet()
        }
    }
    LaunchedEffect(matched.size) {
        if (matched.size == faces.size && !finished) { finished = true; delay(450); onDone(scoreFor(total - elapsed)) }
    }
    val c = T.colors
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, scoreFor(0), "${matched.size / 2} of 6 pairs")
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            faces.indices.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { i ->
                        val shown = i in open || i in matched
                        val turn by animateFloatAsState(if (shown) 180f else 0f, tween(300, easing = EaseOutStrong), label = "flip")
                        val found = i in matched
                        val shape = RoundedCornerShape(18.dp)
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .graphicsLayer { rotationY = turn; cameraDistance = 12f * density }
                                .clip(shape)
                                .background(if (turn > 90f) Color.White.copy(alpha = if (found) .05f else .10f) else c.card)
                                .border(1.dp, if (i in wrong) Color(0xFFFF7D96) else if (found) c.accent.copy(alpha = .5f) else Color.White.copy(alpha = .10f), shape)
                                .clickable(remember { MutableInteractionSource() }, indication = null) {
                                    if (!finished && !shown && open.size < 2) open = open + i
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationY = if (turn > 90f) 180f else 0f }) {
                                val s = size.minDimension * .26f
                                if (turn > 90f) memoryFace(faces[i], center, s)
                                else { glow(center, s * 1.4f, c.accent, .16f); drawPath(sparklePath(center, s * .32f), c.accent.copy(alpha = .7f)) }
                            }
                        }
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

/** Four answers in two rows of glass keys, for the answer games. */
@Composable
internal fun AnswerGrid(options: List<String>, flash: KeyFlash, onPick: (Int) -> Unit) {
    val c = T.colors
    Column(Modifier.padding(16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(2).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { k, v ->
                    val i = r * 2 + k
                    GlassKey(Modifier.weight(1f).height(80.dp), flash.of(i), onTap = { onPick(i) }) {
                        Text(v, style = Type.title.copy(fontSize = 28.sp, fontFeatureSettings = "tnum"), color = c.label, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MathGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 45_000L
    val sums = remember(seed) { makeSums(seed) }
    var idx by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(0) }
    val flash = rememberKeyFlash()
    val perf = LocalPerf.current
    val fx = LocalFx.current
    var shownAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    fun score() = (right * 10 - wrong * 5).coerceAtLeast(0)
    val elapsed = rememberClock(true, total) { onDone(score()) }
    val c = T.colors
    val sum = sums[idx.coerceAtMost(sums.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, score(), "points")
        Spacer(Modifier.weight(1f))
        Question(idx) { Text(sum.text, style = Type.numberHuge.copy(fontSize = 68.sp, lineHeight = 72.sp), color = c.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        Spacer(Modifier.weight(1f))
        AnswerGrid(sum.options.map { "$it" }, flash) { i ->
            if (i == sum.answerIdx) {
                right++; perf.hit(); perf.react((SystemClock.elapsedRealtime() - shownAt).toInt()); fx.burst(FxKind.Stars, "+10"); flash.show(i, true)
            } else { wrong++; perf.miss(); fx.burst(FxKind.Bad, "−5"); flash.show(i, false) }
            shownAt = SystemClock.elapsedRealtime()
            idx++
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
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors

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
            DrawState.Waiting -> { scores = scores + 0; state = DrawState.Early; perf.miss(); fx.burst(FxKind.Bad, "Too early") }
            DrawState.Ready -> {
                lastMs = (SystemClock.elapsedRealtime() - readyAt).toInt()
                scores = scores + (600 - lastMs).coerceAtLeast(0)
                state = DrawState.Result
                perf.hit(); perf.react(lastMs)
                fx.burstAt(if (lastMs < 250) FxKind.Confetti else FxKind.Stars, fx.w / 2f, fx.h * .48f, "+${scores.last()}")
            }
            else -> {}
        }
    }
    // the light comes on at once (a fade would cost you milliseconds) and settles back slowly
    val lit by animateFloatAsState(if (state == DrawState.Ready) 1f else if (state == DrawState.Result) .5f else .08f, if (state == DrawState.Ready) tween(0) else tween(500), label = "drawLit")
    val breathe = remember { Animatable(0f) }
    LaunchedEffect(Unit) { while (true) { breathe.animateTo(1f, tween(1400)); breathe.animateTo(0f, tween(1400)) } }
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onPress = { tap() }) }) {
        Canvas(Modifier.fillMaxSize()) {
            val ctr = Offset(size.width / 2, size.height * .48f)
            glow(ctr, size.maxDimension * .8f, c.accent, .22f * lit)
            orb(ctr, size.minDimension * (.16f + .02f * breathe.value * (1f - lit)), c.accent, lit)
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            RoundHud(scores.sum(), "Try", "${(tryIdx + 1).coerceAtMost(5)} of 5", detail = "points")
            Spacer(Modifier.weight(1f))
            Text(
                when (state) {
                    DrawState.Waiting -> "Wait for the light"
                    DrawState.Ready -> "Now"
                    DrawState.Early -> "Too early"
                    DrawState.Result -> "$lastMs ms"
                },
                style = Type.largeTitle.copy(fontSize = 36.sp, lineHeight = 42.sp, fontFeatureSettings = "tnum"), textAlign = TextAlign.Center, color = c.label,
                modifier = Modifier.padding(bottom = 72.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ Next Number
private class SeqPuzzle(val shown: List<Int>, val options: List<Int>, val answerIdx: Int)

/** 60 puzzles from the seed. Every number stays positive and small, and the wrong answers are always findable. */
private fun makeSequences(seed: Long): List<SeqPuzzle> {
    val rnd = Random(seed)
    return List(60) { n ->
        // later puzzles draw from the harder rules
        val kind = rnd.nextInt(if (n < 5) 3 else if (n < 12) 6 else 8)
        val seq: List<Int> = when (kind) {
            0 -> { val a = rnd.nextInt(1, 20); val d = rnd.nextInt(2, 8); List(6) { a + d * it } }
            1 -> { val a = rnd.nextInt(1, 5); List(6) { a shl it } }                                       // doubling
            2 -> { val d = rnd.nextInt(2, 8); val a = d * 5 + rnd.nextInt(3, 20); List(6) { a - d * it } }  // counting down, stays positive
            3 -> { val o = rnd.nextInt(1, 6); List(6) { (it + o) * (it + o) } }                           // squares
            4 -> { val l = mutableListOf(rnd.nextInt(1, 5), rnd.nextInt(1, 5)); while (l.size < 6) l += l[l.size - 1] + l[l.size - 2]; l } // add the last two
            5 -> { val a = rnd.nextInt(8, 16); val d1 = rnd.nextInt(3, 7); val d2 = rnd.nextInt(1, 3); val l = mutableListOf(a); for (i in 1 until 6) l += l.last() + if (i % 2 == 1) d1 else -d2; l }
            6 -> { val l = mutableListOf(rnd.nextInt(1, 8)); var d = rnd.nextInt(1, 4); val st = rnd.nextInt(1, 3); for (i in 1 until 6) { l += l.last() + d; d += st }; l }
            else -> { val l = mutableListOf(rnd.nextInt(3, 10)); val k = rnd.nextInt(1, 4); for (i in 1 until 6) l += l.last() * 2 - k; l }   // double, then take a little away
        }
        val answer = seq[5]
        // believable wrong answers first (carrying on at the same step, off-by-one, off-by-two), then plain neighbours as a fallback
        val pool = listOf(seq[4] + (seq[4] - seq[3]), answer + 1, answer - 1, answer + 2, answer - 2, answer + 10, answer * 2, answer + 3, answer - 3, answer + 4, answer + 5, answer + 6)
            .filter { it > 0 && it != answer }.distinct().shuffled(rnd).take(3)
        val opts = (pool + answer).distinct().toMutableList()
        var pad = answer + 7
        while (opts.size < 4) { if (pad !in opts) opts += pad; pad++ }      // always ends: pad only goes up
        val list = opts.shuffled(rnd)
        SeqPuzzle(seq.take(5), list, list.indexOf(answer))
    }
}

@Composable
private fun SequenceGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 60_000L
    val puzzles = remember(seed) { makeSequences(seed) }
    var idx by remember { mutableIntStateOf(0) }
    var points by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    val flash = rememberKeyFlash()
    val perf = LocalPerf.current
    val fx = LocalFx.current
    var shownAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    fun score() = points.coerceIn(0, 800)
    val elapsed = rememberClock(true, total) { onDone(score()) }
    val c = T.colors
    val pz = puzzles[idx.coerceAtMost(puzzles.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, score(), if (streak >= 2) "$streak in a row" else "points")
        Spacer(Modifier.weight(1f))
        Text("What comes next", style = Type.footnote, color = c.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Question(idx) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                pz.shown.forEach { v -> Text("$v", style = Type.title.copy(fontSize = 26.sp, fontFeatureSettings = "tnum"), color = c.label, maxLines = 1) ; Text("·", style = Type.title.copy(fontSize = 26.sp), color = c.tertiary) }
                Text("?", style = Type.title.copy(fontSize = 26.sp), color = c.accent)
            }
        }
        Spacer(Modifier.weight(1f))
        AnswerGrid(pz.options.map { "$it" }, flash) { i ->
            if (i == pz.answerIdx) {
                val g = 10 + minOf(streak, 5) * 2
                points += g; streak++
                perf.hit(); perf.react((SystemClock.elapsedRealtime() - shownAt).toInt()); fx.burst(FxKind.Stars, "+$g"); flash.show(i, true)
            } else { points = (points - 3).coerceAtLeast(0); streak = 0; perf.miss(); fx.burst(FxKind.Bad, "−3"); flash.show(i, false) }
            shownAt = SystemClock.elapsedRealtime()
            idx++
        }
    }
}

// ------------------------------------------------------------------ Code Breaker
private val CODE_COLORS = listOf(Color(0xFFFF8FA3), Color(0xFFFFC56E), Color(0xFF9BE3A8), Color(0xFF8DB6FF), Color(0xFFC2A3FF))

private class CodeGuess(val pegs: List<Int>, val exact: Int, val near: Int)

@Composable
private fun CodeBreakerGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 150_000L
    val maxGuesses = 10
    val secret = remember(seed) { CODE_COLORS.indices.toList().shuffled(Random(seed)).take(4) }   // 4 different colours out of 5
    var current by remember { mutableStateOf(listOf<Int>()) }
    var guesses by remember { mutableStateOf(listOf<CodeGuess>()) }
    var finished by remember { mutableStateOf(false) }
    val fx = LocalFx.current
    fun scoreFor(leftMs: Long, solved: Boolean): Int {
        val best = guesses.maxOfOrNull { it.exact } ?: 0
        return if (solved) (400 + (maxGuesses - guesses.size) * 40 + (leftMs / 1000) * 1.5).toInt().coerceAtMost(1000) else best * 40
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
        if (exact == 4) fx.burstAt(FxKind.Confetti, fx.w / 2f, fx.h * .4f, "Cracked") else if (exact > 0) fx.burstAt(FxKind.Ring, fx.w / 2f, fx.h * .4f)
    }
    val solved = guesses.lastOrNull()?.exact == 4
    LaunchedEffect(guesses.size) {
        if (finished) return@LaunchedEffect
        if (solved || guesses.size >= maxGuesses) { finished = true; delay(700); onDone(scoreFor(total - elapsed, solved)) }
    }

    @Composable
    fun Peg(col: Int?, size: Dp, onClick: (() -> Unit)? = null) {
        Canvas(Modifier.size(size).then(if (onClick != null) Modifier.pressable(haptic = false) { onClick() } else Modifier)) {
            val r = this.size.minDimension / 2 * .82f
            if (col == null) drawCircle(Color.White.copy(alpha = .16f), r, center, style = Stroke(1.dp.toPx()))
            else orb(center, r, CODE_COLORS[col], .7f)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, guesses.size, "of $maxGuesses guesses")
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(maxGuesses) { row ->
                val g = guesses.getOrNull(row)
                Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${row + 1}", style = Type.caption.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.width(20.dp))
                    repeat(4) { k -> Peg(g?.pegs?.get(k), 28.dp) }
                    Spacer(Modifier.weight(1f))
                    // the answer: a lit dot for each right colour in the right place, a ring for right colour elsewhere
                    if (g != null) Canvas(Modifier.size(width = 64.dp, height = 14.dp)) {
                        val r = 4.dp.toPx(); var x = r
                        repeat(g.exact) { glow(Offset(x, size.height / 2), r * 3, c.accent, .4f); drawCircle(c.accent, r, Offset(x, size.height / 2)); x += r * 3.2f }
                        repeat(g.near) { drawCircle(c.label.copy(alpha = .7f), r - .5f.dp.toPx(), Offset(x, size.height / 2), style = Stroke(1.2.dp.toPx())); x += r * 3.2f }
                    }
                }
            }
        }
        Text("Lit dot: right colour, right place. Ring: right colour, wrong place", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { k -> Peg(current.getOrNull(k), 46.dp) { if (current.size > k) current = current.filterIndexed { i, _ -> i != k } } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            CODE_COLORS.indices.forEach { col -> Peg(col, 46.dp) { if (!finished && current.size < 4) current = current + col } }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { current = emptyList() }, modifier = Modifier.weight(1f)) { Text("Clear", color = c.secondary) }
            Box(Modifier.weight(2f)) { PrimaryButton("Guess", onClick = { submit() }, enabled = current.size == 4 && !finished, color = Ivory) }
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
        Hud(total - elapsed, total, lights.count { !it }, "$moves taps")
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(n) { y ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(n) { x ->
                        val i = y * n + x
                        GlassKey(Modifier.weight(1f).aspectRatio(1f), if (lights[i]) Lit.On else Lit.Idle, 18.dp, onTap = { tap(i) }) {
                            if (lights[i]) Canvas(Modifier.fillMaxSize()) { orb(center, size.minDimension * .18f, c.accent, 1f) }
                        }
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
        Hud(total - elapsed, total, inPlace(tiles), "of 8 placed")
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(n) { y ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(n) { x ->
                        val i = y * n + x
                        val v = tiles[i]
                        if (v == 0) Spacer(Modifier.weight(1f).aspectRatio(1f))
                        else GlassKey(Modifier.weight(1f).aspectRatio(1f), if (v == i + 1) Lit.On else Lit.Idle, 18.dp, onTap = { tap(i) }) {
                            Text("$v", style = Type.title.copy(fontSize = 32.sp), color = c.label)
                        }
                    }
                }
            }
        }
        Text("Slide the tiles into order, 1 to 8", style = Type.callout, color = c.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
    }
}

// ------------------------------------------------------------------ Odd One Out
/** The colours the dots come in, one per level pair; the odd one is the same colour, a shade lighter. */
private val ODD_HUES = listOf(Color(0xFFB9A7FF), Color(0xFFFF8FA3), Color(0xFF7FD8C8), Color(0xFFFFC56E), Color(0xFF8DB6FF), Color(0xFFE79BFF), Color(0xFF9BE3A8), Color(0xFFFFAE7A))

@Composable
private fun OddOneOutGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 45_000L
    var level by remember { mutableIntStateOf(0) }          // puzzles solved so far; the next puzzle depends only on this, so both of you see the same ones
    var points by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var wrongCell by remember { mutableStateOf<Int?>(null) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    var shownAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val elapsed = rememberClock(true, total) { onDone(points.coerceIn(0, 1000)) }
    val size = if (level < 3) 3 else if (level < 7) 4 else 5
    val puzzle = remember(level, seed) {
        val r = Random(seed * 31 + level)
        val base = ODD_HUES[(level / 2 + r.nextInt(2)) % ODD_HUES.size]
        // the difference shrinks as you go, from easy to only just there
        val shift = (.34f - level * .022f).coerceAtLeast(.07f)
        Triple(base, lerp(base, Color.White, shift), r.nextInt(size * size))   // common, odd, odd position
    }
    LaunchedEffect(wrongCell) { if (wrongCell != null) { delay(350); wrongCell = null } }
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, points.coerceAtLeast(0), if (streak >= 2) "$streak in a row" else "points")
        Spacer(Modifier.weight(1f))
        Text("Tap the one that's different", style = Type.footnote, color = T.colors.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Question(level) {
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(size) { y ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(size) { x ->
                            val i = y * size + x
                            val odd = i == puzzle.third
                            Canvas(
                                Modifier.weight(1f).aspectRatio(1f).pressable(haptic = false) {
                                    if (odd) {
                                        val g = 10 + minOf(streak, 5) * 2
                                        points += g; streak++; level++
                                        perf.hit(); perf.react((SystemClock.elapsedRealtime() - shownAt).toInt()); shownAt = SystemClock.elapsedRealtime()
                                        fx.burst(FxKind.Stars, "+$g")
                                    } else { points = (points - 3).coerceAtLeast(0); streak = 0; wrongCell = i; perf.miss(); fx.burst(FxKind.Bad, "−3") }
                                },
                            ) {
                                val r = this.size.minDimension * .36f
                                orb(center, r, if (odd) puzzle.second else puzzle.first, .55f)
                                if (wrongCell == i) drawCircle(Color(0xFFFF7D96), r * 1.2f, center, style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

// ------------------------------------------------------------------ Make the Target
private class TargetPuzzle(val numbers: List<Int>, val target: Int)

private fun makeTarget(seed: Long, idx: Int): TargetPuzzle {
    val r = Random(seed * 131 + idx)
    val maxN = (12 + idx).coerceAtMost(30)
    val numbers = (1..maxN).shuffled(r).take(6)
    val k = if (idx < 4) 2 else if (idx < 10) 3 else 3 + r.nextInt(2)
    val picks = numbers.indices.shuffled(r).take(k)
    return TargetPuzzle(numbers, picks.sumOf { numbers[it] })        // built from the tiles, so it can always be solved
}

@Composable
private fun TargetGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 60_000L
    var idx by remember { mutableIntStateOf(0) }
    var points by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf(setOf<Int>()) }
    var flash by remember { mutableStateOf<Boolean?>(null) }      // true = solved, false = went over
    val perf = LocalPerf.current
    val fx = LocalFx.current
    var shownAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val puzzle = remember(idx, seed) { makeTarget(seed, idx) }
    val sum = picked.sumOf { puzzle.numbers[it] }
    val elapsed = rememberClock(true, total) { onDone(points.coerceIn(0, 1000)) }
    val c = T.colors
    LaunchedEffect(sum, idx) {
        if (picked.isEmpty()) { flash = null; return@LaunchedEffect }
        if (sum == puzzle.target) {
            flash = true
            val g = 25 + minOf(streak, 5) * 3
            perf.hit(); perf.react((SystemClock.elapsedRealtime() - shownAt).toInt())
            fx.burst(FxKind.Confetti, "+$g")
            delay(350)
            points += g; streak++; idx++; picked = emptySet(); flash = null; shownAt = SystemClock.elapsedRealtime()
        } else if (sum > puzzle.target) {
            flash = false; perf.miss(); fx.burst(FxKind.Bad, "Too much"); delay(500)
            picked = emptySet(); flash = null
        }
    }
    // The layout adapts to the space it gets: the tiles shrink to fit, so nothing is ever pushed off the screen
    // (on small phones, big font sizes or with the gesture bar), and the Clear / Skip buttons are always reachable.
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, points, if (streak >= 2) "$streak in a row" else "points")
        Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Make", style = Type.footnote, color = c.secondary)
            Question(idx) { Text("${puzzle.target}", style = Type.numberHuge.copy(fontSize = 56.sp, lineHeight = 60.sp, fontFeatureSettings = "tnum"), color = c.label, maxLines = 1, textAlign = TextAlign.Center) }
            Text(
                when (flash) { true -> "Made it"; false -> "Too much, try again"; null -> if (picked.isEmpty()) "Tap tiles that add up to it" else picked.joinToString(" + ") { "${puzzle.numbers[it]}" } + " = $sum" },
                style = Type.callout, color = when (flash) { true -> c.accent; false -> Color(0xFFFF9DB2); null -> c.secondary },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp), textAlign = TextAlign.Center, maxLines = 2,
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            val gap = 10.dp
            val tileH = ((maxHeight - gap) / 2).coerceIn(48.dp, 96.dp)
            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(gap)) {
                puzzle.numbers.indices.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { i ->
                            val on = i in picked
                            GlassKey(
                                Modifier.weight(1f).height(tileH),
                                when { on && flash == true -> Lit.Good; on && flash == false -> Lit.Bad; on -> Lit.On; else -> Lit.Idle },
                                onTap = { if (flash == null) picked = if (on) picked - i else picked + i },
                            ) { Text("${puzzle.numbers[i]}", style = Type.title.copy(fontSize = 28.sp, fontFeatureSettings = "tnum"), color = c.label, maxLines = 1) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { picked = emptySet() }, modifier = Modifier.weight(1f)) { Text("Clear", color = c.secondary) }
            TextButton(onClick = { streak = 0; idx++; picked = emptySet(); shownAt = SystemClock.elapsedRealtime() }, modifier = Modifier.weight(1f)) { Text("Skip this one", color = c.secondary) }
        }
    }
}

// ------------------------------------------------------------------ Echo
/** Four moons: rose, periwinkle, mint and amber. */
private val ECHO_COLORS = listOf(Color(0xFFFF9DB5), Color(0xFF9FB4FF), Color(0xFF8EDCC4), Color(0xFFFFD08A))

@Composable
private fun EchoGame(seed: Long, onDone: (Int) -> Unit) {
    val maxLen = 14
    val full = remember(seed) { val r = Random(seed); List(maxLen) { r.nextInt(4) } }     // the first round plays 3 steps, each next round one more
    var length by remember { mutableIntStateOf(3) }
    var lives by remember { mutableIntStateOf(2) }
    var score by remember { mutableIntStateOf(0) }
    var replay by remember { mutableIntStateOf(0) }
    var lit by remember { mutableStateOf<Int?>(null) }
    var accepting by remember { mutableStateOf(false) }
    var inputIdx by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Watch") }
    var finished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fx = LocalFx.current
    val c = T.colors
    val up by rememberUpdatedState(onDone)

    // play the pattern whenever a new round (or a repeat after a mistake) begins
    LaunchedEffect(length, replay) {
        accepting = false; inputIdx = 0; message = "Watch"
        delay(700)
        for (k in 0 until length) { lit = full[k]; delay(480); lit = null; delay(220) }
        message = "Your turn"; accepting = true
    }

    fun finish() { if (!finished) { finished = true; up(score.coerceIn(0, 1000)) } }
    fun tap(pad: Int) {
        if (!accepting || finished) return
        scope.launch { lit = pad; delay(180); lit = null }
        if (pad == full[inputIdx]) {
            inputIdx++
            fx.burst(FxKind.Ring)
            if (inputIdx == length) {
                accepting = false
                score += length * 10
                fx.burst(FxKind.Stars, "+${length * 10}")
                scope.launch {
                    message = "Yes"; delay(800)
                    if (length >= maxLen) finish() else length++
                }
            }
        } else {
            accepting = false
            fx.burst(FxKind.Bad)
            lives--
            scope.launch {
                if (lives <= 0) { message = "That's the end"; delay(900); finish() }
                else { message = "Watch again"; delay(900); replay++ }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        RoundHud(score, "Lives", "●".repeat(lives.coerceAtLeast(0)).ifEmpty { "–" }, detail = "pattern of $length")
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 36.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            (0..3).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { pad ->
                        val on by animateFloatAsState(if (lit == pad) 1f else 0f, if (lit == pad) tween(70) else tween(380), label = "moon")
                        Canvas(
                            Modifier.weight(1f).aspectRatio(1f).clickable(remember { MutableInteractionSource() }, indication = null) { tap(pad) },
                        ) {
                            val r = size.minDimension * .4f * (1f + .06f * on)
                            orb(center, r, ECHO_COLORS[pad], .12f + .88f * on)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        AnimatedContent(message, label = "echoMsg", transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(160)) }, modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { m ->
            Text(m, style = Type.title, color = c.label, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
    }
}
