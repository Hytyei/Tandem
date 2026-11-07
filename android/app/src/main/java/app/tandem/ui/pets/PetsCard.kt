package app.tandem.ui.pets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.tandem.data.CosmosSummary
import app.tandem.data.PetCatalog
import app.tandem.ui.components.pressable
import app.tandem.ui.theme.LocalNight
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Today's pet card: your pets, alive, with a line now and then. Before you have one, an invitation. */
@Composable
fun PetsCard(summary: CosmosSummary?, myName: String, partnerName: String, meId: String, themId: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = T.colors
    val shape = RoundedCornerShape(24.dp)
    val pets = summary?.pets.orEmpty()
    val night = LocalNight.current > .9f
    Column(modifier.clip(shape).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), shape).pressable(haptic = false, onClick = onOpen)) {
        if (pets.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 128.dp, height = 116.dp).petLight()) { PetStage(petLook("cat", "ginger", 0), "happy", Modifier.align(Alignment.BottomCenter).size(108.dp), sketch = false) }
                Column(Modifier.weight(1f)) {
                    Text("Adopt a pet", style = Type.title, color = c.label)
                    Text("Look after it together with what you earn here. The first one is free", style = Type.footnote, color = c.secondary, maxLines = 3)
                }
            }
            return@Column
        }
        var line by remember { mutableStateOf<String?>(null) }
        var who by remember { mutableIntStateOf(0) }
        LaunchedEffect(pets.map { it.id }) {
            delay(2500)
            while (true) {
                who = Random.nextInt(pets.size)
                val p = pets[who]
                line = PetLines.idle(p, myName, partnerName, meId, themId, pets.firstOrNull { it.id != p.id }?.name)
                delay(5200)
                line = null
                delay(14_000L + Random.nextLong(10_000))
            }
        }
        Box(Modifier.fillMaxWidth().height(if (pets.size == 1) 150.dp else 140.dp).petLight(.8f)) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy((-6).dp), verticalAlignment = Alignment.Bottom) {
                pets.forEach { p -> PetStage(p.look(), p.mood, Modifier.size(if (pets.size == 1) 132.dp else 112.dp), seed = p.id.hashCode(), night = night, sketch = false) }
            }
            PetBubble(line, Modifier.align(Alignment.TopCenter).padding(horizontal = 20.dp, vertical = 6.dp), small = true)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 14.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (pets.size == 1) pets[0].name else pets.joinToString(" · ") { it.name }, style = Type.headline, color = c.label, maxLines = 1)
                val needy = pets.minByOrNull { minOf(it.hunger, it.thirst, it.love) }
                val low = needy?.let { minOf(it.hunger, it.thirst, it.love) } ?: 100.0
                Text(
                    when {
                        needy == null -> ""
                        needy.mood == "sulking" -> "${needy.name} is sulking. Some care would help"
                        low < 30 -> "${needy.name} is " + when (low) { needy.hunger -> "hungry"; needy.thirst -> "thirsty"; else -> "missing you" }
                        pets.size == 1 -> "${PetCatalog.breedName(needy.breed)} · ${PetCatalog.moodLabel(needy.mood)}"
                        else -> "All of them are doing fine"
                    },
                    style = Type.footnote, color = if (low < 30 || needy?.mood == "sulking") Color(0xFFFFB3C2) else c.secondary, maxLines = 1,
                )
            }
        }
    }
}
