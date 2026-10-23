package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.StatusLine
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatLongDate
import dev.pridgey.beddery.savers.formatShortDate
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.hour12
import dev.pridgey.beddery.savers.polar
import dev.pridgey.beddery.savers.rememberFrameTime
import java.time.LocalTime

// ---------------------------------------------------------------- Minimal

@Composable
fun MinimalClockSaver(ctx: SaverContext) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val timeSize = with(LocalDensity.current) { (if (landscape) maxHeight * 0.40f else maxWidth * 0.28f).toSp() }
        val t = ctx.now.toLocalTime()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatClock(t, ctx.use24h),
                    fontSize = timeSize, lineHeight = timeSize, fontWeight = FontWeight.Thin,
                    color = Color.White, letterSpacing = (-0.03).em, style = TabularNumbers,
                )
                if (!ctx.use24h) {
                    Text(
                        amPm(t), fontSize = timeSize * 0.16f, color = ctx.accent,
                        modifier = Modifier.padding(start = 6.dp, bottom = with(LocalDensity.current) { (timeSize * 0.18f).toDp() }),
                    )
                }
            }
            Text(formatLongDate(ctx.now.toLocalDate()), fontSize = timeSize * 0.12f, color = ctx.accent, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(18.dp))
            StatusLine(ctx, fontSize = timeSize * 0.075f)
            // A small surprise for anyone awake at 11:11.
            androidx.compose.animation.AnimatedVisibility(t.hour % 12 == 11 && t.minute == 11, enter = androidx.compose.animation.fadeIn(tween(2000)), exit = androidx.compose.animation.fadeOut(tween(2000))) {
                Text(
                    androidx.compose.ui.res.stringResource(dev.pridgey.beddery.R.string.easter_wish), fontSize = timeSize * 0.07f,
                    color = ctx.accent.copy(alpha = 0.8f), fontWeight = FontWeight.Light, modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Nightlight

private val NightRed = Color(0xFFB3261E)

@Composable
fun NightlightSaver(ctx: SaverContext) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val timeSize = with(LocalDensity.current) { (if (landscape) maxHeight * 0.45f else maxWidth * 0.3f).toSp() }
        val t = ctx.now.toLocalTime()
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatClock(t, ctx.use24h), fontSize = timeSize, lineHeight = timeSize,
                fontWeight = FontWeight.ExtraLight, color = NightRed.copy(alpha = 0.7f), style = TabularNumbers,
            )
            ctx.nextAlarm?.let {
                Text(
                    "⏰ " + formatTimeWithAmPm(it.toLocalTime(), ctx.use24h),
                    fontSize = timeSize * 0.09f, color = NightRed.copy(alpha = 0.45f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Analog

@Composable
fun AnalogClockSaver(ctx: SaverContext) {
    val frame = rememberFrameTime()
    val measurer = rememberTextMeasurer()
    val accent = ctx.accent
    val dateLabel = formatShortDate(ctx.now.toLocalDate()).uppercase()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val statusSize = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.035f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            frame.value // redraw every frame for the sweeping second hand
            val now = LocalTime.now()
            val sec = now.second + now.nano / 1e9f
            val min = now.minute + sec / 60f
            val hr = (now.hour % 12) + min / 60f
            val statusRoom = 56.dp.toPx()
            val c = Offset(size.width / 2, (size.height - statusRoom) / 2 + 8.dp.toPx())
            val r = minOf(size.width, size.height - statusRoom) * 0.40f

            drawCircle(Brush.radialGradient(listOf(Color(0xFF1A1D24), Color.Black), c, r * 1.25f), r * 1.25f, c)
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val inner = r - if (major) r * 0.09f else r * 0.035f
                drawLine(
                    color = if (major) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f),
                    start = polar(c, inner, i * 6f), end = polar(c, r, i * 6f),
                    strokeWidth = if (major) r * 0.014f else r * 0.006f, cap = StrokeCap.Round,
                )
            }
            val label = measurer.measure(dateLabel, TextStyle(color = accent, fontSize = (r * 0.065f).toSp(), fontWeight = FontWeight.Medium))
            drawText(label, topLeft = Offset(c.x + r * 0.42f, c.y - label.size.height / 2f))

            hand(c, hr * 30f, r * 0.52f, r * 0.04f, Color.White)
            hand(c, min * 6f, r * 0.78f, r * 0.026f, Color.White)
            drawLine(accent, polar(c, -r * 0.16f, sec * 6f), polar(c, r * 0.88f, sec * 6f), r * 0.009f, StrokeCap.Round)
            drawCircle(accent, r * 0.03f, c)
            drawCircle(Color.Black, r * 0.012f, c)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) { StatusLine(ctx, statusSize) }
    }
}

private fun DrawScope.hand(c: Offset, degrees: Float, length: Float, width: Float, color: Color) {
    drawLine(Color.Black.copy(alpha = 0.5f), polar(c, -length * 0.12f, degrees) + Offset(0f, width), polar(c, length, degrees) + Offset(0f, width), width, StrokeCap.Round)
    drawLine(color, polar(c, -length * 0.12f, degrees), polar(c, length, degrees), width, StrokeCap.Round)
}

// ---------------------------------------------------------------- Flip

private val FlipCard = Color(0xFF1E1F23)
private val FlipText = Color(0xFFE9E9EC)

@Composable
fun FlipClockSaver(ctx: SaverContext) {
    val t = ctx.now.toLocalTime()
    val h = if (ctx.use24h) t.hour else hour12(t.hour)
    val hourText = if (ctx.use24h) "%02d".format(h) else "%2d".format(h)
    val minuteText = "%02d".format(t.minute)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val landscape = maxWidth > maxHeight
        val cardW = if (landscape) min(maxWidth * 0.19f, maxHeight * 0.45f) else maxWidth * 0.36f
        val cardH = cardW * 1.4f
        val gap = cardW * 0.08f
        val fontSize = with(LocalDensity.current) { (cardH * 0.78f).toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (landscape) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    FlipPair(hourText, cardW, cardH, fontSize, gap)
                    Spacer(Modifier.width(gap))
                    FlipPair(minuteText, cardW, cardH, fontSize, gap)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap * 1.5f)) {
                    FlipPair(hourText, cardW, cardH, fontSize, gap)
                    FlipPair(minuteText, cardW, cardH, fontSize, gap)
                }
            }
            Spacer(Modifier.height(24.dp))
            val small = with(LocalDensity.current) { (cardW * 0.11f).toSp() }
            Text(
                listOfNotNull(formatLongDate(ctx.now.toLocalDate()), if (ctx.use24h) null else amPm(t)).joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.7f), fontSize = small,
            )
            Spacer(Modifier.height(8.dp))
            StatusLine(ctx, small * 0.8f)
        }
    }
}

@Composable
private fun FlipPair(text: String, w: Dp, h: Dp, fontSize: TextUnit, gap: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap * 0.5f)) {
        text.forEach { FlipDigit(it, w, h, fontSize) }
    }
}

@Composable
private fun FlipDigit(char: Char, w: Dp, h: Dp, fontSize: TextUnit) {
    var current by remember { mutableStateOf(char) }
    var previous by remember { mutableStateOf(char) }
    val rotation = remember { Animatable(180f) }
    LaunchedEffect(char) {
        if (char != current) {
            previous = current
            current = char
            rotation.snapTo(0f)
            rotation.animateTo(180f, tween(700, easing = FastOutSlowInEasing))
        }
    }
    val rot = rotation.value
    val seam = h * 0.015f
    Box(Modifier.size(w, h)) {
        Column(verticalArrangement = Arrangement.spacedBy(seam)) {
            FlipHalf(current, top = true, w, h, fontSize)
            FlipHalf(if (rot < 90f) previous else current, top = false, w, h, fontSize)
        }
        if (rot < 90f) {
            FlipHalf(
                previous, top = true, w, h, fontSize,
                Modifier.align(Alignment.TopCenter).graphicsLayer {
                    rotationX = -rot
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    cameraDistance = 16f * density
                },
            )
        } else if (rot < 180f) {
            FlipHalf(
                current, top = false, w, h, fontSize,
                Modifier.align(Alignment.BottomCenter).graphicsLayer {
                    rotationX = 180f - rot
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    cameraDistance = 16f * density
                },
            )
        }
    }
}

@Composable
private fun FlipHalf(char: Char, top: Boolean, w: Dp, h: Dp, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val radius = w * 0.1f
    val shape = if (top) RoundedCornerShape(topStart = radius, topEnd = radius) else RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    val halfH = h / 2 - h * 0.0075f
    Box(
        modifier
            .size(w, halfH)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (top) listOf(Color(0xFF26272C), FlipCard) else listOf(Color(0xFF1A1B1F), Color(0xFF151619)),
                ),
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = if (top) Alignment.Top else Alignment.Bottom, unbounded = true)
                .height(h),
            contentAlignment = Alignment.Center,
        ) {
            Text(char.toString(), color = FlipText, fontSize = fontSize, lineHeight = fontSize, fontWeight = FontWeight.Medium, style = TabularNumbers)
        }
    }
}

// ---------------------------------------------------------------- Word clock

object WordClock {
    val grid = listOf(
        "ITLISASAMPM",
        "ACQUARTERDC",
        "TWENTYFIVEX",
        "HALFSTENFTO",
        "PASTERUNINE",
        "ONESIXTHREE",
        "FOURFIVETWO",
        "EIGHTELEVEN",
        "SEVENTWELVE",
        "TENSEOCLOCK",
    )

    data class Word(val row: Int, val col: Int, val length: Int)

    private val IT = Word(0, 0, 2)
    private val IS = Word(0, 3, 2)
    private val AM = Word(0, 7, 2)
    private val PM = Word(0, 9, 2)
    private val A = Word(1, 0, 1)
    private val QUARTER = Word(1, 2, 7)
    private val TWENTY = Word(2, 0, 6)
    private val FIVE_M = Word(2, 6, 4)
    private val HALF = Word(3, 0, 4)
    private val TEN_M = Word(3, 5, 3)
    private val TO = Word(3, 9, 2)
    private val PAST = Word(4, 0, 4)
    private val OCLOCK = Word(9, 5, 6)
    private val HOURS = listOf(
        Word(8, 5, 6), // twelve
        Word(5, 0, 3), // one
        Word(6, 8, 3), // two
        Word(5, 6, 5), // three
        Word(6, 0, 4), // four
        Word(6, 4, 4), // five
        Word(5, 3, 3), // six
        Word(8, 0, 5), // seven
        Word(7, 0, 5), // eight
        Word(4, 7, 4), // nine
        Word(9, 0, 3), // ten
        Word(7, 5, 6), // eleven
    )

    fun words(hour24: Int, minute: Int): Set<Word> {
        val five = minute / 5 * 5
        val result = mutableSetOf(IT, IS, if (hour24 < 12) AM else PM)
        when (five) {
            0 -> result += OCLOCK
            5, 55 -> result += FIVE_M
            10, 50 -> result += TEN_M
            15, 45 -> result += listOf(A, QUARTER)
            20, 40 -> result += TWENTY
            25, 35 -> result += listOf(TWENTY, FIVE_M)
            30 -> result += HALF
        }
        if (five != 0) result += if (five <= 30) PAST else TO
        val hour = if (five > 30) hour24 + 1 else hour24
        result += HOURS[hour % 12]
        return result
    }

    fun litCells(hour24: Int, minute: Int): Set<Pair<Int, Int>> =
        words(hour24, minute).flatMap { w -> (w.col until w.col + w.length).map { w.row to it } }.toSet()
}

@Composable
fun WordClockSaver(ctx: SaverContext) {
    val t = ctx.now.toLocalTime()
    val lit = remember(t.hour, t.minute) { WordClock.litCells(t.hour, t.minute) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cell = min(maxWidth * 0.86f / 11, maxHeight * 0.78f / 11.5f)
        val fontSize = with(LocalDensity.current) { (cell * 0.55f).toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            WordClock.grid.forEachIndexed { r, row ->
                Row {
                    row.forEachIndexed { c, ch ->
                        val on = (r to c) in lit
                        val color by animateColorAsState(
                            if (on) Color.White else Color.White.copy(alpha = 0.1f), tween(900), label = "letter",
                        )
                        Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                            Text(
                                ch.toString(), color = color, fontSize = fontSize,
                                fontWeight = if (on) FontWeight.Medium else FontWeight.Light,
                                style = if (on) TextStyle(shadow = androidx.compose.ui.graphics.Shadow(ctx.accent.copy(alpha = 0.8f), blurRadius = 18f)) else TextStyle.Default,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(cell * 0.4f))
            Row(horizontalArrangement = Arrangement.spacedBy(cell * 0.5f)) {
                repeat(4) { i ->
                    val color by animateColorAsState(
                        if (i < t.minute % 5) ctx.accent else Color.White.copy(alpha = 0.1f), tween(900), label = "dot",
                    )
                    Box(Modifier.size(cell * 0.18f).clip(CircleShape).background(color))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Binary

@Composable
fun BinaryClockSaver(ctx: SaverContext) {
    val t = ctx.now.toLocalTime()
    val h = if (ctx.use24h) t.hour else hour12(t.hour)
    val digits = listOf(h / 10, h % 10, t.minute / 10, t.minute % 10, t.second / 10, t.second % 10)
    val bits = listOf(2, 4, 3, 4, 3, 4)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val dot = min(maxWidth * 0.8f / 9f, maxHeight * 0.6f / 6f)
        val labelSize = with(LocalDensity.current) { (dot * 0.4f).toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(dot * 0.35f), verticalAlignment = Alignment.Bottom) {
                digits.forEachIndexed { i, value ->
                    if (i == 2 || i == 4) Spacer(Modifier.width(dot * 0.5f))
                    Column(verticalArrangement = Arrangement.spacedBy(dot * 0.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                        for (bit in 3 downTo 0) {
                            if (bit >= bits[i]) {
                                Spacer(Modifier.size(dot))
                            } else {
                                val on = (value shr bit) and 1 == 1
                                val color by animateColorAsState(if (on) ctx.accent else Color.White.copy(alpha = 0.08f), tween(350), label = "bit")
                                Box(
                                    Modifier
                                        .size(dot)
                                        .drawBehind {
                                            if (on) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent)), size.minDimension)
                                            drawCircle(color, size.minDimension * 0.42f)
                                        },
                                )
                            }
                        }
                        Text(value.toString(), color = Color.White.copy(alpha = 0.5f), fontSize = labelSize, style = TabularNumbers)
                    }
                }
            }
            Spacer(Modifier.height(dot * 0.8f))
            StatusLine(ctx, labelSize * 0.8f)
        }
    }
}

// ---------------------------------------------------------------- Bouncing clock

private val BouncePalette = listOf(
    Color(0xFF8CC8FF), Color(0xFFFF9AB0), Color(0xFFFFD36E), Color(0xFF7FE3B8),
    Color(0xFFBBA6FF), Color(0xFFFF9F6B), Color(0xFF6EE7F0),
)

private class Bouncer {
    var x = -1f
    var y = -1f
    var vx = 1f
    var vy = 1f
    var colorIndex = 0
    var flash = 0f
    var bounds = androidx.compose.ui.geometry.Size.Zero
    var block = androidx.compose.ui.geometry.Size.Zero
    /** Confetti from a perfect corner hit: x, y, vx, vy, age, color index. */
    val confetti = ArrayList<FloatArray>()
}

@Composable
fun BouncingClockSaver(ctx: SaverContext) {
    val measurer = rememberTextMeasurer()
    val state = remember { Bouncer() }
    val text = formatClock(ctx.now.toLocalTime(), ctx.use24h)
    val sub = if (ctx.use24h) formatShortDate(ctx.now.toLocalDate()) else "${amPm(ctx.now.toLocalTime())} · ${formatShortDate(ctx.now.toLocalDate())}"
    val frame = rememberFrameTime { dt, _ ->
        val s = state
        val bounds = s.bounds
        val block = s.block
        if (bounds.width <= 0f || block.width <= 0f) return@rememberFrameTime
        val speed = bounds.minDimension * 0.12f
        if (s.x < 0f) {
            s.x = (bounds.width - block.width) * 0.3f
            s.y = (bounds.height - block.height) * 0.6f
        }
        s.x += s.vx * speed * dt
        s.y += s.vy * speed * dt
        var hits = 0
        if (s.x <= 0f) { s.x = 0f; s.vx = 1f; hits++ }
        if (s.x + block.width >= bounds.width) { s.x = bounds.width - block.width; s.vx = -1f; hits++ }
        if (s.y <= 0f) { s.y = 0f; s.vy = 1f; hits++ }
        if (s.y + block.height >= bounds.height) { s.y = bounds.height - block.height; s.vy = -1f; hits++ }
        if (hits > 0) s.colorIndex = (s.colorIndex + 1) % BouncePalette.size
        if (hits > 1) {
            s.flash = 1f // a perfect corner hit!
            val cx = s.x + if (s.vx < 0) block.width else 0f
            val cy = s.y + if (s.vy < 0) block.height else 0f
            repeat(70) { k ->
                val a = kotlin.random.Random.nextFloat() * 6.283f
                val v = bounds.minDimension * (0.15f + kotlin.random.Random.nextFloat() * 0.5f)
                s.confetti += floatArrayOf(cx, cy, kotlin.math.cos(a) * v, kotlin.math.sin(a) * v - bounds.minDimension * 0.3f, 0f, (k % BouncePalette.size).toFloat())
            }
        }
        s.flash = (s.flash - dt * 0.5f).coerceAtLeast(0f)
        val it = s.confetti.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p[4] += dt
            p[3] += bounds.minDimension * 0.6f * dt
            p[0] += p[2] * dt
            p[1] += p[3] * dt
            if (p[4] > 3f) it.remove()
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        frame.value
        state.bounds = size
        val main = measurer.measure(text, TextStyle(fontSize = (size.minDimension * 0.16f).toSp(), fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"))
        val small = measurer.measure(sub, TextStyle(fontSize = (size.minDimension * 0.035f).toSp()))
        val block = androidx.compose.ui.geometry.Size(maxOf(main.size.width, small.size.width).toFloat(), (main.size.height + small.size.height).toFloat())
        state.block = block
        val color = BouncePalette[state.colorIndex]
        if (state.flash > 0f) drawRect(color.copy(alpha = state.flash * 0.25f))
        state.confetti.forEach { p ->
            val a = (1f - p[4] / 3f).coerceIn(0f, 1f)
            drawCircle(BouncePalette[p[5].toInt()].copy(alpha = a), size.minDimension * 0.006f, Offset(p[0], p[1]))
        }
        val x = state.x.coerceAtLeast(0f)
        val y = state.y.coerceAtLeast(0f)
        drawText(main, color = color, topLeft = Offset(x + (block.width - main.size.width) / 2f, y))
        drawText(small, color = color.copy(alpha = 0.7f), topLeft = Offset(x + (block.width - small.size.width) / 2f, y + main.size.height))
    }
}
