package app.tandem.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandem.data.DateType
import app.tandem.data.Mood
import app.tandem.data.ratingLabel
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type

/** 1–5 hearts. Pass [onChange] to make it tappable (tapping the current value clears the rating). */
@Composable
fun HeartRating(rating: Int?, modifier: Modifier = Modifier, size: Dp = 28.dp, onChange: ((Int?) -> Unit)? = null) {
    val c = T.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            val on = (rating ?: 0) >= i
            val tapModifier = if (onChange != null) Modifier.size(size + 12.dp).pressable { onChange(if (rating == i) null else i) } else Modifier.size(size)
            Box(tapModifier, contentAlignment = Alignment.Center) {
                Icon(
                    if (on) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (onChange != null) "$i heart${if (i == 1) "" else "s"}" else null,
                    tint = if (on) c.accent else c.tertiary,
                    modifier = Modifier.size(size),
                )
            }
        }
    }
}

/** Date type chips (date night, movie night, gaming session…). Tap the selected one again to clear it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DateTypeChips(selected: DateType?, onSelect: (DateType?) -> Unit, modifier: Modifier = Modifier) {
    val c = T.colors
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DateType.entries.forEach { t ->
            val on = selected == t
            val shape = RoundedCornerShape(50)
            Row(
                Modifier.clip(shape).background(if (on) c.accent.copy(alpha = .18f) else c.card)
                    .border(1.dp, if (on) c.accent else c.separator, shape)
                    .pressable { onSelect(if (on) null else t) }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t.emoji, style = Type.callout)
                Spacer(Modifier.width(6.dp))
                Text(t.label, style = Type.footnote.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal), color = c.label)
            }
        }
    }
}

/** The eight moods as a 4 x 2 grid of picture + NAME, so every artwork is readable. Tap the selected one again to clear it. */
@Composable
fun MoodGrid(selected: Mood?, onSelect: (Mood?) -> Unit, modifier: Modifier = Modifier) {
    val c = T.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Mood.entries.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { m ->
                    val on = selected == m
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.weight(1f).clip(shape).background(if (on) m.tint.copy(alpha = .20f) else c.card)
                            .border(if (on) 1.5.dp else 1.dp, if (on) m.tint else c.separator, shape)
                            .pressable { onSelect(if (on) null else m) }.padding(vertical = 8.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Art(m.art, 44.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(m.label, style = Type.caption.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium), color = c.label, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}


/** 1–5 stars for place reviews. [rating] may be fractional for averages (shown rounded). Pass [onChange] to make it tappable. */
@Composable
fun StarRating(rating: Double?, modifier: Modifier = Modifier, size: Dp = 20.dp, onChange: ((Int?) -> Unit)? = null) {
    val c = T.colors
    val whole = Math.round(rating ?: 0.0).toInt()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (onChange != null) 2.dp else 0.dp)) {
        for (i in 1..5) {
            val on = whole >= i
            val tap = if (onChange != null) Modifier.size(size + 12.dp).pressable { onChange(if (whole == i && rating == i.toDouble()) null else i) } else Modifier.size(size)
            Box(tap, contentAlignment = Alignment.Center) {
                Icon(
                    if (on) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (onChange != null) "$i star${if (i == 1) "" else "s"}" else null,
                    tint = if (on) c.special else c.tertiary, modifier = Modifier.size(size),
                )
            }
        }
    }
}
