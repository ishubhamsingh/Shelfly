package dev.ishubhamsingh.shelfly.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shelfly logomark: a sprouting leaf glyph (stem + left leaf + right sprout).
 * Original design — not copied from any brand.
 */
@Composable
fun ShelflyMark(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    val icon = ImageVector.Builder(
        name           = "ShelflyMark",
        defaultWidth   = 32.dp,
        defaultHeight  = 32.dp,
        viewportWidth  = 32f,
        viewportHeight = 32f,
    ).apply {
        path(
            stroke          = SolidColor(color),
            strokeLineWidth = 2.6f,
            strokeLineCap   = StrokeCap.Round,
        ) {
            moveTo(16f, 28f)
            lineTo(16f, 16f)
        }
        path(fill = SolidColor(color)) {
            moveTo(16f, 18f)
            curveTo(11f, 18f, 7f, 14f, 7f, 10f)
            curveTo(12f, 10f, 16f, 14f, 16f, 18f)
            close()
        }
        path(fill = SolidColor(color)) {
            moveTo(16f, 16f)
            curveTo(16f, 9f, 20f, 4f, 25f, 4f)
            curveTo(25f, 11f, 21f, 16f, 16f, 16f)
            close()
        }
    }.build()

    Image(
        imageVector        = icon,
        contentDescription = null,
        modifier           = modifier.size(size),
    )
}
