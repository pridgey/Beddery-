package dev.pridgey.beddery.savers

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.floor
import kotlin.random.Random

/**
 * Procedural textures generated once on the CPU and reused. Drawing a soft noise texture,
 * stretched and scrolled, gives savers organic detail (clouds, moon surface, nebulae) without
 * GPU shaders, so they look the same on every phone and in tests.
 */
object Textures {
    private val cache = HashMap<String, Bitmap>()

    /**
     * Seamlessly tiling fractal noise. White pixels whose alpha is the noise value, so the
     * texture can be tinted with a color filter. [period] is the number of cells across.
     */
    fun noise(size: Int = 256, period: Int = 4, octaves: Int = 5, seed: Int = 1, contrast: Float = 1f): Bitmap =
        synchronized(cache) {
            cache.getOrPut("noise-$size-$period-$octaves-$seed-$contrast") { buildNoise(size, period, octaves, seed, contrast) }
        }

    /** Fine per-pixel grain, used to stop dark gradients from showing bands. */
    fun grain(size: Int = 128): Bitmap = synchronized(cache) {
        cache.getOrPut("grain-$size") {
            val r = Random(77)
            val px = IntArray(size * size) {
                val v = r.nextInt(256)
                (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
            Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
        }
    }

    private fun buildNoise(size: Int, period: Int, octaves: Int, seed: Int, contrast: Float): Bitmap {
        val values = FloatArray(size * size)
        var amp = 1f
        var p = period
        for (o in 0 until octaves) {
            val lattice = Random(seed * 131 + o).let { r -> FloatArray(p * p) { r.nextFloat() } }
            for (y in 0 until size) {
                val fy = y.toFloat() / size * p
                val y0 = floor(fy).toInt()
                val ty = smooth(fy - y0)
                val r0 = (y0 % p) * p
                val r1 = ((y0 + 1) % p) * p
                for (x in 0 until size) {
                    val fx = x.toFloat() / size * p
                    val x0 = floor(fx).toInt()
                    val tx = smooth(fx - x0)
                    val c0 = x0 % p
                    val c1 = (x0 + 1) % p
                    val a = lattice[r0 + c0] + (lattice[r0 + c1] - lattice[r0 + c0]) * tx
                    val b = lattice[r1 + c0] + (lattice[r1 + c1] - lattice[r1 + c0]) * tx
                    values[y * size + x] += (a + (b - a) * ty) * amp
                }
            }
            amp *= 0.5f
            p *= 2
            if (p > size) break
        }
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (v in values) {
            if (v < lo) lo = v
            if (v > hi) hi = v
        }
        val span = (hi - lo).coerceAtLeast(1e-6f)
        val px = IntArray(values.size) { i ->
            var v = (values[i] - lo) / span
            if (contrast != 1f) v = ((v - 0.5f) * contrast + 0.5f).coerceIn(0f, 1f)
            ((v * 255).toInt() shl 24) or 0xFFFFFF
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun smooth(t: Float) = t * t * t * (t * (t * 6 - 15) + 10)
}

/** A tiling texture brush that can be scaled, rotated and scrolled every frame. */
class TextureBrush(bitmap: Bitmap) {
    private val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val matrix = Matrix()
    val brush = ShaderBrush(shader)
    val width = bitmap.width

    /** Places the texture so one tile covers [tileW] × [tileH] pixels, shifted by ([dx], [dy]). */
    fun place(tileW: Float, tileH: Float, dx: Float = 0f, dy: Float = 0f, degrees: Float = 0f, pivotX: Float = 0f, pivotY: Float = 0f): ShaderBrush {
        matrix.reset()
        matrix.setScale(tileW / width, tileH / width)
        matrix.postTranslate(dx, dy)
        if (degrees != 0f) matrix.postRotate(degrees, pivotX, pivotY)
        shader.setLocalMatrix(matrix)
        return brush
    }
}

@Composable
fun rememberNoiseBrush(size: Int = 256, period: Int = 4, octaves: Int = 5, seed: Int = 1, contrast: Float = 1f): TextureBrush =
    remember(size, period, octaves, seed, contrast) { TextureBrush(Textures.noise(size, period, octaves, seed, contrast)) }

@Composable
fun rememberGrainBrush(): TextureBrush = remember { TextureBrush(Textures.grain()) }

/** Subtle film grain. Overlay blending keeps pure black black (kind to OLED) and hides gradient banding. */
fun DrawScope.drawGrain(grain: TextureBrush, strength: Float = 0.07f) {
    drawRect(grain.place(grain.width.toFloat(), grain.width.toFloat()), alpha = strength, blendMode = BlendMode.Overlay)
}

/** Draws [block] into an offscreen layer, so masking blend modes (DstIn, SrcAtop) only affect it. */
inline fun DrawScope.layer(bounds: androidx.compose.ui.geometry.Rect = androidx.compose.ui.geometry.Rect(androidx.compose.ui.geometry.Offset.Zero, size), block: DrawScope.() -> Unit) {
    drawContext.canvas.saveLayer(bounds, androidx.compose.ui.graphics.Paint())
    block()
    drawContext.canvas.restore()
}

/**
 * One vertical gradient reused for hundreds of lines per frame (aurora rays, light shafts):
 * moved into place with a matrix instead of allocating a new gradient each time.
 */
class VerticalStrip(colors: List<androidx.compose.ui.graphics.Color>, stops: List<Float>) {
    private val shader = android.graphics.LinearGradient(
        0f, 0f, 0f, 1f,
        IntArray(colors.size) { colors[it].toArgb() }, stops.toFloatArray(), Shader.TileMode.CLAMP,
    )
    private val matrix = Matrix()
    private val brush = ShaderBrush(shader)

    fun place(top: Float, height: Float): ShaderBrush {
        matrix.setScale(1f, height.coerceAtLeast(1f))
        matrix.postTranslate(0f, top)
        shader.setLocalMatrix(matrix)
        return brush
    }
}


/**
 * Renders a small image once (a raindrop, a soft dot) that can then be stamped hundreds of
 * times per frame with drawImage, which is much cheaper than building gradients each time.
 */
fun sprite(size: Int, block: DrawScope.() -> Unit): androidx.compose.ui.graphics.ImageBitmap {
    val image = androidx.compose.ui.graphics.ImageBitmap(size, size)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(image), androidx.compose.ui.geometry.Size(size.toFloat(), size.toFloat()), block,
    )
    return image
}

/** Stamps [image] centered at [center] with the given width and height. */
fun DrawScope.stamp(image: androidx.compose.ui.graphics.ImageBitmap, center: androidx.compose.ui.geometry.Offset, w: Float, h: Float = w, alpha: Float = 1f, colorFilter: androidx.compose.ui.graphics.ColorFilter? = null, blendMode: BlendMode = BlendMode.SrcOver) {
    if (w < 0.5f || h < 0.5f) return
    drawImage(
        image,
        dstOffset = androidx.compose.ui.unit.IntOffset((center.x - w / 2).toInt(), (center.y - h / 2).toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
        alpha = alpha.coerceIn(0f, 1f), colorFilter = colorFilter, blendMode = blendMode,
    )
}

/** A soft white dot fading to transparent: tint it to make glows, bokeh, snow and sparks. */
val SoftDot: androidx.compose.ui.graphics.ImageBitmap by lazy {
    sprite(64) {
        drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(
                0f to androidx.compose.ui.graphics.Color.White, 0.25f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                0.6f to androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f), 1f to androidx.compose.ui.graphics.Color.Transparent,
                center = center, radius = 32f,
            ),
            32f, center,
        )
    }
}
