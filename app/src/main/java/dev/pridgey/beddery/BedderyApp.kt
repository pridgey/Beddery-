package dev.pridgey.beddery

import android.app.Application
import dev.pridgey.beddery.system.Notifications

class BedderyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
