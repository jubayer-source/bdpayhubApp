package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.BDPayHubApplication
import com.example.core.common.RedactingLogger
import com.example.domain.model.DeviceAuthStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reschedules WorkManager synchronization after a device reboot (`ACTION_BOOT_COMPLETED`)
 * if the device is authorized and has pending payment events in the Room queue.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = BDPayHubApplication.getContainer(appContext)
                val prefs = container.preferencesDataStore.preferencesFlow.first()
                if (prefs.deviceAuthStatus == DeviceAuthStatus.AUTHORIZED) {
                    val pendingEvents = container.database.paymentEventDao().getPendingSyncEvents()
                    if (pendingEvents.isNotEmpty()) {
                        RedactingLogger.i(
                            "BootReceiver",
                            "Rescheduling WorkManager sync for ${pendingEvents.size} pending events after reboot."
                        )
                        DeviceSyncWorker.enqueueOneTimeSync(appContext)
                    }
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
