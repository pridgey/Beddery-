package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.Sketch
import dev.pridgey.beddery.savers.SketchScene
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rgba
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sLerp
import dev.pridgey.beddery.savers.sSmooth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

private const val TAU = (PI * 2).toFloat()
private const val PIf = PI.toFloat()

/**
 * The deep: a moon jelly and a sea nettle swimming in climbs and slow sinks, ghosts far off,
 * a big jelly drifting past close to the glass. Now and then a far jelly glows in colour and
 * the moon jelly flashes round its rim. Tap to make both happen.
 */
@Composable
fun SceneJellyfishSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val state = remember { JellyState() }
    val cooldown = remember { Cooldown(15_000) }
    OnSaverTap { if (cooldown.tryAcquire()) state.tapAt = time.value }
    SketchScene(time) { t -> drawJellyfish(t, state) }
}

private class JellyState {
    var tapAt = -1000f
    val p = Array(4) { Path() }
    val rect = RectF()
    // The near jelly is drawn small into its own bitmap and scaled up: soft focus, cheaply.
    var buf: Bitmap? = null
    var bufCanvas: Canvas? = null
    val bufPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val src = Rect()
    val dst = RectF()
}

private class Swim(var x: Float = 0f, var y: Float = 0f, var tilt: Float = 0f, var pulse: Float = 0f)

/**
 * A jelly's swim cycle. It climbs from a resting point with a few pulses, heading at an angle
 * toward where the next climb starts, then relaxes and sinks there, tilting upright again.
 */
private fun jellySwim(out: Swim, t: Float, hx: Float, hy: Float, sx: Float, sy: Float, d: Float, period: Float, pulses: Int, sink: Float, ph: Float, seed: Float): Swim {
    val swim = period * pulses
    val total = swim + sink
    val c = floor((t + ph) / total)
    val u = (t + ph) - c * total
    fun restX(k: Float) = hx + (sHash(seed + k * 13) - 0.5f) * sx
    fun restY(k: Float) = hy + (sHash(seed + k * 29) - 0.5f) * sy
    val s0x = restX(c); val s0y = restY(c); val s1x = restX(c + 1); val s1y = restY(c + 1)
    val theta = asin(((s1x - s0x) / d).coerceIn(-0.45f, 0.45f))
    val ax = s0x + d * sin(theta)
    val ay = s0y - d * cos(theta)
    if (u < swim) {
        val n = floor(u / period)
        val k = (u - n * period) / period
        val prog = (n + sSmooth(0f, 0.45f, k)) / pulses
        out.x = sLerp(s0x, ax, prog); out.y = sLerp(s0y, ay, prog)
        out.tilt = theta * sSmooth(0f, 1.5f, u)
        out.pulse = if (k < 0.28f) sSmooth(0f, 0.28f, k) else 1 - sSmooth(0.28f, 1f, k)
    } else {
        val q = (u - swim) / sink
        val e = sSmooth(0f, 1f, q)
        out.x = sLerp(ax, s1x, e) + sin(q * PIf) * d * 0.08f * (if (theta == 0f) 1f else sign(theta))
        out.y = sLerp(ay, s1y, e)
        out.tilt = theta * sSmooth(0.5f, 0f, q)
        out.pulse = 0.15f * sin(q * PIf) // the bell relaxes open as it drifts down
    }
    return out
}

/** Pulse: a quick contraction then a slow relaxation, 0..1. */
private fun jellyPulse(t: Float, ph: Float, period: Float = 3.4f): Float {
    val k = ((t + ph) / period) % 1f
    return if (k < 0.28f) sSmooth(0f, 0.28f, k) else 1 - sSmooth(0.28f, 1f, k)
}

private val GlowColors = intArrayOf(0xff5ab4, 0x5affc8, 0xb48cff, 0x5ac8ff, 0xffd25a)
private val mSwim = Swim()
private val nSwim = Swim()
private val wSwim = Swim()

private fun Sketch.drawJellyfish(t: Float, s: JellyState) {
    val p = s.p
    val sinceTap = t - s.tapAt

    // ---- The deep: a faint glow from the surface far above, black below.
    rect(0f, 0f, W, H, vgrad(0f, H, 0f to rgba(0x0a2a48, 1f), 0.35f to rgba(0x061830, 1f), 0.7f to rgba(0x030b1a, 1f), 1f to rgba(0x010308, 1f)))
    // Shafts of light reaching down from far above, slowly shifting. Three nested passes soften each edge.
    for (i in 0 until 6) {
        val x = W * (0.1f + i * 0.17f) + sin(t * 0.05f + i * 2) * W * 0.04f
        val w0 = W * (0.03f + 0.04f * sHash(i.toFloat()))
        val w1 = w0 * 4
        val a = (0.05f + 0.03f * sin(t * 0.13f + i * 1.7f)) * 0.45f
        val g = vgrad(0f, H * 0.75f, 0f to rgba(0x78BEE6, a), 1f to rgba(0x78BEE6, 0f))
        for (k in floatArrayOf(1f, 0.66f, 0.33f)) {
            val sh = p[0]; sh.reset()
            sh.moveTo(x - w0 * k, 0f); sh.lineTo(x + w0 * k, 0f); sh.lineTo(x + w1 * k + W * 0.1f, H * 0.75f); sh.lineTo(x - w1 * k + W * 0.1f, H * 0.75f); sh.close()
            fillAdd(sh, g)
        }
    }

    // ---- Marine snow: specks drifting, a few big soft ones close to us.
    for (i in 0 until 90) {
        val depth = sHash(i * 3f)
        val x = ((sHash(i * 7f) * W + t * U * 0.004f * (0.5f + depth)) % (W + 20)) - 10
        val y = ((sHash(i * 11f) * H - t * U * 0.006f * (0.3f + depth) + H * 10) % (H + 20)) - 10
        circle(x, y, 0.5f + depth * 1.4f, rgba(0xC8E1F0, 0.08f + 0.25f * depth))
    }
    for (i in 0 until 6) {
        val x = ((sHash(i * 19f) * W + t * U * 0.012f) % (W + U * 0.2f)) - U * 0.1f
        val y = ((sHash(i * 23f) * H - t * U * 0.01f + H * 10) % (H + U * 0.2f)) - U * 0.1f
        glow(x, y, U * (0.03f + 0.03f * sHash(i.toFloat())), 0xa8d0e8, 0.08f)
    }

    // ---- The two big jellies swim: a few strong pulses carry each one up at an angle, then
    // it relaxes and sinks back down, tilting upright again, before the next climb.
    fun moonSwim(out: Swim, tt: Float) = jellySwim(out, tt, W * (if (land) 0.74f else 0.66f), H * (if (land) 0.44f else 0.3f), W * 0.22f, H * 0.06f, U * 0.26f, 3.4f, 4, 11f, 0.7f, 3f)
    fun nettleSwim(out: Swim, tt: Float) = jellySwim(out, tt, W * (if (land) 0.3f else 0.34f), H * (if (land) 0.44f else 0.46f), W * 0.18f, H * 0.05f, U * 0.3f, 3.8f, 4, 12f, 9.5f, 17f)
    fun lunge(tt: Float, ph: Float, step: Float): Float { val k = (((tt + ph) / 3.4f) % 1f + 1f) % 1f; return step * (sSmooth(0f, 0.45f, k) - k) }

    // A school of small fish crossing far back, once a minute or so.
    run {
        val q = ((t + 20) % 75f) / 30f
        if (q < 1f) {
            val fade = sin(q * PIf)
            val fish = p[0]; fish.reset()
            for (i in 0 until 22) {
                val x = W * (1.15f - q * 1.3f) + (sHash(i * 3f) - 0.5f) * U * 0.25f + sin(t * 0.8f + i) * U * 0.01f
                val y = H * (if (land) 0.2f else 0.14f) + (sHash(i * 7f) - 0.5f) * U * 0.12f + sin(q * 6 + sHash(i.toFloat()) * 3) * U * 0.03f
                val l = U * (0.012f + 0.006f * sHash(i * 5f))
                s.rect.set(x - l, y - l * 0.3f, x + l, y + l * 0.3f)
                fish.addOval(s.rect, Path.Direction.CW)
                fish.moveTo(x + l * 0.8f, y); fish.lineTo(x + l * 1.4f, y - l * 0.35f); fish.lineTo(x + l * 1.4f, y + l * 0.35f); fish.close()
            }
            fill(fish, rgba(0x143250, 0.55f * fade))
        }
    }
    // Distant ones: small pale ghosts. A rare moment: about once every minute and a half (or on a tap),
    // one of them glows softly in colour.
    val cyc = floor((t + 50) / 95f).toInt()
    val e = ((t + 50) % 95f) / 9f
    val tapE = sinceTap / 9f
    for (i in 0 until 5) {
        val x = W * (0.1f + 0.2f * i + 0.05f * sHash(i.toFloat())) + sin(t * 0.05f + i * 2) * W * 0.06f + sin(t * 0.13f + i * 4) * W * 0.015f
        val y = H * (0.15f + 0.5f * sHash(i * 5f)) + sin(t * 0.07f + i * 3.4f) * H * 0.04f + lunge(t, i * 1.3f, U * 0.012f)
        val r = U * (0.022f + 0.015f * sHash(i * 3f))
        ghostJelly(x, y, r, t, i * 1.3f)
        var f = 0f
        var col = 0
        if (e < 1f && cyc % 5 == i) { f = sin(e * PIf).pow(1.5f); col = GlowColors[cyc % 5] }
        val tapIdx = ((s.tapAt * 7).toInt() % 5 + 5) % 5
        if (tapE in 0f..1f && i == tapIdx) { f = max(f, sin(tapE * PIf).pow(1.5f)); col = GlowColors[(tapIdx + 2) % 5] }
        if (f > 0f) {
            f *= 0.85f + 0.15f * sin(t * 2.4f)
            val by = y + r * 0.2f
            glow(x, by, r * 5, col, 0.22f * f)
            glow(x, by, r * 1.8f, col, 0.6f * f)
            glow(x, by + r * 1.2f, r * 1.4f, col, 0.25f * f, sy = 1.6f)
        }
    }
    // Swimming stirs up a faint glowing wake of plankton behind each jelly.
    fun wake(nettle: Boolean, r: Float, seed: Int) {
        for (k in 0 until 26) {
            val life = 7f
            val age = (t + k * (life / 26)) % life
            val born = t - age
            if (nettle) nettleSwim(wSwim, born) else moonSwim(wSwim, born)
            val n = floor(born / (life / 26)).toInt() * 7 + k + seed
            val x = wSwim.x + (sHash(n.toFloat()) - 0.5f) * r * 1.4f
            val y = wSwim.y + r * (0.5f + 0.6f * sHash(n * 3f)) + age * U * 0.004f
            val a = sSmooth(0f, 0.5f, age) * (1 - age / life) * 0.55f
            rectAdd(x, y, 1.4f, 1.4f, rgba(0x78DCFF, a))
            if (sHash(n * 5f) > 0.75f) glow(x, y, U * 0.008f, 0x60d0ff, a * 0.6f)
        }
    }
    // The moon jelly, which now and then (or on a tap) lights a ripple of blue round its rim.
    run {
        wake(false, U * 0.16f, 1)
        moonSwim(mSwim, t)
        val r = U * 0.16f
        c.save(); c.translate(mSwim.x, mSwim.y); c.rotate(Math.toDegrees(mSwim.tilt.toDouble()).toFloat())
        moonJelly(p, 0f, 0f, r, t, 0.7f, true, mSwim.pulse)
        var re = ((t + 12) % 40f) / 4f
        if (sinceTap in 0f..4f) re = sinceTap / 4f
        if (re < 1f) {
            val by = r * 0.25f
            val fade = sin(re * PIf)
            glow(0f, by - r * 0.15f, r * 1.4f, 0x58d8ff, 0.28f * fade, sy = 0.6f)
            for (k in 0 until 40) {
                val q = k / 39f
                val a = PIf + q * PIf
                val wave = exp(-((q - re * 1.6f + 0.3f) / 0.07f).pow(2)) + exp(-((1 - q - re * 1.6f + 0.3f) / 0.07f).pow(2))
                val px = cos(a) * r * 0.98f
                val py = by + sin(a) * r * 0.06f
                circle(px, py, U * 0.004f, rgba(0x96F0FF, min(1f, wave) * fade))
                if (wave > 0.6f) glow(px, py, U * 0.014f, 0x60e0ff, 0.35f * fade)
            }
        }
        c.restore()
    }
    // The sea nettle, the warm accent, nearest of the swimmers.
    run {
        wake(true, U * 0.17f, 9)
        nettleSwim(nSwim, t)
        c.save(); c.translate(nSwim.x, nSwim.y); c.rotate(Math.toDegrees(nSwim.tilt.toDouble()).toFloat())
        seaNettle(p, 0f, 0f, U * (if (land) 0.11f else 0.12f) * 1.4f, t, nSwim.pulse)
        c.restore()
    }

    // A big moon jelly drifting right past the glass, softly out of focus.
    run {
        val q = (t / 90f) % 1f
        val alpha = 0.55f * sin(q * PIf)
        if (alpha > 0.01f) {
            val x = W * (1.12f - q * 0.45f)
            val y = H * (if (land) 0.82f else 0.8f) - q * H * 0.12f
            val r = U * 0.34f
            val k = 0.45f // buffer pixels per unit: low enough to blur
            val bw = ceil(r * 2.6f * k).toInt()
            val bh = ceil(r * 2.9f * k).toInt()
            var buf = s.buf
            if (buf == null || buf.width != bw || buf.height != bh) {
                buf?.recycle()
                buf = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                s.buf = buf
                s.bufCanvas = Canvas(buf)
            }
            val bc = s.bufCanvas!!
            bc.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR)
            bc.save()
            bc.translate(bw / 2f, r * 0.9f * k)
            bc.scale(k, k)
            val screen = c
            c = bc
            moonJelly(p, 0f, 0f, r, t, 2.2f, true, null)
            c = screen
            bc.restore()
            s.src.set(0, 0, bw, bh)
            s.dst.set(x - r * 1.3f, y - r * 0.9f, x - r * 1.3f + bw / k, y - r * 0.9f + bh / k)
            s.bufPaint.alpha = (alpha * 255).toInt()
            c.drawBitmap(buf, s.src, s.dst, s.bufPaint)
        }
    }

    // A vignette deepens the dark.
    rect(0f, 0f, W, H, rgrad(W / 2, H * 0.4f, max(W, H) * 0.75f, 0f to rgba(0, 0f), 0.4f to rgba(0, 0f), 1f to rgba(0, 0.55f)))
    grain(0.04f)
}

/** A bell, seen from the side: a dome with a softly scalloped rim. */
private fun bellPath(out: Path, cx: Float, cy: Float, w: Float, h: Float, scallops: Int, wob: Float): Path {
    out.reset()
    out.moveTo(cx - w, cy)
    out.cubicTo(cx - w, cy - h * 1.15f, cx + w, cy - h * 1.15f, cx + w, cy)
    for (i in scallops downTo 0) {
        val x = cx - w + 2 * w * i / scallops
        val y = cy + h * 0.08f * abs(sin(i * 1.3f + wob)) + h * 0.06f * sin(i.toFloat() / scallops * PIf)
        out.lineTo(x, y)
    }
    out.close()
    return out
}

/** A moon jelly: a wide glassy saucer, four violet rings inside, a fringe of fine tentacles. */
private fun Sketch.moonJelly(p: Array<Path>, x: Float, y: Float, r: Float, t: Float, ph: Float, detail: Boolean, pulse: Float?) {
    val c0 = pulse ?: jellyPulse(t, ph)
    val w = r * (1 - 0.14f * c0)
    val h = r * (0.55f + 0.14f * c0)
    val by = y - c0 * r * 0.05f
    val q = p[1]
    if (detail) {
        // Fine tentacles from the rim, trailing.
        for (i in 0 until 36) {
            val fx = x - w + 2 * w * (i + 0.5f) / 36
            val len = r * (0.35f + 0.25f * sHash(i.toFloat()))
            q.reset(); q.moveTo(fx, by + h * 0.05f)
            q.quadTo(fx + sin(t * 1.4f + i) * r * 0.05f, by + len * 0.5f, fx + sin(t * 1.1f + i * 0.7f) * r * 0.08f, by + len)
            stroke(q, -1, 0.7f, shader = vgrad(by, by + len, 0f to rgba(0xBED7FF, 0.35f), 1f to rgba(0xBED7FF, 0f)))
        }
        // Four short frilly oral arms.
        for (i in 0 until 4) {
            val ax = x + (i - 1.5f) * w * 0.14f
            q.reset(); q.moveTo(ax - r * 0.03f, by)
            for (st in 0..10) q.lineTo(ax + sin(st * 1.2f + t * 1.3f + i) * r * 0.03f + r * 0.03f * (1 - st / 10f), by + r * 0.4f * st / 10)
            for (st in 10 downTo 0) q.lineTo(ax + sin(st * 1.2f + t * 1.3f + i) * r * 0.03f - r * 0.03f * (1 - st / 10f), by + r * 0.4f * st / 10)
            q.close()
            fill(q, rgba(0xD2BEF0, 0.28f))
        }
    }
    // The bell: glassy, brighter at the margin, with a soft inner glow.
    glow(x, by - h * 0.4f, r * 1.6f, 0x6aa8ff, 0.12f)
    val bell = bellPath(p[2], x, by, w, h, 16, t * 0.5f + ph)
    fill(bell, rgrad(x, by - h * 0.45f, w * 1.1f, 0f to rgba(0xDCEBFF, 0.18f), 0.7f to rgba(0xAAC8FF, 0.22f), 1f to rgba(0xC8DCFF, 0.5f)))
    if (detail) {
        c.save(); c.clipPath(bell)
        // Four horseshoe gonads, overlapping in a row, foreshortened toward the edges.
        for (i in 0 until 4) {
            val off = (i - 1.5f) / 1.5f
            val gx = x + off * w * 0.42f
            val gy = by - h * 0.38f
            val rw = w * 0.17f * (1 - 0.35f * abs(off))
            val rh = h * 0.2f
            glow(gx, gy, rw * 1.8f, 0xe08ce8, 0.12f)
            q.reset(); val rr = RectF(gx - rw, gy - rh, gx + rw, gy + rh); q.addOval(rr, Path.Direction.CW)
            stroke(q, rgba(0xE6A0EB, 0.2f), r * 0.06f)
        }
        // Radial canals.
        for (i in 0 until 12) {
            val a = -PIf + (i + 0.5f) / 12 * PIf
            line(x, by - h * 0.5f, x + cos(a) * w * 1.05f, by + sin(a) * h * 0.1f - h * 0.05f * abs(cos(a)), rgba(0xC8DCFF, 0.12f), 0.8f)
        }
        c.restore()
    }
    // Rim light, and a highlight across the crown kept inside the bell.
    stroke(bell, rgba(0xD2E6FF, 0.55f), 1.2f)
    c.save(); c.clipPath(bell)
    c.save(); c.translate(x - w * 0.1f, by - h * 0.55f); c.rotate(Math.toDegrees(-0.1).toFloat())
    q.reset(); q.addArc(RectF(-w * 0.6f, -h * 0.42f, w * 0.6f, h * 0.42f), 180f * 1.12f, 180f * 0.38f)
    stroke(q, rgba(0xFFFFFF, 0.3f), r * 0.03f, round = true)
    c.restore()
    c.restore()
}

/** A far-off jelly: only its glow and the hint of a bell and trailing threads. */
private fun Sketch.ghostJelly(x: Float, y: Float, r: Float, t: Float, ph: Float) {
    val c0 = jellyPulse(t, ph)
    val by = y - c0 * r * 0.05f
    glow(x, by - r * 0.2f, r * 2.2f, 0x7ab8ff, 0.1f)
    val w = r * (1 - 0.12f * c0)
    c.save(); c.clipRect(x - w - 1, by - r, x + w + 1, by)
    ellipse(x, by, w, r * 0.55f, 0f, rgba(0xAACDFF, 0.16f))
    c.restore()
    val g = vgrad(by, by + r * 1.6f, 0f to rgba(0xAACDFF, 0.18f), 1f to rgba(0xAACDFF, 0f))
    val q = android.graphics.Path()
    for (i in 0 until 5) {
        val fx = x - r * 0.7f + r * 1.4f * i / 4
        q.moveTo(fx, by); q.quadTo(fx + sin(t + i) * r * 0.2f, by + r * 0.8f, fx + sin(t * 0.8f + i) * r * 0.3f, by + r * 1.6f)
    }
    stroke(q, -1, 0.6f, shader = g)
}

/** A sea nettle: an amber bell with dark stripes, long fine tentacles and creamy ribbon arms. */
private fun Sketch.seaNettle(p: Array<Path>, x: Float, y: Float, r: Float, t: Float, pulse: Float?) {
    val c0 = pulse ?: jellyPulse(t, 1.9f, 3.8f)
    val w = r * (0.8f - 0.1f * c0)
    val h = r * (0.7f + 0.12f * c0)
    val by = y - c0 * r * 0.06f
    val q = p[1]
    // Long, fine tentacles that follow the swimming with a delay.
    for (i in 0 until 16) {
        val fx = x - w * 0.95f + 1.9f * w * (i + 0.5f) / 16
        val len = r * (2.6f + 1.2f * sHash(i * 3f))
        q.reset(); q.moveTo(fx, by)
        for (st in 1..24) {
            val f = st / 24f
            q.lineTo(fx + sin(t * 1.2f - f * 5 + i) * r * 0.12f * f + (x - fx) * 0.3f * f + sin(t * 0.3f + i) * r * 0.2f * f * f, by + len * f)
        }
        stroke(q, -1, 1f, shader = vgrad(by, by + len, 0f to rgba(0xC85A46, 0.6f), 0.6f to rgba(0xA04646, 0.25f), 1f to rgba(0xA04646, 0f)))
    }
    // Four long oral arms: creamy, ruffled ribbons twisting slowly.
    val edge = p[3]
    for (i in 0 until 4) {
        val len = r * (2.2f + 0.4f * sHash(i * 7f))
        q.reset(); edge.reset()
        val lx = FloatArray(31); val rx = FloatArray(31); val ys = FloatArray(31)
        for (st in 0..30) {
            val f = st / 30f
            val cx = x + (i - 1.5f) * w * 0.12f + sin(t * 0.9f - f * 4 + i * 1.6f) * r * 0.16f * f + sin(t * 0.25f + i) * r * 0.15f * f * f
            val half = r * 0.07f * (1 - f * 0.75f) * (0.6f + 0.4f * abs(sin(f * 26 - t * 1.5f + i)))
            lx[st] = cx - half; rx[st] = cx + half; ys[st] = by + len * f
        }
        q.moveTo(lx[0], ys[0]); edge.moveTo(lx[0], ys[0])
        for (st in 1..30) { q.lineTo(lx[st], ys[st]); edge.lineTo(lx[st], ys[st]) }
        for (st in 30 downTo 0) q.lineTo(rx[st], ys[st])
        q.close()
        fill(q, vgrad(by, by + len, 0f to rgba(0xFFE2C4, 0.6f), 0.7f to rgba(0xF0BEA0, 0.3f), 1f to rgba(0xF0BEA0, 0f)))
        stroke(edge, rgba(0xFFF0DC, 0.3f), 0.7f)
    }
    // The bell: amber glass, striped, glowing warm against the blue.
    glow(x, by - h * 0.4f, r * 1.8f, 0xff9a50, 0.14f)
    val bell = bellPath(p[2], x, by, w, h, 22, t * 0.6f)
    fill(bell, rgrad(x - w * 0.1f, by - h * 0.5f, w * 1.2f, 0f to rgba(0xFFD696, 0.85f), 0.6f to rgba(0xF09650, 0.7f), 1f to rgba(0xC85A3C, 0.75f)))
    c.save(); c.clipPath(bell)
    // Dark radiating stripes.
    q.reset()
    for (i in 0 until 16) {
        val a = -PIf + (i + 0.5f) / 16 * PIf
        q.moveTo(x, by - h * 0.9f)
        q.lineTo(x + cos(a - 0.05f) * w * 1.3f, by + sin(a - 0.05f) * h * 0.1f + h * 0.1f)
        q.lineTo(x + cos(a + 0.05f) * w * 1.3f, by + sin(a + 0.05f) * h * 0.1f + h * 0.1f)
        q.close()
    }
    fill(q, rgba(0x8C3228, 0.45f))
    // A darker band where the bell turns under at the rim.
    rect(x - w * 1.2f, by - h * 0.2f, w * 2.4f, h * 0.4f, vgrad(by - h * 0.2f, by + h * 0.1f, 0f to rgba(0x782828, 0f), 1f to rgba(0x782828, 0.5f)))
    c.restore()
    stroke(bell, rgba(0xFFC896, 0.6f), 1.2f)
    c.save(); c.clipPath(bell)
    c.save(); c.translate(x - w * 0.15f, by - h * 0.5f); c.rotate(Math.toDegrees(-0.15).toFloat())
    q.reset(); q.addArc(RectF(-w * 0.6f, -h * 0.48f, w * 0.6f, h * 0.48f), 180f * 1.12f, 180f * 0.36f)
    stroke(q, rgba(0xFFF5E6, 0.45f), r * 0.035f, round = true)
    c.restore()
    c.restore()
}
