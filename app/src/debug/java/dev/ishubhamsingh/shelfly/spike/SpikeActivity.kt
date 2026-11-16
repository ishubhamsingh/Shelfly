package dev.ishubhamsingh.shelfly.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ishubhamsingh.shelfly.ui.theme.ShelflyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShelflyTheme {
                SpikeScreen(context = this)
            }
        }
    }
}

private data class SpikeResult(
    val prompt: SpikePrompt,
    val rawOutput: String,
    val parsed: ParsedCall,
    val loadMs: Long,
    val inferenceMs: Long,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpikeScreen(context: android.content.Context) {
    val scope = rememberCoroutineScope()

    // Default path — user can override. Common adb push targets:
    //   adb push gemma.bin /sdcard/Download/gemma.bin  (then move to app filesDir)
    //   or: adb push gemma.task /data/local/tmp/gemma.task (needs root)
    var modelPath by remember {
        mutableStateOf(
            context.getExternalFilesDir("models")?.absolutePath + "/gemma.task"
        )
    }
    var customInput by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<SpikeResult>() }

    fun runPrompt(prompt: SpikePrompt) {
        if (running) return
        running = true
        statusMsg = "Loading model…"
        scope.launch {
            val todayIso = LocalDate.now().toString()
            val systemPrompt = buildSystemPrompt(todayIso)
            val fullPrompt = buildPrompt(systemPrompt, prompt.input)

            val result = withContext(Dispatchers.Default) {
                val loadStart = System.currentTimeMillis()
                val spike = try {
                    GemmaSpike(context, modelPath)
                } catch (e: Exception) {
                    return@withContext null to "Model load failed: ${e.message}"
                }
                val loadMs = System.currentTimeMillis() - loadStart

                val infStart = System.currentTimeMillis()
                val raw = try {
                    spike.runBlocking(fullPrompt)
                } catch (e: Exception) {
                    spike.close()
                    return@withContext null to "Inference failed: ${e.message}"
                }
                val infMs = System.currentTimeMillis() - infStart
                spike.close()

                val parsed = parseResponse(raw)
                SpikeResult(prompt, raw, parsed, loadMs, infMs) to null
            }

            val (spikeResult, error) = result
            if (spikeResult != null) {
                results.add(0, spikeResult)
                statusMsg = "Done. Load: ${spikeResult.loadMs}ms | Inference: ${spikeResult.inferenceMs}ms"
            } else {
                statusMsg = error ?: "Unknown error"
            }
            running = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Gemma Spike — Phase 0") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Model path
            OutlinedTextField(
                value = modelPath,
                onValueChange = { modelPath = it },
                label = { Text("Model path (on-device)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(
                "adb push gemma.task /sdcard/Android/data/dev.ishubhamsingh.shelfly/files/models/gemma.task",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )

            HorizontalDivider()

            // Quick-select spike prompts
            Text("Spike prompts", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SPIKE_PROMPTS.forEach { sp ->
                    FilterChip(
                        selected = false,
                        onClick = {
                            customInput = sp.input
                            runPrompt(sp)
                        },
                        label = { Text(sp.label, fontSize = 12.sp) },
                    )
                }
            }

            // Custom input
            OutlinedTextField(
                value = customInput,
                onValueChange = { customInput = it },
                label = { Text("Custom prompt") },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val sp = SpikePrompt("Custom", customInput)
                    runPrompt(sp)
                },
                enabled = !running && customInput.isNotBlank() && modelPath.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (running) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(4.dp))
                Text(if (running) "Running…" else "Run")
            }

            if (statusMsg.isNotBlank()) {
                Text(statusMsg, style = MaterialTheme.typography.bodySmall)
            }

            // Score summary
            if (results.isNotEmpty()) {
                val correct = results.count { it.parsed.functionName != null && it.parsed.parseError == null }
                val parseable = results.count { it.parsed.parseError == null }
                SummaryCard(total = results.size, correct = correct, parseable = parseable)
            }

            // Results list
            results.forEach { r ->
                ResultCard(r)
            }
        }
    }
}

@Composable
private fun SummaryCard(total: Int, correct: Int, parseable: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Score Summary", fontWeight = FontWeight.Bold)
            Text("Ran: $total  |  Parseable JSON: $parseable/$total  |  Function selected: $correct/$total")
            val pass = correct >= 7 && total >= 10
            Text(
                if (pass) "PASS (≥7/10 — proceed to Phase 1)" else "In progress (need 10 results)",
                color = if (pass) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ResultCard(r: SpikeResult) {
    val isOk = r.parsed.functionName != null && r.parsed.parseError == null
    val borderColor = when {
        r.parsed.parseError != null -> Color(0xFFB71C1C)
        r.parsed.functionName == null -> Color(0xFFF57F17)
        else -> Color(0xFF2E7D32)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isOk) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(r.prompt.label, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "${r.loadMs}ms load / ${r.inferenceMs}ms inf",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MonoText("Input: ${r.prompt.input}")
            MonoText("Function: ${r.parsed.functionName ?: "none"}")
            if (r.parsed.args != null) MonoText("Args: ${r.parsed.args}")
            if (r.parsed.reason != null) MonoText("Reason: ${r.parsed.reason}")
            if (r.parsed.parseError != null) MonoText("Error: ${r.parsed.parseError}", color = Color(0xFFB71C1C))
            HorizontalDivider(thickness = 0.5.dp)
            Text("Raw output:", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Text(r.rawOutput, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun MonoText(text: String, color: Color = Color.Unspecified) {
    Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = color)
}
