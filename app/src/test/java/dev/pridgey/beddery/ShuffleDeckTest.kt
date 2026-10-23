package dev.pridgey.beddery

import dev.pridgey.beddery.savers.ShuffleDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShuffleDeckTest {
    private val pool = listOf("a", "b", "c", "d")

    @Test fun everyScreensaverShowsOnceBeforeAnyRepeats() {
        var deck = emptyList<String>()
        var last: String? = null
        val seen = mutableListOf<String>()
        repeat(pool.size) {
            val d = ShuffleDeck.draw(pool, deck, last, Random(it))
            seen += d.id
            deck = d.remaining
            last = d.id
        }
        assertEquals(pool.toSet(), seen.toSet())
        assertTrue(deck.isEmpty())
    }

    @Test fun aNewDeckNeverStartsWithTheLastOne() {
        repeat(50) { seed ->
            val d = ShuffleDeck.draw(pool, emptyList(), "c", Random(seed))
            assertNotEquals("c", d.id)
        }
    }

    @Test fun cardsRemovedFromThePoolAreSkipped() {
        val d = ShuffleDeck.draw(listOf("a", "b"), listOf("x", "b", "a"), null)
        assertEquals("b", d.id)
        assertEquals(listOf("a"), d.remaining)
    }

    @Test fun singleScreensaverAlwaysShows() {
        val d = ShuffleDeck.draw(listOf("only"), emptyList(), "only")
        assertEquals("only", d.id)
    }
}
