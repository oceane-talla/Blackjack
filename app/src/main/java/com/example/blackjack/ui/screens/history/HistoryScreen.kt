package com.example.blackjack.ui.screens.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.blackjack.R
import com.example.blackjack.ui.components.*
import com.example.blackjack.viewmodel.HistoryUiState
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(state: HistoryUiState, modifier: Modifier = Modifier) {
    // LazyColumn ne construit que les lignes visibles, même après beaucoup de parties.
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium) }
        item { Text(stringResource(R.string.history_description)) }
        if (state.isLoading) item { CircularProgressIndicator() }
        if (state.hasError) item { Text(stringResource(R.string.read_failed), color = MaterialTheme.colorScheme.error) }
        if (!state.isLoading && !state.hasError && state.games.isEmpty()) item {
            Text(stringResource(R.string.empty_history), Modifier.testTag("empty_history"))
        }
        items(state.games, key = { it.id }) { game ->
            CasinoPanel(Modifier.testTag("history_game_${game.id}")) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(game.playedAt)),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(resultLabel(game.result)), color = MaterialTheme.colorScheme.primary)
                ValueRow(stringResource(R.string.bet), game.totalBet.toString())
                ValueRow(stringResource(R.string.net_change_label), game.chipChange?.let(::chipText) ?: "—")
                ValueRow(stringResource(R.string.dealer), game.dealerTotal.toString())
            }
        }
    }
}
