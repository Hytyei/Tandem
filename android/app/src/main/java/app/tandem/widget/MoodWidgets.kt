package app.tandem.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
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

private fun ago(e: MoodEntry?): String = e?.let { timeAgo(it.createdAt) } ?: "No update yet"
private fun art(e: MoodEntry?): Int = e?.widgetArtRes() ?: R.drawable.w_mood_calm
/** Headline = the activity if there is one (that's what the artwork shows), else the mood. */
private fun mainLabel(e: MoodEntry?): String =
    e?.let { Activity.from(it.activity)?.label ?: Mood.from(it.mood)?.label } ?: "No update yet"
private fun subLabel(e: MoodEntry?): String {
    if (e == null) return "Nothing shared yet"
    val feeling = if (Activity.from(e.activity) != null) Mood.from(e.mood)?.label else null
    return listOfNotNull(feeling, timeAgo(e.createdAt)).joinToString(" · ")
}
/** The mood's colour, lifted a little so it glows on the dark. */
private fun moodGlow(e: MoodEntry?): Int = lerp(Mood.from(e?.mood)?.tint ?: androidx.compose.ui.graphics.Color(0xFF9C8CFF), androidx.compose.ui.graphics.Color.White, .3f).toArgb()

/** A person on a frosted chip: their picture and first name. */
@Composable
private fun NameChip(user: User?, data: WidgetData, small: Boolean) {
    Row(
        GlanceModifier.background(ImageProvider(R.drawable.widget_glass)).padding(start = 3.dp, end = if (small) 3.dp else 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarDot(user, user?.id?.let { data.avatars[it] }, if (small) 20.dp else 22.dp)
        if (!small) {
            Spacer(GlanceModifier.width(6.dp))
            Text(user?.displayName?.substringBefore(' ') ?: "—", style = WText.onArt(12), maxLines = 1)
        }
    }
}

// ===================================================================== partner only
class PartnerMoodWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val kind = resolveWidgetKind(context, id, WidgetKind.Partner)
        provideContent { WidgetContentFor(kind, data) }
    }
}

@Composable
internal fun PartnerContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val st = data.state!!
    val partner = st.couple.partner
    val mood = partner?.let { st.latestMoods[it.id] }
    val size = LocalSize.current
    val wide = size.width >= 220.dp
    val ctx = LocalContext.current

    Box(GlanceModifier.fillMaxSize().clickable(openAppAction())) {
        Image(ImageProvider(WidgetArt.art(ctx, art(mood), size.width.value, size.height.value)), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
        Column(GlanceModifier.fillMaxSize().padding(if (wide) 14.dp else 10.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NameChip(partner, data, small = !wide)
                Spacer(GlanceModifier.defaultWeight())
                RefreshButton(onArt = true)
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(ImageProvider(WidgetArt.dot(moodGlow(mood))), contentDescription = null, modifier = GlanceModifier.size(14.dp))
                Spacer(GlanceModifier.width(4.dp))
                Text(mainLabel(mood), style = WText.serif(if (wide) 22 else 17), maxLines = 1)
            }
            if (wide && !mood?.note.isNullOrBlank()) Text("“${mood!!.note}”", style = WText.onArtDim(12), maxLines = 1)
            Text(subLabel(mood), style = WText.onArtDim(if (wide) 12 else 10), maxLines = 1)
        }
    }
}

class PartnerMoodReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PartnerMoodWidget() }

// ===================================================================== both partners
class BothMoodWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val kind = resolveWidgetKind(context, id, WidgetKind.Both)
        provideContent { WidgetContentFor(kind, data) }
    }
}

/** One of you at the bottom of the shared picture: mood (with its colour) and when. */
@Composable
private fun PersonLine(mood: MoodEntry?, wide: Boolean, end: Boolean) {
    Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!end) { Image(ImageProvider(WidgetArt.dot(moodGlow(mood))), contentDescription = null, modifier = GlanceModifier.size(12.dp)); Spacer(GlanceModifier.width(3.dp)) }
            Text(mainLabel(mood), style = WText.serif(if (wide) 16 else 12), maxLines = 1)
            if (end) { Spacer(GlanceModifier.width(3.dp)); Image(ImageProvider(WidgetArt.dot(moodGlow(mood))), contentDescription = null, modifier = GlanceModifier.size(12.dp)) }
        }
        Text(ago(mood), style = WText.onArtDim(if (wide) 11 else 9), maxLines = 1)
    }
}

@Composable
internal fun BothContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val st = data.state!!
    val partner = st.couple.partner
    val size = LocalSize.current
    val wide = size.width >= 220.dp
    val ctx = LocalContext.current
    val myMood = st.latestMoods[st.me.id]
    val partnerMood = partner?.let { st.latestMoods[it.id] }

    val today = java.time.LocalDate.now()
    fun isToday(e: MoodEntry?) = e != null && java.time.Instant.ofEpochMilli(e.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today
    val both = isToday(myMood) && isToday(partnerMood)
    val pName = partner?.displayName?.substringBefore(' ') ?: "your partner"

    Box(GlanceModifier.fillMaxSize().clickable(openAppAction())) {
        Image(
            ImageProvider(WidgetArt.panorama(ctx, art(myMood), art(partnerMood), size.width.value, size.height.value)),
            contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize(),
        )
        Column(GlanceModifier.fillMaxSize().padding(if (wide) 12.dp else 9.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NameChip(st.me, data, small = !wide)
                Spacer(GlanceModifier.defaultWeight())
                if (wide) {
                    // the middle says whether you both shared today
                    Row(GlanceModifier.background(ImageProvider(R.drawable.widget_glass)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (both) { Image(ImageProvider(R.drawable.widget_link), contentDescription = null, modifier = GlanceModifier.size(width = 20.dp, height = 12.dp)); Spacer(GlanceModifier.width(5.dp)) }
                        Text(if (both) "Both today" else if (isToday(myMood)) "Waiting for $pName" else "Share yours", style = WText.onArt(11, false), maxLines = 1)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                } else if (both) Image(ImageProvider(R.drawable.widget_link), contentDescription = "You both shared a mood today", modifier = GlanceModifier.size(width = 22.dp, height = 13.dp))
                if (!wide) Spacer(GlanceModifier.defaultWeight())
                NameChip(partner, data, small = !wide)
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                PersonLine(myMood, wide, end = false)
                Spacer(GlanceModifier.defaultWeight())
                PersonLine(partnerMood, wide, end = true)
            }
        }
    }
}

class BothMoodReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BothMoodWidget() }
