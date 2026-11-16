package dev.ishubhamsingh.shelfly.spike.eval

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Programmatically rendered product labels for the vision suite.
 *
 * WHY SYNTHETIC: these give exact ground truth and zero setup, which makes them a good
 * PLUMBING and FLOOR test — if a backend cannot read crisp black-on-white text at a
 * known size, it certainly cannot read a crumpled foil pouch under kitchen lighting.
 *
 * WHAT THEY DO NOT MEASURE: real label scanning. No glare, no curvature, no focus blur,
 * no tiny 6pt date codes, no foil, no non-Latin scripts, no "MFG 03/26" stamped in
 * dot-matrix on a crimped seam. A backend passing this suite has cleared the floor,
 * not the bar. Real photos are required before trusting any of these numbers.
 */

private const val W = 720
private const val H = 900

private fun label(block: (Canvas, Paint) -> Unit): Bitmap {
    val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 44f
    }
    block(canvas, paint)
    return bmp
}

private fun Canvas.line(paint: Paint, text: String, x: Float, y: Float, size: Float = 44f, bold: Boolean = false) {
    paint.textSize = size
    paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    drawText(text, x, y, paint)
}

/** A visual style knob so every label does not look identical to the model. */
fun syntheticLabel(id: String): Bitmap = when (id) {

    "milk" -> label { c, p ->
        c.line(p, "DAIRY BEST", 60f, 140f, 64f, bold = true)
        c.line(p, "Whole Milk", 60f, 230f, 52f)
        c.line(p, "1 L", 60f, 320f, 48f)
        c.line(p, "BEST BEFORE", 60f, 470f, 36f)
        c.line(p, "2026-09-20", 60f, 540f, 56f, bold = true)
        c.line(p, "Keep refrigerated", 60f, 640f, 32f)
    }

    "ibuprofen" -> label { c, p ->
        c.line(p, "IBUPROFEN", 60f, 140f, 60f, bold = true)
        c.line(p, "200 mg film-coated tablets", 60f, 210f, 34f)
        c.line(p, "30 tablets", 60f, 300f, 46f)
        c.line(p, "EXP  2028-04-30", 60f, 470f, 50f, bold = true)
        c.line(p, "Store below 25C", 60f, 560f, 30f)
    }

    "sunscreen_mfg" -> label { c, p ->
        c.line(p, "SOLARA", 60f, 130f, 62f, bold = true)
        c.line(p, "Sunscreen Lotion SPF 50", 60f, 200f, 38f)
        c.line(p, "200 ml", 60f, 285f, 46f)
        c.line(p, "MFG  2026-03-15", 60f, 450f, 46f)
        c.line(p, "Best before 18 months", 60f, 520f, 40f)
        c.line(p, "from manufacture", 60f, 575f, 40f)
    }

    "olive_oil" -> label { c, p ->
        c.line(p, "TERRA VERDE", 60f, 130f, 56f, bold = true)
        c.line(p, "Extra Virgin Olive Oil", 60f, 200f, 40f)
        c.line(p, "500 ml", 60f, 290f, 48f)
        c.line(p, "BB: 2027-11-02", 60f, 460f, 50f, bold = true)
    }

    "vitamin_c" -> label { c, p ->
        c.line(p, "VITA-C", 60f, 130f, 62f, bold = true)
        c.line(p, "Vitamin C 1000mg", 60f, 205f, 40f)
        c.line(p, "60 tablets", 60f, 295f, 46f)
        c.line(p, "EXPIRY 2027-06-30", 60f, 465f, 46f, bold = true)
    }

    else -> label { c, p -> c.line(p, "UNKNOWN", 60f, 140f) }
}

/**
 * Renders a label at realistic photo dimensions with realistically SMALL date print.
 *
 * The plain [syntheticLabel] set draws the expiry date at ~5% of image height — huge
 * compared with a real pack, where a stamped date is closer to 1.5%. That flatters any
 * downsampling: text that large survives being shrunk to 512 px. This variant is sized
 * like an actual photo so the app's downsample math bites the way it does in the field.
 */
fun realisticLabel(id: String, longEdgePx: Int = 2160): Bitmap {
    val w = longEdgePx * 3 / 4
    val bmp = Bitmap.createBitmap(w, longEdgePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(Color.WHITE)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    val h = longEdgePx.toFloat()

    // Brand/product set large, as on a real front-of-pack; date small and low-contrast.
    val (brand, product, qty, dateLine) = when (id) {
        "milk"       -> listOf("DAIRY BEST", "Whole Milk", "1 L", "BEST BEFORE 2026-09-20")
        "ibuprofen"  -> listOf("IBUPROFEN", "200 mg tablets", "30 tablets", "EXP 2028-04-30")
        "olive_oil"  -> listOf("TERRA VERDE", "Extra Virgin Olive Oil", "500 ml", "BB 2027-11-02")
        "vitamin_c"  -> listOf("VITA-C", "Vitamin C 1000mg", "60 tablets", "EXPIRY 2027-06-30")
        else         -> listOf("SOLARA", "Sunscreen SPF 50", "200 ml", "MFG 2026-03-15 / 18 months")
    }
    c.line(p, brand,    w * 0.08f, h * 0.14f, h * 0.055f, bold = true)
    c.line(p, product,  w * 0.08f, h * 0.20f, h * 0.034f)
    c.line(p, qty,      w * 0.08f, h * 0.26f, h * 0.030f)
    // ~1.5% of image height — the realistic case
    p.color = Color.rgb(70, 70, 70)
    c.line(p, dateLine, w * 0.08f, h * 0.52f, h * 0.015f)
    return bmp
}

/**
 * Reproduces AgentSheet.processUri()'s downsample EXACTLY, including the detail that
 * BitmapFactory rounds inSampleSize down to a power of two — which is why the effective
 * resolution is not simply [targetLongEdge].
 */
fun downsampleLikeApp(src: Bitmap, targetLongEdge: Int): Bitmap {
    val raw = maxOf(src.width, src.height) / targetLongEdge
    var sample = 1
    while (sample * 2 <= raw) sample *= 2      // round DOWN to power of two
    if (sample <= 1) return src
    return Bitmap.createScaledBitmap(src, src.width / sample, src.height / sample, true)
}

fun bitmapToPngBytes(bmp: Bitmap): ByteArray =
    java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

// ── Vision cases ──────────────────────────────────────────────────────────────

data class VisionCase(
    val label: String,
    val imageIds: List<String>,
    val userInput: String,
    val expectedFunction: String?,
    val expectedArgs: Map<String, List<String>>,
    val probes: String = "",
)

/**
 * Uses the DURATION-style tool set but with an absolute [expiryDate], because a label
 * states a date rather than a duration — that is what the camera actually sees.
 */
fun visionSystemPrompt(todayIso: String): String = """
You are Shelfly, a shelf-tracking assistant. Today is $todayIso.

## Output schema
Respond with exactly one JSON object — no prose, no markdown.
{ "function": <tool_name | null>, "args": { <param: value, ...> }, "reason": <string | null> }

## Tools
addItem(name*, expiryDate*, category*, amount, notes)
(* = required)

## Field definitions
name : string
  Product name only — no brand slogan, no quantity, no unit.
  Prefer the product type over the brand: "Whole Milk", not "DAIRY BEST".

expiryDate : string
  ISO-8601 (YYYY-MM-DD). Read it from the label.
  Labels may print it as "BEST BEFORE", "BB", "EXP", or "EXPIRY".
  If the label gives a manufacture date plus a shelf life in months, add them
  (MFG 2026-03-15 + 18 months -> 2027-09-15).

amount : string  (optional)
  "<number> <unit>", unit one of: count / g / kg / mg / ml / L / tablets / pieces.
  Read the pack size from the label. "30 tablets" -> "30 tablets". "1 L" -> "1 L".
  Do NOT use the dosage strength (e.g. "200 mg" per tablet) as the amount.

category : enum
  "FOOD" | "MEDICINE" | "COSMETIC" | "OTHER"

## Label scanning
You are given one or more photographs of a product label.
Extract the fields above from what is printed on the label.
""".trimIndent()

fun visionCases(): List<VisionCase> = listOf(
    VisionCase(
        "Milk — plain BEST BEFORE", listOf("milk"), "Add this to my shelf.", "addItem",
        mapOf("name" to listOf("whole milk", "milk"), "expiryDate" to listOf("2026-09-20"),
              "amount" to listOf("1 L"), "category" to listOf("FOOD")),
    ),
    VisionCase(
        "Ibuprofen — EXP + dosage trap", listOf("ibuprofen"), "Add this to my shelf.", "addItem",
        mapOf("name" to listOf("ibuprofen"), "expiryDate" to listOf("2028-04-30"),
              "amount" to listOf("30 tablets"), "category" to listOf("MEDICINE")),
        "amount must be pack size (30 tablets), NOT dosage (200 mg)",
    ),
    VisionCase(
        "Sunscreen — MFG + shelf life", listOf("sunscreen_mfg"), "Add this to my shelf.", "addItem",
        mapOf("name" to listOf("sunscreen lotion", "sunscreen"), "expiryDate" to listOf("2027-09-15"),
              "amount" to listOf("200 ml"), "category" to listOf("COSMETIC")),
        "requires date arithmetic ON TOP of OCR",
    ),
    VisionCase(
        "Olive oil — abbreviated BB", listOf("olive_oil"), "Add this to my shelf.", "addItem",
        mapOf("name" to listOf("extra virgin olive oil", "olive oil"),
              "expiryDate" to listOf("2027-11-02"), "amount" to listOf("500 ml"),
              "category" to listOf("FOOD")),
    ),
    VisionCase(
        "Vitamin C — supplement category", listOf("vitamin_c"), "Add this to my shelf.", "addItem",
        mapOf("expiryDate" to listOf("2027-06-30"), "amount" to listOf("60 tablets"),
              "category" to listOf("MEDICINE")),
        "supplements classify as MEDICINE",
    ),
    VisionCase(
        "Two images — front + back", listOf("olive_oil", "milk"), "Add the olive oil.", "addItem",
        mapOf("expiryDate" to listOf("2027-11-02")),
        "multi-image: must not mix up the two labels",
    ),
)
