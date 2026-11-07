package app.tandem.ui.pets

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The pets' little voices, made on the phone (no sound files): soft, low and short. Each pet gets its own
 * pitch from its id, and every call varies a touch, so a meow never sounds pasted. Quiet by design, and
 * muted with one switch on the pet page.
 */
object PetVoice {
    private const val RATE = 22050
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var playing = 0
    private const val PREF = "pet_voice_muted"

    fun muted(ctx: Context) = ctx.getSharedPreferences("tandem_pets", Context.MODE_PRIVATE).getBoolean(PREF, false)
    fun setMuted(ctx: Context, v: Boolean) = ctx.getSharedPreferences("tandem_pets", Context.MODE_PRIVATE).edit().putBoolean(PREF, v).apply()

    /** Plays [kind] for a pet whose voice is pitched by [pitch] (about 0.85 to 1.15). Drops it if two are already playing. */
    fun play(ctx: Context, kind: String, pitch: Float) {
        if (muted(ctx) || playing >= 2) return
        playing++
        worker.execute {
            try {
                val pcm = make(kind, pitch * (0.96f + Random.nextFloat() * .08f)) ?: return@execute
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setVolume(.55f)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 60)
                track.release()
            } catch (_: Throwable) {
            } finally { playing-- }
        }
    }

    /** A pitch for this pet, the same every time. */
    fun pitchOf(id: String) = .86f + ((id.hashCode() and 0xFFFF) / 65535f) * .28f

    // ------------------------------------------------------------------ the synth

    private class Buf(seconds: Float) {
        val n = (seconds * RATE).toInt()
        val a = FloatArray(n)
        var noise = 0f
        val rnd = Random(System.nanoTime())
        fun white() = rnd.nextFloat() * 2f - 1f
        /** One-pole lowpassed noise. */
        fun soft(k: Float): Float { noise += (white() - noise) * k; return noise }
    }

    /** A voiced tone with a pitch curve [f] (Hz over 0..1), harmonic weights [h] (over 0..1), and an envelope. */
    private fun Buf.voice(start: Float, dur: Float, gain: Float, f: (Float) -> Float, h: (Float, Int) -> Float, vibrato: Float = .012f, attack: Float = .03f, release: Float = .12f) {
        var ph = 0.0
        val s0 = (start * RATE).toInt(); val len = (dur * RATE).toInt()
        for (i in 0 until len) {
            val idx = s0 + i; if (idx >= n) break
            val x = i / len.toFloat(); val tt = i / RATE.toFloat()
            val hz = f(x) * (1f + vibrato * sin(2f * PI.toFloat() * 5.6f * tt))
            ph += 2.0 * PI * hz / RATE
            var v = 0f
            for (k in 1..6) { val w = h(x, k); if (w > 0f) v += w * sin((ph * k).toFloat()) }
            val env = min(1f, tt / attack) * min(1f, (dur - tt) / release)
            a[idx] += v * env * gain
        }
    }

    private fun Buf.puff(start: Float, dur: Float, gain: Float, k: Float) {
        val s0 = (start * RATE).toInt(); val len = (dur * RATE).toInt()
        for (i in 0 until len) {
            val idx = s0 + i; if (idx >= n) break
            val x = i / len.toFloat()
            a[idx] += soft(k) * gain * sin(PI.toFloat() * x)
        }
    }

    private fun Buf.pcm(): ShortArray {
        var peak = 0.0001f
        for (v in a) peak = max(peak, kotlin.math.abs(v))
        val g = min(1f, .7f / peak)
        // a tiny fade at both ends so nothing clicks
        val fade = 220
        return ShortArray(n) { i ->
            val f = min(1f, min(i, n - 1 - i) / fade.toFloat())
            (a[i] * g * f * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun make(kind: String, p: Float): ShortArray? = when (kind) {
        // a small, low "mew-ow": up, then down, the vowel opening in the middle
        "meow" -> Buf(.62f).apply {
            voice(0f, .6f, .5f, { x -> p * (330f + 230f * sin(min(1f, x * 1.5f) * PI.toFloat() * .9f) - 60f * x) }, { x, k ->
                val open = sin(x * PI.toFloat())
                when (k) { 1 -> 1f; 2 -> .35f + .5f * open; 3 -> .1f + .45f * open; 4 -> .25f * open; 5 -> .12f * open; else -> 0f }
            }, attack = .05f, release = .2f)
            puff(0f, .6f, .03f, .3f)
        }.pcm()
        // a rolled "mrrp", the happy hello
        "trill" -> Buf(.34f).apply {
            voice(0f, .32f, .5f, { x -> p * (360f + 120f * x) }, { x, k -> val roll = .55f + .45f * sin(x * 2f * PI.toFloat() * 7f); when (k) { 1 -> roll; 2 -> .5f * roll; 3 -> .2f * roll; else -> 0f } }, vibrato = .02f)
        }.pcm()
        // a purr: a low hum that rises and falls with each breath
        "purr" -> Buf(1.3f).apply {
            val s0 = 0; for (i in 0 until n) {
                val tt = i / RATE.toFloat()
                val am = .5f + .5f * sin(2f * PI.toFloat() * 26f * tt)
                val breath = sin(PI.toFloat() * (tt / 1.3f)) * (.6f + .4f * sin(2f * PI.toFloat() * tt / .65f))
                a[s0 + i] = (soft(.08f) * .9f + .25f * sin(2f * PI.toFloat() * 52f * p * tt)) * am * breath
            }
        }.pcm()
        // a soft, low "boof", sometimes two
        "boof" -> Buf(.5f).apply {
            val two = Random.nextFloat() < .4f
            for (s in if (two) listOf(0f, .24f) else listOf(0f)) {
                voice(s, .2f, .6f, { x -> p * (230f - 70f * x) }, { x, k -> when (k) { 1 -> 1f; 2 -> .6f; 3 -> .4f * (1f - x); 4 -> .2f * (1f - x); else -> 0f } }, vibrato = .0f, attack = .012f, release = .09f)
                puff(s, .07f, .2f, .5f)
            }
        }.pcm()
        // happy panting: little breaths
        "pant" -> Buf(.9f).apply { for (i in 0 until 4) puff(i * .21f, .13f, .18f, .35f) }.pcm()
        // a tiny squeak (kept low and round)
        "squeak" -> Buf(.36f).apply {
            val count = 1 + Random.nextInt(2)
            for (i in 0 until count) voice(i * .16f, .12f, .45f, { x -> p * (760f + 380f * sin(x * PI.toFloat())) }, { _, k -> when (k) { 1 -> 1f; 2 -> .25f; else -> 0f } }, vibrato = .0f, attack = .01f, release = .05f)
        }.pcm()
        // a rabbit's soft grunt: a low little honk with a breath
        "grunt" -> Buf(.22f).apply {
            voice(0f, .16f, .45f, { x -> p * (190f + 40f * x) }, { _, k -> when (k) { 1 -> 1f; 2 -> .7f; 3 -> .5f; 4 -> .3f; else -> 0f } }, vibrato = .0f, attack = .01f, release = .07f)
            puff(0f, .2f, .12f, .25f)
        }.pcm()
        // a parrot's chirp: two or three rising whistles
        "chirp" -> Buf(.5f).apply {
            val count = 2 + Random.nextInt(2)
            for (i in 0 until count) voice(i * .13f, .1f, .45f, { x -> p * (1050f + 650f * x + i * 120f) }, { _, k -> when (k) { 1 -> 1f; 2 -> .15f; else -> 0f } }, vibrato = .0f, attack = .008f, release = .04f)
        }.pcm()
        // eating: small soft crunches
        "nom" -> Buf(.7f).apply { for (i in 0 until 3) { puff(i * .2f, .06f, .4f, .7f); voice(i * .2f, .07f, .12f, { p * 260f }, { _, k -> if (k == 1) 1f else 0f }, vibrato = 0f, attack = .005f, release = .04f) } }.pcm()
        // drinking: little laps
        "lap" -> Buf(.7f).apply { for (i in 0 until 4) voice(i * .16f, .06f, .25f, { x -> p * (520f - 200f * x) }, { _, k -> if (k == 1) 1f else if (k == 2) .3f else 0f }, vibrato = 0f, attack = .004f, release = .03f) }.pcm()
        else -> null
    }

    @Suppress("unused") private fun decay(x: Float, k: Float) = exp(-k * x)
}
