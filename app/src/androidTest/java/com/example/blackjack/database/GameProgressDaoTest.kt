package com.example.blackjack.database

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
import com.example.blackjack.database.entity.ShoeCardEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameProgressDaoTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "game-progress-dao-test.db"
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
    fun emptyDatabaseReturnsNoProgressAndSavingDoesNotCreateABalance() = runBlocking {
        assertNull(dao.loadGameProgress())
        dao.saveGameProgress(game(), hands(), cards(), shoeCards())
        assertNotNull(dao.loadGameProgress())
        assertNull(dao.getBalance())
    }

    @Test
    fun completeProgressSurvivesReopeningWithAllListsOrderedAndBalanceUnchanged() = runBlocking {
        dao.insertInitialBalance(PlayerBalanceEntity(chips = 743.5))
        val id = dao.saveGameProgress(
            game().copy(totalBet = 20, activeHandIndex = 1),
            listOf(hands().single().copy(handNumber = 2), hands().single()),
            splitCards().reversed(), shoeCards().reversed()
        )
        val before = checkNotNull(dao.loadGameProgress())
        assertEquals(id, before.game.id)
        assertEquals(1, before.game.activeHandIndex)
        assertEquals(4, before.game.deckCount)
        assertEquals(listOf(1, 2), before.hands.map { it.handNumber })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), before.drawnCards.map { it.drawOrder })
        assertEquals(listOf(0, 1, 2), before.shoeCards.map { it.position })
        assertTrue(before.hands.all { it.gameId == id })
        assertTrue(before.drawnCards.all { it.gameId == id })
        assertTrue(before.shoeCards.all { it.gameId == id })
        assertEquals("ACE", before.shoeCards.last().rank)
        database.close()
        open()
        assertEquals(before, dao.loadGameProgress())
        assertTrue(dao.observeHistory().first().isEmpty())
        assertEquals(6L, dao.observeStatistics().first().totalCards)
        assertEquals(0L, dao.observeStatistics().first().totalGames)
        assertEquals(743.5, dao.getBalance()!!, 0.0)
    }

    @Test
    fun updatingKeepsTheGameIdReassignsHandsAndReplacesDetailsWithoutDoubleCounting() = runBlocking {
        val pairCards = cards().map { if (it.drawOrder == 3) it.copy(rank = "TEN") else it }
        val id = dao.saveGameProgress(
            game(), hands().map { it.copy(total = 20) }, pairCards, shoeCards()
        )
        val splitHands = listOf(hands().single(), hands().single().copy(handNumber = 2))
        // La seconde carte du joueur change de main; deux nouvelles cartes sont distribuées.
        val splitCards = splitCards()
        val updated = game().copy(id = id, totalBet = 20, activeHandIndex = 1)
        val sameId = dao.saveGameProgress(updated, splitHands, splitCards, shoeCards().take(1))
        assertEquals(id, sameId)
        val saved = checkNotNull(dao.loadGameProgress())
        assertEquals(updated, saved.game)
        assertEquals(listOf(1, 2), saved.hands.map { it.handNumber })
        assertEquals(listOf(1, null, 2, null, 1, 2), saved.drawnCards.map { it.handNumber })
        assertEquals(1, saved.shoeCards.size)
        assertEquals(6L, dao.observeStatistics().first().totalCards)
        assertTrue(dao.observeHistory().first().isEmpty())
        assertNull(dao.getBalance())
        // Réenregistrer le même état ne rajoute aucune carte.
        dao.saveGameProgress(updated, splitHands, splitCards, shoeCards().take(1))
        assertEquals(6L, dao.observeStatistics().first().totalCards)
    }

    @Test
    fun failureOnTheLastShoeCardRollsBackANewProgressSave() = runBlocking {
        dao.insertInitialBalance(PlayerBalanceEntity(chips = 743.5))
        expectConstraintFailure {
            dao.saveGameProgress(game(), hands(), cards(), shoeCards() + shoeCards().first())
        }
        assertNull(dao.loadGameProgress())
        assertNull(dao.getGame(1))
        assertTrue(dao.getHands(1).isEmpty())
        assertTrue(dao.getDrawnCards(1).isEmpty())
        assertTrue(dao.getShoeCards(1).isEmpty())
        assertEquals(0L, dao.observeStatistics().first().totalCards)
        assertEquals(743.5, dao.getBalance()!!, 0.0)
    }

    @Test
    fun failureDuringAnUpdatePreservesTheEntirePreviousSaveAndCanBeRetried() = runBlocking {
        dao.insertInitialBalance(PlayerBalanceEntity(chips = 743.5))
        val id = dao.saveGameProgress(game(), hands(), cards(), shoeCards())
        val previous = checkNotNull(dao.loadGameProgress())
        val updated = game().copy(id = id, dealerTotal = 20)
        val extraCard = DrawnCardEntity(0, 0, 1, "TWO", "CLUBS", 5)
        expectConstraintFailure {
            dao.saveGameProgress(updated, hands(), cards() + extraCard, shoeCards() + shoeCards().first())
        }
        assertEquals(previous, dao.loadGameProgress())
        assertEquals(743.5, dao.getBalance()!!, 0.0)
        dao.saveGameProgress(updated, hands(), cards() + extraCard, shoeCards().take(2))
        assertEquals(updated, dao.loadGameProgress()!!.game)
        assertEquals(5L, dao.observeStatistics().first().totalCards)
        assertEquals(743.5, dao.getBalance()!!, 0.0)
    }

    @Test
    fun progressCannotOverwriteFinishedGamesOrCreateAnUpdateForAnUnknownId() = runBlocking {
        val finished = game().copy(status = "FINISHED", phase = "FINISHED", result = "WIN", chipChange = 10.0)
        val id = dao.insertGameWithDetails(
            finished, hands().map { it.copy(result = "WIN", chipChange = 10.0) }, cards()
        )
        for (invalidId in listOf(id, 99999L)) {
            try {
                dao.saveGameProgress(game().copy(id = invalidId), hands(), cards(), shoeCards())
                fail("Only an existing unfinished game may be updated")
            } catch (_: IllegalStateException) { }
        }
        assertEquals(finished.copy(id = id), dao.getGame(id))
        assertEquals(4, dao.getDrawnCards(id).size)
        assertNull(dao.loadGameProgress())
        assertEquals(1010.0, dao.getBalance()!!, 0.0)
    }

    @Test
    fun aNewProgressSaveCannotHideAnotherUnfinishedGame() = runBlocking {
        dao.saveGameProgress(game(), hands(), cards(), shoeCards())
        val previous = dao.loadGameProgress()
        try {
            dao.saveGameProgress(game().copy(playedAt = 200), hands(), cards(), shoeCards())
            fail("The previous unfinished game must remain available")
        } catch (_: IllegalStateException) { }
        assertEquals(previous, dao.loadGameProgress())
        assertEquals(4L, dao.observeStatistics().first().totalCards)
    }

    @Test
    fun progressRejectsPaymentsOrMissingResumeMetadataBeforeChangingThePreviousSave() = runBlocking {
        val id = dao.saveGameProgress(game(), hands(), cards(), shoeCards())
        val previous = dao.loadGameProgress()
        val valid = game().copy(id = id)
        val invalidGames = listOf(
            valid.copy(status = "FINISHED"), valid.copy(result = "WIN"),
            valid.copy(chipChange = 0.0), valid.copy(phase = null),
            valid.copy(activeHandIndex = null), valid.copy(activeHandIndex = 1),
            valid.copy(deckCount = null), valid.copy(deckCount = 0)
        )
        for (invalid in invalidGames) {
            try {
                dao.saveGameProgress(invalid, hands(), cards(), shoeCards())
                fail("Progress must have resume information and no payment")
            } catch (_: IllegalArgumentException) { }
        }
        try {
            dao.saveGameProgress(valid, hands().map { it.copy(chipChange = 0.0) }, cards(), shoeCards())
            fail("A hand payment belongs to the final settlement")
        } catch (_: IllegalArgumentException) { }
        assertEquals(previous, dao.loadGameProgress())
        assertNull(dao.getBalance())
    }

    @Test
    fun anEmptyShoeAndAnAlreadyLostSplitHandRemainLoadableWithoutPaying() = runBlocking {
        val splitHands = hands().map { it.copy(result = "LOSS", total = 22) } +
            hands().single().copy(handNumber = 2)
        val id = dao.saveGameProgress(
            game().copy(totalBet = 20, activeHandIndex = 1), splitHands,
            splitCards() + DrawnCardEntity(0, 0, 1, "SIX", "CLUBS", 7), emptyList()
        )
        database.close()
        open()
        val saved = checkNotNull(dao.loadGameProgress())
        assertEquals(id, saved.game.id)
        assertEquals("LOSS", saved.hands.first().result)
        assertTrue(saved.hands.all { it.chipChange == null })
        assertTrue(saved.shoeCards.isEmpty())
        assertEquals(4, saved.game.deckCount)
        assertNull(dao.getBalance())
    }

    private fun game() = GameEntity(
        playedAt = 100, initialBet = 10, totalBet = 10, result = null, chipChange = null,
        dealerTotal = 16, status = "IN_PROGRESS", phase = "PLAYER_TURN", activeHandIndex = 0, deckCount = 4
    )

    private fun hands() = listOf(HandEntity(0, 1, 10, 16, null, false, null))

    private fun cards() = listOf(
        DrawnCardEntity(0, 0, 1, "TEN", "HEARTS", 1),
        DrawnCardEntity(0, 0, null, "TEN", "CLUBS", 2),
        DrawnCardEntity(0, 0, 1, "SIX", "SPADES", 3),
        DrawnCardEntity(0, 0, null, "SIX", "DIAMONDS", 4)
    )

    private fun shoeCards() = listOf(
        ShoeCardEntity(0, 0, "TEN", "HEARTS"),
        ShoeCardEntity(0, 1, "TEN", "HEARTS"),
        ShoeCardEntity(0, 2, "ACE", "SPADES")
    )

    private fun splitCards() = cards().map {
        if (it.drawOrder == 3) it.copy(handNumber = 2, rank = "TEN") else it
    } + listOf(
        DrawnCardEntity(0, 0, 1, "SIX", "HEARTS", 5),
        DrawnCardEntity(0, 0, 2, "SIX", "CLUBS", 6)
    )

    private suspend fun expectConstraintFailure(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected a SQLite constraint violation")
        } catch (_: SQLiteException) { }
    }
}
