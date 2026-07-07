/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.data.Loan
import com.openapps.fintrack.data.LoanCalculator
import com.openapps.fintrack.data.Party
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLoanScreen(viewModel: ExpenseViewModel, onBack: () -> Unit, onNavigate: ((String) -> Unit)? = null, isNewMode: Boolean = true) {
    // Input Fields
    var loanType by remember { mutableStateOf("BORROWING") } 
    var name by remember { mutableStateOf("") }
    var principalInput by remember { mutableStateOf("") } 
    var totalTenure by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("MONTHLY") }
    var loanIssueDate by remember { mutableStateOf(LocalDate.now()) }
    var firstRepaymentDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var annualRateInput by remember { mutableStateOf("") }
    var installment by remember { mutableStateOf("") } 
    var gapMethod by remember { mutableStateOf("DAYS") } 
    var isActualEmiDifferent by remember { mutableStateOf(false) }
    var actualRepaymentAmount by remember { mutableStateOf("") }
    var isCreateAccountChecked by remember { mutableStateOf(true) }
    var isUpdateBankBalanceChecked by remember { mutableStateOf(false) }
    var selectedTagIds = remember { mutableStateListOf<Int>() }
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Mapping State
    var selectedMinorHeadId by remember { mutableStateOf<Int?>(null) }
    var selectedDisbursementAccountId by remember { mutableStateOf<Int?>(null) }

    // Logic State
    var isInstallmentManuallyEdited by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val allMajorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val allMinorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    
    val loansMajorHead = allMajorHeads.find { it.name.equals("Loans", ignoreCase = true) }
    val loanMinorHeads = allMinorHeads.filter { it.majorHeadId == loansMajorHead?.id }

    val multiplier = remember(frequency) {
        when(frequency) {
            "MONTHLY" -> 12.0
            "QUARTERLY" -> 4.0
            "HALF_YEARLY" -> 2.0
            "YEARLY" -> 1.0
            else -> 12.0
        }
    }

    val annualRate = remember(annualRateInput) {
        annualRateInput.toDoubleOrNull() ?: 0.0
    }

    val periodicRate = remember(annualRate, multiplier) {
        if (multiplier > 0) annualRate / multiplier else 0.0
    }

    val monthlyRate = remember(annualRate) {
        annualRate / 12.0
    }

    val gapInterest = remember(principalInput, annualRate, monthlyRate, loanIssueDate, firstRepaymentDate, gapMethod) {
        val p = principalInput.toDoubleOrNull() ?: 0.0
        if (gapMethod == "DAYS") {
            LoanCalculator.calculateGapInterestDays(p, annualRate, loanIssueDate, firstRepaymentDate)
        } else {
            LoanCalculator.calculateGapInterestBankConvention(p, monthlyRate, loanIssueDate, firstRepaymentDate)
        }
    }

    // EMI Auto-population (only if not manually edited)
    LaunchedEffect(principalInput, periodicRate, totalTenure, gapInterest) {
        if (!isInstallmentManuallyEdited) {
            val p = principalInput.toDoubleOrNull() ?: 0.0
            val n = totalTenure.toIntOrNull() ?: 0
            if (p > 0 && n > 0 && periodicRate > 0) {
                val computed = LoanCalculator.calculateStandardEMI(p, periodicRate, n, gapInterest)
                installment = String.format(Locale.US, "%.2f", computed)
            }
        }
    }

    // Real-time Outstanding Balance (Computed)
    val currentOutstanding = remember(principalInput, periodicRate, totalTenure, installment, firstRepaymentDate, frequency, gapInterest) {
        val p = principalInput.toDoubleOrNull() ?: 0.0
        val n = totalTenure.toIntOrNull() ?: 0
        val inst = installment.toDoubleOrNull() ?: 0.0
        
        LoanCalculator.calculateOutstandingBalance(
            principal = p,
            periodicRatePercent = periodicRate,
            totalPeriods = n,
            standardEMI = inst,
            gapInterest = gapInterest,
            firstRepaymentDate = firstRepaymentDate,
            frequency = frequency
        )
    }

    LaunchedEffect(Unit) {
        viewModel.ensureLoanHeadsExist()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (isNewMode) "New Loan" else "Add Existing Loan") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loan Type Toggle
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Type:", modifier = Modifier.weight(1f))
                FilterChip(
                    selected = loanType == "BORROWING",
                    onClick = { loanType = "BORROWING" },
                    label = { Text("Borrowing") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Red.copy(alpha = 0.2f))
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = loanType == "LENDING",
                    onClick = { loanType = "LENDING" },
                    label = { Text("Lending") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.Green.copy(alpha = 0.2f))
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Loan Reference Name (Account Name)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = principalInput,
                onValueChange = { principalInput = it },
                label = { Text("Loan Amount Issued (Original Principal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = totalTenure,
                onValueChange = { totalTenure = it },
                label = { Text("Total Tenure (Number of Payments)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            var freqExpanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = frequency.replace("_", " "),
                    onValueChange = {},
                    label = { Text("Repayment Frequency") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) }
                )
                DropdownMenu(expanded = freqExpanded, onDismissRequest = { freqExpanded = false }) {
                    listOf("MONTHLY", "QUARTERLY", "HALF_YEARLY", "YEARLY").forEach { freq ->
                        DropdownMenuItem(text = { Text(freq.replace("_", " ")) }, onClick = { frequency = freq; freqExpanded = false })
                    }
                }
                Box(Modifier.matchParentSize().clickable { freqExpanded = true })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val d = loanIssueDate
                        DatePickerDialog(context, { _, y, m, day ->
                            loanIssueDate = LocalDate.of(y, m + 1, day)
                        }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Issue Date", style = MaterialTheme.typography.labelSmall)
                        Text(loanIssueDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")))
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
                        Text("1st Repayment", style = MaterialTheme.typography.labelSmall)
                        Text(firstRepaymentDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")))
                    }
                }
            }

            // Gap Interest Methodology
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Gap Interest Methodology:", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = gapMethod == "DAYS",
                        onClick = { gapMethod = "DAYS" },
                        label = { Text("Actual/365 Days") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = gapMethod == "MONTH_ODD",
                        onClick = { gapMethod = "MONTH_ODD" },
                        label = { Text("Months + Odd Days") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (gapInterest > 0.01) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Estimated Gap Interest: ${viewModel.formatAmount(gapInterest)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Note: This will be covered by the first EMI payment. Total cash outflow remains constant.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
            }

            OutlinedTextField(
                value = annualRateInput,
                onValueChange = { annualRateInput = it },
                label = { Text("Annual Interest Rate (%)") },
                trailingIcon = {
                    IconButton(onClick = {
                        val p = principalInput.toDoubleOrNull() ?: 0.0
                        val n = totalTenure.toIntOrNull() ?: 0
                        if (p > 0 && n > 0 && periodicRate > 0) {
                            val computed = LoanCalculator.calculateStandardEMI(p, periodicRate, n, gapInterest)
                            installment = String.format(Locale.US, "%.2f", computed)
                            isInstallmentManuallyEdited = false
                        } else {
                            Toast.makeText(context, "Enter Principal, Tenure and Rate", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Calculate, "Compute EMI")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = installment,
                onValueChange = { 
                    installment = it
                    isInstallmentManuallyEdited = true
                },
                label = { Text("Periodic EMI") },
                placeholder = { Text("Auto-computed / Editable") },
                trailingIcon = { 
                    Row {
                        IconButton(onClick = {
                            val p = principalInput.toDoubleOrNull() ?: 0.0
                            val emi = installment.toDoubleOrNull() ?: 0.0
                            val n = totalTenure.toIntOrNull() ?: 0
                            if (p > 0 && n > 0 && emi > 0) {
                                val pRate = LoanCalculator.calculatePeriodicRateFromEMI(
                                    p, emi, n, loanIssueDate, firstRepaymentDate, gapMethod, frequency
                                )
                                annualRateInput = String.format(Locale.US, "%.2f", pRate * multiplier)
                            } else {
                                Toast.makeText(context, "Enter Principal, Tenure and EMI", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Percent, "Compute Rate")
                        }
                        if (isInstallmentManuallyEdited) {
                            IconButton(onClick = { isInstallmentManuallyEdited = false }) {
                                Icon(Icons.Default.Refresh, "Recalculate")
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isActualEmiDifferent,
                    onCheckedChange = { isActualEmiDifferent = it }
                )
                Text("Actual EMI is different", style = MaterialTheme.typography.bodyMedium)
            }

            if (isActualEmiDifferent) {
                OutlinedTextField(
                    value = actualRepaymentAmount,
                    onValueChange = { actualRepaymentAmount = it },
                    label = { Text("Actual Repayment Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Show First Payment Info Card
            // Removed card to reflect constant cash outflow

            if (!isNewMode) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Current Outstanding Balance", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = viewModel.formatAmount(currentOutstanding),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Computed based on schedule up to today", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }

            // Terminal Balance Display
            val terminalBalance = remember(principalInput, periodicRate, totalTenure, installment, gapInterest, firstRepaymentDate, frequency) {
                val p = principalInput.toDoubleOrNull() ?: 0.0
                val n = totalTenure.toIntOrNull() ?: 0
                val inst = installment.toDoubleOrNull() ?: 0.0
                
                if (p > 0 && n > 0) {
                    LoanCalculator.calculateOutstandingBalance(
                        principal = p,
                        periodicRatePercent = periodicRate,
                        totalPeriods = n,
                        standardEMI = inst,
                        gapInterest = gapInterest,
                        firstRepaymentDate = firstRepaymentDate,
                        frequency = frequency,
                        asOfDate = LocalDate.now().plusYears(100) // Far future to get end of tenure
                    )
                } else 0.0
            }

            if (principalInput.isNotEmpty() && totalTenure.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Loan Balance at end of tenure", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = viewModel.formatAmountWhole(terminalBalance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (Math.abs(terminalBalance) < 1.0) Color(0xFF4CAF50) else Color.Red
                        )
                        Text("Tip: This should be approximately zero if periodic EMI is correct.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }

            // Mapping Section
            HorizontalDivider()
            Text("Account & Mapping", style = MaterialTheme.typography.titleMedium)
            
            var minorExpanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = loanMinorHeads.find { it.id == selectedMinorHeadId }?.name ?: "Select Loan Category (Minor Head)",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                )
                DropdownMenu(expanded = minorExpanded, onDismissRequest = { minorExpanded = false }) {
                    loanMinorHeads.forEach { head ->
                        DropdownMenuItem(text = { Text(head.name) }, onClick = { selectedMinorHeadId = head.id; minorExpanded = false })
                    }
                }
                Box(Modifier.matchParentSize().clickable { minorExpanded = true })
            }

            val allAccounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
            val disbursementAccounts = allAccounts.filter { acc ->
                val minor = allMinorHeads.find { it.id == acc.minorHeadId }
                minor?.majorHeadId in listOf(2, 3, 7) // Bank, Wallet, Cash
            }

            AccountSelectionDialog(
                label = if (loanType == "BORROWING") "Received In (Account)" else "Paid From (Account)",
                accounts = disbursementAccounts,
                balances = emptyList(),
                majorHeads = emptyList(),
                minorHeads = allMinorHeads,
                viewModel = viewModel,
                selectedId = selectedDisbursementAccountId,
                onSelected = { selectedDisbursementAccountId = it },
                onOnAccountSelected = {},
                isOnAccountSelected = false,
                hasOnAccountOption = false,
                onAdd = { onNavigate?.invoke("add_category") }
            )

            if (!isNewMode) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isCreateAccountChecked,
                            onCheckedChange = { 
                                isCreateAccountChecked = it
                                if (!it) isUpdateBankBalanceChecked = false
                            }
                        )
                        Text("Create loan account", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isUpdateBankBalanceChecked,
                            onCheckedChange = { isUpdateBankBalanceChecked = it },
                            enabled = isCreateAccountChecked
                        )
                        Text("Update bank balance", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "Note: If unchecked, this loan is for tracking only and no transactions will be recorded.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 32.dp)
                    )
                }
            }

            TagSelectionPopup(
                allTags = allTags,
                selectedIds = selectedTagIds,
                multiSelect = viewModel.multiTagEnabled,
                onAdd = { onNavigate?.invoke("add_tag") }
            )

            Button(
                onClick = {
                    if (name.isBlank() || selectedMinorHeadId == null) {
                        Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (isNewMode) {
                        val cutoffDate = LocalDate.now().minusMonths(1)
                        if (firstRepaymentDate.isBefore(cutoffDate)) {
                            Toast.makeText(context, "1st Repayment date is too far in the past. Please use 'Existing Loan' option.", Toast.LENGTH_LONG).show()
                            return@Button
                        }
                    }

                    showConfirmDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Loan")
            }

            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    title = { Text("Confirm Loan Creation") },
                    text = { Text("Please review the input parameters once again. No further changes or removal can be done once loan is created. Do you want to proceed?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showConfirmDialog = false
                                val pVal = principalInput.toDoubleOrNull() ?: 0.0
                                val rVal = annualRate 
                                val nTotal = totalTenure.toIntOrNull() ?: 0
                                val instFinal = installment.toDoubleOrNull() ?: 0.0
                                val actualAmt = actualRepaymentAmount.toDoubleOrNull() ?: instFinal
                                
                                val nPassed = if (isNewMode) 0 else {
                                     LoanCalculator.countPassedPeriods(firstRepaymentDate, LocalDate.now(), frequency).coerceAtMost(nTotal)
                                }

                                val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")

                                val loan = Loan(
                                    name = name,
                                    loanType = loanType,
                                    principalAmount = pVal,
                                    interestRateAnnual = rVal,
                                    frequency = frequency,
                                    installmentAmount = instFinal,
                                    disbursementDate = loanIssueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                    firstRepaymentDate = firstRepaymentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                                    outstandingBalance = if (isNewMode) pVal else currentOutstanding,
                                    nextDueDate = if (isNewMode) {
                                        firstRepaymentDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    } else {
                                        var nextDate = firstRepaymentDate
                                        for (i in 1..nPassed) {
                                            nextDate = when(frequency) {
                                                "MONTHLY" -> nextDate.plusMonths(1)
                                                "QUARTERLY" -> nextDate.plusMonths(3)
                                                "HALF_YEARLY" -> nextDate.plusMonths(6)
                                                "YEARLY" -> nextDate.plusYears(1)
                                                else -> nextDate.plusMonths(1)
                                            }
                                        }
                                        nextDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    },
                                    periodsTotal = nTotal,
                                    periodsPassed = nPassed,
                                    accountId = 0,
                                    partyId = 0,
                                    gapMethod = gapMethod,
                                    gapInterest = gapInterest,
                                    isActualEmiDifferent = isActualEmiDifferent,
                                    actualRepaymentAmount = actualAmt,
                                    isAutoRecordEnabled = false,
                                    sourceAccountId = selectedDisbursementAccountId,
                                    tags = tagsString,
                                    notes = null
                                )

                                viewModel.saveLoanWithAccount(
                                    loan = loan,
                                    minorHeadId = selectedMinorHeadId!!,
                                    accountName = name,
                                    partyName = null,
                                    isExisting = !isNewMode,
                                    disbursementAccountId = selectedDisbursementAccountId,
                                    loanIssueDate = loanIssueDate.format(DateTimeFormatter.ISO_DATE),
                                    issuedAmount = pVal,
                                    isCreateAccount = isCreateAccountChecked,
                                    isUpdateBank = isUpdateBankBalanceChecked
                                )
                                onBack()
                            }
                        ) {
                            Text("Confirm")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showConfirmDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
