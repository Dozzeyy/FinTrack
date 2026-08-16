/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.openapps.fintrack.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ColumnGrouping(val label: String) {
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    HALF_YEARLY("Half Yearly"),
    YEARLY("Yearly")
}

data class GroupedColumn(
    val label: String,
    val months: List<String>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PerformanceScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit
) {
    var startDate by remember { mutableStateOf(LocalDate.now().minusMonths(6).withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var showFilter by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(ColumnGrouping.MONTHLY) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedBudgetId by remember { mutableIntStateOf(-1) }
    var selectedCategoryId by remember { mutableIntStateOf(-1) }

    val performanceData by viewModel.getPerformanceData(startDate, endDate).collectAsState(initial = PerformanceDashboardData(emptyList(), emptyList(), emptyList(), emptyList()))
    val metrics = performanceData.metrics
    val budgets = performanceData.budgetSummaries
    val budgetTrends = performanceData.budgetTrends
    val categoryTrends = performanceData.categoryTrends

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Performance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    var showGroupingMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showGroupingMenu = true }) {
                            Icon(Icons.Default.ViewColumn, "Column Grouping")
                        }
                        DropdownMenu(expanded = showGroupingMenu, onDismissRequest = { showGroupingMenu = false }) {
                            ColumnGrouping.entries.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(g.label) },
                                    onClick = { grouping = g; showGroupingMenu = false },
                                    trailingIcon = { if (grouping == g) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.DateRange, "Select Period")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Numbers") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Charts") })
            }

            if (showFilter) {
                DateRangeFilterDialog(onDismiss = { showFilter = false }, onApply = { s, e -> startDate = s; endDate = e; showFilter = false })
            }

            if (selectedTab == 0) {
                NumbersView(metrics, budgets, budgetTrends, categoryTrends, selectedBudgetId, selectedCategoryId, { selectedBudgetId = it }, { selectedCategoryId = it }, grouping, viewModel)
            } else {
                ChartsView(metrics, budgets, grouping, viewModel)
            }
        }
    }
}

fun getGroupedColumns(months: List<String>, grouping: ColumnGrouping): List<GroupedColumn> {
    if (grouping == ColumnGrouping.MONTHLY) {
        return months.map { m ->
            val label = try {
                LocalDate.parse("$m-01").format(DateTimeFormatter.ofPattern("MMM-yy"))
            } catch (e: Exception) { m }
            GroupedColumn(label, listOf(m))
        }
    }

    val groups = mutableListOf<GroupedColumn>()
    val sortedMonths = months.sorted()
    if (sortedMonths.isEmpty()) return emptyList()

    var currentGroupMonths = mutableListOf<String>()
    var currentGroupKey = ""

    for (m in sortedMonths) {
        val date = try { LocalDate.parse("$m-01") } catch(e: Exception) { null } ?: continue
        val key = when (grouping) {
            ColumnGrouping.QUARTERLY -> "${date.year}-Q${(date.monthValue - 1) / 3 + 1}"
            ColumnGrouping.HALF_YEARLY -> "${date.year}-H${if (date.monthValue <= 6) 1 else 2}"
            ColumnGrouping.YEARLY -> "${date.year}"
            else -> m
        }

        if (currentGroupKey == "") {
            currentGroupKey = key
            currentGroupMonths.add(m)
        } else if (currentGroupKey == key) {
            currentGroupMonths.add(m)
        } else {
            groups.add(createGroupedColumn(currentGroupMonths, grouping))
            currentGroupKey = key
            currentGroupMonths = mutableListOf(m)
        }
    }
    if (currentGroupMonths.isNotEmpty()) {
        groups.add(createGroupedColumn(currentGroupMonths, grouping))
    }

    return groups
}

private fun createGroupedColumn(months: List<String>, grouping: ColumnGrouping): GroupedColumn {
    val first = LocalDate.parse("${months.first()}-01")
    val last = LocalDate.parse("${months.last()}-01")
    val label = if (months.size == 1) {
        first.format(DateTimeFormatter.ofPattern("MMM-yy"))
    } else {
        val firstPart = first.format(DateTimeFormatter.ofPattern("MMM"))
        val lastPart = last.format(DateTimeFormatter.ofPattern("MMM-yy"))
        "$firstPart-$lastPart"
    }
    return GroupedColumn(label, months)
}

@Composable
fun NumbersView(
    metrics: List<PerformanceMetricData>,
    budgets: List<BudgetPerformanceData>,
    budgetTrends: List<BudgetTrendData>,
    categoryTrends: List<CategoryPerformanceData>,
    selectedBudgetId: Int,
    selectedCategoryId: Int,
    onBudgetSelected: (Int) -> Unit,
    onCategorySelected: (Int) -> Unit,
    grouping: ColumnGrouping,
    viewModel: ExpenseViewModel
) {
    val months = metrics.firstOrNull()?.monthValues?.keys?.sorted() ?: emptyList()
    val displayMonths = months.map { 
        try { LocalDate.parse("$it-01").format(DateTimeFormatter.ofPattern("MMM-yy")) } catch(e: Exception) { it }
    }
    
    val groupedColumns = remember(months, grouping) { getGroupedColumns(months, grouping) }
    
    val monthScrollState = rememberScrollState()
    val budgetSummaryScrollState = rememberScrollState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // --- 1. Budget Performance Section ---
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Budget Performance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                var expanded by remember { mutableStateOf(false) }
                val currentBudgetName = budgets.find { it.budgetId == selectedBudgetId }?.name ?: "All Budgets"
                
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(currentBudgetName)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("All Budgets") }, onClick = { onBudgetSelected(-1); expanded = false })
                        budgets.forEach { b ->
                            DropdownMenuItem(text = { Text(b.name) }, onClick = { onBudgetSelected(b.budgetId); expanded = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (selectedBudgetId == -1) {
            stickyHeader {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 4.dp)) {
                    TableCell("Item", width = 140.dp, isHeader = true)
                    Box(Modifier.horizontalScroll(budgetSummaryScrollState)) {
                        Row {
                            TableCell("Period", width = 100.dp, isHeader = true)
                            TableCell("Budget", width = 100.dp, isHeader = true)
                            TableCell("Actual", width = 100.dp, isHeader = true)
                            TableCell("Var Amt", width = 100.dp, isHeader = true)
                            TableCell("Var %", width = 80.dp, isHeader = true)
                        }
                    }
                }
            }
            items(budgets) { b ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    TableCell(b.name, width = 140.dp, isHeader = true)
                    Box(Modifier.horizontalScroll(budgetSummaryScrollState)) {
                        Row {
                            TableCell(b.duration, width = 100.dp)
                            TableCell(viewModel.formatAmount(b.budget), width = 100.dp)
                            TableCell(viewModel.formatAmount(b.actual), width = 100.dp)
                            TableCell(viewModel.formatAmount(b.variance), width = 100.dp, color = if (b.variance >= 0) Color(0xFF4CAF50) else Color.Red)
                            TableCell(String.format(Locale.US, "%.1f%%", b.variancePercent), width = 80.dp, color = if (b.variance >= 0) Color(0xFF4CAF50) else Color.Red)
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        } else {
            val trend = budgetTrends.find { it.budgetId == selectedBudgetId }
            if (trend != null) {
                stickyHeader {
                    Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 4.dp)) {
                        TableCell("Metric", width = 140.dp, isHeader = true)
                        Box(Modifier.horizontalScroll(monthScrollState)) {
                            Row {
                                displayMonths.forEach { m ->
                                    TableCell(m, width = 100.dp, isHeader = true)
                                }
                            }
                        }
                    }
                }
                val budgetMetrics = listOf("Budget", "Actual", "Var Amt", "Var %")
                items(budgetMetrics) { metric ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        TableCell(metric, width = 140.dp, isHeader = true)
                        Box(Modifier.horizontalScroll(monthScrollState)) {
                            Row {
                                months.forEach { month ->
                                    val data = trend.monthData[month]
                                    val value = when(metric) {
                                        "Budget" -> viewModel.formatAmount(data?.budget ?: 0.0)
                                        "Actual" -> viewModel.formatAmount(data?.actual ?: 0.0)
                                        "Var Amt" -> viewModel.formatAmount(data?.variance ?: 0.0)
                                        "Var %" -> String.format(Locale.US, "%.1f%%", data?.variancePercent ?: 0.0)
                                        else -> ""
                                    }
                                    val color = if ((metric == "Var Amt" || metric == "Var %") && (data?.variance ?: 0.0) < 0) Color.Red else if ((metric == "Var Amt" || metric == "Var %") && (data?.variance ?: 0.0) > 0) Color(0xFF4CAF50) else Color.Unspecified
                                    TableCell(value, width = 100.dp, color = color)
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }

        // --- 2. Category Performance Section ---
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Category Performance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                var expanded by remember { mutableStateOf(false) }
                val currentCategoryName = categoryTrends.find { it.categoryId == selectedCategoryId }?.categoryName ?: "All Categories"
                
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(currentCategoryName)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text("All Categories") }, onClick = { onCategorySelected(-1); expanded = false })
                        categoryTrends.forEach { c ->
                            DropdownMenuItem(text = { Text(c.categoryName) }, onClick = { onCategorySelected(c.categoryId); expanded = false })
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (selectedCategoryId == -1) {
            stickyHeader {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 4.dp)) {
                    TableCell("Category", width = 140.dp, isHeader = true)
                    Box(Modifier.horizontalScroll(monthScrollState)) {
                        Row {
                            groupedColumns.forEach { g ->
                                TableCell(g.label, width = 100.dp, isHeader = true)
                            }
                        }
                    }
                }
            }
            items(categoryTrends) { ct ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    TableCell(ct.categoryName, width = 140.dp, isHeader = true)
                    Box(Modifier.horizontalScroll(monthScrollState)) {
                        Row {
                            groupedColumns.forEach { g ->
                                val total = g.months.sumOf { ct.monthData[it]?.amount ?: 0.0 }
                                TableCell(viewModel.formatAmount(total), width = 100.dp)
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        } else {
            val trend = categoryTrends.find { it.categoryId == selectedCategoryId }
            if (trend != null) {
                stickyHeader {
                    Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 4.dp)) {
                        TableCell("Metric", width = 140.dp, isHeader = true)
                        Box(Modifier.horizontalScroll(monthScrollState)) {
                            Row {
                                groupedColumns.forEach { g ->
                                    TableCell(g.label, width = 100.dp, isHeader = true)
                                }
                            }
                        }
                    }
                }
                val catMetrics = listOf("Amount", "MoM Change", "MoM %", "YoY Amount", "YoY Change")
                items(catMetrics) { metric ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        TableCell(metric, width = 140.dp, isHeader = true)
                        Box(Modifier.horizontalScroll(monthScrollState)) {
                            Row {
                                groupedColumns.forEach { g ->
                                    val dataList = g.months.mapNotNull { trend.monthData[it] }
                                    val amount = dataList.sumOf { it.amount }
                                    val momChange = dataList.sumOf { it.momChange }
                                    val yoyAmount = dataList.sumOf { it.yoyAmount }
                                    val yoyChange = dataList.sumOf { it.yoyChange }
                                    
                                    val value = when(metric) {
                                        "Amount" -> viewModel.formatAmount(amount)
                                        "MoM Change" -> viewModel.formatAmount(momChange)
                                        "MoM %" -> {
                                            val prevTotal = amount - momChange
                                            val pct = if (prevTotal > 0) (momChange / prevTotal) * 100 else 0.0
                                            String.format(Locale.US, "%.1f%%", pct)
                                        }
                                        "YoY Amount" -> viewModel.formatAmount(yoyAmount)
                                        "YoY Change" -> viewModel.formatAmount(yoyChange)
                                        else -> ""
                                    }
                                    val color = if (metric.contains("Change") || metric.contains("%")) {
                                        if (momChange < 0 && metric.startsWith("MoM")) Color(0xFF4CAF50)
                                        else if (momChange > 0 && metric.startsWith("MoM")) Color.Red
                                        else if (yoyChange < 0 && metric.startsWith("YoY")) Color(0xFF4CAF50)
                                        else if (yoyChange > 0 && metric.startsWith("YoY")) Color.Red
                                        else Color.Unspecified
                                    } else Color.Unspecified
                                    TableCell(value, width = 100.dp, color = color)
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }

        // --- 3. Monthly Financial Metrics Section ---
        item {
            Text("${grouping.label} Financial Metrics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }

        stickyHeader {
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 4.dp)) {
                TableCell("Metric", width = 140.dp, isHeader = true)
                Box(Modifier.horizontalScroll(monthScrollState)) {
                    Row {
                        groupedColumns.forEach { g ->
                            TableCell(g.label, width = 100.dp, isHeader = true)
                        }
                    }
                }
            }
        }
        items(metrics) { m ->
            Row(Modifier.padding(vertical = 4.dp)) {
                TableCell(m.name, width = 140.dp, isHeader = true)
                Box(Modifier.horizontalScroll(monthScrollState)) {
                    Row {
                        groupedColumns.forEach { g ->
                            val total = g.months.sumOf { m.monthValues[it] ?: 0.0 }
                            val displayValue = if (m.unit == "%") {
                                val avg = if (g.months.isNotEmpty()) total / g.months.size else 0.0
                                String.format(Locale.US, "%.1f%%", avg)
                            } else viewModel.formatAmount(total)
                            TableCell(displayValue, width = 100.dp)
                        }
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun ChartsView(
    metrics: List<PerformanceMetricData>,
    budgets: List<BudgetPerformanceData>,
    grouping: ColumnGrouping,
    viewModel: ExpenseViewModel
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        metrics.forEach { m ->
            item {
                val months = m.monthValues.keys.sorted()
                val grouped = getGroupedColumns(months, grouping)
                
                val dataPoints = grouped.map { g ->
                    val total = g.months.sumOf { m.monthValues[it] ?: 0.0 }
                    if (m.unit == "%" && g.months.isNotEmpty()) total / g.months.size else total
                }
                val labels = grouped.map { it.label }

                if (dataPoints.isNotEmpty()) {
                    Column {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Card(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                            Box(Modifier.padding(16.dp)) {
                                LineChart(
                                    data = dataPoints,
                                    labels = labels,
                                    modifier = Modifier.fillMaxSize(),
                                    lineColor = MaterialTheme.colorScheme.primary,
                                    pointColor = MaterialTheme.colorScheme.secondary,
                                    showValuesAbovePoints = true
                                )
                            }
                        }
                    }
                }
            }
        }

        if (budgets.isNotEmpty()) {
            item {
                Text("Budget Variances (%)", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Card(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    Box(Modifier.padding(16.dp)) {
                        LineChart(
                            data = budgets.map { it.variancePercent },
                            labels = budgets.map { it.name.take(5) },
                            modifier = Modifier.fillMaxSize(),
                            lineColor = Color.Magenta,
                            pointColor = Color.Blue,
                            showValuesAbovePoints = true
                        )
                    }
                }
            }
        }
        
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun HorizontalTable(content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.horizontalScroll(scrollState)) {
        content()
    }
}

@Composable
fun TableCell(
    text: String,
    width: Dp = 100.dp,
    isHeader: Boolean = false,
    color: Color = Color.Unspecified
) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(4.dp),
        style = if (isHeader) MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1
    )
}
