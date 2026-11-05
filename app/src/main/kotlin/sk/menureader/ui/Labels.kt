package sk.menureader.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import sk.menureader.R
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.DietTag

@StringRes
fun Allergen.labelRes(): Int = when (this) {
    Allergen.GLUTEN -> R.string.allergen_1
    Allergen.CRUSTACEANS -> R.string.allergen_2
    Allergen.EGGS -> R.string.allergen_3
    Allergen.FISH -> R.string.allergen_4
    Allergen.PEANUTS -> R.string.allergen_5
    Allergen.SOY -> R.string.allergen_6
    Allergen.MILK -> R.string.allergen_7
    Allergen.TREE_NUTS -> R.string.allergen_8
    Allergen.CELERY -> R.string.allergen_9
    Allergen.MUSTARD -> R.string.allergen_10
    Allergen.SESAME -> R.string.allergen_11
    Allergen.SULPHITES -> R.string.allergen_12
    Allergen.LUPIN -> R.string.allergen_13
    Allergen.MOLLUSCS -> R.string.allergen_14
}

@StringRes
fun DietTag.labelRes(): Int = when (this) {
    DietTag.VEGETARIAN -> R.string.tag_VEGETARIAN
    DietTag.VEGAN -> R.string.tag_VEGAN
    DietTag.MEAT -> R.string.tag_MEAT
    DietTag.PORK -> R.string.tag_PORK
    DietTag.BEEF -> R.string.tag_BEEF
    DietTag.POULTRY -> R.string.tag_POULTRY
    DietTag.LAMB -> R.string.tag_LAMB
    DietTag.ONION -> R.string.tag_ONION
    DietTag.GARLIC -> R.string.tag_GARLIC
    DietTag.SPICY -> R.string.tag_SPICY
    DietTag.CORIANDER -> R.string.tag_CORIANDER
    DietTag.TURMERIC -> R.string.tag_TURMERIC
    DietTag.MUSHROOMS -> R.string.tag_MUSHROOMS
    DietTag.TOMATO -> R.string.tag_TOMATO
    DietTag.PINEAPPLE -> R.string.tag_PINEAPPLE
    DietTag.ALCOHOL -> R.string.tag_ALCOHOL
}

/** Everything except the positive diet labels (vegetarian/vegan) can be avoided. */
val avoidableTags = DietTag.entries - setOf(DietTag.VEGETARIAN, DietTag.VEGAN)

val Allergen.emoji: String get() = when (this) {
    Allergen.GLUTEN -> "🌾"; Allergen.CRUSTACEANS -> "🦐"; Allergen.EGGS -> "🥚"; Allergen.FISH -> "🐟"
    Allergen.PEANUTS -> "🥜"; Allergen.SOY -> "🫘"; Allergen.MILK -> "🥛"; Allergen.TREE_NUTS -> "🌰"
    Allergen.CELERY -> "🥬"; Allergen.MUSTARD -> "🌭"; Allergen.SESAME -> "🥯"; Allergen.SULPHITES -> "🍷"
    Allergen.LUPIN -> "🌼"; Allergen.MOLLUSCS -> "🦑"
}

val DietTag.emoji: String get() = when (this) {
    DietTag.VEGETARIAN -> "🥕"; DietTag.VEGAN -> "🌱"; DietTag.MEAT -> "🥩"; DietTag.PORK -> "🐷"
    DietTag.BEEF -> "🐮"; DietTag.POULTRY -> "🐔"; DietTag.LAMB -> "🐑"; DietTag.ONION -> "🧅"
    DietTag.GARLIC -> "🧄"; DietTag.SPICY -> "🌶️"; DietTag.CORIANDER -> "🌿"; DietTag.TURMERIC -> "🟠"
    DietTag.MUSHROOMS -> "🍄"; DietTag.TOMATO -> "🍅"; DietTag.PINEAPPLE -> "🍍"; DietTag.ALCOHOL -> "🍺"
}

/** "🥛 7 Milk": emoji, EU number, name. */
@Composable fun label(a: Allergen) = "${a.emoji} ${a.code} ${stringResource(a.labelRes())}"
@Composable fun label(t: DietTag) = "${t.emoji} ${stringResource(t.labelRes())}"
