package com.example.blackjack

import android.app.Application
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.example.blackjack.database.BlackjackDatabase
import com.example.blackjack.repository.BlackjackRepository

// Android crée cette Application une fois par processus, grâce à sa déclaration dans le manifeste.
// Elle partage les services de données ; BlackjackApp reste le composable de l'interface.
class BlackjackApplication : Application() {
    // by lazy construit la base au premier accès, puis retourne toujours cette même instance.
    // applicationContext évite de conserver une Activity qui pourrait être recréée à la rotation.
    val database: BlackjackDatabase by lazy {
        Room.databaseBuilder<BlackjackDatabase>(applicationContext, "blackjack.db")
            .setDriver(AndroidSQLiteDriver())
            .addMigrations(BlackjackDatabase.MIGRATION_1_2)
            .build()
    }

    // Un seul repository, utilisant le DAO de la base partagée.
    val repository: BlackjackRepository by lazy {
        BlackjackRepository(database.blackjackDao())
    }
}
