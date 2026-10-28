package dev.pridgey.beddery.savers.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.media.NowPlaying
import dev.pridgey.beddery.savers.SaverContext
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val LText = Color(0xFFECE8E0)
private val LMuted = Color(0xFF8A857B)
private val LIcon = Color(0xFFB8B2A7)

/** One sentence on true black, drifting slowly so nothing stays in one place on the screen. */
@Composable
internal fun OneLineStyle(ctx: SaverContext, track: NowPlaying, transport: Transport) {
    val accent = track.art?.accent ?: Color(0xFF6FD0D4)
    val pos = rememberPositionMs(track)
    val p = track.fraction(pos)
    val clock = rememberClock(!ctx.preview)
    val dim by animateFloatAsState(if (track.playing) 1f else 0.5f, tween(1200), label = "dim")
    val (time, _) = clockParts(ctx)
    val notices = rememberNotices(ctx)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 632.dp)
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .offset {
                    // A two-minute wander of a few dozen pixels.
                    val t = clock.value * 2f * PI.toFloat() / 120f
                    IntOffset((10.dp.toPx() * sin(t)).roundToInt(), (-34.dp.toPx() * sin(t * 2f + 0.6f)).roundToInt())
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(7.dp).graphicsLayer { alpha = if (track.playing) 1f else 0.4f }.background(accent, CircleShape))
                Text(time, color = LMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
            }
            Text(
                buildAnnotatedString {
                    append(track.title)
                    track.artist?.let { withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color(0xFF9A948A))) { append(", by $it") } }
                },
                color = LText, fontFamily = InstrumentSerif, fontSize = 40.sp, lineHeight = 1.12.em, letterSpacing = (-0.3).sp,
                modifier = Modifier.padding(top = 18.dp).graphicsLayer { alpha = dim },
            )
            if (track.durationMs > 0) {
                Canvas(Modifier.padding(top = 22.dp).fillMaxWidth().height(5.dp)) {
                    val y = size.height / 2
                    drawLine(Color(0xFF24221E), Offset(0f, y), Offset(size.width, y), 1f)
                    drawLine(LText, Offset(0f, y), Offset(size.width * p, y), 1f)
                    drawCircle(accent, 2.5.dp.toPx(), Offset(size.width * p, y))
                }
            }
            Text(
                progressWords(track, pos),
                color = LMuted, fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            // While notifications wait, a line of their own names the apps by their icons.
            AnimatedVisibility(notices.waiting, enter = fadeIn(tween(900)) + expandVertically(tween(600)), exit = fadeOut(tween(600)) + shrinkVertically(tween(600))) {
                WaitingLine(notices, accent)
            }
            // A new one gets a sentence of its own.
            ArrivalCard(notices) { item ->
                val reveal = notices.reveals(item)
                Text(
                    buildAnnotatedString {
                        append("Just now, ")
                        if (!reveal) append("something new in ")
                        appendInlineContent(ARRIVAL_ICON)
                        withStyle(SpanStyle(color = accent)) { append(if (reveal) item.title ?: item.appName else item.appName) }
                        if (reveal) {
                            item.text?.let { withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(": \u201C$it\u201D") } }
                        } else {
                            append(".")
                        }
                    },
                    inlineContent = mapOf(ARRIVAL_ICON to inlineGlyph(1.05f) { NoticeGlyph(item, null, accent, 19.dp) }),
                    color = LText, fontFamily = InstrumentSerif, fontSize = 24.sp, lineHeight = 1.2.em, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NpButton(stringResource(R.string.nowplaying_previous), transport::previous, Modifier.size(52.dp), enabled = track.canPrevious) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = Color(0xFFA39D92), modifier = Modifier.size(24.dp))
            }
            NpButton(stringResource(if (track.playing) R.string.nowplaying_pause else R.string.nowplaying_play), transport::playPause, Modifier.size(56.dp)) {
                PlayPauseIcon(track.playing, LText, 28.dp)
            }
            NpButton(stringResource(R.string.nowplaying_next), transport::next, Modifier.size(52.dp), enabled = track.canNext) {
                Icon(Icons.Rounded.SkipNext, null, tint = Color(0xFFA39D92), modifier = Modifier.size(24.dp))
            }
        }
    }
}

private const val ARRIVAL_ICON = "arrival"
private const val MAX_INLINE_ICONS = 4

/** "four notifications are waiting, from [chat] [mail] [calendar]" */
@Composable
private fun WaitingLine(notices: Notices, accent: Color) {
    val apps = notices.apps.take(MAX_INLINE_ICONS)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = accent)) { append(sayCount(notices.count)) }
            append(if (notices.count == 1) " is waiting, from " else " are waiting, from ")
            apps.forEachIndexed { i, _ -> appendInlineContent("app$i") }
            if (notices.apps.size > apps.size) append(" and others")
        },
        inlineContent = apps.withIndex().associate { (i, app) -> "app$i" to inlineGlyph(1.35f) { NoticeGlyph(null, app, LIcon, 14.dp) } },
        color = LMuted, fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontSize = 15.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Room in a line of text for an icon, [width] ems wide, centred on the letters. */
private fun inlineGlyph(width: Float, glyph: @Composable () -> Unit) = InlineTextContent(
    Placeholder(width.em, 1.em, PlaceholderVerticalAlign.TextCenter),
) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { glyph() } }

/** "a minute and a half in, two minutes to go" */
internal fun progressWords(track: NowPlaying, pos: Long): String {
    val inSec = pos / 1000
    if (!track.playing) return "paused, ${sayDuration(inSec)} in"
    if (track.durationMs <= 0) return "${sayDuration(inSec)} in"
    val left = (track.durationMs - pos) / 1000
    return when {
        inSec < 8 -> "just beginning, ${sayDuration(left)} to go"
        left < 15 -> "nearly over"
        else -> "${sayDuration(inSec)} in, ${sayDuration(left)} to go"
    }
}

/** Durations the way a person would say them. */
internal fun sayDuration(seconds: Long): String {
    val words = arrayOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
    return when {
        seconds < 20 -> "a few seconds"
        seconds < 45 -> "half a minute"
        seconds < 75 -> "a minute"
        seconds < 105 -> "a minute and a half"
        else -> {
            val halves = ((seconds + 15) / 30).toInt()
            val whole = halves / 2
            val w = if (whole <= 10) words[whole] else whole.toString()
            w + (if (halves % 2 == 1) " and a half" else "") + " minutes"
        }
    }
}
