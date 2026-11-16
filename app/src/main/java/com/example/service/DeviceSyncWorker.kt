package com.example.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.BDPayHubApplication
import com.example.core.common.AppErrorType
import com.example.core.common.AppResult
import com.example.core.common.RedactingLogger
import com.example.domain.model.DeviceAuthStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Reliable WorkManager synchronization worker (Section 12 & 19).
 *
 * - Runs only when internet connectivity is available (`NetworkType.CONNECTED`).
 * - Uses unique work name (`UNIQUE_WORK_NAME`) with `ExistingWorkPolicy.KEEP` to prevent duplicate jobs.
 * - Uses exponential backoff for temporary network/backend failures.
 * - Enforces a maximum retry ceiling (`MAX_WORKER_ATTEMPTS = 5`) to prevent infinite retry loops.
 * - Immediately stops background sync if the device is revoked (`DeviceAuthStatus.REVOKED`).
 */
class DeviceSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = BDPayHubApplication.getContainer(applicationContext)
        val prefs = container.preferencesDataStore.preferencesFlow.first()

        if (prefs.deviceAuthStatus == DeviceAuthStatus.REVOKED) {
            RedactingLogger.w("DeviceSyncWorker", "Halting background sync: Device is REVOKED.")
            container.notificationHelper.notifyDeviceRevoked(prefs.notifyOnSecurityAlerts)
            return Result.failure()
        }

        if (prefs.deviceAuthStatus != DeviceAuthStatus.AUTHORIZED) {
            RedactingLogger.w("DeviceSyncWorker", "Skipping background sync: Device not authorized.")
            return Result.failure()
        }

        if (runAttemptCount >= MAX_WORKER_ATTEMPTS) {
            RedactingLogger.w("DeviceSyncWorker", "Retry limit ($MAX_WORKER_ATTEMPTS) reached; stopping retry loop.")
            container.notificationHelper.notifySyncFailure(
                reason = AppErrorType.WORKMANAGER_RETRY_EXHAUSTED.defaultUserMessage,
                enabled = prefs.notifyOnSyncFailure
            )
            return Result.failure()
        }

        return when (val outcome = container.repository.syncPendingEvents()) {
            is AppResult.Success -> {
                val summary = outcome.data
                if (summary.succeededCount > 0) {
                    container.notificationHelper.notifyEventSynced(
                        count = summary.succeededCount,
                        matchedCount = summary.matchedCount,
                        enabled = prefs.notifyOnSyncSuccess
                    )
                }
                Result.success()
            }
            is AppResult.Error -> {
                when (outcome.errorType) {
                    AppErrorType.DEVICE_REVOKED -> {
                        container.notificationHelper.notifyDeviceRevoked(prefs.notifyOnSecurityAlerts)
                        Result.failure()
                    }
                    AppErrorType.AUTH_EXPIRED -> {
                        container.notificationHelper.notifySessionExpired(prefs.notifyOnSecurityAlerts)
                        Result.failure()
                    }
                    AppErrorType.SUBSCRIPTION_LIMIT_REACHED -> {
                        container.notificationHelper.notifySyncFailure(
                            reason = outcome.userMessage,
                            enabled = prefs.notifyOnSyncFailure
                        )
                        Result.failure()
                    }
                    AppErrorType.NO_INTERNET,
                    AppErrorType.BACKEND_UNAVAILABLE,
                    AppErrorType.INVALID_API_RESPONSE -> {
                        if (runAttemptCount + 1 >= MAX_WORKER_ATTEMPTS) {
                            container.notificationHelper.notifySyncFailure(
                                reason = AppErrorType.WORKMANAGER_RETRY_EXHAUSTED.defaultUserMessage,
                                enabled = prefs.notifyOnSyncFailure
                            )
                            Result.failure()
                        } else {
                            Result.retry()
                        }
                    }
                    else -> Result.failure()
                }
            }
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "bdpayhub_unique_payment_event_sync"
        const val MAX_WORKER_ATTEMPTS = 5

        fun enqueueOneTimeSync(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<DeviceSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        15L,
                        TimeUnit.SECONDS
                    )
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            }
        }

        fun cancelAllSyncWork(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext)
                    .cancelUniqueWork(UNIQUE_WORK_NAME)
            }
        }
    }
}
