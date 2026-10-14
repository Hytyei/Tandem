package app.tandem.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

/** Web-Mercator maths for the map: world coordinates are x,y in 0..1 (x west->east, y north->south). */
object Geo {
    private const val MAX_LAT = 85.0511
    fun lonToX(lon: Double) = (lon + 180.0) / 360.0
    fun latToY(lat: Double): Double {
        val l = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180.0
        return 0.5 - ln(tan(PI / 4 + l / 2)) / (2 * PI)
    }
    fun xToLon(x: Double) = x * 360.0 - 180.0
    fun yToLat(y: Double): Double = (2 * atan(exp((0.5 - y) * 2 * PI)) - PI / 2) * 180.0 / PI

    const val USER_AGENT = "Tandem-Android (https://github.com/Hytyei/Tandem)"
}

/** Last position the phone knows about, or null (no permission / no fix). Only called when the person taps "Use my location". */
@SuppressLint("MissingPermission")
fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) return null
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val best = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    return best?.let { it.latitude to it.longitude }
}
