package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.Sketch
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rgb
import dev.pridgey.beddery.savers.rgba
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sNoise
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.Canvas as ComposeCanvas

/*
 * Rain on Glass: a city street at blue hour, far out of focus behind a rainy window.
 *
 * Designed from rain-on-window night photography: lights melt into bokeh discs with crisp,
 * slightly brighter rims; neon, sodium lamps and traffic stretch into streaks on the wet road;
 * and every drop on the glass is a tiny lens holding an upside-down picture of the lights.
 *
 * The street is drawn at a third of screen size each frame (it's out of focus anyway). That
 * picture is shown full screen, and each drop samples it, flipped and shrunk, as its lens.
 */

private const val BG_SCALE = 3f

private val Sodium = 0xFFA24A
private val WarmWhite = 0xFFE2B8
private val CoolWhite = 0xD8E4FF
private val Headlight = 0xF4F2EA
private val Taillight = 0xFF3424
private val Magenta = 0xFF3D8B
private val Teal = 0x34E2D0
private val NeonBlue = 0x5A84FF

/** A bokeh disc: a flat, softly bright face with a crisp, brighter rim, as an out-of-focus lens draws a point of light. */
private val BokehSprite: Bitmap by lazy {
    val n = 96
    val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = n / 2f
    fun a(x: Float) = rgba(0xFFFFFF, x)
    p.shader = RadialGradient(
        r, r, r - 1f,
        intArrayOf(a(0.5f), a(0.52f), a(0.62f), a(0.62f), a(0.9f), a(0.75f), a(0f)),
        floatArrayOf(0f, 0.45f, 0.5f, 0.78f, 0.92f, 0.97f, 1f), Shader.TileMode.CLAMP,
    )
    c.drawCircle(r, r, r - 1f, p)
    bmp
}

/** The rim and glint of a water drop, laid over its lens picture. */
private val DropSprite: Bitmap by lazy {
    val n = 96
    val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = n / 2f
    // A dark edge where the drop bends light away, darkest at the top.
    p.shader = RadialGradient(r, r + 4f, r, intArrayOf(rgba(0, 0f), rgba(0, 0f), rgba(0x05080e, 0.35f), rgba(0x05080e, 0.6f)), floatArrayOf(0f, 0.68f, 0.92f, 1f), Shader.TileMode.CLAMP)
    c.drawCircle(r, r, r - 1f, p)
    // A glint near the top and a soft light pooled at the bottom.
    p.shader = RadialGradient(r - 13f, r - 17f, 10f, intArrayOf(rgba(0xFFFFFF, 0.85f), rgba(0xFFFFFF, 0f)), null, Shader.TileMode.CLAMP)
    c.drawCircle(r - 13f, r - 17f, 10f, p)
    p.shader = null
    p.style = Paint.Style.STROKE
    p.strokeWidth = 3f
    p.color = rgba(0xFFFFFF, 0.18f)
    c.drawArc(RectF(10f, 10f, n - 10f, n - 10f), 30f, 120f, false, p)
    bmp
}

/** The drops on the glass: a field of still beads, and a few heavy ones that run and collect them. */
private class RainGlass(private val maxDrops: Int) {
    private val r = Random(3)
    val x = FloatArray(maxDrops) { r.nextFloat() }
    val y = FloatArray(maxDrops) { r.nextFloat() }
    val size = FloatArray(maxDrops) { bead() }

    class Runner(var x: Float, var y: Float, var size: Float, val seed: Int, var lastTrail: Float, val trail: ArrayList<Float> = ArrayList())

    val runners = ArrayList<Runner>()
    private var seed = 0

    private fun bead() = 0.004f + r.nextFloat().pow(2.5f) * 0.018f

    fun step(dt: Float, t: Float) {
        // New drops land.
        repeat((dt * 30f).toInt() + if (r.nextFloat() < (dt * 30f) % 1f) 1 else 0) {
            val i = r.nextInt(maxDrops)
            x[i] = r.nextFloat()
            y[i] = r.nextFloat()
            size[i] = bead()
        }
        if (runners.size < 6 && r.nextFloat() < dt * 0.35f) launch(r.nextFloat(), r.nextFloat() * 0.4f)
        val it = runners.iterator()
        while (it.hasNext()) {
            val d = it.next()
            // Drops stutter: they hang, then rush, as they gather water.
            val surge = 0.5f + 0.5f * noise1(t * 1.3f + d.seed * 9f, d.seed)
            val speed = (0.02f + surge * surge * 0.22f) * (0.6f + d.size * 40f)
            d.y += speed * dt
            d.x += noise1(t * 0.8f + d.seed, d.seed + 3) * dt * 0.01f
            for (i in 0 until maxDrops) {
                if (size[i] <= 0f) continue
                if (abs(x[i] - d.x) < d.size + size[i] && abs((y[i] - d.y) * 2f) < d.size * 2 + size[i]) {
                    d.size = (d.size + size[i] * 0.15f).coerceAtMost(0.034f)
                    size[i] = 0f
                }
            }
            if (d.y - d.lastTrail > 0.025f) {
                d.lastTrail = d.y
                d.trail += d.x
                d.trail += d.y
                // A run leaves a line of tiny beads behind it.
                val i = r.nextInt(maxDrops)
                x[i] = d.x + (r.nextFloat() - 0.5f) * d.size
                y[i] = d.y - d.size * 2.5f
                size[i] = d.size * (0.18f + r.nextFloat() * 0.15f)
            }
            if (d.y > 1.1f) it.remove()
        }
    }

    fun launch(atX: Float, atY: Float) {
        runners += Runner(atX, atY, 0.016f + r.nextFloat() * 0.01f, seed++, atY)
    }
}

/** What's happening on the street: traffic both ways, now and then a bus, and someone walking by under an umbrella. */
private class Street {
    private val r = Random(12)

    // x (0..1 of width), lane (0 near, heading right, headlights; 1 far, heading left, taillights), speed, kind (0 car, 1 bus)
    val cars = ArrayList<FloatArray>()
    private var nextCar = 1f
    private var nextBus = 25f

    // x, speed; a walker crossing in front
    var walker: FloatArray? = null
    private var nextWalker = 14f

    fun step(dt: Float, t: Float) {
        if (t > nextCar) {
            val lane = if (r.nextBoolean()) 0f else 1f
            cars += floatArrayOf(if (lane == 0f) -0.2f else 1.2f, lane, 0.05f + r.nextFloat() * 0.05f, 0f)
            nextCar = t + 2f + r.nextFloat() * 5f
        }
        if (t > nextBus) {
            cars += floatArrayOf(1.4f, 1f, 0.035f, 1f)
            nextBus = t + 50f + r.nextFloat() * 40f
        }
        cars.forEach { it[0] += (if (it[1] == 0f) 1 else -1) * it[2] * dt }
        cars.removeAll { it[0] < -0.5f || it[0] > 1.5f }
        walker?.let { it[0] += it[1] * dt; if (it[0] > 1.3f) walker = null }
        if (walker == null && t > nextWalker) {
            walker = floatArrayOf(-0.3f, 0.025f + r.nextFloat() * 0.01f)
            nextWalker = t + 45f + r.nextFloat() * 40f
        }
    }

    fun signal(t: Float): Int = when (t % 40f) {
        in 0f..20f -> 0x3DFF9A
        in 20f..23f -> 0xFFB13D
        else -> 0xFF4A3D
    }
}

/** The street picture, kept between frames: a third-size bitmap the drops sample. */
private class StreetBuffer {
    var bitmap: Bitmap? = null
    var canvas: Canvas? = null
    var shader: BitmapShader? = null

    fun ensure(w: Int, h: Int): Bitmap {
        bitmap?.takeIf { it.width == w && it.height == h }?.let { return it }
        val b = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        bitmap = b
        canvas = Canvas(b)
        shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        return b
    }
}

@Composable
fun SceneRainSaver(ctx: SaverContext) {
    val glass = remember { RainGlass(if (ctx.preview) 160 else 340) }
    val street = remember { Street() }
    val buffer = remember { StreetBuffer() }
    val sketch = remember { Sketch() }
    val paints = remember { RainPaints() }
    val cooldown = remember { Cooldown(3_000) }
    val canvasSize = remember { floatArrayOf(1f, 1f) }
    val time = rememberFrameTime { dt, t ->
        glass.step(dt, t)
        street.step(dt, t)
    }
    OnSaverTap { at -> if (cooldown.tryAcquire()) glass.launch(at.x / canvasSize[0], at.y / canvasSize[1]) }
    ComposeCanvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        canvasSize[0] = w
        canvasSize[1] = h
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            // 1. The street, small.
            val bmp = buffer.ensure((w / BG_SCALE).roundToInt(), (h / BG_SCALE).roundToInt())
            val bc = buffer.canvas!!
            bc.save()
            bc.scale(density / BG_SCALE, density / BG_SCALE)
            sketch.c = bc
            sketch.W = w / density
            sketch.H = h / density
            sketch.density = density
            sketch.drawStreet(t, street, paints)
            bc.restore()
            bmp.prepareToDraw()

            // 2. Shown full size: the scaling itself is the last of the blur.
            nc.drawBitmap(bmp, null, RectF(0f, 0f, w, h), paints.smooth)

            // 3. The glass: condensation, run trails, then every drop as a lens.
            nc.drawColor(rgba(0x6A7A96, 0.04f))
            val unit = min(w, h)
            for (d in glass.runners) {
                val tr = d.trail
                if (tr.size < 2) continue
                val path = paints.trailPath
                path.rewind()
                path.moveTo(tr[0] * w, tr[1] * h)
                var k = 2
                while (k + 1 < tr.size) { path.lineTo(tr[k] * w, tr[k + 1] * h); k += 2 }
                path.lineTo(d.x * w, d.y * h)
                paints.trail.strokeWidth = d.size * unit * 0.8f
                nc.drawPath(path, paints.trail)
            }
            val shader = buffer.shader!!
            for (i in 0 until glass.size.size) {
                val s = glass.size[i]
                if (s <= 0f) continue
                drop(nc, shader, paints, glass.x[i] * w, glass.y[i] * h, s * unit, s * unit * 1.05f)
            }
            for (d in glass.runners) {
                val px = d.size * unit
                drop(nc, shader, paints, d.x * w, d.y * h - px * 0.7f, px * 0.62f, px * 0.9f)
                drop(nc, shader, paints, d.x * w, d.y * h, px, px * 1.18f)
            }

            // 4. A soft vignette and grain over the whole window.
            nc.save()
            nc.scale(density, density)
            sketch.c = nc
            sketch.rect(0f, 0f, sketch.W, sketch.H, sketch.rgrad(sketch.W / 2, sketch.H / 2, max(sketch.W, sketch.H) * 0.75f, 0.55f to rgba(0, 0f), 1f to rgba(0, 0.5f)))
            sketch.grain(0.05f)
            nc.restore()
        }
    }
}

private class RainPaints {
    val smooth = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    // A drop gathers light: its picture is brighter than the street around it.
    val lens = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(1.7f, 0f, 0f, 0f, 14f, 0f, 1.7f, 0f, 0f, 16f, 0f, 0f, 1.7f, 0f, 22f, 0f, 0f, 0f, 1f, 0f)))
    }
    val trailPath = Path()
    val sprite = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val bokeh = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = rgba(0x0a0f18, 0.14f); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    val matrix = Matrix()
    val rect = RectF()
    private val filters = HashMap<Int, PorterDuffColorFilter>()
    fun tint(color: Int) = filters.getOrPut(color) { PorterDuffColorFilter(rgb(color), PorterDuff.Mode.SRC_IN) }
}

/** A drop of radius [rx]×[ry] at ([cx], [cy]): the street behind it, flipped and shrunk, under a dark rim and a glint. */
private fun drop(nc: Canvas, shader: BitmapShader, p: RainPaints, cx: Float, cy: Float, rx: Float, ry: Float) {
    if (rx < 0.6f) return
    // A drop shows about six times its own width of the scene, upside down.
    val k = 6f
    p.matrix.setScale(-BG_SCALE / k, -BG_SCALE / k)
    p.matrix.postTranslate(cx * (1 + 1 / k), cy * (1 + 1 / k))
    shader.setLocalMatrix(p.matrix)
    p.lens.shader = shader
    p.rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
    nc.drawOval(p.rect, p.lens)
    nc.drawBitmap(DropSprite, null, p.rect, p.sprite)
}

private fun Sketch.bokeh(p: RainPaints, x: Float, y: Float, r: Float, color: Int, alpha: Float, squash: Float = 1f) {
    if (alpha <= 0.004f || r <= 0.3f) return
    p.bokeh.colorFilter = p.tint(color)
    p.bokeh.alpha = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
    p.rect.set(x - r, y - r * squash, x + r, y + r * squash)
    c.drawBitmap(BokehSprite, null, p.rect, p.bokeh)
}

/** A light's long reflection down the wet road. */
private fun Sketch.streak(x: Float, top: Float, r: Float, len: Float, color: Int, alpha: Float) {
    glow(x, top + len * 0.5f, r, color, alpha, sy = len / r * 0.5f)
}

private fun Sketch.drawStreet(t: Float, street: Street, p: RainPaints) {
    val road = H * if (land) 0.7f else 0.66f
    // ---- Blue hour: deep blue above, the street's warm haze low down, black wet asphalt.
    rect(0f, 0f, W, road, vgrad(0f, road, 0f to rgb(0x060a18), 0.45f to rgb(0x0c1630), 0.85f to rgb(0x1c1c34), 1f to rgb(0x2a2030)))
    rect(0f, road, W, H - road, vgrad(road, H, 0f to rgb(0x161420), 0.25f to rgb(0x0c0d16), 1f to rgb(0x05060a)))
    glow(W * 0.3f, road - H * 0.04f, U * 0.9f, Sodium, 0.16f, sy = 0.45f)
    rect(0f, road - H * 0.03f, W, H * 0.06f, vgrad(road - H * 0.03f, road + H * 0.03f, 0f to rgba(0x2a2030, 0f), 0.5f to rgba(0x2a2030, 0.8f), 1f to rgba(0x161420, 0f)))
    glow(W * 0.12f, H * 0.36f, U * 0.5f, Magenta, 0.1f)
    glow(W * 0.78f, H * 0.4f, U * 0.45f, Teal, 0.08f)

    // ---- The towers: lit windows, small and faint, a few switching on and off.
    for (i in 0 until 90) {
        val col = (sHash(i * 1.7f) * 11).toInt()
        val x = W * (0.03f + col * 0.092f + sHash(i * 3.1f) * 0.05f)
        val y = H * (0.04f + sHash(i * 5.3f) * 0.42f)
        val on = sNoise(t * 0.02f + i * 3.7f, i.toFloat()) > 0.28f
        if (!on) continue
        val kind = sHash(i * 7.9f)
        val c = when {
            kind < 0.45f -> WarmWhite
            kind < 0.6f -> Sodium
            kind < 0.85f -> CoolWhite
            kind < 0.93f -> Teal
            else -> Magenta
        }
        val size = sHash(i * 2.3f)
        bokeh(p, x, y, U * (0.008f + size * size * 0.035f), c, 0.1f + sHash(i * 9.1f) * 0.14f)
    }

    // ---- Neon: a tall pink sign on the left, a teal one across the street, a blue bar over a door.
    val buzz = if (sNoise(t * 3f, 4f) > 0.85f) 0.35f else 1f
    for (k in 0 until 14) bokeh(p, W * 0.13f, H * (0.27f + k * 0.015f), U * 0.05f, Magenta, 0.17f * buzz)
    for (k in 0 until 12) bokeh(p, W * 0.62f + k * U * 0.024f, H * 0.38f, U * 0.042f, Teal, 0.15f)
    for (k in 0 until 8) bokeh(p, W * 0.4f + k * U * 0.018f, road - H * 0.07f, U * 0.03f, NeonBlue, 0.17f)

    // ---- Streetlights receding to the right, each with its reflection down the road.
    for (i in 0 until 6) {
        val f = i / 5f
        val x = W * (-0.02f + f.pow(0.8f) * 1.0f)
        val y = road - H * (0.22f - f * 0.12f)
        val r = U * (0.13f - f * 0.09f)
        bokeh(p, x, y, r, Sodium, 0.42f)
        glow(x, y, r * 2.2f, Sodium, 0.08f)
        streak(x, road + H * 0.01f, r * 0.45f, H * (0.22f - f * 0.1f), Sodium, 0.22f)
    }
    // A traffic light on the corner.
    val sig = street.signal(t)
    val sx = W * 0.74f
    val sy = road - H * 0.12f
    bokeh(p, sx, sy, U * 0.05f, sig, 0.5f)
    glow(sx, sy, U * 0.12f, sig, 0.12f)
    streak(sx, road + H * 0.01f, U * 0.02f, H * 0.16f, sig, 0.25f)

    // ---- Traffic.
    for (car in street.cars) {
        val near = car[1] == 0f
        val x = car[0] * W
        if (car[3] == 1f) {
            // A bus on the far side: a long row of warm windows, a route sign, red lights at the back.
            val y = road - H * 0.035f
            for (k in 0 until 9) bokeh(p, x + k * U * 0.06f, y - U * 0.03f, U * 0.035f, WarmWhite, 0.22f)
            bokeh(p, x - U * 0.01f, y - U * 0.07f, U * 0.03f, 0xFFC040, 0.35f)
            bokeh(p, x + U * 0.56f, y + U * 0.01f, U * 0.03f, Taillight, 0.5f)
            streak(x + U * 0.25f, road + H * 0.005f, U * 0.15f, H * 0.08f, WarmWhite, 0.07f)
            continue
        }
        val y = if (near) road + H * 0.03f else road - H * 0.012f
        val r = U * if (near) 0.07f else 0.035f
        val c = if (near) Headlight else Taillight
        for (k in 0..1) {
            val lx = x + (k - 0.5f) * r * 1.6f
            bokeh(p, lx, y, r, c, if (near) 0.55f else 0.5f)
            glow(lx, y, r * 2.4f, if (near) WarmWhite else c, 0.18f)
            streak(lx, y + r * 0.5f, r * 0.35f, H * if (near) 0.2f else 0.1f, c, 0.2f)
        }
    }

    // ---- Someone walks past under an umbrella, a dark soft shape against the lights.
    street.walker?.let { wk ->
        val x = wk[0] * W
        val bob = abs(sin(t * 3.2f)) * U * 0.006f
        val y = road + H * 0.06f - bob
        val s = U * 0.22f
        val body = Path()
        body.addOval(RectF(x - s * 0.13f, y - s * 0.75f, x + s * 0.13f, y + s * 0.25f), Path.Direction.CW)
        fillBlurred(body, rgba(0x020308, 0.85f), s * 0.09f)
        val brolly = Path()
        brolly.moveTo(x - s * 0.5f, y - s * 0.62f)
        brolly.quadTo(x, y - s * 1.25f, x + s * 0.5f, y - s * 0.62f)
        brolly.close()
        fillBlurred(brolly, rgba(0x030409, 0.9f), s * 0.09f)
    }
}
