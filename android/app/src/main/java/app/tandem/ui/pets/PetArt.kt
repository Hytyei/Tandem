package app.tandem.ui.pets

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * The pets, drawn in code in a soft kawaii style: a thick warm outline with small hand-drawn gaps,
 * airbrushed fills, big glossy eyes, blush, pink paw beans. One rig per species; breeds and coats are
 * shapes and palettes on top of it. Everything is drawn in a 100 x 100 box (x from -50 to 50, the floor
 * at y = 93) and scaled to fit, so the same pet draws as a big companion or a small thumbnail.
 */

/** Everything the rig can move. 0 is rest. Set every frame by the stage. */
class PetPose {
    var breathe = 0f      // -1..1
    var blink = 0f        // 0 open .. 1 shut
    var happy = 0f        // eyes closed in a smile
    var lookX = 0f; var lookY = 0f
    var tilt = 0f         // head tilt, degrees
    var headDy = 0f       // head bob, units
    var earL = 0f; var earR = 0f   // twitch, -1..1
    var tail = 0f         // -1..1
    var mouth = 0f        // 0..1 open (meow, bark, chirp)
    var tongue = 0f       // 0..1 (lick, pant)
    var paw = 0f          // 0..1 front paw lifted to the mouth
    var knead = 0f        // -1..1 alternating front paws
    var squash = 0f       // -1 (stretch) .. 1 (squash)
    var hop = 0f          // 0..1 off the floor
    var cheeks = 0f       // 0..1 stuffed cheeks
    var nose = 0f         // 0..1 twitch
    var wing = 0f         // 0..1 open
    var droop = 0f        // 0..1 sulking
    var sleep = 0f        // 0..1 dozing
    var blush = 0f        // 0..1 extra pink

    fun reset() {
        breathe = 0f; blink = 0f; happy = 0f; lookX = 0f; lookY = 0f; tilt = 0f; headDy = 0f; earL = 0f; earR = 0f; tail = 0f
        mouth = 0f; tongue = 0f; paw = 0f; knead = 0f; squash = 0f; hop = 0f; cheeks = 0f; nose = 0f; wing = 0f; droop = 0f; sleep = 0f; blush = 0f
    }
}

/** A breed in one coat. */
data class PetLook(
    val species: String,
    val breed: String,
    val fur: Color,
    val light: Color,          // belly, muzzle, socks
    val mark: Color,           // stripes, points, patches, a second colour
    val inner: Color = Color(0xFFF6B2BA),   // inside the ears
    val outline: Color = OUTLINE,
    val eye: Color? = null,    // a tint in the eyes (blue for Siamese and huskies)
    val nose: Color = Color(0xFFF08E9E),
    val extra: Color = mark,   // parrots: the wing; a third colour elsewhere
    val extra2: Color = mark,  // parrots: the beak
    val extra3: Color = mark,  // parrots: the tail
) {
    val shade: Color get() = lerp(fur, Color(0xFF8A4A45), if (fur.luminance() < .2f) .18f else .26f)
}

private fun Color.luminance() = .2126f * red + .7152f * green + .0722f * blue

private val OUTLINE = Color(0xFF9E5A4B)
private val OUTLINE_DARK = Color(0xFF3E2A2B)
private val EYE = Color(0xFF2C1B20)
private val PINK = Color(0xFFF6A9B6)
private val BLUSH = Color(0xFFFF8FA3)
private val WHITE = Color(0xFFFFFCF8)

private fun c(v: Long) = Color(v)

/** The coats each breed comes in. Every breed has three. */
fun petCoats(species: String, breed: String): List<PetLook> {
    fun L(fur: Long, light: Long, mark: Long, eye: Long? = null, outline: Color = OUTLINE, inner: Long = 0xFFF6B2BA, nose: Long = 0xFFF08E9E, x: Long? = null, x2: Long? = null, x3: Long? = null) =
        PetLook(species, breed, c(fur), c(light), c(mark), c(inner), outline, eye?.let { c(it) }, c(nose), c(x ?: mark), c(x2 ?: mark), c(x3 ?: mark))
    return when (breed) {
        // cats
        "persian" -> listOf(L(0xFFF9F2EA, 0xFFFFFFFF, 0xFFE9DCCF), L(0xFFF3DCBC, 0xFFFBEEDC, 0xFFE2BE92), L(0xFFD8D8E2, 0xFFF4F3F8, 0xFFBDBDCB))
        "scottish" -> listOf(L(0xFFCBBDB0, 0xFFEFE6DC, 0xFFA8917F), L(0xFFF0D5B2, 0xFFFAEEDD, 0xFFD9AF80), L(0xFFB3B9C7, 0xFFE3E6EE, 0xFF8F97AA))
        "british" -> listOf(L(0xFFA9B2C4, 0xFFC9CFDB, 0xFF8E98AD, eye = 0xFFE09A3E), L(0xFFC9B8C2, 0xFFE2D6DC, 0xFFAD99A6, eye = 0xFFE09A3E), L(0xFFEBD2B5, 0xFFF7E8D5, 0xFFD4B08A, eye = 0xFFE09A3E))
        "siamese" -> listOf(L(0xFFF5E8D7, 0xFFFBF4EA, 0xFF6C4B3F, eye = 0xFF5B8FD9, outline = c(0xFF84513F)), L(0xFFF2EDE8, 0xFFFBF8F5, 0xFF7D8598, eye = 0xFF5B8FD9), L(0xFFF5E7D6, 0xFFFBF3EA, 0xFF9C7058, eye = 0xFF5B8FD9))
        "ginger" -> listOf(L(0xFFF3B16C, 0xFFFCE4C6, 0xFFD97E3A), L(0xFFF5D2A2, 0xFFFCEEDC, 0xFFE0A764), L(0xFFC9A07E, 0xFFEEDDCB, 0xFF8A6047))
        "tuxedo" -> listOf(L(0xFF3B3641, 0xFFFFFFFF, 0xFF2A262E, outline = OUTLINE_DARK, inner = 0xFFE9A0AC), L(0xFF7D808E, 0xFFFFFFFF, 0xFF5E6170, outline = c(0xFF5A4142)), L(0xFF5A4038, 0xFFFFFFFF, 0xFF3E2C27, outline = OUTLINE_DARK))
        // dogs
        "golden" -> listOf(L(0xFFE9B36B, 0xFFF8DFB4, 0xFFD18F46, nose = 0xFF3A2626), L(0xFFF2CF95, 0xFFFBEBD0, 0xFFE0AE6C, nose = 0xFF3A2626), L(0xFFD68B4C, 0xFFF1C595, 0xFFB56C35, nose = 0xFF3A2626))
        "shiba" -> listOf(L(0xFFE4995A, 0xFFFFF5E8, 0xFFC97B3F, nose = 0xFF3A2626), L(0xFF3F3537, 0xFFF6EBDD, 0xFFD9A066, outline = OUTLINE_DARK, nose = 0xFF241818), L(0xFFDAA674, 0xFFFFF4E6, 0xFF9C6E48, nose = 0xFF3A2626))
        "corgi" -> listOf(L(0xFFE6984F, 0xFFFFFFFF, 0xFFC97A36, nose = 0xFF3A2626), L(0xFF4A3F3F, 0xFFFFFFFF, 0xFFD9985A, outline = OUTLINE_DARK, nose = 0xFF241818), L(0xFFD9A36A, 0xFFFFFFFF, 0xFFA87648, nose = 0xFF3A2626))
        "pomeranian" -> listOf(L(0xFFF2B068, 0xFFFCE2BE, 0xFFDD9548, nose = 0xFF3A2626), L(0xFFF6E2C4, 0xFFFDF5EA, 0xFFE6C9A0, nose = 0xFF3A2626), L(0xFFFBF6F0, 0xFFFFFFFF, 0xFFEADFD3, nose = 0xFF3A2626))
        "husky" -> listOf(L(0xFF8F98A8, 0xFFFFFFFF, 0xFF6E7788, eye = 0xFF79B5EC, nose = 0xFF2E2A2E), L(0xFFBA7B58, 0xFFFFFFFF, 0xFF97603F, eye = 0xFF79B5EC, nose = 0xFF5A3A30), L(0xFF4D4D57, 0xFFFFFFFF, 0xFF37373F, eye = 0xFF79B5EC, outline = OUTLINE_DARK, nose = 0xFF241818))
        // hamsters
        "syrian" -> listOf(L(0xFFEEB775, 0xFFFFF7EC, 0xFFD69A55), L(0xFFF4DCB8, 0xFFFFF9F0, 0xFFE2BF8E), L(0xFFDA9B6B, 0xFFFBEADB, 0xFFB97A4D))
        "winterwhite" -> listOf(L(0xFFB8B4C0, 0xFFFFFFFF, 0xFF6F6A79), L(0xFFAAB4C9, 0xFFFFFFFF, 0xFF6E7791), L(0xFFF4F2F3, 0xFFFFFFFF, 0xFFBAB5BE))
        "panda" -> listOf(L(0xFFFFFFFF, 0xFFFFFFFF, 0xFF3B3641), L(0xFFFFFFFF, 0xFFFFFFFF, 0xFF8A6A58), L(0xFFFFFFFF, 0xFFFFFFFF, 0xFF7C7F8E))
        "robo" -> listOf(L(0xFFDAB58A, 0xFFFFFFFF, 0xFFC09468), L(0xFFE8CBA8, 0xFFFFFFFF, 0xFFCFAA80), L(0xFFC7B6A4, 0xFFFFFFFF, 0xFFA8957F))
        // rabbits
        "hollandlop" -> listOf(L(0xFFFFFFFF, 0xFFFFFFFF, 0xFFF1A65B), L(0xFF8F6653, 0xFFD9C2B1, 0xFF6E4C3D), L(0xFFC8B9C5, 0xFFEDE5EB, 0xFFA898A6))
        "dwarf" -> listOf(L(0xFFFFFFFF, 0xFFFFFFFF, 0xFF5F4F4C), L(0xFFA89F97, 0xFFE6DFD8, 0xFF847A72), L(0xFF403B41, 0xFFE9DDD0, 0xFF2D292E, outline = OUTLINE_DARK))
        "lionhead" -> listOf(L(0xFFF4DEBC, 0xFFFDF4E6, 0xFFE2C192), L(0xFFDAA170, 0xFFF4DCC2, 0xFF7C5949), L(0xFFFFFFFF, 0xFFFFFFFF, 0xFFE8DED5))
        "rex" -> listOf(L(0xFF906751, 0xFFD8BAA3, 0xFF6E4C3B), L(0xFF413B41, 0xFFE8D9C7, 0xFF2D292E, outline = OUTLINE_DARK), L(0xFFEACAA6, 0xFFF8EBDD, 0xFFCFA67C))
        // parrots (light = face, mark = markings, extra = wing, extra2 = beak, extra3 = tail)
        "budgie" -> listOf(L(0xFF8DCB5F, 0xFFF4E46F, 0xFF2E2A30, x = 0xFF6BA347, x2 = 0xFFEBD08A, x3 = 0xFF3F70A9), L(0xFF7EB7E8, 0xFFFFFFFF, 0xFF2E2A30, x = 0xFF5E93C9, x2 = 0xFFEBD08A, x3 = 0xFF3D5F9D), L(0xFFF5E46B, 0xFFFFF6C9, 0xFFC9B34E, x = 0xFFE6CF57, x2 = 0xFFEBD08A, x3 = 0xFFD9C25A))
        "cockatiel" -> listOf(L(0xFFA8A7AE, 0xFFF6DC7B, 0xFFF29B5A, x = 0xFF8F8D96, x2 = 0xFFB9AEA8, x3 = 0xFF7E7C86), L(0xFFFBF2D9, 0xFFF7E18C, 0xFFF29B5A, x = 0xFFF2E6C2, x2 = 0xFFE9C9B4, x3 = 0xFFEFE2BC), L(0xFFC9C3BC, 0xFFFBF4EC, 0xFFF29B5A, x = 0xFFAEA79F, x2 = 0xFFB9AEA8, x3 = 0xFF9E978F))
        "lovebird" -> listOf(L(0xFF80C66B, 0xFFF6A06F, 0xFFE8604C, x = 0xFF67AE57, x2 = 0xFFE8604C, x3 = 0xFF6AA9D9), L(0xFF79B9E3, 0xFFF4F1F1, 0xFF3A3640, x = 0xFF5F9FCC, x2 = 0xFFF2C6C9, x3 = 0xFF4F86C0), L(0xFFF4E06A, 0xFFF7A86F, 0xFFE8604C, x = 0xFFE7CF4F, x2 = 0xFFE8604C, x3 = 0xFFF2E7B8))
        else -> listOf(L(0xFFFFC34B, 0xFFFF9140, 0xFFFF7A33, x = 0xFF6DB56B, x2 = 0xFF3B3539, x3 = 0xFF4F90D2), L(0xFFFFD86A, 0xFFFFAE5C, 0xFFFF8F3D, x = 0xFFFFC34B, x2 = 0xFF3B3539, x3 = 0xFF6DB56B), L(0xFF8BCB71, 0xFFFFC75A, 0xFFE8604C, x = 0xFF5FA65D, x2 = 0xFF3B3539, x3 = 0xFF4F90D2))
    }
}

fun petLook(species: String, breed: String, coat: Int): PetLook = petCoats(species, breed).let { it[coat.coerceIn(0, it.size - 1)] }

// ------------------------------------------------------------------ drawing helpers (unit space)

private fun oval(cx: Float, cy: Float, rx: Float, ry: Float) = Path().apply { addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry)) }

/** A soft mochi: narrower on top, round full cheeks below. */
private fun mochi(cx: Float, cy: Float, w: Float, h: Float) = Path().apply {
    moveTo(cx, cy - h)
    cubicTo(cx + w * .62f, cy - h, cx + w, cy - h * .45f, cx + w, cy + h * .15f)
    cubicTo(cx + w, cy + h * .72f, cx + w * .55f, cy + h, cx, cy + h)
    cubicTo(cx - w * .55f, cy + h, cx - w, cy + h * .72f, cx - w, cy + h * .15f)
    cubicTo(cx - w, cy - h * .45f, cx - w * .62f, cy - h, cx, cy - h)
    close()
}

/** A rounded triangle (ears): two base points and a tip, the tip softened. */
private fun earPath(b1: Offset, tip: Offset, b2: Offset, bulge: Float = 1.6f) = Path().apply {
    val m1 = lerpO(b1, tip, .82f); val m2 = lerpO(b2, tip, .82f)
    val n1 = normalOut(b1, tip, bulge); val n2 = normalOut(tip, b2, bulge)
    moveTo(b1.x, b1.y)
    quadraticTo((b1.x + m1.x) / 2 + n1.x, (b1.y + m1.y) / 2 + n1.y, m1.x, m1.y)
    quadraticTo(tip.x, tip.y, m2.x, m2.y)
    quadraticTo((b2.x + m2.x) / 2 + n2.x, (b2.y + m2.y) / 2 + n2.y, b2.x, b2.y)
    close()
}

private fun lerpO(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
private fun normalOut(a: Offset, b: Offset, k: Float): Offset {
    val dx = b.x - a.x; val dy = b.y - a.y; val len = max(.001f, kotlin.math.sqrt(dx * dx + dy * dy))
    return Offset(-dy / len * k, dx / len * k)
}

private class Ink(val outline: Color, val sketch: Boolean, val dull: Float) {
    val lw = 2.6f
    fun col(c: Color): Color = if (dull <= 0f) c else lerp(c, Color(0xFF9A93A6), dull * .32f)
    private var phase = 0f
    fun stroke(w: Float = lw): Stroke {
        phase += 13f
        return Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = if (sketch) PathEffect.dashPathEffect(floatArrayOf(41f, 2.6f), phase) else null)
    }
    fun line(w: Float) = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
}

/** A filled, airbrushed, outlined shape. */
private fun DrawScope.blob(ink: Ink, path: Path, fill: Color, shade: Color? = null, outline: Boolean = true, lightAt: Offset? = null, lw: Float = 0f) {
    val b = path.getBounds()
    val f = ink.col(fill)
    val s = ink.col(shade ?: lerp(fill, Color(0xFF8A4A45), .24f))
    val ctr = lightAt ?: Offset(b.left + b.width * .4f, b.top + b.height * .3f)
    drawPath(path, Brush.radialGradient(listOf(lerp(f, Color.White, .2f), f, s), center = ctr, radius = max(b.width, b.height) * .9f))
    if (outline) drawPath(path, ink.outline, style = if (lw > 0f) ink.line(lw) else ink.stroke())
}

/** A thick outlined tube along a path (tails, arms, crests). */
private fun DrawScope.tube(ink: Ink, path: Path, w: Float, fill: Color, outline: Boolean = true) {
    if (outline) drawPath(path, ink.outline, style = Stroke(w + ink.lw * 2, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, ink.col(fill), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, ink.col(lerp(fill, Color.White, .22f)), style = Stroke(w * .3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun segment(path: Path, from: Float, to: Float): Path {
    val pm = PathMeasure(); pm.setPath(path, false)
    val out = Path(); pm.getSegment(pm.length * from, pm.length * to, out, true); return out
}

private fun DrawScope.blushAt(ink: Ink, cx: Float, cy: Float, r: Float, extra: Float) {
    val a = .42f + .3f * extra
    drawOval(Brush.radialGradient(listOf(BLUSH.copy(alpha = a), BLUSH.copy(alpha = 0f)), center = Offset(cx, cy), radius = r * 1.1f), Offset(cx - r * 1.1f, cy - r * .7f), androidx.compose.ui.geometry.Size(r * 2.2f, r * 1.4f))
    for (i in -1..1) drawLine(Color(0xFFE86A82).copy(alpha = .55f + .3f * extra), Offset(cx + i * r * .38f - r * .12f, cy + r * .22f), Offset(cx + i * r * .38f + r * .12f, cy - r * .22f), .9f, StrokeCap.Round)
}

/** A big glossy eye, or a closed one. */
private fun DrawScope.eye(ink: Ink, cx: Float, cy: Float, rx: Float, ry: Float, p: PetPose, tint: Color?, lid: Color) {
    val shut = max(p.blink, p.sleep)
    when {
        p.happy > .5f -> drawPath(Path().apply { moveTo(cx - rx, cy + ry * .25f); quadraticTo(cx, cy - ry * .95f, cx + rx, cy + ry * .25f) }, ink.outline, style = ink.line(2.3f))
        shut > .82f -> drawPath(Path().apply { moveTo(cx - rx, cy); quadraticTo(cx, cy + ry * .75f, cx + rx, cy) }, ink.outline, style = ink.line(2.3f))
        else -> {
            val h = ry * (1f - shut * .85f)
            val x = cx + p.lookX * rx * .2f; val y = cy + p.lookY * ry * .14f + (ry - h) * .5f
            val e = oval(x, y, rx, h)
            drawPath(e, Brush.verticalGradient(listOf(EYE, if (tint != null) lerp(EYE, tint, .85f) else Color(0xFF5E3B3E)), startY = y - h, endY = y + h))
            if (tint != null) drawPath(oval(x, y + h * .18f, rx * .5f, h * .52f), EYE.copy(alpha = .85f))
            // a sulky lid drops over the top of the eye
            if (p.droop > .05f) clipPath(e) { drawRect(ink.col(lid), Offset(x - rx, y - h), androidx.compose.ui.geometry.Size(rx * 2, h * .62f * p.droop)) }
            if (p.droop > .05f) drawLine(ink.outline, Offset(x - rx * 1.05f, y - h + h * .62f * p.droop), Offset(x + rx * 1.05f, y - h + h * .62f * p.droop + .4f), 2f, StrokeCap.Round)
            if (h > ry * .35f) {
                drawCircle(WHITE, rx * .42f, Offset(x - rx * .3f, y - h * .36f))
                drawCircle(WHITE.copy(alpha = .9f), rx * .17f, Offset(x + rx * .36f, y + h * .38f))
            }
        }
    }
}

/** A pink paw pad: the bean and three toes. */
private fun DrawScope.beans(cx: Float, cy: Float, s: Float) {
    drawPath(oval(cx, cy + s * .35f, s * .62f, s * .48f), PINK)
    for ((dx, dy) in listOf(-.62f to -.42f, 0f to -.72f, .62f to -.42f)) drawCircle(PINK, s * .25f, Offset(cx + dx * s, cy + dy * s))
}

private fun DrawScope.sparkle(cx: Float, cy: Float, r: Float, a: Float) {
    val p = Path().apply {
        moveTo(cx, cy - r); quadraticTo(cx, cy, cx + r, cy); quadraticTo(cx, cy, cx, cy + r); quadraticTo(cx, cy, cx - r, cy); quadraticTo(cx, cy, cx, cy - r); close()
    }
    drawPath(p, Color.White.copy(alpha = a))
}

// ------------------------------------------------------------------ the pet

/**
 * Draws the pet into [w] x [h], centred, sitting on the bottom. [sketch] adds the small hand-drawn gaps in the
 * outline (leave it off for thumbnails). [t] is seconds, for the few things that move on their own.
 */
fun DrawScope.drawPet(look: PetLook, p: PetPose, w: Float = size.width, h: Float = size.height, sketch: Boolean = true, t: Float = 0f) {
    val u = minOf(w, h) / 100f
    val ink = Ink(look.outline, sketch, p.droop)
    withTransform({
        translate(w / 2f, (h - 100f * u) / 2f + 100f * u - 7f * u)  // origin at the floor, centre
        scale(u, u, pivot = Offset.Zero)
    }) {
        // the soft shadow on the floor, smaller while hopping
        val sh = 1f - p.hop * .35f
        drawOval(Brush.radialGradient(listOf(Color(0xFF3A1E2A).copy(alpha = .32f * sh), Color.Transparent), center = Offset(0f, 0f), radius = 30f * sh), Offset(-30f * sh, -5f * sh), androidx.compose.ui.geometry.Size(60f * sh, 10f * sh))
        val sq = p.squash
        withTransform({
            translate(0f, -p.hop * 14f - 93f)
            scale(1f + .07f * sq - .01f * p.breathe, 1f - .07f * sq + .015f * p.breathe, pivot = Offset(0f, 93f))
        }) {
            when (look.species) {
                "dog" -> dog(ink, look, p)
                "hamster" -> hamster(ink, look, p)
                "rabbit" -> rabbit(ink, look, p)
                "parrot" -> parrot(ink, look, p, t)
                else -> cat(ink, look, p)
            }
        }
    }
}

// ------------------------------------------------------------------ cats

private fun DrawScope.cat(ink: Ink, k: PetLook, p: PetPose) {
    val tux = k.breed == "tuxedo"; val siam = k.breed == "siamese"; val ginger = k.breed == "ginger"; val persian = k.breed == "persian"
    val pawCol = when { tux -> k.light; siam -> k.mark; else -> lerp(k.fur, Color.White, .25f) }
    // tail, curling up behind
    val sway = p.tail * (1f - p.droop * .7f)
    val tail = Path().apply { moveTo(13f, 86f); cubicTo(30f, 88f, 35f + sway * 5f, 70f, 29f + sway * 9f, 58f + abs(sway) * 2f + p.droop * 22f) }
    tube(ink, tail, if (persian) 9f else 6.6f, k.fur)
    if (siam || tux) tube(ink, segment(tail, .62f, 1f), if (persian) 9f else 6.6f, k.mark, outline = false)
    if (ginger) for (f in listOf(.35f, .55f, .75f, .92f)) drawPath(segment(tail, f, f + .06f), ink.col(k.mark), style = Stroke(6.4f, cap = StrokeCap.Butt))
    // back feet
    for (s in listOf(-1f, 1f)) blob(ink, oval(s * 17f, 90.5f, 6.5f, 3.6f), pawCol)
    // body
    val body = Path().apply {
        moveTo(-11f, 52f); cubicTo(-20f, 58f, -25f, 80f, -23f, 88f); cubicTo(-22f, 93.5f, 22f, 93.5f, 23f, 88f); cubicTo(25f, 80f, 20f, 58f, 11f, 52f); close()
    }
    if (persian) for (i in 0..5) { val a = -2.4f + i * .96f; blob(ink, oval(sin(a) * 15f, 66f - cos(a) * 9f, 7f, 6f), k.fur) }
    blob(ink, body, k.fur, k.shade)
    clipPath(body) {
        drawPath(oval(0f, 76f, 10.5f, 13.5f), ink.col(k.light))
        if (ginger) for (y in listOf(66f, 74f)) for (s in listOf(-1f, 1f)) drawLine(ink.col(k.mark), Offset(s * 24f, y), Offset(s * 17f, y + 2f), 2.6f, StrokeCap.Round)
    }
    // front paws (the right one may be up at the mouth)
    val kn = p.knead
    blob(ink, oval(-6.5f, 89f - max(0f, kn) * 2.4f, 5.3f, 4.2f), pawCol)
    if (p.paw < .08f) blob(ink, oval(6.5f, 89f - max(0f, -kn) * 2.4f, 5.3f, 4.2f), pawCol)
    for (s in listOf(-1f, 1f)) if (s < 0 || p.paw < .08f) {
        val y = 89f - (if (s < 0) max(0f, kn) else max(0f, -kn)) * 2.4f
        for (d in listOf(-1.6f, 1.6f)) drawLine(ink.outline, Offset(s * 6.5f + d, y + 1.6f), Offset(s * 6.5f + d, y + 3.4f), 1.1f, StrokeCap.Round)
    }
    // the head
    withTransform({ rotate(p.tilt, Offset(0f, 56f)); translate(0f, p.headDy + p.droop * 3f) }) {
        val hw = when { k.breed == "british" -> 33f; persian -> 31f; else -> 30.5f }
        val head = mochi(0f, 38f, hw, 25f)
        // ears
        for (s in listOf(-1f, 1f)) {
            val tw = (if (s < 0) p.earL else p.earR) * 12f - p.droop * 26f
            rotate(s * -tw, Offset(s * 16f, 20f)) {
                when (k.breed) {
                    "scottish" -> {
                        val e = earPath(Offset(s * 22f, 22f), Offset(s * 21f, 10.5f), Offset(s * 9f, 15.5f), 1.2f)
                        blob(ink, e, k.fur); drawPath(Path().apply { moveTo(s * 20.5f, 13f); quadraticTo(s * 16f, 17f, s * 11.5f, 15.8f) }, ink.outline, style = ink.line(1.6f))
                    }
                    else -> {
                        val tip = if (persian) Offset(s * 26f, 9f) else Offset(s * 25f, 1.5f)
                        val b1 = if (persian) Offset(s * 27f, 27f) else Offset(s * 25f, 24f)
                        val b2 = if (persian) Offset(s * 13f, 16.5f) else Offset(s * 8f, 15f)
                        blob(ink, earPath(b1, tip, b2), if (siam) k.mark else k.fur)
                        drawPath(earPath(lerpO(b1, tip, .3f).let { Offset(it.x - s * 1.2f, it.y + 1.5f) }, lerpO(tip, Offset(s * 16f, 20f), .18f), lerpO(b2, tip, .3f).let { Offset(it.x + s * 1.5f, it.y + 1.2f) }, .8f), ink.col(k.inner))
                    }
                }
            }
        }
        // a persian's cheek ruff
        if (persian) for (s in listOf(-1f, 1f)) for ((x, y, r) in listOf(Triple(28.5f, 45f, 6.4f), Triple(25.5f, 53f, 6.8f), Triple(18f, 59f, 6.6f))) blob(ink, oval(s * x, y, r, r * .95f), k.fur)
        blob(ink, head, k.fur, k.shade)
        clipPath(head) {
            when {
                tux -> {
                    drawPath(Path().apply { moveTo(-3.5f, 22f); lineTo(3.5f, 22f); lineTo(9f, 44f); lineTo(-9f, 44f); close() }, ink.col(k.light))
                    drawPath(oval(0f, 52f, 17f, 12.5f), ink.col(k.light))
                }
                siam -> {
                    val m = ink.col(lerp(k.mark, k.fur, .18f))
                    drawOval(Brush.radialGradient(listOf(m.copy(alpha = .9f), m.copy(alpha = .78f), m.copy(alpha = 0f)), center = Offset(0f, 48f), radius = 15f), Offset(-15f, 36f), androidx.compose.ui.geometry.Size(30f, 24f))
                }
                ginger || k.breed == "scottish" -> {
                    for ((x, len) in listOf(-5.5f to 5f, 0f to 7f, 5.5f to 5f)) drawLine(ink.col(k.mark), Offset(x, 14.5f), Offset(x * .85f, 14.5f + len), 2.4f, StrokeCap.Round)
                    for (s in listOf(-1f, 1f)) for (y in listOf(36f, 41f)) drawLine(ink.col(k.mark), Offset(s * 31f, y), Offset(s * 26f, y + .6f), 2.2f, StrokeCap.Round)
                }
                else -> drawPath(oval(0f, 51f, 13f, 9f), ink.col(k.light).copy(alpha = .7f))
            }
        }
        val ey = if (persian) 41f else 40f; val ex = if (persian) 11.5f else 11f
        for (s in listOf(-1f, 1f)) eye(ink, s * ex, ey, 5.4f, 6.4f, p, k.eye, if (siam) k.mark else k.fur)
        for (s in listOf(-1f, 1f)) blushAt(ink, s * 17.5f, 48f, 4.6f, p.blush)
        catMouth(ink, k, p, if (persian) 46f else 46.5f)
    }
    // the paw raised to the mouth, beans showing
    if (p.paw >= .08f) {
        val e = easeIO(p.paw)
        val px = lerpF(6.5f, 6f, e); val py = lerpF(89f, 53f + p.headDy, e)
        tube(ink, Path().apply { moveTo(9f, 66f); quadraticTo(13f, 72f - e * 10f, px + 1f, py + 4f) }, 8f, k.fur)
        rotate(-18f * e, Offset(px, py)) {
            blob(ink, oval(px, py, 5.8f, 5.4f), pawCol)
            if (e > .5f) beans(px, py, 2.9f)
        }
    }
}

private fun DrawScope.catMouth(ink: Ink, k: PetLook, p: PetPose, ny: Float) {
    val nose = Path().apply { moveTo(-2.3f, ny - .9f); quadraticTo(0f, ny - 1.6f, 2.3f, ny - .9f); quadraticTo(.8f, ny + 1.9f, 0f, ny + 2.1f); quadraticTo(-.8f, ny + 1.9f, -2.3f, ny - .9f); close() }
    drawPath(nose, ink.col(if (k.breed == "siamese" || k.breed == "tuxedo" && k.fur.luminance() < .2f) Color(0xFFB9707A) else k.nose))
    drawPath(nose, ink.outline, style = ink.line(1.3f))
    val my = ny + 2.1f
    if (p.mouth > .12f) {
        val m = p.mouth
        val mouth = Path().apply { moveTo(-3.2f, my + .4f); quadraticTo(0f, my - .6f, 3.2f, my + .4f); quadraticTo(2.8f, my + 2f + 4.2f * m, 0f, my + 2.4f + 4.4f * m); quadraticTo(-2.8f, my + 2f + 4.2f * m, -3.2f, my + .4f); close() }
        drawPath(mouth, Color(0xFF7A3640)); clipPath(mouth) { drawPath(oval(0f, my + 2.6f + 4.4f * m, 2.6f, 2f), Color(0xFFF08A9A)) }
        drawPath(mouth, ink.outline, style = ink.line(1.6f))
    } else {
        drawPath(Path().apply { moveTo(-4.4f, my + .3f); quadraticTo(-2.2f, my + 3.2f, 0f, my); quadraticTo(2.2f, my + 3.2f, 4.4f, my + .3f) }, ink.outline, style = ink.line(1.7f))
    }
    if (p.tongue > .05f) {
        val tg = oval(.6f, my + 2.6f + p.tongue * 1.2f, 1.8f, 1.2f + p.tongue * 1.6f)
        drawPath(tg, Color(0xFFF58EA0)); drawPath(tg, ink.outline, style = ink.line(1.2f))
    }
}

// ------------------------------------------------------------------ dogs

private fun DrawScope.dog(ink: Ink, k: PetLook, p: PetPose) {
    val b = k.breed
    val white = b == "shiba" || b == "corgi" || b == "husky"
    val pawCol = if (white) k.light else lerp(k.fur, Color.White, .2f)
    // the tail, wagging from its base
    rotate(p.tail * (16f - p.droop * 12f) + p.droop * 30f, Offset(14f, 84f)) {
        when (b) {
            "corgi" -> blob(ink, oval(19.5f, 82f, 5.4f, 4.2f), k.fur)
            "shiba", "husky" -> {
                val curl = Path().apply { moveTo(14f, 82f); cubicTo(30f, 84f, 35f, 64f, 25f, 61f); cubicTo(17f, 59f, 17f, 70f, 24f, 71f) }
                tube(ink, curl, 7f, k.fur); drawPath(segment(curl, .55f, 1f), ink.col(k.light), style = Stroke(3f, cap = StrokeCap.Round))
            }
            "pomeranian" -> for ((x, y, r) in listOf(Triple(20f, 74f, 8f), Triple(26f, 66f, 7.5f), Triple(23f, 58f, 7f), Triple(16f, 63f, 6.5f))) blob(ink, oval(x, y, r, r), k.fur)
            else -> tube(ink, Path().apply { moveTo(14f, 85f); cubicTo(28f, 86f, 34f, 76f, 36f, 64f) }, 8f, k.fur)
        }
    }
    for (s in listOf(-1f, 1f)) blob(ink, oval(s * 17f, 90.5f, 6.8f, 3.7f), pawCol)
    val body = Path().apply { moveTo(-12f, 52f); cubicTo(-21f, 58f, -25f, 80f, -23f, 88f); cubicTo(-22f, 93.5f, 22f, 93.5f, 23f, 88f); cubicTo(25f, 80f, 21f, 58f, 12f, 52f); close() }
    if (b == "pomeranian") for (i in 0..6) { val a = -2.6f + i * .87f; blob(ink, oval(sin(a) * 17f, 64f - cos(a) * 9f, 7.5f, 6.5f), k.fur) }
    blob(ink, body, k.fur, k.shade)
    clipPath(body) { drawPath(oval(0f, 72f, if (white) 12f else 10f, 16f), ink.col(k.light)) }
    for (s in listOf(-1f, 1f)) {
        val y = 89f - (if (s < 0) max(0f, p.knead) else max(0f, -p.knead)) * 2.4f
        blob(ink, oval(s * 6.8f, y, 5.6f, 4.4f), pawCol)
        for (d in listOf(-1.7f, 1.7f)) drawLine(ink.outline, Offset(s * 6.8f + d, y + 1.7f), Offset(s * 6.8f + d, y + 3.5f), 1.1f, StrokeCap.Round)
    }
    withTransform({ rotate(p.tilt, Offset(0f, 56f)); translate(0f, p.headDy + p.droop * 3f) }) {
        val head = mochi(0f, 38f, 30f, 26f)
        if (b == "pomeranian") for (i in 0 until 12) { val a = i * (6.283f / 12); blob(ink, oval(sin(a) * 29f, 40f - cos(a) * 26f, 8f, 8f), k.fur) }
        val upright = b != "golden"
        if (upright) for (s in listOf(-1f, 1f)) {
            val tw = (if (s < 0) p.earL else p.earR) * 10f - p.droop * 28f
            rotate(s * -tw, Offset(s * 16f, 20f)) {
                val big = b == "corgi"; val small = b == "pomeranian"
                val tip = when { big -> Offset(s * 28f, -6f); small -> Offset(s * 20f, 6f); else -> Offset(s * 25f, 0f) }
                val b1 = Offset(s * (if (small) 22f else 26f), if (small) 20f else 24f); val b2 = Offset(s * (if (small) 11f else 8f), if (small) 14f else 15f)
                blob(ink, earPath(b1, tip, b2), k.fur)
                drawPath(earPath(Offset(b1.x - s * 2.5f, b1.y - 1f), lerpO(tip, Offset(s * 16f, 20f), .22f), Offset(b2.x + s * 2.2f, b2.y), .8f), ink.col(if (b == "husky" || b == "shiba") k.light else k.inner))
            }
        }
        blob(ink, head, k.fur, k.shade)
        clipPath(head) {
            when (b) {
                "shiba" -> { for (s in listOf(-1f, 1f)) { drawPath(oval(s * 15f, 52f, 12f, 9f), ink.col(k.light)); drawPath(oval(s * 9.5f, 31f, 2.4f, 1.7f), ink.col(if (k.fur.luminance() < .2f) k.mark else k.light)) }; drawPath(oval(0f, 52f, 9f, 8f), ink.col(k.light)) }
                "corgi" -> drawPath(Path().apply { moveTo(-1.6f, 12f); lineTo(1.6f, 12f); lineTo(7f, 44f); cubicTo(16f, 47f, 14f, 66f, 0f, 66f); cubicTo(-14f, 66f, -16f, 47f, -7f, 44f); close() }, ink.col(k.light))
                "husky" -> {
                    drawPath(Path().apply { moveTo(-1.8f, 18f); lineTo(1.8f, 18f); lineTo(5f, 40f); cubicTo(24f, 40f, 30f, 52f, 30f, 66f); lineTo(-30f, 66f); cubicTo(-30f, 52f, -24f, 40f, -5f, 40f); close() }, ink.col(k.light))
                    for (s in listOf(-1f, 1f)) drawPath(oval(s * 10f, 31.5f, 2.6f, 1.8f), ink.col(k.light))
                }
                else -> drawPath(oval(0f, 50f, 11f, 8.5f), ink.col(k.light))
            }
        }
        if (!upright) for (s in listOf(-1f, 1f)) {
            val sw = (if (s < 0) p.earL else p.earR) * 8f + p.tilt * .4f
            rotate(s * sw + p.droop * s * 6f, Offset(s * 16f, 18f)) {
                blob(ink, Path().apply { moveTo(s * 13f, 17f); cubicTo(s * 25f, 12f, s * 34f, 26f, s * 32f, 43f); cubicTo(s * 31f, 51f, s * 22f, 51f, s * 21.5f, 41f); cubicTo(s * 20.5f, 32f, s * 17f, 24f, s * 13f, 17f); close() }, k.mark)
            }
        }
        for (s in listOf(-1f, 1f)) eye(ink, s * 11.5f, 39f, 5.3f, 6.2f, p, k.eye, k.fur)
        for (s in listOf(-1f, 1f)) blushAt(ink, s * 18f, 48f, 4.6f, p.blush)
        // nose and mouth
        val nose = oval(0f, 46f, 3.4f, 2.4f)
        drawPath(nose, ink.col(k.nose)); drawPath(nose, ink.outline, style = ink.line(1.2f)); drawCircle(Color.White.copy(alpha = .8f), .9f, Offset(-1.1f, 45.2f))
        drawLine(ink.outline, Offset(0f, 48.3f), Offset(0f, 49.8f), 1.6f, StrokeCap.Round)
        if (p.mouth > .12f || p.tongue > .1f) {
            val m = max(p.mouth, p.tongue * .6f)
            val mouth = Path().apply { moveTo(-4.4f, 50f); quadraticTo(0f, 48.6f, 4.4f, 50f); quadraticTo(3.6f, 52.6f + 4f * m, 0f, 53f + 4.2f * m); quadraticTo(-3.6f, 52.6f + 4f * m, -4.4f, 50f); close() }
            drawPath(mouth, Color(0xFF7A3640)); drawPath(mouth, ink.outline, style = ink.line(1.6f))
            if (p.tongue > .1f) {
                val tl = 3f + p.tongue * 5.5f
                val tg = Path().apply { moveTo(-2.6f, 51.5f); lineTo(-2.6f, 51.5f + tl); quadraticTo(-2.6f, 54f + tl, 0f, 54f + tl); quadraticTo(2.6f, 54f + tl, 2.6f, 51.5f + tl); lineTo(2.6f, 51.5f); close() }
                drawPath(tg, Color(0xFFF58EA0)); drawPath(tg, ink.outline, style = ink.line(1.3f))
                drawLine(Color(0xFFD9667C), Offset(0f, 52.5f), Offset(0f, 51.5f + tl * .8f), .9f, StrokeCap.Round)
            }
        } else drawPath(Path().apply { moveTo(-4.6f, 50f); quadraticTo(-2.3f, 52.8f, 0f, 49.8f); quadraticTo(2.3f, 52.8f, 4.6f, 50f) }, ink.outline, style = ink.line(1.7f))
    }
}

// ------------------------------------------------------------------ hamsters

private fun DrawScope.hamster(ink: Ink, k: PetLook, p: PetPose) {
    val b = k.breed
    for (s in listOf(-1f, 1f)) blob(ink, oval(s * 13f, 92f, 4.4f, 2.4f), Color(0xFFF7B9C0), lw = 1.8f)
    withTransform({ translate(0f, p.droop * 2f) }) {
        for (s in listOf(-1f, 1f)) {
            val tw = (if (s < 0) p.earL else p.earR) * 14f - p.droop * 20f
            rotate(s * -tw, Offset(s * 17f, 40f)) {
                blob(ink, oval(s * 19.5f, 36f, 6.4f, 6f), if (b == "panda") k.mark else k.fur)
                drawPath(oval(s * 19.5f, 36.6f, 3.6f, 3.4f), ink.col(k.inner))
            }
        }
        val body = mochi(0f, 63f, 35f, 30f)
        blob(ink, body, k.fur, k.shade)
        clipPath(body) {
            when (b) {
                "panda" -> { drawPath(oval(0f, 30f, 36f, 13f), ink.col(k.mark)); for (s in listOf(-1f, 1f)) drawPath(oval(s * 34f, 74f, 11f, 13f), ink.col(k.mark)) }
                "winterwhite" -> { drawLine(ink.col(k.mark), Offset(0f, 30f), Offset(0f, 44f), 2.6f, StrokeCap.Round); drawPath(oval(0f, 80f, 26f, 16f), ink.col(k.light)) }
                else -> drawPath(oval(0f, 79f, 25f, 17f), ink.col(k.light))
            }
            if (b == "robo") for (s in listOf(-1f, 1f)) drawPath(oval(s * 10f, 46f, 3.6f, 2f), ink.col(k.light))
            // the muzzle, a little lighter
            drawPath(oval(0f, 62f, 9f, 6f), ink.col(lerp(k.fur, k.light, .7f)))
        }
        // cheeks, puffing up when stuffed
        val ch = p.cheeks
        for (s in listOf(-1f, 1f)) {
            val cp = oval(s * (21f + ch * 4f), 63f, 9f + ch * 5f, 7.5f + ch * 4f)
            if (ch > .15f) blob(ink, cp, lerp(k.fur, k.light, .45f)) else drawPath(cp, ink.col(lerp(k.fur, k.light, .45f)).copy(alpha = .55f))
        }
        withTransform({ rotate(p.tilt * .5f, Offset(0f, 70f)); translate(0f, p.headDy * .6f) }) {
            for (s in listOf(-1f, 1f)) eye(ink, s * 10.5f, 54f, 4.5f, 5.2f, p, k.eye, k.fur)
            for (s in listOf(-1f, 1f)) blushAt(ink, s * 17f, 61f, 4f, p.blush)
            val n = p.nose
            val nose = oval(0f, 59.6f - n * .5f, 1.7f + n * .3f, 1.25f)
            drawPath(nose, ink.col(k.nose)); drawPath(nose, ink.outline, style = ink.line(1.1f))
            if (p.mouth > .12f) drawPath(oval(0f, 63f, 1.6f, .8f + p.mouth * 1.4f), Color(0xFF7A3640))
            else drawPath(Path().apply { moveTo(-2.6f, 61.4f); quadraticTo(-1.3f, 63.2f, 0f, 61.2f); quadraticTo(1.3f, 63.2f, 2.6f, 61.4f) }, ink.outline, style = ink.line(1.3f))
            for (s in listOf(-1f, 1f)) for (dy in listOf(-1f, 1f)) drawLine(ink.outline.copy(alpha = .6f), Offset(s * 5.5f, 61f + dy), Offset(s * 11f, 60.5f + dy * 2f), .8f, StrokeCap.Round)
        }
        // little hands, holding a seed while nibbling
        val nib = p.mouth
        if (nib > .05f) {
            val seed = Path().apply { moveTo(0f, 64.5f); quadraticTo(3.4f, 67.5f, 0f, 72f); quadraticTo(-3.4f, 67.5f, 0f, 64.5f); close() }
            rotate(12f, Offset(0f, 68f)) { drawPath(seed, Color(0xFFEAD8A8)); drawLine(Color(0xFF9C8058), Offset(0f, 66f), Offset(0f, 70.5f), .9f); drawPath(seed, ink.outline, style = ink.line(1.2f)) }
        }
        for (s in listOf(-1f, 1f)) blob(ink, oval(s * (if (nib > .05f) 3.4f else 5.2f), 73f - nib * 3.5f + (if (s < 0) 1f else -1f) * nib * sin(p.nose * 6.28f) * .6f, 2.4f, 1.9f), lerp(Color(0xFFF7C4C9), k.light, .35f), lw = 1.4f)
    }
}

// ------------------------------------------------------------------ rabbits

private fun DrawScope.rabbit(ink: Ink, k: PetLook, p: PetPose) {
    val b = k.breed
    val pawCol = if (b == "dwarf" && k.fur.luminance() > .9f) k.mark else lerp(k.fur, k.light, .5f)
    blob(ink, oval(22f, 84f, 6.4f, 6f), k.light)
    for (s in listOf(-1f, 1f)) blob(ink, oval(s * 18f, 91f, 8.4f, 3.6f), pawCol)
    val body = mochi(0f, 77f, 25f, 17f)
    blob(ink, body, k.fur, k.shade)
    clipPath(body) {
        drawPath(oval(0f, 82f, 13f, 12f), ink.col(k.light))
        if (b == "hollandlop" && k.fur.luminance() > .9f) drawPath(oval(-16f, 72f, 10f, 8f), ink.col(k.mark))
    }
    for (s in listOf(-1f, 1f)) blob(ink, oval(s * 5.6f, 90f, 4.3f, 3.4f), pawCol)
    withTransform({ rotate(p.tilt, Offset(0f, 66f)); translate(0f, p.headDy + p.droop * 2f) }) {
        val lop = b == "hollandlop"
        if (!lop) for (s in listOf(-1f, 1f)) {
            val len = when (b) { "dwarf" -> 16f; "lionhead" -> 21f; else -> 29f }
            val tw = (if (s < 0) p.earL else p.earR) * 10f + p.droop * 55f
            rotate(s * (8f + tw), Offset(s * 8f, 31f)) {
                val ear = Path().apply { moveTo(s * 3.5f, 32f); cubicTo(s * 1.5f, 32f - len * .6f, s * 6f, 30f - len, s * 9.5f, 30f - len); cubicTo(s * 14f, 30f - len, s * 15f, 32f - len * .55f, s * 13f, 32f); close() }
                blob(ink, ear, if (b == "dwarf" && k.fur.luminance() > .9f) k.mark else k.fur)
                drawPath(Path().apply { moveTo(s * 6.2f, 32f); cubicTo(s * 5.5f, 32f - len * .55f, s * 7.5f, 33f - len * .9f, s * 9.3f, 33f - len * .9f); cubicTo(s * 11.5f, 33f - len * .9f, s * 11.8f, 32f - len * .5f, s * 10.6f, 32f); close() }, ink.col(k.inner))
            }
        }
        if (b == "lionhead") for (i in 0 until 11) { val a = -2.7f + i * .54f; blob(ink, oval(sin(a) * 25f, 49f - cos(a) * 21f, 7f, 7f), lerp(k.fur, k.light, .3f)) }
        val head = mochi(0f, 48f, 26f, 21.5f)
        blob(ink, head, k.fur, k.shade)
        clipPath(head) {
            if (lop && k.fur.luminance() > .9f) { for (s in listOf(-1f, 1f)) drawPath(oval(s * 10f, 47f, 8f, 7.5f), ink.col(k.mark)); drawPath(oval(0f, 55f, 5f, 4f), ink.col(k.mark)) }
            if (b == "dwarf" && k.fur.luminance() > .9f) drawPath(oval(0f, 55f, 6f, 5f), ink.col(k.mark).copy(alpha = .8f))
            drawPath(oval(0f, 58f, 9f, 6f), ink.col(k.light).copy(alpha = .7f))
        }
        if (lop) for (s in listOf(-1f, 1f)) {
            val sw = (if (s < 0) p.earL else p.earR) * 7f + p.tilt * .5f
            rotate(s * sw, Offset(s * 13f, 30f)) {
                blob(ink, Path().apply { moveTo(s * 9f, 29f); cubicTo(s * 24f, 24f, s * 31f, 40f, s * 29f, 60f); cubicTo(s * 28f, 69f, s * 18f, 68f, s * 19f, 57f); cubicTo(s * 19.5f, 45f, s * 15f, 35f, s * 9f, 29f); close() }, if (k.fur.luminance() > .9f) k.mark else k.fur)
            }
        }
        for (s in listOf(-1f, 1f)) eye(ink, s * 10.5f, 48.5f, 4.9f, 5.7f, p, k.eye, k.fur)
        for (s in listOf(-1f, 1f)) blushAt(ink, s * 16f, 56f, 4.2f, p.blush)
        val n = p.nose
        withTransform({ translate(0f, -n * .7f); scale(1f + n * .12f, 1f - n * .08f, Offset(0f, 54f)) }) {
            val nose = Path().apply { moveTo(-1.9f, 53.4f); lineTo(1.9f, 53.4f); quadraticTo(.5f, 55.6f, 0f, 55.7f); quadraticTo(-.5f, 55.6f, -1.9f, 53.4f); close() }
            drawPath(nose, ink.col(k.nose)); drawPath(nose, ink.outline, style = ink.line(1.1f))
        }
        drawLine(ink.outline, Offset(0f, 55.7f), Offset(0f, 57f), 1.3f, StrokeCap.Round)
        if (p.mouth > .12f) drawPath(oval(0f, 58.6f, 1.8f, .9f + p.mouth * 1.4f), Color(0xFF7A3640))
        else drawPath(Path().apply { moveTo(-3f, 57.2f); quadraticTo(-1.5f, 59.2f, 0f, 57f); quadraticTo(1.5f, 59.2f, 3f, 57.2f) }, ink.outline, style = ink.line(1.4f))
    }
}

// ------------------------------------------------------------------ parrots

private fun DrawScope.parrot(ink: Ink, k: PetLook, p: PetPose, t: Float) {
    val b = k.breed
    // a little perch to stand on
    drawLine(Color(0xFF8A5A44), Offset(-24f, 91f), Offset(24f, 91f), 4.6f, StrokeCap.Round)
    drawLine(Color(0xFFB07C5E), Offset(-23f, 90.2f), Offset(23f, 90.2f), 1.4f, StrokeCap.Round)
    val tail = Path().apply { moveTo(-5.5f, 74f); lineTo(5.5f, 74f); lineTo(4f, 99f); quadraticTo(0f, 102f, -4f, 99f); close() }
    blob(ink, tail, k.extra3)
    val body = Path().apply { moveTo(0f, 44f); cubicTo(17f, 46f, 21f, 66f, 15.5f, 80f); cubicTo(11f, 90f, -11f, 90f, -15.5f, 80f); cubicTo(-21f, 66f, -17f, 46f, 0f, 44f); close() }
    blob(ink, body, k.fur, k.shade)
    clipPath(body) { drawPath(oval(0f, 72f, 10f, 13f), ink.col(lerp(k.fur, Color.White, .25f))) }
    // feet gripping the perch
    for (s in listOf(-1f, 1f)) for (d in listOf(-1.6f, 0f, 1.6f)) drawLine(Color(0xFFC9969A), Offset(s * 5f + d, 86f), Offset(s * 5f + d * 1.3f, 90.4f), 1.8f, StrokeCap.Round)
    // wings
    for (s in listOf(-1f, 1f)) {
        val flap = p.wing * (52f + 8f * sin(t * 22f))
        rotate(s * flap, Offset(s * 12f, 55f)) {
            val wing = Path().apply { moveTo(s * 11f, 53f); cubicTo(s * 22f, 57f, s * 22f, 77f, s * 15f, 88f); cubicTo(s * 10f, 84f, s * 8f, 70f, s * 11f, 53f); close() }
            blob(ink, wing, k.extra)
            clipPath(wing) {
                when (b) {
                    "budgie" -> for (y in listOf(60f, 66f, 72f, 78f)) drawPath(Path().apply { moveTo(s * 9f, y); quadraticTo(s * 15f, y + 3.5f, s * 23f, y) }, ink.col(k.mark).copy(alpha = .75f), style = Stroke(1.5f))
                    "cockatiel" -> drawPath(oval(s * 15f, 62f, 4f, 7f), ink.col(Color.White).copy(alpha = .85f))
                    else -> drawPath(oval(s * 15f, 84f, 6f, 6f), ink.col(lerp(k.extra, k.extra3, .7f)))
                }
            }
        }
    }
    withTransform({ rotate(p.tilt, Offset(0f, 50f)); translate(0f, p.headDy + p.droop * 2f) }) {
        if (b == "cockatiel") for ((dx, len) in listOf(-2f to 15f, 1.5f to 18f, 5f to 14f)) {
            val crest = Path().apply { moveTo(dx, 22f); quadraticTo(dx + 1f - p.droop * 4f, 15f - len * .3f, dx + 6f + p.droop * 8f, 22f - len) }
            tube(ink, crest, 3.2f, k.light)
        }
        val head = mochi(0f, 36f, 19.5f, 18f)
        blob(ink, head, k.fur, k.shade)
        clipPath(head) {
            when (b) {
                "budgie" -> {
                    drawPath(oval(0f, 44f, 14f, 12f), ink.col(k.light))
                    for (y in listOf(21f, 25f, 29f)) drawPath(Path().apply { moveTo(-14f, y + 2f); quadraticTo(0f, y - 2f, 14f, y + 2f) }, ink.col(k.mark).copy(alpha = .55f), style = Stroke(1.2f))
                }
                "cockatiel" -> drawPath(oval(0f, 34f, 19f, 15f), ink.col(k.light))
                "lovebird" -> { drawPath(oval(0f, 38f, 19f, 16f), ink.col(k.light)); drawPath(oval(0f, 22f, 10f, 6f), ink.col(k.mark)) }
                else -> drawPath(oval(0f, 40f, 17f, 14f), ink.col(k.light))
            }
        }
        for (s in listOf(-1f, 1f)) {
            if (b == "lovebird") drawCircle(Color.White, 5.2f, Offset(s * 8.5f, 33.5f))
            eye(ink, s * 8.5f, 33.5f, 3.8f, 4.4f, p, k.eye, k.fur)
            when (b) {
                "cockatiel" -> drawPath(oval(s * 11.5f, 41f, 4f, 3.6f), ink.col(k.mark))
                "budgie" -> { drawPath(oval(s * 11.5f, 44.5f, 2f, 3f), ink.col(Color(0xFF7D6CC9))); drawCircle(ink.col(k.mark), 1.1f, Offset(s * 4f, 50f)) }
                else -> blushAt(ink, s * 12f, 41f, 3.6f, p.blush)
            }
        }
        // the beak, opening to chirp
        val lower = Path().apply { moveTo(-2.4f, 43f); quadraticTo(0f, 47.5f + p.mouth * 3f, 2.4f, 43f); close() }
        drawPath(lower, ink.col(lerp(k.extra2, Color.Black, .15f))); drawPath(lower, ink.outline, style = ink.line(1.2f))
        val upper = Path().apply { moveTo(-4f, 38f); quadraticTo(0f, 36f, 4f, 38f); quadraticTo(3.8f, 43.5f, .6f, 46f - p.mouth * 1.5f); quadraticTo(-3.4f, 43.2f, -4f, 38f); close() }
        blob(ink, upper, k.extra2)
        if (b == "budgie") { val cere = oval(0f, 36.8f, 3.3f, 1.5f); drawPath(cere, ink.col(Color(0xFF7C9EE0))); drawPath(cere, ink.outline, style = ink.line(1f)) }
    }
}

// ------------------------------------------------------------------ bits the stage draws around the pet

internal fun easeIO(x: Float): Float { val t = x.coerceIn(0f, 1f); return if (t < .5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f }
internal fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** A few soft sparkles that drift around a happy pet. */
fun DrawScope.petSparkles(t: Float, strength: Float) {
    if (strength <= .01f) return
    val u = size.minDimension / 100f
    for (i in 0 until 5) {
        val ph = (t * .35f + i * .21f) % 1f
        val x = size.width / 2f + sin(i * 2.1f + t * .4f) * 38f * u
        val y = size.height * .62f - ph * 46f * u - i * 3f * u
        val a = sin(ph * 3.1416f) * strength
        sparkle(x, y, (2.2f + (i % 3)) * u, a * .9f)
    }
}

/** "z z z" floating up from a sleeping pet. */
fun DrawScope.petZzz(t: Float, strength: Float, color: Color) {
    if (strength <= .01f) return
    val u = size.minDimension / 100f
    for (i in 0 until 3) {
        val ph = (t * .3f + i / 3f) % 1f
        // beside the head, clear of the ears, drifting up and away
        val x = size.width / 2f + (30f + ph * 10f + i * 1.5f) * u
        val y = size.height * .26f - ph * 16f * u
        val s = (2.2f + ph * 2.4f) * u
        val a = sin(ph * 3.1416f) * strength
        drawPath(Path().apply { moveTo(x - s, y - s); lineTo(x + s, y - s); lineTo(x - s, y + s); lineTo(x + s, y + s) }, color.copy(alpha = a), style = Stroke(1.6f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A small heart that floats up after a cuddle. */
fun DrawScope.petHeart(x: Float, y: Float, s: Float, a: Float) {
    val p = Path().apply {
        moveTo(x, y + s * .7f)
        cubicTo(x - s * 1.25f, y - s * .05f, x - s * .6f, y - s * 1.05f, x, y - s * .42f)
        cubicTo(x + s * .6f, y - s * 1.05f, x + s * 1.25f, y - s * .05f, x, y + s * .7f)
        close()
    }
    drawPath(p, Brush.radialGradient(listOf(Color(0xFFFFD0DA).copy(alpha = a), Color(0xFFFF8FA3).copy(alpha = a)), center = Offset(x - s * .3f, y - s * .4f), radius = s * 1.4f))
    drawPath(p, OUTLINE.copy(alpha = a), style = Stroke(s * .16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
