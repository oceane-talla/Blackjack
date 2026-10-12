package com.example.blackjack.viewmodel

import com.example.blackjack.database.entity.GameEntity

// Données que l'écran d'historique pourra observer et afficher.
data class HistoryUiState(
    // Résumés enregistrés, du plus récent au plus ancien selon la requête du DAO.
    val games: List<GameEntity> = emptyList(),
    // Vrai jusqu'à la première liste reçue : une liste vide peut ensuite être un résultat normal.
    val isLoading: Boolean = true,
    // Signale un échec de lecture ; l'écran choisira le texte traduit à afficher.
    val hasError: Boolean = false
)
