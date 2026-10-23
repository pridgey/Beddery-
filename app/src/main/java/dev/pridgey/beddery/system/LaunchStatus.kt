package dev.pridgey.beddery.system

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.pridgey.beddery.dream.BedderyDreamService

/** What we can tell about Android's screensaver ("dream") settings. */
data class DreamStatus(
    /** The phone has a Screen saver settings page. */
    val available: Boolean,
    /** Beddery is the selected screensaver, or null if Android won't tell us. */
    val selected: Boolean?,
    /** Screensaver starts while charging, or null if unknown. */
    val whileCharging: Boolean?,
)

data class StandbyStatus(
    val notificationsAllowed: Boolean,
    val fullScreenAllowed: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val ready: Boolean get() = notificationsAllowed && fullScreenAllowed
}

object LaunchStatus {

    fun dream(context: Context): DreamStatus {
        val available = dreamSettingsIntent().resolveActivity(context.packageManager) != null
        // These keys aren't public API. Many phones still let us read them; if not, we just say "unknown".
        val component = ComponentName(context, BedderyDreamService::class.java)
        val selected = runCatching {
            Settings.Secure.getString(context.contentResolver, "screensaver_components")
        }.getOrNull()?.let { value ->
            value.split(',').any { ComponentName.unflattenFromString(it) == component }
        }
        val enabled = readSecureInt(context, "screensaver_enabled")
        val onCharge = readSecureInt(context, "screensaver_activate_on_sleep")
        val whileCharging = if (enabled == null || onCharge == null) null else enabled == 1 && onCharge == 1
        return DreamStatus(available, selected, whileCharging)
    }

    fun standby(context: Context): StandbyStatus {
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        val fullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() ?: false
        } else true
        val battery = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        return StandbyStatus(notifications, fullScreen, battery)
    }

    fun dreamSettingsIntent() = Intent(Settings.ACTION_DREAM_SETTINGS)

    fun fullScreenSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        } else appNotificationSettingsIntent(context)

    fun appNotificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    private fun readSecureInt(context: Context, key: String): Int? = runCatching {
        Settings.Secure.getInt(context.contentResolver, key)
    }.getOrNull()
}
