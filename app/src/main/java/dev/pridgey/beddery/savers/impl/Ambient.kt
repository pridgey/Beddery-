package dev.pridgey.beddery.savers.impl

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.rememberFrameTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ---------------------------------------------------------------- Starfield

private class Starfield(val n: Int) {
    private val r = Random(1)
    val x = FloatArray(n) { r.nextFloat() * 2 - 1 }
    val y = FloatArray(n) { r.nextFloat() * 2 - 1 }
    val z = FloatArray(n) { 0.05f + r.nextFloat() * 0.95f }
    val tint = FloatArray(n) { r.nextFloat() }
    var speed = 0.2f

    fun step(dt: Float, t: Float) {
        // Gentle surges, like dropping in and out of warp.
        speed = 0.12f + 0.1f * (0.5f + 0.5f * sin(t * 0.11f)) + 0.25f * sin(t * 0.04f).coerceAtLeast(0f).let { it * it * it }
        for (i in 0 until n) {
            z[i] -= speed * dt
            if (z[i] <= 0.02f) {
                x[i] = r.nextFloat() * 2 - 1
                y[i] = r.nextFloat() * 2 - 1
                z[i] = 1f
            }
        }
    }
}

@Composable
fun StarfieldSaver(ctx: SaverContext) {
    val field = remember { Starfield(if (ctx.preview) 140 else 420) }
    val time = rememberFrameTime { dt, t -> field.step(dt, t) }
    Canvas(Modifier.fillMaxSize()) {
        time.value
        drawRect(Color.Black)
        val c = center
        val f = size.maxDimension * 0.5f
        val streak = field.speed * 0.12f
        for (i in 0 until field.n) {
            val z = field.z[i]
            val sx = c.x + field.x[i] / z * f
            val sy = c.y + field.y[i] / z * f
            if (sx < -50 || sx > size.width + 50 || sy < -50 || sy > size.height + 50) continue
            val z0 = z + streak
            val px = c.x + field.x[i] / z0 * f
            val py = c.y + field.y[i] / z0 * f
            val bright = (1f - z).coerceIn(0f, 1f)
            val color = lerp(Color(0xFFAFC8FF), Color(0xFFFFF1D6), field.tint[i]).copy(alpha = bright)
            drawLine(color, Offset(px, py), Offset(sx, sy), strokeWidth = (1f + bright * 3.5f) * density / 2f, cap = StrokeCap.Round)
        }
    }
}

// ---------------------------------------------------------------- Orbits

private data class Planet(val orbit: Float, val period: Float, val size: Float, val color: Color, val phase: Float, val ring: Boolean = false)

private val Planets = listOf(
    Planet(0.14f, 9f, 0.010f, Color(0xFFB8B2A7), 0.3f),
    Planet(0.21f, 16f, 0.016f, Color(0xFFE8C48A), 2.1f),
    Planet(0.29f, 24f, 0.017f, Color(0xFF6FA8FF), 4.0f),
    Planet(0.37f, 38f, 0.013f, Color(0xFFE5714A), 1.2f),
    Planet(0.50f, 80f, 0.034f, Color(0xFFD9B38C), 5.1f),
    Planet(0.63f, 130f, 0.028f, Color(0xFFE8D5A0), 3.3f, ring = true),
    Planet(0.75f, 210f, 0.021f, Color(0xFF9FE3E8), 0.8f),
    Planet(0.86f, 320f, 0.020f, Color(0xFF5C7CFF), 2.6f),
)

@Composable
fun OrbitsSaver(@Suppress("UNUSED_PARAMETER") ctx: SaverContext) {
    val stars = remember { val r = Random(9); List(150) { floatArrayOf(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    val time = rememberFrameTime()
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value + 400f
        drawRect(Color(0xFF02030A))
        stars.forEach { s -> drawCircle(Color.White.copy(alpha = 0.15f + 0.4f * s[2]), 0.6f * density * (0.5f + s[2]), Offset(s[0] * size.width, s[1] * size.height)) }
        val c = center
        val scale = minOf(size.width, size.height / 0.55f) * 0.5f
        val tilt = 0.42f
        fun pos(p: Planet): Pair<Offset, Float> {
            val a = t * 2 * PI.toFloat() / p.period + p.phase
            return Offset(c.x + cos(a) * p.orbit * scale, c.y + sin(a) * p.orbit * scale * tilt) to sin(a)
        }
        Planets.forEach { p ->
            val rx = p.orbit * scale
            drawOval(Color.White.copy(alpha = 0.07f), Offset(c.x - rx, c.y - rx * tilt), Size(rx * 2, rx * 2 * tilt), style = Stroke(1f * density / 2))
        }
        // Back half first, then the sun, then the front half, for a 3D feel.
        fun drawPlanets(front: Boolean) {
            Planets.forEach { p ->
                val (o, depth) = pos(p)
                if ((depth >= 0) != front) return@forEach
                val r = p.size * scale * (0.85f + 0.15f * depth) * 1.6f
                // Light each planet from the side facing the sun.
                val dx = c.x - o.x
                val dy = c.y - o.y
                val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val lit = Offset(o.x + dx / d * r * 0.5f, o.y + dy / d * r * 0.5f)
                drawCircle(Brush.radialGradient(listOf(p.color, lerp(p.color, Color.Black, 0.8f)), lit, r * 1.5f), r, o)
                if (p.ring) drawOval(p.color.copy(alpha = 0.6f), Offset(o.x - r * 2f, o.y - r * 0.5f), Size(r * 4f, r), style = Stroke(r * 0.18f))
            }
        }
        drawPlanets(front = false)
        val sunR = scale * 0.06f
        drawCircle(Brush.radialGradient(listOf(Color(0x66FFB347), Color.Transparent), c, sunR * 5), sunR * 5, c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4C2), Color(0xFFFFB347)), c, sunR), sunR, c)
        drawPlanets(front = true)
    }
}

// ---------------------------------------------------------------- Constellation

private class Points(val n: Int) {
    private val r = Random(21)
    val x = FloatArray(n) { r.nextFloat() }
    val y = FloatArray(n) { r.nextFloat() }
    val vx = FloatArray(n) { (r.nextFloat() - 0.5f) * 0.03f }
    val vy = FloatArray(n) { (r.nextFloat() - 0.5f) * 0.03f }

    fun step(dt: Float) {
        for (i in 0 until n) {
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            if (x[i] < 0f || x[i] > 1f) vx[i] = -vx[i]
            if (y[i] < 0f || y[i] > 1f) vy[i] = -vy[i]
        }
    }
}

@Composable
fun ConstellationSaver(ctx: SaverContext) {
    val points = remember { Points(if (ctx.preview) 34 else 80) }
    val time = rememberFrameTime { dt, _ -> points.step(dt) }
    val accent = ctx.accent
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        drawRect(Brush.radialGradient(listOf(Color(0xFF0B1022), Color.Black), center, size.maxDimension * 0.7f))
        val w = size.width
        val h = size.height
        val maxD = size.minDimension * 0.22f
        val maxD2 = maxD * maxD
        for (i in 0 until points.n) {
            val ax = points.x[i] * w
            val ay = points.y[i] * h
            for (j in i + 1 until points.n) {
                val dx = points.x[j] * w - ax
                val dy = points.y[j] * h - ay
                val d2 = dx * dx + dy * dy
                if (d2 < maxD2) {
                    val a = (1f - sqrt(d2) / maxD) * 0.55f
                    drawLine(accent.copy(alpha = a), Offset(ax, ay), Offset(ax + dx, ay + dy), strokeWidth = density * 0.7f)
                }
            }
        }
        for (i in 0 until points.n) {
            val o = Offset(points.x[i] * w, points.y[i] * h)
            val tw = 0.6f + 0.4f * sin(t * 1.5f + i)
            drawCircle(Color.White.copy(alpha = tw), 1.8f * density, o)
            drawCircle(accent.copy(alpha = 0.15f * tw), 6f * density, o, blendMode = BlendMode.Plus)
        }
    }
}

// ---------------------------------------------------------------- Matrix

private const val GLYPHS = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789Z:.=*+-<>"

private class Rain {
    val r = Random(2)
    var cols = 0
    var rows = 0
    var head = FloatArray(0)
    var speed = FloatArray(0)
    var length = IntArray(0)
    var glyphs = CharArray(0)

    fun ensure(c: Int, rw: Int) {
        if (c == cols && rw == rows) return
        cols = c; rows = rw
        head = FloatArray(c) { -r.nextFloat() * rw }
        speed = FloatArray(c) { 6f + r.nextFloat() * 14f }
        length = IntArray(c) { 8 + r.nextInt(24) }
        glyphs = CharArray(c * rw) { GLYPHS[r.nextInt(GLYPHS.length)] }
    }

    fun step(dt: Float) {
        for (i in 0 until cols) {
            head[i] += speed[i] * dt
            if (head[i] - length[i] > rows) {
                head[i] = -r.nextFloat() * rows * 0.5f
                speed[i] = 6f + r.nextFloat() * 14f
                length[i] = 8 + r.nextInt(24)
            }
        }
        repeat((glyphs.size / 60).coerceAtLeast(1)) {
            if (glyphs.isNotEmpty()) glyphs[r.nextInt(glyphs.size)] = GLYPHS[r.nextInt(GLYPHS.length)]
        }
    }
}

@Composable
fun MatrixSaver(@Suppress("UNUSED_PARAMETER") ctx: SaverContext) {
    val rain = remember { Rain() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER } }
    val time = rememberFrameTime { dt, _ -> rain.step(dt) }
    val green = Color(0xFF3CFF8F)
    Canvas(Modifier.fillMaxSize()) {
        time.value
        drawRect(Color.Black)
        val cell = 16.dp.toPx()
        rain.ensure((size.width / cell).toInt() + 1, (size.height / cell).toInt() + 1)
        paint.textSize = cell * 0.9f
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            for (c in 0 until rain.cols) {
                val headRow = rain.head[c].toInt()
                val len = rain.length[c]
                for (k in 0 until len) {
                    val row = headRow - k
                    if (row < 0 || row >= rain.rows) continue
                    val fade = 1f - k / len.toFloat()
                    paint.color = if (k == 0) Color(0xFFE8FFEF).toArgb() else green.copy(alpha = fade * 0.85f).toArgb()
                    nc.drawText(rain.glyphs[c * rain.rows + row].toString(), c * cell + cell / 2, (row + 1) * cell, paint)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Game of Life

private class Life {
    val r = Random(System.nanoTime())
    var cols = 0
    var rows = 0
    var age = IntArray(0)
    private var next = IntArray(0)
    private var acc = 0f
    private var generation = 0
    private val history = ArrayDeque<Int>()

    fun ensure(c: Int, rw: Int) {
        if (c == cols && rw == rows) return
        cols = c; rows = rw
        age = IntArray(c * rw)
        next = IntArray(c * rw)
        seed()
    }

    private fun seed() {
        for (i in age.indices) age[i] = if (r.nextFloat() < 0.28f) 1 else 0
        generation = 0
        history.clear()
    }

    fun step(dt: Float) {
        if (cols == 0) return
        acc += dt
        if (acc < 0.14f) return
        acc = 0f
        var alive = 0
        var hash = 0
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = (x + dx + cols) % cols
                    val yy = (y + dy + rows) % rows
                    if (age[yy * cols + xx] > 0) n++
                }
                val i = y * cols + x
                val a = age[i]
                next[i] = if (a > 0 && (n == 2 || n == 3)) a + 1 else if (a == 0 && n == 3) 1 else 0
                if (next[i] > 0) {
                    alive++
                    hash = hash * 31 + i
                }
            }
        }
        val tmp = age; age = next; next = tmp
        generation++
        // Restart when the world dies out, stagnates or loops.
        if (alive < cols * rows / 60 || generation > 1500 || hash in history) seed()
        else {
            history.addLast(hash)
            if (history.size > 12) history.removeFirst()
        }
    }
}

@Composable
fun GameOfLifeSaver(ctx: SaverContext) {
    val life = remember { Life() }
    val time = rememberFrameTime { dt, _ -> life.step(dt) }
    val young = ctx.accent
    val old = Color(0xFF2A3F6B)
    Canvas(Modifier.fillMaxSize()) {
        time.value
        drawRect(Color.Black)
        val cell = (if (ctx.preview) 14.dp else 11.dp).toPx()
        life.ensure((size.width / cell).toInt(), (size.height / cell).toInt())
        val ox = (size.width - life.cols * cell) / 2
        val oy = (size.height - life.rows * cell) / 2
        val gap = cell * 0.12f
        for (y in 0 until life.rows) {
            for (x in 0 until life.cols) {
                val a = life.age[y * life.cols + x]
                if (a == 0) continue
                val color = lerp(young, old, (a / 30f).coerceAtMost(1f))
                drawRoundRectCell(color, Offset(ox + x * cell + gap, oy + y * cell + gap), cell - gap * 2)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRoundRectCell(color: Color, topLeft: Offset, side: Float) {
    drawRoundRect(color, topLeft, Size(side, side), androidx.compose.ui.geometry.CornerRadius(side * 0.25f))
}
