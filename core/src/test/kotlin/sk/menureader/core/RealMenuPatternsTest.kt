package sk.menureader.core

import sk.menureader.core.model.Allergen
import sk.menureader.core.parse.Legend
import sk.menureader.core.parse.MenuParser
import sk.menureader.core.parse.Token
import sk.menureader.core.parse.Tokenizer
import sk.menureader.core.parse.Weekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Patterns seen on the user's real SK menus (samples/menu*.jpg). */
class RealMenuPatternsTest {
    private fun codes(s: String) = Tokenizer.tokenize(s).filterIsInstance<Token.Codes>().flatMap { it.codes }
    private fun name(s: String) = Tokenizer.strip(s, Tokenizer.tokenize(s))
    private val parser = MenuParser()

    @Test fun codeStyles() {
        assertEquals(listOf(1, 3, 7), codes("HRANOLKY S KEČUPOM /1,3,7/"))
        assertEquals(listOf(3), codes("Bojnícka pochúťka, šalát /3 7,90€"))
        assertEquals(listOf(1, 3, 9), codes("Vývar s rezancami (A1,3,9)"))
        assertEquals(listOf(1, 3, 7), codes("Burger (1,3,7,)"))
        assertEquals(listOf(1, 3, 7), codes("Vyprážaný karfiol 1.3,7 7,50 €"))
        assertEquals(listOf(1, 7, 9, 15), codes("Strapačky (1,9,15,7)"))
        assertEquals(emptyList(), codes("02.11.2026 PONDELOK"))
        assertEquals(emptyList(), codes("1/2 dusená ryža, 1/2 hranolky"))
    }

    @Test fun itemMarkersAndPortions() {
        assertEquals("Bravčový stroganov", name("M1: 200g/200g Bravčový stroganov"))
        assertEquals("Strapačky s kyslou kapustou", name("M1-120g Strapačky s kyslou kapustou"))
        assertEquals("Kelová s klobáskou", name("1.Kelová s klobáskou"))
        val portion = Tokenizer.tokenize("M3: 400g/50g Domáce halušky").filterIsInstance<Token.Portion>().single()
        assertEquals("400 g / 50 g", portion.text)
    }

    @Test fun weekdays() {
        assertEquals("Pondelok 26.10.2026" to "Polievka: Paradajková /1,3,7", Weekday.split("Pondelok /26.10.2026 ..... Polievka: Paradajková /1,3,7")?.let { it.first to it.second })
        assertEquals("02.11.2026 PONDELOK", Weekday.split("02.11.2026 PONDELOK")?.first)
        assertEquals("Štvrtok 10.7.2025", Weekday.split("Štvrtok 10.7.2025")?.first)
        assertNull(Weekday.split("Stredná porcia"))
    }

    @Test fun legendWithNoiseAndCustomCodes() {
        val m = Legend.parseLine("12. Oxid siričitý a siričitany v koncentráciách vyšších ako 10 mg/kg alebo 10 mg/l.")!!
        assertEquals(Allergen.SULPHITES, m[12])
        val mixed = Legend.parseLine("2. Kôrovce a výrobky z nich. 8. Orechy. 15. Med a výrobky z nich")!!
        assertEquals(mapOf(2 to Allergen.CRUSTACEANS, 8 to Allergen.TREE_NUTS), mixed)
    }

    @Test fun weeklyMenuSectionsAndSoupsWithoutPrice() {
        val r = parser.parse(simpleDoc(
            "Štvrtok 10.7.2025",
            "Polievky",
            "Polievka A: 0,33l Talianska šošovicová",
            "Polievka B: 0,33l Vývar s kuracím mäsom (A1,3,9)",
            "Hotové jedlá",
            "M1: 200g/200g Bravčový stroganov, ½ ryža (A1,7) 7,50€",
        ))
        assertEquals(listOf("Štvrtok 10.7.2025 · Polievky", "Štvrtok 10.7.2025 · Hotové jedlá"), r.sections.map { it.name })
        assertEquals(listOf("Polievka A: Talianska šošovicová", "Polievka B: Vývar s kuracím mäsom"), r.sections[0].dishes.map { it.name })
        assertEquals(listOf(1, 3, 9), r.sections[0].dishes[1].printedCodes)
        assertEquals("Bravčový stroganov, ½ ryža", r.sections[1].dishes.single().name)
    }

    @Test fun uppercaseDishWithDescriptionAndSlashCodes() {
        // menu8: name + price on one row, smaller capital description below, codes at its end.
        val p = page {
            line("MINI MENU", 60, 100, h = 22)
            line("HRANOLKY", 60, 160, 120); line("3.50", 260, 158, 290)
            line("HRANOLKY S KEČUPOM ALEBO MAJONÉZOU", 60, 180, 220, h = 12)
            line("/1,3,7/", 60, 196, 100, h = 12)
            line("PIZZA BAMBINI", 60, 220, 150); line("6.90", 260, 218, 290)
            line("DETSKÁ PIZZA S PARADAJKOVÝM ZÁKLADOM /1,7/", 60, 240, 240, h = 12)
        }
        val d = parser.parse(doc(p)).sections.single().dishes
        assertEquals(listOf("HRANOLKY", "PIZZA BAMBINI"), d.map { it.name })
        assertEquals(listOf(listOf(1, 3, 7), listOf(1, 7)), d.map { it.printedCodes })
        assertEquals("HRANOLKY S KEČUPOM ALEBO MAJONÉZOU", d[0].description)
    }

    @Test fun priceRowContinuation() {
        val r = parser.parse(simpleDoc(
            "M3. PIZZA - Šunková alebo Klobásová /1,7 7,90€",
            "(bez polievky 7,90€/s polievkou 8,90€)",
            "M4. Francúzske zemiaky /3,7 7,90€",
        ))
        val d = r.sections.single().dishes
        assertEquals(2, d.size)
        assertEquals(listOf(1, 7), d[0].printedCodes)
    }
}

class PortionTest {
    @Test fun pretty() {
        assertEquals("200 g / 200 g", Tokenizer.prettyPortion("200g/200g"))
        assertEquals("0,33 l", Tokenizer.prettyPortion("0,33L"))
        assertEquals("1 ks", Tokenizer.prettyPortion("1KS"))
        assertEquals("100 g", Tokenizer.prettyPortion("100gr"))
        assertEquals("0,3 l / 0,5 l", Tokenizer.prettyPortion("0,3/0,5l"))
    }

    @Test fun portionFromDescription() {
        val d = MenuParser().parse(simpleDoc("NEAPOLKA Bufala 8,90 €", "(450g) paradajky La Torrente, Bufala")).sections.single().dishes.single()
        assertEquals("450 g", d.portion)
        assertEquals("paradajky La Torrente, Bufala", d.description)
    }
}
