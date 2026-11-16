package com.example.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.config.EnvironmentConfig
import com.example.core.datastore.ThemeMode
import com.example.core.datastore.UserPreferencesState
import com.example.domain.model.AppEnvironment
import com.example.domain.model.DashboardSummary
import com.example.presentation.components.DeviceAuthBadge
import com.example.presentation.components.formatTimestampShort

/**
 * Settings and Device Management Screen (Section 15).
 *
 * Contains:
 * - Merchant account information & Signed-in Google account
 * - Device name & Device authorization status
 * - SMS permission status & System Settings shortcut
 * - Device synchronization action
 * - Environment selector (Development, Staging, Production)
 * - Notification settings & Theme settings (Light, Dark, System)
 * - App version, Privacy information, Terms of Service, Support/Contact information, and Sign out
 */
@Composable
fun SettingsScreen(
    summary: DashboardSummary?,
    preferences: UserPreferencesState,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onEnvironmentChange: (AppEnvironment) -> Unit,
    onNotificationPreferencesChange: (Boolean, Boolean, Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }

    if (showPrivacyDialog) {
        InfoModalDialog(
            title = "Privacy & SMS Data Handling Policy",
            body = """
                1. Local-Only Parsing: BDPayHub parses incoming notifications strictly on-device.
                2. Supported Senders Only: Only messages from 'bKash', 'NAGAD', '16216' (Rocket), and 'upay' matching verified payment structures are processed.
                3. Zero Raw SMS Upload: Raw SMS bodies and personal conversations are never stored in the database or uploaded to BDPayHub servers.
                4. Structured Fields Sent: Only Provider, Transaction Type, Amount, Sender/Agent Number, Verified Receiver Wallet, TrxID, Timestamp, Fee/Commission, Balance, and Parser Version are transmitted over HTTPS TLS.
                5. Keystore Security: Supabase JWT sessions and device credentials are encrypted with Android Keystore AES-256-GCM.
            """.trimIndent(),
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showTermsDialog) {
        InfoModalDialog(
            title = "BDPayHub Terms of Service",
            body = """
                1. Non-Custodial Agent: BDPayHub does not receive, hold, transfer, or settle customer funds. Customer payments go directly to the merchant's wallet.
                2. Independent Verification: An SMS notification alone does not constitute cryptographic proof of payment. All transactions require server-side verification by the BDPayHub backend.
                3. Device Limits & Revocation: Device authorization is governed by the merchant's active subscription plan and may be revoked at any time from the BDPayHub web dashboard.
                4. Google Play & Enterprise Distribution: SMS permissions are subject to Android platform and distribution policies.
            """.trimIndent(),
            onDismiss = { showTermsDialog = false }
        )
    }

    if (showSupportDialog) {
        InfoModalDialog(
            title = "BDPayHub Merchant Support",
            body = """
                Merchant Support Portal: https://bdpayhub.com/support
                Security & Compliance Desk: security@bdpayhub.com
                Documentation & OpenAPI Spec: https://docs.bdpayhub.com
                Operating Hours: 24/7 Infrastructure Monitoring (Bangladesh Standard Time GMT+6)
            """.trimIndent(),
            onDismiss = { showSupportDialog = false }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_list")
    ) {
        item {
            Text(
                text = "Settings & Device Management",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Merchant & Google Account Card
        item {
            SettingsCard(
                title = "Merchant Account & Google OAuth",
                icon = Icons.Default.AccountCircle
            ) {
                SettingsRow("Merchant Business", summary?.merchantName ?: "Unverified")
                SettingsRow("Signed-In Google Account", summary?.merchantEmail ?: "Not signed in")
                SettingsRow("Subscription Plan", summary?.planName ?: "Standard Plan")
            }
        }

        // Device Management & Synchronization Card
        item {
            SettingsCard(
                title = "Authorized Device & Synchronization",
                icon = Icons.Default.Devices
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Authorization Status",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (summary != null) {
                        DeviceAuthBadge(status = summary.deviceStatus)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                SettingsRow("Device Name", summary?.deviceName ?: "Android Agent")
                SettingsRow("Device ID", summary?.deviceId ?: "Unregistered")
                SettingsRow("Last Successful Sync", formatTimestampShort(summary?.lastSyncTimeMillis))

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_sync_now_button")
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSyncing) "Synchronizing with Backend..." else "Synchronize Device Queue Now")
                }
            }
        }

        // SMS Permission & System Settings Card
        item {
            SettingsCard(
                title = "SMS Permission & Android System Settings",
                icon = Icons.Default.Security
            ) {
                SettingsRow(
                    "RECEIVE_SMS Permission",
                    if (summary?.smsPermissionGranted == true) "Granted" else "Disabled"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (summary?.smsPermissionGranted != true) {
                        Button(
                            onClick = onRequestSmsPermission,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_request_sms_button")
                        ) {
                            Text("Request Permission")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                ).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_system_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsApplications,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Android Settings")
                    }
                }
            }
        }

        // Theme & Environment Configuration Card
        item {
            SettingsCard(
                title = "Appearance & Environment Configuration",
                icon = Icons.Default.DarkMode
            ) {
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("theme_chip_${mode.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Backend API Environment",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AppEnvironment.entries.forEach { env ->
                        FilterChip(
                            selected = preferences.environment == env,
                            onClick = { onEnvironmentChange(env) },
                            label = { Text(env.label) },
                            modifier = Modifier.testTag("env_chip_${env.name}")
                        )
                    }
                }
            }
        }

        // Notification Settings Card
        item {
            SettingsCard(
                title = "Notification Preferences (Lockscreen Redacted)",
                icon = Icons.Default.Notifications
            ) {
                NotificationToggleRow(
                    label = "Payment Event Synchronized",
                    checked = preferences.notifyOnSyncSuccess,
                    onCheckedChange = {
                        onNotificationPreferencesChange(
                            it,
                            preferences.notifyOnSyncFailure,
                            preferences.notifyOnSecurityAlerts
                        )
                    }
                )
                NotificationToggleRow(
                    label = "Synchronization Failures & Retries",
                    checked = preferences.notifyOnSyncFailure,
                    onCheckedChange = {
                        onNotificationPreferencesChange(
                            preferences.notifyOnSyncSuccess,
                            it,
                            preferences.notifyOnSecurityAlerts
                        )
                    }
                )
                NotificationToggleRow(
                    label = "Device Security, Revocation & Session Alerts",
                    checked = preferences.notifyOnSecurityAlerts,
                    onCheckedChange = {
                        onNotificationPreferencesChange(
                            preferences.notifyOnSyncSuccess,
                            preferences.notifyOnSyncFailure,
                            it
                        )
                    }
                )
            }
        }

        // Legal, Privacy, Support & Version Card
        item {
            SettingsCard(
                title = "Privacy, Compliance & Support",
                icon = Icons.Default.PrivacyTip
            ) {
                SettingsRow("Application Version", "v${EnvironmentConfig.APP_VERSION}")
                SettingsRow("SMS Parser Engine Version", "v${EnvironmentConfig.PARSER_VERSION}")
                SettingsRow("Credential Storage", "Android Keystore AES-256-GCM")

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showPrivacyDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("privacy_policy_button")
                    ) {
                        Text("Privacy Policy")
                    }
                    OutlinedButton(
                        onClick = { showTermsDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("terms_of_service_button")
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Terms")
                    }
                    OutlinedButton(
                        onClick = { showSupportDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("support_contact_button")
                    ) {
                        Icon(Icons.Default.SupportAgent, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Support")
                    }
                }
            }
        }

        // Sign Out Button
        item {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out & Clear Keystore Session")
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun NotificationToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoModalDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("modal_close_button")
            ) {
                Text("Close")
            }
        }
    )
}
