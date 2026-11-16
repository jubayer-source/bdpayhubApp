package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R

/**
 * Privacy-preserving notification helper (Section 17).
 *
 * Guarantees:
 * - Never creates a notification for unrelated SMS messages.
 * - Uses VISIBILITY_PRIVATE so customer numbers or sensitive details are never shown on the lock screen.
 * - Respects user preferences for non-essential notifications.
 */
class NotificationHelper(private val context: Context) {

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val syncChannel = NotificationChannel(
                CHANNEL_SYNC,
                context.getString(R.string.notif_channel_sync_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Payment event synchronization status updates"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            val securityChannel = NotificationChannel(
                CHANNEL_SECURITY,
                context.getString(R.string.notif_channel_security_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Device authorization, revocation, permission, and session alerts"
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            manager.createNotificationChannel(syncChannel)
            manager.createNotificationChannel(securityChannel)
        }
    }

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notifyDeviceAuthorized(deviceName: String, enabled: Boolean = true) {
        if (!enabled || !canPostNotifications()) return
        postNotification(
            channelId = CHANNEL_SECURITY,
            notificationId = ID_DEVICE_AUTH,
            title = "Device Authorized",
            body = "$deviceName is now registered and authorized for BDPayHub event synchronization."
        )
    }

    fun notifyDeviceRevoked(enabled: Boolean = true) {
        if (!enabled || !canPostNotifications()) return
        postNotification(
            channelId = CHANNEL_SECURITY,
            notificationId = ID_DEVICE_REVOKED,
            title = "Device Authorization Revoked",
            body = "Payment event ingestion and background synchronization have been stopped."
        )
    }

    fun notifyEventSynced(count: Int, matchedCount: Int, enabled: Boolean = true) {
        if (!enabled || !canPostNotifications() || count <= 0) return
        postNotification(
            channelId = CHANNEL_SYNC,
            notificationId = ID_SYNC_SUCCESS,
            title = "Payment Events Synchronized",
            body = "$count event(s) synchronized with BDPayHub ($matchedCount verified by server)."
        )
    }

    fun notifySyncFailure(reason: String, enabled: Boolean = true) {
        if (!enabled || !canPostNotifications()) return
        postNotification(
            channelId = CHANNEL_SYNC,
            notificationId = ID_SYNC_FAILURE,
            title = "Synchronization Attention Required",
            body = reason
        )
    }

    fun notifyPermissionDisabled(enabled: Boolean = true) {
        if (!enabled || !canPostNotifications()) return
        postNotification(
            channelId = CHANNEL_SECURITY,
            notificationId = ID_PERMISSION_DISABLED,
            title = "SMS Permission Disabled",
            body = "Grant RECEIVE_SMS permission so BDPayHub can capture payment notifications."
        )
    }

    fun notifySessionExpired(enabled: Boolean = true) {
        if (!enabled || !canPostNotifications()) return
        postNotification(
            channelId = CHANNEL_SECURITY,
            notificationId = ID_SESSION_EXPIRED,
            title = "Merchant Session Expired",
            body = "Sign in with Google to renew your BDPayHub merchant session."
        )
    }

    private fun postNotification(
        channelId: String,
        notificationId: Int,
        title: String,
        body: String
    ) {
        runCatching {
            val publicNotification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("BDPayHub")
                .setContentText("Open BDPayHub to view payment agent status.")
                .build()

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicNotification)
                .setAutoCancel(true)

            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        }
    }

    companion object {
        const val CHANNEL_SYNC = "bdpayhub_sync_channel"
        const val CHANNEL_SECURITY = "bdpayhub_security_channel"

        private const val ID_DEVICE_AUTH = 1001
        private const val ID_DEVICE_REVOKED = 1002
        private const val ID_SYNC_SUCCESS = 1003
        private const val ID_SYNC_FAILURE = 1004
        private const val ID_PERMISSION_DISABLED = 1005
        private const val ID_SESSION_EXPIRED = 1006
    }
}
