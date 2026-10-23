package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SoftDot
import dev.pridgey.beddery.savers.TAU
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.smoothstep
import dev.pridgey.beddery.savers.stamp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------- Jellyfish

private val JellyColors = listOf(Color(0xFF74E8FF), Color(0xFFB892FF), Color(0xFFFF8AD0), Color(0xFF8AFFD6))

private class Jelly(seed: Int) {
    private val r = Random(seed)
    var x = r.nextFloat()
    var y = r.nextFloat() * 1.2f
    val depth = 0.45f + r.nextFloat() * 0.55f
    val color = JellyColors[seed % JellyColors.size]
    val phase = r.nextFloat()
    val period = 3.2f + r.nextFloat() * 2.2f
    var flash = 0f
    var jet = 0f
    var contraction = 0f

    fun step(dt: Float, t: Float) {
        val cyc = ((t / period) + phase) % 1f
        // Quick squeeze, slow relax.
        contraction = if (cyc < 0.28f) sin(cyc / 0.28f * PI.toFloat() / 2) else cos((cyc - 0.28f) / 0.72f * PI.toFloat() / 2)
        y -= (0.004f + 0.022f * contraction * contraction + jet) * depth * dt
        x += sin(t * 0.05f + phase * TAU) * 0.004f * dt
        flash = (flash - dt * 0.35f).coerceAtLeast(0f)
        jet = (jet - dt * 0.04f).coerceAtLeast(0f)
        if (y < -0.25f) {
            y = 1.25f
            x = 0.1f + r.nextFloat() * 0.8f
        }
    }
}

@Composable
fun JellyfishSaver(ctx: SaverContext) {
    val jellies = remember { List(if (ctx.preview) 4 else 6) { Jelly(it * 7 + 3) }.sortedBy { it.depth } }
    val snow = remember { val r = Random(8); List(if (ctx.preview) 60 else 150) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    val filters = remember { JellyColors.associateWith { ColorFilter.tint(it, BlendMode.Modulate) } }
    val grain = rememberGrainBrush()
    val cooldown = remember { Cooldown(6_000) }
    val canvasSize = remember { floatArrayOf(1f, 1f) }
    val time = rememberFrameTime { dt, t -> jellies.forEach { it.step(dt, t) } }
    OnSaverTap { at ->
        if (!cooldown.tryAcquire()) return@OnSaverTap
        val tx = at.x / canvasSize[0]
        val ty = at.y / canvasSize[1]
        jellies.minByOrNull { (it.x - tx) * (it.x - tx) + (it.y - ty) * (it.y - ty) }?.let {
            it.flash = 1f
            it.jet = 0.06f
        }
    }
    val bell = remember { Path() }
    val tentacle = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        canvasSize[0] = w
        canvasSize[1] = h
        val unit = size.minDimension
        drawRect(Brush.verticalGradient(listOf(Color(0xFF02142A), Color(0xFF010A18), Color(0xFF00040A))))
        // Sunlight from far above, fading with depth.
        for (k in 0 until 4) {
            val cx = w * (0.15f + k * 0.25f) + sin(t * 0.05f + k) * w * 0.05f
            val top = cx - w * 0.04f
            val path = Path().apply {
                moveTo(top, 0f)
                lineTo(top + w * 0.08f, 0f)
                lineTo(cx + w * 0.16f, h * 0.75f)
                lineTo(cx - w * 0.06f, h * 0.75f)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFF5FA8D8).copy(alpha = 0.06f + 0.02f * sin(t * 0.3f + k)), Color.Transparent), 0f, h * 0.75f), blendMode = BlendMode.Plus)
        }
        // Marine snow drifting slowly.
        snow.forEach { s ->
            val y = ((s[1] - t * 0.004f * (0.3f + s[2])) % 1f + 1f) % 1f
            val x = s[0] + sin(t * 0.2f + s[3] * TAU) * 0.004f
            drawCircle(Color(0xFFB8D8F0).copy(alpha = 0.12f + 0.25f * s[2]), unit * 0.0015f * (0.5f + s[2]), Offset(x * w, y * h))
        }
        jellies.forEach { j -> drawJelly(j, t, unit, bell, tentacle, filters.getValue(j.color)) }
        drawGrain(grain)
    }
}

private fun DrawScope.drawJelly(j: Jelly, t: Float, unit: Float, bell: Path, tentacle: Path, filter: ColorFilter) {
    val o = Offset(j.x * size.width, j.y * size.height)
    val c = j.contraction
    val radius = unit * 0.12f * j.depth
    val bw = radius * (1f - 0.17f * c)
    val bh = radius * 0.8f * (1f + 0.12f * c)
    val glow = 0.55f + 0.45f * j.depth + j.flash * 1.4f
    val col = lerp(j.color, Color.White, j.flash * 0.5f)
    stamp(SoftDot, o, radius * 5f, alpha = 0.16f * glow, colorFilter = filter, blendMode = BlendMode.Plus)
    // Tentacles trail behind, swinging more when it's pushing forward.
    val rimY = o.y + bh * 0.3f
    for (k in 0 until 9) {
        val bx = o.x + bw * (-0.85f + 1.7f * k / 8f)
        tentacle.reset()
        tentacle.moveTo(bx, rimY)
        val segs = 18
        val len = radius * (2.6f + (k % 3) * 0.5f)
        for (s in 1..segs) {
            val f = s / segs.toFloat()
            val sway = sin(t * 1.4f - f * 5f + k * 1.3f) * radius * 0.25f * f + sin(t * 0.6f + k) * radius * 0.1f * f
            tentacle.lineTo(bx + sway + (bx - o.x) * 0.35f * f, rimY + f * len * (1f - 0.15f * c))
        }
        drawPath(tentacle, col.copy(alpha = 0.22f * glow.coerceAtMost(1.6f)), style = Stroke(unit * 0.0016f, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }
    // Frilly oral arms.
    for (k in -1..1) {
        tentacle.reset()
        tentacle.moveTo(o.x + k * bw * 0.15f, rimY)
        for (s in 1..14) {
            val f = s / 14f
            tentacle.lineTo(o.x + k * bw * 0.2f + sin(t * 1.1f - f * 6f + k) * radius * 0.12f, rimY + f * radius * 1.6f)
        }
        drawPath(tentacle, col.copy(alpha = 0.14f * glow.coerceAtMost(1.6f)), style = Stroke(unit * 0.008f * j.depth, cap = StrokeCap.Round), blendMode = BlendMode.Plus)
    }
    // The bell: a translucent dome with a scalloped rim.
    bell.reset()
    bell.moveTo(o.x - bw, rimY)
    bell.cubicTo(o.x - bw * 1.05f, o.y - bh * 0.9f, o.x + bw * 1.05f, o.y - bh * 0.9f, o.x + bw, rimY)
    val scallops = 8
    for (s in scallops - 1 downTo 0) {
        val x0 = o.x - bw + 2 * bw * s / scallops
        val xm = x0 + bw / scallops
        bell.quadraticTo(xm, rimY - bh * 0.12f, x0, rimY)
    }
    bell.close()
    drawPath(
        bell,
        Brush.radialGradient(listOf(col.copy(alpha = 0.42f * glow.coerceAtMost(1.5f)), col.copy(alpha = 0.14f), col.copy(alpha = 0.06f)), Offset(o.x, o.y - bh * 0.3f), bw * 1.2f),
        blendMode = BlendMode.Plus,
    )
    drawPath(bell, col.copy(alpha = 0.55f * glow.coerceAtMost(1.5f)), style = Stroke(unit * 0.0018f), blendMode = BlendMode.Plus)
    for (k in 0 until 4) {
        val a = k * PI.toFloat() / 2 + t * 0.2f
        val lo = Offset(o.x + cos(a) * bw * 0.3f, o.y - bh * 0.15f + sin(a) * bh * 0.12f)
        drawCircle(lerp(col, Color.White, 0.4f).copy(alpha = 0.2f * glow.coerceAtMost(1.5f)), bw * 0.14f, lo, blendMode = BlendMode.Plus)
    }
}

// ---------------------------------------------------------------- Color Field

private class Palette(val bg: Color, val colors: List<Color>)

private val Palettes = listOf(
    Palette(Color(0xFF140A2C), listOf(Color(0xFFD9575E), Color(0xFFE59A4E), Color(0xFF6A3AD8), Color(0xFFC23574))), // Dusk
    Palette(Color(0xFF02161F), listOf(Color(0xFF0FA9A0), Color(0xFF1E62D0), Color(0xFF4CCB8C), Color(0xFF07385A))), // Lagoon
    Palette(Color(0xFF160505), listOf(Color(0xFFD9431A), Color(0xFFE08524), Color(0xFF9A1230), Color(0xFF4F0A2B))), // Ember
    Palette(Color(0xFF050A16), listOf(Color(0xFF6E9EE0), Color(0xFFB4C9EC), Color(0xFF4152D0), Color(0xFF22276A))), // Glacier
    Palette(Color(0xFF10051A), listOf(Color(0xFFB04CD6), Color(0xFFD7709F), Color(0xFF5A33D0), Color(0xFF240C42))), // Orchid
)

@Composable
fun ColorFieldSaver(ctx: SaverContext) {
    val grain = rememberGrainBrush()
    // palette index, time the current change started
    val state = remember { floatArrayOf(0f, -100f) }
    val cooldown = remember { Cooldown(3_000) }
    val time = rememberFrameTime { _, t ->
        if (t - state[1] > 180f) {
            state[0] = (state[0] + 1) % Palettes.size
            state[1] = t
        }
    }
    OnSaverTap {
        if (cooldown.tryAcquire()) {
            state[0] = (state[0] + 1) % Palettes.size
            state[1] = time.value - 0.01f
        }
    }
    val seeds = remember { val r = Random(3); List(5) { floatArrayOf(0.03f + r.nextFloat() * 0.03f, 0.03f + r.nextFloat() * 0.03f, r.nextFloat() * TAU, r.nextFloat() * TAU) } }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value + 50f
        val idx = state[0].toInt()
        // Slow 20-second crossfade between palettes.
        val k = smoothstep(0f, 20f, time.value - state[1]).let { if (state[1] < 0f) 1f else it }
        val prev = Palettes[(idx - 1 + Palettes.size) % Palettes.size]
        val cur = Palettes[idx]
        drawRect(lerp(prev.bg, cur.bg, k))
        val big = size.maxDimension
        seeds.forEachIndexed { i, s ->
            val color = lerp(prev.colors[i % 4], cur.colors[i % 4], k)
            val c = Offset(size.width * (0.5f + 0.4f * sin(t * s[0] + s[2])), size.height * (0.5f + 0.4f * sin(t * s[1] + s[3])))
            val r = big * (0.5f + 0.12f * sin(t * 0.05f + i))
            drawCircle(Brush.radialGradient(0f to color.copy(alpha = 0.85f), 0.5f to color.copy(alpha = 0.32f), 1f to Color.Transparent, center = c, radius = r), r, c)
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), center, big * 0.8f))
        drawGrain(grain, 0.12f)
    }
}

// ---------------------------------------------------------------- Harmonograph

private class Harmonograph(seed: Int) {
    private val r = Random(seed)
    private val ratios = listOf(2f to 3f, 3f to 4f, 1f to 2f, 3f to 5f, 2f to 5f, 4f to 5f)
    private val ratio = ratios[r.nextInt(ratios.size)]
    private val f = floatArrayOf(ratio.first, ratio.second + 0.004f + r.nextFloat() * 0.01f, ratio.second, ratio.first + 0.003f + r.nextFloat() * 0.01f)
    private val p = FloatArray(4) { r.nextFloat() * TAU }
    private val a = floatArrayOf(0.3f + r.nextFloat() * 0.2f, 0.3f + r.nextFloat() * 0.2f, 0.3f + r.nextFloat() * 0.2f, 0.3f + r.nextFloat() * 0.2f)
    private val d = FloatArray(4) { 0.004f + r.nextFloat() * 0.004f }
    val hueShift = r.nextFloat()

    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    var s = 0f
    var age = 0f

    fun step(dt: Float, perSecond: Int) {
        age += dt
        val n = (perSecond * dt).toInt().coerceAtLeast(1)
        repeat(n) {
            s += 0.02f
            val x = a[0] * sin(f[0] * s + p[0]) * exp(-d[0] * s) + a[1] * sin(f[1] * s + p[1]) * exp(-d[1] * s)
            val y = a[2] * sin(f[2] * s + p[2]) * exp(-d[2] * s) + a[3] * sin(f[3] * s + p[3]) * exp(-d[3] * s)
            xs += x
            ys += y
        }
    }

    val done get() = age > 100f
}

@Composable
fun HarmonographSaver(ctx: SaverContext) {
    val state = remember { arrayOf(Harmonograph(1)) }
    val seed = remember { intArrayOf(1) }
    val paths = remember { ArrayList<Path>() }
    val accent = ctx.accent
    val time = rememberFrameTime { dt, _ ->
        val h = state[0]
        h.step(dt, if (ctx.preview) 900 else 260)
        if (h.done) {
            seed[0]++
            state[0] = Harmonograph(seed[0])
            paths.clear()
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        time.value
        val h = state[0]
        val c = center
        val scale = size.minDimension * 0.44f
        val chunk = 160
        // Finished chunks become fixed paths; only the newest one is rebuilt.
        while ((paths.size + 1) * chunk < h.xs.size) {
            val start = paths.size * chunk
            paths += Path().apply {
                moveTo(c.x + h.xs[start] * scale, c.y + h.ys[start] * scale)
                for (i in start + 1..start + chunk) lineTo(c.x + h.xs[i] * scale, c.y + h.ys[i] * scale)
            }
        }
        val fade = (1f - smoothstep(95f, 100f, h.age)) * smoothstep(0f, 1f, h.age)
        val other = lerp(accent, Color(0xFFFF9AB0), 0.6f)
        val third = lerp(accent, Color(0xFF7FE3B8), 0.6f)
        val total = (h.xs.size / chunk + 1).coerceAtLeast(1)
        fun colorAt(i: Int): Color {
            val f = ((i.toFloat() / total + h.hueShift) % 1f) * 3f
            return when {
                f < 1f -> lerp(accent, other, f)
                f < 2f -> lerp(other, third, f - 1f)
                else -> lerp(third, accent, f - 2f)
            }
        }
        paths.forEachIndexed { i, p ->
            drawPath(p, colorAt(i).copy(alpha = 0.5f * fade), style = Stroke(density * 0.8f), blendMode = BlendMode.Plus)
        }
        val start = paths.size * chunk
        if (h.xs.size > start + 1) {
            val live = Path().apply {
                moveTo(c.x + h.xs[start] * scale, c.y + h.ys[start] * scale)
                for (i in start + 1 until h.xs.size) lineTo(c.x + h.xs[i] * scale, c.y + h.ys[i] * scale)
            }
            drawPath(live, colorAt(paths.size).copy(alpha = 0.6f * fade), style = Stroke(density * 0.8f), blendMode = BlendMode.Plus)
            val pen = Offset(c.x + h.xs.last() * scale, c.y + h.ys.last() * scale)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f * fade), Color.Transparent), pen, density * 10f), density * 10f, pen, blendMode = BlendMode.Plus)
            drawCircle(Color.White.copy(alpha = fade), density * 1.6f, pen)
        }
    }
}

// ---------------------------------------------------------------- Pendulum Wave

@Composable
fun PendulumWaveSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val accent = ctx.accent
    val n = 18
    val colors = remember(accent) { List(n) { i -> lerp(accent, lerp(accent, Color(0xFFFF9AB0), 0.7f), i / (n - 1f)) } }
    Canvas(Modifier.fillMaxSize()) {
        // All pendulums line up again every 60 seconds.
        val t = time.value
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF05060B), Color.Black)))
        val top = h * 0.12f
        val spacing = h * 0.76f / (n - 1)
        val amp = w * 0.36f
        val r = minOf(spacing * 0.36f, w * 0.03f)
        for (i in 0 until n) {
            val y = top + i * spacing
            val freq = (34 + i) / 60f
            drawLine(Color.White.copy(alpha = 0.05f), Offset(w / 2 - amp, y), Offset(w / 2 + amp, y), strokeWidth = density * 0.6f, cap = StrokeCap.Round)
            // Motion blur: a few fading copies just behind.
            for (k in 4 downTo 1) {
                val x = w / 2 + amp * sin(TAU * freq * (t - k * 0.025f))
                drawCircle(colors[i].copy(alpha = 0.06f * (5 - k)), r, Offset(x, y))
            }
            val x = w / 2 + amp * sin(TAU * freq * t)
            val o = Offset(x, y)
            drawOval(Color.Black.copy(alpha = 0.5f), Offset(x - r * 0.9f + r * 0.4f, y + r * 0.55f), Size(r * 1.8f, r * 0.7f))
            drawCircle(Brush.radialGradient(listOf(colors[i].copy(alpha = 0.35f), Color.Transparent), o, r * 3f), r * 3f, o, blendMode = BlendMode.Plus)
            drawCircle(Brush.radialGradient(listOf(lerp(colors[i], Color.White, 0.75f), colors[i], lerp(colors[i], Color.Black, 0.6f)), o - Offset(r * 0.35f, r * 0.4f), r * 1.5f), r, o)
        }
    }
}

// ---------------------------------------------------------------- Flow Field

private class Flow(val n: Int) {
    private val r = Random(10)
    val x = FloatArray(n)
    val y = FloatArray(n)
    private val life = FloatArray(n)
    var bitmap: Bitmap? = null
    var image: ImageBitmap? = null
    private var canvas: android.graphics.Canvas? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.3f; strokeCap = Paint.Cap.ROUND }
    private val fade = Paint().apply { color = android.graphics.Color.argb(14, 0, 0, 0) }
    private val deepFade = Paint().apply { color = android.graphics.Color.argb(70, 0, 0, 0) }
    private var frame = 0
    var colors = IntArray(3)

    fun ensure(w: Int, h: Int) {
        if (bitmap?.width == w && bitmap?.height == h) return
        val b = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        bitmap = b
        image = b.asImageBitmap()
        canvas = android.graphics.Canvas(b).also { it.drawColor(android.graphics.Color.BLACK) }
        for (i in 0 until n) respawn(i, w, h)
    }

    private fun respawn(i: Int, w: Int, h: Int) {
        x[i] = r.nextFloat() * w
        y[i] = r.nextFloat() * h
        life[i] = 2f + r.nextFloat() * 5f
    }

    fun step(dt: Float, t: Float) {
        val c = canvas ?: return
        val b = bitmap ?: return
        frame++
        c.drawRect(0f, 0f, b.width.toFloat(), b.height.toFloat(), if (frame % 40 == 0) deepFade else fade)
        val scale = 400f / minOf(b.width, b.height)
        for (i in 0 until n) {
            val px = x[i]
            val py = y[i]
            val sx = px * scale
            val sy = py * scale
            val angle = 1.7f * (sin(sx * 0.009f + t * 0.03f) + sin(sy * 0.011f - t * 0.025f) + sin((sx - sy) * 0.006f + t * 0.02f))
            x[i] += cos(angle) * 1.1f / scale * 2.2f
            y[i] += sin(angle) * 1.1f / scale * 2.2f
            life[i] -= dt
            if (life[i] <= 0f || x[i] < 0 || y[i] < 0 || x[i] > b.width || y[i] > b.height) {
                respawn(i, b.width, b.height)
                continue
            }
            val band = (((angle / TAU) % 1f + 1f) % 1f * 3).toInt().coerceIn(0, 2)
            paint.color = colors[band]
            paint.alpha = 46
            c.drawLine(px, py, x[i], y[i], paint)
        }
    }
}

@Composable
fun FlowFieldSaver(ctx: SaverContext) {
    val flow = remember { Flow(if (ctx.preview) 500 else 1800) }
    val accent = ctx.accent
    flow.colors = intArrayOf(accent.toArgb(), lerp(accent, Color(0xFFFF9AB0), 0.65f).toArgb(), lerp(accent, Color(0xFF7FE3B8), 0.65f).toArgb())
    val time = rememberFrameTime { dt, t -> flow.step(dt, t) }
    Canvas(Modifier.fillMaxSize()) {
        time.value
        flow.ensure((size.width / 2).toInt(), (size.height / 2).toInt())
        val image = flow.image ?: return@Canvas
        drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
    }
}
