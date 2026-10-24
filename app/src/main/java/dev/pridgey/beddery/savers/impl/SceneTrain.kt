package dev.pridgey.beddery.savers.impl

import android.graphics.Path
import android.graphics.RectF
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
import dev.pridgey.beddery.savers.sNoise
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TAU = (PI * 2).toFloat()

/**
 * The view from a sleeper compartment: a moonlit lake under mountains, a viaduct, villages,
 * forest and telegraph poles sliding past at their own speeds. Tap and the carriage rattles
 * over a set of points, the lamp flickering.
 */
@Composable
fun SceneTrainSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val paths = remember { Array(6) { Path() } }
    val rattle = remember { floatArrayOf(-100f) }
    val cooldown = remember { Cooldown(20_000) }
    OnSaverTap { if (cooldown.tryAcquire()) rattle[0] = time.value }
    SketchScene(time) { t -> drawTrain(t, paths, t - rattle[0]) }
}

private val TrainClouds = arrayOf(floatArrayOf(0.12f, 0.5f, 0.6f, 0.55f), floatArrayOf(0.24f, 0.38f, 1.0f, 0.45f), floatArrayOf(0.31f, 0.6f, 0.4f, 0.4f))
private val TrainLobes = arrayOf(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(0.15f, -0.6f, 0.5f, 1f), floatArrayOf(0.45f, -0.3f, 0.35f, 0.9f))
private val arcRect = RectF()

private fun Sketch.drawTrain(t: Float, p: Array<Path>, sinceRattle: Float) {
    val wx: Float; val wy: Float; val ww: Float; val wh: Float
    if (land) { wx = W * 0.08f; wy = H * 0.06f; ww = W * 0.84f; wh = H * 0.52f } else { wx = W * 0.07f; wy = H * 0.09f; ww = W * 0.86f; wh = H * 0.5f }
    val r = U * 0.05f
    val table = wy + wh + U * 0.055f
    // Rattling over points: a quick shudder that dies away, and a flickering lamp.
    val rattle = if (sinceRattle in 0f..2.5f) 1f - sinceRattle / 2.5f else 0f
    val shake = sin(sinceRattle * 40f) * rattle * U * 0.006f
    val flicker = if (rattle > 0f) 1f - 0.35f * rattle * (0.5f + 0.5f * sin(sinceRattle * 31f)) else 1f
    val sway = sin(t * 1.3f) * 1.2f + sNoise(t * 0.6f, 3f) * 1.5f + shake
    val speed = U * 0.5f
    val lampX = if (land) wx + ww * 0.86f else wx + ww * 0.8f

    // ---- Outside, clipped to the window.
    val glass = p[0]; glass.reset()
    arcRect.set(wx, wy, wx + ww, wy + wh)
    glass.addRoundRect(arcRect, r, r, Path.Direction.CW)
    c.save()
    c.clipPath(glass)
    c.translate(0f, sway)
    val hz = wy + wh * if (land) 0.5f else 0.42f
    val lakeBot = hz + wh * if (land) 0.1f else 0.13f
    rect(0f, wy - 10, W, hz - wy + 12, vgrad(wy, hz, 0f to rgb(0x0b0f2a), 0.55f to rgb(0x1d2450), 0.85f to rgb(0x3a3768), 1f to rgb(0x6a4d78)))
    stars(wy, wy + wh * 0.4f, 70, 31, t, 0xEEF0FF)
    val mx = wx + ww * 0.74f
    val my = wy + wh * 0.2f
    moon(mx, my, U * 0.04f, 0xF4EEE2, 0.16f)
    // Long flat clouds, lit along their undersides, barely drifting.
    for ((cyF, cwF, s, a) in TrainClouds) {
        val cy = wy + wh * cyF
        val cw = ww * cwF
        val ch = U * 0.032f
        val cx = ((t * s + cyF * 2000) % (W + cw * 2)) - cw
        for ((ox, oy, sw, sh) in TrainLobes) {
            val x0 = cx + cw * ox
            val y0 = cy + ch * oy
            val h0 = ch * sh
            roundRect(x0, y0, cw * sw, h0, h0 / 2, vgrad(y0, y0 + h0, 0f to rgba(0x191c40, a), 0.7f to rgba(0x2e2c5a, a), 1f to rgba(0xa49ac4, a * 0.7f)))
        }
    }

    // Far mountains: barely move. Moonlit slopes: fill the range in its lit colour, then lay the
    // shadow colour over a copy nudged away from the moon, leaving a lit band on every slope that faces it.
    val farShift = t * speed * 0.012f
    val farPts = ridge(hz, wh * 0.24f, 4f, 1.6f, rgb(0x3e3568), hz + 2, farShift, true, rgba(0xDCD2FF, 0.35f))
    val nFar = farPts.size / 2
    val farPath = p[1]; farPath.reset()
    farPath.moveTo(-4f, hz + 2)
    var yTop = Float.MAX_VALUE
    for (i in 0 until nFar) { farPath.lineTo(farPts[i * 2], farPts[i * 2 + 1]); yTop = minOf(yTop, farPts[i * 2 + 1]) }
    farPath.lineTo(farPts[(nFar - 1) * 2], hz + 2); farPath.close()
    c.save(); c.clipPath(farPath)
    rect(0f, yTop - 2, W, hz - yTop + 4, vgrad(yTop, hz, 0f to rgb(0x7a6aa8), 1f to rgb(0x4a3f72)))
    val away = if (mx > wx + ww / 2) -1f else 1f
    c.translate(away * ww * 0.035f, wh * 0.012f)
    fill(farPath, rgb(0x3e3568))
    c.restore()
    fun farY(x: Float) = farPts[((x + 4) / 3).roundToInt().coerceIn(0, nFar - 1) * 2 + 1]
    // Wind turbines along the far ridge, blades turning, red lights blinking.
    val tg = U * 0.5f
    run {
        val i0 = floor(farShift / tg).toInt()
        for (i in i0 - 1 until i0 + ceil(W / tg).toInt() + 2) {
            if (sHash(i * 4.1f) < 0.45f) continue
            val x = i * tg - farShift + sHash(i.toFloat()) * tg * 0.5f
            if (x < wx - 20 || x > wx + ww + 20) continue
            val base = farY(x) + 2
            val th = wh * 0.06f
            line(x, base, x, base - th, rgb(0x6a5e9a), 1.2f)
            val rot = t * 0.9f + i
            for (b in 0 until 3) {
                val a = rot + b * TAU / 3
                line(x, base - th, x + cos(a) * th * 0.5f, base - th + sin(a) * th * 0.5f, rgb(0x6a5e9a), 1f)
            }
            if ((t + i * 0.37f) % 2f < 0.6f) { rect(x - 1, base - th - 2, 2f, 2f, rgb(0xff3a3a)); glow(x, base - th - 1, 6f, 0xff3a3a, 0.5f) }
        }
    }
    // A ruined castle on a far summit, one window still lit.
    val cg = U * 3.2f
    run {
        val i0 = floor(farShift / cg).toInt()
        for (i in i0 - 1 until i0 + 2) {
            val x = i * cg - farShift + cg * 0.4f
            if (x < wx - 60 || x > wx + ww + 60) continue
            val by = farY(x) + 3
            val s2 = wh * 0.05f
            val col = rgb(0x3a3266)
            rect(x - s2 * 1.4f, by - s2 * 0.9f, s2 * 2.8f, s2 * 0.9f, col)
            rect(x - s2 * 0.4f, by - s2 * 1.9f, s2 * 0.8f, s2, col)
            rect(x + s2 * 0.9f, by - s2 * 1.4f, s2 * 0.5f, s2 * 0.6f, col)
            for (m in 0 until 4) rect(x - s2 * 0.4f + m * s2 * 0.22f, by - s2 * 2.05f, s2 * 0.12f, s2 * 0.16f, col)
            rect(x - s2 * 0.08f, by - s2 * 1.55f, s2 * 0.14f, s2 * 0.22f, rgb(0xffc87a))
            glow(x, by - s2 * 1.45f, s2 * 0.8f, 0xffb060, 0.25f)
        }
    }
    mist(hz - wh * 0.01f, wh * 0.07f, 0x7a5a88, 0.5f)

    // ---- The lake: the sky and mountains mirrored, broken by faint ripples. It runs on down
    // behind the near hills, so no gap shows between them.
    val lakeFloor = lakeBot + wh * 0.14f
    c.save()
    c.clipRect(0f, hz, W, lakeFloor)
    rect(0f, hz, W, lakeFloor - hz, vgrad(hz, lakeFloor, 0f to rgb(0x5a4a7c), 0.5f to rgb(0x2a2450), 1f to rgb(0x1e1a3e)))
    val mirror = p[2]; mirror.reset()
    mirror.moveTo(-4f, hz)
    for (i in 0 until nFar) mirror.lineTo(farPts[i * 2], hz + (hz - farPts[i * 2 + 1]) * 0.7f)
    mirror.lineTo(farPts[(nFar - 1) * 2], hz); mirror.close()
    fill(mirror, rgba(0x282250, 0.75f))
    for (k in 0 until 7) {
        val y = hz + (lakeBot - hz) * (k + 0.5f) / 7
        val len = W * (0.2f + 0.3f * sHash(k.toFloat()))
        val x = ((sHash(k * 7f) * W + t * 2) % (W + len)) - len
        rect(x, y, len, 1f, rgba(0x968CD2, 0.1f))
    }
    // The moon's path across the water.
    for (k in 0 until 12) {
        val y = hz + 2 + k * (lakeBot - hz) / 12
        val half = U * (0.012f + 0.022f * sHash(k * 3f)) * (0.6f + 0.4f * sin(t * 1.5f + k * 1.3f))
        rect(mx - half + sin(t + k) * 2, y, half * 2, 1.2f, rgba(0xF5F0E1, 0.55f - k * 0.035f))
    }
    // Lights of a far-shore village, each with a long wavering reflection.
    val lg = U * 0.7f
    run {
        val i0 = floor(farShift / lg).toInt()
        for (i in i0 - 1 until i0 + ceil(W / lg).toInt() + 2) {
            if (sHash(i * 9.1f) < 0.35f) continue
            val n = 4 + floor(sHash(i * 3f) * 5).toInt()
            for (k in 0 until n) {
                val x = i * lg - farShift + sHash(i * 11f + k) * U * 0.3f
                rect(x, hz - 2 - sHash(i.toFloat() + k) * 3, 1.6f, 1.6f, rgb(0xffc87a))
                val len = (lakeBot - hz) * (0.35f + 0.25f * sHash(i + k * 3f))
                rect(x + sin(t * 1.6f + k + i) * 0.8f - 0.4f, hz + 1, 2f, len, vgrad(hz + 1, hz + 1 + len, 0f to rgba(0xFFBE6E, 0.35f), 1f to rgba(0xFFBE6E, 0f)))
            }
        }
    }
    // A long stone viaduct striding across the lake, mirrored in it.
    val vShift = t * speed * 0.035f
    val vg = U * 5
    val water = hz + (lakeBot - hz) * 0.6f
    run {
        val i0 = floor(vShift / vg).toInt()
        for (i in i0 - 1 until i0 + 2) {
            val x = i * vg - vShift + vg * 0.2f
            val span = U * 1.6f
            if (x + span < wx - 10 || x > wx + ww + 10) continue
            val deck = water - wh * 0.07f
            val aw = span / 9
            val v = p[3]; v.reset()
            v.addRect(x, deck, x + span, deck + wh * 0.012f, Path.Direction.CW)
            for (a in 0 until 9) {
                v.moveTo(x + a * aw, deck); v.lineTo(x + (a + 1) * aw, deck); v.lineTo(x + (a + 1) * aw, water)
                v.lineTo(x + (a + 0.86f) * aw, water)
                val acx = x + (a + 0.5f) * aw
                val acy = deck + wh * 0.03f
                val ar = aw * 0.36f
                arcRect.set(acx - ar, acy - ar, acx + ar, acy + ar)
                v.arcTo(arcRect, 0f, -180f, false)
                v.lineTo(x + (a + 0.14f) * aw, water); v.lineTo(x + a * aw, water); v.close()
            }
            // Its reflection, squashed and faint.
            c.saveLayerAlpha(x - 2, hz, x + span + 2, lakeFloor, (0.35f * 255).toInt())
            c.translate(0f, water); c.scale(1f, -0.8f); c.translate(0f, -water)
            fill(v, rgb(0x151230))
            c.restore()
            fill(v, rgb(0x151230))
            rect(x, deck, span, 1f, rgba(0xC8BEFF, 0.25f))
        }
    }
    c.restore()
    // Low mist drifting on the water, wrapping only once the whole soft patch is off-screen.
    for (k in 0 until 5) {
        val x = ((sHash(k * 5f) * W + t * U * 0.01f) % (W + U)) - U * 0.5f
        glow(x, lakeBot - (lakeBot - hz) * 0.25f, U * 0.5f, 0x8a78b0, 0.12f, additive = false, sy = 0.12f)
    }

    // Rolling hills with hedgerows and villages.
    val midShift = t * speed * 0.06f
    val hills = ridge(lakeBot + wh * if (land) 0.08f else 0.1f, wh * if (land) 0.1f else 0.12f, 12f, 2.4f, rgb(0x2b2650), hz + wh, midShift, false, rgba(0xC8BEFF, 0.18f))
    val nHill = hills.size / 2
    fun hillY(x: Float) = hills[((x + 4) / 3).roundToInt().coerceIn(0, nHill - 1) * 2 + 1]
    val hillPath = p[1]; hillPath.reset()
    hillPath.moveTo(-4f, hz + wh)
    for (i in 0 until nHill) hillPath.lineTo(hills[i * 2], hills[i * 2 + 1])
    hillPath.lineTo(hills[(nHill - 1) * 2], hz + wh); hillPath.close()
    c.save(); c.clipPath(hillPath)
    rect(0f, lakeBot, W, wh * 0.22f, vgrad(lakeBot, lakeBot + wh * 0.22f, 0f to rgba(0x8C80C8, 0.14f), 1f to rgba(0, 0f)))
    val hedge = p[2]
    for (row in 0 until 3) {
        val dy = wh * (0.03f + row * 0.045f)
        hedge.reset()
        var x = -4f
        while (x <= W + 8) {
            val y = hillY(x) + dy + (sNoise((x + midShift) * 0.02f, row + 3f) - 0.5f) * wh * 0.03f
            if (x < 0) hedge.moveTo(x, y) else hedge.lineTo(x, y)
            x += 6
        }
        stroke(hedge, rgba(0x120E28, 0.85f), 1.6f)
        // Trees along the hedge at irregular spacing, anchored to the world so they scroll with the hills.
        val tgp = U * 0.05f
        val i0 = floor(midShift / tgp).toInt()
        for (i in i0 - 1 until i0 + ceil(W / tgp).toInt() + 2) {
            if (sHash(i * 5f + row * 17) < 0.72f) continue
            val tx = i * tgp - midShift
            val ty = hillY(tx) + dy + (sNoise((tx + midShift) * 0.02f, row + 3f) - 0.5f) * wh * 0.03f
            val tr = wh * (0.01f + 0.008f * sHash(i.toFloat() + row))
            circle(tx, ty - tr * 0.8f, tr, rgb(0x16122e))
            circle(tx + tr * 0.3f, ty - tr * 1.1f, tr * 0.5f, rgba(0x968CD2, 0.08f))
        }
    }
    c.restore()
    // Village lights sit on the hills, scrolling with them.
    val villageGap = U * 0.9f
    val first = floor((midShift - U) / villageGap).toInt()
    val house = p[3]
    for (v in first until first + ceil(W / villageGap).toInt() + 3) {
        if (sHash(v * 3.3f) < 0.4f) continue
        val vx = v * villageGap - midShift
        val count = 3 + floor(sHash(v * 7.7f) * 6).toInt()
        for (k in 0 until count) {
            val x = vx + (sHash(v * 11f + k) - 0.5f) * U * 0.25f
            val y = hillY(x) + U * (0.01f + 0.02f * sHash(v * 13f + k))
            val hw = U * 0.016f
            house.reset()
            house.moveTo(x - hw, y); house.lineTo(x - hw, y - hw * 0.8f); house.lineTo(x, y - hw * 1.4f); house.lineTo(x + hw, y - hw * 0.8f); house.lineTo(x + hw, y); house.close()
            fill(house, rgb(0x1c1838))
            if (sHash(v * 17f + k) > 0.3f) {
                rect(x - hw * 0.35f, y - hw * 0.6f, hw * 0.4f, hw * 0.35f, rgb(0xffc87a))
                glow(x, y - hw * 0.45f, U * 0.025f, 0xffb060, 0.25f)
            }
        }
    }
    // The valley floor below the hills.
    val riverY = lakeBot + wh * if (land) 0.11f else 0.15f
    rect(0f, riverY - wh * 0.02f, W, wh, vgrad(riverY - wh * 0.02f, wy + wh, 0f to rgb(0x1d1a3a), 1f to rgb(0x100e22)))

    // A road along the valley with the odd car, and a farmhouse with a lit window.
    val rShift = t * speed * 0.2f
    val roadY = riverY + wh * 0.03f
    rect(0f, roadY, W, wh * 0.012f, rgb(0x24203f))
    val fhg = U * 2.6f
    run {
        val i0 = floor(rShift / fhg).toInt()
        for (i in i0 - 1 until i0 + 2) {
            val x = i * fhg - rShift + fhg * 0.6f
            if (x < wx - 80 || x > wx + ww + 80) continue
            val s2 = wh * if (land) 0.07f else 0.06f
            val gy = roadY - 1
            val col = rgb(0x1b1735)
            rect(x, gy - s2, s2 * 1.4f, s2, col)
            val roof = p[4]; roof.reset()
            roof.moveTo(x - s2 * 0.1f, gy - s2); roof.lineTo(x + s2 * 0.7f, gy - s2 * 1.6f); roof.lineTo(x + s2 * 1.5f, gy - s2); roof.close()
            fill(roof, col)
            rect(x + s2 * 1.6f, gy - s2 * 0.8f, s2 * 1.1f, s2 * 0.8f, col)
            roof.reset(); roof.moveTo(x + s2 * 1.55f, gy - s2 * 0.8f); roof.quadTo(x + s2 * 2.15f, gy - s2 * 1.3f, x + s2 * 2.75f, gy - s2 * 0.8f); roof.close()
            fill(roof, col)
            rect(x + s2 * 0.25f, gy - s2 * 0.65f, s2 * 0.22f, s2 * 0.25f, rgb(0xffc87a))
            glow(x + s2 * 0.36f, gy - s2 * 0.5f, s2 * 1.2f, 0xffb060, 0.3f)
            rect(x + s2 * 1.0f, gy - s2 * 1.75f, s2 * 0.15f, s2 * 0.35f, col)
        }
    }
    val cp = (t % 17f) / 17f
    if (cp < 0.45f) {
        // Coming the other way, so it slides past quickly.
        val cx = wx + ww * (1.1f - cp / 0.45f * 1.2f)
        glow(cx, roadY + 2, U * 0.06f, 0xfff0c8, 0.35f)
        rect(cx - 2, roadY + 1, 2f, 2f, rgb(0xfff4dc)); rect(cx + 3, roadY + 1, 2f, 2f, rgb(0xfff4dc))
        val beam = p[4]; beam.reset()
        beam.moveTo(cx - 2, roadY + 2); beam.lineTo(cx - U * 0.2f, roadY - 2); beam.lineTo(cx - U * 0.2f, roadY + 8); beam.close()
        fill(beam, rgba(0xFFF0C8, 0.12f))
    }

    // Near forest: rushes by.
    val nearShift = t * speed * 0.45f
    val forestBase = wy + wh * 0.97f
    val gap = U * 0.045f
    val f0 = floor(nearShift / gap).toInt() - 2
    for (i in f0 until f0 + ceil(W / gap).toInt() + 5) {
        val x = i * gap - nearShift + (sHash(i * 1.3f) - 0.5f) * gap
        val clump = sNoise(i * 0.08f, 5f)
        if (clump < 0.35f) continue // clearings between stands of trees
        val h = wh * (0.06f + 0.15f * sHash(i * 2.7f)) * (0.5f + clump)
        pine(x, forestBase, h, rgb(0x0d0b1c), i.toFloat())
    }
    rect(0f, forestBase - 2, W, wh, rgb(0x0d0b1c))
    // Light from the train's own windows falls on the trackside as we pass.
    rect(0f, wy + wh * 0.88f, W, wh * 0.12f, vgrad(wy + wh * 0.88f, wy + wh, 0f to rgba(0xFFAA64, 0f), 1f to rgba(0xFFAA64, 0.16f * flicker)))

    // Telegraph poles and sagging wires, fastest of all.
    val poleShift = t * speed * 1.1f
    val pg = U * 1.1f
    val p0 = floor(poleShift / pg).toInt() - 1
    val wireY = wy + wh * 0.3f
    var prev = Float.NaN
    val wire = p[4]
    for (i in p0 until p0 + ceil(W / pg).toInt() + 3) {
        val x = i * pg - poleShift
        rect(x - U * 0.006f, wireY, U * 0.012f, wh, rgb(0x07060f))
        rect(x - U * 0.04f, wireY + U * 0.012f, U * 0.08f, U * 0.006f, rgb(0x07060f))
        if (!prev.isNaN()) {
            for (off in floatArrayOf(-0.03f, 0.03f)) {
                wire.reset()
                wire.moveTo(prev + U * off, wireY + U * 0.015f)
                wire.quadTo((prev + x) / 2 + U * off, wireY + U * 0.075f, x + U * off, wireY + U * 0.015f)
                stroke(wire, rgb(0x07060f), 1f)
            }
        }
        prev = x
    }
    c.restore()

    // The lamp reflected in the glass, and a faint sheen.
    c.save(); c.clipPath(glass)
    glow(lampX, wy + wh * 0.82f, U * 0.16f, 0xffb070, 0.14f * flicker)
    val sheen = p[4]; sheen.reset()
    sheen.moveTo(wx + ww * 0.05f, wy + wh); sheen.lineTo(wx + ww * 0.3f, wy); sheen.lineTo(wx + ww * 0.4f, wy); sheen.lineTo(wx + ww * 0.15f, wy + wh); sheen.close()
    fill(sheen, rgba(0xC8BEFF, 0.035f))
    c.restore()

    // ---- The compartment: panelled walls, warm lamplight.
    val wall = p[1]; wall.reset()
    wall.fillType = Path.FillType.EVEN_ODD
    wall.addRect(0f, 0f, W, H, Path.Direction.CW)
    arcRect.set(wx - U * 0.02f, wy - U * 0.02f, wx + ww + U * 0.02f, wy + wh + U * 0.02f)
    wall.addRoundRect(arcRect, r * 1.3f, r * 1.3f, Path.Direction.CW)
    fill(wall, rgb(0x140b07))
    c.save(); c.clipPath(wall)
    val pw = if (land) W / 9 else W / 4
    var px = 0f
    while (px < W) {
        rect(px + U * 0.012f, 0f, pw - U * 0.024f, H, rgb(0x190f09))
        rect(px + U * 0.012f, 0f, 1f, H, rgba(0xFFBE82, 0.045f))
        px += pw
    }
    glow(lampX, table - U * 0.1f, U * 0.9f, 0xc06a30, 0.2f * flicker)
    c.restore()
    wall.fillType = Path.FillType.WINDING
    // Window frame: polished wood with a brass inner trim.
    val frame = p[2]; frame.reset()
    arcRect.set(wx - U * 0.008f, wy - U * 0.008f, wx + ww + U * 0.008f, wy + wh + U * 0.008f)
    frame.addRoundRect(arcRect, r * 1.15f, r * 1.15f, Path.Direction.CW)
    stroke(frame, rgb(0x3a2416), U * 0.025f)
    stroke(glass, rgba(0xDCAA64, 0.55f), 1.2f)
    // A blind rolled up at the top.
    roundRect(wx + U * 0.02f, wy - U * 0.004f, ww - U * 0.04f, U * 0.03f, U * 0.012f, rgb(0x4a2c22))
    rect(wx + U * 0.03f, wy + U * 0.018f, ww - U * 0.06f, 1f, rgba(0xFFC896, 0.18f))
    rect(wx + ww / 2 - U * 0.015f, wy + U * 0.026f, U * 0.03f, U * 0.008f, rgb(0xc89a5a))

    // Curtains tied back on each side.
    val curtain = p[3]
    for (side in intArrayOf(-1, 1)) {
        val cx = if (side < 0) wx - U * 0.01f else wx + ww + U * 0.01f
        val cwid = U * 0.11f
        val top = wy - U * 0.03f
        val bot = table - U * 0.01f
        val pinch = (bot - top) * 0.55f
        for (k in 0 until 4) {
            val xa = cx + side * (k / 4f) * cwid * -0.9f
            val xb = cx + side * ((k + 1) / 4f) * cwid * -0.9f
            curtain.reset()
            curtain.moveTo(xa, top); curtain.lineTo(xb, top)
            curtain.quadTo(cx + side * cwid * 0.1f, top + pinch, xb, bot)
            curtain.lineTo(xa, bot)
            curtain.quadTo(cx + side * cwid * 0.15f, top + pinch, xa, top)
            curtain.close()
            fill(curtain, rgb(if (k % 2 == 1) 0x5a1e2a else 0x4a1822))
        }
        circle(cx + side * cwid * 0.05f, wy + wh * 0.55f, U * 0.008f, rgb(0xc89a5a))
    }

    // ---- The table and lamp.
    rect(0f, table, W, U * 0.03f, rgb(0x321d12))
    rect(0f, table, W, 1f, rgba(0xFFC88C, 0.35f))
    rect(0f, table + U * 0.03f, W, U * 0.06f, vgrad(table + U * 0.03f, table + U * 0.09f, 0f to rgba(0, 0.5f), 1f to rgba(0, 0f)))
    glow(lampX, table, U * 0.4f, 0xffb070, 0.35f * flicker, sy = 0.15f)
    // Lamp: brass stem, pleated shade, lit from within.
    val lh = U * 0.13f
    rect(lampX - 1.5f, table - lh * 0.7f, 3f, lh * 0.7f, rgb(0xb08040))
    ellipse(lampX, table - 1, U * 0.028f, U * 0.006f, 0f, rgb(0x8a6030))
    val shade = p[4]; shade.reset()
    shade.moveTo(lampX - lh * 0.2f, table - lh); shade.lineTo(lampX + lh * 0.2f, table - lh); shade.lineTo(lampX + lh * 0.36f, table - lh * 0.62f); shade.lineTo(lampX - lh * 0.36f, table - lh * 0.62f); shade.close()
    fill(shade, vgrad(table - lh, table - lh * 0.62f, 0f to rgb(0xffd9a0), 1f to rgb(0xf09a50)))
    for (k in -2..2) line(lampX + k * lh * 0.07f, table - lh, lampX + k * lh * 0.13f, table - lh * 0.62f, rgba(0xA0501E, 0.35f), 1f)
    glow(lampX, table - lh * 0.6f, U * 0.2f, 0xffcf90, 0.45f * flicker)
    grain(0.05f)
}
