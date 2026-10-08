package com.example.blackjack.ui.screens.statistics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.blackjack.R
import com.example.blackjack.ui.components.ScreenContent

@Composable
fun StatisticsScreen(modifier: Modifier = Modifier) {
    ScreenContent(
        title = stringResource(R.string.statistics_title),
        description = stringResource(R.string.statistics_description),
        modifier = modifier
    )
}
