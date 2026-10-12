package com.example.blackjack.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.blackjack.R
import com.example.blackjack.model.Card
import com.example.blackjack.model.GameResult
import com.example.blackjack.model.Rank
import com.example.blackjack.model.Suit
import java.text.NumberFormat

// La mise en forme dépend de la langue du téléphone, sans modifier les valeurs du modèle.
fun chipText(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
}.format(value)

fun resultLabel(result: String): Int = when (result) {
    GameResult.WIN.name -> R.string.result_win
    GameResult.LOSS.name -> R.string.result_loss
    GameResult.DRAW.name -> R.string.result_draw
    else -> R.string.result_unknown
}

@Composable
fun CasinoPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PlayingCard(card: Card?, modifier: Modifier = Modifier) {
    val suitName = when (card?.suit) {
        Suit.HEARTS -> R.string.suit_hearts
        Suit.DIAMONDS -> R.string.suit_diamonds
        Suit.CLUBS -> R.string.suit_clubs
        else -> R.string.suit_spades
    }
    val rank = when (card?.rank) {
        Rank.ACE -> "A"
        Rank.JACK -> "J"
        Rank.QUEEN -> "Q"
        Rank.KING -> "K"
        null -> ""
        else -> card.rank.baseValue.toString()
    }
    val description = if (card == null) stringResource(R.string.hidden_card)
        else stringResource(R.string.card_description, rank, stringResource(suitName))
    val suitImage = when (card?.suit) {
        Suit.HEARTS -> R.drawable.card_hearts
        Suit.DIAMONDS -> R.drawable.card_diamonds
        Suit.CLUBS -> R.drawable.card_clubs
        else -> R.drawable.card_spades
    }
    val ink = if (card?.suit == Suit.HEARTS || card?.suit == Suit.DIAMONDS)
        Color(0xFFB32632) else Color(0xFF182520)
    // Une carte null ne révèle ni son rang ni sa sorte dans l'accessibilité.
    Surface(modifier.size(78.dp, 112.dp).semantics(mergeDescendants = true) {
        contentDescription = description
    }, shape = RoundedCornerShape(8.dp), color = Color(0xFFFFFAEE),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary), shadowElevation = 3.dp) {
        Box {
            if (card == null) {
                Image(painterResource(R.drawable.card_back), null, Modifier.fillMaxSize().padding(4.dp))
            } else {
                Text(rank, Modifier.align(Alignment.TopStart).padding(7.dp), color = ink,
                    style = MaterialTheme.typography.titleMedium)
                Image(painterResource(suitImage), null, Modifier.size(32.dp).align(Alignment.Center))
                Text(rank, Modifier.align(Alignment.BottomEnd).padding(7.dp), color = ink,
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
