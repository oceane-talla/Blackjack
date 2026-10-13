package com.example.blackjack.model

class Shoe private constructor() {
    private val availableCards = mutableListOf<Card>()

    val remainingCards: Int
        get() = availableCards.size

    // Une copie : le repository peut sauvegarder l'ordre sans modifier le sabot.
    val remainingCardList: List<Card>
        get() = availableCards.toList()

    constructor(savedCards: List<Card>) : this() {
        // Aucun paquet neuf ni mélange : on reprend exactement le sabot sauvegardé.
        availableCards.addAll(savedCards)
    }

    constructor(numberOfDecks: Int) : this() {
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
