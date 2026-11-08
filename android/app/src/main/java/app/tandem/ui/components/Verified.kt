package app.tandem.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tandem.data.User
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Accounts that carry the verified badge (matched on username, any case). */
private val VERIFIED = setOf("hytyei", "sanaz")

val User.verified: Boolean get() = username.lowercase() in VERIFIED

/** The verified badge: a small scalloped seal with a check, lit from the top left. */
@Composable
fun VerifiedBadge(size: Dp = 16.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).semantics { contentDescription = "Verified" }) {
        val r = this.size.minDimension / 2f
        val ctr = center
        val seal = Path().apply {
            val n = 12
            for (i in 0..n * 2) {
                val a = (i / (n * 2f)) * 2f * PI.toFloat() - PI.toFloat() / 2f
                val rr = if (i % 2 == 0) r else r * .84f
                val p = Offset(ctr.x + cos(a) * rr, ctr.y + sin(a) * rr)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(seal, Brush.radialGradient(listOf(Color(0xFFB8DCFF), Color(0xFF5AA8FF), Color(0xFF3B7FE8)), center = ctr + Offset(-r * .35f, -r * .4f), radius = r * 1.6f))
        val check = Path().apply {
            moveTo(ctr.x - r * .36f, ctr.y + r * .02f)
            lineTo(ctr.x - r * .09f, ctr.y + r * .3f)
            lineTo(ctr.x + r * .4f, ctr.y - r * .26f)
        }
        drawPath(check, Color.White, style = Stroke(r * .2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
