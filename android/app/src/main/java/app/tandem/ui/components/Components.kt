package app.tandem.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.R
import app.tandem.TandemApp
import app.tandem.data.User
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/** iOS-style press feedback: springy scale-down plus a light haptic tick. */
fun Modifier.pressable(enabled: Boolean = true, haptic: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "press",
    )
    val feedback = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            if (haptic) feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Fades and slides a list item in, staggered by its index. */
@Composable
fun Modifier.staggerIn(index: Int, stepMs: Int = 50): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(12) * stepMs).toLong())
        progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
    }
    return this.graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 48f
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = T.colors.card,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = T.colors
    Column(
        modifier.clip(shape).background(color)
            .border(1.dp, if (c.isDark) Color.White.copy(alpha = .10f) else Color.White.copy(alpha = .7f), shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun LargeTitle(title: String, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) Text(subtitle.uppercase(), style = Type.caption.copy(letterSpacing = 0.6.sp), color = T.colors.secondary)
            Text(title, style = Type.largeTitle, color = T.colors.label)
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = Type.footnote.copy(letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium),
        color = T.colors.secondary,
        modifier = modifier.padding(start = 24.dp, bottom = 6.dp, top = 18.dp),
    )
}

/** Full vector artwork (the generated illustrations). */
@Composable
fun Art(@DrawableRes res: Int, size: Dp, modifier: Modifier = Modifier) {
    // The scenes are full-bleed paintings: show them as rounded, centre-cropped tiles.
    Image(
        painterResource(res), contentDescription = null,
        modifier = modifier.size(size).clip(RoundedCornerShape(size * 0.22f)),
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun Avatar(user: User?, size: Dp, ring: Color? = null, modifier: Modifier = Modifier) {
    val repo = (LocalContext.current.applicationContext as TandemApp).repo
    val url = user?.let { repo.avatarUrl(it) }
    val base = modifier.size(size).clip(CircleShape).let { if (ring != null) it.border(2.5.dp, ring, CircleShape) else it }
    if (url != null) {
        AsyncImage(model = url, contentDescription = user?.displayName, contentScale = ContentScale.Crop, modifier = base)
    } else {
        val seed = (user?.id?.hashCode() ?: 0)
        val hue = ((seed % 360) + 360) % 360
        val c1 = Color.hsv(hue.toFloat(), 0.45f, 0.98f)
        val c2 = Color.hsv(((hue + 40) % 360).toFloat(), 0.65f, 0.86f)
        Box(base.background(Brush.linearGradient(listOf(c1, c2))), contentAlignment = Alignment.Center) {
            Text(
                (user?.displayName?.trim()?.firstOrNull()?.uppercase() ?: "?"),
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp,
            )
        }
    }
}

@Composable
fun PrimaryButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, loading: Boolean = false,
) {
    val c = T.colors
    val bg = if (enabled) Brush.linearGradient(listOf(Color(0xFFF2476F), Color(0xFFFF8A6A))) else Brush.linearGradient(listOf(c.tertiary, c.tertiary))
    Box(
        modifier
            .fillMaxWidth().height(54.dp)
            .clip(RoundedCornerShape(18.dp)).background(bg)
            .pressable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(text, style = Type.headline, color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = T.colors.accent) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.13f)).pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Type.callout.copy(fontWeight = FontWeight.SemiBold), color = color) }
}

/** iOS segmented control with a sliding thumb. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = T.colors
    // Dark mode needs a clearly visible track and thumb (the old translucent ones disappeared on the dark sky).
    val track = if (c.isDark) Color.White.copy(alpha = 0.10f) else c.separator.copy(alpha = 0.12f)
    val thumb = if (c.isDark) Color.White.copy(alpha = 0.24f) else c.cardRaised
    BoxWithConstraints(modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(track).padding(3.dp)) {
        val segW = maxWidth / options.size
        val x by androidx.compose.animation.core.animateDpAsState(segW * selected, spring(0.75f, Spring.StiffnessMediumLow), label = "seg")
        Box(Modifier.offset(x = x).width(segW).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(thumb))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().pressable(haptic = false) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Type.callout.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (i == selected) c.label else c.secondary)
                }
            }
        }
    }
}

@Composable
fun Heart(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_heart), contentDescription = null, modifier = modifier.size(size))
}

/** The Tandem heart (same picture as the launcher icon). Use this wherever the app shows "itself", never an artwork. */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_logo), contentDescription = "Tandem", modifier = modifier.size(size))
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppLogo(84.dp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = Type.headline, color = T.colors.label)
        Spacer(Modifier.height(4.dp))
        Text(message, style = Type.callout, color = T.colors.secondary, textAlign = TextAlign.Center)
    }
}
