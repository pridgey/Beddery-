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
import dev.pridgey.beddery.savers.sFlicker
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sNoise
import dev.pridgey.beddery.savers.sSmooth
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private const val TAU = (PI * 2).toFloat()

/** A clearing by a mountain lake at dusk: tent and lantern, a log seat, the fire. Tap for a burst of sparks. */
@Composable
fun CampfireSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val paths = remember { Array(8) { Path() } }
    val burst = remember { floatArrayOf(-100f) }
    val cooldown = remember { Cooldown(20_000) }
    OnSaverTap { if (cooldown.tryAcquire()) burst[0] = time.value }
    SketchScene(time) { t -> drawCampfire(t, paths, t - burst[0]) }
}

private fun Sketch.drawCampfire(t: Float, p: Array<Path>, sinceBurst: Float) {
    val k = if (land) 1.35f else 1f // landscape skies are shorter: stretch heights a little
    val ground = H * if (land) 0.66f else 0.705f
    val lake = H * if (land) 0.585f else 0.625f
    val fx = W * if (land) 0.27f else 0.5f
    val f = 0.85f + 0.15f * sFlicker(t, 1f)

    // ---- Sky.
    rect(0f, 0f, W, lake + 2, vgrad(0f, lake, 0f to rgb(0x0a0d2a), 0.42f to rgb(0x1f1b45), 0.72f to rgb(0x4a2f5e), 1f to rgb(0xa0595d)))
    stars(0f, lake * 0.62f, if (land) 140 else 110, 3, t)
    // A shooting star now and then.
    val sc = t % 23
    if (sc < 0.9f) {
        val q = sc / 0.9f
        val sx = W * 0.15f + q * W * 0.3f
        val sy = H * 0.08f + q * H * 0.06f
        line(sx, sy, sx - W * 0.06f, sy - H * 0.012f, rgba(0xFFF0E6, sin(q * PI.toFloat()) * 0.7f), 1f)
    }
    val mx = W * if (land) 0.8f else 0.74f
    val my = H * if (land) 0.2f else 0.15f
    moon(mx, my, U * 0.048f, 0xF8E7D6, 0.16f)
    // Long, soft clouds lit from beneath by the moon.
    for ((cyF, cwF, speed, a, hF) in Clouds) {
        val cy = H * cyF * if (land) 0.9f else 1f
        val cw = W * cwF * if (land) 0.7f else 1f
        val ch = U * hF
        val cx = ((t * speed + cyF * 977) % (W + cw * 2)) - cw
        for (lobe in CloudLobes) {
            val x0 = cx + cw * lobe[0]
            val y0 = cy + ch * lobe[1]
            val w0 = cw * lobe[2]
            val h0 = ch * lobe[3]
            roundRect(x0, y0, w0, h0, h0 / 2, vgrad(y0, y0 + h0, 0f to rgba(0x2c2350, a), 0.75f to rgba(0x4a3462, a), 1f to rgba(0xb07a86, a * 0.8f)))
        }
    }

    // ---- Mountains, each range hazier with distance.
    val far = ridge(H * 0.5f * (if (land) 0.98f else 1f), H * 0.22f * k, 11f, 1.9f, rgb(0x7b4a6c), lake + 2, 0f, true, rgba(0xFFCDC3, 0.28f))
    // Snow on the high peaks only: each facet lit if it faces the moon, shadowed if not.
    val n = far.size / 2
    var yTop = Float.MAX_VALUE
    var yLow = -Float.MAX_VALUE
    for (i in 0 until n) { yTop = minOf(yTop, far[i * 2 + 1]); yLow = max(yLow, far[i * 2 + 1]) }
    val snowLine = yTop + (yLow - yTop) * 0.38f
    fun sl(x: Float) = snowLine + (sNoise(x * 0.07f, 3f) - 0.5f) * H * 0.03f + (sNoise(x * 0.35f, 7f) - 0.5f) * H * 0.01f
    val facet = p[0]
    for (i in 0 until n - 1) {
        val x0 = far[i * 2]; val y0 = far[i * 2 + 1]; val x1 = far[i * 2 + 2]; val y1 = far[i * 2 + 3]
        val s0 = sl(x0); val s1 = sl(x1)
        if (y0 >= s0 && y1 >= s1) continue
        // Judge the slope over a wider span so small wiggles don't flip the shading.
        val a0 = far[maxOf(0, i - 5) * 2 + 1]
        val a1 = far[minOf(n - 1, i + 6) * 2 + 1]
        val faceMoon = (a1 - a0) * (if (mx > x0) 1 else -1) > 0
        facet.reset()
        facet.moveTo(x0, y0); facet.lineTo(x1 + 0.6f, y1); facet.lineTo(x1 + 0.6f, max(s1, y1)); facet.lineTo(x0, max(s0, y0)); facet.close()
        fill(facet, rgb(if (faceMoon) 0xe2b8c4 else 0xa07090))
    }
    mist(H * 0.515f, H * 0.07f, 0xa35b5f, 0.55f)
    val r2 = ridge(H * 0.555f, H * 0.14f * k, 23f, 1.9f, rgb(0x5b3463), lake + 2, 0f, true, rgba(0xFFBEBE, 0.14f))
    // Tiny pines along the nearer range's crest.
    var i = 0
    while (i < r2.size / 2) {
        if (sHash(i * 1.7f) < 0.55f) pine(r2[i * 2], r2[i * 2 + 1] + 2, H * (0.012f + 0.012f * sHash(i.toFloat())), rgb(0x4a2a58), i.toFloat())
        i += 3
    }
    mist(H * 0.575f, H * 0.05f, 0x7d4866, 0.5f)
    ridge(lake + 1, H * 0.06f * k, 37f, 3.1f, rgb(0x3a2552), lake + 2, 0f, false)

    // ---- The lake, mirroring the sky and the moon.
    rect(0f, lake, W, ground - lake, vgrad(lake, ground, 0f to rgb(0x7a4660), 0.5f to rgb(0x3a2650), 1f to rgb(0x1c1534)))
    for (j in 0 until 16) {
        val y = lake + 3 + j * (ground - lake) / 18
        val half = U * (0.01f + 0.02f * sHash(j.toFloat())) * (0.7f + 0.3f * sin(t * 0.9f + j))
        rect(mx - half + sin(t * 0.5f + j) * 2, y, half * 2, 1.2f, rgba(0xFAE1D2, 0.4f * (1 - j / 16f)))
    }
    // The fire's glow wavering on the water.
    for (j in 0 until 12) {
        val y = lake + (ground - lake) * (0.3f + j * 0.055f)
        val half = U * (0.02f + 0.04f * sHash(j * 5f)) * (0.6f + 0.4f * sin(t * 2 + j))
        rect(fx - half + sin(t + j) * 3, y, half * 2, 1.2f, rgba(0xFF965A, 0.22f * f * (j / 12f)))
    }
    for (j in 0 until 10) {
        val y = lake + 4 + sHash(j * 3f) * (ground - lake - 6)
        val x = ((sHash(j * 7f) * W + t * 3) % (W * 1.08f)) - W * 0.08f
        rect(x, y, W * 0.08f, 1f, rgba(0xFFD2C8, 0.08f))
    }

    // ---- The far shore's trees, then the near forest, with a clearing for the fire.
    val clear = W * if (land) 0.17f else 0.26f
    treeLine(ground - H * 0.012f, H * 0.04f, H * 0.085f, U * 0.035f, 5, rgb(0x1e1636), skip = { x -> abs(x - fx) < clear * 0.75f })
    treeLine(
        ground + 2, H * 0.1f, H * if (land) 0.3f else 0.2f, U * 0.06f, 9, rgb(0x110c22),
        skip = { x -> abs(x - fx) < clear },
        lit = { x -> val d = abs(x - fx) / (W * 0.5f); if (d < 1) rgba(0xFF8C5A, 0.5f * (1 - d) * f) else null },
        litSide = { x -> if (x < fx) 1f else -1f },
    )

    // ---- Ground, with grass along its edge.
    rect(0f, ground, W, H - ground, vgrad(ground, H, 0f to rgb(0x140e24), 0.35f to rgb(0x0c0918), 1f to rgb(0x07050e)))
    var gx = 0f
    while (gx < W) {
        val h = (2 + 5 * sHash(gx * 0.37f)) * if (sHash(gx * 0.11f) > 0.6f) 1.6f else 1f
        rect(gx, ground - h, 1.4f, h, rgb(0x140e24))
        gx += 3
    }
    // Firelight pooled on the ground.
    glow(fx, ground + U * 0.02f, U * 0.75f, 0xff8f55, 0.42f * f, sy = 0.26f)
    glow(fx, ground + U * 0.01f, U * 0.25f, 0xffb27a, 0.35f * f, sy = 0.3f)

    // ---- Tent with a lantern glowing inside.
    val tx = if (land) fx - W * 0.16f else fx - W * 0.3f
    val tw = U * 0.27f
    val th = U * 0.19f
    val side = if (fx > tx) 1f else -1f
    val tent = p[1]
    tent.reset(); tent.moveTo(tx - tw / 2, ground + 2); tent.lineTo(tx, ground - th); tent.lineTo(tx + tw / 2, ground + 2); tent.close()
    fill(tent, rgb(0x1f1530))
    tent.reset(); tent.moveTo(tx, ground - th); tent.lineTo(tx + side * tw / 2, ground + 2); tent.lineTo(tx, ground + 2); tent.close()
    fill(tent, hgrad(tx, tx + side * tw / 2, 0f to rgba(0xff8c5a, 0.1f * f), 1f to rgba(0xff9b62, 0.5f * f)))
    line(tx, ground - th, tx, ground + 2, rgba(0x000000, 0.35f), 1f)
    tent.reset(); tent.moveTo(tx - tw * 0.12f, ground + 2); tent.lineTo(tx - tw * 0.01f, ground - th * 0.5f); tent.lineTo(tx + tw * 0.1f, ground + 2); tent.close()
    fill(tent, rgba(0xffbf7a, 0.8f + 0.1f * sin(t * 1.3f)))
    glow(tx, ground - th * 0.15f, tw * 0.7f, 0xffaa66, 0.22f, sy = 0.6f)
    line(tx, ground - th, tx + tw * 0.85f, ground + 3, rgba(0xFFAA78, 0.22f), 1f)
    line(tx, ground - th, tx - tw * 0.85f, ground + 3, rgba(0xFFAA78, 0.22f), 1f)

    // A lantern hung on a forked stick by the tent door.
    val lpx = tx + side * tw * 0.62f
    val lpTop = ground - th * 0.95f
    val stick = p[2]
    stick.reset(); stick.moveTo(lpx, ground + 3); stick.lineTo(lpx, lpTop); stick.lineTo(lpx + side * U * 0.03f, lpTop - U * 0.012f)
    stroke(stick, rgb(0x1a1220), 2f)
    val lanX = lpx + side * U * 0.026f
    val lanY = lpTop + U * 0.012f
    line(lanX, lpTop - U * 0.008f, lanX, lanY - U * 0.012f, rgba(0x281E28, 0.9f), 1f)
    rect(lanX - U * 0.009f, lanY - U * 0.014f, U * 0.018f, U * 0.004f, rgb(0x2a1c24))
    rect(lanX - U * 0.007f, lanY - U * 0.01f, U * 0.014f, U * 0.018f, rgb(0xffd28a))
    rect(lanX - U * 0.009f, lanY + U * 0.008f, U * 0.018f, U * 0.004f, rgb(0x2a1c24))
    glow(lanX, lanY, U * 0.12f, 0xffc070, 0.35f + 0.04f * sin(t * 2.7f))

    // ---- A log to sit on, a backpack and a stump.
    val bx = fx + if (land) W * 0.075f else W * 0.24f
    val byy = ground + U * if (land) 0.02f else 0.035f
    roundRect(bx - U * 0.1f, byy - U * 0.035f, U * 0.2f, U * 0.035f, U * 0.017f, rgb(0x24131c))
    roundRect(bx - U * 0.095f, byy - U * 0.035f, U * 0.17f, U * 0.008f, U * 0.004f, rgba(0xff9a62, 0.45f * f))
    ellipse(bx - U * 0.098f, byy - U * 0.0175f, U * 0.008f, U * 0.0165f, 0f, rgb(0x3a2228))
    val bpx = bx + U * 0.07f
    val bpy = byy - U * 0.002f
    roundRect(bpx - U * 0.024f, bpy - U * 0.06f, U * 0.048f, U * 0.06f, U * 0.014f, rgb(0x2c1e2e))
    roundRect(bpx - U * 0.02f, bpy - U * 0.066f, U * 0.04f, U * 0.022f, U * 0.01f, rgb(0x3a283a))
    rect(bpx - U * 0.016f, bpy - U * 0.03f, U * 0.032f, U * 0.018f, rgb(0x1e1420))
    rect(bpx - U * 0.024f, bpy - U * 0.055f, U * 0.006f, U * 0.05f, rgba(0xff9a62, 0.35f * f))
    rect(bpx - U * 0.026f, bpy - U * 0.07f, U * 0.052f, U * 0.008f, rgb(0x6a8a5a))
    if (!land) {
        val sx = fx - W * 0.13f
        val sy = ground + U * 0.05f
        rect(sx - U * 0.03f, sy - U * 0.045f, U * 0.06f, U * 0.045f, rgb(0x1c1018))
        ellipse(sx, sy - U * 0.045f, U * 0.03f, U * 0.009f, 0f, rgb(0x4a2c2a))
        ellipse(sx + U * 0.006f, sy - U * 0.046f, U * 0.018f, U * 0.004f, 0f, rgba(0xff9a62, 0.3f * f))
    }

    // ---- Fire: stones, logs, flames, smoke and sparks.
    val fy = ground + U * 0.025f
    for (j in 0 until 9) {
        val a = PI.toFloat() * (0.02f + 0.96f * j / 8f)
        val sx = fx - cos(a) * U * 0.1f
        val sy = fy + sin(a) * U * 0.022f - U * 0.004f
        val rw = U * (0.017f + 0.006f * sHash(j * 3f))
        val rh = U * 0.012f
        ellipse(sx, sy, rw, rh, 0f, rgb(0x1e1520))
        ellipse(sx, sy - rh * 0.45f, rw * 0.6f, rh * 0.32f, 0f, rgba(0xffa06a, 0.5f * f))
    }
    val lw = U * 0.16f
    val lh = U * 0.026f
    for (a in floatArrayOf(-0.3f, 0.3f)) {
        c.save(); c.translate(fx, fy - lh * 0.3f); c.rotate(Math.toDegrees(a.toDouble()).toFloat())
        roundRect(-lw / 2, -lh / 2, lw, lh, lh / 2, rgb(0x2a1518))
        roundRect(-lw / 2 + lh * 0.3f, -lh / 2, lw - lh * 0.6f, lh * 0.28f, lh * 0.14f, rgba(0xff9a5e, 0.6f * f))
        for (e in 0 until 4) {
            val ex = (-0.3f + e * 0.2f) * lw
            rect(ex, lh * 0.05f, lw * 0.06f, 1.3f, rgba(0xff6a2a, (0.5f + 0.5f * sin(t * 2 + e * 2 + a * 10)) * 0.8f))
        }
        c.restore()
    }
    // A tap throws the fire up for a few seconds.
    val boost = if (sinceBurst in 0f..4f) sin(sinceBurst / 4f * PI.toFloat()) else 0f
    val fh = U * 0.19f * (0.92f + 0.12f * sFlicker(t, 3f)) * (1 + 0.35f * boost)
    val sway = (sNoise(t * 1.7f, 4f) - 0.5f) * U * 0.03f
    glow(fx, fy - fh * 0.4f, U * 0.55f, 0xff9a5a, 0.4f * f)
    val flame = p[3]
    for (tg in Tongues) {
        val hh = fh * tg.h * if (tg.seed != 0f) 0.85f + 0.25f * sFlicker(t, tg.seed) else 1f
        val sw = if (tg.seed != 0f) (sNoise(t * 2.2f, tg.seed) - 0.5f) * U * 0.05f else sway * tg.h
        flamePath(flame, fx + tg.dx * U, fy - lh * 0.5f, U * tg.w, hh, sw)
        fill(flame, rgb(tg.color))
    }
    // A wisp of smoke: a chain of soft puffs that thin out as they rise.
    for (j in 0 until 26) {
        val q = j / 26f
        val x = fx + sin(q * 7 - t * 0.7f) * U * 0.03f * (0.3f + q) + q * U * 0.08f
        val y = fy - fh * 0.95f - q * H * 0.2f
        glow(x, y, U * (0.02f + 0.05f * q), 0xc8a8c0, 0.05f * (1 - q), additive = false)
    }
    val sparks = if (boost > 0f) 40 else 16
    for (j in 0 until sparks) {
        val life = 3 + sHash(j.toFloat()) * 3
        val age = (t + sHash(j + 50f) * life) % life
        val q = age / life
        val x = fx + (sHash(j + 9f) - 0.5f) * U * 0.08f + sin(age * 1.4f + j) * U * 0.025f * age + age * U * 0.01f
        val y = fy - fh * 0.6f - age * U * 0.085f
        val a = (1 - q) * 0.9f * sSmooth(0f, 0.08f, q) * if (j >= 16) boost else 1f
        rect(x, y, 1.6f, 1.6f, rgba(0xFFCD8C, a))
    }

    // ---- Foreground grasses framing the scene (never under the clock).
    val blade = p[4]
    fun fern(x0: Float, dir: Float, seed: Float) {
        for (j in 0 until 14) {
            val bx2 = x0 + dir * j * U * 0.012f
            val h = U * (0.1f + 0.18f * sHash(seed + j)) * (1 - j / 18f)
            val lean = dir * (0.15f + 0.35f * sHash(seed + j * 3)) + sin(t * 0.6f + j) * 0.02f
            blade.reset()
            blade.moveTo(bx2 - 2, H)
            blade.quadTo(bx2 + lean * h * 0.3f, H - h * 0.6f, bx2 + lean * h, H - h)
            blade.quadTo(bx2 + lean * h * 0.3f + 2, H - h * 0.55f, bx2 + 3, H)
            blade.close()
            fill(blade, rgb(0x06040c))
        }
    }
    fern(-U * 0.02f, 1f, 3f)
    fern(W + U * 0.02f, -1f, 9f)

    grain(0.055f)
}

private val CloudLobes = arrayOf(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(0.15f, -0.55f, 0.55f, 0.9f), floatArrayOf(0.45f, -0.35f, 0.4f, 0.8f), floatArrayOf(0.62f, 0.1f, 0.38f, 0.8f))

private data class Cloud(val y: Float, val w: Float, val speed: Float, val alpha: Float, val h: Float)

private val Clouds = listOf(Cloud(0.25f, 0.6f, 2.2f, 0.55f, 0.022f), Cloud(0.33f, 0.42f, 3.4f, 0.4f, 0.018f), Cloud(0.19f, 0.32f, 1.6f, 0.35f, 0.014f))

/** One tongue of flame; a flicker seed of 0 marks the steady core. */
private data class Tongue(val dx: Float, val w: Float, val h: Float, val seed: Float, val color: Int)

private val Tongues = listOf(
    Tongue(-0.045f, 0.07f, 0.62f, 7f, 0xe4523e),
    Tongue(0.05f, 0.065f, 0.7f, 8f, 0xe4523e),
    Tongue(0f, 0.12f, 1f, 0f, 0xf2703e),
    Tongue(0.012f, 0.088f, 0.74f, 0f, 0xffa04e),
    Tongue(0f, 0.05f, 0.44f, 0f, 0xffe0a0),
)
