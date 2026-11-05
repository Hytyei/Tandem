package app.tandem.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type

private val Ivory = Color(0xFFF6F2FF)
private val Gold = Color(0xFFFFD58A)

/** How the painter should draw this planet, from the server's state. [me] decides which sun is on the left. */
internal fun PlanetState.look(me: String?): PlanetLook {
    val them = members.firstOrNull { it != me }
    return PlanetLook(
        ocean = biome("ocean"), forest = biome("forest"), crystal = biome("crystal"), aurora = biome("aurora"),
        ice = ice.toFloat(), atmosphere = atmosphere.toFloat(), life = life.toFloat(),
        sunMe = sun(me), sunThem = sun(them),
        moons = aliveMoons.size, lostMoons = moons.size - aliveMoons.size,
        buildings = buildings.count { it.ruined == null }, ruins = buildings.count { it.ruined != null },
        species = species.values.count { it.state == "alive" }, stage = stage,
        seed = planetSeed(created),
    )
}

/** The same continents for the same planet, everywhere it is drawn. */
internal fun planetSeed(created: String) = (created.hashCode() and 0x7FFFFFFF) % 997 + 1

/** The planet, slowly turning. */
@Composable
internal fun PlanetView(look: PlanetLook, modifier: Modifier, showSuns: Boolean = true) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val start = withFrameMillis { it }; while (true) { val now = withFrameMillis { it }; t = (now - start) / 1000f } }
    // the surface map takes a moment to make, so it is made off the main thread; a plain sea shows until then
    LaunchedEffect(look) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { runCatching { PlanetSurface.prepare(look) } } }
    // a drawing problem must never take the whole app down: skip the frame instead
    Canvas(modifier) { drawIntoCanvas { runCatching { PlanetPainter.draw(it.nativeCanvas, size.width, size.height, look, t, showSuns) } } }
}

/** One plain sentence about how the world is doing, and what would help. */
private fun stageLine(p: PlanetState, partner: String): String = when (p.stage) {
    "sleeping" -> "Asleep until ${p.hibernate?.until ?: "you wake it"}. Nothing grows and nothing fades"
    "drift" -> "${p.quiet} quiet days. It's drifting from its suns, and each week of silence loses something for good"
    "winter" -> "${p.quiet} quiet days. Winter has come. Share your moods and send each other a spark"
    "dimming" -> if (p.quiet >= 3) "${p.quiet} quiet days. The light is fading" else "It dimmed while you were away. It needs a spark from each of you to rekindle"
    else -> when {
        p.conjStreak >= 3 -> "${p.conjStreak} days in a row you both shared. It's glowing"
        p.ice > 45 -> "It's cold. Brighter suns would melt the ice"
        else -> "Both suns are keeping it warm"
    }
}

@Composable
fun PlanetScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val me = data.me
    val partner = data.couple.partner
    val pName = partner?.displayName?.substringBefore(' ') ?: "your partner"
    LaunchedEffect(Unit) { vm.loadPlanet() }
    val res = vm.planet
    var naming by remember { mutableStateOf<String?>(null) }      // "" = the planet, else a moon id
    var sparking by remember { mutableStateOf(false) }
    var sleeping by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.background)) { DreamBackground {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(c.card).pressable(haptic = false, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.label, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).pressable(haptic = false) { if (res != null) naming = "" }) {
                Text("Your planet", style = Type.footnote, color = c.secondary)
                Text(res?.planet?.name ?: "…", style = Type.title, color = c.label, maxLines = 1)
            }
            if (res != null) Text("${res.planet.stardust} stardust", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = Gold)
        }
        if (res == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
            return@Column
        }
        val p = res.planet
        val today = res.today
        val partnerId = partner?.id
        val theirSpark = partnerId?.let { p.sparks[it] }
        val mySpark = p.sparks[me.id]

        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 40.dp)) {
            // ---------- the world, and the two suns that keep it
            item {
                Box(Modifier.fillMaxWidth().height(330.dp)) {
                    PlanetView(p.look(me.id), Modifier.fillMaxSize())
                    SunLabel(me, "You", p.sun(me.id), today.shared[me.id] == true, c.me, Modifier.align(Alignment.BottomStart).padding(start = 20.dp))
                    SunLabel(partner, pName, p.sun(partnerId), today.shared[partnerId] == true, c.partner, Modifier.align(Alignment.BottomEnd).padding(end = 20.dp), end = true)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(planetStageLabel(p.stage, p.quiet), style = Type.headline, color = if (p.stage == "thriving") c.label else Color(0xFFFFB3C2))
                    Text(stageLine(p, pName), style = Type.callout, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                }
            }

            // ---------- a spark from your partner
            if (theirSpark != null) item {
                SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), color = c.partner.copy(alpha = .12f)) {
                    Text("$pName sent a spark", style = Type.footnote, color = c.secondary)
                    Text("“${theirSpark.text}”", style = Type.title.copy(fontSize = 19.sp), color = c.label, modifier = Modifier.padding(top = 4.dp))
                    if (p.needsRekindle && mySpark == null) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Send one back to rekindle", onClick = { sparking = true }, color = Ivory)
                    }
                }
            }

            // ---------- what you can do today
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (today.shared[me.id] != true) Text("Share your mood today to light your sun", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(horizontal = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassAction(Modifier.weight(1f), "Warm $pName's sun", if (today.canWarm) "Give 10 of your light" else if (today.shared[me.id] != true) "After you share" else "Done for today", today.canWarm) { vm.planetWarm() }
                        GlassAction(Modifier.weight(1f), "Send a spark", if (p.needsRekindle) "Both sparks rekindle it" else "A note to $pName", true) { sparking = true }
                    }
                    if (p.hibernate != null) GlassAction(Modifier.fillMaxWidth(), "Wake the planet", "It's asleep until ${p.hibernate.until}", true) { vm.planetWake() }
                    else GlassAction(Modifier.fillMaxWidth(), "Put it to sleep", if (today.canHibernate) "For trips and busy weeks, 3 to 14 days, once a month" else "Already slept this month", today.canHibernate) { sleeping = true }
                }
            }

            // ---------- vital signs
            item { SectionHeader("Vital signs") }
            item {
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    Vital("Atmosphere", p.atmosphere.toFloat(), Color(0xFF9FB4FF), "Shared presence. Both of you sharing fills it fastest")
                    Vital("Warmth", p.warmth, Gold, "How bright your two suns are together")
                    Vital("Life", p.life.toFloat(), Color(0xFF9BE3A8), "Grows with diary notes and pictures, when it's warm and the air is full")
                    Vital("Ice", p.ice.toFloat(), Color(0xFFE4ECFF), "Comes when the suns are low or uneven")
                }
            }

            // ---------- terraform
            item { SectionHeader("Shape the world · ${res.terraformCost} stardust") }
            item {
                Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ocean" to Color(0xFF6FA2FF), "forest" to Color(0xFF8FE3A8), "crystal" to Color(0xFFE2C4FF), "aurora" to Color(0xFFA6F5EA)).forEach { (b, col) ->
                        val can = p.stardust >= res.terraformCost && p.biome(b) < 70f
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(c.card).border(1.dp, col.copy(alpha = .3f), RoundedCornerShape(18.dp))
                                .pressable(enabled = can, haptic = false) { vm.planetTerraform(b) }.padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Canvas(Modifier.size(22.dp)) { glow(center, size.minDimension, col, .5f); drawCircle(col, size.minDimension * .3f) }
                            Text(b.replaceFirstChar { it.uppercase() }, style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.label, modifier = Modifier.padding(top = 6.dp))
                            Text("${p.biome(b).toInt()}%", style = Type.caption.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal), color = c.secondary)
                        }
                    }
                }
                Text("Growing one land shrinks the others, and it decides who can live there", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }

            // ---------- life
            val found = res.catalog.filter { p.species.containsKey(it.key) && (p.species[it.key]?.state != "dormant" || p.chronicle.any { e -> e.text.contains(it.name) }) }
            item { SectionHeader("Life · ${p.species.values.count { it.state == "alive" }} of ${res.catalog.size} alive") }
            item {
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    res.catalog.forEach { sp ->
                        val st = p.species[sp.key]?.state
                        val seen = sp in found
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(16.dp)) {
                                when {
                                    st == "alive" -> { glow(center, size.minDimension, Color(0xFF9BE3A8), .6f); drawCircle(Color.White, size.minDimension * .2f) }
                                    st == "extinct" -> drawLine(Color(0xFFFF9DB2), Offset(2f, size.height / 2), Offset(size.width - 2f, size.height / 2), 2.dp.toPx(), StrokeCap.Round)
                                    seen -> drawCircle(Color.White.copy(alpha = .35f), size.minDimension * .2f)
                                    else -> drawCircle(Color.White.copy(alpha = .15f), size.minDimension * .3f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (seen) sp.name else "Not yet seen", style = Type.callout, color = if (st == "alive") c.label else c.secondary)
                                Text(sp.needs, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
                            }
                            Text(when (st) { "alive" -> "Alive"; "extinct" -> "Lost"; else -> if (seen) "Dormant" else "" }, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = when (st) { "alive" -> Color(0xFF9BE3A8); "extinct" -> Color(0xFFFF9DB2); else -> c.tertiary })
                        }
                    }
                }
            }

            // ---------- buildings
            item { SectionHeader("Build · each one costs a little light every day") }
            item {
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    p.buildings.forEach { b ->
                        val name = res.buildings.firstOrNull { it.kind == b.kind }?.name ?: b.kind
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = Type.callout, color = if (b.ruined == null) c.label else c.tertiary, modifier = Modifier.weight(1f))
                            if (b.ruined != null) {
                                val cost = ((res.buildings.firstOrNull { it.kind == b.kind }?.cost ?: 40) + 1) / 2
                                SoftButton("Repair · $cost", onClick = { vm.planetRepair(b.id) }, color = Gold)
                            } else Text("Lit since ${b.built}", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
                        }
                    }
                    if (p.buildings.isNotEmpty()) HorizontalDivider(color = c.separator.copy(alpha = .4f), modifier = Modifier.padding(vertical = 8.dp))
                    res.buildings.forEach { b ->
                        val can = p.stardust >= b.cost
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(b.name, style = Type.callout, color = c.label, modifier = Modifier.weight(1f))
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(if (can) Ivory else c.card).pressable(enabled = can, haptic = false) { vm.planetBuild(b.kind) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            ) { Text("${b.cost}", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace), color = if (can) Color(0xFF1A1240) else c.tertiary) }
                        }
                    }
                    Text("Stardust comes from the games you play and the dates you track", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // ---------- moons and landmarks
            item { SectionHeader("Moons and landmarks") }
            item {
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    if (p.moons.isEmpty()) Text("Your first moon comes after 7 days you both share", style = Type.callout, color = c.secondary)
                    p.moons.forEach { m ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).pressable(enabled = m.lost == null, haptic = false) { naming = m.id }, verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(14.dp)) { if (m.lost == null) drawCircle(Color(0xFFDCD6FF), size.minDimension * .4f) else drawCircle(Color.White.copy(alpha = .25f), size.minDimension * .35f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())) }
                            Spacer(Modifier.width(12.dp))
                            Text(m.name, style = Type.callout, color = if (m.lost == null) c.label else c.tertiary, modifier = Modifier.weight(1f))
                            Text(if (m.lost != null) "Lost ${m.lost}" else "Since ${m.born}", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = if (m.lost != null) Color(0xFFFF9DB2) else c.tertiary)
                        }
                    }
                    if (p.landmarks.isNotEmpty()) HorizontalDivider(color = c.separator.copy(alpha = .4f), modifier = Modifier.padding(vertical = 8.dp))
                    p.landmarks.forEach { l ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(l.name, style = Type.callout, color = if (l.buried == true) c.tertiary else c.label, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(if (l.buried == true) "Under the ice" else l.day, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
                        }
                    }
                    if (p.landmarks.isEmpty() && p.moons.isNotEmpty()) Text("Track a date together to raise a landmark", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 6.dp))
                }
            }

            // ---------- the chronicle
            item { SectionHeader("Chronicle") }
            item {
                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    p.chronicle.take(24).forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(e.day.substring(5), style = Type.caption.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.width(52.dp))
                            Text(e.text, style = Type.callout, color = if (e.kind == "lost" || e.kind == "ruin") Color(0xFFFFB3C2) else c.label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
    } }

    naming?.let { target ->
        val moon = p0Moon(vm.planet?.planet, target)
        TextPrompt(
            title = if (target.isEmpty()) "Name your planet" else "Name this moon",
            initial = if (target.isEmpty()) vm.planet?.planet?.name ?: "" else moon?.name ?: "",
            action = "Save", max = 32,
            onDone = { name -> vm.planetName(name, target.ifEmpty { null }) { naming = null } },
            onDismiss = { naming = null },
        )
    }
    if (sparking) TextPrompt(
        title = "Send $pName a spark", initial = "", action = "Send", max = 200,
        hint = "A few words. When you both send one, a dimmed planet rekindles",
        onDone = { text -> vm.planetSpark(text) { sparking = false } }, onDismiss = { sparking = false },
    )
    if (sleeping) SleepDialog(onDone = { d -> vm.planetHibernate(d) { sleeping = false } }, onDismiss = { sleeping = false })
}

private fun p0Moon(p: PlanetState?, id: String) = p?.moons?.firstOrNull { it.id == id }

@Composable
private fun SunLabel(user: User?, name: String, power: Float, shared: Boolean, color: Color, modifier: Modifier, end: Boolean = false) {
    val c = T.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!end) { Avatar(user, 26.dp, ring = color); Spacer(Modifier.width(8.dp)) }
        Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
            Text(name, style = Type.footnote.copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 1)
            Text("Sun ${power.toInt()}", style = Type.caption.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal), color = c.secondary)
            Text(if (shared) "Shared today" else "Not yet today", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = if (shared) c.secondary else c.tertiary)
        }
        if (end) { Spacer(Modifier.width(8.dp)); Avatar(user, 26.dp, ring = color) }
    }
}

@Composable
private fun GlassAction(modifier: Modifier, title: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    val c = T.colors
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.card).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(18.dp))
            .pressable(enabled = enabled, haptic = false, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, style = Type.callout.copy(fontWeight = FontWeight.Medium), color = if (enabled) c.label else c.tertiary, maxLines = 1)
        Text(sub, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, maxLines = 2)
    }
}

@Composable
private fun Vital(label: String, value: Float, color: Color, help: String) {
    val c = T.colors
    Column(Modifier.padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Type.callout, color = c.label, modifier = Modifier.weight(1f))
            Text("${value.toInt()}", style = Type.callout.copy(fontFamily = FontFamily.Monospace), color = c.label)
        }
        Canvas(Modifier.fillMaxWidth().height(6.dp).padding(top = 3.dp)) {
            val y = size.height / 2
            drawLine(Color.White.copy(alpha = .1f), Offset(0f, y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
            if (value > 0f) drawLine(color, Offset(0f, y), Offset(size.width * (value / 100f).coerceIn(0f, 1f), y), 3.dp.toPx(), StrokeCap.Round)
        }
        Text(help, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TextPrompt(title: String, initial: String, action: String, max: Int, hint: String? = null, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val c = T.colors
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.sheet,
        title = { Text(title) },
        text = {
            Column {
                if (hint != null) Text(hint, style = Type.footnote, color = c.secondary, modifier = Modifier.padding(bottom = 10.dp))
                OutlinedTextField(
                    text, { if (it.length <= max) text = it }, singleLine = max <= 40, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent),
                )
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onDone(text) }) { Text(action, color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.secondary) } },
    )
}

@Composable
private fun SleepDialog(onDone: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = T.colors
    var pick by remember { mutableIntStateOf(1) }
    val days = listOf(3, 7, 14)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.sheet,
        title = { Text("Put the planet to sleep") },
        text = {
            Column {
                Text("While it sleeps nothing fades, and nothing grows. You can do this once a month, and wake it early any time.", style = Type.footnote, color = c.secondary)
                Spacer(Modifier.height(14.dp))
                Segmented(days.map { "$it days" }, pick, { pick = it })
            }
        },
        confirmButton = { TextButton(onClick = { onDone(days[pick]) }) { Text("Sleep", color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.secondary) } },
    )
}

/** The planet on the Today screen: small, turning, with one line of how it's doing. Opens the full page. */
@Composable
fun PlanetCard(summary: PlanetSummary?, me: String, partnerId: String?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = T.colors
    val look = if (summary == null) PlanetLook() else PlanetLook(
        ocean = (summary.biomes["ocean"] ?: 25.0).toFloat(), forest = (summary.biomes["forest"] ?: 25.0).toFloat(),
        crystal = (summary.biomes["crystal"] ?: 25.0).toFloat(), aurora = (summary.biomes["aurora"] ?: 25.0).toFloat(),
        ice = summary.ice.toFloat(), atmosphere = summary.atmosphere.toFloat(), life = summary.life.toFloat(),
        sunMe = (summary.suns[me] ?: 60.0).toFloat(), sunThem = (summary.suns[partnerId ?: ""] ?: 60.0).toFloat(),
        moons = summary.moons, species = summary.alive, stage = summary.stage, seed = planetSeed(summary.created),
    )
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier.clip(shape).background(c.card).border(1.dp, Color.White.copy(alpha = .07f), shape).pressable(haptic = false, onClick = onOpen).padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlanetView(look, Modifier.size(width = 132.dp, height = 112.dp), showSuns = false)
        Column(Modifier.weight(1f)) {
            Text(summary?.name ?: "Meet your planet", style = Type.title, color = c.label, maxLines = 1)
            Text(
                when {
                    summary == null -> "A small world your two suns keep alive"
                    partnerId != null && partnerId in summary.sparks && me !in summary.sparks -> "A spark is waiting for you"
                    else -> planetStageLabel(summary.stage, summary.quiet)
                },
                style = Type.footnote, color = if (summary == null || summary.stage == "thriving") c.secondary else Color(0xFFFFB3C2), maxLines = 2,
            )
        }
    }
}

