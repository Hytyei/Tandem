package app.tandem.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import app.tandem.data.MiniGame
import app.tandem.ui.theme.LocalTandem
import app.tandem.ui.theme.T
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The painted world a game lives in. Each game has its own, so no two rounds look alike. */
internal enum class Scene { Nebula, Constellation, Chalk, Dusk, Meadow, Skyline, Clouds, Kaleido, Range, Horizon, Ripples, Splats, Press, Vault, Felt, Bulbs, Tiles }

/**
 * A game's look: its sky (top to bottom), the accent that marks what you tap, the surface of its buttons and tiles,
 * the ink its text is written in, and the scene painted behind it.
 */
internal class GameSkin(val sky: List<Color>, val accent: Color, val surface: Color, val ink: Color, val scene: Scene)

internal fun MiniGame.skin(): GameSkin = when (this) {
    MiniGame.Heart -> GameSkin(listOf(Color(0xFF3A0F2E), Color(0xFF6E1A45), Color(0xFF2A0B24)), Color(0xFFFF6B8E), Color(0x33FFC2D4), Color(0xFFFFEEF3), Scene.Nebula)
    MiniGame.Memory -> GameSkin(listOf(Color(0xFF130F3A), Color(0xFF241C66), Color(0xFF0C0A28)), Color(0xFFB7A8FF), Color(0x2EC9BEFF), Color(0xFFF1EEFF), Scene.Constellation)
    MiniGame.Sums -> GameSkin(listOf(Color(0xFF0E3A35), Color(0xFF0B2E2A), Color(0xFF07201D)), Color(0xFFF4E9B0), Color(0x24F4F1E4), Color(0xFFF4F1E4), Scene.Chalk)
    MiniGame.Draw -> GameSkin(listOf(Color(0xFF26102F), Color(0xFF7A2A3A), Color(0xFFD9773A)), Color(0xFFFFC857), Color(0x33FFE2B0), Color(0xFFFFF4E2), Scene.Dusk)
    MiniGame.Mole -> GameSkin(listOf(Color(0xFF0D1A30), Color(0xFF173A4C), Color(0xFF10301F)), Color(0xFF8FE3A1), Color(0x2ED7F5DD), Color(0xFFEFFAF1), Scene.Meadow)
    MiniGame.Stack -> GameSkin(listOf(Color(0xFF1B1340), Color(0xFF7C3050), Color(0xFFE07A45)), Color(0xFFFFB067), Color(0x33FFE0C2), Color(0xFFFFF1E4), Scene.Skyline)
    MiniGame.Flap -> GameSkin(listOf(Color(0xFF26307A), Color(0xFF7D4FA8), Color(0xFFE88AA8)), Color(0xFFFFD1E0), Color(0x33FFFFFF), Color(0xFFFFFFFF), Scene.Clouds)
    MiniGame.Odd -> GameSkin(listOf(Color(0xFF2A0F3D), Color(0xFF451A5E), Color(0xFF1D0A2C)), Color(0xFFFF8AD8), Color(0x2EFFD6F2), Color(0xFFFFF0FA), Scene.Kaleido)
    MiniGame.Target -> GameSkin(listOf(Color(0xFF0B1C43), Color(0xFF14306A), Color(0xFF0A1636)), Color(0xFF7FB2FF), Color(0x2ED6E6FF), Color(0xFFEFF5FF), Scene.Range)
    MiniGame.Sequence -> GameSkin(listOf(Color(0xFF120E36), Color(0xFF231A5C), Color(0xFF3B1F63)), Color(0xFFC5B8FF), Color(0x2EDCD3FF), Color(0xFFF3F0FF), Scene.Horizon)
    MiniGame.Echo -> GameSkin(listOf(Color(0xFF052A2C), Color(0xFF0A4644), Color(0xFF042022)), Color(0xFF6EF0D8), Color(0x2EC8FFF4), Color(0xFFEAFFFB), Scene.Ripples)
    MiniGame.Stroop -> GameSkin(listOf(Color(0xFF241A12), Color(0xFF33251A), Color(0xFF1A130C)), Color(0xFFFFC857), Color(0x2EFFF0D8), Color(0xFFFFF7EA), Scene.Splats)
    MiniGame.Word -> GameSkin(listOf(Color(0xFF2B2219), Color(0xFF221A13), Color(0xFF17120D)), Color(0xFFE9C46A), Color(0x2EF6EAD2), Color(0xFFF8F0E0), Scene.Press)
    MiniGame.Code -> GameSkin(listOf(Color(0xFF1D2030), Color(0xFF161824), Color(0xFF0E0F17)), Color(0xFFB59CFF), Color(0x2EDCE0F0), Color(0xFFEEF0F8), Scene.Vault)
    MiniGame.Blackjack -> GameSkin(listOf(Color(0xFF0E4A2C), Color(0xFF13603A), Color(0xFF082F1C)), Color(0xFFF2D27A), Color(0x33E9F7EE), Color(0xFFF4FBF6), Scene.Felt)
    MiniGame.Lights -> GameSkin(listOf(Color(0xFF241A06), Color(0xFF3A2A0A), Color(0xFF1A1305)), Color(0xFFFFD15C), Color(0x2EFFF0C8), Color(0xFFFFF8E6), Scene.Bulbs)
    MiniGame.Slide -> GameSkin(listOf(Color(0xFF07302F), Color(0xFF0B4442), Color(0xFF052322)), Color(0xFF7FE0D2), Color(0x2ECCFFF8), Color(0xFFEAFFFC), Scene.Tiles)
}

/** Runs [content] with the app colours swapped for the game's, so every button and number inside a round wears its skin. */
@Composable
internal fun WithSkin(game: MiniGame, content: @Composable () -> Unit) {
    val base = T.colors
    val s = game.skin()
    val skinned = remember(base, game) {
        base.copy(
            accent = s.accent, accentSoft = s.accent.copy(alpha = .16f), card = s.surface, cardRaised = s.surface.copy(alpha = (s.surface.alpha * 1.5f).coerceAtMost(1f)),
            label = s.ink, secondary = s.ink.copy(alpha = .72f), tertiary = s.ink.copy(alpha = .5f), separator = s.ink.copy(alpha = .18f),
            sheet = s.sky.last(), sky = s.sky,
        )
    }
    CompositionLocalProvider(LocalTandem provides skinned, content = content)
}

/** The game's painted scene behind [content]. [animate] is off for small previews (a still frame is enough there). */
@Composable
internal fun GameBackdrop(game: MiniGame, modifier: Modifier = Modifier, animate: Boolean = true, content: @Composable BoxScope.() -> Unit = {}) {
    val skin = game.skin()
    val t = if (animate) {
        val tr = rememberInfiniteTransition(label = "scene")
        val v by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "t")
        v
    } else .3f
    val seed = remember(game) { game.ordinal * 7919L + 17 }
    Box(modifier.drawBehind { paintScene(skin, t, seed) }, content = content)
}

// ------------------------------------------------------------------ scenes

private fun DrawScope.paintScene(s: GameSkin, t: Float, seed: Long) {
    drawRect(Brush.verticalGradient(s.sky))
    val w = size.width; val h = size.height
    val tau = 2f * PI.toFloat()
    val rnd = Random(seed)
    val ink = s.ink
    when (s.scene) {
        Scene.Nebula -> {
            softBlob(Offset(w * (.25f + .06f * sin(t * tau)), h * .28f), w * .7f, Color(0xFFFF5C8A), .30f)
            softBlob(Offset(w * (.8f - .05f * sin(t * tau)), h * .62f), w * .8f, Color(0xFF9C4DFF), .22f)
            repeat(14) {
                val x = rnd.nextFloat() * w; val sp = .4f + rnd.nextFloat() * .6f; val sz = (6f + rnd.nextFloat() * 14f) * density
                val y = h - ((t * sp + rnd.nextFloat()) % 1f) * (h + 60f)
                drawPath(heartPath(Offset(x, y), sz), Color(0xFFFFB3C7).copy(alpha = .10f + .08f * rnd.nextFloat()))
            }
        }
        Scene.Constellation -> {
            val stars = List(26) { Offset(rnd.nextFloat() * w, rnd.nextFloat() * h) }
            for (i in stars.indices) for (j in i + 1 until stars.size) {
                val d = (stars[i] - stars[j]).getDistance()
                if (d < w * .2f) drawLine(ink.copy(alpha = .08f), stars[i], stars[j], 1f * density)
            }
            stars.forEachIndexed { i, p -> drawCircle(ink.copy(alpha = .35f + .45f * (.5f + .5f * sin((t + i * .13f) * tau))), (1f + (i % 3)) * density, p) }
        }
        Scene.Chalk -> {
            val step = 28f * density
            var x = 0f; while (x < w) { drawLine(ink.copy(alpha = .05f), Offset(x, 0f), Offset(x, h), 1f); x += step }
            var y = 0f; while (y < h) { drawLine(ink.copy(alpha = .05f), Offset(0f, y), Offset(w, y), 1f); y += step }
            glyphs(listOf("+", "−", "×", "=", "7", "3", "÷", "9"), 12, rnd, ink.copy(alpha = .07f), 34f * density)
        }
        Scene.Dusk -> {
            val sun = Offset(w * .66f, h * .72f)
            softBlob(sun, w * .9f, Color(0xFFFFB45C), .35f)
            drawCircle(Color(0xFFFFD58A).copy(alpha = .9f), w * .2f, sun)
            val ground = Path().apply {
                moveTo(0f, h * .8f)
                cubicTo(w * .3f, h * .76f, w * .6f, h * .84f, w, h * .79f); lineTo(w, h); lineTo(0f, h); close()
            }
            drawPath(ground, Color(0xFF2A0F1F))
        }
        Scene.Meadow -> {
            repeat(30) { drawCircle(ink.copy(alpha = .2f + .5f * (.5f + .5f * sin((t * 2 + it * .21f) * tau))), (0.8f + it % 2) * density, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * .55f)) }
            hill(h * .78f, h * .04f, 1.3f, Color(0xFF1C4A33), Color(0xFF0F2C1E))
            hill(h * .88f, h * .03f, 3.1f, Color(0xFF173E2A), Color(0xFF0A2016))
        }
        Scene.Skyline -> {
            softBlob(Offset(w * .3f, h * .78f), w * .8f, Color(0xFFFFA05C), .3f)
            var x = -10f * density
            while (x < w) {
                val bw = (22f + rnd.nextFloat() * 34f) * density; val bh = h * (.12f + rnd.nextFloat() * .2f)
                drawRect(Color(0xFF1A1030), Offset(x, h - bh), Size(bw, bh))
                repeat(3) { if (rnd.nextFloat() < .5f) drawRect(Color(0xFFFFC98A).copy(alpha = .5f), Offset(x + bw * (.2f + .25f * it), h - bh + bh * (.15f + rnd.nextFloat() * .5f)), Size(3f * density, 4f * density)) }
                x += bw + 2f * density
            }
        }
        Scene.Clouds -> repeat(7) {
            val sp = .3f + rnd.nextFloat() * .7f; val cy = h * (.1f + rnd.nextFloat() * .8f); val r = (26f + rnd.nextFloat() * 30f) * density
            val cx = ((rnd.nextFloat() - t * sp) % 1f + 1f) % 1f * (w + r * 4) - r * 2
            for (k in 0..2) drawCircle(Color.White.copy(alpha = .09f), r * (1f - .2f * k), Offset(cx + r * .9f * k, cy - r * .2f * (k % 2)))
        }
        Scene.Kaleido -> {
            val c = Offset(w / 2, h / 2)
            for (k in 1..7) rotate(t * 360f * (if (k % 2 == 0) 1 else -1) / (k + 2), c) {
                drawPath(polygon(c, w * .1f * k, 6), ink.copy(alpha = .06f), style = Stroke(1.2f * density))
            }
        }
        Scene.Range -> {
            val c = Offset(w / 2, h * .62f)
            for (k in 1..9) drawCircle(ink.copy(alpha = if (k % 3 == 0) .09f else .05f), w * .09f * k, c, style = Stroke(1f * density))
            drawLine(ink.copy(alpha = .06f), Offset(c.x, 0f), Offset(c.x, h), 1f * density)
            drawLine(ink.copy(alpha = .06f), Offset(0f, c.y), Offset(w, c.y), 1f * density)
        }
        Scene.Horizon -> {
            val hz = h * .58f
            softBlob(Offset(w / 2, hz), w * .7f, Color(0xFFB07CFF), .2f)
            for (k in -10..10) drawLine(ink.copy(alpha = .07f), Offset(w / 2 + k * w * .04f, hz), Offset(w / 2 + k * w * .25f, h), 1f * density)
            var k = 0
            while (k < 12) { val f = ((k + t * 2f) % 12f) / 12f; val y = hz + (h - hz) * f * f; drawLine(ink.copy(alpha = .08f * f + .02f), Offset(0f, y), Offset(w, y), 1f * density); k++ }
        }
        Scene.Ripples -> {
            val c = Offset(w / 2, h * .55f)
            for (k in 0..5) { val f = (t * 3f + k / 6f) % 1f; drawCircle(ink.copy(alpha = .12f * (1f - f)), w * .9f * f, c, style = Stroke(1.5f * density)) }
        }
        Scene.Splats -> listOf(Color(0xFFE5484D), Color(0xFF3E8BFF), Color(0xFF3DBE6B), Color(0xFFFFC23D)).forEachIndexed { i, col ->
            softBlob(Offset(w * (.2f + .6f * rnd.nextFloat()), h * (.15f + .2f * i)), w * (.35f + .1f * rnd.nextFloat()), col, .16f)
        }
        Scene.Press -> glyphs(listOf("A", "R", "T", "E", "S", "N", "O", "L"), 14, rnd, ink.copy(alpha = .05f), 64f * density)
        Scene.Vault -> {
            val c = Offset(w / 2, h * .42f); val r = w * .46f
            drawCircle(ink.copy(alpha = .06f), r, c, style = Stroke(2f * density))
            drawCircle(ink.copy(alpha = .04f), r * .72f, c, style = Stroke(10f * density))
            rotate(t * 40f, c) {
                for (k in 0 until 60) {
                    val a = k / 60f * tau; val l = if (k % 5 == 0) 16f else 7f
                    drawLine(ink.copy(alpha = .09f), c + Offset(cos(a), sin(a)) * (r - l * density), c + Offset(cos(a), sin(a)) * r, 1.2f * density)
                }
            }
        }
        Scene.Felt -> {
            drawRect(Brush.radialGradient(listOf(Color(0xFF1F8A52).copy(alpha = .55f), Color.Transparent), center = Offset(w / 2, h * .45f), radius = w * .9f))
            drawArc(Color(0xFFF2D27A).copy(alpha = .18f), 200f, 140f, false, Offset(-w * .2f, h * .3f), Size(w * 1.4f, h * .9f), style = Stroke(1.5f * density))
            glyphs(listOf("♠", "♥", "♦", "♣"), 8, rnd, ink.copy(alpha = .05f), 40f * density)
        }
        Scene.Bulbs -> repeat(24) { drawCircle(Color(0xFFFFD15C).copy(alpha = .05f + .05f * (it % 3)), (10f + it % 4 * 6f) * density, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)) }
        Scene.Tiles -> {
            val step = 44f * density
            var y = 0f; var row = 0
            while (y < h) { var x = if (row % 2 == 0) 0f else step / 2; while (x < w) { drawRoundRect(ink.copy(alpha = .04f), Offset(x + 3, y + 3), Size(step - 6, step - 6), CornerRadius(8f * density)); x += step }; y += step; row++ }
        }
    }
    // a little vignette so the edges settle
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .28f)), center = Offset(w / 2, h * .45f), radius = maxOf(w, h) * .8f))
}

private fun DrawScope.softBlob(c: Offset, r: Float, color: Color, a: Float) =
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), center = c, radius = r), r, c)

private fun DrawScope.hill(base: Float, amp: Float, phase: Float, top: Color, bottom: Color) {
    val p = Path(); p.moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width + 8f) { p.lineTo(x, base + amp * sin(x / size.width * 6.2832f * 1.3f + phase)); x += 8f }
    p.lineTo(size.width, size.height); p.close()
    drawPath(p, Brush.verticalGradient(listOf(top, bottom), startY = base - amp, endY = size.height))
}

private fun DrawScope.glyphs(chars: List<String>, n: Int, rnd: Random, color: Color, textSize: Float) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgb(); this.textSize = textSize; textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.SERIF }
    repeat(n) { drawContext.canvas.nativeCanvas.drawText(chars[it % chars.size], rnd.nextFloat() * size.width, rnd.nextFloat() * size.height, paint) }
}

internal fun heartPath(c: Offset, s: Float): Path = Path().apply {
    moveTo(c.x, c.y + s * .7f)
    cubicTo(c.x - s * 1.25f, c.y - s * .05f, c.x - s * .6f, c.y - s * 1.05f, c.x, c.y - s * .42f)
    cubicTo(c.x + s * .6f, c.y - s * 1.05f, c.x + s * 1.25f, c.y - s * .05f, c.x, c.y + s * .7f)
    close()
}

private fun polygon(c: Offset, r: Float, n: Int): Path = Path().apply {
    for (i in 0..n) { val a = i / n.toFloat() * 6.2832f - 1.5708f; val p = c + Offset(cos(a), sin(a)) * r; if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
    close()
}

// ------------------------------------------------------------------ emblems

/**
 * The game's mark: a small painted tile (its sky, a highlight along the top edge) with a drawn glyph in the game's accent.
 * Used wherever the game shows up (tiles, cards, the round intro) instead of an emoji.
 */
@Composable
internal fun GameEmblem(game: MiniGame, size: Dp, modifier: Modifier = Modifier) {
    val s = game.skin()
    Canvas(modifier.size(size)) {
        val d = this.size.minDimension
        val corner = CornerRadius(d * .28f)
        drawRoundRect(Brush.verticalGradient(listOf(lerp(s.sky[1], Color.White, .12f), s.sky.last())), cornerRadius = corner)
        drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.Transparent), endY = d * .5f), cornerRadius = corner, style = Stroke(d * .025f))
        glyph(game, Offset(d / 2, d / 2), d * .5f, s.accent, s.ink)
    }
}

private fun DrawScope.glyph(game: MiniGame, c: Offset, g: Float, a: Color, ink: Color) {
    val line = Stroke(g * .09f, cap = StrokeCap.Round)
    when (game) {
        MiniGame.Heart -> { drawPath(heartPath(c + Offset(0f, g * .04f), g * .52f), a); drawCircle(Color.White.copy(alpha = .55f), g * .08f, c + Offset(-g * .2f, -g * .16f)) }
        MiniGame.Memory -> {
            rotate(-12f, c) { drawRoundRect(ink.copy(alpha = .35f), c - Offset(g * .42f, g * .36f), Size(g * .5f, g * .7f), CornerRadius(g * .08f)) }
            rotate(10f, c) { drawRoundRect(a, c - Offset(g * .02f, g * .36f), Size(g * .5f, g * .7f), CornerRadius(g * .08f)) }
            drawCircle(Color.White.copy(alpha = .8f), g * .07f, c + Offset(g * .25f, 0f))
        }
        MiniGame.Sums -> {
            drawLine(a, c - Offset(g * .42f, g * .2f), c + Offset(-g * .02f, -g * .2f), line.width, StrokeCap.Round)
            drawLine(a, c - Offset(g * .22f, g * .4f), c - Offset(g * .22f, 0f), line.width, StrokeCap.Round)
            drawLine(ink, c + Offset(g * .08f, g * .08f), c + Offset(g * .42f, g * .42f), line.width, StrokeCap.Round)
            drawLine(ink, c + Offset(g * .42f, g * .08f), c + Offset(g * .08f, g * .42f), line.width, StrokeCap.Round)
        }
        MiniGame.Draw -> drawPath(Path().apply {
            moveTo(c.x + g * .1f, c.y - g * .5f); lineTo(c.x - g * .28f, c.y + g * .06f); lineTo(c.x - g * .02f, c.y + g * .06f)
            lineTo(c.x - g * .12f, c.y + g * .5f); lineTo(c.x + g * .3f, c.y - g * .1f); lineTo(c.x + g * .04f, c.y - g * .1f); close()
        }, a)
        MiniGame.Mole -> {
            drawOval(ink.copy(alpha = .25f), c + Offset(-g * .45f, g * .22f), Size(g * .9f, g * .22f))
            drawArc(a, 180f, 180f, true, c + Offset(-g * .3f, -g * .3f), Size(g * .6f, g * .9f))
            drawCircle(Color(0xFF10301F), g * .05f, c + Offset(-g * .1f, -g * .02f)); drawCircle(Color(0xFF10301F), g * .05f, c + Offset(g * .1f, -g * .02f))
        }
        MiniGame.Stack -> listOf(-.36f to .7f, -.02f to .56f, .32f to .4f).forEachIndexed { i, (y, wdt) ->
            drawRoundRect(if (i == 2) a else ink.copy(alpha = .4f + .2f * i), c + Offset(-g * wdt / 2 + (i - 1) * g * .06f, g * y - g * .1f), Size(g * wdt, g * .26f), CornerRadius(g * .05f))
        }
        MiniGame.Flap -> {
            drawPath(heartPath(c + Offset(g * .06f, g * .06f), g * .4f), a)
            drawArc(ink, 150f, 120f, false, c + Offset(-g * .55f, -g * .3f), Size(g * .5f, g * .4f), style = line)
        }
        MiniGame.Odd -> for (i in 0..2) for (j in 0..2) drawCircle(if (i == 2 && j == 1) a else ink.copy(alpha = .35f), g * .11f, c + Offset((j - 1) * g * .32f, (i - 1) * g * .32f))
        MiniGame.Target -> { drawCircle(ink.copy(alpha = .4f), g * .48f, c, style = line); drawCircle(a, g * .3f, c, style = line); drawCircle(a, g * .1f, c) }
        MiniGame.Sequence -> {
            listOf(.3f, .5f, .7f).forEachIndexed { i, hh ->
                drawRoundRect(ink.copy(alpha = .45f), c + Offset(-g * .45f + i * g * .26f, g * .4f - g * hh), Size(g * .18f, g * hh), CornerRadius(g * .04f))
            }
            drawRoundRect(a, c + Offset(g * .33f, g * .4f - g * .9f), Size(g * .18f, g * .9f), CornerRadius(g * .04f), style = Stroke(g * .06f))
        }
        MiniGame.Echo -> for (q in 0..3) drawArc(if (q == 1) a else ink.copy(alpha = .35f), q * 90f + 6f, 78f, true, c - Offset(g * .46f, g * .46f), Size(g * .92f, g * .92f))
        MiniGame.Stroop -> { drawCircle(Color(0xFF3E8BFF), g * .3f, c + Offset(-g * .14f, -g * .08f)); drawCircle(Color(0xFFE5484D).copy(alpha = .9f), g * .3f, c + Offset(g * .16f, g * .1f)) }
        MiniGame.Word -> listOf(-.32f, 0f, .32f).forEachIndexed { i, x ->
            drawRoundRect(if (i == 1) a else ink.copy(alpha = .35f), c + Offset(g * x - g * .14f, -g * .16f), Size(g * .28f, g * .32f), CornerRadius(g * .05f))
        }
        MiniGame.Code -> listOf(Color(0xFFE5484D), Color(0xFFFFC23D), a, Color(0xFF3DBE6B)).forEachIndexed { i, col -> drawCircle(col, g * .12f, c + Offset((i - 1.5f) * g * .3f, 0f)) }
        MiniGame.Blackjack -> {
            rotate(-8f, c) { drawRoundRect(ink, c - Offset(g * .32f, g * .45f), Size(g * .64f, g * .9f), CornerRadius(g * .1f)) }
            drawPath(heartPath(c + Offset(0f, g * .02f), g * .2f), Color(0xFFD7263D))
        }
        MiniGame.Lights -> for (i in 0..2) for (j in 0..2) drawRoundRect(if ((i + j) % 2 == 0) a else ink.copy(alpha = .25f), c + Offset((j - 1.5f) * g * .3f + g * .02f, (i - 1.5f) * g * .3f + g * .02f), Size(g * .26f, g * .26f), CornerRadius(g * .06f))
        MiniGame.Slide -> for (k in 0..7) { val i = k / 3; val j = k % 3; drawRoundRect(if (k == 0) a else ink.copy(alpha = .35f), c + Offset((j - 1.5f) * g * .3f + g * .02f, (i - 1.5f) * g * .3f + g * .02f), Size(g * .26f, g * .26f), CornerRadius(g * .06f)) }
    }
}
