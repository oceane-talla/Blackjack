package com.example.blackjack

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setAndVerifyOrientation() {
        val landscape = InstrumentationRegistry.getArguments().getString("orientation") == "landscape"
        val expectedOrientation = if (landscape) {
            Configuration.ORIENTATION_LANDSCAPE
        } else {
            Configuration.ORIENTATION_PORTRAIT
        }
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.requestedOrientation = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.activity.resources.configuration.orientation == expectedOrientation
        }
        assertEquals(expectedOrientation, composeRule.activity.resources.configuration.orientation)
        composeRule.waitUntil(10_000) { !composeRule.activity.gameViewModel.uiState.value.isLoadingBalance }
    }

    @Test
    fun homeToTableAndBack() {
        composeRule.onNodeWithText(text(R.string.start_game)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.bet_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.open_table)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.game_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_bet").assertIsSelected()

        composeRule.onNodeWithContentDescription(text(R.string.navigate_back)).performClick()
        composeRule.onNodeWithText(text(R.string.bet_title)).performScrollTo().assertIsDisplayed()
        pressSystemBack()
        composeRule.onNodeWithText(text(R.string.home_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun homeTabReturnsFromTheHomePlayButton() {
        composeRule.onNodeWithText(text(R.string.start_game)).performScrollTo().performClick()
        composeRule.onNodeWithText(text(R.string.bet_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_home").performTouchInput { click() }
        composeRule.onNodeWithText(text(R.string.home_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_home").assertIsSelected()
    }

    @Test
    fun homeTabRespondsToTouchAfterPlayingAndRestoringTabs() {
        repeat(3) {
            composeRule.onNodeWithTag("navigation_bet").performTouchInput { click() }
            composeRule.onNodeWithText(text(R.string.bet_title)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("navigation_home").performTouchInput { click() }
            composeRule.onNodeWithText(text(R.string.home_title)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("navigation_home").assertIsSelected()

            composeRule.onNodeWithTag("navigation_bet").performTouchInput { click() }
            composeRule.onNodeWithText(text(R.string.open_table)).performScrollTo().performClick()
            composeRule.onNodeWithTag("navigation_history").performTouchInput { click() }
            composeRule.onNodeWithTag("navigation_statistics").performTouchInput { click() }
            composeRule.onNodeWithTag("navigation_bet").performTouchInput { click() }
            composeRule.onNodeWithText(text(R.string.game_title)).performScrollTo().assertIsDisplayed()
            finishRound()
            composeRule.onNodeWithTag("navigation_home").performTouchInput { click() }
            composeRule.onNodeWithText(text(R.string.home_title)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("navigation_home").assertIsSelected()
            composeRule.onNodeWithTag("new_game_fab").performClick()
            composeRule.onNodeWithTag("navigation_home").performTouchInput { click() }
            composeRule.onNodeWithText(text(R.string.home_title)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun tabsRestoreTheGameAndDoNotStackDuplicates() {
        composeRule.onNodeWithTag("navigation_bet").performClick()
        composeRule.onNodeWithText(text(R.string.open_table)).performScrollTo().performClick()
        composeRule.onNodeWithTag("navigation_history").performClick()
        composeRule.onNodeWithText(text(R.string.history_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_statistics").performClick()
        composeRule.onNodeWithText(text(R.string.statistics_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_bet").performClick()
        composeRule.onNodeWithText(text(R.string.game_title)).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("navigation_home").performClick()
        composeRule.onNodeWithText(text(R.string.home_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_history").performClick()
        composeRule.onNodeWithTag("navigation_history").performClick()
        pressSystemBack()
        composeRule.onNodeWithText(text(R.string.home_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun newGameFabOpensTheBetWithoutStackingIt() {
        composeRule.onNodeWithTag("navigation_bet").performClick()
        composeRule.onNodeWithText(text(R.string.open_table)).performScrollTo().performClick()
        finishRound()
        composeRule.onNodeWithTag("navigation_statistics").performClick()
        composeRule.onNodeWithTag("new_game_fab").performClick()
        composeRule.onNodeWithText(text(R.string.bet_title)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("new_game_fab").performClick()
        composeRule.onNodeWithTag("navigation_history").performClick()
        composeRule.onNodeWithTag("navigation_bet").performClick()
        composeRule.onNodeWithText(text(R.string.bet_title)).performScrollTo().assertIsDisplayed()
        pressSystemBack()
        composeRule.onNodeWithText(text(R.string.home_title)).performScrollTo().assertIsDisplayed()
    }

    private fun finishRound() {
        if (composeRule.activity.gameViewModel.uiState.value.canStand) {
            composeRule.onNodeWithTag("stand_button").performScrollTo().performClick()
        }
        composeRule.waitUntil(15_000) { composeRule.activity.gameViewModel.uiState.value.savedGameId != null }
    }

    private fun text(resourceId: Int): String = composeRule.activity.getString(resourceId)

    private fun pressSystemBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }
}
