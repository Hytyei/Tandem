package app.tandem.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.tandem.MainActivity
import app.tandem.R
import app.tandem.TandemApp
import app.tandem.data.SyncResponse
import app.tandem.data.User
import app.tandem.data.Repository

val SMALL = DpSize(110.dp, 110.dp)
val WIDE = DpSize(250.dp, 110.dp)
val RESPONSIVE = SizeMode.Responsive(setOf(SMALL, WIDE))

/** The four widget types. [receiver] is the broadcast receiver class the home screen binds each one to. */
enum class WidgetKind(val receiver: Class<*>) {
    Partner(PartnerMoodReceiver::class.java), Both(BothMoodReceiver::class.java), Diary(DiaryReceiver::class.java), Event(EventReceiver::class.java),
}

/**
 * Which kind of widget the home screen really placed under [id], asked of Android itself (not of Glance's own bookkeeping).
 * Every widget renders the content of the kind it was added as, so a "both moods" widget can never turn into another widget.
 */
fun resolveWidgetKind(context: Context, id: GlanceId, fallback: WidgetKind): WidgetKind = try {
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
    val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider?.className
    WidgetKind.entries.firstOrNull { it.receiver.name == provider } ?: fallback
} catch (_: Exception) { fallback }

@Composable
fun WidgetContentFor(kind: WidgetKind, data: WidgetData) {
    when (kind) {
        WidgetKind.Partner -> PartnerContent(data)
        WidgetKind.Both -> BothContent(data)
        WidgetKind.Diary -> DiaryContent(data)
        WidgetKind.Event -> EventContent(data)
    }
}

object WidgetUpdater {
    /** Refreshes every placed widget, pairing each Android widget id with its own provider's widget class. */
    suspend fun updateAll(context: Context) {
        val awm = AppWidgetManager.getInstance(context)
        val glance = GlanceAppWidgetManager(context)
        for (kind in WidgetKind.entries) {
            val widget: GlanceAppWidget = when (kind) {
                WidgetKind.Partner -> PartnerMoodWidget(); WidgetKind.Both -> BothMoodWidget()
                WidgetKind.Diary -> DiaryWidget(); WidgetKind.Event -> EventWidget()
            }
            for (appWidgetId in awm.getAppWidgetIds(ComponentName(context, kind.receiver))) {
                try { widget.update(context, glance.getGlanceIdBy(appWidgetId)) } catch (_: Exception) { }
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = (context.applicationContext as TandemApp).repo
        try { repo.refresh() } catch (_: Exception) { WidgetUpdater.updateAll(context) }
    }
}

/** What every widget needs, resolved before composition because Glance content can't suspend. */
class WidgetData(val state: SyncResponse?, val avatars: Map<String, Bitmap?>)

suspend fun loadWidgetData(context: Context): WidgetData {
    val repo = (context.applicationContext as TandemApp).repo
    val state = repo.state.value
    val avatars = mutableMapOf<String, Bitmap?>()
    if (state != null) {
        for (u in listOfNotNull(state.me, state.couple.partner)) {
            avatars[u.id] = try { repo.widgetAvatar(u) } catch (_: Exception) { null }
        }
    }
    return WidgetData(state, avatars)
}

// ---------------------------------------------------------------- text styles
object WText {
    fun primary(size: Int = 14, bold: Boolean = false) =
        TextStyle(color = ColorProvider(R.color.widget_text), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    fun secondary(size: Int = 11) =
        TextStyle(color = ColorProvider(R.color.widget_secondary), fontSize = size.sp, fontWeight = FontWeight.Normal)
    fun accent(size: Int = 14, bold: Boolean = true) =
        TextStyle(color = ColorProvider(R.color.widget_accent), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    /** Text that sits on top of artwork (always white, the scrim keeps it readable). */
    fun onArt(size: Int = 14, bold: Boolean = true) =
        TextStyle(color = ColorProvider(R.color.white), fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    fun onArtDim(size: Int = 11) =
        TextStyle(color = ColorProvider(R.color.white_dim), fontSize = size.sp, fontWeight = FontWeight.Normal)
}

val WidgetCorner = 26.dp

@Composable
fun openAppAction() = actionStartActivity(android.content.Intent(LocalContext.current, MainActivity::class.java))

// ---------------------------------------------------------------- building blocks
/** Rounded widget shell. With [art] the picture fills the whole widget under a soft scrim. */
@Composable
fun WidgetFrame(art: Int? = null, content: @Composable () -> Unit) {
    val base = GlanceModifier.fillMaxSize().cornerRadius(WidgetCorner).clickable(openAppAction())
    if (art != null) {
        Box(modifier = base) {
            ArtFill(art)
            content()
        }
    } else {
        Box(modifier = base.background(ImageProvider(R.drawable.widget_bg))) { content() }
    }
}

/** Full-bleed artwork + bottom scrim. */
@Composable
fun ArtFill(art: Int, corner: Dp = WidgetCorner) {
    Image(ImageProvider(art), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = GlanceModifier.fillMaxSize().cornerRadius(corner))
    Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_scrim)).cornerRadius(corner)) {}
}

@Composable
fun RefreshButton(modifier: GlanceModifier = GlanceModifier, onArt: Boolean = false, size: Dp = 26.dp) {
    Box(
        modifier = modifier.size(size).clickable(actionRunCallback<RefreshAction>())
            .let { if (onArt) it.background(ImageProvider(R.drawable.widget_chip)) else it },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_refresh),
            contentDescription = "Refresh",
            colorFilter = ColorFilter.tint(ColorProvider(if (onArt) R.color.white else R.color.widget_secondary)),
            modifier = GlanceModifier.size(size - 11.dp),
        )
    }
}

@Composable
fun AvatarDot(user: User?, bitmap: Bitmap?, size: Dp) {
    if (bitmap != null) {
        Image(ImageProvider(bitmap), contentDescription = user?.displayName, modifier = GlanceModifier.size(size))
    } else {
        Box(
            modifier = GlanceModifier.size(size).background(ImageProvider(R.drawable.avatar_placeholder)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                user?.displayName?.trim()?.firstOrNull()?.uppercase() ?: "?",
                style = TextStyle(color = ColorProvider(R.color.white), fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
fun MessageWidget(title: String, body: String) {
    WidgetFrame {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(ImageProvider(R.drawable.ic_logo), contentDescription = "Tandem", contentScale = ContentScale.Fit,
                modifier = GlanceModifier.size(48.dp))
            Spacer(GlanceModifier.height(6.dp))
            Text(title, style = WText.primary(14, true), maxLines = 1)
            Text(body, style = WText.secondary(11), maxLines = 2)
        }
    }
}

/** Shared gate: returns an explanatory widget when there's nothing to show yet, else null. */
@Composable
fun GateOrNull(data: WidgetData): Boolean {
    val st = data.state
    return when {
        st == null -> { MessageWidget("Tandem", "Open the app to sign in"); true }
        !st.couple.paired -> { MessageWidget("Almost there", "Connect with your partner"); true }
        else -> false
    }
}
