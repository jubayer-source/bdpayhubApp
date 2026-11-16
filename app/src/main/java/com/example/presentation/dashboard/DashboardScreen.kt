package com.example.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.config.EnvironmentConfig
import com.example.domain.model.ConfiguredWallet
import com.example.domain.model.DashboardSummary
import com.example.domain.model.PaymentEventRecord
import com.example.domain.model.PaymentProvider
import com.example.presentation.components.DeviceAuthBadge
import com.example.presentation.components.NonCustodialTrustBanner
import com.example.presentation.components.TransactionEventCard
import com.example.presentation.components.formatTimestampShort
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusPendingBlue
import com.example.ui.theme.StatusRejectedRed
import com.example.ui.theme.StatusReviewAmber
import com.example.ui.theme.StatusVerifiedGreen

/**
 * Main BDPayHub Merchant Dashboard (Section 13).
 *
 * Displays:
 * - Merchant/business name & tagline "Every Payment. Verified."
 * - Device authorization status, SMS permission status, Internet connection status, Last sync time
 * - Backend vs Local synchronization statistics (never invents financial totals)
 * - Configured provider statuses (bKash, Nagad, Rocket, upay) confirmed by backend
 * - Device health summary & Recent payment event activity
 */
@Composable
fun DashboardScreen(
    summary: DashboardSummary?,
    recentEvents: List<PaymentEventRecord>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onRequestSmsPermission: () -> Unit,
    onOpenTransactionHistory: () -> Unit,
    onOpenParserSandbox: () -> Unit,
    onOpenOnboardingWizard: () -> Unit,
    onSelectEvent: (PaymentEventRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen_list")
    ) {
        item {
            MerchantHeaderCard(
                summary = summary,
                isSyncing = isSyncing,
                onSyncNow = onSyncNow,
                onOpenOnboardingWizard = onOpenOnboardingWizard
            )
        }

        item {
            NonCustodialTrustBanner()
        }

        item {
            SystemHealthGrid(
                summary = summary,
                onRequestSmsPermission = onRequestSmsPermission
            )
        }

        item {
            ReconciliationMetricsSection(
                summary = summary,
                onOpenParserSandbox = onOpenParserSandbox
            )
        }

        item {
            ProviderWalletsSection(
                wallets = summary?.configuredWallets.orEmpty()
            )
        }

        item {
            DeviceHealthCard(
                summary = summary,
                isSyncing = isSyncing,
                onSyncNow = onSyncNow
            )
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Recent Payment Events",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onOpenTransactionHistory,
                    modifier = Modifier.testTag("view_all_transactions_button")
                ) {
                    Text("View All (${summary?.totalEventsReceived ?: 0}) →")
                }
            }
        }

        if (recentEvents.isEmpty()) {
            item {
                EmptyRecentActivityCard(onOpenParserSandbox = onOpenParserSandbox)
            }
        } else {
            items(recentEvents, key = { it.localId }) { record ->
                TransactionEventCard(
                    record = record,
                    onClick = { onSelectEvent(record) }
                )
            }
        }
    }
}

@Composable
private fun MerchantHeaderCard(
    summary: DashboardSummary?,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onOpenOnboardingWizard: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BDPAYHUB • EVERY PAYMENT. VERIFIED.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = summary?.merchantName ?: "Loading Merchant Profile...",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("dashboard_merchant_name")
                    )
                    Text(
                        text = "${summary?.merchantEmail ?: ""} • ${summary?.planName ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (summary != null) {
                    DeviceAuthBadge(status = summary.deviceStatus)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_sync_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSyncing) "Syncing Queue..." else "Sync Queue Now")
                }

                OutlinedButton(
                    onClick = onOpenOnboardingWizard,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_onboarding_wizard_button")
                ) {
                    Text("Auth Setup")
                }
            }
        }
    }
}

@Composable
private fun SystemHealthGrid(
    summary: DashboardSummary?,
    onRequestSmsPermission: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            HealthStatusTile(
                title = "SMS Permission",
                value = if (summary?.smsPermissionGranted == true) "Granted (Local)" else "Permission Needed",
                isHealthy = summary?.smsPermissionGranted == true,
                icon = Icons.Default.Sms,
                actionLabel = if (summary?.smsPermissionGranted == true) null else "Grant",
                onAction = if (summary?.smsPermissionGranted == true) null else onRequestSmsPermission,
                modifier = Modifier.weight(1f)
            )

            HealthStatusTile(
                title = "Connectivity",
                value = if (summary?.isNetworkAvailable == true) "Online (HTTPS)" else "Offline Queue Active",
                isHealthy = summary?.isNetworkAvailable == true,
                icon = if (summary?.isNetworkAvailable == true) Icons.Default.Wifi else Icons.Default.WifiOff,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HealthStatusTile(
    title: String,
    value: String,
    isHealthy: Boolean,
    icon: ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val accent = if (isHealthy) StatusVerifiedGreen else StatusReviewAmber
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(26.dp)
                            .testTag("grant_sms_quick_button")
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ReconciliationMetricsSection(
    summary: DashboardSummary?,
    onOpenParserSandbox: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Event Synchronization & Verification",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (summary?.isBackendSource == true) {
                            "Source: Backend Verification & Local Queue State"
                        } else {
                            "Source: Local Synchronization Queue"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onOpenParserSandbox,
                    modifier = Modifier.testTag("test_sms_parser_button")
                ) {
                    Text("+ Test SMS")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricBox(
                    label = "Total Events",
                    count = summary?.totalEventsReceived ?: 0,
                    color = StatusPendingBlue,
                    icon = Icons.Default.Sms,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Server Matched",
                    count = summary?.matchedVerifiedPayments ?: 0,
                    color = StatusVerifiedGreen,
                    icon = Icons.Default.Verified,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricBox(
                    label = "Pending / Review",
                    count = summary?.pendingOrReviewEvents ?: 0,
                    color = StatusReviewAmber,
                    icon = Icons.Default.HourglassTop,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = "Server Rejected",
                    count = summary?.rejectedEvents ?: 0,
                    color = StatusRejectedRed,
                    icon = Icons.Default.ErrorOutline,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    count: Int,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProviderWalletsSection(
    wallets: List<ConfiguredWallet>
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Configured Receiving Wallets (Backend Confirmed)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Receiving wallet numbers are resolved exclusively from backend merchant configuration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentProvider.entries.forEach { provider ->
                    val wallet = wallets.find { it.provider.equals(provider.apiCode, ignoreCase = true) }
                    ProviderStatusRow(provider = provider, wallet = wallet)
                }
            }
        }
    }
}

@Composable
private fun ProviderStatusRow(
    provider: PaymentProvider,
    wallet: ConfiguredWallet?
) {
    val brandColor = Color(provider.brandHex)
    val isVerifiedActive = wallet?.isConfigured == true && wallet.isActive && wallet.backendVerified
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(brandColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${provider.displayName} (Sender ID: ${provider.expectedSenderId})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (wallet != null && wallet.isConfigured) {
                            "Wallet: ${wallet.walletNumber} • ${wallet.accountType}"
                        } else {
                            "Not configured on merchant account"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isVerifiedActive) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (isVerifiedActive) StatusVerifiedGreen else StatusReviewAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isVerifiedActive) "Active" else "Unverified",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isVerifiedActive) StatusVerifiedGreen else StatusReviewAmber,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DeviceHealthCard(
    summary: DashboardSummary?,
    isSyncing: Boolean,
    onSyncNow: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Authorized Agent Device Health",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Sync status",
                    tint = if (isSyncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            InfoRow("Device Name", summary?.deviceName ?: "N/A")
            InfoRow("Backend Device ID", summary?.deviceId ?: "N/A")
            InfoRow("Authorization Status", summary?.deviceStatus?.displayName ?: "Unknown")
            InfoRow("Last Successful Sync", formatTimestampShort(summary?.lastSyncTimeMillis))
            InfoRow("Local Queue Pending", "${summary?.localQueuedEvents ?: 0} event(s)")
            InfoRow("App & Parser Version", "v${EnvironmentConfig.APP_VERSION} / Parser v${EnvironmentConfig.PARSER_VERSION}")
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyRecentActivityCard(
    onOpenParserSandbox: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Sms,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No Payment Events Recorded Yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Incoming bKash, Nagad, Rocket, and upay notifications will appear here automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenParserSandbox) {
                Text("Open SMS Parser Verifier")
            }
        }
    }
}
