package app.tandem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.data.*
import app.tandem.ui.components.*
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import coil.compose.AsyncImage

/** What is being typed into the "right now" fields (series, movie, game, book, track) plus the posters picked for them. */
internal class NowDraft(current: ProfileDetails) {
    val text = mutableStateMapOf<String, String>().apply { NowKind.entries.forEach { put(it.key, current.nowText(it.key)) } }
    val media = mutableStateMapOf<String, MediaInfo>().apply { putAll(current.media) }
    var searchKind by mutableStateOf<String?>(null)

    /** [p] with the typed texts and posters filled in. A poster only stays while its title is still there. */
    fun applyTo(p: ProfileDetails): ProfileDetails {
        var out = p
        NowKind.entries.forEach { out = out.withNowText(it.key, text[it.key].orEmpty().trim()) }
        return out.copy(media = media.filter { (k, v) -> v.title.isNotBlank() && text[k].orEmpty().isNotBlank() })
    }
}

@Composable
internal fun nowFieldColors(): TextFieldColors {
    val c = T.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent, unfocusedBorderColor = c.separator, focusedTextColor = c.label, unfocusedTextColor = c.label, cursorColor = c.accent,
        focusedContainerColor = c.card, unfocusedContainerColor = c.card,
    )
}

/** The five "right now" fields, each with a magnifier that finds the poster / cover and a short description. */
@Composable
internal fun NowFields(draft: NowDraft) {
    val c = T.colors
    val colors = nowFieldColors()
    Text(
        "Tap the magnifier to find the poster or cover and a short description. The title you search for is sent to a free lookup service (TVmaze, IMDb, Steam, Google Books or Deezer), never to the Tandem server.",
        style = Type.caption, color = c.tertiary, modifier = Modifier.padding(bottom = 8.dp),
    )
    NowKind.entries.forEach { k ->
        val value = draft.text[k.key].orEmpty()
        val info = draft.media[k.key]?.takeIf { it.title.isNotBlank() }
        OutlinedTextField(
            value, { if (it.length <= 60) { draft.text[k.key] = it; if (info != null && it.trim() != info.title) draft.media.remove(k.key) } },
            label = { Text("${k.emoji} ${k.label}") }, singleLine = true, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = if (info != null) 4.dp else 8.dp), colors = colors,
            trailingIcon = {
                Icon(Icons.Filled.Search, "Find poster", tint = if (value.trim().length >= 2) c.accent else c.tertiary,
                    modifier = Modifier.size(40.dp).pressable(enabled = value.trim().length >= 2) { draft.searchKind = k.key }.padding(9.dp))
            },
        )
        if (info != null) Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(14.dp)).background(c.card).padding(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (info.img.isNotBlank()) AsyncImage(info.img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 36.dp, height = 52.dp).clip(RoundedCornerShape(8.dp)).background(c.separator))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(info.title, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, maxLines = 1)
                if (info.sub.isNotBlank()) Text(info.sub, style = Type.caption, color = c.secondary, maxLines = 1)
            }
            Text("Remove", style = Type.caption.copy(fontWeight = FontWeight.SemiBold), color = c.secondary, modifier = Modifier.pressable { draft.media.remove(k.key) }.padding(8.dp))
        }
    }
}

/** The results dialog for whichever field's magnifier was tapped. Put it next to the sheet that holds [NowFields]. */
@Composable
internal fun NowSearchHost(vm: AppViewModel, draft: NowDraft) {
    val kind = draft.searchKind ?: return
    MediaSearchDialog(vm, kind, draft.text[kind].orEmpty().trim(), onDismiss = { draft.searchKind = null }) { info ->
        draft.media[kind] = info
        draft.text[kind] = info.title.take(60)
        draft.searchKind = null
    }
}

/** A short editor for just what you are into right now and today's meters, reachable from the Today page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickEditSheet(vm: AppViewModel, current: ProfileDetails, onDismiss: () -> Unit) {
    val c = T.colors
    val draft = remember { NowDraft(current) }
    val meters = remember { mutableStateMapOf<String, Int>().apply { Meter.entries.forEach { put(it.key, current.meters[it.key] ?: 0) } } }
    var metersOn by remember { mutableStateOf(current.meters.isNotEmpty()) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.sheet, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text("What are you into?", style = Type.title, color = c.label)
            Text("Your partner sees this on Today. Leave a line empty to hide it.", style = Type.footnote, color = c.secondary, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            NowFields(draft)

            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Today's meters", style = Type.headline, color = c.label)
                    Text("Playful sliders your partner can see", style = Type.footnote, color = c.secondary)
                }
                Switch(metersOn, { metersOn = it }, colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
            }
            if (metersOn) Meter.entries.forEach { m ->
                val v = meters[m.key] ?: 0
                Column(Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${m.emoji}  ${m.label}", style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = c.label, modifier = Modifier.weight(1f))
                        Text("$v%", style = Type.callout, color = m.tint)
                    }
                    Slider(
                        v.toFloat(), { meters[m.key] = it.toInt() }, valueRange = 0f..100f,
                        colors = SliderDefaults.colors(thumbColor = m.tint, activeTrackColor = m.tint, inactiveTrackColor = m.tint.copy(alpha = .2f)),
                    )
                    Text(m.caption(v), style = Type.caption, color = c.secondary)
                }
            }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Save", loading = vm.busy, onClick = {
                vm.saveProfileDetails(
                    draft.applyTo(current).copy(meters = if (metersOn) Meter.entries.associate { it.key to (meters[it.key] ?: 0) } else emptyMap()),
                    onDismiss,
                )
            })
        }
    }
    NowSearchHost(vm, draft)
}
