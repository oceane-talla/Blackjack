package com.example.blackjack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlackjackGameTest {
    @Test
    fun newGameIsReadyWithoutCardsOrResult() {
        val game = gameWith()

        assertEquals(GamePhase.READY, game.phase)
        assertTrue(game.playerCards.isEmpty())
        assertTrue(game.dealerCards.isEmpty())
        assertEquals(0, game.playerValue)
        assertEquals(0, game.dealerValue)
        assertNull(game.result)
        assertFalse(game.canHit)
        assertFalse(game.canStand)
    }

    @Test
    fun publicConstructorUsesTheShoeWithoutReplacingIt() {
        val shoe = Shoe(1)
        val game = BlackjackGame(shoe)

        assertTrue(game.startRound())

        assertEquals(48, shoe.remainingCards)
        assertEquals(2, game.playerCards.size)
        assertEquals(2, game.dealerCards.size)
        assertEquals(4, (game.playerCards + game.dealerCards).toSet().size)
    }

    @Test
    fun initialCardsAreDealtAlternately() {
        val game = gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX)

        assertTrue(game.startRound())

        assertEquals(listOf(Rank.TEN, Rank.NINE), game.playerCards.map { it.rank })
        assertEquals(listOf(Rank.FIVE, Rank.SIX), game.dealerCards.map { it.rank })
        assertEquals(19, game.playerValue)
        assertEquals(11, game.dealerValue)
        assertEquals(GamePhase.PLAYER_TURN, game.phase)
        assertNull(game.result)
        assertTrue(game.canHit)
        assertTrue(game.canStand)
    }

    @Test
    fun anotherStartCannotReplaceAnActiveRound() {
        val game = gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX, Rank.TWO, Rank.SIX)
        game.startRound()
        val playerCards = game.playerCards
        val dealerCards = game.dealerCards

        assertFalse(game.startRound())

        assertEquals(playerCards, game.playerCards)
        assertEquals(dealerCards, game.dealerCards)
        assertEquals(GamePhase.PLAYER_TURN, game.phase)
        assertTrue(game.hit())
        assertEquals(21, game.playerValue)
        assertFinished(game, GameResult.WIN)
    }

    @Test
    fun playersNaturalBlackjackWinsImmediately() {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)

        assertTrue(game.startRound())

        assertTrue(game.playerHasBlackjack)
        assertFalse(game.dealerHasBlackjack)
        assertFinished(game, GameResult.WIN)
        assertEquals(2, game.dealerCards.size)
    }

    @Test
    fun dealersNaturalBlackjackWinsImmediately() {
        val game = gameWith(Rank.TEN, Rank.ACE, Rank.NINE, Rank.QUEEN)

        assertTrue(game.startRound())

        assertFalse(game.playerHasBlackjack)
        assertTrue(game.dealerHasBlackjack)
        assertFinished(game, GameResult.LOSS)
    }

    @Test
    fun twoNaturalBlackjacksAreADraw() {
        val game = gameWith(Rank.ACE, Rank.ACE, Rank.TEN, Rank.JACK)
        game.startRound()

        assertTrue(game.playerHasBlackjack)
        assertTrue(game.dealerHasBlackjack)
        assertFinished(game, GameResult.DRAW)
    }

    @Test
    fun hitAddsACardAndKeepsThePlayersTurnUnderTwentyOne() {
        val game = gameWith(Rank.FIVE, Rank.TEN, Rank.SIX, Rank.EIGHT, Rank.SEVEN)
        game.startRound()

        assertTrue(game.hit())

        assertEquals(18, game.playerValue)
        assertEquals(3, game.playerCards.size)
        assertEquals(2, game.dealerCards.size)
        assertEquals(GamePhase.PLAYER_TURN, game.phase)
        assertNull(game.result)
    }

    @Test
    fun hitUsesTheAdjustedAceValue() {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.NINE, Rank.EIGHT, Rank.FIVE)
        game.startRound()

        assertTrue(game.hit())

        assertEquals(15, game.playerValue)
        assertEquals(GamePhase.PLAYER_TURN, game.phase)
    }

    @Test
    fun playerBustLosesWithoutMakingTheDealerDraw() {
        val game = gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX, Rank.THREE)
        game.startRound()

        assertTrue(game.hit())

        assertEquals(22, game.playerValue)
        assertEquals(2, game.dealerCards.size)
        assertFinished(game, GameResult.LOSS)
    }

    @Test
    fun twentyOneWithThreeCardsStartsTheDealerTurnWithoutANaturalBlackjack() {
        val game = gameWith(Rank.SEVEN, Rank.TEN, Rank.SEVEN, Rank.SEVEN, Rank.SEVEN)
        game.startRound()

        assertTrue(game.hit())

        assertEquals(21, game.playerValue)
        assertFalse(game.playerHasBlackjack)
        assertFinished(game, GameResult.WIN)
    }

    @Test
    fun twentyOneWithThreeCardsCanDrawWithTheDealer() {
        val game = gameWith(Rank.SEVEN, Rank.TEN, Rank.SEVEN, Rank.SIX, Rank.SEVEN, Rank.FIVE)
        game.startRound()

        assertTrue(game.hit())

        assertEquals(21, game.playerValue)
        assertEquals(21, game.dealerValue)
        assertFalse(game.playerHasBlackjack)
        assertFalse(game.dealerHasBlackjack)
        assertFinished(game, GameResult.DRAW)
    }

    @Test
    fun dealerDrawsBelowSeventeenAndStopsAtSeventeen() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.NINE, Rank.SIX, Rank.ACE)
        game.startRound()

        assertTrue(game.stand())

        assertEquals(17, game.dealerValue)
        assertEquals(3, game.dealerCards.size)
        assertFinished(game, GameResult.WIN)
    }

    @Test
    fun dealerCanNeedSeveralDraws() {
        val game = gameWith(Rank.TEN, Rank.TWO, Rank.NINE, Rank.THREE, Rank.FOUR, Rank.FIVE, Rank.THREE)
        game.startRound()

        assertTrue(game.stand())

        assertEquals(17, game.dealerValue)
        assertEquals(5, game.dealerCards.size)
        assertFinished(game, GameResult.WIN)
    }

    @Test
    fun dealerStopsAtSoftSeventeen() {
        val game = gameWith(Rank.TEN, Rank.ACE, Rank.NINE, Rank.SIX)
        game.startRound()

        assertTrue(game.stand())

        assertEquals(17, game.dealerValue)
        assertEquals(2, game.dealerCards.size)
        assertFinished(game, GameResult.WIN)
    }

    @Test
    fun dealerStopsAtSeventeenOrHigherWithoutRequestingAnotherCard() {
        for (dealerSecondRank in listOf(Rank.SEVEN, Rank.EIGHT, Rank.NINE, Rank.TEN)) {
            val game = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, dealerSecondRank)
            game.startRound()

            assertTrue(game.stand())

            assertEquals(2, game.dealerCards.size)
            assertEquals(GamePhase.FINISHED, game.phase)
        }
    }

    @Test
    fun dealerBustGivesThePlayerAWin() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.SIX, Rank.KING)
        game.startRound()

        assertTrue(game.stand())

        assertEquals(26, game.dealerValue)
        assertFinished(game, GameResult.WIN)
    }

    @Test
    fun higherPlayerTotalWins() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.NINE, Rank.EIGHT)
        game.startRound()

        assertTrue(game.stand())

        assertFinished(game, GameResult.WIN)
    }

    @Test
    fun higherDealerTotalWins() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.NINE)
        game.startRound()

        assertTrue(game.stand())

        assertFinished(game, GameResult.LOSS)
    }

    @Test
    fun equalTotalsAreADraw() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.EIGHT)
        game.startRound()

        assertTrue(game.stand())

        assertFinished(game, GameResult.DRAW)
    }

    @Test
    fun actionsBeforeStartingDoNothing() {
        val game = gameWith(Rank.TEN)

        assertFalse(game.hit())
        assertFalse(game.stand())

        assertEquals(GamePhase.READY, game.phase)
        assertTrue(game.playerCards.isEmpty())
        assertTrue(game.dealerCards.isEmpty())
    }

    @Test
    fun actionsAfterFinishingDoNotChangeTheCardsOrResult() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.NINE, Rank.EIGHT, Rank.ACE)
        game.startRound()
        game.stand()
        val playerCards = game.playerCards
        val dealerCards = game.dealerCards

        assertFalse(game.hit())
        assertFalse(game.stand())

        assertEquals(playerCards, game.playerCards)
        assertEquals(dealerCards, game.dealerCards)
        assertFinished(game, GameResult.WIN)
    }

    @Test
    fun aNewRoundClearsThePreviousHandsAndBlackjackFlags() {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE,
            Rank.FIVE, Rank.TEN, Rank.SIX, Rank.EIGHT)
        game.startRound()
        assertTrue(game.playerHasBlackjack)

        assertTrue(game.startRound())

        assertEquals(11, game.playerValue)
        assertEquals(18, game.dealerValue)
        assertEquals(2, game.playerCards.size)
        assertEquals(2, game.dealerCards.size)
        assertFalse(game.playerHasBlackjack)
        assertFalse(game.dealerHasBlackjack)
        assertNull(game.result)
        assertEquals(GamePhase.PLAYER_TURN, game.phase)
    }

    @Test
    fun runningOutDuringAnyInitialDrawInterruptsWithoutAResult() {
        val initialRanks = listOf(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX)
        for (availableCount in 0..3) {
            val game = gameWith(*initialRanks.take(availableCount).toTypedArray())

            assertFalse(game.startRound())

            assertEquals(availableCount, game.playerCards.size + game.dealerCards.size)
            assertInterrupted(game)
            assertFalse(game.playerHasBlackjack)
            assertFalse(game.dealerHasBlackjack)
        }
    }

    @Test
    fun emptyShoeOnHitInterruptsWithoutAddingACard() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.NINE)
        game.startRound()

        assertFalse(game.hit())

        assertEquals(2, game.playerCards.size)
        assertEquals(18, game.playerValue)
        assertInterrupted(game)
    }

    @Test
    fun emptyShoeDuringTheDealerTurnInterruptsInsteadOfComparingPartialTotals() {
        val game = gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX, Rank.TWO)
        game.startRound()

        assertFalse(game.stand())

        assertEquals(13, game.dealerValue)
        assertEquals(3, game.dealerCards.size)
        assertInterrupted(game)
    }

    @Test
    fun emptyShoeAfterPlayerReachesTwentyOneStillInterruptsWithoutAWin() {
        val game = gameWith(Rank.SEVEN, Rank.FIVE, Rank.SEVEN, Rank.SIX, Rank.SEVEN)
        game.startRound()

        assertFalse(game.hit())

        assertEquals(21, game.playerValue)
        assertInterrupted(game)
    }

    @Test
    fun actionsAfterInterruptionDoNotDrawAnyMoreCards() {
        var drawCount = 0
        val game = BlackjackGame {
            drawCount++
            null
        }
        game.startRound()

        assertFalse(game.hit())
        assertFalse(game.stand())

        assertEquals(1, drawCount)
        assertInterrupted(game)
    }

    @Test
    fun startingAgainWithAnEmptySourceDoesNotCreateCards() {
        val game = gameWith(Rank.TEN)
        game.startRound()

        assertFalse(game.startRound())

        assertTrue(game.playerCards.isEmpty())
        assertTrue(game.dealerCards.isEmpty())
        assertInterrupted(game)
    }

    @Test
    fun aReturnedCardListRemainsASnapshotWhenTheGameContinues() {
        val game = gameWith(Rank.FIVE, Rank.TEN, Rank.SIX, Rank.EIGHT, Rank.TWO)
        game.startRound()
        val cards = game.playerCards

        assertTrue(game.hit())

        assertEquals(2, cards.size)
        assertEquals(3, game.playerCards.size)
        assertEquals(13, game.playerValue)
    }

    private fun gameWith(vararg ranks: Rank): BlackjackGame {
        val cards = ranks.mapIndexed { index, rank ->
            Card(rank, Suit.entries[index % Suit.entries.size])
        }.toMutableList()

        // Une fonction sans paramètre fournit la prochaine carte, ou null.
        return BlackjackGame {
            if (cards.isEmpty()) null else cards.removeAt(0)
        }
    }

    private fun assertFinished(game: BlackjackGame, expectedResult: GameResult) {
        assertEquals(GamePhase.FINISHED, game.phase)
        assertEquals(expectedResult, game.result)
        assertFalse(game.canHit)
        assertFalse(game.canStand)
    }

    private fun assertInterrupted(game: BlackjackGame) {
        assertEquals(GamePhase.INTERRUPTED, game.phase)
        assertNull(game.result)
        assertFalse(game.canHit)
        assertFalse(game.canStand)
    }
}
