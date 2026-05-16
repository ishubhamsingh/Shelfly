package dev.ishubhamsingh.shelfly.ui.agent

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ishubhamsingh.shelfly.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSheet(
    onDismiss: () -> Unit,
    viewModel: AgentViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Pending image: thumbnail for display, bytes for the model
    var pendingThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var pendingImageBytes by remember { mutableStateOf<ByteArray?>(null) }

    fun clearImage() { pendingThumbnail = null; pendingImageBytes = null }
    fun sendAndClear() {
        viewModel.run(input, pendingImageBytes)
        input = ""
        clearImage()
    }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            // Pass 1: read bounds only to calculate sample size
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val sample = maxOf(boundsOpts.outWidth, boundsOpts.outHeight) / 1024

            // Pass 2: decode with subsampling (keeps memory ~1 MP)
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                val opts = BitmapFactory.Options().apply { inSampleSize = maxOf(1, sample) }
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@launch

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            withContext(Dispatchers.Main) {
                pendingThumbnail  = bitmap
                pendingImageBytes = out.toByteArray()
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) input = text
        }
    }

    fun launchSpeech() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.agent_speech_prompt))
        }
        try { speechLauncher.launch(intent) } catch (_: ActivityNotFoundException) { /* no STT engine */ }
    }

    LaunchedEffect(Unit) { viewModel.reset() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        shape            = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text  = stringResource(R.string.agent_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(Modifier.height(16.dp))

            when (val s = uiState) {
                AgentUiState.Idle -> {
                    Text(
                        text  = stringResource(R.string.agent_sheet_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AgentUiState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text  = stringResource(R.string.agent_sheet_thinking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is AgentUiState.Result -> {
                    Surface(
                        color    = MaterialTheme.colorScheme.primaryContainer,
                        shape    = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text     = s.reply,
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
                is AgentUiState.Error -> {
                    Surface(
                        color    = MaterialTheme.colorScheme.errorContainer,
                        shape    = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text     = stringResource(R.string.agent_sheet_error, s.reason),
                            style    = MaterialTheme.typography.bodyMedium,
                            color    = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val isLoading = uiState is AgentUiState.Loading
            val canSend   = !isLoading && (input.isNotBlank() || pendingImageBytes != null)

            // Thumbnail strip — visible only when an image is attached
            if (pendingThumbnail != null) {
                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    Image(
                        bitmap             = pendingThumbnail!!.asImageBitmap(),
                        contentDescription = stringResource(R.string.agent_image_preview),
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(72.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                    // Clear button
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = CircleShape,
                            ),
                    ) {
                        IconButton(
                            onClick  = { clearImage() },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.agent_image_clear),
                                modifier           = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value         = input,
                    onValueChange = { input = it },
                    placeholder   = { Text(stringResource(R.string.agent_sheet_placeholder)) },
                    enabled       = !isLoading,
                    singleLine    = true,
                    modifier      = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) sendAndClear() }),
                )
                IconButton(
                    onClick  = {
                        imageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled  = !isLoading,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Image,
                        contentDescription = stringResource(R.string.agent_image_pick),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick  = { launchSpeech() },
                    enabled  = !isLoading,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.agent_speech_mic),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledIconButton(
                    onClick  = { sendAndClear() },
                    enabled  = canSend,
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.agent_sheet_send),
                    )
                }
            }
        }
    }
}
