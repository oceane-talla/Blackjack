package com.example.blackjack.repository

import com.example.blackjack.database.dao.BlackjackDao
import com.example.blackjack.database.dao.GameProgress
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import com.example.blackjack.database.entity.ShoeCardEntity
import com.example.blackjack.model.BlackjackGame
import com.example.blackjack.model.Card
import com.example.blackjack.model.DealtCard
import com.example.blackjack.model.GameResult
import com.example.blackjack.model.Rank
import com.example.blackjack.model.Shoe
import com.example.blackjack.model.Suit
import com.example.blackjack.model.GamePhase
import com.example.blackjack.model.GameStatistics
import com.example.blackjack.model.RankStatistics
import kotlinx.coroutines.flow.Flow

// Les objets reconstruits et les informations nécessaires pour continuer la même sauvegarde.
data class RestoredGame(
    val game: BlackjackGame,
    val shoe: Shoe,
    val gameId: Long,
    val bet: Int,
    val deckCount: Int,
    val playedAt: Long
)

class BlackjackRepository(private val dao: BlackjackDao) {
    // Lecture au démarrage, sans exposer le DAO au ViewModel.
    suspend fun loadBalance(): Double = dao.getOrCreateBalance()

    // La lecture est cohérente : le DAO regroupe la partie et ses trois listes dans une transaction.
    // Lecture des données brutes; restoreGameProgress() fournit les objets reconstruits.
    suspend fun loadGameProgress(): GameProgress? = dao.loadGameProgress()

    suspend fun restoreGameProgress(): RestoredGame? {
        val saved = dao.loadGameProgress() ?: return null
        val summary = saved.game
        // Une sauvegarde incomplète est signalée, jamais remplacée par une nouvelle partie.
        val deckCount = requireNotNull(summary.deckCount)
        val activeIndex = requireNotNull(summary.activeHandIndex)
        val phase = GamePhase.valueOf(requireNotNull(summary.phase))
        require(summary.id > 0 && summary.initialBet > 0 && deckCount > 0)
        require(summary.status == "IN_PROGRESS" && summary.result == null && summary.chipChange == null)
        require(phase != GamePhase.READY && phase != GamePhase.FINISHED)
        require(saved.hands.map { it.handNumber } == (1..saved.hands.size).toList())
        require(summary.totalBet == summary.initialBet * saved.hands.size)
        require(saved.hands.all {
            it.gameId == summary.id && it.bet == summary.initialBet && it.chipChange == null
        })
        require(saved.drawnCards.all { it.gameId == summary.id })
        require(saved.shoeCards.all { it.gameId == summary.id })
        require(saved.shoeCards.map { it.position } == saved.shoeCards.indices.toList())

        val shoe = Shoe(saved.shoeCards.map { Card(Rank.valueOf(it.rank), Suit.valueOf(it.suit)) })
        val cards = saved.drawnCards.map {
            DealtCard(Card(Rank.valueOf(it.rank), Suit.valueOf(it.suit)), it.handNumber, it.drawOrder)
        }
        val game = BlackjackGame.restore(shoe, cards,
            saved.hands.map { it.result?.let(GameResult::valueOf) }, activeIndex, phase, null)
        // Les valeurs sont recalculées par Hand : elles doivent correspondre au résumé enregistré.
        require(game.dealerValue == summary.dealerTotal)
        require(game.playerHands.zip(saved.hands).all { (hand, stored) ->
            hand.total == stored.total && hand.hasBlackjack == stored.isNaturalBlackjack
        })
        return RestoredGame(game, shoe, summary.id, summary.initialBet, deckCount, summary.playedAt)
    }

    suspend fun saveGameProgress(
        game: BlackjackGame,
        bet: Int,
        deckCount: Int,
        remainingCards: List<Card>,
        gameId: Long = 0,
        playedAt: Long = System.currentTimeMillis()
    ): Long {
        require(game.phase != GamePhase.READY && game.phase != GamePhase.FINISHED) {
            "Progress must belong to a started, unfinished game."
        }
        val progress = toGameProgress(game, bet, deckCount, remainingCards, gameId, playedAt)
        return dao.saveGameProgress(progress.game, progress.hands, progress.drawnCards, progress.shoeCards)
    }

    suspend fun finishGameProgress(
        game: BlackjackGame,
        bet: Int,
        deckCount: Int,
        remainingCards: List<Card>,
        gameId: Long,
        playedAt: Long
    ): Long {
        require(game.phase == GamePhase.FINISHED && gameId > 0) {
            "Settlement requires a finished game and its saved identifier."
        }
        // La date est explicite : une nouvelle tentative doit renvoyer le même règlement.
        val progress = toGameProgress(game, bet, deckCount, remainingCards, gameId, playedAt)
        return dao.finishGameProgress(progress.game, progress.hands, progress.drawnCards, progress.shoeCards)
    }

    private fun toGameProgress(
        game: BlackjackGame,
        bet: Int,
        deckCount: Int,
        remainingCards: List<Card>,
        gameId: Long,
        playedAt: Long
    ): GameProgress {
        require(bet > 0 && deckCount > 0 && gameId >= 0)
        val finished = game.phase == GamePhase.FINISHED
        val playerHands = game.playerHands
        // Tous les objets sont copiés avant l'appel suspend du DAO : une écriture utilise un seul état.
        return GameProgress(
            game = GameEntity(
                id = gameId, playedAt = playedAt, initialBet = bet,
                totalBet = bet * playerHands.size,
                result = if (finished) checkNotNull(game.result).name else null,
                chipChange = if (finished) game.calculateChipChange(bet) else null,
                dealerTotal = game.dealerValue,
                status = when (game.phase) {
                    GamePhase.FINISHED -> "FINISHED"
                    GamePhase.INTERRUPTED -> "INTERRUPTED"
                    else -> "IN_PROGRESS"
                },
                phase = game.phase.name, activeHandIndex = game.activeHandIndex, deckCount = deckCount
            ),
            hands = playerHands.map { hand ->
                HandEntity(
                    gameId = gameId, handNumber = hand.handNumber, bet = bet,
                    total = hand.total, result = hand.result?.name,
                    isNaturalBlackjack = hand.hasBlackjack,
                    chipChange = if (finished) game.calculateHandChipChange(hand.handNumber, bet) else null
                )
            },
            drawnCards = game.dealtCards.map { dealt ->
                DrawnCardEntity(
                    gameId = gameId, handNumber = dealt.handNumber, rank = dealt.card.rank.name,
                    suit = dealt.card.suit.name, drawOrder = dealt.drawOrder
                )
            },
            // On garde l'ordre de la liste de Shoe; sa dernière carte sera tirée en premier.
            shoeCards = remainingCards.mapIndexed { index, card ->
                ShoeCardEntity(gameId, index, card.rank.name, card.suit.name)
            }
        )
    }

    suspend fun saveFinishedGame(
        game: BlackjackGame,
        bet: Int,
        playedAt: Long = System.currentTimeMillis()
    ): Long {
        require(game.phase == GamePhase.FINISHED) {
            "Only a finished game can be saved."
        }
        // Ancien chemin utilisé par le ViewModel; le Split passe par les nouvelles opérations de progression.
        require(!game.hasSplit) { "Split persistence has not been connected yet." }
        require(bet > 0) { "The bet must be positive." }
        val result = checkNotNull(game.result) { "A finished game must have a result." }
        val chipChange = game.calculateChipChange(bet)

        // La conversion actuelle accepte une seule main : sa mise est la mise totale.
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

    // Les détails des mains terminées restent observables après chaque règlement.
    fun observeFinishedHands(): Flow<List<HandEntity>> = dao.observeFinishedHands()

    // Transmet les calculs SQL au ViewModel, sans recompter l'historique en Kotlin.
    fun observeStatistics(): Flow<GameStatistics> = dao.observeStatistics()

    fun observeRankStatistics(): Flow<List<RankStatistics>> = dao.observeRankStatistics()

    suspend fun getHands(gameId: Long): List<HandEntity> = dao.getHands(gameId)

    suspend fun getDrawnCards(gameId: Long): List<DrawnCardEntity> = dao.getDrawnCards(gameId)
}
