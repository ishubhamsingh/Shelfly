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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ishubhamsingh.shelfly.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Max photos per turn. Users naturally shoot a product from several angles — front for
 * the name, back/rim for the printed date — and the old cap of 2 silently discarded the
 * rest, which is how a can's expiry date never reached the model at all.
 * Both backends take a part list (ML Kit `Content` from beta4; LiteRT `Contents.of`).
 */
private const val MAX_AGENT_IMAGES = 6

/**
 * Target longest edge for images sent to the model. Small print — stamped expiry dates,
 * net-quantity text — has to survive this. Kept finite because images consume input
 * tokens and AICore caps input at ~4000.
 */
private const val AGENT_IMAGE_LONG_EDGE_PX = 1536

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSheet(
    onDismiss     : () -> Unit,
    onAddManually : (PendingItem?) -> Unit = {},
    viewModel     : AgentViewModel = hiltViewModel(),
) {
    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    val lastPrompt by viewModel.lastPrompt.collectAsStateWithLifecycle()

    var input           by rememberSaveable { mutableStateOf("") }
    val focusRequester  = remember { FocusRequester() }
    val context         = LocalContext.current
    val scope           = rememberCoroutineScope()

    // Up to 2 pending images.
    //
    // These MUST be rememberSaveable: attaching an image sends the user to the camera
    // or photo picker, and MainActivity declares no configChanges, so a rotation or a
    // low-memory reclaim while they are away recreates the Activity. With plain
    // remember{} the attached images silently vanished.
    //
    // cameraOutputUri especially — if it is lost while the camera app is in front, the
    // result callback fires with null and the photo the user just took is dropped on
    // the floor with no error.
    var pendingImagePaths by rememberSaveable(
        stateSaver = listSaver<List<String>, String>(save = { it.toList() }, restore = { it })
    ) { mutableStateOf(emptyList<String>()) }
    var showImageMenu     by remember { mutableStateOf(false) }
    var cameraOutputUri   by rememberSaveable { mutableStateOf<Uri?>(null) }

    // Thumbnails are derived from the paths rather than saved: Bitmaps are far too
    // large for a Bundle (TransactionTooLargeException), and the PNGs on disk are the
    // real source of truth. This also repopulates previews after a recreation.
    var pendingThumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(pendingImagePaths) {
        if (pendingThumbnails.size != pendingImagePaths.size) {
            pendingThumbnails = withContext(Dispatchers.IO) {
                pendingImagePaths.mapNotNull { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            }
        }
    }

    fun removeImage(index: Int) {
        pendingThumbnails = pendingThumbnails.toMutableList().also { it.removeAt(index) }
        pendingImagePaths = pendingImagePaths.toMutableList().also { it.removeAt(index) }
    }
    fun clearImages() { pendingThumbnails = emptyList(); pendingImagePaths = emptyList() }
    fun sendAndClear() {
        viewModel.run(input, pendingImagePaths)
        input = ""
        clearImages()
    }

    /** Decodes one image to the cache dir and returns its path, or null on failure. */
    fun decodeToCache(uri: Uri): String? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        // Downsampling to ~512px made small print unreadable: a date stamped on a can
        // rim is only a few pixels tall at that size, and the model hallucinated a value
        // instead of reading one (name came back right, numbers wrong). BitmapFactory
        // also rounds inSampleSize DOWN to a power of two, so the old target produced an
        // unpredictable 512–1000px depending on the source photo.
        val sample = maxOf(boundsOpts.outWidth, boundsOpts.outHeight) / AGENT_IMAGE_LONG_EDGE_PX
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            val opts = BitmapFactory.Options().apply { inSampleSize = maxOf(1, sample) }
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        val tempFile = File(context.cacheDir, "camera")
            .also { it.mkdirs() }
            .let { File(it, "agent_${System.currentTimeMillis()}_${uri.hashCode()}.png") }
        tempFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return tempFile.absolutePath
    }

    /**
     * Adds a batch of images, bounded once up front.
     *
     * Bounding here rather than inside the per-image work matters: the decodes run
     * concurrently, so a per-image `size >= MAX` guard would let every image in a batch
     * pass the check before any of them had been appended.
     */
    fun processUris(uris: List<Uri>) {
        val room = MAX_AGENT_IMAGES - pendingImagePaths.size
        if (room <= 0 || uris.isEmpty()) return
        val batch = uris.take(room)
        scope.launch(Dispatchers.IO) {
            val paths = batch.mapNotNull { runCatching { decodeToCache(it) }.getOrNull() }
            if (paths.isEmpty()) return@launch
            // Thumbnails are re-derived from the paths by the LaunchedEffect above, so
            // paths remain the single source of truth across Activity recreation.
            withContext(Dispatchers.Main) { pendingImagePaths = pendingImagePaths + paths }
        }
    }

    fun processUri(uri: Uri) = processUris(listOf(uri))

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_AGENT_IMAGES)
    ) { uris: List<Uri> -> processUris(uris) }

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
        try { speechLauncher.launch(intent) } catch (_: ActivityNotFoundException) { }
    }

    BackHandler(onBack = onDismiss)

    // Full-screen overlay in the Activity's Compose tree — inherits window insets.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // ── Top bar ───────────────────────────────────────────────────
            AgentTopBar(onDismiss = onDismiss)

            // ── Scrollable state area ─────────────────────────────────────
            val isLoading = uiState is AgentUiState.Loading
            val canSend   = !isLoading && (input.isNotBlank() || pendingImagePaths.isNotEmpty())

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                when (val s = uiState) {
                    AgentUiState.Idle -> {
                        AgentIdleContent(
                            onSuggestionClick = { suggestion ->
                                input = suggestion
                                sendAndClear()
                            },
                            onVoiceClick  = { launchSpeech() },
                            onCameraClick = { launchCamera() },
                        )
                    }
                    AgentUiState.Loading -> {
                        AgentThinkingContent(lastPrompt = lastPrompt)
                    }
                    is AgentUiState.ConfirmAdd -> {
                        AgentConfirmAddContent(
                            item          = s.item,
                            lastPrompt    = lastPrompt,
                            onConfirm     = { viewModel.confirmAdd(s.item) },
                            onEditManually = { onAddManually(s.item) },  // no dismiss — sheet stays alive
                            onTryAgain    = { viewModel.reset() },
                        )
                    }
                    is AgentUiState.ConfirmDestructive -> {
                        AgentConfirmDestructiveContent(
                            functionName  = s.functionName,
                            itemName      = s.itemName,
                            lastPrompt    = lastPrompt,
                            onConfirm     = { viewModel.confirmDestructive(s.functionName, s.rawArgs) },
                            onCancel      = { viewModel.reset() },
                        )
                    }
                    is AgentUiState.ItemListResult -> {
                        AgentItemListContent(
                            items      = s.items,
                            summary    = s.summary,
                            lastPrompt = lastPrompt,
                            onTryAgain = { viewModel.reset() },
                        )
                    }
                    is AgentUiState.Result -> {
                        AgentResultContent(reply = s.reply)
                    }
                    is AgentUiState.Error -> {
                        AgentFailureContent(
                            lastPrompt    = lastPrompt,
                            reason        = s.reason,
                            onAddManually = { onDismiss(); onAddManually(null) },
                            onTryAgain    = { viewModel.reset() },
                        )
                    }
                }
            }

            // ── Pill-shaped composer bar ──────────────────────────────────
            AgentComposerBar(
                input             = input,
                onInputChange     = { input = it },
                isLoading         = isLoading,
                canSend           = canSend,
                pendingThumbnails = pendingThumbnails,
                pendingImagePaths = pendingImagePaths,
                focusRequester    = focusRequester,
                showImageMenu     = showImageMenu,
                onShowImageMenu   = { showImageMenu = it },
                onRemoveImage     = { removeImage(it) },
                onSend            = { sendAndClear() },
                onMic             = { launchSpeech() },
                onCamera          = { launchCamera() },
                onGallery         = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Composer bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AgentComposerBar(
    input             : String,
    onInputChange     : (String) -> Unit,
    isLoading         : Boolean,
    canSend           : Boolean,
    pendingThumbnails : List<Bitmap>,
    pendingImagePaths : List<String>,
    focusRequester    : FocusRequester,
    showImageMenu     : Boolean,
    onShowImageMenu   : (Boolean) -> Unit,
    onRemoveImage     : (Int) -> Unit,
    onSend            : () -> Unit,
    onMic             : () -> Unit,
    onCamera          : () -> Unit,
    onGallery         : () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
    ) {
        // Thumbnail strip above bar
        if (pendingThumbnails.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                pendingThumbnails.forEachIndexed { index, bitmap ->
                    Box {
                        Image(
                            bitmap             = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.agent_image_preview),
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .size(72.dp)
                                .clip(MaterialTheme.shapes.medium),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier         = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = CircleShape,
                                ),
                        ) {
                            IconButton(
                                onClick  = { onRemoveImage(index) },
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
            }
        }

        // Pill bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ＋ attachment button
            Box {
                IconButton(
                    onClick  = { if (!isLoading && pendingImagePaths.size < MAX_AGENT_IMAGES) onShowImageMenu(true) },
                    enabled  = !isLoading && pendingImagePaths.size < MAX_AGENT_IMAGES,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.agent_image_pick),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(
                    expanded         = showImageMenu,
                    onDismissRequest = { onShowImageMenu(false) },
                ) {
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.agent_image_camera)) },
                        leadingIcon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
                        onClick     = { onShowImageMenu(false); onCamera() },
                    )
                    DropdownMenuItem(
                        text        = { Text(stringResource(R.string.agent_image_gallery)) },
                        leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                        onClick     = { onShowImageMenu(false); onGallery() },
                    )
                }
            }

            // Text input
            BasicTextField(
                value         = input,
                onValueChange = onInputChange,
                enabled       = !isLoading,
                singleLine    = true,
                modifier      = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                textStyle     = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) {
                            Text(
                                text  = stringResource(R.string.agent_composer_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )

            // Send (when text/images present) or Mic
            FilledIconButton(
                onClick  = if (canSend) onSend else onMic,
                enabled  = !isLoading || canSend,
            ) {
                Icon(
                    imageVector        = if (canSend) Icons.AutoMirrored.Filled.Send else Icons.Filled.Mic,
                    contentDescription = if (canSend)
                        stringResource(R.string.agent_sheet_send)
                    else
                        stringResource(R.string.agent_speech_mic),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AgentTopBar(onDismiss: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector        = Icons.Filled.Close,
                contentDescription = "Close",
                tint               = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Sparkle badge
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector        = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier           = Modifier.size(16.dp),
            )
        }

        Spacer(Modifier.width(10.dp))

        Column {
            Text(
                text  = stringResource(R.string.agent_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text  = stringResource(R.string.agent_private_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Idle state
// ─────────────────────────────────────────────────────────────────────────────

private data class Capability(
    val icon    : ImageVector,
    val label   : String,
    val onClick : (() -> Unit)?,
)

@Composable
private fun AgentIdleContent(
    onSuggestionClick : (String) -> Unit,
    onVoiceClick      : () -> Unit,
    onCameraClick     : () -> Unit,
) {
    val prompts = remember {
        listOf(
            "Crucin expiring March 2027",
            "Milk that goes bad tomorrow",
            "Photo of a product label",
            "Sunscreen – used up",
        )
    }

    Spacer(Modifier.height(24.dp))

    // ① "What would you like to add to your shelf?" — highlight "add"
    val heading = stringResource(R.string.agent_idle_heading)
    Text(
        text  = buildAnnotatedString {
            val addStart = heading.indexOf("add")
            if (addStart >= 0) {
                append(heading.substring(0, addStart))
                withStyle(SpanStyle(
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )) {
                    append("add")
                }
                append(heading.substring(addStart + 3))
            } else {
                append(heading)
            }
        },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Spacer(Modifier.height(8.dp))

    Text(
        text  = stringResource(R.string.agent_idle_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(24.dp))

    // ② 3 capability pills: Text · Voice · Camera  (no Gallery)
    val capabilities = listOf(
        Capability(Icons.Filled.Keyboard,    stringResource(R.string.agent_pill_text),   null),
        Capability(Icons.Filled.Mic,         stringResource(R.string.agent_pill_voice),  onVoiceClick),
        Capability(Icons.Filled.PhotoCamera, stringResource(R.string.agent_pill_camera), onCameraClick),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        capabilities.forEach { cap ->
            CapabilityPill(icon = cap.icon, label = cap.label, onClick = cap.onClick)
        }
    }

    Spacer(Modifier.height(32.dp))

    Text(
        text  = stringResource(R.string.agent_try_saying),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(8.dp))

    // ③ Suggestion rows with → arrow on right
    prompts.forEach { suggestion ->
        SuggestionRow(text = suggestion, onClick = { onSuggestionClick(suggestion) })
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CapabilityPill(
    icon    : ImageVector,
    label   : String,
    onClick : (() -> Unit)?,
) {
    val base = Modifier
        .background(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
        )
        .padding(horizontal = 10.dp, vertical = 6.dp)

    val mod = if (onClick != null) base.clickable(onClick = onClick) else base

    Row(
        modifier              = mod,
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier           = Modifier.size(14.dp),
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SuggestionRow(text: String, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        color    = MaterialTheme.colorScheme.surfaceContainerLow,
        shape    = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Sparkle icon left
            Icon(
                imageVector        = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(14.dp),
            )
            // Text expands
            Text(
                text     = "\"$text\"",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // ③ Arrow right
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(14.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Thinking state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AgentThinkingContent(lastPrompt: String?) {
    Spacer(Modifier.height(16.dp))

    if (!lastPrompt.isNullOrBlank()) {
        YouSaidCard(text = lastPrompt)
        Spacer(Modifier.height(32.dp))
    }

    // ⑤ Sparkle-star blob (large AutoAwesome icon with radial glow + rotation + scale pulse)
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxWidth(),
    ) {
        AnimatedSparkleBlob(size = 200.dp)
    }

    Spacer(Modifier.height(24.dp))

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text  = stringResource(R.string.agent_thinking_heading),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(4.dp))
        AnimatedDots()
    }

    Spacer(Modifier.height(24.dp))

    ThinkingStepList()
}

// ⑤ Star/sparkle blob: large AutoAwesome icon + radial glow + slow rotation + scale pulse
@Composable
private fun AnimatedSparkleBlob(size: Dp) {
    val infiniteTransition = rememberInfiniteTransition(label = "blob")

    // Slow rotation (12 s full circle — more graceful than 8 s)
    val rotation by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blob_rotation",
    )

    // Gentle scale breath
    val blobScale by infiniteTransition.animateFloat(
        initialValue  = 0.90f,
        targetValue   = 1.10f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blob_scale",
    )

    val primary = MaterialTheme.colorScheme.primary

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.size(size),
    ) {
        // Radial glow halo behind the star
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.20f),
                            primary.copy(alpha = 0.08f),
                            Color.Transparent,
                        )
                    ),
                    shape = CircleShape,
                )
        )

        // 4-point sparkle star icon
        Icon(
            imageVector        = Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint               = primary,
            modifier           = Modifier
                .size(size * 0.58f)
                .rotate(rotation)
                .scale(blobScale),
        )
    }
}

@Composable
private fun AnimatedDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val alphas = (0..2).map { i ->
        infiniteTransition.animateFloat(
            initialValue  = 0.2f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(600, delayMillis = i * 200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot_alpha_$i",
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        alphas.forEach { alphaState ->
            val alpha by alphaState
            Text(
                text  = "·",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
        }
    }
}

@Composable
private fun ThinkingStepList() {
    val steps = listOf(
        "Reading your input"     to true,
        "Checking your shelf"    to true,
        "Extracting item details" to false,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        steps.forEach { (label, done) ->
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (done) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(20.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                            ),
                    ) {
                        Text(
                            text  = "✓",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                } else {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text  = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (done)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Result state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AgentResultContent(reply: String) {
    Spacer(Modifier.height(16.dp))
    Surface(
        color    = MaterialTheme.colorScheme.primaryContainer,
        shape    = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = reply,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(14.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Failure state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AgentFailureContent(
    lastPrompt    : String?,
    reason        : String,
    onAddManually : () -> Unit,
    onTryAgain    : () -> Unit,
) {
    Spacer(Modifier.height(16.dp))

    if (!lastPrompt.isNullOrBlank()) {
        YouSaidCard(text = lastPrompt)
        Spacer(Modifier.height(16.dp))
    }

    Surface(
        color    = MaterialTheme.colorScheme.errorContainer,
        shape    = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = stringResource(R.string.agent_failure_heading),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = stringResource(R.string.agent_failure_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(10.dp))
            listOf(
                stringResource(R.string.agent_failure_tip_name),
                stringResource(R.string.agent_failure_tip_expiry),
                stringResource(R.string.agent_failure_tip_photo),
            ).forEach { tip ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier              = Modifier.padding(vertical = 2.dp),
                ) {
                    Text("•", style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(tip,  style = MaterialTheme.typography.bodySmall,
                         color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        TextButton(onClick = onAddManually) { Text(stringResource(R.string.agent_add_manually)) }
        TextButton(onClick = onTryAgain)    { Text(stringResource(R.string.agent_try_again)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun YouSaidCard(text: String) {
    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape    = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text  = stringResource(R.string.agent_you_said),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirm-add state
// ─────────────────────────────────────────────────────────────────────────────

private enum class FieldBadge { OK, CHECK }

@Composable
private fun AgentConfirmAddContent(
    item           : PendingItem,
    lastPrompt     : String?,
    onConfirm      : () -> Unit,
    onEditManually : () -> Unit,
    onTryAgain     : () -> Unit,
) {
    val today  = LocalDate.now()
    val expiry = item.expiryDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val days   = expiry?.let { ChronoUnit.DAYS.between(today, it).toInt() } ?: 0
    // No date means the model honestly could not read one. The item cannot be saved
    // without it, so the card asks for it instead of pretending or erroring out.
    val needsExpiry = expiry == null

    // ── Days-left chip colours ────────────────────────────────────────────────
    val chipContainer = when {
        needsExpiry -> MaterialTheme.colorScheme.tertiaryContainer
        days < 0  -> MaterialTheme.colorScheme.errorContainer
        days <= 7 -> MaterialTheme.colorScheme.tertiaryContainer
        else      -> MaterialTheme.colorScheme.primaryContainer
    }
    val chipContent = when {
        needsExpiry -> MaterialTheme.colorScheme.onTertiaryContainer
        days < 0  -> MaterialTheme.colorScheme.onErrorContainer
        days <= 7 -> MaterialTheme.colorScheme.onTertiaryContainer
        else      -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    val daysChipText = when {
        needsExpiry -> stringResource(R.string.agent_expiry_add_date)
        days <   0  -> "Expired"
        days ==  0  -> "Today"
        days <   7  -> "${days}d left"
        days <  30  -> "${days / 7}w left"
        days < 365  -> "${days / 30}mo left"
        else        -> "${days / 365}y left"
    }

    // ── Category icon + colours ───────────────────────────────────────────────
    val catIcon = when (item.categoryEnum) {
        "FOOD"     -> Icons.Filled.Restaurant
        "MEDICINE" -> Icons.Filled.Medication
        "COSMETIC" -> Icons.Filled.Face
        else       -> Icons.Filled.Category
    }
    val catContainer = when (item.categoryEnum) {
        "FOOD"     -> MaterialTheme.colorScheme.primaryContainer
        "MEDICINE" -> MaterialTheme.colorScheme.tertiaryContainer
        "COSMETIC" -> MaterialTheme.colorScheme.secondaryContainer
        else       -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val catContent = when (item.categoryEnum) {
        "FOOD"     -> MaterialTheme.colorScheme.onPrimaryContainer
        "MEDICINE" -> MaterialTheme.colorScheme.onTertiaryContainer
        "COSMETIC" -> MaterialTheme.colorScheme.onSecondaryContainer
        else       -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val categoryLabel = when (item.categoryEnum) {
        "FOOD"     -> stringResource(R.string.category_food)
        "MEDICINE" -> stringResource(R.string.category_medicine)
        "COSMETIC" -> stringResource(R.string.category_cosmetic)
        else       -> stringResource(R.string.category_other)
    }

    val expiryFormatted = expiry?.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
        ?: stringResource(R.string.agent_expiry_not_found)

    Spacer(Modifier.height(16.dp))

    // ── Header: green circle + title + subtitle ───────────────────────────────
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Icon(
                imageVector        = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier           = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text       = stringResource(R.string.agent_confirm_add_heading),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text  = stringResource(R.string.agent_confirm_add_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // ── Item card ─────────────────────────────────────────────────────────────
    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape    = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Hero row: category circle + name/subtitle + days chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier          = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier
                        .size(48.dp)
                        .background(catContainer, CircleShape),
                ) {
                    Icon(
                        imageVector        = catIcon,
                        contentDescription = null,
                        tint               = catContent,
                        modifier           = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = item.name,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface,
                    )
                    val subline = listOfNotNull(item.amount, categoryLabel).joinToString(" · ")
                    if (subline.isNotBlank()) {
                        Text(
                            text  = subline,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(color = chipContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        text     = daysChipText,
                        style    = MaterialTheme.typography.labelSmall,
                        color    = chipContent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            // Expiry field row
            ConfirmFieldRow(
                icon  = Icons.Filled.CalendarToday,
                label = stringResource(R.string.detail_label_expiry),
                value = expiryFormatted,
                badge = FieldBadge.OK,
            )

            // Quantity (optional)
            if (!item.amount.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                ConfirmFieldRow(
                    icon  = Icons.Filled.Balance,
                    label = stringResource(R.string.detail_label_quantity),
                    value = item.amount,
                    badge = FieldBadge.OK,
                )
            }

            // Notes (optional) — shown with "? Check" badge (may need review)
            if (!item.notes.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                ConfirmFieldRow(
                    icon  = Icons.Filled.Description,
                    label = stringResource(R.string.detail_label_notes),
                    value = item.notes,
                    badge = FieldBadge.CHECK,
                )
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    // ── Actions: [Discard]  ───────────  [Edit] [✓ Add to shelf] ─────────────
    Row(
        modifier              = Modifier.fillMaxWidth(),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onTryAgain) {
            Text(
                text  = stringResource(R.string.agent_confirm_discard),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEditManually) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.agent_confirm_edit))
            }
            // An item cannot be stored without an expiry date, so when the model could
            // not read one, send the user to the form (date prefilled empty) rather
            // than offering a save that would fail.
            if (needsExpiry) {
                Button(onClick = onEditManually) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.agent_expiry_set_cta))
                }
            } else {
                Button(onClick = onConfirm) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.form_add_cta))
                }
            }
        }
    }
}

@Composable
private fun ConfirmFieldRow(
    icon  : androidx.compose.ui.graphics.vector.ImageVector,
    label : String,
    value : String,
    badge : FieldBadge,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier          = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier           = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text  = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(8.dp))
        when (badge) {
            FieldBadge.OK -> Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            ) {
                Icon(
                    imageVector        = Icons.Filled.Check,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier           = Modifier.size(13.dp),
                )
            }
            FieldBadge.CHECK -> Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text     = stringResource(R.string.agent_confirm_field_check),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Confirm-destructive state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AgentConfirmDestructiveContent(
    functionName : String,
    itemName     : String?,
    lastPrompt   : String?,
    onConfirm    : () -> Unit,
    onCancel     : () -> Unit,
) {
    val isDelete = functionName == "deleteItem" || functionName == "deleteItemByName"
    val titleRes = when {
        isDelete && itemName != null -> R.string.agent_confirm_delete_title_named
        isDelete                     -> R.string.agent_confirm_delete_title
        itemName != null             -> R.string.agent_confirm_consumed_title_named
        else                         -> R.string.agent_confirm_consumed_title
    }
    val title  = if (itemName != null) stringResource(titleRes, itemName)
                 else                  stringResource(titleRes)
    val bodyRes    = if (isDelete) R.string.agent_confirm_delete_body
                     else          R.string.agent_confirm_consumed_body
    val icon       = if (isDelete) Icons.Filled.Delete else Icons.Filled.CheckCircle
    val iconTint   = if (isDelete) MaterialTheme.colorScheme.error
                     else          MaterialTheme.colorScheme.primary

    Spacer(Modifier.height(16.dp))

    if (!lastPrompt.isNullOrBlank()) {
        YouSaidCard(text = lastPrompt)
        Spacer(Modifier.height(20.dp))
    }

    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape    = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier          = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(56.dp)
                    .background(
                        color = if (isDelete) MaterialTheme.colorScheme.errorContainer
                                else          MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = iconTint,
                    modifier           = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text  = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = stringResource(bodyRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    if (isDelete) {
        Button(
            onClick  = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor   = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.agent_confirm_destruct_confirm))
        }
    } else {
        Button(
            onClick  = onConfirm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.agent_confirm_destruct_confirm))
        }
    }

    Spacer(Modifier.height(4.dp))

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.form_cancel))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Item-list result state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AgentItemListContent(
    items      : List<ItemSummary>,
    summary    : String,
    lastPrompt : String?,
    onTryAgain : () -> Unit,
) {
    Spacer(Modifier.height(16.dp))

    if (!lastPrompt.isNullOrBlank()) {
        YouSaidCard(text = lastPrompt)
        Spacer(Modifier.height(16.dp))
    }

    // Summary chip
    Surface(
        color    = MaterialTheme.colorScheme.secondaryContainer,
        shape    = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text     = summary,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }

    Spacer(Modifier.height(12.dp))

    // Item rows
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            ItemSummaryRow(item = item)
        }
    }

    Spacer(Modifier.height(16.dp))

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onTryAgain) {
            Text(stringResource(R.string.agent_try_again))
        }
    }
}

@Composable
private fun ItemSummaryRow(item: ItemSummary) {
    val days = item.daysUntilExpiry
    val expiryStr = when {
        days == 0 -> stringResource(R.string.agent_confirm_expiry_today)
        days == 1 -> "Tomorrow"
        days  > 0 -> stringResource(R.string.detail_days_left, days)
        else      -> stringResource(R.string.detail_expired_ago, -days)
    }
    val statusColor = when {
        days < 0  -> MaterialTheme.colorScheme.error
        days <= 7 -> MaterialTheme.colorScheme.tertiary
        else      -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val categoryLabel = when (item.category) {
        "FOOD"     -> stringResource(R.string.category_food)
        "MEDICINE" -> stringResource(R.string.category_medicine)
        "COSMETIC" -> stringResource(R.string.category_cosmetic)
        else       -> stringResource(R.string.category_other)
    }

    Surface(
        color    = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape    = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text  = expiryStr,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
            )
        }
    }
}
