package dev.ishubhamsingh.shelfly.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ShelflyShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // chips, snackbars
    small      = RoundedCornerShape(8.dp),   // buttons, text fields
    medium     = RoundedCornerShape(12.dp),  // cards
    large      = RoundedCornerShape(16.dp),  // sheets, dialogs
    extraLarge = RoundedCornerShape(28.dp),  // FAB, bottom sheets
)
