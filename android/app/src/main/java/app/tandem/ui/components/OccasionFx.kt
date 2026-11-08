package app.tandem.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import app.tandem.data.Holiday
import app.tandem.data.OccasionFx
import app.tandem.ui.pets.rememberReducedMotion
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/*
 * A little weather for special days, drawn over Today: snow, fireworks, drifting hearts, falling petals,
 * rising embers, autumn leaves with a bat or two, pomegranate seeds by candlelight. Every particle's place
 * is worked out from the clock (nothing is stored), it's sparse and soft so it never gets in the way, it
 * lets every touch through, and it holds still when animations are off.
 */

private class Flake(val x: Float, val y: Float, val speed: Float, val size: Float, val sway: Float, val phase: Float, val spin: Float, val tint: Int)

@Composable
fun OccasionEffect(fx: OccasionFx, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    var t by remember { mutableFloatStateOf(0f) }
    if (!reduced) LaunchedEffect(fx) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { t = (it - start) / 1e9f }
    }
    val count = when (fx) { OccasionFx.Snow -> 46; OccasionFx.NewYear -> 34; OccasionFx.Embers -> 30; OccasionFx.Yalda -> 22; else -> 20 }
    val flakes = remember(fx) {
        val r = Random(fx.ordinal * 97 + 3)
        List(count) { Flake(r.nextFloat(), r.nextFloat(), .03f + r.nextFloat() * .05f, .6f + r.nextFloat() * .8f, .01f + r.nextFloat() * .03f, r.nextFloat() * 6.28f, (r.nextFloat() - .5f) * 120f, r.nextInt(4)) }
    }
    Canvas(modifier) {
        val time = t
        when (fx) {
            OccasionFx.Snow -> flakes.forEach { f -> val p = fall(f, time); drawCircle(Color.White.copy(alpha = .55f * f.size), (1.4f + 2.2f * f.size).dp.toPx(), p) }
            OccasionFx.NewYear -> {
                flakes.forEachIndexed { i, f ->
                    val p = fall(f, time)
                    if (i % 2 == 0) drawCircle(Color.White.copy(alpha = .45f * f.size), (1.2f + 1.8f * f.size).dp.toPx(), p)
                    else rotate(f.spin * time + f.phase * 57f, p) {
                        val col = listOf(Color(0xFFFFD58A), Color(0xFFF6F2FF), Color(0xFFFFB3C2), Color(0xFFB9A8FF))[f.tint]
                        drawRect(col.copy(alpha = .8f), p - Offset(3.dp.toPx(), 1.5.dp.toPx()), Size(6.dp.toPx() * f.size, 3.dp.toPx()))
                    }
                }
                // a firework now and then, high in the sky
                for (k in 0 until 2) {
                    val cycle = 3.4f; val phase = ((time + k * 1.7f) % cycle) / cycle
                    val seed = ((time + k * 1.7f) / cycle).toInt() * 7 + k
                    val rr = Random(seed)
                    val c0 = Offset(size.width * (.2f + .6f * rr.nextFloat()), size.height * (.08f + .14f * rr.nextFloat()))
                    val col = listOf(Color(0xFFFFD58A), Color(0xFFFFB3C2), Color(0xFFB9A8FF), Color(0xFF9FE3E8))[rr.nextInt(4)]
                    if (phase < .7f) {
                        val e = phase / .7f; val radius = 46.dp.toPx() * (1f - (1f - e) * (1f - e))
                        for (j in 0 until 14) {
                            val a = j / 14f * 2f * PI.toFloat()
                            drawCircle(col.copy(alpha = (1f - e) * .9f), 2.dp.toPx(), c0 + Offset(cos(a) * radius, sin(a) * radius + e * e * 14.dp.toPx()))
                        }
                    }
                }
            }
            OccasionFx.Hearts -> flakes.forEach { f -> val p = rise(f, time); heart(p, (5f + 4f * f.size).dp.toPx(), listOf(Color(0xFFFF8FA3), Color(0xFFFFB3C2), Color(0xFFFF6F91), Color(0xFFF6C1D0))[f.tint].copy(alpha = .55f)) }
            OccasionFx.Petals, OccasionFx.Leaves -> flakes.forEach { f ->
                val p = fall(f, time)
                val col = if (fx == OccasionFx.Petals) listOf(Color(0xFFFFC7D6), Color(0xFFFFE4EC), Color(0xFFF7A8C0), Color(0xFFFFF1F5))[f.tint]
                else listOf(Color(0xFF9BE3A8), Color(0xFF7CCB8A), Color(0xFFBFE8A0), Color(0xFF6FBF7C))[f.tint]
                rotate(f.spin * time + f.phase * 57f, p) { petal(p, (5f + 4f * f.size).dp.toPx(), col.copy(alpha = .7f)) }
            }
            OccasionFx.Embers -> flakes.forEach { f ->
                val p = rise(f, time, fast = 1.8f)
                val flick = .6f + .4f * sin(time * 9f + f.phase * 5f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC46B).copy(alpha = .8f * flick), Color(0xFFFF6A3D).copy(alpha = 0f)), center = p, radius = 6.dp.toPx() * f.size), 6.dp.toPx() * f.size, p)
                drawCircle(Color(0xFFFFE2A8).copy(alpha = .9f * flick), 1.3.dp.toPx(), p)
            }
            OccasionFx.Halloween -> {
                flakes.forEach { f ->
                    val p = fall(f, time)
                    val col = listOf(Color(0xFFFFA24C), Color(0xFFE8763A), Color(0xFFFFC46B), Color(0xFFB8572E))[f.tint]
                    rotate(f.spin * time + f.phase * 57f, p) { leaf(p, (6f + 4f * f.size).dp.toPx(), col.copy(alpha = .7f)) }
                }
                // two bats crossing the sky now and then
                for (k in 0 until 2) {
                    val e = ((time * .09f + k * .5f) % 1f)
                    val p = Offset(size.width * (-.1f + 1.2f * e), size.height * (.12f + .08f * k) + sin(e * 18f) * 10.dp.toPx())
                    bat(p, 9.dp.toPx(), sin(time * 14f + k), Color(0xFF1A1030).copy(alpha = .8f))
                }
            }
            OccasionFx.Yalda -> {
                // a warm candle glow from below, and pomegranate seeds drifting down
                val flick = .85f + .15f * sin(time * 3.1f) * sin(time * 1.7f)
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFF7A45).copy(alpha = .10f * flick)), startY = size.height * .6f, endY = size.height))
                flakes.forEach { f -> val p = fall(f, time); seed(p, (3.2f + 2f * f.size).dp.toPx(), f.phase) }
            }
            OccasionFx.Sparkles -> flakes.forEach { f ->
                val p = Offset(f.x * size.width, f.y * size.height)
                val a = .5f + .5f * sin(time * 1.6f + f.phase * 4f)
                sparkle4(p, (3f + 3f * f.size).dp.toPx() * a, Color(0xFFBFD8FF).copy(alpha = .7f * a))
            }
        }
    }
}

private fun DrawScope.fall(f: Flake, t: Float): Offset {
    val y = ((f.y + f.speed * t) % 1.1f) - .05f
    return Offset((f.x + sin(t * 1.3f * f.size + f.phase) * f.sway) * size.width, y * size.height)
}

private fun DrawScope.rise(f: Flake, t: Float, fast: Float = 1f): Offset {
    val y = 1.05f - ((f.y + f.speed * fast * t) % 1.1f)
    return Offset((f.x + sin(t * 1.1f * f.size + f.phase) * f.sway) * size.width, y * size.height)
}

private fun DrawScope.heart(p: Offset, s: Float, col: Color) {
    val path = Path().apply {
        moveTo(p.x, p.y + s * .7f)
        cubicTo(p.x - s * 1.25f, p.y - s * .05f, p.x - s * .6f, p.y - s * 1.05f, p.x, p.y - s * .42f)
        cubicTo(p.x + s * .6f, p.y - s * 1.05f, p.x + s * 1.25f, p.y - s * .05f, p.x, p.y + s * .7f)
        close()
    }
    drawPath(path, col)
}

private fun DrawScope.petal(p: Offset, s: Float, col: Color) {
    val path = Path().apply { moveTo(p.x, p.y - s); quadraticTo(p.x + s * .8f, p.y, p.x, p.y + s); quadraticTo(p.x - s * .8f, p.y, p.x, p.y - s); close() }
    drawPath(path, col)
}

private fun DrawScope.leaf(p: Offset, s: Float, col: Color) {
    petal(p, s, col)
    drawLine(col.copy(alpha = .9f), Offset(p.x, p.y - s), Offset(p.x, p.y + s * 1.3f), 1.dp.toPx())
}

private fun DrawScope.bat(p: Offset, s: Float, flap: Float, col: Color) {
    val w = s * (.8f + .4f * flap)
    val path = Path().apply {
        moveTo(p.x, p.y)
        quadraticTo(p.x - w * .6f, p.y - w * .7f, p.x - w * 1.5f, p.y - w * .2f * flap)
        quadraticTo(p.x - w * .9f, p.y + s * .1f, p.x - w * .55f, p.y + s * .3f)
        quadraticTo(p.x - w * .25f, p.y + s * .1f, p.x, p.y + s * .35f)
        quadraticTo(p.x + w * .25f, p.y + s * .1f, p.x + w * .55f, p.y + s * .3f)
        quadraticTo(p.x + w * .9f, p.y + s * .1f, p.x + w * 1.5f, p.y - w * .2f * flap)
        quadraticTo(p.x + w * .6f, p.y - w * .7f, p.x, p.y)
        close()
    }
    drawPath(path, col)
}

private fun DrawScope.seed(p: Offset, s: Float, phase: Float) {
    rotate(phase * 57f, p) {
        val path = Path().apply { moveTo(p.x, p.y - s); quadraticTo(p.x + s * .9f, p.y - s * .2f, p.x, p.y + s); quadraticTo(p.x - s * .9f, p.y - s * .2f, p.x, p.y - s); close() }
        drawPath(path, Brush.radialGradient(listOf(Color(0xFFFF8A95), Color(0xFFC81E3A)), center = p + Offset(-s * .3f, -s * .3f), radius = s * 1.3f), alpha = .85f)
        drawCircle(Color.White.copy(alpha = .6f), s * .2f, p + Offset(-s * .25f, -s * .35f))
    }
}

private fun DrawScope.sparkle4(p: Offset, r: Float, col: Color) {
    val path = Path().apply { moveTo(p.x, p.y - r); quadraticTo(p.x, p.y, p.x + r, p.y); quadraticTo(p.x, p.y, p.x, p.y + r); quadraticTo(p.x, p.y, p.x - r, p.y); quadraticTo(p.x, p.y, p.x, p.y - r); close() }
    drawPath(path, col)
}

/** The day's greeting on Today: a small glass card under the sky. */
@Composable
fun OccasionGreeting(o: Holiday, modifier: Modifier = Modifier) {
    val c = T.colors
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.clip(shape).background(c.card).border(1.dp, c.special.copy(alpha = .3f), shape).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(o.greeting, style = Type.title, color = c.label)
        Text(o.line, style = Type.footnote, color = c.secondary)
    }
}
