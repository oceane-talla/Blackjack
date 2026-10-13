package com.example.blackjack.database

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameProgressSchemaTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "game-progress-schema-test.db"
    private var database: BlackjackDatabase? = null

    private fun open(): BlackjackDatabase = Room.databaseBuilder<BlackjackDatabase>(context, name)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(BlackjackDatabase.MIGRATION_1_2, BlackjackDatabase.MIGRATION_2_3,
            BlackjackDatabase.MIGRATION_3_4)
        .build().also { database = it }

    @After
    fun cleanup() {
        database?.close()
        context.deleteDatabase(name)
    }

    @Test
    fun migrationFromVersionThreePreservesDataWithoutInventingResumeInformation() = runBlocking {
        createLegacyBlackjackDatabase(context, name, version = 3)
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("INSERT INTO player_balance VALUES (1, 743.5)")
            it.execSQL("INSERT INTO games VALUES (42, 100, 10, 10, 'WIN', 10.0, 18, 'FINISHED')")
            it.execSQL("INSERT INTO games VALUES (43, 200, 25, 25, NULL, NULL, 12, 'IN_PROGRESS')")
            it.execSQL("INSERT INTO hands VALUES (42, 1, 10, 20, 'WIN', 0, 10.0)")
            it.execSQL("INSERT INTO hands VALUES (43, 1, 25, 10, NULL, 0, NULL)")
            it.execSQL("INSERT INTO drawn_cards VALUES (8, 42, 1, 'TEN', 'HEARTS', 1)")
            it.execSQL("INSERT INTO drawn_cards VALUES (9, 43, 1, 'KING', 'SPADES', 1)")
        }
        var dao = open().blackjackDao()
        assertEquals(GameEntity(42, 100, 10, 10, "WIN", 10.0, 18), dao.observeHistory().first().single())
        assertEquals(743.5, dao.getOrCreateBalance(), 0.0)
        assertNull(dao.getHands(43).single().result)
        assertNull(dao.getHands(43).single().chipChange)
        assertEquals(9L, dao.getDrawnCards(43).single().id)
        database!!.close()
        dao = open().blackjackDao()
        assertEquals(743.5, dao.getOrCreateBalance(), 0.0)
        assertEquals(2L, dao.observeStatistics().first().totalCards)
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use { connection ->
            connection.prepare("SELECT status, phase, activeHandIndex, deckCount FROM games WHERE id = 43").use {
                assertTrue(it.step())
                assertEquals("IN_PROGRESS", it.getText(0))
                assertTrue(it.isNull(1))
                assertTrue(it.isNull(2))
                assertTrue(it.isNull(3))
            }
            connection.prepare("SELECT COUNT(*) FROM shoe_cards").use {
                assertTrue(it.step())
                assertEquals(0L, it.getLong(0))
            }
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }

    @Test
    fun resumeColumnsAndOrderedShoeSurviveReopeningWithoutAffectingStatisticsOrBalance() = runBlocking {
        var dao = open().blackjackDao()
        val id = dao.insertGame(finishedGame().copy(
            result = null, chipChange = null, status = "IN_PROGRESS",
            phase = "PLAYER_TURN", activeHandIndex = 1, deckCount = 4
        ))
        dao.insertHands(listOf(
            hand(id).copy(result = null, chipChange = null),
            hand(id).copy(handNumber = 2, result = null, chipChange = null)
        ))
        dao.insertDrawnCards(listOf(card(id).copy(rank = "KING")))
        assertEquals(1000.0, dao.getOrCreateBalance(), 0.0)
        // Insertion volontairement désordonnée, avec deux exemplaires identiques.
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("INSERT INTO shoe_cards VALUES ($id, 2, 'ACE', 'SPADES')")
            it.execSQL("INSERT INTO shoe_cards VALUES ($id, 0, 'TEN', 'HEARTS')")
            it.execSQL("INSERT INTO shoe_cards VALUES ($id, 1, 'TEN', 'HEARTS')")
        }
        database!!.close()
        dao = open().blackjackDao()
        assertEquals(1000.0, dao.getOrCreateBalance(), 0.0)
        assertTrue(dao.observeHistory().first().isEmpty())
        assertEquals(0L, dao.observeStatistics().first().totalGames)
        assertEquals(1L, dao.observeStatistics().first().totalCards)
        assertEquals(100.0, dao.observeRankStatistics().first().single { it.rank == "KING" }.percentage!!, 0.0)
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use { connection ->
            connection.prepare("SELECT phase, activeHandIndex, deckCount FROM games WHERE id = $id").use {
                assertTrue(it.step())
                assertEquals("PLAYER_TURN", it.getText(0))
                assertEquals(1L, it.getLong(1))
                assertEquals(4L, it.getLong(2))
            }
            val cards = mutableListOf<String>()
            connection.prepare("SELECT rank, suit FROM shoe_cards WHERE gameId = $id ORDER BY position").use {
                while (it.step()) cards.add("${it.getText(0)}:${it.getText(1)}")
            }
            assertEquals(listOf("TEN:HEARTS", "TEN:HEARTS", "ACE:SPADES"), cards)
            // Shoe retire la fin de la liste : la prochaine carte est donc toujours cet As.
            assertEquals("ACE:SPADES", cards.last())
        }
    }

    @Test
    fun shoeCardsRequireTheirGameAndAUniquePositionWithinThatGame() = runBlocking {
        val dao = open().blackjackDao()
        val firstId = dao.insertGame(finishedGame())
        val secondId = dao.insertGame(finishedGame())
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use { connection ->
            // Ce test SQL ouvre sa propre connexion : les contraintes doivent y être activées.
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL("INSERT INTO shoe_cards VALUES ($firstId, 0, 'TEN', 'HEARTS')")
            connection.execSQL("INSERT INTO shoe_cards VALUES ($secondId, 0, 'TEN', 'HEARTS')")
            try {
                connection.execSQL("INSERT INTO shoe_cards VALUES ($firstId, 0, 'ACE', 'SPADES')")
                fail("One game cannot have two cards at the same shoe position")
            } catch (_: androidx.sqlite.SQLiteException) { }
            try {
                connection.execSQL("INSERT INTO shoe_cards VALUES (99999, 0, 'ACE', 'SPADES')")
                fail("A saved shoe must belong to an existing game")
            } catch (_: androidx.sqlite.SQLiteException) { }
            connection.prepare("SELECT COUNT(*) FROM shoe_cards").use {
                assertTrue(it.step())
                assertEquals(2L, it.getLong(0))
            }
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }

    @Test
    fun migrationFromVersionTwoPreservesIdsRelationsBalanceAndNextIds() = runBlocking {
        createLegacyBlackjackDatabase(context, name, version = 2)
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("INSERT INTO player_balance VALUES (1, 743.5)")
            it.execSQL("INSERT INTO games VALUES (42, 100, 10, 20, 'MIXED', 0.0, 18)")
            it.execSQL("INSERT INTO hands VALUES (42, 1, 10, 20, 'WIN', 0, 10.0)")
            it.execSQL("INSERT INTO hands VALUES (42, 2, 10, 17, 'LOSS', 0, -10.0)")
            it.execSQL("INSERT INTO drawn_cards VALUES (8, 42, 1, 'TEN', 'HEARTS', 1)")
            it.execSQL("INSERT INTO drawn_cards VALUES (9, 42, 2, 'SEVEN', 'SPADES', 2)")
            it.execSQL("INSERT INTO drawn_cards VALUES (10, 42, NULL, 'KING', 'CLUBS', 3)")
        }
        var dao = open().blackjackDao()
        val migrated = dao.observeHistory().first().single()
        assertEquals(GameEntity(42, 100, 10, 20, "MIXED", 0.0, 18), migrated)
        assertEquals(42L, migrated.id)
        assertEquals("FINISHED", migrated.status)
        assertEquals("MIXED", migrated.result)
        assertEquals(0.0, migrated.chipChange!!, 0.0)
        assertEquals(listOf(1, 2), dao.getHands(42).map { it.handNumber })
        assertEquals(listOf("WIN", "LOSS"), dao.getHands(42).map { it.result })
        assertEquals(listOf(8L, 9L, 10L), dao.getDrawnCards(42).map { it.id })
        assertEquals(listOf(1, 2, null), dao.getDrawnCards(42).map { it.handNumber })
        assertEquals(743.5, dao.getOrCreateBalance(), 0.0)
        try {
            dao.insertDrawnCards(listOf(card(42).copy(handNumber = 3, drawOrder = 4)))
            fail("Migration must preserve the hand foreign key")
        } catch (_: androidx.sqlite.SQLiteException) { }
        try {
            dao.insertDrawnCards(listOf(card(42)))
            fail("Migration must preserve unique draw order")
        } catch (_: androidx.sqlite.SQLiteException) { }
        val newId = dao.insertGameWithDetails(finishedGame(), listOf(hand(0)), listOf(card(0)))
        assertTrue(newId > 42)
        assertTrue(dao.getDrawnCards(newId).single().id > 10)
        assertEquals(753.5, dao.getOrCreateBalance(), 0.0)
        database!!.close()
        dao = open().blackjackDao()
        assertEquals(2, dao.observeHistory().first().size)
        assertEquals(753.5, dao.getOrCreateBalance(), 0.0)
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use { connection ->
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }

    @Test
    fun unfinishedCardsPersistButDoNotInventAResultOrACompletedGame() = runBlocking {
        var dao = open().blackjackDao()
        dao.insertGameWithDetails(finishedGame(), listOf(hand(0)), listOf(card(0)))
        val unfinished = finishedGame().copy(
            playedAt = 200, initialBet = 100, totalBet = 100,
            result = null, chipChange = null, status = "IN_PROGRESS"
        )
        val id = dao.insertGame(unfinished)
        dao.insertHands(listOf(hand(id).copy(bet = 100, total = 10, result = null, chipChange = null)))
        dao.insertDrawnCards(listOf(card(id).copy(rank = "KING")))
        database!!.close()
        dao = open().blackjackDao()
        assertNull(dao.getHands(id).single().result)
        assertNull(dao.getHands(id).single().chipChange)
        assertEquals("KING", dao.getDrawnCards(id).single().rank)
        assertEquals(1, dao.observeHistory().first().size)
        val stats = dao.observeStatistics().first()
        assertEquals(1L, stats.totalGames)
        assertEquals(1L, stats.totalHands)
        assertEquals(1L, stats.winningHands)
        assertEquals(10.0, stats.totalGains, 0.0)
        assertEquals(10.0, stats.averageBet!!, 0.0)
        assertEquals(10, stats.highestBet)
        assertEquals(2L, stats.totalCards)
        assertEquals(50.0, dao.observeRankStatistics().first().single { it.rank == "KING" }.percentage!!, 0.0)
        assertEquals(1010.0, dao.getOrCreateBalance(), 0.0)
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use { connection ->
            connection.prepare("SELECT result, chipChange, status FROM games WHERE id = $id").use {
                assertTrue(it.step())
                assertTrue(it.isNull(0))
                assertTrue(it.isNull(1))
                assertEquals("IN_PROGRESS", it.getText(2))
            }
        }
    }

    @Test
    fun finalPaymentTransactionRejectsUnfinishedOrUnsettledData() = runBlocking {
        val dao = open().blackjackDao()
        dao.getOrCreateBalance()
        val invalidGames = listOf(
            finishedGame().copy(status = "IN_PROGRESS"),
            finishedGame().copy(result = null),
            finishedGame().copy(chipChange = null)
        )
        for (game in invalidGames) {
            try {
                dao.insertGameWithDetails(game, listOf(hand(0)), listOf(card(0)))
                fail("An unsettled game must not be paid")
            } catch (_: IllegalArgumentException) { }
        }
        try {
            dao.insertGameWithDetails(finishedGame(), listOf(hand(0).copy(result = null)), emptyList())
            fail("An unsettled hand must not be paid")
        } catch (_: IllegalArgumentException) { }
        assertTrue(dao.observeHistory().first().isEmpty())
        assertEquals(1000.0, dao.getOrCreateBalance(), 0.0)
    }

    @Test
    fun foreignKeysStillRejectCardsWithoutTheirPlayerHand() = runBlocking {
        val dao = open().blackjackDao()
        val id = dao.insertGame(finishedGame().copy(result = null, chipChange = null, status = "IN_PROGRESS"))
        try {
            dao.insertDrawnCards(listOf(card(id)))
            fail("A player's card requires its hand")
        } catch (_: androidx.sqlite.SQLiteException) { }
        assertTrue(dao.getDrawnCards(id).isEmpty())
    }

    private fun finishedGame() = GameEntity(
        playedAt = 100, initialBet = 10, totalBet = 10,
        result = "WIN", chipChange = 10.0, dealerTotal = 18
    )
    private fun hand(gameId: Long) = HandEntity(gameId, 1, 10, 20, "WIN", false, 10.0)
    private fun card(gameId: Long) = DrawnCardEntity(
        gameId = gameId, handNumber = 1, rank = "TEN", suit = "HEARTS", drawOrder = 1
    )
}
