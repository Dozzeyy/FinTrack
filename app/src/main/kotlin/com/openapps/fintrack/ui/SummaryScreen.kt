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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    var currentSubView by remember { mutableStateOf(viewModel.summaryInitialTab) }
    
    // Clear initial state once used
    LaunchedEffect(Unit) {
        // We keep it until explicitly changed by navigation logic if needed, 
        // but for now, reset to default for next entry
        // viewModel.summaryInitialTab = "Transactions"
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
                    title = { Text("Summary") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = currentSubView == "Transactions", onClick = { currentSubView = "Transactions" }, label = { Text("Transactions") })
                    FilterChip(selected = currentSubView == "Assets", onClick = { currentSubView = "Assets" }, label = { Text("Assets/Liabilities") })
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
