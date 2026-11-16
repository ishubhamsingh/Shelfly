package dev.ishubhamsingh.shelfly.spike.eval

import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Phase-0 backend evaluation suite.
 *
 * Two modes, because they answer different questions:
 *
 *  - [SuiteMode.DURATION] mirrors what production does today: the model copies the
 *    duration the user said ("10 days") and the APP resolves it to a date. This only
 *    measures transcription.
 *
 *  - [SuiteMode.DATE] asks the model to resolve the actual expiry date itself
 *    (today + 10 days -> "2026-08-26"), and to normalise real-world quantities
 *    ("half a litre" -> "0.5 L"). This measures arithmetic.
 *
 * Why the second mode matters: production retreated to duration-copying, and the
 * app-side resolver in AgentViewModel.computeExpiryDate() only understands
 * "<number> <unit>", "tomorrow", and ISO dates — everything else silently becomes
 * today+7. So "expires next Friday" is quietly wrong today. If a backend passes
 * DATE mode, that whole workaround can go away and those inputs start working.
 */

enum class SuiteMode(val label: String) {
    DURATION("Duration (prod)"),
    DATE("Date + qty math"),
    VISION("Vision (labels)"),
}

// ── System prompts ────────────────────────────────────────────────────────────

private const val COMMON_HEADER = """
## Output schema
Respond with exactly one JSON object — no prose, no markdown.
{ "function": <tool_name | null>, "args": { <param: value, ...> }, "reason": <string | null> }

## Field definitions

name : string
  Item name only — no quantity, no unit, no packaging words.
  Correct: "Eggs" / "Sunscreen" / "Ibuprofen"
  Wrong  : "1 dozen eggs" / "500ml milk" / "ibuprofen tablets"

category : enum
  "FOOD" — food and drinks
  "MEDICINE" — medicines, supplements, vitamins, pills
  "COSMETIC" — skincare, beauty, personal care, sunscreen
  "OTHER" — anything else
"""

fun evalSystemPrompt(todayIso: String, mode: SuiteMode): String = when (mode) {
    SuiteMode.VISION -> visionSystemPrompt(todayIso)

    SuiteMode.DURATION -> """
You are Shelfly, a shelf-tracking assistant. Today: $todayIso.
$COMMON_HEADER
amount : string  (optional)
  "<number> <unit>", unit one of: count / g / kg / mg / ml / L / tablets / pieces.
  Copy the exact number and unit — do not round.
  "1 dozen" -> "12 count", "half a dozen" -> "6 count".
  Omit entirely if no quantity was mentioned.

expiryIn : string
  Duration as stated: "<number> <unit>", unit one of days / weeks / months / years.
  Copy the EXACT number the user said — do not round or snap.
  "10 days" -> "10 days", NOT "1 week". "3 weeks" -> "3 weeks", NOT "1 month".
  Word-numbers to digits: "one" -> 1, "half" -> 0.5, "one and a half" -> 1.5.

## Tools
addItem(name*, expiryIn*, category*, amount, notes)
addItemFromMfg(name*, mfgDate*, bestBeforeMonths*, category*, amount, notes)
listExpiringSoon(withinDays*)
listExpired()
markConsumedByName(name*)
deleteItemByName(name*)
(* = required)

mfgDate : string  ISO-8601 (YYYY-MM-DD)
bestBeforeMonths : integer  whole months after manufacture
withinDays : integer
""".trimIndent()

    SuiteMode.DATE -> """
You are Shelfly, a shelf-tracking assistant. Today is $todayIso.
$COMMON_HEADER
amount : string  (optional)
  "<number> <unit>", unit one of: count / g / kg / mg / ml / L / tablets / pieces.
  Convert spoken quantities to a number plus one of those units:
    "a dozen" -> "12 count"        "half a dozen" -> "6 count"
    "half a litre" -> "0.5 L"      "quarter kg" -> "0.25 kg"
    "250 grams" -> "250 g"         "one and a half kg" -> "1.5 kg"
  Omit entirely if no quantity was mentioned.

expiryDate : string
  The ISO-8601 date (YYYY-MM-DD) on which the item expires.
  COMPUTE it from today ($todayIso) and what the user said:
    "in 10 days"  -> today plus 10 days
    "in 3 weeks"  -> today plus 21 days
    "tomorrow"    -> today plus 1 day
    "in 2 months" -> today plus 2 calendar months
    "next Friday" -> the first Friday strictly after today
  If the user states a manufacture date and a shelf life, add the shelf life to the
  manufacture date (e.g. made 2026-03-15, best before 18 months -> 2027-09-15).
  If the user states an absolute date, use it unchanged.

## Tools
addItem(name*, expiryDate*, category*, amount, notes)
listExpiringSoon(withinDays*)
listExpired()
markConsumedByName(name*)
deleteItemByName(name*)
(* = required)

withinDays : integer
""".trimIndent()
}

// ── Cases ─────────────────────────────────────────────────────────────────────

/**
 * @param expectedArgs key -> acceptable values. Several spellings are legitimately
 *   correct ("0.5 L" and "500 ml" are the same volume), so any match passes.
 */
data class EvalCase(
    val label: String,
    val input: String,
    val expectedFunction: String?,
    val expectedArgs: Map<String, List<String>> = emptyMap(),
    val probes: String = "",
)

/** Expectations are derived from [today] so the suite never goes stale. */
fun evalCases(today: LocalDate, mode: SuiteMode): List<EvalCase> = when (mode) {
    SuiteMode.DURATION -> durationCases()
    SuiteMode.DATE -> dateCases(today)
    // Vision cases carry images, so they run through visionCases() instead; this keeps
    // the count right for the UI without duplicating the expectations.
    SuiteMode.VISION -> visionCases().map {
        EvalCase(it.label, it.userInput, it.expectedFunction, it.expectedArgs, it.probes)
    }
}

private fun durationCases(): List<EvalCase> = listOf(
    EvalCase("Exact days (no snapping)", "add milk expiring in 10 days", "addItem",
        mapOf("name" to listOf("milk"), "expiryIn" to listOf("10 days"), "category" to listOf("FOOD")),
        "E2B commonly snaps 10 days -> '1 week'"),
    EvalCase("Exact weeks (no snapping)", "add bread, expires in 3 weeks", "addItem",
        mapOf("name" to listOf("bread"), "expiryIn" to listOf("3 weeks")),
        "3 weeks -> '1 month' is the classic failure"),
    EvalCase("Dozen conversion", "add a dozen eggs, expires in 2 weeks", "addItem",
        mapOf("name" to listOf("eggs"), "amount" to listOf("12 count"), "expiryIn" to listOf("2 weeks"))),
    EvalCase("Half-dozen conversion", "add half a dozen bananas expiring in 5 days", "addItem",
        mapOf("name" to listOf("bananas"), "amount" to listOf("6 count"), "expiryIn" to listOf("5 days"))),
    EvalCase("Fractional duration", "add cream, expires in one and a half months", "addItem",
        mapOf("name" to listOf("cream"), "expiryIn" to listOf("1.5 months"))),
    EvalCase("Mfg + shelf life", "sunscreen manufactured on 2026-03-15, best before 18 months", "addItemFromMfg",
        mapOf("name" to listOf("sunscreen"), "mfgDate" to listOf("2026-03-15"),
              "bestBeforeMonths" to listOf("18"), "category" to listOf("COSMETIC"))),
    EvalCase("Medicine + tablets", "add 30 ibuprofen tablets, expires in 2 years", "addItem",
        mapOf("name" to listOf("ibuprofen"), "amount" to listOf("30 tablets"),
              "expiryIn" to listOf("2 years"), "category" to listOf("MEDICINE")),
        "name must not absorb the unit word 'tablets'"),
    EvalCase("Cosmetic category", "add my face serum, expires in 8 months", "addItem",
        mapOf("expiryIn" to listOf("8 months"), "category" to listOf("COSMETIC"))),
    EvalCase("Name purity", "add 500ml milk expiring in 6 days", "addItem",
        mapOf("name" to listOf("milk"), "amount" to listOf("500 ml"), "expiryIn" to listOf("6 days"))),
    EvalCase("Query exact window", "what's expiring in the next 10 days", "listExpiringSoon",
        mapOf("withinDays" to listOf("10")), "must not snap to 7"),
    EvalCase("Destructive by name", "delete the yogurt", "deleteItemByName",
        mapOf("name" to listOf("yogurt"))),
    EvalCase("Off-topic (null tool)", "what's the weather like today?", null,
        emptyMap(), "must decline, not hallucinate a tool"),
)

private fun dateCases(today: LocalDate): List<EvalCase> {
    fun d(date: LocalDate) = listOf(date.toString())
    val nextFriday = today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))

    return listOf(
        // ── date arithmetic ──
        EvalCase("Date: +10 days", "add milk expiring in 10 days", "addItem",
            mapOf("name" to listOf("milk"), "expiryDate" to d(today.plusDays(10))),
            "today + 10 = ${today.plusDays(10)}"),
        EvalCase("Date: +3 weeks", "add bread, expires in 3 weeks", "addItem",
            mapOf("name" to listOf("bread"), "expiryDate" to d(today.plusWeeks(3))),
            "today + 21d = ${today.plusWeeks(3)}"),
        EvalCase("Date: tomorrow", "add yogurt expiring tomorrow", "addItem",
            mapOf("name" to listOf("yogurt"), "expiryDate" to d(today.plusDays(1)))),
        EvalCase("Date: +2 months", "add cheese, expires in 2 months", "addItem",
            mapOf("name" to listOf("cheese"), "expiryDate" to d(today.plusMonths(2))),
            "calendar-month arithmetic, not 60 days"),
        EvalCase("Date: next Friday", "add juice expiring next Friday", "addItem",
            mapOf("name" to listOf("juice"), "expiryDate" to d(nextFriday)),
            "weekday resolution — today's app silently returns today+7 for this"),
        EvalCase("Date: +1.5 months", "add cream, expires in one and a half months", "addItem",
            mapOf("name" to listOf("cream"),
                  "expiryDate" to listOf(today.plusMonths(1).plusDays(15).toString(),
                                         today.plusDays(45).toString())),
            "fractional month — either reading accepted"),
        EvalCase("Date: mfg + 18 months", "sunscreen manufactured on 2026-03-15, best before 18 months", "addItem",
            mapOf("name" to listOf("sunscreen"), "expiryDate" to listOf("2027-09-15"),
                  "category" to listOf("COSMETIC")),
            "absolute-date arithmetic, independent of today"),
        EvalCase("Date: absolute passthrough", "add vitamins, expiry date 2027-01-31", "addItem",
            mapOf("name" to listOf("vitamins"), "expiryDate" to listOf("2027-01-31")),
            "must NOT recompute an explicit date"),

        // ── quantity normalisation ──
        EvalCase("Qty: half a litre", "add half a litre of milk, expires in 5 days", "addItem",
            mapOf("name" to listOf("milk"), "amount" to listOf("0.5 L", "500 ml"),
                  "expiryDate" to d(today.plusDays(5)))),
        EvalCase("Qty: quarter kg", "add quarter kg of butter, expires in 3 weeks", "addItem",
            mapOf("name" to listOf("butter"), "amount" to listOf("0.25 kg", "250 g"),
                  "expiryDate" to d(today.plusWeeks(3)))),
        EvalCase("Qty: 250 grams", "add 250 grams of cheese, expires in 12 days", "addItem",
            mapOf("name" to listOf("cheese"), "amount" to listOf("250 g", "0.25 kg"),
                  "expiryDate" to d(today.plusDays(12)))),
        EvalCase("Qty: one and a half kg", "add one and a half kg of rice, expires in 6 months", "addItem",
            mapOf("name" to listOf("rice"), "amount" to listOf("1.5 kg", "1500 g"),
                  "expiryDate" to d(today.plusMonths(6)))),
        EvalCase("Qty: a dozen", "add a dozen eggs, expires in 2 weeks", "addItem",
            mapOf("name" to listOf("eggs"), "amount" to listOf("12 count"),
                  "expiryDate" to d(today.plusWeeks(2)))),
        EvalCase("Qty + name purity", "add 30 ibuprofen tablets, expires in 2 years", "addItem",
            mapOf("name" to listOf("ibuprofen"), "amount" to listOf("30 tablets"),
                  "expiryDate" to d(today.plusYears(2)), "category" to listOf("MEDICINE"))),

        EvalCase("Off-topic (null tool)", "what's the weather like today?", null,
            emptyMap(), "must decline, not hallucinate a tool"),
    )
}

// ── Scoring ───────────────────────────────────────────────────────────────────

data class ArgCheck(val key: String, val expected: String, val actual: String?, val pass: Boolean)

data class CaseScore(
    val functionPass: Boolean,
    val argChecks: List<ArgCheck>,
    val parseError: String?,
) {
    val argsPassed: Int get() = argChecks.count { it.pass }
    val argsTotal: Int get() = argChecks.size
    val fullPass: Boolean get() = parseError == null && functionPass && argsPassed == argsTotal
}

/**
 * Lowercase, collapse whitespace, and split a glued number+unit ("500ml" -> "500 ml")
 * so scoring measures the VALUE, not the spacing. Spacing is a prompt nit we can fix
 * cheaply; a wrong number is a capability failure. Keeping them separate stops the
 * former from masquerading as the latter.
 */
private fun norm(s: String): String {
    val base = s.trim().lowercase().replace(Regex("\\s+"), " ").removeSuffix(".")
    return Regex("^(\\d+(?:\\.\\d+)?)\\s*([a-z]+)$").find(base)
        ?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }
        ?: base
}

private fun singleMatch(expected: String, actual: String): Boolean {
    val e = norm(expected)
    val a = norm(actual)
    if (e == a) return true
    e.toDoubleOrNull()?.let { en -> a.toDoubleOrNull()?.let { an -> return en == an } }
    val eParts = e.split(" ", limit = 2)
    val aParts = a.split(" ", limit = 2)
    if (eParts.size == 2 && aParts.size == 2) {
        val en = eParts[0].toDoubleOrNull()
        val an = aParts[0].toDoubleOrNull()
        if (en != null && an != null && en == an) return eParts[1] == aParts[1]
    }
    return false
}

fun scoreCase(case: EvalCase, parsed: ParsedEval): CaseScore {
    if (parsed.parseError != null) return CaseScore(false, emptyList(), parsed.parseError)
    val fnPass = norm(parsed.functionName ?: "null") == norm(case.expectedFunction ?: "null")
    val checks = case.expectedArgs.map { (key, accepted) ->
        val actual = parsed.args?.let { if (it.has(key)) it.optString(key) else null }
        ArgCheck(
            key = key,
            expected = accepted.joinToString(" | "),
            actual = actual,
            pass = actual != null && accepted.any { singleMatch(it, actual) },
        )
    }
    return CaseScore(fnPass, checks, null)
}

// ── Response parsing ──────────────────────────────────────────────────────────

data class ParsedEval(
    val functionName: String?,
    val args: JSONObject?,
    val reason: String?,
    val parseError: String?,
)

fun parseEvalResponse(raw: String): ParsedEval {
    val start = raw.indexOf('{')
    if (start == -1) return ParsedEval(null, null, null, "no JSON object in output")
    return try {
        val obj = JSONObject(repairJson(raw.substring(start)))
        val name = if (obj.isNull("function")) null
                   else obj.optString("function").takeIf { it.isNotBlank() }
        val args = obj.optJSONObject("args") ?: obj.optJSONArray("args")?.optJSONObject(0)
        ParsedEval(name, args, obj.optString("reason").takeIf { it.isNotBlank() }, null)
    } catch (e: Exception) {
        ParsedEval(null, null, null, e.message ?: "JSON parse error")
    }
}

private fun repairJson(s: String): String {
    val sb = StringBuilder(s)
    val stack = ArrayDeque<Char>()
    var inString = false
    var escape = false
    for (ch in s) {
        when {
            escape -> escape = false
            ch == '\\' -> escape = true
            ch == '"' -> inString = !inString
            inString -> Unit
            ch == '{' -> stack.addLast('}')
            ch == '[' -> stack.addLast(']')
            ch == '}' || ch == ']' -> if (stack.isNotEmpty()) stack.removeLast()
        }
    }
    while (stack.isNotEmpty()) sb.append(stack.removeLast())
    return sb.toString()
}
