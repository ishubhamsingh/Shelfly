package dev.ishubhamsingh.shelfly.agent

import android.content.Context
import androidx.appfunctions.AppFunctionContext
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ishubhamsingh.shelfly.appfunctions.ShelflyAppFunctions
import dev.ishubhamsingh.shelfly.appfunctions.dto.ItemDto
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentToolCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appFunctions: ShelflyAppFunctions,
) {
    private val appFunctionContext = object : AppFunctionContext {
        override val context: Context = this@AgentToolCatalog.context
    }

    // ── Tool definitions (JSON Schema) ────────────────────────────────────────

    fun toolDefinitionsJson(): String = TOOL_DEFINITIONS_JSON

    // ── Dispatcher ────────────────────────────────────────────────────────────

    suspend fun execute(name: String, args: JSONObject): JSONObject {
        return try {
            when (name) {
                "addItem" -> {
                    val result = appFunctions.addItem(
                        appFunctionContext = appFunctionContext,
                        name       = args.getString("name"),
                        expiryDate = args.getString("expiryDate"),
                        category   = args.getString("category"),
                        qty        = args.optDoubleOrNull("qty"),
                        unit       = args.optStringOrNull("unit"),
                        notes      = args.optStringOrNull("notes"),
                    )
                    JSONObject().apply {
                        put("ok", true)
                        put("item", result.toJson())
                    }
                }

                "addItemFromMfg" -> {
                    val result = appFunctions.addItemFromMfg(
                        appFunctionContext = appFunctionContext,
                        name              = args.getString("name"),
                        mfgDate           = args.getString("mfgDate"),
                        bestBeforeMonths  = args.getInt("bestBeforeMonths"),
                        category          = args.getString("category"),
                        qty               = args.optDoubleOrNull("qty"),
                        unit              = args.optStringOrNull("unit"),
                        notes             = args.optStringOrNull("notes"),
                    )
                    JSONObject().apply {
                        put("ok", true)
                        put("item", result.toJson())
                    }
                }

                "listExpiringSoon" -> {
                    val items = appFunctions.listExpiringSoon(
                        appFunctionContext = appFunctionContext,
                        withinDays        = args.getInt("withinDays"),
                    )
                    JSONObject().apply {
                        put("ok", true)
                        put("items", items.toJsonArray())
                        put("count", items.size)
                    }
                }

                "listExpired" -> {
                    val items = appFunctions.listExpired(appFunctionContext)
                    JSONObject().apply {
                        put("ok", true)
                        put("items", items.toJsonArray())
                        put("count", items.size)
                    }
                }

                "listByCategory" -> {
                    val items = appFunctions.listByCategory(
                        appFunctionContext = appFunctionContext,
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
                        appFunctionContext = appFunctionContext,
                        id                = args.getString("id"),
                    )
                    JSONObject().apply {
                        put("ok", true)
                        if (item != null) put("item", item.toJson()) else put("item", JSONObject.NULL)
                    }
                }

                "markConsumed" -> {
                    val ok = appFunctions.markConsumed(
                        appFunctionContext = appFunctionContext,
                        id                = args.getString("id"),
                    )
                    JSONObject().apply { put("ok", ok) }
                }

                "updateExpiry" -> {
                    val item = appFunctions.updateExpiry(
                        appFunctionContext = appFunctionContext,
                        id                = args.getString("id"),
                        newExpiry         = args.getString("newExpiry"),
                    )
                    JSONObject().apply {
                        put("ok", item != null)
                        if (item != null) put("item", item.toJson()) else put("item", JSONObject.NULL)
                    }
                }

                "deleteItem" -> {
                    val ok = appFunctions.deleteItem(
                        appFunctionContext = appFunctionContext,
                        id                = args.getString("id"),
                    )
                    JSONObject().apply { put("ok", ok) }
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

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeUnless { it.isNaN() }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key).takeIf { it.isNotBlank() }
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
        "unit":       { "type": "string",  "description": "Optional unit, e.g. 'g', 'ml', 'tablets'." },
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
  }
]
""".trimIndent()
