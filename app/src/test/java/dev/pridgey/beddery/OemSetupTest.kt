package dev.pridgey.beddery

import dev.pridgey.beddery.system.OemSetup
import dev.pridgey.beddery.system.OemSetup.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OemSetupTest {

    @Test
    fun recognisesBrands() {
        assertEquals(Brand.XIAOMI, OemSetup.brand("Xiaomi", "Redmi"))
        assertEquals(Brand.XIAOMI, OemSetup.brand("Xiaomi", "POCO"))
        assertEquals(Brand.OPPO, OemSetup.brand("OPPO", "OPPO"))
        assertEquals(Brand.OPPO, OemSetup.brand("OnePlus", "OnePlus"))
        assertEquals(Brand.OPPO, OemSetup.brand("realme", "realme"))
        assertEquals(Brand.VIVO, OemSetup.brand("vivo", "iQOO"))
        assertEquals(null, OemSetup.brand("Google", "google"))
        assertEquals(null, OemSetup.brand("samsung", "samsung"))
        assertEquals(null, OemSetup.brand("motorola", "motorola"))
    }

    @Test
    fun everyBrandHasSteps() {
        Brand.entries.forEach { assertTrue(OemSetup.steps(it).isNotEmpty()) }
        assertTrue(OemSetup.steps(null).isEmpty())
        val ids = Brand.entries.flatMap { b -> OemSetup.steps(b).map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }
}
