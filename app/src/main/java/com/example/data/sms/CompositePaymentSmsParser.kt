package com.example.data.sms

import com.example.domain.model.ParsedPaymentEvent
import com.example.domain.model.PaymentProvider

sealed class SmsParseOutcome {
    data class Parsed(val event: ParsedPaymentEvent) : SmsParseOutcome()
    data class KnownSenderUnrecognizedFormat(
        val senderId: String,
        val diagnosticReason: String,
        val parserVersion: String
    ) : SmsParseOutcome()
    data object UnrelatedMessageIgnored : SmsParseOutcome()
}

/**
 * Orchestrates provider-specific parsers (bKash, Nagad, Rocket, upay).
 *
 * Rules:
 * - Unrelated SMS messages from personal/other sender IDs are immediately ignored (`UnrelatedMessageIgnored`).
 * - Messages from a recognized provider sender ID (`bKash`, `NAGAD`, `16216`, `upay`) that fail
 *   strict validation or match an unsupported pattern return `KnownSenderUnrecognizedFormat`
 *   with diagnostic metadata only (never storing or uploading raw SMS).
 */
class CompositePaymentSmsParser(
    private val parsers: List<PaymentSmsParser> = listOf(
        BkashSmsParser(),
        NagadSmsParser(),
        RocketSmsParser(),
        UpaySmsParser()
    )
) : PaymentSmsParser {

    override val providerCode: String = "composite"
    override val expectedSenderId: String = "*"
    override val parserVersion: String = SmsParserNormalizer.DEFAULT_PARSER_VERSION

    override fun canParse(senderId: String, message: String): Boolean {
        return parsers.any { it.canParse(senderId, message) }
    }

    override fun parse(
        senderId: String,
        message: String,
        receivedAt: Long
    ): ParsedPaymentEvent? {
        for (parser in parsers) {
            val event = parser.parse(senderId, message, receivedAt)
            if (event != null) return event
        }
        return null
    }

    fun inspectAndParse(
        senderId: String,
        message: String,
        receivedAt: Long = System.currentTimeMillis()
    ): SmsParseOutcome {
        val cleanSender = senderId.trim()
        val matchingProvider = PaymentProvider.fromSenderId(cleanSender)
            ?: return SmsParseOutcome.UnrelatedMessageIgnored

        val parsed = parse(cleanSender, message, receivedAt)
        if (parsed != null) {
            return SmsParseOutcome.Parsed(parsed)
        }

        val reason = diagnoseFailureReason(matchingProvider, message)
        return SmsParseOutcome.KnownSenderUnrecognizedFormat(
            senderId = cleanSender,
            diagnosticReason = reason,
            parserVersion = parserVersion
        )
    }

    private fun diagnoseFailureReason(provider: PaymentProvider, message: String): String {
        val normalized = SmsParserNormalizer.normalizeMessage(message)
        if (provider == PaymentProvider.UPAY && normalized.contains("Cash Out", ignoreCase = true)) {
            return "Unsupported upay Cash-Out pattern (only Personal P2P and Agent Cash-In are supported)"
        }
        if (!normalized.contains("TrxID", ignoreCase = true) && !normalized.contains("TxnID", ignoreCase = true)) {
            return "Non-transactional or promotional notification from ${provider.displayName} (no Transaction ID)"
        }
        return "Message from ${provider.displayName} failed strict structural, account length, amount, or timestamp validation"
    }
}
