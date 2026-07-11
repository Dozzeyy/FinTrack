/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openapps.fintrack.data.Template
import com.openapps.fintrack.data.Party
import com.openapps.fintrack.data.Transaction
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
    var categoryId: Int?,
    var amount: String,
    var note: String? = null,
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
    val multiEntryRows = mutableStateListOf<MultiEntryRow>()
    
    var isSubscription by mutableStateOf(false)
    var isExistingSubscription by mutableStateOf(false)
    var subName by mutableStateOf("")
    var subFrequency by mutableStateOf("")
    
    var amountError by mutableStateOf<String?>(null)
    var accountError by mutableStateOf<String?>(null)
    var toAccountError by mutableStateOf<String?>(null)
    var categoryError by mutableStateOf<String?>(null)
    
    var showCalculator by mutableStateOf(false)
    var showMismatchDialog by mutableStateOf(false)
    var mismatchTotals by mutableStateOf(Pair(0.0, 0.0))
}

@Composable
fun rememberAddTransactionState(
    viewModel: ExpenseViewModel,
    txnDetail: TransactionWithDetails?,
    draft: DraftTransaction?,
    editingTemplate: Template?,
    initialData: Bundle?
): AddTransactionState {
    val initialType = remember {
        draft?.type ?: editingTemplate?.type ?: 
        (if (txnDetail?.transaction?.toAccountId != null) "transfer" else txnDetail?.categoryType) ?: 
        "expense"
    }
    
    val initialAmount = remember {
        draft?.amount ?: editingTemplate?.amount?.toString() ?: 
        txnDetail?.transaction?.let { (it.amountOriginal ?: it.amount).toString() } ?: 
        initialData?.getDouble("amount", 0.0)?.takeIf { it > 0 }?.toString() ?: ""
    }
    
    val initialNote = remember {
        draft?.note ?: editingTemplate?.note ?: txnDetail?.transaction?.note ?: initialData?.getString("sms_body") ?: ""
    }

    val calendar = Calendar.getInstance()
    val initialDate = remember {
        draft?.date ?: txnDetail?.transaction?.date ?: initialData?.getString("date") ?:
        String.format("%d-%02d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH))
    }
    
    val initialTime = remember {
        draft?.time ?: txnDetail?.transaction?.time ?: initialData?.getString("time") ?:
        String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
    }

    val state = remember {
        AddTransactionState(
            initialType = initialType,
            initialAmount = initialAmount,
            initialNote = initialNote,
            initialDate = initialDate,
            initialTime = initialTime,
            initialAccountId = draft?.accountId ?: editingTemplate?.accountId ?: txnDetail?.transaction?.accountId,
            initialToAccountId = draft?.toAccountId ?: editingTemplate?.toAccountId ?: txnDetail?.transaction?.toAccountId,
            initialCategoryId = draft?.categoryId ?: editingTemplate?.categoryId ?: txnDetail?.transaction?.categoryId,
            initialBaseCurrency = viewModel.baseCurrency,
            initialForeignCurrency = txnDetail?.transaction?.currencyCode,
            initialAmountLocal = txnDetail?.transaction?.amountBase?.toString()?.takeIf { it != "null" },
            initialIsManual = (txnDetail != null)
        )
    }

    LaunchedEffect(draft, editingTemplate, txnDetail) {
        if (state.multiEntryRows.isEmpty()) {
            if (draft?.isMultiEntry == true) {
                state.isMultiEntry = true
                state.multiEntryRows.addAll(draft.multiEntryRows.map { MultiEntryRow(categoryId = it.categoryId, amount = it.amount, note = it.note, currencyCode = it.currencyCode) })
            } else if (editingTemplate?.multiEntries != null) {
                state.isMultiEntry = true
                editingTemplate.multiEntries.split("|").forEach { entry ->
                    val parts = entry.split(":")
                    if (parts.size >= 2) {
                        state.multiEntryRows.add(MultiEntryRow(categoryId = parts[0].toIntOrNull(), amount = parts[1], note = parts.getOrNull(2), currencyCode = parts.getOrNull(3)))
                    }
                }
            } else if (!state.isMultiEntry) {
                state.multiEntryRows.clear()
                state.multiEntryRows.add(MultiEntryRow(categoryId = null, amount = "", currencyCode = viewModel.baseCurrency))
            }
        }
        
        if (txnDetail?.transaction?.subFrequency != null) {
            state.isSubscription = true
            state.subName = txnDetail.transaction.subName ?: ""
            state.subFrequency = txnDetail.transaction.subFrequency.toString()
        } else if (editingTemplate?.subName != null) {
            state.isSubscription = true
            state.subName = editingTemplate.subName
            state.subFrequency = editingTemplate.subFrequency?.toString() ?: ""
            state.isExistingSubscription = true
        }
        
        state.selectedPartyId = txnDetail?.transaction?.partyId
        state.selectedToPartyId = txnDetail?.transaction?.toPartyId
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
            label = { Text("Currency") },
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

    if (showSavedDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSavedDialog = false
                onBack() 
            },
            title = { Text("Success") },
            text = { Text("Transaction saved successfully!") },
            confirmButton = {
                Button(onClick = { 
                    showSavedDialog = false
                    onBack() 
                }) {
                    Text("OK")
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
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())

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

    val displayPartyName = txnDetail?.partyName ?: accountsRaw.find { it.id == state.selectedAccountId }?.name ?: "Select"
    val displayToPartyName = txnDetail?.toPartyName ?: accountsRaw.find { it.id == state.selectedToAccountId }?.name ?: "Select"
    
    val selectedTagIds = remember { 
        val list = mutableStateListOf<Int>()
        if (draft != null) list.addAll(draft.selectedTagIds)
        else if (editingTemplate != null) editingTemplate.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { list.addAll(it) }
        else txnDetail?.transaction?.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { list.addAll(it) }
        list
    }

    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
        if (!state.isManualLocalAmount) {
            if (viewModel.enableMultiCurrency && state.foreignCurrency != viewModel.baseCurrency) {
                // If multi-currency is ON and foreign is selected,
                // state.amount acts as the original (foreign) amount.
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
                // If multi-currency is OFF or same as base, sync all amount fields
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
                multiEntryRows = state.multiEntryRows.map { DraftMultiEntryRow(it.categoryId, it.amount, it.note, it.currencyCode) }
            )
        }
    }

    BackHandler {
        saveAsDraft()
        onBack()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (isTemplateMode) "Add Template" else if (isActuallyReadOnly) "Transaction Details" else "Add Transaction") },
                navigationIcon = {
                    IconButton(onClick = { saveAsDraft(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state.showMismatchDialog) {
            AlertDialog(
                onDismissRequest = { state.showMismatchDialog = false },
                title = { Text("Total Mismatch") },
                text = { Text("The total amount (${state.mismatchTotals.first}) does not match the sum of row amounts (${state.mismatchTotals.second}). Total amount will be updated to match the rows.") },
                confirmButton = {
                    TextButton(onClick = { 
                        val newTotal = state.mismatchTotals.second.toString()
                        state.amount = newTotal
                        if (viewModel.enableMultiCurrency && state.type != "transfer") {
                            state.amountForeign = newTotal
                            state.isManualLocalAmount = false
                        }
                        state.showMismatchDialog = false
                    }) { Text("Update Total") }
                }
            )
        }

        if (showNameDialog) {
            TemplateNameDialog(
                onDismiss = { showNameDialog = false },
                onSave = { name ->
                    val tagsString = if (selectedTagIds.isEmpty()) null else selectedTagIds.joinToString(",")
                    val multiStr = if (state.isMultiEntry) {
                        state.multiEntryRows.joinToString("|") { "${it.categoryId}:${it.amount}:${it.note ?: ""}:${it.currencyCode ?: ""}" }
                    } else null

                    val newTemplate = Template(
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
                            Toast.makeText(context, "Template Saved", Toast.LENGTH_SHORT).show()
                            showNameDialog = false
                            if (isTemplateMode) onBack()
                        } else {
                            Toast.makeText(context, "Template with this name already exists", Toast.LENGTH_LONG).show()
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
                onNavigate = onNavigate,
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
                                state.multiEntryRows.add(MultiEntryRow(categoryId = parts[0].toIntOrNull(), amount = parts[1], note = parts.getOrNull(2), currencyCode = parts.getOrNull(3)))
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
                    label = { Text("Template Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = CircleShape
                )
            }

            if (isActuallyReadOnly && txnDetail?.transaction?.transactionNumber != null) {
                OutlinedTextField(
                    value = txnDetail.transaction.transactionNumber,
                    onValueChange = {},
                    label = { Text("Transaction Number") },
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

            TransactionTypeRow(state, isActuallyReadOnly, isTemplateMode, viewModel)

            Spacer(Modifier.height(8.dp))

            if (!isTemplateMode) {
                DateTimeSection(state, isActuallyReadOnly, context, (txnDetail != null))
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
                onAccountMicroAccounts = onAccountMicroAccounts,
                readOnly = isActuallyReadOnly,
                onNavigate = onNavigate,
                displayPartyName = displayPartyName,
                displayToPartyName = displayToPartyName,
                isFromOnAccountSelected = isFromOnAccountSelected,
                isToOnAccountSelected = isToOnAccountSelected,
                onFromOnAccountChange = { isFromOnAccountSelected = it },
                onToOnAccountChange = { isToOnAccountSelected = it }
            )

            if (!isActuallyReadOnly && state.type == "expense" && state.selectedAccountId != null) {
                val selectedAcc = accountsRaw.find { it.id == state.selectedAccountId }
                val selectedMinor = allMinorHeads.find { it.id == selectedAcc?.minorHeadId }
                if (selectedMinor?.majorHeadId == 8) { // Is CC
                    val otherCCs = accountsRaw.filter { a -> 
                        a.id != state.selectedAccountId && 
                        allMinorHeads.find { it.id == a.minorHeadId }?.majorHeadId == 8 
                    }
                    if (otherCCs.isNotEmpty()) {
                        val todayDay = LocalDate.now().dayOfMonth
                        val selectedCycleStart = selectedAcc?.billingCycleStart?.toIntOrNull() ?: 1
                        val selectedFloat: Int = if (todayDay >= selectedCycleStart) todayDay - selectedCycleStart else todayDay + (30 - selectedCycleStart)
                        
                        val betterCard = otherCCs.minByOrNull { a ->
                            val cycleStart = a.billingCycleStart?.toIntOrNull() ?: 1
                            val f: Int = if (todayDay >= cycleStart) todayDay - cycleStart else todayDay + (30 - cycleStart)
                            f
                        }
                        
                        val betterCycleStart = betterCard?.billingCycleStart?.toIntOrNull() ?: 1
                        val betterFloat: Int = if (todayDay >= betterCycleStart) todayDay - betterCycleStart else todayDay + (30 - betterCycleStart)
                        
                        if (betterFloat < selectedFloat && betterCard != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Tip: Use '${betterCard.name}' instead to delay your payment further.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.isMultiEntry && viewModel.enableMultiCurrency && state.type != "transfer") {
                CurrencyPickerField(state, viewModel, isActuallyReadOnly)
            }

            Spacer(Modifier.height(8.dp))

            if (state.type != "transfer") {
                MultiEntrySection(state, viewModel, categories, isActuallyReadOnly, onNavigate)
            }

            AmountAndCurrencySection(state, viewModel, isActuallyReadOnly, hideCurrencyPicker = state.isMultiEntry)

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
                    onAdd = { onNavigate?.invoke("add_tag") }
                )
            }

            if (viewModel.templateFields.contains("note") || !isTemplateMode) {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = { if (!isActuallyReadOnly) state.note = it },
                    label = { Text("Note") },
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
                        Text("Duplicate")
                    }
                    Button(
                        onClick = { isLocalEditMode = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
                
                txnDetail?.transaction?.editedAt?.let { editedAt ->
                    val d = Instant.ofEpochMilli(editedAt).atZone(ZoneId.systemDefault())
                    Text(
                        text = "Edited on ${d.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text("Viewing archived transaction data.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
fun TransactionTypeRow(state: AddTransactionState, readOnly: Boolean, isTemplateMode: Boolean, viewModel: ExpenseViewModel) {
    if (viewModel.templateFields.contains("type") || !isTemplateMode) {
        var menuExpanded by remember { mutableStateOf(false) }
        
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = state.type == "income", 
                onClick = { if (!readOnly) { state.type = "income"; state.selectedCategoryId = null } }, 
                label = { Text("Income") },
                enabled = !readOnly || state.type == "income"
            )
            FilterChip(
                selected = state.type == "expense", 
                onClick = { if (!readOnly) { state.type = "expense"; state.selectedCategoryId = null } }, 
                label = { Text("Expense") },
                enabled = !readOnly || state.type == "expense"
            )
            FilterChip(
                selected = state.type == "transfer", 
                onClick = { if (!readOnly) { state.type = "transfer"; state.selectedAccountId = null; state.selectedToAccountId = null; state.selectedCategoryId = null } },
                label = { Text("Transfer") },
                enabled = !readOnly || state.type == "transfer"
            )
            
            if (state.type != "transfer" && !isTemplateMode && !readOnly) {
                IconButton(onClick = { state.isMultiEntry = !state.isMultiEntry }) {
                    Icon(
                        imageVector = if (state.isMultiEntry) Icons.Default.List else Icons.Default.HorizontalRule,
                        contentDescription = "Toggle Multi-Entry",
                        tint = if (state.isMultiEntry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (readOnly && state.type != "transfer") {
                // Show text only if read-only and not transfer
                Text(
                    text = if (state.isMultiEntry) "Multi" else "Single",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun DateTimeSection(state: AddTransactionState, readOnly: Boolean, context: android.content.Context, isEditMode: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f).clickable(enabled = !readOnly && !isEditMode) {
            val dateParts = state.date.split("-")
            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                state.date = String.format("%d-%02d-%02d", year, month + 1, dayOfMonth)
            }, dateParts[0].toInt(), dateParts[1].toInt() - 1, dateParts[2].toInt()).show()
        }) {
            OutlinedTextField(
                value = state.date,
                onValueChange = { },
                label = { Text("Date") },
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

        Box(modifier = Modifier.weight(1f).clickable(enabled = !readOnly && !isEditMode) {
            val timeParts = state.time.split(":")
            TimePickerDialog(context, { _, hour, minute ->
                state.time = String.format("%02d:%02d", hour, minute)
            }, timeParts[0].toInt(), timeParts[1].toInt(), true).show()
        }) {
            OutlinedTextField(
                value = state.time,
                onValueChange = { },
                label = { Text("Time") },
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

@Composable
fun AccountSection(
    state: AddTransactionState,
    viewModel: ExpenseViewModel,
    accounts: List<com.openapps.fintrack.data.Account>,
    balances: List<com.openapps.fintrack.data.AccountBalance>,
    majorHeads: List<com.openapps.fintrack.data.MajorHead>,
    minorHeads: List<com.openapps.fintrack.data.MinorHead>,
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
    if (state.type == "transfer") {
        AccountSelectionDialog(
            label = "From Account",
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
            label = "To Account",
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
            label = "Account",
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
            label = if (state.type == "transfer") "From Party" else if (state.type == "income") "Payer" else "Party Name",
            parties = onAccountMicroAccounts.map { Party(it.id, it.name, it.openingBalance) },
            selectedId = state.selectedAccountId,
            onSelected = { state.selectedAccountId = it },
            enabled = !readOnly,
            onAdd = { onNavigate?.invoke("add_category") },
            displayValue = if (readOnly) displayPartyName else null
        )
    }

    if (state.type == "transfer" && isToOnAccountSelected) {
        Spacer(Modifier.height(8.dp))
        PartySelectionDialog(
            label = "To Party",
            parties = onAccountMicroAccounts.map { Party(it.id, it.name, it.openingBalance) },
            selectedId = state.selectedToAccountId,
            onSelected = { state.selectedToAccountId = it },
            enabled = !readOnly,
            onAdd = { onNavigate?.invoke("add_category") },
            displayValue = if (readOnly) displayToPartyName else null
        )
    }
}

@Composable
fun MultiEntrySection(
    state: AddTransactionState,
    viewModel: ExpenseViewModel,
    categories: List<com.openapps.fintrack.data.Category>,
    readOnly: Boolean,
    onNavigate: ((String) -> Unit)?
) {
    AnimatedVisibility(
        visible = state.isMultiEntry,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Text("Categories & Amounts", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
            state.multiEntryRows.forEachIndexed { index, row ->
                key(row.id) {
                    MultiEntryRowItem(state, index, row, categories, readOnly, onNavigate, viewModel)
                }
            }
            state.categoryError?.let { Text(it, color = Color.Red, style = MaterialTheme.typography.labelSmall) }
            if (!readOnly) {
                TextButton(onClick = { state.multiEntryRows.add(MultiEntryRow(categoryId = null, amount = "", currencyCode = viewModel.baseCurrency)) }, modifier = Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.Add, null)
                    Text("Add Row")
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
            label = "Category",
            categories = categories,
            selectedId = state.selectedCategoryId,
            onSelected = { state.selectedCategoryId = it; state.categoryError = null },
            enabled = !readOnly,
            onAdd = { onNavigate?.let { it("add_category") } },
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
    readOnly: Boolean,
    onNavigate: ((String) -> Unit)?,
    viewModel: ExpenseViewModel
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.shapes.small).padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(0.55f)) {
                CategorySelectionDialog(
                    label = "Category",
                    categories = categories,
                    selectedId = row.categoryId,
                    onSelected = { 
                        state.multiEntryRows[index] = row.copy(categoryId = it) 
                        state.categoryError = null
                    },
                    enabled = !readOnly,
                    onAdd = { onNavigate?.let { it("add_category") } },
                    isError = state.categoryError != null && row.categoryId == null
                )
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
                label = { Text("Amount") },
                modifier = Modifier.weight(0.35f),
                readOnly = readOnly,
                shape = CircleShape,
                isError = state.categoryError != null && row.amount.isBlank(),
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
                    Icon(Icons.Default.RemoveCircle, "Remove", tint = Color.Red, modifier = Modifier.size(16.dp))
                }
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
            label = { Text("Total Amount") },
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
                        Icon(Icons.Default.Calculate, "Calculator")
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
            if (state.type == "transfer") "Recurring transfer - E.g: SIP Investment" else "This is a subscription", 
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
                label = { Text(if (state.type == "transfer") "Select Recurring Transfer / Loan" else "Select Subscription") },
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
                    DropdownMenuItem(text = { Text("No items found") }, onClick = { subExpanded = false }, enabled = false)
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
                                
                                // Handle Multi-entry if needed
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
                                // Check master for frequency
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
                label = { Text("Frequency (months)") }, 
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
                val templateToSave = Template(
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
                            Toast.makeText(context, "Template Saved", Toast.LENGTH_SHORT).show()
                            onBack()
                        } else {
                            Toast.makeText(context, "Template with this name already exists", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save Template") }
        } else {
            OutlinedButton(onClick = onShowNameDialog, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Text("Save as Template") }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShowTemplateSelection, modifier = Modifier.weight(1f)) { Text("Use Template") }
                Button(onClick = {
                    if (validateAndSave(state, viewModel, selectedTagIds, allTransactions, onTransactionSaved, context, updateId, isTemplateMode)) {
                        // success
                    }
                }, modifier = Modifier.weight(1f)) { Text("Save") }
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
    if (evaluateExpression(amtToCheck) <= 0.0) { state.amountError = "missing value"; hasError = true }
    if (state.selectedAccountId == null) { state.accountError = "missing value"; hasError = true }
    if (state.type == "transfer" && state.selectedToAccountId == null) { state.toAccountError = "missing value"; hasError = true }
    val isMultiEntryToUse = state.isMultiEntry && state.type != "transfer"
    
    if (!isMultiEntryToUse && state.type != "transfer" && state.selectedCategoryId == null) { state.categoryError = "missing value"; hasError = true }
    if (isMultiEntryToUse && state.multiEntryRows.any { it.categoryId == null || evaluateExpression(it.amount) <= 0.0 }) { state.categoryError = "missing value"; hasError = true }

    if (state.isSubscription && state.subName.isBlank()) {
        Toast.makeText(context, "Please enter a name for the subscription/transfer", Toast.LENGTH_SHORT).show()
        return false
    }

    if (hasError) {
        Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
        return false
    }

    if (isTemplateMode) {
        // This shouldn't be reached if UI logic is correct, but added as safety
        Toast.makeText(context, "Cannot save transaction in template mode", Toast.LENGTH_SHORT).show()
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
        viewModel.addMultiEntryTransactionExtended(state.date, state.time, state.selectedAccountId!!, state.multiEntryRows.map { com.openapps.fintrack.ui.MultiEntryRowData(it.categoryId!!, evaluateExpression(it.amount), it.note ?: state.note, state.foreignCurrency) }, tagsString, state.type, state.selectedPartyId, state.subName, state.subFrequency.toIntOrNull(), null)
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
                Toast.makeText(context, "Subscription name already exists", Toast.LENGTH_LONG).show()
                return false
            }
        }

        if (state.type == "transfer") {
            viewModel.addTransaction(state.date, state.time, state.selectedAccountId!!, null, amtBase, state.note, state.selectedToAccountId, tagsString, state.type, state.selectedPartyId, state.selectedToPartyId, subNameVal, subFreqVal, updateId = updateId)
            
            if (subNameVal?.startsWith("LOAN:") == true && updateId == null) {
                val loanId = subNameVal.removePrefix("LOAN:").toLongOrNull()
                if (loanId != null) {
                    viewModel.processLoanRepayment(loanId, amtOriginal)
                }
            }
        } else {
            viewModel.addTransaction(state.date, state.time, state.selectedAccountId!!, state.selectedCategoryId, amtBase, state.note, null, tagsString, state.type, state.selectedPartyId, null, subNameVal, subFreqVal, amtOriginal, state.foreignCurrency, amtBase, updateId = updateId)
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
        title = { Text("Template Name") },
        text = { OutlinedTextField(value = inputName, onValueChange = { inputName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { if (inputName.isNotBlank()) onSave(inputName) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TemplateSelectionDialog(templates: List<Template>, onDismiss: () -> Unit, onNavigate: ((String) -> Unit)?, onSelected: (Template) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Template") },
        text = {
            if (templates.isEmpty()) Text("No templates found.")
            else Box(modifier = Modifier.height(300.dp)) {
                LazyColumn {
                    items(templates) { t ->
                        ListItem(headlineContent = { Text(t.name) }, modifier = Modifier.clickable { onSelected(t) })
                        Divider()
                    }
                }
            }
        },
        confirmButton = { if (templates.isEmpty()) Button(onClick = { onDismiss(); onNavigate?.invoke("templates") }) { Text("Go to Templates") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
            Text("Multi-Entry Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            
            totalsByCurrency.forEach { (code, amount) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total $code:", style = MaterialTheme.typography.bodyMedium)
                    Text(String.format(Locale.US, "%.2f", amount), fontWeight = FontWeight.Bold)
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            OutlinedTextField(
                value = amountLocal,
                onValueChange = { if (!readOnly) onAmountLocalChange(it) },
                label = { Text("Total Amount (${viewModel.baseCurrency})") },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = { Text("Sum of rows (converted): ${String.format(Locale.US, "%.2f", computedTotalBase)}") },
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
                        label = { Text("Curr") },
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
                label = { Text(if (hideCurrencyPicker) "Total Amount ($foreignCurrency)" else "Amount (Foreign)") },
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
                            Icon(Icons.Default.Calculate, "Calculator")
                        }
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        // Conversion Rate Field
        OutlinedTextField(
            value = manualRateInput,
            onValueChange = { if (!readOnly) onManualRateChange(it) },
            label = { Text("Conversion Rate (1 $foreignCurrency = ? ${viewModel.baseCurrency})") },
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            leadingIcon = { Icon(Icons.Default.Sync, null, modifier = Modifier.size(16.dp), tint = Color.Gray) },
            supportingText = {
                if (isStale) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, "Stale Rate", tint = Color.Red, modifier = Modifier.size(12.dp))
                        Text(" Rate is stale (> 7 days old)", style = MaterialTheme.typography.labelSmall, color = Color.Red)
                    }
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = amountLocal,
            onValueChange = { if (!readOnly) onAmountLocalChange(it) },
            label = { Text("Amount (Local / ${viewModel.baseCurrency})") },
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
        title = { Text("Select Currency") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search") },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
    val selectedName = if (isOnAccountSelected) "On Account (Loan)" else accounts.find { it.id == selectedId }?.name ?: "Select Account"

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
                        placeholder = { Text("Search") },
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
                                    headlineContent = { Text("On Account (Loan)") },
                                    modifier = Modifier.clickable {
                                        onOnAccountSelected()
                                        showDialog = false
                                    }
                                )
                                Divider()
                            }
                        }
                        items(filteredAccounts) { account ->
                            val bal = balances.find { it.id == account.id }?.balance ?: 0.0
                            val minor = minorHeads.find { it.id == account.minorHeadId }
                            val major = majorHeads.find { it.id == minor?.majorHeadId }
                            
                            ListItem(
                                headlineContent = { Text(account.name) },
                                supportingContent = { 
                                    Text("${major?.name ?: "Others"} | ${viewModel.formatAmount(bal)}", color = if (bal >= 0) Color(0xFF4CAF50) else Color.Red)
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
                        Text("Add")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun PartySelectionDialog(label: String, parties: List<Party>, selectedId: Int?, onSelected: (Int) -> Unit, enabled: Boolean = true, onAdd: () -> Unit, displayValue: String? = null) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = displayValue ?: parties.find { it.id == selectedId }?.name ?: "Select $label"

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
                        Text("Add New")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
fun CategorySelectionDialog(label: String, categories: List<com.openapps.fintrack.data.Category>, selectedId: Int?, onSelected: (Int) -> Unit, enabled: Boolean = true, onAdd: () -> Unit, isError: Boolean = false) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedName = categories.find { it.id == selectedId }?.name ?: "Select"

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
                        placeholder = { Text("Search") },
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
            confirmButton = {
                Row {
                    Button(onClick = onAdd) {
                        Icon(Icons.Default.Add, null)
                        Text("Add")
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDialog = false }) { Text("Cancel") }
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
                        Text("Add")
                    }
                    if (multiSelect) {
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { showDialog = false }) { Text("Done") }
                    }
                }
            },
            dismissButton = {
                if (!multiSelect) {
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
