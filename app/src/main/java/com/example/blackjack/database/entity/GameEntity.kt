package com.example.blackjack.database.entity

import androidx.room3.Entity
import androidx.room3.ColumnInfo
import androidx.room3.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    // Date en millisecondes depuis le 1er janvier 1970.
    val playedAt: Long,
    val initialBet: Int,
    val totalBet: Int,
    // null pendant la partie : aucun résultat n'est encore déterminé.
    val result: String?,
    // Double permet de conserver une variation avec une moitié de jeton.
    val chipChange: Double?,
    val dealerTotal: Int,
    // Les anciennes parties et la sauvegarde finale restent FINISHED par défaut.
    @ColumnInfo(defaultValue = "'FINISHED'")
    val status: String = "FINISHED",
    // Noms de GamePhase : PLAYER_TURN, FINISHED, INTERRUPTED, etc.
    // null pour les anciennes sauvegardes qui ne contenaient pas ces informations.
    val phase: String? = null,
    // Index Kotlin de la main à jouer : 0 pour la première, 1 après un Split.
    val activeHandIndex: Int? = null,
    // Nombre de paquets ayant servi à créer le sabot, pas le nombre de cartes restantes.
    val deckCount: Int? = null
)
