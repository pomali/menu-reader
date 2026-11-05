package sk.menureader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import sk.menureader.core.model.AllergenClaim
import sk.menureader.core.model.Layer

/**
 * Allergen chip styled by layer (PLAN §5): solid = printed, outline = lexical,
 * dashed + "?" = inferred "may contain". Avoided allergens use the error colour.
 */
@Composable
fun AllergenChip(claim: AllergenClaim, avoided: Boolean) {
    val accent = if (avoided) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    val text = label(claim.allergen) + if (claim.layer == Layer.INFERRED) "?" else ""
    val base = Modifier.padding(end = 6.dp, bottom = 6.dp).clip(shape)
    when (claim.layer) {
        Layer.PRINTED -> Text(
            text, color = if (avoided) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            modifier = base.background(accent).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Layer.LEXICAL -> Text(
            text, color = accent, style = MaterialTheme.typography.labelMedium,
            modifier = base.border(1.5.dp, accent, shape).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Layer.INFERRED -> Text(
            text, color = accent, style = MaterialTheme.typography.labelMedium,
            modifier = base.dashedBorder(accent).padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun Modifier.dashedBorder(color: Color) = drawBehind {
    val w = 1.5.dp.toPx()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}
