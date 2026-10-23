package dev.pridgey.beddery.savers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.LocalDateTime

/**
 * A screensaver that wakes you gently (Sunrise Alarm) needs to lift night mode's dimming and
 * red tint, and raise the screen brightness, as its dawn comes up. It reports how far along
 * the dawn is here, and the screensaver host and window follow.
 */
object WakeLight {
    /** Minutes of dawn before the alarm. */
    const val DAWN_MINUTES = 30L
    /** Minutes the room stays lit after the alarm before fading back. */
    const val AFTER_MINUTES = 20L

    private val state = MutableStateFlow(0f)
    val level: StateFlow<Float> = state

    /**
     * How light the dawn is, 0..1: rises over [DAWN_MINUTES] before [alarm], holds for a while
     * after it, then fades out again.
     */
    fun progress(now: LocalDateTime, alarm: LocalDateTime?): Float {
        alarm ?: return 0f
        val s = Duration.between(alarm, now).seconds.toFloat() // negative before the alarm
        val dawn = DAWN_MINUTES * 60f
        val after = AFTER_MINUTES * 60f
        return when {
            s < -dawn -> 0f
            s < 0f -> 1f + s / dawn
            s < after -> 1f
            s < after + 300f -> 1f - (s - after) / 300f
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    /** Called by the saver while it's on screen during a real session. */
    @Composable
    fun Drive(level: Float, enabled: Boolean) {
        LaunchedEffect(level, enabled) { state.value = if (enabled) level else 0f }
        DisposableEffect(Unit) { onDispose { state.value = 0f } }
    }
}
