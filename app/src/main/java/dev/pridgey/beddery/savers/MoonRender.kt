package dev.pridgey.beddery.savers

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Renders the moon as a lit sphere, pixel by pixel: the real seas (maria) in their real places,
 * a few bright craters, and sunlight falling at the right angle for the phase, so the terminator
 * curves and softens exactly as it does in the sky. Faint earthshine lights the dark side.
 */
object MoonRender {
    // Seas, as seen from the northern hemisphere: x (east is right), y (north is up), radius, darkness.
    private val Maria = listOf(
        floatArrayOf(-0.28f, 0.42f, 0.27f, 0.42f), // Imbrium
        floatArrayOf(0.2f, 0.4f, 0.16f, 0.4f), // Serenitatis
        floatArrayOf(0.36f, 0.12f, 0.21f, 0.4f), // Tranquillitatis
        floatArrayOf(0.7f, 0.3f, 0.12f, 0.42f), // Crisium
        floatArrayOf(0.58f, -0.14f, 0.15f, 0.34f), // Fecunditatis
        floatArrayOf(0.36f, -0.28f, 0.1f, 0.3f), // Nectaris
        floatArrayOf(-0.62f, 0.05f, 0.3f, 0.36f), // Procellarum
        floatArrayOf(-0.5f, 0.32f, 0.22f, 0.3f), // Procellarum, north
        floatArrayOf(-0.16f, -0.36f, 0.17f, 0.3f), // Nubium
        floatArrayOf(-0.48f, -0.38f, 0.1f, 0.34f), // Humorum
        floatArrayOf(0.02f, 0.26f, 0.08f, 0.28f), // Vaporum
        floatArrayOf(-0.1f, 0.72f, 0.1f, 0.25f), // Frigoris
        floatArrayOf(0.22f, 0.7f, 0.08f, 0.22f), // Frigoris, east
    )

    // Bright young craters: x, y, radius, brightness.
    private val Craters = listOf(
        floatArrayOf(-0.1f, -0.72f, 0.035f, 0.35f), // Tycho
        floatArrayOf(-0.33f, 0.14f, 0.03f, 0.25f), // Copernicus
        floatArrayOf(-0.7f, 0.15f, 0.02f, 0.3f), // Aristarchus
        floatArrayOf(-0.55f, -0.02f, 0.02f, 0.18f), // Kepler
    )

    /**
     * @param phase 0 new … 0.5 full … 1 new.
     * @param southern draw as seen from the southern hemisphere (upside down).
     */
    fun render(size: Int, phase: Double, southern: Boolean): Bitmap {
        val px = IntArray(size * size)
        val a = (2 * PI * phase).toFloat()
        // Sunlight direction: from behind at new moon, from the front at full, from the right while waxing.
        val lx = sin(a)
        val lz = -cos(a)
        val half = size / 2f
        for (j in 0 until size) {
            for (i in 0 until size) {
                var x = (i + 0.5f - half) / half
                var y = -(j + 0.5f - half) / half
                val r2 = x * x + y * y
                if (r2 > 1f) continue
                val z = sqrt(1f - r2)
                // Seen from the south, the whole moon (and its lit side) is turned upside down.
                if (southern) { x = -x; y = -y }
                val ndl = x * lx + z * lz
                var albedo = 0.78f + fbm2(x * 5f, y * 5f, 5, 3) * 0.1f
                for (m in Maria) {
                    val dx = x - m[0]
                    val dy = y - m[1]
                    val d2 = (dx * dx + dy * dy) / (m[2] * m[2])
                    val edge = 1f + 0.45f * fbm2(x * 7f, y * 7f, 3, 11)
                    albedo -= m[3] * 0.62f * exp(-d2 / (edge * edge) * 1.4f)
                }
                for (c in Craters) {
                    val dx = x - c[0]
                    val dy = y - c[1]
                    val d = sqrt(dx * dx + dy * dy) / c[2]
                    albedo += c[3] * 0.45f * exp(-d * d * 2f)
                    // Faint rays from the big ones.
                    if (c[3] > 0.3f && d < 14f) {
                        val ang = kotlin.math.atan2(dy, dx)
                        val ray = (0.5f + 0.5f * sin(ang * 13f + 1f)).let { it * it * it * it }
                        albedo += 0.025f * ray * exp(-d * 0.2f)
                    }
                }
                albedo = albedo.coerceIn(0.2f, 1.15f)
                val light = smoothstep(-0.04f, 0.16f, ndl) * (0.88f + 0.12f * ndl.coerceAtLeast(0f))
                val shade = 0.035f // earthshine
                val rC = albedo * (light * 0.94f + shade * 0.75f)
                val gC = albedo * (light * 0.91f + shade * 0.82f)
                val bC = albedo * (light * 0.85f + shade * 1f)
                val alpha = ((1f - sqrt(r2)) * half * 1.5f).coerceIn(0f, 1f)
                px[j * size + i] = argb(alpha, rC, gC, bC)
            }
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun argb(a: Float, r: Float, g: Float, b: Float): Int =
        ((a * 255).toInt() shl 24) or ((r.coerceIn(0f, 1f) * 255).toInt() shl 16) or
            ((g.coerceIn(0f, 1f) * 255).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255).toInt()
}
