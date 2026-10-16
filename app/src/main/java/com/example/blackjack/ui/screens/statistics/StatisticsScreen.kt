package com.example.blackjack.ui.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                StatisticsTableHeader()
                CardStatistic(R.string.red_cards, stats.redCards, stats.redPercentage)
                CardStatistic(R.string.black_cards, stats.blackCards, stats.blackPercentage)
                CardStatistic(R.string.suit_hearts, stats.heartsCards, stats.heartsPercentage)
                CardStatistic(R.string.suit_diamonds, stats.diamondsCards, stats.diamondsPercentage)
                CardStatistic(R.string.suit_clubs, stats.clubsCards, stats.clubsPercentage)
                CardStatistic(R.string.suit_spades, stats.spadesCards, stats.spadesPercentage)
            }
            CasinoPanel {
                Text(stringResource(R.string.ranks_heading), style = MaterialTheme.typography.titleLarge)
                StatisticsTableHeader()
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
                    StatisticsTableRow(label, rank.cardCount.toString(), rank.percentage?.let(::chipText) ?: "—")
                }
            }
        }
    }
}

@Composable
private fun CardStatistic(label: Int, count: Long, percentage: Double?) {
    StatisticsTableRow(stringResource(label), count.toString(), percentage?.let(::chipText) ?: "—")
}

@Composable
private fun StatisticsTableHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            TableCell(stringResource(R.string.stat_category_header), 1.25f, TextAlign.Start, header = true)
            StatisticsColumnDivider()
            TableCell(stringResource(R.string.stat_count_header), 0.75f, TextAlign.End, header = true)
            StatisticsColumnDivider()
            TableCell(stringResource(R.string.stat_share_header), 0.8f, TextAlign.End, header = true)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), thickness = 1.dp)
    }
}

@Composable
private fun StatisticsTableRow(label: String, count: String, percentage: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            TableCell(label, 1.25f, TextAlign.Start)
            StatisticsColumnDivider()
            TableCell(count, 0.75f, TextAlign.End)
            StatisticsColumnDivider()
            TableCell(percentage, 0.8f, TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), thickness = 0.5.dp)
    }
}

@Composable
private fun RowScope.TableCell(text: String, weight: Float, alignment: TextAlign, header: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight).padding(horizontal = 6.dp),
        color = if (header) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontSize = if (header) 11.sp else MaterialTheme.typography.bodyMedium.fontSize,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = alignment,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun StatisticsColumnDivider() {
    Box(Modifier.width(1.dp).height(24.dp).background(Color(0xFFBDA06A).copy(alpha = 0.8f)))
}
