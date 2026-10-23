package dev.pridgey.beddery.standby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.pridgey.beddery.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restarts standby mode after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (SettingsRepository.get(app).settings.first().standbyEnabled) StandbyController.start(app)
            } finally {
                result.finish()
            }
        }
    }
}
