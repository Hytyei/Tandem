package app.tandem.ui.screens

import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.R
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.delay

/** Dusk sky with parallax hills, shared with the rest of the app's backdrop. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    DreamBackground(modifier, scene = true, content = content)

@Composable
private fun IosField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, capitalize: Boolean = false,
) {
    val c = T.colors
    TextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(label, color = c.tertiary) },
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboard,
            capitalization = if (capitalize) androidx.compose.ui.text.input.KeyboardCapitalization.Words else androidx.compose.ui.text.input.KeyboardCapitalization.None,
        ),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.card, unfocusedContainerColor = c.card,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            cursorColor = c.accent, focusedTextColor = c.label, unfocusedTextColor = c.label,
        ),
        textStyle = Type.body,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun AuthScreen(vm: AppViewModel) {
    val c = T.colors
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = sign in, 1 = create account
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var showServer by rememberSaveable { mutableStateOf(false) }
    var server by rememberSaveable { mutableStateOf(vm.repo.currentBaseUrl) }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val pop by animateFloatAsState(if (appeared) 1f else 0.3f, spring(0.45f, Spring.StiffnessLow), label = "pop")
    val float by rememberInfiniteTransition(label = "float").animateFloat(
        -6f, 6f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "floatY")

    AuroraBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            AppLogo(120.dp, Modifier.scale(pop).offset(y = float.dp))
            Spacer(Modifier.height(16.dp))
            Text("Tandem", style = Type.largeTitle.copy(fontSize = 40.sp), color = c.label)
            Text("A little space for the two of you", style = Type.callout, color = c.secondary)
            Spacer(Modifier.height(32.dp))

            Segmented(listOf("Sign in", "Create account"), mode, { mode = it })
            Spacer(Modifier.height(16.dp))

            AnimatedVisibility(mode == 1, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column {
                    IosField(name, { name = it }, "Your name (shown to your partner)", capitalize = true)
                    Spacer(Modifier.height(12.dp))
                }
            }
            IosField(username, { username = it.filter { ch -> !ch.isWhitespace() } }, "Username")
            Spacer(Modifier.height(12.dp))
            IosField(password, { password = it }, "Password", password = true)
            if (mode == 1) {
                Spacer(Modifier.height(6.dp))
                Text("At least 8 characters", style = Type.footnote, color = c.secondary, modifier = Modifier.align(Alignment.Start).padding(start = 8.dp))
            }
            Spacer(Modifier.height(20.dp))
            val valid = username.length >= 3 && (if (mode == 0) password.isNotEmpty() else password.length >= 8)
            PrimaryButton(
                if (mode == 0) "Sign in" else "Create account",
                onClick = { if (mode == 0) vm.login(username, password) else vm.register(username, password, name) },
                enabled = valid, loading = vm.busy,
            )

            Spacer(Modifier.height(20.dp))
            Text(
                if (showServer) "Hide server settings" else "Server settings",
                style = Type.footnote, color = c.secondary,
                modifier = Modifier.pressable(haptic = false) { showServer = !showServer }.padding(8.dp),
            )
            AnimatedVisibility(showServer) {
                Column {
                    IosField(server, { server = it }, "https://your-worker.workers.dev", keyboard = KeyboardType.Uri)
                    Spacer(Modifier.height(8.dp))
                    SoftButton("Save server address", onClick = { vm.setServer(server) { vm.toast("Server address saved") } })
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun PairingScreen(vm: AppViewModel) {
    val c = T.colors
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val state by vm.state.collectAsState()
    val me = state?.me
    val code = state?.couple?.inviteCode
    var entered by rememberSaveable { mutableStateOf("") }

    // Poll while waiting so the screen flips the moment the partner joins.
    LaunchedEffect(Unit) {
        while (true) { vm.refresh(silent = true); delay(4000) }
    }
    // Creating the invite lazily avoids a dangling couple for people who just want to enter a code.
    var showCreate by rememberSaveable { mutableStateOf(code != null) }

    val beat by rememberInfiniteTransition(label = "beat").animateFloat(
        1f, 1.12f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "beat")

    AuroraBackground {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(me, 72.dp, ring = c.me)
                AppLogo(52.dp, Modifier.scale(beat).padding(horizontal = 6.dp))
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(50)).background(c.card), contentAlignment = Alignment.Center) {
                    Text("?", style = Type.title, color = c.tertiary)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Hi ${me?.displayName ?: ""}!", style = Type.largeTitle, color = c.label)
            Text("Now connect with your partner", style = Type.callout, color = c.secondary)
            Spacer(Modifier.height(28.dp))

            SoftCard(Modifier.fillMaxWidth()) {
                Text("Invite your partner", style = Type.headline, color = c.label)
                Spacer(Modifier.height(4.dp))
                Text("Create a code and send it to them.", style = Type.callout, color = c.secondary)
                Spacer(Modifier.height(14.dp))
                AnimatedContent(code, label = "code", transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = .9f)) togetherWith fadeOut(tween(150)) }) { value ->
                    if (value == null) {
                        PrimaryButton("Create invite code", onClick = { showCreate = true; vm.createInvite() }, loading = vm.busy && showCreate)
                    } else {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                value.chunked(3).joinToString(" "),
                                style = Type.largeTitle.copy(fontSize = 40.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
                                color = c.accent,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(c.accentSoft).padding(horizontal = 20.dp, vertical = 12.dp),
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SoftButton("Copy", onClick = { clipboard.setText(AnnotatedString(value)); vm.toast("Code copied") })
                                SoftButton("Share", onClick = {
                                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, "Join me on Tandem! Use my invite code: $value")
                                    ctx.startActivity(Intent.createChooser(send, "Share invite").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                })
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("Waiting for your partner to join…", style = Type.footnote, color = c.secondary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SoftCard(Modifier.fillMaxWidth()) {
                Text("Have a code?", style = Type.headline, color = c.label)
                Spacer(Modifier.height(4.dp))
                Text("Enter the code your partner sent you.", style = Type.callout, color = c.secondary)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    entered, { entered = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) },
                    singleLine = true, placeholder = { Text("ABC123", color = c.tertiary) },
                    textStyle = Type.title.copy(textAlign = TextAlign.Center, letterSpacing = 4.sp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent, unfocusedBorderColor = c.separator,
                        focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Connect", onClick = { vm.join(entered) }, enabled = entered.length >= 4, loading = vm.busy && !showCreate)
                Spacer(Modifier.height(10.dp))
                Text("Reconnecting with a previous partner brings your saved history back. A new partner starts fresh.", style = Type.footnote, color = c.secondary)
            }
            Spacer(Modifier.height(20.dp))
            Text("Sign out", style = Type.callout, color = c.secondary, modifier = Modifier.pressable(haptic = false) { vm.logout() }.padding(12.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}
