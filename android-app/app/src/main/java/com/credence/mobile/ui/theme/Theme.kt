package com.credence.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Deep teal + warm off-white — the same theme_color/background_color
// already used by the web app's PWA manifest (getPwaManifest_() in
// Code.gs), so the Android app reads as the same product rather than a
// different-looking one.
private val CredenceTeal = Color(0xFF22484A)
private val CredenceTealLight = Color(0xFF3E6B6D)
private val CredenceCream = Color(0xFFFAF8F3)
private val CredenceInk = Color(0xFF1F2A2B)

private val LightColors = lightColorScheme(
    primary = CredenceTeal,
    onPrimary = Color.White,
    secondary = CredenceTealLight,
    onSecondary = Color.White,
    background = CredenceCream,
    onBackground = CredenceInk,
    surface = Color.White,
    onSurface = CredenceInk,
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = CredenceTealLight,
    onPrimary = Color.Black,
    secondary = CredenceTeal,
    onSecondary = Color.White,
    background = Color(0xFF10191A),
    onBackground = Color(0xFFE4E7E7),
    surface = Color(0xFF172223),
    onSurface = Color(0xFFE4E7E7),
    error = Color(0xFFF2B8B5)
)

private val CredenceTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

@Composable
fun CredenceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = CredenceTypography,
        content = content
    )
}
