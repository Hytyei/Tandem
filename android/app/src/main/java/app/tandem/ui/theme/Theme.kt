package app.tandem.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** iOS-flavoured semantic palette (grouped backgrounds, system label colours, systemPink accent). */
@Immutable
data class TandemColors(
    val background: Color,
    val card: Color,
    val cardRaised: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    val accent: Color,
    val accentSoft: Color,
    val me: Color,
    val partner: Color,
    val danger: Color,
    val special: Color,     // gold accent for special days
    val sheet: Color,
    val glow: Color,
    val sky: List<Color>,
    val isDark: Boolean,
)

val LightColors = TandemColors(
    background = Color(0xFFF5EDFA), card = Color(0xB8FFFFFF), cardRaised = Color(0xE6FFFFFF),
    label = Color(0xFF2A2058), secondary = Color(0xFF6F678F), tertiary = Color(0xFFA59FC4),
    separator = Color(0x26231A55), accent = Color(0xFFF2476F), accentSoft = Color(0x1FF2476F),
    me = Color(0xFF6A5CF0), partner = Color(0xFFF2476F), danger = Color(0xFFE5384F), special = Color(0xFFD98A00),
    sheet = Color(0xFFF9F4FD), glow = Color(0xFFFFB59A),
    sky = listOf(Color(0xFFD2C8FF), Color(0xFFF0D2EA), Color(0xFFFFE6D6)),
    isDark = false,
)

val DarkColors = TandemColors(
    background = Color(0xFF110C2C), card = Color(0x1FFFFFFF), cardRaised = Color(0x33FFFFFF),
    label = Color(0xFFF6F2FF), secondary = Color(0xFFC4BDE6), tertiary = Color(0xFF9D95C9),
    separator = Color(0x3DFFFFFF), accent = Color(0xFFFF8FA3), accentSoft = Color(0x29FF8FA3),
    me = Color(0xFF9C8CFF), partner = Color(0xFFFF8FA3), danger = Color(0xFFFF6B7D), special = Color(0xFFFFC857),
    sheet = Color(0xFF1A1550), glow = Color(0xFFFF8F8A),
    sky = listOf(Color(0xFF171250), Color(0xFF2C2278), Color(0xFF0F0B2E)),
    isDark = true,
)

val LocalTandem = staticCompositionLocalOf { LightColors }

object T {
    val colors: TandemColors @Composable get() = LocalTandem.current
}

private val Sans = FontFamily.SansSerif
private val Serif = FontFamily.Serif

object Type {
    val largeTitle = TextStyle(fontFamily = Serif, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val title = TextStyle(fontFamily = Serif, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp)
    val headline = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal)
    val callout = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val footnote = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val numberHuge = TextStyle(fontFamily = Serif, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
}

@Composable
fun TandemTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkColors else LightColors
    val scheme = if (dark) darkColorScheme(
        primary = c.accent, onPrimary = Color.White, background = c.background, onBackground = c.label,
        surface = c.sheet, onSurface = c.label, surfaceVariant = c.sheet, onSurfaceVariant = c.secondary,
        surfaceContainerHigh = c.sheet, surfaceContainerHighest = c.sheet, outline = c.separator,
    ) else lightColorScheme(
        primary = c.accent, onPrimary = Color.White, background = c.background, onBackground = c.label,
        surface = c.sheet, onSurface = c.label, surfaceVariant = c.sheet, onSurfaceVariant = c.secondary,
        surfaceContainerHigh = c.sheet, surfaceContainerHighest = c.sheet, outline = c.separator,
    )
    CompositionLocalProvider(LocalTandem provides c) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(bodyLarge = Type.body, bodyMedium = Type.callout, titleLarge = Type.title, labelLarge = Type.headline),
            shapes = Shapes(
                small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(26.dp), extraLarge = RoundedCornerShape(32.dp),
            ),
            content = content,
        )
    }
}
