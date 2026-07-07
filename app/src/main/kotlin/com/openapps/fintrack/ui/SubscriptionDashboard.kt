/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.data.AmortizationRow
import com.openapps.fintrack.data.Loan
import com.openapps.fintrack.data.LoanCalculator
import com.openapps.fintrack.data.TransactionWithDetails
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SubscriptionDashboard(viewModel: ExpenseViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val subscriptionStatuses by viewModel.getAllSubscriptionStatuses().collectAsState(initial = emptyList())
    val activeLoans by viewModel.activeLoans.collectAsState(initial = emptyList())
    val masterSubscriptions by viewModel.getAllSubscriptionsMaster().collectAsState(initial = emptyList())
    
    val subscriptions = remember(allTransactions, viewModel.dismissedCcAlertIds, subscriptionStatuses, masterSubscriptions) {
        val today = LocalDate.now()
        val dateKey = today.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        
        val statuses = runBlocking { viewModel.getAllSubscriptionStatuses().first() }

        val fromTxns = allTransactions.filter { it.transaction.subName != null }
            .groupBy { it.transaction.subName!! to (it.transaction.categoryId == null && it.transaction.toAccountId != null) }
            .map { (key, txns) ->
                val (name, isTransfer) = key
                val sortedTxns = txns.sortedByDescending { it.transaction.date }
                val lastTxn = sortedTxns.first()
                val freq = lastTxn.transaction.subFrequency ?: 1
                
                val lastDate = try { LocalDate.parse(lastTxn.transaction.date) } catch (e: Exception) { today }
                var nextDue = lastDate.plusMonths(freq.toLong())
                
                val isPaid = viewModel.dismissedCcAlertIds.contains("SUB_${name}_$dateKey")
                if (isPaid) {
                    nextDue = nextDue.plusMonths(freq.toLong())
                }
                
                val totalPaid = txns.sumOf { it.transaction.amount }
                val status = statuses.find { it.subName == name }
                val isStopped = status?.isStopped ?: false
                
                SubscriptionInfo(
                    name = name,
                    amount = lastTxn.transaction.amount,
                    frequency = freq,
                    nextDueDate = nextDue,
                    totalPaid = totalPaid,
                    transactions = sortedTxns,
                    isPaidCurrentMonth = isPaid,
                    isStopped = isStopped,
                    isTransfer = isTransfer
                )
            }

        val fromMaster = masterSubscriptions.filter { master -> fromTxns.none { it.name == master.name } }
            .map { master ->
                SubscriptionInfo(
                    name = master.name,
                    amount = 0.0,
                    frequency = master.frequency,
                    nextDueDate = today,
                    totalPaid = 0.0,
                    transactions = emptyList(),
                    isPaidCurrentMonth = false,
                    isStopped = statuses.find { it.subName == master.name }?.isStopped ?: false,
                    isTransfer = master.isTransfer
                )
            }
        
        (fromTxns + fromMaster).sortedWith(compareBy<SubscriptionInfo> { it.isStopped }.thenBy { it.nextDueDate })
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Subscriptions", "Recurring Transfers", "Loans")

    var selectedSubscriptionTxns by remember { mutableStateOf<List<TransactionWithDetails>?>(null) }
    var selectedLoanRepayments by remember { mutableStateOf<Long?>(null) }
    var detailTitle by remember { mutableStateOf("") }
    val context = LocalContext.current

    var showLoanTypeDialog by remember { mutableStateOf(false) }
    var loanCreateMode by remember { mutableStateOf<Boolean?>(null) } // true: New, false: Existing

    var showAddSubscriptionDialog by remember { mutableStateOf(false) }
    var newSubName by remember { mutableStateOf("") }
    var newSubFreq by remember { mutableStateOf("1") }
    var newSubNotes by remember { mutableStateOf("") }

    var selectedLoanForSchedule by remember { mutableStateOf<Loan?>(null) }
    var showLoanContextMenu by remember { mutableStateOf<Loan?>(null) }
    var showCatchupDialog by remember { mutableStateOf<Loan?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { uriVal ->
                selectedSubscriptionTxns?.let { data ->
                    exportToUri(context, data, "CSV", uriVal)
                }
            }
        }
    )

    if (selectedLoanForSchedule != null) {
        val loan = selectedLoanForSchedule!!
        val schedule = remember(loan) {
            val multiplier = when(loan.frequency) {
                "MONTHLY" -> 12.0
                "QUARTERLY" -> 4.0
                "HALF_YEARLY" -> 2.0
                "YEARLY" -> 1.0
                else -> 12.0
            }
            val periodicRate = loan.interestRateAnnual / multiplier
            
            LoanCalculator.generateSchedule(
                principal = loan.principalAmount,
                periodicRatePercent = periodicRate,
                totalPeriods = loan.periodsTotal,
                installment = loan.installmentAmount,
                gapInterest = loan.gapInterest,
                firstRepaymentDate = Instant.ofEpochMilli(loan.firstRepaymentDate).atZone(ZoneId.systemDefault()).toLocalDate(),
                frequency = loan.frequency
            )
        }
        val upcomingPeriod = loan.periodsPassed + 1
        
        BackHandler { selectedLoanForSchedule = null }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Schedule: ${loan.name}") },
                    navigationIcon = { IconButton(onClick = { selectedLoanForSchedule = null }) { Icon(Icons.Default.ArrowBack, null) } }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                // Header Row
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
                    Text("#", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    Text("Date", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    Text("Installment", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    Text("Interest", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    Text("Balance", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(schedule) { row ->
                        val isUpcoming = row.period == upcomingPeriod
                        val isPaid = row.period <= loan.periodsPassed
                        
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (isUpcoming) MaterialTheme.colorScheme.primaryContainer else if (isPaid) Color.Transparent.copy(alpha = 0.05f) else Color.Transparent)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${row.period}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall, color = if (isPaid) Color.Gray else Color.Unspecified)
                            Text(row.dueDate.format(DateTimeFormatter.ofPattern("dd/MM/yy")), modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, color = if (isPaid) Color.Gray else Color.Unspecified)
                            Text(viewModel.formatAmountWhole(row.installment), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, color = if (isPaid) Color.Gray else Color.Unspecified)
                            Text(viewModel.formatAmountWhole(row.interestPortion), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, color = if (isPaid) Color.Gray else Color.Unspecified)
                            Text(viewModel.formatAmountWhole(row.closingBalance), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (isPaid) Color.Gray else Color.Unspecified)
                        }
                        Divider(modifier = Modifier.alpha(0.3f))
                    }
                }
                
                // Footer Totals
                val totalInstallment = schedule.sumOf { it.installment }
                val totalInterest = schedule.sumOf { it.interestPortion }
                
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total", modifier = Modifier.width(30.dp + 70.dp), fontWeight = FontWeight.ExtraBold) 
                        Spacer(Modifier.weight(0.1f))
                        Column(Modifier.weight(1.5f)) {
                            Text("Installments", style = MaterialTheme.typography.labelSmall)
                            Text(viewModel.formatAmountWhole(totalInstallment), fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1.5f)) {
                            Text("Interest", style = MaterialTheme.typography.labelSmall)
                            Text(viewModel.formatAmountWhole(totalInterest), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1.5f))
                    }
                }
            }
        }
        return
    }

    if (showLoanContextMenu != null) {
        val loan = showLoanContextMenu!!
        AlertDialog(
            onDismissRequest = { showLoanContextMenu = null },
            title = { Text(loan.name) },
            text = { Text("Select an option for this loan:") },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { selectedLoanForSchedule = loan; showLoanContextMenu = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("View Schedule")
                    }
                    Button(onClick = { showCatchupDialog = loan; showLoanContextMenu = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Catchup Entries")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoanContextMenu = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCatchupDialog != null) {
        val loan = showCatchupDialog!!
        val pendingDates = remember(loan) { viewModel.getPendingLoanDates(loan) }
        
        AlertDialog(
            onDismissRequest = { showCatchupDialog = null },
            title = { Text("Catchup Loan Entries") },
            text = {
                Column {
                    if (pendingDates.isEmpty()) {
                        Text("No pending entries found for this loan as of today.")
                    } else {
                        Text("The following ${pendingDates.size} payment periods are pending to be recorded:")
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.height(150.dp).fillMaxWidth()) {
                            LazyColumn {
                                items(pendingDates) { date ->
                                    Text("• ${date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Do you want to record these entries now?", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                if (pendingDates.isNotEmpty()) {
                    Button(onClick = {
                        viewModel.catchupLoanEntries(loan.id)
                        showCatchupDialog = null
                        Toast.makeText(context, "Recorded ${pendingDates.size} entries", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Record")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCatchupDialog = null }) {
                    Text(if (pendingDates.isEmpty()) "OK" else "Cancel")
                }
            }
        )
    }

    if (loanCreateMode != null) {
        AddLoanScreen(viewModel = viewModel, onBack = { loanCreateMode = null }, onNavigate = onNavigate, isNewMode = loanCreateMode!!)
        return
    }

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

    if (selectedLoanRepayments != null) {
        val loanId = selectedLoanRepayments!!
        val repayments by viewModel.getRepaymentsForLoan(loanId).collectAsState(initial = emptyList())
        BackHandler { selectedLoanRepayments = null }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Loan Payments - $detailTitle") },
                    navigationIcon = { IconButton(onClick = { selectedLoanRepayments = null }) { Icon(Icons.Default.ArrowBack, null) } }
                )
            }
        ) { padding ->
            if (repayments.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No payments recorded yet.")
                }
            } else {
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                    items(repayments) { item ->
                        ListItem(
                            headlineContent = { Text("Payment of ${viewModel.formatAmount(item.amountPaid)}") },
                            supportingContent = { 
                                Text("Principal: ${viewModel.formatAmount(item.principalPortion)} | Interest: ${viewModel.formatAmount(item.interestPortion)}\nDate: ${Instant.ofEpochMilli(item.paymentDate).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}")
                            },
                            trailingContent = { if (item.isScheduled) Icon(Icons.Default.PauseCircle, "Scheduled") }
                        )
                        Divider()
                    }
                }
            }
        }
        return
    }

    if (selectedSubscriptionTxns != null) {
        val currentTxns = selectedSubscriptionTxns!!
        BackHandler { selectedSubscriptionTxns = null }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(detailTitle) },
                    navigationIcon = { IconButton(onClick = { selectedSubscriptionTxns = null }) { Icon(Icons.Default.ArrowBack, null) } },
                    actions = {
                        IconButton(onClick = { 
                            exportLauncher.launch("subscription_${detailTitle.replace(" ", "_")}.csv")
                        }) {
                            Icon(Icons.Default.FileDownload, "Export")
                        }
                    }
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(currentTxns) { item ->
                    Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = item }.padding(16.dp)) {
                        TransactionRow(detail = item, viewModel = viewModel, showTxnNumber = true)
                    }
                    Divider(modifier = Modifier.alpha(0.5f))
                }
            }
        }
        return
    }

    if (showLoanTypeDialog) {
        AlertDialog(
            onDismissRequest = { showLoanTypeDialog = false },
            title = { Text("Add Loan") },
            text = { Text("Is this a New Loan or are you tracking an Existing Loan?") },
            confirmButton = {
                Button(onClick = { loanCreateMode = true; showLoanTypeDialog = false }) { Text("New Loan") }
            },
            dismissButton = {
                OutlinedButton(onClick = { loanCreateMode = false; showLoanTypeDialog = false }) { Text("Existing Loan") }
            }
        )
    }

    if (showAddSubscriptionDialog) {
        AlertDialog(
            onDismissRequest = { showAddSubscriptionDialog = false },
            title = { Text("Add ${if (selectedTabIndex == 0) "Subscription" else "Recurring Transfer"}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newSubName,
                        onValueChange = { newSubName = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSubFreq,
                        onValueChange = { newSubFreq = it },
                        label = { Text("Frequency (Months)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSubNotes,
                        onValueChange = { newSubNotes = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newSubName.isNotBlank()) {
                        viewModel.saveSubscriptionMaster(
                            name = newSubName,
                            frequency = newSubFreq.toIntOrNull() ?: 1,
                            note = newSubNotes,
                            isTransfer = selectedTabIndex == 1
                        )
                        showAddSubscriptionDialog = false
                        newSubName = ""
                        newSubFreq = "1"
                        newSubNotes = ""
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubscriptionDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring Payments") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        floatingActionButton = {
            if (selectedTabIndex in 0..1) {
                FloatingActionButton(onClick = { showAddSubscriptionDialog = true }) {
                    Icon(Icons.Default.Add, "Add Subscription")
                }
            } else if (selectedTabIndex == 2) {
                FloatingActionButton(onClick = { showLoanTypeDialog = true }) {
                    Icon(Icons.Default.Add, "Add Loan")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0, 1 -> {
                    val filteredSubs = remember(subscriptions, selectedTabIndex) {
                        val showTransfers = selectedTabIndex == 1
                        subscriptions.filter { it.isTransfer == showTransfers }
                    }

                    if (filteredSubs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No ${tabTitles[selectedTabIndex].lowercase()} found.")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            items(filteredSubs) { sub ->
                                SubscriptionCard(
                                    sub = sub,
                                    viewModel = viewModel,
                                    onClick = { selectedSubscriptionTxns = sub.transactions; detailTitle = sub.name }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                2 -> {
                    if (activeLoans.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No active loans found.")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                            items(activeLoans) { loan ->
                                LoanCard(
                                    loan = loan,
                                    viewModel = viewModel,
                                    onClick = { selectedLoanRepayments = loan.id; detailTitle = loan.name },
                                    onLongClick = { showLoanContextMenu = loan }
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoanCard(loan: Loan, viewModel: ExpenseViewModel, onClick: () -> Unit, onLongClick: () -> Unit) {
    val textColor = if (viewModel.currentTheme == "Light") Color.Black else Color.White

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        border = BorderStroke(2.dp, if (loan.loanType == "LENDING") Color.Green else Color.Red),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = textColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    loan.name, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    if (loan.loanType == "LENDING") "Lending" else "Borrowing",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (loan.loanType == "LENDING") Color.Green else Color.Red
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text("Outstanding Balance", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
            Text(
                viewModel.formatAmount(loan.outstandingBalance), 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.ExtraBold, 
                color = textColor
            )
            
            Spacer(Modifier.height(12.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Next Due Date", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(
                        Instant.ofEpochMilli(loan.nextDueDate).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy")), 
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Installment", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(
                        viewModel.formatAmount(loan.installmentAmount), 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Total Interest Paid", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(viewModel.formatAmount(loan.totalInterestPaid), style = MaterialTheme.typography.bodySmall, color = textColor)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto Record", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Switch(
                        checked = loan.isAutoRecordEnabled,
                        onCheckedChange = { viewModel.toggleLoanAutoRecord(loan, it) },
                        modifier = Modifier.scale(0.7f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Progress", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text("${loan.periodsPassed} / ${loan.periodsTotal} Paid", style = MaterialTheme.typography.bodySmall, color = textColor)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text("Click to view payment history", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun SubscriptionCard(sub: SubscriptionInfo, viewModel: ExpenseViewModel, onClick: () -> Unit) {
    val textColor = if (viewModel.currentTheme == "Light") Color.Black else Color.White

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (sub.isStopped) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                            else MaterialTheme.colorScheme.primaryContainer,
            contentColor = textColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sub.name + (if (sub.isStopped) " (Stopped)" else ""), 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.Bold,
                    color = if (sub.isStopped) textColor.copy(alpha = 0.6f) else textColor
                )
                IconButton(onClick = { viewModel.toggleSubscriptionStopped(sub.name, !sub.isStopped) }) {
                    Icon(
                        imageVector = if (sub.isStopped) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                        contentDescription = if (sub.isStopped) "Resume" else "Stop",
                        tint = if (sub.isStopped) Color(0xFF4CAF50) else Color.Red
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Last Amount", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(viewModel.formatAmount(sub.amount), style = MaterialTheme.typography.titleMedium, color = textColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Frequency", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text("${sub.frequency} Months", style = MaterialTheme.typography.titleMedium, color = textColor)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Next Due Date", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                        if (!sub.isStopped) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = if (sub.isPaidCurrentMonth) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle, 
                                contentDescription = "Mark as Paid",
                                tint = if (sub.isPaidCurrentMonth) Color(0xFF4CAF50) else textColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp).clickable {
                                    if (sub.isPaidCurrentMonth) {
                                        viewModel.toggleCcPaidCustom("SUB_${sub.name}", false)
                                    } else {
                                        viewModel.toggleCcPaidCustom("SUB_${sub.name}", true)
                                    }
                                }
                            )
                        }
                    }
                    Text(
                        if (sub.isStopped) "N/A" else sub.nextDueDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), 
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Paid", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(viewModel.formatAmount(sub.totalPaid), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = textColor)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text("Click to view payment history", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

data class SubscriptionInfo(
    val name: String,
    val amount: Double,
    val frequency: Int,
    val nextDueDate: LocalDate,
    val totalPaid: Double,
    val transactions: List<TransactionWithDetails>,
    val isPaidCurrentMonth: Boolean = false,
    val isStopped: Boolean = false,
    val isTransfer: Boolean = false
)
