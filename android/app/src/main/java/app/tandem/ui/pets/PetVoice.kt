package app.tandem.ui.pets

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.random.Random

/**
 * Plays the pets' voices from real recorded-style clips in res/raw (pet_<kind>.mp3, made with ElevenLabs
 * sound effects). Each pet gets its own pitch from its id, and every call varies a touch, so a meow never
 * sounds pasted. A kind with no clip stays silent. Muted with one switch on the pet page.
 * Kinds: meow, trill, purr, boof (bark), pant, squeak, grunt (rabbit sniff), chirp, nom, lap.
 */
object PetVoice {
    private const val PREF = "pet_voice_muted"
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    private val ready = HashSet<Int>()

    fun muted(ctx: Context) = ctx.getSharedPreferences("tandem_pets", Context.MODE_PRIVATE).getBoolean(PREF, false)
    fun setMuted(ctx: Context, v: Boolean) = ctx.getSharedPreferences("tandem_pets", Context.MODE_PRIVATE).edit().putBoolean(PREF, v).apply()

    @Synchronized private fun load(ctx: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder().setMaxStreams(2)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(this) { ready += id } }
        val app = ctx.applicationContext
        for (k in listOf("meow", "trill", "purr", "boof", "pant", "squeak", "grunt", "chirp", "nom", "lap")) {
            val res = app.resources.getIdentifier("pet_$k", "raw", app.packageName)
            if (res != 0) ids[k] = p.load(app, res, 1)
        }
        pool = p
        return p
    }

    /** Plays [kind] for a pet whose voice is pitched by [pitch] (about 0.9 to 1.1). */
    fun play(ctx: Context, kind: String, pitch: Float) {
        if (muted(ctx)) return
        val p = load(ctx)
        val id = ids[kind] ?: return
        if (synchronized(this) { id !in ready }) return
        val rate = (pitch * (0.97f + Random.nextFloat() * .06f)).coerceIn(.5f, 2f)
        p.play(id, .7f, .7f, 1, 0, rate)
    }

    /** Warms the clips up so the first tap isn't silent. */
    fun preload(ctx: Context) { load(ctx) }

    /** A pitch for this pet, the same every time, in a narrow band so every voice stays natural. */
    fun pitchOf(id: String) = .92f + ((id.hashCode() and 0xFFFF) / 65535f) * .16f
}
