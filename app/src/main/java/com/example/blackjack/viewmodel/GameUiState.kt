package com.example.blackjack.viewmodel

import com.example.blackjack.model.Card
import com.example.blackjack.model.GamePhase
import com.example.blackjack.model.GameResult
import com.example.blackjack.model.PlayerHandState

enum class GameError {
    // Impossible de lire le solde : le jeu attend une nouvelle tentative.
    BALANCE_LOAD_FAILED,
    // La mise demandée est hors des limites ou dépasse le solde disponible.
    BET_NOT_ALLOWED,
    // La partie n'a pas pu continuer, par exemple si le sabot est vide.
    ROUND_INTERRUPTED,
    // Une sauvegarde intermédiaire ou finale a échoué; les actions attendent une reprise.
    SAVE_FAILED
}

// Données observées par Compose pour afficher l'écran de partie.
data class GameUiState(
    // Solde du joueur : la variation nette est appliquée à la fin de la partie.
    val chips: Double = 1_000.0,
    // Empêche de jouer avec un solde provisoire pendant sa lecture dans Room.
    val isLoadingBalance: Boolean = true,
    // Mise sélectionnée pour commencer la prochaine partie.
    val selectedBet: Int = 1,
    // Nombre de paquets du prochain sabot, conservé entre les parties (de 1 à 8).
    val selectedDeckCount: Int = 1,
    // Mise fixée au début de la partie ; null avant la première partie.
    val roundBet: Int? = null,
    // Cartes du joueur à afficher.
    val playerCards: List<Card> = emptyList(),
    // Toutes les mains, dans leur ordre de jeu; nécessaire pour préparer l'affichage du Split.
    val playerHands: List<PlayerHandState> = emptyList(),
    // Index Kotlin (0 ou 1) de la main qui reçoit les prochaines actions.
    val activeHandIndex: Int = 0,
    // Une entrée null représente le dos de la carte cachée du croupier.
    val dealerCards: List<Card?> = emptyList(),
    // Total des points de la main du joueur.
    val playerTotal: Int = 0,
    // Total du croupier ; null tant que sa première carte est cachée.
    val dealerTotal: Int? = 0,
    // Étape actuelle : attente, tour du joueur, tour du croupier, fin ou interruption.
    val phase: GamePhase = GamePhase.READY,
    // Victoire, défaite ou égalité ; null si aucun résultat n'a été déterminé.
    val result: GameResult? = null,
    // Indique un Blackjack naturel : 21 avec les deux premières cartes du joueur.
    val playerHasBlackjack: Boolean = false,
    // Gain ou perte nette ; null avant le règlement, 0.0 pour une égalité.
    val chipChange: Double? = null,
    // Permet d'activer le bouton pour commencer une partie.
    val canStartGame: Boolean = false,
    // Permet d'activer le bouton Tirer.
    val canHit: Boolean = false,
    // Permet d'activer le bouton Rester.
    val canStand: Boolean = false,
    // Le modèle autorise le Split si la paire, le solde et le tour sont valides.
    val canSplit: Boolean = false,
    // Indique que l'enregistrement de la partie est en cours.
    val isSaving: Boolean = false,
    // Identifiant retourné par Room ; null tant que la partie n'est pas sauvegardée.
    val savedGameId: Long? = null,
    // Erreur que l'écran pourra expliquer au joueur ; null signifie aucune erreur.
    val error: GameError? = null
) {
    // Calculé à partir de l'état : interdit de changer la mise pendant le jeu
    // ou tant que la sauvegarde est en cours ou reste à réessayer.
    val canChangeBet: Boolean
        get() = !isLoadingBalance && error != GameError.BALANCE_LOAD_FAILED &&
            phase != GamePhase.PLAYER_TURN && phase != GamePhase.DEALER_TURN &&
            !isSaving && error != GameError.SAVE_FAILED

    // Autorise une nouvelle tentative après un échec, sans sauvegardes simultanées.
    val canRetrySave: Boolean
        get() = error == GameError.SAVE_FAILED && !isSaving
}
