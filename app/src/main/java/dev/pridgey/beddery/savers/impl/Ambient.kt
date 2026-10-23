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
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.rememberFrameTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

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

    /** Easter egg: launches a glider from the tapped cell, heading away from the center. */
    fun glider(cx: Int, cy: Int) {
        if (cols == 0) return
        val sx = if (cx < cols / 2) -1 else 1
        val sy = if (cy < rows / 2) -1 else 1
        val shape = listOf(1 to 0, 2 to 1, 0 to 2, 1 to 2, 2 to 2)
        for (y in -2..4) for (x in -2..4) set(cx + x * sx, cy + y * sy, 0)
        shape.forEach { (x, y) -> set(cx + (x - 1) * sx, cy + (y - 1) * sy, 1) }
    }

    private fun set(x: Int, y: Int, v: Int) {
        val xx = (x % cols + cols) % cols
        val yy = (y % rows + rows) % rows
        age[yy * cols + xx] = v
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
    val layout = remember { floatArrayOf(0f, 0f, 1f) } // offset x, offset y, cell size
    OnSaverTap { at -> life.glider(((at.x - layout[0]) / layout[2]).toInt(), ((at.y - layout[1]) / layout[2]).toInt()) }
    val young = ctx.accent
    val old = Color(0xFF2A3F6B)
    Canvas(Modifier.fillMaxSize()) {
        time.value
        drawRect(Color.Black)
        val cell = (if (ctx.preview) 14.dp else 11.dp).toPx()
        life.ensure((size.width / cell).toInt(), (size.height / cell).toInt())
        val ox = (size.width - life.cols * cell) / 2
        val oy = (size.height - life.rows * cell) / 2
        layout[0] = ox; layout[1] = oy; layout[2] = cell
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
