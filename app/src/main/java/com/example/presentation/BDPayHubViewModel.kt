package com.example.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.AppErrorType
import com.example.core.common.AppResult
import com.example.core.common.RedactingLogger
import com.example.core.datastore.ThemeMode
import com.example.core.datastore.UserPreferencesState
import com.example.core.di.AppContainer
import com.example.data.sms.OfficialSmsSampleCatalog
import com.example.domain.model.AppEnvironment
import com.example.domain.model.DashboardSummary
import com.example.domain.model.DateRangeOption
import com.example.domain.model.DeviceAuthStatus
import com.example.domain.model.MerchantProfile
import com.example.domain.model.ParserDiagnosticEntry
import com.example.domain.model.PaymentEventRecord
import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentTransactionType
import com.example.domain.model.SyncState
import com.example.domain.model.TransactionFilter
import com.example.domain.repository.SmsIngestionOutcome
import com.example.service.DeviceSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab(val route: String, val label: String) {
    DASHBOARD("dashboard", "Dashboard"),
    TRANSACTIONS("transactions", "Transactions"),
    AGENT_PARSER("agent_parser", "Agent & Parser"),
    SETTINGS("settings", "Settings")
}

/**
 * Explicit 6-step Authentication & Onboarding flow defined in Sections 4, 5, and 6.
 */
enum class OnboardingStep {
    WELCOME,
    GOOGLE_SIGN_IN,
    AUTH_LOADING,
    ACCOUNT_VALIDATION,
    NO_MERCHANT_PROFILE,
    DEVICE_AUTHORIZATION,
    SMS_PERMISSION_CONSENT,
    ONBOARDING_SUCCESS
}

data class BDPayHubUiState(
    val isInitialized: Boolean = false,
    val showOnboardingFlow: Boolean = false,
    val onboardingStep: OnboardingStep = OnboardingStep.WELCOME,
    val activeTab: MainTab = MainTab.DASHBOARD,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val bannerMessage: String? = null,
    val bannerErrorType: AppErrorType? = null,
    val parserFeedbackMessage: String? = null,
    val smsPermissionGranted: Boolean = false,
    val isNetworkConnected: Boolean = true,
    val simulateOfflineMode: Boolean = false,
    val simulateSubscriptionLimit: Boolean = false,
    val merchantProfile: MerchantProfile? = null,
    val dashboardSummary: DashboardSummary? = null,
    val filter: TransactionFilter = TransactionFilter(),
    val selectedTransaction: PaymentEventRecord? = null,
    val deviceNameInput: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} Counter Agent"
)

class BDPayHubViewModel(
    private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(BDPayHubUiState())
    val uiState: StateFlow<BDPayHubUiState> = _uiState.asStateFlow()

    val preferencesState: StateFlow<UserPreferencesState> =
        container.preferencesDataStore.preferencesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferencesState()
        )

    val allEvents: StateFlow<List<PaymentEventRecord>> =
        container.repository.allEventsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentEvents: StateFlow<List<PaymentEventRecord>> =
        container.repository.recentEventsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val diagnosticLogs: StateFlow<List<ParserDiagnosticEntry>> =
        container.repository.diagnosticLogsFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredEvents: StateFlow<List<PaymentEventRecord>> =
        combine(allEvents, _uiState) { events, state ->
            container.repository.filterEvents(events, state.filter)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        initializeSessionAndBootstrap()
        observeDashboardUpdates()
    }

    private fun observeDashboardUpdates() {
        viewModelScope.launch {
            combine(
                allEvents,
                preferencesState,
                _uiState
            ) { _, _, state ->
                container.repository.buildDashboardSummary(
                    smsPermissionGranted = state.smsPermissionGranted,
                    isNetworkAvailable = state.isNetworkConnected
                )
            }.collect { summary ->
                _uiState.update {
                    it.copy(
                        dashboardSummary = summary,
                        merchantProfile = container.repository.getCachedMerchantProfile()
                    )
                }
            }
        }
    }

    private fun initializeSessionAndBootstrap() {
        viewModelScope.launch {
            refreshSmsPermissionStatus()
            val existingEvents = container.repository.allEventsFlow.first()
            val hasToken = container.credentialStore.getAccessToken() != null

            if (!hasToken && existingEvents.isEmpty()) {
                // Bootstrap authenticated merchant session & sample verified events on first launch
                // so the merchant dashboard, provider statuses, and reconciliation states are immediately live.
                val authRes = container.repository.authenticateWithGoogle("google_oauth_id_token_merchant")
                if (authRes is AppResult.Success) {
                    container.repository.registerAndAuthorizeDevice(
                        deviceName = _uiState.value.deviceNameInput,
                        androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    )
                    seedInitialVerifiedEvents()
                }
            } else if (hasToken) {
                container.repository.fetchMerchantProfile()
            }

            val summary = container.repository.buildDashboardSummary(
                smsPermissionGranted = _uiState.value.smsPermissionGranted,
                isNetworkAvailable = container.isNetworkConnected()
            )
            _uiState.update {
                it.copy(
                    isInitialized = true,
                    dashboardSummary = summary,
                    merchantProfile = container.repository.getCachedMerchantProfile()
                )
            }
        }
    }

    private suspend fun seedInitialVerifiedEvents() {
        val now = System.currentTimeMillis()
        // Ingest 5 representative official SMS messages through the real local parser & backend pipeline
        val samples = listOf(
            OfficialSmsSampleCatalog.templates[0] to (now - 55 * 60 * 1000L), // bKash P2P -> SERVER_MATCHED
            OfficialSmsSampleCatalog.templates[3] to (now - 42 * 60 * 1000L), // Nagad P2P -> SERVER_MATCHED
            OfficialSmsSampleCatalog.templates[1] to (now - 28 * 60 * 1000L), // bKash Agent Cash-In -> SERVER_REVIEW
            OfficialSmsSampleCatalog.templates[6] to (now - 16 * 60 * 1000L), // Rocket P2P -> SERVER_MATCHED
            OfficialSmsSampleCatalog.templates[9] to (now - 6 * 60 * 1000L)   // upay P2P -> SERVER_MATCHED
        )
        for ((tpl, timestamp) in samples) {
            container.repository.processIncomingSms(
                senderId = tpl.senderId,
                rawMessage = tpl.sampleMessage,
                receivedAt = timestamp
            )
        }
        // Also ingest one rejected event (expired window reference)
        container.repository.processIncomingSms(
            senderId = "bKash",
            rawMessage = "You have received Tk 450.00 from 01799887766. Ref EXPIRED2026. Fee Tk 0.00. Balance Tk 21,400.00. TrxID BK4091827E at 17/11/2026 09:45",
            receivedAt = now - 75 * 60 * 1000L
        )
        container.repository.syncPendingEvents()

        // Also queue 1 fresh local pending event so the offline queue indicator is visible
        container.repository.processIncomingSms(
            senderId = "NAGAD",
            rawMessage = "Cash Out Received. Amount: Tk 4,200.00 Customer: 01755667788 Comm: Tk 16.80 TxnID: NG9938471C Balance: Tk 38,900.00 17/11/2026 11:20",
            receivedAt = now - 90 * 1000L
        )
    }

    fun refreshSmsPermissionStatus() {
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        _uiState.update {
            it.copy(
                smsPermissionGranted = granted,
                isNetworkConnected = container.isNetworkConnected()
            )
        }
    }

    fun onSmsPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            container.preferencesDataStore.setSmsConsentAcknowledged(true)
            _uiState.update { it.copy(smsPermissionGranted = granted) }
            if (!granted) {
                val prefs = preferencesState.value
                container.notificationHelper.notifyPermissionDisabled(prefs.notifyOnSecurityAlerts)
                _uiState.update {
                    it.copy(
                        bannerErrorType = AppErrorType.MISSING_SMS_PERMISSION,
                        bannerMessage = AppErrorType.MISSING_SMS_PERMISSION.defaultUserMessage
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        bannerErrorType = null,
                        bannerMessage = "RECEIVE_SMS permission active. Supported payment notifications will be parsed locally."
                    )
                }
            }
            if (_uiState.value.showOnboardingFlow &&
                _uiState.value.onboardingStep == OnboardingStep.SMS_PERMISSION_CONSENT
            ) {
                _uiState.update { it.copy(onboardingStep = OnboardingStep.ONBOARDING_SUCCESS) }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _uiState.update { it.copy(activeTab = tab, selectedTransaction = null) }
    }

    fun startOnboardingWizard(initialStep: OnboardingStep = OnboardingStep.WELCOME) {
        _uiState.update {
            it.copy(
                showOnboardingFlow = true,
                onboardingStep = initialStep,
                bannerMessage = null,
                bannerErrorType = null
            )
        }
    }

    fun setOnboardingStep(step: OnboardingStep) {
        _uiState.update { it.copy(onboardingStep = step, bannerMessage = null, bannerErrorType = null) }
    }

    fun completeOnboardingWizard() {
        viewModelScope.launch {
            container.preferencesDataStore.setOnboardingCompleted(true)
            _uiState.update {
                it.copy(
                    showOnboardingFlow = false,
                    activeTab = MainTab.DASHBOARD
                )
            }
        }
    }

    fun signInWithGoogleAccount(simulateUnregisteredMerchant: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    onboardingStep = OnboardingStep.AUTH_LOADING,
                    bannerMessage = null,
                    bannerErrorType = null
                )
            }
            val tokenPayload = if (simulateUnregisteredMerchant) {
                "google_id_token_unregistered_account"
            } else {
                "google_id_token_verified_merchant"
            }

            _uiState.update { it.copy(onboardingStep = OnboardingStep.ACCOUNT_VALIDATION) }
            when (val result = container.repository.authenticateWithGoogle(tokenPayload)) {
                is AppResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            merchantProfile = result.data,
                            onboardingStep = OnboardingStep.DEVICE_AUTHORIZATION
                        )
                    }
                }
                is AppResult.Error -> {
                    if (result.errorType == AppErrorType.NO_MERCHANT_PROFILE) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                onboardingStep = OnboardingStep.NO_MERCHANT_PROFILE,
                                bannerErrorType = result.errorType,
                                bannerMessage = result.userMessage
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                onboardingStep = OnboardingStep.GOOGLE_SIGN_IN,
                                bannerErrorType = result.errorType,
                                bannerMessage = result.userMessage
                            )
                        }
                    }
                }
            }
        }
    }

    fun updateDeviceNameInput(name: String) {
        _uiState.update { it.copy(deviceNameInput = name) }
    }

    fun registerCurrentDevice() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, bannerMessage = null, bannerErrorType = null) }
            val result = container.repository.registerAndAuthorizeDevice(
                deviceName = _uiState.value.deviceNameInput,
                androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            )
            when (result) {
                is AppResult.Success -> {
                    val prefs = preferencesState.value
                    container.notificationHelper.notifyDeviceAuthorized(
                        deviceName = result.data.deviceName,
                        enabled = prefs.notifyOnSecurityAlerts
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            onboardingStep = OnboardingStep.SMS_PERMISSION_CONSENT,
                            bannerMessage = "Device '${result.data.deviceName}' registered & encrypted credential stored in Android Keystore."
                        )
                    }
                }
                is AppResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            bannerErrorType = result.errorType,
                            bannerMessage = result.userMessage
                        )
                    }
                }
            }
        }
    }

    fun triggerManualSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, bannerMessage = null, bannerErrorType = null) }
            when (val result = container.repository.syncPendingEvents()) {
                is AppResult.Success -> {
                    val s = result.data
                    val msg = if (s.totalAttempted == 0) {
                        "All payment events are already synchronized with the BDPayHub backend."
                    } else {
                        "Synchronized ${s.succeededCount}/${s.totalAttempted} event(s) (${s.matchedCount} verified, ${s.reviewCount} pending review, ${s.rejectedCount} rejected)."
                    }
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            bannerMessage = msg,
                            bannerErrorType = null
                        )
                    }
                }
                is AppResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            bannerErrorType = result.errorType,
                            bannerMessage = result.userMessage
                        )
                    }
                }
            }
        }
    }

    fun testLocalSmsIngestion(senderId: String, rawMessage: String, autoSync: Boolean = true) {
        viewModelScope.launch {
            when (val outcome = container.repository.processIncomingSms(senderId, rawMessage)) {
                is SmsIngestionOutcome.Queued -> {
                    _uiState.update {
                        it.copy(
                            parserFeedbackMessage = "Parsed & queued ${outcome.event.provider.displayName} ${outcome.event.transactionType.displayName} (TrxID: ${outcome.event.transactionId}, BDT ${outcome.event.amount}) -> LOCAL_PENDING"
                        )
                    }
                    if (autoSync && container.isNetworkConnected()) {
                        DeviceSyncWorker.enqueueOneTimeSync(appContext)
                        triggerManualSync()
                    }
                }
                is SmsIngestionOutcome.DuplicateIgnored -> {
                    _uiState.update {
                        it.copy(
                            parserFeedbackMessage = "Idempotent Duplicate Blocked: TrxID ${outcome.transactionId} (${outcome.provider.uppercase()}) already exists in local queue."
                        )
                    }
                }
                is SmsIngestionOutcome.UnrecognizedFormatLogged -> {
                    _uiState.update {
                        it.copy(
                            parserFeedbackMessage = "Unrecognized SMS format from ${outcome.senderId}: ${outcome.reason}. Logged to local diagnostics without raw SMS."
                        )
                    }
                }
                is SmsIngestionOutcome.UnrelatedSmsIgnored -> {
                    _uiState.update {
                        it.copy(
                            parserFeedbackMessage = "Ignored unrelated personal SMS from '$senderId'. Zero data stored or uploaded."
                        )
                    }
                }
                is SmsIngestionOutcome.RejectedByDeviceState -> {
                    _uiState.update {
                        it.copy(
                            parserFeedbackMessage = "Ingestion blocked (${outcome.status.displayName}): ${outcome.message}",
                            bannerErrorType = if (outcome.status == DeviceAuthStatus.REVOKED) {
                                AppErrorType.DEVICE_REVOKED
                            } else {
                                AppErrorType.DEVICE_NOT_AUTHORIZED
                            },
                            bannerMessage = outcome.message
                        )
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(filter = it.filter.copy(searchQuery = query, page = 1)) }
    }

    fun updateProviderFilter(provider: PaymentProvider?) {
        _uiState.update { it.copy(filter = it.filter.copy(provider = provider, page = 1)) }
    }

    fun updateTransactionTypeFilter(type: PaymentTransactionType?) {
        _uiState.update { it.copy(filter = it.filter.copy(transactionType = type, page = 1)) }
    }

    fun updateSyncStateFilter(state: SyncState?) {
        _uiState.update { it.copy(filter = it.filter.copy(syncState = state, page = 1)) }
    }

    fun updateDateRangeFilter(range: DateRangeOption) {
        _uiState.update { it.copy(filter = it.filter.copy(dateRange = range, page = 1)) }
    }

    fun clearAllFilters() {
        _uiState.update { it.copy(filter = TransactionFilter()) }
    }

    fun loadNextPage() {
        _uiState.update { it.copy(filter = it.filter.copy(page = it.filter.page + 1)) }
    }

    fun selectTransaction(record: PaymentEventRecord?) {
        _uiState.update { it.copy(selectedTransaction = record) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            container.preferencesDataStore.setThemeMode(mode)
        }
    }

    fun setEnvironment(environment: AppEnvironment) {
        viewModelScope.launch {
            container.preferencesDataStore.setEnvironment(environment)
            container.updateEnvironment(environment)
            _uiState.update {
                it.copy(
                    bannerMessage = "Switched API environment to ${environment.label} (${container.getActiveApiBaseUrl()})"
                )
            }
        }
    }

    fun setNotificationPreferences(
        syncSuccess: Boolean,
        syncFailure: Boolean,
        securityAlerts: Boolean
    ) {
        viewModelScope.launch {
            container.preferencesDataStore.setNotificationPreferences(
                syncSuccess = syncSuccess,
                syncFailure = syncFailure,
                securityAlerts = securityAlerts
            )
        }
    }

    fun toggleSimulatedOfflineMode(offline: Boolean) {
        container.contractInterceptor.simulateOfflineMode = offline
        _uiState.update {
            it.copy(
                simulateOfflineMode = offline,
                isNetworkConnected = !offline,
                bannerErrorType = if (offline) AppErrorType.NO_INTERNET else null,
                bannerMessage = if (offline) {
                    AppErrorType.NO_INTERNET.defaultUserMessage
                } else {
                    "Network connectivity restored. Synchronizing offline queue..."
                }
            )
        }
        if (!offline) {
            triggerManualSync()
        }
    }

    fun toggleSimulatedSubscriptionLimit(limitReached: Boolean) {
        container.contractInterceptor.simulateSubscriptionLimitReached = limitReached
        _uiState.update {
            it.copy(
                simulateSubscriptionLimit = limitReached,
                bannerErrorType = if (limitReached) AppErrorType.SUBSCRIPTION_LIMIT_REACHED else null,
                bannerMessage = if (limitReached) {
                    AppErrorType.SUBSCRIPTION_LIMIT_REACHED.defaultUserMessage
                } else {
                    "Subscription quota restored."
                }
            )
        }
    }

    fun simulateDeviceRevocation() {
        viewModelScope.launch {
            container.contractInterceptor.simulateDeviceRevoked = true
            container.repository.revokeCurrentDeviceLocally("Revoked from Merchant Security Controls")
            DeviceSyncWorker.cancelAllSyncWork(appContext)
            container.notificationHelper.notifyDeviceRevoked(preferencesState.value.notifyOnSecurityAlerts)
            _uiState.update {
                it.copy(
                    bannerErrorType = AppErrorType.DEVICE_REVOKED,
                    bannerMessage = AppErrorType.DEVICE_REVOKED.defaultUserMessage
                )
            }
        }
    }

    fun reauthorizeRevokedDevice() {
        viewModelScope.launch {
            container.contractInterceptor.simulateDeviceRevoked = false
            registerCurrentDevice()
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(bannerMessage = null, bannerErrorType = null) }
    }

    fun signOut() {
        viewModelScope.launch {
            DeviceSyncWorker.cancelAllSyncWork(appContext)
            container.repository.signOut()
            _uiState.update {
                it.copy(
                    showOnboardingFlow = true,
                    onboardingStep = OnboardingStep.WELCOME,
                    activeTab = MainTab.DASHBOARD,
                    selectedTransaction = null,
                    bannerMessage = "Signed out and cleared encrypted Keystore credentials."
                )
            }
        }
    }

    class Factory(
        private val appContext: Context,
        private val container: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BDPayHubViewModel(appContext, container) as T
        }
    }
}
