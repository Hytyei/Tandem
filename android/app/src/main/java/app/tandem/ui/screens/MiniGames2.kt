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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min as minDp
import app.tandem.ui.components.FxKind
import app.tandem.ui.components.LocalFx
import app.tandem.ui.components.PrimaryButton
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private val Ivory = Color(0xFFF6F2FF)

// ------------------------------------------------------------------ Whack-a-Mole
private class MoleSpawn(val t: Long, val hole: Int, val kind: Int, val life: Long)   // kind: 0 mole, 1 golden mole (bonus), 2 bomb

/** A mole (or the golden one) peeking out, or a bomb with a lit fuse. [up] 0..1 is how far it has risen. */
private fun DrawScope.molePeek(kind: Int, up: Float, light: Color) {
    val w = size.width; val h = size.height
    val hole = Offset(w / 2, h * .72f)
    val hw = w * .4f; val hh = h * .1f
    // the hole: a dark opening with a faint rim of the game's light
    drawOval(Color(0xFF0B0824), hole - Offset(hw, hh), Size(hw * 2, hh * 2))
    drawOval(light.copy(alpha = .22f), hole - Offset(hw, hh), Size(hw * 2, hh * 2), style = Stroke(1.dp.toPx()))
    if (up <= 0f) return
    val bw = w * .5f; val bh = h * .5f
    val top = hole.y - bh * up
    clipRect(bottom = hole.y) {
        when (kind) {
            2 -> {
                val c = Offset(hole.x, top + bw * .5f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF4A3F78), Color(0xFF14102E)), center = c - Offset(bw * .15f, bw * .18f), radius = bw * .6f), bw * .45f, c)
                val fuse = c + Offset(bw * .2f, -bw * .5f)
                drawLine(Color(0xFFB9A7FF).copy(alpha = .6f), c + Offset(bw * .1f, -bw * .4f), fuse, 2.dp.toPx())
                glow(fuse, bw * .35f, Color(0xFFFF7D96), .8f)
                drawCircle(Color(0xFFFFD6DE), 3.dp.toPx(), fuse)
            }
            else -> {
                val gold = kind == 1
                val body = if (gold) listOf(Color(0xFFFFF1C9), Color(0xFFFFC56E), Color(0xFFB9852E)) else listOf(Color(0xFFEDE6FF), Color(0xFFB9A7FF), Color(0xFF6E5DB0))
                if (gold) glow(Offset(hole.x, top + bh * .3f), bw, Color(0xFFFFD58A), .45f)
                drawRoundRect(
                    Brush.radialGradient(body, center = Offset(hole.x - bw * .18f, top + bh * .18f), radius = bw * 1.1f),
                    Offset(hole.x - bw / 2, top), Size(bw, bh * 1.2f), CornerRadius(bw / 2, bw / 2),
                )
                val eyeY = top + bh * .32f
                drawCircle(Color(0xFF1C1440), bw * .06f, Offset(hole.x - bw * .16f, eyeY))
                drawCircle(Color(0xFF1C1440), bw * .06f, Offset(hole.x + bw * .16f, eyeY))
                drawOval(Color(0xFFFF9DB5), Offset(hole.x - bw * .07f, eyeY + bw * .1f), Size(bw * .14f, bw * .1f))
                if (gold) drawPath(sparklePath(Offset(hole.x + bw * .42f, top + bh * .05f), bw * .14f), Color.White)
            }
        }
    }
    // the near lip of the hole, so it rises out of it rather than floating in front
    drawArc(Color(0xFF0B0824), 0f, 180f, true, hole - Offset(hw, hh), Size(hw * 2, hh * 2))
    drawArc(light.copy(alpha = .3f), 0f, 180f, false, hole - Offset(hw, hh), Size(hw * 2, hh * 2), style = Stroke(1.dp.toPx()))
}

@Composable
internal fun WhackGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 40_000L
    val spawns = remember(seed) {
        val r = Random(seed)
        val freeAt = LongArray(9)
        buildList {
            var t = 600L
            while (t < total - 400) {
                val life = (1150L - t / 55).coerceAtLeast(620L)
                val free = (0..8).filter { freeAt[it] <= t }
                if (free.isNotEmpty()) {
                    val h = free[r.nextInt(free.size)]
                    val roll = r.nextInt(100)
                    add(MoleSpawn(t, h, if (roll < 14) 2 else if (roll < 22) 1 else 0, life))
                    freeAt[h] = t + life + 120
                }
                t += (700L - t / 80).coerceAtLeast(380L)
            }
        }
    }
    var whacked by remember { mutableStateOf(setOf<Int>()) }
    var points by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val elapsed = rememberClock(true, total) {
        val good = spawns.indices.count { spawns[it].kind != 2 && spawns[it].t + spawns[it].life <= total }
        val got = whacked.count { spawns[it].kind != 2 }
        repeat((good - got).coerceAtLeast(0)) { perf.miss() }
        onDone(points.coerceIn(0, 1000))
    }
    val nowMs by rememberUpdatedState(elapsed)
    val c = T.colors

    fun activeAt(h: Int, t: Long): Int? = spawns.indices.firstOrNull {
        val s = spawns[it]
        s.hole == h && it !in whacked && t >= s.t && t < s.t + s.life
    }
    fun tap(h: Int) {
        val t = nowMs
        val i = activeAt(h, t)
        if (i == null) { streak = 0; perf.miss(); fx.burst(FxKind.Bad); return }
        val s = spawns[i]
        whacked = whacked + i
        when (s.kind) {
            2 -> { points = (points - 15).coerceAtLeast(0); streak = 0; perf.miss(); fx.burst(FxKind.Boom, "−15") }
            1 -> {
                val g = 30 + minOf(streak, 5) * 2
                points += g; streak++; perf.hit(); perf.react((t - s.t).toInt()); fx.burst(FxKind.Coins, "+$g")
            }
            else -> {
                val g = 8 + minOf(streak, 5) * 2
                points += g; streak++; perf.hit(); perf.react((t - s.t).toInt()); fx.burst(FxKind.Stars, "+$g")
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, points, if (streak >= 2) "$streak in a row" else "points")
        Text("Tap the moles, never the bombs", style = Type.footnote, color = c.secondary, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.Center)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
            val gap = 12.dp
            val cell = minDp((maxWidth - gap * 2) / 3, (maxHeight - gap * 2) / 3)
            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(3) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(3) { col ->
                            val h = row * 3 + col
                            val act = activeAt(h, elapsed)
                            val s = act?.let { spawns[it] }
                            val up = if (s == null) 0f else {
                                val inP = ((elapsed - s.t) / 140f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) * (1f - it) }
                                val outP = ((s.t + s.life - elapsed) / 160f).coerceIn(0f, 1f)
                                min(inP, outP)
                            }
                            Canvas(Modifier.size(cell).pointerInput(h) { detectTapGestures(onPress = { tap(h) }) }) {
                                molePeek(s?.kind ?: 0, up, c.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Stack
private class Offcut(val left: Float, val width: Float, var y: Float, val k: Int) { var vy = 0f; var age = 0f }

@Composable
internal fun StackGame(seed: Long, onDone: (Int) -> Unit) {
    val density = LocalDensity.current.density
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors
    val scope = rememberCoroutineScope()
    var box by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val lefts = remember { mutableStateListOf<Float>() }
    val widths = remember { mutableStateListOf<Float>() }
    var pos by remember { mutableFloatStateOf(0f) }
    var dir by remember { mutableFloatStateOf(1f) }
    var perfects by remember { mutableIntStateOf(0) }
    var shift by remember { mutableFloatStateOf(0f) }
    var over by remember { mutableStateOf(false) }
    val cuts = remember { mutableStateListOf<Offcut>() }
    var frame by remember { mutableIntStateOf(0) }
    val sides = remember(seed) { val r = Random(seed); List(120) { r.nextBoolean() } }
    val floors = (lefts.size - 1).coerceAtLeast(0)
    val score = (floors * 10 + perfects * 5).coerceAtMost(1000)
    val lh = 30f * density
    fun scoreNow() = ((lefts.size - 1).coerceAtLeast(0) * 10 + perfects * 5).coerceAtMost(1000)

    LaunchedEffect(box) {
        if (box.width > 0 && lefts.isEmpty()) {
            val bw = box.width * 0.56f
            lefts.add((box.width - bw) / 2f); widths.add(bw)
            pos = 0f; dir = 1f
        }
    }
    LaunchedEffect(box, over) {
        if (box.width == 0) return@LaunchedEffect
        var last = 0L
        while (true) {
            val t = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((t - last) / 1000f).coerceIn(0f, .05f)
            last = t
            // the cut-off pieces fall away and fade
            val it = cuts.iterator()
            while (it.hasNext()) { val p = it.next(); p.vy += 1600f * density * dt; p.y += p.vy * dt; p.age += dt; if (p.age > .9f) it.remove() }
            frame++
            if (over) continue
            if (widths.isEmpty()) continue
            val w = box.width.toFloat()
            val speed = (w * (0.75f + 0.04f * (lefts.size - 1).coerceAtLeast(0))).coerceAtMost(w * 1.7f)
            pos += dir * speed * dt
            val maxPos = w - widths.last()
            if (pos > maxPos) { pos = maxPos; dir = -1f }
            if (pos < 0f) { pos = 0f; dir = 1f }
            shift *= exp(-10f * dt)
        }
    }
    fun drop() {
        if (over || lefts.isEmpty()) return
        val pl = lefts.last(); val pw = widths.last()
        val d = pos - pl
        val tol = box.width * 0.014f
        val yTop = box.height * .52f
        val cx = origin.x + pl + pw / 2; val cy = origin.y + yTop - lh
        if (abs(d) <= tol) {
            lefts.add(pl); widths.add(pw); perfects++; perf.hit(); fx.burstAt(FxKind.Stars, cx, cy, "Perfect")
        } else {
            val overlap = pw - abs(d)
            if (overlap <= 4f * density) {
                cuts.add(Offcut(pos, pw, yTop - lh, lefts.size))
                over = true; perf.miss(); fx.burstAt(FxKind.Boom, cx, cy)
                scope.launch { delay(900); onDone(scoreNow()) }
                return
            }
            // what hangs over is cut off and falls
            if (d > 0) cuts.add(Offcut(pl + pw, d, yTop - lh, lefts.size)) else cuts.add(Offcut(pos, -d, yTop - lh, lefts.size))
            lefts.add(max(pos, pl)); widths.add(overlap); perf.miss(); fx.burstAt(FxKind.Ring, cx, cy)
        }
        shift = lh
        val right = sides[lefts.size % sides.size]
        dir = if (right) -1f else 1f
        pos = if (dir > 0f) 0f else box.width - widths.last()
    }

    Column(Modifier.fillMaxSize()) {
        RoundHud(score, "Floors", "$floors", detail = if (perfects > 0) "$perfects perfect" else "points")
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }.onGloballyPositioned { origin = it.positionInParent() }
                .pointerInput(Unit) { detectTapGestures(onPress = { drop() }) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                frame // redraw every frame
                val h = size.height
                val yTop = h * 0.52f
                val n = lefts.size
                for (k in 0 until n) {
                    val y = yTop + (n - 1 - k) * lh - shift
                    if (y > h) continue
                    stackBlock(k, Offset(lefts[k], y), Size(widths[k], lh - 3f * density), c.accent, c.me, k == n - 1)
                }
                for (p in cuts) {
                    val a = (1f - p.age / .9f).coerceIn(0f, 1f)
                    stackBlock(p.k, Offset(p.left, p.y), Size(p.width, lh - 3f * density), c.accent, c.me, false, a)
                }
                if (!over && n > 0) stackBlock(n, Offset(pos, yTop - lh - shift), Size(widths.last(), lh - 3f * density), c.accent, c.me, true)
            }
            if (floors == 0) Text("Tap to drop the block", style = Type.callout, color = c.secondary, modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
        }
    }
}

/** One floor of the tower: a slab of light that cools from the game's colour to periwinkle as the tower grows. */
private fun DrawScope.stackBlock(k: Int, at: Offset, sz: Size, warm: Color, cool: Color, top: Boolean, alpha: Float = 1f) {
    val f = ((k % 24) / 12f).let { if (it > 1f) 2f - it else it }
    val col = lerp(warm, cool, f)
    val r = CornerRadius(8f * density)
    if (top) glow(at + Offset(sz.width / 2, sz.height / 2), sz.width * .7f, col, .28f * alpha)
    drawRoundRect(Brush.verticalGradient(listOf(lerp(col, Color.White, .35f).copy(alpha = .95f * alpha), col.copy(alpha = .78f * alpha)), startY = at.y, endY = at.y + sz.height), at, sz, r)
    drawRoundRect(Color.White.copy(alpha = .5f * alpha), at + Offset(sz.width * .04f, 1.5f * density), Size(sz.width * .92f, 1.2f * density), CornerRadius(1f * density))
}

// ------------------------------------------------------------------ Flappy Heart
@Composable
internal fun FlappyGame(seed: Long, onDone: (Int) -> Unit) {
    val density = LocalDensity.current.density
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors
    val scope = rememberCoroutineScope()
    var box by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var started by remember { mutableStateOf(false) }
    var dead by remember { mutableStateOf(false) }
    var y by remember { mutableFloatStateOf(0f) }
    var vy by remember { mutableFloatStateOf(0f) }
    var scroll by remember { mutableFloatStateOf(0f) }
    var passed by remember { mutableIntStateOf(0) }
    val trail = remember { ArrayDeque<Float>() }
    val gaps = remember(seed) {
        val r = Random(seed)
        var prev = .5f
        List(140) { prev = (prev + (r.nextFloat() - .5f) * .5f).coerceIn(.24f, .76f); prev }
    }
    val spacing = 200f * density
    val pipeW = 58f * density
    val radius = 14f * density
    val birdX = box.width * 0.28f
    val firstX = box.width * 0.9f

    LaunchedEffect(box) { if (box.height > 0 && !started) y = box.height * 0.45f }
    fun flap() {
        if (dead) return
        if (!started) started = true
        vy = -430f * density
    }
    LaunchedEffect(started, box) {
        if (!started || box.width == 0) return@LaunchedEffect
        var last = 0L
        while (true) {
            val t = withFrameMillis { it }
            val dt = if (last == 0L) 0.016f else ((t - last) / 1000f).coerceIn(0f, .04f)
            last = t
            val h = box.height.toFloat()
            vy += 1350f * density * dt
            y = (y + vy * dt).coerceAtMost(h - radius)
            if (dead) { if (y >= h - radius) break; continue }
            scroll += 165f * density * dt
            trail.addFirst(y); if (trail.size > 12) trail.removeLast()
            if (y < radius) { y = radius; if (vy < 0f) vy = 0f }
            // score: pipes the heart has fully passed
            val q = (scroll + birdX - firstX - pipeW) / spacing
            val nowPassed = (if (q < 0f) 0 else q.toInt() + 1).coerceIn(0, gaps.size)
            if (nowPassed > passed) { passed = nowPassed; perf.hit(); fx.burstAt(FxKind.Stars, origin.x + birdX, origin.y + y, "+10") }
            // crash?
            var crash = y >= h - radius
            val gapH = (170f - min(passed, 30) * 1.6f) * density
            for (i in 0 until gaps.size) {
                val px = firstX + i * spacing - scroll
                if (px > birdX + radius) break
                if (px + pipeW < birdX - radius) continue
                val gc = gaps[i] * h
                if (y - radius < gc - gapH / 2f || y + radius > gc + gapH / 2f) crash = true
            }
            if (crash) {
                dead = true; perf.miss(); trail.clear()
                fx.burstAt(FxKind.Boom, origin.x + birdX, origin.y + y)
                scope.launch { delay(900); onDone((passed * 10).coerceAtMost(1000)) }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        RoundHud((passed * 10).coerceAtMost(1000), "Gaps", "$passed", detail = "points")
        Box(
            Modifier.weight(1f).fillMaxWidth().onSizeChanged { box = it }.onGloballyPositioned { origin = it.positionInParent() }
                .pointerInput(Unit) { detectTapGestures(onPress = { flap() }) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height
                val gapH = (170f - min(passed, 30) * 1.6f) * density
                for (i in 0 until gaps.size) {
                    val px = firstX + i * spacing - scroll
                    if (px > size.width) break
                    if (px + pipeW < 0f) continue
                    val gc = gaps[i] * h
                    val topEnd = gc - gapH / 2f; val botStart = gc + gapH / 2f
                    // columns of soft light, brightest at the edge of the gap
                    drawRoundRect(Brush.verticalGradient(listOf(c.accent.copy(alpha = .05f), c.accent.copy(alpha = .30f)), startY = 0f, endY = topEnd), Offset(px, -20f * density), Size(pipeW, topEnd + 20f * density), CornerRadius(14f * density))
                    drawRoundRect(Brush.verticalGradient(listOf(c.accent.copy(alpha = .30f), c.accent.copy(alpha = .05f)), startY = botStart, endY = h), Offset(px, botStart), Size(pipeW, h - botStart + 20f * density), CornerRadius(14f * density))
                    drawLine(lerp(c.accent, Color.White, .5f), Offset(px + 6f * density, topEnd), Offset(px + pipeW - 6f * density, topEnd), 2f * density)
                    drawLine(lerp(c.accent, Color.White, .5f), Offset(px + 6f * density, botStart), Offset(px + pipeW - 6f * density, botStart), 2f * density)
                    glow(Offset(px + pipeW / 2, topEnd), pipeW * .8f, c.accent, .25f)
                    glow(Offset(px + pipeW / 2, botStart), pipeW * .8f, c.accent, .25f)
                }
                // a fading trail of light behind the heart
                trail.forEachIndexed { k, ty -> drawCircle(c.accent.copy(alpha = .22f * (1f - k / 12f)), radius * (.55f - k * .03f), Offset(birdX - k * 7f * density, ty)) }
                val tilt = (vy / (900f * density)).coerceIn(-.5f, .8f) * 40f
                rotate(tilt, Offset(birdX, y)) { softHeart(Offset(birdX, y), radius * 1.7f, c.accent, dim = if (dead) 1f else 0f) }
            }
            if (!started) Text("Tap to fly", style = Type.title, color = c.label, modifier = Modifier.align(Alignment.Center).padding(top = 120.dp))
        }
    }
}

// ------------------------------------------------------------------ Colour Clash (Stroop)
private val STROOP_NAMES = listOf("Red", "Blue", "Green", "Yellow")
private val STROOP_COLORS = listOf(Color(0xFFFF6B7D), Color(0xFF6FA8FF), Color(0xFF6FD39A), Color(0xFFFFD05C))

@Composable
internal fun StroopGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 30_000L
    val rounds = remember(seed) {
        val r = Random(seed)
        List(300) {
            val word = r.nextInt(4)
            val ink = if (r.nextInt(100) < 75) (word + 1 + r.nextInt(3)) % 4 else word      // mostly a clash
            word to ink
        }
    }
    var idx by remember { mutableIntStateOf(0) }
    var points by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }
    val flash = rememberKeyFlash()
    val perf = LocalPerf.current
    val fx = LocalFx.current
    var shownAt by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val elapsed = rememberClock(true, total) { onDone(points.coerceIn(0, 1000)) }
    val c = T.colors
    val (word, ink) = rounds[idx.coerceAtMost(rounds.lastIndex)]
    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, points, if (streak >= 2) "$streak in a row" else "points")
        Spacer(Modifier.weight(1f))
        Text("Tap the colour you see", style = Type.footnote, color = c.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) { glow(center, size.minDimension * .6f, STROOP_COLORS[ink], .22f) }
            Text(
                STROOP_NAMES[word], style = Type.numberHuge.copy(fontSize = 76.sp, lineHeight = 80.sp, fontWeight = FontWeight.SemiBold), color = STROOP_COLORS[ink],
                textAlign = TextAlign.Center, maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        AnswerGrid(STROOP_NAMES, flash) { k ->
            val ms = (SystemClock.elapsedRealtime() - shownAt).toInt()
            if (k == ink) {
                val g = 10 + minOf(streak, 5) * 2
                points += g; streak++; perf.hit(); perf.react(ms)
                fx.burst(if (ms < 600) FxKind.Confetti else FxKind.Stars, "+$g"); flash.show(k, true)
            } else {
                points = (points - 5).coerceAtLeast(0); streak = 0; perf.miss(); fx.burst(FxKind.Bad, "−5"); flash.show(k, false)
            }
            shownAt = SystemClock.elapsedRealtime()
            idx++
        }
    }
}

// ------------------------------------------------------------------ Word Duel
private val WORDS = (
    "ABOUT ABOVE ACTOR ADAPT ADMIT ADOPT AFTER AGAIN AGREE AHEAD ALARM ALBUM ALIVE ALLOW ALONE ALONG ANGEL ANGER ANGLE ANKLE APART APPLE APPLY ARENA ARGUE ARISE ARROW ASIDE AUDIO AVOID AWAKE AWARD AWARE " +
    "BADGE BAKER BASIC BEACH BEARD BEAST BEGIN BEING BELOW BENCH BERRY BIRTH BLACK BLADE BLAME BLANK BLAZE BLEND BLESS BLIND BLOCK BLOOM BOARD BOAST BONUS BOOST BRAIN BRAND BRAVE BREAD BREAK BRICK BRIDE BRIEF BRING BROAD BROWN BRUSH BUILD BUNCH " +
    "CABIN CABLE CANDY CARGO CARRY CATCH CAUSE CHAIN CHAIR CHALK CHARM CHART CHASE CHEAP CHECK CHEEK CHEER CHEST CHIEF CHILD CHOIR CIVIL CLAIM CLASS CLEAN CLEAR CLERK CLICK CLIMB CLOCK CLOSE CLOUD COACH COAST COUCH COUNT COURT COVER CRAFT CRANE CRASH CRAZY CREAM CRISP CROWD CROWN CRUSH CURVE CYCLE " +
    "DAILY DAIRY DAISY DANCE DEALT DEATH DELAY DEPTH DIARY DIRTY DOUBT DOZEN DRAFT DRAMA DREAM DRESS DRIFT DRINK DRIVE EAGER EARLY EARTH EIGHT ELITE EMPTY ENEMY ENJOY ENTER ENTRY EQUAL ERROR EVENT EVERY EXACT EXTRA " +
    "FAIRY FAITH FALSE FANCY FEAST FENCE FIELD FIFTH FIGHT FINAL FIRST FLAME FLASH FLEET FLOAT FLOOD FLOOR FLOUR FLUTE FOCUS FORCE FORTH FOUND FRAME FRESH FRONT FROST FRUIT FUNNY " +
    "GHOST GIANT GLASS GLOBE GLORY GLOVE GRACE GRADE GRAIN GRAND GRANT GRAPE GRASS GREAT GREEN GREET GRILL GROUP GUARD GUESS GUEST GUIDE HAPPY HARSH HEART HEAVY HENCE HONEY HONOR HORSE HOTEL HOUSE HUMAN HUMOR " +
    "IDEAL IMAGE INDEX INNER INPUT IRONY JELLY JEWEL JOINT JUICE KNIFE KNOCK LABEL LARGE LASER LATER LAUGH LAYER LEARN LEASE LEAST LEAVE LEGAL LEMON LEVEL LIGHT LIMIT LINEN LOCAL LOGIC LOOSE LOVER LUCKY LUNCH MAGIC MAJOR MAKER MANGO MARCH MATCH MAYBE MAYOR MEDAL MERCY METAL METER MIGHT MINOR MIXED MODEL MONEY MONTH MORAL MOTOR MOUNT MOUSE MOUTH MOVIE MUSIC " +
    "NAIVE NERVE NEVER NIGHT NOBLE NOISE NORTH NOTED NOVEL NURSE OASIS OCEAN OFFER OFTEN OLIVE ONION OPERA ORBIT ORDER OTHER OUTER OWNER PAINT PANEL PAPER PARTY PASTA PATCH PAUSE PEACE PEACH PEARL PHASE PHONE PHOTO PIANO PILOT PITCH PIZZA PLACE PLAIN PLANE PLANT PLATE PLAZA POINT POUND POWER PRESS PRICE PRIDE PRIME PRINT PRIZE PROOF PROUD PUPIL PURSE " +
    "QUEEN QUEST QUICK QUIET QUILT QUOTE RADIO RAISE RALLY RANCH RANGE RAPID RATIO REACH READY REALM REBEL RELAX REPLY RIDER RIDGE RIGHT RIVER ROBOT ROCKY ROMAN ROUGH ROUND ROUTE ROYAL RURAL SALAD SCALE SCARF SCENE SCOUT SENSE SERVE SEVEN SHADE SHAKE SHAPE SHARE SHARP SHEEP SHEET SHELF SHELL SHINE SHIRT SHORE SHORT SIGHT SILLY SINCE SKILL SLEEP SLICE SLIDE SMALL SMART SMILE SMOKE SNACK SNAKE SOLAR SOLID SOUND SOUTH SPACE SPARE SPARK SPEAK SPEED SPELL SPEND SPICE SPOON SPORT SQUAD STAGE STAIR STAMP STAND STARE START STATE STEAM STEEL STICK STILL STONE STORE STORM STORY STOVE STRAW STUDY STUFF STYLE SUGAR SUNNY SUPER SWAMP SWEET SWIFT SWING SWORD " +
    "TABLE TASTE TEACH TEETH THANK THEME THICK THING THINK THIRD THOSE THREE THROW THUMB TIGER TIGHT TIMER TIRED TITLE TOAST TODAY TOOTH TOPIC TORCH TOTAL TOUCH TOUGH TOWEL TOWER TRACK TRADE TRAIL TRAIN TREAT TREND TRIAL TRIBE TRICK TRUCK TRULY TRUST TRUTH TULIP TWICE UNCLE UNDER UNION UNITY UNTIL UPPER UPSET URBAN USAGE USUAL VALUE VIDEO VIRUS VISIT VITAL VOCAL VOICE WAGON WASTE WATCH WATER WEARY WEAVE WHEAT WHEEL WHERE WHICH WHILE WHITE WHOLE WHOSE WOMAN WORLD WORRY WORTH WOULD WRITE WRONG YACHT YOUNG YOUTH ZEBRA"
    ).split(" ").filter { it.length == 5 }
private val KEY_ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
private val GREEN = Color(0xFF52BE92)
private val YELLOW = Color(0xFFDDAE52)
private val GRAY = Color(0xFF2E2960)

/** 2 = right letter, right place; 1 = in the word elsewhere; 0 = not in the word (standard Wordle rules, repeats handled). */
private fun grade(guess: String, answer: String): List<Int> {
    val res = MutableList(5) { 0 }
    val left = answer.toMutableList()
    for (i in 0 until 5) if (guess[i] == answer[i]) { res[i] = 2; left[i] = '.' }
    for (i in 0 until 5) if (res[i] == 0) {
        val k = left.indexOf(guess[i])
        if (k >= 0) { res[i] = 1; left[k] = '.' }
    }
    return res
}

/** One letter of the grid. A graded letter turns over to show its colour, one after another along the row. */
@Composable
private fun WordTile(ch: Char?, res: Int?, typing: Boolean, col: Int, size: androidx.compose.ui.unit.Dp) {
    val c = T.colors
    val turn = remember { Animatable(if (res != null) 1f else 0f) }
    LaunchedEffect(res) { if (res != null && turn.value < 1f) { delay(col * 110L); turn.animateTo(1f, tween(340, easing = EaseOutStrong)) } }
    val shown = turn.value > .5f
    val bg = if (res != null && shown) when (res) { 2 -> GREEN; 1 -> YELLOW; else -> GRAY } else c.card
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier.size(size).graphicsLayer { rotationX = turn.value * 180f * (if (shown) -1f else 1f) + if (shown) 180f else 0f; cameraDistance = 10f * density }
            .clip(shape).background(bg).border(1.dp, if (typing) c.accent.copy(alpha = .8f) else if (res != null && shown) Color.Transparent else Color.White.copy(alpha = .10f), shape),
        contentAlignment = Alignment.Center,
    ) { if (ch != null) Text("$ch", style = Type.title.copy(fontSize = (size.value * .48f).sp), color = Color.White) }
}

@Composable
internal fun WordGame(seed: Long, onDone: (Int) -> Unit) {
    val total = 180_000L
    val answer = remember(seed) { WORDS[Random(seed).nextInt(WORDS.size)] }
    var guesses by remember { mutableStateOf(listOf<String>()) }
    var current by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val scope = rememberCoroutineScope()
    val c = T.colors
    val startedAt = remember { SystemClock.elapsedRealtime() }

    fun partial(): Int {
        var best = 0
        for (g in guesses) { val r = grade(g, answer); best = max(best, r.count { it == 2 } * 15 + r.count { it == 1 } * 5) }
        return best
    }
    fun finish(solved: Boolean) {
        if (finished) return
        finished = true
        val secs = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
        val score = if (solved) 400 + (7 - guesses.size) * 80 + (120 - (secs - 30).coerceAtLeast(0)).coerceIn(0, 120) else partial()
        if (solved) { perf.hit(); perf.react(secs * 1000) } else perf.miss()
        scope.launch { delay(if (solved) 1200 else 1500); onDone(score.coerceIn(0, 1000)) }
    }
    val elapsed = rememberClock(!finished, total) { if (!finished) { finish(false) } }

    val letterState = remember(guesses) {
        val m = HashMap<Char, Int>()
        for (g in guesses) { val r = grade(g, answer); for (i in 0 until 5) m[g[i]] = max(m[g[i]] ?: 0, r[i] + 1) }
        m
    }
    fun submit() {
        if (finished) return
        if (current.length < 5) { shake = true; fx.burst(FxKind.Bad, "5 letters"); return }
        val g = current
        guesses = guesses + g
        current = ""
        val r = grade(g, answer)
        scope.launch {
            delay(5 * 110L + 200)       // let the row turn over first
            if (r.all { it == 2 }) fx.burstAt(FxKind.Confetti, fx.w / 2f, fx.h * .35f, "Solved")
            else if (r.count { it == 2 } >= 3) fx.burstAt(FxKind.Stars, fx.w / 2f, fx.h * .35f)
        }
        if (r.all { it == 2 }) finish(true) else if (guesses.size >= 6) finish(false)
    }
    LaunchedEffect(shake) { if (shake) { delay(300); shake = false } }

    Column(Modifier.fillMaxSize()) {
        Hud(total - elapsed, total, (guesses.size + if (finished) 0 else 1).coerceAtMost(6), "guess of 6")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            val gap = 6.dp
            val tile = minDp((maxHeight - gap * 5) / 6, (maxWidth - gap * 4) / 5).coerceIn(30.dp, 56.dp)
            Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(gap)) {
                repeat(6) { row ->
                    val word = guesses.getOrNull(row)
                    val typing = if (row == guesses.size && !finished) current else ""
                    val res = word?.let { grade(it, answer) }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.graphicsLayer { if (shake && row == guesses.size) translationX = 10f }) {
                        repeat(5) { col ->
                            key(row, col, word) { WordTile(word?.get(col) ?: typing.getOrNull(col), res?.get(col), word == null && col < typing.length, col, tile) }
                        }
                    }
                }
            }
        }
        // keyboard
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp).padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KEY_ROWS.forEachIndexed { r, keys ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)) {
                    if (r == 2) KeyCap("Enter", Modifier.weight(1.5f), c.accent.copy(alpha = .3f)) { submit() }
                    keys.forEach { ch ->
                        val bg = when (letterState[ch]) { 3 -> GREEN; 2 -> YELLOW; 1 -> GRAY.copy(alpha = .6f); else -> c.card }
                        KeyCap("$ch", Modifier.weight(1f), bg, dim = letterState[ch] == 1) { if (!finished && current.length < 5) current += ch }
                    }
                    if (r == 2) KeyCap("", Modifier.weight(1.5f), c.card, back = true) { if (!finished && current.isNotEmpty()) current = current.dropLast(1) }
                }
            }
            if (finished) Text(
                if (guesses.lastOrNull() == answer) "Solved in ${guesses.size}" else "The word was $answer",
                style = Type.headline, color = c.label, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun KeyCap(label: String, modifier: Modifier, bg: Color, dim: Boolean = false, back: Boolean = false, onTap: () -> Unit) {
    val c = T.colors
    Box(
        modifier.height(46.dp).clip(RoundedCornerShape(9.dp)).background(bg).border(1.dp, Color.White.copy(alpha = .06f), RoundedCornerShape(9.dp))
            .pointerInput(label, back) { detectTapGestures(onPress = { onTap() }) },
        contentAlignment = Alignment.Center,
    ) {
        if (back) Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete", tint = c.label, modifier = Modifier.size(18.dp))
        else Text(label, style = Type.footnote.copy(fontWeight = FontWeight.Medium, fontSize = if (label.length > 1) 12.sp else 16.sp), color = if (dim) c.tertiary else c.label, maxLines = 1)
    }
}

// ------------------------------------------------------------------ Blackjack
private enum class BjPhase { Bet, Player, Dealer, Result, End }

private fun cardRank(id: Int) = id % 13 + 1                       // 1 = ace .. 13 = king
private fun cardSuit(id: Int) = id / 13 % 4
private fun rankText(id: Int): String = when (val k = cardRank(id)) { 1 -> "A"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; else -> "$k" }
private fun suitText(id: Int): String = listOf("♠", "♥", "♦", "♣")[cardSuit(id)]
/** Best total of a hand (aces count 11 while that does not bust). */
private fun handTotal(cards: List<Int>): Int {
    var t = 0; var aces = 0
    for (c in cards) { val r = cardRank(c); t += if (r == 1) { aces++; 11 } else min(r, 10) }
    while (t > 21 && aces > 0) { t -= 10; aces-- }
    return t
}
private fun isBlackjack(cards: List<Int>) = cards.size == 2 && handTotal(cards) == 21

/** A card that slides in from the shoe. Its back is night blue with a gold line and the app's star. */
@Composable
private fun CardView(id: Int, hidden: Boolean = false) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320, easing = EaseOutStrong)) }
    val shape = RoundedCornerShape(10.dp)
    val red = cardSuit(id) == 1 || cardSuit(id) == 2
    val ink = if (red) Color(0xFFD23F63) else Color(0xFF231B4D)
    Box(
        Modifier.size(width = 58.dp, height = 84.dp)
            .graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * -28.dp.toPx(); rotationZ = (1f - enter.value) * -6f }
            .clip(shape)
            .background(if (hidden) Brush.verticalGradient(listOf(Color(0xFF3B2C8E), Color(0xFF1E165A))) else Brush.verticalGradient(listOf(Color(0xFFFFFBF4), Color(0xFFEFE7F6))))
            .border(1.dp, if (hidden) Color(0xFFFFD58A).copy(alpha = .5f) else Color.White.copy(alpha = .6f), shape),
    ) {
        if (hidden) Canvas(Modifier.fillMaxSize()) { glow(center, size.minDimension * .6f, Color(0xFFFFD58A), .25f); drawPath(sparklePath(center, size.minDimension * .22f), Color(0xFFFFE2A8)) }
        else {
            Text(rankText(id), style = Type.headline.copy(fontSize = 17.sp), color = ink, modifier = Modifier.padding(start = 6.dp, top = 3.dp))
            Text(suitText(id), style = Type.title.copy(fontSize = 26.sp), color = ink, modifier = Modifier.align(Alignment.Center).padding(top = 10.dp))
        }
    }
}

@Composable
internal fun BlackjackGame(seed: Long, onDone: (Int) -> Unit) {
    val handsTotal = 6
    val startChips = 500
    val shoe = remember(seed) { (0 until 104).map { it % 52 }.shuffled(Random(seed)) }     // two decks, same order for both of you
    var deckPos by remember { mutableIntStateOf(0) }
    var chips by remember { mutableIntStateOf(startChips) }
    var handNo by remember { mutableIntStateOf(1) }
    var bet by remember { mutableIntStateOf(50) }
    var player by remember { mutableStateOf(listOf<Int>()) }
    var dealer by remember { mutableStateOf(listOf<Int>()) }
    var phase by remember { mutableStateOf(BjPhase.Bet) }
    var doubled by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Place your bet") }
    var delta by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    val perf = LocalPerf.current
    val fx = LocalFx.current
    val c = T.colors
    val bets = listOf(25, 50, 100, 250)

    fun draw(): Int { val card = shoe[deckPos % shoe.size]; deckPos++; return card }
    fun settle(text: String, net: Int, kind: FxKind?) {
        delta = net; chips = (chips + net).coerceAtLeast(0); message = text; revealed = true
        if (net > 0) perf.hit() else if (net < 0) perf.miss()
        if (kind != null) fx.burstAt(kind, fx.w / 2f, fx.h * .5f, if (net > 0) "+$net" else if (net < 0) "−${-net}" else "Push")
        phase = if (handNo >= handsTotal || chips <= 0) BjPhase.End else BjPhase.Result
    }
    fun deal() {
        val b = min(bet, chips)
        bet = b
        val p = listOf(draw()); val d = listOf(draw())
        val p2 = p + draw(); val d2 = d + draw()
        player = p2; dealer = d2; doubled = false; revealed = false; delta = 0
        val pb = isBlackjack(p2); val db = isBlackjack(d2)
        when {
            pb && db -> settle("Both have blackjack. Push", 0, null)
            pb -> settle("Blackjack, pays 3 to 2", b * 3 / 2, FxKind.Coins)
            db -> settle("Dealer has blackjack", -b, FxKind.Bad)
            else -> { phase = BjPhase.Player; message = "Hit, stand or double" }
        }
    }
    fun standNow() { phase = BjPhase.Dealer; message = "Dealer plays" }
    fun hit() {
        player = player + draw()
        val t = handTotal(player)
        if (t > 21) settle("Bust with $t", -(if (doubled) bet * 2 else bet), FxKind.Boom)
        else if (t == 21) standNow()
    }
    fun double() {
        doubled = true
        player = player + draw()
        val t = handTotal(player)
        if (t > 21) settle("Bust with $t", -bet * 2, FxKind.Boom) else standNow()
    }
    // the dealer reveals and draws to 17
    LaunchedEffect(phase) {
        if (phase != BjPhase.Dealer) return@LaunchedEffect
        revealed = true
        delay(600)
        while (handTotal(dealer) < 17) { delay(650); dealer = dealer + draw() }
        delay(450)
        val pt = handTotal(player); val dt = handTotal(dealer)
        val stake = if (doubled) bet * 2 else bet
        when {
            dt > 21 -> settle("Dealer busts with $dt", stake, FxKind.Coins)
            pt > dt -> settle("You win, $pt to $dt", stake, FxKind.Coins)
            pt < dt -> settle("Dealer wins, $dt to $pt", -stake, FxKind.Bad)
            else -> settle("Push at $pt", 0, null)
        }
    }

    Column(Modifier.fillMaxSize()) {
        RoundHud(chips, "Hand", "${handNo.coerceAtMost(handsTotal)} of $handsTotal", detail = "chips")
        Spacer(Modifier.weight(1f))
        // dealer
        Text("Dealer" + if (dealer.isNotEmpty() && revealed) " · ${handTotal(dealer)}" else "", style = Type.footnote, color = c.secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Row(Modifier.fillMaxWidth().height(92.dp).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            dealer.forEachIndexed { i, card -> key(i, card) { CardView(card, hidden = i == 1 && !revealed) } }
        }
        Spacer(Modifier.height(14.dp))
        Text(message, style = Type.title.copy(fontSize = 20.sp), color = if (delta > 0) Color(0xFFFFD58A) else if (delta < 0) Color(0xFFFF9DB2) else c.label, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), textAlign = TextAlign.Center, maxLines = 2)
        Spacer(Modifier.height(14.dp))
        // player
        Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            player.forEachIndexed { i, card -> key(i, card) { CardView(card) } }
        }
        Text("You" + if (player.isNotEmpty()) " · ${handTotal(player)}" else "", style = Type.footnote, color = c.secondary, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (phase) {
                BjPhase.Bet -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bets.forEach { b ->
                            val ok = b <= chips
                            GlassKey(Modifier.weight(1f).height(48.dp), if (b == bet && ok) Lit.On else Lit.Idle, 16.dp, onTap = { if (ok) bet = b }) {
                                Text("$b", style = Type.headline.copy(fontFeatureSettings = "tnum"), color = if (!ok) c.tertiary else c.label)
                            }
                        }
                    }
                    PrimaryButton("Deal  ·  bet ${min(bet, chips)}", onClick = { deal() }, color = Ivory)
                }
                BjPhase.Player -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) { PrimaryButton("Hit", onClick = { hit() }, color = Ivory) }
                    GlassKey(Modifier.weight(1f).height(52.dp), onTap = { standNow() }) { Text("Stand", style = Type.headline, color = c.label) }
                    if (player.size == 2 && chips >= bet * 2) GlassKey(Modifier.weight(1f).height(52.dp), onTap = { double() }) { Text("Double", style = Type.headline, color = Color(0xFFFFD58A)) }
                }
                BjPhase.Dealer -> Spacer(Modifier.height(52.dp))
                BjPhase.Result -> PrimaryButton("Next hand", onClick = { bet = bets.filter { it <= chips && it <= bet }.maxOrNull() ?: min(bet, chips); handNo++; player = emptyList(); dealer = emptyList(); revealed = false; delta = 0; message = "Place your bet"; phase = BjPhase.Bet }, color = Ivory)
                BjPhase.End -> PrimaryButton("See my score  ·  $chips chips", onClick = { onDone(chips.coerceIn(0, 5000)) }, color = Ivory)
            }
        }
    }
}
