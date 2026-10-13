package com.example.blackjack.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.example.blackjack.database.entity.PlayerBalanceEntity
import com.example.blackjack.database.dao.BlackjackDao
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import com.example.blackjack.database.entity.ShoeCardEntity

@Database(
    entities = [GameEntity::class, HandEntity::class, DrawnCardEntity::class,
        PlayerBalanceEntity::class, ShoeCardEntity::class],
    version = 4,
    exportSchema = false
)
abstract class BlackjackDatabase : RoomDatabase() {
    abstract fun blackjackDao(): BlackjackDao

    companion object {
        // Les données de reprise étaient absentes : ne pas inventer un sabot pour l'historique.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE games ADD COLUMN phase TEXT")
                connection.execSQL("ALTER TABLE games ADD COLUMN activeHandIndex INTEGER")
                connection.execSQL("ALTER TABLE games ADD COLUMN deckCount INTEGER")
                connection.execSQL("""
                    CREATE TABLE shoe_cards (
                        gameId INTEGER NOT NULL, position INTEGER NOT NULL,
                        rank TEXT NOT NULL, suit TEXT NOT NULL,
                        PRIMARY KEY(gameId, position),
                        FOREIGN KEY(gameId) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                """.trimIndent())
            }
        }

        // SQLite ne permet pas de retirer NOT NULL directement : on recopie les tables.
        // Les cartes sont recopiées aussi pour conserver leurs deux clés étrangères.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE games_backup AS SELECT * FROM games")
                connection.execSQL("CREATE TABLE hands_backup AS SELECT * FROM hands")
                connection.execSQL("CREATE TABLE drawn_cards_backup AS SELECT * FROM drawn_cards")
                // Copies sans relations : les tables sont recréées sous leur nom final.
                connection.execSQL("DROP TABLE drawn_cards")
                connection.execSQL("DROP TABLE hands")
                connection.execSQL("DROP TABLE games")
                connection.execSQL("""
                    CREATE TABLE games (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        playedAt INTEGER NOT NULL, initialBet INTEGER NOT NULL,
                        totalBet INTEGER NOT NULL, result TEXT, chipChange REAL,
                        dealerTotal INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'FINISHED'
                    )
                """.trimIndent())
                connection.execSQL("""
                    CREATE TABLE hands (
                        gameId INTEGER NOT NULL, handNumber INTEGER NOT NULL,
                        bet INTEGER NOT NULL, total INTEGER NOT NULL, result TEXT,
                        isNaturalBlackjack INTEGER NOT NULL, chipChange REAL,
                        PRIMARY KEY(gameId, handNumber),
                        FOREIGN KEY(gameId) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                """.trimIndent())
                connection.execSQL("""
                    CREATE TABLE drawn_cards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gameId INTEGER NOT NULL, handNumber INTEGER,
                        rank TEXT NOT NULL, suit TEXT NOT NULL, drawOrder INTEGER NOT NULL,
                        FOREIGN KEY(gameId) REFERENCES games(id) ON UPDATE NO ACTION ON DELETE NO ACTION,
                        FOREIGN KEY(gameId, handNumber) REFERENCES hands(gameId, handNumber) ON UPDATE NO ACTION ON DELETE NO ACTION
                    )
                """.trimIndent())
                connection.execSQL("INSERT INTO games (id, playedAt, initialBet, totalBet, result, chipChange, dealerTotal) SELECT id, playedAt, initialBet, totalBet, result, chipChange, dealerTotal FROM games_backup")
                connection.execSQL("INSERT INTO hands SELECT gameId, handNumber, bet, total, result, isNaturalBlackjack, chipChange FROM hands_backup")
                connection.execSQL("INSERT INTO drawn_cards SELECT id, gameId, handNumber, rank, suit, drawOrder FROM drawn_cards_backup")
                connection.execSQL("DROP TABLE drawn_cards_backup")
                connection.execSQL("DROP TABLE hands_backup")
                connection.execSQL("DROP TABLE games_backup")
                connection.execSQL("CREATE INDEX index_drawn_cards_gameId_handNumber ON drawn_cards(gameId, handNumber)")
                connection.execSQL("CREATE UNIQUE INDEX index_drawn_cards_gameId_drawOrder ON drawn_cards(gameId, drawOrder)")
            }
        }

        // Ajoute le solde sans supprimer les trois tables de l'historique.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS player_balance (id INTEGER NOT NULL, chips REAL NOT NULL, PRIMARY KEY(id))")
                // Choix approuvé : départ à 1000, sans recalculer les anciennes parties.
                connection.execSQL("INSERT INTO player_balance (id, chips) VALUES (1, 1000.0)")
            }
        }
    }
}
