package com.example.blackjack

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.BlackjackDatabase
import com.example.blackjack.model.*
import com.example.blackjack.repository.BlackjackRepository
import com.example.blackjack.ui.BlackjackApp
import com.example.blackjack.ui.theme.BlackjackTheme
import com.example.blackjack.viewmodel.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GameplayUiInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: BlackjackDatabase
    private lateinit var game: GameViewModel
    private lateinit var history: HistoryViewModel
    private lateinit var statistics: StatisticsViewModel
    private val store = ViewModelStore()
    private val landscape get() = InstrumentationRegistry.getArguments().getString("orientation") == "landscape"

    @Before fun prepare() {
        rule.activity.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val expected = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        rule.waitUntil(15_000) { rule.activity.resources.configuration.orientation == expected }
        database = Room.inMemoryDatabaseBuilder<BlackjackDatabase>(instrumentation.targetContext)
            .setDriver(AndroidSQLiteDriver()).build()
    }

    @After fun close() {
        rule.runOnUiThread { store.clear() }
        database.close()
    }

    private fun showGame(vararg ranks: Rank) {
        val repository = BlackjackRepository(database.blackjackDao())
        rule.runOnUiThread {
            game = GameViewModel(repository) {
                val cards = ranks.mapIndexed { index, rank -> Card(rank, Suit.entries[index % 4]) }.iterator()
                BlackjackGame { if (cards.hasNext()) cards.next() else null }
            }
            history = HistoryViewModel(repository)
            statistics = StatisticsViewModel(repository)
            store.put("game", game)
            store.put("history", history)
            store.put("statistics", statistics)
        }
        rule.setContent { BlackjackTheme { BlackjackApp(game, history, statistics) } }
        rule.waitUntil(10_000) { !game.uiState.value.isLoadingBalance }
    }

    private fun deal(amount: Int = 10) {
        rule.onNodeWithTag("navigation_bet").performClick()
        rule.onNodeWithTag("chip_$amount").performScrollTo().performClick()
        assertEquals(amount, game.uiState.value.selectedBet)
        rule.onNodeWithTag("deal_button").performScrollTo().performClick()
    }

    @Test fun deckControlsRespectBoundsAndKeepSelectionAcrossTabs() {
        showGame(Rank.TEN, Rank.TEN, Rank.NINE, Rank.EIGHT)
        rule.onNodeWithTag("navigation_bet").performClick()
        rule.onNodeWithTag("fewer_decks").performScrollTo().assertIsNotEnabled()
        repeat(7) { rule.onNodeWithTag("more_decks").performScrollTo().performClick() }
        assertEquals(8, game.uiState.value.selectedDeckCount)
        rule.onNodeWithTag("more_decks").assertIsNotEnabled()
        rule.onNodeWithTag("navigation_home").performClick()
        rule.onNodeWithTag("navigation_bet").performClick()
        rule.onNodeWithTag("fewer_decks").performScrollTo().performClick()
        assertEquals(7, game.uiState.value.selectedDeckCount)
        rule.onNodeWithTag("deal_button").performScrollTo().performClick()
        rule.runOnUiThread { assertFalse(game.selectDeckCount(1)) }
        assertEquals(7, game.uiState.value.selectedDeckCount)
    }

    @Test fun standSavesOnceAndUpdatesHistoryAndSqlStatistics() {
        showGame(Rank.TEN, Rank.TEN, Rank.NINE, Rank.EIGHT)
        deal()
        assertEquals(19, game.uiState.value.playerTotal)
        rule.onNodeWithContentDescription(text(R.string.hidden_card)).assertExists()
        snapshot("game")
        rule.onNodeWithTag("navigation_home").performClick()
        rule.onNodeWithTag("new_game_fab").performClick()
        rule.onNodeWithText(text(R.string.game_title)).assertIsDisplayed()
        assertEquals(GamePhase.PLAYER_TURN, game.uiState.value.phase)
        rule.onNodeWithTag("stand_button").performScrollTo().performClick()
        rule.waitUntil(15_000) { game.uiState.value.savedGameId != null && statistics.uiState.value.statistics.totalGames == 1L }
        assertEquals(1010.0, game.uiState.value.chips, 0.0)
        assertEquals(GameResult.WIN, game.uiState.value.result)
        rule.onNodeWithContentDescription(text(R.string.hidden_card)).assertDoesNotExist()
        rule.onNodeWithTag("navigation_history").performClick()
        rule.waitUntil(10_000) { history.uiState.value.games.size == 1 }
        rule.onNodeWithTag("history_game_${game.uiState.value.savedGameId}").assertIsDisplayed()
        snapshot("history")
        rule.onNodeWithTag("navigation_statistics").performClick()
        rule.onNodeWithText(text(R.string.total_games)).performScrollTo().assertIsDisplayed()
        assertEquals(1L, statistics.uiState.value.statistics.winningHands)
        assertEquals(4L, statistics.uiState.value.statistics.totalCards)
        snapshot("statistics")
        rule.onNodeWithTag("navigation_bet").performClick()
        rule.onNodeWithTag("change_bet_button").performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.bet_title)).performScrollTo().assertIsDisplayed()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithText(text(R.string.home_title)).performScrollTo().assertIsDisplayed()
        assertEquals(1, history.uiState.value.games.size)
    }

    @Test fun hitBustDisablesActionsAndKeepsSelectedBetAcrossTabs() {
        showGame(Rank.TEN, Rank.TEN, Rank.SIX, Rank.EIGHT, Rank.KING)
        deal(25)
        rule.onNodeWithTag("navigation_history").performClick()
        rule.onNodeWithTag("navigation_bet").performClick()
        assertEquals(25, game.uiState.value.roundBet)
        rule.onNodeWithTag("hit_button").performScrollTo().performClick()
        rule.waitUntil(15_000) { game.uiState.value.savedGameId != null }
        assertEquals(GameResult.LOSS, game.uiState.value.result)
        assertEquals(975.0, game.uiState.value.chips, 0.0)
        rule.onNodeWithTag("hit_button").assertIsNotEnabled()
        rule.onNodeWithTag("stand_button").assertIsNotEnabled()
    }

    @Test fun naturalBlackjackIsSettledWithoutAnActionAndEmptyHistoryIsExplicit() {
        showGame(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        rule.onNodeWithTag("navigation_history").performClick()
        rule.waitUntil(10_000) { !history.uiState.value.isLoading }
        rule.onNodeWithTag("empty_history").assertIsDisplayed()
        rule.onNodeWithTag("navigation_home").performClick()
        snapshot("home")
        rule.onNodeWithTag("navigation_bet").performClick()
        snapshot("bet")
        rule.onNodeWithTag("chip_5").performScrollTo().performClick()
        assertEquals(5, game.uiState.value.selectedBet)
        rule.onNodeWithTag("deal_button").performScrollTo().performClick()
        rule.waitUntil(15_000) { game.uiState.value.savedGameId != null }
        assertTrue(game.uiState.value.playerHasBlackjack)
        assertEquals(1007.5, game.uiState.value.chips, 0.0)
        rule.onNodeWithTag("round_result").performScrollTo().assertIsDisplayed()
        snapshot("result")
    }

    private fun text(id: Int) = instrumentation.targetContext.getString(id)

    // Captures pour contrôler visuellement les deux orientations, sans données personnelles.
    private fun snapshot(name: String) {
        rule.waitForIdle()
        val orientation = if (landscape) "landscape" else "portrait"
        val file = File(rule.activity.getExternalFilesDir(null), "ui-$name-$orientation.png")
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
