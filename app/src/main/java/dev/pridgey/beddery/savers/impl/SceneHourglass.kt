package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.Sketch
import dev.pridgey.beddery.savers.SketchScene
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rgb
import dev.pridgey.beddery.savers.rgba
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sLerp
import dev.pridgey.beddery.savers.sSmooth
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val PIf = PI.toFloat()

/** Seconds since midnight at frame time 0, so the glasses keep real time. Shifts let a tap flip a glass. */
private class GlassClock(start: Float) {
    var base = start
    val shift = FloatArray(3)
}

private fun secondsNow(): Float { val n = LocalTime.now(); return n.toSecondOfDay() + n.nano / 1e9f }

/** A single hourglass under a spotlight on polished wood. It drains over each hour and turns over on the hour. Tap to turn it now. */
@Composable
fun SceneHourglassSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val clock = remember { GlassClock(secondsNow()).also { it.base -= time.value } }
    val cooldown = remember { Cooldown(8_000) }
    OnSaverTap { if (cooldown.tryAcquire()) { val into = (clock.base + clock.shift[1] + time.value) % 3600f; clock.shift[1] += 3600f - into } }
    val g = remember { GlassDraw() }
    SketchScene(time) { t -> drawHourglassScene(t, clock, g) }
}

/** Day, hour and minute hourglasses grouped like a still life. Each drains over its span and turns over when it runs out. Tap to turn the minute glass. */
@Composable
fun SceneThreeHourglassesSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val clock = remember { GlassClock(secondsNow()).also { it.base -= time.value } }
    val cooldown = remember { Cooldown(4_000) }
    OnSaverTap { if (cooldown.tryAcquire()) { val into = (clock.base + clock.shift[2] + time.value) % 60f; clock.shift[2] += 60f - into } }
    val g = remember { GlassDraw() }
    SketchScene(time) { t -> drawThreeHourglasses(t, clock, g) }
}

/** Looks: wood (light, mid, dark), sand (dark, light, mid, stream), metal fittings (dark, light, shade). */
private class Look(val wood: IntArray, val sand: IntArray, val metal: IntArray)
private val Walnut = Look(intArrayOf(0x8a5630, 0xc8905a, 0x3a2010), intArrayOf(0xc8964e, 0xe8c07a, 0xa87a3e, 0xd8a860), intArrayOf(0x8a6a2a, 0xf0d080, 0x5a4018))
private val Ebony = Look(intArrayOf(0x3a2a22, 0x6a4a3a, 0x140c08), intArrayOf(0xa8b8c8, 0xe0e8f0, 0x8898a8, 0xc8d4e0), intArrayOf(0x8a6a2a, 0xf0d080, 0x5a4018))
private val Ash = Look(intArrayOf(0xb89068, 0xe8c8a0, 0x6a4a30), intArrayOf(0xc86a5a, 0xf0a088, 0xa8504a, 0xe08a70), intArrayOf(0x8a8a90, 0xf0f0f4, 0x4a4a50))

/** The turn-over: the glass lifts, turns half a revolution and settles back down. */
private fun flipLift(into: Float, dur: Float) = sin(PIf * sSmooth(0f, 1f, (into / dur).coerceIn(0f, 1f))) * 0.12f
private fun flipAngle(into: Float, dur: Float) = PIf * sSmooth(0.15f, 0.85f, (into / dur).coerceIn(0f, 1f))

private fun Sketch.drawHourglassScene(t: Float, clock: GlassClock, gd: GlassDraw) {
    val secs = clock.base + clock.shift[1] + t
    val sinceTurn = ((secs % 3600f) + 3600f) % 3600f
    val q = sinceTurn / 3600f
    val hh = if (land) H * 0.78f else min(H * 0.58f, W * 1.6f)
    val hx = if (land) W * 0.3f else W * 0.5f
    val base = if (land) H * 0.88f else H * 0.07f + hh

    rect(0f, 0f, W, H, vgrad(0f, H, 0f to rgb(0x0c0907), 0.6f to rgb(0x140e0a), 1f to rgb(0x0a0705)))
    glow(hx, base - hh * 0.55f, hh * 1.1f, 0xc88a4a, 0.22f, sy = 1.2f)
    rect(0f, base, W, H - base, vgrad(base, H, 0f to rgb(0x2a1a10), 0.2f to rgb(0x1a100a), 1f to rgb(0x080504)))
    rect(0f, base, W, 1.2f, rgba(0xFFC88C, 0.18f))
    glow(hx, base + U * 0.01f, hh * 0.9f, 0xffb070, 0.18f, sy = 0.08f)
    tableGrain(base, 10, U * 0.03f)

    val turning = sinceTurn < 3f
    val lift = if (turning) flipLift(sinceTurn, 3f) else 0f
    val ang = if (turning) flipAngle(sinceTurn, 3f) else 0f
    val settle = if (turning) 1f else sSmooth(3f, 4.5f, sinceTurn)
    val draw: Sketch.() -> Unit = {
        c.save(); c.translate(hx, base - hh / 2 - lift * hh); c.rotate(Math.toDegrees(ang.toDouble()).toFloat())
        gd.body(this, hh, if (turning) 1f else q, t, turning, Walnut, ang, settle)
        c.restore()
    }
    gd.reflection(this, hx - hh * 0.6f, hx + hh * 0.6f, base, hh * 0.55f, 0.22f, draw)
    glow(hx, base, hh * 0.3f, 0, 0.55f * (1 - lift * 4), additive = false, sy = 0.08f)
    draw()
    dust(t, hx, base, hh * 0.9f, hh * 1.1f, hh * 0.6f, 0.6f)
    vignette(hx, base - hh * 0.5f, hh * 0.4f, 0.6f)
    grain(0.05f)
}

private class GlassSpec(val look: Look, val span: Float, val flip: Float, val x: Float, val baseOff: Float, val h: Float, val shiftIdx: Int)

private fun Sketch.drawThreeHourglasses(t: Float, clock: GlassClock, gd: GlassDraw) {
    val now = clock.base + t
    val G = if (land) H * 0.66f else min(W * 1.15f, H * 0.6f)
    val cx = if (land) W * 0.3f else W * 0.54f
    val back = if (land) H * 0.56f else H * 0.48f
    val books = G * 0.11f
    val glasses = arrayOf(
        GlassSpec(Ebony, 86400f, 4f, -0.17f, G * 0.06f - books, 0.62f, 0),
        GlassSpec(Walnut, 3600f, 3f, 0.2f, G * 0.14f, 0.52f, 1),
        GlassSpec(Ash, 60f, 1.6f, -0.02f, G * 0.27f, 0.34f, 2),
    )
    val lightX = cx + G * 0.05f
    val lightY = back - G * 0.35f

    rect(0f, 0f, W, back, vgrad(0f, back, 0f to rgb(0x0a0806), 1f to rgb(0x16100b)))
    glow(lightX, lightY, G * 0.9f, 0xc88a4a, 0.2f, sy = 1.1f)
    rect(0f, back, W, H - back, vgrad(back, H, 0f to rgb(0x2e1d12), 0.25f to rgb(0x1c120b), 1f to rgb(0x080504)))
    rect(0f, back, W, 1.2f, rgba(0xFFC88C, 0.16f))
    glow(lightX, back + G * 0.18f, G * 0.75f, 0xffb070, 0.2f, sy = 0.22f)
    tableGrain(back, 14, (H - back) / 15)

    // The books under the day glass: two old cloth-bound volumes, slightly askew.
    val d = glasses[0]
    val dx = cx + d.x * G
    val by = back + d.baseOff
    val bw = d.h * G * 0.62f
    glow(dx, by + books, bw * 0.7f, 0, 0.5f, additive = false, sy = 0.12f)
    book(dx + bw * 0.04f, by + books, bw * 1.1f, books * 0.55f, 0f, 0x3a2a40, 0xd8c8a8)
    book(dx - bw * 0.02f, by + books * 0.45f, bw, books * 0.45f, -0.035f, 0x5a2a24, 0xe0d0b0)

    for (g in glasses) {
        val hh = g.h * G
        val gx = cx + g.x * G
        val gb = back + g.baseOff
        val into = (((now + clock.shift[g.shiftIdx]) % g.span) + g.span) % g.span
        val turning = into < g.flip
        val lift = if (turning) flipLift(into, g.flip) else 0f
        val ang = if (turning) flipAngle(into, g.flip) else 0f
        val settle = if (turning) 1f else sSmooth(g.flip, g.flip + 1.4f, into)
        val draw: Sketch.() -> Unit = {
            c.save(); c.translate(gx, gb - hh / 2 - lift * hh); c.rotate(Math.toDegrees(ang.toDouble()).toFloat())
            gd.body(this, hh, if (turning) 1f else into / g.span, t, turning, g.look, ang, settle)
            c.restore()
        }
        if (g !== d) gd.reflection(this, gx - hh * 0.6f, gx + hh * 0.6f, gb, hh * 0.5f, 0.2f, draw)
        glow(gx, gb, hh * 0.32f, 0, 0.55f * (1 - lift * 4), additive = false, sy = 0.08f)
        draw()
    }
    dust(t, lightX, back + G * 0.2f, G * 0.9f, G * 0.9f, Float.MAX_VALUE, 0.5f)
    vignette(lightX, back, G * 0.35f, 0.65f)
    grain(0.05f)
}

private val hp = Path()

private fun Sketch.tableGrain(top: Float, n: Int, step: Float) {
    for (k in 0 until n) {
        hp.reset()
        val y0 = top + U * 0.02f + k * step
        var x = -4f
        while (x <= W + 4) { val y = y0 + sin(x * 0.01f + k) * U * 0.004f; if (x < 0) hp.moveTo(x, y) else hp.lineTo(x, y); x += 8 }
        stroke(hp, rgba(0, 0.24f), 1f)
    }
}

private fun Sketch.dust(t: Float, cx: Float, base: Float, spreadX: Float, spreadY: Float, falloff: Float, k: Float) {
    for (i in 0 until 40) {
        val px = cx + (sHash(i * 3f) - 0.5f) * spreadX + sin(t * 0.05f + i) * U * 0.03f
        val py = base - ((sHash(i * 7f) * spreadY + t * U * 0.003f * (0.5f + sHash(i.toFloat()))) % spreadY)
        val a = 0.5f * sin(((t * 0.07f + sHash(i * 5f)) % 1f) * PIf) * (if (falloff == Float.MAX_VALUE) 1f else sSmooth(falloff, 0f, abs(px - cx)))
        if (a > 0.01f) rectAdd(px, py, 1.3f, 1.3f, rgba(0xFFDCAA, a * k))
    }
}

private fun Sketch.vignette(x: Float, y: Float, r0: Float, a: Float) {
    val far = max(W, H) * 0.85f
    rect(0f, 0f, W, H, rgrad(x, y, far, 0f to rgba(0, 0f), r0 / far to rgba(0, 0f), 1f to rgba(0, a)))
}

private fun Sketch.book(x: Float, y: Float, w: Float, h: Float, rot: Float, cloth: Int, page: Int) {
    c.save(); c.translate(x, y); c.rotate(Math.toDegrees(rot.toDouble()).toFloat())
    rect(-w / 2 + w * 0.03f, -h * 0.85f, w * 0.94f, h * 0.7f, rgb(page))
    for (k in 1 until 5) line(-w / 2 + w * 0.04f, -h * 0.85f + h * 0.7f * k / 5, w / 2 - w * 0.04f, -h * 0.85f + h * 0.7f * k / 5, rgba(0x786446, 0.5f), 1f)
    rect(-w / 2, -h, w, h * 0.17f, rgb(cloth)); rect(-w / 2, -h * 0.17f, w, h * 0.17f, rgb(cloth))
    rect(-w / 2 - w * 0.02f, -h, w * 0.05f, h, rgb(cloth))
    rect(-w / 2, -h, w, 1.2f, rgba(0xFFDCA0, 0.25f))
    rect(-w / 2 - w * 0.02f, -h * 0.7f, w * 0.05f, h * 0.06f, rgba(0xDCB45A, 0.7f)); rect(-w / 2 - w * 0.02f, -h * 0.4f, w * 0.05f, h * 0.06f, rgba(0xDCB45A, 0.7f))
    c.restore()
}

/** Draws the hourglass and its reflections, with reusable paths and buffers. */
private class GlassDraw {
    private val upper = Path()
    private val lower = Path()
    private val p = Path()
    private val ptsUp = FloatArray(41 * 2)
    private val ptsDn = FloatArray(41 * 2)
    private var reflBmp: Bitmap? = null
    private var reflCanvas: Canvas? = null
    private val reflPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val fadePaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val src = Rect()
    private val dst = RectF()

    /** A mirror image of [draw], reflected in a polished surface at [base] and faded to nothing over [fade]. */
    fun reflection(s: Sketch, x0: Float, x1: Float, base: Float, fade: Float, alpha: Float, draw: Sketch.() -> Unit) {
        val k = min(2f, s.density)
        val w = max(1, ceil((x1 - x0) * k).toInt())
        val h = max(1, ceil(fade * k).toInt())
        var b = reflBmp
        if (b == null || b.width < w || b.height < h) {
            b?.recycle()
            b = Bitmap.createBitmap(max(w, b?.width ?: 0), max(h, b?.height ?: 0), Bitmap.Config.ARGB_8888)
            reflBmp = b; reflCanvas = Canvas(b)
        }
        val rc = reflCanvas!!
        rc.drawColor(0, PorterDuff.Mode.CLEAR)
        rc.save()
        rc.translate(-x0 * k, base * k); rc.scale(k, -k)
        val screen = s.c
        s.c = rc
        s.draw()
        s.c = screen
        rc.restore()
        fadePaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), -0x1000000, 0, Shader.TileMode.CLAMP)
        rc.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fadePaint)
        src.set(0, 0, w, h); dst.set(x0, base, x1, base + fade)
        reflPaint.alpha = (alpha * 255).roundToInt()
        s.c.drawBitmap(b, src, dst, reflPaint)
    }

    /** The hourglass, centred at the origin, [h] tall; [q] is how much of the sand has fallen.
     *  Lighting stays fixed to the room as the glass turns ([angle]); [settle] eases the freshly
     *  turned sand from a heap into a flat surface. */
    fun body(s: Sketch, h: Float, q: Float, t: Float, turning: Boolean, look: Look, angle: Float, settle: Float) = with(s) {
        val plateW = h * 0.5f; val plateH = h * 0.06f; val gw = h * 0.21f; val neck = h * 0.018f
        val top = -h / 2 + plateH; val bot = h / 2 - plateH
        val ca = cos(angle); val sa = sin(angle)
        fun wgrad(cx: Float, cy: Float, half: Float, vararg stops: Pair<Float, Int>) = lgrad(cx - ca * half, cy + sa * half, cx + ca * half, cy - sa * half, *stops)
        val face = ca * ca
        val lit = if (ca >= 0) -1f else 1f

        fun bez(p0x: Float, p0y: Float, p1x: Float, p1y: Float, p2x: Float, p2y: Float, p3x: Float, p3y: Float, u: Float, out: FloatArray, i: Int) {
            val v = 1 - u
            out[i * 2] = v * v * v * p0x + 3 * v * v * u * p1x + 3 * v * u * u * p2x + u * u * u * p3x
            out[i * 2 + 1] = v * v * v * p0y + 3 * v * v * u * p1y + 3 * v * u * u * p2y + u * u * u * p3y
        }
        fun side(dir: Float, out: FloatArray) {
            val y0 = if (dir < 0) top else bot
            for (i in 0..20) bez(-neck, 0f, -neck * 1.4f, dir * h * 0.05f, -gw * 1.05f, dir * h * 0.1f, -gw, dir * h * 0.27f, i / 20f, out, i)
            for (i in 1..20) bez(-gw, dir * h * 0.27f, -gw * 0.97f, dir * h * 0.372f, -gw * 0.76f, y0, -gw * 0.6f, y0, i / 20f, out, 20 + i)
        }
        side(-1f, ptsUp); side(1f, ptsDn)
        fun bulb(pts: FloatArray, path: Path) {
            path.reset(); path.moveTo(pts[0], pts[1])
            for (i in 1..40) path.lineTo(pts[i * 2], pts[i * 2 + 1])
            for (i in 40 downTo 0) path.lineTo(-pts[i * 2], pts[i * 2 + 1])
            path.close()
        }
        bulb(ptsUp, upper); bulb(ptsDn, lower)

        rect(-h * 0.015f, top, h * 0.03f, bot - top, rgb(look.wood[2]))
        for (b in arrayOf(upper, lower)) {
            fill(b, rgba(0xBED7E1, 0.1f))
            c.save(); c.clipPath(b)
            rect(-gw * 1.2f, -h, gw * 2.4f, h * 2, wgrad(0f, 0f, gw, 0f to rgba(0x0A1418, 0.4f), 0.25f to rgba(0x0A1418, 0f), 0.75f to rgba(0x0A1418, 0f), 1f to rgba(0x0A1418, 0.4f)))
            c.restore()
        }

        // ---- Sand.
        val sandFill = wgrad(0f, 0f, gw, 0f to rgb(look.sand[0]), 0.4f to rgb(look.sand[1]), 1f to rgb(look.sand[2]))
        val pw = gw * 1.1f
        fun heap(ph: Float, x: Float): Float { val u = min(1f, abs(x) / pw); return ph * (1 - 0.5f * u * u) }
        val fullPh = h * 0.36f
        val left = 1 - q
        val e = sSmooth(0f, 1f, settle)
        if (left > 0.002f) {
            c.save(); c.clipPath(upper)
            val level = sLerp(-h * 0.03f, top + h * 0.02f, Math.pow(left.toDouble(), 0.6).toFloat())
            val dip = h * 0.035f * sSmooth(0f, 0.2f, left) * sSmooth(1f, 0.9f, left) * e
            val surf = sLerp(top, level, e)
            p.reset(); p.moveTo(-gw * 1.2f, surf); p.quadTo(0f, surf + dip * 2, gw * 1.2f, surf)
            for (i in 0..24) { val x = gw * 1.2f - i / 24f * gw * 2.4f; p.lineTo(x, sLerp(heap(fullPh, x) - bot, 0f, e)) }
            p.close(); fill(p, sandFill)
            c.restore()
        }
        val ph = fullPh * Math.pow(q.toDouble(), 0.7).toFloat()
        if (q > 0f) {
            c.save(); c.clipPath(lower)
            p.reset(); p.moveTo(-gw * 1.2f, bot)
            for (i in 0..24) { val x = -gw * 1.2f + i / 24f * gw * 2.4f; p.lineTo(x, bot - heap(ph, x)) }
            p.lineTo(gw * 1.2f, bot); p.close(); fill(p, sandFill)
            p.reset(); p.moveTo(lit * pw * 0.5f, bot - heap(ph, pw * 0.5f)); p.lineTo(0f, bot - ph); p.lineTo(lit * pw * 0.1f, bot - ph * 0.82f); p.close()
            fill(p, rgba(0xFFF0C8, 0.25f * face))
            c.restore()
        }
        // The falling thread of sand, glinting, and grains bouncing where it lands.
        if (q < 0.999f && left > 0f && !turning) {
            val land2 = bot - ph
            rect(-0.8f, 0f, 1.6f, land2, rgba(look.sand[3], e))
            for (k in 0 until 8) rect(-0.9f, (t * h * 0.6f + k * h * 0.05f) % land2, 1.8f, 1.5f, rgba(0xFFF0C8, 0.75f * e))
            for (k in 0 until 10) {
                val p2 = (t * 2.2f + k / 10f) % 1f
                val sd = if (sHash(k * 3f) > 0.5f) 1f else -1f
                val sp = h * (0.02f + 0.03f * sHash(k * 7f))
                rect(sd * sp * p2 - 0.6f, land2 - sin(p2 * PIf) * h * 0.015f + p2 * p2 * h * 0.012f - 0.6f, 1.2f, 1.2f, rgba(0xF0C882, 0.9f * (1 - p2) * e))
            }
        }
        // Fine grains speckled over the sand.
        for (b in arrayOf(upper, lower)) {
            c.save(); c.clipPath(b)
            for (k in 0 until 220) {
                val gx = (sHash(k * 3.7f) - 0.5f) * gw * 2.1f
                val gy = (sHash(k * 5.1f) - 0.5f) * h
                rect(gx, gy, 1f, 1f, if (sHash(k.toFloat()) > 0.5f) rgba(0xFFEBBE, 0.2f) else rgba(0x785028, 0.18f))
            }
            c.restore()
        }

        // ---- Glass highlights, following each bulb's curve just inside its edge.
        for (pts in arrayOf(ptsUp, ptsDn)) {
            for (hl in 0..1) {
                val sideSign = if (hl == 0) lit else -lit
                val a = if (hl == 0) 0.4f else 0.16f
                val w = if (hl == 0) 0.013f else 0.006f
                val inset = if (hl == 0) 0.8f else 0.84f
                val from = if (hl == 0) 9 else 12
                val to = if (hl == 0) 34 else 30
                p.reset()
                for (i in from..to) { val xx = -sideSign * pts[i * 2] * inset; val yy = pts[i * 2 + 1]; if (i == from) p.moveTo(xx, yy) else p.lineTo(xx, yy) }
                stroke(p, rgba(0xFFF2E0, a * face), h * w, round = true)
            }
        }
        stroke(upper, rgba(0xE1F0F5, 0.55f), max(1f, h * 0.006f))
        stroke(lower, rgba(0xE1F0F5, 0.55f), max(1f, h * 0.006f))
        glow(0f, 0f, h * 0.04f, 0xfff0d0, 0.35f)

        // ---- Front spindles: a turned profile with beads at each end and in the middle.
        fun prof(u: Float): Float {
            fun bead(cc: Float, w: Float, a: Float) = a * kotlin.math.exp(-((u - cc) / w) * ((u - cc) / w))
            return h * (0.012f + bead(0.06f, 0.03f, 0.009f) + bead(0.94f, 0.03f, 0.009f) + bead(0.5f, 0.05f, 0.008f) + 0.003f * sin(u * PIf))
        }
        for (sx in floatArrayOf(-plateW * 0.42f, plateW * 0.42f)) {
            p.reset()
            for (k in 0..40) { val u = k / 40f; val y = sLerp(top, bot, u); if (k == 0) p.moveTo(sx - prof(u), y) else p.lineTo(sx - prof(u), y) }
            for (k in 40 downTo 0) { val u = k / 40f; p.lineTo(sx + prof(u), sLerp(top, bot, u)) }
            p.close()
            fill(p, wgrad(sx, 0f, h * 0.022f, 0f to rgb(look.wood[0]), 0.35f to rgb(look.wood[1]), 1f to rgb(look.wood[2])))
        }
        // ---- End plates: stacked turned mouldings; the edge facing up catches the light.
        for ((y, dir) in arrayOf(-h / 2 to 1, h / 2 - plateH to -1)) {
            for (tier in Tiers) {
                val o = tier[0]; val th = tier[1]; val wf = tier[2]
                val ty = if (dir > 0) y + plateH * o else y + plateH * (1 - o - th)
                val tw = plateW * wf; val tt = plateH * th
                roundRect(-tw / 2, ty, tw, tt + 0.5f, tt * 0.5f, wgrad(0f, ty, tw / 2, 0f to rgb(look.wood[0]), 0.28f to rgb(look.wood[1]), 0.55f to rgb(look.wood[0]), 1f to rgb(look.wood[2])))
                rect(-tw / 2 + plateH * 0.2f, if (ca >= 0) ty + 0.5f else ty + tt - 1.5f, tw - plateH * 0.4f, 1f, rgba(0xFFE1B4, 0.4f * face))
            }
            for (k in 0 until 5) { val yy = y + plateH * (0.38f + k * 0.06f); line(-plateW * 0.48f, yy, plateW * 0.48f, yy, rgba(0x3C1E0A, 0.3f), 0.8f) }
        }
        for (sx in floatArrayOf(-plateW * 0.42f, plateW * 0.42f)) for (fy in floatArrayOf(top, bot - h * 0.02f)) {
            rect(sx - h * 0.026f, fy, h * 0.052f, h * 0.02f, wgrad(sx, 0f, h * 0.026f, 0f to rgb(look.metal[0]), 0.35f to rgb(look.metal[1]), 1f to rgb(look.metal[2])))
        }
    }

    companion object {
        private val Tiers = arrayOf(floatArrayOf(0f, 0.3f, 0.88f), floatArrayOf(0.3f, 0.45f, 1f), floatArrayOf(0.75f, 0.25f, 0.92f))
    }
}

