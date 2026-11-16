package com.example.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ParserDiagnosticEntry
import com.example.domain.model.PaymentEventRecord
import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentTransactionType
import com.example.domain.model.SyncState

/**
 * Room Entity storing structured, locally parsed payment events.
 *
 * Security & Privacy:
 * - Never stores raw SMS text.
 * - Enforces composite uniqueness on (provider, transactionId) to prevent duplicate queue entries.
 */
@Entity(
    tableName = "payment_events",
    indices = [
        Index(value = ["provider", "transactionId"], unique = true),
        Index(value = ["syncState"]),
        Index(value = ["receivedAt"])
    ]
)
data class PaymentEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val merchantId: String,
    val deviceId: String,
    val provider: String,
    val transactionType: String,
    val amount: String,
    val currency: String = "BDT",
    val senderNumber: String?,
    val receiverNumber: String?,
    val transactionId: String,
    val transactionTime: String?,
    val reference: String?,
    val fee: String?,
    val commission: String?,
    val balance: String?,
    val parserVersion: String,
    val receivedAt: Long,
    val syncState: String,
    val serverStatusMessage: String? = null,
    val retryCount: Int = 0,
    val lastSyncAttemptAt: Long? = null,
    val lastSyncedAt: Long? = null
) {
    fun toDomain(): PaymentEventRecord {
        return PaymentEventRecord(
            localId = id,
            merchantId = merchantId,
            deviceId = deviceId,
            provider = PaymentProvider.fromApiCode(provider) ?: PaymentProvider.BKASH,
            transactionType = PaymentTransactionType.fromApiValue(transactionType)
                ?: PaymentTransactionType.SEND_MONEY_RECEIVED,
            amount = amount,
            currency = currency,
            senderNumber = senderNumber,
            receiverNumber = receiverNumber,
            transactionId = transactionId,
            transactionTime = transactionTime,
            reference = reference,
            fee = fee,
            commission = commission,
            balance = balance,
            parserVersion = parserVersion,
            receivedAt = receivedAt,
            syncState = runCatching { SyncState.valueOf(syncState) }.getOrDefault(SyncState.LOCAL_PENDING),
            serverStatusMessage = serverStatusMessage,
            retryCount = retryCount,
            lastSyncAttemptAt = lastSyncAttemptAt,
            lastSyncedAt = lastSyncedAt
        )
    }
}

/**
 * Local diagnostic log for unrecognized SMS formats from known payment sender IDs.
 * Stores metadata only — NEVER stores raw SMS message content.
 */
@Entity(tableName = "parser_diagnostics")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val senderId: String,
    val failureReason: String,
    val parserVersion: String,
    val recordedAt: Long
) {
    fun toDomain() = ParserDiagnosticEntry(
        id = id,
        senderId = senderId,
        failureReason = failureReason,
        parserVersion = parserVersion,
        recordedAt = recordedAt
    )
}
