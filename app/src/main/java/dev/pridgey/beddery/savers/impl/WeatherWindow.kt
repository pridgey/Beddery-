package dev.pridgey.beddery.savers.impl

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.HourForecast
import dev.pridgey.beddery.data.WeatherData
import dev.pridgey.beddery.data.WeatherKind
import dev.pridgey.beddery.data.WeatherRepository
import dev.pridgey.beddery.data.weatherKind
import dev.pridgey.beddery.savers.ClockOverlay
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.Sketch
import dev.pridgey.beddery.savers.SketchScene
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.mixc
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rgb
import dev.pridgey.beddery.savers.rgba
import dev.pridgey.beddery.savers.sFlicker
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sLerp
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAU = (PI * 2).toFloat()

/** What the window shows: the forecast for when you get up. */
private data class WakeView(val at: LocalDateTime, val kind: WeatherKind, val temp: Int, val high: Int?, val low: Int?, val sample: Boolean)

private fun sampleView(now: LocalDateTime, wake: LocalDateTime, seed: Int): WakeView {
    val kinds = listOf(WeatherKind.RAIN, WeatherKind.CLEAR, WeatherKind.SNOW, WeatherKind.PARTLY_CLOUDY, WeatherKind.FOG, WeatherKind.STORM)
    return WakeView(wake, kinds[seed % kinds.size], 9, 14, 6, sample = true)
}

/** When you'll wake: the next alarm if there is one in the next day, otherwise 7 AM. */
private fun wakeTime(now: LocalDateTime, alarm: LocalDateTime?): LocalDateTime {
    if (alarm != null && Duration.between(now, alarm).toHours() < 24) return alarm
    val seven = now.toLocalDate().atTime(LocalTime.of(7, 0))
    return if (seven.isAfter(now)) seven else seven.plusDays(1)
}

private fun viewFor(data: WeatherData, wake: LocalDateTime): WakeView {
    val hours = data.allHours.ifEmpty { data.hourly }
    val pick: HourForecast? = hours.minByOrNull { abs(Duration.between(it.time, wake).toMinutes()) }
    val day = hours.filter { it.time.toLocalDate() == wake.toLocalDate() && it.time.hour in 6..21 }
    return WakeView(
        at = wake,
        kind = weatherKind(pick?.code ?: data.code),
        temp = pick?.temp ?: data.temp,
        high = day.maxOfOrNull { it.temp },
        low = day.minOfOrNull { it.temp },
        sample = false,
    )
}

/**
 * Your bedroom window at night, looking out on the town as the forecast says it will be when you
 * wake: a clear dawn, cloud, fog, drizzle, rain, snow or a storm. The forecast sits under the clock.
 */
@Composable
fun WeatherWindowSaver(ctx: SaverContext) {
    val location = ctx.settings.weatherLocation
    val unit = ctx.settings.tempUnit
    var data by remember(location, unit) { mutableStateOf<WeatherData?>(null) }
    var failed by remember(location, unit) { mutableStateOf(false) }
    LaunchedEffect(location, unit) {
        while (location != null) {
            val r = WeatherRepository.forecast(location, unit).getOrNull()
            if (r != null) data = r
            failed = r == null && data == null
            delay(if (r != null) 15 * 60_000L else 2 * 60_000L)
        }
    }
    val time = rememberFrameTime()
    val wake = remember(ctx.now.hour, ctx.now.minute / 10, ctx.nextAlarm) { wakeTime(ctx.now, ctx.nextAlarm) }
    val d = data
    val view: WakeView? = when {
        d != null -> remember(d, wake) { viewFor(d, wake) }
        ctx.demo -> sampleView(ctx.now, wake, if (ctx.preview) 0 else (ctx.now.minute / 2))
        else -> null
    }
    val line = when {
        view != null -> {
            val label = stringResource(weatherLabel(view.kind))
            val at = formatTimeWithAmPm(view.at.toLocalTime(), ctx.use24h)
            if (view.high != null) stringResource(R.string.window_line, label, at, view.temp, view.high) else stringResource(R.string.window_line_nohigh, label, at, view.temp)
        }
        location == null -> stringResource(R.string.weather_no_location)
        failed -> stringResource(R.string.weather_error)
        else -> stringResource(R.string.weather_loading)
    }
    val kind = view?.kind ?: WeatherKind.CLOUDY
    Box(Modifier.fillMaxSize()) {
        SketchScene(time) { t -> drawWeatherWindow(t, kind) }
        if (!ctx.preview) {
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ClockOverlay(ctx)
                Text(line, color = Color(0xCCFFE2B4), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp))
            }
        }
    }
}

private val wp = Path()
private val wq = Path()

private fun hexMix(a: Int, b: Int, t: Float) = mixc(rgb(a), rgb(b), t.coerceIn(0f, 1f)) and 0xFFFFFF

/** 0..1: a storm's lightning, a double flicker every twelve seconds or so. */
private fun stormFlash(t: Float): Float {
    val p = t % 12.7f
    return if (p < 0.08f) 1f else if (p > 0.18f && p < 0.26f) 0.7f else if (p < 0.6f) max(0f, 0.4f - (p - 0.26f) * 1.2f) else 0f
}

private fun Sketch.drawWeatherWindow(t: Float, kind: WeatherKind) {
    val wet = kind == WeatherKind.DRIZZLE || kind == WeatherKind.RAIN || kind == WeatherKind.STORM
    val wx: Float; val wy: Float; val ww: Float; val wh: Float
    if (land) { wx = W * 0.24f; wy = H * 0.07f; ww = W * 0.52f; wh = H * 0.52f } else { wx = W * 0.1f; wy = H * 0.06f; ww = W * 0.8f; wh = H * 0.46f }
    val sill = wy + wh

    rect(0f, 0f, W, H, vgrad(0f, H, 0f to rgb(0x14141e), 0.6f to rgb(0x1a1822), 1f to rgb(0x0e0c12)))

    // ---- Outside, at wake-up time.
    c.save(); c.clipRect(wx, wy, wx + ww, wy + wh)
    windowView(t, kind, wx, wy, ww, wh)
    if (wet) windowDrops(t, wx, wy, ww, wh, if (kind == WeatherKind.DRIZZLE) 0.5f else 1f)
    if (kind == WeatherKind.SNOW || kind == WeatherKind.FOG) {
        val snow = kind == WeatherKind.SNOW
        for ((cx, cy) in arrayOf(wx to wy + wh, wx + ww to wy + wh, wx to wy, wx + ww to wy)) glow(cx, cy, U * if (snow) 0.22f else 0.12f, 0xe0e8f4, if (snow) 0.4f else 0.15f, additive = false)
    }
    wp.reset(); wp.moveTo(wx + ww * 0.55f, wy); wp.lineTo(wx + ww * 0.7f, wy); wp.lineTo(wx + ww * 0.4f, wy + wh); wp.lineTo(wx + ww * 0.25f, wy + wh); wp.close()
    fill(wp, rgba(0xFFFFFF, 0.03f))
    c.restore()
    if (kind == WeatherKind.STORM) { val f = stormFlash(t); if (f > 0) rect(0f, 0f, W, H, rgba(0xC8D2FF, 0.12f * f)) }

    // ---- The frame: painted wood, four panes, a deep sill.
    val fw = U * 0.022f
    wp.reset(); wp.addRect(wx, wy, wx + ww, wy + wh, Path.Direction.CW); stroke(wp, rgb(0xd8d2c8), fw)
    line(wx + ww / 2, wy, wx + ww / 2, wy + wh, rgb(0xd8d2c8), fw * 0.55f)
    line(wx, wy + wh * 0.42f, wx + ww, wy + wh * 0.42f, rgb(0xd8d2c8), fw * 0.55f)
    rect(wx + ww + fw / 2, wy - fw / 2, fw * 0.4f, wh + fw, rgba(0, 0.35f))
    rect(wx - U * 0.06f, sill + fw / 2, ww + U * 0.12f, U * 0.03f, rgb(0xc8c0b4))
    rect(wx - U * 0.06f, sill + fw / 2 + U * 0.03f, ww + U * 0.12f, U * 0.012f, rgba(0, 0.4f))
    if (kind == WeatherKind.SNOW) {
        wp.reset(); wp.moveTo(wx, sill - fw * 0.5f)
        var x = wx
        while (x <= wx + ww) { wp.lineTo(x, sill - fw * 0.5f - U * (0.012f + 0.01f * abs(sin(x * 0.05f)))); x += 6 }
        wp.lineTo(wx + ww, sill - fw * 0.5f); wp.close(); fill(wp, rgb(0xeef2fa))
    }

    // ---- String lights looped along the top of the frame, gently twinkling.
    val n = 13
    wp.reset()
    for (i in 0..n * 4) {
        val q = i / (n * 4f)
        val x = wx - U * 0.04f + (ww + U * 0.08f) * q
        val y = wy - U * 0.02f + abs(sin(q * PI.toFloat() * 3)) * U * 0.035f
        if (i == 0) wp.moveTo(x, y) else wp.lineTo(x, y)
    }
    stroke(wp, rgba(0x1E1E1E, 0.9f), 1f)
    for (i in 0 until n) {
        val q = (i + 0.5f) / n
        val x = wx - U * 0.04f + (ww + U * 0.08f) * q
        val y = wy - U * 0.02f + abs(sin(q * PI.toFloat() * 3)) * U * 0.035f + U * 0.008f
        val tw = 0.75f + 0.25f * sin(t * (0.7f + sHash(i.toFloat()) * 0.8f) + i * 2)
        ellipse(x, y, U * 0.005f, U * 0.008f, 0f, rgb(0xffe0a0))
        glow(x, y, U * 0.04f, 0xffc070, 0.35f * tw)
    }
    glow(wx + ww / 2, wy, ww * 0.7f, 0xffb060, 0.08f, sy = 0.4f)

    // ---- On the sill: a trailing plant and a candle jar, in silhouette against the glass.
    val sx = wx + ww * 0.08f
    val sy = sill + fw / 2
    wp.reset(); wp.moveTo(sx - U * 0.03f, sy - U * 0.05f); wp.lineTo(sx + U * 0.03f, sy - U * 0.05f); wp.lineTo(sx + U * 0.024f, sy); wp.lineTo(sx - U * 0.024f, sy); wp.close()
    fill(wp, rgb(0x3a3440))
    for (v in 0 until 7) {
        val a = -PI.toFloat() / 2 + (v - 3) * 0.32f + sin(t * 0.3f + v) * 0.02f
        val len = U * (0.06f + 0.04f * sHash(v * 3f))
        val lx = sx + kotlin.math.cos(a) * len
        val ly = sy - U * 0.05f + sin(a) * len
        wq.reset(); wq.moveTo(sx, sy - U * 0.05f); wq.quadTo(sx + kotlin.math.cos(a) * len * 0.3f, sy - U * 0.05f + sin(a) * len * 0.8f, lx, ly)
        stroke(wq, rgb(0x1e2a22), U * 0.006f)
        ellipse(lx, ly, U * 0.012f, U * 0.022f, a + PI.toFloat() / 2, rgb(0x24322a))
    }
    val cx = wx + ww * 0.86f
    roundRect(cx - U * 0.022f, sy - U * 0.05f, U * 0.044f, U * 0.05f, U * 0.008f, rgba(0xFFE6BE, 0.25f))
    val fl = 0.85f + 0.15f * sFlicker(t, 3f)
    ellipse(cx, sy - U * 0.032f, U * 0.004f, U * 0.01f * fl, 0f, rgb(0xffe8b0))
    glow(cx, sy - U * 0.03f, U * 0.1f, 0xffb060, 0.35f * fl)
    glow(cx, sy, U * 0.25f, 0xffa050, 0.12f * fl, sy = 0.3f)

    // ---- Portrait: a cast-iron radiator under the window, and the floor.
    if (!land) {
        val rx = wx + ww * 0.12f; val ry = sill + U * 0.1f; val rw = ww * 0.76f; val rh = U * 0.24f
        val fy = ry + rh + U * 0.05f
        rect(0f, fy, W, H - fy, vgrad(fy, H, 0f to rgb(0x2a2024), 1f to rgb(0x120c0e)))
        for (k in -8..8) line(W / 2 + k * W * 0.07f, fy, W / 2 + k * W * 0.2f, H, rgba(0, 0.35f), 1f)
        rect(0f, fy - U * 0.035f, W, U * 0.035f, rgb(0xd0c8bc))
        rect(0f, fy - 2, W, 2f, rgba(0, 0.3f))
        glow(wx + ww * 0.5f, fy, W * 0.6f, 0x8a90a8, 0.08f, sy = 0.2f)
        rect(rx + 4, ry + 6, rw, rh, rgba(0, 0.35f))
        val fins = (rw / (U * 0.035f)).roundToInt()
        for (f in 0 until fins) {
            val fx = rx + rw * f / fins
            val fw2 = rw / fins * 0.82f
            roundRect(fx, ry, fw2, rh, fw2 * 0.45f, hgrad(fx, fx + fw2, 0f to rgb(0x6a6670), 0.35f to rgb(0x9a96a0), 1f to rgb(0x3a3640)))
        }
        rect(rx - U * 0.01f, ry + rh * 0.08f, rw + U * 0.02f, U * 0.012f, rgb(0x5a5660))
        rect(rx - U * 0.01f, ry + rh * 0.86f, rw + U * 0.02f, U * 0.012f, rgb(0x5a5660))
        circle(rx - U * 0.02f, ry + rh * 0.9f, U * 0.012f, rgb(0xb08040))
        glow(wx + ww * 0.86f, ry, U * 0.3f, 0xffa050, 0.1f, sy = 0.6f)
    }

    val far = max(W, H) * 0.8f
    rect(0f, 0f, W, H, rgrad(W / 2, H * 0.4f, far, 0f to rgba(0, 0f), U * 0.3f / far to rgba(0, 0f), 1f to rgba(0, 0.55f)))
    grain(0.05f)
}

private class Sky(val stops: Array<Pair<Float, Int>>, val clouds: Int, val lit: Int, val dark: Int, val haze: Int, val shade: Float)
private fun skyFor(k: WeatherKind): Sky = when (k) {
    WeatherKind.CLEAR -> Sky(arrayOf(0f to 0x5a7ab0, 0.5f to 0xc8a0b0, 0.8f to 0xf0b890, 1f to 0xffd8a0), 0, 0xb8bcc4, 0x8a8a98, 0x8a7a98, 0.25f)
    WeatherKind.PARTLY_CLOUDY -> Sky(arrayOf(0f to 0x5a74a8, 0.55f to 0xb8a0b8, 0.85f to 0xe8b090, 1f to 0xf8d0a0), 4, 0xf0c0a8, 0x8a7a98, 0x7a7090, 0.3f)
    WeatherKind.CLOUDY -> Sky(arrayOf(0f to 0x6a7488, 1f to 0xa8aab0), 7, 0xb8bcc4, 0x6a7080, 0x7a8090, 0.45f)
    WeatherKind.FOG -> Sky(arrayOf(0f to 0x9aa0a8, 1f to 0xc8c8c8), 0, 0xb8bcc4, 0x6a7080, 0xb0b4b8, 0.35f)
    WeatherKind.DRIZZLE -> Sky(arrayOf(0f to 0x5e6878, 1f to 0x9aa0a8), 6, 0xb8bcc4, 0x6a7080, 0x6e7684, 0.5f)
    WeatherKind.RAIN -> Sky(arrayOf(0f to 0x4a5464, 1f to 0x7a8290), 7, 0xb8bcc4, 0x6a7080, 0x5e6676, 0.55f)
    WeatherKind.SNOW -> Sky(arrayOf(0f to 0x8a94a8, 1f to 0xd0d4dc), 6, 0xb8bcc4, 0x6a7080, 0xa0a8b8, 0.4f)
    WeatherKind.STORM -> Sky(arrayOf(0f to 0x24283a, 1f to 0x4a4e60), 8, 0x3a3e52, 0x1a1c2a, 0x2e3244, 0.7f)
}

private val HousePal = arrayOf(intArrayOf(0xe0c8a8, 0xb8604a), intArrayOf(0xc8a888, 0x8a4a3a), intArrayOf(0xd8d0c0, 0x6a5a5a), intArrayOf(0xe8d8b0, 0xa8584a), intArrayOf(0xb8a898, 0x7a4a48))
private val Shutters = intArrayOf(0x3a5a4a, 0x4a5a7a, 0x7a3a32)
private val Doors = intArrayOf(0x3a4a6a, 0x6a2a2a, 0x2a4a3a)
private val Blooms = intArrayOf(0xc84a5a, 0xe8c84a, 0xd86a8a)

/** The town outside at wake-up, painted for the weather. */
private fun Sketch.windowView(t: Float, k: WeatherKind, X: Float, Y: Float, WW: Float, HH: Float) {
    val sky = skyFor(k)
    val dim = k != WeatherKind.CLEAR && k != WeatherKind.PARTLY_CLOUDY
    rect(X, Y, WW, HH, vgrad(Y, Y + HH * 0.75f, *Array(sky.stops.size) { sky.stops[it].first to rgb(sky.stops[it].second) }))
    val flash = if (k == WeatherKind.STORM) stormFlash(t) else 0f
    if (!dim) {
        val sx = X + WW * 0.7f
        val sy = Y + HH * 0.62f
        glow(sx, sy, WW * 0.8f, 0xffd090, 0.45f, sy = 0.6f)
        circle(sx, sy, U * 0.035f, rgb(0xfff0c8))
        for (i in 0 until 4) {
            val bx = X + ((sHash(i * 3f) * WW + t * U * 0.02f * (0.7f + sHash(i.toFloat()))) % (WW * 1.2f)) - WW * 0.1f
            val by = Y + HH * (0.2f + 0.15f * sHash(i * 7f)) + sin(t + i) * 4
            val f = sin(t * 6 + i * 2) * 3
            wq.reset(); wq.moveTo(bx - 5, by - f); wq.quadTo(bx - 2, by - 2, bx, by); wq.quadTo(bx + 2, by - 2, bx + 5, by - f)
            stroke(wq, rgba(0x3C323C, 0.6f), 1f)
        }
    }
    for (i in 0 until sky.clouds) {
        val cw = WW * (0.45f + 0.35f * sHash(i * 5f))
        val cy = Y + HH * (0.06f + 0.34f * sHash(i * 3f))
        val cx = X + ((sHash(i * 7f) * (WW + cw) + t * U * 0.006f * (0.5f + sHash(i.toFloat()))) % (WW + cw * 1.5f)) - cw * 0.7f
        for (j in 0 until 8) {
            val lx = cx + cw * (j / 7f)
            val ly = cy + sin(j * 1.9f + i) * HH * 0.015f
            val r = cw * (0.16f + 0.07f * sin(j * 2.3f + i))
            glow(lx, ly + r * 0.15f, r * 1.1f, sky.dark, 0.9f, additive = false, sy = 0.42f)
            glow(lx - r * 0.1f, ly - r * 0.12f, r * 0.85f, sky.lit, if (k == WeatherKind.PARTLY_CLOUDY) 0.75f else 0.5f, additive = false, sy = 0.32f)
        }
    }
    if (sky.clouds >= 6) rect(X, Y, WW, HH * 0.35f, vgrad(Y, Y + HH * 0.35f, 0f to rgba(sky.dark, 0.7f), 1f to rgba(sky.dark, 0f)))
    if (flash > 0) rect(X, Y, WW, HH, rgba(0xDCE1FF, 0.5f * flash))
    if (k == WeatherKind.STORM && flash > 0.6f) {
        val bolt = floor(t / 12.7f)
        var lx = X + WW * (0.3f + 0.4f * sHash(bolt))
        var ly = Y + HH * 0.2f
        wq.reset(); wq.moveTo(lx, ly)
        for (s in 0 until 7) { lx += (sHash(s * 3 + bolt) - 0.5f) * U * 0.05f; ly += HH * 0.06f; wq.lineTo(lx, ly) }
        stroke(wq, rgba(0xF0F0FF, 0.9f), 1.5f)
    }
    // Distant hills and a church spire.
    c.save(); c.translate(X, 0f)
    ridge(Y + HH * 0.66f, HH * 0.1f, 6f, 1.5f, rgb(sky.haze), Y + HH + 2, 0f, false, null, WW)
    c.restore()
    val spX = X + WW * 0.3f
    val spY = Y + HH * 0.62f
    val spire = rgb(hexMix(sky.haze, 0, 0.15f))
    rect(spX - U * 0.012f, spY - HH * 0.08f, U * 0.024f, HH * 0.1f, spire)
    wq.reset(); wq.moveTo(spX - U * 0.014f, spY - HH * 0.08f); wq.lineTo(spX, spY - HH * 0.2f); wq.lineTo(spX + U * 0.014f, spY - HH * 0.08f); wq.close(); fill(wq, spire)
    if (k == WeatherKind.FOG) rect(X, Y, WW, HH, rgba(0xD2D4D8, 0.55f))

    // Two rows of houses: the near row in detail, the far row simpler and hazed.
    for (row in 0..1) {
        val far = row == 0
        val base = Y + HH * if (far) 0.8f else 1.02f
        val s = HH * if (far) 0.11f else 0.2f
        var x = X - s * 0.5f
        var i = 0
        while (x < X + WW + s) {
            val seed = i * 7f + if (far) 100 else 0
            val w = s * (1.25f + 0.7f * sHash(seed))
            val h = s * (0.75f + 0.45f * sHash(seed + 1))
            val roofH = s * (0.45f + 0.25f * sHash(seed + 2))
            val sideW = if (far) 0f else s * 0.22f
            val pal = HousePal[(sHash(seed + 3) * 5).toInt().coerceIn(0, 4)]
            fun tone(col: Int, extra: Float = 0f) = rgb(hexMix(hexMix(col, 0x2a2a36, sky.shade + extra), sky.haze, if (far) 0.55f else 0f))
            val wall = tone(pal[0]); val roof = tone(pal[1]); val roofDark = tone(pal[1], 0.2f); val trim = tone(0xf0ece4)
            if (sideW > 0) {
                wq.reset(); wq.moveTo(x + w, base + 2); wq.lineTo(x + w, base - h); wq.lineTo(x + w + sideW, base - h - s * 0.06f); wq.lineTo(x + w + sideW, base + 2); wq.close()
                fill(wq, tone(pal[0], 0.25f))
            }
            rect(x, base - h, w, h + 2, wall)
            val style = sHash(seed + 4)
            if (!far && style > 0.66f) {
                wq.reset(); wq.moveTo(x, base - h * 0.5f); wq.lineTo(x + w, base - h * 0.5f)
                for (b2 in 0..4) { wq.moveTo(x + w * b2 / 4, base - h); wq.lineTo(x + w * b2 / 4, base) }
                wq.moveTo(x, base - h); wq.lineTo(x + w / 4, base - h * 0.5f); wq.moveTo(x + w, base - h); wq.lineTo(x + w * 0.75f, base - h * 0.5f)
                stroke(wq, tone(0x4a3020), max(1f, s * 0.035f))
            } else if (!far && style < 0.33f) {
                var yy = base - h + s * 0.06f
                while (yy < base) { line(x, yy, x + w, yy, rgba(0, 0.12f), 1f); yy += s * 0.06f }
            }
            val cols = if (w > s * 1.6f) 3 else 2
            for (fl in 0..1) for (c2 in 0 until cols) {
                val wW = w * if (cols == 3) 0.16f else 0.22f
                val wH = h * 0.24f
                val wX = x + w * ((c2 + 0.5f) / cols) - wW / 2
                val wY = base - h + h * (0.14f + fl * 0.42f)
                if (!far && fl == 1 && c2 == cols / 2) continue // the door goes here
                val lit = sHash(seed * 3 + fl * 5 + c2) > 0.68f
                rect(wX - 1.5f, wY - 1.5f, wW + 3, wH + 3, trim)
                rect(wX, wY, wW, wH, if (lit) rgb(0xffcf80) else tone(0x2a3040))
                if (lit) glow(wX + wW / 2, wY + wH / 2, wW * 1.6f, 0xffb060, if (far) 0.15f else 0.3f)
                if (!far) {
                    rect(wX + wW / 2 - 0.6f, wY, 1.2f, wH, trim); rect(wX, wY + wH * 0.45f, wW, 1.2f, trim)
                    rect(wX - 2, wY + wH + 1.5f, wW + 4, 1.5f, rgba(0, 0.25f))
                    if (sHash(seed + fl * 11 + c2) > 0.55f) {
                        val sh = tone(Shutters[(sHash(seed + 9) * 3).toInt().coerceIn(0, 2)])
                        rect(wX - wW * 0.45f, wY, wW * 0.4f, wH, sh); rect(wX + wW * 1.05f, wY, wW * 0.4f, wH, sh)
                        for (sl in 1 until 5) { rect(wX - wW * 0.45f, wY + wH * sl / 5, wW * 0.4f, 0.8f, rgba(0, 0.2f)); rect(wX + wW * 1.05f, wY + wH * sl / 5, wW * 0.4f, 0.8f, rgba(0, 0.2f)) }
                    }
                    if (fl == 0 && sHash(seed + c2 * 13) > 0.6f) {
                        rect(wX - 1, wY + wH + 2, wW + 2, wH * 0.18f, tone(0x5a3a2a))
                        for (fk in 0 until 5) circle(wX + wW * (fk + 0.5f) / 5, wY + wH + 1.5f, wW * 0.08f, tone(Blooms[fk % 3]))
                        rect(wX, wY + wH, wW, 1.5f, tone(0x3a6a3a))
                    }
                }
            }
            if (!far) {
                val dw = w * 0.2f; val dh = h * 0.36f; val dx = x + w / 2 - dw / 2; val dy = base - dh
                rect(dx - 2, dy - 2, dw + 4, dh + 2, trim)
                rect(dx, dy, dw, dh, tone(Doors[(sHash(seed + 5) * 3).toInt().coerceIn(0, 2)]))
                rect(dx + dw * 0.15f, dy + dh * 0.1f, dw * 0.3f, dh * 0.35f, rgba(0xFFFFFF, 0.12f)); rect(dx + dw * 0.55f, dy + dh * 0.1f, dw * 0.3f, dh * 0.35f, rgba(0xFFFFFF, 0.12f))
                circle(dx + dw * 0.8f, dy + dh * 0.55f, 1.2f, rgb(0xe0c060))
                if (sHash(seed + 6) > 0.5f) { rect(dx + dw / 2 - 1.5f, dy - s * 0.08f, 3f, 3f, rgb(0xffe0a0)); glow(dx + dw / 2, dy - s * 0.07f, s * 0.25f, 0xffc070, 0.3f) }
            }
            val slope = if (sideW > 0) s * 0.06f else 0f
            wq.reset(); wq.moveTo(x - s * 0.08f, base - h); wq.lineTo(x + w * 0.2f, base - h - roofH); wq.lineTo(x + w * 0.8f + sideW, base - h - roofH - slope); wq.lineTo(x + w + sideW + s * 0.08f, base - h - slope); wq.close()
            fill(wq, roof)
            for (r in 1 until 6) {
                val q = r / 6f; val yy = base - h - roofH * q
                line(sLerp(x - s * 0.08f, x + w * 0.2f, q), yy, sLerp(x + w + sideW + s * 0.08f, x + w * 0.8f + sideW, q), yy - slope, rgba(0, 0.2f), 1f)
            }
            if (!far) {
                rect(x - s * 0.08f, base - h - 2, w + sideW + s * 0.16f, 3f, roofDark)
                if (sHash(seed + 7) > 0.55f) {
                    val dx2 = x + w * 0.35f; val dw2 = w * 0.22f; val dh2 = roofH * 0.45f; val dy2 = base - h - roofH * 0.75f
                    rect(dx2, dy2, dw2, dh2, wall)
                    rect(dx2 + dw2 * 0.2f, dy2 + dh2 * 0.25f, dw2 * 0.6f, dh2 * 0.6f, if (sHash(seed + 8) > 0.5f) rgb(0xffcf80) else tone(0x2a3040))
                    wq.reset(); wq.moveTo(dx2 - dw2 * 0.15f, dy2); wq.lineTo(dx2 + dw2 / 2, dy2 - dh2 * 0.6f); wq.lineTo(dx2 + dw2 * 1.15f, dy2); wq.close(); fill(wq, roofDark)
                }
            }
            if (k == WeatherKind.SNOW) {
                wq.reset(); wq.moveTo(x - s * 0.08f, base - h); wq.lineTo(x + w * 0.2f, base - h - roofH); wq.lineTo(x + w * 0.8f + sideW, base - h - roofH); wq.lineTo(x + w + sideW + s * 0.08f, base - h)
                wq.lineTo(x + w * 0.75f, base - h - roofH * 0.82f); wq.lineTo(x + w * 0.25f, base - h - roofH * 0.82f); wq.close(); fill(wq, rgb(0xeef0f6))
            }
            if (sHash(seed + 10) > 0.4f) {
                val chx = x + w * 0.66f; val chy = base - h - roofH * 0.75f; val cw = s * 0.16f
                rect(chx, chy - s * 0.3f, cw, s * 0.4f, tone(0x8a5a48))
                for (b2 in 1 until 4) rect(chx, chy - s * 0.3f + b2 * s * 0.1f, cw, 0.8f, rgba(0, 0.15f))
                rect(chx - 1, chy - s * 0.32f, cw + 2, s * 0.03f, tone(0x6a4a3a))
                rect(chx + cw * 0.15f, chy - s * 0.38f, cw * 0.25f, s * 0.06f, tone(0xb86a4a)); rect(chx + cw * 0.6f, chy - s * 0.37f, cw * 0.25f, s * 0.05f, tone(0xb86a4a))
                for (j in 0 until 4) {
                    val p = (t * 0.06f + j / 4f + i * 0.13f) % 1f
                    glow(chx + cw / 2 + p * s * if (k == WeatherKind.STORM) 1.6f else 0.8f, chy - s * 0.4f - p * s * 1.2f, s * (0.08f + 0.25f * p), if (dim) 0xc8ccd4 else 0xe8d8d0, 0.16f * sin(p * PI.toFloat()), additive = false)
                }
            }
            x += w + sideW + s * 0.1f; i++
        }
        if (far && k == WeatherKind.FOG) rect(X, Y, WW, HH, rgba(0xD2D4D8, 0.35f))
    }
    // Precipitation falling past the window.
    if (k == WeatherKind.SNOW) {
        for (i in 0 until 120) {
            val dd = sHash(i * 3f)
            val x = X + ((sHash(i * 7f) * WW + sin(t * 0.6f + i) * U * 0.02f) % WW + WW) % WW
            val y = Y + ((sHash(i * 11f) * HH + t * U * (0.02f + 0.05f * dd)) % HH)
            circle(x, y, 0.8f + 1.8f * dd, rgba(0xFAFCFF, 0.5f + 0.5f * dd))
        }
    }
    if (k == WeatherKind.DRIZZLE || k == WeatherKind.RAIN || k == WeatherKind.STORM) {
        val drizzle = k == WeatherKind.DRIZZLE
        val n = if (drizzle) 60 else 160
        val len = if (drizzle) U * 0.015f else U * 0.035f
        val slant = if (k == WeatherKind.STORM) 0.3f else 0.12f
        wq.reset()
        for (i in 0 until n) {
            val sp = U * (0.6f + 0.4f * sHash(i * 5f)) * if (drizzle) 0.4f else 1f
            val x = X + ((sHash(i * 7f) * WW * 1.3f + t * sp * slant) % (WW * 1.3f)) - WW * 0.15f
            val y = Y + ((sHash(i * 11f) * HH + t * sp) % HH)
            wq.moveTo(x, y); wq.lineTo(x - len * slant, y - len)
        }
        stroke(wq, if (k == WeatherKind.STORM) rgba(0xC8D2E6, 0.35f) else rgba(0xD2DCEB, 0.3f), 1f)
    }
    if (k == WeatherKind.FOG) {
        for (i in 0 until 4) {
            val fx = X + ((sHash(i * 3f) * WW + t * U * 0.008f) % (WW * 1.6f)) - WW * 0.3f
            glow(fx, Y + HH * (0.6f + 0.12f * i), WW * 0.6f, 0xe0e2e6, 0.35f, additive = false, sy = 0.25f)
        }
    }
}

/** Raindrops on the glass: still beads, and now and then one gathers and runs down. */
private fun Sketch.windowDrops(t: Float, X: Float, Y: Float, WW: Float, HH: Float, amount: Float) {
    for (i in 0 until (70 * amount).roundToInt()) {
        val x = X + sHash(i * 3.3f) * WW
        val y = Y + sHash(i * 5.7f) * HH
        val r = U * (0.003f + 0.006f * sHash(i * 9.1f))
        ellipse(x, y + r * 0.2f, r, r * 1.1f, 0f, rgba(0x141822, 0.35f))
        ellipse(x - r * 0.25f, y - r * 0.3f, r * 0.35f, r * 0.3f, 0f, rgba(0xE6ECF5, 0.45f))
    }
    for (i in 0 until (8 * amount).roundToInt()) {
        val period = 5 + sHash(i * 7f) * 6
        val p = ((t + sHash(i * 3f) * period) % period) / period
        val x = X + sHash(i * 13.1f) * WW
        val y0 = Y + sHash(i * 17.3f) * HH * 0.4f
        val y = y0 + (HH - (y0 - Y)) * p * p
        val r = U * 0.007f
        val xx = x + sin(p * 9 + i) * 2
        line(x, y0, xx, y, rgba(0xDCE4F0, 0.18f), r * 0.8f)
        ellipse(xx, y, r, r * 1.3f, 0f, rgba(0x141822, 0.4f))
        circle(xx - r * 0.3f, y - r * 0.4f, r * 0.35f, rgba(0xEBF0FA, 0.5f))
    }
}
