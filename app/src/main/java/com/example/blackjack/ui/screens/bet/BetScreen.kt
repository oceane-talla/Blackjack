package com.example.blackjack.ui.screens.bet

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.blackjack.R
import com.example.blackjack.ui.components.ScreenContent

@Composable
fun BetScreen(onOpenTable: () -> Unit, modifier: Modifier = Modifier) {
    ScreenContent(
        title = stringResource(R.string.bet_title),
        description = stringResource(R.string.bet_description),
        modifier = modifier
    ) {
        Button(onClick = onOpenTable) {
            Text(stringResource(R.string.open_table))
        }
    }
}
