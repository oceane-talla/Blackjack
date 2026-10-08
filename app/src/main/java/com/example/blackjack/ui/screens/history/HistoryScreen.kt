package com.example.blackjack.ui.screens.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.blackjack.R
import com.example.blackjack.ui.components.ScreenContent

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    ScreenContent(
        title = stringResource(R.string.history_title),
        description = stringResource(R.string.history_description),
        modifier = modifier
    )
}
