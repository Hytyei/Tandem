package app.tandem.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
import app.tandem.data.Pet
import app.tandem.data.PetCatalog
import app.tandem.ui.galaxy.GalaxyBitmap
import app.tandem.ui.galaxy.GalaxyGen
import app.tandem.ui.pets.PetPose
import app.tandem.ui.pets.drawPet
import app.tandem.ui.pets.look
import java.util.Calendar
import java.util.Locale

// ===================================================================== our galaxy (the old planet widget, same receiver)
class PlanetWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val kind = resolveWidgetKind(context, id, WidgetKind.Planet)
        provideContent { WidgetContentFor(kind, data) }
    }
}
class PlanetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PlanetWidget() }

@Composable
internal fun GalaxyContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val cos = data.state!!.cosmos
    if (cos == null) { MessageWidget("Your galaxy", "Open it once in the app"); return }
    val size = LocalSize.current
    val ctx = LocalContext.current
    val d = ctx.resources.displayMetrics.density
    // painted at most 640 px wide, so the widget stays light
    val k = minOf(1f, 640f / (size.width.value * d))
    val w = (size.width.value * d * k).toInt().coerceAtLeast(120); val h = (size.height.value * d * k).toInt().coerceAtLeast(120)
    // the app's dusk behind, the galaxy's light on top
    val sky = WidgetArt.dusk(ctx, size.width.value, size.height.value, 0xFF7C6CFF.toInt(), (cos.seed % 997).toInt())
    runCatching {
        val g = GalaxyBitmap.render(cos.seed, cos.lifetime, w, h, yawDeg = 30f, zoom = .95f)
        android.graphics.Canvas(sky).drawBitmap(g, null, android.graphics.Rect(0, 0, sky.width, sky.height), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    }
    Box(GlanceModifier.fillMaxSize().clickable(openAppAction())) {
        Image(ImageProvider(sky), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Our galaxy", style = WText.serif(if (size.width >= 220.dp) 16 else 14), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                RefreshButton(size = 22.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(String.format(Locale.US, "%,d stars", GalaxyGen.starsFor(GalaxyGen.growth(cos.lifetime))), style = WText.secondary(11), maxLines = 1)
        }
    }
}

// ===================================================================== our pets
class PetWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = RESPONSIVE
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val kind = resolveWidgetKind(context, id, WidgetKind.Pets)
        provideContent { WidgetContentFor(kind, data) }
    }
}
class PetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = PetWidget() }

@Composable
internal fun PetContent(data: WidgetData) {
    if (GateOrNull(data)) return
    val pets = data.state!!.cosmos?.pets.orEmpty()
    if (pets.isEmpty()) { MessageWidget("Adopt a pet", "Look after it together in the app"); return }
    val size = LocalSize.current
    val ctx = LocalContext.current
    // the one who needs you most, or a different one each day
    val needy = pets.minByOrNull { minOf(it.hunger, it.thirst, it.love) }!!
    val pet = if (minOf(needy.hunger, needy.thirst, needy.love) < 30) needy else pets[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % pets.size]
    val bmp = WidgetArt.dusk(ctx, size.width.value, size.height.value, 0xFFFFB08A.toInt(), pet.id.hashCode())
    runCatching { paintPet(bmp, pet) }
    val low = minOf(pet.hunger, pet.thirst, pet.love)
    Box(GlanceModifier.fillMaxSize().clickable(openAppAction())) {
        Image(ImageProvider(bmp), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(pet.name, style = WText.serif(if (size.width >= 220.dp) 16 else 14), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                RefreshButton(size = 22.dp)
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                when {
                    pet.mood == "sulking" -> "Sulking. Come say sorry"
                    low < 30 -> when (low) { pet.hunger -> "Hungry"; pet.thirst -> "Thirsty"; else -> "Missing you" }
                    else -> PetCatalog.moodLabel(pet.mood)
                },
                style = if (low < 30 || pet.mood == "sulking") WText.accent(11, false) else WText.secondary(11), maxLines = 1,
            )
        }
    }
}

private fun paintPet(bmp: Bitmap, pet: Pet) {
    val cv = androidx.compose.ui.graphics.Canvas(android.graphics.Canvas(bmp))
    val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
    val side = minOf(w * .78f, h * .72f)
    val pose = PetPose().apply { droop = when (pet.mood) { "sulking" -> 1f; "grumpy" -> .5f; else -> 0f } }
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, cv, Size(w, h)) {
        translate((w - side) / 2f, h - side - h * .04f) { drawPet(pet.look(), pose, side, side, sketch = false) }
    }
}
