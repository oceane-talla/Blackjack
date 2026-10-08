package com.example.blackjack.model

class Shoe(numberOfDecks: Int) {
    private val availableCards = mutableListOf<Card>()

    val remainingCards: Int
        get() = availableCards.size

    init {
        require(numberOfDecks > 0) { "The number of decks must be greater than zero." }

        repeat(numberOfDecks) {
            for (suit in Suit.entries) {
                for (rank in Rank.entries) {
                    availableCards.add(Card(rank, suit))
                }
            }
        }

        shuffle()
    }

    fun shuffle() {
        availableCards.shuffle()
    }

    fun drawCard(): Card? {
        if (availableCards.isEmpty()) {
            return null
        }

        // Retirer la carte empêche de distribuer à nouveau le même exemplaire.
        return availableCards.removeAt(availableCards.lastIndex)
    }
}
