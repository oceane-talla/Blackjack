package com.example.blackjack.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlackjackDao {
    @Insert
    suspend fun insertGame(game: GameEntity): Long

    @Insert
    suspend fun insertHands(hands: List<HandEntity>)

    @Insert
    suspend fun insertDrawnCards(cards: List<DrawnCardEntity>)

    @Transaction
    suspend fun insertGameWithDetails(
        game: GameEntity,
        hands: List<HandEntity>,
        cards: List<DrawnCardEntity>
    ): Long {
        val gameId = insertGame(game)

        // Room génère l'identifiant : les mains et cartes doivent le reprendre.
        val linkedHands = hands.map { it.copy(gameId = gameId) }
        val linkedCards = cards.map { it.copy(gameId = gameId) }

        // Les mains doivent exister avant leurs cartes pour les clés étrangères.
        insertHands(linkedHands)
        insertDrawnCards(linkedCards)
        return gameId
    }

    @Query("SELECT * FROM games ORDER BY playedAt DESC, id DESC")
    fun observeHistory(): Flow<List<GameEntity>>

    @Query("SELECT * FROM hands WHERE gameId = :gameId ORDER BY handNumber ASC")
    suspend fun getHands(gameId: Long): List<HandEntity>

    @Query("SELECT * FROM drawn_cards WHERE gameId = :gameId ORDER BY drawOrder ASC")
    suspend fun getDrawnCards(gameId: Long): List<DrawnCardEntity>
}
