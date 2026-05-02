package com.openapps.fintrack.ui

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
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
import com.openapps.fintrack.data.BudgetWithDetails
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
        bottomBar = {
            Button(
                onClick = { 
                    viewModel.editingBudget = null
                    onEditBudget() 
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Budget")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            items(budgets) { budgetDetail ->
                ListItem(
                    headlineContent = { Text(budgetDetail.categoryName) },
                    supportingContent = { 
                        Text("${budgetDetail.categoryType.title()} | ${budgetDetail.budget.duration} | ${viewModel.formatAmount(budgetDetail.budget.amount)}") 
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { 
                                viewModel.editingBudget = budgetDetail
                                onEditBudget() 
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { viewModel.deleteBudget(budgetDetail.budget) }) {
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
    
    var selectedCategoryId by remember { mutableStateOf(viewModel.editingBudget?.budget?.categoryId) }
    var amount by remember { mutableStateOf(viewModel.editingBudget?.budget?.amount?.toString() ?: "") }
    var duration by remember { mutableStateOf(viewModel.editingBudget?.budget?.duration ?: "Monthly") }
    var note by remember { mutableStateOf(viewModel.editingBudget?.budget?.note ?: "") }

    var showCategoryDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.editingBudget == null) "Add Budget" else "Edit Budget") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            val selectedCategory = categories.find { it.id == selectedCategoryId }
            OutlinedTextField(
                value = selectedCategory?.let { "${it.name} (${it.type})" } ?: "Select Category",
                onValueChange = {},
                label = { Text("Category") },
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
                    if (selectedCategoryId != null) {
                        viewModel.saveBudget(selectedCategoryId!!, amt, duration, note)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedCategoryId != null && amount.isNotEmpty()
            ) {
                Text("Save Budget")
            }
        }
    }

    if (showCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            title = { Text("Select Category") },
            text = {
                Box(Modifier.height(400.dp)) {
                    LazyColumn {
                        items(categories) { category ->
                            ListItem(
                                headlineContent = { Text(category.name) },
                                supportingContent = { Text(category.type.title()) },
                                modifier = Modifier.clickable {
                                    selectedCategoryId = category.id
                                    showCategoryDialog = false
                                }
                            )
                            Divider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCategoryDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun BudgetComparisonScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    var asOfDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    val budgetVsActual by viewModel.getBudgetVsActual(asOfDate).collectAsState(initial = emptyList())
    val context = LocalContext.current

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
                        Icon(Icons.Default.Share, "Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("As of: $asOfDate", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(16.dp))
            
            LazyColumn {
                items(budgetVsActual) { item ->
                    BudgetComparisonRow(item, viewModel)
                    Divider(Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun BudgetComparisonRow(item: com.openapps.fintrack.ui.BudgetVsActual, viewModel: ExpenseViewModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.categoryName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(item.duration, style = MaterialTheme.typography.labelSmall)
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
                    color = if (item.categoryType == "expense") {
                        if (item.actualAmount > item.budgetAmount) Color.Red else Color(0xFF4CAF50)
                    } else {
                        if (item.actualAmount < item.budgetAmount) Color.Red else Color(0xFF4CAF50)
                    }
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                val diff = item.budgetAmount - item.actualAmount
                Text("Diff", style = MaterialTheme.typography.labelSmall)
                Text(viewModel.formatAmount(diff))
            }
        }
        
        Spacer(Modifier.height(8.dp))
        val progress = if (item.budgetAmount > 0) (item.actualAmount / item.budgetAmount).toFloat().coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = if (progress > 0.9f && item.categoryType == "expense") Color.Red else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
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
