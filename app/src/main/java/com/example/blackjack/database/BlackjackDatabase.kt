package com.example.blackjack.database

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.example.blackjack.database.dao.BlackjackDao
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity

@Database(
    entities = [GameEntity::class, HandEntity::class, DrawnCardEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BlackjackDatabase : RoomDatabase() {
    abstract fun blackjackDao(): BlackjackDao
}
