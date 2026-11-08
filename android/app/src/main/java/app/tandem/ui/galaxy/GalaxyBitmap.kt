package app.tandem.ui.galaxy

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The galaxy painted on the CPU into a bitmap: for the Today card, the re-roll choices and the home screen
 * widget, where a live GL view would be too much. Light is added up in floats and then tone-mapped, like a
 * long exposure, so crowded places glow instead of clipping.
 */
object GalaxyBitmap {
    private val cache = object : LinkedHashMap<String, Bitmap>(8, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > 6
    }

    fun render(seed: Long, lifetime: Int, w: Int, h: Int, yawDeg: Float = 25f, pitchDeg: Float? = null, zoom: Float = 1f, clear: Boolean = true): Bitmap {
        val key = "$seed/$lifetime/$w/$h/$yawDeg/$pitchDeg/$zoom/$clear"
        synchronized(cache) { cache[key]?.let { return it } }
        val l = GalaxyGen.build(seed, lifetime, scale = (w * h / (700f * 700f)).coerceIn(.35f, .8f))
        val bmp = paint(l, w, h, yawDeg, pitchDeg ?: l.look.tilt, zoom, clear)
        synchronized(cache) { cache[key] = bmp }
        return bmp
    }

    private fun paint(l: GalaxyLayers, w: Int, h: Int, yawDeg: Float, pitchDeg: Float, zoom: Float, clear: Boolean): Bitmap {
        val R = FloatArray(w * h); val G = FloatArray(w * h); val B = FloatArray(w * h)
        val yaw = yawDeg * PI.toFloat() / 180f; val pitch = -pitchDeg * PI.toFloat() / 180f
        val cy = cos(yaw); val sy = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
        val dist = 3.1f
        val focal = min(w, h) * 1.25f * zoom
        val cx0 = w / 2f; val cy0 = h / 2f

        fun project(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
            // yaw in the disk's plane, then tilt toward the viewer (same as the GL view)
            val x1 = x * cy - y * sy; val y1 = x * sy + y * cy
            val y2 = y1 * cp - z * sp; val z2 = y1 * sp + z * cp
            val d = dist - z2
            if (d < .1f) return false
            out[0] = cx0 + x1 / d * focal; out[1] = cy0 - y2 / d * focal; out[2] = d
            return true
        }

        val o = FloatArray(3)
        fun splat(px: Float, py: Float, rad: Float, r: Float, g: Float, b: Float, a: Float, soft: Boolean) {
            val ri = rad.coerceAtMost(min(w, h) * .6f)
            val x0 = max(0, (px - ri).toInt()); val x1 = min(w - 1, (px + ri).toInt())
            val y0 = max(0, (py - ri).toInt()); val y1 = min(h - 1, (py + ri).toInt())
            if (x0 > x1 || y0 > y1) return
            val inv = 1f / (ri * ri)
            for (yy in y0..y1) for (xx in x0..x1) {
                val dx = xx + .5f - px; val dy = yy + .5f - py
                val q = (dx * dx + dy * dy) * inv
                if (q >= 1f) continue
                val k = if (soft) (1f - q).let { it * it * it } else exp(-q * 9f) + .18f * exp(-q * 2.5f)
                val i = yy * w + xx
                R[i] += r * k * a; G[i] += g * k * a; B[i] += b * k * a
            }
        }

        val px = w / 1080f * 2.2f
        fun stars(arr: FloatArray) {
            var i = 0
            while (i < arr.size) {
                if (project(arr[i], arr[i + 1], arr[i + 2], o)) {
                    var s = arr[i + 7] * px * 1.6f * (3f / o[2]).coerceIn(.6f, 1.8f); var a = arr[i + 6]
                    if (s < 1.4f) { a *= s / 1.4f; s = 1.4f }
                    splat(o[0], o[1], s / 2f + .6f, arr[i + 3], arr[i + 4], arr[i + 5], a * 1.7f, false)
                }
                i += 8
            }
        }
        stars(l.field)
        var i = 0
        while (i < l.glow.size) {
            val a = l.glow
            if (project(a[i], a[i + 1], a[i + 2], o)) splat(o[0], o[1], a[i + 7] * focal / o[2] / 2f, a[i + 3], a[i + 4], a[i + 5], a[i + 6] * 1.5f, true)
            i += 8
        }
        // dust darkens what's already there
        i = 0
        while (i < l.dust.size) {
            val a = l.dust
            if (project(a[i], a[i + 1], a[i + 2], o)) {
                val rad = a[i + 7] * focal / o[2] / 2f
                val x0 = max(0, (o[0] - rad).toInt()); val x1 = min(w - 1, (o[0] + rad).toInt())
                val y0 = max(0, (o[1] - rad).toInt()); val y1 = min(h - 1, (o[1] + rad).toInt())
                val inv = 1f / max(.01f, rad * rad)
                for (yy in y0..y1) for (xx in x0..x1) {
                    val dx = xx + .5f - o[0]; val dy = yy + .5f - o[1]
                    val q = (dx * dx + dy * dy) * inv
                    if (q >= 1f) continue
                    val k = (1f - q).let { it * it } * a[i + 6]
                    val idx = yy * w + xx
                    R[idx] = R[idx] * (1f - k) + a[i + 3] * k; G[idx] = G[idx] * (1f - k) + a[i + 4] * k; B[idx] = B[idx] * (1f - k) + a[i + 5] * k
                }
            }
            i += 8
        }
        stars(l.stars)

        val px2 = IntArray(w * h)
        for (k in 0 until w * h) {
            val r = tone(R[k]); val g = tone(G[k]); val b = tone(B[k])
            px2[k] = if (!clear) (0xFF shl 24) or (r shl 16) or (g shl 8) or b else {
                // light on a clear background, so whatever sky is behind shows through
                val a = maxOf(r, g, b)
                if (a == 0) 0 else (a shl 24) or ((r * 255 / a) shl 16) or ((g * 255 / a) shl 8) or (b * 255 / a)
            }
        }
        return Bitmap.createBitmap(px2, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun tone(v: Float): Int = ((1f - exp(-v * 1.1f)).let { sqrt(it) * .35f + it * .65f } * 255f).toInt().coerceIn(0, 255)
}
