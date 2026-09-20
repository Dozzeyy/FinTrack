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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.R
import com.openapps.fintrack.data.SmsTransactionDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsInboxScreen(
    viewModel: ExpenseViewModel, 
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val drafts by viewModel.smsDrafts.collectAsState(initial = emptyList())
    val accounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val categories by viewModel.getEnabledCategories().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_sms_inbox)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        if (drafts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Sms, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.msg_sms_inbox_empty), color = Color.Gray)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                items(drafts) { draft ->
                    SmsDraftRow(draft, accounts, categories, viewModel, onNavigate)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsDraftRow(
    draft: SmsTransactionDraft,
    accounts: List<com.openapps.fintrack.data.Account>,
    categories: List<com.openapps.fintrack.data.Category>,
    viewModel: ExpenseViewModel,
    onNavigate: (String) -> Unit
) {
    var showRecordDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { showRecordDialog = true }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(draft.merchantName ?: draft.sender, fontWeight = FontWeight.Bold)
                Text(viewModel.formatAmount(draft.amount), color = if (draft.type == "income") Color(0xFF4CAF50) else Color.Red, fontWeight = FontWeight.Bold)
            }
            Text(draft.date + " " + draft.time, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text(draft.body, style = MaterialTheme.typography.bodySmall)
            
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.deleteSmsDraft(draft) }) {
                    Text(stringResource(R.string.btn_dismiss), color = Color.Gray)
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val defaultAcc = draft.accountId ?: accounts.firstOrNull()?.id
                        val defaultCat = categories.find { it.type == draft.type }?.id
                        viewModel.draftTransaction = DraftTransaction(
                            type = draft.type,
                            amount = draft.amount.toString(),
                            note = draft.body,
                            date = draft.date,
                            time = draft.time,
                            accountId = defaultAcc,
                            toAccountId = null,
                            categoryId = defaultCat,
                            selectedTagIds = emptyList(),
                            merchantName = draft.merchantName ?: ""
                        )
                        viewModel.deleteSmsDraft(draft)
                        onNavigate("add_transaction")
                    }
                ) {
                    Text(stringResource(R.string.btn_modify))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showRecordDialog = true }) {
                    Text(stringResource(R.string.btn_record))
                }
            }
        }
    }

    if (showRecordDialog) {
        var selectedAccountId by remember { mutableIntStateOf(draft.accountId ?: accounts.firstOrNull()?.id ?: 0) }
        var selectedCategoryId by remember { mutableStateOf(categories.find { it.type == draft.type }) }

        AlertDialog(
            onDismissRequest = { showRecordDialog = false },
            title = { Text(stringResource(R.string.btn_record_transaction)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stringResource(R.string.msg_confirm_sms_record, viewModel.formatAmount(draft.amount)))
                    
            
                    var accountExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { accountExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(accounts.find { it.id == selectedAccountId }?.name ?: "Select Account")
                        }
                        DropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.name) }, onClick = { selectedAccountId = acc.id; accountExpanded = false })
                            }
                        }
                    }

                    var categoryExpanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { categoryExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedCategoryId?.name ?: "Select Category")
                        }
                        DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                            categories.filter { it.type == draft.type }.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name) }, onClick = { selectedCategoryId = cat; categoryExpanded = false })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.recordFromSmsDraft(draft, selectedAccountId, selectedCategoryId?.id, null)
                    showRecordDialog = false
                }, enabled = selectedAccountId != 0 && selectedCategoryId != null) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRecordDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}
