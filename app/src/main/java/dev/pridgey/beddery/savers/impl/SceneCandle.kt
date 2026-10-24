package dev.pridgey.beddery.savers.impl

import android.graphics.Path
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
import dev.pridgey.beddery.savers.sFlicker
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sNoise
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Three candles on the sill of an open window on a summer night. Tap and a breath of air bends the flames. */
@Composable
fun CandleSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val paths = remember { Array(4) { Path() } }
    val breath = remember { floatArrayOf(-100f) }
    val cooldown = remember { Cooldown(20_000) }
    OnSaverTap { if (cooldown.tryAcquire()) breath[0] = time.value }
    SketchScene(time) { t -> drawCandle(t, paths, t - breath[0]) }
}

private data class Pillar(val dx: Float, val h: Float, val w: Float, val seed: Float)

private val Pillars = listOf(Pillar(-0.07f, 0.2f, 0.075f, 1f), Pillar(0f, 0.3f, 0.09f, 2f), Pillar(0.075f, 0.14f, 0.07f, 3f))

private fun Sketch.drawCandle(t: Float, p: Array<Path>, sinceBreath: Float) {
    val winX: Float
    val winY: Float
    val winW: Float
    val winH: Float
    if (land) { winX = W * 0.36f; winY = H * 0.07f; winW = W * 0.46f; winH = H * 0.5f } else { winX = W * 0.14f; winY = H * 0.1f; winW = W * 0.72f; winH = H * 0.44f }
    val sill = winY + winH + U * 0.03f
    val groupX = if (land) W * 0.2f else W * 0.6f
    fun flick(s: Float) = 0.9f + 0.1f * sFlicker(t, s)
    val f = flick(1f)
    // A tap is a breath across the flames: they lean and gutter, then settle.
    val gust = if (sinceBreath in 0f..5f) sin(sinceBreath / 5f * PI.toFloat()).let { it * it } else 0f
    val lightX = groupX
    val lightY = sill - U * 0.012f - Pillars[1].h * U - U * 0.04f

    // ---- Wall: warm plaster lit by the candles.
    rect(0f, 0f, W, H, rgb(0x1a110f))
    glow(lightX, lightY, U * 1.4f, 0x6a3a22, 0.6f * f, additive = false)
    glow(lightX, lightY, U * 0.6f, 0xa05a30, 0.3f * f)
    grain(0.06f)

    // ---- Outside: a moonlit summer garden.
    c.save()
    c.clipRect(winX, winY, winX + winW, winY + winH)
    rect(winX, winY, winW, winH, vgrad(winY, winY + winH, 0f to rgb(0x081428), 0.6f to rgb(0x14304a), 1f to rgb(0x2a4a5a)))
    c.save(); c.translate(winX, winY); stars(0f, winH * 0.5f, 45, 8, t, 0xEEF4FF, width = winW); c.restore()
    val mx = winX + winW * 0.3f
    val my = winY + winH * 0.24f
    moon(mx, my, U * 0.05f, 0xFBF3D8, 0.2f)
    c.save(); c.translate(winX, 0f)
    ridge(winY + winH * 0.7f, winH * 0.12f, 6f, 1.3f, rgb(0x1e3a4a), winY + winH, 0f, false, rgba(0xC8E6F0, 0.25f), width = winW)
    mist(winY + winH * 0.72f, winH * 0.06f, 0x5a8090, 0.35f, width = winW)
    // A meadow, with tall grasses and flowers catching the moon.
    val meadow = winY + winH * 0.8f
    rect(0f, meadow, winW, winH, rgb(0x132a30))
    val grass = p[0]
    for (i in 0 until 160) {
        val x = sHash(i * 3f) * winW
        val h = winH * (0.04f + 0.1f * sHash(i * 5f))
        val sw = sin(t * 0.8f + x * 0.05f) * h * 0.12f
        grass.reset(); grass.moveTo(x, winY + winH); grass.quadTo(x + sw * 0.3f, meadow + winH * 0.05f, x + sw, meadow + winH * 0.12f - h)
        stroke(grass, rgb(if (i % 3 != 0) 0x0e2228 else 0x1c3a3e), 1f)
        if (sHash(i * 7f) > 0.8f) circle(x + sw, meadow + winH * 0.12f - h, 1.6f, rgb(if (sHash(i.toFloat()) > 0.5f) 0xe8e4d0 else 0xc8b0e0))
    }
    // A post-and-rail fence across the meadow.
    for (k in 0 until 7) rect(winW * (0.05f + k * 0.16f), meadow - winH * 0.03f, 3f, winH * 0.08f, rgb(0x0c1a1e))
    rect(0f, meadow - winH * 0.02f, winW, 2f, rgb(0x0c1a1e))
    rect(0f, meadow + winH * 0.015f, winW, 2f, rgb(0x0c1a1e))
    // A lone tree: limbs and twigs grown by branching, leaves clustered toward the tips.
    val tr = Rng(77)
    val tips = ArrayList<Float>()
    val limb = p[1]
    fun grow(x: Float, y: Float, len: Float, ang: Float, depth: Int, w: Float) {
        val a = ang + sin(t * 0.5f + depth) * 0.012f * (7 - depth)
        val ex = x + sin(a) * len
        val ey = y - cos(a) * len
        limb.reset(); limb.moveTo(x, y); limb.quadTo(x + sin(a + 0.15f) * len * 0.5f, y - cos(a + 0.15f) * len * 0.5f, ex, ey)
        stroke(limb, rgb(0x071216), w, round = true)
        if (depth <= 2) { tips += ex; tips += ey }
        if (depth == 0) return
        val kids = if (depth > 4) 2 else 2 + if (tr.next() > 0.6f) 1 else 0
        for (k in 0 until kids) {
            val spread = (k - (kids - 1) / 2f) * (0.5f + 0.25f * tr.next()) + (tr.next() - 0.5f) * 0.25f
            grow(ex, ey, len * (0.7f + 0.12f * tr.next()), a + spread * if (depth > 4) 0.8f else 1f, depth - 1, max(0.7f, w * 0.66f))
        }
    }
    val ox = winW * 0.74f
    val oy = meadow + winH * 0.04f
    grow(ox, oy, winH * 0.14f, -0.05f, 6, winW * 0.028f)
    // Leaves: small dark clumps, the moon-facing edge of each picked out faintly.
    for (j in 0 until tips.size / 2) {
        val x = tips[j * 2]
        val y = tips[j * 2 + 1]
        for (k in 0 until 5) {
            val lx = x + (tr.next() - 0.5f) * winW * 0.05f
            val ly = y + (tr.next() - 0.6f) * winH * 0.04f
            val r = winW * (0.012f + 0.012f * tr.next())
            ellipse(lx, ly, r * 1.3f, r, tr.next() * 3, rgb(0x081619))
            if (lx < ox + winW * 0.02f) ellipse(lx - r * 0.35f, ly - r * 0.3f, r * 0.6f, r * 0.4f, 0f, rgba(0x82AFB4, 0.12f))
        }
    }
    c.restore()
    // Fireflies drifting over the meadow.
    for (i in 0 until 18) {
        val x = winX + winW * (sHash(i * 3f) + sin(t * 0.13f + i) * 0.05f)
        val y = winY + winH * (0.6f + 0.3f * sHash(i * 7f)) + sin(t * 0.4f + i * 2) * winH * 0.03f
        val on = max(0f, sin(t * (0.5f + sHash(i.toFloat()) * 0.6f) + i * 3))
        glow(x, y, U * 0.025f, 0xd8ff7a, 0.55f * on)
        rect(x - 1, y - 1, 2f, 2f, rgba(0xF0FFBE, on))
    }
    c.restore()

    // ---- Casement windows swung open, glass catching the candlelight.
    val fw = U * 0.02f
    val frame = rgb(0x3a2a22)
    rect(winX - fw, winY - fw, winW + fw * 2, fw, frame)
    rect(winX - fw, winY, fw, winH, frame)
    rect(winX + winW, winY, fw, winH, frame)
    val leaf = p[2]
    for (side in floatArrayOf(-1f, 1f)) {
        val hinge = if (side < 0) winX else winX + winW
        val depth = winW * 0.16f
        leaf.reset()
        leaf.moveTo(hinge, winY + U * 0.004f); leaf.lineTo(hinge - side * depth, winY + winH * 0.06f)
        leaf.lineTo(hinge - side * depth, winY + winH * 0.94f); leaf.lineTo(hinge, winY + winH - U * 0.004f); leaf.close()
        fill(leaf, rgba(0x28465A, 0.35f))
        stroke(leaf, frame, U * 0.012f)
        line(hinge - side * depth / 2, winY + winH * 0.03f, hinge - side * depth / 2, winY + winH * 0.97f, frame, U * 0.006f)
        line(hinge, winY + winH * 0.5f, hinge - side * depth, winY + winH * 0.5f, frame, U * 0.006f)
        // Reflection of the flames in the near pane.
        if (abs(hinge - lightX) < W * 0.4f) glow(hinge - side * depth * 0.5f, winY + winH * 0.75f, depth * 0.6f, 0xffb060, 0.3f * f, sy = 1.6f)
    }

    // ---- A sheer curtain, lifted by the breeze, lit warm on the candle side.
    val rodY = winY - fw * 2.6f
    roundRect(winX - winW * 0.1f, rodY - U * 0.006f, winW * 1.2f, U * 0.012f, U * 0.006f, rgb(0x6a4a30))
    val cLeft = if (land) winX - winW * 0.04f else winX + winW * 0.62f
    val cw = winW * if (land) 0.36f else 0.42f
    val breeze = sin(t * 0.5f) * 0.5f + sin(t * 1.3f) * 0.2f + gust * 0.8f
    val sheer = p[3]
    for (fold in 0 until 6) {
        val x0 = cLeft + cw * fold / 6f
        val x1 = cLeft + cw * (fold + 1) / 6f
        val bottom = sill - U * 0.01f
        fun billow(q: Float) = breeze * U * 0.06f * sin(q * PI.toFloat()) * (0.6f + 0.4f * sin(fold + t * 0.7f))
        sheer.reset()
        sheer.moveTo(x0, rodY); sheer.lineTo(x1, rodY)
        sheer.cubicTo(x1 + billow(0.3f), rodY + (bottom - rodY) * 0.35f, x1 + billow(0.7f) * 1.6f, rodY + (bottom - rodY) * 0.7f, x1 + billow(1f) * 1.8f, bottom)
        sheer.lineTo(x0 + billow(1f) * 1.8f, bottom)
        sheer.cubicTo(x0 + billow(0.7f) * 1.6f, rodY + (bottom - rodY) * 0.7f, x0 + billow(0.3f), rodY + (bottom - rodY) * 0.35f, x0, rodY)
        sheer.close()
        fill(sheer, rgba(0xFAF0E1, if (fold % 2 != 0) 0.13f else 0.08f))
    }
    // Warm light through the sheer nearest the flames.
    glow(cLeft + if (lightX > cLeft) cw else 0f, (rodY + sill) / 2, cw * 0.8f, 0xffb070, 0.12f * f, sy = 1.6f)

    // ---- Panelling below, and the sill.
    val panTop = sill + U * 0.07f
    if (panTop < H) {
        rect(0f, panTop - U * 0.01f, W, H, rgb(0x1c1210))
        rect(0f, panTop - U * 0.01f, W, 1f, rgba(0xff9a62, 0.14f * f))
        val n = if (land) 6 else 3
        val gap = U * 0.03f
        val pw = (W - gap * (n + 1)) / n
        val panel = p[0]
        for (i in 0 until n) {
            val x = gap + i * (pw + gap)
            panel.reset(); panel.addRect(x, panTop + gap * 0.6f, x + pw, panTop + gap * 0.6f + H, Path.Direction.CW)
            stroke(panel, rgba(0x000000, 0.45f), 2f)
            line(x + 1, panTop + gap * 0.6f + 1, x + pw - 1, panTop + gap * 0.6f + 1, rgba(0xff9a62, 0.09f * f * (1 - min(1f, abs(x + pw / 2 - lightX) / W))), 1f)
        }
        glow(lightX, panTop, U * 0.6f, 0xff9a62, 0.12f * f, sy = 0.5f)
    }
    val sx0 = if (land) 0f else winX - fw * 2.5f
    val sx1 = winX + winW + fw * 2.5f
    rect(sx0, sill - U * 0.006f, sx1 - sx0, U * 0.02f, rgb(0x4a3426))
    rect(sx0, sill + U * 0.014f, sx1 - sx0, U * 0.04f, vgrad(sill + U * 0.014f, sill + U * 0.05f, 0f to rgba(0, 0.5f), 1f to rgba(0, 0f)))
    rect(sx0, sill - U * 0.006f, sx1 - sx0, 1f, rgba(0xffb07a, 0.45f * f))
    glow(lightX, sill - U * 0.004f, U * 0.4f, 0xffb070, 0.5f * f, sy = 0.12f)

    // ---- A jug of wildflowers.
    val jx = if (land) winX + winW * 0.8f else winX + winW * 0.16f
    val stem = p[1]
    for (k in 0 until 9) {
        val a = -PI.toFloat() / 2 + (sHash(k * 3f) - 0.5f) * 1.1f
        val len = U * (0.08f + 0.06f * sHash(k * 5f))
        val sw = sin(t * 0.6f + k) * 0.03f
        val ex = jx + cos(a + sw) * len
        val ey = sill - U * 0.06f + sin(a + sw) * len
        stem.reset(); stem.moveTo(jx, sill - U * 0.06f); stem.quadTo(jx + (ex - jx) * 0.3f, ey + len * 0.4f, ex, ey)
        stroke(stem, rgb(0x2a4a2c), 1f)
        if (k % 3 == 0) {
            for (q in 0 until 6) ellipse(ex + cos(q.toFloat()) * U * 0.007f, ey + sin(q.toFloat()) * U * 0.007f, U * 0.005f, U * 0.0025f, q.toFloat(), rgb(0xf2ece0))
            circle(ex, ey, U * 0.0035f, rgb(0xf2c040))
        } else {
            val col = rgb(if (k % 3 == 1) 0x9a7ad0 else 0xc86a8a)
            for (q in 0 until 4) circle(ex, ey + q * U * 0.006f, U * 0.0035f, col)
        }
    }
    val jug = p[2]
    jug.reset()
    jug.moveTo(jx - U * 0.025f, sill - U * 0.065f); jug.quadTo(jx - U * 0.04f, sill - U * 0.03f, jx - U * 0.028f, sill - U * 0.006f)
    jug.lineTo(jx + U * 0.028f, sill - U * 0.006f); jug.quadTo(jx + U * 0.04f, sill - U * 0.03f, jx + U * 0.025f, sill - U * 0.065f); jug.close()
    fill(jug, rgb(0xc8b8a0))
    rect(jx + U * 0.005f, sill - U * 0.064f, U * 0.028f, U * 0.058f, rgba(0, 0.22f))
    rect(jx - U * 0.03f, sill - U * 0.04f, U * 0.06f, U * 0.006f, rgb(0x3a5a8a))

    // ---- The candles: a brass tray, three pillars, three flames.
    ellipse(groupX, sill - U * 0.008f, U * 0.13f, U * 0.014f, 0f, rgb(0x6a4a26))
    ellipse(groupX, sill - U * 0.012f, U * 0.12f, U * 0.01f, 0f, rgb(0xb08848))
    rect(groupX - U * 0.08f, sill - U * 0.02f, U * 0.16f, 1f, rgba(0xffe0a0, 0.5f * f))
    val wax = p[3]
    for (i in intArrayOf(0, 2, 1)) {
        val pl = Pillars[i]
        val x = groupX + pl.dx * U
        val top = sill - U * 0.012f - pl.h * U
        val cw2 = pl.w * U
        val x0 = x - cw2 / 2
        wax.reset(); wax.moveTo(x0, sill - U * 0.014f); wax.lineTo(x0, top + U * 0.006f)
        for (k in 0..8) wax.lineTo(x0 + cw2 * k / 8, top + U * 0.003f * sin(k * 1.9f + i) + if (k in 3..5) U * 0.006f else 0f)
        wax.lineTo(x0 + cw2, sill - U * 0.014f); wax.close()
        fill(wax, hgrad(x0, x0 + cw2, 0f to rgb(0x8a6a52), 0.35f to rgb(0xdcbe9c), 0.7f to rgb(0xc4a482), 1f to rgb(0x6a4e3c)))
        glow(x, top + U * 0.02f, cw2 * 0.8f, 0xffa060, 0.28f * flick(pl.seed), sy = 0.7f)
        roundRect(x0 + cw2 * (0.2f + 0.4f * sHash(i.toFloat())), top + U * 0.004f, U * 0.008f, pl.h * U * (0.2f + 0.25f * sHash(i + 3f)), U * 0.004f, rgb(0xd8bc9a))
        ellipse(x, top + U * 0.007f, cw2 * 0.3f, U * 0.004f, 0f, rgb(0xfff0d4))
        val fh = U * 0.07f * (0.94f + 0.1f * sFlicker(t, pl.seed + 10)) * (1 - gust * 0.3f)
        val sway = (sNoise(t * 1.4f, pl.seed + 20) - 0.5f) * U * 0.012f + sin(t * 0.5f) * U * 0.004f + gust * U * 0.03f * sin(sinceBreath * 7 + i)
        val fy = top - U * 0.008f
        line(x, top + U * 0.006f, x + sway * 0.2f, fy, rgb(0x2a1a14), 1.4f)
        glow(x + sway * 0.4f, fy - fh * 0.45f, U * 0.16f, 0xffb060, 0.4f * flick(pl.seed) * (1 - gust * 0.3f))
        val flame = p[0]
        fill(flamePath(flame, x, fy + U * 0.004f, U * 0.03f, fh, sway), rgb(0xff9a3c))
        fill(flamePath(flame, x, fy + U * 0.002f, U * 0.021f, fh * 0.8f, sway * 0.8f), rgb(0xffd27a))
        fill(flamePath(flame, x, fy, U * 0.012f, fh * 0.52f, sway * 0.6f), rgb(0xfff6e0))
        ellipse(x, fy + U * 0.002f, U * 0.006f, U * 0.004f, 0f, rgba(0x5A82FF, 0.5f))
    }

    // ---- A moth, drawn to the light, looping round it.
    val ma = t * 1.1f
    val mxm = lightX + cos(ma) * U * 0.12f + sin(ma * 2.3f) * U * 0.03f
    val mym = lightY - U * 0.06f + sin(ma * 1.7f) * U * 0.06f
    val flap = abs(sin(t * 22))
    for (sd in floatArrayOf(-1f, 1f)) ellipse(mxm + sd * U * 0.006f, mym, U * 0.007f, U * 0.004f * (0.3f + flap), sd * 0.4f, rgba(0xE6D2B4, 0.85f))
    rect(mxm - 0.8f, mym - U * 0.004f, 1.6f, U * 0.008f, rgb(0x5a4636))

    grain(0.045f)
}
