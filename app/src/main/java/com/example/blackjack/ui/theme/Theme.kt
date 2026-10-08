package com.example.blackjack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CasinoColorScheme = darkColorScheme(
    primary = CasinoGold,
    onPrimary = CasinoGreen,
    secondary = CasinoCream,
    onSecondary = CasinoGreen,
    tertiary = CasinoGold,
    background = TableGreen,
    onBackground = CasinoCream,
    surface = CasinoGreen,
    onSurface = CasinoCream,
    surfaceVariant = HandGreen,
    onSurfaceVariant = MutedCream,
    outline = CasinoOutline
)

@Composable
fun BlackjackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CasinoColorScheme,
        typography = Typography,
        content = content
    )
}
