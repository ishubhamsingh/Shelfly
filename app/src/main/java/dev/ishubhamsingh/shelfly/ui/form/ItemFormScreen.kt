package dev.ishubhamsingh.shelfly.ui.form

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import dev.ishubhamsingh.shelfly.domain.model.Category
import dev.ishubhamsingh.shelfly.ui.components.CategoryAvatar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemFormScreen(
    itemId: String?,
    onNavigateUp: () -> Unit,
    viewModel: ItemFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateUp()
    }

    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.expiryDate
                ?.atStartOfDay(ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        viewModel.setExpiryDate(
                            Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        )
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isEditMode) stringResource(R.string.form_edit_title)
                        else stringResource(R.string.form_add_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.form_cancel))
                    }
                },
                actions = {
                    if (state.isEditMode) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.detail_action_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 4.dp,
                modifier        = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onNavigateUp) { Text(stringResource(R.string.form_cancel)) }
                    Button(
                        onClick  = viewModel::save,
                        enabled  = state.canSave,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            if (state.isEditMode) stringResource(R.string.form_save_cta)
                            else stringResource(R.string.form_add_cta)
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── Name ──────────────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.form_section_what))
            OutlinedTextField(
                value         = state.name,
                onValueChange = viewModel::setName,
                label         = { Text(stringResource(R.string.form_name_label)) },
                placeholder   = { Text(stringResource(R.string.form_name_hint)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )

            // ── Category tiles ────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.form_section_category))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Category.entries.forEach { cat ->
                    CategoryTile(
                        category = cat,
                        selected = state.category == cat,
                        onClick  = { viewModel.setCategory(cat) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Expiry date ────────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.form_section_expires))
            Surface(
                onClick  = { showDatePicker = true },
                shape    = MaterialTheme.shapes.small,
                color    = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = MaterialTheme.shapes.small,
                    ),
            ) {
                Row(
                    modifier             = Modifier.padding(16.dp),
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Filled.CalendarMonth,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text  = state.expiryDate
                            ?.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy"))
                            ?: stringResource(R.string.form_date_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (state.expiryDate != null)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Quantity ───────────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.form_section_quantity))
            OutlinedTextField(
                value         = state.qty,
                onValueChange = viewModel::setQty,
                label         = { Text(stringResource(R.string.form_qty_label)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
            )
            val units = listOf("g", "kg", "ml", "L", "tablets", "count")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                units.forEach { u ->
                    FilterChip(
                        selected = state.unit == u,
                        onClick  = { viewModel.setUnit(if (state.unit == u) null else u) },
                        label    = { Text(u) },
                    )
                }
            }

            // ── Notes ──────────────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.form_section_notes))
            OutlinedTextField(
                value         = state.notes,
                onValueChange = viewModel::setNotes,
                placeholder   = { Text(stringResource(R.string.form_notes_hint)) },
                maxLines      = 4,
                minLines      = 3,
                modifier      = Modifier.fillMaxWidth(),
            )
            Text(
                text     = stringResource(R.string.form_notes_count, state.notes.length),
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CategoryTile(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val labelRes = when (category) {
        Category.FOOD     -> R.string.category_food
        Category.MEDICINE -> R.string.category_medicine
        Category.COSMETIC -> R.string.category_cosmetic
        Category.OTHER    -> R.string.category_other
    }
    Surface(
        onClick  = onClick,
        shape    = MaterialTheme.shapes.medium,
        color    = if (selected) MaterialTheme.colorScheme.secondaryContainer
                   else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.secondary,
                    shape = MaterialTheme.shapes.medium,
                ) else Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = MaterialTheme.shapes.medium,
                )
            ),
    ) {
        Column(
            modifier             = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.spacedBy(6.dp),
        ) {
            CategoryAvatar(category = category, size = 36.dp)
            Text(
                text  = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
