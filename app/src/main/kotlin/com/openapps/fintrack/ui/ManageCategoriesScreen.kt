package com.openapps.fintrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.data.Account
import com.openapps.fintrack.data.Category

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Categories/Accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item { CategoryHeader("Income Categories") }
            items(categories.filter { it.type == "income" }) { category ->
                CategoryItem(
                    name = category.name,
                    isEnabled = category.isEnabled,
                    onEdit = {
                        viewModel.editingCategory = category
                        onEditCategory()
                    },
                    onDelete = { viewModel.deleteCategory(category) },
                    onToggle = { viewModel.toggleCategoryEnabled(category) }
                )
            }

            item { CategoryHeader("Expense Categories") }
            items(categories.filter { it.type == "expense" }) { category ->
                CategoryItem(
                    name = category.name,
                    isEnabled = category.isEnabled,
                    onEdit = {
                        viewModel.editingCategory = category
                        onEditCategory()
                    },
                    onDelete = { viewModel.deleteCategory(category) },
                    onToggle = { viewModel.toggleCategoryEnabled(category) }
                )
            }

            item { CategoryHeader("Accounts (Assets/Liabilities)") }
            items(accounts) { account ->
                CategoryItem(
                    name = account.name,
                    isEnabled = account.isEnabled,
                    onEdit = {
                        viewModel.editingAccount = account
                        onEditAccount()
                    },
                    onDelete = { viewModel.deleteAccount(account) },
                    onToggle = { viewModel.toggleAccountEnabled(account) }
                )
            }
        }
    }
}

@Composable
fun CategoryHeader(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun CategoryItem(
    name: String,
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
