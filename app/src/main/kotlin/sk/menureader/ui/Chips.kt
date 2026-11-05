package sk.menureader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import sk.menureader.core.model.Layer

/**
 * Chip styled by where the claim comes from (PLAN §5): filled = printed on the menu,
 * outlined = an ingredient word, dashed + "?" = typical recipe ("may contain").
 * Avoided things use the error colour. Tap to see why it's there.
 */
@Composable
fun LayerChip(label: String, layer: Layer, avoided: Boolean, onClick: () -> Unit) {
    // Only what you avoid is loud (red); everything else stays a calm neutral.
    val accent = if (avoided) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = MaterialTheme.shapes.small
    val text = label + if (layer == Layer.INFERRED) " ?" else ""
    val (container, content, border) = when (layer) {
        Layer.PRINTED ->
            if (avoided) Triple(accent, MaterialTheme.colorScheme.onError, null)
            else Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, null)
        Layer.LEXICAL -> Triple(Color.Transparent, accent, BorderStroke(1.5.dp, accent))
        Layer.INFERRED -> Triple(Color.Transparent, accent, null)
    }
    Surface(
        onClick = onClick, shape = shape, color = container, contentColor = content, border = border,
        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)
            .then(if (layer == Layer.INFERRED) Modifier.dashedBorder(accent) else Modifier),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

/** A neutral chip (e.g. "blurry?"). */
@Composable
fun NoteChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

private fun Modifier.dashedBorder(color: Color) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(12.dp.toPx()),
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
    )
}
