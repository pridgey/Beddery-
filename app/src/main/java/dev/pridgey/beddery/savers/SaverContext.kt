package dev.pridgey.beddery.savers

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.ClockFormat
import dev.pridgey.beddery.data.NotificationsMode
import dev.pridgey.beddery.system.BatteryStatus
import dev.pridgey.beddery.system.batteryFlow
import dev.pridgey.beddery.system.clockFlow
import dev.pridgey.beddery.system.nextAlarm
import java.time.LocalDateTime

/** Everything a screensaver needs to know about the world. Updated once per second. */
@Immutable
data class SaverContext(
    val now: LocalDateTime,
    val battery: BatteryStatus,
    val use24h: Boolean,
    val accent: Color,
    val nextAlarm: LocalDateTime?,
    val settings: AppSettings,
    /** True when drawn as a small gallery thumbnail: savers should do less work and may show sample data. */
    val preview: Boolean,
    /** Shown as a preview (thumbnail or full-screen): use sample data where real data is missing. */
    val demo: Boolean = preview,
    /** How much of a notification this screen may show right now (set by the host, so night rules apply). */
    val notifications: NotificationsMode = NotificationsMode.OFF,
)

@Composable
fun rememberSaverContext(settings: AppSettings, preview: Boolean, demo: Boolean = preview): SaverContext {
    val context = LocalContext.current
    val now by remember { clockFlow() }.collectAsStateWithLifecycle(LocalDateTime.now().withNano(0))
    val battery by remember(context) { context.batteryFlow() }.collectAsStateWithLifecycle(BatteryStatus.Unknown)
    val alarm = remember(now.hour, now.minute) { context.nextAlarm() }
    val use24h = when (settings.clockFormat) {
        ClockFormat.SYSTEM -> DateFormat.is24HourFormat(context)
        ClockFormat.H12 -> false
        ClockFormat.H24 -> true
    }
    return SaverContext(
        now = now,
        battery = battery,
        use24h = use24h,
        accent = Color(settings.accent.argb),
        nextAlarm = alarm,
        settings = settings,
        preview = preview,
        demo = demo,
    )
}
