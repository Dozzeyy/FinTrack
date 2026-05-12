/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.openapps.fintrack.data.AccountBalance
import com.openapps.fintrack.data.TransactionWithDetails
import com.openapps.fintrack.data.PartyBalance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun TransactionRow(detail: TransactionWithDetails, viewModel: ExpenseViewModel, showTxnNumber: Boolean = false) {
    val transaction = detail.transaction
    val color = when (detail.categoryType) {
        "income" -> Color(0xFF4CAF50)
        "expense" -> Color.Red
        else -> Color(0xFF2196F3)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (showTxnNumber && !transaction.transactionNumber.isNullOrEmpty()) {
                Text(
                    text = "Txn: ${transaction.transactionNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.8f)
                )
            }
            Text(
                text = (detail.categoryName ?: "Transfer") + 
                       (if (detail.partyName != null) " (${detail.partyName})" else "") +
                       (if (detail.toPartyName != null) " -> ${detail.toPartyName}" else ""),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${transaction.date} | ${detail.accountName}${if (detail.toAccountName != null) " -> " + detail.toAccountName else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            if (!transaction.note.isNullOrEmpty()) {
                Text(
                    text = transaction.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
        Text(
            text = viewModel.formatAmount(transaction.amount),
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
    }
}

@Composable
fun PieChart(data: List<Pair<String, Double>>, colors: List<Color>) {
    val total = data.sumOf { it.second }
    if (total <= 0.0) return

    Canvas(modifier = Modifier.size(150.dp)) {
        var startAngle = -90f
        data.forEachIndexed { index, pair ->
            val sweepAngle = (pair.second / total * 360).toFloat()
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                size = Size(size.width, size.height)
            )
            startAngle += sweepAngle
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryView(viewModel: ExpenseViewModel, onOpenDrawer: (() -> Unit)? = null) {
    var month by remember { mutableStateOf(LocalDate.now()) }
    var startDate by remember { mutableStateOf(month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)) }
    var showFilterDialog by remember { mutableStateOf(false) }
    val filterTypes = remember { mutableStateListOf("All") }
    
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val selectedTagIds = remember { mutableStateListOf<Int>() }
    var showTagFilterDialog by remember { mutableStateOf(false) }

    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    val context = LocalContext.current

    val filteredTransactions = remember(transactions, filterTypes.toList(), selectedTagIds.toList()) {
        val baseFiltered = if (filterTypes.contains("All")) transactions
        else {
            transactions.filter {
                (filterTypes.contains("Income") && it.categoryType == "income") ||
                (filterTypes.contains("Expense") && it.categoryType == "expense") ||
                (filterTypes.contains("Transfer") && it.categoryType == null)
            }
        }

        if (selectedTagIds.isEmpty()) baseFiltered
        else {
            baseFiltered.filter { detail ->
                val tTags = detail.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                selectedTagIds.any { it in tTags }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { exportToUri(context, filteredTransactions, "CSV", it) }
        }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onOpenDrawer != null) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                }
                Text("Transactions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }

        // Month Navigation
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            IconButton(onClick = { 
                month = month.minusMonths(1)
                startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
            }) { Icon(Icons.Default.ChevronLeft, "") }
            
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), 
                modifier = Modifier.weight(1f).clickable { showFilterDialog = true }, 
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            IconButton(onClick = { 
                month = month.plusMonths(1)
                startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
            }) { Icon(Icons.Default.ChevronRight, "") }

            IconButton(onClick = { showFilterDialog = true }) {
                Icon(Icons.Default.DateRange, contentDescription = "Filter")
            }

            IconButton(onClick = { 
                exportLauncher.launch("transactions_${month.format(DateTimeFormatter.ofPattern("MMM_yyyy"))}.csv")
            }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export")
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("All", "Income", "Expense", "Transfer").forEach { type ->
                val isSelected = filterTypes.contains(type)
                FilterChip(
                    selected = isSelected,
                    onClick = { 
                        if (type == "All") {
                            filterTypes.clear()
                            filterTypes.add("All")
                        } else {
                            if (isSelected) {
                                filterTypes.remove(type)
                            } else {
                                filterTypes.remove("All")
                                filterTypes.add(type)
                            }
                            if (filterTypes.isEmpty()) filterTypes.add("All")
                        }
                    },
                    label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                )
            }
            
            FilterChip(
                selected = selectedTagIds.isNotEmpty(),
                onClick = { showTagFilterDialog = true },
                label = { Text("Tags", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = if (selectedTagIds.isNotEmpty()) {
                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                } else null,
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
            )
        }
        
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(filteredTransactions) { detail ->
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

    if (showTagFilterDialog) {
        AlertDialog(
            onDismissRequest = { showTagFilterDialog = false },
            title = { Text("Filter by Tags") },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedTagIds.clear() }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedTagIds.isEmpty(), onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text("All (No Tag Filter)")
                    }
                    Divider()
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(allTags) { tag ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (selectedTagIds.contains(tag.id)) selectedTagIds.remove(tag.id)
                                    else selectedTagIds.add(tag.id)
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = selectedTagIds.contains(tag.id), onCheckedChange = null)
                                Spacer(Modifier.width(8.dp))
                                Text(tag.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagFilterDialog = false }) { Text("Apply") }
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

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { 
                exportToUri(context, balances.map { b ->
                    TransactionWithDetails(
                        transaction = com.openapps.fintrack.data.Transaction(date = asOfDate, time = "00:00", accountId = b.id, amount = b.balance, note = "Balance Export", categoryId = null),
                        categoryName = null,
                        categoryType = if (b.balance >= 0) "asset" else "liability",
                        accountName = b.name,
                        toAccountName = null,
                        partyName = null,
                        toPartyName = null
                    )
                }, "CSV", it) 
            }
        }
    )

    if (selectedAccountForDetail == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "As of: $asOfDate", modifier = Modifier.clickable {
                    val date = try { LocalDate.parse(asOfDate) } catch(e: Exception) { LocalDate.now() }
                    DatePickerDialog(context, { _, year, month, day ->
                        asOfDate = LocalDate.of(year, month + 1, day).format(DateTimeFormatter.ISO_DATE)
                    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                })
                IconButton(onClick = { 
                    exportLauncher.launch("balances_$asOfDate.csv")
                }) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export")
                }
            }
            
            val assets = balances.filter { it.balance > 0 && !it.name.equals("On Account", ignoreCase = true) }
            val liabilities = balances.filter { it.balance < 0 && !it.name.equals("On Account", ignoreCase = true) }
            
            val partyBalances by viewModel.getPartyBalances(asOfDate).collectAsState(initial = emptyList())
            val onAccountNet = partyBalances.sumOf { it.balance }
            
            val totalAssets = assets.sumOf { it.balance } + (if (onAccountNet > 0) onAccountNet else 0.0)
            val totalLiabilities = liabilities.sumOf { it.balance } + (if (onAccountNet < 0) onAccountNet else 0.0)

            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                item { 
                    Text("Assets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                }
                
                items(assets) { balance ->
                    AccountBalanceRow(balance, viewModel) { selectedAccountForDetail = balance }
                }
                
                if (onAccountNet != 0.0) {
                    item {
                        val accBal = AccountBalance(0, "On Account", "asset", 0.0, onAccountNet)
                        AccountBalanceRow(accBal, viewModel) { selectedAccountForDetail = accBal }
                    }
                }

                item {
                    SubtotalRow("Sub-total Assets", totalAssets, viewModel, Color(0xFF4CAF50))
                }

                if (liabilities.isNotEmpty() || onAccountNet < 0) {
                    item { 
                        Text("Liabilities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    items(liabilities) { balance ->
                        AccountBalanceRow(balance, viewModel) { selectedAccountForDetail = balance }
                    }
                    item {
                        SubtotalRow("Sub-total Liabilities", totalLiabilities, viewModel, Color.Red)
                    }
                }
                
                item {
                    val netPosition = totalAssets + totalLiabilities
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer, 
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Net Position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(viewModel.formatAmount(netPosition), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    } else {
        if (selectedAccountForDetail?.name?.equals("On Account", ignoreCase = true) == true) {
            OnAccountPartyBalancesView(viewModel, asOfDate, onBack = { selectedAccountForDetail = null })
        } else {
            AccountDetailView(viewModel, selectedAccountForDetail!!, onBack = { selectedAccountForDetail = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAccountPartyBalancesView(viewModel: ExpenseViewModel, asOfDate: String, onBack: () -> Unit) {
    val allPartiesRaw by viewModel.getAllParties().collectAsState(initial = emptyList())
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val allCategories by viewModel.getAllCategories().collectAsState(initial = emptyList())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    
    var filterType by remember { mutableStateOf("Both") } 
    val selectedTagIds = remember { mutableStateListOf<Int>() }
    val selectedCategoryIds = remember { mutableStateListOf<Int>() }
    
    var showTagFilter by remember { mutableStateOf(false) }
    var showCategoryFilter by remember { mutableStateOf(false) }
    var selectedPartyForHistory by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val displayParties = remember(allPartiesRaw, filterType, selectedTagIds.toList(), selectedCategoryIds.toList(), allTransactions, asOfDate) {
        allPartiesRaw.filter { it.isEnabled }.map { p ->
            // Filter transactions associated with this party (either as sender or receiver)
            val partyTxnsIn = allTransactions.filter { t ->
                (t.transaction.toPartyId == p.id || (t.transaction.partyId == p.id && t.categoryType == "income")) &&
                t.transaction.date <= asOfDate &&
                (selectedTagIds.isEmpty() || (t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in selectedTagIds } == true)) &&
                (selectedCategoryIds.isEmpty() || t.transaction.categoryId in selectedCategoryIds)
            }
            
            val partyTxnsOut = allTransactions.filter { t ->
                (t.transaction.partyId == p.id && (t.categoryType == "expense" || t.transaction.categoryId == null)) &&
                t.transaction.date <= asOfDate &&
                (selectedTagIds.isEmpty() || (t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in selectedTagIds } == true)) &&
                (selectedCategoryIds.isEmpty() || t.transaction.categoryId in selectedCategoryIds)
            }
            
            val inc = partyTxnsIn.sumOf { it.transaction.amount }
            val exp = partyTxnsOut.sumOf { it.transaction.amount }
            
            // Opening balance is only included if no categorical/tag filters are active
            val opening = if (selectedTagIds.isEmpty() && selectedCategoryIds.isEmpty()) p.openingBalance else 0.0
            val currentFilteredBalance = opening + inc - exp
            
            PartyBalance(p.id, p.name, currentFilteredBalance)
        }.filter { p ->
            val matchType = when (filterType) {
                "Receivable" -> p.balance > 0
                "Payable" -> p.balance < 0
                else -> true
            }
            val isFiltered = selectedTagIds.isNotEmpty() || selectedCategoryIds.isNotEmpty()
            if (isFiltered) {
                // If filtered, only show if they have transactions matching the filter
                val hasMatch = allTransactions.any { t ->
                    (t.transaction.partyId == p.id || t.transaction.toPartyId == p.id) &&
                    t.transaction.date <= asOfDate &&
                    (selectedTagIds.isEmpty() || (t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in selectedTagIds } == true)) &&
                    (selectedCategoryIds.isEmpty() || t.transaction.categoryId in selectedCategoryIds)
                }
                matchType && hasMatch
            } else {
                matchType
            }
        }
    }

    val balancesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let {
                val csv = StringBuilder("Party Name,Balance\n")
                displayParties.forEach { csv.append("${it.name},${it.balance}\n") }
                context.contentResolver.openOutputStream(it)?.use { out -> out.write(csv.toString().toByteArray()) }
                Toast.makeText(context, "Balances Exported", Toast.LENGTH_SHORT).show()
            }
        }
    )
    val txnsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let {
                val csv = StringBuilder("Date,Time,Party,Category,Amount,Note\n")
                val partyIds = displayParties.map { it.id }
                val txns = allTransactions.filter { t ->
                    t.transaction.partyId in partyIds && t.transaction.date <= asOfDate &&
                    (selectedTagIds.isEmpty() || (t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in selectedTagIds } == true)) &&
                    (selectedCategoryIds.isEmpty() || t.transaction.categoryId in selectedCategoryIds)
                }
                txns.forEach { t ->
                    csv.append("${t.transaction.date},${t.transaction.time},${t.partyName ?: ""},${t.categoryName ?: ""},${t.transaction.amount},\"${t.transaction.note ?: ""}\"\n")
                }
                context.contentResolver.openOutputStream(it)?.use { out -> out.write(csv.toString().toByteArray()) }
                Toast.makeText(context, "Transactions Exported", Toast.LENGTH_SHORT).show()
            }
        }
    )

    if (selectedPartyForHistory != null) {
        BackHandler { selectedPartyForHistory = null }
        PartyTransactionsOverlay(
            viewModel = viewModel, 
            partyId = selectedPartyForHistory!!.first, 
            partyName = selectedPartyForHistory!!.second,
            filterTagIds = selectedTagIds.toList(),
            filterCategoryIds = selectedCategoryIds.toList(),
            asOfDate = asOfDate
        ) { selectedPartyForHistory = null }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") }
                Text("On Account - Parties", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.FileDownload, "Export") }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Both", "Receivable", "Payable").forEach { t ->
                    FilterChip(selected = filterType == t, onClick = { filterType = t }, label = { Text(t, style = MaterialTheme.typography.labelSmall) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                FilterChip(selected = selectedTagIds.isNotEmpty(), onClick = { showTagFilter = true }, label = { Text("Tags (${selectedTagIds.size})") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = selectedCategoryIds.isNotEmpty(), onClick = { showCategoryFilter = true }, label = { Text("Categories (${selectedCategoryIds.size})") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
            }
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(displayParties) { p ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { selectedPartyForHistory = p.id to p.name }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name); Text(viewModel.formatAmount(p.balance), color = if (p.balance >= 0) Color(0xFF4CAF50) else Color.Red)
                    }
                    Divider()
                }
            }
        }
    }

    if (showTagFilter) {
        AlertDialog(onDismissRequest = { showTagFilter = false }, title = { Text("Filter by Tags") }, text = {
            Box(Modifier.height(300.dp)) {
                LazyColumn {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedTagIds.clear() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedTagIds.isEmpty(), onClick = null); Text("All", modifier = Modifier.padding(start = 8.dp))
                        }
                        Divider()
                    }
                    items(allTags) { tag ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { if (selectedTagIds.contains(tag.id)) selectedTagIds.remove(tag.id) else selectedTagIds.add(tag.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedTagIds.contains(tag.id), onCheckedChange = null); Text(tag.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showTagFilter = false }) { Text("Done") } })
    }

    if (showCategoryFilter) {
        AlertDialog(onDismissRequest = { showCategoryFilter = false }, title = { Text("Filter by Categories") }, text = {
            Box(Modifier.height(300.dp)) {
                LazyColumn {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedCategoryIds.clear() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedCategoryIds.isEmpty(), onClick = null); Text("All", modifier = Modifier.padding(start = 8.dp))
                        }
                        Divider()
                    }
                    items(allCategories) { cat ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { if (selectedCategoryIds.contains(cat.id)) selectedCategoryIds.remove(cat.id) else selectedCategoryIds.add(cat.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedCategoryIds.contains(cat.id), onCheckedChange = null); Text("${cat.name} (${cat.type})", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showCategoryFilter = false }) { Text("Done") } })
    }

    if (showExportDialog) {
        AlertDialog(onDismissRequest = { showExportDialog = false }, title = { Text("Export On Account Data") }, text = { Text("Choose export type:") },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { showExportDialog = false; balancesLauncher.launch("party_balances_${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export Party Balances") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showExportDialog = false; txnsLauncher.launch("party_transactions_${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export Transactions Detail") }
                }
            }, dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyTransactionsOverlay(viewModel: ExpenseViewModel, partyId: Int, partyName: String, filterTagIds: List<Int>, filterCategoryIds: List<Int>, asOfDate: String, onBack: () -> Unit) {
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val partyTransactions = remember(allTransactions, partyId, filterTagIds, filterCategoryIds, asOfDate) {
        allTransactions.filter { t ->
            (t.transaction.partyId == partyId || t.transaction.toPartyId == partyId) &&
            t.transaction.date <= asOfDate &&
            (filterTagIds.isEmpty() || (t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in filterTagIds } == true)) &&
            (filterCategoryIds.isEmpty() || t.transaction.categoryId in filterCategoryIds)
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(partyName) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") } }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            items(partyTransactions) { detail ->
                Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = detail }) { TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = true) }
                Divider()
            }
        }
    }
}

@Composable
fun AccountBalanceRow(balance: AccountBalance, viewModel: ExpenseViewModel, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(balance.name); Text(viewModel.formatAmount(balance.balance), color = if (balance.balance >= 0) Color(0xFF4CAF50) else Color.Red)
    }
    Divider()
}

@Composable
fun SubtotalRow(label: String, total: Double, viewModel: ExpenseViewModel, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.Bold); Text(viewModel.formatAmount(total), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AccountDetailView(viewModel: ExpenseViewModel, account: AccountBalance, onBack: () -> Unit) {
    var month by remember { mutableStateOf(LocalDate.now()) }
    var startDate by remember { mutableStateOf(month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)) }
    var showFilterDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val transactions by viewModel.getAccountTransactions(account.id, startDate, endDate).collectAsState(initial = emptyList())
    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("text/csv"), onResult = { uri -> uri?.let { exportToUri(context, transactions, "CSV", it) } })
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") }
            Text(account.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            IconButton(onClick = { month = month.minusMonths(1); startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE); endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE) }) { Icon(Icons.Default.ChevronLeft, "") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), modifier = Modifier.weight(1f).clickable { showFilterDialog = true }, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { month = month.plusMonths(1); startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE); endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE) }) { Icon(Icons.Default.ChevronRight, "") }
            IconButton(onClick = { showFilterDialog = true }) { Icon(Icons.Default.DateRange, contentDescription = "Filter") }
            IconButton(onClick = { exportLauncher.launch("${account.name.replace(" ", "_")}_${month.format(DateTimeFormatter.ofPattern("MMM_yyyy"))}.csv") }) { Icon(Icons.Default.FileDownload, contentDescription = "Export") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(transactions) { detail ->
                Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = detail }) { TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = true) }
                Divider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
    if (showFilterDialog) { DateRangeFilterDialog(onDismiss = { showFilterDialog = false }, onApply = { s, e -> startDate = s; endDate = e; showFilterDialog = false }) }
}

@Composable
fun ExportView(viewModel: ExpenseViewModel) {
    var format by remember { mutableStateOf("CSV") }
    val transactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument(if (format == "CSV") "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), onResult = { uri -> uri?.let { exportToUri(context, transactions, format, it) } })
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Export Data", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(16.dp))
        Row {
            RadioButton(selected = format == "CSV", onClick = { format = "CSV" }); Text("CSV", modifier = Modifier.align(Alignment.CenterVertically)); Spacer(Modifier.width(16.dp))
            RadioButton(selected = format == "Excel", onClick = { format = "Excel" }); Text("Excel (XLSX)", modifier = Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = { exportLauncher.launch("fintrack_export_${System.currentTimeMillis()}.${if (format == "CSV") "csv" else "xlsx"}") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Select Destination and Export")
        }
    }
}

fun exportToUri(context: Context, data: List<TransactionWithDetails>, format: String, uri: Uri) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.bufferedWriter().use { out ->
                if (format == "CSV") {
                    out.write("TxnNum,Date,Time,Account,ToAccount,Party,Category,Type,Amount,Note\n")
                    data.forEach { d ->
                        val t = d.transaction
                        out.write("${t.transactionNumber ?: ""},${t.date},${t.time},${d.accountName},${d.toAccountName ?: ""},${d.partyName ?: ""},${d.categoryName ?: ""},${d.categoryType ?: "transfer"},${t.amount},\"${t.note ?: ""}\"\n")
                    }
                } else {
                    out.write("TxnNum\tDate\tTime\tAccount\tToAccount\tParty\tCategory\tType\tAmount\tNote\n")
                    data.forEach { d ->
                        val t = d.transaction
                        out.write("${t.transactionNumber ?: ""}\t${t.date}\t${t.time}\t${d.accountName}\t${d.toAccountName ?: ""}\t${d.partyName ?: ""}\t${d.categoryName ?: ""}\t${d.categoryType ?: "transfer"}\t${t.amount}\t${t.note ?: ""}\n")
                    }
                }
            }
        }
        Toast.makeText(context, "Export Successful!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun QrCodeImage(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { try { val encoder = BarcodeEncoder(); encoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 512, 512) } catch (e: Exception) { null } }
    bitmap?.let { androidx.compose.foundation.Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = modifier) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DateRangeFilterDialog(onDismiss: () -> Unit, onApply: (String, String) -> Unit) {
    val context = LocalContext.current
    var start by remember { mutableStateOf(LocalDate.now().minusDays(7)) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Filter by Date") }, text = {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { DatePickerDialog(context, { _, y, m, d -> start = LocalDate.of(y, m+1, d) }, start.year, start.monthValue - 1, start.dayOfMonth).show() }.padding(8.dp)) { Text("From: ${start.format(DateTimeFormatter.ISO_DATE)}") }
            Row(modifier = Modifier.fillMaxWidth().clickable { DatePickerDialog(context, { _, y, m, d -> end = LocalDate.of(y, m+1, d) }, end.year, end.monthValue - 1, end.dayOfMonth).show() }.padding(8.dp)) { Text("To: ${end.format(DateTimeFormatter.ISO_DATE)}") }
            Spacer(Modifier.height(8.dp)); Text("Shortcuts:", style = MaterialTheme.typography.labelSmall)
            val shortcuts = listOf("Today" to { start = LocalDate.now(); end = LocalDate.now() }, "Yesterday" to { start = LocalDate.now().minusDays(1); end = LocalDate.now().minusDays(1) }, "Last 7 Days" to { start = LocalDate.now().minusDays(7); end = LocalDate.now() }, "Last 2 Weeks" to { start = LocalDate.now().minusWeeks(2); end = LocalDate.now() }, "This Month" to { start = LocalDate.now().withDayOfMonth(1); end = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()) }, "Last Month" to { start = LocalDate.now().minusMonths(1).withDayOfMonth(1); end = LocalDate.now().minusMonths(1).with(TemporalAdjusters.lastDayOfMonth()) }, "Last 2 Months" to { start = LocalDate.now().minusMonths(2); end = LocalDate.now() }, "Last 6 Months" to { start = LocalDate.now().minusMonths(6); end = LocalDate.now() }, "This Year" to { start = LocalDate.now().with(TemporalAdjusters.firstDayOfYear()); end = LocalDate.now() })
            androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth()) { shortcuts.forEach { (label, action) -> AssistChip(onClick = action, label = { Text(label) }, modifier = Modifier.padding(2.dp)) } }
        }
    }, confirmButton = { Button(onClick = { onApply(start.format(DateTimeFormatter.ISO_DATE), end.format(DateTimeFormatter.ISO_DATE)) }) { Text("Apply") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) { if (context is Activity) return context; context = context.baseContext }
    return null
}
