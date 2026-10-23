package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.HourForecast
import dev.pridgey.beddery.data.WeatherData
import dev.pridgey.beddery.data.WeatherKind
import dev.pridgey.beddery.data.WeatherRepository
import dev.pridgey.beddery.data.weatherKind
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.SoftDot
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.rememberNoiseBrush
import dev.pridgey.beddery.savers.ridge
import dev.pridgey.beddery.savers.smoothstep
import dev.pridgey.beddery.savers.stamp
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.sin

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

private class WindowLayout(size: Size) {
    val landscape = size.width > size.height
    val glass: Rect = if (landscape) {
        Rect(size.width * 0.5f, size.height * 0.12f, size.width * 0.9f, size.height * 0.8f)
    } else {
        Rect(size.width * 0.13f, size.height * 0.1f, size.width * 0.87f, size.height * 0.52f)
    }
    val frame = glass.inflate(size.minDimension * 0.028f)
    val roofs: Path
    val trees: Path

    init {
        val g = glass
        val base = g.bottom - g.height * 0.18f
        roofs = Path().apply {
            var x = g.left - 10f
            var k = 0
            while (x < g.right) {
                val bw = g.width * (0.12f + 0.1f * hash(k * 7 + 3))
                val bh = g.height * (0.08f + 0.1f * hash(k * 13 + 1))
                val top = base - bh
                moveTo(x, g.bottom)
                lineTo(x, top)
                if (hash(k * 5) > 0.45f) {
                    lineTo(x + bw / 2, top - bw * 0.35f)
                } else {
                    lineTo(x + bw * 0.7f, top)
                    lineTo(x + bw * 0.7f, top - bh * 0.25f)
                    lineTo(x + bw * 0.8f, top - bh * 0.25f)
                    lineTo(x + bw * 0.8f, top)
                }
                lineTo(x + bw, top)
                lineTo(x + bw, g.bottom)
                close()
                x += bw * (0.92f + 0.2f * hash(k * 11))
                k++
            }
        }
        val hgt = ridge(48, 81, 0.6f)
        trees = Path().apply {
            moveTo(g.left, g.bottom)
            hgt.forEachIndexed { i, v -> lineTo(g.left + g.width * i / (hgt.size - 1f), base - g.height * (0.1f + 0.14f * v)) }
            lineTo(g.right, g.bottom)
            close()
        }
    }
}

/** The view out of a bedroom window, as the forecast says it'll be when you get up. */
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
    val grain = rememberGrainBrush()
    val clouds = rememberNoiseBrush(256, 4, 5, seed = 64, contrast = 1.6f)
    val layout = remember { SizeCache { WindowLayout(it) } }
    val wake = remember(ctx.now.hour, ctx.now.minute / 10, ctx.nextAlarm) { wakeTime(ctx.now, ctx.nextAlarm) }
    val d = data
    val view: WakeView? = when {
        d != null -> remember(d, wake) { viewFor(d, wake) }
        ctx.demo -> sampleView(ctx.now, wake, if (ctx.preview) 0 else (ctx.now.minute / 2))
        else -> null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val dens = LocalDensity.current
        val u = min(maxWidth, maxHeight)
        val clock = with(dens) { (u * if (landscape) 0.22f else 0.2f).toSp() }
        val body = with(dens) { (u * 0.042f).toSp() }
        val big = with(dens) { (u * 0.075f).toSp() }

        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val l = layout.get(size)
            drawRoom(l, view, t)
            clipRect(l.glass.left, l.glass.top, l.glass.right, l.glass.bottom) {
                drawOutside(l, view, t, clouds)
            }
            drawFrame(l, view?.kind, t)
            drawGrain(grain, 0.06f)
        }

        val col: @Composable () -> Unit = {
            val tNow = ctx.now.toLocalTime()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatClock(tNow, ctx.use24h), color = Color.White.copy(alpha = 0.88f), fontSize = clock, lineHeight = clock, fontWeight = FontWeight.Light, style = TabularNumbers)
                if (!ctx.use24h) Text(amPm(tNow), color = Color.White.copy(alpha = 0.5f), fontSize = body, modifier = Modifier.offset(x = 6.dp, y = (-14).dp))
            }
            val v = view
            when {
                v != null -> {
                    Text(
                        stringResource(R.string.window_when, formatTimeWithAmPm(v.at.toLocalTime(), ctx.use24h)).uppercase(),
                        color = Color.White.copy(alpha = 0.45f), fontSize = body * 0.75f, letterSpacing = body * 0.12f,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "${v.temp}° · ${stringResource(weatherLabel(v.kind))}", color = Color.White.copy(alpha = 0.85f),
                        fontSize = big, fontWeight = FontWeight.Light, style = TabularNumbers,
                    )
                    if (v.high != null && v.low != null) {
                        Text(stringResource(R.string.window_range, v.high, v.low), color = Color.White.copy(alpha = 0.5f), fontSize = body, style = TabularNumbers)
                    }
                    if (v.sample) Text(stringResource(R.string.window_sample), color = Color.White.copy(alpha = 0.3f), fontSize = body * 0.75f, modifier = Modifier.padding(top = 4.dp))
                }
                location == null -> Text(stringResource(R.string.weather_no_location), color = Color.White.copy(alpha = 0.55f), fontSize = body)
                failed -> Text(stringResource(R.string.weather_error), color = Color.White.copy(alpha = 0.55f), fontSize = body)
                else -> Text(stringResource(R.string.weather_loading), color = Color.White.copy(alpha = 0.55f), fontSize = body)
            }
        }
        if (landscape) {
            Column(
                Modifier.align(Alignment.CenterStart).padding(start = maxWidth * 0.07f).width(maxWidth * 0.38f),
                verticalArrangement = Arrangement.Center,
            ) { col() }
        } else {
            Column(
                Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.6f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { col() }
        }
    }
}

private val Wall = Color(0xFF0C0D11)
private val FrameColor = Color(0xFF1C1D22)

private fun DrawScope.drawRoom(l: WindowLayout, view: WakeView?, t: Float) {
    drawRect(Wall)
    // A warm bedside lamp just out of view, low on the left.
    val lamp = Offset(-size.width * 0.05f, size.height * 0.95f)
    val r = size.maxDimension * 0.9f
    drawCircle(Brush.radialGradient(listOf(Color(0xFF3A2414).copy(alpha = 0.55f), Color(0xFF1A110A).copy(alpha = 0.25f), Color.Transparent), lamp, r), r, lamp)
    // Cool light from outside spilling onto the wall below the window.
    val spill = lerp(Color(0xFF6A88B0), Color(0xFFB0B8C8), if (view?.kind == WeatherKind.SNOW) 1f else 0f)
    val below = Rect(l.frame.left - l.frame.width * 0.15f, l.frame.bottom, l.frame.right + l.frame.width * 0.15f, l.frame.bottom + l.frame.height * 0.6f)
    drawRect(
        Brush.verticalGradient(listOf(spill.copy(alpha = 0.05f), Color.Transparent), below.top, below.bottom),
        below.topLeft, below.size,
    )
}

private fun skyColors(kind: WeatherKind?): Pair<Color, Color> = when (kind) {
    WeatherKind.CLEAR -> Color(0xFF13233F) to Color(0xFF7A8AA8)
    WeatherKind.PARTLY_CLOUDY -> Color(0xFF1A2640) to Color(0xFF6E7A92)
    WeatherKind.CLOUDY -> Color(0xFF22272F) to Color(0xFF555C66)
    WeatherKind.FOG -> Color(0xFF3A3F46) to Color(0xFF7C838C)
    WeatherKind.DRIZZLE, WeatherKind.RAIN -> Color(0xFF181D25) to Color(0xFF3E4652)
    WeatherKind.SNOW -> Color(0xFF2A3038) to Color(0xFF8A929C)
    WeatherKind.STORM -> Color(0xFF0E1016) to Color(0xFF2C303A)
    null -> Color(0xFF0A0E18) to Color(0xFF1A2232)
}

private fun DrawScope.drawOutside(l: WindowLayout, view: WakeView?, t: Float, clouds: dev.pridgey.beddery.savers.TextureBrush) {
    val g = l.glass
    val kind = view?.kind
    val (top, bottom) = skyColors(kind)
    // Lightning: a double flicker every so often during storms.
    val flash = if (kind == WeatherKind.STORM) {
        val cycle = t % 11f
        (if (cycle in 0f..0.12f) 1f else 0f) + (if (cycle in 0.22f..0.3f) 0.6f else 0f)
    } else 0f
    drawRect(Brush.verticalGradient(listOf(lerp(top, Color(0xFFB8C0E0), flash * 0.5f), lerp(bottom, Color(0xFFD8DCF0), flash * 0.4f)), g.top, g.bottom), g.topLeft, g.size)

    // Clear mornings: a low glow and the last stars.
    if (kind == WeatherKind.CLEAR || kind == WeatherKind.PARTLY_CLOUDY) {
        val c = Offset(g.left + g.width * 0.7f, g.bottom - g.height * 0.15f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB27A).copy(alpha = 0.35f), Color.Transparent), c, g.width * 0.7f), g.width * 0.7f, c, blendMode = BlendMode.Plus)
        for (i in 0 until 22) {
            val p = Offset(g.left + hash(i * 3) * g.width, g.top + hash(i * 7 + 1) * g.height * 0.5f)
            val a = (0.25f + 0.5f * hash(i * 11)) * (0.7f + 0.3f * sin(t * (1f + hash(i)) + i))
            drawCircle(Color.White.copy(alpha = a * (1f - (p.y - g.top) / (g.height * 0.5f))), 1.1f * density * (0.6f + hash(i * 5)), p)
        }
    }
    // Clouds drifting by.
    val cover = when (kind) {
        WeatherKind.PARTLY_CLOUDY -> 0.45f
        WeatherKind.CLOUDY, WeatherKind.DRIZZLE, WeatherKind.RAIN, WeatherKind.SNOW -> 0.75f
        WeatherKind.STORM -> 0.9f
        WeatherKind.FOG -> 0.5f
        else -> 0f
    }
    if (cover > 0f) {
        val tint = when (kind) {
            WeatherKind.PARTLY_CLOUDY -> Color(0xFF8C96AC)
            WeatherKind.STORM -> lerp(Color(0xFF2A2E38), Color(0xFFC8CCE0), flash)
            WeatherKind.SNOW, WeatherKind.FOG -> Color(0xFFA8AEB6)
            else -> Color(0xFF5A626E)
        }
        for (layer in 0 until 2) {
            drawRect(
                clouds.place(g.width * (1.6f - layer * 0.4f), g.height * (0.7f - layer * 0.2f), dx = g.left + t * (4f + layer * 3f), dy = g.top + layer * 40f),
                g.topLeft, Size(g.width, g.height * 0.7f),
                alpha = cover * (0.7f - layer * 0.25f), colorFilter = ColorFilter.tint(tint, BlendMode.Modulate),
            )
        }
    }
    // Rooftops and trees.
    val sil = when (kind) {
        WeatherKind.FOG -> Color(0xFF4A5058)
        WeatherKind.SNOW -> Color(0xFF1E232A)
        null -> Color(0xFF05070B)
        else -> lerp(Color(0xFF06080C), bottom, 0.15f)
    }
    drawPath(l.trees, lerp(sil, bottom, 0.25f))
    drawPath(l.roofs, sil)
    if (kind == WeatherKind.SNOW) {
        // Snow lying on the ground and roofs.
        drawRect(Color(0xFFB8C0CA).copy(alpha = 0.85f), Offset(g.left, g.bottom - g.height * 0.05f), Size(g.width, g.height * 0.05f))
    }
    // A couple of lit windows across the street, warm against the cold.
    for (k in 0 until 4) {
        val x = g.left + g.width * (0.12f + k * 0.23f + 0.05f * hash(k * 9))
        val y = g.bottom - g.height * (0.08f + 0.05f * hash(k * 4))
        val on = hash(k * 31) > 0.3f
        if (on) drawRect(Color(0xFFFFC27A).copy(alpha = 0.55f), Offset(x, y), Size(g.width * 0.02f, g.width * 0.026f))
    }

    // Fog: flat banks of haze.
    if (kind == WeatherKind.FOG) {
        for (b in 0 until 3) {
            val y = g.top + g.height * (0.45f + b * 0.17f)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF9AA0A8).copy(alpha = 0.5f), Color.Transparent), y - g.height * 0.12f, y + g.height * 0.12f), Offset(g.left, y - g.height * 0.12f), Size(g.width, g.height * 0.24f))
        }
    }
    // Rain and snow falling outside.
    when (kind) {
        WeatherKind.RAIN, WeatherKind.DRIZZLE, WeatherKind.STORM -> {
            val n = if (kind == WeatherKind.DRIZZLE) 60 else 140
            val len = g.height * if (kind == WeatherKind.DRIZZLE) 0.03f else 0.06f
            for (i in 0 until n) {
                val speed = 0.9f + hash(i * 3) * 0.5f
                val y = g.top + ((hash(i * 7) + t * speed) % 1f) * (g.height + len) - len
                val x = g.left + hash(i * 13) * g.width + (y - g.top) * 0.08f
                drawLine(Color(0xFFB0BCCC).copy(alpha = 0.12f + 0.16f * hash(i)), Offset(x, y), Offset(x + len * 0.08f, y + len), strokeWidth = density * 0.9f)
            }
        }
        WeatherKind.SNOW -> {
            for (i in 0 until 90) {
                val depth = 0.4f + hash(i * 5) * 0.6f
                val y = g.top + ((hash(i * 7) + t * 0.05f * depth) % 1f) * g.height
                val x = g.left + ((hash(i * 13) + noise1(t * 0.2f + i, i) * 0.03f) % 1f) * g.width
                stamp(SoftDot, Offset(x, y), 6f * density * depth, alpha = 0.55f * depth)
            }
        }
        else -> {}
    }
}

private fun DrawScope.drawFrame(l: WindowLayout, kind: WeatherKind?, t: Float) {
    val g = l.glass
    val f = l.frame
    val bar = size.minDimension * 0.014f

    // Raindrops on the glass itself, some sliding down.
    if (kind == WeatherKind.RAIN || kind == WeatherKind.DRIZZLE || kind == WeatherKind.STORM) {
        clipRect(g.left, g.top, g.right, g.bottom) {
            for (i in 0 until 70) {
                val r = size.minDimension * (0.003f + 0.006f * hash(i * 17))
                val slides = hash(i * 29) > 0.75f
                val y0 = g.top + hash(i * 19) * g.height
                val y = if (slides) g.top + ((hash(i * 19) + t * 0.03f * (0.5f + hash(i))) % 1f) * g.height else y0
                val p = Offset(g.left + hash(i * 23) * g.width, y)
                drawCircle(Color.Black.copy(alpha = 0.25f), r, p + Offset(0f, r * 0.3f))
                drawCircle(Color(0xFF9AA6B8).copy(alpha = 0.22f), r, p)
                drawCircle(Color.White.copy(alpha = 0.35f), r * 0.32f, p - Offset(r * 0.3f, r * 0.35f))
            }
        }
    }
    // Condensation along the bottom of the glass on cold mornings.
    if (kind == WeatherKind.SNOW || kind == WeatherKind.FOG) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFCAD2DC).copy(alpha = 0.14f)), g.bottom - g.height * 0.25f, g.bottom), Offset(g.left, g.bottom - g.height * 0.25f), Size(g.width, g.height * 0.25f))
    }

    // The frame: painted wood with a lit inner edge.
    val frame = Path().apply {
        addRect(f)
        addRect(g)
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    }
    drawPath(frame, FrameColor)
    drawRect(Color.White.copy(alpha = 0.05f), g.topLeft - Offset(bar * 0.25f, bar * 0.25f), Size(g.width + bar * 0.5f, bar * 0.25f))
    // Mullions: one across, one down.
    val cx = g.center.x
    val cy = g.top + g.height * 0.42f
    drawRect(FrameColor, Offset(cx - bar / 2, g.top), Size(bar, g.height))
    drawRect(FrameColor, Offset(g.left, cy - bar / 2), Size(g.width, bar))
    drawLine(Color.White.copy(alpha = 0.05f), Offset(cx + bar / 2, g.top), Offset(cx + bar / 2, g.bottom), strokeWidth = density)
    drawLine(Color.White.copy(alpha = 0.05f), Offset(g.left, cy + bar / 2), Offset(g.right, cy + bar / 2), strokeWidth = density)
    // A faint reflection across the glass.
    drawLine(
        Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.04f), Color.Transparent), g.topLeft, g.bottomRight),
        Offset(g.left + g.width * 0.1f, g.bottom), Offset(g.left + g.width * 0.55f, g.top), strokeWidth = g.width * 0.08f, cap = StrokeCap.Butt,
    )

    // Sill, catching the light from outside.
    val sill = Rect(f.left - bar * 1.2f, f.bottom - bar * 0.2f, f.right + bar * 1.2f, f.bottom + bar * 1.3f)
    drawRoundRect(Color(0xFF22232A), sill.topLeft, sill.size, CornerRadius(bar * 0.3f))
    drawRect(Color.White.copy(alpha = 0.07f), sill.topLeft, Size(sill.width, bar * 0.25f))
    drawRect(Color.Black.copy(alpha = 0.4f), Offset(sill.left, sill.bottom), Size(sill.width, bar * 0.8f))

    // A small potted plant on the sill.
    val pot = Offset(f.left + f.width * 0.16f, sill.top)
    val ph = size.minDimension * 0.05f
    val potPath = Path().apply {
        moveTo(pot.x - ph * 0.42f, pot.y - ph * 0.7f)
        lineTo(pot.x + ph * 0.42f, pot.y - ph * 0.7f)
        lineTo(pot.x + ph * 0.32f, pot.y)
        lineTo(pot.x - ph * 0.32f, pot.y)
        close()
    }
    for (k in 0 until 7) {
        val a = -70f + k * 23f + 4f * sin(t * 0.4f + k)
        val rad = Math.toRadians(a.toDouble())
        val len = ph * (0.9f + 0.5f * hash(k * 3))
        val base = Offset(pot.x, pot.y - ph * 0.7f)
        val tip = base + Offset((kotlin.math.sin(rad) * len).toFloat(), (-kotlin.math.cos(rad) * len).toFloat())
        val leaf = Path().apply {
            moveTo(base.x, base.y)
            quadraticTo((base.x + tip.x) / 2 + len * 0.18f, (base.y + tip.y) / 2, tip.x, tip.y)
            quadraticTo((base.x + tip.x) / 2 - len * 0.18f, (base.y + tip.y) / 2, base.x, base.y)
        }
        drawPath(leaf, Color(0xFF0A120C))
    }
    drawPath(potPath, Color(0xFF16100C))
    drawRect(Color.White.copy(alpha = 0.04f), Offset(pot.x - ph * 0.42f, pot.y - ph * 0.7f), Size(ph * 0.84f, ph * 0.08f))

    // Curtains drawn back on each side, gathered in soft folds.
    val ch = f.height + bar * 4
    for (side in listOf(-1f, 1f)) {
        val edge = if (side < 0) f.left else f.right
        val cw = f.width * 0.17f
        val x0 = edge - cw * 0.55f
        val curtain = Path().apply {
            moveTo(x0, f.top - bar * 2)
            lineTo(x0 + cw, f.top - bar * 2)
            cubicTo(x0 + cw * 0.9f, f.top + ch * 0.4f, x0 + cw * 0.55f, f.top + ch * 0.6f, x0 + cw * 0.75f, f.top + ch)
            lineTo(x0 - cw * 0.05f, f.top + ch)
            close()
        }
        val folds = Brush.horizontalGradient(
            0f to Color(0xFF15141A), 0.2f to Color(0xFF221F28), 0.4f to Color(0xFF131218), 0.6f to Color(0xFF24212A), 0.8f to Color(0xFF141319), 1f to Color(0xFF1C1A22),
            startX = x0, endX = x0 + cw,
        )
        drawPath(curtain, folds)
        // Inner edge picks up a little window light.
        drawLine(Color(0xFF8090A8).copy(alpha = 0.06f), Offset(x0 + cw * (if (side < 0) 0.95f else 0.05f), f.top), Offset(x0 + cw * (if (side < 0) 0.7f else 0.1f), f.top + ch), strokeWidth = density * 2)
    }
    // Curtain rail.
    drawRoundRect(Color(0xFF2A2A30), Offset(f.left - f.width * 0.12f, f.top - bar * 2.6f), Size(f.width * 1.24f, bar * 0.7f), CornerRadius(bar))
}
