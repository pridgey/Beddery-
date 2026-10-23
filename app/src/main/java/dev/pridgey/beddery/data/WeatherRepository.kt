package dev.pridgey.beddery.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDateTime
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class HourForecast(val time: LocalDateTime, val temp: Int, val code: Int, val isDay: Boolean)

data class WeatherData(
    val temp: Int,
    val feelsLike: Int,
    val code: Int,
    val isDay: Boolean,
    val high: Int,
    val low: Int,
    val precipitationChance: Int?,
    val hourly: List<HourForecast>,
    val fetchedAtMs: Long,
    /** Every hour from now through tomorrow night. */
    val allHours: List<HourForecast> = emptyList(),
)

/** Forecasts from Open-Meteo (free, no API key). Cached in memory for 30 minutes. */
object WeatherRepository {
    private const val CACHE_MS = 30 * 60 * 1000L
    private val mutex = Mutex()
    private var cacheKey: String? = null
    private var cached: WeatherData? = null

    suspend fun forecast(location: WeatherLocation, unit: TempUnit): Result<WeatherData> = mutex.withLock {
        val key = "${location.latitude},${location.longitude},$unit"
        val hit = cached
        if (hit != null && key == cacheKey && System.currentTimeMillis() - hit.fetchedAtMs < CACHE_MS) {
            return Result.success(hit)
        }
        runCatching { fetch(location, unit) }.onSuccess {
            cached = it
            cacheKey = key
        }
    }

    private suspend fun fetch(location: WeatherLocation, unit: TempUnit): WeatherData = withContext(Dispatchers.IO) {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast?")
            append("latitude=").append(location.latitude)
            append("&longitude=").append(location.longitude)
            append("&current=temperature_2m,apparent_temperature,weather_code,is_day")
            append("&hourly=temperature_2m,weather_code,is_day")
            append("&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            append("&timezone=auto&forecast_days=2")
            if (unit == TempUnit.FAHRENHEIT) append("&temperature_unit=fahrenheit")
        }
        val json = JSONObject(httpGet(url))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val hourly = json.getJSONObject("hourly")

        val nowHour = current.getString("time").take(13) // "2026-10-22T14"
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val days = hourly.getJSONArray("is_day")
        var start = 0
        for (i in 0 until times.length()) {
            if (times.getString(i).startsWith(nowHour)) {
                start = i; break
            }
        }
        val hours = (1..5).mapNotNull { step ->
            val i = start + step * 2
            if (i >= times.length()) null
            else HourForecast(
                time = LocalDateTime.parse(times.getString(i)),
                temp = temps.getDouble(i).roundToInt(),
                code = codes.getInt(i),
                isDay = days.getInt(i) == 1,
            )
        }
        val all = (start until times.length()).map { i ->
            HourForecast(LocalDateTime.parse(times.getString(i)), temps.getDouble(i).roundToInt(), codes.getInt(i), days.getInt(i) == 1)
        }
        val precip = daily.optJSONArray("precipitation_probability_max")
        WeatherData(
            temp = current.getDouble("temperature_2m").roundToInt(),
            feelsLike = current.getDouble("apparent_temperature").roundToInt(),
            code = current.getInt("weather_code"),
            isDay = current.getInt("is_day") == 1,
            high = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            precipitationChance = precip?.takeIf { !it.isNull(0) }?.getInt(0),
            hourly = hours,
            fetchedAtMs = System.currentTimeMillis(),
            allHours = all,
        )
    }

    suspend fun searchCity(query: String): Result<List<WeatherLocation>> = runCatching {
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            val lang = Locale.getDefault().language.ifBlank { "en" }
            val json = JSONObject(
                httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=$lang&format=json"),
            )
            val results = json.optJSONArray("results") ?: return@withContext emptyList()
            (0 until results.length()).map { i ->
                val r = results.getJSONObject(i)
                val label = listOfNotNull(
                    r.optString("name").takeIf { it.isNotBlank() },
                    r.optString("admin1").takeIf { it.isNotBlank() },
                    r.optString("country").takeIf { it.isNotBlank() },
                ).distinct().joinToString(", ")
                WeatherLocation(label, r.getDouble("latitude"), r.getDouble("longitude"))
            }
        }
    }

    /** One-shot coarse location, named via the platform geocoder when possible. Caller must hold location permission. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): WeatherLocation? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val location: Location? = withTimeoutOrNull(15_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val provider = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.hasProvider(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> LocationManager.GPS_PROVIDER
                }
                suspendCancellableCoroutine { cont ->
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) }
                }
            } else null
        } ?: lastKnown(lm)
        location ?: return null
        val name = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.let {
                    it.locality ?: it.subAdminArea ?: it.adminArea
                }
            }.getOrNull()
        }
        return WeatherLocation(name ?: "", location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** WMO weather interpretation codes, grouped into what we can draw. */
enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

fun weatherKind(code: Int): WeatherKind = when (code) {
    0 -> WeatherKind.CLEAR
    1, 2 -> WeatherKind.PARTLY_CLOUDY
    3 -> WeatherKind.CLOUDY
    45, 48 -> WeatherKind.FOG
    in 51..57 -> WeatherKind.DRIZZLE
    in 61..67, in 80..82 -> WeatherKind.RAIN
    in 71..77, 85, 86 -> WeatherKind.SNOW
    in 95..99 -> WeatherKind.STORM
    else -> WeatherKind.CLOUDY
}
