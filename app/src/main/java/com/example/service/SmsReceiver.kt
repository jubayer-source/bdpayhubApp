package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.BDPayHubApplication
import com.example.core.common.RedactingLogger
import com.example.domain.repository.SmsIngestionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Event-driven BroadcastReceiver for incoming SMS messages (`SMS_RECEIVED_ACTION`).
 *
 * Security & Privacy Guarantees:
 * - Never logs or uploads raw SMS bodies.
 * - Ignores all personal and non-payment SMS messages locally.
 * - Requires explicit user consent and an AUTHORIZED device state.
 * - Delegates parsing and local persistence to `BDPayHubRepository`, then schedules
 *   `DeviceSyncWorker` through WorkManager when a recognized payment event is queued.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val senderId = messages.firstOrNull()?.displayOriginatingAddress?.trim() ?: return
        val fullMessageBody = buildString {
            for (sms in messages) {
                append(sms.displayMessageBody ?: "")
            }
        }
        if (fullMessageBody.isBlank()) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = BDPayHubApplication.getContainer(appContext)
                when (val outcome = container.repository.processIncomingSms(senderId, fullMessageBody)) {
                    is SmsIngestionOutcome.Queued -> {
                        DeviceSyncWorker.enqueueOneTimeSync(appContext)
                    }
                    is SmsIngestionOutcome.DuplicateIgnored -> {
                        RedactingLogger.i(
                            "SmsReceiver",
                            "Duplicate payment event ignored locally: provider=${outcome.provider} trxId=${outcome.transactionId}"
                        )
                    }
                    is SmsIngestionOutcome.UnrecognizedFormatLogged -> {
                        RedactingLogger.w(
                            "SmsReceiver",
                            "Unrecognized format from known sender=${outcome.senderId} logged to diagnostics."
                        )
                    }
                    is SmsIngestionOutcome.UnrelatedSmsIgnored -> {
                        // Completely ignore personal/unrelated SMS without logging or notifying
                    }
                    is SmsIngestionOutcome.RejectedByDeviceState -> {
                        RedactingLogger.w(
                            "SmsReceiver",
                            "Skipped SMS processing because device status is ${outcome.status}"
                        )
                    }
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
