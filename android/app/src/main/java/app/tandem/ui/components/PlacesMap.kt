package app.tandem.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tandem.TandemApp
import app.tandem.data.Geo
import app.tandem.data.Place
import app.tandem.data.PlaceCategory
import app.tandem.ui.theme.T
import app.tandem.ui.theme.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ------------------------------------------------------------------ map tiles (OpenStreetMap, cached on the phone)

/**
 * Downloads OpenStreetMap tiles, keeps recent ones in memory and on disk (so the map works offline for places you've looked at),
 * and never sends anything but the tile address. Follows the OSM tile policy: a real User-Agent, a small number of parallel
 * requests, and caching. The Tandem login token is never attached (it only goes to the Tandem server host).
 */
private class TileStore(cacheRoot: File, private val http: OkHttpClient) {
    private val dir = File(cacheRoot, "osm-tiles").apply { mkdirs() }
    private val memory = LruCache<Long, ImageBitmap>(180)
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()
    private val failedAt = ConcurrentHashMap<Long, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(4)
    /** Bumped whenever a tile arrives, so the map canvas redraws. */
    var version by mutableIntStateOf(0)
        private set

    init { scope.launch { trimDisk() } }

    private fun key(z: Int, x: Int, y: Int) = (z.toLong() shl 48) or (x.toLong() shl 24) or y.toLong()
    private fun file(z: Int, x: Int, y: Int) = File(dir, "$z/$x/$y.png")

    /** The tile if it's already in memory; otherwise null, and a load is started (unless [fetch] is false). */
    fun peek(z: Int, x: Int, y: Int, fetch: Boolean): ImageBitmap? {
        val k = key(z, x, y)
        memory.get(k)?.let { return it }
        if (!fetch || !inFlight.add(k)) return null
        val failed = failedAt[k]
        if (failed != null && System.currentTimeMillis() - failed < 15_000) { inFlight.remove(k); return null }
        scope.launch {
            try {
                gate.withPermit {
                    val f = file(z, x, y)
                    var bytes: ByteArray? = if (f.exists() && System.currentTimeMillis() - f.lastModified() < 14L * 24 * 3600 * 1000) runCatching { f.readBytes() }.getOrNull() else null
                    if (bytes == null) {
                        val req = Request.Builder().url("https://tile.openstreetmap.org/$z/$x/$y.png").header("User-Agent", Geo.USER_AGENT).build()
                        bytes = runCatching { http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null } }.getOrNull()
                        if (bytes != null) runCatching { f.parentFile?.mkdirs(); f.writeBytes(bytes) }
                    }
                    val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    if (bmp != null) { memory.put(k, bmp.asImageBitmap()); failedAt.remove(k); version++ }
                    else failedAt[k] = System.currentTimeMillis()
                }
            } finally { inFlight.remove(k) }
        }
        return null
    }

    private fun trimDisk() {
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        var size = files.sumOf { it.length() }
        if (size <= 40L * 1024 * 1024) return
        for (f in files.sortedBy { it.lastModified() }) { size -= f.length(); f.delete(); if (size <= 30L * 1024 * 1024) break }
    }
}

private var sharedStore: TileStore? = null
private fun tileStore(context: Context): TileStore = sharedStore ?: synchronized(TileStore::class.java) {
    sharedStore ?: TileStore(context.applicationContext.cacheDir, (context.applicationContext as TandemApp).repo.http).also { sharedStore = it }
}

// ------------------------------------------------------------------ camera

private const val TILE = 256.0
private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 18.0

/** The visible part of the world: centre (in 0..1 Web-Mercator coordinates) and a fractional zoom level. */
@Stable
class MapCamera(cx: Double = 0.5, cy: Double = 0.38, zoom: Double = 3.0) {
    var cx by mutableDoubleStateOf(cx)
    var cy by mutableDoubleStateOf(cy)
    var zoom by mutableDoubleStateOf(zoom)
    internal var viewW = 0f
    internal var viewH = 0f
    internal var k = 1.5f   // physical pixels of one tile pixel (grows with screen density so labels stay readable)

    internal fun worldPx(z: Double = zoom) = TILE * k * 2.0.pow(z)

    fun lat() = Geo.yToLat(cy)
    fun lon() = Geo.xToLon(cx)

    suspend fun animateTo(lat: Double, lon: Double, targetZoom: Double = zoom) {
        val x0 = cx; val y0 = cy; val z0 = zoom
        val x1 = Geo.lonToX(lon); val y1 = Geo.latToY(lat); val z1 = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        animate(0f, 1f, animationSpec = tween(550)) { v, _ ->
            cx = x0 + (x1 - x0) * v; cy = y0 + (y1 - y0) * v; zoom = z0 + (z1 - z0) * v
        }
    }

    suspend fun zoomBy(delta: Double) { animateTo(lat(), lon(), zoom + delta) }

    /** Frames every place (or a single one at street level). */
    suspend fun fit(places: List<Place>, animated: Boolean = true) {
        if (places.isEmpty()) return
        val xs = places.map { Geo.lonToX(it.lon) }; val ys = places.map { Geo.latToY(it.lat) }
        val mx = (xs.min() + xs.max()) / 2; val my = (ys.min() + ys.max()) / 2
        val dx = xs.max() - xs.min(); val dy = ys.max() - ys.min()
        val z = if (viewW <= 0f || (dx < 1e-6 && dy < 1e-6)) 14.0 else {
            val zx = ln(viewW * 0.72 / (max(dx, 1e-6) * TILE * k)) / ln(2.0)
            val zy = ln(viewH * 0.72 / (max(dy, 1e-6) * TILE * k)) / ln(2.0)
            min(zx, zy).coerceIn(MIN_ZOOM, 16.0)
        }
        if (animated) animateTo(Geo.yToLat(my), Geo.xToLon(mx), z)
        else { cx = mx; cy = my; zoom = z }
    }
}

// ------------------------------------------------------------------ the map

/** App-coloured heat: soft violet halo, pink body, coral-gold core. Overlapping places build up into a hotter spot. */
private val HALO = Color(0xFF8B5CF6)
private val BODY = Color(0xFFF2476F)
private val CORE = Color(0xFFFFB347)

/**
 * A slippy map of the couple's places: OpenStreetMap tiles tinted to the app's dusk colours, a glowing heat layer
 * (bigger and hotter for places you visit often and rate highly), category pins, pan / pinch / double-tap zoom,
 * tap a pin to select it and long-press to drop a new pin.
 */
@Composable
fun PlacesMap(
    places: List<Place>,
    camera: MapCamera,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    heat: Boolean = true,
    pending: Pair<Double, Double>? = null,
    onSelect: (String?) -> Unit = {},
    onLongPress: ((lat: Double, lon: Double) -> Unit)? = null,
) {
    val c = T.colors
    val ctx = LocalContext.current
    val store = remember { tileStore(ctx) }
    val density = LocalDensity.current.density
    camera.k = (density / 1.5f).coerceIn(1f, 2f)
    val dark = c.isDark
    val tileFilter = remember(dark) { ColorFilter.colorMatrix(ColorMatrix(if (dark) DARK_TILE else LIGHT_TILE)) }
    val label = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val currentPlaces by rememberUpdatedState(places)
    val onSelectNow by rememberUpdatedState(onSelect)
    val onLongNow by rememberUpdatedState(onLongPress)

    Box(modifier.background(if (dark) Color(0xFF1D1850) else Color(0xFFE9E1F5)).clipToBounds()) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val old = camera.worldPx()
                        val nz = (camera.zoom + ln(zoomChange.toDouble()) / ln(2.0)).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        val nw = camera.worldPx(nz)
                        val ox = (centroid.x - size.width / 2f) / old
                        val oy = (centroid.y - size.height / 2f) / old
                        val wx = camera.cx + ox; val wy = camera.cy + oy
                        camera.zoom = nz
                        camera.cx = (wx - (centroid.x - size.width / 2f) / nw - pan.x / nw).coerceIn(0.0, 1.0)
                        camera.cy = (wy - (centroid.y - size.height / 2f) / nw - pan.y / nw).coerceIn(0.0, 1.0)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { o ->
                            val w = camera.worldPx()
                            var best: Place? = null; var bestD = (26 * density).toDouble()
                            for (p in currentPlaces) {
                                val sx = size.width / 2.0 + (Geo.lonToX(p.lon) - camera.cx) * w
                                val sy = size.height / 2.0 + (Geo.latToY(p.lat) - camera.cy) * w
                                val d = sqrt((sx - o.x) * (sx - o.x) + (sy - o.y) * (sy - o.y))
                                if (d < bestD) { bestD = d; best = p }
                            }
                            onSelectNow(best?.id)
                        },
                        onLongPress = { o ->
                            val w = camera.worldPx()
                            val lon = Geo.xToLon(camera.cx + (o.x - size.width / 2.0) / w)
                            val lat = Geo.yToLat(camera.cy + (o.y - size.height / 2.0) / w)
                            onLongNow?.invoke(lat, lon)
                        },
                        onDoubleTap = { o ->
                            val old = camera.worldPx()
                            val nz = (camera.zoom + 1).coerceAtMost(MAX_ZOOM)
                            val nw = camera.worldPx(nz)
                            val wx = camera.cx + (o.x - size.width / 2f) / old
                            val wy = camera.cy + (o.y - size.height / 2f) / old
                            camera.zoom = nz
                            camera.cx = (wx - (o.x - size.width / 2f) / nw).coerceIn(0.0, 1.0)
                            camera.cy = (wy - (o.y - size.height / 2f) / nw).coerceIn(0.0, 1.0)
                        },
                    )
                },
        ) {
            camera.viewW = size.width; camera.viewH = size.height
            @Suppress("UNUSED_VARIABLE") val tick = store.version   // redraw when tiles arrive
            val w = camera.worldPx()
            drawTiles(camera, w, store, tileFilter)
            if (heat) drawHeat(camera, w, places, density)
            drawPins(camera, w, places, selectedId, pending, density, label, c.sheet, c.accent)
        }
        Text(
            "© OpenStreetMap contributors", style = Type.caption.copy(fontSize = 10.sp), color = c.secondary,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                .background(c.sheet.copy(alpha = .75f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun DrawScope.drawTiles(cam: MapCamera, w: Double, store: TileStore, filter: ColorFilter) {
    val tz = floor(cam.zoom).toInt().coerceIn(MIN_ZOOM.toInt(), MAX_ZOOM.toInt())
    val n = 1 shl tz
    val tileSize = w / n
    val left = cam.cx - size.width / 2.0 / w; val right = cam.cx + size.width / 2.0 / w
    val top = cam.cy - size.height / 2.0 / w; val bottom = cam.cy + size.height / 2.0 / w
    val x0 = floor(left * n).toInt().coerceAtLeast(0); val x1 = floor(right * n).toInt().coerceAtMost(n - 1)
    val y0 = floor(top * n).toInt().coerceAtLeast(0); val y1 = floor(bottom * n).toInt().coerceAtMost(n - 1)
    for (ty in y0..y1) for (tx in x0..x1) {
        val sx0 = size.width / 2.0 + (tx.toDouble() / n - cam.cx) * w
        val sy0 = size.height / 2.0 + (ty.toDouble() / n - cam.cy) * w
        val dstOff = IntOffset(floor(sx0).toInt(), floor(sy0).toInt())
        val dstSize = IntSize(ceil(sx0 + tileSize).toInt() - dstOff.x, ceil(sy0 + tileSize).toInt() - dstOff.y)
        val img = store.peek(tz, tx, ty, fetch = true)
        if (img != null) {
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), dstOff, dstSize, filterQuality = FilterQuality.Medium, colorFilter = filter)
        } else {
            // not here yet: show a blurry stand-in from a coarser tile that is already cached
            for (up in 1..3) {
                if (tz - up < 0) break
                val parent = store.peek(tz - up, tx shr up, ty shr up, fetch = false) ?: continue
                val part = parent.width shr up
                val ox = (tx and ((1 shl up) - 1)) * part; val oy = (ty and ((1 shl up) - 1)) * part
                drawImage(parent, IntOffset(ox, oy), IntSize(part, part), dstOff, dstSize, filterQuality = FilterQuality.Low, colorFilter = filter)
                break
            }
        }
    }
}

private fun DrawScope.drawHeat(cam: MapCamera, w: Double, places: List<Place>, density: Float) {
    val spots = places.map { p ->
        val sx = (size.width / 2.0 + (Geo.lonToX(p.lon) - cam.cx) * w).toFloat()
        val sy = (size.height / 2.0 + (Geo.latToY(p.lat) - cam.cy) * w).toFloat()
        Triple(Offset(sx, sy), p, 0)
    }
    // pass 1: wide violet halo, pass 2: pink body, pass 3: warm core — overlaps stack into hotter colours
    for (pass in 0..2) {
        for ((pos, p, _) in spots) {
            if (pos.x < -400 || pos.y < -400 || pos.x > size.width + 400 || pos.y > size.height + 400) continue
            val visits = p.visits.coerceIn(1, 12)
            val rating = ((p.avg ?: 3.5) / 5.0).toFloat()
            val intensity = ((0.45f + 0.08f * min(visits, 6)) * (0.7f + 0.3f * rating)).coerceIn(0.3f, 1f)
            val baseR = (34f + 7f * sqrt((visits - 1).toFloat())) * density
            val (color, scale, alpha) = when (pass) {
                0 -> Triple(HALO, 1.9f, .30f)
                1 -> Triple(BODY, 1.2f, .45f)
                else -> Triple(CORE, .6f, .60f)
            }
            val r = baseR * scale
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = alpha * intensity), color.copy(alpha = alpha * intensity * .35f), Color.Transparent), center = pos, radius = r),
                radius = r, center = pos,
            )
        }
    }
}

private fun DrawScope.drawPins(
    cam: MapCamera, w: Double, places: List<Place>, selectedId: String?, pending: Pair<Double, Double>?,
    density: Float, paint: Paint, ring: Color, accent: Color,
) {
    fun at(lat: Double, lon: Double) = Offset(
        (size.width / 2.0 + (Geo.lonToX(lon) - cam.cx) * w).toFloat(), (size.height / 2.0 + (Geo.latToY(lat) - cam.cy) * w).toFloat(),
    )
    val ordered = places.sortedBy { if (it.id == selectedId) 1 else 0 }
    for (p in ordered) {
        val pos = at(p.lat, p.lon)
        if (pos.x < -40 || pos.y < -40 || pos.x > size.width + 40 || pos.y > size.height + 40) continue
        val sel = p.id == selectedId
        val r = (if (sel) 17f else 13f) * density
        val cat = PlaceCategory.from(p.category)
        drawCircle(Color.Black.copy(alpha = .18f), r + 2 * density, pos + Offset(0f, 2 * density))
        drawCircle(ring, r + 2.5f * density, pos)
        drawCircle(cat.tint, r, pos)
        if (sel) drawCircle(accent, r + 5f * density, pos, style = Stroke(2.5f * density))
        drawIntoCanvas { cv ->
            paint.textSize = r * 1.05f
            cv.nativeCanvas.drawText(cat.emoji, pos.x, pos.y + paint.textSize * 0.35f, paint)
        }
    }
    if (pending != null) {
        val pos = at(pending.first, pending.second)
        drawCircle(accent.copy(alpha = .25f), 22f * density, pos)
        drawCircle(accent, 9f * density, pos)
        drawCircle(Color.White, 4f * density, pos)
    }
}

// Colour matrices (4x5, row-major, offsets in 0..255) that restyle the OSM tiles into the app's dusk look.
// light: a bit desaturated, pulled toward lavender. dark: inverted, then tinted deep violet (land darkest, water a little lighter).
private val LIGHT_TILE = floatArrayOf(
    .665f, .277f, .028f, 0f, 8f,
    .080f, .833f, .027f, 0f, 4f,
    .085f, .286f, .629f, 0f, 14f,
    0f, 0f, 0f, 1f, 0f,
)
private val DARK_TILE = floatArrayOf(
    -.90f, 0f, 0f, 0f, 247f,
    0f, -.80f, 0f, 0f, 218f,
    0f, 0f, -1.30f, 0f, 386f,
    0f, 0f, 0f, 1f, 0f,
)
