package com.example.blackjack.viewmodel

import androidx.lifecycle.ViewModelStore
import androidx.room3.Room
import androidx.sqlite.SQLiteException
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.BlackjackDatabase
import com.example.blackjack.database.dao.BlackjackDao
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import com.example.blackjack.database.entity.PlayerBalanceEntity
import com.example.blackjack.database.entity.ShoeCardEntity
import com.example.blackjack.model.BlackjackGame
import com.example.blackjack.model.Card
import com.example.blackjack.model.GamePhase
import com.example.blackjack.model.GameResult
import com.example.blackjack.model.Rank
import com.example.blackjack.model.Suit
import com.example.blackjack.model.Shoe
import com.example.blackjack.repository.BlackjackRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: BlackjackDatabase
    private lateinit var repository: BlackjackRepository
    private lateinit var viewModel: GameViewModel
    private val store = ViewModelStore()

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder<BlackjackDatabase>(instrumentation.targetContext)
            .setDriver(AndroidSQLiteDriver())
            .build()
        repository = BlackjackRepository(database.blackjackDao())
    }

    @After
    fun closeDatabase() {
        onMain { store.clear() }
        database.close()
    }

    @Test
    fun initialStateHasOneThousandChipsAndAMinimumBet() {
        createViewModel { gameWith() }
        val state = viewModel.uiState.value
        assertEquals(1_000.0, state.chips, 0.0)
        assertEquals(1, state.selectedBet)
        assertEquals(GamePhase.READY, state.phase)
        assertTrue(state.canStartGame)
        assertTrue(state.canChangeBet)
        assertFalse(state.canHit)
        assertFalse(state.canStand)
        assertTrue(state.playerCards.isEmpty())
        assertTrue(state.dealerCards.isEmpty())
        assertNull(state.result)
        assertNull(state.savedGameId)
    }

    @Test
    fun exhaustedBalanceAfterLossCanBeResetWhileKeepingTheBetAndHistory() = runBlocking {
        database.blackjackDao().insertInitialBalance(PlayerBalanceEntity(chips = 1.0))
        createViewModel { gameWith(Rank.TEN, Rank.NINE, Rank.SIX, Rank.EIGHT) }
        onMain {
            assertTrue(viewModel.selectBet(1))
            assertTrue(viewModel.startGame())
        }
        awaitIdle()
        onMain { assertTrue(viewModel.stand()) }
        val lostGame = awaitSaved()
        assertEquals(0.0, lostGame.chips, 0.0)
        assertTrue(lostGame.canResetBalance)

        onMain { viewModel.resetBalance() }
        val resetState = withTimeout(10_000) {
            viewModel.uiState.first { !it.isResettingBalance && it.chips == 1_000.0 }
        }
        assertEquals(1, resetState.selectedBet)
        assertTrue(resetState.canStartGame)
        assertEquals(1_000.0, repository.loadBalance(), 0.0)
        assertEquals(1, repository.observeHistory().first().size)
    }

    @Test
    fun splitPublishesBothHandsSavesProgressAndSettlesBothHands() = runBlocking {
        createViewModel { gameWith(
            Rank.TEN, Rank.SIX, Rank.TEN, Rank.EIGHT, Rank.TWO, Rank.THREE, Rank.TEN
        ) }
        onMain {
            assertTrue(viewModel.selectBet(10))
            assertTrue(viewModel.startGame())
        }
        awaitIdle()

        assertTrue(viewModel.uiState.value.canSplit)
        onMain { assertTrue(viewModel.split()) }
        val splitState = viewModel.uiState.value
        assertEquals(2, splitState.playerHands.size)
        assertEquals(listOf(12, 13), splitState.playerHands.map { it.total })
        assertEquals(0, splitState.activeHandIndex)
        assertFalse(splitState.canSplit)
        awaitIdle()

        val savedProgress = checkNotNull(repository.loadGameProgress())
        assertEquals(20, savedProgress.game.totalBet)
        assertEquals(listOf(12, 13), savedProgress.hands.map { it.total })
        assertEquals(0, savedProgress.game.activeHandIndex)

        onMain { assertTrue(viewModel.stand()) }
        awaitIdle()
        assertEquals(1, viewModel.uiState.value.activeHandIndex)
        onMain { assertTrue(viewModel.stand()) }
        val finished = awaitSaved()
        assertEquals(GameResult.WIN, finished.result)
        assertEquals(20.0, finished.chipChange!!, 0.0)
        assertEquals(1_020.0, finished.chips, 0.0)
        assertEquals(1_020.0, repository.loadBalance(), 0.0)
        assertEquals(listOf(GameResult.WIN.name, GameResult.WIN.name),
            repository.getHands(checkNotNull(finished.savedGameId)).map { it.result })
    }

    @Test
    fun recreationRestoresTheRoundAndContinuesWithTheSavedShoe() = runBlocking {
        val cards = listOf(Rank.TEN, Rank.TEN, Rank.SIX, Rank.SIX, Rank.THREE, Rank.TWO)
            .map { Card(it, Suit.HEARTS) }.reversed()
        onMain {
            viewModel = GameViewModel(repository, createShoe = { Shoe(cards) },
                createGame = { BlackjackGame(it) })
            store.put("game", viewModel)
        }
        withTimeout(10_000) { viewModel.uiState.first { !it.isLoadingBalance } }
        onMain {
            viewModel.selectBet(10)
            viewModel.startGame()
            assertFalse(viewModel.hit()) // Sauvegarde initiale en cours.
        }
        awaitIdle()
        val id = checkNotNull(repository.loadGameProgress()).game.id
        createViewModel { gameWith() }
        assertEquals(10, viewModel.uiState.value.roundBet)
        assertEquals(16, viewModel.uiState.value.playerTotal)
        assertNull(viewModel.uiState.value.dealerCards.first())
        assertTrue(viewModel.uiState.value.canHit)
        onMain { assertTrue(viewModel.hit()) }
        awaitIdle()
        assertEquals(19, viewModel.uiState.value.playerTotal)
        assertEquals(id, checkNotNull(repository.loadGameProgress()).game.id)
        onMain { assertTrue(viewModel.stand()) }
        val finished = awaitSaved()
        assertEquals(id, finished.savedGameId)
        assertEquals(1010.0, finished.chips, 0.0)
        assertEquals(1010.0, repository.loadBalance(), 0.0)
        createViewModel { gameWith() }
        assertEquals(GamePhase.READY, viewModel.uiState.value.phase)
        assertEquals(1010.0, viewModel.uiState.value.chips, 0.0)
    }

    @Test
    fun failedIntermediateSaveBlocksActionsUntilRetryWithoutPaying() = runBlocking {
        val realDao = database.blackjackDao()
        var attempts = 0
        val failingDao = object : BlackjackDao by realDao {
            override suspend fun saveGameProgress(game: GameEntity, hands: List<HandEntity>,
                drawnCards: List<DrawnCardEntity>, shoeCards: List<ShoeCardEntity>): Long {
                attempts++
                if (attempts == 1) throw SQLiteException("Simulated progress failure")
                return realDao.saveGameProgress(game, hands, drawnCards, shoeCards)
            }
        }
        createViewModel(BlackjackRepository(failingDao)) {
            gameWith(Rank.TEN, Rank.TEN, Rank.NINE, Rank.EIGHT)
        }
        onMain { viewModel.startGame() }
        val failed = withTimeout(10_000) { viewModel.uiState.first { it.canRetrySave } }
        assertEquals(1000.0, failed.chips, 0.0)
        onMain {
            assertFalse(viewModel.hit())
            assertFalse(viewModel.stand())
            assertFalse(viewModel.startGame())
            viewModel.retrySave()
        }
        awaitIdle()
        assertEquals(2, attempts)
        assertTrue(viewModel.uiState.value.canHit)
        assertEquals(4, checkNotNull(repository.loadGameProgress()).drawnCards.size)
        assertEquals(1000.0, repository.loadBalance(), 0.0)
    }

    @Test
    fun invalidBetDoesNotReplaceThePreviousValidSelection() {
        createViewModel { gameWith() }
        onMain {
            assertTrue(viewModel.selectBet(100))
            assertTrue(viewModel.selectBet(10))
            for (amount in listOf(-1, 0, 101)) {
                assertFalse(viewModel.selectBet(amount))
                assertEquals(10, viewModel.uiState.value.selectedBet)
                assertEquals(GameError.BET_NOT_ALLOWED, viewModel.uiState.value.error)
            }
            assertTrue(viewModel.selectBet(1))
        }
        assertNull(viewModel.uiState.value.error)
        assertEquals(1_000.0, viewModel.uiState.value.chips, 0.0)
    }

    @Test
    fun startingHidesTheFirstDealerCardAndItsTotalWithoutTakingTheBet() = runBlocking {
        var gameCount = 0
        createViewModel {
            gameCount++
            gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX)
        }
        onMain {
            viewModel.selectBet(10)
            assertTrue(viewModel.startGame())
            assertFalse(viewModel.startGame())
            assertFalse(viewModel.selectBet(20))
        }
        awaitIdle()
        val state = viewModel.uiState.value
        assertEquals(1, gameCount)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(19, state.playerTotal)
        assertEquals(2, state.dealerCards.size)
        assertEquals(Rank.SIX, state.dealerCards[1]?.rank)
        assertNull(state.dealerCards[0])
        assertNull(state.dealerTotal)
        assertEquals(1_000.0, state.chips, 0.0)
        assertEquals(10, state.selectedBet)
        assertTrue(state.canHit)
        assertTrue(state.canStand)
        assertFalse(state.canChangeBet)
        assertTrue(repository.observeHistory().first().isEmpty())
    }

    @Test
    fun hitPublishesANewStateWithoutChangingThePreviouslyReadState() = runBlocking {
        createViewModel { gameWith(Rank.FIVE, Rank.TEN, Rank.SIX, Rank.EIGHT, Rank.SEVEN) }
        onMain { viewModel.startGame() }
        awaitIdle()
        withTimeout(10_000) {
            val firstEmission = CompletableDeferred<Unit>()
            val emissions = async {
                viewModel.uiState.onEach { firstEmission.complete(Unit) }.take(2).toList()
            }
            firstEmission.await()
            onMain { assertTrue(viewModel.hit()) }
            val states = emissions.await()
            assertEquals(11, states.first().playerTotal)
            assertEquals(2, states.first().playerCards.size)
            assertEquals(18, states.last().playerTotal)
            assertEquals(3, states.last().playerCards.size)
            assertNull(states.last().dealerCards[0])
            assertNull(states.last().dealerTotal)
            assertEquals(1_000.0, states.last().chips, 0.0)
        }
    }

    @Test
    fun standRevealsTheDealerPaysOnceAndSavesOneCompleteGame() = runBlocking {
        createViewModel { gameWith(Rank.TEN, Rank.TEN, Rank.NINE, Rank.EIGHT) }
        onMain {
            viewModel.selectBet(10)
            viewModel.startGame()
        }
        awaitIdle()
        onMain {
            assertTrue(viewModel.stand())
            assertTrue(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.selectDeckCount(8))
            assertFalse(viewModel.startGame())
            assertFalse(viewModel.selectBet(20))
            assertFalse(viewModel.stand())
            assertFalse(viewModel.hit())
        }
        val state = awaitSaved()
        assertEquals(GameResult.WIN, state.result)
        assertEquals(18, state.dealerTotal)
        assertTrue(state.dealerCards.all { it != null })
        assertEquals(10.0, state.chipChange!!, 0.0)
        assertEquals(1_010.0, state.chips, 0.0)
        assertFalse(state.canHit)
        assertFalse(state.canStand)
        assertTrue(state.canStartGame)
        val id = state.savedGameId!!
        assertEquals(listOf(HandEntity(id, 1, 10, 19, "WIN", false, 10.0)), repository.getHands(id))
        assertEquals(4, repository.getDrawnCards(id).size)
        assertEquals(1, repository.observeHistory().first().size)
        onMain {
            viewModel.stand()
            viewModel.hit()
            viewModel.retrySave()
        }
        assertEquals(state, viewModel.uiState.value)
        assertEquals(1, repository.observeHistory().first().size)
    }

    @Test
    fun naturalBlackjackFinishesImmediatelyAndPaysTwoBets() = runBlocking {
        createViewModel { gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE) }
        onMain {
            viewModel.selectBet(5)
            viewModel.startGame()
        }
        val state = awaitSaved()
        assertTrue(state.playerHasBlackjack)
        assertEquals(GameResult.WIN, state.result)
        assertEquals(10.0, state.chipChange!!, 0.0)
        assertEquals(1_010.0, state.chips, 0.0)
        assertTrue(state.dealerCards.all { it != null })
        assertEquals(10.0, repository.observeHistory().first().single().chipChange!!, 0.0)
    }

    @Test
    fun dealerBlackjackLosesTheBet() = runBlocking {
        createViewModel { gameWith(Rank.TEN, Rank.ACE, Rank.NINE, Rank.QUEEN) }
        onMain {
            viewModel.selectBet(10)
            viewModel.startGame()
        }
        val state = awaitSaved()
        assertEquals(GameResult.LOSS, state.result)
        assertEquals(-10.0, state.chipChange!!, 0.0)
        assertEquals(990.0, state.chips, 0.0)
    }

    @Test
    fun twoBlackjacksKeepTheBalanceUnchanged() = runBlocking {
        createViewModel { gameWith(Rank.ACE, Rank.ACE, Rank.TEN, Rank.KING) }
        onMain {
            viewModel.selectBet(10)
            viewModel.startGame()
        }
        val state = awaitSaved()
        assertEquals(GameResult.DRAW, state.result)
        assertEquals(0.0, state.chipChange!!, 0.0)
        assertEquals(1_000.0, state.chips, 0.0)
    }

    @Test
    fun playerBustSettlesAndSavesWithoutAnotherDealerDraw() = runBlocking {
        createViewModel { gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX, Rank.THREE) }
        onMain {
            viewModel.selectBet(10)
            viewModel.startGame()
        }
        awaitIdle()
        onMain {
            viewModel.hit()
        }
        val state = awaitSaved()
        assertEquals(GameResult.LOSS, state.result)
        assertEquals(22, state.playerTotal)
        assertEquals(2, state.dealerCards.size)
        assertEquals(990.0, state.chips, 0.0)
        assertEquals(5, repository.getDrawnCards(state.savedGameId!!).size)
    }

    @Test
    fun twentyOneOnHitAutomaticallyPlaysTheDealerAndPaysAnOrdinaryWin() = runBlocking {
        createViewModel { gameWith(Rank.SEVEN, Rank.TEN, Rank.SEVEN, Rank.SIX, Rank.SEVEN, Rank.TEN) }
        onMain {
            viewModel.selectBet(10)
            viewModel.startGame()
        }
        awaitIdle()
        onMain {
            viewModel.hit()
        }
        val state = awaitSaved()
        assertEquals(21, state.playerTotal)
        assertEquals(26, state.dealerTotal)
        assertFalse(state.playerHasBlackjack)
        assertEquals(10.0, state.chipChange!!, 0.0)
        assertEquals(1_010.0, state.chips, 0.0)
        assertEquals(6, repository.getDrawnCards(state.savedGameId!!).size)
    }

    @Test
    fun interruptedRoundsKeepTheirCardsWithoutPaymentAndAllowANewRound() = runBlocking {
        val games = listOf(
            gameWith(Rank.TEN),
            gameWith(Rank.TEN, Rank.TEN, Rank.EIGHT, Rank.NINE),
            gameWith(Rank.TEN, Rank.FIVE, Rank.NINE, Rank.SIX)
        )
        for ((index, game) in games.withIndex()) {
            createViewModel { game }
            onMain {
                viewModel.selectBet(10)
                viewModel.startGame()
            }
            awaitIdle()
            onMain {
                if (index == 1) viewModel.hit()
                if (index == 2) viewModel.stand()
            }
            awaitIdle()
            val state = viewModel.uiState.value
            assertEquals(GamePhase.INTERRUPTED, state.phase)
            assertEquals(GameError.ROUND_INTERRUPTED, state.error)
            assertEquals(1_000.0, state.chips, 0.0)
            assertNull(state.chipChange)
            assertNull(state.result)
            assertNull(state.savedGameId)
            assertTrue(state.canStartGame)
            assertTrue(repository.observeHistory().first().isEmpty())
            assertNull(repository.loadGameProgress())
        }
    }

    @Test
    fun changingTheNextBetKeepsTheFinishedRoundsBetAndPayment() = runBlocking {
        createViewModel { gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE) }
        onMain {
            viewModel.selectBet(10)
            viewModel.startGame()
        }
        val saved = awaitSaved()
        onMain { assertTrue(viewModel.selectBet(20)) }
        val state = viewModel.uiState.value
        assertEquals(20, state.selectedBet)
        assertEquals(10, state.roundBet)
        assertEquals(1_020.0, state.chips, 0.0)
        assertEquals(20.0, state.chipChange!!, 0.0)
        assertEquals(10, repository.getHands(saved.savedGameId!!).single().bet)
    }

    @Test
    fun newGameKeepsTheBetAndBalanceButUsesANewGameAndClearsThePreviousResult() = runBlocking {
        var gameCount = 0
        createViewModel {
            gameCount++
            if (gameCount == 1) gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE)
            else gameWith(Rank.TEN, Rank.TEN, Rank.NINE, Rank.EIGHT)
        }
        onMain {
            viewModel.selectBet(5)
            viewModel.startGame()
        }
        val previousId = awaitSaved().savedGameId!!
        onMain { assertTrue(viewModel.startGame()) }
        val state = viewModel.uiState.value
        assertEquals(2, gameCount)
        assertEquals(5, state.selectedBet)
        assertEquals(1_010.0, state.chips, 0.0)
        assertEquals(GamePhase.PLAYER_TURN, state.phase)
        assertEquals(19, state.playerTotal)
        assertNull(state.chipChange)
        assertNull(state.savedGameId)
        assertNull(state.result)
        assertNull(state.error)
        assertEquals(4, repository.getDrawnCards(previousId).size)
    }

    @Test
    fun depletedBalanceCannotStartAnotherRoundOrAcceptAnUnaffordableBet() = runBlocking {
        createViewModel { gameWith(Rank.TEN, Rank.ACE, Rank.NINE, Rank.KING) }
        onMain { viewModel.selectBet(100) }
        repeat(10) { index ->
            onMain { assertTrue(viewModel.startGame()) }
            val state = awaitSaved()
            assertEquals(900.0 - index * 100, state.chips, 0.0)
        }
        onMain {
            assertFalse(viewModel.startGame())
            assertFalse(viewModel.selectBet(1))
        }
        assertEquals(100, viewModel.uiState.value.selectedBet)
        assertFalse(viewModel.uiState.value.canStartGame)
        assertEquals(10, repository.observeHistory().first().size)
    }

    @Test
    fun failedSaveCanBeRetriedWithoutPayingTwiceOrDuplicatingTheGame() = runBlocking {
        val realDao = database.blackjackDao()
        val firstAttempt = CompletableDeferred<Unit>()
        var attempts = 0
        // Seule la première écriture échoue volontairement; les autres opérations
        // sont déléguées au vrai DAO Room pour tester la reprise de sauvegarde.
        val failingOnceDao = object : BlackjackDao by realDao {
            override suspend fun insertGameWithDetails(
                game: GameEntity, hands: List<HandEntity>, cards: List<DrawnCardEntity>
            ): Long {
                attempts++
                if (attempts == 1) {
                    firstAttempt.await()
                    throw SQLiteException("Simulated write failure.")
                }
                return realDao.insertGameWithDetails(game, hands, cards)
            }
        }
        createViewModel(
            BlackjackRepository(failingOnceDao)
        ) { gameWith(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE) }
        onMain {
            viewModel.selectBet(10)
            viewModel.startGame()
            assertTrue(viewModel.uiState.value.isSaving)
            assertFalse(viewModel.startGame())
            assertFalse(viewModel.selectBet(20))
            viewModel.retrySave()
        }
        firstAttempt.complete(Unit)
        val failed = withTimeout(10_000) {
            viewModel.uiState.first { it.error == GameError.SAVE_FAILED }
        }
        assertEquals(1_020.0, failed.chips, 0.0)
        assertTrue(failed.canRetrySave)
        onMain { assertFalse(viewModel.selectDeckCount(8)) }
        assertNull(failed.savedGameId)
        assertTrue(repository.observeHistory().first().isEmpty())
        onMain {
            assertFalse(viewModel.startGame())
            assertFalse(viewModel.selectBet(20))
            viewModel.retrySave()
        }
        val saved = awaitSaved()
        assertEquals(2, attempts)
        assertEquals(1_020.0, saved.chips, 0.0)
        assertNull(saved.error)
        assertFalse(saved.canRetrySave)
        assertNotNull(saved.savedGameId)
        assertEquals(1, repository.observeHistory().first().size)
        onMain { viewModel.retrySave() }
        assertEquals(2, attempts)
    }

    @Test
    fun deckSelectionAcceptsOneToEightAndRejectsChangesDuringTheRound() {
        createViewModel { gameWith(Rank.EIGHT, Rank.TEN, Rank.EIGHT, Rank.SEVEN) }
        onMain {
            assertEquals(1, viewModel.uiState.value.selectedDeckCount)
            for (count in 1..8) assertTrue(viewModel.selectDeckCount(count))
            for (count in listOf(0, 9, Int.MIN_VALUE, Int.MAX_VALUE)) {
                assertFalse(viewModel.selectDeckCount(count))
            }
            assertEquals(8, viewModel.uiState.value.selectedDeckCount)
            viewModel.startGame()
            assertFalse(viewModel.selectDeckCount(2))
            assertEquals(8, viewModel.uiState.value.selectedDeckCount)
        }
    }

    @Test
    fun discardAccumulatesAcrossRoundsAndClearsWithANewShoe() = runBlocking {
        onMain {
            viewModel = GameViewModel(repository,
                createShoe = { decks ->
                    // Tirage par la fin : joueur 20, croupier 17, aucune pige supplémentaire.
                    Shoe(List(13 * decks) {
                        listOf(Rank.EIGHT, Rank.TEN, Rank.NINE, Rank.TEN)
                            .map { Card(it, Suit.SPADES) }
                    }.flatten())
                }, createGame = { BlackjackGame(it) })
            store.put("game", viewModel)
        }
        withTimeout(10_000) { viewModel.uiState.first { !it.isLoadingBalance } }
        repeat(2) { round ->
            onMain { assertTrue(viewModel.startGame()) }
            awaitIdle()
            assertEquals(round * 4, viewModel.uiState.value.discardedCardCount)
            onMain { assertTrue(viewModel.stand()) }
            assertEquals((round + 1) * 4, awaitSaved().discardedCardCount)
        }
        onMain {
            assertTrue(viewModel.selectDeckCount(2))
            assertTrue(viewModel.startGame())
        }
        awaitIdle()
        assertEquals(0, viewModel.uiState.value.discardedCardCount)
    }

    @Test
    fun selectedDeckCountKeepsTheSameShoeForFollowingRounds() = runBlocking {
        val shoes = mutableListOf<Shoe>()
        onMain {
            viewModel = GameViewModel(repository) { shoe ->
                shoes.add(shoe)
                BlackjackGame(shoe)
            }
            store.put("game", viewModel)
        }
        withTimeout(10_000) { viewModel.uiState.first { !it.isLoadingBalance } }
        onMain {
            assertTrue(viewModel.selectDeckCount(8))
            assertTrue(viewModel.startGame())
            assertEquals(412, shoes.last().remainingCards)
        }
        awaitIdle()
        onMain {
            if (viewModel.uiState.value.canStand) viewModel.stand()
        }
        awaitSaved()
        val remainingAfterFirstRound = shoes.last().remainingCards
        onMain {
            assertTrue(viewModel.startGame())
            assertEquals(3, shoes.size) // Préparation initiale, première partie, seconde partie.
            assertEquals(remainingAfterFirstRound - 4, shoes.last().remainingCards)
            assertTrue(shoes[1] === shoes[2])
            assertEquals(8, viewModel.uiState.value.selectedDeckCount)
        }
    }

    @Test
    fun recreatedViewModelReadsTheStoredBalance() = runBlocking {
        database.blackjackDao().getOrCreateBalance()
        database.blackjackDao().applyChipChange(-250.5)
        createViewModel { gameWith() }
        assertEquals(749.5, viewModel.uiState.value.chips, 0.0)
        createViewModel { gameWith() }
        assertEquals(749.5, viewModel.uiState.value.chips, 0.0)
    }

    @Test
    fun balanceReadFailureBlocksPlayUntilRetrySucceeds() = runBlocking {
        val realDao = database.blackjackDao()
        var failRead = true
        val failingDao = object : BlackjackDao by realDao {
            override suspend fun getOrCreateBalance(): Double {
                if (failRead) throw SQLiteException("Test balance read failure")
                return realDao.getOrCreateBalance()
            }
        }
        createViewModel(BlackjackRepository(failingDao)) { gameWith() }
        assertEquals(GameError.BALANCE_LOAD_FAILED, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.canStartGame)
        assertFalse(viewModel.uiState.value.canChangeBet)
        failRead = false
        onMain { viewModel.retryLoadBalance() }
        withTimeout(10_000) { viewModel.uiState.first { !it.isLoadingBalance && it.error == null } }
        assertTrue(viewModel.uiState.value.canStartGame)
        assertEquals(1000.0, viewModel.uiState.value.chips, 0.0)
    }

    @Test
    fun exactlyFourRemainingCardsAreReusedThenAnEmptyShoeIsRenewed() = runBlocking {
        val shoes = prepareShortShoes(8)
        onMain { assertTrue(viewModel.startGame()) }
        awaitSaved()
        assertEquals(4, shoes.single().remainingCards)
        onMain { assertTrue(viewModel.startGame()) }
        awaitSaved()
        assertEquals(1, shoes.size)
        assertEquals(0, shoes.single().remainingCards)
        onMain { assertTrue(viewModel.startGame()) }
        awaitSaved()
        assertEquals(2, shoes.size)
        assertEquals(4, shoes.last().remainingCards)
        assertEquals(3, repository.observeHistory().first().size)
    }

    @Test
    fun oneTwoOrThreeRemainingCardsTriggerRenewalBeforeTheNextDistribution() = runBlocking {
        for (remaining in 1..3) {
            val shoes = prepareShortShoes(remaining + 4)
            onMain { assertTrue(viewModel.startGame()) }
            awaitSaved()
            assertEquals(remaining, shoes.single().remainingCards)
            onMain { assertTrue(viewModel.startGame()) }
            awaitSaved()
            assertEquals(2, shoes.size)
            assertEquals(remaining, shoes.last().remainingCards)
        }
    }

    @Test
    fun changingDeckCountReplacesAShoeThatStillHasEnoughCards() = runBlocking {
        val shoes = prepareShortShoes(12)
        onMain { assertTrue(viewModel.startGame()) }
        awaitSaved()
        assertEquals(8, shoes.single().remainingCards)
        onMain {
            assertTrue(viewModel.selectDeckCount(2))
            assertTrue(viewModel.startGame())
        }
        awaitSaved()
        assertEquals(2, shoes.size)
        assertEquals(8, shoes.first().remainingCards)
        assertEquals(100, shoes.last().remainingCards)
    }

    // Consomme réellement le sabot, mais retourne des rangs prévisibles pour finir
    // chaque partie en quatre cartes, indépendamment du mélange aléatoire de Shoe.
    private fun prepareShortShoes(initialCards: Int): MutableList<Shoe> {
        val shoes = mutableListOf<Shoe>()
        onMain {
            store.clear()
            viewModel = GameViewModel(
                repository,
                createShoe = { count ->
                    Shoe(count).also { shoe ->
                        if (count == 1) repeat(shoe.remainingCards - initialCards) { shoe.drawCard() }
                        shoes.add(shoe)
                    }
                },
                createGame = { shoe ->
                    val ranks = ArrayDeque(listOf(Rank.ACE, Rank.TEN, Rank.KING, Rank.NINE))
                    BlackjackGame {
                        if (shoe.drawCard() == null) null
                        else Card(ranks.removeFirst(), Suit.HEARTS)
                    }
                }
            )
            store.put("game", viewModel)
        }
        runBlocking { withTimeout(10_000) { viewModel.uiState.first { !it.isLoadingBalance } } }
        return shoes
    }

    private fun createViewModel(
        dataRepository: BlackjackRepository = repository,
        createGame: () -> BlackjackGame
    ) {
        onMain {
            store.clear()
            viewModel = GameViewModel(dataRepository) { createGame() }
            store.put("game", viewModel)
        }
        runBlocking {
            withTimeout(10_000) { viewModel.uiState.first { !it.isLoadingBalance } }
        }
    }

    private suspend fun awaitSaved(): GameUiState = withTimeout(10_000) {
        viewModel.uiState.first { !it.isSaving && it.savedGameId != null }
    }

    private suspend fun awaitIdle() = withTimeout(10_000) {
        viewModel.uiState.first { !it.isSaving }
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun gameWith(vararg ranks: Rank): BlackjackGame {
        val cards = ranks.mapIndexed { index, rank ->
            Card(rank, Suit.entries[index % Suit.entries.size])
        }.toMutableList()
        return BlackjackGame { if (cards.isEmpty()) null else cards.removeAt(0) }
    }
}
