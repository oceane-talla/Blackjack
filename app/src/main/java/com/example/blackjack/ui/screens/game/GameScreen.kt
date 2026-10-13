package com.example.blackjack.ui.screens.game

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.blackjack.R
import com.example.blackjack.model.Card
import com.example.blackjack.model.GamePhase
import com.example.blackjack.ui.components.*
import com.example.blackjack.viewmodel.GameUiState
import com.example.blackjack.viewmodel.GameError

@Composable
fun GameScreen(state: GameUiState, onHit: () -> Unit, onStand: () -> Unit, onSplit: () -> Unit,
    onRetrySave: () -> Unit, onNewGame: () -> Unit, modifier: Modifier = Modifier) {
    ScreenContent(stringResource(R.string.game_title), stringResource(R.string.game_description), modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.bet_amount, state.roundBet ?: state.selectedBet))
            Text(stringResource(R.string.balance_amount, chipText(state.chips)))
        }
        HandPanel(stringResource(R.string.dealer), state.dealerCards, state.dealerTotal)
        state.playerHands.forEach { hand ->
            val handLabel = when {
                state.playerHands.size == 1 -> stringResource(R.string.player)
                hand.handNumber - 1 == state.activeHandIndex && state.phase == GamePhase.PLAYER_TURN ->
                    stringResource(R.string.active_hand, hand.handNumber)
                else -> stringResource(R.string.hand_number, hand.handNumber)
            }
            HandPanel(
                label = handLabel,
                cards = hand.cards,
                total = hand.total,
                result = hand.result?.name,
                modifier = Modifier.testTag("player_hand_${hand.handNumber}")
            )
        }
        if (state.playerHasBlackjack) Text(stringResource(R.string.natural_blackjack), color = MaterialTheme.colorScheme.primary)
        if (state.canSplit) {
            OutlinedButton(
                onClick = onSplit,
                modifier = Modifier.fillMaxWidth().testTag("split_button")
            ) {
                Text(stringResource(R.string.split_pair))
            }
        }
        state.result?.let { result ->
            CasinoPanel(Modifier.testTag("round_result")) {
                Text(stringResource(resultLabel(result.name)), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.net_change, chipText(state.chipChange ?: 0.0)))
                when {
                    state.isSaving -> Text(stringResource(R.string.saving_game))
                    state.savedGameId != null -> Text(stringResource(R.string.saved_game))
                }
            }
        }
        if (state.error == GameError.ROUND_INTERRUPTED) Text(stringResource(R.string.interrupted_round))
        if (state.error == GameError.SAVE_FAILED) {
            Text(stringResource(R.string.save_failed), color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetrySave, enabled = state.canRetrySave) { Text(stringResource(R.string.retry_save)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onHit, enabled = state.canHit, modifier = Modifier.weight(1f).testTag("hit_button")) {
                Text(stringResource(R.string.hit))
            }
            OutlinedButton(onClick = onStand, enabled = state.canStand, modifier = Modifier.weight(1f).testTag("stand_button")) {
                Text(stringResource(R.string.stand))
            }
        }
        if (state.canChangeBet) OutlinedButton(onClick = onNewGame, modifier = Modifier.testTag("change_bet_button")) {
            Text(stringResource(R.string.change_bet))
        }
    }
}

@Composable
private fun HandPanel(label: String, cards: List<Card?>, total: Int?, result: String? = null,
    modifier: Modifier = Modifier) {
    CasinoPanel(modifier) {
        ValueRow(label, stringResource(R.string.hand_total, total?.toString() ?: "—"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.forEach { PlayingCard(it) }
        }
        result?.let { Text(stringResource(resultLabel(it)), color = MaterialTheme.colorScheme.primary) }
    }
}
