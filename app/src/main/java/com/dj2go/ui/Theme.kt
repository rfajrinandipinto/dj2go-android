package com.dj2go.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DeckAAccent = Color(0xFF2E9BFF)
val DeckBAccent = Color(0xFFFF7A2E)
val ScreenBackground = Color(0xFF07070B)
val PanelBackground = Color(0xFF12121A)
val WaveBackground = Color(0xFF0A0A12)
val MutedText = Color(0xFF8A8AA0)
val PrimaryText = Color(0xFFE6E6F0)

@Composable
fun Dj2GoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DeckAAccent,
            secondary = DeckBAccent,
            background = ScreenBackground,
            surface = PanelBackground,
            onBackground = PrimaryText,
            onSurface = PrimaryText
        ),
        content = content
    )
}
