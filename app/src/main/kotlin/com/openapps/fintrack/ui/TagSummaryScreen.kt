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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagSummaryScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    var startDate by remember { mutableStateOf(LocalDate.now().minusMonths(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var showFilterDialog by remember { mutableStateOf(false) }
    
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val selectedTagIds = remember { mutableStateListOf<Int>() }
    
    val transactions by viewModel.getTransactionsByTags(selectedTagIds.toList(), startDate, endDate).collectAsState(initial = emptyList())
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { exportToUri(context, transactions, "CSV", it) }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.btn_summary_by_tags)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(stringResource(R.string.label_select_tags_colon), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            
            FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                allTags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in selectedTagIds,
                        onClick = {
                            if (tag.id in selectedTagIds) selectedTagIds.remove(tag.id)
                            else selectedTagIds.add(tag.id)
                        },
                        label = { Text(tag.name) },
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_num_transactions, transactions.size), style = MaterialTheme.typography.labelLarge)
                Row {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.label_filter))
                    }
                    IconButton(onClick = { 
                        exportLauncher.launch("tags_summary_${LocalDate.now()}.csv")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.label_export))
                    }
                }
            }
            
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(transactions) { detail ->
                    TransactionRow(detail = detail, viewModel = viewModel)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
    
    if (showFilterDialog) {
        DateRangeFilterDialog(
            onDismiss = { showFilterDialog = false },
            onApply = { start, end ->
                startDate = start
                endDate = end
                showFilterDialog = false
            }
        )
    }
}
