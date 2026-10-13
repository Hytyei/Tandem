package app.tandem.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type

/** Relationship milestones: unlocked ones in colour, locked ones greyed out with a progress bar. */
@Composable
fun AchievementsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val detail = vm.stats
    LaunchedEffect(Unit) { if (vm.stats == null) vm.loadStats() }
    val ctx = remember(detail, data.couple.anniversary) { AchContext(daysTogether(data.couple.anniversary) ?: 0L, detail ?: StatsDetail()) }
    val done = Achievements.all.count { it.unlocked(ctx) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        DreamBackground {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(bottom = 32.dp)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(c.card).pressable(onClick = onBack), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = c.label)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("MILESTONES", style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary)
                        Text("Achievements", style = Type.largeTitle, color = c.label)
                    }
                }

                SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = 18.dp) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$done", style = Type.numberHuge.copy(fontSize = 44.sp, lineHeight = 46.sp), color = c.special)
                        Text(" / ${Achievements.all.size} unlocked", style = Type.title, color = c.label, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    ProgressTrack(done.toFloat() / Achievements.all.size, c.special, 10.dp)
                    if (detail == null) {
                        Spacer(Modifier.height(8.dp))
                        Text("Loading your progress…", style = Type.footnote, color = c.secondary)
                    }
                }

                AchGroup.entries.forEach { g ->
                    val list = Achievements.all.filter { it.group == g }
                    SectionHeader(g.label)
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        list.forEach { a -> AchievementRow(a, ctx) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementRow(a: Achievement, ctx: AchContext) {
    val c = T.colors
    val unlocked = a.unlocked(ctx)
    val tint = if (unlocked) a.group.tint else c.tertiary
    val progress = a.progress(ctx)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(c.card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = if (unlocked) .18f else .12f)), contentAlignment = Alignment.Center) {
            Icon(if (unlocked) a.icon else Icons.Filled.Lock, null, tint = tint, modifier = Modifier.size(if (unlocked) 26.dp else 22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(a.title, style = Type.headline, color = if (unlocked) c.label else c.secondary, maxLines = 1)
            Text(a.description, style = Type.footnote, color = c.secondary, maxLines = 2)
            if (!unlocked) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ProgressTrack(progress.toFloat() / a.target, a.group.tint, 6.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text("$progress / ${a.target}", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.secondary)
                }
            }
        }
        if (unlocked) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(26.dp).clip(CircleShape).background(a.group.tint), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, "Unlocked", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ProgressTrack(frac: Float, tint: androidx.compose.ui.graphics.Color, height: androidx.compose.ui.unit.Dp) {
    val c = T.colors
    val anim = remember { Animatable(0f) }
    LaunchedEffect(frac) { anim.animateTo(frac.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing)) }
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(c.label.copy(alpha = .10f))) {
        Box(Modifier.fillMaxWidth(anim.value.coerceAtLeast(0.01f)).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(tint))
    }
}
