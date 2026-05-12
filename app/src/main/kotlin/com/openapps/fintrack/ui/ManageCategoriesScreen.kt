/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

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
import androidx.compose.ui.unit.dp
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
    val categories by viewModel.getAllCategories().collectAsState(initial = emptyList())
    val accounts by viewModel.getAllAccounts().collectAsState(initial = emptyList())
    val parties by viewModel.getAllParties().collectAsState(initial = emptyList())
    val filterTypes = remember { mutableStateListOf("Income", "Expense", "Accounts", "Payer/ee") }
    
    var showDeleteConfirm by remember { mutableStateOf<Any?>(null) } // Category, Account or Party
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Confirm Deletion") },
            text = { Text("Are you sure you want to delete this? Warning: All existing records for this account or category will be deleted from the transactions list.") },
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
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
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
                            placeholder = { Text("Search categories/accounts...") },
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
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("Manage Categories/Accounts") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                    Icon(Icons.Default.Search, "Search")
                }
                FloatingActionButton(onClick = {
                    viewModel.editingCategory = null
                    onEditCategory()
                }) {
                    Icon(Icons.Default.Add, "Add Category/Account")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!isSearchActive) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Income", "Expense", "Accounts", "Payer/ee").forEach { type ->
                        val isSelected = filterTypes.contains(type)
                        FilterChip(
                            selected = isSelected,
                            onClick = { 
                                if (isSelected) filterTypes.remove(type) else filterTypes.add(type)
                                if (filterTypes.isEmpty()) filterTypes.add(type) // Ensure at least one is selected
                            },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            val displayedItems = remember(categories, accounts, parties, filterTypes.toList(), searchQuery, isSearchActive) {
                val list = mutableListOf<Any>()
                
                val filteredCategories = categories.filter { 
                    (filterTypes.contains("Income") && it.type == "income") || 
                    (filterTypes.contains("Expense") && it.type == "expense")
                }.filter { it.name.contains(searchQuery, ignoreCase = true) }

                val filteredAccounts = if (filterTypes.contains("Accounts")) {
                    accounts.filter { it.name.contains(searchQuery, ignoreCase = true) }
                } else emptyList()

                val filteredParties = if (filterTypes.contains("Payer/ee")) {
                    parties.filter { it.name.contains(searchQuery, ignoreCase = true) }
                } else emptyList()

                // Sort: Expense, Income, Accounts, Parties
                list.addAll(filteredCategories.filter { it.type == "expense" }.sortedBy { it.name })
                list.addAll(filteredCategories.filter { it.type == "income" }.sortedBy { it.name })
                list.addAll(filteredAccounts.sortedBy { it.name })
                list.addAll(filteredParties.sortedBy { it.name })
                
                list
            }

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(displayedItems, key = { 
                    when(it) {
                        is Category -> "cat_${it.id}"
                        is Account -> "acc_${it.id}"
                        is Party -> "party_${it.id}"
                        else -> it.hashCode()
                    }
                }) { item ->
                    Box(modifier = Modifier.animateContentSize()) {
                        when (item) {
                            is Category -> {
                                CategoryItem(
                                    name = item.name,
                                    nature = if (item.type == "income") "Income" else "Expense",
                                    isEnabled = item.isEnabled,
                                    onEdit = {
                                        viewModel.editingCategory = item
                                        onEditCategory()
                                    },
                                    onDelete = { showDeleteConfirm = item },
                                    onToggle = { viewModel.toggleCategoryEnabled(item) }
                                )
                            }
                            is Account -> {
                                CategoryItem(
                                    name = item.name,
                                    nature = "Account",
                                    isEnabled = item.isEnabled,
                                    onEdit = {
                                        viewModel.editingAccount = item
                                        onEditAccount()
                                    },
                                    onDelete = { showDeleteConfirm = item },
                                    onToggle = { viewModel.toggleAccountEnabled(item) }
                                )
                            }
                            is Party -> {
                                CategoryItem(
                                    name = item.name,
                                    nature = "Payer/ee",
                                    isEnabled = item.isEnabled,
                                    onEdit = {
                                        viewModel.editingParty = item
                                        onEditCategory() // Re-using add category screen for party too? Or need new one.
                                    },
                                    onDelete = { showDeleteConfirm = item },
                                    onToggle = { viewModel.togglePartyEnabled(item) }
                                )
                            }
                        }
                    }
                    Divider()
                }
                
                item { Spacer(Modifier.height(140.dp)) }
            }
        }
    }
}

@Composable
fun CategoryItem(
    name: String,
    nature: String,
    isEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = if (isEnabled) Color.Unspecified else Color.Gray,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = nature,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            if (!isEnabled) {
                Text(text = "Disabled", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        
        Row {
            Switch(checked = isEnabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}
