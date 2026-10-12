package com.example.blackjack.repository

import com.example.blackjack.database.dao.BlackjackDao
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import com.example.blackjack.model.BlackjackGame
import com.example.blackjack.model.GamePhase
import com.example.blackjack.model.GameStatistics
import kotlinx.coroutines.flow.Flow

class BlackjackRepository(private val dao: BlackjackDao) {
    // Lecture au démarrage, sans exposer le DAO au ViewModel.
    suspend fun loadBalance(): Double = dao.getOrCreateBalance()

    suspend fun saveFinishedGame(
        game: BlackjackGame,
        bet: Int,
        playedAt: Long = System.currentTimeMillis()
    ): Long {
        require(game.phase == GamePhase.FINISHED) {
            "Only a finished game can be saved."
        }
        require(bet > 0) { "The bet must be positive." }
        val result = checkNotNull(game.result) { "A finished game must have a result." }
        val chipChange = game.calculateChipChange(bet)

        // Le modèle actuel possède une seule main : sa mise est la mise totale.
        val gameEntity = GameEntity(
            playedAt = playedAt,
            initialBet = bet,
            totalBet = bet,
            result = result.name,
            chipChange = chipChange,
            dealerTotal = game.dealerValue
        )
        val handEntity = HandEntity(
            gameId = 0,
            handNumber = 1,
            bet = bet,
            total = game.playerValue,
            result = result.name,
            isNaturalBlackjack = game.playerHasBlackjack,
            chipChange = chipChange
        )
        val cards = game.dealtCards.map { dealtCard ->
            DrawnCardEntity(
                gameId = 0,
                handNumber = dealtCard.handNumber,
                rank = dealtCard.card.rank.name,
                suit = dealtCard.card.suit.name,
                drawOrder = dealtCard.drawOrder
            )
        }

        // Le DAO remplace les gameId provisoires par l'identifiant généré.
        return dao.insertGameWithDetails(gameEntity, listOf(handEntity), cards)
    }

    fun observeHistory(): Flow<List<GameEntity>> = dao.observeHistory()

    // Transmet les calculs SQL au ViewModel, sans recompter l'historique en Kotlin.
    fun observeStatistics(): Flow<GameStatistics> = dao.observeStatistics()

    suspend fun getHands(gameId: Long): List<HandEntity> = dao.getHands(gameId)

    suspend fun getDrawnCards(gameId: Long): List<DrawnCardEntity> = dao.getDrawnCards(gameId)
}
