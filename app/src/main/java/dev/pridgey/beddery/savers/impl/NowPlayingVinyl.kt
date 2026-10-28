package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.media.NowPlaying
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.TabularNumbers
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val VCream = Color(0xFFF3E9DC)
private val VMuted = Color(0xFFA8978A)
private val VLine = Color(0xFF3A2E26)

/** A turntable: the cover is the record's label, and the tonearm creeps inward as the song plays. */
@Composable
internal fun VinylStyle(ctx: SaverContext, track: NowPlaying, transport: Transport) {
    val accent = track.art?.accent ?: Color(0xFFF59A55)
    var rpm45 by remember { mutableStateOf(false) }
    // 33⅓ rpm is 200 degrees a second; 45 rpm is 270.
    val spin = rememberSpin(track.playing && !ctx.preview, if (rpm45) 270f else 200f)
    val pos = rememberPositionMs(track)
    val p = track.fraction(pos)
    // Playing, the needle rides from the edge to the label; stopped, the arm returns to its rest.
    val arm by animateFloatAsState(if (track.playing) 5f + p * 20f else -6f, tween(1400, easing = FastOutSlowInEasing), label = "arm")

    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFF34261C), Color(0xFF17110D), Color(0xFF0C0907)),
                    center = Offset(size.width * 0.18f, size.height * 0.08f),
                    radius = size.maxDimension * 1.1f,
                ),
            )
        },
    ) {
        if (maxWidth > maxHeight) {
            val deck = min(maxHeight - 56.dp, maxWidth * 0.48f)
            Row(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Turntable(track, spin, arm, accent, deck)
                Spacer(Modifier.width(48.dp))
                Column(Modifier.weight(1f)) {
                    VinylTop(ctx, rpm45) { rpm45 = it }
                    Spacer(Modifier.height(24.dp))
                    VinylInfo(track, accent, 30.sp)
                    Spacer(Modifier.height(18.dp))
                    VinylProgress(track, pos, p, accent)
                    Spacer(Modifier.height(18.dp))
                    VinylControls(track, transport, accent, Modifier.fillMaxWidth())
                }
            }
        } else {
            val deck = min(maxWidth - 20.dp, maxHeight * 0.46f)
            Column(Modifier.fillMaxSize().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                VinylTop(ctx, rpm45, Modifier.padding(horizontal = 26.dp)) { rpm45 = it }
                Spacer(Modifier.weight(0.6f))
                Turntable(track, spin, arm, accent, deck)
                Spacer(Modifier.weight(0.6f))
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp)) {
                    VinylInfo(track, accent, 34.sp)
                    Spacer(Modifier.height(22.dp))
                    VinylProgress(track, pos, p, accent)
                }
                Spacer(Modifier.weight(1f))
                VinylControls(track, transport, accent, Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun VinylTop(ctx: SaverContext, rpm45: Boolean, modifier: Modifier = Modifier, onSpeed: (Boolean) -> Unit) {
    val (time, ampm) = clockParts(ctx)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, color = VCream, fontFamily = Fraunces, fontWeight = FontWeight.Light, fontSize = 36.sp, style = TabularNumbers)
            if (ampm != null) Text(ampm, color = VMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, bottom = 7.dp))
        }
        Row(
            Modifier
                .background(Color(0xFF1F1813), CircleShape)
                .border(1.dp, VLine, CircleShape)
                .padding(3.dp),
        ) {
            SpeedChip("33⅓", "33 rpm", !rpm45) { onSpeed(false) }
            SpeedChip("45", "45 rpm", rpm45) { onSpeed(true) }
        }
    }
}

@Composable
private fun SpeedChip(text: String, label: String, on: Boolean, onClick: () -> Unit) {
    NpButton(label, onClick, Modifier.height(32.dp).background(if (on) VCream else Color.Transparent, CircleShape)) {
        Text(
            text, color = if (on) Color(0xFF17110D) else VMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun VinylInfo(track: NowPlaying, accent: Color, titleSize: TextUnit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "SIDE A · ${track.appName.uppercase()}", color = accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            track.title, color = VCream, fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = titleSize,
            lineHeight = 1.06.em, letterSpacing = (-0.5).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        val by = listOfNotNull(track.artist, track.album?.takeIf { it != track.title }).joinToString(" — ")
        if (by.isNotEmpty()) {
            Text(by, color = Color(0xFFC9B8A6), fontFamily = Fraunces, fontStyle = FontStyle.Italic, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun VinylProgress(track: NowPlaying, pos: Long, p: Float, accent: Color) {
    if (track.durationMs <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(formatTrackTime(pos), color = VMuted, fontFamily = Fraunces, fontSize = 14.sp, style = TabularNumbers)
        Canvas(Modifier.weight(1f).height(2.dp)) {
            drawRoundRect(VLine, cornerRadius = CornerRadius(1f))
            drawRoundRect(accent, size = Size(size.width * p, size.height), cornerRadius = CornerRadius(1f))
        }
        Text(formatTrackTime(track.durationMs), color = VMuted, fontFamily = Fraunces, fontSize = 14.sp, style = TabularNumbers)
    }
}

@Composable
private fun VinylControls(track: NowPlaying, transport: Transport, accent: Color, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        NpButton(stringResource(R.string.nowplaying_previous), transport::previous, Modifier.size(60.dp).border(1.dp, VLine, CircleShape), enabled = track.canPrevious) {
            Icon(Icons.Rounded.SkipPrevious, null, tint = VCream, modifier = Modifier.size(28.dp))
        }
        val key = RoundedCornerShape(16.dp)
        NpButton(
            stringResource(if (track.playing) R.string.nowplaying_pause else R.string.nowplaying_play), transport::playPause,
            Modifier
                .size(width = 176.dp, height = 68.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF2E2520), Color(0xFF221B17))), key)
                .border(1.dp, Color(0xFF43382F), key),
            shape = key,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .size(9.dp)
                        .drawBehind {
                            if (track.playing) drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.6f), Color.Transparent)), size.minDimension * 1.6f)
                            drawCircle(if (track.playing) accent else Color(0xFF4A3E35))
                        },
                )
                Text("START · STOP", color = VCream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.6.sp)
            }
        }
        NpButton(stringResource(R.string.nowplaying_next), transport::next, Modifier.size(60.dp).border(1.dp, VLine, CircleShape), enabled = track.canNext) {
            Icon(Icons.Rounded.SkipNext, null, tint = VCream, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun Turntable(track: NowPlaying, spin: State<Float>, arm: Float, accent: Color, size: Dp) {
    val image = track.art?.image
    Canvas(Modifier.size(size)) {
        // Drawn in the 370-unit space of the mockup.
        val k = this.size.width / 370f
        val c = Offset(185f * k, 185f * k)
        val labelR = 80f * k

        drawCircle(Brush.radialGradient(listOf(Color(0xFF241E1B), Color(0xFF1A1513)), c, 185f * k), 185f * k, c)
        drawCircle(Color.White.copy(alpha = 0.06f), 185f * k, c, style = Stroke(1f))

        rotate(spin.value, c) {
            // Strobe dots on the platter rim.
            for (i in 0 until 60) {
                val a = Math.toRadians(i * 6.0)
                drawCircle(Color(0x8CA89C90), 1.3f * k, Offset(c.x + cos(a).toFloat() * 180f * k, c.y + sin(a).toFloat() * 180f * k))
            }
            drawCircle(Color(0xFF121012), 175f * k, c)
            var r = labelR + 5f * k
            while (r < 170f * k) {
                drawCircle(Color(0xFF1E1B1D), r, c, style = Stroke(0.9f * k))
                r += 2.3f * k
            }
            // Quiet gaps between tracks.
            drawCircle(Color.White.copy(alpha = 0.06f), 112f * k, c, style = Stroke(2f * k))
            drawCircle(Color.White.copy(alpha = 0.05f), 142f * k, c, style = Stroke(2f * k))
            drawCircle(Color(0xFF0D0B0C), 172.5f * k, c, style = Stroke(5f * k))

            clipPath(Path().apply { addOval(Rect(c, labelR)) }) {
                if (image != null) {
                    drawImage(
                        image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset((c.x - labelR).roundToInt(), (c.y - labelR).roundToInt()),
                        dstSize = IntSize((labelR * 2).roundToInt(), (labelR * 2).roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                } else {
                    drawCircle(accent, labelR, c)
                }
            }
            drawCircle(Color(0xFF0D0B0C), labelR, c, style = Stroke(2f * k))
        }

        // The sheen stays put while the record turns under it.
        rotate(300f, c) {
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.05f to Color(0x1CFFF0DC), 0.15f to Color.Transparent,
                    0.5f to Color.Transparent, 0.59f to Color(0x12FFF0DC), 0.66f to Color.Transparent, 1f to Color.Transparent,
                    center = c,
                ),
                175f * k, c,
            )
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF4EEE6), Color(0xFF8C847C)), c - Offset(2f * k, 2f * k), 7f * k), 6f * k, c)

        // Strobe lamp.
        val lamp = Offset(11f * k, 323f * k)
        drawCircle(Brush.radialGradient(listOf(Color(0x73FF8A3D), Color.Transparent), lamp, 16f * k), 16f * k, lamp)
        drawCircle(Color(0xFFFF8A3D), 5f * k, lamp)

        // Tonearm.
        val pivot = Offset(348f * k, 30f * k)
        drawCircle(Color(0xFF231C18), 27f * k, pivot)
        drawCircle(Color(0xFF43382F), 27f * k, pivot, style = Stroke(1.5f * k))
        val steel = listOf(Color(0xFF8E857C), Color(0xFFF1EAE1), Color(0xFF8E857C))
        rotate(arm, pivot) {
            drawRoundRect(Brush.horizontalGradient(steel, 336f * k, 360f * k), Offset(336f * k, -14f * k), Size(24f * k, 30f * k), CornerRadius(5f * k))
            val tube = Path().apply {
                moveTo(348f * k, 30f * k)
                lineTo(348f * k, 236f * k)
                lineTo(342f * k, 260f * k)
            }
            drawPath(tube, Brush.horizontalGradient(steel, 343f * k, 353f * k), style = Stroke(5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            rotate(-12f, Offset(342f * k, 270f * k)) {
                drawRoundRect(Color(0xFF1A1614), Offset(334f * k, 256f * k), Size(16f * k, 26f * k), CornerRadius(2.5f * k))
                drawRoundRect(Color(0xFFC9BFB4), Offset(334f * k, 256f * k), Size(16f * k, 26f * k), CornerRadius(2.5f * k), style = Stroke(1.2f * k))
                drawRect(accent, Offset(334f * k, 270f * k), Size(16f * k, 2.5f * k))
                drawLine(Color(0xFFC9BFB4), Offset(350f * k, 262f * k), Offset(360f * k, 258f * k), 2f * k, cap = StrokeCap.Round)
            }
            drawCircle(Brush.radialGradient(steel, pivot, 10f * k), 10f * k, pivot)
        }
    }
}
