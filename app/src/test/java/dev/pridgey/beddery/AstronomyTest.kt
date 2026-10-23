package dev.pridgey.beddery

import dev.pridgey.beddery.data.Astronomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class AstronomyTest {
    private fun near(expected: LocalDateTime, actual: LocalDateTime?, minutes: Long = 5) {
        assertNotNull(actual)
        val diff = abs(Duration.between(expected, actual).toMinutes())
        assertTrue("expected $expected, got $actual", diff <= minutes)
    }

    @Test fun londonMidsummer() {
        val sun = Astronomy.sunTimes(LocalDate.of(2026, 6, 21), 51.5074, -0.1278, ZoneId.of("Europe/London"))
        near(LocalDateTime.of(2026, 6, 21, 4, 43), sun.sunrise)
        near(LocalDateTime.of(2026, 6, 21, 21, 21), sun.sunset)
    }

    @Test fun newYorkWinter() {
        val sun = Astronomy.sunTimes(LocalDate.of(2026, 12, 21), 40.7128, -74.006, ZoneId.of("America/New_York"))
        near(LocalDateTime.of(2026, 12, 21, 7, 17), sun.sunrise)
        near(LocalDateTime.of(2026, 12, 21, 16, 32), sun.sunset)
    }

    @Test fun polarNightHasNoSunrise() {
        val sun = Astronomy.sunTimes(LocalDate.of(2026, 12, 21), 78.22, 15.65, ZoneId.of("Arctic/Longyearbyen"))
        assertNull(sun.sunrise)
        assertEquals(false, sun.polarDay)
    }

    @Test fun knownFullAndNewMoons() {
        // Full moon 2024-04-23 23:49 UTC, new moon 2024-04-08 18:21 UTC (the eclipse).
        val full = Astronomy.moonPhase(ZonedDateTime.of(2024, 4, 23, 23, 49, 0, 0, ZoneId.of("UTC")).toInstant())
        val new = Astronomy.moonPhase(ZonedDateTime.of(2024, 4, 8, 18, 21, 0, 0, ZoneId.of("UTC")).toInstant())
        assertEquals(0.5, full, 0.02)
        assertTrue(new < 0.02 || new > 0.98)
        assertEquals(Astronomy.Phase.FULL, Astronomy.phaseName(full))
        assertEquals(1.0, Astronomy.illumination(full), 0.01)
    }

    @Test fun daysUntilWrapsAround() {
        assertEquals(Astronomy.SYNODIC_MONTH / 2, Astronomy.daysUntil(0.0, 0.5), 0.001)
        assertEquals(Astronomy.SYNODIC_MONTH * 0.75, Astronomy.daysUntil(0.75, 0.5), 0.001)
    }
}
