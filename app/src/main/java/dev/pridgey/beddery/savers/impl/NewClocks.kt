package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.Astronomy
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.StatusLine
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatDuration
import dev.pridgey.beddery.savers.formatLongDate
import dev.pridgey.beddery.savers.formatShortDate
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.hour12
import dev.pridgey.beddery.savers.rememberFrameTime
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private fun hourDigits(ctx: SaverContext): Pair<Int?, Int> {
    val t = ctx.now.toLocalTime()
    val h = if (ctx.use24h) t.hour else hour12(t.hour)
    // 12-hour clocks leave the leading digit dark, like the real thing.
    val tens = if (ctx.use24h || h >= 10) h / 10 else null
    return tens to h % 10
}

// ---------------------------------------------------------------- Bedside Classic (seven-segment LED)

private val LedRed = Color(0xFFFF2A17)

// Segments a b c d e f g, per digit.
private val SegmentMask = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F)

private fun segmentPath(x0: Float, y0: Float, x1: Float, y1: Float, t: Float, skew: (Float, Float) -> Offset): Path {
    val horizontal = abs(y1 - y0) < abs(x1 - x0)
    val pts = if (horizontal) {
        listOf(Offset(x0, y0), Offset(x0 + t / 2, y0 - t / 2), Offset(x1 - t / 2, y1 - t / 2), Offset(x1, y1), Offset(x1 - t / 2, y1 + t / 2), Offset(x0 + t / 2, y0 + t / 2))
    } else {
        listOf(Offset(x0, y0), Offset(x0 + t / 2, y0 + t / 2), Offset(x1 + t / 2, y1 - t / 2), Offset(x1, y1), Offset(x1 - t / 2, y1 - t / 2), Offset(x0 - t / 2, y0 + t / 2))
    }
    return Path().apply {
        pts.forEachIndexed { i, p ->
            val q = skew(p.x, p.y)
            if (i == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
        }
        close()
    }
}

private fun digitSegments(x: Float, y: Float, w: Float, h: Float, t: Float, skew: (Float, Float) -> Offset): List<Path> {
    val g = t * 0.18f
    val l = x + t / 2
    val r = x + w - t / 2
    val top = y + t / 2
    val mid = y + h / 2
    val bot = y + h - t / 2
    return listOf(
        segmentPath(l + g, top, r - g, top, t, skew), // a
        segmentPath(r, top + g, r, mid - g, t, skew), // b
        segmentPath(r, mid + g, r, bot - g, t, skew), // c
        segmentPath(l + g, bot, r - g, bot, t, skew), // d
        segmentPath(l, mid + g, l, bot - g, t, skew), // e
        segmentPath(l, top + g, l, mid - g, t, skew), // f
        segmentPath(l + g, mid, r - g, mid, t, skew), // g
    )
}

private class LedLayout(val digits: List<List<Path>>, val colon: List<Rect>, val labelX: Float, val top: Float, val bottom: Float, val height: Float)

@Composable
fun BedsideClassicSaver(ctx: SaverContext) {
    val measurer = rememberTextMeasurer()
    val (tens, ones) = hourDigits(ctx)
    val t = ctx.now.toLocalTime()
    val digits = listOf(tens, ones, t.minute / 10, t.minute % 10)
    val colonOn = t.second % 2 == 0
    val pm = !ctx.use24h && t.hour >= 12
    val alarm = ctx.nextAlarm
    val date = formatShortDate(ctx.now.toLocalDate()).uppercase()
    val alarmText = alarm?.let { "AL " + formatTimeWithAmPm(it.toLocalTime(), ctx.use24h) }
    val layout = remember {
        SizeCache { s ->
            val landscape = s.width > s.height
            val dh = if (landscape) minOf(s.height * 0.44f, s.width * 0.19f) else s.width * 0.27f
            val dw = dh * 0.52f
            val gap = dw * 0.22f
            val colonW = dw * 0.35f
            val total = dw * 4 + gap * 2 + colonW + gap * 2
            val x0 = (s.width - total) / 2 + dw * 0.05f
            val y0 = (s.height - dh) / 2 - dh * 0.08f
            val th = dw * 0.17f
            val skewK = 0.07f
            val skew = { x: Float, y: Float -> Offset(x + (y0 + dh / 2 - y) * skewK, y) }
            val xs = listOf(x0, x0 + dw + gap, x0 + dw * 2 + gap * 3 + colonW, x0 + dw * 3 + gap * 4 + colonW)
            val cx = x0 + dw * 2 + gap * 2 + colonW / 2
            val dot = th * 1.05f
            LedLayout(
                digits = xs.map { digitSegments(it, y0, dw, dh, th, skew) },
                colon = listOf(y0 + dh * 0.32f, y0 + dh * 0.68f).map { cy -> skew(cx, cy).let { Rect(it.x - dot / 2, it.y - dot / 2, it.x + dot / 2, it.y + dot / 2) } },
                labelX = x0 + dh * 0.06f,
                top = y0,
                bottom = y0 + dh,
                height = dh,
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val l = layout.get(size)
        // Smoked acrylic: a faint reflection across the top of the panel.
        drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.035f), Color.Transparent), Offset(0f, 0f), Offset(size.width * 0.3f, size.height * 0.6f)))
        val glowA = Stroke(width = l.height * 0.06f, join = StrokeJoin.Round)
        val glowB = Stroke(width = l.height * 0.025f, join = StrokeJoin.Round)
        l.digits.forEachIndexed { i, segs ->
            val mask = digits[i]?.let { SegmentMask[it] } ?: 0
            segs.forEachIndexed { s, path ->
                if (mask and (1 shl s) != 0) {
                    drawPath(path, LedRed.copy(alpha = 0.10f), style = glowA, blendMode = BlendMode.Plus)
                    drawPath(path, LedRed.copy(alpha = 0.22f), style = glowB, blendMode = BlendMode.Plus)
                    drawPath(path, LedRed)
                } else {
                    drawPath(path, LedRed.copy(alpha = 0.055f))
                }
            }
        }
        l.colon.forEach { r ->
            drawRoundRect(LedRed.copy(alpha = if (colonOn) 1f else 0.06f), r.topLeft, r.size, CornerRadius(r.width * 0.25f))
            if (colonOn) drawCircle(LedRed.copy(alpha = 0.12f), r.width * 1.4f, r.center, blendMode = BlendMode.Plus)
        }
        // Indicator lamps, like the little dots beside an old alarm clock's digits.
        val label = TextStyle(color = LedRed, fontSize = (l.height * 0.08f).toSp(), letterSpacing = 0.12.em, fontWeight = FontWeight.Medium)
        // Indicator lamps above the digits: a dot that lights up beside its label.
        var x = l.labelX
        val y = l.top - l.height * 0.2f
        fun indicator(text: String, on: Boolean) {
            val layoutText = measurer.measure(text, label)
            val dotR = l.height * 0.03f
            drawCircle(LedRed.copy(alpha = if (on) 1f else 0.08f), dotR, Offset(x + dotR, y))
            if (on) drawCircle(LedRed.copy(alpha = 0.15f), dotR * 3f, Offset(x + dotR, y), blendMode = BlendMode.Plus)
            drawText(layoutText, color = LedRed.copy(alpha = if (on) 0.7f else 0.18f), topLeft = Offset(x + dotR * 3f, y - layoutText.size.height / 2f))
            x += dotR * 3f + layoutText.size.width + l.height * 0.14f
        }
        if (!ctx.use24h) indicator("PM", pm)
        indicator("ALARM", alarm != null)
        val footer = measurer.measure(
            listOfNotNull(date, alarmText).joinToString("     "),
            TextStyle(color = LedRed.copy(alpha = 0.55f), fontSize = (l.height * 0.1f).toSp(), letterSpacing = 0.2.em, fontFamily = FontFamily.Monospace),
        )
        drawText(footer, topLeft = Offset((size.width - footer.size.width) / 2f, l.bottom + l.height * 0.22f))
    }
}

// ---------------------------------------------------------------- Nixie

private val NixieOrange = Color(0xFFFF7A1E)
private val NixieCore = Color(0xFFFFC08A)

private class NixieLayouts(val digits: List<TextLayoutResult>, val tubeW: Float, val tubeH: Float, val positions: List<Offset>, val separators: List<Offset>)

@Composable
fun NixieSaver(ctx: SaverContext) {
    val measurer = rememberTextMeasurer()
    val (tens, ones) = hourDigits(ctx)
    val t = ctx.now.toLocalTime()
    val values = listOf(tens ?: 0, ones, t.minute / 10, t.minute % 10)
    val lit = listOf(tens != null, true, true, true)
    // Each tube fades its new digit in with a brief flicker, like warming cathodes.
    val fades = List(4) { remember { Animatable(1f) } }
    values.forEachIndexed { i, v ->
        LaunchedEffect(v) {
            fades[i].snapTo(0f)
            fades[i].animateTo(1f, tween(600))
        }
    }
    val previous = remember { IntArray(4) { -1 } }
    val shown = remember { IntArray(4) { -1 } }
    val density = LocalDensity.current
    val cache = remember {
        SizeCache { s ->
            val landscape = s.width > s.height
            val tubeW = if (landscape) minOf(s.width * 0.15f, s.height * 0.36f) else s.width * 0.3f
            val tubeH = tubeW * 1.7f
            val gap = tubeW * 0.12f
            val sep = tubeW * 0.36f
            val positions: List<Offset>
            val seps: List<Offset>
            if (landscape) {
                val total = tubeW * 4 + gap * 2 + sep
                val x0 = (s.width - total) / 2
                val y = (s.height - tubeH) / 2 - tubeH * 0.06f
                positions = listOf(x0, x0 + tubeW + gap, x0 + tubeW * 2 + gap + sep, x0 + tubeW * 3 + gap * 2 + sep).map { Offset(it, y) }
                seps = listOf(Offset(x0 + tubeW * 2 + gap + sep / 2 - gap / 2, y + tubeH * 0.62f))
            } else {
                val total = tubeW * 2 + gap
                val x0 = (s.width - total) / 2
                val rowGap = tubeH * 0.2f
                val y0 = (s.height - tubeH * 2 - rowGap) / 2 - tubeH * 0.05f
                positions = listOf(Offset(x0, y0), Offset(x0 + tubeW + gap, y0), Offset(x0, y0 + tubeH + rowGap), Offset(x0 + tubeW + gap, y0 + tubeH + rowGap))
                seps = emptyList()
            }
            val style = TextStyle(fontSize = with(density) { (tubeH * 0.6f).toSp() }, fontWeight = FontWeight.ExtraLight, color = NixieCore)
            NixieLayouts((0..9).map { measurer.measure("$it", style) }, tubeW, tubeH, positions, seps)
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val l = cache.get(size)
        // Warm light spilling onto the surface below the tubes.
        val floor = l.positions.maxOf { it.y } + l.tubeH
        drawOval(
            Brush.radialGradient(listOf(NixieOrange.copy(alpha = 0.07f), Color.Transparent), Offset(size.width / 2, floor), size.width * 0.5f),
            Offset(0f, floor - size.height * 0.08f), Size(size.width, size.height * 0.2f),
        )
        l.positions.forEachIndexed { i, p ->
            if (shown[i] != values[i]) {
                previous[i] = shown[i]
                shown[i] = values[i]
            }
            tube(l, p, values[i], previous[i], fades[i].value, lit[i])
        }
        l.separators.forEach { s ->
            listOf(-l.tubeH * 0.16f, l.tubeH * 0.04f).forEach { dy ->
                val o = s + Offset(0f, dy)
                drawCircle(Brush.radialGradient(listOf(NixieOrange.copy(alpha = 0.45f), Color.Transparent), o, l.tubeW * 0.12f), l.tubeW * 0.12f, o, blendMode = BlendMode.Plus)
                drawCircle(Color(0xFFFFB27A), l.tubeW * 0.03f, o)
            }
        }
    }
}

private fun DrawScope.tube(l: NixieLayouts, p: Offset, digit: Int, previous: Int, fade: Float, lit: Boolean) {
    val w = l.tubeW
    val h = l.tubeH
    val glass = RoundRect(p.x, p.y, p.x + w, p.y + h * 0.9f, topLeftCornerRadius = CornerRadius(w * 0.48f), topRightCornerRadius = CornerRadius(w * 0.48f), bottomLeftCornerRadius = CornerRadius(w * 0.08f), bottomRightCornerRadius = CornerRadius(w * 0.08f))
    val shape = Path().apply { addRoundRect(glass) }
    drawPath(shape, Brush.verticalGradient(listOf(Color(0xFF15100B), Color(0xFF0B0806)), p.y, p.y + h))
    clipPath(shape) {
        // Anode mesh.
        val step = w * 0.07f
        var k = -h
        while (k < w + h) {
            drawLine(Color(0xFFB08050).copy(alpha = 0.05f), Offset(p.x + k, p.y), Offset(p.x + k + h, p.y + h), strokeWidth = 1f)
            drawLine(Color(0xFFB08050).copy(alpha = 0.05f), Offset(p.x + k + h, p.y), Offset(p.x + k, p.y + h), strokeWidth = 1f)
            k += step
        }
        // The stacked cathodes behind, faintly visible.
        val center = Offset(p.x + w / 2, p.y + h * 0.47f)
        for (d in 0..9) {
            if (lit && d == digit && fade > 0.99f) continue
            val layout = l.digits[d]
            val o = Offset(center.x - layout.size.width / 2f + (d % 3 - 1) * w * 0.01f, center.y - layout.size.height / 2f)
            drawText(layout, color = Color(0xFF3B2C1F), topLeft = o, alpha = 0.22f)
        }
        if (lit) {
            val flicker = if (fade < 1f) (0.6f + 0.4f * sin(fade * 40f)).coerceIn(0f, 1f) * fade else 1f
            drawCircle(Brush.radialGradient(listOf(NixieOrange.copy(alpha = 0.22f * flicker), Color.Transparent), center, w * 0.6f), w * 0.6f, center)
            if (previous >= 0 && fade < 1f) glowDigit(l.digits[previous], center, w, 1f - fade)
            glowDigit(l.digits[digit], center, w, flicker)
        }
        // Curved glass catching the light.
        drawRect(Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.08f), 0.18f to Color.White.copy(alpha = 0.01f), 0.8f to Color.Transparent, 0.95f to Color.White.copy(alpha = 0.04f), startX = p.x, endX = p.x + w), p, Size(w, h))
    }
    drawPath(shape, Color(0xFF6A5238).copy(alpha = 0.35f), style = Stroke(1.2f * density))
    // Base and pins.
    val base = Rect(p.x + w * 0.04f, p.y + h * 0.9f, p.x + w * 0.96f, p.y + h)
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF1E1A16), Color(0xFF0A0908)), base.top, base.bottom), base.topLeft, base.size, CornerRadius(w * 0.05f))
    drawLine(Color.White.copy(alpha = 0.06f), Offset(base.left, base.top + 1f), Offset(base.right, base.top + 1f))
}

private fun DrawScope.glowDigit(layout: TextLayoutResult, center: Offset, w: Float, alpha: Float) {
    val o = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
    drawText(layout, color = NixieOrange, topLeft = o, alpha = alpha * 0.9f, shadow = Shadow(NixieOrange, blurRadius = w * 0.22f), blendMode = BlendMode.Plus)
    drawText(layout, color = NixieCore, topLeft = o, alpha = alpha, shadow = Shadow(Color(0xFFFF9A40), blurRadius = w * 0.05f))
}

// ---------------------------------------------------------------- Rings

@Composable
fun RingsClockSaver(ctx: SaverContext) {
    val frame = rememberFrameTime()
    val accent = ctx.accent
    val hourColor = accent
    val minuteColor = lerp(accent, Color(0xFF7FE3B8), 0.55f)
    val secondColor = lerp(accent, Color(0xFFFF9AB0), 0.6f)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val unit = minOf(maxWidth, maxHeight)
        val timeSize = with(LocalDensity.current) { (unit * 0.17f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            frame.value
            val now = LocalTime.now()
            val sec = now.second + now.nano / 1e9f
            val min = now.minute + sec / 60f
            val hr = (if (ctx.use24h) now.hour else now.hour % 12) + min / 60f
            val c = center
            val base = size.minDimension * 0.4f
            val stroke = size.minDimension * 0.03f
            ring(c, base, stroke, sec / 60f, secondColor)
            ring(c, base - stroke * 1.5f, stroke, min / 60f, minuteColor)
            ring(c, base - stroke * 3f, stroke, hr / if (ctx.use24h) 24f else 12f, hourColor)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val t = ctx.now.toLocalTime()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatClock(t, ctx.use24h), color = Color.White, fontSize = timeSize, lineHeight = timeSize, fontWeight = FontWeight.Thin, style = TabularNumbers)
                if (!ctx.use24h) Text(amPm(t), color = accent, fontSize = timeSize * 0.2f, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            }
            Text(formatShortDate(ctx.now.toLocalDate()), color = Color.White.copy(alpha = 0.55f), fontSize = timeSize * 0.16f)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { StatusLine(ctx, timeSize * 0.09f) }
    }
}

private fun DrawScope.ring(c: Offset, r: Float, stroke: Float, progress: Float, color: Color) {
    val tl = Offset(c.x - r, c.y - r)
    val sz = Size(r * 2, r * 2)
    drawCircle(color.copy(alpha = 0.1f), r, c, style = Stroke(stroke))
    val sweep = 360f * progress.coerceIn(0f, 1f)
    withTransform({ rotate(-90f, c) }) {
        drawArc(
            Brush.sweepGradient(0f to color.copy(alpha = 0.25f), (progress.coerceIn(0.001f, 1f)) to color, 1f to color.copy(alpha = 0.25f), center = c),
            0f, sweep, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round),
        )
    }
    val head = Offset(c.x + cos((sweep - 90f) * PI.toFloat() / 180f) * r, c.y + sin((sweep - 90f) * PI.toFloat() / 180f) * r)
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.45f), Color.Transparent), head, stroke * 1.6f), stroke * 1.6f, head, blendMode = BlendMode.Plus)
    drawCircle(Color.White.copy(alpha = 0.9f), stroke * 0.22f, head)
}

// ---------------------------------------------------------------- Day Arc (sun and moon)

@Composable
fun DayArcSaver(ctx: SaverContext) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val loc = ctx.settings.weatherLocation
    val (lat, lon) = remember(loc) { loc?.let { it.latitude to it.longitude } ?: Astronomy.guessLocation(zone) }
    val today = ctx.now.toLocalDate()
    val sun = remember(today, lat, lon) {
        Triple(
            Astronomy.sunTimes(today.minusDays(1), lat, lon, zone),
            Astronomy.sunTimes(today, lat, lon, zone),
            Astronomy.sunTimes(today.plusDays(1), lat, lon, zone),
        )
    }
    val now = ctx.now
    // Work out which arc we're on: today's daylight, or the night between two days.
    val (yesterday, td, tomorrow) = sun
    val rise = td.sunrise
    val set = td.sunset
    val isDay = rise != null && set != null && !now.isBefore(rise) && now.isBefore(set) || (rise == null && td.polarDay)
    val (start, end) = when {
        rise == null || set == null -> null to null
        isDay -> rise to set
        now.isBefore(rise) -> (yesterday.sunset ?: rise.minusHours(12)) to rise
        else -> set to (tomorrow.sunrise ?: set.plusHours(12))
    }
    val progress = if (start != null && end != null) {
        (Duration.between(start, now).seconds.toFloat() / Duration.between(start, end).seconds.coerceAtLeast(1)).coerceIn(0f, 1f)
    } else 0.5f
    val phase = remember(today) { Astronomy.moonPhase(java.time.Instant.now()) }
    val stars = remember { StarSky(140, 71, 0f, 0.6f) }
    val measurer = rememberTextMeasurer()
    val accent = ctx.accent
    val untilLabel = when {
        end == null -> null
        isDay -> stringResource(R.string.dayarc_sunset_in, formatDuration(context, Duration.between(now, end).toMillis()))
        else -> stringResource(R.string.dayarc_sunrise_in, formatDuration(context, Duration.between(now, end).toMillis()))
    }
    val startLabel = start?.let { stringResource(if (isDay) R.string.dayarc_sunrise else R.string.dayarc_sunset) + "  " + formatTimeWithAmPm(it.toLocalTime(), ctx.use24h) }
    val endLabel = end?.let { stringResource(if (isDay) R.string.dayarc_sunset else R.string.dayarc_sunrise) + "  " + formatTimeWithAmPm(it.toLocalTime(), ctx.use24h) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val timeSize = with(LocalDensity.current) { (if (landscape) maxHeight * 0.2f else maxWidth * 0.2f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val horizon = h * if (landscape) 0.72f else 0.66f
            // Twilight colors near the ends of the arc, deep blue at night, soft blue by day.
            val edge = 1f - (abs(progress - 0.5f) * 2f)
            val skyTop = if (isDay) lerp(Color(0xFF1A2F5C), Color(0xFF214A86), edge) else Color(0xFF02040C)
            val skyLow = if (isDay) lerp(Color(0xFF8A4A52), Color(0xFF3F74B0), edge) else lerp(Color(0xFF1C1838), Color(0xFF071028), edge)
            drawRect(Brush.verticalGradient(listOf(skyTop, skyLow), 0f, horizon), size = Size(w, horizon))
            if (!isDay) stars.draw(this, ctx.now.second.toFloat(), 0.8f, horizonFade = 0.5f)
            drawRect(Brush.verticalGradient(listOf(Color(0xFF05070D), Color.Black), horizon, h), Offset(0f, horizon), Size(w, h - horizon))
            drawLine(Color.White.copy(alpha = 0.18f), Offset(w * 0.04f, horizon), Offset(w * 0.96f, horizon), strokeWidth = density)

            val left = w * 0.1f
            val right = w * 0.9f
            val rx = (right - left) / 2
            val ry = horizon * if (landscape) 0.38f else 0.36f
            val cx = (left + right) / 2
            val rect = Rect(cx - rx, horizon - ry, cx + rx, horizon + ry)
            // The full path, dotted; the part already travelled, solid.
            val dots = 48
            for (k in 0..dots) {
                val a = PI.toFloat() * (1f - k / dots.toFloat())
                drawCircle(Color.White.copy(alpha = 0.18f), density * 1.2f, Offset(cx + cos(a) * rx, horizon - sin(a) * ry))
            }
            val bodyColor = if (isDay) Color(0xFFFFD27A) else Color(0xFFE9EEF8)
            drawArc(
                Brush.horizontalGradient(listOf(bodyColor.copy(alpha = 0.05f), bodyColor.copy(alpha = 0.6f)), rect.left, cx - rx + 2 * rx * progress),
                180f, 180f * progress, false, rect.topLeft, rect.size, style = Stroke(density * 2f, cap = StrokeCap.Round),
            )
            val a = PI.toFloat() * (1f - progress)
            val body = Offset(cx + cos(a) * rx, horizon - sin(a) * ry)
            val br = size.minDimension * 0.035f
            drawCircle(Brush.radialGradient(listOf(bodyColor.copy(alpha = 0.35f), Color.Transparent), body, br * 5f), br * 5f, body)
            if (isDay) {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFBEA), Color(0xFFFFC85A)), body, br), br, body)
            } else {
                drawMoonDisc(body, br, phase, lat < 0)
            }
            fun label(text: String, x: Float, alignRight: Boolean) {
                val l = measurer.measure(text, TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = (size.minDimension * 0.032f).toSp()))
                drawText(l, topLeft = Offset(if (alignRight) x - l.size.width else x, horizon + size.minDimension * 0.03f))
            }
            startLabel?.let { label(it, left - rx * 0.05f, false) }
            endLabel?.let { label(it, right + rx * 0.05f, true) }
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = if (landscape) 24.dp else 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val t = ctx.now.toLocalTime()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatClock(t, ctx.use24h), color = Color.White, fontSize = timeSize, lineHeight = timeSize, fontWeight = FontWeight.Thin, style = TabularNumbers)
                if (!ctx.use24h) Text(amPm(t), color = accent, fontSize = timeSize * 0.2f, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            }
            Text(formatLongDate(today), color = Color.White.copy(alpha = 0.6f), fontSize = timeSize * 0.15f)
        }
        untilLabel?.let {
            Text(
                it, color = Color.White.copy(alpha = 0.75f), fontSize = timeSize * 0.15f,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (landscape) 18.dp else 64.dp),
            )
        }
    }
}

/** A small moon with the right phase, lit from the correct side for the hemisphere. */
fun DrawScope.drawMoonDisc(c: Offset, r: Float, phase: Double, southern: Boolean, litColor: Color = Color(0xFFF2EEE4)) {
    drawCircle(Color(0xFF2A2D36), r, c)
    val lit = moonLitPath(c, r, phase, southern)
    drawPath(lit, Brush.radialGradient(listOf(litColor, lerp(litColor, Color(0xFF9A968C), 0.4f)), c - Offset(r * 0.2f, r * 0.2f), r * 1.4f))
}

/** The lit part of the moon's disc for [phase] (0 new … 0.5 full … 1 new). */
fun moonLitPath(c: Offset, r: Float, phase: Double, southern: Boolean): Path {
    val waxing = phase < 0.5
    // How far the terminator bulges: r at new/full, 0 at the quarters.
    val k = cos(2 * PI * phase).toFloat()
    val a = abs(k) * r
    val crescent = if (waxing) phase < 0.25 else phase > 0.75
    val rect = Rect(c.x - r, c.y - r, c.x + r, c.y + r)
    val ell = Rect(c.x - a, c.y - r, c.x + a, c.y + r)
    val path = Path()
    // Built for a lit right side, then mirrored when needed.
    path.arcTo(rect, -90f, 180f, false)
    path.arcTo(ell, 90f, if (crescent) -180f else 180f, false)
    path.close()
    val mirror = waxing == southern
    if (mirror) {
        val m = androidx.compose.ui.graphics.Matrix()
        m.translate(c.x, 0f)
        m.scale(-1f, 1f)
        m.translate(-c.x, 0f)
        path.transform(m)
    }
    return path
}

// ---------------------------------------------------------------- Odometer

@Composable
fun OdometerSaver(ctx: SaverContext) {
    val measurer = rememberTextMeasurer()
    val t = ctx.now.toLocalTime()
    val h = if (ctx.use24h) t.hour else hour12(t.hour)
    val digits = intArrayOf(h / 10, h % 10, t.minute / 10, t.minute % 10, t.second / 10, t.second % 10)
    val drums = List(6) { i -> remember { Animatable(digits[i].toFloat()) } }
    val counts = remember { IntArray(6) { digits[it] } }
    digits.forEachIndexed { i, d ->
        LaunchedEffect(d) {
            val current = ((counts[i] % 10) + 10) % 10
            if (current != d) {
                counts[i] += (d - current + 10) % 10
                drums[i].animateTo(counts[i].toFloat(), spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
            }
        }
    }
    val accent = ctx.accent
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val small = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.04f).toSp() }
        val unitDp = if (landscape) maxHeight * 0.36f else maxWidth * 0.235f
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.fillMaxWidth().height(unitDp * 1.1f)) {
                val unit = unitDp.toPx()
                val dw = unit * 0.66f
                val dh = unit
                val gap = dw * 0.08f
                val sep = dw * 0.36f
                val secScale = 0.62f
                val total = dw * 4 + gap * 2 + sep * 2 + dw * secScale * 2 + gap
                var x = (size.width - total) / 2
                val y = (size.height - dh) / 2
                val style = TextStyle(fontSize = (dh * 0.62f).toSp(), fontWeight = FontWeight.Light, color = Color(0xFFEDEDEF), fontFeatureSettings = "tnum")
                val secStyle = style.copy(fontSize = (dh * secScale * 0.62f).toSp(), color = accent)
                for (i in 0 until 6) {
                    val sec = i >= 4
                    val w = if (sec) dw * secScale else dw
                    val hh = if (sec) dh * secScale else dh
                    drum(measurer, if (sec) secStyle else style, Offset(x, y + (dh - hh)), w, hh, drums[i].value)
                    x += w + if (i == 1 || i == 3) sep else gap
                    if (i == 1 || i == 3) {
                        val cxp = x - sep / 2 - gap / 2
                        drawCircle(Color.White.copy(alpha = 0.35f), dw * 0.045f, Offset(cxp, y + dh * 0.36f))
                        drawCircle(Color.White.copy(alpha = 0.35f), dw * 0.045f, Offset(cxp, y + dh * 0.64f))
                    }
                }
            }
            Spacer(Modifier.height(unitDp * 0.3f))
            Text(
                listOfNotNull(formatLongDate(ctx.now.toLocalDate()), if (ctx.use24h) null else amPm(t)).joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.6f), fontSize = small,
            )
            Spacer(Modifier.height(6.dp))
            StatusLine(ctx, small * 0.8f)
        }
    }
}

private fun DrawScope.drum(measurer: TextMeasurer, style: TextStyle, p: Offset, w: Float, h: Float, value: Float) {
    val rr = RoundRect(p.x, p.y, p.x + w, p.y + h, CornerRadius(w * 0.12f))
    val shape = Path().apply { addRoundRect(rr) }
    drawPath(shape, Brush.verticalGradient(0f to Color(0xFF050506), 0.5f to Color(0xFF1C1C1F), 1f to Color(0xFF050506), startY = p.y, endY = p.y + h))
    clipPath(shape) {
        val line = h * 0.82f
        val base = floor(value).toInt()
        val cy = p.y + h / 2
        for (k in base - 1..base + 2) {
            val off = k - value
            if (abs(off) > 1.2f) continue
            val digit = ((k % 10) + 10) % 10
            val l = measurer.measure("$digit", style)
            val yy = cy + off * line
            // Squash digits as they roll over the top or bottom of the cylinder.
            val squash = cos(off.coerceIn(-1.5f, 1.5f) * 1.05f).coerceAtLeast(0.05f)
            withTransform({ scale(1f, squash, Offset(p.x + w / 2, yy)) }) {
                drawText(l, topLeft = Offset(p.x + (w - l.size.width) / 2f, yy - l.size.height / 2f), alpha = (1f - abs(off) * 0.7f).coerceIn(0f, 1f))
            }
        }
        drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.85f), 0.3f to Color.Transparent, 0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f), startY = p.y, endY = p.y + h), p, Size(w, h))
        drawLine(Color.White.copy(alpha = 0.05f), Offset(p.x, p.y + h * 0.36f), Offset(p.x + w, p.y + h * 0.36f))
    }
    drawPath(shape, Color.White.copy(alpha = 0.08f), style = Stroke(density))
}

// ---------------------------------------------------------------- Big Type

@Composable
fun BigTypeSaver(ctx: SaverContext) {
    val t = ctx.now.toLocalTime()
    val h = if (ctx.use24h) "%02d".format(t.hour) else "${hour12(t.hour)}"
    val m = "%02d".format(t.minute)
    val day = ctx.now.toLocalDate()
    val weekday = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
    val dateLine = formatShortDate(day).uppercase()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val density = LocalDensity.current
        val boxH = maxHeight
        if (landscape) {
            val big = with(density) { (maxHeight * 0.62f).toSp() }
            Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Text(h, color = Color.White, fontSize = big, lineHeight = big, fontWeight = FontWeight.Black, letterSpacing = (-0.04).em, style = TabularNumbers)
                Spacer(Modifier.width(boxH * 0.04f))
                Box(Modifier.width(2.dp).height(boxH * 0.38f).background(ctx.accent.copy(alpha = 0.7f)))
                Spacer(Modifier.width(boxH * 0.04f))
                Text(m, color = ctx.accent, fontSize = big, lineHeight = big, fontWeight = FontWeight.Thin, letterSpacing = (-0.03).em, style = TabularNumbers)
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(weekday.uppercase(), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.em)
                Text("   ·   $dateLine" + if (ctx.use24h) "" else "   ·   ${amPm(t)}", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, letterSpacing = 0.2.em)
            }
        } else {
            val big = with(density) { (maxWidth * 0.62f).toSp() }
            Column(Modifier.align(Alignment.CenterStart).padding(start = maxWidth * 0.08f), verticalArrangement = Arrangement.spacedBy((-28).dp)) {
                Text(weekday.uppercase(), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.em, modifier = Modifier.padding(start = 8.dp, bottom = 36.dp))
                Text(h, color = Color.White, fontSize = big, lineHeight = big, fontWeight = FontWeight.Black, letterSpacing = (-0.05).em, style = TabularNumbers)
                Text(m, color = ctx.accent, fontSize = big, lineHeight = big, fontWeight = FontWeight.Thin, letterSpacing = (-0.04).em, style = TabularNumbers)
                Text(
                    dateLine + if (ctx.use24h) "" else "  ·  ${amPm(t)}", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, letterSpacing = 0.2.em,
                    modifier = Modifier.padding(start = 8.dp, top = 36.dp),
                )
            }
        }
    }
}
