package com.example.blackjack.database.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

// Une seule ligne, identifiée par 1 : le solde est indépendant de l'historique.
@Entity(tableName = "player_balance")
data class PlayerBalanceEntity(
    @PrimaryKey val id: Int = 1,
    // Conserve le type existant et les éventuels demi-jetons des anciennes parties.
    val chips: Double = 1_000.0
)
