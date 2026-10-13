package com.example.blackjack.ui.screens.statistics

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.blackjack.R
import com.example.blackjack.ui.components.*
import com.example.blackjack.viewmodel.StatisticsUiState

@Composable
fun StatisticsScreen(state: StatisticsUiState, modifier: Modifier = Modifier) {
    ScreenContent(stringResource(R.string.statistics_title), stringResource(R.string.statistics_description), modifier) {
        if (state.isLoading) CircularProgressIndicator()
        if (state.hasError) Text(stringResource(R.string.read_failed), color = MaterialTheme.colorScheme.error)
        if (!state.isLoading) {
            val stats = state.statistics
            // SQLite fournit toutes les valeurs : ici, seule leur présentation change.
            CasinoPanel {
                Text(stringResource(R.string.results_heading), style = MaterialTheme.typography.titleLarge)
                ValueRow(stringResource(R.string.total_games), stats.totalGames.toString())
                ValueRow(stringResource(R.string.total_hands), stats.totalHands.toString())
                ValueRow(stringResource(R.string.result_win), stats.winningHands.toString())
                ValueRow(stringResource(R.string.result_loss), stats.losingHands.toString())
                ValueRow(stringResource(R.string.result_draw), stats.drawnHands.toString())
                ValueRow(stringResource(R.string.natural_blackjack), stats.naturalBlackjacks.toString())
            }
            CasinoPanel {
                Text(stringResource(R.string.chips_heading), style = MaterialTheme.typography.titleLarge)
                ValueRow(stringResource(R.string.total_gains), chipText(stats.totalGains))
                ValueRow(stringResource(R.string.total_losses), chipText(stats.totalLosses))
                ValueRow(stringResource(R.string.average_bet), stats.averageBet?.let(::chipText) ?: "—")
                ValueRow(stringResource(R.string.highest_bet), stats.highestBet?.toString() ?: "—")
            }
            CasinoPanel {
                Text(stringResource(R.string.cards_heading), style = MaterialTheme.typography.titleLarge)
                ValueRow(stringResource(R.string.total_cards), stats.totalCards.toString())
                CardStatistic(R.string.red_cards, stats.redCards, stats.redPercentage)
                CardStatistic(R.string.black_cards, stats.blackCards, stats.blackPercentage)
                CardStatistic(R.string.suit_hearts, stats.heartsCards, stats.heartsPercentage)
                CardStatistic(R.string.suit_diamonds, stats.diamondsCards, stats.diamondsPercentage)
                CardStatistic(R.string.suit_clubs, stats.clubsCards, stats.clubsPercentage)
                CardStatistic(R.string.suit_spades, stats.spadesCards, stats.spadesPercentage)
            }
            CasinoPanel {
                Text(stringResource(R.string.ranks_heading), style = MaterialTheme.typography.titleLarge)
                state.rankStatistics.forEach { rank ->
                    val label = when (rank.rank) {
                        "ACE" -> stringResource(R.string.rank_ace)
                        "TWO" -> "2"
                        "THREE" -> "3"
                        "FOUR" -> "4"
                        "FIVE" -> "5"
                        "SIX" -> "6"
                        "SEVEN" -> "7"
                        "EIGHT" -> "8"
                        "NINE" -> "9"
                        "TEN" -> "10"
                        "JACK" -> stringResource(R.string.rank_jack)
                        "QUEEN" -> stringResource(R.string.rank_queen)
                        "KING" -> stringResource(R.string.rank_king)
                        else -> rank.rank
                    }
                    ValueRow(label, stringResource(R.string.card_statistic,
                        rank.cardCount.toString(), rank.percentage?.let(::chipText) ?: "—"))
                }
            }
        }
    }
}

@Composable
private fun CardStatistic(label: Int, count: Long, percentage: Double?) {
    ValueRow(stringResource(label), stringResource(R.string.card_statistic, count.toString(), percentage?.let(::chipText) ?: "—"))
}
