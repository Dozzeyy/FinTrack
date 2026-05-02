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

    var name by remember { mutableStateOf(editingCategory?.name ?: editingAccount?.name ?: "") }
    var type by remember { mutableStateOf(editingCategory?.type ?: if (editingAccount != null) "accounts" else "expense") }
    var description by remember { mutableStateOf(editingCategory?.description ?: editingAccount?.description ?: "") }
    var openingBalance by remember { mutableStateOf(editingAccount?.openingBalance?.toString() ?: "0.0") }
    var isEnabled by remember { mutableStateOf(editingCategory?.isEnabled ?: editingAccount?.isEnabled ?: true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editingCategory != null || editingAccount != null) "Edit Item" else "Add Item") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.editingCategory = null
                        viewModel.editingAccount = null
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

            if (editingCategory == null && editingAccount == null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = type == "income", onClick = { type = "income" }, label = { Text("Income") })
                    FilterChip(selected = type == "expense", onClick = { type = "expense" }, label = { Text("Expense") })
                    FilterChip(selected = type == "accounts", onClick = { type = "accounts" }, label = { Text("Account") })
                }
            } else {
                Text("Type: ${type.replaceFirstChar { it.uppercase() }}", modifier = Modifier.padding(vertical = 8.dp))
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Notes)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            // User requested: "within add category option, add one more field to let user add opening balance"
            // and "within edit button... let user modify the opening balance"
            // I'll show it for all account types.
            if (type == "accounts") {
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
