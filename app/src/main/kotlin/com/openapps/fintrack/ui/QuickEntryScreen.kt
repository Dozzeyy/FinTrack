/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.openapps.fintrack.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.R
import com.openapps.fintrack.data.Account
import com.openapps.fintrack.data.Category
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntryScreen(
    viewModel: ExpenseViewModel,
    onExit: () -> Unit
) {
    var type by remember { mutableStateOf("expense") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedAccountId by remember { mutableStateOf<Int?>(null) }
    var selectedToAccountId by remember { mutableStateOf<Int?>(null) }
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }

    val accounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val categories by viewModel.getEnabledCategoriesByType(type).collectAsState(initial = emptyList())

    val context = LocalContext.current

    BackHandler {
        onExit()
    }

    fun reset() {
        amount = ""
        note = ""
        selectedAccountId = null
        selectedToAccountId = null
        selectedCategoryId = null
    }

    fun save(andNew: Boolean) {
        val amtValue = amount.toDoubleOrNull()
        if (amtValue == null || amtValue <= 0) {
            Toast.makeText(context, context.getString(R.string.err_total_amount_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedAccountId == null) {
            Toast.makeText(context, context.getString(R.string.err_primary_account_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (type == "transfer" && selectedToAccountId == null) {
            Toast.makeText(context, context.getString(R.string.err_destination_account_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (type != "transfer" && selectedCategoryId == null) {
            Toast.makeText(context, context.getString(R.string.err_category_required), Toast.LENGTH_SHORT).show()
            return
        }

        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

        viewModel.addTransaction(
            date = date,
            time = time,
            accountId = selectedAccountId!!,
            categoryId = selectedCategoryId,
            amount = amtValue,
            note = note,
            toAccountId = selectedToAccountId,
            tags = null,
            type = type
        )

        Toast.makeText(context, context.getString(R.string.msg_saved_success), Toast.LENGTH_SHORT).show()

        if (andNew) {
            reset()
        } else {
            onExit()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shortcut_quick_entry_short)) },
                actions = {
                    IconButton(onClick = onExit) { Icon(Icons.Default.Close, null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    selected = type == "income",
                    onClick = { type = "income"; selectedCategoryId = null },
                    label = { Text(stringResource(R.string.label_income)) }
                )
                FilterChip(
                    selected = type == "expense",
                    onClick = { type = "expense"; selectedCategoryId = null },
                    label = { Text(stringResource(R.string.label_expense)) }
                )
                FilterChip(
                    selected = type == "transfer",
                    onClick = { type = "transfer"; selectedCategoryId = null },
                    label = { Text(stringResource(R.string.label_transfer)) }
                )
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.label_amount)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(8.dp))

            SimpleAccountSelectionDialog(
                label = if (type == "transfer") stringResource(R.string.label_from_account) else stringResource(R.string.label_account),
                accounts = accounts,
                selectedId = selectedAccountId,
                onSelected = { selectedAccountId = it }
            )

            if (type == "transfer") {
                Spacer(Modifier.height(8.dp))
                SimpleAccountSelectionDialog(
                    label = stringResource(R.string.label_to_account),
                    accounts = accounts,
                    selectedId = selectedToAccountId,
                    onSelected = { selectedToAccountId = it }
                )
            } else {
                Spacer(Modifier.height(8.dp))
                SimpleCategorySelectionDialog(
                    label = stringResource(R.string.label_category),
                    categories = categories,
                    selectedId = selectedCategoryId,
                    onSelected = { selectedCategoryId = it },
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.label_note)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { save(true) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.btn_save_and_new))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { save(false) },
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            }
        }
    }
}

@Composable
fun SimpleAccountSelectionDialog(
    label: String,
    accounts: List<Account>,
    selectedId: Int?,
    onSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = accounts.find { it.id == selectedId }?.let { (it.icon ?: "🏦") + " " + it.name } ?: stringResource(R.string.label_select)

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).clickable { showDialog = true }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = Color.Transparent,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, "") }
        )
    }

    if (showDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredAccounts = remember(searchQuery, accounts) {
            if (searchQuery.isBlank()) accounts
            else accounts.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.label_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
            },
            text = {
                Box(Modifier.height(350.dp)) {
                    LazyColumn {
                        items(filteredAccounts) { account ->
                            ListItem(
                                headlineContent = { Text((account.icon ?: "🏦") + " " + account.name) },
                                modifier = Modifier.clickable {
                                    onSelected(account.id)
                                    showDialog = false
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
fun SimpleCategorySelectionDialog(
    label: String,
    categories: List<Category>,
    selectedId: Int?,
    onSelected: (Int) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selectedId }?.let { (it.icon ?: "📁") + " " + it.name } ?: stringResource(R.string.label_select)

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).clickable { showDialog = true }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = Color.Transparent,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, "") }
        )
    }

    if (showDialog) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredCategories = remember(searchQuery, categories) {
            if (searchQuery.isBlank()) categories
            else categories.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.label_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
            },
            text = {
                Box(Modifier.height(350.dp)) {
                    LazyColumn {
                        items(filteredCategories) { category ->
                            ListItem(
                                headlineContent = { Text((category.icon ?: "📁") + " " + category.name) },
                                modifier = Modifier.clickable {
                                    onSelected(category.id)
                                    showDialog = false
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}
