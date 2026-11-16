package dev.ishubhamsingh.shelfly.spike.eval

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

/**
 * Phase-0 backend eval harness.
 *
 * Launch:
 *   adb shell am start -n dev.ishubhamsingh.shelfly.debug/dev.ishubhamsingh.shelfly.spike.eval.EvalActivity
 */
class EvalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ShelflyTheme { EvalScreen(this) } }
    }
}

private enum class BackendChoice(val label: String) {
    AICORE("AICore / Nano"),
    E4B("Gemma E4B"),
    E2B("Gemma E2B"),
}

/** Fraction of cases needing FULL credit (tool + every arg) for a tier to be shippable. */
private const val PASS_FRACTION = 0.83

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvalScreen(context: Context) {
    val scope = rememberCoroutineScope()
    val modelsDir = remember { context.getExternalFilesDir("models")?.absolutePath.orEmpty() }

    var choice by remember { mutableStateOf(BackendChoice.AICORE) }
    var mode by remember { mutableStateOf(SuiteMode.DATE) }
    val caseCount = remember(mode) { evalCases(LocalDate.now(), mode).size }
    var lowTemp by remember { mutableStateOf(false) }
    // 0 = original crisp labels; otherwise the app's downsample target in px
    var visionRes by remember { mutableStateOf(512) }
    var e2bPath by remember { mutableStateOf("$modelsDir/gemma.litertlm") }
    var e4bPath by remember { mutableStateOf("$modelsDir/gemma-e4b.litertlm") }
    var running by remember { mutableStateOf(false) }
    // Separate from [running]: the Nano download is a different action, and sharing
    // one flag made a download-in-progress render as "Running…" on the RUN button
    // with a stale status line underneath — indistinguishable from an eval run.
    var downloading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val runs = remember { mutableStateListOf<EvalRun>() }

    val deviceInfo = remember {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalGb = mi.totalMem / 1_000_000_000.0
        "RAM ${"%.1f".format(totalGb)} GB · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · SDK ${android.os.Build.VERSION.SDK_INT}"
    }

    fun buildBackend(): EvalBackend = when (choice) {
        BackendChoice.AICORE -> AiCoreBackend()
        BackendChoice.E4B -> LiteRtBackend(context, e4bPath, "Gemma E4B", lowTemp)
        BackendChoice.E2B -> LiteRtBackend(context, e2bPath, "Gemma E2B", lowTemp)
    }

    fun runSuite() {
        if (running) return
        running = true
        runs.clear()
        status = "Preparing ${choice.label}…"
        scope.launch {
            val backend = buildBackend()
            val today = LocalDate.now()
            val isVision = mode == SuiteMode.VISION
            val systemPrompt =
                if (isVision) visionSystemPrompt(today.toString())
                else evalSystemPrompt(today.toString(), mode)
            val cases = evalCases(today, mode)
            val visionImages = if (isVision) visionCases().map { it.imageIds } else emptyList()
            try {
                val blocked = withContext(Dispatchers.Default) { backend.unavailableReason() }
                if (blocked != null) {
                    status = "Unavailable: $blocked"
                    return@launch
                }
                // One engine load for the whole suite — per-case reload would swamp
                // the latency numbers we are trying to measure.
                cases.forEachIndexed { i, case ->
                    status = "Running ${i + 1}/${cases.size}: ${case.label}"
                    val imgs = visionImages.getOrNull(i)?.map { id ->
                        if (visionRes == 0) syntheticLabel(id)
                        else downsampleLikeApp(realisticLabel(id), visionRes)
                    } ?: emptyList()
                    val run = withContext(Dispatchers.Default) {
                        val t0 = System.currentTimeMillis()
                        val raw = try {
                            backend.generate(systemPrompt, case.input, imgs)
                        } catch (e: Exception) {
                            "INFERENCE_ERROR: ${e.message}"
                        }
                        val ms = System.currentTimeMillis() - t0
                        val parsed = parseEvalResponse(raw)
                        EvalRun(case, raw, parsed, scoreCase(case, parsed), ms)
                    }
                    runs.add(run)
                }
                val sampler = if (choice == BackendChoice.AICORE) "t0.2" else if (lowTemp) "t0.2" else "t1.0"
                status = "Done — ${choice.label} · ${mode.label} · $sampler"
            } catch (e: Exception) {
                status = "Failed: ${e.message}"
            } finally {
                withContext(Dispatchers.Default) { runCatching { backend.close() } }
                running = false
            }
        }
    }

    fun downloadNano() {
        if (running || downloading) return
        downloading = true
        status = "Requesting Nano download…"
        scope.launch {
            val backend = AiCoreBackend()
            try {
                withContext(Dispatchers.Default) {
                    backend.ensureDownloaded { status = it }
                }
                // The Flow can complete without ever emitting progress — AICore hands
                // the transfer to Android System Intelligence, which downloads out of
                // process. Re-read checkStatus() so we report real state, not silence.
                val reason = withContext(Dispatchers.Default) { backend.unavailableReason() }
                status = reason ?: "Nano is AVAILABLE — ready to run."
            } catch (e: Exception) {
                status = "Download failed: ${e.message}"
            } finally {
                downloading = false
            }
        }
    }

    /** Nano downloads out of process, so polling status is the only honest progress signal. */
    fun recheckStatus() {
        if (running || downloading) return
        scope.launch {
            val backend = AiCoreBackend()
            val reason = withContext(Dispatchers.Default) { backend.unavailableReason() }
            status = "${backend.lastStatus}${reason?.let { " — $it" } ?: " — ready to run."}"
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Backend Eval — Phase 0") }) }) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(deviceInfo, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackendChoice.entries.forEach { c ->
                    FilterChip(
                        selected = choice == c,
                        onClick = { choice = c },
                        label = { Text(c.label, fontSize = 12.sp) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuiteMode.entries.forEach { m ->
                    FilterChip(
                        selected = mode == m,
                        onClick = { mode = m },
                        label = { Text(m.label, fontSize = 12.sp) },
                    )
                }
            }

            // Vision resolution sweep — realistic 2160px label put through the app's
            // own downsample at each target, to isolate "resolution too low" from
            // "model can't read labels".
            if (mode == SuiteMode.VISION) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(512 to "512px (app)", 1024 to "1024px", 2160 to "full").forEach { (px, lbl) ->
                        FilterChip(
                            selected = visionRes == px,
                            onClick = { visionRes = px },
                            label = { Text(lbl, fontSize = 12.sp) },
                        )
                    }
                }
            }

            // Sampler A/B — only affects the LiteRT backends; AICore always runs 0.2/16.
            if (choice != BackendChoice.AICORE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !lowTemp,
                        onClick = { lowTemp = false },
                        label = { Text("temp 1.0 (prod)", fontSize = 12.sp) },
                    )
                    FilterChip(
                        selected = lowTemp,
                        onClick = { lowTemp = true },
                        label = { Text("temp 0.2 (match Nano)", fontSize = 12.sp) },
                    )
                }
            }

            when (choice) {
                BackendChoice.E2B -> OutlinedTextField(
                    value = e2bPath, onValueChange = { e2bPath = it },
                    label = { Text("E2B model path") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                BackendChoice.E4B -> OutlinedTextField(
                    value = e4bPath, onValueChange = { e4bPath = it },
                    label = { Text("E4B model path") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                BackendChoice.AICORE -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { downloadNano() },
                        enabled = !running && !downloading,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (downloading) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (downloading) "Downloading…" else "Download Nano", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { recheckStatus() },
                        enabled = !running && !downloading,
                        modifier = Modifier.weight(1f),
                    ) { Text("Re-check status", fontSize = 12.sp) }
                }
            }

            Button(
                onClick = { runSuite() },
                enabled = !running && !downloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (running) "Running…" else "Run all $caseCount cases")
            }

            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)

            if (runs.isNotEmpty()) SummaryCard(runs, caseCount)
            runs.forEach { RunCard(it) }
        }
    }
}

@Composable
private fun SummaryCard(runs: List<EvalRun>, caseCount: Int) {
    val full = runs.count { it.score.fullPass }
    val fnOk = runs.count { it.score.functionPass && it.score.parseError == null }
    val argsOk = runs.sumOf { it.score.argsPassed }
    val argsTot = runs.sumOf { it.score.argsTotal }
    val unparseable = runs.count { it.score.parseError != null }
    val meanMs = if (runs.isEmpty()) 0 else runs.sumOf { it.latencyMs } / runs.size
    val complete = runs.size == caseCount
    val bar = Math.ceil(caseCount * PASS_FRACTION).toInt()
    val pass = complete && full >= bar

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Summary", fontWeight = FontWeight.Bold)
            Text("Full pass (tool + all args): $full/${runs.size}")
            Text("Tool choice correct:         $fnOk/${runs.size}")
            Text("Arg values correct:          $argsOk/$argsTot")
            Text("Unparseable JSON:            $unparseable/${runs.size}")
            Text("Mean latency:                ${meanMs}ms")
            Spacer(Modifier.size(4.dp))
            Text(
                when {
                    !complete -> "Incomplete run"
                    pass -> "PASS (≥$bar/$caseCount full credit)"
                    else -> "FAIL (<$bar/$caseCount full credit)"
                },
                color = if (pass) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun RunCard(r: EvalRun) {
    val tint = when {
        r.score.parseError != null -> Color(0xFFB71C1C)
        r.score.fullPass -> Color(0xFF2E7D32)
        r.score.functionPass -> Color(0xFFF57F17)   // right tool, wrong numbers
        else -> Color(0xFFB71C1C)
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row {
                Text(r.case.label, fontWeight = FontWeight.SemiBold, color = tint)
                Spacer(Modifier.weight(1f))
                Text("${r.latencyMs}ms", fontSize = 10.sp)
            }
            Mono("in:  ${r.case.input}")
            Mono("fn:  ${r.parsed.functionName ?: "null"}  (want ${r.case.expectedFunction ?: "null"})")
            r.score.argChecks.forEach { c ->
                Mono(
                    "${if (c.pass) "✓" else "✗"} ${c.key}: ${c.actual ?: "<missing>"}" +
                        if (c.pass) "" else "   want ${c.expected}",
                    color = if (c.pass) Color.Unspecified else Color(0xFFB71C1C),
                )
            }
            if (r.score.parseError != null) Mono("parse error: ${r.score.parseError}", Color(0xFFB71C1C))
            if (r.case.probes.isNotBlank()) {
                Text(r.case.probes, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                    .padding(6.dp)
            ) { Text(r.raw, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun Mono(text: String, color: Color = Color.Unspecified) {
    Text(text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = color)
}
