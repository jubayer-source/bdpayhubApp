package com.example.data.sms

import com.example.domain.model.ParsedPaymentEvent
import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentTransactionType

/**
 * Local SMS Parser for Rocket notifications (Sender ID: "16216").
 *
 * Supported formats:
 * A. Personal account — P2P received:
 *    You have received BDT [Amount] from [Sender 12-Digit Account]. Fee: BDT [Fee]. Balance: BDT [New Balance]. TrxID: [Transaction ID]. Time: [Date/Time].
 * B. Personal account — Agent Cash-In received:
 *    You have received Cash In of BDT [Amount] from Agent [Agent 12-Digit Account]. Fee: BDT [Fee]. Balance: BDT [New Balance]. TrxID: [Transaction ID]. Time: [Date/Time].
 * C. Agent account — Customer Cash-Out received:
 *    You have received Cash Out of BDT [Amount] from [Customer 12-Digit Account]. Comm: BDT [Commission]. Balance: BDT [New Balance]. TrxID: [Transaction ID]. Time: [Date/Time].
 */
class RocketSmsParser(
    override val parserVersion: String = SmsParserNormalizer.DEFAULT_PARSER_VERSION
) : PaymentSmsParser {

    override val providerCode: String = PaymentProvider.ROCKET.apiCode
    override val expectedSenderId: String = PaymentProvider.ROCKET.expectedSenderId

    private val p2pReceivedRegex = Regex(
        """^You have received BDT ([0-9,]+(?:\.\d{1,2})?) from (\d+)\. Fee: BDT ([0-9,]+(?:\.\d{1,2})?)\. Balance: BDT ([0-9,]+(?:\.\d{1,2})?)\. TrxID: ([A-Za-z0-9]+)\. Time: ([^.]+)\.?$"""
    )

    private val agentCashInRegex = Regex(
        """^You have received Cash In of BDT ([0-9,]+(?:\.\d{1,2})?) from Agent (\d+)\. Fee: BDT ([0-9,]+(?:\.\d{1,2})?)\. Balance: BDT ([0-9,]+(?:\.\d{1,2})?)\. TrxID: ([A-Za-z0-9]+)\. Time: ([^.]+)\.?$"""
    )

    private val customerCashOutRegex = Regex(
        """^You have received Cash Out of BDT ([0-9,]+(?:\.\d{1,2})?) from (\d+)\. Comm: BDT ([0-9,]+(?:\.\d{1,2})?)\. Balance: BDT ([0-9,]+(?:\.\d{1,2})?)\. TrxID: ([A-Za-z0-9]+)\. Time: ([^.]+)\.?$"""
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
            val senderAccount = SmsParserNormalizer.validate12DigitRocketAccount(match.groupValues[2]) ?: return null
            val fee = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[3]) ?: return null
            val balance = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[4]) ?: return null
            val trxId = SmsParserNormalizer.validateTransactionId(match.groupValues[5]) ?: return null
            val trxTime = SmsParserNormalizer.parseAndFormatTimestamp(match.groupValues[6]) ?: return null

            return ParsedPaymentEvent(
                provider = providerCode,
                transactionType = PaymentTransactionType.SEND_MONEY_RECEIVED.apiValue,
                amount = amount,
                currency = "BDT",
                senderNumber = senderAccount,
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

        agentCashInRegex.matchEntire(normalized)?.let { match ->
            val amount = SmsParserNormalizer.validatePositiveAmount(match.groupValues[1]) ?: return null
            val agentAccount = SmsParserNormalizer.validate12DigitRocketAccount(match.groupValues[2]) ?: return null
            val fee = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[3]) ?: return null
            val balance = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[4]) ?: return null
            val trxId = SmsParserNormalizer.validateTransactionId(match.groupValues[5]) ?: return null
            val trxTime = SmsParserNormalizer.parseAndFormatTimestamp(match.groupValues[6]) ?: return null

            return ParsedPaymentEvent(
                provider = providerCode,
                transactionType = PaymentTransactionType.AGENT_CASH_IN_RECEIVED.apiValue,
                amount = amount,
                currency = "BDT",
                senderNumber = agentAccount, // 12-digit Agent account
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
            val customerAccount = SmsParserNormalizer.validate12DigitRocketAccount(match.groupValues[2]) ?: return null
            val commission = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[3]) ?: return null
            val balance = SmsParserNormalizer.validateNonNegativeAmount(match.groupValues[4]) ?: return null
            val trxId = SmsParserNormalizer.validateTransactionId(match.groupValues[5]) ?: return null
            val trxTime = SmsParserNormalizer.parseAndFormatTimestamp(match.groupValues[6]) ?: return null

            return ParsedPaymentEvent(
                provider = providerCode,
                transactionType = PaymentTransactionType.CUSTOMER_CASH_OUT_RECEIVED.apiValue,
                amount = amount,
                currency = "BDT",
                senderNumber = customerAccount,
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
