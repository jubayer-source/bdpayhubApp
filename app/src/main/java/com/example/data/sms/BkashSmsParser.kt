package com.example.data.sms

import com.example.domain.model.ParsedPaymentEvent
import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentTransactionType

/**
 * Local SMS Parser for bKash notifications (Sender ID: "bKash").
 *
 * Supported formats:
 * A. Personal account — Send Money received:
 *    You have received Tk [Amount] from [Sender 11-Digit Number]. Ref [Reference]. Fee Tk 0.00. Balance Tk [New Balance]. TrxID [Transaction ID] at [DD/MM/YYYY HH:MM]
 * B. Personal account — Agent Cash-In received:
 *    Cash In Tk [Amount] from [Agent 11-Digit Number] successful. Fee Tk 0.00. Balance Tk [New Balance]. TrxID [Transaction ID] at [DD/MM/YYYY HH:MM]. Download App: bka.sh
 * C. Agent account — Customer Cash-Out received:
 *    You have received Cash Out Tk [Amount] from [Customer 11-Digit Number]. Comm Tk [Commission]. Balance Tk [New Balance]. TrxID [Transaction ID] at [DD/MM/YYYY HH:MM].
 */
class BkashSmsParser(
    override val parserVersion: String = SmsParserNormalizer.DEFAULT_PARSER_VERSION
) : PaymentSmsParser {

    override val providerCode: String = PaymentProvider.BKASH.apiCode
    override val expectedSenderId: String = PaymentProvider.BKASH.expectedSenderId

    private val sendMoneyRegex = Regex(
        """^You have received Tk ([0-9,]+(?:\.\d{1,2})?) from (\d+)\.(?: Ref ([^.]+)\.)? Fee Tk ([0-9,]+(?:\.\d{1,2})?)\. Balance Tk ([0-9,]+(?:\.\d{1,2})?)\. TrxID ([A-Za-z0-9]+) at (\d{2}/\d{2}/\d{4} \d{2}:\d{2})\.?$"""
    )

    private val agentCashInRegex = Regex(
        """^Cash In Tk ([0-9,]+(?:\.\d{1,2})?) from (\d+) successful\. Fee Tk ([0-9,]+(?:\.\d{1,2})?)\. Balance Tk ([0-9,]+(?:\.\d{1,2})?)\. TrxID ([A-Za-z0-9]+) at (\d{2}/\d{2}/\d{4} \d{2}:\d{2})\.?(?: Download App: bka\.sh\.?)?$"""
    )

    private val customerCashOutRegex = Regex(
        """^You have received Cash Out Tk ([0-9,]+(?:\.\d{1,2})?) from (\d+)\. Comm Tk ([0-9,]+(?:\.\d{1,2})?)\. Balance Tk ([0-9,]+(?:\.\d{1,2})?)\. TrxID ([A-Za-z0-9]+) at (\d{2}/\d{2}/\d{4} \d{2}:\d{2})\.?$"""
    )

    override fun canParse(senderId: String, message: String): Boolean {
        if (senderId.trim() != expectedSenderId) return false
        val normalized = SmsParserNormalizer.normalizeMessage(message)
        return sendMoneyRegex.matches(normalized) ||
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

        sendMoneyRegex.matchEntire(normalized)?.let { match ->
            val amount = SmsParserNormalizer.validatePositiveAmount(match.groupValues[1]) ?: return null
            val senderNumber = SmsParserNormalizer.validate11DigitBdNumber(match.groupValues[2]) ?: return null
            val reference = SmsParserNormalizer.cleanReference(match.groupValues[3])
            val fee = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[4]) ?: return null
            val balance = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[5]) ?: return null
            val trxId = SmsParserNormalizer.validateTransactionId(match.groupValues[6]) ?: return null
            val trxTime = SmsParserNormalizer.parseAndFormatTimestamp(match.groupValues[7]) ?: return null

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
                fee = fee,
                commission = null,
                balance = balance,
                parserVersion = parserVersion,
                receivedAt = receivedAt
            )
        }

        agentCashInRegex.matchEntire(normalized)?.let { match ->
            val amount = SmsParserNormalizer.validatePositiveAmount(match.groupValues[1]) ?: return null
            val agentNumber = SmsParserNormalizer.validate11DigitBdNumber(match.groupValues[2]) ?: return null
            val fee = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[3]) ?: return null
            val balance = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[4]) ?: return null
            val trxId = SmsParserNormalizer.validateTransactionId(match.groupValues[5]) ?: return null
            val trxTime = SmsParserNormalizer.parseAndFormatTimestamp(match.groupValues[6]) ?: return null

            return ParsedPaymentEvent(
                provider = providerCode,
                transactionType = PaymentTransactionType.AGENT_CASH_IN_RECEIVED.apiValue,
                amount = amount,
                currency = "BDT",
                senderNumber = agentNumber, // Agent number, not customer number
                receiverNumber = null,
                transactionId = trxId,
                transactionTime = trxTime,
                reference = null,
                fee = fee,
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
            val balance = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[4]) ?: return null
            val trxId = SmsParserNormalizer.validateTransactionId(match.groupValues[5]) ?: return null
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
