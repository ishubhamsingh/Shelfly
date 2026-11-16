package dev.ishubhamsingh.shelfly.agent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a product photo into tiles the vision encoder can actually read.
 *
 * WHY THIS EXISTS
 * Gemma-family vision encoders run at a FIXED input size (~768x768) and emit a fixed
 * token count per image — measured here as ~400 tokens regardless of the source
 * resolution. So sending a 4000x3000 photo does not give the model more detail; the
 * encoder downsamples it ~5x first. A dot-matrix `EXP: 19/10/26` stamp that is ~180x36
 * px on the original arrives as ~35x7 px. At that scale a 3 and a 1 are the same
 * smudge, which is exactly the failure we measured: the date and quantity came back as
 * plausible-but-wrong digits ("330 ml" read as "130 ml", "150 g", "0.15 ml") while
 * large brand text was always correct.
 *
 * Raising the capture resolution cannot help — the encoder discards it either way. The
 * only lever is to spend those fixed pixels on a smaller area of the page. A ~900 px
 * crop scaled into a 768 tile is roughly 4.4x more effective magnification for the same
 * token cost.
 *
 * ML Kit Text Recognition is used purely as a LOCATOR — we want its bounding boxes, not
 * its transcription. It is on-device, bundled, and needs no network.
 */
@Singleton
class LabelTiler @Inject constructor() {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Returns PNG-encoded tiles for one photo: a whole-frame context tile first (so the
     * model can still see what the product is), then up to [maxCrops] crops centred on
     * dense text. Falls back to the single context tile if nothing is found.
     */
    suspend fun tile(imagePath: String, maxCrops: Int = 2): List<ByteArray> {
        val full = runCatching { BitmapFactory.decodeFile(imagePath) }.getOrNull()
            ?: return emptyList()
        return try {
            val boxes = runCatching { locateText(full) }.getOrElse {
                Log.w(TAG, "text location failed; sending context tile only", it)
                emptyList()
            }
            val tiles = buildList {
                add(scaleToTile(full))
                clusterBoxes(boxes, full.width, full.height)
                    .take(maxCrops)
                    .forEach { add(cropToTile(full, it)) }
            }
            Log.i(TAG, "tiled ${File(imagePath).name}: ${tiles.size} tiles from ${full.width}x${full.height}")
            tiles.map { it.toPng() }
        } finally {
            // The decoded source can be tens of MB; the tiles are what we keep.
            full.recycle()
        }
    }

    private suspend fun locateText(bmp: Bitmap): List<Rect> =
        suspendCancellableCoroutine { cont ->
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                .addOnSuccessListener { result ->
                    cont.resume(result.textBlocks.mapNotNull { it.boundingBox })
                }
                .addOnFailureListener { cont.resume(emptyList()) }
        }

    /**
     * Groups nearby text boxes into crop regions.
     *
     * Text on a pack is clustered — a date stamp sits near a batch code, the net
     * quantity sits near the brand. Cropping each individual box would spend a whole
     * tile on three characters and lose the context that disambiguates them (the `EXP:`
     * prefix is what makes the following digits a date). Merging into a padded region
     * keeps neighbours together.
     *
     * Regions are ranked by text density — area covered by boxes relative to region
     * size — because a tight cluster of small glyphs is exactly what we cannot read at
     * full-frame scale, whereas one large brand word is already legible.
     */
    private fun clusterBoxes(boxes: List<Rect>, w: Int, h: Int): List<Rect> {
        if (boxes.isEmpty()) return emptyList()
        val regions = mutableListOf<Rect>()
        val covered = mutableListOf<Int>()

        boxes.sortedByDescending { it.width().toLong() * it.height() }.forEach { box ->
            val idx = regions.indexOfFirst { Rect.intersects(it, box.expanded(PAD, w, h)) }
            if (idx >= 0) {
                regions[idx] = Rect(regions[idx]).apply { union(box) }
                covered[idx] = covered[idx] + box.width() * box.height()
            } else {
                regions += Rect(box)
                covered += box.width() * box.height()
            }
        }

        return regions.indices
            .sortedByDescending { i ->
                val r = regions[i]
                val area = max(1, r.width() * r.height())
                covered[i].toFloat() / area
            }
            .map { regions[it].toCropWindow(w, h) }
    }

    /**
     * Grows a text region into a square crop window of at least [MIN_CROP] px.
     *
     * Square because the encoder input is square — a long thin strip would be letterboxed
     * and waste most of the tile. The minimum size stops a tiny region from being blown
     * up past the source resolution, which adds no information.
     */
    private fun Rect.toCropWindow(w: Int, h: Int): Rect {
        val cx = centerX()
        val cy = centerY()
        val side = min(min(w, h), max(MIN_CROP, max(width(), height()) + PAD * 2))
        val half = side / 2
        var l = cx - half
        var t = cy - half
        l = l.coerceIn(0, max(0, w - side))
        t = t.coerceIn(0, max(0, h - side))
        return Rect(l, t, l + side, t + side)
    }

    private fun Rect.expanded(by: Int, w: Int, h: Int) = Rect(
        (left - by).coerceAtLeast(0),
        (top - by).coerceAtLeast(0),
        (right + by).coerceAtMost(w),
        (bottom + by).coerceAtMost(h),
    )

    private fun cropToTile(src: Bitmap, r: Rect): Bitmap {
        val safe = Rect(
            r.left.coerceIn(0, src.width - 1),
            r.top.coerceIn(0, src.height - 1),
            r.right.coerceIn(1, src.width),
            r.bottom.coerceIn(1, src.height),
        )
        val crop = Bitmap.createBitmap(src, safe.left, safe.top, safe.width(), safe.height())
        val tile = scaleToTile(crop)
        if (crop !== tile) crop.recycle()
        return tile
    }

    /** Scales the longest edge to [TILE] px. Never upscales beyond the source. */
    private fun scaleToTile(src: Bitmap): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= TILE) return src
        val scale = TILE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun Bitmap.toPng(): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    private companion object {
        const val TAG = "LabelTiler"
        /** Encoder input edge. Sending more than this is discarded upstream. */
        const val TILE = 768
        /** Padding around a text box so neighbouring context survives the crop. */
        const val PAD = 48
        /** Smallest crop window; below this we are magnifying noise. */
        const val MIN_CROP = 900
    }
}
