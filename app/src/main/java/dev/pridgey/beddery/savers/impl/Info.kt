package dev.pridgey.beddery.savers.impl

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.AgendaEvent
import dev.pridgey.beddery.data.CalendarRepository
import dev.pridgey.beddery.data.HourForecast
import dev.pridgey.beddery.data.PhotoLoader
import dev.pridgey.beddery.data.WeatherData
import dev.pridgey.beddery.data.WeatherKind
import dev.pridgey.beddery.data.WeatherRepository
import dev.pridgey.beddery.data.weatherKind
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.StatusLine
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.chargingLabel
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatLongDate
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.rememberFrameTime
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Big time + date block shared by the info savers. */
@Composable
private fun ClockBlock(ctx: SaverContext, timeSize: TextUnit, align: Alignment.Horizontal = Alignment.CenterHorizontally) {
    val t = ctx.now.toLocalTime()
    Column(horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatClock(t, ctx.use24h), color = Color.White, fontSize = timeSize, lineHeight = timeSize, fontWeight = FontWeight.Thin, style = TabularNumbers)
            if (!ctx.use24h) Text(amPm(t), color = ctx.accent, fontSize = timeSize * 0.2f, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        Text(formatLongDate(ctx.now.toLocalDate()), color = ctx.accent, fontSize = timeSize * 0.16f)
    }
}

@Composable
private fun Message(text: String, fontSize: TextUnit = 16.sp) {
    Text(
        text, color = Color.White.copy(alpha = 0.6f), fontSize = fontSize, textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 24.dp),
    )
}

// ---------------------------------------------------------------- Charging

@Composable
fun ChargingSaver(ctx: SaverContext) {
    val context = LocalContext.current
    val color = when {
        ctx.battery.level <= 20 -> Color(0xFFFF7B7B)
        ctx.battery.level >= 80 -> Color(0xFF7FE3A0)
        else -> ctx.accent
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val density = LocalDensity.current
        val ring = if (landscape) min(maxHeight * 0.8f, maxWidth * 0.45f) else min(maxWidth * 0.75f, maxHeight * 0.5f)
        val unit = min(maxWidth, maxHeight)
        val timeSize = with(density) { (unit * if (landscape) 0.2f else 0.16f).toSp() }
        val labelSize = with(density) { (unit * 0.045f).toSp() }
        val label = remember(ctx.battery) { chargingLabel(context, ctx) }
        val info: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatClock(ctx.now.toLocalTime(), ctx.use24h), color = Color.White.copy(alpha = 0.9f), fontSize = timeSize, lineHeight = timeSize, fontWeight = FontWeight.Thin, style = TabularNumbers)
                Text(formatLongDate(ctx.now.toLocalDate()), color = Color.White.copy(alpha = 0.55f), fontSize = labelSize * 0.9f)
            }
        }
        if (landscape) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 0.12f)) {
                ChargingRing(ctx, color, ring)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    info()
                    Spacer(Modifier.height(16.dp))
                    Text(label, color = color, fontSize = labelSize)
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                info()
                Spacer(Modifier.height(unit * 0.12f))
                ChargingRing(ctx, color, ring)
                Spacer(Modifier.height(unit * 0.1f))
                Text(label, color = color, fontSize = labelSize)
            }
        }
    }
}

@Composable
private fun ChargingRing(ctx: SaverContext, color: Color, diameter: Dp) {
    val level by animateFloatAsState(ctx.battery.level / 100f, tween(1500), label = "level")
    val charging = ctx.battery.isCharging && !ctx.battery.isFull
    val bubbles = remember { List(14) { floatArrayOf(Random.nextFloat(), Random.nextFloat(), 0.4f + Random.nextFloat()) } }
    val frame = rememberFrameTime()
    val textSize = with(LocalDensity.current) { (diameter * 0.2f).toSp() }
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = frame.value
            val c = center
            val radius = size.minDimension / 2 / 1.15f
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent), c, radius * 1.5f), radius * 1.5f, c)
            drawCircle(Color.White.copy(alpha = 0.06f), radius, c)
            val circle = Path().apply { addOval(Rect(c, radius * 0.94f)) }
            clipPath(circle) {
                val levelY = c.y + radius - 2 * radius * level
                for (layer in 0..1) {
                    val amp = radius * (if (charging) 0.045f else 0.02f) * (1 - layer * 0.4f)
                    val wave = Path().apply {
                        moveTo(c.x - radius, c.y + radius)
                        var x = c.x - radius
                        while (x <= c.x + radius) {
                            val y = levelY + amp * sin((x / radius) * 3.2f + t * (1.6f + layer * 0.7f) + layer * 2f)
                            lineTo(x, y)
                            x += 4f
                        }
                        lineTo(c.x + radius, c.y + radius)
                        close()
                    }
                    drawPath(wave, Brush.verticalGradient(listOf(color.copy(alpha = 0.55f - layer * 0.2f), color.copy(alpha = 0.2f)), levelY, c.y + radius))
                }
                if (charging) {
                    bubbles.forEach { b ->
                        val speed = b[2]
                        val p = (t * 0.12f * speed + b[1]) % 1f
                        val y = c.y + radius - p * (2 * radius * level)
                        val x = c.x - radius * 0.7f + b[0] * radius * 1.4f + sin(t * 2f + b[0] * 9f) * radius * 0.03f
                        drawCircle(Color.White.copy(alpha = 0.25f * (1 - p)), radius * 0.025f * speed, Offset(x, y))
                    }
                }
            }
            drawArc(
                color, -90f, 360f * level, useCenter = false,
                topLeft = Offset(c.x - radius * 1.1f, c.y - radius * 1.1f),
                size = androidx.compose.ui.geometry.Size(radius * 2.2f, radius * 2.2f),
                style = Stroke(width = radius * 0.035f, cap = StrokeCap.Round),
            )
        }
        Text("${ctx.battery.level}%", color = Color.White, fontSize = textSize, fontWeight = FontWeight.Light, style = TabularNumbers)
    }
}

// ---------------------------------------------------------------- Weather

private sealed interface WeatherState {
    data object NoLocation : WeatherState
    data object Loading : WeatherState
    data object Error : WeatherState
    data class Ready(val data: WeatherData, val sample: Boolean = false) : WeatherState
}

private val SampleWeather = WeatherData(
    temp = 18, feelsLike = 17, code = 2, isDay = false, high = 21, low = 11, precipitationChance = 10,
    hourly = (1..5).map { HourForecast(LocalDateTime.now().plusHours(it * 2L).withMinute(0), 18 - it, if (it == 3) 61 else 2, false) },
    fetchedAtMs = 0,
)

fun weatherIcon(kind: WeatherKind, isDay: Boolean): ImageVector = when (kind) {
    WeatherKind.CLEAR -> if (isDay) Icons.Rounded.WbSunny else Icons.Rounded.Bedtime
    WeatherKind.PARTLY_CLOUDY -> if (isDay) Icons.Rounded.WbCloudy else Icons.Rounded.NightsStay
    WeatherKind.CLOUDY -> Icons.Rounded.Cloud
    WeatherKind.FOG -> Icons.Rounded.Dehaze
    WeatherKind.DRIZZLE -> Icons.Rounded.Grain
    WeatherKind.RAIN -> Icons.Rounded.Umbrella
    WeatherKind.SNOW -> Icons.Rounded.AcUnit
    WeatherKind.STORM -> Icons.Rounded.Thunderstorm
}

fun weatherLabel(kind: WeatherKind): Int = when (kind) {
    WeatherKind.CLEAR -> R.string.weather_clear
    WeatherKind.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherKind.CLOUDY -> R.string.weather_cloudy
    WeatherKind.FOG -> R.string.weather_fog
    WeatherKind.DRIZZLE -> R.string.weather_drizzle
    WeatherKind.RAIN -> R.string.weather_rain
    WeatherKind.SNOW -> R.string.weather_snow
    WeatherKind.STORM -> R.string.weather_storm
}

private fun weatherTint(kind: WeatherKind, isDay: Boolean): Color = when (kind) {
    WeatherKind.CLEAR, WeatherKind.PARTLY_CLOUDY -> if (isDay) Color(0xFFFFD36E) else Color(0xFFC9D6FF)
    WeatherKind.RAIN, WeatherKind.DRIZZLE -> Color(0xFF8CC8FF)
    WeatherKind.SNOW -> Color(0xFFE6F2FF)
    WeatherKind.STORM -> Color(0xFFBBA6FF)
    else -> Color(0xFFCFD8DC)
}

@Composable
fun WeatherSaver(ctx: SaverContext) {
    val location = ctx.settings.weatherLocation
    val unit = ctx.settings.tempUnit
    val initial = when {
        location != null -> WeatherState.Loading
        ctx.preview -> WeatherState.Ready(SampleWeather, sample = true)
        else -> WeatherState.NoLocation
    }
    var state by remember(location, unit) { mutableStateOf(initial) }
    LaunchedEffect(location, unit) {
        while (location != null) {
            val data = WeatherRepository.forecast(location, unit).getOrNull()
            val previous = state
            // Keep showing the last good forecast if a refresh fails.
            state = when {
                data != null -> WeatherState.Ready(data)
                previous is WeatherState.Ready -> previous
                else -> WeatherState.Error
            }
            delay(if (data != null) 15 * 60_000L else 2 * 60_000L)
        }
    }
    val float = rememberFrameTime()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val unitSize = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        val timeSize = with(density) { (if (landscape) maxHeight * 0.26f else maxWidth * 0.22f).toSp() }
        val body = with(density) { (unitSize * 0.045f).toSp() }

        val weatherBlock: @Composable () -> Unit = {
            when (val s = state) {
                WeatherState.NoLocation -> Message(stringResource(R.string.weather_no_location), body)
                WeatherState.Loading -> Message(stringResource(R.string.weather_loading), body)
                WeatherState.Error -> Message(stringResource(R.string.weather_error), body)
                is WeatherState.Ready -> {
                    val d = s.data
                    val kind = weatherKind(d.code)
                    val tint = weatherTint(kind, d.isDay)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                weatherIcon(kind, d.isDay), null, tint = tint,
                                modifier = Modifier
                                    .size(unitSize * 0.18f)
                                    .graphicsLayer { translationY = sin(float.value * 0.8f) * 6.dp.toPx() },
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("${d.temp}°", color = Color.White, fontSize = body * 3.2f, lineHeight = body * 3.2f, fontWeight = FontWeight.Light, style = TabularNumbers)
                                Text(stringResource(weatherLabel(kind)), color = tint, fontSize = body)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        val place = location?.name?.takeIf { it.isNotBlank() }
                        Text(
                            listOfNotNull(
                                stringResource(R.string.weather_high_low, d.high, d.low),
                                d.precipitationChance?.takeIf { it >= 20 }?.let { stringResource(R.string.weather_precip, it) },
                                place,
                                if (s.sample) stringResource(R.string.sample_data) else null,
                            ).joinToString("  ·  "),
                            color = Color.White.copy(alpha = 0.55f), fontSize = body * 0.8f, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(unitSize * 0.05f))
                        Row(horizontalArrangement = Arrangement.spacedBy(unitSize * 0.06f)) {
                            d.hourly.forEach { h ->
                                val k = weatherKind(h.code)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        if (ctx.use24h) "%02d".format(h.time.hour) else "${dev.pridgey.beddery.savers.hour12(h.time.hour)}${amPm(h.time.toLocalTime()).lowercase()}",
                                        color = Color.White.copy(alpha = 0.5f), fontSize = body * 0.7f,
                                    )
                                    Icon(weatherIcon(k, h.isDay), null, tint = weatherTint(k, h.isDay).copy(alpha = 0.85f), modifier = Modifier.size(unitSize * 0.06f).padding(vertical = 2.dp))
                                    Text("${h.temp}°", color = Color.White.copy(alpha = 0.8f), fontSize = body * 0.8f, style = TabularNumbers)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (landscape) {
            Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ClockBlock(ctx, timeSize)
                        Spacer(Modifier.height(12.dp))
                        StatusLine(ctx, body * 0.7f)
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { weatherBlock() }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                ClockBlock(ctx, timeSize)
                Spacer(Modifier.height(unitSize * 0.14f))
                weatherBlock()
                Spacer(Modifier.height(unitSize * 0.12f))
                StatusLine(ctx, body * 0.7f)
            }
        }
    }
}

// ---------------------------------------------------------------- Agenda

private sealed interface AgendaState {
    data object NoPermission : AgendaState
    data class Ready(val events: List<AgendaEvent>, val sample: Boolean = false) : AgendaState
}

private fun sampleEvents(): List<AgendaEvent> {
    val base = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
    return listOf(
        AgendaEvent("Morning run", base.plusHours(9), base.plusHours(10), false, 0xFF7FE3B8.toInt()),
        AgendaEvent("Team stand-up", base.plusHours(11), base.plusHours(11).plusMinutes(15), false, 0xFF8CC8FF.toInt()),
        AgendaEvent("Lunch with Sam", base.plusHours(13), base.plusHours(14), false, 0xFFFFC66E.toInt()),
        AgendaEvent("Dentist", base.plusHours(26), base.plusHours(27), false, 0xFFFF9AB0.toInt()),
    )
}

@Composable
fun AgendaSaver(ctx: SaverContext) {
    val context = LocalContext.current
    val state by produceState<AgendaState>(
        if (CalendarRepository.hasPermission(context)) AgendaState.Ready(emptyList())
        else if (ctx.preview) AgendaState.Ready(sampleEvents(), true) else AgendaState.NoPermission,
    ) {
        if (!CalendarRepository.hasPermission(context)) return@produceState
        while (true) {
            value = AgendaState.Ready(CalendarRepository.upcoming(context))
            delay(5 * 60_000L)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val unit = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        val timeSize = with(density) { (if (landscape) maxHeight * 0.28f else maxWidth * 0.24f).toSp() }
        val body = with(density) { (unit * 0.048f).toSp() }
        val list: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(unit * 0.03f)) {
                when (val s = state) {
                    AgendaState.NoPermission -> Message(stringResource(R.string.agenda_no_permission), body)
                    is AgendaState.Ready -> if (s.events.isEmpty()) {
                        Message(stringResource(R.string.agenda_empty), body)
                    } else {
                        var lastDay: java.time.LocalDate? = null
                        s.events.forEach { e ->
                            val day = e.begin.toLocalDate()
                            if (day != lastDay) {
                                lastDay = day
                                val today = ctx.now.toLocalDate()
                                val label = when {
                                    !day.isAfter(today) -> stringResource(R.string.agenda_today)
                                    day == today.plusDays(1) -> stringResource(R.string.agenda_tomorrow)
                                    else -> dev.pridgey.beddery.savers.formatShortDate(day)
                                }
                                Text(label.uppercase(), color = ctx.accent, fontSize = body * 0.7f, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
                            }
                            EventRow(e, ctx, body, unit)
                        }
                        if (s.sample) Text(stringResource(R.string.sample_data), color = Color.White.copy(alpha = 0.35f), fontSize = body * 0.65f)
                    }
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    ClockBlock(ctx, timeSize)
                    Spacer(Modifier.height(12.dp))
                    StatusLine(ctx, body * 0.65f)
                }
                Spacer(Modifier.width(32.dp))
                list(Modifier.weight(1.1f))
            }
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                ClockBlock(ctx, timeSize)
                Spacer(Modifier.height(8.dp))
                StatusLine(ctx, body * 0.65f)
                Spacer(Modifier.height(unit * 0.12f))
                list(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun EventRow(e: AgendaEvent, ctx: SaverContext, body: TextUnit, unit: Dp) {
    val now = ctx.now
    val happening = !e.allDay && !now.isBefore(e.begin) && now.isBefore(e.end)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .height(unit * 0.09f)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(e.color).copy(alpha = if (happening) 1f else 0.8f)),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                e.title.ifBlank { stringResource(R.string.agenda_untitled) },
                color = Color.White.copy(alpha = if (happening) 1f else 0.85f), fontSize = body,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val time = when {
                e.allDay -> stringResource(R.string.agenda_all_day)
                happening -> stringResource(R.string.agenda_now_until, formatTimeWithAmPm(e.end.toLocalTime(), ctx.use24h))
                else -> {
                    val minutes = Duration.between(now, e.begin).toMinutes()
                    val range = "${formatTimeWithAmPm(e.begin.toLocalTime(), ctx.use24h)} – ${formatTimeWithAmPm(e.end.toLocalTime(), ctx.use24h)}"
                    if (minutes in 1..59) stringResource(R.string.agenda_in_minutes, range, minutes) else range
                }
            }
            Text(time, color = Color.White.copy(alpha = 0.5f), fontSize = body * 0.75f, style = TabularNumbers)
        }
    }
}

// ---------------------------------------------------------------- World clock

fun cityName(zoneId: String): String = zoneId.substringAfterLast('/').replace('_', ' ')

@Composable
fun WorldClockSaver(ctx: SaverContext) {
    val local = ctx.now.atZone(ZoneId.systemDefault())
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val unit = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        val timeSize = with(density) { (unit * 0.2f).toSp() }
        val cityTime = with(density) { (unit * 0.075f).toSp() }
        val small = with(density) { (unit * 0.034f).toSp() }
        val zones = ctx.settings.worldZones.mapNotNull { runCatching { ZoneId.of(it) }.getOrNull() }
        val columns = if (landscape) 3 else 2
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ClockBlock(ctx, timeSize)
            Spacer(Modifier.height(unit * 0.1f))
            zones.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(unit * 0.06f)) {
                    row.forEach { zone ->
                        val there = local.withZoneSameInstant(zone)
                        val dayDiff = there.toLocalDate().compareTo(local.toLocalDate())
                        val offsetHours = (there.offset.totalSeconds - local.offset.totalSeconds) / 3600f
                        val isDay = there.hour in 7..18
                        Column(Modifier.width(unit * 0.38f).padding(vertical = unit * 0.025f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isDay) Icons.Rounded.LightMode else Icons.Rounded.NightsStay, null,
                                    tint = if (isDay) Color(0xFFFFD36E) else Color(0xFFC9D6FF),
                                    modifier = Modifier.size(unit * 0.04f),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(cityName(zone.id), color = Color.White.copy(alpha = 0.75f), fontSize = small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(
                                formatTimeWithAmPm(there.toLocalTime(), ctx.use24h), color = Color.White,
                                fontSize = cityTime, fontWeight = FontWeight.Light, style = TabularNumbers, maxLines = 1,
                            )
                            val offsetLabel = when {
                                offsetHours == 0f -> stringResource(R.string.world_same_time)
                                offsetHours % 1f == 0f -> "%+d h".format(offsetHours.roundToInt())
                                else -> "%+.1f h".format(offsetHours)
                            }
                            val dayLabel = when {
                                dayDiff > 0 -> stringResource(R.string.agenda_tomorrow)
                                dayDiff < 0 -> stringResource(R.string.world_yesterday)
                                else -> stringResource(R.string.agenda_today)
                            }
                            Text("$dayLabel, $offsetLabel", color = Color.White.copy(alpha = 0.45f), fontSize = small * 0.85f)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Photo frame

@Composable
fun PhotoFrameSaver(ctx: SaverContext) {
    val context = LocalContext.current
    val uris = ctx.settings.photoUris
    if (uris.isEmpty()) {
        PhotoPlaceholder(ctx)
        return
    }
    val order = remember(uris) { uris.shuffled() }
    var index by remember(order) { mutableIntStateOf(0) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxEdge = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).roundToPx() }.coerceAtMost(2400)
        val target = if (ctx.preview) maxEdge / 3 else maxEdge
        LaunchedEffect(order, index) {
            var tries = 0
            var loaded: ImageBitmap? = null
            while (loaded == null && tries < order.size) {
                loaded = PhotoLoader.load(context, Uri.parse(order[(index + tries) % order.size]), target)?.asImageBitmap()
                if (loaded == null) tries++
            }
            if (loaded != null) image = loaded
            delay(if (ctx.preview) 6_000 else 25_000)
            index = (index + tries + 1) % order.size
        }
        Crossfade(image, animationSpec = tween(2000), label = "photo") { bmp ->
            if (bmp != null) KenBurns(bmp) else Box(Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
        )
        val timeSize = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.16f).toSp() }
        Column(Modifier.align(Alignment.BottomStart).padding(28.dp)) {
            ClockBlock(ctx, timeSize, Alignment.Start)
            Spacer(Modifier.height(6.dp))
            StatusLine(ctx, timeSize * 0.12f)
        }
    }
}

@Composable
private fun KenBurns(bitmap: ImageBitmap) {
    val progress = remember(bitmap) { Animatable(0f) }
    val dir = remember(bitmap) { floatArrayOf(Random.nextFloat() * 2 - 1, Random.nextFloat() * 2 - 1, if (Random.nextBoolean()) 1f else -1f) }
    LaunchedEffect(bitmap) { progress.animateTo(1f, tween(30_000, easing = LinearEasing)) }
    Image(
        bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = progress.value
                val zoom = if (dir[2] > 0) 1.05f + 0.12f * p else 1.17f - 0.12f * p
                scaleX = zoom
                scaleY = zoom
                translationX = dir[0] * size.width * 0.03f * (p - 0.5f)
                translationY = dir[1] * size.height * 0.03f * (p - 0.5f)
            },
    )
}

@Composable
private fun PhotoPlaceholder(ctx: SaverContext) {
    val t = rememberFrameTime()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val unit = min(maxWidth, maxHeight)
        Canvas(Modifier.fillMaxSize()) {
            val time = t.value
            val colors = listOf(Color(0xFF2B2F6B), Color(0xFF6B2B5A), Color(0xFF2B6B5E))
            colors.forEachIndexed { i, c ->
                val cx = size.width * (0.5f + 0.3f * sin(time * 0.1f + i * 2.1f))
                val cy = size.height * (0.5f + 0.3f * sin(time * 0.13f + i * 1.3f + PI.toFloat()))
                drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), Offset(cx, cy), size.maxDimension * 0.5f), size.maxDimension * 0.5f, Offset(cx, cy))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ClockBlock(ctx, with(LocalDensity.current) { (unit * 0.2f).toSp() })
            Spacer(Modifier.height(24.dp))
            Message(stringResource(R.string.photos_none), with(LocalDensity.current) { (unit * 0.04f).toSp() })
        }
    }
}
