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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatLongDate
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.smoothstep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val PW = 150
private const val PH = 112

// Room size in iso units.
private const val RA = 36f
private const val RB = 36f
private const val RZ = 30f

/** How the room is lit at a given time of day. */
private class Light(hour: Float) {
    /** 1 in full daylight, 0 at night. */
    val day = smoothstep(5.5f, 8f, hour) * (1f - smoothstep(17.5f, 20f, hour))
    /** Sunrise or sunset glow, 0..1. */
    val golden = max(1f - abs(hour - 6.8f) / 1.3f, 1f - abs(hour - 18.6f) / 1.4f).coerceIn(0f, 1f)
    val lamp = hour >= 18f && hour < 23.5f || (hour >= 6.3f && hour < 7.5f)
    val screen = hour >= 19f && hour < 22.5f
    val ambient: Color = lerp(lerp(Color(0xFF3A4270), Color(0xFFFFFFFF), day), Color(0xFFFFC8A0), golden * 0.35f)
        .let { if (lamp && day < 0.5f) lerp(it, Color(0xFFB08A70), 0.45f) else it }
    val sky: List<Color> = when {
        golden > 0.2f && day < 0.9f -> listOf(Color(0xFF4A5A9A), Color(0xFFE88A70), Color(0xFFFFC88A))
        day > 0.5f -> listOf(Color(0xFF5AA8E8), Color(0xFF8ACCF2), Color(0xFFC8E8F8))
        else -> listOf(Color(0xFF060A1C), Color(0xFF0C1430), Color(0xFF16204A))
    }
}

private fun shade(c: Color, light: Color, k: Float = 1f) =
    Color((c.red * light.red * k).coerceIn(0f, 1f), (c.green * light.green * k).coerceIn(0f, 1f), (c.blue * light.blue * k).coerceIn(0f, 1f))

/** Draws a crisp pixel-art room into a small image. Coordinates are iso units: a (right-down), b (left-down), z (up). */
private class RoomPainter(private val scope: DrawScope, private val light: Light) {
    private val ox = PW / 2f
    private val oy = RZ + 4f

    fun p(a: Float, b: Float, z: Float) = Offset(ox + (a - b) * 2f, oy + (a + b) - z)

    fun px(x: Int, y: Int, c: Color, w: Int = 1, h: Int = 1) {
        scope.drawRect(c, Offset(x.toFloat(), y.toFloat()), Size(w.toFloat(), h.toFloat()))
    }

    /** Fills a convex polygon column by column, so edges stay crisp. */
    fun fill(points: List<Offset>, c: Color) {
        val minX = floor(points.minOf { it.x }).toInt()
        val maxX = ceil(points.maxOf { it.x }).toInt()
        for (x in minX until maxX) {
            val cx = x + 0.5f
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            for (i in points.indices) {
                val a = points[i]
                val b = points[(i + 1) % points.size]
                if ((cx >= min(a.x, b.x)) && (cx <= max(a.x, b.x)) && a.x != b.x) {
                    val y = a.y + (b.y - a.y) * (cx - a.x) / (b.x - a.x)
                    lo = min(lo, y)
                    hi = max(hi, y)
                }
            }
            if (lo > hi) continue
            val y0 = lo.roundToInt()
            val y1 = hi.roundToInt()
            if (y1 > y0) px(x, y0, c, 1, y1 - y0)
        }
    }

    fun line(from: Offset, to: Offset, c: Color) {
        var x0 = from.x.roundToInt()
        var y0 = from.y.roundToInt()
        val x1 = to.x.roundToInt()
        val y1 = to.y.roundToInt()
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            px(x0, y0, c)
            if (x0 == x1 && y0 == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x0 += sx }
            if (e2 <= dx) { err += dx; y0 += sy }
        }
    }

    /** A box with lit top, mid-tone right face and darker left face. */
    fun box(a: Float, b: Float, z: Float, da: Float, db: Float, dz: Float, base: Color, k: Float = 1f) {
        val a1 = a + da
        val b1 = b + db
        val z1 = z + dz
        fill(listOf(p(a, b1, z1), p(a1, b1, z1), p(a1, b1, z), p(a, b1, z)), shade(base, light.ambient, 0.72f * k))
        fill(listOf(p(a1, b, z1), p(a1, b1, z1), p(a1, b1, z), p(a1, b, z)), shade(base, light.ambient, 0.88f * k))
        fill(listOf(p(a, b, z1), p(a1, b, z1), p(a1, b1, z1), p(a, b1, z1)), shade(base, light.ambient, 1.06f * k))
    }

    fun c(base: Color, k: Float = 1f) = shade(base, light.ambient, k)
}

private fun paintRoom(img: ImageBitmap, hour: Float, minute: Int, accent: Color) {
    val light = Light(hour)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(PW.toFloat(), PH.toFloat())) {
        drawRect(Color.Transparent, blendMode = BlendMode.Clear)
        val r = RoomPainter(this, light)
        // Floor: warm planks.
        r.fill(listOf(r.p(0f, 0f, 0f), r.p(RA, 0f, 0f), r.p(RA, RB, 0f), r.p(0f, RB, 0f)), r.c(Color(0xFFB07A4A)))
        var b = 3f
        while (b < RB) {
            r.line(r.p(0f, b, 0f), r.p(RA, b, 0f), r.c(Color(0xFF8A5A34)))
            b += 3f
        }
        for (k in 0 until 24) {
            val a = 1f + hash(k * 3) * (RA - 2)
            val bb = 3f * (1 + (hash(k * 7) * 11).toInt())
            r.line(r.p(a, bb, 0f), r.p(a, bb + 3f, 0f), r.c(Color(0xFF8A5A34)))
        }
        // Back walls with a skirting board.
        r.fill(listOf(r.p(0f, 0f, RZ), r.p(0f, RB, RZ), r.p(0f, RB, 0f), r.p(0f, 0f, 0f)), r.c(Color(0xFFD8CBB4), 0.8f))
        r.fill(listOf(r.p(0f, 0f, RZ), r.p(RA, 0f, RZ), r.p(RA, 0f, 0f), r.p(0f, 0f, 0f)), r.c(Color(0xFFE8DCC4), 0.95f))
        r.fill(listOf(r.p(0f, 0f, 2f), r.p(0f, RB, 2f), r.p(0f, RB, 0f), r.p(0f, 0f, 0f)), r.c(Color(0xFF8A6A4A), 0.8f))
        r.fill(listOf(r.p(0f, 0f, 2f), r.p(RA, 0f, 2f), r.p(RA, 0f, 0f), r.p(0f, 0f, 0f)), r.c(Color(0xFF9A7A5A), 0.95f))
        // Wall tops, seen edge-on.
        r.fill(listOf(r.p(0f, 0f, RZ), r.p(0f, RB, RZ), r.p(-1.5f, RB, RZ), r.p(-1.5f, -1.5f, RZ)), r.c(Color(0xFF6A5A4A)))
        r.fill(listOf(r.p(0f, 0f, RZ), r.p(RA, 0f, RZ), r.p(RA, -1.5f, RZ), r.p(-1.5f, -1.5f, RZ)), r.c(Color(0xFF7A6A5A)))

        // Window on the left wall, showing the sky.
        val wb0 = 13f
        val wb1 = 27f
        val wz0 = 12f
        val wz1 = 24f
        for (z in wz0.toInt() until wz1.toInt()) {
            val f = (z - wz0) / (wz1 - wz0)
            val sky = when {
                f > 0.66f -> light.sky[0]
                f > 0.33f -> light.sky[1]
                else -> light.sky[2]
            }
            r.fill(listOf(r.p(0f, wb0, z + 1f), r.p(0f, wb1, z + 1f), r.p(0f, wb1, z.toFloat()), r.p(0f, wb0, z.toFloat())), sky)
        }
        // Sun or moon in the window.
        if (light.day > 0.5f) {
            val s = r.p(0f, wb0 + 4f, wz1 - 4f)
            r.px(s.x.toInt() - 1, s.y.toInt() - 1, Color(0xFFFFF2B0), 3, 3)
        } else {
            val m = r.p(0f, wb0 + 3.5f, wz1 - 3f)
            r.px(m.x.toInt() - 1, m.y.toInt() - 1, Color(0xFFF0F0E0), 3, 3)
            r.px(m.x.toInt() - 1, m.y.toInt() - 1, light.sky[0], 1, 1)
        }
        // Distant hills.
        for (k in 0..((wb1 - wb0) * 4).toInt()) {
            val bb = wb0 + k / 4f
            val hgt = 1.5f + 1.5f * sin(k * 0.35f) + hash(k) * 0.8f
            val top = r.p(0f, bb, wz0 + hgt)
            val bot = r.p(0f, bb, wz0)
            r.px(top.x.toInt(), top.y.toInt(), lerp(light.sky[0], Color(0xFF1A2A2A), 0.6f), 1, (bot.y - top.y).toInt().coerceAtLeast(1))
        }
        // Frame and cross bars.
        val frame = r.c(Color(0xFFF4EEE4), 0.85f)
        r.line(r.p(0f, wb0, wz0), r.p(0f, wb1, wz0), frame)
        r.line(r.p(0f, wb0, wz1), r.p(0f, wb1, wz1), frame)
        r.line(r.p(0f, wb0, wz0), r.p(0f, wb0, wz1), frame)
        r.line(r.p(0f, wb1, wz0), r.p(0f, wb1, wz1), frame)
        r.line(r.p(0f, (wb0 + wb1) / 2, wz0), r.p(0f, (wb0 + wb1) / 2, wz1), frame)
        r.line(r.p(0f, wb0, (wz0 + wz1) / 2), r.p(0f, wb1, (wz0 + wz1) / 2), frame)
        r.fill(listOf(r.p(0f, wb0 - 1, wz0), r.p(0f, wb1 + 1, wz0), r.p(1.5f, wb1 + 1, wz0), r.p(1.5f, wb0 - 1, wz0)), r.c(Color(0xFFF4EEE4)))

        // Sunlight or moonlight falling through the window onto the floor.
        val beam = if (light.day > 0.5f) Color(0xFFFFF0C0).copy(alpha = 0.22f) else Color(0xFF8AA8FF).copy(alpha = 0.12f)
        r.fill(listOf(r.p(6f, wb0 + 3, 0f), r.p(6f, wb1 + 3, 0f), r.p(16f, wb1 + 6, 0f), r.p(16f, wb0 + 6, 0f)), beam)

        // Poster and shelf on the right wall.
        r.fill(listOf(r.p(5f, 0f, 24f), r.p(12f, 0f, 24f), r.p(12f, 0f, 14f), r.p(5f, 0f, 14f)), r.c(Color(0xFF2E4A6A), 0.95f))
        r.fill(listOf(r.p(6f, 0f, 21f), r.p(11f, 0f, 21f), r.p(11f, 0f, 16f), r.p(6f, 0f, 16f)), r.c(Color(0xFFE8A060), 0.95f))
        r.fill(listOf(r.p(7f, 0f, 19f), r.p(10f, 0f, 19f), r.p(10f, 0f, 17f), r.p(7f, 0f, 17f)), r.c(Color(0xFFF4D080), 0.95f))
        r.box(14f, 0f, 21f, 12f, 3f, 1f, Color(0xFF8A5A34))
        val bookColors = listOf(Color(0xFFC8503A), Color(0xFF3A6AA8), Color(0xFFE8C060), Color(0xFF4A8A5A), Color(0xFF8A4A8A), Color(0xFFD8D0C0))
        var ba = 14.5f
        for ((i, bc) in bookColors.withIndex()) {
            val bh = 3f + (i % 3)
            r.box(ba, 0.5f, 22f, 1.4f, 2f, bh, bc)
            ba += 1.6f
        }
        r.box(ba + 1.5f, 0.5f, 22f, 2.5f, 2f, 2f, Color(0xFF7A9A6A))

        // Wall clock showing the real time.
        val cc = Offset(30f, 23f)
        val radius = 3.5f
        val ring = (0 until 24).map { i -> val a = i / 24f * 2 * PI.toFloat(); r.p(cc.x + cos(a) * radius, 0f, cc.y + sin(a) * radius) }
        r.fill(ring, r.c(Color(0xFFF8F4EC)))
        for (i in ring.indices) r.line(ring[i], ring[(i + 1) % ring.size], r.c(Color(0xFF3A3A3A)))
        val hourAngle = ((hour % 12f) / 12f) * 2 * PI.toFloat()
        val minAngle = (minute / 60f) * 2 * PI.toFloat()
        val centre = r.p(cc.x, 0f, cc.y)
        r.line(centre, r.p(cc.x + sin(hourAngle) * 2f, 0f, cc.y + cos(hourAngle) * 2f), Color(0xFF1A1A1A))
        r.line(centre, r.p(cc.x + sin(minAngle) * 3f, 0f, cc.y + cos(minAngle) * 3f), Color(0xFFC0392B))

        // Rug.
        r.fill(listOf(r.p(16f, 17f, 0f), r.p(30f, 17f, 0f), r.p(30f, 30f, 0f), r.p(16f, 30f, 0f)), r.c(accent, 0.75f))
        r.fill(listOf(r.p(17.5f, 18.5f, 0f), r.p(28.5f, 18.5f, 0f), r.p(28.5f, 28.5f, 0f), r.p(17.5f, 28.5f, 0f)), r.c(lerp(accent, Color.White, 0.35f), 0.8f))
        r.fill(listOf(r.p(20f, 21f, 0f), r.p(26f, 21f, 0f), r.p(26f, 26f, 0f), r.p(20f, 26f, 0f)), r.c(accent, 0.6f))

        // Desk, chair and computer against the right wall.
        r.box(20f, 1f, 0f, 1f, 1f, 11f, Color(0xFF6A4428))
        r.box(32f, 1f, 0f, 1f, 1f, 11f, Color(0xFF6A4428))
        r.box(20f, 7f, 0f, 1f, 1f, 11f, Color(0xFF6A4428))
        r.box(32f, 7f, 0f, 1f, 1f, 11f, Color(0xFF6A4428))
        r.box(19f, 0.5f, 11f, 15f, 9f, 1.2f, Color(0xFF9A6A40))
        r.box(23f, 1.5f, 12.2f, 7f, 1f, 7f, Color(0xFF2A2A30))
        val screen = if (light.screen) Color(0xFF7AB8FF) else Color(0xFF1A2230)
        r.fill(listOf(r.p(23.5f, 2.5f, 18.6f), r.p(29.5f, 2.5f, 18.6f), r.p(29.5f, 2.5f, 13f), r.p(23.5f, 2.5f, 13f)), screen)
        if (light.screen) {
            for (k in 0 until 4) r.line(r.p(24.5f, 2.5f, 17.5f - k * 1.2f), r.p(24.5f + 2f + hash(k) * 3f, 2.5f, 17.5f - k * 1.2f), Color(0xFFDDEEFF))
        }
        r.box(25.5f, 3f, 12.2f, 2f, 1f, 0.6f, Color(0xFF2A2A30))
        r.box(23f, 5f, 12.2f, 5f, 2f, 0.4f, Color(0xFFD8D8D8))
        r.box(31f, 4f, 12.2f, 1.2f, 1.2f, 1.6f, Color(0xFFE8E0D0))
        // Chair.
        r.box(24f, 11f, 6f, 6f, 5f, 1.2f, Color(0xFF3A5A7A))
        r.box(24f, 15f, 6f, 6f, 1f, 7f, Color(0xFF3A5A7A))
        r.box(26.5f, 13f, 0f, 1f, 1f, 6f, Color(0xFF2A2A2A))

        // Nightstand with a lamp.
        r.box(1f, 4f, 0f, 6f, 6f, 9f, Color(0xFF8A5A34))
        r.line(r.p(7f, 5f, 5f), r.p(7f, 9f, 5f), r.c(Color(0xFF5A3A1E)))
        r.box(3f, 6f, 9f, 2f, 2f, 1f, Color(0xFF6A6A6A))
        r.box(3.6f, 6.6f, 10f, 0.8f, 0.8f, 3f, Color(0xFF6A6A6A))
        val shadeColor = if (light.lamp) Color(0xFFFFE0A0) else Color(0xFFE8D8B8)
        r.box(2.2f, 5.2f, 13f, 3.6f, 3.6f, 3.5f, shadeColor, if (light.lamp) 1.25f else 1f)

        // Bed under the window: frame, mattress, pillow and blanket.
        r.box(1f, 11f, 0f, 13f, 1.5f, 12f, Color(0xFF7A4A2A))
        r.box(1f, 12.5f, 0f, 13f, 20f, 4f, Color(0xFF7A4A2A))
        r.box(1.5f, 12.5f, 4f, 12f, 19.5f, 3f, Color(0xFFF0EEE8))
        r.box(3f, 13f, 7f, 9f, 4f, 2f, Color(0xFFFFFFFF))
        r.box(1.2f, 18f, 6.5f, 12.6f, 14.2f, 1.6f, accent)
        for (k in 0 until 5) {
            val bb = 19.5f + k * 2.6f
            r.line(r.p(1.5f, bb, 8.1f), r.p(13.6f, bb, 8.1f), r.c(lerp(accent, Color.White, 0.4f)))
        }
        // The blanket draping over the side.
        r.fill(listOf(r.p(13.8f, 18f, 8.1f), r.p(13.8f, 32.2f, 8.1f), r.p(13.8f, 32.2f, 3f), r.p(13.8f, 18f, 3f)), r.c(accent, 0.8f))
        // A ginger cat curled up asleep on the blanket.
        val cat = r.p(7f, 26f, 8.1f)
        val sprite = listOf(
            "..o.o.......",
            ".ooooo.oo...",
            "ookoooooooo.",
            "ooooooooooso",
            ".ssssssssss.",
            "..tttttt....",
        )
        val catColors = mapOf('o' to Color(0xFFE8964A), 's' to Color(0xFFB8682A), 'k' to Color(0xFF4A2A14), 't' to Color(0xFFC87838))
        for ((row, line) in sprite.withIndex()) {
            for ((col, ch) in line.withIndex()) {
                val color = catColors[ch] ?: continue
                r.px(cat.x.toInt() - 6 + col, cat.y.toInt() - 5 + row, r.c(color))
            }
        }

        // A plant in the far corner and one by the desk.
        r.box(32f, 12f, 0f, 3f, 3f, 3f, Color(0xFFB0603A))
        for (k in 0 until 10) {
            val a = 33.5f + (hash(k * 3) - 0.5f) * 4f
            val bb = 13.5f + (hash(k * 7) - 0.5f) * 4f
            val z = 3f + hash(k * 11) * 6f
            val q = r.p(a, bb, z)
            r.px(q.x.toInt(), q.y.toInt(), r.c(if (k % 3 == 0) Color(0xFF6AB050) else Color(0xFF3A8A3A)), 2, 1)
        }
    }
}

/** A little pixel-art bedroom whose window, lamps and wall clock follow the real time of day. */
@Composable
fun TinyRoomSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val image = remember { ImageBitmap(PW, PH) }
    val hour = ctx.now.hour + ctx.now.minute / 60f
    val light = remember(ctx.now.hour, ctx.now.minute) { Light(hour) }
    // The room is repainted once a minute (or when the accent changes): only small touches animate.
    val painted = remember { arrayOfNulls<Any>(1) }
    val paintKey = Triple(ctx.now.hour, ctx.now.minute, ctx.accent)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val dens = LocalDensity.current
        val u = min(maxWidth, maxHeight)
        val clock = with(dens) { (u * if (landscape) 0.16f else 0.17f).toSp() }
        val body = with(dens) { (u * 0.04f).toSp() }

        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            if (painted[0] != paintKey) {
                paintRoom(image, hour, ctx.now.minute, ctx.accent)
                painted[0] = paintKey
            }
            val w = size.width
            val h = size.height
            val scale = floor(min(w * (if (landscape) 0.55f else 0.98f) / PW, h * (if (landscape) 0.9f else 0.52f) / PH)).coerceAtLeast(1f)
            val rw = PW * scale
            val rh = PH * scale
            val left = ((if (landscape) w * 0.66f else w / 2) - rw / 2).roundToInt().toFloat()
            val top = ((if (landscape) h * 0.5f else h * 0.6f) - rh / 2).roundToInt().toFloat()

            drawRect(lerp(Color(0xFF0A0C14), Color(0xFF1A2230), light.day))
            // A faint glow of the room on the dark around it.
            val roomCentre = Offset(left + rw / 2, top + rh * 0.55f)
            drawCircle(Brush.radialGradient(listOf(lerp(Color(0xFF3A2A20), Color(0xFF2A3A50), light.day).copy(alpha = 0.35f), Color.Transparent), roomCentre, rw * 0.6f), rw * 0.6f, roomCentre)
            drawImage(image, dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(rw.toInt(), rh.toInt()), filterQuality = FilterQuality.None)
            if (light.lamp) {
                val lamp = Offset(left + (PW / 2f + (4f - 7.6f) * 2f) * scale, top + (RZ + 4f + 11.6f - 14f) * scale)
                val flicker = 0.95f + 0.05f * sin(t * 3f)
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFD9A0).copy(alpha = 0.2f * flicker), Color(0xFFFFB86A).copy(alpha = 0.06f), Color.Transparent), lamp, rw * 0.3f), rw * 0.3f, lamp, blendMode = BlendMode.Plus)
            }
            if (light.screen) {
                val glow = Offset(left + (PW / 2f + (26.5f - 2.5f) * 2f) * scale, top + (RZ + 4f + 29f - 16f) * scale)
                drawCircle(Brush.radialGradient(listOf(Color(0xFF7AB8FF).copy(alpha = 0.12f), Color.Transparent), glow, rw * 0.3f), rw * 0.3f, glow, blendMode = BlendMode.Plus)
            }
            // Stars twinkling in the window at night.
            if (light.day < 0.3f && light.golden < 0.3f) {
                for (k in 0 until 7) {
                    if (sin(t * (0.8f + hash(k) * 1.5f) + k * 2f) < -0.2f) continue
                    val bb = 14.5f + hash(k * 5) * 11f
                    val z = 16f + hash(k * 9) * 7f
                    val x = (PW / 2f + (0f - bb) * 2f).roundToInt()
                    val y = (RZ + 4f + bb - z).roundToInt()
                    drawRect(Color.White.copy(alpha = 0.8f), Offset(left + x * scale, top + y * scale), Size(scale, scale))
                }
            }
            // The sleeping cat's Zs, in pixels.
            val zt = (t % 5f) / 5f
            val zx = (PW / 2f + (7f - 26f) * 2f).roundToInt() + 2 + (zt * 4).toInt()
            val zy = (RZ + 4f + 33f - 8.1f).roundToInt() - 12 - (zt * 10).toInt()
            val za = sin(zt * PI.toFloat()) * 0.6f
            for ((dx, dy) in listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0, 2 to 1, 1 to 2, 0 to 3, 1 to 3, 2 to 3, 3 to 3)) {
                drawRect(Color.White.copy(alpha = za), Offset(left + (zx + dx) * scale, top + (zy + dy) * scale), Size(scale, scale))
            }
        }

        val tNow = ctx.now.toLocalTime()
        Column(
            if (landscape) Modifier.align(BiasAlignment(-0.78f, 0f)) else Modifier.align(Alignment.TopCenter).padding(top = maxHeight * 0.1f),
            horizontalAlignment = if (landscape) Alignment.Start else Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatClock(tNow, ctx.use24h), color = Color.White.copy(alpha = 0.88f), fontSize = clock, lineHeight = clock, fontWeight = FontWeight.Light, style = TabularNumbers)
                if (!ctx.use24h) Text(amPm(tNow), color = Color.White.copy(alpha = 0.5f), fontSize = body, modifier = Modifier.offset(x = 6.dp, y = (-10).dp))
            }
            Text(formatLongDate(ctx.now.toLocalDate()), color = Color.White.copy(alpha = 0.5f), fontSize = body)
        }
    }
}
