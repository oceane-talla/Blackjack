package com.example.blackjack.viewmodel

import com.example.blackjack.model.GameStatistics
import com.example.blackjack.model.RankStatistics

// État observable de l'écran de statistiques.
data class StatisticsUiState(
    // Valeurs déjà calculées par SQLite, sans collection de parties dupliquée dans l'écran.
    val statistics: GameStatistics = GameStatistics(),
    // Les 13 rangs, avec leurs nombres et pourcentages calculés par SQL.
    val rankStatistics: List<RankStatistics> = emptyList(),
    // Vrai jusqu'au premier résultat des deux requêtes, sauf erreur de lecture.
    val isLoading: Boolean = true,
    // Signale une erreur de lecture ; l'écran choisira le message traduit.
    val hasError: Boolean = false
)
