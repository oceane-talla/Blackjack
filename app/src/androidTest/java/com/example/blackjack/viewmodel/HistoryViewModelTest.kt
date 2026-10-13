package com.example.blackjack.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.BlackjackDatabase
import com.example.blackjack.database.dao.BlackjackDao
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.repository.BlackjackRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
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
class HistoryViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: BlackjackDatabase
    private val store = ViewModelStore()

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder<BlackjackDatabase>(instrumentation.targetContext)
            .setDriver(AndroidSQLiteDriver())
            .build()
    }

    @After
    fun closeDatabase() {
        instrumentation.runOnMainSync { store.clear() }
        database.close()
    }

    @Test
    fun loadingEndsWhenRoomReturnsAnEmptyHistory() = runBlocking {
        // Retarde uniquement l'observation pour vérifier le chargement sans dépendre du timing.
        val release = CompletableDeferred<Unit>()
        val realDao = database.blackjackDao()
        val delayedDao = object : BlackjackDao by realDao {
            override fun observeHistory(): Flow<List<GameEntity>> = flow {
                release.await()
                emitAll(realDao.observeHistory())
            }
        }
        val viewModel = createViewModel(delayedDao)
        assertTrue(viewModel.uiState.value.isLoading)
        assertFalse(viewModel.uiState.value.hasError)
        release.complete(Unit)

        val state = withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        assertTrue(state.games.isEmpty())
        assertFalse(state.hasError)
    }

    @Test
    fun savedGamesAreObservedInDescendingDateAndIdOrder() = runBlocking {
        val dao = database.blackjackDao()
        val olderId = dao.insertGame(game(100))
        val newerId = dao.insertGame(game(200))
        val tiedId = dao.insertGame(game(200))
        val viewModel = createViewModel(dao)

        val state = withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        assertEquals(listOf(tiedId, newerId, olderId), state.games.map { it.id })
        assertFalse(state.hasError)
    }

    @Test
    fun insertingAnotherGameUpdatesTheSameViewModelAutomatically() = runBlocking {
        val dao = database.blackjackDao()
        val viewModel = createViewModel(dao)
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }

        val id = dao.insertGame(game(300))
        val state = withTimeout(5_000) { viewModel.uiState.first { it.games.size == 1 } }
        assertEquals(id, state.games.single().id)
        assertFalse(state.isLoading)
        assertFalse(state.hasError)
    }

    @Test
    fun readFailureKeepsTheLastListAndEndsLoading() = runBlocking {
        val lastGames = listOf(game(400).copy(id = 42))
        // Simule seulement une erreur du flux de lecture ; aucun changement du code de production.
        val failingDao = object : BlackjackDao by database.blackjackDao() {
            override fun observeHistory(): Flow<List<GameEntity>> = flow {
                emit(lastGames)
                throw IllegalStateException("Simulated history read failure")
            }
        }
        val viewModel = createViewModel(failingDao)

        val state = withTimeout(5_000) { viewModel.uiState.first { it.hasError } }
        assertEquals(lastGames, state.games)
        assertFalse(state.isLoading)
    }

    @Test
    fun observationStartsWithASubscriberAndStopsAfterTheTimeout() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val dao = object : BlackjackDao by database.blackjackDao() {
            override fun observeHistory(): Flow<List<GameEntity>> = flow {
                started.complete(Unit)
                try {
                    emit(emptyList())
                    awaitCancellation()
                } finally {
                    stopped.complete(Unit)
                }
            }
        }
        val viewModel = createViewModel(dao)
        assertFalse(started.isCompleted)
        // Lire value ne crée pas d'abonnement; collect représente l'écran actif.
        assertTrue(viewModel.uiState.value.isLoading)
        val subscriber = launch { viewModel.uiState.collect() }
        withTimeout(5_000) { started.await() }
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        subscriber.cancel()
        subscriber.join()
        delay(1_000)
        assertFalse(stopped.isCompleted)
        withTimeout(7_000) { stopped.await() }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun resubscribingReadsGamesSavedWhileObservationWasStopped() = runBlocking {
        val dao = database.blackjackDao()
        val viewModel = createViewModel(dao)
        withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
        delay(6_000)
        val id = dao.insertGame(game(500))
        val state = withTimeout(5_000) { viewModel.uiState.first { it.games.isNotEmpty() } }
        assertEquals(id, state.games.single().id)
    }

    private fun createViewModel(dao: BlackjackDao): HistoryViewModel {
        lateinit var viewModel: HistoryViewModel
        instrumentation.runOnMainSync {
            viewModel = HistoryViewModel(BlackjackRepository(dao))
            store.put("history", viewModel)
        }
        return viewModel
    }

    private fun game(playedAt: Long) = GameEntity(
        playedAt = playedAt,
        initialBet = 10,
        totalBet = 10,
        result = "WIN",
        chipChange = 10.0,
        dealerTotal = 18
    )
}
