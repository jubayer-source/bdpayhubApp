package com.example.data.sms

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Deterministic normalization and validation helpers for the local SMS parser engine.
 * Never uses floating-point arithmetic for monetary values.
 */
object SmsParserNormalizer {

    const val DEFAULT_PARSER_VERSION = "1.0.0"

    private val multipleWhitespaceRegex = Regex("""\s+""")
    private val valid11DigitPhoneRegex = Regex("""^01[3-9]\d{8}$""")
    private val valid12DigitRocketAccountRegex = Regex("""^01[3-9]\d{9}$""")
    private val validTransactionIdRegex = Regex("""^[A-Z0-9]{6,20}$""")

    private val bengaliToEnglishMap = mapOf(
        '০' to '0',
        '১' to '1',
        '২' to '2',
        '৩' to '3',
        '৪' to '4',
        '৫' to '5',
        '৬' to '6',
        '৭' to '7',
        '৮' to '8',
        '৯' to '9'
    )

    /**
     * Collapses consecutive whitespace into a single space and converts supported
     * Bengali numerals (০-৯) to ASCII digits (0-9).
     */
    fun normalizeMessage(raw: String): String {
        val digitNormalized = buildString(raw.length) {
            for (ch in raw) {
                append(bengaliToEnglishMap[ch] ?: ch)
            }
        }
        return multipleWhitespaceRegex.replace(digitNormalized.trim(), " ")
    }

    /**
     * Validates a positive monetary amount using BigDecimal and returns a normalized
     * two-decimal plain string (e.g., "500.00"). Rejects zero, negative, or non-numeric strings.
     */
    fun validatePositiveAmount(rawAmount: String?): String? {
        if (rawAmount.isNullOrBlank()) return null
        val cleaned = rawAmount.trim().replace(",", "")
        return try {
            val bd = BigDecimal(cleaned)
            if (bd <= BigDecimal.ZERO) return null
            bd.setScale(2, RoundingMode.HALF_UP).toPlainString()
        } catch (_: NumberFormatException) {
            null
        }
    }

    /**
     * Validates a non-negative monetary amount (such as Fee 0.00, Commission, or Balance)
     * using BigDecimal and returns a normalized two-decimal plain string.
     */
    fun validateNonNegativeAmount(rawAmount: String?): String? {
        if (rawAmount.isNullOrBlank()) return null
        val cleaned = rawAmount.trim().replace(",", "")
        return try {
            val bd = BigDecimal(cleaned)
            if (bd < BigDecimal.ZERO) return null
            bd.setScale(2, RoundingMode.HALF_UP).toPlainString()
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun validate11DigitBdNumber(rawNumber: String?): String? {
        if (rawNumber.isNullOrBlank()) return null
        val cleaned = rawNumber.trim()
        return if (valid11DigitPhoneRegex.matches(cleaned)) cleaned else null
    }

    fun validate12DigitRocketAccount(rawAccount: String?): String? {
        if (rawAccount.isNullOrBlank()) return null
        val cleaned = rawAccount.trim()
        return if (valid12DigitRocketAccountRegex.matches(cleaned)) cleaned else null
    }

    /**
     * Strict Transaction ID validation:
     * - Must be 6 to 20 uppercase alphanumeric characters
     * - Must contain at least one digit (rejects plain words)
     */
    fun validateTransactionId(rawTrxId: String?): String? {
        if (rawTrxId.isNullOrBlank()) return null
        val cleaned = rawTrxId.trim().removeSuffix(".")
        if (!validTransactionIdRegex.matches(cleaned)) return null
        if (!cleaned.any { it.isDigit() }) return null
        return cleaned
    }

    /**
     * Strictly parses Bangladesh timestamp strings into ISO-8601 with +06:00 offset:
     * Supports:
     * - "dd/MM/yyyy HH:mm" (e.g. "17/11/2026 00:30")
     * - "dd-MMM-yy hh:mm:ss a" (e.g. "17-Nov-26 12:30:00 am")
     * - "dd/MM/yyyy HH:mm:ss"
     */
    fun parseAndFormatTimestamp(rawTimestamp: String?): String? {
        if (rawTimestamp.isNullOrBlank()) return null
        val cleaned = rawTimestamp.trim().removeSuffix(".")
        val patterns = listOf(
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy HH:mm:ss",
            "dd-MMM-yy hh:mm:ss a",
            "dd-MMM-yyyy hh:mm:ss a",
            "dd-MM-yyyy HH:mm"
        )
        val bstTimeZone = TimeZone.getTimeZone("GMT+06:00")
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    timeZone = bstTimeZone
                }
                val parsedDate = parser.parse(cleaned)
                if (parsedDate != null) {
                    val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+06:00'", Locale.US).apply {
                        timeZone = bstTimeZone
                    }
                    return isoFormatter.format(parsedDate)
                }
            } catch (_: Exception) {
                // Try next explicit pattern
            }
        }
        return null
    }

    fun cleanReference(rawReference: String?): String? {
        if (rawReference.isNullOrBlank()) return null
        val cleaned = rawReference.trim().removeSuffix(".")
        return if (cleaned.isEmpty() || cleaned.equals("N/A", ignoreCase = true)) null else cleaned
    }
}
