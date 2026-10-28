package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.media.NowPlaying
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.formatLongDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

private val MText = Color(0xFFF4ECF4)
private val MMuted = Color(0xFF9C8DB5)
private val MTile = Color(0xFF231B38)

/** The order pieces arrive in: scattered, so the picture builds up all over. */
private val PieceOrder = intArrayOf(5, 10, 0, 15, 6, 3, 12, 9, 1, 14, 7, 8, 2, 13, 4, 11)

/** The cover as sixteen tiles that light up one by one, so the picture is whole when the song ends. */
@Composable
internal fun MosaicStyle(ctx: SaverContext, track: NowPlaying, transport: Transport) {
    val accent = track.art?.accent ?: Color(0xFFF2A3C2)
    val pos = rememberPositionMs(track)
    val p = track.fraction(pos)
    val clock = rememberClock(track.playing && !ctx.preview)
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "p")

    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Color(0xFF0F0C1A))
            drawRect(Brush.radialGradient(listOf(Color(0xFF1C1630), Color.Transparent), Offset(size.width / 2, size.height * 0.3f), size.maxDimension * 0.7f))
        },
    ) {
        if (maxWidth > maxHeight) {
            val grid = maxHeight - 56.dp
            Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Tiles(track, p, clock, pulse, grid)
                Spacer(Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    MosaicHeader(ctx, track, p)
                    Spacer(Modifier.weight(1f))
                    MosaicInfo(track, pos)
                    Spacer(Modifier.height(18.dp))
                    MosaicButtons(track, transport, accent, Modifier.fillMaxWidth(), (grid - 15.dp) / 4)
                }
            }
        } else {
            val grid = min(maxWidth - 48.dp, maxHeight * 0.42f)
            Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MosaicHeader(ctx, track, p)
                Spacer(Modifier.weight(0.6f))
                Tiles(track, p, clock, pulse, grid)
                Spacer(Modifier.weight(0.5f))
                Column(Modifier.width(grid)) { MosaicInfo(track, pos) }
                Spacer(Modifier.weight(1f))
                MosaicButtons(track, transport, accent, Modifier.width(grid), (grid - 15.dp) / 4)
            }
        }
    }
}

@Composable
private fun MosaicHeader(ctx: SaverContext, track: NowPlaying, p: Float) {
    val (time, _) = clockParts(ctx)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column {
            Text(time, color = MText, fontSize = 38.sp, fontWeight = FontWeight.ExtraLight, style = TabularNumbers)
            Text(formatLongDate(ctx.now.toLocalDate()), color = MMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(track.appName, color = MMuted, fontSize = 12.sp, maxLines = 1)
            if (track.durationMs > 0) {
                val accent = track.art?.accent ?: Color(0xFFF2A3C2)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = accent)) { append(floor(p * 16).toInt().toString()) }
                        append(" of 16 pieces")
                    },
                    color = MMuted, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Tiles(track: NowPlaying, p: Float, clock: State<Float>, pulse: State<Float>, size: Dp) {
    val image = track.art?.image
    val live = track.durationMs > 0
    Canvas(Modifier.size(size)) {
        val s = this.size.width
        val gap = 5.dp.toPx()
        val cell = (s - gap * 3) / 4
        val radius = CornerRadius(6.dp.toPx())
        // Without a song length, show the whole picture.
        val done = if (live) p * 16f else 16f
        val step = floor(done).toInt().coerceAtMost(15)
        val t = clock.value
        for (k in 0 until 16) {
            val rank = PieceOrder.indexOf(k)
            val x = (k % 4) * (cell + gap)
            val y = (k / 4) * (cell + gap)
            // Each piece drifts on its own slow loop.
            val period = 6f + (k * 7) % 5
            val ph = t * 2f * PI.toFloat() / period + k
            val o = Offset(x + sin(ph) * 1.8.dp.toPx(), y + cos(ph * 0.8f) * 2.dp.toPx())
            val (filter, alpha) = when {
                !live || rank < step -> null to 1f
                rank == step -> {
                    val f = done - step
                    grayscale(1f - f, 0.4f + 0.6f * f) to 0.6f + 0.4f * f
                }
                else -> grayscale(1f, 0.38f) to 0.55f
            }
            clipPath(Path().apply { addRoundRect(RoundRect(o.x, o.y, o.x + cell, o.y + cell, radius)) }) {
                if (image != null) {
                    val sx = image.width / s
                    val sy = image.height / s
                    drawImage(
                        image,
                        srcOffset = IntOffset((x * sx).roundToInt(), (y * sy).roundToInt()),
                        srcSize = IntSize((cell * sx).roundToInt().coerceAtLeast(1), (cell * sy).roundToInt().coerceAtLeast(1)),
                        dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt()),
                        dstSize = IntSize(cell.roundToInt(), cell.roundToInt()),
                        alpha = alpha, colorFilter = filter, filterQuality = FilterQuality.Medium,
                    )
                } else {
                    drawRect(MTile, o, Size(cell, cell), alpha = alpha)
                }
            }
            if (live && rank == step && track.playing) {
                drawRoundRect(Color.White.copy(alpha = 0.55f * pulse.value), o, Size(cell, cell), radius, style = Stroke(2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun MosaicInfo(track: NowPlaying, pos: Long) {
    Column(Modifier.fillMaxWidth()) {
        Text(track.title, color = MText, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, lineHeight = 33.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val by = listOfNotNull(track.artist, track.album?.takeIf { it != track.title }).joinToString(" · ")
        if (by.isNotEmpty()) Text(by, color = Color(0xFFC9B8D6), fontSize = 16.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        if (track.durationMs > 0) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTrackTime(pos), color = MMuted, fontSize = 13.sp, fontWeight = FontWeight.Light, style = TabularNumbers)
                Text("-" + formatTrackTime(track.durationMs - pos), color = MMuted, fontSize = 13.sp, fontWeight = FontWeight.Light, style = TabularNumbers)
            }
        }
    }
}

/** The buttons sit on the same four-column grid as the tiles. */
@Composable
private fun MosaicButtons(track: NowPlaying, transport: Transport, accent: Color, modifier: Modifier, cell: Dp) {
    val shape = RoundedCornerShape(8.dp)
    val ink = track.art?.shade ?: Color(0xFF120E1F)
    val h = cell.coerceIn(56.dp, 88.dp)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        NpButton(stringResource(R.string.nowplaying_previous), transport::previous, Modifier.weight(1f).height(h).background(MTile, shape), shape, enabled = track.canPrevious) {
            Icon(Icons.Rounded.SkipPrevious, null, tint = MText, modifier = Modifier.size(26.dp))
        }
        NpButton(stringResource(if (track.playing) R.string.nowplaying_pause else R.string.nowplaying_play), transport::playPause, Modifier.weight(2f).height(h).background(accent, shape), shape, ink) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayPauseIcon(track.playing, ink, 30.dp)
                Text(stringResource(if (track.playing) R.string.nowplaying_pause else R.string.nowplaying_play), color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        NpButton(stringResource(R.string.nowplaying_next), transport::next, Modifier.weight(1f).height(h).background(MTile, shape), shape, enabled = track.canNext) {
            Icon(Icons.Rounded.SkipNext, null, tint = MText, modifier = Modifier.size(26.dp))
        }
    }
}
