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
            lineTo(16f, 19f)
        }
        path(fill = SolidColor(color)) {
            moveTo(16f, 21f)
            curveTo(11f, 21f, 7f, 17f, 7f, 13f)
            curveTo(12f, 13f, 16f, 17f, 16f, 21f)
            close()
        }
        path(fill = SolidColor(color)) {
            moveTo(16f, 19f)
            curveTo(16f, 13f, 20f, 10f, 24f, 10f)
            curveTo(24f, 15f, 20f, 19f, 16f, 19f)
            close()
        }
    }.build()

    Image(
        imageVector        = icon,
        contentDescription = null,
        modifier           = modifier.size(size),
    )
}
