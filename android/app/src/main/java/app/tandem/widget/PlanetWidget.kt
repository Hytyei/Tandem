package app.tandem.widget

import android.content.Context
import android.graphics.Canvas
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
import androidx.glance.layout.*
import androidx.glance.text.Text
import app.tandem.data.PlanetSummary
import app.tandem.data.planetStageLabel
import app.tandem.ui.components.PlanetLook
import app.tandem.ui.components.PlanetPainter
import app.tandem.ui.components.PlanetSurface

// ===================================================================== our planet
class PlanetWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val kind = resolveWidgetKind(context, id, WidgetKind.Planet)
        provideContent { WidgetContentFor(kind, data) }
    }
}

private fun lookOf(p: PlanetSummary, me: String, them: String?) = PlanetLook(
    ocean = (p.biomes["ocean"] ?: 25.0).toFloat(), forest = (p.biomes["forest"] ?: 25.0).toFloat(),
    crystal = (p.biomes["crystal"] ?: 25.0).toFloat(), aurora = (p.biomes["aurora"] ?: 25.0).toFloat(),
    ice = p.ice.toFloat(), atmosphere = p.atmosphere.toFloat(), life = p.life.toFloat(),
    sunMe = (p.suns[me] ?: 60.0).toFloat(), sunThem = (p.suns[them ?: ""] ?: 60.0).toFloat(),
    moons = p.moons, species = p.alive, stage = p.stage, seed = (p.created.hashCode() and 0x7FFFFFFF) % 997 + 1,
)

@Composable
internal fun PlanetContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val st = data.state!!
    val p = st.planet
    if (p == null) { MessageWidget("Meet your planet", "Open it once in the app"); return }
    val partner = st.couple.partner
    val size = LocalSize.current
    val ctx = LocalContext.current
    val wide = size.width >= 220.dp
    // the dusk, with the planet (and both suns when there's room) painted on top
    val bmp = WidgetArt.dusk(ctx, size.width.value, size.height.value, 0xFF9C8CFF.toInt(), 31)
    runCatching { PlanetSurface.prepare(lookOf(p, st.me.id, partner?.id)) }
    runCatching { PlanetPainter.draw(Canvas(bmp), bmp.width.toFloat(), bmp.height * if (wide) 1f else .9f, lookOf(p, st.me.id, partner?.id), 6f, showSuns = wide) }
    val spark = partner != null && partner.id in p.sparks && st.me.id !in p.sparks
    Box(GlanceModifier.fillMaxSize().clickable(openAppAction())) {
        Image(ImageProvider(bmp), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = WText.serif(if (wide) 16 else 14), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                RefreshButton(size = 22.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                if (spark) "A spark is waiting for you" else planetStageLabel(p.stage, p.quiet),
                style = if (p.stage == "thriving" && !spark) WText.secondary(11) else WText.accent(11, false), maxLines = 1,
            )
        }
    }
}

class PlanetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PlanetWidget() }
