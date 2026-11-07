package app.tandem.ui.pets

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Something that happened to the pet that it should react to. A new [id] starts a new reaction. */
data class PetReaction(val kind: String, val id: Long = System.nanoTime())

/** The pet's little life: who drives the rig, frame by frame. Kept outside composition so it survives recompositions. */
class PetLife(val species: String, seed: Int) {
    val pose = PetPose()
    private val rnd = Random(seed)
    private var act: String? = null
    private var actStart = 0f
    private var actDur = 0f
    private var nextAct = 2.5f + rnd.nextFloat() * 2f
    private var nextBlink = 1.5f
    private var blinkStart = -9f
    private var doubleBlink = false
    private var sq = 0f; private var sqV = 0f        // a spring for squash and stretch
    private var lastT = 0f
    private var idleSince = 0f
    var hearts = mutableListOf<Float>()               // start times of floating hearts
        private set
    var sparkle = 0f; private set
    var asleep = 0f; private set
    /** Called with a sound the pet makes ("meow", "boof", ...). */
    var onSound: (String) -> Unit = {}

    private fun actions(mood: String): List<Pair<String, Float>> {
        val happy = mood == "happy"; val low = mood == "grumpy" || mood == "sulking"
        val common = listOf("look" to 3f, "tilt" to 2f, "ear" to 2f, "yawn" to if (low) 1.5f else .6f)
        val own = when (species) {
            "cat" -> listOf("lick" to 3f, "meow" to 2f, "knead" to if (happy) 2f else .5f, "flick" to 2f, "slowblink" to if (low) .3f else 1.5f)
            "dog" -> listOf("wag" to if (low) .6f else 3f, "pant" to 2f, "bark" to 1.5f, "tilt" to 2f, "scratch" to 1f)
            "hamster" -> listOf("nibble" to 3f, "stuff" to 1.5f, "sniff" to 2f, "squeak" to 1.5f, "groom" to 1.2f)
            "rabbit" -> listOf("nose" to 3f, "hop" to if (low) .4f else 2f, "binky" to if (happy) .8f else 0f, "grunt" to 1f, "flop" to .8f)
            else -> listOf("bob" to 3f, "flap" to 1.6f, "chirp" to 2f, "tilt" to 2f, "fluff" to 1.2f)
        }
        return common + own
    }

    private fun duration(a: String) = when (a) {
        "look" -> 2.6f; "tilt" -> 1.8f; "ear" -> .5f; "yawn" -> 1.6f
        "lick" -> 3.2f; "meow" -> .9f; "knead" -> 2.8f; "flick" -> .9f; "slowblink" -> 1.5f
        "wag" -> 2.2f; "pant" -> 2.8f; "bark" -> .8f; "scratch" -> 1.4f
        "nibble" -> 2.8f; "stuff" -> 3.2f; "sniff" -> 1.6f; "squeak" -> .6f; "groom" -> 1.8f
        "nose" -> 1.8f; "hop" -> .75f; "binky" -> 1f; "grunt" -> .5f; "flop" -> 2.4f
        "bob" -> 2.2f; "flap" -> 1.3f; "chirp" -> .8f; "fluff" -> 1.2f
        // reactions
        "feed" -> 1.8f; "water" -> 1.8f; "cuddle" -> 2.2f; "pat" -> 1.4f; "poke" -> 1f; "nope" -> 1.1f; "hello" -> 1.6f
        else -> 1f
    }

    private fun soundFor(a: String): String? = when (a) {
        "meow" -> "meow"; "bark" -> "boof"; "squeak" -> "squeak"; "chirp" -> "chirp"; "grunt" -> "grunt"; "pant" -> "pant"
        "feed" -> "nom"; "water" -> "lap"
        "cuddle" -> when (species) { "cat" -> "purr"; "dog" -> "boof"; "hamster" -> "squeak"; "rabbit" -> "grunt"; else -> "chirp" }
        "pat", "poke", "hello" -> when (species) { "cat" -> "trill"; "dog" -> "boof"; "hamster" -> "squeak"; "rabbit" -> "grunt"; else -> "chirp" }
        "nope" -> when (species) { "cat" -> "meow"; "dog" -> "boof"; "hamster" -> "squeak"; "rabbit" -> "grunt"; else -> "chirp" }
        else -> null
    }

    fun react(kind: String, t: Float) {
        act = kind; actStart = t; actDur = duration(kind); idleSince = t
        nextAct = t + actDur + 2f + rnd.nextFloat() * 3f
        when (kind) {
            "cuddle" -> { sqV += 5f; hearts.add(t); hearts.add(t + .35f) }
            "pat" -> sqV += 3.5f
            "poke" -> sqV += 6f
            "feed", "water" -> sqV += 2.5f
            "nope" -> sqV -= 3f
        }
        soundFor(kind)?.let(onSound)
    }

    /** Advances the pet to time [t] (seconds) and sets the pose. */
    fun step(t: Float, mood: String, night: Boolean, reduced: Boolean) {
        val dt = (t - lastT).coerceIn(0f, .05f); lastT = t
        val p = pose
        val low = when (mood) { "sulking" -> 1f; "grumpy" -> .5f; else -> 0f }
        // dozing: at night, after a while of nothing happening
        val wantSleep = night && t - idleSince > 25f && act == null
        asleep = (asleep + (if (wantSleep) dt / 2.5f else -dt / .4f)).coerceIn(0f, 1f)

        p.reset()
        p.droop = low
        p.sleep = asleep
        val breathRate = if (asleep > .5f) 4.8f else 3.4f
        p.breathe = sin(t * 2f * PI.toFloat() / breathRate)
        if (!reduced) {
            p.lookX = sin(t * .31f) * .35f * (1f - asleep)
            p.lookY = sin(t * .23f + 1f) * .2f * (1f - asleep) + low * .4f
            p.tail = when (species) { "cat" -> sin(t * .9f) * .55f; "dog" -> sin(t * 1.7f) * (.35f - low * .3f); else -> 0f } * (1f - asleep * .8f)
            p.headDy = p.breathe * .5f + asleep * 2f
        }

        // blinking, sometimes twice
        if (t >= nextBlink) { blinkStart = t; doubleBlink = rnd.nextFloat() < .2f; nextBlink = t + 2.4f + rnd.nextFloat() * 3.4f }
        val bt = t - blinkStart
        p.blink = max(blinkPulse(bt), if (doubleBlink) blinkPulse(bt - .24f) else 0f)
        if (low > .9f) p.blink = max(p.blink, .25f)

        // the next little thing to do
        if (!reduced && asleep < .1f && act == null && t >= nextAct) {
            val opts = actions(mood).filter { it.second > 0f }
            var r = rnd.nextFloat() * opts.sumOf { it.second.toDouble() }.toFloat()
            val pick = opts.firstOrNull { r -= it.second; r <= 0f }?.first ?: opts.first().first
            act = pick; actStart = t; actDur = duration(pick)
            soundFor(pick)?.let(onSound)
            nextAct = t + actDur + (if (low > 0f) 5f else 3f) + rnd.nextFloat() * 5f
        }
        act?.let { a ->
            val x = (t - actStart) / actDur
            if (x >= 1f) act = null else perform(a, x, t - actStart, p)
        }

        // squash and stretch on a spring (stiff, a little bouncy)
        val k = 160f; val d = 11f
        sqV += (-k * sq - d * sqV) * dt; sq += sqV * dt
        p.squash = (p.squash + sq * .12f).coerceIn(-1f, 1f)
        if (reduced) { p.squash = 0f; p.hop = 0f }

        sparkle += ((if (mood == "happy" && asleep < .2f) .55f else 0f) - sparkle) * min(1f, dt * 2f)
        hearts.removeAll { t - it > 1.8f }
    }

    private fun blinkPulse(x: Float): Float = if (x < 0f || x > .17f) 0f else sin(x / .17f * PI.toFloat())

    /** One micro-action at progress [x] (0..1), [s] seconds in. */
    private fun perform(a: String, x: Float, s: Float, p: PetPose) {
        val env = bell(x)                       // eases in and out
        val tau = 2f * PI.toFloat()
        when (a) {
            "look" -> { p.lookX = sin(x * tau) * .9f; p.tilt += sin(x * tau) * 5f * env }
            "tilt" -> { p.tilt += 13f * env; p.earL = env * .6f; p.lookY = -.3f * env }
            "ear" -> { val f = sin(x * PI.toFloat()); p.earR = f; p.earL = f * .3f }
            "yawn" -> { p.mouth = bell(x, .2f, .7f); p.blink = max(p.blink, bell(x, .1f, .85f)); p.headDy -= 2f * env; p.squash -= .4f * env }
            // cats
            "lick" -> {
                p.paw = bell(x, .14f, .14f)
                p.tongue = if (x in .25f..0.8f) abs(sin(s * 9f)) else 0f
                p.happy = if (x in .22f..0.84f) 1f else 0f
                p.tilt -= 7f * p.paw; p.headDy += 2f * p.paw
            }
            "meow" -> { p.mouth = bell(x, .25f, .45f); p.headDy -= 2.5f * env; p.tilt += 4f * env; p.earL = -.3f * env; p.earR = -.3f * env }
            "knead" -> { p.knead = sin(s * 6f) * env; p.happy = if (env > .6f) 1f else 0f; p.squash += .25f * env }
            "flick" -> p.tail += sin(x * tau * 2f) * .9f
            "slowblink" -> { p.blink = max(p.blink, bell(x, .35f, .35f)); p.blush = .6f * env }
            // dogs
            "wag" -> { p.tail = sin(s * 24f) * env; p.happy = if (env > .5f) 1f else 0f; p.tilt += sin(s * 6f) * 3f * env; p.squash += sin(s * 12f) * .15f * env }
            "pant" -> { p.tongue = env; p.mouth = .5f * env; p.headDy += sin(s * 14f) * .9f * env; p.breathe = sin(s * 14f) }
            "bark" -> { p.mouth = max(bell(x * 2f), bell(x * 2f - 1f)); p.squash += .5f * p.mouth; p.earL = .4f * env; p.earR = .4f * env }
            "scratch" -> { p.tilt += 10f * env; p.happy = if (env > .5f) 1f else 0f; p.earL = sin(s * 18f) * env; p.knead = sin(s * 18f) * env * .6f }
            // hamsters
            "nibble" -> { p.mouth = env; p.nose = (sin(s * 30f) * .5f + .5f) * env; p.lookY = .5f * env }
            "stuff" -> { p.cheeks = if (x < .6f) easeIO(x / .45f) else 1f - easeIO((x - .6f) / .4f); p.mouth = if (x < .45f) abs(sin(s * 16f)) * .6f else 0f; p.happy = if (x in .5f..0.8f) 1f else 0f }
            "sniff" -> { p.nose = (sin(s * 34f) * .5f + .5f) * env; p.lookY = -.6f * env; p.headDy -= 2.5f * env; p.squash -= .5f * env }
            "squeak" -> { p.mouth = bell(x, .2f, .5f); p.squash -= .4f * env; p.earL = env; p.earR = env }
            "groom" -> { p.happy = if (env > .4f) 1f else 0f; p.mouth = 0f; p.tilt += sin(s * 10f) * 4f * env; p.nose = .5f * env }
            // rabbits
            "nose" -> p.nose = (sin(s * 28f) * .5f + .5f) * env
            "hop", "binky" -> {
                val air = if (x in .18f..0.82f) sin((x - .18f) / .64f * PI.toFloat()) else 0f
                p.hop = air * (if (a == "binky") 1.25f else .8f)
                p.squash += if (x < .18f) .9f * sin(x / .18f * PI.toFloat()) else if (x > .82f) .8f * sin((x - .82f) / .18f * PI.toFloat()) else -.5f * air
                if (a == "binky") { p.tilt += sin(x * tau) * 14f; p.earL = sin(s * 20f) * air; p.earR = -sin(s * 20f) * air; p.happy = if (air > .2f) 1f else 0f }
            }
            "grunt" -> { p.squash += .6f * env; p.earL = -.6f * env; p.earR = -.6f * env }
            "flop" -> { p.squash += .7f * env; p.happy = if (env > .5f) 1f else 0f; p.tilt += 10f * env; p.headDy += 2f * env }
            // parrots
            "bob" -> { p.headDy += sin(s * 16f) * 3.2f * env; p.squash += sin(s * 16f) * .25f * env }
            "flap" -> { p.wing = env; p.hop = .2f * env; p.squash -= .3f * env }
            "chirp" -> { p.mouth = max(bell(x * 2f), bell(x * 2f - 1f)); p.tilt += 8f * env; p.headDy -= 1.5f * env }
            "fluff" -> { p.squash += sin(x * tau) * .8f; p.happy = if (x in .2f..0.7f) 1f else 0f }
            // reactions to the two of you
            "feed" -> { p.mouth = if (x < .8f) abs(sin(s * 11f)) * .7f else 0f; p.happy = if (x < .85f) 1f else 0f; p.lookY = .5f * env; p.cheeks = if (species == "hamster") env else 0f }
            "water" -> { p.tongue = if (species == "cat" || species == "dog") abs(sin(s * 12f)) * env else 0f; p.mouth = if (species == "cat" || species == "dog") 0f else abs(sin(s * 12f)) * .5f * env; p.lookY = .6f * env; p.headDy += 2.5f * env; p.happy = .0f }
            "cuddle" -> { p.happy = 1f; p.blush = env; p.tilt += 8f * env; p.earL = -.5f * env; p.earR = -.5f * env; if (species == "cat") p.knead = sin(s * 6f) * env; if (species == "dog") p.tail = sin(s * 24f) * env }
            "pat" -> { p.happy = if (env > .3f) 1f else 0f; p.earL = -.8f * env; p.earR = -.8f * env; p.headDy += 2.5f * env; p.blush = .5f * env }
            "poke" -> { p.lookX = 0f; p.lookY = -.2f; p.earL = env; p.earR = env; p.mouth = bell(x, .15f, .5f) * .6f }
            "nope" -> { p.lookX = -.9f * env; p.tilt -= 10f * env; p.earL = -.7f * env; p.earR = -.7f * env }
            "hello" -> { p.happy = if (x in .2f..0.8f) 1f else 0f; p.hop = if (species == "rabbit" || species == "parrot") .4f * sin(x * PI.toFloat()) else 0f; p.mouth = bell(x, .3f, .4f) * .6f; p.tail = sin(s * 20f) * env }
        }
    }

    /** 0 → 1 → 0 with eased edges: [inW] and [outW] are the fractions spent easing in and out. */
    private fun bell(x: Float, inW: Float = .3f, outW: Float = .3f): Float = when {
        x <= 0f || x >= 1f -> 0f
        x < inW -> easeIO(x / inW)
        x > 1f - outW -> easeIO((1f - x) / outW)
        else -> 1f
    }
}

/** Animations are off on this phone (Settings › Accessibility › Remove animations). */
@Composable
fun rememberReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
}

/**
 * A pet, alive: breathing, blinking, doing its own little things, reacting when you look after it.
 * [reaction] plays once each time it changes. [onTap] fires when the pet is tapped (it reacts on its own too).
 */
@Composable
fun PetStage(
    look: PetLook,
    mood: String,
    modifier: Modifier = Modifier,
    seed: Int = look.breed.hashCode(),
    reaction: PetReaction? = null,
    night: Boolean = false,
    sketch: Boolean = true,
    voice: ((String) -> Unit)? = null,
    onTap: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val life = remember(look.species, seed) { PetLife(look.species, seed) }
    life.onSound = voice ?: {}
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(life) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now -> t = (now - start) / 1e9f; life.step(t, mood, night, reduced) }
    }
    LaunchedEffect(reaction?.id) { reaction?.let { life.react(it.kind, t) } }
    val tap = if (onTap != null) Modifier.pointerInput(life) { detectTapGestures { life.react("poke", t); onTap() } } else Modifier
    Canvas(modifier.then(tap)) {
        @Suppress("UNUSED_EXPRESSION") t   // read the clock so every frame redraws
        drawPet(look, life.pose, sketch = sketch, t = t)
        petSparkles(t, life.sparkle * (1f - life.pose.droop))
        petZzz(t, life.asleep, Color(0xFFF6F2FF))
        val u = size.minDimension / 100f
        for (h0 in life.hearts) {
            val k = ((t - h0) / 1.8f)
            if (k < 0f || k > 1f) continue
            val x = size.width / 2f + (if (h0 * 7f % 2f > 1f) 14f else -12f) * u + sin(k * 6f) * 3f * u
            petHeart(x, size.height * .38f - k * 30f * u, 4.2f * u, sin(k * PI.toFloat()))
        }
    }
}

/** A pet holding still (for pickers and thumbnails), with an occasional blink if [blink] is on. */
@Composable
fun PetPortrait(look: PetLook, modifier: Modifier = Modifier, mood: String = "happy", sketch: Boolean = false) {
    val pose = remember { PetPose() }
    Canvas(modifier) {
        pose.reset(); pose.droop = if (mood == "sulking") 1f else if (mood == "grumpy") .5f else 0f
        drawPet(look, pose, sketch = sketch)
    }
}

internal fun Offset.dist(o: Offset) = kotlin.math.hypot(x - o.x, y - o.y)
