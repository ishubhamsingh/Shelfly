package dev.ishubhamsingh.shelfly.ui.settings

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ishubhamsingh.shelfly.R
import dev.ishubhamsingh.shelfly.domain.model.Category
import dev.ishubhamsingh.shelfly.ui.components.CategoryChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings        by viewModel.settings.collectAsStateWithLifecycle()
    val workerScheduled by viewModel.workerScheduled.collectAsStateWithLifecycle()
    val context          = LocalContext.current

    // Re-check permission on every resume (user may have changed it in system settings)
    var notificationsEnabled by remember {
        mutableStateOf(
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        )
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = context
            .getSystemService(NotificationManager::class.java)
            .areNotificationsEnabled()
        viewModel.checkWorkerStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ── Notifications card ─────────────────────────────────────────────
            SettingsCard {
                SectionHeader(
                    icon  = Icons.Filled.Notifications,
                    label = stringResource(R.string.settings_notifications_header),
                )

                // Status rows: permission + worker
                StatusRow(
                    ok         = notificationsEnabled,
                    okLabel    = stringResource(R.string.settings_notif_status_on),
                    nokLabel   = stringResource(R.string.settings_notif_status_off),
                    nokSub     = stringResource(R.string.settings_notif_status_off_sub),
                    actionLabel = if (notificationsEnabled)
                        stringResource(R.string.settings_notif_send_test)
                    else
                        stringResource(R.string.settings_notif_open_settings),
                    onAction   = if (notificationsEnabled) {
                        { viewModel.sendTestNotification() }
                    } else {
                        {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        }
                    },
                )

                workerScheduled?.let { scheduled ->
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                    StatusRow(
                        ok          = scheduled,
                        okLabel     = stringResource(R.string.settings_worker_on),
                        nokLabel    = stringResource(R.string.settings_worker_off),
                        nokSub      = stringResource(R.string.settings_worker_off_sub),
                        actionLabel = if (!scheduled) stringResource(R.string.settings_worker_fix) else null,
                        onAction    = { viewModel.rescheduleWorker() },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                // Live preview card
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text  = stringResource(R.string.settings_notif_preview_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    NotificationPreviewCard(
                        showCount = settings.notifShowCount,
                        showDays  = settings.notifShowDays,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                // Format toggles
                SwitchRow(
                    title           = stringResource(R.string.settings_notif_show_count_title),
                    subtitle        = stringResource(R.string.settings_notif_show_count_body),
                    checked         = settings.notifShowCount,
                    onCheckedChange = viewModel::setNotifShowCount,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                SwitchRow(
                    title           = stringResource(R.string.settings_notif_show_days_title),
                    subtitle        = stringResource(R.string.settings_notif_show_days_body),
                    checked         = settings.notifShowDays,
                    onCheckedChange = viewModel::setNotifShowDays,
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

                // Lead time slider
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text     = stringResource(R.string.settings_lead_time_title),
                    style    = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Text(
                    text     = stringResource(R.string.settings_lead_time_body),
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Slider(
                    value         = settings.defaultLeadTimeDays.toFloat(),
                    onValueChange = { viewModel.setLeadTimeDays(it.toInt()) },
                    valueRange    = 1f..14f,
                    steps         = 12,
                    modifier      = Modifier.padding(horizontal = 20.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = stringResource(R.string.settings_lead_time_min),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text  = stringResource(R.string.settings_lead_time_label, settings.defaultLeadTimeDays),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text  = stringResource(R.string.settings_lead_time_max),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

                // Quiet hours
                SwitchRow(
                    title           = stringResource(R.string.settings_quiet_hours_title),
                    subtitle        = stringResource(R.string.settings_quiet_hours_body),
                    checked         = settings.quietHours,
                    onCheckedChange = viewModel::setQuietHours,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Appearance card ────────────────────────────────────────────────
            SettingsCard {
                SectionHeader(
                    icon  = Icons.Filled.Palette,
                    label = stringResource(R.string.settings_theme_header),
                )
                SwitchRow(
                    title           = stringResource(R.string.settings_dynamic_color_title),
                    subtitle        = stringResource(R.string.settings_dynamic_color_body),
                    checked         = settings.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── About card ─────────────────────────────────────────────────────
            SettingsCard {
                SectionHeader(
                    icon  = Icons.Filled.Info,
                    label = stringResource(R.string.settings_about_header),
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                SettingsRow(
                    icon     = Icons.Filled.Tag,
                    title    = stringResource(R.string.settings_version_title),
                    subtitle = "1.0 (1)",
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon     = Icons.Filled.Code,
                    title    = stringResource(R.string.settings_source_title),
                    subtitle = stringResource(R.string.settings_source_subtitle),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon     = Icons.Filled.Favorite,
                    title    = stringResource(R.string.settings_feedback_title),
                    subtitle = stringResource(R.string.settings_feedback_subtitle),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Mock notification preview card ────────────────────────────────────────────

@Composable
private fun NotificationPreviewCard(
    showCount: Boolean,
    showDays: Boolean,
) {
    val item1    = stringResource(R.string.notif_preview_item1)
    val item2    = stringResource(R.string.notif_preview_item2)
    val tomorrow = stringResource(R.string.notif_body_tomorrow)
    val inDays   = stringResource(R.string.notif_body_in_days, 5)

    val title = if (showCount)
        stringResource(R.string.notif_title_plural, 2)
    else
        stringResource(R.string.notif_title_no_count)

    val body = if (showDays)
        "$item1 ($tomorrow), $item2 ($inDays). Tap to review."
    else
        "$item1, $item2. Tap to review."

    Surface(
        shape    = MaterialTheme.shapes.large,
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {

            // App identifier row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth(),
            ) {
                // App icon — small rounded square in primary color
                Box(
                    modifier         = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimary,
                        modifier           = Modifier.size(10.dp),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text  = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text  = " · now",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Title + body
            Text(
                text  = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text  = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Item category chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryChip(category = Category.FOOD, label = item1)
                CategoryChip(category = Category.FOOD, label = item2)
            }
        }
    }
}

// ── Status row (permission / worker) ──────────────────────────────────────────

@Composable
private fun StatusRow(
    ok: Boolean,
    okLabel: String,
    nokLabel: String,
    nokSub: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint               = if (ok) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.error,
            modifier           = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = if (ok) okLabel else nokLabel,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!ok) {
                Text(
                    text  = nokSub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ── Shared composables ─────────────────────────────────────────────────────────

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        shape    = MaterialTheme.shapes.medium,
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, label: String) {
    Row(
        modifier          = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text  = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text  = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
