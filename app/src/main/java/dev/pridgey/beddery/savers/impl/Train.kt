package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.pridgey.beddery.savers.MoonRender
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SizeCache
import dev.pridgey.beddery.savers.SoftDot
import dev.pridgey.beddery.savers.StarSky
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.stamp
import kotlin.math.floor
import kotlin.math.sin

private class Compartment(size: Size) {
    val w = size.width
    val h = size.height
    val landscape = w > h
    val glass: Rect = if (landscape) Rect(w * 0.1f, h * 0.08f, w * 0.9f, h * 0.7f) else Rect(w * 0.07f, h * 0.1f, w * 0.93f, h * 0.6f)
    val radius = size.minDimension * 0.06f
    val window = RoundRect(glass, CornerRadius(radius))
    val windowPath = Path().apply { addRoundRect(window) }
    val wall = Path().apply {
        addRect(Rect(0f, 0f, w, h))
        addRoundRect(RoundRect(glass.inflate(size.minDimension * 0.022f), CornerRadius(radius * 1.25f)))
        fillType = PathFillType.EvenOdd
    }
    val table = Rect(0f, glass.bottom + size.minDimension * 0.05f, w, glass.bottom + size.minDimension * 0.085f)
    val lamp = Offset(glass.right - glass.width * 0.1f, table.top)
    /** Horizon inside the window. */
    val horizon = glass.top + glass.height * 0.62f
}

private val Night = Color(0xFF03060D)

/** The view from a night train: moonlit hills, sleeping villages, telegraph poles and the odd station going by. */
@Composable
fun TrainSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val grain = rememberGrainBrush()
    val stars = remember { StarSky(160, 23, 0.05f, 0.45f) }
    val room = remember { SizeCache { Compartment(it) } }
    val far = remember { Path() }
    val mid = remember { Path() }
    val near = remember { Path() }
    val moonImage = remember { MoonRender.render(128, 0.55, false).asImageBitmap() }
    // A tap sounds the horn, in spirit: the lamp flickers as the train rattles over points.
    val rattle = remember { floatArrayOf(-100f) }
    val cooldown = remember { Cooldown(20_000) }
    OnSaverTap { if (cooldown.tryAcquire()) rattle[0] = time.value }

    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val c = room.get(size)
        val g = c.glass
        val unit = size.minDimension
        val since = t - rattle[0]
        val shake = if (since in 0f..2.5f) sin(since * 40f) * (1f - since / 2.5f) * unit * 0.004f else 0f
        // The carriage sways gently; the world outside bobs against the window.
        val sway = sin(t * 1.4f) * unit * 0.0025f + noise1(t * 0.7f, 4) * unit * 0.003f + shake
        val travel = t * unit * 0.6f // distance covered, in pixels at the nearest layer

        drawRect(Night)
        clipPath(c.windowPath) {
            translate(0f, sway) {
                drawOutside(c, t, travel, unit, far, mid, near, stars, moonImage)
            }
            // The lamp reflected in the glass, and a faint sheen.
            val refl = Offset(c.lamp.x - g.width * 0.04f, g.bottom - g.height * 0.2f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB46A).copy(alpha = 0.14f), Color.Transparent), refl, unit * 0.18f), unit * 0.18f, refl, blendMode = BlendMode.Plus)
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.035f), Color.Transparent, Color.White.copy(alpha = 0.02f), Color.Transparent), g.topLeft, g.bottomRight), g.topLeft, g.size)
        }
        drawInterior(c, t, since, unit)
        drawGrain(grain, 0.06f)
    }
}

private fun DrawScope.drawOutside(c: Compartment, t: Float, travel: Float, unit: Float, far: Path, mid: Path, near: Path, stars: StarSky, moonImage: ImageBitmap) {
    val g = c.glass
    val hz = c.horizon
    // Sky.
    drawRect(Brush.verticalGradient(0f to Color(0xFF040812), 0.75f to Color(0xFF0D1830), 1f to Color(0xFF1A2440), startY = g.top, endY = hz), Offset(g.left, g.top - 10f), Size(g.width, hz - g.top + 20f))
    stars.draw(this, t, alpha = 0.8f, horizonFade = 0.4f)
    val moon = Offset(g.left + g.width * 0.72f, g.top + g.height * 0.22f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFC8D8FF).copy(alpha = 0.18f), Color.Transparent), moon, unit * 0.3f), unit * 0.3f, moon)
    val mr = unit * 0.03f
    drawCircle(Color.Black, mr * 0.98f, moon)
    drawImage(moonImage, dstOffset = IntOffset((moon.x - mr).toInt(), (moon.y - mr).toInt()), dstSize = IntSize((mr * 2).toInt(), (mr * 2).toInt()), filterQuality = FilterQuality.High)

    // Distant mountains: barely moving.
    val step = 6f
    far.reset()
    far.moveTo(g.left, hz + 2)
    var x = g.left
    while (x <= g.right + step) {
        val wx = (x + travel * 0.02f) / unit
        val y = hz - unit * (0.06f + 0.07f * (noise1(wx * 2.2f, 3) * 0.6f + noise1(wx * 5.1f, 4) * 0.3f + 0.5f))
        far.lineTo(x, y)
        x += step
    }
    far.lineTo(g.right + step, hz + 2)
    far.close()
    drawPath(far, Color(0xFF0E172A))
    // Moonlight catching the ridges.
    drawPath(far, Color(0xFF3A4E78).copy(alpha = 0.18f), style = Stroke(density))

    // A long lake in the valley, catching the moon where the hills dip.
    val lakeTop = hz - unit * 0.012f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A2640), Color(0xFF0A1020)), lakeTop, hz + unit * 0.08f), Offset(g.left, lakeTop), Size(g.width, unit * 0.1f))
    for (k in 0 until 16) {
        val y = lakeTop + unit * 0.004f + k * unit * 0.005f
        val half = unit * (0.01f + 0.03f * hash(k * 5)) * (0.6f + 0.4f * noise1(t * 0.8f + k, 6))
        val x = moon.x + (hash(k * 9) - 0.5f) * unit * 0.04f
        drawLine(Color(0xFFDCE6FF).copy(alpha = 0.35f * (1f - k / 16f)), Offset(x - half, y), Offset(x + half, y), strokeWidth = density)
    }
    // Lights of a far-off town along the far shore.
    val townTravel = travel * 0.05f
    for (k in 0 until 60) {
        val wx = hash(k * 13) * unit * 6f
        val x = g.left + ((wx - townTravel) % (unit * 6f) + unit * 6f) % (unit * 6f) - unit * 0.5f
        if (x < g.left || x > g.right) continue
        if (hash((wx / (unit * 0.8f)).toInt() * 7) < 0.5f) continue
        drawCircle(Color(0xFFFFD49A).copy(alpha = 0.35f + 0.3f * hash(k)), density * 0.9f, Offset(x, lakeTop - unit * 0.002f - hash(k * 3) * unit * 0.006f))
    }

    // Rolling hills with villages.
    val midTravel = travel * 0.12f
    mid.reset()
    mid.moveTo(g.left, g.bottom)
    x = g.left
    while (x <= g.right + step) {
        mid.lineTo(x, midHill(x + midTravel, hz, unit))
        x += step
    }
    mid.lineTo(g.right + step, g.bottom)
    mid.close()
    drawPath(mid, Color(0xFF080D18))
    drawVillages(g, hz, midTravel, unit, t)

    // Mist lying in the valley.
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF8AA0C8).copy(alpha = 0.08f), Color.Transparent), hz - unit * 0.02f, hz + unit * 0.08f), Offset(g.left, hz - unit * 0.02f), Size(g.width, unit * 0.1f))

    // Trees and hedges along the line, rushing by.
    val nearTravel = travel * 0.8f
    val bank = g.bottom - g.height * 0.12f
    near.reset()
    near.moveTo(g.left, g.bottom + 10f)
    x = g.left
    val ns = 4f
    while (x <= g.right + ns) {
        val wx = (x + nearTravel) / unit
        val tree = (noise1(wx * 2.6f, 11) * 0.5f + 0.5f).let { it * it }
        val crown = noise1(wx * 22f, 13) * 0.5f + 0.5f
        // Rounded crowns on top of the trees, a bushy hedge between them.
        val bumps = kotlin.math.abs(noise1(wx * 30f, 14))
        val y = bank - unit * (0.012f + 0.02f * (noise1(wx * 14f, 12) * 0.5f + 0.5f) + 0.26f * tree * (0.8f + 0.2f * crown) + 0.025f * bumps * (0.3f + tree))
        near.lineTo(x, y)
        x += ns
    }
    near.lineTo(g.right + ns, g.bottom + 10f)
    near.close()
    drawPath(near, Color(0xFF020306))

    // Telegraph poles and their sagging wires, fastest of all.
    val poleTravel = travel * 1.4f
    val spacing = unit * 0.9f
    val first = floor((g.left + poleTravel) / spacing).toInt() - 1
    val top = g.top + g.height * 0.28f
    var prev: Offset? = null
    for (k in first..first + (g.width / spacing).toInt() + 3) {
        val px = k * spacing - poleTravel
        val p = Offset(px, top + unit * 0.01f * hash(k))
        drawLine(Color(0xFF010204), Offset(px, g.bottom), p, strokeWidth = unit * 0.012f)
        drawLine(Color(0xFF010204), p + Offset(-unit * 0.04f, unit * 0.012f), p + Offset(unit * 0.04f, unit * 0.012f), strokeWidth = unit * 0.006f)
        prev?.let { q ->
            for (wire in 0..1) {
                val dy = unit * 0.012f + wire * unit * 0.02f
                val a = Offset(q.x + (wire * 2 - 1) * unit * 0.03f, q.y + dy)
                val b = Offset(p.x + (wire * 2 - 1) * unit * 0.03f, p.y + dy)
                val wirePath = Path().apply {
                    moveTo(a.x, a.y)
                    quadraticTo((a.x + b.x) / 2, (a.y + b.y) / 2 + unit * 0.05f, b.x, b.y)
                }
                drawPath(wirePath, Color(0xFF010204), style = Stroke(density * 0.9f))
            }
        }
        prev = p
    }

    // Now and then a little country station slides by, lamps glowing.
    val stationEvery = unit * 260f
    val len = unit * 4f
    val start = g.right + unit * 0.3f - (nearTravel % stationEvery)
    if (start < g.right && start + len > g.left) {
        val platTop = g.bottom - g.height * 0.1f
        // The station building behind the platform.
        val bx = start + len * 0.35f
        val bw = unit * 0.9f
        val bh = g.height * 0.22f
        drawRect(Color(0xFF0A0C10), Offset(bx, platTop - bh), Size(bw, bh))
        val roof = Path().apply {
            moveTo(bx - unit * 0.05f, platTop - bh)
            lineTo(bx + bw / 2, platTop - bh - unit * 0.12f)
            lineTo(bx + bw + unit * 0.05f, platTop - bh)
            close()
        }
        drawPath(roof, Color(0xFF07080B))
        for (k in 0 until 3) {
            val wx = bx + bw * (0.15f + k * 0.28f)
            drawRect(Color(0xFFFFC27A).copy(alpha = 0.7f), Offset(wx, platTop - bh * 0.7f), Size(bw * 0.14f, bh * 0.32f))
        }
        drawRect(Color(0xFF0C0E12), Offset(start, platTop), Size(len, g.height * 0.1f))
        drawRect(Color(0xFF8A7A50).copy(alpha = 0.45f), Offset(start, platTop), Size(len, unit * 0.004f))
        for (k in 0 until 8) {
            val lx = start + unit * (0.25f + k * 0.5f)
            val lt = platTop - g.height * 0.34f
            drawLine(Color(0xFF020306), Offset(lx, platTop), Offset(lx, lt), strokeWidth = unit * 0.008f)
            val bulb = Offset(lx + unit * 0.02f, lt)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC879).copy(alpha = 0.45f), Color.Transparent), bulb, unit * 0.14f), unit * 0.14f, bulb, blendMode = BlendMode.Plus)
            drawCircle(Color(0xFFFFE6B8), unit * 0.007f, bulb)
            drawOval(Color(0xFFFFC879).copy(alpha = 0.1f), Offset(lx - unit * 0.12f, platTop - unit * 0.004f), Size(unit * 0.28f, unit * 0.02f))
        }
        val sign = Rect(start + len * 0.2f, platTop - g.height * 0.2f, start + len * 0.2f + unit * 0.3f, platTop - g.height * 0.15f)
        drawRect(Color(0xFF1A2A44), sign.topLeft, sign.size)
        drawRect(Color.White.copy(alpha = 0.35f), Offset(sign.left + sign.width * 0.15f, sign.center.y - unit * 0.002f), Size(sign.width * 0.7f, unit * 0.004f))
    }
}

private fun midHill(wx: Float, hz: Float, unit: Float): Float {
    val x = wx / unit
    val n = noise1(x * 1.1f, 21) * 0.5f + 0.5f
    return hz + unit * 0.03f - unit * (0.11f * n * n + 0.012f * noise1(x * 6f, 22) + 0.01f)
}

private fun DrawScope.drawVillages(g: Rect, hz: Float, travel: Float, unit: Float, t: Float) {
    // Villages sit in fixed stretches of the line; each is a handful of houses on the hillside.
    val seg = unit * 1.6f
    val first = floor((g.left + travel) / seg).toInt() - 1
    for (k in first..first + (g.width / seg).toInt() + 2) {
        if (hash(k * 17 + 3) < 0.45f) continue
        val houses = 2 + (hash(k * 5) * 6).toInt()
        for (j in 0 until houses) {
            val wx = k * seg + seg * (0.2f + 0.6f * hash(k * 31 + j * 7))
            val x = wx - travel
            if (x < g.left - unit * 0.05f || x > g.right + unit * 0.05f) continue
            val ground = midHill(wx, hz, unit) + unit * 0.012f
            val hw = unit * (0.026f + 0.016f * hash(k * 3 + j))
            val hh = hw * 0.8f
            val house = Path().apply {
                moveTo(x - hw / 2, ground)
                lineTo(x - hw / 2, ground - hh)
                lineTo(x, ground - hh - hw * 0.45f)
                lineTo(x + hw / 2, ground - hh)
                lineTo(x + hw / 2, ground)
                close()
            }
            drawPath(house, Color(0xFF05080F))
            if (hash(k * 13 + j * 3) > 0.35f) {
                val lit = Offset(x - hw * 0.15f, ground - hh * 0.55f)
                val flicker = 0.85f + 0.15f * sin(t * 0.3f + j)
                drawRect(Color(0xFFFFC27A).copy(alpha = 0.8f * flicker), lit, Size(hw * 0.22f, hh * 0.28f))
                stamp(SoftDot, lit, unit * 0.04f, alpha = 0.25f, colorFilter = ColorFilter.tint(Color(0xFFFFB060), BlendMode.Modulate), blendMode = BlendMode.Plus)
            }
        }
        // A street lamp in the bigger villages.
        if (houses > 4) {
            val wx = k * seg + seg * 0.5f
            val x = wx - travel
            val ground = midHill(wx, hz, unit) + unit * 0.012f
            val bulb = Offset(x, ground - unit * 0.035f)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFB866).copy(alpha = 0.35f), Color.Transparent), bulb, unit * 0.05f), unit * 0.05f, bulb, blendMode = BlendMode.Plus)
        }
    }
}

private fun DrawScope.drawInterior(c: Compartment, t: Float, since: Float, unit: Float) {
    val g = c.glass
    // Wood-panelled wall, warmed by the reading lamp.
    drawPath(c.wall, Brush.verticalGradient(listOf(Color(0xFF140D09), Color(0xFF0D0805)), 0f, c.h))
    val lampGlow = c.lamp - Offset(0f, unit * 0.08f)
    val flicker = if (since in 0f..2.5f) 0.75f + 0.25f * sin(since * 55f) else 1f
    val r = c.h * 0.6f
    clipPath(c.wall) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFF6A3E1E).copy(alpha = 0.45f * flicker), Color(0xFF2A160A).copy(alpha = 0.25f * flicker), Color.Transparent), lampGlow, r), r, lampGlow)
        // Panel seams.
        for (k in 1..5) {
            val x = c.w * k / 6f
            drawLine(Color.Black.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, c.h), strokeWidth = density)
            drawLine(Color(0xFF3A2414).copy(alpha = 0.2f), Offset(x + density, 0f), Offset(x + density, c.h), strokeWidth = density)
        }
    }
    // The window frame: brushed metal, lit on its lower edge.
    val frame = RoundRect(g.inflate(unit * 0.022f), CornerRadius(c.radius * 1.25f))
    drawRoundRect(Color(0xFF2A2420), Offset(frame.left, frame.top), Size(frame.width, frame.height), CornerRadius(c.radius * 1.25f), style = Stroke(unit * 0.012f))
    drawRoundRect(Color(0xFFFFC890).copy(alpha = 0.12f * flicker), g.topLeft - Offset(unit * 0.008f, unit * 0.008f), Size(g.width + unit * 0.016f, g.height + unit * 0.016f), CornerRadius(c.radius * 1.1f), style = Stroke(density * 1.2f))
    // A blind, rolled most of the way up.
    val blind = g.top + g.height * 0.07f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2A1E16), Color(0xFF1C140E)), g.top - unit * 0.02f, blind), Offset(g.left - unit * 0.02f, g.top - unit * 0.025f), Size(g.width + unit * 0.04f, blind - g.top + unit * 0.025f))
    drawRect(Color(0xFF3A2A1E), Offset(g.left + g.width * 0.45f, blind - unit * 0.004f), Size(g.width * 0.1f, unit * 0.01f))

    // Table under the window with the lamp.
    val tb = c.table
    drawRect(Brush.verticalGradient(listOf(Color(0xFF3A2416), Color(0xFF1A0F08)), tb.top, tb.bottom), tb.topLeft, tb.size)
    drawRect(Color(0xFFFFC890).copy(alpha = 0.2f * flicker), tb.topLeft, Size(tb.width, density * 1.2f))
    drawRect(Color.Black.copy(alpha = 0.5f), Offset(0f, tb.bottom), Size(c.w, unit * 0.02f))
    val pool = c.lamp
    drawOval(Brush.radialGradient(listOf(Color(0xFFFFB86A).copy(alpha = 0.3f * flicker), Color.Transparent), pool, unit * 0.3f), Offset(pool.x - unit * 0.3f, tb.top - unit * 0.004f), Size(unit * 0.6f, unit * 0.03f))
    // Lamp: a brass stem and a glowing shade.
    val lh = unit * 0.11f
    drawLine(Color(0xFF8A6A3A), pool, pool - Offset(0f, lh * 0.75f), strokeWidth = unit * 0.006f, cap = StrokeCap.Round)
    drawOval(Color(0xFF6A4E2A), Offset(pool.x - unit * 0.025f, pool.y - unit * 0.008f), Size(unit * 0.05f, unit * 0.012f))
    val shade = Path().apply {
        moveTo(pool.x - lh * 0.22f, pool.y - lh * 0.95f)
        lineTo(pool.x + lh * 0.22f, pool.y - lh * 0.95f)
        lineTo(pool.x + lh * 0.38f, pool.y - lh * 0.62f)
        lineTo(pool.x - lh * 0.38f, pool.y - lh * 0.62f)
        close()
    }
    drawPath(shade, Brush.verticalGradient(listOf(Color(0xFFFFD49A), Color(0xFFE89A50)), pool.y - lh, pool.y - lh * 0.6f), alpha = flicker)
    val bulb = Offset(pool.x, pool.y - lh * 0.62f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFDDA8).copy(alpha = 0.5f * flicker), Color.Transparent), bulb, unit * 0.14f), unit * 0.14f, bulb, blendMode = BlendMode.Plus)
}
