package dev.pridgey.beddery.standby

import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.pridgey.beddery.MainActivity
import dev.pridgey.beddery.R
import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.SettingsRepository
import dev.pridgey.beddery.system.Notifications
import dev.pridgey.beddery.system.currentBattery
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Standby mode's watcher. Android no longer delivers charger/screen broadcasts to apps that
 * aren't running, so this small foreground service listens for them while standby is enabled.
 * It does no work in between events.
 */
class ChargingWatcherService : Service() {

    private val scope = MainScope()
    private var settings: AppSettings? = null
    private var pending: Job? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = handle(intent.action)
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            SettingsRepository.get(this@ChargingWatcherService).settings.collect { s ->
                settings = s
                if (!s.standbyEnabled) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(receiver)
        scope.cancel()
        super.onDestroy()
    }

    private fun handle(action: String?) {
        val s = settings ?: return
        when (action) {
            Intent.ACTION_POWER_CONNECTED -> {
                StandbyState.suppressed = false
                if (s.standbyOnPlugIn && isLockedOrOff()) schedule(900)
            }
            Intent.ACTION_POWER_DISCONNECTED -> pending?.cancel()
            Intent.ACTION_SCREEN_OFF -> {
                if (StandbyState.recentlyVisible()) {
                    // The user turned the screen off while standby was showing: they want it dark.
                    StandbyState.suppressed = true
                    return
                }
                if (s.standbyOnScreenOff && !StandbyState.suppressed && currentBattery().isPluggedIn) schedule(1_200)
            }
            Intent.ACTION_USER_PRESENT -> {
                StandbyState.suppressed = false
                pending?.cancel()
            }
        }
    }

    private fun schedule(delayMs: Long) {
        pending?.cancel()
        pending = scope.launch {
            delay(delayMs)
            if (currentBattery().isPluggedIn && isLockedOrOff() && !StandbyState.visible) {
                StandbyController.launch(this@ChargingWatcherService)
            }
        }
    }

    private fun isLockedOrOff(): Boolean {
        val power = getSystemService(PowerManager::class.java)
        val keyguard = getSystemService(KeyguardManager::class.java)
        return power?.isInteractive == false || keyguard?.isKeyguardLocked == true
    }

    private fun startInForeground() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_WATCHER)
            .setSmallIcon(R.drawable.ic_stat_beddery)
            .setContentTitle(getString(R.string.watcher_title))
            .setContentText(getString(R.string.watcher_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.ID_WATCHER, notification, type)
    }
}
