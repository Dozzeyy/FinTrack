/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.openapps.fintrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import com.openapps.fintrack.data.TransactionWithDetails
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ExpenseViewModel,
    onNavigate: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf("home") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var analysisInitialType by remember { mutableStateOf<String?>(null) }
    var sharedDateRange by remember { mutableStateOf<Pair<String, String>?>(null) }

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())

    val filteredSearchTransactions = remember(searchQuery, allTransactions) {
        if (searchQuery.isBlank()) emptyList()
        else {
            allTransactions.filter { t ->
                t.transaction.note?.contains(searchQuery, ignoreCase = true) == true ||
                t.categoryName?.contains(searchQuery, ignoreCase = true) == true ||
                t.accountName.contains(searchQuery, ignoreCase = true) == true ||
                t.toAccountName?.contains(searchQuery, ignoreCase = true) == true ||
                t.transaction.amount.toString().contains(searchQuery) ||
                t.transaction.date.contains(searchQuery) ||
                t.transaction.transactionNumber?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.app_name) + stringResource(R.string.label_menu_suffix), modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                        Divider()
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_home)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; selectedTab = "home" },
                            icon = { Icon(Icons.Default.Home, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_summary)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("summary") },
                            icon = { Icon(Icons.Default.Assessment, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_import)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("import_statement") },
                            icon = { Icon(Icons.Default.FileUpload, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_credit_cards)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("credit_cards") },
                            icon = { Icon(Icons.Default.CreditCard, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_subscriptions)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("subscriptions") },
                            icon = { Icon(Icons.Default.CardMembership, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_performance)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("performance") },
                            icon = { Icon(Icons.Default.Speed, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_categories)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("manage_categories") },
                            icon = { Icon(Icons.Default.Category, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_templates)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("templates") },
                            icon = { Icon(Icons.Default.Dashboard, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_tags)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("manage_tags") },
                            icon = { Icon(Icons.Default.Label, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_budgets)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("manage_budgets") },
                            icon = { Icon(Icons.Default.AccountBalanceWallet, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_notes)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("notes") },
                            icon = { Icon(Icons.Default.Notes, null) }
                        )

                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_permissions)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("permissions") },
                            icon = { Icon(Icons.Default.Security, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_tutorial)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("tutorial") },
                            icon = { Icon(Icons.Default.HelpCenter, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_settings)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("settings") },
                            icon = { Icon(Icons.Default.Settings, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_database)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("database") },
                            icon = { Icon(Icons.Default.Storage, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.menu_contact)) },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("contact") },
                            icon = { Icon(Icons.Default.Email, null) }
                        )
                    }
                    
                    Text(
                        "v1.0.18",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }
        }
    ) {
        Scaffold { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(
                    targetState = isSearchActive,
                    transitionSpec = {
                        if (targetState) {
                            (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
                        } else {
                            (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                        }
                    },
                    label = "SearchTransition"
                ) { searchActive ->
                    if (searchActive) {
                        Column {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 4.dp
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text(stringResource(R.string.label_search_placeholder)) },
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    singleLine = true,
                                    leadingIcon = {
                                        IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                            Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                                        }
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    )
                                )
                            }

                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                items(filteredSearchTransactions) { detail ->
                                    Box(modifier = Modifier.clickable { 
                                        viewModel.selectedTransactionDetail = detail 
                                    }) {
                                        TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = false)
                                    }
                                }
                            }
                        }
                        if (viewModel.selectedTransactionDetail != null) {
                            BackHandler { viewModel.selectedTransactionDetail = null }
                            AddTransactionScreen(
                                viewModel = viewModel, 
                                onBack = { viewModel.selectedTransactionDetail = null }, 
                                onNavigate = onNavigate, 
                                readOnly = true
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = selectedTab,
                            transitionSpec = {
                                fadeIn().togetherWith(fadeOut())
                            },
                            label = "TabTransition"
                        ) { tab ->
                            when (tab) {
                                "home" -> HomeView(
                                    viewModel, 
                                    onNavigate, 
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onNavigateToAnalysis = { type, start, end ->
                                        analysisInitialType = type
                                        sharedDateRange = Pair(start, end)
                                        selectedTab = "analysis"
                                    },
                                    onTabChange = { selectedTab = it }
                                )
                                "analysis" -> {
                                    AnalysisView(
                                        viewModel, 
                                        onNavigate, 
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        initialType = analysisInitialType,
                                        initialDateRange = sharedDateRange
                                    )
                                }
                                "transactions" -> TransactionsView(viewModel, onNavigate, onOpenDrawer = { scope.launch { drawerState.open() } })
                                "budgets" -> BudgetsTab(viewModel, onNavigate, onOpenDrawer = { scope.launch { drawerState.open() } })
                            }
                        }
                    }
                }

                if (!isSearchActive && viewModel.selectedTransactionDetail == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        FloatingBottomNav(
                            selectedTab = selectedTab,
                            onTabChange = { selectedTab = it },
                            onSearchClick = { isSearchActive = true },
                            tabOrder = viewModel.bottomTabOrder
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 72.dp, end = 16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val fabLabel = when (selectedTab) {
                            "budgets" -> stringResource(R.string.btn_add_budget_fab)
                            else -> stringResource(R.string.btn_add_transaction_fab)
                        }
                        
                        val isServerRunning by viewModel.isServerRunning.collectAsState()
                        val snackbarHostState = remember { SnackbarHostState() }
                        val scope = rememberCoroutineScope()
                        val editingDisabledMsg = stringResource(R.string.msg_editing_disabled_server)

                        FloatingActionButton(
                            onClick = { 
                                if (isServerRunning) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(editingDisabledMsg)
                                    }
                                } else {
                                    if (selectedTab == "budgets") onNavigate("add_budget")
                                    else onNavigate("add_transaction")
                                }
                            },
                            containerColor = if (isServerRunning) Color.Gray else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(Icons.Default.Add, fabLabel)
                        }
                        
                        SnackbarHost(hostState = snackbarHostState)
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingBottomNav(
    selectedTab: String,
    onTabChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    tabOrder: List<String>
) {
    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .height(60.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            tabOrder.forEach { tabKey ->
                val isSelected = selectedTab == tabKey
                val (label, icon) = when (tabKey) {
                    "home" -> stringResource(R.string.menu_home) to Icons.Default.Home
                    "analysis" -> stringResource(R.string.menu_analysis) to Icons.Default.PieChart
                    "transactions" -> stringResource(R.string.menu_entries) to Icons.Default.List
                    "budgets" -> stringResource(R.string.menu_budgets) to Icons.Default.AccountBalanceWallet
                    else -> "" to Icons.Default.Home
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onTabChange(tabKey) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(22.dp),
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.btn_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(
    viewModel: ExpenseViewModel, 
    onNavigate: (String) -> Unit, 
    onOpenDrawer: () -> Unit,
    onNavigateToAnalysis: (String, String, String) -> Unit,
    onTabChange: (String) -> Unit
) {
    val refreshTrigger by viewModel.refreshTrigger.collectAsState()
    
    var startDate by remember(refreshTrigger) { mutableStateOf(LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember(refreshTrigger) { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var showFilter by remember { mutableStateOf(false) }
    
    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    
    val s = LocalDate.parse(startDate)
    val e = LocalDate.parse(endDate)
    val prevStart = s.minusMonths(1).format(DateTimeFormatter.ISO_DATE)
    val prevEnd = e.minusMonths(1).format(DateTimeFormatter.ISO_DATE)
    val prevTransactions by viewModel.getFilteredTransactions(prevStart, prevEnd).collectAsState(initial = emptyList())

    var showTransactionListType by remember { mutableStateOf<String?>(null) }
    var showDiscretionaryOnly by remember { mutableStateOf(false) }

    val expenseLabel = stringResource(R.string.label_expense)
    val incomeLabel = stringResource(R.string.label_income)

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else if (showTransactionListType != null || showDiscretionaryOnly) {
        BackHandler { 
            showTransactionListType = null
            showDiscretionaryOnly = false
        }
        TransactionListOverlay(
            title = if (showDiscretionaryOnly) stringResource(R.string.label_discretionary_spending) else if (showTransactionListType == "expense") expenseLabel else incomeLabel,
            transactions = if (showDiscretionaryOnly) transactions.filter { it.transaction.isDiscretionary } else transactions.filter { it.categoryType == showTransactionListType },
            viewModel = viewModel,
            onBack = { 
                showTransactionListType = null
                showDiscretionaryOnly = false
            }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            val ccAlerts by viewModel.getCcAlerts().collectAsState(initial = emptyList())
            val subAlerts by viewModel.getSubscriptionAlerts().collectAsState(initial = emptyList())

            ccAlerts.forEach { alert ->
                var offsetX by remember { mutableStateOf(0f) }
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta -> offsetX += delta },
                            onDragStopped = {
                                if (kotlin.math.abs(offsetX) > 300) {
                                    viewModel.toggleCcPaid(alert.accountId, true)
                                }
                                offsetX = 0f
                            }
                        ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${alert.accountName} " + stringResource(R.string.label_due_colon) + alert.dueDate, style = MaterialTheme.typography.labelLarge)
                            Text(stringResource(R.string.label_amt_payable_colon) + viewModel.formatAmount(kotlin.math.abs(alert.amount)), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            subAlerts.forEach { alert ->
                var offsetX by remember { mutableStateOf(0f) }
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta -> offsetX += delta },
                            onDragStopped = {
                                if (kotlin.math.abs(offsetX) > 300) {
                                    viewModel.toggleCcPaidCustom("SUB_${alert.subName}", true)
                                }
                                offsetX = 0f
                            }
                        ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (alert.isTransfer) Icons.Default.SwapHoriz else Icons.Default.CardMembership, 
                            null, 
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("${alert.subName} " + stringResource(R.string.label_due_colon) + alert.dueDate, style = MaterialTheme.typography.labelLarge)
                            Text(
                                (if(alert.isTransfer) stringResource(R.string.label_recurring_transfers) + ": " else stringResource(R.string.label_total_due) + ": ") + viewModel.formatAmount(alert.amount), 
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (viewModel.draftTransaction != null) {
                var offsetX by remember { mutableStateOf(0f) }
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                offsetX += delta
                            },
                            onDragStopped = {
                                if (kotlin.math.abs(offsetX) > 300) {
                                    viewModel.draftTransaction = null
                                }
                                offsetX = 0f
                            }
                        )
                        .clickable { onNavigate("add_transaction") },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Drafts, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.msg_draft_pending), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, stringResource(R.string.menu_home))
                    }
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Row {
                    IconButton(onClick = { viewModel.generateFinancialInsights() }) {
                        if (viewModel.isGeneratingInsights) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, stringResource(R.string.label_insights))
                        }
                    }
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.DateRange, stringResource(R.string.label_filter))
                    }
                }
            }
            
            Text("$startDate to $endDate", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            if (viewModel.showInsightsOverlay && viewModel.financialInsights.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.label_insights), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                viewModel.financialInsights.forEach { insight ->
                    var offsetX by remember { mutableStateOf(0f) }
                    val backgroundColor = when(insight.type) {
                        com.openapps.fintrack.data.InsightType.WARNING -> MaterialTheme.colorScheme.errorContainer
                        com.openapps.fintrack.data.InsightType.ANOMALY -> MaterialTheme.colorScheme.tertiaryContainer
                        com.openapps.fintrack.data.InsightType.TREND -> MaterialTheme.colorScheme.secondaryContainer
                        com.openapps.fintrack.data.InsightType.OPPORTUNITY -> MaterialTheme.colorScheme.primaryContainer
                    }
                    val icon = when(insight.type) {
                        com.openapps.fintrack.data.InsightType.WARNING -> Icons.Default.Warning
                        com.openapps.fintrack.data.InsightType.ANOMALY -> Icons.Default.ErrorOutline
                        com.openapps.fintrack.data.InsightType.TREND -> Icons.Default.TrendingUp
                        com.openapps.fintrack.data.InsightType.OPPORTUNITY -> Icons.Default.Lightbulb
                    }

                    Surface(
                        color = backgroundColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta -> offsetX += delta },
                                onDragStopped = {
                                    if (kotlin.math.abs(offsetX) > 300) {
                                        viewModel.dismissInsight(insight)
                                    }
                                    offsetX = 0f
                                }
                            ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(insight.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(insight.description, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    onClick = { viewModel.pauseInsight(insight.id) },
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Icon(Icons.Default.PauseCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.btn_pause_7_days), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                if (viewModel.financialInsights.isEmpty()) {
                    viewModel.showInsightsOverlay = false
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            val curInc = transactions.filter { it.categoryType == "income" }.sumOf { it.transaction.amount }
            val curExp = transactions.filter { it.categoryType == "expense" }.sumOf { it.transaction.amount }
            val prevInc = prevTransactions.filter { it.categoryType == "income" }.sumOf { it.transaction.amount }
            val prevExp = prevTransactions.filter { it.categoryType == "expense" }.sumOf { it.transaction.amount }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().clickable { showTransactionListType = "expense" }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.label_expense), style = MaterialTheme.typography.labelMedium, color = Color.Red)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(viewModel.formatAmount(curExp), style = MaterialTheme.typography.titleLarge, color = Color.Red, fontWeight = FontWeight.Bold)
                                if (curExp > prevExp) {
                                    Text(" (↑)", style = MaterialTheme.typography.titleLarge, color = Color.Red, fontWeight = FontWeight.ExtraBold)
                                } else if (curExp < prevExp) {
                                    Text(" (↓)", style = MaterialTheme.typography.titleLarge, color = Color.Red, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Text(stringResource(R.string.label_prev_month_colon) + viewModel.formatAmount(prevExp), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth().clickable { showTransactionListType = "income" }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.label_income), style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(viewModel.formatAmount(curInc), style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                if (curInc > prevInc) {
                                    Text(" (↑)", style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.ExtraBold)
                                } else if (curInc < prevInc) {
                                    Text(" (↓)", style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Text(stringResource(R.string.label_prev_month_colon) + viewModel.formatAmount(prevInc), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }

                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.label_net), fontWeight = FontWeight.Bold)
                        Text(viewModel.formatAmount(curInc - curExp), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (viewModel.negotiationTrackerEnabled) {
                val totalSavings = transactions.sumOf { (it.transaction.negotiationAmountOriginal ?: it.transaction.amount) - it.transaction.amount }
                if (totalSavings > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.label_negotiated_savings), fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            Text(viewModel.formatAmount(totalSavings), fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        }
                    }
                }
            }

            if (viewModel.discretionarySpendingTrackerEnabled) {
                val discTotal = transactions.filter { it.transaction.isDiscretionary }.sumOf { it.transaction.amount }
                if (discTotal > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clickable { showDiscretionaryOnly = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.label_discretionary_spend_title), fontWeight = FontWeight.Bold)
                            Text(viewModel.formatAmount(discTotal), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            val balances by viewModel.getAccountBalances(endDate).collectAsState(initial = emptyList())
            val netPosition = balances.sumOf { it.balance }
            var isNetPositionVisible by remember { mutableStateOf(!viewModel.tapToShowNetPosition) }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.label_net_position), 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            viewModel.summaryInitialTab = "Assets"
                            onNavigate("summary")
                        }
                    )
                    Text(
                        if (isNetPositionVisible) viewModel.formatAmount(netPosition) else "****",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            isNetPositionVisible = !isNetPositionVisible
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            
            Text(stringResource(R.string.label_top_expenses), style = MaterialTheme.typography.titleMedium)
            val topExpenses = transactions.filter { it.categoryType == "expense" }
                .groupBy { it.categoryName ?: stringResource(R.string.label_uncategorized) }
                .mapValues { it.value.sumOf { t -> t.transaction.amount } }
                .toList()
                .sortedByDescending { it.second }
                .take(4)
            
            if (topExpenses.isNotEmpty()) {
                Box(modifier = Modifier.clickable { onNavigateToAnalysis("Expense", startDate, endDate) }) {
                    ExpensePieChart(topExpenses, viewModel)
                }
            } else {
                Text(stringResource(R.string.label_no_expenses_period), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))

            if (viewModel.dashboardAccountIds.isNotEmpty()) {
                val selectedBalances = balances.filter { it.id in viewModel.dashboardAccountIds }
                if (selectedBalances.isNotEmpty()) {
                    Text(stringResource(R.string.label_dashboard_accounts), style = MaterialTheme.typography.titleMedium)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clickable {
                                viewModel.summaryInitialTab = "Assets"
                                onNavigate("summary")
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        HorizontalBalanceChart(accounts = selectedBalances, viewModel = viewModel)
                    }
                }
            }

            if (viewModel.dashboardBudgetIds.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val budgetVsActual by viewModel.getBudgetVsActual(endDate).collectAsState(initial = emptyList())
                val selectedBudgetsRaw by viewModel.getAllBudgets().collectAsState(initial = emptyList())
                val selectedBudgets = selectedBudgetsRaw.filter { it.id in viewModel.dashboardBudgetIds }

                if (selectedBudgets.isNotEmpty()) {
                    Text(stringResource(R.string.label_budget_performance), style = MaterialTheme.typography.titleMedium)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedBudgets) { budget ->
                            val performance = budgetVsActual.find { it.categoryName == budget.name || (budget.name == null && it.categoryName.contains(budget.categoryIds.split(",")[0])) }
                            val actual = performance?.actualAmount ?: 0.0
                            val limit = budget.amount
                            val percent = if (limit != 0.0) (actual / limit * 100).toInt() else 0
                            
                            val isGoalMet = if (budget.higherIsBetter) {
                                actual >= limit
                            } else {
                                actual <= limit
                            }
                            val statusColor = if (isGoalMet) Color(0xFF4CAF50) else Color.Red

                            Card(
                                modifier = Modifier.width(180.dp).clickable { onTabChange("budgets") }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(budget.name ?: stringResource(R.string.label_budget), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    Text("$percent%", style = MaterialTheme.typography.titleMedium, color = statusColor, fontWeight = FontWeight.Bold)
                                    LinearProgressIndicator(
                                        progress = (actual / limit).toFloat().coerceIn(0f, 1f),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        color = statusColor,
                                        trackColor = statusColor.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(80.dp))
        }
    }

    if (showFilter) {
        DateRangeFilterDialog(
            onDismiss = { showFilter = false },
            onApply = { start, end ->
                startDate = start
                endDate = end
                showFilter = false
            }
        )
    }
}

@Composable
fun SummaryColumn(label: String, current: Double, previous: Double, color: Color, viewModel: ExpenseViewModel, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable { onClick() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(viewModel.formatAmount(current), style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold)
            if (current > previous) {
                Text(" (↑)", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.ExtraBold)
            } else if (current < previous) {
                Text(" (↓)", style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.ExtraBold)
            }
        }
        Text(stringResource(R.string.label_prev_colon) + viewModel.formatAmount(previous), style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
    }
}

@Composable
fun ExpensePieChart(expenses: List<Pair<String, Double>>, viewModel: ExpenseViewModel) {
    val colors = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow)
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        PieChart(data = expenses, colors = colors)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            expenses.forEachIndexed { index, pair ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Box(modifier = Modifier.size(12.dp).background(colors[index % colors.size]))
                    Spacer(Modifier.width(8.dp))
                    Text(pair.first, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    Text(viewModel.formatAmount(pair.second), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisView(
    viewModel: ExpenseViewModel, 
    onNavigate: (String) -> Unit, 
    onOpenDrawer: () -> Unit,
    initialType: String? = null,
    initialDateRange: Pair<String, String>? = null
) {
    val expenseLabel = stringResource(R.string.label_expense)
    val incomeLabel = stringResource(R.string.label_income)
    val accountLabel = stringResource(R.string.label_account)
    val tagsLabel = stringResource(R.string.label_tags)
    val merchantsLabel = stringResource(R.string.label_merchants)
    val networthLabel = stringResource(R.string.label_networth)
    val onAccountLoanLabel = stringResource(R.string.label_on_account_loan)

    var type by remember { mutableStateOf(initialType ?: expenseLabel) }
    var month by remember { mutableStateOf(LocalDate.now()) }
    var startDate by remember { mutableStateOf(initialDateRange?.first ?: month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(initialDateRange?.second ?: month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)) }
    var showFilter by remember { mutableStateOf(false) }
    var categoryFilterIds by remember { mutableStateOf<Set<Int>?>(null) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var isMainLevelAnalysis by remember { mutableStateOf(false) }
    
    var selectedAnalysisPartyIds = remember { mutableStateListOf<Int>() }
    
    var invoiceStatusFilter by remember { mutableStateOf("Open") }
    
    var filterByParty by remember { mutableStateOf(false) }
    var filterByOverdue by remember { mutableStateOf(false) }
    var filterByAmount by remember { mutableStateOf(false) }
    var minAmtFilter by remember { mutableStateOf("") }
    var maxAmtFilter by remember { mutableStateOf("") }
    var filterByDueInXDays by remember { mutableStateOf(false) }
    var dueInXDaysFilter by remember { mutableStateOf("") }
    var filterByWasDueYDays by remember { mutableStateOf(false) }
    var wasDueYDaysFilter by remember { mutableStateOf("") }
    var showAdvancedFilterDialog by remember { mutableStateOf(false) }
    var tagFilterIds by remember { mutableStateOf<Set<Int>?>(null) }
    var groupByParty by remember { mutableStateOf(false) }

    var accountsSubTab by remember { mutableStateOf("Balance") }
    var isTagBarChartView by remember { mutableStateOf(false) }
    var analysisSelectedAccountId by remember { mutableStateOf<Int?>(null) }
    
    val allTransactionsList by viewModel.allTransactions.collectAsState(initial = emptyList())
    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    val balances by viewModel.getAccountBalances(endDate).collectAsState(initial = emptyList())
    val tags by viewModel.getAllTags().collectAsState(initial = emptyList())
    val allAccountsList by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val minorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())
    val majorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val allCategories by viewModel.getEnabledCategories().collectAsState(initial = emptyList())

    val allInvoices by viewModel.getInvoicesForParties(allAccountsList.filter { a -> minorHeads.find { it.id == a.minorHeadId }?.majorHeadId == (majorHeads.find { it.name.contains("On Account", ignoreCase = true) }?.id ?: 6) }.map { it.id }, endDate).collectAsState(initial = emptyList())

    val filteredInvoices = remember(allInvoices, selectedAnalysisPartyIds.toList(), filterByParty, filterByOverdue, filterByAmount, minAmtFilter, maxAmtFilter, filterByDueInXDays, dueInXDaysFilter, filterByWasDueYDays, wasDueYDaysFilter, invoiceStatusFilter, categoryFilterIds, tagFilterIds) {
        allInvoices.filter { inv ->
            val outstanding = inv.detail.transaction.amount - inv.totalCleared
            val isOverdue = try { 
                val dueDate = LocalDate.parse(inv.detail.transaction.date).plusDays(inv.detail.transaction.dueDays?.toLong() ?: 0)
                dueDate.isBefore(LocalDate.now()) && outstanding > 0
            } catch(e: Exception) { false }

            val statusMatch = when(invoiceStatusFilter) {
                "Open" -> outstanding > 0
                "Cleared" -> outstanding <= 0
                else -> true
            }
            if (!statusMatch) return@filter false

            if (filterByParty && selectedAnalysisPartyIds.isNotEmpty()) {
                val partyId = inv.detail.transaction.partyId ?: inv.detail.transaction.accountId
                if (partyId !in selectedAnalysisPartyIds) return@filter false
            }
            if (filterByOverdue && !isOverdue) return@filter false
            
            if (filterByAmount) {
                val min = minAmtFilter.toDoubleOrNull() ?: 0.0
                val max = maxAmtFilter.toDoubleOrNull() ?: Double.MAX_VALUE
                if (inv.detail.transaction.amount < min || inv.detail.transaction.amount > max) return@filter false
            }

            if (filterByDueInXDays) {
                val days = dueInXDaysFilter.toLongOrNull() ?: 0L
                try {
                    val dueDate = LocalDate.parse(inv.detail.transaction.date).plusDays(inv.detail.transaction.dueDays?.toLong() ?: 0)
                    val diff = ChronoUnit.DAYS.between(LocalDate.now(), dueDate)
                    if (diff < 0 || diff > days) return@filter false
                } catch(e: Exception) { return@filter false }
            }

            if (filterByWasDueYDays) {
                val days = wasDueYDaysFilter.toLongOrNull() ?: 0L
                try {
                    val dueDate = LocalDate.parse(inv.detail.transaction.date).plusDays(inv.detail.transaction.dueDays?.toLong() ?: 0)
                    val diff = ChronoUnit.DAYS.between(dueDate, LocalDate.now())
                    if (diff < 0 || diff > days) return@filter false
                } catch(e: Exception) { return@filter false }
            }

            if (categoryFilterIds != null && inv.detail.transaction.categoryId !in categoryFilterIds!!) return@filter false
            
            if (tagFilterIds != null) {
                val txnTags = inv.detail.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                if (txnTags.none { it in tagFilterIds!! }) return@filter false
            }

            true
        }
    }

    LaunchedEffect(allAccountsList) {
        if (analysisSelectedAccountId == null && allAccountsList.isNotEmpty()) {
            analysisSelectedAccountId = allAccountsList.first().id
        }
    }

    var showDetailList by remember { mutableStateOf<String?>(null) }
    var showTagDetailList by remember { mutableStateOf<Int?>(null) }
    
    val selectAccountLabel = stringResource(R.string.label_select_account_analysis)
    val currentAccountName = remember(analysisSelectedAccountId, allAccountsList) {
        allAccountsList.find { it.id == analysisSelectedAccountId }?.name ?: selectAccountLabel
    }

    val uncategorizedLabel = stringResource(R.string.label_uncategorized)

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else if (showDetailList != null) {
        BackHandler { showDetailList = null }
        val filteredList = remember(transactions, type, accountsSubTab, analysisSelectedAccountId, showDetailList, isMainLevelAnalysis) {
            if (type == accountLabel) {
            
                if (accountsSubTab == "Spending") {
                    transactions.filter { it.transaction.accountId == analysisSelectedAccountId && it.categoryName == showDetailList }
                } else if (accountsSubTab == "Source") {
                    transactions.filter { 
                        it.transaction.categoryId == null &&
                        ((it.transaction.accountId == analysisSelectedAccountId && it.toAccountName == showDetailList) || 
                         (it.transaction.toAccountId == analysisSelectedAccountId && it.accountName == showDetailList))
                    }
                } else {
                    transactions.filter { it.accountName == showDetailList || it.toAccountName == showDetailList }
                }
            } else if (type == expenseLabel || type == incomeLabel) {
            
                transactions.filter { 
                    val catName = it.categoryName ?: uncategorizedLabel
                    if (isMainLevelAnalysis) {
                        val mainPart = if (catName.contains(":")) catName.split(":").first().trim() else catName
                        mainPart == showDetailList
                    } else {
                        val minorPart = if (catName.contains(":")) catName.split(":").last().trim() else catName
                        minorPart == showDetailList
                    }
                }
            } else if (showDetailList!!.startsWith("MERCHANT:")) {
                val merchant = showDetailList!!.removePrefix("MERCHANT:")
                transactions.filter { it.transaction.merchantName == merchant }
            } else {
                transactions.filter { it.accountName == showDetailList || it.toAccountName == showDetailList }
            }
        }
        TransactionListOverlay(
            title = showDetailList!!,
            transactions = filteredList,
            viewModel = viewModel,
            onBack = { showDetailList = null }
        )
    } else if (showTagDetailList != null) {
        BackHandler { showTagDetailList = null }
        val tagName = tags.find { it.id == showTagDetailList }?.name ?: "Tag"
        TransactionListOverlay(
            title = tagName,
            transactions = transactions.filter { t -> t.transaction.tags?.split(",")?.contains(showTagDetailList.toString()) == true },
            viewModel = viewModel,
            onBack = { showTagDetailList = null }
        )
    } else {
        val chartColors = listOf(Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta, Color.Cyan, Color.Gray, Color.DarkGray, Color.LightGray)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, stringResource(R.string.menu_home))
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        val currentTypeLabel = when(type) {
                            expenseLabel -> expenseLabel
                            incomeLabel -> incomeLabel
                            accountLabel -> accountLabel
                            tagsLabel -> tagsLabel
                            merchantsLabel -> merchantsLabel
                            networthLabel -> networthLabel
                            onAccountLoanLabel -> onAccountLoanLabel
                            else -> type
                        }
                        TextButton(onClick = { expanded = true }) {
                            Text(currentTypeLabel, style = MaterialTheme.typography.titleLarge)
                            Icon(Icons.Default.ArrowDropDown, "")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text(expenseLabel) }, onClick = { type = expenseLabel; expanded = false })
                            DropdownMenuItem(text = { Text(incomeLabel) }, onClick = { type = incomeLabel; expanded = false })
                            DropdownMenuItem(text = { Text(accountLabel) }, onClick = { type = accountLabel; expanded = false })
                            DropdownMenuItem(text = { Text(tagsLabel) }, onClick = { type = tagsLabel; expanded = false })
                            DropdownMenuItem(text = { Text(merchantsLabel) }, onClick = { type = merchantsLabel; expanded = false })
                            DropdownMenuItem(text = { Text(networthLabel) }, onClick = { type = networthLabel; expanded = false })
                            DropdownMenuItem(text = { Text(onAccountLoanLabel) }, onClick = { type = onAccountLoanLabel; expanded = false })
                        }
                    }
                    if (type == expenseLabel || type == incomeLabel) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { showCategoryFilterDialog = true }) {
                            Icon(
                                Icons.Default.FilterAlt, 
                                null, 
                                modifier = Modifier.size(20.dp),
                                tint = if (categoryFilterIds != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { isMainLevelAnalysis = !isMainLevelAnalysis }) {
                            Icon(
                                if (isMainLevelAnalysis) Icons.Default.Layers else Icons.Default.LayersClear,
                                contentDescription = if (isMainLevelAnalysis) stringResource(R.string.label_main_level) else stringResource(R.string.label_minor_level),
                                modifier = Modifier.size(20.dp),
                                tint = if (isMainLevelAnalysis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (type == tagsLabel) {
                        IconButton(onClick = { isTagBarChartView = !isTagBarChartView }) {
                            Icon(if (isTagBarChartView) Icons.Default.PieChart else Icons.Default.BarChart, stringResource(R.string.label_toggle_chart))
                        }
                    }
                    
                    if (type == onAccountLoanLabel && viewModel.invoiceAgeTrackingEnabled) {
                        IconButton(onClick = { showAdvancedFilterDialog = true }) {
                            val hasActiveFilter = filterByParty || filterByOverdue || filterByAmount || filterByDueInXDays || filterByWasDueYDays || categoryFilterIds != null || tagFilterIds != null || groupByParty
                            Icon(
                                Icons.Default.FilterList, 
                                contentDescription = stringResource(R.string.label_advanced_filters),
                                tint = if (hasActiveFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    IconButton(onClick = { showFilter = true }) { 
                        Icon(if (type == onAccountLoanLabel) Icons.Default.DateRange else Icons.Default.FilterList, stringResource(R.string.label_filter)) 
                    }
                }
            }
                
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                var monthMenuExpanded by remember { mutableStateOf(false) }
                var yearMenuExpanded by remember { mutableStateOf(false) }
                
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { monthMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        val locale = Locale.getDefault()
                        Text(month.format(DateTimeFormatter.ofPattern("MMMM", locale)))
                        Icon(Icons.Default.ArrowDropDown, "")
                    }
                    DropdownMenu(expanded = monthMenuExpanded, onDismissRequest = { monthMenuExpanded = false }) {
                        val locale = Locale.getDefault()
                        (1..12).forEach { m ->
                            val mObj = Month.of(m)
                            DropdownMenuItem(
                                text = { Text(mObj.getDisplayName(java.time.format.TextStyle.FULL, locale)) },
                                onClick = {
                                    month = month.withMonth(m)
                                    startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                                    endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
                                    monthMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = { yearMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(month.year.toString())
                        Icon(Icons.Default.ArrowDropDown, "")
                    }
                    DropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                        val currentYear = LocalDate.now().year
                        (currentYear - 5..currentYear + 5).forEach { y ->
                            DropdownMenuItem(
                                text = { Text(y.toString()) },
                                onClick = {
                                    month = month.withYear(y)
                                    startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                                    endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
                                    yearMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            val bTrendData = remember(allTransactionsList, analysisSelectedAccountId, startDate, endDate, accountsSubTab, type) {
                if (type == "Accounts" && accountsSubTab == "BTrend" && analysisSelectedAccountId != null) {
                    val start = LocalDate.parse(startDate)
                    val end = LocalDate.parse(endDate)
                    val accountId = analysisSelectedAccountId!!
                    
                    val accountObj = allAccountsList.find { it.id == accountId }
                    val openingBalance = accountObj?.openingBalance ?: 0.0
                    
                    val txnsBefore = allTransactionsList.filter { 
                        it.transaction.date < startDate && 
                        (it.transaction.accountId == accountId || it.transaction.toAccountId == accountId)
                    }
                    
                    var currentBal = openingBalance + txnsBefore.sumOf { t ->
                        if (t.transaction.toAccountId == accountId) t.transaction.amount
                        else if (t.transaction.accountId == accountId) {
                            if (t.categoryType == "income") t.transaction.amount else -t.transaction.amount
                        } else 0.0
                    }
                    
                    val daysInMonth = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
                    val trend = mutableListOf<Pair<String, Double>>()
                    
                    val monthTxns = allTransactionsList.filter { 
                        it.transaction.date >= startDate && it.transaction.date <= endDate &&
                        (it.transaction.accountId == accountId || it.transaction.toAccountId == accountId)
                    }
                    
                    for (day in 0 until daysInMonth) {
                        val d = start.plusDays(day.toLong())
                        val dStr = d.format(DateTimeFormatter.ISO_DATE)
                        val dayTxns = monthTxns.filter { it.transaction.date == dStr }
                        
                        currentBal += dayTxns.sumOf { t ->
                            if (t.transaction.toAccountId == accountId) t.transaction.amount
                            else if (t.transaction.accountId == accountId) {
                                if (t.categoryType == "income") t.transaction.amount else -t.transaction.amount
                            } else 0.0
                        }
                        trend.add(d.format(DateTimeFormatter.ofPattern("dd MMM")) to currentBal)
                    }
                    trend
                } else if (type == "Networth") {
                    val start = LocalDate.parse(startDate)
                    val end = LocalDate.parse(endDate)
                    
                    val txnsBefore = allTransactionsList.filter { it.transaction.date < startDate }
                    val openingBalancesSum = allAccountsList.sumOf { it.openingBalance }
                    
                    var currentNetPosition = openingBalancesSum + txnsBefore.sumOf { t ->
                        if (t.transaction.toAccountId != null) 0.0 
                        else if (t.categoryType == "income") t.transaction.amount 
                        else if (t.categoryType == "expense") -t.transaction.amount 
                        else 0.0
                    }
                    
                    val daysInMonth = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
                    val trend = mutableListOf<Pair<String, Double>>()
                    val monthTxns = allTransactionsList.filter { it.transaction.date >= startDate && it.transaction.date <= endDate }
                    
                    for (day in 0 until daysInMonth) {
                        val d = start.plusDays(day.toLong())
                        val dStr = d.format(DateTimeFormatter.ISO_DATE)
                        val dayTxns = monthTxns.filter { it.transaction.date == dStr }
                        
                        currentNetPosition += dayTxns.sumOf { t ->
                            if (t.transaction.toAccountId != null) 0.0
                            else if (t.categoryType == "income") t.transaction.amount 
                            else if (t.categoryType == "expense") -t.transaction.amount 
                            else 0.0
                        }
                        trend.add(d.format(DateTimeFormatter.ofPattern("dd MMM")) to currentNetPosition)
                    }
                    trend
                } else emptyList()
            }

            val uncategorizedLabel = stringResource(R.string.label_uncategorized)
            val data = when (type) {
                expenseLabel, incomeLabel -> {
                    val targetType = if (type == expenseLabel) "expense" else "income"
                    val filteredTxns = transactions.filter { 
                        it.categoryType?.lowercase() == targetType && 
                        (categoryFilterIds == null || it.transaction.categoryId in categoryFilterIds!!) 
                    }
                    
                    if (isMainLevelAnalysis) {
                        filteredTxns.groupBy { 
                            val name = it.categoryName ?: uncategorizedLabel
                            if (name.contains(":")) name.split(":").first().trim() else name
                        }.mapValues { it.value.sumOf { t -> t.transaction.amount } }
                        .toList().sortedByDescending { it.second }
                    } else {
                        filteredTxns.groupBy { 
                            val name = it.categoryName ?: uncategorizedLabel
                            if (name.contains(":")) name.split(":").last().trim() else name
                        }.mapValues { it.value.sumOf { t -> t.transaction.amount } }
                        .toList().sortedByDescending { it.second }
                    }
                }
                accountLabel -> {
                    if (accountsSubTab == "Balance") {
                        val onAccountLoanId = majorHeads.find { it.name.contains("On Account", ignoreCase = true) }?.id ?: 6
                        balances.filter { b ->
                            val minor = minorHeads.find { it.id == b.minorHeadId }
                            minor?.majorHeadId != onAccountLoanId && !b.name.equals("On Account", ignoreCase = true)
                        }.map { it.name to it.balance }.sortedByDescending { it.second }
                    } else if (accountsSubTab == "BTrend") {
                        bTrendData
                    } else if (accountsSubTab == "Spending" && analysisSelectedAccountId != null) {
                        transactions.filter { it.transaction.accountId == analysisSelectedAccountId && it.transaction.categoryId != null }
                            .groupBy { it.categoryName ?: uncategorizedLabel }
                            .mapValues { entry -> 
                                entry.value.sumOf { if (it.categoryType == "income") it.transaction.amount else -it.transaction.amount } 
                            }
                            .toList().sortedByDescending { Math.abs(it.second) }
                    } else if (accountsSubTab == "Source" && analysisSelectedAccountId != null) {
                        transactions.filter { (it.transaction.accountId == analysisSelectedAccountId || it.transaction.toAccountId == analysisSelectedAccountId) && it.transaction.categoryId == null }
                            .groupBy { if (it.transaction.accountId == analysisSelectedAccountId) it.toAccountName ?: stringResource(R.string.label_other) else it.accountName }
                            .mapValues { entry ->
                                entry.value.sumOf {
                                    if (it.transaction.toAccountId == analysisSelectedAccountId) it.transaction.amount
                                    else -it.transaction.amount
                                }
                            }
                            .toList().sortedByDescending { Math.abs(it.second) }
                    } else {
                        emptyList()
                    }
                }
                tagsLabel -> tags.map { tag ->
                    val total = transactions.filter { t -> t.transaction.tags?.split(",")?.contains(tag.id.toString()) == true }.sumOf { it.transaction.amount }
                    tag.name to total
                }.filter { it.second > 0 }.sortedByDescending { it.second }
                merchantsLabel -> {
                    transactions.filter { it.transaction.merchantName != null && it.transaction.merchantName!!.isNotBlank() }
                        .groupBy { it.transaction.merchantName!! }
                        .mapValues { it.value.sumOf { t -> t.transaction.amount } }
                        .toList().sortedByDescending { it.second }
                }
                networthLabel -> bTrendData
                onAccountLoanLabel -> {
                    filteredInvoices.groupBy { it.detail.partyName ?: it.detail.accountName }
                        .mapValues { entry -> 
                            entry.value.sumOf { inv -> 
                                val outstanding = inv.detail.transaction.amount - inv.totalCleared
                                if (invoiceStatusFilter == "Cleared") inv.totalCleared else outstanding
                            } 
                        }
                        .toList().sortedByDescending { kotlin.math.abs(it.second) }
                }
                else -> emptyList()
            }

            if (type == "Accounts") {
                ScrollableTabRow(
                    selectedTabIndex = when(accountsSubTab) { "Spending" -> 0; "Source" -> 1; "BTrend" -> 2; else -> 3 },
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    divider = {},
                    edgePadding = 0.dp
                ) {
                    Tab(selected = accountsSubTab == "Spending", onClick = { accountsSubTab = "Spending" }) {
                        Text(stringResource(R.string.label_spending), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Tab(selected = accountsSubTab == "Source", onClick = { accountsSubTab = "Source" }) {
                        Text(stringResource(R.string.label_source), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Tab(selected = accountsSubTab == "BTrend", onClick = { accountsSubTab = "BTrend" }) {
                        Text(stringResource(R.string.label_btrend), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Tab(selected = accountsSubTab == "Balance", onClick = { accountsSubTab = "Balance" }) {
                        Text(stringResource(R.string.label_balance), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }

                if (accountsSubTab != "Balance") {
                    var accountMenuExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        OutlinedButton(
                            onClick = { accountMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(currentAccountName)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = accountMenuExpanded, onDismissRequest = { accountMenuExpanded = false }) {
                            allAccountsList.forEach { acc ->
                                DropdownMenuItem(text = { Text(acc.name) }, onClick = {
                                    analysisSelectedAccountId = acc.id
                                    accountMenuExpanded = false
                                })
                            }
                        }
                    }
                }
            }

            if (data.isEmpty() && type == accountLabel && accountsSubTab != "Balance" && analysisSelectedAccountId == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.msg_select_account_analysis), color = Color.Gray)
                }
            } else if (data.isNotEmpty()) {
                val chartData = if (type == "On Account (Loan)" || type == "Accounts" || type == "Networth") {
                    data.map { it.component1() to kotlin.math.abs(it.component2()) }
                } else data

                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    if ((type == "Accounts" && accountsSubTab == "BTrend") || type == "Networth") {
                        LineChart(data = data.map { it.second }, labels = data.map { it.first })
                    } else if (type == "Tags" && isTagBarChartView) {
                        val tagBarData = tags.filter { (it.targetNumber ?: 0.0) > 0.0 }.map { tag ->
                            val total = transactions.filter { t -> t.transaction.tags?.split(",")?.contains(tag.id.toString()) == true }.sumOf { it.transaction.amount }
                            tag to total
                        }
                        TagTargetBarChart(data = tagBarData, viewModel = viewModel)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PieChart(data = chartData, colors = chartColors)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                for (i in 0 until minOf(5, chartData.size)) {
                                    val pair = chartData[i]
                                    val totalVal = chartData.sumOf { it.second }
                                    val pct = if (totalVal != 0.0) (pair.second / totalVal * 100).toInt() else 0
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(8.dp).background(chartColors[i % chartColors.size]))
                                        Text(" $pct%", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (type != onAccountLoanLabel) {
                 Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.msg_no_data), color = Color.Gray)
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (type == onAccountLoanLabel && viewModel.invoiceAgeTrackingEnabled) {
                    if (filteredInvoices.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.msg_no_invoices_matching), color = Color.Gray)
                            }
                        }
                    }

                    if (groupByParty) {
                        val grouped = filteredInvoices.groupBy { it.detail.partyName ?: it.detail.accountName }
                            .mapValues { entry -> 
                                entry.value.sumOf { inv -> 
                                    val outstanding = inv.detail.transaction.amount - inv.totalCleared
                                    if (invoiceStatusFilter == "Cleared") inv.totalCleared else outstanding
                                } 
                            }.toList().sortedByDescending { kotlin.math.abs(it.second) }
                        
                        items(grouped) { (name, amount) ->
                            Surface(
                                color = (if (amount >= 0) Color(0xFF4CAF50) else Color.Red).copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    showDetailList = name
                                }
                            ) {
                                ListItem(
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    headlineContent = { Text(name, fontWeight = FontWeight.Bold) },
                                    trailingContent = {
                                        Text(viewModel.formatAmount(amount), color = if (amount >= 0) Color(0xFF4CAF50) else Color.Red, fontWeight = FontWeight.Bold)
                                    }
                                )
                            }
                        }
                    } else {
                        items(filteredInvoices) { inv ->
                            val outstanding = inv.detail.transaction.amount - inv.totalCleared
                            val dueDate = try { LocalDate.parse(inv.detail.transaction.date).plusDays(inv.detail.transaction.dueDays?.toLong() ?: 0) } catch(e: Exception) { null }
                            val isCleared = outstanding <= 0
                            
                            Surface(
                                color = (if (isCleared) Color.Gray else MaterialTheme.colorScheme.primaryContainer).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                    viewModel.selectedTransactionDetail = inv.detail
                                }
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(stringResource(R.string.label_inv_item, inv.detail.transaction.invoiceNumber ?: "N/A", ""), fontWeight = FontWeight.Bold)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isCleared) {
                                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(stringResource(R.string.label_cleared_status), style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                                            } else {
                                                Text(stringResource(R.string.label_open_status), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(8.dp))
                                                Text(viewModel.formatAmount(outstanding), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    Text(stringResource(R.string.label_party_colon) + (inv.detail.partyName ?: inv.detail.accountName), style = MaterialTheme.typography.labelSmall)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.label_date_colon) + inv.detail.transaction.date, style = MaterialTheme.typography.labelSmall)
                                        if (dueDate != null) {
                                            Text(stringResource(R.string.label_due_colon) + dueDate, style = MaterialTheme.typography.labelSmall, color = if (!isCleared && dueDate.isBefore(LocalDate.now())) Color.Red else Color.Unspecified)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if ((type == accountLabel && accountsSubTab == "BTrend") || type == networthLabel) {
                    items(data.reversed()) { pair ->
                        val dateLabel = pair.component1()
                        val amount = pair.component2()
                        Surface(
                            color = (if (amount >= 0) Color(0xFF4CAF50) else Color.Red).copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(dateLabel) },
                                trailingContent = { 
                                    Text(
                                        viewModel.formatAmount(amount), 
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (amount >= 0) Color(0xFF4CAF50) else Color.Red
                                    ) 
                                }
                            )
                        }
                    }
                } else {
                    items(data) { pair ->
                        val name = pair.component1()
                        val amount = pair.component2()
                        val index = data.indexOfFirst { it.component1() == name }
                        val itemColor = if (type == "Accounts") {
                            if (amount >= 0) Color(0xFF4CAF50) else Color.Red
                        } else if (type == "On Account (Loan)" || type == "Accounts") {
                            if (amount >= 0) Color(0xFF4CAF50) else Color.Red
                        } else MaterialTheme.colorScheme.onSurface

                        val itemIcon = when (type) {
                            expenseLabel, incomeLabel -> allCategories.find { it.name == name }?.icon ?: "📁"
                            accountLabel, onAccountLoanLabel -> balances.find { it.name == name }?.icon ?: "🏦"
                            else -> "📁"
                        }

                        Surface(
                            color = itemColor.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { 
                                    if (type == tagsLabel) {
                                        showTagDetailList = tags.find { it.name == name }?.id
                                    } else if (type == accountLabel && accountsSubTab != "Balance") {
                                        showDetailList = name
                                    } else if (type == merchantsLabel) {
                                        showDetailList = "MERCHANT:$name"
                                    } else {
                                        showDetailList = name 
                                    }
                                }
                        ) {
                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(12.dp).background(chartColors[index % chartColors.size]))
                                        Spacer(Modifier.width(8.dp))
                                        Text(text = "$itemIcon $name", style = MaterialTheme.typography.bodyLarge)
                                    }
                                },
                                trailingContent = { 
                                    Text(
                                        viewModel.formatAmount(amount), 
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = itemColor
                                    ) 
                                }
                            )
                        }
                    }
                }
            }
            if (type == tagsLabel) {
                Button(onClick = { onNavigate("summary_by_tags") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.label_view_all_txns_tag))
                }
            }
        }
    }

    if (showFilter) {
        DateRangeFilterDialog(onDismiss = { showFilter = false }, onApply = { s, e -> startDate = s; endDate = e; showFilter = false })
    }

    if (showAdvancedFilterDialog) {
        val onAccountLoanId = majorHeads.find { it.name.contains("On Account", ignoreCase = true) }?.id ?: 6
        val partyAccounts = allAccountsList.filter { a -> minorHeads.find { it.id == a.minorHeadId }?.majorHeadId == onAccountLoanId }
        val allTags by viewModel.getAllTags().collectAsState(initial = emptyList())

        AlertDialog(
            onDismissRequest = { showAdvancedFilterDialog = false },
            title = { Text(stringResource(R.string.title_filter_analysis)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = groupByParty, onCheckedChange = { groupByParty = it })
                        Text(stringResource(R.string.label_group_by_party))
                    }

                    Column {
                        Text(stringResource(R.string.label_status), style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("All", "Open", "Cleared").forEach { s ->
                                val label = when(s) {
                                    "All" -> stringResource(R.string.label_all)
                                    "Open" -> stringResource(R.string.label_open)
                                    else -> stringResource(R.string.label_cleared)
                                }
                                FilterChip(
                                    selected = invoiceStatusFilter == s, 
                                    onClick = { invoiceStatusFilter = s }, 
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterByParty, onCheckedChange = { filterByParty = it })
                            Text(stringResource(R.string.label_by_party_name))
                        }
                        if (filterByParty) {
                            Box(modifier = Modifier.height(150.dp).padding(start = 24.dp)) {
                                LazyColumn {
                                    items(partyAccounts) { acc ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                if (acc.id in selectedAnalysisPartyIds) selectedAnalysisPartyIds.remove(acc.id)
                                                else selectedAnalysisPartyIds.add(acc.id)
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = acc.id in selectedAnalysisPartyIds, onCheckedChange = {
                                                if (it) selectedAnalysisPartyIds.add(acc.id)
                                                else selectedAnalysisPartyIds.remove(acc.id)
                                            })
                                            Text(acc.name, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterByOverdue, onCheckedChange = { filterByOverdue = it })
                            Text(stringResource(R.string.label_overdue))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterByAmount, onCheckedChange = { filterByAmount = it })
                            Text(stringResource(R.string.label_amount_range))
                        }
                        if (filterByAmount) {
                            Row(modifier = Modifier.padding(start = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = minAmtFilter, onValueChange = { minAmtFilter = it }, label = { Text(stringResource(R.string.label_min)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                OutlinedTextField(value = maxAmtFilter, onValueChange = { maxAmtFilter = it }, label = { Text(stringResource(R.string.label_max)) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterByDueInXDays, onCheckedChange = { filterByDueInXDays = it })
                            Text(stringResource(R.string.label_due_in_x_days))
                        }
                        if (filterByDueInXDays) {
                            OutlinedTextField(value = dueInXDaysFilter, onValueChange = { dueInXDaysFilter = it }, label = { Text(stringResource(R.string.label_no_of_days)) }, modifier = Modifier.padding(start = 24.dp).fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = filterByWasDueYDays, onCheckedChange = { filterByWasDueYDays = it })
                            Text(stringResource(R.string.label_was_due_y_days))
                        }
                        if (filterByWasDueYDays) {
                            OutlinedTextField(value = wasDueYDaysFilter, onValueChange = { wasDueYDaysFilter = it }, label = { Text(stringResource(R.string.label_no_of_days)) }, modifier = Modifier.padding(start = 24.dp).fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }

                        var showCatsInFilter by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = categoryFilterIds != null, onCheckedChange = { if (it) showCatsInFilter = true else categoryFilterIds = null })
                            Text(stringResource(R.string.label_category))
                        }
                        if (categoryFilterIds != null || showCatsInFilter) {
                             Box(modifier = Modifier.height(150.dp).padding(start = 24.dp)) {
                                LazyColumn {
                                    items(allCategories) { cat ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                val current = categoryFilterIds?.toMutableSet() ?: mutableSetOf()
                                                if (cat.id in current) current.remove(cat.id)
                                                else current.add(cat.id)
                                                categoryFilterIds = if (current.isEmpty()) null else current
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = cat.id in (categoryFilterIds ?: emptySet()), onCheckedChange = {
                                                val current = categoryFilterIds?.toMutableSet() ?: mutableSetOf()
                                                if (it) current.add(cat.id)
                                                else current.remove(cat.id)
                                                categoryFilterIds = if (current.isEmpty()) null else current
                                            })
                                            Text(cat.name, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }

                        var showTagsInFilter by remember { mutableStateOf(false) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = tagFilterIds != null, onCheckedChange = { if (it) showTagsInFilter = true else tagFilterIds = null })
                            Text(stringResource(R.string.label_tags))
                        }
                        if (tagFilterIds != null || showTagsInFilter) {
                             Box(modifier = Modifier.height(150.dp).padding(start = 24.dp)) {
                                LazyColumn {
                                    items(allTags) { tag ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                val current = tagFilterIds?.toMutableSet() ?: mutableSetOf()
                                                if (tag.id in current) current.remove(tag.id)
                                                else current.add(tag.id)
                                                tagFilterIds = if (current.isEmpty()) null else current
                                            },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(checked = tag.id in (tagFilterIds ?: emptySet()), onCheckedChange = {
                                                val current = tagFilterIds?.toMutableSet() ?: mutableSetOf()
                                                if (it) current.add(tag.id)
                                                else current.remove(tag.id)
                                                tagFilterIds = if (current.isEmpty()) null else current
                                            })
                                            Text(tag.name, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showAdvancedFilterDialog = false }) { Text(stringResource(R.string.btn_apply)) } },
            dismissButton = {
                TextButton(onClick = {
                    filterByParty = false; filterByOverdue = false; filterByAmount = false; filterByDueInXDays = false; filterByWasDueYDays = false
                    selectedAnalysisPartyIds.clear(); minAmtFilter = ""; maxAmtFilter = ""; dueInXDaysFilter = ""; wasDueYDaysFilter = ""
                    categoryFilterIds = null; tagFilterIds = null; groupByParty = false; invoiceStatusFilter = "Open"
                    showAdvancedFilterDialog = false
                }) {
                    Text(stringResource(R.string.btn_clear_all), color = Color.Red)
                }
            }
        )
    }
}

@Composable
fun TransactionsView(viewModel: ExpenseViewModel, onNavigate: (String) -> Unit, onOpenDrawer: () -> Unit) {
    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else {
        Column {
            TransactionHistoryView(viewModel, onOpenDrawer = onOpenDrawer)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsTab(viewModel: ExpenseViewModel, onNavigate: (String) -> Unit, onOpenDrawer: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, stringResource(R.string.menu_home))
            }
            Text(stringResource(R.string.title_budgets), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        BudgetComparisonScreen(viewModel = viewModel, onBack = {}, isTab = true)
    }
}
