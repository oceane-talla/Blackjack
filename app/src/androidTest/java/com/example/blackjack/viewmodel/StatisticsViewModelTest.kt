package com.example.blackjack.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.BlackjackDatabase
import com.example.blackjack.database.dao.BlackjackDao
import com.example.blackjack.model.BlackjackGame
import com.example.blackjack.model.Card
import com.example.blackjack.model.GameStatistics
import com.example.blackjack.model.Rank
import com.example.blackjack.model.Suit
import com.example.blackjack.repository.BlackjackRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: BlackjackDatabase
    private val store = ViewModelStore()

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder<BlackjackDatabase>(instrumentation.targetContext)
            .setDriver(AndroidSQLiteDriver()).build()
    }

    @After
    fun closeDatabase() {
        instrumentation.runOnMainSync { store.clear() }
        database.close()
    }

    @Test
    fun firstEmptySummaryEndsLoadingWithoutAnError() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val realDao = database.blackjackDao()
        val delayedDao = object : BlackjackDao by realDao {
            override fun observeStatistics(): Flow<GameStatistics> = flow {
                release.await()
                emitAll(realDao.observeStatistics())
            }
        }
        val viewModel = createViewModel(delayedDao)
        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.hasError)
        release.complete(Unit)

        val state = withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        assertEquals(GameStatistics(), state.statistics)
        assertFalse(state.hasError)
    }

    @Test
    fun savingThroughRepositoryUpdatesTheExistingViewModel() = runBlocking {
        val dao = database.blackjackDao()
        val viewModel = createViewModel(dao)
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        // Un vrai Blackjack naturel, sauvegardé par le repository dans Room.
        val cards = ArrayDeque(listOf(
            Card(Rank.ACE, Suit.HEARTS), Card(Rank.TEN, Suit.CLUBS),
            Card(Rank.KING, Suit.DIAMONDS), Card(Rank.SEVEN, Suit.SPADES)
        ))
        val game = BlackjackGame { cards.removeFirstOrNull() }
        assertTrue(game.startRound())
        BlackjackRepository(dao).saveFinishedGame(game, bet = 5, playedAt = 100)

        val state = withTimeout(5_000) { viewModel.uiState.first { it.statistics.totalGames == 1L } }
        assertEquals(1L, state.statistics.totalHands)
        assertEquals(4L, state.statistics.totalCards)
        assertEquals(1L, state.statistics.naturalBlackjacks)
        assertEquals(7.5, state.statistics.totalGains, 0.0)
        assertEquals(50.0, state.statistics.redPercentage!!, 0.0)
        assertFalse(state.hasError)
    }

    @Test
    fun anInitialReadFailureEndsLoadingAndPublishesAnError() = runBlocking {
        val dao = object : BlackjackDao by database.blackjackDao() {
            override fun observeStatistics(): Flow<GameStatistics> = flow {
                throw IllegalStateException("Simulated initial statistics failure")
            }
        }
        val viewModel = createViewModel(dao)
        val state = withTimeout(5_000) { viewModel.uiState.first { it.hasError } }
        assertFalse(state.isLoading)
        assertEquals(GameStatistics(), state.statistics)
    }

    @Test
    fun aLaterReadFailurePreservesTheLastSummary() = runBlocking {
        val lastSummary = GameStatistics(totalGames = 3, totalHands = 4)
        val dao = object : BlackjackDao by database.blackjackDao() {
            override fun observeStatistics(): Flow<GameStatistics> = flow {
                emit(lastSummary)
                throw IllegalStateException("Simulated later statistics failure")
            }
        }
        val viewModel = createViewModel(dao)
        val state = withTimeout(5_000) { viewModel.uiState.first { it.hasError } }
        assertEquals(lastSummary, state.statistics)
        assertFalse(state.isLoading)
    }

    @Test
    fun clearingTheViewModelCancelsObservationWithoutPublishingAnError() = runBlocking {
        val collecting = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val dao = object : BlackjackDao by database.blackjackDao() {
            override fun observeStatistics(): Flow<GameStatistics> = flow {
                try {
                    emit(GameStatistics())
                    collecting.complete(Unit)
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
        val viewModel = createViewModel(dao)
        withTimeout(5_000) { collecting.await() }
        instrumentation.runOnMainSync { store.clear() }
        withTimeout(5_000) { cancelled.await() }
        assertFalse(viewModel.uiState.value.hasError)
    }

    private fun createViewModel(dao: BlackjackDao): StatisticsViewModel {
        lateinit var viewModel: StatisticsViewModel
        instrumentation.runOnMainSync {
            viewModel = StatisticsViewModel(BlackjackRepository(dao))
            store.put("statistics", viewModel)
        }
        return viewModel
    }
}
