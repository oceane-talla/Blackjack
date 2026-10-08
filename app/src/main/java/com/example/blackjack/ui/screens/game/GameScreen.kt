package com.example.blackjack.ui.screens.game

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.blackjack.R
import com.example.blackjack.ui.components.ScreenContent

@Composable
fun GameScreen(onNewGame: () -> Unit, modifier: Modifier = Modifier) {
    ScreenContent(
        title = stringResource(R.string.game_title),
        description = stringResource(R.string.game_description),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(stringResource(R.string.dealer), modifier = Modifier.padding(24.dp))
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(stringResource(R.string.player), modifier = Modifier.padding(24.dp))
        }
        OutlinedButton(onClick = onNewGame) {
            Text(stringResource(R.string.change_bet))
        }
    }
}
