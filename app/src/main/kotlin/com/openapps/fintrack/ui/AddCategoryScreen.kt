/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategoryScreen(viewModel: ExpenseViewModel, onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val editingCategory = viewModel.editingCategory
    val editingAccount = viewModel.editingAccount
    val editingParty = viewModel.editingParty
    val draft = viewModel.draftAccount

    var name by remember(editingCategory, editingAccount, editingParty, draft) { 
        mutableStateOf(draft?.name ?: editingCategory?.name ?: editingAccount?.name ?: editingParty?.name ?: "") 
    }
    var type by remember(editingCategory, editingAccount, editingParty, draft) { 
        mutableStateOf(draft?.type ?: editingCategory?.type ?: if (editingAccount != null) "accounts" else if (editingParty != null) "party" else "expense") 
    }
    var description by remember(editingCategory, editingAccount, editingParty, draft) { 
        mutableStateOf(draft?.description ?: editingCategory?.description ?: editingAccount?.description ?: "") 
    }
    var openingBalance by remember(editingCategory, editingAccount, editingParty, draft) { 
        mutableStateOf(draft?.openingBalance ?: editingAccount?.openingBalance?.toString() ?: editingParty?.openingBalance?.toString() ?: "0.0") 
    }
    var isEnabled by remember(editingCategory, editingAccount, editingParty, draft) { 
        mutableStateOf(draft?.isEnabled ?: editingCategory?.isEnabled ?: editingAccount?.isEnabled ?: editingParty?.isEnabled ?: true) 
    }

    // New fields
    val allMajorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val allMinorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())

    var selectedMajorHeadId by remember(draft) { mutableStateOf<Int?>(draft?.selectedMajorHeadId) }
    var selectedMinorHeadId by remember(draft) { mutableStateOf<Int?>(draft?.selectedMinorHeadId) }

    // Initialize selection when editing or when lists load
    LaunchedEffect(editingAccount, allMajorHeads, allMinorHeads) {
        if (draft != null) return@LaunchedEffect
        
        if (editingAccount != null && allMinorHeads.isNotEmpty()) {
            val minor = allMinorHeads.find { it.id == editingAccount.minorHeadId }
            if (minor != null) {
                selectedMinorHeadId = minor.id
                selectedMajorHeadId = minor.majorHeadId
            }
        } else if (selectedMajorHeadId == null && allMajorHeads.isNotEmpty()) {
            selectedMajorHeadId = allMajorHeads.find { it.name == "Others" }?.id ?: allMajorHeads.firstOrNull()?.id
        }
    }

    var creditLimit by remember(editingAccount, draft) { mutableStateOf(draft?.creditLimit ?: editingAccount?.creditLimit?.toString() ?: "") }
    var billingCycleStart by remember(editingAccount, draft) { mutableStateOf(draft?.billingCycleStart ?: editingAccount?.billingCycleStart ?: "") }
    var billingCycleEnd by remember(editingAccount, draft) { mutableStateOf(draft?.billingCycleEnd ?: editingAccount?.billingCycleEnd ?: "") }
    var paymentDueDate by remember(editingAccount, draft) { mutableStateOf(draft?.paymentDueDate ?: editingAccount?.paymentDueDate ?: "") }

    val context = LocalContext.current

    fun saveAsDraft() {
        if (editingCategory == null && editingAccount == null && editingParty == null) {
            viewModel.draftAccount = DraftAccount(
                name = name,
                type = type,
                description = description,
                openingBalance = openingBalance,
                isEnabled = isEnabled,
                selectedMajorHeadId = selectedMajorHeadId,
                selectedMinorHeadId = selectedMinorHeadId,
                creditLimit = creditLimit,
                billingCycleStart = billingCycleStart,
                billingCycleEnd = billingCycleEnd,
                paymentDueDate = paymentDueDate
            )
        }
    }

    BackHandler {
        saveAsDraft()
        onBack()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
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
                },
                actions = {
                    if (type == "accounts") {
                        TextButton(onClick = { 
                            saveAsDraft()
                            onNavigate("manage_heads") 
                        }) {
                            Text("Modify Heads")
                        }
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
                .verticalScroll(rememberScrollState())
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
                }
            } else {
                val typeLabel = when(type) {
                    "accounts" -> "Account"
                    "party" -> "Payer/ee"
                    else -> type.replaceFirstChar { it.uppercase() }
                }
                Text("Type: $typeLabel", modifier = Modifier.padding(vertical = 8.dp))
            }

            if (type == "accounts") {
                // Major Head Dropdown
                var majorExpanded by remember { mutableStateOf(false) }
                val majorName = allMajorHeads.find { it.id == selectedMajorHeadId }?.name ?: "Select Major Head"
                
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedTextField(
                        value = majorName,
                        onValueChange = {},
                        label = { Text("Major Head") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { majorExpanded = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    )
                    DropdownMenu(
                        expanded = majorExpanded, 
                        onDismissRequest = { majorExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        allMajorHeads.forEach { head ->
                            DropdownMenuItem(
                                text = { Text(head.name) },
                                onClick = {
                                    selectedMajorHeadId = head.id
                                    selectedMinorHeadId = null
                                    majorExpanded = false
                                }
                            )
                        }
                    }
                }

                // Minor Head Dropdown
                var minorExpanded by remember { mutableStateOf(false) }
                val filteredMinors = allMinorHeads.filter { it.majorHeadId == selectedMajorHeadId }
                val minorName = filteredMinors.find { it.id == selectedMinorHeadId }?.name ?: "Select Minor Head"

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedTextField(
                        value = minorName,
                        onValueChange = {},
                        label = { Text("Minor Head") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { minorExpanded = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    )
                    DropdownMenu(
                        expanded = minorExpanded, 
                        onDismissRequest = { minorExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        filteredMinors.forEach { head ->
                            DropdownMenuItem(
                                text = { Text(head.name) },
                                onClick = {
                                    selectedMinorHeadId = head.id
                                    minorExpanded = false
                                }
                            )
                        }
                    }
                }

                // Credit Card specific fields
                val currentMajorName = allMajorHeads.find { it.id == selectedMajorHeadId }?.name
                if (currentMajorName?.equals("credit cards", ignoreCase = true) == true) {
                    OutlinedTextField(
                        value = creditLimit,
                        onValueChange = { creditLimit = it },
                        label = { Text("Credit Limit") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            var startExpanded by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = billingCycleStart,
                                onValueChange = {},
                                label = { Text("Cycle Start (Day)") },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().clickable { startExpanded = true },
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                            )
                            DropdownMenu(expanded = startExpanded, onDismissRequest = { startExpanded = false }) {
                                (1..31).forEach { day ->
                                    DropdownMenuItem(text = { Text(day.toString()) }, onClick = { billingCycleStart = day.toString(); startExpanded = false })
                                }
                            }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            var endExpanded by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = billingCycleEnd,
                                onValueChange = {},
                                label = { Text("Cycle End (Day)") },
                                readOnly = true,
                                modifier = Modifier.fillMaxWidth().clickable { endExpanded = true },
                                enabled = false,
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                            )
                            DropdownMenu(expanded = endExpanded, onDismissRequest = { endExpanded = false }) {
                                (1..31).forEach { day ->
                                    DropdownMenuItem(text = { Text(day.toString()) }, onClick = { billingCycleEnd = day.toString(); endExpanded = false })
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = paymentDueDate,
                        onValueChange = { paymentDueDate = it },
                        label = { Text("Days after billing cycle ends") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Notes)") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            if (type == "income" || type == "expense") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Tip: If you want to track expenses under sub-categories, keep the category description in the format - Main: Minor.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Example - Food: Dining, Food: Snacks, Health: Medicines, Health: Consultation etc.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

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
                    if (name.isBlank()) return@Button
                    
                    if (type == "accounts") {
                        if (selectedMajorHeadId == null) {
                            Toast.makeText(context, "Major Head is mandatory for accounts", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (selectedMinorHeadId == null) {
                            Toast.makeText(context, "Minor Head is mandatory for accounts", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.saveAccount(
                            name = name, 
                            openingBalance = openingBalance.toDoubleOrNull() ?: 0.0, 
                            description = description, 
                            isEnabled = isEnabled,
                            minorHeadId = selectedMinorHeadId,
                            creditLimit = creditLimit.toDoubleOrNull(),
                            billingCycleStart = billingCycleStart,
                            billingCycleEnd = billingCycleEnd,
                            paymentDueDate = paymentDueDate
                        )
                        viewModel.draftAccount = null
                    } else if (type == "party") {
                        viewModel.saveParty(name, openingBalance.toDoubleOrNull() ?: 0.0, isEnabled)
                        viewModel.draftAccount = null
                    } else {
                        viewModel.saveCategory(name, type, description, isEnabled)
                        viewModel.draftAccount = null
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
