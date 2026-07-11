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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        Text("FinTrack Menu", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                        Divider()
                        NavigationDrawerItem(
                            label = { Text("Home") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; selectedTab = "home" },
                            icon = { Icon(Icons.Default.Home, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("View Summary") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("summary") },
                            icon = { Icon(Icons.Default.Assessment, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Credit Cards") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("credit_cards") },
                            icon = { Icon(Icons.Default.CreditCard, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Loan & Subscriptions") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("subscriptions") },
                            icon = { Icon(Icons.Default.CardMembership, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Categories & Accounts") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("manage_categories") },
                            icon = { Icon(Icons.Default.Category, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Templates") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("templates") },
                            icon = { Icon(Icons.Default.Dashboard, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Tags") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("manage_tags") },
                            icon = { Icon(Icons.Default.Label, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Budgets") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("manage_budgets") },
                            icon = { Icon(Icons.Default.AccountBalanceWallet, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Notes") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("notes") },
                            icon = { Icon(Icons.Default.Notes, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Permissions") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("permissions") },
                            icon = { Icon(Icons.Default.Security, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Settings") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("settings") },
                            icon = { Icon(Icons.Default.Settings, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Database") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("database") },
                            icon = { Icon(Icons.Default.Storage, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Contact Us") },
                            selected = false,
                            onClick = { scope.launch { drawerState.close() }; onNavigate("contact") },
                            icon = { Icon(Icons.Default.Email, null) }
                        )
                    }
                    
                    Text(
                        "v1.0.13",
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
                                    placeholder = { Text("Search transactions...") },
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    singleLine = true,
                                    leadingIcon = {
                                        IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                            Icon(Icons.Default.ArrowBack, "Back")
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
                                        TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = true)
                                    }
                                    Divider(modifier = Modifier.padding(vertical = 4.dp))
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

                if (!isSearchActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp)
                            .navigationBarsPadding(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        FloatingBottomNav(
                            selectedTab = selectedTab,
                            onTabChange = { selectedTab = it },
                            tabOrder = viewModel.bottomTabOrder
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FloatingActionButton(
                            onClick = { isSearchActive = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        
                        val fabLabel = when (selectedTab) {
                            "budgets" -> "Add Budget"
                            else -> "Add Transaction"
                        }
                        
                        val isServerRunning by viewModel.isServerRunning.collectAsState()
                        val snackbarHostState = remember { SnackbarHostState() }
                        val scope = rememberCoroutineScope()

                        FloatingActionButton(
                            onClick = { 
                                if (isServerRunning) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Editing disabled while local server is active.")
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
    tabOrder: List<String>
) {
    Surface(
        modifier = Modifier
            .wrapContentWidth()
            .height(56.dp),
        shape = androidx.compose.foundation.shape.CircleShape,
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            tabOrder.forEach { tabKey ->
                val isSelected = selectedTab == tabKey
                val (label, icon) = when (tabKey) {
                    "home" -> "Home" to Icons.Default.Home
                    "analysis" -> "Analysis" to Icons.Default.PieChart
                    "transactions" -> "Transactns" to Icons.Default.List
                    "budgets" -> "Budgets" to Icons.Default.AccountBalanceWallet
                    else -> "" to Icons.Default.Home
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onTabChange(tabKey) }
                        .padding(horizontal = 12.dp),
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
                            Spacer(Modifier.width(8.dp))
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

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else if (showTransactionListType != null) {
        BackHandler { showTransactionListType = null }
        TransactionListOverlay(
            title = showTransactionListType!!.replaceFirstChar { it.uppercase() },
            transactions = transactions.filter { it.categoryType == showTransactionListType },
            viewModel = viewModel,
            onBack = { showTransactionListType = null }
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
                            Text("${alert.accountName} Due: ${alert.dueDate}", style = MaterialTheme.typography.labelLarge)
                            Text("Amount Payable: ${viewModel.formatAmount(kotlin.math.abs(alert.amount))}", style = MaterialTheme.typography.bodySmall)
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
                            Text("${alert.subName} Due: ${alert.dueDate}", style = MaterialTheme.typography.labelLarge)
                            Text(
                                (if(alert.isTransfer) "Recurring Transfer: " else "Amount Due: ") + viewModel.formatAmount(alert.amount), 
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
                        Text("There is a draft transaction pending to be recorded", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                    Text("FinTrack", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
                Row {
                    IconButton(onClick = { viewModel.generateFinancialInsights() }) {
                        if (viewModel.isGeneratingInsights) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, "Insights")
                        }
                    }
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.DateRange, "Filter")
                    }
                }
            }
            
            Text("$startDate to $endDate", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

            if (viewModel.showInsightsOverlay && viewModel.financialInsights.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Smart Insights", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(insight.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(insight.description, style = MaterialTheme.typography.bodyMedium)
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
                            Text("Expense", style = MaterialTheme.typography.labelMedium, color = Color.Red)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(viewModel.formatAmount(curExp), style = MaterialTheme.typography.titleLarge, color = Color.Red, fontWeight = FontWeight.Bold)
                                if (curExp > prevExp) {
                                    Text(" (↑)", style = MaterialTheme.typography.titleLarge, color = Color.Red, fontWeight = FontWeight.ExtraBold)
                                } else if (curExp < prevExp) {
                                    Text(" (↓)", style = MaterialTheme.typography.titleLarge, color = Color.Red, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Text("prev month: ${viewModel.formatAmount(prevExp)}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth().clickable { showTransactionListType = "income" }) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Income", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4CAF50))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(viewModel.formatAmount(curInc), style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                if (curInc > prevInc) {
                                    Text(" (↑)", style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.ExtraBold)
                                } else if (curInc < prevInc) {
                                    Text(" (↓)", style = MaterialTheme.typography.titleLarge, color = Color(0xFF4CAF50), fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Text("prev month: ${viewModel.formatAmount(prevInc)}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    }

                    Spacer(Modifier.height(8.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net", fontWeight = FontWeight.Bold)
                        Text(viewModel.formatAmount(curInc - curExp), fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Unnecessary gap removed
            val balances by viewModel.getAccountBalances(endDate).collectAsState(initial = emptyList())
            val netPosition = balances.sumOf { it.balance }
            var isNetPositionVisible by remember { mutableStateOf(!viewModel.tapToShowNetPosition) }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp), 
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Net Position", 
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
            
            Text("Top Expenses", style = MaterialTheme.typography.titleMedium)
            val topExpenses = transactions.filter { it.categoryType == "expense" }
                .groupBy { it.categoryName ?: "Uncategorized" }
                .mapValues { it.value.sumOf { t -> t.transaction.amount } }
                .toList()
                .sortedByDescending { it.second }
                .take(4)
            
            if (topExpenses.isNotEmpty()) {
                Box(modifier = Modifier.clickable { onNavigateToAnalysis("Expense", startDate, endDate) }) {
                    ExpensePieChart(topExpenses, viewModel)
                }
            } else {
                Text("No expenses in this period", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }

            Spacer(Modifier.height(8.dp))

            if (viewModel.dashboardAccountIds.isNotEmpty()) {
                val selectedBalances = balances.filter { it.id in viewModel.dashboardAccountIds }
                if (selectedBalances.isNotEmpty()) {
                    Text("Dashboard Accounts", style = MaterialTheme.typography.titleMedium)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
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
                    Text("Budget Performance", style = MaterialTheme.typography.titleMedium)
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
                                    Text(budget.name ?: "Budget", style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
        DateRangeFilterDialog(onDismiss = { showFilter = false }, onApply = { s, e -> startDate = s; endDate = e; showFilter = false })
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
        Text("Prev: ${viewModel.formatAmount(previous)}", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
    var type by remember { mutableStateOf(initialType ?: "Expense") }
    var month by remember { mutableStateOf(LocalDate.now()) }
    var startDate by remember { mutableStateOf(initialDateRange?.first ?: month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(initialDateRange?.second ?: month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)) }
    var showFilter by remember { mutableStateOf(false) }
    var categoryFilterIds by remember { mutableStateOf<Set<Int>?>(null) }
    var showCategoryFilterDialog by remember { mutableStateOf(false) }
    var isMainLevelAnalysis by remember { mutableStateOf(false) }
    
    var accountsSubTab by remember { mutableStateOf("Balance") }
    var analysisSelectedAccountId by remember { mutableStateOf<Int?>(null) }
    
    val allTransactionsList by viewModel.allTransactions.collectAsState(initial = emptyList())
    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    val balances by viewModel.getAccountBalances(endDate).collectAsState(initial = emptyList())
    val tags by viewModel.getAllTags().collectAsState(initial = emptyList())
    val allAccountsList by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val minorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())
    val majorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val allCategories by viewModel.getEnabledCategories().collectAsState(initial = emptyList())

    LaunchedEffect(allAccountsList) {
        if (analysisSelectedAccountId == null && allAccountsList.isNotEmpty()) {
            analysisSelectedAccountId = allAccountsList.first().id
        }
    }

    var showDetailList by remember { mutableStateOf<String?>(null) }
    var showTagDetailList by remember { mutableStateOf<Int?>(null) }
    
    val currentAccountName = remember(analysisSelectedAccountId, allAccountsList) {
        allAccountsList.find { it.id == analysisSelectedAccountId }?.name ?: "Select Account"
    }

    if (viewModel.selectedTransactionDetail != null) {
        BackHandler { viewModel.selectedTransactionDetail = null }
        AddTransactionScreen(viewModel = viewModel, onBack = { viewModel.selectedTransactionDetail = null }, onNavigate = onNavigate, readOnly = true)
    } else if (showDetailList != null) {
        BackHandler { showDetailList = null }
        val filteredList = remember(transactions, type, accountsSubTab, analysisSelectedAccountId, showDetailList, isMainLevelAnalysis) {
            if (type == "Accounts") {
                // ... (Existing accounts logic remains unchanged)
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
            } else if (type == "Expense" || type == "Income") {
                transactions.filter { 
                    val catName = it.categoryName ?: "Uncategorized"
                    if (isMainLevelAnalysis) {
                        val mainPart = if (catName.contains(":")) catName.split(":").first().trim() else catName
                        mainPart == showDetailList
                    } else {
                        val minorPart = if (catName.contains(":")) catName.split(":").last().trim() else catName
                        minorPart == showDetailList
                    }
                }
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
                        Icon(Icons.Default.Menu, "Menu")
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(type, style = MaterialTheme.typography.titleLarge)
                            Icon(Icons.Default.ArrowDropDown, "")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Expense") }, onClick = { type = "Expense"; expanded = false })
                            DropdownMenuItem(text = { Text("Income") }, onClick = { type = "Income"; expanded = false })
                            DropdownMenuItem(text = { Text("Accounts") }, onClick = { type = "Accounts"; expanded = false })
                            DropdownMenuItem(text = { Text("Tags") }, onClick = { type = "Tags"; expanded = false })
                            DropdownMenuItem(text = { Text("On Account (Loan)") }, onClick = { type = "On Account (Loan)"; expanded = false })
                        }
                    }
                    if (type == "Expense" || type == "Income") {
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = categoryFilterIds != null,
                            onClick = { showCategoryFilterDialog = true },
                            label = { Text("Filter") },
                            leadingIcon = { Icon(Icons.Default.FilterAlt, null, modifier = Modifier.size(18.dp)) }
                        )
                        Spacer(Modifier.width(4.dp))
                        IconButton(onClick = { isMainLevelAnalysis = !isMainLevelAnalysis }) {
                            Icon(
                                if (isMainLevelAnalysis) Icons.Default.Layers else Icons.Default.LayersClear,
                                contentDescription = if (isMainLevelAnalysis) "Main Level" else "Minor Level",
                                tint = if (isMainLevelAnalysis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                IconButton(onClick = { showFilter = true }) { Icon(Icons.Default.FilterList, "") }
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
                        trend.add(d.dayOfMonth.toString() to currentBal)
                    }
                    trend
                } else emptyList()
            }

            val data = when (type) {
                "Expense", "Income" -> {
                    val filteredTxns = transactions.filter { 
                        it.categoryType?.lowercase() == type.lowercase() && 
                        (categoryFilterIds == null || it.transaction.categoryId in categoryFilterIds!!) 
                    }
                    
                    if (isMainLevelAnalysis) {
                        filteredTxns.groupBy { 
                            val name = it.categoryName ?: "Uncategorized"
                            if (name.contains(":")) name.split(":").first().trim() else name
                        }.mapValues { it.value.sumOf { t -> t.transaction.amount } }
                        .toList().sortedByDescending { it.second }
                    } else {
                        filteredTxns.groupBy { 
                            val name = it.categoryName ?: "Uncategorized"
                            if (name.contains(":")) name.split(":").last().trim() else name
                        }.mapValues { it.value.sumOf { t -> t.transaction.amount } }
                        .toList().sortedByDescending { it.second }
                    }
                }
                "Accounts" -> {
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
                            .groupBy { it.categoryName ?: "Uncategorized" }
                            .mapValues { entry -> 
                                entry.value.sumOf { if (it.categoryType == "income") it.transaction.amount else -it.transaction.amount } 
                            }
                            .toList().sortedByDescending { Math.abs(it.second) }
                    } else if (accountsSubTab == "Source" && analysisSelectedAccountId != null) {
                        transactions.filter { (it.transaction.accountId == analysisSelectedAccountId || it.transaction.toAccountId == analysisSelectedAccountId) && it.transaction.categoryId == null }
                            .groupBy { if (it.transaction.accountId == analysisSelectedAccountId) it.toAccountName ?: "Other" else it.accountName }
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
                "Tags" -> tags.map { tag ->
                    val total = transactions.filter { t -> t.transaction.tags?.split(",")?.contains(tag.id.toString()) == true }.sumOf { it.transaction.amount }
                    tag.name to total
                }.filter { it.second > 0 }.sortedByDescending { it.second }
                "On Account (Loan)" -> {
                    val onAccountLoanId = majorHeads.find { it.name.contains("On Account", ignoreCase = true) }?.id ?: 6
                    balances.filter { b ->
                        val minor = minorHeads.find { it.id == b.minorHeadId }
                        minor?.majorHeadId == onAccountLoanId
                    }.map { it.name to it.balance }.sortedByDescending { kotlin.math.abs(it.second) }
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
                        Text("Spending", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Tab(selected = accountsSubTab == "Source", onClick = { accountsSubTab = "Source" }) {
                        Text("Source", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Tab(selected = accountsSubTab == "BTrend", onClick = { accountsSubTab = "BTrend" }) {
                        Text("BTrend", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    Tab(selected = accountsSubTab == "Balance", onClick = { accountsSubTab = "Balance" }) {
                        Text("Balance", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge)
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

            if (data.isEmpty() && type == "Accounts" && accountsSubTab != "Balance" && analysisSelectedAccountId == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Please select an account to analyze", color = Color.Gray)
                }
            } else if (data.isNotEmpty()) {
                val chartData = if (type == "On Account (Loan)" || type == "Accounts") {
                    data.map { it.first to kotlin.math.abs(it.second) }
                } else data

                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    if (type == "Accounts" && accountsSubTab == "BTrend") {
                        LineChart(data = data.map { it.second }, labels = data.map { it.first })
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PieChart(data = chartData, colors = chartColors)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                chartData.take(5).forEachIndexed { i, pair ->
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
            } else if (type != "Accounts" || accountsSubTab == "Balance" || accountsSubTab == "BTrend") {
                 Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No data for the selected period", color = Color.Gray)
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (type == "Accounts" && accountsSubTab == "BTrend") {
                    items(data.reversed()) { (day, amount) ->
                        ListItem(
                            headlineContent = { Text("Day $day") },
                            trailingContent = { 
                                Text(
                                    viewModel.formatAmount(amount), 
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (amount >= 0) Color(0xFF4CAF50) else Color.Red
                                ) 
                            }
                        )
                        Divider()
                    }
                } else {
                    items(data) { (name, amount) ->
                        val index = data.indexOfFirst { it.first == name }
                        val itemColor = if (type == "Accounts") {
                            if (amount >= 0) Color(0xFF4CAF50) else Color.Red
                        } else if (type == "On Account (Loan)" || type == "Accounts") {
                            if (amount >= 0) Color(0xFF4CAF50) else Color.Red
                        } else MaterialTheme.colorScheme.onSurface

                        ListItem(
                            headlineContent = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(12.dp).background(chartColors[index % chartColors.size]))
                                    Spacer(Modifier.width(8.dp))
                                    Text(name, style = MaterialTheme.typography.bodyLarge)
                                }
                            },
                            trailingContent = { 
                                Text(
                                    viewModel.formatAmount(amount), 
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = itemColor
                                ) 
                            },
                            modifier = Modifier.clickable { 
                                if (type == "Tags") {
                                    showTagDetailList = tags.find { it.name == name }?.id
                                } else if (type == "Accounts" && accountsSubTab != "Balance") {
                                    showDetailList = name
                                } else {
                                    showDetailList = name 
                                }
                            }
                        )
                        Divider()
                    }
                }
            }
            if (type == "Tags") {
                Button(onClick = { onNavigate("summary_by_tags") }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("View All Transactions by Tag")
                }
            }
        }
    }

    if (showFilter) {
        DateRangeFilterDialog(onDismiss = { showFilter = false }, onApply = { s, e -> startDate = s; endDate = e; showFilter = false })
    }

    if (showCategoryFilterDialog) {
        var searchText by remember { mutableStateOf("") }
        val filteredCats = allCategories.filter { 
            it.type.equals(type, ignoreCase = true) && 
            it.name.contains(searchText, ignoreCase = true) 
        }
        val tempSelectedIds = remember { mutableStateListOf<Int>().apply { categoryFilterIds?.let { addAll(it) } } }

        AlertDialog(
            onDismissRequest = { showCategoryFilterDialog = false },
            title = { Text("Filter Categories") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Search Category") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    
                    Box(Modifier.height(300.dp)) {
                        if (filteredCats.isEmpty() && searchText.isNotEmpty()) {
                            Text("No categories found matching \"$searchText\"", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                        } else {
                            LazyColumn {
                                items(filteredCats) { cat ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            if (cat.id in tempSelectedIds) tempSelectedIds.remove(cat.id)
                                            else tempSelectedIds.add(cat.id)
                                        }.padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = cat.id in tempSelectedIds,
                                            onCheckedChange = {
                                                if (cat.id in tempSelectedIds) tempSelectedIds.remove(cat.id)
                                                else tempSelectedIds.add(cat.id)
                                            }
                                        )
                                        Text(cat.name)
                                    }
                                    Divider(modifier = Modifier.alpha(0.3f))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { categoryFilterIds = null; showCategoryFilterDialog = false }) {
                        Text("Remove Filter", color = Color.Red)
                    }
                    Button(onClick = { 
                        categoryFilterIds = if (tempSelectedIds.isEmpty()) null else tempSelectedIds.toSet()
                        showCategoryFilterDialog = false 
                    }) {
                        Text("Apply")
                    }
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
                Icon(Icons.Default.Menu, "Menu")
            }
            Text("Budgets", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        BudgetComparisonScreen(viewModel = viewModel, onBack = {}, isTab = true)
    }
}
