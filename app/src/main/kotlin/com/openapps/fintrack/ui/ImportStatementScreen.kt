/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.openapps.fintrack.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import java.io.InputStreamReader
import java.io.BufferedReader
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.openapps.fintrack.data.Account
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportStatementScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    
    val selectedAccount = viewModel.selectedImportAccount
    val importStatus = viewModel.importStatus
    var isProcessing by remember { mutableStateOf(false) }
    
    val pendingTransactions = viewModel.pendingTransactions
    val showAccountSelection = viewModel.showAccountSelection
    
    var showBulkDialog by remember { mutableStateOf(false) }
    var selectedBulkType by remember { mutableStateOf("expense") }
    var selectedBulkCategoryId by remember { mutableStateOf<Int?>(null) }
    var selectedTxnKeys by remember { mutableStateOf(setOf<String>()) }
    var menuExpanded by remember { mutableStateOf(false) }
    
    val categories by viewModel.getEnabledCategories().collectAsState(initial = emptyList())
    val filteredCategories = remember(selectedBulkType, categories) {
        categories.filter { it.type == selectedBulkType }
    }

    val errorTitle = stringResource(R.string.title_error)
    val parsingCsvMsg = stringResource(R.string.msg_parsing_csv)
    val extractingTxnsMsg = stringResource(R.string.msg_extracting_txns)
    val noNewTxnsMsg = stringResource(R.string.msg_no_new_txns)
    val foundNTxnsMsg = stringResource(R.string.msg_found_n_txns)

    fun processFile(uri: Uri) {
        isProcessing = true
        viewModel.importStatus = parsingCsvMsg
        scope.launch {
            try {
                val rows = extractBlocksFromCsv(context, uri)
                viewModel.rawRows = rows
                
                val assignments = mutableListOf<ColumnAssignment>()
                if (rows.isNotEmpty()) {
                    val maxCols = rows.take(20).maxOf { it.size }
                    for (index in 0 until maxCols) {
                        assignments.add(ColumnAssignment(ColumnRole.IGNORE, index.toFloat(), (index + 1).toFloat()))
                    }
                }
                viewModel.detectedColumnMap = ColumnMap(assignments)
                
                viewModel.isShowingTablePreview = true
                viewModel.showAccountSelection = false
                isProcessing = false
            } catch (e: Exception) {
                isProcessing = false
                viewModel.importStatus = "$errorTitle: ${e.localizedMessage}"
            }
        }
    }

    fun finalizeImport() {
        val mapping = viewModel.detectedColumnMap ?: return
        isProcessing = true
        viewModel.importStatus = extractingTxnsMsg
        scope.launch {
            val results = processTransactions(viewModel.rawRows, mapping, viewModel, selectedAccount)
            viewModel.pendingTransactions = results
            viewModel.isShowingTablePreview = false
            isProcessing = false
            viewModel.importStatus = if (results.isEmpty()) noNewTxnsMsg else foundNTxnsMsg.format(results.size)
            viewModel.showAccountSelection = false
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processFile(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_import_statement)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (viewModel.isShowingTablePreview) {
                            viewModel.isShowingTablePreview = false
                            viewModel.showAccountSelection = true
                        } else {
                            viewModel.draftTransaction = null
                            viewModel.currentRecordingImportTxnKey = null
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    if (importStatus.isNotEmpty() && !showAccountSelection && !viewModel.isShowingTablePreview) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.label_more))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_bulk_record)) },
                                onClick = {
                                    menuExpanded = false
                                    if (pendingTransactions.isNotEmpty()) showBulkDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.LibraryAdd, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_reset)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.showAccountSelection = true
                                    viewModel.pendingTransactions = emptyList()
                                    viewModel.importStatus = ""
                                    viewModel.selectedImportAccount = null
                                    selectedTxnKeys = emptySet()
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (showAccountSelection) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.label_how_it_works), style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(8.dp))
                        val steps = listOf(
                            stringResource(R.string.msg_import_step_1),
                            stringResource(R.string.msg_import_step_2),
                            stringResource(R.string.msg_import_step_3),
                            stringResource(R.string.msg_import_step_4),
                            stringResource(R.string.msg_import_step_5),
                            stringResource(R.string.msg_import_step_6)
                        )
                        steps.forEach { step ->
                            Text(step, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }

                Text(stringResource(R.string.label_select_account_mapping), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                
                var accExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { accExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedAccount?.name ?: stringResource(R.string.label_choose_account))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = accExpanded, onDismissRequest = { accExpanded = false }) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    viewModel.selectedImportAccount = account
                                    accExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { fileLauncher.launch("text/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedAccount != null && !isProcessing
                ) {
                    Icon(Icons.Default.Description, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_select_csv_file))
                }
            }

            if (viewModel.isShowingTablePreview) {
                Text(stringResource(R.string.title_confirm_col_mapping), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.msg_col_mapping_desc), style = MaterialTheme.typography.bodySmall)
                
                Spacer(Modifier.height(8.dp))
                
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    ColumnTablePreview(
                        rows = viewModel.rawRows.take(50),
                        columnMap = viewModel.detectedColumnMap,
                        onMappingChange = { viewModel.detectedColumnMap = it }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { finalizeImport() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_confirm_extract))
                }
            } else if (isProcessing) {
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(importStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            } else if (importStatus.isNotEmpty() && !showAccountSelection) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    Column {
                        Text(importStatus, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (selectedTxnKeys.isNotEmpty()) {
                            Text(stringResource(R.string.label_n_selected, selectedTxnKeys.size), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }

                if (showBulkDialog) {
                    AlertDialog(
                        onDismissRequest = { showBulkDialog = false },
                        title = { Text(stringResource(R.string.title_bulk_record_txns)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.msg_select_mapping_bulk), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    FilterChip(selected = selectedBulkType == "expense", onClick = { selectedBulkType = "expense"; selectedBulkCategoryId = null }, label = { Text(stringResource(R.string.label_expense)) })
                                    FilterChip(selected = selectedBulkType == "income", onClick = { selectedBulkType = "income"; selectedBulkCategoryId = null }, label = { Text(stringResource(R.string.label_income)) })
                                }
                                CategorySelectionDialog(
                                    label = stringResource(R.string.label_category),
                                    categories = filteredCategories,
                                    selectedId = selectedBulkCategoryId,
                                    onSelected = { selectedBulkCategoryId = it },
                                    onAdd = { onNavigate("add_category") }
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                enabled = selectedBulkCategoryId != null && selectedTxnKeys.isNotEmpty(),
                                onClick = {
                                    scope.launch {
                                        val toBook = pendingTransactions.filter { (it.description + it.date.toString() + it.amount.toString()) in selectedTxnKeys }
                                        toBook.forEach { txn ->
                                            viewModel.addTransaction(
                                                date = txn.date.format(DateTimeFormatter.ISO_DATE),
                                                time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                                accountId = selectedAccount?.id ?: 0,
                                                categoryId = selectedBulkCategoryId,
                                                amount = txn.amount,
                                                note = txn.description,
                                                toAccountId = null,
                                                tags = null,
                                                type = selectedBulkType,
                                                amountOriginal = txn.amount,
                                                currencyCode = viewModel.baseCurrency,
                                                amountBase = txn.amount
                                            )
                                        }
                                        viewModel.pendingTransactions = pendingTransactions.filter { (it.description + it.date.toString() + it.amount.toString()) !in selectedTxnKeys }
                                        Toast.makeText(context, context.getString(R.string.msg_booked_n_txns, toBook.size), Toast.LENGTH_SHORT).show()
                                        selectedTxnKeys = emptySet()
                                        showBulkDialog = false
                                    }
                                }
                            ) { Text(stringResource(R.string.btn_confirm_bulk_record)) }
                        },
                        dismissButton = { TextButton(onClick = { showBulkDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Checkbox(
                        checked = selectedTxnKeys.size == pendingTransactions.size && pendingTransactions.isNotEmpty(),
                        onCheckedChange = { checked ->
                            if (checked) selectedTxnKeys = pendingTransactions.map { it.description + it.date.toString() + it.amount.toString() }.toSet()
                            else selectedTxnKeys = emptySet()
                        }
                    )
                    Text(stringResource(R.string.label_select_all), style = MaterialTheme.typography.labelMedium)
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(pendingTransactions, key = { it.description + it.date.toString() + it.amount.toString() }) { txn ->
                        val key = txn.description + txn.date.toString() + txn.amount.toString()
                        val isSelected = key in selectedTxnKeys
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                                    viewModel.pendingTransactions = viewModel.pendingTransactions.filter { item -> item != txn }
                                    true
                                } else false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.EndToStart -> Color.Red.copy(alpha = 0.8f)
                                    SwipeToDismissBoxValue.StartToEnd -> Color.Red.copy(alpha = 0.8f)
                                    else -> Color.Transparent
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(Icons.Default.Delete, "Remove", tint = Color.White)
                                }
                            },
                            content = {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            // Pre-fill Add Transaction screen
                                            viewModel.currentRecordingImportTxnKey = key
                                            viewModel.draftTransaction = DraftTransaction(
                                                type = if (txn.isCredit) "income" else "expense",
                                                amount = txn.amount.toString(),
                                                note = txn.description,
                                                date = txn.date.format(DateTimeFormatter.ISO_DATE),
                                                time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                                                accountId = selectedAccount?.id,
                                                toAccountId = null,
                                                categoryId = null,
                                                selectedTagIds = emptyList()
                                            )
                                            onNavigate("add_transaction")
                                        }
                                ) {
                                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                if (checked) selectedTxnKeys += key
                                                else selectedTxnKeys -= key
                                            }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(txn.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Row {
                                                Text(txn.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), style = MaterialTheme.typography.bodySmall)
                                                txn.sourceColumn?.let {
                                                    Text(" • $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        }
                                        Text(
                                            viewModel.formatAmount(if (txn.isCredit) txn.amount else -txn.amount),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (txn.isCredit) Color(0xFF4CAF50) else Color.Red
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnTablePreview(
    rows: List<List<String>>,
    columnMap: ColumnMap?,
    onMappingChange: (ColumnMap) -> Unit
) {
    if (rows.isEmpty() || columnMap == null) return

    val horizontalScrollState = rememberScrollState()
    val roles = ColumnRole.entries
    val assignments = columnMap.assignments
    val columnWidth = 150.dp

    Column(modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScrollState)) {
        Row(
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            assignments.forEachIndexed { index, assignment ->
                var expanded by remember { mutableStateOf(false) }
                
                Box(modifier = Modifier.width(columnWidth).border(0.5.dp, MaterialTheme.colorScheme.outline).clickable { expanded = true }.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = assignment.role.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(16.dp))
                    }
                    
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        roles.forEach { newRole ->
                            DropdownMenuItem(
                                text = { Text(newRole.name) },
                                onClick = {
                                    val newAssignments = columnMap.assignments.toMutableList()
                                    newAssignments[index] = assignment.copy(role = newRole)
                                    onMappingChange(ColumnMap(newAssignments))
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rows) { row ->
                Row {
                    for (i in assignments.indices) {
                        val cellText = row.getOrNull(i) ?: ""
                        Box(
                            modifier = Modifier.width(columnWidth).border(0.2.dp, MaterialTheme.colorScheme.outlineVariant).padding(8.dp)
                        ) {
                            Text(
                                text = cellText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun extractBlocksFromCsv(
    context: android.content.Context,
    uri: Uri
): List<List<String>> = withContext(Dispatchers.IO) {
    val rows = mutableListOf<List<String>>()
    context.contentResolver.openInputStream(uri)?.use { inputStream ->
        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                val parts = splitCsvLine(line)
                if (parts.any { it.isNotEmpty() }) {
                    rows.add(parts)
                }
                line = reader.readLine()
            }
        }
    }
    return@withContext rows
}

private fun splitCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var inQuotes = false
    val currentPart = StringBuilder()
    var i = 0
    while (i < line.length) {
        val char = line[i]
        if (char == '\"') {
            inQuotes = !inQuotes
        } else if (char == ',' && !inQuotes) {
            result.add(currentPart.toString().trim().trim('\"'))
            currentPart.setLength(0)
        } else {
            currentPart.append(char)
        }
        i++
    }
    result.add(currentPart.toString().trim().trim('\"'))
    return result
}

private suspend fun processTransactions(
    rows: List<List<String>>,
    columnMap: ColumnMap,
    viewModel: ExpenseViewModel,
    account: Account?
): List<ExtractedTransaction> = withContext(Dispatchers.IO) {
    val extracted = mutableListOf<ExtractedTransaction>()
    val existingTxns = viewModel.dao.getAllTransactionsWithDetails().first()
    val accountId = account?.id

    val dateIdx = columnMap.assignments.indexOfFirst { it.role == ColumnRole.DATE }
    val descIdx = columnMap.assignments.indexOfFirst { it.role == ColumnRole.DESCRIPTION }
    val amountIdx = columnMap.assignments.indexOfFirst { it.role == ColumnRole.AMOUNT }
    val debitIdx = columnMap.assignments.indexOfFirst { it.role == ColumnRole.DEBIT }
    val creditIdx = columnMap.assignments.indexOfFirst { it.role == ColumnRole.CREDIT }
    val drCrIdx = columnMap.assignments.indexOfFirst { it.role == ColumnRole.DR_CR }

    var currentTxn: ExtractedTransaction? = null

    fun isCreditMarker(s: String): Boolean {
        val low = s.lowercase()
        return low.contains("cr") || low.contains("credit") || low.contains("deposit") || low.contains("receipt")
    }

    rows.forEach { row ->
        val dateStr = row.getOrNull(dateIdx) ?: ""
        val date = tryParseDate(dateStr)

        if (date != null) {
            currentTxn?.let { extracted.add(it) }
            
            val description = row.getOrNull(descIdx) ?: ""
            val amountStr = row.getOrNull(amountIdx)?.replace(",", "") ?: ""
            val debitStr = row.getOrNull(debitIdx)?.replace(",", "") ?: ""
            val creditStr = row.getOrNull(creditIdx)?.replace(",", "") ?: ""
            val drCrStr = if (drCrIdx != -1) row.getOrNull(drCrIdx) ?: "" else ""

            val amountRaw = amountStr.toDoubleOrNull() ?: 0.0
            val debit = debitStr.toDoubleOrNull() ?: 0.0
            val credit = creditStr.toDoubleOrNull() ?: 0.0

            if (amountRaw != 0.0) {
                val isCredit = if (drCrStr.isNotBlank()) isCreditMarker(drCrStr) else amountRaw > 0
                currentTxn = ExtractedTransaction(date, description, Math.abs(amountRaw), isCredit, if (drCrStr.isNotBlank()) "Amount ($drCrStr)" else "Amount")
            } else if (debit != 0.0) {
                currentTxn = ExtractedTransaction(date, description, Math.abs(debit), false, "Debit")
            } else if (credit != 0.0) {
                currentTxn = ExtractedTransaction(date, description, Math.abs(credit), true, "Credit")
            } else {
                currentTxn = ExtractedTransaction(date, description, 0.0, false, "N/A")
            }
        } else {
            currentTxn?.let { txn ->
                val extraDesc = row.getOrNull(descIdx) ?: ""
                if (extraDesc.isNotBlank()) {
                    val updatedDesc = if (txn.description.isEmpty()) extraDesc else "${txn.description} $extraDesc"
                    currentTxn = txn.copy(description = updatedDesc)
                }
                
                if (txn.amount == 0.0) {
                    val amountStr = row.getOrNull(amountIdx)?.replace(",", "") ?: ""
                    val debitStr = row.getOrNull(debitIdx)?.replace(",", "") ?: ""
                    val creditStr = row.getOrNull(creditIdx)?.replace(",", "") ?: ""
                    val drCrStr = if (drCrIdx != -1) row.getOrNull(drCrIdx) ?: "" else ""

                    val amountRaw = amountStr.toDoubleOrNull() ?: 0.0
                    val debit = debitStr.toDoubleOrNull() ?: 0.0
                    val credit = creditStr.toDoubleOrNull() ?: 0.0
                    
                    if (amountRaw != 0.0) {
                        val isCredit = if (drCrStr.isNotBlank()) isCreditMarker(drCrStr) else amountRaw > 0
                        currentTxn = txn.copy(amount = Math.abs(amountRaw), isCredit = isCredit, sourceColumn = if (drCrStr.isNotBlank()) "Amount ($drCrStr)" else "Amount")
                    } else if (debit != 0.0) {
                        currentTxn = txn.copy(amount = Math.abs(debit), isCredit = false, sourceColumn = "Debit")
                    } else if (credit != 0.0) {
                        currentTxn = txn.copy(amount = Math.abs(credit), isCredit = true, sourceColumn = "Credit")
                    }
                }
            }
        }
    }
    currentTxn?.let { extracted.add(it) }
    
    return@withContext extracted.filter { it.amount != 0.0 }.filter { ext ->
        existingTxns.none { db ->
            val matchesAccount = db.transaction.accountId == accountId || db.transaction.toAccountId == accountId
            matchesAccount &&
            db.transaction.date == ext.date.format(DateTimeFormatter.ISO_DATE) &&
            Math.abs(db.transaction.amount - ext.amount) < 0.01
        }
    }.sortedByDescending { it.date }
}

private fun tryParseDate(dateStr: String): LocalDate? {
    val formats = listOf(
        "dd/MM/yyyy", "dd-MM-yyyy", "dd/MM/yy", "dd-MM-yy",
        "dd MMM yyyy", "dd MMM yy", "dd MMMM yyyy",
        "d/M/yyyy", "d-M-yyyy", "d/M/yy", "d-M-yy",
        "dd-MMM-yyyy", "dd-MMM-yy"
    )
    formats.forEach { fmt ->
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern(fmt, Locale.ENGLISH))
        } catch (e: Exception) {}
    }
    return null
}
