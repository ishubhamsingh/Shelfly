package dev.ishubhamsingh.shelfly.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ishubhamsingh.shelfly.R
import dev.ishubhamsingh.shelfly.domain.model.Item
import dev.ishubhamsingh.shelfly.domain.model.ItemStatus
import dev.ishubhamsingh.shelfly.domain.util.daysUntilExpiry
import dev.ishubhamsingh.shelfly.domain.util.formattedQty
import dev.ishubhamsingh.shelfly.domain.util.statusFor
import dev.ishubhamsingh.shelfly.ui.components.AddOptionsSheet
import dev.ishubhamsingh.shelfly.ui.components.CategoryAvatar
import dev.ishubhamsingh.shelfly.ui.components.ShelflyMark
import dev.ishubhamsingh.shelfly.ui.components.StatusBadge
import dev.ishubhamsingh.shelfly.ui.components.rememberIsAppFunctionsCapable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToForm: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isCapable = rememberIsAppFunctionsCapable()
    var showAddSheet by remember { mutableStateOf(false) }

    if (showAddSheet) {
        AddOptionsSheet(
            onDismiss      = { showAddSheet = false },
            onAskAssistant = { showAddSheet = false /* TODO: launch assistant intent */ },
            onAddManually  = onNavigateToForm,
        )
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                itemCount       = state.items.size,
                onSettingsClick = onNavigateToSettings,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick        = { if (isCapable) showAddSheet = true else onNavigateToForm() },
                icon           = { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add_item)) },
                text           = { Text(stringResource(R.string.form_add_cta)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            FilterRow(
                selected  = state.filter,
                onSelect  = viewModel::setFilter,
            )
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (state.items.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                ItemList(
                    items        = state.items,
                    settings     = state.settings,
                    onItemClick  = onNavigateToDetail,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    itemCount: Int,
    onSettingsClick: () -> Unit,
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ShelflyMark(color = MaterialTheme.colorScheme.primary, size = 28.dp)
                Text(
                    text  = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = MaterialTheme.colorScheme.primary,
                    ),
                )
                if (itemCount > 0) {
                    Text(
                        text  = stringResource(R.string.home_item_count, itemCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = {}) {
                Icon(
                    imageVector        = Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.cd_notifications),
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector        = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                )
            }
        },
    )
}

@Composable
private fun FilterRow(
    selected: HomeFilter,
    onSelect: (HomeFilter) -> Unit,
) {
    val filters = listOf(
        HomeFilter.ALL           to R.string.filter_all,
        HomeFilter.EXPIRING_SOON to R.string.filter_expiring_soon,
        HomeFilter.EXPIRED       to R.string.filter_expired,
        HomeFilter.CONSUMED      to R.string.filter_consumed,
    )
    LazyRow(
        contentPadding      = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier            = Modifier.padding(bottom = 8.dp),
    ) {
        items(filters) { (filter, labelRes) ->
            FilterChip(
                selected = filter == selected,
                onClick  = { onSelect(filter) },
                label    = { Text(stringResource(labelRes)) },
            )
        }
    }
}

@Composable
private fun ItemList(
    items: List<Item>,
    settings: dev.ishubhamsingh.shelfly.domain.model.Settings,
    onItemClick: (String) -> Unit,
) {
    val sorted = items.sortedWith(
        compareBy(
            { it.statusFor(settings.defaultLeadTimeDays).ordinal },
            { it.daysUntilExpiry },
        )
    )
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sorted, key = { it.id }) { item ->
            ItemCard(
                item      = item,
                leadTime  = settings.defaultLeadTimeDays,
                onClick   = { onItemClick(item.id) },
            )
        }
    }
}

@Composable
private fun ItemCard(
    item: Item,
    leadTime: Int,
    onClick: () -> Unit,
) {
    val status = item.statusFor(leadTime)
    val badgeLabel = when (status) {
        ItemStatus.GOOD          -> "${item.daysUntilExpiry}d left"
        ItemStatus.EXPIRING_SOON -> "${item.daysUntilExpiry}d left"
        ItemStatus.EXPIRED       -> "Expired ${-item.daysUntilExpiry}d ago"
        ItemStatus.CONSUMED      -> "Consumed"
    }

    Surface(
        shape    = MaterialTheme.shapes.medium,
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column {
            Row(
                modifier             = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CategoryAvatar(category = item.category, size = 44.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text     = item.name,
                        style    = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.formattedQty()?.let { qty ->
                        Text(
                            text  = qty,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                StatusBadge(status = status, label = badgeLabel)
            }
            // Thin shelf-life progress bar — a quiet visual cue
            val progressFraction = when {
                status == ItemStatus.CONSUMED || status == ItemStatus.EXPIRED -> 0f
                else -> (item.daysUntilExpiry.toFloat() / (item.daysUntilExpiry + leadTime).toFloat()).coerceIn(0f, 1f)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .padding(start = 72.dp, end = 14.dp)
                    .fillMaxWidth()
                    .height(3.dp),
            ) {
                LinearProgressIndicator(
                    progress         = { progressFraction },
                    modifier         = Modifier.fillMaxSize(),
                    trackColor       = MaterialTheme.colorScheme.surfaceContainerHighest,
                    color            = when (status) {
                        ItemStatus.GOOD          -> MaterialTheme.colorScheme.primary
                        ItemStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.tertiary
                        else                     -> MaterialTheme.colorScheme.error
                    },
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier             = modifier.padding(32.dp),
        horizontalAlignment  = Alignment.CenterHorizontally,
        verticalArrangement  = Arrangement.Center,
    ) {
        // Simple shelf illustration using Material icons as placeholders
        Icon(
            imageVector        = Icons.Filled.Notifications,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primaryContainer,
            modifier           = Modifier.size(80.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text  = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text  = "Tap + Add to start tracking the things on your shelves.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
