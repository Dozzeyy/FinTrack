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

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import com.openapps.fintrack.domain.model.*
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

enum class ColumnGrouping(val labelRes: Int) {
    MONTHLY(R.string.label_monthly),
    QUARTERLY(R.string.label_quarterly),
    HALF_YEARLY(R.string.label_half_yearly),
    YEARLY(R.string.label_yearly),
    CUSTOM(R.string.label_custom)
}

data class GroupedColumn(
    val label: String,
    val chartLabel: String,
    val months: List<String>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PerformanceScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit,
    isEmbedded: Boolean = false
) {
    var startDate by remember { mutableStateOf(LocalDate.now().minusMonths(6).withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var showFilter by remember { mutableStateOf(false) }
    var grouping by remember { mutableStateOf(ColumnGrouping.MONTHLY) }
    var customValue by remember { mutableStateOf("1") }
    var customUnit by remember { mutableStateOf("month/s") }
    var showCustomDialog by remember { mutableStateOf(false) }
    
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
            if (!isEmbedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.title_performance_dashboard)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                        }
                    },
                    actions = {
                        var showGroupingMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showGroupingMenu = true }) {
                                Icon(Icons.Default.ViewColumn, stringResource(R.string.label_column_grouping))
                            }
                            DropdownMenu(expanded = showGroupingMenu, onDismissRequest = { showGroupingMenu = false }) {
                                ColumnGrouping.entries.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(g.labelRes)) },
                                        onClick = {
                                            if (g == ColumnGrouping.CUSTOM) {
                                                showCustomDialog = true
                                            } else {
                                                grouping = g
                                            }
                                            showGroupingMenu = false
                                        },
                                        trailingIcon = { if (grouping == g) Icon(Icons.Default.Check, null) }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { showFilter = true }) {
                            Icon(Icons.Default.DateRange, stringResource(R.string.label_select_period))
                        }
                    }
                )
            }
        }
    ) { padding ->
        val contentPadding = if (isEmbedded) PaddingValues(0.dp) else padding
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize()) {
            if (isEmbedded) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 0.dp, end = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var showGroupingMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showGroupingMenu = true }) {
                            Icon(Icons.Default.ViewColumn, stringResource(R.string.label_column_grouping))
                        }
                        DropdownMenu(expanded = showGroupingMenu, onDismissRequest = { showGroupingMenu = false }) {
                            ColumnGrouping.entries.forEach { g ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(g.labelRes)) },
                                    onClick = {
                                        if (g == ColumnGrouping.CUSTOM) {
                                            showCustomDialog = true
                                        } else {
                                            grouping = g
                                        }
                                        showGroupingMenu = false
                                    },
                                    trailingIcon = { if (grouping == g) Icon(Icons.Default.Check, null) }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { showFilter = true }) {
                        Icon(Icons.Default.DateRange, stringResource(R.string.label_select_period))
                    }
                }
            }
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.label_numbers)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.label_charts)) })
            }

            if (showCustomDialog) {
                var tempValue by remember { mutableStateOf(customValue) }
                var tempUnit by remember { mutableStateOf(customUnit) }
                
                AlertDialog(
                    onDismissRequest = { showCustomDialog = false },
                    title = { Text(stringResource(R.string.label_custom)) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = tempValue,
                                onValueChange = { tempValue = it },
                                modifier = Modifier.width(80.dp),
                                label = { Text("No.") },
                                singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                            )
                            
                            var unitExpanded by remember { mutableStateOf(false) }
                            val unitOptions = listOf("day/s" to R.string.label_days, "week/s" to R.string.label_weeks, "month/s" to R.string.label_months, "year/s" to R.string.label_years)
                            
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = stringResource(unitOptions.find { it.first == tempUnit }?.second ?: R.string.label_months),
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier.fillMaxWidth().clickable { unitExpanded = true },
                                    enabled = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, "") }
                                )
                                DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                                    unitOptions.forEach { (key, res) ->
                                        DropdownMenuItem(text = { Text(stringResource(res)) }, onClick = { tempUnit = key; unitExpanded = false })
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            customValue = tempValue
                            customUnit = tempUnit
                            grouping = ColumnGrouping.CUSTOM
                            showCustomDialog = false
                        }) { Text(stringResource(R.string.btn_apply)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCustomDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
                    }
                )
            }

            if (showFilter) {
                DateRangeFilterDialog(onDismiss = { showFilter = false }, onApply = { s, e -> startDate = s; endDate = e; showFilter = false })
            }

            val cVal = customValue.toIntOrNull() ?: 1
            if (selectedTab == 0) {
                NumbersView(metrics, budgets, budgetTrends, categoryTrends, selectedBudgetId, selectedCategoryId, { selectedBudgetId = it }, { selectedCategoryId = it }, grouping, cVal, customUnit, viewModel)
            } else {
                ChartsView(metrics, budgets, grouping, cVal, customUnit, viewModel)
            }
        }
    }
}

fun getGroupedColumns(days: List<String>, grouping: ColumnGrouping, customValue: Int = 1, customUnit: String = "month/s"): List<GroupedColumn> {
    if (days.isEmpty()) return emptyList()
    val sortedDays = days.sorted()

    if (grouping == ColumnGrouping.MONTHLY) {
        val months = sortedDays.map { it.substring(0, 7) }.distinct()
        return months.map { m ->
            val label = try {
                LocalDate.parse("$m-01").format(DateTimeFormatter.ofPattern("MMM-yy"))
            } catch (e: Exception) { m }
            GroupedColumn(label, label, days.filter { it.startsWith(m) })
        }
    }

    if (grouping == ColumnGrouping.CUSTOM) {
        val groups = mutableListOf<GroupedColumn>()
        val start = LocalDate.parse(sortedDays.first())
        val end = LocalDate.parse(sortedDays.last())
        
        var currentStart = start
        while (!currentStart.isAfter(end)) {
            val currentEnd = when (customUnit) {
                "day/s" -> currentStart.plusDays(customValue.toLong() - 1)
                "week/s" -> currentStart.plusWeeks(customValue.toLong()).minusDays(1)
                "month/s" -> currentStart.plusMonths(customValue.toLong()).minusDays(1)
                "year/s" -> currentStart.plusYears(customValue.toLong()).minusDays(1)
                else -> currentStart.plusMonths(1).minusDays(1)
            }
            
            val periodDays = sortedDays.filter { 
                val d = LocalDate.parse(it)
                !d.isBefore(currentStart) && !d.isAfter(currentEnd)
            }
            
            if (periodDays.isNotEmpty()) {
                val label = if (customUnit == "day/s" && customValue == 1) {
                    currentStart.format(DateTimeFormatter.ofPattern("dd-MMM"))
                } else {
                    "${currentStart.format(DateTimeFormatter.ofPattern("dd-MMM"))} to ${currentEnd.coerceAtMost(end).format(DateTimeFormatter.ofPattern("dd-MMM-yy"))}"
                }
                
                val chartLabel = if (customUnit == "day/s" && customValue == 1) {
                    currentStart.format(DateTimeFormatter.ofPattern("dd-MMM"))
                } else {
                    val startMonth = currentStart.format(DateTimeFormatter.ofPattern("MMM-yy"))
                    val endMonth = currentEnd.coerceAtMost(end).format(DateTimeFormatter.ofPattern("MMM-yy"))
                    if (startMonth == endMonth) startMonth else "$startMonth\nto\n$endMonth"
                }
                
                groups.add(GroupedColumn(label, chartLabel, periodDays))
            }
            
            currentStart = when (customUnit) {
                "day/s" -> currentStart.plusDays(customValue.toLong())
                "week/s" -> currentStart.plusWeeks(customValue.toLong())
                "month/s" -> currentStart.plusMonths(customValue.toLong())
                "year/s" -> currentStart.plusYears(customValue.toLong())
                else -> currentStart.plusMonths(1)
            }
        }
        return groups
    }

    val groups = mutableListOf<GroupedColumn>()
    var currentGroupDays = mutableListOf<String>()
    var currentGroupKey = ""

    for (dStr in sortedDays) {
        val date = LocalDate.parse(dStr)
        val key = when (grouping) {
            ColumnGrouping.QUARTERLY -> "${date.year}-Q${(date.monthValue - 1) / 3 + 1}"
            ColumnGrouping.HALF_YEARLY -> "${date.year}-H${if (date.monthValue <= 6) 1 else 2}"
            ColumnGrouping.YEARLY -> "${date.year}"
            else -> dStr.substring(0, 7)
        }

        if (currentGroupKey == "") {
            currentGroupKey = key
            currentGroupDays.add(dStr)
        } else if (currentGroupKey == key) {
            currentGroupDays.add(dStr)
        } else {
            groups.add(createGroupedColumn(currentGroupDays, grouping))
            currentGroupKey = key
            currentGroupDays = mutableListOf(dStr)
        }
    }
    if (currentGroupDays.isNotEmpty()) {
        groups.add(createGroupedColumn(currentGroupDays, grouping))
    }

    return groups
}

private fun createGroupedColumn(days: List<String>, grouping: ColumnGrouping): GroupedColumn {
    val first = LocalDate.parse(days.first())
    val last = LocalDate.parse(days.last())
    val label = if (days.size <= 31 && grouping != ColumnGrouping.YEARLY) {
        first.format(DateTimeFormatter.ofPattern("MMM-yy"))
    } else {
        val firstPart = first.format(DateTimeFormatter.ofPattern("MMM-yy"))
        val lastPart = last.format(DateTimeFormatter.ofPattern("MMM-yy"))
        if (firstPart == lastPart) firstPart else "$firstPart-$lastPart"
    }
    return GroupedColumn(label, label, days)
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
    customValue: Int,
    customUnit: String,
    viewModel: ExpenseViewModel
) {
    val days = metrics.firstOrNull()?.periodValues?.keys?.sorted() ?: emptyList()
    
    val groupedColumns = remember(days, grouping, customValue, customUnit) { getGroupedColumns(days, grouping, customValue, customUnit) }
    
    val monthScrollState = rememberScrollState()
    val budgetSummaryScrollState = rememberScrollState()

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // --- 1. Budget Performance Section ---
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_budget_performance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                var expanded by remember { mutableStateOf(false) }
                val currentBudgetName = budgets.find { it.budgetId == selectedBudgetId }?.name ?: stringResource(R.string.label_all_budgets)
                
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(currentBudgetName)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.label_all_budgets)) }, onClick = { onBudgetSelected(-1); expanded = false })
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
                    TableCell(stringResource(R.string.label_item), width = 140.dp, isHeader = true)
                    Box(Modifier.horizontalScroll(monthScrollState)) {
                        Row {
                            TableCell(stringResource(R.string.label_period), width = 120.dp, isHeader = true)
                            TableCell(stringResource(R.string.label_budget), width = 100.dp, isHeader = true)
                            TableCell(stringResource(R.string.label_actual), width = 100.dp, isHeader = true)
                            TableCell(stringResource(R.string.label_var_amt), width = 100.dp, isHeader = true)
                            TableCell(stringResource(R.string.label_var_pct), width = 80.dp, isHeader = true)
                        }
                    }
                }
            }
            items(budgets) { b ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    TableCell(b.name, width = 140.dp, isHeader = true)
                    Box(Modifier.horizontalScroll(monthScrollState)) {
                        Row {
                            TableCell(b.duration, width = 120.dp)
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
                        TableCell(stringResource(R.string.label_metric), width = 140.dp, isHeader = true)
                        Box(Modifier.horizontalScroll(monthScrollState)) {
                            Row {
                                groupedColumns.forEach { g ->
                                    TableCell(g.label, width = 120.dp, isHeader = true)
                                }
                            }
                        }
                    }
                }
                val budgetMetrics = listOf("Budget", "Actual", "Var Amt", "Var %")
                items(budgetMetrics) { metric ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        val metricLabel = when(metric) {
                            "Budget" -> stringResource(R.string.label_budget)
                            "Actual" -> stringResource(R.string.label_actual)
                            "Var Amt" -> stringResource(R.string.label_var_amt)
                            "Var %" -> stringResource(R.string.label_var_pct)
                            else -> metric
                        }
                        TableCell(metricLabel, width = 140.dp, isHeader = true)
                        Box(Modifier.horizontalScroll(monthScrollState)) {
                            Row {
                                groupedColumns.forEach { g ->

                                    val monthsInGroup = g.months.map { it.substring(0, 7) }.distinct()
                                    val dataList = monthsInGroup.mapNotNull { trend.monthData[it] }
                                    
                                    val budget = dataList.sumOf { it.budget }
                                    val actual = dataList.sumOf { it.actual }
                                    val variance = budget - actual
                                    val varPct = if (budget > 0) (variance / budget) * 100 else 0.0

                                    val value = when(metric) {
                                        "Budget" -> viewModel.formatAmount(budget)
                                        "Actual" -> viewModel.formatAmount(actual)
                                        "Var Amt" -> viewModel.formatAmount(variance)
                                        "Var %" -> String.format(Locale.US, "%.1f%%", varPct)
                                        else -> ""
                                    }
                                    val color = if ((metric == "Var Amt" || metric == "Var %") && variance < 0) Color.Red else if ((metric == "Var Amt" || metric == "Var %") && variance > 0) Color(0xFF4CAF50) else Color.Unspecified
                                    TableCell(value, width = 120.dp, color = color)
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
                Text(stringResource(R.string.label_category_performance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                
                var expanded by remember { mutableStateOf(false) }
                val currentCategoryName = categoryTrends.find { it.categoryId == selectedCategoryId }?.categoryName ?: stringResource(R.string.label_all_categories)
                
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text(currentCategoryName)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.label_all_categories)) }, onClick = { onCategorySelected(-1); expanded = false })
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
                    TableCell(stringResource(R.string.label_category), width = 140.dp, isHeader = true)
                    Box(Modifier.horizontalScroll(monthScrollState)) {
                        Row {
                            groupedColumns.forEach { g ->
                                TableCell(g.label, width = 120.dp, isHeader = true)
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
                                val monthsInGroup = g.months.map { it.substring(0, 7) }.distinct()
                                val total = monthsInGroup.sumOf { ct.monthData[it]?.amount ?: 0.0 }
                                TableCell(viewModel.formatAmount(total), width = 120.dp)
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
                        TableCell(stringResource(R.string.label_metric), width = 140.dp, isHeader = true)
                        Box(Modifier.horizontalScroll(monthScrollState)) {
                            Row {
                                groupedColumns.forEach { g ->
                                    TableCell(g.label, width = 120.dp, isHeader = true)
                                }
                            }
                        }
                    }
                }
                val catMetrics = listOf("Amount", "MoM Change", "MoM %", "YoY Amount", "YoY Change")
                items(catMetrics) { metric ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        val metricLabel = when(metric) {
                            "Amount" -> stringResource(R.string.label_amount)
                            "MoM Change" -> stringResource(R.string.label_mom_change)
                            "MoM %" -> stringResource(R.string.label_mom_pct)
                            "YoY Amount" -> stringResource(R.string.label_yoy_amount)
                            "YoY Change" -> stringResource(R.string.label_yoy_change)
                            else -> metric
                        }
                        TableCell(metricLabel, width = 140.dp, isHeader = true)
                        Box(Modifier.horizontalScroll(monthScrollState)) {
                            Row {
                                groupedColumns.forEach { g ->
                                    val monthsInGroup = g.months.map { it.substring(0, 7) }.distinct()
                                    val dataList = monthsInGroup.mapNotNull { trend.monthData[it] }
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
                                    TableCell(value, width = 120.dp, color = color)
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
            Text(stringResource(R.string.label_financial_metrics, stringResource(grouping.labelRes)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }

        stickyHeader {
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 4.dp)) {
                TableCell(stringResource(R.string.label_metric), width = 140.dp, isHeader = true)
                Box(Modifier.horizontalScroll(monthScrollState)) {
                    Row {
                        groupedColumns.forEach { g ->
                            TableCell(g.label, width = 120.dp, isHeader = true)
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
                            val value = when(m.aggregation) {
                                MetricAggregation.SUM -> g.months.sumOf { m.periodValues[it] ?: 0.0 }
                                MetricAggregation.AVERAGE -> {
                                    val sum = g.months.sumOf { m.periodValues[it] ?: 0.0 }
                                    if (g.months.isNotEmpty()) sum / g.months.size else 0.0
                                }
                                MetricAggregation.LAST -> {
                                    val lastDay = g.months.lastOrNull()
                                    if (lastDay != null) m.periodValues[lastDay] ?: 0.0 else 0.0
                                }
                            }
                            val displayValue = if (m.unit == "%") {
                                String.format(Locale.US, "%.1f%%", value)
                            } else viewModel.formatAmount(value)
                            TableCell(displayValue, width = 120.dp)
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
    customValue: Int,
    customUnit: String,
    viewModel: ExpenseViewModel
) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        metrics.forEach { m ->
            item {
                val days = m.periodValues.keys.sorted()
                val grouped = getGroupedColumns(days, grouping, customValue, customUnit)
                
                val dataPoints = grouped.map { g ->
                    when(m.aggregation) {
                        MetricAggregation.SUM -> g.months.sumOf { m.periodValues[it] ?: 0.0 }
                        MetricAggregation.AVERAGE -> {
                            val sum = g.months.sumOf { m.periodValues[it] ?: 0.0 }
                            if (g.months.isNotEmpty()) sum / g.months.size else 0.0
                        }
                        MetricAggregation.LAST -> {
                            val lastDay = g.months.lastOrNull()
                            if (lastDay != null) m.periodValues[lastDay] ?: 0.0 else 0.0
                        }
                    }
                }
                val labels = grouped.map { it.chartLabel }

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
                Text(stringResource(R.string.label_budget_variances_pct), style = MaterialTheme.typography.titleSmall)
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
