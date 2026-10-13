package com.example.blackjack.model

// Résultat d'une requête SQL, sans @Entity : cette classe ne crée aucune nouvelle table.
// Les pourcentages sont exprimés de 0 à 100 ; null signifie aucune carte enregistrée.
data class GameStatistics(
    // Parties terminées : une partie avec Split compte une seule fois.
    val totalGames: Long = 0,
    // Mains du joueur des parties terminées : une partie avec Split en possède deux.
    val totalHands: Long = 0,
    // Toutes les cartes distribuées et sauvegardées, joueur et croupier inclus.
    val totalCards: Long = 0,
    // Résultats comptés par main, indépendamment du résultat résumé de la partie.
    val winningHands: Long = 0,
    val losingHands: Long = 0,
    val drawnHands: Long = 0,
    // Nombre de mains marquées comme Blackjack naturel, même en cas d'égalité.
    val naturalBlackjacks: Long = 0,
    // Somme des gains nets positifs des mains, sans y inclure les mises rendues.
    val totalGains: Double = 0.0,
    // Somme des pertes des mains, exprimée en montant positif (exemple : 10 perdus).
    val totalLosses: Double = 0.0,
    // Mise totale moyenne par partie ; null si aucune partie n'a été enregistrée.
    val averageBet: Double? = null,
    // Plus grosse mise totale par partie ; null si aucune partie n'a été enregistrée.
    val highestBet: Int? = null,
    // Cartes rouges : cœurs et carreaux. Cartes noires : trèfles et piques.
    val redCards: Long = 0,
    val blackCards: Long = 0,
    val redPercentage: Double? = null,
    val blackPercentage: Double? = null,
    // Nombre et proportion de chaque sorte parmi toutes les cartes sauvegardées.
    val heartsCards: Long = 0,
    val diamondsCards: Long = 0,
    val clubsCards: Long = 0,
    val spadesCards: Long = 0,
    val heartsPercentage: Double? = null,
    val diamondsPercentage: Double? = null,
    val clubsPercentage: Double? = null,
    val spadesPercentage: Double? = null
)
