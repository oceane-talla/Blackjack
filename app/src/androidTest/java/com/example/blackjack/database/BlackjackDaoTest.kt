package com.example.blackjack.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.dao.BlackjackDao
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import com.example.blackjack.database.entity.PlayerBalanceEntity
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
class BlackjackDaoTest {
    private lateinit var context: Context
    private lateinit var database: BlackjackDatabase
    private lateinit var dao: BlackjackDao
    private val testDatabaseName = "blackjack-dao-persistence-test.db"

    @Before
    fun createDatabase() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder<BlackjackDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()
        dao = database.blackjackDao()
    }

    @After
    fun closeDatabase() {
        database.close()
        context.deleteDatabase(testDatabaseName)
    }

    @Test
    fun emptyDatabaseHasNoHistoryHandsOrCards() = runBlocking {
        assertTrue(dao.observeHistory().first().isEmpty())
        assertTrue(dao.getHands(42).isEmpty())
        assertTrue(dao.getDrawnCards(42).isEmpty())
    }

    @Test
    fun resetBalanceRestoresOneThousandChips() = runBlocking {
        dao.insertInitialBalance(PlayerBalanceEntity(chips = 0.0))

        assertEquals(1_000.0, dao.resetBalanceToInitial(), 0.0)
        assertEquals(1_000.0, dao.getBalance()!!, 0.0)
    }

    @Test
    fun transactionSavesTheGameAndLinksItsHandsAndCards() = runBlocking {
        val hand = sampleHand()
        val cards = sampleCards()

        val gameId = dao.insertGameWithDetails(sampleGame(), listOf(hand), cards)

        assertTrue(gameId > 0)
        assertEquals(listOf(sampleGame().copy(id = gameId)), dao.observeHistory().first())
        assertEquals(listOf(hand.copy(gameId = gameId)), dao.getHands(gameId))
        val storedCards = dao.getDrawnCards(gameId)
        assertEquals(4, storedCards.size)
        assertTrue(storedCards.all { it.gameId == gameId && it.id > 0 })
        assertEquals(4, storedCards.map { it.id }.toSet().size)
        assertEquals(cards, storedCards.map { it.copy(id = 0, gameId = 0) })
    }

    @Test
    fun splitKeepsOneGameWithTwoDifferentHandResults() = runBlocking {
        val firstHand = sampleHand(handNumber = 1)
        val secondHand = sampleHand(handNumber = 2).copy(result = "LOSS", chipChange = -10.0)
        val cards = sampleCards() + DrawnCardEntity(
            gameId = 0, handNumber = 2, rank = "EIGHT", suit = "SPADES", drawOrder = 5
        )

        val gameId = dao.insertGameWithDetails(
            // Valeur de test : la convention du résultat résumé reste à choisir.
            sampleGame().copy(totalBet = 20, result = "TEST_SPLIT", chipChange = 0.0),
            listOf(secondHand, firstHand),
            cards
        )

        assertEquals(1, dao.observeHistory().first().size)
        assertEquals(listOf(1, 2), dao.getHands(gameId).map { it.handNumber })
        assertEquals(listOf("WIN", "LOSS"), dao.getHands(gameId).map { it.result })
        assertEquals(listOf(1, null, 1, null, 2), dao.getDrawnCards(gameId).map { it.handNumber })
    }

    @Test
    fun historyUsesTheDateThenTheIdForNewestFirst() = runBlocking {
        val oldestId = dao.insertGame(sampleGame(playedAt = 1_000))
        val newestId = dao.insertGame(sampleGame(playedAt = 3_000))
        val middleId = dao.insertGame(sampleGame(playedAt = 2_000))
        val newestSameDateId = dao.insertGame(sampleGame(playedAt = 3_000))

        assertEquals(
            listOf(newestSameDateId, newestId, middleId, oldestId),
            dao.observeHistory().first().map { it.id }
        )
    }

    @Test
    fun historyFlowEmitsAgainWhenACompleteGameIsSaved() = runBlocking {
        withTimeout(10_000) {
            val firstEmission = CompletableDeferred<Unit>()
            val emissions = async {
                dao.observeHistory()
                    .onEach { if (it.isEmpty()) firstEmission.complete(Unit) }
                    .take(2)
                    .toList()
            }
            firstEmission.await()

            val gameId = dao.insertGameWithDetails(sampleGame(), listOf(sampleHand()), sampleCards())

            val lists = emissions.await()
            assertTrue(lists.first().isEmpty())
            assertEquals(listOf(gameId), lists.last().map { it.id })
            assertEquals(1, dao.getHands(gameId).size)
            assertEquals(4, dao.getDrawnCards(gameId).size)
        }
    }

    @Test
    fun cardsAreReadInDistributionOrderRegardlessOfInsertionOrder() = runBlocking {
        val cards = sampleCards()
        val gameId = dao.insertGameWithDetails(sampleGame(), listOf(sampleHand()), cards.reversed())

        val storedCards = dao.getDrawnCards(gameId)

        assertEquals(listOf(1, 2, 3, 4), storedCards.map { it.drawOrder })
        assertEquals(cards.map { it.rank }, storedCards.map { it.rank })
    }

    @Test
    fun sameHandNumberCanBelongToDifferentGames() = runBlocking {
        val firstId = dao.insertGameWithDetails(sampleGame(), listOf(sampleHand()), sampleCards())
        val secondId = dao.insertGameWithDetails(sampleGame(), listOf(sampleHand()), sampleCards())

        assertTrue(firstId != secondId)
        assertEquals(listOf(sampleHand(gameId = firstId)), dao.getHands(firstId))
        assertEquals(listOf(sampleHand(gameId = secondId)), dao.getHands(secondId))
    }

    @Test
    fun missingGameIsRejectedForHandsAndDealerCards() = runBlocking {
        expectConstraintViolation { dao.insertHands(listOf(sampleHand(gameId = 42))) }
        expectConstraintViolation {
            dao.insertDrawnCards(listOf(sampleCards()[1].copy(gameId = 42)))
        }

        assertTrue(dao.getHands(42).isEmpty())
        assertTrue(dao.getDrawnCards(42).isEmpty())
    }

    @Test
    fun playerCardCannotReferToAHandFromAnotherGame() = runBlocking {
        val firstId = dao.insertGame(sampleGame())
        val secondId = dao.insertGame(sampleGame())
        dao.insertHands(listOf(sampleHand(gameId = firstId)))

        expectConstraintViolation {
            dao.insertDrawnCards(listOf(sampleCards().first().copy(gameId = secondId)))
        }

        assertTrue(dao.getDrawnCards(secondId).isEmpty())
        assertEquals(1, dao.getHands(firstId).size)
    }

    @Test
    fun dealerCardWithNullHandNumberOnlyNeedsAnExistingGame() = runBlocking {
        val gameId = dao.insertGame(sampleGame())
        dao.insertDrawnCards(listOf(sampleCards()[1].copy(gameId = gameId)))

        assertEquals(listOf<Int?>(null), dao.getDrawnCards(gameId).map { it.handNumber })
        assertTrue(dao.getHands(gameId).isEmpty())
    }

    @Test
    fun invalidCardRollsBackTheGameItsHandAndPreviouslyInsertedCards() = runBlocking {
        val cards = sampleCards() + sampleCards().first().copy(handNumber = 2, drawOrder = 5)

        expectConstraintViolation {
            dao.insertGameWithDetails(sampleGame(id = 42), listOf(sampleHand()), cards)
        }

        assertEmptyGame(42)
    }

    @Test
    fun duplicateDistributionOrderRollsBackTheCompleteSave() = runBlocking {
        val cards = sampleCards() + sampleCards().first().copy(rank = "TWO")

        expectConstraintViolation {
            dao.insertGameWithDetails(sampleGame(id = 42), listOf(sampleHand()), cards)
        }

        assertEmptyGame(42)
    }

    @Test
    fun duplicateHandRollsBackTheCompleteSave() = runBlocking {
        expectConstraintViolation {
            dao.insertGameWithDetails(
                sampleGame(id = 42), listOf(sampleHand(), sampleHand()), sampleCards()
            )
        }

        assertEmptyGame(42)
    }

    @Test
    fun equalRankAndSuitAreAllowedAtDifferentDistributionPositions() = runBlocking {
        val repeatedCards = sampleCards().map { it.copy(rank = "TEN", suit = "HEARTS") }

        val gameId = dao.insertGameWithDetails(sampleGame(), listOf(sampleHand()), repeatedCards)

        assertEquals(4, dao.getDrawnCards(gameId).size)
        assertEquals(1, dao.getDrawnCards(gameId).map { it.rank to it.suit }.toSet().size)
    }

    @Test
    fun fileDatabaseKeepsGamesHandsAndCardsAfterClosingAndReopening() = runBlocking {
        database.close()
        context.deleteDatabase(testDatabaseName)
        database = openFileDatabase()
        dao = database.blackjackDao()
        val gameId = dao.insertGameWithDetails(sampleGame(), listOf(sampleHand()), sampleCards())
        val savedCards = dao.getDrawnCards(gameId)

        database.close()
        database = openFileDatabase()
        dao = database.blackjackDao()

        assertEquals(listOf(sampleGame().copy(id = gameId)), dao.observeHistory().first())
        assertEquals(listOf(sampleHand(gameId = gameId)), dao.getHands(gameId))
        assertEquals(savedCards, dao.getDrawnCards(gameId))
    }

    private fun openFileDatabase(): BlackjackDatabase =
        Room.databaseBuilder<BlackjackDatabase>(context, testDatabaseName)
            .setDriver(AndroidSQLiteDriver())
            .build()

    private fun sampleGame(id: Long = 0, playedAt: Long = 1_000) = GameEntity(
        id = id, playedAt = playedAt, initialBet = 10, totalBet = 10,
        result = "WIN", chipChange = 10.0, dealerTotal = 18
    )

    private fun sampleHand(gameId: Long = 0, handNumber: Int = 1) = HandEntity(
        gameId = gameId, handNumber = handNumber, bet = 10, total = 20,
        result = "WIN", isNaturalBlackjack = false, chipChange = 10.0
    )

    private fun sampleCards() = listOf(
        DrawnCardEntity(gameId = 0, handNumber = 1, rank = "TEN", suit = "HEARTS", drawOrder = 1),
        DrawnCardEntity(gameId = 0, handNumber = null, rank = "TEN", suit = "CLUBS", drawOrder = 2),
        DrawnCardEntity(gameId = 0, handNumber = 1, rank = "KING", suit = "SPADES", drawOrder = 3),
        DrawnCardEntity(gameId = 0, handNumber = null, rank = "EIGHT", suit = "DIAMONDS", drawOrder = 4)
    )

    private suspend fun expectConstraintViolation(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected a SQLite constraint violation.")
        } catch (exception: SQLiteException) {
            assertTrue(exception.message.orEmpty().contains("constraint", ignoreCase = true))
        }
    }

    private suspend fun assertEmptyGame(gameId: Long) {
        assertTrue(dao.observeHistory().first().isEmpty())
        assertTrue(dao.getHands(gameId).isEmpty())
        assertTrue(dao.getDrawnCards(gameId).isEmpty())
    }
}
