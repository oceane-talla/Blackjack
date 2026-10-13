package com.example.blackjack.database

import androidx.room3.Room
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.dao.BlackjackDao
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import com.example.blackjack.database.entity.PlayerBalanceEntity
import com.example.blackjack.database.entity.ShoeCardEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameSettlementDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "game-settlement-dao-test.db"
    private lateinit var database: BlackjackDatabase
    private lateinit var dao: BlackjackDao

    private fun open() {
        database = Room.databaseBuilder<BlackjackDatabase>(context, name)
            .setDriver(AndroidSQLiteDriver())
            .build()
        dao = database.blackjackDao()
    }

    @Before
    fun setup() {
        context.deleteDatabase(name)
        open()
    }

    @After
    fun cleanup() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun settlementKeepsTheIdAndFinalShoeAndSurvivesReopeningWithoutPayingTwice() = runBlocking {
        val id = saveProgress()
        val finished = finishedGame(id)
        assertEquals(id, dao.finishGameProgress(finished, finishedHands(), finishedCards(), finalShoe()))
        assertEquals(finished, dao.getGame(id))
        assertNull(dao.loadGameProgress())
        assertEquals(listOf(finished), dao.observeHistory().first())
        assertEquals(5L, dao.observeStatistics().first().totalCards)
        assertEquals(1L, dao.observeStatistics().first().totalGames)
        assertEquals(753.5, dao.getBalance()!!, 0.0)
        val savedCards = dao.getDrawnCards(id)
        assertEquals(finishedCards().map { it.copy(gameId = id) }, savedCards.map { it.copy(id = 0) })
        assertEquals(finalShoe().map { it.copy(gameId = id) }, dao.getShoeCards(id))
        // Même état, listes reçues dans un ordre différent et identifiants attribués par Room.
        dao.finishGameProgress(finished, dao.getHands(id), savedCards.reversed(), dao.getShoeCards(id).reversed())
        database.close()
        open()
        assertEquals(finished, dao.getGame(id))
        assertEquals(id, dao.finishGameProgress(finished, finishedHands(), finishedCards(), finalShoe()))
        assertEquals(savedCards, dao.getDrawnCards(id))
        assertEquals(753.5, dao.getBalance()!!, 0.0)
        assertEquals(1, dao.observeHistory().first().size)
        // Le règlement libère la place pour la prochaine progression.
        val nextId = dao.saveGameProgress(game(), hands(), cards(), shoe())
        assertTrue(nextId != id)
        assertEquals(nextId, dao.loadGameProgress()!!.game.id)
    }

    @Test
    fun anInvalidLastDrawnCardPreservesProgressAndDoesNotCreateTheBalance() = runBlocking {
        val id = dao.saveGameProgress(game(), hands(), cards(), shoe())
        val before = dao.loadGameProgress()
        expectConstraintFailure {
            dao.finishGameProgress(
                finishedGame(id), finishedHands(),
                finishedCards() + DrawnCardEntity(0, 0, 2, "TWO", "HEARTS", 6), finalShoe()
            )
        }
        assertEquals(before, dao.loadGameProgress())
        assertTrue(dao.observeHistory().first().isEmpty())
        assertNull(dao.getBalance())
    }

    @Test
    fun failureOnTheLastShoeCardRestoresAllProgressAndLeavesBalanceUnchanged() = runBlocking {
        val id = saveProgress()
        val before = dao.loadGameProgress()
        expectConstraintFailure {
            dao.finishGameProgress(
                finishedGame(id), finishedHands(), finishedCards(), finalShoe() + finalShoe().last()
            )
        }
        assertEquals(before, dao.loadGameProgress())
        assertTrue(dao.observeHistory().first().isEmpty())
        assertEquals(4L, dao.observeStatistics().first().totalCards)
        assertEquals(743.5, dao.getBalance()!!, 0.0)
    }

    @Test
    fun paymentFailureRollsBackFinishedStatusAndCardsThenRetryPaysExactlyOnce() = runBlocking {
        val id = saveProgress()
        val before = dao.loadGameProgress()
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("CREATE TRIGGER reject_settlement BEFORE UPDATE ON player_balance BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        }
        expectConstraintFailure {
            dao.finishGameProgress(finishedGame(id), finishedHands(), finishedCards(), finalShoe())
        }
        assertEquals(before, dao.loadGameProgress())
        assertEquals(743.5, dao.getBalance()!!, 0.0)
        assertTrue(dao.observeHistory().first().isEmpty())
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("DROP TRIGGER reject_settlement")
        }
        repeat(2) {
            dao.finishGameProgress(finishedGame(id), finishedHands(), finishedCards(), finalShoe())
        }
        assertNull(dao.loadGameProgress())
        assertEquals(753.5, dao.getBalance()!!, 0.0)
        assertEquals(1, dao.observeHistory().first().size)
    }

    @Test
    fun unknownIdsAndIncompleteSettlementCannotModifyProgressOrBalance() = runBlocking {
        val id = saveProgress()
        val before = dao.loadGameProgress()
        val invalid = listOf(
            finishedGame(id).copy(id = 0), finishedGame(id).copy(status = "IN_PROGRESS"),
            finishedGame(id).copy(phase = "PLAYER_TURN"), finishedGame(id).copy(result = null),
            finishedGame(id).copy(chipChange = null)
        )
        for (game in invalid) {
            try {
                dao.finishGameProgress(game, finishedHands(), finishedCards(), finalShoe())
                fail("A settlement must be complete and identify an existing game")
            } catch (_: IllegalArgumentException) { }
        }
        try {
            dao.finishGameProgress(finishedGame(99999), finishedHands(), finishedCards(), finalShoe())
            fail("An unknown game cannot be paid")
        } catch (_: IllegalStateException) { }
        try {
            dao.finishGameProgress(finishedGame(id), hands(), finishedCards(), finalShoe())
            fail("All hands must have their final result and payment")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, dao.loadGameProgress())
        assertEquals(743.5, dao.getBalance()!!, 0.0)
    }

    @Test
    fun aDifferentSettlementForAnAlreadyFinishedGameIsRejectedWithoutChangingAnyData() = runBlocking {
        val id = saveProgress()
        val finished = finishedGame(id)
        dao.finishGameProgress(finished, finishedHands(), finishedCards(), finalShoe())
        val savedCards = dao.getDrawnCards(id)
        try {
            dao.finishGameProgress(
                finished.copy(result = "LOSS", chipChange = -10.0),
                finishedHands().map { it.copy(result = "LOSS", chipChange = -10.0) }, finishedCards(), finalShoe()
            )
            fail("A paid result must not change")
        } catch (_: IllegalStateException) { }
        try {
            dao.finishGameProgress(
                finished, finishedHands(), finishedCards().map { it.copy(suit = "SPADES") }, finalShoe()
            )
            fail("A paid game's cards must not change")
        } catch (_: IllegalStateException) { }
        try {
            dao.finishGameProgress(
                finished, finishedHands().map { it.copy(total = 19) }, finishedCards(), finalShoe()
            )
            fail("A paid game's hands must not change")
        } catch (_: IllegalStateException) { }
        try {
            dao.finishGameProgress(finished, finishedHands(), finishedCards(), emptyList())
            fail("A paid game's remaining shoe must not change")
        } catch (_: IllegalStateException) { }
        assertEquals(finished, dao.getGame(id))
        assertEquals(savedCards, dao.getDrawnCards(id))
        assertEquals(753.5, dao.getBalance()!!, 0.0)
    }

    @Test
    fun twoConcurrentSettlementRequestsPayTheSameGameOnlyOnce() = runBlocking {
        val id = saveProgress()
        val ids = List(2) {
            async { dao.finishGameProgress(finishedGame(id), finishedHands(), finishedCards(), finalShoe()) }
        }.awaitAll()
        assertEquals(listOf(id, id), ids)
        assertEquals(753.5, dao.getBalance()!!, 0.0)
        assertEquals(1, dao.observeHistory().first().size)
        assertEquals(5L, dao.observeStatistics().first().totalCards)
    }

    @Test
    fun splitSettlementStoresTwoResultsButOnlyOneGameAndOneNetPayment() = runBlocking {
        dao.insertInitialBalance(PlayerBalanceEntity(chips = 743.5))
        val splitCards = cards().map {
            if (it.drawOrder == 3) it.copy(handNumber = 2, rank = "TEN") else it
        } + listOf(
            DrawnCardEntity(0, 0, 1, "SIX", "HEARTS", 5),
            DrawnCardEntity(0, 0, 2, "SIX", "CLUBS", 6)
        )
        val splitHands = hands().map { it.copy(total = 16) } +
            hands().single().copy(handNumber = 2, total = 16)
        val id = dao.saveGameProgress(
            game().copy(totalBet = 20), splitHands, splitCards,
            listOf(ShoeCardEntity(0, 0, "ACE", "SPADES"),
                ShoeCardEntity(0, 1, "TWO", "CLUBS"), ShoeCardEntity(0, 2, "THREE", "DIAMONDS"))
        )
        val finished = finishedGame(id).copy(totalBet = 20, result = "MIXED", chipChange = 0.0, activeHandIndex = 1)
        val paidHands = listOf(
            HandEntity(0, 1, 10, 19, "WIN", false, 10.0),
            HandEntity(0, 2, 10, 16, "LOSS", false, -10.0)
        )
        val finalCards = splitCards + listOf(
            DrawnCardEntity(0, 0, 1, "THREE", "DIAMONDS", 7),
            DrawnCardEntity(0, 0, null, "TWO", "CLUBS", 8)
        )
        val finalShoe = listOf(ShoeCardEntity(0, 0, "ACE", "SPADES"))
        repeat(2) { dao.finishGameProgress(finished, paidHands, finalCards, finalShoe) }
        val stats = dao.observeStatistics().first()
        assertEquals(1L, stats.totalGames)
        assertEquals(2L, stats.totalHands)
        assertEquals(8L, stats.totalCards)
        assertEquals(1L, stats.winningHands)
        assertEquals(1L, stats.losingHands)
        assertEquals(10.0, stats.totalGains, 0.0)
        assertEquals(10.0, stats.totalLosses, 0.0)
        assertEquals(743.5, dao.getBalance()!!, 0.0)
        assertEquals(listOf("WIN", "LOSS"), dao.getHands(id).map { it.result })
        assertEquals(listOf(finished), dao.observeHistory().first())
    }

    @Test
    fun lossesAndDrawsApplyTheirProvidedNetChangeOnlyOnce() = runBlocking {
        dao.insertInitialBalance(PlayerBalanceEntity(chips = 743.5))
        var expectedBalance = 743.5
        for ((result, change) in listOf("LOSS" to -10.0, "DRAW" to 0.0)) {
            val playerTotal = if (result == "LOSS") 16 else 18
            val playerRank = if (result == "LOSS") "SIX" else "EIGHT"
            val startingCards = cards().map { if (it.drawOrder == 3) it.copy(rank = playerRank) else it }
            val id = dao.saveGameProgress(
                game(), hands().map { it.copy(total = playerTotal) }, startingCards, shoe()
            )
            val finished = finishedGame(id).copy(result = result, chipChange = change)
            val paidHands = finishedHands().map {
                it.copy(total = playerTotal, result = result, chipChange = change)
            }
            val endingCards = startingCards + DrawnCardEntity(0, 0, null, "TWO", "CLUBS", 5)
            repeat(2) { dao.finishGameProgress(finished, paidHands, endingCards, finalShoe()) }
            expectedBalance += change
            assertEquals(expectedBalance, dao.getBalance()!!, 0.0)
        }
        assertEquals(2, dao.observeHistory().first().size)
    }

    private suspend fun saveProgress(): Long {
        dao.insertInitialBalance(PlayerBalanceEntity(chips = 743.5))
        return dao.saveGameProgress(game(), hands(), cards(), shoe())
    }

    private fun game() = GameEntity(
        playedAt = 100, initialBet = 10, totalBet = 10, result = null, chipChange = null,
        dealerTotal = 16, status = "IN_PROGRESS", phase = "PLAYER_TURN", activeHandIndex = 0, deckCount = 4
    )

    private fun finishedGame(id: Long) = game().copy(
        id = id, playedAt = 200, status = "FINISHED", phase = "FINISHED",
        result = "WIN", chipChange = 10.0, dealerTotal = 18
    )

    private fun hands() = listOf(HandEntity(0, 1, 10, 20, null, false, null))
    private fun finishedHands() = listOf(HandEntity(0, 1, 10, 20, "WIN", false, 10.0))

    private fun cards() = listOf(
        DrawnCardEntity(0, 0, 1, "TEN", "HEARTS", 1),
        DrawnCardEntity(0, 0, null, "TEN", "CLUBS", 2),
        DrawnCardEntity(0, 0, 1, "KING", "SPADES", 3),
        DrawnCardEntity(0, 0, null, "SIX", "DIAMONDS", 4)
    )

    private fun finishedCards() = cards() + DrawnCardEntity(0, 0, null, "TWO", "CLUBS", 5)

    private fun shoe() = listOf(
        ShoeCardEntity(0, 0, "ACE", "SPADES"), ShoeCardEntity(0, 1, "TEN", "HEARTS"),
        ShoeCardEntity(0, 2, "TWO", "CLUBS")
    )

    private fun finalShoe() = shoe().take(2)

    private suspend fun expectConstraintFailure(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected a SQLite constraint violation")
        } catch (_: SQLiteException) { }
    }
}
