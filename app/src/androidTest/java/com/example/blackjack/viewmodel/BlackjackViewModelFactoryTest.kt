package com.example.blackjack.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.BlackjackDatabase
import com.example.blackjack.model.BlackjackGame
import com.example.blackjack.model.Card
import com.example.blackjack.model.Rank
import com.example.blackjack.model.Suit
import com.example.blackjack.repository.BlackjackRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlackjackViewModelFactoryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: BlackjackDatabase
    private lateinit var repository: BlackjackRepository
    private lateinit var factory: BlackjackViewModelFactory
    private val stores = mutableListOf<ViewModelStore>()

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder<BlackjackDatabase>(instrumentation.targetContext)
            .setDriver(AndroidSQLiteDriver()).build()
        repository = BlackjackRepository(database.blackjackDao())
        factory = BlackjackViewModelFactory(repository)
    }

    @After
    fun closeDatabase() {
        instrumentation.runOnMainSync { stores.forEach { it.clear() } }
        database.close()
    }

    @Test
    fun providerCreatesAllThreeSupportedViewModels() = runBlocking {
        lateinit var game: GameViewModel
        lateinit var history: HistoryViewModel
        lateinit var statistics: StatisticsViewModel
        instrumentation.runOnMainSync {
            val provider = newProvider()
            game = provider[GameViewModel::class.java]
            history = provider[HistoryViewModel::class.java]
            statistics = provider[StatisticsViewModel::class.java]
        }
        withTimeout(5_000) { game.uiState.first { !it.isLoadingBalance } }
        assertEquals(1_000.0, game.uiState.value.chips, 0.0)
        val historyState = withTimeout(5_000) { history.uiState.first { !it.isLoading } }
        val statisticsState = withTimeout(5_000) { statistics.uiState.first { !it.isLoading } }
        assertTrue(historyState.games.isEmpty())
        assertEquals(0L, statisticsState.statistics.totalGames)
        assertFalse(historyState.hasError)
        assertFalse(statisticsState.hasError)
    }

    @Test
    fun factoryRejectsAnUnsupportedViewModel() {
        assertThrows(IllegalArgumentException::class.java) {
            factory.create(UnsupportedViewModel::class.java)
        }
    }

    @Test
    fun anotherProviderAndFactoryReuseTheSameOwnersViewModel() = runBlocking {
        lateinit var first: GameViewModel
        lateinit var second: GameViewModel
        instrumentation.runOnMainSync {
            val store = ViewModelStore().also { stores.add(it) }
            first = ViewModelProvider(store, factory)[GameViewModel::class.java]
            second = ViewModelProvider(store, BlackjackViewModelFactory(repository))[
                GameViewModel::class.java
            ]
        }
        withTimeout(5_000) { first.uiState.first { !it.isLoadingBalance } }
        instrumentation.runOnMainSync {
            assertTrue(first.selectBet(25))
            assertSame(first, second)
            assertEquals(25, second.uiState.value.selectedBet)
        }
    }

    @Test
    fun differentOwnersReceiveDifferentGameViewModels() = runBlocking {
        lateinit var first: GameViewModel
        lateinit var second: GameViewModel
        instrumentation.runOnMainSync {
            first = newProvider()[GameViewModel::class.java]
            second = newProvider()[GameViewModel::class.java]
        }
        withTimeout(5_000) { first.uiState.first { !it.isLoadingBalance } }
        instrumentation.runOnMainSync {
            assertTrue(first.selectBet(25))
            assertNotSame(first, second)
            assertEquals(1, second.uiState.value.selectedBet)
        }
    }

    @Test
    fun createdReadersObserveTheRepositoryGivenToTheFactory() = runBlocking {
        lateinit var history: HistoryViewModel
        lateinit var statistics: StatisticsViewModel
        instrumentation.runOnMainSync {
            val provider = newProvider()
            history = provider[HistoryViewModel::class.java]
            statistics = provider[StatisticsViewModel::class.java]
        }
        withTimeout(5_000) { history.uiState.first { !it.isLoading } }
        withTimeout(5_000) { statistics.uiState.first { !it.isLoading } }
        val cards = ArrayDeque(listOf(
            Card(Rank.ACE, Suit.HEARTS), Card(Rank.TEN, Suit.SPADES),
            Card(Rank.KING, Suit.DIAMONDS), Card(Rank.SEVEN, Suit.CLUBS)
        ))
        val game = BlackjackGame { cards.removeFirstOrNull() }
        assertTrue(game.startRound())
        val id = repository.saveFinishedGame(game, bet = 7, playedAt = 100)

        val historyState = withTimeout(5_000) { history.uiState.first { it.games.isNotEmpty() } }
        val statisticsState = withTimeout(5_000) {
            statistics.uiState.first { it.statistics.totalGames == 1L }
        }
        assertEquals(id, historyState.games.single().id)
        assertEquals(7, historyState.games.single().initialBet)
        assertEquals(14.0, statisticsState.statistics.totalGains, 0.0)
    }

    private fun newProvider(): ViewModelProvider {
        val store = ViewModelStore().also { stores.add(it) }
        return ViewModelProvider(store, factory)
    }

    private class UnsupportedViewModel : ViewModel()
}
