package com.example.domain.model

import kotlinx.serialization.Serializable

enum class AppEnvironment(val label: String) {
    DEVELOPMENT("Development"),
    STAGING("Staging"),
    PRODUCTION("Production")
}

enum class PaymentProvider(
    val apiCode: String,
    val displayName: String,
    val expectedSenderId: String,
    val brandHex: Long
) {
    BKASH("bkash", "bKash", "bKash", 0xFFE2136EL),
    NAGAD("nagad", "Nagad", "NAGAD", 0xFFF7941DL),
    ROCKET("rocket", "Rocket", "16216", 0xFF8C3494L),
    UPAY("upay", "upay", "upay", 0xFF005AA7L);

    companion object {
        fun fromApiCode(code: String): PaymentProvider? =
            entries.find { it.apiCode.equals(code.trim(), ignoreCase = true) }

        fun fromSenderId(senderId: String): PaymentProvider? =
            entries.find { it.expectedSenderId == senderId.trim() }
    }
}

enum class PaymentTransactionType(
    val apiValue: String,
    val displayName: String,
    val senderRoleDescription: String
) {
    SEND_MONEY_RECEIVED(
        apiValue = "SEND_MONEY_RECEIVED",
        displayName = "Send Money (P2P)",
        senderRoleDescription = "Customer Wallet / Account Number"
    ),
    AGENT_CASH_IN_RECEIVED(
        apiValue = "AGENT_CASH_IN_RECEIVED",
        displayName = "Agent Cash-In",
        senderRoleDescription = "Agent / Uddokta Number (Not Customer)"
    ),
    CUSTOMER_CASH_OUT_RECEIVED(
        apiValue = "CUSTOMER_CASH_OUT_RECEIVED",
        displayName = "Customer Cash-Out (Agent Wallet)",
        senderRoleDescription = "Customer Wallet / Account Number"
    );

    companion object {
        fun fromApiValue(value: String): PaymentTransactionType? =
            entries.find { it.apiValue.equals(value.trim(), ignoreCase = true) }
    }
}

/**
 * Local & backend synchronization states.
 * Note: These represent synchronization and server-side processing status.
 * The Android app NEVER marks a transaction successful by itself; only the backend
 * can return SERVER_MATCHED after independent verification.
 */
enum class SyncState(
    val displayName: String,
    val description: String,
    val isServerVerifiedPayment: Boolean
) {
    LOCAL_PENDING(
        displayName = "Local Pending",
        description = "Parsed locally and queued for backend upload",
        isServerVerifiedPayment = false
    ),
    UPLOADING(
        displayName = "Uploading",
        description = "Currently transmitting structured event to BDPayHub backend",
        isServerVerifiedPayment = false
    ),
    SERVER_RECEIVED(
        displayName = "Event Received",
        description = "Received by backend; awaiting payment intent reconciliation",
        isServerVerifiedPayment = false
    ),
    SERVER_REVIEW(
        displayName = "Pending Review",
        description = "Flagged by backend for manual review (e.g. Agent Cash-In or ambiguous intent)",
        isServerVerifiedPayment = false
    ),
    SERVER_MATCHED(
        displayName = "Payment Verified",
        description = "Independently matched and verified by BDPayHub backend",
        isServerVerifiedPayment = true
    ),
    SERVER_REJECTED(
        displayName = "Server Rejected",
        description = "Rejected by backend validation (unmatched wallet, expired window, or invalid state)",
        isServerVerifiedPayment = false
    ),
    UPLOAD_FAILED(
        displayName = "Upload Failed",
        description = "Network or server error prevented upload; queued for retry",
        isServerVerifiedPayment = false
    )
}

enum class DeviceAuthStatus(val displayName: String) {
    UNREGISTERED("Unregistered"),
    PENDING("Pending Approval"),
    AUTHORIZED("Authorized"),
    REVOKED("Revoked")
}

/**
 * Exact structured payment event produced by the local SMS parser engine.
 * Never contains raw SMS body. Monetary amounts use decimal-safe String representation.
 */
@Serializable
data class ParsedPaymentEvent(
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
    val receivedAt: Long
)

@Serializable
data class ConfiguredWallet(
    val provider: String,
    val walletNumber: String,
    val accountType: String, // PERSONAL or AGENT
    val isConfigured: Boolean,
    val isActive: Boolean,
    val backendVerified: Boolean
)

@Serializable
data class MerchantProfile(
    val merchantId: String,
    val businessName: String,
    val googleEmail: String,
    val planName: String,
    val maxAllowedDevices: Int,
    val activeDeviceCount: Int,
    val monthlyEventQuota: Int,
    val usedEventsCount: Int,
    val isSubscriptionActive: Boolean,
    val configuredWallets: List<ConfiguredWallet>
)

@Serializable
data class DeviceRegistrationInfo(
    val deviceId: String,
    val merchantId: String,
    val deviceName: String,
    val androidVersion: String,
    val appVersion: String,
    val registeredAt: Long,
    val lastActiveAt: Long,
    val lastSyncedAt: Long?,
    val authorizationStatus: DeviceAuthStatus
)

data class DashboardSummary(
    val merchantName: String,
    val merchantEmail: String,
    val planName: String,
    val deviceStatus: DeviceAuthStatus,
    val deviceName: String,
    val deviceId: String,
    val smsPermissionGranted: Boolean,
    val isNetworkAvailable: Boolean,
    val lastSyncTimeMillis: Long?,
    val totalEventsReceived: Int,
    val matchedVerifiedPayments: Int,
    val pendingOrReviewEvents: Int,
    val rejectedEvents: Int,
    val localQueuedEvents: Int,
    val uploadFailedEvents: Int,
    val configuredWallets: List<ConfiguredWallet>,
    val isBackendSource: Boolean
)

data class PaymentEventRecord(
    val localId: Long,
    val merchantId: String,
    val deviceId: String,
    val provider: PaymentProvider,
    val transactionType: PaymentTransactionType,
    val amount: String,
    val currency: String,
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
    val syncState: SyncState,
    val serverStatusMessage: String?,
    val retryCount: Int,
    val lastSyncAttemptAt: Long?,
    val lastSyncedAt: Long?
)

enum class DateRangeOption(val label: String, val durationMillis: Long?) {
    ALL_TIME("All Time", null),
    TODAY("Today (24h)", 24L * 60 * 60 * 1000L),
    LAST_7_DAYS("Last 7 Days", 7L * 24 * 60 * 60 * 1000L),
    LAST_30_DAYS("Last 30 Days", 30L * 24 * 60 * 60 * 1000L)
}

data class TransactionFilter(
    val searchQuery: String = "",
    val provider: PaymentProvider? = null,
    val transactionType: PaymentTransactionType? = null,
    val syncState: SyncState? = null,
    val dateRange: DateRangeOption = DateRangeOption.ALL_TIME,
    val page: Int = 1,
    val pageSize: Int = 15
)

data class ParserDiagnosticEntry(
    val id: Long,
    val senderId: String,
    val failureReason: String,
    val parserVersion: String,
    val recordedAt: Long
)
