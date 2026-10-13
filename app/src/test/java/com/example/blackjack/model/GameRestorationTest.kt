package com.example.blackjack.model

import org.junit.Assert.*
import org.junit.Test

class GameRestorationTest {
    private fun shoe(vararg ranks: Rank) =
        Shoe(ranks.map { Card(it, Suit.HEARTS) }.reversed())

    private fun restore(game: BlackjackGame, shoe: Shoe): BlackjackGame =
        BlackjackGame.restore(shoe, game.dealtCards, game.playerHands.map { it.result },
            game.activeHandIndex, game.phase, game.result)

    @Test fun restoredShoePreservesOrderDuplicatesAndCopiesItsInput() {
        val card = Card(Rank.ACE, Suit.HEARTS)
        val input = mutableListOf(card, card, Card(Rank.TEN, Suit.SPADES))
        val shoe = Shoe(input)
        input.clear()
        val snapshot = shoe.remainingCardList
        assertEquals(Card(Rank.TEN, Suit.SPADES), shoe.drawCard())
        assertEquals(3, snapshot.size)
        assertEquals(card, shoe.drawCard())
        assertEquals(card, shoe.drawCard())
        assertNull(shoe.drawCard())
        assertNull(Shoe(emptyList()).drawCard())
    }

    @Test fun restoredRoundContinuesWithTheSameNextCardAndResult() {
        val originalShoe = shoe(Rank.TEN, Rank.TEN, Rank.SIX, Rank.SIX, Rank.THREE, Rank.TWO)
        val original = BlackjackGame(originalShoe)
        original.startRound()
        val restoredShoe = Shoe(originalShoe.remainingCardList)
        val restored = restore(original, restoredShoe)
        assertEquals(original.playerHands, restored.playerHands)
        assertEquals(original.dealerCards, restored.dealerCards)
        assertEquals(originalShoe.remainingCardList, restoredShoe.remainingCardList)
        original.hit(); restored.hit()
        original.stand(); restored.stand()
        assertEquals(original.dealtCards, restored.dealtCards)
        assertEquals(original.result, restored.result)
        assertEquals(original.calculateChipChange(10), restored.calculateChipChange(10), 0.0)
    }

    @Test fun splitRestoresSecondActiveHandAndFirstHandsLoss() {
        val originalShoe = shoe(Rank.TEN, Rank.TEN, Rank.TEN, Rank.SEVEN,
            Rank.SIX, Rank.SIX, Rank.KING, Rank.THREE)
        val original = BlackjackGame(originalShoe)
        original.startRound()
        original.split(10, 1000.0)
        original.hit() // Première main dépassée; deuxième main active.
        val restored = restore(original, Shoe(originalShoe.remainingCardList))
        assertTrue(restored.hasSplit)
        assertEquals(1, restored.activeHandIndex)
        assertEquals(original.playerHands, restored.playerHands)
        original.hit(); restored.hit()
        original.stand(); restored.stand()
        assertEquals(original.playerHands, restored.playerHands)
        assertEquals(original.result, restored.result)
        assertEquals(original.calculateChipChange(10), restored.calculateChipChange(10), 0.0)
    }

    @Test fun finishedNaturalBlackjackKeepsItsBonusWithoutDrawingAgain() {
        val source = shoe(Rank.ACE, Rank.TEN, Rank.KING, Rank.SEVEN, Rank.TWO)
        val original = BlackjackGame(source)
        original.startRound()
        val remaining = Shoe(source.remainingCardList)
        val restored = restore(original, remaining)
        assertTrue(restored.playerHasBlackjack)
        assertEquals(GamePhase.FINISHED, restored.phase)
        assertEquals(20.0, restored.calculateChipChange(10), 0.0)
        assertFalse(restored.hit())
        assertEquals(1, remaining.remainingCards)
    }

    @Test fun interruptedRoundStaysInterruptedWithItsExistingCards() {
        val source = shoe(Rank.TEN, Rank.TEN, Rank.SIX, Rank.SIX)
        val original = BlackjackGame(source)
        original.startRound(); original.hit()
        val restored = restore(original, Shoe(source.remainingCardList))
        assertEquals(GamePhase.INTERRUPTED, restored.phase)
        assertEquals(original.dealtCards, restored.dealtCards)
        assertNull(restored.result)
        assertFalse(restored.hit())
    }

    @Test fun finishedSplitAcesKeepOrdinaryPayments() {
        val source = shoe(Rank.ACE, Rank.TEN, Rank.ACE, Rank.SEVEN, Rank.KING, Rank.QUEEN)
        val original = BlackjackGame(source)
        original.startRound(); original.split(10, 1000.0)
        val restored = restore(original, Shoe(source.remainingCardList))
        assertEquals(GamePhase.FINISHED, restored.phase)
        assertTrue(restored.playerHands.all { it.total == 21 && !it.hasBlackjack })
        assertEquals(10.0, restored.calculateHandChipChange(1, 10), 0.0)
        assertEquals(10.0, restored.calculateHandChipChange(2, 10), 0.0)
        assertFalse(restored.canHit)
    }

    @Test fun invalidOrderHandIndexAndFinishedResultAreRejected() {
        val source = shoe(Rank.TEN, Rank.TEN, Rank.SIX, Rank.SIX)
        val original = BlackjackGame(source)
        original.startRound()
        assertThrows(IllegalArgumentException::class.java) {
            BlackjackGame.restore(source, original.dealtCards.reversed(), listOf(null),
                0, GamePhase.PLAYER_TURN, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BlackjackGame.restore(source, original.dealtCards, listOf(null),
                1, GamePhase.PLAYER_TURN, null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BlackjackGame.restore(source, original.dealtCards, listOf(GameResult.WIN),
                0, GamePhase.FINISHED, GameResult.LOSS)
        }
    }
}
