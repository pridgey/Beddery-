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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.SoftDot
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.TAU
import dev.pridgey.beddery.savers.TextureBrush
import dev.pridgey.beddery.savers.addPine
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.layer
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.noise2
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


/**
 * The city beyond a rainy window, all out of focus: lit windows up high, a row of streetlights
 * receding into the distance, cars passing both ways on the street, a traffic light, and the
 * wet road reflecting it all.
 */
private class City {
    private val r = Random(12)
    // x, y, size, warm?, flicker phase
    val windows = List(70) {
        val column = r.nextInt(9)
        floatArrayOf(0.04f + column * 0.115f + r.nextFloat() * 0.05f, 0.06f + r.nextFloat() * 0.4f, 0.012f + r.nextFloat() * 0.012f, if (r.nextFloat() < 0.75f) 1f else 0f, r.nextFloat() * TAU)
    }
    val lamps = List(7) { i -> val f = i / 6f; floatArrayOf(-0.04f + f * 1.1f, 0.5f + f * 0.05f, 0.075f - f * 0.045f) }

    // x, lane (0 near going right with headlights, 1 far going left with taillights), speed
    val cars = ArrayList<FloatArray>()
    private var nextCar = 0f

    fun step(dt: Float, t: Float) {
        if (t > nextCar) {
            val lane = if (r.nextBoolean()) 0f else 1f
            cars += floatArrayOf(if (lane == 0f) -0.15f else 1.15f, lane, 0.05f + r.nextFloat() * 0.04f)
            nextCar = t + 2.5f + r.nextFloat() * 5f
        }
        cars.forEach { it[0] += (if (it[1] == 0f) 1 else -1) * it[2] * dt }
        cars.removeAll { it[0] < -0.2f || it[0] > 1.2f }
    }

    /** Traffic light color: a slow green, amber, red cycle. */
    fun signal(t: Float): Color = when ((t % 40f)) {
        in 0f..20f -> Color(0xFF3DFF9A)
        in 20f..23f -> Color(0xFFFFB13D)
        else -> Color(0xFFFF4A3D)
    }
}

private val Sodium = Color(0xFFFFA24A)
private val WarmWindow = Color(0xFFFFC98A)
private val CoolWindow = Color(0xFFD8E2FF)
private val Headlight = Color(0xFFF2F5FF)
private val Taillight = Color(0xFFFF3A2E)

@Composable
fun RainGlassSaver(ctx: SaverContext) {
    val glass = remember { Glass(if (ctx.preview) 140 else 340) }
    val city = remember { City() }
    val filters = remember { listOf(Sodium, WarmWindow, CoolWindow, Headlight, Taillight).associateWith { tint(it) } }
    val grain = rememberGrainBrush()
    val cooldown = remember { Cooldown(3_000) }
    val canvasSize = remember { floatArrayOf(1f, 1f) }
    val time = rememberFrameTime { dt, t ->
        glass.step(dt, t)
        city.step(dt, t)
    }
    OnSaverTap { at -> if (cooldown.tryAcquire()) glass.launch(at.x / canvasSize[0], at.y / canvasSize[1]) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        canvasSize[0] = w
        canvasSize[1] = h
        val unit = size.minDimension
        val road = h * 0.66f
        drawRect(Brush.verticalGradient(0f to Color(0xFF03040A), 0.45f to Color(0xFF0A0C16), 0.62f to Color(0xFF17151B), 1f to Color(0xFF07070C)))
        // Glow of the street lighting the rain-filled air.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Sodium.copy(alpha = 0.07f), Color.Transparent), h * 0.38f, h * 0.8f), blendMode = BlendMode.Plus)
        // Lit windows in the towers.
        city.windows.forEach { win ->
            val on = sin(t * 0.02f + win[4]) > -0.85f
            if (!on) return@forEach
            val c = if (win[3] == 1f) WarmWindow else CoolWindow
            stamp(SoftDot, Offset(win[0] * w, win[1] * h), win[2] * unit * 2.2f, alpha = 0.14f + 0.06f * sin(win[4]), colorFilter = filters[c], blendMode = BlendMode.Plus)
        }
        // Streetlights, nearest on the left, with their reflections on the wet road.
        city.lamps.forEach { l ->
            val o = Offset(l[0] * w, l[1] * h)
            val d = l[2] * unit * 2.4f
            stamp(SoftDot, o, d, alpha = 0.55f, colorFilter = filters[Sodium], blendMode = BlendMode.Plus)
            stamp(SoftDot, Offset(o.x, road + (road - o.y) * 0.6f + d * 0.4f), d * 0.35f, d * 2.4f, alpha = 0.22f, colorFilter = filters[Sodium], blendMode = BlendMode.Plus)
        }
        // A traffic light on the corner.
        val sig = city.signal(t)
        val so = Offset(w * 0.72f, h * 0.46f)
        stamp(SoftDot, so, unit * 0.07f, alpha = 0.5f, colorFilter = ColorFilter.tint(sig, BlendMode.Modulate), blendMode = BlendMode.Plus)
        stamp(SoftDot, Offset(so.x, road + unit * 0.12f), unit * 0.025f, unit * 0.16f, alpha = 0.18f, colorFilter = ColorFilter.tint(sig, BlendMode.Modulate), blendMode = BlendMode.Plus)
        // Traffic: headlights coming along the near lane, taillights going away on the far one.
        city.cars.forEach { car ->
            val near = car[1] == 0f
            val y = if (near) road + h * 0.03f else road - h * 0.012f
            val d = unit * if (near) 0.1f else 0.055f
            val c = if (near) Headlight else Taillight
            val x = car[0] * w
            for (k in 0..1) {
                val o = Offset(x + (k - 0.5f) * d * 0.9f, y)
                stamp(SoftDot, o, d, alpha = if (near) 0.5f else 0.55f, colorFilter = filters[c], blendMode = BlendMode.Plus)
                stamp(SoftDot, Offset(o.x, y + d * 1.2f), d * 0.3f, d * 2.2f, alpha = 0.18f, colorFilter = filters[c], blendMode = BlendMode.Plus)
            }
        }
        // Condensation on the glass.
        drawRect(Color(0xFF6A7A96).copy(alpha = 0.05f))
        for (i in 0 until glass.size.size) {
            val s = glass.size[i]
            if (s <= 0f) continue
            stamp(Droplet, Offset(glass.x[i] * w, glass.y[i] * h), s * unit * 2f, s * unit * 2.1f)
        }
        glass.runners.forEach { d ->
            val o = Offset(d.x * w, d.y * h)
            val px = d.size * unit
            drawLine(Color(0xFF9FB4D8).copy(alpha = 0.05f), o - Offset(0f, h * 0.18f), o, strokeWidth = px * 0.9f, cap = StrokeCap.Round)
            stamp(Droplet, o + Offset(0f, -px * 0.6f), px * 1.3f, px * 2f)
            stamp(Droplet, o, px * 2f, px * 2.3f)
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), center, size.maxDimension * 0.7f))
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

private class Winter(val far: Path, val near: Path, val forest: Path, val trees: Path, val nearTrees: Path, val drift: Path, val cabin: Rect, val ground: Float)

/** Draws [path] snow-covered: the snow color, then the dark shape nudged down, leaving snow on every top edge. */
private fun DrawScope.snowy(path: Path, dark: Color, snow: Color, depth: Float) {
    drawPath(path, snow)
    translate(0f, depth) { drawPath(path, dark) }
}

@Composable
fun SnowfallSaver(ctx: SaverContext) {
    val snow = remember { Snow(if (ctx.preview) 160 else 380) }
    val grain = rememberGrainBrush()
    val clouds = rememberNoiseBrush(256, 3, 6, seed = 44, contrast = 2.2f)
    val flake = remember { tint(Color(0xFFEAF2FF)) }
    val smokeTint = remember { tint(Color(0xFF9AA8BF)) }
    val cooldown = remember { Cooldown(20_000) }
    val time = rememberFrameTime { dt, t -> snow.step(dt, t) }
    OnSaverTap { if (cooldown.tryAcquire()) snow.gust(time.value) }
    val scene = remember {
        SizeCache { s ->
            val w = s.width
            val h = s.height
            val ground = h * 0.72f
            val unit = s.minDimension
            Winter(
                far = ridgePath(ridge(65, 4, 0.55f), w, ground - h * 0.12f, h * 0.12f, ground),
                near = ridgePath(ridge(65, 9, 0.5f), w, ground - h * 0.05f, h * 0.06f, ground),
                forest = pineLine(w, ground - h * 0.03f, h * 0.025f, h * 0.06f, w * 0.022f, 15, ground + 4f),
                trees = pineLine(w, ground + h * 0.005f, h * 0.04f, h * 0.09f, w * 0.05f, 27, ground + 4f),
                nearTrees = Path().apply {
                    addPine(w * 0.05f, h * 1.02f, h * 0.48f, 3)
                    addPine(w * 0.18f, h * 1.02f, h * 0.3f, 11)
                    addPine(w * 0.95f, h * 1.02f, h * 0.42f, 7)
                },
                drift = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, ground + h * 0.12f)
                    cubicTo(w * 0.3f, ground + h * 0.06f, w * 0.6f, ground + h * 0.16f, w, ground + h * 0.1f)
                    lineTo(w, h)
                    close()
                },
                cabin = (if (w > h) w * 0.68f else w * 0.54f).let { cx -> Rect(cx, ground - unit * 0.11f, cx + unit * 0.24f, ground + unit * 0.008f) },
                ground = ground,
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        val sc = scene.get(size)
        val ground = sc.ground
        // Night sky with the moon glowing behind thin cloud.
        drawRect(Brush.verticalGradient(0f to Color(0xFF060A15), 0.6f to Color(0xFF111B2E), 1f to Color(0xFF1C2840), endY = ground))
        val moon = Offset(w * 0.26f, h * 0.15f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFC9D6EA).copy(alpha = 0.3f), Color(0xFF6A7C9A).copy(alpha = 0.08f), Color.Transparent), moon, unit * 0.6f), unit * 0.6f, moon)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF2F5FA), Color(0xFFDDE5F0).copy(alpha = 0.6f), Color.Transparent), moon, unit * 0.05f), unit * 0.05f, moon)
        drawRect(clouds.place(w * 1.5f, h * 0.3f, -t * w * 0.004f, 0f), size = Size(w, ground * 0.75f), colorFilter = ColorFilter.tint(Color(0xFF8E9FBC), BlendMode.SrcIn), alpha = 0.16f)
        drawFlakes(snow, 0f, 0.3f, flake)
        // Mountains fading into the snowy haze.
        drawPath(sc.far, Brush.verticalGradient(listOf(Color(0xFF5A6C88), Color(0xFF2E3C55)), ground - h * 0.24f, ground))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF1E2A40).copy(alpha = 0.8f)), ground - h * 0.12f, ground), Offset(0f, ground - h * 0.12f), Size(w, h * 0.12f))
        drawPath(sc.near, Brush.verticalGradient(listOf(Color(0xFF3E4E68), Color(0xFF25324A)), ground - h * 0.11f, ground))
        snowy(sc.forest, Color(0xFF111A2A), Color(0xFF3C4C66), 1.2f * density)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF1B2638).copy(alpha = 0.7f)), ground - h * 0.05f, ground), Offset(0f, ground - h * 0.05f), Size(w, h * 0.05f))
        // Snow-covered ground.
        drawRect(Brush.verticalGradient(listOf(Color(0xFF4A5B78), Color(0xFF3A4A64), Color(0xFF26334A)), ground, h), Offset(0f, ground), Size(w, h - ground))

        // The cabin, warm inside.
        val c = sc.cabin
        val warm = 0.9f + 0.1f * noise1(t * 3f, 4)
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFB25A).copy(alpha = 0.32f * warm), Color.Transparent), Offset(c.center.x, c.bottom + unit * 0.02f), unit * 0.22f), Offset(c.center.x - unit * 0.26f, c.bottom - unit * 0.02f), Size(unit * 0.52f, unit * 0.1f))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2A1E16), Color(0xFF18110C)), c.top, c.bottom), c.topLeft, c.size)
        for (k in 1..5) drawLine(Color.Black.copy(alpha = 0.35f), Offset(c.left, c.top + c.height * k / 6f), Offset(c.right, c.top + c.height * k / 6f), strokeWidth = density * 0.8f)
        val chimney = Rect(c.right - c.width * 0.26f, c.top - c.height * 0.85f, c.right - c.width * 0.14f, c.top - c.height * 0.2f)
        drawRect(Color(0xFF1C1612), chimney.topLeft, chimney.size)
        drawRoundRect(Color(0xFFDDE6F2), Offset(chimney.left - 2f, chimney.top - c.height * 0.06f), Size(chimney.width + 4f, c.height * 0.1f), CornerRadius(c.height * 0.05f))
        val roof = Path().apply {
            moveTo(c.left - c.width * 0.1f, c.top + c.height * 0.12f)
            lineTo(c.center.x, c.top - c.height * 0.6f)
            lineTo(c.right + c.width * 0.1f, c.top + c.height * 0.12f)
            close()
        }
        drawPath(roof, Color(0xFF1A130E))
        // A thick, rounded blanket of snow on the roof.
        val snowRoof = Path().apply {
            moveTo(c.left - c.width * 0.13f, c.top + c.height * 0.1f)
            quadraticTo(c.left - c.width * 0.16f, c.top + c.height * 0.2f, c.left - c.width * 0.05f, c.top + c.height * 0.14f)
            lineTo(c.center.x, c.top - c.height * 0.46f)
            lineTo(c.right + c.width * 0.05f, c.top + c.height * 0.14f)
            quadraticTo(c.right + c.width * 0.16f, c.top + c.height * 0.2f, c.right + c.width * 0.13f, c.top + c.height * 0.1f)
            lineTo(c.center.x, c.top - c.height * 0.66f)
            close()
        }
        drawPath(snowRoof, Brush.verticalGradient(listOf(Color(0xFFE8EEF7), Color(0xFFA8B6CC)), c.top - c.height * 0.66f, c.top + c.height * 0.2f))
        listOf(0.18f, 0.6f).forEach { fx ->
            val win = Rect(c.left + c.width * fx, c.top + c.height * 0.3f, c.left + c.width * (fx + 0.22f), c.top + c.height * 0.7f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB25A).copy(alpha = 0.22f * warm), Color.Transparent), win.center, unit * 0.06f), unit * 0.06f, win.center, blendMode = BlendMode.Plus)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFD08A), Color(0xFFFFA24A)), win.top, win.bottom), win.topLeft, win.size, alpha = warm)
            drawLine(Color(0xFF1A130E), Offset(win.center.x, win.top), Offset(win.center.x, win.bottom), strokeWidth = win.width * 0.12f)
            drawLine(Color(0xFF1A130E), Offset(win.left, win.center.y), Offset(win.right, win.center.y), strokeWidth = win.width * 0.12f)
            drawLine(Color(0xFFE8EEF7), Offset(win.left - 1f, win.bottom + 1f), Offset(win.right + 1f, win.bottom + 1f), strokeWidth = win.height * 0.14f)
        }
        // Wood smoke curling up from the chimney.
        for (k in 0 until 7) {
            val p = (t * 0.045f + k / 7f) % 1f
            val o = Offset(chimney.center.x + sin(p * 5f + k) * unit * 0.025f * p + snow.wind * unit * p * 2f, chimney.top - p * unit * 0.22f)
            stamp(SoftDot, o, unit * (0.03f + 0.09f * p), alpha = 0.14f * (1f - p) * smoothstep(0f, 0.1f, p), colorFilter = smokeTint)
        }
        snowy(sc.trees, Color(0xFF0A1220), Color(0xFF6E809C), 1.6f * density)
        // Footprints across the snow to the door.
        for (k in 0 until 9) {
            val f = k / 8f
            val x = w * 0.08f + (c.center.x - w * 0.08f) * f + if (k % 2 == 0) unit * 0.006f else -unit * 0.006f
            val y = h * 0.95f - (h * 0.95f - c.bottom - unit * 0.01f) * f
            val s = unit * (0.012f - f * 0.007f)
            drawOval(Color(0xFF1E2A3E).copy(alpha = 0.6f), Offset(x - s, y - s * 0.4f), Size(s * 2f, s * 0.8f))
        }
        drawPath(sc.drift, Brush.verticalGradient(listOf(Color(0xFF55678A), Color(0xFF2E3B52)), sc.ground + h * 0.06f, h))
        drawFlakes(snow, 0.3f, 0.75f, flake)
        snowy(sc.nearTrees, Color(0xFF050912), Color(0xFF8A9CB8), 2.2f * density)
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
