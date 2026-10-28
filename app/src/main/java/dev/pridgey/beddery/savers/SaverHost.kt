package dev.pridgey.beddery.savers

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.NotificationsMode
import dev.pridgey.beddery.data.SettingsRepository
import dev.pridgey.beddery.data.ShuffleMode
import dev.pridgey.beddery.media.rememberNowPlaying
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.time.LocalTime

private val NightTint = Color(0xFFFF4A26)

/**
 * Draws the active screensaver plus the shared extras: shuffle, small clock overlay,
 * notifications, burn-in protection and night mode. Used by the dream, standby mode and previews.
 *
 * [session] is true for a real screensaver session (not a preview): only sessions advance
 * the "new screensaver each time" shuffle.
 */
@Composable
fun SaverHost(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    saverOverride: String? = null,
    preview: Boolean = false,
    session: Boolean = false,
    context: SaverContext = rememberSaverContext(settings, preview),
    /** Told whether the saver on screen has its own buttons, so the window can accept touches. */
    onInteractive: (Boolean) -> Unit = {},
) {
    val picked = if (saverOverride != null) Savers.find(saverOverride) else rememberActiveSaver(settings, session, context.now.toLocalTime())
    val music = rememberMusicTakeover(enabled = !preview && saverOverride == null && settings.musicTakeover)
    val saver = if (music) Savers.find(NOW_PLAYING) else picked
    val interactiveCallback by rememberUpdatedState(onInteractive)
    LaunchedEffect(saver.interactive) { interactiveCallback(saver.interactive) }
    val night = !preview && settings.isNight(context.now.toLocalTime())
    // A wake-up dawn lifts night mode as it comes up.
    val wake by WakeLight.level.collectAsStateWithLifecycle()
    val lift = if (preview) 0f else wake
    val dim by animateFloatAsState(if (night) settings.nightDim.coerceIn(0f, 0.9f) * (1f - lift) else 0f, tween(3000), label = "dim")
    val redTint by animateFloatAsState(if (night && settings.nightRedTint) 1f - lift else 0f, tween(3000), label = "tint")
    val notificationMode = when {
        preview -> NotificationsMode.OFF
        night && !settings.notificationsAtNight -> NotificationsMode.OFF
        else -> settings.notificationsMode
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clipToBounds()
            .graphicsLayer { if (redTint > 0f) compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (redTint > 0f) {
                    // Multiply keeps the reds and removes blue/green light, which is kindest to night vision.
                    val tint = androidx.compose.ui.graphics.lerp(Color.White, NightTint, redTint)
                    drawRect(tint, blendMode = BlendMode.Multiply)
                }
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            },
    ) {
        Crossfade(targetState = saver, animationSpec = tween(1200), label = "saver") { s ->
            val shift = Modifier.burnInShift(settings.burnInProtection && !preview)
            Box(Modifier.fillMaxSize()) {
                // Animated savers move on their own; static clocks drift slightly to protect the screen.
                Box(Modifier.fillMaxSize().then(if (s.showsTime) shift else Modifier)) {
                    s.content(context.copy(notifications = notificationMode))
                }
                if (!s.showsTime && settings.showClockOverlay) {
                    ClockOverlay(
                        context,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp)
                            .then(shift),
                        notifications = notificationMode,
                    )
                } else if (s.showsNotifications) {
                    NotificationBar(
                        notificationMode,
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 36.dp, start = 24.dp, end = 24.dp)
                            .then(shift),
                    )
                }
            }
        }
    }
}

private const val NOW_PLAYING = "nowplaying"

/**
 * True while music plays, so the host can switch to Now Playing. Stays on for a while after a
 * pause, so pausing from the screen itself doesn't snap straight back to the old screensaver.
 */
@Composable
private fun rememberMusicTakeover(enabled: Boolean): Boolean {
    val track = rememberNowPlaying(enabled)
    val playing = track?.playing == true
    val present = track != null
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(enabled, playing, present) {
        when {
            !enabled -> on = false
            playing -> on = true
            on -> {
                delay(if (present) 2 * 60_000L else 10_000L)
                on = false
            }
        }
    }
    return on
}

/** The night screen during the night hours; otherwise the selected saver, or a shuffled pick when shuffle is on. */
@Composable
fun rememberActiveSaver(settings: AppSettings, session: Boolean = false, now: LocalTime = LocalTime.now()): Saver {
    val day = rememberDaySaver(settings, session)
    val night = settings.nightSaverId?.let { Savers.findOrNull(it) }
    return if (night != null && settings.isNightHours(now)) night else day
}

@Composable
private fun rememberDaySaver(settings: AppSettings, session: Boolean): Saver {
    val pool = remember(settings.shuffleEnabled, settings.shuffleIds, settings.selectedSaverId) {
        if (settings.shuffleEnabled) {
            Savers.all.filter { it.id in settings.shuffleIds }.ifEmpty { Savers.all }
        } else {
            listOf(Savers.find(settings.selectedSaverId))
        }
    }
    if (settings.shuffleEnabled && settings.shuffleMode == ShuffleMode.ON_START) {
        return rememberSessionPick(pool, settings, session)
    }
    val order = remember(pool) { pool.shuffled() }
    var index by remember(order) { mutableIntStateOf(0) }
    LaunchedEffect(order, settings.shuffleMinutes) {
        if (order.size < 2) return@LaunchedEffect
        while (true) {
            delay(settings.shuffleMinutes.coerceAtLeast(1) * 60_000L)
            index = (index + 1) % order.size
        }
    }
    return order[index.coerceIn(order.indices)]
}

/** One screensaver for the whole session, drawn from the shuffle deck when it starts. */
@Composable
private fun rememberSessionPick(pool: List<Saver>, settings: AppSettings, session: Boolean): Saver {
    val context = LocalContext.current
    // Keyed on the pool only: saving the new deck below must not trigger another draw.
    val draw = remember(pool) {
        ShuffleDeck.draw(pool.map { it.id }, settings.shuffleDeck, settings.lastShuffleId)
    }
    if (session) {
        LaunchedEffect(draw) {
            SettingsRepository.get(context).update { it.copy(shuffleDeck = draw.remaining, lastShuffleId = draw.id) }
        }
    }
    return Savers.find(draw.id)
}
