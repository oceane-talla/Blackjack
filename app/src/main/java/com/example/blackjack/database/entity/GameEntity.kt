package com.example.blackjack.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Date en millisecondes depuis le 1er janvier 1970.
    val playedAt: Long,
    val initialBet: Int,
    val totalBet: Int,
    val result: String,
    // Double permet de conserver une variation avec une moitié de jeton.
    val chipChange: Double,
    val dealerTotal: Int
)
