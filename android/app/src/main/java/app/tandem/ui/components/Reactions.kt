package app.tandem.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.data.REACTION_EMOJIS
import app.tandem.data.Reaction
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type

/**
 * Emoji reactions under a mood or a diary note. Everybody's reactions show as small chips (yours outlined in your colour,
 * your partner's in theirs). When [canReact] is true (the post is your partner's) a button opens the emoji picker;
 * tapping your current emoji again removes it.
 */
@Composable
fun ReactionBar(
    reactions: List<Reaction>, meId: String, canReact: Boolean, onReact: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reactions.isEmpty() && !canReact) return
    val c = T.colors
    var open by remember { mutableStateOf(false) }
    val mine = reactions.firstOrNull { it.userId == meId }?.emoji
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        reactions.forEach { r ->
            val ring = if (r.userId == meId) c.me else c.partner
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.clip(shape).background(ring.copy(alpha = .14f)).border(1.dp, ring.copy(alpha = .55f), shape).padding(horizontal = 9.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) { Text(r.emoji, fontSize = 16.sp) }
        }
        if (canReact) Box {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(c.card).border(1.dp, c.separator, CircleShape).pressable { open = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.EmojiEmotions, if (mine == null) "React" else "Change reaction", tint = c.secondary, modifier = Modifier.size(18.dp)) }
            DropdownMenu(open, { open = false }, containerColor = c.sheet) {
                Column(Modifier.padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    REACTION_EMOJIS.chunked(7).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.forEach { e ->
                                val on = e == mine
                                Box(
                                    Modifier.size(42.dp).clip(CircleShape).background(if (on) c.accent.copy(alpha = .20f) else androidx.compose.ui.graphics.Color.Transparent)
                                        .pressable(haptic = false) { open = false; onReact(if (on) null else e) },
                                    contentAlignment = Alignment.Center,
                                ) { Text(e, fontSize = 24.sp) }
                            }
                        }
                    }
                    if (mine != null) Text(
                        "Tap your reaction again to remove it", style = Type.caption, color = c.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        if (canReact && reactions.isEmpty()) Text("React", style = Type.caption.copy(fontWeight = FontWeight.Medium), color = c.tertiary)
    }
}
