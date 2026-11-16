package com.example.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseGoogleAuthRequest(
    @SerialName("id_token") val idToken: String,
    @SerialName("provider") val provider: String = "google"
)

@Serializable
data class SupabaseRefreshRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class SupabaseUserDto(
    @SerialName("id") val id: String,
    @SerialName("email") val email: String
)

@Serializable
data class SupabaseSessionResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("user") val user: SupabaseUserDto
)

@Serializable
data class ConfiguredWalletDto(
    @SerialName("provider") val provider: String,
    @SerialName("wallet_number") val walletNumber: String,
    @SerialName("account_type") val accountType: String,
    @SerialName("is_configured") val isConfigured: Boolean,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("backend_verified") val backendVerified: Boolean
)

@Serializable
data class MerchantProfileResponse(
    @SerialName("merchant_id") val merchantId: String,
    @SerialName("business_name") val businessName: String,
    @SerialName("google_email") val googleEmail: String,
    @SerialName("plan_name") val planName: String,
    @SerialName("max_allowed_devices") val maxAllowedDevices: Int,
    @SerialName("active_device_count") val activeDeviceCount: Int,
    @SerialName("monthly_event_quota") val monthlyEventQuota: Int,
    @SerialName("used_events_count") val usedEventsCount: Int,
    @SerialName("is_subscription_active") val isSubscriptionActive: Boolean,
    @SerialName("configured_wallets") val configuredWallets: List<ConfiguredWalletDto>
)

@Serializable
data class DeviceRegistrationRequest(
    @SerialName("device_name") val deviceName: String,
    @SerialName("android_version") val androidVersion: String,
    @SerialName("app_version") val appVersion: String
)

@Serializable
data class DeviceRegistrationResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("merchant_id") val merchantId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("authorization_status") val authorizationStatus: String,
    @SerialName("device_credential") val deviceCredential: String?,
    @SerialName("registered_at") val registeredAtEpochMillis: Long,
    @SerialName("message") val message: String
)

@Serializable
data class DeviceStatusResponse(
    @SerialName("device_id") val deviceId: String,
    @SerialName("merchant_id") val merchantId: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("authorization_status") val authorizationStatus: String,
    @SerialName("last_active_at") val lastActiveAtEpochMillis: Long,
    @SerialName("message") val message: String? = null
)

/**
 * Exact ingestion payload specified in Section 10.3:
 * POST /api/v1/transactions/ingest
 */
@Serializable
data class TransactionIngestRequest(
    @SerialName("merchant_id") val merchantId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("provider") val provider: String,
    @SerialName("transaction_type") val transactionType: String,
    @SerialName("amount") val amount: String,
    @SerialName("currency") val currency: String = "BDT",
    @SerialName("sender_number") val senderNumber: String?,
    @SerialName("receiver_number") val receiverNumber: String?,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("transaction_time") val transactionTime: String?,
    @SerialName("reference") val reference: String?,
    @SerialName("fee") val fee: String?,
    @SerialName("commission") val commission: String?,
    @SerialName("balance") val balance: String?,
    @SerialName("parser_version") val parserVersion: String
)

@Serializable
data class TransactionIngestResponse(
    @SerialName("event_id") val eventId: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("processing_status") val processingStatus: String, // SERVER_RECEIVED, SERVER_REVIEW, SERVER_MATCHED, SERVER_REJECTED
    @SerialName("duplicate") val duplicate: Boolean = false,
    @SerialName("matched_intent_id") val matchedIntentId: String? = null,
    @SerialName("message") val message: String,
    @SerialName("processed_at") val processedAtEpochMillis: Long
)

@Serializable
data class BackendErrorResponse(
    @SerialName("error_code") val errorCode: String,
    @SerialName("message") val message: String
)
