package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.Meteors
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.TAU
import dev.pridgey.beddery.savers.VerticalStrip
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.layer
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.pineLine
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.rememberNoiseBrush
import dev.pridgey.beddery.savers.smoothstep
import dev.pridgey.beddery.savers.ridge
import dev.pridgey.beddery.savers.ridgePath
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

// ---------------------------------------------------------------- Moonlit Ocean (refined)

@Composable
fun OceanRefinedSaver(ctx: SaverContext) {
    val stars = remember { StarSky(if (ctx.preview) 160 else 340, 41, 0f, 0.6f) }
    val meteors = remember { Meteors(3) }
    val clouds = rememberNoiseBrush(256, 3, 6, seed = 12, contrast = 2.3f)
    val maria = rememberNoiseBrush(128, 3, 5, seed = 4, contrast = 1.7f)
    val grain = rememberGrainBrush()
    val cooldown = remember { Cooldown(15_000) }
    val time = rememberFrameTime { dt, t -> meteors.step(dt, t) }
    val wave = remember { Path() }
    OnSaverTap { if (cooldown.tryAcquire()) meteors.launch() }

    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val landscape = w > h
        val horizon = h * if (landscape) 0.58f else 0.6f
        val moon = Offset(w * if (landscape) 0.74f else 0.68f, h * if (landscape) 0.2f else 0.2f)
        val mr = size.minDimension * 0.058f

        // Sky, brightening towards the horizon and around the moon.
        drawRect(
            Brush.verticalGradient(0f to Color(0xFF02040B), 0.5f to Color(0xFF061128), 0.86f to Color(0xFF10244A), 1f to Color(0xFF22406C), startY = 0f, endY = horizon),
            size = Size(w, horizon),
        )
        drawCircle(Brush.radialGradient(listOf(Color(0xFF34558C).copy(alpha = 0.5f), Color.Transparent), moon, size.maxDimension * 0.6f), size.maxDimension * 0.6f, moon)
        stars.draw(this, t, 0.9f, horizonFade = 0.4f)
        meteors.draw(this, maxY = horizon / h)

        // The moon: wide halo, tight corona, a softly shaded disc with its seas.
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2D6).copy(alpha = 0.16f), Color.Transparent), moon, mr * 8f), mr * 8f, moon)
        drawCircle(
            Brush.radialGradient(0f to Color(0xFFFFF6E2).copy(alpha = 0.6f), 0.42f to Color(0xFFFFEFD0).copy(alpha = 0.1f), 1f to Color.Transparent, center = moon, radius = mr * 2.8f),
            mr * 2.8f, moon,
        )
        layer(Rect(moon, mr * 1.1f)) {
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFFFFFDF5), Color(0xFFF4EAD3), Color(0xFFD8CBAE)), moon - Offset(mr * 0.3f, mr * 0.3f), mr * 1.35f),
                mr, moon,
            )
            drawRect(
                maria.place(mr * 2.4f, mr * 2.4f, moon.x - mr * 1.2f, moon.y - mr * 1.2f),
                topLeft = moon - Offset(mr, mr), size = Size(mr * 2, mr * 2),
                colorFilter = ColorFilter.tint(Color(0xFF7D735F), BlendMode.SrcIn), alpha = 0.34f, blendMode = BlendMode.SrcAtop,
            )
        }

        // Thin clouds drifting past the moon, lit from behind.
        layer(Rect(0f, 0f, w, horizon)) {
            val drift = t * w * 0.0045f
            drawRect(
                clouds.place(w * 1.4f, h * 0.17f, -drift, moon.y - h * 0.05f), size = Size(w, horizon),
                colorFilter = ColorFilter.tint(Color(0xFFC3CFE6), BlendMode.SrcIn), alpha = 0.6f,
            )
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent, startY = moon.y - h * 0.13f, endY = moon.y + h * 0.15f),
                blendMode = BlendMode.DstIn,
            )
            drawRect(Brush.radialGradient(listOf(Color.Black, Color.Black.copy(alpha = 0.18f)), moon, w * 0.75f), blendMode = BlendMode.DstIn)
        }

        // Water.
        drawRect(
            Brush.verticalGradient(0f to Color(0xFF0E2041), 0.25f to Color(0xFF071430), 1f to Color(0xFF01050C), startY = horizon, endY = h),
            topLeft = Offset(0f, horizon), size = Size(w, h - horizon),
        )
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF4F6C9C).copy(alpha = 0.4f), Color.Transparent), horizon - h * 0.025f, horizon + h * 0.012f),
            topLeft = Offset(0f, horizon - h * 0.025f), size = Size(w, h * 0.037f),
        )
        // Swell lines, packed tightly near the horizon for perspective.
        val rows = if (ctx.preview) 24 else 44
        for (k in 1..rows) {
            val depth = (k / rows.toFloat()).pow(1.8f)
            val y = horizon + (h - horizon) * depth
            val amp = h * (0.0012f + depth * 0.007f)
            val len = w * (0.07f + depth * 0.28f)
            val step = (6f + depth * 12f) * density / 2.6f
            wave.reset()
            var x = 0f
            wave.moveTo(0f, y)
            while (x <= w + step) {
                val yy = y + amp * sin(x / len * TAU + t * (0.45f + depth * 0.35f) + k * 1.7f) + amp * 0.5f * sin(x / (len * 0.43f) * TAU - t * 0.6f + k)
                wave.lineTo(x, yy)
                x += step
            }
            drawPath(wave, Color(0xFF3B5B90).copy(alpha = 0.09f + depth * 0.08f), style = Stroke(width = (0.6f + depth * 1.5f) * density))
        }
        // Moon glitter: hundreds of tiny sparkles, densest straight below the moon.
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFFFE6B5).copy(alpha = 0.08f), Color.Transparent), moon.x - mr * 5, moon.x + mr * 5),
            topLeft = Offset(moon.x - mr * 5, horizon), size = Size(mr * 10, h - horizon), blendMode = BlendMode.Plus,
        )
        val glitterRows = if (ctx.preview) 40 else 96
        for (k in 0 until glitterRows) {
            val depth = ((k + 0.5f) / glitterRows).pow(1.6f)
            val y = horizon + 2f + (h - horizon) * depth
            val spread = mr * (0.6f + depth * 4.6f)
            val n = 3 + (depth * 6).toInt()
            for (j in 0 until n) {
                val off = hash(k, j) * 2 - 1
                val centered = off * abs(off)
                val x = moon.x + centered * spread + sin(t * 0.6f + k * 0.9f + j) * spread * 0.07f
                val tw = sin(t * (1.1f + hash(k * 7 + j, 3) * 3f) + hash(j, k) * TAU)
                val spark = (tw * tw * tw).coerceAtLeast(0f) * 0.75f + 0.12f * (0.5f + 0.5f * tw)
                val a = spark * (1f - abs(centered)) * (1f - depth * 0.4f)
                if (a < 0.03f) continue
                val half = mr * (0.07f + depth * 0.55f) * (0.5f + hash(k + j * 11, 9))
                drawLine(
                    Color(0xFFFFF1D2).copy(alpha = a.coerceAtMost(1f)), Offset(x - half, y), Offset(x + half, y),
                    strokeWidth = (0.7f + depth * 2f) * density, cap = StrokeCap.Round, blendMode = BlendMode.Plus,
                )
            }
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), center, size.maxDimension * 0.75f))
        drawGrain(grain)
    }
}

// ---------------------------------------------------------------- Aurora (refined)

private class Curtain(val base: Float, val height: Float, val strength: Float, val seed: Int, val strip: VerticalStrip, val glow: VerticalStrip)

private fun curtainStrip(low: Color, mid: Color, top: Color) = VerticalStrip(
    listOf(Color.Transparent, top.copy(alpha = 0.22f), mid.copy(alpha = 0.55f), low, Color.Transparent),
    listOf(0f, 0.32f, 0.7f, 0.94f, 1f),
)

private fun glowStrip(c: Color) = VerticalStrip(listOf(Color.Transparent, c.copy(alpha = 0.5f), Color.Transparent), listOf(0f, 0.75f, 1f))

private class Scenery(val far: Path, val mid: Path, val trees: Path)

@Composable
fun AuroraRefinedSaver(ctx: SaverContext) {
    val stars = remember { StarSky(if (ctx.preview) 150 else 320, 7, 0f, 0.62f) }
    val meteors = remember { Meteors(11) }
    val grain = rememberGrainBrush()
    val curtains = remember {
        listOf(
            Curtain(0.33f, 0.24f, 1f, 1, curtainStrip(Color(0xFFA6FFD0), Color(0xFF2FE39A), Color(0xFF6E45FF)), glowStrip(Color(0xFF2FE39A))),
            Curtain(0.40f, 0.18f, 0.7f, 2, curtainStrip(Color(0xFFB7FFE0), Color(0xFF29D3A0), Color(0xFF3F7BFF)), glowStrip(Color(0xFF29D3A0))),
            Curtain(0.25f, 0.28f, 0.42f, 3, curtainStrip(Color(0xFFD7B6FF), Color(0xFF9A5CFF), Color(0xFFFF5CAE)), glowStrip(Color(0xFF9A5CFF))),
        )
    }
    val scenery = remember {
        SizeCache { s ->
            val lake = s.height * lakeLevel(s)
            Scenery(
                far = ridgePath(ridge(129, 21, 0.58f), s.width, lake - s.height * 0.035f, s.height * 0.11f, lake + 2f),
                mid = ridgePath(ridge(129, 34, 0.5f), s.width, lake - s.height * 0.006f, s.height * 0.05f, lake + 2f),
                trees = pineLine(s.width, lake, s.height * 0.012f, s.height * 0.034f, s.width * 0.018f, 9, lake + 2f),
            )
        }
    }
    val ripples = remember { List(34) { floatArrayOf(hash(it, 1), hash(it, 2), 0.04f + hash(it, 3) * 0.16f, hash(it, 4)) } }
    val cooldown = remember { Cooldown(30_000) }
    val surge = remember { floatArrayOf(-100f) } // time the last surge started
    val time = rememberFrameTime { dt, t -> meteors.step(dt, t) }
    OnSaverTap {
        if (cooldown.tryAcquire()) {
            surge[0] = time.value
            meteors.launch()
        }
    }

    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val lake = h * lakeLevel(size)
        val since = t - surge[0]
        val boost = 1f + if (since in 0f..14f) 0.9f * sin(since / 14f * Math.PI.toFloat()).coerceAtLeast(0f) else 0f
        val sc = scenery.get(size)
        val step = (if (ctx.preview) 6f else 2.6f) * density

        fun DrawScope.sky(reflection: Boolean) {
            drawRect(
                Brush.verticalGradient(0f to Color(0xFF01030A), 0.55f to Color(0xFF03101F), 1f to Color(0xFF092733), startY = 0f, endY = lake),
                size = Size(w, lake),
            )
            if (!reflection) stars.draw(this, t, 0.85f, horizonFade = 0.45f)
            drawCurtains(curtains, t, if (reflection) step * 2.5f else step, boost)
            // Airglow along the horizon, lit by the aurora above.
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF1FBF8A).copy(alpha = 0.09f * boost)), lake - h * 0.18f, lake),
                topLeft = Offset(0f, lake - h * 0.18f), size = Size(w, h * 0.18f), blendMode = BlendMode.Plus,
            )
            drawPath(sc.far, Brush.verticalGradient(listOf(Color(0xFF0E2733), Color(0xFF081720)), lake - h * 0.15f, lake))
            drawPath(sc.mid, Color(0xFF050E15))
            drawPath(sc.trees, Color(0xFF010407))
        }

        sky(reflection = false)
        meteors.draw(this, maxY = lake / h)

        // Still lake: a darkened mirror image with a few slow ripples.
        clipRect(0f, lake, w, h) {
            withTransform({ scale(1f, -1f, Offset(0f, lake)) }) {
                clipRect(0f, lake - (h - lake), w, lake) { sky(reflection = true) }
            }
            drawRect(
                Brush.verticalGradient(0f to Color(0xFF010810).copy(alpha = 0.45f), 1f to Color(0xFF000305).copy(alpha = 0.92f), startY = lake, endY = h),
                topLeft = Offset(0f, lake), size = Size(w, h - lake),
            )
            ripples.forEach { r ->
                val y = lake + (h - lake) * r[1].pow(1.5f)
                val len = w * r[2] * (0.4f + (y - lake) / (h - lake))
                val x = ((r[0] + t * 0.004f * (0.5f + r[3])) % 1.2f - 0.1f) * w
                val a = 0.06f + 0.06f * sin(t * 0.5f + r[3] * TAU)
                drawLine(Color(0xFF8FE8D0).copy(alpha = a), Offset(x, y), Offset(x + len, y), strokeWidth = density * 0.8f, cap = StrokeCap.Round)
            }
            drawLine(Color(0xFF6FD6C0).copy(alpha = 0.08f), Offset(0f, lake + 1f), Offset(w, lake + 1f), strokeWidth = density)
        }
        drawGrain(grain)
    }
}

private fun lakeLevel(s: Size) = if (s.width > s.height) 0.72f else 0.68f

private fun DrawScope.drawCurtains(curtains: List<Curtain>, t: Float, step: Float, boost: Float) {
    val w = size.width
    val h = size.height
    val rayScale = 7f * density
    curtains.forEach { c ->
        fun baseAt(nx: Float) = h * (
            c.base + 0.085f * sin(nx * 1.7f + t * 0.04f + c.seed * 1.3f) +
                0.045f * sin(nx * 4.6f - t * 0.055f + c.seed) +
                0.025f * noise1(nx * 7f + t * 0.05f, c.seed * 7)
            )
        var x = -step
        var i = 0
        var prev = baseAt(x / w)
        while (x <= w + step) {
            val nx = x / w
            val base = baseAt(nx)
            // Curtains look brightest where they fold and we see them edge-on (where they're steep).
            val slope = abs(base - prev) / step
            prev = base
            val fold = 0.3f + 0.7f * smoothstep(0f, 1.1f, slope) * (0.6f + 0.4f * sin(nx * 2.3f - t * 0.05f + c.seed))
            val rays = 0.45f + 0.55f * noise1(x / rayScale + t * 0.3f * boost, c.seed * 13 + 1)
            val shimmer = 0.85f + 0.15f * noise1(nx * 22f + t * 1.4f * boost, c.seed)
            val intensity = (c.strength * fold * rays * shimmer * boost).coerceIn(0f, 1f)
            val height = h * c.height * (0.6f + 0.7f * (0.5f + 0.5f * noise1(nx * 3f + t * 0.04f, c.seed * 3 + 2)))
            val top = base - height
            if (intensity > 0.02f) {
                drawLine(c.strip.place(top, height), Offset(x, top), Offset(x, base), strokeWidth = step * 1.2f, alpha = intensity, blendMode = BlendMode.Plus)
                if (i % 4 == 0) {
                    drawLine(c.glow.place(top - height * 0.2f, height * 1.3f), Offset(x, top - height * 0.2f), Offset(x, base + height * 0.08f), strokeWidth = step * 7f, alpha = intensity * 0.1f, blendMode = BlendMode.Plus)
                }
            }
            x += step
            i++
        }
    }
}
