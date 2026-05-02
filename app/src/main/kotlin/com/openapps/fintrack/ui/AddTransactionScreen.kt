package com.openapps.fintrack.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: ExpenseViewModel, 
    onBack: () -> Unit, 
    initialData: android.os.Bundle? = null,
    readOnly: Boolean = false
) {
    val txnDetail = viewModel.selectedTransactionDetail
    
    var type by remember { mutableStateOf(txnDetail?.categoryType ?: txnDetail?.let { "transfer" } ?: "expense") }
    
    val initialAmount = txnDetail?.transaction?.amount?.toString() ?: initialData?.getDouble("amount", 0.0)?.takeIf { it > 0 }?.toString() ?: ""
    val initialNote = txnDetail?.transaction?.note ?: initialData?.getString("sms_body") ?: ""
    val initialDate = txnDetail?.transaction?.date ?: initialData?.getString("date")
    val initialTime = txnDetail?.transaction?.time ?: initialData?.getString("time")

    var amount by remember { mutableStateOf(initialAmount) }
    var note by remember { mutableStateOf(initialNote) }
    
    val calendar = Calendar.getInstance()
    var date by remember { mutableStateOf(initialDate ?: String.format("%d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))) }
    var time by remember { mutableStateOf(initialTime ?: String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))) }

    val accounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val categories by viewModel.getEnabledCategoriesByType(type).collectAsState(initial = emptyList())
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())

    var selectedAccountId by remember { mutableStateOf(txnDetail?.transaction?.accountId) }
    var selectedToAccountId by remember { mutableStateOf(txnDetail?.transaction?.toAccountId) }
    var selectedCategoryId by remember { mutableStateOf(txnDetail?.transaction?.categoryId) }
    
    val selectedTagIds = remember { 
        val list = mutableStateListOf<Int>()
        txnDetail?.transaction?.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { list.addAll(it) }
        list
    }

    var showCalculator by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (readOnly) "Transaction Details" else "Add Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Transaction Number Field (Read-only)
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
                    onClick = { if (!readOnly) { type = "transfer"; selectedAccountId = null; selectedToAccountId = null } }, 
                    label = { Text("Transfer") },
                    enabled = !readOnly || type == "transfer"
                )
            }

            Spacer(Modifier.height(8.dp))

            // Date and Time side by side
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

            if (type == "transfer") {
                AccountSelectionDialog(
                    label = "From Account",
                    accounts = accounts,
                    selectedId = selectedAccountId,
                    onSelected = { selectedAccountId = it },
                    enabled = !readOnly
                )
                AccountSelectionDialog(
                    label = "To Account",
                    accounts = accounts,
                    selectedId = selectedToAccountId,
                    onSelected = { selectedToAccountId = it },
                    enabled = !readOnly
                )
            } else {
                AccountSelectionDialog(
                    label = "Account",
                    accounts = accounts,
                    selectedId = selectedAccountId,
                    onSelected = { selectedAccountId = it },
                    enabled = !readOnly
                )
                CategorySelectionDialog(
                    label = "Category",
                    categories = categories,
                    selectedId = selectedCategoryId,
                    onSelected = { selectedCategoryId = it },
                    enabled = !readOnly
                )
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { if (!readOnly) amount = it },
                label = { Text("Amount") },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                trailingIcon = {
                    if (!readOnly) {
                        IconButton(onClick = { showCalculator = !showCalculator }) {
                            Icon(Icons.Default.Calculate, "Calculator")
                        }
                    }
                }
            )

            if (showCalculator && !readOnly) {
                CalculatorKeypad(onValueChange = { amount = it }, currentValue = amount)
            }

            TagSelectionPopup(
                allTags = allTags,
                selectedIds = selectedTagIds,
                multiSelect = viewModel.multiTagEnabled,
                enabled = !readOnly
            )

            OutlinedTextField(
                value = note,
                onValueChange = { if (!readOnly) note = it },
                label = { Text("Note") },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                minLines = 3
            )

            if (!readOnly) {
                Button(
                    onClick = {
                        val amt = evaluateExpression(amount)
                        val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                        if (type == "transfer") {
                            if (selectedAccountId != null && selectedToAccountId != null) {
                                viewModel.addTransaction(date, time, selectedAccountId!!, null, amt, note, selectedToAccountId, tagsString, type)
                                onBack()
                            }
                        } else {
                            if (selectedCategoryId != null && selectedAccountId != null) {
                                viewModel.addTransaction(date, time, selectedAccountId!!, selectedCategoryId, amt, note, null, tagsString, type)
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Save Transaction")
                }
            } else {
                Spacer(Modifier.height(24.dp))
                Text("Viewing archived transaction data.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun AccountSelectionDialog(label: String, accounts: List<com.openapps.fintrack.data.Account>, selectedId: Int?, onSelected: (Int) -> Unit, enabled: Boolean = true) {
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
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun CategorySelectionDialog(label: String, categories: List<com.openapps.fintrack.data.Category>, selectedId: Int?, onSelected: (Int) -> Unit, enabled: Boolean = true) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selectedId }?.name ?: "Select Category"

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
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
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
                                        if (tag.id in selectedIds) selectedIds.remove(tag.id)
                                        else selectedIds.add(tag.id)
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
