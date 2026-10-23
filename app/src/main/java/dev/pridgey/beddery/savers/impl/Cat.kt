package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import dev.pridgey.beddery.savers.Cooldown
import dev.pridgey.beddery.savers.OnSaverTap
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.TextureBrush
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.noise1
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.rememberNoiseBrush
import dev.pridgey.beddery.savers.smoothstep
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

private val FurLight = Color(0xFFF4B677)
private val Fur = Color(0xFFD9853F)
private val FurDark = Color(0xFF7A3C16)
private val Stripe = Color(0xFF9A4A1C)
private val Cream = Color(0xFFF7DDB8)
private val EarPink = Color(0xFFE4A08E)
private val Line = Color(0xFF3A1A0A)

/** Maps the cat's own coordinates (about -1.5..1.5) onto the screen. */
private class Frame(val cx: Float, val cy: Float, val s: Float) {
    fun p(x: Float, y: Float) = Offset(cx + x * s, cy + y * s)
    fun Path.m(x: Float, y: Float) = moveTo(cx + x * s, cy + y * s)
    fun Path.l(x: Float, y: Float) = lineTo(cx + x * s, cy + y * s)
    fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) =
        cubicTo(cx + x1 * s, cy + y1 * s, cx + x2 * s, cy + y2 * s, cx + x * s, cy + y * s)
    fun Path.q(x1: Float, y1: Float, x: Float, y: Float) = quadraticTo(cx + x1 * s, cy + y1 * s, cx + x * s, cy + y * s)
}

/** A ginger tabby curled up asleep in a round bed, breathing slowly. Tap and an ear might twitch. */
@Composable
fun SleepingCatSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val grain = rememberGrainBrush()
    val fur = rememberNoiseBrush(128, 16, 3, seed = 88, contrast = 1.4f)
    val knit = rememberNoiseBrush(128, 32, 2, seed = 90, contrast = 2f)
    val twitchAt = remember { floatArrayOf(-100f, 0f) }
    val cooldown = remember { Cooldown(4_000) }
    OnSaverTap { if (cooldown.tryAcquire()) twitchAt[0] = time.value }

    Canvas(Modifier.fillMaxSize()) {
        val t = time.value
        val w = size.width
        val h = size.height
        val landscape = w > h
        val s = if (landscape) h * 0.28f else w * 0.29f
        val f = Frame(w / 2, h * if (landscape) 0.31f else 0.4f, s)
        // An ear twitches by itself now and then too.
        if (t - twitchAt[0] > 50f + hash((t / 50f).toInt()) * 40f) twitchAt[0] = t
        val since = t - twitchAt[0]
        val twitch = if (since in 0f..1.4f) sin(since * 28f) * exp(-since * 3.5f) else 0f
        // Slow breathing: in for a little under two seconds, out for a little more.
        val cycle = (t % 4.6f) / 4.6f
        val breath = if (cycle < 0.42f) smoothstep(0f, 0.42f, cycle) else 1f - smoothstep(0.42f, 1f, cycle)

        drawRoom(f, w, h)
        drawBedBack(f, knit)
        drawCat(f, t, breath, twitch, fur)
        drawBedFront(f, knit)
        drawZs(f, t)
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(w / 2, h * 0.42f), size.maxDimension * 0.7f))
        drawGrain(grain, 0.07f)
    }
}

private fun DrawScope.drawRoom(f: Frame, w: Float, h: Float) {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF120B0A), Color(0xFF1A100C), Color(0xFF0C0706)), 0f, h))
    // A warm lamp somewhere up and to the left.
    val lamp = Offset(w * 0.1f, f.cy - f.s * 2.2f)
    val r = size.maxDimension * 0.9f
    drawCircle(Brush.radialGradient(listOf(Color(0xFF6A3A1C).copy(alpha = 0.55f), Color(0xFF3A1E0E).copy(alpha = 0.25f), Color.Transparent), lamp, r), r, lamp)
    // A rug under the bed.
    val rug = f.p(0f, 0.95f)
    drawOval(Brush.radialGradient(listOf(Color(0xFF2A1A16), Color(0xFF140C0A).copy(alpha = 0f)), rug, f.s * 2.6f), Offset(rug.x - f.s * 2.6f, rug.y - f.s * 0.55f), Size(f.s * 5.2f, f.s * 1.1f))
    // Shadow of the bed.
    drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent), f.p(0.1f, 0.98f), f.s * 1.7f), Offset(f.cx - f.s * 1.65f, f.cy + f.s * 0.7f), Size(f.s * 3.5f, f.s * 0.6f))
}

private val BedOuter = Color(0xFF2E4752)
private val BedLight = Color(0xFF5A7C88)

private fun DrawScope.drawBedBack(f: Frame, knit: TextureBrush) = with(f) {
    // The back of the rim and the cushion inside.
    val outer = Rect(p(-1.6f, -0.22f), p(1.6f, 1.08f))
    drawOval(Brush.verticalGradient(listOf(BedLight, BedOuter, Color(0xFF16242A)), outer.top, outer.bottom), outer.topLeft, outer.size)
    // The inside wall of the far rim, in shadow.
    drawOval(Brush.verticalGradient(listOf(Color(0xFF1E323A), Color(0xFF101C22)), cy - 0.05f * s, cy + 0.5f * s), p(-1.38f, -0.06f), Size(s * 2.76f, s * 0.95f))
    drawOval(knit.place(s * 0.35f, s * 0.35f), outer.topLeft, outer.size, alpha = 0.12f, colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn))
    val cushion = Rect(p(-1.2f, 0.18f), p(1.2f, 0.86f))
    drawOval(Brush.radialGradient(listOf(Color(0xFF7A6A5E), Color(0xFF3A3029)), cushion.center - Offset(0f, s * 0.1f), cushion.width * 0.6f), cushion.topLeft, cushion.size)
    // Shadow the cat casts into the cushion.
    drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), p(0.0f, 0.6f), s * 1.2f), p(-1.15f, 0.36f), Size(s * 2.3f, s * 0.48f))
}

private fun DrawScope.drawBedFront(f: Frame, knit: TextureBrush) = with(f) {
    // The front lip of the bed: the lower half of a thick ring, in front of the cat.
    val lip = Path().apply {
        val outer = Rect(p(-1.6f, -0.22f), p(1.6f, 1.08f))
        val inner = Rect(p(-1.32f, 0.08f), p(1.32f, 0.78f))
        arcTo(outer, 180f, -180f, false)
        arcTo(inner, 0f, 180f, false)
        close()
    }
    // A rounded, padded roll: lit along its top, falling into shadow underneath.
    drawPath(lip, Brush.verticalGradient(0f to Color(0xFF6E929E), 0.35f to BedLight, 0.7f to BedOuter, 1f to Color(0xFF101C22), startY = cy + 0.6f * s, endY = cy + 1.08f * s))
    clipPath(lip) {
        drawRect(knit.place(s * 0.3f, s * 0.22f), Offset(cx - s * 2, cy), Size(s * 4, s * 1.2f), alpha = 0.18f, colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn))
        // Lamp light along the top of the rim.
        drawOval(Brush.radialGradient(listOf(Color(0xFFFFC890).copy(alpha = 0.25f), Color.Transparent), p(-0.6f, 0.62f), s * 1.0f), p(-1.6f, 0.45f), Size(s * 2.4f, s * 0.4f))
    }
    // A soft seam along the rim.
    drawArc(Color(0xFFB8D2DC).copy(alpha = 0.3f), 25f, 130f, false, p(-1.32f, 0.08f), Size(s * 2.64f, s * 0.7f), style = Stroke(s * 0.014f))
}

private fun DrawScope.drawCat(f: Frame, t: Float, breath: Float, twitch: Float, fur: TextureBrush) = with(f) {
    val pivot = p(0f, 0.6f)
    // Body (breathing), tail and haunch.
    withTransform({ scale(1f + 0.012f * breath, 1f + 0.035f * breath, pivot) }) {
        val body = Path().apply {
            m(-1.06f, 0.4f)
            c(-1.16f, -0.22f, -0.56f, -0.7f, 0.04f, -0.64f)
            c(0.56f, -0.6f, 0.98f, -0.32f, 1.02f, 0.06f)
            c(1.04f, 0.36f, 0.82f, 0.58f, 0.4f, 0.62f)
            l(-0.62f, 0.62f)
            c(-0.92f, 0.62f, -1.04f, 0.54f, -1.06f, 0.4f)
            close()
        }
        drawPath(body, Brush.radialGradient(0f to FurLight, 0.45f to Fur, 1f to FurDark, center = p(-0.35f, -0.45f), radius = s * 1.7f))
        clipPath(body) {
            // Tabby stripes running down the flanks from the spine, uneven and a little wavy.
            for (k in 0 until 13) {
                val a = (-176f + k * 12.5f + 4f * sin(k * 1.7f)) * PI.toFloat() / 180f
                val inner = 0.62f + 0.14f * hash(k * 3)
                val bend = 0.07f * sin(k * 2.3f)
                val path = Path().apply {
                    m(0.02f + cos(a) * 1.15f, 0.3f + sin(a) * 1.05f)
                    q(0.02f + cos(a + 0.05f) * 0.9f + bend, 0.3f + sin(a + 0.05f) * 0.85f, 0.02f + cos(a - 0.02f) * inner, 0.3f + sin(a - 0.02f) * inner * 0.92f)
                }
                drawPath(path, Stripe.copy(alpha = 0.5f), style = Stroke(s * (0.05f + 0.025f * hash(k)), cap = StrokeCap.Round))
            }
            // A darker line along the spine.
            val spineLine = Path().apply {
                m(-0.98f, 0.12f)
                c(-0.95f, -0.42f, -0.4f, -0.64f, 0.1f, -0.6f)
                c(0.5f, -0.57f, 0.85f, -0.36f, 0.96f, -0.1f)
            }
            drawPath(spineLine, Stripe.copy(alpha = 0.3f), style = Stroke(s * 0.12f, cap = StrokeCap.Round))
            // The hind leg, folded: a soft rounded haunch.
            drawCircle(Brush.radialGradient(listOf(FurLight.copy(alpha = 0.35f), Color.Transparent), p(-0.62f, 0.12f), s * 0.42f), s * 0.42f, p(-0.62f, 0.12f))
            // Fur texture and shading toward the bed.
            drawRect(fur.place(s * 0.25f, s * 0.6f), p(-1.2f, -0.8f), Size(s * 2.4f, s * 1.5f), alpha = 0.16f, colorFilter = ColorFilter.tint(FurDark, BlendMode.SrcIn))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)), cy + 0.1f * s, cy + 0.62f * s), p(-1.2f, 0.1f), Size(s * 2.4f, s * 0.6f))
        }
        // Lamp light catching the fur along the back.
        clipPath(body) {
            drawPath(body, Brush.linearGradient(listOf(Color(0xFFFFD9A8).copy(alpha = 0.55f), Color.Transparent), p(-0.9f, -0.6f), p(-0.2f, 0.1f)), style = Stroke(s * 0.05f))
        }
    }

    // Tail: wraps round the front toward the face, the tip curling gently.
    val sway = sin(t * 0.45f) * 0.04f + noise1(t * 0.3f, 7) * 0.03f
    val spine = listOf(p(-0.9f, 0.5f), p(-0.7f, 0.62f), p(-0.25f, 0.68f), p(0.15f, 0.66f), p(0.42f + sway, 0.55f - sway))
    drawTail(spine, s, f)

    // Front paws tucked under the chin.
    val lift = -0.012f * breath
    for (x in listOf(0.3f, 0.6f)) {
        val c = p(x, 0.42f + lift)
        drawOval(Brush.radialGradient(listOf(Cream, Color(0xFFD9B48A)), c - Offset(0f, s * 0.03f), s * 0.14f), c - Offset(s * 0.13f, s * 0.07f), Size(s * 0.26f, s * 0.15f))
        for (k in -1..1) drawLine(Color(0xFF8A6A4A).copy(alpha = 0.5f), c + Offset(k * s * 0.05f, s * 0.02f), c + Offset(k * s * 0.05f, s * 0.07f), strokeWidth = s * 0.008f, cap = StrokeCap.Round)
    }

    drawHead(f, lift, twitch, fur)
}

/** The tail, as a tapering shape around a smooth curve through [spine]. */
private fun DrawScope.drawTail(spine: List<Offset>, s: Float, f: Frame) {
    val pts = ArrayList<Offset>()
    for (i in 0 until spine.size - 1) {
        val p0 = spine[(i - 1).coerceAtLeast(0)]
        val p1 = spine[i]
        val p2 = spine[i + 1]
        val p3 = spine[(i + 2).coerceAtMost(spine.size - 1)]
        for (k in 0 until 8) {
            val u = k / 8f
            val u2 = u * u
            val u3 = u2 * u
            pts += (p1 * 2f + (p2 - p0) * u + (p0 * 2f - p1 * 5f + p2 * 4f - p3) * u2 + (p1 * 3f - p0 - p2 * 3f + p3) * u3) * 0.5f
        }
    }
    pts += spine.last()
    val left = ArrayList<Offset>()
    val right = ArrayList<Offset>()
    for (i in pts.indices) {
        val a = pts[(i - 1).coerceAtLeast(0)]
        val b = pts[(i + 1).coerceAtMost(pts.size - 1)]
        val d = b - a
        val len = sqrt(d.x * d.x + d.y * d.y).coerceAtLeast(0.001f)
        val n = Offset(-d.y / len, d.x / len)
        val k = i / (pts.size - 1f)
        val width = s * (0.14f - 0.05f * k)
        left += pts[i] + n * width
        right += pts[i] - n * width
    }
    val tail = Path().apply {
        moveTo(left[0].x, left[0].y)
        for (q in left) lineTo(q.x, q.y)
        for (q in right.reversed()) lineTo(q.x, q.y)
        close()
    }
    val tailBrush = Brush.verticalGradient(listOf(FurLight, Fur, FurDark), f.cy + 0.45f * s, f.cy + 0.82f * s)
    // Rounded base, tucked against the body.
    drawCircle(tailBrush, s * 0.14f, pts.first())
    drawPath(tail, tailBrush)
    drawCircle(tailBrush, s * 0.09f, pts.last())
    clipPath(tail) {
        // Bands across the tail.
        for (k in 1 until 7) {
            val i = (k * pts.size / 7).coerceAtMost(pts.size - 1)
            val n = (left[i] - right[i])
            drawLine(Stripe.copy(alpha = 0.55f), right[i] - n * 0.1f, left[i] + n * 0.1f, strokeWidth = s * 0.055f)
        }
        drawPath(tail, Brush.verticalGradient(listOf(Color(0xFFFFE0B8).copy(alpha = 0.4f), Color.Transparent), f.cy + 0.45f * s, f.cy + 0.62f * s), style = Stroke(s * 0.04f))
    }
    drawCircle(Color(0xFF6A2E10).copy(alpha = 0.6f), s * 0.07f, pts.last())
}

private fun DrawScope.drawHead(f: Frame, lift: Float, twitch: Float, fur: TextureBrush) = with(f) {
    val dy = lift
    // The head's soft shadow on the body.
    drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), p(0.38f, 0.12f + dy), s * 0.55f), p(-0.15f, -0.3f + dy), Size(s * 1.1f, s * 0.85f))
    // Ears first, behind the head.
    fun ear(baseL: Offset, baseR: Offset, tip: Offset, angle: Float) {
        val pivot = (baseL + baseR) / 2f
        rotate(angle, pivot) {
            val outer = Path().apply {
                moveTo(baseL.x, baseL.y)
                quadraticTo(baseL.x + (tip.x - baseL.x) * 0.4f - s * 0.03f, baseL.y + (tip.y - baseL.y) * 0.6f, tip.x, tip.y)
                quadraticTo(baseR.x + (tip.x - baseR.x) * 0.4f + s * 0.03f, baseR.y + (tip.y - baseR.y) * 0.6f, baseR.x, baseR.y)
                close()
            }
            drawPath(outer, Brush.linearGradient(listOf(FurLight, Fur, FurDark), tip, pivot))
            val inset = 0.32f
            val il = baseL + (tip - baseL) * 0.14f + (baseR - baseL) * 0.18f
            val ir = baseR + (tip - baseR) * 0.14f - (baseR - baseL) * 0.18f
            val it = tip + (pivot - tip) * inset
            val inner = Path().apply {
                moveTo(il.x, il.y)
                quadraticTo((il.x + it.x) / 2, (il.y + it.y) / 2 + s * 0.01f, it.x, it.y)
                quadraticTo((ir.x + it.x) / 2, (ir.y + it.y) / 2 + s * 0.01f, ir.x, ir.y)
                close()
            }
            drawPath(inner, Brush.linearGradient(listOf(EarPink, Color(0xFF9A5A4A)), it, pivot))
            // Tufts of fur at the inner edge.
            drawLine(Cream.copy(alpha = 0.6f), il + (it - il) * 0.2f, il + (it - il) * 0.55f + Offset(s * 0.02f, 0f), strokeWidth = s * 0.008f, cap = StrokeCap.Round)
        }
    }
    ear(p(0.1f, -0.16f + dy), p(0.38f, -0.3f + dy), p(0.11f, -0.54f + dy), -6f + twitch * 18f)
    ear(p(0.54f, -0.3f + dy), p(0.82f, -0.14f + dy), p(0.88f, -0.5f + dy), 6f)

    val head = Path().apply {
        m(0.45f, -0.34f + dy)
        c(0.72f, -0.34f + dy, 0.9f, -0.18f + dy, 0.9f, 0.06f + dy)
        c(0.9f, 0.28f + dy, 0.7f, 0.38f + dy, 0.45f, 0.38f + dy)
        c(0.2f, 0.38f + dy, 0.0f, 0.28f + dy, 0.0f, 0.06f + dy)
        c(0.0f, -0.18f + dy, 0.18f, -0.34f + dy, 0.45f, -0.34f + dy)
        close()
    }
    drawPath(head, Brush.radialGradient(0f to FurLight, 0.55f to Fur, 1f to Color(0xFF9A4E20), center = p(0.32f, -0.12f + dy), radius = s * 0.62f))
    clipPath(head) {
        // The forehead "M" and cheek stripes.
        for ((x0, x1) in listOf(0.36f to 0.39f, 0.45f to 0.45f, 0.54f to 0.51f)) {
            drawLine(Stripe.copy(alpha = 0.6f), p(x0, -0.33f + dy), p(x1, -0.17f + dy), strokeWidth = s * 0.035f, cap = StrokeCap.Round)
        }
        for (side in listOf(-1f, 1f)) {
            for (k in 0..1) {
                val x = 0.45f + side * (0.37f - k * 0.02f)
                drawLine(Stripe.copy(alpha = 0.5f), p(x, 0.02f + k * 0.08f + dy), p(x - side * 0.12f, 0.04f + k * 0.08f + dy), strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            }
        }
        // Pale muzzle and chin.
        drawOval(Brush.radialGradient(listOf(Cream, Cream.copy(alpha = 0f)), p(0.45f, 0.2f + dy), s * 0.22f), p(0.24f, 0.08f + dy), Size(s * 0.42f, s * 0.3f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)), cy + (0.2f + dy) * s, cy + (0.38f + dy) * s), p(0f, 0.2f + dy), Size(s, s * 0.2f))
        drawRect(fur.place(s * 0.2f, s * 0.4f), p(0f, -0.4f + dy), Size(s, s * 0.8f), alpha = 0.1f, colorFilter = ColorFilter.tint(FurDark, BlendMode.SrcIn))
    }
    clipPath(head) {
        drawPath(head, Brush.linearGradient(listOf(Color(0xFFFFDDB0).copy(alpha = 0.45f), Color.Transparent), p(0.12f, -0.32f + dy), p(0.3f, -0.12f + dy)), style = Stroke(s * 0.05f))
    }

    // Eyes gently closed.
    for (x in listOf(0.29f, 0.61f)) {
        val eye = Path().apply {
            m(x - 0.08f, 0.03f + dy)
            q(x, 0.1f + dy, x + 0.08f, 0.03f + dy)
        }
        drawPath(eye, Line.copy(alpha = 0.85f), style = Stroke(s * 0.02f, cap = StrokeCap.Round))
        drawLine(Line.copy(alpha = 0.5f), p(x + (if (x < 0.45f) -0.08f else 0.08f), 0.03f + dy), p(x + (if (x < 0.45f) -0.11f else 0.11f), 0.015f + dy), strokeWidth = s * 0.012f, cap = StrokeCap.Round)
    }
    // Nose and mouth.
    val nose = Path().apply {
        m(0.405f, 0.135f + dy)
        l(0.495f, 0.135f + dy)
        q(0.47f, 0.18f + dy, 0.45f, 0.185f + dy)
        q(0.43f, 0.18f + dy, 0.405f, 0.135f + dy)
        close()
    }
    drawPath(nose, Brush.verticalGradient(listOf(Color(0xFFF0A898), Color(0xFFC0706A)), cy + (0.135f + dy) * s, cy + (0.185f + dy) * s))
    val mouth = Path().apply {
        m(0.45f, 0.185f + dy)
        l(0.45f, 0.215f + dy)
        m(0.45f, 0.215f + dy)
        q(0.42f, 0.24f + dy, 0.395f, 0.22f + dy)
        m(0.45f, 0.215f + dy)
        q(0.48f, 0.24f + dy, 0.505f, 0.22f + dy)
    }
    drawPath(mouth, Line.copy(alpha = 0.6f), style = Stroke(s * 0.012f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    // Whiskers.
    for (side in listOf(-1f, 1f)) {
        for (k in 0..2) {
            val from = p(0.45f + side * 0.12f, 0.19f + k * 0.02f + dy)
            val to = p(0.45f + side * (0.52f + 0.03f * k), 0.12f + k * 0.07f + dy)
            val ctrl = p(0.45f + side * 0.34f, 0.15f + k * 0.04f + dy)
            val wsk = Path().apply {
                moveTo(from.x, from.y)
                quadraticTo(ctrl.x, ctrl.y, to.x, to.y)
            }
            drawPath(wsk, Color.White.copy(alpha = 0.45f), style = Stroke(s * 0.006f, cap = StrokeCap.Round))
        }
    }
}

/** A few small "z"s drifting up from the sleeper. */
private fun DrawScope.drawZs(f: Frame, t: Float) {
    for (k in 0 until 3) {
        val life = 7.5f
        val age = (t + k * life / 3f) % life
        val u = age / life
        val a = sin(u * PI.toFloat()) * 0.26f
        val c = f.p(0.95f + u * 0.45f + sin(u * 5f + k) * 0.06f, -0.55f - u * 0.9f)
        val z = f.s * (0.05f + 0.04f * u)
        val path = Path().apply {
            moveTo(c.x - z, c.y - z)
            lineTo(c.x + z, c.y - z)
            lineTo(c.x - z, c.y + z)
            lineTo(c.x + z, c.y + z)
        }
        drawPath(path, Color(0xFFFFE2C0).copy(alpha = a), style = Stroke(f.s * 0.018f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
