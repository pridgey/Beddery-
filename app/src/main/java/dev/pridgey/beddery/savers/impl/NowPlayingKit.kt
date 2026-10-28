package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.media.NowPlaying
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.formatClock
import kotlinx.coroutines.delay
import kotlin.random.Random

// Shared pieces for the Now Playing styles.

@OptIn(ExperimentalTextApi::class)
internal val Caveat = FontFamily(
    Font(R.font.caveat, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.caveat, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
internal val Anton = FontFamily(Font(R.font.anton))
internal val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif),
    Font(R.font.instrument_serif_italic, style = FontStyle.Italic),
)
internal val PermanentMarker = FontFamily(Font(R.font.permanent_marker))

/** Where the track is now, refreshed four times a second while it plays. */
@Composable
internal fun rememberPositionMs(track: NowPlaying): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(track.playing, track.positionAt, track.positionMs) {
        now = SystemClock.elapsedRealtime()
        while (track.playing) {
            delay(250)
            now = SystemClock.elapsedRealtime()
        }
    }
    return track.positionNow(now)
}

internal fun NowPlaying.fraction(position: Long): Float =
    if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

/**
 * An angle in degrees that turns while [running] and holds still otherwise, so paused records
 * and reels stop where they are. Read it while drawing, so only drawing repeats.
 */
@Composable
internal fun rememberSpin(running: Boolean, degreesPerSecond: Float): State<Float> {
    val angle = remember { mutableFloatStateOf(0f) }
    val speed by rememberUpdatedState(degreesPerSecond)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { t ->
                angle.floatValue = (angle.floatValue + (t - last) / 1e9f * speed) % 3600f
                last = t
            }
        }
    }
    return angle
}

/** Time in seconds that only moves while [running]: drives slow drifts that should rest when paused. */
@Composable
internal fun rememberClock(running: Boolean): State<Float> = rememberSpin(running, 1f)

/** A tappable area for the styles' own buttons: ripple, a small press squish, and a spoken label. */
@Composable
internal fun NpButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    rippleColor: Color = Color.White,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f), label = "press")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .clickable(press, ripple(color = rippleColor), enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
internal fun PlayPauseIcon(playing: Boolean, tint: Color, size: Dp) {
    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = tint, modifier = Modifier.size(size))
}

internal fun clockParts(ctx: SaverContext): Pair<String, String?> {
    val t = ctx.now.toLocalTime()
    return formatClock(t, ctx.use24h) to if (ctx.use24h) null else amPm(t)
}

/** Sizes taken from a 390-wide mockup, scaled to the space at hand. */
@Composable
internal fun Float.scaledSp(k: Float): TextUnit = with(LocalDensity.current) { (this@scaledSp * k).dp.toSp() }

internal fun Float.scaledDp(k: Float): Dp = (this * k).dp

/** Gentle film fade: a little less colour, a touch warmer. */
internal val FilmFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(0.84f)
        timesAssign(ColorMatrix(floatArrayOf(1.05f, 0f, 0f, 0f, 6f, 0f, 1.0f, 0f, 0f, 2f, 0f, 0f, 0.9f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    },
)

internal fun grayscale(amount: Float, brightness: Float): ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply {
        setToSaturation(1f - amount.coerceIn(0f, 1f))
        timesAssign(ColorMatrix(floatArrayOf(brightness, 0f, 0f, 0f, 0f, 0f, brightness, 0f, 0f, 0f, 0f, 0f, brightness, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
    },
)

/** A small tile of soft random specks, drawn repeated as film grain or paper texture. */
internal object Grain {
    val tile: ImageBitmap by lazy {
        val n = 128
        val rnd = Random(7)
        val px = IntArray(n * n) {
            val v = rnd.nextInt(256)
            val a = rnd.nextInt(256)
            (a shl 24) or (v shl 16) or (v shl 8) or v
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
