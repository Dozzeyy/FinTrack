/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()
private val OledDarkColorScheme = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun FinTrackTheme(
    theme: String = "Dark",
    primaryColor: Int = 0xFFE91E63.toInt(),
    content: @Composable () -> Unit
) {
    val darkTheme = theme != "Light"
    val pColor = Color(primaryColor)
    
    val baseScheme = when (theme) {
        "Light" -> lightColorScheme(
            primary = pColor,
            onPrimary = Color.White,
            primaryContainer = pColor.copy(alpha = 0.1f),
            onPrimaryContainer = pColor
        )
        "OLED Dark" -> darkColorScheme(
            primary = pColor,
            onPrimary = Color.Black,
            primaryContainer = pColor.copy(alpha = 0.2f),
            onPrimaryContainer = pColor,
            background = Color.Black,
            surface = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White
        )
        else -> darkColorScheme(
            primary = pColor,
            onPrimary = Color.Black,
            primaryContainer = pColor.copy(alpha = 0.2f),
            onPrimaryContainer = pColor
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor = baseScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = baseScheme,
        content = content
    )
}
