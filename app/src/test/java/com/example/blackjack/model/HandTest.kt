package com.example.blackjack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandTest {
    @Test
    fun emptyHandHasNoValueAndNoBlackjack() {
        val hand = Hand()

        assertEquals(0, hand.calculateValue())
        assertFalse(hand.isBust())
        assertFalse(hand.hasBlackjackValue())
    }

    @Test
    fun numberedCardsAreAddedTogether() {
        val hand = Hand(listOf(card(Rank.FOUR), card(Rank.SIX)))

        assertEquals(10, hand.calculateValue())
    }

    @Test
    fun twoFaceCardsAreWorthTwenty() {
        val hand = Hand(listOf(card(Rank.QUEEN), card(Rank.KING)))

        assertEquals(20, hand.calculateValue())
        assertFalse(hand.hasBlackjackValue())
    }

    @Test
    fun aceAndNineAreWorthTwenty() {
        val hand = Hand(listOf(card(Rank.ACE), card(Rank.NINE)))

        assertEquals(20, hand.calculateValue())
    }

    @Test
    fun addingFiveToAceAndNineReducesTheAceToOne() {
        val hand = Hand(listOf(card(Rank.ACE), card(Rank.NINE)))

        hand.addCard(card(Rank.FIVE))

        assertEquals(15, hand.calculateValue())
        assertEquals(3, hand.cards.size)
        assertFalse(hand.isBust())
    }

    @Test
    fun twoAcesAndNineAreTwentyOneWithoutBlackjack() {
        val hand = Hand(listOf(card(Rank.ACE), card(Rank.ACE), card(Rank.NINE)))

        assertEquals(21, hand.calculateValue())
        assertFalse(hand.isBust())
        assertFalse(hand.hasBlackjackValue())
    }

    @Test
    fun twoAcesAndTenNeedBothAcesReduced() {
        val hand = Hand(listOf(card(Rank.ACE), card(Rank.ACE), card(Rank.TEN)))

        assertEquals(12, hand.calculateValue())
        assertFalse(hand.isBust())
    }

    @Test
    fun fourAcesLeaveOneAceAtEleven() {
        val hand = Hand(List(4) { card(Rank.ACE) })

        assertEquals(14, hand.calculateValue())
    }

    @Test
    fun twentyTwoIsBust() {
        val hand = Hand(listOf(card(Rank.KING), card(Rank.TEN), card(Rank.TWO)))

        assertEquals(22, hand.calculateValue())
        assertTrue(hand.isBust())
        assertFalse(hand.hasBlackjackValue())
    }

    @Test
    fun aceAndTenOrFaceCardHaveBlackjackValue() {
        for (rank in listOf(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING)) {
            val hand = Hand(listOf(card(Rank.ACE), card(rank)))

            assertEquals(21, hand.calculateValue())
            assertTrue(hand.hasBlackjackValue())
            assertFalse(hand.isBust())
        }
    }

    @Test
    fun threeSevensAreTwentyOneWithoutBlackjack() {
        val hand = Hand(List(3) { card(Rank.SEVEN) })

        assertEquals(21, hand.calculateValue())
        assertFalse(hand.hasBlackjackValue())
    }

    private fun card(rank: Rank): Card = Card(rank, Suit.HEARTS)
}
