package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.R
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.smoothstep
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private fun phaseLabel(i: Int) = when (i) {
    0 -> R.string.breathe_in
    2 -> R.string.breathe_out
    else -> R.string.breathe_hold
}

// ---------------------------------------------------------------- Box Breathing

/** A dot traces a square, four seconds per side: in, hold, out, hold. */
@Composable
fun BoxBreathSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val side by remember { derivedStateOf { ((time.value / 4f).toInt()) % 4 } }
    val count by remember { derivedStateOf { 4 - ((time.value % 4f).toInt()) } }
    val accent = ctx.accent
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val lift = if (landscape) 0.1f else 0f
        val label = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.055f).toSp() }
        val small = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.032f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            drawRect(Brush.radialGradient(listOf(Color(0xFF0A0F1C), Color(0xFF010205)), center, size.maxDimension * 0.7f))
            val s = size.minDimension * if (landscape) 0.5f else 0.58f
            val c = Offset(size.width / 2, size.height * (0.5f - lift))
            val tl = c - Offset(s / 2, s / 2)
            val corners = listOf(tl, tl + Offset(s, 0f), tl + Offset(s, s), tl + Offset(0f, s))
            drawRoundRect(Color.White.copy(alpha = 0.08f), tl, Size(s, s), CornerRadius(s * 0.04f), style = Stroke(1.5f * density))
            val i = ((t / 4f).toInt()) % 4
            val f = (t % 4f) / 4f
            val from = corners[i]
            val to = corners[(i + 1) % 4]
            val dot = from + (to - from) * f
            drawLine(accent.copy(alpha = 0.7f), from, dot, strokeWidth = 2.5f * density, cap = StrokeCap.Round)
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent), dot, s * 0.12f), s * 0.12f, dot, blendMode = BlendMode.Plus)
            drawCircle(lerp(accent, Color.White, 0.6f), 5f * density, dot)
            // A soft breath of light inside the square: brighter when full.
            val fill = when (i) {
                0 -> f
                1 -> 1f
                2 -> 1f - f
                else -> 0f
            }
            drawRoundRect(accent.copy(alpha = 0.04f + 0.08f * fill), tl, Size(s, s), CornerRadius(s * 0.04f))
        }
        Column(Modifier.align(BiasAlignment(0f, -lift * 2f)), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(side, transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(400)) }, label = "side") { s ->
                Text(stringResource(phaseLabel(s)), color = Color.White, fontSize = label, fontWeight = FontWeight.Light)
            }
            Text("$count", color = Color.White.copy(alpha = 0.4f), fontSize = label * 0.6f, style = TabularNumbers)
        }
        Text(
            stringResource(R.string.box_breath_hint), color = Color.White.copy(alpha = 0.35f), fontSize = small,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = if (landscape) 16.dp else 64.dp),
        )
    }
}

// ---------------------------------------------------------------- Tide

/** Breathe with the sea: the tide rises as you breathe in and falls as you breathe out. */
@Composable
fun TideSaver(ctx: SaverContext) {
    val pattern = ctx.settings.breathPattern
    val durations = floatArrayOf(pattern.inhale.toFloat(), pattern.holdIn.toFloat(), pattern.exhale.toFloat(), pattern.holdOut.toFloat())
    val cycle = durations.sum()
    val time = rememberFrameTime()
    val stars = remember { StarSky(120, 61, 0f, 0.6f) }
    fun at(t: Float): Triple<Int, Float, Float> {
        var p = t % cycle
        var i = 0
        while (i < 3 && p >= durations[i]) {
            p -= durations[i]; i++
        }
        return Triple(i, if (durations[i] > 0f) p / durations[i] else 1f, durations[i] - p)
    }
    val phase by remember(pattern) { derivedStateOf { at(time.value).first } }
    val left by remember(pattern) { derivedStateOf { ceil(at(time.value).third.toDouble()).toInt() } }
    val level by remember(pattern) {
        derivedStateOf {
            val (i, f, _) = at(time.value)
            val e = (1 - cos(f * PI.toFloat())) / 2f
            when (i) {
                0 -> e
                1 -> 1f
                2 -> 1f - e
                else -> 0f
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val label = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.055f).toSp() }
        val low = 0.72f
        val high = 0.38f
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(Color(0xFF02040C), Color(0xFF07122A))))
            stars.draw(this, t, 0.5f, horizonFade = 0.4f)
            val surface = h * (low + (high - low) * level)
            for (layer in 0..1) {
                val wave = Path().apply {
                    moveTo(0f, h)
                    var x = 0f
                    while (x <= w + 8f) {
                        val y = surface + layer * h * 0.012f + h * 0.008f * sin(x / w * 5f + t * (0.5f + layer * 0.3f) + layer * 2f)
                        lineTo(x, y)
                        x += 8f
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(wave, Brush.verticalGradient(listOf(Color(0xFF1C6E8A).copy(alpha = 0.55f - layer * 0.15f), Color(0xFF041828)), surface, h))
            }
            drawLine(Color(0xFF9FE0F0).copy(alpha = 0.25f), Offset(0f, surface), Offset(w, surface), strokeWidth = density)
        }
        Column(
            Modifier.align(BiasAlignment(0f, ((low + (high - low) * level) - 0.12f) * 2f - 1f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(phase, transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(500)) }, label = "phase") { ph ->
                Text(stringResource(phaseLabel(ph)), color = Color.White, fontSize = label, fontWeight = FontWeight.Light)
            }
            Text("$left", color = Color.White.copy(alpha = 0.35f), fontSize = label * 0.55f, style = TabularNumbers)
        }
    }
}

// ---------------------------------------------------------------- Wind Down

private const val PROMPT_SECONDS = 14f

/** Gentle prompts, one at a time, to let the body settle. */
@Composable
fun WindDownSaver(ctx: SaverContext) {
    val prompts = stringArrayResource(R.array.wind_down_prompts)
    val time = rememberFrameTime()
    val index by remember { derivedStateOf { ((time.value / PROMPT_SECONDS).toInt()) % prompts.size } }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val label = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.062f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            drawRect(Color(0xFF020309))
            // Two very slow, dim pools of color drifting behind the words.
            listOf(Color(0xFF1E2A5A), Color(0xFF16404A)).forEachIndexed { i, c ->
                val o = Offset(size.width * (0.5f + 0.3f * sin(t * 0.03f + i * 2.5f)), size.height * (0.5f + 0.25f * cos(t * 0.025f + i * 1.7f)))
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), o, size.maxDimension * 0.55f), size.maxDimension * 0.55f, o)
            }
        }
        val fade by remember { derivedStateOf { (time.value % PROMPT_SECONDS).let { smoothstep(0f, 2.5f, it) * (1f - smoothstep(PROMPT_SECONDS - 2.5f, PROMPT_SECONDS, it)) } } }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                prompts[index], color = Color.White.copy(alpha = 0.88f), fontSize = label, fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center, lineHeight = label * 1.3f,
                modifier = Modifier.widthIn(max = 460.dp).padding(horizontal = 36.dp).alpha(fade),
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                prompts.indices.forEach { i ->
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = if (i == index) 0.5f else 0.12f)))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Count Down

private const val COUNT_SECONDS = 6

/** Count slowly down from 100, one number per long out-breath. A classic trick for drifting off. */
@Composable
fun CountDownSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val number by remember { derivedStateOf { 100 - ((time.value / COUNT_SECONDS).toInt() % 100) } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val big = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.32f).toSp() }
        val small = with(LocalDensity.current) { (min(maxWidth, maxHeight) * 0.034f).toSp() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                number,
                transitionSpec = { (fadeIn(tween(1800)) + scaleIn(tween(1800), 1.06f)) togetherWith (fadeOut(tween(1400)) + scaleOut(tween(1800), 0.94f)) },
                label = "count",
            ) { n ->
                Text("$n", color = lerp(ctx.accent, Color.White, 0.5f).copy(alpha = 0.75f), fontSize = big, fontWeight = FontWeight.Thin, style = TabularNumbers)
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.count_down_hint), color = Color.White.copy(alpha = 0.3f), fontSize = small)
        }
    }
}
