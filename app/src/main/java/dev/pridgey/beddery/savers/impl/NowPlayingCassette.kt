package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.media.NowPlaying
import dev.pridgey.beddery.savers.SaverContext
import kotlin.math.roundToInt

private val CInk = Color(0xFFE9E4D8)
private val CMuted = Color(0xFF8A8592)
private val CPaper = Color(0xFFF1E9D6)

/** A clear cassette over the album art; the tape winds from one reel to the other as the song plays. */
@Composable
internal fun CassetteStyle(ctx: SaverContext, track: NowPlaying, transport: Transport) {
    val accent = track.art?.accent ?: Color(0xFFF59A55)
    val pos = rememberPositionMs(track)
    val p = track.fraction(pos)
    val hubs = rememberSpin(track.playing && !ctx.preview, 160f)

    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            drawRect(Color(0xFF131216))
            drawRect(Brush.radialGradient(listOf(Color(0xFF26242B), Color.Transparent), Offset(size.width / 2, 0f), size.maxDimension * 0.7f))
        },
    ) {
        if (maxWidth > maxHeight) {
            val tapeW = min(maxWidth * 0.5f, (maxHeight - 56.dp) * (350f / 224f))
            Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Cassette(track, p, hubs, accent, tapeW)
                Spacer(Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    CassetteTop(ctx)
                    Spacer(Modifier.height(18.dp))
                    CassetteInfo(track, 22f)
                    Spacer(Modifier.height(14.dp))
                    CassetteProgress(track, pos, p, accent)
                    Spacer(Modifier.height(16.dp))
                    Deck(track, transport, accent, keyHeight = 64.dp)
                }
            }
        } else {
            val tapeW = maxWidth - 40.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 36.dp)) {
                CassetteTop(ctx)
                Spacer(Modifier.weight(0.7f))
                Cassette(track, p, hubs, accent, tapeW)
                Spacer(Modifier.weight(0.6f))
                CassetteInfo(track, 26f)
                Spacer(Modifier.height(22.dp))
                CassetteProgress(track, pos, p, accent)
                Spacer(Modifier.weight(1f))
                Deck(track, transport, accent, keyHeight = 84.dp)
            }
        }
    }
}

@Composable
private fun CassetteTop(ctx: SaverContext) {
    val (time, ampm) = clockParts(ctx)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .background(Color(0xFF06120F), RoundedCornerShape(6.dp))
                .border(1.dp, Color(0xFF2A2830), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            Text(
                time + (ampm?.let { " $it" } ?: ""),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 26.sp, color = Color(0xFF7CF5C4),
                    shadow = Shadow(Color(0xAA7CF5C4), blurRadius = 16f),
                ),
            )
        }
        Text(
            "AUTO REVERSE ⇄\nDOLBY B NR · TYPE II", color = CMuted, fontSize = 10.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp,
            fontFamily = FontFamily.Monospace, textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun CassetteInfo(track: NowPlaying, titleSize: Float) {
    Text(
        track.title.uppercase(), color = CInk, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
        fontSize = titleSize.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, letterSpacing = 0.5.sp,
    )
    val by = listOfNotNull(track.artist, track.album?.takeIf { it != track.title }).joinToString(" — ")
    if (by.isNotEmpty()) Text(by, color = Color(0xFFB3AC9E), fontFamily = FontFamily.Monospace, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun CassetteProgress(track: NowPlaying, pos: Long, p: Float, accent: Color) {
    if (track.durationMs <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(formatTrackTime(pos), color = CMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Canvas(Modifier.weight(1f).height(4.dp)) {
            drawRoundRect(Color(0xFF2A2830), cornerRadius = CornerRadius(size.height / 2))
            drawRoundRect(accent, size = Size(size.width * p, size.height), cornerRadius = CornerRadius(size.height / 2))
        }
        Text("-" + formatTrackTime(track.durationMs - pos), color = CMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun Deck(track: NowPlaying, transport: Transport, accent: Color, keyHeight: Dp) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF1D1C21), Color(0xFF141317))), shape)
            .border(1.dp, Color(0xFF2E2C33), shape)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .drawBehind {
                    if (track.playing) drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.6f), Color.Transparent)), size.minDimension * 1.7f)
                    drawCircle(if (track.playing) accent else Color(0xFF3A3840))
                },
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckKey(stringResource(R.string.nowplaying_previous), "REW", Icons.Rounded.FastRewind, false, transport::previous, keyHeight, Modifier.weight(1f))
            DeckKey(
                stringResource(if (track.playing) R.string.nowplaying_pause else R.string.nowplaying_play),
                if (track.playing) "PAUSE" else "PLAY",
                if (track.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                // The play key latches down while the tape runs, like a real deck.
                track.playing, transport::playPause, keyHeight, Modifier.weight(1f),
            )
            DeckKey(stringResource(R.string.nowplaying_next), "F.FWD", Icons.Rounded.FastForward, false, transport::next, keyHeight, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeckKey(label: String, word: String, icon: ImageVector, down: Boolean, onClick: () -> Unit, height: Dp, modifier: Modifier) {
    val drop by animateDpAsState(if (down) 5.dp else 0.dp, tween(120), label = "key")
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.height(height + 8.dp)) {
        Box(Modifier.fillMaxWidth().height(height).offset(y = 7.dp).background(Color(0xFF4A4740), shape))
        NpButton(
            label, onClick,
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset { IntOffset(0, drop.roundToPx()) }
                .background(Brush.verticalGradient(listOf(Color(0xFFECE9E2), if (down) Color(0xFF9F9B93) else Color(0xFFB4B0A8))), shape),
            shape = shape,
            rippleColor = Color.Black,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, tint = Color(0xFF2B2830), modifier = Modifier.size(24.dp))
                Text(word, color = Color(0xFF2B2830), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun Cassette(track: NowPlaying, p: Float, hubs: State<Float>, accent: Color, width: Dp) {
    val k = width.value / 350f
    val image = track.art?.image
    val shape = RoundedCornerShape((14 * k).dp)
    Box(Modifier.size(width, width * (224f / 350f)).shadow((24 * k).dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape)) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 350f
            // The J-card art seen through a smoked shell.
            if (image != null) {
                drawImage(
                    image, srcOffset = IntOffset.Zero, srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(0, (-63f * u).roundToInt()), dstSize = IntSize(size.width.roundToInt(), size.width.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )
            } else {
                drawRect(accent.copy(alpha = 0.5f))
            }
            drawRect(Brush.linearGradient(listOf(Color(0x8C28262E), Color(0xB8101014)), Offset.Zero, Offset(size.width * 0.3f, size.height)))
            drawRect(
                Brush.linearGradient(0.3f to Color.Transparent, 0.38f to Color(0x1AFFFFFF), 0.46f to Color.Transparent, start = Offset.Zero, end = Offset(size.width, size.height * 0.6f)),
            )

            // Reel window.
            val wx = 62f * u
            val wy = 122f * u
            drawRoundRect(Color(0x8C060608), Offset(wx, wy), Size(226f * u, 66f * u), CornerRadius(8f * u))
            drawRoundRect(Color(0x24FFFFFF), Offset(wx, wy), Size(226f * u, 66f * u), CornerRadius(8f * u), style = Stroke(1f))
            clipPath(Path().apply { addRoundRect(RoundRect(wx, wy, wx + 226f * u, wy + 66f * u, CornerRadius(8f * u))) }) {
                val rMin = 13f * u
                val rMax = 30f * u
                val left = Offset(wx + 56f * u, wy + 33f * u)
                val right = Offset(wx + 170f * u, wy + 33f * u)
                val packs = listOf(left to rMin + (rMax - rMin) * (1f - p), right to rMin + (rMax - rMin) * p)
                drawLine(Color(0xFF3B2A20), Offset(left.x - 18f * u, wy + 60f * u), Offset(right.x + 18f * u, wy + 60f * u), 2f * u)
                for ((c, r) in packs) {
                    drawCircle(Brush.radialGradient(0f to Color(0xFF2A1E17), 0.55f to Color(0xFF4A3528), 1f to Color(0xFF3B2A20), center = c, radius = r), r, c)
                    rotate(hubs.value, c) {
                        drawCircle(CInk, 11f * u, c, style = Stroke(2f * u))
                        for (j in 0 until 6) {
                            rotate(j * 60f, c) { drawRect(CInk, Offset(c.x - 1.5f * u, c.y - 10f * u), Size(3f * u, 4.5f * u)) }
                        }
                    }
                }
            }

            // Lower guide, holes and screws.
            val guide = Path().apply {
                moveTo(78f * u, 224f * u)
                lineTo(94f * u, 198f * u)
                lineTo(256f * u, 198f * u)
                lineTo(272f * u, 224f * u)
                close()
            }
            drawPath(guide, Color(0x12FFFFFF))
            drawPath(guide, Color(0x24FFFFFF), style = Stroke(1f))
            for (x in floatArrayOf(132f, 218f)) drawCircle(Color(0xFF0B0A0D), 5f * u, Offset(x * u, 212f * u))
            for (x in floatArrayOf(104f, 238f)) drawRect(Color(0xFF0B0A0D), Offset(x * u, 206f * u), Size(8f * u, 8f * u))
            for ((x, y) in listOf(11f to 11f, 339f to 11f, 11f to 213f, 339f to 213f, 175f to 118f)) {
                val c = Offset(x * u, y * u)
                drawCircle(Color(0xFF5C5862), 4.5f * u, c)
                drawLine(Color(0xFF2A2830), c - Offset(3f * u, 0f), c + Offset(3f * u, 0f), 1.2f * u)
                drawLine(Color(0xFF2A2830), c - Offset(0f, 3f * u), c + Offset(0f, 3f * u), 1.2f * u)
            }
            drawRoundRect(Color(0x24FFFFFF), size = size, cornerRadius = CornerRadius(14f * u), style = Stroke(1.5f))
        }

        // The label sticker, written by hand.
        val labelShape = RoundedCornerShape((6 * k).dp)
        Box(
            Modifier
                .offset((16 * k).dp, (12 * k).dp)
                .size((318 * k).dp, (100 * k).dp)
                .shadow((2 * k).dp, labelShape)
                .background(CPaper, labelShape)
                .clip(labelShape),
        ) {
            Row(
                Modifier.fillMaxWidth().height((22 * k).dp).background(accent).padding(horizontal = (8 * k).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((8 * k).dp),
            ) {
                Box(Modifier.size((16 * k).dp).background(CPaper, CircleShape), contentAlignment = Alignment.Center) {
                    Text("A", color = Color(0xFF2B2420), fontSize = 10f.scaledSp(k), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Text("SIDE A · C60 · NR", color = Color(0xFF2B2420), fontSize = 9f.scaledSp(k), fontWeight = FontWeight.Bold, letterSpacing = 2f.scaledSp(k), fontFamily = FontFamily.Monospace)
            }
            Box(Modifier.offset(y = (46 * k).dp).padding(horizontal = (10 * k).dp).fillMaxWidth().height(1.dp).background(Color(0x2E2B2420)))
            Box(Modifier.offset(y = (72 * k).dp).padding(horizontal = (10 * k).dp).fillMaxWidth().height(1.dp).background(Color(0x2E2B2420)))
            Text(
                track.title.lowercase(), color = Color(0xFF2B2420), fontFamily = PermanentMarker, fontSize = 23f.scaledSp(k),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset((12 * k).dp, (20 * k).dp).width((292 * k).dp).rotate(-1.5f),
            )
            val by = listOfNotNull(track.artist, track.album?.takeIf { it != track.title }).joinToString(" · ")
            Text(
                by, color = Color(0xFF4A3F5E), fontFamily = PermanentMarker, fontSize = 13f.scaledSp(k), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset((14 * k).dp, (52 * k).dp).width((290 * k).dp).rotate(-1f),
            )
        }
    }
}
