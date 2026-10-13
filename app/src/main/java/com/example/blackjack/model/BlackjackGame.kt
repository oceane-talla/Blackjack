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
    DRAW,
    // Résumé d'une partie dont les deux mains ont des résultats différents.
    MIXED
}

// Copie en lecture seule : l'extérieur peut consulter chaque main sans modifier Hand.
data class PlayerHandState(
    val handNumber: Int,
    val cards: List<Card>,
    val total: Int,
    val hasBlackjack: Boolean,
    val result: GameResult?
)

class BlackjackGame internal constructor(private val drawCard: () -> Card?) {
    constructor(shoe: Shoe) : this({ shoe.drawCard() })

    companion object {
        // Reconstitue le modèle sans distribuer de carte et sans dépendre de Room.
        fun restore(
            shoe: Shoe,
            savedCards: List<DealtCard>,
            handResults: List<GameResult?>,
            activeHandIndex: Int,
            phase: GamePhase,
            result: GameResult?
        ): BlackjackGame {
            require(handResults.size in 1..2 && activeHandIndex in handResults.indices)
            require(phase != GamePhase.READY)
            require(savedCards.map { it.drawOrder } == (1..savedCards.size).toList()) {
                "Saved cards must follow their distribution order."
            }
            require(savedCards.all { it.handNumber == null || it.handNumber in 1..handResults.size })
            require(handResults.none { it == GameResult.MIXED })
            if (phase == GamePhase.FINISHED) {
                require(handResults.all { it != null })
                val results = handResults.distinct()
                require(result == if (results.size == 1) results.single() else GameResult.MIXED)
            } else {
                require(result == null)
            }

            val game = BlackjackGame(shoe)
            game.hands.clear()
            handResults.indices.forEach { index ->
                game.hands.add(Hand(savedCards.filter { it.handNumber == index + 1 }.map { it.card }))
            }
            game.handResults.clear()
            game.handResults.addAll(handResults)
            game.dealerHand = Hand(savedCards.filter { it.handNumber == null }.map { it.card })
            game.distributedCards.addAll(savedCards)
            game.activateHand(activeHandIndex)
            game.dealerHasBlackjack = game.dealerHand.hasBlackjackValue()
            game.phase = phase
            game.result = result
            return game
        }
    }

    private val hands = mutableListOf(Hand())
    private val handResults = mutableListOf<GameResult?>(null)
    private val playerHand: Hand
        get() = hands[activeHandIndex]
    private var dealerHand = Hand()

    // Index Kotlin : 0 pour la première main, 1 pour la seconde.
    var activeHandIndex: Int = 0
        private set

    val hasSplit: Boolean
        get() = hands.size == 2

    val playerHands: List<PlayerHandState>
        get() = hands.mapIndexed { index, hand ->
            PlayerHandState(index + 1, hand.cards, hand.calculateValue(),
                isNaturalBlackjack(hand), handResults[index])
        }

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

        hands.clear()
        hands.add(Hand())
        handResults.clear()
        handResults.add(null)
        activeHandIndex = 0
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
            playerHand.isBust() || playerValue == 21 -> finishActiveHand()
        }

        return phase != GamePhase.INTERRUPTED
    }

    fun stand(): Boolean {
        if (!canStand) {
            return false
        }

        finishActiveHand()
        return phase != GamePhase.INTERRUPTED
    }

    fun validateBet(amount: Int, availableChips: Double): Boolean =
        amount in 1..100 && availableChips.isFinite() && amount <= availableChips

    // Vérifie seulement l'admissibilité : cette fonction ne sépare aucune carte.
    // availableChips est le solde avant règlement, puisque la mise n'est pas débitée au départ.
    fun canSplit(bet: Int, availableChips: Double): Boolean {
        val cards = playerHand.cards
        return phase == GamePhase.PLAYER_TURN && !hasSplit &&
            cards.size == 2 &&
            cards[0].rank == cards[1].rank &&
            validateBet(bet, availableChips) &&
            availableChips >= bet * 2.0
    }

    fun split(bet: Int, availableChips: Double): Boolean {
        if (!canSplit(bet, availableChips)) return false

        val pair = playerHand.cards
        hands.clear()
        hands.add(Hand(listOf(pair[0])))
        hands.add(Hand(listOf(pair[1])))
        handResults.clear()
        handResults.addAll(listOf(null, null))

        // La deuxième carte du joueur change de main, sans être repigée.
        // Sa position dans la distribution globale reste identique.
        val secondCardIndex = distributedCards.indexOfLast { it.handNumber == 1 }
        distributedCards[secondCardIndex] = distributedCards[secondCardIndex].copy(handNumber = 2)

        // Une nouvelle carte pour chaque main avant de commencer leurs tours.
        if (!dealCardTo(hands[0]) || !dealCardTo(hands[1])) return false
        activateHand(0)

        if (pair[0].rank == Rank.ACE) {
            // Choix B : une seule carte par As, puis aucun Tirer/Rester.
            completePlayerTurn()
        } else if (playerValue == 21) {
            finishActiveHand()
        }
        return phase != GamePhase.INTERRUPTED
    }

    fun calculateChipChange(bet: Int): Double {
        require(bet > 0) { "The bet must be positive." }
        check(phase == GamePhase.FINISHED) {
            "The chip change can only be calculated for a finished game."
        }
        // Chaque main a la même mise; MIXED ne dit pas à lui seul combien payer.
        return (1..hands.size).sumOf { calculateHandChipChange(it, bet) }
    }

    // handNumber suit Room et DealtCard : 1 ou 2, contrairement à l'index Kotlin.
    fun calculateHandChipChange(handNumber: Int, bet: Int): Double {
        require(bet > 0) { "The bet must be positive." }
        check(phase == GamePhase.FINISHED) {
            "The chip change can only be calculated for a finished game."
        }
        require(handNumber in 1..hands.size) { "Unknown player hand." }
        val index = handNumber - 1
        val finishedResult = checkNotNull(handResults[index])

        // Variation nette : la mise rendue n'est pas un gain supplémentaire.
        return when (finishedResult) {
            GameResult.LOSS -> -bet.toDouble()
            GameResult.DRAW -> 0.0
            // L'énoncé du projet impose un gain net de deux fois la mise.
            // Après Split, un 21 reste ordinaire : le bonus est réservé à la main initiale.
            GameResult.WIN -> if (isNaturalBlackjack(hands[index])) bet * 2.0 else bet.toDouble()
            GameResult.MIXED -> error("MIXED describes a whole round, never one hand.")
        }
    }

    private fun isNaturalBlackjack(hand: Hand): Boolean =
        !hasSplit && hand.hasBlackjackValue()

    private fun activateHand(index: Int) {
        activeHandIndex = index
        playerHasBlackjack = isNaturalBlackjack(playerHand)
    }

    private fun finishActiveHand() {
        if (playerHand.isBust()) handResults[activeHandIndex] = GameResult.LOSS
        if (hasSplit && activeHandIndex == 0) {
            activateHand(1)
            // Un 21 s'arrête automatiquement, comme dans la partie standard.
            if (playerValue == 21) finishActiveHand()
        } else {
            completePlayerTurn()
        }
    }

    private fun completePlayerTurn() {
        // Un 21 après Split attend le croupier : il peut encore faire égalité.
        // Seules des mains toutes dépassées permettent de sauter ce tour.
        if (hands.all { it.isBust() }) {
            hands.forEachIndexed { index, _ ->
                handResults[index] = GameResult.LOSS
            }
            finishRoundFromHands()
        } else {
            playDealerTurn()
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
        hands.forEachIndexed { index, hand ->
            handResults[index] = when {
                hand.isBust() -> GameResult.LOSS
                dealerHand.isBust() -> GameResult.WIN
                hand.calculateValue() > dealerValue -> GameResult.WIN
                hand.calculateValue() < dealerValue -> GameResult.LOSS
                else -> GameResult.DRAW
            }
        }
        finishRoundFromHands()
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
                handNumber = if (hand === dealerHand) null else hands.indexOf(hand) + 1,
                drawOrder = distributedCards.size + 1
            )
        )
        return true
    }

    private fun finishRound(roundResult: GameResult) {
        handResults[0] = roundResult
        finishRoundFromHands()
    }

    private fun finishRoundFromHands() {
        check(handResults.all { it != null })
        val differentResults = handResults.distinct()
        result = if (differentResults.size == 1) differentResults.single() else GameResult.MIXED
        phase = GamePhase.FINISHED
    }
}
