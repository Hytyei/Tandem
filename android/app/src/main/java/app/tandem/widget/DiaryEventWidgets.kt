package app.tandem.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
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

// ===================================================================== diary wall
class DiaryWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent { DiaryContent(data) }
    }
}

@Composable
private fun NoteBlock(e: DiaryEntry, data: WidgetData, lines: Int, modifier: GlanceModifier = GlanceModifier) {
    val st = data.state!!
    val author = if (e.userId == st.me.id) st.me else st.couple.partner
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarDot(author, author?.id?.let { data.avatars[it] }, 18.dp)
            Spacer(GlanceModifier.width(5.dp))
            Text(author?.displayName?.substringBefore(' ') ?: "", style = WText.primary(12, true), maxLines = 1)
            Spacer(GlanceModifier.width(5.dp))
            Text(timeAgo(e.createdAt), style = WText.secondary(10), maxLines = 1)
        }
        Spacer(GlanceModifier.height(3.dp))
        Text(e.text, style = WText.primary(13), maxLines = lines)
    }
}

@Composable
private fun DiaryContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val entries = data.state!!.diary
    val wide = LocalSize.current.width >= 220.dp
    WidgetFrame {
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("DIARY WALL", style = WText.accent(10), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                RefreshButton()
            }
            if (entries.isEmpty()) {
                Text("No notes yet — pin the first one", style = WText.secondary(12), maxLines = 3)
            } else if (wide && entries.size > 1) {
                Row(GlanceModifier.fillMaxWidth()) {
                    NoteBlock(entries[0], data, 3, GlanceModifier.defaultWeight())
                    Spacer(GlanceModifier.width(12.dp))
                    NoteBlock(entries[1], data, 3, GlanceModifier.defaultWeight())
                }
            } else {
                NoteBlock(entries[0], data, if (wide) 3 else 4)
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
        provideContent { EventContent(data) }
    }
}

@Composable
private fun CountdownBlock(days: Long) {
    if (days > 1) {
        Text("$days", style = WText.accent(30), maxLines = 1)
        Text("days", style = WText.secondary(11), maxLines = 1)
    } else {
        Text(countdownLabel(days), style = WText.accent(18), maxLines = 1)
    }
}

@Composable
private fun EventContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val list = upcoming(data.state!!.events)
    val wide = LocalSize.current.width >= 220.dp
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM")
    WidgetFrame {
        if (list.isEmpty()) {
            MessageWidget("No events yet", "Add one in the app")
            return@WidgetFrame
        }
        val (first, firstDate) = list.first()
        val days = daysUntil(firstDate)
        Row(GlanceModifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Text(first.emoji, style = WText.primary(if (wide) 26 else 22), maxLines = 1)
                Text(first.title, style = WText.primary(if (wide) 15 else 13, true), maxLines = 2)
                Text(firstDate.format(fmt) + (first.time?.let { " · $it" } ?: ""), style = WText.secondary(10), maxLines = 1)
            }
            if (wide && list.size > 1) {
                Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    list.drop(1).take(2).forEach { (e, d) ->
                        Row(GlanceModifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.emoji, style = WText.primary(14), maxLines = 1)
                            Spacer(GlanceModifier.width(5.dp))
                            Column {
                                Text(e.title, style = WText.primary(12), maxLines = 1)
                                Text(countdownLabel(daysUntil(d)), style = WText.accent(11, false), maxLines = 1)
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxHeight()) {
                RefreshButton()
                CountdownBlock(days)
            }
        }
    }
}

class EventReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = EventWidget() }
