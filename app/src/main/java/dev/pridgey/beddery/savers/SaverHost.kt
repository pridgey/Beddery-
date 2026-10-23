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
import androidx.compose.ui.unit.dp
import dev.pridgey.beddery.data.AppSettings
import kotlinx.coroutines.delay

private val NightTint = Color(0xFFFF4A26)

/**
 * Draws the active screensaver plus the shared extras: shuffle, small clock overlay,
 * burn-in protection and night mode. Used by the dream, standby mode and previews.
 */
@Composable
fun SaverHost(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    saverOverride: String? = null,
    preview: Boolean = false,
    context: SaverContext = rememberSaverContext(settings, preview),
) {
    val saver = if (saverOverride != null) Savers.find(saverOverride) else rememberActiveSaver(settings)
    val night = !preview && settings.isNight(context.now.toLocalTime())
    val dim by animateFloatAsState(if (night) settings.nightDim.coerceIn(0f, 0.9f) else 0f, tween(3000), label = "dim")
    val redTint by animateFloatAsState(if (night && settings.nightRedTint) 1f else 0f, tween(3000), label = "tint")

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
                    s.content(context)
                }
                if (!s.showsTime && settings.showClockOverlay) {
                    ClockOverlay(
                        context,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 40.dp)
                            .then(shift),
                    )
                }
            }
        }
    }
}

/** The selected saver, or a rotating pick when shuffle is on. */
@Composable
fun rememberActiveSaver(settings: AppSettings): Saver {
    val pool = remember(settings.shuffleEnabled, settings.shuffleIds, settings.selectedSaverId) {
        if (settings.shuffleEnabled) {
            Savers.all.filter { it.id in settings.shuffleIds }.ifEmpty { Savers.all }.shuffled()
        } else {
            listOf(Savers.find(settings.selectedSaverId))
        }
    }
    var index by remember(pool) { mutableIntStateOf(0) }
    LaunchedEffect(pool, settings.shuffleMinutes) {
        if (pool.size < 2) return@LaunchedEffect
        while (true) {
            delay(settings.shuffleMinutes.coerceAtLeast(1) * 60_000L)
            index = (index + 1) % pool.size
        }
    }
    return pool[index.coerceIn(pool.indices)]
}
