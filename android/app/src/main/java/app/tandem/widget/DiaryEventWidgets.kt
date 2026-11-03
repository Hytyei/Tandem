package app.tandem.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.Text
import app.tandem.R
import app.tandem.data.*
import java.time.format.DateTimeFormatter

private const val ME = 0xFF9C8CFF.toInt()
private const val PARTNER = 0xFFFF8FA3.toInt()
private const val GOLD = 0xFFFFC857.toInt()

/** The painted dusk behind the diary and event widgets, sized to the widget. */
@Composable
private fun DuskBackground(tint: Int, seed: Int) {
    val size = LocalSize.current
    Image(
        ImageProvider(WidgetArt.dusk(LocalContext.current, size.width.value, size.height.value, tint, seed)),
        contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize(),
    )
}

// ===================================================================== diary wall
class DiaryWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val kind = resolveWidgetKind(context, id, WidgetKind.Diary)
        provideContent { WidgetContentFor(kind, data) }
    }
}

@Composable
private fun Byline(e: DiaryEntry, data: WidgetData) {
    val st = data.state!!
    val author = if (e.userId == st.me.id) st.me else st.couple.partner
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarDot(author, author?.id?.let { data.avatars[it] }, 18.dp)
        Spacer(GlanceModifier.width(6.dp))
        Text(author?.displayName?.substringBefore(' ') ?: "", style = WText.primary(11, true), maxLines = 1)
        Spacer(GlanceModifier.width(5.dp))
        Text(timeAgo(e.createdAt), style = WText.secondary(10), maxLines = 1)
    }
}

@Composable
internal fun DiaryContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val st = data.state!!
    val entries = st.diary
    val wide = LocalSize.current.width >= 220.dp
    val latest = entries.firstOrNull()
    Box(GlanceModifier.fillMaxSize().clickable(openAppAction())) {
        DuskBackground(if (latest != null && latest.userId != st.me.id) PARTNER else ME, 11)
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Diary", style = WText.secondary(11), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                RefreshButton(size = 22.dp)
            }
            if (latest == null) {
                Spacer(GlanceModifier.defaultWeight())
                Text("No notes yet", style = WText.serif(16), maxLines = 1)
                Text("Pin the first one in the app", style = WText.secondary(11), maxLines = 2)
            } else if (wide && entries.size > 1) {
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                        Text(latest.text, style = WText.serif(15), maxLines = 3)
                        Spacer(GlanceModifier.defaultWeight())
                        Byline(latest, data)
                    }
                    Spacer(GlanceModifier.width(10.dp))
                    // the note before it, smaller, on a card
                    Column(GlanceModifier.defaultWeight().fillMaxHeight().background(ImageProvider(R.drawable.widget_surface)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(entries[1].text, style = WText.primary(12), maxLines = 3)
                        Spacer(GlanceModifier.defaultWeight())
                        Byline(entries[1], data)
                    }
                }
            } else {
                Spacer(GlanceModifier.height(4.dp))
                Text(latest.text, style = WText.serif(if (wide) 16 else 14), maxLines = if (wide) 2 else 3, modifier = GlanceModifier.defaultWeight())
                Byline(latest, data)
            }
        }
    }
}

class DiaryReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = DiaryWidget() }

// ===================================================================== events
class EventWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val kind = resolveWidgetKind(context, id, WidgetKind.Event)
        provideContent { WidgetContentFor(kind, data) }
    }
}

/** The days left, set inside a glowing ring that closes as the day gets closer. */
@Composable
private fun CountdownBlock(days: Long, special: Boolean, size: androidx.compose.ui.unit.Dp) {
    Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
        Image(ImageProvider(WidgetArt.ring(days, special)), contentDescription = null, modifier = GlanceModifier.size(size))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (days > 1) {
                Text("$days", style = WText.serif(if (size > 70.dp) 24 else 20), maxLines = 1)
                Text("days", style = WText.secondary(9), maxLines = 1)
            } else Text(if (days <= 0) "Today" else "Tmrw", style = WText.serif(13), maxLines = 1)
        }
    }
}

@Composable
internal fun EventContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val list = upcoming(data.state!!.events)
    val wide = LocalSize.current.width >= 220.dp
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM")
    if (list.isEmpty()) { MessageWidget("No plans yet", "Add one in the app"); return }
    val (first, firstDate) = list.first()
    val days = daysUntil(firstDate)
    Box(GlanceModifier.fillMaxSize().clickable(openAppAction())) {
        DuskBackground(if (first.isSpecial) GOLD else PARTNER, 23)
        val label = if (first.isSpecial) "Special day" else "Next up"
        val date = firstDate.format(fmt) + (first.time?.let { " · $it" } ?: "")
        if (wide) Row(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                Text(label, style = WText.secondary(11), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Text(first.title, style = WText.serif(18), maxLines = 2)
                Text(date, style = WText.secondary(10), maxLines = 1)
                if (list.size > 1) {
                    val (e, d) = list[1]
                    Spacer(GlanceModifier.height(4.dp))
                    Text("Then ${e.title}, ${countdownLabel(daysUntil(d)).replaceFirstChar { it.lowercase() }}", style = WText.secondary(10), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.width(8.dp))
            CountdownBlock(days, first.isSpecial, 80.dp)
        } else Column(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            // narrow: the ring on top, the words underneath at full width
            Row(GlanceModifier.fillMaxWidth()) {
                Text(label, style = WText.secondary(11), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                CountdownBlock(days, first.isSpecial, 64.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(first.title, style = WText.serif(16), maxLines = 2)
            Text(date, style = WText.secondary(10), maxLines = 1)
        }
    }
}

class EventReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = EventWidget() }
