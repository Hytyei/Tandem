package app.tandem.ui.pets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.data.CosmosResponse
import app.tandem.data.Pet
import app.tandem.data.PetCatalog
import app.tandem.ui.components.*
import app.tandem.ui.screens.EaseOutStrong
import app.tandem.ui.theme.LocalNight
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Ivory = Color(0xFFF6F2FF)
private val BubbleInk = Color(0xFF5B342E)
private val BubbleFill = Color(0xFFFFF7EE)
private val BubbleLine = Color(0xFF9E5A4B)

fun Pet.look() = petLook(species, breed, coat).copy(grow = growth.toFloat())

/** A comic speech bubble, its tail pointing down at the pet. It pops in from the tail and fades out quicker. */
@Composable
fun PetBubble(text: String?, modifier: Modifier = Modifier, small: Boolean = false) {
    var shown by remember { mutableStateOf(text) }
    if (text != null) shown = text
    AnimatedVisibility(
        visible = text != null, modifier = modifier,
        enter = fadeIn(tween(180, easing = EaseOutStrong)) + scaleIn(tween(220, easing = EaseOutStrong), initialScale = .92f, transformOrigin = TransformOrigin(.5f, 1f)),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = .96f, transformOrigin = TransformOrigin(.5f, 1f)),
    ) {
        val tail = if (small) 7.dp else 10.dp
        Box(
            Modifier.graphicsLayer { rotationZ = -1.2f }.padding(bottom = tail)
                .drawBehind {
                    val r = (if (small) 14.dp else 18.dp).toPx(); val lw = 2.dp.toPx(); val tw = tail.toPx()
                    val path = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(r)))
                    }
                    val tip = Path().apply {
                        moveTo(size.width * .44f, size.height - lw); lineTo(size.width * .40f, size.height + tw); lineTo(size.width * .56f, size.height - lw); close()
                    }
                    drawPath(path, BubbleFill); drawPath(tip, BubbleFill)
                    drawPath(path, BubbleLine, style = Stroke(lw))
                    // the tail's two edges, so the bubble's outline stays open where the tail joins
                    drawLine(BubbleFill, Offset(size.width * .44f + lw, size.height - lw / 2), Offset(size.width * .56f - lw, size.height - lw / 2), lw * 1.6f)
                    drawLine(BubbleLine, Offset(size.width * .44f, size.height - lw / 2), Offset(size.width * .40f, size.height + tw), lw, androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(BubbleLine, Offset(size.width * .56f, size.height - lw / 2), Offset(size.width * .40f, size.height + tw), lw, androidx.compose.ui.graphics.StrokeCap.Round)
                }
                .padding(horizontal = if (small) 12.dp else 16.dp, vertical = if (small) 8.dp else 11.dp),
        ) {
            Text(shown ?: "", style = (if (small) Type.footnote else Type.callout).copy(fontWeight = FontWeight.Medium, lineHeight = if (small) 17.sp else 20.sp), color = BubbleInk, textAlign = TextAlign.Center)
        }
    }
}

/** The warm pool of light the pet sits in. */
fun Modifier.petLight(strength: Float = 1f) = drawBehind {
    val ctr = Offset(size.width / 2f, size.height * .84f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE2C6).copy(alpha = .22f * strength), Color(0xFFFF9FB0).copy(alpha = .08f * strength), Color.Transparent), center = ctr, radius = size.width * .55f), size.width * .55f, ctr)
    drawOval(Brush.radialGradient(listOf(Color(0xFFFFF0DF).copy(alpha = .16f * strength), Color.Transparent), center = ctr, radius = size.width * .3f), Offset(ctr.x - size.width * .3f, ctr.y - size.width * .05f), androidx.compose.ui.geometry.Size(size.width * .6f, size.width * .1f))
}

@Composable
fun PetsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = T.colors
    val ctx = LocalContext.current
    val s by vm.state.collectAsState()
    val data = s ?: return
    val me = data.me
    val partner = data.couple.partner
    val myName = me.displayName.substringBefore(' ')
    val pName = partner?.displayName?.substringBefore(' ') ?: "your partner"
    LaunchedEffect(Unit) { vm.loadCosmos() }
    val res = vm.cosmos
    var sel by rememberSaveable { mutableIntStateOf(0) }
    var adopting by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var releasing by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(PetVoice.muted(ctx)) }
    LaunchedEffect(Unit) { PetVoice.preload(ctx) }
    val night = LocalNight.current > .9f

    Box(Modifier.fillMaxSize().background(c.background)) { DreamBackground {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your pets", style = Type.footnote, color = c.secondary)
                    Text(res?.cosmos?.pets?.getOrNull(sel)?.name ?: if (res?.cosmos?.pets?.isEmpty() == true) "A new friend" else "…", style = Type.title, color = c.label, maxLines = 1)
                }
                RoundIcon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, if (muted) "Turn voices on" else "Mute voices") { muted = !muted; PetVoice.setMuted(ctx, muted) }
            }
            if (res == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }; return@Column }
            val pets = res.cosmos.pets
            if (pets.isEmpty()) { NoPets(res) { adopting = true }; return@Column }
            if (sel >= pets.size) sel = pets.size - 1
            val pet = pets[sel]
            PetPage(vm, res, pet, pets, sel, { sel = it }, myName, pName, me.id, partner?.id ?: "", night, muted, onAdopt = { adopting = true }, onRename = { renaming = true }, onRelease = { releasing = true })
        }
    } }

    if (adopting && res != null) AdoptSheet(res, onDismiss = { adopting = false }) { species, breed, coat, name ->
        vm.petAdopt(species, breed, coat, name) { ok -> if (ok) { adopting = false; sel = (vm.cosmos?.cosmos?.pets?.size ?: 1) - 1 } }
    }
    val cur = res?.cosmos?.pets?.getOrNull(sel)
    if (renaming && cur != null) NameDialog("Rename ${cur.name}", cur.name, onDismiss = { renaming = false }) { n -> vm.petRename(cur.id, n) { renaming = false } }
    if (releasing && cur != null) AlertDialog(
        onDismissRequest = { releasing = false }, containerColor = c.sheet,
        title = { Text("Let ${cur.name} go?") },
        text = { Text("${cur.name} will go to live with a lovely family who has a garden. You can't get them back, and the stardust you spent stays spent.", style = Type.callout, color = c.secondary) },
        confirmButton = { TextButton(onClick = { vm.petRelease(cur.id) { releasing = false; sel = 0 } }) { Text("Let go", color = c.danger) } },
        dismissButton = { TextButton(onClick = { releasing = false }) { Text("Keep", color = c.label) } },
    )
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = T.colors
    Box(Modifier.size(42.dp).clip(CircleShape).background(c.card).pressable(haptic = false, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = c.label, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun PetPage(
    vm: AppViewModel, res: CosmosResponse, pet: Pet, pets: List<Pet>, sel: Int, onSelect: (Int) -> Unit,
    myName: String, pName: String, meId: String, themId: String, night: Boolean, muted: Boolean,
    onAdopt: () -> Unit, onRename: () -> Unit, onRelease: () -> Unit,
) {
    val c = T.colors
    val ctx = LocalContext.current
    val look = remember(pet.species, pet.breed, pet.coat, pet.growth) { pet.look() }
    var reaction by remember(pet.id) { mutableStateOf<PetReaction?>(PetReaction("hello")) }
    var bubble by remember(pet.id) { mutableStateOf<String?>(null) }
    var pokes by remember(pet.id) { mutableStateOf(emptyList<Long>()) }
    var bubbleKey by remember { mutableLongStateOf(0L) }
    fun say(text: String) { bubble = text; bubbleKey = System.nanoTime() }
    val sibling = pets.firstOrNull { it.id != pet.id }?.name
    // the pet speaks up now and then, and every bubble goes away on its own
    LaunchedEffect(pet.id) {
        delay(900)
        say(PetLines.idle(pet, myName, pName, meId, themId, sibling))
        while (true) {
            delay(16_000L + Random.nextLong(12_000))
            say(PetLines.idle(pets.firstOrNull { it.id == pet.id } ?: pet, myName, pName, meId, themId, sibling))
        }
    }
    LaunchedEffect(bubbleKey) { if (bubble != null) { delay(2600L + (bubble?.length ?: 0) * 45L); bubble = null } }
    val voice: (String) -> Unit = { k -> if (!muted) PetVoice.play(ctx, k, PetVoice.pitchOf(pet.id)) }
    val store = res.me
    val now = System.currentTimeMillis()

    fun care(kind: String, have: Int, run: () -> Unit) {
        if (have <= 0) { reaction = PetReaction("nope"); say(PetLines.after("empty_$kind", pet, myName, pName)); return }
        reaction = PetReaction(when (kind) { "food" -> "feed"; "drink" -> "water"; else -> "cuddle" })
        say(PetLines.after(when (kind) { "food" -> "feed"; "drink" -> "water"; else -> "cuddle" }, pet, myName, pName))
        run()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
        // ---------- who's who, when there's more than one (or room for another)
        if (pets.size > 1 || pets.size < res.maxPets) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pets.forEachIndexed { i, p ->
                val on = i == sel
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White.copy(alpha = .16f) else c.card)
                        .border(1.dp, Color.White.copy(alpha = if (on) .22f else .07f), RoundedCornerShape(50))
                        .pressable(haptic = false) { onSelect(i) }.padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PetPortrait(p.look(), Modifier.size(30.dp), p.mood)
                    Spacer(Modifier.width(6.dp))
                    Text(p.name, style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = if (on) c.label else c.secondary)
                }
            }
            if (pets.size < res.maxPets) Row(
                Modifier.clip(RoundedCornerShape(50)).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(50))
                    .pressable(haptic = false, onClick = onAdopt).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = c.secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Adopt · ${res.adoptCost ?: 0} stardust", style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.secondary)
            }
        }

        // ---------- the pet, in its pool of light
        Box(Modifier.fillMaxWidth().height(340.dp).petLight(if (pet.mood == "sulking") .5f else 1f)) {
            PetStage(
                look, pet.mood, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(260.dp),
                seed = pet.id.hashCode(), reaction = reaction, night = night, voice = voice,
                onTap = {
                    // poke it seven times in a few seconds and it gives up its tickle spot
                    val t = System.currentTimeMillis()
                    pokes = pokes.filter { t - it < 4000 } + t
                    if (pokes.size >= 7) { pokes = emptyList(); reaction = PetReaction("cuddle"); say(PetLines.after("tickle", pet, myName, pName)) }
                    // best friends get a trick now and then
                    else if (app.tandem.data.Bond.level(pet.bondWith(meId)) >= 4 && kotlin.random.Random.nextFloat() < .4f) { reaction = PetReaction("trick"); say(PetLines.after("trick", pet, myName, pName)) }
                    else say(PetLines.after("poke", pet, myName, pName))
                },
            )
            PetBubble(bubble, Modifier.align(Alignment.TopCenter).padding(horizontal = 36.dp, vertical = 6.dp))
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${app.tandem.data.stageName(pet.species, pet.stage)} · ${PetCatalog.breedName(pet.breed)} · ${PetCatalog.moodLabel(pet.mood)}", style = Type.callout, color = if (pet.mood == "sulking" || pet.mood == "grumpy") Color(0xFFFFB3C2) else c.secondary)
            Text(PetLines.traits(pet).joinToString(" · ") { it.label }, style = Type.footnote, color = c.special, modifier = Modifier.padding(top = 2.dp))
            val fav = pet.favourite()
            Text(
                when { fav == null -> "Loves you both the same"; fav == meId -> "Prefers you, for now"; else -> "Prefers $pName, for now" },
                style = Type.footnote, color = c.tertiary, modifier = Modifier.padding(top = 2.dp),
            )
        }

        // ---------- looking after it, from your own storage
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CareButton(Modifier.weight(1f), Icons.Rounded.Restaurant, "Feed", "${store.food} food") { care("food", store.food) { vm.petFeed(pet.id) } }
            CareButton(Modifier.weight(1f), Icons.Rounded.WaterDrop, "Water", "${store.drink} drink") { care("drink", store.drink) { vm.petWater(pet.id) } }
            CareButton(Modifier.weight(1f), Icons.Rounded.Favorite, "Cuddle", "${store.love} love") { care("love", store.love) { vm.petCuddle(pet.id) } }
            val wait = ((pet.patReadyAt - now) / 60_000L).toInt() + 1
            CareButton(Modifier.weight(1f), Icons.Rounded.PanTool, "Pat", if (pet.patReadyAt <= now) "Free" else "In $wait min", dim = pet.patReadyAt > now) {
                if (pet.patReadyAt > System.currentTimeMillis()) { reaction = PetReaction("nope"); say(PetLines.after("soon", pet, myName, pName)) }
                else { reaction = PetReaction("pat"); say(PetLines.after("pat", pet, myName, pName)); vm.petPat(pet.id) }
            }
        }

        // ---------- how it's doing
        SoftCard(Modifier.padding(horizontal = 16.dp).padding(top = 14.dp).fillMaxWidth(), padding = 18.dp) {
            Need("Food", pet.hunger.toFloat())
            Spacer(Modifier.height(12.dp))
            Need("Water", pet.thirst.toFloat())
            Spacer(Modifier.height(12.dp))
            Need("Love", pet.love.toFloat())
            if (pets.size > 1) Text(
                "With ${pets.size} pets everyone gets hungry ${if (pets.size == 2) "35" else "70"}% faster",
                style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 14.dp),
            )
        }

        // ---------- growing up, and who it loves (in depth)
        GrowthCard(pet, Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth())
        BondCard(pet, meId, themId, pName, Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth())
        MomentsCard(pet, meId, myName, pName, Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth())

        // ---------- your storage
        SoftCard(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp).fillMaxWidth(), padding = 18.dp) {
            Text("Your storage", style = Type.footnote, color = c.secondary)
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                StoreCount(Icons.Rounded.Restaurant, store.food, "food")
                StoreCount(Icons.Rounded.WaterDrop, store.drink, "drink")
                StoreCount(Icons.Rounded.Favorite, store.love, "love")
            }
            Text("Moods and pictures fill your love, notes and games your food and drink. A tracked date fills both of yours", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 12.dp))
        }

        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onRename) { Text("Rename", color = c.secondary) }
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onRelease) { Text("Let ${pet.name} go", color = c.tertiary) }
        }
    }
}

@Composable
private fun CareButton(modifier: Modifier, icon: ImageVector, label: String, sub: String, dim: Boolean = false, onClick: () -> Unit) {
    val c = T.colors
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(20.dp))
            .pressable(onClick = onClick).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = if (dim) c.tertiary else Ivory, modifier = Modifier.size(22.dp))
        Text(label, style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = if (dim) c.secondary else c.label, modifier = Modifier.padding(top = 6.dp))
        Text(sub, style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace, fontSize = 11.sp), color = c.tertiary)
    }
}

@Composable
private fun Need(label: String, v: Float) {
    val c = T.colors
    val shown by animateFloatAsState(v.coerceIn(0f, 100f) / 100f, tween(500, easing = EaseOutStrong), label = "need")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.callout, color = c.label, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .10f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(RoundedCornerShape(3.dp)).background(if (v < 30f) c.accent else Ivory.copy(alpha = .85f)))
        }
        Text("${v.toInt()}", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.secondary, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun Bond(name: String, v: Float, color: Color) {
    val c = T.colors
    val shown by animateFloatAsState(v.coerceIn(0f, 100f) / 100f, tween(500, easing = EaseOutStrong), label = "bond")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = Type.callout, color = c.label, maxLines = 1, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .10f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(RoundedCornerShape(3.dp)).background(color))
        }
        Text("${v.toInt()}", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.secondary, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun StoreCount(icon: ImageVector, n: Int, label: String) {
    val c = T.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.secondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("$n", style = Type.headline.copy(fontFamily = FontFamily.Monospace), color = c.label)
        Spacer(Modifier.width(4.dp))
        Text(label, style = Type.footnote, color = c.secondary)
    }
}

// ------------------------------------------------------------------ no pets yet

@Composable
private fun NoPets(res: CosmosResponse, onAdopt: () -> Unit) {
    val c = T.colors
    val showcase = remember { listOf(petLook("cat", "ginger", 0), petLook("dog", "shiba", 0), petLook("rabbit", "hollandlop", 0), petLook("hamster", "syrian", 0), petLook("parrot", "cockatiel", 0)) }
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(5200); i = (i + 1) % showcase.size } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(330.dp).petLight()) {
            androidx.compose.animation.Crossfade(i, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).size(250.dp), animationSpec = tween(420), label = "showcase") { k ->
                PetStage(showcase[k], "happy", Modifier.fillMaxSize(), seed = k * 31)
            }
        }
        Text("Adopt a pet together", style = Type.title, color = c.label, modifier = Modifier.padding(top = 8.dp))
        Text(
            "A cat, a dog, a hamster, a rabbit or a parrot to look after, the two of you. Everything you do in Tandem fills your storage with food, drink and love for it",
            style = Type.callout, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Choose your pet", onClick = onAdopt, color = Ivory, modifier = Modifier.padding(horizontal = 24.dp))
        Text("Your first pet is free", style = Type.footnote, color = c.tertiary, modifier = Modifier.padding(top = 10.dp))
    }
}

// ------------------------------------------------------------------ adopting

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AdoptSheet(res: CosmosResponse, onDismiss: () -> Unit, onAdopt: (String, String, Int, String) -> Unit) {
    val c = T.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sp by remember { mutableIntStateOf(0) }
    val species = PetCatalog.species[sp]
    val breeds = PetCatalog.breeds[species].orEmpty()
    var breed by remember(species) { mutableStateOf(breeds.first()) }
    var coat by remember(breed) { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(PetCatalog.suggestedNames.random()) }
    var busy by remember { mutableStateOf(false) }
    val cost = res.adoptCost ?: 0
    val afford = res.cosmos.stardust >= cost
    val look = remember(species, breed, coat) { petLook(species, breed, coat) }
    // the sheet closes on success; after a failure (a toast says why) the button comes back
    LaunchedEffect(busy) { if (busy) { delay(4000); busy = false } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("Adopt a pet", style = Type.title, color = c.label)
            Text(if (cost == 0) "Your first pet is free" else "$cost stardust · you have ${res.cosmos.stardust}", style = Type.footnote, color = c.secondary)
            Box(Modifier.fillMaxWidth().height(210.dp).petLight()) {
                PetStage(look, "happy", Modifier.align(Alignment.BottomCenter).size(190.dp), seed = breed.hashCode() + coat)
            }
            Segmented(PetCatalog.species.map { PetCatalog.speciesName(it) }, sp, { sp = it })
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                breeds.forEach { b ->
                    val on = b == breed
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(if (on) Color.White.copy(alpha = .16f) else c.card)
                            .border(1.dp, Color.White.copy(alpha = if (on) .24f else .07f), RoundedCornerShape(50))
                            .pressable(haptic = false) { breed = b }.padding(start = 4.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PetPortrait(petLook(species, b, 0), Modifier.size(32.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(PetCatalog.breedName(b), style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = if (on) c.label else c.secondary)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Coat", style = Type.footnote, color = c.secondary)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                petCoats(species, breed).forEachIndexed { i, k ->
                    val on = i == coat
                    Canvas(Modifier.size(38.dp).clip(CircleShape).border(if (on) 2.dp else 1.dp, if (on) Ivory else Color.White.copy(alpha = .15f), CircleShape).pressable(haptic = false) { coat = i }) {
                        val main = if (species == "parrot") k.fur else k.fur
                        val second = when { species == "parrot" -> k.light; k.mark.luminanceOf() < k.fur.luminanceOf() - .05f || k.mark.luminanceOf() > k.fur.luminanceOf() + .05f -> k.mark; else -> k.light }
                        drawCircle(main, size.minDimension / 2f - 3.dp.toPx())
                        drawArc(second, -45f, 180f, true, topLeft = Offset(3.dp.toPx(), 3.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    name, { if (it.length <= 24) name = it }, placeholder = { Text("Name", color = c.tertiary) }, singleLine = true,
                    shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent, focusedContainerColor = c.card, unfocusedContainerColor = c.card),
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(c.card).pressable(haptic = false) { name = PetCatalog.suggestedNames.filter { it != name }.random() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Casino, "Suggest a name", tint = c.secondary, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                if (afford) "Adopt ${name.trim().ifBlank { "them" }}" else "Needs ${cost - res.cosmos.stardust} more stardust",
                onClick = { busy = true; onAdopt(species, breed, coat, name.trim()) },
                enabled = afford && name.isNotBlank(), loading = busy, color = Ivory,
            )
            if (res.cosmos.pets.isNotEmpty()) Text("Every extra pet makes all of them get hungry, thirsty and lonely faster", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 10.dp))
        }
    }
}


private fun Color.luminanceOf() = .2126f * red + .7152f * green + .0722f * blue

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val c = T.colors
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.sheet,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { if (it.length <= 24) text = it }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent),
            )
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onDone(text.trim()) }) { Text("Save", color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.secondary) } },
    )
}

/** A pet's head-and-shoulders for small places (Today, chips), at [size]. */
@Composable
fun PetThumb(pet: Pet, size: Dp, modifier: Modifier = Modifier) = PetPortrait(pet.look(), modifier.size(size), pet.mood)
