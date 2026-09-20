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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(viewModel: ExpenseViewModel, onBack: () -> Unit, isEmbedded: Boolean = false) {
    var currentSubView by remember { mutableStateOf(viewModel.summaryInitialTab) }
    
    LaunchedEffect(Unit) {
    }
    
    if (viewModel.selectedTransactionDetail != null) {
        BackHandler {
            viewModel.selectedTransactionDetail = null
        }
        AddTransactionScreen(
            viewModel = viewModel,
            onBack = { viewModel.selectedTransactionDetail = null },
            initialData = null,
            readOnly = true
        )
    } else {
        Scaffold(
            topBar = {
                if (!isEmbedded) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.menu_summary)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                            }
                        }
                    )
                }
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            val contentPadding = if (isEmbedded) PaddingValues(0.dp) else padding
            Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val transactionsTab = stringResource(R.string.menu_transactions)
                    val assetsTab = stringResource(R.string.menu_assets)
                    FilterChip(selected = currentSubView == "Transactions" || currentSubView == transactionsTab, onClick = { currentSubView = "Transactions" }, label = { Text(transactionsTab) })
                    FilterChip(selected = currentSubView == "Assets" || currentSubView == assetsTab, onClick = { currentSubView = "Assets" }, label = { Text(assetsTab) })
                }
                
                AnimatedContent(
                    targetState = currentSubView,
                    transitionSpec = {
                        fadeIn().togetherWith(fadeOut())
                    },
                    label = "SummaryTabTransition"
                ) { subView ->
                    when (subView) {
                        "Transactions" -> TransactionHistoryView(viewModel, isEmbedded = isEmbedded)
                        "Assets" -> AssetsLiabilitiesView(viewModel, isEmbedded = isEmbedded)
                    }
                }
            }
        }
    }
}
