/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapps.fintrack.data.Account
import com.openapps.fintrack.data.TransactionWithDetails
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CreditCardDashboard(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val accounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val minorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())
    val allMajorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())

    val ccMajorId = remember(allMajorHeads) { allMajorHeads.find { it.name.contains("Credit Card", ignoreCase = true) }?.id ?: 8 }
    val ccAccounts = remember(accounts, minorHeads, ccMajorId) {
        accounts.filter { acc ->
            val minor = minorHeads.find { it.id == acc.minorHeadId }
            minor?.majorHeadId == ccMajorId
        }
    }

    var selectedDetailTransactions by remember { mutableStateOf<List<TransactionWithDetails>?>(null) }
    var detailTitle by remember { mutableStateOf("") }

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(
            viewModel = viewModel, 
            onBack = { viewModel.selectedTransactionDetail = null }, 
            onNavigate = {}, 
            readOnly = true
        )
        return
    }

    if (selectedDetailTransactions != null) {
        BackHandler { selectedDetailTransactions = null }
        TransactionListOverlay(
            title = detailTitle,
            transactions = selectedDetailTransactions!!,
            viewModel = viewModel,
            onBack = { selectedDetailTransactions = null }
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { ccAccounts.size })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credit Cards") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (ccAccounts.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No credit card accounts found.")
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 32.dp),
                    pageSpacing = 16.dp
                ) { page ->
                    val account = ccAccounts[page]
                    CCSummaryCard(
                        account = account,
                        viewModel = viewModel,
                        allTransactions = allTransactions,
                        onShowBilled = { txns, title -> selectedDetailTransactions = txns; detailTitle = title },
                        onShowUnbilled = { txns, title -> selectedDetailTransactions = txns; detailTitle = title }
                    )
                }

                Spacer(Modifier.height(24.dp))

                val currentAccount = ccAccounts[pagerState.currentPage]
                CCExpenseAnalysis(
                    account = currentAccount,
                    viewModel = viewModel,
                    allTransactions = allTransactions,
                    onCategoryClick = { txns, title -> selectedDetailTransactions = txns; detailTitle = title }
                )
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun CCSummaryCard(
    account: Account,
    viewModel: ExpenseViewModel,
    allTransactions: List<TransactionWithDetails>,
    onShowBilled: (List<TransactionWithDetails>, String) -> Unit,
    onShowUnbilled: (List<TransactionWithDetails>, String) -> Unit
) {
    val today = LocalDate.now()
    
    // 1. Total Due (Current Balance)
    val currentBalance = run {
        val dateStr = today.format(DateTimeFormatter.ISO_DATE)
        val txns = allTransactions.filter { it.transaction.date <= dateStr && (it.transaction.accountId == account.id || it.transaction.toAccountId == account.id) }
        val inc = txns.filter { it.transaction.toAccountId == account.id || it.categoryType == "income" }.sumOf { it.transaction.amount }
        val exp = txns.filter { it.transaction.accountId == account.id && it.categoryType != "income" }.sumOf { it.transaction.amount }
        account.openingBalance + inc - exp
    }

    // 2. Billing Cycle Logic
    val startDay = account.billingCycleStart?.toIntOrNull() ?: 1
    val endDay = account.billingCycleEnd?.toIntOrNull() ?: 31
    val daysPost = account.paymentDueDate?.toIntOrNull() ?: 20

    // Immediate previous cycle
    var cycleEnd = try {
        LocalDate.of(today.year, today.monthValue, endDay.coerceAtMost(today.lengthOfMonth()))
    } catch (e: Exception) { today.with(TemporalAdjusters.lastDayOfMonth()) }
    
    if (cycleEnd.isAfter(today)) cycleEnd = cycleEnd.minusMonths(1)
    
    var cycleStart = try {
        var s = cycleEnd.withDayOfMonth(startDay.coerceAtMost(cycleEnd.lengthOfMonth()))
        if (s.isAfter(cycleEnd) || s.isEqual(cycleEnd)) s = s.minusMonths(1)
        s
    } catch (e: Exception) { cycleEnd.minusMonths(1) }

    // Billed Amount (Transactions in previous cycle)
    val billedTransactions = allTransactions.filter { t ->
        (t.transaction.accountId == account.id) &&
        t.transaction.date >= cycleStart.format(DateTimeFormatter.ISO_DATE) &&
        t.transaction.date <= cycleEnd.format(DateTimeFormatter.ISO_DATE) &&
        !(t.transaction.categoryId == null && t.transaction.toAccountId != null) // Exclude transfers OUT
    }
    // Negate for outflow representation
    val billedAmount = -billedTransactions.sumOf { if (it.transaction.toAccountId == account.id) it.transaction.amount else -it.transaction.amount }

    // Last Paid Amount and Date (Transfers TO this card after previous cycle end)
    val paymentsAfterCycle = allTransactions.filter { t ->
        t.transaction.toAccountId == account.id &&
        t.transaction.date > cycleEnd.format(DateTimeFormatter.ISO_DATE) &&
        t.transaction.date <= today.format(DateTimeFormatter.ISO_DATE) &&
        t.transaction.categoryId == null // Pure transfers
    }
    val lastPaidAmount = paymentsAfterCycle.sumOf { it.transaction.amount }
    val lastPaidDate = paymentsAfterCycle.maxByOrNull { it.transaction.date }?.transaction?.date

    // Unbilled Transactions (After cycleEnd, excluding transfers IN)
    val unbilledTransactions = allTransactions.filter { t ->
        (t.transaction.accountId == account.id || t.transaction.toAccountId == account.id) &&
        t.transaction.date > cycleEnd.format(DateTimeFormatter.ISO_DATE) &&
        t.transaction.date <= today.format(DateTimeFormatter.ISO_DATE) &&
        !(t.transaction.toAccountId == account.id && t.transaction.categoryId == null)
    }

    // Unbilled Amount = sum of incomes - sum of expenses (after cycle end) to show as negative debt
    val unbilledAmount = unbilledTransactions.sumOf { 
        if (it.transaction.toAccountId == account.id || it.categoryType == "income") it.transaction.amount 
        else if (it.transaction.accountId == account.id && it.categoryType != "income") -it.transaction.amount
        else 0.0
    }

    // Paid / Alert Dismissal logic
    val dateKey = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    val isPaid = viewModel.dismissedCcAlertIds.contains("${account.id}_$dateKey")
    
    // Next Due Date
    var nextDueDate = cycleEnd.plusDays(daysPost.toLong())
    if (isPaid) {
        val nextCycleEnd = cycleEnd.plusMonths(1)
        nextDueDate = nextCycleEnd.plusDays(daysPost.toLong())
    }

    // Credit Limit
    val totalLimit = account.creditLimit ?: 0.0
    val availableLimit = totalLimit - Math.abs(currentBalance)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp).clickable { onShowBilled(billedTransactions + unbilledTransactions, "${account.name} History") }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(account.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${cycleStart.format(DateTimeFormatter.ofPattern("dd MMM"))} - ${cycleEnd.format(DateTimeFormatter.ofPattern("dd MMM"))}", 
                     style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Total Due", style = MaterialTheme.typography.labelSmall)
                    Text(viewModel.formatAmount(currentBalance), style = MaterialTheme.typography.titleMedium, color = if (currentBalance < 0) Color.Red else Color.Unspecified)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Next Due", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isPaid) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle, 
                            contentDescription = "Mark as Paid",
                            tint = if (isPaid) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp).clickable {
                                if (isPaid) {
                                    viewModel.toggleCcPaid(account.id, false)
                                } else {
                                    viewModel.dismissCcAlert(account.id)
                                }
                            }
                        )
                    }
                    Text(nextDueDate.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }

            if (lastPaidAmount > 0) {
                Spacer(Modifier.height(8.dp))
                Text("Last Paid: ${viewModel.formatAmount(lastPaidAmount)} (${lastPaidDate ?: ""})", 
                     style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
            }
            
            Spacer(Modifier.height(12.dp))
            Divider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f))
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.clickable { onShowBilled(billedTransactions, "Billed Transactions") }) {
                    Text("Billed", style = MaterialTheme.typography.labelSmall)
                    Text(viewModel.formatAmount(billedAmount), style = MaterialTheme.typography.bodyLarge, color = if (billedAmount < 0) Color.Red else Color.Unspecified)
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.clickable { onShowUnbilled(unbilledTransactions, "Unbilled Transactions") }) {
                    Text("Unbilled", style = MaterialTheme.typography.labelSmall)
                    Text(viewModel.formatAmount(unbilledAmount), style = MaterialTheme.typography.bodyLarge, color = if (unbilledAmount < 0) Color.Red else Color.Unspecified)
                }
            }

            Spacer(Modifier.height(12.dp))
            
            Text("Credit Limit", style = MaterialTheme.typography.labelSmall)
            LinearProgressIndicator(
                progress = if (totalLimit > 0) (availableLimit / totalLimit).toFloat().coerceIn(0f, 1f) else 0f,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                color = if (availableLimit < totalLimit * 0.2) Color.Red else MaterialTheme.colorScheme.primary
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Available: ${viewModel.formatAmount(availableLimit)}", style = MaterialTheme.typography.bodySmall)
                Text("Total: ${viewModel.formatAmount(totalLimit)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun CCExpenseAnalysis(
    account: Account,
    viewModel: ExpenseViewModel,
    allTransactions: List<TransactionWithDetails>,
    onCategoryClick: (List<TransactionWithDetails>, String) -> Unit
) {
    var analysisMode by remember(account.id) { mutableStateOf("Last Billed") }
    var menuExpanded by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val endDay = account.billingCycleEnd?.toIntOrNull() ?: 31
    val startDay = account.billingCycleStart?.toIntOrNull() ?: 1

    var cycleEnd = try {
        LocalDate.of(today.year, today.monthValue, endDay.coerceAtMost(today.lengthOfMonth()))
    } catch (e: Exception) { today.with(TemporalAdjusters.lastDayOfMonth()) }
    if (cycleEnd.isAfter(today)) cycleEnd = cycleEnd.minusMonths(1)
    
    var cycleStart = try {
        var s = cycleEnd.withDayOfMonth(startDay.coerceAtMost(cycleEnd.lengthOfMonth()))
        if (s.isAfter(cycleEnd) || s.isEqual(cycleEnd)) s = s.minusMonths(1)
        s
    } catch (e: Exception) { cycleEnd.minusMonths(1) }

    val filteredTransactions = remember(analysisMode, account.id, allTransactions, cycleStart, cycleEnd) {
        allTransactions.filter { t ->
            val isAccMatch = (t.transaction.accountId == account.id || t.transaction.toAccountId == account.id)
            val isExpense = t.categoryType == "expense"
            if (!isAccMatch || !isExpense) return@filter false

            val date = t.transaction.date
            val startStr = cycleStart.format(DateTimeFormatter.ISO_DATE)
            val endStr = cycleEnd.format(DateTimeFormatter.ISO_DATE)
            val todayStr = today.format(DateTimeFormatter.ISO_DATE)

            when (analysisMode) {
                "Last Billed" -> date >= startStr && date <= endStr
                "Unbilled" -> date > endStr && date <= todayStr
                "Both" -> date >= startStr && date <= todayStr
                else -> false
            }
        }
    }

    val categorySummary = filteredTransactions
        .groupBy { it.categoryName ?: "Uncategorized" }
        .mapValues { it.value.sumOf { t -> t.transaction.amount } }
        .toList()
        .sortedByDescending { it.second }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Expense Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Box {
                TextButton(onClick = { menuExpanded = true }) {
                    Text(analysisMode)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    listOf("Last Billed", "Unbilled", "Both").forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode) },
                            onClick = {
                                analysisMode = mode
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))

        if (categorySummary.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PieChart(data = categorySummary, colors = emptyList()) 
            }
            
            Spacer(Modifier.height(16.dp))
            
            val chartColors = listOf(
                Color(0xFFf44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
                Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
                Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
                Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722)
            )

            categorySummary.forEachIndexed { i, pair ->
                val (cat, amount) = pair
                val total = categorySummary.sumOf { it.second }
                val percent = if (total > 0) (amount / total * 100).toInt() else 0
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { 
                        val filtered = filteredTransactions.filter { it.categoryName == cat }
                        onCategoryClick(filtered, "$cat Expenses ($analysisMode)")
                    }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(chartColors[i % chartColors.size], shape = MaterialTheme.shapes.extraSmall))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(cat, style = MaterialTheme.typography.bodyLarge)
                            Text("$percent%", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                    Text(viewModel.formatAmount(amount), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Divider(modifier = Modifier.alpha(0.5f))
            }
        } else {
            Text("No expenses recorded for $analysisMode.", modifier = Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}
