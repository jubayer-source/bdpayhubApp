package com.example.presentation.devices

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.sms.OfficialSmsSampleCatalog
import com.example.domain.model.DeviceAuthStatus
import com.example.domain.model.ParserDiagnosticEntry
import com.example.presentation.components.DeviceAuthBadge
import com.example.presentation.components.formatTimestampShort
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.UUID

/**
 * Interactive Agent & Local SMS Parser Diagnostics Screen.
 *
 * Allows merchants and QA engineers to:
 * - Inspect all 11 official bKash, Nagad, Rocket, and upay SMS patterns
 * - Parse & queue live notifications into the Room offline queue and trigger backend ingestion
 * - Verify that unrelated personal SMS messages and unsupported patterns (like upay Cash-Out) are rejected
 * - Test Offline Queue behavior, Subscription Limit preservation, and Device Revocation enforcement
 * - Inspect privacy-preserving local diagnostic logs (which never contain raw SMS)
 */
@Composable
fun DeviceAndParserScreen(
    deviceStatus: DeviceAuthStatus,
    simulateOfflineMode: Boolean,
    simulateSubscriptionLimit: Boolean,
    parserFeedbackMessage: String?,
    diagnosticLogs: List<ParserDiagnosticEntry>,
    onTestSmsIngestion: (senderId: String, message: String) -> Unit,
    onToggleOfflineMode: (Boolean) -> Unit,
    onToggleSubscriptionLimit: (Boolean) -> Unit,
    onSimulateRevokeDevice: () -> Unit,
    onReauthorizeDevice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTemplate by remember { mutableStateOf(OfficialSmsSampleCatalog.templates.first()) }
    var senderInput by remember { mutableStateOf(selectedTemplate.senderId) }
    var messageInput by remember { mutableStateOf(selectedTemplate.sampleMessage) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("agent_parser_screen_list")
    ) {
        item {
            Column {
                Text(
                    text = "Local SMS Parser & Queue Diagnostics",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Verify on-device parsing for bKash, Nagad, Rocket (16216), and upay notifications",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Interactive SMS Parser Verifier Card
        item {
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
                            imageVector = Icons.Default.Sms,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "11 Official Provider SMS Templates",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        OfficialSmsSampleCatalog.templates.forEach { tpl ->
                            FilterChip(
                                selected = selectedTemplate.id == tpl.id,
                                onClick = {
                                    selectedTemplate = tpl
                                    senderInput = tpl.senderId
                                    // Generate a fresh TrxID suffix so repeated tests can also test new events
                                    val freshSuffix = UUID.randomUUID().toString().uppercase().replace("-", "").take(3)
                                    messageInput = tpl.sampleMessage.replace(
                                        Regex("""(TrxID:? |TxnID: )([A-Z0-9]{6})[A-Z0-9]*"""),
                                        "$1$2$freshSuffix"
                                    )
                                },
                                label = { Text(tpl.title) },
                                modifier = Modifier.testTag("template_chip_${tpl.id}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = selectedTemplate.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = senderInput,
                        onValueChange = { senderInput = it },
                        label = { Text("SMS Sender ID (e.g. bKash, NAGAD, 16216, upay)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parser_sender_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = { messageInput = it },
                        label = { Text("Notification Body (Parsed 100% On-Device)") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parser_message_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onTestSmsIngestion(senderInput, messageInput) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("run_local_parser_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Parse & Queue Event")
                        }

                        OutlinedButton(
                            onClick = {
                                senderInput = "+8801700000000"
                                messageInput = "Hey, I have received your dinner invitation for 500 Tk at 8pm!"
                                onTestSmsIngestion(senderInput, messageInput)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("test_unrelated_sms_button")
                        ) {
                            Text("Test Personal SMS")
                        }
                    }

                    if (!parserFeedbackMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("parser_feedback_box")
                        ) {
                            Text(
                                text = parserFeedbackMessage,
                                style = MaterialTheme.typography.labelMedium,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Offline Queue & Security Resilience Controls
        item {
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
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Resilience & Revocation Verification",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        DeviceAuthBadge(status = deviceStatus)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Offline Queue Mode (No Internet)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Events stay in LOCAL_PENDING / UPLOAD_FAILED and auto-sync when online.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = simulateOfflineMode,
                            onCheckedChange = onToggleOfflineMode,
                            modifier = Modifier.testTag("toggle_offline_mode_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Simulate Plan Quota Reached (HTTP 429)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Verifies events are preserved locally and never silently discarded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = simulateSubscriptionLimit,
                            onCheckedChange = onToggleSubscriptionLimit,
                            modifier = Modifier.testTag("toggle_subscription_limit_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (deviceStatus == DeviceAuthStatus.REVOKED) {
                            Button(
                                onClick = onReauthorizeDevice,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reauthorize_device_button")
                            ) {
                                Text("Re-Register & Authorize Device")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onSimulateRevokeDevice,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("simulate_revoke_device_button")
                            ) {
                                Text("Test Device Revocation Enforcement")
                            }
                        }
                    }
                }
            }
        }

        // Local Diagnostic Logs Section (Section 8: no raw SMS stored)
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Local Parser Diagnostics (Zero Raw SMS Stored)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Records metadata when a known provider sender ID sends an unsupported format.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (diagnosticLogs.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = "No unrecognized format diagnostics recorded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(diagnosticLogs, key = { it.id }) { entry ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Sender ID: ${entry.senderId}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = formatTimestampShort(entry.recordedAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.failureReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
