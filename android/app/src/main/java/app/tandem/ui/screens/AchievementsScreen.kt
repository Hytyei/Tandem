package app.tandem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandem.AppViewModel
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.pets.rememberReducedMotion
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type

/**
 * Achievements as a night sky you fill in together: every achievement is a star, each kind its own small
 * constellation. Under it, the few you're closest to, then every medal by kind (tap one for the details).
 */
@Composable
fun AchievementsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val detail = vm.stats
    LaunchedEffect(Unit) { if (vm.stats == null) vm.loadStats() }
    val ctx = remember(detail, data.couple.anniversary) { AchContext(daysTogether(data.couple.anniversary) ?: 0L, detail ?: StatsDetail()) }
    val all = Achievements.all
    val done = all.count { it.unlocked(ctx) }
    val reduced = rememberReducedMotion()
    var open by remember { mutableStateOf<Achievement?>(null) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        DreamBackground {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = 40.dp)) {
                Row(Modifier.padding(start = 14.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BackDisc(onBack)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Achievements", style = Type.title, color = c.label)
                        Text(if (detail == null) "Counting…" else "$done of ${all.size} earned", style = Type.footnote, color = c.secondary)
                    }
                }

                // ---------- the sky you fill in
                GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().staggerIn(0), padding = 16.dp) {
                    AchievementSky(AchGroup.entries.map { g -> g.label to all.filter { it.group == g }.map { it.unlocked(ctx) } }, still = reduced)
                }

                // ---------- almost there
                val next = all.filter { !it.unlocked(ctx) }.sortedByDescending { it.progress(ctx).toFloat() / it.target }.take(3)
                if (detail != null && next.isNotEmpty()) {
                    SectionHeader("Almost there")
                    GlassCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(1), padding = 16.dp) {
                        next.forEachIndexed { i, a ->
                            Row(Modifier.fillMaxWidth().pressable(haptic = false) { open = a }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Medal(a.icon, false, a.progress(ctx).toFloat() / a.target, size = 46.dp)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.title, style = Type.callout, color = c.label, maxLines = 1)
                                    Text(a.description, style = Type.footnote, color = c.secondary, maxLines = 1)
                                }
                                Text("${a.progress(ctx)}/${a.target}", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.secondary)
                            }
                            if (i < next.lastIndex) HorizontalDivider(color = c.label.copy(alpha = .06f), modifier = Modifier.padding(start = 60.dp))
                        }
                    }
                }

                // ---------- every medal, by kind
                AchGroup.entries.forEachIndexed { gi, g ->
                    val list = all.filter { it.group == g }
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                        Text(g.label, style = Type.headline, color = c.label, modifier = Modifier.weight(1f))
                        Text("${list.count { it.unlocked(ctx) }} of ${list.size}", style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = c.tertiary)
                    }
                    MedalGrid(list, ctx, Modifier.padding(horizontal = 12.dp).staggerIn(2 + gi)) { open = it }
                }
            }
        }
    }
    open?.let { a -> MedalSheet(a, ctx) { open = null } }
}

@Composable
private fun MedalGrid(list: List<Achievement>, ctx: AchContext, modifier: Modifier, onOpen: (Achievement) -> Unit) {
    val c = T.colors
    Column(modifier) {
        list.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { a ->
                    val on = a.unlocked(ctx)
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).pressable(haptic = false) { onOpen(a) }.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Medal(a.icon, on, a.progress(ctx).toFloat() / a.target)
                        Text(a.title, style = Type.footnote, color = if (on) c.label else c.secondary, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedalSheet(a: Achievement, ctx: AchContext, onDismiss: () -> Unit) {
    val c = T.colors
    val on = a.unlocked(ctx)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Medal(a.icon, on, a.progress(ctx).toFloat() / a.target, size = 96.dp)
            Text(a.title, style = Type.title, color = c.label, modifier = Modifier.padding(top = 16.dp))
            Text(a.description, style = Type.callout, color = c.secondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            Text(
                if (on) "Earned" else "${a.progress(ctx)} of ${a.target} so far",
                style = Type.footnote.copy(fontFamily = FontFamily.Monospace), color = if (on) c.special else c.tertiary, modifier = Modifier.padding(top = 14.dp),
            )
            Text(a.group.label, style = Type.footnote, color = c.tertiary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
