package sk.menureader.core

import sk.menureader.core.parse.Token
import sk.menureader.core.parse.Tokenizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenizerTest {
    private fun prices(s: String) = Tokenizer.tokenize(s).filterIsInstance<Token.PriceTok>().map { it.price.amountMinor to it.price.currency }
    private fun codes(s: String) = Tokenizer.tokenize(s).filterIsInstance<Token.Codes>().flatMap { it.codes }
    private fun portions(s: String) = Tokenizer.tokenize(s).filterIsInstance<Token.Portion>().map { it.text }

    @Test fun euroPrices() {
        assertEquals(listOf(890L to "EUR"), prices("Vyprážaný syr 8,90 €"))
        assertEquals(listOf(890L to "EUR"), prices("Vyprážaný syr 8.90€"))
        assertEquals(listOf(1250L to "EUR"), prices("Steak 12,50 EUR"))
        assertEquals(listOf(750L to "EUR"), prices("Burger € 7,50"))
        assertEquals(listOf(600L to "EUR"), prices("Menu 2 6 €"))
    }

    @Test fun czkPrices() {
        assertEquals(listOf(18900L to "CZK"), prices("Svíčková 189,-"))
        assertEquals(listOf(18900L to "CZK"), prices("Svíčková 189 Kč"))
        assertEquals(listOf(129000L to "CZK"), prices("Tatarák 1 290 Kč"))
    }

    @Test fun bareTrailingPrice() {
        assertEquals(listOf(450L to null), prices("Kofola 0,5 l 4,50"))
    }

    @Test fun allergenCodes() {
        assertEquals(listOf(1, 3, 7), codes("Vyprážaný syr (1,3,7) 8,90 €"))
        assertEquals(listOf(1, 3, 7), codes("Vyprážaný syr A: 1, 3, 7 8,90 €"))
        assertEquals(listOf(1, 3, 7), codes("Vyprážaný syr Alergény: 1,3,7"))
        assertEquals(listOf(1, 7, 9), codes("Kapustnica 1,7,9 3,20 €"))
        assertEquals(listOf(1, 10), codes("Párky s horčicou (1,10) 3,50 €"))
        assertEquals(listOf(1, 3, 7), codes("Rezeň A1,3,7 8,90€"))
    }

    @Test fun codesInParenthesesAreNotPrices() {
        assertEquals(emptyList(), prices("Párky s horčicou (1,10)"))
    }

    @Test fun slovakAndIsNotAPrefix() {
        // "a" = "and"; "a 2 ks" must not become allergen 2.
        assertEquals(emptyList(), codes("Párky a 2 ks rožok 3,50 €"))
    }

    @Test fun portions() {
        assertEquals(listOf("150 g"), portions("150 g Kuracie prsia 7,50 €"))
        assertEquals(listOf("0,33 l"), portions("Polievka 0,33 l (1,9)"))
        assertEquals(listOf("2 ks"), portions("Palacinky 2 ks 4,20 €"))
    }

    @Test fun weightIsNotPriceOrCode() {
        val t = Tokenizer.tokenize("Hovädzí guláš 300g 9,50 €")
        assertTrue(t.none { it is Token.Codes })
        assertEquals(listOf(950L to "EUR"), prices("Hovädzí guláš 300g 9,50 €"))
    }

    @Test fun nameIsWhatRemains() {
        val s = "1. Vyprážaný syr ....... (1,3,7) ........ 8,90 €"
        val clean = sk.menureader.core.parse.Text.clean(s)
        assertEquals("Vyprážaný syr", Tokenizer.strip(clean, Tokenizer.tokenize(clean)))
    }
}
