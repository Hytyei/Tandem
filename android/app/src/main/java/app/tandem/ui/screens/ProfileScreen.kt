package app.tandem.ui.screens

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tandem.AppViewModel
import app.tandem.BuildConfig
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(vm: AppViewModel, openPage: (Boolean) -> Unit = {}) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val me = data.me
    val partner = data.couple.partner

    var editProfile by remember { mutableStateOf(false) }
    var pickAnniv by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val notifOn = remember { mutableStateOf(app.tandem.work.Notifier.enabled(ctx)) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { notifOn.value = app.tandem.work.Notifier.enabled(ctx) }
    val nMoods by vm.notifyMoods.collectAsState()
    val nDiary by vm.notifyDiary.collectAsState()
    val nEvents by vm.notifyEvents.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.uploadAvatar(uri) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 130.dp)) {
        LargeTitle("Profile")

        // ---------- Header
        Column(Modifier.fillMaxWidth().staggerIn(0), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.pressable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Avatar(me, 112.dp, ring = c.me)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(34.dp).clip(CircleShape).background(c.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Filled.CameraAlt, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(me.displayName, style = Type.title, color = c.label)
            Text("@${me.username}", style = Type.callout, color = c.secondary)
            if (me.bio.isNotBlank()) Text(me.bio, style = Type.callout, color = c.label, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 40.dp, vertical = 6.dp))
            Spacer(Modifier.height(6.dp))
            SoftButton("Edit profile", onClick = { editProfile = true })
        }

        // ---------- Relationship
        SectionHeader("Relationship")
        Group(Modifier.staggerIn(1)) {
            ListRow("Partner", partner?.displayName ?: "—", leading = { Avatar(partner, 30.dp) })
            Divider()
            ListRow("My page", if (me.profile.isEmpty) "Fill in" else "", accent = me.profile.isEmpty, onClick = { openPage(true) })
            Divider()
            if (partner != null) {
                ListRow("${partner.displayName.substringBefore(' ')}'s page", "", onClick = { openPage(false) })
                Divider()
            }
            ListRow("Together since", parseDate(data.couple.anniversary)?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "Set date", accent = true, onClick = { pickAnniv = true })
            Divider()
            ListRow("Remove partner", "", destructive = true, onClick = { confirmRemove = true })
        }

        // ---------- Notifications
        SectionHeader("Notifications")
        Group(Modifier.staggerIn(2)) {
            if (!notifOn.value) {
                ListRow("Notifications are off", "Turn on", accent = true, onClick = {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                })
                Divider()
            }
            SwitchRow("Partner's mood updates", nMoods) { vm.setNotifyMoods(it) }
            Divider()
            SwitchRow("New diary notes", nDiary) { vm.setNotifyDiary(it) }
            Divider()
            SwitchRow("Events & special days added or changed", nEvents) { vm.setNotifyEvents(it) }
            Divider()
            ListRow("Send a test notification", "", accent = true, onClick = { vm.testNotification() })
            Text(
                "Event reminders always arrive on time. Partner updates arrive within about 15 minutes when the app is closed (instantly while it's open) because Tandem has no push server.",
                style = Type.footnote, color = c.secondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // ---------- App
        SectionHeader("App")
        Group(Modifier.staggerIn(3)) {
            val upd = vm.update
            ListRow("Version", BuildConfig.VERSION_NAME + if (upd != null) "  ·  ${upd.version} available" else "")
            Divider()
            ListRow("What's new", "Patch notes", onClick = { showNotes = true })
            Divider()
            if (upd != null) {
                ListRow(if (vm.updateProgress != null) "Downloading… ${((vm.updateProgress ?: 0f) * 100).toInt()}%" else "Install version ${upd.version}", "", accent = true, onClick = { vm.installUpdate() })
            } else {
                ListRow(if (vm.checkingUpdate) "Checking…" else "Check for updates", "", accent = true, onClick = { vm.checkForUpdate(manual = true) })
            }
            if (upd != null && upd.notes.isNotBlank()) {
                Text(upd.notes.take(400), style = Type.footnote, color = c.secondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        Group(Modifier.staggerIn(4)) {
            ListRow("Sign out", "", destructive = true, onClick = { confirmLogout = true })
            Divider()
            ListRow("Delete my account", "", destructive = true, onClick = { confirmDelete = true })
        }
    }

    if (showNotes) PatchNotesSheet { showNotes = false }

    if (editProfile) {
        var name by remember { mutableStateOf(me.displayName) }
        var bio by remember { mutableStateOf(me.bio) }
        AlertDialog(
            onDismissRequest = { editProfile = false }, containerColor = c.sheet,
            title = { Text("Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { if (it.length <= 40) name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(bio, { if (it.length <= 160) bio = it }, label = { Text("Bio") }, maxLines = 3, shape = RoundedCornerShape(14.dp))
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveProfile(name, bio) { editProfile = false } }) { Text("Save", color = c.accent) } },
            dismissButton = { TextButton(onClick = { editProfile = false }) { Text("Cancel", color = c.secondary) } },
        )
    }

    if (pickAnniv) {
        val initial = (parseDate(data.couple.anniversary) ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickAnniv = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { vm.setAnniversary(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                pickAnniv = false
            }) { Text("OK", color = c.accent) } },
            dismissButton = { TextButton(onClick = { pickAnniv = false }) { Text("Cancel", color = c.secondary) } },
        ) { DatePicker(state = state) }
    }

    if (confirmRemove) {
        val name = partner?.displayName ?: "your partner"
        AlertDialog(
            onDismissRequest = { confirmRemove = false }, containerColor = c.sheet,
            title = { Text("Remove $name?") },
            text = {
                Text(
                    "You'll both be disconnected and go back to the connect screen. Nothing is deleted: your shared moods, diary and events stay saved " +
                        "for the two of you, and come back if you ever connect again. A new partner starts with a blank slate.",
                )
            },
            confirmButton = { TextButton(enabled = !vm.busy, onClick = { vm.leaveCouple { confirmRemove = false } }) { Text("Remove partner", color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep", color = c.secondary) } },
        )
    }

    if (confirmDelete) {
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirmDelete = false }, containerColor = c.sheet,
            title = { Text("Delete your account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This permanently deletes your account, your moods, diary notes, pictures and events. Your partner is disconnected and keeps their own data. This can't be undone.")
                    OutlinedTextField(
                        pw, { pw = it }, label = { Text("Your password") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = { TextButton(enabled = pw.isNotEmpty() && !vm.busy, onClick = { vm.deleteAccount(pw) { confirmDelete = false } }) { Text("Delete forever", color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = c.secondary) } },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false }, containerColor = c.sheet,
            title = { Text("Sign out?") },
            text = { Text("Your data stays safe in the cloud. Sign back in any time.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text("Sign out", color = c.danger) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel", color = c.secondary) } },
        )
    }
}

@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(T.colors.card), content = content)
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = T.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Type.body, color = c.label, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}

@Composable
private fun Divider() { HorizontalDivider(color = T.colors.separator, modifier = Modifier.padding(start = 16.dp)) }

@Composable
private fun ListRow(
    title: String, value: String, accent: Boolean = false, destructive: Boolean = false,
    leading: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
    val c = T.colors
    val mod = Modifier.fillMaxWidth().let { if (onClick != null) it.pressable(haptic = false, onClick = onClick) else it }
    Row(mod.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Text(title, style = Type.body.copy(fontWeight = if (accent || destructive) FontWeight.Medium else FontWeight.Normal),
            color = when { destructive -> c.danger; accent -> c.accent; else -> c.label }, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, style = Type.body, color = c.secondary, maxLines = 1)
    }
}
