package sk.menureader.core

import kotlinx.serialization.encodeToString
import sk.menureader.core.allergen.printedMismatches
import sk.menureader.core.filter.Query
import sk.menureader.core.filter.Ranker
import sk.menureader.core.filter.Reason
import sk.menureader.core.filter.Strictness
import sk.menureader.core.filter.UserProfile
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.Layer
import sk.menureader.core.model.Quality
import sk.menureader.core.model.Snapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractAndFilterTest {
    private val snapshot = LocalExtractor().extract(
        id = "t1",
        ocr = simpleDoc(
            "DENNÉ MENU",
            "Paradajková polievka (1,7) 2,50 €",
            "Kuracie prsia, ryža (7) 7,90 €",
            "Zeleninové rizoto so smotanou 6,90 €",
            "Grilovaná zelenina 6,50 €",
        ),
        pages = emptyList(),
        capturedAt = 0,
    )

    @Test fun basicSnapshot() {
        assertEquals(Quality.BASIC, snapshot.quality)
        assertEquals("EUR", snapshot.currency)
        assertEquals(4, snapshot.dishes.size)
    }

    @Test fun layersAndMismatch() {
        val chicken = snapshot.dishes[1]
        assertEquals(listOf(Allergen.MILK), chicken.allergens.filter { it.layer == Layer.PRINTED }.map { it.allergen })
        val risotto = snapshot.dishes[2]
        val milk = risotto.allergens.single { it.allergen == Allergen.MILK }
        assertEquals(Layer.LEXICAL, milk.layer)
        assertEquals("'smotanou'", milk.evidence)
        // Soup prints (1,7); the weak "polievka -> celery" hint is a mismatch the UI shows.
        val soup = snapshot.dishes[0]
        assertTrue(soup.printedMismatches().any { it.allergen == Allergen.CELERY && it.layer == Layer.INFERRED })
    }

    @Test fun reorderNotHide() {
        val ranked = Ranker.rank(snapshot, UserProfile(avoid = setOf(Allergen.MILK)))
        val dishes = ranked.single().dishes
        assertEquals(4, dishes.size)
        assertEquals("Grilovaná zelenina", dishes.first().dish.name)
        assertTrue(dishes.drop(1).all { it.flagged })
        assertTrue(dishes.first().noPrintedCodes)
    }

    @Test fun strictnessLimitsLayers() {
        val printedOnly = UserProfile(avoid = setOf(Allergen.MILK), strictness = Strictness.PRINTED_ONLY)
        val r = Ranker.rank(snapshot, printedOnly).single().dishes
        assertEquals(listOf("Zeleninové rizoto so smotanou", "Grilovaná zelenina"), r.filter { !it.flagged }.map { it.dish.name })
    }

    @Test fun priceAndTextQuery() {
        val r = Ranker.rank(snapshot, UserProfile(), Query(text = "RIZOTO", maxPriceMinor = 700)).single().dishes
        assertEquals("Zeleninové rizoto so smotanou", r.first().dish.name)
        assertTrue(r.first { it.dish.name.startsWith("Kuracie") }.reasons.any { it is Reason.OverPrice })
    }

    @Test fun jsonRoundTrip() {
        val json = MenuJson.encodeToString(snapshot)
        assertEquals(snapshot, MenuJson.decodeFromString<Snapshot>(json))
    }
}
