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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.R
import com.openapps.fintrack.data.AmortizationRow
import com.openapps.fintrack.data.Loan
import com.openapps.fintrack.data.LoanCalculator
import com.openapps.fintrack.data.TransactionWithDetails
import com.openapps.fintrack.domain.model.SubscriptionSuggestion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SubscriptionDashboard(viewModel: ExpenseViewModel, onBack: () -> Unit, onNavigate: (String) -> Unit, isEmbedded: Boolean = false) {
    val localContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.openapps.fintrack.data.CcAlertWorker>().build()
        androidx.work.WorkManager.getInstance(localContext).enqueue(workRequest)
        viewModel.triggerRefresh()
    }

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
                val isAutoRecord = status?.isAutoRecordEnabled ?: true
                
                SubscriptionInfo(
                    name = name,
                    amount = lastTxn.transaction.amount,
                    frequency = freq,
                    nextDueDate = nextDue,
                    totalPaid = totalPaid,
                    transactions = sortedTxns,
                    isPaidCurrentMonth = isPaid,
                    isStopped = isStopped,
                    isTransfer = isTransfer,
                    isAutoRecordEnabled = isAutoRecord
                )
            }

        val fromMaster = masterSubscriptions.filter { master -> fromTxns.none { it.name == master.name } }
            .map { master ->
                val status = statuses.find { it.subName == master.name }
                SubscriptionInfo(
                    name = master.name,
                    amount = 0.0,
                    frequency = master.frequency,
                    nextDueDate = today,
                    totalPaid = 0.0,
                    transactions = emptyList(),
                    isPaidCurrentMonth = false,
                    isStopped = status?.isStopped ?: false,
                    isTransfer = master.isTransfer,
                    isAutoRecordEnabled = status?.isAutoRecordEnabled ?: false
                )
            }
        
        (fromTxns + fromMaster).sortedWith(compareBy<SubscriptionInfo> { it.isStopped }.thenBy { it.nextDueDate })
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(stringResource(R.string.menu_subscriptions), stringResource(R.string.label_recurring_transfers), stringResource(R.string.label_loans))

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
    var calcSchedule by remember { mutableStateOf<List<AmortizationRow>?>(null) }
    var showLoanContextMenu by remember { mutableStateOf<Loan?>(null) }
    var showCatchupDialog by remember { mutableStateOf<Loan?>(null) }
    var showLoanDeleteConfirm by remember { mutableStateOf<Loan?>(null) }

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
        
        BackHandler { selectedLoanForSchedule = null }
        AmortizationScheduleOverlay(
            title = "Schedule: ${loan.name}",
            schedule = schedule,
            viewModel = viewModel,
            onBack = { selectedLoanForSchedule = null }
        )
        return
    }

    if (calcSchedule != null) {
        BackHandler { calcSchedule = null }
        AmortizationScheduleOverlay(
            title = stringResource(R.string.title_loan_calculator),
            schedule = calcSchedule!!,
            viewModel = viewModel,
            onBack = { calcSchedule = null }
        )
        return
    }

    if (showLoanContextMenu != null) {
        val loan = showLoanContextMenu!!
        AlertDialog(
            onDismissRequest = { showLoanContextMenu = null },
            title = { Text(loan.name) },
            text = { Text(stringResource(R.string.msg_select_option_loan)) },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { selectedLoanForSchedule = loan; showLoanContextMenu = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.btn_see_schedule))
                    }
                    Button(onClick = { showCatchupDialog = loan; showLoanContextMenu = null }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.btn_catchup_entries))
                    }
                    Button(
                        onClick = { showLoanDeleteConfirm = loan; showLoanContextMenu = null },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.btn_delete) + " " + stringResource(R.string.label_loan))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoanContextMenu = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showLoanDeleteConfirm != null) {
        val loan = showLoanDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { showLoanDeleteConfirm = null },
            title = { Text(stringResource(R.string.title_delete_notebook)) },
            text = { Text(stringResource(R.string.msg_delete_loan_desc)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLoan(loan)
                        showLoanDeleteConfirm = null
                        Toast.makeText(context, context.getString(R.string.msg_loan_deleted), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoanDeleteConfirm = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showCatchupDialog != null) {
        val loan = showCatchupDialog!!
        val pendingDates = remember(loan) { viewModel.getPendingLoanDates(loan) }
        
        AlertDialog(
            onDismissRequest = { showCatchupDialog = null },
            title = { Text(stringResource(R.string.title_catchup_loan_entries)) },
            text = {
                Column {
                    if (pendingDates.isEmpty()) {
                        Text(stringResource(R.string.msg_no_pending_loan_entries))
                    } else {
                        Text(stringResource(R.string.msg_pending_loan_periods, pendingDates.size))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.height(150.dp).fillMaxWidth()) {
                            LazyColumn {
                                items(pendingDates) { date ->
                                    Text("• ${date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.msg_record_entries_now), style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                if (pendingDates.isNotEmpty()) {
                    Button(onClick = {
                        viewModel.catchupLoanEntries(loan.id)
                        showCatchupDialog = null
                        Toast.makeText(context, context.getString(R.string.msg_recorded_n_entries, pendingDates.size), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.btn_record))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCatchupDialog = null }) {
                    Text(if (pendingDates.isEmpty()) stringResource(R.string.btn_ok) else stringResource(R.string.btn_cancel))
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
                    title = { Text(stringResource(R.string.title_loan_payments, detailTitle)) },
                    navigationIcon = { IconButton(onClick = { selectedLoanRepayments = null }) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back)) } }
                )
            }
        ) { padding ->
            if (repayments.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.msg_no_payments_recorded))
                }
            } else {
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                    items(repayments) { item ->
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.label_payment_of, viewModel.formatAmount(item.amountPaid))) },
                            supportingContent = { 
                                Text(stringResource(R.string.label_principal) + ": ${viewModel.formatAmount(item.principalPortion)} | " + stringResource(R.string.label_interest_colon_val, viewModel.formatAmount(item.interestPortion)) + "\n" + stringResource(R.string.label_date_colon) + "${Instant.ofEpochMilli(item.paymentDate).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}")
                            },
                            trailingContent = { if (item.isScheduled) Icon(Icons.Default.PauseCircle, stringResource(R.string.label_scheduled)) }
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
                        TransactionRow(detail = item, viewModel = viewModel, showTxnNumber = false)
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
            title = { Text(stringResource(R.string.label_loan)) },
            text = { Text(stringResource(R.string.msg_add_loan_ask)) },
            confirmButton = {
                Button(onClick = { loanCreateMode = true; showLoanTypeDialog = false }) { Text(stringResource(R.string.title_new_loan)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { loanCreateMode = false; showLoanTypeDialog = false }) { Text(stringResource(R.string.title_add_existing_loan)) }
            }
        )
    }

    if (showAddSubscriptionDialog) {
        val subLabel = stringResource(R.string.label_subscription)
        val recTransLabel = stringResource(R.string.label_recurring_transfers)
        AlertDialog(
            onDismissRequest = { showAddSubscriptionDialog = false },
            title = { Text(stringResource(R.string.btn_add) + " ${if (selectedTabIndex == 0) subLabel else recTransLabel}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newSubName,
                        onValueChange = { newSubName = it },
                        label = { Text(stringResource(R.string.label_name)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSubFreq,
                        onValueChange = { newSubFreq = it },
                        label = { Text(stringResource(R.string.label_freq_months)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSubNotes,
                        onValueChange = { newSubNotes = it },
                        label = { Text(stringResource(R.string.label_notes)) },
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
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddSubscriptionDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            if (!isEmbedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.title_recurring_payments)) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back)) } }
                )
            }
        },
        floatingActionButton = {
            if (!isEmbedded) {
                if (selectedTabIndex in 0..1) {
                    FloatingActionButton(onClick = { showAddSubscriptionDialog = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.btn_add_subscription))
                    }
                } else if (selectedTabIndex == 2) {
                    FloatingActionButton(onClick = { showLoanTypeDialog = true }) {
                        Icon(Icons.Default.Add, stringResource(R.string.title_add_loan))
                    }
                }
            }
        }
    ) { padding ->
        val contentPadding = if (isEmbedded) PaddingValues(0.dp) else padding
        val bottomPadding = if (isEmbedded) 88.dp else 16.dp
        Column(modifier = Modifier.padding(contentPadding)) {
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
                            Text(stringResource(R.string.msg_no_active_loans))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = bottomPadding)
                        ) {
                            if (selectedTabIndex == 0) {
                                item { SubscriptionSuggestionsSection(viewModel) }
                            }
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = bottomPadding)
                    ) {
                        item {
                            LoanCalculatorCard(
                                viewModel = viewModel,
                                onSeeSchedule = { sched -> calcSchedule = sched }
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        if (activeLoans.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.msg_no_active_loans))
                                }
                            }
                        } else {
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
                    if (loan.loanType == "LENDING") stringResource(R.string.label_lending) else stringResource(R.string.label_borrowing),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (loan.loanType == "LENDING") Color.Green else Color.Red
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(stringResource(R.string.label_outstanding_balance), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
            Text(
                viewModel.formatAmount(loan.outstandingBalance), 
                style = MaterialTheme.typography.headlineMedium, 
                fontWeight = FontWeight.ExtraBold, 
                color = textColor
            )
            
            Spacer(Modifier.height(12.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.label_next_due_date), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(
                        Instant.ofEpochMilli(loan.nextDueDate).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy")), 
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.label_installment), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
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
                    Text(stringResource(R.string.label_total_interest_paid), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(viewModel.formatAmount(loan.totalInterestPaid), style = MaterialTheme.typography.bodySmall, color = textColor)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.label_auto_record), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Switch(
                        checked = loan.isAutoRecordEnabled,
                        onCheckedChange = { viewModel.toggleLoanAutoRecord(loan, it) },
                        modifier = Modifier.scale(0.7f)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.label_progress), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(stringResource(R.string.label_n_of_n_paid, loan.periodsPassed, loan.periodsTotal), style = MaterialTheme.typography.bodySmall, color = textColor)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.msg_click_view_history), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
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
                    sub.name + (if (sub.isStopped) stringResource(R.string.label_stopped_suffix) else ""), 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.Bold,
                    color = if (sub.isStopped) textColor.copy(alpha = 0.6f) else textColor
                )
                IconButton(onClick = { viewModel.toggleSubscriptionStopped(sub.name, !sub.isStopped) }) {
                    Icon(
                        imageVector = if (sub.isStopped) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                        contentDescription = if (sub.isStopped) stringResource(R.string.btn_resume) else stringResource(R.string.btn_stop),
                        tint = if (sub.isStopped) Color(0xFF4CAF50) else Color.Red
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(stringResource(R.string.label_last_amount), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(viewModel.formatAmount(sub.amount), style = MaterialTheme.typography.titleMedium, color = textColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.label_frequency), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(stringResource(R.string.label_n_months, sub.frequency), style = MaterialTheme.typography.titleMedium, color = textColor)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.label_next_due_date), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                        if (!sub.isStopped) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = if (sub.isPaidCurrentMonth) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle, 
                                contentDescription = stringResource(R.string.label_mark_as_paid),
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
                        if (sub.isStopped) stringResource(R.string.label_not_applicable) else sub.nextDueDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), 
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.label_auto_record), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                        Switch(
                            checked = sub.isAutoRecordEnabled,
                            onCheckedChange = { viewModel.toggleSubscriptionAutoRecord(sub.name, it) },
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.label_total_paid), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f))
                    Text(viewModel.formatAmount(sub.totalPaid), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = textColor)
                }
            }
            
            Spacer(Modifier.height(8.dp))

            val pendingMissingDates = remember(sub) { viewModel.getMissingSubscriptionDates(sub.name) }
            var showCatchupDialog by remember { mutableStateOf(false) }

            if (pendingMissingDates.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showCatchupDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Update, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_catchup_missing_entries, pendingMissingDates.size))
                }
            }

            if (showCatchupDialog) {
                AlertDialog(
                    onDismissRequest = { showCatchupDialog = false },
                    title = { Text(stringResource(R.string.title_catchup_missing_entries)) },
                    text = { Text(stringResource(R.string.msg_catchup_missing_entries, pendingMissingDates.size, sub.name)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.catchupSubscriptionEntries(sub.name)
                                showCatchupDialog = false
                            }
                        ) { Text(stringResource(R.string.btn_catch_up)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCatchupDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                    }
                )
            }

            Text(stringResource(R.string.msg_click_view_history), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterHorizontally))
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
    val isTransfer: Boolean = false,
    val isAutoRecordEnabled: Boolean = false
)

@Composable
fun SubscriptionSuggestionsSection(viewModel: ExpenseViewModel) {
    val suggestions by viewModel.subscriptionSuggestions.collectAsState(initial = emptyList())
    
    if (suggestions.isNotEmpty()) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text(stringResource(R.string.title_suggested_subscriptions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            suggestions.forEach { suggestion ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(suggestion.merchantName, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.msg_detected_monthly_expense, viewModel.formatAmount(suggestion.amount)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanCalculatorCard(
    viewModel: ExpenseViewModel,
    onSeeSchedule: (List<AmortizationRow>) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }

    val useMillions = viewModel.useMillionsSystem

    var amountInputText by remember { mutableStateOf("1000000") }
    val amountValue = amountInputText.toDoubleOrNull() ?: 0.0

    val amountFactor = if (useMillions) 1_000_000.0 else 100_000.0
    val maxSliderVal = if (useMillions) 20.0 else 200.0
    val currentAmountSliderVal = (amountValue / amountFactor).coerceIn(0.0, maxSliderVal)

    var tenureUnit by remember { mutableStateOf("mo") }
    var tenureInputText by remember { mutableStateOf("12") }
    val tenureValue = tenureInputText.toDoubleOrNull() ?: 0.0

    val maxTenureSliderVal = if (tenureUnit == "mo") 360.0 else 30.0
    val currentTenureSliderVal = tenureValue.coerceIn(0.0, maxTenureSliderVal)

    val totalMonths = remember(tenureValue, tenureUnit) {
        if (tenureUnit == "yr") (tenureValue * 12.0).roundToInt() else tenureValue.roundToInt()
    }

    var rateInputText by remember { mutableStateOf("10.0") }
    val rateValue = rateInputText.toDoubleOrNull() ?: 0.0
    val currentRateSliderVal = rateValue.coerceIn(5.0, 20.0)

    var issueDate by remember { mutableStateOf(LocalDate.now()) }
    var firstRepaymentDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var gapMethod by remember { mutableStateOf("DAYS") }

    val monthlyRate = rateValue / 12.0

    val gapInterest = remember(amountValue, rateValue, monthlyRate, issueDate, firstRepaymentDate, gapMethod) {
        if (gapMethod == "DAYS") {
            LoanCalculator.calculateGapInterestDays(amountValue, rateValue, issueDate, firstRepaymentDate)
        } else {
            LoanCalculator.calculateGapInterestBankConvention(amountValue, monthlyRate, issueDate, firstRepaymentDate)
        }
    }

    val periodicRate = rateValue / 12.0
    val emi = remember(amountValue, periodicRate, totalMonths, gapInterest) {
        if (amountValue > 0 && totalMonths > 0) {
            LoanCalculator.calculateStandardEMI(amountValue, periodicRate, totalMonths, gapInterest)
        } else 0.0
    }

    val schedule = remember(amountValue, periodicRate, totalMonths, emi, gapInterest, firstRepaymentDate) {
        if (amountValue > 0 && totalMonths > 0) {
            LoanCalculator.generateSchedule(
                principal = amountValue,
                periodicRatePercent = periodicRate,
                totalPeriods = totalMonths,
                installment = emi,
                gapInterest = gapInterest,
                firstRepaymentDate = firstRepaymentDate,
                frequency = "MONTHLY"
            )
        } else emptyList()
    }

    val totalInterestPayable = remember(schedule) {
        if (schedule.isNotEmpty()) schedule.sumOf { it.interestPortion } else 0.0
    }

    val totalRepayment = remember(amountValue, totalInterestPayable) {
        amountValue + totalInterestPayable
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Calculate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.title_loan_calculator),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { isExpanded = !isExpanded }) {
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.label_loan_amount),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (useMillions) "Slider: 0 - 20M (0.5M)" else "Slider: 0 - 200L (0.5L)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    OutlinedTextField(
                        value = amountInputText,
                        onValueChange = { amountInputText = it },
                        label = { Text(stringResource(R.string.label_loan_amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Slider(
                        value = currentAmountSliderVal.toFloat(),
                        onValueChange = { raw ->
                            val stepped = (raw * 2).roundToInt() / 2.0
                            val calculatedAmount = stepped * amountFactor
                            amountInputText = if (calculatedAmount % 1.0 == 0.0) calculatedAmount.toLong().toString() else String.format(Locale.US, "%.2f", calculatedAmount)
                        },
                        valueRange = 0f..maxSliderVal.toFloat(),
                        steps = ((maxSliderVal / 0.5) - 1).coerceAtLeast(0.0).toInt(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.label_tenure),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(
                                selected = tenureUnit == "yr",
                                onClick = {
                                    if (tenureUnit != "yr") {
                                        tenureUnit = "yr"
                                        val yrs = (tenureValue / 12.0)
                                        tenureInputText = if (yrs % 1.0 == 0.0) yrs.toInt().toString() else String.format(Locale.US, "%.1f", yrs)
                                    }
                                },
                                label = { Text(stringResource(R.string.label_yr)) }
                            )
                            FilterChip(
                                selected = tenureUnit == "mo",
                                onClick = {
                                    if (tenureUnit != "mo") {
                                        tenureUnit = "mo"
                                        val mos = (tenureValue * 12.0)
                                        tenureInputText = if (mos % 1.0 == 0.0) mos.toInt().toString() else String.format(Locale.US, "%.1f", mos)
                                    }
                                },
                                label = { Text(stringResource(R.string.label_mo)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = tenureInputText,
                        onValueChange = { tenureInputText = it },
                        label = { Text("${stringResource(R.string.label_tenure)} (${if (tenureUnit == "yr") stringResource(R.string.label_yr) else stringResource(R.string.label_mo)})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Slider(
                        value = currentTenureSliderVal.toFloat(),
                        onValueChange = { raw ->
                            val stepped = (raw * 2).roundToInt() / 2.0
                            tenureInputText = if (stepped % 1.0 == 0.0) stepped.toInt().toString() else stepped.toString()
                        },
                        valueRange = 0f..maxTenureSliderVal.toFloat(),
                        steps = ((maxTenureSliderVal / 0.5) - 1).coerceAtLeast(0.0).toInt(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.label_interest_rate_pct),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Slider: 5% - 20% (0.5%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                    OutlinedTextField(
                        value = rateInputText,
                        onValueChange = { rateInputText = it },
                        label = { Text(stringResource(R.string.label_interest_rate_pct)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Slider(
                        value = currentRateSliderVal.toFloat(),
                        onValueChange = { raw ->
                            val stepped = (raw * 2).roundToInt() / 2.0
                            rateInputText = String.format(Locale.US, "%.1f", stepped)
                        },
                        valueRange = 5f..20f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val d = issueDate
                            DatePickerDialog(context, { _, y, m, day ->
                                issueDate = LocalDate.of(y, m + 1, day)
                            }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.label_issue_date), style = MaterialTheme.typography.labelSmall)
                            Text(issueDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val d = firstRepaymentDate
                            DatePickerDialog(context, { _, y, m, day ->
                                firstRepaymentDate = LocalDate.of(y, m + 1, day)
                            }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.label_1st_repayment), style = MaterialTheme.typography.labelSmall)
                            Text(firstRepaymentDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.label_gap_interest_method), style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = gapMethod == "DAYS",
                            onClick = { gapMethod = "DAYS" },
                            label = { Text(stringResource(R.string.label_actual_365_days)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = gapMethod == "MONTH_ODD",
                            onClick = { gapMethod = "MONTH_ODD" },
                            label = { Text(stringResource(R.string.label_months_odd_days)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (gapInterest > 0.01) {
                    Spacer(Modifier.height(4.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = stringResource(R.string.label_est_gap_interest, viewModel.formatAmount(gapInterest)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.label_monthly_emi), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(viewModel.formatAmount(emi), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.label_total_interest_payable), style = MaterialTheme.typography.bodySmall)
                            Text(viewModel.formatAmount(totalInterestPayable), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider(modifier = Modifier.alpha(0.3f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.label_total_repayment), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(viewModel.formatAmount(totalRepayment), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (schedule.isNotEmpty()) {
                            onSeeSchedule(schedule)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = schedule.isNotEmpty()
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_see_loan_schedule))
                }
            }
        }
    }
}
