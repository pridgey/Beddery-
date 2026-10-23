package dev.pridgey.beddery.standby

import android.Manifest
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.pridgey.beddery.R
import dev.pridgey.beddery.system.Notifications

/** Shared, in-process state between the watcher service and the standby activity. */
object StandbyState {
    @Volatile
    var visible: Boolean = false

    /** Elapsed-realtime when the standby screen was last hidden. */
    @Volatile
    var lastHiddenAt: Long = 0L

    /**
     * Set when the user dismisses standby. Turning the screen off again shouldn't bring it right back;
     * it re-arms when the phone is unlocked or re-plugged.
     */
    @Volatile
    var suppressed: Boolean = false

    fun recentlyVisible(): Boolean = visible || SystemClock.elapsedRealtime() - lastHiddenAt < 2_000
}

object StandbyController {
    private const val TAG = "Standby"

    fun start(context: Context) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, ChargingWatcherService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Couldn't start standby watcher", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Couldn't start standby watcher", e)
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Foreground service start not allowed right now", e)
            } else throw e
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, ChargingWatcherService::class.java))
    }

    /**
     * Shows the standby screen. A full-screen-intent notification is the supported way to put an
     * activity over the lock screen from the background; Android launches it directly when the
     * device is locked.
     */
    fun launch(context: Context) {
        val intent = StandbyActivity.intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_LAUNCH)
            .setSmallIcon(R.drawable.ic_stat_beddery)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.launch_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Alarm category lets the screensaver appear even when Do Not Disturb / Bedtime mode is on.
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pending, true)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setTimeoutAfter(15_000)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Notification permission missing; can't show standby")
            return
        }
        NotificationManagerCompat.from(context).notify(Notifications.ID_LAUNCH, notification)
    }
}
