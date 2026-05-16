package dev.ishubhamsingh.shelfly.ui.agent

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ishubhamsingh.shelfly.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    // Pending image: thumbnail for display, file path for the model
    var pendingThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var pendingImagePath by remember { mutableStateOf<String?>(null) }
    var showImageMenu    by remember { mutableStateOf(false) }
    // URI of the temp file created for camera capture
    var cameraOutputUri  by remember { mutableStateOf<Uri?>(null) }

    fun clearImage() { pendingThumbnail = null; pendingImagePath = null }
    fun sendAndClear() {
        viewModel.run(input, pendingImagePath)
        input = ""
        clearImage()
    }

    // Shared: decode URI → thumbnail, save to temp file, store path for model
    fun processUri(uri: Uri) {
        scope.launch(Dispatchers.IO) {
            // Pass 1: bounds only
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val sample = maxOf(boundsOpts.outWidth, boundsOpts.outHeight) / 1024

            // Pass 2: subsample to ~1 MP
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                val opts = BitmapFactory.Options().apply { inSampleSize = maxOf(1, sample) }
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@launch

            // Save to a temp file — Content.ImageFile reads the file directly,
            // which avoids the raw-pixel format issue with Content.ImageBytes
            val tempFile = File(context.cacheDir, "camera")
                .also { it.mkdirs() }
                .let { File(it, "agent_${System.currentTimeMillis()}.jpg") }
            tempFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }

            withContext(Dispatchers.Main) {
                pendingThumbnail = bitmap
                pendingImagePath = tempFile.absolutePath
            }
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> if (uri != null) processUri(uri) }

    // Camera — writes to a FileProvider temp file
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraOutputUri?.let { processUri(it) } }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "camera").also { it.mkdirs() }
                .let { File(it, "label_${System.currentTimeMillis()}.jpg") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            val file = File(context.cacheDir, "camera").also { it.mkdirs() }
                .let { File(it, "label_${System.currentTimeMillis()}.jpg") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            cameraOutputUri = uri
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
            val canSend   = !isLoading && (input.isNotBlank() || pendingImagePath != null)

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
                Box {
                    IconButton(
                        onClick  = { showImageMenu = true },
                        enabled  = !isLoading,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Image,
                            contentDescription = stringResource(R.string.agent_image_pick),
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(
                        expanded          = showImageMenu,
                        onDismissRequest  = { showImageMenu = false },
                    ) {
                        DropdownMenuItem(
                            text         = { Text(stringResource(R.string.agent_image_camera)) },
                            leadingIcon  = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                            onClick      = { showImageMenu = false; launchCamera() },
                        )
                        DropdownMenuItem(
                            text         = { Text(stringResource(R.string.agent_image_gallery)) },
                            leadingIcon  = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                            onClick      = {
                                showImageMenu = false
                                galleryLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        )
                    }
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
