package com.example.data.sms

import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentTransactionType

data class OfficialSmsTemplate(
    val id: String,
    val title: String,
    val provider: PaymentProvider,
    val transactionType: PaymentTransactionType,
    val senderId: String,
    val sampleMessage: String,
    val note: String
)

/**
 * Exact 11 initial provider SMS formats defined in Section 7 of the specification,
 * for local verification, parser diagnostics, and QA testing.
 */
object OfficialSmsSampleCatalog {

    val templates: List<OfficialSmsTemplate> = listOf(
        // 7.1 bKash
        OfficialSmsTemplate(
            id = "bkash_p2p",
            title = "bKash Personal — Send Money",
            provider = PaymentProvider.BKASH,
            transactionType = PaymentTransactionType.SEND_MONEY_RECEIVED,
            senderId = "bKash",
            sampleMessage = "You have received Tk 1,250.00 from 01712345678. Ref INV2026A. Fee Tk 0.00. Balance Tk 18,450.00. TrxID BK9823417A at 17/11/2026 10:15",
            note = "Sender is customer 11-digit number; eligible for automatic intent matching."
        ),
        OfficialSmsTemplate(
            id = "bkash_agent_cashin",
            title = "bKash Personal — Agent Cash-In",
            provider = PaymentProvider.BKASH,
            transactionType = PaymentTransactionType.AGENT_CASH_IN_RECEIVED,
            senderId = "bKash",
            sampleMessage = "Cash In Tk 2,500.00 from 01819876543 successful. Fee Tk 0.00. Balance Tk 20,950.00. TrxID BK7741209C at 17/11/2026 10:22. Download App: bka.sh",
            note = "Sender is the bKash Agent number (NOT the customer). Backend flags as SERVER_REVIEW."
        ),
        OfficialSmsTemplate(
            id = "bkash_cashout",
            title = "bKash Agent — Customer Cash-Out",
            provider = PaymentProvider.BKASH,
            transactionType = PaymentTransactionType.CUSTOMER_CASH_OUT_RECEIVED,
            senderId = "bKash",
            sampleMessage = "You have received Cash Out Tk 3,000.00 from 01911223344. Comm Tk 12.00. Balance Tk 45,200.00. TrxID BK5531908D at 17/11/2026 10:30.",
            note = "Customer Cash-Out received on merchant Agent wallet."
        ),

        // 7.2 Nagad
        OfficialSmsTemplate(
            id = "nagad_p2p",
            title = "Nagad Personal — P2P Received",
            provider = PaymentProvider.NAGAD,
            transactionType = PaymentTransactionType.SEND_MONEY_RECEIVED,
            senderId = "NAGAD",
            sampleMessage = "Money Received. Amount: Tk 750.00 Sender: 01611445566 Ref: ORD8821 TxnID: NG71829304 Balance: Tk 12,300.00 17/11/2026 11:05",
            note = "Sender is customer 11-digit number with order reference."
        ),
        OfficialSmsTemplate(
            id = "nagad_agent_cashin",
            title = "Nagad Personal — Agent Cash-In",
            provider = PaymentProvider.NAGAD,
            transactionType = PaymentTransactionType.AGENT_CASH_IN_RECEIVED,
            senderId = "NAGAD",
            sampleMessage = "Cash In Received. Amount: Tk 1,500.00 Uddokta: 01522334455 TxnID: NG8291045B Balance: Tk 13,800.00 17/11/2026 11:12",
            note = "Uddokta number is the Agent, never substituted for the customer."
        ),
        OfficialSmsTemplate(
            id = "nagad_cashout",
            title = "Nagad Agent — Customer Cash-Out",
            provider = PaymentProvider.NAGAD,
            transactionType = PaymentTransactionType.CUSTOMER_CASH_OUT_RECEIVED,
            senderId = "NAGAD",
            sampleMessage = "Cash Out Received. Amount: Tk 4,200.00 Customer: 01755667788 Comm: Tk 16.80 TxnID: NG9938471C Balance: Tk 38,900.00 17/11/2026 11:20",
            note = "Customer number extracted from Nagad Agent Cash-Out notification."
        ),

        // 7.3 Rocket
        OfficialSmsTemplate(
            id = "rocket_p2p",
            title = "Rocket Personal — P2P Received",
            provider = PaymentProvider.ROCKET,
            transactionType = PaymentTransactionType.SEND_MONEY_RECEIVED,
            senderId = "16216",
            sampleMessage = "You have received BDT 900.00 from 017112233445. Fee: BDT 0.00. Balance: BDT 9,400.00. TrxID: RK88392014. Time: 17/11/2026 11:40.",
            note = "Uses 12-digit Rocket account number and sender ID 16216."
        ),
        OfficialSmsTemplate(
            id = "rocket_agent_cashin",
            title = "Rocket Personal — Agent Cash-In",
            provider = PaymentProvider.ROCKET,
            transactionType = PaymentTransactionType.AGENT_CASH_IN_RECEIVED,
            senderId = "16216",
            sampleMessage = "You have received Cash In of BDT 2,000.00 from Agent 018223344556. Fee: BDT 0.00. Balance: BDT 11,400.00. TrxID: RK77482910. Time: 17/11/2026 11:45.",
            note = "12-digit Agent account; held for server review."
        ),
        OfficialSmsTemplate(
            id = "rocket_cashout",
            title = "Rocket Agent — Customer Cash-Out",
            provider = PaymentProvider.ROCKET,
            transactionType = PaymentTransactionType.CUSTOMER_CASH_OUT_RECEIVED,
            senderId = "16216",
            sampleMessage = "You have received Cash Out of BDT 1,800.00 from 019334455667. Comm: BDT 7.20. Balance: BDT 26,500.00. TrxID: RK66574839. Time: 17/11/2026 11:50.",
            note = "12-digit Customer Rocket account number."
        ),

        // 7.4 upay
        OfficialSmsTemplate(
            id = "upay_p2p",
            title = "upay Personal — Received Payment",
            provider = PaymentProvider.UPAY,
            transactionType = PaymentTransactionType.SEND_MONEY_RECEIVED,
            senderId = "upay",
            sampleMessage = "You have received Tk 600.00 from 01677889900. Fee Tk 0.00. Balance Tk 5,600.00. TrxID UP4492018A at 17/11/2026 12:05.",
            note = "Personal payment received on upay wallet."
        ),
        OfficialSmsTemplate(
            id = "upay_agent_cashin",
            title = "upay Personal — Agent Cash-In",
            provider = PaymentProvider.UPAY,
            transactionType = PaymentTransactionType.AGENT_CASH_IN_RECEIVED,
            senderId = "upay",
            sampleMessage = "Cash In Tk 1,100.00 from 01344556677 successful. Fee Tk 0.00. Balance Tk 6,700.00. TrxID UP5583920B at 17/11/2026 12:15.",
            note = "Agent Cash-In on upay wallet (upay Agent Cash-Out is intentionally unsupported)."
        )
    )
}
