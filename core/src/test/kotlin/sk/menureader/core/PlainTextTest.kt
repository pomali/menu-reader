package sk.menureader.core

import sk.menureader.core.parse.MenuParser
import sk.menureader.core.parse.PlainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlainTextTest {
    @Test fun deliveryAppStyle() {
        val text = """
            Polievky
            Kapustnica
            Hustá kapustnica s klobásou a smotanou
            3,90 €
            Hovädzí vývar s rezancami (1,3,9)
            2,90 €
            Hlavné jedlá
            Vyprážaný syr	8,90 €
        """.trimIndent()
        val r = MenuParser().parse(PlainText.toOcr(text))
        assertEquals(listOf("Polievky", "Hlavné jedlá"), r.sections.map { it.name })
        val soup = r.sections[0].dishes[0]
        assertEquals("Kapustnica", soup.name)
        assertEquals("Hustá kapustnica s klobásou a smotanou", soup.description)
        assertEquals(390L, soup.prices.single().amountMinor)
        assertEquals(listOf(1, 3, 9), r.sections[0].dishes[1].printedCodes)
        assertEquals(890L, r.sections[1].dishes.single().prices.single().amountMinor)
    }

    @Test fun linkOnlyShare() {
        assertTrue(PlainText.isOnlyLinks("Pozri si Bistro X na Wolte https://wolt.com/sk/svk/bratislava/restaurant/bistro-x"))
        assertFalse(PlainText.isOnlyLinks("Guláš 7,50 €\nhttps://example.com"))
    }
}
