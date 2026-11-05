package app.tandem.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type

private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** 0 → 1 once, when a chart first appears, so it draws itself in. */
@Composable
private fun reveal(key: Any? = Unit, ms: Int = 900): Float {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, tween(ms, easing = EaseOut)) }
    return a.value
}

/**
 * A ring split between the two of you (and the draws), with the score in the middle. The bigger share leads;
 * draws sit quietly in a neutral tone.
 */
@Composable
fun DuelRing(mine: Int, theirs: Int, draws: Int, mineColor: Color, theirColor: Color, caption: String, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val c = T.colors
    val p = reveal(Triple(mine, theirs, draws))
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * .085f
            val inset = stroke / 2 + 4.dp.toPx()
            val box = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val tl = Offset(inset, inset)
            drawArc(Color.White.copy(alpha = .08f), 0f, 360f, false, tl, box, style = Stroke(stroke))
            val total = (mine + theirs + draws).toFloat()
            if (total <= 0f) return@Canvas
            val gap = 4f
            var start = -90f
            listOf(mine to mineColor, theirs to theirColor, draws to Color.White.copy(alpha = .3f)).forEach { (n, col) ->
                if (n <= 0) return@forEach
                val sweep = (360f * n / total - gap).coerceAtLeast(1f) * p
                drawArc(col.copy(alpha = .25f), start, sweep, false, tl, box, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                drawArc(col, start, sweep, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
                start += 360f * n / total
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$mine", style = Type.numberHuge.copy(fontSize = 40.sp, lineHeight = 44.sp, fontFeatureSettings = "tnum"), color = mineColor)
                Text("  –  ", style = Type.title, color = c.tertiary)
                Text("$theirs", style = Type.numberHuge.copy(fontSize = 40.sp, lineHeight = 44.sp, fontFeatureSettings = "tnum"), color = theirColor)
            }
            Text(caption, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary)
        }
    }
}

/**
 * Two lines over the same steps (yours and your partner's), on a muted grid, with a soft fill under each and a lit
 * dot at the end. [a] and [b] can contain nulls where that person has no value.
 */
@Composable
fun DuoLines(a: List<Float?>, b: List<Float?>, aColor: Color, bColor: Color, modifier: Modifier = Modifier, height: Dp = 170.dp, yLabel: (Float) -> String = { it.toInt().toString() }) {
    val c = T.colors
    val p = reveal(a.size to b.size, 1100)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val n = maxOf(a.size, b.size)
        val max = (a.filterNotNull() + b.filterNotNull()).maxOrNull()?.coerceAtLeast(1f) ?: 1f
        val left = 30.dp.toPx(); val right = 10.dp.toPx(); val top = 10.dp.toPx(); val bottom = 8.dp.toPx()
        val w = size.width - left - right; val h = size.height - top - bottom
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.dp.toPx(); color = c.tertiary.toArgb(); typeface = android.graphics.Typeface.MONOSPACE; textAlign = android.graphics.Paint.Align.RIGHT
        }
        for (k in 0..3) {
            val y = top + h * k / 3f
            drawLine(Color.White.copy(alpha = .06f), Offset(left, y), Offset(left + w, y), 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(yLabel(max * (3 - k) / 3f), left - 6.dp.toPx(), y + 3.dp.toPx(), paint)
        }
        if (n == 0) return@Canvas
        fun x(i: Int) = left + if (n == 1) w / 2 else w * i / (n - 1f)
        fun y(v: Float) = top + h * (1f - v / max)
        clipRect(0f, 0f, left + w * p + 2f, size.height) {
            listOf(a to aColor, b to bColor).forEach { (vals, col) ->
                val pts = vals.mapIndexedNotNull { i, v -> v?.let { Offset(x(i), y(it)) } }
                if (pts.isEmpty()) return@forEach
                val line = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val p0 = pts[i - 1]; val p1 = pts[i]; val mx = (p0.x + p1.x) / 2
                        cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                    }
                }
                val fill = Path().apply { addPath(line); lineTo(pts.last().x, top + h); lineTo(pts[0].x, top + h); close() }
                drawPath(fill, Brush.verticalGradient(listOf(col.copy(alpha = .22f), Color.Transparent), startY = top, endY = top + h))
                drawPath(line, col, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val last = pts.last()
                drawCircle(col.copy(alpha = .3f), 7.dp.toPx(), last)
                drawCircle(Color.White, 3.dp.toPx(), last)
            }
        }
    }
}

/**
 * One row of a head-to-head: yours grows left from the middle, your partner's right, with the numbers at the ends.
 */
@Composable
fun DuelBar(label: String, mine: Float, theirs: Float, max: Float, mineColor: Color, theirColor: Color, mineText: String, theirText: String, modifier: Modifier = Modifier, leading: @Composable (() -> Unit)? = null) {
    val c = T.colors
    val p = reveal(Triple(mine, theirs, max))
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(mineText, style = Type.callout.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"), color = mineColor, modifier = Modifier.weight(1f))
            if (leading != null) { leading(); Spacer(Modifier.width(6.dp)) }
            Text(label, style = Type.footnote, color = c.secondary, maxLines = 1)
            Text(theirText, style = Type.callout.copy(fontFamily = FontFamily.Monospace, fontFeatureSettings = "tnum"), color = theirColor, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Canvas(Modifier.fillMaxWidth().height(10.dp).padding(top = 4.dp)) {
            val mid = size.width / 2; val h = size.height; val r = CornerRadius(h / 2)
            drawRoundRect(Color.White.copy(alpha = .06f), Offset.Zero, size, r)
            val m = if (max <= 0f) 0f else (mine / max).coerceIn(0f, 1f) * (mid - 2f) * p
            val t = if (max <= 0f) 0f else (theirs / max).coerceIn(0f, 1f) * (mid - 2f) * p
            if (m > 0f) drawRoundRect(Brush.horizontalGradient(listOf(mineColor, mineColor.copy(alpha = .55f)), startX = mid - m, endX = mid), Offset(mid - 1f - m, 0f), Size(m, h), r)
            if (t > 0f) drawRoundRect(Brush.horizontalGradient(listOf(theirColor.copy(alpha = .55f), theirColor), startX = mid, endX = mid + t), Offset(mid + 1f, 0f), Size(t, h), r)
            drawLine(Color.White.copy(alpha = .25f), Offset(mid, -2f), Offset(mid, h + 2f), 1.dp.toPx())
        }
    }
}

/** A single share (0..1) as a lit ring, with the number in the middle. */
@Composable
fun RingGauge(value: Float?, color: Color, label: String, modifier: Modifier = Modifier, size: Dp = 92.dp) {
    val c = T.colors
    val p = reveal(value)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = this.size.minDimension * .09f
                val inset = stroke / 2 + 3.dp.toPx()
                val box = Size(this.size.width - inset * 2, this.size.height - inset * 2)
                drawArc(Color.White.copy(alpha = .08f), 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
                if (value != null && value > 0f) {
                    drawArc(color.copy(alpha = .25f), -90f, 360f * value * p, false, Offset(inset, inset), box, style = Stroke(stroke * 2.2f, cap = StrokeCap.Round))
                    drawArc(color, -90f, 360f * value * p, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Text(value?.let { "${(it * 100).toInt()}%" } ?: "–", style = Type.title.copy(fontSize = 20.sp, fontFeatureSettings = "tnum"), color = c.label)
        }
        Text(label, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

/** A small legend: a dot and a name for each line or share. */
@Composable
fun ChartLegend(vararg items: Pair<String, Color>, modifier: Modifier = Modifier) {
    val c = T.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (name, col) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(8.dp)) { drawCircle(col) }
                Spacer(Modifier.width(6.dp))
                Text(name, style = Type.caption.copy(fontWeight = FontWeight.Normal), color = c.secondary)
            }
        }
    }
}
