package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.Astronomy
import dev.pridgey.beddery.data.CalendarRepository
import dev.pridgey.beddery.data.NotificationsMode
import dev.pridgey.beddery.notifications.NotificationStore
import dev.pridgey.beddery.notifications.byApp
import dev.pridgey.beddery.savers.AppGlyph
import dev.pridgey.beddery.savers.MoonRender
import dev.pridgey.beddery.savers.SaverContext
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.StatusLine
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatLongDate
import dev.pridgey.beddery.savers.formatShortDate
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.rememberNoiseBrush
import dev.pridgey.beddery.savers.rememberNotifications
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Compact time + date header used by several info screens. */
@Composable
private fun TimeHeader(ctx: SaverContext, size: TextUnit, align: Alignment.Horizontal = Alignment.CenterHorizontally) {
    val t = ctx.now.toLocalTime()
    Column(horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatClock(t, ctx.use24h), color = Color.White, fontSize = size, lineHeight = size, fontWeight = FontWeight.Thin, style = TabularNumbers)
            if (!ctx.use24h) Text(amPm(t), color = ctx.accent, fontSize = size * 0.2f, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        }
        Text(formatLongDate(ctx.now.toLocalDate()), color = Color.White.copy(alpha = 0.55f), fontSize = size * 0.15f)
    }
}

// ---------------------------------------------------------------- Sleep

@Composable
fun SleepSaver(ctx: SaverContext) {
    val sampleAlarm = remember { LocalDateTime.now().plusHours(7).plusMinutes(25) }
    val alarm = ctx.nextAlarm ?: if (ctx.demo) sampleAlarm else null
    val stars = remember { StarSky(90, 17, 0f, 1f) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val unit = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        val big = with(density) { (unit * 0.26f).toSp() }
        val body = with(density) { (unit * 0.045f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF060914), Color.Black)))
            stars.draw(this, ctx.now.second * 0.3f, 0.45f, horizonFade = 0.6f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(formatTimeWithAmPm(ctx.now.toLocalTime(), ctx.use24h), color = Color.White.copy(alpha = 0.55f), fontSize = body, style = TabularNumbers)
            Spacer(Modifier.height(unit * 0.06f))
            if (alarm != null) {
                val remaining = Duration.between(ctx.now, alarm).coerceAtLeast(Duration.ZERO)
                val h = remaining.toHours()
                val m = remaining.toMinutes() % 60
                Row(verticalAlignment = Alignment.Bottom) {
                    if (h > 0) {
                        Text("$h", color = Color.White, fontSize = big, lineHeight = big, fontWeight = FontWeight.Thin, style = TabularNumbers)
                        Text(stringResource(R.string.unit_h), color = ctx.accent, fontSize = big * 0.28f, modifier = Modifier.padding(start = 4.dp, end = 18.dp, bottom = 14.dp))
                    }
                    Text("$m", color = Color.White, fontSize = big, lineHeight = big, fontWeight = FontWeight.Thin, style = TabularNumbers)
                    Text(stringResource(R.string.unit_m), color = ctx.accent, fontSize = big * 0.28f, modifier = Modifier.padding(start = 4.dp, bottom = 14.dp))
                }
                Text(
                    stringResource(R.string.sleep_until_alarm, formatTimeWithAmPm(alarm.toLocalTime(), ctx.use24h)),
                    color = Color.White.copy(alpha = 0.7f), fontSize = body,
                )
                Spacer(Modifier.height(unit * 0.09f))
                // 90-minute sleep cycles, after about a quarter hour to fall asleep.
                val asleep = remaining.minusMinutes(15).toMinutes().coerceAtLeast(0)
                val full = (asleep / 90).toInt().coerceAtMost(8)
                val partial = (asleep % 90) / 90f
                Row(horizontalArrangement = Arrangement.spacedBy(unit * 0.02f), verticalAlignment = Alignment.CenterVertically) {
                    repeat(full) { CyclePill(unit, 1f, ctx.accent) }
                    if (full < 8 && partial > 0.05f) CyclePill(unit, partial, ctx.accent)
                }
                Spacer(Modifier.height(unit * 0.03f))
                Text(
                    if (full == 0) stringResource(R.string.sleep_short) else pluralStringResource(R.plurals.sleep_cycles, full, full),
                    color = Color.White.copy(alpha = 0.5f), fontSize = body * 0.85f, textAlign = TextAlign.Center,
                )
            } else {
                Text(stringResource(R.string.sleep_no_alarm), color = Color.White, fontSize = body * 2f, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(unit * 0.03f))
                Text(stringResource(R.string.sleep_suggest), color = Color.White.copy(alpha = 0.6f), fontSize = body, textAlign = TextAlign.Center)
                Spacer(Modifier.height(unit * 0.05f))
                Row(horizontalArrangement = Arrangement.spacedBy(unit * 0.025f)) {
                    listOf(4, 5, 6).forEach { cycles ->
                        val wake = ctx.now.plusMinutes(15L + cycles * 90L)
                        val best = cycles == 5
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (best) ctx.accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f))
                                .padding(horizontal = unit * 0.03f, vertical = unit * 0.025f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(formatTimeWithAmPm(wake.toLocalTime(), ctx.use24h), color = if (best) ctx.accent else Color.White, fontSize = body, maxLines = 1, softWrap = false, style = TabularNumbers)
                            Text(stringResource(R.string.sleep_hours, if (cycles % 2 == 0) "${cycles * 3 / 2}" else "%.1f".format(cycles * 1.5f)), color = Color.White.copy(alpha = 0.45f), fontSize = body * 0.75f)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CyclePill(unit: Dp, fill: Float, accent: Color) {
    Box(
        Modifier
            .width(unit * 0.07f)
            .height(unit * 0.022f)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.1f)),
    ) {
        Box(Modifier.fillMaxSize().padding(end = unit * 0.07f * (1f - fill)).clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.85f)))
    }
}

// ---------------------------------------------------------------- Moon phase

private fun phaseLabel(p: Astronomy.Phase): Int = when (p) {
    Astronomy.Phase.NEW -> R.string.moon_new
    Astronomy.Phase.WAXING_CRESCENT -> R.string.moon_waxing_crescent
    Astronomy.Phase.FIRST_QUARTER -> R.string.moon_first_quarter
    Astronomy.Phase.WAXING_GIBBOUS -> R.string.moon_waxing_gibbous
    Astronomy.Phase.FULL -> R.string.moon_full
    Astronomy.Phase.WANING_GIBBOUS -> R.string.moon_waning_gibbous
    Astronomy.Phase.LAST_QUARTER -> R.string.moon_last_quarter
    Astronomy.Phase.WANING_CRESCENT -> R.string.moon_waning_crescent
}

@Composable
fun MoonPhaseSaver(ctx: SaverContext) {
    val phase = remember(ctx.now.hour, ctx.now.minute / 10) { Astronomy.moonPhase(Instant.now()) }
    val illum = Astronomy.illumination(phase)
    val southern = ctx.settings.weatherLocation?.let { it.latitude < 0 } ?: (Astronomy.guessLocation(ZoneId.systemDefault()).first < 0)
    val stars = remember { StarSky(200, 81) }
    val moon = remember((phase * 200).toInt(), southern) { MoonRender.render(480, phase, southern) }
    val nextFull = phase < 0.5
    val days = Astronomy.daysUntil(phase, if (nextFull) 0.5 else 0.0)
    val date = ctx.now.toLocalDate().plusDays(Math.round(days))
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val unit = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        val title = with(density) { (unit * 0.075f).toSp() }
        val body = with(density) { (unit * 0.042f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            stars.draw(this, ctx.now.second * 0.4f, 0.6f, horizonFade = 0f)
            val r = size.minDimension * if (landscape) 0.3f else 0.32f
            val c = if (landscape) Offset(size.width * 0.3f, size.height / 2) else Offset(size.width / 2, size.height * 0.4f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFDDE4F5).copy(alpha = 0.04f + 0.12f * illum.toFloat()), Color.Transparent), c, r * 2.4f), r * 2.4f, c)
            // Keep the dark side from being lit by stars behind it.
            drawCircle(Color.Black, r * 0.995f, c)
            drawImage(
                moon.asImageBitmap(), srcOffset = IntOffset.Zero, srcSize = IntSize(moon.width, moon.height),
                dstOffset = IntOffset((c.x - r).toInt(), (c.y - r).toInt()), dstSize = IntSize((r * 2).toInt(), (r * 2).toInt()),
                filterQuality = FilterQuality.High,
            )
        }
        val info: @Composable () -> Unit = {
            Column(horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally) {
                Text(stringResource(phaseLabel(Astronomy.phaseName(phase))), color = Color.White, fontSize = title, fontWeight = FontWeight.Light)
                Text(stringResource(R.string.moon_illuminated, Math.round(illum * 100).toInt()), color = ctx.accent, fontSize = body)
                Spacer(Modifier.height(unit * 0.03f))
                val whole = Math.round(days).toInt()
                Text(
                    when {
                        whole == 0 -> stringResource(if (nextFull) R.string.moon_full_today else R.string.moon_new_today)
                        else -> pluralStringResource(if (nextFull) R.plurals.moon_full_in else R.plurals.moon_new_in, whole, whole, formatShortDate(date))
                    },
                    color = Color.White.copy(alpha = 0.6f), fontSize = body * 0.9f,
                )
                Spacer(Modifier.height(unit * 0.06f))
                Text(formatTimeWithAmPm(ctx.now.toLocalTime(), ctx.use24h) + "  ·  " + formatShortDate(ctx.now.toLocalDate()), color = Color.White.copy(alpha = 0.45f), fontSize = body * 0.85f, style = TabularNumbers)
            }
        }
        if (landscape) {
            Box(Modifier.align(Alignment.CenterEnd).padding(end = maxWidth * 0.08f).width(maxWidth * 0.36f)) { info() }
        } else {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.1f)) { info() }
        }
    }
}

// ---------------------------------------------------------------- Month

@Composable
fun MonthSaver(ctx: SaverContext) {
    val context = LocalContext.current
    val today = ctx.now.toLocalDate()
    val month = YearMonth.from(today)
    var events by remember(month) { mutableStateOf(if (ctx.demo) sampleDays(month) else emptySet()) }
    LaunchedEffect(month) {
        while (CalendarRepository.hasPermission(context)) {
            events = CalendarRepository.eventDays(context, month.atDay(1), month.atEndOfMonth())
            delay(15 * 60_000L)
        }
    }
    val firstDow = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val offset = (month.atDay(1).dayOfWeek.value - firstDow.value + 7) % 7
    val cells = offset + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val unit = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        val cell = if (landscape) min(maxHeight / (rows + 2.4f), maxWidth * 0.5f / 7f) else min(maxWidth / 7.4f, maxHeight * 0.5f / (rows + 2f))
        val dayText = with(density) { (cell * 0.38f).toSp() }
        val timeSize = with(density) { (unit * if (landscape) 0.2f else 0.2f).toSp() }
        val grid: @Composable () -> Unit = {
            Column {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(start = cell * 0.2f, bottom = cell * 0.25f)) {
                    Text(month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()), color = ctx.accent, fontSize = dayText * 1.5f, fontWeight = FontWeight.Medium)
                    Text("  ${month.year}", color = Color.White.copy(alpha = 0.45f), fontSize = dayText * 1.5f, fontWeight = FontWeight.Light)
                }
                Row {
                    for (i in 0 until 7) {
                        val dow = firstDow.plus(i.toLong())
                        Box(Modifier.size(cell, cell * 0.7f), contentAlignment = Alignment.Center) {
                            Text(dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = Color.White.copy(alpha = 0.4f), fontSize = dayText * 0.8f, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                for (r in 0 until rows) {
                    Row {
                        for (c in 0 until 7) {
                            val n = r * 7 + c - offset + 1
                            Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                                if (n in 1..month.lengthOfMonth()) {
                                    val day = month.atDay(n)
                                    val isToday = day == today
                                    if (isToday) Box(Modifier.size(cell * 0.78f).clip(CircleShape).background(ctx.accent))
                                    Text(
                                        "$n", fontSize = dayText, style = TabularNumbers,
                                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                                        color = when {
                                            isToday -> Color.Black
                                            day.isBefore(today) -> Color.White.copy(alpha = 0.28f)
                                            else -> Color.White.copy(alpha = 0.88f)
                                        },
                                    )
                                    if (day in events && !isToday) {
                                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = cell * 0.1f).size(cell * 0.08f).clip(CircleShape).background(ctx.accent.copy(alpha = if (day.isBefore(today)) 0.35f else 0.9f)))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (landscape) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 0.12f)) {
                Column {
                    TimeHeader(ctx, timeSize, Alignment.Start)
                    Spacer(Modifier.height(12.dp))
                    StatusLine(ctx, timeSize * 0.09f)
                }
                grid()
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimeHeader(ctx, timeSize)
                Spacer(Modifier.height(unit * 0.12f))
                grid()
            }
        }
    }
}

private fun sampleDays(month: YearMonth): Set<LocalDate> = listOf(3, 8, 12, 17, 21, 26).mapNotNull { d -> month.atDay(d.coerceAtMost(month.lengthOfMonth())) }.toSet()

// ---------------------------------------------------------------- Progress

@Composable
fun ProgressSaver(ctx: SaverContext) {
    val now = ctx.now
    val today = now.toLocalDate()
    val dayFrac = now.toLocalTime().toSecondOfDay() / 86_400f
    val firstDow = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val weekIndex = (today.dayOfWeek.value - firstDow.value + 7) % 7
    val rows = listOf(
        stringResource(R.string.progress_day) to dayFrac,
        stringResource(R.string.progress_week) to (weekIndex + dayFrac) / 7f,
        stringResource(R.string.progress_month) to (today.dayOfMonth - 1 + dayFrac) / today.lengthOfMonth(),
        today.year.toString() to (today.dayOfYear - 1 + dayFrac) / today.lengthOfYear(),
    )
    val pulse = rememberInfiniteTransition(label = "today")
    val glow by pulse.animateFloat(0.45f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow")
    BoxWithConstraints(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val unit = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        val body = with(density) { (unit * 0.045f).toSp() }
        val timeSize = with(density) { (unit * 0.18f).toSp() }
        val bars: @Composable (Modifier) -> Unit = { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(unit * 0.035f)) {
                rows.forEach { (label, frac) ->
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Text(label, color = Color.White.copy(alpha = 0.75f), fontSize = body, modifier = Modifier.weight(1f))
                            Text("${(frac * 100).toInt()}%", color = Color.White, fontSize = body, style = TabularNumbers)
                        }
                        Spacer(Modifier.height(unit * 0.012f))
                        Box(Modifier.fillMaxWidth().height(unit * 0.018f).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
                            Box(Modifier.fillMaxWidth(frac.coerceIn(0.01f, 1f)).height(unit * 0.018f).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(ctx.accent.copy(alpha = 0.45f), ctx.accent))))
                        }
                    }
                }
            }
        }
        val boxH = maxHeight
        val dots: @Composable (Modifier) -> Unit = { m ->
            Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
                val total = today.lengthOfYear()
                Canvas(Modifier.fillMaxWidth().height(if (landscape) boxH * 0.55f else boxH * 0.3f)) {
                    val cols = if (size.width > size.height * 1.4f) 31 else 25
                    val rowsN = (total + cols - 1) / cols
                    val step = minOf(size.width / cols, size.height / rowsN)
                    val ox = (size.width - step * cols) / 2 + step / 2
                    val oy = (size.height - step * rowsN) / 2 + step / 2
                    for (i in 0 until total) {
                        val o = Offset(ox + (i % cols) * step, oy + (i / cols) * step)
                        val d = i + 1
                        when {
                            d < today.dayOfYear -> drawCircle(Color.White.copy(alpha = 0.5f), step * 0.26f, o)
                            d == today.dayOfYear -> {
                                drawCircle(ctx.accent.copy(alpha = 0.3f * glow), step * 0.6f, o)
                                drawCircle(ctx.accent, step * 0.32f, o)
                            }
                            else -> drawCircle(Color.White.copy(alpha = 0.09f), step * 0.22f, o)
                        }
                    }
                }
                Spacer(Modifier.height(unit * 0.03f))
                Text(
                    stringResource(R.string.progress_caption, today.dayOfYear, total, total - today.dayOfYear),
                    color = Color.White.copy(alpha = 0.5f), fontSize = body * 0.85f,
                )
            }
        }
        if (landscape) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(unit * 0.1f)) {
                Column(Modifier.weight(1f)) {
                    TimeHeader(ctx, timeSize, Alignment.Start)
                    Spacer(Modifier.height(unit * 0.08f))
                    bars(Modifier.fillMaxWidth())
                }
                dots(Modifier.weight(1.1f))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimeHeader(ctx, timeSize)
                Spacer(Modifier.height(unit * 0.1f))
                bars(Modifier.fillMaxWidth())
                Spacer(Modifier.height(unit * 0.1f))
                dots(Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------------------------------------------------------------- Notifications

fun relativeTime(context: android.content.Context, postedAt: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - postedAt) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> context.getString(R.string.time_now)
        minutes < 60 -> context.getString(R.string.duration_minutes, minutes.toInt())
        minutes < 24 * 60 -> context.getString(R.string.duration_hours, (minutes / 60).toInt())
        else -> context.getString(R.string.time_days, (minutes / (24 * 60)).toInt())
    }
}

@Composable
fun NotificationsSaver(ctx: SaverContext) {
    val context = LocalContext.current
    val mode = ctx.settings.notificationsMode
    var access by remember { mutableStateOf(NotificationStore.hasAccess(context)) }
    LaunchedEffect(ctx.now.minute) { access = NotificationStore.hasAccess(context) }
    val items = rememberNotifications(sample = ctx.demo)
    val apps = remember(items) { items.byApp() }
    BoxWithConstraints(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val unit = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        val timeSize = with(density) { (unit * 0.2f).toSp() }
        val body = with(density) { (unit * 0.042f).toSp() }
        val showContent = mode == NotificationsMode.PREVIEWS || ctx.demo
        val list: @Composable (Modifier) -> Unit = { m ->
            Column(m.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(unit * 0.025f)) {
                when {
                    !ctx.demo && mode == NotificationsMode.OFF -> Hint(stringResource(R.string.notifications_saver_off), body)
                    !ctx.demo && !access -> Hint(stringResource(R.string.notifications_saver_no_access), body)
                    apps.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = ctx.accent.copy(alpha = 0.8f), modifier = Modifier.size(unit * 0.06f))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.notifications_caught_up), color = Color.White.copy(alpha = 0.75f), fontSize = body * 1.1f)
                    }
                    else -> apps.take(if (landscape) 4 else 5).forEach { app ->
                        val latest = app.latest
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp))
                                .padding(unit * 0.03f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppGlyph(app, null, unit * 0.1f)
                            Spacer(Modifier.width(unit * 0.035f))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(app.appName, color = Color.White.copy(alpha = 0.55f), fontSize = body * 0.8f, fontWeight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                                    Text("  ·  " + relativeTime(context, latest.postedAt), color = Color.White.copy(alpha = 0.4f), fontSize = body * 0.8f, maxLines = 1)
                                }
                                if (showContent && !latest.isPrivate) {
                                    Text(latest.title ?: app.appName, color = Color.White, fontSize = body, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    latest.text?.let { Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = body * 0.88f, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                } else {
                                    Text(pluralStringResource(R.plurals.notifications_count, app.items.size, app.items.size), color = Color.White, fontSize = body)
                                }
                            }
                            if (app.items.size > 1) {
                                Spacer(Modifier.width(8.dp))
                                Text("${app.items.size}", color = Color.White.copy(alpha = 0.6f), fontSize = body * 0.85f, style = TabularNumbers)
                            }
                        }
                    }
                }
            }
        }
        if (landscape) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(0.9f)) {
                    TimeHeader(ctx, timeSize, Alignment.Start)
                    Spacer(Modifier.height(12.dp))
                    StatusLine(ctx, timeSize * 0.09f)
                }
                list(Modifier.weight(1.1f))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TimeHeader(ctx, timeSize)
                Spacer(Modifier.height(8.dp))
                StatusLine(ctx, timeSize * 0.09f)
                Spacer(Modifier.height(unit * 0.1f))
                list(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Hint(text: String, size: TextUnit) {
    Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = size, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), letterSpacing = 0.em)
}
