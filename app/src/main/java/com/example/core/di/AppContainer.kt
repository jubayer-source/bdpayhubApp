package com.example.core.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.core.config.EnvironmentConfig
import com.example.core.database.BDPayHubDatabase
import com.example.core.datastore.AppPreferencesDataStore
import com.example.core.network.AxumContractFallbackInterceptor
import com.example.core.network.BDPayHubBackendApi
import com.example.core.network.NetworkClientFactory
import com.example.core.network.SupabaseAuthApi
import com.example.core.security.KeystoreCredentialStore
import com.example.core.security.SecureCredentialStore
import com.example.data.repository.BDPayHubRepositoryImpl
import com.example.data.sms.CompositePaymentSmsParser
import com.example.domain.model.AppEnvironment
import com.example.domain.repository.BDPayHubRepository
import com.example.service.NotificationHelper

/**
 * Clean Architecture Dependency Injection container.
 * Provides constructor-injected singletons and allows clean replacement in automated tests.
 */
class AppContainer(
    context: Context,
    customDatabase: BDPayHubDatabase? = null,
    customCredentialStore: SecureCredentialStore? = null
) {
    private val appContext = context.applicationContext

    val preferencesDataStore = AppPreferencesDataStore(appContext)
    val credentialStore: SecureCredentialStore =
        customCredentialStore ?: KeystoreCredentialStore(appContext)
    val database: BDPayHubDatabase =
        customDatabase ?: BDPayHubDatabase.getInstance(appContext)

    val smsParser = CompositePaymentSmsParser()
    val notificationHelper = NotificationHelper(appContext)

    val contractInterceptor = AxumContractFallbackInterceptor(NetworkClientFactory.json)
    private val okHttpClient = NetworkClientFactory.createOkHttpClient(contractInterceptor)

    @Volatile
    private var currentEnvironment: AppEnvironment = AppEnvironment.PRODUCTION

    @Volatile
    private var customApiBaseUrlOverride: String? = null

    private var supabaseAuthApi: SupabaseAuthApi =
        NetworkClientFactory.createSupabaseAuthApi(
            EnvironmentConfig.getSupabaseUrl(),
            okHttpClient
        )

    private var backendApi: BDPayHubBackendApi =
        NetworkClientFactory.createBackendApi(
            EnvironmentConfig.getApiBaseUrl(currentEnvironment),
            okHttpClient
        )

    fun updateEnvironment(environment: AppEnvironment, overrideBaseUrl: String? = null) {
        currentEnvironment = environment
        customApiBaseUrlOverride = overrideBaseUrl
        backendApi = NetworkClientFactory.createBackendApi(
            EnvironmentConfig.getApiBaseUrl(environment, overrideBaseUrl),
            okHttpClient
        )
    }

    fun getActiveApiBaseUrl(): String =
        EnvironmentConfig.getApiBaseUrl(currentEnvironment, customApiBaseUrlOverride)

    fun isNetworkConnected(): Boolean {
        if (contractInterceptor.simulateOfflineMode) return false
        return runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    val repository: BDPayHubRepository = BDPayHubRepositoryImpl(
        supabaseAuthApiProvider = { supabaseAuthApi },
        backendApiProvider = { backendApi },
        paymentEventDao = database.paymentEventDao(),
        credentialStore = credentialStore,
        preferencesDataStore = preferencesDataStore,
        smsParser = smsParser
    )
}
