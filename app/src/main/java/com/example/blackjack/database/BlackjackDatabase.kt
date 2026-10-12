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

@Database(
    entities = [GameEntity::class, HandEntity::class, DrawnCardEntity::class, PlayerBalanceEntity::class],
    version = 2,
    exportSchema = false
)
abstract class BlackjackDatabase : RoomDatabase() {
    abstract fun blackjackDao(): BlackjackDao

    companion object {
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
