package com.example.blackjack

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.viewmodel.GameViewModel
import com.example.blackjack.viewmodel.HistoryViewModel
import com.example.blackjack.viewmodel.StatisticsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedObjectsInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun manifestProvidesOneApplicationDatabaseAndRepository() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val application = activity.application
            assertTrue(application is BlackjackApplication)
            val blackjackApplication = application as BlackjackApplication
            assertSame(
                InstrumentationRegistry.getInstrumentation().targetContext.applicationContext,
                blackjackApplication
            )
            assertSame(blackjackApplication.database, blackjackApplication.database)
            assertSame(blackjackApplication.repository, blackjackApplication.repository)
        }
    }

    @Test
    fun activityGameViewModelSurvivesNavigationBetweenBetGameAndOtherTabs() {
        composeRule.waitUntil(10_000) { !composeRule.activity.gameViewModel.uiState.value.isLoadingBalance }
        lateinit var original: GameViewModel
        composeRule.activityRule.scenario.onActivity {
            original = it.gameViewModel
            assertTrue(original.selectBet(25))
        }
        composeRule.onNodeWithTag("navigation_bet").performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.open_table)).performScrollTo().performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.game_title)).assertIsDisplayed()
        composeRule.onNodeWithTag("navigation_history").performClick()
        composeRule.onNodeWithTag("navigation_statistics").performClick()
        composeRule.onNodeWithTag("navigation_bet").performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.game_title)).assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity {
            assertSame(original, it.gameViewModel)
            assertEquals(25, it.gameViewModel.uiState.value.selectedBet)
        }
    }

    @Test
    fun rotationRecreatesActivityButKeepsViewModelsAndSharedServices() {
        composeRule.waitUntil(10_000) { !composeRule.activity.gameViewModel.uiState.value.isLoadingBalance }
        lateinit var originalActivity: MainActivity
        lateinit var application: BlackjackApplication
        lateinit var game: GameViewModel
        lateinit var history: HistoryViewModel
        lateinit var statistics: StatisticsViewModel
        var expectedOrientation = Configuration.ORIENTATION_LANDSCAPE
        composeRule.activityRule.scenario.onActivity {
            originalActivity = it
            application = it.application as BlackjackApplication
            game = it.gameViewModel
            history = it.historyViewModel
            statistics = it.statisticsViewModel
            assertTrue(game.selectBet(25))
            assertTrue(game.selectDeckCount(4))
            // Change réellement l'orientation, plutôt que de simuler seulement une recréation.
            if (it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                expectedOrientation = Configuration.ORIENTATION_PORTRAIT
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.activity !== originalActivity &&
                composeRule.activity.resources.configuration.orientation == expectedOrientation
        }
        composeRule.activityRule.scenario.onActivity {
            assertNotSame(originalActivity, it)
            assertSame(application, it.application)
            assertSame(application.database, (it.application as BlackjackApplication).database)
            assertSame(application.repository, (it.application as BlackjackApplication).repository)
            assertSame(game, it.gameViewModel)
            assertSame(history, it.historyViewModel)
            assertSame(statistics, it.statisticsViewModel)
            assertEquals(25, it.gameViewModel.uiState.value.selectedBet)
            assertEquals(4, it.gameViewModel.uiState.value.selectedDeckCount)
        }
    }
}
