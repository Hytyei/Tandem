package app.tandem.ui.components

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The planet's surface: a map of the whole world (built once per look, from noise) and a sphere renderer that
 * wraps that map around a ball, lit from the brighter sun. Building the map takes a moment, so the app does it off
 * the main thread ([prepare]); turning the sphere afterwards is only a lookup per pixel.
 */
object PlanetSurface {
    private const val TW = 512
    private const val TH = 256

    /** A map of the world (equirectangular): colour, whether it is sea, and cloud cover. */
    class Map(val color: IntArray, val sea: BooleanArray, val cloud: FloatArray)

    private val maps = java.util.concurrent.ConcurrentHashMap<String, Map>()

    private fun key(l: PlanetLook) = "${l.seed}:${(l.ocean / 4).toInt()},${(l.forest / 4).toInt()},${(l.crystal / 4).toInt()},${(l.aurora / 4).toInt()}:${(l.ice / 5).toInt()}:${(l.atmosphere / 20).toInt()}"

    fun ready(l: PlanetLook): Map? = maps[key(l)]

    /** Builds (or finds) the map for this look. Safe to call from any thread. */
    fun prepare(l: PlanetLook): Map = maps.getOrPut(key(l)) { if (maps.size > 8) maps.clear(); build(l) }

    // ---------------------------------------------------------------- noise
    private fun hash(x: Int, y: Int, z: Int, s: Int): Float {
        var h = x * 73856093 xor (y * 19349663) xor (z * 83492791) xor (s * -1640531535)
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF) / 16777215f
    }

    private fun fade(t: Float) = t * t * (3 - 2 * t)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun value3(x: Float, y: Float, z: Float, s: Int): Float {
        val xi = floor(x).toInt(); val yi = floor(y).toInt(); val zi = floor(z).toInt()
        val xf = fade(x - xi); val yf = fade(y - yi); val zf = fade(z - zi)
        val a = lerp(lerp(hash(xi, yi, zi, s), hash(xi + 1, yi, zi, s), xf), lerp(hash(xi, yi + 1, zi, s), hash(xi + 1, yi + 1, zi, s), xf), yf)
        val b = lerp(lerp(hash(xi, yi, zi + 1, s), hash(xi + 1, yi, zi + 1, s), xf), lerp(hash(xi, yi + 1, zi + 1, s), hash(xi + 1, yi + 1, zi + 1, s), xf), yf)
        return lerp(a, b, zf)
    }

    private fun fbm(x: Float, y: Float, z: Float, s: Int, octaves: Int): Float {
        var sum = 0f; var amp = .5f; var f = 1f; var norm = 0f
        for (i in 0 until octaves) { sum += amp * value3(x * f, y * f, z * f, s + i * 31); norm += amp; amp *= .5f; f *= 2.03f }
        return sum / norm
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(sh: Int) = (((a shr sh) and 0xFF) * (1 - k) + ((b shr sh) and 0xFF) * k).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun shade(c: Int, f: Float): Int {
        fun ch(sh: Int) = (((c shr sh) and 0xFF) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // ---------------------------------------------------------------- the map
    private fun build(l: PlanetLook): Map {
        val n = TW * TH
        val elev = FloatArray(n); val moist = FloatArray(n); val detail = FloatArray(n); val cloud = FloatArray(n); val latN = FloatArray(n)
        val s = l.seed * 7
        for (j in 0 until TH) {
            val lat = (.5f - (j + .5f) / TH) * PI.toFloat()
            val cl = cos(lat); val sl = sin(lat)
            for (i in 0 until TW) {
                val lon = (i + .5f) / TW * 2 * PI.toFloat()
                val x = cl * cos(lon); val y = sl; val z = cl * sin(lon)
                val k = j * TW + i
                // continents: noise warped by noise, so coastlines wander like real ones
                val wx = fbm(x * 1.3f + 11f, y * 1.3f, z * 1.3f, s + 5, 2) - .5f
                val wz = fbm(x * 1.3f, y * 1.3f + 7f, z * 1.3f, s + 9, 2) - .5f
                elev[k] = fbm(x * 1.8f + wx * 1.4f, y * 1.8f, z * 1.8f + wz * 1.4f, s, 5)
                moist[k] = fbm(x * 1.6f + 40f, y * 1.6f, z * 1.6f, s + 101, 3)
                detail[k] = fbm(x * 9f, y * 9f, z * 9f, s + 202, 2)
                cloud[k] = fbm(x * 2.6f + 3f, y * 4.2f, z * 2.6f, s + 303, 4)
                latN[k] = abs(lat) / (PI.toFloat() / 2)
            }
        }
        // sea level: more ocean share, more sea (but always some land, and always some sea)
        val landFrac = ((1f - l.ocean / 100f) * .72f + .06f).coerceIn(.12f, .8f)
        val sorted = elev.copyOf().also { it.sort() }
        val sea = sorted[((1f - landFrac) * (n - 1)).toInt()]
        val top = sorted[n - 1]
        // which land is which: dry and warm near the equator is crystal, wet middle latitudes forest, the far north and south aurora
        val land = (0 until n).filter { elev[it] > sea }
        val key = FloatArray(n)
        for (k in land) key[k] = .55f * latN[k] + .45f * moist[k]
        val order = land.sortedBy { key[it] }
        val lf = l.forest.coerceAtLeast(1f); val lc = l.crystal.coerceAtLeast(1f); val la = l.aurora.coerceAtLeast(1f)
        val tot = lf + lc + la
        val biome = ByteArray(n)        // 0 sea, 1 crystal, 2 forest, 3 aurora
        order.forEachIndexed { r, k ->
            val q = r.toFloat() / order.size.coerceAtLeast(1)
            biome[k] = when { q < lc / tot -> 1; q < (lc + lf) / tot -> 2; else -> 3 }
        }
        val iceFrac = .05f + (l.ice / 100f) * .55f
        val color = IntArray(n); val isSea = BooleanArray(n)
        val atmo = (l.atmosphere / 100f).coerceIn(0f, 1f)
        for (k in 0 until n) {
            val e = elev[k]; val d = detail[k]
            var col: Int
            if (e <= sea) {
                isSea[k] = true
                val depth = ((sea - e) / (sea - sorted[0]).coerceAtLeast(.001f)).pow(.55f)
                col = mix(0xFF67A9EE.toInt(), 0xFF16226A.toInt(), depth)
                if (sea - e < .012f) col = mix(col, 0xFF9CD7F0.toInt(), .45f)          // shallows along the coast
            } else {
                val h = ((e - sea) / (top - sea).coerceAtLeast(.001f))
                col = when (biome[k].toInt()) {
                    1 -> mix(0xFFD9C4FA.toInt(), 0xFF8C68D2.toInt(), h * .8f + d * .3f)
                    2 -> mix(0xFF74C994.toInt(), 0xFF2E6A55.toInt(), h * .7f + d * .4f)
                    else -> mix(0xFFAAF1E6.toInt(), 0xFF3B9496.toInt(), h * .7f + d * .4f)
                }
                if (e - sea < .006f) col = mix(col, 0xFFE9DDBF.toInt(), .5f)             // a thin beach
                if (h > .62f) col = mix(col, 0xFFEDE6F7.toInt(), (h - .62f) * 1.6f)     // high ground goes pale
                col = shade(col, .9f + d * .2f)
            }
            // ice caps with a ragged edge, and frost on the peaks when it is cold
            val edge = latN[k] + (d - .5f) * .12f
            if (edge > 1f - iceFrac) col = mix(col, 0xFFEFF4FF.toInt(), ((edge - (1f - iceFrac)) / .05f).coerceIn(0f, 1f) * .95f)
            else if (!isSea[k] && l.ice > 40f && e > sea + (top - sea) * (1.1f - l.ice / 100f)) col = mix(col, 0xFFEFF4FF.toInt(), .7f)
            color[k] = col
            // clouds: thicker when the air is full
            val cc = cloud[k]
            val lo = .58f - .1f * atmo
            cloud[k] = (((cc - lo) / .18f).coerceIn(0f, 1f)) * (.35f + .55f * atmo)
        }
        return Map(color, isSea, cloud)
    }

    // ---------------------------------------------------------------- the sphere
    /** Per-pixel geometry and light for one sphere size and light side; does not change while the planet turns. */
    private class Frame(val size: Int, val lonBase: FloatArray, val row: IntArray, val light: FloatArray, val spec: FloatArray, val rim: FloatArray, val alpha: FloatArray)

    /** What one size of planet keeps between frames: its geometry, its bitmap, and what that bitmap last showed. */
    private class Slot(val frame: Frame, val bitmap: Bitmap, val pixels: IntArray) {
        var rot = Float.NaN; var map: Map? = null; var dim = -1f
    }

    // keyed by size and light side: Today's small card and the planet page each keep their own
    private val slots = LinkedHashMap<Long, Slot>()

    private fun frameFor(size: Int, left: Boolean): Frame {
        val n = size * size
        val lonBase = FloatArray(n); val row = IntArray(n); val light = FloatArray(n); val spec = FloatArray(n); val rim = FloatArray(n); val alpha = FloatArray(n)
        // the light comes from the brighter sun, a little above and in front
        var lx = if (left) -.62f else .62f; var ly = .42f; var lz = .66f
        val ll = sqrt(lx * lx + ly * ly + lz * lz); lx /= ll; ly /= ll; lz /= ll
        var hx = lx; var hy = ly; var hz = lz + 1f
        val hl = sqrt(hx * hx + hy * hy + hz * hz); hx /= hl; hy /= hl; hz /= hl
        for (j in 0 until size) for (i in 0 until size) {
            val k = j * size + i
            val x = (i + .5f) / size * 2 - 1; val y = 1 - (j + .5f) / size * 2
            val r2 = x * x + y * y
            if (r2 > 1f) { alpha[k] = 0f; continue }
            val z = sqrt(1 - r2)
            lonBase[k] = atan2(x, z)
            row[k] = ((.5f - asin(y.coerceIn(-1f, 1f)) / PI.toFloat()) * TH).toInt().coerceIn(0, TH - 1)
            val diff = (x * lx + y * ly + z * lz).coerceAtLeast(0f)
            light[k] = .1f + .9f * diff.pow(.85f)
            spec[k] = (x * hx + y * hy + z * hz).coerceAtLeast(0f).pow(48f) * .55f
            rim[k] = (1 - z).pow(3f)
            alpha[k] = ((1f - sqrt(r2)) * size / 1.5f).coerceIn(0f, 1f)          // a soft edge, one pixel wide
        }
        return Frame(size, lonBase, row, light, spec, rim, alpha)
    }

    /** The planet as a [size]×[size] bitmap, turned by [rotation] radians. [dim] 0..1 drains its colour (a neglected world). */
    @Synchronized
    fun render(map: Map, size: Int, rotation: Float, lightFromLeft: Boolean, dim: Float): Bitmap {
        val key = size.toLong() * 2 + if (lightFromLeft) 1 else 0
        val slot = slots.getOrPut(key) {
            if (slots.size >= 4) slots.remove(slots.keys.first())
            Slot(frameFor(size, lightFromLeft), Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888), IntArray(size * size))
        }
        // nothing to redo until the planet has turned by about a pixel
        val step = 2 * PI.toFloat() / TW * .7f
        if (slot.map === map && slot.dim == dim && abs(slot.rot - rotation) < step) return slot.bitmap
        slot.rot = rotation; slot.map = map; slot.dim = dim
        val f = slot.frame; val pixels = slot.pixels; val bmp = slot.bitmap
        val tau = 2 * PI.toFloat()
        val cloudTurn = rotation * 1.35f + .7f
        for (k in 0 until size * size) {
            val a = f.alpha[k]
            if (a <= 0f) { pixels[k] = 0; continue }
            var u = (f.lonBase[k] + rotation) / tau; u -= floor(u)
            var cu = (f.lonBase[k] + cloudTurn) / tau; cu -= floor(cu)
            val idx = f.row[k] * TW + (u * TW).toInt().coerceIn(0, TW - 1)
            val cidx = f.row[k] * TW + (cu * TW).toInt().coerceIn(0, TW - 1)
            val base = map.color[idx]
            var r = ((base shr 16) and 0xFF).toFloat(); var g = ((base shr 8) and 0xFF).toFloat(); var b = (base and 0xFF).toFloat()
            // clouds sit over the ground
            val cl = map.cloud[cidx]
            r += (245 - r) * cl; g += (247 - g) * cl; b += (255 - b) * cl
            // sunlight, a glint on the sea, and a blue rim of air
            val lit = f.light[k]
            r *= lit; g *= lit; b *= lit
            if (map.sea[idx] && cl < .3f) { val sp = f.spec[k] * 255; r += sp; g += sp; b += sp }
            val rim = f.rim[k] * (.35f + .5f * lit)
            r += (150 - r) * rim * .6f; g += (175 - g) * rim * .6f; b += (255 - b) * rim * .6f
            if (dim > 0f) { val grey = (r * .3f + g * .5f + b * .2f); r += (grey * .8f + 30 - r) * dim; g += (grey * .8f + 28 - g) * dim; b += (grey * .8f + 48 - b) * dim }
            pixels[k] = ((a * 255).toInt() shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)
        return bmp
    }
}
