package com.openapps.fintrack.ui

import android.app.DatePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.data.AccountBalance
import com.openapps.fintrack.data.TransactionWithDetails
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    var currentSubView by remember { mutableStateOf("Transactions") }
    
    if (viewModel.selectedTransactionDetail != null) {
        AddTransactionScreen(
            viewModel = viewModel,
            onBack = { viewModel.selectedTransactionDetail = null },
            initialData = null, // Logic handled inside AddTransactionScreen using viewModel.selectedTransactionDetail
            readOnly = true
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Summary") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = currentSubView == "Transactions", onClick = { currentSubView = "Transactions" }, label = { Text("Transactions") })
                    FilterChip(selected = currentSubView == "Assets", onClick = { currentSubView = "Assets" }, label = { Text("Assets/Liabilities") })
                    FilterChip(selected = currentSubView == "Export", onClick = { currentSubView = "Export" }, label = { Text("Export") })
                }
                
                when (currentSubView) {
                    "Transactions" -> TransactionHistoryView(viewModel)
                    "Assets" -> AssetsLiabilitiesView(viewModel)
                    "Export" -> ExportView(viewModel)
                }
            }
        }
    }
}

@Composable
fun TransactionHistoryView(viewModel: ExpenseViewModel) {
    var startDate by remember { mutableStateOf(LocalDate.now().minusMonths(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var showFilterDialog by remember { mutableStateOf(false) }
    
    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { showFilterDialog = true }) {
                Icon(Icons.Default.FilterList, contentDescription = "Filter")
            }
            IconButton(onClick = { 
                exportToLocalPath(context, transactions, "CSV")
            }) {
                Icon(Icons.Default.Share, contentDescription = "Export")
            }
        }
        
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(transactions) { detail ->
                Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = detail }) {
                    TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = true)
                }
                Divider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
    
    if (showFilterDialog) {
        DateRangeFilterDialog(
            onDismiss = { showFilterDialog = false },
            onApply = { start, end ->
                startDate = start
                endDate = end
                showFilterDialog = false
            }
        )
    }
}

@Composable
fun AssetsLiabilitiesView(viewModel: ExpenseViewModel) {
    var asOfDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    val balances by viewModel.getAccountBalances(asOfDate).collectAsState(initial = emptyList())
    var selectedAccountForDetail by remember { mutableStateOf<AccountBalance?>(null) }
    val context = LocalContext.current

    if (selectedAccountForDetail == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "As of: $asOfDate", modifier = Modifier.clickable {
                    val date = try { LocalDate.parse(asOfDate) } catch(e: Exception) { LocalDate.now() }
                    DatePickerDialog(context, { _, year, month, day ->
                        asOfDate = LocalDate.of(year, month + 1, day).format(DateTimeFormatter.ISO_DATE)
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                })
                IconButton(onClick = { /* Export */ }) {
                    Icon(Icons.Default.Share, contentDescription = "Export")
                }
            }
            
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(balances) { balance ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedAccountForDetail = balance }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(balance.name)
                        Text(viewModel.formatAmount(balance.balance), color = if (balance.balance >= 0) Color(0xFF4CAF50) else Color.Red)
                    }
                    Divider()
                }
                
                item {
                    val netPosition = balances.sumOf { it.balance }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer, 
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Net Position", style = MaterialTheme.typography.titleMedium)
                            Text(viewModel.formatAmount(netPosition), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    } else {
        AccountDetailView(viewModel, selectedAccountForDetail!!, onBack = { selectedAccountForDetail = null })
    }
}

@Composable
fun AccountDetailView(viewModel: ExpenseViewModel, account: AccountBalance, onBack: () -> Unit) {
    var startDate by remember { mutableStateOf(LocalDate.now().minusMonths(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var showFilterDialog by remember { mutableStateOf(false) }
    
    val transactions by viewModel.getAccountTransactions(account.id, startDate, endDate).collectAsState(initial = emptyList())
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") }
            Text(account.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { showFilterDialog = true }) { Icon(Icons.Default.FilterList, "") }
        }
        
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(transactions) { detail ->
                Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = detail }) {
                    TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = true)
                }
                Divider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
    
    if (showFilterDialog) {
        DateRangeFilterDialog(onDismiss = { showFilterDialog = false }, onApply = { s, e -> startDate = s; endDate = e; showFilterDialog = false })
    }
}

@Composable
fun ExportView(viewModel: ExpenseViewModel) {
    var format by remember { mutableStateOf("CSV") }
    val transactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Export Data", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Row {
            RadioButton(selected = format == "CSV", onClick = { format = "CSV" })
            Text("CSV", modifier = Modifier.align(Alignment.CenterVertically))
            Spacer(Modifier.width(16.dp))
            RadioButton(selected = format == "Excel", onClick = { format = "Excel" })
            Text("Excel (XLSX)", modifier = Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = { 
            exportToLocalPath(context, transactions, format)
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Export to Local Path")
        }
    }
}

fun exportToLocalPath(context: Context, data: List<TransactionWithDetails>, format: String) {
    val fileName = "transactions_${System.currentTimeMillis()}.${format.lowercase()}"
    val path = File(context.getExternalFilesDir(null), fileName)
    
    try {
        if (format == "CSV") {
            path.bufferedWriter().use { out ->
                out.write("TxnNum,Date,Time,Account,ToAccount,Category,Type,Amount,Note\n")
                data.forEach { d ->
                    val t = d.transaction
                    out.write("${t.transactionNumber ?: ""},${t.date},${t.time},${d.accountName},${d.toAccountName ?: ""},${d.categoryName ?: ""},${d.categoryType ?: "transfer"},${t.amount},\"${t.note ?: ""}\"\n")
                }
            }
        } else {
            path.bufferedWriter().use { out ->
                out.write("TxnNum\tDate\tTime\tAccount\tToAccount\tCategory\tType\tAmount\tNote\n")
                data.forEach { d ->
                    val t = d.transaction
                    out.write("${t.transactionNumber ?: ""}\t${t.date}\t${t.time}\t${d.accountName}\t${d.toAccountName ?: ""}\t${d.categoryName ?: ""}\t${d.categoryType ?: "transfer"}\t${t.amount}\t${t.note ?: ""}\n")
                }
            }
        }
        Toast.makeText(context, "Export Successful!\nSaved to: ${path.absolutePath}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DateRangeFilterDialog(onDismiss: () -> Unit, onApply: (String, String) -> Unit) {
    val context = LocalContext.current
    var start by remember { mutableStateOf(LocalDate.now().minusDays(7)) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by Date") },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth().clickable {
                    DatePickerDialog(context, { _, y, m, d -> start = LocalDate.of(y, m+1, d) }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                }.padding(8.dp)) {
                    Text("From: ${start.format(DateTimeFormatter.ISO_DATE)}")
                }
                Row(modifier = Modifier.fillMaxWidth().clickable {
                    DatePickerDialog(context, { _, y, m, d -> end = LocalDate.of(y, m+1, d) }, end.year, end.monthValue - 1, end.dayOfMonth).show()
                }.padding(8.dp)) {
                    Text("To: ${end.format(DateTimeFormatter.ISO_DATE)}")
                }
                
                Spacer(Modifier.height(8.dp))
                Text("Shortcuts:", style = MaterialTheme.typography.labelSmall)
                val shortcuts = listOf(
                    "Today" to { start = LocalDate.now(); end = LocalDate.now() },
                    "Last 7 Days" to { start = LocalDate.now().minusDays(7); end = LocalDate.now() },
                    "Last 2 Weeks" to { start = LocalDate.now().minusWeeks(2); end = LocalDate.now() },
                    "Last Month" to { start = LocalDate.now().minusMonths(1); end = LocalDate.now() },
                    "Last 2 Months" to { start = LocalDate.now().minusMonths(2); end = LocalDate.now() },
                    "Last 6 Months" to { start = LocalDate.now().minusMonths(6); end = LocalDate.now() },
                    "This Year" to { start = LocalDate.now().with(TemporalAdjusters.firstDayOfYear()); end = LocalDate.now() }
                )
                
                FlowRow(modifier = Modifier.fillMaxWidth()) {
                    shortcuts.forEach { (label, action) ->
                        AssistChip(onClick = action, label = { Text(label) }, modifier = Modifier.padding(2.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(start.format(DateTimeFormatter.ISO_DATE), end.format(DateTimeFormatter.ISO_DATE)) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRow(modifier: Modifier = Modifier, content: @Composable (FlowRowScope.() -> Unit)) {
    androidx.compose.foundation.layout.FlowRow(modifier = modifier) {
        content()
    }
}
