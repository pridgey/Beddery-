package dev.pridgey.beddery.savers

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Beddery's illustrated scenes are designed as browser prototypes and drawn here the same way:
 * this kit mirrors the prototypes' helpers one for one (same noise, same random numbers, same
 * shapes) on an Android canvas measured in density-independent units.
 */

/** Mulberry32, identical to the prototypes' rng(), so scattered details land in the same places. */
class Rng(private var seed: Int) {
    fun next(): Float {
        seed += 0x6d2b79f5
        var t = (seed xor (seed ushr 15)) * (1 or seed)
        t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
        return ((t xor (t ushr 14)).toLong() and 0xffffffffL).toFloat() / 4294967296f
    }
}

// The hash multiplies its input by a large number, so it's computed in doubles as the prototypes
// are: in floats, the same landscape would come out differently.
fun sHash(n: Double): Double {
    val x = sin(n * 127.1 + 311.7) * 43758.5453
    return x - floor(x)
}

fun sHash(n: Float): Float = sHash(n.toDouble()).toFloat()

fun sNoise(x: Double, seed: Double = 0.0): Double {
    val i = floor(x)
    val f = x - i
    val u = f * f * (3 - 2 * f)
    return sHash(i + seed * 57.3) * (1 - u) + sHash(i + 1 + seed * 57.3) * u
}

fun sNoise(x: Float, seed: Float = 0f): Float = sNoise(x.toDouble(), seed.toDouble()).toFloat()

fun sFbm(x: Double, seed: Double = 0.0, oct: Int = 4): Double {
    var s = 0.0
    var a = 1.0
    var f = 1.0
    var n = 0.0
    for (i in 0 until oct) {
        s += sNoise(x * f, seed + i * 13) * a
        n += a
        a *= 0.48
        f *= 2.13
    }
    return s / n
}

fun sFbm(x: Float, seed: Float = 0f, oct: Int = 4): Float = sFbm(x.toDouble(), seed.toDouble(), oct).toFloat()

/** Crisp mountain peaks from folded noise. */
fun sPeaks(x: Double, seed: Double, oct: Int = 4): Double = (1 - abs(sFbm(x, seed, oct) * 2 - 1)).pow(2)

fun sSmooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

fun sLerp(a: Float, b: Float, t: Float) = a + (b - a) * t
fun sFlicker(t: Float, seed: Float) = sNoise(t * 2.3f, seed) * 0.6f + sNoise(t * 6.1f, seed + 9) * 0.4f

/** An opaque colour from 0xRRGGBB. */
fun rgb(hex: Int): Int = 0xFF000000.toInt() or hex

/** A colour from 0xRRGGBB with an alpha of 0..1. */
fun rgba(hex: Int, a: Float): Int = ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (hex and 0xFFFFFF)

/** Mixes two 0xRRGGBB colours. */
fun mixc(a: Int, b: Int, t: Float): Int {
    fun ch(c: Int, s: Int) = (c shr s) and 255
    fun m(s: Int) = sLerp(ch(a, s).toFloat(), ch(b, s).toFloat(), t).roundToInt()
    return rgb((m(16) shl 16) or (m(8) shl 8) or m(0))
}

class Sketch {
    lateinit var c: Canvas
    var W = 0f
    var H = 0f
    /** Pixels per unit: taps arrive in pixels, drawing is in dp. */
    var density = 1f
    val U get() = min(W, H)
    val land get() = W > H

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val overlay = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
    private val rect = RectF()
    private val tmp = Path()
    private var grainShader: BitmapShader? = null

    private fun fillPaint(color: Int, shader: Shader? = null, additive: Boolean = false): Paint = paint.apply {
        reset()
        maskFilter = null
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = color
        this.shader = shader
        xfermode = if (additive) add else null
    }

    // ---- Basic shapes.

    fun rect(x: Float, y: Float, w: Float, h: Float, color: Int) = c.drawRect(x, y, x + w, y + h, fillPaint(color))
    fun rect(x: Float, y: Float, w: Float, h: Float, shader: Shader) = c.drawRect(x, y, x + w, y + h, fillPaint(-1, shader))
    fun fill(path: Path, color: Int, additive: Boolean = false) = c.drawPath(path, fillPaint(color, null, additive))
    fun fill(path: Path, shader: Shader) = c.drawPath(path, fillPaint(-1, shader))
    /** Additive fills, for light: beams, glints, flashes. */
    fun fillAdd(path: Path, shader: Shader) = c.drawPath(path, fillPaint(-1, shader, true))
    fun rectAdd(x: Float, y: Float, w: Float, h: Float, shader: Shader) = c.drawRect(x, y, x + w, y + h, fillPaint(-1, shader, true))
    fun rectAdd(x: Float, y: Float, w: Float, h: Float, color: Int) = c.drawRect(x, y, x + w, y + h, fillPaint(color, null, true))
    /** Fills [path] softened by a blur of [radius]: soft shadows. */
    fun fillBlurred(path: Path, color: Int, radius: Float) {
        val p = fillPaint(color)
        if (radius > 0f) p.maskFilter = android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        c.drawPath(path, p)
    }

    fun circle(x: Float, y: Float, r: Float, color: Int) = c.drawCircle(x, y, r, fillPaint(color))
    fun circle(x: Float, y: Float, r: Float, shader: Shader) = c.drawCircle(x, y, r, fillPaint(-1, shader))

    fun ellipse(x: Float, y: Float, rx: Float, ry: Float, rot: Float, color: Int) {
        c.save()
        c.translate(x, y)
        if (rot != 0f) c.rotate(Math.toDegrees(rot.toDouble()).toFloat())
        rect.set(-rx, -ry, rx, ry)
        c.drawOval(rect, fillPaint(color))
        c.restore()
    }

    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Int) {
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, r, r, fillPaint(color))
    }

    fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float, shader: Shader) {
        rect.set(x, y, x + w, y + h)
        c.drawRoundRect(rect, r, r, fillPaint(-1, shader))
    }

    fun stroke(path: Path, color: Int, width: Float, round: Boolean = false, shader: Shader? = null) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = color
        paint.shader = shader
        paint.strokeWidth = width
        paint.strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        paint.strokeJoin = if (round) Paint.Join.ROUND else Paint.Join.MITER
        c.drawPath(path, paint)
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, color: Int, width: Float, round: Boolean = false) {
        tmp.reset(); tmp.moveTo(x0, y0); tmp.lineTo(x1, y1)
        stroke(tmp, color, width, round)
    }

    /** An arc stroke; angles in radians, as in the prototypes. */
    fun arcStroke(x: Float, y: Float, rx: Float, ry: Float, a0: Float, a1: Float, color: Int, width: Float, rot: Float = 0f) {
        tmp.reset()
        rect.set(x - rx, y - ry, x + rx, y + ry)
        tmp.addArc(rect, Math.toDegrees(a0.toDouble()).toFloat(), Math.toDegrees((a1 - a0).toDouble()).toFloat())
        if (rot != 0f) {
            c.save(); c.rotate(Math.toDegrees(rot.toDouble()).toFloat(), x, y)
            stroke(tmp, color, width)
            c.restore()
        } else stroke(tmp, color, width)
    }

    // ---- Gradients.

    fun vgrad(y0: Float, y1: Float, vararg stops: Pair<Float, Int>): Shader =
        LinearGradient(0f, y0, 0f, y1, IntArray(stops.size) { stops[it].second }, FloatArray(stops.size) { stops[it].first }, Shader.TileMode.CLAMP)

    fun hgrad(x0: Float, x1: Float, vararg stops: Pair<Float, Int>): Shader =
        LinearGradient(x0, 0f, x1, 0f, IntArray(stops.size) { stops[it].second }, FloatArray(stops.size) { stops[it].first }, Shader.TileMode.CLAMP)

    fun lgrad(x0: Float, y0: Float, x1: Float, y1: Float, vararg stops: Pair<Float, Int>): Shader =
        LinearGradient(x0, y0, x1, y1, IntArray(stops.size) { stops[it].second }, FloatArray(stops.size) { stops[it].first }, Shader.TileMode.CLAMP)

    fun rgrad(x: Float, y: Float, r: Float, vararg stops: Pair<Float, Int>): Shader =
        RadialGradient(x, y, max(r, 0.01f), IntArray(stops.size) { stops[it].second }, FloatArray(stops.size) { stops[it].first }, Shader.TileMode.CLAMP)

    /** A soft round light, additive by default; [sy] squashes it vertically. */
    fun glow(x: Float, y: Float, r: Float, color: Int, alpha: Float, additive: Boolean = true, sy: Float = 1f) {
        if (alpha <= 0.001f || r <= 0f) return
        c.save()
        c.translate(x, y)
        c.scale(1f, sy)
        val g = RadialGradient(0f, 0f, r, intArrayOf(rgba(color, alpha), rgba(color, alpha * 0.4f), rgba(color, 0f)), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(-r, -r, r, r, fillPaint(-1, g, additive))
        c.restore()
    }

    /** Fine film grain, blended so blacks stay black. */
    fun grain(alpha: Float = 0.05f) {
        val shader = grainShader ?: BitmapShader(grainBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also { grainShader = it }
        paint.reset()
        paint.shader = shader
        paint.alpha = (alpha * 255).roundToInt()
        paint.xfermode = overlay
        c.drawRect(0f, 0f, W, H, paint)
    }

    // ---- Scenery.

    fun stars(top: Float, bottom: Float, n: Int, seed: Int, t: Float, color: Int = 0xFFF1E4, fadeBottom: Boolean = true, width: Float = W) {
        val r = Rng(seed)
        for (i in 0 until n) {
            val x = r.next() * width
            val y = top + r.next() * (bottom - top)
            val s = r.next().pow(2.5f)
            val fade = if (fadeBottom) 1 - sSmooth(top, bottom, y) * 0.85f else 1f
            val a = (0.2f + 0.75f * s) * fade * (0.7f + 0.3f * sin(t * (0.5f + r.next() * 2) + i))
            circle(x, y, 0.45f + s * 1.2f, rgba(color, a))
            if (s > 0.75f) glow(x, y, 5f, color, a * 0.35f)
        }
    }

    fun moon(x: Float, y: Float, r: Float, color: Int = 0xF7E6D3, halo: Float = 0.2f) {
        glow(x, y, r * 8, color, halo, additive = false)
        glow(x, y, r * 2.2f, color, halo * 1.2f, additive = false)
        circle(x, y, r, rgb(color))
        ellipse(x - r * 0.25f, y - r * 0.15f, r * 0.32f, r * 0.22f, 0.4f, rgba(0xA08C96, 0.16f))
        ellipse(x + r * 0.2f, y + r * 0.25f, r * 0.25f, r * 0.18f, -0.3f, rgba(0xA08C96, 0.16f))
    }

    /** A ridge of hills across the width, filled down to [bottom]. Returns its outline as x,y pairs. */
    fun ridge(base: Float, amp: Float, seed: Float, scale: Float, fill: Int, bottom: Float, shift: Float = 0f, sharp: Boolean = true, rim: Int? = null, width: Float = W): FloatArray {
        val n = ((width + 12) / 3).toInt() + 1
        val pts = FloatArray(n * 2)
        var k = 0
        var x = -4f
        while (k < n) {
            val u = (x.toDouble() + shift) / width * scale + seed.toDouble() * 3.7
            val v = (if (sharp) sPeaks(u, seed.toDouble()) else sFbm(u, seed.toDouble())).toFloat()
            pts[k * 2] = x
            pts[k * 2 + 1] = base - v * amp
            x += 3f
            k++
        }
        tmp.reset()
        tmp.moveTo(-4f, bottom)
        for (i in 0 until n) tmp.lineTo(pts[i * 2], pts[i * 2 + 1])
        tmp.lineTo(pts[(n - 1) * 2], bottom)
        tmp.close()
        fill(tmp, fill)
        if (rim != null) {
            tmp.reset()
            for (i in 0 until n) if (i == 0) tmp.moveTo(pts[0], pts[1]) else tmp.lineTo(pts[i * 2], pts[i * 2 + 1])
            stroke(tmp, rim, 1.2f)
        }
        return pts
    }

    fun mist(y: Float, h: Float, color: Int, alpha: Float, width: Float = W) {
        rect(0f, y - h, width, h * 1.4f, vgrad(y - h, y + h * 0.4f, 0f to rgba(color, 0f), 0.7f to rgba(color, alpha), 1f to rgba(color, 0f)))
    }

    private val pinePath = Path()

    /** A stylised pine: stacked, slightly drooping tiers. */
    fun pine(x: Float, base: Float, h: Float, color: Int, seed: Float = 0f, lit: Int? = null, litSide: Float = 1f) {
        val tiers = 5 + floor(sHash(seed) * 3).toInt()
        val w = h * (0.34f + 0.08f * sHash(seed + 1))
        val p = pinePath
        p.reset()
        p.moveTo(x, base - h)
        for (k in 1..tiers) {
            val f = k / tiers.toFloat()
            val y = base - h + h * 0.86f * f
            val reach = w * 0.5f * (0.3f + 0.7f * f) * (0.9f + 0.2f * sHash(seed + k * 3))
            p.quadTo(x + reach * 0.5f, y - h * 0.06f, x + reach, y + h * 0.015f)
            p.lineTo(x + reach * 0.42f, y - h * 0.02f)
        }
        p.lineTo(x + h * 0.025f, base)
        p.lineTo(x - h * 0.025f, base)
        for (k in tiers downTo 1) {
            val f = k / tiers.toFloat()
            val y = base - h + h * 0.86f * f
            val reach = w * 0.5f * (0.3f + 0.7f * f) * (0.9f + 0.2f * sHash(seed + k * 7))
            p.lineTo(x - reach * 0.42f, y - h * 0.02f)
            p.lineTo(x - reach, y + h * 0.015f)
            p.quadTo(x - reach * 0.5f, y - h * 0.06f, x, base - h + h * 0.86f * ((k - 1) / tiers.toFloat()))
        }
        p.close()
        fill(p, color)
        if (lit != null) {
            c.save()
            c.clipPath(p)
            rect(x - w, base - h, w * 2, h, hgrad(x, x + litSide * w * 0.5f, 0f to 0, 1f to lit))
            c.restore()
        }
    }

    fun treeLine(
        base: Float, hMin: Float, hMax: Float, gap: Float, seed: Int, color: Int,
        skip: ((Float) -> Boolean)? = null, lit: ((Float) -> Int?)? = null, litSide: ((Float) -> Float)? = null,
    ) {
        val r = Rng(seed)
        var x = -20f
        var i = 0
        while (x < W + 30) {
            val h = hMin + (hMax - hMin) * r.next()
            val jitter = (r.next() - 0.5f) * gap * 0.6f
            val px = x + jitter
            if (skip == null || !skip(px)) {
                pine(px, base + r.next() * 0f, h, color, seed * 100f + i, lit?.invoke(px), litSide?.invoke(px) ?: 1f)
            }
            x += gap * (0.6f + 0.8f * r.next())
            i++
        }
    }

    /** A teardrop flame from its base centre. */
    fun flamePath(p: Path, cx: Float, by: Float, w: Float, h: Float, sway: Float, lean: Float = 0f): Path {
        p.reset()
        p.moveTo(cx - w / 2, by)
        p.cubicTo(cx - w * 0.62f, by - h * 0.45f, cx - w * 0.12f + sway * 0.4f + lean, by - h * 0.62f, cx + sway + lean, by - h)
        p.cubicTo(cx + w * 0.18f + sway * 0.4f + lean, by - h * 0.6f, cx + w * 0.62f, by - h * 0.42f, cx + w / 2, by)
        p.cubicTo(cx + w * 0.3f, by + w * 0.22f, cx - w * 0.3f, by + w * 0.22f, cx - w / 2, by)
        p.close()
        return p
    }

    companion object {
        private val grainBitmap: Bitmap by lazy {
            val r = Rng(7)
            val size = 180
            val px = IntArray(size * size) {
                val v = floor(r.next() * 255).toInt()
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        }
    }
}

/**
 * Hosts an illustrated scene: a full-size canvas measured in dp, redrawn every frame.
 * [time] is the frame clock in seconds.
 */
@Composable
fun SketchScene(time: State<Float>, draw: Sketch.(t: Float) -> Unit) {
    val sketch = remember { Sketch() }
    ComposeCanvas(Modifier.fillMaxSize()) {
        val t = time.value
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.save()
            nc.scale(density, density)
            sketch.c = nc
            sketch.W = size.width / density
            sketch.H = size.height / density
            sketch.density = density
            sketch.draw(t)
            nc.restore()
        }
    }
}
