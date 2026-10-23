package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.R
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.TAU
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.layer
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.rememberNoiseBrush
import dev.pridgey.beddery.savers.ridge
import dev.pridgey.beddery.savers.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ---------------------------------------------------------------- Breathe (refined)

@Composable
fun BreatheRefinedSaver(ctx: SaverContext) {
    val pattern = ctx.settings.breathPattern
    val durations = floatArrayOf(pattern.inhale.toFloat(), pattern.holdIn.toFloat(), pattern.exhale.toFloat(), pattern.holdOut.toFloat())
    val cycle = durations.sum()
    val time = rememberFrameTime()
    val grain = rememberGrainBrush()
    fun phaseAt(t: Float): Triple<Int, Float, Float> {
        var p = t % cycle
        var i = 0
        while (i < 3 && p >= durations[i]) {
            p -= durations[i]; i++
        }
        val f = if (durations[i] > 0f) p / durations[i] else 1f
        return Triple(i, f, durations[i] - p)
    }
    val phase by remember(pattern) { derivedStateOf { phaseAt(time.value).first } }
    val remaining by remember(pattern) { derivedStateOf { kotlin.math.ceil(phaseAt(time.value).third.toDouble()).toInt() } }
    val accent = ctx.accent
    val cool = lerp(accent, Color(0xFF62E3D0), 0.5f)
    val warm = lerp(accent, Color(0xFFB08CFF), 0.5f)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        // In landscape, lift the orb so the clock fits underneath.
        val lift = if (landscape) 0.12f else 0f
        val label = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.055f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val (i, f, _) = phaseAt(t)
            val center = Offset(size.width / 2, size.height * (0.5f - lift))
            val ease = (1 - cos(f * PI.toFloat())) / 2f
            val fill = when (i) {
                0 -> ease
                1 -> 1f
                2 -> 1f - ease
                else -> 0f
            }
            val maxR = size.minDimension * if (landscape) 0.24f else 0.3f
            val r = maxR * (0.42f + 0.58f * fill)
            val tone = lerp(warm, cool, fill)
            // Room light follows the breath: cooler and brighter when full, warmer when empty.
            drawRect(Brush.radialGradient(listOf(lerp(Color(0xFF070A14), tone, 0.12f), Color(0xFF010205)), center, size.maxDimension * 0.75f))
            drawCircle(Brush.radialGradient(listOf(tone.copy(alpha = 0.14f + 0.08f * fill), Color.Transparent), center, r * 2.2f), r * 2.2f, center)

            // The orb: a soft, glassy body that fills and empties with the breath.
            drawCircle(
                Brush.radialGradient(listOf(lerp(tone, Color.White, 0.3f).copy(alpha = 0.75f), tone.copy(alpha = 0.42f), tone.copy(alpha = 0.18f)), center, r),
                r, center,
            )
            drawCircle(Color.White.copy(alpha = 0.16f), r, center, style = Stroke(width = density * 1f))

            // Progress through the current step, on a quiet outer ring.
            val ringR = maxR * 1.12f
            drawCircle(Color.White.copy(alpha = 0.05f), ringR, center, style = Stroke(width = density * 1.5f))
            drawArc(
                Color.White.copy(alpha = 0.28f), -90f, 360f * f, useCenter = false,
                topLeft = center - Offset(ringR, ringR), size = Size(ringR * 2, ringR * 2),
                style = Stroke(width = density * 1.5f, cap = StrokeCap.Round),
            )
        }
        Column(Modifier.align(androidx.compose.ui.BiasAlignment(0f, -lift * 2f)), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(phase, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) }, label = "phase") { ph ->
                val res = when (ph) {
                    0 -> R.string.breathe_in
                    2 -> R.string.breathe_out
                    else -> R.string.breathe_hold
                }
                Text(stringResource(res), color = Color.White, fontSize = label, fontWeight = FontWeight.Light)
            }
            Text("$remaining", color = Color.White.copy(alpha = 0.35f), fontSize = label * 0.5f, style = TabularNumbers)
        }
    }
}
