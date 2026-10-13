package com.example.blackjack.database.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey

// État du sabot associé à la sauvegarde d'une partie, sans compter ces cartes comme distribuées.
@Entity(
    tableName = "shoe_cards",
    primaryKeys = ["gameId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["gameId"]
        )
    ]
)
data class ShoeCardEntity(
    val gameId: Long,
    // Index dans la liste de Shoe, à partir de 0. drawCard() retire la dernière position.
    // La position distingue aussi les exemplaires identiques venant de plusieurs paquets.
    val position: Int,
    val rank: String,
    val suit: String
)
