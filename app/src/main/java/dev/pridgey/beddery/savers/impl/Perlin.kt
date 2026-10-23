package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.fbm2
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import kotlin.math.exp

// ---------------------------------------------------------------- Contours

/**
 * Topographic contour lines over a slowly shifting landscape of noise, recomputed with
 * marching squares a few times a second.
 */
private class Topo {
    var cols = 0
    var rows = 0
    private var field = FloatArray(0)
    val levels = 16
    val paths = List(levels) { Path() }
    private var last = -1f

    fun update(w: Float, h: Float, t: Float, detail: Int) {
        val c = detail
        val r = (detail * h / w).toInt().coerceAtLeast(2)
        if (c != cols || r != rows) {
            cols = c
            rows = r
            field = FloatArray((c + 1) * (r + 1))
            last = -1f
        }
        if (last >= 0f && t - last < 0.1f) return
        last = t
        val scale = 3.2f / c
        for (j in 0..rows) for (i in 0..cols) {
            field[j * (cols + 1) + i] = fbm2(i * scale + t * 0.012f, j * scale - t * 0.009f, 4, 5) + 0.25f * fbm2(i * scale * 0.4f - t * 0.004f, j * scale * 0.4f, 2, 9)
        }
        val cw = w / cols
        val ch = h / rows
        for (l in 0 until levels) {
            val iso = -0.7f + 1.4f * (l + 0.5f) / levels
            val p = paths[l]
            p.reset()
            for (j in 0 until rows) for (i in 0 until cols) {
                val a = field[j * (cols + 1) + i] - iso
                val b = field[j * (cols + 1) + i + 1] - iso
                val cc = field[(j + 1) * (cols + 1) + i + 1] - iso
                val d = field[(j + 1) * (cols + 1) + i] - iso
                val case = (if (a > 0) 1 else 0) or (if (b > 0) 2 else 0) or (if (cc > 0) 4 else 0) or (if (d > 0) 8 else 0)
                if (case == 0 || case == 15) continue
                val x0 = i * cw
                val y0 = j * ch
                // Edge crossings, interpolated: top, right, bottom, left.
                fun top() = Offset(x0 + cw * a / (a - b), y0)
                fun right() = Offset(x0 + cw, y0 + ch * b / (b - cc))
                fun bottom() = Offset(x0 + cw * d / (d - cc), y0 + ch)
                fun left() = Offset(x0, y0 + ch * a / (a - d))
                fun seg(p1: Offset, p2: Offset) {
                    p.moveTo(p1.x, p1.y)
                    p.lineTo(p2.x, p2.y)
                }
                when (case) {
                    1, 14 -> seg(left(), top())
                    2, 13 -> seg(top(), right())
                    3, 12 -> seg(left(), right())
                    4, 11 -> seg(right(), bottom())
                    5 -> { seg(left(), top()); seg(right(), bottom()) }
                    6, 9 -> seg(top(), bottom())
                    7, 8 -> seg(left(), bottom())
                    10 -> { seg(top(), right()); seg(left(), bottom()) }
                }
            }
        }
    }
}

@Composable
fun ContoursSaver(ctx: SaverContext) {
    val topo = remember { Topo() }
    val grain = rememberGrainBrush()
    val time = rememberFrameTime()
    val accent = ctx.accent
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value + 100f
        topo.update(size.width, size.height, t, if (ctx.preview) 40 else 72)
        drawRect(Brush.verticalGradient(listOf(Color(0xFF060A14), Color(0xFF0A1220))))
        topo.paths.forEachIndexed { l, p ->
            val f = l / (topo.levels - 1f)
            val major = l % 4 == 0
            val color = lerp(lerp(accent, Color(0xFF243A6A), 0.65f), lerp(accent, Color.White, 0.35f), f)
            drawPath(p, color.copy(alpha = if (major) 0.75f else 0.38f), style = Stroke(if (major) 1.6f * density else 0.9f * density, join = StrokeJoin.Round))
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), center, size.maxDimension * 0.75f))
        drawGrain(grain, 0.05f)
    }
}

// ---------------------------------------------------------------- Ridgelines

/** Stacked lines lifted by noise into mountain ridges, like a famous album cover, rolling slowly forward. */
@Composable
fun RidgelinesSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val line = remember { Path() }
    val fill = remember { Path() }
    val accent = ctx.accent
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        drawRect(Color.Black)
        val count = if (ctx.preview) 36 else 54
        val top = h * 0.2f
        val bottom = h * 0.82f
        val margin = w * 0.12f
        val span = w - margin * 2
        val gap = (bottom - top) / count
        val amp = gap * 7f
        val steps = 90
        // The ridges scroll towards you, one row every few seconds.
        val scroll = t * 0.25f
        for (k in 0 until count) {
            val y0 = top + k * gap
            val row = k - scroll
            line.reset()
            fill.reset()
            fill.moveTo(margin, y0 + gap * 3)
            for (s in 0..steps) {
                val f = s / steps.toFloat()
                val x = margin + span * f
                val env = exp(-((f - 0.5f) / 0.2f).let { it * it })
                val n = fbm2(f * 7f, row * 0.32f, 4, 21)
                val lift = (0.35f * (n + 0.6f).coerceAtLeast(0f) + 0.05f * fbm2(f * 30f, row * 0.5f, 2, 3)) * env
                val y = y0 - lift * amp * 2.2f
                if (s == 0) line.moveTo(x, y) else line.lineTo(x, y)
                fill.lineTo(x, y)
            }
            fill.lineTo(margin + span, y0 + gap * 3)
            fill.close()
            // Each ridge hides the ones behind it.
            drawPath(fill, Color.Black)
            val front = k / (count - 1f)
            drawPath(line, lerp(Color.White, accent, 0.25f).copy(alpha = 0.5f + 0.45f * front), style = Stroke(1.3f * density, join = StrokeJoin.Round))
        }
        // Fade the rows in at the back and out at the front as they scroll.
        drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent), top - gap * 8, top + gap * 2), blendMode = BlendMode.SrcOver)
    }
}
