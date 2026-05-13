package dev.ishubhamsingh.shelfly.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Pixel-faithful reproduction of the Shelfly shelf illustration.
 * Switches palette between light and dark automatically.
 * Scales uniformly to any [modifier] size; the SVG viewport is 220×180.
 */
@Composable
fun ShelfIllustration(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()

    val bgColor     = if (isDark) Color(0xFF1A1C14) else Color(0xFFF5F6EE)
    val shelfColor  = if (isDark) Color(0xFF282B22) else Color(0xFFE9EBE2)
    val greenColor  = if (isDark) Color(0xFF275111) else Color(0xFFC0F0A1)
    val amberColor  = if (isDark) Color(0xFF5E4200) else Color(0xFFFFDEA8)
    val mutedColor  = if (isDark) Color(0xFF3F4A35) else Color(0xFFDAE8C9)
    val checkStroke = if (isDark) Color(0xFFC0F0A1) else Color(0xFF0E2300)

    Canvas(modifier = modifier) {
        val s  = minOf(size.width / 220f, size.height / 180f)
        val ox = (size.width  - 220f * s) / 2f
        val oy = (size.height - 180f * s) / 2f

        fun x(v: Float)  = ox + v * s
        fun y(v: Float)  = oy + v * s
        fun sz(v: Float) = v * s
        fun cr(v: Float) = CornerRadius(sz(v))

        drawRoundRect(color = bgColor,    topLeft = Offset(x(10f), y(10f)),  size = Size(sz(200f), sz(160f)), cornerRadius = cr(12f))
        drawRoundRect(color = shelfColor, topLeft = Offset(x(22f), y(56f)),  size = Size(sz(176f), sz(8f)),  cornerRadius = cr(2f))
        drawRoundRect(color = shelfColor, topLeft = Offset(x(22f), y(112f)), size = Size(sz(176f), sz(8f)),  cornerRadius = cr(2f))
        drawRoundRect(color = greenColor, topLeft = Offset(x(44f), y(28f)),  size = Size(sz(28f),  sz(28f)), cornerRadius = cr(4f))
        drawRoundRect(color = greenColor, topLeft = Offset(x(48f), y(22f)),  size = Size(sz(20f),  sz(8f)),  cornerRadius = cr(2f))
        drawRoundRect(color = amberColor, topLeft = Offset(x(86f), y(20f)),  size = Size(sz(18f),  sz(36f)), cornerRadius = cr(4f))
        drawRect(     color = amberColor, topLeft = Offset(x(92f), y(14f)),  size = Size(sz(6f),   sz(8f)))
        drawRoundRect(color = mutedColor, topLeft = Offset(x(120f), y(34f)), size = Size(sz(34f),  sz(22f)), cornerRadius = cr(3f))
        drawRoundRect(color = amberColor, topLeft = Offset(x(40f),  y(80f)), size = Size(sz(40f),  sz(32f)), cornerRadius = cr(4f))
        drawRoundRect(color = greenColor, topLeft = Offset(x(96f),  y(84f)), size = Size(sz(22f),  sz(28f)), cornerRadius = cr(3f))
        drawRoundRect(color = mutedColor, topLeft = Offset(x(132f), y(76f)), size = Size(sz(26f),  sz(36f)), cornerRadius = cr(4f))
        drawCircle(color = greenColor, radius = sz(14f), center = Offset(x(186f), y(40f)))
        val checkPath = Path().apply {
            moveTo(x(180f), y(41f)); lineTo(x(184f), y(45f)); lineTo(x(192f), y(36f))
        }
        drawPath(checkPath, color = checkStroke, style = Stroke(width = sz(2.4f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
