package dev.pridgey.beddery.savers

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Taps that reach the screensaver. Only full-screen surfaces that don't close on a single tap
 * (preview, or "double-tap to close") send them, so savers can hide small surprises behind a tap.
 */
class SaverInput {
    private val events = MutableSharedFlow<Offset>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val taps: SharedFlow<Offset> = events

    fun tap(at: Offset) {
        events.tryEmit(at)
    }
}

/** Thumbnails get an instance nobody taps. */
val LocalSaverInput = staticCompositionLocalOf { SaverInput() }

/** Calls [onTap] with the tap position, in the saver's own coordinates. */
@Composable
fun OnSaverTap(onTap: (Offset) -> Unit) {
    val input = LocalSaverInput.current
    val callback by rememberUpdatedState(onTap)
    LaunchedEffect(input) { input.taps.collect { callback(it) } }
}

/** Lets something happen at most once per [periodMs], so easter eggs stay special. */
class Cooldown(private val periodMs: Long) {
    private var last = -periodMs

    fun tryAcquire(now: Long = SystemClock.uptimeMillis()): Boolean {
        if (now - last < periodMs) return false
        last = now
        return true
    }
}
