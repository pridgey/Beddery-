package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.Astronomy
import dev.pridgey.beddery.savers.Rng
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.Sketch
import dev.pridgey.beddery.savers.SketchScene
import dev.pridgey.beddery.savers.formatShortDate
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rgb
import dev.pridgey.beddery.savers.rgba
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sSmooth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = (PI * 2).toFloat()

/** Tonight's moon at its true phase, alone in a deep night sky with a faint Milky Way. */
@Composable
fun SceneMoonSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val phase = remember(ctx.now.hour, ctx.now.minute / 10) { Astronomy.moonPhase(Instant.now()) }
    val southern = ctx.settings.weatherLocation?.let { it.latitude < 0 } ?: (Astronomy.guessLocation(ZoneId.systemDefault()).first < 0)
    val maps by produceState<LunarMaps?>(null) { value = withContext(Dispatchers.Default) { LunarMaps.get(if (ctx.preview) 160 else 420) } }
    val lit = Astronomy.illumination(phase)
    val nextFull = phase < 0.5
    val days = Astronomy.daysUntil(phase, if (nextFull) 0.5 else 0.0)
    val whole = Math.round(days).toInt()
    val date = ctx.now.toLocalDate().plusDays(Math.round(days))
    val text = MoonText(
        stringResource(phaseLabel(Astronomy.phaseName(phase))),
        stringResource(R.string.moon_illuminated, Math.round(lit * 100).toInt()),
        if (whole == 0) stringResource(if (nextFull) R.string.moon_full_today else R.string.moon_new_today)
        else pluralStringResource(if (nextFull) R.plurals.moon_full_in else R.plurals.moon_new_in, whole, whole, formatShortDate(date)),
    )
    val disc = remember { MoonDisc() }
    SketchScene(time) { t -> drawMoonScene(t, phase.toFloat(), southern, maps, disc, text) }
}

private class MoonText(val name: String, val lit: String, val next: String)

private val sans = Paint(Paint.ANTI_ALIAS_FLAG)
private val light = Typeface.create("sans-serif-light", Typeface.NORMAL)
private val p0 = Path()
private val r0 = RectF()

private fun Sketch.drawMoonScene(t: Float, phase: Float, south: Boolean, maps: LunarMaps?, disc: MoonDisc, text: MoonText) {
    val lit = (1 - cos(TAU * phase)) / 2
    val mx = if (land) W * 0.26f else W * 0.5f
    val my = if (land) H * 0.46f else H * 0.3f
    val mr = U * 0.3f

    // ---- Sky.
    rect(0f, 0f, W, H, vgrad(0f, H, 0f to rgb(0x050818), 0.5f to rgb(0x0c1430), 0.85f to rgb(0x141c3c), 1f to rgb(0x1c2448)))
    // The Milky Way: a faint diagonal river of haze and fine stars, with dark dust lanes.
    c.save(); c.translate(W / 2, H / 2); c.rotate(Math.toDegrees(if (land) -0.35 else -1.05).toFloat())
    val span = hypot(W, H)
    for (i in 0 until 9) glow((i / 8f - 0.5f) * span, sin(i * 1.7f) * U * 0.04f, U * 0.35f, 0x8a96d0, 0.05f, sy = 0.35f)
    val r2 = Rng(23)
    for (i in 0 until 500) {
        val x = (r2.next() - 0.5f) * span
        val y = (r2.next() + r2.next() + r2.next() - 1.5f) * U * 0.12f
        rect(x, y, 0.9f, 0.9f, rgba(0xDCE2FF, 0.15f + 0.35f * r2.next() * r2.next()))
    }
    for (i in 0 until 8) glow((r2.next() - 0.5f) * span * 0.8f, (r2.next() - 0.5f) * U * 0.08f, U * 0.07f, 0x1a1030, 0.35f, additive = false, sy = 0.4f)
    c.restore()
    glow(W * 0.5f, H * 1.05f, max(W, H) * 0.6f, 0x5a3a5a, 0.35f, sy = 0.35f)
    glow(mx, my, mr * 4.5f, 0x4a5c9a, 0.14f + 0.22f * lit)
    stars(0f, H, if (land) 260 else 240, 17, t, 0xE8EEFF, false)
    // A shooting star now and then.
    val sc = (t + 9) % 31f
    if (sc < 0.9f) {
        val q = sc / 0.9f
        val sx = W * (0.62f + q * 0.25f)
        val sy = H * (0.06f + q * 0.05f)
        line(sx, sy, sx - W * 0.07f, sy - H * 0.014f, rgba(0xF0F4FF, sin(q * PI.toFloat()) * 0.7f), 1.2f)
    }

    // ---- The moon.
    glow(mx, my, mr * 2.6f, 0xdfe6ff, 0.08f + 0.3f * lit)
    if (maps != null) {
        val bmp = disc.get(maps, phase, south)
        r0.set(mx - mr, my - mr, mx + mr, my + mr)
        c.drawBitmap(bmp, null, r0, disc.paint)
    }

    // ---- Tonight's phase: name, how much is lit, what's next, and the month as a strip of tiny moons.
    val ix = if (land) W * 0.6f else W / 2
    val iy = if (land) H * 0.36f else my + mr + U * 0.16f
    val align = if (land) Paint.Align.LEFT else Paint.Align.CENTER
    sans.textAlign = align
    sans.typeface = light; sans.textSize = U * 0.06f; sans.color = rgba(0xF0F2FF, 0.9f)
    c.drawText(text.name, ix, iy, sans)
    sans.typeface = Typeface.DEFAULT; sans.textSize = U * 0.032f; sans.color = rgba(0xC8D4FF, 0.75f)
    c.drawText(text.lit, ix, iy + U * 0.05f, sans)
    sans.color = rgba(0xC8D4FF, 0.5f)
    c.drawText(text.next, ix, iy + U * 0.09f, sans)
    val stripW = U * 0.6f
    val sx0 = if (land) ix else ix - stripW / 2
    val sy = iy + U * 0.135f
    for (k in 0..14) {
        val p = k / 14f
        val x = sx0 + stripW * p
        val r = U * 0.011f
        tinyMoon(x, sy, r, p % 1f, south)
        if (abs(((phase - p + 1.5f) % 1f) - 0.5f) < 0.5f / 14) {
            p0.reset(); p0.addCircle(x, sy, r * 1.7f, Path.Direction.CW)
            stroke(p0, rgba(0xFFD696, 0.9f), 1.2f)
        }
    }
    grain(0.05f)
}

/** A small flat phase glyph for the cycle strip. */
private fun Sketch.tinyMoon(x: Float, y: Float, r: Float, p: Float, south: Boolean) {
    circle(x, y, r, rgba(0x3C4878, 0.6f))
    if ((1 - cos(TAU * p)) / 2 < 0.02f) return
    val waxing = (p < 0.5f) != south
    val k = cos(TAU * p) // 1 at new, -1 at full
    p0.reset()
    r0.set(x - r, y - r, x + r, y + r)
    // The lit half, then the terminator ellipse back across.
    p0.arcTo(r0, -90f, if (waxing) 180f else -180f, true)
    r0.set(x - abs(k) * r, y - r, x + abs(k) * r, y + r)
    val ccw = (k > 0) == waxing
    p0.arcTo(r0, 90f, if (ccw) 180f else -180f, false)
    p0.close()
    fill(p0, rgba(0xECF0FF, 0.9f))
}

/** The near side, built once: brightness (maria, rays, speckle) and height (crater bowls and rims). */
private class LunarMaps(val n: Int, val albedo: FloatArray, val height: FloatArray) {
    companion object {
        @Volatile private var cached: LunarMaps? = null
        fun get(n: Int): LunarMaps = cached?.takeIf { it.n >= n } ?: build(n).also { cached = it }

        // The real maria, as soft ellipses on the disc (x right, y down, north up): centre, radii, darkness.
        private val Maria = arrayOf(
            floatArrayOf(-0.56f, 0.02f, 0.28f, 0.5f, 0.75f), floatArrayOf(-0.3f, -0.42f, 0.3f, 0.26f, 0.95f),
            floatArrayOf(0.16f, -0.42f, 0.16f, 0.15f, 1f), floatArrayOf(0.3f, -0.1f, 0.2f, 0.17f, 0.95f),
            floatArrayOf(0.68f, -0.28f, 0.1f, 0.12f, 1f), floatArrayOf(0.55f, 0.16f, 0.13f, 0.15f, 0.85f),
            floatArrayOf(0.35f, 0.26f, 0.09f, 0.09f, 0.85f), floatArrayOf(-0.18f, 0.38f, 0.17f, 0.14f, 0.8f),
            floatArrayOf(-0.5f, 0.38f, 0.1f, 0.09f, 0.85f), floatArrayOf(-0.1f, -0.7f, 0.3f, 0.07f, 0.55f),
            floatArrayOf(0.25f, -0.74f, 0.2f, 0.05f, 0.5f), floatArrayOf(-0.02f, -0.22f, 0.09f, 0.07f, 0.8f),
            floatArrayOf(0.06f, -0.06f, 0.08f, 0.07f, 0.7f),
        )
        // Bright young craters with rays: Tycho, Copernicus, Kepler, Aristarchus.
        private val Rayed = arrayOf(floatArrayOf(-0.12f, 0.72f, 0.035f, 1f), floatArrayOf(-0.32f, -0.12f, 0.04f, 0.7f), floatArrayOf(-0.6f, -0.08f, 0.02f, 0.45f), floatArrayOf(-0.72f, -0.34f, 0.016f, 0.6f))

        private fun noise(xf: Float, yf: Float, zf: Float, seedF: Float): Float {
            val x = xf.toDouble(); val y = yf.toDouble(); val z = zf.toDouble(); val seed = seedF.toDouble()
            val ix = floor(x); val iy = floor(y); val iz = floor(z)
            val fx = x - ix; val fy = y - iy; val fz = z - iz
            val u = fx * fx * (3 - 2 * fx); val v = fy * fy * (3 - 2 * fy); val w = fz * fz * (3 - 2 * fz)
            fun h(a: Double, b: Double, c: Double) = sHash(a * 157 + b * 113 + c * 271 + seed * 31)
            fun l(a: Double, b: Double, t: Double) = a + (b - a) * t
            val l1 = l(l(h(ix, iy, iz), h(ix + 1, iy, iz), u), l(h(ix, iy + 1, iz), h(ix + 1, iy + 1, iz), u), v)
            val l2 = l(l(h(ix, iy, iz + 1), h(ix + 1, iy, iz + 1), u), l(h(ix, iy + 1, iz + 1), h(ix + 1, iy + 1, iz + 1), u), v)
            return l(l1, l2, w).toFloat()
        }

        private fun build(n: Int): LunarMaps {
            val albedo = FloatArray(n * n)
            val height = FloatArray(n * n)
            val r = Rng(31)
            val craters = ArrayList<FloatArray>()
            repeat(420) {
                val rad = 0.004f + 0.045f * r.next().pow(4)
                craters.add(floatArrayOf((r.next() * 2 - 1) * 0.98f, (r.next() * 2 - 1) * 0.98f, rad, 0.3f + 0.5f * r.next()))
            }
            for (c in Rayed) craters.add(floatArrayOf(c[0], c[1], c[2], 1f))
            for (j in 0 until n) for (i in 0 until n) {
                val x = (i + 0.5f) / n * 2 - 1
                val y = (j + 0.5f) / n * 2 - 1
                if (x * x + y * y > 1.02f) continue
                var mare = 0f
                val nShore = (noise(x * 5, y * 5, 1f, 9f) - 0.5f) * 0.7f + (noise(x * 14, y * 14, 1f, 10f) - 0.5f) * 0.3f
                val nDark = 0.75f + 0.35f * noise(x * 9, y * 9, 2f, 11f)
                for (m in Maria) {
                    val e = hypot((x - m[0]) / m[2], (y - m[1]) / m[3]) + nShore
                    mare = max(mare, sSmooth(1.15f, 0.6f, e) * m[4] * nDark)
                }
                val speck = noise(x * 30, y * 30, 2f, 4f) * 0.55f + noise(x * 70, y * 70, 3f, 5f) * 0.45f
                var a = 0.86f + (0.5f - 0.86f) * mare + (speck - 0.5f) * 0.14f + (noise(x * 3, y * 3, 4f, 6f) - 0.5f) * 0.1f
                for (c in Rayed) {
                    val dx = x - c[0]; val dy = y - c[1]; val dd = hypot(dx, dy); val ang = atan2(dy, dx)
                    val ray = max(0f, sin(ang * 17 + c[0] * 7 + dd * 3) * sin(ang * 41 + c[1] * 5)).pow(6)
                    a += c[3] * 0.16f * ray * sSmooth(c[2] * 16, c[2] * 1.5f, dd) * sSmooth(c[2] * 0.8f, c[2] * 1.4f, dd)
                    a += c[3] * 0.25f * sSmooth(c[2] * 1.8f, c[2] * 0.9f, dd)
                }
                albedo[j * n + i] = a.coerceIn(0.2f, 1.1f)
            }
            // Craters into the heightfield, each over its own small box.
            for (c in craters) {
                val ext = c[2] * 1.6f
                val i0 = max(0, ((c[0] - ext + 1) / 2 * n).toInt()); val i1 = min(n - 1, ceil((c[0] + ext + 1) / 2 * n).toInt())
                val j0 = max(0, ((c[1] - ext + 1) / 2 * n).toInt()); val j1 = min(n - 1, ceil((c[1] + ext + 1) / 2 * n).toInt())
                for (j in j0..j1) for (i in i0..i1) {
                    val x = (i + 0.5f) / n * 2 - 1
                    val y = (j + 0.5f) / n * 2 - 1
                    val dd = hypot(x - c[0], y - c[1]) / c[2]
                    if (dd > 1.6f) continue
                    height[j * n + i] += c[2] * c[3] * (if (dd < 1) -0.6f * (1 - dd * dd) else 0f) + c[2] * c[3] * 0.25f * exp(-((dd - 1) / 0.18f).pow(2))
                }
            }
            return LunarMaps(n, albedo, height)
        }
    }
}

/** The disc at a given phase, relit only when the phase changes noticeably. */
private class MoonDisc {
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var bmp: Bitmap? = null
    private var key = ""

    fun get(m: LunarMaps, phase: Float, south: Boolean): Bitmap {
        val k = "${m.n}:${(phase * 400).roundToInt()}:$south"
        bmp?.let { if (k == key) return it }
        val n = m.n
        val px = IntArray(n * n)
        val a = phase * TAU
        val lx = sin(a) * if (south) -1 else 1
        val lz = -cos(a)
        val al = m.albedo; val hh = m.height
        for (j in 1 until n - 1) for (i in 1 until n - 1) {
            val x = (i + 0.5f) / n * 2 - 1
            val y = (j + 0.5f) / n * 2 - 1
            val rr = x * x + y * y
            if (rr >= 1f) continue
            val z = sqrt(1 - rr)
            // Surface normal: the sphere's, tilted by the slope of the heightfield.
            val hx = (hh[j * n + i + 1] - hh[j * n + i - 1]) * n * 0.5f
            val hy = (hh[(j + 1) * n + i] - hh[(j - 1) * n + i]) * n * 0.5f
            var nx = x - hx * 0.12f; var nz = z
            val ny = y - hy * 0.12f
            val nl = sqrt(nx * nx + ny * ny + nz * nz); nx /= nl; nz /= nl
            val sun = nx * lx + nz * lz
            val sphereSun = x * lx + z * lz
            val day = sSmooth(-0.02f, 0.08f, sphereSun) * (0.25f + 1.2f * sun).coerceIn(0f, 1.25f)
            val limb = 0.82f + 0.18f * z
            val earthshine = 0.035f * (1 - sSmooth(-0.1f, 0.1f, sphereSun))
            val b = 255 * al[j * n + i] * (earthshine + day) * limb
            val alpha = (255 * sSmooth(1f, 0.99f, sqrt(rr))).toInt()
            val rC = b.coerceIn(0f, 255f).toInt()
            val gC = (b * 0.975f).coerceIn(0f, 255f).toInt()
            val bC = (b * 0.93f + 255 * earthshine * 0.12f).coerceIn(0f, 255f).toInt()
            px[j * n + i] = (alpha shl 24) or (rC shl 16) or (gC shl 8) or bC
        }
        val out = bmp?.takeIf { it.width == n } ?: Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, n, 0, 0, n, n)
        bmp = out; key = k
        return out
    }
}
