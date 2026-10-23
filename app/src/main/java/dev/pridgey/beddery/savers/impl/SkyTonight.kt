package dev.pridgey.beddery.savers.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import dev.pridgey.beddery.savers.SoftDot
import dev.pridgey.beddery.savers.stamp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.Astronomy
import dev.pridgey.beddery.data.Sky
import dev.pridgey.beddery.savers.MoonRender
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.drawGrain
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatTimeWithAmPm
import dev.pridgey.beddery.savers.hash
import dev.pridgey.beddery.savers.rememberFrameTime
import dev.pridgey.beddery.savers.rememberGrainBrush
import dev.pridgey.beddery.savers.ridge
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

private const val RD = PI.toFloat() / 180f

/** Everything in the sky for one moment, as altitude/azimuth in degrees. */
private class SkySnapshot(
    val at: LocalDateTime,
    val tonight: Boolean,
    val starAlt: FloatArray, val starAz: FloatArray, val starMag: FloatArray,
    val faintAlt: FloatArray, val faintAz: FloatArray, val faintMag: FloatArray,
    val lines: List<Pair<Int, Int>>,
    val groups: List<Pair<String, IntArray>>,
    val planets: List<Triple<Sky.Planet, Float, Float>>,
    val moonAlt: Float, val moonAz: Float, val moonPhase: Double,
    val southern: Boolean,
    val milkyAlt: FloatArray, val milkyAz: FloatArray, val milkyGlow: FloatArray,
)

private val FaintField: List<Sky.Equatorial> by lazy {
    // A spread of fainter stars so the sky isn't only the famous ones. Uniform on the sphere.
    (0 until 1600).map { i ->
        val u = hash(i * 7 + 1)
        val v = hash(i * 13 + 5)
        Sky.Equatorial(u * 360.0, Math.toDegrees(asin(2.0 * v - 1.0)))
    }
}

/** Puffs of light scattered along the galactic plane, brightest toward the centre in Sagittarius. */
private val MilkyWay: List<Pair<Sky.Equatorial, Float>> by lazy {
    (0 until 420).map { i ->
        val l = hash(i * 3 + 7) * 360.0
        val spread = (hash(i * 5 + 2) - 0.5) * 2
        val b = spread * spread * spread * 14.0 + (hash(i * 11) - 0.5) * 4
        val glow = (0.35f + 0.65f * ((1 + kotlin.math.cos(Math.toRadians(l))) / 2).toFloat().pow(2f)) * (1f - kotlin.math.abs(spread.toFloat()) * 0.5f)
        Sky.galactic(l, b) to glow
    }
}

private fun snapshot(now: LocalDateTime, lat: Double, lon: Double, zone: ZoneId): SkySnapshot {
    // During the day, show tonight's sky at 10 PM instead.
    val nowInstant = now.atZone(zone).toInstant()
    val sunAlt = Sky.horizontal(Sky.sunPosition(nowInstant), Sky.siderealDeg(nowInstant, lon), lat).altDeg
    val tonight = sunAlt > -8
    val at = if (tonight) {
        val ten = now.toLocalDate().atTime(22, 0)
        if (now.hour < 4) now else ten
    } else now
    val instant = at.atZone(zone).toInstant()
    val lst = Sky.siderealDeg(instant, lon)
    fun hor(eq: Sky.Equatorial) = Sky.horizontal(eq, lst, lat)

    val stars = Sky.stars
    val sAlt = FloatArray(stars.size)
    val sAz = FloatArray(stars.size)
    stars.forEachIndexed { i, s ->
        val h = hor(Sky.Equatorial(s.raH * 15, s.dec))
        sAlt[i] = h.altDeg.toFloat()
        sAz[i] = h.azDeg.toFloat()
    }
    val fAlt = FloatArray(FaintField.size)
    val fAz = FloatArray(FaintField.size)
    val fMag = FloatArray(FaintField.size) { 3.6f + 2.2f * hash(it * 29).pow(0.5f) }
    FaintField.forEachIndexed { i, e ->
        val h = hor(e)
        fAlt[i] = h.altDeg.toFloat()
        fAz[i] = h.azDeg.toFloat()
    }
    val index = stars.withIndex().associate { it.value.name to it.index }
    val lines = Sky.constellations.flatMap { c -> c.lines.mapNotNull { (a, b) -> val i = index[a]; val j = index[b]; if (i != null && j != null) i to j else null } }
    val groups = Sky.constellations.map { c -> c.name to c.lines.flatMap { listOf(it.first, it.second) }.distinct().mapNotNull { index[it] }.toIntArray() }
    val planets = Sky.Planet.entries.map { p -> hor(Sky.planetPosition(p, instant)).let { Triple(p, it.altDeg.toFloat(), it.azDeg.toFloat()) } }
    val moon = hor(Sky.moonPosition(instant))
    val mAlt = FloatArray(MilkyWay.size)
    val mAz = FloatArray(MilkyWay.size)
    MilkyWay.forEachIndexed { i, (e, _) ->
        val h = hor(e)
        mAlt[i] = h.altDeg.toFloat()
        mAz[i] = h.azDeg.toFloat()
    }
    return SkySnapshot(
        at, tonight, sAlt, sAz, FloatArray(stars.size) { stars[it].mag.toFloat() }, fAlt, fAz, fMag, lines, groups, planets,
        moon.altDeg.toFloat(), moon.azDeg.toFloat(), Astronomy.moonPhase(instant), lat < 0,
        mAlt, mAz, FloatArray(MilkyWay.size) { MilkyWay[it].second },
    )
}

/** A stereographic view looking toward [azC], centred [alt0] degrees up. Natural star shapes, curved horizon. */
private class View(val azC: Float, val alt0: Float, val scale: Float, val cx: Float, val cy: Float) {
    private val sa0 = sin(alt0 * RD)
    private val ca0 = cos(alt0 * RD)

    fun project(alt: Float, az: Float): Offset? {
        val d = Sky.angleDiff(az.toDouble(), azC.toDouble()).toFloat() * RD
        val a = alt * RD
        val cosc = sa0 * sin(a) + ca0 * cos(a) * cos(d)
        if (cosc < -0.3f) return null
        val k = 2f / (1f + cosc)
        val x = k * cos(a) * sin(d)
        val y = k * (ca0 * sin(a) - sa0 * cos(a) * cos(d))
        return Offset(cx + x * scale, cy - y * scale)
    }
}

private fun planetColor(p: Sky.Planet) = when (p) {
    Sky.Planet.MERCURY -> Color(0xFFE8DCC8)
    Sky.Planet.VENUS -> Color(0xFFFFF6DE)
    Sky.Planet.MARS -> Color(0xFFFFA27A)
    Sky.Planet.JUPITER -> Color(0xFFFFEBC8)
    Sky.Planet.SATURN -> Color(0xFFF2D9A0)
}

private fun planetSize(p: Sky.Planet) = when (p) {
    Sky.Planet.VENUS -> 1.0f
    Sky.Planet.JUPITER -> 0.85f
    Sky.Planet.MARS, Sky.Planet.SATURN -> 0.62f
    Sky.Planet.MERCURY -> 0.5f
}

private val compass = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/** The real night sky above you: bright stars, constellations, the planets and the Moon, slowly panning round. */
@Composable
fun SkyTonightSaver(ctx: SaverContext) {
    val time = rememberFrameTime()
    val grain = rememberGrainBrush()
    val measurer = rememberTextMeasurer(64)
    val zone = remember { ZoneId.systemDefault() }
    val loc = ctx.settings.weatherLocation
    val (lat, lon) = remember(loc) { loc?.let { it.latitude to it.longitude } ?: Astronomy.guessLocation(zone) }
    // Recomputed once a minute: the sky moves a quarter of a degree in that time.
    val sky = remember(ctx.now.hour, ctx.now.minute, lat, lon) { snapshot(ctx.now, lat, lon, zone) }
    val moonImage = remember((sky.moonPhase * 100).toInt(), sky.southern) {
        MoonRender.render(160, sky.moonPhase, sky.southern).asImageBitmap()
    }
    val land = remember { ridge(181, 404, 0.55f) }
    val labels = remember { HashMap<String, TextLayoutResult>() }
    val placed = remember { ArrayList<Rect>() }
    val milkyTint = remember { ColorFilter.tint(Color(0xFFC8D4FF), BlendMode.Modulate) }
    val visible = Sky.Planet.entries.filter { p -> sky.planets.first { it.first == p }.second > 2f }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        val dens = LocalDensity.current
        val u = min(maxWidth, maxHeight)
        val clock = with(dens) { (u * 0.13f).toSp() }
        val body = with(dens) { (u * 0.036f).toSp() }

        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            val w = size.width
            val h = size.height
            val fov = if (landscape) 100f else 84f
            val alt0 = if (landscape) 34f else 38f
            val scale = (w / 2f) / (2f * tan(fov / 4f * RD))
            val facing = if (sky.southern) 0f else 180f
            // Pan slowly all the way round: about 25 minutes a turn. Thumbnails hold still.
            val azC = (facing + if (ctx.preview) 0f else t * 0.24f) % 360f
            val horizonY = h * if (landscape) 0.9f else 0.8f
            // Place the view so the horizon straight ahead sits at horizonY.
            val probe = View(azC, alt0, scale, w / 2, 0f).project(0f, azC)!!.y
            val view = View(azC, alt0, scale, w / 2, horizonY - probe)
            val unit = size.minDimension
            placed.clear()

            // Sky: darkest overhead, a faint glow of distant towns at the horizon.
            drawRect(Brush.verticalGradient(0f to Color(0xFF010208), 0.6f to Color(0xFF050A18), 0.92f to Color(0xFF0E1426), 1f to Color(0xFF1A1A24), startY = 0f, endY = horizonY))

            // The Milky Way: a soft, uneven river of light.
            for (i in sky.milkyAlt.indices) {
                if (sky.milkyAlt[i] < -4f) continue
                val p = view.project(sky.milkyAlt[i], sky.milkyAz[i]) ?: continue
                val ext = (1f - sky.milkyAlt[i] / 30f).coerceIn(0f, 1f) * 0.7f
                val d = scale * (0.1f + 0.08f * hash(i * 7))
                stamp(SoftDot, p, d, alpha = 0.075f * sky.milkyGlow[i] * (1f - ext), colorFilter = milkyTint)
            }

            // Faint stars.
            for (i in sky.faintAlt.indices) {
                if (sky.faintAlt[i] < 0f) continue
                val p = view.project(sky.faintAlt[i], sky.faintAz[i]) ?: continue
                if (p.x < -4 || p.x > w + 4 || p.y < -4 || p.y > h) continue
                val m = sky.faintMag[i]
                val ext = (1f - sky.faintAlt[i] / 25f).coerceIn(0f, 1f) * 0.6f
                val a = ((6.2f - m) / 3f).coerceIn(0.08f, 0.6f) * (1f - ext)
                drawCircle(Color(0xFFD8E2FF).copy(alpha = a), unit * 0.0016f * (6.4f - m) / 2f, p)
            }

            // Constellation figures, drawn faintly, with a small gap at each star.
            val proj = arrayOfNulls<Offset>(sky.starAlt.size)
            for (i in proj.indices) if (sky.starAlt[i] > -3f) proj[i] = view.project(sky.starAlt[i], sky.starAz[i])
            for ((a, b) in sky.lines) {
                val pa = proj[a] ?: continue
                val pb = proj[b] ?: continue
                if ((pa - pb).getDistance() > w * 0.6f) continue
                val dir = (pb - pa) / (pb - pa).getDistance().coerceAtLeast(1f)
                val gap = unit * 0.012f
                drawLine(Color(0xFF9DB4E8).copy(alpha = 0.2f), pa + dir * gap, pb - dir * gap, strokeWidth = density * 0.9f)
            }
            for ((name, members) in sky.groups) {
                val pts = members.toList().mapNotNull { proj[it] }
                if (pts.size < members.size || pts.isEmpty()) continue
                val c = Offset(pts.sumOf { it.x.toDouble() }.toFloat() / pts.size, pts.maxOf { it.y })
                if (c.x < 0 || c.x > w || c.y > horizonY - 10) continue
                val lay = labels.getOrPut("c:$name") {
                    measurer.measure(name.uppercase(), TextStyle(color = Color(0xFF9DB4E8), fontSize = 9.sp, letterSpacing = 1.6.sp))
                }
                drawText(lay, topLeft = Offset(c.x - lay.size.width / 2f, c.y + unit * 0.02f), alpha = 0.32f)
            }

            // Bright stars, sized and coloured by magnitude.
            for (i in proj.indices) {
                val p = proj[i] ?: continue
                if (sky.starAlt[i] < 0f) continue
                val m = sky.starMag[i]
                val tw = 0.85f + 0.15f * sin(t * (1.5f + hash(i) * 2f) + i)
                val ext = (1f - sky.starAlt[i] / 20f).coerceIn(0f, 1f) * 0.5f
                val a = ((5f - m) / 4f).coerceIn(0.25f, 1f) * tw * (1f - ext)
                val r = unit * 0.0022f * (4.6f - m).coerceIn(0.8f, 6f) / 1.6f
                if (m < 1.2f) drawCircle(Brush.radialGradient(listOf(Color(0xFFE6EEFF).copy(alpha = 0.25f * a), Color.Transparent), p, r * 5f), r * 5f, p, blendMode = BlendMode.Plus)
                drawCircle(Color(0xFFF2F5FF).copy(alpha = a), r, p)
            }

            // The Moon.
            if (sky.moonAlt > -2f) view.project(sky.moonAlt, sky.moonAz)?.let { p ->
                val mr = unit * 0.034f
                drawCircle(Brush.radialGradient(listOf(Color(0xFFDDE6FF).copy(alpha = 0.16f), Color.Transparent), p, mr * 6f), mr * 6f, p, blendMode = BlendMode.Plus)
                drawCircle(Color.Black, mr * 0.98f, p)
                placed += Rect(p - Offset(mr, mr), Size(mr * 2, mr * 2))
                drawImage(moonImage, dstOffset = IntOffset((p.x - mr).toInt(), (p.y - mr).toInt()), dstSize = IntSize((mr * 2).toInt(), (mr * 2).toInt()), filterQuality = FilterQuality.High)
                drawLabel(measurer, labels, placed, "Moon", p, mr * 1.4f, Color(0xFFE6ECFF), 0.6f)
            }

            // Planets: steady (they don't twinkle), with a gentle name.
            for ((planet, alt, az) in sky.planets) {
                if (alt < -1f) continue
                val p = view.project(alt, az) ?: continue
                val c = planetColor(planet)
                val r = unit * 0.006f * planetSize(planet)
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent), p, r * 6f), r * 6f, p, blendMode = BlendMode.Plus)
                drawCircle(c, r, p)
                drawLabel(measurer, labels, placed, planet.label, p, r * 3f, c, 0.75f)
            }

            // The land: a low, uneven horizon that turns with the view.
            drawGround(view, azC, fov, land, horizonY, h, measurer, labels)
            drawGrain(grain, 0.05f)
        }

        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.75f
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), Offset(size.minDimension * 0.18f, size.minDimension * 0.12f), r), r, Offset(size.minDimension * 0.18f, size.minDimension * 0.12f))
        }
        Column(Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = if (landscape) 20.dp else 56.dp)) {
            val tNow = ctx.now.toLocalTime()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatClock(tNow, ctx.use24h), color = Color.White.copy(alpha = 0.85f), fontSize = clock, lineHeight = clock, fontWeight = FontWeight.Light, style = TabularNumbers)
                if (!ctx.use24h) Text(amPm(tNow), color = Color.White.copy(alpha = 0.5f), fontSize = body, modifier = Modifier.offset(x = 5.dp, y = (-8).dp))
            }
            Text(
                if (sky.tonight) stringResource(R.string.sky_tonight_at, formatTimeWithAmPm(sky.at.toLocalTime(), ctx.use24h)) else stringResource(R.string.sky_now),
                color = Color.White.copy(alpha = 0.55f), fontSize = body,
            )
            if (visible.isNotEmpty()) {
                Text(
                    stringResource(R.string.sky_visible, visible.joinToString(", ") { it.label }),
                    color = Color(0xFFFFE2B0).copy(alpha = 0.6f), fontSize = body * 0.9f,
                )
            }
            if (loc == null) Text(stringResource(R.string.sky_estimated), color = Color.White.copy(alpha = 0.3f), fontSize = body * 0.8f, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Draws a name next to [at], trying below, above, right and left until it doesn't overlap another label. */
private fun DrawScope.drawLabel(
    measurer: TextMeasurer, cache: HashMap<String, TextLayoutResult>, placed: MutableList<Rect>,
    text: String, at: Offset, gap: Float, color: Color, alpha: Float,
) {
    val lay = cache.getOrPut("l:$text") { measurer.measure(text, TextStyle(color = color, fontSize = 11.sp)) }
    val lw = lay.size.width.toFloat()
    val lh = lay.size.height.toFloat()
    val candidates = listOf(
        Offset(at.x - lw / 2, at.y + gap), Offset(at.x - lw / 2, at.y - gap - lh),
        Offset(at.x + gap * 1.2f, at.y - lh / 2), Offset(at.x - gap * 1.2f - lw, at.y - lh / 2),
        Offset(at.x - lw / 2, at.y + gap + lh), Offset(at.x - lw / 2, at.y - gap - lh * 2),
    )
    val pad = lh * 0.3f
    val spot = candidates.firstOrNull { c -> placed.none { it.overlaps(Rect(c, Size(lw, lh)).inflate(pad)) } } ?: return
    placed += Rect(spot, Size(lw, lh))
    drawText(lay, topLeft = spot, alpha = alpha)
}

private fun DrawScope.drawGround(view: View, azC: Float, fov: Float, land: FloatArray, horizonY: Float, h: Float, measurer: TextMeasurer, cache: HashMap<String, TextLayoutResult>) {
    val path = Path()
    val half = fov / 2f + 12f
    var first = true
    var a = -half
    while (a <= half) {
        val az = ((azC + a) % 360f + 360f) % 360f
        // Hills and trees: a couple of degrees high, varying around the horizon.
        val k = (az / 2f).toInt().coerceIn(0, land.size - 1)
        val bump = 0.4f + land[k] * 2.2f + if (hash(k * 3) > 0.7f) 0.8f else 0f
        val p = view.project(bump, az) ?: Offset(0f, horizonY)
        if (first) {
            path.moveTo(p.x, h)
            first = false
        }
        path.lineTo(p.x, p.y)
        a += 1f
    }
    path.lineTo(size.width + 50f, h)
    path.close()
    drawPath(path, Color(0xFF040508))
    // Compass points on the ground below the horizon.
    for (i in compass.indices) {
        val az = i * 45f
        if (kotlin.math.abs(Sky.angleDiff(az.toDouble(), azC.toDouble())) > fov / 2f) continue
        val p = view.project(-4f, az) ?: continue
        val lay = cache.getOrPut("d:${compass[i]}") {
            measurer.measure(compass[i], TextStyle(color = Color(0xFF9DB4E8), fontSize = 10.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium))
        }
        drawText(lay, topLeft = Offset(p.x - lay.size.width / 2f, p.y), alpha = if (i % 2 == 0) 0.45f else 0.25f)
    }
}
