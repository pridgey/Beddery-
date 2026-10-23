package dev.pridgey.beddery.standby

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.beddery.ScreensaverScreen
import dev.pridgey.beddery.data.SettingsRepository
import dev.pridgey.beddery.data.StandbyOrientation
import dev.pridgey.beddery.system.Notifications
import dev.pridgey.beddery.system.batteryFlow
import dev.pridgey.beddery.ui.preview.PreviewBrowser
import dev.pridgey.beddery.ui.theme.BedderyTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

/**
 * Full-screen screensaver shown over the lock screen (standby mode), or the swipeable
 * preview browser from the app.
 * It never unlocks the phone: closing it reveals the lock screen.
 */
class StandbyActivity : ComponentActivity() {

    private val isPreview by lazy { intent.getBooleanExtra(EXTRA_PREVIEW, false) }
    private var resumedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(!isPreview)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        NotificationManagerCompat.from(this).cancel(Notifications.ID_LAUNCH)

        val saverOverride = intent.getStringExtra(EXTRA_SAVER)
        if (isPreview) {
            // Previews follow the phone's rotation so you can try both layouts.
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
        val repo = SettingsRepository.get(this)

        setContent {
            if (isPreview) {
                BedderyTheme(darkTheme = true) {
                    PreviewBrowser(window = window, startId = saverOverride, onClose = ::finish)
                }
                return@setContent
            }
            val settings by repo.settings.collectAsStateWithLifecycle(initialValue = null)
            LaunchedEffect(settings?.standbyOrientation) {
                val orientation = settings?.standbyOrientation ?: return@LaunchedEffect
                requestedOrientation = when (orientation) {
                    StandbyOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    StandbyOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    StandbyOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
            // Close when unplugged.
            LaunchedEffect(Unit) {
                batteryFlow().drop(1).first { !it.isPluggedIn }
                finish()
            }
            BackHandler { dismiss() }
            ScreensaverScreen(window = window, onClose = ::dismiss)
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isPreview) StandbyState.visible = true
    }

    override fun onResume() {
        super.onResume()
        resumedAt = SystemClock.elapsedRealtime()
    }

    override fun onStop() {
        super.onStop()
        if (isPreview) return
        StandbyState.visible = false
        StandbyState.lastHiddenAt = SystemClock.elapsedRealtime()
        // If the user turned the screen off (or something else took over), close for good so
        // the next wake shows the normal lock screen. Ignore the brief stop that can happen while launching.
        if (!isChangingConfigurations && SystemClock.elapsedRealtime() - resumedAt > 1_500) finish()
    }

    private fun dismiss() {
        if (!isPreview) StandbyState.suppressed = true
        finish()
    }

    companion object {
        private const val EXTRA_PREVIEW = "preview"
        private const val EXTRA_SAVER = "saver"

        fun intent(context: Context, preview: Boolean = false, saverId: String? = null): Intent =
            Intent(context, StandbyActivity::class.java)
                .putExtra(EXTRA_PREVIEW, preview)
                .apply { if (saverId != null) putExtra(EXTRA_SAVER, saverId) }
    }
}
