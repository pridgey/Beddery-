@file:OptIn(ExperimentalTextApi::class)

package dev.pridgey.beddery.savers.impl

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas as ComposeCanvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.content.res.ResourcesCompat
import dev.pridgey.beddery.R
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.rememberFrameTime
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// ---------------------------------------------------------------- Sleep Coach
// A sheep illustration per scene, chosen by how much sleep you'll get. Each sheep sits flush with the bottom
// of the screen (or on a soft floor), is colour-graded into the mood colour, and has a small animated touch.

private enum class CoachMood(val lines: Int, val top: Int, val bottom: Int) {
    THRILLED(R.array.coach_thrilled, 0xFF3F8CF8.toInt(), 0xFF2A6CE6.toInt()),
    HAPPY(R.array.coach_happy, 0xFF5D66F0.toInt(), 0xFF454BD3.toInt()),
    MEH(R.array.coach_meh, 0xFF7C5CF0.toInt(), 0xFF6243D6.toInt()),
    SAD(R.array.coach_disappointed, 0xFF9C4FE0.toInt(), 0xFF7E36C4.toInt()),
    PANIC(R.array.coach_panic, 0xFFD94B8E.toInt(), 0xFFB83271.toInt()),
    FRANTIC(R.array.coach_frantic, 0xFFE8545A.toInt(), 0xFFC83A43.toInt()),
    NO_ALARM(R.array.coach_no_alarm, 0xFF3D4E92.toInt(), 0xFF2B3A75.toInt()),
}

private fun coachMoodFor(hours: Double?) = when {
    hours == null -> CoachMood.NO_ALARM
    hours >= 9 -> CoachMood.THRILLED
    hours >= 7.5 -> CoachMood.HAPPY
    hours >= 6 -> CoachMood.MEH
    hours >= 4 -> CoachMood.SAD
    hours >= 2 -> CoachMood.PANIC
    else -> CoachMood.FRANTIC
}

/** How a sticker is placed: on a floor, flush with a straight cut-off bottom, or pinned to the right / bottom-right. */
private enum class Seat { FLOOR, CUT, RIGHT, CORNER }

/** The small animated touch drawn over each scene. */
private enum class CoachFx { ZEN, NOTES, ZZZ, SLEEP, SPARKLE, BUBBLES, STEAM, THINK, MOON, JUMPERS, TEARS, GLASS, SWEAT, TICK, SHOUT, MELT }

/**
 * One sticker. Sizes are in the image's own pixels: [top]/[bottom]/[left]/[right] bound the drawing, and
 * [edgeL]..[edgeR] is the width of its lowest row (a straight cut-off bottom spans most of the image).
 * [ax], [ay] place the effect as a fraction of the image; [bx], [by] are a second point where needed.
 */
private class CoachSticker(
    val res: Int, val kind: Seat, val fx: CoachFx, val w: Int, val h: Int, val top: Int, val bottom: Int, val left: Int, val right: Int,
    val edgeL: Int, val edgeR: Int, val ax: Float = 0.5f, val ay: Float = 0.1f, val bx: Float = 0f, val by: Float = 0f,
)

private val COACH_STICKERS = mapOf(
    CoachMood.THRILLED to listOf(
        CoachSticker(R.drawable.coach_zen, Seat.FLOOR, CoachFx.ZEN, 385, 422, 5, 416, 5, 379, 31, 354),
        CoachSticker(R.drawable.coach_guitar, Seat.FLOOR, CoachFx.NOTES, 447, 449, 5, 443, 5, 441, 51, 188, 0.85f, 0.12f),
        CoachSticker(R.drawable.coach_pillow_hug, Seat.FLOOR, CoachFx.ZZZ, 451, 467, 5, 461, 5, 445, 105, 279, 0.8f, 0.1f),
        CoachSticker(R.drawable.coach_yoga, Seat.FLOOR, CoachFx.SPARKLE, 461, 358, 5, 352, 5, 455, 136, 198),
        CoachSticker(R.drawable.coach_bubble_bath, Seat.FLOOR, CoachFx.BUBBLES, 398, 417, 5, 411, 5, 392, 60, 333),
        CoachSticker(R.drawable.coach_sleep_mask, Seat.FLOOR, CoachFx.ZZZ, 389, 232, 5, 226, 5, 383, 70, 220, 0.3f, 0.15f),
    ),
    CoachMood.HAPPY to listOf(
        CoachSticker(R.drawable.coach_tucked_in, Seat.FLOOR, CoachFx.SLEEP, 504, 474, 5, 468, 5, 497, 118, 368, 0.62f, 0.12f),
        CoachSticker(R.drawable.coach_groggy, Seat.FLOOR, CoachFx.ZZZ, 372, 411, 5, 405, 5, 366, 210, 273, 0.79f, 0.08f),
        CoachSticker(R.drawable.coach_cocoa, Seat.FLOOR, CoachFx.STEAM, 429, 455, 5, 449, 5, 423, 77, 342, 0.5f, 0.52f),
        CoachSticker(R.drawable.coach_bedtime_story, Seat.CUT, CoachFx.SPARKLE, 467, 474, 5, 467, 5, 460, 22, 454),
        CoachSticker(R.drawable.coach_headphones, Seat.FLOOR, CoachFx.NOTES, 418, 477, 5, 471, 5, 412, 146, 218, 0.86f, 0.14f),
        CoachSticker(R.drawable.coach_star_blanket, Seat.FLOOR, CoachFx.SPARKLE, 408, 456, 5, 450, 5, 402, 61, 350),
    ),
    CoachMood.MEH to listOf(
        CoachSticker(R.drawable.coach_chin_on_hoof, Seat.CUT, CoachFx.THINK, 390, 388, 5, 382, 5, 383, 10, 382, 0.82f, 0.1f),
        CoachSticker(R.drawable.coach_fading_fast, Seat.FLOOR, CoachFx.ZZZ, 353, 403, 5, 397, 5, 347, 198, 259, 0.79f, 0.08f),
        CoachSticker(R.drawable.coach_moon_gazing, Seat.CUT, CoachFx.MOON, 434, 375, 5, 369, 5, 428, 25, 409, 0.1f, 0.0f),
        CoachSticker(R.drawable.coach_counting, Seat.FLOOR, CoachFx.JUMPERS, 358, 352, 4, 346, 5, 317, 160, 205),
    ),
    CoachMood.SAD to listOf(
        CoachSticker(R.drawable.coach_pleading, Seat.CUT, CoachFx.TEARS, 459, 415, 5, 409, 5, 453, 39, 428, 0.36f, 0.46f, 0.66f, 0.46f),
        CoachSticker(R.drawable.coach_crying, Seat.FLOOR, CoachFx.TEARS, 342, 356, 5, 350, 5, 336, 161, 214, 0.21f, 0.55f, 0.53f, 0.56f),
    ),
    CoachMood.PANIC to listOf(
        CoachSticker(R.drawable.coach_glass, Seat.CUT, CoachFx.GLASS, 460, 405, 5, 399, 5, 454, 26, 433, 0.5f, 0.66f),
        CoachSticker(R.drawable.coach_squished, Seat.CUT, CoachFx.SWEAT, 510, 423, 5, 417, 5, 504, 47, 482),
        CoachSticker(R.drawable.coach_look_at_time, Seat.RIGHT, CoachFx.TICK, 438, 359, 5, 352, 5, 432, 27, 325, 0.82f, 0.64f),
        CoachSticker(R.drawable.coach_megaphone, Seat.CORNER, CoachFx.SHOUT, 449, 299, 5, 294, 5, 443, 171, 405, 0.05f, 0.38f),
    ),
    CoachMood.FRANTIC to listOf(
        CoachSticker(R.drawable.coach_melted, Seat.FLOOR, CoachFx.MELT, 450, 247, 5, 241, 5, 444, 45, 98),
        CoachSticker(R.drawable.coach_flat_out, Seat.FLOOR, CoachFx.MELT, 465, 226, 5, 220, 5, 459, 138, 353),
    ),
    CoachMood.NO_ALARM to listOf(
        CoachSticker(R.drawable.coach_waiting, Seat.RIGHT, CoachFx.TICK, 453, 412, 5, 405, 5, 447, 26, 351, 0.876f, 0.62f),
        CoachSticker(R.drawable.coach_midnight_snack, Seat.FLOOR, CoachFx.SPARKLE, 441, 483, 5, 476, 5, 435, 174, 175),
        CoachSticker(R.drawable.coach_butterfly_net, Seat.FLOOR, CoachFx.SPARKLE, 375, 476, 5, 469, 5, 369, 91, 130),
        CoachSticker(R.drawable.coach_painter, Seat.FLOOR, CoachFx.SPARKLE, 424, 470, 6, 463, 5, 418, 137, 282),
    ),
)

private val Fraunces = FontFamily(
    Font(R.font.fraunces, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.fraunces, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.fraunces_italic, FontWeight.Normal, FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(420))),
)

private fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(c: Int, s: Int) = (c shr s) and 0xFF
    fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
}
private fun alpha(c: Int, a: Float) = ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (c and 0xFFFFFF)

/** Decoded stickers, and copies tinted for each mood, kept for the life of the screen. */
private class CoachArt(context: Context) {
    private val res: Resources = context.resources
    private val raw = HashMap<Int, Bitmap>()
    private val graded = HashMap<Pair<Int, CoachMood>, Bitmap>()
    val bold: Typeface? = runCatching { ResourcesCompat.getFont(context, R.font.fraunces_italic) }.getOrNull()

    fun raw(id: Int): Bitmap = raw.getOrPut(id) {
        BitmapFactory.decodeResource(res, id, BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.ARGB_8888 })
    }

    /** The sticker lit by the scene: its shading takes on the mood colour, deepening toward the bottom. */
    fun graded(s: CoachSticker, mood: CoachMood): Bitmap = graded.getOrPut(s.res to mood) {
        val src = raw(s.res)
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val p = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, out.height.toFloat(),
                intArrayOf(0xFFFFFFFF.toInt(), mix(mood.top, 0xFFFFFFFF.toInt(), 0.86f), mix(mood.bottom, 0xFFFFFFFF.toInt(), 0.62f)),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }
        c.drawRect(0f, 0f, out.width.toFloat(), out.height.toFloat(), p)
        c.drawBitmap(src, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        out
    }
}

/** Where the sticker lands on screen: scale, top-left corner, the image row that sits on the anchor, and the floor (if any). */
private class CoachPlacement(val s: Float, val x: Float, val y: Float, val anchorY: Float, val floorY: Float?)

private fun place(st: CoachSticker, W: Float, H: Float, land: Boolean): CoachPlacement {
    val stX = if (land) W * 0.44f else 0f
    val stW = if (land) W * 0.56f else W
    val topLimit = if (land) H * 0.05f else H * 0.37f
    if (st.kind != Seat.FLOOR) {
        val edge = st.bottom - 1f
        val span = (st.edgeR - st.edgeL).toFloat()
        var s = if (span / st.w > 0.8f) stW * 1.02f / span else stW * (if (land) 0.92f else 0.98f) / st.w
        s = minOf(s, (H - topLimit) / (edge - st.top))
        val x = when (st.kind) {
            Seat.RIGHT -> stX + stW - st.right * s - stW * 0.02f
            Seat.CORNER -> stX + stW - st.right * s
            else -> stX + stW / 2 - (st.edgeL + st.edgeR) / 2f * s
        }
        return CoachPlacement(s, x, H + 1 - edge * s, edge, null)
    }
    val floorY = H * (if (land) 0.955f else 0.95f)
    val wide = st.w.toFloat() / st.h > 1.4f
    val s = minOf(stW * (if (wide) 0.98f else 0.9f) / st.w, (floorY - topLimit) / (st.bottom - st.top))
    return CoachPlacement(s, stX + stW / 2 - st.w * s / 2, floorY - st.bottom * s, st.bottom.toFloat(), floorY)
}

@Composable
fun SleepCoachSaver(ctx: SaverContext) {
    val context = LocalContext.current
    val art = remember(context) { CoachArt(context) }
    val sampleAlarm = remember { LocalDateTime.now().plusHours(5).plusMinutes(12) }
    val alarm = ctx.nextAlarm ?: if (ctx.demo) sampleAlarm else null
    val remaining = alarm?.let { Duration.between(ctx.now, it).coerceAtLeast(Duration.ZERO) }
    val hours = remaining?.let { it.toMinutes() / 60.0 }
    val mood = coachMoodFor(hours)
    val list = COACH_STICKERS.getValue(mood)

    // A new sheep every few minutes; tapping moves on to the next one.
    var nudge by remember { mutableIntStateOf(0) }
    var switchedAt by remember { mutableLongStateOf(0L) }
    OnSaverTap { nudge++; switchedAt = System.currentTimeMillis() }
    val epochMin = ctx.now.atZone(ZoneId.systemDefault()).toEpochSecond() / 60
    val sticker = list[((epochMin / 4 + nudge) % list.size).toInt()]

    val lines = stringArrayResource(mood.lines)
    val line = lines[(ctx.now.toLocalTime().toSecondOfDay() / 30) % lines.size]
    val time = rememberFrameTime()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val land = maxWidth > maxHeight
        val unit = min(maxWidth, maxHeight)
        val density = LocalDensity.current
        ComposeCanvas(Modifier.fillMaxSize()) {
            val t = time.value
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas
                val d = density.density
                nc.save(); nc.scale(d, d)
                drawCoachScene(nc, art, mood, sticker, size.width / d, size.height / d, land, t, System.currentTimeMillis() - switchedAt)
                nc.restore()
            }
        }
        CoachText(mood, line, remaining, alarm, ctx, land, unit, maxWidth, maxHeight)
    }
}

@Composable
private fun CoachText(mood: CoachMood, line: String, remaining: Duration?, alarm: LocalDateTime?, ctx: SaverContext, land: Boolean, unit: Dp, w: Dp, h: Dp) {
    val density = LocalDensity.current
    val cap = with(density) { (unit * if (land) 0.062f else 0.064f).toSp() }
    val big = with(density) { (unit * 0.2f).toSp() }
    val small = with(density) { (unit * 0.072f).toSp() }
    val note = with(density) { (unit * 0.036f).toSp() }
    val align = if (land) Alignment.Start else Alignment.CenterHorizontally
    val textAlign = if (land) TextAlign.Start else TextAlign.Center
    val mod = if (land) Modifier.padding(start = w * 0.06f, top = h * 0.12f).width(w * 0.36f) else Modifier.fillMaxWidth().padding(top = h * 0.055f, start = w * 0.08f, end = w * 0.08f)
    Column(mod, horizontalAlignment = align) {
        AnimatedContent(line, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) }, label = "coachLine") { text ->
            Text(text, color = Color.White.copy(alpha = 0.95f), fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = cap, lineHeight = cap * 1.28f, textAlign = textAlign, modifier = if (land) Modifier else Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(unit * if (land) 0.08f else 0.05f))
        if (remaining != null && alarm != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${remaining.toHours()}", color = Color.White, fontFamily = Fraunces, fontWeight = FontWeight.Light, fontSize = big, lineHeight = big)
                Text("h", color = Color.White, fontFamily = Fraunces, fontSize = small, modifier = Modifier.padding(start = 2.dp, end = 8.dp, bottom = unit * 0.022f))
                Text("%02d".format(remaining.toMinutes() % 60), color = Color.White, fontFamily = Fraunces, fontWeight = FontWeight.Light, fontSize = big, lineHeight = big)
                Text("m", color = Color.White, fontFamily = Fraunces, fontSize = small, modifier = Modifier.padding(start = 2.dp, bottom = unit * 0.022f))
            }
            Text(stringResource(R.string.coach_until, formatTimeWithAmPm(alarm.toLocalTime(), ctx.use24h)), color = Color.White.copy(alpha = 0.78f), fontFamily = Fraunces, fontSize = note)
            Spacer(Modifier.height(unit * 0.03f))
            val hours = remaining.toMinutes() / 60f
            ComposeCanvas(Modifier.width(unit * 0.29f).height(unit * 0.03f)) {
                // eight hours as eight small moons
                val r = size.height * 0.37f
                val gap = (size.width - 2 * r) / 7f
                for (i in 0 until 8) {
                    val cx = r + i * gap
                    val cy = size.height / 2
                    drawCircle(Color.White.copy(alpha = 0.55f), r, Offset(cx, cy), style = Stroke(width = max(1f, r * 0.22f)))
                    val fill = (hours - i).coerceIn(0f, 1f)
                    if (fill > 0f) drawArc(Color.White, 90f, 360f * fill, useCenter = true, topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r))
                }
            }
        } else {
            Text(stringResource(R.string.sleep_no_alarm), color = Color.White, fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = big * 0.5f)
            Text(stringResource(R.string.coach_no_alarm_sub), color = Color.White.copy(alpha = 0.78f), fontFamily = Fraunces, fontSize = note)
        }
    }
}

// ---------------------------------------------------------------- drawing (dp units)

private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
private val path = Path()

private fun sparkle(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
    path.reset()
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4 - PI.toFloat() / 2
        val d = if (i % 2 == 1) r * 0.28f else r
        if (i == 0) path.moveTo(x + cos(a) * d, y + sin(a) * d) else path.lineTo(x + cos(a) * d, y + sin(a) * d)
    }
    path.close(); paint.reset(); paint.isAntiAlias = true; paint.color = color; c.drawPath(path, paint)
}

private fun drop(c: Canvas, x: Float, y: Float, r: Float, a: Float) {
    path.reset(); path.moveTo(x, y - r * 1.6f)
    path.quadTo(x + r * 1.1f, y - r * 0.2f, x, y + r); path.quadTo(x - r * 1.1f, y - r * 0.2f, x, y - r * 1.6f)
    paint.reset(); paint.isAntiAlias = true; paint.color = alpha(0x93DCFF, a); c.drawPath(path, paint)
    paint.color = alpha(0xFFFFFF, a * 0.8f); c.drawCircle(x - r * 0.3f, y, r * 0.25f, paint)
}

private fun strokeLine(c: Canvas, x0: Float, y0: Float, x1: Float, y1: Float, color: Int, w: Float) {
    paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = w; paint.color = color
    c.drawLine(x0, y0, x1, y1, paint)
}

private fun note(c: Canvas, x: Float, y: Float, s: Float, a: Float) {
    paint.reset(); paint.isAntiAlias = true; paint.color = alpha(0xFFFFFF, a)
    c.save(); c.rotate(-23f, x, y); c.drawOval(RectF(x - s * 0.32f, y - s * 0.24f, x + s * 0.32f, y + s * 0.24f), paint); c.restore()
    c.drawRect(x + s * 0.22f, y - s * 0.95f, x + s * 0.32f, y, paint)
    path.reset(); path.moveTo(x + s * 0.32f, y - s * 0.95f); path.quadTo(x + s * 0.75f, y - s * 0.7f, x + s * 0.55f, y - s * 0.35f)
    path.quadTo(x + s * 0.6f, y - s * 0.65f, x + s * 0.32f, y - s * 0.7f); path.close(); c.drawPath(path, paint)
}

private fun drawCoachScene(c: Canvas, art: CoachArt, mood: CoachMood, st: CoachSticker, W: Float, H: Float, land: Boolean, t: Float, sinceSwitch: Long) {
    val U = minOf(W, H)
    // background: the mood gradient, a soft pool of light behind the sheep and a few sparkles
    paint.reset(); paint.shader = LinearGradient(0f, 0f, 0f, H, mood.top, mood.bottom, Shader.TileMode.CLAMP)
    c.drawRect(0f, 0f, W, H, paint); paint.shader = null
    val stX = if (land) W * 0.44f else 0f
    val stW = if (land) W * 0.56f else W
    val stY = if (land) 0f else H * 0.335f
    val stH = if (land) H else H * 0.665f
    val gx = stX + stW / 2
    val gy = stY + (if (land) 0.16f + 0.86f * 0.56f else 0.56f) * stH
    val gr = (if (land) stH else stW) * 0.62f
    paint.isAntiAlias = true
    paint.color = alpha(mix(mood.top, 0xFFFFFFFF.toInt(), 0.4f), 0.16f); c.drawCircle(gx, gy, gr, paint)
    paint.color = alpha(mix(mood.top, 0xFFFFFFFF.toInt(), 0.4f), 0.10f); c.drawCircle(gx, gy, gr * 1.45f, paint)
    val sky = alpha(mix(mood.top, 0xFFFFFFFF.toInt(), 0.7f), 0.5f)
    for (i in 0 until 9) {
        val sx = ((i * 0.618f + 0.13f) % 1f) * W
        val sy = ((i * 0.377f + 0.21f) % 1f) * H * 0.95f
        val k = 0.6f + 0.4f * sin(t * (0.5f + (i % 3) * 0.2f) + i * 2)
        sparkle(c, sx, sy, U * (0.008f + (i % 4) * 0.004f) * k, sky)
    }

    val p = place(st, W, H, land)
    p.floorY?.let { fy ->
        path.reset(); path.moveTo(-10f, H + 10); path.lineTo(-10f, fy + U * 0.04f)
        path.quadTo(W / 2, fy - U * 0.06f, W + 10, fy + U * 0.04f); path.lineTo(W + 10, H + 10); path.close()
        paint.reset(); paint.isAntiAlias = true; paint.color = mix(mood.bottom, 0xFF1A0B33.toInt(), 0.22f); c.drawPath(path, paint)
    }
    val w = st.w * p.s
    val h = st.h * p.s
    val ax = p.x + w / 2
    val ay = p.y + p.anchorY * p.s
    var sx = 1f
    var sy = 1f + sin(t * 1.3f) * 0.008f
    var rot = 0f
    var dx = 0f
    when (st.fx) {
        CoachFx.ZEN -> rot = sin(t * 0.9f) * 1.4f
        CoachFx.MELT -> { val k = sin(t * 0.8f); sx = 1 + k * 0.025f; sy = 1 - k * 0.03f }
        CoachFx.SHOUT, CoachFx.SWEAT -> dx = sin(t * 38f) * W * 0.002f
        CoachFx.TICK -> rot = sin(t * 2.4f) * 0.35f
        else -> {}
    }
    if (p.floorY != null) {
        paint.reset(); paint.isAntiAlias = true; paint.color = 0x38140028
        c.drawOval(RectF(ax - w * 0.36f * sx, ay - h * 0.045f, ax + w * 0.36f * sx, ay + h * 0.025f), paint)
    }
    val fade = (sinceSwitch / 300f).coerceIn(0f, 1f)
    c.save(); c.translate(ax + dx, ay); c.rotate(rot); c.scale(sx, sy)
    paint.reset(); paint.isFilterBitmap = true; paint.alpha = (255 * fade).toInt()
    c.drawBitmap(art.graded(st, mood), null, RectF(-w / 2, -p.anchorY * p.s, w / 2, h - p.anchorY * p.s), paint)
    c.restore()
    // screen position of a point given as a fraction of the sticker
    fun at(fx: Float, fy: Float) = Offset(ax + dx + (fx * st.w - st.w / 2f) * p.s * sx, ay + (fy * st.h - p.anchorY) * p.s * sy)
    drawFx(c, art, st, W, H, U, land, t, w, h, ::at)
}

private fun drawFx(c: Canvas, art: CoachArt, st: CoachSticker, W: Float, H: Float, U: Float, land: Boolean, t: Float, w: Float, h: Float, at: (Float, Float) -> Offset) {
    val gold = 0xFFFFF0AA.toInt()
    when (st.fx) {
        CoachFx.ZZZ, CoachFx.SLEEP -> {
            val o0 = at(st.ax, st.ay)
            val o = Offset(minOf(o0.x, W - U * 0.2f), maxOf(o0.y, H * 0.08f + U * 0.26f)) // room for the drift up
            paint.reset(); paint.isAntiAlias = true; paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
            for (i in 0 until 3) {
                val k = (t * 0.16f + i / 3f) % 1f
                paint.textSize = U * (0.045f + k * 0.04f); paint.color = alpha(0xFFFFFF, sin(k * PI.toFloat()) * 0.85f)
                c.drawText("z", o.x + k * U * 0.12f + sin(k * 7 + i) * U * 0.015f, o.y - k * U * 0.22f, paint)
            }
            if (st.fx == CoachFx.SLEEP) for ((i, f) in listOf(0.15f to 0.06f, 0.82f to 0.04f, 0.9f to 0.2f).withIndex()) {
                val x = if (land) W * 0.44f + f.first * W * 0.56f else f.first * W
                val y = if (land) H * (0.06f + f.second) else H * (0.335f + f.second * 0.665f)
                sparkle(c, x, y, U * 0.025f * (0.6f + 0.4f * sin(t * 2 + i * 2)), gold)
            }
        }
        CoachFx.THINK -> {
            val o = at(st.ax, st.ay)
            paint.reset(); paint.isAntiAlias = true
            for (i in 0 until 3) {
                paint.color = alpha(0xFFFFFF, if ((t * 1.2f) % 4f > i) 0.9f else 0.25f)
                c.drawCircle(o.x + i * U * 0.04f, o.y - i * U * 0.015f, U * 0.012f, paint)
            }
        }
        CoachFx.TEARS -> for ((i, pt) in listOf(st.ax to st.ay, st.bx to st.by).withIndex()) {
            val k = (t * 0.6f + i * 0.5f) % 1f
            val o = at(pt.first, pt.second)
            drop(c, o.x, o.y + k * U * 0.12f, U * 0.014f, 1 - k * k)
        }
        CoachFx.GLASS -> {
            val o = at(st.ax, st.ay)
            val f = 0.5f + 0.5f * sin(t * 1.6f)
            paint.reset(); paint.shader = RadialGradient(o.x, o.y, w * 0.16f, alpha(0xFFFFFF, 0.12f + f * 0.14f), 0x00FFFFFF, Shader.TileMode.CLAMP)
            c.drawCircle(o.x, o.y, w * 0.16f, paint); paint.shader = null
            val top = at(0f, 0f).y
            c.save(); c.clipRect(if (land) W * 0.44f else 0f, top, W, H)
            paint.reset(); paint.isAntiAlias = true; paint.color = alpha(0xFFFFFF, 0.12f)
            path.reset(); path.moveTo(W * 0.62f, 0f); path.lineTo(W * 0.8f, 0f); path.lineTo(W * 0.2f, H); path.lineTo(W * 0.02f, H); path.close(); c.drawPath(path, paint)
            path.reset(); path.moveTo(W * 0.86f, 0f); path.lineTo(W * 0.91f, 0f); path.lineTo(W * 0.33f, H); path.lineTo(W * 0.28f, H); path.close(); c.drawPath(path, paint)
            c.restore()
        }
        CoachFx.SWEAT -> for ((i, pt) in listOf(0.12f to 0.18f, 0.9f to 0.2f).withIndex()) {
            val k = (t * 0.7f + i * 0.5f) % 1f
            val o = at(pt.first, pt.second)
            drop(c, o.x + (if (i == 1) 1 else -1) * k * U * 0.05f, o.y + k * U * 0.06f, U * 0.018f, 1 - k)
        }
        CoachFx.TICK -> {
            val o = at(st.ax, st.ay)
            val k = (t * 2) % 1f
            for (a in listOf(-2.2f, -1.6f, -1.0f)) {
                val r0 = U * (0.06f + k * 0.02f)
                strokeLine(c, o.x + cos(a) * r0, o.y + sin(a) * r0, o.x + cos(a) * (r0 + U * 0.03f), o.y + sin(a) * (r0 + U * 0.03f), alpha(0xFFFFFF, 0.9f * (1 - k)), U * 0.006f)
            }
        }
        CoachFx.SHOUT -> {
            val o = at(st.ax, st.ay)
            val k = 0.75f + 0.25f * sin(t * 14)
            for (a in listOf(-2.6f, -3.0f, 2.8f)) {
                val r0 = U * 0.05f
                strokeLine(c, o.x + cos(a) * r0, o.y + sin(a) * r0, o.x + cos(a) * (r0 + U * 0.07f * k), o.y + sin(a) * (r0 + U * 0.07f * k), 0xE6FFFFFF.toInt(), U * 0.01f)
            }
            paint.reset(); paint.isAntiAlias = true; paint.color = 0xFFFFFFFF.toInt(); paint.typeface = art.bold ?: Typeface.DEFAULT_BOLD
            paint.isFakeBoldText = true; paint.textSize = U * 0.09f; paint.textAlign = Paint.Align.CENTER
            c.save(); c.translate(max(U * 0.14f + (if (land) W * 0.44f else 0f), o.x - U * 0.08f), o.y - U * 0.16f); c.rotate(-7f); c.scale(k, k)
            c.drawText("BED!", 0f, 0f, paint); c.restore()
        }
        CoachFx.STEAM -> {
            val o = at(st.ax, st.ay)
            for (i in 0 until 2) {
                val k = (t * 0.35f + i * 0.5f) % 1f
                val x = o.x + (i - 0.5f) * U * 0.04f
                val y = o.y - U * 0.04f - k * U * 0.12f
                path.reset(); path.moveTo(x, y + U * 0.06f); path.cubicTo(x + U * 0.025f, y + U * 0.04f, x - U * 0.025f, y + U * 0.02f, x, y)
                paint.reset(); paint.isAntiAlias = true; paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeWidth = U * 0.012f
                paint.color = alpha(0xFFFFFF, sin(k * PI.toFloat()) * 0.8f); c.drawPath(path, paint)
            }
        }
        CoachFx.NOTES -> {
            val o = at(st.ax, st.ay)
            for (i in 0 until 3) {
                val k = (t * 0.3f + i / 3f) % 1f
                note(c, o.x + i * U * 0.03f + sin(k * 6) * U * 0.02f, o.y - k * U * 0.2f, U * (0.04f + i * 0.008f), sin(k * PI.toFloat()))
            }
        }
        CoachFx.BUBBLES -> for (i in 0 until 6) {
            val k = (t * 0.22f + i / 6f) % 1f
            val o = at(0.2f + (i * 0.41f) % 0.6f, 0.5f - k * 0.6f)
            val r = U * (0.012f + (i % 3) * 0.006f)
            val a = sin(k * PI.toFloat())
            paint.reset(); paint.isAntiAlias = true; paint.color = alpha(0xFFFFFF, 0.25f * a); c.drawCircle(o.x + sin(k * 8 + i) * U * 0.01f, o.y, r, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = U * 0.004f; paint.color = alpha(0xFFFFFF, 0.9f * a); c.drawCircle(o.x + sin(k * 8 + i) * U * 0.01f, o.y, r, paint)
            paint.style = Paint.Style.FILL; c.drawCircle(o.x + sin(k * 8 + i) * U * 0.01f - r * 0.35f, o.y - r * 0.35f, r * 0.25f, paint)
        }
        CoachFx.SPARKLE, CoachFx.ZEN -> {
            val pts = listOf(0.15f to 0.12f, 0.85f to 0.15f, 0.1f to 0.45f, 0.9f to 0.42f)
            for ((i, f) in pts.withIndex()) {
                val k = 0.5f + 0.5f * sin(t * 1.5f + i * 1.7f)
                val x = if (land) W * 0.44f + f.first * W * 0.56f else f.first * W
                val y = if (land) H * (0.16f + f.second * 0.86f) else H * (0.335f + f.second * 0.665f)
                sparkle(c, x, y, U * 0.025f * (0.4f + 0.6f * k), gold)
            }
        }
        CoachFx.MOON -> {
            // the moon he's gazing at, with a few twinkling stars
            val o = at(st.ax, st.ay)
            val r = U * 0.07f
            val mx = o.x + U * 0.02f
            val my = o.y - r * 1.4f
            path.reset(); path.fillType = Path.FillType.EVEN_ODD
            path.addCircle(mx, my, r, Path.Direction.CW); path.addCircle(mx + r * 0.45f, my - r * 0.35f, r * 0.85f, Path.Direction.CW)
            c.save(); c.clipPath(Path().apply { addCircle(mx, my, r, Path.Direction.CW) })
            paint.reset(); paint.isAntiAlias = true; paint.color = 0xFFFFE9A8.toInt(); c.drawPath(path, paint); c.restore()
            path.fillType = Path.FillType.WINDING
            for (i in 0 until 3) sparkle(c, mx + U * (0.14f + i * 0.09f), my + U * (if (i == 1) -0.05f else 0.03f), U * 0.022f * (0.5f + 0.5f * sin(t * 1.8f + i * 2)), gold)
        }
        CoachFx.JUMPERS -> {
            // little sheep leaping in an arc over his head, one after another
            val mini = art.raw(R.drawable.coach_mini_sheep)
            val head = at(0.47f, 0.0f)
            val span = w * 0.62f
            val lift = minOf(h * 0.36f, head.y - H * 0.06f - U * 0.05f)
            val sw = U * 0.15f
            val sh = sw * mini.height / mini.width
            paint.reset(); paint.isFilterBitmap = true
            for (i in 0 until 3) {
                val k = (t / 3.2f + i / 3f) % 1f
                val x = head.x + (k - 0.5f) * span * 2
                val y = head.y + lift * 0.15f - sin(k * PI.toFloat()) * lift
                val slope = -cos(k * PI.toFloat()) * lift * PI.toFloat() / (span * 2)
                paint.alpha = (255 * minOf(1f, k * 6, (1 - k) * 6)).toInt()
                c.save(); c.translate(x, y); c.rotate(Math.toDegrees(atan2(slope, 1f).toDouble()).toFloat() * 0.7f)
                // the sprite faces left, so flip it to travel left to right
                c.scale(-1f, 1f)
                c.drawBitmap(mini, null, RectF(-sw / 2, -sh / 2, sw / 2, sh / 2), paint)
                c.restore()
            }
        }
        CoachFx.MELT -> {}
    }
}
