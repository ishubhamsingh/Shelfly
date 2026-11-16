package dev.ishubhamsingh.shelfly.agent

import dev.ishubhamsingh.shelfly.appfunctions.ShelflyAppFunctions
import dev.ishubhamsingh.shelfly.appfunctions.dto.ItemDto
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentToolCatalog @Inject constructor(
    private val appFunctions: ShelflyAppFunctions,
) {
    // ── Tool definitions (JSON Schema) ────────────────────────────────────────

    fun toolDefinitionsJson(): String = TOOL_DEFINITIONS_JSON

    // ── Dispatcher ────────────────────────────────────────────────────────────

    /**
     * Resolves a user-supplied name string to the actual DB item name (first match).
     * Returns null if no matching item is found. Used to show the real name in
     * destructive-action confirmation cards before execution.
     */
    suspend fun lookupItemName(query: String): String? =
        appFunctions.findByName(query).firstOrNull()?.name

    suspend fun execute(name: String, args: JSONObject): JSONObject {
        return try {
            when (name) {
                "addItem" -> {
                    val expiryDate = resolveExpiry(args)
                    val (qty, unit) = parseAmount(args.optStringOrNull("amount"))
                    val result = appFunctions.addItem(
                        name       = args.getString("name"),
                        expiryDate = expiryDate,
                        category   = args.getString("category"),
                        qty        = qty,
                        unit       = unit,
                        notes      = args.optStringOrNull("notes"),
                    )
                    JSONObject().apply {
                        put("ok", true)
                        put("item", result.toJson())
                    }
                }

                "addItemFromMfg" -> {
                    val (qty, unit) = parseAmount(args.optStringOrNull("amount"))
                    val result = appFunctions.addItemFromMfg(
                        name              = args.getString("name"),
                        mfgDate           = args.getString("mfgDate"),
                        bestBeforeMonths  = args.getInt("bestBeforeMonths"),
                        category          = args.getString("category"),
                        qty               = qty,
                        unit              = unit,
                        notes             = args.optStringOrNull("notes"),
                    )
                    JSONObject().apply {
                        put("ok", true)
                        put("item", result.toJson())
                    }
                }

                "listExpiringSoon" -> {
                    val items = appFunctions.listExpiringSoon(
                        withinDays        = args.getInt("withinDays"),
                    )
                    JSONObject().apply {
                        put("ok", true)
                        put("items", items.toJsonArray())
                        put("count", items.size)
                    }
                }

                "listExpired" -> {
                    val items = appFunctions.listExpired()
                    JSONObject().apply {
                        put("ok", true)
                        put("items", items.toJsonArray())
                        put("count", items.size)
                    }
                }

                "listByCategory" -> {
                    val items = appFunctions.listByCategory(
                        category          = args.getString("category"),
                    )
                    JSONObject().apply {
                        put("ok", true)
                        put("items", items.toJsonArray())
                        put("count", items.size)
                    }
                }

                "getItem" -> {
                    val item = appFunctions.getItem(
                        id                = args.getString("id"),
                    )
                    JSONObject().apply {
                        put("ok", true)
                        if (item != null) put("item", item.toJson()) else put("item", JSONObject.NULL)
                    }
                }

                "markConsumed" -> {
                    val ok = appFunctions.markConsumed(
                        id                = args.getString("id"),
                    )
                    JSONObject().apply { put("ok", ok) }
                }

                "updateExpiry" -> {
                    val newExpiry = parseExpiryIn(args.getString("expiryIn")).toString()
                    val item = appFunctions.updateExpiry(
                        id                = args.getString("id"),
                        newExpiry         = newExpiry,
                    )
                    JSONObject().apply {
                        put("ok", item != null)
                        if (item != null) put("item", item.toJson()) else put("item", JSONObject.NULL)
                    }
                }

                "deleteItem" -> {
                    val ok = appFunctions.deleteItem(
                        id                = args.getString("id"),
                    )
                    JSONObject().apply { put("ok", ok) }
                }

                "markConsumedByName" -> {
                    val name    = args.getString("name")
                    val matches = appFunctions.findByName(name)
                    if (matches.isEmpty()) {
                        JSONObject().apply {
                            put("ok", false)
                            put("error", "No item named \"$name\" found on your shelf")
                        }
                    } else {
                        val item = matches.first()
                        val ok   = appFunctions.markConsumed(item.id)
                        JSONObject().apply {
                            put("ok", ok)
                            if (ok) put("item", item.toJson())
                        }
                    }
                }

                "deleteItemByName" -> {
                    val name    = args.getString("name")
                    val matches = appFunctions.findByName(name)
                    if (matches.isEmpty()) {
                        JSONObject().apply {
                            put("ok", false)
                            put("error", "No item named \"$name\" found on your shelf")
                        }
                    } else {
                        val item = matches.first()
                        val ok   = appFunctions.deleteItem(item.id)
                        JSONObject().apply {
                            put("ok", ok)
                            if (ok) put("deletedName", item.name)
                        }
                    }
                }

                else -> JSONObject().apply {
                    put("ok", false)
                    put("error", "Unknown function: $name")
                }
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("ok", false)
                put("error", e.message ?: "Execution error")
            }
        }
    }

    // ── Marshalling helpers ───────────────────────────────────────────────────

    /**
     * Parses a duration string produced by the model ("9 days", "3 weeks", "18 months",
     * "1.5 years") into an absolute LocalDate relative to today.
     * Also accepts an ISO-8601 date directly if the model produced one.
     */
    /**
     * Resolves the expiry for addItem, preferring an absolute date over a duration.
     *
     * Measured (Gemma 4 E2B, real can photos, Edge Gallery): asking for a DURATION made
     * the model estimate rather than read — a can printed "EXP 19/10/26" came back as
     * "3 months" (28 days late). Asking for the printed date returned 2026-10-19 exactly.
     *
     * Both keys stay supported: typed input ("milk in 10 days") is naturally a duration,
     * label scans are naturally a date. Falling back to expiryIn also keeps older
     * behaviour working if the model omits expiryDate.
     */
    private fun resolveExpiry(args: JSONObject): String {
        args.optStringOrNull("expiryDate")?.let { raw ->
            runCatching { LocalDate.parse(raw.trim()).toString() }
                .getOrNull()
                ?.let { return it }
        }
        return parseExpiryIn(args.getString("expiryIn")).toString()
    }

    private fun parseExpiryIn(raw: String): LocalDate {
        val today = LocalDate.now()
        val s = raw.trim().lowercase()

        // Already an ISO date — use directly
        if (s.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            return try { LocalDate.parse(s) } catch (_: Exception) { today.plusDays(7) }
        }

        // "tomorrow" shorthand
        if (s == "tomorrow") return today.plusDays(1)

        // Extract leading number (int or decimal) and time unit word
        val match = Regex("""(\d+(?:\.\d+)?)\s*(days?|weeks?|months?|years?)""").find(s)
            ?: return today.plusDays(7) // safe fallback

        val amount = match.groupValues[1].toDouble()
        return when {
            match.groupValues[2].startsWith("day")   -> today.plusDays(amount.toLong())
            match.groupValues[2].startsWith("week")  -> today.plusWeeks(amount.toLong())
            match.groupValues[2].startsWith("month") -> today.plusMonths(amount.toLong())
            match.groupValues[2].startsWith("year")  -> today.plusMonths((amount * 12).toLong())
            else -> today.plusDays(7)
        }
    }

    private fun ItemDto.toJson() = JSONObject().apply {
        put("id",             id)
        put("name",           name)
        put("category",       category)
        put("expiryDate",     expiryDate)
        put("daysUntilExpiry",daysUntilExpiry)
        put("status",         status)
        if (qty != null) put("qty", qty) else put("qty", JSONObject.NULL)
        if (unit != null) put("unit", unit) else put("unit", JSONObject.NULL)
        if (notes != null) put("notes", notes) else put("notes", JSONObject.NULL)
        put("consumed",       consumed)
    }

    private fun List<ItemDto>.toJsonArray(): JSONArray =
        JSONArray().also { arr -> forEach { arr.put(it.toJson()) } }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }

    /**
     * Parses the model's "amount" string (e.g. "250 ml", "10 tablets", "12 count")
     * into a (qty, unit) pair. All arithmetic is done here — the model only copies text.
     */
    private fun parseAmount(raw: String?): Pair<Double?, String?> {
        if (raw.isNullOrBlank()) return Pair(null, null)
        val s = raw.trim().lowercase()

        // "N dozen" → N*12, count
        Regex("""(\d+(?:\.\d+)?)\s*dozen""").find(s)?.let { m ->
            return Pair(m.groupValues[1].toDouble() * 12, "count")
        }
        // "half dozen" / "half-dozen" → 6, count
        if (s.contains("half dozen") || s.contains("half-dozen")) {
            return Pair(6.0, "count")
        }

        // Standard: number (int or decimal) followed by a unit word
        val match = Regex("""(\d+(?:\.\d+)?)\s*([a-z]+)""").find(s)
        if (match != null) {
            val qty  = match.groupValues[1].toDouble()
            val unit = normalizeUnit(match.groupValues[2])
            return Pair(qty, unit)
        }

        // Bare number with no unit → count
        Regex("""(\d+(?:\.\d+)?)""").find(s)?.let { m ->
            return Pair(m.groupValues[1].toDouble(), "count")
        }

        return Pair(null, null)
    }

    /**
     * Delegates to [ShelfUnits] so the agent and the form's chip row cannot drift.
     * Falls back to the raw string only when nothing maps, which the form then shows
     * as no chip selected rather than inventing one.
     */
    private fun normalizeUnit(raw: String): String =
        dev.ishubhamsingh.shelfly.domain.model.ShelfUnits.normalize(raw) ?: raw
}

// ── Tool definitions JSON ─────────────────────────────────────────────────────

private val TOOL_DEFINITIONS_JSON = """
[
  {
    "name": "addItem",
    "description": "Add a new item to the shelf with a specific expiry date.",
    "parameters": {
      "type": "object",
      "properties": {
        "name":       { "type": "string",  "description": "Human-readable name, e.g. 'Whole milk'." },
        "expiryDate": { "type": "string",  "description": "ISO-8601 date, e.g. '2026-05-17'. Resolve relative dates like 'tomorrow' or 'next Friday' to absolute dates." },
        "category":   { "type": "string",  "enum": ["FOOD","MEDICINE","COSMETIC","OTHER"], "description": "Item category." },
        "qty":        { "type": "number",  "description": "Optional numeric quantity." },
        "unit":       { "type": "string",  "description": "Optional unit, e.g. 'g', 'mg', 'ml', 'tablets'." },
        "notes":      { "type": "string",  "description": "Optional freeform notes." }
      },
      "required": ["name","expiryDate","category"]
    }
  },
  {
    "name": "addItemFromMfg",
    "description": "Add an item whose packaging shows a manufacture date and a best-before window in months.",
    "parameters": {
      "type": "object",
      "properties": {
        "name":             { "type": "string",  "description": "Human-readable name." },
        "mfgDate":          { "type": "string",  "description": "ISO-8601 manufacture date, e.g. '2025-11-01'." },
        "bestBeforeMonths": { "type": "integer", "description": "Months the item is good for after manufacture." },
        "category":         { "type": "string",  "enum": ["FOOD","MEDICINE","COSMETIC","OTHER"] },
        "qty":              { "type": "number" },
        "unit":             { "type": "string" },
        "notes":            { "type": "string" }
      },
      "required": ["name","mfgDate","bestBeforeMonths","category"]
    }
  },
  {
    "name": "listExpiringSoon",
    "description": "List items expiring within the next N days.",
    "parameters": {
      "type": "object",
      "properties": {
        "withinDays": { "type": "integer", "description": "How many days ahead to look. Use 7 for 'this week', 30 for 'this month'." }
      },
      "required": ["withinDays"]
    }
  },
  {
    "name": "listExpired",
    "description": "List all items that have already passed their expiry date.",
    "parameters": { "type": "object", "properties": {} }
  },
  {
    "name": "listByCategory",
    "description": "List all items in a specific category.",
    "parameters": {
      "type": "object",
      "properties": {
        "category": { "type": "string", "enum": ["FOOD","MEDICINE","COSMETIC","OTHER"] }
      },
      "required": ["category"]
    }
  },
  {
    "name": "getItem",
    "description": "Get a single item by its ID.",
    "parameters": {
      "type": "object",
      "properties": {
        "id": { "type": "string", "description": "UUID of the item." }
      },
      "required": ["id"]
    }
  },
  {
    "name": "markConsumed",
    "description": "Mark an item as consumed, removing it from the active shelf.",
    "parameters": {
      "type": "object",
      "properties": {
        "id": { "type": "string", "description": "UUID of the item to mark consumed." }
      },
      "required": ["id"]
    }
  },
  {
    "name": "updateExpiry",
    "description": "Update the expiry date of an existing item.",
    "parameters": {
      "type": "object",
      "properties": {
        "id":        { "type": "string", "description": "UUID of the item." },
        "newExpiry": { "type": "string", "description": "New ISO-8601 expiry date." }
      },
      "required": ["id","newExpiry"]
    }
  },
  {
    "name": "deleteItem",
    "description": "Permanently delete an item from the shelf.",
    "parameters": {
      "type": "object",
      "properties": {
        "id": { "type": "string", "description": "UUID of the item to delete." }
      },
      "required": ["id"]
    }
  },
  {
    "name": "markConsumedByName",
    "description": "Find an item by name and mark it as consumed. Use this when the user refers to an item by name.",
    "parameters": {
      "type": "object",
      "properties": {
        "name": { "type": "string", "description": "Name of the item as the user stated it." }
      },
      "required": ["name"]
    }
  },
  {
    "name": "deleteItemByName",
    "description": "Find an item by name and permanently delete it from the shelf. Use this when the user refers to an item by name.",
    "parameters": {
      "type": "object",
      "properties": {
        "name": { "type": "string", "description": "Name of the item as the user stated it." }
      },
      "required": ["name"]
    }
  }
]
""".trimIndent()
