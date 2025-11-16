package com.aichallengekmp.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = OzonBlue,
    onPrimary = Color.White,
    secondary = OzonMagenta,
    onSecondary = Color.White,
    background = Neutral10,
    onBackground = Neutral60,
    surface = OzonSurface,
    onSurface = Neutral60,
    error = Color(0xFFB00020),
)

private val DarkColors = darkColorScheme(
    primary = OzonMorningBlue,
    onPrimary = Color.Black,
    secondary = OzonMagenta,
    onSecondary = Color.Black,
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF8),
    surface = Color(0xFF07102A),
    onSurface = Color(0xFFE6EDF8),
    error = Color(0xFFCF6679)
)

@Composable
fun ChatTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
//        typography = Typography, // your typography
//        shapes = Shapes,         // your shapes
        content = content
    )
}