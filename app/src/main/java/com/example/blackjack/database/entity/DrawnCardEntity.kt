package com.example.blackjack.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "drawn_cards",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"]
        ),
        ForeignKey(
            entity = HandEntity::class,
            parentColumns = ["gameId", "handNumber"],
            childColumns = ["gameId", "handNumber"]
        )
    ],
    indices = [
        Index(value = ["gameId", "handNumber"]),
        Index(value = ["gameId", "drawOrder"], unique = true)
    ]
)
data class DrawnCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gameId: Long,
    // null désigne le croupier; sinon, le numéro de la main du joueur.
    val handNumber: Int?,
    // Noms anglais de Rank et Suit : ACE, KING, HEARTS, CLUBS, etc.
    val rank: String,
    val suit: String,
    // Ordre de distribution dans toute la partie, en commençant à 1.
    val drawOrder: Int
)
