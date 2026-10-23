package dev.pridgey.beddery.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.pridgey.beddery.R

object Notifications {
    const val CHANNEL_WATCHER = "standby_watcher"
    const val CHANNEL_LAUNCH = "standby_launch"

    const val ID_WATCHER = 1
    const val ID_LAUNCH = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val watcher = NotificationChannel(
            CHANNEL_WATCHER, context.getString(R.string.channel_watcher), NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.channel_watcher_desc)
            setShowBadge(false)
        }
        // High importance is required for full-screen intents. It never makes a sound.
        val launch = NotificationChannel(
            CHANNEL_LAUNCH, context.getString(R.string.channel_launch), NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_launch_desc)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        nm.createNotificationChannels(listOf(watcher, launch))
    }
}
