package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.media.NowPlaying
import dev.pridgey.beddery.notifications.NotificationItem
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.formatLongDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

private val AGround = Color(0xFF040507)
private val ASoft = Color(0xFFC3D3D6)
private val Warm = Color(0xFFFFA870)
private val WarmInk = Color(0xFFFFE2C4)
private val DotColors = listOf(Color(0xFFFFD2A6), Color(0xFFB4D8DC), Color(0xFFC8C0F0), Color(0xFFE8C4D8))
private val Saturate = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

/** The cover floats in a glow made of its own colours, which breathes while the music plays. */
@Composable
internal fun AuraStyle(ctx: SaverContext, track: NowPlaying, transport: Transport) {
    val accent = track.art?.accent ?: Color(0xFF8FE3E6)
    val clock = rememberClock(track.playing && !ctx.preview)
    val glow by animateFloatAsState(if (track.playing) 1f else 0.35f, tween(1600), label = "glow")
    val artScale by animateFloatAsState(if (track.playing) 1f else 0.92f, tween(1200), label = "art")
    val pos = rememberPositionMs(track)
    val p = track.fraction(pos)
    // The glow layers fade to nothing at their own edges, so turning and stretching them never shows a line.
    val glowImage = remember(track.art?.key) { track.art?.backdrop?.let(::softEdged) }
    var artCenter by remember { mutableStateOf(Offset.Unspecified) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val notices = rememberNotices(ctx)

    BoxWithConstraints(Modifier.fillMaxSize().background(AGround)) {
        val land = maxWidth > maxHeight
        val artSize = if (land) min(maxHeight * 0.6f, maxWidth * 0.34f) else min(maxWidth * 0.62f, maxHeight * 0.3f)

        Canvas(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
            val c = if (artCenter.isSpecified) artCenter - origin else Offset(size.width / 2, size.height * 0.42f)
            val a = artSize.toPx()
            val t = clock.value
            val breathe = 0.5f - 0.5f * cos(t * 2f * PI.toFloat() / 16f)
            val drift = 0.5f - 0.5f * cos(t * 2f * PI.toFloat() / 23f)
            val back = glowImage
            // The glow is drawn in its own layer, then faded out with a soft round mask so no edges show.
            drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), Paint()) }
            if (back == null) {
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.5f * glow), Color.Transparent), c, a * 1.4f), a * 1.4f, c)
            } else {
                val s1 = a * 2.55f * (1f + 0.18f * breathe)
                rotate(14f * breathe, c) {
                    drawImage(
                        back, srcOffset = IntOffset.Zero, srcSize = IntSize(back.width, back.height),
                        dstOffset = IntOffset((c.x - s1 / 2).roundToInt(), (c.y - s1 / 2).roundToInt()), dstSize = IntSize(s1.roundToInt(), s1.roundToInt()),
                        alpha = 0.85f * glow, colorFilter = Saturate, filterQuality = FilterQuality.Low,
                    )
                }
                val s2 = a * 1.95f * (1.1f - 0.15f * drift)
                val c2 = c + Offset(-18f * drift, 12f * drift + a * 0.25f)
                rotate(180f - 18f * drift, c2) {
                    drawImage(
                        back, srcOffset = IntOffset.Zero, srcSize = IntSize(back.width, back.height),
                        dstOffset = IntOffset((c2.x - s2 / 2).roundToInt(), (c2.y - s2 / 2).roundToInt()), dstSize = IntSize(s2.roundToInt(), s2.roundToInt()),
                        alpha = 0.5f * glow, colorFilter = Saturate, filterQuality = FilterQuality.Low, blendMode = BlendMode.Screen,
                    )
                }
            }
            drawRect(Brush.radialGradient(0f to Color.Black, 0.35f to Color.Black, 1f to Color.Transparent, center = c, radius = a * 1.45f), blendMode = BlendMode.DstIn)
            drawIntoCanvas { it.restore() }
            // Fine grain keeps the soft gradients from banding.
            drawRect(ShaderBrush(ImageShader(Grain.tile, TileMode.Repeated, TileMode.Repeated)), alpha = 0.06f, blendMode = BlendMode.Overlay)
        }

        val art: @Composable () -> Unit = {
            Box(Modifier.size(artSize)) {
                FeatheredArt(track, accent, artSize, artScale, Modifier.onGloballyPositioned { artCenter = it.boundsInRoot().center })
                ArrivalCard(notices, Modifier.align(Alignment.BottomEnd).offset(x = artSize * 0.16f, y = artSize * 0.12f)) { item ->
                    SecondGlow(item, artSize * 0.5f)
                }
            }
        }
        if (land) {
            Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { art() }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    AuraClock(ctx, notices)
                    Spacer(Modifier.height(30.dp))
                    AuraInfo(track, pos, notices)
                    Spacer(Modifier.height(26.dp))
                    AuraControls(track, transport, accent, p)
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(top = 54.dp, bottom = 56.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AuraClock(ctx, notices)
                Spacer(Modifier.weight(0.8f))
                art()
                Spacer(Modifier.weight(0.55f))
                AuraInfo(track, pos, notices)
                Spacer(Modifier.weight(1f))
                AuraControls(track, transport, accent, p)
            }
        }
    }
}

/** The cover with its edges fading into the glow instead of a hard frame. */
@Composable
private fun FeatheredArt(track: NowPlaying, accent: Color, size: Dp, scale: Float, modifier: Modifier) {
    val image = track.art?.image
    Canvas(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, this.size), Paint()) }
        if (image != null) {
            drawImage(
                image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
                dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()), filterQuality = FilterQuality.Medium,
            )
        } else {
            drawRect(Brush.linearGradient(listOf(accent, AGround)))
        }
        drawRect(
            Brush.radialGradient(0f to Color.Black, 0.45f to Color.Black, 0.75f to Color.Black.copy(alpha = 0.55f), 1f to Color.Transparent, center = center, radius = this.size.width * 0.55f),
            blendMode = BlendMode.DstIn,
        )
        drawIntoCanvas { it.restore() }
    }
}

@Composable
private fun AuraClock(ctx: SaverContext, notices: Notices) {
    val (time, _) = clockParts(ctx)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(time, color = ASoft, fontSize = 20.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 9.sp, modifier = Modifier.padding(start = 9.dp))
        Text(formatLongDate(ctx.now.toLocalDate()).uppercase(), color = Color(0xFF7F959A), fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 3.sp, modifier = Modifier.padding(top = 6.dp))
        AnimatedVisibility(notices.waiting, enter = fadeIn(tween(900)), exit = fadeOut(tween(900))) {
            WaitingDots(notices)
        }
    }
}

/** Each app with notifications waiting, as its icon glowing softly; the newest is a little bigger. */
@Composable
private fun WaitingDots(notices: Notices) {
    val label = noticeCountLabel(notices)
    Row(
        Modifier.padding(top = 12.dp).clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        notices.apps.take(DotColors.size).forEachIndexed { i, app ->
            val c = DotColors[i]
            Box(
                Modifier
                    .size(if (i == 0) 18.dp else 15.dp)
                    .drawBehind { drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.4f), Color.Transparent)), size.minDimension * 1.1f) },
                contentAlignment = Alignment.Center,
            ) {
                NoticeGlyph(null, app, c, if (i == 0) 16.dp else 13.dp)
            }
        }
    }
}

/** A smaller, warmer glow that drifts in beside the cover, holding the app's icon. */
@Composable
private fun SecondGlow(item: NotificationItem, size: Dp) {
    Box(
        Modifier
            .size(size)
            .drawBehind { drawCircle(Brush.radialGradient(0f to Warm.copy(alpha = 0.55f), 0.4f to Warm.copy(alpha = 0.22f), 1f to Color.Transparent)) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .background(WarmInk.copy(alpha = 0.16f), CircleShape)
                .border(1.dp, WarmInk.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            NoticeGlyph(item, null, WarmInk, 20.dp)
        }
    }
}

@Composable
private fun AuraInfo(track: NowPlaying, pos: Long, notices: Notices) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(track.title, color = Color(0xFFEAF3F4), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        track.artist?.let { Text(it, color = Color(0xFFB4C6C9), fontSize = 15.sp, fontWeight = FontWeight.Light, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
        val line = when {
            !track.playing -> "PAUSED  ·  " + formatTrackTime(pos)
            track.durationMs > 0 -> formatTrackTime(pos) + "  ·  " + formatTrackTime(track.durationMs)
            else -> formatTrackTime(pos)
        }
        // A new notification takes the time line's place for a few seconds.
        Crossfade(notices.arrival, Modifier.padding(top = 14.dp), animationSpec = tween(700), label = "notice") { item ->
            if (item == null) {
                Text(line, color = Color(0xFF8BA0A4), fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.5.sp)
            } else {
                val (head, body) = noticeLines(notices, item)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        NoticeGlyph(item, null, Color(0xFFFFCFA3), 14.dp)
                        Text(head.uppercase(), color = Color(0xFFFFCFA3), fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (body != null) {
                        Text(body, color = Color(0xFFF2E3D4), fontSize = 14.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AuraControls(track: NowPlaying, transport: Transport, accent: Color, p: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(38.dp), verticalAlignment = Alignment.CenterVertically) {
        NpButton(stringResource(R.string.nowplaying_previous), transport::previous, Modifier.size(52.dp), enabled = track.canPrevious) {
            Icon(Icons.Rounded.SkipPrevious, null, tint = ASoft, modifier = Modifier.size(24.dp))
        }
        // The song's progress runs around the play button.
        NpButton(
            stringResource(if (track.playing) R.string.nowplaying_pause else R.string.nowplaying_play), transport::playPause,
            Modifier.size(84.dp).background(Color.White.copy(alpha = 0.07f), CircleShape),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 2.5.dp.toPx()
                val inset = w
                drawCircle(Color.White.copy(alpha = 0.12f), size.minDimension / 2 - inset, style = Stroke(2.dp.toPx()))
                if (track.durationMs > 0) {
                    drawArc(
                        accent, -90f, 360f * p, false,
                        topLeft = Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
            }
            PlayPauseIcon(track.playing, Color(0xFFEAF3F4), 30.dp)
        }
        NpButton(stringResource(R.string.nowplaying_next), transport::next, Modifier.size(52.dp), enabled = track.canNext) {
            Icon(Icons.Rounded.SkipNext, null, tint = ASoft, modifier = Modifier.size(24.dp))
        }
    }
}

/** A copy of [src] whose alpha falls smoothly to zero toward a circle's edge. */
private fun softEdged(src: ImageBitmap): ImageBitmap {
    val n = 64
    val source = src.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
    val scaled = Bitmap.createScaledBitmap(source, n, n, true)
    val px = IntArray(n * n)
    scaled.getPixels(px, 0, n, 0, 0, n, n)
    val half = n / 2f
    for (y in 0 until n) {
        for (x in 0 until n) {
            val d = hypot(x + 0.5f - half, y + 0.5f - half) / half
            val t = ((1f - d) / 0.55f).coerceIn(0f, 1f)
            val a = (t * t * (3f - 2f * t) * 255).toInt()
            val i = y * n + x
            px[i] = (a shl 24) or (px[i] and 0xFFFFFF)
        }
    }
    return Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
}
