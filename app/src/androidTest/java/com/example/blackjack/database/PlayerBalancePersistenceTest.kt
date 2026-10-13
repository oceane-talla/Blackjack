package com.example.blackjack.database

import android.content.Context
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

// Schémas historiques figés : ne pas utiliser les Entity actuelles pour simuler le passé.
internal fun createLegacyBlackjackDatabase(context: Context, name: String, version: Int) {
    require(version in 1..3)
    context.getDatabasePath(name).parentFile!!.mkdirs()
    AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use { connection ->
        if (version == 3) {
            connection.execSQL("CREATE TABLE games (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, playedAt INTEGER NOT NULL, initialBet INTEGER NOT NULL, totalBet INTEGER NOT NULL, result TEXT, chipChange REAL, dealerTotal INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'FINISHED')")
            connection.execSQL("CREATE TABLE hands (gameId INTEGER NOT NULL, handNumber INTEGER NOT NULL, bet INTEGER NOT NULL, total INTEGER NOT NULL, result TEXT, isNaturalBlackjack INTEGER NOT NULL, chipChange REAL, PRIMARY KEY(gameId, handNumber), FOREIGN KEY(gameId) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        } else {
            connection.execSQL("CREATE TABLE games (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, playedAt INTEGER NOT NULL, initialBet INTEGER NOT NULL, totalBet INTEGER NOT NULL, result TEXT NOT NULL, chipChange REAL NOT NULL, dealerTotal INTEGER NOT NULL)")
            connection.execSQL("CREATE TABLE hands (gameId INTEGER NOT NULL, handNumber INTEGER NOT NULL, bet INTEGER NOT NULL, total INTEGER NOT NULL, result TEXT NOT NULL, isNaturalBlackjack INTEGER NOT NULL, chipChange REAL NOT NULL, PRIMARY KEY(gameId, handNumber), FOREIGN KEY(gameId) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        }
        connection.execSQL("CREATE TABLE drawn_cards (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, gameId INTEGER NOT NULL, handNumber INTEGER, rank TEXT NOT NULL, suit TEXT NOT NULL, drawOrder INTEGER NOT NULL, FOREIGN KEY(gameId) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(gameId, handNumber) REFERENCES hands(gameId, handNumber) ON UPDATE NO ACTION ON DELETE NO ACTION)")
        connection.execSQL("CREATE INDEX index_drawn_cards_gameId_handNumber ON drawn_cards(gameId, handNumber)")
        connection.execSQL("CREATE UNIQUE INDEX index_drawn_cards_gameId_drawOrder ON drawn_cards(gameId, drawOrder)")
        if (version >= 2) {
            connection.execSQL("CREATE TABLE player_balance (id INTEGER NOT NULL, chips REAL NOT NULL, PRIMARY KEY(id))")
        }
        connection.execSQL("PRAGMA user_version = $version")
    }
}

@RunWith(AndroidJUnit4::class)
class PlayerBalancePersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "balance-isolated-test.db"
    private var database: BlackjackDatabase? = null

    private fun open(): BlackjackDatabase = Room.databaseBuilder<BlackjackDatabase>(context, name)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(BlackjackDatabase.MIGRATION_1_2, BlackjackDatabase.MIGRATION_2_3,
            BlackjackDatabase.MIGRATION_3_4)
        .build().also { database = it }

    private fun game() = GameEntity(playedAt = 1, initialBet = 5, totalBet = 5,
        result = "WIN", chipChange = 7.5, dealerTotal = 18)

    @After
    fun cleanup() {
        database?.close()
        context.deleteDatabase(name)
    }

    @Test
    fun balanceSurvivesReopeningAndDoesNotDependOnHistory() = runBlocking {
        var dao = open().blackjackDao()
        assertEquals(1000.0, dao.getOrCreateBalance(), 0.0)
        dao.insertGameWithDetails(game(), emptyList(), emptyList())
        database!!.close()
        dao = open().blackjackDao()
        assertEquals(1007.5, dao.getOrCreateBalance(), 0.0)
        assertEquals(1, dao.observeHistory().first().size)
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("DELETE FROM games")
        }
        assertEquals(1007.5, dao.getOrCreateBalance(), 0.0)
    }

    @Test
    fun migrationPreservesHistoryAndStartsAtOneThousandOnlyOnce() = runBlocking {
        createLegacyBlackjackDatabase(context, name, version = 1)
        val oldId = 42L
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("INSERT INTO games VALUES (42, 1, 5, 5, 'WIN', 7.5, 18)")
            it.execSQL("INSERT INTO hands VALUES (42, 1, 5, 21, 'WIN', 1, 7.5)")
            it.execSQL("INSERT INTO drawn_cards VALUES (9, 42, 1, 'ACE', 'SPADES', 1)")
        }
        var dao = open().blackjackDao()
        assertEquals(1000.0, dao.getOrCreateBalance(), 0.0)
        assertEquals(7.5, dao.observeHistory().first().single().chipChange!!, 0.0)
        assertEquals(21, dao.getHands(oldId).single().total)
        assertEquals("ACE", dao.getDrawnCards(oldId).single().rank)
        dao.insertGameWithDetails(game(), emptyList(), emptyList())
        database!!.close()
        dao = open().blackjackDao()
        assertEquals(1007.5, dao.getOrCreateBalance(), 0.0)
        assertEquals(2, dao.observeHistory().first().size)
    }

    @Test
    fun balanceWriteFailureRollsBackHistoryAndRetryAppliesTheGainOnce() = runBlocking {
        val dao = open().blackjackDao()
        dao.getOrCreateBalance()
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("CREATE TRIGGER reject_balance BEFORE UPDATE ON player_balance BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        }
        try {
            dao.insertGameWithDetails(game(), emptyList(), emptyList())
            fail("The balance write must fail")
        } catch (_: androidx.sqlite.SQLiteException) {
            assertTrue(dao.observeHistory().first().isEmpty())
            assertEquals(1000.0, dao.getOrCreateBalance(), 0.0)
        }
        AndroidSQLiteDriver().open(context.getDatabasePath(name).path).use {
            it.execSQL("DROP TRIGGER reject_balance")
        }
        dao.insertGameWithDetails(game(), emptyList(), emptyList())
        assertEquals(1, dao.observeHistory().first().size)
        assertEquals(1007.5, dao.getOrCreateBalance(), 0.0)
    }
}
