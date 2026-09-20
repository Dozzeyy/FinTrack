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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import com.openapps.fintrack.data.Account
import com.openapps.fintrack.data.Category
import com.openapps.fintrack.data.Party

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(
    viewModel: ExpenseViewModel,
    onEditCategory: () -> Unit,
    onEditAccount: () -> Unit,
    onBack: () -> Unit
) {
    var selectedCategoryForTxns by remember { mutableStateOf<Category?>(null) }
    var selectedAccountForTxns by remember { mutableStateOf<Account?>(null) }

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(
            viewModel = viewModel,
            onBack = { viewModel.selectedTransactionDetail = null },
            onNavigate = {},
            readOnly = true
        )
    } else if (selectedCategoryForTxns != null || selectedAccountForTxns != null) {
        BackHandler {
            selectedCategoryForTxns = null
            selectedAccountForTxns = null
        }
        Surface(modifier = Modifier.fillMaxSize()) {
            TransactionHistoryView(
                viewModel = viewModel,
                initialCategoryId = selectedCategoryForTxns?.id,
                initialAccountId = selectedAccountForTxns?.id,
                onBack = {
                    selectedCategoryForTxns = null
                    selectedAccountForTxns = null
                }
            )
        }
    } else {
        val categories by viewModel.getAllCategories().collectAsState(initial = emptyList())
        val accounts by viewModel.getAllAccounts().collectAsState(initial = emptyList())
        val expenseLabel = stringResource(R.string.label_expense)
        val incomeLabel = stringResource(R.string.label_income)
        val accountsLabel = stringResource(R.string.label_accounts)
        val filterTypes = remember { mutableStateListOf(expenseLabel, incomeLabel, accountsLabel) }
        
        var showDeleteConfirm by remember { mutableStateOf<Any?>(null) }
        var isSearchActive by remember { mutableStateOf(false) }
        var searchQuery by remember { mutableStateOf("") }

        if (showDeleteConfirm != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text(stringResource(R.string.title_confirm_delete)) },
                text = { Text(stringResource(R.string.msg_delete_cat_acc_confirm)) },
                confirmButton = {
                    Button(
                        onClick = {
                            val item = showDeleteConfirm
                            if (item is Category) {
                                viewModel.deleteCategory(item)
                            } else if (item is Account) {
                                viewModel.deleteAccount(item)
                            } else if (item is Party) {
                                viewModel.deleteParty(item)
                            }
                            showDeleteConfirm = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) { Text(stringResource(R.string.btn_delete), color = Color.White) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.btn_cancel)) }
                }
            )
        }

        Scaffold(
            topBar = {
                if (isSearchActive) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text(stringResource(R.string.label_search_cat_acc_placeholder)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.title_categories_accounts)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                            }
                        }
                    )
                }
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    FloatingActionButton(
                        onClick = { isSearchActive = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Default.Search, stringResource(R.string.btn_search))
                    }
                    FloatingActionButton(onClick = {
                        viewModel.editingCategory = null
                        viewModel.editingAccount = null
                        viewModel.editingParty = null
                        onEditCategory()
                    }) {
                        Icon(Icons.Default.Add, stringResource(R.string.btn_add_cat_acc))
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (!isSearchActive) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(expenseLabel, incomeLabel, accountsLabel).forEach { type ->
                            val isSelected = filterTypes.contains(type)
                            FilterChip(
                                selected = isSelected,
                                onClick = { 
                                    if (isSelected) filterTypes.remove(type) else filterTypes.add(type)
                                    if (filterTypes.isEmpty()) filterTypes.add(type) 
                                },
                                label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                val expenseCategories = remember(categories, searchQuery) {
                    categories.filter { it.type == "expense" && it.name.contains(searchQuery, ignoreCase = true) }.sortedBy { it.name }
                }
                val incomeCategories = remember(categories, searchQuery) {
                    categories.filter { it.type == "income" && it.name.contains(searchQuery, ignoreCase = true) }.sortedBy { it.name }
                }
                val filteredAccounts = remember(accounts, searchQuery) {
                    accounts.filter { it.name.contains(searchQuery, ignoreCase = true) }.sortedBy { it.name }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    if (filterTypes.contains(expenseLabel) && expenseCategories.isNotEmpty()) {
                        item(key = "header_expense") {
                            CategorySectionHeader("Expense Categories")
                        }
                        items(expenseCategories, key = { "cat_${it.id}" }) { item ->
                            Box(modifier = Modifier.animateContentSize()) {
                                CategoryItem(
                                    name = item.name,
                                    nature = expenseLabel,
                                    isEnabled = item.isEnabled,
                                    icon = item.icon,
                                    onClick = { selectedCategoryForTxns = item },
                                    onEdit = {
                                        viewModel.editingAccount = null
                                        viewModel.editingParty = null
                                        viewModel.editingCategory = item
                                        onEditCategory()
                                    },
                                    onDelete = { showDeleteConfirm = item },
                                    onToggle = { viewModel.toggleCategoryEnabled(item) }
                                )
                            }
                            Divider()
                        }
                    }

                    if (filterTypes.contains(incomeLabel) && incomeCategories.isNotEmpty()) {
                        item(key = "header_income") {
                            CategorySectionHeader("Income Categories")
                        }
                        items(incomeCategories, key = { "cat_${it.id}" }) { item ->
                            Box(modifier = Modifier.animateContentSize()) {
                                CategoryItem(
                                    name = item.name,
                                    nature = incomeLabel,
                                    isEnabled = item.isEnabled,
                                    icon = item.icon,
                                    onClick = { selectedCategoryForTxns = item },
                                    onEdit = {
                                        viewModel.editingAccount = null
                                        viewModel.editingParty = null
                                        viewModel.editingCategory = item
                                        onEditCategory()
                                    },
                                    onDelete = { showDeleteConfirm = item },
                                    onToggle = { viewModel.toggleCategoryEnabled(item) }
                                )
                            }
                            Divider()
                        }
                    }

                    if (filterTypes.contains(accountsLabel) && filteredAccounts.isNotEmpty()) {
                        item(key = "header_accounts") {
                            CategorySectionHeader("Accounts")
                        }
                        items(filteredAccounts, key = { "acc_${it.id}" }) { item ->
                            Box(modifier = Modifier.animateContentSize()) {
                                CategoryItem(
                                    name = item.name,
                                    nature = accountsLabel,
                                    isEnabled = item.isEnabled,
                                    icon = item.icon,
                                    onClick = { selectedAccountForTxns = item },
                                    onEdit = {
                                        viewModel.editingCategory = null
                                        viewModel.editingParty = null
                                        viewModel.editingAccount = item
                                        onEditAccount()
                                    },
                                    onDelete = { showDeleteConfirm = item },
                                    onToggle = { viewModel.toggleAccountEnabled(item) }
                                )
                            }
                            Divider()
                        }
                    }

                    item { Spacer(Modifier.height(140.dp)) }
                }
            }
        }
    }
}

@Composable
fun CategorySectionHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun CategoryItem(
    name: String,
    nature: String,
    isEnabled: Boolean,
    icon: String?,
    onClick: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = icon ?: (if (nature == "Account" || nature == stringResource(R.string.label_accounts)) "🏦" else "📁"),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = name,
                    color = if (isEnabled) Color.Unspecified else Color.Gray,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                text = nature,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            if (!isEnabled) {
                Text(text = stringResource(R.string.label_disabled), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        
        Row {
            Switch(checked = isEnabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.btn_edit))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.btn_delete))
            }
        }
    }
}
