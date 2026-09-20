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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.R
import com.openapps.fintrack.data.Account
import com.openapps.fintrack.data.Goal
import com.openapps.fintrack.data.GoalAccountAllocation
import com.openapps.fintrack.data.GoalRule
import com.openapps.fintrack.data.TransactionWithDetails
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun getTimeRemainingText(targetDateMillis: Long?): String {
    if (targetDateMillis == null) return stringResource(R.string.goal_no_deadline)
    val now = System.currentTimeMillis()
    val diffDays = ((targetDateMillis - now) / (1000 * 60 * 60 * 24)).toInt()
    return when {
        diffDays < 0 -> stringResource(R.string.goal_expired)
        diffDays == 0 -> stringResource(R.string.goal_due_today)
        diffDays < 30 -> stringResource(R.string.goal_days_left, diffDays)
        diffDays < 365 -> stringResource(R.string.goal_months_left, diffDays / 30)
        else -> stringResource(R.string.goal_years_left, diffDays / 365)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GoalsScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    isEmbedded: Boolean = false
) {
    val goals by viewModel.allGoals.collectAsState(initial = emptyList())
    val allocations by viewModel.allAllocations.collectAsState(initial = emptyList())
    val accounts by viewModel.getAllAccounts().collectAsState(initial = emptyList())
    val rules by viewModel.allGoalRules.collectAsState(initial = emptyList())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())

    var selectedGoal by remember { mutableStateOf<Goal?>(null) }
    var selectedGoalForDeletion by remember { mutableStateOf<Goal?>(null) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var showRulesScreen by remember { mutableStateOf(false) }
    var showAccountBalancesDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedGoal != null || showRulesScreen || selectedGoalForDeletion != null) {
        if (selectedGoal != null) {
            selectedGoal = null
        } else if (showRulesScreen) {
            showRulesScreen = false
        } else if (selectedGoalForDeletion != null) {
            selectedGoalForDeletion = null
        }
    }

    val sortedGoals = remember(goals) {
        goals.sortedBy { it.targetDate ?: Long.MAX_VALUE }
    }

    val totalTarget = sortedGoals.sumOf { it.targetAmount }
    val totalSaved = allocations.sumOf { it.allocatedAmount }
    val overallProgress = if (totalTarget > 0.0) (totalSaved / totalTarget).toFloat().coerceIn(0f, 1f) else 0f

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (!isEmbedded || selectedGoal != null || showRulesScreen || selectedGoalForDeletion != null) {
                TopAppBar(
                    title = { Text(if (selectedGoal == null) stringResource(R.string.goal_title_financial_goals) else selectedGoal!!.name) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedGoal != null) selectedGoal = null
                            else if (showRulesScreen) showRulesScreen = false
                            else onBack()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                        }
                    },
                    actions = {
                        if (selectedGoalForDeletion != null) {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.goal_options))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.goal_delete_goal), color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        selectedGoalForDeletion?.let { viewModel.deleteGoal(it) }
                                        selectedGoalForDeletion = null
                                        menuExpanded = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                                )
                            }
                        } else if (selectedGoal == null && !showRulesScreen) {
                            IconButton(onClick = { showRulesScreen = true }) {
                                Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.goal_recurring_rules))
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isEmbedded && selectedGoal == null && !showRulesScreen && selectedGoalForDeletion == null) {
                FloatingActionButton(onClick = { showAddGoalDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.goal_add_goal))
                }
            }
        }
    ) { padding ->
        val contentPadding = if (isEmbedded && selectedGoal == null && !showRulesScreen && selectedGoalForDeletion == null) PaddingValues(0.dp) else padding
        if (showRulesScreen) {
            GoalRulesManagerScreen(viewModel = viewModel, goals = sortedGoals, accounts = accounts, rules = rules, modifier = Modifier.padding(contentPadding))
        } else if (selectedGoal != null) {
            val goalTxns by viewModel.getTransactionsForGoal(selectedGoal!!.id).collectAsState(initial = emptyList())
            GoalDetailView(
                goal = selectedGoal!!,
                allocations = allocations.filter { it.goalId == selectedGoal!!.id },
                accounts = accounts,
                transactions = goalTxns,
                viewModel = viewModel,
                onNavigate = onNavigate,
                modifier = Modifier.padding(contentPadding)
            )
        } else {
            val bottomPadding = if (isEmbedded) 88.dp else 16.dp
            LazyColumn(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = if (isEmbedded) 0.dp else 16.dp, end = 16.dp, bottom = bottomPadding)
            ) {
                if (isEmbedded) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = { showRulesScreen = true }) {
                                Icon(Icons.Default.Schedule, contentDescription = stringResource(R.string.goal_recurring_rules))
                            }
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.goal_total_savings_progress), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.goal_saved_format, viewModel.formatAmount(totalSaved)))
                                Text(stringResource(R.string.goal_target_format, viewModel.formatAmount(totalTarget)))
                            }
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = overallProgress,
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showAccountBalancesDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.goal_see_account_balances))
                            }
                        }
                    }
                }

                if (sortedGoals.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.goal_no_goals_created), color = Color.Gray)
                        }
                    }
                }

                items(sortedGoals) { goal ->
                    val goalAllocations = allocations.filter { it.goalId == goal.id }
                    val saved = goalAllocations.sumOf { it.allocatedAmount }
                    val progress = if (goal.targetAmount > 0.0) (saved / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                    val timeRemaining = getTimeRemainingText(goal.targetDate)
                    val isSelectedForDelete = selectedGoalForDeletion?.id == goal.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .combinedClickable(
                                onClick = {
                                    if (selectedGoalForDeletion != null) {
                                        selectedGoalForDeletion = null
                                    } else {
                                        selectedGoal = goal
                                    }
                                },
                                onLongClick = {
                                    selectedGoalForDeletion = goal
                                }
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelectedForDelete) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(goal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.goal_target_format, viewModel.formatAmount(goal.targetAmount)) + " | " + stringResource(R.string.goal_saved_format, viewModel.formatAmount(saved)), style = MaterialTheme.typography.bodyMedium)
                            Text(timeRemaining, style = MaterialTheme.typography.labelSmall, color = if (timeRemaining == stringResource(R.string.goal_expired)) MaterialTheme.colorScheme.error else Color.Gray)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddGoalDialog) {
        AddGoalDialog(viewModel = viewModel, onDismiss = { showAddGoalDialog = false })
    }

    if (showAccountBalancesDialog) {
        AccountBalancesDialog(
            accounts = accounts,
            goals = sortedGoals,
            allocations = allocations,
            viewModel = viewModel,
            onDismiss = { showAccountBalancesDialog = false }
        )
    }
}

@Composable
fun AddGoalDialog(viewModel: ExpenseViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_create_financial_goal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.goal_name_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.goal_target_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deadline,
                    onValueChange = { deadline = it },
                    label = { Text(stringResource(R.string.goal_target_date_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetAmount = target.toDoubleOrNull() ?: 0.0
                    val targetMillis = try {
                        if (deadline.isNotBlank()) java.time.LocalDate.parse(deadline).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() else null
                    } catch (e: Exception) { null }

                    if (name.isNotBlank() && targetAmount > 0.0) {
                        viewModel.saveGoal(0, name, targetAmount, targetMillis, "flag", 0xFF4CAF50.toInt())
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank() && target.toDoubleOrNull() != null
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun GoalDetailView(
    goal: Goal,
    allocations: List<GoalAccountAllocation>,
    accounts: List<Account>,
    transactions: List<TransactionWithDetails>,
    viewModel: ExpenseViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSaved = allocations.sumOf { it.allocatedAmount }
    val progress = if (goal.targetAmount > 0.0) (totalSaved / goal.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
    var showAllocateDialog by remember { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    val accountBalances by viewModel.getAccountBalances(java.time.LocalDate.now().toString()).collectAsState(initial = emptyList())
    val allocationHistory by viewModel.getAllocationHistoryForGoal(goal.id).collectAsState(initial = emptyList())

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(goal.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.goal_target_format, viewModel.formatAmount(goal.targetAmount)), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.goal_saved_format, viewModel.formatAmount(totalSaved)) + " (${(progress * 100).toInt()}%)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { showAllocateDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.AccountBalanceWallet, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.goal_allocate_funds))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showTransferDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.SwapHoriz, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.goal_transfer_funds_between_accounts))
                    }
                }
            }
        }

        item {
            Text(stringResource(R.string.goal_accounts_funding), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
        }

        if (allocations.isEmpty()) {
            item {
                Text(stringResource(R.string.goal_no_funds_allocated), color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        items(allocations) { allocation ->
            val account = accounts.find { it.id == allocation.accountId }
            if (account != null) {
                val actualBalMinor = accountBalances.find { it.id == account.id }?.balance ?: 0L
                val actualBal = actualBalMinor.toDouble() / 100.0
                val allocated = allocation.allocatedAmount
                val trueBal = actualBal - allocated

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(R.string.goal_actual_balance, viewModel.formatAmount(actualBal)), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.goal_allocated_to_goal, viewModel.formatAmount(allocated)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.goal_true_balance_available, viewModel.formatAmount(trueBal)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        IconButton(onClick = { viewModel.removeGoalAllocation(goal.id, account.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.goal_allocation_history), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.goal_allocation_history_desc), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
        }

        if (allocationHistory.isEmpty()) {
            item {
                Text(stringResource(R.string.goal_no_allocation_history), color = Color.Gray)
            }
        }

        items(allocationHistory) { history ->
            val acc = accounts.find { it.id == history.accountId }
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.goal_source_allocation, history.source), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        val dateStr = try {
                            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(java.time.Instant.ofEpochMilli(history.timestamp).atZone(java.time.ZoneId.systemDefault()))
                        } catch (e: Exception) { "" }
                        Text(stringResource(R.string.goal_history_date_account, dateStr, acc?.name ?: stringResource(R.string.goal_unknown)), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = viewModel.formatAmount(history.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.goal_transaction_visibility), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.goal_tap_transaction_details), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(Modifier.height(8.dp))
        }

        if (transactions.isEmpty()) {
            item {
                Text(stringResource(R.string.goal_no_transactions_linked), color = Color.Gray)
            }
        }

        items(transactions) { txn ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        viewModel.selectedTransactionDetail = txn
                        onNavigate("add_transaction")
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(txn.transaction.note ?: txn.categoryName ?: stringResource(R.string.label_transaction), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.goal_txn_date_account, txn.transaction.date, txn.accountName), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = viewModel.formatAmount(txn.transaction.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    if (showAllocateDialog) {
        AllocateFundsDialog(goal = goal, accounts = accounts, allocations = allocations, viewModel = viewModel, onDismiss = { showAllocateDialog = false })
    }

    if (showTransferDialog) {
        TransferAllocationDialog(goal = goal, accounts = accounts, allocations = allocations, viewModel = viewModel, onDismiss = { showTransferDialog = false })
    }
}

@Composable
fun TransferAllocationDialog(
    goal: Goal,
    accounts: List<Account>,
    allocations: List<GoalAccountAllocation>,
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    var fromAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var toAccountId by remember { mutableStateOf(accounts.firstOrNull { it.id != fromAccountId }?.id ?: accounts.firstOrNull()?.id) }
    var amountInput by remember { mutableStateOf("") }
    val fromAlloc = allocations.find { it.accountId == fromAccountId }?.allocatedAmount ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_transfer_allocation_title, goal.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var fromExp by remember { mutableStateOf(false) }
                val fromAcc = accounts.find { it.id == fromAccountId }

                Text(stringResource(R.string.goal_from_account_allocated, viewModel.formatAmount(fromAlloc)), style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { fromExp = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(fromAcc?.name ?: stringResource(R.string.goal_select_source_account))
                    }
                    DropdownMenu(expanded = fromExp, onDismissRequest = { fromExp = false }) {
                        accounts.forEach { acc ->
                            val allocAmt = allocations.find { it.accountId == acc.id }?.allocatedAmount ?: 0.0
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.goal_account_allocated_format, acc.name, viewModel.formatAmount(allocAmt))) },
                                onClick = {
                                    fromAccountId = acc.id
                                    fromExp = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                var toExp by remember { mutableStateOf(false) }
                val toAcc = accounts.find { it.id == toAccountId }

                Text(stringResource(R.string.goal_to_account), style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { toExp = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(toAcc?.name ?: stringResource(R.string.goal_select_destination_account))
                    }
                    DropdownMenu(expanded = toExp, onDismissRequest = { toExp = false }) {
                        accounts.filter { it.id != fromAccountId }.forEach { acc ->
                            val allocAmt = allocations.find { it.accountId == acc.id }?.allocatedAmount ?: 0.0
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.goal_account_allocated_format, acc.name, viewModel.formatAmount(allocAmt))) },
                                onClick = {
                                    toAccountId = acc.id
                                    toExp = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(stringResource(R.string.goal_transfer_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountInput.toDoubleOrNull() ?: 0.0
                    if (fromAccountId != null && toAccountId != null && amount > 0.0 && amount <= fromAlloc) {
                        viewModel.transferGoalAllocation(goal.id, fromAccountId!!, toAccountId!!, amount)
                        onDismiss()
                    }
                },
                enabled = fromAccountId != null && toAccountId != null && (amountInput.toDoubleOrNull() ?: 0.0) > 0.0 && (amountInput.toDoubleOrNull() ?: 0.0) <= fromAlloc
            ) {
                Text(stringResource(R.string.goal_btn_transfer))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun AllocateFundsDialog(
    goal: Goal,
    accounts: List<Account>,
    allocations: List<GoalAccountAllocation>,
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var amountInput by remember { mutableStateOf("") }
    val accountBalances by viewModel.getAccountBalances(java.time.LocalDate.now().toString()).collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_allocate_funds_title, goal.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var accExpanded by remember { mutableStateOf(false) }
                val selectedAccount = accounts.find { it.id == selectedAccountId }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { accExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedAccount?.name ?: stringResource(R.string.goal_select_account))
                    }
                    DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                        accounts.forEach { acc ->
                            val accBal = accountBalances.find { it.id == acc.id }?.balance?.toDouble()?.div(100.0) ?: 0.0
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.goal_account_balance_format, acc.name, viewModel.formatAmount(accBal))) },
                                onClick = {
                                    selectedAccountId = acc.id
                                    accExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(stringResource(R.string.goal_amount_to_allocate)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountInput.toDoubleOrNull() ?: 0.0
                    if (selectedAccountId != null && amount > 0.0) {
                        viewModel.allocateFundsToGoal(goal.id, selectedAccountId!!, amount)
                        onDismiss()
                    }
                },
                enabled = selectedAccountId != null && amountInput.toDoubleOrNull() != null
            ) {
                Text(stringResource(R.string.goal_btn_allocate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun GoalRulesManagerScreen(
    viewModel: ExpenseViewModel,
    goals: List<Goal>,
    accounts: List<Account>,
    rules: List<GoalRule>,
    modifier: Modifier = Modifier
) {
    var showAddRuleDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.goal_recurring_auto_contributions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showAddRuleDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.goal_add_rule))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (rules.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.goal_no_rules_set), color = Color.Gray)
                    }
                }
            }

            items(rules) { rule ->
                val goal = goals.find { it.id == rule.goalId }
                val account = accounts.find { it.id == rule.accountId }

                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(goal?.name ?: stringResource(R.string.goal_unknown_goal), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.goal_rule_account_amt, account?.name ?: stringResource(R.string.goal_unknown), viewModel.formatAmount(rule.amount)), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.goal_rule_freq_day, rule.frequency, rule.triggerDay), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { viewModel.toggleGoalRuleEnabled(rule) }
                            )
                            IconButton(onClick = { viewModel.deleteGoalRule(rule) }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddRuleDialog) {
        AddGoalRuleDialog(viewModel = viewModel, goals = goals, accounts = accounts, onDismiss = { showAddRuleDialog = false })
    }
}

@Composable
fun AddGoalRuleDialog(
    viewModel: ExpenseViewModel,
    goals: List<Goal>,
    accounts: List<Account>,
    onDismiss: () -> Unit
) {
    var selectedGoalId by remember { mutableStateOf(goals.firstOrNull()?.id) }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }
    var amountInput by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("Monthly") }
    var triggerDayInput by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_add_recurring_contribution)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var goalExp by remember { mutableStateOf(false) }
                val selGoal = goals.find { it.id == selectedGoalId }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { goalExp = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selGoal?.name ?: stringResource(R.string.goal_select_goal))
                    }
                    DropdownMenu(expanded = goalExp, onDismissRequest = { goalExp = false }) {
                        goals.forEach { g ->
                            DropdownMenuItem(text = { Text(g.name) }, onClick = { selectedGoalId = g.id; goalExp = false })
                        }
                    }
                }

                var accExp by remember { mutableStateOf(false) }
                val selAcc = accounts.find { it.id == selectedAccountId }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { accExp = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selAcc?.name ?: stringResource(R.string.goal_select_account))
                    }
                    DropdownMenu(expanded = accExp, onDismissRequest = { accExp = false }) {
                        accounts.forEach { a ->
                            DropdownMenuItem(text = { Text(a.name) }, onClick = { selectedAccountId = a.id; accExp = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(stringResource(R.string.label_amount)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                var freqExp by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { freqExp = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.goal_frequency_label, frequency))
                    }
                    DropdownMenu(expanded = freqExp, onDismissRequest = { freqExp = false }) {
                        listOf("Daily", "Weekly", "Monthly", "Quarterly", "Half-Yearly", "Yearly").forEach { f ->
                            DropdownMenuItem(text = { Text(f) }, onClick = { frequency = f; freqExp = false })
                        }
                    }
                }

                OutlinedTextField(
                    value = triggerDayInput,
                    onValueChange = { triggerDayInput = it },
                    label = { Text(stringResource(R.string.goal_trigger_day_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountInput.toDoubleOrNull() ?: 0.0
                    val day = triggerDayInput.toIntOrNull() ?: 1
                    if (selectedGoalId != null && selectedAccountId != null && amount > 0.0) {
                        viewModel.saveGoalRule(GoalRule(goalId = selectedGoalId!!, accountId = selectedAccountId!!, amount = amount, frequency = frequency, triggerDay = day))
                        onDismiss()
                    }
                },
                enabled = selectedGoalId != null && selectedAccountId != null && amountInput.toDoubleOrNull() != null
            ) {
                Text(stringResource(R.string.goal_save_rule))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun AccountBalancesDialog(
    accounts: List<Account>,
    goals: List<Goal>,
    allocations: List<GoalAccountAllocation>,
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    val accountBalances by viewModel.getAccountBalances(java.time.LocalDate.now().toString()).collectAsState(initial = emptyList())

    val accountsWithAllocations = remember(accounts, allocations) {
        accounts.filter { account ->
            allocations.any { it.accountId == account.id && it.allocatedAmount > 0.0 }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_account_balances_allocations_title)) },
        text = {
            if (accountsWithAllocations.isEmpty()) {
                Text(stringResource(R.string.goal_no_funds_allocated), color = Color.Gray)
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(accountsWithAllocations) { account ->
                        val actualBalMinor = accountBalances.find { it.id == account.id }?.balance ?: 0L
                        val actualBal = actualBalMinor.toDouble() / 100.0
                        val accountAllocations = allocations.filter { it.accountId == account.id && it.allocatedAmount > 0.0 }
                        val totalAllocated = accountAllocations.sumOf { it.allocatedAmount }
                        val trueAvailable = actualBal - totalAllocated

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(account.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.goal_actual_account_balance, viewModel.formatAmount(actualBal)), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.goal_allocations_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                accountAllocations.forEach { alloc ->
                                    val goalName = goals.find { it.id == alloc.goalId }?.name ?: stringResource(R.string.goal_unknown_goal)
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("• $goalName", style = MaterialTheme.typography.bodySmall)
                                        Text(viewModel.formatAmount(alloc.allocatedAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    }
                                }
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    stringResource(R.string.goal_true_available_balance, viewModel.formatAmount(trueAvailable)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (trueAvailable < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

