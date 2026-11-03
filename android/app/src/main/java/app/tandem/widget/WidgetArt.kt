package app.tandem.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import kotlin.random.Random

/**
 * Widgets can't draw, so their backgrounds are painted here as bitmaps, with the same dusk, glow, stars and grain
 * as the app. Every bitmap is clipped to the widget's own rounded corners, so it looks right on any Android version.
 */
internal object WidgetArt {
    private const val TOP = 0xFF241B6A.toInt()
    private const val MID = 0xFF2A2076.toInt()
    private const val BOTTOM = 0xFF100C32.toInt()
    private const val PERIWINKLE = 0xFF9C8CFF.toInt()

    /** Bitmap size for a widget of [wDp] x [hDp], kept small enough for the home screen to accept. */
    fun px(context: Context, wDp: Float, hDp: Float): Pair<Int, Int> {
        val d = context.resources.displayMetrics.density
        val scale = minOf(1f, 720f / (wDp * d))
        return (wDp * d * scale).toInt().coerceAtLeast(64) to (hDp * d * scale).toInt().coerceAtLeast(64)
    }

    private fun canvas(w: Int, h: Int, r: Float): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) })
        return bmp to c
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, color: Int, alpha: Float) {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(x, y, r, (color and 0xFFFFFF) or (a shl 24), color and 0xFFFFFF, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, p)
    }

    private fun stars(c: Canvas, w: Int, h: Int, n: Int, seed: Int, unit: Float, maxY: Float = 1f) {
        val r = Random(seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(n) {
            val s = r.nextFloat()
            p.color = ((((.25f + .6f * s) * 255).toInt()) shl 24) or 0xFFF8FF
            c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * maxY, (.5f + 1.2f * s) * unit, p)
        }
    }

    private fun grain(bmp: Bitmap, amount: Int = 14) {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val r = Random(5)
        for (i in px.indices) {
            val c = px[i]
            if (c ushr 24 == 0) continue
            val n = r.nextInt(-amount, amount + 1)
            val rr = ((c shr 16 and 0xFF) + n).coerceIn(0, 255)
            val gg = ((c shr 8 and 0xFF) + n).coerceIn(0, 255)
            val bb = ((c and 0xFF) + n).coerceIn(0, 255)
            px[i] = (c and 0xFF000000.toInt()) or (rr shl 16) or (gg shl 8) or bb
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun hairline(c: Canvas, w: Int, h: Int, r: Float, unit: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = unit; color = 0x26FFFFFF }
        c.drawRoundRect(RectF(unit / 2, unit / 2, w - unit / 2, h - unit / 2), r, r, p)
    }

    /** The app's dusk: a deep gradient, a glow of [tint] high on one side, a cool glow low on the other, stars and grain. */
    fun dusk(context: Context, wDp: Float, hDp: Float, tint: Int, seed: Int = 3): Bitmap {
        val (w, h) = px(context, wDp, hDp)
        val unit = w / wDp
        val r = 24f * unit
        val (bmp, c) = canvas(w, h, r)
        c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(TOP, MID, BOTTOM), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP) })
        glow(c, w * .82f, h * .08f, maxOf(w, h) * .95f, tint, .42f)
        glow(c, w * .1f, h * 1.05f, maxOf(w, h) * .8f, PERIWINKLE, .22f)
        stars(c, w, h, (w * h / (2600f * unit * unit)).toInt().coerceIn(18, 70), seed, unit)
        grain(bmp)
        hairline(c, w, h, r, unit)
        return bmp
    }

    private fun drawCover(c: Canvas, art: Bitmap, dst: RectF, p: Paint? = null) {
        val sr = art.width.toFloat() / art.height; val dr = dst.width() / dst.height()
        val src = if (sr > dr) {
            val sw = art.height * dr; val x = (art.width - sw) / 2
            Rect(x.toInt(), 0, (x + sw).toInt(), art.height)
        } else {
            val sh = art.width / dr; val y = (art.height - sh) * .4f
            Rect(0, y.toInt(), art.width, (y + sh).toInt())
        }
        c.drawBitmap(art, src, dst, p ?: Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun scrim(c: Canvas, w: Int, h: Int, from: Float = .35f) {
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, h * from, 0f, h.toFloat(), intArrayOf(0x00140C2E, 0x55140C2E, 0xE6140C2E.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        })
        c.drawRect(0f, 0f, w.toFloat(), h * .3f, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * .3f, 0x66140C2E, 0x00140C2E, Shader.TileMode.CLAMP)
        })
    }

    /** One artwork filling the widget, with soft shade at the top and bottom where the text sits. */
    fun art(context: Context, res: Int, wDp: Float, hDp: Float): Bitmap {
        val (w, h) = px(context, wDp, hDp)
        val unit = w / wDp
        val r = 24f * unit
        val (bmp, c) = canvas(w, h, r)
        BitmapFactory.decodeResource(context.resources, res)?.let { drawCover(c, it, RectF(0f, 0f, w.toFloat(), h.toFloat())) }
        scrim(c, w, h)
        hairline(c, w, h, r, unit)
        return bmp
    }

    /**
     * The two of you as one picture: your mood's artwork on the left melting into your partner's on the right,
     * so the widget reads as one shared sky rather than two boxes.
     */
    fun panorama(context: Context, left: Int, right: Int, wDp: Float, hDp: Float): Bitmap {
        val (w, h) = px(context, wDp, hDp)
        val unit = w / wDp
        val r = 24f * unit
        val (bmp, c) = canvas(w, h, r)
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val a = BitmapFactory.decodeResource(context.resources, left)
        val b = BitmapFactory.decodeResource(context.resources, right)
        if (a != null) drawCover(c, a, RectF(0f, 0f, w * .62f, h.toFloat()))
        if (b != null) {
            // the right picture fades in across the middle third
            val layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val lc = Canvas(layer)
            drawCover(lc, b, RectF(w * .38f, 0f, w.toFloat(), h.toFloat()))
            lc.drawRect(full, Paint().apply {
                shader = LinearGradient(w * .38f, 0f, w * .62f, 0f, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            })
            c.drawBitmap(layer, 0f, 0f, null)
        }
        scrim(c, w, h, .3f)
        hairline(c, w, h, r, unit)
        return bmp
    }

    /** The countdown to the next event: a soft glowing arc that closes as the day comes nearer. */
    fun ring(days: Long, special: Boolean, px: Int = 220): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val col = if (special) 0xFFFFD58A.toInt() else 0xFFFF9DB5.toInt()
        val stroke = px * .06f
        val inset = px * .14f
        val oval = RectF(inset, inset, px - inset, px - inset)
        glow(c, px / 2f, px / 2f, px / 2f, col, .28f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        p.color = 0x22FFFFFF
        c.drawArc(oval, 0f, 360f, false, p)
        val f = (1f - days.coerceIn(0, 30) / 30f).coerceIn(.04f, 1f)
        p.shader = android.graphics.SweepGradient(px / 2f, px / 2f, intArrayOf(col and 0x66FFFFFF, col, col), floatArrayOf(0f, f.coerceAtLeast(.05f), 1f))
        c.save(); c.rotate(-90f, px / 2f, px / 2f)
        c.drawArc(oval, 0f, 360f * f, false, p)
        c.restore()
        // a bright bead at the leading end of the arc
        val ang = Math.toRadians((-90.0 + 360.0 * f))
        val bx = px / 2f + (oval.width() / 2) * Math.cos(ang).toFloat(); val by = px / 2f + (oval.height() / 2) * Math.sin(ang).toFloat()
        glow(c, bx, by, stroke * 2.2f, col, .8f)
        c.drawCircle(bx, by, stroke * .55f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        return bmp
    }

    /** A small lit dot in a mood's colour, for the label line. */
    fun dot(color: Int, px: Int = 36): Bitmap {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        glow(c, px / 2f, px / 2f, px / 2f, color, .7f)
        c.drawCircle(px / 2f, px / 2f, px * .2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() })
        return bmp
    }
}
