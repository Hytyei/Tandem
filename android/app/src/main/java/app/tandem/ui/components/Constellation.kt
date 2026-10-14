package app.tandem.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandem.ui.theme.Type
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private class Dust(val x: Float, val y: Float, val r: Float, val phase: Float, val speed: Int)

/** Nodes of the big constellation, in 0..1 of the panel (x across, y down). Stays in the top ~40% so the number can glow below it. */
private val STARS = listOf(
    .05f to .30f, .14f to .16f, .25f to .30f, .34f to .10f, .46f to .20f, .56f to .08f,
    .66f to .24f, .77f to .12f, .87f to .30f, .95f to .16f,
)
/** Index pairs that are joined by a line (a main chain plus a few branches, so it reads as a constellation, not a zig-zag). */
private val LINKS = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8, 8 to 9, 1 to 3, 4 to 6, 6 to 8)

/**
 * Days together as a night sky: dust that twinkles, a wide constellation that draws itself star by star,
 * an occasional shooting star, and then the number forms out of connected, blinking stars.
 */
@Composable
fun ConstellationDays(days: Long, modifier: Modifier = Modifier, height: Dp = 230.dp, caption: String? = null) {
    val text = days.coerceAtLeast(0).toString()
    val n = text.length
    val density = LocalDensity.current.density
    val intro = remember(days) { Animatable(0f) }
    LaunchedEffect(days) { intro.animateTo(1f, tween(4600, easing = LinearEasing)) }

    val t = rememberInfiniteTransition(label = "sky")
    val tw by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "twinkle")
    val shoot by t.animateFloat(0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Restart), label = "shoot")

    val dust = remember { val r = Random(11); List(80) { Dust(r.nextFloat(), r.nextFloat(), .5f + r.nextFloat() * 1.1f, r.nextFloat() * 6.28f, 1 + r.nextInt(3)) } }
    val sparkles = remember { val r = Random(5); List(7) { Dust(.06f + r.nextFloat() * .88f, .06f + r.nextFloat() * .55f, 4f + r.nextFloat() * 4f, r.nextFloat() * 6.28f, 1 + r.nextInt(2)) } }

    val pink = Color(0xFFFF8FA3)
    val violet = Color(0xFF9C8CFF)
    val gold = Color(0xFFFFD27A)

    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF0F0A33), Color(0xFF221A60), Color(0xFF3A2470))))
            .semantics { contentDescription = "$days days together" },
    ) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width; val h = size.height
            val p = intro.value
            val appear = (p / 0.12f).coerceIn(0f, 1f)

            // warm horizon glow
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFB59A).copy(alpha = .22f * appear), Color.Transparent), center = Offset(w / 2, h * 1.05f), radius = w * .7f),
                radius = w * .7f, center = Offset(w / 2, h * 1.05f),
            )

            // background dust
            for (s in dust) {
                val a = (.25f + .75f * (.5f + .5f * sin(tw * s.speed + s.phase))) * appear
                drawCircle(Color.White.copy(alpha = a * .85f), s.r * density, Offset(s.x * w, s.y * h))
            }
            // bigger blinking sparkles
            for (s in sparkles) {
                val k = (.5f + .5f * sin(tw * s.speed + s.phase)).let { it * it } * appear
                val c = Offset(s.x * w, s.y * h)
                val len = s.r * density * (.4f + 1.4f * k)
                val col = Color.White.copy(alpha = .25f + .7f * k)
                drawLine(col, Offset(c.x - len, c.y), Offset(c.x + len, c.y), 1.2f * density, StrokeCap.Round)
                drawLine(col, Offset(c.x, c.y - len), Offset(c.x, c.y + len), 1.2f * density, StrokeCap.Round)
                drawCircle(col, 1.6f * density, c)
            }

            // the constellation draws itself, star by star
            val cp = (p / 0.5f).coerceIn(0f, 1f) * LINKS.size
            val lineBrush = Brush.horizontalGradient(listOf(pink, violet), startX = 0f, endX = w)
            LINKS.forEachIndexed { i, (a, b) ->
                val f = (cp - i).coerceIn(0f, 1f)
                if (f > 0f) {
                    val pa = Offset(STARS[a].first * w, STARS[a].second * h); val pb = Offset(STARS[b].first * w, STARS[b].second * h)
                    drawLine(lineBrush, pa, Offset(pa.x + (pb.x - pa.x) * f, pa.y + (pb.y - pa.y) * f), 1.3f * density, StrokeCap.Round, alpha = .6f)
                }
            }
            STARS.forEachIndexed { i, (sx, sy) ->
                val reveal = ((cp - (i - 1)) / 1f).coerceIn(0f, 1f).let { if (i == 0) (p / 0.05f).coerceIn(0f, 1f) else it }
                if (reveal > 0f) {
                    val pos = Offset(sx * w, sy * h)
                    val blink = .5f + .5f * sin(tw * (1 + i % 3) + i * 1.7f)
                    val r = (3.2f + 1.6f * blink) * density * reveal
                    drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = .55f * reveal), Color.Transparent), center = pos, radius = r * 4f), r * 4f, pos)
                    drawCircle(Color.White.copy(alpha = .75f + .25f * blink), r * .7f, pos)
                }
            }

            // a shooting star now and then, once the sky has settled
            if (p > .9f && shoot < .09f) {
                val u = shoot / .09f
                val head = Offset(w * (.95f - .5f * u), h * (.02f + .30f * u))
                val tail = Offset(head.x + w * .16f, head.y - h * .11f)
                drawLine(Brush.linearGradient(listOf(Color.White.copy(alpha = .9f * (1f - u)), Color.Transparent), start = head, end = tail), head, tail, 2f * density, StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = 1f - u), 2.4f * density, head)
            }

            // the number forms from connected, blinking stars
            val dp = ((p - .38f) / .62f).coerceIn(0f, 1f)
            if (dp > 0f) {
                val digitH = min(h * .34f, 86f * density)
                var cell = digitH / 4f
                val maxW = w * .88f
                val gapK = 1.1f
                val needed = 2 * cell * n + gapK * cell * (n - 1)
                if (needed > maxW) cell *= maxW / needed
                val gap = gapK * cell
                val totalW = 2 * cell * n + gap * (n - 1)
                val left = (w - totalW) / 2f
                val top = h * .50f
                val dh = cell * 4f
                val center = Offset(w / 2f, top + dh / 2f)
                drawCircle(
                    Brush.radialGradient(listOf(pink.copy(alpha = .26f * dp), violet.copy(alpha = .10f * dp), Color.Transparent), center = center, radius = w * .42f),
                    radius = w * .42f, center = center,
                )
                val r = cell * .17f
                val stroke = r * .9f
                val numBrush = Brush.linearGradient(listOf(pink, violet), start = Offset(left, top), end = Offset(left + totalW, top + dh))
                text.forEachIndexed { i, ch ->
                    val ox = left + i * (2 * cell + gap)
                    fun pt(x: Int, y: Int) = Offset(ox + x * cell, top + y * cell)
                    for (gx in 0..2) for (gy in 0..4) drawCircle(Color.White.copy(alpha = .10f * dp), r * .45f, pt(gx, gy))
                    val paths = DIGIT_PATHS[ch] ?: return@forEachIndexed
                    val segments = paths.sumOf { it.size - 1 }
                    val start = i.toFloat() / (n + 1)
                    val local = ((dp - start) / (2f / (n + 1))).coerceIn(0f, 1f)
                    var k = 0
                    for (path in paths) {
                        for (j in 0 until path.size - 1) {
                            val f = (local * segments - k).coerceIn(0f, 1f)
                            if (f > 0f) {
                                val a = pt(path[j].first, path[j].second); val b = pt(path[j + 1].first, path[j + 1].second)
                                drawLine(numBrush, a, Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f), stroke, StrokeCap.Round)
                            }
                            k++
                        }
                    }
                    k = 0
                    for (path in paths) {
                        path.forEachIndexed { j, (x, y) ->
                            val reach = (local * segments - (k + j - 1)).coerceIn(0f, 1f)
                            val s = if (j == 0) (local * segments * 3f).coerceIn(0f, 1f) else reach
                            if (s > 0f) {
                                val o = pt(x, y)
                                val blink = .5f + .5f * sin(tw * 2 + i * 2.3f + x * 1.9f + y * 1.3f)
                                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .35f * s * blink), Color.Transparent), center = o, radius = r * 3.4f), r * 3.4f, o)
                                drawCircle(numBrush, r * 1.25f * s, o)
                                drawCircle(Color.White.copy(alpha = .85f + .15f * blink), r * (.5f + .18f * blink) * s, o)
                            }
                        }
                        k += path.size - 1
                    }
                }
            }
        }
        if (caption != null) Text(
            caption, style = Type.callout, color = Color.White.copy(alpha = .85f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).graphicsLayer { alpha = ((intro.value - .85f) / .15f).coerceIn(0f, 1f) },
        )
    }
}
