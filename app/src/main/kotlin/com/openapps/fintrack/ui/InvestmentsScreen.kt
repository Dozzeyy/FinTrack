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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.R
import com.openapps.fintrack.data.AccountBalance
import com.openapps.fintrack.data.Category
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestmentsScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
    val accounts by viewModel.getInvestmentAccounts(today).collectAsState(initial = emptyList())
    val incomeCategories by viewModel.getEnabledCategoriesByType("income").collectAsState(initial = emptyList())
    val expenseCategories by viewModel.getEnabledCategoriesByType("expense").collectAsState(initial = emptyList())

    val currentValues = remember { mutableStateMapOf<Int, String>() }
    val rowAdjustmentCategories = remember { mutableStateMapOf<Int, Category?>() }
    
    var showConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(accounts) {
        accounts.forEach { acc ->
            if (!currentValues.containsKey(acc.id)) {
                val saved = viewModel.getInvestmentValue(acc.id)
                currentValues[acc.id] = if (saved == 0.0) "" else saved.toString()
            }
        }
    }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_investments)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            accounts.forEach { acc ->
                                val value = currentValues[acc.id]?.toDoubleOrNull() ?: 0.0
                                viewModel.saveInvestmentValue(acc.id, value)
                            }
                            Toast.makeText(context, context.getString(R.string.msg_saved_success), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(stringResource(R.string.btn_save))
                    }
                    OutlinedButton(
                        onClick = {
                            val anyMissing = accounts.any { acc ->
                                val bookBalance = acc.balance.toDouble() / 100.0
                                val currentValue = currentValues[acc.id]?.toDoubleOrNull() ?: bookBalance
                                val diff = bookBalance - currentValue
                                val isGain = diff < -0.01
                                val isLoss = diff > 0.01
                                (isGain || isLoss) && rowAdjustmentCategories[acc.id] == null
                            }
                            if (anyMissing) {
                                Toast.makeText(context, context.getString(R.string.msg_adjustment_category_missing), Toast.LENGTH_LONG).show()
                            }
                            showConfirmDialog = true 
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(stringResource(R.string.btn_save_and_record))
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().horizontalScroll(scrollState)) {
            Row(
                modifier = Modifier
                    .width(700.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.label_account), modifier = Modifier.width(160.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.label_closing), modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.label_current_value), modifier = Modifier.width(140.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.label_difference), modifier = Modifier.width(120.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.label_adjustment_category), modifier = Modifier.width(160.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            Divider(modifier = Modifier.width(700.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(accounts) { acc ->
                    InvestmentRow(
                        account = acc,
                        currentValue = currentValues[acc.id] ?: "",
                        onValueChange = { currentValues[acc.id] = it },
                        incomeCategories = incomeCategories,
                        expenseCategories = expenseCategories,
                        selectedCategory = rowAdjustmentCategories[acc.id],
                        onCategorySelected = { rowAdjustmentCategories[acc.id] = it },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.btn_save_and_record)) },
            text = {
                Text(stringResource(R.string.msg_investment_record_confirm))
            },
            confirmButton = {
                Button(
                    onClick = {
                        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                        var bookedCount = 0
                        accounts.forEach { acc ->
                            val bookBalance = acc.balance.toDouble() / 100.0
                            val currentValue = currentValues[acc.id]?.toDoubleOrNull() ?: bookBalance
                            val diff = bookBalance - currentValue
                            
                            viewModel.saveInvestmentValue(acc.id, currentValue)

                            if (diff > 0.01) {
                                val expenseCat = rowAdjustmentCategories[acc.id]
                                if (expenseCat != null) {
                                    viewModel.addTransaction(
                                        date = today,
                                        time = time,
                                        accountId = acc.id,
                                        categoryId = expenseCat.id,
                                        amount = diff,
                                        note = "Investment Value Adjustment (Loss)",
                                        toAccountId = null,
                                        tags = null,
                                        type = "expense"
                                    )
                                    bookedCount++
                                }
                            } else if (diff < -0.01) {
                                val incomeCat = rowAdjustmentCategories[acc.id]
                                if (incomeCat != null) {
                                    viewModel.addTransaction(
                                        date = today,
                                        time = time,
                                        accountId = acc.id,
                                        categoryId = incomeCat.id,
                                        amount = kotlin.math.abs(diff),
                                        note = "Investment Value Adjustment (Gain)",
                                        toAccountId = null,
                                        tags = null,
                                        type = "income"
                                    )
                                    bookedCount++
                                }
                            }
                        }
                        showConfirmDialog = false
                        viewModel.triggerRefresh()
                        if (bookedCount > 0) {
                            Toast.makeText(context, context.getString(R.string.msg_saved_success), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

@Composable
fun InvestmentRow(
    account: AccountBalance,
    currentValue: String,
    onValueChange: (String) -> Unit,
    incomeCategories: List<Category>,
    expenseCategories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category) -> Unit,
    viewModel: ExpenseViewModel
) {
    val bookBalance = account.balance.toDouble() / 100.0
    val currentValDouble = currentValue.toDoubleOrNull() ?: bookBalance
    val diff = bookBalance - currentValDouble

    Row(
        modifier = Modifier
            .width(700.dp)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(account.name, modifier = Modifier.width(160.dp), style = MaterialTheme.typography.bodyMedium)
        Text(viewModel.formatAmount(account.balance), modifier = Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium)
        TextField(
            value = currentValue,
            onValueChange = onValueChange,
            modifier = Modifier.width(140.dp).padding(vertical = 4.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
        Text(
            viewModel.formatAmount(diff),
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (diff > 0.01) Color.Red else if (diff < -0.01) Color(0xFF4CAF50) else Color.Unspecified
        )
        
        Box(modifier = Modifier.width(160.dp).padding(horizontal = 4.dp)) {
            val dynamicCategories = when {
                diff > 0.01 -> expenseCategories
                diff < -0.01 -> incomeCategories
                else -> emptyList()
            }
            
            CategoryDropdown(
                label = "",
                categories = dynamicCategories,
                selectedCategory = selectedCategory,
                onCategorySelected = onCategorySelected,
                isCompact = true,
                enabled = dynamicCategories.isNotEmpty()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    label: String,
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category) -> Unit,
    isCompact: Boolean = false,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = if (enabled) (selectedCategory?.name ?: "") else "",
            onValueChange = {},
            label = if (label.isNotEmpty()) { { Text(label) } } else null,
            readOnly = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            textStyle = if (isCompact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            trailingIcon = {
                IconButton(onClick = { if (enabled) expanded = true }, enabled = enabled) {
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = if (isCompact) Color.Transparent else MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = if (isCompact) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.outlineVariant,
                disabledIndicatorColor = Color.Transparent
            )
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = {
                        onCategorySelected(cat)
                        expanded = false
                    }
                )
            }
        }
    }
}
