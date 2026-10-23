package dev.pridgey.beddery

import dev.pridgey.beddery.savers.impl.WordClock
import org.junit.Assert.assertEquals
import org.junit.Test

class WordClockTest {

    private fun phrase(hour: Int, minute: Int): String =
        WordClock.words(hour, minute)
            .sortedWith(compareBy({ it.row }, { it.col }))
            .joinToString(" ") { WordClock.grid[it.row].substring(it.col, it.col + it.length) }

    @Test fun onTheHour() = assertEquals("IT IS AM TEN OCLOCK", phrase(10, 0))

    @Test fun pastTheHour() = assertEquals("IT IS PM TWENTY FIVE PAST TWELVE", phrase(12, 27))

    @Test fun quarterTo() = assertEquals("IT IS PM A QUARTER TO NINE", phrase(20, 45))

    @Test fun halfPast() = assertEquals("IT IS AM HALF PAST SEVEN", phrase(7, 31))

    @Test fun toWrapsAroundMidnight() = assertEquals("IT IS PM FIVE TO TWELVE", phrase(23, 55))

    @Test fun gridIsRectangular() = WordClock.grid.forEach { assertEquals(11, it.length) }

    @Test fun everyMinuteLightsAnHour() {
        for (h in 0 until 24) for (m in 0 until 60) {
            assert(WordClock.litCells(h, m).isNotEmpty())
        }
    }
}
