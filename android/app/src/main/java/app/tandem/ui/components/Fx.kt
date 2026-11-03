package app.tandem.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The little bursts that play when you touch things in a game. */
enum class FxKind { Hearts, Stars, Confetti, Boom, Coins, Bad, Ring }

private enum class Shape { Glyph, Chip, Ring, Label, Heart, Star, Coin, Cross }

private class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val life: Float, val shape: Shape, val text: String = "", val color: Color = Color.White,
    val size: Float = 20f, val gravity: Float = 0f, val spin: Float = 0f, val grow: Float = 0f,
) {
    var age = 0f
    var angle = 0f
}

/**
 * Particles for one game round. [touch] is called for every finger-down (a soft ripple under the finger);
 * games call [burst] when something good or bad happens, and it plays at the spot that was just touched.
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

    fun touch(x: Float, y: Float) {
        lastX = x; lastY = y
        parts.add(Particle(x, y, 0f, 0f, .38f, Shape.Ring, color = Color(0xFFFFC2D4), size = 10f * d, grow = 56f * d))
        repeat(3) {
            val a = Random.nextFloat() * 6.2832f
            parts.add(Particle(x, y, cos(a) * 90f * d, sin(a) * 90f * d, .35f, Shape.Chip, color = Color(0xFFFFE08A), size = 3.5f * d))
        }
        if (parts.size > 220) parts.subList(0, parts.size - 220).clear()
    }

    /** A burst at the last touched spot (or the middle of the screen if nothing was touched yet). */
    fun burst(kind: FxKind, label: String? = null) {
        val x = if (lastX.isNaN()) w / 2f else lastX
        val y = if (lastY.isNaN()) h / 2f else lastY
        burstAt(kind, x, y, label)
    }

    fun burstAt(kind: FxKind, x: Float, y: Float, label: String? = null) {
        val r = Random
        when (kind) {
            FxKind.Hearts -> repeat(9) {
                val col = listOf(Color(0xFFFF6B8E), Color(0xFFFF9DB5), Color(0xFFE0305F), Color(0xFFC9A2FF))[r.nextInt(4)]
                parts.add(Particle(x, y, (r.nextFloat() - .5f) * 280f * d, -(170f + r.nextFloat() * 260f) * d, .9f + r.nextFloat() * .5f, Shape.Heart, color = col, size = (6f + r.nextFloat() * 6f) * d, gravity = 150f * d, spin = (r.nextFloat() - .5f) * 120f))
            }
            FxKind.Stars -> repeat(10) {
                val a = r.nextFloat() * 6.2832f; val s = (150f + r.nextFloat() * 260f) * d
                val col = listOf(Color(0xFFFFE08A), Color(0xFFFFF4CF), Color(0xFFFFC857))[r.nextInt(3)]
                parts.add(Particle(x, y, cos(a) * s, sin(a) * s, .7f + r.nextFloat() * .4f, Shape.Star, color = col, size = (5f + r.nextFloat() * 6f) * d, gravity = 160f * d, spin = (r.nextFloat() - .5f) * 300f))
            }
            FxKind.Confetti -> repeat(22) {
                val a = r.nextFloat() * 6.2832f; val s = (120f + r.nextFloat() * 380f) * d
                val col = listOf(Color(0xFFF2476F), Color(0xFFFFC857), Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFF9C8CFF))[r.nextInt(5)]
                parts.add(Particle(x, y, cos(a) * s, sin(a) * s - 160f * d, .9f + r.nextFloat() * .5f, Shape.Chip, color = col, size = (4f + r.nextFloat() * 4f) * d, gravity = 620f * d, spin = (r.nextFloat() - .5f) * 700f))
            }
            FxKind.Boom -> {
                parts.add(Particle(x, y, 0f, 0f, .45f, Shape.Ring, color = Color(0xFFFFC857), size = 10f * d, grow = 120f * d))
                parts.add(Particle(x, y, 0f, 0f, .3f, Shape.Coin, color = Color(0xFFFFE6B0), size = 22f * d, grow = -18f * d))
                repeat(10) {
                    val a = r.nextFloat() * 6.2832f; val s = (140f + r.nextFloat() * 260f) * d
                    parts.add(Particle(x, y, cos(a) * s, sin(a) * s, .55f, Shape.Chip, color = listOf(Color(0xFFFF8A3D), Color(0xFFFFC857), Color(0xFFEF4444))[r.nextInt(3)], size = 5f * d, gravity = 300f * d))
                }
            }
            FxKind.Coins -> repeat(7) {
                parts.add(Particle(x, y, (r.nextFloat() - .5f) * 240f * d, -(260f + r.nextFloat() * 240f) * d, .9f + r.nextFloat() * .3f, Shape.Coin, color = Color(0xFFFFC857), size = (6f + r.nextFloat() * 3f) * d, gravity = 700f * d, spin = (r.nextFloat() - .5f) * 500f))
            }
            FxKind.Bad -> {
                parts.add(Particle(x, y, 0f, 0f, .45f, Shape.Ring, color = Color(0xFFFF6B7D), size = 12f * d, grow = 90f * d))
                parts.add(Particle(x, y, 0f, -40f * d, .55f, Shape.Cross, color = Color(0xFFFF6B7D), size = 9f * d))
            }
            FxKind.Ring -> parts.add(Particle(x, y, 0f, 0f, .5f, Shape.Ring, color = Color(0xFFFFFFFF), size = 14f * d, grow = 110f * d))
        }
        if (label != null) {
            val col = if (kind == FxKind.Bad || kind == FxKind.Boom) Color(0xFFFF8E9B) else Color(0xFFFFE08A)
            parts.add(Particle(x, y - 28f * d, 0f, -70f * d, .85f, Shape.Label, label, col, size = 20f * d))
        }
        if (parts.size > 220) parts.subList(0, parts.size - 220).clear()
        tick++
    }

    internal fun step(dt: Float) {
        if (parts.isEmpty()) return
        val it = parts.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age >= p.life) { it.remove(); continue }
            p.vy += p.gravity * dt
            p.x += p.vx * dt; p.y += p.vy * dt
            p.angle += p.spin * dt
        }
        tick++
    }

    internal fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope) {
        tick // read, so the canvas redraws whenever something moved
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.textAlign = android.graphics.Paint.Align.CENTER
        for (p in parts) {
            val t = (p.age / p.life).coerceIn(0f, 1f)
            val fade = if (t < .65f) 1f else (1f - t) / .35f
            when (p.shape) {
                Shape.Glyph -> scope.drawContext.canvas.nativeCanvas.let { cv ->
                    paint.textSize = p.size + p.grow * t
                    paint.alpha = (255 * fade).toInt().coerceIn(0, 255)
                    cv.drawText(p.text, p.x, p.y + paint.textSize * .35f, paint)
                }
                Shape.Chip -> with(scope) {
                    rotate(p.angle, Offset(p.x, p.y)) {
                        drawRoundRect(p.color.copy(alpha = fade), Offset(p.x - p.size, p.y - p.size * .6f), Size(p.size * 2f, p.size * 1.2f), CornerRadius(p.size * .3f))
                    }
                }
                Shape.Ring -> with(scope) {
                    drawCircle(p.color.copy(alpha = .8f * (1f - t)), p.size + p.grow * t, Offset(p.x, p.y), style = Stroke(width = 2.5f * d))
                }
                Shape.Heart -> with(scope) {
                    rotate(p.angle, Offset(p.x, p.y)) {
                        val sz = p.size
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(p.x, p.y + sz * .7f)
                            cubicTo(p.x - sz * 1.25f, p.y - sz * .05f, p.x - sz * .6f, p.y - sz * 1.05f, p.x, p.y - sz * .42f)
                            cubicTo(p.x + sz * .6f, p.y - sz * 1.05f, p.x + sz * 1.25f, p.y - sz * .05f, p.x, p.y + sz * .7f)
                            close()
                        }
                        drawPath(path, p.color.copy(alpha = fade))
                    }
                }
                Shape.Star -> with(scope) {
                    rotate(p.angle, Offset(p.x, p.y)) {
                        val r = p.size
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(p.x, p.y - r); quadraticTo(p.x, p.y, p.x + r, p.y); quadraticTo(p.x, p.y, p.x, p.y + r)
                            quadraticTo(p.x, p.y, p.x - r, p.y); quadraticTo(p.x, p.y, p.x, p.y - r); close()
                        }
                        drawPath(path, p.color.copy(alpha = fade))
                    }
                }
                Shape.Coin -> with(scope) {
                    // a coin seen edge-on as it spins: squash its width with the angle
                    val sz = (p.size + p.grow * t).coerceAtLeast(1f)
                    val squash = kotlin.math.abs(cos(p.angle * 0.0174533f)).coerceAtLeast(.15f)
                    drawOval(p.color.copy(alpha = fade), Offset(p.x - sz * squash, p.y - sz), Size(sz * 2 * squash, sz * 2))
                    drawOval(Color.White.copy(alpha = .5f * fade), Offset(p.x - sz * squash * .5f, p.y - sz * .7f), Size(sz * squash * .6f, sz * .6f))
                }
                Shape.Cross -> with(scope) {
                    val r = p.size
                    drawLine(p.color.copy(alpha = fade), Offset(p.x - r, p.y - r), Offset(p.x + r, p.y + r), 3f * d, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(p.color.copy(alpha = fade), Offset(p.x + r, p.y - r), Offset(p.x - r, p.y + r), 3f * d, androidx.compose.ui.graphics.StrokeCap.Round)
                }
                Shape.Label -> scope.drawContext.canvas.nativeCanvas.let { cv ->
                    paint.textSize = p.size
                    paint.isFakeBoldText = true
                    paint.color = android.graphics.Color.argb((255 * fade).toInt().coerceIn(0, 255), (p.color.red * 255).toInt(), (p.color.green * 255).toInt(), (p.color.blue * 255).toInt())
                    cv.drawText(p.text, p.x, p.y, paint)
                    paint.isFakeBoldText = false
                }
            }
        }
    }
}

@Composable
fun rememberFx(): FxState = remember { FxState() }

/** What a game uses to play a burst. Outside a round it does nothing visible. */
val LocalFx = staticCompositionLocalOf { FxState() }

/** Draws the particles of [fx] over whatever is below. Does not take touches. */
@Composable
fun FxLayer(fx: FxState, modifier: Modifier = Modifier) {
    val d = LocalDensity.current.density
    fx.d = d
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
