package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.chargingLabel
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.layer
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.smoothstep
import dev.pridgey.beddery.system.BatteryStatus
import dev.pridgey.beddery.system.PlugType
import kotlin.math.PI
import kotlin.math.sin

/** In previews, show a phone mid-charge so these screens look alive. */
private fun SaverContext.shownBattery(): BatteryStatus =
    if (demo && !battery.isPluggedIn) BatteryStatus(level = 72, isCharging = true, isFull = false, plug = PlugType.AC, chargeTimeRemainingMs = 38 * 60_000L)
    else battery

private fun SaverContext.withBattery(b: BatteryStatus) = copy(battery = b)

/** Red when low, amber in the middle, green when nearly full. */
fun batteryColor(level: Int): Color = when {
    level <= 20 -> Color(0xFFFF5A5A)
    level <= 50 -> lerp(Color(0xFFFFB13D), Color(0xFFFFD25A), (level - 20) / 30f)
    else -> lerp(Color(0xFF9BE36A), Color(0xFF4FE08C), (level - 50) / 50f)
}

/** Time, percent and charging status in one quiet block. */
@Composable
private fun BatteryCaption(ctx: SaverContext, b: BatteryStatus, size: TextUnit, align: Alignment.Horizontal = Alignment.CenterHorizontally, showPercent: Boolean = true) {
    val context = LocalContext.current
    val label = remember(b) { chargingLabel(context, ctx.withBattery(b)) }
    Column(horizontalAlignment = align) {
        if (showPercent) {
            Text("${b.level}%", color = Color.White, fontSize = size * 2.4f, lineHeight = size * 2.4f, fontWeight = FontWeight.Light, style = TabularNumbers)
        }
        Text(label, color = batteryColor(b.level).copy(alpha = 0.9f), fontSize = size)
        Spacer(Modifier.height(4.dp))
        Text(formatTimeWithAmPm(ctx.now.toLocalTime(), ctx.use24h), color = Color.White.copy(alpha = 0.5f), fontSize = size * 0.9f, style = TabularNumbers)
    }
}

private val charging: BatteryStatus.() -> Boolean = { isCharging && !isFull }

// ---------------------------------------------------------------- Charge Vial

@Composable
fun ChargeVialSaver(ctx: SaverContext) {
    val b = ctx.shownBattery()
    val level by animateFloatAsState(b.level / 100f, tween(1500), label = "level")
    val time = rememberFrameTime()
    val bubbles = remember { List(18) { floatArrayOf(hash(it, 1), hash(it, 2), 0.4f + hash(it, 3)) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val small = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.045f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val unit = size.minDimension
            val vw = unit * 0.2f
            val vh = unit * if (landscape) 0.7f else 0.62f
            val cx = if (landscape) size.width * 0.36f else size.width / 2
            val top = (size.height - vh) / 2 - if (landscape) 0f else unit * 0.12f
            val color = batteryColor(b.level)
            val tube = Path().apply { addRoundRect(RoundRect(cx - vw / 2, top, cx + vw / 2, top + vh, CornerRadius(vw / 2))) }
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.14f), Color.Transparent), Offset(cx, top + vh * (1f - level / 2)), unit * 0.6f), unit * 0.6f, Offset(cx, top + vh * (1f - level / 2)))
            drawPath(tube, Color(0xFF0B0F18).copy(alpha = 0.9f))
            clipPath(tube) {
                val inner = vh - vw * 0.1f
                val surface = top + vw * 0.05f + inner * (1f - level)
                val amp = if (b.charging()) vw * 0.03f else vw * 0.01f
                val liquid = Path().apply {
                    moveTo(cx - vw, top + vh)
                    var x = cx - vw / 2
                    lineTo(x, surface)
                    while (x <= cx + vw / 2) {
                        lineTo(x, surface + amp * sin((x - cx) / vw * 6f + t * 2.2f))
                        x += 2f
                    }
                    lineTo(cx + vw, top + vh)
                    close()
                }
                drawPath(liquid, Brush.verticalGradient(listOf(lerp(color, Color.White, 0.25f), color, lerp(color, Color.Black, 0.45f)), surface, top + vh))
                // Meniscus highlight.
                drawLine(Color.White.copy(alpha = 0.45f), Offset(cx - vw * 0.42f, surface), Offset(cx + vw * 0.42f, surface), strokeWidth = 1.5f * density, cap = StrokeCap.Round)
                if (b.charging()) {
                    bubbles.forEach { bb ->
                        val p = (t * 0.1f * bb[2] + bb[1]) % 1f
                        val y = top + vh - p * (top + vh - surface)
                        val x = cx + (bb[0] - 0.5f) * vw * 0.7f + sin(t * 2f + bb[0] * 9f) * vw * 0.03f
                        drawCircle(Color.White.copy(alpha = 0.35f * (1f - p)), vw * 0.035f * bb[2], Offset(x, y), style = Stroke(1f * density))
                    }
                }
            }
            // Glass: a bright edge on the left, a faint one on the right.
            drawPath(tube, Color.White.copy(alpha = 0.2f), style = Stroke(1.5f * density))
            drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f)), top, top + vh), Offset(cx - vw * 0.36f, top + vw * 0.3f), Size(vw * 0.1f, vh - vw * 0.6f), CornerRadius(vw * 0.05f))
            // Measuring marks.
            for (k in 1..9) {
                val y = top + vw * 0.05f + (vh - vw * 0.1f) * k / 10f
                drawLine(Color.White.copy(alpha = if (k == 5) 0.35f else 0.18f), Offset(cx + vw * 0.25f, y), Offset(cx + vw * if (k == 5) 0.42f else 0.36f, y), strokeWidth = density)
            }
            // Stopper.
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF3A3F4A), Color(0xFF8A93A6), Color(0xFF2A2E36)), cx - vw * 0.42f, cx + vw * 0.42f), Offset(cx - vw * 0.42f, top - vw * 0.28f), Size(vw * 0.84f, vw * 0.38f), CornerRadius(vw * 0.08f))
        }
        Box(
            if (landscape) Modifier.align(Alignment.CenterEnd).padding(end = maxWidth * 0.12f)
            else Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.1f),
        ) { BatteryCaption(ctx, b, small) }
    }
}

// ---------------------------------------------------------------- Fill Up

@Composable
fun BigFillSaver(ctx: SaverContext) {
    val b = ctx.shownBattery()
    val level by animateFloatAsState(b.level / 100f, tween(1500), label = "level")
    val time = rememberFrameTime()
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val small = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.045f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val color = batteryColor(b.level)
            val fontSize = minOf(size.width * 0.34f, size.height * 0.42f).toSp()
            val text = measurer.measure("${b.level}", TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = (-0.04).em, fontFeatureSettings = "tnum"))
            val pct = measurer.measure("%", TextStyle(fontSize = fontSize * 0.35f, fontWeight = FontWeight.Bold))
            val totalW = text.size.width + pct.size.width
            val o = Offset((size.width - totalW) / 2f, (size.height - text.size.height) / 2f - size.height * 0.06f)
            val bounds = Rect(o, Size(text.size.width.toFloat(), text.size.height.toFloat()))
            // Usable glyph height: digits sit roughly between 18% and 88% of the line box.
            val glyphTop = bounds.top + bounds.height * 0.18f
            val glyphBottom = bounds.top + bounds.height * 0.88f
            val surface = glyphBottom - (glyphBottom - glyphTop) * level
            drawText(text, color = Color.White.copy(alpha = 0.08f), topLeft = o)
            layer(bounds.inflate(4f)) {
                drawText(text, color = Color.White, topLeft = o)
                val amp = if (b.charging()) bounds.height * 0.012f else bounds.height * 0.004f
                val wave = Path().apply {
                    moveTo(bounds.left, bounds.bottom)
                    var x = bounds.left
                    while (x <= bounds.right) {
                        lineTo(x, surface + amp * sin((x - bounds.left) / bounds.width * 9f + t * 2f))
                        x += 3f
                    }
                    lineTo(bounds.right, bounds.bottom)
                    close()
                }
                drawPath(wave, Brush.verticalGradient(listOf(lerp(color, Color.White, 0.2f), color, lerp(color, Color.Black, 0.35f)), surface, glyphBottom), blendMode = BlendMode.SrcIn)
                drawRect(Color.Transparent, bounds.topLeft, Size(bounds.width, (surface - bounds.top - amp * 2).coerceAtLeast(0f)), blendMode = BlendMode.Clear)
            }
            drawText(pct, color = color.copy(alpha = 0.8f), topLeft = Offset(o.x + text.size.width + 8f, o.y + text.size.height * 0.62f - pct.size.height))
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.14f)) {
            BatteryCaption(ctx, b, small, showPercent = false)
        }
    }
}

// ---------------------------------------------------------------- Power Flow

@Composable
fun PowerFlowSaver(ctx: SaverContext) {
    val b = ctx.shownBattery()
    val level by animateFloatAsState(b.level / 100f, tween(1500), label = "level")
    val time = rememberFrameTime()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val small = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.045f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val unit = size.minDimension
            val color = batteryColor(b.level)
            val bw = unit * 0.26f
            val bh = unit * 0.46f
            val cx = if (landscape) size.width * 0.36f else size.width / 2
            val top = if (landscape) (size.height - bh) / 2 - unit * 0.06f else size.height * 0.2f
            val body = RoundRect(cx - bw / 2, top, cx + bw / 2, top + bh, CornerRadius(bw * 0.16f))
            val flowing = b.charging()
            val wireless = b.plug == PlugType.WIRELESS
            // Charging speed: wall plugs fastest, USB slower.
            val rate = when (b.plug) {
                PlugType.AC, PlugType.DOCK -> 1f
                PlugType.USB -> 0.55f
                else -> 0.4f
            }
            val bottomPt = Offset(cx, top + bh)
            if (wireless) {
                val pad = Offset(cx, top + bh + unit * 0.12f)
                drawOval(Color(0xFF1A1D24), Offset(pad.x - bw * 0.8f, pad.y - unit * 0.02f), Size(bw * 1.6f, unit * 0.05f))
                if (flowing) for (k in 0 until 4) {
                    val p = (t * 0.35f + k / 4f) % 1f
                    val rr = bw * (0.3f + p * 0.7f)
                    drawOval(color.copy(alpha = 0.4f * (1f - p)), Offset(pad.x - rr, pad.y - rr * 0.22f - p * unit * 0.08f), Size(rr * 2, rr * 0.44f), style = Stroke(1.5f * density))
                }
            } else {
                // The cable, curving up from below into the battery.
                val start = Offset(cx + unit * 0.25f, size.height + 10f)
                val c1 = Offset(cx + unit * 0.25f, size.height - unit * 0.2f)
                val c2 = Offset(cx, bottomPt.y + unit * 0.25f)
                val cable = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(c1.x, c1.y, c2.x, c2.y, bottomPt.x, bottomPt.y + unit * 0.06f)
                }
                drawPath(cable, Color(0xFF2A2E36), style = Stroke(unit * 0.018f, cap = StrokeCap.Round))
                drawRoundRect(Color(0xFF3A3F4A), Offset(cx - unit * 0.025f, bottomPt.y), Size(unit * 0.05f, unit * 0.07f), CornerRadius(unit * 0.008f))
                if (flowing) {
                    val end = Offset(bottomPt.x, bottomPt.y + unit * 0.06f)
                    fun bez(s: Float): Offset {
                        val u = 1 - s
                        return start * (u * u * u) + c1 * (3 * u * u * s) + c2 * (3 * u * s * s) + end * (s * s * s)
                    }
                    for (k in 0 until 14) {
                        val s = (t * 0.28f * rate + k / 14f) % 1f
                        val p = bez(s)
                        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.8f), Color.Transparent), p, unit * 0.022f), unit * 0.022f, p, blendMode = BlendMode.Plus)
                        drawCircle(Color.White.copy(alpha = 0.9f), unit * 0.004f, p)
                    }
                }
            }
            // Battery body, terminal and fill.
            drawRoundRect(Color(0xFF3A3F4A), Offset(cx - bw * 0.18f, top - unit * 0.035f), Size(bw * 0.36f, unit * 0.045f), CornerRadius(unit * 0.01f))
            val shell = Path().apply { addRoundRect(body) }
            drawPath(shell, Color(0xFF0D1016))
            clipPath(shell) {
                val inset = bw * 0.07f
                val fillTop = top + inset + (bh - inset * 2) * (1f - level)
                drawRect(Brush.verticalGradient(listOf(lerp(color, Color.White, 0.2f), color, lerp(color, Color.Black, 0.4f)), fillTop, top + bh), Offset(cx - bw / 2 + inset, fillTop), Size(bw - inset * 2, top + bh - inset - fillTop))
                if (flowing) {
                    // A shimmer rising through the charge.
                    val p = (t * 0.5f * rate) % 1f
                    val y = top + bh - (top + bh - fillTop) * p
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.3f), Color.Transparent), y - bh * 0.05f, y + bh * 0.05f), Offset(cx - bw / 2, y - bh * 0.05f), Size(bw, bh * 0.1f), blendMode = BlendMode.Plus)
                }
            }
            drawPath(shell, Color.White.copy(alpha = 0.25f), style = Stroke(2f * density))
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.12f), Color.Transparent), Offset(cx, top + bh / 2), unit * 0.55f), unit * 0.55f, Offset(cx, top + bh / 2))
        }
        Box(
            if (landscape) Modifier.align(Alignment.CenterEnd).padding(end = maxWidth * 0.12f)
            else Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.08f),
        ) { BatteryCaption(ctx, b, small) }
    }
}

// ---------------------------------------------------------------- Charge Garden

@Composable
fun ChargeGardenSaver(ctx: SaverContext) {
    val b = ctx.shownBattery()
    val level by animateFloatAsState(b.level / 100f, tween(2500), label = "level")
    val time = rememberFrameTime()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val small = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.045f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val unit = size.minDimension
            val cx = if (landscape) size.width * 0.34f else size.width / 2
            val ground = size.height * if (landscape) 0.86f else 0.7f
            // A soft spotlight on a dark windowsill.
            drawRect(Brush.verticalGradient(listOf(Color(0xFF070A10), Color(0xFF0C1014))))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF2A3A2E).copy(alpha = 0.45f), Color.Transparent), Offset(cx, ground - unit * 0.3f), unit * 0.6f), unit * 0.6f, Offset(cx, ground - unit * 0.3f))
            drawRect(Color(0xFF14110E), Offset(0f, ground), Size(size.width, size.height - ground))
            // Pot.
            val pw = unit * 0.2f
            val ph = unit * 0.16f
            val pot = Path().apply {
                moveTo(cx - pw / 2, ground - ph)
                lineTo(cx + pw / 2, ground - ph)
                lineTo(cx + pw * 0.38f, ground)
                lineTo(cx - pw * 0.38f, ground)
                close()
            }
            drawPath(pot, Brush.horizontalGradient(listOf(Color(0xFF7A3A22), Color(0xFFC0643C), Color(0xFF6A301C)), cx - pw / 2, cx + pw / 2))
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF8A4428), Color(0xFFD27448), Color(0xFF7A3A22)), cx - pw * 0.55f, cx + pw * 0.55f), Offset(cx - pw * 0.55f, ground - ph - unit * 0.035f), Size(pw * 1.1f, unit * 0.04f), CornerRadius(unit * 0.008f))
            drawOval(Color(0xFF2A1A10), Offset(cx - pw * 0.48f, ground - ph - unit * 0.035f), Size(pw * 0.96f, unit * 0.022f))

            // The plant grows with the charge, and droops a little when the battery is low.
            val base = Offset(cx, ground - ph - unit * 0.025f)
            val height = unit * (0.04f + 0.48f * level)
            val droop = if (b.level < 20) 0.25f else 0f
            val sway = sin(t * 0.6f) * unit * 0.01f * (0.3f + level)
            val tip = base + Offset(sway + droop * height * 0.6f, -height * (1f - droop * 0.3f))
            val ctrl = base + Offset(-sway * 0.5f, -height * 0.55f)
            val stem = Path().apply {
                moveTo(base.x, base.y)
                quadraticTo(ctrl.x, ctrl.y, tip.x, tip.y)
            }
            drawPath(stem, Brush.verticalGradient(listOf(Color(0xFF8BD06A), Color(0xFF3E8A3A)), tip.y, base.y), style = Stroke(unit * 0.012f, cap = StrokeCap.Round))
            fun stemAt(s: Float): Offset {
                val u = 1 - s
                return base * (u * u) + ctrl * (2 * u * s) + tip * (s * s)
            }
            // A new leaf every 12% of charge, alternating sides, unfurling as it arrives.
            for (k in 0 until 8) {
                val needed = 0.08f + k * 0.12f
                val grow = smoothstep(needed, needed + 0.08f, level)
                if (grow <= 0f) continue
                val s = (0.18f + k * 0.1f).coerceAtMost(0.92f)
                val p = stemAt(s)
                val side = if (k % 2 == 0) 1f else -1f
                val leaf = unit * 0.075f * grow * (1f - k * 0.04f)
                val angle = side * (55f - k * 3f) + sin(t * 0.8f + k) * 4f - droop * side * 40f
                rotate(angle, p) {
                    val lp = Path().apply {
                        moveTo(p.x, p.y)
                        quadraticTo(p.x + leaf * 0.5f, p.y - leaf * 0.45f, p.x, p.y - leaf)
                        quadraticTo(p.x - leaf * 0.5f, p.y - leaf * 0.45f, p.x, p.y)
                        close()
                    }
                    drawPath(lp, Brush.verticalGradient(listOf(Color(0xFF9BE07A), Color(0xFF3E9A48)), p.y - leaf, p.y))
                    drawLine(Color(0xFF2E6A2E).copy(alpha = 0.6f), p, Offset(p.x, p.y - leaf * 0.85f), strokeWidth = density)
                }
            }
            // A flower opens when you're nearly full.
            val bloom = smoothstep(0.9f, 1f, level)
            if (bloom > 0f) {
                for (k in 0 until 7) {
                    val a = 360f * k / 7 + t * 2f
                    rotate(a, tip) {
                        drawOval(Brush.verticalGradient(listOf(Color(0xFFFFE07A), Color(0xFFFFB13D)), tip.y - unit * 0.06f * bloom, tip.y), Offset(tip.x - unit * 0.016f * bloom, tip.y - unit * 0.06f * bloom), Size(unit * 0.032f * bloom, unit * 0.06f * bloom))
                    }
                }
                drawCircle(Color(0xFF8A5A2A), unit * 0.016f * bloom, tip)
            } else {
                drawCircle(Color(0xFF9BE07A), unit * 0.008f, tip)
            }
            if (b.charging()) {
                // Little motes of light drifting up around the plant.
                for (k in 0 until 10) {
                    val p = (t * 0.05f + hash(k, 1)) % 1f
                    val o = Offset(cx + (hash(k, 2) - 0.5f) * unit * 0.4f + sin(t + k) * unit * 0.01f, base.y - p * unit * 0.55f)
                    drawCircle(Color(0xFFE8FFB0).copy(alpha = 0.5f * sin(p * PI.toFloat())), unit * 0.004f, o)
                }
            }
        }
        Box(
            if (landscape) Modifier.align(Alignment.CenterEnd).padding(end = maxWidth * 0.12f)
            else Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.06f),
        ) { BatteryCaption(ctx, b, small) }
    }
}

// ---------------------------------------------------------------- Power Cells

@Composable
fun PowerCellsSaver(ctx: SaverContext) {
    val b = ctx.shownBattery()
    val time = rememberFrameTime()
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val small = with(LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.045f).toSp() }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val unit = size.minDimension
            val cells = 10
            val full = (b.level / 10f)
            val color = batteryColor(b.level)
            // Horizontal battery in landscape, vertical in portrait.
            val long = if (landscape) size.width * 0.62f else size.height * 0.5f
            val short = unit * 0.32f
            val bw = if (landscape) long else short
            val bh = if (landscape) short else long
            val o = Offset((size.width - bw) / 2, (size.height - bh) / 2 - if (landscape) unit * 0.08f else unit * 0.1f)
            val shell = RoundRect(o.x, o.y, o.x + bw, o.y + bh, CornerRadius(unit * 0.04f))
            drawRoundRect(Color.White.copy(alpha = 0.3f), o, Size(bw, bh), CornerRadius(unit * 0.04f), style = Stroke(3f * density))
            if (landscape) drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(o.x + bw + unit * 0.01f, o.y + bh * 0.32f), Size(unit * 0.03f, bh * 0.36f), CornerRadius(unit * 0.01f))
            else drawRoundRect(Color.White.copy(alpha = 0.3f), Offset(o.x + bw * 0.32f, o.y - unit * 0.04f), Size(bw * 0.36f, unit * 0.03f), CornerRadius(unit * 0.01f))
            val pad = unit * 0.025f
            val gap = unit * 0.012f
            val sweep = (t * 0.9f) % (cells + 2f)
            for (i in 0 until cells) {
                val cell = if (landscape) {
                    val cw = (bw - pad * 2 - gap * (cells - 1)) / cells
                    Rect(o.x + pad + i * (cw + gap), o.y + pad, o.x + pad + i * (cw + gap) + cw, o.y + bh - pad)
                } else {
                    val ch = (bh - pad * 2 - gap * (cells - 1)) / cells
                    val y = o.y + bh - pad - (i + 1) * ch - i * gap
                    Rect(o.x + pad, y, o.x + bw - pad, y + ch)
                }
                val fill = (full - i).coerceIn(0f, 1f)
                val pulse = if (b.charging() && i >= full.toInt()) (1f - kotlin.math.abs(sweep - (i - full.toInt()) - 1f)).coerceIn(0f, 1f) else 0f
                drawRoundRect(Color.White.copy(alpha = 0.05f), cell.topLeft, cell.size, CornerRadius(unit * 0.012f))
                if (fill > 0f) {
                    drawRoundRect(Brush.radialGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), cell.center, cell.maxDimension), cell.inflate(unit * 0.02f).topLeft, cell.inflate(unit * 0.02f).size, CornerRadius(unit * 0.02f), blendMode = BlendMode.Plus)
                    drawRoundRect(color.copy(alpha = 0.35f + 0.65f * fill), cell.topLeft, cell.size, CornerRadius(unit * 0.012f))
                }
                if (pulse > 0f) drawRoundRect(color.copy(alpha = 0.35f * pulse), cell.topLeft, cell.size, CornerRadius(unit * 0.012f))
            }
            if (b.charging()) {
                // Lightning bolt, gently pulsing.
                val c = Offset(o.x + bw / 2, o.y + bh / 2)
                val s = unit * 0.07f * (1f + 0.06f * sin(t * 3f))
                val bolt = Path().apply {
                    moveTo(c.x + s * 0.15f, c.y - s)
                    lineTo(c.x - s * 0.45f, c.y + s * 0.1f)
                    lineTo(c.x - s * 0.02f, c.y + s * 0.1f)
                    lineTo(c.x - s * 0.15f, c.y + s)
                    lineTo(c.x + s * 0.45f, c.y - s * 0.1f)
                    lineTo(c.x + s * 0.02f, c.y - s * 0.1f)
                    close()
                }
                drawPath(bolt, Color.Black.copy(alpha = 0.55f), style = Stroke(5f * density))
                drawPath(bolt, Color.White)
            }
            val label = measurer.measure("${b.level}%", TextStyle(color = color, fontSize = (unit * 0.1f).toSp(), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold))
            drawText(label, topLeft = Offset((size.width - label.size.width) / 2f, shell.bottom + unit * 0.06f))
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = maxHeight * 0.07f)) {
            BatteryCaption(ctx, b, small, showPercent = false)
        }
    }
}
