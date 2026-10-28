package dev.pridgey.beddery.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.savers.Saver
import dev.pridgey.beddery.savers.SaverContext
import dev.pridgey.beddery.savers.SaverHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneOffset
import kotlin.math.roundToInt

/*
 * Gallery thumbnails are still pictures. Running every visible screensaver live at full size
 * made the gallery stutter, so each card renders its saver for about a second, keeps a picture
 * of it, and stops. Only one card renders at a time, never while the list is scrolling, and
 * pictures are retaken each minute so the clocks in them stay right.
 */

private class Still(val image: ImageBitmap, val minute: Long)

private object StillCache {
    private val budgetKb = (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceIn(16 * 1024, 96 * 1024)
    private val cache = object : LruCache<String, Still>(budgetKb) {
        override fun sizeOf(key: String, value: Still) = value.image.width * value.image.height * 2 / 1024
    }
    /** Bumped on every put so cards waiting on a picture recompose. */
    var version by mutableIntStateOf(0)
        private set

    /** Savers whose picture couldn't be taken: they stay live. */
    val failed = mutableStateListOf<String>()

    private val io = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun get(key: String): Still? = cache.get(key)

    fun put(key: String, still: Still) {
        cache.put(key, still)
        version++
    }

    // Pictures are also kept on disk, so the gallery opens with them already in place.
    private fun file(dir: File, key: String) = File(dir, "stills/${Integer.toHexString(key.hashCode())}.jpg")

    fun save(dir: File, key: String, image: ImageBitmap) {
        io.launch {
            runCatching {
                val f = file(dir, key)
                f.parentFile?.mkdirs()
                f.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }.onFailure { Log.w("Beddery", "Couldn't save a thumbnail", it) }
        }
    }

    /** Loads a saved picture, marked stale so it's retaken once it's on screen. */
    suspend fun load(dir: File, key: String) {
        val image = withContext(Dispatchers.IO) {
            val f = file(dir, key)
            if (!f.exists()) return@withContext null
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 })
        } ?: return
        if (get(key) == null) put(key, Still(image.asImageBitmap(), -1))
    }
}

/**
 * Takes one picture at a time and stays out of the way of scrolling: a scroll stops the picture
 * being taken (it's retried later), and cards with no picture yet go before clock refreshes.
 */
@Stable
class StillScheduler {
    private val queue = ArrayList<String>()
    var active by mutableStateOf<String?>(null)
        private set
    var paused = false
        set(value) {
            field = value
            if (value) {
                // Building a full-size screensaver mid-scroll is what made the list hiccup.
                active?.let { queue.add(0, it) }
                active = null
            }
            pump()
        }

    /** [missing]: the card shows nothing yet, so it jumps ahead of cards that only need a fresher clock. */
    fun request(key: String, missing: Boolean = false) {
        if (key == active) return
        queue.remove(key)
        if (missing) queue.add(queue.indexOfFirst { it !in missingKeys }.let { if (it < 0) queue.size else it }, key) else queue += key
        if (missing) missingKeys += key else missingKeys -= key
        pump()
    }

    private val missingKeys = HashSet<String>()

    fun release(key: String) {
        queue.remove(key)
        missingKeys -= key
        if (active == key) {
            active = null
            pump()
        }
    }

    private fun pump() {
        if (active == null && !paused && queue.isNotEmpty()) active = queue.removeAt(0)
    }
}

val LocalStillScheduler = compositionLocalOf<StillScheduler?> { null }

/** Settings that change how screensavers look. Rotation and bookkeeping settings are left out. */
private fun AppSettings.lookKey(): Int = listOf(
    clockFormat, accent, showClockOverlay, tempUnit, weatherLocation, photoUris, worldZones,
    breathPattern, pendulumSpeed, odometerSeconds, catCoat,
).hashCode()

/**
 * A gallery thumbnail: a still picture of [saver], taken from a short live render. Without a
 * [LocalStillScheduler] it falls back to the live [SaverPreview].
 */
@Composable
fun SaverStill(
    saver: Saver,
    settings: AppSettings,
    context: SaverContext,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
) {
    val scheduler = LocalStillScheduler.current
    if (scheduler == null || saver.id in StillCache.failed) return SaverPreview(saver, settings, context, modifier, corner)
    val config = LocalConfiguration.current
    val vw = config.screenWidthDp.dp
    val vh = config.screenHeightDp.dp
    val key = "${saver.id}|${config.screenWidthDp}x${config.screenHeightDp}|${settings.lookKey()}|${context.use24h}"
    val minute = context.now.toEpochSecond(ZoneOffset.UTC) / 60
    // Reading the version recomposes this card when a picture lands.
    val still = StillCache.version.let { StillCache.get(key) }
    val fresh = still != null && still.minute == minute
    val dir = LocalContext.current.cacheDir
    if (still == null) LaunchedEffect(key) { StillCache.load(dir, key) }
    if (!fresh) {
        DisposableEffect(key, minute) {
            scheduler.request(key, missing = still == null)
            onDispose { scheduler.release(key) }
        }
    }
    val live = !fresh && scheduler.active == key
    BoxWithConstraints(
        modifier
            .aspectRatio(vw / vh)
            .clip(RoundedCornerShape(corner))
            .background(Color.Black)
            .clearAndSetSemantics { },
    ) {
        if (still != null) {
            Image(still.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        if (live) {
            val layer = rememberGraphicsLayer()
            val scale = maxWidth / vw
            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        // Layers need a hardware canvas; anywhere else just draw (and stay live).
                        if (drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        } else {
                            drawContent()
                        }
                    },
            ) {
                Box(
                    Modifier
                        .requiredSize(vw, vh)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                ) {
                    SaverHost(settings = settings, saverOverride = saver.id, preview = true, context = context)
                }
            }
            LaunchedEffect(key, minute) {
                // Let the scene settle: some fade in or lay themselves out on the first frames.
                delay(if (still == null) 900 else 600)
                // The picture needs at least one drawn frame.
                var waits = 0
                while (layer.size == IntSize.Zero && waits++ < 20) delay(100)
                runCatching { shrink(layer.toImageBitmap()) }.onFailure {
                    // Fall back to a live thumbnail for this one.
                    Log.w("Beddery", "Couldn't take a thumbnail of ${saver.id}", it)
                    StillCache.failed += saver.id
                }.getOrNull()?.let {
                    StillCache.put(key, Still(it, minute))
                    StillCache.save(dir, key, it)
                }
                scheduler.release(key)
            }
        }
    }
}

/** A thumbnail-sized, half-memory copy: RGB_565, at most 420 pixels wide. */
private fun shrink(image: ImageBitmap): ImageBitmap {
    val src = image.asAndroidBitmap()
    val w = minOf(src.width, 420)
    val h = (src.height * w / src.width.toFloat()).roundToInt().coerceAtLeast(1)
    val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
    val scaled = if (w == soft.width) soft else Bitmap.createScaledBitmap(soft, w, h, true)
    return scaled.copy(Bitmap.Config.RGB_565, false).asImageBitmap()
}
