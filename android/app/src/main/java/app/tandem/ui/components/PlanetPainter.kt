package app.tandem.ui.components

import android.graphics.BlurMaskFilter
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
    private val FOREST = intArrayOf(0xFF6FC79A.toInt(), 0xFF2F7A68.toInt())
    private val CRYSTAL = intArrayOf(0xFFC9AEF5.toInt(), 0xFF7C5CC4.toInt())
    private val AURORA = intArrayOf(0xFF8FE6DA.toInt(), 0xFF3A9C9E.toInt())

    private class Blob(val u: Float, val v: Float, val r: Float, val biome: Int, val shade: Float)

    private val blobCache = HashMap<String, List<Blob>>()

    /**
     * Continents: each land biome gets a few landmasses (more for a bigger share), each one a cluster of soft lobes,
     * laid out the same way every time for the same planet. Ocean is the ground they sit in.
     */
    private fun blobs(look: PlanetLook): List<Blob> {
        val shares = floatArrayOf(look.ocean, look.forest, look.crystal, look.aurora)
        val key = "${look.seed}:" + shares.joinToString(",") { (it / 5).toInt().toString() }
        return blobCache.getOrPut(key) {
            val rnd = Random(look.seed)
            fun gauss() = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f) / 1.5f
            buildList {
                for (b in 1..3) {
                    val lands = when { shares[b] < 6f -> 0; else -> (shares[b] / 9f).toInt().coerceIn(1, 6) }
                    repeat(lands) {
                        val u0 = rnd.nextFloat(); val v0 = .22f + rnd.nextFloat() * .56f
                        val lobes = 5 + (shares[b] / 8f).toInt().coerceAtMost(7)
                        repeat(lobes) { add(Blob(u0 + gauss() * .09f, (v0 + gauss() * .13f).coerceIn(.1f, .9f), .06f + rnd.nextFloat() * .09f, b, rnd.nextFloat())) }
                    }
                }
            }
        }
    }

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

        // ---- the planet itself
        cv.save()
        val clip = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        cv.clipPath(clip)
        val ground = Paint(Paint.ANTI_ALIAS_FLAG)
        ground.shader = LinearGradient(cx, cy - r, cx, cy + r, OCEAN[0], OCEAN[1], Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, ground)
        // continents drift past as it turns
        val spin = (t * .012f) % 1f
        val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(r * .03f, BlurMaskFilter.Blur.NORMAL) }
        for (b in blobs(look)) {
            val u = (((b.u + spin) % 1f) + 1f) % 1f
            // on a sphere, land near the edge is squeezed and fades into the limb
            val lon = (u - .5f) * Math.PI.toFloat()
            val face = cos(lon)
            if (face <= 0f) continue
            val x = cx + sin(lon) * r
            val y = cy + (b.v - .5f) * 2f * r * .92f
            val col = when (b.biome) { 1 -> FOREST; 2 -> CRYSTAL; else -> AURORA }
            bp.color = alpha(blend(col[0], col[1], .15f + .45f * b.shade), .55f + .4f * face)
            cv.save(); cv.scale(face.coerceAtLeast(.2f), 1f, x, y); cv.drawCircle(x, y, b.r * r, bp); cv.restore()
        }
        // aurora ribbons near the poles, if there is aurora at all
        if (look.aurora > 15f) {
            val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * .05f; maskFilter = BlurMaskFilter(r * .05f, BlurMaskFilter.Blur.NORMAL) }
            ap.color = alpha(AURORA[0], (look.aurora / 100f) * .7f)
            val path = Path(); path.moveTo(cx - r, cy - r * .62f)
            var x = cx - r
            while (x <= cx + r) { path.lineTo(x, cy - r * .62f + sin((x / r) * 4f + t * .6f) * r * .05f); x += r * .05f }
            cv.drawPath(path, ap)
        }
        // ice caps grow from both poles
        val icy = (look.ice / 100f).coerceIn(0f, 1f)
        if (icy > .02f) {
            val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(r * .06f, BlurMaskFilter.Blur.NORMAL); color = alpha(0xFFF2F6FF.toInt(), .92f) }
            val cap = r * (.08f + .9f * icy)
            cv.drawOval(RectF(cx - r * 1.2f, cy - r - cap, cx + r * 1.2f, cy - r + cap), ip)
            cv.drawOval(RectF(cx - r * 1.2f, cy + r - cap * .8f, cx + r * 1.2f, cy + r + cap * .8f), ip)
        }
        // clouds
        val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(r * .05f, BlurMaskFilter.Blur.NORMAL); color = alpha(Color.WHITE, .22f * (.4f + atmo)) }
        val cr = Random(look.seed + 3)
        repeat(6) {
            val u = ((cr.nextFloat() + spin * 1.6f) % 1f); val lon = (u - .5f) * Math.PI.toFloat()
            if (cos(lon) > 0f) { val x = cx + sin(lon) * r; val y = cy + (cr.nextFloat() - .5f) * r * 1.4f; cv.drawOval(RectF(x - r * .22f * cos(lon), y - r * .035f, x + r * .22f * cos(lon), y + r * .035f), cp) }
            else cr.nextFloat()
        }
        // life: tiny lights that twinkle, more with every species alive
        val lp = Paint(Paint.ANTI_ALIAS_FLAG)
        val lr = Random(look.seed + 11)
        repeat((look.species * 4 + (look.life / 10f).toInt()).coerceAtMost(60)) { i ->
            val u = ((lr.nextFloat() + spin) % 1f); val lon = (u - .5f) * Math.PI.toFloat(); val v = lr.nextFloat()
            if (cos(lon) > .15f) {
                val x = cx + sin(lon) * r; val y = cy + (v - .5f) * r * 1.5f
                lp.color = alpha(Color.WHITE, (.35f + .45f * sin(t * 2f + i)).coerceIn(.1f, .85f))
                cv.drawCircle(x, y, r * .012f + .6f, lp)
            }
        }
        // the night side, lit by whichever sun is brighter
        val lightFromLeft = look.sunMe >= look.sunThem
        val shade = Paint(Paint.ANTI_ALIAS_FLAG)
        val lx = if (lightFromLeft) cx - r * .55f else cx + r * .55f
        shade.shader = RadialGradient(lx, cy - r * .45f, r * 2.1f, intArrayOf(alpha(0xFF0E0A2C.toInt(), 0f), alpha(0xFF0E0A2C.toInt(), .25f), alpha(0xFF0E0A2C.toInt(), .82f)), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, r, shade)
        // buildings glow on the dark side; ruins are dark specks
        val br = Random(look.seed + 29)
        repeat((look.buildings + look.ruins).coerceAtMost(10)) { i ->
            val x = cx + (if (lightFromLeft) 1 else -1) * r * (.25f + br.nextFloat() * .55f); val y = cy + (br.nextFloat() - .5f) * r * 1.2f
            if (i < look.buildings) { glow(cv, x, y, r * .09f, 0xFFFFD58A.toInt(), .7f); cv.drawCircle(x, y, r * .018f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF4D8.toInt() }) }
            else cv.drawCircle(x, y, r * .02f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A3358.toInt() })
        }
        // a neglected world loses its colour
        if (stageDim > 0f) cv.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(0xFF5E5A78.toInt(), stageDim) })
        cv.restore()
        // rim light
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = r * .02f; shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, alpha(Color.WHITE, if (lightFromLeft) .5f else .05f), alpha(Color.WHITE, if (lightFromLeft) .05f else .5f), Shader.TileMode.CLAMP) }
        cv.drawCircle(cx, cy, r, rim)

        moonAngles.filter { sin(it) >= 0 }.forEach(::moon)
    }
}
