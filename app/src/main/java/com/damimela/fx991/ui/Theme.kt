package com.damimela.fx991.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.damimela.fx991.model.ThemeChoice

/** Colours of the physical calculator (the body keeps its look in both app themes). */
object CasioColors {
    val Body = Color(0xFF26282C)
    val BodyEdge = Color(0xFF111316)
    val Lcd = Color(0xFFC9D2B8)
    val LcdInk = Color(0xFF1A2114)
    val LcdDim = Color(0xFF7E8970)
    val KeyDark = Color(0xFF3B3E44)
    val KeyDarker = Color(0xFF2E3035)
    val KeyLight = Color(0xFFE7E7E2)
    val KeyBlue = Color(0xFF3F5E8C)
    val KeyText = Color(0xFFF4F4F4)
    val KeyTextDark = Color(0xFF1C1C1C)
    val Shift = Color(0xFFF2C94C)
    val Alpha = Color(0xFFE5484D)
    val Pad = Color(0xFF33363B)
}

@Composable
fun AppTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val dark = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(background = Color(0xFF0E1013), surface = Color(0xFF16181C))
        else lightColorScheme(background = Color(0xFFE9EBEE), surface = Color(0xFFFFFFFF)),
        content = content
    )
}
