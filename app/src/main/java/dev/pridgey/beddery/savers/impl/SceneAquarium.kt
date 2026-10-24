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
import dev.pridgey.beddery.savers.mixc
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rgb
import dev.pridgey.beddery.savers.rgba
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sLerp
import dev.pridgey.beddery.savers.sSmooth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

private const val TAU = (PI * 2).toFloat()
private const val PIf = PI.toFloat()

/**
 * The screen is the front glass of a planted nature tank: stone and spiderwood, stem plants,
 * a moss carpet, neon tetras, cherry shrimp, a toy diver puffing air, and every so often a
 * showpiece fish gliding through. Tap to call the next showpiece fish.
 */
@Composable
fun SceneAquariumSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val state = remember { AquaState() }
    val cooldown = remember { Cooldown(20_000) }
    OnSaverTap {
        val t = time.value + state.visitorOffset
        val into = (t + 18) % 50f
        // Only when no visitor is passing: start the next one now.
        if (into > 23f && cooldown.tryAcquire()) state.visitorOffset += 50f - into
    }
    SketchScene(time) { t -> drawAquarium(t, state) }
}

private class AquaState {
    var visitorOffset = 0f
    var key = ""
    var bg: Bitmap? = null
    var over: Bitmap? = null
    val stems = HashMap<Int, Bitmap>()
    val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    val src = Rect()
    val dst = RectF()

    fun reset(newKey: String) {
        key = newKey
        bg?.recycle(); bg = null
        over?.recycle(); over = null
        stems.values.forEach { it.recycle() }; stems.clear()
    }
}

// Scratch paths and rects for drawing; scenes draw on one thread.
private val ap = Array(10) { Path() }
private val ar = RectF()

/** Renders a layer once into a bitmap at the screen's pixel density. */
private fun Sketch.bake(w: Float, h: Float, draw: Sketch.() -> Unit): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(w * density).toInt().coerceAtLeast(1), ceil(h * density).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val bc = Canvas(bmp)
    bc.scale(density, density)
    val screen = c
    c = bc
    draw()
    c = screen
    return bmp
}

private fun Sketch.drawAquarium(t0: Float, s: AquaState) {
    val t = t0
    val tv = t0 + s.visitorOffset
    val X = { f: Float -> W * f }
    val Y = { f: Float -> H * f }
    val waterTop = -2f
    val S = U
    val M = if (land) -0.12f else 0f // shifts the hardscape onto the left third in landscape
    val TV = min(H, U * 1.5f) // vertical scale for wood and plants
    val key = "${W}x$H@$density"
    if (s.key != key) s.reset(key)

    // The soil line, high at the back left and falling to the front right.
    fun sub(x: Float): Float { val f = x / W; return Y(if (land) 0.74f else 0.77f) + TV * 0.06f * f - TV * 0.016f * sin(f * 6 + 1) }
    val haze = 0x3e8a8c

    // The backlit backdrop and a hazy stand of eelgrass far behind never change: bake them once.
    val bg = s.bg ?: bake(W, H) {
        rect(0f, 0f, W, H, vgrad(0f, H, 0f to rgb(0x0c2e3c), 0.45f to rgb(0x1f5e6c), 0.74f to rgb(0x4e9c9c), 1f to rgb(0x3a7e80)))
        val q = Path()
        for (i in 0 until 70) {
            val x = X(sHash(i * 3.1f) * 1.04f - 0.02f)
            val base = sub(x) + TV * 0.05f
            val h = TV * (0.25f + 0.3f * sHash(i * 5.3f)) * (0.6f + 0.4f * abs(sin(x / W * 3)))
            val lean = (sHash(i * 7.7f) - 0.5f) * S * 0.1f
            q.reset(); q.moveTo(x, base); q.quadTo(x + lean * 0.2f, base - h * 0.6f, x + lean, base - h)
            stroke(q, mixc(rgb(0x2a6650), rgb(haze), 0.45f + 0.25f * sHash(i * 9.1f)), S * (0.008f + 0.006f * sHash(i.toFloat())), round = true)
        }
    }.also { s.bg = it }
    s.dst.set(0f, 0f, W, H)
    c.drawBitmap(bg, null, s.dst, s.bmpPaint)

    // Plants are rooted below the soil line, at the lowest ground under the bunch, so the carpet
    // always covers where they meet the ground.
    fun plantBase(x: Float) = max(max(sub(x - S * 0.06f), sub(x)), sub(x + S * 0.06f)) + TV * 0.05f
    // Stem plants: tall swaying columns, green below and red at the tips.
    stemColumn(s, X(0.07f), plantBase(X(0.07f)), TV * 0.67f, S, t, 1, 0x2e6a3a, 0x5aa24a, 0x3a7a3e, 0x7ac05a)
    stemColumn(s, X(0.15f), plantBase(X(0.15f)), TV * 0.57f, S, t, 2, 0x6a2e2a, 0xc0503a, 0x8a3a2c, 0xe07a4a)
    stemColumn(s, X(0.21f), plantBase(X(0.21f)), TV * 0.49f, S, t, 3, 0x2e6a3a, 0x5aa24a, 0x5a6a2e, 0xa8b84a)
    stemColumn(s, X(0.86f), plantBase(X(0.86f)), TV * 0.53f, S, t, 4, 0x2e6a3a, 0x5aa24a, 0x3a7a3e, 0x7ac05a)
    stemColumn(s, X(0.94f), plantBase(X(0.94f)), TV * 0.61f, S, t, 5, 0x6a2e2a, 0xc0503a, 0x8a3a2c, 0xe07a4a)

    // Spiderwood rising out of the main stone, tufted with moss.
    val tips = FloatArray(16)
    var nt = 0
    for (w in Wood) {
        val (x0, cxF, cyF, x1, y1, w0, w1) = w
        val sx = X(x0 + M)
        val g0 = sub(sx)
        val sy = g0 + TV * 0.02f
        val ccx = X(cxF + M); val ccy = g0 + TV * cyF; val ex = X(x1 + M); val ey = g0 + TV * y1
        limb(sx, sy, ccx, ccy, ex, ey, S * w0, S * w1)
        tips[nt++] = ex; tips[nt++] = ey
        // A side twig from two-thirds of the way up.
        val sv = 0.65f; val u = 1 - sv
        val mx = u * u * sx + 2 * u * sv * ccx + sv * sv * ex
        val my = u * u * sy + 2 * u * sv * ccy + sv * sv * ey
        val dir = if (x1 > x0) 1f else -1f
        val tx = mx + dir * W * 0.09f
        val ty = my - H * 0.05f
        limb(mx, my, mx + dir * W * 0.05f, my - H * 0.01f, tx, ty, S * w1 * 1.4f, S * 0.004f)
        tips[nt++] = tx; tips[nt++] = ty
    }
    for (k in 0 until nt / 2) {
        val x = tips[k * 2]; val y = tips[k * 2 + 1]
        val m = FloatArray(16 * 3)
        for (j in 0 until 16) {
            m[j * 3] = x + (sHash(k * 11f + j) - 0.5f) * S * 0.08f
            m[j * 3 + 1] = y + (sHash(k * 13f + j * 3) - 0.55f) * S * 0.025f
            m[j * 3 + 2] = S * (0.006f + 0.008f * sHash(k + j * 7f))
        }
        blobs(m, rgb(0x2a5a2e), rgb(0x5a9a44), t, 0.2f)
        val fuzz = ap[0]; fuzz.reset()
        for (j in 0 until 14) {
            val fx = x + (sHash(k * 17f + j) - 0.5f) * S * 0.09f
            val fy = y + (sHash(k * 19f + j) - 0.6f) * S * 0.03f
            val a = sHash(k * 23f + j) * TAU
            fuzz.moveTo(fx, fy); fuzz.lineTo(fx + cos(a) * S * 0.012f, fy + sin(a) * S * 0.012f - S * 0.004f)
        }
        stroke(fuzz, rgb(0x4a8a3c), 0.8f)
    }

    // Iwagumi stones, each seated on the lowest ground under its footprint.
    fun seat(x: Float, w: Float) = max(max(sub(x - w * 0.55f), sub(x)), sub(x + w * 0.55f)) + TV * 0.015f
    val st = FloatArray(12)
    fun place(i: Int, x: Float, w: Float, h: Float, lean: Float) {
        stone(x, seat(x, w), w, h, lean)
        st[i * 4] = x; st[i * 4 + 1] = seat(x, w) + h * 0.12f - h; st[i * 4 + 2] = w; st[i * 4 + 3] = h
    }
    place(0, X(0.42f + M), U * 0.3f, U * 0.36f, 0.18f)
    place(1, X(if (land) 0.66f else 0.61f), U * 0.19f, U * 0.2f, -0.12f)
    place(2, X(0.29f + M), U * 0.13f, U * 0.12f, 0.25f)

    // Every so often a showpiece fish passes through, a different one each time:
    // an angelfish, a discus, a betta, then a little group of rainbowfish.
    run {
        val cyc = floor((tv + 18) / 50f).toInt()
        val into = (tv + 18) % 50f
        val kind = cyc % 4
        val dur = floatArrayOf(16f, 18f, 22f, 11f)[kind]
        val q = into / dur
        if (q < 1f) {
            val dir = if (cyc % 2 == 1) -1f else 1f
            val len = U * floatArrayOf(0.2f, 0.17f, 0.16f, 0.1f)[kind]
            val x = if (dir > 0) sLerp(-len * 1.5f, W + len * 1.5f, q) else sLerp(W + len * 1.5f, -len * 1.5f, q)
            val y = Y(if (land) 0.3f else 0.32f) + sin(q * 5 + cyc) * H * 0.025f + (sHash(cyc.toFloat()) - 0.5f) * TV * 0.15f
            when (kind) {
                0 -> angelfish(x, y, len, dir, t)
                1 -> discus(x, y, len, dir, t)
                2 -> betta(x, y, len, dir, t)
                else -> for (k in 0 until 4) rainbowfish(x - dir * k * len * (0.9f + 0.3f * sHash(k.toFloat())), y + (sHash(k * 7f) - 0.5f) * len * 1.4f + sin(t * 0.8f + k) * len * 0.1f, len * (0.85f + 0.25f * sHash(k * 3f)), dir, t + k)
            }
        }
    }

    // Neon tetras schooling through the open water, nearer fish drawn last.
    run {
        val cx = X(0.55f + 0.22f * sin(t * 0.06f) + 0.05f * sin(t * 0.17f))
        val cy = Y(0.36f + 0.1f * sin(t * 0.09f + 1))
        val vx = 0.22f * 0.06f * cos(t * 0.06f) + 0.05f * 0.17f * cos(t * 0.17f)
        for (i in TetraOrder) {
            val d = 0.75f + 0.35f * sHash(i * 5f)
            val ox = (sHash(i * 3f) - 0.5f) * W * 0.32f + sin(t * 0.45f + i) * W * 0.02f
            val oy = (sHash(i * 7f) - 0.5f) * H * 0.2f + sin(t * 0.38f + i * 2) * H * 0.02f
            val v = vx + 0.45f * 0.02f * cos(t * 0.45f + i) * 0.3f
            tetra(cx + ox, cy + oy, S * 0.075f * d, tanh(v / 0.0025f), t, i, d)
        }
    }

    // Cherry shrimp picking over the stones.
    for (i in 0 until 3) {
        val si = if (i == 2) 1 else 0
        val q = sin(t * 0.12f + i * 2.1f)
        val sx = st[si * 4] + q * st[si * 4 + 2] * 0.25f + (i - 0.5f) * st[si * 4 + 2] * 0.2f
        val sy = st[si * 4 + 1] + st[si * 4 + 3] * (0.45f + 0.2f * i) + abs(q) * st[si * 4 + 3] * 0.05f
        shrimp(sx, sy, S * 0.04f, if (cos(t * 0.12f + i * 2.1f) > 0) 1f else -1f, t, i)
    }

    // Soil, with dappled caustics.
    val soil = ap[0]; soil.reset()
    soil.moveTo(0f, H)
    var x = 0f
    while (x <= W + 4) { soil.lineTo(x, sub(x) + TV * 0.02f); x += 4 }
    soil.lineTo(W + 4, H); soil.close()
    fill(soil, vgrad(Y(0.72f), H, 0f to rgb(0x3a2c20), 0.4f to rgb(0x2a1f17), 1f to rgb(0x1a130e)))
    c.save(); c.clipPath(soil)
    for (i in 0 until 70) {
        val cxp = X(sHash(i * 3f) * 1.1f - 0.05f) + sin(t * 0.3f + i) * W * 0.01f
        val cyp = sub(cxp) + TV * (0.02f + 0.12f * sHash(i * 7f))
        val a = (0.5f + 0.5f * sin(t * (0.5f + 0.4f * sHash(i.toFloat())) + i * 2.7f)).pow(3) * 0.16f
        if (a < 0.005f) continue
        ar.set(cxp - W * (0.012f + 0.02f * sHash(i * 9f)), cyp - H * 0.004f, cxp + W * (0.012f + 0.02f * sHash(i * 9f)), cyp + H * 0.004f)
        val o = ap[1]; o.reset(); o.addOval(ar, Path.Direction.CW)
        fill(o, rgba(0xC8FFF0, a), additive = true)
    }
    c.restore()
    // Aquasoil granules pressed against the glass.
    run {
        val gA = ap[1]; gA.reset()
        val gB = ap[2]; gB.reset()
        for (i in 0 until 420) {
            val gx = X(sHash(i * 1.37f))
            val y0 = sub(gx) + TV * 0.06f
            val gy = y0 + (H - y0) * sHash(i * 2.71f)
            (if (i % 2 == 1) gA else gB).addRect(gx, gy, gx + 1.5f, gy + 1.2f, Path.Direction.CW)
        }
        fill(gA, rgba(0x785F46, 0.35f)); fill(gB, rgba(0, 0.3f))
    }
    // Pebbles against the front glass.
    for (i in 0 until 46) {
        val px = X(sHash(i * 13f))
        val py = H - H * (0.012f + 0.07f * sHash(i * 17f))
        val r = S * (0.006f + 0.007f * sHash(i.toFloat()))
        ellipse(px, py, r, r * 0.7f, 0f, rgb(Pebbles[i % 4]))
        ellipse(px - r * 0.2f, py - r * 0.3f, r * 0.5f, r * 0.25f, 0f, rgba(0xFFF0DC, 0.12f))
    }

    // The carpet: a continuous mound underneath, then two batched layers of small leaves.
    run {
        val mound = ap[1]; mound.reset()
        mound.moveTo(0f, sub(0f) + TV * 0.08f)
        x = 0f
        while (x <= W + 4) {
            val f = x / W
            val g = sSmooth(0.06f, 0f, abs(f - 0.43f - M))
            mound.lineTo(x, sub(x) + TV * (0.012f + 0.03f * g) - TV * 0.008f * sin(f * 60))
            x += 3
        }
        x = W + 4
        while (x >= 0) { mound.lineTo(x, sub(x) + TV * 0.075f); x -= 3 }
        mound.close()
        fill(mound, rgb(0x2a5a28))
        val dark = ap[2]; dark.reset()
        val lit = ap[3]; lit.reset()
        for (i in 0 until 320) {
            val f = sHash(i * 5 + 0.5f) * 1.04f - 0.02f
            val gap = sSmooth(0.06f, 0f, abs(f - 0.43f - M)) // a sandy clearing in front of the stones
            if (sHash(i * 2 + 7f) < gap * 0.9f) continue
            val cxp = X(f)
            val cyp = sub(cxp) + TV * (0.02f + 0.05f * sHash(i * 3f).pow(1.5f))
            val r = S * (0.006f + 0.007f * sHash(i * 9f))
            ar.set(cxp - r * 1.2f, cyp - r * 0.7f, cxp + r * 1.2f, cyp + r * 0.7f); dark.addOval(ar, Path.Direction.CW)
            ar.set(cxp - r, cyp - r * 0.62f, cxp + r * 0.6f, cyp + r * 0.18f); lit.addOval(ar, Path.Direction.CW)
        }
        fill(dark, rgb(0x336e2e)); fill(lit, rgb(0x6ab448))
        // Hairgrass tufts poking through.
        for (k in 0 until 9) {
            val bx = X(0.04f + k * 0.115f + 0.03f * sHash(k.toFloat()))
            val by = sub(bx) + TV * 0.06f
            val gA = ap[4]; gA.reset()
            val gB = ap[5]; gB.reset()
            for (i in 0 until 14) {
                val a = (i / 13f - 0.5f) * 0.9f
                val h = TV * (0.05f + 0.05f * sHash(k * 20f + i))
                val sw = sin(t * 0.7f + k + i * 0.3f) * h * 0.12f
                val g = if (i % 3 != 0) gA else gB
                g.moveTo(bx + a * S * 0.02f, by)
                g.quadTo(bx + a * h * 0.4f, by - h * 0.6f, bx + a * h * 0.7f + sw, by - h)
            }
            stroke(gA, rgb(0x5aa848), 1f); stroke(gB, rgb(0x8ad060), 1f)
        }
    }

    // A steady stream of bubbles from an airstone in the back corner.
    val bx = X(if (land) 0.92f else 0.9f)
    for (i in 0 until 60) {
        val life = 2.6f + sHash(i.toFloat()) * 1.6f
        val q = ((t + sHash(i * 5f) * life) % life) / life
        val by = sub(bx) - q * (sub(bx) - waterTop)
        val bxx = bx + (sHash(i * 9f) - 0.5f) * S * 0.02f * (0.4f + q) + sin(q * 14 + i) * S * 0.008f * (0.3f + q)
        val r = S * (0.003f + 0.005f * sHash(i * 3f)) * (0.8f + 0.5f * q)
        bubble(bxx, by, r, 0.55f * sSmooth(0f, 0.08f, q) * sSmooth(1f, 0.94f, q))
    }
    // The toy diver on the soil, puffing air from his helmet every few seconds.
    run {
        val dx = X(if (land) 0.8f else 0.76f)
        val dy = sub(dx) + TV * 0.035f
        val size = U * 0.17f
        diver(dx, dy, size)
        val vx = dx + size * 0.08f
        val vy = dy - size * 1.02f
        for (i in 0 until 9) {
            val q = (((t - i * 0.12f) % 7f + 7f) % 7f) / 3.2f
            if (q >= 1f) continue
            val y = vy - q * (vy - waterTop)
            val xx = vx + sin(q * 10 + i) * S * 0.01f * (0.3f + q)
            bubble(xx, y, S * (0.005f + 0.004f * sHash(i * 3 + 1f)) * (0.8f + 0.6f * q), 0.6f * sSmooth(0f, 0.04f, q) * sSmooth(1f, 0.94f, q))
        }
    }

    // The underside of the surface: a silver sheen and drifting glints.
    rect(0f, 0f, W, H * 0.06f, vgrad(0f, H * 0.06f, 0f to rgba(0xA0DCE6, 0.28f), 1f to rgba(0xA0DCE6, 0f)))
    for (i in 0 until 22) {
        val len = W * (0.03f + 0.04f * sHash(i * 7f))
        val gx = -len + ((sHash(i * 3f) * (W + len) + t * (4 + 4 * sHash(i.toFloat()))) % (W + len))
        rect(gx, waterTop + 2 + sHash(i.toFloat()) * H * 0.015f, len, 1f, rgba(0xE6FCFF, 0.12f + 0.1f * sin(t * 1.3f + i)))
    }
    // Depth shading and the darker edges of the glass, baked into one overlay.
    val over = s.over ?: bake(W, H) {
        rect(0f, 0f, W, H, vgrad(0f, H, 0f to rgba(0x00141E, 0f), 0.6f to rgba(0x00141E, 0.08f), 1f to rgba(0x000C14, 0.35f)))
        rect(0f, 0f, W, H, hgrad(0f, W, 0f to rgba(0x00141E, 0.25f), 0.12f to rgba(0x00141E, 0f), 0.88f to rgba(0x00141E, 0f), 1f to rgba(0x00141E, 0.25f)))
        val far = sqrt(W * W + H * H) * 0.6f
        rect(0f, 0f, W, H, rgrad(W / 2, H * 0.45f, far, 0f to rgba(0x00080E, 0f), U * 0.35f / far to rgba(0x00080E, 0f), 1f to rgba(0x00080E, 0.55f)))
    }.also { s.over = it }
    c.drawBitmap(over, null, s.dst, s.bmpPaint)
    grain(0.04f)
}

private data class Limb(val x0: Float, val cx: Float, val cy: Float, val x1: Float, val y1: Float, val w0: Float, val w1: Float)
private val Wood = arrayOf(
    Limb(0.4f, 0.3f, -0.38f, 0.22f, -0.62f, 0.05f, 0.016f),
    Limb(0.43f, 0.5f, -0.32f, 0.64f, -0.58f, 0.045f, 0.014f),
    Limb(0.38f, 0.26f, -0.1f, 0.12f, -0.3f, 0.035f, 0.01f),
    Limb(0.45f, 0.6f, -0.12f, 0.74f, -0.34f, 0.03f, 0.009f),
)
private val Pebbles = intArrayOf(0x5a4a3a, 0x45382c, 0x6a5a48, 0x3a3028)
// Tetras drawn far to near, so nearer fish overlap farther ones.
private val TetraOrder = (0 until 16).sortedBy { 0.75f + 0.35f * sHash(it * 5f) }.toIntArray()

/** Two-tone cel blobs (x, y, r triples): each a shadowed circle with a lit one nudged up and left, top to bottom. */
private fun Sketch.blobs(list: FloatArray, dark: Int, lit: Int, t: Float, sway: Float) {
    val n = list.size / 3
    val order = (0 until n).sortedBy { list[it * 3 + 1] }
    for (i in order) {
        val y = list[i * 3 + 1]
        val r = list[i * 3 + 2]
        val x = list[i * 3] + sin(t * 0.45f + y * 0.03f) * sway * r
        circle(x, y, r, dark)
        circle(x - r * 0.22f, y - r * 0.28f, r * 0.68f, lit)
    }
}

/**
 * A bunch of stem plants. Drawn once, upright, into a sprite; each frame it is laid down in
 * thin horizontal slices, each shifted a little more toward the top, so the bunch sways.
 */
private fun Sketch.stemColumn(s: AquaState, x: Float, base: Float, h: Float, S: Float, t: Float, seed: Int, dark0: Int, lit0: Int, dark1: Int, lit1: Int) {
    val sw = S * 0.32f
    val sh = h + S * 0.04f
    val spr = s.stems[seed] ?: bake(sw, sh) { drawStems(sw / 2, sh, h, S, seed, dark0, lit0, dark1, lit1) }.also { s.stems[seed] = it }
    val slice = 4f
    val k = spr.height / sh
    var y = 0f
    while (y < sh) {
        val q = ((sh - y) / h).coerceIn(0f, 1f)
        val hgt = min(slice, sh - y)
        val sway = sin(t * 0.5f + seed - q * 1.2f) * S * 0.03f * q * q
        s.src.set(0, (y * k).roundToInt(), spr.width, ((y + hgt) * k).roundToInt().coerceAtMost(spr.height))
        s.dst.set(x - sw / 2 + sway, base - sh + y, x + sw / 2 + sway, base - sh + y + hgt)
        c.drawBitmap(spr, s.src, s.dst, s.bmpPaint)
        y += slice
    }
    s.dst.set(0f, 0f, W, H)
}

private fun Sketch.drawStems(x: Float, base: Float, h: Float, S: Float, seed: Int, dark0: Int, lit0: Int, dark1: Int, lit1: Int) {
    val nodes = ArrayList<FloatArray>()
    for (st in 0 until 5) {
        val sx = x + (st - 2) * S * 0.018f + (sHash(seed * 5f + st) - 0.5f) * S * 0.01f
        val hh = h * (0.7f + 0.3f * sHash(seed * 7f + st))
        val n = max(12, (hh / (S * 0.021f)).roundToInt())
        for (k in 0..n) {
            val q = k / n.toFloat()
            nodes.add(floatArrayOf(sx, base - hh * q, S * (0.026f - 0.008f * q), q, (st * 37 + k).toFloat()))
        }
    }
    // Back leaves first (dark), then front leaves (lit), so each bunch has volume.
    for (pass in 0..1) {
        for (nd in nodes) {
            val k = sSmooth(0.45f, 0.95f, nd[3])
            val col = if (pass == 1) mixc(rgb(lit0), rgb(lit1), k) else mixc(rgb(dark0), rgb(dark1), k)
            val angs = if (pass == 1) LitLeaves else DarkLeaves
            for (a0 in angs) {
                val a = a0 + (sHash(nd[4] * 3 + a0) - 0.5f) * 0.4f - PIf / 2
                val l = nd[2] * if (pass == 1) 0.9f else 1.1f
                ellipse(nd[0] + cos(a) * l * 0.5f, nd[1] + sin(a) * l * 0.5f, l * 0.5f, l * 0.13f, a, col)
            }
        }
    }
}
private val LitLeaves = floatArrayOf(-0.55f, 0.55f)
private val DarkLeaves = floatArrayOf(-1.25f, 1.25f, -0.2f)

/** A tapered, curving branch of driftwood, lit along its upper-left edge. */
private fun Sketch.limb(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, w0: Float, w1: Float) {
    val n = 18
    val a = FloatArray((n + 1) * 2)
    val b = FloatArray((n + 1) * 2)
    for (i in 0..n) {
        val s = i / n.toFloat(); val u = 1 - s
        val px = u * u * x0 + 2 * u * s * cx + s * s * x1
        val py = u * u * y0 + 2 * u * s * cy + s * s * y1
        var dx = 2 * u * (cx - x0) + 2 * s * (x1 - cx)
        var dy = 2 * u * (cy - y0) + 2 * s * (y1 - cy)
        val l = sqrt(dx * dx + dy * dy).takeIf { it > 0f } ?: 1f
        dx /= l; dy /= l
        val w = sLerp(w0, w1, s.pow(0.8f)) / 2 * (1 + 0.15f * sin(s * 19 + x0))
        a[i * 2] = px - dy * w; a[i * 2 + 1] = py + dx * w
        b[i * 2] = px + dy * w; b[i * 2 + 1] = py - dx * w
    }
    val p = ap[6]; p.reset()
    p.moveTo(a[0], a[1])
    for (i in 1..n) p.lineTo(a[i * 2], a[i * 2 + 1])
    for (i in n downTo 0) p.lineTo(b[i * 2], b[i * 2 + 1])
    p.close()
    fill(p, rgb(0x3a281c))
    val h = n / 2
    val lit = if (a[h * 2] - a[h * 2 + 1] < b[h * 2] - b[h * 2 + 1]) a else b
    p.reset(); p.moveTo(lit[0], lit[1])
    for (i in 1..n) p.lineTo(lit[i * 2], lit[i * 2 + 1])
    stroke(p, rgba(0xDCC8AA, 0.4f), 1.2f)
    p.reset()
    var i = 2
    while (i < n - 2) {
        val mx = (a[i * 2] + b[i * 2]) / 2; val my = (a[i * 2 + 1] + b[i * 2 + 1]) / 2
        p.moveTo(mx, my); p.lineTo((mx + a[(i + 2) * 2]) / 2, (my + a[(i + 2) * 2 + 1]) / 2)
        i += 3
    }
    stroke(p, rgba(0, 0.25f), 0.8f)
}

/** A faceted grey stone: lit top-left plane, shadowed right plane and a fissure. */
private fun Sketch.stone(cx: Float, ground: Float, w: Float, h: Float, lean: Float) {
    val base = ground + h * 0.12f
    val pk = cx + lean * w
    val p = ap[6]
    p.reset()
    p.moveTo(cx - w * 0.55f, base); p.lineTo(cx - w * 0.52f, base - h * 0.35f); p.lineTo(cx - w * 0.25f, base - h * 0.82f); p.lineTo(pk, base - h); p.lineTo(cx + w * 0.38f, base - h * 0.62f); p.lineTo(cx + w * 0.55f, base); p.close()
    fill(p, rgb(0x4c5558))
    p.reset(); p.moveTo(cx - w * 0.52f, base - h * 0.35f); p.lineTo(cx - w * 0.25f, base - h * 0.82f); p.lineTo(pk, base - h); p.lineTo(cx - w * 0.02f, base - h * 0.5f); p.lineTo(cx - w * 0.3f, base - h * 0.2f); p.close()
    fill(p, rgb(0x79878a))
    p.reset(); p.moveTo(cx - w * 0.25f, base - h * 0.82f); p.lineTo(pk, base - h); p.lineTo(cx - w * 0.1f, base - h * 0.7f); p.close()
    fill(p, rgb(0x93a2a4))
    p.reset(); p.moveTo(pk, base - h); p.lineTo(cx + w * 0.38f, base - h * 0.62f); p.lineTo(cx + w * 0.55f, base); p.lineTo(cx + w * 0.08f, base); p.lineTo(cx - w * 0.02f, base - h * 0.5f); p.close()
    fill(p, rgba(0x0A1419, 0.35f))
    p.reset(); p.moveTo(cx - w * 0.12f, base - h * 0.74f); p.lineTo(cx - w * 0.04f, base - h * 0.52f); p.lineTo(cx + w * 0.02f, base - h * 0.3f)
    stroke(p, rgba(0x0A1419, 0.4f), 1f)
    p.reset(); p.moveTo(cx - w * 0.52f, base - h * 0.35f); p.lineTo(cx - w * 0.25f, base - h * 0.82f); p.lineTo(pk, base - h)
    stroke(p, rgba(0xDCF0F0, 0.35f), 1f)
}

/** A neon tetra from the side, turning by squashing its width. Far fish fade into the water. */
private fun Sketch.tetra(x: Float, y: Float, len: Float, facing: Float, t: Float, i: Int, depth: Float) {
    val f = (if (facing == 0f) 1f else sign(facing)) * max(0.12f, abs(facing))
    val wag = sin(t * 11 + i * 1.7f) * 0.18f
    val a = (0.55f + (depth - 0.75f) * 1.4f).coerceIn(0.55f, 1f)
    c.save(); c.translate(x, y); c.scale(f, 1f)
    val p = ap[6]
    p.reset(); p.moveTo(-len * 0.3f, 0f); p.lineTo(-len * 0.52f, -len * (0.13f - wag * 0.1f)); p.lineTo(-len * 0.46f, wag * len * 0.05f); p.lineTo(-len * 0.52f, len * (0.13f + wag * 0.1f)); p.close()
    p.moveTo(-len * 0.02f, -len * 0.12f); p.lineTo(-len * 0.1f, -len * 0.2f); p.lineTo(-len * 0.14f, -len * 0.11f); p.close()
    fill(p, rgba(0xDCEBF0, 0.35f * a))
    val body = ap[7]; body.reset()
    body.moveTo(len * 0.5f, 0f)
    body.cubicTo(len * 0.42f, -len * 0.16f, -len * 0.12f, -len * 0.17f, -len * 0.32f, -len * 0.035f)
    body.lineTo(-len * 0.32f, len * 0.035f)
    body.cubicTo(-len * 0.12f, len * 0.17f, len * 0.42f, len * 0.16f, len * 0.5f, 0f)
    body.close()
    fill(body, vgrad(-len * 0.16f, len * 0.16f, 0f to rgba(0x7a8c96, a), 0.5f to rgba(0xc8d4da, a), 1f to rgba(0xeef4f6, a)))
    c.save(); c.clipPath(body)
    rect(-len * 0.4f, len * 0.015f, len * 0.56f, len * 0.2f, rgba(0xe0303a, a))
    rect(-len * 0.34f, -len * 0.055f, len * 0.78f, len * 0.055f, hgrad(-len * 0.32f, len * 0.42f, 0f to rgba(0x2a7cff, a), 1f to rgba(0x6af4ff, a)))
    c.restore()
    glow(len * 0.05f, -len * 0.03f, len * 0.3f, 0x48e0ff, 0.14f * depth)
    circle(len * 0.34f, -len * 0.025f, len * 0.04f, rgb(0x0e0e10))
    c.restore()
}

/** A bubble: a thin bright ring with a fleck of highlight. */
private fun Sketch.bubble(x: Float, y: Float, r: Float, a: Float) {
    if (a <= 0.01f) return
    val p = ap[8]; p.reset()
    p.addCircle(x, y, r, Path.Direction.CW)
    stroke(p, rgba(0xEBFCFF, a), 0.8f)
    rect(x - r * 0.45f, y - r * 0.5f, 1.2f, 1.2f, rgba(0xFFFFFF, a * 0.8f))
}

/** The classic toy deep-sea diver: brass helmet with portholes, puffy suit, lead boots. Glossy plastic. */
private fun Sketch.diver(x: Float, base: Float, s: Float) {
    fun gloss(cx: Float, cy: Float, rx: Float, ry: Float) = ellipse(cx, cy, rx, ry, -0.5f, rgba(0xFFFFFF, 0.35f))
    ellipse(x, base, s * 0.3f, s * 0.05f, 0f, rgba(0, 0.35f))
    for (k in intArrayOf(-1, 1)) roundRect(x + k * s * 0.1f - s * 0.09f + k * s * 0.02f, base - s * 0.1f, s * 0.17f, s * 0.1f, s * 0.03f, rgb(0x2a2e34))
    // Legs and body: a puffy canvas suit, shaded on the right.
    roundRect(x - s * 0.17f, base - s * 0.42f, s * 0.15f, s * 0.34f, s * 0.06f, rgb(0xc8b48a))
    roundRect(x + s * 0.02f, base - s * 0.42f, s * 0.15f, s * 0.34f, s * 0.06f, rgb(0xc8b48a))
    roundRect(x - s * 0.2f, base - s * 0.72f, s * 0.4f, s * 0.38f, s * 0.12f, rgb(0xc8b48a))
    roundRect(x + s * 0.05f, base - s * 0.72f, s * 0.15f, s * 0.38f, s * 0.1f, rgba(0, 0.18f))
    rect(x + s * 0.08f, base - s * 0.42f, s * 0.09f, s * 0.32f, rgba(0, 0.18f))
    // Belt.
    rect(x - s * 0.2f, base - s * 0.42f, s * 0.4f, s * 0.04f, rgb(0x3a2a1e))
    rect(x - s * 0.025f, base - s * 0.425f, s * 0.05f, s * 0.05f, rgb(0xd8a840))
    // Arms: one at his side, one raised in a wave.
    val p = ap[8]
    p.reset(); p.moveTo(x - s * 0.18f, base - s * 0.64f); p.quadTo(x - s * 0.28f, base - s * 0.5f, x - s * 0.24f, base - s * 0.38f)
    p.moveTo(x + s * 0.18f, base - s * 0.64f); p.quadTo(x + s * 0.32f, base - s * 0.72f, x + s * 0.34f, base - s * 0.86f)
    stroke(p, rgb(0xc0ac82), s * 0.1f, round = true)
    circle(x - s * 0.24f, base - s * 0.36f, s * 0.05f, rgb(0x2a2e34))
    circle(x + s * 0.34f, base - s * 0.89f, s * 0.05f, rgb(0x2a2e34))
    gloss(x - s * 0.1f, base - s * 0.62f, s * 0.05f, s * 0.1f)
    // Brass collar and helmet.
    ellipse(x, base - s * 0.73f, s * 0.22f, s * 0.07f, 0f, rgb(0xa87a2a))
    val hy = base - s * 0.88f
    val hr = s * 0.17f
    circle(x, hy, hr, vgrad(hy - hr, hy + hr, 0f to rgb(0xf0c860), 0.6f to rgb(0xc8902e), 1f to rgb(0x8a5a1a)))
    // Front porthole with its grille, and side ports.
    circle(x, hy + hr * 0.08f, hr * 0.5f, rgb(0x6a4a18))
    circle(x, hy + hr * 0.08f, hr * 0.38f, rgb(0x1a3a48))
    p.reset(); p.moveTo(x, hy - hr * 0.3f); p.lineTo(x, hy + hr * 0.46f); p.moveTo(x - hr * 0.38f, hy + hr * 0.08f); p.lineTo(x + hr * 0.38f, hy + hr * 0.08f)
    stroke(p, rgb(0x6a4a18), s * 0.015f)
    circle(x - hr * 0.14f, hy - hr * 0.06f, hr * 0.1f, rgba(0xA0E6FF, 0.5f))
    for (k in intArrayOf(-1, 1)) {
        circle(x + k * hr * 0.82f, hy + hr * 0.05f, hr * 0.2f, rgb(0x6a4a18))
        circle(x + k * hr * 0.82f, hy + hr * 0.05f, hr * 0.12f, rgb(0x1a3a48))
    }
    // Air valve on top.
    rect(x + s * 0.06f, hy - hr * 1.15f, s * 0.04f, hr * 0.3f, rgb(0xc8902e))
    gloss(x - hr * 0.45f, hy - hr * 0.5f, hr * 0.22f, hr * 0.12f)
}

/** A fin: translucent membrane with rays fanning from its base, darker toward the edge. */
private fun Sketch.fin(path: Path, rays: FloatArray, tint: Int, L: Float) {
    fill(path, hgrad(0f, -L * 0.6f, 0f to rgba(tint, 0.82f), 1f to rgba(tint, 0.38f)))
    c.save(); c.clipPath(path)
    val r = ap[9]; r.reset()
    var i = 0
    while (i < rays.size) { r.moveTo(rays[i], rays[i + 1]); r.lineTo(rays[i + 2], rays[i + 3]); i += 4 }
    stroke(r, rgba(0x28282E, 0.28f), max(0.6f, L * 0.006f))
    c.restore()
    stroke(path, rgba(0xF0F4F0, 0.35f), max(0.6f, L * 0.005f))
}

/** Rays fanning from points along a base line (bx0,by0)-(bx1,by1) out to each tip. */
private fun fan(bx0: Float, by0: Float, bx1: Float, by1: Float, vararg tips: Float): FloatArray {
    val n = tips.size / 2
    val out = FloatArray(n * 4)
    for (i in 0 until n) {
        val q = i / (n - 1f)
        out[i * 4] = sLerp(bx0, bx1, q); out[i * 4 + 1] = sLerp(by0, by1, q); out[i * 4 + 2] = tips[i * 2]; out[i * 4 + 3] = tips[i * 2 + 1]
    }
    return out
}

/**
 * An angelfish from the side: a tall, flattened disc with sail-like dorsal and anal fins
 * sweeping back into long points, a lyre tail, trailing ventral threads, and four soft bars.
 */
private fun Sketch.angelfish(x: Float, y: Float, L: Float, dir: Float, t: Float) {
    val sw = sin(t * 2.6f)
    val sw2 = sin(t * 2.6f - 0.8f)
    c.save(); c.translate(x, y); c.scale(dir, 1f); c.rotate(Math.toDegrees(sin(t * 0.7) * 0.04).toFloat())
    val dTipX = -L * 0.5f; val dTipY = -L * (0.98f + 0.03f * sw2)
    val dorsal = ap[0]; dorsal.reset()
    dorsal.moveTo(L * 0.22f, -L * 0.3f)
    dorsal.cubicTo(L * 0.06f, -L * 0.6f, -L * 0.2f, -L * 0.82f, dTipX, dTipY)
    dorsal.cubicTo(-L * 0.42f, -L * 0.62f, -L * 0.36f, -L * 0.3f, -L * 0.3f, -L * 0.04f)
    dorsal.lineTo(0f, -L * 0.1f); dorsal.close()
    val aTipY = L * (0.98f - 0.03f * sw2)
    val anal = ap[1]; anal.reset()
    anal.moveTo(L * 0.18f, L * 0.3f)
    anal.cubicTo(L * 0.04f, L * 0.6f, -L * 0.2f, L * 0.82f, dTipX, aTipY)
    anal.cubicTo(-L * 0.42f, L * 0.62f, -L * 0.36f, L * 0.3f, -L * 0.3f, L * 0.04f)
    anal.lineTo(0f, L * 0.1f); anal.close()
    val tw = sw * 0.06f
    val tail = ap[2]; tail.reset()
    tail.moveTo(-L * 0.3f, -L * 0.07f)
    tail.quadTo(-L * 0.45f, -L * (0.2f + tw), -L * 0.66f, -L * (0.3f + tw))
    tail.quadTo(-L * 0.56f, -L * (0.05f + tw), -L * 0.58f, L * (0f - tw))
    tail.quadTo(-L * 0.56f, L * (0.05f - tw), -L * 0.66f, L * (0.3f - tw))
    tail.quadTo(-L * 0.45f, L * (0.2f - tw), -L * 0.3f, L * 0.07f)
    tail.close()
    fin(dorsal, fan(L * 0.14f, -L * 0.3f, -L * 0.24f, -L * 0.2f, L * 0.05f, -L * 0.55f, -L * 0.1f, -L * 0.75f, -L * 0.25f, -L * 0.87f, dTipX, dTipY, -L * 0.38f, -L * 0.62f, -L * 0.32f, -L * 0.4f), 0xC8CCC4, L)
    fin(anal, fan(L * 0.1f, L * 0.3f, -L * 0.24f, L * 0.2f, L * 0.02f, L * 0.55f, -L * 0.1f, L * 0.75f, -L * 0.25f, L * 0.87f, dTipX, aTipY, -L * 0.38f, L * 0.62f, -L * 0.32f, L * 0.4f), 0xC8CCC4, L)
    fin(tail, fan(-L * 0.3f, -L * 0.06f, -L * 0.3f, L * 0.06f, -L * 0.66f, -L * (0.3f + tw), -L * 0.6f, -L * (0.14f + tw), -L * 0.58f, -tw * L, -L * 0.6f, L * (0.14f - tw), -L * 0.66f, L * (0.3f - tw)), 0xD6DAD4, L)
    // Black bars carry on into the fins.
    c.save(); c.clipPath(dorsal)
    rect(-L * 0.26f, -L, L * 0.1f, L, rgba(0x14141A, 0.6f)); rect(L * 0.01f, -L, L * 0.08f, L, rgba(0x14141A, 0.6f))
    c.restore()
    c.save(); c.clipPath(anal)
    rect(-L * 0.26f, 0f, L * 0.1f, L, rgba(0x14141A, 0.6f)); rect(L * 0.01f, 0f, L * 0.08f, L, rgba(0x14141A, 0.6f))
    c.restore()
    // Ventral filaments: two long threads trailing and rippling.
    val th = ap[3]; th.reset()
    for (k in 0..1) {
        th.moveTo(L * 0.2f, L * 0.24f)
        th.cubicTo(L * (0.16f - k * 0.03f), L * 0.5f, L * (0.03f * sin(t * 2 + k)), L * 0.72f, -L * (0.08f + 0.05f * k) + L * 0.04f * sin(t * 1.6f + k), L * (0.95f + 0.05f * k))
    }
    stroke(th, rgba(0xEBEEEC, 0.6f), max(0.8f, L * 0.008f), round = true)
    // Body: a deep, compressed disc with a pointed snout.
    val body = ap[4]; body.reset()
    body.moveTo(L * 0.44f, -L * 0.02f)
    body.cubicTo(L * 0.4f, -L * 0.14f, L * 0.3f, -L * 0.3f, L * 0.12f, -L * 0.33f)
    body.cubicTo(-L * 0.06f, -L * 0.34f, -L * 0.24f, -L * 0.2f, -L * 0.32f, -L * 0.06f)
    body.lineTo(-L * 0.32f, L * 0.06f)
    body.cubicTo(-L * 0.24f, L * 0.2f, -L * 0.06f, L * 0.34f, L * 0.1f, L * 0.33f)
    body.cubicTo(L * 0.28f, L * 0.3f, L * 0.38f, L * 0.14f, L * 0.44f, L * 0.03f)
    body.close()
    fill(body, vgrad(-L * 0.34f, L * 0.34f, 0f to rgb(0xa9ad9e), 0.35f to rgb(0xe8e8dc), 0.6f to rgb(0xf4f2ea), 1f to rgb(0xb6b8ae)))
    c.save(); c.clipPath(body)
    glow(L * 0.2f, -L * 0.24f, L * 0.28f, 0xe8b860, 0.45f, additive = false)
    glow(-L * 0.02f, L * 0.02f, L * 0.3f, 0x7ad0d8, 0.22f, additive = false, sy = 0.6f)
    // Scales: rows of small arcs.
    val sc = L * 0.04f
    val scales = ap[5]; scales.reset()
    for (row in -8..8) for (col in -8..10) {
        val sx = col * sc + (row % 2) * sc * 0.5f
        val sy = row * sc * 0.8f
        ar.set(sx - sc * 0.5f, sy - sc * 0.5f, sx + sc * 0.5f, sy + sc * 0.5f)
        scales.addArc(ar, 0f, 126f)
    }
    stroke(scales, rgba(0x787C74, 0.25f), max(0.5f, L * 0.004f))
    // Four soft bars: through the eye, mid-body, rear, and at the base of the tail.
    for ((bx, bw) in AngelBars) {
        rect(L * bx - L * bw, -L * 0.4f, L * bw * 2, L * 0.8f, hgrad(L * bx - L * bw, L * bx + L * bw, 0f to rgba(0x16161C, 0f), 0.3f to rgba(0x16161C, 0.85f), 0.7f to rgba(0x16161C, 0.85f), 1f to rgba(0x16161C, 0f)))
    }
    // Lateral line and gill cover.
    val ln = ap[6]; ln.reset()
    ln.moveTo(L * 0.22f, -L * 0.12f); ln.quadTo(0f, -L * 0.22f, -L * 0.26f, -L * 0.06f)
    ln.moveTo(L * 0.2f, -L * 0.2f); ln.quadTo(L * 0.13f, -L * 0.02f, L * 0.2f, L * 0.16f)
    stroke(ln, rgba(0x5A5C56, 0.4f), max(0.6f, L * 0.005f))
    rect(-L, -L, L * 2, L * 2, vgrad(-L * 0.34f, L * 0.34f, 0f to rgba(0xFFFFFF, 0.18f), 0.3f to rgba(0xFFFFFF, 0f), 0.75f to rgba(0, 0f), 1f to rgba(0, 0.22f)))
    c.restore()
    // Pectoral fin, clear and fluttering.
    val pec = ap[6]; pec.reset()
    pec.moveTo(L * 0.14f, 0f)
    pec.quadTo(L * 0.02f, -L * (0.04f + 0.04f * sw), -L * 0.04f, L * (0.06f + 0.03f * sw))
    pec.quadTo(L * 0.06f, L * 0.08f, L * 0.14f, L * 0.04f); pec.close()
    fill(pec, rgba(0xEBF0EC, 0.35f))
    // Eye: gold-ringed red iris, dark pupil, catchlight; a small down-turned mouth.
    val ex = L * 0.3f; val ey = -L * 0.08f; val er = L * 0.045f
    circle(ex, ey, er, rgb(0xc89a3a)); circle(ex, ey, er * 0.75f, rgb(0xb03a2a))
    circle(ex + er * 0.1f, ey, er * 0.48f, rgb(0x0c0c0e))
    circle(ex - er * 0.15f, ey - er * 0.3f, er * 0.18f, rgba(0xFFFFFF, 0.85f))
    val mouth = ap[6]; mouth.reset()
    mouth.moveTo(L * 0.44f, 0f); mouth.quadTo(L * 0.41f, L * 0.03f, L * 0.37f, L * 0.03f)
    stroke(mouth, rgba(0x463C32, 0.6f), max(0.6f, L * 0.006f))
    c.restore()
}
private val AngelBars = arrayOf(0.28f to 0.06f, 0.05f to 0.085f, -0.18f to 0.08f, -0.31f to 0.04f)

/** A discus: a near-perfect disc ringed by a fin frill, turquoise with wavy rust-red stripes, a red eye. */
private fun Sketch.discus(x: Float, y: Float, L: Float, dir: Float, t: Float) {
    val sw = sin(t * 2.2f)
    c.save(); c.translate(x, y); c.scale(dir, 1f); c.rotate(Math.toDegrees(sin(t * 0.6) * 0.03).toFloat())
    val tail = ap[0]; tail.reset()
    tail.moveTo(-L * 0.38f, 0f); tail.lineTo(-L * 0.58f, -L * (0.14f + 0.03f * sw)); tail.quadTo(-L * 0.52f, 0f, -L * 0.58f, L * (0.14f - 0.03f * sw)); tail.close()
    fill(tail, rgba(0x78BEC8, 0.55f))
    // The fin frill: widest top and bottom, thin round the face and the tail, rippling at its edge.
    val frill = ap[1]; frill.reset()
    for (i in 0..48) {
        val a = (i / 48f) * TAU
        val ripple = 1 + 0.015f * sin(a * 7 - t * 3)
        val fw = 0.4f + 0.06f * abs(sin(a)).pow(1.5f)
        val px = cos(a) * L * fw * ripple - L * 0.01f
        val py = sin(a) * L * fw * ripple
        if (i == 0) frill.moveTo(px, py) else frill.lineTo(px, py)
    }
    frill.close()
    fill(frill, rgb(0x2a6a7a))
    val wav = ap[2]
    c.save(); c.clipPath(frill)
    wav.reset()
    for (k in -6..6) {
        var xx = -L * 0.6f
        var first = true
        while (xx <= L * 0.5f) { val yy = k * L * 0.08f + sin(xx / L * 9 + k) * L * 0.015f; if (first) wav.moveTo(xx, yy) else wav.lineTo(xx, yy); first = false; xx += L * 0.04f }
    }
    stroke(wav, rgba(0xAA3C28, 0.4f), L * 0.02f)
    c.restore()
    stroke(frill, rgba(0x78F0FF, 0.55f), max(0.8f, L * 0.01f))
    // Body disc.
    val body = ap[3]; body.reset()
    ar.set(L * 0.02f - L * 0.38f, -L * 0.37f, L * 0.02f + L * 0.38f, L * 0.37f); body.addOval(ar, Path.Direction.CW)
    fill(body, vgrad(-L * 0.36f, L * 0.36f, 0f to rgb(0x3aa8b4), 0.5f to rgb(0x5ac8c8), 1f to rgb(0x2a8090)))
    c.save(); c.clipPath(body)
    wav.reset()
    for (k in -5..5) {
        var xx = -L * 0.4f
        var first = true
        while (xx <= L * 0.42f) { val yy = k * L * 0.07f + sin(xx / L * 10 + k * 1.7f) * L * 0.014f; if (first) wav.moveTo(xx, yy) else wav.lineTo(xx, yy); first = false; xx += L * 0.03f }
    }
    stroke(wav, rgba(0xBE4628, 0.75f), L * 0.03f)
    for (k in -3..4) rect(k * L * 0.09f - L * 0.012f, -L * 0.4f, L * 0.024f, L * 0.8f, rgba(0x142832, 0.18f))
    rect(-L, -L, L * 2, L * 2, vgrad(-L * 0.36f, L * 0.36f, 0f to rgba(0xFFFFFF, 0.2f), 0.35f to rgba(0xFFFFFF, 0f), 1f to rgba(0, 0.2f)))
    c.restore()
    // Face: a dark bar through the red eye, a small mouth.
    rect(L * 0.2f, -L * 0.3f, L * 0.05f, L * 0.6f, rgba(0x1E282E, 0.55f))
    val ex = L * 0.24f; val ey = -L * 0.07f; val er = L * 0.045f
    circle(ex, ey, er, rgb(0xd83a2a)); circle(ex + er * 0.1f, ey, er * 0.5f, rgb(0x101010))
    circle(ex - er * 0.2f, ey - er * 0.3f, er * 0.18f, rgba(0xFFFFFF, 0.8f))
    ellipse(L * 0.38f, L * 0.02f, L * 0.03f, L * 0.02f, 0f, rgb(0x3aa8b4))
    val pec = ap[4]; pec.reset()
    pec.moveTo(L * 0.12f, L * 0.04f); pec.quadTo(0f, L * (0.04f * sw), -L * 0.05f, L * 0.1f); pec.quadTo(L * 0.05f, L * 0.1f, L * 0.12f, L * 0.08f); pec.close()
    fill(pec, rgba(0xC8F0F0, 0.35f))
    c.restore()
}

/** A halfmoon betta: a slim deep-blue body trailing huge, billowing red fins edged in iridescent blue. */
private fun Sketch.betta(x: Float, y: Float, L: Float, dir: Float, t: Float) {
    fun b(k: Float) = sin(t * 1.8f - k)
    c.save(); c.translate(x, y); c.scale(dir, 1f); c.rotate(Math.toDegrees(sin(t * 0.5) * 0.05).toFloat())
    fun finFill(path: Path, edge: Path) {
        fill(path, rgrad(-L * 0.1f, 0f, L * 0.85f, 0f to rgba(0xC8283C, 0.95f), 0.7f to rgba(0xAA1E46, 0.85f), 1f to rgba(0x5A50DC, 0.7f)))
        c.save(); c.clipPath(path)
        val r = ap[9]; r.reset()
        for (k in 0 until 18) {
            val a = -PIf * 0.95f + k / 17f * PIf * 1.9f
            r.moveTo(-L * 0.12f, 0f)
            r.quadTo(-L * 0.12f + cos(a) * L * 0.4f, sin(a) * L * 0.4f + b(k * 0.2f) * L * 0.03f, -L * 0.12f + cos(a) * L * 0.9f, sin(a) * L * 0.9f)
        }
        stroke(r, rgba(0xFF8C96, 0.25f), max(0.6f, L * 0.006f))
        c.restore()
        stroke(edge, rgba(0x78AAFF, 0.7f), max(1f, L * 0.012f))
    }
    // The great tail: a half-moon fan with a scalloped, rippling edge.
    val tail = ap[0]; tail.reset()
    val tailEdge = ap[1]; tailEdge.reset()
    tail.moveTo(-L * 0.2f, -L * 0.06f)
    for (i in 0..30) {
        val a = -PIf * 0.45f + (i / 30f) * PIf * 0.9f
        val r = L * (0.52f + 0.03f * sin(i * 1.3f + t * 2)) * (1 + 0.04f * b(i * 0.3f))
        val px = -L * 0.2f - cos(a) * r * 0.95f
        val py = sin(a) * r * 0.85f + b(1f) * L * 0.04f * cos(a)
        tail.lineTo(px, py)
        if (i == 0) tailEdge.moveTo(px, py) else tailEdge.lineTo(px, py)
    }
    tail.lineTo(-L * 0.2f, L * 0.06f); tail.close()
    // Dorsal: a long flag swept back over the tail.
    val dorsalEdge = ap[2]; dorsalEdge.reset()
    dorsalEdge.moveTo(L * 0.02f, -L * 0.1f)
    dorsalEdge.cubicTo(-L * 0.1f, -L * 0.4f, -L * 0.35f, -L * (0.48f + 0.04f * b(0.5f)), -L * 0.55f, -L * (0.42f + 0.05f * b(1f)))
    val dorsal = ap[3]; dorsal.set(dorsalEdge)
    dorsal.quadTo(-L * 0.35f, -L * 0.2f, -L * 0.2f, -L * 0.08f); dorsal.close()
    // Anal: a deep curtain hanging beneath, trailing back.
    val analEdge = ap[4]; analEdge.reset()
    analEdge.moveTo(L * 0.12f, L * 0.08f)
    analEdge.cubicTo(L * 0.05f, L * 0.45f, -L * 0.25f, L * (0.62f + 0.05f * b(0.8f)), -L * 0.55f, L * (0.55f + 0.05f * b(1.3f)))
    val anal = ap[5]; anal.set(analEdge)
    anal.quadTo(-L * 0.35f, L * 0.25f, -L * 0.2f, L * 0.08f); anal.close()
    finFill(anal, analEdge); finFill(tail, tailEdge); finFill(dorsal, dorsalEdge)
    // Ventral thread.
    val th = ap[6]; th.reset()
    th.moveTo(L * 0.2f, L * 0.08f); th.quadTo(L * 0.18f, L * 0.3f, L * (0.08f + 0.03f * b(2f)), L * 0.42f)
    stroke(th, rgba(0xDC3C50, 0.85f), max(1f, L * 0.018f), round = true)
    // Body: slim, deep blue with an iridescent scale sheen.
    val body = ap[7]; body.reset()
    body.moveTo(L * 0.34f, 0f)
    body.cubicTo(L * 0.3f, -L * 0.1f, L * 0.1f, -L * 0.13f, -L * 0.24f, -L * 0.07f)
    body.lineTo(-L * 0.24f, L * 0.07f)
    body.cubicTo(L * 0.1f, L * 0.13f, L * 0.3f, L * 0.1f, L * 0.34f, 0f); body.close()
    fill(body, vgrad(-L * 0.13f, L * 0.13f, 0f to rgb(0x1a2a70), 0.5f to rgb(0x3050b8), 1f to rgb(0x1a2060)))
    c.save(); c.clipPath(body)
    val sc = L * 0.035f
    val scales = ap[8]; scales.reset()
    for (r in -3..3) for (col in -8..9) {
        val sx = col * sc + (r % 2) * sc * 0.5f
        val sy = r * sc * 0.8f
        ar.set(sx - sc * 0.5f, sy - sc * 0.5f, sx + sc * 0.5f, sy + sc * 0.5f)
        scales.addArc(ar, 0f, 126f)
    }
    stroke(scales, rgba(0x78C8FF, 0.4f), max(0.5f, L * 0.006f))
    glow(L * 0.05f, -L * 0.02f, L * 0.2f, 0x60b0ff, 0.35f, sy = 0.5f)
    c.restore()
    // Pectoral, eye, upturned mouth.
    val pec = ap[6]; pec.reset()
    pec.moveTo(L * 0.16f, L * 0.02f); pec.quadTo(L * 0.08f, L * (0.04f * b(3f)), L * 0.05f, L * 0.07f); pec.close()
    fill(pec, rgba(0xFF788C, 0.5f))
    circle(L * 0.25f, -L * 0.025f, L * 0.028f, rgb(0x101010))
    circle(L * 0.24f, -L * 0.035f, L * 0.009f, rgba(0xFFFFFF, 0.8f))
    line(L * 0.34f, -L * 0.005f, L * 0.3f, -L * 0.02f, rgba(0x0A0A1E, 0.7f), max(0.6f, L * 0.008f))
    c.restore()
}

/** Boeseman's rainbowfish: steel-blue in front blending through a dark band to orange behind. */
private fun Sketch.rainbowfish(x: Float, y: Float, L: Float, dir: Float, t: Float) {
    val sw = sin(t * 6)
    c.save(); c.translate(x, y); c.scale(dir, 1f)
    val fins = ap[0]; fins.reset()
    fins.moveTo(-L * 0.3f, 0f); fins.lineTo(-L * 0.55f, -L * (0.18f + 0.03f * sw)); fins.lineTo(-L * 0.46f, 0f); fins.lineTo(-L * 0.55f, L * (0.18f - 0.03f * sw)); fins.close()
    fins.moveTo(0f, -L * 0.2f); fins.lineTo(-L * 0.08f, -L * 0.3f); fins.lineTo(-L * 0.12f, -L * 0.18f); fins.close()
    fins.moveTo(-L * 0.1f, -L * 0.18f); fins.quadTo(-L * 0.2f, -L * 0.3f, -L * 0.32f, -L * 0.1f); fins.lineTo(-L * 0.24f, -L * 0.08f); fins.close()
    fins.moveTo(L * 0.05f, L * 0.16f); fins.quadTo(-L * 0.12f, L * 0.3f, -L * 0.32f, L * 0.1f); fins.lineTo(-L * 0.24f, L * 0.08f); fins.close()
    fill(fins, rgba(0xF0963C, 0.6f))
    val body = ap[1]; body.reset()
    body.moveTo(L * 0.42f, L * 0.02f)
    body.cubicTo(L * 0.36f, -L * 0.14f, L * 0.15f, -L * 0.24f, -L * 0.05f, -L * 0.2f)
    body.quadTo(-L * 0.24f, -L * 0.14f, -L * 0.32f, -L * 0.04f)
    body.lineTo(-L * 0.32f, L * 0.04f)
    body.quadTo(-L * 0.2f, L * 0.14f, 0f, L * 0.16f)
    body.cubicTo(L * 0.2f, L * 0.17f, L * 0.36f, L * 0.12f, L * 0.42f, L * 0.02f)
    body.close()
    fill(body, hgrad(L * 0.42f, -L * 0.32f, 0f to rgb(0x6a88b8), 0.35f to rgb(0x4a6aa8), 0.5f to rgb(0x2a2a40), 0.62f to rgb(0xe07a2a), 1f to rgb(0xf0a838)))
    c.save(); c.clipPath(body)
    rect(-L, -L, L * 2, L * 2, vgrad(-L * 0.24f, L * 0.17f, 0f to rgba(0xFFFFFF, 0.18f), 0.4f to rgba(0xFFFFFF, 0f), 1f to rgba(0, 0.2f)))
    val st = ap[2]; st.reset()
    for (k in -3..3) { st.moveTo(-L * 0.3f, k * L * 0.045f); st.lineTo(L * 0.4f, k * L * 0.045f - L * 0.02f) }
    stroke(st, rgba(0xFFFFFF, 0.12f), max(0.5f, L * 0.008f))
    c.restore()
    circle(L * 0.3f, -L * 0.02f, L * 0.04f, rgb(0xe8e0d0))
    circle(L * 0.31f, -L * 0.02f, L * 0.022f, rgb(0x101010))
    c.restore()
}

/** A cherry shrimp, curved body and twitching feelers. */
private fun Sketch.shrimp(x: Float, y: Float, len: Float, dir: Float, t: Float, i: Int) {
    c.save(); c.translate(x, y); c.scale(dir, 1f)
    val p = ap[0]; p.reset()
    p.moveTo(len * 0.5f, -len * 0.05f)
    p.quadTo(len * 0.1f, -len * 0.38f, -len * 0.4f, -len * 0.08f)
    p.lineTo(-len * 0.55f, len * 0.06f); p.lineTo(-len * 0.35f, len * 0.06f)
    p.quadTo(len * 0.1f, len * 0.02f, len * 0.5f, len * 0.08f); p.close()
    fill(p, rgb(0xc8342a))
    ellipse(len * 0.1f, -len * 0.16f, len * 0.2f, len * 0.05f, -0.15f, rgba(0xFFAA96, 0.5f))
    val tw = sin(t * 3 + i * 2) * len * 0.08f
    p.reset()
    p.moveTo(len * 0.45f, -len * 0.04f); p.quadTo(len * 0.9f, -len * 0.4f, len * 1.1f, -len * 0.15f + tw)
    p.moveTo(len * 0.45f, -len * 0.02f); p.quadTo(len * 0.8f, -len * 0.25f, len * 1.0f, len * 0.05f - tw)
    stroke(p, rgba(0xDC5A46, 0.8f), 0.6f)
    circle(len * 0.38f, -len * 0.08f, len * 0.04f, rgb(0x101010))
    c.restore()
}
