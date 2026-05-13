package dev.ishubhamsingh.shelfly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ishubhamsingh.shelfly.R
import dev.ishubhamsingh.shelfly.domain.model.Category

private data class CategoryStyle(
    val icon: ImageVector,
    val background: @Composable () -> Color,
    val tint: @Composable () -> Color,
)

@Composable
private fun categoryStyle(category: Category): CategoryStyle = when (category) {
    Category.FOOD -> CategoryStyle(
        icon       = Icons.Filled.Restaurant,
        background = { MaterialTheme.colorScheme.primaryContainer },
        tint       = { MaterialTheme.colorScheme.onPrimaryContainer },
    )
    Category.MEDICINE -> CategoryStyle(
        icon       = Icons.Filled.Medication,
        background = { MaterialTheme.colorScheme.tertiaryContainer },
        tint       = { MaterialTheme.colorScheme.onTertiaryContainer },
    )
    Category.COSMETIC -> CategoryStyle(
        icon       = Icons.Filled.Spa,
        background = { MaterialTheme.colorScheme.secondaryContainer },
        tint       = { MaterialTheme.colorScheme.onSecondaryContainer },
    )
    Category.OTHER -> CategoryStyle(
        icon       = Icons.Filled.Inventory2,
        background = { MaterialTheme.colorScheme.surfaceContainerHigh },
        tint       = { MaterialTheme.colorScheme.onSurfaceVariant },
    )
}

@Composable
fun CategoryAvatar(
    category: Category,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val style = categoryStyle(category)
    Box(
        modifier        = modifier
            .size(size)
            .background(color = style.background(), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = style.icon,
            contentDescription = stringResource(R.string.cd_category_icon, category.name),
            tint               = style.tint(),
            modifier           = Modifier.size(size * 0.55f),
        )
    }
}

/**
 * Small pill-shaped badge combining a category icon and an item name.
 * Used in the notification preview card in Settings.
 */
@Composable
fun CategoryChip(
    category: Category,
    label: String,
    modifier: Modifier = Modifier,
) {
    val style = categoryStyle(category)
    Row(
        modifier          = modifier
            .background(color = style.background(), shape = RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = style.icon,
            contentDescription = null,
            tint               = style.tint(),
            modifier           = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text  = label,
            color = style.tint(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        )
    }
}
