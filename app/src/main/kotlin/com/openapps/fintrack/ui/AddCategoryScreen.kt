/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategoryScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val editingCategory = viewModel.editingCategory
    val editingAccount = viewModel.editingAccount
    val editingParty = viewModel.editingParty

    var name by remember { mutableStateOf(editingCategory?.name ?: editingAccount?.name ?: editingParty?.name ?: "") }
    var type by remember { mutableStateOf(editingCategory?.type ?: if (editingAccount != null) "accounts" else if (editingParty != null) "party" else "expense") }
    var description by remember { mutableStateOf(editingCategory?.description ?: editingAccount?.description ?: "") }
    var openingBalance by remember { mutableStateOf(editingAccount?.openingBalance?.toString() ?: editingParty?.openingBalance?.toString() ?: "0.0") }
    var isEnabled by remember { mutableStateOf(editingCategory?.isEnabled ?: editingAccount?.isEnabled ?: editingParty?.isEnabled ?: true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingCategory != null || editingAccount != null || editingParty != null) "Edit Item" else "Add Item") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.editingCategory = null
                        viewModel.editingAccount = null
                        viewModel.editingParty = null
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            if (editingCategory == null && editingAccount == null && editingParty == null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = type == "income", onClick = { type = "income" }, label = { Text("Income") })
                    FilterChip(selected = type == "expense", onClick = { type = "expense" }, label = { Text("Expense") })
                    FilterChip(selected = type == "accounts", onClick = { type = "accounts" }, label = { Text("Account") })
                    FilterChip(selected = type == "party", onClick = { type = "party" }, label = { Text("Payer/ee") })
                }
            } else {
                val typeLabel = when(type) {
                    "accounts" -> "Account"
                    "party" -> "Payer/ee"
                    else -> type.replaceFirstChar { it.uppercase() }
                }
                Text("Type: $typeLabel", modifier = Modifier.padding(vertical = 8.dp))
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Notes)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            // User requested: "add opening balance field for payer/ee creation (similar to accounts...)"
            if (type == "accounts" || type == "party") {
                OutlinedTextField(
                    value = openingBalance,
                    onValueChange = { openingBalance = it },
                    label = { Text("Opening Balance") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (type == "accounts") {
                        viewModel.saveAccount(name, openingBalance.toDoubleOrNull() ?: 0.0, description, isEnabled)
                    } else if (type == "party") {
                        viewModel.saveParty(name, openingBalance.toDoubleOrNull() ?: 0.0, isEnabled)
                    } else {
                        viewModel.saveCategory(name, type, description, isEnabled)
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text("Save")
            }
        }
    }
}
