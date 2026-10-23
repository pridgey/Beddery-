package dev.pridgey.beddery.savers.impl

import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.R
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.rememberFrameTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2 * PI).toFloat()

/** A field of softly twinkling stars, positions in 0..1 space. */
private class Stars(count: Int, seed: Int, maxY: Float = 1f) {
    private val r = Random(seed)
    val x = FloatArray(count) { r.nextFloat() }
    val y = FloatArray(count) { r.nextFloat() * maxY }
    val size = FloatArray(count) { 0.4f + r.nextFloat() * r.nextFloat() * 1.6f }
    val phase = FloatArray(count) { r.nextFloat() * TAU }

    fun draw(scope: DrawScope, t: Float, alpha: Float = 1f) = with(scope) {
        val unit = size.minDimension / 400f
        for (i in x.indices) {
            val a = (0.35f + 0.65f * (0.5f + 0.5f * sin(t * 1.3f + phase[i]))) * alpha
            drawCircle(Color.White.copy(alpha = a.coerceIn(0f, 1f)), this@Stars.size[i] * unit, Offset(x[i] * this.size.width, y[i] * this.size.height))
        }
    }
}

// ---------------------------------------------------------------- Breathe

private enum class BreathPhase(val label: Int) {
    IN(R.string.breathe_in), HOLD_IN(R.string.breathe_hold), OUT(R.string.breathe_out), HOLD_OUT(R.string.breathe_hold)
}

@Composable
fun BreatheSaver(ctx: SaverContext) {
    val pattern = ctx.settings.breathPattern
    val durations = floatArrayOf(pattern.inhale.toFloat(), pattern.holdIn.toFloat(), pattern.exhale.toFloat(), pattern.holdOut.toFloat())
    val cycle = durations.sum()
    val time = rememberFrameTime()
    // Phase + progress derived from the frame clock; the label only recomposes when the phase changes.
    val phase by remember(pattern) {
        derivedStateOf {
            var p = time.value % cycle
            var i = 0
            while (i < 3 && p >= durations[i]) {
                p -= durations[i]; i++
            }
            BreathPhase.entries[i]
        }
    }
    val c1 = ctx.accent
    val c2 = Color(0xFF7FE3B8)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val label = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.06f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            var p = t % cycle
            var i = 0
            while (i < 3 && p >= durations[i]) {
                p -= durations[i]; i++
            }
            val f = if (durations[i] > 0f) p / durations[i] else 1f
            val ease = (1 - cos(f * PI.toFloat())) / 2f
            val scale = when (i) {
                0 -> 0.4f + 0.6f * ease
                1 -> 1f
                2 -> 1f - 0.6f * ease
                else -> 0.4f
            }
            val maxR = size.minDimension * 0.36f
            val r = maxR * scale
            drawCircle(Brush.radialGradient(listOf(c1.copy(alpha = 0.25f), Color.Transparent), center, r * 1.8f), r * 1.8f, center)
            drawCircle(Brush.radialGradient(listOf(c2.copy(alpha = 0.55f), c1.copy(alpha = 0.35f), c1.copy(alpha = 0.05f)), center, r), r, center)
            drawCircle(Color.White.copy(alpha = 0.18f), maxR, center, style = Stroke(width = 1.5f))
            drawCircle(Color.White.copy(alpha = 0.10f), maxR * 0.4f, center, style = Stroke(width = 1.5f))
            // Six orbiting petals for a little life.
            for (k in 0 until 6) {
                val a = t * 0.15f + k * TAU / 6
                val o = Offset(center.x + cos(a) * r * 0.55f, center.y + sin(a) * r * 0.55f)
                drawCircle(Color.White.copy(alpha = 0.08f), r * 0.45f, o, blendMode = BlendMode.Plus)
            }
        }
        AnimatedContent(phase, transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) }, label = "phase") { ph ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(ph.label), color = Color.White.copy(alpha = 0.9f), fontSize = label, fontWeight = FontWeight.Light)
            }
        }
    }
}

// ---------------------------------------------------------------- Ocean

@Composable
fun OceanSaver(@Suppress("UNUSED_PARAMETER") ctx: SaverContext) {
    val stars = remember { Stars(140, 7, maxY = 0.55f) }
    val time = rememberFrameTime()
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val horizon = h * 0.58f
        drawRect(Brush.verticalGradient(listOf(Color(0xFF040817), Color(0xFF0D1838), Color(0xFF233A6B)), 0f, horizon))
        stars.draw(this, t, 0.8f)
        val moon = Offset(w * 0.72f, h * 0.2f)
        val mr = size.minDimension * 0.07f
        drawCircle(Brush.radialGradient(listOf(Color(0x55FFF4D6), Color.Transparent), moon, mr * 5), mr * 5, moon)
        drawCircle(Color(0xFFFFF6E0), mr, moon)
        drawCircle(Color(0x22000000), mr * 0.25f, moon + Offset(-mr * 0.3f, -mr * 0.2f))
        drawCircle(Color(0x18000000), mr * 0.18f, moon + Offset(mr * 0.35f, mr * 0.3f))

        drawRect(Brush.verticalGradient(listOf(Color(0xFF15264D), Color(0xFF020611)), horizon, h), Offset(0f, horizon))
        // Shimmering moon reflection.
        var y = horizon + 4f
        var row = 0
        while (y < h) {
            val depth = (y - horizon) / (h - horizon)
            val half = mr * (0.6f + depth * 2.2f) * (0.6f + 0.4f * sin(t * 2.3f + row * 1.7f))
            val a = (0.55f * (1 - depth)) * (0.5f + 0.5f * sin(t * 3.1f + row * 2.9f))
            drawLine(Color(0xFFFFF0C8).copy(alpha = a.coerceIn(0f, 1f)), Offset(moon.x - half, y), Offset(moon.x + half, y), strokeWidth = 2f + depth * 4f, cap = StrokeCap.Round)
            y += 6f + depth * 14f
            row++
        }
        // Layered swells, back to front.
        val layers = 5
        for (l in 0 until layers) {
            val depth = l / (layers - 1f)
            val base = horizon + (h - horizon) * (0.12f + depth * 0.8f)
            val amp = h * (0.006f + depth * 0.02f)
            val len = w * (0.25f + depth * 0.4f)
            val speed = 0.25f + depth * 0.35f
            val path = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w + 8f) {
                    val yy = base + amp * sin(x / len * TAU + t * speed + l) + amp * 0.4f * sin(x / (len * 0.37f) * TAU - t * speed * 1.4f)
                    lineTo(x, yy)
                    x += 8f
                }
                lineTo(w, h)
                close()
            }
            val shade = Color(0xFF0A1630).copy(alpha = 0.45f + depth * 0.4f)
            drawPath(path, Brush.verticalGradient(listOf(shade, Color(0xFF01030A)), base - amp, h))
        }
    }
}

// ---------------------------------------------------------------- Aurora

private val AuroraColors = listOf(Color(0xFF3DFFB0), Color(0xFF39D0FF), Color(0xFFA36BFF))

@Composable
fun AuroraSaver(ctx: SaverContext) {
    val stars = remember { Stars(160, 3, maxY = 0.8f) }
    val time = rememberFrameTime()
    val step = if (ctx.preview) 14f else 5f
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF01030B), Color(0xFF05122A), Color(0xFF0B1E33))))
        stars.draw(this, t, 0.7f)
        AuroraColors.forEachIndexed { i, color ->
            val ph = i * 1.9f
            val baseY = h * (0.22f + i * 0.07f)
            var x = 0f
            while (x <= w) {
                val nx = x / w
                val top = baseY + h * 0.08f * sin(nx * 5f + t * 0.22f + ph) + h * 0.04f * sin(nx * 13f - t * 0.37f + ph * 2)
                val len = h * (0.18f + 0.12f * sin(nx * 7f + t * 0.3f + ph)) * (0.7f + 0.3f * sin(nx * 31f + t * 0.9f))
                val intensity = (0.5f + 0.5f * sin(nx * 9f - t * 0.5f + ph)) * (0.6f + 0.4f * sin(nx * 23f + t * 1.1f + ph))
                drawLine(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.25f to color.copy(alpha = 0.35f * intensity),
                        0.8f to color.copy(alpha = 0.12f * intensity),
                        1f to Color.Transparent,
                        startY = top, endY = top + len,
                    ),
                    Offset(x, top), Offset(x, top + len), strokeWidth = step * 1.4f, blendMode = BlendMode.Plus,
                )
                x += step
            }
        }
        // Mountain silhouette.
        val ridge = Path().apply {
            moveTo(0f, h)
            val peaks = floatArrayOf(0.82f, 0.74f, 0.79f, 0.68f, 0.77f, 0.71f, 0.8f, 0.75f, 0.83f)
            peaks.forEachIndexed { i, p -> lineTo(w * i / (peaks.size - 1f), h * p) }
            lineTo(w, h)
            close()
        }
        drawPath(ridge, Color(0xFF02040A))
    }
}

// ---------------------------------------------------------------- Lava lamp

private class Blob(seed: Int) {
    private val r = Random(seed)
    val sx = 0.08f + r.nextFloat() * 0.12f
    val sy = 0.05f + r.nextFloat() * 0.1f
    val px = r.nextFloat() * TAU
    val py = r.nextFloat() * TAU
    val radius = 0.07f + r.nextFloat() * 0.09f
}

@Composable
fun LavaLampSaver(ctx: SaverContext) {
    val blobs = remember { List(if (ctx.preview) 6 else 9) { Blob(it * 31 + 5) } }
    val time = rememberFrameTime()
    val gooey = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1A0B2E), Color(0xFF3A0F3F), Color(0xFF13061F))))
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (gooey && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val blur = size.minDimension * 0.045f
                        // Blur, then sharpen the alpha channel: nearby blobs melt together like real wax.
                        val threshold = ColorMatrixColorFilter(
                            floatArrayOf(
                                1f, 0f, 0f, 0f, 0f,
                                0f, 1f, 0f, 0f, 0f,
                                0f, 0f, 1f, 0f, 0f,
                                0f, 0f, 0f, 24f, -24f * 110f,
                            ),
                        )
                        renderEffect = RenderEffect.createColorFilterEffect(
                            threshold,
                            RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL),
                        ).asComposeRenderEffect()
                    }
                },
        ) {
            val t = time.value
            val w = size.width
            val h = size.height
            val brush = Brush.verticalGradient(listOf(Color(0xFFFFB347), Color(0xFFFF5E7E), Color(0xFFD14BFF)))
            blobs.forEach { b ->
                val cx = w * (0.5f + 0.32f * sin(t * b.sx + b.px))
                val cy = h * (0.5f + 0.44f * sin(t * b.sy + b.py))
                val r = size.minDimension * b.radius * (1f + 0.12f * sin(t * 0.7f + b.px))
                if (gooey) drawCircle(brush, r, Offset(cx, cy))
                else drawCircle(Brush.radialGradient(listOf(Color(0xFFFF7A6B), Color(0x00FF5E7E)), Offset(cx, cy), r * 1.4f), r * 1.4f, Offset(cx, cy))
            }
            // Pools of wax at the top and bottom of the lamp.
            if (gooey) {
                drawCircle(brush, w * 0.7f, Offset(w / 2, h + w * 0.55f))
                drawCircle(brush, w * 0.5f, Offset(w / 2, -w * 0.42f))
            }
        }
    }
}

// ---------------------------------------------------------------- Fireflies

private class Swarm(val n: Int) {
    private val r = Random(11)
    val x = FloatArray(n) { r.nextFloat() }
    val y = FloatArray(n) { 0.2f + r.nextFloat() * 0.75f }
    val heading = FloatArray(n) { r.nextFloat() * TAU }
    val speed = FloatArray(n) { 0.01f + r.nextFloat() * 0.025f }
    val pulse = FloatArray(n) { r.nextFloat() * TAU }
    val rate = FloatArray(n) { 0.6f + r.nextFloat() * 1.4f }
    val turn = FloatArray(n) { 0f }

    fun step(dt: Float) {
        for (i in 0 until n) {
            turn[i] = (turn[i] + (r.nextFloat() - 0.5f) * 4f * dt).coerceIn(-1.2f, 1.2f)
            heading[i] += turn[i] * dt
            x[i] += cos(heading[i]) * speed[i] * dt
            y[i] += sin(heading[i]) * speed[i] * dt
            if (x[i] < -0.05f) x[i] = 1.05f
            if (x[i] > 1.05f) x[i] = -0.05f
            if (y[i] < 0.15f) heading[i] = PI.toFloat() / 2
            if (y[i] > 0.97f) heading[i] = -PI.toFloat() / 2
        }
    }
}

@Composable
fun FirefliesSaver(ctx: SaverContext) {
    val swarm = remember { Swarm(if (ctx.preview) 22 else 48) }
    val grass = remember {
        val r = Random(4)
        List(90) { floatArrayOf(r.nextFloat(), 0.03f + r.nextFloat() * 0.09f, (r.nextFloat() - 0.5f) * 0.03f) }
    }
    val time = rememberFrameTime { dt, _ -> swarm.step(dt) }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF020611), Color(0xFF061A1A), Color(0xFF0A2318))))
        val glow = size.minDimension * 0.05f
        for (i in 0 until swarm.n) {
            val p = sin(t * swarm.rate[i] + swarm.pulse[i])
            val a = (p * p * p).coerceAtLeast(0f) // mostly dark, bright blinks
            if (a < 0.02f) continue
            val o = Offset(swarm.x[i] * w, swarm.y[i] * h)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFD8FF6B).copy(alpha = 0.45f * a), Color.Transparent), o, glow), glow, o, blendMode = BlendMode.Plus)
            drawCircle(Color(0xFFF4FFC2).copy(alpha = a), glow * 0.08f, o)
        }
        grass.forEach { g ->
            val sway = sin(t * 0.8f + g[0] * 20f) * 0.008f
            val bx = g[0] * w
            val tip = Offset(bx + (g[2] + sway) * w, h - g[1] * h)
            drawLine(Color(0xFF010402), Offset(bx, h), tip, strokeWidth = max(2f, w * 0.006f), cap = StrokeCap.Round)
        }
    }
}
