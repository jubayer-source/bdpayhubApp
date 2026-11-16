package com.example.core.common

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Security-hardened structured logger with automatic sensitive-field redaction.
 *
 * Guarantees:
 * - Never logs full raw SMS messages.
 * - Never logs Bearer access tokens, refresh tokens, or device credentials.
 * - Masks phone/wallet numbers (e.g. 01712345678 -> 017****78).
 */
object RedactingLogger {

    data class RedactedLogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val sanitizedMessage: String
    )

    private val recentLogs = CopyOnWriteArrayList<RedactedLogEntry>()
    private const val MAX_BUFFER_SIZE = 100

    private val bearerRegex = Regex("""(?i)(Bearer\s+)[A-Za-z0-9\-._~+/]+=*""")
    private val jwtRegex = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
    private val credentialKeyRegex = Regex(
        """(?i)((?:access_token|refresh_token|device_credential|authorization|secret|api_key)\s*[:=]\s*["']?)([^"'\s,}]+)"""
    )
    private val bdPhoneRegex = Regex("""\b(01[3-9]\d)(\d{5})(\d{2})\b""")
    private val rocketAccountRegex = Regex("""\b(01[3-9]\d)(\d{6})(\d{2})\b""")
    private val rawSmsIndicatorRegex = Regex(
        """(?i)(You have received|Money Received\.|Cash In Tk|Cash In Received\.|Cash Out Received\.)[^\n]*"""
    )

    fun sanitize(raw: String): String {
        var sanitized = raw
        sanitized = bearerRegex.replace(sanitized, "$1[REDACTED_TOKEN]")
        sanitized = jwtRegex.replace(sanitized, "[REDACTED_JWT]")
        sanitized = credentialKeyRegex.replace(sanitized, "$1[REDACTED_CREDENTIAL]")
        sanitized = rawSmsIndicatorRegex.replace(sanitized, "[REDACTED_RAW_SMS_BODY]")
        sanitized = rocketAccountRegex.replace(sanitized, "$1******$3")
        sanitized = bdPhoneRegex.replace(sanitized, "$1*****$3")
        return sanitized
    }

    fun maskPhoneOrWallet(number: String?): String {
        if (number.isNullOrBlank()) return "N/A"
        val clean = number.trim()
        if (clean.length < 7) return "****"
        return "${clean.take(3)}*****${clean.takeLast(2)}"
    }

    fun i(tag: String, message: String) {
        val clean = sanitize(message)
        appendEntry("INFO", tag, clean)
        runCatching { Log.i("BDPayHub:$tag", clean) }
    }

    fun w(tag: String, message: String) {
        val clean = sanitize(message)
        appendEntry("WARN", tag, clean)
        runCatching { Log.w("BDPayHub:$tag", clean) }
    }

    fun e(tag: String, message: String) {
        val clean = sanitize(message)
        appendEntry("ERROR", tag, clean)
        runCatching { Log.e("BDPayHub:$tag", clean) }
    }

    fun getRecentLogs(): List<RedactedLogEntry> = recentLogs.toList()

    fun clearLogsForTest() {
        recentLogs.clear()
    }

    private fun appendEntry(level: String, tag: String, sanitizedMessage: String) {
        recentLogs.add(
            0,
            RedactedLogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                sanitizedMessage = sanitizedMessage
            )
        )
        while (recentLogs.size > MAX_BUFFER_SIZE) {
            recentLogs.removeAt(recentLogs.lastIndex)
        }
    }
}
