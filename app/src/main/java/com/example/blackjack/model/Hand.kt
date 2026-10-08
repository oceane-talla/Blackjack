package com.example.blackjack.model

class Hand(initialCards: List<Card> = emptyList()) {
    private val handCards = initialCards.toMutableList()

    val cards: List<Card>
        get() = handCards.toList()

    fun addCard(card: Card) {
        handCards.add(card)
    }

    fun calculateValue(): Int {
        var total = 0
        var acesCountedAsEleven = 0

        for (card in handCards) {
            total += card.baseValue
            if (card.rank == Rank.ACE) {
                acesCountedAsEleven++
            }
        }

        // Chaque As peut passer de 11 à 1 : le total diminue alors de 10.
        while (total > 21 && acesCountedAsEleven > 0) {
            total -= 10
            acesCountedAsEleven--
        }

        return total
    }

    fun isBust(): Boolean = calculateValue() > 21

    // Le contexte de la partie décidera si ce 21 est un Blackjack naturel.
    fun hasBlackjackValue(): Boolean = handCards.size == 2 && calculateValue() == 21
}
