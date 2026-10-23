package dev.pridgey.beddery.savers

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

const val TAU = (2 * PI).toFloat()

/** Deterministic pseudo-random value in 0..1 for an integer (or pair). */
fun hash(n: Int): Float {
    var x = n * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0x7FFFFFFF) / 2147483647f
}

fun hash(a: Int, b: Int): Float = hash(a * 73856093 xor b * 19349663)

/** Smooth 1D value noise, roughly in -1..1. */
fun noise1(x: Float, seed: Int = 0): Float {
    val i = floor(x).toInt()
    val f = x - i
    val u = f * f * (3 - 2 * f)
    val a = hash(i, seed) * 2 - 1
    val b = hash(i + 1, seed) * 2 - 1
    return a + (b - a) * u
}

fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

fun Offset.length(): Float = sqrt(x * x + y * y)

private val HotStar = Color(0xFFA8C0FF)
private val WarmStar = Color(0xFFFFD7A8)

/**
 * A night sky full of stars with a realistic spread of brightness and color temperature.
 * Positions are in 0..1 space; [top] and [bottom] limit where they appear.
 */
class StarSky(count: Int, seed: Int, private val top: Float = 0f, private val bottom: Float = 1f) {
    private val r = Random(seed)
    private val x = FloatArray(count) { r.nextFloat() }
    private val y = FloatArray(count) { top + r.nextFloat() * (bottom - top) }
    // Most stars are faint, a few are bright.
    private val mag = FloatArray(count) { r.nextFloat().pow(3.2f) }
    private val color = Array(count) {
        val k = r.nextFloat()
        when {
            k < 0.25f -> lerp(Color.White, HotStar, 0.5f + r.nextFloat() * 0.5f)
            k > 0.8f -> lerp(Color.White, WarmStar, 0.4f + r.nextFloat() * 0.6f)
            else -> Color.White
        }
    }
    private val phase = FloatArray(count) { r.nextFloat() * TAU }
    private val rate = FloatArray(count) { 0.6f + r.nextFloat() * 2.2f }

    /** [horizonFade] fades stars out over the lowest part of their band, like real atmospheric haze. */
    fun draw(scope: DrawScope, t: Float, alpha: Float = 1f, horizonFade: Float = 0.25f, scale: Float = 1f) = with(scope) {
        val unit = size.minDimension / 420f * scale
        val band = bottom - top
        for (i in x.indices) {
            val ny = (y[i] - top) / band
            val haze = if (horizonFade > 0f) 1f - smoothstep(1f - horizonFade, 1f, ny) else 1f
            // Faint stars twinkle more; low stars scintillate more.
            val twinkle = 1f - (0.25f + 0.35f * ny) * (0.5f + 0.5f * sin(t * rate[i] + phase[i])) * (1f - mag[i] * 0.6f)
            val a = (0.18f + 0.82f * mag[i]) * twinkle * haze * alpha
            if (a < 0.02f) continue
            val o = Offset(x[i] * size.width, y[i] * size.height)
            val rad = (0.4f + mag[i] * 1.2f) * unit
            if (mag[i] > 0.7f) {
                drawCircle(Brush.radialGradient(listOf(color[i].copy(alpha = a * 0.22f), Color.Transparent), o, rad * 3.5f), rad * 3.5f, o)
            }
            drawCircle(color[i].copy(alpha = a.coerceIn(0f, 1f)), rad, o)
        }
    }
}

/** Shooting stars that appear on request or now and then on their own. */
class Meteors(private val seed: Int = 5) {
    private class M(var x: Float, var y: Float, val dx: Float, val dy: Float, val speed: Float, var age: Float, val life: Float, val length: Float)

    private val active = ArrayList<M>()
    private val r = Random(seed)
    private var next = 40f + r.nextFloat() * 80f

    /** Launches one starting near [at] (0..1 coordinates), or somewhere in the upper sky. */
    fun launch(at: Offset? = null) {
        val angle = (PI.toFloat() * (0.12f + r.nextFloat() * 0.18f)) * if (r.nextBoolean()) 1 else -1
        val start = at ?: Offset(0.2f + r.nextFloat() * 0.6f, 0.05f + r.nextFloat() * 0.25f)
        active += M(start.x, start.y, cos(angle) * if (angle > 0) 1 else -1, abs(sin(angle)), 0.55f + r.nextFloat() * 0.35f, 0f, 0.9f + r.nextFloat() * 0.6f, 0.08f + r.nextFloat() * 0.07f)
    }

    fun step(dt: Float, t: Float, spontaneous: Boolean = true) {
        if (spontaneous && t > next) {
            launch()
            next = t + 60f + r.nextFloat() * 150f
        }
        val it = active.iterator()
        while (it.hasNext()) {
            val m = it.next()
            m.age += dt
            m.x += m.dx * m.speed * dt
            m.y += m.dy * m.speed * dt
            if (m.age > m.life) it.remove()
        }
    }

    fun draw(scope: DrawScope, maxY: Float = 1f) = with(scope) {
        val unit = size.minDimension
        for (m in active) {
            val p = m.age / m.life
            val a = sin(p * PI.toFloat()).coerceIn(0f, 1f)
            val head = Offset(m.x * size.width, m.y * size.height)
            if (head.y > maxY * size.height) continue
            val len = m.length * unit * (0.4f + 0.6f * a)
            val tail = head - Offset(m.dx, m.dy) * len
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, Color(0xFFDDE8FF).copy(alpha = 0.85f * a)), tail, head),
                tail, head, strokeWidth = unit * 0.0025f, cap = StrokeCap.Round,
            )
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a), Color.Transparent), head, unit * 0.008f), unit * 0.008f, head, blendMode = BlendMode.Plus)
        }
    }
}

/** Fractal ridgeline heights in 0..1, for mountains and hills. */
fun ridge(points: Int, seed: Int, roughness: Float = 0.55f): FloatArray {
    val r = Random(seed)
    var n = 1
    while (n < points - 1) n *= 2
    val h = FloatArray(n + 1)
    h[0] = r.nextFloat()
    h[n] = r.nextFloat()
    var step = n
    var amp = 1f
    while (step > 1) {
        val half = step / 2
        var i = half
        while (i < n) {
            h[i] = (h[i - half] + h[i + half]) / 2 + (r.nextFloat() - 0.5f) * amp
            i += step
        }
        step = half
        amp *= roughness
    }
    val lo = h.min()
    val hi = h.max()
    return FloatArray(points) { i -> (h[i * n / (points - 1)] - lo) / (hi - lo).coerceAtLeast(1e-4f) }
}

/** A closed silhouette path following [heights] across [width], from [baseY] up by [amplitude]. */
fun ridgePath(heights: FloatArray, width: Float, baseY: Float, amplitude: Float, bottom: Float): Path = Path().apply {
    moveTo(0f, bottom)
    heights.forEachIndexed { i, v -> lineTo(width * i / (heights.size - 1f), baseY - v * amplitude) }
    lineTo(width, bottom)
    close()
}

/** Adds a conifer silhouette to [path]: a trunk and jagged layers of branches. */
fun Path.addPine(x: Float, baseY: Float, height: Float, seed: Int) {
    val w = height * (0.32f + hash(seed) * 0.1f)
    val tiers = 6 + (hash(seed + 1) * 3).toInt()
    moveTo(x, baseY - height)
    // Right side down, zig-zagging branch tips.
    for (k in 1..tiers) {
        val f = k / tiers.toFloat()
        val y = baseY - height + height * 0.92f * f
        val reach = w * 0.5f * f * (0.85f + 0.3f * hash(seed + k * 7))
        lineTo(x + reach, y)
        lineTo(x + reach * 0.45f, y - height * 0.02f)
    }
    lineTo(x + w * 0.05f, baseY)
    lineTo(x - w * 0.05f, baseY)
    for (k in tiers downTo 1) {
        val f = k / tiers.toFloat()
        val y = baseY - height + height * 0.92f * f
        val reach = w * 0.5f * f * (0.85f + 0.3f * hash(seed + k * 13))
        lineTo(x - reach * 0.45f, y - height * 0.02f)
        lineTo(x - reach, y)
    }
    close()
}

/** A treeline of pines along [baseY], as one path. */
fun pineLine(width: Float, baseY: Float, minH: Float, maxH: Float, spacing: Float, seed: Int, bottom: Float): Path = Path().apply {
    var x = -spacing
    var i = 0
    while (x < width + spacing) {
        val h = minH + (maxH - minH) * hash(seed + i * 31)
        addPine(x, baseY + hash(seed + i * 5) * minH * 0.15f, h, seed + i * 101)
        x += spacing * (0.55f + hash(seed + i * 17) * 0.9f)
        i++
    }
    addRect(androidx.compose.ui.geometry.Rect(0f, baseY, width, bottom))
}

/** Caches something that depends only on the canvas size (paths, layouts). */
class SizeCache<T>(private val build: (Size) -> T) {
    private var key: Size? = null
    private var value: T? = null

    fun get(size: Size): T {
        if (size != key || value == null) {
            value = build(size)
            key = size
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }
}

/**
 * A comet that sweeps past the sun on a curved path. Its ion tail always points away from
 * the sun and grows as it gets closer, like the real thing.
 */
class Comet {
    private var age = -1f
    private var duration = 16f
    private var angle = 0f
    private var flip = 1f
    private val trail = ArrayDeque<Offset>()

    val active get() = age >= 0f

    fun launch(seed: Int) {
        val r = Random(seed)
        age = 0f
        duration = 14f + r.nextFloat() * 6f
        angle = r.nextFloat() * TAU
        flip = if (r.nextBoolean()) 1f else -1f
        trail.clear()
    }

    /** Position relative to the sun in units of the system radius, for progress s in -1..1. */
    private fun position(s: Float): Offset {
        // A parabola: far away at both ends, closest to the sun in the middle.
        val px = s * 1.5f
        val py = (s * s * 1.1f - 0.12f) * flip
        val c = cos(angle)
        val sn = sin(angle)
        return Offset(px * c - py * sn, px * sn + py * c)
    }

    fun step(dt: Float) {
        if (age < 0f) return
        age += dt
        if (age > duration) {
            age = -1f
            trail.clear()
        }
    }

    fun draw(scope: DrawScope, sun: Offset, scale: Float, tilt: Float) = with(scope) {
        if (age < 0f) return@with
        // Slow far away, fast near the sun.
        val p = age / duration
        val s = sin((p - 0.5f) * PI.toFloat()) * 1f
        val rel = position(s)
        val head = Offset(sun.x + rel.x * scale, sun.y + rel.y * scale * tilt)
        trail.addLast(head)
        if (trail.size > 70) trail.removeFirst()
        val dist = rel.length().coerceAtLeast(0.08f)
        val fade = (sin(p * PI.toFloat()) * 1.4f).coerceIn(0f, 1f)
        val unit = size.minDimension
        // Dust tail: the curved path it just travelled, softly glowing.
        trail.forEachIndexed { i, pt ->
            val k = i / trail.size.toFloat()
            drawCircle(Color(0xFFFFE9C4).copy(alpha = 0.10f * k * fade), unit * (0.003f + 0.006f * k), pt, blendMode = BlendMode.Plus)
        }
        // Ion tail: straight, away from the sun, longer when close.
        val away = (head - sun).let { it / it.length().coerceAtLeast(1f) }
        val tailLen = unit * (0.08f + 0.18f / (dist * 3f)).coerceAtMost(unit * 0.45f)
        val end = head + away * tailLen
        val perp = Offset(-away.y, away.x) * unit * 0.012f
        val tail = Path().apply {
            moveTo(head.x + perp.x * 0.3f, head.y + perp.y * 0.3f)
            lineTo(end.x + perp.x, end.y + perp.y)
            lineTo(end.x - perp.x, end.y - perp.y)
            lineTo(head.x - perp.x * 0.3f, head.y - perp.y * 0.3f)
            close()
        }
        drawPath(tail, Brush.linearGradient(listOf(Color(0xFF9FDCFF).copy(alpha = 0.55f * fade), Color.Transparent), head, end), blendMode = BlendMode.Plus)
        // Coma and nucleus.
        drawCircle(Brush.radialGradient(listOf(Color(0xFFBFE8FF).copy(alpha = 0.6f * fade), Color.Transparent), head, unit * 0.025f), unit * 0.025f, head, blendMode = BlendMode.Plus)
        drawCircle(Color.White.copy(alpha = fade), unit * 0.0035f, head)
    }
}
