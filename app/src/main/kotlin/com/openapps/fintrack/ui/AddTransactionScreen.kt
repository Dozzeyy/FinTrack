/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapps.fintrack.data.Template
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

data class MultiEntryRow(
    val id: String = UUID.randomUUID().toString(),
    var categoryId: Int?,
    var amount: String,
    var note: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: ExpenseViewModel, 
    onBack: () -> Unit, 
    onNavigate: ((String) -> Unit)? = null,
    initialData: android.os.Bundle? = null,
    readOnly: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val txnDetail = viewModel.selectedTransactionDetail
    val draft = viewModel.draftTransaction
    val editingTemplate = viewModel.editingTemplate
    
    val isTemplateMode = initialData?.getBoolean("template_mode", false) ?: false
    var templateName by remember { mutableStateOf(editingTemplate?.name ?: "") }
    var showNameDialog by remember { mutableStateOf(false) }
    var showTemplateSelection by remember { mutableStateOf(false) }

    var isMultiEntry by remember { mutableStateOf(draft?.isMultiEntry ?: (editingTemplate?.multiEntries != null)) }
    val multiEntryRows = remember { mutableStateListOf<MultiEntryRow>() }
    
    LaunchedEffect(draft, editingTemplate) {
        if (multiEntryRows.isNotEmpty()) return@LaunchedEffect

        if (draft != null && draft.isMultiEntry) {
            multiEntryRows.clear()
            multiEntryRows.addAll(draft.multiEntryRows.map { MultiEntryRow(categoryId = it.categoryId, amount = it.amount, note = it.note) })
        } else if (editingTemplate?.multiEntries != null) {
            multiEntryRows.clear()
            editingTemplate.multiEntries.split("|").forEach { entry ->
                val parts = entry.split(":")
                if (parts.size >= 2) {
                    multiEntryRows.add(MultiEntryRow(categoryId = parts[0].toIntOrNull(), amount = parts[1], note = parts.getOrNull(2)))
                }
            }
        }
        
        if (multiEntryRows.isEmpty()) {
            multiEntryRows.add(MultiEntryRow(categoryId = null, amount = ""))
        }
    }

    var type by remember { mutableStateOf(draft?.type ?: editingTemplate?.type ?: txnDetail?.categoryType ?: txnDetail?.let { "transfer" } ?: "expense") }
    
    val initialAmount = draft?.amount ?: editingTemplate?.amount?.toString() ?: txnDetail?.transaction?.amount?.toString() ?: initialData?.getDouble("amount", 0.0)?.takeIf { it > 0 }?.toString() ?: ""
    val initialNote = draft?.note ?: editingTemplate?.note ?: txnDetail?.transaction?.note ?: initialData?.getString("sms_body") ?: ""
    val initialDate = draft?.date ?: txnDetail?.transaction?.date ?: initialData?.getString("date")
    val initialTime = draft?.time ?: txnDetail?.transaction?.time ?: initialData?.getString("time")

    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(initialNote) }
    
    val calendar = Calendar.getInstance()
    var date by remember { mutableStateOf(initialDate ?: String.format("%d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))) }
    var time by remember { mutableStateOf(initialTime ?: String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))) }

    val accounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val categories by viewModel.getEnabledCategoriesByType(type).collectAsState(initial = emptyList())
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val allParties by viewModel.getEnabledParties().collectAsState(initial = emptyList())

    var selectedAccountId by remember { mutableStateOf(draft?.accountId ?: editingTemplate?.accountId ?: txnDetail?.transaction?.accountId) }
    var selectedToAccountId by remember { mutableStateOf(draft?.toAccountId ?: editingTemplate?.toAccountId ?: txnDetail?.transaction?.toAccountId) }
    var selectedCategoryId by remember { mutableStateOf(draft?.categoryId ?: editingTemplate?.categoryId ?: txnDetail?.transaction?.categoryId) }
    var selectedPartyId by remember { mutableStateOf(txnDetail?.transaction?.partyId) }
    var selectedToPartyId by remember { mutableStateOf(txnDetail?.transaction?.toPartyId) }
    
    val selectedAccountName = accounts.find { it.id == selectedAccountId }?.name ?: txnDetail?.accountName ?: ""
    val selectedToAccountName = accounts.find { it.id == selectedToAccountId }?.name ?: txnDetail?.toAccountName ?: ""
    
    val isFromOnAccount = selectedAccountName.equals("On Account", ignoreCase = true)
    val isToOnAccount = selectedToAccountName.equals("On Account", ignoreCase = true)
    
    // For archived view (readOnly), use the partyName from detail if available
    val displayPartyName = txnDetail?.partyName ?: allParties.find { it.id == selectedPartyId }?.name ?: "Select"
    val displayToPartyName = txnDetail?.toPartyName ?: allParties.find { it.id == selectedToPartyId }?.name ?: "Select"
    
    // Validation states
    var amountError by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var toAccountError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }

    val selectedTagIds = remember { 
        val list = mutableStateListOf<Int>()
        val fromDraft = draft?.selectedTagIds
        val fromTemplate = editingTemplate?.tags?.split(",")?.mapNotNull { it.toIntOrNull() }
        if (fromDraft != null) {
            list.addAll(fromDraft)
        } else if (fromTemplate != null) {
            list.addAll(fromTemplate)
        } else {
            txnDetail?.transaction?.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { list.addAll(it) }
        }
        list
    }

    var showCalculator by remember { mutableStateOf(false) }
    var showMismatchDialog by remember { mutableStateOf(false) }
    var mismatchTotals by remember { mutableStateOf(Pair(0.0, 0.0)) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun saveAsDraft() {
        if (!readOnly && !isTemplateMode) {
            viewModel.draftTransaction = com.openapps.fintrack.ui.DraftTransaction(
                type = type,
                amount = amount,
                note = note,
                date = date,
                time = time,
                accountId = selectedAccountId,
                toAccountId = selectedToAccountId,
                categoryId = selectedCategoryId,
                selectedTagIds = selectedTagIds.toList(),
                isMultiEntry = isMultiEntry,
                multiEntryRows = multiEntryRows.map { DraftMultiEntryRow(it.categoryId, it.amount, it.note) }
            )
        }
    }

    LaunchedEffect(Unit) {
        if (draft == null && !isTemplateMode && !readOnly) {
            saveAsDraft()
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
                title = { Text(if (isTemplateMode) "Add Template" else if (readOnly) "Transaction Details" else "Add Transaction") },
                navigationIcon = {
                    IconButton(onClick = { saveAsDraft(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val templates by viewModel.getAllTemplates().collectAsState(initial = emptyList())

        if (showMismatchDialog) {
            AlertDialog(
                onDismissRequest = { showMismatchDialog = false },
                title = { Text("Total Mismatch") },
                text = { Text("The total amount (${mismatchTotals.first}) does not match the sum of row amounts (${mismatchTotals.second}). Total amount will be updated to match the rows.") },
                confirmButton = {
                    TextButton(onClick = { 
                        amount = mismatchTotals.second.toString()
                        showMismatchDialog = false
                    }) { Text("Update Total") }
                }
            )
        }

        if (showNameDialog) {
            var inputName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showNameDialog = false },
                title = { Text("Template Name") },
                text = {
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Name the template") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        if (inputName.isNotBlank()) {
                            val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                            val multiStr = if (isMultiEntry) {
                                multiEntryRows.joinToString("|") { "${it.categoryId}:${it.amount}:${it.note ?: ""}" }
                            } else null

                            val newTemplate = Template(
                                name = inputName,
                                type = type,
                                accountId = selectedAccountId,
                                toAccountId = selectedToAccountId,
                                categoryId = selectedCategoryId,
                                amount = evaluateExpression(amount).takeIf { it > 0 },
                                note = note,
                                tags = tagsString,
                                multiEntries = multiStr
                            )
                            
                            scope.launch {
                                val success = viewModel.saveTemplate(newTemplate)
                                if (success) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Template Saved", Toast.LENGTH_SHORT).show()
                                        showNameDialog = false
                                        if (isTemplateMode) onBack()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Template with this name already exists", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showTemplateSelection) {
            AlertDialog(
                onDismissRequest = { showTemplateSelection = false },
                title = { Text("Select Template") },
                text = {
                    if (templates.isEmpty()) {
                        Text("No templates found.")
                    } else {
                        Box(modifier = Modifier.height(300.dp)) {
                            LazyColumn {
                                items(templates) { t ->
                                    ListItem(
                                        headlineContent = { Text(t.name) },
                                        modifier = Modifier.clickable {
                                            type = t.type
                                            selectedAccountId = t.accountId
                                            selectedToAccountId = t.toAccountId
                                            selectedCategoryId = t.categoryId
                                            amount = t.amount?.toString() ?: ""
                                            note = t.note ?: ""
                                            selectedTagIds.clear()
                                            t.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { selectedTagIds.addAll(it) }
                                            
                                            if (t.multiEntries != null) {
                                                isMultiEntry = true
                                                multiEntryRows.clear()
                                                t.multiEntries.split("|").forEach { entry ->
                                                    val parts = entry.split(":")
                                                    if (parts.size >= 2) {
                                                        multiEntryRows.add(MultiEntryRow(categoryId = parts[0].toIntOrNull(), amount = parts[1], note = parts.getOrNull(2)))
                                                    }
                                                }
                                            } else {
                                                isMultiEntry = false
                                            }
                                            showTemplateSelection = false
                                        }
                                    )
                                    Divider()
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    if (templates.isEmpty()) {
                        Button(onClick = {
                            showTemplateSelection = false
                            onNavigate?.invoke("templates")
                        }) { Text("Go to Templates") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTemplateSelection = false }) { Text("Cancel") }
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            if (isTemplateMode) {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text("Template Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }

            // Transaction Number Field
            OutlinedTextField(
                value = txnDetail?.transaction?.transactionNumber ?: "Auto-generated on save",
                onValueChange = {},
                label = { Text("Transaction Number") },
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            if (viewModel.templateFields.contains("type") || !isTemplateMode) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(
                        selected = type == "income", 
                        onClick = { if (!readOnly) { type = "income"; selectedCategoryId = null } }, 
                        label = { Text("Income") },
                        enabled = !readOnly || type == "income"
                    )
                    FilterChip(
                        selected = type == "expense", 
                        onClick = { if (!readOnly) { type = "expense"; selectedCategoryId = null } }, 
                        label = { Text("Expense") },
                        enabled = !readOnly || type == "expense"
                    )
                    FilterChip(
                        selected = type == "transfer", 
                        onClick = { if (!readOnly) { type = "transfer"; selectedAccountId = null; selectedToAccountId = null; selectedCategoryId = null } },
                        label = { Text("Transfer") },
                        enabled = !readOnly || type == "transfer"
                    )
                }

                if (type != "transfer" && !isTemplateMode && !readOnly) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        FilterChip(
                            selected = !isMultiEntry,
                            onClick = { isMultiEntry = false },
                            label = { Text("Single Entry") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = isMultiEntry,
                            onClick = { isMultiEntry = true },
                            label = { Text("Multi Entry") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (!isTemplateMode) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f).clickable(enabled = !readOnly) {
                        val dateParts = date.split("-")
                        DatePickerDialog(context, { _, year, month, dayOfMonth ->
                            date = String.format("%d-%02d-%02d", year, month + 1, dayOfMonth)
                        }, dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt()).show()
                    }) {
                        OutlinedTextField(
                            value = date,
                            onValueChange = { },
                            label = { Text("Date") },
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Box(modifier = Modifier.weight(1f).clickable(enabled = !readOnly) {
                        val timeParts = time.split(":")
                        TimePickerDialog(context, { _, hour, minute ->
                            time = String.format("%02d:%02d", hour, minute)
                        }, timeParts[0].toInt(), timeParts[1].toInt(), true).show()
                    }) {
                        OutlinedTextField(
                            value = time,
                            onValueChange = { },
                            label = { Text("Time") },
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Account Selection (Common)
            if (type == "transfer") {
                if (viewModel.templateFields.contains("accountId") || !isTemplateMode) {
                    AccountSelectionDialog(
                        label = "From Account",
                        accounts = accounts,
                        selectedId = selectedAccountId,
                        onSelected = { selectedAccountId = it; accountError = null },
                        enabled = !readOnly,
                        onAdd = {
                            saveAsDraft()
                            onNavigate?.invoke("add_category")
                        },
                        isError = accountError != null
                    )
                    if (accountError != null) Text(accountError!!, color = Color.Red, style = MaterialTheme.typography.labelSmall)
                    
                    AccountSelectionDialog(
                        label = "To Account",
                        accounts = accounts,
                        selectedId = selectedToAccountId,
                        onSelected = { selectedToAccountId = it; toAccountError = null },
                        enabled = !readOnly,
                        onAdd = {
                            saveAsDraft()
                            onNavigate?.invoke("add_category")
                        },
                        isError = toAccountError != null
                    )
                    if (toAccountError != null) Text(toAccountError!!, color = Color.Red, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                if (viewModel.templateFields.contains("accountId") || !isTemplateMode) {
                    AccountSelectionDialog(
                        label = "Account",
                        accounts = accounts,
                        selectedId = selectedAccountId,
                        onSelected = { selectedAccountId = it; accountError = null },
                        enabled = !readOnly,
                        onAdd = {
                            saveAsDraft()
                            onNavigate?.invoke("add_category")
                        },
                        isError = accountError != null
                    )
                    if (accountError != null) Text(accountError!!, color = Color.Red, style = MaterialTheme.typography.labelSmall)
                }
            }

            if (isFromOnAccount) {
                Spacer(Modifier.height(8.dp))
                PartySelectionDialog(
                    label = if (type == "transfer") "From Party" else if (type == "income") "Payer" else "Party Name",
                    parties = allParties,
                    selectedId = selectedPartyId,
                    onSelected = { selectedPartyId = it },
                    enabled = !readOnly,
                    onAdd = {
                        saveAsDraft()
                        onNavigate?.invoke("add_category")
                    },
                    displayValue = if (readOnly) displayPartyName else null
                )
            }

            if (type == "transfer" && isToOnAccount) {
                Spacer(Modifier.height(8.dp))
                PartySelectionDialog(
                    label = "To Party",
                    parties = allParties,
                    selectedId = selectedToPartyId,
                    onSelected = { selectedToPartyId = it },
                    enabled = !readOnly,
                    onAdd = {
                        saveAsDraft()
                        onNavigate?.invoke("add_category")
                    },
                    displayValue = if (readOnly) displayToPartyName else null
                )
            }

            Spacer(Modifier.height(8.dp))

            // Categories (Conditional)
            if (type != "transfer") {
                AnimatedVisibility(
                    visible = isMultiEntry,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Text("Categories & Amounts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                        multiEntryRows.forEachIndexed { index, row ->
                            key(row.id) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(0.5f)) {
                                        var rowCategoryId by remember(row.categoryId) { mutableStateOf(row.categoryId) }
                                        CategorySelectionDialog(
                                            label = "Category",
                                            categories = categories,
                                            selectedId = rowCategoryId,
                                            onSelected = { 
                                                rowCategoryId = it
                                                multiEntryRows[index] = row.copy(categoryId = it) 
                                                categoryError = null
                                            },
                                            enabled = !readOnly,
                                            onAdd = {
                                                saveAsDraft()
                                                onNavigate?.let { it("add_category") }
                                            },
                                            isError = categoryError != null && row.categoryId == null
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedTextField(
                                        value = row.amount,
                                        onValueChange = { 
                                            multiEntryRows[index] = row.copy(amount = it)
                                            val total = multiEntryRows.sumOf { r -> evaluateExpression(r.amount) }
                                            amount = total.toString()
                                            amountError = null
                                        },
                                        label = { Text("Amt") },
                                        modifier = Modifier.weight(0.35f),
                                        readOnly = readOnly,
                                        isError = categoryError != null && row.amount.isBlank(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        )
                                    )
                                    if (!readOnly) {
                                        IconButton(
                                            onClick = {
                                                if (multiEntryRows.size > 1) multiEntryRows.removeAt(index)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.RemoveCircle, 
                                                "Remove", 
                                                tint = Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (categoryError != null) Text(categoryError!!, color = Color.Red, style = MaterialTheme.typography.labelSmall)
                        if (!readOnly) {
                            TextButton(onClick = { multiEntryRows.add(MultiEntryRow(categoryId = null, amount = "")) }, modifier = Modifier.align(Alignment.End)) {
                                Icon(Icons.Default.Add, null)
                                Text("Add Row")
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !isMultiEntry,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    if (viewModel.templateFields.contains("categoryId") || !isTemplateMode) {
                        CategorySelectionDialog(
                            label = "Category",
                            categories = categories,
                            selectedId = selectedCategoryId,
                            onSelected = { selectedCategoryId = it; categoryError = null },
                            enabled = !readOnly,
                            onAdd = {
                                saveAsDraft()
                                onNavigate?.let { it("add_category") }
                            },
                            isError = categoryError != null
                        )
                        if (categoryError != null) Text(categoryError!!, color = Color.Red, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { if (!readOnly) { amount = it; amountError = null } },
                label = { Text("Total Amount") },
                readOnly = readOnly,
                isError = amountError != null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                trailingIcon = {
                    if (!readOnly) {
                        IconButton(onClick = { showCalculator = !showCalculator }) {
                            Icon(Icons.Default.Calculate, "Calculator")
                        }
                    }
                }
            )
            if (amountError != null) Text(amountError!!, color = Color.Red, style = MaterialTheme.typography.labelSmall)

            if (showCalculator && !readOnly) {
                CalculatorKeypad(onValueChange = { 
                    amount = it
                }, currentValue = amount)
            }

            if (viewModel.templateFields.contains("tags") || !isTemplateMode) {
                TagSelectionPopup(
                    allTags = allTags,
                    selectedIds = selectedTagIds,
                    multiSelect = viewModel.multiTagEnabled,
                    enabled = !readOnly
                )
            }

            if (viewModel.templateFields.contains("note") || !isTemplateMode) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (!readOnly) note = it },
                    label = { Text("Note") },
                    readOnly = readOnly,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                )
            }

            if (!readOnly) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    val onSaveClick: () -> Unit = {
                        var hasError = false
                        if (evaluateExpression(amount) <= 0.0) {
                            amountError = "missing value"
                            hasError = true
                        }
                        if (selectedAccountId == null) {
                            accountError = "missing value"
                            hasError = true
                        }
                        if (type == "transfer" && selectedToAccountId == null) {
                            toAccountError = "missing value"
                            hasError = true
                        }
                        if (!isMultiEntry && type != "transfer" && selectedCategoryId == null) {
                            categoryError = "missing value"
                            hasError = true
                        }
                        if (isMultiEntry && multiEntryRows.any { it.categoryId == null || evaluateExpression(it.amount) <= 0.0 }) {
                            categoryError = "missing value"
                            hasError = true
                        }

                        if (!hasError) {
                            val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                            
                            if (isMultiEntry) {
                                val rowTotal = multiEntryRows.sumOf { evaluateExpression(it.amount) }
                                val mainAmountValue = evaluateExpression(amount)
                                
                                if (mainAmountValue != rowTotal) {
                                    mismatchTotals = Pair(mainAmountValue, rowTotal)
                                    showMismatchDialog = true
                                } else {
                                    if (selectedAccountId != null && multiEntryRows.all { it.categoryId != null }) {
                                        val entries = multiEntryRows.map { Triple(it.categoryId!!, evaluateExpression(it.amount), note) }
                                        viewModel.addMultiEntryTransaction(date, time, selectedAccountId!!, entries, tagsString, type, selectedPartyId)
                                        viewModel.draftTransaction = null
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                }
                            } else {
                                val amt = evaluateExpression(amount)
                                if (type == "transfer") {
                                    if (selectedAccountId != null && selectedToAccountId != null) {
                                        viewModel.addTransaction(date, time, selectedAccountId!!, null, amt, note, selectedToAccountId, tagsString, type, selectedPartyId, selectedToPartyId)
                                        viewModel.draftTransaction = null
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                } else {
                                    if (selectedCategoryId != null && selectedAccountId != null) {
                                        viewModel.addTransaction(date, time, selectedAccountId!!, selectedCategoryId, amt, note, null, tagsString, type, selectedPartyId)
                                        viewModel.draftTransaction = null
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                }
                            }
                        }
                    }

                    val onSaveTemplateClick: () -> Unit = {
                        val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                        val multiStr = if (isMultiEntry) {
                            multiEntryRows.joinToString("|") { "${it.categoryId}:${it.amount}:${it.note ?: ""}" }
                        } else null

                        val templateToSave = Template(
                            id = if (isTemplateMode) editingTemplate?.id ?: 0 else 0,
                            name = if (isTemplateMode) templateName else "", // Will be overwritten by dialog if not in template mode
                            type = type,
                            accountId = selectedAccountId,
                            toAccountId = selectedToAccountId,
                            categoryId = selectedCategoryId,
                            amount = evaluateExpression(amount).takeIf { it > 0 },
                            note = note,
                            tags = tagsString,
                            multiEntries = multiStr
                        )

                        if (isTemplateMode && templateName.isNotBlank()) {
                            scope.launch {
                                val success = viewModel.saveTemplate(templateToSave)
                                if (success) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Template Saved", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Template with this name already exists", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        } else {
                            showNameDialog = true
                        }
                    }

                    if (isTemplateMode) {
                        Button(onClick = onSaveTemplateClick, modifier = Modifier.fillMaxWidth()) {
                            Text("Save Template")
                        }
                    } else {
                        OutlinedButton(onClick = onSaveTemplateClick, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Text("Save as Template")
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showTemplateSelection = true }, modifier = Modifier.weight(1f)) {
                                Text("Use Template")
                            }
                            Button(onClick = onSaveClick, modifier = Modifier.weight(1f)) {
                                Text("Save")
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(24.dp))
                Text("Viewing archived transaction data.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun AccountSelectionDialog(label: String, accounts: List<com.openapps.fintrack.data.Account>, selectedId: Int?, onSelected: (Int) -> Unit, enabled: Boolean = true, onAdd: () -> Unit, isError: Boolean = false) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = accounts.find { it.id == selectedId }?.name ?: "Select Account"

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled) { showDialog = true }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                disabledLabelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            trailingIcon = { if (enabled) Icon(Icons.Default.ArrowDropDown, "") }
        )
    }

    if (showDialog && enabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Box(Modifier.height(300.dp)) {
                    LazyColumn {
                        items(accounts) { account ->
                            ListItem(
                                headlineContent = { Text(account.name) },
                                supportingContent = { Text(account.type.title()) },
                                modifier = Modifier.clickable {
                                    onSelected(account.id)
                                    showDialog = false
                                }
                            )
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    Button(onClick = onAdd) {
                        Icon(Icons.Default.Add, null)
                        Text("Add")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun PartySelectionDialog(label: String, parties: List<com.openapps.fintrack.data.Party>, selectedId: Int?, onSelected: (Int) -> Unit, enabled: Boolean = true, onAdd: () -> Unit, displayValue: String? = null) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = displayValue ?: parties.find { it.id == selectedId }?.name ?: "Select $label"

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled) { showDialog = true }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            trailingIcon = { if (enabled) Icon(Icons.Default.ArrowDropDown, "") }
        )
    }

    if (showDialog && enabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Box(Modifier.height(300.dp)) {
                    LazyColumn {
                        items(parties) { party ->
                            ListItem(
                                headlineContent = { Text(party.name) },
                                modifier = Modifier.clickable {
                                    onSelected(party.id)
                                    showDialog = false
                                }
                            )
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    Button(onClick = {
                        showDialog = false
                        onAdd()
                    }) {
                        Icon(Icons.Default.Add, null)
                        Text("Add New")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun CategorySelectionDialog(label: String, categories: List<com.openapps.fintrack.data.Category>, selectedId: Int?, onSelected: (Int) -> Unit, enabled: Boolean = true, onAdd: () -> Unit, isError: Boolean = false) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selectedId }?.name ?: "Select"

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled) { showDialog = true }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            isError = isError,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                disabledLabelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            trailingIcon = { if (enabled) Icon(Icons.Default.ArrowDropDown, "") }
        )
    }

    if (showDialog && enabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(label) },
            text = {
                Box(Modifier.height(400.dp)) {
                    LazyColumn {
                        items(categories) { category ->
                            ListItem(
                                headlineContent = { Text(category.name) },
                                supportingContent = { Text(category.type.title()) },
                                modifier = Modifier.clickable {
                                    onSelected(category.id)
                                    showDialog = false
                                }
                            )
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    Button(onClick = onAdd) {
                        Icon(Icons.Default.Add, null)
                        Text("Add")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun TagSelectionPopup(
    allTags: List<com.openapps.fintrack.data.Tag>,
    selectedIds: MutableList<Int>,
    multiSelect: Boolean,
    enabled: Boolean = true
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedNames = allTags.filter { it.id in selectedIds }.map { it.name }.joinToString(", ")
    val displayText = if (selectedNames.isEmpty()) "Select Tags" else selectedNames

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled) { showDialog = true }) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            label = { Text("Tags") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            trailingIcon = { if (enabled) Icon(Icons.Default.ArrowDropDown, "") }
        )
    }

    if (showDialog && enabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Select Tags") },
            text = {
                Box(Modifier.height(300.dp)) {
                    LazyColumn {
                        items(allTags) { tag ->
                            ListItem(
                                headlineContent = { Text(tag.name) },
                                trailingContent = {
                                    if (multiSelect) {
                                        Checkbox(checked = tag.id in selectedIds, onCheckedChange = {
                                            if (tag.id in selectedIds) selectedIds.remove(tag.id)
                                            else selectedIds.add(tag.id)
                                        })
                                    }
                                },
                                modifier = Modifier.clickable {
                                    if (multiSelect) {
                                        if (tag.id in selectedIds) {
                                            selectedIds.remove(tag.id)
                                        } else {
                                            selectedIds.add(tag.id)
                                        }
                                    } else {
                                        selectedIds.clear()
                                        selectedIds.add(tag.id)
                                        showDialog = false
                                    }
                                }
                            )
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {
                if (multiSelect) {
                    Button(onClick = { showDialog = false }) { Text("Done") }
                } else {
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun CalculatorKeypad(onValueChange: (String) -> Unit, currentValue: String) {
    val buttons = listOf(
        listOf("7", "8", "9", "/"),
        listOf("4", "5", "6", "*"),
        listOf("1", "2", "3", "-"),
        listOf("0", ".", "C", "+")
    )

    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
        buttons.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { btn ->
                    Button(
                        onClick = {
                            when (btn) {
                                "C" -> onValueChange("")
                                else -> {
                                    val lastChar = if (currentValue.isNotEmpty()) currentValue.last() else ' '
                                    val isOp = btn in "+-*/"
                                    val wasOp = lastChar in "+-*/"
                                    if (!(isOp && wasOp)) {
                                        onValueChange(currentValue + btn)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (btn in "0123456789.") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(btn, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Button(
            onClick = { onValueChange(evaluateExpression(currentValue).toString()) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        ) {
            Text("=")
        }
    }
}

fun evaluateExpression(expression: String): Double {
    try {
        if (expression.isBlank()) return 0.0
        val sanitized = expression.replace(",", "").trim()
        
        val tokens = mutableListOf<String>()
        var currentNum = StringBuilder()
        for (char in sanitized) {
            if (char in "0123456789.") {
                currentNum.append(char)
            } else if (char in "+-*/") {
                if (currentNum.isNotEmpty()) tokens.add(currentNum.toString())
                tokens.add(char.toString())
                currentNum = StringBuilder()
            }
        }
        if (currentNum.isNotEmpty()) tokens.add(currentNum.toString())
        
        if (tokens.isEmpty()) return 0.0
        
        if (tokens[0] == "-" && tokens.size > 1) {
            tokens[1] = "-" + tokens[1]
            tokens.removeAt(0)
        }

        var j = 0
        while (j < tokens.size) {
            if (tokens[j] == "*" || tokens[j] == "/") {
                if (j > 0 && j + 1 < tokens.size) {
                    val left = tokens[j-1].toDoubleOrNull() ?: 0.0
                    val right = tokens[j+1].toDoubleOrNull() ?: 1.0
                    val result = if (tokens[j] == "*") left * right else {
                        if (right != 0.0) left / right else 0.0
                    }
                    tokens[j-1] = result.toString()
                    tokens.removeAt(j)
                    tokens.removeAt(j)
                    j--
                }
            }
            j++
        }
        
        if (tokens.isEmpty()) return 0.0
        var result = tokens[0].toDoubleOrNull() ?: 0.0
        var k = 1
        while (k < tokens.size) {
            val op = tokens[k]
            if (k + 1 < tokens.size) {
                val nextVal = tokens[k+1].toDoubleOrNull() ?: 0.0
                result = if (op == "+") result + nextVal else result - nextVal
            }
            k += 2
        }
        
        return result
    } catch (e: Exception) {
        return expression.toDoubleOrNull() ?: 0.0
    }
}
