package sk.menureader.core

import sk.menureader.core.model.Allergen
import sk.menureader.core.parse.Legend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegendTest {
    @Test fun multiEntryLine() {
        val m = Legend.parseLine("Alergény: 1 - obilniny obsahujúce lepok, 3 - vajcia, 7 - mlieko")!!
        assertEquals(mapOf(1 to Allergen.GLUTEN, 3 to Allergen.EGGS, 7 to Allergen.MILK), m)
    }

    @Test fun czechSingleEntry() {
        assertEquals(mapOf(12 to Allergen.SULPHITES), Legend.parseLine("12. Oxid siřičitý a siřičitany"))
        assertEquals(mapOf(8 to Allergen.TREE_NUTS), Legend.parseLine("8 – Skořápkové plody"))
        assertEquals(mapOf(5 to Allergen.PEANUTS), Legend.parseLine("5 – Podzemnice olejná (arašídy)"))
    }

    @Test fun dishLineIsNotLegend() {
        assertNull(Legend.parseLine("3. Kuracie prsia so zemiakmi"))
    }

    @Test fun nonStandardNumberingWarns() {
        val w = Legend.mismatches(mapOf(1 to Allergen.MILK))
        assertTrue(w.single().contains("EU numbering"))
    }
}
