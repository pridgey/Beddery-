package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.media.Art
import dev.pridgey.beddery.media.NowPlaying
import dev.pridgey.beddery.savers.SaverContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private val Paper = Color(0xFFF2E8D5)
private val Blue = Color(0xFF2343B0)
private val Pink = Color(0xFFFF4F8B)
private val Stub = Color(0xFFF9DCE2)

/** A two-colour gig poster: the cover printed in blue and pink dots, the controls on the ticket stub. */
@Composable
internal fun PosterStyle(ctx: SaverContext, track: NowPlaying, transport: Transport) {
    val pos = rememberPositionMs(track)
    val p = track.fraction(pos)
    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Paper)
            drawRect(ShaderBrush(ImageShader(Grain.tile, TileMode.Repeated, TileMode.Repeated)), alpha = 0.22f, blendMode = BlendMode.Multiply)
        },
    ) {
        val width = maxWidth
        if (maxWidth > maxHeight) {
            val art = maxHeight - 48.dp
            Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                PrintedArt(track.art, art, overlap = 0.dp)
                Spacer(Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    PosterHeader(track)
                    Spacer(Modifier.height(10.dp))
                    PosterTitle(track, width - art - 76.dp, 80f)
                    PosterDetails(ctx, track, pos, p)
                    Spacer(Modifier.weight(1f))
                    PosterStub(track, transport)
                }
            }
        } else {
            val art = min(maxWidth - 70.dp, maxHeight * 0.4f)
            Column(Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 22.dp)) {
                PosterHeader(track)
                Spacer(Modifier.height(14.dp))
                PrintedArt(track.art, art, overlap = 40.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
                PosterTitle(track, width - 44.dp, 104f)
                PosterDetails(ctx, track, pos, p)
                Spacer(Modifier.weight(1f))
                PosterStub(track, transport)
            }
        }
    }
}

@Composable
private fun PosterHeader(track: NowPlaying) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("BEDDERY HALL PRESENTS", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.4.sp)
            Text(track.appName.uppercase(), color = Pink, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.4.sp, maxLines = 1)
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp).background(Blue))
    }
}

/** The halftone, made off the main thread whenever the cover changes. [overlap] lets the title print over its foot. */
@Composable
private fun PrintedArt(art: Art?, size: Dp, overlap: Dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    var printed by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    LaunchedEffect(art?.key, px) {
        val a = art ?: return@LaunchedEffect
        val key = "${a.key}@$px"
        if (printed?.first == key) return@LaunchedEffect
        printed = key to withContext(Dispatchers.Default) { Halftone.print(a.image, px) }
    }
    Box(
        modifier
            .layout { m, c ->
                val placeable = m.measure(c)
                layout(placeable.width, placeable.height - overlap.roundToPx()) { placeable.place(0, 0) }
            }
            .size(size),
    ) {
        printed?.second?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
        // "One night only" sticker, overprinted.
        Box(Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = 18.dp).size(size * 0.23f).rotate(12f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawCircle(Pink) }
            Text("ONE\nNIGHT\nONLY", color = Blue, fontFamily = Anton, fontSize = (size.value * 0.047f).sp, lineHeight = 1.0.em, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PosterTitle(track: NowPlaying, width: Dp, maxSp: Float) {
    // Big as the longest word allows.
    val longest = track.title.split(' ').maxOf { it.length }.coerceAtLeast(1)
    val size = min(maxSp, width.value / (longest * 0.47f))
    Text(
        track.title.uppercase(), color = Blue, fontFamily = Anton, fontSize = size.sp, lineHeight = 0.88.em, letterSpacing = 0.5.sp,
        maxLines = 2, overflow = TextOverflow.Ellipsis,
    )
    track.artist?.let {
        Text(it.uppercase(), color = Pink, fontFamily = Anton, fontSize = 28.sp, lineHeight = 1.em, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PosterDetails(ctx: SaverContext, track: NowPlaying, pos: Long, p: Float) {
    val (time, ampm) = clockParts(ctx)
    Column(Modifier.padding(top = 14.dp)) {
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(Blue))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Detail("TONIGHT", time + (ampm?.let { " $it" } ?: ""), Modifier.weight(1f))
            Detail("ALBUM", track.album?.uppercase() ?: "—", Modifier.weight(1f))
            val set = if (track.durationMs > 0) formatTrackTime(pos) + " / " + formatTrackTime(track.durationMs) else formatTrackTime(pos)
            Detail("SET TIME", set, Modifier.weight(1f), TextAlign.End)
        }
        if (track.durationMs > 0) {
            // Quiet blocks: the song so far in blue, the moment now in pink.
            val blink = rememberInfiniteTransition(label = "blink")
            val now by blink.animateFloat(0.4f, 0.75f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "now")
            Canvas(Modifier.padding(top = 12.dp).fillMaxWidth().height(6.dp)) {
                val n = 24
                val gap = 3.dp.toPx()
                val w = (size.width - gap * (n - 1)) / n
                val lit = (p * n).toInt().coerceAtMost(n - 1)
                for (i in 0 until n) {
                    val x = i * (w + gap)
                    when {
                        i < lit -> drawRect(Blue.copy(alpha = 0.6f), Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                        i == lit -> drawRect(Pink.copy(alpha = if (track.playing) now else 0.6f), Offset(x, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                        else -> drawRect(Blue.copy(alpha = 0.3f), Offset(x + 0.5f, 0.5f), androidx.compose.ui.geometry.Size(w - 1f, size.height - 1f), style = Stroke(1.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Column(modifier) {
        Text(label, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, textAlign = align, modifier = Modifier.fillMaxWidth())
        Text(value, color = Blue, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp, textAlign = align, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
    }
}

/** The tear-off stub, holding the buttons. */
@Composable
private fun PosterStub(track: NowPlaying, transport: Transport) {
    Box(
        Modifier
            .fillMaxWidth()
            .clipToBounds()
            .drawBehind {
                drawRect(Stub)
                val notch = 11.dp.toPx()
                drawCircle(Paper, notch, Offset(0f, 0f))
                drawCircle(Paper, notch, Offset(size.width, 0f))
                drawLine(Blue, Offset(notch, 0f), Offset(size.width - notch, 0f), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            }
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NpButton(stringResource(R.string.nowplaying_previous), transport::previous, Modifier.weight(1f).height(62.dp).border(2.5.dp, Blue, RectangleShape), RectangleShape, Blue, track.canPrevious) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = Blue, modifier = Modifier.size(28.dp))
            }
            NpButton(stringResource(if (track.playing) R.string.nowplaying_pause else R.string.nowplaying_play), transport::playPause, Modifier.weight(1.4f).height(62.dp).background(Blue), RectangleShape, Stub) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlayPauseIcon(track.playing, Stub, 26.dp)
                    Text(if (track.playing) "PAUSE" else "PLAY", color = Stub, fontFamily = Anton, fontSize = 20.sp, letterSpacing = 1.5.sp)
                }
            }
            NpButton(stringResource(R.string.nowplaying_next), transport::next, Modifier.weight(1f).height(62.dp).border(2.5.dp, Blue, RectangleShape), RectangleShape, Blue, track.canNext) {
                Icon(Icons.Rounded.SkipNext, null, tint = Blue, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/**
 * Prints a picture as a two-ink halftone, like a riso poster: blue dots carry the darks, pink
 * dots carry the warm colours, each on its own screen angle and a hair out of register.
 */
internal object Halftone {
    fun print(src: ImageBitmap, sizePx: Int): ImageBitmap {
        val n = 120
        val source = src.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        val small = Bitmap.createScaledBitmap(source, n, n, true)
        val px = IntArray(n * n)
        small.getPixels(px, 0, n, 0, 0, n, n)

        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(Paper.toArgb())
        val ink = Paint(Paint.ANTI_ALIAS_FLAG)
        // Inks multiply where they overlap; older phones get the nearest match.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ink.blendMode = android.graphics.BlendMode.MULTIPLY
        else ink.xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)

        val s = sizePx.toFloat()
        val cell = s / 53f
        fun sample(x: Float, y: Float): Int {
            val xi = (x / s * n).toInt().coerceIn(0, n - 1)
            val yi = (y / s * n).toInt().coerceIn(0, n - 1)
            return px[yi * n + xi]
        }
        fun screen(degrees: Float, color: Color, dx: Float, dy: Float, value: (Float, Float, Float) -> Float) {
            ink.color = color.toArgb()
            val a = Math.toRadians(degrees.toDouble())
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            val r = s * 0.75f
            var u = -r
            while (u <= r) {
                var v = -r
                while (v <= r) {
                    val x = s / 2 + u * ca - v * sa
                    val y = s / 2 + u * sa + v * ca
                    if (x > -cell && y > -cell && x < s + cell && y < s + cell) {
                        val c = sample(x, y)
                        val amount = value((c shr 16 and 0xFF) / 255f, (c shr 8 and 0xFF) / 255f, (c and 0xFF) / 255f)
                        if (amount >= 0.05f) canvas.drawCircle(x + dx, y + dy, sqrt(min(1f, amount)) * cell * 0.64f, ink)
                    }
                    v += cell
                }
                u += cell
            }
        }
        fun lum(r: Float, g: Float, b: Float) = 0.299f * r + 0.587f * g + 0.114f * b
        screen(15f, Blue, 0f, 0f) { r, g, b -> Math.pow((1f - lum(r, g, b)).toDouble(), 1.15).toFloat() * 1.05f }
        screen(75f, Pink, cell * 0.23f, -cell * 0.18f) { r, g, b ->
            val warm = (r - b) * 1.25f + (r - g) * 0.35f
            (warm + 0.12f).coerceIn(0f, 1f) * (0.35f + 0.65f * min(1f, lum(r, g, b) * 1.6f))
        }
        return out.asImageBitmap()
    }
}
