package dev.pridgey.beddery

import android.view.Window
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.SettingsRepository
import dev.pridgey.beddery.savers.LocalSaverInput
import dev.pridgey.beddery.savers.SaverHost
import dev.pridgey.beddery.savers.SaverInput
import dev.pridgey.beddery.savers.rememberSaverContext
import kotlinx.coroutines.delay

/** Current settings, or null while they load (a few milliseconds). */
@Composable
fun rememberSettings(): AppSettings? {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    return settings
}

/**
 * Full-screen screensaver used by both the system dream and standby mode.
 * Also manages window brightness, which drops to the minimum during night mode.
 */
@Composable
fun ScreensaverScreen(
    window: Window?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: (AppSettings) -> Unit = {},
) {
    val s = rememberSettings()
    if (s == null) {
        Box(modifier.fillMaxSize().background(Color.Black))
        return
    }
    val settingsCallback by rememberUpdatedState(onSettings)
    LaunchedEffect(s) { settingsCallback(s) }
    val saverContext = rememberSaverContext(s, preview = false)
    WindowBrightness(window, s, s.isNight(saverContext.now.toLocalTime()))
    ScreensaverGestures(doubleTapToClose = s.doubleTapToExit, onClose = onClose) {
        SaverHost(settings = s, modifier = modifier, session = true, context = saverContext)
    }
}

/**
 * A single tap closes the screensaver, unless "double-tap to close" is on: then single taps
 * reach the screensaver (some hide a surprise) and a hint explains how to close.
 */
@Composable
fun ScreensaverGestures(doubleTapToClose: Boolean, onClose: () -> Unit, content: @Composable () -> Unit) {
    val input = remember { SaverInput() }
    var showHint by remember { mutableStateOf(false) }
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(showHint) {
        if (showHint) {
            delay(2_000)
            showHint = false
        }
    }
    CompositionLocalProvider(LocalSaverInput provides input) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(doubleTapToClose) {
                    if (doubleTapToClose) {
                        detectTapGestures(
                            onTap = {
                                input.tap(it)
                                showHint = true
                            },
                            onDoubleTap = { close() },
                        )
                    } else {
                        detectTapGestures(onTap = { close() })
                    }
                },
        ) {
            content()
            HintPill(showHint, R.string.double_tap_hint, Modifier.align(Alignment.TopCenter).padding(top = 48.dp))
        }
    }
}

@Composable
fun HintPill(visible: Boolean, @StringRes text: Int, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            stringResource(text), color = Color.White, fontSize = 14.sp,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun WindowBrightness(window: Window?, settings: AppSettings, night: Boolean) {
    val target = when {
        night -> 0.01f
        settings.brightness != null -> settings.brightness.coerceIn(0.01f, 1f)
        else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }
    LaunchedEffect(window, target) {
        window ?: return@LaunchedEffect
        val attrs = window.attributes
        attrs.screenBrightness = target
        window.attributes = attrs
    }
}
