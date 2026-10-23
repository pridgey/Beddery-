package dev.pridgey.beddery

import dev.pridgey.beddery.savers.Savers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaversTest {
    @Test fun idsAreUnique() = assertEquals(Savers.all.size, Savers.all.map { it.id }.toSet().size)

    @Test fun unknownIdFallsBackToDefault() = assertEquals("minimal", Savers.find("nope").id)

    @Test fun hasPlentyOfVariety() = assertTrue(Savers.all.size >= 20)
}
