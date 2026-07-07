/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapps.fintrack.data.Budget
import com.openapps.fintrack.data.Category
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsMainScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            Button(
                onClick = { onNavigate("manage_budgets") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Set / Remove Budget")
            }
            
            Button(
                onClick = { onNavigate("budget_vs_actual") },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text("Budget vs Actual")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageBudgetsScreen(
    viewModel: ExpenseViewModel,
    onEditBudget: () -> Unit,
    onBack: () -> Unit
) {
    val budgets by viewModel.getAllBudgets().collectAsState(initial = emptyList())
    val categories by viewModel.getAllCategories().collectAsState(initial = emptyList())
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set / Remove Budget") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    viewModel.editingBudgetRaw = null
                    onEditBudget() 
                }
            ) {
                Icon(Icons.Default.Add, "Add Budget")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(budgets) { budget ->
                val catNames = budget.categoryIds.split(",")
                    .mapNotNull { id -> categories.find { it.id == id.toIntOrNull() }?.name }
                    .joinToString(", ")
                
                ListItem(
                    headlineContent = { Text(budget.name ?: catNames) },
                    supportingContent = { 
                        Text("${budget.duration} | ${viewModel.formatAmount(budget.amount)}") 
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { 
                                viewModel.editingBudgetRaw = budget
                                onEditBudget() 
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { viewModel.deleteBudget(budget) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                )
                Divider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBudgetScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val categories by viewModel.getEnabledCategories().collectAsState(initial = emptyList())
    val ccAccounts by viewModel.getCreditCardAccounts().collectAsState(initial = emptyList())
    
    var budgetName by remember { mutableStateOf(viewModel.editingBudgetRaw?.name ?: "") }
    val selectedCategoryIds = remember { 
        val list = mutableStateListOf<Int>()
        viewModel.editingBudgetRaw?.categoryIds?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { list.addAll(it) }
        list
    }
    val selectedAccountIds = remember {
        val list = mutableStateListOf<Int>()
        viewModel.editingBudgetRaw?.accountIds?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { list.addAll(it) }
        list
    }
    var amount by remember { mutableStateOf(viewModel.editingBudgetRaw?.amount?.toString() ?: "") }
    var duration by remember { mutableStateOf(viewModel.editingBudgetRaw?.duration ?: "Monthly") }
    var note by remember { mutableStateOf(viewModel.editingBudgetRaw?.note ?: "") }
    var higherIsBetter by remember { mutableStateOf(viewModel.editingBudgetRaw?.higherIsBetter ?: false) }

    var showCategoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.editingBudgetRaw == null) "Add Budget" else "Edit Budget") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(
                value = budgetName,
                onValueChange = { budgetName = it },
                label = { Text("Budget Name (Optional for single category)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            val selectedNames = (categories.filter { it.id in selectedCategoryIds }.map { it.name } + 
                                 ccAccounts.filter { it.id in selectedAccountIds }.map { it.name }).joinToString(", ")
            
            OutlinedTextField(
                value = if (selectedNames.isEmpty()) "Select Categories / Cards" else selectedNames,
                onValueChange = {},
                label = { Text("Categories / Credit Cards") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth().clickable { showCategoryDialog = true },
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, "") }
            )
            
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(16.dp))

            var durationExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = {},
                    label = { Text("Duration") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable { durationExpanded = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, "") }
                )
                DropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                    listOf("Daily", "Weekly", "Monthly", "Half Yearly", "Yearly").forEach { d ->
                        DropdownMenuItem(text = { Text(d) }, onClick = { duration = d; durationExpanded = false })
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = !higherIsBetter, onClick = { higherIsBetter = false })
                Text("Lower the better", modifier = Modifier.clickable { higherIsBetter = false })
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = higherIsBetter, onClick = { higherIsBetter = true })
                Text("Higher the better", modifier = Modifier.clickable { higherIsBetter = true })
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            
            Spacer(Modifier.weight(1f))
            
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (selectedCategoryIds.isNotEmpty() || selectedAccountIds.isNotEmpty()) {
                        viewModel.saveBudgetRaw(
                            budgetName.takeIf { it.isNotBlank() },
                            selectedCategoryIds.joinToString(","),
                            amt, 
                            duration, 
                            note,
                            higherIsBetter,
                            selectedAccountIds.joinToString(",")
                        )
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = (selectedCategoryIds.isNotEmpty() || selectedAccountIds.isNotEmpty()) && amount.isNotEmpty()
            ) {
                Text("Save Budget")
            }
        }
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Select Categories / Cards") },
            text = {
                Box(Modifier.height(400.dp)) {
                    LazyColumn {
                        item {
                            Text("Categories", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                        }
                        items(categories) { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (category.id in selectedCategoryIds) {
                                            selectedCategoryIds.remove(category.id)
                                        } else {
                                            selectedCategoryIds.add(category.id)
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = category.id in selectedCategoryIds,
                                    onCheckedChange = {
                                        if (category.id in selectedCategoryIds) {
                                            selectedCategoryIds.remove(category.id)
                                        } else {
                                            selectedCategoryIds.add(category.id)
                                        }
                                    }
                                )
                                Column {
                                    Text(category.name)
                                    Text(category.type.title(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                }
                            }
                            Divider()
                        }
                        
                        if (ccAccounts.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(16.dp))
                                Text("Credit Cards (Micro Heads)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(8.dp))
                            }
                            items(ccAccounts) { acc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (acc.id in selectedAccountIds) {
                                                selectedAccountIds.remove(acc.id)
                                            } else {
                                                selectedAccountIds.add(acc.id)
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = acc.id in selectedAccountIds,
                                        onCheckedChange = {
                                            if (acc.id in selectedAccountIds) {
                                                selectedAccountIds.remove(acc.id)
                                            } else {
                                                selectedAccountIds.add(acc.id)
                                            }
                                        }
                                    )
                                    Column {
                                        Text(acc.name)
                                        Text("Credit Card Expense Tracking", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                                Divider()
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showCategoryDialog = false }) { Text("Done") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun BudgetComparisonScreen(viewModel: ExpenseViewModel, onBack: () -> Unit, isTab: Boolean = false) {
    var asOfDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    val budgetVsActual by viewModel.getBudgetVsActual(asOfDate).collectAsState(initial = emptyList())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val context = LocalContext.current

    var selectedPerformance by remember { mutableStateOf<BudgetVsActual?>(null) }

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, readOnly = true)
    } else if (selectedPerformance != null) {
        BackHandler { selectedPerformance = null }
        val allAccounts by viewModel.getAllAccounts().collectAsState(initial = emptyList())
        val filteredTxns = remember(allTransactions, selectedPerformance, allAccounts) {
            val perf = selectedPerformance!!
            val ccAccountIds = allAccounts.filter { it.id in perf.accountIds }.map { it.id }
            allTransactions.filter { 
                (it.transaction.categoryId in perf.categoryIds) || (it.transaction.accountId in ccAccountIds && it.transaction.categoryId != null)
            }.filter {
                it.transaction.date >= perf.startDate &&
                it.transaction.date <= perf.endDate
            }
        }
        TransactionListOverlay(
            title = selectedPerformance!!.categoryName,
            transactions = filteredTxns,
            viewModel = viewModel,
            onBack = { selectedPerformance = null }
        )
    } else {
        val content: @Composable (PaddingValues) -> Unit = { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("As of: $asOfDate", style = MaterialTheme.typography.labelMedium)
                    if (isTab) {
                        Row {
                            IconButton(onClick = {
                                val date = try { LocalDate.parse(asOfDate) } catch(e: Exception) { LocalDate.now() }
                                DatePickerDialog(context, { _, y, m, d ->
                                    asOfDate = LocalDate.of(y, m + 1, d).format(DateTimeFormatter.ISO_DATE)
                                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                            }) {
                                Icon(Icons.Default.Event, "Change Date")
                            }
                            IconButton(onClick = { 
                                exportBudgetVsActual(context, budgetVsActual, asOfDate)
                            }) {
                                Icon(Icons.Default.FileDownload, "Export")
                            }
                        }
                    }
                }
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(budgetVsActual) { item ->
                        BudgetComparisonRow(item, viewModel) {
                            selectedPerformance = item
                        }
                        Divider(Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }

        if (isTab) {
            content(PaddingValues(0.dp))
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Budget vs Actual") },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                val date = try { LocalDate.parse(asOfDate) } catch(e: Exception) { LocalDate.now() }
                                DatePickerDialog(context, { _, y, m, d ->
                                    asOfDate = LocalDate.of(y, m + 1, d).format(DateTimeFormatter.ISO_DATE)
                                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                            }) {
                                Icon(Icons.Default.Event, "Change Date")
                            }
                            IconButton(onClick = { 
                                exportBudgetVsActual(context, budgetVsActual, asOfDate)
                            }) {
                                Icon(Icons.Default.FileDownload, "Export")
                            }
                        }
                    )
                }
            ) { padding ->
                content(padding)
            }
        }
    }
}

@Composable
fun BudgetComparisonRow(item: com.openapps.fintrack.ui.BudgetVsActual, viewModel: ExpenseViewModel, onClick: () -> Unit) {
    val isGoalMet = if (item.higherIsBetter) {
        item.actualAmount >= item.budgetAmount
    } else {
        item.actualAmount <= item.budgetAmount
    }
    
    val statusColor = if (isGoalMet) Color(0xFF4CAF50) else Color.Red
    
    val averageText = remember(item) {
        val today = LocalDate.now()
        try {
            when (item.duration) {
                "Daily" -> "Avg Daily: " + viewModel.formatAmount(item.actualAmount)
                "Weekly" -> {
                    val dayOfWeek = today.dayOfWeek.value
                    "Avg Daily: " + viewModel.formatAmount(item.actualAmount / dayOfWeek)
                }
                "Monthly" -> {
                    val dayOfMonth = today.dayOfMonth
                    "Avg Daily: " + viewModel.formatAmount(item.actualAmount / dayOfMonth)
                }
                "Half Yearly" -> {
                    val monthOfHalfYear = if (today.monthValue <= 6) today.monthValue else today.monthValue - 6
                    "Avg Monthly: " + viewModel.formatAmount(item.actualAmount / monthOfHalfYear)
                }
                "Yearly" -> {
                    val monthOfYear = today.monthValue
                    "Avg Monthly: " + viewModel.formatAmount(item.actualAmount / monthOfYear)
                }
                else -> ""
            }
        } catch (e: Exception) { "" }
    }

    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                item.categoryName, 
                fontWeight = FontWeight.Bold, 
                fontSize = 16.sp,
                color = statusColor
            )
            Text(item.duration, style = MaterialTheme.typography.labelSmall)
        }
        if (averageText.isNotEmpty()) {
            Text(averageText, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Budget", style = MaterialTheme.typography.labelSmall)
                Text(viewModel.formatAmount(item.budgetAmount))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Actual", style = MaterialTheme.typography.labelSmall)
                Text(
                    viewModel.formatAmount(item.actualAmount),
                    color = statusColor
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val diff = item.actualAmount - item.budgetAmount
                Text("Diff", style = MaterialTheme.typography.labelSmall)
                Text(viewModel.formatAmount(diff), color = statusColor)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        val progress = if (item.budgetAmount > 0) (item.actualAmount / item.budgetAmount).toFloat().coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = statusColor,
            trackColor = statusColor.copy(alpha = 0.2f)
        )
    }
}

fun exportBudgetVsActual(context: Context, data: List<com.openapps.fintrack.ui.BudgetVsActual>, asOfDate: String) {
    val fileName = "budget_report_$asOfDate.csv"
    val path = File(context.getExternalFilesDir(null), fileName)
    
    try {
        path.bufferedWriter().use { out ->
            out.write("Category,Type,Duration,Budget,Actual,Difference\n")
            data.forEach { d ->
                val diff = d.budgetAmount - d.actualAmount
                out.write("${d.categoryName},${d.categoryType},${d.duration},${d.budgetAmount},${d.actualAmount},$diff\n")
            }
        }
        Toast.makeText(context, "Export Successful!\nSaved to: ${path.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun String.title() = this.lowercase().replaceFirstChar { it.uppercase() }
