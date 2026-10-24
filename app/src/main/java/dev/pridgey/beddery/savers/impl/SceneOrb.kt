package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.Rng
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.Sketch
import dev.pridgey.beddery.savers.SketchScene
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rgb
import dev.pridgey.beddery.savers.rgba
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sLerp
import dev.pridgey.beddery.savers.sSmooth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
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
private const val PIf = PI.toFloat()

// The shows, in order, with how long each runs (seconds).
private enum class Show(val secs: Float) { Smiley(46f), Moon(28f), Kitty(34f), Earth(28f), Sleepy(34f), Fireworks(28f) }
private val Cycle = Show.entries.sumOf { it.secs.toDouble() }.toFloat()
private const val FADE = 2.5f
// The smiley's moments, in seconds into its show.
private const val PLANE0 = 5f
private const val PLANE1 = 19f
private const val CAR0 = 24f
private const val CAR1 = 38f
private const val FACE_EL = 0.6f
private const val TILT = -0.18f

/** The cat's coat on the globe, chosen in the saver's options. */
enum class CatCoat(val key: String) { Ginger("ginger"), Brown("brown"), Grey("grey"), Black("black"), White("white"), Calico("calico"), Tuxedo("tuxedo") }

private class Coat(val base: Int, val stripe: Int?, val muzzle: Int, val eye: Int, val nose: Int, val line: Int, val patches: Boolean = false, val white: Boolean = false)
private val Coats = mapOf(
    "ginger" to Coat(0xEC923E, 0xC46828, 0xFCE6C8, 0x96C846, 0xF0828C, 0x6E3C28),
    "brown" to Coat(0x846648, 0x3A2A1E, 0xD2BEA0, 0xB8A040, 0xB07068, 0x2A1C12),
    "grey" to Coat(0x9698A2, 0x646672, 0xE2E2E6, 0xB0CC50, 0xE696A0, 0x464248),
    "black" to Coat(0x2A2830, null, 0x3A3840, 0xECC43C, 0xBE6E82, 0x968C9C),
    "white" to Coat(0xF2EEE8, null, 0xFFFCF8, 0x6EAAF0, 0xF496A0, 0x96786E),
    "tuxedo" to Coat(0x1A181C, null, 0xF0EEEA, 0x9AC860, 0xD6969C, 0x78707A, white = true),
    "calico" to Coat(0xF5F0E8, null, 0xFFFAF4, 0xC8AA3C, 0xF08C96, 0x785A50, patches = true),
)

/**
 * A giant LED globe over a desert city at night, mirrored in a lake. It cycles through shows:
 * a smiley that spots a passing plane and car and slides round the globe to watch them, the
 * moon, a cat, the earth, a snoozing face and fireworks. Tap to move on to the next show.
 */
@Composable
fun SceneOrbSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val state = remember { OrbState() }
    remember { Thread { OrbMaps.warm() }.apply { isDaemon = true; start() } }
    state.coat = Coats[ctx.settings.catCoat] ?: Coats.getValue("ginger")
    val cooldown = remember { Cooldown(4_000) }
    OnSaverTap {
        if (cooldown.tryAcquire()) {
            val st = showAt(time.value + state.offset)
            // Skip ahead so the crossfade to the next show starts now.
            if (st.mix <= 0f) state.offset += st.a.secs - FADE - st.ua
        }
    }
    SketchScene(time) { t -> drawOrb(t + state.offset, state) }
}

private class ShowState(var a: Show = Show.Smiley, var b: Show = Show.Moon, var ua: Float = 0f, var ub: Float = 0f, var mix: Float = 0f)

private fun showAt(t: Float, out: ShowState = ShowState()): ShowState {
    var u = ((t % Cycle) + Cycle) % Cycle
    val shows = Show.entries
    for (i in shows.indices) {
        val s = shows[i]
        if (u < s.secs) {
            out.a = s; out.b = shows[(i + 1) % shows.size]; out.ua = u; out.ub = u - s.secs
            out.mix = sSmooth(s.secs - FADE, s.secs, u)
            return out
        }
        u -= s.secs
    }
    return out
}

private class OrbState {
    var offset = 0f
    var coat: Coat = Coats.getValue("ginger")
    var key = ""
    var back: Bitmap? = null
    var front: Bitmap? = null
    var face: Bitmap? = null
    var pixels = IntArray(0)
    var faceT = -100f
    val avg = FloatArray(3)
    val show = ShowState()
    val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    val dst = RectF()
    // Where the face sits on the globe and where its eyes look, this frame.
    var lookOn = 0f
    var lookX = 0f
    var lookY = 0f
    var faceLon = 0f
    var faceEl = FACE_EL

    fun reset(k: String) {
        key = k
        back?.recycle(); back = null
        front?.recycle(); front = null
    }
}

private val op = Array(4) { Path() }

private fun Sketch.bakeLayer(w: Float, h: Float, draw: Sketch.() -> Unit): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(w * density).toInt().coerceAtLeast(1), ceil(h * density).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val bc = Canvas(bmp)
    bc.scale(density, density)
    val screen = c
    c = bc
    draw()
    c = screen
    return bmp
}

private fun hexOf(c: FloatArray): Int = (c[0].roundToInt().coerceIn(0, 255) shl 16) or (c[1].roundToInt().coerceIn(0, 255) shl 8) or c[2].roundToInt().coerceIn(0, 255)

private fun Sketch.drawOrb(t: Float, s: OrbState) {
    val shore = if (land) H * 0.62f else H * 0.5f // the far edge of the lake
    val ground = shore - U * 0.05f
    val R = if (land) H * 0.42f else W * 0.43f
    val cx = W * 0.5f
    val cy = ground - R * 0.12f // its middle sits just above the horizon
    val road = shore - U * 0.016f
    val key = "${W}x$H@$density"
    if (s.key != key) s.reset(key)

    // ---- The far scene never changes: sky, distant mountains, the far city.
    val back = s.back ?: bakeLayer(W, shore) {
        rect(0f, 0f, W, shore, vgrad(0f, shore, 0f to rgb(0x05051a), 0.45f to rgb(0x120c30), 0.8f to rgb(0x2c1640), 1f to rgb(0x4a2048)))
        ridge(ground - U * 0.1f, U * 0.06f, 11f, 0.004f * (411 / U), rgb(0x1e1438), shore, 0f, false)
        glow(W / 2, ground, W * 0.8f, 0xc04a6a, 0.25f, sy = 0.12f)
        city(ground, 21, 0.05f, 0.18f, 0x1a1232, 0xe8a060, 0.25f, 0.06f, shore)
    }.also { s.back = it }
    // ---- The near scene, also fixed: towers parted round the globe, landmarks, the waterfront.
    val front = s.front ?: bakeLayer(W, shore + 2) {
        city(ground + U * 0.03f, 37, 0.08f, 0.36f, 0x0e0a1c, 0xffd29a, 0.45f, 0.11f, shore, cx, R)
        towersStatic(ground + U * 0.03f, cx, R, shore)
        rect(0f, road, W, shore - road, rgb(0x0a0712))
        rect(0f, shore - 1.5f, W, 3f, rgb(0x05030a))
        for (i in 0 until 14) {
            val lx = (i + 0.5f) / 14 * W
            rect(lx - 0.6f, shore - U * 0.035f, 1.2f, U * 0.035f, rgb(0x05030a))
            glow(lx, shore - U * 0.036f, U * 0.014f, 0xffc070, 0.7f)
            rect(lx - 1, shore - U * 0.037f, 2f, 2f, rgb(0xffe0b0))
        }
        val palms = if (land) floatArrayOf(0.04f, 0.12f, 0.88f, 0.96f) else floatArrayOf(0.05f, 0.95f)
        palms.forEachIndexed { i, f -> palm(W * f, shore, U * (0.2f + 0.05f * sHash(i * 3f)), i) }
    }.also { s.front = it }

    s.dst.set(0f, 0f, W, shore)
    c.drawBitmap(back, null, s.dst, s.bmpPaint)
    stars(0f, ground * 0.5f, 70, 4, t)

    // ---- The smiley's moments: a plane crossing the sky, then a car along the waterfront.
    val show = showAt(t, s.show)
    val su = if (show.a == Show.Smiley) show.ua else -1f
    val plane = if (su in PLANE0..PLANE1) (su - PLANE0) / (PLANE1 - PLANE0) else -1f
    val car = if (su in CAR0..CAR1) (su - CAR0) / (CAR1 - CAR0) else -1f
    val planeX = W * (1.1f - plane * 1.2f)
    val planeY = (cy - R) * 0.55f + sin(plane * 3) * U * 0.01f
    val carX = W * (-0.1f + car * 1.2f)
    val carY = road + U * 0.008f
    val hasTarget = plane >= 0f || car >= 0f
    val tx = if (plane >= 0f) planeX else carX
    val ty = if (plane >= 0f) planeY else carY
    // At rest the face sits front and centre, swaying a touch. When something passes, the whole
    // face slides round the globe toward it, and the eyes lock on.
    val idleLon = if (show.a == Show.Smiley) 0.08f * sin(t * 0.13f) else 0f
    val p = if (plane >= 0f) plane else car
    val on = if (hasTarget) sSmooth(0f, 0.1f, p) * sSmooth(1f, 0.85f, p) else 0f
    val faceY0 = cy + R * (-sin(FACE_EL) * cos(TILT) - cos(FACE_EL) * sin(TILT))
    val tLon = if (hasTarget) ((tx - cx) / R * 0.9f).coerceIn(-0.8f, 0.8f) else 0f
    val tEl = if (hasTarget) (FACE_EL + (faceY0 - ty) / R * 0.45f).coerceIn(0.45f, 0.95f) else FACE_EL
    s.faceLon = sLerp(idleLon, tLon, on)
    s.faceEl = sLerp(FACE_EL, tEl, on)
    s.lookOn = on
    if (hasTarget) {
        val fsx = cx + R * sin(s.faceLon) * cos(s.faceEl)
        val fsy = cy + R * (-sin(s.faceEl) * cos(TILT) - cos(s.faceLon) * cos(s.faceEl) * sin(TILT))
        val dx = tx - fsx
        val dy = ty - fsy
        val d = hypot(dx, dy).takeIf { it > 0f } ?: 1f
        val k = min(1f, d / (R * 0.6f))
        s.lookX = dx / d * 0.06f * k; s.lookY = dy / d * 0.06f * k
    }
    if (plane >= 0f) jet(planeX, planeY, t)

    // ---- The globe: its face rendered on the CPU about twenty times a second.
    val face = orbFace(s, R, t)
    drawGlobe(face, cx, cy, R, ground, s)
    glow(cx, cy, R * 2.1f, hexOf(s.avg), 0.3f)

    s.dst.set(0f, 0f, W, shore + 2)
    c.drawBitmap(front, null, s.dst, s.bmpPaint)
    towersLive(ground + U * 0.03f, t, cx, R, shore)
    glow(cx, ground + U * 0.02f, R * 1.4f, hexOf(s.avg), 0.22f, sy = 0.35f)
    if (car >= 0f) carLights(carX, carY)

    // ---- The lake mirrors everything above it, row by row with a gentle ripple.
    val band = 3f
    var y = shore + 1
    while (y < H) {
        val d = y - shore
        val wob = sin(d * 0.35f - t * 0.7f) * 0.6f + sin(d * 0.11f + t * 0.3f) * 0.4f + sin(d * 0.9f + t * 1.0f) * 0.3f
        val sy = shore - 2 - d * 0.85f + wob * (0.8f + d * 0.04f)
        if (sy < 0) break
        val dx = (sin(d * 0.21f + t * 0.5f) + 0.5f * sin(d * 0.67f - t * 0.8f)) * (0.6f + d * 0.02f) * U * 0.005f
        c.save()
        c.clipRect(0f, y, W, y + band)
        c.translate(dx, y - sy)
        s.dst.set(0f, 0f, W, shore); c.drawBitmap(back, null, s.dst, s.bmpPaint)
        s.dst.set(cx - R, cy - R, cx + R, cy + R); c.drawBitmap(face, null, s.dst, s.bmpPaint)
        s.dst.set(0f, 0f, W, shore + 2); c.drawBitmap(front, null, s.dst, s.bmpPaint)
        c.restore()
        y += band
    }
    rect(0f, shore, W, H - shore, vgrad(shore, H, 0f to rgba(0x060410, 0.5f), 0.4f to rgba(0x060410, 0.72f), 1f to rgba(0x04030A, 0.92f)))
    // Dark troughs between ripples.
    for (i in 0 until 70) {
        val d = sHash(i * 13f).pow(1.3f) * (H - shore)
        val len = U * (0.05f + 0.15f * sHash(i * 17f)) * (1 + d / U)
        val x = ((sHash(i * 19f) * (W + len) + t * U * 0.004f * (sHash(i.toFloat()) - 0.5f)) % (W + len) + W + len) % (W + len) - len
        rect(x, shore + 3 + d, len, 1 + d / U * 2, rgba(0x04030A, 0.35f * sSmooth(0f, 0.15f, sHash(i * 23f))))
    }
    // Long glints where the globe lights the water.
    val gl = floatArrayOf(min(255f, s.avg[0] * 1.3f), min(255f, s.avg[1] * 1.3f), min(255f, s.avg[2] * 1.3f))
    val glHex = hexOf(gl)
    for (i in 0 until 40) {
        val d = sHash(i * 3f).pow(1.4f) * (H - shore)
        val w = U * (0.02f + 0.05f * sHash(i * 7f)) * (1 + d / U)
        val x = cx + (sHash(i * 5f) - 0.5f) * R * 1.6f + sin(t * 0.3f + i) * U * 0.006f
        val a = (0.09f + 0.05f * sin(t * (0.3f + 0.3f * sHash(i.toFloat())) + i * 2)) * (1 - d / (H - shore))
        if (a > 0f) rectAdd(x - w / 2, shore + 4 + d, w, 1f, rgba(glHex, a))
    }
    grain(0.05f)
}

private fun Sketch.drawGlobe(face: Bitmap, cx: Float, cy: Float, R: Float, ground: Float, s: OrbState) {
    c.save()
    c.clipRect(cx - R - 2, cy - R - 2, cx + R + 2, ground)
    s.dst.set(cx - R, cy - R, cx + R, cy + R)
    c.drawBitmap(face, null, s.dst, s.bmpPaint)
    // Sheen across the upper left, and a dark rim where the panels turn away.
    circle(cx, cy, R, rgrad(cx - R * 0.35f, cy - R * 0.45f, R * 0.9f, 0f to rgba(0xFFFFFF, 0.08f), 1f to rgba(0xFFFFFF, 0f)))
    val rim = op[0]; rim.reset(); rim.addCircle(cx, cy, R - 0.5f, Path.Direction.CW)
    stroke(rim, rgba(0, 0.5f), 1.5f)
    c.restore()
}

// ---------------------------------------------------------------- The globe's face

private val px = FloatArray(3)
private val qx = FloatArray(3)

/** Renders the face into a small bitmap, at most twenty-four times a second. Updates the average colour. */
private fun orbFace(s: OrbState, R: Float, t: Float): Bitmap {
    val n = max(64, (R * 0.9f).roundToInt())
    var bmp = s.face
    if (bmp == null || bmp.width != n) {
        bmp?.recycle()
        bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        s.face = bmp
        s.pixels = IntArray(n * n)
        s.faceT = -100f
    }
    if (abs(t - s.faceT) < 1f / 24f) return bmp
    s.faceT = t
    val show = s.show
    val ct = cos(TILT); val st = sin(TILT)
    // The face's own frame on the globe: d points out through its centre, r to its right, w down it.
    val fl = s.faceLon; val fe = s.faceEl
    val d0 = sin(fl) * cos(fe); val d1 = -sin(fe); val d2 = cos(fl) * cos(fe)
    val r0 = cos(fl); val r2 = -sin(fl)
    val w0 = sin(fl) * sin(fe); val w1 = cos(fe); val w2 = cos(fl) * sin(fe)
    var sr = 0f; var sg = 0f; var sb = 0f; var cnt = 0
    val pix = s.pixels
    for (j in 0 until n) {
        val y = (j + 0.5f) / n * 2 - 1
        for (i in 0 until n) {
            val x = (i + 0.5f) / n * 2 - 1
            val o = j * n + i
            val rr = x * x + y * y
            if (rr >= 1f) { pix[o] = 0; continue }
            val z = sqrt(1 - rr)
            // Undo the tilt to find the point on the globe's own surface.
            val y0 = y * ct + z * st
            val z0 = -y * st + z * ct
            val lat = -asin(y0.coerceIn(-1f, 1f))
            val lon = atan2(x, z0)
            val facing = x * d0 + y0 * d1 + z0 * d2
            val fu = if (facing > 0.3f) x * r0 + z0 * r2 else 9f
            val fv = if (facing > 0.3f) x * w0 + y0 * w1 + z0 * w2 else 9f
            content(show.a, x, y, lat, lon, t, show.ua, fu, fv, s, px)
            if (show.mix > 0f) {
                content(show.b, x, y, lat, lon, t, show.ub, fu, fv, s, qx)
                for (k in 0..2) px[k] = sLerp(px[k], qx[k], show.mix)
            }
            val shade = 0.5f + 0.5f * z
            val r = (px[0] * shade).coerceIn(0f, 255f)
            val g = (px[1] * shade).coerceIn(0f, 255f)
            val b = (px[2] * shade).coerceIn(0f, 255f)
            pix[o] = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
            sr += r; sg += g; sb += b; cnt++
        }
    }
    bmp.setPixels(pix, 0, n, 0, 0, n, n)
    if (cnt > 0) { s.avg[0] = sr / cnt; s.avg[1] = sg / cnt; s.avg[2] = sb / cnt }
    return bmp
}

/** Colour of one LED for a show. x,y face the viewer; fu,fv are the face's own coordinates. */
private fun content(show: Show, x: Float, y: Float, lat: Float, lon: Float, t: Float, u: Float, fu: Float, fv: Float, s: OrbState, out: FloatArray) {
    val rim = hypot(x, y)
    when (show) {
        Show.Smiley -> smileyAt(fu, fv, t, rim, s, out)
        Show.Sleepy -> sleepyAt(fu, fv, u, rim, out)
        Show.Kitty -> kittyAt(fu, fv, t, s.coat, out)
        Show.Moon -> OrbMaps.sample(OrbMaps.moon, lat, lon + t * 0.02f, out)
        Show.Earth -> {
            OrbMaps.sample(OrbMaps.earth, lat, lon + t * 0.03f, out)
            OrbMaps.sample(OrbMaps.clouds, lat, lon + t * 0.045f, qy)
            val cl = qy[0] / 255f * 0.8f
            for (k in 0..2) out[k] = sLerp(out[k], 240f, cl)
        }
        Show.Fireworks -> fireworksAt(x, y, t, out)
    }
}
private val qy = FloatArray(3)

private fun set(out: FloatArray, hex: Int) { out[0] = ((hex shr 16) and 255).toFloat(); out[1] = ((hex shr 8) and 255).toFloat(); out[2] = (hex and 255).toFloat() }
/** Blends [hex] into [c] by k. */
private fun mixIn(c: FloatArray, hex: Int, k: Float) {
    if (k <= 0f) return
    c[0] = sLerp(c[0], ((hex shr 16) and 255).toFloat(), k)
    c[1] = sLerp(c[1], ((hex shr 8) and 255).toFloat(), k)
    c[2] = sLerp(c[2], (hex and 255).toFloat(), k)
}
/** Soft-edged mask: 1 inside a shape whose signed distance is d, fading over w. */
private fun inside(d: Float, w: Float = 0.012f) = sSmooth(w, -w, d)
private fun ellipseD(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float) = (hypot((x - cx) / rx, (y - cy) / ry) - 1) * min(rx, ry)
private fun segD(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val k = (((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
    return hypot(x - ax - dx * k, y - ay - dy * k)
}

/** A happy yellow face: big shiny eyes that look around, blink, and now and then wink. Watching
 *  something, the eyes go wide, the brows lift and the mouth drops into an amazed "o". */
private fun smileyAt(x: Float, fy: Float, t: Float, rim: Float, s: OrbState, c: FloatArray) {
    set(c, 0xFFC430)
    mixIn(c, 0xFFA028, sSmooth(0.3f, 1f, rim) * 0.6f)
    if (abs(x) > 0.7f || abs(fy) > 0.7f) return
    val L = s.lookOn
    val gx = sLerp(0.03f * sin(t * 0.4f) + 0.015f * sin(t * 1.1f), s.lookX, L)
    val gy = sLerp(0.02f * sin(t * 0.33f + 1), s.lookY, L)
    val bp = t % 6.1f
    val blink = if (bp < 0.3f) sin(bp / 0.3f * PIf) else 0f
    val wp = t % 19f
    val wink = if (wp > 9f && wp < 10.6f) sSmooth(9f, 9.15f, wp) * sSmooth(10.6f, 10.45f, wp) else 0f
    for (side in Sides) mixIn(c, 0xFF786E, inside(ellipseD(x, fy, side * 0.43f, 0.1f, 0.1f, 0.06f), 0.05f) * 0.7f)
    for (side in Sides) {
        val ex = side * 0.26f + gx * 0.8f
        val ey = -0.12f + gy * 0.8f
        val shut = (if (side > 0) max(blink, wink) else blink) * (1 - L)
        if (shut > 0.85f) {
            // A closed, happy "^" eye.
            val u = (x - ex) / 0.1f
            val arc = ey + 0.02f - 0.06f * (1 - u * u)
            if (abs(u) < 1f) mixIn(c, 0x462814, inside(abs(fy - arc) - 0.022f))
            continue
        }
        val es = 1 + 0.22f * L
        val ry = 0.14f * es * (1 - shut)
        mixIn(c, 0x3C2212, inside(ellipseD(x, fy, ex, ey, 0.095f * es, ry)))
        if (L > 0.01f) {
            val bw = (x - ex) / 0.1f
            val by = ey - 0.2f - 0.04f * L - 0.035f * (1 - bw * bw)
            if (abs(bw) < 1f) mixIn(c, 0x5A3218, inside(abs(fy - by) - 0.014f) * L)
        }
        // Two catchlights make it read as cute rather than staring.
        mixIn(c, 0xFFFFFF, inside(ellipseD(x, fy, ex - 0.03f + gx, ey - 0.05f + gy, 0.035f, 0.035f * (1 - shut))))
        mixIn(c, 0xFFFFFF, inside(ellipseD(x, fy, ex + 0.03f + gx, ey + 0.04f + gy, 0.016f, 0.016f * (1 - shut))))
    }
    val o = sSmooth(0.15f, 0.5f, L)
    val mr = 0.1f + 0.015f * sin(t * 0.25f)
    if (o < 1f && fy > 0.06f) mixIn(c, 0x5A2814, inside(abs(hypot(x, fy - 0.06f) - mr) - 0.02f) * (1 - o))
    if (o > 0f) {
        val ow = 0.055f + 0.006f * sin(t * 3)
        val oh = 0.075f + 0.008f * sin(t * 3 + 1)
        val md = ellipseD(x, fy, 0f, 0.15f, ow, oh)
        mixIn(c, 0x601E1A, inside(md) * o)
        mixIn(c, 0xE66E6E, inside(max(md, ellipseD(x, fy, 0f, 0.2f, ow * 0.7f, oh * 0.45f))) * o)
    }
}
private val Sides = floatArrayOf(-1f, 1f)

/** A snoozing face: closed eyes, slow breathing, a little bubble, and Zs drifting up. */
private fun sleepyAt(x: Float, v: Float, u: Float, rim: Float, c: FloatArray) {
    set(c, 0xF6C85C)
    mixIn(c, 0xECA03C, sSmooth(0.3f, 1f, rim) * 0.6f)
    if (abs(x) > 0.8f || abs(v) > 0.8f) return
    val breath = sin(u * 1.1f)
    val fy = v - breath * 0.012f
    for (side in Sides) mixIn(c, 0xFF8278, inside(ellipseD(x, fy, side * 0.43f, 0.1f, 0.1f, 0.06f), 0.05f) * 0.55f)
    for (side in Sides) {
        val ex = side * 0.26f
        val w = (x - ex) / 0.1f
        if (abs(w) < 1f) mixIn(c, 0x462814, inside(abs(fy - (-0.1f + 0.035f * (1 - w * w))) - 0.018f))
    }
    val mr = 0.035f + 0.012f * (0.5f + 0.5f * breath)
    mixIn(c, 0x783228, inside(ellipseD(x, fy, 0.02f, 0.12f, mr * 0.9f, mr)))
    // A bubble that swells with each breath and pops now and then.
    val bp = (u.mod(7f)) / 7f
    val br = 0.02f + 0.08f * sSmooth(0f, 0.9f, bp) * (0.85f + 0.15f * breath)
    if (bp < 0.92f) {
        val d = hypot(x - 0.1f - br * 0.7f, fy - 0.1f + br * 0.2f) - br
        mixIn(c, 0xC8EBFF, inside(abs(d) - 0.006f) * 0.8f)
        mixIn(c, 0xE6F5FF, inside(d) * 0.25f)
        mixIn(c, 0xFFFFFF, inside(ellipseD(x, fy, 0.1f + br * 0.4f, 0.1f - br * 0.3f, br * 0.18f, br * 0.12f)))
    }
    // Zs drifting up and to the right, growing and fading.
    for (k in 0 until 3) {
        val p = (u / 4.5f + k / 3f).mod(1f)
        val sz = 0.03f + 0.045f * p
        val a = sSmooth(0f, 0.15f, p) * sSmooth(1f, 0.7f, p)
        val zx = 0.28f + p * 0.25f + sin(p * 6 + k) * 0.03f
        val zy = -0.18f - p * 0.32f
        val d = min(min(segD(x, fy, zx - sz, zy - sz, zx + sz, zy - sz), segD(x, fy, zx + sz, zy - sz, zx - sz, zy + sz)), segD(x, fy, zx - sz, zy + sz, zx + sz, zy + sz))
        mixIn(c, 0xFFFFFF, inside(d - sz * 0.18f) * a)
    }
}

/** A cat: eyes that slow-blink (a cat's way of saying hello), a pink nose, whiskers, ears. */
private fun kittyAt(x: Float, fy: Float, t: Float, coat: Coat, c: FloatArray) {
    set(c, coat.base)
    if (abs(x) > 0.8f || abs(fy) > 0.8f) return
    if (coat.patches) {
        mixIn(c, 0xE88E3C, inside(ellipseD(x, fy, -0.34f, -0.3f, 0.32f, 0.26f), 0.03f))
        mixIn(c, 0x342E32, inside(ellipseD(x, fy, 0.36f, -0.36f, 0.28f, 0.22f), 0.03f))
    }
    val stripe = coat.stripe
    if (stripe != null && fy < -0.18f) for (k in floatArrayOf(0f, 0.1f, 0.2f)) mixIn(c, stripe, inside(abs(x - (k - 0.1f)) - 0.022f * (1 + fy * 0.6f), 0.015f) * sSmooth(-0.18f, -0.3f, fy))
    val sx = abs(x)
    mixIn(c, coat.muzzle, inside(ellipseD(sx, fy, 0.09f, 0.14f, 0.13f, 0.1f), 0.03f))
    mixIn(c, coat.muzzle, inside(ellipseD(x, fy, 0f, 0.24f, 0.12f, 0.08f), 0.03f))
    // A tuxedo's white bib and the blaze running up between its eyes.
    if (coat.white) {
        mixIn(c, coat.muzzle, inside(ellipseD(x, fy, 0f, 0.2f, 0.3f, 0.22f), 0.04f))
        mixIn(c, coat.muzzle, inside(abs(x) - 0.05f - (fy + 0.2f).coerceAtLeast(0f) * 0.3f, 0.02f) * sSmooth(-0.45f, -0.2f, fy))
    }
    val bp = t % 8.3f
    val shut = if (bp < 1.6f) (if (bp < 0.5f) sSmooth(0f, 0.5f, bp) else if (bp < 1f) 1f else sSmooth(1.6f, 1f, bp)) else 0f
    val look = 0.02f * sin(t * 0.3f)
    for (side in Sides) {
        val ex = side * 0.24f
        val ey = -0.08f
        if (shut > 0.9f) {
            val u = (x - ex) / 0.1f
            if (abs(u) < 1f) mixIn(c, coat.line, inside(abs(fy - (ey + 0.025f * (1 - u * u))) - 0.016f))
            continue
        }
        val ry = 0.085f * (1 - shut)
        val d = ellipseD(x, fy, ex, ey, 0.1f, ry)
        mixIn(c, 0x1E1610, inside(d - 0.012f))
        mixIn(c, coat.eye, inside(d))
        mixIn(c, 0x141410, inside(ellipseD(x, fy, ex + look, ey, 0.028f, ry * 0.95f)))
        mixIn(c, 0xFFFFFF, inside(ellipseD(x, fy, ex + look - 0.035f, ey - 0.03f * (1 - shut), 0.018f, 0.018f * (1 - shut))))
    }
    val nd = max(abs(x) * 1.4f + (fy - 0.06f), -(fy - 0.02f)) - 0.03f
    mixIn(c, coat.nose, inside(nd, 0.01f))
    if (fy > 0.1f) for (side in Sides) mixIn(c, coat.line, inside(abs(hypot(x - side * 0.045f, fy - 0.1f) - 0.045f) - 0.009f))
    // Whiskers.
    for (side in Sides) for (k in -1..1) {
        val x0 = side * 0.16f
        val y0 = 0.1f + k * 0.035f
        val u = (x - x0) * side
        if (u > 0f && u < 0.3f) mixIn(c, 0xFFFAF0, inside(abs(fy - (y0 + k * u * 0.25f + u * u * 0.2f)) - 0.005f, 0.006f) * (1 - u / 0.3f))
    }
    // Pink inner ears at the top of the globe.
    for (side in Sides) {
        val ax = side * 0.4f
        val ay = -0.55f
        val rimC = if (coat.patches) (if (side < 0) 0xC86E28 else 0x242024) else (coat.stripe ?: coat.line)
        val d = max(-(fy - ay - 0.05f), abs(x - ax) * 1.6f + (fy - ay) * 0.9f - 0.12f)
        mixIn(c, rimC, inside(d - 0.03f, 0.02f))
        mixIn(c, 0xFAAAAA, inside(d, 0.02f))
    }
}

private val FireworkColors = intArrayOf(0xFF5A78, 0x78DCFF, 0xFFD25A, 0xB478FF, 0x78FFA0)

private fun fireworksAt(x: Float, y: Float, t: Float, c: FloatArray) {
    c[0] = 6f; c[1] = 8f; c[2] = 26f
    for (k in 0 until 7) {
        val period = 3.6f + sHash(k.toFloat()) * 1.5f
        val cycle = floor((t + k * 1.3f) / period).toInt()
        val a = ((t + k * 1.3f) % period) / period
        val life = (1 - a) * (1 - a) * sSmooth(0f, 0.04f, a)
        if (life <= 0.002f) continue
        val bx = (sHash(k * 7f + cycle * 13) - 0.5f) * 1.2f
        val by = -0.5f + (sHash(k * 11f + cycle * 17) - 0.5f) * 0.6f + a * a * 0.15f
        val dx = x - bx; val dy = y - by
        val d = hypot(dx, dy)
        val rad = (0.4f + 0.2f * sHash(k * 3f + cycle)) * (1 - exp(-a * 5))
        if (d > rad + 0.25f) continue
        val spokes = max(0f, cos(atan2(dy, dx) * 9 + k)).pow(3)
        val ring = exp(-((d - rad) / 0.06f).pow(2)) * (0.35f + 0.65f * spokes)
        val trail = if (d < rad) exp(-((rad - d) / 0.18f).pow(2)) * 0.35f * spokes else 0f
        val f = (ring + trail) * life * 1.7f
        val col = FireworkColors[(k + cycle).mod(FireworkColors.size)]
        c[0] += ((col shr 16) and 255) * f; c[1] += ((col shr 8) and 255) * f; c[2] += (col and 255) * f
    }
    for (k in 0..2) c[k] = min(255f, c[k])
}

/** The moon and the earth, baked once into small latitude/longitude maps; drawing is a lookup. */
private object OrbMaps {
    private const val MW = 256
    private const val MH = 128
    val moon: FloatArray by lazy { bake { la, lo, o -> moonAt(la, lo, o) } }
    val earth: FloatArray by lazy { bake { la, lo, o -> earthAt(la, lo, o) } }
    val clouds: FloatArray by lazy { bake { la, lo, o -> o[0] = cloudAt(la, lo) * 255; o[1] = 0f; o[2] = 0f } }

    fun warm() { moon; earth; clouds }

    private fun bake(f: (Double, Double, FloatArray) -> Unit): FloatArray {
        val m = FloatArray(MW * MH * 3)
        val o = FloatArray(3)
        for (j in 0 until MH) for (i in 0 until MW) {
            f(PI / 2 - (j + 0.5) / MH * PI, (i + 0.5) / MW * PI * 2, o)
            val k = (j * MW + i) * 3
            m[k] = o[0]; m[k + 1] = o[1]; m[k + 2] = o[2]
        }
        return m
    }

    /** Bilinear lookup, wrapping round in longitude. */
    fun sample(m: FloatArray, lat: Float, lon: Float, out: FloatArray) {
        val fx = ((lon / TAU) % 1f + 1f) % 1f * MW - 0.5f
        val fy = ((PIf / 2 - lat) / PIf * MH - 0.5f).coerceIn(0f, MH - 1.001f)
        val i0 = floor(fx).toInt(); val j0 = floor(fy).toInt()
        val u = fx - i0; val v = fy - j0
        val a = ((i0 % MW) + MW) % MW; val b = (a + 1) % MW
        val o00 = (j0 * MW + a) * 3; val o10 = (j0 * MW + b) * 3; val o01 = ((j0 + 1) * MW + a) * 3; val o11 = ((j0 + 1) * MW + b) * 3
        for (k in 0..2) out[k] = sLerp(sLerp(m[o00 + k], m[o10 + k], u), sLerp(m[o01 + k], m[o11 + k], u), v)
    }

    private fun sm(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }
    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    /** Smooth 3D value noise in 0..1. */
    private fun noise3(x: Double, y: Double, z: Double, seed: Double): Double {
        val ix = floor(x); val iy = floor(y); val iz = floor(z)
        val fx = x - ix; val fy = y - iy; val fz = z - iz
        val u = fx * fx * (3 - 2 * fx); val v = fy * fy * (3 - 2 * fy); val w = fz * fz * (3 - 2 * fz)
        fun h(a: Double, b: Double, c: Double) = sHash(a * 157 + b * 113 + c * 271 + seed * 31)
        val l1 = lerp(lerp(h(ix, iy, iz), h(ix + 1, iy, iz), u), lerp(h(ix, iy + 1, iz), h(ix + 1, iy + 1, iz), u), v)
        val l2 = lerp(lerp(h(ix, iy, iz + 1), h(ix + 1, iy, iz + 1), u), lerp(h(ix, iy + 1, iz + 1), h(ix + 1, iy + 1, iz + 1), u), v)
        return lerp(l1, l2, w)
    }

    private fun moonAt(lat: Double, lon: Double, o: FloatArray) {
        val p0 = cos(lat) * cos(lon); val p1 = sin(lat); val p2 = cos(lat) * sin(lon)
        val maria = sm(0.52, 0.62, noise3(p0 * 1.8, p1 * 1.8, p2 * 1.8, 3.0))
        val rough = noise3(p0 * 9, p1 * 9, p2 * 9, 5.0) * 0.6 + noise3(p0 * 22, p1 * 22, p2 * 22, 6.0) * 0.4
        var v = 205 - 75 * maria + 22 * (rough - 0.5)
        for (k in 0 until 16) {
            val cl = (sHash(k * 3.0) - 0.5) * 2.4
            val cn = sHash(k * 7.0) * PI * 2
            val q0 = cos(cl) * cos(cn); val q1 = sin(cl); val q2 = cos(cl) * sin(cn)
            val d = acos((p0 * q0 + p1 * q1 + p2 * q2).coerceIn(-1.0, 1.0))
            val cq = d / (0.05 + 0.08 * sHash(k * 11.0))
            v -= 26 * sm(1.0, 0.3, cq)
            v += 16 * exp(-((cq - 1.05) / 0.12).pow(2))
            v += 8 * sm(2.4, 1.2, cq) * (0.5 + 0.5 * noise3(p0 * 40, p1 * 40, p2 * 40, k.toDouble()))
        }
        o[0] = v.toFloat(); o[1] = (v * 0.98).toFloat(); o[2] = (v * 0.93).toFloat()
    }

    private fun earthAt(lat: Double, lon: Double, o: FloatArray) {
        val p0 = cos(lat) * cos(lon); val p1 = sin(lat); val p2 = cos(lat) * sin(lon)
        fun f(k: Double, seed: Double) = noise3(p0 * k, p1 * k, p2 * k, seed)
        val h = f(1.6, 8.0) * 0.5 + f(4.0, 9.0) * 0.3 + f(10.0, 10.0) * 0.2
        if (h < 0.5) {
            val k = sm(0.25, 0.5, h)
            o[0] = lerp(10.0, 30.0, k).toFloat(); o[1] = lerp(34.0, 90.0, k).toFloat(); o[2] = lerp(90.0, 150.0, k).toFloat()
        } else if (h < 0.515) {
            o[0] = 70f; o[1] = 130f; o[2] = 150f // shallows
        } else {
            val dry = sm(0.4, 0.7, f(3.0, 12.0)) * (1 - sm(0.9, 1.2, abs(lat)))
            val k = sm(0.515, 0.7, h)
            o[0] = lerp(lerp(50.0, 70.0, k), 190.0, dry).toFloat(); o[1] = lerp(lerp(110.0, 120.0, k), 160.0, dry).toFloat(); o[2] = lerp(lerp(50.0, 60.0, k), 100.0, dry).toFloat()
        }
        if (abs(lat) > 1.15 + 0.1 * f(5.0, 13.0)) { o[0] = 230f; o[1] = 238f; o[2] = 245f }
    }

    private fun cloudAt(lat: Double, lon: Double): Float {
        val cl = cos(lat)
        val q0 = cl * cos(lon); val q1 = sin(lat); val q2 = cl * sin(lon)
        val warp = noise3(q0 * 2, q1 * 2, q2 * 2, 20.0) * 2
        return sm(0.52, 0.72, noise3(q0 * 4 + warp, q1 * 7, q2 * 4 + warp, 14.0) * 0.7 + noise3(q0 * 12, q1 * 12, q2 * 12, 15.0) * 0.3).toFloat()
    }
}

// ---------------------------------------------------------------- The city

/** A row of towers with lit windows. Towers near the globe are kept short so it stays in view. */
private fun Sketch.city(base: Float, seed: Int, hMin: Float, hMax: Float, fill: Int, win: Int, winRate: Float, colW: Float, floor: Float, gx: Float = -1f, gR: Float = 0f) {
    val r = Rng(seed)
    var x = -U * 0.02f
    while (x < W) {
        val w = U * (colW * 0.5f + colW * r.next())
        var h = U * (hMin + (hMax - hMin) * r.next().pow(1.6f))
        if (gx >= 0 && abs(x + w / 2 - gx) < gR * 0.85f) h *= 0.3f
        rect(x, base - h, w, floor - (base - h), rgb(fill))
        if (r.next() < 0.3f) rect(x + w * 0.4f, base - h - U * 0.02f, w * 0.2f, U * 0.02f, rgb(fill))
        val cw = U * 0.008f
        val ch = U * 0.009f
        var wy = base - h + ch
        while (wy < base - ch) {
            var wx = x + cw
            while (wx < x + w - cw) {
                if (r.next() < winRate * (0.5f + 0.5f * sHash(floor(wy / ch) + seed))) rect(wx, wy, cw, ch * 0.7f, rgba(win, 0.35f + 0.4f * r.next()))
                wx += cw * 1.8f
            }
            wy += ch * 1.9f
        }
        x += w + U * 0.004f * r.next()
    }
}

private fun Sketch.slabX(gx: Float, gR: Float) = max(gx + gR * 1.08f, if (land) W * 0.68f else W * 0.78f)

/** The landmark towers' fixed parts: a curved glass slab and a spire. */
private fun Sketch.towersStatic(base: Float, gx: Float, gR: Float, floor: Float) {
    val sw = U * 0.13f
    val sx = slabX(gx, gR)
    val sh = U * if (land) 0.4f else 0.34f
    val p = op[2]; p.reset()
    p.moveTo(sx, base); p.lineTo(sx, base - sh); p.quadTo(sx + sw * 0.6f, base - sh - U * 0.03f, sx + sw, base - sh * 0.92f); p.lineTo(sx + sw, base); p.close()
    fill(p, vgrad(base - sh, base, 0f to rgb(0x2a1c3a), 1f to rgb(0x120c1e)))
    rect(sx, base, sw, floor - base, rgb(0x120c1e))
    c.save(); c.clipPath(p)
    var row = 0
    var y = base - sh
    while (y < floor) {
        var x = sx + U * 0.004f
        var col = 0
        while (x < sx + sw - U * 0.004f) {
            val lit = sHash(row * 31f + col * 7)
            if (lit < 0.55f) rect(x, y, U * 0.006f, U * 0.006f, rgba(0xFFCD96, 0.15f + 0.35f * lit))
            x += U * 0.009f; col++
        }
        y += U * 0.011f; row++
    }
    c.restore()
    // Spire, left of the globe.
    val tx = W * if (land) 0.12f else 0.14f
    val th = U * if (land) 0.5f else 0.46f
    p.reset(); p.moveTo(tx - U * 0.012f, floor); p.lineTo(tx - U * 0.008f, base - th * 0.8f); p.lineTo(tx + U * 0.008f, base - th * 0.8f); p.lineTo(tx + U * 0.012f, floor); p.close()
    fill(p, rgb(0x130e22))
    ellipse(tx, base - th * 0.82f, U * 0.04f, U * 0.018f, 0f, rgb(0x130e22))
    rect(tx - U * 0.03f, base - th * 0.86f, U * 0.06f, U * 0.03f, rgb(0x130e22))
    rect(tx - 1, base - th, 2f, th * 0.15f, rgb(0x130e22))
    rect(tx - U * 0.03f, base - th * 0.86f, U * 0.06f, 1f, rgba(0x78E6FF, 0.7f))
}

/** The landmark towers' lights: the slab's pulsing neon edge, the spire's turning crown and beacon. */
private fun Sketch.towersLive(base: Float, t: Float, gx: Float, gR: Float, floor: Float) {
    val sw = U * 0.13f
    val sx = slabX(gx, gR)
    val sh = U * if (land) 0.4f else 0.34f
    val pulse = 0.75f + 0.25f * sin(t * 0.8f)
    val p = op[2]; p.reset()
    p.moveTo(sx, floor); p.lineTo(sx, base - sh); p.quadTo(sx + sw * 0.6f, base - sh - U * 0.03f, sx + sw, base - sh * 0.92f)
    stroke(p, rgba(0xFF50AA, 0.9f * pulse), 1.6f)
    glow(sx, base - sh * 0.5f, U * 0.06f, 0xff50aa, 0.25f * pulse, sy = 3f)
    val tx = W * if (land) 0.12f else 0.14f
    val th = U * if (land) 0.5f else 0.46f
    for (k in 0 until 10) {
        val a = (k / 10f) * TAU + t * 0.4f
        if (sin(a) < 0) continue
        rect(tx + cos(a) * U * 0.038f - 1, base - th * 0.83f, 2f, 2f, rgba(0x78E6FF, 0.4f + 0.5f * sin(a)))
    }
    if (t % 1.6f < 0.8f) glow(tx, base - th, U * 0.012f, 0xff4040, 0.9f)
}

/** A palm in silhouette. */
private fun Sketch.palm(x: Float, base: Float, h: Float, seed: Int) {
    val lean = (sHash(seed * 5f) - 0.5f) * 0.25f
    val topx = x + lean * h
    val topy = base - h
    val trunk = op[3]; trunk.reset()
    trunk.moveTo(x, base); trunk.quadTo(x + lean * h * 0.2f, base - h * 0.5f, topx, topy)
    stroke(trunk, rgb(0x06040a), h * 0.035f, round = true)
    trunk.reset()
    for (k in 0 until 9) {
        val a = (k / 9f) * TAU
        val fl = h * (0.32f + 0.06f * sHash(k + seed * 3f))
        val ex = topx + cos(a) * fl
        val ey = topy + sin(a) * fl * 0.45f + fl * 0.25f
        val mx = topx + cos(a) * fl * 0.5f
        val my = topy + sin(a) * fl * 0.2f - fl * 0.12f
        val nx = -sin(a) * h * 0.02f
        val ny = cos(a) * h * 0.012f
        trunk.moveTo(topx, topy); trunk.quadTo(mx + nx, my + ny, ex, ey); trunk.quadTo(mx - nx, my - ny, topx, topy); trunk.close()
    }
    fill(trunk, rgb(0x06040a))
}

/** An airliner at night, side-on and heading left: a dark silhouette with lit cabin windows and its lights. */
private fun Sketch.jet(x: Float, y: Float, t: Float) {
    val L = U * 0.16f
    val hull = rgb(0x3a3258)
    c.save(); c.translate(x, y)
    val p = op[2]
    p.reset(); p.moveTo(-L * 0.05f, -L * 0.01f); p.lineTo(L * 0.22f, -L * 0.1f); p.lineTo(L * 0.27f, -L * 0.1f); p.lineTo(L * 0.12f, -L * 0.01f); p.close()
    fill(p, rgb(0x1c1830))
    p.reset(); p.moveTo(L * 0.34f, -L * 0.03f); p.lineTo(L * 0.44f, -L * 0.2f); p.lineTo(L * 0.5f, -L * 0.2f); p.lineTo(L * 0.48f, -L * 0.03f); p.close()
    p.moveTo(L * 0.38f, 0f); p.lineTo(L * 0.5f, L * 0.04f); p.lineTo(L * 0.52f, L * 0.02f); p.lineTo(L * 0.46f, -L * 0.005f); p.close()
    fill(p, hull)
    val body = op[3]; body.reset()
    body.moveTo(-L * 0.5f, L * 0.01f)
    body.quadTo(-L * 0.5f, -L * 0.045f, -L * 0.42f, -L * 0.045f)
    body.lineTo(L * 0.36f, -L * 0.045f)
    body.quadTo(L * 0.46f, -L * 0.04f, L * 0.52f, -L * 0.02f)
    body.lineTo(L * 0.5f, 0f)
    body.quadTo(L * 0.4f, L * 0.04f, L * 0.3f, L * 0.045f)
    body.lineTo(-L * 0.42f, L * 0.045f)
    body.quadTo(-L * 0.49f, L * 0.045f, -L * 0.5f, L * 0.01f)
    body.close()
    fill(body, hull)
    // The city's warm glow lights its belly.
    c.save(); c.clipPath(body)
    rect(-L, -L, L * 2, L * 2, vgrad(-L * 0.045f, L * 0.045f, 0f to rgba(0xFFA08C, 0f), 1f to rgba(0xFFA08C, 0.35f)))
    c.restore()
    line(-L * 0.46f, -L * 0.04f, L * 0.4f, -L * 0.04f, rgba(0xBEAAE6, 0.55f), 0.8f)
    p.reset(); p.moveTo(-L * 0.1f, L * 0.02f); p.lineTo(L * 0.18f, L * 0.16f); p.lineTo(L * 0.24f, L * 0.16f); p.lineTo(L * 0.1f, L * 0.02f); p.close()
    fill(p, rgb(0x332c4c))
    roundRect(-L * 0.04f, L * 0.05f, L * 0.12f, L * 0.04f, L * 0.02f, hull)
    rect(-L * 0.04f, L * 0.06f, L * 0.012f, L * 0.02f, rgba(0xFFBE78, 0.35f))
    for (k in 0 until 22) rect(-L * 0.36f + k * L * 0.03f, -L * 0.012f, L * 0.012f, L * 0.012f, rgba(0xFFD696, 0.85f))
    rect(-L * 0.47f, -L * 0.022f, L * 0.03f, L * 0.01f, rgba(0xC8DCFF, 0.7f))
    c.restore()
    // Lights: steady red on the near wingtip, a pulsing red beacon, a double white strobe, a landing light.
    glow(x + L * 0.21f, y + L * 0.16f, U * 0.006f, 0xff3a3a, 0.9f)
    glow(x - L * 0.05f, y - L * 0.05f, U * 0.01f, 0xff4040, if ((t * 0.9f) % 1f < 0.15f) 0.95f else 0.15f)
    val sp = (t * 0.8f) % 1f
    if (sp < 0.05f || (sp > 0.12f && sp < 0.17f)) {
        glow(x + L * 0.21f, y + L * 0.16f, U * 0.018f, 0xffffff, 0.9f)
        glow(x + L * 0.5f, y - L * 0.02f, U * 0.014f, 0xffffff, 0.8f)
    }
    glow(x - L * 0.47f, y + L * 0.02f, U * 0.014f, 0xfff4e0, 0.4f)
}

/** A car on the waterfront road: headlights reaching ahead, taillights behind. */
private fun Sketch.carLights(x: Float, y: Float) {
    val l = U * 0.045f
    rect(x - l * 0.5f, y - l * 0.3f, l, l * 0.3f, rgb(0x06040c))
    val p = op[2]; p.reset()
    p.moveTo(x + l * 0.5f, y - l * 0.15f); p.lineTo(x + l * 3, y - l * 0.4f); p.lineTo(x + l * 3, y + l * 0.2f); p.close()
    fillAdd(p, hgrad(x + l * 0.5f, x + l * 3, 0f to rgba(0xFFF0C8, 0.75f), 1f to rgba(0xFFF0C8, 0f)))
    glow(x + l * 0.5f, y - l * 0.12f, U * 0.022f, 0xfff2d0, 1f)
    glow(x - l * 0.5f, y - l * 0.12f, U * 0.014f, 0xff3030, 0.9f)
}
