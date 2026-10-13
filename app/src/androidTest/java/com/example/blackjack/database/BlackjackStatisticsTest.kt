package com.example.blackjack.database

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.blackjack.database.dao.BlackjackDao
import com.example.blackjack.database.entity.DrawnCardEntity
import com.example.blackjack.database.entity.GameEntity
import com.example.blackjack.database.entity.HandEntity
import com.example.blackjack.model.GameStatistics
import com.example.blackjack.model.Rank
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlackjackStatisticsTest {
    private lateinit var database: BlackjackDatabase
    private lateinit var dao: BlackjackDao
    private lateinit var repository: BlackjackRepository

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder<BlackjackDatabase>(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).setDriver(AndroidSQLiteDriver()).build()
        dao = database.blackjackDao()
        repository = BlackjackRepository(dao)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun emptyDatabaseReturnsZeroCountsAndUndefinedAveragesAndProportions() = runBlocking {
        assertEquals(GameStatistics(), dao.observeStatistics().first())
        assertEquals(GameStatistics(), repository.observeStatistics().first())
    }

    @Test
    fun standardSplitAndNaturalDrawProduceExactIndependentAggregates() = runBlocking {
        // Partie 1 : Blackjack gagnant à mise 5, donc gain net de 7,5.
        dao.insertGameWithDetails(
            game(bet = 5, change = 7.5),
            listOf(hand(bet = 5, change = 7.5, natural = true)),
            cards("HEARTS", "CLUBS", "DIAMONDS", "SPADES")
        )
        // Partie 2 : une victoire et une défaite après Split, mise totale 20, bilan nul.
        // Le résultat résumé est volontairement différent pour vérifier le comptage par main.
        val splitCards = cards("HEARTS", "CLUBS", "DIAMONDS", "SPADES", "HEARTS", "CLUBS")
            .map { card ->
                when (card.drawOrder) {
                    3, 6 -> card.copy(handNumber = 2)
                    5 -> card.copy(handNumber = 1)
                    else -> card
                }
            }
        dao.insertGameWithDetails(
            game(bet = 10, change = 0.0).copy(totalBet = 20, result = "TEST_SPLIT"),
            listOf(hand(), hand(number = 2, result = "LOSS", change = -10.0)),
            splitCards
        )
        // Partie 3 : deux Blackjacks naturels, donc égalité et aucun gain.
        dao.insertGameWithDetails(
            game(bet = 11, change = 0.0).copy(result = "DRAW"),
            listOf(hand(bet = 11, result = "DRAW", change = 0.0, natural = true)),
            cards("HEARTS", "CLUBS", "DIAMONDS", "SPADES")
        )

        val actual = repository.observeStatistics().first()
        val expected = GameStatistics(
            totalGames = 3, totalHands = 4, totalCards = 14,
            winningHands = 2, losingHands = 1, drawnHands = 1, naturalBlackjacks = 2,
            totalGains = 17.5, totalLosses = 10.0, averageBet = 12.0, highestBet = 20,
            redCards = 7, blackCards = 7, redPercentage = 50.0, blackPercentage = 50.0,
            heartsCards = 4, diamondsCards = 3, clubsCards = 4, spadesCards = 3,
            heartsPercentage = 100.0 * 4 / 14, diamondsPercentage = 100.0 * 3 / 14,
            clubsPercentage = 100.0 * 4 / 14, spadesPercentage = 100.0 * 3 / 14
        )
        assertEquals(expected, actual)
        assertEquals(100.0, actual.redPercentage!! + actual.blackPercentage!!, 0.000001)
        assertEquals(100.0, actual.heartsPercentage!! + actual.diamondsPercentage!! +
            actual.clubsPercentage!! + actual.spadesPercentage!!, 0.000001)
    }

    @Test
    fun averageBetKeepsFractionsAndDoesNotRequireHandsOrCards() = runBlocking {
        dao.insertGame(game(bet = 1))
        dao.insertGame(game(bet = 2))

        val statistics = repository.observeStatistics().first()
        assertEquals(2L, statistics.totalGames)
        assertEquals(0L, statistics.totalHands)
        assertEquals(0L, statistics.totalCards)
        assertEquals(1.5, statistics.averageBet!!, 0.0)
        assertEquals(2, statistics.highestBet)
        assertNull(statistics.redPercentage)
        assertNull(statistics.blackPercentage)
    }

    @Test
    fun absentSuitsHaveZeroPercentWhenOtherCardsExist() = runBlocking {
        dao.insertGameWithDetails(
            game(), listOf(hand()), cards("CLUBS", "SPADES", "CLUBS")
        )
        val statistics = repository.observeStatistics().first()
        assertEquals(3L, statistics.totalCards)
        assertEquals(0L, statistics.redCards)
        assertEquals(3L, statistics.blackCards)
        assertEquals(0.0, statistics.redPercentage!!, 0.0)
        assertEquals(100.0, statistics.blackPercentage!!, 0.0)
        assertEquals(0.0, statistics.heartsPercentage!!, 0.0)
        assertEquals(0.0, statistics.diamondsPercentage!!, 0.0)
        assertEquals(200.0 / 3, statistics.clubsPercentage!!, 0.000001)
        assertEquals(100.0 / 3, statistics.spadesPercentage!!, 0.000001)
    }

    @Test
    fun aTwentyOneWithoutNaturalFlagIsNotCountedAsBlackjack() = runBlocking {
        dao.insertGameWithDetails(game(), listOf(hand().copy(total = 21)), emptyList())
        val statistics = repository.observeStatistics().first()
        assertEquals(1L, statistics.winningHands)
        assertEquals(0L, statistics.naturalBlackjacks)
    }

    @Test
    fun theSameFlowReactsToChangesInEachOfTheThreeTables() = runBlocking {
        withTimeout(10_000) {
            val initial = CompletableDeferred<Unit>()
            val gameReceived = CompletableDeferred<Unit>()
            val handReceived = CompletableDeferred<Unit>()
            val emissions = async {
                repository.observeStatistics().onEach {
                    when {
                        it.totalGames == 0L -> initial.complete(Unit)
                        it.totalHands == 0L -> gameReceived.complete(Unit)
                        it.totalCards == 0L -> handReceived.complete(Unit)
                    }
                }.take(4).toList()
            }
            initial.await()
            val id = dao.insertGame(game())
            gameReceived.await()
            dao.insertHands(listOf(hand().copy(gameId = id)))
            handReceived.await()
            dao.insertDrawnCards(cards("HEARTS").map { it.copy(gameId = id) })

            val states = emissions.await()
            assertEquals(4, states.size)
            assertEquals(GameStatistics(), states.first())
            assertEquals(1L, states.last().totalGames)
            assertEquals(1L, states.last().totalHands)
            assertEquals(1L, states.last().totalCards)
            assertEquals(100.0, states.last().redPercentage!!, 0.0)
        }
    }

    @Test
    fun emptyDatabaseReturnsEveryRankWithZeroCountAndUndefinedPercentage() = runBlocking {
        val statistics = dao.observeRankStatistics().first()
        assertEquals(Rank.entries.map { it.name }, statistics.map { it.rank })
        statistics.forEach {
            assertEquals(0L, it.cardCount)
            assertNull(it.percentage)
        }
    }

    @Test
    fun ranksKeepFiguresSeparateAndCountBothPlayerAndDealerAcrossGames() = runBlocking {
        val ranks = listOf("ACE", "TEN", "JACK", "QUEEN", "KING", "ACE")
        val firstCards = cards(*Array(ranks.size) { "HEARTS" })
            .mapIndexed { index, card -> card.copy(rank = ranks[index]) }
        dao.insertGameWithDetails(game(), listOf(hand()), firstCards)
        dao.insertGameWithDetails(game(), listOf(hand()), cards("SPADES"))

        val statistics = dao.observeRankStatistics().first()
        val byRank = statistics.associateBy { it.rank }
        assertEquals(13, statistics.size)
        assertEquals(7L, statistics.sumOf { it.cardCount })
        assertEquals(2L, byRank.getValue("ACE").cardCount)
        assertEquals(2L, byRank.getValue("TEN").cardCount)
        listOf("JACK", "QUEEN", "KING").forEach {
            assertEquals(1L, byRank.getValue(it).cardCount)
            assertEquals(100.0 / 7, byRank.getValue(it).percentage!!, 0.000001)
        }
        assertEquals(200.0 / 7, byRank.getValue("ACE").percentage!!, 0.000001)
        assertEquals(0L, byRank.getValue("TWO").cardCount)
        assertEquals(0.0, byRank.getValue("TWO").percentage!!, 0.0)
        assertEquals(100.0, statistics.sumOf { it.percentage!! }, 0.000001)
    }

    @Test
    fun rankStatisticsFlowUpdatesAfterCardsAreSaved() = runBlocking {
        withTimeout(10_000) {
            val initial = CompletableDeferred<Unit>()
            val emissions = async {
                dao.observeRankStatistics().onEach { initial.complete(Unit) }.take(2).toList()
            }
            initial.await()
            dao.insertGameWithDetails(game(), listOf(hand()), cards("CLUBS"))
            val states = emissions.await()
            assertEquals(0L, states.first().sumOf { it.cardCount })
            val ten = states.last().single { it.rank == "TEN" }
            assertEquals(1L, ten.cardCount)
            assertEquals(100.0, ten.percentage!!, 0.0)
        }
    }

    private fun game(bet: Int = 10, change: Double = 10.0) = GameEntity(
        playedAt = 1_000, initialBet = bet, totalBet = bet,
        result = "WIN", chipChange = change, dealerTotal = 19
    )

    private fun hand(
        number: Int = 1,
        bet: Int = 10,
        result: String = "WIN",
        change: Double = 10.0,
        natural: Boolean = false
    ) = HandEntity(
        gameId = 0, handNumber = number, bet = bet,
        total = if (natural) 21 else 20,
        result = result, isNaturalBlackjack = natural, chipChange = change
    )

    // Ces données vérifient les agrégats SQL ; elles ne simulent pas une partie du moteur.
    private fun cards(vararg suits: String) = suits.mapIndexed { index, suit ->
        DrawnCardEntity(
            gameId = 0, handNumber = if (index % 2 == 0) 1 else null,
            rank = "TEN", suit = suit, drawOrder = index + 1
        )
    }
}
