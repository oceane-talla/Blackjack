package com.example.blackjack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Test

class ShoeTest {
    @Test
    fun oneDeckContainsAllFiftyTwoCardValues() {
        val shoe = Shoe(numberOfDecks = 1)

        assertEquals(52, shoe.remainingCards)
        val cards = drawAll(shoe)

        assertEquals(52, cards.size)
        assertEquals(52, cards.toSet().size)
        for (suit in Suit.entries) {
            assertEquals(13, cards.count { it.suit == suit })
        }
    }

    @Test
    fun threeDecksContainThreeCopiesOfEachRankAndSuit() {
        val shoe = Shoe(numberOfDecks = 3)

        assertEquals(156, shoe.remainingCards)
        val cards = drawAll(shoe)

        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
                assertEquals(3, cards.count { it.rank == rank && it.suit == suit })
            }
        }
    }

    @Test
    fun everyDrawRemovesOneDistinctPhysicalCard() {
        val shoe = Shoe(numberOfDecks = 2)

        val cards = drawAll(shoe)

        // Deux paquets donnent 104 exemplaires, mais seulement 52 rangs/couleurs.
        assertEquals(104, cards.size)
        assertEquals(52, cards.toSet().size)
        assertEquals(0, shoe.remainingCards)
    }

    @Test
    fun emptyShoeKeepsReturningNullWithoutRefillingItself() {
        val shoe = Shoe(numberOfDecks = 1)
        drawAll(shoe)

        assertNull(shoe.drawCard())
        assertNull(shoe.drawCard())
        assertEquals(0, shoe.remainingCards)
    }

    @Test
    fun shufflingDoesNotReintroduceDrawnCards() {
        val shoe = Shoe(numberOfDecks = 1)
        val drawnCards = mutableListOf<Card>()
        repeat(10) {
            drawnCards.add(shoe.drawCard() ?: error("Expected an available card."))
        }

        shoe.shuffle()

        assertEquals(42, shoe.remainingCards)
        val remainingCards = drawAll(shoe)
        for (card in remainingCards) {
            for (drawnCard in drawnCards) {
                assertNotSame(drawnCard, card)
            }
        }
        assertEquals(52, (drawnCards + remainingCards).toSet().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDecksAreRejected() {
        Shoe(numberOfDecks = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDeckCountsAreRejected() {
        Shoe(numberOfDecks = -1)
    }

    private fun drawAll(shoe: Shoe): List<Card> {
        val initialCount = shoe.remainingCards
        val cards = mutableListOf<Card>()

        repeat(initialCount) { index ->
            val card = shoe.drawCard() ?: error("Expected an available card.")
            assertEquals(initialCount - index - 1, shoe.remainingCards)
            // Des valeurs identiques sont permises; le même objet ne doit pas revenir.
            for (previousCard in cards) {
                assertNotSame(previousCard, card)
            }
            cards.add(card)
        }

        assertNull(shoe.drawCard())
        return cards
    }
}
