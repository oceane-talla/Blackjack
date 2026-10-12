package com.example.blackjack.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.OnConflictStrategy
import com.example.blackjack.database.entity.PlayerBalanceEntity
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import com.example.blackjack.model.GameStatistics
import kotlinx.coroutines.flow.Flow

@Dao
interface BlackjackDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitialBalance(balance: PlayerBalanceEntity)

    @Query("SELECT chips FROM player_balance WHERE id = 1")
    suspend fun getBalance(): Double?

    @Query("UPDATE player_balance SET chips = chips + :chipChange WHERE id = 1")
    suspend fun applyChipChange(chipChange: Double): Int

    @Transaction
    suspend fun getOrCreateBalance(): Double {
        // IGNORE préserve le solde existant lors des prochains démarrages.
        insertInitialBalance(PlayerBalanceEntity())
        return checkNotNull(getBalance())
    }

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
        getOrCreateBalance()
        val gameId = insertGame(game)

        // Room génère l'identifiant : les mains et cartes doivent le reprendre.
        val linkedHands = hands.map { it.copy(gameId = gameId) }
        val linkedCards = cards.map { it.copy(gameId = gameId) }

        // Les mains doivent exister avant leurs cartes pour les clés étrangères.
        insertHands(linkedHands)
        insertDrawnCards(linkedCards)
        // Dans la même transaction : une erreur annule la partie ET le paiement.
        check(applyChipChange(game.chipChange) == 1)
        return gameId
    }

    @Query("SELECT * FROM games ORDER BY playedAt DESC, id DESC")
    fun observeHistory(): Flow<List<GameEntity>>

    // Chaque sous-requête produit un seul résumé : CROSS JOIN assemble ces trois résumés.
    // Joindre directement les parties, mains et cartes multiplierait les mises et les gains.
    // COUNT compte, SUM additionne, AVG calcule la moyenne et MAX cherche la plus grande valeur.
    // CASE WHEN choisit la valeur à additionner selon une condition (exemple : une main gagnée).
    // COALESCE remplace une somme vide par 0 ; NULLIF évite une division par zéro.
    @Query("""
        SELECT
            gameStats.totalGames,
            handStats.totalHands,
            cardStats.totalCards,
            handStats.winningHands,
            handStats.losingHands,
            handStats.drawnHands,
            handStats.naturalBlackjacks,
            handStats.totalGains,
            handStats.totalLosses,
            gameStats.averageBet,
            gameStats.highestBet,
            cardStats.redCards,
            cardStats.blackCards,
            100.0 * cardStats.redCards / NULLIF(cardStats.totalCards, 0) AS redPercentage,
            100.0 * cardStats.blackCards / NULLIF(cardStats.totalCards, 0) AS blackPercentage,
            cardStats.heartsCards,
            cardStats.diamondsCards,
            cardStats.clubsCards,
            cardStats.spadesCards,
            100.0 * cardStats.heartsCards / NULLIF(cardStats.totalCards, 0) AS heartsPercentage,
            100.0 * cardStats.diamondsCards / NULLIF(cardStats.totalCards, 0) AS diamondsPercentage,
            100.0 * cardStats.clubsCards / NULLIF(cardStats.totalCards, 0) AS clubsPercentage,
            100.0 * cardStats.spadesCards / NULLIF(cardStats.totalCards, 0) AS spadesPercentage
        FROM (
            SELECT COUNT(*) AS totalGames, AVG(totalBet) AS averageBet, MAX(totalBet) AS highestBet
            FROM games
        ) AS gameStats
        CROSS JOIN (
            SELECT
                COUNT(*) AS totalHands,
                COALESCE(SUM(CASE WHEN result = 'WIN' THEN 1 ELSE 0 END), 0) AS winningHands,
                COALESCE(SUM(CASE WHEN result = 'LOSS' THEN 1 ELSE 0 END), 0) AS losingHands,
                COALESCE(SUM(CASE WHEN result = 'DRAW' THEN 1 ELSE 0 END), 0) AS drawnHands,
                COALESCE(SUM(CASE WHEN isNaturalBlackjack = 1 THEN 1 ELSE 0 END), 0) AS naturalBlackjacks,
                COALESCE(SUM(CASE WHEN chipChange > 0 THEN chipChange ELSE 0.0 END), 0.0) AS totalGains,
                COALESCE(SUM(CASE WHEN chipChange < 0 THEN -chipChange ELSE 0.0 END), 0.0) AS totalLosses
            FROM hands
        ) AS handStats
        CROSS JOIN (
            SELECT
                COUNT(*) AS totalCards,
                COALESCE(SUM(CASE WHEN suit IN ('HEARTS', 'DIAMONDS') THEN 1 ELSE 0 END), 0) AS redCards,
                COALESCE(SUM(CASE WHEN suit IN ('CLUBS', 'SPADES') THEN 1 ELSE 0 END), 0) AS blackCards,
                COALESCE(SUM(CASE WHEN suit = 'HEARTS' THEN 1 ELSE 0 END), 0) AS heartsCards,
                COALESCE(SUM(CASE WHEN suit = 'DIAMONDS' THEN 1 ELSE 0 END), 0) AS diamondsCards,
                COALESCE(SUM(CASE WHEN suit = 'CLUBS' THEN 1 ELSE 0 END), 0) AS clubsCards,
                COALESCE(SUM(CASE WHEN suit = 'SPADES' THEN 1 ELSE 0 END), 0) AS spadesCards
            FROM drawn_cards
        ) AS cardStats
    """)
    fun observeStatistics(): Flow<GameStatistics>

    @Query("SELECT * FROM hands WHERE gameId = :gameId ORDER BY handNumber ASC")
    suspend fun getHands(gameId: Long): List<HandEntity>

    @Query("SELECT * FROM drawn_cards WHERE gameId = :gameId ORDER BY drawOrder ASC")
    suspend fun getDrawnCards(gameId: Long): List<DrawnCardEntity>
}
