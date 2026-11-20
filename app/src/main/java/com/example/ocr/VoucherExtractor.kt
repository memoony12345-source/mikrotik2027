package com.example.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.example.data.model.VoucherCandidate
import com.example.data.model.VoucherCodeMode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrLineBox(
    val text: String,
    val boundingBox: Rect?,
)

object VoucherExtractor {

    private val noiseWords = setOf(
        "MIKROTIK", "HOTSPOT", "VOUCHER", "PASSWORD", "USERNAME",
        "INTERNET", "NETWORK", "CONNECT", "LOGIN", "SIGNIN",
        "MINUTES", "HOURS", "UNLIMITED", "ACCESS", "ROUTER",
        "ROUTEROS", "WIFI", "WIRELESS", "CARD", "SERIAL",
        "EXPIRY", "EXPIRE", "VALID", "SUPPORT", "SERVICE",
    )

    /**
     * Extracts ranked voucher candidates (6 to 12 characters) from ML Kit Text result.
     * Supports pure digits (0-9) and alphanumeric English+digits (A-Za-z0-9).
     */
    fun extractFromMlKitText(
        visionText: Text,
        mode: VoucherCodeMode,
        roiRect: Rect? = null,
    ): List<VoucherCandidate> {
        val lines = mutableListOf<OcrLineBox>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                lines.add(OcrLineBox(line.text, line.boundingBox))
            }
        }
        return extractFromLines(lines, mode, roiRect)
    }

    fun extractFromLines(
        lines: List<OcrLineBox>,
        mode: VoucherCodeMode,
        roiRect: Rect? = null,
    ): List<VoucherCandidate> {
        val bestByCode = linkedMapOf<String, VoucherCandidate>()

        for (line in lines) {
            val normalizedLine = normalizeArabicDigits(line.text.trim())
            if (normalizedLine.isEmpty()) continue

            val inRoi = if (roiRect != null && line.boundingBox != null) {
                Rect.intersects(roiRect, line.boundingBox) ||
                    roiRect.contains(line.boundingBox.centerX(), line.boundingBox.centerY())
            } else {
                true
            }

            // 1. Direct token scanning (split on whitespace, slashes, colons)
            val rawTokens = normalizedLine.split(Regex("[\\s:/|]+"))
            for (token in rawTokens) {
                val cleaned = token.trim().replace(Regex("[^A-Za-z0-9-]"), "")
                val compact = cleaned.replace("-", "")
                evaluateAndAdd(compact, line.text, mode, inRoi, bestByCode)
            }

            // 2. Handle scratch cards that print codes in spaced groups like "4829 1038" or "AB12 34CD"
            val collapsedGroups = normalizedLine.replace(Regex("[\\s-]+"), "")
            if (collapsedGroups.length in 6..12 && collapsedGroups.all { it.isLetterOrDigit() }) {
                evaluateAndAdd(collapsedGroups, line.text, mode, inRoi, bestByCode, groupedBonus = -0.05f)
            }
        }

        return bestByCode.values.sortedWith(
            compareByDescending<VoucherCandidate> { it.inRoi }
                .thenByDescending { it.confidenceScore }
                .thenByDescending { it.code.length in 7..10 }
        )
    }

    private fun evaluateAndAdd(
        candidate: String,
        rawLineContext: String,
        mode: VoucherCodeMode,
        inRoi: Boolean,
        output: MutableMap<String, VoucherCandidate>,
        groupedBonus: Float = 0f,
    ) {
        if (candidate.length !in 6..12) return
        if (!candidate.all { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }) return

        val upper = candidate.uppercase()
        if (upper in noiseWords) return

        val isDigitsOnly = candidate.all { it.isDigit() }
        val hasDigit = candidate.any { it.isDigit() }
        val hasLetter = candidate.any { it.isLetter() }

        when (mode) {
            VoucherCodeMode.DIGITS_ONLY -> if (!isDigitsOnly) return
            VoucherCodeMode.ALPHANUMERIC -> if (!hasDigit) return
            VoucherCodeMode.AUTO -> {
                // MikroTik scratch codes are either pure digits (6-12) or letters+digits (6-12).
                // Reject pure-letter English words unless they aren't ordinary words, so labels don't misfire.
                if (!hasDigit) return
            }
        }

        // Ignore obvious phone numbers if line explicitly says Tel/Phone/واتس/جوال
        val lowerContext = rawLineContext.lowercase()
        val isPhoneContext = lowerContext.contains("tel") ||
            lowerContext.contains("phone") ||
            lowerContext.contains("هاتف") ||
            lowerContext.contains("جوال") ||
            lowerContext.contains("واتس") ||
            lowerContext.contains("تواصل")

        var score = 0.60f + groupedBonus
        if (inRoi) score += 0.25f
        if (isDigitsOnly) score += 0.08f
        if (hasDigit && hasLetter) score += 0.10f
        if (candidate.length in 7..10) score += 0.06f
        if (isPhoneContext) score -= 0.35f
        // Penalize repetitive single digits like "000000" or "111111"
        if (candidate.toSet().size <= 2) score -= 0.25f

        val finalScore = score.coerceIn(0.05f, 1.0f)
        val existing = output[candidate]
        if (existing == null || finalScore > existing.confidenceScore) {
            output[candidate] = VoucherCandidate(
                code = candidate,
                confidenceScore = finalScore,
                isDigitsOnly = isDigitsOnly,
                inRoi = inRoi,
            )
        }
    }

    fun normalizeArabicDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val mapped = when (ch) {
                '٠', '۰' -> '0'
                '١', '۱' -> '1'
                '٢', '۲' -> '2'
                '٣', '۳' -> '3'
                '٤', '۴' -> '4'
                '٥', '۵' -> '5'
                '٦', '۶' -> '6'
                '٧', '۷' -> '7'
                '٨', '۸' -> '8'
                '٩', '۹' -> '9'
                else -> ch
            }
            sb.append(mapped)
        }
        return sb.toString()
    }
}

object OcrHelper {
    suspend fun recognizeFromUri(
        context: Context,
        uri: Uri,
        mode: VoucherCodeMode,
    ): List<VoucherCandidate> {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val visionText = suspendCancellableCoroutine<Text> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result -> cont.resume(result) }
                    .addOnFailureListener { e -> cont.resumeWithException(e) }
            }
            VoucherExtractor.extractFromMlKitText(visionText, mode, roiRect = null)
        } finally {
            recognizer.close()
        }
    }
}
