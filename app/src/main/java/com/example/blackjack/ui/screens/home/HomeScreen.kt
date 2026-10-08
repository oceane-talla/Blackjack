package com.example.blackjack.ui.screens.home

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.blackjack.R
import com.example.blackjack.ui.components.ScreenContent

@Composable
fun HomeScreen(onStartGame: () -> Unit, modifier: Modifier = Modifier) {
    ScreenContent(
        title = stringResource(R.string.home_title),
        description = stringResource(R.string.home_description),
        modifier = modifier
    ) {
        Button(onClick = onStartGame) {
            Text(stringResource(R.string.start_game))
        }
    }
}
