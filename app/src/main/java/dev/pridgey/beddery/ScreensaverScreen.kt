package dev.pridgey.beddery

import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.SettingsRepository
import dev.pridgey.beddery.savers.SaverHost
import dev.pridgey.beddery.savers.rememberSaverContext

/**
 * Full-screen screensaver used by both the system dream and standby mode.
 * Also manages window brightness, which drops to the minimum during night mode.
 */
@Composable
fun ScreensaverScreen(window: Window?, modifier: Modifier = Modifier, saverOverride: String? = null) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings
    if (s == null) {
        Box(modifier.fillMaxSize().background(Color.Black))
        return
    }
    val saverContext = rememberSaverContext(s, preview = false)
    val night = s.isNight(saverContext.now.toLocalTime())
    WindowBrightness(window, s, night)
    SaverHost(settings = s, modifier = modifier, saverOverride = saverOverride, context = saverContext)
}

@Composable
private fun WindowBrightness(window: Window?, settings: AppSettings, night: Boolean) {
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
