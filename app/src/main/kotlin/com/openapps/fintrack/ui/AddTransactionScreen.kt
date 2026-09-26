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
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.openapps.fintrack.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapps.fintrack.data.Account
import com.openapps.fintrack.data.Category
import com.openapps.fintrack.data.RawTransactionHelper
import com.openapps.fintrack.data.TemplateLegacy
import com.openapps.fintrack.data.Party
import com.openapps.fintrack.data.TransactionLegacy
import com.openapps.fintrack.data.TransactionWithDetails
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.Locale

@Serializable
data class MultiEntryRow(
    val id: String = UUID.randomUUID().toString(),
    var categoryId: Int? = null,
    var accountId: Int? = null,
    var amount: String = "",
    var note: String? = null,
    var tags: String? = null,
    var currencyCode: String? = null
)

class AddTransactionState(
    initialType: String,
    initialAmount: String,
    initialNote: String,
    initialDate: String,
    initialTime: String,
    initialAccountId: Int?,
    initialToAccountId: Int?,
    initialCategoryId: Int?,
    initialBaseCurrency: String,
    initialForeignCurrency: String?,
    initialAmountLocal: String?,
    initialIsManual: Boolean = false
) {
    var type by mutableStateOf(initialType)
    var amount by mutableStateOf(initialAmount)
    var note by mutableStateOf(initialNote)
    var date by mutableStateOf(initialDate)
    var time by mutableStateOf(initialTime)
    
    var selectedAccountId by mutableStateOf(initialAccountId)
    var selectedToAccountId by mutableStateOf(initialToAccountId)
    var selectedCategoryId by mutableStateOf(initialCategoryId)
    var selectedPartyId by mutableStateOf<Int?>(null)
    var selectedToPartyId by mutableStateOf<Int?>(null)
    
    var foreignCurrency by mutableStateOf(initialForeignCurrency ?: initialBaseCurrency)
    var amountForeign by mutableStateOf(initialAmount)
    var amountLocal by mutableStateOf(initialAmountLocal ?: initialAmount)
    var currentRate by mutableDoubleStateOf(1.0)
    var manualRateInput by mutableStateOf("1.0")
    var isManualLocalAmount by mutableStateOf(initialIsManual)
    
    var isMultiEntry by mutableStateOf(false)
    var multiEntryType by mutableStateOf("Category") 
    val multiEntryRows = mutableStateListOf<MultiEntryRow>()
    
    var isSubscription by mutableStateOf(false)
    var isExistingSubscription by mutableStateOf(false)
    var subName by mutableStateOf("")
    var subFrequency by mutableStateOf("")
    
    var amountError by mutableStateOf<String?>(null)
    var accountError by mutableStateOf<String?>(null)
    var toAccountError by mutableStateOf<String?>(null)
    var categoryError by mutableStateOf<String?>(null)
    
    val attachments = androidx.compose.runtime.mutableStateListOf<String>()
    var isNegotiated by mutableStateOf(false)
    var negotiationAmountOriginal by mutableStateOf("")
    var merchantName by mutableStateOf("")
    var isDiscretionary by mutableStateOf(false)
    
    var showCalculator by mutableStateOf(false)
    var showMismatchDialog by mutableStateOf(false)
    var mismatchTotals by mutableStateOf(Pair(0.0, 0.0))

    var invoiceNumber by mutableStateOf("")
    var dueDays by mutableStateOf("")
    val selectedInvoiceIds = mutableStateListOf<Int>()

    var isForGoal by mutableStateOf(false)
    var selectedGoalId by mutableStateOf<Int?>(null)

    var fdLast4 by mutableStateOf("")
    var fdMaturityDate by mutableStateOf("")
    var selectedFdCreationHeaderId by mutableStateOf<Int?>(null)
    var showFdSelectionDialog by mutableStateOf(false)
}

@Composable
fun rememberAddTransactionState(
    viewModel: ExpenseViewModel,
    txnDetail: TransactionWithDetails?,
    draft: DraftTransaction?,
    editingTemplate: TemplateLegacy?,
    initialData: Bundle?
): AddTransactionState {
    val initialType = remember {
        if (txnDetail != null) (if (txnDetail.transaction.toAccountId != null) "transfer" else txnDetail.categoryType) ?: "expense"
        else if (editingTemplate != null) editingTemplate.type
        else draft?.type ?: initialData?.getString("type") ?: "expense"
    }
    
    val initialAmount = remember {
        if (txnDetail != null) txnDetail.transaction.let { (it.amountOriginal ?: it.amount).toString() }
        else if (editingTemplate != null) editingTemplate.amount?.toString() ?: ""
        else draft?.amount ?: initialData?.getDouble("amount", 0.0)?.takeIf { it > 0 }?.toString() ?: ""
    }
    
    val initialNote = remember {
        if (txnDetail != null) txnDetail.transaction.note ?: ""
        else if (editingTemplate != null) editingTemplate.note ?: ""
        else draft?.note ?: initialData?.getString("sms_body") ?: ""
    }

    val calendar = Calendar.getInstance()
    val initialDate = remember {
        if (txnDetail != null) txnDetail.transaction.date
        else draft?.date ?: initialData?.getString("date") ?:
        String.format("%d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
    }
    
    val initialTime = remember {
        if (txnDetail != null) txnDetail.transaction.time
        else draft?.time ?: initialData?.getString("time") ?:
        String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    val state = remember {
        AddTransactionState(
            initialType = initialType,
            initialAmount = initialAmount,
            initialNote = initialNote,
            initialDate = initialDate,
            initialTime = initialTime,
            initialAccountId = (if(txnDetail != null) txnDetail.transaction.accountId else editingTemplate?.accountId ?: draft?.accountId),
            initialToAccountId = (if(txnDetail != null) txnDetail.transaction.toAccountId else editingTemplate?.toAccountId ?: draft?.toAccountId),
            initialCategoryId = (if(txnDetail != null) txnDetail.transaction.categoryId else editingTemplate?.categoryId ?: draft?.categoryId),
            initialBaseCurrency = viewModel.baseCurrency,
            initialForeignCurrency = txnDetail?.transaction?.currencyCode,
            initialAmountLocal = txnDetail?.transaction?.amountBase?.toString()?.takeIf { it != "null" },
            initialIsManual = (txnDetail != null)
        ).apply {
            if (draft != null) {
                invoiceNumber = draft.invoiceNumber
                dueDays = draft.dueDays
                selectedInvoiceIds.clear()
                selectedInvoiceIds.addAll(draft.selectedInvoiceIds)
            } else if (txnDetail != null) {
                invoiceNumber = txnDetail.transaction.invoiceNumber ?: ""
                dueDays = txnDetail.transaction.dueDays?.toString() ?: ""
            }
        }
    }

    LaunchedEffect(txnDetail) {
        if (txnDetail != null) {
            state.isNegotiated = txnDetail.transaction.isNegotiated
            state.negotiationAmountOriginal = txnDetail.transaction.negotiationAmountOriginal?.toString() ?: ""
            state.merchantName = txnDetail.transaction.merchantName ?: ""
            state.isDiscretionary = txnDetail.transaction.isDiscretionary
        }
    }

    val headerDetailed by remember(txnDetail?.transaction?.id) {
        if (txnDetail != null) viewModel.getTransactionHeaderWithLines(txnDetail.transaction.id)
        else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    LaunchedEffect(headerDetailed) {
        val line = headerDetailed?.lines?.firstOrNull()?.line
        if (line != null) {
            if (!line.fdLast4.isNullOrBlank()) state.fdLast4 = line.fdLast4
            if (!line.fdMaturityDate.isNullOrBlank()) state.fdMaturityDate = line.fdMaturityDate
            val clearances = viewModel.getFdClearancesForRedemption(headerDetailed!!.header.id)
            clearances.firstOrNull()?.let {
                state.selectedFdCreationHeaderId = it.creationHeaderId
            }
        }
    }

    LaunchedEffect(draft, editingTemplate, txnDetail) {
        if (state.multiEntryRows.isEmpty()) {
            if (draft?.isMultiEntry == true) {
                state.isMultiEntry = true
                state.multiEntryType = draft.multiEntryType
                state.multiEntryRows.addAll(draft.multiEntryRows.map { MultiEntryRow(categoryId = it.categoryId, accountId = it.accountId, amount = it.amount, note = it.note, tags = it.tags, currencyCode = it.currencyCode) })
            } else if (editingTemplate?.multiEntries != null) {
                state.isMultiEntry = true
                state.multiEntryType = "Category" 
                editingTemplate.multiEntries.split("|").forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size >= 2) {
                        state.multiEntryRows.add(MultiEntryRow(categoryId = parts[0].toIntOrNull(), amount = parts[1], note = parts.getOrNull(2)?.ifBlank { null }, currencyCode = parts.getOrNull(3)?.ifBlank { null }, tags = parts.getOrNull(4)?.ifBlank { null }))
                    }
                }
            } else if (!state.isMultiEntry) {
                state.multiEntryRows.clear()
                state.multiEntryRows.add(MultiEntryRow(categoryId = null, accountId = null, amount = "", currencyCode = viewModel.baseCurrency))
            }
        }
        
        if (draft != null) {
            state.selectedPartyId = draft.selectedPartyId
            state.selectedToPartyId = draft.selectedToPartyId
            state.foreignCurrency = draft.foreignCurrency ?: state.foreignCurrency
            state.isNegotiated = draft.isNegotiated
            state.negotiationAmountOriginal = draft.negotiationAmountOriginal
            state.merchantName = draft.merchantName
            state.isDiscretionary = draft.isDiscretionary
            state.subName = draft.subName
            state.subFrequency = draft.subFrequency
            state.isSubscription = draft.subName.isNotEmpty()
        } else if (txnDetail?.transaction?.subFrequency != null) {
            state.isSubscription = true
            state.subName = txnDetail.transaction.subName ?: ""
            state.subFrequency = txnDetail.transaction.subFrequency.toString()
            state.selectedPartyId = txnDetail.transaction.partyId
            state.selectedToPartyId = txnDetail.transaction.toPartyId
        } else if (editingTemplate?.subName != null) {
            state.isSubscription = true
            state.subName = editingTemplate.subName
            state.subFrequency = editingTemplate.subFrequency?.toString() ?: ""
            state.isExistingSubscription = true
        }
    }

    return state
}

@Composable
fun CurrencyPickerField(state: AddTransactionState, viewModel: ExpenseViewModel, readOnly: Boolean) {
    var showCurrencyDialog by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !readOnly) { showCurrencyDialog = true }) {
        OutlinedTextField(
            value = state.foreignCurrency,
            onValueChange = {},
            label = { Text(stringResource(R.string.label_currency)) },
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline
            ),
            trailingIcon = { if (!readOnly) Icon(Icons.Default.ArrowDropDown, null) }
        )
    }
    if (showCurrencyDialog) {
        CurrencySelectionDialog(
            onDismiss = { showCurrencyDialog = false },
            onSelected = { 
                state.foreignCurrency = it
                showCurrencyDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    viewModel: ExpenseViewModel, 
    onBack: () -> Unit, 
    onNavigate: ((String) -> Unit)? = null,
    initialData: Bundle? = null,
    readOnly: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val txnDetail = viewModel.selectedTransactionDetail
    val draft = viewModel.draftTransaction
    val editingTemplate = viewModel.editingTemplate
    
    val state = rememberAddTransactionState(viewModel, txnDetail, draft, editingTemplate, initialData)
    
    var isLocalEditMode by remember { mutableStateOf(false) }
    var isDuplicateMode by remember { mutableStateOf(false) }
    val isActuallyReadOnly = readOnly && !isLocalEditMode && !isDuplicateMode

    val isTemplateMode = initialData?.getBoolean("template_mode", false) ?: false
    var templateName by remember { mutableStateOf(editingTemplate?.name ?: "") }
    var showNameDialog by remember { mutableStateOf(false) }
    var showTemplateSelection by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }

    val accountsRaw by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val allMinorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())
    val allMajorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val accountBalances by viewModel.getAccountBalances(state.date).collectAsState(initial = emptyList())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val exchangeRates by viewModel.getExchangeRates().collectAsState(initial = emptyList())
    val masterSubscriptions by viewModel.getAllSubscriptionsMaster().collectAsState(initial = emptyList())
    val allAllocations by viewModel.allAllocations.collectAsState(initial = emptyList())

    val clipboardManager = LocalClipboardManager.current
    val allAccountsList by viewModel.getAllAccounts().collectAsState(initial = emptyList())
    val allCategories by viewModel.getAllCategories().collectAsState(initial = emptyList())
    val dbParties by viewModel.getAllParties().collectAsState(initial = emptyList())

    var showRawStringPasteDialog by remember { mutableStateOf(false) }
    var rawStringInput by remember { mutableStateOf("") }
    var rawStringErrorMessage by remember { mutableStateOf<String?>(null) }

    val totalGoalAllocated = remember(state.selectedAccountId, allAllocations) {
        if (state.selectedAccountId != null) {
            allAllocations.filter { it.accountId == state.selectedAccountId }.sumOf { it.allocatedAmount }
        } else 0.0
    }
    val currentAccountBalance = remember(state.selectedAccountId, accountBalances) {
        if (state.selectedAccountId != null) {
            val balMinor = accountBalances.find { it.id == state.selectedAccountId }?.balance ?: 0L
            balMinor.toDouble() / 100.0
        } else 0.0
    }
    val enteredAmount = evaluateExpression(state.amount)
    val remainingBalance = currentAccountBalance - enteredAmount
    val isGoalDiversion = state.selectedAccountId != null &&
            totalGoalAllocated > 0.0 &&
            enteredAmount > 0.0 &&
            remainingBalance < totalGoalAllocated

    if (showSavedDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSavedDialog = false
                onBack() 
            },
            title = { Text(stringResource(R.string.title_success)) },
            text = { Text(stringResource(R.string.msg_saved_success)) },
            confirmButton = {
                Button(onClick = { 
                    showSavedDialog = false
                    onBack() 
                }) {
                    Text(stringResource(R.string.btn_ok))
                }
            }
        )
    }

    LaunchedEffect(state.subName, allTransactions) {
        if (state.subName.isNotEmpty() && state.isSubscription) {
            state.isExistingSubscription = allTransactions.any { 
                it.transaction.subName == state.subName && 
                it.transaction.id != txnDetail?.transaction?.id
            }
        }
    }
    
    val accounts = remember(accountsRaw, allMinorHeads) {
        accountsRaw.filter { a -> 
            allMinorHeads.find { it.id == a.minorHeadId }?.majorHeadId != 6
        }
    }
    
    val onAccountMicroAccounts = remember(accountsRaw, allMinorHeads) {
        accountsRaw.filter { a ->
            allMinorHeads.find { it.id == a.minorHeadId }?.majorHeadId == 6
        }
    }

    val categories by viewModel.getEnabledCategoriesByType(state.type).collectAsState(initial = emptyList())
    val allTags by viewModel.getAllTags().collectAsState(initial = emptyList())
    val ccCycles by viewModel.ccCycles.collectAsState(initial = emptyList())

    val isFromOnAccount = remember(state.selectedAccountId, allMinorHeads, accountsRaw) {
        val acc = accountsRaw.find { it.id == state.selectedAccountId }
        allMinorHeads.find { it.id == acc?.minorHeadId }?.majorHeadId == 6
    }
    val isToOnAccount = remember(state.selectedToAccountId, allMinorHeads, accountsRaw) {
        val acc = accountsRaw.find { it.id == state.selectedToAccountId }
        allMinorHeads.find { it.id == acc?.minorHeadId }?.majorHeadId == 6
    }
    
    var isFromOnAccountSelected by remember(isFromOnAccount) { mutableStateOf(isFromOnAccount) }
    var isToOnAccountSelected by remember(isToOnAccount) { mutableStateOf(isToOnAccount) }

    val displayPartyName = txnDetail?.partyName ?: accountsRaw.find { it.id == state.selectedAccountId }?.name ?: stringResource(R.string.label_select)
    val displayToPartyName = txnDetail?.toPartyName ?: accountsRaw.find { it.id == state.selectedToAccountId }?.name ?: stringResource(R.string.label_select)
    
    val selectedTagIds = remember { 
        val list = mutableStateListOf<Int>()
        if (draft != null) list.addAll(draft.selectedTagIds)
        else if (editingTemplate != null) editingTemplate.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { list.addAll(it) }
        else txnDetail?.transaction?.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { list.addAll(it) }
        list
    }

    val headerDetailed by remember(txnDetail?.transaction?.id) {
        if (txnDetail != null) viewModel.getTransactionHeaderWithLines(txnDetail.transaction.id)
        else kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    LaunchedEffect(headerDetailed) {
        headerDetailed?.let { h ->
            val numLines = h.lines.size
            val isMulti = numLines > 1 || h.header.transactionNumber.startsWith("MEXP") || h.header.transactionNumber.startsWith("MINC")
            
            if (isMulti) {
                state.isMultiEntry = true
                val distinctCats = h.lines.mapNotNull { it.line.categoryId }.distinct()
                val distinctAccs = h.lines.mapNotNull { it.line.accountId }.distinct()
                state.multiEntryType = if (distinctAccs.size > 1 && distinctCats.size <= 1) "Account" else "Category"

                state.multiEntryRows.clear()
                val rowTagIds = mutableSetOf<Int>()

                h.lines.forEach { l ->
                    val rowTagsStr = l.line.tags
                    if (!rowTagsStr.isNullOrBlank()) {
                        rowTagsStr.split(",").mapNotNull { it.trim().toIntOrNull() }.forEach { rowTagIds.add(it) }
                    }
                    state.multiEntryRows.add(
                        MultiEntryRow(
                            categoryId = l.line.categoryId,
                            accountId = l.line.accountId,
                            amount = (l.line.amountOriginal ?: l.line.amount).toString(),
                            note = l.line.note,
                            tags = rowTagsStr,
                            currencyCode = l.line.currencyCode
                        )
                    )
                }

                val totalSum = h.lines.sumOf { it.line.amountOriginal ?: it.line.amount }
                state.amount = totalSum.toString()
                if (viewModel.enableMultiCurrency) {
                    state.amountForeign = totalSum.toString()
                }

                if (!h.header.note.isNullOrBlank()) {
                    state.note = h.header.note
                }

                selectedTagIds.clear()
                val commonTagIds = h.tags.map { it.id }.filter { it !in rowTagIds }
                selectedTagIds.addAll(commonTagIds)
            }
        }
    }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun handleRawStringImport(rawInput: String) {
        if (rawInput.isBlank()) return

        val parseResult = RawTransactionHelper.parse(rawInput)
        if (parseResult.isFailure) {
            rawStringErrorMessage = "Invalid Raw String JSON format:\n${parseResult.exceptionOrNull()?.localizedMessage}"
            return
        }

        val rawData = parseResult.getOrThrow()
        val missingEntities = mutableListOf<String>()

        fun findAccount(name: String?): Account? {
            if (name.isNullOrBlank()) return null
            return allAccountsList.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
                ?: accountsRaw.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
        }

        fun findCategory(name: String?): Category? {
            if (name.isNullOrBlank()) return null
            return allCategories.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
        }

        fun findParty(name: String?): Any? {
            if (name.isNullOrBlank()) return null
            return dbParties.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
                ?: onAccountMicroAccounts.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
                ?: allAccountsList.find { it.name.trim().equals(name.trim(), ignoreCase = true) }
        }

        val matchedAccount = if (!rawData.accountName.isNullOrBlank()) {
            val acc = findAccount(rawData.accountName)
            if (acc == null) missingEntities.add("- Account: ${rawData.accountName}")
            acc
        } else null

        val matchedToAccount = if (!rawData.toAccountName.isNullOrBlank()) {
            val acc = findAccount(rawData.toAccountName)
            if (acc == null) missingEntities.add("- To Account: ${rawData.toAccountName}")
            acc
        } else null

        val matchedCategory = if (!rawData.categoryName.isNullOrBlank()) {
            val cat = findCategory(rawData.categoryName)
            if (cat == null) missingEntities.add("- Category: ${rawData.categoryName}")
            cat
        } else null

        val matchedParty = if (!rawData.partyName.isNullOrBlank()) {
            val p = findParty(rawData.partyName)
            if (p == null) missingEntities.add("- Party: ${rawData.partyName}")
            p
        } else null

        val matchedToParty = if (!rawData.toPartyName.isNullOrBlank()) {
            val p = findParty(rawData.toPartyName)
            if (p == null) missingEntities.add("- To Party: ${rawData.toPartyName}")
            p
        } else null

        rawData.multiLines.forEachIndexed { idx, line ->
            if (!line.accountName.isNullOrBlank() && findAccount(line.accountName) == null) {
                missingEntities.add("- Line ${idx + 1} Account: ${line.accountName}")
            }
            if (!line.toAccountName.isNullOrBlank() && findAccount(line.toAccountName) == null) {
                missingEntities.add("- Line ${idx + 1} To Account: ${line.toAccountName}")
            }
            if (!line.categoryName.isNullOrBlank() && findCategory(line.categoryName) == null) {
                missingEntities.add("- Line ${idx + 1} Category: ${line.categoryName}")
            }
        }

        if (missingEntities.isNotEmpty()) {
            rawStringErrorMessage = "The following entities from the raw string do not exist in your database:\n" + missingEntities.joinToString("\n")
            return
        }

        showRawStringPasteDialog = false
        rawStringInput = ""

        if (rawData.type.isNotBlank()) state.type = rawData.type
        if (rawData.date.isNotBlank()) state.date = rawData.date
        if (rawData.time.isNotBlank()) state.time = rawData.time

        if (matchedAccount != null) state.selectedAccountId = matchedAccount.id
        if (matchedToAccount != null) state.selectedToAccountId = matchedToAccount.id
        if (matchedCategory != null) state.selectedCategoryId = matchedCategory.id

        val amt = rawData.originalAmount ?: rawData.baseAmount
        if (amt != null) {
            state.amount = if (amt == 0.0) "" else amt.toString()
            state.amountForeign = if (amt == 0.0) "" else amt.toString()
        }
        if (!rawData.currencyCode.isNullOrBlank()) {
            state.foreignCurrency = rawData.currencyCode
        }
        if (rawData.baseAmount != null) {
            state.amountLocal = rawData.baseAmount.toString()
        }

        state.note = rawData.note ?: ""
        state.merchantName = rawData.merchantName ?: ""
        state.isDiscretionary = rawData.isDiscretionary
        state.isNegotiated = rawData.isNegotiated
        state.negotiationAmountOriginal = rawData.negotiationAmountOriginal?.toString() ?: ""

        if (matchedParty != null) {
            state.selectedPartyId = when (matchedParty) {
                is Party -> matchedParty.id
                is Account -> matchedParty.id
                else -> null
            }
        }
        if (matchedToParty != null) {
            state.selectedToPartyId = when (matchedToParty) {
                is Party -> matchedToParty.id
                is Account -> matchedToParty.id
                else -> null
            }
        }

        state.invoiceNumber = rawData.invoiceNumber ?: ""
        state.dueDays = rawData.dueDays?.toString() ?: ""

        if (!rawData.subName.isNullOrBlank()) {
            state.isSubscription = true
            state.subName = rawData.subName
            state.subFrequency = rawData.subFrequency?.toString() ?: ""
        } else {
            state.isSubscription = false
            state.subName = ""
            state.subFrequency = ""
        }

        state.fdLast4 = rawData.fdLast4 ?: ""
        state.fdMaturityDate = rawData.fdMaturityDate ?: ""

        selectedTagIds.clear()
        rawData.tagNames.forEach { tagName ->
            allTags.find { it.name.trim().equals(tagName.trim(), ignoreCase = true) }?.let { tag ->
                selectedTagIds.add(tag.id)
            }
        }

        if (rawData.multiLines.isNotEmpty()) {
            state.isMultiEntry = true
            state.multiEntryRows.clear()
            rawData.multiLines.forEach { line ->
                val lineCatId = findCategory(line.categoryName)?.id
                val lineAccId = findAccount(line.accountName)?.id
                val lineTagIdsStr = line.tagNames.mapNotNull { tn ->
                    allTags.find { it.name.trim().equals(tn.trim(), ignoreCase = true) }?.id?.toString()
                }.joinToString(",").takeIf { it.isNotBlank() }

                state.multiEntryRows.add(
                    MultiEntryRow(
                        categoryId = lineCatId,
                        accountId = lineAccId,
                        amount = if (line.amount == 0.0) "" else line.amount.toString(),
                        note = line.note,
                        tags = lineTagIdsStr,
                        currencyCode = line.currencyCode
                    )
                )
            }
        }

        Toast.makeText(context, "Transaction details imported from Raw String!", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(state.foreignCurrency, exchangeRates) {
        if (state.foreignCurrency == viewModel.baseCurrency) {
            state.currentRate = 1.0
            state.manualRateInput = "1.0"
        } else {
            val rateObj = exchangeRates.find { it.currencyCode == state.foreignCurrency }
            val rate = rateObj?.rateToBase ?: 1.0
            state.currentRate = rate
            state.manualRateInput = String.format(Locale.US, "%.4f", rate)
        }
    }

    LaunchedEffect(state.amount, state.currentRate, state.foreignCurrency, viewModel.enableMultiCurrency) {
        if (!state.isManualLocalAmount || !viewModel.enableMultiCurrency || state.foreignCurrency == viewModel.baseCurrency) {
            if (viewModel.enableMultiCurrency && state.foreignCurrency != viewModel.baseCurrency) {

                state.amountForeign = state.amount
                val foreignVal = evaluateExpression(state.amount)
                if (foreignVal > 0) {
                    state.amountLocal = try {
                        String.format(Locale.US, "%.2f", foreignVal * state.currentRate)
                    } catch (e: Exception) {
                        (foreignVal * state.currentRate).toString()
                    }
                } else {
                    state.amountLocal = ""
                }
            } else {
                state.amountForeign = state.amount
                state.amountLocal = state.amount
            }
        }
    }

    fun saveAsDraft() {
        if (!isActuallyReadOnly && !isTemplateMode) {
            viewModel.draftTransaction = DraftTransaction(
                type = state.type,
                amount = state.amount,
                note = state.note,
                date = state.date,
                time = state.time,
                accountId = state.selectedAccountId,
                toAccountId = state.selectedToAccountId,
                categoryId = state.selectedCategoryId,
                selectedTagIds = selectedTagIds.toList(),
                isMultiEntry = state.isMultiEntry,
                multiEntryRows = state.multiEntryRows.map { DraftMultiEntryRow(it.categoryId, it.accountId, it.amount, it.note, it.currencyCode, it.tags) },
                multiEntryType = state.multiEntryType,
                selectedPartyId = state.selectedPartyId,
                selectedToPartyId = state.selectedToPartyId,
                foreignCurrency = state.foreignCurrency,
                isNegotiated = state.isNegotiated,
                negotiationAmountOriginal = state.negotiationAmountOriginal,
                merchantName = state.merchantName,
                isDiscretionary = state.isDiscretionary,
                subName = state.subName,
                subFrequency = state.subFrequency,
                invoiceNumber = state.invoiceNumber,
                dueDays = state.dueDays,
                selectedInvoiceIds = state.selectedInvoiceIds.toList()
            )
        }
    }

    val navWithDraft: (String) -> Unit = { route ->
        saveAsDraft()
        onNavigate?.invoke(route)
    }

    BackHandler {
        saveAsDraft()
        onBack()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (isTemplateMode) stringResource(R.string.btn_add_template) else if (isActuallyReadOnly) stringResource(R.string.title_transaction_details) else stringResource(R.string.btn_add_transaction)) },
                navigationIcon = {
                    IconButton(onClick = { saveAsDraft(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    if (isActuallyReadOnly && txnDetail != null) {
                        IconButton(onClick = {
                            val rawData = if (headerDetailed != null) {
                                RawTransactionHelper.fromTransactionWithLinesAndDetails(headerDetailed!!, allTags)
                            } else {
                                RawTransactionHelper.fromTransactionWithDetails(txnDetail, allTags)
                            }
                            val jsonStr = RawTransactionHelper.toJson(rawData)
                            clipboardManager.setText(AnnotatedString(jsonStr))
                            Toast.makeText(context, "Transaction Raw String copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Raw String"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (showRawStringPasteDialog) {
            AlertDialog(
                onDismissRequest = { showRawStringPasteDialog = false },
                title = { Text("Import Transaction Raw String") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Paste or enter transaction Raw String (JSON):",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = rawStringInput,
                            onValueChange = { rawStringInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                            maxLines = 8,
                            placeholder = { Text("{\n  \"type\": \"expense\",\n  ...\n}") }
                        )
                        Button(
                            onClick = {
                                val clipData = clipboardManager.getText()
                                if (clipData != null) {
                                    rawStringInput = clipData.text
                                }
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Paste Clipboard")
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { handleRawStringImport(rawStringInput) }) {
                        Text("Import")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRawStringPasteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (rawStringErrorMessage != null) {
            AlertDialog(
                onDismissRequest = { rawStringErrorMessage = null },
                title = { Text("Import Error") },
                text = {
                    Text(rawStringErrorMessage!!)
                },
                confirmButton = {
                    Button(onClick = { rawStringErrorMessage = null }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }
        if (state.showMismatchDialog) {
            AlertDialog(
                onDismissRequest = { state.showMismatchDialog = false },
                title = { Text(stringResource(R.string.title_total_mismatch)) },
                text = { Text(stringResource(R.string.msg_total_mismatch_desc, state.mismatchTotals.first, state.mismatchTotals.second)) },
                confirmButton = {
                    TextButton(onClick = { 
                        val newTotal = state.mismatchTotals.second.toString()
                        state.amount = newTotal
                        if (viewModel.enableMultiCurrency && state.type != "transfer") {
                            state.amountForeign = newTotal
                            state.isManualLocalAmount = false
                        }
                        state.showMismatchDialog = false
                    }) { Text(stringResource(R.string.btn_update_total)) }
                }
            )
        }

        if (showNameDialog) {
            TemplateNameDialog(
                onDismiss = { showNameDialog = false },
                onSave = { name ->
                    val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                    val multiStr = if (state.isMultiEntry) {
                        state.multiEntryRows.joinToString("|") { "${it.categoryId}:${it.amount}:${it.note ?: ""}:${it.currencyCode ?: ""}:${it.tags ?: ""}" }
                    } else null

                    val newTemplate = TemplateLegacy(
                        name = name,
                        type = state.type,
                        accountId = state.selectedAccountId,
                        toAccountId = state.selectedToAccountId,
                        categoryId = state.selectedCategoryId,
                        amount = evaluateExpression(state.amount).takeIf { it > 0 },
                        note = state.note,
                        tags = tagsString,
                        multiEntries = multiStr,
                        subName = if (state.isSubscription) state.subName else null,
                        subFrequency = if (state.isSubscription) state.subFrequency.toIntOrNull() else null
                    )
                    
                    scope.launch {
                        if (viewModel.saveTemplate(newTemplate)) {
                            Toast.makeText(context, context.getString(R.string.msg_template_saved), Toast.LENGTH_SHORT).show()
                            showNameDialog = false
                            if (isTemplateMode) onBack()
                        } else {
                            Toast.makeText(context, context.getString(R.string.msg_template_exists), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }

        if (showTemplateSelection) {
            val templates by viewModel.getAllTemplates().collectAsState(initial = emptyList())
            TemplateSelectionDialog(
                templates = templates,
                onDismiss = { showTemplateSelection = false },
                onNavigate = navWithDraft,
                onSelected = { t ->
                    state.type = t.type
                    state.selectedAccountId = t.accountId
                    state.selectedToAccountId = t.toAccountId
                    state.selectedCategoryId = t.categoryId
                    state.amount = t.amount?.toString() ?: ""
                    state.note = t.note ?: ""
                    selectedTagIds.clear()
                    t.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { selectedTagIds.addAll(it) }
                    
                    if (t.multiEntries != null) {
                        state.isMultiEntry = true
                        state.multiEntryRows.clear()
                        t.multiEntries.split("|").forEach { entry ->
                            val parts = entry.split(":")
                            if (parts.size >= 2) {
                                state.multiEntryRows.add(MultiEntryRow(categoryId = parts[0].toIntOrNull(), amount = parts[1], note = parts.getOrNull(2)?.ifBlank { null }, currencyCode = parts.getOrNull(3)?.ifBlank { null }, tags = parts.getOrNull(4)?.ifBlank { null }))
                            }
                        }
                    } else {
                        state.isMultiEntry = false
                    }

                    if (t.subName != null) {
                        state.isSubscription = true
                        state.subName = t.subName
                        state.subFrequency = t.subFrequency?.toString() ?: ""
                        state.isExistingSubscription = true
                    } else {
                        state.isSubscription = false
                    }

                    showTemplateSelection = false
                }
            )
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            if (isTemplateMode) {
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    label = { Text(stringResource(R.string.label_template_name)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = CircleShape
                )
            }

            if (txnDetail != null) {
                val currentStatus = txnDetail.transaction.reconciliationStatus.ifBlank {
                    if (txnDetail.transaction.isReconciled) "VERIFIED" else "PENDING"
                }

                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text(
                        text = stringResource(R.string.label_transaction_status),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pending
                        val isPending = currentStatus.equals("PENDING", ignoreCase = true)
                        FilterChip(
                            selected = isPending,
                            onClick = {
                                viewModel.updateTransactionStatus(txnDetail.transaction.id, "PENDING")
                            },
                            label = { Text(stringResource(R.string.status_pending)) },
                            leadingIcon = if (isPending) {
                                { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Verified (Reconciled)
                        val isVerified = currentStatus.equals("VERIFIED", ignoreCase = true)
                        FilterChip(
                            selected = isVerified,
                            onClick = {
                                viewModel.updateTransactionStatus(txnDetail.transaction.id, "VERIFIED")
                            },
                            label = { Text(stringResource(R.string.status_verified)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isVerified) Color(0xFF4CAF50) else Color.Gray
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF4CAF50).copy(alpha = 0.15f),
                                selectedLabelColor = Color(0xFF2E7D32)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        // Void
                        val isVoid = currentStatus.equals("VOID", ignoreCase = true)
                        FilterChip(
                            selected = isVoid,
                            onClick = {
                                viewModel.updateTransactionStatus(txnDetail.transaction.id, "VOID")
                            },
                            label = { Text(stringResource(R.string.status_void)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Warning,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isVoid) Color(0xFFF44336) else Color.Gray
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF44336).copy(alpha = 0.15f),
                                selectedLabelColor = Color(0xFFC62828)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (txnDetail.transaction.transactionNumber != null) {
                    OutlinedTextField(
                        value = txnDetail.transaction.transactionNumber,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_txn_no_colon)) },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.primary,
                            disabledBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            disabledLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            TransactionTypeRow(state, isActuallyReadOnly, isTemplateMode, viewModel)

            Spacer(Modifier.height(8.dp))

            if (!isTemplateMode) {
                DateTimeSection(state, isActuallyReadOnly, context, (txnDetail != null), onPasteRawStringClick = { showRawStringPasteDialog = true })
            }

            SubscriptionSection(state, viewModel, allTransactions, isActuallyReadOnly)

            Spacer(Modifier.height(8.dp))

            AccountSection(
                state = state,
                viewModel = viewModel,
                accounts = accounts,
                balances = accountBalances,
                majorHeads = allMajorHeads,
                minorHeads = allMinorHeads,
                categories = categories,
                onAccountMicroAccounts = onAccountMicroAccounts,
                readOnly = isActuallyReadOnly,
                onNavigate = navWithDraft,
                displayPartyName = displayPartyName,
                displayToPartyName = displayToPartyName,
                isFromOnAccountSelected = isFromOnAccountSelected,
                isToOnAccountSelected = isToOnAccountSelected,
                onFromOnAccountChange = { isFromOnAccountSelected = it },
                onToOnAccountChange = { isToOnAccountSelected = it }
            )

            if (viewModel.invoiceAgeTrackingEnabled && (isFromOnAccountSelected || isToOnAccountSelected)) {
                if (state.type == "transfer") {
                    val partyId = if (isFromOnAccountSelected) state.selectedAccountId else state.selectedToAccountId
                    if (partyId != null) {
                        InvoiceSelectionSection(state, viewModel, partyId, isActuallyReadOnly)
                    }
                } else {
                    InvoiceInfoSection(state, isActuallyReadOnly)
                }
            }

            val selectedCc = ccCycles.find { it.accountId == state.selectedAccountId }
            if (!isActuallyReadOnly && selectedCc != null) {
                val bestCc = ccCycles.maxByOrNull { it.interestFreeDaysLeft }
                if (bestCc != null && bestCc.accountId != selectedCc.accountId && bestCc.interestFreeDaysLeft > selectedCc.interestFreeDaysLeft) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    R.string.msg_credit_card_tip_days,
                                    bestCc.accountName,
                                    bestCc.interestFreeDaysLeft,
                                    selectedCc.interestFreeDaysLeft
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (state.isMultiEntry && viewModel.enableMultiCurrency && state.type != "transfer") {
                CurrencyPickerField(state, viewModel, isActuallyReadOnly)
            }

            Spacer(Modifier.height(8.dp))

            if (state.type != "transfer") {
                MultiEntrySection(state, viewModel, categories, accounts, accountBalances, allMajorHeads, allMinorHeads, isActuallyReadOnly, navWithDraft)
            }

            AmountAndCurrencySection(state, viewModel, isActuallyReadOnly, hideCurrencyPicker = state.isMultiEntry)

            if (isGoalDiversion) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ Goal Diversion Warning: This transaction will divert funds allocated for your goals. Total Goal Allocation for this account: ${viewModel.formatAmount(totalGoalAllocated)} | Remaining Balance After Transaction: ${viewModel.formatAmount(remainingBalance)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (state.type != "transfer" && viewModel.negotiationTrackerEnabled && !isActuallyReadOnly) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Checkbox(checked = state.isNegotiated, onCheckedChange = { state.isNegotiated = it })
                    Text(stringResource(R.string.label_i_negotiated), modifier = Modifier.clickable { state.isNegotiated = !state.isNegotiated })
                }
                if (state.isNegotiated) {
                    OutlinedTextField(
                        value = state.negotiationAmountOriginal,
                        onValueChange = { state.negotiationAmountOriginal = it },
                        label = { Text(stringResource(R.string.label_amt_before_negotiation)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = CircleShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }

            if (viewModel.merchantTrackerEnabled || state.merchantName.isNotBlank()) {
                var merchantExpanded by remember { mutableStateOf(false) }
                val existingMerchants = remember(allTransactions) {
                    allTransactions.mapNotNull { it.transaction.merchantName }.filter { it.isNotBlank() }.distinct().sorted()
                }
                val filteredMerchants = remember(state.merchantName) {
                    existingMerchants.filter { it.contains(state.merchantName, ignoreCase = true) && it != state.merchantName }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = state.merchantName,
                        onValueChange = { 
                            if (!isActuallyReadOnly) {
                                state.merchantName = it
                                merchantExpanded = true
                            }
                        },
                        label = { Text(stringResource(R.string.label_merchant_name)) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        readOnly = isActuallyReadOnly,
                        shape = CircleShape
                    )
                    if (merchantExpanded && filteredMerchants.isNotEmpty() && !isActuallyReadOnly) {
                        DropdownMenu(
                            expanded = merchantExpanded,
                            onDismissRequest = { merchantExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f),
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                        ) {
                            filteredMerchants.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m) },
                                    onClick = {
                                        state.merchantName = m
                                        merchantExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (state.type != "transfer" && viewModel.discretionarySpendingTrackerEnabled && !isActuallyReadOnly) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Checkbox(checked = state.isDiscretionary, onCheckedChange = { state.isDiscretionary = it })
                    Text(stringResource(R.string.label_discretionary_spend), modifier = Modifier.clickable { state.isDiscretionary = !state.isDiscretionary })
                }
            }

            if (state.showCalculator && !isActuallyReadOnly) {
                CalculatorKeypad(onValueChange = { 
                    if (viewModel.enableMultiCurrency && state.type != "transfer") {
                        state.amountForeign = it
                        state.amount = it
                        state.isManualLocalAmount = false
                    } else {
                        state.amount = it
                    }
                }, currentValue = if (viewModel.enableMultiCurrency && state.type != "transfer") state.amountForeign else state.amount)
            }

            if (viewModel.templateFields.contains("tags") || !isTemplateMode) {
                TagSelectionPopup(
                    allTags = allTags,
                    selectedIds = selectedTagIds,
                    multiSelect = viewModel.multiTagEnabled,
                    enabled = !isActuallyReadOnly,
                    onAdd = { navWithDraft("add_tag") }
                )
            }

            if (viewModel.templateFields.contains("note") || !isTemplateMode) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { if (!isActuallyReadOnly) state.note = it },
                    label = { Text(stringResource(R.string.label_note)) },
                    readOnly = isActuallyReadOnly,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    minLines = 3,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                )
            }

            AttachmentSection(viewModel, state, headerDetailed?.header?.transactionNumber, isActuallyReadOnly)

            if (!isActuallyReadOnly) {
                val goals by viewModel.allGoals.collectAsState(initial = emptyList())
                if (goals.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("This is for Goal", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Checkbox(
                                    checked = state.isForGoal,
                                    onCheckedChange = { state.isForGoal = it }
                                )
                            }
                            if (state.isForGoal) {
                                Spacer(Modifier.height(4.dp))
                                var goalExpanded by remember { mutableStateOf(false) }
                                val selectedGoal = goals.find { it.id == state.selectedGoalId }
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { goalExpanded = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(selectedGoal?.name ?: "Select Goal")
                                    }
                                    DropdownMenu(expanded = goalExpanded, onDismissRequest = { goalExpanded = false }) {
                                        goals.forEach { goal ->
                                            DropdownMenuItem(
                                                text = { Text(goal.name) },
                                                onClick = {
                                                    state.selectedGoalId = goal.id
                                                    goalExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isActuallyReadOnly) {
                ActionButtons(
                    state = state,
                    viewModel = viewModel,
                    isTemplateMode = isTemplateMode,
                    templateName = templateName,
                    selectedTagIds = selectedTagIds,
                    allTransactions = allTransactions,
                    onBack = onBack,
                    onShowNameDialog = { showNameDialog = true },
                    onShowTemplateSelection = { showTemplateSelection = true },
                    updateId = if (isLocalEditMode && !isDuplicateMode) txnDetail?.transaction?.id else null,
                    onTransactionSaved = { showSavedDialog = true }
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val now = Calendar.getInstance()
                            state.date = String.format("%d-%02d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
                            state.time = String.format("%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
                            isDuplicateMode = true
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_duplicate))
                    }
                    Button(
                        onClick = { isLocalEditMode = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_edit))
                    }
                }
                
                txnDetail?.transaction?.editedAt?.let { editedAt ->
                    val d = Instant.ofEpochMilli(editedAt).atZone(ZoneId.systemDefault())
                    Text(
                        text = stringResource(R.string.msg_edited_on, d.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.msg_viewing_archived_data), style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun TransactionTypeRow(state: AddTransactionState, readOnly: Boolean, isTemplateMode: Boolean, viewModel: ExpenseViewModel) {
    if (viewModel.templateFields.contains("type") || !isTemplateMode) {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.type == "income", 
                onClick = { if (!readOnly) { state.type = "income"; state.selectedCategoryId = null } }, 
                label = { Text(stringResource(R.string.label_income)) },
                enabled = !readOnly || state.type == "income"
            )
            FilterChip(
                selected = state.type == "expense", 
                onClick = { if (!readOnly) { state.type = "expense"; state.selectedCategoryId = null } }, 
                label = { Text(stringResource(R.string.label_expense)) },
                enabled = !readOnly || state.type == "expense"
            )
            FilterChip(
                selected = state.type == "transfer", 
                onClick = { if (!readOnly) { state.type = "transfer"; state.selectedAccountId = null; state.selectedToAccountId = null; state.selectedCategoryId = null; state.isMultiEntry = false } },
                label = { Text(stringResource(R.string.label_transfer)) },
                enabled = !readOnly || state.type == "transfer"
            )
            
            if (state.type != "transfer" && !isTemplateMode && !readOnly) {
                IconButton(onClick = {
                    when {
                        !state.isMultiEntry -> {
                            // Single -> Multi Category
                            state.multiEntryType = "Category"
                            state.isMultiEntry = true
                            state.multiEntryRows.clear()
                            state.multiEntryRows.add(MultiEntryRow(categoryId = state.selectedCategoryId, accountId = state.selectedAccountId, amount = state.amount, currencyCode = viewModel.baseCurrency))
                        }
                        state.multiEntryType == "Category" -> {
                            // Multi Category -> Multi Account
                            state.multiEntryType = "Account"
                            state.multiEntryRows.clear()
                            state.multiEntryRows.add(MultiEntryRow(categoryId = state.selectedCategoryId, accountId = state.selectedAccountId, amount = state.amount, currencyCode = viewModel.baseCurrency))
                        }
                        else -> {
                            // Multi Account -> Single
                            state.isMultiEntry = false
                        }
                    }
                }) {
                    Icon(
                        imageVector = when {
                            !state.isMultiEntry -> Icons.Default.HorizontalRule
                            state.multiEntryType == "Category" -> Icons.Default.List
                            else -> Icons.Default.Tune
                        },
                        contentDescription = "Cycle Mode",
                        tint = if (state.isMultiEntry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (readOnly && state.type != "transfer") {
                Text(
                    text = if (state.isMultiEntry) stringResource(R.string.label_multi_val, state.multiEntryType) else stringResource(R.string.label_single_val),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun DateTimeSection(
    state: AddTransactionState,
    readOnly: Boolean,
    context: android.content.Context,
    isEditMode: Boolean,
    onPasteRawStringClick: (() -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!readOnly) {
            IconButton(onClick = {
                try {
                    val current = LocalDate.parse(state.date)
                    state.date = current.minusDays(1).format(DateTimeFormatter.ISO_DATE)
                } catch (e: Exception) {}
            }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ChevronLeft, stringResource(R.string.label_prev_day), tint = MaterialTheme.colorScheme.primary)
            }
        }

        Box(modifier = Modifier.weight(1f).clickable(enabled = !readOnly) {
            val dateParts = state.date.split("-")
            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                state.date = String.format("%d-%02d-%02d", year, month + 1, dayOfMonth)
            }, dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt()).show()
        }) {
            OutlinedTextField(
                value = state.date,
                onValueChange = { },
                label = { Text(stringResource(R.string.label_date)) },
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        if (!readOnly) {
            IconButton(onClick = {
                try {
                    val current = LocalDate.parse(state.date)
                    state.date = current.plusDays(1).format(DateTimeFormatter.ISO_DATE)
                } catch (e: Exception) {}
            }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ChevronRight, stringResource(R.string.label_next_day), tint = MaterialTheme.colorScheme.primary)
            }
        }

        Box(modifier = Modifier.weight(0.7f).clickable(enabled = !readOnly) {
            val timeParts = state.time.split(":")
            TimePickerDialog(context, { _, hour, minute ->
                state.time = String.format("%02d:%02d", hour, minute)
            }, timeParts[0].toInt(), timeParts[1].toInt(), true).show()
        }) {
            OutlinedTextField(
                value = state.time,
                onValueChange = { },
                label = { Text(stringResource(R.string.label_time)) },
                readOnly = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        if (!readOnly && onPasteRawStringClick != null) {
            IconButton(
                onClick = onPasteRawStringClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Paste Raw String",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun InvoiceInfoSection(state: AddTransactionState, readOnly: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = state.invoiceNumber,
            onValueChange = { if (!readOnly) state.invoiceNumber = it },
            label = { Text(stringResource(R.string.label_invoice_no)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            shape = CircleShape
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.dueDays,
            onValueChange = { if (!readOnly) state.dueDays = it },
            label = { Text(stringResource(R.string.label_due_day_x)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = CircleShape
        )
    }
}

@Composable
fun InvoiceSelectionSection(state: AddTransactionState, viewModel: ExpenseViewModel, partyId: Int, readOnly: Boolean) {
    var showDialog by remember { mutableStateOf(false) }
    val pendingInvoices by viewModel.getInvoicesForPartyFlow(partyId, "9999-12-31").collectAsState(initial = emptyList())
    
    val selectedInvoicesText = if (state.selectedInvoiceIds.isEmpty()) {
        if (pendingInvoices.isEmpty()) stringResource(R.string.msg_no_pending_invoices) else stringResource(R.string.label_select_invoices_clear)
    } else stringResource(R.string.label_invoices_selected, state.selectedInvoiceIds.size)

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !readOnly && pendingInvoices.isNotEmpty()) { showDialog = true }) {
        OutlinedTextField(
            value = selectedInvoicesText,
            onValueChange = {},
            label = { Text(stringResource(R.string.label_clear_invoices)) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = if (pendingInvoices.isEmpty()) Color.Gray else MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline
            ),
            trailingIcon = { if (!readOnly && pendingInvoices.isNotEmpty()) Icon(Icons.Default.ArrowDropDown, "") }
        )
    }

    if (showDialog && !readOnly) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.title_select_invoices)) },
            text = {
                Box(Modifier.height(400.dp)) {
                    LazyColumn {
                        items(pendingInvoices) { inv ->
                            val outstanding = inv.detail.transaction.amount - inv.totalCleared
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (state.selectedInvoiceIds.contains(inv.detail.transaction.id)) {
                                        state.selectedInvoiceIds.remove(inv.detail.transaction.id)
                                    } else {
                                        state.selectedInvoiceIds.add(inv.detail.transaction.id)
                                    }
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = state.selectedInvoiceIds.contains(inv.detail.transaction.id),
                                    onCheckedChange = {
                                        if (it) state.selectedInvoiceIds.add(inv.detail.transaction.id)
                                        else state.selectedInvoiceIds.remove(inv.detail.transaction.id)
                                    }
                                )
                                Column {
                                    Text(stringResource(R.string.label_inv_item, inv.detail.transaction.invoiceNumber ?: stringResource(R.string.label_not_applicable), viewModel.formatAmount(outstanding)), style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.label_date_colon) + inv.detail.transaction.date, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            HorizontalDivider(modifier = Modifier.alpha(0.3f))
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showDialog = false }) { Text(stringResource(R.string.btn_done)) } }
        )
    }
}

@Composable
fun AccountSection(
    state: AddTransactionState,
    viewModel: ExpenseViewModel,
    accounts: List<com.openapps.fintrack.data.Account>,
    balances: List<com.openapps.fintrack.data.AccountBalance>,
    majorHeads: List<com.openapps.fintrack.data.MajorHead>,
    minorHeads: List<com.openapps.fintrack.data.MinorHead>,
    categories: List<com.openapps.fintrack.data.Category>,
    onAccountMicroAccounts: List<com.openapps.fintrack.data.Account>,
    readOnly: Boolean,
    onNavigate: ((String) -> Unit)?,
    displayPartyName: String,
    displayToPartyName: String,
    isFromOnAccountSelected: Boolean,
    isToOnAccountSelected: Boolean,
    onFromOnAccountChange: (Boolean) -> Unit,
    onToOnAccountChange: (Boolean) -> Unit
) {
    if (state.isMultiEntry && state.multiEntryType == "Account" && state.type != "transfer") {

        CategorySelectionDialog(
            label = stringResource(R.string.label_category),
            categories = categories,
            selectedId = state.selectedCategoryId,
            onSelected = { state.selectedCategoryId = it; state.categoryError = null },
            enabled = !readOnly,
            onAdd = { onNavigate?.invoke("add_category") },
            isError = state.categoryError != null
        )
        state.categoryError?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.labelSmall) }
        return
    }

    if (state.type == "transfer") {
        AccountSelectionDialog(
            label = stringResource(R.string.label_from_account),
            accounts = accounts,
            balances = balances,
            majorHeads = majorHeads,
            minorHeads = minorHeads,
            viewModel = viewModel,
            selectedId = state.selectedAccountId,
            onSelected = { 
                state.selectedAccountId = it
                onFromOnAccountChange(false)
                state.accountError = null 
            },
            onOnAccountSelected = {
                onFromOnAccountChange(true)
                state.selectedAccountId = null
                state.accountError = null
            },
            isOnAccountSelected = isFromOnAccountSelected,
            hasOnAccountOption = onAccountMicroAccounts.isNotEmpty(),
            enabled = !readOnly,
            onAdd = { onNavigate?.invoke("add_category") },
            isError = state.accountError != null
        )
        state.accountError?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.labelSmall) }
        
        AccountSelectionDialog(
            label = stringResource(R.string.label_to_account),
            accounts = accounts,
            balances = balances,
            majorHeads = majorHeads,
            minorHeads = minorHeads,
            viewModel = viewModel,
            selectedId = state.selectedToAccountId,
            onSelected = { 
                state.selectedToAccountId = it
                onToOnAccountChange(false)
                state.toAccountError = null 
            },
            onOnAccountSelected = {
                onToOnAccountChange(true)
                state.selectedToAccountId = null
                state.toAccountError = null
            },
            isOnAccountSelected = isToOnAccountSelected,
            hasOnAccountOption = onAccountMicroAccounts.isNotEmpty(),
            enabled = !readOnly,
            onAdd = { onNavigate?.invoke("add_category") },
            isError = state.toAccountError != null
        )
        state.toAccountError?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.labelSmall) }
    } else {
        AccountSelectionDialog(
            label = stringResource(R.string.label_account),
            accounts = accounts,
            balances = balances,
            majorHeads = majorHeads,
            minorHeads = minorHeads,
            viewModel = viewModel,
            selectedId = state.selectedAccountId,
            onSelected = { 
                state.selectedAccountId = it
                onFromOnAccountChange(false)
                state.accountError = null 
            },
            onOnAccountSelected = {
                onFromOnAccountChange(true)
                state.selectedAccountId = null
                state.accountError = null
            },
            isOnAccountSelected = isFromOnAccountSelected,
            hasOnAccountOption = onAccountMicroAccounts.isNotEmpty(),
            enabled = !readOnly,
            onAdd = { onNavigate?.invoke("add_category") },
            isError = state.accountError != null
        )
        state.accountError?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.labelSmall) }
    }

    if (isFromOnAccountSelected) {
        Spacer(Modifier.height(8.dp))
        PartySelectionDialog(
            label = if (state.type == "transfer") stringResource(R.string.label_from_party) else if (state.type == "income") stringResource(R.string.label_payer) else stringResource(R.string.label_party_name),
            parties = onAccountMicroAccounts.map { Party(it.id, it.name, it.openingBalance) },
            selectedId = state.selectedAccountId,
            onSelected = { id -> 
                state.selectedAccountId = id
                state.selectedPartyId = id
                if (viewModel.invoiceAgeTrackingEnabled) {
                    val acc = onAccountMicroAccounts.find { it.id == id }
                    acc?.defaultDueDays?.let { state.dueDays = it.toString() }
                }
            },
            enabled = !readOnly,
            onAdd = { onNavigate?.invoke("add_category") },
            displayValue = if (readOnly) displayPartyName else null
        )
    }

    if (state.type == "transfer" && isToOnAccountSelected) {
        Spacer(Modifier.height(8.dp))
        PartySelectionDialog(
            label = stringResource(R.string.label_to_party),
            parties = onAccountMicroAccounts.map { Party(it.id, it.name, it.openingBalance) },
            selectedId = state.selectedToAccountId,
            onSelected = { id -> 
                state.selectedToAccountId = id
                state.selectedToPartyId = id
                if (viewModel.invoiceAgeTrackingEnabled) {
                    val acc = onAccountMicroAccounts.find { it.id == id }
                    acc?.defaultDueDays?.let { state.dueDays = it.toString() }
                }
            },
            enabled = !readOnly,
            onAdd = { onNavigate?.invoke("add_category") },
            displayValue = if (readOnly) displayToPartyName else null
        )
    }

    val context = LocalContext.current

    if (state.type == "transfer") {
        val selectedToAccount = accounts.find { it.id == state.selectedToAccountId }
        val selectedToMinor = minorHeads.find { it.id == selectedToAccount?.minorHeadId }
        val isToFdAccount = selectedToAccount != null && ((selectedToMinor?.name?.contains("Fixed Deposit", ignoreCase = true) == true) || selectedToAccount.name.contains("Fixed Deposit", ignoreCase = true))

        if (isToFdAccount) {
            Spacer(Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Fixed Deposit Details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = state.fdLast4,
                        onValueChange = { state.fdLast4 = it },
                        label = { Text("Last 4 Digits of FD No.") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = CircleShape,
                        enabled = !readOnly
                    )
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = !readOnly) {
                        val today = LocalDate.now()
                        val current = try { LocalDate.parse(state.fdMaturityDate) } catch (e: Exception) { today }
                        DatePickerDialog(context, { _, year, month, dayOfMonth ->
                            state.fdMaturityDate = String.format("%d-%02d-%02d", year, month + 1, dayOfMonth)
                        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
                    }) {
                        OutlinedTextField(
                            value = state.fdMaturityDate,
                            onValueChange = {},
                            label = { Text("FD Maturity Date (YYYY-MM-DD)") },
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = CircleShape,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        val selectedFromAccount = accounts.find { it.id == state.selectedAccountId }
        val selectedFromMinor = minorHeads.find { it.id == selectedFromAccount?.minorHeadId }
        val isFromFdAccount = selectedFromAccount != null && ((selectedFromMinor?.name?.contains("Fixed Deposit", ignoreCase = true) == true) || selectedFromAccount.name.contains("Fixed Deposit", ignoreCase = true))

        if (isFromFdAccount) {
            Spacer(Modifier.height(8.dp))
            val activeFds by viewModel.getAllActiveFdsForAccount(selectedFromAccount!!.id).collectAsState(initial = emptyList())
            val selectedFd = activeFds.find { it.creationHeaderId == state.selectedFdCreationHeaderId }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("FD Redemption / Withdrawal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { state.showFdSelectionDialog = true },
                        enabled = !readOnly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (selectedFd != null)
                                "Redeeming FD: ${selectedFd.fdLast4 ?: "----"} (Matures: ${selectedFd.maturityDate ?: "N/A"}, Rem: ${viewModel.formatAmount(selectedFd.outstandingAmount)})"
                            else "Select FD to Redeem/Withdraw"
                        )
                    }
                }
            }

            if (state.showFdSelectionDialog) {
                AlertDialog(
                    onDismissRequest = { state.showFdSelectionDialog = false },
                    title = { Text("Select FD to Redeem/Withdraw") },
                    text = {
                        if (activeFds.isEmpty()) {
                            Text("No active Fixed Deposits found in this account.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(activeFds) { fd ->
                                    Card(
                                        onClick = {
                                            state.selectedFdCreationHeaderId = fd.creationHeaderId
                                            state.showFdSelectionDialog = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("FD A/c Last 4: ${fd.fdLast4 ?: "----"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(4.dp))
                                            Text("Maturity: ${fd.maturityDate ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                                            Text("Outstanding: ${viewModel.formatAmount(fd.outstandingAmount)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { state.showFdSelectionDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MultiEntrySection(
    state: AddTransactionState,
    viewModel: ExpenseViewModel,
    categories: List<com.openapps.fintrack.data.Category>,
    accounts: List<com.openapps.fintrack.data.Account>,
    balances: List<com.openapps.fintrack.data.AccountBalance>,
    majorHeads: List<com.openapps.fintrack.data.MajorHead>,
    minorHeads: List<com.openapps.fintrack.data.MinorHead>,
    readOnly: Boolean,
    onNavigate: ((String) -> Unit)?
) {
    AnimatedVisibility(
        visible = state.isMultiEntry,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Text(if (state.multiEntryType == "Account") stringResource(R.string.label_accounts_amounts) else stringResource(R.string.label_categories_amounts), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            state.multiEntryRows.forEachIndexed { index, row ->
                key(row.id) {
                    MultiEntryRowItem(state, index, row, categories, accounts, balances, majorHeads, minorHeads, readOnly, onNavigate, viewModel)
                }
            }
            state.categoryError?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.labelSmall) }
            state.accountError?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.labelSmall) }
            if (!readOnly) {
                TextButton(onClick = { state.multiEntryRows.add(MultiEntryRow(categoryId = if (state.multiEntryType == "Account") state.selectedCategoryId else null, accountId = if (state.multiEntryType == "Category") state.selectedAccountId else null, amount = "", currencyCode = viewModel.baseCurrency)) }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.Add, null)
                    Text(stringResource(R.string.btn_add_row))
                }
            }
        }
    }

    AnimatedVisibility(
        visible = !state.isMultiEntry,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        CategorySelectionDialog(
            label = stringResource(R.string.label_category),
            categories = categories,
            selectedId = state.selectedCategoryId,
            onSelected = { state.selectedCategoryId = it; state.categoryError = null },
            enabled = !readOnly,
            onAdd = { onNavigate?.invoke("add_category") },
            isError = state.categoryError != null
        )
        state.categoryError?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
fun MultiEntryRowItem(
    state: AddTransactionState,
    index: Int,
    row: MultiEntryRow,
    categories: List<com.openapps.fintrack.data.Category>,
    accounts: List<com.openapps.fintrack.data.Account>,
    balances: List<com.openapps.fintrack.data.AccountBalance>,
    majorHeads: List<com.openapps.fintrack.data.MajorHead>,
    minorHeads: List<com.openapps.fintrack.data.MinorHead>,
    readOnly: Boolean,
    onNavigate: ((String) -> Unit)?,
    viewModel: ExpenseViewModel
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.small).padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(0.55f)) {
                if (state.multiEntryType == "Account") {
                    AccountSelectionDialog(
                        label = stringResource(R.string.label_account),
                        accounts = accounts,
                        balances = balances,
                        majorHeads = majorHeads,
                        minorHeads = minorHeads,
                        viewModel = viewModel,
                        selectedId = row.accountId,
                        onSelected = { 
                            state.multiEntryRows[index] = row.copy(accountId = it)
                            state.accountError = null
                        },
                        onOnAccountSelected = {}, 
                        isOnAccountSelected = false,
                        hasOnAccountOption = false,
                        enabled = !readOnly,
                        onAdd = { onNavigate?.invoke("add_category") },
                        isError = state.accountError != null && row.accountId == null
                    )
                } else {
                    CategorySelectionDialog(
                        label = stringResource(R.string.label_category),
                        categories = categories,
                        selectedId = row.categoryId,
                        onSelected = { 
                            state.multiEntryRows[index] = row.copy(categoryId = it) 
                            state.categoryError = null
                        },
                        enabled = !readOnly,
                        onAdd = { onNavigate?.invoke("add_category") },
                        isError = state.categoryError != null && row.categoryId == null
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = row.amount,
                onValueChange = { 
                    state.multiEntryRows[index] = row.copy(amount = it)
                    val total = state.multiEntryRows.sumOf { r -> evaluateExpression(r.amount) }
                    val totalStr = total.toString()
                    state.amount = totalStr
                    if (viewModel.enableMultiCurrency && state.type != "transfer") {
                        state.amountForeign = totalStr
                        state.isManualLocalAmount = false
                    }
                    state.amountError = null
                },
                label = { Text(stringResource(R.string.label_amount)) },
                modifier = Modifier.weight(0.35f),
                readOnly = readOnly,
                shape = CircleShape,
                isError = (state.categoryError != null || state.accountError != null) && row.amount.isBlank(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            if (!readOnly) {
                IconButton(onClick = { 
                    if (state.multiEntryRows.size > 1) {
                        state.multiEntryRows.removeAt(index)
                        val total = state.multiEntryRows.sumOf { r -> evaluateExpression(r.amount) }
                        val totalStr = total.toString()
                        state.amount = totalStr
                        if (viewModel.enableMultiCurrency && state.type != "transfer") {
                            state.amountForeign = totalStr
                            state.isManualLocalAmount = false
                        }
                    }
                }, modifier = Modifier.size(24.dp).padding(start = 4.dp)) {
                    Icon(Icons.Default.RemoveCircle, stringResource(R.string.btn_delete), tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = row.note ?: "",
                onValueChange = { newNote ->
                    state.multiEntryRows[index] = row.copy(note = newNote.ifBlank { null })
                },
                label = { Text(stringResource(R.string.label_note)) },
                modifier = Modifier.weight(0.55f),
                readOnly = readOnly,
                shape = CircleShape,
                singleLine = true
            )

            Spacer(Modifier.width(8.dp))

            val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
            val rowTagIds = remember(row.tags) {
                row.tags?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.toMutableStateList() ?: mutableStateListOf()
            }

            LaunchedEffect(rowTagIds.toList()) {
                val newTagsStr = if (rowTagIds.isEmpty()) null else rowTagIds.joinToString(",")
                if (row.tags != newTagsStr) {
                    state.multiEntryRows[index] = row.copy(tags = newTagsStr)
                }
            }

            Box(modifier = Modifier.weight(0.45f)) {
                TagSelectionPopup(
                    allTags = allTags,
                    selectedIds = rowTagIds,
                    multiSelect = viewModel.multiTagEnabled,
                    enabled = !readOnly,
                    onAdd = { onNavigate?.invoke("add_tag") }
                )
            }
        }
    }
}

@Composable 
fun AmountAndCurrencySection(state: AddTransactionState, viewModel: ExpenseViewModel, readOnly: Boolean, hideCurrencyPicker: Boolean = false) {
    if (viewModel.enableMultiCurrency && state.type != "transfer") {
        MultiCurrencyAmountSection(
            viewModel = viewModel,
            foreignCurrency = state.foreignCurrency,
            amountForeign = state.amountForeign,
            amountLocal = state.amountLocal,
            currentRate = state.currentRate,
            manualRateInput = state.manualRateInput,
            readOnly = readOnly,
            hideCurrencyPicker = hideCurrencyPicker,
            onForeignCurrencyChange = { state.foreignCurrency = it },
            onAmountForeignChange = { 
                state.amountForeign = it
                state.amount = it 
                state.amountError = null
                state.isManualLocalAmount = false
            },
            onAmountLocalChange = { 
                state.amountLocal = it
                state.isManualLocalAmount = true
            },
            onManualRateChange = { input ->
                state.manualRateInput = input
                input.toDoubleOrNull()?.let { rate ->
                    state.currentRate = rate
                    val foreignVal = evaluateExpression(state.amountForeign)
                    if (foreignVal > 0) {
                        state.amountLocal = String.format(Locale.US, "%.2f", foreignVal * rate)
                    }
                }
            },
            showCalculator = state.showCalculator,
            onToggleCalculator = { state.showCalculator = !state.showCalculator }
        )
    } else {
        OutlinedTextField(
            value = state.amount,
            onValueChange = { if (!readOnly) { state.amount = it; state.amountError = null } },
            label = { Text(stringResource(R.string.label_total_amount)) },
            readOnly = readOnly,
            isError = state.amountError != null,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent
            ),
            trailingIcon = {
                if (!readOnly) {
                    IconButton(onClick = { state.showCalculator = !state.showCalculator }) {
                        Icon(Icons.Default.Calculate, stringResource(R.string.btn_calculator))
                    }
                }
            }
        )
    }
    state.amountError?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.labelSmall) }
}

@Composable
fun SubscriptionSection(state: AddTransactionState, viewModel: ExpenseViewModel, allTransactions: List<TransactionWithDetails>, readOnly: Boolean) {
    val masterSubscriptions by viewModel.getAllSubscriptionsMaster().collectAsState(initial = emptyList())
    val activeLoans by viewModel.activeLoans.collectAsState(initial = emptyList())
    val subscriptionStatuses by viewModel.getAllSubscriptionStatuses().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Checkbox(checked = state.isSubscription, onCheckedChange = { if(!readOnly) state.isSubscription = it }, enabled = !readOnly)
        Text(
            if (state.type == "transfer") stringResource(R.string.label_recurring_sip_tip) else stringResource(R.string.label_is_subscription), 
            style = MaterialTheme.typography.bodyMedium, 
            modifier = Modifier.clickable { if(!readOnly) state.isSubscription = !state.isSubscription }
        )
    }
    
    if (state.isSubscription) {
        val existingNames = remember(allTransactions, masterSubscriptions, state.type) {
            val fromTxns = allTransactions.filter { it.transaction.subName != null }
                .filter { (state.type == "transfer") == (it.transaction.categoryId == null && it.transaction.toAccountId != null) }
                .map { it.transaction.subName!! }
            val fromMaster = masterSubscriptions.filter { it.isTransfer == (state.type == "transfer") }.map { it.name }
            (fromTxns + fromMaster).distinct()
                .filter { name -> subscriptionStatuses.find { it.subName == name }?.isStopped != true }
        }
        var subExpanded by remember { mutableStateOf(false) }
        
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            val displaySubName = if (state.subName.startsWith("LOAN:")) {
                val id = state.subName.removePrefix("LOAN:").toLongOrNull()
                "LOAN: " + (activeLoans.find { it.id == id }?.name ?: state.subName)
            } else state.subName

            OutlinedTextField(
                value = displaySubName,
                onValueChange = { if(!readOnly) { state.subName = it; state.isExistingSubscription = false } },
                label = { Text(if (state.type == "transfer") stringResource(R.string.label_select_recurring_loan) else stringResource(R.string.label_select_subscription)) },
                readOnly = false,
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                trailingIcon = { if (!readOnly) IconButton(onClick = { subExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } }
            )
            DropdownMenu(expanded = subExpanded, onDismissRequest = { subExpanded = false }, modifier = Modifier.fillMaxWidth(0.9f) ) {
                if (existingNames.isEmpty() && activeLoans.isEmpty()) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.msg_no_items_found)) }, onClick = { subExpanded = false }, enabled = false)
                }
                existingNames.forEach { name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = {
                        state.subName = name
                        state.isExistingSubscription = true
                        subExpanded = false
                        
                        // Auto-fill logic
                        scope.launch {
                            val lastTxn = viewModel.getLastTransactionForSubscription(name)
                            if (lastTxn != null) {
                                state.selectedAccountId = lastTxn.transaction.accountId
                                state.selectedToAccountId = lastTxn.transaction.toAccountId
                                state.selectedCategoryId = lastTxn.transaction.categoryId
                                state.amount = lastTxn.transaction.amount.toString()
                                state.amountForeign = (lastTxn.transaction.amountOriginal ?: lastTxn.transaction.amount).toString()
                                state.foreignCurrency = lastTxn.transaction.currencyCode ?: viewModel.baseCurrency
                                state.subFrequency = lastTxn.transaction.subFrequency?.toString() ?: ""
                                
                                val siblings = allTransactions.filter { 
                                    it.transaction.date == lastTxn.transaction.date && 
                                    it.transaction.time == lastTxn.transaction.time && 
                                    it.transaction.accountId == lastTxn.transaction.accountId &&
                                    it.transaction.transactionNumber?.startsWith("M") == true
                                }
                                
                                if (siblings.size > 1) {
                                    state.isMultiEntry = true
                                    state.multiEntryRows.clear()
                                    siblings.forEach { s ->
                                        state.multiEntryRows.add(MultiEntryRow(
                                            categoryId = s.transaction.categoryId,
                                            amount = s.transaction.amountOriginal?.toString() ?: s.transaction.amount.toString(),
                                            note = s.transaction.note,
                                            currencyCode = s.transaction.currencyCode
                                        ))
                                    }
                                } else {
                                    state.isMultiEntry = false
                                }
                            } else {
                                
                                masterSubscriptions.find { it.name == name }?.let { 
                                    state.subFrequency = it.frequency.toString()
                                }
                            }
                        }
                    })
                }
                if (state.type == "transfer") {
                    activeLoans.forEach { loan ->
                        DropdownMenuItem(text = { Text("LOAN: ${loan.name}") }, onClick = {
                            state.subName = "LOAN:${loan.id}"
                            state.isExistingSubscription = true
                            state.subFrequency = when(loan.frequency) {
                                "MONTHLY" -> "1"
                                "QUARTERLY" -> "3"
                                "HALF_YEARLY" -> "6"
                                "YEARLY" -> "12"
                                else -> "1"
                            }
                            state.selectedToAccountId = loan.accountId
                            state.selectedToPartyId = loan.partyId
                            state.amount = loan.installmentAmount.toString()
                            state.amountForeign = loan.installmentAmount.toString()
                            state.isMultiEntry = false
                            subExpanded = false
                        })
                    }
                }
            }
        }
        
        if (!state.isExistingSubscription) {
             OutlinedTextField(
                value = state.subFrequency, 
                onValueChange = { if(!readOnly) state.subFrequency = it }, 
                label = { Text(stringResource(R.string.label_frequency_months)) }, 
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), 
                readOnly = readOnly,
                shape = CircleShape
            )
        }
    }
}

@Composable
fun ActionButtons(
    state: AddTransactionState,
    viewModel: ExpenseViewModel,
    isTemplateMode: Boolean,
    templateName: String,
    selectedTagIds: List<Int>,
    allTransactions: List<TransactionWithDetails>,
    onBack: () -> Unit,
    onShowNameDialog: () -> Unit,
    onShowTemplateSelection: () -> Unit,
    updateId: Int? = null,
    onTransactionSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (isTemplateMode) {
            Button(onClick = {
                val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                val multiStr = if (state.isMultiEntry) {
                    state.multiEntryRows.joinToString("|") { "${it.categoryId}:${it.amount}:${it.note ?: ""}:${it.currencyCode ?: ""}" }
                } else null
                val templateToSave = TemplateLegacy(
                    name = templateName, 
                    type = state.type, 
                    accountId = state.selectedAccountId, 
                    toAccountId = state.selectedToAccountId, 
                    categoryId = state.selectedCategoryId, 
                    amount = evaluateExpression(state.amount).takeIf { it > 0 }, 
                    note = state.note, 
                    tags = tagsString, 
                    multiEntries = multiStr,
                    subName = if (state.isSubscription) state.subName else null,
                    subFrequency = if (state.isSubscription) state.subFrequency.toIntOrNull() else null
                )
                if (templateName.isNotBlank()) {
                    scope.launch {
                        if (viewModel.saveTemplate(templateToSave)) {
                            Toast.makeText(context, context.getString(R.string.msg_template_saved), Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, context.getString(R.string.msg_template_exists), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_save_template)) }
        } else {
            OutlinedButton(onClick = onShowNameDialog, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text(stringResource(R.string.btn_save_as_template)) }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShowTemplateSelection, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.btn_use_template)) }
                Button(onClick = {
                    if (validateAndSave(state, viewModel, selectedTagIds, allTransactions, onTransactionSaved, context, updateId, isTemplateMode)) {
                        
                    }
                }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.btn_save)) }
            }
        }
    }
}

fun validateAndSave(
    state: AddTransactionState, 
    viewModel: ExpenseViewModel, 
    selectedTagIds: List<Int>, 
    allTransactions: List<TransactionWithDetails>, 
    onSuccess: () -> Unit, 
    context: android.content.Context,
    updateId: Int? = null,
    isTemplateMode: Boolean = false
): Boolean {
    var hasError = false
    val amtToCheck = if (viewModel.enableMultiCurrency && state.type != "transfer") state.amountForeign else state.amount
    val isMultiEntryToUse = state.isMultiEntry && state.type != "transfer"
    val isMultiAccount = isMultiEntryToUse && state.multiEntryType == "Account"

    if (evaluateExpression(amtToCheck) <= 0.0) { state.amountError = context.getString(R.string.err_total_amount_required); hasError = true }
    if (state.selectedAccountId == null && !isMultiAccount) { state.accountError = context.getString(R.string.err_primary_account_required); hasError = true }
    if (state.type == "transfer" && state.selectedToAccountId == null) { state.toAccountError = context.getString(R.string.err_destination_account_required); hasError = true }
    
    if (!isMultiEntryToUse && state.type != "transfer" && state.selectedCategoryId == null) { state.categoryError = context.getString(R.string.err_category_required); hasError = true }
    if (isMultiEntryToUse && state.multiEntryType == "Category" && state.multiEntryRows.any { it.categoryId == null || evaluateExpression(it.amount) <= 0.0 }) { state.categoryError = context.getString(R.string.err_row_category_amount); hasError = true }
    if (isMultiAccount && state.multiEntryRows.any { it.accountId == null || evaluateExpression(it.amount) <= 0.0 }) { state.accountError = context.getString(R.string.err_row_account_amount); hasError = true }
    if (isMultiAccount && state.selectedCategoryId == null) { state.categoryError = context.getString(R.string.err_category_required_top); hasError = true }

    if (state.isSubscription && state.subName.isBlank()) {
        Toast.makeText(context, context.getString(R.string.msg_enter_sub_name), Toast.LENGTH_SHORT).show()
        return false
    }

    if (hasError) {
        Toast.makeText(context, context.getString(R.string.msg_fill_required), Toast.LENGTH_SHORT).show()
        return false
    }

    if (isTemplateMode) {
        // Added as safety - This shouldn't be reached if UI logic is correct.
        Toast.makeText(context, context.getString(R.string.msg_cannot_save_template_mode), Toast.LENGTH_SHORT).show()
        return false
    }

    val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
    
    if (isMultiEntryToUse) {
        val totalEntered = evaluateExpression(if (viewModel.enableMultiCurrency && state.type != "transfer") state.amountForeign else state.amount)
        val rowsSum = state.multiEntryRows.sumOf { evaluateExpression(it.amount) }
        if (Math.abs(totalEntered - rowsSum) > 0.01) {
            state.mismatchTotals = Pair(totalEntered, rowsSum)
            state.showMismatchDialog = true
            return false
        }

        fun combineNotes(rowNote: String?, commonNote: String): String? {
            val r = rowNote?.trim().orEmpty()
            val c = commonNote.trim()
            return when {
                r.isNotEmpty() && c.isNotEmpty() -> "$r / $c"
                r.isNotEmpty() -> r
                c.isNotEmpty() -> c
                else -> null
            }
        }

        if (state.multiEntryType == "Account") {
            // Multi-account mode
            val entries = state.multiEntryRows.map { r ->
                val rowTags = if (!r.tags.isNullOrBlank()) r.tags else tagsString
                com.openapps.fintrack.ui.MultiEntryRowData(
                    state.selectedCategoryId!!,
                    r.accountId,
                    evaluateExpression(r.amount),
                    combineNotes(r.note, state.note),
                    state.foreignCurrency,
                    rowTags
                )
            }
            viewModel.addMultiEntryTransactionExtended(state.date, state.time, 0, entries, tagsString, state.type, state.selectedPartyId, state.subName, state.subFrequency.toIntOrNull(), updateId, state.attachments.toList())
        } else {
            // Multi-category mode
            val entries = state.multiEntryRows.map { r ->
                val rowTags = if (!r.tags.isNullOrBlank()) r.tags else tagsString
                com.openapps.fintrack.ui.MultiEntryRowData(
                    r.categoryId!!,
                    state.selectedAccountId,
                    evaluateExpression(r.amount),
                    combineNotes(r.note, state.note),
                    state.foreignCurrency,
                    rowTags
                )
            }
            viewModel.addMultiEntryTransactionExtended(state.date, state.time, state.selectedAccountId ?: 0, entries, tagsString, state.type, state.selectedPartyId, state.subName, state.subFrequency.toIntOrNull(), updateId, state.attachments.toList())
        }
        
        viewModel.currentRecordingImportTxnKey?.let { key ->
            viewModel.pendingTransactions = viewModel.pendingTransactions.filter { 
                (it.description + it.date.toString() + it.amount.toString()) != key 
            }
            viewModel.currentRecordingImportTxnKey = null
        }

        viewModel.draftTransaction = null
        onSuccess()
    } else {
        val amtOriginal = evaluateExpression(state.amountForeign)
        val amtBase = evaluateExpression(state.amountLocal)
        val subNameVal = if (state.isSubscription) state.subName else null
        val subFreqVal = if (state.isSubscription) state.subFrequency.toIntOrNull() else null

        if (state.isSubscription && !state.isExistingSubscription && subNameVal != null && updateId == null) {
            val exists = allTransactions.any { 
                it.transaction.subName?.equals(subNameVal, ignoreCase = true) == true &&
                (state.type == "transfer") == (it.transaction.categoryId == null && it.transaction.toAccountId != null)
            }
            if (exists) {
                Toast.makeText(context, context.getString(R.string.msg_sub_name_exists), Toast.LENGTH_LONG).show()
                return false
            }
        }

        if (state.type == "transfer") {
            viewModel.addTransaction(
                state.date, state.time, state.selectedAccountId!!, null, amtBase, state.note, state.selectedToAccountId, tagsString, state.type, state.selectedPartyId, state.selectedToPartyId, subNameVal, subFreqVal, updateId = updateId, isNegotiated = state.isNegotiated, negotiationAmountOriginal = state.negotiationAmountOriginal.toDoubleOrNull(), merchantName = state.merchantName, isDiscretionary = state.isDiscretionary, clearInvoiceIds = state.selectedInvoiceIds.toList(), goalId = if (state.isForGoal) state.selectedGoalId else null,
                fdLast4 = state.fdLast4.ifBlank { null },
                fdMaturityDate = state.fdMaturityDate.ifBlank { null },
                selectedFdCreationHeaderId = state.selectedFdCreationHeaderId,
                attachments = state.attachments.toList()
            )
            
            if (subNameVal?.startsWith("LOAN:") == true && updateId == null) {
                val loanId = subNameVal.removePrefix("LOAN:").toLongOrNull()
                if (loanId != null) {
                    viewModel.processLoanRepayment(loanId, amtOriginal)
                }
            }
        } else {
            viewModel.addTransaction(state.date, state.time, state.selectedAccountId!!, state.selectedCategoryId, amtBase, state.note, null, tagsString, state.type, state.selectedPartyId, null, subNameVal, subFreqVal, amtOriginal, state.foreignCurrency, amtBase, updateId = updateId, isNegotiated = state.isNegotiated, negotiationAmountOriginal = state.negotiationAmountOriginal.toDoubleOrNull(), merchantName = state.merchantName, isDiscretionary = state.isDiscretionary, invoiceNumber = if (viewModel.invoiceAgeTrackingEnabled) state.invoiceNumber else null, dueDays = if (viewModel.invoiceAgeTrackingEnabled) state.dueDays.toIntOrNull() else null, goalId = if (state.isForGoal) state.selectedGoalId else null, attachments = state.attachments.toList())
        }

        if (state.isForGoal && state.selectedGoalId != null) {
            val targetAccId = if (state.type == "transfer") state.selectedToAccountId ?: state.selectedAccountId else state.selectedAccountId
            if (targetAccId != null) {
                val delta = if (state.type == "expense") -amtBase else amtBase
                viewModel.adjustGoalAllocation(state.selectedGoalId!!, targetAccId, delta)
            }
        }

        viewModel.currentRecordingImportTxnKey?.let { key ->
            viewModel.pendingTransactions = viewModel.pendingTransactions.filter { 
                (it.description + it.date.toString() + it.amount.toString()) != key 
            }
            viewModel.currentRecordingImportTxnKey = null
        }

        viewModel.draftTransaction = null
        onSuccess()
    }
    return true
}

@Composable
fun TemplateNameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var inputName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.label_template_name)) },
        text = { OutlinedTextField(value = inputName, onValueChange = { inputName = it }, label = { Text(stringResource(R.string.label_name)) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { if (inputName.isNotBlank()) onSave(inputName) }) { Text(stringResource(R.string.btn_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun TemplateSelectionDialog(templates: List<TemplateLegacy>, onDismiss: () -> Unit, onNavigate: ((String) -> Unit)?, onSelected: (TemplateLegacy) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_templates)) },
        text = {
            if (templates.isEmpty()) Text(stringResource(R.string.msg_no_templates))
            else Box(modifier = Modifier.height(300.dp)) {
                LazyColumn {
                    items(templates) { t ->
                        ListItem(headlineContent = { Text(t.name) }, modifier = Modifier.clickable { onSelected(t) })
                        Divider()
                    }
                }
            }
        },
        confirmButton = { if (templates.isEmpty()) Button(onClick = { onDismiss(); onNavigate?.invoke("templates") }) { Text(stringResource(R.string.menu_templates)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun MultiEntrySummaryCard(
    viewModel: ExpenseViewModel,
    multiEntryRows: List<MultiEntryRow>,
    amountLocal: String,
    readOnly: Boolean,
    onAmountLocalChange: (String) -> Unit
) {
    val exchangeRates by viewModel.getExchangeRates().collectAsState(initial = emptyList())
    
    val totalsByCurrency = remember(multiEntryRows) {
        multiEntryRows.groupBy { it.currencyCode ?: viewModel.baseCurrency }
            .mapValues { entry -> entry.value.sumOf { evaluateExpression(it.amount) } }
    }

    var computedTotalBase by remember { mutableStateOf(0.0) }

    LaunchedEffect(totalsByCurrency, exchangeRates) {
        var total = 0.0
        totalsByCurrency.forEach { (code, amount) ->
            if (code == viewModel.baseCurrency) {
                total += amount
            } else {
                val rate = exchangeRates.find { it.currencyCode == code }?.rateToBase ?: 1.0
                total += amount * rate
            }
        }
        computedTotalBase = total
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.label_multi_entry_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            
            totalsByCurrency.forEach { (code, amount) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.label_total_code_colon, code), style = MaterialTheme.typography.bodyMedium)
                    Text(String.format(Locale.US, "%.2f", amount), fontWeight = FontWeight.Bold)
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            OutlinedTextField(
                value = amountLocal,
                onValueChange = { if (!readOnly) onAmountLocalChange(it) },
                label = { Text(stringResource(R.string.label_total_amount_code, viewModel.baseCurrency)) },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text(stringResource(R.string.label_sum_converted_colon, String.format(Locale.US, "%.2f", computedTotalBase))) },
                trailingIcon = { Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

@Composable
fun MultiCurrencyAmountSection(
    viewModel: ExpenseViewModel,
    foreignCurrency: String,
    amountForeign: String,
    amountLocal: String,
    currentRate: Double,
    manualRateInput: String,
    readOnly: Boolean,
    onForeignCurrencyChange: (String) -> Unit,
    onAmountForeignChange: (String) -> Unit,
    onAmountLocalChange: (String) -> Unit,
    onManualRateChange: (String) -> Unit,
    showCalculator: Boolean,
    onToggleCalculator: () -> Unit,
    hideCurrencyPicker: Boolean = false
) {
    var showCurrencyDialog by remember { mutableStateOf(false) }
    val exchangeRates by viewModel.getExchangeRates().collectAsState(initial = emptyList())
    val rateObj = exchangeRates.find { it.currencyCode == foreignCurrency }
    val isStale = rateObj?.let { (System.currentTimeMillis() - it.updatedAt) > 7 * 24 * 60 * 60 * 1000L } ?: false

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!hideCurrencyPicker) {
                Box(modifier = Modifier.weight(0.3f).clickable(enabled = !readOnly) { showCurrencyDialog = true }) {
                    OutlinedTextField(
                        value = foreignCurrency,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_curr)) },
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        trailingIcon = { if (!readOnly) Icon(Icons.Default.ArrowDropDown, null) }
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            OutlinedTextField(
                value = amountForeign,
                onValueChange = { if (!readOnly) onAmountForeignChange(it) },
                label = { Text(if (hideCurrencyPicker) stringResource(R.string.label_total_amount_code, foreignCurrency) else stringResource(R.string.label_amount_foreign)) },
                readOnly = readOnly,
                modifier = Modifier.weight(if (hideCurrencyPicker) 1f else 0.7f).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                ),
                trailingIcon = {
                    if (!readOnly) {
                        IconButton(onClick = onToggleCalculator) {
                            Icon(Icons.Default.Calculate, stringResource(R.string.btn_calculator))
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = manualRateInput,
            onValueChange = { if (!readOnly) onManualRateChange(it) },
            label = { Text(stringResource(R.string.label_conversion_rate_tip, foreignCurrency, viewModel.baseCurrency)) },
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            leadingIcon = { Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
            supportingText = {
                if (isStale) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, stringResource(R.string.label_stale_rate), tint = Color.Red, modifier = Modifier.size(12.dp))
                        Text(stringResource(R.string.msg_rate_stale_desc), style = MaterialTheme.typography.labelSmall, color = Color.Red)
                    }
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = amountLocal,
            onValueChange = { if (!readOnly) onAmountLocalChange(it) },
            label = { Text(stringResource(R.string.label_amount_local_code, viewModel.baseCurrency)) },
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            leadingIcon = { Icon(Icons.Default.LockOpen, null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        )
    }

    if (showCurrencyDialog) {
        CurrencySelectionDialog(
            onDismiss = { showCurrencyDialog = false },
            onSelected = { 
                onForeignCurrencyChange(it)
                showCurrencyDialog = false
            }
        )
    }
}

@Composable
fun CurrencySelectionDialog(onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val currencies = remember { java.util.Currency.getAvailableCurrencies().map { it.currencyCode }.sorted() }
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery) {
        if (searchQuery.isBlank()) currencies
        else currencies.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.title_select_currency)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.label_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape
                )
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.height(300.dp)) {
                    LazyColumn {
                        items(filtered) { code ->
                            val currency = java.util.Currency.getInstance(code)
                            ListItem(
                                headlineContent = { Text("$code - ${currency.getDisplayName(Locale.getDefault())}") },
                                modifier = Modifier.clickable { onSelected(code) }
                            )
                            Divider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun AccountSelectionDialog(
    label: String, 
    accounts: List<com.openapps.fintrack.data.Account>, 
    balances: List<com.openapps.fintrack.data.AccountBalance>,
    majorHeads: List<com.openapps.fintrack.data.MajorHead>,
    minorHeads: List<com.openapps.fintrack.data.MinorHead>,
    viewModel: ExpenseViewModel,
    selectedId: Int?, 
    onSelected: (Int) -> Unit, 
    onOnAccountSelected: () -> Unit,
    isOnAccountSelected: Boolean,
    hasOnAccountOption: Boolean,
    enabled: Boolean = true, 
    onAdd: () -> Unit, 
    isError: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectAccountLabel = stringResource(R.string.label_select_account_analysis)
    val selectedName = if (isOnAccountSelected) "👤 " + stringResource(R.string.label_on_account_loan) else {
        val acc = accounts.find { it.id == selectedId }
        if (acc != null) (acc.icon ?: "🏦") + " " + acc.name else selectAccountLabel
    }

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).clickable(enabled = enabled) { showDialog = true }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            isError = isError,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = Color.Transparent,
                disabledLabelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            trailingIcon = { if (enabled) Icon(Icons.Default.ArrowDropDown, "") }
        )
    }

    if (showDialog && enabled) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredAccounts = remember(searchQuery, accounts) {
            if (searchQuery.isBlank()) accounts
            else accounts.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Column {
                    Text(label)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.label_search)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        }
                    )
                }
            },
            text = {
                Box(Modifier.height(350.dp)) {
                    LazyColumn {
                        if (hasOnAccountOption && searchQuery.isBlank()) {
                            item {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.label_on_account_loan)) },
                                    modifier = Modifier.clickable {
                                        onOnAccountSelected()
                                        showDialog = false
                                    }
                                )
                                Divider()
                            }
                        }
                        items(filteredAccounts) { account ->
                            val bal = balances.find { it.id == account.id }?.balance ?: 0L
                            val minor = minorHeads.find { it.id == account.minorHeadId }
                            val major = majorHeads.find { it.id == minor?.majorHeadId }
                            
                            ListItem(
                                headlineContent = { Text((account.icon ?: "🏦") + " " + account.name) },
                                supportingContent = { 
                                    Text("${major?.name ?: stringResource(R.string.label_other)} | ${viewModel.formatAmount(bal)}", color = if (bal >= 0) Color(0xFF4CAF50) else Color.Red)
                                },
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
            confirmButton = {
                Row {
                    Button(onClick = onAdd) {
                        Icon(Icons.Default.Add, null)
                        Text(stringResource(R.string.btn_add))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                }
            }
        )
    }
}

@Composable
fun PartySelectionDialog(label: String, parties: List<Party>, selectedId: Int?, onSelected: (Int) -> Unit, enabled: Boolean = true, onAdd: () -> Unit, displayValue: String? = null) {
    var showDialog by remember { mutableStateOf(false) }
    val selectLabel = stringResource(R.string.label_select_label, label)
    val selectedName = displayValue ?: parties.find { it.id == selectedId }?.name ?: selectLabel

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled) { showDialog = true }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            shape = CircleShape,
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
                        items(parties) { party ->
                            ListItem(
                                headlineContent = { Text(party.name) },
                                modifier = Modifier.clickable {
                                    onSelected(party.id)
                                    showDialog = false
                                }
                            )
                            Divider()
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    Button(onClick = {
                        showDialog = false
                        onAdd()
                    }) {
                        Icon(Icons.Default.Add, null)
                        Text(stringResource(R.string.btn_add_new))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                }
            }
        )
    }
}

@Composable
fun CategorySelectionDialog(label: String, categories: List<com.openapps.fintrack.data.Category>, selectedId: Int?, onSelected: (Int) -> Unit, enabled: Boolean = true, onAdd: () -> Unit, isError: Boolean = false) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selectedId }?.let { (it.icon ?: "📁") + " " + it.name } ?: stringResource(R.string.label_select)

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(16.dp)).clickable(enabled = enabled) { showDialog = true }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            isError = isError,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = Color.Transparent,
                disabledLabelColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            trailingIcon = { if (enabled) Icon(Icons.Default.ArrowDropDown, "") }
        )
    }

    if (showDialog && enabled) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredCategories = remember(searchQuery, categories) {
            if (searchQuery.isBlank()) categories
            else categories.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Column {
                    Text(label)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.label_search)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        }
                    )
                }
            },
            text = {
                Box(Modifier.height(400.dp)) {
                    LazyColumn {
                        items(filteredCategories) { category ->
                            ListItem(
                                headlineContent = { Text((category.icon ?: "📁") + " " + category.name) },
                                supportingContent = { Text(category.type.replaceFirstChar { it.uppercase() }) },
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
            confirmButton = {
                Row {
                    Button(onClick = onAdd) {
                        Icon(Icons.Default.Add, null)
                        Text(stringResource(R.string.btn_add))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                }
            }
        )
    }
}

@Composable
fun TagSelectionPopup(
    allTags: List<com.openapps.fintrack.data.Tag>,
    selectedIds: MutableList<Int>,
    multiSelect: Boolean,
    enabled: Boolean = true,
    onAdd: () -> Unit = {}
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedNames = remember(allTags, selectedIds.toList()) {
        allTags.filter { it.id in selectedIds }.map { it.name }.joinToString(", ")
    }
    val displayText = if (selectedNames.isEmpty()) stringResource(R.string.label_select_tags) else selectedNames

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled) { showDialog = true }) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            label = { Text(stringResource(R.string.label_tags)) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = false,
            shape = CircleShape,
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
            title = { Text(stringResource(R.string.label_select_tags)) },
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
                                        if (tag.id in selectedIds) {
                                            selectedIds.remove(tag.id)
                                        } else {
                                            selectedIds.add(tag.id)
                                        }
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
                Row {
                    Button(onClick = {
                        showDialog = false
                        onAdd()
                    }) {
                        Icon(Icons.Default.Add, null)
                        Text(stringResource(R.string.btn_add))
                    }
                    if (multiSelect) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showDialog = false }) { Text(stringResource(R.string.btn_done)) }
                    }
                }
            },
            dismissButton = {
                if (!multiSelect) {
                    TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
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
