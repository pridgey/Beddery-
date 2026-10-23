package dev.pridgey.beddery

import dev.pridgey.beddery.savers.Savers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaversTest {
    @Test fun idsAreUnique() = assertEquals(Savers.all.size, Savers.all.map { it.id }.toSet().size)

    @Test fun unknownIdFallsBackToDefault() = assertEquals("minimal", Savers.find("nope").id)

    @Test fun hasPlentyOfVariety() = assertTrue(Savers.all.size >= 50)

    @Test fun refinedVersionsPointAtRealOriginals() {
        val refined = Savers.all.filter { it.refined }
        assertEquals(1, refined.size)
        refined.forEach { r ->
            val original = assertNotNull(Savers.findOrNull(r.variantOf)).let { Savers.find(r.variantOf) }
            assertEquals(original.category, r.category)
            assertEquals(original.id, Savers.counterpart(r.id)?.id)
            assertEquals(r.id, Savers.counterpart(original.id)?.id)
        }
    }

    @Test fun retiredIdsStillResolve() {
        assertEquals("ocean", Savers.find("ocean_refined").id)
        assertEquals("constellation", Savers.find("constellation_refined").id)
        assertEquals("lava", Savers.find("lava_refined").id)
    }

    @Test fun refinedSitsRightAfterItsOriginalForSwipeComparison() {
        Savers.all.forEachIndexed { i, s ->
            if (s.refined) assertEquals(s.variantOf, Savers.all[i - 1].id)
        }
    }
}
