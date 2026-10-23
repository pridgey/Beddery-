package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.R
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.smoothstep
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

private val SandLight = Color(0xFFF0CB8C)
private val Sand = Color(0xFFD9A85E)
private val SandDark = Color(0xFF9A6A30)
private val Wood = Color(0xFF5A3418)
private val WoodLight = Color(0xFFA0683A)
private val WoodDark = Color(0xFF2A160A)

/** Half-width of the glass at height u (0 at the neck, 1 at the end), as a fraction of the bulb radius. */
private fun glassRadius(u: Float): Float {
    val a = abs(u)
    val neck = 0.05f
    return neck + (1f - neck) * sqrt(max(0f, 1f - ((a - 0.52f) / 0.5f).pow(2)))
}

/** Sand slipping through an hourglass over each hour, which turns itself over on the hour. */
@Composable
fun HourglassSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val grain = rememberGrainBrush()
    // When the hour turns, flip the glass over a couple of seconds.
    val flip = remember { floatArrayOf(ctx.now.hour.toFloat(), -100f) }
    val glass = remember { Path() }
    val top = remember { Path() }
    val bottom = remember { Path() }
    val seconds = ctx.now.minute * 60 + ctx.now.second
    val left = 60 - ctx.now.minute

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val dens = LocalDensity.current
        val u = min(maxWidth, maxHeight)
        val clock = with(dens) { (u * if (landscape) 0.16f else 0.15f).toSp() }
        val body = with(dens) { (u * 0.04f).toSp() }

        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            if (ctx.now.hour.toFloat() != flip[0]) {
                flip[0] = ctx.now.hour.toFloat()
                flip[1] = t
            }
            val fp = ((t - flip[1]) / 2.6f).coerceIn(0f, 1f)
            val flipping = fp < 1f
            // While turning over, the sand is all at the (old) bottom.
            val f = if (flipping) 1f else seconds / 3600f
            val angle = if (flipping) 180f * smoothstep(0f, 1f, fp) else 0f

            val w = size.width
            val h = size.height
            val height = if (landscape) h * 0.7f else minOf(h * 0.5f, w * 1.0f)
            val c = Offset(w * if (landscape) 0.33f else 0.5f, h * if (landscape) 0.5f else 0.4f)

            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B0806), Color(0xFF140D08), Color(0xFF080504)), 0f, h))
            val lamp = Offset(c.x - height * 0.6f, c.y - height * 0.7f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFF5A3418).copy(alpha = 0.5f), Color.Transparent), lamp, size.maxDimension * 0.8f), size.maxDimension * 0.8f, lamp)
            // The table it stands on.
            val tableY = c.y + height * 0.52f
            drawRect(Brush.verticalGradient(listOf(Color(0xFF1E130C), Color(0xFF0A0605)), tableY, h), Offset(0f, tableY), Size(w, h - tableY))
            drawLine(Color(0xFF6A4A2A).copy(alpha = 0.35f), Offset(0f, tableY), Offset(w, tableY), strokeWidth = density)
            drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), Offset(c.x + height * 0.05f, tableY), height * 0.45f), Offset(c.x - height * 0.4f, tableY - height * 0.03f), Size(height * 0.9f, height * 0.08f))
            drawOval(Brush.radialGradient(listOf(SandLight.copy(alpha = 0.08f), Color.Transparent), Offset(c.x, tableY + height * 0.02f), height * 0.35f), Offset(c.x - height * 0.35f, tableY - height * 0.01f), Size(height * 0.7f, height * 0.06f))

            rotate(angle, Offset(c.x, c.y)) {
                drawHourglass(c, height, f, t, flipping, glass, top, bottom)
            }
            drawGrain(grain, 0.06f)
        }

        val tNow = ctx.now.toLocalTime()
        Column(
            if (landscape) Modifier.align(BiasAlignment(0.62f, 0f)) else Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.12f),
            horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatClock(tNow, ctx.use24h), color = Color(0xFFF4E6D0).copy(alpha = 0.88f), fontSize = clock, lineHeight = clock, fontWeight = FontWeight.Light, style = TabularNumbers)
                if (!ctx.use24h) Text(amPm(tNow), color = Color(0xFFF4E6D0).copy(alpha = 0.5f), fontSize = body, modifier = Modifier.offset(x = 6.dp, y = (-10).dp))
            }
            Text(stringResource(R.string.hourglass_left, left), color = SandLight.copy(alpha = 0.6f), fontSize = body, style = TabularNumbers)
        }
    }
}

private fun DrawScope.drawHourglass(c: Offset, height: Float, f: Float, t: Float, flipping: Boolean, glass: Path, top: Path, bottom: Path) {
    val half = height * 0.42f // from neck to the end of each bulb
    val bulbR = height * 0.2f
    fun y(u: Float) = c.y + u * half
    fun r(u: Float) = glassRadius(u) * bulbR

    // The glass outline, as one closed shape.
    glass.reset()
    val steps = 60
    for (i in 0..steps) {
        val u = -1f + 2f * i / steps
        val p = Offset(c.x + r(u), y(u))
        if (i == 0) glass.moveTo(p.x, p.y) else glass.lineTo(p.x, p.y)
    }
    for (i in steps downTo 0) {
        val u = -1f + 2f * i / steps
        glass.lineTo(c.x - r(u), y(u))
    }
    glass.close()

    // A back post, seen through the glass.
    drawLine(Brush.horizontalGradient(listOf(WoodDark, Wood, WoodDark), c.x - height * 0.015f, c.x + height * 0.015f), Offset(c.x, y(-1.05f)), Offset(c.x, y(1.05f)), strokeWidth = height * 0.03f)
    // Faint glass body.
    drawPath(glass, Color(0xFFBFD8E0).copy(alpha = 0.05f))

    clipPath(glass) {
        // Sand in the top bulb: a level surface with a crater over the neck.
        val remaining = 1f - f
        if (remaining > 0.002f) {
            val level = -(0.1f + 0.78f * remaining.pow(0.7f))
            val dip = 0.12f * (1f - remaining).pow(0.3f).coerceAtMost(1f) * if (remaining < 0.999f) 1f else 0f
            top.reset()
            top.moveTo(c.x - bulbR * 1.2f, y(level))
            val n = 30
            for (i in 0..n) {
                val xf = -1.2f + 2.4f * i / n
                val crater = dip * max(0f, 1f - abs(xf) / 0.9f).pow(1.6f)
                top.lineTo(c.x + xf * bulbR, y(level + crater))
            }
            top.lineTo(c.x + bulbR * 1.2f, y(0.02f))
            top.lineTo(c.x - bulbR * 1.2f, y(0.02f))
            top.close()
            drawPath(top, Brush.horizontalGradient(listOf(SandDark, Sand, SandLight, Sand, SandDark), c.x - bulbR, c.x + bulbR))
            // Speckles of darker grains.
            clipPath(top) {
                for (k in 0 until 80) {
                    val p = Offset(c.x + (hash(k * 3) - 0.5f) * bulbR * 2f, y(-hash(k * 7)))
                    drawCircle(SandDark.copy(alpha = 0.4f), height * 0.0025f, p)
                }
            }
        }
        // The pile in the bottom bulb, a cone that grows.
        val pile = 0.08f + 0.78f * f.pow(0.75f)
        val peak = 1f - pile
        val shoulder = 1f - pile * 0.72f
        bottom.reset()
        bottom.moveTo(c.x - bulbR * 1.2f, y(1.05f))
        bottom.lineTo(c.x - bulbR * 1.2f, y(shoulder + 0.06f))
        bottom.cubicTo(c.x - bulbR * 0.7f, y(shoulder), c.x - bulbR * 0.25f, y(peak + 0.02f), c.x, y(peak))
        bottom.cubicTo(c.x + bulbR * 0.25f, y(peak + 0.02f), c.x + bulbR * 0.7f, y(shoulder), c.x + bulbR * 1.2f, y(shoulder + 0.06f))
        bottom.lineTo(c.x + bulbR * 1.2f, y(1.05f))
        bottom.close()
        drawPath(bottom, Brush.horizontalGradient(listOf(SandDark, Sand, SandLight, Sand, SandDark), c.x - bulbR, c.x + bulbR))
        drawPath(bottom, Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)), y(peak), y(1f)))

        // The falling stream and a few grains bouncing off the pile.
        if (!flipping && f < 0.999f) {
            drawLine(Brush.verticalGradient(listOf(SandLight, Sand), y(0f), y(peak)), Offset(c.x, y(-0.02f)), Offset(c.x, y(peak)), strokeWidth = height * 0.006f)
            for (k in 0 until 14) {
                val phase = (t * 1.6f + hash(k * 5)) % 1f
                val gy = y(phase * peak)
                drawCircle(SandLight.copy(alpha = 0.8f), height * 0.003f, Offset(c.x + (hash(k * 9) - 0.5f) * height * 0.006f, gy))
            }
            for (k in 0 until 6) {
                val phase = (t * 2.2f + hash(k * 7)) % 1f
                val side = if (k % 2 == 0) -1f else 1f
                val p = Offset(c.x + side * phase * bulbR * 0.25f, y(peak + phase * 0.05f) - (1f - (2 * phase - 1).pow(2)) * height * 0.012f)
                drawCircle(SandLight.copy(alpha = 0.6f * (1f - phase)), height * 0.0025f, p)
            }
        }
    }
    // Glass: bright rims and long reflections.
    drawPath(glass, Color.White.copy(alpha = 0.22f), style = Stroke(height * 0.004f))
    for (side in listOf(-1f, 1f)) {
        val hl = Path()
        for (i in 0..24) {
            val u = (if (side < 0) -0.9f else 0.18f) + 0.7f * i / 24f
            val p = Offset(c.x - r(u) * 0.72f, y(u))
            if (i == 0) hl.moveTo(p.x, p.y) else hl.lineTo(p.x, p.y)
        }
        drawPath(hl, Color.White.copy(alpha = 0.16f), style = Stroke(height * 0.012f, cap = StrokeCap.Round))
        val hl2 = Path()
        for (i in 0..16) {
            val u = (if (side < 0) -0.8f else 0.26f) + 0.5f * i / 16f
            val p = Offset(c.x + r(u) * 0.8f, y(u))
            if (i == 0) hl2.moveTo(p.x, p.y) else hl2.lineTo(p.x, p.y)
        }
        drawPath(hl2, Color.White.copy(alpha = 0.07f), style = Stroke(height * 0.006f, cap = StrokeCap.Round))
    }

    // Wooden end caps and turned posts.
    val capW = bulbR * 2.9f
    val capH = height * 0.065f
    for (end in listOf(-1f, 1f)) {
        val cy = y(end * 1.04f) + end * capH * 0.5f
        val tl = Offset(c.x - capW / 2, cy - capH / 2)
        drawRoundRect(Brush.verticalGradient(listOf(WoodLight, Wood, WoodDark), tl.y, tl.y + capH), tl, Size(capW, capH), CornerRadius(capH * 0.25f))
        drawRoundRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent, Color.Black.copy(alpha = 0.5f)), tl.x, tl.x + capW), tl, Size(capW, capH), CornerRadius(capH * 0.25f))
        // A thin lip where the cap meets the glass.
        val lipY = if (end < 0) tl.y + capH else tl.y
        drawRect(WoodDark, Offset(c.x - capW * 0.42f, lipY - capH * 0.08f), Size(capW * 0.84f, capH * 0.16f))
    }
    for (side in listOf(-1f, 1f)) {
        val px = c.x + side * capW * 0.42f
        val top = y(-1.04f)
        val bot = y(1.04f)
        val pw = height * 0.028f
        drawLine(Brush.horizontalGradient(listOf(WoodDark, WoodLight, Wood, WoodDark), px - pw / 2, px + pw / 2), Offset(px, top), Offset(px, bot), strokeWidth = pw)
        // Turned beads along the post.
        for (k in listOf(0.12f, 0.5f, 0.88f)) {
            val by = top + (bot - top) * k
            drawOval(Brush.horizontalGradient(listOf(WoodDark, WoodLight, Wood, WoodDark), px - pw, px + pw), Offset(px - pw * 0.95f, by - pw * 0.5f), Size(pw * 1.9f, pw))
        }
    }
    // A touch of warm light on the glass from the lamp.
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD9A0).copy(alpha = 0.1f), Color.Transparent), Offset(c.x - bulbR * 0.5f, y(-0.6f)), bulbR), bulbR, Offset(c.x - bulbR * 0.5f, y(-0.6f)), blendMode = BlendMode.Plus)
}
