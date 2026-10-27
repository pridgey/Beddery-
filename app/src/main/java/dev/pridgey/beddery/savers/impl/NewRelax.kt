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
