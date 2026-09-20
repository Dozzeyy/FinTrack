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

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
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
    var isEmergencyFund by remember(editingCategory, editingAccount, editingParty, draft) {
        mutableStateOf(draft?.isEmergencyFund ?: editingAccount?.isEmergencyFund ?: false)
    }
    var icon by remember(editingCategory, editingAccount, editingParty, draft) {
        mutableStateOf(draft?.icon ?: editingCategory?.icon ?: editingAccount?.icon ?: "📁")
    }

    val allMajorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val allMinorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())

    var selectedMajorHeadId by remember(draft) { mutableStateOf<Int?>(draft?.selectedMajorHeadId) }
    var selectedMinorHeadId by remember(draft) { mutableStateOf<Int?>(draft?.selectedMinorHeadId) }

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
    var defaultDueDays by remember(editingAccount, draft) { mutableStateOf(draft?.defaultDueDays ?: editingAccount?.defaultDueDays?.toString() ?: "") }
    var last4Digits by remember(editingAccount, draft) { mutableStateOf(draft?.last4Digits ?: editingAccount?.last4Digits ?: "") }
    var ifscCode by remember(editingAccount, draft) { mutableStateOf(draft?.ifscCode ?: editingAccount?.ifscCode ?: "") }
    var branchName by remember(editingAccount, draft) { mutableStateOf(draft?.branchName ?: editingAccount?.branchName ?: "") }
    var websiteUrl by remember(editingAccount, draft) { mutableStateOf(draft?.websiteUrl ?: editingAccount?.websiteUrl ?: "") }
    var contactPerson by remember(editingAccount, draft) { mutableStateOf(draft?.contactPerson ?: editingAccount?.contactPerson ?: "") }
    var minimumBalance by remember(editingAccount, draft) { mutableStateOf(draft?.minimumBalance ?: editingAccount?.minimumBalance?.toString() ?: "") }
    var maturityDate by remember(editingAccount, draft) { mutableStateOf(draft?.maturityDate ?: editingAccount?.maturityDate ?: "") }
    var bankName by remember(editingAccount, draft) { mutableStateOf(draft?.bankName ?: editingAccount?.bankName ?: "") }

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
                paymentDueDate = paymentDueDate,
                icon = icon,
                isEmergencyFund = isEmergencyFund,
                defaultDueDays = defaultDueDays,
                last4Digits = last4Digits,
                ifscCode = ifscCode,
                branchName = branchName,
                websiteUrl = websiteUrl,
                contactPerson = contactPerson,
                minimumBalance = minimumBalance,
                maturityDate = maturityDate,
                bankName = bankName
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
                title = { Text(if (editingCategory != null || editingAccount != null || editingParty != null) stringResource(R.string.title_edit_item) else stringResource(R.string.title_add_item)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.editingCategory = null
                        viewModel.editingAccount = null
                        viewModel.editingParty = null
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    if (type == "accounts") {
                        TextButton(onClick = { 
                            saveAsDraft()
                            onNavigate("manage_heads") 
                        }) {
                            Text(stringResource(R.string.btn_modify_heads))
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
                label = { Text(stringResource(R.string.label_name)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            Text(stringResource(R.string.label_select_icon), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            val emojiList = listOf(
                "💰", "🏦", "💵", "💳", "📈", "📉", "🍔", "🛒", "🚗", "🏠", "📱", "💻", 
                "🎬", "🎓", "👔", "🥂", "📞", "⛽", "📦", "👨‍💼", "💹", "🍀", "🔌", "💧", 
                "💊", "🏥", "🎁", "✈️", "🏋️", "🧹", "🐾", "👤", "📁"
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(emojiList) { e ->
                    Box(
                        modifier = Modifier
                            .size(45.dp)
                            .clip(CircleShape)
                            .background(if (icon == e) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { icon = e }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(e, fontSize = 24.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            if (editingCategory == null && editingAccount == null && editingParty == null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = type == "income", onClick = { type = "income" }, label = { Text(stringResource(R.string.label_income)) })
                    FilterChip(selected = type == "expense", onClick = { type = "expense" }, label = { Text(stringResource(R.string.label_expense)) })
                    FilterChip(selected = type == "accounts", onClick = { type = "accounts" }, label = { Text(stringResource(R.string.label_account)) })
                }
            } else {
                val typeLabel = when(type) {
                    "accounts" -> stringResource(R.string.label_account)
                    "party" -> stringResource(R.string.label_payer)
                    else -> type.replaceFirstChar { it.uppercase() }
                }
                Text(stringResource(R.string.label_type_colon_val, typeLabel), modifier = Modifier.padding(vertical = 8.dp))
            }

            if (type == "accounts") {
                var majorExpanded by remember { mutableStateOf(false) }
                val majorName = allMajorHeads.find { it.id == selectedMajorHeadId }?.name ?: stringResource(R.string.label_select_major_head)
                
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedTextField(
                        value = majorName,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_major_head)) },
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

                var minorExpanded by remember { mutableStateOf(false) }
                val filteredMinors = allMinorHeads.filter { it.majorHeadId == selectedMajorHeadId }
                val minorName = filteredMinors.find { it.id == selectedMinorHeadId }?.name ?: stringResource(R.string.label_select_minor_head)

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedTextField(
                        value = minorName,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_minor_head)) },
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

                val currentMajorName = allMajorHeads.find { it.id == selectedMajorHeadId }?.name
                if (currentMajorName?.equals("credit cards", ignoreCase = true) == true) {
                    OutlinedTextField(
                        value = creditLimit,
                        onValueChange = { creditLimit = it },
                        label = { Text(stringResource(R.string.label_credit_limit)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            var startExpanded by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = billingCycleStart,
                                onValueChange = {},
                                label = { Text(stringResource(R.string.label_cycle_start_day)) },
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
                                label = { Text(stringResource(R.string.label_cycle_end_day)) },
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
                        label = { Text(stringResource(R.string.label_days_after_billing)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                if (currentMajorName?.equals("On Account (Loan)", ignoreCase = true) == true) {
                    OutlinedTextField(
                        value = defaultDueDays,
                        onValueChange = { defaultDueDays = it },
                        label = { Text(stringResource(R.string.label_default_due_days)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = CircleShape
                    )
                }

                if (currentMajorName?.equals("Investments", ignoreCase = true) == true) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Checkbox(checked = isEmergencyFund, onCheckedChange = { isEmergencyFund = it })
                        Text(stringResource(R.string.label_is_emergency_fund), modifier = Modifier.clickable { isEmergencyFund = !isEmergencyFund })
                    }

                    val selectedMinor = allMinorHeads.find { it.id == selectedMinorHeadId }
                    val minorName = selectedMinor?.name.orEmpty()
                    if (minorName.contains("Fixed Deposit", ignoreCase = true)) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Fixed Deposit (FD) Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text("Bank Name") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = CircleShape)
                        OutlinedTextField(value = branchName, onValueChange = { branchName = it }, label = { Text("Branch Name") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = CircleShape)
                        OutlinedTextField(value = contactPerson, onValueChange = { contactPerson = it }, label = { Text("Contact Person") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = CircleShape)
                    }
                }

                if (currentMajorName?.equals("Bank Accounts", ignoreCase = true) == true) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Bank Account Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = last4Digits, onValueChange = { last4Digits = it }, label = { Text("Last 4 Digits") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = CircleShape)
                    OutlinedTextField(value = ifscCode, onValueChange = { ifscCode = it }, label = { Text("IFSC Code") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = CircleShape)
                    OutlinedTextField(value = branchName, onValueChange = { branchName = it }, label = { Text("Branch Name") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = CircleShape)
                    OutlinedTextField(value = websiteUrl, onValueChange = { websiteUrl = it }, label = { Text("Website URL") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = CircleShape)
                    OutlinedTextField(value = contactPerson, onValueChange = { contactPerson = it }, label = { Text("Contact Person") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = CircleShape)
                    OutlinedTextField(value = minimumBalance, onValueChange = { minimumBalance = it }, label = { Text("Minimum Balance") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = CircleShape, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.label_description_notes)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            if (type == "income" || type == "expense") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.msg_subcat_tip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.msg_subcat_example),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            if (type == "accounts" || type == "party") {
                OutlinedTextField(
                    value = openingBalance,
                    onValueChange = { openingBalance = it },
                    label = { Text(stringResource(R.string.label_opening_balance)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    
                    if (type == "accounts") {
                        if (selectedMajorHeadId == null) {
                            Toast.makeText(context, context.getString(R.string.msg_major_head_mandatory), Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (selectedMinorHeadId == null) {
                            Toast.makeText(context, context.getString(R.string.msg_minor_head_mandatory), Toast.LENGTH_SHORT).show()
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
                            paymentDueDate = paymentDueDate,
                            icon = icon,
                            isEmergencyFund = isEmergencyFund,
                            defaultDueDays = defaultDueDays.toIntOrNull(),
                            last4Digits = last4Digits,
                            ifscCode = ifscCode,
                            branchName = branchName,
                            websiteUrl = websiteUrl,
                            contactPerson = contactPerson,
                            minimumBalance = minimumBalance.toDoubleOrNull(),
                            maturityDate = maturityDate,
                            bankName = bankName
                        )
                        viewModel.draftAccount = null
                    } else if (type == "party") {
                        viewModel.saveParty(name, openingBalance.toDoubleOrNull() ?: 0.0, isEnabled)
                        viewModel.draftAccount = null
                    } else {
                        viewModel.saveCategory(name, type, description, isEnabled, icon)
                        viewModel.draftAccount = null
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.btn_save))
            }
        }
    }
}
