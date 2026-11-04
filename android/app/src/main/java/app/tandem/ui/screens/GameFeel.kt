package app.tandem.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import app.tandem.R
import app.tandem.data.MiniGame
import app.tandem.ui.components.FxState
import app.tandem.ui.theme.LocalTandem
import app.tandem.ui.theme.T
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Each game has one light: the colour its round glows in. Everything else is the app's own dusk, so a round
 * feels like part of Tandem rather than a different app, and two games still never look alike.
 */
internal val MiniGame.light: Color get() = when (this) {
    MiniGame.Heart -> Color(0xFFFF8FA3)
    MiniGame.Memory -> Color(0xFFB9A7FF)
    MiniGame.Sums -> Color(0xFF7FD8C8)
    MiniGame.Draw -> Color(0xFFFFC56E)
    MiniGame.Mole -> Color(0xFF9BE3A8)
    MiniGame.Stack -> Color(0xFFFFAE7A)
    MiniGame.Flap -> Color(0xFFFF9EC4)
    MiniGame.Odd -> Color(0xFFE79BFF)
    MiniGame.Target -> Color(0xFF8DB6FF)
    MiniGame.Sequence -> Color(0xFFA9B4FF)
    MiniGame.Echo -> Color(0xFF8FE0E8)
    MiniGame.Stroop -> Color(0xFFFFB86B)
    MiniGame.Word -> Color(0xFFF0CF86)
    MiniGame.Code -> Color(0xFFC2A3FF)
    MiniGame.Blackjack -> Color(0xFF8FD9B0)
    MiniGame.Lights -> Color(0xFFFFD66E)
    MiniGame.Slide -> Color(0xFF8FDCCF)
    MiniGame.Path -> Color(0xFFE9D7FF)
    MiniGame.Orbit -> Color(0xFF9FB4FF)
    MiniGame.Drift -> Color(0xFFFFC56E)
}

/** Runs [content] with the app's accent swapped for the game's light. Surfaces and text stay the app's own. */
@Composable
internal fun WithSkin(game: MiniGame, content: @Composable () -> Unit) {
    val base = T.colors
    val skinned = remember(base, game) { base.copy(accent = game.light, accentSoft = game.light.copy(alpha = .16f)) }
    CompositionLocalProvider(LocalTandem provides skinned, content = content)
}

private class Mote(val x: Float, val y: Float, val z: Float, val phase: Float)

/**
 * Behind a round: the app's dusk sky, two slow glows (the game's light and a cool periwinkle) and fine motes
 * drifting up. The glows warm up as [fx] reports a streak, so a good run is something you can see.
 */
@Composable
internal fun RoundBackdrop(game: MiniGame, fx: FxState?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = T.colors
    val light = game.light
    val grain: ImageBitmap = ImageBitmap.imageResource(R.drawable.grain)
    val motes = remember { val r = Random(7); List(56) { Mote(r.nextFloat(), r.nextFloat(), .3f + r.nextFloat() * .7f, r.nextFloat() * 6.28f) } }
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) now = withFrameMillis { it } }
    Box(
        modifier.drawBehind {
            val t = now.toFloat()
            val warm = fx?.warmth ?: 0f
            drawRect(Brush.verticalGradient(c.sky))
            val w = size.width; val h = size.height
            glow(Offset(w * (.3f + .12f * sin(t / 5200f)), h * (.3f + .06f * cos(t / 4100f))), w * 1.05f, light, .16f + .22f * warm)
            glow(Offset(w * (.75f + .1f * cos(t / 6100f)), h * (.72f + .05f * sin(t / 4700f))), w, c.me, .12f + .10f * warm)
            for (m in motes) {
                val y = (((m.y - t / 90000f * m.z) % 1f) + 1f) % 1f * h
                val x = m.x * w + sin(t / 3000f + m.phase) * 8f * density * m.z
                val a = (.22f + .5f * m.z) * (.6f + .4f * sin(t / 900f + m.phase))
                drawCircle(Color.White.copy(alpha = a), (.6f + m.z * 1.1f) * density, Offset(x, y))
            }
            drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = .10f)
        },
        content = content,
    )
}

/** A soft pool of [color] light, fading to nothing at [r]. */
internal fun DrawScope.glow(center: Offset, r: Float, color: Color, alpha: Float) {
    if (alpha <= 0f || r <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha.coerceAtMost(1f)), Color.Transparent), center = center, radius = r), r, center)
}

/** The faint glow of a game's light in a tile's corner, so a list of games reads by colour at a glance. */
internal fun Modifier.gameGlow(game: MiniGame, strength: Float = 1f): Modifier = drawBehind {
    glow(Offset(size.width * .18f, size.height * .1f), size.maxDimension * .9f, game.light, .20f * strength)
}

/**
 * A luminous orb: a lit sphere in [color] with a halo. [lit] 0..1 takes it from a quiet glass marble to fully
 * glowing (Echo's moons, Quick Draw's light, Code Breaker's pegs, Odd One Out's dots).
 */
internal fun DrawScope.orb(center: Offset, r: Float, color: Color, lit: Float = 1f, alpha: Float = 1f) {
    glow(center, r * (1.5f + 1.6f * lit), color, (.10f + .45f * lit) * alpha)
    val core = Brush.radialGradient(
        listOf(lerp(color, Color.White, .35f + .6f * lit).copy(alpha = (.55f + .45f * lit) * alpha), color.copy(alpha = (.5f + .45f * lit) * alpha), lerp(color, Color(0xFF120D3A), .55f).copy(alpha = (.6f + .35f * lit) * alpha)),
        center = center + Offset(-r * .35f, -r * .4f), radius = r * 1.35f,
    )
    drawCircle(core, r, center)
    drawCircle(Color.White.copy(alpha = (.08f + .3f * lit) * alpha), r * 1.16f, center, style = Stroke(1f * density))
}

/** A soft, lit heart: the one heart Heart Catch and Flappy Heart need. */
internal fun DrawScope.softHeart(center: Offset, s: Float, color: Color = Color(0xFFFF8FA3), alpha: Float = 1f, dim: Float = 0f) {
    glow(center, s * 1.9f, color, .42f * alpha * (1f - dim))
    val base = lerp(color, Color(0xFF6E6290), dim)
    drawPath(
        heartPath(center, s * .62f),
        Brush.radialGradient(
            listOf(lerp(base, Color.White, .8f).copy(alpha = alpha), lerp(base, Color.White, .25f).copy(alpha = alpha), lerp(base, Color(0xFFB0305A), .45f).copy(alpha = alpha)),
            center = center + Offset(-s * .25f, -s * .35f), radius = s * 1.15f,
        ),
    )
}

internal fun heartPath(c: Offset, s: Float): Path = Path().apply {
    moveTo(c.x, c.y + s * .7f)
    cubicTo(c.x - s * 1.25f, c.y - s * .05f, c.x - s * .6f, c.y - s * 1.05f, c.x, c.y - s * .42f)
    cubicTo(c.x + s * .6f, c.y - s * 1.05f, c.x + s * 1.25f, c.y - s * .05f, c.x, c.y + s * .7f)
    close()
}

/** A four-point sparkle, the app's star. */
internal fun sparklePath(c: Offset, r: Float): Path = Path().apply {
    moveTo(c.x, c.y - r); quadraticTo(c.x, c.y, c.x + r, c.y); quadraticTo(c.x, c.y, c.x, c.y + r)
    quadraticTo(c.x, c.y, c.x - r, c.y); quadraticTo(c.x, c.y, c.x, c.y - r); close()
}

/**
 * The game's mark: a small sphere of glass lit from inside by the game's light, with a fine glyph in it.
 * Used wherever a game shows up (tiles, cards, the round intro).
 */
@Composable
internal fun GameMark(game: MiniGame, size: Dp, modifier: Modifier = Modifier) {
    val ink = T.colors.label
    Canvas(modifier.size(size)) {
        val d = this.size.minDimension
        val ctr = Offset(d / 2, d / 2)
        drawCircle(Brush.radialGradient(listOf(game.light.copy(alpha = .34f), game.light.copy(alpha = .06f)), center = ctr + Offset(-d * .12f, -d * .16f), radius = d * .62f), d / 2, ctr)
        drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = .30f), Color.White.copy(alpha = .06f))), d / 2 - .5f * density, ctr, style = Stroke(1f * density))
        glyph(game, ctr, d * .5f, game.light, ink)
    }
}

private fun DrawScope.glyph(game: MiniGame, c: Offset, g: Float, a: Color, ink: Color) {
    val line = Stroke(g * .085f, cap = StrokeCap.Round)
    val soft = ink.copy(alpha = .55f)
    when (game) {
        MiniGame.Heart -> drawPath(heartPath(c + Offset(0f, g * .04f), g * .46f), a)
        MiniGame.Memory -> {
            rotate(-12f, c) { drawRoundRect(soft, c - Offset(g * .42f, g * .34f), Size(g * .46f, g * .64f), CornerRadius(g * .08f), style = line) }
            rotate(10f, c) { drawRoundRect(a, c - Offset(g * .02f, g * .34f), Size(g * .46f, g * .64f), CornerRadius(g * .08f)) }
        }
        MiniGame.Sums -> {
            drawLine(a, c - Offset(g * .42f, g * .2f), c + Offset(-g * .02f, -g * .2f), line.width, StrokeCap.Round)
            drawLine(a, c - Offset(g * .22f, g * .4f), c - Offset(g * .22f, 0f), line.width, StrokeCap.Round)
            drawLine(soft, c + Offset(g * .08f, g * .1f), c + Offset(g * .4f, g * .1f), line.width, StrokeCap.Round)
            drawLine(soft, c + Offset(g * .08f, g * .3f), c + Offset(g * .4f, g * .3f), line.width, StrokeCap.Round)
        }
        MiniGame.Draw -> { glow(c, g * .5f, a, .5f); drawCircle(a, g * .18f, c); drawCircle(soft, g * .4f, c, style = Stroke(g * .05f)) }
        MiniGame.Mole -> {
            drawLine(soft, c + Offset(-g * .42f, g * .3f), c + Offset(g * .42f, g * .3f), line.width, StrokeCap.Round)
            drawArc(a, 180f, 180f, true, c + Offset(-g * .26f, -g * .24f), Size(g * .52f, g * .9f))
        }
        MiniGame.Stack -> listOf(.3f to .74f, .02f to .58f, -.26f to .42f).forEachIndexed { i, (y, wdt) ->
            drawRoundRect(if (i == 2) a else soft, c + Offset(-g * wdt / 2 + (i - 1) * g * .05f, g * y - g * .1f), Size(g * wdt, g * .2f), CornerRadius(g * .05f))
        }
        MiniGame.Flap -> {
            drawPath(heartPath(c + Offset(g * .08f, g * .06f), g * .36f), a)
            drawArc(soft, 150f, 120f, false, c + Offset(-g * .52f, -g * .28f), Size(g * .46f, g * .38f), style = line)
        }
        MiniGame.Odd -> for (i in 0..2) for (j in 0..2) drawCircle(if (i == 2 && j == 1) a else soft.copy(alpha = .4f), g * .1f, c + Offset((j - 1) * g * .3f, (i - 1) * g * .3f))
        MiniGame.Target -> { drawCircle(soft, g * .44f, c, style = line); drawCircle(a, g * .26f, c, style = line); drawCircle(a, g * .08f, c) }
        MiniGame.Sequence -> {
            listOf(.26f, .44f, .62f).forEachIndexed { i, hh -> drawRoundRect(soft, c + Offset(-g * .44f + i * g * .25f, g * .36f - g * hh), Size(g * .15f, g * hh), CornerRadius(g * .04f)) }
            drawRoundRect(a, c + Offset(g * .31f, g * .36f - g * .8f), Size(g * .15f, g * .8f), CornerRadius(g * .04f))
        }
        MiniGame.Echo -> for (q in 0..3) drawCircle(if (q == 1) a else soft.copy(alpha = .4f), g * .15f, c + Offset(if (q % 2 == 0) -g * .22f else g * .22f, if (q < 2) -g * .22f else g * .22f))
        MiniGame.Stroop -> { drawCircle(Color(0xFF8DB6FF).copy(alpha = .9f), g * .26f, c + Offset(-g * .12f, -g * .08f)); drawCircle(a.copy(alpha = .9f), g * .26f, c + Offset(g * .14f, g * .1f)) }
        MiniGame.Word -> listOf(-.3f, 0f, .3f).forEachIndexed { i, x ->
            drawRoundRect(if (i == 1) a else soft.copy(alpha = .4f), c + Offset(g * x - g * .12f, -g * .14f), Size(g * .24f, g * .28f), CornerRadius(g * .05f))
        }
        MiniGame.Code -> for (i in 0..3) drawCircle(if (i == 2) a else soft.copy(alpha = .4f), g * .1f, c + Offset((i - 1.5f) * g * .28f, 0f))
        MiniGame.Blackjack -> {
            rotate(-10f, c) { drawRoundRect(soft, c - Offset(g * .34f, g * .42f), Size(g * .5f, g * .74f), CornerRadius(g * .08f), style = line) }
            rotate(8f, c) { drawRoundRect(a, c - Offset(g * .12f, g * .36f), Size(g * .5f, g * .74f), CornerRadius(g * .08f)) }
        }
        MiniGame.Lights -> for (i in 0..1) for (j in 0..1) drawRoundRect(if (i == j) a else soft.copy(alpha = .4f), c + Offset((j - 1f) * g * .36f + g * .02f, (i - 1f) * g * .36f + g * .02f), Size(g * .32f, g * .32f), CornerRadius(g * .07f))
        MiniGame.Path -> {
            val pts = listOf(Offset(-.38f, .22f), Offset(-.1f, -.3f), Offset(.18f, .05f), Offset(.4f, -.28f))
            for (k in 1 until pts.size) drawLine(soft, c + pts[k - 1] * g, c + pts[k] * g, g * .05f, StrokeCap.Round)
            pts.forEachIndexed { k, p -> drawCircle(if (k == 3) a else ink, g * .08f, c + p * g) }
        }
        MiniGame.Orbit -> { drawCircle(soft, g * .42f, c, style = Stroke(g * .05f)); drawArc(a, -60f, 50f, false, c - Offset(g * .42f, g * .42f), Size(g * .84f, g * .84f), style = Stroke(g * .12f, cap = StrokeCap.Round)); drawCircle(ink, g * .1f, c + Offset(-g * .3f, g * .3f)) }
        MiniGame.Drift -> {
            drawPath(sparklePath(c + Offset(-g * .2f, -g * .2f), g * .18f), a)
            drawCircle(soft, g * .1f, c + Offset(g * .25f, -g * .05f))
            drawCircle(a, g * .13f, c + Offset(0f, g * .32f))
        }
        MiniGame.Slide -> for (k in 0..2) drawRoundRect(if (k == 0) a else soft.copy(alpha = .4f), c + Offset((k - 1.5f) * g * .32f + g * .02f, -g * .14f), Size(g * .28f, g * .28f), CornerRadius(g * .06f))
    }
}

/** Darkens a light into something text can sit on: for the label on a lit button. */
internal fun Color.deep(): Color = lerp(this, Color(0xFF1A1240), .78f)

internal val TAU = 2f * PI.toFloat()
