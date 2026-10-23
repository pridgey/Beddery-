package dev.pridgey.beddery

import dev.pridgey.beddery.data.Astronomy
import dev.pridgey.beddery.data.Sky
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class SkyTest {
    @Test
    fun sunAtJ2000() {
        // 2000-01-01 12:00 UTC: RA 18h 45m, Dec -23.0°.
        val sun = Sky.sunPosition(Instant.parse("2000-01-01T12:00:00Z"))
        assertEquals(281.3, sun.raDeg, 0.3)
        assertEquals(-23.0, sun.decDeg, 0.2)
    }

    @Test
    fun moonAgreesWithPhase() {
        // The Moon's angle from the Sun should match the phase: ~180° at full, ~90° at quarters.
        var t = Instant.parse("2026-01-01T00:00:00Z")
        repeat(60) {
            val phase = Astronomy.moonPhase(t)
            val sep = Sky.separation(Sky.sunPosition(t), Sky.moonPosition(t))
            val expected = if (phase <= 0.5) phase * 360 else (1 - phase) * 360
            assertEquals("at $t (phase $phase)", expected, sep, 12.0)
            t = t.plus(Duration.ofHours(29))
        }
    }

    @Test
    fun polarisSitsAtYourLatitude() {
        val t = Instant.parse("2026-10-23T22:00:00Z")
        val polaris = Sky.stars.first { it.name == "Polaris" }
        for (lat in listOf(20.0, 42.0, 60.0)) {
            val h = Sky.horizontal(Sky.Equatorial(polaris.raH * 15, polaris.dec), Sky.siderealDeg(t, -71.0), lat)
            assertEquals(lat, h.altDeg, 1.0)
            assertTrue(h.azDeg < 2 || h.azDeg > 358)
        }
    }

    @Test
    fun planetsStayNearTheEcliptic() {
        val t = Instant.parse("2026-10-23T00:00:00Z")
        for (p in Sky.Planet.entries) {
            val eq = Sky.planetPosition(p, t)
            assertTrue("$p dec ${eq.decDeg}", kotlin.math.abs(eq.decDeg) < 30)
        }
    }

    @Test
    fun constellationLinesUseKnownStars() {
        val names = Sky.stars.map { it.name }.toSet()
        for (c in Sky.constellations) for ((a, b) in c.lines) {
            assertTrue("${c.name}: $a", a in names)
            assertTrue("${c.name}: $b", b in names)
        }
    }
}
