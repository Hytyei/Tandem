package app.tandem.ui.screens

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.R
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun CountUp(target: Long, style: androidx.compose.ui.text.TextStyle, color: Color) {
    val v by animateIntAsState(target.toInt(), tween(1100, easing = FastOutSlowInEasing), label = "count")
    Text("$v", style = style, color = color)
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, openCalendar: () -> Unit, openStats: () -> Unit) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val me = data.me
    val partner = data.couple.partner
    var showSheet by rememberSaveable { mutableStateOf(false) }

    // Re-render "x min ago" labels every minute.
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick = System.currentTimeMillis() } }

    val pull = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refresh(silent = false) }, state = pull) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
            LargeTitle("${greeting()}, ${me.displayName.substringBefore(' ')}", subtitle = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")))

            vm.update?.let { u -> UpdateBanner(u.version, vm.updateProgress, onInstall = { vm.installUpdate() }) }

            // ---------- Our numbers: days together drawn as a node graph + three flat icon stats. Tap for the full page.
            val days = daysTogether(data.couple.anniversary)
            val st = data.stats ?: Stats()
            SoftCard(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(0).pressable(onClick = openStats),
                shape = RoundedCornerShape(30.dp), padding = 18.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(me, 34.dp, ring = c.me)
                    Box(Modifier.offset(x = (-8).dp)) { Avatar(partner, 34.dp, ring = c.partner) }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${me.displayName.substringBefore(' ')} & ${partner?.displayName?.substringBefore(' ') ?: "…"}",
                        style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.weight(1f), maxLines = 1,
                    )
                    Text("Stats  ›", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                }
                Spacer(Modifier.height(14.dp))
                if (days != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        NodeNumber(days, digitHeight = 64.dp, from = c.accent, to = c.me, ghost = c.label.copy(alpha = .12f))
                        Spacer(Modifier.width(14.dp))
                        Text(if (days == 1L) "day\ntogether" else "days\ntogether", style = Type.title, color = c.label)
                    }
                } else {
                    Text("Set your anniversary in Profile to start counting days together", style = Type.callout, color = c.secondary)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat(Modifier.weight(1f), Icons.Filled.EmojiEmotions, st.moods.toLong(), "moods", c.accent)
                    MiniStat(Modifier.weight(1f), Icons.Filled.Edit, st.diary.toLong(), "notes", c.me)
                    MiniStat(Modifier.weight(1f), Icons.Filled.CalendarMonth, st.activeDays.toLong(), "active days", c.special)
                }
            }

            // ---------- Right now
            SectionHeader("Right now")
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MoodNowCard(me, data.latestMoods[me.id], "You", c.me, tick, Modifier.weight(1f).staggerIn(4))
                MoodNowCard(partner, partner?.let { data.latestMoods[it.id] }, partner?.displayName?.substringBefore(' ') ?: "Partner", c.partner, tick, Modifier.weight(1f).staggerIn(5))
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.padding(horizontal = 16.dp).staggerIn(6)) {
                PrimaryButton("Share your mood", onClick = { showSheet = true })
            }

            // ---------- Next event
            val next = remember(data.events) { upcoming(data.events).firstOrNull() }
            if (next != null) {
                SectionHeader("Coming up")
                Box(Modifier.padding(horizontal = 16.dp).staggerIn(7)) { NextEventCard(next.first, next.second, openCalendar) }
            }
        }
    }

    if (showSheet) MoodSheet(vm, onDismiss = { showSheet = false })
}

@Composable
private fun UpdateBanner(version: String, progress: Float?, onInstall: () -> Unit) {
    val c = T.colors
    Row(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)).background(c.accent).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Update available", style = Type.headline, color = Color.White)
            Text(if (progress != null) "Downloading… ${(progress * 100).toInt()}%" else "Tandem $version is ready to install", style = Type.footnote, color = Color.White.copy(alpha = .85f))
        }
        if (progress == null) {
            Text("Install", style = Type.callout.copy(fontWeight = FontWeight.Bold), color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).pressable(onClick = onInstall).padding(horizontal = 16.dp, vertical = 8.dp))
        } else {
            CircularProgressIndicator(progress = { progress }, color = Color.White, trackColor = Color.White.copy(alpha = .3f), modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: Long, label: String, tint: Color) {
    val c = T.colors
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .12f)).padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(7.dp))
        Column {
            CountUp(value, Type.callout.copy(fontWeight = FontWeight.Bold), c.label)
            Text(label, style = Type.caption.copy(fontSize = 10.sp), color = c.secondary, maxLines = 1)
        }
    }
}

@Composable
private fun MoodNowCard(user: User?, mood: MoodEntry?, name: String, ring: Color, tick: Long, modifier: Modifier) {
    val c = T.colors
    Box(modifier.aspectRatio(0.9f).clip(RoundedCornerShape(28.dp)).background(c.card)) {
        AnimatedContent(
            mood?.id, label = "moodSwap", modifier = Modifier.fillMaxSize(),
            transitionSpec = { (fadeIn(tween(450)) + scaleIn(tween(700), initialScale = 1.12f)) togetherWith fadeOut(tween(300)) },
        ) { _ ->
            Box(Modifier.fillMaxSize()) {
                if (mood != null) {
                    Image(painterResource(mood.artRes()), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33120A3A), Color(0x00120A3A), Color(0xD9120A3A)), startY = 0f)))
                    Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                        Text(mood.headline(), style = Type.title.copy(fontSize = 19.sp, lineHeight = 23.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (mood.note.isNotBlank()) Text("“${mood.note}”", style = Type.footnote, color = Color.White.copy(alpha = .85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(timeAgo(mood.createdAt, tick), style = Type.caption, color = Color.White.copy(alpha = .65f))
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌙", fontSize = 40.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("No update yet", style = Type.footnote, color = c.secondary)
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50)).background(Color(0x66120A3A)).padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(user, 26.dp, ring = ring)
            Spacer(Modifier.width(6.dp))
            Text(name, style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun NextEventCard(e: CalendarEvent, date: LocalDate, onClick: () -> Unit) {
    val c = T.colors
    val days = daysUntil(date)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(c.card).pressable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) { Text(e.emoji, fontSize = 28.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, style = Type.headline, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(date.format(DateTimeFormatter.ofPattern("EEE, d MMM")) + (e.time?.let { " · $it" } ?: ""), style = Type.footnote, color = c.secondary)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (days > 1) {
                Text("$days", style = Type.title.copy(fontSize = 28.sp), color = c.accent)
                Text("days", style = Type.caption, color = c.secondary)
            } else {
                Text(countdownLabel(days), style = Type.headline, color = c.accent)
            }
        }
    }
}

// ================================================================== mood sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val c = T.colors
    var mood by remember { mutableStateOf<Mood?>(null) }
    var activity by remember { mutableStateOf<Activity?>(null) }
    var note by remember { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            // Live preview of what the partner will see
            val previewRes = activity?.art ?: mood?.art
            Box(Modifier.fillMaxWidth().height(132.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(previewRes, label = "preview", transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(spring(0.5f, Spring.StiffnessMedium), initialScale = .5f)) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 1.2f))
                }) { res ->
                    if (res != null) Image(painterResource(res), null, Modifier.height(124.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
                    else Text("How are you?", style = Type.title, color = c.tertiary)
                }
            }

            Text("Mood", style = Type.headline, color = c.label, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            TileGrid(Mood.entries, columns = 4, selected = mood, onSelect = { mood = if (mood == it) null else it }, art = { it.art }, label = { it.label }, tint = { it.tint })

            Text("What are you up to?  (optional)", style = Type.headline, color = c.label, modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 8.dp))
            ActivityStrip(activity) { activity = if (activity == it) null else it }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                note, { if (it.length <= 140) note = it }, placeholder = { Text("Add a note for your partner…", color = c.tertiary) },
                shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent),
            )
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Share", onClick = { vm.shareMood(mood!!, activity, note, onDismiss) }, enabled = mood != null, loading = vm.busy)
        }
    }
}

@Composable
private fun <E> TileGrid(
    items: List<E>, columns: Int, selected: E?, onSelect: (E) -> Unit,
    art: (E) -> Int, label: (E) -> String, tint: (E) -> Color,
) {
    val c = T.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { item ->
                    val on = item == selected
                    val scale by animateFloatAsState(if (on) 1.06f else 1f, spring(0.45f, Spring.StiffnessMedium), label = "tile")
                    Column(
                        Modifier.weight(1f).scale(scale).clip(RoundedCornerShape(18.dp))
                            .background(if (on) tint(item).copy(alpha = .16f) else c.card)
                            .border(if (on) 2.dp else 0.dp, if (on) tint(item) else Color.Transparent, RoundedCornerShape(18.dp))
                            .pressable { onSelect(item) }.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Art(art(item), if (columns >= 5) 46.dp else 56.dp)
                        Text(label(item), style = Type.caption.copy(fontSize = 11.sp), color = if (on) tint(item) else c.secondary, maxLines = 1)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The activity artworks as a swipeable row of little posters (they are wide paintings, so they get room). */
@Composable
private fun ActivityStrip(selected: Activity?, onSelect: (Activity) -> Unit) {
    val c = T.colors
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Activity.entries.forEach { a ->
            val on = a == selected
            val scale by animateFloatAsState(if (on) 1f else .96f, spring(0.5f, Spring.StiffnessMediumLow), label = "poster")
            Box(
                Modifier.width(148.dp).aspectRatio(4f / 3f).scale(scale).clip(RoundedCornerShape(22.dp))
                    .border(if (on) 3.dp else 0.dp, if (on) Color.White else Color.Transparent, RoundedCornerShape(22.dp))
                    .pressable { onSelect(a) },
            ) {
                Image(painterResource(a.art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x00120A3A), Color(0xB3120A3A)), startY = 60f)))
                Text(a.label, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 9.dp))
                if (on) Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(RoundedCornerShape(50)).background(c.accent), contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
