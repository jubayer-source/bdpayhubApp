package com.example.data.sms

import com.example.domain.model.ParsedPaymentEvent

/**
 * Contract for provider-specific local SMS parsers.
 * All parsing occurs strictly on-device with deterministic validation.
 */
interface PaymentSmsParser {
    val providerCode: String
    val expectedSenderId: String
    val parserVersion: String

    fun canParse(senderId: String, message: String): Boolean

    fun parse(
        senderId: String,
        message: String,
        receivedAt: Long
    ): ParsedPaymentEvent?
}
