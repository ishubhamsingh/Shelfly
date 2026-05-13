package dev.ishubhamsingh.shelfly.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.ishubhamsingh.shelfly.domain.model.ItemStatus
import dev.ishubhamsingh.shelfly.ui.theme.LocalShelflyStatusColors

@Composable
fun StatusBadge(
    status: ItemStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalShelflyStatusColors.current
    val (bg, fg) = when (status) {
        ItemStatus.GOOD          -> colors.goodContainer to colors.onGoodContainer
        ItemStatus.EXPIRING_SOON -> colors.soonContainer to colors.onSoonContainer
        ItemStatus.EXPIRED       -> colors.expiredContainer to colors.onExpiredContainer
        ItemStatus.CONSUMED      -> colors.consumedContainer to colors.onConsumedContainer
    }
    Surface(
        shape    = MaterialTheme.shapes.extraSmall,
        color    = bg,
        modifier = modifier,
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
