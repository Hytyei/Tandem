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
fun ProfileScreen(vm: AppViewModel) {
    val c = T.colors
    val s by vm.state.collectAsState()
    val data = s ?: return
    val me = data.me
    val partner = data.couple.partner
    val theme by vm.theme.collectAsState()

    var editProfile by remember { mutableStateOf(false) }
    var pickAnniv by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

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
            ListRow("Together since", parseDate(data.couple.anniversary)?.format(DateTimeFormatter.ofPattern("d MMM yyyy")) ?: "Set date", accent = true, onClick = { pickAnniv = true })
        }

        // ---------- Appearance
        SectionHeader("Appearance")
        Group(Modifier.staggerIn(2)) {
            Box(Modifier.padding(12.dp)) {
                Segmented(listOf("System", "Light", "Dark"), theme.ordinal, { vm.setTheme(ThemeMode.entries[it]) })
            }
        }

        // ---------- App
        SectionHeader("App")
        Group(Modifier.staggerIn(3)) {
            val upd = vm.update
            ListRow("Version", BuildConfig.VERSION_NAME + if (upd != null) "  ·  ${upd.version} available" else "")
            Divider()
            if (upd != null) {
                ListRow(if (vm.updateProgress != null) "Downloading… ${((vm.updateProgress ?: 0f) * 100).toInt()}%" else "Install version ${upd.version}", "", accent = true, onClick = { vm.installUpdate() })
            } else {
                ListRow(if (vm.checkingUpdate) "Checking…" else "Check for updates", "", accent = true, onClick = { vm.checkForUpdate(manual = true) })
            }
            if (upd != null && upd.notes.isNotBlank()) {
                Text(upd.notes.take(400), style = Type.footnote, color = c.secondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            Divider()
            ListRow("Server", vm.repo.currentBaseUrl.removePrefix("https://"), onClick = { editServer = true })
        }

        Spacer(Modifier.height(20.dp))
        Group(Modifier.staggerIn(4)) { ListRow("Sign out", "", destructive = true, onClick = { confirmLogout = true }) }
    }

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

    if (editServer) {
        var url by remember { mutableStateOf(vm.repo.currentBaseUrl) }
        AlertDialog(
            onDismissRequest = { editServer = false }, containerColor = c.sheet,
            title = { Text("Server address") },
            text = { OutlinedTextField(url, { url = it }, singleLine = true, shape = RoundedCornerShape(14.dp), label = { Text("Worker URL") }) },
            confirmButton = { TextButton(onClick = { vm.setServer(url); editServer = false; vm.refresh(silent = false) }) { Text("Save", color = c.accent) } },
            dismissButton = { TextButton(onClick = { editServer = false }) { Text("Cancel", color = c.secondary) } },
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
