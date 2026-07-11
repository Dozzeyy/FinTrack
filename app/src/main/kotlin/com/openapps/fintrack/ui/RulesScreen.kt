/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
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
                title = { Text("Automation Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddRule = true }) {
                Icon(Icons.Default.Add, "Add Rule")
            }
        }
    ) { padding ->
        if (rules.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No rules defined. Add one to automate recording.", color = Color.Gray)
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
                            contentDescription = "Toggle",
                            tint = if (rule.isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                    IconButton(onClick = { viewModel.deleteRule(rule) }) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                    }
                }
            }
            Text("From: ${rule.msgFrom ?: "Any"}", style = MaterialTheme.typography.bodySmall)
            Text("Contains: \"${rule.textContaining}\"", style = MaterialTheme.typography.bodySmall)
            Text("Action: ${rule.type.uppercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            
            if (!rule.isEnabled) {
                Text("DISABLED", style = MaterialTheme.typography.labelSmall, color = Color.Red, fontWeight = FontWeight.ExtraBold)
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
        title = { Text("Add Automation Rule") },
        text = {
            Column(modifier = Modifier.verticalScroll(scrollState).fillMaxWidth()) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Rule Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = msgFrom, onValueChange = { msgFrom = it }, label = { Text("Msg From (Sender)") }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("e.g. ICICI") })
                OutlinedTextField(value = textContaining, onValueChange = { textContaining = it }, label = { Text("Text Exactly Containing") }, modifier = Modifier.fillMaxWidth())
                
                Spacer(Modifier.height(16.dp))
                Text("Action Details", style = MaterialTheme.typography.labelMedium)
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = type == "income", onClick = { type = "income"; selectedCategoryId = null }, label = { Text("Income") })
                    FilterChip(selected = type == "expense", onClick = { type = "expense"; selectedCategoryId = null }, label = { Text("Expense") })
                    FilterChip(selected = type == "transfer", onClick = { type = "transfer"; selectedCategoryId = null }, label = { Text("Transfer") })
                }

                if (type == "transfer") {
                    AccountSelectionDialog(
                        label = "From Account", accounts = accounts, balances = accountBalances, majorHeads = allMajorHeads, minorHeads = allMinorHeads,
                        viewModel = viewModel, selectedId = if(isFromOnAccountSelected) null else selectedAccountId,
                        onSelected = { selectedAccountId = it; isFromOnAccountSelected = false },
                        onOnAccountSelected = { isFromOnAccountSelected = true; selectedAccountId = null },
                        isOnAccountSelected = isFromOnAccountSelected, hasOnAccountOption = onAccountMicroAccounts.isNotEmpty(),
                        onAdd = {}
                    )
                    if (isFromOnAccountSelected) {
                        PartySelectionDialog(label = "From Party", parties = onAccountMicroAccounts.map { Party(it.id, it.name, it.openingBalance) }, selectedId = selectedPartyId, onSelected = { selectedPartyId = it }, onAdd = {})
                    }
                    
                    AccountSelectionDialog(
                        label = "To Account", accounts = accounts, balances = accountBalances, majorHeads = allMajorHeads, minorHeads = allMinorHeads,
                        viewModel = viewModel, selectedId = if(isToOnAccountSelected) null else selectedToAccountId,
                        onSelected = { selectedToAccountId = it; isToOnAccountSelected = false },
                        onOnAccountSelected = { isToOnAccountSelected = true; selectedToAccountId = null },
                        isOnAccountSelected = isToOnAccountSelected, hasOnAccountOption = onAccountMicroAccounts.isNotEmpty(),
                        onAdd = {}
                    )
                    if (isToOnAccountSelected) {
                        PartySelectionDialog(label = "To Party", parties = onAccountMicroAccounts.map { Party(it.id, it.name, it.openingBalance) }, selectedId = selectedToPartyId, onSelected = { selectedToPartyId = it }, onAdd = {})
                    }
                } else {
                    AccountSelectionDialog(
                        label = "Account", accounts = accounts, balances = accountBalances, majorHeads = allMajorHeads, minorHeads = allMinorHeads,
                        viewModel = viewModel, selectedId = if(isFromOnAccountSelected) null else selectedAccountId,
                        onSelected = { selectedAccountId = it; isFromOnAccountSelected = false },
                        onOnAccountSelected = { isFromOnAccountSelected = true; selectedAccountId = null },
                        isOnAccountSelected = isFromOnAccountSelected, hasOnAccountOption = onAccountMicroAccounts.isNotEmpty(),
                        onAdd = {}
                    )
                    if (isFromOnAccountSelected) {
                        PartySelectionDialog(label = "Party", parties = onAccountMicroAccounts.map { Party(it.id, it.name, it.openingBalance) }, selectedId = selectedPartyId, onSelected = { selectedPartyId = it }, onAdd = {})
                    }
                    CategorySelectionDialog(label = "Category", categories = categories, selectedId = selectedCategoryId, onSelected = { selectedCategoryId = it }, onAdd = {})
                }

                TagSelectionPopup(allTags = allTags, selectedIds = selectedTagIds, multiSelect = viewModel.multiTagEnabled)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank() || textContaining.isBlank()) {
                    Toast.makeText(context, "Please fill required fields", Toast.LENGTH_SHORT).show()
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
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
