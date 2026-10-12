package app.tandem.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import app.tandem.R
import app.tandem.ui.theme.T
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private fun DrawScope.hill(base: Float, amp: Float, phase: Float, freq: Float, top: Color, bottom: Color) {
    val p = Path()
    p.moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width + 8f) {
        val u = x / size.width
        val y = base + amp * sin(u * freq * 2f * PI.toFloat() + phase) + amp * 0.45f * sin(u * freq * 5.1f + phase * 1.7f)
        p.lineTo(x, y)
        x += 8f
    }
    p.lineTo(size.width, size.height)
    p.close()
    drawPath(p, Brush.verticalGradient(listOf(top, bottom), startY = base - amp, endY = size.height))
}

/**
 * The app's signature backdrop: a dusk sky with a drifting sun-glow, twinkling stars at night,
 * optional slow-parallax hills and a film-grain veil. `scene = true` adds the hills (sign-in, pairing).
 */
@Composable
fun DreamBackground(modifier: Modifier = Modifier, scene: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val c = T.colors
    val t = rememberInfiniteTransition(label = "dream")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val twinkle by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "twinkle")
    val grain: ImageBitmap = ImageBitmap.imageResource(R.drawable.grain)
    val stars = remember { List(52) { Triple(Random.nextFloat(), Random.nextFloat() * 0.5f, Random.nextFloat()) } }

    Box(
        modifier.fillMaxSize().background(c.background).drawBehind {
            drawRect(Brush.verticalGradient(c.sky))
            val r = size.width * 1.0f
            val center = Offset(size.width * (0.8f - 0.1f * drift), size.height * (0.14f + 0.03f * drift))
            drawCircle(Brush.radialGradient(listOf(c.glow.copy(alpha = if (c.isDark) 0.42f else 0.6f), Color.Transparent), center = center, radius = r), radius = r, center = center)
            if (c.isDark) {
                for ((x, y, ph) in stars) {
                    val a = 0.25f + 0.65f * (0.5f + 0.5f * sin((twinkle + ph) * 2f * PI.toFloat()))
                    drawCircle(Color.White.copy(alpha = a), radius = 1.2f + 1.6f * ph, center = Offset(x * size.width, y * size.height))
                }
            }
            if (scene) {
                val farTop = if (c.isDark) Color(0x883B2E9A) else Color(0xAAC9B6F5)
                val farBot = if (c.isDark) Color(0xCC1F1766) else Color(0xCCB59AEA)
                val midTop = if (c.isDark) Color(0xCC2A2078) else Color(0xCCA68BE8)
                val midBot = if (c.isDark) Color(0xFF150F48) else Color(0xFF8E70DA)
                val nearTop = if (c.isDark) Color(0xFF181050) else Color(0xFF7B5FCF)
                val nearBot = if (c.isDark) Color(0xFF0B0828) else Color(0xFF5A40B0)
                hill(size.height * 0.70f, size.height * 0.05f, 1.2f + 1.2f * drift, 1.1f, farTop, farBot)
                hill(size.height * 0.80f, size.height * 0.045f, 3.4f - 1.0f * drift, 1.5f, midTop, midBot)
                hill(size.height * 0.90f, size.height * 0.035f, 0.4f + 0.8f * drift, 1.9f, nearTop, nearBot)
            }
            drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)), alpha = if (c.isDark) 0.10f else 0.08f)
        },
        content = content,
    )
}
