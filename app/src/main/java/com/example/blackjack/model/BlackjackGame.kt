package com.example.blackjack.model

enum class GamePhase {
    READY,
    PLAYER_TURN,
    DEALER_TURN,
    FINISHED,
    INTERRUPTED
}

enum class GameResult {
    WIN,
    LOSS,
    DRAW
}

class BlackjackGame internal constructor(private val drawCard: () -> Card?) {
    constructor(shoe: Shoe) : this({ shoe.drawCard() })

    private var playerHand = Hand()
    private var dealerHand = Hand()

    private val distributedCards = mutableListOf<DealtCard>()

    val dealtCards: List<DealtCard>
        get() = distributedCards.toList()

    val playerCards: List<Card>
        get() = playerHand.cards

    val dealerCards: List<Card>
        get() = dealerHand.cards

    val playerValue: Int
        get() = playerHand.calculateValue()

    val dealerValue: Int
        get() = dealerHand.calculateValue()

    var phase: GamePhase = GamePhase.READY
        private set

    var result: GameResult? = null
        private set

    var playerHasBlackjack: Boolean = false
        private set

    var dealerHasBlackjack: Boolean = false
        private set

    val canHit: Boolean
        get() = phase == GamePhase.PLAYER_TURN

    val canStand: Boolean
        get() = phase == GamePhase.PLAYER_TURN

    fun startRound(): Boolean {
        if (phase == GamePhase.PLAYER_TURN || phase == GamePhase.DEALER_TURN) {
            return false
        }

        playerHand = Hand()
        dealerHand = Hand()
        distributedCards.clear()
        result = null
        playerHasBlackjack = false
        dealerHasBlackjack = false
        phase = GamePhase.READY

        // Distribution alternée des cartes : joueur, croupier, joueur, croupier.
        repeat(2) {
            if (!dealCardTo(playerHand) || !dealCardTo(dealerHand)) {
                return false
            }
        }

        playerHasBlackjack = playerHand.hasBlackjackValue()
        dealerHasBlackjack = dealerHand.hasBlackjackValue()

        when {
            playerHasBlackjack && dealerHasBlackjack -> finishRound(GameResult.DRAW)
            playerHasBlackjack -> finishRound(GameResult.WIN)
            dealerHasBlackjack -> finishRound(GameResult.LOSS)
            else -> phase = GamePhase.PLAYER_TURN
        }

        return true
    }

    fun hit(): Boolean {
        if (!canHit || !dealCardTo(playerHand)) {
            return false
        }

        when {
            playerHand.isBust() -> finishRound(GameResult.LOSS)
            playerValue == 21 -> playDealerTurn()
        }

        return phase != GamePhase.INTERRUPTED
    }

    fun stand(): Boolean {
        if (!canStand) {
            return false
        }

        playDealerTurn()
        return phase != GamePhase.INTERRUPTED
    }

    fun calculateChipChange(bet: Int): Double {
        require(bet > 0) { "The bet must be positive." }
        check(phase == GamePhase.FINISHED) {
            "The chip change can only be calculated for a finished game."
        }
        val finishedResult = checkNotNull(result) {
            "A finished game must have a result."
        }

        // Variation nette : la mise rendue n'est pas un gain supplémentaire.
        return when (finishedResult) {
            GameResult.LOSS -> -bet.toDouble()
            GameResult.DRAW -> 0.0
            GameResult.WIN -> if (playerHasBlackjack) bet * 1.5 else bet.toDouble()
        }
    }

    private fun playDealerTurn() {
        phase = GamePhase.DEALER_TURN

        // Le croupier s'arrête sur tout total de 17 ou plus, même As + 6.
        while (dealerValue < 17) {
            if (!dealCardTo(dealerHand)) {
                return
            }
        }

        determineResult()
    }

    private fun determineResult() {
        val roundResult = when {
            dealerHand.isBust() -> GameResult.WIN
            playerValue > dealerValue -> GameResult.WIN
            playerValue < dealerValue -> GameResult.LOSS
            else -> GameResult.DRAW
        }

        finishRound(roundResult)
    }

    private fun dealCardTo(hand: Hand): Boolean {
        val card = drawCard()
        if (card == null) {
            // Une interruption n'est ni une égalité ni une défaite.
            phase = GamePhase.INTERRUPTED
            result = null
            return false
        }

        hand.addCard(card)
        distributedCards.add(
            DealtCard(
                card = card,
                handNumber = if (hand === playerHand) 1 else null,
                drawOrder = distributedCards.size + 1
            )
        )
        return true
    }

    private fun finishRound(roundResult: GameResult) {
        result = roundResult
        phase = GamePhase.FINISHED
    }
}
