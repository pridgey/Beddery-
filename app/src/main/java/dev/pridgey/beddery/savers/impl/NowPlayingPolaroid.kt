package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.media.Art
import dev.pridgey.beddery.media.NowPlaying
import dev.pridgey.beddery.notifications.NotificationItem
import dev.pridgey.beddery.savers.SaverContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val PInk = Color(0xFFE6DED0)
private val PMuted = Color(0xFFA9A1B4)
private val PPen = Color(0xFF26213A)
private val NoteTop = Color(0xFFF6E3A1)
private val NoteBottom = Color(0xFFEDD486)
private val Dimmed = grayscale(0.15f, 0.62f)
private val ShortDay: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }

/** The cover as an instant photo taped to a desk, with the last song's photo tucked behind it. */
@Composable
internal fun PolaroidStyle(ctx: SaverContext, track: NowPlaying, transport: Transport) {
    // When the song changes, the old photo goes behind the new one.
    var last by remember { mutableStateOf<Art?>(null) }
    var behind by remember { mutableStateOf<Art?>(null) }
    LaunchedEffect(track.art?.key) {
        val a = track.art ?: return@LaunchedEffect
        if (a.key != last?.key) {
            behind = last
            last = a
        }
    }
    val pos = rememberPositionMs(track)
    val p = track.fraction(pos)
    val notices = rememberNotices(ctx)

    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Color(0xFF1B1A1F))
            drawRect(Brush.radialGradient(listOf(Color(0x29FFC482), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.12f), size.maxDimension * 0.6f))
            drawRect(ShaderBrush(ImageShader(Grain.tile, TileMode.Repeated, TileMode.Repeated)), alpha = 0.05f)
        },
    ) {
        if (maxWidth > maxHeight) {
            val front = min(maxHeight * 0.66f, maxWidth * 0.36f)
            Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Photos(track, behind, front, notices)
                Spacer(Modifier.width(40.dp))
                Column(Modifier.weight(1f)) {
                    PolaroidTop(ctx)
                    Spacer(Modifier.height(20.dp))
                    PolaroidCaption(track, TextAlign.Start)
                    Spacer(Modifier.height(14.dp))
                    WavyProgress(track, pos, p)
                    Spacer(Modifier.height(16.dp))
                    PolaroidControls(track, transport)
                }
            }
        } else {
            val front = min(maxWidth * 0.754f, maxHeight * 0.36f)
            Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                PolaroidTop(ctx)
                Spacer(Modifier.weight(0.6f))
                Photos(track, behind, front, notices)
                Spacer(Modifier.weight(0.5f))
                PolaroidCaption(track, TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                WavyProgress(track, pos, p)
                Spacer(Modifier.weight(1f))
                PolaroidControls(track, transport)
            }
        }
    }
}

@Composable
private fun PolaroidTop(ctx: SaverContext) {
    val (time, ampm) = clockParts(ctx)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Text(time + (ampm?.let { " " + it.lowercase() } ?: ""), color = PInk, fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 34.sp)
        Text(ShortDay.format(ctx.now).lowercase(), color = PMuted, fontFamily = Caveat, fontWeight = FontWeight.Medium, fontSize = 22.sp)
    }
}

/** The current photo, with the previous one peeking out behind it. */
@Composable
private fun Photos(track: NowPlaying, behind: Art?, front: Dp, notices: Notices) {
    val k = front.value / 294f
    Box(Modifier.size(width = (330 * k).dp, height = (410 * k).dp)) {
        if (behind != null) {
            Photo(behind, null, (268 * k).dp, Modifier.offset((30 * k).dp, (14 * k).dp).rotate(8f), filter = Dimmed, k = k * 268f / 294f, tape = false)
        }
        Photo(track.art, track.title, front, Modifier.offset(0.dp, (8 * k).dp).rotate(-3.5f), filter = FilmFilter, k = k, tape = true)
        AnimatedVisibility(
            notices.waiting && notices.arrival == null, Modifier.align(Alignment.BottomEnd).offset((14 * k).dp, (-6 * k).dp),
            enter = fadeIn(tween(900)), exit = fadeOut(tween(500)),
        ) {
            NotePile(notices, k)
        }
        ArrivalCard(notices, Modifier.align(Alignment.BottomEnd).offset((22 * k).dp, (-18 * k).dp)) { item ->
            StickyNote(notices, item, k)
        }
    }
}

/** A sticky note on the desk with the message written out by hand. */
@Composable
private fun StickyNote(notices: Notices, item: NotificationItem, k: Float) {
    val (head, body) = noticeLines(notices, item)
    Box(Modifier.rotate(5f)) {
        Column(
            Modifier
                .padding(top = (10 * k).dp)
                .width((176 * k).dp)
                .heightIn(min = (150 * k).dp)
                .shadow((12 * k).dp, RectangleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .background(Brush.verticalGradient(listOf(NoteTop, NoteBottom)))
                .padding(start = (14 * k).dp, end = (14 * k).dp, top = (18 * k).dp, bottom = (12 * k).dp),
        ) {
            Text(head, color = PPen, fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 25f.scaledSp(k), lineHeight = 1.em, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (body != null) {
                Text(
                    body, color = PPen, fontFamily = Caveat, fontWeight = FontWeight.Medium, fontSize = 23f.scaledSp(k), lineHeight = 1.05.em,
                    maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = (4 * k).dp),
                )
            }
        }
        Tape(Modifier.align(Alignment.TopCenter).size((72 * k).dp, (20 * k).dp).rotate(-4f))
    }
}

/** A few notes stacked on the desk, with how many are waiting. */
@Composable
private fun NotePile(notices: Notices, k: Float) {
    val label = noticeCountLabel(notices)
    val note = (70 * k).dp
    Box(Modifier.size(note).clearAndSetSemantics { contentDescription = label }) {
        Box(Modifier.fillMaxSize().rotate(-6f).shadow((6 * k).dp, RectangleShape).background(Color(0xFFE0C877)))
        Box(Modifier.fillMaxSize().rotate(3f).background(Color(0xFFEAD38A)))
        Box(Modifier.fillMaxSize().rotate(8f).background(Brush.verticalGradient(listOf(NoteTop, NoteBottom))), contentAlignment = Alignment.Center) {
            Text("${notices.count} new", color = PPen, fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 24f.scaledSp(k))
        }
    }
}

@Composable
private fun Photo(art: Art?, caption: String?, width: Dp, modifier: Modifier, filter: ColorFilter, k: Float, tape: Boolean) {
    val border = (16 * k).dp
    val image = width - border * 2
    Box(
        modifier
            .size(width, width - border * 2 + border + (78 * k).dp)
            .shadow((22 * k).dp, RectangleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Brush.verticalGradient(listOf(Color(0xFFFBF8F0), Color(0xFFEFE9DA)))),
    ) {
        Canvas(Modifier.offset(border, border).size(image)) {
            val img = art?.image
            if (img != null) {
                drawImage(
                    img, srcOffset = IntOffset.Zero, srcSize = IntSize(img.width, img.height),
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), colorFilter = filter, filterQuality = FilterQuality.Medium,
                )
            } else {
                drawRect(Color(0xFF3A3448))
            }
            drawRect(Brush.radialGradient(0.5f to Color.Transparent, 1f to Color(0x52140A00), center = Offset(size.width * 0.5f, size.height * 0.45f), radius = size.width * 0.75f))
            if (tape) {
                // A warm light leak from the corner.
                drawRect(Brush.linearGradient(listOf(Color(0x61FF9646), Color.Transparent), Offset(size.width, 0f), Offset(size.width * 0.62f, size.height * 0.32f)), blendMode = BlendMode.Screen)
            }
            drawRect(Color(0x2E000000), style = Stroke(1f))
        }
        if (tape) {
            val d = java.time.LocalDate.now()
            Text(
                "'%02d %02d %02d".format(d.year % 100, d.monthValue, d.dayOfMonth),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15f.scaledSp(k), color = Color(0xE6FF9A4A), shadow = Shadow(Color(0xCCFF7828), blurRadius = 10f)),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = -border - (10 * k).dp, y = border + image - (26 * k).dp),
            )
        }
        if (caption != null) {
            Text(
                "$caption ♡", color = PPen, fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 34f.scaledSp(k), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = (20 * k).dp, end = (16 * k).dp, bottom = (16 * k).dp).rotate(-1f),
            )
        }
        if (tape) Tape(Modifier.align(Alignment.TopCenter).offset(y = (-15 * k).dp).size((104 * k).dp, (30 * k).dp).rotate(3f))
    }
}

@Composable
private fun Tape(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val teeth = 6
        val path = Path().apply {
            moveTo(0f, 0f)
            // Torn ends: little zigzags on the left and right.
            lineTo(w, 0f)
            for (i in 0..teeth) lineTo(if (i % 2 == 0) w else w - w * 0.04f, h * i / teeth)
            lineTo(0f, h)
            for (i in teeth downTo 0) lineTo(if (i % 2 == 0) 0f else w * 0.04f, h * i / teeth)
            close()
        }
        drawPath(path, Color(0xCCEEE0B6))
    }
}

@Composable
private fun PolaroidCaption(track: NowPlaying, align: TextAlign) {
    val by = listOfNotNull(track.artist, track.album?.takeIf { it != track.title }?.lowercase()).joinToString(" — ")
    if (by.isNotEmpty()) {
        Text(by, color = Color(0xFFD3CADF), fontFamily = Caveat, fontWeight = FontWeight.Medium, fontSize = 27.sp, textAlign = align, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
    }
}

/** A pencil line, inked in as the song plays. */
@Composable
private fun WavyProgress(track: NowPlaying, pos: Long, p: Float) {
    if (track.durationMs <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(formatTrackTime(pos), color = PMuted, fontFamily = Caveat, fontSize = 21.sp)
        Canvas(Modifier.weight(1f).height(16.dp)) {
            val sx = size.width / 240f
            val sy = size.height / 16f
            val line = Path().apply {
                moveTo(2f * sx, 9f * sy)
                cubicTo(22f * sx, 4f * sy, 38f * sx, 13f * sy, 60f * sx, 8f * sy)
                cubicTo(82f * sx, 3f * sy, 98f * sx, 4f * sy, 120f * sx, 9f * sy)
                cubicTo(142f * sx, 14f * sy, 160f * sx, 13f * sy, 182f * sx, 8f * sy)
                cubicTo(204f * sx, 3f * sy, 222f * sx, 5f * sy, 238f * sx, 9f * sy)
            }
            drawPath(line, Color(0xFF5E5869), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))))
            val measure = PathMeasure().apply { setPath(line, false) }
            val inked = Path()
            measure.getSegment(0f, measure.length * p, inked, true)
            drawPath(inked, Color(0xFFEDE6DA), style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round))
        }
        Text(formatTrackTime(track.durationMs), color = PMuted, fontFamily = Caveat, fontSize = 21.sp)
    }
}

/** A circle drawn by hand: it doesn't quite close. */
private fun handCircle(size: Float): Path = Path().apply {
    val f = size / 62f
    moveTo(31f * f, 4f * f)
    cubicTo(47f * f, 3f * f, 59f * f, 15f * f, 58f * f, 31f * f)
    cubicTo(57f * f, 47f * f, 45f * f, 59f * f, 29f * f, 58f * f)
    cubicTo(14f * f, 57f * f, 3f * f, 45f * f, 4f * f, 29f * f)
    cubicTo(5f * f, 15f * f, 17f * f, 5f * f, 35f * f, 6f * f)
}

@Composable
private fun PolaroidControls(track: NowPlaying, transport: Transport) {
    Row(horizontalArrangement = Arrangement.spacedBy(34.dp), verticalAlignment = Alignment.CenterVertically) {
        NpButton(stringResource(R.string.nowplaying_previous), transport::previous, Modifier.size(62.dp), enabled = track.canPrevious) {
            Canvas(Modifier.fillMaxSize()) { drawPath(handCircle(size.width), PMuted, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
            Icon(Icons.Rounded.SkipPrevious, null, tint = PInk, modifier = Modifier.size(26.dp))
        }
        NpButton(stringResource(if (track.playing) R.string.nowplaying_pause else R.string.nowplaying_play), transport::playPause, Modifier.size(86.dp), rippleColor = PPen) {
            Canvas(Modifier.fillMaxSize()) {
                val f = size.width / 86f
                val blob = Path().apply {
                    moveTo(43f * f, 4f * f)
                    cubicTo(66f * f, 3f * f, 83f * f, 20f * f, 82f * f, 43f * f)
                    cubicTo(81f * f, 66f * f, 64f * f, 83f * f, 41f * f, 82f * f)
                    cubicTo(19f * f, 81f * f, 3f * f, 64f * f, 4f * f, 41f * f)
                    cubicTo(5f * f, 19f * f, 22f * f, 4f * f, 46f * f, 5f * f)
                    close()
                }
                drawPath(blob, Color(0xFFF6F1E4))
            }
            PlayPauseIcon(track.playing, PPen, 34.dp)
        }
        NpButton(stringResource(R.string.nowplaying_next), transport::next, Modifier.size(62.dp), enabled = track.canNext) {
            Canvas(Modifier.fillMaxSize().rotate(140f)) { drawPath(handCircle(size.width), PMuted, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)) }
            Icon(Icons.Rounded.SkipNext, null, tint = PInk, modifier = Modifier.size(26.dp))
        }
    }
}
