package sk.menureader.core.model

import kotlinx.serialization.Serializable

/**
 * The 14 allergens of EU Regulation 1169/2011, Annex II, in the official order.
 * [code] is the number SK/CZ menus print next to dishes ("(1,3,7)").
 */
@Serializable
enum class Allergen(val code: Int, val en: String, val sk: String) {
    GLUTEN(1, "Cereals containing gluten", "Obilniny obsahujúce lepok"),
    CRUSTACEANS(2, "Crustaceans", "Kôrovce"),
    EGGS(3, "Eggs", "Vajcia"),
    FISH(4, "Fish", "Ryby"),
    PEANUTS(5, "Peanuts", "Arašidy"),
    SOY(6, "Soybeans", "Sója"),
    MILK(7, "Milk", "Mlieko"),
    TREE_NUTS(8, "Tree nuts", "Orechy"),
    CELERY(9, "Celery", "Zeler"),
    MUSTARD(10, "Mustard", "Horčica"),
    SESAME(11, "Sesame", "Sezam"),
    SULPHITES(12, "Sulphur dioxide and sulphites", "Oxid siričitý a siričitany"),
    LUPIN(13, "Lupin", "Vlčí bôb"),
    MOLLUSCS(14, "Molluscs", "Mäkkýše");

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Int): Allergen? = byCode[code]
    }
}

/** Dietary tags, kept separate from allergens. */
@Serializable
enum class DietTag { VEGETARIAN, VEGAN, PORK, BEEF, POULTRY, SPICY, ALCOHOL }

/** Where an allergen claim comes from. Ordered from most to least trusted. */
@Serializable
enum class Layer { PRINTED, LEXICAL, INFERRED }
