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
import coil.compose.AsyncImage
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
fun HomeScreen(vm: AppViewModel, openCalendar: () -> Unit, openStats: () -> Unit, openPartnerPage: () -> Unit = {}) {
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
            val days = daysTogether(data.couple.anniversary)
            val st = data.stats ?: Stats()
            val first = me.displayName.substringBefore(' ')

            // ---------- Our sky: full-bleed, no box. Days together drawn as a constellation behind the greeting. Tap for Stats.
            Box(Modifier.fillMaxWidth()) {
                ConstellationDays(
                    days ?: 0L, Modifier.fillMaxWidth(), height = 470.dp,
                    caption = if (days == null) null else if (days == 1L) "day together" else "days together",
                    framed = false, showNumber = days != null,
                )
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 18.dp)) {
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")).uppercase(), style = Type.caption.copy(letterSpacing = 0.6.sp), color = Color.White.copy(alpha = .7f))
                    Text("${greeting()}, $first", style = Type.largeTitle, color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color(0x33FFFFFF)).pressable(onClick = openStats).padding(start = 6.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(me, 28.dp, ring = c.me)
                        Box(Modifier.offset(x = (-8).dp)) { Avatar(partner, 28.dp, ring = c.partner) }
                        Spacer(Modifier.width(2.dp))
                        Text("$first & ${partner?.displayName?.substringBefore(' ') ?: "…"}", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1)
                        Spacer(Modifier.width(10.dp))
                        Text("Stats  ›", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFFFFB5C5))
                    }
                }
                if (days == null) Text(
                    "Set your anniversary in Profile to start counting days together", style = Type.callout, color = Color.White.copy(alpha = .85f),
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(horizontal = 36.dp).padding(top = 70.dp),
                )
            }

            vm.update?.let { u -> UpdateBanner(u.version, vm.updateProgress, onInstall = { vm.installUpdate() }) }

            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().staggerIn(0), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(Modifier.weight(1f), Icons.Filled.EmojiEmotions, st.moods.toLong(), "moods", c.accent)
                MiniStat(Modifier.weight(1f), Icons.Filled.Edit, st.diary.toLong(), "notes", c.me)
                MiniStat(Modifier.weight(1f), Icons.Filled.CalendarMonth, st.activeDays.toLong(), "active days", c.special)
            }

            // ---------- Mood shares
            SectionHeader("Right now")
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val myMood = data.latestMoods[me.id]
                val theirMood = partner?.let { data.latestMoods[it.id] }
                Column(Modifier.weight(1f)) {
                    MoodNowCard(me, myMood, "You", c.me, tick, Modifier.fillMaxWidth().staggerIn(4))
                    ReactionBar(myMood?.reactions.orEmpty(), me.id, canReact = false, onReact = {}, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
                Column(Modifier.weight(1f)) {
                    MoodNowCard(partner, theirMood, partner?.displayName?.substringBefore(' ') ?: "Partner", c.partner, tick, Modifier.fillMaxWidth().staggerIn(5))
                    if (theirMood != null) ReactionBar(
                        theirMood.reactions, me.id, canReact = true, onReact = { emoji -> vm.react("mood", theirMood.id, emoji) },
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.padding(horizontal = 16.dp).staggerIn(6)) {
                PrimaryButton("Share your mood", onClick = { showSheet = true })
            }

            // ---------- About your partner: what they're into right now + today's meters (from their page). Tap for the whole page.
            if (partner != null) {
                Spacer(Modifier.height(18.dp))
                PartnerSnapshot(partner, openPartnerPage)
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
    var activity by remember { mutableStateOf<Activity?>(null) }
    var mood by remember { mutableStateOf<Mood?>(null) }
    var note by remember { mutableStateOf("") }
    var choosing by remember { mutableStateOf(true) }   // the activity grid is open until one is picked, then it folds into one row
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
            // Only this part scrolls (and only vertically); the Share button below always stays in reach.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                // Live preview of what the partner will see
                val previewRes = activity?.art ?: mood?.art
                Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(previewRes, label = "preview", transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(spring(0.5f, Spring.StiffnessMedium), initialScale = .5f)) togetherWith (fadeOut(tween(100)) + scaleOut(targetScale = 1.2f))
                    }) { res ->
                        if (res != null) Image(painterResource(res), null, Modifier.height(106.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop)
                        else Text("What are you up to?", style = Type.title, color = c.tertiary)
                    }
                }

                // ---- 1. activity (required, comes first)
                StepTitle(1, "What are you up to?", done = activity != null)
                AnimatedContent(choosing || activity == null, label = "activityStep") { open ->
                    if (open) ActivityPicker(activity) { activity = it; choosing = false }
                    else activity?.let { a ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(a.tint.copy(alpha = .14f)).pressable { choosing = true }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(painterResource(a.art), null, Modifier.width(76.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, style = Type.headline, color = c.label, modifier = Modifier.weight(1f))
                            Text("Change", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.accent, modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                }

                // ---- 2. mood (appears once an activity is chosen)
                AnimatedVisibility(activity != null && !choosing, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column {
                        StepTitle(2, "How are you feeling?", done = mood != null)
                        TileGrid(Mood.entries, columns = 4, selected = mood, onSelect = { mood = if (mood == it) null else it }, art = { it.art }, label = { it.label }, tint = { it.tint })
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            note, { if (it.length <= 140) note = it }, placeholder = { Text("Add a note for your partner… (optional)", color = c.tertiary) },
                            shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(), maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                when { activity == null -> "Pick what you're up to"; mood == null -> "Now pick how you feel"; else -> "Share" },
                onClick = { vm.shareMood(mood!!, activity, note, onDismiss) }, enabled = mood != null && activity != null, loading = vm.busy,
            )
        }
    }
}

@Composable
private fun StepTitle(n: Int, text: String, done: Boolean) {
    val c = T.colors
    Row(Modifier.padding(start = 4.dp, top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(50)).background(if (done) c.accent else c.accentSoft), contentAlignment = Alignment.Center) {
            Text(if (done) "✓" else "$n", color = if (done) Color.White else c.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = Type.headline, color = c.label)
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

/** All activities as a tidy grid grouped by theme: everything is visible by scrolling down, nothing hides sideways. */
@Composable
private fun ActivityPicker(selected: Activity?, onSelect: (Activity) -> Unit) {
    val c = T.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ACTIVITY_GROUPS.forEach { (group, list) ->
            Text(group.uppercase(), style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            list.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { a ->
                        val on = a == selected
                        val scale by animateFloatAsState(if (on) 1f else .97f, spring(0.5f, Spring.StiffnessMediumLow), label = "poster")
                        Box(
                            Modifier.weight(1f).aspectRatio(4f / 3f).scale(scale).clip(RoundedCornerShape(16.dp))
                                .border(if (on) 3.dp else 0.dp, if (on) c.accent else Color.Transparent, RoundedCornerShape(16.dp))
                                .pressable { onSelect(a) },
                        ) {
                            Image(painterResource(a.art), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x00120A3A), Color(0xB3120A3A)), startY = 40f)))
                            Text(a.label, style = Type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp), color = Color.White, maxLines = 1,
                                modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 6.dp))
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ================================================================== about your partner (home)
@Composable
private fun PartnerSnapshot(partner: User, onOpen: () -> Unit) {
    val c = T.colors
    val p = partner.profile
    val name = partner.displayName.substringBefore(' ')
    val now = listOf(
        Triple(MediaLookup.WATCHING, "📺" to "Watching", p.watching), Triple(MediaLookup.PLAYING, "🎮" to "Playing", p.playing),
        Triple(MediaLookup.LISTENING, "🎧" to "Listening to", p.listening), Triple(MediaLookup.READING, "📖" to "Reading", p.reading),
    ).filter { it.third.isNotBlank() }
    val meters = Meter.entries.filter { it.key in p.meters }
    SoftCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().pressable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(partner, 34.dp, ring = c.partner)
            Spacer(Modifier.width(10.dp))
            Text("About $name", style = Type.headline, color = c.label, modifier = Modifier.weight(1f), maxLines = 1)
            Text("Open  ›", style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
        }
        if (now.isEmpty() && meters.isEmpty()) {
            Text("$name hasn't shared what they're into yet. Open their page to see the rest.", style = Type.callout, color = c.secondary, modifier = Modifier.padding(top = 10.dp))
        }
        if (now.isNotEmpty()) {
            Text("RIGHT NOW", style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            now.forEach { (kind, label, title) ->
                val m = p.mediaFor(kind)
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (m != null && m.img.isNotBlank()) {
                        AsyncImage(
                            model = m.img, contentDescription = m.title, contentScale = ContentScale.Crop,
                            modifier = Modifier.width(46.dp).height(64.dp).clip(RoundedCornerShape(10.dp)).background(c.separator),
                        )
                    } else Box(Modifier.width(46.dp).height(46.dp).clip(RoundedCornerShape(14.dp)).background(c.accentSoft), contentAlignment = Alignment.Center) { Text(label.first, fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${label.first}  ${label.second}", style = Type.caption, color = c.secondary)
                        Text(m?.title?.ifBlank { null } ?: title, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val extra = m?.sub?.takeIf { it.isNotBlank() } ?: m?.blurb?.takeIf { it.isNotBlank() }
                        if (extra != null) Text(extra, style = Type.caption, color = c.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (meters.isNotEmpty()) {
            Text("TODAY'S METERS", style = Type.caption.copy(letterSpacing = 0.6.sp), color = c.secondary, modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
            meters.forEach { m ->
                val v = p.meters[m.key] ?: 0
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(m.label, style = Type.footnote.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.weight(1f))
                        Text("$v% · ${m.caption(v)}", style = Type.caption, color = m.tint, maxLines = 1)
                    }
                    Spacer(Modifier.height(5.dp))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(m.tint.copy(alpha = .16f))) {
                        Box(Modifier.fillMaxWidth((v / 100f).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(m.tint.copy(alpha = .65f), m.tint))))
                    }
                }
            }
        }
    }
}
