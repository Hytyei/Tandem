package app.tandem.ui.galaxy

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/*
 * A galaxy from a seed. The seed picks the shape (spiral, barred spiral, ring or lens), the number and
 * tightness of the arms, the tilt, and a palette after real telescope photos (teal arms around a gold core,
 * silver monochrome, magenta and violet nebulae, rose and amber, ember red). Lifetime stardust decides how
 * much of it has formed: more stars, more nebulae, a brighter core.
 *
 * Output is three layers of particles, each 8 floats (x, y, z, r, g, b, a, size), in a disk of radius 1
 * lying in the x-y plane:
 *   stars   drawn additively, small and sharp
 *   glow    drawn additively, big and soft (nebulae, the core's light)
 *   dust    drawn over the glow with normal blending, big, soft and dark (the dust lanes)
 */

class GalaxyLayers(val stars: FloatArray, val glow: FloatArray, val dust: FloatArray, val field: FloatArray, val look: GalaxyLook) {
    val starCount get() = stars.size / 8
}

data class GalaxyLook(
    val kind: String,          // "spiral", "barred", "ring", "lens"
    val arms: Int,
    val pitch: Float,          // radians: how open the arms are
    val tilt: Float,           // degrees: how it first faces you
    val palette: Palette,
    val growth: Float,         // 0..1
)

class Palette(val name: String, val core: Int, val inner: Int, val outer: Int, val accent: Int, val knot: Int, val dust: Int, val field: Int)

private val PALETTES = listOf(
    // a teal spiral around a golden core
    Palette("Tide", 0xFFFFD79A.toInt(), 0xFF9FE3E8.toInt(), 0xFF3C86E0.toInt(), 0xFF5FD8C8.toInt(), 0xFFFF86B4.toInt(), 0xFF2A1A12.toInt(), 0xFFDDE8FF.toInt()),
    // silver monochrome
    Palette("Silver", 0xFFFFF3E2.toInt(), 0xFFE3E8F4.toInt(), 0xFFAFC0DE.toInt(), 0xFFCBD6EE.toInt(), 0xFFE9EEFF.toInt(), 0xFF141414.toInt(), 0xFFE6E9F2.toInt()),
    // magenta, violet and blue nebulae with red knots
    Palette("Orchid", 0xFFFFD0B0.toInt(), 0xFFFF6FB5.toInt(), 0xFF5872FF.toInt(), 0xFFA05BFF.toInt(), 0xFFFF4B5E.toInt(), 0xFF2B0E14.toInt(), 0xFFE8E0FF.toInt()),
    // rose and amber, teal at the edges
    Palette("Ember rose", 0xFFFFC985.toInt(), 0xFFFF9A8A.toInt(), 0xFF56C3D8.toInt(), 0xFFFFB46B.toInt(), 0xFFFF6E9C.toInt(), 0xFF2A140C.toInt(), 0xFFFFEEDD.toInt()),
    // violet and cyan
    Palette("Aurora", 0xFFF2E0FF.toInt(), 0xFFB79BFF.toInt(), 0xFF49D6F0.toInt(), 0xFF7C6CFF.toInt(), 0xFFFF7FD0.toInt(), 0xFF110F22.toInt(), 0xFFE6ECFF.toInt()),
    // red and gold, like an old starburst
    Palette("Ember", 0xFFFFE2A0.toInt(), 0xFFFF9050.toInt(), 0xFFE0485A.toInt(), 0xFFFFC060.toInt(), 0xFFFF5C7A.toInt(), 0xFF1E0A06.toInt(), 0xFFFFE8D6.toInt()),
)

object GalaxyGen {
    /** How much of the galaxy has formed, from lifetime stardust (it starts at 300). */
    fun growth(lifetime: Int): Float = sqrt(((lifetime - 300).coerceAtLeast(0) / 15_000f).coerceIn(0f, 1f))

    fun starsFor(growth: Float) = (6_000 + 84_000 * growth).toInt()

    fun look(seed: Long, lifetime: Int): GalaxyLook {
        val r = Random(seed)
        val roll = r.nextFloat()
        val kind = when { roll < .52f -> "spiral"; roll < .76f -> "barred"; roll < .88f -> "ring"; else -> "lens" }
        val arms = when (kind) { "barred" -> 2 + r.nextInt(2); "spiral" -> listOf(2, 2, 3, 3, 4, 5)[r.nextInt(6)]; else -> 2 }
        return GalaxyLook(kind, arms, (11f + r.nextFloat() * 16f) * PI.toFloat() / 180f, 38f + r.nextFloat() * 30f, PALETTES[r.nextInt(PALETTES.size)], growth(lifetime))
    }

    /** Builds the particles. [scale] (0..1) thins everything out for small previews. */
    fun build(seed: Long, lifetime: Int, scale: Float = 1f): GalaxyLayers {
        val lk = look(seed, lifetime)
        val r = Random(seed * 7919 + 17)
        val pal = lk.palette
        val g = lk.growth
        val nStars = (starsFor(g) * scale).toInt().coerceAtLeast(1500)
        val stars = FloatArray(nStars * 8)
        val glow = ArrayList<Float>(8 * 900)
        val dust = ArrayList<Float>(8 * 600)
        val armTwist = 1f / tan(lk.pitch)
        val bar = if (lk.kind == "barred") .22f + r.nextFloat() * .12f else 0f
        val ringR = .55f + r.nextFloat() * .2f
        val coreR = when (lk.kind) { "lens" -> .32f; "ring" -> .12f; else -> .1f + r.nextFloat() * .05f } * (0.85f + .3f * g)
        val thick = .035f + r.nextFloat() * .03f
        val armJitter = r.nextFloat() * 6f

        fun gauss(): Float { var s = 0f; repeat(4) { s += r.nextFloat() }; return (s - 2f) * 1.73f }

        /** The angle of arm [i] at radius [rad]. */
        fun armAngle(i: Int, rad: Float): Float {
            val start = i * 2f * PI.toFloat() / lk.arms
            val from = max(bar, .06f)
            return start + armTwist * ln(max(rad, from) / from) + sin(rad * 9f + armJitter + i) * .08f
        }

        fun put(arr: FloatArray, idx: Int, x: Float, y: Float, z: Float, col: Int, a: Float, size: Float) {
            val o = idx * 8
            arr[o] = x; arr[o + 1] = y; arr[o + 2] = z
            arr[o + 3] = ((col shr 16) and 255) / 255f; arr[o + 4] = ((col shr 8) and 255) / 255f; arr[o + 5] = (col and 255) / 255f
            arr[o + 6] = a; arr[o + 7] = size
        }
        fun add(list: ArrayList<Float>, x: Float, y: Float, z: Float, col: Int, a: Float, size: Float) {
            list.add(x); list.add(y); list.add(z)
            list.add(((col shr 16) and 255) / 255f); list.add(((col shr 8) and 255) / 255f); list.add((col and 255) / 255f)
            list.add(a); list.add(size)
        }

        // ---------- stars
        for (i in 0 until nStars) {
            val pick = r.nextFloat()
            var x: Float; var y: Float; var z: Float
            var col: Int; var a: Float; var size: Float
            val bulgeShare = when (lk.kind) { "lens" -> .55f; "ring" -> .2f; else -> .17f }
            if (pick < bulgeShare) {
                // the bulge: a warm, dense ellipsoid
                val rad = abs(gauss()) * coreR * .7f
                val th = r.nextFloat() * 2f * PI.toFloat(); val ph = (r.nextFloat() * 2f - 1f)
                val s = sqrt(1f - ph * ph)
                x = rad * s * cos(th); y = rad * s * sin(th) * (if (bar > 0f) .65f else 1f); z = rad * ph * .62f
                if (bar > 0f && r.nextFloat() < .5f) { x = (r.nextFloat() * 2f - 1f) * bar; y = gauss() * bar * .14f; z = gauss() * thick * .8f }
                col = mix(pal.core, 0xFFFFFFFF.toInt(), r.nextFloat() * .4f)
                a = .22f + r.nextFloat() * .35f; size = .7f + r.nextFloat() * 1f
            } else if (lk.kind == "lens" || pick > .93f) {
                // a smooth disk of old stars (and the whole of a lens galaxy)
                val rad = min(1.15f, -ln(1f - r.nextFloat() * .985f) * (if (lk.kind == "lens") .26f else .3f))
                val th = r.nextFloat() * 2f * PI.toFloat()
                x = rad * cos(th); y = rad * sin(th); z = gauss() * thick * (1.4f - rad * .5f)
                col = mix(mix(pal.core, pal.outer, (rad * 1.2f).coerceIn(0f, 1f)), 0xFFFFFFFF.toInt(), .45f)
                a = .25f + r.nextFloat() * .45f; size = .6f + r.nextFloat() * .8f
            } else if (lk.kind == "ring") {
                val rad = if (r.nextFloat() < .7f) ringR + gauss() * .05f else .2f + r.nextFloat() * .8f
                val th = r.nextFloat() * 2f * PI.toFloat() + sin(rad * 14f) * .1f
                x = rad * cos(th); y = rad * sin(th); z = gauss() * thick
                val t = ((rad - .2f) / .8f).coerceIn(0f, 1f)
                col = mix(pal.inner, pal.outer, t + gauss() * .15f)
                if (r.nextFloat() < .06f) col = pal.knot
                a = .4f + r.nextFloat() * .6f; size = .7f + r.nextFloat() * 1.3f
            } else {
                // the arms
                val arm = r.nextInt(lk.arms)
                val rad = bar * .9f + (1f - bar * .9f) * r.nextFloat().pow(1.25f) * 1.02f
                val spread = (.05f + .22f * rad) * (if (r.nextFloat() < .55f) .45f else 1.25f)
                val th = armAngle(arm, rad) + gauss() * spread
                val rr = rad + gauss() * .025f
                x = rr * cos(th); y = rr * sin(th); z = gauss() * thick * (1.1f - rad * .6f)
                val t = rad.coerceIn(0f, 1f)
                col = mix(mix(pal.inner, pal.outer, t), pal.accent, (r.nextFloat() * .5f).pow(2f) * 2f)
                col = mix(col, 0xFFFFFFFF.toInt(), r.nextFloat() * .35f)
                if (r.nextFloat() < .045f * (.4f + g)) col = pal.knot
                a = .35f + r.nextFloat() * .65f
                size = .6f + r.nextFloat().pow(3f) * 2.6f
            }
            // a few bright stars
            if (r.nextFloat() < .006f) { size = 3.2f + r.nextFloat() * 2.4f; a = 1f; col = mix(col, 0xFFFFFFFF.toInt(), .6f) }
            put(stars, i, x, y, z, col, a, size)
        }

        // ---------- glow: the core's light, and nebulae along the arms
        val coreGlow = 1f + g * .8f
        add(glow, 0f, 0f, 0f, pal.core, .2f * coreGlow, coreR * 10f)
        add(glow, 0f, 0f, 0f, mix(pal.core, 0xFFFFFFFF.toInt(), .4f), .32f * coreGlow, coreR * 3.6f)
        add(glow, 0f, 0f, 0f, 0xFFFFFFFF.toInt(), .45f * coreGlow, coreR * 1.1f)
        if (bar > 0f) for (s in listOf(-1f, 1f)) add(glow, s * bar * .6f, 0f, 0f, pal.core, .16f, bar * 1.6f)
        val nNeb = ((140 + 700 * g) * max(scale, .4f)).toInt()
        for (i in 0 until nNeb) {
            val rad: Float; val th: Float
            when (lk.kind) {
                "lens" -> { rad = abs(gauss()) * .35f; th = r.nextFloat() * 2f * PI.toFloat() }
                "ring" -> { rad = ringR + gauss() * .06f; th = r.nextFloat() * 2f * PI.toFloat() }
                else -> { val arm = r.nextInt(lk.arms); rad = bar + (1f - bar) * r.nextFloat().pow(1.1f); th = armAngle(arm, rad) + gauss() * (.04f + .12f * rad) }
            }
            val t = rad.coerceIn(0f, 1f)
            var col = mix(pal.inner, pal.outer, t)
            if (r.nextFloat() < .35f) col = pal.accent
            if (r.nextFloat() < .12f * (.5f + g)) col = pal.knot
            val big = r.nextFloat()
            // pale clouds add up to white fast, so they are fainter and wider
            val lum = (((col shr 16) and 255) * .2126f + ((col shr 8) and 255) * .7152f + (col and 255) * .0722f) / 255f
            val pale = ((lum - .55f) / .35f).coerceIn(0f, 1f)
            add(glow, rad * cos(th), rad * sin(th), gauss() * thick * .6f, col, (.06f + .12f * r.nextFloat()) * (.75f + .5f * g) * (1f - .6f * pale), (.04f + big * big * .22f) * (1f + .5f * pale))
        }

        // ---------- dust lanes: dark clouds along the inner edge of each arm
        if (lk.kind != "lens") {
            val nDust = ((700 + 900 * g) * max(scale, .4f)).toInt()
            for (i in 0 until nDust) {
                val rad: Float; val th: Float
                if (lk.kind == "ring") { rad = ringR - .06f + gauss() * .03f; th = r.nextFloat() * 2f * PI.toFloat() }
                else { val arm = r.nextInt(lk.arms); rad = max(bar, .1f) + (1f - max(bar, .1f)) * r.nextFloat().pow(1.3f); th = armAngle(arm, rad) - .1f - .08f * rad + gauss() * .03f }
                add(dust, rad * cos(th), rad * sin(th), gauss() * thick * .3f, pal.dust, .07f + r.nextFloat() * .12f, .02f + r.nextFloat() * .045f)
            }
        }

        // ---------- the far field: stars all around, far away
        val nField = (3200 * max(scale, .3f)).toInt()
        val field = FloatArray(nField * 8)
        for (i in 0 until nField) {
            val u = r.nextFloat() * 2f - 1f; val th = r.nextFloat() * 2f * PI.toFloat(); val s = sqrt(1f - u * u)
            val d = 7f + r.nextFloat() * 4f
            val tint = when (r.nextInt(6)) { 0 -> 0xFFFFD9B0.toInt(); 1 -> 0xFFB9CCFF.toInt(); else -> pal.field }
            put(field, i, d * s * cos(th), d * s * sin(th), d * u, tint, .25f + r.nextFloat().pow(3f) * .75f, .5f + r.nextFloat().pow(4f) * 2.2f)
        }

        return GalaxyLayers(stars, glow.toFloatArray(), dust.toFloatArray(), field, lk)
    }

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val ar = (a shr 16) and 255; val ag = (a shr 8) and 255; val ab = a and 255
        val br = (b shr 16) and 255; val bg = (b shr 8) and 255; val bb = b and 255
        return (0xFF shl 24) or ((ar + (br - ar) * k).toInt() shl 16) or ((ag + (bg - ag) * k).toInt() shl 8) or (ab + (bb - ab) * k).toInt()
    }

    @Suppress("unused") private fun soft(x: Float) = 1f - exp(-x)
}
