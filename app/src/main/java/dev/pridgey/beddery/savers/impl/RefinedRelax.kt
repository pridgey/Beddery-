package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.R
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.TAU
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.layer
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.rememberNoiseBrush
import dev.pridgey.beddery.savers.ridge
import dev.pridgey.beddery.savers.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ---------------------------------------------------------------- Lava lamp (refined)

/**
 * Real metaballs: the wax is a smooth field rendered pixel by pixel at a reduced resolution,
 * then lit like a glossy, translucent solid (diffuse light, specular highlights and a warm rim
 * from the bulb below), and scaled up smoothly.
 */
private class LavaField(private val resolution: Int) {
    private val count = 9
    private val r = Random(8)
    private val period = FloatArray(count) { 46f + r.nextFloat() * 54f }
    private val phase = FloatArray(count) { r.nextFloat() * TAU }
    private val xBase = FloatArray(count) { 0.22f + r.nextFloat() * 0.56f }
    private val xFreq = FloatArray(count) { 0.015f + r.nextFloat() * 0.03f }
    private val radius = FloatArray(count) { 0.06f + r.nextFloat() * 0.06f }

    private val cx = FloatArray(count + 3)
    private val cy = FloatArray(count + 3)
    private val r2 = FloatArray(count + 3)
    private var active = 0

    var gw = 0
        private set
    var gh = 0
        private set
    private var pixels = IntArray(0)
    var bitmap: Bitmap? = null
        private set

    // A blob dropped in by a tap: it rises from where you touched.
    private var tapX = 0f
    private var tapY = 0f
    private var tapAge = -1f

    fun drop(x: Float, y: Float) {
        tapX = x
        tapY = y
        tapAge = 0f
    }

    fun ensure(w: Float, h: Float) {
        val (nw, nh) = if (w < h) resolution to (resolution * h / w).toInt() else (resolution * w / h).toInt() to resolution
        if (nw == gw && nh == gh) return
        gw = nw
        gh = nh
        pixels = IntArray(gw * gh)
        bitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888)
    }

    fun step(dt: Float) {
        if (tapAge >= 0f) {
            tapAge += dt
            if (tapAge > 40f) tapAge = -1f
        }
    }

    fun render(t: Float) {
        val bmp = bitmap ?: return
        val unit = min(gw, gh).toFloat()
        active = 0
        for (i in 0 until count) {
            // Heat at the bottom, cool at the top: blobs rise and sink in long, slow cycles.
            val p = 0.5f - 0.5f * cos(t * TAU / period[i] + phase[i])
            cx[active] = gw * (xBase[i] + 0.12f * sin(t * xFreq[i] * TAU + phase[i] * 1.7f))
            cy[active] = gh * (1.04f - 1.08f * p)
            val rr = unit * radius[i] * (1f + 0.08f * sin(t * 0.4f + phase[i]))
            r2[active++] = rr * rr
        }
        // Wax pooled at the bottom and top of the lamp.
        cx[active] = gw / 2f; cy[active] = gh + unit * 0.42f; r2[active] = (unit * 0.62f).let { it * it }; active++
        cx[active] = gw / 2f; cy[active] = -unit * 0.36f; r2[active] = (unit * 0.46f).let { it * it }; active++
        if (tapAge >= 0f) {
            val grow = smoothstep(0f, 3f, tapAge) * (1f - smoothstep(30f, 40f, tapAge))
            cx[active] = gw * tapX + sin(tapAge * 0.3f) * unit * 0.02f
            cy[active] = gh * tapY - tapAge * gh * 0.02f
            r2[active] = (unit * 0.08f * grow).let { it * it }
            active++
        }

        // Light from the upper left, fairly low, so highlights sit near the edges like on real wax.
        val lx = -0.55f; val ly = -0.62f; val lz = 0.56f
        // Half vector between light and viewer for highlights.
        val hxr = lx; val hyr = ly; val hzr = lz + 1f
        val hl = sqrt(hxr * hxr + hyr * hyr + hzr * hzr)
        val hx = hxr / hl; val hy = hyr / hl; val hz = hzr / hl
        var idx = 0
        for (py in 0 until gh) {
            val fy = py + 0.5f
            val vy = fy / gh
            // Hotter, more orange wax low down; cooler rose near the top.
            val baseR = lerpF(0.95f, 1f, vy)
            val baseG = lerpF(0.28f, 0.52f, vy * vy)
            val baseB = lerpF(0.42f, 0.16f, vy)
            for (px in 0 until gw) {
                val fx = px + 0.5f
                var f = 0f
                var gx = 0f
                var gy = 0f
                for (i in 0 until active) {
                    val dx = fx - cx[i]
                    val dy = fy - cy[i]
                    val d2 = dx * dx + dy * dy + 0.25f
                    val v = r2[i] / d2
                    f += v
                    val k = 2f * v / d2
                    gx -= k * dx
                    gy -= k * dy
                }
                if (f < 0.28f) {
                    pixels[idx++] = 0
                    continue
                }
                val alpha = smoothstep(0.86f, 1.12f, f)
                val glow = smoothstep(0.28f, 1f, f) * 0.22f
                if (alpha <= 0f) {
                    pixels[idx++] = argb(glow, baseR, baseG * 0.8f, baseB)
                    continue
                }
                // A rounded dome: the surface tilts away from the middle, steepest at the edge and
                // flattening towards the thickest part, so merged blobs stay smooth.
                val gl = sqrt(gx * gx + gy * gy).coerceAtLeast(1e-4f)
                val tilt = (1f - smoothstep(1f, 3.2f, f)).let { sqrt(it) } * 1.7f
                var nx = -gx / gl * tilt
                var ny = -gy / gl * tilt
                var nz = 1f
                val nl = sqrt(nx * nx + ny * ny + nz * nz)
                nx /= nl; ny /= nl; nz /= nl
                val diffuse = max(0f, nx * lx + ny * ly + nz * lz)
                var spec = max(0f, nx * hx + ny * hy + nz * hz)
                spec *= spec; spec *= spec; spec *= spec // ^8: a broad sheen
                val sheen = spec
                spec *= spec; spec *= spec // ^32: a tight highlight
                val rim = (1f - nz).let { it * it }
                val thick = smoothstep(1.1f, 4f, f)
                val shade = 0.55f + 0.45f * diffuse
                // Light from the bulb glows through thick wax and around its lower edges.
                val red = baseR * shade + thick * 0.18f + rim * 0.5f * vy + sheen * 0.12f + spec * 0.55f
                val green = baseG * shade + thick * 0.14f + rim * 0.3f * vy + sheen * 0.1f + spec * 0.5f
                val blue = baseB * shade + thick * 0.05f + rim * 0.08f + sheen * 0.1f + spec * 0.45f
                pixels[idx++] = argb(max(alpha, glow), red, green, blue)
            }
        }
        bmp.setPixels(pixels, 0, gw, 0, 0, gw, gh)
    }

    private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun argb(a: Float, r: Float, g: Float, b: Float): Int =
        ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or ((r.coerceIn(0f, 1f) * 255).toInt() shl 16) or
            ((g.coerceIn(0f, 1f) * 255).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255).toInt()
}

@Composable
fun LavaRefinedSaver(ctx: SaverContext) {
    val field = remember { LavaField(if (ctx.preview) 60 else 128) }
    val grain = rememberGrainBrush()
    val cooldown = remember { Cooldown(20_000) }
    val lastRender = remember { floatArrayOf(-1f) }
    val time = rememberFrameTime { dt, t ->
        field.step(dt)
        // 30 fps is plenty for wax this slow, and halves the work.
        if (t - lastRender[0] >= 1f / 30f || lastRender[0] < 0f) {
            field.render(t + 300f)
            lastRender[0] = t
        }
    }
    val image = remember { arrayOfNulls<androidx.compose.ui.graphics.ImageBitmap>(1) }
    val canvasSize = remember { floatArrayOf(1f, 1f) }
    OnSaverTap { at ->
        if (cooldown.tryAcquire()) field.drop(at.x / canvasSize[0], at.y / canvasSize[1])
    }
    Canvas(Modifier.fillMaxSize()) {
        time.value
        canvasSize[0] = size.width
        canvasSize[1] = size.height
        field.ensure(size.width, size.height)
        if (lastRender[0] < 0f) field.render(300f)
        val bmp = field.bitmap ?: return@Canvas
        if (image[0] == null || image[0]!!.width != bmp.width || image[0]!!.height != bmp.height) image[0] = bmp.asImageBitmap()
        val w = size.width
        val h = size.height
        // Deep purple liquid, warmed from below by the bulb.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF13062A), Color(0xFF2A0A3A), Color(0xFF4A1230))))
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFF6A2A).copy(alpha = 0.32f), Color.Transparent), Offset(w / 2, h * 1.05f), size.maxDimension * 0.65f),
            size.maxDimension * 0.65f, Offset(w / 2, h * 1.05f),
        )
        drawImage(
            image[0]!!, srcOffset = IntOffset.Zero, srcSize = IntSize(field.gw, field.gh),
            dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.Low,
        )
        // Reflections on the curved glass.
        drawRect(
            Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.07f), 0.08f to Color.White.copy(alpha = 0.015f), 0.2f to Color.Transparent, 0.86f to Color.Transparent, 0.93f to Color.White.copy(alpha = 0.03f), 1f to Color.Transparent),
            blendMode = BlendMode.Plus,
        )
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), center, size.maxDimension * 0.72f))
        drawGrain(grain, 0.06f)
    }
}

// ---------------------------------------------------------------- Fireflies (refined)

private class Flies(val n: Int) {
    private val r = Random(11)
    val x = FloatArray(n) { r.nextFloat() }
    val depth = FloatArray(n) { r.nextFloat() }
    val y = FloatArray(n) { i -> bandTop(depth[i]) + r.nextFloat() * (0.97f - bandTop(depth[i])) }
    val heading = FloatArray(n) { r.nextFloat() * TAU }
    val speed = FloatArray(n) { 0.012f + r.nextFloat() * 0.02f }
    val nextFlash = FloatArray(n) { r.nextFloat() * 6f }
    val age = FloatArray(n) { 99f }
    val sync = IntArray(n)

    fun bandTop(d: Float) = 0.5f + 0.15f * (1f - d)

    fun step(dt: Float, t: Float) {
        for (i in 0 until n) {
            heading[i] += noise1(t * 0.35f + i * 7.1f, i) * 2.2f * dt
            val s = speed[i] * (0.5f + depth[i])
            x[i] += cos(heading[i]) * s * dt
            y[i] += sin(heading[i]) * s * dt * 0.6f
            if (x[i] < -0.05f) x[i] = 1.05f
            if (x[i] > 1.05f) x[i] = -0.05f
            val top = bandTop(depth[i])
            if (y[i] < top) heading[i] = lerpAngle(heading[i], PI.toFloat() / 2, dt * 2f)
            if (y[i] > 0.98f) heading[i] = lerpAngle(heading[i], -PI.toFloat() / 2, dt * 2f)
            age[i] += dt
            if (t >= nextFlash[i]) {
                age[i] = 0f
                nextFlash[i] = if (sync[i] > 0) {
                    sync[i]--
                    t + 1.3f
                } else {
                    t + 1.6f + r.nextFloat() * 3.4f
                }
            }
        }
    }

    /** Real fireflies synchronize. A tap starts a wave of matching flashes spreading out from it. */
    fun synchronize(tx: Float, ty: Float, t: Float) {
        for (i in 0 until n) {
            val d = sqrt((x[i] - tx).let { it * it } + (y[i] - ty).let { it * it })
            nextFlash[i] = t + 0.2f + d * 1.4f
            sync[i] = 3
        }
    }

    fun brightness(i: Int): Float {
        val a = age[i]
        return if (a < 0.12f) a / 0.12f else exp(-(a - 0.12f) * 2.2f)
    }

    private fun lerpAngle(a: Float, b: Float, k: Float) = a + (b - a) * k.coerceIn(0f, 1f)
}

private class Meadow(val far: Path, val hill: Path)

/** A soft line of round treetops, like a distant wood. */
private fun canopy(width: Float, base: Float, height: Float, bottom: Float, seed: Int): Path = Path().apply {
    val r = Random(seed)
    var x = -height * 0.5f
    while (x < width + height) {
        val rad = height * (0.35f + r.nextFloat() * 0.45f)
        val top = base - height * (0.2f + r.nextFloat() * 0.8f) + rad
        addOval(Rect(x - rad, top - rad, x + rad, top + rad))
        addRect(Rect(x - rad * 0.9f, top, x + rad * 0.9f, bottom))
        x += rad * (0.8f + r.nextFloat() * 0.6f)
    }
    addRect(Rect(0f, base, width, bottom))
}

@Composable
fun FirefliesRefinedSaver(ctx: SaverContext) {
    val flies = remember { Flies(if (ctx.preview) 40 else 80) }
    val stars = remember { StarSky(110, 23, 0f, 0.5f) }
    val grain = rememberGrainBrush()
    val blades = remember {
        val r = Random(4)
        // x, height, lean, width, sway phase
        List(170) { floatArrayOf(r.nextFloat() * 1.1f - 0.05f, 0.06f + r.nextFloat() * r.nextFloat() * 0.26f, (r.nextFloat() - 0.5f) * 0.06f, 0.004f + r.nextFloat() * 0.005f, r.nextFloat() * TAU) }
    }
    val midBlades = remember {
        val r = Random(9)
        List(120) { floatArrayOf(r.nextFloat(), 0.012f + r.nextFloat() * 0.03f, (r.nextFloat() - 0.5f) * 0.02f, 0.0025f, r.nextFloat() * TAU, r.nextFloat()) }
    }
    val scene = remember {
        SizeCache { s ->
            Meadow(
                far = canopy(s.width, s.height * 0.63f, s.height * 0.07f, s.height * 0.7f, 31),
                hill = dev.pridgey.beddery.savers.ridgePath(ridge(65, 8, 0.45f), s.width, s.height * 0.69f, s.height * 0.035f, s.height),
            )
        }
    }
    val cooldown = remember { Cooldown(12_000) }
    val time = rememberFrameTime { dt, t -> flies.step(dt, t) }
    val canvasSize = remember { floatArrayOf(1f, 1f) }
    OnSaverTap { at -> if (cooldown.tryAcquire()) flies.synchronize(at.x / canvasSize[0], at.y / canvasSize[1], time.value) }
    val grass = remember { Path() }

    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        canvasSize[0] = size.width
        canvasSize[1] = size.height
        val w = size.width
        val h = size.height
        val sc = scene.get(size)
        drawRect(Brush.verticalGradient(0f to Color(0xFF03071A), 0.42f to Color(0xFF081A30), 0.64f to Color(0xFF14363B), startY = 0f, endY = h * 0.7f))
        stars.draw(this, t, 0.55f, horizonFade = 0.5f)
        val moonGlow = Offset(w * 0.86f, h * 0.06f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF2E5268).copy(alpha = 0.4f), Color.Transparent), moonGlow, size.maxDimension * 0.55f), size.maxDimension * 0.55f, moonGlow)
        drawPath(sc.far, Brush.verticalGradient(listOf(Color(0xFF0C2129), Color(0xFF07141A)), h * 0.55f, h * 0.7f))
        drawFlies(flies, 0f, 0.33f)
        drawPath(sc.hill, Brush.verticalGradient(listOf(Color(0xFF0E2420), Color(0xFF061210), Color(0xFF020504)), h * 0.66f, h))
        drawFlies(flies, 0.33f, 0.75f)
        grass.reset()
        midBlades.forEach { b ->
            val bx = b[0] * w
            val by = h * (0.71f + b[5] * 0.05f)
            addBlade(grass, bx, by, b[1] * h, (b[2] + sin(t * 0.7f + b[4]) * 0.004f) * w, b[3] * w)
        }
        drawPath(grass, Color(0xFF051410))
        grass.reset()
        blades.forEach { b ->
            val sway = sin(t * 0.8f + b[4]) * 0.006f + sin(t * 0.23f + b[0] * 3f) * 0.004f
            addBlade(grass, b[0] * w, h + 2f, b[1] * h, (b[2] + sway) * w, b[3] * w)
        }
        drawPath(grass, Color(0xFF010302))
        drawFlies(flies, 0.75f, 1.01f)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), center, size.maxDimension * 0.8f))
        drawGrain(grain)
    }
}

private fun addBlade(path: Path, bx: Float, by: Float, height: Float, lean: Float, width: Float) {
    val tipX = bx + lean
    val tipY = by - height
    val cx = bx + lean * 0.25f
    val cy = by - height * 0.55f
    path.moveTo(bx - width / 2, by)
    path.quadraticTo(cx - width * 0.3f, cy, tipX, tipY)
    path.quadraticTo(cx + width * 0.3f, cy, bx + width / 2, by)
    path.close()
}

private val FlyGlow = Color(0xFFCBFF62)
private val FlyCore = Color(0xFFFBFFD8)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFlies(flies: Flies, minDepth: Float, maxDepth: Float) {
    val unit = size.minDimension / 400f
    for (i in 0 until flies.n) {
        val d = flies.depth[i]
        if (d < minDepth || d >= maxDepth) continue
        val a = flies.brightness(i)
        if (a < 0.02f) continue
        val o = Offset(flies.x[i] * size.width, flies.y[i] * size.height)
        if (d > 0.75f) {
            // Close to the camera and out of focus: a soft disc with a faint bright rim.
            val r = unit * (10f + (d - 0.75f) * 60f)
            drawCircle(
                Brush.radialGradient(0f to FlyGlow.copy(alpha = 0.22f * a), 0.72f to FlyGlow.copy(alpha = 0.2f * a), 0.86f to FlyGlow.copy(alpha = 0.3f * a), 1f to Color.Transparent, center = o, radius = r),
                r, o, blendMode = BlendMode.Plus,
            )
        } else {
            val glow = unit * (5f + d * 12f)
            drawCircle(Brush.radialGradient(listOf(FlyGlow.copy(alpha = 0.5f * a), FlyGlow.copy(alpha = 0.12f * a), Color.Transparent), o, glow), glow, o, blendMode = BlendMode.Plus)
            drawCircle(FlyCore.copy(alpha = a), unit * (0.6f + d * 1.1f), o)
        }
    }
}

// ---------------------------------------------------------------- Breathe (refined)

@Composable
fun BreatheRefinedSaver(ctx: SaverContext) {
    val pattern = ctx.settings.breathPattern
    val durations = floatArrayOf(pattern.inhale.toFloat(), pattern.holdIn.toFloat(), pattern.exhale.toFloat(), pattern.holdOut.toFloat())
    val cycle = durations.sum()
    val time = rememberFrameTime()
    val swirl = rememberNoiseBrush(256, 3, 5, seed = 17, contrast = 1.6f)
    val grain = rememberGrainBrush()
    val motes = remember { val r = Random(2); List(if (ctx.preview) 40 else 80) { floatArrayOf(r.nextFloat() * TAU, r.nextFloat(), 0.4f + r.nextFloat(), r.nextFloat()) } }
    fun phaseAt(t: Float): Triple<Int, Float, Float> {
        var p = t % cycle
        var i = 0
        while (i < 3 && p >= durations[i]) {
            p -= durations[i]; i++
        }
        val f = if (durations[i] > 0f) p / durations[i] else 1f
        return Triple(i, f, durations[i] - p)
    }
    val phase by remember(pattern) { derivedStateOf { phaseAt(time.value).first } }
    val remaining by remember(pattern) { derivedStateOf { kotlin.math.ceil(phaseAt(time.value).third.toDouble()).toInt() } }
    val accent = ctx.accent
    val cool = lerp(accent, Color(0xFF62E3D0), 0.5f)
    val warm = lerp(accent, Color(0xFFB08CFF), 0.5f)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        // In landscape, lift the orb so the clock fits underneath.
        val lift = if (landscape) 0.12f else 0f
        val label = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.055f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val (i, f, _) = phaseAt(t)
            val center = Offset(size.width / 2, size.height * (0.5f - lift))
            val ease = (1 - cos(f * PI.toFloat())) / 2f
            val fill = when (i) {
                0 -> ease
                1 -> 1f
                2 -> 1f - ease
                else -> 0f
            }
            val maxR = size.minDimension * if (landscape) 0.24f else 0.3f
            val r = maxR * (0.42f + 0.58f * fill)
            val tone = lerp(warm, cool, fill)
            // Room light follows the breath: cooler and brighter when full, warmer when empty.
            drawRect(Brush.radialGradient(listOf(lerp(Color(0xFF0B0F1E), tone, 0.18f + 0.1f * fill), Color(0xFF020308)), center, size.maxDimension * 0.75f))
            drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.22f + 0.1f * fill), Color.Transparent), center, r * 2.4f), r * 2.4f, center)

            // Motes drift in as you breathe in and out as you breathe out.
            motes.forEach { m ->
                val orbit = r * (1.18f + (1f - fill) * 0.7f * m[2] + 0.12f * m[1])
                val a = m[0] + t * 0.05f * (0.5f + m[3])
                val o = Offset(center.x + cos(a) * orbit, center.y + sin(a) * orbit)
                val alpha = 0.12f + 0.28f * m[3] * (0.6f + 0.4f * sin(t * 1.3f + m[0] * 3f))
                drawCircle(Color.White.copy(alpha = alpha), size.minDimension * 0.002f * m[2], o)
            }

            // The orb: glassy body with slowly swirling light inside.
            layer(Rect(center, r * 1.02f)) {
                drawCircle(Brush.radialGradient(listOf(lerp(tone, Color.White, 0.35f).copy(alpha = 0.85f), tone.copy(alpha = 0.5f), lerp(tone, Color(0xFF0A1030), 0.6f).copy(alpha = 0.55f)), center, r), r, center)
                drawRect(
                    swirl.place(r * 2.6f, r * 2.6f, center.x - r * 1.3f, center.y - r * 1.3f, degrees = t * 4f, pivotX = center.x, pivotY = center.y),
                    topLeft = center - Offset(r, r), size = Size(r * 2, r * 2),
                    colorFilter = ColorFilter.tint(Color.White, BlendMode.SrcIn), alpha = 0.16f, blendMode = BlendMode.SrcAtop,
                )
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.32f), Color.Transparent), center - Offset(r * 0.38f, r * 0.42f), r * 0.55f),
                    r, center, blendMode = BlendMode.SrcAtop,
                )
            }
            drawCircle(Color.White.copy(alpha = 0.22f), r, center, style = Stroke(width = density * 1f))

            // Progress through the current step, on a quiet outer ring.
            val ringR = maxR * 1.12f
            drawCircle(Color.White.copy(alpha = 0.07f), ringR, center, style = Stroke(width = density * 2f))
            drawArc(
                Color.White.copy(alpha = 0.5f), -90f, 360f * f, useCenter = false,
                topLeft = center - Offset(ringR, ringR), size = Size(ringR * 2, ringR * 2),
                style = Stroke(width = density * 2f, cap = StrokeCap.Round),
            )
            // A ripple sent out at the start of each exhale.
            if (i == 2) {
                val rr = maxR * (1f + f * 0.9f)
                drawCircle(tone.copy(alpha = 0.25f * (1f - f)), rr, center, style = Stroke(width = density * 1.5f))
            }
        }
        Column(Modifier.align(androidx.compose.ui.BiasAlignment(0f, -lift * 2f)), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(phase, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) }, label = "phase") { ph ->
                val res = when (ph) {
                    0 -> R.string.breathe_in
                    2 -> R.string.breathe_out
                    else -> R.string.breathe_hold
                }
                Text(stringResource(res), color = Color.White, fontSize = label, fontWeight = FontWeight.Light)
            }
            Text("$remaining", color = Color.White.copy(alpha = 0.55f), fontSize = label * 0.6f, style = TabularNumbers)
        }
    }
}
