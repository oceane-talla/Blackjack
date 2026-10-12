package com.example.blackjack.ui.screens.home

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.blackjack.R
import com.example.blackjack.ui.components.ScreenContent
import com.example.blackjack.ui.components.CasinoPanel
import com.example.blackjack.ui.components.PlayingCard
import com.example.blackjack.model.Card
import com.example.blackjack.model.Rank
import com.example.blackjack.model.Suit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onStartGame: () -> Unit, modifier: Modifier = Modifier) {
    ScreenContent(
        title = stringResource(R.string.home_title),
        description = stringResource(R.string.home_description),
        modifier = modifier
    ) {
        CasinoPanel {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PlayingCard(Card(Rank.ACE, Suit.SPADES))
                PlayingCard(Card(Rank.KING, Suit.HEARTS))
            }
            Text(stringResource(R.string.home_rules))
            Button(onClick = onStartGame, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.start_game))
            }
        }
    }
}
