package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.pridgey.beddery.savers.Rng
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.Sketch
import dev.pridgey.beddery.savers.SketchScene
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rgb
import dev.pridgey.beddery.savers.rgba
import dev.pridgey.beddery.savers.sHash
import dev.pridgey.beddery.savers.sLerp
import dev.pridgey.beddery.savers.sNoise
import dev.pridgey.beddery.savers.sSmooth
import java.util.concurrent.Executors
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
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAU = (PI * 2).toFloat()
private const val PIf = PI.toFloat()

/**
 * A tabby asleep on a hand-knit blanket in a dark bedroom, painted stroke by stroke. Moonlight
 * from a round window lies on the wall behind and creeps across it through the night; a little
 * night-light glows warm on the other side. The cat breathes, an ear flicks, the tail tip twitches.
 */
@Composable
fun SleepingCatSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val state = remember { CatState() }
    state.coat = CatCoats[ctx.settings.catCoat] ?: CatCoats.getValue("ginger")
    SketchScene(time) { t -> drawCatScene(t, state) }
}

/** Fur colours: base, markings and pale parts; how strongly tabby stripes show; calico patches; tuxedo whites. */
internal class FurCoat(val key: String, val base: IntArray, val dark: IntArray, val cream: IntArray, val stripes: Float, val nose: IntArray, val line: IntArray, val ear: IntArray, val patches: Boolean = false, val white: Boolean = false)

internal val CatCoats: Map<String, FurCoat> = listOf(
    FurCoat("ginger", intArrayOf(178, 120, 68), intArrayOf(64, 40, 22), intArrayOf(234, 200, 156), 1f, intArrayOf(208, 128, 112), intArrayOf(42, 22, 12), intArrayOf(216, 154, 138)),
    FurCoat("brown", intArrayOf(132, 102, 72), intArrayOf(34, 26, 20), intArrayOf(210, 190, 160), 1f, intArrayOf(176, 112, 104), intArrayOf(26, 18, 12), intArrayOf(200, 146, 136)),
    FurCoat("grey", intArrayOf(140, 142, 152), intArrayOf(48, 50, 58), intArrayOf(222, 222, 226), 1f, intArrayOf(196, 140, 146), intArrayOf(30, 30, 36), intArrayOf(210, 160, 164)),
    FurCoat("black", intArrayOf(17, 15, 18), intArrayOf(9, 8, 10), intArrayOf(28, 25, 29), 0.15f, intArrayOf(96, 72, 78), intArrayOf(96, 88, 92), intArrayOf(92, 66, 72)),
    FurCoat("white", intArrayOf(232, 228, 220), intArrayOf(204, 198, 190), intArrayOf(246, 242, 236), 0f, intArrayOf(228, 156, 156), intArrayOf(120, 100, 96), intArrayOf(236, 176, 172)),
    FurCoat("calico", intArrayOf(236, 230, 220), intArrayOf(204, 198, 190), intArrayOf(246, 242, 236), 0f, intArrayOf(224, 150, 150), intArrayOf(60, 44, 40), intArrayOf(232, 172, 168), patches = true),
    FurCoat("tuxedo", intArrayOf(19, 17, 20), intArrayOf(10, 9, 11), intArrayOf(240, 238, 234), 0f, intArrayOf(214, 150, 156), intArrayOf(120, 112, 116), intArrayOf(92, 66, 72), white = true),
).associateBy { it.key }

private val bakers = Executors.newSingleThreadExecutor { Thread(it, "cat-bake").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

private class CatState {
    @Volatile var coat: FurCoat = CatCoats.getValue("ginger")
    @Volatile var sprite: Bitmap? = null
    @Volatile var spriteKey = ""
    @Volatile var blanket: Bitmap? = null
    @Volatile var blanketKey = ""
    var pendingSprite = ""
    var pendingBlanket = ""
    var wall: Bitmap? = null
    var wallKey = ""
    var moon: Bitmap? = null
    var moonCanvas: Canvas? = null
    val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val strokes = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val dst = RectF()
}

private val cp = Path()
private val overlayPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY) }
private val outPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) }
private val addPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }

private fun Sketch.drawCatScene(t: Float, s: CatState) {
    val seat = H * if (land) 0.6f else 0.62f
    val catS = if (land) W * 0.42f else W * 0.92f
    val catX = if (land) W * 0.6f else W * 0.53f
    val dpr = min(2f, density)

    // ---- The wall, out of focus: rendered small and scaled up.
    val wkey = "${W}x$H"
    if (s.wallKey != wkey) {
        s.wall?.recycle()
        val k = 0.18f
        val b = Bitmap.createBitmap(ceil(W * k).toInt().coerceAtLeast(1), ceil(H * k).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val g = Canvas(b); g.scale(k, k)
        val p = Paint()
        p.shader = LinearGradient(0f, 0f, 0f, seat, intArrayOf(rgb(0x0b0e1c), rgb(0x141a2e), rgb(0x1a1e30)), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        g.drawRect(0f, 0f, W, H, p); p.shader = null
        p.color = rgb(0x0e1020); g.drawRect(0f, seat - U * 0.06f, W, seat, p)
        p.color = rgba(0xA0B4E6, 0.08f); g.drawRect(0f, seat - U * 0.06f, W, seat - U * 0.06f + 2, p)
        s.wall = b; s.wallKey = wkey
    }
    s.dst.set(0f, 0f, W, H); c.drawBitmap(s.wall!!, null, s.dst, s.paint)
    moonbeam(t, seat, s)

    // ---- The blanket and the cat, in focus. The first bake runs right away; rebakes run off the main thread.
    val cushTop = seat - U * 0.02f
    rect(0f, cushTop, W, H - cushTop, vgrad(cushTop, H, 0f to rgb(0x2a1c18), 0.3f to rgb(0x1a110e), 1f to rgb(0x0a0605)))
    val bkey = "$wkey@$dpr"
    if (s.blanketKey != bkey && s.pendingBlanket != bkey) {
        s.pendingBlanket = bkey
        val w = W; val h = H; val u = U; val l = land
        if (s.blanket == null) { s.blanket = bakeBlanket(w, h, seat, u, l, dpr); s.blanketKey = bkey }
        else bakers.execute { val b = bakeBlanket(w, h, seat, u, l, dpr); s.blanket = b; s.blanketKey = bkey }
    }
    s.blanket?.let { if (s.blanketKey == bkey) c.drawBitmap(it, null, s.dst.apply { set(0f, 0f, W, H) }, s.paint) }
    val px = min(900, (catS * min(3f, density)).roundToInt())
    val coat = s.coat
    val skey = "${coat.key}:$px"
    if (s.spriteKey != skey && s.pendingSprite != skey) {
        s.pendingSprite = skey
        if (s.sprite == null) { s.sprite = bakeKitty(px, coat); s.spriteKey = skey }
        else bakers.execute { val b = bakeKitty(px, coat); s.sprite = b; s.spriteKey = skey }
    }
    val sprite = s.sprite
    if (sprite != null) kitty(catX, seat + U * 0.01f, catS, t, sprite, coat, s)
    // Moonlight behind the cat: a cool glow round its silhouette.
    val cx = (if (land) W * 0.3f else W * 0.55f)
    glow(sLerp(catX, cx, 0.35f), seat - catS * 0.3f, catS * 0.62f, 0xa8bef0, 0.16f, sy = 0.55f)

    // A cool moonlit grade and a gentle vignette.
    overlayPaint.color = rgba(0x7896DC, 0.28f)
    c.drawRect(0f, 0f, W, H, overlayPaint)
    val far = max(W, H) * 0.85f
    rect(0f, 0f, W, H, rgrad(catX, seat - U * 0.15f, far, 0f to rgba(0, 0f), U * 0.3f / far to rgba(0, 0f), 1f to rgba(0, 0.65f)))
    grain(0.05f)
}

/** The moonlight from a round window lying on the wall, slanted, split by the window's bars, a curtain's shadow swaying at its edge. */
private fun Sketch.moonbeam(t: Float, seat: Float, s: CatState) {
    val k = 0.25f
    val bw = ceil(W * k).toInt().coerceAtLeast(1); val bh = ceil(H * k).toInt().coerceAtLeast(1)
    var b = s.moon
    if (b == null || b.width != bw || b.height != bh) { b?.recycle(); b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888); s.moon = b; s.moonCanvas = Canvas(b) }
    val g = s.moonCanvas!!
    g.drawColor(0, PorterDuff.Mode.CLEAR)
    g.save(); g.scale(k, k)
    val drift = sin(t / 2400f * TAU) * W * 0.06f
    val cx = (if (land) W * 0.3f else W * 0.55f) + drift
    val top = seat - U * if (land) 0.58f else 0.9f
    val bot = seat - U * if (land) 0.1f else 0.12f
    val w = if (land) W * 0.22f else W * 0.62f
    val skew = w * 0.3f
    val ry = (bot - top) / 2; val rx = w / 2; val oy = (top + bot) / 2
    val bar = U * 0.025f
    g.save(); g.translate(cx, oy); g.skew(skew / (bot - top), 0f)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.shader = RadialGradient(-rx * 0.1f, ry * 0.25f, max(rx, ry), rgba(0xAFC8FF, 0.24f), rgba(0x96AFEB, 0.12f), Shader.TileMode.CLAMP)
    val r = RectF(-rx, -ry, rx, ry)
    g.drawOval(r, p)
    outPaint.style = Paint.Style.FILL; outPaint.color = -0x1000000
    g.drawRect(-bar / 2, -ry, bar / 2, ry, outPaint); g.drawRect(-rx, -bar / 2, rx, bar / 2, outPaint)
    outPaint.style = Paint.Style.STROKE; outPaint.strokeWidth = bar * 0.8f
    r.set(-rx * 0.93f, -ry * 0.93f, rx * 0.93f, ry * 0.93f); g.drawOval(r, outPaint)
    g.restore()
    // A curtain's shadow swaying gently across one edge.
    val x0 = cx - w / 2 - skew / 2
    val sway = sin(t * 0.25f) * U * 0.02f + sin(t * 0.11f) * U * 0.015f
    cp.reset(); cp.moveTo(x0 - U * 0.05f, top); cp.quadTo(x0 + w * 0.12f + sway, (top + bot) / 2, x0 + skew - U * 0.05f + sway * 0.5f, bot)
    cp.lineTo(x0 - U * 0.2f, bot); cp.lineTo(x0 - U * 0.2f, top); cp.close()
    outPaint.style = Paint.Style.FILL; outPaint.color = rgba(0, 0.75f)
    g.drawPath(cp, outPaint)
    g.restore()
    s.dst.set(0f, 0f, W, H); c.drawBitmap(b, null, s.dst, addPaint)
    // The night-light, low on the far side.
    val nx = if (land) W * 0.12f else W * 0.1f
    val ny = seat - U * 0.12f
    glow(nx, ny, U * 0.35f, 0xffb070, 0.18f)
    glow(nx, ny, U * 0.05f, 0xffd8a0, 0.5f)
}

// ---------------------------------------------------------------- The cat

// The cat's shapes in cat units: x right, y up is negative, about one unit wide, base at y = 0.
private const val KBcx = 0.04f; private const val KBcy = -0.19f; private const val KBrx = 0.43f; private const val KBry = 0.205f
private const val KHcx = -0.3f; private const val KHcy = -0.15f; private const val KHrx = 0.155f; private const val KHry = 0.128f; private const val KHrot = -0.22f
private const val HAx = 0.27f; private const val HAy = -0.15f; private const val HAr = 0.15f

private fun ell(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float): Float { val u = (x - cx) / rx; val v = (y - cy) / ry; return u * u + v * v }
private fun bodyE(x: Float, y: Float) = min(min(ell(x, y, KBcx, KBcy, KBrx, KBry), ell(x, y, 0.24f, -0.21f, 0.22f, 0.22f)), ell(x, y, -0.14f, -0.2f, 0.17f, 0.17f))
private fun lerpC(a: IntArray, b: IntArray, t: Float) = FloatArray(3) { sLerp(a[it].toFloat(), b[it].toFloat(), t) }
private fun FloatArray.mixTo(b: IntArray, t: Float): FloatArray { for (i in 0..2) this[i] = sLerp(this[i], b[i].toFloat(), t); return this }
private fun argb(c: FloatArray, a: Float = 1f): Int =
    ((a.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (c[0].coerceIn(0f, 255f).toInt() shl 16) or (c[1].coerceIn(0f, 255f).toInt() shl 8) or c[2].coerceIn(0f, 255f).toInt()
private fun argb(c: IntArray, k: Float = 1f, a: Float = 1f) = argb(FloatArray(3) { c[it] * k }, a)

/** A calico's patches: 1 orange, -1 black, 0 white, in soft-edged blobs. */
private fun calicoPatch(x: Float, y: Float): Float {
    fun n2(a: Float, b: Float, seed: Float): Float {
        val ia = floor(a); val ib = floor(b); val fa = a - ia; val fb = b - ib
        val u = fa * fa * (3 - 2 * fa); val v = fb * fb * (3 - 2 * fb)
        fun hh(i: Float, j: Float) = sHash(i * 157 + j * 311 + seed * 71)
        return sLerp(sLerp(hh(ia, ib), hh(ia + 1, ib), u), sLerp(hh(ia, ib + 1), hh(ia + 1, ib + 1), u), v)
    }
    val n = n2(x * 4.2f + 3, y * 4.2f + 1, 1f) * 0.7f + n2(x * 9, y * 9, 2f) * 0.3f
    return sSmooth(0.4f, 0.48f, n) * if (n2(x * 2.6f + 7, y * 2.6f + 5, 3f) > 0.5f) 1f else -1f
}
private val PatchOrange = intArrayOf(206, 124, 62)
private val PatchBlack = intArrayOf(40, 34, 34)
private fun FloatArray.patch(coat: FurCoat, x: Float, y: Float): FloatArray {
    if (!coat.patches) return this
    val p = calicoPatch(x, y)
    return if (p > 0) mixTo(PatchOrange, p) else if (p < 0) mixTo(PatchBlack, -p) else this
}

private fun bodyFur(x: Float, y: Float, coat: FurCoat): FloatArray {
    val u = (x - KBcx) / KBrx; val v = (y - KBcy) / KBry; val f = sqrt(bodyE(x, y))
    val hd = hypot(x - HAx, y - HAy) / HAr
    val band = sin(u * 16 + 1.6f * v * v + sNoise(x * 40, 3f) * 1.4f + sNoise(y * 60, 5f) * 0.8f)
    var k = sSmooth(0.55f, 0.85f, band) * sSmooth(0.95f, 0.2f, v) * 0.8f * (0.6f + 0.4f * sNoise(x * 25 + y * 18, 8f)) * coat.stripes
    val ring = sSmooth(0.55f, 0.9f, sin(hd * 8 + 0.5f)) * sSmooth(1.05f, 0.7f, hd)
    k = sLerp(k, ring * 0.55f * coat.stripes, sSmooth(1.1f, 0.8f, hd) * 0.6f)
    k = max(k, sSmooth(0.8f, 0.95f, f) * sSmooth(-0.1f, -0.5f, v) * 0.7f * coat.stripes)
    val c = lerpC(coat.base, coat.dark, k)
    c.mixTo(coat.cream, sSmooth(0.25f, 0.85f, v) * sSmooth(0.6f, 0f, abs(u + 0.1f)) * 0.65f)
    if (coat.white) c.mixTo(coat.cream, sSmooth(0.2f, 0.1f, hypot(x + 0.2f, (y + 0.05f) * 1.3f)))
    return c.patch(coat, x, y)
}

private fun bodyFlow(x: Float, y: Float): Float {
    val u = (x - KBcx) / KBrx; val v = (y - KBcy) / KBry
    var dx = -v / KBry; var dy = u / KBrx
    if (dx < 0) { dx = -dx; dy = -dy }
    val l = hypot(dx, dy).takeIf { it > 0f } ?: 1f
    dx = dx / l + 0.8f; dy = dy / l + 0.15f
    val hx = x - HAx; val hy = y - HAy
    val hl = hypot(hx, hy).takeIf { it > 0f } ?: 1f
    val w = sSmooth(1.1f, 0.5f, hl / HAr) * 0.45f
    dx = sLerp(dx, -hy / hl, w); dy = sLerp(dy, hx / hl, w)
    return atan2(dy, dx)
}

private fun headFur(u: Float, v: Float, coat: FurCoat): FloatArray {
    val c = FloatArray(3) { coat.base[it] * 1.03f }
    val m = v < -0.15f && v > -0.95f && abs(sin((u + 0.05f) * 9.5f)) > 0.75f && abs(u) < 0.55f
    val cheek = v > -0.05f && v < 0.35f && abs(sin(v * 12 + u * 3)) > 0.82f && abs(u) > 0.55f
    c.mixTo(coat.dark, max(if (m) 0.85f else 0f, if (cheek) 0.7f else 0f) * coat.stripes)
    val muzzle = if (coat.white) max(sSmooth(0.75f, 0.4f, hypot(u * 1.1f, (v - 0.4f) * 1.3f)), sSmooth(0.2f, 0.08f, abs(u + 0.05f * v)) * sSmooth(-0.6f, -0.1f, v))
    else sSmooth(0.55f, 0.25f, hypot(u * 1.3f, (v - 0.45f) * 1.6f))
    c.mixTo(coat.cream, muzzle * 0.9f)
    return c.patch(coat, u * 0.15f - 0.3f, v * 0.13f - 0.15f)
}

private fun tailX(q: Float): Float { val u = 1 - q; return u * u * u * 0.4f + 3 * u * u * q * 0.52f + 3 * u * q * q * 0.12f + q * q * q * -0.17f }
private fun tailY(q: Float): Float { val u = 1 - q; return u * u * u * -0.12f + 3 * u * u * q * 0f + 3 * u * q * q * 0.035f + q * q * q * -0.008f }
private fun tailW(q: Float) = 0.04f * (1 - 0.3f * q) * if (q > 0.9f) sqrt(max(0f, (1 - q) / 0.1f)) * 0.5f + 0.5f else 1f

/** Bakes the cat's still parts into a sprite, [px] pixels per cat unit. */
private fun bakeKitty(px: Int, coat: FurCoat): Bitmap {
    val bw = ceil(px * 1.2f).toInt(); val bh = ceil(px * 0.62f).toInt()
    val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val g = Canvas(bmp)
    g.translate(bw * 0.5f, bh * 0.92f); g.scale(px.toFloat(), px.toFloat())
    val lw = 1f / px
    val r = Rng(11)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val body = Path().apply {
        addOval(RectF(KBcx - KBrx, KBcy - KBry, KBcx + KBrx, KBcy + KBry), Path.Direction.CW)
        addOval(RectF(0.02f, -0.43f, 0.46f, 0.01f), Path.Direction.CW)
        addOval(RectF(-0.31f, -0.37f, 0.03f, -0.03f), Path.Direction.CW)
    }
    g.save(); g.clipRect(-1f, -1f, 1f, 0.005f)
    p.color = argb(coat.base, 0.75f); g.drawPath(body, p)
    val strokes = (14000 * (px / 400f)).roundToInt()
    for (i in 0 until strokes) {
        val x = -0.65f + r.next() * 1.25f
        val y = -0.43f + r.next() * 0.44f
        if (bodyE(x, y) >= 1f || y >= 0.005f) continue
        if (y > -0.01f && r.next() < 0.5f) continue
        val a = bodyFlow(x, y) + (r.next() - 0.5f) * 0.35f
        val len = 0.016f + 0.016f * r.next()
        val col = bodyFur(x, y, coat)
        val lamp = (0.5f - (x + 0.3f) * 0.9f + (y + 0.1f) * -0.3f).coerceIn(0f, 1f)
        val under = sSmooth(-0.08f, 0f, y)
        val shade = (1 + 0.35f * lamp) * (1 - 0.35f * under) * (0.85f + 0.3f * r.next())
        val fu = (x - KBcx) / KBrx; val fv = (y - KBcy) / KBry
        val rimK = sSmooth(0.9f, 1f, sqrt(bodyE(x, y))) * sSmooth(-0.1f, -0.6f, fv) * sSmooth(-0.6f, 0.2f, fu) * 0.35f
        for (k in 0..2) col[k] = sLerp(col[k] * shade, RimWarm[k], min(1f, rimK * 1.6f))
        st.color = argb(col); st.strokeWidth = lw * (1.2f + r.next() * 1.2f)
        g.drawLine(x, y, x + cos(a) * len, y + sin(a) * len, st)
    }
    g.restore()
    // Shadow where the head and paws rest against the body, and under the belly.
    g.save(); g.clipPath(body)
    p.shader = RadialGradient(-0.24f, -0.12f, 0.3f, intArrayOf(rgba(0x28140A, 0.5f), rgba(0x28140A, 0.5f), rgba(0x28140A, 0f)), floatArrayOf(0f, 0.27f, 1f), Shader.TileMode.CLAMP)
    g.drawRect(-0.6f, -0.5f, 0.2f, 0f, p)
    p.shader = LinearGradient(0f, -0.1f, 0f, 0f, rgba(0x28140A, 0f), rgba(0x28140A, 0.4f), Shader.TileMode.CLAMP)
    g.drawRect(-0.6f, -0.1f, 0.6f, 0f, p); p.shader = null
    g.restore()

    paintTail(g, lw, r, 0f, 0.86f, 0f, coat, p, st)

    // Front paws under the chin.
    for (pw in Paws) {
        g.save(); g.translate(pw[0], pw[1]); g.rotate(Math.toDegrees(pw[2].toDouble()).toFloat()); g.scale(pw[3], pw[3])
        p.color = argb(coat.base, 0.95f); g.drawOval(RectF(-0.05f, -0.038f, 0.11f, 0.026f), p)
        p.color = argb(coat.cream); g.drawOval(RectF(-0.072f, -0.032f, 0.012f, 0.024f), p)
        for (k in 0 until 60) {
            val x = -0.05f + r.next() * 0.15f; val y = -0.03f + r.next() * 0.05f
            if (ell(x, y, 0.03f, -0.006f, 0.08f, 0.032f) > 1f) continue
            st.color = if (x < 0f) rgba(0xFFF0DC, 0.5f) else rgba(0x784628, 0.4f); st.strokeWidth = lw
            g.drawLine(x, y, x + 0.015f, y + 0.002f, st)
        }
        st.color = rgba(0x785A46, 0.55f); st.strokeWidth = lw * 1.2f
        for (tx in floatArrayOf(-0.055f, -0.035f)) g.drawLine(tx, -0.02f, tx - 0.004f, 0.012f, st)
        g.restore()
    }

    // The head, resting on the paws.
    g.save(); g.translate(KHcx, KHcy); g.rotate(Math.toDegrees(KHrot.toDouble()).toFloat())
    val head = Path().apply {
        addOval(RectF(-KHrx, -KHry, KHrx, KHry), Path.Direction.CW)
        val m = android.graphics.Matrix()
        val a = Path().apply { addOval(RectF(-0.11f, -0.085f, 0.11f, 0.085f), Path.Direction.CW) }
        m.setRotate(Math.toDegrees(0.4).toFloat()); m.postTranslate(-0.06f, 0.035f); a.transform(m); addPath(a)
        val b2 = Path().apply { addOval(RectF(-0.11f, -0.08f, 0.11f, 0.08f), Path.Direction.CW) }
        m.setRotate(Math.toDegrees(-0.4).toFloat()); m.postTranslate(0.07f, 0.04f); b2.transform(m); addPath(b2)
    }
    ear(g, lw / 1.3f, r, 0.075f, -0.09f, 0.3f, false, coat, p, st)
    p.color = argb(coat.base, 0.8f); g.drawPath(head, p)
    for (i in 0 until (3200 * (px / 400f)).roundToInt()) {
        val x = -0.19f + r.next() * 0.38f; val y = -0.15f + r.next() * 0.28f
        if (ell(x, y, 0f, 0f, KHrx, KHry) > 1 && ell(x, y, -0.06f, 0.035f, 0.11f, 0.085f) > 1 && ell(x, y, 0.07f, 0.04f, 0.11f, 0.08f) > 1) continue
        val u = x / KHrx; val v = y / KHry
        val a = atan2(y - 0.03f, x + 0.03f) + (r.next() - 0.5f) * 0.3f
        val len = 0.014f + 0.012f * r.next()
        val col = headFur(u, v, coat)
        val lamp = (0.6f - u * 0.5f).coerceIn(0f, 1f)
        val sh = (1 + 0.3f * lamp) * (0.85f + 0.3f * r.next()) * (1 - 0.25f * sSmooth(0.3f, 1f, v))
        for (k in 0..2) col[k] *= sh
        st.color = argb(col); st.strokeWidth = lw * (1 + r.next())
        g.drawLine(x, y, x + cos(a) * len, y + sin(a) * len, st)
    }
    // Closed eyes, nose, mouth, whiskers.
    for (e in Eyes) {
        val ex = e[0]; val ey = e[1]; val w = e[2]
        st.color = argb(coat.line); st.strokeWidth = lw * 3.2f
        g.drawArc(RectF(ex - w, ey - w * 0.45f, ex + w, ey + w * 0.45f), 27f, 126f, false, st)
        st.strokeWidth = lw * 2
        g.drawLine(ex + sign(ex) * w * 0.95f, ey + w * 0.12f, ex + sign(ex) * w * 1.25f, ey + w * 0.02f, st)
    }
    val nose = Path().apply { moveTo(-0.018f, 0.045f); lineTo(0.016f, 0.043f); quadTo(0.004f, 0.068f, -0.002f, 0.07f); quadTo(-0.01f, 0.066f, -0.018f, 0.045f); close() }
    p.color = argb(coat.nose); g.drawPath(nose, p)
    p.color = rgba(0xFFE6DC, 0.6f); g.drawOval(RectF(-0.013f, 0.047f, 0.001f, 0.053f), p)
    st.color = argb(FloatArray(3) { min(255f, coat.line[it] + 30f) }); st.strokeWidth = lw * 1.6f
    val mouth = Path().apply { moveTo(-0.002f, 0.07f); lineTo(-0.002f, 0.08f); quadTo(-0.014f, 0.09f, -0.026f, 0.084f); moveTo(-0.002f, 0.08f); quadTo(0.01f, 0.09f, 0.022f, 0.083f) }
    g.drawPath(mouth, st)
    p.color = rgba(0x5A3C28, 0.6f)
    for (sd in floatArrayOf(-1f, 1f)) for (k in 0 until 3) for (m in 0 until 3) g.drawCircle(sd * (0.03f + m * 0.012f), 0.072f + k * 0.009f, lw * 1.3f, p)
    st.color = rgba(0xFAF5EB, 0.4f); st.strokeWidth = lw
    val wh = Path()
    for (sd in floatArrayOf(-1f, 1f)) for (k in 0 until 4) { wh.moveTo(sd * 0.04f, 0.075f + k * 0.007f); wh.quadTo(sd * 0.13f, 0.06f + k * 0.018f, sd * (0.22f - k * 0.01f), 0.07f + k * 0.03f) }
    g.drawPath(wh, st)
    g.restore()
    // Soft shadow where the head rests on the body and paws.
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    p.shader = RadialGradient(-0.3f, -0.03f, 0.2f, rgba(0x28140A, 0.35f), rgba(0x28140A, 0f), Shader.TileMode.CLAMP)
    g.drawRect(-0.6f, -0.3f, 0f, 0.05f, p)
    return bmp
}
private val RimWarm = floatArrayOf(255f, 196f, 128f)
private val Paws = arrayOf(floatArrayOf(-0.36f, -0.03f, 0.15f, 1f), floatArrayOf(-0.22f, -0.02f, -0.05f, 0.95f))
private val Eyes = arrayOf(floatArrayOf(-0.065f, -0.005f, 0.042f), floatArrayOf(0.06f, -0.01f, 0.04f))

/** An ear in fur strokes: pink inner skin with pale tufts, combed fur along the rims, backlit hairs. */
private fun ear(g: Canvas, lw: Float, r: Rng, x: Float, y: Float, a: Float, near: Boolean, coat: FurCoat, p: Paint, st: Paint) {
    val dim = if (near) 1f else 0.7f
    g.save(); g.translate(x, y); g.rotate(Math.toDegrees(a.toDouble()).toFloat()); g.scale(1.3f, 1.3f)
    fun halfW(q: Float) = 0.056f * (1 - q) * (1 - 0.25f * q)
    val under = Path().apply { moveTo(-0.05f, 0.03f); quadTo(-0.034f, -0.045f, 0.002f, -0.09f); quadTo(0.03f, -0.045f, 0.048f, 0.03f); close() }
    p.color = argb(coat.base, 0.6f * dim); g.drawPath(under, p)
    for (k in 0 until 90) {
        val q = r.next().pow(0.8f) * 0.75f; val u = (r.next() * 2 - 1) * 0.6f
        val xx = u * halfW(q); val yy = 0.02f - q * 0.11f
        st.color = argb(coat.ear, (0.55f + 0.45f * q) * dim * (0.9f + 0.2f * r.next())); st.strokeWidth = lw * (1.2f + r.next())
        g.drawLine(xx, yy, xx * 0.9f, yy - 0.012f, st)
    }
    for (k in 0 until 320) {
        val q = r.next(); val side = if (r.next() < 0.5f) -1f else 1f
        val edge = 0.55f + 0.6f * r.next()
        val xx = side * halfW(q) * edge; val yy = 0.03f - q * 0.12f
        val ang = -PIf / 2 + side * (0.25f + 0.35f * (edge - 0.55f)) + (r.next() - 0.5f) * 0.4f
        val len = 0.012f + 0.012f * r.next()
        val c = lerpC(coat.base, coat.dark, sSmooth(0.55f, 0.95f, q) * coat.stripes * 0.7f).patch(coat, x + xx, y + yy)
        val k2 = (if (side < 0) 1.18f else 0.85f) * dim * (0.85f + 0.3f * r.next())
        for (i in 0..2) c[i] = min(255f, c[i] * k2)
        st.color = argb(c); st.strokeWidth = lw * (1 + r.next() * 1.1f)
        g.drawLine(xx, yy, xx + cos(ang) * len, yy + sin(ang) * len, st)
    }
    val tuft = Path()
    for (k in 0 until 30) {
        val xx = (r.next() - 0.5f) * 0.05f; val yy = 0.016f - r.next() * 0.02f
        st.color = argb(coat.cream, 1f, (0.35f + 0.35f * r.next()) * dim); st.strokeWidth = lw * (0.8f + r.next() * 0.7f)
        tuft.reset(); tuft.moveTo(xx, yy); tuft.quadTo(xx * 0.7f, yy - 0.03f, xx * 0.4f + (r.next() - 0.5f) * 0.01f, yy - 0.045f - r.next() * 0.025f)
        g.drawPath(tuft, st)
    }
    for (k in 0 until 40) {
        val q = r.next() * 0.9f; val side = if (r.next() < 0.5f) -1f else 1f
        val xx = side * halfW(q) * 1.02f; val yy = 0.03f - q * 0.12f
        st.color = rgba(0xFFC480, (if (near) 0.45f else 0.25f) * r.next()); st.strokeWidth = lw
        g.drawLine(xx, yy, xx + side * 0.006f, yy - 0.01f, st)
    }
    g.restore()
}

/** The tail from [q0] to [q1] along its length: a furred tube with rings and a dark tip. */
private fun paintTail(g: Canvas, lw: Float, r: Rng, q0: Float, q1: Float, twitch: Float, coat: FurCoat, p: Paint, st: Paint) {
    val n = 40
    val px = FloatArray(n + 1); val py = FloatArray(n + 1); val pq = FloatArray(n + 1)
    for (i in 0..n) { val q = q0 + (q1 - q0) * i / n; pq[i] = q; px[i] = tailX(q); py[i] = tailY(q) + if (q > 0.8f) twitch * (q - 0.8f) / 0.2f else 0f }
    val path = Path()
    val rx = FloatArray(n + 1); val ry = FloatArray(n + 1)
    for (i in 0..n) {
        val i2 = min(n, i + 1); val i1 = max(0, i - 1)
        val dx = px[i2] - px[i1]; val dy = py[i2] - py[i1]; val l = hypot(dx, dy).takeIf { it > 0f } ?: 1f; val w = tailW(pq[i])
        if (i == 0) path.moveTo(px[i] - dy / l * w, py[i] + dx / l * w) else path.lineTo(px[i] - dy / l * w, py[i] + dx / l * w)
        rx[i] = px[i] + dy / l * w; ry[i] = py[i] - dx / l * w
    }
    for (i in n downTo 0) path.lineTo(rx[i], ry[i])
    path.close()
    p.xfermode = null; p.shader = null
    p.color = argb(coat.base, 0.8f); g.drawPath(path, p)
    for (i in 0 until (2600 * (q1 - q0)).roundToInt()) {
        val k = (r.next() * n).toInt().coerceAtMost(n - 1)
        val x = px[k]; val y = py[k]; val q = pq[k]; val x2 = px[k + 1]; val y2 = py[k + 1]
        val a = atan2(y2 - y, x2 - x) + (r.next() - 0.5f) * 0.3f
        val w = tailW(q)
        val off = (r.next() - 0.5f) * 2.2f * w
        val nl = hypot(y2 - y, x2 - x).takeIf { it > 0f } ?: 1f
        val sx = x - (y2 - y) / nl * off; val sy = y + (x2 - x) / nl * off
        val ring = sSmooth(0.15f, 0.7f, sin(q * 22 - 1)) * sSmooth(0.15f, 0.3f, q) * 0.8f
        val tip = sSmooth(0.88f, 0.95f, q)
        val dk = max(ring * 0.85f * coat.stripes, tip * if (coat.stripes > 0.5f) 1f else 0f)
        val c = lerpC(coat.base, coat.dark, dk).patch(coat, sx, sy)
        val lit = 1 - 0.25f * (off / w)
        val j = 0.85f + 0.3f * r.next()
        for (m in 0..2) c[m] *= lit * j
        st.color = argb(c); st.strokeWidth = lw * (1.2f + r.next())
        val len = 0.02f + 0.015f * r.next()
        g.drawLine(sx, sy, sx + cos(a) * len, sy + sin(a) * len, st)
    }
}

private val earPaint = Paint(Paint.ANTI_ALIAS_FLAG)

/** Draws the sleeping cat at (x, y), the middle of its base, [s] wide. */
private fun Sketch.kitty(x: Float, y: Float, s: Float, t: Float, sprite: Bitmap, coat: FurCoat, st: CatState) {
    val breath = 0.5f + 0.5f * sin(t * 1.1f)
    glow(x + s * 0.04f, y, s * 0.6f, 0, 0.5f, additive = false, sy = 0.14f)
    c.save()
    c.translate(x, y); c.scale(1f, 1 + 0.018f * breath)
    // The near ear sits behind the head's fur, so its base is buried in it. It flicks now and then.
    val fp = t % 11f
    val flick = if (fp < 0.45f) sin(fp / 0.45f * PIf) * 0.35f else 0f
    c.save(); c.scale(s, s); c.translate(KHcx, KHcy); c.rotate(Math.toDegrees(KHrot.toDouble()).toFloat())
    ear(c, 1 / s / 1.3f, Rng(5), -0.075f, -0.09f, -0.3f - flick, true, coat, earPaint, st.strokes)
    c.restore()
    st.dst.set(-s * 0.6f, -s * 0.62f * 0.92f, s * 0.6f, s * 0.62f * 0.08f)
    c.drawBitmap(sprite, null, st.dst, st.paint)
    c.restore()
    // The tail tip twitches every so often.
    val tp = t % 7f
    val tw = if (tp < 0.7f) sin(tp / 0.7f * PIf * 2) * 0.012f else 0f
    c.save(); c.translate(x, y); c.scale(s, s)
    paintTail(c, 1 / s, Rng(9), 0.84f, 1f, tw, coat, earPaint, st.strokes)
    c.restore()
}

// ---------------------------------------------------------------- The blanket

/** A chunky hand-knit blanket in stockinette: columns of stacked Vs, each V two plump loops,
 *  lit from the lamp side, fuzzed with fibres, the far edge soft, the near edge rolling into shadow. */
private fun bakeBlanket(W: Float, H: Float, seat: Float, U: Float, land: Boolean, dpr: Float): Bitmap {
    val bmp = Bitmap.createBitmap(ceil(W * dpr).toInt().coerceAtLeast(1), ceil(H * dpr).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val g = Canvas(bmp); g.scale(dpr, dpr)
    val r = Rng(17)
    val top = seat - U * 0.08f
    val edge = seat + U * if (land) 0.1f else 0.27f
    val rh = U * 0.03f
    fun rowY(x: Float, row: Int) = top + row * rh + sin(x / W * 3.1f + 0.4f) * U * 0.01f + exp(-((x / W - 0.5f) / 0.28f).pow(2)) * U * 0.014f * min(1f, row / 6f)
    val rows = ceil((edge - top) / rh).toInt() + 1
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    val under = Path()
    under.moveTo(-10f, rowY(-10f, 0))
    var x = -10f
    while (x <= W + 10) { under.lineTo(x, rowY(x, 0) - rh * 0.4f); x += 6 }
    x = W + 10
    while (x >= -10) { under.lineTo(x, rowY(x, rows - 1) + rh * 0.5f); x -= 6 }
    under.close()
    p.color = rgb(0x6a5038); g.drawPath(under, p)
    val oval = RectF()
    for (row in 0 until rows) {
        val persp = 0.8f + 0.2f * (row.toFloat() / rows)
        val sw = U * 0.046f * persp
        val shift = sin(row * 0.7f) * sw * 0.08f
        var col = -1
        while (col < W / sw + 1) {
            val cx = col * sw + shift + (r.next() - 0.5f) * sw * 0.05f
            val y = rowY(cx, row)
            val roll = sSmooth(edge - rh * 2.2f, edge + rh * 0.4f, y)
            val back = sSmooth(top + rh * 2.5f, top, y)
            val lamp = (1.05f - cx / W * 0.45f).coerceIn(0.55f, 1f)
            val lit = lamp * (1 - 0.6f * roll) * (1 - 0.35f * back) * (0.92f + 0.12f * r.next())
            for (side in intArrayOf(-1, 1)) {
                val lx = cx + side * sw * 0.2f; val ly = y - rh * 0.1f; val rx = sw * 0.23f; val ry = rh * 0.78f * persp; val rot = side * 0.5f
                p.shader = RadialGradient(lx - side * sw * 0.04f - sw * 0.04f, ly - ry * 0.35f, ry * 1.05f,
                    intArrayOf(argb(floatArrayOf(255 * lit, 238 * lit, 204 * lit)), argb(floatArrayOf(230 * lit, 198 * lit, 152 * lit)), argb(floatArrayOf(116 * lit, 90 * lit, 62 * lit))),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                g.save(); g.rotate(Math.toDegrees(rot.toDouble()).toFloat(), lx, ly)
                oval.set(lx - rx, ly - ry, lx + rx, ly + ry); g.drawOval(oval, p)
                g.restore()
                p.shader = null
                st.color = argb(floatArrayOf(90 * lit, 70 * lit, 48 * lit), 0.35f); st.strokeWidth = U * 0.002f
                val cr = cos(rot); val sr = sin(rot)
                for (k in floatArrayOf(-0.35f, 0.25f)) g.drawLine(lx + cr * rx * k - sr * ry * 0.7f, ly + sr * rx * k - cr * ry * 0.7f + ry * 0.2f, lx + cr * rx * (k + 0.4f) + sr * ry * 0.5f, ly + sr * rx * (k + 0.4f) + ry * 0.4f, st)
                st.strokeWidth = U * 0.0022f
                for (k in 0 until 6) {
                    val a = r.next() * TAU; val d = ry * (0.8f + 0.3f * r.next())
                    val fx = lx + cos(a) * rx * 0.9f; val fy = ly + sin(a) * d
                    st.color = argb(floatArrayOf(255 * lit, 242 * lit, 216 * lit), 0.25f + 0.25f * r.next())
                    g.drawLine(fx, fy, fx + cos(a) * U * 0.006f, fy + sin(a) * U * 0.006f, st)
                }
            }
            col++
        }
    }
    // The far part softens into the room.
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    p.shader = LinearGradient(0f, top - rh, 0f, top + rh * 2.5f, rgba(0x2E1E18, 0.85f), rgba(0x2E1E18, 0f), Shader.TileMode.CLAMP)
    g.drawRect(0f, top - rh * 2, W, top + rh * 3, p)
    // Shadow under the rolled front edge.
    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
    p.shader = LinearGradient(0f, edge, 0f, edge + U * 0.08f, rgba(0x080503, 0.7f), rgba(0x080503, 0f), Shader.TileMode.CLAMP)
    g.drawRect(0f, edge - U * 0.01f, W, edge + U * 0.08f, p)
    return bmp
}
