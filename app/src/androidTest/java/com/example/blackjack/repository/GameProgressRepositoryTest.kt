package com.example.blackjack.repository

import androidx.room3.Room
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.BlackjackDatabase
import com.example.blackjack.model.BlackjackGame
import com.example.blackjack.model.Card
import com.example.blackjack.model.GamePhase
import com.example.blackjack.model.Rank
import com.example.blackjack.model.Suit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameProgressRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "game-progress-repository-test.db"
    private lateinit var database: BlackjackDatabase
    private lateinit var repository: BlackjackRepository

    private fun open() {
        database = Room.databaseBuilder<BlackjackDatabase>(context, name)
            .setDriver(AndroidSQLiteDriver())
            .build()
        repository = BlackjackRepository(database.blackjackDao())
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
    fun noSavedProgressReturnsNullWithoutCreatingABalance() = runBlocking {
        assertNull(repository.loadGameProgress())
        assertNull(repository.restoreGameProgress())
        assertNull(database.blackjackDao().getBalance())
    }

    @Test
    fun restoredObjectsAfterReopeningContinueAndSettleTheSameGame() = runBlocking {
        val (original, remaining) = standardTable()
        original.startRound()
        val id = repository.saveGameProgress(original, 10, 4, remaining, playedAt = 100)
        database.close()
        open()
        val restored = checkNotNull(repository.restoreGameProgress())
        assertEquals(id, restored.gameId)
        assertEquals(10, restored.bet)
        assertEquals(4, restored.deckCount)
        assertEquals(100L, restored.playedAt)
        assertEquals(original.playerHands, restored.game.playerHands)
        assertEquals(remaining, restored.shoe.remainingCardList)
        assertNull(database.blackjackDao().getBalance())
        original.hit(); restored.game.hit()
        original.stand(); restored.game.stand()
        assertEquals(original.dealtCards, restored.game.dealtCards)
        assertEquals(original.result, restored.game.result)
        repository.finishGameProgress(restored.game, restored.bet, restored.deckCount,
            restored.shoe.remainingCardList, restored.gameId, 200)
        assertEquals(1010.0, repository.loadBalance(), 0.0)
        assertEquals(id, repository.observeHistory().first().single().id)
        assertNull(repository.restoreGameProgress())
    }

    @Test
    fun restorationRejectsUnknownEnumsWithoutDeletingTheSavedProgress() = runBlocking {
        val (game, remaining) = standardTable()
        game.startRound()
        repository.saveGameProgress(game, 10, 4, remaining, playedAt = 100)
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use { connection ->
            connection.execSQL("UPDATE games SET phase = 'UNKNOWN'")
        }
        val before = repository.loadGameProgress()
        try {
            repository.restoreGameProgress()
            fail("Unknown phases must not create a replacement game")
        } catch (_: IllegalArgumentException) {
            assertEquals(before, repository.loadGameProgress())
            assertNull(database.blackjackDao().getBalance())
        }
    }

    @Test
    fun aRealModelIsConvertedWithItsActualDealerCardsAndOrderedShoeAndSurvivesReopening() = runBlocking {
        val (game, remaining) = standardTable()
        game.startRound()
        val beforeCards = game.dealtCards
        val beforeShoe = remaining.toList()
        val id = repository.saveGameProgress(game, 10, 4, remaining, playedAt = 100)
        val saved = checkNotNull(repository.loadGameProgress())
        assertEquals(id, saved.game.id)
        assertEquals(100L, saved.game.playedAt)
        assertEquals("PLAYER_TURN", saved.game.phase)
        assertEquals("IN_PROGRESS", saved.game.status)
        assertEquals(0, saved.game.activeHandIndex)
        assertEquals(4, saved.game.deckCount)
        assertEquals(10, saved.game.initialBet)
        assertEquals(10, saved.game.totalBet)
        assertNull(saved.game.result)
        assertNull(saved.game.chipChange)
        assertEquals(16, saved.hands.single().total)
        assertNull(saved.hands.single().chipChange)
        assertEquals(beforeCards.map { it.card.rank.name }, saved.drawnCards.map { it.rank })
        assertEquals(beforeCards.map { it.handNumber }, saved.drawnCards.map { it.handNumber })
        assertEquals(listOf(1, 2, 3, 4), saved.drawnCards.map { it.drawOrder })
        assertEquals(game.dealerCards.map { it.rank.name }, saved.drawnCards.filter { it.handNumber == null }.map { it.rank })
        assertEquals(beforeShoe.map { it.rank.name to it.suit.name }, saved.shoeCards.map { it.rank to it.suit })
        assertEquals(listOf(0, 1, 2), saved.shoeCards.map { it.position })
        assertEquals(beforeCards, game.dealtCards)
        assertEquals(beforeShoe, remaining)
        database.close()
        open()
        assertEquals(saved, repository.loadGameProgress())
        assertTrue(repository.observeHistory().first().isEmpty())
        assertNull(database.blackjackDao().getBalance())
    }

    @Test
    fun hitUpdatesTheSameIdThenStandSettlesThatIdOnceWithTheFinalShoe() = runBlocking {
        val (game, remaining) = standardTable()
        assertEquals(1000.0, repository.loadBalance(), 0.0)
        game.startRound()
        val id = repository.saveGameProgress(game, 10, 4, remaining, playedAt = 100)
        game.hit()
        assertEquals(id, repository.saveGameProgress(game, 10, 4, remaining, id, 100))
        val afterHit = checkNotNull(repository.loadGameProgress())
        assertEquals(19, afterHit.hands.single().total)
        assertEquals(5, afterHit.drawnCards.size)
        assertEquals(2, afterHit.shoeCards.size)
        assertEquals(1000.0, repository.loadBalance(), 0.0)
        game.stand()
        val cardsBefore = game.dealtCards
        val shoeBefore = remaining.toList()
        repeat(2) { assertEquals(id, repository.finishGameProgress(game, 10, 4, remaining, id, 200)) }
        val saved = repository.observeHistory().first().single()
        assertEquals(id, saved.id)
        assertEquals(200L, saved.playedAt)
        assertEquals("FINISHED", saved.phase)
        assertEquals("WIN", saved.result)
        assertEquals(10.0, saved.chipChange!!, 0.0)
        assertEquals(1010.0, repository.loadBalance(), 0.0)
        assertEquals(6L, repository.observeStatistics().first().totalCards)
        assertNull(repository.loadGameProgress())
        assertEquals(listOf("ACE"), database.blackjackDao().getShoeCards(id).map { it.rank })
        assertEquals(cardsBefore, game.dealtCards)
        assertEquals(shoeBefore, remaining)
        database.close()
        open()
        repository.finishGameProgress(game, 10, 4, remaining, id, 200)
        assertEquals(1010.0, repository.loadBalance(), 0.0)
        assertEquals(1, repository.observeHistory().first().size)
    }

    @Test
    fun anInterruptedGameKeepsItsDistributedCardsAndEmptyShoeWithoutInventingAPayment() = runBlocking {
        val (game, remaining) = table(Rank.TEN, Rank.TEN, Rank.SIX, Rank.SIX)
        game.startRound()
        game.hit()
        assertEquals(GamePhase.INTERRUPTED, game.phase)
        val id = repository.saveGameProgress(game, 10, 1, remaining, playedAt = 100)
        val saved = checkNotNull(database.blackjackDao().getGame(id))
        assertEquals("INTERRUPTED", saved.phase)
        assertEquals("INTERRUPTED", saved.status)
        assertEquals(4, repository.getDrawnCards(id).size)
        assertTrue(database.blackjackDao().getShoeCards(id).isEmpty())
        assertNull(saved.result)
        assertNull(saved.chipChange)
        assertNull(repository.loadGameProgress())
        assertTrue(repository.observeHistory().first().isEmpty())
        assertNull(database.blackjackDao().getBalance())
    }

    @Test
    fun readyFinishedOrInvalidProgressRequestsDoNotModifyThePreviousSave() = runBlocking {
        val (game, remaining) = standardTable()
        game.startRound()
        val id = repository.saveGameProgress(game, 10, 4, remaining, playedAt = 100)
        val before = repository.loadGameProgress()
        val (ready, readyCards) = standardTable()
        expectInvalid { repository.saveGameProgress(ready, 10, 4, readyCards) }
        expectInvalid { repository.saveGameProgress(game, 0, 4, remaining, id, 100) }
        expectInvalid { repository.saveGameProgress(game, 10, 0, remaining, id, 100) }
        expectInvalid { repository.saveGameProgress(game, 10, 4, remaining, -1, 100) }
        game.stand()
        expectInvalid { repository.saveGameProgress(game, 10, 4, remaining, id, 100) }
        assertEquals(before, repository.loadGameProgress())
        assertNull(database.blackjackDao().getBalance())
    }

    @Test
    fun settlementRejectsAnActiveGameOrUnknownIdentifierWithoutPaying() = runBlocking {
        val (game, remaining) = standardTable()
        game.startRound()
        val id = repository.saveGameProgress(game, 10, 4, remaining, playedAt = 100)
        val before = repository.loadGameProgress()
        expectInvalid { repository.finishGameProgress(game, 10, 4, remaining, id, 200) }
        game.stand()
        expectInvalid { repository.finishGameProgress(game, 10, 4, remaining, 0, 200) }
        try {
            repository.finishGameProgress(game, 10, 4, remaining, 99999, 200)
            fail("The DAO error must reach the caller")
        } catch (_: IllegalStateException) { }
        assertEquals(before, repository.loadGameProgress())
        assertNull(database.blackjackDao().getBalance())
    }

    @Test
    fun splitProgressKeepsTheActiveHandAndSettlementUsesEachHandsActualResult() = runBlocking {
        val (game, remaining) = table(
            Rank.TEN, Rank.TEN, Rank.TEN, Rank.SIX,
            Rank.SIX, Rank.SIX, Rank.THREE, Rank.TWO, Rank.ACE
        )
        game.startRound()
        val id = repository.saveGameProgress(game, 10, 4, remaining, playedAt = 100)
        game.split(10, 1000.0)
        game.hit()
        game.stand()
        repository.saveGameProgress(game, 10, 4, remaining, id, 100)
        val split = checkNotNull(repository.loadGameProgress())
        assertEquals(1, split.game.activeHandIndex)
        assertEquals(20, split.game.totalBet)
        assertEquals(listOf(19, 16), split.hands.map { it.total })
        assertTrue(split.hands.all { it.chipChange == null })
        assertEquals(listOf(1, null, 2, null, 1, 2, 1), split.drawnCards.map { it.handNumber })
        val restored = checkNotNull(repository.restoreGameProgress())
        assertEquals(game.playerHands, restored.game.playerHands)
        assertEquals(1, restored.game.activeHandIndex)
        assertEquals(remaining, restored.shoe.remainingCardList)
        restored.game.stand()
        game.stand()
        assertEquals(game.dealtCards, restored.game.dealtCards)
        assertEquals(game.result, restored.game.result)
        repeat(2) { repository.finishGameProgress(game, 10, 4, remaining, id, 200) }
        val saved = repository.observeHistory().first().single()
        assertEquals("MIXED", saved.result)
        assertEquals(20, saved.totalBet)
        assertEquals(0.0, saved.chipChange!!, 0.0)
        assertEquals(listOf("WIN", "LOSS"), repository.getHands(id).map { it.result })
        assertEquals(listOf(10.0, -10.0), repository.getHands(id).map { it.chipChange })
        assertEquals(1000.0, repository.loadBalance(), 0.0)
        assertEquals(8L, repository.observeStatistics().first().totalCards)
    }

    @Test
    fun splitAcesStoreOrdinaryTwentyOnePaymentsAndNoNaturalBlackjackFlag() = runBlocking {
        val (game, remaining) = table(Rank.ACE, Rank.TEN, Rank.ACE, Rank.SEVEN, Rank.KING, Rank.QUEEN, Rank.TWO)
        game.startRound()
        val id = repository.saveGameProgress(game, 10, 1, remaining, playedAt = 100)
        game.split(10, 1000.0)
        assertEquals(GamePhase.FINISHED, game.phase)
        repository.finishGameProgress(game, 10, 1, remaining, id, 200)
        val hands = repository.getHands(id)
        assertEquals(listOf(21, 21), hands.map { it.total })
        assertEquals(listOf(false, false), hands.map { it.isNaturalBlackjack })
        assertEquals(listOf(10.0, 10.0), hands.map { it.chipChange })
        assertEquals(1020.0, repository.loadBalance(), 0.0)
        assertEquals(0L, repository.observeStatistics().first().naturalBlackjacks)
    }

    @Test
    fun anIntermediateDatabaseFailureReachesTheCallerAndKeepsThePreviousProgress() = runBlocking {
        val (game, remaining) = standardTable()
        game.startRound()
        val id = repository.saveGameProgress(game, 10, 4, remaining, playedAt = 100)
        val before = repository.loadGameProgress()
        game.hit()
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("CREATE TRIGGER reject_repository_shoe BEFORE INSERT ON shoe_cards WHEN NEW.position = 1 BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        }
        try {
            repository.saveGameProgress(game, 10, 4, remaining, id, 100)
            fail("The SQLite failure must reach the caller")
        } catch (_: SQLiteException) { }
        assertEquals(before, repository.loadGameProgress())
        assertEquals(19, game.playerValue)
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("DROP TRIGGER reject_repository_shoe")
        }
        repository.saveGameProgress(game, 10, 4, remaining, id, 100)
        assertEquals(19, repository.loadGameProgress()!!.hands.single().total)
        assertNull(database.blackjackDao().getBalance())
    }

    @Test
    fun aPaymentFailureKeepsProgressAndRetryUsesTheSameModelResultWithoutDoublePayment() = runBlocking {
        val (game, remaining) = standardTable()
        repository.loadBalance()
        game.startRound()
        val id = repository.saveGameProgress(game, 10, 4, remaining, playedAt = 100)
        val before = repository.loadGameProgress()
        game.hit()
        game.stand()
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("CREATE TRIGGER reject_repository_payment BEFORE UPDATE ON player_balance BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        }
        try {
            repository.finishGameProgress(game, 10, 4, remaining, id, 200)
            fail("The payment failure must reach the caller")
        } catch (_: SQLiteException) { }
        assertEquals(before, repository.loadGameProgress())
        assertEquals(1000.0, repository.loadBalance(), 0.0)
        assertEquals(GamePhase.FINISHED, game.phase)
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("DROP TRIGGER reject_repository_payment")
        }
        repeat(2) { repository.finishGameProgress(game, 10, 4, remaining, id, 200) }
        assertEquals(1010.0, repository.loadBalance(), 0.0)
        assertNull(repository.loadGameProgress())
        assertEquals(1, repository.observeHistory().first().size)
    }

    private fun standardTable() = table(Rank.TEN, Rank.TEN, Rank.SIX, Rank.SIX, Rank.THREE, Rank.TWO, Rank.ACE)

    private fun table(vararg ranks: Rank): Pair<BlackjackGame, MutableList<Card>> {
        // Comme Shoe, on retire la fin de la liste. Les rangs reçus indiquent l'ordre de tirage.
        val remaining = ranks.mapIndexed { index, rank -> Card(rank, Suit.entries[index % Suit.entries.size]) }
            .reversed().toMutableList()
        return BlackjackGame { if (remaining.isEmpty()) null else remaining.removeAt(remaining.lastIndex) } to remaining
    }

    private suspend fun expectInvalid(action: suspend () -> Unit) {
        try {
            action()
            fail("The invalid save must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
