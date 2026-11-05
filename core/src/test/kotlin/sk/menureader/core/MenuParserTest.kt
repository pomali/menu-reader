package sk.menureader.core

import sk.menureader.core.model.Allergen
import sk.menureader.core.parse.MenuParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MenuParserTest {
    private val parser = MenuParser()

    @Test fun sectionsDishesPricesCodes() {
        val r = parser.parse(simpleDoc(
            "POLIEVKY",
            "Kapustnica s klobásou 0,33 l (1,7,9) 3,20 €",
            "Hovädzí vývar s rezancami 0,33 l (1,3,9) 2,50 €",
            "HLAVNÉ JEDLÁ",
            "1. Vyprážaný syr, hranolky, tatárska omáčka ....... (1,3,7) ..... 8,90 €",
            "2. Kuracie prsia na grile 150 g 7,50 €",
        ))
        assertEquals(listOf("POLIEVKY", "HLAVNÉ JEDLÁ"), r.sections.map { it.name })
        val soup = r.sections[0].dishes[0]
        assertEquals("Kapustnica s klobásou", soup.name)
        assertEquals("0,33 l", soup.portion)
        assertEquals(listOf(1, 7, 9), soup.printedCodes)
        assertEquals(320L, soup.prices.single().amountMinor)
        val syr = r.sections[1].dishes[0]
        assertEquals("Vyprážaný syr, hranolky, tatárska omáčka", syr.name)
        assertEquals(listOf(1, 3, 7), syr.printedCodes)
        assertTrue(syr.confidence > 0.8)
        assertEquals("150 g", r.sections[1].dishes[1].portion)
    }

    @Test fun nameAndPriceInSeparateOcrLines() {
        // ML Kit often returns the price on the right as its own line on the same baseline.
        val p = page {
            line("Hlavné jedlá", 50, 40, h = 44)
            line("Bryndzové halušky so slaninou (1,3,7)", 50, 120, 600)
            line("7,90 €", 850, 122, 950)
            line("Pirohy s bryndzou (1,3,7)", 50, 170, 450)
            line("7,50 €", 850, 171, 950)
        }
        val r = parser.parse(doc(p))
        val dishes = r.sections.single().dishes
        assertEquals(listOf("Bryndzové halušky so slaninou", "Pirohy s bryndzou"), dishes.map { it.name })
        assertEquals(listOf(790L, 750L), dishes.map { it.prices.single().amountMinor })
    }

    @Test fun descriptionBelowDish() {
        val r = parser.parse(simpleDoc(
            "Caesar šalát 9,90 €",
            "rímsky šalát, kuracie mäso, parmezán, krutóny",
            "Grécky šalát 7,90 €",
        ))
        val d = r.sections.single().dishes
        assertEquals("rímsky šalát, kuracie mäso, parmezán, krutóny", d[0].description)
        assertNull(d[1].description)
    }

    @Test fun nameOnOneLineCodesAndPriceOnNext() {
        val r = parser.parse(simpleDoc("Svíčková na smetaně, houskový knedlík", "(1,3,7,9,10) 189,-"))
        val d = r.sections.single().dishes.single()
        assertEquals("Svíčková na smetaně, houskový knedlík", d.name)
        assertEquals(listOf(1, 3, 7, 9, 10), d.printedCodes)
        assertEquals(18900L, d.prices.single().amountMinor)
    }

    @Test fun twoColumns() {
        val p = page {
            line("Kofola 0,5 l 2,00 €", 40, 100, 420)
            line("Espresso 1,80 €", 560, 100, 900)
            line("Pivo 0,5 l 2,50 €", 40, 150, 420)
            line("Cappuccino (7) 2,40 €", 560, 150, 900)
        }
        val names = parser.parse(doc(p)).sections.flatMap { it.dishes }.map { it.name }
        assertEquals(listOf("Kofola", "Pivo", "Espresso", "Cappuccino"), names)
    }

    @Test fun legendIsParsedNotADish() {
        val r = parser.parse(simpleDoc(
            "Guláš (1,9) 6,50 €",
            "1 - obilniny obsahujúce lepok, 9 - zeler",
        ))
        assertEquals(1, r.sections.single().dishes.size)
        assertEquals(mapOf(1 to Allergen.GLUTEN, 9 to Allergen.CELERY), r.legend)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun drinkVariants() {
        val d = parser.parse(simpleDoc("Zlatý Bažant 0,3 l 1,80 € / 0,5 l 2,40 €")).sections.single().dishes.single()
        assertEquals(listOf("0,3 l" to 180L, "0,5 l" to 240L), d.prices.map { it.variant to it.amountMinor })
    }
}
