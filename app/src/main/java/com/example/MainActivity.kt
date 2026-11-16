package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.presentation.BDPayHubUiState
import com.example.presentation.BDPayHubViewModel
import com.example.presentation.MainTab
import com.example.presentation.OnboardingStep
import com.example.presentation.components.FeedbackBanner
import com.example.presentation.dashboard.DashboardScreen
import com.example.presentation.devices.DeviceAndParserScreen
import com.example.presentation.onboarding.AuthAndOnboardingScreen
import com.example.presentation.settings.SettingsScreen
import com.example.presentation.transactions.TransactionHistoryScreen
import com.example.ui.theme.BDPayHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appContext = LocalContext.current.applicationContext
            val container = BDPayHubApplication.getContainer(appContext)
            val viewModel: BDPayHubViewModel = viewModel(
                factory = BDPayHubViewModel.Factory(appContext, container)
            )
            BDPayHubRootApp(viewModel = viewModel)
        }
    }
}

@Composable
fun BDPayHubRootApp(
    viewModel: BDPayHubViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val allEvents by viewModel.allEvents.collectAsStateWithLifecycle()
    val recentEvents by viewModel.recentEvents.collectAsStateWithLifecycle()
    val filteredEvents by viewModel.filteredEvents.collectAsStateWithLifecycle()
    val diagnosticLogs by viewModel.diagnosticLogs.collectAsStateWithLifecycle()

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val smsGranted = result[Manifest.permission.RECEIVE_SMS] == true
        viewModel.onSmsPermissionResult(smsGranted)
    }

    val requestPermissionsAction = {
        val perms = buildList {
            add(Manifest.permission.RECEIVE_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        smsPermissionLauncher.launch(perms)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshSmsPermissionStatus()
    }

    BDPayHubTheme(themeMode = preferences.themeMode) {
        if (uiState.showOnboardingFlow) {
            BackHandler {
                viewModel.completeOnboardingWizard()
            }
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (uiState.bannerMessage != null) {
                        FeedbackBanner(
                            message = uiState.bannerMessage!!,
                            isError = uiState.bannerErrorType != null,
                            onDismiss = viewModel::dismissBanner,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    AuthAndOnboardingScreen(
                        step = uiState.onboardingStep,
                        isLoading = uiState.isLoading,
                        deviceNameInput = uiState.deviceNameInput,
                        merchantProfile = uiState.merchantProfile,
                        smsPermissionGranted = uiState.smsPermissionGranted,
                        onStepChange = viewModel::setOnboardingStep,
                        onGoogleSignIn = viewModel::signInWithGoogleAccount,
                        onDeviceNameChange = viewModel::updateDeviceNameInput,
                        onAuthorizeDevice = viewModel::registerCurrentDevice,
                        onRequestSmsPermission = requestPermissionsAction,
                        onSkipSmsPermissionGracefully = {
                            viewModel.onSmsPermissionResult(false)
                        },
                        onFinishOnboarding = viewModel::completeOnboardingWizard
                    )
                }
            }
        } else {
            if (uiState.activeTab != MainTab.DASHBOARD && uiState.selectedTransaction == null) {
                BackHandler {
                    viewModel.selectTab(MainTab.DASHBOARD)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 600.dp

                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (!isWideScreen) {
                            BDPayHubBottomBar(
                                activeTab = uiState.activeTab,
                                onSelectTab = viewModel::selectTab
                            )
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        if (isWideScreen) {
                            BDPayHubNavigationRail(
                                activeTab = uiState.activeTab,
                                onSelectTab = viewModel::selectTab
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            if (uiState.bannerMessage != null) {
                                FeedbackBanner(
                                    message = uiState.bannerMessage!!,
                                    isError = uiState.bannerErrorType != null,
                                    onDismiss = viewModel::dismissBanner,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                                )
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                when (uiState.activeTab) {
                                    MainTab.DASHBOARD -> DashboardScreen(
                                        summary = uiState.dashboardSummary,
                                        recentEvents = recentEvents,
                                        isSyncing = uiState.isSyncing,
                                        onSyncNow = viewModel::triggerManualSync,
                                        onRequestSmsPermission = requestPermissionsAction,
                                        onOpenTransactionHistory = {
                                            viewModel.selectTab(MainTab.TRANSACTIONS)
                                        },
                                        onOpenParserSandbox = {
                                            viewModel.selectTab(MainTab.AGENT_PARSER)
                                        },
                                        onOpenOnboardingWizard = {
                                            viewModel.startOnboardingWizard(OnboardingStep.WELCOME)
                                        },
                                        onSelectEvent = { record ->
                                            viewModel.selectTab(MainTab.TRANSACTIONS)
                                            viewModel.selectTransaction(record)
                                        }
                                    )

                                    MainTab.TRANSACTIONS -> TransactionHistoryScreen(
                                        allEventsCount = allEvents.size,
                                        filteredEvents = filteredEvents,
                                        filter = uiState.filter,
                                        selectedTransaction = uiState.selectedTransaction,
                                        isSyncing = uiState.isSyncing,
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onProviderFilterChange = viewModel::updateProviderFilter,
                                        onTypeFilterChange = viewModel::updateTransactionTypeFilter,
                                        onSyncStateFilterChange = viewModel::updateSyncStateFilter,
                                        onDateRangeFilterChange = viewModel::updateDateRangeFilter,
                                        onClearFilters = viewModel::clearAllFilters,
                                        onLoadMore = viewModel::loadNextPage,
                                        onRefreshSync = viewModel::triggerManualSync,
                                        onSelectTransaction = viewModel::selectTransaction
                                    )

                                    MainTab.AGENT_PARSER -> DeviceAndParserScreen(
                                        deviceStatus = preferences.deviceAuthStatus,
                                        simulateOfflineMode = uiState.simulateOfflineMode,
                                        simulateSubscriptionLimit = uiState.simulateSubscriptionLimit,
                                        parserFeedbackMessage = uiState.parserFeedbackMessage,
                                        diagnosticLogs = diagnosticLogs,
                                        onTestSmsIngestion = { sender, msg ->
                                            viewModel.testLocalSmsIngestion(sender, msg)
                                        },
                                        onToggleOfflineMode = viewModel::toggleSimulatedOfflineMode,
                                        onToggleSubscriptionLimit = viewModel::toggleSimulatedSubscriptionLimit,
                                        onSimulateRevokeDevice = viewModel::simulateDeviceRevocation,
                                        onReauthorizeDevice = viewModel::reauthorizeRevokedDevice
                                    )

                                    MainTab.SETTINGS -> SettingsScreen(
                                        summary = uiState.dashboardSummary,
                                        preferences = preferences,
                                        isSyncing = uiState.isSyncing,
                                        onSyncNow = viewModel::triggerManualSync,
                                        onRequestSmsPermission = requestPermissionsAction,
                                        onThemeModeChange = viewModel::setThemeMode,
                                        onEnvironmentChange = viewModel::setEnvironment,
                                        onNotificationPreferencesChange = viewModel::setNotificationPreferences,
                                        onSignOut = viewModel::signOut
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BDPayHubBottomBar(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        MainTab.entries.forEach { tab ->
            val selected = activeTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = tabIcon(tab, selected),
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("nav_tab_${tab.route}")
            )
        }
    }
}

@Composable
private fun BDPayHubNavigationRail(
    activeTab: MainTab,
    onSelectTab: (MainTab) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        MainTab.entries.forEach { tab ->
            val selected = activeTab == tab
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        imageVector = tabIcon(tab, selected),
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag("nav_rail_${tab.route}")
            )
        }
    }
}

private fun tabIcon(tab: MainTab, selected: Boolean) = when (tab) {
    MainTab.DASHBOARD -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
    MainTab.TRANSACTIONS -> if (selected) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong
    MainTab.AGENT_PARSER -> if (selected) Icons.Filled.Memory else Icons.Outlined.Memory
    MainTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}
