package app.tandem.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.AppViewModel
import app.tandem.ui.components.LargeTitle
import app.tandem.ui.components.SoftCard
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type

/** Placeholder tab for the mini-games that are coming later. */
@Composable
fun GamesScreen(@Suppress("UNUSED_PARAMETER") vm: AppViewModel) {
    val c = T.colors
    Column(Modifier.fillMaxSize().padding(bottom = 130.dp)) {
        LargeTitle("Play", subtitle = "Little games for two")
        Spacer(Modifier.weight(1f))
        SoftCard(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), padding = 28.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(c.accent.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SportsEsports, null, tint = c.accent, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    "WORK IN PROGRESS", style = Type.caption.copy(letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold), color = c.accent,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accent.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 5.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text("Coming soon", style = Type.title, color = c.label)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Mini-games for the two of you are on their way. Check back after the next update.",
                    style = Type.callout, color = c.secondary, textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.weight(1.4f))
    }
}
