package app.tandem

import androidx.compose.runtime.saveable.rememberSaveable
import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.tandem.data.ThemeMode
import app.tandem.work.EXTRA_TAB
import app.tandem.ui.components.*
import app.tandem.ui.screens.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.TandemTheme
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay

enum class AppTab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Filled.Favorite),
    Timeline("Moods", Icons.Filled.Timeline),
    Diary("Diary", Icons.Filled.MenuBook),
    Calendar("Calendar", Icons.Filled.CalendarMonth),
    Play("Play", Icons.Filled.SportsEsports),
    Profile("Profile", Icons.Filled.Person),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.requestedTab = intent?.getStringExtra(EXTRA_TAB)
        setContent {
            val mode by vm.theme.collectAsState()
            val dark = when (mode) { ThemeMode.System -> isSystemInDarkTheme(); ThemeMode.Light -> false; ThemeMode.Dark -> true }
            TandemTheme(dark) {
                Surface(Modifier.fillMaxSize(), color = T.colors.background) { Root(vm) }
            }
        }
    }

    // Tapping a notification opens the matching tab.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.requestedTab = intent.getStringExtra(EXTRA_TAB)
    }

    // While visible, partner updates become in-app banners; otherwise they're system notifications.
    override fun onStart() { super.onStart(); (application as TandemApp).repo.appInForeground = true }
    override fun onStop() { (application as TandemApp).repo.appInForeground = false; super.onStop() }
}

@Composable
private fun Root(vm: AppViewModel) {
    val loggedIn by vm.loggedIn.collectAsState()
    val state by vm.state.collectAsState()

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(loggedIn) {
        if (loggedIn && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        if (loggedIn) vm.autoCheckForUpdate()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (vm.loggedIn.value) { vm.refresh(silent = true); vm.autoCheckForUpdate(); vm.startPolling() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.stopPolling() }

    // 0 = auth, 1 = loading, 2 = pairing, 3 = app
    val stage = when {
        !loggedIn -> 0
        state == null -> 1
        state?.couple?.paired != true -> 2
        else -> 3
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(stage, label = "stage", transitionSpec = {
            (fadeIn(tween(350)) + scaleIn(initialScale = 0.96f, animationSpec = tween(350))) togetherWith fadeOut(tween(200))
        }) { st ->
            when (st) {
                0 -> AuthScreen(vm)
                1 -> LoadingScreen(vm)
                2 -> PairingScreen(vm)
                else -> Shell(vm)
            }
        }
        ToastHost(vm)
    }
}

@Composable
private fun LoadingScreen(vm: AppViewModel) {
    val c = T.colors
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refresh(silent = false); delay(6000); failed = true }
    AuroraBackground {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            AppLogo(88.dp)
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = c.accent)
            AnimatedVisibility(failed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp)) {
                    Text("Taking a while…", style = Type.callout, color = c.secondary)
                    Spacer(Modifier.height(8.dp))
                    SoftButton("Try again", onClick = { vm.refresh(silent = false) })
                    Spacer(Modifier.height(4.dp))
                    SoftButton("Sign out", color = c.secondary, onClick = { vm.logout() })
                }
            }
        }
    }
}

@Composable
private fun Shell(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(AppTab.Today) }
    var showStats by rememberSaveable { mutableStateOf(false) }
    var showAchievements by rememberSaveable { mutableStateOf(false) }
    val requested = vm.requestedTab
    LaunchedEffect(requested) {
        if (requested != null) {
            AppTab.entries.firstOrNull { it.name == requested }?.let { tab = it; showStats = false }
            vm.requestedTab = null
        }
    }
    val update = vm.update
    val dismissed by vm.dismissedUpdate.collectAsState()
    DreamBackground {
        AnimatedContent(tab, label = "tab", transitionSpec = {
            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
            (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { dir * it / 6 } + fadeIn(tween(220))) togetherWith
                (slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { -dir * it / 6 } + fadeOut(tween(120)))
        }) { t ->
            when (t) {
                AppTab.Today -> HomeScreen(vm, openCalendar = { tab = AppTab.Calendar }, openStats = { showStats = true })
                AppTab.Timeline -> TimelineScreen(vm)
                AppTab.Diary -> DiaryScreen(vm)
                AppTab.Calendar -> CalendarScreen(vm)
                AppTab.Play -> GamesScreen(vm)
                AppTab.Profile -> ProfileScreen(vm)
            }
        }
        FloatingTabBar(tab, { tab = it }, Modifier.align(Alignment.BottomCenter), badge = if (update != null) AppTab.Profile else null)

        // Full stats page, slides over everything
        AnimatedVisibility(
            showStats, modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(spring(0.85f, Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutVertically { it / 3 } + fadeOut(tween(160)),
        ) {
            BackHandler(enabled = !showAchievements) { showStats = false }
            StatsScreen(vm, onBack = { showStats = false }, openAchievements = { showAchievements = true })
        }
        AnimatedVisibility(
            showAchievements, modifier = Modifier.fillMaxSize(),
            enter = slideInVertically(spring(0.85f, Spring.StiffnessMediumLow)) { it / 3 } + fadeIn(tween(220)),
            exit = slideOutVertically { it / 3 } + fadeOut(tween(160)),
        ) {
            BackHandler { showAchievements = false }
            AchievementsScreen(vm, onBack = { showAchievements = false })
        }
    }

    // Patch notes: once after the app has been updated to a new version.
    val notesSeen by vm.notesSeen.collectAsState()
    val version = BuildConfig.VERSION_NAME
    val showPatchNotes = notesSeen != "?" && notesSeen != version
    if (showPatchNotes) PatchNotesDialog(onDismiss = { vm.markNotesSeen(version) })

    // New release on GitHub: tell the person once per version, right inside the app.
    if (!showPatchNotes && update != null && dismissed != update.version) {
        val c = T.colors
        AlertDialog(
            onDismissRequest = { vm.dismissUpdateDialog() }, containerColor = c.sheet,
            title = { Text("Tandem ${update.version} is available") },
            text = {
                Column {
                    Text("A new version is ready to install. Your data stays exactly as it is.", color = c.secondary)
                    if (update.notes.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(update.notes.take(500), style = Type.footnote, color = c.secondary)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.dismissUpdateDialog(); vm.installUpdate() }) { Text("Update now", color = c.accent) } },
            dismissButton = { TextButton(onClick = { vm.dismissUpdateDialog() }) { Text("Later", color = c.secondary) } },
        )
    }
}

@Composable
private fun FloatingTabBar(selected: AppTab, onSelect: (AppTab) -> Unit, modifier: Modifier = Modifier, badge: AppTab? = null) {
    val c = T.colors
    Row(
        modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 14.dp)
            .shadow(if (c.isDark) 0.dp else 14.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(RoundedCornerShape(32.dp)).background(c.sheet.copy(alpha = 0.92f))
            .border(1.dp, if (c.isDark) Color.White.copy(alpha = .12f) else Color.White.copy(alpha = .8f), RoundedCornerShape(32.dp)).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTab.entries.forEach { t ->
            val on = t == selected
            val bg by animateColorAsState(if (on) c.accentSoft else Color.Transparent, tween(220), label = "tabBg")
            val tint by animateColorAsState(if (on) c.accent else c.secondary, tween(220), label = "tabTint")
            Row(
                Modifier.clip(RoundedCornerShape(26.dp)).background(bg).pressable(haptic = !on) { onSelect(t) }
                    .padding(horizontal = if (on) 12.dp else 9.dp, vertical = 11.dp).animateContentSize(spring(0.8f, Spring.StiffnessMediumLow)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Icon(t.icon, contentDescription = t.label, tint = tint, modifier = Modifier.size(22.dp))
                    if (badge == t) Box(Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-2).dp).size(9.dp).clip(RoundedCornerShape(50)).background(c.accent)
                        .border(1.5.dp, c.sheet, RoundedCornerShape(50)))
                }
                if (on) {
                    Spacer(Modifier.width(6.dp))
                    Text(t.label, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = tint, maxLines = 1)
                }
            }
        }
    }
}

/** iOS-style banner that drops from the top for messages and errors. */
@Composable
private fun BoxScope.ToastHost(vm: AppViewModel) {
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        vm.toasts.collect { m -> message = m; delay(2800); if (message == m) message = null }
    }
    AnimatedVisibility(
        message != null, modifier = Modifier.align(Alignment.TopCenter),
        enter = slideInVertically(spring(0.6f, Spring.StiffnessMedium)) { -it * 2 } + fadeIn(),
        exit = slideOutVertically { -it * 2 } + fadeOut(),
    ) {
        val c = T.colors
        Box(
            Modifier.statusBarsPadding().padding(top = 8.dp, start = 24.dp, end = 24.dp)
                .shadow(12.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp))
                .background(if (c.isDark) c.cardRaised else Color(0xFF1C1C1E)).padding(horizontal = 18.dp, vertical = 12.dp),
        ) { Text(message ?: "", style = Type.callout.copy(fontWeight = FontWeight.Medium), color = Color.White) }
    }
}
