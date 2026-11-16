package dev.ishubhamsingh.shelfly.ui.settings

import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ishubhamsingh.shelfly.R
import dev.ishubhamsingh.shelfly.agent.AiCoreState
import dev.ishubhamsingh.shelfly.agent.ModelState
import dev.ishubhamsingh.shelfly.domain.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings        by viewModel.settings.collectAsStateWithLifecycle()
    val workerScheduled by viewModel.workerScheduled.collectAsStateWithLifecycle()
    val modelState      by viewModel.modelState.collectAsStateWithLifecycle()
    val aiCoreState     by viewModel.aiCoreState.collectAsStateWithLifecycle()
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
                    NotificationPreviewCard()
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))

                // Group toggle
                SwitchRow(
                    title           = stringResource(R.string.settings_notif_grouped_title),
                    subtitle        = stringResource(R.string.settings_notif_grouped_body),
                    checked         = settings.notifGrouped,
                    onCheckedChange = viewModel::setNotifGrouped,
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

            // ── AI Scanning card ───────────────────────────────────────────────
            AiScanningCard(
                state          = modelState,
                aiCoreState    = aiCoreState,
                onEnableAiCore = { viewModel.enableAiCore() },
                onDownload     = { viewModel.downloadModel(dev.ishubhamsingh.shelfly.agent.GemmaModelManager.MODEL_DOWNLOAD_URL) },
                onPause        = { viewModel.pauseModelDownload() },
                onResume       = { viewModel.resumeModelDownload() },
                onCancel       = { viewModel.cancelModelDownload() },
                onDelete       = { viewModel.deleteModel() },
                onTryAssistant = onNavigateUp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── About card ─────────────────────────────────────────────────────
            val uriHandler = LocalUriHandler.current
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
                    onClick  = { uriHandler.openUri("https://github.com/ishubhamsingh/Shelfly") },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SettingsRow(
                    icon     = Icons.Filled.Favorite,
                    title    = stringResource(R.string.settings_feedback_title),
                    subtitle = stringResource(R.string.settings_feedback_subtitle),
                    onClick  = { uriHandler.openUri("https://github.com/ishubhamsingh/Shelfly/issues/new") },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Mock notification preview card ────────────────────────────────────────────

@Composable
private fun NotificationPreviewCard() {
    val item1    = stringResource(R.string.notif_preview_item1)
    val item2    = stringResource(R.string.notif_preview_item2)
    val tomorrow = stringResource(R.string.notif_body_tomorrow)
    val inDays   = stringResource(R.string.notif_body_in_days, 5)

    val title = stringResource(R.string.notif_title_plural, 2)
    val body  = "$item1 ($tomorrow), $item2 ($inDays). Tap to review."

    Surface(
        shape           = MaterialTheme.shapes.large,
        color           = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier        = Modifier.fillMaxWidth(),
        shadowElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {

            // App identifier row — matches actual notification header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth(),
            ) {
                // Replicate the launcher icon badge (adaptive icons can't use painterResource)
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFC0F0A1)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter            = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint               = Color.Unspecified,
                        modifier           = Modifier.size(16.dp),
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

            // Item chips — use the same hardcoded amber/category colors as the real notification
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NotifPreviewChip(category = Category.FOOD, label = item1)
                NotifPreviewChip(category = Category.FOOD, label = item2)
            }
        }
    }
}

/**
 * Chip that uses the same hardcoded color resources as the actual RemoteViews
 * notification chips, so preview and real notification always match.
 */
@Composable
private fun NotifPreviewChip(category: Category, label: String) {
    val bgColor = colorResource(
        when (category) {
            Category.FOOD     -> R.color.chip_food_bg
            Category.MEDICINE -> R.color.chip_medicine_bg
            Category.COSMETIC -> R.color.chip_cosmetic_bg
            Category.OTHER    -> R.color.chip_other_bg
        }
    )
    val fgColor = colorResource(
        when (category) {
            Category.FOOD     -> R.color.chip_food_fg
            Category.MEDICINE -> R.color.chip_medicine_fg
            Category.COSMETIC -> R.color.chip_cosmetic_fg
            Category.OTHER    -> R.color.chip_other_fg
        }
    )
    val icon = when (category) {
        Category.FOOD     -> Icons.Filled.Restaurant
        Category.MEDICINE -> Icons.Filled.Medication
        Category.COSMETIC -> Icons.Filled.Spa
        Category.OTHER    -> Icons.Filled.Inventory2
    }
    Row(
        modifier          = Modifier
            .background(color = bgColor, shape = RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = fgColor,
            modifier           = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text  = label,
            color = fgColor,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        )
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
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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

// ── AI Assistant card ──────────────────────────────────────────────────────────

private fun isOnWifi(context: android.content.Context): Boolean {
    val cm   = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    return caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
}

@Composable
private fun AiScanningCard(
    state          : ModelState,
    aiCoreState    : AiCoreState,
    onEnableAiCore : () -> Unit,
    onDownload     : () -> Unit,
    onPause        : () -> Unit,
    onResume       : () -> Unit,
    onCancel       : () -> Unit,
    onDelete       : () -> Unit,
    onTryAssistant : () -> Unit,
) {
    val context = LocalContext.current
    // Two-dialog download flow: Wi-Fi → confirm dialog; mobile → data-warning dialog
    var showWifiConfirmDialog  by remember { mutableStateOf(false) }
    var showMobileDataDialog   by remember { mutableStateOf(false) }
    var showDeleteDialog       by remember { mutableStateOf(false) }

    fun handleDownloadTap() {
        if (isOnWifi(context)) showWifiConfirmDialog = true
        else                   showMobileDataDialog  = true
    }

    if (showWifiConfirmDialog) {
        WifiDownloadConfirmDialog(
            onConfirm = { showWifiConfirmDialog = false; onDownload() },
            onDismiss = { showWifiConfirmDialog = false },
        )
    }
    if (showMobileDataDialog) {
        MobileDataWarningDialog(
            onWaitForWifi = { showMobileDataDialog = false },
            onContinue    = { showMobileDataDialog = false; onDownload() },
        )
    }
    if (showDeleteDialog) {
        DeleteConfirmDialog(
            onConfirm = { showDeleteDialog = false; onDelete() },
            onDismiss = { showDeleteDialog = false },
        )
    }

    SettingsCard {
        // ── Header: sparkle + "AI Assistant" + "Experimental" chip ───────
        Row(
            modifier          = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(20.dp),
            )
            Text(
                text  = stringResource(R.string.settings_ai_header),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text     = stringResource(R.string.settings_ai_experimental_badge),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

        // Nano takes precedence over the downloaded model: it costs no app-side bytes,
        // the system keeps it updated, and it measured at least as accurate. The Gemma
        // download is only surfaced when Nano cannot serve this device.
        when {
            aiCoreState is AiCoreState.Ready -> AiCoreReadySection(
                onTryAssistant = onTryAssistant,
            )

            aiCoreState is AiCoreState.Downloading -> AiCoreEnablingSection()

            aiCoreState is AiCoreState.Downloadable && state is ModelState.NotDownloaded ->
                AiCoreOfferSection(
                    onEnable      = onEnableAiCore,
                    onDownloadAlt = { handleDownloadTap() },
                )

            else -> when (state) {
            is ModelState.NotDownloaded -> AiIdleSection(
                onDownload = { handleDownloadTap() },
            )
            is ModelState.Downloading -> AiDownloadingSection(
                state    = state,
                onPause  = onPause,
                onCancel = onCancel,
                context  = context,
            )
            is ModelState.Paused -> AiPausedSection(
                state    = state,
                onResume = onResume,
                onCancel = onCancel,
            )
            is ModelState.Ready -> AiReadySection(
                state          = state,
                onTryAssistant = onTryAssistant,
                onDelete       = { showDeleteDialog = true },
            )
            is ModelState.Error -> AiErrorSection(
                message    = state.message,
                onDownload = { handleDownloadTap() },
            )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ── Gemini Nano (AICore) sections ─────────────────────────────────────────────

@Composable
private fun AiCoreReadySection(onTryAssistant: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text  = stringResource(R.string.settings_aicore_ready_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = stringResource(R.string.settings_aicore_ready_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        FilledTonalButton(onClick = onTryAssistant) {
            Text(stringResource(R.string.settings_ai_try_assistant))
        }
    }
}

@Composable
private fun AiCoreEnablingSection() {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text  = stringResource(R.string.settings_aicore_enabling_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Indeterminate on purpose: AICore hands the download to Android System
        // Intelligence out-of-process and reports no byte progress, so a percentage
        // bar here would be fabricated.
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text  = stringResource(R.string.settings_aicore_enabling_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AiCoreOfferSection(
    onEnable      : () -> Unit,
    onDownloadAlt : () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text  = stringResource(R.string.settings_aicore_offer_title),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text  = stringResource(R.string.settings_aicore_offer_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        FilledTonalButton(onClick = onEnable) {
            Text(stringResource(R.string.settings_aicore_enable_cta))
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = onDownloadAlt) {
            Text(stringResource(R.string.settings_aicore_download_instead))
        }
    }
}

// ── NotDownloaded ─────────────────────────────────────────────────────────────

@Composable
private fun AiIdleSection(onDownload: () -> Unit) {
    // Description
    Text(
        text     = stringResource(R.string.settings_ai_idle_desc),
        style    = MaterialTheme.typography.bodySmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )

    // Privacy callout
    Surface(
        color    = MaterialTheme.colorScheme.primaryContainer,
        shape    = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.Lock,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier           = Modifier.size(18.dp),
            )
            Text(
                text  = stringResource(R.string.settings_ai_privacy_callout),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Model size row
    AiStorageRow(
        label = stringResource(R.string.settings_ai_model_size_label),
        value = stringResource(R.string.settings_ai_model_size),
        hint  = stringResource(R.string.settings_ai_wifi_hint),
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

    // Full-width Download button
    Button(
        onClick   = onDownload,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        shape     = MaterialTheme.shapes.extraLarge,
    ) {
        Icon(
            imageVector        = Icons.Filled.Download,
            contentDescription = null,
            modifier           = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.settings_ai_download_cta))
    }
}

// ── Downloading ───────────────────────────────────────────────────────────────

@Composable
private fun AiDownloadingSection(
    state    : ModelState.Downloading,
    onPause  : () -> Unit,
    onCancel : () -> Unit,
    context  : android.content.Context,
) {
    val pct          = (state.progress * 100).toInt()
    val downloadedGb = state.downloadedBytes / 1_000_000_000.0
    val totalGb      = if (state.totalBytes > 0) state.totalBytes / 1_000_000_000.0 else 2.60
    val onWifi       = remember { isOnWifi(context) }

    // "47%  downloaded" — two-part: large number + smaller suffix
    Row(
        modifier          = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text  = stringResource(R.string.settings_ai_downloading_pct, pct),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text     = stringResource(R.string.settings_ai_downloading_suffix),
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }

    // Thick progress bar
    LinearProgressIndicator(
        progress    = { state.progress },
        modifier    = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .padding(horizontal = 20.dp)
            .clip(MaterialTheme.shapes.extraSmall),
        trackColor  = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    // GB progress + network status row
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text  = stringResource(
                R.string.settings_ai_gb_progress,
                "%.2f GB".format(downloadedGb),
                "%.2f GB".format(totalGb),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector        = if (onWifi) Icons.Filled.Wifi
                                     else        Icons.Filled.SignalCellularAlt,
                contentDescription = null,
                tint               = if (onWifi) MaterialTheme.colorScheme.primary
                                     else        MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(14.dp),
            )
            Text(
                text  = stringResource(
                    if (onWifi) R.string.settings_ai_downloading_wifi
                    else        R.string.settings_ai_downloading_cellular,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (onWifi) MaterialTheme.colorScheme.primary
                        else        MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

    // Pause + Cancel — both OutlinedButtons with icons
    Row(
        modifier              = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedButton(
            onClick  = onPause,
            modifier = Modifier.weight(1f),
            shape    = MaterialTheme.shapes.extraLarge,
        ) {
            Icon(
                imageVector        = Icons.Filled.Pause,
                contentDescription = null,
                modifier           = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_ai_action_pause))
        }
        OutlinedButton(
            onClick  = onCancel,
            modifier = Modifier.weight(1f),
            shape    = MaterialTheme.shapes.extraLarge,
            colors   = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.38f)),
        ) {
            Icon(
                imageVector        = Icons.Filled.Close,
                contentDescription = null,
                modifier           = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_ai_action_cancel))
        }
    }
}

// ── Paused ────────────────────────────────────────────────────────────────────

@Composable
private fun AiPausedSection(
    state    : ModelState.Paused,
    onResume : () -> Unit,
    onCancel : () -> Unit,
) {
    val downloadedGb = state.downloadedBytes / 1_000_000_000.0
    val totalGb      = if (state.totalBytes > 0) state.totalBytes / 1_000_000_000.0 else 2.60
    val progress     = if (state.totalBytes > 0)
        state.downloadedBytes.toFloat() / state.totalBytes else 0f
    val pct          = (progress * 100).toInt()

    // "Paused at 47%"
    Row(
        modifier          = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector        = Icons.Filled.Pause,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(18.dp),
        )
        Text(
            text  = stringResource(R.string.settings_ai_paused_at_pct, pct),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    // Partial progress bar
    LinearProgressIndicator(
        progress   = { progress },
        modifier   = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .padding(horizontal = 20.dp)
            .clip(MaterialTheme.shapes.extraSmall),
        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

    // GB progress + "Resume to continue" hint
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text  = "%.2f GB of %.2f GB".format(downloadedGb, totalGb),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text  = stringResource(R.string.settings_ai_paused_resume_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

    // Resume (primary Button) + Cancel (error OutlinedButton)
    Row(
        modifier              = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick  = onResume,
            modifier = Modifier.weight(1f),
            shape    = MaterialTheme.shapes.extraLarge,
        ) {
            Icon(
                imageVector        = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier           = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_ai_action_resume))
        }
        OutlinedButton(
            onClick  = onCancel,
            modifier = Modifier.weight(1f),
            shape    = MaterialTheme.shapes.extraLarge,
            colors   = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            border   = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.38f)),
        ) {
            Icon(
                imageVector        = Icons.Filled.Close,
                contentDescription = null,
                modifier           = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_ai_action_cancel))
        }
    }
}

// ── Ready ─────────────────────────────────────────────────────────────────────

@Composable
private fun AiReadySection(
    state          : ModelState.Ready,
    onTryAssistant : () -> Unit,
    onDelete       : () -> Unit,
) {
    val sizeGb = state.sizeBytes / 1_000_000_000.0

    // "Model ready · Gemma 3n 4B"
    Row(
        modifier          = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector        = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(18.dp),
        )
        Text(
            text  = stringResource(R.string.settings_ai_ready_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

    // Storage used row
    AiStorageRow(
        label = stringResource(R.string.settings_ai_storage_used_label),
        value = "%.2f GB".format(sizeGb),
    )

    HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

    // Action buttons
    Row(
        modifier              = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilledTonalButton(
            onClick   = onTryAssistant,
            modifier  = Modifier.weight(1.4f),
            shape     = MaterialTheme.shapes.extraLarge,
        ) {
            Icon(
                imageVector        = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier           = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(stringResource(R.string.settings_ai_try_assistant))
        }
        TextButton(
            onClick  = onDelete,
            modifier = Modifier.weight(1f),
            colors   = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(stringResource(R.string.settings_ai_action_delete))
        }
    }
}

// ── Error ─────────────────────────────────────────────────────────────────────

@Composable
private fun AiErrorSection(message: String, onDownload: () -> Unit) {
    Text(
        text     = stringResource(R.string.settings_ai_status_error, message),
        style    = MaterialTheme.typography.bodySmall,
        color    = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
    TextButton(
        onClick  = onDownload,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Text(stringResource(R.string.settings_ai_action_download))
    }
    Spacer(modifier = Modifier.height(4.dp))
}

// ── Shared row ────────────────────────────────────────────────────────────────

@Composable
private fun AiStorageRow(label: String, value: String, hint: String? = null) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector        = Icons.Filled.Memory,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurface)
            if (hint != null) {
                Text(text = hint, style = MaterialTheme.typography.labelSmall,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            text  = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

/** Shown when the user is on Wi-Fi — info-rich confirm dialog. */
@Composable
private fun WifiDownloadConfirmDialog(
    onConfirm : () -> Unit,
    onDismiss : () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape          = MaterialTheme.shapes.extraLarge,
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Large circular primary icon — centered, mirrors the mobile data dialog's amber icon
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(72.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Download,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier           = Modifier.size(36.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text  = stringResource(R.string.settings_ai_download_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text  = stringResource(R.string.settings_ai_download_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Info rows
                DialogInfoRow(
                    icon  = Icons.Filled.Download,
                    label = stringResource(R.string.settings_ai_download_size_label),
                    value = stringResource(R.string.settings_ai_model_size),
                )
                DialogInfoRow(
                    icon  = Icons.Filled.Memory,
                    label = stringResource(R.string.settings_ai_dialog_storage_label),
                    value = stringResource(R.string.settings_ai_dialog_storage_value),
                )
                DialogInfoRow(
                    icon  = Icons.Filled.Timer,
                    label = stringResource(R.string.settings_ai_dialog_time_label),
                    value = stringResource(R.string.settings_ai_dialog_time_value),
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Wi-Fi recommendation callout
                Surface(
                    color    = MaterialTheme.colorScheme.primaryContainer,
                    shape    = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Wifi,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier           = Modifier.size(16.dp),
                        )
                        Text(
                            text  = stringResource(R.string.settings_ai_download_dialog_wifi_callout),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_ai_download_dialog_not_now))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onConfirm) {
                        Icon(
                            imageVector        = Icons.Filled.Download,
                            contentDescription = null,
                            modifier           = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_ai_download_dialog_confirm))
                    }
                }
            }
        }
    }
}

/** Shown when the user is on mobile data — centered with large amber icon. */
@Composable
private fun MobileDataWarningDialog(
    onWaitForWifi : () -> Unit,
    onContinue    : () -> Unit,
) {
    Dialog(onDismissRequest = onWaitForWifi) {
        Surface(
            shape          = MaterialTheme.shapes.extraLarge,
            color          = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier            = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Large circular amber/tertiary icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.SignalCellularAlt,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier           = Modifier.size(36.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text      = stringResource(R.string.settings_ai_mobile_title),
                    style     = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text      = stringResource(R.string.settings_ai_mobile_body),
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Amber chip — icon + two-line text (bold title + subtitle)
                Surface(
                    color    = MaterialTheme.colorScheme.tertiaryContainer,
                    shape    = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.DataUsage,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier           = Modifier.size(20.dp),
                        )
                        Column {
                            Text(
                                text  = stringResource(R.string.settings_ai_mobile_chip_title),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                text  = stringResource(R.string.settings_ai_mobile_chip_sub),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary action — stacked full-width buttons
                Button(
                    onClick  = onWaitForWifi,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = MaterialTheme.shapes.extraLarge,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Wifi,
                        contentDescription = null,
                        modifier           = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_ai_mobile_wait_wifi))
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick  = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(stringResource(R.string.settings_ai_mobile_continue))
                }
            }
        }
    }
}

@Composable
private fun DialogInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(16.dp),
        )
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text  = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DeleteConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title  = { Text(stringResource(R.string.settings_ai_delete_confirm_title)) },
        text   = { Text(stringResource(R.string.settings_ai_delete_confirm_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors  = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.settings_ai_delete_confirm_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.form_cancel))
            }
        },
    )
}
