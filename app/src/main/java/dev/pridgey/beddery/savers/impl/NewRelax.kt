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


private class Camp(val trees: Path, val rocks: List<Path>, val rockCenters: List<Offset>)

/** An irregular rock outline around [c]. */
private fun rock(c: Offset, rx: Float, ry: Float, seed: Int): Path = Path().apply {
    val n = 10
    for (k in 0..n) {
        val a = TAU * k / n
        val rr = 0.82f + 0.3f * hash(seed, k % n)
        val p = Offset(c.x + cos(a) * rx * rr, c.y + sin(a) * ry * rr * if (sin(a) > 0) 0.7f else 1f)
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

@Composable
fun CampfireSaver(ctx: SaverContext) {
    val fire = remember { Fire(if (ctx.preview) 50 else 110) }
    val stars = remember { StarSky(180, 19, 0f, 0.5f) }
    val grain = rememberGrainBrush()
    val bark = rememberNoiseBrush(128, 8, 4, seed = 61, contrast = 1.8f)
    val coals = rememberNoiseBrush(128, 6, 4, seed = 62, contrast = 2.4f)
    val filters = remember { (0..20).map { tint(fireColor(it / 20f)) } }
    val sparkFilter = remember { tint(Color(0xFFFFB04A)) }
    val smokeFilter = remember { tint(Color(0xFF4A3A34)) }
    val cooldown = remember { Cooldown(8_000) }
    val time = rememberFrameTime { dt, t -> fire.step(dt, t) }
    OnSaverTap { if (cooldown.tryAcquire()) fire.burst() }
    val scene = remember {
        SizeCache { s ->
            val unit = s.minDimension
            val base = fireBase(s)
            val ground = base.y - unit * 0.12f
            val rocks = (0 until 11).map { k ->
                val a = PI.toFloat() * (-0.08f + 1.16f * k / 10f)
                Offset(base.x + cos(a) * unit * 0.26f, base.y + unit * 0.02f + sin(a) * unit * 0.055f) to k
            }
            Camp(
                trees = pineLine(s.width, ground, unit * 0.06f, unit * 0.2f, s.width * 0.07f, 23, ground + 2f),
                rocks = rocks.map { (c, k) -> rock(c, unit * (0.045f + hash(k) * 0.02f), unit * 0.032f, k) },
                rockCenters = rocks.map { it.first },
            )
        }
    }
    val tongue = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        val base = fireBase(size)
        val sc = scene.get(size)
        val flicker = 0.85f + 0.1f * noise1(t * 6f, 1) + 0.05f * noise1(t * 17f, 2) + fire.flareLevel * 0.3f
        val ground = base.y - unit * 0.12f

        drawRect(Brush.verticalGradient(0f to Color(0xFF02030A), 0.7f to Color(0xFF0A0812), endY = ground))
        stars.draw(this, t, 0.6f, horizonFade = 0.5f)
        // Trees at the edge of the clearing, their trunks catching a little firelight.
        drawPath(sc.trees, Brush.verticalGradient(listOf(Color(0xFF040509), Color(0xFF120A08)), ground - unit * 0.2f, ground))
        drawRect(Brush.verticalGradient(listOf(Color(0xFF140C09), Color(0xFF060403)), ground, h), Offset(0f, ground), Size(w, h - ground))
        // Firelight on the ground and in the air.
        drawOval(
            Brush.radialGradient(listOf(Color(0xFFFF7A2A).copy(alpha = 0.32f * flicker), Color(0xFF8A2A0A).copy(alpha = 0.1f * flicker), Color.Transparent), base, unit * 0.7f),
            Offset(base.x - unit * 0.9f, base.y - unit * 0.22f), Size(unit * 1.8f, unit * 0.5f),
        )
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A3A).copy(alpha = 0.14f * flicker), Color.Transparent), base - Offset(0f, unit * 0.15f), unit * 0.8f), unit * 0.8f, base - Offset(0f, unit * 0.15f))

        // Bed of glowing coals.
        val bed = Rect(base.x - unit * 0.17f, base.y - unit * 0.035f, base.x + unit * 0.17f, base.y + unit * 0.04f)
        drawOval(Color(0xFF140704), bed.topLeft, bed.size)
        layer(bed.inflate(4f)) {
            drawOval(Brush.radialGradient(listOf(Color(0xFFFF6A1A), Color(0xFF8A1A05), Color(0xFF2A0A04)), bed.center, bed.width / 2), bed.topLeft, bed.size)
            drawRect(coals.place(unit * 0.12f, unit * 0.12f, t * unit * 0.004f, 0f), bed.topLeft, bed.size, colorFilter = ColorFilter.tint(Color(0xFFFFC04A), BlendMode.SrcIn), alpha = 0.6f * flicker, blendMode = BlendMode.SrcAtop)
        }

        // Logs leaning in, with bark, cut ends and glowing char where they burn.
        listOf(-28f to 0f, 26f to 0.01f, -8f to -0.02f, 168f to 0.005f).forEachIndexed { i, (deg, dy) ->
            rotate(deg, base + Offset(0f, unit * dy)) {
                val lw = unit * 0.36f
                val lh = unit * 0.05f
                val tl = base + Offset(-lw * 0.05f, -lh / 2 + unit * dy)
                val log = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(tl.x, tl.y, tl.x + lw, tl.y + lh, CornerRadius(lh / 2))) }
                drawPath(log, Brush.verticalGradient(listOf(Color(0xFF6A4028), Color(0xFF3A2216), Color(0xFF140B07)), tl.y, tl.y + lh))
                clipPath(log) {
                    drawRect(bark.place(unit * 0.12f, unit * 0.03f, i * 37f, 0f), tl, Size(lw, lh), colorFilter = ColorFilter.tint(Color(0xFF0A0503), BlendMode.SrcIn), alpha = 0.55f)
                    drawRect(
                        Brush.horizontalGradient(listOf(Color(0xFFFF6A1A).copy(alpha = 0.9f * flicker), Color(0xFFB8300A).copy(alpha = 0.35f), Color.Transparent), tl.x, tl.x + lw * 0.45f),
                        tl, Size(lw * 0.45f, lh), blendMode = BlendMode.Plus,
                    )
                }
                // The cut end, facing out of the fire.
                val end = Offset(tl.x + lw - lh * 0.15f, tl.y + lh / 2)
                drawOval(Color(0xFF9A6A44), Offset(end.x - lh * 0.22f, end.y - lh / 2), Size(lh * 0.44f, lh))
                drawOval(Color(0xFF6A4228), Offset(end.x - lh * 0.13f, end.y - lh * 0.3f), Size(lh * 0.26f, lh * 0.6f), style = Stroke(1.2f * density))
                drawOval(Color(0xFF6A4228), Offset(end.x - lh * 0.06f, end.y - lh * 0.13f), Size(lh * 0.12f, lh * 0.26f), style = Stroke(1f * density))
            }
        }

        // Rocks, lit on the side facing the fire.
        sc.rocks.forEachIndexed { k, path ->
            val c = sc.rockCenters[k]
            val toFire = (base - c).let { it / (it.getDistance().coerceAtLeast(1f)) }
            drawPath(
                path,
                Brush.linearGradient(listOf(Color(0xFF9A6A50).copy(alpha = flicker.coerceAtMost(1f)), Color(0xFF2A1E1A), Color(0xFF0E0A09)), c + toFire * unit * 0.04f, c - toFire * unit * 0.04f),
            )
        }

        // Flames: tongues of fire, hottest and whitest at the base.
        val by = base.y - unit * 0.015f
        for (layer in 0..1) {
            val n = if (layer == 0) 7 else 5
            for (i in 0 until n) {
                val seed = i + layer * 10
                val offset = ((i + 0.5f) / n - 0.5f) * unit * if (layer == 0) 0.2f else 0.11f
                val height = unit * (if (layer == 0) 0.2f else 0.13f) * (0.7f + 0.4f * (0.5f + 0.5f * noise1(t * 2.2f + seed * 3.1f, seed))) *
                    (1f - abs(offset) / (unit * 0.16f)).coerceAtLeast(0.35f) * (1f + fire.flareLevel * 0.5f)
                val bw = unit * if (layer == 0) 0.035f else 0.025f
                val sway = noise1(t * 1.7f + seed * 2.3f, seed + 40) * height * 0.28f
                val x = base.x + offset
                tongue.reset()
                tongue.moveTo(x - bw, by)
                tongue.cubicTo(x - bw * 1.3f, by - height * 0.35f, x - bw * 0.3f + sway * 0.6f, by - height * 0.7f, x + sway, by - height)
                tongue.cubicTo(x + bw * 0.4f + sway * 0.6f, by - height * 0.65f, x + bw * 1.3f, by - height * 0.3f, x + bw, by)
                tongue.close()
                val colors = if (layer == 0) listOf(Color(0xFFFFD27A), Color(0xFFFF8A26), Color(0xFFD2380F).copy(alpha = 0.6f), Color.Transparent)
                else listOf(Color(0xFFFFFBE8), Color(0xFFFFE08A), Color(0xFFFFA43A).copy(alpha = 0.5f), Color.Transparent)
                drawPath(tongue, Brush.verticalGradient(colors, by, by - height), alpha = 0.8f, blendMode = BlendMode.Plus)
            }
        }
        // A little turbulence on top.
        for (i in 0 until fire.x.size) {
            val p = (fire.age[i] / fire.life[i]).coerceIn(0f, 1f)
            if (p < 0.25f) continue
            val o = Offset(base.x + fire.x[i] * unit * 0.8f, by + fire.y[i] * unit)
            val s = fire.size[i] * unit * (1f - p * 0.7f) * 1.4f
            stamp(SoftDot, o, s * 0.8f, s, alpha = (1f - p) * 0.3f, colorFilter = filters[(p * 20).toInt().coerceIn(0, 20)], blendMode = BlendMode.Plus)
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE0A0).copy(alpha = 0.35f * flicker), Color.Transparent), base - Offset(0f, unit * 0.06f), unit * 0.14f), unit * 0.14f, base - Offset(0f, unit * 0.06f), blendMode = BlendMode.Plus)
        // Smoke drifting up out of the light.
        for (k in 0 until 6) {
            val p = (t * 0.035f + k / 6f) % 1f
            val o = Offset(base.x + sin(p * 4f + k) * unit * 0.05f * p, base.y - unit * 0.25f - p * unit * 0.5f)
            stamp(SoftDot, o, unit * (0.08f + 0.2f * p), alpha = 0.1f * sin(p * PI.toFloat()), colorFilter = smokeFilter)
        }
        // Embers rising.
        for (i in fire.sage.indices) {
            val p = fire.sage[i] / fire.slife[i]
            if (p >= 1f) continue
            val o = Offset(base.x + fire.sx[i] * unit, base.y + fire.sy[i] * unit)
            val a = (1f - p) * (0.6f + 0.4f * sin(t * 20f + i))
            stamp(SoftDot, o, unit * 0.012f, alpha = a, colorFilter = sparkFilter, blendMode = BlendMode.Plus)
            drawCircle(Color(0xFFFFE2A0).copy(alpha = a), unit * 0.0018f, o)
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)), base, size.maxDimension * 0.8f))
        drawGrain(grain)
    }
}

private fun fireBase(s: Size) = Offset(s.width / 2, s.height * if (s.width > s.height) 0.5f else 0.64f)

// ---------------------------------------------------------------- Candle

@Composable
fun CandleSaver(ctx: SaverContext) {
    val grain = rememberGrainBrush()
    val wax = rememberNoiseBrush(128, 6, 4, seed = 71, contrast = 1.5f)
    val blowAt = remember { floatArrayOf(-100f) }
    val cooldown = remember { Cooldown(30_000) }
    val time = rememberFrameTime()
    OnSaverTap { if (cooldown.tryAcquire()) blowAt[0] = time.value }
    val flame = remember { Path() }
    val body = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        val landscape = w > h
        // A breath on the flame when tapped: it bends, gutters and recovers.
        val since = t - blowAt[0]
        val blow = if (since in 0f..6f) sin(since / 6f * PI.toFloat()).let { it * it } else 0f
        val flick = noise1(t * 2.3f, 1) * 0.6f + noise1(t * 7.1f, 2) * 0.3f + noise1(t * 13f, 3) * 0.1f
        val bright = (0.92f + 0.08f * flick) * (1f - blow * 0.55f)

        val table = h * if (landscape) 0.8f else 0.7f
        val cw = unit * 0.19f
        val ch = unit * if (landscape) 0.3f else 0.34f
        val cx = w / 2
        val top = table - unit * 0.035f - ch
        val rimRy = cw * 0.11f
        val wick = Offset(cx, top - unit * 0.012f)
        val flameH = unit * (0.13f + 0.01f * flick) * (1f - blow * 0.4f)
        val sway = (flick * 0.05f + blow * 0.45f * sin(since * 9f)) * flameH
        val glowC = wick - Offset(-sway * 0.3f, flameH * 0.45f)

        // The room: a warm pool of light on the wall and the table.
        drawRect(Color.Black)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF3A1E0C).copy(alpha = 0.55f * bright), Color(0xFF1A0C05).copy(alpha = 0.3f * bright), Color.Transparent), glowC, size.maxDimension * 0.65f), size.maxDimension * 0.65f, glowC)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF140B06), Color(0xFF060302)), table, h), Offset(0f, table), Size(w, h - table))
        drawLine(Color(0xFF5A3A20).copy(alpha = 0.35f * bright), Offset(0f, table), Offset(w, table), strokeWidth = density)
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFB06A).copy(alpha = 0.26f * bright), Color.Transparent), Offset(cx, table), unit * 0.55f), Offset(cx - unit * 0.6f, table - unit * 0.02f), Size(unit * 1.2f, unit * 0.16f))

        // Brass holder.
        val dish = Rect(cx - cw * 0.95f, table - unit * 0.045f, cx + cw * 0.95f, table + unit * 0.015f)
        drawOval(Color.Black.copy(alpha = 0.5f), dish.topLeft + Offset(0f, unit * 0.012f), dish.size)
        drawOval(Brush.horizontalGradient(listOf(Color(0xFF3A2814), Color(0xFFC8955A), Color(0xFF6A4A26), Color(0xFF2A1C0E)), dish.left, dish.right), dish.topLeft, dish.size)
        drawArc(Color(0xFFFFD9A0).copy(alpha = 0.5f * bright), 200f, 140f, false, dish.topLeft + Offset(dish.width * 0.1f, 0f), Size(dish.width * 0.8f, dish.height * 0.7f), style = Stroke(1.5f * density))

        // Wax body: a rounded pillar, glowing where the flame shines through it.
        val left = cx - cw / 2
        val right = cx + cw / 2
        val bottom = table - unit * 0.025f
        body.reset()
        body.moveTo(left, top)
        body.lineTo(left, bottom)
        body.arcTo(Rect(left, bottom - rimRy, right, bottom + rimRy), 180f, -180f, false)
        body.lineTo(right, top)
        // A slightly uneven melted lip across the front.
        val steps = 12
        for (k in steps downTo 0) {
            val f = k / steps.toFloat()
            val x = left + cw * f
            val lip = rimRy * sin(f * PI.toFloat()) + rimRy * 0.25f * noise1(f * 5f, 7)
            body.lineTo(x, top + lip)
        }
        body.close()
        drawPath(body, Brush.horizontalGradient(0f to Color(0xFF8C775C), 0.22f to Color(0xFFDCCDB2), 0.48f to Color(0xFFF4EBDB), 0.78f to Color(0xFFCDBC9F), 1f to Color(0xFF7A664E), startX = left, endX = right))
        clipPath(body) {
            drawRect(wax.place(unit * 0.15f, unit * 0.15f), Offset(left, top), Size(cw, ch + rimRy), colorFilter = ColorFilter.tint(Color(0xFF5A4A36), BlendMode.SrcIn), alpha = 0.06f)
            drawRect(Brush.verticalGradient(listOf(Color(0xFFFFB86A).copy(alpha = 0.7f * bright), Color(0xFFFF9A4A).copy(alpha = 0.18f * bright), Color.Transparent), top, top + ch * 0.45f), Offset(left, top), Size(cw, ch * 0.45f), blendMode = BlendMode.Plus)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), top + ch * 0.4f, bottom + rimRy), Offset(left, top), Size(cw, ch + rimRy))
        }
        // Drips running down from the lip.
        listOf(0.18f to 0.22f, 0.7f to 0.12f).forEach { (fx, len) ->
            val x = left + cw * fx
            val y0 = top + rimRy * sin(fx * PI.toFloat())
            val dw = cw * 0.07f
            drawRoundRect(Color(0xFFF1E6D2), Offset(x - dw / 2, y0 - dw * 0.3f), Size(dw, ch * len), CornerRadius(dw / 2))
            drawLine(Color.White.copy(alpha = 0.5f), Offset(x - dw * 0.15f, y0), Offset(x - dw * 0.15f, y0 + ch * len - dw), strokeWidth = dw * 0.18f, cap = StrokeCap.Round)
        }
        // Melted pool on top, lit from the flame above.
        val pool = Rect(left + cw * 0.06f, top - rimRy * 0.75f, right - cw * 0.06f, top + rimRy * 0.75f)
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFE2AE).copy(alpha = bright), Color(0xFFE8B477), Color(0xFFB98A55)), Offset(cx, top), cw * 0.55f), pool.topLeft, pool.size)
        drawArc(Color(0xFFFFF4E0).copy(alpha = 0.7f * bright), 0f, 180f, false, pool.topLeft, pool.size, style = Stroke(1.6f * density))

        // Wick.
        drawLine(Color(0xFF1A120C), Offset(cx, top), wick + Offset(sway * 0.05f, 0f), strokeWidth = unit * 0.005f, cap = StrokeCap.Round)
        drawCircle(Color(0xFFFF6A1A).copy(alpha = 0.9f), unit * 0.0035f, wick)

        // The flame: halo, luminous envelope, bright core, dark zone round the wick and a blue root.
        fun flamePath(scale: Float, lift: Float = 0f) {
            val fh = flameH * scale
            val fw = flameH * 0.3f * scale
            val b = wick - Offset(0f, lift)
            val tip = b + Offset(sway, -fh)
            flame.reset()
            flame.moveTo(b.x, b.y + fw * 0.2f)
            flame.cubicTo(b.x - fw * 1.1f, b.y - fh * 0.05f, b.x - fw * 0.6f + sway * 0.35f, b.y - fh * 0.62f, tip.x, tip.y)
            flame.cubicTo(b.x + fw * 0.6f + sway * 0.35f, b.y - fh * 0.62f, b.x + fw * 1.1f, b.y - fh * 0.05f, b.x, b.y + fw * 0.2f)
            flame.close()
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB35A).copy(alpha = 0.38f * bright), Color(0xFFFF8A3A).copy(alpha = 0.1f * bright), Color.Transparent), glowC, flameH * 1.6f), flameH * 1.6f, glowC, blendMode = BlendMode.Plus)
        flamePath(1f)
        drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFF8A2A).copy(alpha = 0f), Color(0xFFFFAE45).copy(alpha = 0.9f * bright), Color(0xFFFFD685).copy(alpha = bright)), wick.y - flameH, wick.y), blendMode = BlendMode.Plus)
        flamePath(0.58f, flameH * 0.08f)
        drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFFFF6DE).copy(alpha = 0.7f * bright), Color.White.copy(alpha = bright)), wick.y - flameH * 0.66f, wick.y), blendMode = BlendMode.Plus)
        drawOval(Color(0xFF2A1408).copy(alpha = 0.35f), Offset(wick.x - flameH * 0.05f, wick.y - flameH * 0.16f), Size(flameH * 0.1f, flameH * 0.18f))
        drawOval(Color(0xFF3F6BFF).copy(alpha = 0.4f * bright), Offset(wick.x - flameH * 0.1f, wick.y - flameH * 0.06f), Size(flameH * 0.2f, flameH * 0.12f), blendMode = BlendMode.Plus)
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

// ---------------------------------------------------------------- Koi Pond

private class KoiFish(seed: Int, val base: Color, val patch: Color, val patch2: Color?, val depth: Float, val length: Float) {
    private val r = Random(seed)
    var x = 0.2f + r.nextFloat() * 0.6f
    var y = 0.2f + r.nextFloat() * 0.6f
    var heading = r.nextFloat() * TAU
    var turn = 0f
    val patches = List(4) { floatArrayOf(0.15f + r.nextFloat() * 0.6f, (r.nextFloat() - 0.5f) * 0.6f, 0.3f + r.nextFloat() * 0.5f, if (r.nextFloat() < 0.3f) 2f else 1f) }
    private val seedF = seed * 3.7f

    fun step(dt: Float, t: Float, target: Offset?) {
        val desired = if (target != null) atan2(target.y - y, target.x - x) else heading + noise1(t * 0.15f + seedF, seed = 3) * 1.2f
        var diff = desired - heading
        while (diff > PI) diff -= TAU
        while (diff < -PI) diff += TAU
        // Steer away from the edges.
        if (x < 0.08f || x > 0.92f || y < 0.08f || y > 0.92f) diff = atan2(0.5f - y, 0.5f - x) - heading
        while (diff > PI) diff -= TAU
        while (diff < -PI) diff += TAU
        turn = diff.coerceIn(-1f, 1f) * 0.7f
        heading += turn * dt
        val speed = if (target != null) 0.05f else 0.02f
        x += cos(heading) * speed * dt
        y += sin(heading) * speed * dt
    }
}

private class Pond {
    class Drop(val x: Float, val y: Float, var age: Float, val strength: Float)

    val drops = ArrayList<Drop>()
    private val r = Random(22)
    private var next = 0.5f
    var target: Offset? = null
    private var targetSet = -100f
    val koi = listOf(
        KoiFish(1, Color(0xFFF6F0E8), Color(0xFFE2471A), null, 0.2f, 0.24f),
        KoiFish(2, Color(0xFFF2A23A), Color(0xFFFFD27A), null, 0.6f, 0.2f),
        KoiFish(3, Color(0xFF1C1C1E), Color(0xFFE2471A), Color(0xFFF2EDE6), 0.9f, 0.18f),
    )

    fun drop(x: Float, y: Float, strength: Float = 1f) {
        drops += Drop(x, y, 0f, strength)
    }

    fun call(x: Float, y: Float, t: Float) {
        target = Offset(x, y)
        targetSet = t
        drop(x, y, 1.4f)
    }

    fun step(dt: Float, t: Float) {
        if (t > next) {
            drop(r.nextFloat(), r.nextFloat(), 0.4f + r.nextFloat() * 0.5f)
            next = t + 0.8f + r.nextFloat() * 2.5f
        }
        drops.forEach { it.age += dt }
        drops.removeAll { it.age > 6f }
        if (t - targetSet > 12f) target = null
        koi.forEach { it.step(dt, t, target) }
    }
}

@Composable
fun KoiPondSaver(ctx: SaverContext) {
    val pond = remember { Pond() }
    val grain = rememberGrainBrush()
    val causticA = rememberNoiseBrush(256, 6, 4, seed = 91, contrast = 2.6f)
    val causticB = rememberNoiseBrush(256, 5, 4, seed = 92, contrast = 2.6f)
    val pads = remember { val r = Random(31); List(6) { floatArrayOf(r.nextFloat(), 0.1f + r.nextFloat() * 0.8f, 0.06f + r.nextFloat() * 0.05f, r.nextFloat() * 360f, r.nextFloat()) } }
    val petals = remember { val r = Random(33); List(6) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat() * 360f, r.nextFloat()) } }
    val cooldown = remember { Cooldown(5_000) }
    val canvasSize = remember { floatArrayOf(1f, 1f) }
    val time = rememberFrameTime { dt, t -> pond.step(dt, t) }
    OnSaverTap { at -> if (cooldown.tryAcquire()) pond.call(at.x / canvasSize[0], at.y / canvasSize[1], time.value) }
    val body = remember { Path() }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        canvasSize[0] = w
        canvasSize[1] = h
        val unit = size.minDimension
        drawRect(Brush.verticalGradient(listOf(Color(0xFF041B22), Color(0xFF07303A), Color(0xFF04202A))))
        // Rippling light on the pond floor.
        drawRect(causticA.place(unit * 0.7f, unit * 0.7f, t * unit * 0.01f, t * unit * 0.004f), colorFilter = ColorFilter.tint(Color(0xFF7FD8DC), BlendMode.SrcIn), alpha = 0.06f, blendMode = BlendMode.Plus)
        drawRect(causticB.place(unit * 0.55f, unit * 0.55f, -t * unit * 0.008f, t * unit * 0.009f), colorFilter = ColorFilter.tint(Color(0xFF7FD8DC), BlendMode.SrcIn), alpha = 0.05f, blendMode = BlendMode.Plus)
        // Koi, deepest first: deeper fish are dimmer and bluer.
        pond.koi.sortedBy { it.depth }.forEach { k -> drawKoi(body, k, w, h, unit, t) }
        // Moonlight on the surface.
        val moon = Offset(w * 0.66f, h * 0.24f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFBFD8EA).copy(alpha = 0.16f), Color.Transparent), moon, unit * 0.45f), unit * 0.45f, moon)
        for (k in 0 until 7) {
            val y = moon.y + (k - 3) * unit * 0.012f
            val len = unit * (0.05f - abs(k - 3) * 0.01f) * (0.7f + 0.3f * sin(t * 1.3f + k))
            drawLine(Color(0xFFEAF4FA).copy(alpha = 0.35f), Offset(moon.x - len + sin(t * 0.7f + k) * unit * 0.008f, y), Offset(moon.x + len, y), strokeWidth = density * 1.4f, cap = StrokeCap.Round)
        }
        // Rain rings, with a bright crest and a shadowed trough.
        pond.drops.forEach { d ->
            val o = Offset(d.x * w, d.y * h)
            for (k in 0 until 3) {
                val rad = (d.age * 0.06f - k * 0.022f) * unit
                if (rad <= 0f) continue
                val a = (1f - d.age / 6f).coerceIn(0f, 1f).pow(1.5f) * (1f - k * 0.3f) * d.strength
                drawOval(Color(0xFFCFE6F2).copy(alpha = 0.28f * a), Offset(o.x - rad, o.y - rad * 0.55f), Size(rad * 2, rad * 1.1f), style = Stroke(density * 1.1f))
                val r2 = rad + unit * 0.005f
                drawOval(Color.Black.copy(alpha = 0.3f * a), Offset(o.x - r2, o.y - r2 * 0.55f), Size(r2 * 2, r2 * 1.1f), style = Stroke(density * 1.1f))
            }
        }
        // Lily pads, casting soft shadows on the water below.
        pads.forEachIndexed { i, p ->
            val bob = sin(t * 0.4f + p[4] * TAU) * unit * 0.002f
            val o = Offset(p[0] * w, p[1] * h + bob)
            val rx = p[2] * unit * 1.5f
            withTransform({ scale(1f, 0.62f, o) }) {
                drawCircle(Color.Black.copy(alpha = 0.3f), rx * 1.02f, o + Offset(rx * 0.12f, rx * 0.2f))
                drawArc(Brush.radialGradient(listOf(Color(0xFF2E7A4A), Color(0xFF1B5434), Color(0xFF103A24)), o - Offset(rx * 0.2f, rx * 0.2f), rx * 1.2f), p[3] + 7f, 346f, true, o - Offset(rx, rx), Size(rx * 2, rx * 2))
                drawArc(Color(0xFF6FBF84).copy(alpha = 0.35f), p[3] + 7f, 346f, false, o - Offset(rx, rx), Size(rx * 2, rx * 2), style = Stroke(density * 1.2f))
                for (v in 0 until 9) {
                    val a = (p[3] + 34f + v * 36f) * PI.toFloat() / 180f
                    drawLine(Color(0xFF4F9C68).copy(alpha = 0.3f), o, o + Offset(cos(a), sin(a)) * rx * 0.88f, strokeWidth = density * 0.7f)
                }
            }
            if (i == 2) drawLotus(o - Offset(0f, rx * 0.1f), rx * 0.55f)
            if (i == 4) {
                // A closed bud on a short stem.
                drawOval(Brush.verticalGradient(listOf(Color(0xFFFFD6E4), Color(0xFFE8709A)), o.y - rx * 0.6f, o.y), Offset(o.x + rx * 0.4f, o.y - rx * 0.6f), Size(rx * 0.24f, rx * 0.5f))
            }
        }
        // Fallen petals drifting.
        petals.forEach { p ->
            val o = Offset(((p[0] + t * 0.003f * (0.5f + p[3])) % 1.1f) * w, (p[1] + sin(t * 0.1f + p[3] * 9f) * 0.01f) * h)
            rotate(p[2] + t * 4f * (p[3] - 0.5f), o) {
                drawOval(Color(0xFFF7B5C8).copy(alpha = 0.8f), o - Offset(unit * 0.012f, unit * 0.007f), Size(unit * 0.024f, unit * 0.014f))
            }
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), center, size.maxDimension * 0.75f))
        drawGrain(grain)
    }
}

/** A koi built along a flexing spine, so its whole body bends as it swims. */
private fun DrawScope.drawKoi(path: Path, k: KoiFish, w: Float, h: Float, unit: Float, t: Float) {
    val n = 12
    val len = k.length * unit
    val seg = len / n
    val xs = FloatArray(n + 1)
    val ys = FloatArray(n + 1)
    var a = k.heading + PI.toFloat()
    xs[0] = k.x * w
    ys[0] = k.y * h
    for (i in 1..n) {
        // The tail swings more than the head; turning bends the body.
        a += k.turn * 0.12f + sin(t * 4f - i * 0.55f) * 0.09f * (i / n.toFloat())
        xs[i] = xs[i - 1] + cos(a) * seg
        ys[i] = ys[i - 1] + sin(a) * seg
    }
    fun width(i: Int) = len * 0.13f * sin(PI.toFloat() * ((i + 1.2f) / (n + 2.4f))).pow(0.7f) * if (i < 2) 0.9f else 1f
    path.reset()
    for (i in 0..n) {
        val dx = if (i < n) xs[i + 1] - xs[i] else xs[i] - xs[i - 1]
        val dy = if (i < n) ys[i + 1] - ys[i] else ys[i] - ys[i - 1]
        val l = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        val px = -dy / l * width(i)
        val py = dx / l * width(i)
        if (i == 0) path.moveTo(xs[i] + px, ys[i] + py) else path.lineTo(xs[i] + px, ys[i] + py)
    }
    for (i in n downTo 0) {
        val dx = if (i < n) xs[i + 1] - xs[i] else xs[i] - xs[i - 1]
        val dy = if (i < n) ys[i + 1] - ys[i] else ys[i] - ys[i - 1]
        val l = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.01f)
        path.lineTo(xs[i] + dy / l * width(i), ys[i] - dx / l * width(i))
    }
    path.close()
    val fade = 0.45f + 0.5f * k.depth
    val water = Color(0xFF07303A)
    val shadowOff = Offset(len * 0.05f, len * 0.09f) * (1.2f - k.depth)
    translate(shadowOff.x, shadowOff.y) { drawPath(path, Color.Black.copy(alpha = 0.22f * fade)) }
    // Tail fin, fanning and swishing.
    val tail = Offset(xs[n], ys[n])
    val back = kotlin.math.atan2(ys[n] - ys[n - 1], xs[n] - xs[n - 1])
    val swish = sin(t * 4f - n * 0.55f) * 0.5f
    val fin = Path().apply {
        moveTo(tail.x, tail.y)
        val l = len * 0.28f
        lineTo(tail.x + cos(back + 0.55f + swish) * l, tail.y + sin(back + 0.55f + swish) * l)
        quadraticTo(tail.x + cos(back + swish) * l * 0.6f, tail.y + sin(back + swish) * l * 0.6f, tail.x + cos(back - 0.55f + swish) * l, tail.y + sin(back - 0.55f + swish) * l)
        close()
    }
    drawPath(fin, lerp(k.base, water, 0.35f).copy(alpha = 0.55f * fade))
    // Pectoral fins just behind the head.
    val side = k.heading + PI.toFloat() / 2
    for (s in listOf(-1f, 1f)) {
        val fo = Offset(xs[2] + cos(side) * s * width(2), ys[2] + sin(side) * s * width(2))
        rotate((k.heading + s * 1.1f + sin(t * 3f) * 0.2f) * 180f / PI.toFloat(), fo) {
            drawOval(lerp(k.base, water, 0.3f).copy(alpha = 0.45f * fade), fo - Offset(0f, len * 0.04f), Size(len * 0.16f, len * 0.08f))
        }
    }
    drawPath(path, lerp(k.base, water, 0.3f * (1f - k.depth)).copy(alpha = fade))
    clipPath(path) {
        k.patches.forEach { p ->
            val i = (p[0] * n).toInt().coerceIn(0, n)
            val c = if (p[3] == 2f && k.patch2 != null) k.patch2 else k.patch
            drawCircle(lerp(c, water, 0.25f * (1f - k.depth)).copy(alpha = 0.9f * fade), len * 0.1f * p[2], Offset(xs[i] + p[1] * width(i), ys[i]))
        }
        // A soft sheen down the back.
        drawPath(path, Color.White.copy(alpha = 0.08f * fade), style = Stroke(len * 0.03f))
    }
    // Eyes.
    for (s in listOf(-1f, 1f)) {
        drawCircle(Color.Black.copy(alpha = 0.6f * fade), len * 0.012f, Offset(xs[0] + cos(k.heading + PI.toFloat()) * len * 0.02f + cos(side) * s * width(1) * 0.6f, ys[0] + sin(k.heading + PI.toFloat()) * len * 0.02f + sin(side) * s * width(1) * 0.6f))
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
