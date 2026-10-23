package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.Astronomy
import dev.pridgey.beddery.savers.MoonRender
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatDuration
import dev.pridgey.beddery.savers.formatLongDate
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.hour12
import dev.pridgey.beddery.savers.smoothstep
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos

/** The sun's height over a day, shaped from today's real sunrise and sunset. */
private class SunCurve(val noon: Double, private val c: Double) {
    /** Height at [hours] since midnight: above 0 is daylight. */
    fun altitude(hours: Double): Double = cos(2 * PI * (hours - noon) / 24.0) - c
    val peak get() = 1 - c
    val trough get() = 1 + c
}

private fun hoursOf(t: LocalDateTime, day: java.time.LocalDate): Double =
    Duration.between(day.atStartOfDay(), t).seconds / 3600.0

private val NightTop = Color(0xFF02040C)
private val NightLow = Color(0xFF0A1530)
private val DuskTop = Color(0xFF0E1236)
private val DuskLow = Color(0xFFB8563E)
private val DayTop = Color(0xFF1C3F78)
private val DayLow = Color(0xFF6E9CCB)
private val Sun = Color(0xFFFFD27A)

@Composable
fun DayArcSaver(ctx: SaverContext) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val loc = ctx.settings.weatherLocation
    val (lat, lon) = remember(loc) { loc?.let { it.latitude to it.longitude } ?: Astronomy.guessLocation(zone) }
    val today = ctx.now.toLocalDate()
    val days = remember(today, lat, lon) { (-1..1).map { Astronomy.sunTimes(today.plusDays(it.toLong()), lat, lon, zone) } }
    val sun = days[1]
    val curve = remember(sun) {
        val rise = sun.sunrise
        val set = sun.sunset
        if (rise != null && set != null) {
            val dayLength = Duration.between(rise, set).seconds / 3600.0
            SunCurve((hoursOf(rise, today) + hoursOf(set, today)) / 2, cos(PI * dayLength / 24))
        } else SunCurve(12.0, if (sun.polarDay) -1.05 else 1.05)
    }
    val phase = remember(today) { Astronomy.moonPhase(Instant.now()) }
    val moon = remember(today) { MoonRender.render(160, phase, lat < 0).asImageBitmap() }
    val stars = remember { StarSky(160, 73, 0f, 0.6f) }
    val measurer = rememberTextMeasurer()
    val nowH = hoursOf(ctx.now, today)
    val altNow = curve.altitude(nowH)
    // -1 deep night … 0 horizon … 1 midday.
    val sky = (if (altNow >= 0) altNow / curve.peak else altNow / curve.trough).toFloat().coerceIn(-1f, 1f)

    // Next sunrise or sunset, from the three days around today.
    val events = days.flatMap { listOfNotNull(it.sunrise?.let { t -> t to true }, it.sunset?.let { t -> t to false }) }.sortedBy { it.first }
    val next = events.firstOrNull { it.first.isAfter(ctx.now) }
    val nextLabel = next?.let { (t, rise) ->
        val left = formatDuration(context, Duration.between(ctx.now, t).toMillis())
        stringResource(if (rise) R.string.dayarc_sunrise_in else R.string.dayarc_sunset_in, left) + "  ·  " + formatTimeWithAmPm(t.toLocalTime(), ctx.use24h)
    }
    val daylight = if (sun.sunrise != null && sun.sunset != null) {
        stringResource(R.string.dayarc_daylight, formatDuration(context, Duration.between(sun.sunrise, sun.sunset).toMillis()))
    } else null

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val dens = LocalDensity.current
        val timeSize = with(dens) { (if (landscape) maxHeight * 0.17f else maxWidth * 0.2f).toSp() }
        val small = with(dens) { (minOf(maxWidth, maxHeight) * 0.036f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val day = smoothstep(-0.05f, 0.35f, sky)
            val dusk = (1f - kotlin.math.abs(sky) / 0.3f).coerceIn(0f, 1f)
            val top = lerp(lerp(NightTop, DayTop, day), DuskTop, dusk * 0.5f)
            val low = lerp(lerp(NightLow, DayLow, day), DuskLow, dusk * 0.7f)
            val horizon = h * if (landscape) 0.66f else 0.6f
            drawRect(Brush.verticalGradient(listOf(top, low), 0f, horizon), size = Size(w, horizon))
            drawRect(Brush.verticalGradient(listOf(lerp(low, Color.Black, 0.75f), Color.Black), horizon, h), Offset(0f, horizon), Size(w, h - horizon))
            if (day < 1f) stars.draw(this, ctx.now.second * 0.2f, (1f - day) * 0.8f, horizonFade = 0.5f)
            if (sky < 0.05f) {
                val mr = size.minDimension * 0.045f
                val mo = Offset(w * 0.84f, h * if (landscape) 0.14f else 0.1f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFDDE4F5).copy(alpha = 0.12f), Color.Transparent), mo, mr * 4f), mr * 4f, mo)
                drawImage(moon, IntOffset.Zero, IntSize(moon.width, moon.height), IntOffset((mo.x - mr).toInt(), (mo.y - mr).toInt()), IntSize((mr * 2).toInt(), (mr * 2).toInt()), filterQuality = FilterQuality.High)
            }

            // The curve: 24 hours centered on now, so "now" sits still and the day scrolls past.
            val cx = w / 2
            val half = w * 0.48f
            val up = h * if (landscape) 0.24f else 0.15f
            val down = h * 0.1f
            fun xOf(hours: Double) = (cx + (hours - nowH) / 12.0 * half).toFloat()
            fun yOf(hours: Double): Float {
                val a = curve.altitude(hours)
                return if (a >= 0) horizon - (a / curve.peak.coerceAtLeast(0.3)).toFloat() * up
                else horizon + (-a / curve.trough).toFloat() * down
            }
            val path = Path()
            val fill = Path()
            var first = true
            var hh = nowH - 12.5
            while (hh <= nowH + 12.5) {
                val x = xOf(hh)
                val y = yOf(hh)
                if (first) {
                    path.moveTo(x, y)
                    fill.moveTo(x, horizon)
                    fill.lineTo(x, minOf(y, horizon))
                    first = false
                } else {
                    path.lineTo(x, y)
                    fill.lineTo(x, minOf(y, horizon))
                }
                hh += 0.1
            }
            fill.lineTo(xOf(nowH + 12.5), horizon)
            fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(Sun.copy(alpha = 0.22f), Sun.copy(alpha = 0.02f)), horizon - up, horizon))
            clipRect(bottom = horizon) {
                drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFFE6A8), Sun), horizon - up, horizon), style = Stroke(3f * density, cap = StrokeCap.Round))
            }
            clipRect(top = horizon) {
                drawPath(path, Color(0xFF8FA6D9).copy(alpha = 0.35f), style = Stroke(1.6f * density, cap = StrokeCap.Round))
            }
            drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, horizon), Offset(w, horizon), strokeWidth = density)

            // Hour marks along the horizon.
            val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.4f), fontSize = small * 0.85f, fontFeatureSettings = "tnum")
            var mark = kotlin.math.ceil(nowH - 12).toInt()
            while (mark <= nowH + 12) {
                val x = xOf(mark.toDouble())
                val hourOfDay = ((mark % 24) + 24) % 24
                val major = hourOfDay % 6 == 0
                drawLine(Color.White.copy(alpha = if (major) 0.35f else 0.14f), Offset(x, horizon), Offset(x, horizon + if (major) 8.dp.toPx() else 4.dp.toPx()), strokeWidth = density)
                if (major) {
                    val text = if (ctx.use24h) "%02d:00".format(hourOfDay) else "${hour12(hourOfDay)} ${amPm(java.time.LocalTime.of(hourOfDay, 0))}"
                    val l = measurer.measure(text, labelStyle)
                    drawText(l, topLeft = Offset(x - l.size.width / 2f, horizon + 12.dp.toPx()))
                }
                mark++
            }

            // Sunrise and sunset where the curve meets the horizon.
            events.forEach { (t, rise) ->
                val hrs = hoursOf(t, today)
                if (hrs < nowH - 12 || hrs > nowH + 12) return@forEach
                val x = xOf(hrs)
                drawCircle(Sun.copy(alpha = 0.9f), 3.5f * density, Offset(x, horizon))
                val l = measurer.measure(
                    (if (rise) "↑ " else "↓ ") + formatTimeWithAmPm(t.toLocalTime(), ctx.use24h),
                    TextStyle(color = Sun.copy(alpha = 0.85f), fontSize = small, fontWeight = FontWeight.Medium),
                )
                // Sunrise labels sit to the left of the crossing, sunsets to the right, clear of the curve.
                val lx = if (rise) x - l.size.width - 8.dp.toPx() else x + 8.dp.toPx()
                drawText(l, topLeft = Offset(lx.coerceIn(8f, w - l.size.width - 8f), horizon - l.size.height - 4.dp.toPx()))
            }

            // Now.
            drawLine(
                Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.3f), Color.Transparent), horizon - up * 1.2f, horizon + down * 1.4f),
                Offset(cx, horizon - up * 1.2f), Offset(cx, horizon + down * 1.4f), strokeWidth = density,
            )
            val body = Offset(cx, yOf(nowH))
            val br = size.minDimension * 0.026f
            if (altNow >= 0) {
                drawCircle(Brush.radialGradient(listOf(Sun.copy(alpha = 0.45f), Color.Transparent), body, br * 6f), br * 6f, body, blendMode = BlendMode.Plus)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFCEB), Sun), body, br), br, body)
            } else {
                drawCircle(Color(0xFF8FA6D9).copy(alpha = 0.6f), br * 0.75f, body, style = Stroke(1.6f * density))
            }
        }
        Column(
            Modifier.padding(start = 28.dp, top = if (landscape) 20.dp else 64.dp, end = 28.dp),
        ) {
            val t = ctx.now.toLocalTime()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatClock(t, ctx.use24h), color = Color.White, fontSize = timeSize, lineHeight = timeSize, fontWeight = FontWeight.Thin, style = TabularNumbers)
                if (!ctx.use24h) Text(amPm(t), color = Color.White.copy(alpha = 0.7f), fontSize = timeSize * 0.2f, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
            }
            Text(formatLongDate(today), color = Color.White.copy(alpha = 0.65f), fontSize = small * 1.2f)
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 28.dp, bottom = if (landscape) 18.dp else 56.dp, end = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            nextLabel?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = small * 1.15f, letterSpacing = 0.01.em) }
            daylight?.let { Text(it, color = Color.White.copy(alpha = 0.45f), fontSize = small) }
            Spacer(Modifier.height(0.dp))
        }
    }
}
