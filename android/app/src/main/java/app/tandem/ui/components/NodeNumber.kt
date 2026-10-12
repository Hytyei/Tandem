package app.tandem.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A number drawn as a little graph: every digit is a path of connected nodes on a 3 x 5 grid
 * (think constellation / circuit diagram). The faint dots are the unused grid points. Lines draw in, nodes pop in.
 */
private val DIGIT_PATHS: Map<Char, List<List<Pair<Int, Int>>>> = mapOf(
    '0' to listOf(listOf(0 to 0, 2 to 0, 2 to 4, 0 to 4, 0 to 0)),
    '1' to listOf(listOf(0 to 1, 1 to 0, 1 to 4)),
    '2' to listOf(listOf(0 to 0, 2 to 0, 2 to 2, 0 to 2, 0 to 4, 2 to 4)),
    '3' to listOf(listOf(0 to 0, 2 to 0, 2 to 4, 0 to 4), listOf(0 to 2, 2 to 2)),
    '4' to listOf(listOf(0 to 0, 0 to 2, 2 to 2), listOf(2 to 0, 2 to 4)),
    '5' to listOf(listOf(2 to 0, 0 to 0, 0 to 2, 2 to 2, 2 to 4, 0 to 4)),
    '6' to listOf(listOf(2 to 0, 0 to 0, 0 to 4, 2 to 4, 2 to 2, 0 to 2)),
    '7' to listOf(listOf(0 to 0, 2 to 0, 2 to 4)),
    '8' to listOf(listOf(0 to 0, 2 to 0, 2 to 4, 0 to 4, 0 to 0), listOf(0 to 2, 2 to 2)),
    '9' to listOf(listOf(2 to 2, 0 to 2, 0 to 0, 2 to 0, 2 to 4, 0 to 4)),
)

@Composable
fun NodeNumber(
    value: Long,
    modifier: Modifier = Modifier,
    digitHeight: Dp = 84.dp,
    from: Color,
    to: Color,
    ghost: Color,
) {
    val text = value.coerceAtLeast(0).toString()
    val n = text.length
    val progress = remember(value) { Animatable(0f) }
    LaunchedEffect(value) { progress.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }

    val density = LocalDensity.current
    val cell = digitHeight / 4f                       // grid spacing
    val nodeR = cell * 0.17f
    val pad = nodeR * 1.6f
    val gap = cell * 1.1f
    val width = pad * 2 + cell * 2 * n + gap * (n - 1)
    val height = digitHeight + pad * 2

    Canvas(modifier.size(width, height)) {
        val c = with(density) { cell.toPx() }
        val r = with(density) { nodeR.toPx() }
        val p = with(density) { pad.toPx() }
        val g = with(density) { gap.toPx() }
        val brush = Brush.linearGradient(listOf(from, to), start = Offset(0f, 0f), end = Offset(size.width, size.height))
        val stroke = r * 0.9f
        text.forEachIndexed { i, ch ->
            val ox = p + i * (2 * c + g)
            val oy = p
            fun pt(x: Int, y: Int) = Offset(ox + x * c, oy + y * c)
            // unused grid points
            for (gx in 0..2) for (gy in 0..4) drawCircle(ghost, r * 0.45f, pt(gx, gy))

            val paths = DIGIT_PATHS[ch] ?: return@forEachIndexed
            val segments = paths.sumOf { it.size - 1 }
            // each digit starts a little after the previous one
            val start = i.toFloat() / (n + 1)
            val local = ((progress.value - start) / (2f / (n + 1))).coerceIn(0f, 1f)
            var k = 0
            for (path in paths) {
                for (j in 0 until path.size - 1) {
                    val f = (local * segments - k).coerceIn(0f, 1f)
                    if (f > 0f) {
                        val a = pt(path[j].first, path[j].second)
                        val b = pt(path[j + 1].first, path[j + 1].second)
                        drawLine(brush, a, Offset(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f), stroke, StrokeCap.Round)
                    }
                    k++
                }
            }
            // nodes (pop in as the line reaches them)
            k = 0
            for (path in paths) {
                path.forEachIndexed { j, (x, y) ->
                    val reach = (local * segments - (k + j - 1) - 0.0f).coerceIn(0f, 1f)
                    val s = if (j == 0) (local * segments * 3f).coerceIn(0f, 1f) else reach
                    if (s > 0f) {
                        val o = pt(x, y)
                        drawCircle(brush, r * 1.25f * s, o)
                        drawCircle(Color.White.copy(alpha = .92f), r * 0.55f * s, o)
                    }
                }
                k += path.size - 1
            }
        }
    }
}
