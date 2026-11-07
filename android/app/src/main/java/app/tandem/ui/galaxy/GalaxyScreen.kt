package app.tandem.ui.galaxy

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.tandem.AppViewModel
import app.tandem.data.CosmosEntry
import app.tandem.ui.components.PrimaryButton
import app.tandem.ui.components.pressable
import app.tandem.ui.pets.rememberReducedMotion
import app.tandem.ui.screens.EaseOutStrong
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ivory = Color(0xFFF6F2FF)
private val Gold = Color(0xFFFFD58A)
private val Glass = Color(0xFF0E0B1C).copy(alpha = .72f)

private fun fmt(n: Int) = String.format(Locale.US, "%,d", n)

private fun shapeName(kind: String, arms: Int) = when (kind) {
    "barred" -> "Barred spiral, $arms arms"; "ring" -> "Ring galaxy"; "lens" -> "Lens galaxy"; else -> "Spiral, $arms arms"
}

@Composable
fun GalaxyScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = T.colors
    LaunchedEffect(Unit) { vm.loadCosmos() }
    val res = vm.cosmos
    val reduced = rememberReducedMotion()
    var stars by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Long?>(null) }
    var confirmReroll by remember { mutableStateOf(false) }
    var showEarn by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    val cos = res?.cosmos
    val shown = preview ?: cos?.seed
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var glView by remember { mutableStateOf<GalaxyView?>(null) }
    DisposableEffect(lifecycle, glView) {
        val obs = LifecycleEventObserver { _, e -> when (e) { Lifecycle.Event.ON_PAUSE -> glView?.onPause(); Lifecycle.Event.ON_RESUME -> glView?.onResume(); else -> {} } }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx -> GalaxyView(ctx, reduced).also { v -> v.onReady = { stars = it }; glView = v } },
            modifier = Modifier.fillMaxSize(),
            update = { v -> if (shown != null && cos != null) v.show(shown, cos.lifetime) },
            onRelease = { it.onPause() },
        )

        // ---------- top: back, what it is, the wallet
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 14.dp, end = 20.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Glass).pressable(haptic = false, onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.label, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Your galaxy", style = Type.title, color = c.label)
                if (shown != null && cos != null) {
                    val lk = remember(shown, cos.lifetime) { GalaxyGen.look(shown, cos.lifetime) }
                    Text("${lk.palette.name} · ${shapeName(lk.kind, lk.arms)}", style = Type.footnote, color = c.secondary)
                }
            }
            if (cos != null) Text("${fmt(cos.stardust)} stardust", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = Gold)
        }

        if (res == null || cos == null) { CircularProgressIndicator(Modifier.align(Alignment.Center), color = c.accent); return@Box }

        // a re-roll waiting to be chosen opens the choice straight away
        LaunchedEffect(cos.candidates) { if (cos.candidates.isNotEmpty()) choosing = true }

        // ---------- bottom: how it's grown, and the choices
        AnimatedContent(
            choosing && cos.candidates.isNotEmpty(), Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
            transitionSpec = { fadeIn(tween(200, easing = EaseOutStrong)) togetherWith fadeOut(tween(140)) }, label = "panel",
        ) { pick ->
            if (pick) ChoosePanel(cos.seed, cos.candidates, cos.lifetime, preview ?: cos.seed, onPreview = { preview = it }) { seed ->
                vm.galaxyChoose(seed) { ok -> if (ok) { choosing = false; preview = null } }
            } else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Glass).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(28.dp)).padding(20.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Stat(Modifier.weight(1f), if (stars > 0) fmt(stars) else "…", "stars")
                    Stat(Modifier.weight(1f), fmt(cos.lifetime), "stardust earned")
                    Stat(Modifier.weight(1f), "${cos.comets}", if (cos.comets == 1) "comet" else "comets")
                }
                val g = GalaxyGen.growth(cos.lifetime)
                Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = .1f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(g.coerceAtLeast(.02f)).clip(RoundedCornerShape(2.dp)).background(Ivory))
                }
                Text(
                    if (g >= 1f) "Fully formed. Every star you earn still counts" else "${(g * 100).toInt()}% formed. It grows with every bit of stardust you earn",
                    style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 8.dp),
                )
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp)).background(if (cos.comets > 0) Ivory else Color.White.copy(alpha = .08f))
                            .pressable(enabled = cos.comets > 0) { confirmReroll = true },
                        contentAlignment = Alignment.Center,
                    ) { Text(if (cos.comets > 0) "Re-roll · 1 comet" else "Re-roll needs a comet", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = if (cos.comets > 0) Color(0xFF1A1226) else c.tertiary) }
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = .08f)).pressable(haptic = false) { showEarn = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Info, "How to earn", tint = c.label, modifier = Modifier.size(20.dp))
                    }
                }
                Text("Drag to turn it, pinch to come closer", style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary, modifier = Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally))
            }
        }
    }

    if (confirmReroll) AlertDialog(
        onDismissRequest = { confirmReroll = false }, containerColor = c.sheet,
        title = { Text("Re-roll your galaxy?") },
        text = { Text("A comet brings three new galaxies to choose from. You can still keep the one you have.", style = Type.callout, color = c.secondary) },
        confirmButton = { TextButton(onClick = { confirmReroll = false; vm.galaxyReroll { ok -> if (ok) choosing = true } }) { Text("Use a comet", color = c.accent) } },
        dismissButton = { TextButton(onClick = { confirmReroll = false }) { Text("Not now", color = c.secondary) } },
    )
    if (showEarn && cos != null) EarnSheet(cos.log, onDismiss = { showEarn = false })
}

@Composable
private fun Stat(modifier: Modifier, value: String, label: String) {
    val c = T.colors
    Column(modifier) {
        Text(value, style = Type.headline.copy(fontFamily = FontFamily.Monospace, fontSize = 18.sp), color = c.label, maxLines = 1)
        Text(label, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, maxLines = 1)
    }
}

/** Picking between the galaxy you have and three new ones. The big view shows whichever you tap. */
@Composable
private fun ChoosePanel(current: Long, candidates: List<Long>, lifetime: Int, selected: Long, onPreview: (Long) -> Unit, onKeep: (Long) -> Unit) {
    val c = T.colors
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(busy) { if (busy) { kotlinx.coroutines.delay(4000); busy = false } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Glass).border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(28.dp)).padding(18.dp)) {
        Text("Choose your galaxy", style = Type.headline, color = c.label)
        Text("Tap one to see it up close", style = Type.footnote, color = c.secondary)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(current) + candidates).forEachIndexed { i, s ->
                val on = s == selected
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.aspectRatio(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black)
                            .border(if (on) 2.dp else 1.dp, if (on) Ivory else Color.White.copy(alpha = .1f), RoundedCornerShape(16.dp))
                            .pressable(haptic = false) { onPreview(s) },
                    ) { GalaxyThumb(s, lifetime, Modifier.fillMaxSize()) }
                    Text(if (i == 0) "Yours" else "New ${i}", style = Type.caption.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = if (on) c.label else c.tertiary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton(if (selected == current) "Keep the one we have" else "Keep this one", onClick = { busy = true; onKeep(selected) }, loading = busy, color = Ivory)
    }
}

/** A still galaxy, painted off the main thread. */
@Composable
fun GalaxyThumb(seed: Long, lifetime: Int, modifier: Modifier = Modifier, px: Int = 260, yaw: Float = 25f) {
    var bmp by remember(seed, lifetime) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(seed, lifetime) { bmp = withContext(Dispatchers.Default) { runCatching { GalaxyBitmap.render(seed, lifetime, px, px, yaw) }.getOrNull() } }
    Box(modifier) { bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EarnSheet(log: List<CosmosEntry>, onDismiss: () -> Unit) {
    val c = T.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Text("Stardust", style = Type.title, color = c.label)
            Text("Everything you do in Tandem earns it. Your galaxy grows with all you've ever earned, so spending never shrinks it", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            listOf(
                Triple("Share a mood", "+3", "3 a day each"),
                Triple("Write a diary note", "+5", "5 a day each"),
                Triple("Add a picture", "+2", "6 a day each"),
                Triple("Play a game round", "+2", "30 a day each"),
                Triple("Finish a tracked date", "+15", "3 a day"),
                Triple("You both share a mood", "+5", "once a day"),
                Triple("Seven days in a row of that", "a comet", ""),
                Triple("Milestones", "+40 to +300", "some bring a comet"),
            ).forEach { (what, gets, note) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(what, style = Type.callout, color = c.label)
                        if (note.isNotEmpty()) Text(note, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.tertiary)
                    }
                    Text(gets, style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = Gold)
                }
            }
            if (log.isNotEmpty()) {
                Text("Lately", style = Type.headline, color = c.label, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                val df = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
                log.take(12).forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = c.tertiary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(e.text, style = Type.footnote, color = c.secondary, modifier = Modifier.weight(1f))
                        Text(df.format(Date(e.at)), style = Type.caption.copy(fontWeight = FontWeight.Normal, fontFamily = FontFamily.Monospace), color = c.tertiary)
                    }
                }
            }
        }
    }
}

/** Today's galaxy card: the galaxy on black, how many stars, the wallet. */
@Composable
fun GalaxyCard(seed: Long?, lifetime: Int, stardust: Int, comets: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = T.colors
    val shape = RoundedCornerShape(24.dp)
    Box(modifier.height(168.dp).clip(shape).background(Color.Black).border(1.dp, Color.White.copy(alpha = .08f), shape).pressable(haptic = false, onClick = onOpen)) {
        if (seed != null) GalaxyThumb(seed, lifetime, Modifier.fillMaxHeight().aspectRatio(1f).align(Alignment.CenterEnd).padding(end = 4.dp), px = 360, yaw = 40f)
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text("Your galaxy", style = Type.title, color = c.label)
            Text(
                if (seed == null) "Earn stardust to make it grow" else "${fmt(GalaxyGen.starsFor(GalaxyGen.growth(lifetime)))} stars · ${fmt(stardust)} stardust" + if (comets > 0) " · $comets ${if (comets == 1) "comet" else "comets"}" else "",
                style = Type.footnote, color = c.secondary,
            )
        }
    }
}
