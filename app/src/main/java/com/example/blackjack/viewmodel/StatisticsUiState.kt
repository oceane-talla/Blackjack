package com.example.blackjack.viewmodel

import com.example.blackjack.model.GameStatistics

// État observable du futur écran de statistiques.
data class StatisticsUiState(
    // Valeurs déjà calculées par SQLite, sans collection de parties dupliquée dans l'écran.
    val statistics: GameStatistics = GameStatistics(),
    // Vrai jusqu'à la réception du premier résultat, même si la base est vide.
    val isLoading: Boolean = true,
    // Signale une erreur de lecture ; l'écran choisira le message traduit.
    val hasError: Boolean = false
)
