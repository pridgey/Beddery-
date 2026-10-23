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

// ---------------------------------------------------------------- The Orb

private enum class OrbShow { FACE, EYE, MOON }

/**
 * A giant LED sphere looming over a city at night. Content is drawn onto the sphere one LED at a
 * time, then each LED is shown as a dot, so it reads as a screen wrapped round a ball.
 */
private class Orb(val leds: Int) {
    val bitmap: Bitmap = Bitmap.createBitmap(leds, leds, Bitmap.Config.ARGB_8888)
    val image: ImageBitmap = bitmap.asImageBitmap()
    private val px = IntArray(leds * leds)
    var lookX = 0f
    var lookY = 0f
    private var targetX = 0f
    private var targetY = 0f
    private var nextLook = 0f
    var surprised = 0f
    private var moon: IntArray? = null
    private var moonSize = 0

    fun look(x: Float, y: Float) {
        targetX = x.coerceIn(-1f, 1f)
        targetY = y.coerceIn(-1f, 1f)
        surprised = 1f
        nextLook = Float.MAX_VALUE
    }

    fun step(dt: Float, t: Float) {
        if (t > nextLook || (nextLook == Float.MAX_VALUE && surprised <= 0f)) {
            targetX = noise1(t * 0.7f, 4)
            targetY = noise1(t * 0.5f, 9) * 0.6f
            nextLook = t + 1.5f + hash(t.toInt()) * 3f
        }
        lookX += (targetX - lookX) * (dt * 6f).coerceAtMost(1f)
        lookY += (targetY - lookY) * (dt * 6f).coerceAtMost(1f)
        surprised = (surprised - dt * 0.4f).coerceAtLeast(0f)
    }

    fun render(t: Float, show: OrbShow, next: OrbShow, wipe: Float, sleepy: Boolean, moonBitmap: Bitmap) {
        if (moon == null) {
            moonSize = moonBitmap.width
            moon = IntArray(moonSize * moonSize).also { moonBitmap.getPixels(it, 0, moonSize, 0, 0, moonSize, moonSize) }
        }
        val blink = (t % 3.7f) < 0.12f
        for (j in 0 until leds) {
            val y = 1f - 2f * (j + 0.5f) / leds
            val mode = if (y > 1f - 2f * wipe) next else show
            for (i in 0 until leds) {
                val x = 2f * (i + 0.5f) / leds - 1f
                val r2 = x * x + y * y
                if (r2 > 1f) {
                    px[j * leds + i] = 0
                    continue
                }
                val z = sqrt(1f - r2)
                // Wrap the flat picture around the sphere.
                val u = atan2(x, z) / (PI.toFloat() / 2) * 1.05f
                val v = asin(y) / (PI.toFloat() / 2) * 1.05f
                var c = when (mode) {
                    OrbShow.FACE -> face(u, v, blink, sleepy, t)
                    OrbShow.EYE -> eye(u, v, blink, sleepy)
                    OrbShow.MOON -> moonAt(u, v)
                }
                // Brighter towards the middle of the sphere.
                val shade = 0.5f + 0.5f * z
                c = scale(c, shade)
                px[j * leds + i] = c
            }
        }
        bitmap.setPixels(px, 0, leds, 0, 0, leds, leds)
    }

    private fun face(u: Float, v: Float, blink: Boolean, sleepy: Boolean, t: Float): Int {
        var r = 1f; var g = 0.78f; var b = 0.2f
        val ex = 0.33f
        val ey = 0.2f
        for (side in listOf(-1f, 1f)) {
            val dx = u - side * ex
            val dy = v - ey
            val closed = blink || sleepy
            if (closed) {
                // A closed eye is a gentle curve.
                if (abs(dx) < 0.16f && abs(dy + dx * dx * 2.5f - 0.02f) < 0.035f) return rgb(0.35f, 0.18f, 0.05f)
            } else {
                val d = sqrt(dx * dx / (0.15f * 0.15f) + dy * dy / (0.2f * 0.2f))
                if (d < 1f) {
                    val pdx = dx - lookX * 0.06f
                    val pdy = dy - lookY * 0.07f
                    val pr = if (surprised > 0f) 0.06f else 0.085f
                    if (pdx * pdx + pdy * pdy < pr * pr) {
                        if ((pdx + 0.03f).let { it * it } + (pdy - 0.03f).let { it * it } < 0.022f * 0.022f) return rgb(1f, 1f, 1f)
                        return rgb(0.12f, 0.08f, 0.04f)
                    }
                    return rgb(1f, 1f, 1f)
                }
            }
            // Rosy cheeks.
            val cdx = u - side * 0.5f
            val cdy = v + 0.12f
            if (cdx * cdx + cdy * cdy < 0.1f * 0.1f) { r = 1f; g = 0.55f; b = 0.4f }
        }
        val mouthY = v + 0.28f
        if (sleepy) {
            if (abs(u) < 0.1f && abs(mouthY) < 0.025f) return rgb(0.35f, 0.18f, 0.05f)
            // A little "z" drifting up.
            val zp = (t * 0.25f) % 1f
            val zx = u - 0.55f - zp * 0.1f
            val zy = v - 0.55f - zp * 0.3f
            if (abs(zx) < 0.07f && (abs(zy - 0.06f) < 0.018f || abs(zy + 0.06f) < 0.018f || abs(zy + zx * 0.85f) < 0.02f) && abs(zy) < 0.08f) return rgb(1f, 1f, 1f)
        } else if (surprised > 0f) {
            val d = sqrt(u * u + (mouthY + 0.05f).let { it * it }) / 0.11f
            if (d < 1f) return if (d > 0.6f) rgb(0.35f, 0.18f, 0.05f) else rgb(0.55f, 0.18f, 0.12f)
        } else {
            // A broad smile.
            val d = sqrt(u * u + (v - 0.05f).let { it * it })
            if (abs(d - 0.45f) < 0.045f && v < -0.15f && abs(u) < 0.38f) return rgb(0.35f, 0.18f, 0.05f)
        }
        // Slight shading towards the edge of the face.
        val edge = (u * u + v * v)
        return rgb(r - edge * 0.12f, g - edge * 0.2f, b)
    }

    private fun eye(u: Float, v: Float, blink: Boolean, sleepy: Boolean): Int {
        // Eyelids close from top and bottom.
        val open = if (blink) 0.05f else if (sleepy) 0.35f else 0.85f
        val lid = abs(v) > open * (1f - u * u * 0.6f)
        if (lid) return rgb(0.78f, 0.52f, 0.38f)
        val ix = u - lookX * 0.35f
        val iy = v - lookY * 0.3f
        val d = sqrt(ix * ix + iy * iy)
        val pupil = if (surprised > 0f) 0.1f else 0.17f
        return when {
            d < pupil -> if ((ix + 0.06f).let { it * it } + (iy - 0.07f).let { it * it } < 0.035f * 0.035f) rgb(1f, 1f, 1f) else rgb(0.02f, 0.02f, 0.03f)
            d < 0.4f -> {
                val a = atan2(iy, ix)
                val streak = 0.75f + 0.25f * sin(a * 37f) * sin(a * 11f + d * 20f)
                val k = (d - pupil) / (0.4f - pupil)
                rgb((0.12f + 0.15f * k) * streak, (0.45f + 0.2f * k) * streak, (0.8f - 0.25f * k) * streak)
            }
            d < 0.43f -> rgb(0.05f, 0.12f, 0.25f)
            else -> {
                // Sclera, faintly pink towards the corners.
                val pink = smoothstep(0.6f, 1f, abs(u))
                rgb(0.95f, 0.93f - pink * 0.2f, 0.92f - pink * 0.25f)
            }
        }
    }

    private fun moonAt(u: Float, v: Float): Int {
        val m = moon ?: return 0
        val x = ((u * 0.95f + 1f) / 2f * moonSize).toInt()
        val y = ((1f - (v * 0.95f + 1f) / 2f) * moonSize).toInt()
        if (x !in 0 until moonSize || y !in 0 until moonSize) return rgb(0.02f, 0.02f, 0.05f)
        val c = m[y * moonSize + x]
        return if ((c ushr 24) < 10) rgb(0.02f, 0.02f, 0.05f) else c or (0xFF shl 24)
    }

    private fun rgb(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or ((r.coerceIn(0f, 1f) * 255).toInt() shl 16) or ((g.coerceIn(0f, 1f) * 255).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255).toInt()

    private fun scale(c: Int, k: Float): Int {
        val r = (((c shr 16) and 0xFF) * k).toInt()
        val g = (((c shr 8) and 0xFF) * k).toInt()
        val b = ((c and 0xFF) * k).toInt()
        return (c and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
    }
}

/** One LED's frame: dark around a round lens, tiled across the sphere. */
private val LedMask: Bitmap by lazy {
    val n = 16
    val px = IntArray(n * n) { i ->
        val x = i % n + 0.5f - n / 2f
        val y = i / n + 0.5f - n / 2f
        val d = sqrt(x * x + y * y) / (n / 2f)
        val a = smoothstep(0.62f, 0.9f, d)
        (((a * 0.92f) * 255).toInt() shl 24)
    }
    Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
}

@Composable
fun OrbSaver(ctx: SaverContext) {
    val orb = remember { Orb(if (ctx.preview) 56 else 88) }
    val sleepy = ctx.settings.isNight(ctx.now.toLocalTime())
    val phase = remember(ctx.now.toLocalDate()) { Astronomy.moonPhase(Instant.now()) }
    val moon = remember(ctx.now.toLocalDate()) { MoonRender.render(120, phase, false) }
    val mask = remember { BitmapShader(LedMask, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    val maskMatrix = remember { Matrix() }
    val maskBrush = remember { ShaderBrush(mask) }
    val cooldown = remember { Cooldown(5_000) }
    val sphere = remember { floatArrayOf(0f, 0f, 1f) }
    val lastRender = remember { floatArrayOf(-1f) }
    val time = rememberFrameTime { dt, t ->
        orb.step(dt, t)
        if (t - lastRender[0] >= 1f / 30f) {
            val cycle = t / 40f
            val shows = OrbShow.entries
            val show = shows[cycle.toInt() % shows.size]
            val next = shows[(cycle.toInt() + 1) % shows.size]
            val wipe = smoothstep(0.96f, 1f, cycle % 1f)
            orb.render(t, show, next, wipe, sleepy, moon)
            lastRender[0] = t
        }
    }
    OnSaverTap { at ->
        if (cooldown.tryAcquire()) orb.look((at.x - sphere[0]) / sphere[2], -(at.y - sphere[1]) / sphere[2])
    }
    val city = remember { List(26) { floatArrayOf(hash(it, 1), 0.04f + hash(it, 2) * 0.16f, 0.04f + hash(it, 3) * 0.05f) } }
    Canvas(Modifier.fillMaxSize()) {
        time.value
        if (lastRender[0] < 0f) orb.render(0f, OrbShow.FACE, OrbShow.FACE, 0f, sleepy, moon)
        val w = size.width
        val h = size.height
        val landscape = w > h
        val r = if (landscape) minOf(w * 0.4f, h * 0.27f) else minOf(w * 0.4f, h * 0.3f)
        val c = if (landscape) Offset(w * 0.5f, h * 0.36f) else Offset(w / 2, h * 0.38f)
        sphere[0] = c.x; sphere[1] = c.y; sphere[2] = r
        val skyline = c.y + r * 0.92f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF03040C), Color(0xFF0C0E22), Color(0xFF241A30)), 0f, skyline))
        // The sphere lights up the sky around it.
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC94A).copy(alpha = 0.16f), Color.Transparent), c, r * 2.2f), r * 2.2f, c, blendMode = BlendMode.Plus)
        val disc = Path().apply { addOval(Rect(c, r)) }
        drawCircle(Color.Black, r, c)
        clipPath(disc) {
            drawImage(orb.image, IntOffset.Zero, IntSize(orb.leds, orb.leds), IntOffset((c.x - r).toInt(), (c.y - r).toInt()), IntSize((r * 2).toInt(), (r * 2).toInt()), filterQuality = FilterQuality.None)
            val cell = r * 2 / orb.leds
            maskMatrix.setScale(cell / LedMask.width, cell / LedMask.height)
            maskMatrix.postTranslate(c.x - r, c.y - r)
            mask.setLocalMatrix(maskMatrix)
            drawRect(maskBrush, Offset(c.x - r, c.y - r), Size(r * 2, r * 2))
            drawCircle(Brush.radialGradient(0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f), center = c, radius = r), r, c)
        }
        // City skyline in front, with a few lit windows.
        val ground = h
        city.forEachIndexed { i, b ->
            val bw = w * b[2] * 1.4f
            val x = w * b[0] - bw / 2
            val top = skyline - h * b[1] * 0.6f + (if (abs(x + bw / 2 - c.x) < r * 0.8f) h * 0.08f else 0f)
            drawRect(Color(0xFF07070E), Offset(x, top), Size(bw, ground - top))
            for (k in 0 until 6) {
                if (hash(i * 7 + k) < 0.5f) continue
                val wy = top + (k + 1) * h * 0.02f
                if (wy > ground) break
                drawRect(Color(0xFFFFD08A).copy(alpha = 0.5f), Offset(x + bw * (0.2f + hash(i, k) * 0.5f), wy), Size(bw * 0.12f, h * 0.006f))
            }
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFFB13D).copy(alpha = 0.06f), Color.Transparent), skyline - h * 0.05f, skyline + h * 0.08f), blendMode = BlendMode.Plus)
    }
}
