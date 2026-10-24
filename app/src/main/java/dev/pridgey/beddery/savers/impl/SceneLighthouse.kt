package dev.pridgey.beddery.savers.impl

import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
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
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private const val TAU = (PI * 2).toFloat()

/** A lighthouse on a moonlit headland, its beam sweeping round every 14 seconds. Tap to send a boat across. */
@Composable
fun SceneLighthouseSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val paths = remember { Array(6) { Path() } }
    // Shifts the boat's schedule so a tap starts a crossing straight away.
    val boatOffset = remember { floatArrayOf(0f) }
    val cooldown = remember { Cooldown(30_000) }
    OnSaverTap {
        val t = time.value
        if (((t + boatOffset[0]) / 70f) % 1.4f >= 1f && cooldown.tryAcquire()) boatOffset[0] = -t
    }
    SketchScene(time) { t -> drawLighthouse(t, paths, boatOffset[0]) }
}

private val CloudLayers = arrayOf(floatArrayOf(0.2f, 0.7f, 3f, 0.6f), floatArrayOf(0.29f, 0.5f, 5f, 0.5f), floatArrayOf(0.37f, 0.6f, 2.2f, 0.45f))
private val CloudLobes = arrayOf(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(0.12f, -0.6f, 0.5f, 1f), floatArrayOf(0.4f, -0.9f, 0.35f, 1.1f), floatArrayOf(0.62f, -0.3f, 0.3f, 0.9f))

private fun Sketch.drawLighthouse(t: Float, p: Array<Path>, boatOffset: Float) {
    val hz = H * if (land) 0.56f else 0.5f
    val moonX = W * if (land) 0.18f else 0.24f
    val moonY = H * if (land) 0.2f else 0.14f

    // ---- Sky and moon.
    rect(0f, 0f, W, hz + 1, vgrad(0f, hz, 0f to rgb(0x060d22), 0.5f to rgb(0x102445), 0.85f to rgb(0x1f3d62), 1f to rgb(0x355a7c)))
    stars(0f, hz * 0.7f, if (land) 130 else 100, 21, t, 0xE6F0FF)
    moon(moonX, moonY, U * 0.045f, 0xF2F4EC, 0.18f)
    // Layered clouds, lit along their lower edge, wrapping fully off-screen at both ends.
    for ((cyF, cwF, speed, a) in CloudLayers) {
        val cy = H * cyF
        val cw = W * cwF
        val ch = U * 0.028f
        val cx = ((t * speed + cyF * 1500) % (W + cw * 2)) - cw
        for ((ox, oy, sw, sh) in CloudLobes) {
            val x0 = cx + cw * ox
            val y0 = cy + ch * oy
            val h0 = ch * sh
            roundRect(x0, y0, cw * sw, h0, h0 / 2, vgrad(y0, y0 + h0, 0f to rgba(0x0e1d3a, a), 0.7f to rgba(0x1d3456, a), 1f to rgba(0x8aa4c4, a * 0.7f)))
        }
    }

    // ---- Headland position: the lighthouse stands on it.
    val lx = W * if (land) 0.8f else 0.74f
    val top = H * if (land) 0.46f else 0.42f
    val towerH = U * if (land) 0.3f else 0.33f
    val towerW = towerH * 0.16f
    val lampX = lx
    val lampY = top - towerH * 1.05f
    val ang = (t / 14) * TAU + 0.9f
    val side = sin(ang)
    val toward = cos(ang)

    // ---- Distant islands, one with a lit cottage.
    c.save(); c.clipRect(0f, 0f, W, hz + 1)
    ridge(hz + 1, H * 0.035f, 3f, 1.2f, rgb(0x1a3050), hz + 2, 0f, false, null, W * 0.55f)
    c.restore()
    c.save(); c.translate(W * 0.08f, 0f)
    ridge(hz + 1, H * 0.022f, 8f, 1.6f, rgb(0x142742), hz + 2, 0f, false, null, W * 0.32f)
    c.restore()
    rect(W * 0.2f, hz - H * 0.012f, 2.4f, 2.4f, rgb(0xffc477))
    glow(W * 0.2f + 1, hz - H * 0.011f, U * 0.03f, 0xffb060, 0.3f)

    // ---- The sea: bands that darken toward us, a moonlit path, gentle swells.
    rect(0f, hz, W, H - hz, vgrad(hz, H, 0f to rgb(0x2a4c6c), 0.15f to rgb(0x183450), 0.5f to rgb(0x0c1e34), 1f to rgb(0x050c18)))
    for (i in 0 until 60) {
        val q = i / 60f
        val y = hz + 2 + (H - hz) * q * q * 0.9f
        val spread = U * (0.02f + 0.22f * q)
        val x = moonX + (sHash(i * 7f) - 0.5f) * 2 * spread + sin(t * 0.5f + i) * spread * 0.1f
        val half = spread * (0.12f + 0.25f * sHash(i * 3f)) * (0.6f + 0.4f * sin(t * 1.3f + i * 2))
        rect(x - half, y, half * 2, 1 + q * 1.5f, rgba(0xE6F0FF, 0.5f * (1 - q * 0.7f)))
    }
    for (i in 0 until 26) {
        val q = (i + 0.5f) / 26
        val y = hz + (H - hz) * q * q
        val len = W * (0.08f + 0.2f * q)
        val x = ((sHash(i * 11f) * W + t * (5 + 25 * q)) % (W + len)) - len
        rect(x, y, len, 1 + q, rgba(0x8CB4DC, 0.06f + 0.08f * q))
    }
    // A small boat crossing far out, lights on, fading in and out at the ends of its crossing.
    val boatP = ((t + boatOffset) / 70f) % 1.4f
    if (boatP in 0f..1f) {
        val alpha = sSmooth(0f, 0.08f, boatP) * sSmooth(1f, 0.85f, boatP)
        c.saveLayerAlpha(0f, hz - U * 0.05f, W, hz + U * 0.05f, (alpha * 255).toInt())
        val bx = W * (-0.05f + boatP * if (land) 0.6f else 0.62f)
        val by = hz + H * 0.008f
        val bs = U * 0.03f
        val hull = p[0]; hull.reset()
        hull.moveTo(bx - bs, by); hull.lineTo(bx + bs, by); hull.lineTo(bx + bs * 0.8f, by + bs * 0.18f); hull.lineTo(bx - bs * 0.85f, by + bs * 0.18f); hull.close()
        fill(hull, rgb(0x08111f))
        rect(bx - bs * 0.4f, by - bs * 0.3f, bs * 0.6f, bs * 0.3f, rgb(0x08111f))
        for (k in 0 until 3) rect(bx - bs * 0.32f + k * bs * 0.18f, by - bs * 0.2f, 1.5f, 1.5f, rgb(0xffd08a))
        rect(bx, by - bs * 0.7f, 1.5f, 1.5f, rgb(0xffffff))
        rect(bx - bs * 0.3f, by + bs * 0.25f, bs * 0.4f, bs * 0.4f, rgba(0xFFD28C, 0.2f))
        c.restore()
    }

    // ---- The headland: faceted rock columns, drawn back to front.
    val cliffL = lx - W * if (land) 0.17f else 0.3f
    val shore = H * if (land) 0.7f else 0.64f
    val span = W - cliffL
    fun col(x0: Float, x1: Float, y: Float, seed: Float, scale: Float = 1f) {
        val d = (x1 - x0) * 0.16f
        val sag = U * 0.012f * scale
        val tlx = x0 + d * 1.6f; val tly = y + sag * 0.4f
        val trx = x1 - d * 0.5f; val try_ = y - sag * 0.3f + (sHash(seed) - 0.5f) * sag
        val front = p[1]; front.reset()
        front.moveTo(x0 + d * 0.4f, shore + 2); front.lineTo(x0 + d * 0.8f, y + sag * 2.2f); front.lineTo(tlx, tly)
        front.lineTo(trx, try_); front.lineTo(x1, y + sag * 1.4f); front.lineTo(x1 + d * 0.2f, shore + 2); front.close()
        fill(front, vgrad(y, shore, 0f to rgb(0x22365a), 1f to rgb(0x0c1628)))
        // The face turned toward the moon.
        val face = p[2]; face.reset()
        face.moveTo(x0, shore + 2); face.lineTo(x0 + d * 0.4f, y + sag * 3); face.lineTo(tlx, tly); face.lineTo(x0 + d * 0.8f, y + sag * 2.2f); face.lineTo(x0 + d * 0.4f, shore + 2); face.close()
        fill(face, vgrad(y, shore, 0f to rgb(0x4a6a94), 1f to rgb(0x1a2a44)))
        // The top surface.
        val topS = p[3]; topS.reset()
        topS.moveTo(x0 + d * 0.4f, y + sag * 3); topS.lineTo(x0 + d * 1.1f, y - sag * 0.2f); topS.lineTo(trx - d * 0.3f, try_ - sag * 0.6f); topS.lineTo(trx, try_); topS.lineTo(tlx, tly); topS.close()
        fill(topS, rgb(0x5a7aa4))
        c.save(); c.clipPath(front)
        // Moonlight falls across the face from the left.
        rect(x0, y - U * 0.05f, x1 - x0 + d, shore - y + U * 0.06f, hgrad(x0, x1, 0f to rgba(0x6E8CBE, 0.28f), 0.45f to rgba(0x6E8CBE, 0.06f), 1f to rgba(0, 0.25f)))
        // Cracks running down the front.
        val n = 1 + floor(sHash(seed + 3) * 2).toInt()
        val crack = p[4]
        for (k in 0 until n) {
            val cx0 = sLerp(tlx, trx, (k + 0.5f + (sHash(seed + k) - 0.5f) * 0.5f) / n)
            crack.reset(); crack.moveTo(cx0, y)
            var cx = cx0
            var cy = y
            while (cy < shore) {
                cy += U * (0.05f + 0.05f * sHash(seed + cy))
                cx += (sHash(seed + cy * 0.7f) - 0.5f) * U * 0.025f
                crack.lineTo(cx, cy)
            }
            stroke(crack, rgba(0x060C18, 0.7f), 1.8f)
        }
        // Horizontal bedding lines, faint.
        for (k in 1 until 5) {
            val yy = sLerp(y, shore, k / 5f)
            line(x0, yy, x1, yy + U * 0.01f, rgba(0x7896BE, 0.08f), 1.8f)
        }
        c.restore()
    }
    col(cliffL + span * 0.55f, W + U * 0.05f, top + U * 0.004f, 31f)
    col(cliffL + span * 0.3f, cliffL + span * 0.62f, top + U * 0.01f, 23f)
    col(cliffL + span * 0.08f, cliffL + span * 0.36f, sLerp(top, shore, 0.18f), 17f)
    col(cliffL - span * 0.06f, cliffL + span * 0.14f, sLerp(top, shore, 0.48f), 11f, 0.8f)
    // A sea stack standing off the point.
    col(cliffL - span * 0.26f, cliffL - span * 0.14f, sLerp(top, shore, 0.66f), 5f, 0.6f)

    // A grassy cap with a ragged overhang, lit along its top.
    val capL = cliffL + span * 0.3f
    val cap = p[1]; cap.reset()
    cap.moveTo(capL, top - U * 0.006f)
    var x = capL
    while (x <= W + 6) { cap.lineTo(x, top - U * 0.008f - sHash(x * 0.21f) * U * 0.006f); x += 6 }
    cap.lineTo(W + 6, top + U * 0.01f)
    x = W + 6
    while (x >= capL) { cap.lineTo(x, top + U * (0.008f + 0.012f * sHash(x * 0.37f))); x -= 5 }
    cap.close()
    fill(cap, rgb(0x16261c))
    rect(capL, top - U * 0.012f, W - capL + 6, U * 0.004f, rgb(0x2c4a34))
    x = capL
    while (x < W) {
        val hgt = U * (0.004f + 0.01f * sHash(x * 0.31f))
        rect(x, top - U * 0.01f - hgt, 1.1f, hgt, rgb(if (sHash(x) > 0.5f) 0x2c4a34 else 0x1e3426))
        x += 2.2f
    }
    // Surf breaking round the rocks: irregular foam that swells and drains.
    for (k in 0 until 26) {
        val fx = cliffL - span * 0.3f + (W - cliffL + span * 0.3f) * sHash(k * 2.3f)
        val surge = 0.5f + 0.5f * sin(t * 0.7f - fx * 0.02f + sHash(k.toFloat()) * 3)
        val w0 = U * (0.015f + 0.04f * sHash(k * 5f)) * (0.5f + 0.6f * surge)
        ellipse(fx, shore + 1 + sHash(k * 9f) * U * 0.012f, w0, U * 0.003f + w0 * 0.08f, 0f, rgba(0xD7E8F6, 0.08f + 0.32f * surge * sHash(k * 7f)))
    }

    // ---- Keeper's cottage: a lit gable end, a side wall in shadow, a slate roof.
    val gw = towerH * 0.26f
    val sideW = towerH * 0.36f
    val wh2 = towerH * 0.16f
    val hx = lx + towerW * 1.3f
    val base = top - U * 0.008f
    val apexX = hx + gw / 2
    val apexY = base - wh2 - gw * 0.45f
    val rise = -wh2 * 0.06f
    if (hx < W) {
        val q = p[1]
        q.reset(); q.moveTo(hx + gw, base); q.lineTo(hx + gw + sideW, base + rise); q.lineTo(hx + gw + sideW, base - wh2 + rise); q.lineTo(hx + gw, base - wh2); q.close()
        fill(q, rgb(0x56607a))
        q.reset(); q.moveTo(apexX, apexY); q.lineTo(apexX + sideW, apexY + rise); q.lineTo(hx + gw + sideW + gw * 0.08f, base - wh2 + rise + gw * 0.04f); q.lineTo(hx + gw + gw * 0.08f, base - wh2 + gw * 0.04f); q.close()
        fill(q, rgb(0x26304a))
        line(apexX, apexY, apexX + sideW, apexY + rise, rgba(0xA0B4DC, 0.35f), 1f)
        for (k in 1 until 5) {
            val f = k / 5f
            line(sLerp(apexX, hx + gw * 1.08f, f), sLerp(apexY, base - wh2 + gw * 0.04f, f), sLerp(apexX + sideW, hx + gw * 1.08f + sideW, f), sLerp(apexY + rise, base - wh2 + rise + gw * 0.04f, f), rgba(0x0A0E1C, 0.35f), 1f)
        }
        // Gable end, catching the moon.
        q.reset(); q.moveTo(hx, base); q.lineTo(hx + gw, base); q.lineTo(hx + gw, base - wh2); q.lineTo(apexX, apexY); q.lineTo(hx, base - wh2); q.close()
        fill(q, rgb(0xb4bfd2))
        q.reset(); q.moveTo(hx - gw * 0.06f, base - wh2 + gw * 0.03f); q.lineTo(apexX, apexY - 1); q.lineTo(hx + gw * 1.06f, base - wh2 + gw * 0.03f)
        stroke(q, rgb(0x1e2638), 1.6f)
        // Windows warm behind their glazing bars.
        fun win(wx: Float, wy: Float, w: Float, h: Float, skew: Float = 0f) {
            val r = p[5]; r.reset()
            r.moveTo(wx, wy); r.lineTo(wx + w, wy + skew); r.lineTo(wx + w, wy + h + skew); r.lineTo(wx, wy + h); r.close()
            fill(r, rgb(0xffc477))
            line(wx + w / 2, wy + skew / 2, wx + w / 2, wy + h + skew / 2, rgba(0x3C1E0A, 0.7f), 1f)
            line(wx, wy + h / 2, wx + w, wy + h / 2 + skew, rgba(0x3C1E0A, 0.7f), 1f)
            glow(wx + w / 2, wy + h / 2, w * 2.2f, 0xffb060, 0.28f)
        }
        win(hx + gw * 0.36f, base - wh2 * 0.72f, gw * 0.28f, wh2 * 0.42f)
        win(hx + gw + sideW * 0.18f, base - wh2 * 0.7f + rise * 0.2f, sideW * 0.16f, wh2 * 0.4f, rise * 0.16f)
        win(hx + gw + sideW * 0.6f, base - wh2 * 0.7f + rise * 0.6f, sideW * 0.16f, wh2 * 0.4f, rise * 0.16f)
        // Chimney and smoke.
        val chx = apexX + sideW * 0.7f
        val chy = apexY + rise * 0.7f + gw * 0.08f
        rect(chx, chy - gw * 0.3f, gw * 0.14f, gw * 0.3f, rgb(0x3a2e3a))
        rect(chx - 1, chy - gw * 0.32f, gw * 0.14f + 2, gw * 0.04f, rgb(0x4a3c48))
        for (k in 0 until 8) {
            val f = (t * 0.06f + k / 8f) % 1f
            glow(chx + gw * 0.07f - f * gw * 1.4f, chy - gw * 0.35f - f * H * 0.08f, gw * (0.08f + 0.3f * f), 0x8a9ab8, 0.12f * sin(f * PI.toFloat()), additive = false)
        }
        // A short picket fence and a path to the tower.
        rect(lx + towerW * 0.4f, base, hx - lx - towerW * 0.4f, 2f, rgba(0xAAB4C8, 0.15f))
        var fxp = hx - gw * 0.9f
        while (fxp < hx - gw * 0.1f) { rect(fxp, base - wh2 * 0.3f, 1.4f, wh2 * 0.3f, rgb(0x8a94a8)); fxp += gw * 0.12f }
        rect(hx - gw * 0.9f, base - wh2 * 0.22f, gw * 0.8f, 1f, rgb(0x8a94a8))
    }

    // ---- The tower: white and red bands, lit from the moon's side.
    val tb = top + 1
    val tt = top - towerH
    val bw = towerW
    val tw2 = towerW * 0.7f
    val tower = p[1]; tower.reset()
    tower.moveTo(lx - bw / 2, tb); tower.lineTo(lx - tw2 / 2, tt); tower.lineTo(lx + tw2 / 2, tt); tower.lineTo(lx + bw / 2, tb); tower.close()
    c.save(); c.clipPath(tower)
    for (i in 0 until 5) rect(lx - bw, tt + (tb - tt) * i / 5, bw * 2, (tb - tt) / 5 + 1, rgb(if (i % 2 == 1) 0xb8343a else 0xeef0f2))
    rect(lx, tt, bw, tb - tt, rgba(0x081024, 0.55f))
    rect(lx - bw / 2, tt, bw * 0.18f, tb - tt, rgba(0xFFFFFF, 0.12f))
    rect(lx - bw * 0.06f, tt + (tb - tt) * 0.48f, bw * 0.12f, bw * 0.2f, rgb(0xffcf88))
    c.restore()
    // Gallery, lantern and dome.
    rect(lx - tw2 * 0.85f, tt - towerW * 0.08f, tw2 * 1.7f, towerW * 0.1f, rgb(0x1b2233))
    rect(lx - tw2 * 0.8f, tt - towerW * 0.32f, tw2 * 1.6f, 1.2f, rgb(0x1b2233))
    for (k in 0..6) rect(lx - tw2 * 0.8f + tw2 * 1.6f * k / 6, tt - towerW * 0.32f, 1f, towerW * 0.24f, rgb(0x1b2233))
    val roomTop = lampY - towerW * 0.28f
    val roomBot = tt - towerW * 0.08f
    rect(lx - tw2 * 0.42f, roomTop, tw2 * 0.84f, roomBot - roomTop, rgb(0xfff4d6))
    for (k in 1 until 3) rect(lx - tw2 * 0.42f + tw2 * 0.84f * k / 3, roomTop, 1f, roomBot - roomTop, rgba(0x3C2814, 0.5f))
    val dome = p[1]; dome.reset()
    dome.moveTo(lx - tw2 * 0.55f, roomTop); dome.quadTo(lx, roomTop - towerW * 0.6f, lx + tw2 * 0.55f, roomTop); dome.close()
    fill(dome, rgb(0x1b2233))
    rect(lx - 0.6f, roomTop - towerW * 0.7f, 1.2f, towerW * 0.2f, rgb(0x1b2233))

    // ---- The beam: one layer whichever way it points, so nothing jumps as it swings past side-on.
    run {
        val reach = (if (side < 0) lampX else W - lampX) + W * 0.5f
        val len = reach * abs(side).pow(0.8f)
        // The cone fades out as it swings toward us; the flash takes over.
        val fade = sSmooth(0.08f, 0.55f, abs(side))
        if (len >= 4 && fade > 0) {
            val dir = if (side < 0) -1f else 1f
            val spread = U * 0.02f + len * 0.07f
            val ex = lampX + dir * len
            val ey = lampY + len * 0.03f
            val s = (0.28f + 0.1f * toward) * fade
            val b = p[1]; b.reset()
            b.moveTo(lampX, lampY - towerW * 0.15f); b.lineTo(ex, ey - spread / 2); b.lineTo(ex, ey + spread / 2); b.lineTo(lampX, lampY + towerW * 0.15f); b.close()
            fillAdd(b, hgrad(lampX, ex, 0f to rgba(0xfff2cc, s), 0.5f to rgba(0xffe8b8, s * 0.35f), 1f to rgba(0xffe8b8, 0f)))
        }
    }
    glow(lampX, lampY, towerW * 1.4f, 0xfff0cc, 0.6f)
    val glare = max(0f, toward).pow(22)
    if (glare > 0.01f) {
        glow(lampX, lampY, U * (0.15f + 0.4f * glare), 0xfff2d6, 0.7f * glare)
        rectAdd(lampX - U, lampY - 1.5f, U * 2, 3f, hgrad(lampX - U, lampX + U, 0f to rgba(0xFFF0D2, 0f), 0.5f to rgba(0xFFF0D2, 0.4f * glare), 1f to rgba(0xFFF0D2, 0f)))
    }
    // The light's reflection on the water below the headland.
    for (k in 0 until 10) {
        val y = shore + 6 + k * U * 0.012f
        val half = towerW * (0.3f + 0.6f * sHash(k.toFloat())) * (0.6f + 0.4f * sin(t * 1.5f + k))
        rect(lx - half, y, half * 2, 1.2f, rgba(0xFFE6B4, 0.22f * (1 - k / 10f)))
    }

    // ---- Foreground boulders with surf, on the side away from the clock.
    fun boulder(cx: Float, base: Float, w: Float, h: Float, seed: Float) {
        val pk = cx + (sHash(seed) - 0.4f) * w * 0.3f
        val b = p[1]; b.reset()
        b.moveTo(cx - w / 2, H + 4); b.lineTo(cx - w * 0.48f, base - h * 0.35f); b.lineTo(cx - w * 0.3f, base - h * 0.85f); b.lineTo(pk, base - h)
        b.lineTo(cx + w * 0.32f, base - h * 0.78f); b.lineTo(cx + w / 2, base - h * 0.25f); b.lineTo(cx + w / 2, H + 4); b.close()
        fill(b, vgrad(base - h, H, 0f to rgb(0x14223a), 1f to rgb(0x04080f)))
        // Moonlit facet on the upper left, a softer one on the top.
        b.reset(); b.moveTo(cx - w * 0.48f, base - h * 0.35f); b.lineTo(cx - w * 0.3f, base - h * 0.85f); b.lineTo(pk, base - h); b.lineTo(cx - w * 0.12f, base - h * 0.55f); b.close()
        fill(b, rgba(0x5A78AA, 0.45f))
        b.reset(); b.moveTo(pk, base - h); b.lineTo(cx + w * 0.32f, base - h * 0.78f); b.lineTo(cx + w * 0.1f, base - h * 0.5f); b.lineTo(cx - w * 0.12f, base - h * 0.55f); b.close()
        fill(b, rgba(0x465F8C, 0.3f))
        // Foam swelling and draining round the base.
        val surge = 0.5f + 0.5f * sin(t * 0.7f + seed)
        for (k in 0 until 5) {
            val fx = cx + (sHash(seed * 3 + k) - 0.5f) * w * 1.2f
            val fy = base - h * 0.15f + sHash(seed + k * 7) * h * 0.25f
            ellipse(fx, fy, w * (0.12f + 0.15f * sHash(k + seed * 5)) * (0.6f + 0.4f * surge), U * 0.004f, 0f, rgba(0xD2E4F4, (0.1f + 0.25f * surge) * sHash(seed + k)))
        }
    }
    if (land) {
        boulder(W * 0.08f, H * 0.92f, U * 0.32f, U * 0.16f, 3f)
        boulder(W * 0.2f, H * 0.97f, U * 0.2f, U * 0.08f, 7f)
    } else {
        boulder(W * 0.08f, H * 0.9f, U * 0.42f, U * 0.2f, 3f)
        boulder(W * 0.3f, H * 0.97f, U * 0.24f, U * 0.08f, 7f)
    }
    grain(0.05f)
}
