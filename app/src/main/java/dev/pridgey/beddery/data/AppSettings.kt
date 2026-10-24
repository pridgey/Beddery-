package dev.pridgey.beddery.data

import java.time.LocalTime
import java.util.Locale

enum class ClockFormat { SYSTEM, H12, H24 }

enum class TempUnit { CELSIUS, FAHRENHEIT }

enum class StandbyOrientation { AUTO, PORTRAIT, LANDSCAPE }

/** When shuffle picks a new screensaver: on a timer, or once each time Beddery starts. */
enum class ShuffleMode { TIMED, ON_START }

/** How much of a notification the screensavers may show. */
enum class NotificationsMode { OFF, ICONS, PREVIEWS }

/** Seconds for each phase of a breathing cycle: in, hold, out, hold. */
enum class BreathPattern(val inhale: Int, val holdIn: Int, val exhale: Int, val holdOut: Int) {
    BOX(4, 4, 4, 4),
    RELAX(4, 7, 8, 0),
    CALM(4, 0, 6, 0),
}

enum class Accent(val argb: Long) {
    SKY(0xFF8CC8FF),
    MINT(0xFF7FE3B8),
    AMBER(0xFFFFC66E),
    ROSE(0xFFFF9AB0),
    LAVENDER(0xFFBBA6FF),
    SNOW(0xFFECEFF4),
}

data class WeatherLocation(val name: String, val latitude: Double, val longitude: Double)

data class AppSettings(
    // Which screensaver(s)
    val selectedSaverId: String = DEFAULT_SAVER,
    val shuffleEnabled: Boolean = false,
    val shuffleIds: Set<String> = emptySet(),
    val shuffleMinutes: Int = 15,
    val shuffleMode: ShuffleMode = ShuffleMode.TIMED,
    /** Screensavers still to come before the shuffle repeats (ON_START mode). */
    val shuffleDeck: List<String> = emptyList(),
    val lastShuffleId: String? = null,

    // Display
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val accent: Accent = Accent.SKY,
    /** Window brightness 0..1, or null to follow the system. */
    val brightness: Float? = null,
    val showClockOverlay: Boolean = true,
    val burnInProtection: Boolean = true,

    // Notifications (needs notification access)
    val notificationsMode: NotificationsMode = NotificationsMode.ICONS,
    val notificationsAtNight: Boolean = false,

    // Night mode
    val nightModeEnabled: Boolean = true,
    val nightStartMinutes: Int = 22 * 60,
    val nightEndMinutes: Int = 7 * 60,
    val nightRedTint: Boolean = true,
    /** Extra darkening applied at night, 0..0.9. */
    val nightDim: Float = 0.5f,

    // Standby mode (fallback for phones without Android's screensaver)
    val standbyEnabled: Boolean = false,
    val standbyOnPlugIn: Boolean = true,
    val standbyOnScreenOff: Boolean = true,
    val doubleTapToExit: Boolean = false,
    val standbyOrientation: StandbyOrientation = StandbyOrientation.AUTO,
    /** Seen the swipe hint in full-screen preview. */
    val previewHintSeen: Boolean = false,

    // Per-screensaver options
    val tempUnit: TempUnit = defaultTempUnit(),
    val weatherLocation: WeatherLocation? = null,
    val photoUris: List<String> = emptyList(),
    val worldZones: List<String> = DEFAULT_WORLD_ZONES,
    val breathPattern: BreathPattern = BreathPattern.BOX,
    /** Pendulum Wave speed multiplier. */
    val pendulumSpeed: Float = 1f,
    /** Odometer shows a seconds pair. */
    val odometerSeconds: Boolean = true,
    /** The Orb's cat show: ginger, grey, black, white or calico. */
    val orbCat: String = "ginger",

    val onboardingDone: Boolean = false,
) {
    fun isNight(time: LocalTime): Boolean =
        nightModeEnabled && isInWindow(time.hour * 60 + time.minute, nightStartMinutes, nightEndMinutes)

    companion object {
        const val DEFAULT_SAVER = "minimal"

        val DEFAULT_WORLD_ZONES = listOf(
            "America/Los_Angeles",
            "America/New_York",
            "Europe/London",
            "Asia/Dubai",
            "Asia/Tokyo",
            "Australia/Sydney",
        )

        /** True when [minute] (minutes since midnight) lies in [start, end), wrapping past midnight. */
        fun isInWindow(minute: Int, start: Int, end: Int): Boolean = when {
            start == end -> false
            start < end -> minute in start until end
            else -> minute >= start || minute < end
        }

        fun defaultTempUnit(locale: Locale = Locale.getDefault()): TempUnit =
            if (locale.country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")) TempUnit.FAHRENHEIT
            else TempUnit.CELSIUS
    }
}
