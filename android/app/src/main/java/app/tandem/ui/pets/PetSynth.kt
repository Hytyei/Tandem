package app.tandem.ui.pets

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The pets' voices, made from scratch the way a throat makes them: a buzzing source (a pitch with all its
 * harmonics) shaped by a few resonances (formants) that move as the "mouth" opens and closes, plus breath.
 * That is what turns a beep into a "mew". Small natural wobbles in pitch and loudness keep it from sounding
 * machine-made. Pure Kotlin: no Android here, so it can be tried anywhere.
 */
object PetSynth {
    const val RATE = 32000

    /** A resonance: centre frequency, width (Hz) and strength. */
    class F(val hz: Float, val bw: Float, val gain: Float)

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

    /** Formants that move through a list of "mouth shapes" over the sound (0..1). */
    private fun shapes(x: Float, keys: List<Pair<Float, List<F>>>): List<F> {
        if (x <= keys.first().first) return keys.first().second
        for (i in 1 until keys.size) {
            val (x1, f1) = keys[i]
            val (x0, f0) = keys[i - 1]
            if (x <= x1) {
                val t = smooth((x - x0) / (x1 - x0))
                return f0.indices.map { k -> F(lerp(f0[k].hz, f1[k].hz, t), lerp(f0[k].bw, f1[k].bw, t), lerp(f0[k].gain, f1[k].gain, t)) }
            }
        }
        return keys.last().second
    }

    /** How loud harmonic [hz] comes out through these formants. */
    private fun envelope(hz: Float, fs: List<F>): Float {
        var a = 0f
        for (f in fs) { val d = (hz - f.hz) / f.bw; a += f.gain / (1f + d * d) }
        return a
    }

    class Buf(seconds: Float) {
        val n = (seconds * RATE).toInt()
        val a = FloatArray(n)
        val rnd = Random(System.nanoTime())
        fun white() = rnd.nextFloat() * 2f - 1f

        /**
         * A voiced sound from [start] for [dur] seconds: pitch [f0] and mouth shape [mouth] over 0..1, loudness
         * [amp] over 0..1, a little breath mixed in. Pitch wobbles slowly (jitter) like a real voice.
         */
        fun voice(start: Float, dur: Float, gain: Float, f0: (Float) -> Float, mouth: (Float) -> List<F>, amp: (Float) -> Float, breath: Float = .06f, wobble: Float = .012f, roll: Float = 1f) {
            val s0 = (start * RATE).toInt(); val len = (dur * RATE).toInt()
            var ph = 0.0
            var drift = 0f; var driftV = 0f
            var weights = FloatArray(0); var fsNow: List<F> = emptyList()
            var nLp = 0f; var nBp = 0f
            for (i in 0 until len) {
                val idx = s0 + i; if (idx >= n) break
                val x = i / len.toFloat()
                // a slow random walk in pitch: the "life" in a voice
                driftV += (white() * .002f - drift * .0006f); drift = (drift + driftV).coerceIn(-1f, 1f); driftV *= .995f
                val hz = f0(x) * (1f + wobble * drift + .006f * sin(2f * PI.toFloat() * 5.2f * i / RATE))
                if (i % 48 == 0) {
                    fsNow = mouth(x)
                    val harm = min(28, (6000f / max(60f, hz)).toInt())
                    weights = FloatArray(harm) { k -> val h = (k + 1) * hz; envelope(h, fsNow) / (1f + (k * .12f) * roll) }
                }
                ph += 2.0 * PI * hz / RATE
                var v = 0f
                for (k in weights.indices) v += weights[k] * sin((ph * (k + 1)).toFloat())
                // breath: noise, softened, coloured by the mouth
                nLp += (white() - nLp) * .35f; nBp += (nLp - nBp) * .12f
                val noise = (nLp - nBp) * breath * (fsNow.firstOrNull()?.gain ?: 1f)
                a[idx] += (v * .22f + noise) * amp(x) * gain
            }
        }

        /** A burst of soft noise (breaths, puffs, crunches). [bright] 0..1 opens it up. */
        fun puff(start: Float, dur: Float, gain: Float, bright: Float, shape: (Float) -> Float = { sin(PI.toFloat() * it) }) {
            val s0 = (start * RATE).toInt(); val len = (dur * RATE).toInt()
            var lp = 0f; var lp2 = 0f
            val k = .05f + .6f * bright
            for (i in 0 until len) {
                val idx = s0 + i; if (idx >= n) break
                lp += (white() - lp) * k; lp2 += (lp - lp2) * k
                a[idx] += lp2 * gain * shape(i / len.toFloat())
            }
        }

        fun pcm(volume: Float = .62f): ShortArray {
            // a gentle low cut (no rumble) and a soft high cut (no fizz), then level and fade
            var hp = 0f; var prev = 0f; var lp = 0f
            for (i in 0 until n) { val x = a[i]; hp = .995f * (hp + x - prev); prev = x; lp += (hp - lp) * .55f; a[i] = lp }
            var peak = 1e-4f
            for (v in a) peak = max(peak, abs(v))
            val g = volume / peak
            val fade = RATE / 120
            return ShortArray(n) { i ->
                val f = min(1f, min(i, n - 1 - i) / fade.toFloat())
                // a soft limiter so peaks round off instead of clipping
                val y = a[i] * g * f
                val s = y / (1f + abs(y) * .35f)
                (s * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
    }

    // mouth shapes
    private val M = listOf(F(280f, 90f, 1f), F(1100f, 220f, .12f), F(2500f, 300f, .05f))      // closed, nasal "m"
    private val EE = listOf(F(520f, 120f, 1f), F(1900f, 220f, .7f), F(2900f, 300f, .35f))     // "e" of mew
    private val AA = listOf(F(780f, 140f, 1f), F(1400f, 220f, .65f), F(2700f, 320f, .28f))    // open "a"
    private val OW = listOf(F(560f, 120f, 1f), F(950f, 180f, .55f), F(2500f, 320f, .18f))     // round "ow"
    private val UH = listOf(F(450f, 120f, 1f), F(1150f, 200f, .4f), F(2400f, 300f, .15f))     // soft "uh"
    private val OO = listOf(F(330f, 100f, 1f), F(800f, 160f, .3f), F(2300f, 300f, .08f))      // "oo"

    private fun bump(x: Float, a: Float, r: Float) = smooth(x / a) * smooth((1f - x) / r)

    /** The sound [kind] at [p] times this pet's pitch (about 0.85 to 1.15). Null for an unknown kind. */
    fun make(kind: String, p: Float): ShortArray? = when (kind) {
        // a small, low "mew-ow": opens from a hummed m, rises a little, settles
        "meow" -> Buf(.62f).apply {
            val len = .48f + Random.nextFloat() * .1f
            voice(.01f, len, 1f, { x -> p * (390f + 110f * sin(min(1f, x * 1.6f) * PI.toFloat() * .85f) - 65f * x) },
                { x -> shapes(x, listOf(0f to M, .18f to EE, .5f to AA, .85f to OW, 1f to OW)) },
                { x -> bump(x, .14f, .32f) * (.55f + .45f * smooth(x / .2f)) }, breath = .02f)
        }.pcm()
        // the hello: a rolled, happy "mrrp" going up
        "trill" -> Buf(.4f).apply {
            voice(.01f, .34f, 1f, { x -> p * (400f + 140f * x) },
                { x -> shapes(x, listOf(0f to M, .45f to UH, 1f to EE)) },
                { x -> bump(x, .1f, .25f) * (.6f + .4f * sin(x * 2f * PI.toFloat() * 9f)) }, breath = .015f)
        }.pcm()
        // a purr: rumbling pulses, louder on the out-breath
        "purr" -> Buf(1.6f).apply {
            val rate = 26f + Random.nextFloat() * 3f
            var t = 0f
            while (t < 1.55f) {
                val breathPhase = (t % .8f) / .8f
                val out = breathPhase > .45f
                val level = (if (out) 1f else .55f) * smooth(t / .2f) * smooth((1.55f - t) / .25f)
                puff(t, .03f, .9f * level, .18f) { x -> exp(-x * 5f) * smooth(x * 8f) }
                t += 1f / (rate * (if (out) 1.08f else 1f))
            }
            voice(0f, 1.55f, .25f, { p * 52f }, { OO }, { x -> smooth(x / .2f) * smooth((1f - x) / .2f) }, breath = 0f, wobble = .02f, roll = 2f)
        }.pcm(.5f)
        // a soft, low "boof", sometimes two
        "boof" -> Buf(.5f).apply {
            val twice = Random.nextFloat() < .35f
            for (s in if (twice) listOf(0f, .23f) else listOf(0f)) {
                puff(s, .05f, .25f, .4f)
                voice(s + .01f, .17f, 1f, { x -> p * (250f - 80f * x) }, { x -> shapes(x, listOf(0f to UH, .4f to OW, 1f to OO)) }, { x -> bump(x, .12f, .55f) }, breath = .04f, roll = 1.5f)
            }
        }.pcm()
        // happy panting: soft breaths in and out
        "pant" -> Buf(1.0f).apply { for (i in 0 until 5) puff(i * .19f, .12f, if (i % 2 == 0) .55f else .38f, .5f) }.pcm(.4f)
        // a tiny, round squeak (kept low)
        "squeak" -> Buf(.38f).apply {
            val count = 1 + Random.nextInt(2)
            for (i in 0 until count) voice(i * .15f, .1f, 1f, { x -> p * (820f + 300f * sin(x * PI.toFloat())) }, { EE }, { x -> bump(x, .2f, .4f) }, breath = .02f, roll = 3f)
        }.pcm(.5f)
        // a rabbit's soft grunt: a low little honk through the nose
        "grunt" -> Buf(.24f).apply {
            puff(0f, .06f, .3f, .3f)
            voice(.01f, .15f, 1f, { x -> p * (210f + 40f * x) }, { M }, { x -> bump(x, .1f, .5f) }, breath = .04f, roll = 1.5f)
        }.pcm(.5f)
        // a parrot's chirp: two or three rising whistles
        "chirp" -> Buf(.5f).apply {
            val count = 2 + Random.nextInt(2)
            for (i in 0 until count) voice(i * .13f, .09f, 1f, { x -> p * (1150f + 600f * x + i * 90f) }, { listOf(F(1800f, 900f, 1f)) }, { x -> bump(x, .15f, .35f) }, breath = 0f, roll = 6f)
        }.pcm(.45f)
        // eating: small crunches, then a happy little "mm"
        "nom" -> Buf(.8f).apply {
            for (i in 0 until 3) puff(i * .17f, .05f, .5f, .75f) { x -> exp(-x * 6f) }
            voice(.52f, .2f, .6f, { p * 330f }, { M }, { x -> bump(x, .2f, .4f) }, breath = .02f)
        }.pcm(.45f)
        // drinking: quick little laps
        "lap" -> Buf(.75f).apply {
            for (i in 0 until 4) puff(i * .16f, .045f, .5f, .55f) { x -> exp(-x * 7f) * smooth(x * 10f) }
        }.pcm(.4f)
        else -> null
    }

    /** Writes [pcm] as a WAV file's bytes (for trying the voices outside the app). */
    fun wav(pcm: ShortArray): ByteArray {
        val data = pcm.size * 2
        val b = java.nio.ByteBuffer.allocate(44 + data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + data); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(1); b.putInt(RATE); b.putInt(RATE * 2); b.putShort(2); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(data)
        pcm.forEach { b.putShort(it) }
        return b.array()
    }

    @Suppress("unused") private fun rms(a: FloatArray) = sqrt(a.fold(0f) { s, v -> s + v * v } / a.size)
}
