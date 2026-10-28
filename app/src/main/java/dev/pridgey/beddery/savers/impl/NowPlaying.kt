package dev.pridgey.beddery.savers.impl

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import dev.pridgey.beddery.R
import dev.pridgey.beddery.media.Art
import dev.pridgey.beddery.media.ArtColors
import dev.pridgey.beddery.media.NowPlaying
import dev.pridgey.beddery.media.NowPlayingStore
import dev.pridgey.beddery.media.rememberNowPlaying
import dev.pridgey.beddery.notifications.NotificationItem
import dev.pridgey.beddery.notifications.NotificationStore
import dev.pridgey.beddery.savers.AppGlyph
import dev.pridgey.beddery.savers.CountedGlyph
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.TabularNumbers
import dev.pridgey.beddery.savers.amPm
import dev.pridgey.beddery.savers.formatClock
import dev.pridgey.beddery.savers.formatLongDate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay

private val Ground = Color(0xFF09080E)
private val NoArt = Color(0xFF2A2547)

internal interface Transport {
    fun playPause()
    fun next()
    fun previous()
}

internal object LiveTransport : Transport {
    override fun playPause() = NowPlayingStore.playPause()
    override fun next() = NowPlayingStore.next()
    override fun previous() = NowPlayingStore.previous()
}

/**
 * Album art, the track and simple controls for whatever the phone is playing. Previews play a
 * pretend album so the screen can be seen without music on.
 */
@Composable
fun NowPlayingSaver(ctx: SaverContext) {
    val live = rememberNowPlaying(enabled = !ctx.preview)
    val demo = if (live == null && ctx.demo) remember { DemoPlayer() } else null
    val track = live ?: demo?.track
    val transport: Transport = if (live == null && demo != null) demo else LiveTransport

    Box(Modifier.fillMaxSize().background(Ground)) {
        if (track == null) {
            NothingPlaying(ctx)
            return@Box
        }
        when (ctx.settings.nowPlayingStyle) {
            "vinyl" -> VinylStyle(ctx, track, transport)
            "cassette" -> CassetteStyle(ctx, track, transport)
            "aura" -> AuraStyle(ctx, track, transport)
            "polaroid" -> PolaroidStyle(ctx, track, transport)
            "poster" -> PosterStyle(ctx, track, transport)
            "mosaic" -> MosaicStyle(ctx, track, transport)
            "line" -> OneLineStyle(ctx, track, transport)
            else -> ClassicStyle(ctx, track, transport)
        }
    }
}

/** The Now Playing looks, in the order the settings show them. */
val NowPlayingStyles: List<Pair<String, Int>> = listOf(
    "classic" to R.string.np_style_classic,
    "vinyl" to R.string.np_style_vinyl,
    "cassette" to R.string.np_style_cassette,
    "aura" to R.string.np_style_aura,
    "polaroid" to R.string.np_style_polaroid,
    "poster" to R.string.np_style_poster,
    "mosaic" to R.string.np_style_mosaic,
    "line" to R.string.np_style_line,
)

/** Blurred art behind a large cover, with the track and controls below. */
@Composable
private fun ClassicStyle(ctx: SaverContext, track: NowPlaying, transport: Transport) {
    val notices = rememberNotices(ctx)
    Box(Modifier.fillMaxSize()) {
        Backdrop(track.art, still = ctx.preview)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth > maxHeight) {
                val art = min(maxHeight - 72.dp, maxWidth * 0.42f)
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtCard(track, art)
                    Spacer(Modifier.width(48.dp))
                    Column(Modifier.weight(1f).height(art)) {
                        Clock(ctx, 36.sp)
                        ClassicNotices(notices, Modifier.padding(top = 12.dp))
                        Spacer(Modifier.weight(1f))
                        TrackInfo(track, titleSize = 30.sp, showAlbum = true)
                        Spacer(Modifier.height(22.dp))
                        Progress(track)
                        Spacer(Modifier.height(18.dp))
                        Controls(track, transport, Modifier.offset(x = (-12).dp), play = 68.dp)
                    }
                }
            } else {
                val art = min(maxWidth - 64.dp, maxHeight * 0.42f)
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Clock(ctx, 40.sp, Modifier.width(art))
                    ClassicNotices(notices, Modifier.width(art).padding(top = 12.dp))
                    Spacer(Modifier.weight(1f))
                    ArtCard(track, art)
                    Spacer(Modifier.height(36.dp))
                    Column(Modifier.width(art)) {
                        TrackInfo(track, titleSize = 26.sp, showAlbum = false)
                        Spacer(Modifier.height(26.dp))
                        Progress(track)
                    }
                    Spacer(Modifier.height(22.dp))
                    Controls(track, transport, play = 76.dp)
                    Spacer(Modifier.weight(1.2f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Pieces

/** App icons under the clock; when something arrives, its icon widens into a pill with the message. */
@Composable
private fun ClassicNotices(notices: Notices, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf<NotificationItem?>(null) }
    notices.arrival?.let { last = it }
    val arrivingApp = notices.arrival?.packageName
    Row(modifier.height(34.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            notices.arrival != null,
            enter = fadeIn(tween(400)) + expandHorizontally(spring(stiffness = 300f), expandFrom = Alignment.Start),
            exit = fadeOut(tween(500)) + shrinkHorizontally(tween(600), shrinkTowards = Alignment.Start),
        ) {
            last?.let { NoticePill(notices, it) }
        }
        notices.apps.take(5).forEach { app ->
            // The arriving app's icon is the pill for now.
            AnimatedVisibility(
                app.packageName != arrivingApp,
                enter = fadeIn(tween(600)) + expandHorizontally(tween(500)),
                exit = fadeOut(tween(300)) + shrinkHorizontally(tween(400)),
            ) {
                Box(Modifier.padding(end = 6.dp)) { CountedGlyph(app, 26.dp) }
            }
        }
    }
}

@Composable
private fun NoticePill(notices: Notices, item: NotificationItem) {
    val (head, body) = noticeLines(notices, item)
    Row(
        Modifier
            .widthIn(max = 320.dp)
            .height(34.dp)
            .background(Color(0xB80C101A), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            .padding(start = 4.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppGlyph(null, item, 26.dp)
        Text(head, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
        if (body != null) Text(body, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The art's tiny blurred copy, stretched past the edges and drifting slowly, under a dark wash. */
@Composable
private fun Backdrop(art: Art?, still: Boolean) {
    val drift = rememberInfiniteTransition(label = "drift")
    val phase by drift.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(120_000, easing = LinearEasing)), label = "phase")
    Crossfade(art, Modifier.fillMaxSize(), animationSpec = tween(1500), label = "backdrop") { a ->
        Canvas(Modifier.fillMaxSize()) {
            if (a == null) {
                drawRect(Brush.linearGradient(listOf(NoArt, Ground), Offset.Zero, Offset(size.width, size.height)))
                return@Canvas
            }
            val p = if (still) 0.6f else phase
            val cover = max(size.width, size.height) * 1.6f
            translate(cos(p) * size.width * 0.06f, sin(p * 2) * size.height * 0.03f) {
                rotate(sin(p) * 10f) {
                    drawImage(
                        a.backdrop,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(a.backdrop.width, a.backdrop.height),
                        dstOffset = IntOffset((center.x - cover / 2).toInt(), (center.y - cover / 2).toInt()),
                        dstSize = IntSize(cover.toInt(), cover.toInt()),
                        filterQuality = FilterQuality.Low,
                    )
                }
            }
        }
    }
    // Keeps white text readable on bright covers, darkest where the controls sit.
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.38f), 0.45f to Color.Black.copy(alpha = 0.42f), 1f to Color.Black.copy(alpha = 0.78f)),
        ),
    )
}

@Composable
private fun ArtCard(track: NowPlaying, size: Dp) {
    // Paused art settles back a little, the way a record player's platter stops.
    val scale by animateFloatAsState(if (track.playing) 1f else 0.9f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessVeryLow), label = "art")
    val shape = RoundedCornerShape(size * 0.05f)
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(32.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(track.art?.shade ?: NoArt),
    ) {
        Crossfade(track.art, Modifier.fillMaxSize(), animationSpec = tween(900), label = "art") { a ->
            if (a != null) {
                Image(a.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(size * 0.3f))
                }
            }
        }
        Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.08f), shape))
    }
}

@Composable
private fun Clock(ctx: SaverContext, size: TextUnit, modifier: Modifier = Modifier) {
    val t = ctx.now.toLocalTime()
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatClock(t, ctx.use24h), color = Color.White.copy(alpha = 0.92f), fontSize = size, fontWeight = FontWeight.Light, style = TabularNumbers)
            if (!ctx.use24h) {
                Text(amPm(t), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, modifier = Modifier.padding(start = 5.dp, bottom = 7.dp))
            }
        }
        Text(formatLongDate(ctx.now.toLocalDate()), color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackInfo(track: NowPlaying, titleSize: TextUnit, showAlbum: Boolean) {
    val accent = track.art?.accent ?: Color(0xFFBBA6FF)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Equalizer(track.playing, accent)
            Spacer(Modifier.width(10.dp))
            Text(
                if (track.playing) track.appName.uppercase() else stringResource(R.string.nowplaying_paused, track.appName).uppercase(),
                color = accent.copy(alpha = 0.9f), fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        // Long titles scroll slowly instead of being cut off.
        Text(
            track.title, color = Color.White, fontSize = titleSize, fontWeight = FontWeight.SemiBold, maxLines = 1,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 4000, repeatDelayMillis = 6000, velocity = 24.dp),
        )
        track.artist?.let {
            Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = titleSize * 0.65f, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (showAlbum) {
            track.album?.takeIf { it != track.title }?.let {
                Text(it, color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Three little bars that dance while music plays and lie flat when paused. */
@Composable
private fun Equalizer(playing: Boolean, color: Color) {
    val t = rememberInfiniteTransition(label = "eq")
    val bars = listOf(520, 380, 610).mapIndexed { i, ms ->
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms, delayMillis = i * 90), RepeatMode.Reverse), label = "bar$i")
    }
    val on by animateFloatAsState(if (playing) 1f else 0f, tween(500), label = "eqOn")
    Canvas(Modifier.size(width = 14.dp, height = 12.dp)) {
        val w = size.width / 5
        bars.forEachIndexed { i, bar ->
            val h = size.height * (0.22f + (bar.value - 0.22f) * on)
            drawRoundRect(color, Offset(i * 2 * w, size.height - h), Size(w, h), CornerRadius(w / 2))
        }
    }
}

@Composable
private fun Progress(track: NowPlaying) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(track.playing, track.positionAt) {
        now = SystemClock.elapsedRealtime()
        while (track.playing) {
            delay(250)
            now = SystemClock.elapsedRealtime()
        }
    }
    val duration = track.durationMs
    // Live radio and streams have no length: no bar.
    if (duration <= 0) return
    val position = track.positionNow(now)
    val fraction = (position.toFloat() / duration).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = r)
            drawRoundRect(Color.White.copy(alpha = 0.92f), size = Size(size.width * fraction, size.height), cornerRadius = r)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(formatTrackTime(position), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, style = TabularNumbers)
            Spacer(Modifier.weight(1f))
            Text("-" + formatTrackTime(duration - position), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, style = TabularNumbers)
        }
    }
}

internal fun formatTrackTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0) + 500) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Controls(track: NowPlaying, transport: Transport, modifier: Modifier = Modifier, play: Dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        SkipButton(Icons.Rounded.SkipPrevious, stringResource(R.string.nowplaying_previous), track.canPrevious) { transport.previous() }
        PlayButton(track, play) { transport.playPause() }
        SkipButton(Icons.Rounded.SkipNext, stringResource(R.string.nowplaying_next), track.canNext) { transport.next() }
    }
}

@Composable
private fun SkipButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, spring(dampingRatio = 0.5f), label = "skip")
    Box(
        Modifier
            .size(60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(press, ripple(color = Color.White), enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 0.95f else 0.3f), modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun PlayButton(track: NowPlaying, size: Dp, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f), label = "play")
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(16.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(press, ripple(color = Color.Black), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(track.playing, animationSpec = tween(200), label = "playIcon") { playing ->
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (playing) R.string.nowplaying_pause else R.string.nowplaying_play),
                tint = track.art?.shade ?: NoArt,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

@Composable
private fun NothingPlaying(ctx: SaverContext) {
    val context = LocalContext.current
    val access = remember(ctx.now.minute) { NotificationStore.hasAccess(context) }
    Box(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF1A1730), Ground), radius = 1400f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 40.dp)) {
            val t = ctx.now.toLocalTime()
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatClock(t, ctx.use24h), color = Color.White.copy(alpha = 0.9f), fontSize = 64.sp, fontWeight = FontWeight.Light, style = TabularNumbers)
                if (!ctx.use24h) Text(amPm(t), color = Color.White.copy(alpha = 0.55f), fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
            }
            Text(formatLongDate(ctx.now.toLocalDate()), color = Color.White.copy(alpha = 0.55f), fontSize = 15.sp)
            Spacer(Modifier.height(48.dp))
            Box(Modifier.size(56.dp).background(Color.White.copy(alpha = 0.07f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.nowplaying_nothing), color = Color.White.copy(alpha = 0.85f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text(
                stringResource(if (access) R.string.nowplaying_nothing_sub else R.string.nowplaying_no_access),
                color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------- Preview album

/** A pretend album for previews, with working buttons. */
@Stable
private class DemoPlayer : Transport {
    private val tracks = DemoAlbum.tracks
    private var index by mutableIntStateOf(0)
    private var playing by mutableStateOf(true)
    private var position by mutableLongStateOf(tracks[0].positionMs)
    private var since by mutableLongStateOf(SystemClock.elapsedRealtime())

    val track: NowPlaying get() = tracks[index].copy(playing = playing, positionMs = position, positionAt = since)

    override fun playPause() {
        val now = SystemClock.elapsedRealtime()
        position = track.positionNow(now)
        since = now
        playing = !playing
    }

    override fun next() = jump(1)

    override fun previous() {
        // Like most players: back to the start first, then the track before.
        if (track.positionNow() > 3_000) {
            position = 0
            since = SystemClock.elapsedRealtime()
        } else jump(-1)
    }

    private fun jump(by: Int) {
        index = (index + by + tracks.size) % tracks.size
        position = 0
        since = SystemClock.elapsedRealtime()
        playing = true
    }
}

/** Made-up covers, painted in code: no image files and nobody's real album. */
private object DemoAlbum {
    val tracks: List<NowPlaying> by lazy {
        listOf(
            demo("Slow Tide", "Harbor Lights", "Low Water", 214_000, 71_000, cover(0xFF0E6B78, 0xFF0A1B3A, 0xFFF4E3C1, Motif.MOON)),
            demo("Paper Lanterns", "June Atlas", "Night Market", 187_000, 0, cover(0xFFF08A4B, 0xFF7A1F5C, 0xFFFFE2A8, Motif.LANTERNS)),
            demo("Northbound", "The Quiet Hours", "Long Way Home", 243_000, 0, cover(0xFF6B4BC8, 0xFFE07A9A, 0xFF1D1440, Motif.RIDGES)),
        )
    }

    private enum class Motif { MOON, LANTERNS, RIDGES }

    private fun demo(title: String, artist: String, album: String, duration: Long, at: Long, art: Art) = NowPlaying(
        packageName = "dev.pridgey.beddery", appName = "Beddery", title = title, artist = artist, album = album, art = art,
        playing = true, durationMs = duration, positionMs = at, positionAt = SystemClock.elapsedRealtime(), speed = 1f,
        canPrevious = true, canNext = true,
    )

    private fun cover(top: Long, bottom: Long, ink: Long, motif: Motif): Art {
        val s = 512f
        val bmp = Bitmap.createBitmap(s.toInt(), s.toInt(), Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, s * 0.3f, s, top.toInt(), bottom.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s, p)
        p.shader = null
        p.color = ink.toInt()
        when (motif) {
            Motif.MOON -> {
                c.drawCircle(s * 0.62f, s * 0.36f, s * 0.17f, p)
                p.alpha = 70
                for (i in 0 until 7) {
                    val y = s * (0.62f + i * 0.05f)
                    c.drawRoundRect(s * (0.18f + i * 0.03f), y, s * (0.86f - i * 0.02f), y + s * 0.012f, 6f, 6f, p)
                }
            }
            Motif.LANTERNS -> {
                p.strokeWidth = 3f
                for (i in 0 until 5) {
                    val x = s * (0.16f + i * 0.17f)
                    val y = s * (0.3f + (i % 2) * 0.1f + i * 0.04f)
                    p.alpha = 90
                    c.drawLine(x, 0f, x, y, p)
                    p.alpha = 255
                    c.drawOval(x - s * 0.06f, y, x + s * 0.06f, y + s * 0.15f, p)
                }
            }
            Motif.RIDGES -> {
                for (i in 0 until 4) {
                    p.alpha = 90 + i * 55
                    val path = android.graphics.Path()
                    val base = s * (0.5f + i * 0.13f)
                    path.moveTo(0f, s)
                    path.lineTo(0f, base)
                    var x = 0f
                    var up = i % 2 == 0
                    while (x < s) {
                        x += s * (0.14f + 0.05f * ((i + x.toInt() / 40) % 3))
                        path.lineTo(x, base + if (up) -s * 0.1f else s * 0.02f)
                        up = !up
                    }
                    path.lineTo(s, s)
                    path.close()
                    c.drawPath(path, p)
                }
            }
        }
        return ArtColors.make("demo-${motif.name}", bmp, 512)
    }
}
