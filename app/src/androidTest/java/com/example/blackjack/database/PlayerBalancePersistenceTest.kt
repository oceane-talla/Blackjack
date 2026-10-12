package com.example.blackjack.database

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Insert
import androidx.room3.Room
import androidx.room3.RoomDatabase
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

// Reproduit le schéma livré avant l'ajout du solde, sans toucher à la vraie base.
@Database(entities = [GameEntity::class, HandEntity::class, DrawnCardEntity::class], version = 1, exportSchema = false)
abstract class LegacyBalanceTestDatabase : RoomDatabase() {
    abstract fun dao(): LegacyBalanceTestDao
}

@Dao
interface LegacyBalanceTestDao {
    @Insert
    suspend fun insertGame(game: GameEntity): Long
    @Insert
    suspend fun insertHand(hand: HandEntity)
    @Insert
    suspend fun insertCard(card: DrawnCardEntity)
}

@RunWith(AndroidJUnit4::class)
class PlayerBalancePersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "balance-isolated-test.db"
    private var database: BlackjackDatabase? = null

    private fun open(): BlackjackDatabase = Room.databaseBuilder<BlackjackDatabase>(context, name)
        .setDriver(AndroidSQLiteDriver())
        .addMigrations(BlackjackDatabase.MIGRATION_1_2)
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
        val legacy = Room.databaseBuilder<LegacyBalanceTestDatabase>(context, name)
            .setDriver(AndroidSQLiteDriver()).build()
        val oldId = legacy.dao().insertGame(game())
        legacy.dao().insertHand(HandEntity(oldId, 1, 5, 21, "WIN", true, 7.5))
        legacy.dao().insertCard(DrawnCardEntity(gameId = oldId, handNumber = 1,
            rank = "ACE", suit = "SPADES", drawOrder = 1))
        legacy.close()
        var dao = open().blackjackDao()
        assertEquals(1000.0, dao.getOrCreateBalance(), 0.0)
        assertEquals(7.5, dao.observeHistory().first().single().chipChange, 0.0)
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
