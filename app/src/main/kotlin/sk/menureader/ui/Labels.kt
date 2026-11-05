package sk.menureader.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import sk.menureader.R
import sk.menureader.core.model.Allergen
import sk.menureader.core.model.DietTag
import sk.menureader.core.model.Layer

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

@StringRes
fun Layer.labelRes(): Int = when (this) {
    Layer.PRINTED -> R.string.layer_printed
    Layer.LEXICAL -> R.string.layer_lexical
    Layer.INFERRED -> R.string.layer_inferred
}

@Composable fun label(a: Allergen) = "${a.code} ${stringResource(a.labelRes())}"
@Composable fun label(t: DietTag) = stringResource(t.labelRes())
@Composable fun label(l: Layer) = stringResource(l.labelRes())
