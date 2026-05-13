package dev.ishubhamsingh.shelfly.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import dev.ishubhamsingh.shelfly.domain.model.Category
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
    var showAddSheet    by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    if (showAddSheet) {
        AddOptionsSheet(
            onDismiss      = { showAddSheet = false },
            onAskAssistant = { showAddSheet = false },
            onAddManually  = onNavigateToForm,
        )
    }

    if (showFilterSheet) {
        FilterSortSheet(
            currentSort     = state.sort,
            currentOrder    = state.sortOrder,
            currentCategory = state.categoryFilter,
            onApply         = { sort, order, cat ->
                viewModel.setSortAndFilter(sort, order, cat)
                showFilterSheet = false
            },
            onReset         = {
                viewModel.resetSortAndFilter()
                showFilterSheet = false
            },
            onDismiss       = { showFilterSheet = false },
        )
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                itemCount       = state.items.size,
                sort            = state.sort,
                categoryFilter  = state.categoryFilter,
                onFilterClick   = { showFilterSheet = true },
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
                selected = state.filter,
                onSelect = viewModel::setFilter,
            )
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (state.items.isEmpty()) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                ItemList(
                    items       = state.items,
                    settings    = state.settings,
                    onItemClick = onNavigateToDetail,
                )
            }
        }
    }
}

// ── Top bar ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(
    itemCount      : Int,
    sort           : HomeSort,
    categoryFilter : Category?,
    onFilterClick  : () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val sortShortLabel = when (sort) {
        HomeSort.EXPIRY_DATE -> stringResource(R.string.sort_short_expiry)
        HomeSort.CATEGORY    -> stringResource(R.string.sort_short_category)
        HomeSort.NAME        -> stringResource(R.string.sort_short_name)
        HomeSort.DATE_ADDED  -> stringResource(R.string.sort_short_date)
        HomeSort.QUANTITY    -> stringResource(R.string.sort_short_quantity)
    }
    val subtitle = buildString {
        append(itemCount)
        if (categoryFilter != null) {
            append(" · ${categoryFilter.name.lowercase()}")
        }
        append(" items · by $sortShortLabel")
    }

    TopAppBar(
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShelflyMark(color = MaterialTheme.colorScheme.primary, size = 28.dp)
                Column {
                    Text(
                        text  = stringResource(R.string.home_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (itemCount > 0) {
                        Text(
                            text  = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            IconButton(onClick = onFilterClick) {
                Icon(
                    imageVector        = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.cd_filter_sort),
                    tint = if (categoryFilter != null || sort != HomeSort.EXPIRY_DATE)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface,
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

// ── Status filter row ─────────────────────────────────────────────────────────

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
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.padding(bottom = 8.dp),
    ) {
        items(filters) { (filter, labelRes) ->
            val isSelected = filter == selected
            FilterChip(
                selected    = isSelected,
                onClick     = { onSelect(filter) },
                label       = { Text(stringResource(labelRes)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
            )
        }
    }
}

// ── Filter & Sort bottom sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSortSheet(
    currentSort    : HomeSort,
    currentOrder   : SortOrder,
    currentCategory: Category?,
    onApply        : (HomeSort, SortOrder, Category?) -> Unit,
    onReset        : () -> Unit,
    onDismiss      : () -> Unit,
) {
    var draftSort     by remember { mutableStateOf(currentSort) }
    var draftOrder    by remember { mutableStateOf(currentOrder) }
    var draftCategory by remember { mutableStateOf(currentCategory) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            // Title
            Text(
                text     = stringResource(R.string.filter_sort_title),
                style    = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            // ── Category ──────────────────────────────────────────────────────
            SheetSectionLabel(stringResource(R.string.filter_section_category))
            Spacer(Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding        = PaddingValues(end = 4.dp),
            ) {
                // All
                item {
                    FilterChip(
                        selected    = draftCategory == null,
                        onClick     = { draftCategory = null },
                        label       = { Text(stringResource(R.string.filter_all)) },
                        leadingIcon = if (draftCategory == null) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null,
                    )
                }
                // Category chips
                items(Category.entries) { cat ->
                    val labelRes = when (cat) {
                        Category.FOOD     -> R.string.category_food
                        Category.MEDICINE -> R.string.category_medicine
                        Category.COSMETIC -> R.string.category_cosmetic
                        Category.OTHER    -> R.string.category_other
                    }
                    FilterChip(
                        selected    = draftCategory == cat,
                        onClick     = { draftCategory = if (draftCategory == cat) null else cat },
                        label       = { Text(stringResource(labelRes)) },
                        leadingIcon = { CategoryAvatar(category = cat, size = 20.dp) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            // ── Sort by ───────────────────────────────────────────────────────
            SheetSectionLabel(stringResource(R.string.filter_section_sort_by))
            Spacer(Modifier.height(4.dp))

            val sortOptions = listOf(
                HomeSort.EXPIRY_DATE to (R.string.sort_expiry_date to R.string.sort_expiry_date_sub),
                HomeSort.CATEGORY    to (R.string.sort_category    to R.string.sort_category_sub),
                HomeSort.NAME        to (R.string.sort_name        to R.string.sort_name_sub),
                HomeSort.DATE_ADDED  to (R.string.sort_date_added  to R.string.sort_date_added_sub),
                HomeSort.QUANTITY    to (R.string.sort_quantity     to R.string.sort_quantity_sub),
            )
            sortOptions.forEach { (sort, labels) ->
                SortRadioRow(
                    title    = stringResource(labels.first),
                    subtitle = stringResource(labels.second),
                    selected = draftSort == sort,
                    onClick  = { draftSort = sort },
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            // ── Order ─────────────────────────────────────────────────────────
            SheetSectionLabel(stringResource(R.string.filter_section_order))
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = draftOrder == SortOrder.ASCENDING,
                    onClick  = { draftOrder = SortOrder.ASCENDING },
                    shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.filter_order_ascending)) }
                SegmentedButton(
                    selected = draftOrder == SortOrder.DESCENDING,
                    onClick  = { draftOrder = SortOrder.DESCENDING },
                    shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.filter_order_descending)) }
            }

            Spacer(Modifier.height(24.dp))

            // ── Actions ───────────────────────────────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    onClick  = onReset,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.filter_reset)) }
                Button(
                    onClick  = { onApply(draftSort, draftOrder, draftCategory) },
                    modifier = Modifier.weight(2f),
                ) { Text(stringResource(R.string.filter_apply)) }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text  = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SortRadioRow(
    title   : String,
    subtitle: String,
    selected: Boolean,
    onClick : () -> Unit,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.size(8.dp))
        Column {
            Text(text = title,    style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Item list ─────────────────────────────────────────────────────────────────

@Composable
private fun ItemList(
    items      : List<Item>,
    settings   : dev.ishubhamsingh.shelfly.domain.model.Settings,
    onItemClick: (String) -> Unit,
) {
    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            ItemCard(
                item     = item,
                leadTime = settings.defaultLeadTimeDays,
                onClick  = { onItemClick(item.id) },
            )
        }
    }
}

@Composable
private fun ItemCard(
    item    : Item,
    leadTime: Int,
    onClick : () -> Unit,
) {
    val status = item.statusFor(leadTime)
    val days   = item.daysUntilExpiry
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
                modifier              = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
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

            // Danger meter — fills as expiry approaches
            val progressFraction = when {
                status == ItemStatus.CONSUMED -> 0f
                status == ItemStatus.EXPIRED  -> 1f
                else -> {
                    val d = days.toFloat().coerceAtLeast(0f)
                    val safe = when {
                        d <= 7f  -> (d / 7f) * 0.25f
                        d <= 30f -> 0.25f + ((d - 7f) / 23f) * 0.25f
                        d <= 90f -> 0.50f + ((d - 30f) / 60f) * 0.25f
                        else     -> 0.75f + (d.coerceAtMost(365f) / 365f) * 0.25f
                    }
                    1f - safe
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .padding(start = 72.dp, end = 14.dp)
                    .fillMaxWidth()
                    .height(3.dp),
            ) {
                LinearProgressIndicator(
                    progress          = { progressFraction },
                    modifier          = Modifier.fillMaxSize(),
                    trackColor        = MaterialTheme.colorScheme.surfaceContainerHighest,
                    color             = when (status) {
                        ItemStatus.GOOD          -> MaterialTheme.colorScheme.primary
                        ItemStatus.EXPIRING_SOON -> MaterialTheme.colorScheme.tertiary
                        else                     -> MaterialTheme.colorScheme.error
                    },
                    drawStopIndicator = {},
                    gapSize           = 0.dp,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))
        ShelfIllustration(modifier = Modifier.size(width = 176.dp, height = 144.dp))
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
        Spacer(modifier = Modifier.weight(2f))
    }
}

// ── Shelf illustration (light + dark SVG) ─────────────────────────────────────

@Composable
private fun ShelfIllustration(modifier: Modifier = Modifier) {
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
