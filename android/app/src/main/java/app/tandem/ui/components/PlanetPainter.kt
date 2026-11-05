package app.tandem.ui.components

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** What the painter needs to know about the planet. Everything 0..100 unless said otherwise. */
data class PlanetLook(
    val ocean: Float = 25f, val forest: Float = 25f, val crystal: Float = 25f, val aurora: Float = 25f,
    val ice: Float = 20f, val atmosphere: Float = 60f, val life: Float = 30f,
    val sunMe: Float = 60f, val sunThem: Float = 60f,
    val moons: Int = 0, val lostMoons: Int = 0,
    val buildings: Int = 0, val ruins: Int = 0, val species: Int = 0,
    /** thriving, dimming, winter, drift or sleeping */
    val stage: String = "thriving",
    val seed: Int = 7,
)

/**
 * Paints the couple's planet with android.graphics, so the app screen (through Compose's native canvas) and the
 * home-screen widget (into a bitmap) draw the very same world. [t] is time in seconds, for the slow turn.
 * Suns sit left (you) and right (your partner); the planet in the middle, its moons on a tilted orbit.
 */
object PlanetPainter {
    private const val ME = 0xFF9C8CFF.toInt()
    private const val THEM = 0xFFFF8FA3.toInt()
    private val OCEAN = intArrayOf(0xFF4F78D8.toInt(), 0xFF1E2A78.toInt())

    private fun alpha(c: Int, a: Float) = (c and 0xFFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

    private fun glow(cv: Canvas, x: Float, y: Float, r: Float, color: Int, a: Float) {
        if (r <= 0f || a <= 0f) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(x, y, r, alpha(color, a), alpha(color, 0f), Shader.TileMode.CLAMP)
        cv.drawCircle(x, y, r, p)
    }

    private fun sun(cv: Canvas, x: Float, y: Float, base: Float, color: Int, power: Float, t: Float) {
        val k = (power / 100f).coerceIn(0f, 1f)
        val breathe = 1f + .04f * sin(t * 1.3f + x)
        glow(cv, x, y, base * (2.2f + 3.2f * k) * breathe, color, .14f + .4f * k)
        val core = base * (.55f + .45f * k)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val lit = blend(color, Color.WHITE, .55f + .35f * k)
        p.shader = RadialGradient(x - core * .3f, y - core * .3f, core * 1.3f, intArrayOf(Color.WHITE, lit, alpha(blend(color, 0xFF2A2070.toInt(), .5f - .4f * k), 1f)), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        p.alpha = (110 + 145 * k).toInt()
        cv.drawCircle(x, y, core, p)
    }

    private fun blend(a: Int, b: Int, f: Float): Int {
        val g = f.coerceIn(0f, 1f)
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - g) + ((b shr s) and 0xFF) * g).toInt()
        return Color.argb(255, ch(16), ch(8), ch(0))
    }

    fun draw(cv: Canvas, w: Float, h: Float, look: PlanetLook, t: Float, showSuns: Boolean = true) {
        // gradients and blurs throw on a zero radius, so nothing is drawn until there is room for a planet
        if (min(w, h) < 24f) return
        val cx = w / 2f
        val cy = h * .54f
        val r = min(w, h) * .27f
        val stageDim = when (look.stage) { "dimming" -> .3f; "winter" -> .45f; "drift" -> .6f; "sleeping" -> .5f; else -> 0f }

        // ---- the two suns, and the faint orbit they pull the planet along
        if (showSuns) {
            sun(cv, w * .12f, h * .3f, r * .22f, ME, look.sunMe, t)
            sun(cv, w * .88f, h * .3f, r * .22f, THEM, look.sunThem, t)
        }
        val tilt = ((look.sunMe - look.sunThem) / 100f).coerceIn(-1f, 1f)
        val orbit = RectF(cx - r * 1.75f, cy - r * .5f, cx + r * 1.75f, cy + r * .5f)
        val op = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * .008f + .5f; color = alpha(Color.WHITE, .10f) }
        cv.save(); cv.rotate(-10f + tilt * 8f, cx, cy); cv.drawOval(orbit, op)
        // lost moons: an empty ring on a wider orbit, so what's gone is still remembered
        if (look.lostMoons > 0) {
            val far = RectF(cx - r * 2.15f, cy - r * .66f, cx + r * 2.15f, cy + r * .66f)
            val dp = Paint(op).apply { pathEffect = DashPathEffect(floatArrayOf(r * .04f, r * .06f), 0f); color = alpha(Color.WHITE, .07f) }
            cv.drawOval(far, dp)
            repeat(look.lostMoons.coerceAtMost(5)) { i ->
                val a = 2.4f + i * 1.1f
                val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * .01f + .5f; color = alpha(Color.WHITE, .18f) }
                cv.drawCircle(cx + cos(a) * r * 2.15f, cy + sin(a) * r * .66f, r * .07f, mp)
            }
        }
        cv.restore()

        // ---- atmosphere: a soft halo, thick when the air is full
        val atmo = (look.atmosphere / 100f).coerceIn(0f, 1f)
        glow(cv, cx, cy, r * (1.25f + .45f * atmo), blend(0xFF7FA8FF.toInt(), ME, .4f), (.12f + .4f * atmo) * (1f - stageDim * .6f))

        // moons behind the planet first, then the planet, then the ones in front
        val moonAngles = List(look.moons.coerceAtMost(6)) { i -> t * (.18f + i * .05f) + i * 2.1f }
        fun moon(a: Float) {
            // a point on the tilted orbit
            val rad = Math.toRadians((-10f + tilt * 8f).toDouble())
            val dx = cos(a) * r * 1.75f; val dy = sin(a) * r * .5f
            val rx = cx + dx * cos(rad).toFloat() - dy * sin(rad).toFloat(); val ry = cy + dx * sin(rad).toFloat() + dy * cos(rad).toFloat()
            glow(cv, rx, ry, r * .2f, 0xFFDCD6FF.toInt(), .3f)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = RadialGradient(rx - r * .03f, ry - r * .03f, r * .1f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFCFC8F0.toInt(), 0xFF6E66A6.toInt()), null, Shader.TileMode.CLAMP)
            cv.drawCircle(rx, ry, r * .075f, p)
        }
        moonAngles.filter { sin(it) < 0 }.forEach(::moon)

        // ---- the planet itself: a real surface (terrain, seas, ice, clouds) turning slowly, lit by the brighter sun
        val lightFromLeft = look.sunMe >= look.sunThem
        val map = PlanetSurface.ready(look)
        cv.save()
        cv.clipPath(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) })
        if (map != null) {
            val size = (r * 2).toInt().coerceIn(32, 720)
            val bmp = PlanetSurface.render(map, size, t * .075f, lightFromLeft, stageDim)
            cv.drawBitmap(bmp, null, RectF(cx - r, cy - r, cx + r, cy + r), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        } else {
            // the surface is still being made: a plain lit sea for a moment
            val ground = Paint(Paint.ANTI_ALIAS_FLAG)
            ground.shader = RadialGradient(cx + (if (lightFromLeft) -r else r) * .4f, cy - r * .35f, r * 1.6f, OCEAN[0], OCEAN[1], Shader.TileMode.CLAMP)
            cv.drawCircle(cx, cy, r, ground)
        }
        // life: a few lights twinkling on the night side, more with every species alive
        val lp = Paint(Paint.ANTI_ALIAS_FLAG)
        val lr = Random(look.seed + 11)
        repeat((look.species * 2 + (look.life / 20f).toInt()).coerceAtMost(28)) { i ->
            val x = cx + (if (lightFromLeft) 1 else -1) * r * (.2f + lr.nextFloat() * .65f); val y = cy + (lr.nextFloat() - .5f) * r * 1.3f
            lp.color = alpha(0xFFFFF1C9.toInt(), (.25f + .4f * sin(t * 1.6f + i)).coerceIn(.05f, .65f))
            cv.drawCircle(x, y, r * .008f + .5f, lp)
        }
        // buildings glow on the dark side; ruins are dark specks
        val br = Random(look.seed + 29)
        repeat((look.buildings + look.ruins).coerceAtMost(10)) { i ->
            val x = cx + (if (lightFromLeft) 1 else -1) * r * (.25f + br.nextFloat() * .55f); val y = cy + (br.nextFloat() - .5f) * r * 1.2f
            if (i < look.buildings) { glow(cv, x, y, r * .09f, 0xFFFFD58A.toInt(), .7f); cv.drawCircle(x, y, r * .018f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF4D8.toInt() }) }
            else cv.drawCircle(x, y, r * .02f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3358.toInt() })
        }
        if (map == null && stageDim > 0f) cv.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(0xFF5E5A78.toInt(), stageDim) })
        cv.restore()
        // rim light
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * .02f; shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, alpha(Color.WHITE, if (lightFromLeft) .5f else .05f), alpha(Color.WHITE, if (lightFromLeft) .05f else .5f), Shader.TileMode.CLAMP) }
        cv.drawCircle(cx, cy, r, rim)

        moonAngles.filter { sin(it) >= 0 }.forEach(::moon)
    }
}
