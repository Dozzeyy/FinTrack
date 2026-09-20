/*
 * FinTrack
 * Copyright (C) 2026 Bhuvan (app.upstream242@passmail.com)
 * SPDX-License-Identifier: GPL-3.0-or-later

 This program is free software; you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation; either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.
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
        "OLED" -> darkColorScheme(
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
            @Suppress("DEPRECATION")
            window.statusBarColor = baseScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = baseScheme,
        content = content
    )
}
