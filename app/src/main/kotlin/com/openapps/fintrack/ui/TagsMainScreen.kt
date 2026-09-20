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

package com.openapps.fintrack.ui

import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsMainScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_tags_management)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Button(
                onClick = { onNavigate("add_tag") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(stringResource(R.string.btn_create_tag))
            }
            
            Button(
                onClick = { onNavigate("manage_tags") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(stringResource(R.string.title_manage_tags))
            }
            
            Button(
                onClick = { onNavigate("summary_by_tags") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(stringResource(R.string.btn_summary_by_tags))
            }
        }
    }
}
