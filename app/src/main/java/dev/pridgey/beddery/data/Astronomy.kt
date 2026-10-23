package dev.pridgey.beddery.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/** Just enough astronomy for a bedside clock. Accurate to a minute or two. */
object Astronomy {
    const val SYNODIC_MONTH = 29.530588853
    private const val KNOWN_NEW_MOON_JD = 2451550.26 // 2000-01-06 18:14 UTC

    private fun julian(instant: Instant): Double = instant.toEpochMilli() / 86_400_000.0 + 2440587.5
    private fun instantOf(jd: Double): Instant = Instant.ofEpochMilli(((jd - 2440587.5) * 86_400_000.0).roundToLong())

    /** Moon phase in 0..1: 0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter. */
    fun moonPhase(instant: Instant): Double {
        val cycles = (julian(instant) - KNOWN_NEW_MOON_JD) / SYNODIC_MONTH
        return ((cycles % 1.0) + 1.0) % 1.0
    }

    /** Fraction of the disc that is lit, 0..1. */
    fun illumination(phase: Double): Double = (1 - cos(2 * PI * phase)) / 2

    /** Days until the moon next reaches [target] phase (0.5 for full, 0 for new). */
    fun daysUntil(phase: Double, target: Double): Double = (((target - phase) % 1.0 + 1.0) % 1.0) * SYNODIC_MONTH

    enum class Phase { NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT }

    fun phaseName(phase: Double): Phase = when {
        phase < 0.0339 || phase >= 0.9661 -> Phase.NEW
        phase < 0.216 -> Phase.WAXING_CRESCENT
        phase < 0.284 -> Phase.FIRST_QUARTER
        phase < 0.466 -> Phase.WAXING_GIBBOUS
        phase < 0.534 -> Phase.FULL
        phase < 0.716 -> Phase.WANING_GIBBOUS
        phase < 0.784 -> Phase.LAST_QUARTER
        else -> Phase.WANING_CRESCENT
    }

    /** Sunrise and sunset; null when the sun doesn't rise or set that day (polar day or night). */
    data class SunTimes(val sunrise: LocalDateTime?, val sunset: LocalDateTime?, val polarDay: Boolean = false)

    fun sunTimes(date: LocalDate, latitude: Double, longitude: Double, zone: ZoneId): SunTimes {
        val rad = PI / 180
        val noon = date.atTime(LocalTime.NOON).atZone(zone).toInstant()
        val n = Math.round(julian(noon) - 2451545.0 + 0.0008).toDouble()
        val jStar = n - longitude / 360.0
        val m = (357.5291 + 0.98560028 * jStar) % 360
        val c = 1.9148 * sin(m * rad) + 0.02 * sin(2 * m * rad) + 0.0003 * sin(3 * m * rad)
        val lambda = (m + c + 180 + 102.9372) % 360
        val transit = 2451545.0 + jStar + 0.0053 * sin(m * rad) - 0.0069 * sin(2 * lambda * rad)
        val declination = asin(sin(lambda * rad) * sin(23.44 * rad))
        val cosOmega = (sin(-0.833 * rad) - sin(latitude * rad) * sin(declination)) / (cos(latitude * rad) * cos(declination))
        if (cosOmega < -1) return SunTimes(null, null, polarDay = true)
        if (cosOmega > 1) return SunTimes(null, null, polarDay = false)
        val omega = acos(cosOmega) / rad
        fun local(jd: Double) = LocalDateTime.ofInstant(instantOf(jd), zone)
        return SunTimes(local(transit - omega / 360), local(transit + omega / 360))
    }

    /**
     * Best guess at where the phone is when no weather location is set: longitude from the
     * time zone's offset, and a typical latitude for that hemisphere.
     */
    fun guessLocation(zone: ZoneId, now: Instant = Instant.now()): Pair<Double, Double> {
        val offsetHours = zone.rules.getStandardOffset(now).totalSeconds / 3600.0
        val south = zone.id.startsWith("Australia/") || zone.id in SouthernZones
        return (if (south) -34.0 else 42.0) to offsetHours * 15.0
    }

    private val SouthernZones = setOf(
        "Pacific/Auckland", "America/Sao_Paulo", "America/Argentina/Buenos_Aires", "America/Santiago",
        "America/Montevideo", "Africa/Johannesburg", "America/Lima", "Pacific/Fiji", "Africa/Maputo",
    )
}
