package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.SoftDot
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.TAU
import dev.pridgey.beddery.savers.addPine
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.pineLine
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.rememberNoiseBrush
import dev.pridgey.beddery.savers.ridge
import dev.pridgey.beddery.savers.ridgePath
import dev.pridgey.beddery.savers.smoothstep
import dev.pridgey.beddery.savers.sprite
import dev.pridgey.beddery.savers.stamp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private fun tint(c: Color) = ColorFilter.tint(c, BlendMode.Modulate)

// ---------------------------------------------------------------- Rain on Glass

/** A water drop on glass: it flips and focuses the light behind it, with a dark rim and a glint. */
private val Droplet by lazy {
    sprite(64) {
        val c = center
        drawCircle(
            Brush.radialGradient(0f to Color(0xFFCFE0FF).copy(alpha = 0.34f), 0.55f to Color(0xFF7D93BD).copy(alpha = 0.2f), 1f to Color(0xFF0B1220).copy(alpha = 0.55f), center = c + Offset(0f, 9f), radius = 34f),
            30f, c,
        )
        drawCircle(Color.Black.copy(alpha = 0.35f), 29f, c + Offset(0f, -1.5f), style = Stroke(4f))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.Transparent), c + Offset(-9f, -11f), 8f), 8f, c + Offset(-9f, -11f))
        drawArc(Color.White.copy(alpha = 0.25f), 20f, 110f, false, Offset(8f, 8f), Size(48f, 48f), style = Stroke(2.5f))
    }
}

private class Glass(private val maxDrops: Int) {
    private val r = Random(3)
    val x = FloatArray(maxDrops) { r.nextFloat() }
    val y = FloatArray(maxDrops) { r.nextFloat() }
    val size = FloatArray(maxDrops) { droplet() }

    class Runner(var x: Float, var y: Float, var size: Float, var speed: Float, val seed: Int, var lastTrail: Float)

    val runners = ArrayList<Runner>()
    private var seed = 0

    private fun droplet() = 0.004f + r.nextFloat().pow(2.5f) * 0.017f

    fun step(dt: Float, t: Float) {
        // New drops landing on the glass.
        repeat((dt * 30f).toInt() + if (r.nextFloat() < (dt * 30f) % 1f) 1 else 0) {
            val i = r.nextInt(maxDrops)
            x[i] = r.nextFloat()
            y[i] = r.nextFloat()
            size[i] = droplet()
        }
        if (runners.size < 7 && r.nextFloat() < dt * 0.35f) launch(r.nextFloat(), r.nextFloat() * 0.4f)
        val it = runners.iterator()
        while (it.hasNext()) {
            val d = it.next()
            // Drops stutter: they hang, then rush, as they gather water.
            val surge = 0.5f + 0.5f * noise1(t * 1.3f + d.seed * 9f, d.seed)
            d.speed = (0.02f + surge * surge * 0.22f) * (0.6f + d.size * 40f)
            d.y += d.speed * dt
            d.x += noise1(t * 0.8f + d.seed, d.seed + 3) * dt * 0.01f
            for (i in 0 until maxDrops) {
                if (size[i] <= 0f) continue
                val dx = x[i] - d.x
                val dy = (y[i] - d.y) * 2f
                if (abs(dx) < d.size + size[i] && abs(dy) < d.size * 2 + size[i]) {
                    d.size = (d.size + size[i] * 0.15f).coerceAtMost(0.034f)
                    size[i] = 0f
                }
            }
            if (d.y - d.lastTrail > 0.025f) {
                d.lastTrail = d.y
                val i = r.nextInt(maxDrops)
                x[i] = d.x + (r.nextFloat() - 0.5f) * d.size
                y[i] = d.y - d.size * 2.5f
                size[i] = d.size * (0.18f + r.nextFloat() * 0.15f)
            }
            if (d.y > 1.1f) it.remove()
        }
    }

    fun launch(atX: Float, atY: Float) {
        runners += Runner(atX, atY, 0.016f + r.nextFloat() * 0.01f, 0f, seed++, atY)
    }
}

@Composable
fun RainGlassSaver(ctx: SaverContext) {
    val glass = remember { Glass(if (ctx.preview) 140 else 340) }
    val lights = remember {
        val r = Random(12)
        val palette = listOf(Color(0xFFFFB25A), Color(0xFFFF6A4D), Color(0xFFFFE2A8), Color(0xFF7FD3FF), Color(0xFFB8C6FF), Color(0xFFFF8FB0))
        List(38) { floatArrayOf(r.nextFloat(), 0.25f + r.nextFloat() * 0.75f, 0.04f + r.nextFloat() * 0.09f, r.nextInt(palette.size).toFloat(), r.nextFloat()) } to palette
    }
    val filters = remember { lights.second.map { tint(it) } }
    val grain = rememberGrainBrush()
    val cooldown = remember { Cooldown(3_000) }
    val canvasSize = remember { floatArrayOf(1f, 1f) }
    val time = rememberFrameTime { dt, t -> glass.step(dt, t) }
    OnSaverTap { at -> if (cooldown.tryAcquire()) glass.launch(at.x / canvasSize[0], at.y / canvasSize[1]) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        canvasSize[0] = w
        canvasSize[1] = h
        val unit = size.minDimension
        drawRect(Brush.verticalGradient(listOf(Color(0xFF04060C), Color(0xFF0A1220), Color(0xFF111A2A))))
        // The city beyond, out of focus.
        lights.first.forEach { l ->
            val drift = sin(t * 0.03f + l[4] * TAU) * 0.01f
            val o = Offset((l[0] + drift) * w, l[1] * h)
            val flicker = 0.75f + 0.25f * sin(t * (0.3f + l[4]) + l[4] * 10f)
            stamp(SoftDot, o, l[2] * unit * 2.4f, alpha = 0.32f * flicker, colorFilter = filters[l[3].toInt()], blendMode = BlendMode.Plus)
        }
        // Fog on the glass.
        drawRect(Color(0xFF6A7A96).copy(alpha = 0.06f))
        for (i in 0 until glass.size.size) {
            val s = glass.size[i]
            if (s <= 0f) continue
            stamp(Droplet, Offset(glass.x[i] * w, glass.y[i] * h), s * unit * 2f, s * unit * 2.1f)
        }
        glass.runners.forEach { d ->
            val o = Offset(d.x * w, d.y * h)
            val px = d.size * unit
            // A faint wet streak left behind.
            drawLine(Color(0xFF9FB4D8).copy(alpha = 0.05f), o - Offset(0f, h * 0.18f), o, strokeWidth = px * 0.9f, cap = StrokeCap.Round)
            stamp(Droplet, o + Offset(0f, -px * 0.6f), px * 1.3f, px * 2f)
            stamp(Droplet, o, px * 2f, px * 2.3f)
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), center, size.maxDimension * 0.7f))
        drawGrain(grain)
    }
}

// ---------------------------------------------------------------- Campfire

private class Fire(private val n: Int) {
    private val r = Random(6)
    val x = FloatArray(n)
    val y = FloatArray(n)
    val vx = FloatArray(n)
    val vy = FloatArray(n)
    val age = FloatArray(n) { r.nextFloat() }
    val life = FloatArray(n) { 1f }
    val size = FloatArray(n)

    private val sparks = 70
    val sx = FloatArray(sparks)
    val sy = FloatArray(sparks)
    val svx = FloatArray(sparks)
    val svy = FloatArray(sparks)
    val sage = FloatArray(sparks) { 99f }
    val slife = FloatArray(sparks) { 1f }
    private var flare = 0f

    init {
        for (i in 0 until n) respawn(i, r.nextFloat())
    }

    private fun respawn(i: Int, startAge: Float = 0f) {
        val g = (r.nextFloat() + r.nextFloat() + r.nextFloat()) / 3f - 0.5f
        x[i] = g * 0.16f
        y[i] = 0f
        vx[i] = -g * 0.05f
        vy[i] = -(0.16f + r.nextFloat() * 0.12f)
        life[i] = 0.7f + r.nextFloat() * 0.8f
        age[i] = startAge * life[i]
        size[i] = 0.045f + r.nextFloat() * 0.04f
    }

    fun burst() {
        flare = 1f
        repeat(30) { spark() }
    }

    private fun spark() {
        val i = sage.indices.maxByOrNull { sage[it] / slife[it] } ?: return
        sx[i] = (r.nextFloat() - 0.5f) * 0.1f
        sy[i] = -0.05f
        svx[i] = (r.nextFloat() - 0.5f) * 0.12f
        svy[i] = -(0.15f + r.nextFloat() * 0.3f)
        sage[i] = 0f
        slife[i] = 1.2f + r.nextFloat() * 1.8f
    }

    fun step(dt: Float, t: Float) {
        flare = (flare - dt * 0.4f).coerceAtLeast(0f)
        for (i in 0 until n) {
            age[i] += dt
            if (age[i] > life[i]) respawn(i)
            // Flames lean and lick in the moving air.
            vx[i] += noise1(t * 2f + i * 0.37f, 5) * dt * 0.25f
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt * (1f + flare * 0.6f)
            x[i] *= 1f - dt * 0.8f
        }
        if (r.nextFloat() < dt * 4f) spark()
        for (i in 0 until sparks) {
            sage[i] += dt
            svx[i] += noise1(t * 1.5f + i, 9) * dt * 0.3f
            sx[i] += svx[i] * dt
            sy[i] += svy[i] * dt
            svy[i] *= 1f - dt * 0.3f
        }
    }

    val flareLevel get() = flare
}

private val FireStops = listOf(0f to Color(0xFFFFF4D2), 0.18f to Color(0xFFFFD36A), 0.42f to Color(0xFFFF8A26), 0.7f to Color(0xFFD63A12), 1f to Color(0xFF5A0E06))

private fun fireColor(p: Float): Color {
    for (k in 1 until FireStops.size) {
        val (p1, c1) = FireStops[k]
        val (p0, c0) = FireStops[k - 1]
        if (p <= p1) return lerp(c0, c1, (p - p0) / (p1 - p0))
    }
    return FireStops.last().second
}

@Composable
fun CampfireSaver(ctx: SaverContext) {
    val fire = remember { Fire(if (ctx.preview) 70 else 150) }
    val stars = remember { StarSky(160, 19, 0f, 0.55f) }
    val grain = rememberGrainBrush()
    val filters = remember { (0..20).map { tint(fireColor(it / 20f)) } }
    val sparkFilter = remember { tint(Color(0xFFFFB04A)) }
    val cooldown = remember { Cooldown(8_000) }
    val time = rememberFrameTime { dt, t -> fire.step(dt, t) }
    OnSaverTap { if (cooldown.tryAcquire()) fire.burst() }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        val base = Offset(w / 2, h * if (w > h) 0.58f else 0.66f)
        val flicker = 0.85f + 0.1f * noise1(t * 6f, 1) + 0.05f * noise1(t * 17f, 2) + fire.flareLevel * 0.3f
        drawRect(Brush.verticalGradient(0f to Color(0xFF020309), 0.6f to Color(0xFF0B0810), 1f to Color(0xFF140B08)))
        stars.draw(this, t, 0.6f, horizonFade = 0.4f)
        // Firelight on the ground and in the air.
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF7A2A).copy(alpha = 0.22f * flicker), Color.Transparent), base, unit * 0.9f), unit * 0.9f, base)
        drawOval(
            Brush.radialGradient(listOf(Color(0xFFFF8A3A).copy(alpha = 0.35f * flicker), Color.Transparent), base + Offset(0f, unit * 0.04f), unit * 0.45f),
            Offset(base.x - unit * 0.55f, base.y - unit * 0.02f), Size(unit * 1.1f, unit * 0.18f),
        )
        // Stones.
        for (k in 0 until 9) {
            val a = PI.toFloat() * (0.05f + 0.9f * k / 8f)
            val o = Offset(base.x + cos(a) * unit * 0.24f, base.y + unit * 0.035f + sin(a) * unit * 0.05f)
            val sw = unit * (0.06f + hash(k) * 0.025f)
            drawOval(Brush.verticalGradient(listOf(Color(0xFF6A4334).copy(alpha = 0.9f * flicker), Color(0xFF16100D)), o.y - sw * 0.35f, o.y + sw * 0.35f), Offset(o.x - sw / 2, o.y - sw * 0.33f), Size(sw, sw * 0.62f))
        }
        // Logs, glowing where they burn.
        listOf(-24f, 22f, -4f).forEachIndexed { i, deg ->
            rotate(deg, base + Offset(0f, unit * 0.01f)) {
                val lw = unit * 0.38f
                val lh = unit * 0.048f
                val tl = base + Offset(-lw / 2, -lh / 2 + i * unit * 0.008f)
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF5A3020), Color(0xFF1A0E09)), tl.y, tl.y + lh), tl, Size(lw, lh), CornerRadius(lh / 2))
                drawRoundRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFF5A1A).copy(alpha = 0.55f * flicker), Color.Transparent), tl.x + lw * 0.25f, tl.x + lw * 0.75f), tl, Size(lw, lh * 0.5f), CornerRadius(lh / 2), blendMode = BlendMode.Plus)
            }
        }
        // Flames: many soft, hot puffs added together.
        for (i in 0 until fire.x.size) {
            val p = (fire.age[i] / fire.life[i]).coerceIn(0f, 1f)
            val o = Offset(base.x + fire.x[i] * unit, base.y - unit * 0.02f + fire.y[i] * unit)
            val s = fire.size[i] * unit * (1f - p * 0.75f) * 2.2f
            val a = (1f - p).pow(0.7f) * smoothstep(0f, 0.08f, p) * 0.55f
            stamp(SoftDot, o, s * 0.8f, s, alpha = a, colorFilter = filters[(p * 20).toInt().coerceIn(0, 20)], blendMode = BlendMode.Plus)
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF0C0).copy(alpha = 0.5f * flicker), Color.Transparent), base - Offset(0f, unit * 0.04f), unit * 0.09f), unit * 0.09f, base - Offset(0f, unit * 0.04f), blendMode = BlendMode.Plus)
        // Embers rising.
        for (i in fire.sage.indices) {
            val p = fire.sage[i] / fire.slife[i]
            if (p >= 1f) continue
            val o = Offset(base.x + fire.sx[i] * unit, base.y + fire.sy[i] * unit)
            val a = (1f - p) * (0.6f + 0.4f * sin(t * 20f + i))
            stamp(SoftDot, o, unit * 0.014f, alpha = a, colorFilter = sparkFilter, blendMode = BlendMode.Plus)
            drawCircle(Color(0xFFFFE2A0).copy(alpha = a), unit * 0.0022f, o)
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), base, size.maxDimension * 0.8f))
        drawGrain(grain)
    }
}

// ---------------------------------------------------------------- Snowfall

private class Snow(val n: Int) {
    private val r = Random(14)
    val x = FloatArray(n) { r.nextFloat() }
    val y = FloatArray(n) { r.nextFloat() }
    val depth = FloatArray(n) { r.nextFloat().pow(1.6f) }
    val phase = FloatArray(n) { r.nextFloat() * TAU }
    var wind = 0f
    private var gust = -100f

    fun gust(t: Float) {
        gust = t
    }

    fun step(dt: Float, t: Float) {
        val since = t - gust
        val g = if (since in 0f..8f) sin(since / 8f * PI.toFloat()) * 0.35f else 0f
        wind = 0.015f * noise1(t * 0.1f, 3) + g
        for (i in 0 until n) {
            val d = depth[i]
            y[i] += (0.02f + d * 0.07f) * dt
            x[i] += (wind * (0.4f + d) + sin(t * (0.6f + d) + phase[i]) * 0.006f) * dt
            if (y[i] > 1.02f) { y[i] = -0.02f; x[i] = r.nextFloat() }
            if (x[i] > 1.05f) x[i] -= 1.1f
            if (x[i] < -0.05f) x[i] += 1.1f
        }
    }
}

private class Winter(val hills: Path, val farTrees: Path, val nearTrees: Path, val cabin: Rect)

@Composable
fun SnowfallSaver(ctx: SaverContext) {
    val snow = remember { Snow(if (ctx.preview) 160 else 380) }
    val grain = rememberGrainBrush()
    val smokeTint = remember { tint(Color(0xFF9AA8BF)) }
    val flake = remember { tint(Color(0xFFEAF2FF)) }
    val cooldown = remember { Cooldown(20_000) }
    val time = rememberFrameTime { dt, t -> snow.step(dt, t) }
    OnSaverTap { if (cooldown.tryAcquire()) snow.gust(time.value) }
    val scene = remember {
        SizeCache { s ->
            val w = s.width
            val h = s.height
            val ground = h * 0.74f
            val near = Path().apply {
                addPine(w * 0.06f, h * 1.02f, h * 0.5f, 3)
                addPine(w * 0.94f, h * 1.02f, h * 0.44f, 7)
                addPine(w * 0.16f, h * 1.02f, h * 0.3f, 11)
            }
            Winter(
                hills = ridgePath(ridge(65, 4, 0.5f), w, ground - h * 0.02f, h * 0.08f, h),
                farTrees = pineLine(w, ground - h * 0.02f, h * 0.02f, h * 0.055f, w * 0.03f, 15, ground + 4f),
                nearTrees = near,
                cabin = Rect(w * 0.6f, ground - h * 0.055f, w * 0.6f + h * 0.07f, ground - h * 0.005f),
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        val sc = scene.get(size)
        val ground = h * 0.74f
        drawRect(Brush.verticalGradient(0f to Color(0xFF060A15), 0.6f to Color(0xFF111B2D), 1f to Color(0xFF1A2639), endY = ground))
        val moon = Offset(w * 0.24f, h * 0.16f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFBFD0EA).copy(alpha = 0.22f), Color.Transparent), moon, unit * 0.5f), unit * 0.5f, moon)
        drawCircle(Color(0xFFE6EDF8).copy(alpha = 0.5f), unit * 0.035f, moon)
        drawFlakes(snow, 0f, 0.3f, flake)
        drawPath(sc.hills, Brush.verticalGradient(listOf(Color(0xFF2A3A52), Color(0xFF1B2638)), ground - h * 0.1f, ground))
        drawPath(sc.farTrees, Color(0xFF0E1726))
        // Snowy ground.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2B3A52), Color(0xFF3A4B66), Color(0xFF2A374C)), ground, h), Offset(0f, ground), Size(w, h - ground))
        // A cabin with a warm window, smoke curling from the chimney.
        val c = sc.cabin
        val roof = Path().apply {
            moveTo(c.left - c.width * 0.12f, c.top + c.height * 0.15f)
            lineTo(c.center.x, c.top - c.height * 0.55f)
            lineTo(c.right + c.width * 0.12f, c.top + c.height * 0.15f)
            close()
        }
        val chimney = Offset(c.right - c.width * 0.22f, c.top - c.height * 0.4f)
        drawRect(Color(0xFF0B111B), chimney - Offset(c.width * 0.06f, c.height * 0.1f), Size(c.width * 0.12f, c.height * 0.35f))
        drawRect(Color(0xFFD6E0EE), chimney - Offset(c.width * 0.075f, c.height * 0.14f), Size(c.width * 0.15f, c.height * 0.06f))
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF111A28), Color(0xFF0A0F18)), c.left, c.right), c.topLeft, c.size)
        // Snow lies thick on the roof.
        drawPath(roof, Brush.verticalGradient(listOf(Color(0xFFE2EAF5), Color(0xFFA8B6CC)), c.top - c.height * 0.55f, c.top + c.height * 0.15f))
        drawLine(Color(0xFF070B12), Offset(c.left - c.width * 0.1f, c.top + c.height * 0.16f), Offset(c.right + c.width * 0.1f, c.top + c.height * 0.16f), strokeWidth = c.height * 0.05f)
        val win = Rect(c.left + c.width * 0.22f, c.top + c.height * 0.35f, c.left + c.width * 0.48f, c.top + c.height * 0.7f)
        val warm = 0.9f + 0.1f * noise1(t * 3f, 4)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB25A).copy(alpha = 0.28f * warm), Color.Transparent), win.center, unit * 0.12f), unit * 0.12f, win.center)
        drawRect(Color(0xFFFFC879).copy(alpha = warm), win.topLeft, win.size)
        drawLine(Color(0xFF0B111B), Offset(win.center.x, win.top), Offset(win.center.x, win.bottom), strokeWidth = win.width * 0.12f)
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFB25A).copy(alpha = 0.18f * warm), Color.Transparent), Offset(win.center.x, c.bottom + unit * 0.01f), unit * 0.08f), Offset(win.center.x - unit * 0.1f, c.bottom - unit * 0.004f), Size(unit * 0.2f, unit * 0.03f))
        // Wood smoke curling up from the chimney.
        for (k in 0 until 7) {
            val p = (t * 0.045f + k / 7f) % 1f
            val o = chimney + Offset(sin(p * 5f + k) * unit * 0.025f * p + snow.wind * unit * p * 2f, -c.height * 0.15f - p * unit * 0.22f)
            stamp(SoftDot, o, unit * (0.03f + 0.09f * p), alpha = 0.16f * (1f - p) * smoothstep(0f, 0.1f, p), colorFilter = smokeTint)
        }
        drawFlakes(snow, 0.3f, 0.75f, flake)
        drawPath(sc.nearTrees, Color(0xFF050912))
        drawFlakes(snow, 0.75f, 1.01f, flake)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), center, size.maxDimension * 0.8f))
        drawGrain(grain)
    }
}

private fun DrawScope.drawFlakes(snow: Snow, from: Float, to: Float, filter: ColorFilter) {
    val unit = size.minDimension
    for (i in 0 until snow.n) {
        val d = snow.depth[i]
        if (d < from || d >= to) continue
        val o = Offset(snow.x[i] * size.width, snow.y[i] * size.height)
        if (d > 0.75f) {
            stamp(SoftDot, o, unit * (0.02f + (d - 0.75f) * 0.08f), alpha = 0.35f, colorFilter = filter)
        } else {
            drawCircle(Color(0xFFEAF2FF).copy(alpha = 0.35f + d * 0.5f), unit * (0.0012f + d * 0.004f), o)
        }
    }
}

// ---------------------------------------------------------------- Candle

@Composable
fun CandleSaver(ctx: SaverContext) {
    val grain = rememberGrainBrush()
    val blowAt = remember { floatArrayOf(-100f) }
    val cooldown = remember { Cooldown(30_000) }
    val time = rememberFrameTime()
    OnSaverTap { if (cooldown.tryAcquire()) blowAt[0] = time.value }
    val flame = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        // A gentle breath on the flame when tapped: it bends, gutters and recovers.
        val since = t - blowAt[0]
        val blow = if (since in 0f..6f) sin(since / 6f * PI.toFloat()).let { it * it } else 0f
        val flick = noise1(t * 2.3f, 1) * 0.6f + noise1(t * 7.1f, 2) * 0.3f + noise1(t * 13f, 3) * 0.1f
        val bright = (0.9f + 0.1f * flick) * (1f - blow * 0.55f)
        val landscape = w > h
        val cw = unit * if (landscape) 0.16f else 0.2f
        val ch = unit * if (landscape) 0.34f else 0.5f
        val cx = w / 2
        val top = h * if (landscape) 0.3f else 0.46f
        val bottom = top + ch
        val wick = Offset(cx, top - unit * 0.012f)
        val flameH = unit * ((if (landscape) 0.13f else 0.16f) + 0.012f * flick) * (1f - blow * 0.4f)
        val sway = (flick * 0.05f + blow * 0.45f * sin(since * 9f)) * flameH
        val glowC = wick - Offset(-sway * 0.3f, flameH * 0.45f)
        drawRect(Color.Black)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9A4A).copy(alpha = 0.2f * bright), Color(0xFF5A2410).copy(alpha = 0.08f * bright), Color.Transparent), glowC, size.maxDimension * 0.6f), size.maxDimension * 0.6f, glowC)
        // Table and the light pooled on it.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0705), Color(0xFF050302)), bottom, h), Offset(0f, bottom), Size(w, h - bottom))
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFB06A).copy(alpha = 0.25f * bright), Color.Transparent), Offset(cx, bottom), unit * 0.5f), Offset(cx - unit * 0.55f, bottom - unit * 0.03f), Size(unit * 1.1f, unit * 0.12f))
        // Holder.
        drawOval(Brush.horizontalGradient(listOf(Color(0xFF3A2A1E), Color(0xFFB58A5A), Color(0xFF2A1E15)), cx - cw, cx + cw), Offset(cx - cw * 1.0f, bottom - unit * 0.03f), Size(cw * 2f, unit * 0.06f))
        // Wax body: rounded with light glowing through near the top.
        val body = Rect(cx - cw / 2, top, cx + cw / 2, bottom - unit * 0.01f)
        drawRect(Brush.horizontalGradient(0f to Color(0xFF8C7A64), 0.35f to Color(0xFFE9DCC6), 0.6f to Color(0xFFF4EAD8), 1f to Color(0xFF7A6A56), startX = body.left, endX = body.right), body.topLeft, body.size)
        drawRect(Brush.verticalGradient(listOf(Color(0xFFFFC27A).copy(alpha = 0.55f * bright), Color.Transparent), body.top, body.top + ch * 0.35f), body.topLeft, Size(body.width, ch * 0.35f), blendMode = BlendMode.Plus)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), body.top + ch * 0.3f, body.bottom), body.topLeft, body.size)
        // A drip down the side.
        drawRoundRect(Color(0xFFEFE3CF).copy(alpha = 0.85f), Offset(body.left + cw * 0.12f, body.top), Size(cw * 0.08f, ch * 0.16f), CornerRadius(cw * 0.04f))
        drawCircle(Color(0xFFEFE3CF).copy(alpha = 0.85f), cw * 0.055f, Offset(body.left + cw * 0.16f, body.top + ch * 0.16f))
        // Melted pool on top.
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFD9A0).copy(alpha = 0.95f * bright), Color(0xFFE2C59C)), Offset(cx, top), cw * 0.6f), Offset(body.left, top - unit * 0.016f), Size(cw, unit * 0.032f))
        // Wick.
        drawLine(Color(0xFF1A120C), Offset(cx, top - unit * 0.004f), wick + Offset(sway * 0.05f, 0f), strokeWidth = unit * 0.006f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFFF6A1A).copy(alpha = 0.9f), unit * 0.004f, wick)
        // The flame: a blue root, a glowing outer envelope and a bright core.
        fun flamePath(scale: Float) {
            val fh = flameH * scale
            val fw = flameH * 0.32f * scale
            val tip = wick + Offset(sway, -fh)
            flame.reset()
            flame.moveTo(wick.x, wick.y + fw * 0.15f)
            flame.cubicTo(wick.x - fw * 1.05f, wick.y - fh * 0.08f, wick.x - fw * 0.55f + sway * 0.4f, wick.y - fh * 0.6f, tip.x, tip.y)
            flame.cubicTo(wick.x + fw * 0.55f + sway * 0.4f, wick.y - fh * 0.6f, wick.x + fw * 1.05f, wick.y - fh * 0.08f, wick.x, wick.y + fw * 0.15f)
            flame.close()
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB35A).copy(alpha = 0.45f * bright), Color.Transparent), glowC, flameH * 0.9f), flameH * 0.9f, glowC, blendMode = BlendMode.Plus)
        flamePath(1f)
        drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFF7A1E).copy(alpha = 0.0f), Color(0xFFFFA43A).copy(alpha = 0.85f * bright), Color(0xFFFFD27A).copy(alpha = 0.95f * bright)), wick.y - flameH, wick.y), blendMode = BlendMode.Plus)
        flamePath(0.62f)
        drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFFF4D8).copy(alpha = 0.6f * bright), Color.White.copy(alpha = 0.95f * bright)), wick.y - flameH * 0.62f, wick.y), blendMode = BlendMode.Plus)
        drawOval(Color(0xFF3F6BFF).copy(alpha = 0.35f * bright), Offset(wick.x - flameH * 0.09f, wick.y - flameH * 0.1f), Size(flameH * 0.18f, flameH * 0.16f), blendMode = BlendMode.Plus)
        // A wisp of smoke when the flame gutters.
        if (blow > 0.05f) {
            for (k in 0 until 14) {
                val f = k / 14f
                val o = wick + Offset(sin(f * 8f + since * 2f) * unit * 0.02f * f, -flameH - f * unit * 0.25f)
                drawCircle(Color(0xFF8A8A8A).copy(alpha = 0.06f * blow * (1f - f)), unit * (0.008f + f * 0.03f), o)
            }
        }
        drawGrain(grain, 0.06f)
    }
}

// ---------------------------------------------------------------- Koi Pond

private class Pond {
    class Drop(val x: Float, val y: Float, var age: Float, val strength: Float)

    val drops = ArrayList<Drop>()
    private val r = Random(22)
    private var next = 0.5f
    var koiX = 0.4f
    var koiY = 0.6f
    var koiHeading = 0f
    var koiTarget: Offset? = null
    private var targetSet = -100f

    fun drop(x: Float, y: Float, strength: Float = 1f) {
        drops += Drop(x, y, 0f, strength)
    }

    fun call(x: Float, y: Float, t: Float) {
        koiTarget = Offset(x, y)
        targetSet = t
        drop(x, y, 1.4f)
    }

    fun step(dt: Float, t: Float) {
        if (t > next) {
            drop(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat() * 0.5f)
            next = t + 0.6f + r.nextFloat() * 2.2f
        }
        drops.forEach { it.age += dt }
        drops.removeAll { it.age > 6f }
        // The koi wanders lazily, or swims over to where you tapped.
        val target = koiTarget?.takeIf { t - targetSet < 12f } ?: Offset(0.5f + 0.35f * sin(t * 0.05f), 0.55f + 0.3f * sin(t * 0.083f + 1f))
        val desired = atan2(target.y - koiY, target.x - koiX)
        var diff = desired - koiHeading
        while (diff > PI) diff -= TAU
        while (diff < -PI) diff += TAU
        koiHeading += diff.coerceIn(-1f, 1f) * dt * 0.8f
        val speed = if (koiTarget != null && t - targetSet < 12f) 0.06f else 0.025f
        koiX += cos(koiHeading) * speed * dt
        koiY += sin(koiHeading) * speed * dt
    }
}

@Composable
fun KoiPondSaver(ctx: SaverContext) {
    val pond = remember { Pond() }
    val grain = rememberGrainBrush()
    val pads = remember { val r = Random(31); List(7) { floatArrayOf(r.nextFloat(), 0.12f + r.nextFloat() * 0.8f, 0.05f + r.nextFloat() * 0.05f, r.nextFloat() * 360f, r.nextFloat()) } }
    val cooldown = remember { Cooldown(5_000) }
    val canvasSize = remember { floatArrayOf(1f, 1f) }
    val time = rememberFrameTime { dt, t -> pond.step(dt, t) }
    OnSaverTap { at -> if (cooldown.tryAcquire()) pond.call(at.x / canvasSize[0], at.y / canvasSize[1], time.value) }
    val koi = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        canvasSize[0] = w
        canvasSize[1] = h
        val unit = size.minDimension
        drawRect(Brush.verticalGradient(listOf(Color(0xFF02101A), Color(0xFF052032), Color(0xFF031521))))
        // Moonlight on the water.
        val moon = Offset(w * 0.64f, h * 0.26f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFA9C4E2).copy(alpha = 0.16f), Color.Transparent), moon, unit * 0.42f), unit * 0.42f, moon)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE8F0FA).copy(alpha = 0.35f), Color.Transparent), moon, unit * 0.06f), unit * 0.06f, moon)
        // The koi, under the surface.
        drawKoi(koi, Offset(pond.koiX * w, pond.koiY * h), pond.koiHeading, unit * 0.22f, t)
        // Ripples: rings of light with a shadow just outside, squashed by perspective.
        pond.drops.forEach { d ->
            val o = Offset(d.x * w, d.y * h)
            for (k in 0 until 3) {
                val rad = (d.age * 0.07f - k * 0.025f) * unit
                if (rad <= 0f) continue
                val a = (1f - d.age / 6f).coerceIn(0f, 1f).pow(1.5f) * (1f - k * 0.28f) * d.strength
                val tl = Offset(o.x - rad, o.y - rad * 0.5f)
                drawOval(Color(0xFFBFDDF2).copy(alpha = 0.3f * a), tl, Size(rad * 2, rad), style = Stroke(density * 1.2f))
                val r2 = rad + unit * 0.006f
                drawOval(Color.Black.copy(alpha = 0.35f * a), Offset(o.x - r2, o.y - r2 * 0.5f), Size(r2 * 2, r2), style = Stroke(density * 1.2f))
            }
        }
        // Lily pads and a lotus.
        pads.forEachIndexed { i, p ->
            val bob = sin(t * 0.4f + p[4] * TAU) * unit * 0.002f
            val o = Offset(p[0] * w, p[1] * h + bob)
            val rx = p[2] * unit * 1.6f
            withTransform({ scale(1f, 0.55f, o) }) {
                drawArc(Brush.radialGradient(listOf(Color(0xFF1F5A3A), Color(0xFF0D2E1F)), o, rx), p[3] + 18f, 324f, true, o - Offset(rx, rx), Size(rx * 2, rx * 2))
                for (v in 0 until 7) {
                    val a = (p[3] + 30f + v * 47f) * PI.toFloat() / 180f
                    drawLine(Color(0xFF2E7A50).copy(alpha = 0.35f), o, o + Offset(cos(a), sin(a)) * rx * 0.9f, strokeWidth = density * 0.6f)
                }
            }
            if (i == 2) drawLotus(o - Offset(0f, rx * 0.12f), rx * 0.55f)
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), center, size.maxDimension * 0.75f))
        drawGrain(grain)
    }
}

private fun DrawScope.drawKoi(path: Path, o: Offset, heading: Float, length: Float, t: Float) {
    val wag = sin(t * 3f) * 0.25f
    rotate(heading * 180f / PI.toFloat(), o) {
        val l = length
        path.reset()
        path.moveTo(o.x + l * 0.5f, o.y)
        path.cubicTo(o.x + l * 0.35f, o.y - l * 0.16f, o.x - l * 0.2f, o.y - l * 0.14f, o.x - l * 0.38f, o.y + wag * l * 0.1f)
        path.lineTo(o.x - l * 0.62f, o.y - l * 0.14f + wag * l * 0.3f)
        path.lineTo(o.x - l * 0.56f, o.y + wag * l * 0.2f)
        path.lineTo(o.x - l * 0.62f, o.y + l * 0.14f + wag * l * 0.3f)
        path.lineTo(o.x - l * 0.38f, o.y + wag * l * 0.1f)
        path.cubicTo(o.x - l * 0.2f, o.y + l * 0.14f, o.x + l * 0.35f, o.y + l * 0.16f, o.x + l * 0.5f, o.y)
        path.close()
        drawPath(path, Brush.horizontalGradient(listOf(Color(0xFFFFF2E6), Color(0xFFFF7A2E), Color(0xFFFFE8D8)), o.x + l * 0.5f, o.x - l * 0.6f), alpha = 0.55f)
        drawOval(Color(0xFFE2471A).copy(alpha = 0.4f), Offset(o.x - l * 0.05f, o.y - l * 0.08f), Size(l * 0.25f, l * 0.12f))
        // Fins.
        drawOval(Color(0xFFFFE8D8).copy(alpha = 0.25f), Offset(o.x + l * 0.12f, o.y - l * 0.22f + wag * l * 0.04f), Size(l * 0.12f, l * 0.1f))
        drawOval(Color(0xFFFFE8D8).copy(alpha = 0.25f), Offset(o.x + l * 0.12f, o.y + l * 0.12f - wag * l * 0.04f), Size(l * 0.12f, l * 0.1f))
    }
}

private fun DrawScope.drawLotus(o: Offset, r: Float) {
    for (layer in 0..1) {
        val n = if (layer == 0) 8 else 6
        val pr = r * if (layer == 0) 1f else 0.7f
        for (k in 0 until n) {
            val a = 360f * k / n + layer * 22f
            rotate(a, o) {
                drawOval(
                    Brush.verticalGradient(listOf(Color(0xFFFFE3EC), Color(0xFFF07AA4)), o.y - pr, o.y),
                    Offset(o.x - pr * 0.22f, o.y - pr), Size(pr * 0.44f, pr), alpha = 0.92f,
                )
            }
        }
    }
    drawCircle(Color(0xFFFFD25A), r * 0.18f, o)
}
