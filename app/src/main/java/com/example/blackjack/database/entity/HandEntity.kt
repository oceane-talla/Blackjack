package com.example.blackjack.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey

@Entity(
    tableName = "hands",
    primaryKeys = ["gameId", "handNumber"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"]
        )
    ]
)
data class HandEntity(
    val gameId: Long,
    // 1 pour la première main du joueur, 2 pour la seconde après un Split.
    val handNumber: Int,
    val bet: Int,
    val total: Int,
    // WIN, LOSS ou DRAW; null tant que la main n'est pas réglée.
    val result: String?,
    val isNaturalBlackjack: Boolean,
    // null avant le règlement, distinct de 0.0 (égalité réglée).
    val chipChange: Double?
)
