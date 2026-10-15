package com.example.blackjack.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.OnConflictStrategy
import androidx.room3.Update
import com.example.blackjack.database.entity.PlayerBalanceEntity
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import com.example.blackjack.database.entity.ShoeCardEntity
import com.example.blackjack.model.GameStatistics
import com.example.blackjack.model.RankStatistics
import kotlinx.coroutines.flow.Flow

// Simple regroupement de données lues ensemble, pas une nouvelle table Room.
data class GameProgress(
    val game: GameEntity,
    val hands: List<HandEntity>,
    val drawnCards: List<DrawnCardEntity>,
    val shoeCards: List<ShoeCardEntity>
)

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

    @Insert
    suspend fun insertShoeCards(cards: List<ShoeCardEntity>)

    @Update
    suspend fun updateGame(game: GameEntity): Int

    @Query("SELECT * FROM games WHERE id = :gameId")
    suspend fun getGame(gameId: Long): GameEntity?

    @Query("SELECT * FROM games WHERE status = 'IN_PROGRESS' ORDER BY id DESC LIMIT 1")
    suspend fun getInProgressGame(): GameEntity?

    @Query("SELECT * FROM shoe_cards WHERE gameId = :gameId ORDER BY position ASC")
    suspend fun getShoeCards(gameId: Long): List<ShoeCardEntity>

    @Query("DELETE FROM drawn_cards WHERE gameId = :gameId")
    suspend fun deleteDrawnCards(gameId: Long)

    @Query("DELETE FROM shoe_cards WHERE gameId = :gameId")
    suspend fun deleteShoeCards(gameId: Long)

    @Query("DELETE FROM hands WHERE gameId = :gameId")
    suspend fun deleteHands(gameId: Long)

    @Transaction
    suspend fun saveGameProgress(
        game: GameEntity,
        hands: List<HandEntity>,
        drawnCards: List<DrawnCardEntity>,
        shoeCards: List<ShoeCardEntity>
    ): Long {
        // Une sauvegarde intermédiaire n'est pas un règlement : aucun paiement n'est enregistré.
        require(game.id >= 0 && game.status in listOf("IN_PROGRESS", "INTERRUPTED"))
        require(game.status != "INTERRUPTED" || game.phase == "INTERRUPTED")
        require(game.result == null && game.chipChange == null)
        requireNotNull(game.phase)
        require(requireNotNull(game.activeHandIndex) in hands.indices)
        require(requireNotNull(game.deckCount) > 0)
        require(hands.all { it.chipChange == null })

        val gameId = if (game.id == 0L) {
            // Une autre partie en attente ne doit pas être oubliée au démarrage d'une nouvelle.
            check(getInProgressGame() == null) { "An unfinished game already exists." }
            insertGame(game)
        } else {
            val previous = getGame(game.id)
            check(previous?.status == "IN_PROGRESS" ||
                (previous?.status == "INTERRUPTED" && game.status == "INTERRUPTED")) {
                "Only an existing unfinished game can be updated."
            }
            check(updateGame(game) == 1)
            // Les cartes du joueur référencent les mains : les supprimer avant les mains.
            deleteDrawnCards(game.id)
            deleteShoeCards(game.id)
            deleteHands(game.id)
            game.id
        }

        insertHands(hands.map { it.copy(gameId = gameId) })
        insertDrawnCards(drawnCards.map { it.copy(gameId = gameId) })
        insertShoeCards(shoeCards.map { it.copy(gameId = gameId) })
        // Une erreur, même sur la dernière carte du sabot, restaure la sauvegarde précédente.
        // Aucun appel à getOrCreateBalance() ou applyChipChange() ici.
        return gameId
    }

    @Transaction
    suspend fun loadGameProgress(): GameProgress? {
        val game = getInProgressGame() ?: return null
        // La transaction évite de lire une partie avec les détails d'une autre sauvegarde.
        return GameProgress(game, getHands(game.id), getDrawnCards(game.id), getShoeCards(game.id))
    }

    @Transaction
    suspend fun finishGameProgress(
        game: GameEntity,
        hands: List<HandEntity>,
        drawnCards: List<DrawnCardEntity>,
        shoeCards: List<ShoeCardEntity>
    ): Long {
        require(game.id > 0 && game.status == "FINISHED" && game.phase == "FINISHED")
        require(game.result != null)
        val chipChange = requireNotNull(game.chipChange)
        require(requireNotNull(game.activeHandIndex) in hands.indices)
        require(requireNotNull(game.deckCount) > 0)
        require(hands.all { it.result != null && it.chipChange != null })

        val storedGame = checkNotNull(getGame(game.id)) { "The saved game does not exist." }
        if (storedGame.status == "FINISHED") {
            // Réessayer le même règlement est permis, sans réécrire ni payer une deuxième fois.
            // Les identifiants de cartes sont générés par Room : on compare leur contenu.
            check(storedGame == game &&
                getHands(game.id) == hands.map { it.copy(gameId = game.id) }.sortedBy { it.handNumber } &&
                getDrawnCards(game.id).map { it.copy(id = 0) } ==
                    drawnCards.map { it.copy(id = 0, gameId = game.id) }.sortedBy { it.drawOrder } &&
                getShoeCards(game.id) == shoeCards.map { it.copy(gameId = game.id) }.sortedBy { it.position }
            ) { "The game was already settled with different data." }
            return game.id
        }
        check(storedGame.status == "IN_PROGRESS") { "The game is not awaiting settlement." }

        check(updateGame(game) == 1)
        deleteDrawnCards(game.id)
        deleteShoeCards(game.id)
        deleteHands(game.id)
        insertHands(hands.map { it.copy(gameId = game.id) })
        insertDrawnCards(drawnCards.map { it.copy(gameId = game.id) })
        insertShoeCards(shoeCards.map { it.copy(gameId = game.id) })
        getOrCreateBalance()
        // Le passage à FINISHED et le paiement réussissent ensemble, ou sont tous deux annulés.
        check(applyChipChange(chipChange) == 1)
        return game.id
    }

    @Transaction
    suspend fun insertGameWithDetails(
        game: GameEntity,
        hands: List<HandEntity>,
        cards: List<DrawnCardEntity>
    ): Long {
        // Cette transaction reste réservée au règlement final, pas aux sauvegardes en cours.
        require(game.status == "FINISHED" && game.result != null)
        val chipChange = requireNotNull(game.chipChange)
        require(hands.all { it.result != null && it.chipChange != null })
        getOrCreateBalance()
        val gameId = insertGame(game)

        // Room génère l'identifiant : les mains et cartes doivent le reprendre.
        val linkedHands = hands.map { it.copy(gameId = gameId) }
        val linkedCards = cards.map { it.copy(gameId = gameId) }

        // Les mains doivent exister avant leurs cartes pour les clés étrangères.
        insertHands(linkedHands)
        insertDrawnCards(linkedCards)
        // Dans la même transaction : une erreur annule la partie ET le paiement.
        check(applyChipChange(chipChange) == 1)
        return gameId
    }

    @Query("SELECT * FROM games WHERE status = 'FINISHED' ORDER BY playedAt DESC, id DESC")
    fun observeHistory(): Flow<List<GameEntity>>

    @Query("SELECT * FROM hands WHERE gameId IN (SELECT id FROM games WHERE status = 'FINISHED') ORDER BY gameId DESC, handNumber ASC")
    fun observeFinishedHands(): Flow<List<HandEntity>>

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
            FROM games WHERE status = 'FINISHED'
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
            FROM hands WHERE gameId IN (SELECT id FROM games WHERE status = 'FINISHED')
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

    // Cette liste SQL garantit les 13 rangs, même ceux qui n'ont pas été distribués.
    // GROUP BY compte les cartes de chaque rang; LEFT JOIN conserve les rangs absents.
    @Query("""
        WITH ranks(rank, position) AS (
            VALUES ('ACE', 1), ('TWO', 2), ('THREE', 3), ('FOUR', 4),
                   ('FIVE', 5), ('SIX', 6), ('SEVEN', 7), ('EIGHT', 8),
                   ('NINE', 9), ('TEN', 10), ('JACK', 11), ('QUEEN', 12), ('KING', 13)
        ), counts AS (
            SELECT rank, COUNT(*) AS cardCount
            FROM drawn_cards
            GROUP BY rank
        )
        SELECT ranks.rank,
               COALESCE(counts.cardCount, 0) AS cardCount,
               100.0 * COALESCE(counts.cardCount, 0)
                   / NULLIF((SELECT COUNT(*) FROM drawn_cards), 0) AS percentage
        FROM ranks
        LEFT JOIN counts ON counts.rank = ranks.rank
        ORDER BY ranks.position
    """)
    fun observeRankStatistics(): Flow<List<RankStatistics>>

    @Query("SELECT * FROM hands WHERE gameId = :gameId ORDER BY handNumber ASC")
    suspend fun getHands(gameId: Long): List<HandEntity>

    @Query("SELECT * FROM drawn_cards WHERE gameId = :gameId ORDER BY drawOrder ASC")
    suspend fun getDrawnCards(gameId: Long): List<DrawnCardEntity>
}
