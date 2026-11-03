package app.tandem.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * What just happened in a game. Every good kind is light (a ripple, motes drifting up, a few sparkles, a floating
 * number); they differ in size and colour. [Bad] and [Boom] are misses: a soft rose vignette and a small shake.
 */
enum class FxKind { Hearts, Stars, Confetti, Boom, Coins, Bad, Ring }

private enum class Shape { Mote, Spark, Ring, Label }

private class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val life: Float, val shape: Shape, val color: Color, val size: Float,
    val text: String = "", val grow: Float = 0f, val rise: Float = 0f,
) { var age = 0f }

private val Gold = Color(0xFFFFD58A)
private val Rose = Color(0xFFFF7D96)

/**
 * The light of one round. [touch] answers every finger-down with a faint ripple; games call [burst] when something
 * good or bad happens. Good bursts in a row build a streak that warms the round ([warmth] 0..1, read by the
 * backdrop); a miss cools it, washes the edges rose ([vignette]) and gives a small shake.
 */
@Stable
class FxState {
    private val parts = ArrayList<Particle>()
    private var tick by mutableIntStateOf(0)
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    internal var d = 1f
    internal var w = 0f
    internal var h = 0f
    internal var view: View? = null

    /** The game's light. Ripples and motes take this colour. */
    var tint: Color = Color(0xFFFF8FA3)
    var streak = 0; private set
    var warmth = 0f; private set
    var flash = 0f; private set
    var vignette = 0f; private set
    private var shakeAmp = 0f
    var shakeX by mutableFloatStateOf(0f); private set
    var shakeY by mutableFloatStateOf(0f); private set

    fun touch(x: Float, y: Float) {
        lastX = x; lastY = y
        parts.add(Particle(x, y, 0f, 0f, .42f, Shape.Ring, tint.copy(alpha = .55f), 6f * d, grow = 30f * d))
        trim()
    }

    /** A burst at the last touched spot (or the middle of the screen if nothing was touched yet). */
    fun burst(kind: FxKind, label: String? = null) {
        val x = if (lastX.isNaN()) w / 2f else lastX
        val y = if (lastY.isNaN()) h / 2f else lastY
        burstAt(kind, x, y, label)
    }

    fun burstAt(kind: FxKind, x: Float, y: Float, label: String? = null) {
        val bad = kind == FxKind.Bad || kind == FxKind.Boom
        if (bad) miss(x, y, kind == FxKind.Boom) else good(kind, x, y)
        if (label != null) parts.add(Particle(x, y - 22f * d, 0f, 0f, .85f, Shape.Label, if (bad) Rose else if (kind == FxKind.Coins) Gold else tint, 22f * d, label, rise = 46f * d))
        trim()
        tick++
    }

    private fun good(kind: FxKind, x: Float, y: Float) {
        val r = Random
        streak++
        warmth = (warmth + .08f).coerceAtMost(1f)
        flash = if (kind == FxKind.Confetti) 1.6f else 1f
        // bigger moments and longer streaks throw a little more light, never a shower
        val big = when (kind) { FxKind.Confetti -> 1.6f; FxKind.Ring -> .6f; else -> 1f } * (1f + minOf(streak, 8) * .05f)
        val col = if (kind == FxKind.Coins) Gold else tint
        parts.add(Particle(x, y, 0f, 0f, .52f, Shape.Ring, col, 10f * d, grow = 64f * d * big))
        val n = ((if (kind == FxKind.Ring) 5 else 9) + minOf(streak, 8)) * (if (kind == FxKind.Confetti) 2 else 1)
        repeat(n) {
            val a = r.nextFloat() * 6.2832f; val s = (60f + r.nextFloat() * 170f) * d * big
            val spark = r.nextFloat() < .28f
            parts.add(Particle(x, y, cos(a) * s, sin(a) * s - 40f * d, .6f + r.nextFloat() * .55f, if (spark) Shape.Spark else Shape.Mote, if (spark) Color.White else col, (1.4f + r.nextFloat() * 2.2f) * d))
        }
        haptic(false)
    }

    private fun miss(x: Float, y: Float, hard: Boolean) {
        streak = 0
        warmth = (warmth - .3f).coerceAtLeast(0f)
        vignette = if (hard) 1.3f else 1f
        shakeAmp = if (hard) 1.4f else 1f
        parts.add(Particle(x, y, 0f, 0f, .45f, Shape.Ring, Rose, 10f * d, grow = 44f * d))
        repeat(4) {
            val a = Random.nextFloat() * 6.2832f
            parts.add(Particle(x, y, cos(a) * 50f * d, sin(a) * 30f * d + 30f * d, .6f, Shape.Mote, Rose.copy(alpha = .6f), 1.6f * d))
        }
        haptic(true)
    }

    private fun haptic(bad: Boolean) {
        val v = view ?: return
        val k = when {
            !bad -> HapticFeedbackConstants.CLOCK_TICK
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.REJECT
            else -> HapticFeedbackConstants.LONG_PRESS
        }
        v.performHapticFeedback(k)
    }

    private fun trim() { if (parts.size > 240) parts.subList(0, parts.size - 240).clear() }

    internal fun step(dt: Float) {
        flash = (flash - dt * 4f).coerceAtLeast(0f)
        vignette = (vignette - dt * 2.4f).coerceAtLeast(0f)
        warmth = (warmth - dt * .02f).coerceAtLeast(0f)
        if (shakeAmp > 0f) {
            shakeAmp = (shakeAmp - dt * 5f).coerceAtLeast(0f)
            shakeX = (Random.nextFloat() - .5f) * 7f * d * shakeAmp; shakeY = (Random.nextFloat() - .5f) * 7f * d * shakeAmp
        } else if (shakeX != 0f || shakeY != 0f) { shakeX = 0f; shakeY = 0f }
        if (parts.isEmpty()) { if (flash > 0f || vignette > 0f) tick++; return }
        val drag = exp(-3.2f * dt)
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age >= p.life) { it.remove(); continue }
            p.vx *= drag; p.vy = p.vy * drag - 30f * d * dt      // motes float up as they slow, like embers
            p.x += p.vx * dt; p.y += p.vy * dt
        }
        tick++
    }

    internal fun draw(scope: DrawScope) {
        tick // read, so the canvas redraws whenever something moved
        with(scope) {
            if (flash > 0f) drawRect(tint.copy(alpha = (flash * .07f).coerceAtMost(.12f)))
            if (vignette > 0f) drawRect(
                Brush.radialGradient(listOf(Color.Transparent, Color(0xFF8C2A5A).copy(alpha = (vignette * .42f).coerceAtMost(.5f))), center = Offset(size.width / 2, size.height / 2), radius = size.maxDimension * .72f),
            )
        }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        }
        for (p in parts) {
            val t = (p.age / p.life).coerceIn(0f, 1f)
            val fade = 1f - t
            when (p.shape) {
                Shape.Mote -> with(scope) {
                    drawCircle(Brush.radialGradient(listOf(p.color.copy(alpha = .38f * fade), Color.Transparent), center = Offset(p.x, p.y), radius = p.size * 6f), p.size * 6f, Offset(p.x, p.y), blendMode = BlendMode.Plus)
                    drawCircle(Color.White.copy(alpha = .9f * fade), p.size, Offset(p.x, p.y))
                }
                Shape.Spark -> with(scope) {
                    val r = p.size * 2.6f
                    drawPath(spark(p.x, p.y, r), Color.White.copy(alpha = fade))
                }
                Shape.Ring -> with(scope) {
                    val e = 1f - (1f - t) * (1f - t) * (1f - t)
                    drawCircle(p.color.copy(alpha = p.color.alpha * .75f * fade), p.size + p.grow * e, Offset(p.x, p.y), style = Stroke(width = (2.2f * fade + .6f) * d))
                }
                Shape.Label -> scope.drawContext.canvas.nativeCanvas.let { cv ->
                    val inA = (t / .15f).coerceAtMost(1f)
                    val outA = if (t < .15f) 1f else 1f - ((t - .15f) / .85f).let { 1f - (1f - it) * (1f - it) }
                    val a = (255 * inA * outA).toInt().coerceIn(0, 255)
                    paint.textSize = p.size * (1f + .2f * (1f - t))
                    paint.color = android.graphics.Color.argb(a, 255, 255, 255)
                    paint.setShadowLayer(16f * d, 0f, 0f, p.color.copy(alpha = inA * outA).toArgb())
                    val e = 1f - (1f - t) * (1f - t) * (1f - t)
                    cv.drawText(p.text, p.x, p.y - p.rise * e, paint)
                }
            }
        }
    }

    private fun spark(x: Float, y: Float, r: Float) = Path().apply {
        moveTo(x, y - r); quadraticTo(x, y, x + r, y); quadraticTo(x, y, x, y + r)
        quadraticTo(x, y, x - r, y); quadraticTo(x, y, x, y - r); close()
    }
}

@Composable
fun rememberFx(): FxState = remember { FxState() }

/** What a game uses to play a burst. Outside a round it does nothing visible. */
val LocalFx = staticCompositionLocalOf { FxState() }

/** Draws the light of [fx] over whatever is below. Does not take touches. */
@Composable
fun FxLayer(fx: FxState, modifier: Modifier = Modifier) {
    fx.d = LocalDensity.current.density
    fx.view = LocalView.current
    LaunchedEffect(fx) {
        var last = 0L
        while (true) {
            val t = androidx.compose.runtime.withFrameMillis { it }
            val dt = if (last == 0L) .016f else ((t - last) / 1000f).coerceIn(0f, .05f)
            last = t
            fx.step(dt)
        }
    }
    Canvas(modifier) {
        fx.w = size.width; fx.h = size.height
        fx.draw(this)
    }
}

/** Watches every finger-down inside (without taking the touch away from the game) and ripples there. */
fun Modifier.touchFx(fx: FxState): Modifier = pointerInput(fx) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            e.changes.firstOrNull { it.changedToDown() }?.let { fx.touch(it.position.x, it.position.y) }
        }
    }
}
