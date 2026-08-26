/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
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
import com.openapps.fintrack.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
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
                TopAppBar(
                    title = { Text(stringResource(R.string.menu_summary)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                        }
                    }
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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
                        "Transactions" -> TransactionHistoryView(viewModel)
                        "Assets" -> AssetsLiabilitiesView(viewModel)
                    }
                }
            }
        }
    }
}
