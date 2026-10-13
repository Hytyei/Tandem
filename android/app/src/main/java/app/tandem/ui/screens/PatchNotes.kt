package app.tandem.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tandem.BuildConfig
import app.tandem.data.CHANGELOG
import app.tandem.data.ReleaseNotes
import app.tandem.data.notesFor
import app.tandem.ui.components.AppLogo
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import androidx.compose.foundation.background

@Composable
private fun NotesList(notes: ReleaseNotes) {
    val c = T.colors
    notes.items.forEach { line ->
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(c.accent))
            Spacer(Modifier.width(10.dp))
            Text(line, style = Type.callout, color = c.label)
        }
    }
}

/** Shown once after the app has been updated: what's new in this version. */
@Composable
fun PatchNotesDialog(onDismiss: () -> Unit) {
    val c = T.colors
    val notes = notesFor(BuildConfig.VERSION_NAME) ?: return
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.sheet,
        icon = { AppLogo(44.dp) },
        title = { Text("What's new") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(notes.title, style = Type.headline, color = c.label)
                Spacer(Modifier.height(8.dp))
                NotesList(notes)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it", color = c.accent) } },
    )
}

/** Every release's notes, newest first (Profile > What's new). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchNotesSheet(onDismiss: () -> Unit) {
    val c = T.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("Patch notes", style = Type.title, color = c.label)
            Text("You're on version ${BuildConfig.VERSION_NAME}", style = Type.footnote, color = c.secondary)
            CHANGELOG.forEach { n ->
                Spacer(Modifier.height(18.dp))
                Text(n.title, style = Type.headline, color = c.label)
                Text(n.date, style = Type.caption, color = c.secondary)
                Spacer(Modifier.height(6.dp))
                NotesList(n)
            }
        }
    }
}
