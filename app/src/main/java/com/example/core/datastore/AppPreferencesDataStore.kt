package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AppEnvironment
import com.example.domain.model.DeviceAuthStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bdPayHubDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bdpayhub_user_preferences"
)

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

data class UserPreferencesState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val environment: AppEnvironment = AppEnvironment.PRODUCTION,
    val onboardingCompleted: Boolean = false,
    val smsConsentAcknowledged: Boolean = false,
    val deviceAuthStatus: DeviceAuthStatus = DeviceAuthStatus.UNREGISTERED,
    val lastSuccessfulSyncMillis: Long? = null,
    val lastActiveMillis: Long? = null,
    val notifyOnSyncSuccess: Boolean = true,
    val notifyOnSyncFailure: Boolean = true,
    val notifyOnSecurityAlerts: Boolean = true
)

class AppPreferencesDataStore(private val context: Context) {

    val preferencesFlow: Flow<UserPreferencesState> = context.bdPayHubDataStore.data.map { prefs ->
        UserPreferencesState(
            themeMode = prefs[KEY_THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.DARK,
            environment = prefs[KEY_ENVIRONMENT]?.let {
                runCatching { AppEnvironment.valueOf(it) }.getOrNull()
            } ?: AppEnvironment.PRODUCTION,
            onboardingCompleted = prefs[KEY_ONBOARDING_COMPLETED] ?: false,
            smsConsentAcknowledged = prefs[KEY_SMS_CONSENT_ACKNOWLEDGED] ?: false,
            deviceAuthStatus = prefs[KEY_DEVICE_AUTH_STATUS]?.let {
                runCatching { DeviceAuthStatus.valueOf(it) }.getOrNull()
            } ?: DeviceAuthStatus.UNREGISTERED,
            lastSuccessfulSyncMillis = prefs[KEY_LAST_SYNC_MILLIS],
            lastActiveMillis = prefs[KEY_LAST_ACTIVE_MILLIS],
            notifyOnSyncSuccess = prefs[KEY_NOTIFY_SYNC_SUCCESS] ?: true,
            notifyOnSyncFailure = prefs[KEY_NOTIFY_SYNC_FAILURE] ?: true,
            notifyOnSecurityAlerts = prefs[KEY_NOTIFY_SECURITY_ALERTS] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.bdPayHubDataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setEnvironment(environment: AppEnvironment) {
        context.bdPayHubDataStore.edit { it[KEY_ENVIRONMENT] = environment.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.bdPayHubDataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setSmsConsentAcknowledged(acknowledged: Boolean) {
        context.bdPayHubDataStore.edit { it[KEY_SMS_CONSENT_ACKNOWLEDGED] = acknowledged }
    }

    suspend fun setDeviceAuthStatus(status: DeviceAuthStatus) {
        context.bdPayHubDataStore.edit {
            it[KEY_DEVICE_AUTH_STATUS] = status.name
            it[KEY_LAST_ACTIVE_MILLIS] = System.currentTimeMillis()
        }
    }

    suspend fun recordSuccessfulSync(timestampMillis: Long = System.currentTimeMillis()) {
        context.bdPayHubDataStore.edit {
            it[KEY_LAST_SYNC_MILLIS] = timestampMillis
            it[KEY_LAST_ACTIVE_MILLIS] = timestampMillis
        }
    }

    suspend fun setNotificationPreferences(
        syncSuccess: Boolean,
        syncFailure: Boolean,
        securityAlerts: Boolean
    ) {
        context.bdPayHubDataStore.edit {
            it[KEY_NOTIFY_SYNC_SUCCESS] = syncSuccess
            it[KEY_NOTIFY_SYNC_FAILURE] = syncFailure
            it[KEY_NOTIFY_SECURITY_ALERTS] = securityAlerts
        }
    }

    suspend fun resetSessionPreferences() {
        context.bdPayHubDataStore.edit {
            it[KEY_ONBOARDING_COMPLETED] = false
            it[KEY_DEVICE_AUTH_STATUS] = DeviceAuthStatus.UNREGISTERED.name
        }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ENVIRONMENT = stringPreferencesKey("app_environment")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_SMS_CONSENT_ACKNOWLEDGED = booleanPreferencesKey("sms_consent_acknowledged")
        private val KEY_DEVICE_AUTH_STATUS = stringPreferencesKey("device_auth_status")
        private val KEY_LAST_SYNC_MILLIS = longPreferencesKey("last_sync_millis")
        private val KEY_LAST_ACTIVE_MILLIS = longPreferencesKey("last_active_millis")
        private val KEY_NOTIFY_SYNC_SUCCESS = booleanPreferencesKey("notify_sync_success")
        private val KEY_NOTIFY_SYNC_FAILURE = booleanPreferencesKey("notify_sync_failure")
        private val KEY_NOTIFY_SECURITY_ALERTS = booleanPreferencesKey("notify_security_alerts")
    }
}
