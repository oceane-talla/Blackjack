package com.example.blackjack.model

data class DealtCard(
    val card: Card,
    // null pour le croupier; 1 ou 2 pour les mains du joueur.
    val handNumber: Int?,
    val drawOrder: Int
)
