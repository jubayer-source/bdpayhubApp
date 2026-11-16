package com.example.domain.repository

import com.example.core.common.AppResult
import com.example.domain.model.DashboardSummary
import com.example.domain.model.DeviceAuthStatus
import com.example.domain.model.DeviceRegistrationInfo
import com.example.domain.model.MerchantProfile
import com.example.domain.model.ParserDiagnosticEntry
import com.example.domain.model.PaymentEventRecord
import com.example.domain.model.TransactionFilter
import kotlinx.coroutines.flow.Flow

sealed class SmsIngestionOutcome {
    data class Queued(val localId: Long, val event: PaymentEventRecord) : SmsIngestionOutcome()
    data class DuplicateIgnored(val transactionId: String, val provider: String) : SmsIngestionOutcome()
    data class UnrecognizedFormatLogged(val senderId: String, val reason: String) : SmsIngestionOutcome()
    data object UnrelatedSmsIgnored : SmsIngestionOutcome()
    data class RejectedByDeviceState(val status: DeviceAuthStatus, val message: String) : SmsIngestionOutcome()
}

data class SyncBatchSummary(
    val totalAttempted: Int,
    val succeededCount: Int,
    val matchedCount: Int,
    val reviewCount: Int,
    val rejectedCount: Int,
    val failedCount: Int
)

interface BDPayHubRepository {
    val allEventsFlow: Flow<List<PaymentEventRecord>>
    val recentEventsFlow: Flow<List<PaymentEventRecord>>
    val diagnosticLogsFlow: Flow<List<ParserDiagnosticEntry>>

    suspend fun authenticateWithGoogle(googleIdToken: String): AppResult<MerchantProfile>
    suspend fun refreshSessionIfNeeded(): AppResult<Unit>
    suspend fun fetchMerchantProfile(): AppResult<MerchantProfile>
    fun getCachedMerchantProfile(): MerchantProfile?

    suspend fun registerAndAuthorizeDevice(
        deviceName: String,
        androidVersion: String
    ): AppResult<DeviceRegistrationInfo>

    suspend fun verifyDeviceStatusWithBackend(): AppResult<DeviceAuthStatus>
    suspend fun revokeCurrentDeviceLocally(reason: String)

    suspend fun processIncomingSms(
        senderId: String,
        rawMessage: String,
        receivedAt: Long = System.currentTimeMillis()
    ): SmsIngestionOutcome

    suspend fun syncPendingEvents(): AppResult<SyncBatchSummary>

    suspend fun getEventById(localId: Long): PaymentEventRecord?
    fun filterEvents(allEvents: List<PaymentEventRecord>, filter: TransactionFilter): List<PaymentEventRecord>

    suspend fun buildDashboardSummary(
        smsPermissionGranted: Boolean,
        isNetworkAvailable: Boolean
    ): DashboardSummary

    suspend fun signOut()
}
