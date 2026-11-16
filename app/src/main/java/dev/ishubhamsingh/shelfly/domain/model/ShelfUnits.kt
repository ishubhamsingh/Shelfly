package dev.ishubhamsingh.shelfly.domain.model

/**
 * The canonical set of quantity units, and the mapping from whatever a model or a user
 * actually writes onto that set.
 *
 * Single source of truth on purpose. The form renders selectable chips from [CANONICAL],
 * and the agent normalises into the same list — so an extracted unit always lands on a
 * chip. Previously the form hard-coded its own list while the agent normalised against a
 * separate one, so a value like "cans" or "ML" produced a selected-nothing chip row and
 * the unit was silently lost on save.
 */
object ShelfUnits {

    /** Order matters — this is the chip order shown in the form. */
    val CANONICAL = listOf("count", "g", "kg", "mg", "ml", "L", "tablets", "pieces")

    /**
     * Maps a free-form unit onto [CANONICAL], or null if it is not a quantity unit we
     * support. Null is deliberate: dropping an unmappable unit is better than showing a
     * chip row where nothing is selected and the typed value quietly disappears.
     */
    fun normalize(raw: String?): String? {
        val s = raw?.trim()?.lowercase()?.removeSuffix(".") ?: return null
        if (s.isEmpty()) return null
        return when (s) {
            "count", "ct", "no", "nos", "qty"                      -> "count"
            "tab", "tabs", "tablet", "tablets", "cap", "caps",
            "capsule", "capsules"                                  -> "tablets"
            "pc", "pcs", "piece", "pieces", "unit", "units",
            // Container words are not quantities, but users and models reach for them;
            // mapping to "pieces" keeps the number rather than discarding the field.
            "can", "cans", "bottle", "bottles", "pack", "packs",
            "sachet", "sachets", "strip", "strips"                 -> "pieces"
            "l", "ltr", "litre", "litres", "liter", "liters"       -> "L"
            "ml", "milliliter", "milliliters", "millilitre",
            "millilitres", "mililiter", "cc"                       -> "ml"
            "mg", "milligram", "milligrams"                        -> "mg"
            "g", "gm", "gms", "gram", "grams"                      -> "g"
            "kg", "kgs", "kilogram", "kilograms", "kilo", "kilos"  -> "kg"
            else                                                   -> null
        }
    }

    /**
     * Splits "330 ml", "330ml", "1.5 kg" or "12" into a numeric part and a canonical
     * unit. Handles the no-space form, which models emit often and a naive
     * `split(" ")` turns into the qty string "330ml" with no unit selected.
     */
    fun splitAmount(raw: String?): Pair<String, String?> {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return "" to null
        val m = Regex("""^(\d+(?:[.,]\d+)?)\s*([A-Za-z]*)$""").find(s)
            ?: return s to null
        val qty  = m.groupValues[1].replace(',', '.')
        val unit = normalize(m.groupValues[2])
        return qty to unit
    }
}
