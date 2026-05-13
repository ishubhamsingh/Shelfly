package dev.ishubhamsingh.shelfly.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ishubhamsingh.shelfly.R
import dev.ishubhamsingh.shelfly.domain.model.Item
import dev.ishubhamsingh.shelfly.domain.model.ItemStatus
import dev.ishubhamsingh.shelfly.domain.model.Settings
import dev.ishubhamsingh.shelfly.domain.util.daysUntilExpiry
import dev.ishubhamsingh.shelfly.domain.util.formattedQty
import dev.ishubhamsingh.shelfly.domain.util.statusFor
import dev.ishubhamsingh.shelfly.ui.components.CategoryAvatar
import dev.ishubhamsingh.shelfly.ui.theme.LocalShelflyStatusColors
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: String,
    onNavigateUp: () -> Unit,
    onNavigateToEdit: () -> Unit,
    viewModel: ItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onNavigateUp()
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title            = { Text(stringResource(R.string.detail_delete_confirm_title)) },
            text             = { Text(stringResource(R.string.detail_delete_confirm_body)) },
            confirmButton    = {
                TextButton(
                    onClick = { viewModel.delete(); showDeleteDialog = false },
                    colors  = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.detail_delete_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.detail_delete_confirm_no))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.item?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(
                            expanded         = showOverflow,
                            onDismissRequest = { showOverflow = false },
                        ) {
                            DropdownMenuItem(
                                text    = { Text(stringResource(R.string.detail_action_delete)) },
                                onClick = { showDeleteDialog = true; showOverflow = false },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            val item = state.item
            if (item != null && !item.consumed) {
                Surface(
                    shadowElevation = 4.dp,
                    modifier        = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick   = viewModel::markConsumed,
                            modifier  = Modifier.weight(1.4f),
                            colors    = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor   = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.detail_action_consume))
                        }
                        OutlinedButton(
                            onClick  = onNavigateToEdit,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(stringResource(R.string.detail_action_edit))
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val item = state.item
        if (item == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            StatusHero(item = item, settings = state.settings)

            Spacer(modifier = Modifier.height(8.dp))

            DetailRow(
                icon    = { CategoryAvatar(category = item.category, size = 28.dp) },
                label   = stringResource(R.string.detail_label_category),
                value   = item.category.name.lowercase().replaceFirstChar { it.uppercase() },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))

            item.formattedQty()?.let { qty ->
                DetailRow(
                    icon    = { Icon(Icons.Filled.Scale, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    label   = stringResource(R.string.detail_label_quantity),
                    value   = qty,
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }

            val addedStr = item.createdAt
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            DetailRow(
                icon    = { Icon(Icons.Filled.EventAvailable, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                label   = stringResource(R.string.detail_label_added),
                value   = addedStr,
            )

            item.notes?.let { notes ->
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                DetailRow(
                    icon  = { Icon(Icons.Filled.StickyNote2, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    label = stringResource(R.string.detail_label_notes),
                    value = notes,
                )
            }
        }
    }
}

@Composable
private fun StatusHero(item: Item, settings: Settings) {
    val status    = item.statusFor(settings.defaultLeadTimeDays)
    val statusColors = LocalShelflyStatusColors.current
    val (bg, fg)  = when (status) {
        ItemStatus.GOOD          -> statusColors.goodContainer     to statusColors.onGoodContainer
        ItemStatus.EXPIRING_SOON -> statusColors.soonContainer     to statusColors.onSoonContainer
        ItemStatus.EXPIRED       -> statusColors.expiredContainer  to statusColors.onExpiredContainer
        ItemStatus.CONSUMED      -> statusColors.consumedContainer to statusColors.onConsumedContainer
    }
    val statusLabel = when (status) {
        ItemStatus.GOOD          -> stringResource(R.string.status_looking_good)
        ItemStatus.EXPIRING_SOON -> stringResource(R.string.status_expiring_soon)
        ItemStatus.EXPIRED       -> stringResource(R.string.status_already_expired)
        ItemStatus.CONSUMED      -> stringResource(R.string.status_consumed)
    }
    val daysText = when {
        item.consumed            -> stringResource(R.string.status_consumed)
        item.daysUntilExpiry < 0 -> "Expired ${-item.daysUntilExpiry} days ago"
        else                     -> "${item.daysUntilExpiry} days left"
    }

    Surface(
        color    = bg,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape    = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Schedule,
                    contentDescription = null,
                    tint               = fg,
                    modifier           = Modifier.size(20.dp),
                )
                Text(
                    text  = statusLabel.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text  = daysText,
                style = MaterialTheme.typography.displaySmall,
                color = fg,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text  = "Expires ${item.expiryDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))}",
                style = MaterialTheme.typography.bodyMedium,
                color = fg.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun DetailRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
) {
    Row(
        modifier             = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment    = Alignment.Top,
    ) {
        Surface(
            shape = MaterialTheme.shapes.full,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
        }
        Column {
            Text(
                text  = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text  = value,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private val androidx.compose.material3.Shapes.full
    get() = androidx.compose.foundation.shape.CircleShape
