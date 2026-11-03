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
import app.tandem.data.AddressKind
import app.tandem.data.SavedAddress
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.BlendMode
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
/** Where the map pictures come from. Only OpenStreetMap; every look is made on the phone from those tiles. */
internal enum class TileStyle(val dir: String) {
    Osm("osm-tiles");

    fun url(z: Int, x: Int, y: Int): String = "https://tile.openstreetmap.org/$z/$x/$y.png"
}

/**
 * The looks of the map. All of them are painted on the phone from the regular OpenStreetMap tiles: each pixel is sorted into
 * water, green, road, building, label or ground and recoloured with the look's palette, so no third-party tile service or key is involved.
 * [Street] is the plain map with a soft colour filter.
 */
enum class MapStyle(
    val label: String, val dark: Boolean,
    internal val ground: Int, internal val water: Int, internal val green: Int,
    internal val building: Int, internal val road: Int, internal val ink: Int,
) {
    Chart("Star chart", true, 0xFF15123A.toInt(), 0xFF0A0924.toInt(), 0xFF181B3E.toInt(), 0xFF1E1A48.toInt(), 0xFF4A4390.toInt(), 0xFF7D76C2.toInt()),
    Blueprint("Blueprint", true, 0xFF0F3463.toInt(), 0xFF0A2850.toInt(), 0xFF123B6E.toInt(), 0xFF164278.toInt(), 0xFF6F9FD8.toInt(), 0xFFA9C6EC.toInt()),
    Atlas("Atlas", false, 0xFFEFE6D4.toInt(), 0xFFC4D3D3.toInt(), 0xFFDCE0C2.toInt(), 0xFFE3D5BE.toInt(), 0xFFFBF7EE.toInt(), 0xFF8C7C62.toInt()),
    Street("Street", true, 0, 0, 0, 0, 0, 0);

    internal val background: Color get() = if (this == Street) Color(0xFF1D1850) else Color(ground)
}

private fun smooth(e0: Float, e1: Float, x: Float): Float { val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
private fun mix(a: Int, b: Int, t: Float): Int {
    if (t <= 0f) return a
    if (t >= 1f) return b
    val ar = a shr 16 and 255; val ag = a shr 8 and 255; val ab = a and 255
    val br = b shr 16 and 255; val bg = b shr 8 and 255; val bb = b and 255
    return (0xFF shl 24) or ((ar + (br - ar) * t).toInt() shl 16) or ((ag + (bg - ag) * t).toInt() shl 8) or (ab + (bb - ab) * t).toInt()
}

/**
 * Repaints one OpenStreetMap tile in [style]. Soft weights (not hard classes) keep the anti-aliased edges of roads and coastlines smooth.
 * The thresholds follow the OSM Carto colours: water #aad3df, parks #c8facc / #add19e, land #f2efe9, buildings #d9d0c9, roads white to orange.
 */
private fun stylize(src: android.graphics.Bitmap, style: MapStyle): android.graphics.Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) {
        val p = px[i]
        val r = p shr 16 and 255; val g = p shr 8 and 255; val b = p and 255
        val lum = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
        val mn = minOf(r, g, b); val mx = maxOf(r, g, b)
        var out = style.ground
        // buildings and other mid-light fills sit a step above the ground
        out = mix(out, style.building, smooth(.90f, .80f, lum) * smooth(.62f, .74f, lum))
        out = mix(out, style.green, smooth(6f, 22f, (g - maxOf(r, b)).toFloat()))
        out = mix(out, style.water, smooth(12f, 34f, (b - r).toFloat()) * smooth(4f, 14f, (g - r).toFloat()))
        // roads: near-white, or the warm yellow / orange / pink of bigger roads
        val warmRoad = smooth(.80f, .90f, lum) * smooth(20f, 50f, (mx - mn).toFloat()) * smooth(-4f, 10f, (r - b).toFloat())
        out = mix(out, style.road, maxOf(smooth(243f, 252f, mn.toFloat()), warmRoad))
        // labels and outlines are dark: they become a faint ink
        out = mix(out, style.ink, smooth(.55f, .30f, lum) * .75f)
        px[i] = out
    }
    return android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888)
}

private class TileStore(cacheRoot: File, private val http: OkHttpClient) {
    private val root = File(cacheRoot, "map-tiles").apply { mkdirs() }
    private val dir get() = root
    // sized by bytes (in KB); each look keeps its own painted copy of a tile
    private val memory = object : LruCache<Long, ImageBitmap>(48 * 1024) {
        override fun sizeOf(key: Long, value: ImageBitmap): Int = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()
    private val failedAt = ConcurrentHashMap<Long, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(4)
    /** Bumped whenever a tile arrives, so the map canvas redraws. */
    var version by mutableIntStateOf(0)
        private set

    init { scope.launch { trimDisk() } }

    private fun key(look: MapStyle, z: Int, x: Int, y: Int) = (look.ordinal.toLong() shl 56) or (z.toLong() shl 48) or (x.toLong() shl 24) or y.toLong()
    private fun file(st: TileStyle, z: Int, x: Int, y: Int) = File(dir, "${st.dir}/$z/$x/$y.png")

    /** The tile if it's already in memory; otherwise null, and a load is started (unless [fetch] is false). */
    fun peek(st: TileStyle, look: MapStyle, z: Int, x: Int, y: Int, fetch: Boolean): ImageBitmap? {
        val k = key(look, z, x, y)
        memory.get(k)?.let { return it }
        if (!fetch || !inFlight.add(k)) return null
        val failed = failedAt[k]
        if (failed != null && System.currentTimeMillis() - failed < 15_000) { inFlight.remove(k); return null }
        scope.launch {
            try {
                gate.withPermit {
                    val f = file(st, z, x, y)
                    var bytes: ByteArray? = if (f.exists() && System.currentTimeMillis() - f.lastModified() < 14L * 24 * 3600 * 1000) runCatching { f.readBytes() }.getOrNull() else null
                    if (bytes == null) {
                        val req = Request.Builder().url(st.url(z, x, y)).header("User-Agent", Geo.USER_AGENT).build()
                        bytes = runCatching { http.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null } }.getOrNull()
                        if (bytes != null) runCatching { f.parentFile?.mkdirs(); f.writeBytes(bytes) }
                    }
                    val raw = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    val bmp = raw?.let { if (look == MapStyle.Street) it else runCatching { stylize(it, look) }.getOrNull() ?: it }
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
    suspend fun fit(places: List<Place>, animated: Boolean = true) = fitPoints(places.map { it.lat to it.lon }, animated)

    /** Frames a set of (lat, lon) points. */
    suspend fun fitPoints(points: List<Pair<Double, Double>>, animated: Boolean = true) {
        if (points.isEmpty()) return
        val xs = points.map { Geo.lonToX(it.second) }; val ys = points.map { Geo.latToY(it.first) }
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
    /** Saved addresses (homes etc.), drawn as house-style pins ringed in the owner's colour. [meId] tells which are yours. */
    addresses: List<SavedAddress> = emptyList(),
    meId: String? = null,
    selectedAddressId: String? = null,
    onSelectAddress: (String?) -> Unit = {},
    /** Recorded routes of a live-tracked date. */
    paths: List<MapPath> = emptyList(),
    /** How the map itself is painted. */
    style: MapStyle = MapStyle.Chart,
    /** Routes of past tracked dates: drawn as faint trails, and (with [heat] on) they warm the heat layer along the way. */
    trails: List<MapPath> = emptyList(),
    /** Where each tracked date began, with a short date label shown once you zoom in. */
    dateMarks: List<DateMark> = emptyList(),
) {
    val c = T.colors
    val ctx = LocalContext.current
    val store = remember { tileStore(ctx) }
    val density = LocalDensity.current.density
    camera.k = (density / 1.5f).coerceIn(1f, 2f)
    val dark = style.dark
    val tileStyle = TileStyle.Osm
    val tileFilter = remember(style) { if (style == MapStyle.Street) ColorFilter.colorMatrix(ColorMatrix(DARK_TILE)) else null }
    val trailHeat = remember(trails) { sampleTrail(trails) }
    val label = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val currentPlaces by rememberUpdatedState(places)
    val currentAddresses by rememberUpdatedState(addresses)
    val onSelectAddressNow by rememberUpdatedState(onSelectAddress)
    val onSelectNow by rememberUpdatedState(onSelect)
    val onLongNow by rememberUpdatedState(onLongPress)

    Box(modifier.background(style.background).clipToBounds()) {
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
                            var bestAddr: SavedAddress? = null; var bestAD = (26 * density).toDouble()
                            for (a in currentAddresses) {
                                val sx = size.width / 2.0 + (Geo.lonToX(a.lon) - camera.cx) * w
                                val sy = size.height / 2.0 + (Geo.latToY(a.lat) - camera.cy) * w
                                val d = sqrt((sx - o.x) * (sx - o.x) + (sy - o.y) * (sy - o.y))
                                if (d < bestAD) { bestAD = d; bestAddr = a }
                            }
                            var best: Place? = null; var bestD = (26 * density).toDouble()
                            for (p in currentPlaces) {
                                val sx = size.width / 2.0 + (Geo.lonToX(p.lon) - camera.cx) * w
                                val sy = size.height / 2.0 + (Geo.latToY(p.lat) - camera.cy) * w
                                val d = sqrt((sx - o.x) * (sx - o.x) + (sy - o.y) * (sy - o.y))
                                if (d < bestD) { bestD = d; best = p }
                            }
                            // the closer of the two wins; selecting one clears the other
                            if (bestAddr != null && bestAD <= bestD) { onSelectAddressNow(bestAddr.id); onSelectNow(null) }
                            else { onSelectNow(best?.id); onSelectAddressNow(null) }
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
            drawTiles(camera, w, store, tileStyle, style, tileFilter)
            drawTrails(camera, w, trails, density)
            drawPaths(camera, w, paths, density)
            if (heat) {
                drawTrailHeat(camera, w, trailHeat, density, additive = dark)
                drawHeat(camera, w, places, density, additive = dark)
            }
            drawDateMarks(camera, w, dateMarks, density, if (dark) Color.White else Color(0xFF3A3020), if (dark) Color(0xCC15123A) else Color(0xE6FBF7EE))
            drawPins(camera, w, places, selectedId, pending, density, label, c.sheet, c.accent)
            drawAddresses(camera, w, addresses, meId, selectedAddressId, density, label, c.sheet, c.accent, c.me, c.partner, c.label)
        }
        Text(
            "© OpenStreetMap contributors", style = Type.caption.copy(fontSize = 10.sp), color = c.secondary,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                .background(c.sheet.copy(alpha = .75f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun DrawScope.drawTiles(cam: MapCamera, w: Double, store: TileStore, style: TileStyle, look: MapStyle, filter: ColorFilter?) {
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
        val img = store.peek(style, look, tz, tx, ty, fetch = true)
        if (img != null) {
            drawImage(img, IntOffset.Zero, IntSize(img.width, img.height), dstOff, dstSize, filterQuality = FilterQuality.Medium, colorFilter = filter)
        } else {
            // not here yet: show a blurry stand-in from a coarser tile that is already cached
            for (up in 1..3) {
                if (tz - up < 0) break
                val parent = store.peek(style, look, tz - up, tx shr up, ty shr up, fetch = false) ?: continue
                val part = parent.width shr up
                val ox = (tx and ((1 shl up) - 1)) * part; val oy = (ty and ((1 shl up) - 1)) * part
                drawImage(parent, IntOffset(ox, oy), IntSize(part, part), dstOff, dstSize, filterQuality = FilterQuality.Low, colorFilter = filter)
                break
            }
        }
    }
}

private fun DrawScope.drawHeat(cam: MapCamera, w: Double, places: List<Place>, density: Float, additive: Boolean) {
    val spots = places.map { p ->
        val sx = (size.width / 2.0 + (Geo.lonToX(p.lon) - cam.cx) * w).toFloat()
        val sy = (size.height / 2.0 + (Geo.latToY(p.lat) - cam.cy) * w).toFloat()
        Offset(sx, sy) to p
    }
    // On the dark map the layers add up like light (glow); on the light map they paint over it.
    val blend = if (additive) BlendMode.Plus else BlendMode.SrcOver
    // pass 0: wide violet halo, 1: pink body, 2: warm core, 3: pale-gold hot spot. Overlapping places stack into hotter colours.
    for (pass in 0..3) {
        for ((pos, p) in spots) {
            if (pos.x < -500 || pos.y < -500 || pos.x > size.width + 500 || pos.y > size.height + 500) continue
            val visits = p.visits.coerceIn(1, 12)
            val rating = ((p.avg ?: 3.5) / 5.0).toFloat()
            val intensity = ((0.50f + 0.08f * min(visits, 6)) * (0.72f + 0.28f * rating)).coerceIn(0.35f, 1f)
            if (pass == 3 && intensity < .72f) continue          // only the favourites get a hot spot
            val baseR = (42f + 9f * sqrt((visits - 1).toFloat())) * density
            val (color, scale, alpha) = when (pass) {
                0 -> Triple(HALO, 2.3f, if (additive) .30f else .38f)
                1 -> Triple(BODY, 1.35f, if (additive) .42f else .58f)
                2 -> Triple(CORE, .72f, if (additive) .55f else .72f)
                else -> Triple(Color(0xFFFFF1C9), .30f, if (additive) .55f else .65f)
            }
            val r = baseR * scale
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = (alpha * intensity).coerceAtMost(1f)), color.copy(alpha = alpha * intensity * .38f), Color.Transparent), center = pos, radius = r),
                radius = r, center = pos, blendMode = blend,
            )
        }
    }
}

/** A recorded route: a soft glow under a clean line, with a ring at the start and a dot at the end. */
private fun DrawScope.drawPaths(cam: MapCamera, w: Double, paths: List<MapPath>, density: Float) {
    fun at(lat: Double, lon: Double) = Offset(
        (size.width / 2.0 + (Geo.lonToX(lon) - cam.cx) * w).toFloat(), (size.height / 2.0 + (Geo.latToY(lat) - cam.cy) * w).toFloat(),
    )
    for (p in paths) {
        if (p.points.size < 2) continue
        val path = androidx.compose.ui.graphics.Path()
        p.points.forEachIndexed { i, (lat, lon) -> val o = at(lat, lon); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
        drawPath(path, p.color.copy(alpha = .28f), style = Stroke(11f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, p.color, style = Stroke(4.2f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val s0 = at(p.points.first().first, p.points.first().second); val s1 = at(p.points.last().first, p.points.last().second)
        drawCircle(Color.White, 7f * density, s0); drawCircle(p.color, 4.5f * density, s0)
        drawCircle(Color.White, 8f * density, s1); drawCircle(p.color, 5.5f * density, s1)
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

private fun DrawScope.drawAddresses(
    cam: MapCamera, w: Double, addresses: List<SavedAddress>, meId: String?, selectedId: String?, density: Float, paint: Paint,
    ring: Color, accent: Color, meColor: Color, partnerColor: Color, labelColor: Color,
) {
    for (a in addresses.sortedBy { if (it.id == selectedId) 1 else 0 }) {
        val pos = Offset(
            (size.width / 2.0 + (Geo.lonToX(a.lon) - cam.cx) * w).toFloat(), (size.height / 2.0 + (Geo.latToY(a.lat) - cam.cy) * w).toFloat(),
        )
        if (pos.x < -80 || pos.y < -80 || pos.x > size.width + 80 || pos.y > size.height + 80) continue
        val sel = a.id == selectedId
        val tint = if (a.userId == meId) meColor else partnerColor
        val half = (if (sel) 15f else 12f) * density
        val corner = androidx.compose.ui.geometry.CornerRadius(half * .55f)
        drawRoundRect(Color.Black.copy(alpha = .18f), pos - Offset(half, half) + Offset(0f, 2 * density), androidx.compose.ui.geometry.Size(half * 2, half * 2), corner)
        drawRoundRect(ring, pos - Offset(half + 2.5f * density, half + 2.5f * density), androidx.compose.ui.geometry.Size(half * 2 + 5 * density, half * 2 + 5 * density), androidx.compose.ui.geometry.CornerRadius((half + 2.5f * density) * .55f))
        drawRoundRect(tint, pos - Offset(half, half), androidx.compose.ui.geometry.Size(half * 2, half * 2), corner)
        if (sel) drawRoundRect(accent, pos - Offset(half + 6 * density, half + 6 * density), androidx.compose.ui.geometry.Size(half * 2 + 12 * density, half * 2 + 12 * density),
            androidx.compose.ui.geometry.CornerRadius((half + 6 * density) * .55f), style = Stroke(2.5f * density))
        drawIntoCanvas { cv ->
            paint.textSize = half * 1.15f
            cv.nativeCanvas.drawText(AddressKind.from(a.kind).emoji, pos.x, pos.y + paint.textSize * 0.35f, paint)
            // a small name tag below the pin once you are zoomed in enough to care
            if (sel || cam.zoom >= 13.0) {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 11f * density; isFakeBoldText = true; color = labelColor.toArgb() }
                val text = a.label.take(22)
                val tw = tp.measureText(text)
                val ty = pos.y + half + 20f * density
                val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ring.copy(alpha = .92f).toArgb() }
                cv.nativeCanvas.drawRoundRect(pos.x - tw / 2 - 6 * density, ty - 12f * density, pos.x + tw / 2 + 6 * density, ty + 5f * density, 8 * density, 8 * density, bg)
                cv.nativeCanvas.drawText(text, pos.x, ty, tp)
            }
        }
    }
}

// Colour matrices (4x5, row-major, offsets in 0..255) that restyle the OSM tiles into the app's dusk look.
// The Street look: inverted, then tinted deep violet (land darkest, water a little lighter).
private val DARK_TILE = floatArrayOf(
    -.90f, 0f, 0f, 0f, 247f,
    0f, -.80f, 0f, 0f, 218f,
    0f, 0f, -1.30f, 0f, 386f,
    0f, 0f, 0f, 1f, 0f,
)

/** One recorded route to draw on the map: (lat, lon) points in order. */
class MapPath(val color: Color, val points: List<Pair<Double, Double>>)

/** A tracked date's starting point on the Heatmap. */
class DateMark(val lat: Double, val lon: Double, val label: String, val color: Color)

/** Spreads the trail points out (at most one every ~40 m and 900 in all), so long walks warm the map evenly and stay cheap to draw. */
private fun sampleTrail(trails: List<MapPath>): List<Pair<Double, Double>> {
    val out = ArrayList<Pair<Double, Double>>()
    for (t in trails) {
        var last: Pair<Double, Double>? = null
        for (p in t.points) {
            val l = last
            if (l == null || abs(l.first - p.first) + abs(l.second - p.second) > 0.00045) { out.add(p); last = p }
        }
    }
    if (out.size <= 900) return out
    val step = out.size / 900.0
    return List(900) { out[(it * step).toInt()] }
}

/** Past date routes: a thin, quiet line in the walker's colour. */
private fun DrawScope.drawTrails(cam: MapCamera, w: Double, trails: List<MapPath>, density: Float) {
    for (p in trails) {
        if (p.points.size < 2) continue
        val path = androidx.compose.ui.graphics.Path()
        p.points.forEachIndexed { i, (lat, lon) ->
            val x = (size.width / 2.0 + (Geo.lonToX(lon) - cam.cx) * w).toFloat(); val y = (size.height / 2.0 + (Geo.latToY(lat) - cam.cy) * w).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, p.color.copy(alpha = .55f), style = Stroke(1.6f * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** The warmth that walking leaves behind: small soft dabs along every trail, which stack up where you walk often. */
private fun DrawScope.drawTrailHeat(cam: MapCamera, w: Double, pts: List<Pair<Double, Double>>, density: Float, additive: Boolean) {
    if (pts.isEmpty()) return
    val blend = if (additive) BlendMode.Plus else BlendMode.SrcOver
    val r = (14f + 2f * (cam.zoom - 12).coerceIn(0.0, 5.0).toFloat()) * density
    for (pass in 0..1) {
        val color = if (pass == 0) HALO else BODY
        val rr = if (pass == 0) r * 1.8f else r
        val a = if (pass == 0) (if (additive) .16f else .18f) else (if (additive) .22f else .24f)
        // one dab per radius of screen distance, so a single walk glows evenly and only walking the same streets again builds heat
        var last = Offset(Float.NaN, Float.NaN)
        for ((lat, lon) in pts) {
            val pos = Offset((size.width / 2.0 + (Geo.lonToX(lon) - cam.cx) * w).toFloat(), (size.height / 2.0 + (Geo.latToY(lat) - cam.cy) * w).toFloat())
            if (pos.x < -rr || pos.y < -rr || pos.x > size.width + rr || pos.y > size.height + rr) continue
            if (!last.x.isNaN() && (pos - last).getDistance() < r * .9f) continue
            last = pos
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a), Color.Transparent), center = pos, radius = rr), radius = rr, center = pos, blendMode = blend)
        }
    }
}

/** A small flag where each date began; the date itself shows from street level, so the overview stays calm. */
private fun DrawScope.drawDateMarks(cam: MapCamera, w: Double, marks: List<DateMark>, density: Float, ink: Color, plate: Color) {
    if (marks.isEmpty()) return
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT; textSize = 10.5f * density; color = ink.toArgb() }
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = plate.toArgb() }
    for (m in marks) {
        val pos = Offset((size.width / 2.0 + (Geo.lonToX(m.lon) - cam.cx) * w).toFloat(), (size.height / 2.0 + (Geo.latToY(m.lat) - cam.cy) * w).toFloat())
        if (pos.x < -60 || pos.y < -60 || pos.x > size.width + 60 || pos.y > size.height + 60) continue
        drawCircle(plate, 5.5f * density, pos)
        drawCircle(m.color, 3.5f * density, pos)
        if (cam.zoom >= 12.5) drawIntoCanvas { cv ->
            val tw = tp.measureText(m.label)
            val x = pos.x + 9 * density; val y = pos.y + 3.5f * density
            cv.nativeCanvas.drawRoundRect(x - 5 * density, y - 11 * density, x + tw + 5 * density, y + 4 * density, 6 * density, 6 * density, bg)
            cv.nativeCanvas.drawText(m.label, x, y, tp)
        }
    }
}
