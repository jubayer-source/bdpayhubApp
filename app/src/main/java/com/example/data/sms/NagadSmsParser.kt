package com.example.data.sms

import com.example.domain.model.ParsedPaymentEvent
import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentTransactionType

/**
 * Local SMS Parser for Nagad notifications (Sender ID: "NAGAD").
 *
 * Supported formats:
 * A. Personal account — P2P received:
 *    Money Received. Amount: Tk [Amount] Sender: [Sender 11-Digit Number] Ref: [Reference] TxnID: [Transaction ID] Balance: Tk [New Balance] [DD/MM/YYYY HH:MM]
 * B. Personal account — Agent Cash-In received:
 *    Cash In Received. Amount: Tk [Amount] Uddokta: [Agent 11-Digit Number] TxnID: [Transaction ID] Balance: Tk [New Balance] [DD/MM/YYYY HH:MM]
 * C. Agent account — Customer Cash-Out received:
 *    Cash Out Received. Amount: Tk [Amount] Customer: [Customer 11-Digit Number] Comm: Tk [Commission] TxnID: [Transaction ID] Balance: Tk [New Balance] [DD/MM/YYYY HH:MM]
 */
class NagadSmsParser(
    override val parserVersion: String = SmsParserNormalizer.DEFAULT_PARSER_VERSION
) : PaymentSmsParser {

    override val providerCode: String = PaymentProvider.NAGAD.apiCode
    override val expectedSenderId: String = PaymentProvider.NAGAD.expectedSenderId

    private val p2pReceivedRegex = Regex(
        """^Money Received\. Amount: Tk ([0-9,]+(?:\.\d{1,2})?) Sender: (\d+)(?: Ref: ([^\s]+))? TxnID: ([A-Za-z0-9]+) Balance: Tk ([0-9,]+(?:\.\d{1,2})?) (\d{2}/\d{2}/\d{4} \d{2}:\d{2})\.?$"""
    )

    private val agentCashInRegex = Regex(
        """^Cash In Received\. Amount: Tk ([0-9,]+(?:\.\d{1,2})?) Uddokta: (\d+) TxnID: ([A-Za-z0-9]+) Balance: Tk ([0-9,]+(?:\.\d{1,2})?) (\d{2}/\d{2}/\d{4} \d{2}:\d{2})\.?$"""
    )

    private val customerCashOutRegex = Regex(
        """^Cash Out Received\. Amount: Tk ([0-9,]+(?:\.\d{1,2})?) Customer: (\d+) Comm: Tk ([0-9,]+(?:\.\d{1,2})?) TxnID: ([A-Za-z0-9]+) Balance: Tk ([0-9,]+(?:\.\d{1,2})?) (\d{2}/\d{2}/\d{4} \d{2}:\d{2})\.?$"""
    )

    override fun canParse(senderId: String, message: String): Boolean {
        if (senderId.trim() != expectedSenderId) return false
        val normalized = SmsParserNormalizer.normalizeMessage(message)
        return p2pReceivedRegex.matches(normalized) ||
            agentCashInRegex.matches(normalized) ||
            customerCashOutRegex.matches(normalized)
    }

    override fun parse(
        senderId: String,
        message: String,
        receivedAt: Long
    ): ParsedPaymentEvent? {
        if (senderId.trim() != expectedSenderId) return null
        val normalized = SmsParserNormalizer.normalizeMessage(message)

        p2pReceivedRegex.matchEntire(normalized)?.let { match ->
            val amount = SmsParserNormalizer.validatePositiveAmount(match.groupValues[1]) ?: return null
            val senderNumber = SmsParserNormalizer.validate11DigitBdNumber(match.groupValues[2]) ?: return null
            val reference = SmsParserNormalizer.cleanReference(match.groupValues[3])
            val trxId = SmsParserNormalizer.validateTransactionId(match.groupValues[4]) ?: return null
            val balance = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[5]) ?: return null
            val trxTime = SmsParserNormalizer.parseAndFormatTimestamp(match.groupValues[6]) ?: return null

            return ParsedPaymentEvent(
                provider = providerCode,
                transactionType = PaymentTransactionType.SEND_MONEY_RECEIVED.apiValue,
                amount = amount,
                currency = "BDT",
                senderNumber = senderNumber,
                receiverNumber = null,
                transactionId = trxId,
                transactionTime = trxTime,
                reference = reference,
                fee = null,
                commission = null,
                balance = balance,
                parserVersion = parserVersion,
                receivedAt = receivedAt
            )
        }

        agentCashInRegex.matchEntire(normalized)?.let { match ->
            val amount = SmsParserNormalizer.validatePositiveAmount(match.groupValues[1]) ?: return null
            val uddoktaNumber = SmsParserNormalizer.validate11DigitBdNumber(match.groupValues[2]) ?: return null
            val trxId = SmsParserNormalizer.validateTransactionId(match.groupValues[3]) ?: return null
            val balance = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[4]) ?: return null
            val trxTime = SmsParserNormalizer.parseAndFormatTimestamp(match.groupValues[5]) ?: return null

            return ParsedPaymentEvent(
                provider = providerCode,
                transactionType = PaymentTransactionType.AGENT_CASH_IN_RECEIVED.apiValue,
                amount = amount,
                currency = "BDT",
                senderNumber = uddoktaNumber, // Uddokta / Agent number
                receiverNumber = null,
                transactionId = trxId,
                transactionTime = trxTime,
                reference = null,
                fee = null,
                commission = null,
                balance = balance,
                parserVersion = parserVersion,
                receivedAt = receivedAt
            )
        }

        customerCashOutRegex.matchEntire(normalized)?.let { match ->
            val amount = SmsParserNormalizer.validatePositiveAmount(match.groupValues[1]) ?: return null
            val customerNumber = SmsParserNormalizer.validate11DigitBdNumber(match.groupValues[2]) ?: return null
            val commission = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[3]) ?: return null
            val trxId = SmsParserNormalizer.validateTransactionId(match.groupValues[4]) ?: return null
            val balance = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[5]) ?: return null
            val trxTime = SmsParserNormalizer.parseAndFormatTimestamp(match.groupValues[6]) ?: return null

            return ParsedPaymentEvent(
                provider = providerCode,
                transactionType = PaymentTransactionType.CUSTOMER_CASH_OUT_RECEIVED.apiValue,
                amount = amount,
                currency = "BDT",
                senderNumber = customerNumber,
                receiverNumber = null,
                transactionId = trxId,
                transactionTime = trxTime,
                reference = null,
                fee = null,
                commission = commission,
                balance = balance,
                parserVersion = parserVersion,
                receivedAt = receivedAt
            )
        }

        return null
    }
}
