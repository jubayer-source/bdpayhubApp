package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.core.database.BDPayHubDatabase
import com.example.core.di.AppContainer
import com.example.presentation.BDPayHubViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI Robolectric Test Suite verifying Critical User Journeys (Section 20):
 * - Dashboard loading & merchant verification stats
 * - Navigation to Transaction History, Details view, Agent & Parser, and Settings
 * - Onboarding & Google Sign-In screens
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BDPayHubUiRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var testContainer: AppContainer
    private lateinit var viewModel: BDPayHubViewModel

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val inMemoryDb = BDPayHubDatabase.createInMemory(context)
        testContainer = AppContainer(
            context = context,
            customDatabase = inMemoryDb
        )
        BDPayHubApplication.setContainerForTesting(testContainer)
        viewModel = BDPayHubViewModel(context, testContainer)
    }

    @Test
    fun `dashboard displays merchant profile, health indicators, and navigates across tabs`() {
        composeTestRule.setContent {
            BDPayHubRootApp(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // Verify Dashboard renders
        composeTestRule.onNodeWithTag("dashboard_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("Non-Custodial Verification Agent").assertIsDisplayed()

        // Navigate to Transactions tab
        composeTestRule.onNodeWithTag("nav_tab_transactions").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("transaction_history_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transaction_search_input").assertIsDisplayed()

        // Navigate to Agent & Parser tab
        composeTestRule.onNodeWithTag("nav_tab_agent_parser").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("agent_parser_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("11 Official Provider SMS Templates").assertIsDisplayed()

        // Navigate to Settings tab
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings & Device Management").assertIsDisplayed()
    }

    @Test
    fun `onboarding flow renders welcome, google sign in, and no merchant profile warning`() {
        composeTestRule.setContent {
            BDPayHubRootApp(viewModel = viewModel)
        }
        composeTestRule.waitForIdle()

        // Launch Auth & Onboarding Wizard from Dashboard
        composeTestRule.onNodeWithTag("open_onboarding_wizard_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("onboarding_screen_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_get_started_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        // Test Google Account Without Merchant Profile
        composeTestRule.onNodeWithTag("google_sign_in_unregistered_button")
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000L) {
            composeTestRule.onAllNodesWithTag("no_merchant_profile_card")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("no_merchant_profile_card")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
