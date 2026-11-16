package com.example

import com.example.data.sms.BkashSmsParser
import com.example.data.sms.CompositePaymentSmsParser
import com.example.data.sms.NagadSmsParser
import com.example.data.sms.RocketSmsParser
import com.example.data.sms.SmsParseOutcome
import com.example.data.sms.UpaySmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive unit test suite for all 11 supported provider SMS patterns
 * and edge/negative validation cases defined in Sections 7, 8, and 20.
 */
class PaymentSmsParserTest {

    private val bkashParser = BkashSmsParser()
    private val nagadParser = NagadSmsParser()
    private val rocketParser = RocketSmsParser()
    private val upayParser = UpaySmsParser()
    private val compositeParser = CompositePaymentSmsParser()
    private val sampleReceivedAt = 1763339400000L

    // =========================================================================
    // 1. bKash Tests (7.1 A, B, C)
    // =========================================================================

    @Test
    fun `parse bKash P2P Send Money received`() {
        val sms = "You have received Tk 1,500.00 from 01712345678. Ref COURSE2026. Fee Tk 0.00. Balance Tk 25,500.00. TrxID BK9876543A at 17/11/2026 00:30"
        assertTrue(bkashParser.canParse("bKash", sms))
        val event = bkashParser.parse("bKash", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("bkash", event!!.provider)
        assertEquals("SEND_MONEY_RECEIVED", event.transactionType)
        assertEquals("1500.00", event.amount)
        assertEquals("BDT", event.currency)
        assertEquals("01712345678", event.senderNumber)
        assertNull(event.receiverNumber)
        assertEquals("COURSE2026", event.reference)
        assertEquals("0.00", event.fee)
        assertNull(event.commission)
        assertEquals("25500.00", event.balance)
        assertEquals("BK9876543A", event.transactionId)
        assertEquals("2026-11-17T00:30:00+06:00", event.transactionTime)
        assertEquals("1.0.0", event.parserVersion)
    }

    @Test
    fun `parse bKash Agent Cash-In received preserves agent number`() {
        val sms = "Cash In Tk 2,500.00 from 01819876543 successful. Fee Tk 0.00. Balance Tk 28,000.00. TrxID BK1122334C at 17/11/2026 10:22. Download App: bka.sh"
        val event = bkashParser.parse("bKash", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("bkash", event!!.provider)
        assertEquals("AGENT_CASH_IN_RECEIVED", event.transactionType)
        assertEquals("2500.00", event.amount)
        assertEquals("01819876543", event.senderNumber)
        assertEquals("0.00", event.fee)
        assertEquals("28000.00", event.balance)
        assertEquals("BK1122334C", event.transactionId)
        assertEquals("2026-11-17T10:22:00+06:00", event.transactionTime)
    }

    @Test
    fun `parse bKash Agent Customer Cash-Out received`() {
        val sms = "You have received Cash Out Tk 3,000.00 from 01911223344. Comm Tk 12.00. Balance Tk 45,200.00. TrxID BK5531908D at 17/11/2026 10:30."
        val event = bkashParser.parse("bKash", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("bkash", event!!.provider)
        assertEquals("CUSTOMER_CASH_OUT_RECEIVED", event.transactionType)
        assertEquals("3000.00", event.amount)
        assertEquals("01911223344", event.senderNumber)
        assertEquals("12.00", event.commission)
        assertNull(event.fee)
        assertEquals("45200.00", event.balance)
        assertEquals("BK5531908D", event.transactionId)
    }

    // =========================================================================
    // 2. Nagad Tests (7.2 A, B, C)
    // =========================================================================

    @Test
    fun `parse Nagad P2P Money Received`() {
        val sms = "Money Received. Amount: Tk 750.50 Sender: 01611445566 Ref: ORD8821 TxnID: NG71829304 Balance: Tk 12,300.50 17/11/2026 11:05"
        val event = nagadParser.parse("NAGAD", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("nagad", event!!.provider)
        assertEquals("SEND_MONEY_RECEIVED", event.transactionType)
        assertEquals("750.50", event.amount)
        assertEquals("01611445566", event.senderNumber)
        assertEquals("ORD8821", event.reference)
        assertEquals("NG71829304", event.transactionId)
        assertEquals("12300.50", event.balance)
        assertEquals("2026-11-17T11:05:00+06:00", event.transactionTime)
    }

    @Test
    fun `parse Nagad Agent Cash-In received extracts Uddokta number`() {
        val sms = "Cash In Received. Amount: Tk 1,500.00 Uddokta: 01522334455 TxnID: NG8291045B Balance: Tk 13,800.00 17/11/2026 11:12"
        val event = nagadParser.parse("NAGAD", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("nagad", event!!.provider)
        assertEquals("AGENT_CASH_IN_RECEIVED", event.transactionType)
        assertEquals("1500.00", event.amount)
        assertEquals("01522334455", event.senderNumber)
        assertEquals("NG8291045B", event.transactionId)
        assertEquals("13800.00", event.balance)
    }

    @Test
    fun `parse Nagad Agent Customer Cash-Out received`() {
        val sms = "Cash Out Received. Amount: Tk 4,200.00 Customer: 01755667788 Comm: Tk 16.80 TxnID: NG9938471C Balance: Tk 38,900.00 17/11/2026 11:20"
        val event = nagadParser.parse("NAGAD", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("nagad", event!!.provider)
        assertEquals("CUSTOMER_CASH_OUT_RECEIVED", event.transactionType)
        assertEquals("4200.00", event.amount)
        assertEquals("01755667788", event.senderNumber)
        assertEquals("16.80", event.commission)
        assertEquals("NG9938471C", event.transactionId)
    }

    // =========================================================================
    // 3. Rocket Tests (7.3 A, B, C)
    // =========================================================================

    @Test
    fun `parse Rocket P2P received with 12-digit account`() {
        val sms = "You have received BDT 900.00 from 017112233445. Fee: BDT 0.00. Balance: BDT 9,400.00. TrxID: RK88392014. Time: 17/11/2026 11:40."
        val event = rocketParser.parse("16216", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("rocket", event!!.provider)
        assertEquals("SEND_MONEY_RECEIVED", event.transactionType)
        assertEquals("900.00", event.amount)
        assertEquals("017112233445", event.senderNumber)
        assertEquals("0.00", event.fee)
        assertEquals("9400.00", event.balance)
        assertEquals("RK88392014", event.transactionId)
    }

    @Test
    fun `parse Rocket Agent Cash-In received`() {
        val sms = "You have received Cash In of BDT 2,000.00 from Agent 018223344556. Fee: BDT 0.00. Balance: BDT 11,400.00. TrxID: RK77482910. Time: 17/11/2026 11:45."
        val event = rocketParser.parse("16216", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("rocket", event!!.provider)
        assertEquals("AGENT_CASH_IN_RECEIVED", event.transactionType)
        assertEquals("2000.00", event.amount)
        assertEquals("018223344556", event.senderNumber)
        assertEquals("RK77482910", event.transactionId)
    }

    @Test
    fun `parse Rocket Agent Customer Cash-Out received`() {
        val sms = "You have received Cash Out of BDT 1,800.00 from 019334455667. Comm: BDT 7.20. Balance: BDT 26,500.00. TrxID: RK66574839. Time: 17/11/2026 11:50."
        val event = rocketParser.parse("16216", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("rocket", event!!.provider)
        assertEquals("CUSTOMER_CASH_OUT_RECEIVED", event.transactionType)
        assertEquals("1800.00", event.amount)
        assertEquals("019334455667", event.senderNumber)
        assertEquals("7.20", event.commission)
        assertEquals("RK66574839", event.transactionId)
    }

    // =========================================================================
    // 4. upay Tests (7.4 A, B)
    // =========================================================================

    @Test
    fun `parse upay P2P received payment`() {
        val sms = "You have received Tk 600.00 from 01677889900. Fee Tk 0.00. Balance Tk 5,600.00. TrxID UP4492018A at 17/11/2026 12:05."
        val event = upayParser.parse("upay", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("upay", event!!.provider)
        assertEquals("SEND_MONEY_RECEIVED", event.transactionType)
        assertEquals("600.00", event.amount)
        assertEquals("01677889900", event.senderNumber)
        assertEquals("UP4492018A", event.transactionId)
    }

    @Test
    fun `parse upay Agent Cash-In received`() {
        val sms = "Cash In Tk 1,100.00 from 01344556677 successful. Fee Tk 0.00. Balance Tk 6,700.00. TrxID UP5583920B at 17/11/2026 12:15."
        val event = upayParser.parse("upay", sms, sampleReceivedAt)
        assertNotNull(event)
        assertEquals("upay", event!!.provider)
        assertEquals("AGENT_CASH_IN_RECEIVED", event.transactionType)
        assertEquals("1100.00", event.amount)
        assertEquals("01344556677", event.senderNumber)
        assertEquals("UP5583920B", event.transactionId)
    }

    @Test
    fun `reject unsupported upay Cash-Out pattern`() {
        val unsupportedUpayCashOut = "You have received Cash Out Tk 1,000.00 from 01712345678. Comm Tk 4.00. Balance Tk 5,000.00. TrxID UP99887766 at 17/11/2026 12:30."
        assertFalse(upayParser.canParse("upay", unsupportedUpayCashOut))
        assertNull(upayParser.parse("upay", unsupportedUpayCashOut, sampleReceivedAt))

        val outcome = compositeParser.inspectAndParse("upay", unsupportedUpayCashOut, sampleReceivedAt)
        assertTrue(outcome is SmsParseOutcome.KnownSenderUnrecognizedFormat)
    }

    // =========================================================================
    // 5. Validation & Negative Edge Case Tests
    // =========================================================================

    @Test
    fun `reject incorrect sender IDs even if message structure matches`() {
        val validBody = "You have received Tk 500.00 from 01712345678. Ref TEST. Fee Tk 0.00. Balance Tk 2,500.00. TrxID TRX12345678 at 17/11/2026 00:30"
        assertNull(bkashParser.parse("+8801711111111", validBody, sampleReceivedAt))
        assertNull(bkashParser.parse("BKASH", validBody, sampleReceivedAt)) // Sender ID is case-sensitive "bKash"
        val outcome = compositeParser.inspectAndParse("+8801711111111", validBody, sampleReceivedAt)
        assertEquals(SmsParseOutcome.UnrelatedMessageIgnored, outcome)
    }

    @Test
    fun `reject missing or malformed transaction IDs`() {
        // Too short (< 6 chars) or purely alphabetic without digits
        val shortTrx = "You have received Tk 500.00 from 01712345678. Ref TEST. Fee Tk 0.00. Balance Tk 2,500.00. TrxID AB12 at 17/11/2026 00:30"
        val alphaOnlyTrx = "You have received Tk 500.00 from 01712345678. Ref TEST. Fee Tk 0.00. Balance Tk 2,500.00. TrxID ABCDEFGH at 17/11/2026 00:30"
        assertNull(bkashParser.parse("bKash", shortTrx, sampleReceivedAt))
        assertNull(bkashParser.parse("bKash", alphaOnlyTrx, sampleReceivedAt))
    }

    @Test
    fun `reject zero or invalid amounts`() {
        val zeroAmount = "You have received Tk 0.00 from 01712345678. Ref TEST. Fee Tk 0.00. Balance Tk 2,500.00. TrxID TRX12345678 at 17/11/2026 00:30"
        assertNull(bkashParser.parse("bKash", zeroAmount, sampleReceivedAt))
    }

    @Test
    fun `reject invalid phone or Rocket account lengths`() {
        // 10 digits for bKash (requires 11 digits)
        val tenDigitBkash = "You have received Tk 500.00 from 0171234567. Ref TEST. Fee Tk 0.00. Balance Tk 2,500.00. TrxID TRX12345678 at 17/11/2026 00:30"
        assertNull(bkashParser.parse("bKash", tenDigitBkash, sampleReceivedAt))

        // 11 digits for Rocket (requires 12-digit Rocket account)
        val elevenDigitRocket = "You have received BDT 900.00 from 01711223344. Fee: BDT 0.00. Balance: BDT 9,400.00. TrxID: RK88392014. Time: 17/11/2026 11:40."
        assertNull(rocketParser.parse("16216", elevenDigitRocket, sampleReceivedAt))
    }

    @Test
    fun `reject malformed calendar timestamps`() {
        val invalidDate = "You have received Tk 500.00 from 01712345678. Ref TEST. Fee Tk 0.00. Balance Tk 2,500.00. TrxID TRX12345678 at 32/13/2026 25:99"
        assertNull(bkashParser.parse("bKash", invalidDate, sampleReceivedAt))
    }

    @Test
    fun `normalize extra whitespace and Bengali numerals safely`() {
        val messySms = "  You   have  received Tk  ৫০০.০০  from  ০১৭১২৩৪৫৬৭৮. Ref COURSE2026. Fee Tk ০.০০. Balance Tk ২৫০০.০০. TrxID TRX12345678 at ১৭/১১/২০২৬ ০০:৩০  "
        val parsed = bkashParser.parse("bKash", messySms, sampleReceivedAt)
        assertNotNull(parsed)
        assertEquals("500.00", parsed!!.amount)
        assertEquals("01712345678", parsed.senderNumber)
        assertEquals("2026-11-17T00:30:00+06:00", parsed.transactionTime)
    }

    @Test
    fun `ignore unrelated personal messages containing payment keywords`() {
        val personalSms = "Hi, I have received your cash in request for 500 Tk. Please call me!"
        val outcome = compositeParser.inspectAndParse("Friend", personalSms, sampleReceivedAt)
        assertEquals(SmsParseOutcome.UnrelatedMessageIgnored, outcome)
    }
}
