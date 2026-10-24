package dev.pridgey.beddery.savers.impl

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
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = (PI * 2).toFloat()
private const val DEG = (180 / PI).toFloat()

private enum class Variety { KOHAKU, SHOWA, PLATINUM, TANCHO, CHAGOI }

private data class Koi(val pat: Variety, val len: Float, val speed: Float, val ph: Float, val r: Float, val depth: Float, val butterfly: Boolean)

// Sorted shallowest last, so nearer fish draw over deeper ones.
private val School = listOf(
    Koi(Variety.KOHAKU, 0.24f, 0.07f, 0f, 1f, 1f, false),
    Koi(Variety.SHOWA, 0.21f, 0.07f, 2.3f, 0.8f, 0.85f, false),
    Koi(Variety.PLATINUM, 0.2f, 0.07f, 4.2f, 1.15f, 0.95f, true),
    Koi(Variety.TANCHO, 0.18f, 0.07f, 1.2f, 0.55f, 0.7f, false),
    Koi(Variety.CHAGOI, 0.22f, 0.07f, 5.4f, 0.95f, 0.55f, false),
).sortedBy { it.depth }

/** Reusable geometry for the fish, so a frame allocates nothing. */
private class KoiPaths {
    val body = Path()
    val tail = Path()
    val fin = Path()
    val scratch = Path()
    val sp = FloatArray(46)
    val rect = RectF()
    val leaf = Path()
}

/** Looking down into still, moonlit water: five koi circling, a lotus, drifting maple leaves. Tap to send out a ripple. */
@Composable
fun KoiPondSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val paths = remember { KoiPaths() }
    val tap = remember { floatArrayOf(-100f, 0f, 0f) }
    val cooldown = remember { Cooldown(3_000) }
    // The scene's size in pixels, to turn a tap into a spot on the water.
    val px = remember { floatArrayOf(1f, 1f) }
    OnSaverTap { at -> if (cooldown.tryAcquire()) { tap[0] = time.value; tap[1] = at.x / px[0]; tap[2] = at.y / px[1] } }
    SketchScene(time) { t ->
        px[0] = W * density; px[1] = H * density
        drawKoiPond(t, paths, tap)
    }
}

private fun Sketch.drawKoiPond(t: Float, k: KoiPaths, tap: FloatArray) {
    val mlx = W * 0.78f
    val mly = H * if (land) 0.28f else 0.2f
    val shx = U * 0.028f // shadows fall down and to the right
    val shy = U * 0.04f

    // ---- Water: deep indigo, a little lighter where the moon shines in.
    rect(0f, 0f, W, H, vgrad(0f, H, 0f to rgb(0x0d2238), 0.5f to rgb(0x0a1c30), 1f to rgb(0x061220)))
    glow(mlx, mly, U * 0.9f, 0x3a6a8a, 0.35f, additive = false)
    // The pond floor: smooth river stones, barely seen.
    val fr = Rng(21)
    for (i in 0 until 120) {
        val x = fr.next() * W
        val y = fr.next() * H
        val r = U * (0.012f + 0.03f * fr.next())
        val col = if (fr.next() > 0.5f) 0x28465A else 0x040C16
        val a = 0.07f + 0.08f * fr.next()
        val ry = r * (0.6f + 0.3f * fr.next())
        ellipse(x, y, r, ry, fr.next() * 3, rgba(col, a))
    }
    // Light rippling over the floor.
    for (i in 0 until 40) {
        val x = sHash(i * 3f) * W + sin(t * 0.3f + i) * U * 0.02f
        val y = sHash(i * 7f) * H + cos(t * 0.25f + i) * U * 0.02f
        arcStroke(x, y, U * 0.05f, U * 0.025f, 0.3f, 2.6f, rgba(0x6EAAC8, 0.035f * (0.6f + 0.4f * sin(t * 0.8f + i * 2))), 1.2f, sHash(i.toFloat()) * 3)
    }

    // ---- Koi: shadows on the floor first, then the fish.
    val cx = W * 0.5f
    val cy = H * if (land) 0.46f else 0.4f
    val rx = if (land) W * 0.3f else W * 0.32f
    val ry = if (land) H * 0.28f else H * 0.2f
    fun pose(f: Koi, tt: Float, out: FloatArray) {
        // They circle the pond together, each wandering a little in and out.
        val a = tt * f.speed + f.ph
        val wob = 1 + 0.12f * sin(tt * 0.13f + f.ph * 3)
        out[0] = cx + cos(a) * rx * f.r * wob
        out[1] = cy + sin(a) * ry * f.r * wob
    }
    val p0 = FloatArray(2)
    val p1 = FloatArray(2)
    for (shadow in listOf(true, false)) for (f in School) {
        pose(f, t, p0); pose(f, t + 0.3f, p1)
        drawKoi(f, p0[0], p0[1], atan2(p1[1] - p0[1], p1[0] - p0[0]), t, shadow, shx, shy, k)
    }

    // ---- Calligraphic ripple lines drifting across the surface.
    for (i in 0 until 7) {
        val y0 = H * (0.12f + i * 0.13f) + sin(t * 0.07f + i) * H * 0.02f
        val len = W * (0.25f + 0.3f * sHash(i * 3f))
        val x0 = ((sHash(i * 5f) * W * 1.4f + t * U * (0.008f + 0.006f * sHash(i.toFloat()))) % (W + len * 1.2f)) - len * 1.1f
        val thick = U * (0.003f + 0.004f * sHash(i * 7f))
        val p = k.scratch
        p.reset()
        p.moveTo(x0, y0)
        p.cubicTo(x0 + len * 0.3f, y0 - U * 0.03f, x0 + len * 0.6f, y0 + U * 0.03f, x0 + len, y0)
        p.cubicTo(x0 + len * 0.6f, y0 + U * 0.03f + thick, x0 + len * 0.3f, y0 - U * 0.03f + thick * 1.6f, x0, y0)
        p.close()
        fill(p, rgba(0xAAD2E6, 0.08f + 0.05f * sHash(i.toFloat())))
    }

    // A ripple spreading from a tap.
    val since = t - tap[0]
    if (since in 0f..4f) {
        val rr = since * U * 0.06f
        val a = 0.35f * (1 - since / 4) * min(1f, since / 0.3f)
        arcStroke(tap[1] * W, tap[2] * H, rr, rr, 0f, TAU, rgba(0xBEE1F0, a), 1.2f)
        arcStroke(tap[1] * W, tap[2] * H, rr * 0.65f, rr * 0.65f, 0f, TAU, rgba(0xBEE1F0, a * 0.7f), 1f)
    }

    // ---- The moon's reflection: a soft pool of light with a few trembling glints.
    glow(mlx, mly, U * 0.16f, 0xe8f2ff, 0.16f)
    glow(mlx, mly, U * 0.05f, 0xf4f8ff, 0.35f)
    for (i in 0 until 12) {
        val a = sHash(i * 3f) * TAU
        val d = U * 0.05f * sqrt(sHash(i * 5f))
        val tw = max(0f, sin(t * (1.2f + sHash(i.toFloat())) + i * 2))
        ellipse(mlx + cos(a) * d * 1.4f, mly + sin(a) * d * 0.8f, U * 0.008f, U * 0.0018f, 0.1f, rgba(0xF5FAFF, 0.5f * tw))
    }

    // ---- Lily pads with a white lotus, and a second smaller group.
    fun pad(x: Float, y: Float, r: Float, rot: Float, notch: Float = 0.35f) {
        val p = k.scratch
        fun wedge(px: Float, py: Float) {
            p.reset(); p.moveTo(px, py)
            k.rect.set(px - r, py - r, px + r, py + r)
            p.arcTo(k.rect, (rot + notch / 2) * DEG, (TAU - notch) * DEG)
            p.close()
        }
        wedge(x + shx * 0.6f, y + shy * 0.6f); fill(p, rgba(0x00060E, 0.45f))
        wedge(x, y); fill(p, rgrad(x - r * 0.3f, y - r * 0.3f, r * 1.3f, 0f to rgb(0x4a8a54), 1f to rgb(0x24502e)))
        for (v in 0 until 9) {
            val a = rot + notch / 2 + 0.2f + v * (TAU - notch - 0.4f) / 8
            line(x, y, x + cos(a) * r * 0.9f, y + sin(a) * r * 0.9f, rgba(0x14321A, 0.55f), 1f)
        }
        arcStroke(x, y, r - 1, r - 1, rot + PI.toFloat() * 1.05f, rot + PI.toFloat() * 1.6f, rgba(0xAADCAA, 0.35f), 1.2f)
    }
    fun lotus(x: Float, y: Float, s: Float) {
        glow(x, y, s * 2.4f, 0xffe8f0, 0.14f)
        val counts = intArrayOf(10, 8, 6)
        val lens = floatArrayOf(1f, 0.78f, 0.55f)
        val wids = floatArrayOf(0.34f, 0.3f, 0.26f)
        val p = k.scratch
        for (ring in 0 until 3) {
            val n = counts[ring]
            val len = s * lens[ring]
            val wid = s * wids[ring]
            val shader = lgrad(0f, 0f, 0f, -len, 0f to rgb(if (ring == 0) 0xe8b8c8 else 0xf4d8e2), 1f to rgb(0xfff8fa))
            for (i in 0 until n) {
                val a = i.toFloat() / n * TAU + ring * 0.33f + sin(t * 0.2f) * 0.01f
                c.save(); c.translate(x, y); c.rotate(a * DEG)
                p.reset(); p.moveTo(0f, 0f); p.quadTo(wid, -len * 0.5f, 0f, -len); p.quadTo(-wid, -len * 0.5f, 0f, 0f); p.close()
                fill(p, shader)
                line(0f, -len * 0.15f, 0f, -len * 0.85f, rgba(0xC88CA0, 0.35f), 0.8f)
                c.restore()
            }
        }
        circle(x, y, s * 0.2f, rgb(0xf2c84a))
        for (i in 0 until 8) {
            val a = i / 8f * TAU
            circle(x + cos(a) * s * 0.1f, y + sin(a) * s * 0.1f, s * 0.025f, rgb(0xc89a2a))
        }
    }
    val g1x = W * if (land) 0.12f else 0.14f
    val g1y = H * if (land) 0.72f else 0.64f
    var dx = sin(t * 0.11f + 1) * U * 0.006f
    var dy = cos(t * 0.09f + 1) * U * 0.006f
    pad(g1x + dx, g1y + dy, U * 0.11f, 0.6f)
    pad(g1x + U * 0.13f + dx, g1y - U * 0.1f + dy, U * 0.075f, 2.4f)
    pad(g1x - U * 0.04f + dx, g1y - U * 0.16f + dy, U * 0.06f, 4.4f)
    lotus(g1x + U * 0.07f + dx, g1y + U * 0.035f + dy, U * 0.065f)
    val g2x = W * if (land) 0.9f else 0.88f
    val g2y = H * if (land) 0.2f else 0.46f
    dx = sin(t * 0.11f + 4) * U * 0.006f
    dy = cos(t * 0.09f + 4) * U * 0.006f
    pad(g2x + dx, g2y + dy, U * 0.085f, 3.4f)
    pad(g2x - U * 0.1f + dx, g2y + U * 0.06f + dy, U * 0.055f, 1.2f)
    // A closed bud standing beside the second group.
    ellipse(g2x + U * 0.03f + dx, g2y - U * 0.07f + dy, U * 0.018f, U * 0.03f, 0.4f, rgb(0xe8a0b8))
    ellipse(g2x + U * 0.026f + dx, g2y - U * 0.075f + dy, U * 0.009f, U * 0.02f, 0.4f, rgb(0xf8d0dc))

    // ---- Fallen maple leaves floating, turning slowly, with ripples where they land.
    val leafColors = intArrayOf(0xc8402a, 0xe06a2a, 0xa82a2a, 0xe8a03a)
    for (i in 0 until 7) {
        val life = 40f
        val shifted = t + i * life / 7
        val age = shifted % life
        val q = age / life
        val sx = sHash(i * 13f + floor(shifted / life)) * W
        val x = sx + sin(age * 0.1f + i) * U * 0.06f + q * U * 0.15f
        val y = H * (0.08f + 0.84f * sHash(i * 17f)) + q * U * 0.08f
        val s = U * (0.028f + 0.012f * sHash(i * 3f))
        val a = min(1f, age / 2) * min(1f, (life - age) / 3)
        val rot = sHash(i.toFloat()) * TAU + age * 0.05f
        if (age < 4) {
            val rr = age * U * 0.03f
            val ra = 0.3f * (1 - age / 4) * min(1f, age / 0.4f) * a
            arcStroke(x, y, rr, rr, 0f, TAU, rgba(0xBEE1F0, ra), 1f)
            arcStroke(x, y, rr * 0.6f, rr * 0.6f, 0f, TAU, rgba(0xBEE1F0, ra), 1f)
        }
        mapleLeaf(k.leaf, x + shx * 0.5f, y + shy * 0.5f, s, rot, rgba(0x00060E, 0.4f * a), false, a)
        mapleLeaf(k.leaf, x, y, s, rot, rgba(leafColors[i % 4], a), true, a)
    }

    // ---- An overhanging maple branch framing the top corner, its shadow on the water.
    val branch = if (land) floatArrayOf(-10f, H * 0.08f, W * 0.1f, H * 0.12f, W * 0.2f, H * 0.1f, W * 0.27f, H * 0.16f)
    else floatArrayOf(-10f, H * 0.04f, W * 0.2f, H * 0.07f, W * 0.38f, H * 0.06f, W * 0.5f, H * 0.1f)
    val branchLeaves = intArrayOf(0x8a1e1e, 0xb0302a, 0xc84a2a, 0x6a1416)
    for (shadow in listOf(true, false)) {
        val ox = if (shadow) shx * 2.2f else 0f
        val oy = if (shadow) shy * 2.2f else 0f
        val col = if (shadow) rgba(0x00060E, 0.35f) else rgb(0x1a0e0c)
        val p = k.scratch
        p.reset()
        for (j in 0 until 4) if (j == 0) p.moveTo(branch[0] + ox, branch[1] + oy) else p.lineTo(branch[j * 2] + ox, branch[j * 2 + 1] + oy)
        stroke(p, col, U * 0.02f, round = true)
        val lr = Rng(12)
        for (i in 0 until 9) {
            val bx = branch[(1 + i % 3) * 2]
            val by = branch[(1 + i % 3) * 2 + 1]
            val a = (lr.next() - 0.3f) * 2.4f
            val l = U * (0.06f + 0.06f * lr.next())
            val ex = bx + cos(a) * l
            val ey = by + sin(a) * l + U * 0.02f
            line(bx + ox, by + oy, ex + ox, ey + oy, col, U * 0.008f, round = true)
            for (j in 0 until 4) {
                val lx = ex + (lr.next() - 0.5f) * U * 0.07f
                val ly = ey + (lr.next() - 0.3f) * U * 0.06f
                val sway = sin(t * 0.6f + i + j) * 0.06f
                val size = U * (0.03f + 0.012f * lr.next())
                val turn = lr.next() * TAU + sway
                val color = if (shadow) col else rgb(branchLeaves[floor(lr.next() * 4).toInt()])
                if (shadow) lr.next()
                mapleLeaf(k.leaf, lx + ox, ly + oy, size, turn, color, !shadow, 1f)
            }
        }
    }

    // A soft vignette pulls the eye to the middle.
    val vr = max(W, H) * 0.75f
    rect(0f, 0f, W, H, rgrad(W / 2, H * 0.45f, vr, 0f to rgba(0, 0f), (U * 0.3f / vr) to rgba(0, 0f), 1f to rgba(0, 0.5f)))
    grain(0.045f)
}

/** A five-lobed maple leaf with pointed, lightly toothed tips. */
private fun Sketch.mapleLeaf(p: Path, x: Float, y: Float, s: Float, rot: Float, color: Int, veins: Boolean, alpha: Float) {
    c.save(); c.translate(x, y); c.rotate(rot * DEG)
    p.reset()
    val half = PI.toFloat() / 2
    val lobes = floatArrayOf(-half - 1.9f, 0.55f, -half - 0.95f, 0.85f, -half, 1f, -half + 0.95f, 0.85f, -half + 1.9f, 0.55f)
    for (i in 0 until 5) {
        val a = lobes[i * 2]
        val r = lobes[i * 2 + 1]
        val between = a - 0.48f
        if (i == 0) p.moveTo(cos(between) * s * 0.32f, sin(between) * s * 0.32f) else p.lineTo(cos(between) * s * 0.32f, sin(between) * s * 0.32f)
        p.lineTo(cos(a - 0.2f) * s * r * 0.72f, sin(a - 0.2f) * s * r * 0.72f)
        p.lineTo(cos(a - 0.08f) * s * r * 0.8f, sin(a - 0.08f) * s * r * 0.8f)
        p.lineTo(cos(a) * s * r, sin(a) * s * r)
        p.lineTo(cos(a + 0.08f) * s * r * 0.8f, sin(a + 0.08f) * s * r * 0.8f)
        p.lineTo(cos(a + 0.2f) * s * r * 0.72f, sin(a + 0.2f) * s * r * 0.72f)
    }
    p.close()
    fill(p, color)
    if (veins) {
        for (i in 0 until 5) {
            val a = lobes[i * 2]
            val r = lobes[i * 2 + 1]
            line(0f, 0f, cos(a) * s * r * 0.85f, sin(a) * s * r * 0.85f, rgba(0, 0.25f * alpha), 0.7f)
        }
        line(0f, 0f, 0f, s * 0.45f, rgba(0, 0.35f * alpha), 1f)
    }
    c.restore()
}

/** One koi seen from above: a bending body, flowing fins with rays, scales, markings and light. */
private fun Sketch.drawKoi(f: Koi, x: Float, y: Float, heading: Float, t: Float, shadow: Boolean, shx: Float, shy: Float, k: KoiPaths) {
    val L = U * f.len * (0.75f + 0.25f * f.depth)
    val n = 22
    val swim = t * 2.2f + f.ph * 5
    val sp = k.sp
    for (i in 0..n) {
        val s = i.toFloat() / n
        sp[i * 2] = -s * L
        sp[i * 2 + 1] = sin(s * 3.4f - swim) * L * 0.06f * (0.15f + s * s * 1.4f)
    }
    fun prof(s: Float): Float = when {
        s < 0.08f -> 0.62f + 0.38f * sqrt(s / 0.08f) * 0.55f
        s < 0.32f -> 0.83f + 0.17f * sin((s - 0.08f) / 0.24f * PI.toFloat() / 2)
        else -> 1 - ((s - 0.32f) / 0.68f).pow(1.25f) * 0.86f
    }
    fun wid(s: Float) = L * 0.145f * prof(s)
    fun sideX(i: Int, dir: Float): Float {
        val a = max(0, i - 1); val b = min(n, i + 1)
        val ddx = sp[b * 2] - sp[a * 2]; val ddy = sp[b * 2 + 1] - sp[a * 2 + 1]
        val l = hypot(ddx, ddy).takeIf { it > 0 } ?: 1f
        return sp[i * 2] - ddy / l * wid(i.toFloat() / n) * dir
    }
    fun sideY(i: Int, dir: Float): Float {
        val a = max(0, i - 1); val b = min(n, i + 1)
        val ddx = sp[b * 2] - sp[a * 2]; val ddy = sp[b * 2 + 1] - sp[a * 2 + 1]
        val l = hypot(ddx, ddy).takeIf { it > 0 } ?: 1f
        return sp[i * 2 + 1] + ddx / l * wid(i.toFloat() / n) * dir
    }
    val body = k.body
    body.reset()
    body.moveTo(sp[0] + L * 0.035f, sp[1])
    for (i in 0..n) body.lineTo(sideX(i, 1f), sideY(i, 1f))
    for (i in n downTo 0) body.lineTo(sideX(i, -1f), sideY(i, -1f))
    body.close()

    val tx = sp[n * 2]
    val ty = sp[n * 2 + 1]
    val tw = sin(t * 2.2f - 3.4f + f.ph * 5) * 0.5f
    val tl = L * if (f.butterfly) 0.42f else 0.3f
    val tail = k.tail
    tail.reset()
    tail.moveTo(tx + L * 0.04f, ty)
    tail.cubicTo(tx - tl * 0.3f, ty - L * 0.08f, tx - tl * 0.7f, ty - L * 0.14f + tw * L * 0.06f, tx - tl, ty - L * 0.16f + tw * L * 0.1f)
    tail.quadTo(tx - tl * 0.6f, ty + tw * L * 0.05f, tx - tl, ty + L * 0.16f + tw * L * 0.1f)
    tail.cubicTo(tx - tl * 0.7f, ty + L * 0.14f + tw * L * 0.06f, tx - tl * 0.3f, ty + L * 0.08f, tx + L * 0.04f, ty)
    tail.close()

    c.save()
    if (shadow) {
        c.translate(x + shx * (1.6f - f.depth), y + shy * (1.6f - f.depth))
        c.rotate(heading * DEG)
        fillBlurred(body, rgba(0x00040C, 0.42f), U * 0.008f)
        fillBlurred(tail, rgba(0x00040C, 0.42f), U * 0.008f)
        c.restore()
        return
    }
    c.translate(x, y)
    c.rotate(heading * DEG)
    val finFill = when (f.pat) {
        Variety.SHOWA -> rgba(0xD2DAE4, 0.5f)
        Variety.CHAGOI -> rgba(0xE6B482, 0.5f)
        else -> rgba(0xFFF4EC, 0.58f)
    }
    // Fins and tail behind the body.
    fill(tail, finFill)
    val ray = k.scratch
    ray.reset()
    for (j in 1 until 7) {
        val q = j / 7f
        ray.moveTo(tx, ty)
        ray.quadTo(tx - tl * 0.5f, ty + (q - 0.5f) * L * 0.2f, tx - tl * (0.9f + 0.1f * sin(q * PI.toFloat())), ty + (q - 0.5f) * L * 0.3f + tw * L * 0.08f)
    }
    stroke(ray, rgba(0xFFFFFF, 0.18f), 0.7f)
    val flap = sin(t * 1.6f + f.ph) * 0.18f
    val finLen = if (f.butterfly) 1.6f else 1f
    for ((s, dir, len, spread, fl) in listOf(
        floatArrayOf(0.2f, 1f, 0.19f, 0.9f, flap), floatArrayOf(0.2f, -1f, 0.19f, 0.9f, -flap),
        floatArrayOf(0.5f, 1f, 0.09f, 0.7f, flap * 0.5f), floatArrayOf(0.5f, -1f, 0.09f, 0.7f, -flap * 0.5f),
    ).map { arrayOf(it[0], it[1], it[2], it[3], it[4]) }) {
        // Root on the body's flank; the fan opens outward and back toward the tail.
        val idx = (s * n).roundToInt()
        val bx = sideX(idx, dir)
        val by = sideY(idx, dir)
        val out = atan2(-dir, -0.75f) + fl * dir
        val fin = k.fin
        fin.reset(); fin.moveTo(bx, by)
        ray.reset()
        for (j in 0..8) {
            val a = out + (j / 8f - 0.5f) * spread
            val r = L * len * finLen * (0.7f + 0.3f * sin(j / 8f * PI.toFloat()))
            val px = bx + cos(a) * r
            val py = by + sin(a) * r
            fin.lineTo(px, py)
            if (j in 1..7 && j % 2 == 1) { ray.moveTo(bx, by); ray.lineTo(px, py) }
        }
        fin.close()
        fill(fin, finFill)
        stroke(ray, rgba(0xFFFFFF, 0.2f), 0.6f)
    }
    // The body.
    fill(body, rgb(when (f.pat) {
        Variety.KOHAKU -> 0xf2ece4
        Variety.SHOWA -> 0x1c1c22
        Variety.PLATINUM -> 0xe4e6e2
        Variety.TANCHO -> 0xf4f0ea
        Variety.CHAGOI -> 0x9a6a42
    }))
    c.save()
    c.clipPath(body)
    fun atX(s: Float) = sp[min(n, max(0, (s * n).roundToInt())) * 2]
    fun atY(s: Float) = sp[min(n, max(0, (s * n).roundToInt())) * 2 + 1]
    // Markings: each patch is a cluster of overlapping, slightly turned lobes, so the edges are irregular.
    var seed = f.ph * 100
    fun patch(s: Float, dy: Float, prx: Float, pry: Float, color: Int, rot: Float = 0f) {
        val px = atX(s)
        val py = atY(s)
        for (j in 0 until 4) {
            seed += 1.7f
            val ox = (sHash(seed) - 0.5f) * prx * L * 0.9f
            val oy = (sHash(seed + 0.3f) - 0.5f) * pry * L * 0.9f
            val sx = 0.55f + 0.4f * sHash(seed + 0.6f)
            val sy = 0.55f + 0.4f * sHash(seed + 0.9f)
            ellipse(px + ox, py + dy * L + oy, prx * L * sx, pry * L * sy, rot + (sHash(seed + 1.2f) - 0.5f) * 1.2f, color)
        }
    }
    val red = rgb(0xd23a24)
    val white = rgb(0xf2ece4)
    when (f.pat) {
        Variety.KOHAKU -> { patch(0.07f, 0f, 0.06f, 0.07f, red); patch(0.3f, -0.03f, 0.1f, 0.08f, red, 0.3f); patch(0.5f, 0.04f, 0.07f, 0.06f, red); patch(0.7f, -0.02f, 0.05f, 0.05f, red) }
        Variety.SHOWA -> { patch(0.06f, 0f, 0.06f, 0.07f, red); patch(0.26f, 0.03f, 0.08f, 0.07f, white); patch(0.36f, -0.04f, 0.07f, 0.06f, red); patch(0.56f, 0.02f, 0.06f, 0.06f, white); patch(0.66f, -0.03f, 0.05f, 0.05f, red) }
        Variety.TANCHO -> patch(0.07f, 0f, 0.045f, 0.045f, rgb(0xd8302a))
        Variety.PLATINUM -> rect(-L, -L * 0.03f, L, L * 0.06f, rgba(0xFFE6A0, 0.25f))
        Variety.CHAGOI -> for (j in 0 until 6) patch(0.15f + j * 0.12f, 0f, 0.05f, 0.1f, rgba(0x462812, 0.18f))
    }
    // Scales: rows of small crescents along the body, gathered into one stroke.
    val step = L * 0.032f
    val scales = k.scratch
    scales.reset()
    var s = 0.12f
    while (s < 0.9f) {
        val px = atX(s)
        val py = atY(s)
        val w0 = wid(s)
        var o = -w0 * 0.8f
        while (o <= w0 * 0.8f) {
            val cxs = px + step * 0.3f
            val cys = py + o + ((s * 100) % 2) * step * 0.5f
            val r = step * 0.55f
            k.rect.set(cxs - r, cys - r, cxs + r, cys + r)
            scales.addArc(k.rect, 108f, 144f)
            o += step
        }
        s += step / L
    }
    stroke(scales, if (f.pat == Variety.SHOWA) rgba(0xFFFFFF, 0.07f) else rgba(0x281E1E, 0.1f), 0.7f)
    // Light along the back, shade on the flanks.
    val hi = k.fin
    hi.reset()
    hi.moveTo(sp[2], sp[3])
    for (i in 1..n - 4) hi.lineTo(sp[i * 2], sp[i * 2 + 1] - wid(i.toFloat() / n) * 0.28f)
    for (i in n - 4 downTo 1) hi.lineTo(sp[i * 2], sp[i * 2 + 1] + wid(i.toFloat() / n) * 0.12f)
    hi.close()
    fill(hi, rgba(0xFFFFFF, 0.16f))
    stroke(body, rgba(0x000A14, 0.28f), L * 0.035f)
    // The dorsal fin seen edge-on, a fine dark line along the spine.
    val dorsal = k.scratch
    dorsal.reset()
    val d0 = (n * 0.25f).roundToInt()
    for (i in d0..(n * 0.6f).roundToInt()) if (i == d0) dorsal.moveTo(sp[i * 2], sp[i * 2 + 1]) else dorsal.lineTo(sp[i * 2], sp[i * 2 + 1])
    stroke(dorsal, if (f.pat == Variety.SHOWA) rgba(0xFFFFFF, 0.12f) else rgba(0x3C1E14, 0.28f), 1.2f)
    c.restore()
    // Eyes.
    for (d in floatArrayOf(-1f, 1f)) {
        val ex = sideX(1, d)
        val ey = sideY(1, d)
        circle(ex - L * 0.01f, ey - d * L * 0.012f, L * 0.013f, rgb(0x14100e))
        circle(ex - L * 0.014f, ey - d * L * 0.014f, L * 0.004f, rgba(0xFFFFFF, 0.6f))
    }
    // Deeper fish sink into the blue.
    if (f.depth < 1) {
        val veil = rgba(0x081C30, (1 - f.depth) * 0.75f)
        fill(body, veil)
        fill(tail, veil)
    }
    c.restore()
}
