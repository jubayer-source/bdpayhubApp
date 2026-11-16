package com.example.presentation.transactions

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.DateRangeOption
import com.example.domain.model.PaymentEventRecord
import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentTransactionType
import com.example.domain.model.SyncState
import com.example.domain.model.TransactionFilter
import com.example.presentation.components.ProviderBadge
import com.example.presentation.components.SyncStatusBadge
import com.example.presentation.components.TransactionEventCard
import com.example.presentation.components.formatTimestampShort
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.StatusReviewAmber
import com.example.ui.theme.StatusVerifiedGreen

/**
 * Transaction History & Details Screen (Section 14).
 *
 * Features:
 * - Search by Transaction ID / Reference / Sender
 * - Filter by Provider, Transaction Type, Synchronization Status, Date Range
 * - Pagination & Pull/Tap-to-Refresh
 * - Full Transaction Details view clearly distinguishing "Payment Event Received" from "Payment Verified"
 */
@Composable
fun TransactionHistoryScreen(
    allEventsCount: Int,
    filteredEvents: List<PaymentEventRecord>,
    filter: TransactionFilter,
    selectedTransaction: PaymentEventRecord?,
    isSyncing: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onProviderFilterChange: (PaymentProvider?) -> Unit,
    onTypeFilterChange: (PaymentTransactionType?) -> Unit,
    onSyncStateFilterChange: (SyncState?) -> Unit,
    onDateRangeFilterChange: (DateRangeOption) -> Unit,
    onClearFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onRefreshSync: () -> Unit,
    onSelectTransaction: (PaymentEventRecord?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedTransaction != null) {
        BackHandler { onSelectTransaction(null) }
        TransactionDetailsView(
            record = selectedTransaction,
            onBack = { onSelectTransaction(null) },
            modifier = modifier
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("transaction_history_list")
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Transaction Event History",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Showing ${filteredEvents.size} of $allEventsCount recorded payment events",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onRefreshSync,
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("history_refresh_sync_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh and Sync",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isSyncing) "Syncing..." else "Refresh")
                }
            }
        }

        item {
            OutlinedTextField(
                value = filter.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search by Transaction ID, Reference, or Number...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (filter.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_search_input")
            )
        }

        item {
            FilterSectionCard(
                filter = filter,
                onProviderFilterChange = onProviderFilterChange,
                onTypeFilterChange = onTypeFilterChange,
                onSyncStateFilterChange = onSyncStateFilterChange,
                onDateRangeFilterChange = onDateRangeFilterChange,
                onClearFilters = onClearFilters
            )
        }

        if (filteredEvents.isEmpty()) {
            item {
                EmptyTransactionsView(onClearFilters = onClearFilters)
            }
        } else {
            items(filteredEvents, key = { it.localId }) { record ->
                TransactionEventCard(
                    record = record,
                    onClick = { onSelectTransaction(record) }
                )
            }

            if (filteredEvents.size < allEventsCount) {
                item {
                    Button(
                        onClick = onLoadMore,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("load_more_transactions_button")
                    ) {
                        Text("Load More Events")
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionCard(
    filter: TransactionFilter,
    onProviderFilterChange: (PaymentProvider?) -> Unit,
    onTypeFilterChange: (PaymentTransactionType?) -> Unit,
    onSyncStateFilterChange: (SyncState?) -> Unit,
    onDateRangeFilterChange: (DateRangeOption) -> Unit,
    onClearFilters: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Filter Events",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = onClearFilters,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("clear_filters_button")
                ) {
                    Text("Reset All", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Provider Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = filter.provider == null,
                    onClick = { onProviderFilterChange(null) },
                    label = { Text("All Providers") }
                )
                PaymentProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = filter.provider == provider,
                        onClick = {
                            onProviderFilterChange(if (filter.provider == provider) null else provider)
                        },
                        label = { Text(provider.displayName) },
                        modifier = Modifier.testTag("filter_provider_${provider.apiCode}")
                    )
                }
            }

            // Transaction Type Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = filter.transactionType == null,
                    onClick = { onTypeFilterChange(null) },
                    label = { Text("All Types") }
                )
                PaymentTransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = filter.transactionType == type,
                        onClick = {
                            onTypeFilterChange(if (filter.transactionType == type) null else type)
                        },
                        label = { Text(type.displayName) },
                        modifier = Modifier.testTag("filter_type_${type.apiValue}")
                    )
                }
            }

            // Sync Status Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = filter.syncState == null,
                    onClick = { onSyncStateFilterChange(null) },
                    label = { Text("All Statuses") }
                )
                SyncState.entries.forEach { state ->
                    FilterChip(
                        selected = filter.syncState == state,
                        onClick = {
                            onSyncStateFilterChange(if (filter.syncState == state) null else state)
                        },
                        label = { Text(state.displayName) },
                        modifier = Modifier.testTag("filter_status_${state.name}")
                    )
                }
            }

            // Date Range Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                DateRangeOption.entries.forEach { option ->
                    FilterChip(
                        selected = filter.dateRange == option,
                        onClick = { onDateRangeFilterChange(option) },
                        label = { Text(option.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionDetailsView(
    record: PaymentEventRecord,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("transaction_details_view")
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("details_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Transaction Event Details",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Critical Distinction Banner: Payment Event Received vs Payment Verified
        item {
            val isVerified = record.syncState.isServerVerifiedPayment
            val accent = if (isVerified) StatusVerifiedGreen else StatusReviewAmber
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .testTag("verification_distinction_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isVerified) {
                                "PAYMENT VERIFIED BY BACKEND"
                            } else {
                                "PAYMENT EVENT RECEIVED (NOT VERIFIED AS COMPLETED PAYMENT)"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isVerified) {
                                "The BDPayHub backend independently matched this payment event against an active checkout intent and verified the receiving merchant wallet."
                            } else {
                                "A locally parsed SMS notification alone is not cryptographic proof of payment. This event's current status is ${record.syncState.displayName}: ${record.syncState.description}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Agent Cash-In Semantics Warning if applicable (Section 11)
        if (record.transactionType == PaymentTransactionType.AGENT_CASH_IN_RECEIVED) {
            item {
                Surface(
                    color = StatusReviewAmber.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StatusReviewAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = "Agent Cash-In Semantics",
                            tint = StatusReviewAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Agent / Uddokta Cash-In Matching Rule",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "For Agent Cash-In notifications, the sender number (${record.senderNumber ?: "N/A"}) belongs to the Agent/Uddokta, NOT the customer. Amount alone never auto-verifies a customer payment.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ProviderBadge(provider = record.provider)
                        SyncStatusBadge(syncState = record.syncState)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "৳ ${record.amount} ${record.currency}",
                        style = MaterialTheme.typography.displayLarge,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Transaction ID: ${record.transactionId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(12.dp))

                    DetailFieldRow("Provider", "${record.provider.displayName} (${record.provider.apiCode})")
                    DetailFieldRow("Transaction ID", record.transactionId)
                    DetailFieldRow("Transaction Type", "${record.transactionType.displayName} (${record.transactionType.apiValue})")
                    DetailFieldRow("Amount", "${record.amount} ${record.currency}")
                    DetailFieldRow(
                        label = if (record.transactionType == PaymentTransactionType.AGENT_CASH_IN_RECEIVED) {
                            "Agent / Uddokta Number"
                        } else {
                            "Sender / Customer Number"
                        },
                        value = record.senderNumber ?: "Unavailable"
                    )
                    DetailFieldRow("Receiving Merchant Wallet", record.receiverNumber ?: "Pending Backend Confirmation")
                    DetailFieldRow("Provider Timestamp (ISO-8601)", record.transactionTime ?: "N/A")
                    DetailFieldRow("Reference", record.reference ?: "None")
                    DetailFieldRow("Fee", record.fee?.let { "BDT $it" } ?: "N/A")
                    DetailFieldRow("Commission", record.commission?.let { "BDT $it" } ?: "N/A")
                    DetailFieldRow("Wallet Balance in Notification", record.balance?.let { "BDT $it" } ?: "N/A")
                    DetailFieldRow("Parser Engine Version", "v${record.parserVersion}")
                    DetailFieldRow("Backend Processing Status", record.syncState.displayName)
                    DetailFieldRow("Server Status Detail", record.serverStatusMessage ?: "N/A")
                    DetailFieldRow("Event Captured Locally", formatTimestampShort(record.receivedAt))
                    DetailFieldRow("Last Synchronization Time", formatTimestampShort(record.lastSyncedAt))
                }
            }
        }
    }
}

@Composable
private fun DetailFieldRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyTransactionsView(
    onClearFilters: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .testTag("empty_transactions_card")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
        ) {
            Text(
                text = "No Matching Payment Events",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No locally parsed or synchronized events match your current search or filter criteria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(onClick = onClearFilters) {
                Text("Clear Active Filters")
            }
        }
    }
}
