package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.Astronomy
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.MoonRender
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.smoothstep
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------- Sleep Coach

private val CoachBg = Color(0xFF131F24)
private val CoachCard = Color(0xFF1F2F36)
private val CoachBorder = Color(0xFF37464F)
private val CoachText = Color(0xFFF1F7FB)
private val CoachMuted = Color(0xFF8FA2AD)
private val Green = Color(0xFF58CC02)
private val GreenDark = Color(0xFF58A700)
private val Orange = Color(0xFFFF9600)
private val OrangeDark = Color(0xFFCC7800)
private val Red = Color(0xFFFF4B4B)
private val RedDark = Color(0xFFD33131)
private val Blue = Color(0xFF1CB0F6)

private enum class Mood(val lines: Int, val color: Color, val shade: Color) {
    THRILLED(R.array.coach_thrilled, Blue, Color(0xFF1899D6)),
    HAPPY(R.array.coach_happy, Green, GreenDark),
    MEH(R.array.coach_meh, Orange, OrangeDark),
    DISAPPOINTED(R.array.coach_disappointed, Orange, OrangeDark),
    PANIC(R.array.coach_panic, Red, RedDark),
    NO_ALARM(R.array.coach_no_alarm, CoachMuted, CoachBorder),
}

private fun moodFor(hours: Double?): Mood = when {
    hours == null -> Mood.NO_ALARM
    hours >= 9 -> Mood.THRILLED
    hours >= 7.5 -> Mood.HAPPY
    hours >= 6 -> Mood.MEH
    hours >= 4 -> Mood.DISAPPOINTED
    else -> Mood.PANIC
}

@Composable
fun SleepCoachSaver(ctx: SaverContext) {
    val sampleAlarm = remember { LocalDateTime.now().plusHours(5).plusMinutes(12) }
    val alarm = ctx.nextAlarm ?: if (ctx.demo) sampleAlarm else null
    val remaining = alarm?.let { Duration.between(ctx.now, it).coerceAtLeast(Duration.ZERO) }
    val hours = remaining?.let { it.toMinutes() / 60.0 }
    val mood = moodFor(hours)
    val lines = stringArrayResource(mood.lines)
    // A new jab every 20 seconds.
    val line = lines[((ctx.now.toLocalTime().toSecondOfDay() / 20) % lines.size)]
    val time = rememberFrameTime()
    BoxWithConstraints(Modifier.fillMaxSize().background(CoachBg)) {
        val landscape = maxWidth > maxHeight
        val unit = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        val body = with(density) { (unit * 0.05f).toSp() }
        val big = with(density) { (unit * 0.17f).toSp() }
        val sheep: @Composable (Dp) -> Unit = { s ->
            Canvas(Modifier.size(s)) { drawSheep(mood, time.value) }
        }
        val bubble: @Composable () -> Unit = {
            AnimatedContent(line, transitionSpec = { (fadeIn(tween(300)) + scaleIn(tween(300), 0.9f)) togetherWith fadeOut(tween(200)) }, label = "line") { text ->
                Box(
                    Modifier
                        .widthIn(max = 420.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(CoachBorder)
                        .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 5.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(CoachCard)
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text(text, color = CoachText, fontSize = body * 1.1f, fontWeight = FontWeight.ExtraBold, lineHeight = body * 1.45f)
                }
            }
        }
        val stats: @Composable () -> Unit = {
            Column(horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally) {
                if (remaining != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${remaining.toHours()}", color = mood.color, fontSize = big, lineHeight = big, fontWeight = FontWeight.Black, style = TabularNumbers)
                        Text(stringResource(R.string.unit_h), color = mood.color, fontSize = big * 0.45f, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 2.dp, end = 12.dp, bottom = 8.dp))
                        Text("${remaining.toMinutes() % 60}", color = mood.color, fontSize = big, lineHeight = big, fontWeight = FontWeight.Black, style = TabularNumbers)
                        Text(stringResource(R.string.unit_m), color = mood.color, fontSize = big * 0.45f, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
                    }
                    Text(
                        stringResource(R.string.coach_until, formatTimeWithAmPm(alarm.toLocalTime(), ctx.use24h)),
                        color = CoachMuted, fontSize = body * 0.85f, fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(unit * 0.05f))
                    // Progress towards a full night, Duolingo-style: chunky, with a shine on top.
                    val goal = ((hours ?: 0.0) / 8.0).toFloat().coerceIn(0f, 1f)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 420.dp)) {
                        Box(Modifier.weight(1f).height(unit * 0.045f).clip(RoundedCornerShape(50)).background(CoachBorder)) {
                            Box(Modifier.fillMaxWidth(goal.coerceAtLeast(0.06f)).height(unit * 0.045f).clip(RoundedCornerShape(50)).background(mood.color)) {
                                Box(
                                    Modifier
                                        .padding(start = unit * 0.02f, end = unit * 0.02f, top = unit * 0.01f)
                                        .fillMaxWidth()
                                        .height(unit * 0.01f)
                                        .clip(RoundedCornerShape(50))
                                        .background(Color.White.copy(alpha = 0.3f)),
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.coach_goal), color = CoachMuted, fontSize = body * 0.8f, fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Text(formatTimeWithAmPm(ctx.now.toLocalTime(), ctx.use24h), color = CoachText, fontSize = big * 0.8f, fontWeight = FontWeight.Black, style = TabularNumbers)
                    Text(stringResource(R.string.sleep_no_alarm), color = CoachMuted, fontSize = body, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    bubble()
                    Spacer(Modifier.height(12.dp))
                    sheep(unit * 0.5f)
                }
                stats()
            }
        } else {
            Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                bubble()
                Spacer(Modifier.height(10.dp))
                sheep(unit * 0.62f)
                Spacer(Modifier.height(unit * 0.04f))
                stats()
            }
        }
    }
}

/** The coach: a round, woolly sheep whose face says everything. */
private fun DrawScope.drawSheep(mood: Mood, t: Float) {
    val s = size.minDimension
    val happy = mood == Mood.THRILLED || mood == Mood.HAPPY
    val bounce = when (mood) {
        Mood.THRILLED -> abs(sin(t * 3f)) * s * 0.04f
        Mood.HAPPY -> abs(sin(t * 2f)) * s * 0.02f
        Mood.PANIC -> 0f
        else -> sin(t * 1.2f) * s * 0.006f
    }
    val shake = if (mood == Mood.PANIC) sin(t * 40f) * s * 0.004f else 0f
    val c = Offset(size.width / 2 + shake, size.height * 0.56f - bounce)
    // Shadow.
    drawOval(Color.Black.copy(alpha = 0.25f), Offset(c.x - s * 0.3f, size.height * 0.9f), Size(s * 0.6f, s * 0.06f))
    // Legs.
    for (k in 0 until 4) {
        val x = c.x + (k - 1.5f) * s * 0.13f
        drawRoundRect(Color(0xFF3A3F4A), Offset(x - s * 0.03f, c.y + s * 0.12f), Size(s * 0.06f, s * 0.2f + bounce), androidx.compose.ui.geometry.CornerRadius(s * 0.03f))
    }
    // Wool: a darker underside, then the fluffy top.
    fun wool(color: Color, dy: Float) {
        val n = 11
        for (k in 0 until n) {
            val a = 2 * PI.toFloat() * k / n
            drawCircle(color, s * 0.13f, Offset(c.x + kotlin.math.cos(a) * s * 0.27f, c.y + dy + sin(a) * s * 0.17f))
        }
        drawOval(color, Offset(c.x - s * 0.3f, c.y + dy - s * 0.2f), Size(s * 0.6f, s * 0.4f))
    }
    wool(Color(0xFFCFC8BA), s * 0.03f)
    wool(Color(0xFFF7F3EA), 0f)
    // Head.
    val head = Offset(c.x - s * 0.02f, c.y - s * 0.12f)
    val hw = s * 0.36f
    val hh = s * 0.32f
    drawOval(Color(0xFF2E333D), Offset(head.x - hw * 0.62f, head.y - hh * 0.4f), Size(hw * 0.4f, hh * 0.22f))
    drawOval(Color(0xFF2E333D), Offset(head.x + hw * 0.22f, head.y - hh * 0.4f), Size(hw * 0.4f, hh * 0.22f))
    drawOval(Color(0xFF3A3F4A), Offset(head.x - hw / 2, head.y - hh / 2), Size(hw, hh))
    // Tuft.
    for (k in -1..1) drawCircle(Color(0xFFF7F3EA), s * 0.055f, Offset(head.x + k * s * 0.06f, head.y - hh * 0.48f))
    // Eyes.
    val blink = ((t % 4.2f) < 0.14f)
    val lid = when (mood) {
        Mood.THRILLED -> 0f
        Mood.HAPPY -> 0.1f
        Mood.MEH -> 0.35f
        Mood.DISAPPOINTED -> 0.5f
        Mood.PANIC -> 0f
        Mood.NO_ALARM -> 0.3f
    }.let { if (blink) 1f else it }
    val er = s * if (mood == Mood.PANIC) 0.075f else 0.065f
    val look = Offset(noise1(t * 0.3f, 2) * er * 0.25f, if (mood == Mood.DISAPPOINTED) er * 0.25f else 0f)
    for (side in listOf(-1f, 1f)) {
        val e = Offset(head.x + side * hw * 0.2f, head.y - hh * 0.02f)
        drawCircle(Color.White, er, e)
        if (lid < 1f) {
            drawCircle(Color(0xFF131F24), er * 0.55f, e + look)
            drawCircle(Color.White, er * 0.18f, e + look + Offset(-er * 0.18f, -er * 0.2f))
        }
        if (lid > 0f) {
            clipPath(Path().apply { addOval(Rect(e, er + 1f)) }) {
                drawRect(Color(0xFF2E333D), Offset(e.x - er, e.y - er - 1f), Size(er * 2, er * 2 * lid + 1f))
            }
        }
        // Eyebrows set the tone.
        val tilt = when (mood) {
            Mood.THRILLED, Mood.HAPPY -> -0.15f
            Mood.MEH -> if (side < 0) 0.25f else -0.05f // one raised: skeptical
            Mood.DISAPPOINTED -> -0.35f
            Mood.PANIC -> -0.5f
            Mood.NO_ALARM -> 0.3f
        } * side
        val by = e.y - er * 1.35f - if (happy) er * 0.15f else 0f
        drawLine(Color(0xFF1C2028), Offset(e.x - er * 0.7f, by + tilt * er), Offset(e.x + er * 0.7f, by - tilt * er), strokeWidth = s * 0.018f, cap = StrokeCap.Round)
        if (mood == Mood.PANIC || mood == Mood.DISAPPOINTED) {
            drawArc(Color(0xFF8A6AC8).copy(alpha = 0.7f), 20f, 140f, false, Offset(e.x - er * 0.8f, e.y + er * 0.4f), Size(er * 1.6f, er * 0.9f), style = Stroke(s * 0.008f, cap = StrokeCap.Round))
        }
    }
    // Mouth.
    val m = Offset(head.x, head.y + hh * 0.26f)
    val mw = hw * 0.18f
    when (mood) {
        Mood.THRILLED -> {
            drawArc(Color(0xFF131F24), 0f, 180f, true, Offset(m.x - mw, m.y - mw * 0.6f), Size(mw * 2, mw * 1.4f))
            drawArc(Color(0xFFFF7A8A), 20f, 140f, true, Offset(m.x - mw * 0.6f, m.y), Size(mw * 1.2f, mw * 0.7f))
        }
        Mood.HAPPY -> drawArc(Color(0xFF131F24), 20f, 140f, false, Offset(m.x - mw, m.y - mw * 0.7f), Size(mw * 2, mw * 1.2f), style = Stroke(s * 0.018f, cap = StrokeCap.Round))
        Mood.MEH, Mood.NO_ALARM -> drawLine(Color(0xFF131F24), Offset(m.x - mw * 0.7f, m.y + mw * 0.1f), Offset(m.x + mw * 0.7f, m.y - mw * 0.1f), strokeWidth = s * 0.018f, cap = StrokeCap.Round)
        Mood.DISAPPOINTED -> drawArc(Color(0xFF131F24), 200f, 140f, false, Offset(m.x - mw * 0.8f, m.y), Size(mw * 1.6f, mw), style = Stroke(s * 0.018f, cap = StrokeCap.Round))
        Mood.PANIC -> drawOval(Color(0xFF131F24), Offset(m.x - mw * 0.4f, m.y - mw * 0.2f), Size(mw * 0.8f, mw * 1f))
    }
    // Sweat drop when things are bad.
    if (mood == Mood.PANIC || mood == Mood.DISAPPOINTED) {
        val d = Offset(head.x + hw * 0.52f, head.y - hh * 0.1f + (t * 0.3f % 1f) * s * 0.06f)
        val drop = Path().apply {
            moveTo(d.x, d.y - s * 0.04f)
            quadraticTo(d.x + s * 0.03f, d.y + s * 0.005f, d.x, d.y + s * 0.02f)
            quadraticTo(d.x - s * 0.03f, d.y + s * 0.005f, d.x, d.y - s * 0.04f)
        }
        drawPath(drop, Blue)
    }
    // Sparkles when it's thrilled.
    if (mood == Mood.THRILLED) {
        for (k in 0 until 3) {
            val p = (t * 0.6f + k / 3f) % 1f
            val o = Offset(c.x + (k - 1) * s * 0.38f, c.y - s * 0.38f - p * s * 0.1f)
            val r = s * 0.025f * sin(p * PI.toFloat())
            drawLine(Color(0xFFFFD84A), o - Offset(r, 0f), o + Offset(r, 0f), strokeWidth = s * 0.012f, cap = StrokeCap.Round)
            drawLine(Color(0xFFFFD84A), o - Offset(0f, r), o + Offset(0f, r), strokeWidth = s * 0.012f, cap = StrokeCap.Round)
        }
    }
}

