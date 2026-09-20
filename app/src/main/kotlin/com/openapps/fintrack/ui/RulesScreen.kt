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

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import com.openapps.fintrack.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val rules by viewModel.getAllRules().collectAsState(initial = emptyList())
    var showAddRule by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_automation_rules)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddRule = true }) {
                Icon(Icons.Default.Add, stringResource(R.string.title_add_rule))
            }
        }
    ) { padding ->
        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.msg_no_rules_defined), color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(rules) { rule ->
                    RuleItem(rule, viewModel)
                }
            }
        }

        if (showAddRule) {
            AddRuleDialog(viewModel) { showAddRule = false }
        }
    }
}

@Composable
fun RuleItem(rule: Rule, viewModel: ExpenseViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = { viewModel.saveRule(rule.copy(isEnabled = !rule.isEnabled)) }) {
                        Icon(
                            imageVector = if (rule.isEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = stringResource(R.string.label_toggle),
                            tint = if (rule.isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    IconButton(onClick = { viewModel.deleteRule(rule) }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.btn_delete), tint = Color.Red)
                    }
                }
            }
            Text(stringResource(R.string.label_from_any, rule.msgFrom ?: stringResource(R.string.label_all)), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.label_contains_text, rule.textContaining), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.label_action_type, rule.type.uppercase()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            
            if (!rule.isEnabled) {
                Text(stringResource(R.string.label_disabled_caps), style = MaterialTheme.typography.labelSmall, color = Color.Red, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleDialog(viewModel: ExpenseViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var msgFrom by remember { mutableStateOf("") }
    var textContaining by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("expense") }
    
    var selectedAccountId by remember { mutableStateOf<Int?>(null) }
    var selectedToAccountId by remember { mutableStateOf<Int?>(null) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }
    var selectedPartyId by remember { mutableStateOf<Int?>(null) }
    var selectedToPartyId by remember { mutableStateOf<Int?>(null) }
    var notes by remember { mutableStateOf("") }
    val selectedTagIds = remember { mutableStateListOf<Int>() }

    val accountsRaw by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val allMinorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())
    val allMajorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val accountBalances by viewModel.getAccountBalances(java.time.LocalDate.now().toString()).collectAsState(initial = emptyList())
    val categories by viewModel.getEnabledCategoriesByType(type).collectAsState(initial = emptyList())
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())

    val accounts = remember(accountsRaw, allMinorHeads) {
        accountsRaw.filter { a -> allMinorHeads.find { it.id == a.minorHeadId }?.majorHeadId != 6 }
    }
    val onAccountMicroAccounts = remember(accountsRaw, allMinorHeads) {
        accountsRaw.filter { a -> allMinorHeads.find { it.id == a.minorHeadId }?.majorHeadId == 6 }
    }

    var isFromOnAccountSelected by remember { mutableStateOf(false) }
    var isToOnAccountSelected by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_add_automation_rule)) },
        text = {
            Column(modifier = Modifier.verticalScroll(scrollState).fillMaxWidth()) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_rule_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = msgFrom, onValueChange = { msgFrom = it }, label = { Text(stringResource(R.string.label_msg_from_sender)) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(stringResource(R.string.label_msg_from_sender_hint)) })
                OutlinedTextField(value = textContaining, onValueChange = { textContaining = it }, label = { Text(stringResource(R.string.label_text_exactly_containing)) }, modifier = Modifier.fillMaxWidth())
                
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.label_action_details), style = MaterialTheme.typography.labelMedium)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = type == "income", onClick = { type = "income"; selectedCategoryId = null }, label = { Text(stringResource(R.string.label_income)) })
                    FilterChip(selected = type == "expense", onClick = { type = "expense"; selectedCategoryId = null }, label = { Text(stringResource(R.string.label_expense)) })
                    FilterChip(selected = type == "transfer", onClick = { type = "transfer"; selectedCategoryId = null }, label = { Text(stringResource(R.string.label_transfer)) })
                }

                if (type == "transfer") {
                    AccountSelectionDialog(
                        label = stringResource(R.string.label_from_account), accounts = accounts, balances = accountBalances, majorHeads = allMajorHeads, minorHeads = allMinorHeads,
                        viewModel = viewModel, selectedId = if(isFromOnAccountSelected) null else selectedAccountId,
                        onSelected = { selectedAccountId = it; isFromOnAccountSelected = false },
                        onOnAccountSelected = { isFromOnAccountSelected = true; selectedAccountId = null },
                        isOnAccountSelected = isFromOnAccountSelected, hasOnAccountOption = onAccountMicroAccounts.isNotEmpty(),
                        onAdd = {}
                    )
                    if (isFromOnAccountSelected) {
                        PartySelectionDialog(label = stringResource(R.string.label_from_party), parties = onAccountMicroAccounts.map { Party(it.id, it.name, it.openingBalance) }, selectedId = selectedPartyId, onSelected = { selectedPartyId = it }, onAdd = {})
                    }
                    
                    AccountSelectionDialog(
                        label = stringResource(R.string.label_to_account), accounts = accounts, balances = accountBalances, majorHeads = allMajorHeads, minorHeads = allMinorHeads,
                        viewModel = viewModel, selectedId = if(isToOnAccountSelected) null else selectedToAccountId,
                        onSelected = { selectedToAccountId = it; isToOnAccountSelected = false },
                        onOnAccountSelected = { isToOnAccountSelected = true; selectedToAccountId = null },
                        isOnAccountSelected = isToOnAccountSelected, hasOnAccountOption = onAccountMicroAccounts.isNotEmpty(),
                        onAdd = {}
                    )
                    if (isToOnAccountSelected) {
                        PartySelectionDialog(label = stringResource(R.string.label_to_party), parties = onAccountMicroAccounts.map { Party(it.id, it.name, it.openingBalance) }, selectedId = selectedToPartyId, onSelected = { selectedToPartyId = it }, onAdd = {})
                    }
                } else {
                    AccountSelectionDialog(
                        label = stringResource(R.string.label_account), accounts = accounts, balances = accountBalances, majorHeads = allMajorHeads, minorHeads = allMinorHeads,
                        viewModel = viewModel, selectedId = if(isFromOnAccountSelected) null else selectedAccountId,
                        onSelected = { selectedAccountId = it; isFromOnAccountSelected = false },
                        onOnAccountSelected = { isFromOnAccountSelected = true; selectedAccountId = null },
                        isOnAccountSelected = isFromOnAccountSelected, hasOnAccountOption = onAccountMicroAccounts.isNotEmpty(),
                        onAdd = {}
                    )
                    if (isFromOnAccountSelected) {
                        PartySelectionDialog(label = stringResource(R.string.label_party_name), parties = onAccountMicroAccounts.map { Party(it.id, it.name, it.openingBalance) }, selectedId = selectedPartyId, onSelected = { selectedPartyId = it }, onAdd = {})
                    }
                    CategorySelectionDialog(label = stringResource(R.string.label_category), categories = categories, selectedId = selectedCategoryId, onSelected = { selectedCategoryId = it }, onAdd = {})
                }

                TagSelectionPopup(allTags = allTags, selectedIds = selectedTagIds, multiSelect = viewModel.multiTagEnabled)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.label_notes)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || textContaining.isBlank()) {
                    Toast.makeText(context, context.getString(R.string.msg_fill_required), Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                val rule = Rule(
                    name = name,
                    msgFrom = msgFrom.takeIf { it.isNotBlank() },
                    textContaining = textContaining,
                    type = type,
                    categoryId = selectedCategoryId,
                    accountId = selectedAccountId,
                    toAccountId = selectedToAccountId,
                    partyId = selectedPartyId,
                    toPartyId = selectedToPartyId,
                    note = notes.takeIf { it.isNotBlank() },
                    tags = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(","),
                    isEnabled = true
                )
                viewModel.saveRule(rule)
                onDismiss()
            }) {
                Text(stringResource(R.string.btn_save_rule))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}
