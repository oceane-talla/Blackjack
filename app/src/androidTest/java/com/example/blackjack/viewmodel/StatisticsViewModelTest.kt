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
import com.example.blackjack.model.RankStatistics
import com.example.blackjack.model.Suit
import com.example.blackjack.repository.BlackjackRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
        assertEquals(13, state.rankStatistics.size)
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

        val state = withTimeout(5_000) {
            viewModel.uiState.first {
                it.statistics.totalGames == 1L && it.rankStatistics.sumOf { rank -> rank.cardCount } == 4L
            }
        }
        assertEquals(1L, state.statistics.totalHands)
        assertEquals(4L, state.statistics.totalCards)
        assertEquals(1L, state.statistics.naturalBlackjacks)
        assertEquals(10.0, state.statistics.totalGains, 0.0)
        assertEquals(50.0, state.statistics.redPercentage!!, 0.0)
        val king = state.rankStatistics.single { it.rank == "KING" }
        assertEquals(1L, king.cardCount)
        assertEquals(25.0, king.percentage!!, 0.0)
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
        val subscriber = launch { viewModel.uiState.collect() }
        withTimeout(5_000) { collecting.await() }
        instrumentation.runOnMainSync { store.clear() }
        withTimeout(5_000) { cancelled.await() }
        subscriber.cancel()
        assertFalse(viewModel.uiState.value.hasError)
    }

    @Test
    fun loadingWaitsForTheRankQueryToo() = runBlocking {
        val summaryReceived = CompletableDeferred<Unit>()
        val releaseRanks = CompletableDeferred<Unit>()
        val realDao = database.blackjackDao()
        val dao = object : BlackjackDao by realDao {
            override fun observeStatistics(): Flow<GameStatistics> = flow {
                emit(GameStatistics(totalGames = 2))
                summaryReceived.complete(Unit)
                awaitCancellation()
            }
            override fun observeRankStatistics(): Flow<List<RankStatistics>> = flow {
                releaseRanks.await()
                emitAll(realDao.observeRankStatistics())
            }
        }
        val viewModel = createViewModel(dao)
        val subscriber = launch { viewModel.uiState.collect() }
        withTimeout(5_000) { summaryReceived.await() }
        assertTrue(viewModel.uiState.value.isLoading)
        releaseRanks.complete(Unit)
        val state = withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        assertEquals(2L, state.statistics.totalGames)
        assertEquals(13, state.rankStatistics.size)
        subscriber.cancel()
    }

    @Test
    fun rankReadFailureKeepsItsLastValuesAndTheSummary() = runBlocking {
        val failRanks = CompletableDeferred<Unit>()
        val lastRanks = listOf(RankStatistics("KING", 2, 100.0))
        val dao = object : BlackjackDao by database.blackjackDao() {
            override fun observeRankStatistics(): Flow<List<RankStatistics>> = flow {
                emit(lastRanks)
                failRanks.await()
                throw IllegalStateException("Simulated rank statistics failure")
            }
        }
        val viewModel = createViewModel(dao)
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        failRanks.complete(Unit)
        val state = withTimeout(5_000) { viewModel.uiState.first { it.hasError } }
        assertEquals(lastRanks, state.rankStatistics)
        assertEquals(GameStatistics(), state.statistics)
        assertFalse(state.isLoading)
    }

    @Test
    fun bothQueriesStopWithoutSubscribersAndRestartWithFreshData() = runBlocking {
        val summaryStarted = CompletableDeferred<Unit>()
        val ranksStarted = CompletableDeferred<Unit>()
        val summaryStopped = CompletableDeferred<Unit>()
        val ranksStopped = CompletableDeferred<Unit>()
        val realDao = database.blackjackDao()
        val dao = object : BlackjackDao by realDao {
            override fun observeStatistics(): Flow<GameStatistics> = flow {
                summaryStarted.complete(Unit)
                try { emitAll(realDao.observeStatistics()) }
                finally { summaryStopped.complete(Unit) }
            }
            override fun observeRankStatistics(): Flow<List<RankStatistics>> = flow {
                ranksStarted.complete(Unit)
                try { emitAll(realDao.observeRankStatistics()) }
                finally { ranksStopped.complete(Unit) }
            }
        }
        val viewModel = createViewModel(dao)
        assertFalse(summaryStarted.isCompleted)
        assertFalse(ranksStarted.isCompleted)
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        delay(1_000)
        assertFalse(summaryStopped.isCompleted)
        assertFalse(ranksStopped.isCompleted)
        withTimeout(7_000) { summaryStopped.await(); ranksStopped.await() }
        assertEquals(13, viewModel.uiState.value.rankStatistics.size)

        val cards = ArrayDeque(listOf(
            Card(Rank.ACE, Suit.HEARTS), Card(Rank.TEN, Suit.CLUBS),
            Card(Rank.KING, Suit.DIAMONDS), Card(Rank.SEVEN, Suit.SPADES)
        ))
        val game = BlackjackGame { cards.removeFirstOrNull() }
        assertTrue(game.startRound())
        BlackjackRepository(realDao).saveFinishedGame(game, 5)
        val state = withTimeout(5_000) {
            viewModel.uiState.first {
                it.statistics.totalGames == 1L && it.rankStatistics.sumOf { rank -> rank.cardCount } == 4L
            }
        }
        assertEquals(25.0, state.rankStatistics.single { it.rank == "KING" }.percentage!!, 0.0)
    }

    @Test
    fun aRankFailureIsPublishedEvenWhileTheSummaryIsStillWaiting() = runBlocking {
        val dao = object : BlackjackDao by database.blackjackDao() {
            override fun observeStatistics(): Flow<GameStatistics> = flow { awaitCancellation() }
            override fun observeRankStatistics(): Flow<List<RankStatistics>> = flow {
                throw IllegalStateException("Simulated initial rank failure")
            }
        }
        val viewModel = createViewModel(dao)
        val state = withTimeout(5_000) { viewModel.uiState.first { it.hasError } }
        assertFalse(state.isLoading)
        assertEquals(GameStatistics(), state.statistics)
        assertTrue(state.rankStatistics.isEmpty())
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
