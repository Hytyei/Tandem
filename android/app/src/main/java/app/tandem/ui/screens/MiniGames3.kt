package app.tandem.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.tandem.ui.components.FxKind
import app.tandem.ui.components.LocalFx
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val StarGold = Color(0xFFFFD58A)
private val MeteorRose = Color(0xFFFF7D96)

// ------------------------------------------------------------------ Star Path
/** One constellation: [n] stars spread over the sky (0..1 on each axis), never too close to each other. */
private fun makeConstellation(seed: Long, idx: Int): List<Offset> {
    val r = Random(seed * 97 + idx)
    val n = (6 + idx).coerceAtMost(14)
    val pts = ArrayList<Offset>()
    var tries = 0
    while (pts.size < n && tries < 2000) {
        val p = Offset(.08f + r.nextFloat() * .84f, .06f + r.nextFloat() * .88f)
        val gap = if (tries < 1500) .2f else .12f
        if (pts.none { hypot(it.x - p.x, it.y - p.y) < gap }) pts += p
        tries++
    }
    return pts
}

@Composable
internal fun StarPathGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 45_000L
    var idx by remember { mutableIntStateOf(0) }
    var next by remember { mutableIntStateOf(0) }           // the star to tap next
    var points by remember { mutableIntStateOf(0) }
    var wrong by remember { mutableIntStateOf(-1) }
    val stars = remember(idx, seed) { makeConstellation(seed, idx) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors
    var origin by remember { mutableStateOf(Offset.Zero) }
    var shownAt by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    val elapsed = rememberClock(true, total) { onDone(points.coerceAtLeast(0)) }
    LaunchedEffect(wrong) { if (wrong >= 0) { delay(300); wrong = -1 } }

    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, points, "constellation ${idx + 1}")
        Text("Tap the stars in order, 1 to ${stars.size}", style = Type.footnote, color = c.secondary, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(24.dp).onGloballyPositioned { origin = it.positionInParent() }) {
            val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
            val reach = with(LocalDensity.current) { 34.dp.toPx() }
            Canvas(
                Modifier.fillMaxSize().pointerInput(idx, next) {
                    detectTapGestures(onPress = { pos ->
                        val hit = stars.indices.minByOrNull { hypot(stars[it].x * w - pos.x, stars[it].y * h - pos.y) } ?: return@detectTapGestures
                        if (hypot(stars[hit].x * w - pos.x, stars[hit].y * h - pos.y) > reach || hit < next) return@detectTapGestures
                        if (hit == next) {
                            points += 5; next++; perf.hit(); perf.react((android.os.SystemClock.elapsedRealtime() - shownAt).toInt()); shownAt = android.os.SystemClock.elapsedRealtime()
                            if (next == stars.size) {
                                points += 20
                                fx.burstAt(FxKind.Confetti, pos.x, pos.y, "+25")
                                next = 0; idx++
                            } else fx.burst(FxKind.Stars, "+5")
                        } else { points = (points - 3).coerceAtLeast(0); wrong = hit; perf.miss(); fx.burst(FxKind.Bad, "−3") }
                    })
                },
            ) {
                // the lines you have drawn so far
                for (k in 1 until next) {
                    val a = Offset(stars[k - 1].x * w, stars[k - 1].y * h); val b = Offset(stars[k].x * w, stars[k].y * h)
                    drawLine(c.accent.copy(alpha = .25f), a, b, 6.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = .8f), a, b, 1.5.dp.toPx(), StrokeCap.Round)
                }
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    textAlign = android.graphics.Paint.Align.CENTER; textSize = 13.dp.toPx(); typeface = android.graphics.Typeface.SERIF
                }
                stars.forEachIndexed { k, s ->
                    val p = Offset(s.x * w, s.y * h)
                    val done = k < next
                    orb(p, 9.dp.toPx(), if (done) c.accent else Color(0xFFE9E2FF), if (done) 1f else .25f)
                    if (k == wrong) drawCircle(MeteorRose, 16.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                    paint.color = (if (done) Color.White.copy(alpha = .45f) else Color.White.copy(alpha = .9f)).toArgb()
                    drawContext.canvas.nativeCanvas.drawText("${k + 1}", p.x, p.y - 15.dp.toPx(), paint)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Orbit
private class Gate(val at: Float, val flip: Boolean)

@Composable
internal fun OrbitGame(seed: Long, onDone: (Int) -> Unit) {
    val gates = remember(seed) {
        val r = Random(seed)
        var a = (r.nextFloat() * 2 * PI).toFloat()
        List(90) { a += 1.2f + r.nextFloat() * 2.8f; Gate(a % (2 * PI).toFloat(), r.nextInt(100) < 22) }
    }
    var hits by remember { mutableIntStateOf(0) }
    var points by remember { mutableIntStateOf(0) }
    var lives by remember { mutableIntStateOf(3) }
    var angle by remember { mutableFloatStateOf(-PI.toFloat() / 2) }
    var dir by remember { mutableFloatStateOf(1f) }
    var over by remember { mutableStateOf(false) }
    var flash by remember { mutableFloatStateOf(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors
    val scope = rememberCoroutineScope()
    // These are read fresh wherever they are used (the tap handler and the frame loop are made once, so a value
    // captured here would stay stuck at the first gate)
    fun gateNow() = gates[hits.coerceAtMost(gates.lastIndex)]
    fun halfNow() = (.46f - hits * .012f).coerceAtLeast(.16f)       // half the gate's width, in radians
    fun speedNow() = (1.5f + hits * .07f).coerceAtMost(4.6f)        // radians a second
    val gate = gateNow()
    val half = halfNow()

    LaunchedEffect(over) {
        if (over) return@LaunchedEffect
        var last = 0L
        while (true) {
            val t = withFrameMillis { it }
            val dt = if (last == 0L) .016f else ((t - last) / 1000f).coerceIn(0f, .05f)
            last = t
            angle = (angle + dir * speedNow() * dt) % (2 * PI).toFloat()
            flash = (flash - dt * 3f).coerceAtLeast(0f)
        }
    }
    fun gap(a: Float, b: Float): Float { val d = abs(((a - b) % (2 * PI).toFloat() + 3 * PI.toFloat()) % (2 * PI).toFloat() - PI.toFloat()); return d }
    fun tap() {
        if (over) return
        val rad = box.width.coerceAtMost(box.height) * .36f
        val mx = origin.x + box.width / 2f + cos(angle) * rad; val my = origin.y + box.height / 2f + sin(angle) * rad
        val g0 = gateNow(); val w0 = halfNow()
        val d = gap(angle, g0.at)
        if (d <= w0) {
            val perfect = d < w0 * .3f
            val g = 10 + if (perfect) 5 else 0
            points += g; hits++; perf.hit(); flash = 1f
            if (g0.flip) dir = -dir
            fx.burstAt(if (perfect) FxKind.Confetti else FxKind.Stars, mx, my, if (perfect) "Perfect" else "+$g")
            // no ceiling: it goes on until three misses
        } else {
            lives--; perf.miss(); fx.burstAt(FxKind.Bad, mx, my)
            if (lives <= 0) { over = true; scope.launch { delay(900); onDone(points) } }
        }
    }

    Column(Modifier.fillMaxSize()) {
        RoundHud(points, "Lives", "●".repeat(lives.coerceAtLeast(0)).ifEmpty { "–" }, detail = "$hits gates")
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }.onGloballyPositioned { origin = it.positionInParent() }
                .pointerInput(Unit) { detectTapGestures(onPress = { tap() }) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val ctr = Offset(size.width / 2, size.height / 2)
                val rad = size.minDimension * .36f
                // the planet it circles, glowing brighter on each gate you hit
                orb(ctr, rad * .32f, c.accent, .3f + .5f * flash)
                drawCircle(Color.White.copy(alpha = .1f), rad, ctr, style = Stroke(1.5.dp.toPx()))
                // the gate: a lit arc on the ring
                val deg = Math.toDegrees(gate.at.toDouble()).toFloat(); val span = Math.toDegrees(half.toDouble()).toFloat()
                drawArc(c.accent.copy(alpha = .25f), deg - span, span * 2, false, ctr - Offset(rad, rad), Size(rad * 2, rad * 2), style = Stroke(16.dp.toPx(), cap = StrokeCap.Round))
                drawArc(c.accent, deg - span, span * 2, false, ctr - Offset(rad, rad), Size(rad * 2, rad * 2), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                // the moon and its trail
                for (k in 1..8) {
                    val a = angle - dir * k * .05f
                    drawCircle(Color.White.copy(alpha = .14f * (1f - k / 9f)), 9.dp.toPx() * (1f - k / 12f), ctr + Offset(cos(a), sin(a)) * rad)
                }
                orb(ctr + Offset(cos(angle), sin(angle)) * rad, 10.dp.toPx(), Color(0xFFE9E2FF), 1f)
            }
            if (hits == 0 && !over) Text("Tap when the moon is in the light", style = Type.callout, color = c.secondary, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp))
        }
    }
}

// ------------------------------------------------------------------ Meteor Drift
private class Fall(val t: Long, val x: Float, val meteor: Boolean, val dur: Long)

@Composable
internal fun DriftGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 45_000L
    val falls = remember(seed) {
        val r = Random(seed)
        buildList {
            var t = 500L
            while (t < total - 1200) {
                val meteorOdds = 30 + (t / 2000).toInt()
                add(Fall(t, .08f + r.nextFloat() * .84f, r.nextInt(100) < meteorOdds, (2700 - t / 25).coerceAtLeast(1400)))
                t += (560 - t / 120).coerceAtLeast(230)
            }
        }
    }
    var px by remember { mutableFloatStateOf(.5f) }            // where your light is, 0..1
    var target by remember { mutableFloatStateOf(.5f) }
    var points by remember { mutableIntStateOf(0) }
    var caught by remember { mutableStateOf(setOf<Int>()) }
    var shieldUntil by remember { mutableLongStateOf(0L) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val trail = remember { ArrayDeque<Float>() }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors
    val density = LocalDensity.current.density
    val elapsed = rememberClock(true, total) {
        repeat(falls.indices.count { !falls[it].meteor && it !in caught && falls[it].t + falls[it].dur < total }) { perf.miss() }
        onDone(points.coerceAtLeast(0))
    }
    val now by rememberUpdatedState(elapsed)

    // your light glides toward your finger, and things that reach it are caught (or hurt)
    LaunchedEffect(box) {
        if (box.width == 0) return@LaunchedEffect
        var last = 0L
        while (true) {
            val t = withFrameMillis { it }
            val dt = if (last == 0L) .016f else ((t - last) / 1000f).coerceIn(0f, .05f)
            last = t
            px += (target - px) * (1f - kotlin.math.exp(-14f * dt))
            trail.addFirst(px); if (trail.size > 10) trail.removeLast()
            val w = box.width.toFloat(); val h = box.height.toFloat()
            val py = h * .86f
            val reach = 30f * density
            val ms = now
            falls.forEachIndexed { i, f ->
                if (i in caught || ms < f.t || ms > f.t + f.dur) return@forEachIndexed
                val y = (ms - f.t).toFloat() / f.dur * (h + 40f * density) - 20f * density
                if (abs(y - py) < reach && abs(f.x * w - px * w) < reach) {
                    caught = caught + i
                    val x = origin.x + f.x * w; val yy = origin.y + y
                    if (f.meteor) {
                        if (ms >= shieldUntil) { points = (points - 15).coerceAtLeast(0); shieldUntil = ms + 900; perf.miss(); fx.burstAt(FxKind.Boom, x, yy, "−15") }
                    } else { points += 5; perf.hit(); fx.burstAt(FxKind.Coins, x, yy, "+5") }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, points, "catch stars, dodge meteors")
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }.onGloballyPositioned { origin = it.positionInParent() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        target = (down.position.x / size.width).coerceIn(.05f, .95f)
                        while (true) {
                            val e = awaitPointerEvent()
                            val p = e.changes.firstOrNull() ?: break
                            if (!p.pressed) break
                            target = (p.position.x / size.width).coerceIn(.05f, .95f)
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width; val h = size.height
                val ms = elapsed
                falls.forEachIndexed { i, f ->
                    if (i in caught || ms < f.t || ms > f.t + f.dur) return@forEachIndexed
                    val y = (ms - f.t).toFloat() / f.dur * (h + 40.dp.toPx()) - 20.dp.toPx()
                    val p = Offset(f.x * w, y)
                    if (f.meteor) {
                        for (k in 1..6) drawCircle(MeteorRose.copy(alpha = .12f * (1f - k / 7f)), 10.dp.toPx() * (1f - k / 9f), p - Offset(0f, k * 7.dp.toPx()))
                        orb(p, 11.dp.toPx(), Color(0xFF6B4A7A), .25f)
                        glow(p, 18.dp.toPx(), MeteorRose, .35f)
                    } else {
                        glow(p, 22.dp.toPx(), StarGold, .45f)
                        drawPath(sparklePath(p, 11.dp.toPx()), StarGold)
                        drawCircle(Color.White, 2.5.dp.toPx(), p)
                    }
                }
                val py = h * .86f
                val shield = ms < shieldUntil
                trail.forEachIndexed { k, tx -> drawCircle(c.accent.copy(alpha = .16f * (1f - k / 10f)), 12.dp.toPx() * (1f - k / 14f), Offset(tx * w, py + k * 3.dp.toPx())) }
                orb(Offset(px * w, py), 13.dp.toPx(), if (shield) MeteorRose else c.accent, if (shield) .5f else 1f)
            }
            if (elapsed < 2500) Text("Slide your finger to move your light", style = Type.callout, color = c.secondary, modifier = Modifier.align(Alignment.Center))
        }
    }
}
