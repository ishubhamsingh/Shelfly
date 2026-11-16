package dev.ishubhamsingh.shelfly.ui.components

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ishubhamsingh.shelfly.R
import dev.ishubhamsingh.shelfly.agent.ModelState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOptionsSheet(
    onDismiss           : () -> Unit,
    onAddManually       : () -> Unit,
    modelState          : ModelState,
    /**
     * True when Gemini Nano can serve a turn. Independent of [modelState] — on a
     * Nano-capable device the assistant works with nothing downloaded at all, so the
     * card must not be gated on the model file alone.
     */
    aiCoreReady         : Boolean,
    hasAssistant        : Boolean,
    onAskAgent          : () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val context    = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val prompts    = stringArrayResource(R.array.assistant_prompts)
    var promptIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            promptIndex = (promptIndex + 1) % prompts.size
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        shape            = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text     = stringResource(R.string.sheet_how_to_add),
                style    = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))

            // ── AI Assistant card ─────────────────────────────────────────────
            // Nano wins outright: when it is ready the assistant is usable regardless
            // of download state, so show the ready card and skip the model branches.
            when {
                aiCoreReady -> AiCardReady(
                    prompts     = prompts,
                    promptIndex = promptIndex,
                    onClick     = { onDismiss(); onAskAgent() },
                )

                else -> when (modelState) {
                is ModelState.Ready -> AiCardReady(
                    prompts     = prompts,
                    promptIndex = promptIndex,
                    onClick     = { onDismiss(); onAskAgent() },
                )

                is ModelState.Downloading -> AiCardDownloading(
                    progress      = modelState.progress,
                    downloadedBytes = modelState.downloadedBytes,
                    totalBytes    = modelState.totalBytes,
                )

                is ModelState.Paused -> AiCardDownloading(
                    progress        = if (modelState.totalBytes > 0)
                        modelState.downloadedBytes.toFloat() / modelState.totalBytes else 0f,
                    downloadedBytes = modelState.downloadedBytes,
                    totalBytes      = modelState.totalBytes,
                    paused          = true,
                )

                is ModelState.NotDownloaded, is ModelState.Error -> AiCardNotDownloaded(
                    hasAssistant        = hasAssistant,
                    onAskAssistant      = {
                        onDismiss()
                        context.startActivity(
                            Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onNavigateToSettings = onNavigateToSettings,
                )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Add manually card ─────────────────────────────────────────────
            Surface(
                onClick  = { onDismiss(); onAddManually() },
                shape    = MaterialTheme.shapes.extraLarge,
                color    = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier          = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape    = CircleShape,
                        color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector        = Icons.Filled.Edit,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier           = Modifier.size(22.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text  = stringResource(R.string.sheet_add_manually),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text  = stringResource(R.string.sheet_add_manually_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector        = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Ready ─────────────────────────────────────────────────────────────────────

@Composable
private fun AiCardReady(
    prompts     : Array<String>,
    promptIndex : Int,
    onClick     : () -> Unit,
) {
    Surface(
        onClick  = onClick,
        shape    = MaterialTheme.shapes.extraLarge,
        color    = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint               = contentColor,
                    modifier           = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text     = stringResource(R.string.sheet_ask_shelfly),
                    style    = MaterialTheme.typography.titleMedium,
                    color    = contentColor,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector        = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint               = contentColor,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Rotating prompt area
            Surface(
                color    = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape    = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text  = stringResource(R.string.sheet_shelfly_subtitle).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AnimatedContent(
                        targetState    = promptIndex,
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn()) togetherWith
                                (slideOutVertically { -it } + fadeOut())
                        },
                        label = "prompt_rotation",
                    ) { index ->
                        Text(
                            text  = prompts[index],
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Pagination dots
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                prompts.indices.forEach { i ->
                    val isActive = i == promptIndex
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (isActive) 18.dp else 6.dp)
                            .background(
                                color = contentColor.copy(alpha = if (isActive) 0.9f else 0.3f),
                                shape = CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

// ── Downloading / Paused ──────────────────────────────────────────────────────

@Composable
private fun AiCardDownloading(
    progress        : Float,
    downloadedBytes : Long,
    totalBytes      : Long,
    paused          : Boolean = false,
) {
    val pct          = (progress * 100).toInt()
    val downloadedGb = downloadedBytes / 1_000_000_000.0
    val totalGb      = if (totalBytes > 0) totalBytes / 1_000_000_000.0 else 2.60

    Surface(
        shape    = MaterialTheme.shapes.extraLarge,
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text  = stringResource(R.string.sheet_ask_shelfly),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text  = if (paused)
                            stringResource(R.string.sheet_ai_paused, pct)
                        else
                            stringResource(R.string.sheet_ai_downloading, pct),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mini progress bar
            LinearProgressIndicator(
                progress   = { progress },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text  = "%.2f GB of %.2f GB".format(downloadedGb, totalGb),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Not Downloaded / Error ────────────────────────────────────────────────────

@Composable
private fun AiCardNotDownloaded(
    hasAssistant        : Boolean,
    onAskAssistant      : () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    Surface(
        onClick  = if (hasAssistant) onAskAssistant else onNavigateToSettings,
        shape    = MaterialTheme.shapes.extraLarge,
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(26.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text     = if (hasAssistant)
                        stringResource(R.string.sheet_ask_assistant)
                    else
                        stringResource(R.string.sheet_ask_shelfly),
                    style    = MaterialTheme.typography.titleMedium,
                    color    = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector        = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                color    = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape    = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text     = if (hasAssistant)
                            stringResource(R.string.sheet_no_assistant_hint)
                        else
                            stringResource(R.string.sheet_ai_download_hint),
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!hasAssistant) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector        = Icons.Filled.Download,
                                contentDescription = null,
                                modifier           = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text  = stringResource(R.string.sheet_ai_get),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
