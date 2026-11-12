package app.tandem.ui.screens

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandem.ui.components.FxKind
import app.tandem.ui.components.LocalFx
import app.tandem.ui.components.pressable
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/*
 * Games added in 2.3.0. Like the rest: one seed per round, so both of you get the very same round; the
 * app's dusk with the game's own light; no score ceiling (endless with lives, or timed).
 */

// ------------------------------------------------------------------ Pulse
private class Ring(val t: Long, val speed: Float)      // born at [t] ms, radius grows [speed] target-radii per second

/**
 * Rings ripple out from the centre. Tap the moment one meets the lit circle: dead on scores most. A ring that
 * slips past, or a tap with nothing there, costs a life. Rings come faster and closer together as you go.
 */
@Composable
internal fun PulseGame(seed: Long, onDone: (Int) -> Unit) {
    val rings = remember(seed) {
        val r = Random(seed)
        buildList {
            var t = 900L; var k = 0
            while (t < 600_000L) {
                val speed = (.62f + k * .012f).coerceAtMost(1.35f)
                add(Ring(t, speed))
                // the gap shrinks; now and then two come close together
                t += (if (r.nextFloat() < .15f && k > 6) 380L else (1350L - k * 14L).coerceAtLeast(560L)) + r.nextInt(0, 260)
                k++
            }
        }
    }
    var resolved by remember { mutableStateOf(mapOf<Int, Int>()) }    // ring -> 2 perfect, 1 good, 0 missed
    var lives by remember { mutableIntStateOf(3) }
    var points by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var over by remember { mutableStateOf(false) }
    var flash by remember { mutableFloatStateOf(0f) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors
    val scope = rememberCoroutineScope()
    val elapsed = rememberClock(!over, 600_000L) { over = true; onDone(points) }
    fun radiusOf(i: Int) = (elapsed - rings[i].t) / 1000f * rings[i].speed

    fun loseLife() {
        lives--; combo = 0; perf.miss()
        if (lives <= 0 && !over) { over = true; scope.launch { delay(800); onDone(points) } }
    }
    // rings that slipped past unanswered
    LaunchedEffect(elapsed / 40) {
        if (over) return@LaunchedEffect
        val firstCandidate = rings.indexOfFirst { it.t > elapsed - 4000 }.coerceAtLeast(0)
        for (i in firstCandidate until rings.size) {
            if (rings[i].t > elapsed) break
            if (i !in resolved && radiusOf(i) > 1.2f) { resolved = resolved + (i to 0); fx.burst(FxKind.Bad, "Missed"); loseLife() }
        }
    }
    fun tap() {
        if (over) return
        val live = rings.indices.filter { rings[it].t <= elapsed && it !in resolved && radiusOf(it) < 1.2f }
        val best = live.minByOrNull { abs(radiusOf(it) - 1f) }
        val off = best?.let { abs(radiusOf(it) - 1f) } ?: 9f
        when {
            best != null && off < .06f -> { resolved = resolved + (best to 2); combo++; points += 30 + combo * 2; perf.hit(); flash = 1f; fx.burstAt(FxKind.Confetti, fx.w / 2f, fx.h * .52f, "Perfect") }
            best != null && off < .15f -> { resolved = resolved + (best to 1); combo++; points += 15 + combo; perf.hit(); flash = .6f; fx.burstAt(FxKind.Stars, fx.w / 2f, fx.h * .52f, "+${15 + combo}") }
            else -> { if (best != null && off < .3f) resolved = resolved + (best to 0); fx.burst(FxKind.Bad, if (best == null) "Nothing there" else "Off beat"); loseLife() }
        }
    }
    LaunchedEffect(flash) { if (flash > 0f) { delay(16); flash = (flash - .08f).coerceAtLeast(0f) } }

    Column(Modifier.fillMaxSize()) {
        RoundHud(points, "Lives", "●".repeat(lives.coerceAtLeast(0)).ifEmpty { "–" }, detail = if (combo >= 3) "$combo in a row" else "tap on the circle")
        Box(Modifier.fillMaxWidth().weight(1f).pointerInput(Unit) { detectTapGestures(onPress = { tap() }) }) {
            Canvas(Modifier.fillMaxSize()) {
                val ctr = Offset(size.width / 2f, size.height * .48f)
                val R = size.minDimension * .34f
                glow(ctr, R * 1.8f, c.accent, .10f + .25f * flash)
                // the target circle
                drawCircle(Color.White.copy(alpha = .10f), R, ctr, style = Stroke(R * .15f))
                drawCircle(Color(0xFFF6F2FF).copy(alpha = .75f + .25f * flash), R, ctr, style = Stroke(2.5.dp.toPx()))
                // live rings
                for (i in rings.indices) {
                    val ring = rings[i]
                    if (ring.t > elapsed) break
                    if (ring.t < elapsed - 4000) continue
                    val r = radiusOf(i)
                    val res = resolved[i]
                    if (res != null && res > 0) continue
                    if (r > 1.5f) continue
                    val a = if (r < .15f) r / .15f else if (r > 1f) (1.5f - r) / .5f else 1f
                    val near = 1f - (abs(r - 1f) / .3f).coerceIn(0f, 1f)
                    drawCircle(c.accent.copy(alpha = .25f * a), R * r, ctr, style = Stroke(10.dp.toPx() * (.4f + near)))
                    drawCircle(c.accent.copy(alpha = .9f * a), R * r, ctr, style = Stroke(2.dp.toPx() + 2.dp.toPx() * near))
                }
                orb(ctr, R * .12f, c.accent, .6f + .4f * flash)
            }
            Text("Tap anywhere when a ring meets the circle", style = Type.footnote, color = c.secondary, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp))
        }
    }
}

// ------------------------------------------------------------------ Firefly Count
private class FireflyRound(val spots: List<Offset>, val answer: Int, val options: List<Int>, val showMs: Long)

private fun fireflyRound(seed: Long, idx: Int): FireflyRound {
    val r = Random(seed * 41 + idx)
    val n = (3 + idx / 2 + r.nextInt(0, 3)).coerceAtMost(16)
    val spots = ArrayList<Offset>()
    var tries = 0
    while (spots.size < n && tries < 3000) {
        val p = Offset(.08f + r.nextFloat() * .84f, .08f + r.nextFloat() * .84f)
        if (spots.none { hypot(it.x - p.x, it.y - p.y) < .12f }) spots += p
        tries++
    }
    val ans = spots.size
    val opts = (listOf(ans) + listOf(ans - 2, ans - 1, ans + 1, ans + 2).filter { it > 0 }.shuffled(r).take(3)).shuffled(r)
    return FireflyRound(spots, ans, opts, (950L - idx * 35L).coerceAtLeast(380L))
}

/** Fireflies blink on together for a moment, then go dark. How many were there? It gets quicker and busier. */
@Composable
internal fun FireflyGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 45_000L
    var idx by remember { mutableIntStateOf(0) }
    val round = remember(seed, idx) { fireflyRound(seed, idx) }
    var showing by remember { mutableStateOf(true) }
    var points by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    var askedAt by remember { mutableLongStateOf(0L) }
    val glowA = remember { Animatable(0f) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val flash = rememberKeyFlash()
    val c = T.colors
    val elapsed = rememberClock(true, total) { onDone(points) }
    LaunchedEffect(idx) {
        showing = true; glowA.snapTo(0f)
        delay(350)
        glowA.animateTo(1f, tween(140))
        delay(round.showMs)
        glowA.animateTo(0f, tween(220))
        showing = false; askedAt = SystemClock.elapsedRealtime()
    }
    fun answer(k: Int) {
        if (showing) return
        if (round.options[k] == round.answer) {
            streak++; val got = 10 + streak * 2 + round.answer
            points += got; perf.hit(); perf.react((SystemClock.elapsedRealtime() - askedAt).toInt()); fx.burst(FxKind.Stars, "+$got"); flash.show(k, true)
        } else { streak = 0; points = (points - 4).coerceAtLeast(0); perf.miss(); fx.burst(FxKind.Bad, "It was ${round.answer}"); flash.show(k, false) }
        idx++
    }
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, points, if (streak >= 3) "$streak in a row" else "round ${idx + 1}")
        Box(Modifier.fillMaxWidth().weight(1f).padding(20.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF0B0828).copy(alpha = .55f))) {
            Canvas(Modifier.fillMaxSize()) {
                // a few faint stars that never blink, so the dark has some depth
                val far = Random(seed + idx)
                repeat(18) { drawCircle(Color.White.copy(alpha = .08f + far.nextFloat() * .1f), 1.2.dp.toPx(), Offset(far.nextFloat() * size.width, far.nextFloat() * size.height)) }
                val g = glowA.value
                round.spots.forEach { p ->
                    val o = Offset(p.x * size.width, p.y * size.height)
                    glow(o, 26.dp.toPx(), Color(0xFFE8FF8A), .55f * g)
                    drawCircle(Color(0xFFF7FFD0).copy(alpha = g), 4.dp.toPx(), o)
                }
            }
            if (!showing) Text("How many?", style = Type.title, color = c.label, modifier = Modifier.align(Alignment.Center))
        }
        Box(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            AnswerGrid(round.options.map { "$it" }, flash) { answer(it) }
        }
    }
}

// ------------------------------------------------------------------ Twin Stars
private val TWIN_COLORS = listOf(Color(0xFFFF8FA3), Color(0xFFFFC56E), Color(0xFF9BE3A8), Color(0xFF8DB6FF), Color(0xFFC2A3FF), Color(0xFF8FE0E8))

private fun DrawScope.twinShape(kind: Int, col: Color, c: Offset, s: Float, turn: Float) {
    rotate(turn, c) {
        when (kind) {
            0 -> drawCircle(col, s * .5f, c)
            1 -> drawCircle(col, s * .44f, c, style = Stroke(s * .16f))
            2 -> drawPath(sparklePath(c, s * .62f), col)
            3 -> drawPath(heartPath(c + Offset(0f, s * .05f), s * .55f), col)
            4 -> drawPath(Path().apply { moveTo(c.x, c.y - s * .6f); lineTo(c.x + s * .46f, c.y); lineTo(c.x, c.y + s * .6f); lineTo(c.x - s * .46f, c.y); close() }, col)
            5 -> drawPath(Path().apply { moveTo(c.x, c.y - s * .55f); lineTo(c.x + s * .55f, c.y + s * .42f); lineTo(c.x - s * .55f, c.y + s * .42f); close() }, col)
            6 -> drawRect(col, c - Offset(s * .4f, s * .4f), androidx.compose.ui.geometry.Size(s * .8f, s * .8f))
            else -> { drawCircle(col, s * .5f, c); drawCircle(Color(0xFF1A1450), s * .44f, c + Offset(s * .22f, -s * .14f)) }
        }
    }
}

private class TwinPuzzle(val cols: Int, val tiles: List<Pair<Int, Int>>, val a: Int, val b: Int)   // (shape, colour) per tile; a and b are the twins

private fun twinPuzzle(seed: Long, idx: Int): TwinPuzzle {
    val r = Random(seed * 53 + idx)
    val (cols, rows) = listOf(3 to 3, 3 to 4, 4 to 4, 4 to 5, 5 to 5, 5 to 6)[(idx / 2).coerceAtMost(5)]
    val n = cols * rows
    // every tile different, then one copied over another
    val all = (0 until 8).flatMap { s -> TWIN_COLORS.indices.map { col -> s to col } }.shuffled(r).take(n - 1)
    val a = r.nextInt(n - 1)
    val tiles = all.toMutableList()
    val b = r.nextInt(n)
    tiles.add(b, all[a])
    val aIdx = tiles.indexOfFirst { it == all[a] }
    val bIdx = tiles.indexOfLast { it == all[a] }
    return TwinPuzzle(cols, tiles, aIdx, bIdx)
}

/** A field of shapes, every one different except two. Find the pair. Bigger fields score more. */
@Composable
internal fun TwinsGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 45_000L
    var idx by remember { mutableIntStateOf(0) }
    val puzzle = remember(seed, idx) { twinPuzzle(seed, idx) }
    var picked by remember(idx) { mutableStateOf(listOf<Int>()) }
    var wrong by remember { mutableStateOf(setOf<Int>()) }
    var points by remember { mutableIntStateOf(0) }
    var shownAt by remember(idx) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors
    val scope = rememberCoroutineScope()
    val elapsed = rememberClock(true, total) { onDone(points) }
    // a little tilt for life, the same on both twins so "exactly the same" stays fair
    val turns = remember(seed, idx) { val r = Random(seed + idx * 7); MutableList(puzzle.tiles.size) { (r.nextFloat() - .5f) * 40f }.also { it[puzzle.b] = it[puzzle.a] } }

    fun pick(i: Int) {
        if (i in picked) { picked = picked - i; return }
        val now = picked + i
        if (now.size < 2) { picked = now; return }
        if (now.toSet() == setOf(puzzle.a, puzzle.b)) {
            val got = 20 + puzzle.tiles.size
            points += got; perf.hit(); perf.react((SystemClock.elapsedRealtime() - shownAt).toInt())
            fx.burstAt(FxKind.Confetti, fx.w / 2f, fx.h * .45f, "+$got")
            picked = now
            scope.launch { delay(380); idx++ }
        } else {
            points = (points - 3).coerceAtLeast(0); perf.miss(); fx.burst(FxKind.Bad, "Not twins")
            wrong = now.toSet(); picked = emptyList()
            scope.launch { delay(320); wrong = emptySet() }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, points, "field ${idx + 1} · ${puzzle.tiles.size} stars")
        Text("Two of these are exactly the same. Tap both", style = Type.footnote, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Column(Modifier.fillMaxWidth().weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            puzzle.tiles.indices.chunked(puzzle.cols).forEach { row ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { i ->
                        val on = i in picked
                        val bad = i in wrong
                        val shape = RoundedCornerShape(16.dp)
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(shape)
                                .background(if (on) c.accent.copy(alpha = .18f) else c.card)
                                .border(1.dp, if (bad) Color(0xFFFF7D96) else if (on) c.accent else Color.White.copy(alpha = .08f), shape)
                                .pressable(haptic = false) { pick(i) },
                        ) {
                            Canvas(Modifier.fillMaxSize()) {
                                val (k, col) = puzzle.tiles[i]
                                val s = size.minDimension * .5f
                                glow(center, s * 1.1f, TWIN_COLORS[col], .22f)
                                twinShape(k, TWIN_COLORS[col], center, s, turns[i])
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused") private fun wobble(t: Float) = sin(t)
