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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.pridgey.beddery.data.Astronomy
import dev.pridgey.beddery.savers.MoonRender
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.SoftDot
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.TAU
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.layer
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.rememberNoiseBrush
import dev.pridgey.beddery.savers.ridge
import dev.pridgey.beddery.savers.stamp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Seconds for one full turn of the light: a slow, steady sweep, like breathing. */
private const val TURN = 14f

private class Coast(size: Size) {
    val w = size.width
    val h = size.height
    val landscape = w > h
    val horizon = h * if (landscape) 0.56f else 0.47f
    /** Where the headland meets the sea. */
    val shore = h * if (landscape) 0.74f else 0.63f
    val plateau = h * if (landscape) 0.46f else 0.4f
    val cliffX = w * if (landscape) 0.68f else 0.55f
    val towerX = w * if (landscape) 0.82f else 0.78f
    val towerH = size.minDimension * if (landscape) 0.3f else 0.36f
    val towerW = towerH * 0.17f
    val lantern = Offset(towerX, plateau - towerH * 1.06f)
    val moon = Offset(w * if (landscape) 0.2f else 0.24f, h * if (landscape) 0.2f else 0.14f)

    /** The headland: a steep, broken cliff with a grassy top. */
    val land: Path = Path().apply {
        val rough = ridge(64, 9, 0.62f)
        moveTo(cliffX - w * 0.1f, shore)
        val steps = 22
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            // Steep at the bottom, rounding over at the top.
            val x = cliffX - w * 0.1f + (w * 0.1f + (towerX - cliffX) * 0.25f) * f.pow(1.6f)
            val y = shore - (shore - plateau) * (1 - (1 - f).pow(2.2f)) + (rough[i] - 0.5f) * h * 0.018f * (1 - f)
            lineTo(x, y)
        }
        for (i in 0..14) {
            val x = cliffX + (towerX - cliffX) * 0.25f + (w - cliffX - (towerX - cliffX) * 0.25f) * i / 14f
            lineTo(x, plateau + (rough[30 + i] - 0.5f) * h * 0.006f)
        }
        lineTo(w, shore)
        // The waterline, a little ragged.
        for (i in 0..20) {
            val x = w - (w - (cliffX - w * 0.1f)) * i / 20f
            lineTo(x, shore + (rough[40 + i] - 0.5f) * h * 0.006f)
        }
        close()
    }
    /** Rocks at the foot of the cliff. */
    val rocks: List<Path> = (0 until 5).map { i ->
        val cx = cliffX - w * (0.05f + 0.13f * hash(i * 3 + 1))
        val base = shore + h * (0.004f + 0.035f * hash(i * 7 + 2))
        val r = size.minDimension * (0.018f + 0.03f * hash(i * 5 + 3))
        Path().apply {
            // A rounded, slightly lumpy boulder sitting in the water.
            val n = 10
            moveTo(cx - r * 1.2f, base)
            for (k in 1 until n) {
                val f = k / n.toFloat()
                val bump = 0.75f + 0.35f * hash(i * 11 + k)
                lineTo(cx - r * 1.2f + 2.4f * r * f, base - r * 0.85f * bump * kotlin.math.sin(f * PI).toFloat().pow(0.6f))
            }
            lineTo(cx + r * 1.2f, base)
            close()
        }
    }
    val rockBases: List<Offset> = (0 until 5).map { i ->
        Offset(cliffX - w * (0.05f + 0.13f * hash(i * 3 + 1)), shore + h * (0.004f + 0.035f * hash(i * 7 + 2)))
    }
}

/** A lighthouse on a headland, its beam sweeping slowly round over a dark sea. */
@Composable
fun LighthouseSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val grain = rememberGrainBrush()
    val clouds = rememberNoiseBrush(256, 4, 5, seed = 19, contrast = 1.8f)
    val rock = rememberNoiseBrush(128, 8, 4, seed = 27, contrast = 1.6f)
    val stars = remember { StarSky(160, 41, 0f, 0.5f) }
    val coast = remember { SizeCache { Coast(it) } }
    val shipAt = remember { floatArrayOf(25f) }
    val cooldown = remember { Cooldown(45_000) }
    OnSaverTap { if (cooldown.tryAcquire() && time.value - shipAt[0] > 70f) shipAt[0] = time.value }
    val tower = remember { Path() }
    val phase = remember(ctx.now.dayOfYear) { Astronomy.moonPhase(java.time.Instant.now()) }
    val moonImage = remember(phase) { MoonRender.render(128, if (phase < 0.08 || phase > 0.92) 0.5 else phase, false).asImageBitmap() }

    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val c = coast.get(size)
        val w = size.width
        val h = size.height
        val unit = size.minDimension
        // A ship passes now and then, or when you tap.
        if (t - shipAt[0] > 240f) shipAt[0] = t + 30f
        val angle = (t / TURN) * TAU + 1.2f

        // Sky and sea.
        drawRect(Brush.verticalGradient(0f to Color(0xFF02040A), 0.7f to Color(0xFF071222), 1f to Color(0xFF0E1D30), startY = 0f, endY = c.horizon), size = Size(w, c.horizon + 1))
        stars.draw(this, t, alpha = 0.8f, horizonFade = 0.5f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFBFD4FF).copy(alpha = 0.16f), Color.Transparent), c.moon, unit * 0.35f), unit * 0.35f, c.moon)
        val mr = unit * 0.032f
        drawCircle(Color.Black, mr * 0.98f, c.moon)
        drawImage(moonImage, dstOffset = IntOffset((c.moon.x - mr).toInt(), (c.moon.y - mr).toInt()), dstSize = IntSize((mr * 2).toInt(), (mr * 2).toInt()), filterQuality = FilterQuality.High)
        // Drifting cloud, edged by moonlight.
        val cb = Rect(0f, c.horizon * 0.05f, w, c.horizon * 0.75f)
        layer(cb) {
            drawRect(clouds.place(w * 1.3f, cb.height * 1.2f, dx = t * 4f, dy = cb.top), cb.topLeft, cb.size, alpha = 0.5f, colorFilter = ColorFilter.tint(Color(0xFF2A3A55), BlendMode.Modulate))
            drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent, startY = cb.top, endY = cb.bottom), cb.topLeft, cb.size, blendMode = BlendMode.DstIn)
        }
        drawRect(Brush.verticalGradient(listOf(Color(0xFF0A1626), Color(0xFF02050A)), c.horizon, h), Offset(0f, c.horizon), Size(w, h - c.horizon))

        // Moonlight on the water: a broken path of glints.
        for (k in 0 until 70) {
            val near = k / 70f
            val y = c.horizon + (h - c.horizon) * near * near + 2f
            val spread = unit * (0.02f + 0.16f * near)
            val x = c.moon.x + (hash(k * 7) - 0.5f) * 2f * spread + noise1(k + t * 0.4f, 3) * spread * 0.3f
            val half = spread * (0.15f + 0.3f * hash(k * 13))
            val a = (0.35f - 0.2f * near) * (0.5f + 0.5f * noise1(k * 1.7f + t * 1.3f, 8))
            drawLine(Color(0xFFDCE6FF).copy(alpha = a.coerceIn(0f, 1f)), Offset(x - half, y), Offset(x + half, y), strokeWidth = density * (0.8f + near * 1.5f), cap = StrokeCap.Round)
        }
        // Long swells across the sea.
        for (k in 0 until 40) {
            val near = k / 40f
            val y = c.horizon + (h - c.horizon) * near * near + 3f
            val x = ((hash(k * 3) * w + t * (6f + near * 18f)) % (w * 1.3f)) - w * 0.15f
            val len = w * (0.05f + 0.15f * near)
            drawLine(Color(0xFF6A88B0).copy(alpha = 0.05f + 0.07f * near), Offset(x - len, y), Offset(x + len, y), strokeWidth = density * (0.6f + near))
        }

        drawShip(c, t - shipAt[0], unit)

        // The beams, behind the tower when pointing away.
        val beams = listOf(angle)
        for (a in beams) if (cos(a) < 0f) drawBeam(c, a, unit)

        // The headland and its reflection.
        withTransform({ scale(1f, -0.5f, pivot = Offset(0f, c.shore)) }) {
            drawPath(c.land, Color(0xFF03050A).copy(alpha = 0.75f))
        }
        drawPath(c.land, Color(0xFF070A10))
        clipPath(c.land) {
            // Rough rock, lit from the moon's side, darker toward the sea.
            drawRect(rock.place(unit * 0.22f, unit * 0.5f), Offset(c.cliffX - w * 0.12f, c.plateau), Size(w, c.shore - c.plateau), alpha = 0.28f, colorFilter = ColorFilter.tint(Color(0xFF1C2638), BlendMode.Modulate))
            // Weathered strata running across the cliff face.
            for (k in 0 until 7) {
                val y = c.plateau + (c.shore - c.plateau) * (0.15f + k * 0.12f)
                drawLine(Color(0xFF7A90B8).copy(alpha = 0.05f), Offset(c.cliffX - w * 0.12f, y + w * 0.02f), Offset(w, y - w * 0.03f), strokeWidth = density)
            }
            drawRect(Brush.horizontalGradient(listOf(Color(0xFF6A84B0).copy(alpha = 0.14f), Color.Transparent), c.cliffX - w * 0.1f, c.cliffX + w * 0.08f), Offset(0f, c.plateau - h * 0.02f), Size(w, c.shore - c.plateau + h * 0.04f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), c.plateau, c.shore), Offset(0f, c.plateau - h * 0.02f), Size(w, c.shore - c.plateau + h * 0.04f))
            // Grass along the top.
            drawRect(Brush.verticalGradient(listOf(Color(0xFF14201A), Color.Transparent), c.plateau - h * 0.01f, c.plateau + h * 0.025f), Offset(0f, c.plateau - h * 0.012f), Size(w, h * 0.04f))
        }
        // Rocks with surf washing round them, and a line of foam along the shore.
        for ((i, rk) in c.rocks.withIndex()) {
            drawPath(rk, Color(0xFF05070C))
            drawPath(rk, Color(0xFF8AA0C8).copy(alpha = 0.12f), style = androidx.compose.ui.graphics.drawscope.Stroke(density))
            val base = c.rockBases[i]
            val foam = 0.5f + 0.5f * sin(t * 0.8f + i * 1.9f)
            stamp(SoftDot, base, unit * 0.12f * (0.8f + 0.4f * foam), unit * 0.012f, alpha = 0.22f * foam, colorFilter = ColorFilter.tint(Color(0xFFCAD8EE), BlendMode.Modulate))
        }
        for (k in 0 until 14) {
            val x = c.cliffX - w * 0.08f + (w - c.cliffX + w * 0.08f) * (k + 0.5f) / 14f
            val surge = 0.5f + 0.5f * sin(t * 0.7f - k * 0.9f)
            stamp(SoftDot, Offset(x, c.shore + h * 0.003f), w * 0.1f, h * 0.008f, alpha = 0.08f + 0.16f * surge, colorFilter = ColorFilter.tint(Color(0xFFCAD8EE), BlendMode.Modulate))
        }
        // The lamp's reflection shimmering on the water.
        for (k in 0 until 14) {
            val y = c.shore + h * 0.01f + k * h * 0.012f
            val half = c.towerW * (0.2f + 0.5f * hash(k * 3)) * (0.6f + 0.4f * noise1(t * 1.2f + k, 2))
            drawLine(Color(0xFFFFE6B8).copy(alpha = 0.16f * (1f - k / 14f)), Offset(c.towerX - half, y), Offset(c.towerX + half, y), strokeWidth = density * 1.2f)
        }

        // Keeper's cottage with a warm window.
        val cot = Offset(c.towerX + c.towerW * 1.6f, c.plateau)
        val cw = c.towerH * 0.42f
        val ch = c.towerH * 0.2f
        drawRect(Color(0xFF0B0F16), Offset(cot.x, cot.y - ch), Size(cw, ch))
        val roof = Path().apply {
            moveTo(cot.x - cw * 0.06f, cot.y - ch)
            lineTo(cot.x + cw * 0.5f, cot.y - ch * 1.75f)
            lineTo(cot.x + cw * 1.06f, cot.y - ch)
            close()
        }
        drawPath(roof, Color(0xFF070A0F))
        val win = Rect(cot.x + cw * 0.18f, cot.y - ch * 0.7f, cot.x + cw * 0.34f, cot.y - ch * 0.3f)
        drawRect(Color(0xFFFFBE6E).copy(alpha = 0.85f), win.topLeft, win.size)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB060).copy(alpha = 0.18f), Color.Transparent), win.center, cw * 0.5f), cw * 0.5f, win.center, blendMode = BlendMode.Plus)

        drawTower(c, tower, unit)

        // Beams pointing toward us, in front of the tower.
        for (a in beams) if (cos(a) >= 0f) drawBeam(c, a, unit)

        // Lamp and glare: brightest as a beam swings straight at you.
        val facing = beams.maxOf { max(0f, cos(it)) }
        val glare = facing.pow(24f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2D0).copy(alpha = 0.55f), Color.Transparent), c.lantern, c.towerW * 1.1f), c.towerW * 1.1f, c.lantern, blendMode = BlendMode.Plus)
        if (glare > 0.01f) {
            val gr = unit * (0.15f + 0.45f * glare)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4DA).copy(alpha = 0.75f * glare), Color(0xFFFFD9A0).copy(alpha = 0.18f * glare), Color.Transparent), c.lantern, gr), gr, c.lantern, blendMode = BlendMode.Plus)
            // A soft horizontal flare.
            stamp(SoftDot, c.lantern, unit * 1.4f * glare, unit * 0.02f, alpha = 0.5f * glare, blendMode = BlendMode.Plus)
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), Offset(w / 2, h / 2), size.maxDimension * 0.75f))
        drawGrain(grain, 0.06f)
    }
}

/** One beam: a wedge of light whose apparent length depends on how far round it points. */
private fun DrawScope.drawBeam(c: Coast, a: Float, unit: Float) {
    val side = sin(a) // + right, - left
    val toward = cos(a) // + toward viewer
    val reach = (if (side < 0) c.lantern.x else c.w - c.lantern.x) + c.w * 0.4f
    val len = reach * abs(side).pow(0.8f)
    if (len < 4f) return
    val spread = unit * (0.02f + 0.1f * abs(toward)) + len * 0.06f
    val dir = if (side < 0) -1f else 1f
    val end = Offset(c.lantern.x + dir * len, c.lantern.y + len * 0.02f)
    val strength = if (toward >= 0) 0.32f + 0.2f * toward else 0.22f + 0.1f * toward
    val beam = Path().apply {
        moveTo(c.lantern.x, c.lantern.y - c.towerW * 0.12f)
        lineTo(end.x, end.y - spread / 2)
        lineTo(end.x, end.y + spread / 2)
        lineTo(c.lantern.x, c.lantern.y + c.towerW * 0.12f)
        close()
    }
    drawPath(
        beam,
        Brush.horizontalGradient(
            0f to Color(0xFFFFF0CC).copy(alpha = strength), 0.5f to Color(0xFFFFE6B8).copy(alpha = strength * 0.35f), 1f to Color.Transparent,
            startX = c.lantern.x, endX = end.x,
        ),
        blendMode = BlendMode.Plus,
    )
    // Where the beam lights the sea far out, a faint sheen.
    val seaY = c.horizon + (c.h - c.horizon) * 0.08f
    val mid = Offset((c.lantern.x + end.x) / 2, seaY)
    stamp(SoftDot, mid, len * 0.9f, c.h * 0.03f, alpha = strength * 0.25f, colorFilter = ColorFilter.tint(Color(0xFFBFD0F0), BlendMode.Modulate), blendMode = BlendMode.Plus)
}

private fun DrawScope.drawTower(c: Coast, path: Path, unit: Float) {
    val base = Offset(c.towerX, c.plateau + c.h * 0.004f)
    val top = c.plateau - c.towerH
    val bw = c.towerW
    val tw = c.towerW * 0.68f
    path.reset()
    path.moveTo(base.x - bw / 2, base.y)
    path.lineTo(base.x - tw / 2, top)
    path.lineTo(base.x + tw / 2, top)
    path.lineTo(base.x + bw / 2, base.y)
    path.close()
    // Painted bands, lit faintly by the moon from the left.
    drawPath(path, Color(0xFF9AA4B4))
    clipPath(path) {
        val bands = 4
        for (i in 0 until bands) {
            val y0 = top + (base.y - top) * (i * 2 + 1) / (bands * 2f)
            drawRect(Color(0xFF6A2A2E), Offset(base.x - bw, y0), Size(bw * 2, (base.y - top) / (bands * 2f)))
        }
        drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.75f)), base.x - bw / 2, base.x + bw / 2), Offset(base.x - bw, top), Size(bw * 2, base.y - top))
        drawRect(Color.Black.copy(alpha = 0.45f), Offset(base.x - bw, top), Size(bw * 2, base.y - top))
        // A small window halfway up.
        drawRect(Color(0xFFFFC27A).copy(alpha = 0.5f), Offset(base.x - bw * 0.06f, top + (base.y - top) * 0.45f), Size(bw * 0.12f, bw * 0.2f))
    }
    // Gallery, lantern room and cap.
    val gy = top
    drawRect(Color(0xFF0A0C10), Offset(base.x - tw * 0.85f, gy - bw * 0.08f), Size(tw * 1.7f, bw * 0.1f))
    for (k in 0..6) {
        val x = base.x - tw * 0.8f + tw * 1.6f * k / 6f
        drawLine(Color(0xFF0A0C10), Offset(x, gy - bw * 0.08f), Offset(x, gy - bw * 0.3f), strokeWidth = density)
    }
    drawLine(Color(0xFF0A0C10), Offset(base.x - tw * 0.8f, gy - bw * 0.3f), Offset(base.x + tw * 0.8f, gy - bw * 0.3f), strokeWidth = density)
    val room = Rect(base.x - tw * 0.42f, c.lantern.y - bw * 0.3f, base.x + tw * 0.42f, gy - bw * 0.08f)
    drawRect(Color(0xFFFFE9B8), room.topLeft, room.size)
    drawRect(Brush.radialGradient(listOf(Color.White, Color(0xFFFFD08A)), Offset(room.center.x, room.center.y), room.width), room.topLeft, room.size)
    for (k in 1..2) {
        val x = room.left + room.width * k / 3f
        drawLine(Color(0xFF14100A).copy(alpha = 0.7f), Offset(x, room.top), Offset(x, room.bottom), strokeWidth = density)
    }
    val cap = Path().apply {
        moveTo(room.left - bw * 0.08f, room.top)
        lineTo(base.x, room.top - bw * 0.42f)
        lineTo(room.right + bw * 0.08f, room.top)
        close()
    }
    drawPath(cap, Color(0xFF0A0C10))
    drawLine(Color(0xFF0A0C10), Offset(base.x, room.top - bw * 0.42f), Offset(base.x, room.top - bw * 0.6f), strokeWidth = density * 1.5f)
}

/** A small ship crossing far out, lights on, over about a minute and a half. */
private fun DrawScope.drawShip(c: Coast, age: Float, unit: Float) {
    if (age < 0f || age > 95f) return
    val f = age / 95f
    val fade = (minOf(f, 1f - f) * 8f).coerceIn(0f, 1f)
    val x = -c.w * 0.1f + (c.cliffX - c.w * 0.06f + c.w * 0.1f) * f
    val y = c.horizon + c.h * 0.006f
    val s = unit * 0.05f
    val hull = Path().apply {
        moveTo(x - s, y - s * 0.12f)
        lineTo(x + s * 1.05f, y - s * 0.12f)
        lineTo(x + s * 0.85f, y + s * 0.06f)
        lineTo(x - s * 0.9f, y + s * 0.06f)
        close()
        addRect(Rect(x - s * 0.55f, y - s * 0.32f, x + s * 0.15f, y - s * 0.12f))
        addRect(Rect(x - s * 0.35f, y - s * 0.46f, x - s * 0.05f, y - s * 0.32f))
    }
    drawPath(hull, Color(0xFF03050A).copy(alpha = fade))
    // Cabin lights, a masthead light and a green starboard light.
    for (k in 0 until 4) drawCircle(Color(0xFFFFD08A).copy(alpha = 0.8f * fade), s * 0.025f, Offset(x - s * 0.48f + k * s * 0.17f, y - s * 0.22f))
    val mast = Offset(x - s * 0.2f, y - s * 0.7f)
    drawLine(Color(0xFF03050A).copy(alpha = fade), Offset(mast.x, y - s * 0.46f), mast, strokeWidth = density)
    drawCircle(Color.White.copy(alpha = fade), s * 0.03f, mast)
    drawCircle(Color(0xFF6BFFB0).copy(alpha = 0.9f * fade), s * 0.03f, Offset(x + s * 0.6f, y - s * 0.16f))
    stamp(SoftDot, mast, s * 0.4f, alpha = 0.4f * fade, blendMode = BlendMode.Plus)
    // Reflections.
    for (k in 0 until 4) {
        val rx = x - s * 0.48f + k * s * 0.17f
        drawLine(Color(0xFFFFD08A).copy(alpha = 0.18f * fade), Offset(rx, y + s * 0.1f), Offset(rx, y + s * 0.5f), strokeWidth = density)
    }
}
