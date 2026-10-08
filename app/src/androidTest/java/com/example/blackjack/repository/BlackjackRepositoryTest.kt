package com.example.blackjack.repository

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.BlackjackDatabase
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import com.example.blackjack.model.BlackjackGame
import com.example.blackjack.model.Card
import com.example.blackjack.model.GamePhase
import com.example.blackjack.model.Rank
import com.example.blackjack.model.Suit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlackjackRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: BlackjackDatabase
    private lateinit var repository: BlackjackRepository
    private val testDatabaseName = "blackjack-repository-persistence-test.db"

    @Before
    fun createDatabase() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder<BlackjackDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()
        repository = BlackjackRepository(database.blackjackDao())
    }

    @After
    fun closeDatabase() {
        database.close()
        context.deleteDatabase(testDatabaseName)
    }

    @Test
    fun emptyRepositoryReturnsEmptyHistoryAndDetails() = runBlocking {
        assertTrue(repository.observeHistory().first().isEmpty())
        assertTrue(repository.getHands(42).isEmpty())
        assertTrue(repository.getDrawnCards(42).isEmpty())
    }

    @Test
    fun completedGameIsConvertedAndSavedWithEveryCardInGlobalOrder() = runBlocking {
        val game = gameWith(Rank.SEVEN, Rank.TEN, Rank.SEVEN, Rank.SIX, Rank.FOUR, Rank.SIX)
        game.startRound()
        game.hit()
        game.stand()
        val cardsBeforeSaving = game.dealtCards

        val gameId = repository.saveFinishedGame(game, bet = 10, playedAt = 123_456)

        assertTrue(gameId > 0)
        assertEquals(listOf(GameEntity(gameId, 123_456, 10, 10, "WIN", 10.0, 22)),
            repository.observeHistory().first())
        assertEquals(listOf(HandEntity(gameId, 1, 10, 18, "WIN", false, 10.0)),
            repository.getHands(gameId))
        val cards = repository.getDrawnCards(gameId)
        assertEquals(listOf("SEVEN", "TEN", "SEVEN", "SIX", "FOUR", "SIX"), cards.map { it.rank })
        assertEquals(listOf("CLUBS", "DIAMONDS", "HEARTS", "SPADES", "CLUBS", "DIAMONDS"),
            cards.map { it.suit })
        assertEquals(listOf<Int?>(1, null, 1, null, 1, null), cards.map { it.handNumber })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), cards.map { it.drawOrder })
        assertTrue(cards.all { it.gameId == gameId && it.id > 0 })
        assertEquals(6, cards.map { it.id }.toSet().size)
        assertEquals(cardsBeforeSaving, game.dealtCards)
        assertEquals(GamePhase.FINISHED, game.phase)
    }

    @Test
    fun naturalBlackjackStoresTheHalfChipPaymentAndNaturalFlag() = runBlocking {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        game.startRound()

        val gameId = repository.saveFinishedGame(game, bet = 5, playedAt = 1_000)

        assertEquals(listOf(GameEntity(gameId, 1_000, 5, 5, "WIN", 7.5, 19)),
            repository.observeHistory().first())
        assertEquals(listOf(HandEntity(gameId, 1, 5, 21, "WIN", true, 7.5)),
            repository.getHands(gameId))
        assertEquals(4, repository.getDrawnCards(gameId).size)
    }

    @Test
    fun twentyOneWithThreeCardsStoresAnOrdinaryPayment() = runBlocking {
        val game = gameWith(Rank.SEVEN, Rank.TEN, Rank.SEVEN, Rank.SEVEN, Rank.SEVEN)
        game.startRound()
        game.hit()

        val gameId = repository.saveFinishedGame(game, bet = 10, playedAt = 1_000)

        assertEquals(listOf(GameEntity(gameId, 1_000, 10, 10, "WIN", 10.0, 17)),
            repository.observeHistory().first())
        assertEquals(listOf(HandEntity(gameId, 1, 10, 21, "WIN", false, 10.0)),
            repository.getHands(gameId))
        assertEquals(listOf<Int?>(1, null, 1, null, 1),
            repository.getDrawnCards(gameId).map { it.handNumber })
    }

    @Test
    fun lossesStoreNegativeChipChangesIncludingBustAndDealerBlackjack() = runBlocking {
        val higherDealer = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.NINE)
        higherDealer.startRound()
        higherDealer.stand()
        val playerBust = gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX, Rank.THREE)
        playerBust.startRound()
        playerBust.hit()
        val dealerBlackjack = gameWith(Rank.TEN, Rank.ACE, Rank.NINE, Rank.QUEEN)
        dealerBlackjack.startRound()

        for ((game, expectedTotal) in listOf(higherDealer to 18, playerBust to 22, dealerBlackjack to 19)) {
            val id = repository.saveFinishedGame(game, bet = 10)
            val savedGame = repository.observeHistory().first().first { it.id == id }
            assertEquals("LOSS", savedGame.result)
            assertEquals(-10.0, savedGame.chipChange, 0.0)
            assertEquals(listOf(HandEntity(id, 1, 10, expectedTotal, "LOSS", false, -10.0)),
                repository.getHands(id))
        }
    }

    @Test
    fun drawsStoreZeroChipChangeEvenWhenBothPlayersHaveBlackjack() = runBlocking {
        val equalTotals = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.EIGHT)
        equalTotals.startRound()
        equalTotals.stand()
        val bothBlackjack = gameWith(Rank.ACE, Rank.ACE, Rank.TEN, Rank.KING)
        bothBlackjack.startRound()

        for ((game, natural) in listOf(equalTotals to false, bothBlackjack to true)) {
            val id = repository.saveFinishedGame(game, bet = 10)
            val savedGame = repository.observeHistory().first().first { it.id == id }
            assertEquals("DRAW", savedGame.result)
            assertEquals(0.0, savedGame.chipChange, 0.0)
            val hand = repository.getHands(id).single()
            assertEquals("DRAW", hand.result)
            assertEquals(0.0, hand.chipChange, 0.0)
            assertEquals(natural, hand.isNaturalBlackjack)
        }
    }

    @Test
    fun readyActiveAndInterruptedGamesAreRejectedWithoutWritingAnything() = runBlocking {
        val ready = gameWith()
        val active = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.NINE)
        active.startRound()
        val interruptedDistribution = gameWith(Rank.TEN)
        interruptedDistribution.startRound()
        val interruptedDealer = gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX)
        interruptedDealer.startRound()
        interruptedDealer.stand()

        for (game in listOf(ready, active, interruptedDistribution, interruptedDealer)) {
            expectInvalidSave { repository.saveFinishedGame(game, bet = 10) }
            assertTrue(repository.observeHistory().first().isEmpty())
        }
        assertTrue(repository.getHands(1).isEmpty())
        assertTrue(repository.getDrawnCards(1).isEmpty())
    }

    @Test
    fun invalidBetsAreRejectedWithoutChangingPreviouslySavedData() = runBlocking {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        game.startRound()
        val id = repository.saveFinishedGame(game, bet = 10, playedAt = 1_000)
        val history = repository.observeHistory().first()
        val hands = repository.getHands(id)
        val cards = repository.getDrawnCards(id)

        for (bet in listOf(0, -1, Int.MIN_VALUE)) {
            expectInvalidSave { repository.saveFinishedGame(game, bet = bet) }
            assertEquals(history, repository.observeHistory().first())
            assertEquals(hands, repository.getHands(id))
            assertEquals(cards, repository.getDrawnCards(id))
        }
    }

    @Test
    fun historyFlowPublishesTheSavedGameAndItsDetailsAreReadable() = runBlocking {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        game.startRound()
        withTimeout(10_000) {
            val firstEmission = CompletableDeferred<Unit>()
            val emissions = async {
                repository.observeHistory()
                    .onEach { if (it.isEmpty()) firstEmission.complete(Unit) }
                    .take(2)
                    .toList()
            }
            firstEmission.await()
            val id = repository.saveFinishedGame(game, bet = 5, playedAt = 1_000)
            val history = emissions.await()
            assertTrue(history.first().isEmpty())
            assertEquals(listOf(GameEntity(id, 1_000, 5, 5, "WIN", 7.5, 19)), history.last())
            assertEquals(1, repository.getHands(id).size)
            assertEquals(4, repository.getDrawnCards(id).size)
        }
    }

    @Test
    fun repositoryHistoryRemainsSortedByDateThenId() = runBlocking {
        val first = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        first.startRound()
        val firstId = repository.saveFinishedGame(first, 10, playedAt = 2_000)
        val second = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        second.startRound()
        val secondId = repository.saveFinishedGame(second, 10, playedAt = 1_000)
        val third = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        third.startRound()
        val thirdId = repository.saveFinishedGame(third, 10, playedAt = 2_000)

        assertEquals(listOf(thirdId, firstId, secondId),
            repository.observeHistory().first().map { it.id })
    }

    @Test
    fun startingAnotherRoundDoesNotReplaceTheSavedRoundOrMixItsCards() = runBlocking {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE,
            Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.EIGHT)
        game.startRound()
        val firstId = repository.saveFinishedGame(game, 5, playedAt = 1_000)
        val firstCards = repository.getDrawnCards(firstId)
        game.startRound()
        game.stand()
        val secondId = repository.saveFinishedGame(game, 10, playedAt = 2_000)

        assertTrue(firstId != secondId)
        assertEquals(firstCards, repository.getDrawnCards(firstId))
        assertEquals(listOf(HandEntity(firstId, 1, 5, 21, "WIN", true, 7.5)),
            repository.getHands(firstId))
        assertEquals(listOf(HandEntity(secondId, 1, 10, 18, "DRAW", false, 0.0)),
            repository.getHands(secondId))
        assertEquals(listOf("TEN", "TEN", "EIGHT", "EIGHT"),
            repository.getDrawnCards(secondId).map { it.rank })
        assertEquals(listOf(1, 2, 3, 4), repository.getDrawnCards(secondId).map { it.drawOrder })
    }

    @Test
    fun savedGameAndConvertedDetailsSurviveClosingAndReopeningTheDatabase() = runBlocking {
        database.close()
        context.deleteDatabase(testDatabaseName)
        database = openFileDatabase()
        repository = BlackjackRepository(database.blackjackDao())
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        game.startRound()
        val id = repository.saveFinishedGame(game, 5, playedAt = 1_000)
        val cards = repository.getDrawnCards(id)

        database.close()
        database = openFileDatabase()
        repository = BlackjackRepository(database.blackjackDao())

        assertEquals(listOf(GameEntity(id, 1_000, 5, 5, "WIN", 7.5, 19)),
            repository.observeHistory().first())
        assertEquals(listOf(HandEntity(id, 1, 5, 21, "WIN", true, 7.5)), repository.getHands(id))
        assertEquals(cards, repository.getDrawnCards(id))
    }

    private fun openFileDatabase(): BlackjackDatabase =
        Room.databaseBuilder<BlackjackDatabase>(context, testDatabaseName)
            .setDriver(AndroidSQLiteDriver())
            .build()

    private fun gameWith(vararg ranks: Rank): BlackjackGame {
        val cards = ranks.mapIndexed { index, rank ->
            Card(rank, Suit.entries[index % Suit.entries.size])
        }.toMutableList()
        return BlackjackGame { if (cards.isEmpty()) null else cards.removeAt(0) }
    }

    private suspend fun expectInvalidSave(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected an invalid save to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Les paramètres invalides doivent être refusés avant toute écriture.
        }
    }
}
