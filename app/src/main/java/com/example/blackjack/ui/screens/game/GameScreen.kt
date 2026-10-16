package com.example.blackjack.ui.screens.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.blackjack.R
import com.example.blackjack.model.Card
import com.example.blackjack.model.GamePhase
import com.example.blackjack.ui.components.*
import com.example.blackjack.viewmodel.GameUiState
import com.example.blackjack.viewmodel.GameError
import kotlinx.coroutines.delay

@Composable
fun GameScreen(state: GameUiState, onHit: () -> Unit, onStand: () -> Unit, onSplit: () -> Unit,
    onRetrySave: () -> Unit, onNewGame: () -> Unit, onReplaySameBet: () -> Unit,
    modifier: Modifier = Modifier) {
    var shoePosition by remember { mutableStateOf<Offset?>(null) }
    var discardPosition by remember { mutableStateOf<Offset?>(null) }
    val isFinished = state.phase == GamePhase.FINISHED && state.result != null
    var collected by rememberSaveable(state.roundId) { mutableStateOf(false) }
    val collection = remember(state.roundId) { Animatable(if (collected) 1f else 0f) }
    LaunchedEffect(state.roundId, isFinished, discardPosition != null) {
        if (isFinished && !collected && discardPosition != null) {
            // Laisser lire la dernière carte avant de ramasser les mains.
            delay(900)
            collection.animateTo(1f, tween(700))
            collected = true
        }
    }
    val cardsOnTable = state.dealerCards.size + state.playerHands.sumOf { it.cards.size }
    val visibleDiscardCount = (state.discardedCardCount -
        if (isFinished && !collected) cardsOnTable else 0).coerceAtLeast(0)
    Column(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
        Color(0xFF062C21), Color(0xFF17613A), Color(0xFF0A3E29)
    ))).border(2.dp, Color(0xFFBDA06A), RoundedCornerShape(24.dp))
        .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.bet_amount, state.roundBet ?: state.selectedBet))
            Text(stringResource(R.string.balance_amount, chipText(state.chips)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BLACKJACK", color = Color(0xFFE2C584), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.game_title), style = MaterialTheme.typography.labelLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // Le point de départ est mesuré : il reste juste en portrait et en paysage.
                Box(Modifier.clearAndSetSemantics { }.background(Color(0xFF493220), RoundedCornerShape(10.dp))
                    .border(2.dp, Color(0xFFBDA06A), RoundedCornerShape(10.dp)).padding(8.dp)) {
                    PlayingCard(null, Modifier.size(48.dp, 68.dp).onGloballyPositioned {
                        shoePosition = it.positionInRoot()
                    })
                }
                DiscardPile(visibleDiscardCount) { discardPosition = it }
            }
        }
        key(state.roundId) {
        if (!collected) {
            HandPanel(stringResource(R.string.dealer), state.dealerCards, state.dealerTotal,
                discardPosition = discardPosition, collectionProgress = collection.value,
                shoePosition = shoePosition, dealer = true)
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
                    shoePosition = shoePosition,
                    discardPosition = discardPosition, collectionProgress = collection.value,
                    active = hand.handNumber - 1 == state.activeHandIndex && state.phase == GamePhase.PLAYER_TURN,
                    split = state.playerHands.size > 1,
                    modifier = Modifier.testTag("player_hand_${hand.handNumber}")
                )
            }
        }
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
                if (state.savedGameId != null && state.canStartGame) {
                    Button(
                        onClick = onReplaySameBet,
                        enabled = collected,
                        modifier = Modifier.fillMaxWidth().testTag("replay_same_bet_button")
                    ) {
                        Text("${stringResource(R.string.new_game)} (${stringResource(
                            R.string.bet_amount, state.roundBet ?: state.selectedBet
                        )})")
                    }
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
        if (state.canChangeBet) OutlinedButton(onClick = onNewGame,
            enabled = !isFinished || collected, modifier = Modifier.testTag("change_bet_button")) {
            Text(stringResource(R.string.change_bet))
        }
    }
}

@Composable
private fun DiscardPile(cardCount: Int, onPosition: (Offset) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.testTag("discard_pile")) {
        Box(Modifier.size(60.dp, 80.dp).onGloballyPositioned { onPosition(it.positionInRoot()) }) {
            // Une épaisseur progressive, plafonnée pour garder la pile dans son emplacement.
            val layers = if (cardCount == 0) 0 else (1 + cardCount / 4).coerceAtMost(12)
            repeat(layers) { index ->
                PlayingCard(null, Modifier.offset(x = index.dp, y = index.dp)
                    .size(48.dp, 68.dp))
            }
        }
        Text(stringResource(R.string.discard_title), style = MaterialTheme.typography.labelSmall,
            color = Color(0xFFE2C584))
    }
}

@Composable
private fun HandPanel(label: String, cards: List<Card?>, total: Int?, result: String? = null,
    discardPosition: Offset? = null, collectionProgress: Float = 0f,
    shoePosition: Offset? = null, dealer: Boolean = false, active: Boolean = false, split: Boolean = false,
    modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()
        .border(if (active) 2.dp else 1.dp,
            Color(0xFFE2C584).copy(alpha = if (active) 1f else 0.3f), RoundedCornerShape(20.dp))
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ValueRow(label, stringResource(R.string.hand_total, total?.toString() ?: "—"))
        BoxWithConstraints(Modifier.fillMaxWidth().height(112.dp)) {
            // Le chevauchement garde les mains longues dans la largeur disponible.
            val step = if (cards.size <= 1) 0.dp else minOf(46.dp, (maxWidth - 78.dp) / (cards.size - 1))
            val start = (maxWidth - 78.dp - step * (cards.size - 1).coerceAtLeast(0)) / 2
            cards.forEachIndexed { index, card ->
                DealtPlayingCard(card, shoePosition,
                    discardPosition = discardPosition, collectionProgress = collectionProgress,
                    delayMillis = if (index < 2 && !split) (index * 2 + if (dealer) 1 else 0) * 160 else 0,
                    animate = !(split && index == 0),
                    modifier = Modifier.offset(x = start + step * index).zIndex(index.toFloat()))
            }
        }
        result?.let { Text(stringResource(resultLabel(it)), color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun DealtPlayingCard(card: Card?, shoePosition: Offset?, delayMillis: Int,
    discardPosition: Offset?, collectionProgress: Float,
    animate: Boolean, modifier: Modifier = Modifier) {
    var destination by remember { mutableStateOf<Offset?>(null) }
    var delivered by rememberSaveable { mutableStateOf(!animate) }
    val progress = remember { Animatable(if (delivered) 1f else 0f) }
    LaunchedEffect(shoePosition != null, destination != null) {
        if (!delivered && shoePosition != null && destination != null) {
            progress.animateTo(1f, tween(durationMillis = 420, delayMillis = delayMillis))
            delivered = true
        }
    }
    // Seul le dessin se déplace. Le modèle reste la source des cartes et des résultats.
    Box(modifier.size(78.dp, 112.dp).onGloballyPositioned { destination = it.positionInRoot() }) {
        PlayingCard(card, Modifier.graphicsLayer {
            val origin = shoePosition
            val target = destination
            val remaining = 1f - progress.value
            alpha = if (origin == null || target == null) 0f else 1f
            translationX = if (origin != null && target != null) (origin.x - target.x) * remaining else 0f
            translationY = if (origin != null && target != null) (origin.y - target.y) * remaining else 0f
            scaleX = 1f - 0.38f * remaining
            scaleY = scaleX
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            if (collectionProgress > 0f && discardPosition != null && target != null) {
                translationX = (discardPosition.x - target.x) * collectionProgress
                translationY = (discardPosition.y - target.y) * collectionProgress
                scaleX = 1f - (1f - 48f / 78f) * collectionProgress
                scaleY = 1f - (1f - 68f / 112f) * collectionProgress
            }
        })
    }
}
