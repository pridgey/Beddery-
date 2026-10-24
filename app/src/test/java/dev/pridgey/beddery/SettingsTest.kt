package dev.pridgey.beddery

import dev.pridgey.beddery.data.AppSettings
import dev.pridgey.beddery.data.TempUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

class SettingsTest {

    @Test fun nightWindowWrapsPastMidnight() {
        val s = AppSettings(nightStartMinutes = 22 * 60, nightEndMinutes = 7 * 60)
        assertTrue(s.isNight(LocalTime.of(23, 30)))
        assertTrue(s.isNight(LocalTime.of(3, 0)))
        assertFalse(s.isNight(LocalTime.of(7, 0)))
        assertFalse(s.isNight(LocalTime.of(12, 0)))
        assertTrue(s.isNight(LocalTime.of(22, 0)))
    }

    @Test fun nightWindowSameDay() {
        val s = AppSettings(nightStartMinutes = 13 * 60, nightEndMinutes = 15 * 60)
        assertTrue(s.isNight(LocalTime.of(14, 0)))
        assertFalse(s.isNight(LocalTime.of(16, 0)))
    }

    @Test fun nightModeOffIsNeverNight() {
        assertFalse(AppSettings(nightModeEnabled = false).isNight(LocalTime.of(2, 0)))
    }

    @Test fun nightScreenFollowsTheHoursEvenWithoutDimming() {
        val s = AppSettings(nightModeEnabled = false, nightSaverId = "moon")
        assertTrue(s.isNightHours(LocalTime.of(2, 0)))
        assertFalse(s.isNightHours(LocalTime.of(12, 0)))
    }

    @Test fun emptyWindowIsNeverNight() {
        assertFalse(AppSettings(nightStartMinutes = 60, nightEndMinutes = 60).isNight(LocalTime.of(1, 0)))
    }

    @Test fun temperatureUnitFollowsRegion() {
        assertEquals(TempUnit.FAHRENHEIT, AppSettings.defaultTempUnit(Locale.US))
        assertEquals(TempUnit.CELSIUS, AppSettings.defaultTempUnit(Locale.UK))
        assertEquals(TempUnit.CELSIUS, AppSettings.defaultTempUnit(Locale.GERMANY))
    }
}
