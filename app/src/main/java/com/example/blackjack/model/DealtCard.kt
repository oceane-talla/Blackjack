package com.example.blackjack.model

data class DealtCard(
    val card: Card,
    // null pour le croupier; 1 pour la main actuelle du joueur.
    val handNumber: Int?,
    val drawOrder: Int
)
