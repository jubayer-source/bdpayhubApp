package com.example.data.repository

import android.os.Build
import com.example.core.common.AppErrorType
import com.example.core.common.AppResult
import com.example.core.common.RedactingLogger
import com.example.core.config.EnvironmentConfig
import com.example.core.database.DiagnosticLogEntity
import com.example.core.database.PaymentEventDao
import com.example.core.database.PaymentEventEntity
import com.example.core.datastore.AppPreferencesDataStore
import com.example.core.network.BDPayHubBackendApi
import com.example.core.network.ConfiguredWalletDto
import com.example.core.network.DeviceRegistrationRequest
import com.example.core.network.MerchantProfileResponse
import com.example.core.network.SupabaseAuthApi
import com.example.core.network.SupabaseGoogleAuthRequest
import com.example.core.network.SupabaseRefreshRequest
import com.example.core.network.TransactionIngestRequest
import com.example.core.security.SecureCredentialStore
import com.example.data.sms.CompositePaymentSmsParser
import com.example.data.sms.SmsParseOutcome
import com.example.domain.model.ConfiguredWallet
import com.example.domain.model.DashboardSummary
import com.example.domain.model.DeviceAuthStatus
import com.example.domain.model.DeviceRegistrationInfo
import com.example.domain.model.MerchantProfile
import com.example.domain.model.ParserDiagnosticEntry
import com.example.domain.model.PaymentEventRecord
import com.example.domain.model.SyncState
import com.example.domain.model.TransactionFilter
import com.example.domain.repository.BDPayHubRepository
import com.example.domain.repository.SmsIngestionOutcome
import com.example.domain.repository.SyncBatchSummary
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class BDPayHubRepositoryImpl(
    private val supabaseAuthApiProvider: () -> SupabaseAuthApi,
    private val backendApiProvider: () -> BDPayHubBackendApi,
    private val paymentEventDao: PaymentEventDao,
    private val credentialStore: SecureCredentialStore,
    private val preferencesDataStore: AppPreferencesDataStore,
    private val smsParser: CompositePaymentSmsParser = CompositePaymentSmsParser()
) : BDPayHubRepository {

    @Volatile
    private var cachedMerchantProfile: MerchantProfile? = null

    override val allEventsFlow: Flow<List<PaymentEventRecord>> =
        paymentEventDao.observeAllEvents().map { list -> list.map { it.toDomain() } }

    override val recentEventsFlow: Flow<List<PaymentEventRecord>> =
        paymentEventDao.observeRecentEvents(8).map { list -> list.map { it.toDomain() } }

    override val diagnosticLogsFlow: Flow<List<ParserDiagnosticEntry>> =
        paymentEventDao.observeDiagnosticLogs().map { list -> list.map { it.toDomain() } }

    override suspend fun authenticateWithGoogle(googleIdToken: String): AppResult<MerchantProfile> {
        return try {
            val authResponse = supabaseAuthApiProvider().signInWithGoogleIdToken(
                request = SupabaseGoogleAuthRequest(idToken = googleIdToken)
            )
            if (!authResponse.isSuccessful || authResponse.body() == null) {
                return AppResult.Error(
                    errorType = AppErrorType.AUTH_EXPIRED,
                    httpStatusCode = authResponse.code()
                )
            }
            val session = authResponse.body()!!
            val nowSec = System.currentTimeMillis() / 1000L
            credentialStore.saveSupabaseSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresAtEpochSeconds = nowSec + session.expiresIn,
                userEmail = session.user.email,
                merchantId = null
            )

            // Validate JWT with BDPayHub Rust backend and retrieve merchant profile
            fetchMerchantProfile()
        } catch (_: IOException) {
            AppResult.Error(AppErrorType.NO_INTERNET)
        } catch (e: Exception) {
            RedactingLogger.e("Auth", "Authentication failed: ${e.javaClass.simpleName}")
            AppResult.Error(AppErrorType.BACKEND_UNAVAILABLE)
        }
    }

    override suspend fun refreshSessionIfNeeded(): AppResult<Unit> {
        if (!credentialStore.isAccessTokenExpired()) {
            return AppResult.Success(Unit)
        }
        val refreshToken = credentialStore.getRefreshToken()
            ?: return AppResult.Error(AppErrorType.AUTH_EXPIRED)

        return try {
            val response = supabaseAuthApiProvider().refreshSession(
                request = SupabaseRefreshRequest(refreshToken = refreshToken)
            )
            if (!response.isSuccessful || response.body() == null) {
                return AppResult.Error(
                    errorType = AppErrorType.AUTH_EXPIRED,
                    httpStatusCode = response.code()
                )
            }
            val session = response.body()!!
            val nowSec = System.currentTimeMillis() / 1000L
            credentialStore.saveSupabaseSession(
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                expiresAtEpochSeconds = nowSec + session.expiresIn,
                userEmail = session.user.email,
                merchantId = credentialStore.getMerchantId()
            )
            AppResult.Success(Unit)
        } catch (_: IOException) {
            AppResult.Error(AppErrorType.NO_INTERNET)
        } catch (_: Exception) {
            AppResult.Error(AppErrorType.AUTH_EXPIRED)
        }
    }

    override suspend fun fetchMerchantProfile(): AppResult<MerchantProfile> {
        val refreshOutcome = refreshSessionIfNeeded()
        if (refreshOutcome is AppResult.Error && refreshOutcome.errorType == AppErrorType.AUTH_EXPIRED) {
            return refreshOutcome
        }
        val accessToken = credentialStore.getAccessToken()
            ?: return AppResult.Error(AppErrorType.AUTH_EXPIRED)

        return try {
            val response = backendApiProvider().getMerchantProfile("Bearer $accessToken")
            when (response.code()) {
                200 -> {
                    val body = response.body()
                        ?: return AppResult.Error(AppErrorType.INVALID_API_RESPONSE)
                    val domainProfile = body.toDomain()
                    cachedMerchantProfile = domainProfile
                    credentialStore.saveSupabaseSession(
                        accessToken = accessToken,
                        refreshToken = credentialStore.getRefreshToken() ?: "",
                        expiresAtEpochSeconds = credentialStore.getSessionExpiresAt(),
                        userEmail = domainProfile.googleEmail,
                        merchantId = domainProfile.merchantId
                    )
                    AppResult.Success(domainProfile)
                }
                401 -> AppResult.Error(AppErrorType.AUTH_EXPIRED, httpStatusCode = 401)
                404 -> AppResult.Error(AppErrorType.NO_MERCHANT_PROFILE, httpStatusCode = 404)
                503, 502, 504 -> AppResult.Error(AppErrorType.BACKEND_UNAVAILABLE, httpStatusCode = response.code())
                else -> AppResult.Error(AppErrorType.INVALID_API_RESPONSE, httpStatusCode = response.code())
            }
        } catch (_: IOException) {
            AppResult.Error(AppErrorType.NO_INTERNET)
        } catch (_: Exception) {
            AppResult.Error(AppErrorType.INVALID_API_RESPONSE)
        }
    }

    override fun getCachedMerchantProfile(): MerchantProfile? = cachedMerchantProfile

    override suspend fun registerAndAuthorizeDevice(
        deviceName: String,
        androidVersion: String
    ): AppResult<DeviceRegistrationInfo> {
        val accessToken = credentialStore.getAccessToken()
            ?: return AppResult.Error(AppErrorType.AUTH_EXPIRED)
        val cleanName = deviceName.trim().ifBlank { "Merchant Android Agent" }

        return try {
            val response = backendApiProvider().registerDevice(
                bearerAccessToken = "Bearer $accessToken",
                request = DeviceRegistrationRequest(
                    deviceName = cleanName,
                    androidVersion = androidVersion,
                    appVersion = EnvironmentConfig.APP_VERSION
                )
            )
            when (response.code()) {
                200, 201 -> {
                    val dto = response.body()
                        ?: return AppResult.Error(AppErrorType.INVALID_API_RESPONSE)
                    val status = runCatching {
                        DeviceAuthStatus.valueOf(dto.authorizationStatus.uppercase())
                    }.getOrDefault(DeviceAuthStatus.PENDING)

                    if (!dto.deviceCredential.isNullOrBlank()) {
                        credentialStore.saveDeviceCredential(
                            deviceId = dto.deviceId,
                            deviceCredential = dto.deviceCredential,
                            deviceName = dto.deviceName
                        )
                    }
                    preferencesDataStore.setDeviceAuthStatus(status)
                    if (status == DeviceAuthStatus.AUTHORIZED) {
                        preferencesDataStore.setOnboardingCompleted(true)
                    }

                    AppResult.Success(
                        DeviceRegistrationInfo(
                            deviceId = dto.deviceId,
                            merchantId = dto.merchantId,
                            deviceName = dto.deviceName,
                            androidVersion = androidVersion,
                            appVersion = EnvironmentConfig.APP_VERSION,
                            registeredAt = dto.registeredAtEpochMillis,
                            lastActiveAt = System.currentTimeMillis(),
                            lastSyncedAt = null,
                            authorizationStatus = status
                        )
                    )
                }
                401 -> AppResult.Error(AppErrorType.AUTH_EXPIRED, httpStatusCode = 401)
                403 -> {
                    revokeCurrentDeviceLocally("Device authorization rejected or revoked by backend")
                    AppResult.Error(AppErrorType.DEVICE_REVOKED, httpStatusCode = 403)
                }
                429 -> AppResult.Error(AppErrorType.SUBSCRIPTION_LIMIT_REACHED, httpStatusCode = 429)
                else -> AppResult.Error(AppErrorType.BACKEND_UNAVAILABLE, httpStatusCode = response.code())
            }
        } catch (_: IOException) {
            AppResult.Error(AppErrorType.NO_INTERNET)
        } catch (_: Exception) {
            AppResult.Error(AppErrorType.INVALID_API_RESPONSE)
        }
    }

    override suspend fun verifyDeviceStatusWithBackend(): AppResult<DeviceAuthStatus> {
        val deviceId = credentialStore.getDeviceId()
            ?: return AppResult.Error(AppErrorType.DEVICE_NOT_AUTHORIZED)
        val credential = credentialStore.getDeviceCredential()
            ?: return AppResult.Error(AppErrorType.DEVICE_NOT_AUTHORIZED)

        return try {
            val response = backendApiProvider().getDeviceStatus(
                bearerDeviceCredential = "Bearer $credential",
                deviceId = deviceId
            )
            when (response.code()) {
                200 -> {
                    val statusStr = response.body()?.authorizationStatus ?: "AUTHORIZED"
                    val status = runCatching {
                        DeviceAuthStatus.valueOf(statusStr.uppercase())
                    }.getOrDefault(DeviceAuthStatus.AUTHORIZED)
                    preferencesDataStore.setDeviceAuthStatus(status)
                    if (status == DeviceAuthStatus.REVOKED) {
                        revokeCurrentDeviceLocally("Revoked by backend status check")
                        return AppResult.Error(AppErrorType.DEVICE_REVOKED, httpStatusCode = 403)
                    }
                    AppResult.Success(status)
                }
                403 -> {
                    revokeCurrentDeviceLocally("Device revoked by backend")
                    AppResult.Error(AppErrorType.DEVICE_REVOKED, httpStatusCode = 403)
                }
                401 -> AppResult.Error(AppErrorType.DEVICE_NOT_AUTHORIZED, httpStatusCode = 401)
                else -> AppResult.Error(AppErrorType.BACKEND_UNAVAILABLE, httpStatusCode = response.code())
            }
        } catch (_: IOException) {
            AppResult.Error(AppErrorType.NO_INTERNET)
        } catch (_: Exception) {
            AppResult.Error(AppErrorType.BACKEND_UNAVAILABLE)
        }
    }

    override suspend fun revokeCurrentDeviceLocally(reason: String) {
        RedactingLogger.w("DeviceSecurity", "Enforcing device revocation: $reason")
        credentialStore.clearDeviceCredential()
        preferencesDataStore.setDeviceAuthStatus(DeviceAuthStatus.REVOKED)
    }

    override suspend fun processIncomingSms(
        senderId: String,
        rawMessage: String,
        receivedAt: Long
    ): SmsIngestionOutcome {
        val prefs = preferencesDataStore.preferencesFlow.first()
        val deviceStatus = prefs.deviceAuthStatus
        if (deviceStatus == DeviceAuthStatus.REVOKED) {
            RedactingLogger.w("SmsIngestion", "Blocked SMS ingestion: Device is REVOKED")
            return SmsIngestionOutcome.RejectedByDeviceState(
                status = DeviceAuthStatus.REVOKED,
                message = AppErrorType.DEVICE_REVOKED.defaultUserMessage
            )
        }
        val credential = credentialStore.getDeviceCredential()
        val deviceId = credentialStore.getDeviceId()
        val merchantId = credentialStore.getMerchantId()
        if (deviceStatus != DeviceAuthStatus.AUTHORIZED || credential.isNullOrBlank() || deviceId.isNullOrBlank() || merchantId.isNullOrBlank()) {
            return SmsIngestionOutcome.RejectedByDeviceState(
                status = deviceStatus,
                message = AppErrorType.DEVICE_NOT_AUTHORIZED.defaultUserMessage
            )
        }

        return when (val parseOutcome = smsParser.inspectAndParse(senderId, rawMessage, receivedAt)) {
            is SmsParseOutcome.UnrelatedMessageIgnored -> {
                SmsIngestionOutcome.UnrelatedSmsIgnored
            }
            is SmsParseOutcome.KnownSenderUnrecognizedFormat -> {
                paymentEventDao.insertDiagnosticLog(
                    DiagnosticLogEntity(
                        senderId = parseOutcome.senderId,
                        failureReason = parseOutcome.diagnosticReason,
                        parserVersion = parseOutcome.parserVersion,
                        recordedAt = receivedAt
                    )
                )
                SmsIngestionOutcome.UnrecognizedFormatLogged(
                    senderId = parseOutcome.senderId,
                    reason = parseOutcome.diagnosticReason
                )
            }
            is SmsParseOutcome.Parsed -> {
                val parsed = parseOutcome.event
                val verifiedReceiverWallet = resolveVerifiedReceiverWallet(parsed.provider)

                // Check for local duplicate first
                val existing = paymentEventDao.findByProviderAndTransactionId(
                    provider = parsed.provider,
                    transactionId = parsed.transactionId
                )
                if (existing != null) {
                    return SmsIngestionOutcome.DuplicateIgnored(
                        transactionId = parsed.transactionId,
                        provider = parsed.provider
                    )
                }

                val entity = PaymentEventEntity(
                    merchantId = merchantId,
                    deviceId = deviceId,
                    provider = parsed.provider,
                    transactionType = parsed.transactionType,
                    amount = parsed.amount,
                    currency = parsed.currency,
                    senderNumber = parsed.senderNumber,
                    receiverNumber = verifiedReceiverWallet,
                    transactionId = parsed.transactionId,
                    transactionTime = parsed.transactionTime,
                    reference = parsed.reference,
                    fee = parsed.fee,
                    commission = parsed.commission,
                    balance = parsed.balance,
                    parserVersion = parsed.parserVersion,
                    receivedAt = parsed.receivedAt,
                    syncState = SyncState.LOCAL_PENDING.name,
                    serverStatusMessage = "Queued locally for backend ingestion and verification."
                )

                val rowId = paymentEventDao.insertEventIgnoreDuplicate(entity)
                if (rowId == -1L) {
                    return SmsIngestionOutcome.DuplicateIgnored(
                        transactionId = parsed.transactionId,
                        provider = parsed.provider
                    )
                }

                val savedEntity = entity.copy(id = rowId)
                RedactingLogger.i(
                    "SmsIngestion",
                    "Queued structured event id=$rowId provider=${parsed.provider} trxId=${parsed.transactionId}"
                )
                SmsIngestionOutcome.Queued(localId = rowId, event = savedEntity.toDomain())
            }
        }
    }

    private fun resolveVerifiedReceiverWallet(providerCode: String): String? {
        val profile = cachedMerchantProfile ?: return null
        return profile.configuredWallets.firstOrNull {
            it.provider.equals(providerCode, ignoreCase = true) &&
                it.isConfigured &&
                it.isActive &&
                it.backendVerified
        }?.walletNumber
    }

    override suspend fun syncPendingEvents(): AppResult<SyncBatchSummary> {
        val prefs = preferencesDataStore.preferencesFlow.first()
        if (prefs.deviceAuthStatus == DeviceAuthStatus.REVOKED) {
            return AppResult.Error(AppErrorType.DEVICE_REVOKED)
        }
        val deviceCredential = credentialStore.getDeviceCredential()
        val deviceId = credentialStore.getDeviceId()
        val merchantId = credentialStore.getMerchantId()
        if (prefs.deviceAuthStatus != DeviceAuthStatus.AUTHORIZED ||
            deviceCredential.isNullOrBlank() ||
            deviceId.isNullOrBlank() ||
            merchantId.isNullOrBlank()
        ) {
            return AppResult.Error(AppErrorType.DEVICE_NOT_AUTHORIZED)
        }

        val pendingList = paymentEventDao.getPendingSyncEvents()
        if (pendingList.isEmpty()) {
            val now = System.currentTimeMillis()
            preferencesDataStore.recordSuccessfulSync(now)
            return AppResult.Success(
                SyncBatchSummary(
                    totalAttempted = 0,
                    succeededCount = 0,
                    matchedCount = 0,
                    reviewCount = 0,
                    rejectedCount = 0,
                    failedCount = 0
                )
            )
        }

        var succeededCount = 0
        var matchedCount = 0
        var reviewCount = 0
        var rejectedCount = 0
        var failedCount = 0

        for (entity in pendingList) {
            if (entity.retryCount >= MAX_EVENT_RETRY_ATTEMPTS) {
                paymentEventDao.updateSyncStatus(
                    id = entity.id,
                    newState = SyncState.UPLOAD_FAILED.name,
                    statusMessage = AppErrorType.WORKMANAGER_RETRY_EXHAUSTED.defaultUserMessage,
                    retryCount = entity.retryCount,
                    attemptAt = System.currentTimeMillis(),
                    syncedAt = entity.lastSyncedAt
                )
                failedCount++
                continue
            }

            val attemptTime = System.currentTimeMillis()
            paymentEventDao.updateSyncStatus(
                id = entity.id,
                newState = SyncState.UPLOADING.name,
                statusMessage = "Submitting structured payment event to BDPayHub backend...",
                retryCount = entity.retryCount,
                attemptAt = attemptTime,
                syncedAt = entity.lastSyncedAt
            )

            try {
                val receiverWallet = entity.receiverNumber ?: resolveVerifiedReceiverWallet(entity.provider)
                val requestDto = TransactionIngestRequest(
                    merchantId = entity.merchantId.ifBlank { merchantId },
                    deviceId = entity.deviceId.ifBlank { deviceId },
                    provider = entity.provider,
                    transactionType = entity.transactionType,
                    amount = entity.amount,
                    currency = entity.currency,
                    senderNumber = entity.senderNumber,
                    receiverNumber = receiverWallet,
                    transactionId = entity.transactionId,
                    transactionTime = entity.transactionTime,
                    reference = entity.reference,
                    fee = entity.fee,
                    commission = entity.commission,
                    balance = entity.balance,
                    parserVersion = entity.parserVersion
                )

                val response = backendApiProvider().ingestTransactionEvent(
                    bearerDeviceCredential = "Bearer $deviceCredential",
                    request = requestDto
                )

                when (response.code()) {
                    200, 201 -> {
                        val body = response.body()
                        if (body == null) {
                            paymentEventDao.updateSyncStatus(
                                id = entity.id,
                                newState = SyncState.UPLOAD_FAILED.name,
                                statusMessage = AppErrorType.INVALID_API_RESPONSE.defaultUserMessage,
                                retryCount = entity.retryCount + 1,
                                attemptAt = attemptTime,
                                syncedAt = null
                            )
                            failedCount++
                        } else {
                            // Server response determines processing status; never mark matched on client alone
                            val resolvedState = when (body.processingStatus.uppercase()) {
                                "SERVER_MATCHED" -> SyncState.SERVER_MATCHED
                                "SERVER_REVIEW" -> SyncState.SERVER_REVIEW
                                "SERVER_REJECTED" -> SyncState.SERVER_REJECTED
                                else -> SyncState.SERVER_RECEIVED
                            }
                            paymentEventDao.updateSyncStatus(
                                id = entity.id,
                                newState = resolvedState.name,
                                statusMessage = body.message,
                                retryCount = entity.retryCount,
                                attemptAt = attemptTime,
                                syncedAt = body.processedAtEpochMillis
                            )
                            succeededCount++
                            when (resolvedState) {
                                SyncState.SERVER_MATCHED -> matchedCount++
                                SyncState.SERVER_REVIEW, SyncState.SERVER_RECEIVED -> reviewCount++
                                SyncState.SERVER_REJECTED -> rejectedCount++
                                else -> Unit
                            }
                        }
                    }
                    403 -> {
                        // Device revoked: preserve local event and stop ingestion/sync immediately
                        paymentEventDao.updateSyncStatus(
                            id = entity.id,
                            newState = SyncState.LOCAL_PENDING.name,
                            statusMessage = AppErrorType.DEVICE_REVOKED.defaultUserMessage,
                            retryCount = entity.retryCount,
                            attemptAt = attemptTime,
                            syncedAt = null
                        )
                        revokeCurrentDeviceLocally("Backend returned 403 DEVICE_REVOKED during event ingestion")
                        return AppResult.Error(AppErrorType.DEVICE_REVOKED, httpStatusCode = 403)
                    }
                    429 -> {
                        // Subscription limit reached: preserve local event, do not delete
                        paymentEventDao.updateSyncStatus(
                            id = entity.id,
                            newState = SyncState.UPLOAD_FAILED.name,
                            statusMessage = AppErrorType.SUBSCRIPTION_LIMIT_REACHED.defaultUserMessage,
                            retryCount = entity.retryCount + 1,
                            attemptAt = attemptTime,
                            syncedAt = null
                        )
                        return AppResult.Error(AppErrorType.SUBSCRIPTION_LIMIT_REACHED, httpStatusCode = 429)
                    }
                    401 -> {
                        paymentEventDao.updateSyncStatus(
                            id = entity.id,
                            newState = SyncState.LOCAL_PENDING.name,
                            statusMessage = AppErrorType.DEVICE_NOT_AUTHORIZED.defaultUserMessage,
                            retryCount = entity.retryCount,
                            attemptAt = attemptTime,
                            syncedAt = null
                        )
                        return AppResult.Error(AppErrorType.DEVICE_NOT_AUTHORIZED, httpStatusCode = 401)
                    }
                    else -> {
                        paymentEventDao.updateSyncStatus(
                            id = entity.id,
                            newState = SyncState.UPLOAD_FAILED.name,
                            statusMessage = "Backend error (${response.code()}). Preserved locally for retry.",
                            retryCount = entity.retryCount + 1,
                            attemptAt = attemptTime,
                            syncedAt = null
                        )
                        failedCount++
                    }
                }
            } catch (_: IOException) {
                paymentEventDao.updateSyncStatus(
                    id = entity.id,
                    newState = SyncState.UPLOAD_FAILED.name,
                    statusMessage = AppErrorType.NO_INTERNET.defaultUserMessage,
                    retryCount = entity.retryCount + 1,
                    attemptAt = attemptTime,
                    syncedAt = null
                )
                return AppResult.Error(AppErrorType.NO_INTERNET)
            } catch (_: Exception) {
                paymentEventDao.updateSyncStatus(
                    id = entity.id,
                    newState = SyncState.UPLOAD_FAILED.name,
                    statusMessage = AppErrorType.BACKEND_UNAVAILABLE.defaultUserMessage,
                    retryCount = entity.retryCount + 1,
                    attemptAt = attemptTime,
                    syncedAt = null
                )
                failedCount++
            }
        }

        if (succeededCount > 0) {
            preferencesDataStore.recordSuccessfulSync(System.currentTimeMillis())
        }

        val summary = SyncBatchSummary(
            totalAttempted = pendingList.size,
            succeededCount = succeededCount,
            matchedCount = matchedCount,
            reviewCount = reviewCount,
            rejectedCount = rejectedCount,
            failedCount = failedCount
        )
        return if (failedCount > 0 && succeededCount == 0) {
            AppResult.Error(AppErrorType.BACKEND_UNAVAILABLE)
        } else {
            AppResult.Success(summary)
        }
    }

    override suspend fun getEventById(localId: Long): PaymentEventRecord? {
        return paymentEventDao.getEventById(localId)?.toDomain()
    }

    override fun filterEvents(
        allEvents: List<PaymentEventRecord>,
        filter: TransactionFilter
    ): List<PaymentEventRecord> {
        val now = System.currentTimeMillis()
        val cutoff = filter.dateRange.durationMillis?.let { now - it }
        val query = filter.searchQuery.trim()

        val filtered = allEvents.filter { record ->
            val matchesQuery = query.isEmpty() ||
                record.transactionId.contains(query, ignoreCase = true) ||
                (record.reference?.contains(query, ignoreCase = true) == true) ||
                (record.senderNumber?.contains(query) == true)

            val matchesProvider = filter.provider == null || record.provider == filter.provider
            val matchesType = filter.transactionType == null || record.transactionType == filter.transactionType
            val matchesState = filter.syncState == null || record.syncState == filter.syncState
            val matchesDate = cutoff == null || record.receivedAt >= cutoff

            matchesQuery && matchesProvider && matchesType && matchesState && matchesDate
        }

        val limit = (filter.page.coerceAtLeast(1)) * filter.pageSize
        return filtered.take(limit)
    }

    override suspend fun buildDashboardSummary(
        smsPermissionGranted: Boolean,
        isNetworkAvailable: Boolean
    ): DashboardSummary {
        val prefs = preferencesDataStore.preferencesFlow.first()
        val allEvents = allEventsFlow.first()
        val profile = cachedMerchantProfile

        val matchedCount = allEvents.count { it.syncState == SyncState.SERVER_MATCHED }
        val pendingOrReviewCount = allEvents.count {
            it.syncState == SyncState.SERVER_REVIEW ||
                it.syncState == SyncState.SERVER_RECEIVED ||
                it.syncState == SyncState.LOCAL_PENDING ||
                it.syncState == SyncState.UPLOADING
        }
        val rejectedCount = allEvents.count { it.syncState == SyncState.SERVER_REJECTED }
        val localQueuedCount = allEvents.count {
            it.syncState == SyncState.LOCAL_PENDING || it.syncState == SyncState.UPLOADING
        }
        val failedCount = allEvents.count { it.syncState == SyncState.UPLOAD_FAILED }

        val wallets = profile?.configuredWallets ?: listOf(
            ConfiguredWallet("bkash", "Not verified yet", "PERSONAL", isConfigured = false, isActive = false, backendVerified = false),
            ConfiguredWallet("nagad", "Not verified yet", "PERSONAL", isConfigured = false, isActive = false, backendVerified = false),
            ConfiguredWallet("rocket", "Not verified yet", "PERSONAL", isConfigured = false, isActive = false, backendVerified = false),
            ConfiguredWallet("upay", "Not verified yet", "PERSONAL", isConfigured = false, isActive = false, backendVerified = false)
        )

        return DashboardSummary(
            merchantName = profile?.businessName ?: "Unverified Merchant Account",
            merchantEmail = credentialStore.getSignedInEmail() ?: profile?.googleEmail ?: "Not signed in",
            planName = profile?.planName ?: "Standard Plan",
            deviceStatus = prefs.deviceAuthStatus,
            deviceName = credentialStore.getDeviceName() ?: "${Build.MANUFACTURER} ${Build.MODEL}",
            deviceId = credentialStore.getDeviceId() ?: "Not registered",
            smsPermissionGranted = smsPermissionGranted,
            isNetworkAvailable = isNetworkAvailable,
            lastSyncTimeMillis = prefs.lastSuccessfulSyncMillis,
            totalEventsReceived = allEvents.size,
            matchedVerifiedPayments = matchedCount,
            pendingOrReviewEvents = pendingOrReviewCount,
            rejectedEvents = rejectedCount,
            localQueuedEvents = localQueuedCount,
            uploadFailedEvents = failedCount,
            configuredWallets = wallets,
            isBackendSource = profile != null
        )
    }

    override suspend fun signOut() {
        cachedMerchantProfile = null
        credentialStore.clearAllCredentials()
        preferencesDataStore.resetSessionPreferences()
        RedactingLogger.i("Auth", "Merchant signed out and encrypted Keystore credentials cleared.")
    }

    private fun MerchantProfileResponse.toDomain(): MerchantProfile {
        return MerchantProfile(
            merchantId = merchantId,
            businessName = businessName,
            googleEmail = googleEmail,
            planName = planName,
            maxAllowedDevices = maxAllowedDevices,
            activeDeviceCount = activeDeviceCount,
            monthlyEventQuota = monthlyEventQuota,
            usedEventsCount = usedEventsCount,
            isSubscriptionActive = isSubscriptionActive,
            configuredWallets = configuredWallets.map { it.toDomain() }
        )
    }

    private fun ConfiguredWalletDto.toDomain(): ConfiguredWallet {
        return ConfiguredWallet(
            provider = provider,
            walletNumber = walletNumber,
            accountType = accountType,
            isConfigured = isConfigured,
            isActive = isActive,
            backendVerified = backendVerified
        )
    }

    companion object {
        const val MAX_EVENT_RETRY_ATTEMPTS = 5
    }
}
