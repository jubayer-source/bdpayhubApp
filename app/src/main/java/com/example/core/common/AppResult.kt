package com.example.core.common

/**
 * Exhaustive error taxonomy covering all 13 required failure modes in Section 18.
 * Never exposes raw stack traces, SQL errors, or internal server details to users.
 */
enum class AppErrorType(
    val defaultUserMessage: String,
    val isRetryable: Boolean
) {
    NO_INTERNET(
        defaultUserMessage = "No internet connection. Payment events are safely queued locally and will synchronize when connectivity returns.",
        isRetryable = true
    ),
    BACKEND_UNAVAILABLE(
        defaultUserMessage = "BDPayHub backend is temporarily unavailable. Queued events will retry automatically using exponential backoff.",
        isRetryable = true
    ),
    AUTH_EXPIRED(
        defaultUserMessage = "Your merchant session has expired. Please sign in with Google again to refresh your session.",
        isRetryable = false
    ),
    NO_MERCHANT_PROFILE(
        defaultUserMessage = "No BDPayHub merchant profile was found for this Google account. Please complete merchant registration on the BDPayHub website first.",
        isRetryable = false
    ),
    DEVICE_NOT_AUTHORIZED(
        defaultUserMessage = "This device is not authorized to submit payment events. Complete device registration and approval first.",
        isRetryable = false
    ),
    DEVICE_REVOKED(
        defaultUserMessage = "This device's authorization has been revoked. Event ingestion and background synchronization have been stopped.",
        isRetryable = false
    ),
    SUBSCRIPTION_LIMIT_REACHED(
        defaultUserMessage = "Merchant subscription limit reached. The payment event is preserved locally; upgrade your BDPayHub plan to resume synchronization.",
        isRetryable = false
    ),
    INVALID_API_RESPONSE(
        defaultUserMessage = "The server returned an unexpected response format. Your local payment event remains safely preserved.",
        isRetryable = true
    ),
    DUPLICATE_TRANSACTION_EVENT(
        defaultUserMessage = "Duplicate Transaction ID detected. This payment notification has already been recorded.",
        isRetryable = false
    ),
    UNKNOWN_SMS_FORMAT(
        defaultUserMessage = "Message did not match any verified provider payment pattern and was ignored.",
        isRetryable = false
    ),
    MISSING_SMS_PERMISSION(
        defaultUserMessage = "SMS permission is disabled. Grant RECEIVE_SMS permission so BDPayHub can parse payment notifications locally.",
        isRetryable = false
    ),
    LOCAL_DATABASE_FAILURE(
        defaultUserMessage = "A local storage error occurred while accessing the event queue. Please check device storage and try again.",
        isRetryable = true
    ),
    WORKMANAGER_RETRY_EXHAUSTED(
        defaultUserMessage = "Automatic background retries were exhausted for one or more queued events. Tap Sync Now when your connection is stable.",
        isRetryable = true
    ),
    SERVER_VALIDATION_REJECTION(
        defaultUserMessage = "Backend validation rejected this payment event (unmatched receiving wallet, expired payment window, or invalid parameters).",
        isRetryable = false
    )
}

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(
        val errorType: AppErrorType,
        val userMessage: String = errorType.defaultUserMessage,
        val httpStatusCode: Int? = null,
        val requestId: String? = null
    ) : AppResult<Nothing>()
}
