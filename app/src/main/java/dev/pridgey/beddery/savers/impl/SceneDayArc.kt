package dev.pridgey.beddery.savers.impl

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.Astronomy
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.Sketch
import dev.pridgey.beddery.savers.SketchScene
import dev.pridgey.beddery.savers.formatDuration
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.mixc
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rgb
import dev.pridgey.beddery.savers.rgba
import dev.pridgey.beddery.savers.sFbm
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sSmooth
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAU = (PI * 2).toFloat()
private const val PIf = PI.toFloat()

/**
 * A valley through the whole day. The sun rides a dotted arc from today's real sunrise to
 * sunset, the moon takes the night arc, and every colour in the land follows the light.
 */
@Composable
fun SceneDayArcSaver(ctx: SaverContext) {
    val context = LocalContext.current
    val time = rememberFrameTime()
    val zone = ZoneId.systemDefault()
    val loc = ctx.settings.weatherLocation
    val (lat, lon) = remember(loc) { loc?.let { it.latitude to it.longitude } ?: Astronomy.guessLocation(zone) }
    val today = ctx.now.toLocalDate()
    val days = remember(today, lat, lon) { (-1..1).map { Astronomy.sunTimes(today.plusDays(it.toLong()), lat, lon, zone) } }
    fun hours(t: LocalDateTime) = Duration.between(today.atStartOfDay(), t).seconds / 3600f
    val sun = days[1]
    val rise = sun.sunrise?.let { hours(it) } ?: 6f
    val set = sun.sunset?.let { hours(it) } ?: 18f
    val now = hours(ctx.now)
    val isDay = now > rise && now < set
    // Tonight's arc runs from today's sunset to tomorrow's sunrise (or yesterday's sunset to today's sunrise).
    val nightFrom = if (now >= set) sun.sunset else days[0].sunset
    val nightTo = if (now >= set) days[2].sunrise else sun.sunrise
    val fromLabel = if (isDay) sun.sunrise else nightFrom
    val toLabel = if (isDay) sun.sunset else nightTo
    val left = if (isDay) sun.sunset else nightTo
    val leftText = left?.let { formatDuration(context, Duration.between(ctx.now, it).toMillis()) } ?: ""
    val labels = ArcLabels(
        fromLabel?.let { formatTimeWithAmPm(it.toLocalTime(), ctx.use24h) } ?: "",
        toLabel?.let { formatTimeWithAmPm(it.toLocalTime(), ctx.use24h) } ?: "",
        if (isDay) stringResource(R.string.dayarc_daylight_left, leftText) else stringResource(R.string.dayarc_sunrise_in, leftText),
    )
    SketchScene(time) { t -> drawDayArc(t, now, rise, set, labels) }
}

private class ArcLabels(val from: String, val to: String, val left: String)

private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
private val lightFace = Typeface.create("sans-serif-light", Typeface.NORMAL)
private val ap0 = Path()
private val ap1 = Path()

private fun pal(keys: Array<Pair<Float, Int>>, l: Float): Int {
    for (i in 1 until keys.size) if (l <= keys[i].first) {
        val (l0, c0) = keys[i - 1]; val (l1, c1) = keys[i]
        return mixc(rgb(c0), rgb(c1), (l - l0) / (l1 - l0))
    }
    return rgb(keys.last().second)
}
private val TopPal = arrayOf(0f to 0x05081a, 0.35f to 0x1c2050, 0.7f to 0x3a6ab0, 1f to 0x4a8ad8)
private val MidPal = arrayOf(0f to 0x0e1430, 0.35f to 0x3a3466, 0.7f to 0x8ab8e0, 1f to 0xa8d4f0)
private val LowPal = arrayOf(0f to 0x1a2244, 0.35f to 0x5a4a78, 0.7f to 0xc8e0ee, 1f to 0xd8ecf4)
private fun Int.rgbOnly() = this and 0xFFFFFF

private fun Sketch.drawDayArc(t: Float, hNow: Float, rise: Float, set: Float, labels: ArcLabels) {
    val h = ((hNow % 24) + 24) % 24
    val isDay = h > rise && h < set
    val dayP = ((h - rise) / (set - rise)).coerceIn(0f, 1f)
    val nightP = ((h - set + 24) % 24) / (24 - (set - rise))
    val sunAlt = if (isDay) sin(dayP * PIf) else -sin(nightP * PIf)
    val L = sSmooth(-0.25f, 0.35f, sunAlt)
    val warm = exp(-(sunAlt / 0.22f).pow(2)) // strongest at sunrise and sunset
    val hz = H * if (land) 0.6f else 0.55f

    // ---- Sky.
    val top = pal(TopPal, L)
    val mid = pal(MidPal, L)
    val low = mixc(pal(LowPal, L), rgb(0xf0905a), warm * 0.85f)
    val midW = mixc(mid, rgb(0xc87a8a), warm * 0.5f)
    rect(0f, 0f, W, hz + 1, vgrad(0f, hz, 0f to top, 0.6f to midW, 1f to low))
    stars(0f, hz * 0.85f, 140, 7, t, 0xEEF0FF, true)
    if (L > 0.05f) {
        val a = (L * 2).coerceIn(0f, 1f)
        rect(0f, 0f, W, hz + 1, vgrad(0f, hz, 0f to rgba(top.rgbOnly(), a), 0.6f to rgba(midW.rgbOnly(), a), 1f to rgba(low.rgbOnly(), a)))
    }

    // ---- The arc.
    val ax0 = W * 0.1f; val ax1 = W * 0.9f
    val acx = (ax0 + ax1) / 2; val arx = (ax1 - ax0) / 2
    val ary = min(hz * if (land) 0.72f else 0.62f, arx * 1.05f)
    val ay = hz - U * 0.01f
    fun ptX(p: Float) = acx - cos(p * PIf) * arx
    fun ptY(p: Float) = ay - sin(p * PIf) * ary
    val p = if (isDay) dayP else nightP
    val bright = L > 0.5f
    val ink = if (bright) rgba(0xFFFFFF, 0.75f) else rgba(0xDCE2FF, 0.6f)
    val faint = if (bright) rgba(0xFFFFFF, 0.35f) else rgba(0xDCE2FF, 0.25f)
    for (i in 0..120) {
        val q = i / 120f
        circle(ptX(q), ptY(q), if (q <= p) 1.4f else 1f, if (q <= p) ink else faint)
    }
    val span = if (isDay) set - rise else 24 - (set - rise)
    val start = if (isDay) rise else set
    var hr = ceil(start).toInt()
    while (hr < start + span) {
        val q = (hr - start) / span
        val x = ptX(q); val y = ptY(q)
        val nx = (x - acx) / arx; val ny = (y - ay) / ary
        val n = hypot(nx, ny).takeIf { it > 0f } ?: 1f
        val big = hr % 6 == 0
        val len = if (big) U * 0.022f else U * 0.012f
        line(x, y, x + nx / n * len, y + ny / n * len, ink, if (big) 1.5f else 1f)
        hr++
    }
    val bx = ptX(p); val by = ptY(p)
    if (isDay) {
        val sunCol = mixc(rgb(0xfff4d0), rgb(0xffb070), warm).rgbOnly()
        glow(bx, by, U * 0.5f, sunCol, 0.35f + 0.2f * warm)
        glow(bx, by, U * 0.12f, sunCol, 0.6f)
        circle(bx, by, U * 0.04f, rgb(sunCol))
    } else moon(bx, by, U * 0.032f, 0xF2F0E6, 0.2f)

    // ---- The valley.
    fun tone(night: Int, day: Int) = mixc(mixc(rgb(night), rgb(day), L), rgb(0xc06a6a), warm * 0.25f)
    ridge(hz - H * 0.02f, H * 0.12f, 4f, 1.6f, tone(0x1a2040, 0x7a9ab8), hz + 2, 0f, true, if (L > 0.4f) rgba(0xFFFFFF, 0.25f) else rgba(0xC8D2FF, 0.2f))
    val hillBase = hz + H * 0.05f
    // Far fields between the mountains and the hill, so the land runs on without a gap.
    rect(0f, hz - 1, W, hillBase + H * 0.1f - hz, vgrad(hz, hillBase + H * 0.05f, 0f to tone(0x161c38, 0x8aa6a0), 1f to tone(0x121a32, 0x6e9468)))
    for (k in 0 until 3) {
        val y = hz + (hillBase - hz) * (0.25f + k * 0.3f)
        rect(0f, y, W, H * 0.004f, if (k % 2 == 0) rgba(0xFFFFFF, 0.04f) else rgba(0, 0.05f))
    }
    mist(hz - H * 0.02f, H * 0.05f, mixc(low, rgb(0xffffff), 0.2f).rgbOnly(), 0.45f)
    val n = ((W + 12) / 3).toInt() + 1
    val hill = FloatArray(n)
    for (i in 0 until n) { val x = -4f + i * 3; hill[i] = hillBase - sFbm((x / W * 2 + 3).toDouble(), 9.0).toFloat() * H * 0.09f }
    fun hillY(x: Float) = hill[((x + 4) / 3).roundToInt().coerceIn(0, n - 1)]
    // The lowest ground under a footprint, so nothing hangs over a dip.
    fun groundY(x0: Float, x1: Float): Float {
        var y = hillY(x0)
        var x = x0
        while (x < x1) { x += 3f; y = max(y, hillY(x)) }
        return y
    }
    ap0.reset(); ap0.moveTo(-4f, H)
    for (i in 0 until n) ap0.lineTo(-4f + i * 3, hill[i])
    ap0.lineTo(-4f + (n - 1) * 3, H); ap0.close()
    fill(ap0, vgrad(hillBase - H * 0.09f, hillBase + H * 0.1f, 0f to tone(0x141c34, 0x6a9a5a), 1f to tone(0x0e1428, 0x4a7a42)))
    // Field strips across the hill.
    c.save(); c.clipPath(ap0)
    for (k in 0 until 6) {
        ap1.reset()
        for (i in 0 until n) { val x = -4f + i * 3; val yy = hill[i] + H * 0.012f * k + sin(x * 0.01f + k) * H * 0.004f; if (i == 0) ap1.moveTo(x, yy) else ap1.lineTo(x, yy) }
        for (i in n - 1 downTo 0) { val x = -4f + i * 3; ap1.lineTo(x, hill[i] + H * 0.012f * (k + 1) + sin(x * 0.01f + k + 1) * H * 0.004f) }
        ap1.close()
        fill(ap1, if (k % 2 == 1) rgba(0xFFFFFF, 0.03f) else rgba(0, 0.06f))
    }
    c.restore()
    // The farm: a house and a barn, lights on after dark.
    val fx = W * if (land) 0.28f else 0.22f
    val fs = U * 0.05f
    val fy = groundY(fx - fs * 0.7f, fx + fs * 1.95f) + U * 0.004f
    // A worn yard under the buildings.
    ellipse(fx + fs * 0.6f, fy, fs * 1.6f, fs * 0.16f, 0f, tone(0x10162a, 0x5a7a3a))
    val roofC = tone(0x141828, 0xa84a3a)
    rect(fx - fs * 0.6f, fy - fs * 0.6f, fs * 1.2f, fs * 0.6f, tone(0x1c2238, 0xe8dcc8))
    ap1.reset(); ap1.moveTo(fx - fs * 0.7f, fy - fs * 0.6f); ap1.lineTo(fx, fy - fs * 1.1f); ap1.lineTo(fx + fs * 0.7f, fy - fs * 0.6f); ap1.close(); fill(ap1, roofC)
    rect(fx + fs * 0.8f, fy - fs * 0.75f, fs * 1.1f, fs * 0.75f, tone(0x1a1e32, 0xb84a3a))
    ap1.reset(); ap1.moveTo(fx + fs * 0.75f, fy - fs * 0.75f); ap1.quadTo(fx + fs * 1.35f, fy - fs * 1.25f, fx + fs * 1.95f, fy - fs * 0.75f); ap1.close(); fill(ap1, roofC)
    val lights = sSmooth(0.35f, 0.15f, L)
    for (wx in floatArrayOf(-0.35f, 0.15f)) {
        rect(fx + wx * fs, fy - fs * 0.45f, fs * 0.2f, fs * 0.2f, if (L < 0.3f) rgba(0xFFC86E, 0.4f + 0.6f * lights) else rgba(0x282832, 0.5f))
        if (lights > 0.1f) glow(fx + wx * fs + fs * 0.1f, fy - fs * 0.35f, fs * 0.8f, 0xffb060, 0.3f * lights)
    }
    // The windmill on the crest, sails turning slowly.
    val wmx = W * if (land) 0.66f else 0.72f
    val ws = U * 0.07f
    val wmy = groundY(wmx - ws * 0.3f, wmx + ws * 0.3f) + U * 0.004f
    ellipse(wmx, wmy, ws * 0.45f, ws * 0.07f, 0f, tone(0x10162a, 0x5a7a3a))
    ap1.reset(); ap1.moveTo(wmx - ws * 0.25f, wmy); ap1.lineTo(wmx - ws * 0.15f, wmy - ws); ap1.lineTo(wmx + ws * 0.15f, wmy - ws); ap1.lineTo(wmx + ws * 0.25f, wmy); ap1.close()
    fill(ap1, tone(0x121828, 0xd8d0c0))
    ap1.reset(); ap1.moveTo(wmx - ws * 0.2f, wmy - ws); ap1.lineTo(wmx, wmy - ws * 1.25f); ap1.lineTo(wmx + ws * 0.2f, wmy - ws); ap1.close()
    fill(ap1, tone(0x0e1220, 0x6a4a3a))
    val hubY = wmy - ws * 1.02f
    for (k in 0 until 4) {
        c.save(); c.translate(wmx, hubY); c.rotate(Math.toDegrees((t * 0.4f + k * PIf / 2).toDouble()).toFloat())
        rect(-ws * 0.025f, 0f, ws * 0.05f, ws * 0.95f, tone(0x141a2c, 0xf0e8dc))
        rect(ws * 0.025f, ws * 0.2f, ws * 0.16f, ws * 0.72f, tone(0x10162a, 0xc8c0b0))
        for (r in 0 until 5) line(ws * 0.025f, ws * (0.25f + r * 0.14f), ws * 0.185f, ws * (0.25f + r * 0.14f), rgba(0, 0.25f), 0.8f)
        c.restore()
    }
    // A row of poplars along the hill.
    for (i in 0 until 9) {
        val x = W * (if (land) 0.4f else 0.34f) + i * W * 0.035f
        val th = U * (0.06f + 0.02f * sHash(i.toFloat()))
        if (abs(x - wmx) < ws * 0.9f) continue // leave the windmill in the clear
        val y = groundY(x - th * 0.16f, x + th * 0.16f) + U * 0.006f
        ellipse(x + th * 0.05f, y, th * 0.2f, th * 0.035f, 0f, rgba(0x0a1018, 0.3f))
        rect(x - th * 0.022f, y - th * 0.2f, th * 0.044f, th * 0.2f, tone(0x0a0e1a, 0x4a3a2a))
        ellipse(x, y - th * 0.62f, th * 0.16f, th * 0.52f, 0f, tone(0x0e1424, 0x3a6a3a))
        ellipse(x - th * 0.04f, y - th * 0.7f, th * 0.08f, th * 0.38f, 0f, if (L > 0.4f) rgba(0xFFFFDC, 0.12f) else rgba(0xA0AAE6, 0.06f))
    }
    // The near meadow: a soft rise, tall grass and a scatter of flowers.
    val mb = H * if (land) 0.8f else 0.74f
    ap0.reset(); ap0.moveTo(-4f, H)
    var x = -4f
    while (x <= W + 8) { ap0.lineTo(x, mb - sin(x / W * 3 + 0.5f) * H * 0.03f); x += 4 }
    ap0.lineTo(W + 8, H); ap0.close()
    fill(ap0, vgrad(mb - H * 0.04f, H, 0f to tone(0x0c1222, 0x5a8a3a), 1f to tone(0x05080f, 0x2a4a22)))
    val flowers = intArrayOf(0xf0e060, 0xf0f0f0, 0xe07aa0)
    for (i in 0 until 160) {
        val gx = sHash(i * 2.3f) * W
        val gy = mb - sin(gx / W * 3 + 0.5f) * H * 0.03f + sHash(i * 3.7f) * H * 0.05f
        val gh = U * (0.02f + 0.03f * sHash(i * 5f))
        val sw = sin(t * 0.8f + gx * 0.02f) * gh * 0.15f
        ap1.reset(); ap1.moveTo(gx, gy); ap1.quadTo(gx + sw * 0.3f, gy - gh * 0.6f, gx + sw, gy - gh)
        stroke(ap1, if (i % 2 == 1) tone(0x0a1020, 0x4a7a32) else tone(0x121a2c, 0x6a9a42), 1f)
        if (sHash(i * 9f) > 0.85f) circle(gx + sw, gy - gh, U * 0.004f, if (L > 0.3f) rgb(flowers[i % 3]) else rgba(0xC8D2F0, 0.4f))
    }
    // Birds by day; fireflies by night.
    if (L > 0.5f) {
        for (i in 0 until 5) {
            val bxx = ((sHash(i * 3f) * W + t * U * 0.03f * (0.6f + sHash(i.toFloat()))) % (W * 1.2f)) - W * 0.1f
            val byy = hz * (0.3f + 0.2f * sHash(i * 7f)) + sin(t + i) * 4
            val f = sin(t * 6 + i * 2) * 3
            ap1.reset(); ap1.moveTo(bxx - 5, byy - f); ap1.quadTo(bxx - 2, byy - 2, bxx, byy); ap1.quadTo(bxx + 2, byy - 2, bxx + 5, byy - f)
            stroke(ap1, rgba(0x282832, 0.55f), 1f)
        }
    } else {
        for (i in 0 until 14) {
            val fx2 = W * sHash(i * 3f) + sin(t * 0.3f + i) * U * 0.04f
            val fy2 = mb - U * 0.05f + cos(t * 0.4f + i * 2) * U * 0.03f + sHash(i * 7f) * U * 0.08f
            val a = max(0f, sin(t * 1.2f + i * 2.1f)) * (1 - L * 2)
            if (a > 0.02f) { circle(fx2, fy2, 1.3f, rgba(0xE6FF96, a)); glow(fx2, fy2, U * 0.015f, 0xc8ff80, 0.4f * a) }
        }
    }

    // ---- Labels: the times at the arc's feet; daylight left, or how long till sunrise.
    txt.typeface = Typeface.DEFAULT; txt.textSize = U * 0.03f
    txt.color = if (L > 0.55f) rgba(0x1E283C, 0.75f) else rgba(0xE6EAFF, 0.75f)
    txt.textAlign = Paint.Align.LEFT; c.drawText(labels.from, ptX(0f) - U * 0.02f, ptY(0f) - U * 0.03f, txt)
    txt.textAlign = Paint.Align.RIGHT; c.drawText(labels.to, ptX(1f) + U * 0.02f, ptY(1f) - U * 0.03f, txt)
    txt.textAlign = Paint.Align.CENTER; txt.typeface = lightFace; txt.textSize = U * 0.036f
    c.drawText(labels.left, acx, ay - ary - U * 0.06f, txt)
    grain(0.05f)
}

