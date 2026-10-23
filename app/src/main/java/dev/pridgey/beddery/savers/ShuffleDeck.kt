package dev.pridgey.beddery.savers

import kotlin.random.Random

/**
 * "New screensaver each time it starts" works like a shuffled deck of cards: every screensaver
 * in the pool shows once before any repeats, and a fresh deck never starts with the last one shown.
 */
object ShuffleDeck {

    data class Draw(val id: String, val remaining: List<String>)

    fun draw(pool: List<String>, deck: List<String>, last: String?, random: Random = Random.Default): Draw {
        require(pool.isNotEmpty())
        var cards = deck.filter { it in pool }.distinct()
        if (cards.isEmpty()) {
            cards = pool.distinct().shuffled(random)
            if (cards.size > 1 && cards.first() == last) cards = cards.drop(1) + cards.first()
        }
        return Draw(cards.first(), cards.drop(1))
    }
}
