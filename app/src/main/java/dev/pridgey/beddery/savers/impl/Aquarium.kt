package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.SoftDot
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.rememberNoiseBrush
import dev.pridgey.beddery.savers.ridge
import dev.pridgey.beddery.savers.stamp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The tank's layout: water, sand, rocks and where plants grow. */
private class TankLayout(size: Size) {
    val w = size.width
    val h = size.height
    val landscape = w > h
    val surface = h * 0.07f
    val sand: Path
    val sandTop: FloatArray = ridge(48, 5, 0.4f).map { h * (if (landscape) 0.8f else 0.78f) - it * h * 0.06f }.toFloatArray()
    val rocks: List<Pair<Path, Rect>>

    init {
        sand = Path().apply {
            moveTo(0f, h)
            sandTop.forEachIndexed { i, y -> lineTo(w * i / (sandTop.size - 1f), y) }
            lineTo(w, h)
            close()
        }
        val r = Random(12)
        rocks = listOf(0.2f to 0.13f, 0.32f to 0.08f, 0.74f to 0.11f).map { (fx, fr) ->
            val cx = w * fx
            val base = sandAt(cx) + h * 0.02f
            val rad = size.minDimension * fr * if (landscape) 0.8f else 1f
            val path = Path().apply {
                val n = 11
                moveTo(cx - rad * 1.1f, base)
                for (k in 1 until n) {
                    val f = k / n.toFloat()
                    val bump = 0.8f + 0.35f * r.nextFloat()
                    lineTo(cx - rad * 1.1f + rad * 2.2f * f, base - rad * bump * sin(f * Math.PI.toFloat()).let { sqrt(it) })
                }
                lineTo(cx + rad * 1.1f, base)
                close()
            }
            path to Rect(cx - rad * 1.1f, base - rad * 1.2f, cx + rad * 1.1f, base)
        }
    }

    fun sandAt(x: Float): Float {
        val f = (x / w).coerceIn(0f, 1f) * (sandTop.size - 1)
        val i = f.toInt().coerceAtMost(sandTop.size - 2)
        return sandTop[i] + (sandTop[i + 1] - sandTop[i]) * (f - i)
    }
}

private class Fish(var x: Float, var y: Float, var vx: Float, var vy: Float, var facing: Float, val seed: Int)

/** Everyone in the tank, and how they move. Positions are in pixels. */
private class Tank {
    val tetras = List(14) { Fish(0f, 0f, 0f, 0f, 1f, it) }
    val angels = List(2) { Fish(0f, 0f, 0f, 0f, 1f, 100 + it) }
    val cories = List(3) { Fish(0f, 0f, 0f, 0f, 1f, 200 + it) }
    var placed = false
    var w = 0f
    var h = 0f
    var startle = 0f

    fun place(layout: TankLayout) {
        if (placed && w == layout.w && h == layout.h) return
        w = layout.w
        h = layout.h
        val r = Random(3)
        tetras.forEach { it.x = w * (0.3f + 0.4f * r.nextFloat()); it.y = h * (0.35f + 0.2f * r.nextFloat()) }
        angels.forEachIndexed { i, f -> f.x = w * (0.3f + 0.4f * i); f.y = h * (0.42f + 0.1f * i); f.facing = if (i == 0) 1f else -1f }
        cories.forEachIndexed { i, f -> f.x = w * (0.35f + 0.2f * i); f.y = layout.sandAt(f.x) + h * 0.006f }
        placed = true
    }

    fun step(dt: Float, t: Float, layout: TankLayout, sleep: Float) {
        val unit = minOf(w, h)
        val pace = 1f - 0.8f * sleep
        startle = max(0f, startle - dt)
        // The school follows a slow wandering point; at night it drifts low among the plants.
        val target = Offset(
            w * (0.5f + 0.32f * sin(t * 0.07f) + 0.08f * sin(t * 0.19f)),
            h * (0.36f + 0.12f * sin(t * 0.11f + 1f)) * (1f - sleep) + h * 0.6f * sleep,
        )
        for (f in tetras) {
            val slot = Offset((hash(f.seed * 3) - 0.5f) * unit * 0.32f, (hash(f.seed * 7) - 0.5f) * unit * 0.16f)
            val wobble = Offset(noise1(t * 0.3f + f.seed, f.seed) * unit * 0.06f, noise1(t * 0.25f + f.seed * 2, f.seed + 9) * unit * 0.04f)
            val goal = target + slot * (1f + sleep * 1.5f) + wobble
            var ax = (goal.x - f.x) * 0.6f
            var ay = (goal.y - f.y) * 0.6f
            for (o in tetras) {
                if (o === f) continue
                val dx = f.x - o.x
                val dy = f.y - o.y
                val d2 = dx * dx + dy * dy
                val min = unit * 0.035f
                if (d2 < min * min && d2 > 0.01f) {
                    val d = sqrt(d2)
                    ax += dx / d * unit * 1.2f
                    ay += dy / d * unit * 1.2f
                }
            }
            if (startle > 0f) {
                ax += (f.x - w / 2).sign * unit * 4f
                ay += (hash(f.seed) - 0.5f) * unit * 3f
            }
            move(f, ax, ay, dt, unit * (0.16f * pace + if (startle > 0f) 0.4f else 0f), damping = 1.6f)
        }
        for ((i, f) in angels.withIndex()) {
            val goal = Offset(
                w * (0.5f + 0.38f * sin(t * 0.035f + i * 2.7f)),
                h * (0.4f + 0.14f * sin(t * 0.05f + i * 1.3f)) + h * 0.12f * sleep,
            )
            move(f, (goal.x - f.x) * 0.25f, (goal.y - f.y) * 0.25f, dt, unit * 0.06f * pace, damping = 1.2f)
        }
        for ((i, f) in cories.withIndex()) {
            // Little hops along the bottom, then a rest.
            val phase = (t * 0.15f + i * 0.37f) % 1f
            val active = phase < 0.35f && sleep < 0.5f
            val dir = if (noise1(t * 0.05f + i * 5f, i + 40) > 0f) 1f else -1f
            val gx = if (active) f.x + dir * unit * 0.2f else f.x
            move(f, (gx - f.x) * 1.2f, 0f, dt, unit * 0.05f, damping = 3f)
            f.x = f.x.coerceIn(w * 0.08f, w * 0.92f)
            f.y = layout.sandAt(f.x) + unit * 0.004f
        }
    }

    private fun move(f: Fish, ax: Float, ay: Float, dt: Float, maxSpeed: Float, damping: Float) {
        f.vx += (ax - f.vx * damping) * dt
        f.vy += (ay - f.vy * damping) * dt
        val sp = sqrt(f.vx * f.vx + f.vy * f.vy)
        if (sp > maxSpeed) {
            f.vx *= maxSpeed / sp
            f.vy *= maxSpeed / sp
        }
        f.x += f.vx * dt
        f.y += f.vy * dt
        f.x = f.x.coerceIn(w * 0.03f, w * 0.97f)
        f.y = f.y.coerceIn(h * 0.12f, h * 0.86f)
        // Turning round takes a moment: the fish narrows as it turns, like it would in 3D.
        if (abs(f.vx) > maxSpeed * 0.08f) {
            val want = f.vx.sign
            f.facing += (want - f.facing).coerceIn(-dt * 2.5f, dt * 2.5f)
        }
    }
}

/** A planted tank of tropical fish. Late at night the light turns moonlit blue and they settle down. */
@Composable
fun AquariumSaver(ctx: SaverContext) {
    val grain = rememberGrainBrush()
    val caustics = rememberNoiseBrush(256, 6, 3, seed = 51, contrast = 2.6f)
    val layout = remember { SizeCache { TankLayout(it) } }
    val tank = remember { Tank() }
    val hour = ctx.now.hour
    val lateNight = !ctx.demo && (hour >= 23 || hour < 6 || ctx.settings.isNight(ctx.now.toLocalTime()))
    val night by animateFloatAsState(if (lateNight) 1f else 0f, tween(60_000), label = "tankNight")
    val sizeRef = remember { floatArrayOf(0f, 0f) }
    val time = rememberFrameTime { dt, t ->
        if (sizeRef[0] > 0f) {
            val l = layout.get(Size(sizeRef[0], sizeRef[1]))
            tank.place(l)
            tank.step(dt, t, l, night)
        }
    }
    val cooldown = remember { Cooldown(15_000) }
    OnSaverTap { if (cooldown.tryAcquire()) tank.startle = 1.2f }
    val plants = remember { Path() }

    Canvas(Modifier.fillMaxSize()) {
        sizeRef[0] = size.width
        sizeRef[1] = size.height
        val t = time.value
        val l = layout.get(size)
        tank.place(l)
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        val n = night

        // Water, lit from above: warm white by day, cool moonlight blue late at night.
        val top = lerp(Color(0xFF1E5A6E), Color(0xFF0A1E3A), n)
        val mid = lerp(Color(0xFF0E3A4C), Color(0xFF061428), n)
        val deep = lerp(Color(0xFF07202C), Color(0xFF030A16), n)
        drawRect(Brush.verticalGradient(listOf(top, mid, deep), l.surface, h))
        // Light shafts from the lamp.
        for (k in 0 until 5) {
            val x = w * (0.12f + k * 0.2f) + sin(t * 0.15f + k) * w * 0.03f
            val shaft = Path().apply {
                moveTo(x - w * 0.03f, l.surface)
                lineTo(x + w * 0.03f, l.surface)
                lineTo(x + w * 0.14f, h * 0.85f)
                lineTo(x + w * 0.02f, h * 0.85f)
                close()
            }
            val a = (0.05f + 0.03f * sin(t * 0.4f + k * 2f)) * (1f - 0.7f * n)
            drawPath(shaft, Brush.verticalGradient(listOf(lerp(Color(0xFFE8F6FF), Color(0xFF8AB0FF), n).copy(alpha = a), Color.Transparent), l.surface, h * 0.85f), blendMode = BlendMode.Plus)
        }

        // Background plants: tall ribbons swaying in the current.
        drawRibbons(l, t, n, back = true)

        // Sand and caustics.
        val sandTop = l.sandTop.min()
        drawPath(l.sand, Brush.verticalGradient(listOf(lerp(Color(0xFF9A8262), Color(0xFF34425A), n), lerp(Color(0xFF4A3A28), Color(0xFF121A28), n), lerp(Color(0xFF1E160E), Color(0xFF080C14), n)), sandTop, h))
        clipPath(l.sand) {
            // Rippling light on the sand, fading toward the front of the tank.
            drawRect(caustics.place(unit * 0.2f, unit * 0.12f, dx = t * 5f, dy = sin(t * 0.2f) * 6f), Offset(0f, sandTop), Size(w, (h - sandTop) * 0.6f), alpha = 0.1f * (1f - 0.8f * n), colorFilter = ColorFilter.tint(Color(0xFFFFF4D8), BlendMode.Modulate), blendMode = BlendMode.Plus)
            // Pebbles.
            for (k in 0 until 60) {
                val x = hash(k * 3) * w
                val y = l.sandAt(x) + (h - l.sandAt(x)) * hash(k * 7) * 0.9f + unit * 0.01f
                val r = unit * (0.004f + 0.008f * hash(k * 11))
                drawOval(lerp(Color(0xFF7A6A58), Color(0xFF26303E), n).copy(alpha = 0.8f), Offset(x - r, y - r * 0.6f), Size(r * 2, r * 1.2f))
            }
        }

        // Rocks.
        for ((path, b) in l.rocks) {
            drawPath(path, Brush.radialGradient(listOf(lerp(Color(0xFF7A7E80), Color(0xFF2A3446), n), lerp(Color(0xFF2E3234), Color(0xFF0C1018), n)), Offset(b.left + b.width * 0.35f, b.top + b.height * 0.25f), b.width * 0.8f))
            clipPath(path) {
                drawRect(caustics.place(unit * 0.25f, unit * 0.25f, dx = t * 4f), b.topLeft, b.size, alpha = 0.12f * (1f - 0.8f * n), colorFilter = ColorFilter.tint(Color.White, BlendMode.Modulate), blendMode = BlendMode.Plus)
                // Moss on top.
                drawRect(Brush.verticalGradient(listOf(lerp(Color(0xFF4A7A3A), Color(0xFF1A2A2A), n).copy(alpha = 0.7f), Color.Transparent), b.top, b.top + b.height * 0.4f), b.topLeft, b.size)
            }
        }

        // Fish, back to front: angelfish are a little further back.
        for (f in tank.angels) drawAngel(f, unit, t, n)
        for (f in tank.cories) drawCory(f, unit, t, n)
        for (f in tank.tetras) drawTetra(f, unit, t, n)

        // Front plants: broad sword leaves and a carpet.
        drawSwords(l, t, n, plants)
        drawRibbons(l, t, n, back = false)

        // Bubbles from an airstone at the back.
        val stone = Offset(w * 0.86f, l.sandAt(w * 0.86f))
        for (k in 0 until 18) {
            val life = 3.2f + hash(k * 3) * 1.5f
            val u = ((t + hash(k * 7) * life) % life) / life
            val y = stone.y - (stone.y - l.surface) * u
            val x = stone.x + sin(u * 14f + k) * unit * 0.008f * (0.3f + u)
            val r = unit * (0.004f + 0.005f * hash(k * 5)) * (0.8f + 0.5f * u)
            drawCircle(Color.White.copy(alpha = 0.25f), r, Offset(x, y), style = Stroke(max(1f, r * 0.25f)))
            drawCircle(Color.White.copy(alpha = 0.5f), r * 0.3f, Offset(x - r * 0.35f, y - r * 0.35f))
        }

        // The surface and the lid light.
        drawRect(Brush.verticalGradient(listOf(lerp(Color(0xFFBFE4F0), Color(0xFF6A88C8), n).copy(alpha = 0.35f), Color.Transparent), l.surface, l.surface + h * 0.025f), Offset(0f, l.surface), Size(w, h * 0.025f))
        for (k in 0 until 24) {
            val x = w * k / 24f + sin(t * 0.8f + k) * w * 0.01f
            drawLine(Color.White.copy(alpha = 0.12f + 0.08f * sin(t * 1.3f + k * 1.7f)), Offset(x, l.surface + 1f), Offset(x + w * 0.03f, l.surface + 1f), strokeWidth = density)
        }
        drawRect(Brush.verticalGradient(listOf(Color(0xFF050608), Color(0xFF14181C)), 0f, l.surface), size = Size(w, l.surface))
        drawRect(lerp(Color(0xFFEFF8FF), Color(0xFF6A8AD8), n).copy(alpha = 0.5f), Offset(w * 0.05f, l.surface - h * 0.006f), Size(w * 0.9f, h * 0.004f))
        // Glass edges.
        drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent), 0f, w * 0.04f), Offset(0f, l.surface), Size(w * 0.04f, h))
        drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)), w * 0.94f, w), Offset(w * 0.94f, l.surface), Size(w * 0.06f, h))
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), Offset(w / 2, h * 0.5f), size.maxDimension * 0.75f))
        drawGrain(grain, 0.05f)
    }
}

private fun DrawScope.drawRibbons(l: TankLayout, t: Float, n: Float, back: Boolean) {
    val count = if (back) 30 else 12
    val pts = ArrayList<Offset>(12)
    for (k in 0 until count) {
        val seed = if (back) k else 500 + k
        val x = l.w * (if (back) (0.02f + 0.96f * hash(seed * 3)) else (0.01f + 0.18f * hash(seed * 3) + if (k % 2 == 0) 0f else 0.79f))
        val base = Offset(x, l.sandAt(x) + l.h * 0.015f)
        val len = l.h * (if (back) 0.44f else 0.24f) * (0.55f + 0.45f * hash(seed * 7))
        val shade = hash(seed * 13)
        val color = lerp(
            lerp(if (back) Color(0xFF2A6236) else Color(0xFF4E9E48), if (back) Color(0xFF3E7A34) else Color(0xFF6AB050), shade),
            Color(0xFF0E2A2E), n * 0.8f,
        )
        pts.clear()
        val segs = 10
        for (i in 0..segs) {
            val f = i / segs.toFloat()
            val sway = sin(t * 0.6f + seed + f * 2.2f) * l.w * 0.025f * f * f + noise1(t * 0.2f + seed, seed) * l.w * 0.015f * f
            pts += Offset(base.x + sway, base.y - len * f)
        }
        // A blade: widest a little above the base, tapering to a fine tip.
        val wide = l.w * (if (back) 0.007f else 0.011f)
        val path = Path()
        for (i in pts.indices) {
            val f = i / segs.toFloat()
            val half = wide * (1f - f).let { it * (0.6f + 0.4f * (1f - it)) } + 0.6f
            val p = pts[i] + Offset(half, 0f)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        for (i in pts.indices.reversed()) {
            val f = i / segs.toFloat()
            val half = wide * (1f - f).let { it * (0.6f + 0.4f * (1f - it)) } + 0.6f
            val p = pts[i] - Offset(half, 0f)
            path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, color.copy(alpha = if (back) 0.8f else 1f))
        // A faint midrib catching the light.
        if (!back) drawLine(Color.White.copy(alpha = 0.08f * (1f - n)), pts[0], pts[segs * 2 / 3], strokeWidth = density * 0.8f)
    }
}

private fun DrawScope.drawSwords(l: TankLayout, t: Float, n: Float, leaf: Path) {
    for ((ci, cx) in listOf(0.58f, 0.12f).withIndex()) {
        val base = Offset(l.w * cx, l.sandAt(l.w * cx) + l.h * 0.02f)
        val leaves = 9
        for (k in 0 until leaves) {
            val a = -70f + k * (140f / (leaves - 1)) + sin(t * 0.5f + k + ci) * 4f
            val rad = Math.toRadians(a.toDouble()).toFloat()
            val len = l.h * (0.14f + 0.08f * hash(k * 5 + ci * 31)) * (1f - abs(a) / 220f)
            val tip = base + Offset(sin(rad) * len, -cos(rad) * len)
            val mid = base + (tip - base) * 0.5f
            val nrm = Offset(cos(rad), sin(rad)) * len * 0.16f
            leaf.reset()
            leaf.moveTo(base.x, base.y)
            leaf.quadraticTo(mid.x + nrm.x, mid.y + nrm.y, tip.x, tip.y)
            leaf.quadraticTo(mid.x - nrm.x, mid.y - nrm.y, base.x, base.y)
            leaf.close()
            val light = lerp(Color(0xFF6ABF5A), Color(0xFF1E3A3E), n * 0.85f)
            val dark = lerp(Color(0xFF235A2A), Color(0xFF0A1A20), n * 0.85f)
            drawPath(leaf, Brush.linearGradient(listOf(dark, light), base, tip))
            drawLine(dark.copy(alpha = 0.7f), base, tip, strokeWidth = density)
        }
    }
}

private fun DrawScope.fishTransform(f: Fish, block: DrawScope.() -> Unit) {
    val angle = Math.toDegrees(atan2(f.vy, abs(f.vx) + 1e-3f).toDouble()).toFloat().coerceIn(-25f, 25f)
    withTransform({
        translate(f.x, f.y)
        rotate(angle * f.facing.sign, Offset.Zero)
        scale(f.facing.let { if (abs(it) < 0.12f) 0.12f * it.sign.let { s -> if (s == 0f) 1f else s } else it }, 1f, Offset.Zero)
    }) { block() }
}

private fun DrawScope.drawTetra(f: Fish, unit: Float, t: Float, n: Float) = fishTransform(f) {
    val len = unit * 0.05f
    val wag = sin(t * 14f + f.seed) * 0.25f
    val body = Path().apply {
        moveTo(len * 0.5f, 0f)
        cubicTo(len * 0.4f, -len * 0.16f, -len * 0.15f, -len * 0.17f, -len * 0.34f, -len * 0.04f)
        lineTo(-len * 0.34f, len * 0.04f)
        cubicTo(-len * 0.15f, len * 0.17f, len * 0.4f, len * 0.16f, len * 0.5f, 0f)
        close()
    }
    val tail = Path().apply {
        moveTo(-len * 0.32f, 0f)
        lineTo(-len * 0.55f, -len * (0.13f - wag * 0.1f))
        lineTo(-len * 0.5f, len * wag * 0.05f)
        lineTo(-len * 0.55f, len * (0.13f + wag * 0.1f))
        close()
    }
    val glow = 1f - 0.5f * n
    drawPath(tail, Color(0xFFD8E4EC).copy(alpha = 0.35f))
    drawPath(body, Brush.verticalGradient(listOf(Color(0xFF9AA6B0), Color(0xFFDDE4EA)), -len * 0.16f, len * 0.16f), alpha = 0.92f)
    clipPath(body) {
        // The red belly toward the tail, and the electric blue stripe.
        drawRect(Color(0xFFE0303A).copy(alpha = 0.9f), Offset(-len * 0.4f, 0f), Size(len * 0.55f, len * 0.2f))
        drawRect(Brush.horizontalGradient(listOf(Color(0xFF2AA8FF), Color(0xFF48F0FF)), -len * 0.35f, len * 0.4f), Offset(-len * 0.38f, -len * 0.045f), Size(len * 0.8f, len * 0.05f), alpha = glow)
    }
    drawCircle(Color.Black, len * 0.035f, Offset(len * 0.36f, -len * 0.02f))
    if (n < 0.95f) stamp(SoftDot, Offset(0f, -len * 0.02f), len * 0.9f, len * 0.25f, alpha = 0.14f * glow, colorFilter = ColorFilter.tint(Color(0xFF48E0FF), BlendMode.Modulate), blendMode = BlendMode.Plus)
}

private fun DrawScope.drawAngel(f: Fish, unit: Float, t: Float, n: Float) = fishTransform(f) {
    val len = unit * 0.11f
    val wag = sin(t * 3f + f.seed) * 0.08f
    val dim = 1f - 0.35f * n
    // Long, swept-back fins above and below.
    val dorsal = Path().apply {
        moveTo(len * 0.1f, -len * 0.3f)
        quadraticTo(-len * 0.1f, -len * 0.75f, -len * 0.5f, -len * (0.95f + wag))
        quadraticTo(-len * 0.3f, -len * 0.5f, -len * 0.3f, -len * 0.12f)
        close()
    }
    val anal = Path().apply {
        moveTo(len * 0.05f, len * 0.3f)
        quadraticTo(-len * 0.12f, len * 0.75f, -len * 0.48f, len * (0.95f - wag))
        quadraticTo(-len * 0.3f, len * 0.5f, -len * 0.3f, len * 0.12f)
        close()
    }
    val finColor = Color(0xFFD8DCD8).copy(alpha = 0.55f * dim)
    drawPath(dorsal, finColor)
    drawPath(anal, finColor)
    // Tail fan.
    val tail = Path().apply {
        moveTo(-len * 0.3f, 0f)
        lineTo(-len * 0.58f, -len * (0.22f + wag))
        quadraticTo(-len * 0.5f, 0f, -len * 0.58f, len * (0.22f - wag))
        close()
    }
    drawPath(tail, Color(0xFFE8ECE8).copy(alpha = 0.4f * dim))
    // The tall, flat body.
    val body = Path().apply {
        moveTo(len * 0.42f, -len * 0.02f)
        cubicTo(len * 0.3f, -len * 0.38f, -len * 0.18f, -len * 0.4f, -len * 0.32f, -len * 0.06f)
        lineTo(-len * 0.32f, len * 0.06f)
        cubicTo(-len * 0.18f, len * 0.4f, len * 0.3f, len * 0.38f, len * 0.42f, len * 0.02f)
        close()
    }
    drawPath(body, Brush.linearGradient(listOf(Color(0xFFF4F2EA), Color(0xFFB8B8B0)), Offset(0f, -len * 0.4f), Offset(0f, len * 0.4f)), alpha = dim)
    clipPath(body) {
        for (k in 0..2) {
            val x = len * (0.2f - k * 0.22f)
            drawRect(Color(0xFF2A2A2A).copy(alpha = 0.75f * dim), Offset(x - len * 0.035f, -len * 0.5f), Size(len * 0.07f, len))
        }
    }
    drawLine(Color(0xFF2A2A2A).copy(alpha = 0.7f * dim), Offset(-len * 0.0f, -len * 0.38f), Offset(-len * 0.38f, -len * 0.85f), strokeWidth = len * 0.04f)
    drawLine(Color(0xFF2A2A2A).copy(alpha = 0.7f * dim), Offset(-len * 0.0f, len * 0.38f), Offset(-len * 0.36f, len * 0.85f), strokeWidth = len * 0.04f)
    // Trailing feelers.
    drawLine(Color(0xFFE8E8E0).copy(alpha = 0.4f * dim), Offset(len * 0.15f, len * 0.25f), Offset(-len * 0.05f, len * (0.85f + wag)), strokeWidth = density)
    drawCircle(Color(0xFFB03020), len * 0.045f, Offset(len * 0.28f, -len * 0.06f))
    drawCircle(Color.Black, len * 0.025f, Offset(len * 0.28f, -len * 0.06f))
}

private fun DrawScope.drawCory(f: Fish, unit: Float, t: Float, n: Float) = fishTransform(f) {
    val len = unit * 0.05f
    val body = Path().apply {
        moveTo(len * 0.5f, len * 0.05f)
        cubicTo(len * 0.45f, -len * 0.28f, -len * 0.25f, -len * 0.22f, -len * 0.38f, -len * 0.02f)
        lineTo(-len * 0.38f, len * 0.06f)
        cubicTo(-len * 0.2f, len * 0.16f, len * 0.4f, len * 0.18f, len * 0.5f, len * 0.05f)
        close()
    }
    val c = lerp(Color(0xFFC8B08A), Color(0xFF3A4252), n)
    drawPath(Path().apply { moveTo(-len * 0.35f, 0f); lineTo(-len * 0.55f, -len * 0.14f); lineTo(-len * 0.55f, len * 0.12f); close() }, c.copy(alpha = 0.6f))
    drawPath(body, Brush.verticalGradient(listOf(c, lerp(c, Color.White, 0.3f)), -len * 0.25f, len * 0.15f))
    clipPath(body) {
        for (k in 0 until 7) drawCircle(Color(0xFF3A2E22).copy(alpha = 0.5f), len * 0.035f, Offset(len * (0.25f - k * 0.09f), -len * (0.05f + 0.06f * (k % 2))))
    }
    drawLine(c, Offset(len * 0.05f, -len * 0.18f), Offset(-len * 0.08f, -len * 0.36f), strokeWidth = len * 0.06f, cap = StrokeCap.Round)
    drawCircle(Color.Black, len * 0.04f, Offset(len * 0.32f, -len * 0.06f))
    drawLine(c, Offset(len * 0.48f, len * 0.08f), Offset(len * 0.56f, len * 0.16f), strokeWidth = density)
}
