package com.example.blackjack.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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

    @Test
    fun distributionHistoryStartsEmpty() {
        assertTrue(gameWith().dealtCards.isEmpty())
    }

    @Test
    fun distributionHistoryKeepsInitialOrderAndRecipients() {
        val game = gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX)
        game.startRound()

        assertEquals(listOf(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX),
            game.dealtCards.map { it.card.rank })
        assertEquals(Suit.entries.toList(), game.dealtCards.map { it.card.suit })
        assertEquals(listOf<Int?>(1, null, 1, null), game.dealtCards.map { it.handNumber })
        assertEquals(listOf(1, 2, 3, 4), game.dealtCards.map { it.drawOrder })
    }

    @Test
    fun distributionHistoryContinuesThroughHitAndSeveralDealerDraws() {
        val ranks = listOf(Rank.FIVE, Rank.TWO, Rank.SIX, Rank.THREE,
            Rank.FOUR, Rank.FOUR, Rank.FIVE, Rank.THREE)
        val game = gameWith(*ranks.toTypedArray())
        game.startRound()
        game.hit()
        game.stand()

        assertFinished(game, GameResult.LOSS)
        assertEquals(ranks, game.dealtCards.map { it.card.rank })
        assertEquals(listOf<Int?>(1, null, 1, null, 1, null, null, null),
            game.dealtCards.map { it.handNumber })
        assertEquals((1..8).toList(), game.dealtCards.map { it.drawOrder })
    }

    @Test
    fun reachingTwentyOneRecordsTheHitBeforeTheAutomaticDealerDraw() {
        val game = gameWith(Rank.SEVEN, Rank.TEN, Rank.SEVEN, Rank.SIX, Rank.SEVEN, Rank.FIVE)
        game.startRound()
        game.hit()

        assertEquals(listOf<Int?>(1, null, 1, null, 1, null),
            game.dealtCards.map { it.handNumber })
        assertEquals(listOf(Rank.SEVEN, Rank.FIVE), game.dealtCards.takeLast(2).map { it.card.rank })
        assertEquals(listOf(1, 2, 3, 4, 5, 6), game.dealtCards.map { it.drawOrder })
    }

    @Test
    fun newRoundResetsDistributionWithoutChangingThePreviousSnapshot() {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE,
            Rank.FIVE, Rank.TEN, Rank.SIX, Rank.EIGHT)
        game.startRound()
        val previousCards = game.dealtCards
        game.startRound()

        assertEquals(listOf(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE),
            previousCards.map { it.card.rank })
        assertEquals(listOf(Rank.FIVE, Rank.TEN, Rank.SIX, Rank.EIGHT),
            game.dealtCards.map { it.card.rank })
        assertEquals(listOf(1, 2, 3, 4), game.dealtCards.map { it.drawOrder })
    }

    @Test
    fun returnedDistributionIsASnapshotWhileThePlayerContinues() {
        val game = gameWith(Rank.FIVE, Rank.TEN, Rank.SIX, Rank.EIGHT, Rank.TWO)
        game.startRound()
        val previousCards = game.dealtCards
        game.hit()

        assertEquals(4, previousCards.size)
        assertEquals(5, game.dealtCards.size)
        assertEquals(DealtCard(Card(Rank.TWO, Suit.CLUBS), 1, 5), game.dealtCards.last())
    }

    @Test
    fun refusedActionsLeaveDistributionUnchanged() {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        game.hit()
        game.stand()
        assertTrue(game.dealtCards.isEmpty())
        game.startRound()
        val finishedCards = game.dealtCards
        game.hit()
        game.stand()
        assertEquals(finishedCards, game.dealtCards)

        val activeGame = gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX)
        activeGame.startRound()
        val activeCards = activeGame.dealtCards
        assertFalse(activeGame.startRound())
        assertEquals(activeCards, activeGame.dealtCards)
    }

    @Test
    fun interruptedInitialDistributionRecordsOnlyCardsActuallyDrawn() {
        val ranks = listOf(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX)
        for (count in 0..3) {
            val game = gameWith(*ranks.take(count).toTypedArray())
            game.startRound()
            assertEquals(ranks.take(count), game.dealtCards.map { it.card.rank })
            assertEquals((1..count).toList(), game.dealtCards.map { it.drawOrder })
        }
    }

    @Test
    fun emptySourceOnHitDoesNotAddAPhantomDistributionEntry() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.NINE)
        game.startRound()
        val cards = game.dealtCards
        game.hit()
        assertInterrupted(game)
        assertEquals(cards, game.dealtCards)
    }

    @Test
    fun interruptedDealerTurnKeepsSuccessfulDrawsOnly() {
        val game = gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX, Rank.TWO)
        game.startRound()
        game.stand()
        val cards = game.dealtCards
        assertInterrupted(game)
        assertEquals(5, cards.size)
        assertEquals(DealtCard(Card(Rank.TWO, Suit.CLUBS), null, 5), cards.last())
        game.hit()
        game.stand()
        assertEquals(cards, game.dealtCards)
        game.startRound()
        assertTrue(game.dealtCards.isEmpty())
    }

    @Test
    fun ordinaryWinPaysOneBetWithoutChangingTheGame() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.NINE, Rank.EIGHT)
        game.startRound()
        game.stand()
        val cards = game.dealtCards
        assertEquals(10.0, game.calculateChipChange(10), 0.0)
        assertEquals(10.0, game.calculateChipChange(10), 0.0)
        assertEquals(cards, game.dealtCards)
        assertFinished(game, GameResult.WIN)
    }

    @Test
    fun naturalBlackjackPaysTwoBetsAsRequiredByTheAssignment() {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        game.startRound()
        for ((bet, expected) in listOf(1 to 2.0, 5 to 10.0, 10 to 20.0, 100 to 200.0)) {
            assertEquals(expected, game.calculateChipChange(bet), 0.0)
        }
    }

    @Test
    fun twentyOneInThreeCardsPaysAnOrdinaryWin() {
        val game = gameWith(Rank.SEVEN, Rank.TEN, Rank.SEVEN, Rank.SEVEN, Rank.SEVEN)
        game.startRound()
        game.hit()
        assertEquals(10.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun dealerBustPaysAnOrdinaryWin() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.SIX, Rank.KING)
        game.startRound()
        game.stand()
        assertEquals(10.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun lossCostsTheBetForHigherDealerTotalPlayerBustAndDealerBlackjack() {
        val higherDealer = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.NINE)
        higherDealer.startRound()
        higherDealer.stand()
        val playerBust = gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX, Rank.THREE)
        playerBust.startRound()
        playerBust.hit()
        val dealerBlackjack = gameWith(Rank.TEN, Rank.ACE, Rank.NINE, Rank.QUEEN)
        dealerBlackjack.startRound()
        for (game in listOf(higherDealer, playerBust, dealerBlackjack)) {
            assertEquals(-10.0, game.calculateChipChange(10), 0.0)
        }
    }

    @Test
    fun equalTotalsAndTwoNaturalBlackjacksHaveNoNetChipChange() {
        val equalTotals = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.EIGHT)
        equalTotals.startRound()
        equalTotals.stand()
        val twoBlackjacks = gameWith(Rank.ACE, Rank.ACE, Rank.TEN, Rank.KING)
        twoBlackjacks.startRound()
        for (game in listOf(equalTotals, twoBlackjacks)) {
            assertEquals(0.0, game.calculateChipChange(10), 0.0)
        }
    }

    @Test
    fun chipChangeRejectsZeroAndNegativeBets() {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        game.startRound()
        for (bet in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { game.calculateChipChange(bet) }
        }
    }

    @Test
    fun chipChangeRejectsReadyActiveAndInterruptedGames() {
        val ready = gameWith()
        val active = gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.NINE)
        active.startRound()
        val interrupted = gameWith(Rank.TEN)
        interrupted.startRound()
        for (game in listOf(ready, active, interrupted)) {
            assertThrows(IllegalStateException::class.java) { game.calculateChipChange(10) }
        }
    }

    @Test
    fun betValidationAcceptsTheLimitsWhenTheyAreAffordable() {
        val game = gameWith()
        assertTrue(game.validateBet(1, 1.0))
        assertTrue(game.validateBet(100, 100.0))
        assertTrue(game.validateBet(50, 1_000.0))
    }

    @Test
    fun betValidationRejectsAmountsOutsideOneToOneHundred() {
        val game = gameWith()
        for (bet in listOf(-1, 0, 101, Int.MAX_VALUE)) {
            assertFalse(game.validateBet(bet, 1_000.0))
        }
    }

    @Test
    fun betValidationUsesTheAvailableBalanceIncludingHalfChips() {
        val game = gameWith()
        assertTrue(game.validateBet(1, 1.5))
        assertFalse(game.validateBet(2, 1.5))
        assertFalse(game.validateBet(1, 0.5))
        assertFalse(game.validateBet(1, 0.0))
    }

    @Test
    fun betValidationRejectsInvalidBalances() {
        val game = gameWith()
        for (chips in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(game.validateBet(1, chips))
        }
    }

    @Test
    fun splitAcceptsPairsOfTheSameRankIncludingAcesAndFigures() {
        for (rank in Rank.entries) {
            val game = gameWith(rank, Rank.TEN, rank, Rank.SEVEN)
            assertTrue(game.startRound())
            assertTrue(game.canSplit(10, 20.0))
        }
    }

    @Test
    fun splitRejectsDifferentRanksEvenWhenBothCardsAreWorthTen() {
        for (otherRank in listOf(Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.NINE)) {
            val game = gameWith(Rank.KING, Rank.TEN, otherRank, Rank.SEVEN)
            game.startRound()
            assertFalse(game.canSplit(10, 1_000.0))
        }
    }

    @Test
    fun splitRequiresEnoughChipsForBothBetsWithoutLimitingTheirCombinedTotalToOneHundred() {
        val game = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN)
        game.startRound()
        assertFalse(game.canSplit(25, 49.5))
        assertTrue(game.canSplit(25, 50.0))
        assertTrue(game.canSplit(100, 200.0))
        for (bet in listOf(-1, 0, 101, Int.MAX_VALUE)) {
            assertFalse(game.canSplit(bet, 1_000.0))
        }
        for (chips in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFalse(game.canSplit(10, chips))
        }
    }

    @Test
    fun splitIsUnavailableBeforeTheRoundAfterHittingAfterFinishingAndAfterInterruption() {
        val ready = gameWith()
        assertFalse(ready.canSplit(10, 1_000.0))
        val hit = gameWith(Rank.FIVE, Rank.TEN, Rank.FIVE, Rank.SEVEN, Rank.TWO)
        hit.startRound()
        hit.hit()
        assertFalse(hit.canSplit(10, 1_000.0))
        val finished = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN)
        finished.startRound()
        finished.stand()
        assertFalse(finished.canSplit(10, 1_000.0))
        val interrupted = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT)
        interrupted.startRound()
        assertFalse(interrupted.canSplit(10, 1_000.0))
    }

    @Test
    fun checkingSplitDoesNotChangeCardsPhaseDistributionOrResult() {
        val game = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN)
        game.startRound()
        val cards = game.playerCards
        val dealer = game.dealerCards
        val dealt = game.dealtCards
        repeat(2) { assertTrue(game.canSplit(10, 20.0)) }
        assertEquals(cards, game.playerCards)
        assertEquals(dealer, game.dealerCards)
        assertEquals(dealt, game.dealtCards)
        assertEquals(GamePhase.PLAYER_TURN, game.phase)
        assertNull(game.result)
    }

    @Test
    fun splitSeparatesTheOriginalCardsAndDealsOneNewCardToEachHand() {
        val game = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN, Rank.TWO, Rank.THREE)
        game.startRound()
        val originalCards = game.playerCards
        val originalDistribution = game.dealtCards

        assertTrue(game.split(10, 20.0))

        assertTrue(game.hasSplit)
        assertEquals(0, game.activeHandIndex)
        assertEquals(listOf(1, 2), game.playerHands.map { it.handNumber })
        assertEquals(listOf(originalCards[0], game.dealtCards[4].card), game.playerHands[0].cards)
        assertEquals(listOf(originalCards[1], game.dealtCards[5].card), game.playerHands[1].cards)
        assertEquals(listOf(10, 11), game.playerHands.map { it.total })
        assertEquals(listOf(1, null, 2, null, 1, 2), game.dealtCards.map { it.handNumber })
        assertEquals((1..6).toList(), game.dealtCards.map { it.drawOrder })
        assertEquals(originalDistribution.map { it.card }, game.dealtCards.take(4).map { it.card })
        assertEquals(1, originalDistribution[2].handNumber)
        assertFalse(game.canSplit(10, 1000.0))
        assertTrue(game.canHit)
        assertTrue(game.canStand)
    }

    @Test
    fun identicalCardsFromDifferentDecksAreAssignedToDifferentHands() {
        val card = Card(Rank.EIGHT, Suit.HEARTS)
        val cards = ArrayDeque(listOf(card, Card(Rank.TEN, Suit.CLUBS), card,
            Card(Rank.SEVEN, Suit.SPADES), Card(Rank.TWO, Suit.CLUBS), Card(Rank.THREE, Suit.CLUBS)))
        val game = BlackjackGame { cards.removeFirstOrNull() }
        game.startRound()
        game.split(10, 20.0)
        assertEquals(1, game.dealtCards[0].handNumber)
        assertEquals(2, game.dealtCards[2].handNumber)
        assertEquals(card, game.playerHands[0].cards.first())
        assertEquals(card, game.playerHands[1].cards.first())
    }

    @Test
    fun rejectedSplitDoesNotDrawCardsOrChangeTheRound() {
        for (bet in listOf(0, 10, 101)) {
            val game = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN, Rank.TWO, Rank.THREE)
            game.startRound()
            val cards = game.dealtCards
            assertFalse(game.split(bet, 19.0))
            assertFalse(game.hasSplit)
            assertEquals(cards, game.dealtCards)
            assertEquals(16, game.playerValue)
            assertEquals(GamePhase.PLAYER_TURN, game.phase)
        }
        val differentRanks = gameWith(Rank.KING, Rank.TEN, Rank.QUEEN, Rank.SEVEN)
        differentRanks.startRound()
        assertFalse(differentRanks.split(10, 1000.0))
    }

    @Test
    fun splitHandsPlaySuccessivelyAndTheDealerDrawsOnlyAfterBothStop() {
        val game = gameWith(Rank.EIGHT, Rank.FIVE, Rank.EIGHT, Rank.SIX,
            Rank.TWO, Rank.THREE, Rank.NINE, Rank.EIGHT, Rank.SEVEN)
        game.startRound()
        game.split(10, 20.0)
        assertTrue(game.hit()) // Première main : 19.
        assertEquals(19, game.playerValue)
        assertEquals(11, game.dealerValue)
        assertTrue(game.stand())
        assertEquals(1, game.activeHandIndex)
        assertEquals(11, game.playerValue)
        assertEquals(2, game.dealerCards.size)
        assertNull(game.result)
        assertTrue(game.hit()) // Seconde main : 19.
        assertTrue(game.stand()) // Le croupier tire maintenant son 7 : 18.
        assertFinished(game, GameResult.WIN)
        assertEquals(listOf(GameResult.WIN, GameResult.WIN), game.playerHands.map { it.result })
        assertEquals(listOf(19, 19), game.playerHands.map { it.total })
        assertEquals(3, game.dealerCards.size)
        assertEquals(20.0, game.calculateChipChange(10), 0.0)
        assertEquals(listOf(1, null, 2, null, 1, 2, 1, 2, null), game.dealtCards.map { it.handNumber })
    }

    @Test
    fun bustingTheFirstHandMovesToTheSecondWithoutEndingTheRound() {
        val game = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN,
            Rank.TEN, Rank.TEN, Rank.KING)
        game.startRound()
        game.split(10, 20.0)
        assertTrue(game.hit())
        assertEquals(1, game.activeHandIndex)
        assertEquals(GameResult.LOSS, game.playerHands[0].result)
        assertEquals(GamePhase.PLAYER_TURN, game.phase)
        assertTrue(game.stand())
        assertFinished(game, GameResult.MIXED)
        assertEquals(-10.0, game.calculateHandChipChange(1, 10), 0.0)
        assertEquals(10.0, game.calculateHandChipChange(2, 10), 0.0)
        assertEquals(0.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun twoBustedHandsLoseBothBetsWithoutDrawingForTheDealer() {
        val game = gameWith(Rank.EIGHT, Rank.FIVE, Rank.EIGHT, Rank.SIX,
            Rank.TEN, Rank.NINE, Rank.KING, Rank.KING)
        game.startRound()
        game.split(10, 20.0)
        game.hit()
        game.hit()
        assertFinished(game, GameResult.LOSS)
        assertEquals(2, game.dealerCards.size)
        assertEquals(-20.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun splitTwentyOneAutomaticallyMovesToTheOtherHandAndPaysAnOrdinaryWin() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.TEN, Rank.SEVEN, Rank.ACE, Rank.NINE)
        game.startRound()
        game.split(10, 20.0)
        assertEquals(1, game.activeHandIndex)
        assertFalse(game.playerHands[0].hasBlackjack)
        assertFalse(game.playerHasBlackjack)
        assertFalse(game.playerHands[1].hasBlackjack)
        game.stand()
        assertFinished(game, GameResult.WIN)
        assertEquals(10.0, game.calculateHandChipChange(1, 10), 0.0)
        assertEquals(10.0, game.calculateHandChipChange(2, 10), 0.0)
        assertEquals(20.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun aSecondHandAtTwentyOneIsSkippedAfterTheFirstHandStops() {
        val game = gameWith(Rank.TEN, Rank.TEN, Rank.TEN, Rank.SEVEN, Rank.NINE, Rank.ACE)
        game.startRound()
        game.split(5, 10.0)
        assertEquals(0, game.activeHandIndex)
        game.stand()
        assertFinished(game, GameResult.WIN)
        assertFalse(game.playerHasBlackjack)
        assertEquals(10.0, game.calculateChipChange(5), 0.0)
    }

    @Test
    fun twoSplitTwentyOnesWaitForTheDealerAndCanBothDraw() {
        val game = gameWith(Rank.TEN, Rank.FIVE, Rank.TEN, Rank.SIX, Rank.ACE, Rank.ACE, Rank.TEN)
        game.startRound()
        assertTrue(game.split(100, 200.0))
        assertFinished(game, GameResult.DRAW)
        assertEquals(21, game.dealerValue)
        assertEquals(0.0, game.calculateChipChange(100), 0.0)
        assertEquals(7, game.dealtCards.size)
        assertTrue(game.playerHands.none { it.hasBlackjack })
    }

    @Test
    fun twentyOneInThreeCardsAfterSplitAdvancesWithoutTheNaturalBonus() {
        val game = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN,
            Rank.SIX, Rank.NINE, Rank.SEVEN)
        game.startRound()
        game.split(10, 20.0)
        game.hit()
        assertEquals(21, game.playerHands[0].total)
        assertFalse(game.playerHands[0].hasBlackjack)
        assertEquals(1, game.activeHandIndex)
        game.stand()
        assertFinished(game, GameResult.MIXED)
        assertEquals(10.0, game.calculateHandChipChange(1, 10), 0.0)
        assertEquals(10.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun splitAcesReceiveOnlyOneCardEachAndFinishAutomatically() {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.ACE, Rank.SEVEN, Rank.TWO, Rank.THREE)
        game.startRound()
        assertTrue(game.split(10, 20.0))
        assertFinished(game, GameResult.LOSS)
        assertEquals(listOf(13, 14), game.playerHands.map { it.total })
        assertEquals(listOf(2, 2), game.playerHands.map { it.cards.size })
        val cards = game.dealtCards
        assertFalse(game.hit())
        assertFalse(game.stand())
        assertEquals(cards, game.dealtCards)
        assertEquals(-20.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun splitAcesAtTwentyOneStillDrawAgainstDealerTwentyOne() {
        val game = gameWith(Rank.ACE, Rank.FIVE, Rank.ACE, Rank.SIX, Rank.KING, Rank.QUEEN, Rank.TEN)
        game.startRound()
        assertTrue(game.split(10, 20.0))
        assertFinished(game, GameResult.DRAW)
        assertEquals(listOf(21, 21), game.playerHands.map { it.total })
        assertTrue(game.playerHands.none { it.hasBlackjack })
        assertEquals(0.0, game.calculateChipChange(10), 0.0)
        assertFalse(game.canHit)
    }

    @Test
    fun splitTwentyOnesCannotBePaidIfTheDealerRunsOutOfCards() {
        val game = gameWith(Rank.TEN, Rank.FIVE, Rank.TEN, Rank.SIX, Rank.ACE, Rank.ACE)
        game.startRound()
        assertFalse(game.split(10, 20.0))
        assertInterrupted(game)
        assertThrows(IllegalStateException::class.java) { game.calculateChipChange(10) }
    }

    @Test
    fun aSplitAceAndTenGetsOnlyAnOrdinaryPayment() {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.ACE, Rank.EIGHT, Rank.KING, Rank.SEVEN)
        game.startRound()
        game.split(10, 20.0)
        assertFinished(game, GameResult.MIXED)
        assertEquals(listOf(GameResult.WIN, GameResult.DRAW), game.playerHands.map { it.result })
        assertTrue(game.playerHands.none { it.hasBlackjack })
        assertEquals(10.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun splitTwentyOneDrawsAgainstDealerTwentyOneInThreeCards() {
        val game = gameWith(Rank.TEN, Rank.FIVE, Rank.TEN, Rank.SIX, Rank.ACE, Rank.NINE, Rank.TEN)
        game.startRound()
        game.split(10, 20.0)
        game.stand()
        assertEquals(21, game.dealerValue)
        assertFinished(game, GameResult.MIXED)
        assertEquals(listOf(GameResult.DRAW, GameResult.LOSS), game.playerHands.map { it.result })
        assertEquals(-10.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun drawnAndLosingMixedHandsCanHaveANegativeNetPayment() {
        val game = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.EIGHT, Rank.TEN, Rank.NINE)
        game.startRound()
        game.split(10, 20.0)
        game.stand()
        game.stand()
        assertFinished(game, GameResult.MIXED)
        assertEquals(listOf(GameResult.DRAW, GameResult.LOSS), game.playerHands.map { it.result })
        assertEquals(-10.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun bothDrawnHandsHaveADrawSummaryAndNoPayment() {
        val game = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN, Rank.NINE, Rank.NINE)
        game.startRound()
        game.split(10, 20.0)
        game.stand()
        game.stand()
        assertFinished(game, GameResult.DRAW)
        assertEquals(0.0, game.calculateChipChange(10), 0.0)
    }

    @Test
    fun aNewPairCreatedBySplitCannotBeSplitAgain() {
        val game = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN, Rank.EIGHT, Rank.EIGHT)
        game.startRound()
        game.split(10, 1000.0)
        val cards = game.dealtCards
        assertFalse(game.canSplit(10, 1000.0))
        assertFalse(game.split(10, 1000.0))
        game.stand()
        assertFalse(game.split(10, 1000.0))
        assertEquals(cards, game.dealtCards)
        assertEquals(2, game.playerHands.size)
    }

    @Test
    fun runningOutDuringEitherSplitDrawInterruptsWithoutAPayment() {
        for (extraCards in listOf(emptyList(), listOf(Rank.TWO))) {
            val ranks = listOf(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN) + extraCards
            val game = gameWith(*ranks.toTypedArray())
            game.startRound()
            assertFalse(game.split(10, 20.0))
            assertInterrupted(game)
            assertEquals(ranks, game.dealtCards.map { it.card.rank })
            assertEquals(listOf(1, null, 2, null) + extraCards.map { 1 }, game.dealtCards.map { it.handNumber })
            assertThrows(IllegalStateException::class.java) { game.calculateChipChange(10) }
        }
    }

    @Test
    fun emptyShoeDuringTheSplitDealerTurnDoesNotSettlePartialResults() {
        val game = gameWith(Rank.EIGHT, Rank.FIVE, Rank.EIGHT, Rank.SIX, Rank.TWO, Rank.THREE)
        game.startRound()
        game.split(10, 20.0)
        game.stand()
        assertFalse(game.stand())
        assertInterrupted(game)
        assertThrows(IllegalStateException::class.java) { game.calculateHandChipChange(1, 10) }
    }

    @Test
    fun startingANewRoundClearsBothSplitHandsAndTheirResults() {
        val game = gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN, Rank.NINE, Rank.NINE,
            Rank.FIVE, Rank.TEN, Rank.SIX, Rank.EIGHT)
        game.startRound()
        game.split(10, 20.0)
        game.stand()
        game.stand()
        assertTrue(game.startRound())
        assertFalse(game.hasSplit)
        assertEquals(0, game.activeHandIndex)
        assertEquals(1, game.playerHands.size)
        assertNull(game.playerHands.single().result)
        assertEquals(4, game.dealtCards.size)
        assertEquals(listOf(1, null, 1, null), game.dealtCards.map { it.handNumber })
    }

    @Test
    fun perHandPaymentRejectsUnknownHandsAndInvalidBets() {
        val game = gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
        game.startRound()
        assertThrows(IllegalArgumentException::class.java) { game.calculateHandChipChange(0, 10) }
        assertThrows(IllegalArgumentException::class.java) { game.calculateHandChipChange(2, 10) }
        assertThrows(IllegalArgumentException::class.java) { game.calculateHandChipChange(1, 0) }
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
