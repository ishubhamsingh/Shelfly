package dev.ishubhamsingh.shelfly.ui.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ishubhamsingh.shelfly.agent.AgentTurn
import dev.ishubhamsingh.shelfly.agent.AiCoreManager
import dev.ishubhamsingh.shelfly.agent.AiCoreState
import dev.ishubhamsingh.shelfly.agent.GemmaAgent
import dev.ishubhamsingh.shelfly.agent.GemmaModelManager
import dev.ishubhamsingh.shelfly.agent.ModelState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject

// ── UI-layer model classes ────────────────────────────────────────────────────

/**
 * Extracted details of a pending add operation (addItem / addItemFromMfg),
 * ready for display in the confirm card.
 */
data class PendingItem(
    val functionName : String,
    val rawArgs      : String,   // JSONObject.toString() — re-parsed on confirm
    val name         : String,
    /**
     * ISO "YYYY-MM-DD", or null when the model could not read one.
     *
     * Nullable on purpose: the model is instructed to omit a date it cannot see rather
     * than invent one, and the old contract turned that honest answer into a dead-end
     * error card. Missing is a state to surface for correction, not a failure.
     */
    val expiryDate   : String?,
    val categoryEnum : String,   // "FOOD" | "MEDICINE" | "COSMETIC" | "OTHER"
    val amount       : String?,
    val notes        : String?,
)

/** One row in the item-list result card. */
data class ItemSummary(
    val id              : String,
    val name            : String,
    val category        : String,
    val daysUntilExpiry : Int,
    val status          : String,
    val qty             : Double?,
    val unit            : String?,
)

// ── UI state ──────────────────────────────────────────────────────────────────

sealed interface AgentUiState {
    data object Idle    : AgentUiState
    data object Loading : AgentUiState

    /** Confirm before saving a new item. */
    data class ConfirmAdd(val item: PendingItem) : AgentUiState

    /** Confirm before executing a destructive operation (delete / markConsumed). */
    data class ConfirmDestructive(
        val functionName : String,
        val rawArgs      : String,
        val itemName     : String? = null,   // populated for name-based tools
    ) : AgentUiState

    /** Plain-text reply from the agent (non-list tool calls, or raw text replies). */
    data class Result(val reply: String) : AgentUiState

    /** A list of shelf items returned by a list query. */
    data class ItemListResult(
        val items   : List<ItemSummary>,
        val summary : String,
    ) : AgentUiState

    data class Error(val reason: String) : AgentUiState
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class AgentViewModel @Inject constructor(
    private val agent: GemmaAgent,
    modelManager: GemmaModelManager,
    private val aiCoreManager: AiCoreManager,
) : ViewModel() {

    val modelState: StateFlow<ModelState> = modelManager.state.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = ModelState.NotDownloaded,
    )

    val aiCoreState: StateFlow<AiCoreState> = aiCoreManager.state.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.WhileSubscribed(5_000),
        initialValue = AiCoreState.Unknown,
    )

    /**
     * The assistant is usable if EITHER backend can serve a turn. Gating the UI on the
     * downloaded model alone hides the assistant on devices where Nano is ready and no
     * download is needed — which is the whole point of the AICore path.
     */
    val assistantReady: StateFlow<Boolean> =
        combine(modelState, aiCoreState) { model, aiCore ->
            model is ModelState.Ready || aiCore is AiCoreState.Ready
        }.stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private val _uiState    = MutableStateFlow<AgentUiState>(AgentUiState.Idle)
    private val _lastPrompt = MutableStateFlow<String?>(null)

    val uiState    : StateFlow<AgentUiState> = _uiState.asStateFlow()
    val lastPrompt : StateFlow<String?>      = _lastPrompt.asStateFlow()

    init {
        // Pre-load as soon as EITHER backend can serve, so the first inference doesn't
        // pay the cold-start cost while the sheet is visible. Waiting on the downloaded
        // model alone would never warm up on a Nano-only device.
        viewModelScope.launch {
            assistantReady.filter { it }.first()
            agent.warmUp()
        }
    }

    fun run(input: String, imagePaths: List<String> = emptyList()) {
        if (input.isBlank() && imagePaths.isEmpty()) return
        _lastPrompt.value = when {
            input.isNotBlank() && imagePaths.isNotEmpty() ->
                "${input.trim()} (+${imagePaths.size} photo${if (imagePaths.size > 1) "s" else ""})"
            input.isNotBlank() -> input.trim()
            else -> "${imagePaths.size} photo${if (imagePaths.size > 1) "s" else ""} attached"
        }
        _uiState.value = AgentUiState.Loading
        viewModelScope.launch {
            _uiState.value = resolveTurn(agent.run(input, imagePaths))
        }
    }

    /** User tapped "Add to shelf" on the confirm-add card. */
    fun confirmAdd(item: PendingItem) {
        _uiState.value = AgentUiState.Loading
        viewModelScope.launch {
            _uiState.value = resolveTurn(
                agent.confirm(item.functionName, JSONObject(item.rawArgs))
            )
        }
    }

    /** User tapped "Confirm" on the destructive-action card. */
    fun confirmDestructive(functionName: String, rawArgs: String) {
        _uiState.value = AgentUiState.Loading
        viewModelScope.launch {
            _uiState.value = resolveTurn(
                agent.confirm(functionName, JSONObject(rawArgs))
            )
        }
    }

    fun reset() {
        _uiState.value    = AgentUiState.Idle
        _lastPrompt.value = null
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun resolveTurn(turn: AgentTurn): AgentUiState = when (turn) {
        is AgentTurn.Pending -> when (turn.functionName) {
            "addItem", "addItemFromMfg" ->
                buildPendingItem(turn)
                    ?.let { AgentUiState.ConfirmAdd(it) }
                    ?: AgentUiState.Error("Could not parse item details from agent response")

            "deleteItem", "markConsumed" ->
                AgentUiState.ConfirmDestructive(turn.functionName, turn.args.toString())

            "deleteItemByName", "markConsumedByName" -> {
                val promptName = turn.args.optString("name").takeIf { it.isNotBlank() }
                // Look up the real DB name so the card shows the stored name, not the prompt term
                val resolvedName = promptName?.let {
                    runCatching { agent.lookupItemName(it) }.getOrNull() ?: it
                }
                AgentUiState.ConfirmDestructive(turn.functionName, turn.args.toString(), resolvedName)
            }

            else -> AgentUiState.Error("Unexpected pending function: ${turn.functionName}")
        }

        is AgentTurn.ToolCall -> when (turn.functionName) {
            "listExpiringSoon", "listExpired", "listByCategory" -> {
                val items = parseItemList(turn.result)
                if (items.isNotEmpty()) AgentUiState.ItemListResult(items, turn.reply)
                else AgentUiState.Result(turn.reply)
            }
            else -> AgentUiState.Result(turn.reply)
        }

        is AgentTurn.Reply   -> AgentUiState.Result(turn.text)
        is AgentTurn.Failure -> AgentUiState.Error(turn.reason)
    }

    private fun buildPendingItem(turn: AgentTurn.Pending): PendingItem? {
        val args = turn.args
        val name = args.optStringOrNull("name") ?: return null

        val expiryDate: String? = when (turn.functionName) {
            "addItem" -> {
                // Absolute date wins: on label scans the model transcribes what is
                // printed, which is exact. A duration makes it estimate — measured
                // "3 months" for a can printed EXP 19/10/26. Mirrors
                // AgentToolCatalog.resolveExpiry() so the card and the DB agree.
                val printed = args.optStringOrNull("expiryDate")
                    ?.let { raw -> runCatching { LocalDate.parse(raw.trim()).toString() }.getOrNull() }
                // Null when neither is present — the card asks the user for it.
                printed ?: args.optStringOrNull("expiryIn")?.let { computeExpiryDate(it) }
            }
            "addItemFromMfg" -> {
                val mfgDate = args.optStringOrNull("mfgDate")
                val months  = args.optInt("bestBeforeMonths", 0)
                mfgDate?.let {
                    runCatching { LocalDate.parse(it).plusMonths(months.toLong()).toString() }
                        .getOrNull()
                }
            }
            else -> return null
        }

        return PendingItem(
            functionName = turn.functionName,
            rawArgs      = args.toString(),
            name         = name,
            expiryDate   = expiryDate,
            categoryEnum = args.optStringOrNull("category") ?: "OTHER",
            amount       = args.optStringOrNull("amount"),
            notes        = args.optStringOrNull("notes"),
        )
    }

    /**
     * Mirrors AgentToolCatalog.parseExpiryIn() — duplicated here so the ViewModel
     * has no dependency on the catalog layer.
     */
    private fun computeExpiryDate(raw: String): String {
        val today = LocalDate.now()
        val s     = raw.trim().lowercase()
        if (s.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return s
        if (s == "tomorrow") return today.plusDays(1).toString()
        val match = Regex("""(\d+(?:\.\d+)?)\s*(days?|weeks?|months?|years?)""").find(s)
            ?: return today.plusDays(7).toString()
        val amount = match.groupValues[1].toDouble()
        return when {
            match.groupValues[2].startsWith("day")   -> today.plusDays(amount.toLong())
            match.groupValues[2].startsWith("week")  -> today.plusWeeks(amount.toLong())
            match.groupValues[2].startsWith("month") -> today.plusMonths(amount.toLong())
            match.groupValues[2].startsWith("year")  -> today.plusMonths((amount * 12).toLong())
            else                                      -> today.plusDays(7)
        }.toString()
    }

    private fun parseItemList(result: JSONObject): List<ItemSummary> {
        val arr  = result.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<ItemSummary>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list += ItemSummary(
                id              = obj.optString("id"),
                name            = obj.optString("name"),
                category        = obj.optString("category"),
                daysUntilExpiry = obj.optInt("daysUntilExpiry", 0),
                status          = obj.optString("status"),
                qty             = if (obj.isNull("qty"))  null else obj.optDouble("qty"),
                unit            = if (obj.isNull("unit")) null else obj.optString("unit"),
            )
        }
        return list
    }
}

// ── Top-level helpers ─────────────────────────────────────────────────────────

/**
 * Null-safe string read.
 *
 * `JSONObject.optString` returns the literal text `"null"` for a JSON null on Android,
 * so a blank check alone lets `"amount": null` reach the UI and render as "null".
 * Nano emits explicit nulls for optional fields where Gemma tended to omit the key,
 * which is why this surfaces now.
 */
private fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null
    else optString(key).trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

// ── Top-level helper ──────────────────────────────────────────────────────────

/** Serialize a [PendingItem] to a compact JSON string for nav-arg prefill. */
fun PendingItem.toPrefillJson(): String = JSONObject().apply {
    put("name", name)
    // Omitted when unknown, so the form opens with an empty date for the user to pick
    // rather than a placeholder that looks like a real value.
    if (expiryDate != null) put("expiryDate", expiryDate)
    put("categoryEnum", categoryEnum)
    if (amount != null) put("amount", amount)
    if (notes != null) put("notes", notes)
}.toString()
