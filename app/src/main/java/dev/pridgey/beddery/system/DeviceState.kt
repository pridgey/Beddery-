package dev.pridgey.beddery.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class PlugType { NONE, AC, USB, WIRELESS, DOCK }

data class BatteryStatus(
    val level: Int,
    val isCharging: Boolean,
    val isFull: Boolean,
    val plug: PlugType,
    /** Estimated time until full, when the system can tell us. */
    val chargeTimeRemainingMs: Long?,
) {
    val isPluggedIn: Boolean get() = plug != PlugType.NONE

    companion object {
        val Unknown = BatteryStatus(level = 100, isCharging = false, isFull = false, plug = PlugType.NONE, chargeTimeRemainingMs = null)
    }
}

private const val PLUGGED_DOCK = 8 // BatteryManager.BATTERY_PLUGGED_DOCK (API 33)

fun parseBattery(intent: Intent, manager: BatteryManager?): BatteryStatus {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val plug = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
        BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
        PLUGGED_DOCK -> PlugType.DOCK
        0 -> PlugType.NONE
        else -> PlugType.AC
    }
    val full = status == BatteryManager.BATTERY_STATUS_FULL
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || (full && plug != PlugType.NONE)
    val remaining = manager?.computeChargeTimeRemaining()?.takeIf { it > 0 && charging && !full }
    return BatteryStatus(
        level = if (level >= 0) (level * 100 / scale).coerceIn(0, 100) else 100,
        isCharging = charging,
        isFull = full,
        plug = plug,
        chargeTimeRemainingMs = remaining,
    )
}

fun Context.currentBattery(): BatteryStatus {
    val sticky = ContextCompat.registerReceiver(
        this, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
    ) ?: return BatteryStatus.Unknown
    return parseBattery(sticky, getSystemService(BatteryManager::class.java))
}

/** Live battery status. Emits immediately with the current (sticky) value. */
fun Context.batteryFlow(): Flow<BatteryStatus> = callbackFlow {
    val manager = getSystemService(BatteryManager::class.java)
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            trySend(parseBattery(intent, manager))
        }
    }
    val sticky = ContextCompat.registerReceiver(
        this@batteryFlow, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    sticky?.let { trySend(parseBattery(it, manager)) }
    awaitClose { unregisterReceiver(receiver) }
}.conflate().distinctUntilChanged()

/** Emits the current local time at the start of every second. */
fun clockFlow(): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now.withNano(0))
        delay(1000L - now.nano / 1_000_000L)
    }
}

/** The next alarm set in the user's clock app, if it rings within [withinHours]. */
fun Context.nextAlarm(withinHours: Long = 24): LocalDateTime? {
    val trigger = getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime ?: return null
    val now = System.currentTimeMillis()
    if (trigger - now > withinHours * 3_600_000L) return null
    return LocalDateTime.ofInstant(Instant.ofEpochMilli(trigger), ZoneId.systemDefault())
}
