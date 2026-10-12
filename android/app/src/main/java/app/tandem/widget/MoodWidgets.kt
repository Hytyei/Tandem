package app.tandem.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.Text
import app.tandem.R
import app.tandem.data.*

private fun ago(e: MoodEntry?): String = e?.let { timeAgo(it.createdAt) } ?: "—"
private fun art(e: MoodEntry?): Int = e?.widgetArtRes() ?: R.drawable.w_mood_calm
/** Headline = the activity if there is one (that's what the artwork shows), else the mood. */
private fun mainLabel(e: MoodEntry?): String =
    e?.let { Activity.from(it.activity)?.label ?: Mood.from(it.mood)?.label } ?: "No update yet"
private fun subLabel(e: MoodEntry?, wide: Boolean): String {
    if (e == null) return "—"
    val feeling = if (Activity.from(e.activity) != null) Mood.from(e.mood)?.label else null
    return listOfNotNull(feeling, timeAgo(e.createdAt)).joinToString(" · ")
}

// ===================================================================== partner only
class PartnerMoodWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent { PartnerContent(data) }
    }
}

@Composable
private fun PartnerContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val st = data.state!!
    val partner = st.couple.partner
    val mood = partner?.let { st.latestMoods[it.id] }
    val wide = androidx.glance.LocalSize.current.width >= 220.dp
    val name = partner?.displayName?.substringBefore(' ') ?: "Partner"

    WidgetFrame(art = art(mood)) {
        Column(GlanceModifier.fillMaxSize().padding(if (wide) 14.dp else 11.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AvatarDot(partner, partner?.id?.let { data.avatars[it] }, 22.dp)
                Spacer(GlanceModifier.width(6.dp))
                Text(name, style = WText.onArt(12), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                RefreshButton(onArt = true)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(mainLabel(mood), style = WText.onArt(if (wide) 20 else 16), maxLines = 1)
            if (wide && !mood?.note.isNullOrBlank()) Text("“${mood!!.note}”", style = WText.onArtDim(12), maxLines = 1)
            Text(subLabel(mood, wide), style = WText.onArtDim(if (wide) 12 else 10), maxLines = 1)
        }
    }
}

class PartnerMoodReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PartnerMoodWidget() }

// ===================================================================== both partners
class BothMoodWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent { BothContent(data) }
    }
}

@Composable
private fun PersonHalf(user: User?, mood: MoodEntry?, data: WidgetData, wide: Boolean, showRefresh: Boolean, modifier: GlanceModifier) {
    Box(modifier.cornerRadius(20.dp)) {
        ArtFill(art(mood), 20.dp)
        Column(GlanceModifier.fillMaxSize().padding(if (wide) 10.dp else 7.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AvatarDot(user, user?.id?.let { data.avatars[it] }, if (wide) 22.dp else 18.dp)
                if (wide) {
                    Spacer(GlanceModifier.width(5.dp))
                    Text(user?.displayName?.substringBefore(' ') ?: "—", style = WText.onArt(12), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                } else Spacer(GlanceModifier.defaultWeight())
                if (showRefresh) RefreshButton(onArt = true, size = if (wide) 26.dp else 22.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(mainLabel(mood), style = WText.onArt(if (wide) 14 else 11), maxLines = 1)
            Text(ago(mood), style = WText.onArtDim(if (wide) 11 else 9), maxLines = 1)
        }
    }
}

@Composable
private fun BothContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val st = data.state!!
    val partner = st.couple.partner
    val wide = androidx.glance.LocalSize.current.width >= 220.dp
    val myMood = st.latestMoods[st.me.id]
    val partnerMood = partner?.let { st.latestMoods[it.id] }

    Box(GlanceModifier.fillMaxSize().clickable(openAppAction())) {
        Row(GlanceModifier.fillMaxSize()) {
            PersonHalf(st.me, myMood, data, wide, false, GlanceModifier.defaultWeight().fillMaxHeight())
            Spacer(GlanceModifier.width(4.dp))
            PersonHalf(partner, partnerMood, data, wide, true, GlanceModifier.defaultWeight().fillMaxHeight())
        }
    }
}

class BothMoodReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BothMoodWidget() }
