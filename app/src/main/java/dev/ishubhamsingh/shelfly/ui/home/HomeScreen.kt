package dev.ishubhamsingh.shelfly.ui.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
                    style = MaterialTheme.typography.headlineSmall.copy(
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
            val isSelected = filter == selected
            FilterChip(
                selected    = isSelected,
                onClick     = { onSelect(filter) },
                label       = { Text(stringResource(labelRes)) },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector        = Icons.Filled.Check,
                            contentDescription = null,
                            modifier           = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
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
    val days = item.daysUntilExpiry
    val badgeLabel = when (status) {
        ItemStatus.GOOD, ItemStatus.EXPIRING_SOON -> when {
            days < 7  -> "${days}d left"
            days < 60 -> "${days / 7}wk left"
            else      -> "${days / 30}mo left"
        }
        ItemStatus.EXPIRED -> {
            val ago = -days
            when {
                ago < 7  -> "${ago}d ago"
                ago < 60 -> "${ago / 7}wk ago"
                else     -> "${ago / 30}mo ago"
            }
        }
        ItemStatus.CONSUMED -> "Consumed"
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
            // Shelf-life bar: full when added, empty at expiry (lifespan-relative)
            val totalDays = ChronoUnit.DAYS.between(
                item.createdAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                item.expiryDate,
            ).toFloat().coerceAtLeast(1f)
            val progressFraction = when {
                status == ItemStatus.CONSUMED || status == ItemStatus.EXPIRED -> 0f
                else -> (item.daysUntilExpiry.toFloat() / totalDays).coerceIn(0f, 1f)
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
        ShelfIllustration(modifier = Modifier.size(160.dp))
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text  = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text  = buildAnnotatedString {
                append("Tap ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("+ Add") }
                append(" to start tracking the things on your shelves.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShelfIllustration(modifier: Modifier = Modifier) {
    val primary          = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimary        = MaterialTheme.colorScheme.onPrimary
    val surfaceHigh      = MaterialTheme.colorScheme.surfaceContainerHigh

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // ── Shelf boards ──────────────────────────────────────────────────────
        drawRoundRect(
            color        = surfaceHigh,
            topLeft      = Offset(w * 0.08f, h * 0.61f),
            size         = Size(w * 0.84f, h * 0.07f),
            cornerRadius = CornerRadius(12f),
        )
        drawRoundRect(
            color        = surfaceHigh,
            topLeft      = Offset(w * 0.18f, h * 0.33f),
            size         = Size(w * 0.60f, h * 0.07f),
            cornerRadius = CornerRadius(12f),
        )

        // ── Items on bottom shelf ─────────────────────────────────────────────
        // Tall bottle (primary)
        drawRoundRect(
            color        = primary,
            topLeft      = Offset(w * 0.17f, h * 0.40f),
            size         = Size(w * 0.13f, h * 0.21f),
            cornerRadius = CornerRadius(10f),
        )
        // Short jar (container)
        drawRoundRect(
            color        = primaryContainer,
            topLeft      = Offset(w * 0.36f, h * 0.46f),
            size         = Size(w * 0.13f, h * 0.15f),
            cornerRadius = CornerRadius(8f),
        )
        // Slim bottle (muted)
        drawRoundRect(
            color        = primary.copy(alpha = 0.4f),
            topLeft      = Offset(w * 0.56f, h * 0.44f),
            size         = Size(w * 0.10f, h * 0.17f),
            cornerRadius = CornerRadius(10f),
        )

        // ── Items on top shelf ────────────────────────────────────────────────
        drawRoundRect(
            color        = primaryContainer,
            topLeft      = Offset(w * 0.27f, h * 0.16f),
            size         = Size(w * 0.12f, h * 0.17f),
            cornerRadius = CornerRadius(8f),
        )
        drawRoundRect(
            color        = primary.copy(alpha = 0.65f),
            topLeft      = Offset(w * 0.46f, h * 0.18f),
            size         = Size(w * 0.10f, h * 0.15f),
            cornerRadius = CornerRadius(8f),
        )

        // ── Green checkmark circle ────────────────────────────────────────────
        val cx = w * 0.74f
        val cy = h * 0.73f
        val r  = w * 0.13f
        drawCircle(color = primary, radius = r, center = Offset(cx, cy))
        val checkPath = Path().apply {
            moveTo(cx - r * 0.44f, cy + r * 0.02f)
            lineTo(cx - r * 0.06f, cy + r * 0.40f)
            lineTo(cx + r * 0.50f, cy - r * 0.28f)
        }
        drawPath(
            path  = checkPath,
            color = onPrimary,
            style = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
