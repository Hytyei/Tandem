package app.tandem.ui.pets

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.tandem.data.CosmosSummary
import app.tandem.data.PetCatalog
import app.tandem.ui.components.pressable
import app.tandem.ui.galaxy.GalaxyBitmap
import app.tandem.ui.galaxy.GalaxyGen
import app.tandem.ui.theme.LocalNight
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.random.Random

/**
 * Today's little world: your galaxy glowing in the sky, your pets on a soft hill under it in a pool of
 * light. One scene instead of two boxes. The sky opens the galaxy; everything else opens the pets.
 */
@Composable
fun WorldCard(
    cosmos: CosmosSummary?, myName: String, partnerName: String, meId: String, themId: String,
    openPets: () -> Unit, openGalaxy: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = T.colors
    val shape = RoundedCornerShape(28.dp)
    val pets = cosmos?.pets.orEmpty()
    val night = LocalNight.current > .9f
    val reduced = rememberReducedMotion()

    Column(modifier.clip(shape).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), shape).pressable(haptic = false, onClick = openPets)) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            // ---------- the night above: deeper at the top, melting into the card's glass
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF07051C).copy(alpha = .55f), Color(0xFF0B0828).copy(alpha = .25f), Color.Transparent), endY = size.height * .72f))
                val far = Random(5)
                repeat(36) { drawCircle(Color.White.copy(alpha = .08f + far.nextFloat() * .18f), (.5f + far.nextFloat()).dp.toPx(), Offset(far.nextFloat() * size.width, far.nextFloat() * size.height * .6f)) }
            }
            if (cosmos != null) Sky(cosmos.seed, cosmos.lifetime, reduced, Modifier.fillMaxWidth().height(210.dp).pressable(haptic = false, onClick = openGalaxy))

            // ---------- the hill they sit on, lit warm from below
            Canvas(Modifier.fillMaxSize()) {
                val top = size.height * .70f
                val hill = Path().apply {
                    moveTo(0f, size.height); lineTo(0f, top + 30.dp.toPx())
                    quadraticTo(size.width / 2f, top - 18.dp.toPx(), size.width, top + 30.dp.toPx())
                    lineTo(size.width, size.height); close()
                }
                drawPath(hill, Brush.verticalGradient(listOf(Color(0xFFB79BFF).copy(alpha = .16f), Color(0xFF2A1F5E).copy(alpha = .10f)), startY = top - 18.dp.toPx(), endY = size.height))
                val pool = Offset(size.width / 2f, top + 14.dp.toPx())
                drawOval(Brush.radialGradient(listOf(Color(0xFFFFE2C6).copy(alpha = .26f), Color(0xFFFF9FB0).copy(alpha = .06f), Color.Transparent), center = pool, radius = size.width * .4f),
                    Offset(pool.x - size.width * .4f, pool.y - size.width * .1f), Size(size.width * .8f, size.width * .2f))
            }

            // ---------- the pets (or an invitation)
            if (pets.isEmpty()) {
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PetStage(petLook("cat", "ginger", 0), "happy", Modifier.size(118.dp), sketch = false, night = night)
                }
            } else {
                var line by remember { mutableStateOf<String?>(null) }
                var sleeping by remember { mutableIntStateOf(0) }
                val awake = sleeping < pets.size
                LaunchedEffect(pets.map { it.id }, awake) {
                    line = null
                    if (!awake) return@LaunchedEffect
                    delay(2500)
                    while (true) {
                        val p = pets[Random.nextInt(pets.size)]
                        line = PetLines.idle(p, myName, partnerName, meId, themId, pets.firstOrNull { it.id != p.id }?.name)
                        delay(5200); line = null
                        delay(15_000L + Random.nextLong(10_000))
                    }
                }
                val size = when (pets.size) { 1 -> 140.dp; 2 -> 118.dp; else -> 100.dp }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy((-10).dp), verticalAlignment = Alignment.Bottom) {
                    pets.forEach { p ->
                        PetStage(p.look(), p.mood, Modifier.size(size), seed = p.id.hashCode(), night = night, sketch = false,
                            onAsleep = { a -> sleeping = (sleeping + if (a) 1 else -1).coerceIn(0, pets.size) })
                    }
                }
                PetBubble(line, Modifier.align(Alignment.BottomCenter).padding(bottom = size + 4.dp).padding(horizontal = 28.dp), small = true)
            }
        }

        // ---------- names and how they're doing; the galaxy as a small pill
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 2.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (pets.isEmpty()) {
                    Text("Adopt a pet", style = Type.headline, color = c.label)
                    Text("The first one is free", style = Type.footnote, color = c.secondary)
                } else {
                    Text(pets.joinToString(" & ") { it.name }, style = Type.headline, color = c.label, maxLines = 1)
                    val needy = pets.minByOrNull { minOf(it.hunger, it.thirst, it.love) }!!
                    val low = minOf(needy.hunger, needy.thirst, needy.love)
                    val asleepNow = night
                    Text(
                        when {
                            needy.mood == "sulking" -> "${needy.name} is sulking"
                            low < 30 -> "${needy.name} is " + when (low) { needy.hunger -> "hungry"; needy.thirst -> "thirsty"; else -> "missing you" }
                            asleepNow -> if (pets.size == 1) "Asleep under the stars" else "Asleep under the stars together"
                            pets.size == 1 -> "${PetCatalog.breedName(needy.breed)} · ${PetCatalog.moodLabel(needy.mood).lowercase()}"
                            else -> "All doing well"
                        },
                        style = Type.footnote, color = if (low < 30 || needy.mood == "sulking") Color(0xFFFFB3C2) else c.secondary, maxLines = 1,
                    )
                }
            }
            if (cosmos != null) Row(
                Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .08f)).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(50))
                    .pressable(haptic = false, onClick = openGalaxy).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = c.special, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(String.format(Locale.US, "%,d stars", GalaxyGen.starsFor(GalaxyGen.growth(cosmos.lifetime))), style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.label)
            }
        }
    }
}

/** The galaxy, painted once, faded out at every edge so it hangs in the sky; it breathes very slowly. */
@Composable
private fun Sky(seed: Long, lifetime: Int, reduced: Boolean, modifier: Modifier) {
    var bmp by remember(seed, lifetime) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(seed, lifetime) {
        bmp = withContext(Dispatchers.Default) { runCatching { GalaxyBitmap.render(seed, lifetime, 760, 420, yawDeg = 32f, zoom = .78f) }.getOrNull() }
    }
    val tw = rememberInfiniteTransition(label = "sky")
    val breathe by tw.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "breathe")
    Box(modifier) {
        bmp?.let { b ->
            Image(
                b.asImageBitmap(), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        val s = if (reduced) 1f else 1f + .035f * breathe
                        scaleX = s; scaleY = s
                        rotationZ = if (reduced) 0f else -2f + 4f * breathe
                    }
                    .drawWithContent {
                        drawContent()
                        // fade to nothing before any edge, so there is never a box around it
                        drawRect(Brush.radialGradient(listOf(Color.Black, Color.Black, Color.Transparent), center = Offset(size.width / 2f, size.height / 2f), radius = size.width * .5f), blendMode = BlendMode.DstIn)
                    },
            )
        }
    }
}
