package dev.pridgey.beddery.media

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.beddery.notifications.BedderyNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** What's playing on the phone right now, read from the active media session. */
@Immutable
data class NowPlaying(
    val packageName: String,
    val appName: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val art: Art?,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() when [positionMs] was measured. */
    val positionAt: Long,
    val speed: Float,
    val canPrevious: Boolean,
    val canNext: Boolean,
) {
    fun positionNow(now: Long = SystemClock.elapsedRealtime()): Long {
        if (positionMs < 0) return 0
        val p = if (playing) positionMs + ((now - positionAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** Album art plus the colours and soft backdrop drawn from it. */
@Immutable
class Art(
    val key: String,
    val image: ImageBitmap,
    /** A tiny, pre-blurred copy for the background. */
    val backdrop: ImageBitmap,
    /** The art's most vivid colour, lifted so it reads on dark. */
    val accent: Color,
    /** A deep shade of the art's overall colour. */
    val shade: Color,
)

@SuppressLint("StaticFieldLeak")
/**
 * Watches the phone's media sessions. Needs notification access (the same permission the
 * notification icons use); without it, nothing is playing as far as Beddery can tell.
 */
object NowPlayingStore {
    private const val TAG = "BedderyNowPlaying"
    private const val ART_PX = 720

    private val state = MutableStateFlow<NowPlaying?>(null)
    val current: StateFlow<NowPlaying?> = state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchers = 0
    // Only ever the application context, which lives as long as the app.
    @SuppressLint("StaticFieldLeak")
    private var app: Context? = null
    private var manager: MediaSessionManager? = null
    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null
    private var artKey: String? = null
    private var art: Art? = null
    private var artJob: Job? = null
    private val labels = HashMap<String, String>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> onSessions(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pick()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pick()
        override fun onSessionDestroyed() = pick()
    }

    @MainThread
    fun watch(context: Context) {
        if (watchers++ == 0) start(context.applicationContext)
    }

    @MainThread
    fun unwatch() {
        if (watchers > 0 && --watchers == 0) stop()
    }

    /** Notification access was just granted or the listener reconnected: try again. */
    @MainThread
    fun reconnect() {
        val ctx = app ?: return
        if (watchers > 0 && manager == null) start(ctx)
    }

    fun playPause() = control { c ->
        if (c.playbackState?.state.isPlaying()) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = control { it.transportControls.skipToNext() }

    fun previous() = control { it.transportControls.skipToPrevious() }

    private inline fun control(action: (MediaController) -> Unit) {
        val c = active ?: return
        try {
            action(c)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Media control failed", e)
        }
    }

    private fun start(ctx: Context) {
        app = ctx
        val m = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(ctx, BedderyNotificationListener::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            manager = m
            onSessions(m.getActiveSessions(component))
        } catch (e: SecurityException) {
            // No notification access yet.
            manager = null
            state.value = null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't read media sessions", e)
            manager = null
        }
    }

    private fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        manager = null
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
        active = null
        artJob?.cancel()
        artKey = null
        art = null
        state.value = null
    }

    private fun onSessions(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { runCatching { it.registerCallback(callback, main) } }
        pick()
    }

    /** The session that's playing; otherwise the one we were showing; otherwise the most recent with a track. */
    private fun pick() {
        val playing = controllers.firstOrNull { it.playbackState?.state.isPlaying() && it.metadata != null }
        val previous = active?.let { a -> controllers.firstOrNull { it.sessionToken == a.sessionToken } }
        val chosen = playing ?: previous?.takeIf { it.metadata != null } ?: controllers.firstOrNull { it.metadata != null }
        active = chosen
        publish(chosen)
    }

    private fun publish(c: MediaController?) {
        val md = c?.metadata
        val title = md?.text(MediaMetadata.METADATA_KEY_TITLE) ?: md?.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (c == null || md == null || title == null) {
            state.value = null
            return
        }
        val artist = md.text(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.text(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val album = md.text(MediaMetadata.METADATA_KEY_ALBUM)
        loadArt(md, c.packageName, album, artist)

        val ps = c.playbackState
        val actions = ps?.actions ?: 0L
        state.value = NowPlaying(
            packageName = c.packageName,
            appName = appName(c.packageName),
            title = title,
            artist = artist,
            album = album,
            art = art,
            playing = ps?.state.isPlaying(),
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = ps?.position ?: -1,
            positionAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            canPrevious = ps == null || actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canNext = ps == null || actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
        )
    }

    /** Decodes and colours new art off the main thread; keeps the old art until the new one is ready. */
    private fun loadArt(md: MediaMetadata, pkg: String, album: String?, artist: String?) {
        val bitmap = md.bitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.bitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.bitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val uri = if (bitmap == null) {
            md.text(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: md.text(MediaMetadata.METADATA_KEY_ART_URI)
                ?: md.text(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        } else null
        // Apps often send the same art again, or a small copy before the full one: key on the size too.
        val key = "$pkg|$album|$artist|${bitmap?.width}x${bitmap?.height}|$uri"
        if (key == artKey) return
        artKey = key
        artJob?.cancel()
        if (bitmap == null && uri == null) {
            art = null
            return
        }
        val resolver = app?.contentResolver
        artJob = scope.launch {
            val made = withContext(Dispatchers.Default) {
                runCatching {
                    val source = bitmap ?: resolver?.let { decodeUri(it, uri!!) }
                    source?.let { ArtColors.make(key, it, ART_PX) }
                }.onFailure { Log.w(TAG, "Couldn't read album art", it) }.getOrNull()
            }
            if (artKey == key) {
                art = made
                publish(active)
            }
        }
    }

    private fun decodeUri(resolver: ContentResolver, s: String): Bitmap? {
        val uri = Uri.parse(s)
        if (uri.scheme !in setOf(ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_FILE, ContentResolver.SCHEME_ANDROID_RESOURCE)) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ART_PX) sample *= 2
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }

    private fun appName(pkg: String): String = labels.getOrPut(pkg) {
        val pm = app?.packageManager
        runCatching { pm!!.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString() }.getOrNull()
            ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    private fun MediaMetadata.text(key: String): String? =
        runCatching { getText(key)?.toString() }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun MediaMetadata.bitmap(key: String): Bitmap? = runCatching { getBitmap(key) }.getOrNull()
}

private fun Int?.isPlaying(): Boolean = when (this) {
    PlaybackState.STATE_PLAYING, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_FAST_FORWARDING,
    PlaybackState.STATE_REWINDING, PlaybackState.STATE_SKIPPING_TO_NEXT, PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
    PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM, PlaybackState.STATE_CONNECTING,
    -> true
    else -> false
}

/** Follows the phone's media while composed. Returns null when nothing is playing (or when [enabled] is false). */
@Composable
fun rememberNowPlaying(enabled: Boolean = true): NowPlaying? {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        if (enabled) NowPlayingStore.watch(context)
        onDispose { if (enabled) NowPlayingStore.unwatch() }
    }
    val np by NowPlayingStore.current.collectAsStateWithLifecycle()
    return if (enabled) np else null
}

/** Turns a bitmap into [Art]: a scaled copy, a blurred backdrop and two colours. Pure Android graphics, no Compose state. */
object ArtColors {
    private const val BACKDROP_PX = 40

    fun make(key: String, source: Bitmap, maxPx: Int): Art {
        val soft = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
        val scale = maxPx.toFloat() / max(soft.width, soft.height)
        val image = if (scale < 1f) {
            Bitmap.createScaledBitmap(soft, (soft.width * scale).toInt().coerceAtLeast(1), (soft.height * scale).toInt().coerceAtLeast(1), true)
        } else soft.copy(Bitmap.Config.ARGB_8888, false)

        val tiny = Bitmap.createScaledBitmap(image, BACKDROP_PX, BACKDROP_PX, true)
        val px = IntArray(BACKDROP_PX * BACKDROP_PX)
        tiny.getPixels(px, 0, BACKDROP_PX, 0, 0, BACKDROP_PX, BACKDROP_PX)
        val (accent, shade) = colours(px)
        // Three box-blur passes look close to a Gaussian, so the stretched backdrop has no blocky edges.
        repeat(3) { boxBlur(px, BACKDROP_PX, 3) }
        val backdrop = Bitmap.createBitmap(px, BACKDROP_PX, BACKDROP_PX, Bitmap.Config.ARGB_8888)
        return Art(key, image.asImageBitmap(), backdrop.asImageBitmap(), accent, shade)
    }

    /** The most vivid colour (lifted for dark backgrounds) and a deep shade of the average. */
    internal fun colours(px: IntArray): Pair<Color, Color> {
        val hsv = FloatArray(3)
        var best = -1f
        var bestHue = 0f
        var bestSat = 0f
        var r = 0L
        var g = 0L
        var b = 0L
        for (c in px) {
            val cr = c shr 16 and 0xFF
            val cg = c shr 8 and 0xFF
            val cb = c and 0xFF
            r += cr; g += cg; b += cb
            android.graphics.Color.RGBToHSV(cr, cg, cb, hsv)
            // Favour saturated colours that aren't nearly black.
            val score = hsv[1] * (0.35f + hsv[2]) * if (hsv[2] < 0.2f) 0.2f else 1f
            if (score > best) {
                best = score
                bestHue = hsv[0]
                bestSat = hsv[1]
            }
        }
        val n = px.size.coerceAtLeast(1)
        android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        val avgHue = hsv[0]
        val avgSat = hsv[1]
        val accent = if (bestSat < 0.18f) {
            Color(0xFFE8E6F0) // A greyscale cover: a soft white accent.
        } else {
            Color(android.graphics.Color.HSVToColor(floatArrayOf(bestHue, bestSat.coerceIn(0.35f, 0.7f), 0.95f)))
        }
        val shade = Color(android.graphics.Color.HSVToColor(floatArrayOf(avgHue, (avgSat * 1.1f).coerceIn(0.1f, 0.65f), 0.2f)))
        return accent to shade
    }

    private fun boxBlur(px: IntArray, size: Int, radius: Int) {
        val tmp = IntArray(px.size)
        blurPass(px, tmp, size, radius, horizontal = true)
        blurPass(tmp, px, size, radius, horizontal = false)
    }

    private fun blurPass(src: IntArray, dst: IntArray, size: Int, radius: Int, horizontal: Boolean) {
        for (line in 0 until size) {
            for (i in 0 until size) {
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (k in -radius..radius) {
                    val j = (i + k).coerceIn(0, size - 1)
                    val c = if (horizontal) src[line * size + j] else src[j * size + line]
                    r += c shr 16 and 0xFF
                    g += c shr 8 and 0xFF
                    b += c and 0xFF
                    count++
                }
                val out = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                if (horizontal) dst[line * size + i] = out else dst[i * size + line] = out
            }
        }
    }
}
