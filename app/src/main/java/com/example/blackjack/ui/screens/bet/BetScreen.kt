package com.example.blackjack.ui.screens.bet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.blackjack.R
import com.example.blackjack.ui.components.*
import com.example.blackjack.viewmodel.GameUiState
import com.example.blackjack.viewmodel.GameError
import kotlin.math.roundToInt

@Composable
fun BetScreen(state: GameUiState, onSelectBet: (Int) -> Unit, onSelectDeckCount: (Int) -> Unit, onOpenTable: () -> Unit,
    onResume: () -> Unit, onRetryBalanceLoad: () -> Unit, onResetBalance: () -> Unit,
    modifier: Modifier = Modifier) {
    var showBalanceResetDialog by rememberSaveable { mutableStateOf(false) }
    if (showBalanceResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.isResettingBalance) showBalanceResetDialog = false },
            title = { Text(stringResource(R.string.balance_reset_title)) },
            text = { Text(stringResource(R.string.balance_reset_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBalanceResetDialog = false
                        onResetBalance()
                    },
                    enabled = !state.isResettingBalance
                ) { Text(stringResource(R.string.balance_reset_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showBalanceResetDialog = false },
                    enabled = !state.isResettingBalance
                ) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
    ScreenContent(
        title = stringResource(R.string.bet_title),
        description = stringResource(R.string.bet_description),
        modifier = modifier
    ) {
        if (state.isLoadingBalance) {
            CircularProgressIndicator()
            Text(stringResource(R.string.loading_balance))
        } else if (state.error == GameError.BALANCE_LOAD_FAILED) {
            Text(stringResource(R.string.balance_load_failed), color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetryBalanceLoad) { Text(stringResource(R.string.retry_balance_load)) }
        } else CasinoPanel {
            val maxAffordableBet = state.chips.toInt().coerceIn(0, 100)
            ValueRow(stringResource(R.string.balance), chipText(state.chips))
            ValueRow(stringResource(R.string.bet), state.selectedBet.toString())
            ValueRow(stringResource(R.string.deck_count), state.selectedDeckCount.toString())
            if (state.canResetBalance) {
                Text(stringResource(R.string.balance_exhausted))
                Button(
                    onClick = { showBalanceResetDialog = true },
                    enabled = !state.isResettingBalance,
                    modifier = Modifier.fillMaxWidth().testTag("reset_balance_button")
                ) { Text(stringResource(R.string.balance_reset_action)) }
                if (state.isResettingBalance) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.error == GameError.BALANCE_RESET_FAILED) {
                    Text(stringResource(R.string.balance_reset_failed), color = MaterialTheme.colorScheme.error)
                }
            } else if (state.canChangeBet) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { onSelectDeckCount(state.selectedDeckCount - 1) },
                        enabled = state.selectedDeckCount > 1,
                        modifier = Modifier.weight(1f).testTag("fewer_decks")) {
                        Text(stringResource(R.string.fewer_decks))
                    }
                    OutlinedButton(onClick = { onSelectDeckCount(state.selectedDeckCount + 1) },
                        enabled = state.selectedDeckCount < 8,
                        modifier = Modifier.weight(1f).testTag("more_decks")) {
                        Text(stringResource(R.string.more_decks))
                    }
                }
                // Les jetons reviennent à la ligne : un seul défilement vertical suffit.
                FlowRow(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val amounts = listOf(1, 5, 10, 25, 50, 100)
                    val colors = listOf(0xFF53666D, 0xFFAD3441, 0xFF255F9C, 0xFF257044, 0xFF704391, 0xFF272D32)
                    amounts.forEachIndexed { index, amount ->
                        val isAffordable = amount <= state.chips
                        Surface(onClick = { onSelectBet(amount) }, enabled = isAffordable, shape = CircleShape,
                            modifier = Modifier.size(68.dp).testTag("chip_$amount"),
                            color = Color(colors[index]).copy(alpha = if (isAffordable) 1f else 0.38f),
                            contentColor = Color.White.copy(alpha = if (isAffordable) 1f else 0.55f),
                            border = BorderStroke(
                                if (state.selectedBet == amount && isAffordable) 3.dp else 1.dp,
                                if (state.selectedBet == amount && isAffordable) MaterialTheme.colorScheme.primary
                                else Color.White.copy(alpha = if (isAffordable) 1f else 0.55f)
                            )) {
                            BettingChipFace(
                                amount = amount,
                                baseColor = Color(colors[index]),
                                selected = state.selectedBet == amount && isAffordable,
                                enabled = isAffordable
                            )
                        }
                    }
                }
                if (maxAffordableBet >= 2) {
                    Slider(
                        value = state.selectedBet.coerceIn(1, maxAffordableBet).toFloat(),
                        onValueChange = { onSelectBet(it.roundToInt()) },
                        valueRange = 1f..maxAffordableBet.toFloat(),
                        steps = (maxAffordableBet - 2).coerceAtLeast(0),
                        modifier = Modifier.testTag("bet_slider")
                    )
                }
                Text(stringResource(R.string.bet_range), style = MaterialTheme.typography.bodySmall)
                if (state.error == GameError.BET_NOT_ALLOWED) Text(stringResource(R.string.invalid_bet), color = MaterialTheme.colorScheme.error)
                if (state.error == GameError.ROUND_INTERRUPTED) Text(stringResource(R.string.interrupted_round))
                Button(onClick = onOpenTable, enabled = state.canStartGame,
                    modifier = Modifier.fillMaxWidth().testTag("deal_button")) {
                    Text(stringResource(R.string.open_table))
                }
            } else {
                Text(stringResource(R.string.round_pending))
                Button(onClick = onResume) { Text(stringResource(R.string.resume_game)) }
            }
        }
    }
}

@Composable
private fun BettingChipFace(amount: Int, baseColor: Color, selected: Boolean, enabled: Boolean) {
    val opacity = if (enabled) 1f else 0.45f
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(3.dp)) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f
            val rimColor = baseColor.copy(alpha = opacity)

            drawCircle(color = Color(0xFF17221F).copy(alpha = opacity), radius = radius)
            drawCircle(color = rimColor, radius = radius * 0.91f)

            // Les petits segments clairs donnent au jeton un motif de couronne vu de dessus.
            repeat(12) { segment ->
                drawArc(
                    color = Color.White.copy(alpha = opacity),
                    startAngle = segment * 30f + 3f,
                    sweepAngle = 13f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius * 0.82f, center.y - radius * 0.82f),
                    size = androidx.compose.ui.geometry.Size(radius * 1.64f, radius * 1.64f),
                    style = Stroke(width = radius * 0.18f)
                )
            }
            drawCircle(
                color = if (amount == 1 || amount == 50) Color(0xFFF7F1DC) else Color(0xFF17221F),
                radius = radius * 0.62f
            )
            drawCircle(
                color = if (selected) Color(0xFFE5C76B) else Color.White.copy(alpha = 0.8f),
                radius = radius * 0.62f,
                style = Stroke(width = radius * 0.045f)
            )
        }
        Text(
            text = amount.toString(),
            color = if (amount == 1 || amount == 50) Color(0xFF17221F) else Color.White,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
