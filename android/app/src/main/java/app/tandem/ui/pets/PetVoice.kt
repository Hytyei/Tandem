package app.tandem.ui.pets

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.random.Random

/**
 * Plays the pets' little voices (made by [PetSynth], no sound files): soft, low and short. Each pet gets its
 * own pitch from its id, and every call varies a touch, so a meow never sounds pasted. Muted with one switch
 * on the pet page.
 */
object PetVoice {
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
                val pcm = PetSynth.make(kind, pitch * (0.97f + Random.nextFloat() * .06f)) ?: return@execute
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(PetSynth.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setVolume(.6f)
                track.play()
                Thread.sleep(pcm.size * 1000L / PetSynth.RATE + 60)
                track.release()
            } catch (_: Throwable) {
            } finally { playing-- }
        }
    }

    /** A pitch for this pet, the same every time. Kept in a narrow, low-ish band so every voice stays soft. */
    fun pitchOf(id: String) = .88f + ((id.hashCode() and 0xFFFF) / 65535f) * .22f
}
