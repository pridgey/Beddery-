package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import dev.pridgey.beddery.savers.Comet
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.TAU
import dev.pridgey.beddery.savers.TextureBrush
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.layer
import dev.pridgey.beddery.savers.length
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.rememberNoiseBrush
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Shared by both solar systems: a comet when you tap (once a minute), and very rarely on its own. */
class CometLauncher {
    val comet = Comet()
    private val cooldown = Cooldown(60_000)
    private var seed = 1
    private var nextFree = 900f + Random.nextFloat() * 600f

    fun tap() {
        if (!comet.active && cooldown.tryAcquire()) comet.launch(seed++)
    }

    fun step(dt: Float, t: Float) {
        if (t > nextFree) {
            if (!comet.active) comet.launch(seed++)
            nextFree = t + 1200f + Random.nextFloat() * 900f
        }
        comet.step(dt)
    }
}

// ---------------------------------------------------------------- Orbits (refined)

private enum class Kind { ROCKY, VENUS, EARTH, BANDED, RINGED, ICE }

private class Body(
    val orbit: Float, val period: Float, val size: Float,
    val light: Color, val base: Color, val dark: Color,
    val phase: Float, val kind: Kind,
)

private val Bodies = listOf(
    Body(0.13f, 9f, 0.009f, Color(0xFFD2CBC1), Color(0xFF8E877D), Color(0xFF3B3833), 0.3f, Kind.ROCKY),
    Body(0.19f, 15f, 0.015f, Color(0xFFFFF2D2), Color(0xFFE2C48E), Color(0xFF6E5633), 2.1f, Kind.VENUS),
    Body(0.26f, 23f, 0.016f, Color(0xFFA9DAFF), Color(0xFF2F70D2), Color(0xFF0B2350), 4.0f, Kind.EARTH),
    Body(0.34f, 37f, 0.012f, Color(0xFFFFB892), Color(0xFFC8562F), Color(0xFF4A1A0E), 1.2f, Kind.ROCKY),
    Body(0.53f, 82f, 0.036f, Color(0xFFF6E6CB), Color(0xFFC9A27A), Color(0xFF5A3E26), 5.1f, Kind.BANDED),
    Body(0.67f, 135f, 0.029f, Color(0xFFF8EBC6), Color(0xFFD9BE85), Color(0xFF6B5530), 3.3f, Kind.RINGED),
    Body(0.79f, 215f, 0.021f, Color(0xFFDDFBFF), Color(0xFF86D8E3), Color(0xFF1F5A66), 0.8f, Kind.ICE),
    Body(0.9f, 330f, 0.02f, Color(0xFFB5CBFF), Color(0xFF3E5FE0), Color(0xFF101F5C), 2.6f, Kind.ICE),
)

private val JupiterBands = listOf(-0.62f to 0.1f, -0.38f to 0.16f, -0.12f to 0.08f, 0.1f to 0.18f, 0.36f to 0.1f, 0.6f to 0.14f)

@Composable
fun OrbitsRefinedSaver(ctx: SaverContext) {
    val stars = remember { StarSky(if (ctx.preview) 160 else 300, 9) }
    val nebulaA = rememberNoiseBrush(256, 2, 6, seed = 31, contrast = 1.9f)
    val nebulaB = rememberNoiseBrush(256, 3, 6, seed = 32, contrast = 2.2f)
    val corona = rememberNoiseBrush(128, 6, 4, seed = 33, contrast = 1.6f)
    val grain = rememberGrainBrush()
    val belt = remember { val r = Random(5); List(if (ctx.preview) 90 else 200) { floatArrayOf(0.405f + r.nextFloat() * 0.06f, r.nextFloat() * TAU, 55f + r.nextFloat() * 15f, r.nextFloat()) } }
    val launcher = remember { CometLauncher() }
    val time = rememberFrameTime { dt, t -> launcher.step(dt, t) }
    OnSaverTap { launcher.tap() }
    val disc = remember { Path() }

    Canvas(Modifier.fillMaxSize()) {
        val t = time.value + 400f
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF010207))
        drawNebula(nebulaA, nebulaB, t)
        stars.draw(this, t, 0.6f, horizonFade = 0f)

        val c = center
        val scale = minOf(w, h / 0.55f) * 0.5f
        val tilt = 0.38f
        fun pos(b: Body): Pair<Offset, Float> {
            val a = t * TAU / b.period + b.phase
            return Offset(c.x + cos(a) * b.orbit * scale, c.y + sin(a) * b.orbit * scale * tilt) to sin(a)
        }
        // Orbits: brighter on the near side.
        Bodies.forEach { b ->
            val rx = b.orbit * scale
            val tl = Offset(c.x - rx, c.y - rx * tilt)
            val sz = Size(rx * 2, rx * 2 * tilt)
            drawArc(Color.White.copy(alpha = 0.045f), 180f, 180f, false, tl, sz, style = Stroke(density * 0.6f))
            drawArc(Color.White.copy(alpha = 0.09f), 0f, 180f, false, tl, sz, style = Stroke(density * 0.7f))
        }
        fun drawBelt(front: Boolean) = belt.forEach { a ->
            val ang = a[1] + t * TAU / a[2]
            if ((sin(ang) >= 0) != front) return@forEach
            val o = Offset(c.x + cos(ang) * a[0] * scale, c.y + sin(ang) * a[0] * scale * tilt)
            drawCircle(Color(0xFFCFC4B4).copy(alpha = (0.18f + 0.3f * a[3]) * (0.6f + 0.4f * sin(ang))), density * (0.5f + 0.6f * a[3]), o)
        }
        fun drawBodies(front: Boolean) = Bodies.forEach { b ->
            val (o, depth) = pos(b)
            if ((depth >= 0) != front) return@forEach
            val r = b.size * scale * (0.86f + 0.14f * depth) * 2.1f
            drawBody(b, o, r, c, t, disc)
        }

        drawBelt(front = false)
        drawBodies(front = false)
        drawSun(c, scale * 0.06f, corona, t)
        launcher.comet.draw(this, c, scale, tilt)
        drawBelt(front = true)
        drawBodies(front = true)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)), center, size.maxDimension * 0.75f))
        drawGrain(grain)
    }
}

private fun DrawScope.drawNebula(a: TextureBrush, b: TextureBrush, t: Float) {
    val w = size.width
    val h = size.height
    val big = size.maxDimension * 1.6f
    drawRect(a.place(big, big, -t * w * 0.002f, -h * 0.2f), colorFilter = ColorFilter.tint(Color(0xFF3A2470), BlendMode.SrcIn), alpha = 0.28f, blendMode = BlendMode.Plus)
    drawRect(b.place(big * 0.8f, big * 0.8f, w * 0.3f, t * h * 0.0015f), colorFilter = ColorFilter.tint(Color(0xFF0D4A6E), BlendMode.SrcIn), alpha = 0.22f, blendMode = BlendMode.Plus)
}

private fun DrawScope.drawSun(c: Offset, r: Float, corona: TextureBrush, t: Float) {
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9A3C).copy(alpha = 0.12f), Color.Transparent), c, r * 10f), r * 10f, c)
    // Turbulent corona: noise spun slowly and faded out from the edge of the disc.
    // The layer sits just inside the rectangles drawn into it, so no edge pixels are left half-masked.
    layer(Rect(c, r * 4.2f).deflate(2f)) {
        drawRect(
            corona.place(r * 3f, r * 3f, c.x, c.y, degrees = t * 1.5f, pivotX = c.x, pivotY = c.y),
            topLeft = c - Offset(r * 4.2f, r * 4.2f), size = Size(r * 8.4f, r * 8.4f),
            colorFilter = ColorFilter.tint(Color(0xFFFFB44D), BlendMode.SrcIn), alpha = 0.7f,
        )
        // The mask must cover the whole layer: DstIn only affects pixels it draws over.
        drawRect(
            Brush.radialGradient(0f to Color.Black, 0.24f to Color.Black.copy(alpha = 0.7f), 0.5f to Color.Black.copy(alpha = 0.15f), 1f to Color.Transparent, center = c, radius = r * 4.2f),
            topLeft = c - Offset(r * 4.2f, r * 4.2f), size = Size(r * 8.4f, r * 8.4f), blendMode = BlendMode.DstIn,
        )
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD27A).copy(alpha = 0.55f), Color.Transparent), c, r * 2.6f), r * 2.6f, c, blendMode = BlendMode.Plus)
    drawCircle(Brush.radialGradient(0f to Color(0xFFFFFEF4), 0.6f to Color(0xFFFFE19A), 1f to Color(0xFFFF9C3A), center = c, radius = r), r, c)
}

private fun DrawScope.drawBody(b: Body, o: Offset, r: Float, sun: Offset, t: Float, disc: Path) {
    val toSun = (sun - o).let { it / it.length().coerceAtLeast(1f) }
    if (b.kind == Kind.RINGED) drawRings(o, r, back = true)
    drawCircle(Brush.radialGradient(listOf(b.light, b.base, b.dark), o + toSun * r * 0.5f, r * 1.7f), r, o)
    disc.reset()
    disc.addOval(Rect(o, r))
    when (b.kind) {
        Kind.BANDED, Kind.RINGED -> clipPath(disc) {
            JupiterBands.forEachIndexed { i, (y, hgt) ->
                val color = if (i % 2 == 0) b.dark else b.light
                drawRect(color.copy(alpha = if (b.kind == Kind.BANDED) 0.28f else 0.14f), Offset(o.x - r, o.y + y * r), Size(r * 2, hgt * r))
            }
            if (b.kind == Kind.BANDED) {
                // The Great Red Spot, carried round as the planet spins.
                val spin = ((t * 0.02f) % 1f) * 2.4f - 1.2f
                drawOval(Color(0xFFB8643E).copy(alpha = 0.5f), Offset(o.x + spin * r - r * 0.2f, o.y + r * 0.22f), Size(r * 0.4f, r * 0.2f))
            }
        }
        Kind.EARTH -> clipPath(disc) {
            val spin = (t * 0.03f) % 1f
            for (k in 0..2) {
                val x = o.x + ((spin + k * 0.37f) % 1f * 2.6f - 1.3f) * r
                drawOval(Color(0xFF4F9A5A).copy(alpha = 0.55f), Offset(x - r * 0.3f, o.y - r * (0.5f - k * 0.35f)), Size(r * 0.6f, r * 0.42f))
            }
            drawRect(Color.White.copy(alpha = 0.18f), Offset(o.x - r, o.y - r * 0.25f), Size(r * 2, r * 0.12f))
        }
        else -> Unit
    }
    // Night side.
    drawCircle(
        Brush.linearGradient(0f to Color.Transparent, 0.46f to Color.Transparent, 0.7f to Color.Black.copy(alpha = 0.72f), 1f to Color.Black.copy(alpha = 0.93f), start = o + toSun * r, end = o - toSun * r),
        r, o,
    )
    if (b.kind == Kind.EARTH || b.kind == Kind.VENUS || b.kind == Kind.ICE) {
        drawCircle(Brush.linearGradient(listOf(b.light.copy(alpha = 0.6f), Color.Transparent), o + toSun * r, o), r * 1.03f, o, style = Stroke(r * 0.12f))
    }
    if (b.kind == Kind.RINGED) drawRings(o, r, back = false)
    if (b.kind == Kind.EARTH) {
        val a = t * TAU / 4f
        val mo = Offset(o.x + cos(a) * r * 2.3f, o.y + sin(a) * r * 0.8f)
        if (sin(a) < 0) {
            // Behind the planet: draw only if it isn't hidden.
            if ((mo - o).length() > r) drawCircle(Color(0xFF9C9890), r * 0.27f, mo)
        } else drawCircle(Color(0xFFC9C5BC), r * 0.27f, mo)
    }
}

private val RingBands = listOf(Triple(1.38f, 0.12f, 0.2f), Triple(1.64f, 0.26f, 0.5f), Triple(1.98f, 0.18f, 0.36f), Triple(2.16f, 0.05f, 0.18f))

private fun DrawScope.drawRings(o: Offset, r: Float, back: Boolean) {
    RingBands.forEach { (k, wdt, a) ->
        val rx = r * k
        val ry = rx * 0.3f
        drawArc(
            Color(0xFFE9D8AE).copy(alpha = a), if (back) 180f else 0f, 180f, false,
            Offset(o.x - rx, o.y - ry), Size(rx * 2, ry * 2), style = Stroke(r * wdt),
        )
    }
}

// ---------------------------------------------------------------- Starfield (refined)

private class Hyperspace(val n: Int) {
    private val r = Random(1)
    val x = FloatArray(n) { r.nextFloat() * 2 - 1 }
    val y = FloatArray(n) { r.nextFloat() * 2 - 1 }
    val z = FloatArray(n) { 0.05f + r.nextFloat() * 0.95f }
    val tint = FloatArray(n) { r.nextFloat() }
    var speed = 0.2f
    var warp = 0f
    private var warpStart = -100f

    fun jump(t: Float) {
        warpStart = t
    }

    fun step(dt: Float, t: Float) {
        val cruise = 0.1f + 0.06f * (0.5f + 0.5f * sin(t * 0.09f))
        val since = t - warpStart
        warp = when {
            since < 0f -> 0f
            since < 1.5f -> smooth(since / 1.5f)
            since < 5f -> 1f
            since < 8f -> 1f - smooth((since - 5f) / 3f)
            else -> 0f
        }
        // An occasional jump on its own, every few minutes.
        if (since > 200f && sin(t * 0.013f) > 0.999f) jump(t)
        speed = cruise + warp * 1.4f
        for (i in 0 until n) {
            z[i] -= speed * dt
            if (z[i] <= 0.02f) {
                x[i] = r.nextFloat() * 2 - 1
                y[i] = r.nextFloat() * 2 - 1
                z[i] = 1f
            }
        }
    }

    private fun smooth(f: Float) = f * f * (3 - 2 * f)
}

@Composable
fun StarfieldRefinedSaver(ctx: SaverContext) {
    val field = remember { Hyperspace(if (ctx.preview) 200 else 600) }
    val distant = remember { StarSky(if (ctx.preview) 120 else 260, 53) }
    val nebula = rememberNoiseBrush(256, 2, 6, seed = 51, contrast = 2f)
    val nebula2 = rememberNoiseBrush(256, 3, 5, seed = 52, contrast = 2.4f)
    val grain = rememberGrainBrush()
    val cooldown = remember { Cooldown(20_000) }
    val time = rememberFrameTime { dt, t -> field.step(dt, t) }
    OnSaverTap { if (cooldown.tryAcquire()) field.jump(time.value) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        drawRect(Color(0xFF00010A))
        val c = center
        val big = size.maxDimension * (1.5f + 0.1f * sin(t * 0.02f))
        drawRect(
            nebula.place(big, big, c.x - big / 2, c.y - big / 2, degrees = t * 0.6f, pivotX = c.x, pivotY = c.y),
            colorFilter = ColorFilter.tint(Color(0xFF213A8C), BlendMode.SrcIn), alpha = 0.26f + 0.1f * field.warp, blendMode = BlendMode.Plus,
        )
        drawRect(
            nebula2.place(big * 0.7f, big * 0.7f, c.x, c.y, degrees = -t * 0.4f, pivotX = c.x, pivotY = c.y),
            colorFilter = ColorFilter.tint(Color(0xFF5A1F6E), BlendMode.SrcIn), alpha = 0.18f, blendMode = BlendMode.Plus,
        )
        distant.draw(this, t, 0.55f * (1f - field.warp * 0.6f), horizonFade = 0f, scale = 0.8f)
        if (field.warp > 0f) {
            drawCircle(Brush.radialGradient(listOf(Color(0xFFBFD6FF).copy(alpha = 0.22f * field.warp), Color.Transparent), c, size.minDimension * 0.45f), size.minDimension * 0.45f, c, blendMode = BlendMode.Plus)
        }
        val f = size.maxDimension * 0.5f
        val streak = field.speed * 0.13f
        for (i in 0 until field.n) {
            val z = field.z[i]
            val sx = c.x + field.x[i] / z * f
            val sy = c.y + field.y[i] / z * f
            if (sx < -60 || sx > size.width + 60 || sy < -60 || sy > size.height + 60) continue
            val bright = (0.12f + 0.88f * (1f - z)).coerceIn(0f, 1f).pow(0.9f)
            val color = lerp(Color(0xFFA9C4FF), Color(0xFFFFF0DA), field.tint[i])
            val width = (0.7f + bright * 2.8f) * density / 1.4f
            // A trail that fades towards its tail, in a few segments.
            var hx = sx
            var hy = sy
            for (k in 1..4) {
                val zk = z + streak * k / 4f
                val px = c.x + field.x[i] / zk * f
                val py = c.y + field.y[i] / zk * f
                val a = bright * (1f - (k - 1) / 4f).pow(1.6f)
                drawLine(color.copy(alpha = a), Offset(px, py), Offset(hx, hy), strokeWidth = width, cap = StrokeCap.Round)
                hx = px
                hy = py
            }
            if (bright > 0.6f) drawCircle(color.copy(alpha = (bright - 0.6f) * 0.6f), width * 2.2f, Offset(sx, sy), blendMode = BlendMode.Plus)
        }
        drawGrain(grain, 0.05f)
    }
}

// ---------------------------------------------------------------- Constellation (refined)

private class Network(val n: Int) {
    private val r = Random(21)
    val x = FloatArray(n) { r.nextFloat() }
    val y = FloatArray(n) { r.nextFloat() }
    val depth = FloatArray(n) { 0.35f + r.nextFloat() * 0.65f }
    private val vx = FloatArray(n) { (r.nextFloat() - 0.5f) * 0.022f }
    private val vy = FloatArray(n) { (r.nextFloat() - 0.5f) * 0.022f }
    private val ix = FloatArray(n)
    private val iy = FloatArray(n)

    // Signals travelling along links: from, to, progress, hops left.
    class Signal(var from: Int, var to: Int, var p: Float, var hops: Int)

    val signals = ArrayList<Signal>()
    private var nextSignal = 1.5f
    var maxD2 = 0.04f
    var ripple = Offset.Zero
    var rippleAge = 99f

    fun step(dt: Float, t: Float, aspect: Float) {
        for (i in 0 until n) {
            x[i] += (vx[i] * depth[i] + ix[i]) * dt
            y[i] += (vy[i] * depth[i] + iy[i]) * dt * aspect
            ix[i] *= 1f - dt * 1.2f
            iy[i] *= 1f - dt * 1.2f
            if (x[i] < 0f) { x[i] = 0f; vx[i] = -vx[i]; ix[i] = -ix[i] }
            if (x[i] > 1f) { x[i] = 1f; vx[i] = -vx[i]; ix[i] = -ix[i] }
            if (y[i] < 0f) { y[i] = 0f; vy[i] = -vy[i]; iy[i] = -iy[i] }
            if (y[i] > 1f) { y[i] = 1f; vy[i] = -vy[i]; iy[i] = -iy[i] }
        }
        rippleAge += dt
        if (t > nextSignal) {
            fire(r.nextInt(n), 4 + r.nextInt(4))
            nextSignal = t + 1.8f + r.nextFloat() * 2.5f
        }
        val it = signals.iterator()
        val added = ArrayList<Signal>()
        while (it.hasNext()) {
            val s = it.next()
            s.p += dt * 1.1f
            if (s.p >= 1f) {
                it.remove()
                if (s.hops > 0) neighbor(s.to, s.from)?.let { added += Signal(s.to, it, 0f, s.hops - 1) }
            }
        }
        signals += added
    }

    fun fire(from: Int, hops: Int) {
        neighbor(from, -1)?.let { signals += Signal(from, it, 0f, hops) }
    }

    fun nudge(tx: Float, ty: Float) {
        ripple = Offset(tx, ty)
        rippleAge = 0f
        var nearest = 0
        var best = Float.MAX_VALUE
        for (i in 0 until n) {
            val dx = x[i] - tx
            val dy = y[i] - ty
            val d = sqrt(dx * dx + dy * dy)
            if (d < best) { best = d; nearest = i }
            if (d < 0.3f) {
                val push = (0.3f - d) * 0.5f
                ix[i] += dx / d.coerceAtLeast(0.01f) * push
                iy[i] += dy / d.coerceAtLeast(0.01f) * push
            }
        }
        fire(nearest, 8)
        fire(nearest, 8)
    }

    private fun neighbor(i: Int, exclude: Int): Int? {
        var choice: Int? = null
        var bestScore = -1f
        for (j in 0 until n) {
            if (j == i || j == exclude) continue
            val dx = x[j] - x[i]
            val dy = y[j] - y[i]
            val d2 = dx * dx + dy * dy
            if (d2 < maxD2) {
                val score = r.nextFloat()
                if (score > bestScore) { bestScore = score; choice = j }
            }
        }
        return choice
    }
}

@Composable
fun ConstellationRefinedSaver(ctx: SaverContext) {
    val net = remember { Network(if (ctx.preview) 40 else 92) }
    val nebula = rememberNoiseBrush(256, 2, 6, seed = 61, contrast = 2.1f)
    val grain = rememberGrainBrush()
    val aspect = remember { floatArrayOf(1f) }
    val canvasSize = remember { floatArrayOf(1f, 1f) }
    val time = rememberFrameTime { dt, t -> net.step(dt, t, aspect[0]) }
    val cooldown = remember { Cooldown(4_000) }
    OnSaverTap { at -> if (cooldown.tryAcquire()) net.nudge(at.x / canvasSize[0], at.y / canvasSize[1]) }
    val accent = ctx.accent
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        canvasSize[0] = w
        canvasSize[1] = h
        aspect[0] = w / h
        drawRect(Brush.radialGradient(listOf(Color(0xFF0A1024), Color(0xFF02030A)), center, size.maxDimension * 0.75f))
        val big = size.maxDimension * 1.4f
        drawRect(nebula.place(big, big, -t * w * 0.0015f, 0f), colorFilter = ColorFilter.tint(accent, BlendMode.SrcIn), alpha = 0.09f, blendMode = BlendMode.Plus)
        val maxD = size.minDimension * 0.24f
        net.maxD2 = (maxD / w).let { it * it }
        val maxD2 = maxD * maxD
        for (i in 0 until net.n) {
            val ax = net.x[i] * w
            val ay = net.y[i] * h
            for (j in i + 1 until net.n) {
                val dx = net.x[j] * w - ax
                val dy = net.y[j] * h - ay
                val d2 = dx * dx + dy * dy
                if (d2 < maxD2) {
                    val k = 1f - sqrt(d2) / maxD
                    val a = k * sqrt(k) * 0.75f * (net.depth[i] + net.depth[j]) / 2f
                    drawLine(accent.copy(alpha = a), Offset(ax, ay), Offset(ax + dx, ay + dy), strokeWidth = density * 0.6f)
                }
            }
        }
        // Signals hopping from star to star.
        net.signals.forEach { s ->
            val a = Offset(net.x[s.from] * w, net.y[s.from] * h)
            val b = Offset(net.x[s.to] * w, net.y[s.to] * h)
            val p = s.p.coerceIn(0f, 1f)
            val head = a + (b - a) * p
            val tail = a + (b - a) * (p - 0.25f).coerceAtLeast(0f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, lerp(accent, Color.White, 0.5f)), tail, head), tail, head, strokeWidth = density * 1.4f, cap = StrokeCap.Round, blendMode = BlendMode.Plus)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f), Color.Transparent), head, density * 6f), density * 6f, head, blendMode = BlendMode.Plus)
        }
        for (i in 0 until net.n) {
            val o = Offset(net.x[i] * w, net.y[i] * h)
            val d = net.depth[i]
            val tw = 0.65f + 0.35f * sin(t * 1.2f + i * 1.7f)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f * tw * d), Color.Transparent), o, density * 9f * d), density * 9f * d, o, blendMode = BlendMode.Plus)
            drawCircle(Color.White.copy(alpha = (0.45f + 0.55f * d) * tw), density * (0.7f + 1.3f * d), o)
        }
        if (net.rippleAge < 2.5f) {
            val p = net.rippleAge / 2.5f
            val o = Offset(net.ripple.x * w, net.ripple.y * h)
            drawCircle(accent.copy(alpha = 0.35f * (1f - p)), size.minDimension * 0.35f * p, o, style = Stroke(density * 1.2f))
        }
        drawGrain(grain, 0.05f)
    }
}
