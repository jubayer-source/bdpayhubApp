package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DeviceAuthStatus
import com.example.domain.model.PaymentEventRecord
import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentTransactionType
import com.example.domain.model.SyncState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusPendingBlue
import com.example.ui.theme.StatusRejectedRed
import com.example.ui.theme.StatusReviewAmber
import com.example.ui.theme.StatusVerifiedGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProviderBadge(
    provider: PaymentProvider,
    modifier: Modifier = Modifier
) {
    val brandColor = Color(provider.brandHex)
    Surface(
        color = brandColor.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, brandColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(brandColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = brandColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SyncStatusBadge(
    syncState: SyncState,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (syncState) {
        SyncState.SERVER_MATCHED -> StatusVerifiedGreen to Icons.Default.CheckCircle
        SyncState.SERVER_REVIEW -> StatusReviewAmber to Icons.Default.WarningAmber
        SyncState.SERVER_RECEIVED -> StatusPendingBlue to Icons.Default.CloudDone
        SyncState.LOCAL_PENDING, SyncState.UPLOADING -> StatusPendingBlue to Icons.Default.CloudQueue
        SyncState.SERVER_REJECTED -> StatusRejectedRed to Icons.Default.ErrorOutline
        SyncState.UPLOAD_FAILED -> StatusRejectedRed to Icons.Default.SyncProblem
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = syncState.displayName,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = syncState.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DeviceAuthBadge(
    status: DeviceAuthStatus,
    modifier: Modifier = Modifier
) {
    val (color, icon) = when (status) {
        DeviceAuthStatus.AUTHORIZED -> StatusVerifiedGreen to Icons.Default.VerifiedUser
        DeviceAuthStatus.PENDING -> StatusReviewAmber to Icons.Default.HourglassEmpty
        DeviceAuthStatus.REVOKED -> StatusRejectedRed to Icons.Default.CloudOff
        DeviceAuthStatus.UNREGISTERED -> StatusPendingBlue to Icons.Default.Info
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = status.displayName,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = status.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun NonCustodialTrustBanner(
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Non-custodial architecture notice",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Non-Custodial Verification Agent",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "BDPayHub never holds or settles customer funds. Payments go directly to your wallet. SMS notifications are parsed locally and verified independently on the BDPayHub server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FeedbackBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isError) StatusRejectedRed else StatusVerifiedGreen
    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .testTag("feedback_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = if (isError) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                contentDescription = if (isError) "Alert" else "Success",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("dismiss_banner_button")
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TransactionEventCard(
    record: PaymentEventRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .testTag("transaction_card_${record.transactionId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                ProviderBadge(provider = record.provider)
                SyncStatusBadge(syncState = record.syncState)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TrxID: ${record.transactionId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = record.transactionType.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "৳ ${record.amount}",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = if (record.syncState.isServerVerifiedPayment) {
                            StatusVerifiedGreen
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Text(
                        text = if (record.syncState.isServerVerifiedPayment) {
                            "Payment Verified"
                        } else {
                            "Event Received (Unverified)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (record.syncState.isServerVerifiedPayment) {
                            StatusVerifiedGreen
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                val senderLabel = if (record.transactionType == PaymentTransactionType.AGENT_CASH_IN_RECEIVED) {
                    "Agent/Uddokta: ${record.senderNumber ?: "N/A"}"
                } else {
                    "Sender: ${record.senderNumber ?: "N/A"}"
                }
                Text(
                    text = senderLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatTimestampShort(record.receivedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatTimestampShort(epochMillis: Long?): String {
    if (epochMillis == null || epochMillis <= 0L) return "Never"
    val formatter = SimpleDateFormat("dd MMM HH:mm:ss", Locale.US)
    return formatter.format(Date(epochMillis))
}
