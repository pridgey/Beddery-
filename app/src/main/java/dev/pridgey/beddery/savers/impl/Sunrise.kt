package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.R
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.WakeLight
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.rememberNoiseBrush
import dev.pridgey.beddery.savers.ridge
import dev.pridgey.beddery.savers.ridgePath
import dev.pridgey.beddery.savers.smoothstep
import kotlin.math.PI
import kotlin.math.cos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.layer
import dev.pridgey.beddery.savers.noise1
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** Sky colors through a dawn: zenith, mid sky, horizon. */
private class DawnKey(val at: Float, val zenith: Color, val mid: Color, val horizon: Color)

private val DawnKeys = listOf(
    DawnKey(0.00f, Color(0xFF010208), Color(0xFF03050F), Color(0xFF080A18)),
    DawnKey(0.22f, Color(0xFF040716), Color(0xFF0E1230), Color(0xFF2A1834)),
    DawnKey(0.45f, Color(0xFF0B1434), Color(0xFF33305E), Color(0xFF9A4636)),
    DawnKey(0.68f, Color(0xFF1E3666), Color(0xFF7A6C90), Color(0xFFE88A4E)),
    DawnKey(0.86f, Color(0xFF3C67A2), Color(0xFFB4A6B8), Color(0xFFFFC07A)),
    DawnKey(1.00f, Color(0xFF5E8FC8), Color(0xFFC9D4E2), Color(0xFFFFE2B0)),
)

private fun dawn(p: Float, pick: (DawnKey) -> Color): Color {
    val i = DawnKeys.indexOfLast { it.at <= p }.coerceIn(0, DawnKeys.size - 2)
    val a = DawnKeys[i]
    val b = DawnKeys[i + 1]
    return lerp(pick(a), pick(b), ((p - a.at) / (b.at - a.at)).coerceIn(0f, 1f))
}

private class DawnScene(size: Size) {
    val w = size.width
    val h = size.height
    val landscape = w > h
    val horizon = h * if (landscape) 0.6f else 0.56f
    val water = horizon + h * 0.028f
    /** Distant mountains along the horizon. */
    val far = ridgePath(ridge(64, 11, 0.5f), w, horizon + h * 0.012f, h * 0.05f, water + 1f)
    /** Wooded headlands either side of the lake, low in the middle so the sunrise shows through. */
    val headlands: Path = Path().apply {
        val hs = ridge(64, 23, 0.6f)
        moveTo(0f, water + 1f)
        hs.forEachIndexed { i, v ->
            val f = i / (hs.size - 1f)
            val env = (abs(2 * f - 1)).pow(1.6f)
            lineTo(w * f, water - (0.15f + 0.85f * v) * env * h * 0.07f)
        }
        lineTo(w, water + 1f)
        close()
    }
    /** The near bank, with reeds. */
    val bank: Path = ridgePath(ridge(32, 47, 0.5f), w, h * 0.975f, h * 0.035f, h + 1f)
    val reeds: List<Triple<Float, Float, Float>> = (0 until 46).map { k ->
        val side = if (k % 2 == 0) hash(k * 3) * 0.32f else 1f - hash(k * 3) * 0.3f
        Triple(w * side, h * (0.05f + 0.06f * hash(k * 7)), (hash(k * 11) - 0.5f) * 0.4f)
    }
}

/**
 * A dawn that comes up over the 30 minutes before your alarm: the sky warms from deep night
 * through violet and amber to morning blue, and the screen itself brightens with it.
 */
@Composable
fun SunriseSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val grain = rememberGrainBrush()
    val cloudTex = rememberNoiseBrush(256, 4, 5, seed = 91, contrast = 1.7f)
    val stars = remember { StarSky(220, 17, 0f, 0.7f) }
    val scene = remember { SizeCache { DawnScene(it) } }

    // Previews play the whole dawn on a loop; a real session follows the alarm.
    val demoAlarm = ctx.nextAlarm ?: ctx.now.plusMinutes(18)
    val real = WakeLight.progress(ctx.now, ctx.nextAlarm)
    WakeLight.Drive(real, enabled = !ctx.preview && !ctx.demo)
    val loop = { t: Float -> ((1 - cos((t / 40f) * PI.toFloat())) / 2f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val dens = LocalDensity.current
        val clockSize = with(dens) { (if (landscape) maxHeight * 0.24f else maxWidth * 0.24f).toSp() }
        val small = with(dens) { (min(maxWidth, maxHeight) * 0.038f).toSp() }

        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val p = if (ctx.demo) loop(t) else real
            val sc = scene.get(size)
            val w = size.width
            val h = size.height
            val horizon = sc.horizon
            val water = sc.water
            val zenith = dawn(p) { it.zenith }
            val mid = dawn(p) { it.mid }
            val low = dawn(p) { it.horizon }

            // Sky.
            drawRect(Brush.verticalGradient(0f to zenith, 0.55f to mid, 1f to low, startY = 0f, endY = horizon), size = Size(w, water + 2))
            stars.draw(this, t, alpha = 1f - smoothstep(0.2f, 0.62f, p), horizonFade = 0.5f)

            // The sun: below the horizon for the first half, then rising clear.
            val sunX = w * if (landscape) 0.74f else 0.5f
            val sunR = size.minDimension * 0.055f
            val sunY = horizon + h * 0.12f - (h * if (landscape) 0.25f else 0.24f) * smoothstep(0.3f, 1f, p)
            // The glow is strongest as the sun breaks the horizon, then eases off as the sky turns blue.
            val glowA = smoothstep(0.15f, 0.62f, p) * (1f - 0.45f * smoothstep(0.8f, 1f, p))
            val glow = Offset(sunX, maxOf(sunY, horizon - h * 0.02f))
            drawCircle(
                Brush.radialGradient(
                    0f to Color(0xFFFFB46A).copy(alpha = 0.5f * glowA), 0.35f to Color(0xFFFF8A50).copy(alpha = 0.18f * glowA), 1f to Color.Transparent,
                    center = glow, radius = size.maxDimension * 0.55f,
                ),
                size.maxDimension * 0.55f, glow, blendMode = BlendMode.Plus,
            )
            val sunA = smoothstep(0.35f, 0.6f, p)
            if (sunA > 0f) {
                val c = Offset(sunX, sunY)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE6B8).copy(alpha = 0.55f * sunA), Color.Transparent), c, sunR * 3f), sunR * 3f, c, blendMode = BlendMode.Plus)
                drawCircle(lerp(Color(0xFFFF8A48), Color(0xFFFFFAEE), smoothstep(0.55f, 0.9f, p)).copy(alpha = sunA), sunR, c)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f * smoothstep(0.6f, 1f, p)), Color.Transparent), c, sunR * 1.6f), sunR * 1.6f, c, blendMode = BlendMode.Plus)
            }

            // Long thin clouds with soft edges: dark against the night, lit from beneath as the sun nears.
            for (band in 0 until 3) {
                val y = horizon - h * (0.09f + band * 0.075f)
                val bh = h * (0.035f + band * 0.01f)
                val tint = lerp(Color(0xFF0B0D1A), lerp(Color(0xFFE88C64), Color(0xFFFFE0C4), smoothstep(0.6f, 1f, p) * (band / 2f + 0.5f)), smoothstep(0.25f, 0.85f, p))
                val bounds = Rect(0f, y - bh, w, y + bh)
                layer(bounds) {
                    drawRect(
                        cloudTex.place(w * (1.3f + band * 0.35f), bh * 2.4f, dx = t * (2f + band) + band * 240f, dy = y - bh),
                        bounds.topLeft, bounds.size, alpha = 0.7f - band * 0.12f,
                        colorFilter = ColorFilter.tint(tint, BlendMode.Modulate),
                    )
                    drawRect(
                        Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent, startY = bounds.top, endY = bounds.bottom),
                        bounds.topLeft, bounds.size, blendMode = BlendMode.DstIn,
                    )
                }
            }

            // Mountains, picking up the sky's color as it lightens.
            val night = Color(0xFF020306)
            val farC = lerp(night, lerp(mid, Color.Black, 0.5f), smoothstep(0.1f, 1f, p))
            drawPath(sc.far, farC)
            // Haze lit along the horizon.
            drawRect(
                Brush.verticalGradient(0f to Color.Transparent, 0.8f to low.copy(alpha = 0.3f * glowA), 1f to low.copy(alpha = 0.45f * glowA), startY = horizon - h * 0.08f, endY = water),
                Offset(0f, horizon - h * 0.08f), Size(w, water - horizon + h * 0.08f),
            )

            // The lake mirrors the sky, darker and softer.
            drawRect(
                Brush.verticalGradient(0f to lerp(low, Color.Black, 0.35f), 0.4f to lerp(mid, Color.Black, 0.5f), 1f to lerp(zenith, Color.Black, 0.6f), startY = water, endY = h),
                Offset(0f, water), Size(w, h - water),
            )
            withTransform({ scale(1f, -0.55f, pivot = Offset(0f, water)) }) {
                drawPath(sc.far, farC.copy(alpha = 0.7f))
            }
            // The sun's path across the water: broken ripples of light that widen toward you.
            val pathA = smoothstep(0.3f, 0.7f, p)
            if (pathA > 0f) {
                val warm = lerp(Color(0xFFFF9A5A), Color(0xFFFFF0D0), smoothstep(0.6f, 1f, p))
                var y = water + h * 0.003f
                var k = 0
                while (y < h) {
                    val near = (y - water) / (h - water)
                    val spread = sunR * (0.45f + 2.4f * near)
                    // Each ripple row breaks into a few glints that shimmer and drift.
                    val pieces = 1 + (hash(k * 5) * 3).toInt()
                    for (q in 0 until pieces) {
                        val centre = sunX + (hash(k * 7 + q * 31) - 0.5f) * 2f * spread * 0.8f + noise1(k * 0.5f + q + t * 0.35f, 13) * spread * 0.25f
                        val half = spread * (0.12f + 0.35f * hash(k * 11 + q * 17)) * (0.6f + 0.4f * noise1(k * 0.9f + q * 3 + t * 0.8f, 5))
                        val fall = 1f - abs(centre - sunX) / (spread * 1.3f)
                        val a = pathA * (0.6f - 0.35f * near) * fall.coerceIn(0f, 1f) * (0.55f + 0.45f * noise1(k * 1.3f + q + t * 1.1f, 9))
                        if (a <= 0.01f || half < 1f) continue
                        drawLine(warm.copy(alpha = a.coerceIn(0f, 1f)), Offset(centre - half, y), Offset(centre + half, y), strokeWidth = h * 0.0018f * (1f + near * 1.5f), cap = StrokeCap.Round)
                    }
                    y += h * (0.004f + 0.009f * near) * (0.7f + 0.6f * hash(k * 13))
                    k++
                }
            }
            // Faint ripples across the whole lake.
            for (k in 0 until 26) {
                val near = k / 26f
                val y = water + (h - water) * near * near + h * 0.006f
                val x = w * hash(k * 17) + sin(t * 0.2f + k) * w * 0.03f
                val len = w * (0.08f + 0.2f * near)
                drawLine(lerp(low, Color.White, 0.3f).copy(alpha = 0.06f + 0.06f * smoothstep(0.2f, 0.8f, p)), Offset(x - len, y), Offset(x + len, y), strokeWidth = density * (0.6f + near))
            }

            // Wooded headlands and the near bank in silhouette.
            val nearC = lerp(night, lerp(zenith, Color.Black, 0.72f), smoothstep(0.2f, 1f, p))
            withTransform({ scale(1f, -0.8f, pivot = Offset(0f, water)) }) {
                drawPath(sc.headlands, nearC.copy(alpha = 0.6f))
            }
            drawPath(sc.headlands, lerp(nearC, farC, 0.25f))
            drawPath(sc.bank, nearC)
            for ((x, len, lean) in sc.reeds) {
                val base = Offset(x, h * 0.99f)
                val sway = sin(t * 0.7f + x * 0.01f) * len * 0.04f
                val tip = base + Offset(lean * len + sway, -len)
                drawLine(nearC, base, tip, strokeWidth = density * 1.6f, cap = StrokeCap.Round)
            }
            drawGrain(grain, 0.05f)
        }

        // Clock and a line about the alarm.
        val tNow = ctx.now.toLocalTime()
        val p = if (ctx.demo) 0.5f else real
        val shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 2f), 18f)
        Column(
            Modifier.align(BiasAlignment(0f, if (landscape) -0.66f else -0.6f)).widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatClock(tNow, ctx.use24h), color = Color.White.copy(alpha = 0.92f), fontSize = clockSize,
                    fontWeight = FontWeight.ExtraLight, style = TabularNumbers.merge(TextStyle(shadow = shadow)), lineHeight = clockSize,
                )
                if (!ctx.use24h) {
                    Text(amPm(tNow), color = Color.White.copy(alpha = 0.6f), fontSize = small, modifier = Modifier.offset(x = 6.dp, y = -clockSizeOffset(landscape)))
                }
            }
            val alarm = if (ctx.demo) demoAlarm else ctx.nextAlarm
            val line = when {
                alarm == null -> stringResource(R.string.sunrise_no_alarm)
                p >= 1f -> stringResource(R.string.sunrise_morning)
                p > 0f -> stringResource(R.string.sunrise_rising, formatTimeWithAmPm(alarm.toLocalTime(), ctx.use24h))
                else -> stringResource(
                    R.string.sunrise_scheduled,
                    formatTimeWithAmPm(alarm.minusMinutes(WakeLight.DAWN_MINUTES).toLocalTime(), ctx.use24h),
                    formatTimeWithAmPm(alarm.toLocalTime(), ctx.use24h),
                )
            }
            Text(
                line, color = Color.White.copy(alpha = 0.7f), fontSize = small, textAlign = TextAlign.Center,
                style = TextStyle(shadow = shadow), modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        if (ctx.nextAlarm == null && !ctx.demo) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp, start = 32.dp, end = 32.dp)) {
                Text(stringResource(R.string.sunrise_how), color = Color.White.copy(alpha = 0.4f), fontSize = small * 0.85f, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun clockSizeOffset(landscape: Boolean) = if (landscape) 18.dp else 22.dp
