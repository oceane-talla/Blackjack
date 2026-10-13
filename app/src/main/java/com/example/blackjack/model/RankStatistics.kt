package com.example.blackjack.model

// Résultat SQL, pas une Entity : aucune table supplémentaire n'est créée.
data class RankStatistics(
    // Nom de Rank sauvegardé dans Room : ACE, TWO, ..., KING.
    val rank: String,
    val cardCount: Long,
    // Proportion parmi toutes les cartes distribuées, joueur et croupier inclus.
    // null sans cartes; 0 pour un rang absent lorsque d'autres cartes existent.
    val percentage: Double?
)
