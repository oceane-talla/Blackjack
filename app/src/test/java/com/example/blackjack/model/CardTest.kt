package com.example.blackjack.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CardTest {
    @Test
    fun numberedCardsKeepTheirNumberAsValue() {
        val ranks = listOf(
            Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.SIX,
            Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN
        )

        for ((index, rank) in ranks.withIndex()) {
            assertEquals(index + 2, Card(rank, Suit.SPADES).baseValue)
        }
    }

    @Test
    fun faceCardsAreWorthTen() {
        for (rank in listOf(Rank.JACK, Rank.QUEEN, Rank.KING)) {
            assertEquals(10, Card(rank, Suit.HEARTS).baseValue)
        }
    }

    @Test
    fun aceStartsAtElevenBeforeTheHandAdjustsIt() {
        assertEquals(11, Card(Rank.ACE, Suit.CLUBS).baseValue)
    }
}
