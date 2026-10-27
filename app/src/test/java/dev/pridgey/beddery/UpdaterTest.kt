package dev.pridgey.beddery

import dev.pridgey.beddery.system.Updater
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun comparesVersionsNumerically() {
        assertTrue(Updater.isNewer("1.0.10", "1.0.9"))
        assertTrue(Updater.isNewer("1.1", "1.0.42"))
        assertTrue(Updater.isNewer("2.0.0", "1.9.9"))
        assertFalse(Updater.isNewer("1.0.7", "1.0.7"))
        assertFalse(Updater.isNewer("1.0.7", "1.0.8"))
        assertFalse(Updater.isNewer("1.0", "1.0.0"))
    }
}
