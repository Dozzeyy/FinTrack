/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.openapps.fintrack.data.AccountBalance
import com.openapps.fintrack.data.TransactionWithDetails
import com.openapps.fintrack.data.PartyBalance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun TransactionRow(detail: TransactionWithDetails, viewModel: ExpenseViewModel, showTxnNumber: Boolean = false) {
    val transaction = detail.transaction
    val typeColor = when (detail.categoryType) {
        "income" -> Color(0xFF4CAF50)
        "expense" -> Color.Red
        else -> Color(0xFF2196F3)
    }
    
    val backgroundColor = typeColor.copy(alpha = 0.05f)
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (detail.categoryType == null) (detail.toAccountIcon ?: "🏦") else (detail.categoryIcon ?: detail.accountIcon ?: "📁"),
                    fontSize = 20.sp
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                val headlineText = if (detail.categoryType == null && detail.toAccountName != null) {
                    detail.toAccountName
                } else {
                    (detail.categoryName ?: "Transfer")
                }

                Text(
                    text = headlineText + 
                           (if (detail.partyName != null) " (${detail.partyName})" else "") +
                           (if (detail.toPartyName != null) " -> ${detail.toPartyName}" else ""),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Text(" | ", color = Color.LightGray)
                    
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = detail.accountName,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    
                    if (detail.toAccountName != null && detail.categoryType != null) {
                        Text(" → ", color = Color.Gray, fontSize = 10.sp)
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = detail.toAccountName,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                
                if (!transaction.note.isNullOrEmpty()) {
                    Text(
                        text = transaction.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = viewModel.formatAmount(transaction.amount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = typeColor
                )
                if (showTxnNumber && transaction.transactionNumber != null) {
                    Text(
                        text = transaction.transactionNumber,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

@Composable
fun PieChart(data: List<Pair<String, Double>>, colors: List<Color>) {
    val total = data.sumOf { Math.abs(it.second) }
    if (total <= 0.0) return

    val chartColors = if (colors.isEmpty() || colors.all { it == Color.Gray }) {
        listOf(
            Color(0xFFf44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
            Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
            Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
            Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
            Color(0xFF795548), Color(0xFF9E9E9E), Color(0xFF607D8B), Color(0xFF33691E),
            Color(0xFF1B5E20), Color(0xFF004D40), Color(0xFF01579B), Color(0xFF0D47A1),
            Color(0xFF1A237E), Color(0xFF311B92), Color(0xFF4A148C), Color(0xFF880E4F),
            Color(0xFFB71C1C), Color(0xFFBF360C)
        )
    } else colors

    Canvas(modifier = Modifier.size(150.dp)) {
        var startAngle = -90f
        data.forEachIndexed { index, pair ->
            val sweepAngle = (Math.abs(pair.second) / total * 360).toFloat()
            drawArc(
                color = chartColors[index % chartColors.size],
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true,
                size = Size(size.width, size.height)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
fun DonutChart(
    data: List<Pair<String, Double>>, 
    colors: List<Color>, 
    centerText: String, 
    centerSubText: String = "",
    modifier: Modifier = Modifier.size(200.dp)
) {
    val total = data.sumOf { Math.abs(it.second) }
    if (total <= 0.0) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No Data", color = Color.Gray)
        }
        return
    }

    val chartColors = if (colors.isEmpty()) {
        listOf(
            Color(0xFFf44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
            Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
            Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
            Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
            Color(0xFF795548), Color(0xFF9E9E9E), Color(0xFF607D8B), Color(0xFF33691E),
            Color(0xFF1B5E20), Color(0xFF004D40), Color(0xFF01579B), Color(0xFF0D47A1),
            Color(0xFF1A237E), Color(0xFF311B92), Color(0xFF4A148C), Color(0xFF880E4F),
            Color(0xFFB71C1C), Color(0xFFBF360C)
        )
    } else colors

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            var startAngle = -90f
            data.forEachIndexed { index, pair ->
                val sweepAngle = (Math.abs(pair.second) / total * 360).toFloat()
                drawArc(
                    color = chartColors[index % chartColors.size],
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    size = Size(size.width, size.height),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 40f, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
                )
                startAngle += sweepAngle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (centerSubText.isNotEmpty()) {
                Text(centerSubText, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun HorizontalBalanceChart(
    accounts: List<AccountBalance>,
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 300.dp)
) {
    if (accounts.isEmpty()) return

    val maxAbsVal = accounts.maxOfOrNull { kotlin.math.abs(it.balance) }?.coerceAtLeast(1.0) ?: 1.0

    Column(modifier = modifier.padding(8.dp)) {
        accounts.forEach { account ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (account.icon ?: "📁") + " " + account.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.width(110.dp),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Box(modifier = Modifier.weight(1f).height(24.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        
                        // Baseline
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.3f),
                            start = androidx.compose.ui.geometry.Offset(0f, canvasHeight),
                            end = androidx.compose.ui.geometry.Offset(canvasWidth, canvasHeight),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        val barWidthFraction = (kotlin.math.abs(account.balance) / maxAbsVal).toFloat()
                        
                        drawRoundRect(
                            color = if (account.balance >= 0) Color(0xFF4CAF50) else Color.Red,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, 4.dp.toPx()),
                            size = Size(canvasWidth * barWidthFraction, canvasHeight - 8.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
                
                Text(
                    text = viewModel.formatAmountWhole(account.balance),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(70.dp).padding(start = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    color = if (account.balance >= 0) Color(0xFF4CAF50) else Color.Red
                )
            }
        }
    }
}

@Composable
fun LineChart(
    data: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier.fillMaxWidth().height(200.dp),
    lineColor: Color = MaterialTheme.colorScheme.primary,
    pointColor: Color = MaterialTheme.colorScheme.secondary
) {
    if (data.isEmpty()) return

    val maxVal = (data.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val minVal = (data.minOrNull() ?: 0.0)
    val range = (maxVal - minVal).coerceAtLeast(1.0)
    
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onSurfaceColor)

    Canvas(modifier = modifier.padding(16.dp).padding(top = 24.dp, start = 48.dp)) {
        val width = size.width
        val height = size.height
        val spacing = width / (data.size - 1).coerceAtLeast(1)

        val points = data.mapIndexed { index, value ->
            val x = index * spacing
            val y = height - ((value - minVal) / range * height).toFloat()
            androidx.compose.ui.geometry.Offset(x, y)
        }

        // Draw line
        for (i in 0 until points.size - 1) {
            drawLine(
                color = lineColor,
                start = points[i],
                end = points[i + 1],
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Draw points and values
        points.forEachIndexed { index, point ->
            drawCircle(
                color = pointColor,
                radius = 4.dp.toPx(),
                center = point
            )
            
            // Draw value text on the left edge
            val valueText = if (abs(data[index]) >= 1000000) {
                String.format(java.util.Locale.US, "%.1fM", data[index] / 1000000)
            } else if (abs(data[index]) >= 1000) {
                String.format(java.util.Locale.US, "%.1fk", data[index] / 1000)
            } else {
                data[index].roundToInt().toString()
            }
            
            val textLayoutResult = textMeasurer.measure(valueText, style = labelStyle)
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = androidx.compose.ui.geometry.Offset(
                    -48.dp.toPx(),
                    point.y - textLayoutResult.size.height / 2
                )
            )
        }
        
        // Optional: Draw baseline
        drawLine(
            color = Color.Gray.copy(alpha = 0.5f),
            start = androidx.compose.ui.geometry.Offset(0f, height),
            end = androidx.compose.ui.geometry.Offset(width, height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryView(viewModel: ExpenseViewModel, onOpenDrawer: (() -> Unit)? = null) {
    var month by remember { mutableStateOf(LocalDate.now()) }
    var startDate by remember { mutableStateOf(month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)) }
    var showFilterDialog by remember { mutableStateOf(false) }
    val filterTypes = remember { mutableStateListOf<String>() }
    
    var viewMode by remember { mutableStateOf("List") } // "List" or "Calendar"

    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val allAccounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    
    val selectedTagIds = remember { mutableStateListOf<Int>() }
    val selectedAccountIds = remember { mutableStateListOf<Int>() }
    
    var showTagFilterDialog by remember { mutableStateOf(false) }
    var showAccountFilterDialog by remember { mutableStateOf(false) }

    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    val context = LocalContext.current

    val filteredTransactions = remember(transactions, filterTypes.toList(), selectedTagIds.toList(), selectedAccountIds.toList()) {
        var list = if (filterTypes.isEmpty()) transactions
        else {
            transactions.filter {
                (filterTypes.contains("Income") && it.categoryType == "income") ||
                (filterTypes.contains("Expense") && it.categoryType == "expense") ||
                (filterTypes.contains("Transfer") && it.categoryType == null)
            }
        }

        if (selectedTagIds.isNotEmpty()) {
            list = list.filter { detail ->
                val tTags = detail.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                selectedTagIds.any { it in tTags }
            }
        }
        
        if (selectedAccountIds.isNotEmpty()) {
            list = list.filter { detail ->
                detail.transaction.accountId in selectedAccountIds || detail.transaction.toAccountId in selectedAccountIds
            }
        }
        
        list
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { exportToUri(context, filteredTransactions, "CSV", it) }
        }
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (onOpenDrawer != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, "Menu")
                    }
                    Text("Transactions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Month Navigation
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            IconButton(onClick = { 
                month = month.minusMonths(1)
                startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
            }) { Icon(Icons.Default.ChevronLeft, "") }
            
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), 
                modifier = Modifier.weight(1f).clickable { showFilterDialog = true }, 
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            IconButton(onClick = { 
                month = month.plusMonths(1)
                startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)
            }) { Icon(Icons.Default.ChevronRight, "") }

            IconButton(onClick = { viewMode = if (viewMode == "List") "Calendar" else "List" }) {
                Icon(if (viewMode == "List") Icons.Default.CalendarMonth else Icons.Default.FormatListBulleted, contentDescription = "Toggle View")
            }

            IconButton(onClick = { showFilterDialog = true }) {
                Icon(Icons.Default.DateRange, contentDescription = "Filter")
            }

            IconButton(onClick = { showAccountFilterDialog = true }) {
                Box {
                    Icon(
                        imageVector = Icons.Default.AccountBalance, 
                        contentDescription = "Accounts Filter",
                        tint = if (selectedAccountIds.isNotEmpty()) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                    if (selectedAccountIds.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.size(6.dp).align(Alignment.TopEnd),
                            shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.error
                        ) {}
                    }
                }
            }

            IconButton(onClick = { 
                exportLauncher.launch("transactions_${month.format(DateTimeFormatter.ofPattern("MMM_yyyy"))}.csv")
            }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export")
            }
        }

        if (viewMode == "List") {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Income", "Expense", "Transfer").forEach { type ->
                    val isSelected = filterTypes.contains(type)
                    FilterChip(
                        selected = isSelected,
                        onClick = { 
                            if (isSelected) {
                                filterTypes.remove(type)
                            } else {
                                filterTypes.add(type)
                            }
                        },
                        label = { Text(type, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                
                FilterChip(
                    selected = selectedTagIds.isNotEmpty(),
                    onClick = { showTagFilterDialog = true },
                    label = { Text("Tags", style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (selectedTagIds.isNotEmpty()) {
                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                )
            }
            
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 0.dp)) {
                items(filteredTransactions) { detail ->
                    Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = detail }) {
                        TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = false)
                    }
                }
            }
        } else {
            TransactionCalendarView(month, filteredTransactions, viewModel)
        }
    }
    
    if (showFilterDialog) {
        DateRangeFilterDialog(
            onDismiss = { showFilterDialog = false },
            onApply = { start, end ->
                startDate = start
                endDate = end
                showFilterDialog = false
            }
        )
    }

    if (showTagFilterDialog) {
        MultiSelectFilterDialog(
            title = "Filter by Tags",
            items = allTags.map { it.id to it.name },
            selectedIds = selectedTagIds,
            onDismiss = { showTagFilterDialog = false }
        )
    }

    if (showAccountFilterDialog) {
        MultiSelectFilterDialog(
            title = "Filter by Accounts",
            items = allAccounts.map { it.id to it.name },
            selectedIds = selectedAccountIds,
            onDismiss = { showAccountFilterDialog = false }
        )
    }
}

@Composable
fun MultiSelectFilterDialog(
    title: String,
    items: List<Pair<Int, String>>,
    selectedIds: MutableList<Int>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { selectedIds.clear() }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedIds.isEmpty(), onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text("All")
                }
                Divider()
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(items) { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (selectedIds.contains(item.first)) selectedIds.remove(item.first)
                                else selectedIds.add(item.first)
                            }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = selectedIds.contains(item.first), onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(item.second)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Apply") }
        }
    )
}

@Composable
fun AssetsLiabilitiesView(viewModel: ExpenseViewModel) {
    var asOfDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_DATE)) }
    var viewMode by remember { mutableStateOf(0) } // 0: Major, 1: Major+Minor, 2: Major+Minor+Micro, 3: Minor, 4: Micro
    
    var drillMajorId by remember { mutableStateOf<Int?>(null) }
    var drillMinorId by remember { mutableStateOf<Int?>(null) }
    var drillMicroId by remember { mutableStateOf<Int?>(null) }

    val majorBalances by viewModel.getMajorHeadBalances(asOfDate).collectAsState(initial = emptyList())
    val minorBalances by viewModel.getMinorHeadBalances(asOfDate).collectAsState(initial = emptyList())
    val microBalances by viewModel.getAccountBalances(asOfDate).collectAsState(initial = emptyList())

    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let { 
                exportToUri(context, microBalances.map { b ->
                    TransactionWithDetails(
                        transaction = com.openapps.fintrack.data.Transaction(date = asOfDate, time = "00:00", accountId = b.id, amount = b.balance, note = "Balance Export", categoryId = null),
                        categoryName = null,
                        categoryType = if (b.balance >= 0) "asset" else "liability",
                        categoryIcon = null,
                        accountName = b.name,
                        accountIcon = b.icon,
                        toAccountName = null,
                        toAccountIcon = null,
                        partyName = null,
                        toPartyName = null
                    )
                }, "CSV", it) 
            }
        }
    )

    if (drillMicroId != null) {
        val account = microBalances.find { it.id == drillMicroId }
        if (account != null) {
            AccountDetailView(viewModel, account, onBack = { drillMicroId = null }, initialAsOfDate = asOfDate)
            return
        }
    }

    if (drillMinorId != null) {
        val minor = minorBalances.find { it.id == drillMinorId }
        val micros = microBalances.filter { it.minorHeadId == drillMinorId }
        DrillDownView(
            title = minor?.name ?: "Minor Head",
            items = micros.map { it.name to it.balance },
            onItemClick = { name -> drillMicroId = microBalances.find { it.name == name }?.id },
            onBack = { drillMinorId = null },
            viewModel = viewModel,
            asOfDate = asOfDate,
            microBalances = microBalances
        )
        return
    }

    if (drillMajorId != null) {
        val major = majorBalances.find { it.id == drillMajorId }
        val minors = minorBalances.filter { it.majorHeadId == drillMajorId }
        DrillDownView(
            title = major?.name ?: "Major Head",
            items = minors.map { it.name to it.balance },
            onItemClick = { name -> drillMinorId = minorBalances.find { it.name == name && it.majorHeadId == drillMajorId }?.id },
            onBack = { drillMajorId = null },
            viewModel = viewModel,
            asOfDate = asOfDate,
            microBalances = microBalances
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewMode = (viewMode + 1) % 5 }) {
                Icon(Icons.Default.Layers, "Toggle Mode")
            }
            Text(text = "As of: $asOfDate", modifier = Modifier.clickable {
                val date = try { LocalDate.parse(asOfDate) } catch(e: Exception) { LocalDate.now() }
                DatePickerDialog(context, { _, year, month, day ->
                    asOfDate = LocalDate.of(year, month + 1, day).format(DateTimeFormatter.ISO_DATE)
                }, date.year, date.monthValue - 1, date.dayOfMonth).show()
            })
            IconButton(onClick = { exportLauncher.launch("balances_$asOfDate.csv") }) {
                Icon(Icons.Default.FileDownload, "Export")
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (viewMode) {
                0 -> { // Major only
                    val pos = majorBalances.filter { it.balance > 0 }
                    val neg = majorBalances.filter { it.balance <= 0 }
                    if (pos.isNotEmpty()) {
                        item { Text("Assets", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(pos) { hb -> BalanceRow(hb.name, hb.balance, null, viewModel) { drillMajorId = hb.id } }
                    }
                    if (neg.isNotEmpty()) {
                        item { Text("Liabilities", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(neg) { hb -> BalanceRow(hb.name, hb.balance, null, viewModel) { drillMajorId = hb.id } }
                    }
                }
                1 -> { // Major + Minor
                    val posMajor = majorBalances.filter { it.balance > 0 }
                    val negMajor = majorBalances.filter { it.balance <= 0 }
                    
                    if (posMajor.isNotEmpty()) {
                        item { Text("Assets", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        posMajor.forEach { mhb ->
                            item { Text(mhb.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp)) }
                            items(minorBalances.filter { it.majorHeadId == mhb.id }) { mihb ->
                                BalanceRow(mihb.name, mihb.balance, null, viewModel, indent = true) { drillMinorId = mihb.id }
                            }
                        }
                    }
                    if (negMajor.isNotEmpty()) {
                        item { Text("Liabilities", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        negMajor.forEach { mhb ->
                            item { Text(mhb.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp)) }
                            items(minorBalances.filter { it.majorHeadId == mhb.id }) { mihb ->
                                BalanceRow(mihb.name, mihb.balance, null, viewModel, indent = true) { drillMinorId = mihb.id }
                            }
                        }
                    }
                }
                2 -> { // Major + Minor + Micro
                    val posMajor = majorBalances.filter { it.balance > 0 }
                    val negMajor = majorBalances.filter { it.balance <= 0 }

                    if (posMajor.isNotEmpty()) {
                        item { Text("Assets", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        posMajor.forEach { mhb ->
                            item { Text(mhb.name, style = MaterialTheme.typography.titleSmall) }
                            minorBalances.filter { it.majorHeadId == mhb.id }.forEach { mihb ->
                                val mms = microBalances.filter { it.minorHeadId == mihb.id || (mihb.name == "Default" && mihb.majorHeadId == 10 && it.minorHeadId == null) }
                                if (mms.isNotEmpty()) {
                                    item { Text(mihb.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp)) }
                                    items(mms) { mb ->
                                        BalanceRow(mb.name, mb.balance, mb.icon, viewModel, indent = true, doubleIndent = true) { drillMicroId = mb.id }
                                    }
                                }
                            }
                        }
                    }
                    if (negMajor.isNotEmpty()) {
                        item { Text("Liabilities", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        negMajor.forEach { mhb ->
                            item { Text(mhb.name, style = MaterialTheme.typography.titleSmall) }
                            minorBalances.filter { it.majorHeadId == mhb.id }.forEach { mihb ->
                                val mms = microBalances.filter { it.minorHeadId == mihb.id || (mihb.name == "Default" && mihb.majorHeadId == 10 && it.minorHeadId == null) }
                                if (mms.isNotEmpty()) {
                                    item { Text(mihb.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp)) }
                                    items(mms) { mb ->
                                        BalanceRow(mb.name, mb.balance, mb.icon, viewModel, indent = true, doubleIndent = true) { drillMicroId = mb.id }
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> { // Minor only
                    val pos = minorBalances.filter { it.balance > 0 }
                    val neg = minorBalances.filter { it.balance <= 0 }
                    if (pos.isNotEmpty()) {
                        item { Text("Assets", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(pos) { hb -> BalanceRow(hb.name, hb.balance, null, viewModel) { drillMinorId = hb.id } }
                    }
                    if (neg.isNotEmpty()) {
                        item { Text("Liabilities", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(neg) { hb -> BalanceRow(hb.name, hb.balance, null, viewModel) { drillMinorId = hb.id } }
                    }
                }
                4 -> { // Micro only
                    val pos = microBalances.filter { it.balance > 0 }
                    val neg = microBalances.filter { it.balance <= 0 }
                    if (pos.isNotEmpty()) {
                        item { Text("Assets", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(pos) { hb -> BalanceRow(hb.name, hb.balance, hb.icon, viewModel) { drillMicroId = hb.id } }
                    }
                    if (neg.isNotEmpty()) {
                        item { Text("Liabilities", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                        items(neg) { hb -> BalanceRow(hb.name, hb.balance, hb.icon, viewModel) { drillMicroId = hb.id } }
                    }
                }
            }
            
            item {
                val netPosition = microBalances.sumOf { it.balance }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer, 
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net Position", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(viewModel.formatAmount(netPosition), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceRow(name: String, balance: Double, icon: String?, viewModel: ExpenseViewModel, indent: Boolean = false, doubleIndent: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
            .padding(start = if (doubleIndent) 32.dp else if (indent) 16.dp else 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = (icon ?: "🏦") + " " + name, modifier = Modifier.weight(1f))
        Text(viewModel.formatAmount(balance), color = if (balance >= 0) Color(0xFF4CAF50) else Color.Red)
    }
    Divider(modifier = Modifier.padding(start = if (doubleIndent) 32.dp else if (indent) 16.dp else 0.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillDownView(
    title: String, 
    items: List<Pair<String, Double>>, 
    onItemClick: (String) -> Unit, 
    onBack: () -> Unit, 
    viewModel: ExpenseViewModel,
    asOfDate: String,
    microBalances: List<AccountBalance>
) {
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val allAccounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    
    val selectedTagIds = remember { mutableStateListOf<Int>() }
    val selectedAccountIds = remember { mutableStateListOf<Int>() }
    
    var showTagFilter by remember { mutableStateOf(false) }
    var showAccountFilter by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val filteredItems = remember(items, selectedTagIds.toList(), selectedAccountIds.toList(), allTransactions, asOfDate) {
        if (selectedTagIds.isEmpty() && selectedAccountIds.isEmpty()) items
        else {
            items.map { (name, balance) ->
                val accountId = microBalances.find { it.name == name }?.id
                val txns = allTransactions.filter { t ->
                    (t.accountName == name || t.toAccountName == name) && 
                    t.transaction.date <= asOfDate &&
                    (selectedTagIds.isEmpty() || t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in selectedTagIds } == true) &&
                    (selectedAccountIds.isEmpty() || t.transaction.accountId in selectedAccountIds || t.transaction.toAccountId in selectedAccountIds || accountId in selectedAccountIds)
                }
                name to txns.sumOf { 
                    if (it.transaction.toAccountId == accountId || (it.transaction.accountId == accountId && it.categoryType == "income")) 
                        it.transaction.amount 
                    else if (it.transaction.accountId == accountId)
                        -it.transaction.amount
                    else 0.0
                }
            }.filter { it.second != 0.0 }
        }
    }

    val balancesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            val csv = StringBuilder("Item,Balance\n")
            filteredItems.forEach { csv.append("${it.first},${it.second}\n") }
            context.contentResolver.openOutputStream(it)?.use { out -> out.write(csv.toString().toByteArray()) }
            Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
        }
    }

    val txnsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            val csv = StringBuilder("Date,Time,Account,Category,Amount,Note\n")
            val itemNames = filteredItems.map { it.first }
            val txns = allTransactions.filter { t ->
                (t.accountName in itemNames || t.toAccountName in itemNames) &&
                t.transaction.date <= asOfDate &&
                (selectedTagIds.isEmpty() || t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in selectedTagIds } == true) &&
                (selectedAccountIds.isEmpty() || t.transaction.accountId in selectedAccountIds || t.transaction.toAccountId in selectedAccountIds)
            }
            txns.forEach { t ->
                csv.append("${t.transaction.date},${t.transaction.time},${t.accountName},${t.categoryName ?: "Transfer"},${t.transaction.amount},\"${t.transaction.note ?: ""}\"\n")
            }
            context.contentResolver.openOutputStream(it)?.use { out -> out.write(csv.toString().toByteArray()) }
            Toast.makeText(context, "Exported", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) }, 
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") } },
                actions = {
                    IconButton(onClick = { showTagFilter = true }) {
                        Icon(Icons.Default.Label, "Tags", tint = if (selectedTagIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showAccountFilter = true }) {
                        Icon(Icons.Default.AccountBalance, "Accounts", tint = if (selectedAccountIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.FileDownload, "Export")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            items(filteredItems) { item ->
                val icon = microBalances.find { it.name == item.first }?.icon ?: "🏦"
                BalanceRow(item.first, item.second, icon, viewModel) { onItemClick(item.first) }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Data") },
            text = { Text("What would you like to export?") },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false; balancesLauncher.launch("balances_${title}_$asOfDate.csv") }) { Text("Balances") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false; txnsLauncher.launch("transactions_${title}_$asOfDate.csv") }) { Text("Transactions") }
            }
        )
    }

    if (showTagFilter) {
        MultiSelectFilterDialog(title = "Filter by Tags", items = allTags.map { it.id to it.name }, selectedIds = selectedTagIds, onDismiss = { showTagFilter = false })
    }
    if (showAccountFilter) {
        MultiSelectFilterDialog(title = "Filter by Accounts", items = allAccounts.map { it.id to it.name }, selectedIds = selectedAccountIds, onDismiss = { showAccountFilter = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnAccountPartyBalancesView(viewModel: ExpenseViewModel, asOfDate: String, onBack: () -> Unit) {
    val allPartiesRaw by viewModel.getAllParties().collectAsState(initial = emptyList())
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val allCategories by viewModel.getAllCategories().collectAsState(initial = emptyList())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    
    var filterType by remember { mutableStateOf("Both") } 
    val selectedTagIds = remember { mutableStateListOf<Int>() }
    val selectedCategoryIds = remember { mutableStateListOf<Int>() }
    
    var showTagFilter by remember { mutableStateOf(false) }
    var showCategoryFilter by remember { mutableStateOf(false) }
    var selectedPartyForHistory by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val displayParties = remember(allPartiesRaw, filterType, selectedTagIds.toList(), selectedCategoryIds.toList(), allTransactions, asOfDate) {
        allPartiesRaw.filter { it.isEnabled }.map { p ->
            // Filter transactions associated with this party (either as sender or receiver)
            val partyTxnsIn = allTransactions.filter { t ->
                (t.transaction.toPartyId == p.id || (t.transaction.partyId == p.id && t.categoryType == "income")) &&
                t.transaction.date <= asOfDate &&
                (selectedTagIds.isEmpty() || (t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in selectedTagIds } == true)) &&
                (selectedCategoryIds.isEmpty() || t.transaction.categoryId in selectedCategoryIds)
            }
            
            val partyTxnsOut = allTransactions.filter { t ->
                (t.transaction.partyId == p.id && (t.categoryType == "expense" || t.transaction.categoryId == null)) &&
                t.transaction.date <= asOfDate &&
                (selectedTagIds.isEmpty() || (t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in selectedTagIds } == true)) &&
                (selectedCategoryIds.isEmpty() || t.transaction.categoryId in selectedCategoryIds)
            }
            
            val inc = partyTxnsIn.sumOf { it.transaction.amount }
            val exp = partyTxnsOut.sumOf { it.transaction.amount }
            
            // Opening balance is only included if no categorical/tag filters are active
            val opening = if (selectedTagIds.isEmpty() && selectedCategoryIds.isEmpty()) p.openingBalance else 0.0
            val currentFilteredBalance = opening + inc - exp
            
            PartyBalance(p.id, p.name, currentFilteredBalance)
        }.filter { p ->
            val matchType = when (filterType) {
                "Receivable" -> p.balance > 0
                "Payable" -> p.balance < 0
                else -> true
            }
            val isFiltered = selectedTagIds.isNotEmpty() || selectedCategoryIds.isNotEmpty()
            if (isFiltered) {
                // If filtered, only show if they have transactions matching the filter
                val hasMatch = allTransactions.any { t ->
                    (t.transaction.partyId == p.id || t.transaction.toPartyId == p.id) &&
                    t.transaction.date <= asOfDate &&
                    (selectedTagIds.isEmpty() || (t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in selectedTagIds } == true)) &&
                    (selectedCategoryIds.isEmpty() || t.transaction.categoryId in selectedCategoryIds)
                }
                matchType && hasMatch
            } else {
                matchType
            }
        }
    }

    val balancesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let {
                val csv = StringBuilder("Party Name,Balance\n")
                displayParties.forEach { csv.append("${it.name},${it.balance}\n") }
                context.contentResolver.openOutputStream(it)?.use { out -> out.write(csv.toString().toByteArray()) }
                Toast.makeText(context, "Balances Exported", Toast.LENGTH_SHORT).show()
            }
        }
    )
    val txnsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let {
                val csv = StringBuilder("Date,Time,Party,Category,Amount,Note\n")
                val partyIds = displayParties.map { it.id }
                val txns = allTransactions.filter { t ->
                    t.transaction.partyId in partyIds && t.transaction.date <= asOfDate &&
                    (selectedTagIds.isEmpty() || (t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in selectedTagIds } == true)) &&
                    (selectedCategoryIds.isEmpty() || t.transaction.categoryId in selectedCategoryIds)
                }
                txns.forEach { t ->
                    csv.append("${t.transaction.date},${t.transaction.time},${t.partyName ?: ""},${t.categoryName ?: ""},${t.transaction.amount},\"${t.transaction.note ?: ""}\"\n")
                }
                context.contentResolver.openOutputStream(it)?.use { out -> out.write(csv.toString().toByteArray()) }
                Toast.makeText(context, "Transactions Exported", Toast.LENGTH_SHORT).show()
            }
        }
    )

    if (selectedPartyForHistory != null) {
        BackHandler { selectedPartyForHistory = null }
        PartyTransactionsOverlay(
            viewModel = viewModel, 
            partyId = selectedPartyForHistory!!.first, 
            partyName = selectedPartyForHistory!!.second,
            filterTagIds = selectedTagIds.toList(),
            filterCategoryIds = selectedCategoryIds.toList(),
            asOfDate = asOfDate
        ) { selectedPartyForHistory = null }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") }
                Text("On Account - Parties", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.FileDownload, "Export") }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Both", "Receivable", "Payable").forEach { t ->
                    FilterChip(selected = filterType == t, onClick = { filterType = t }, label = { Text(t, style = MaterialTheme.typography.labelSmall) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                FilterChip(selected = selectedTagIds.isNotEmpty(), onClick = { showTagFilter = true }, label = { Text("Tags (${selectedTagIds.size})") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = selectedCategoryIds.isNotEmpty(), onClick = { showCategoryFilter = true }, label = { Text("Categories (${selectedCategoryIds.size})") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
            }
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(displayParties) { p ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { selectedPartyForHistory = p.id to p.name }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name); Text(viewModel.formatAmount(p.balance), color = if (p.balance >= 0) Color(0xFF4CAF50) else Color.Red)
                    }
                    Divider()
                }
            }
        }
    }

    if (showTagFilter) {
        AlertDialog(onDismissRequest = { showTagFilter = false }, title = { Text("Filter by Tags") }, text = {
            Box(Modifier.height(300.dp)) {
                LazyColumn {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedTagIds.clear() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedTagIds.isEmpty(), onClick = null); Text("All", modifier = Modifier.padding(start = 8.dp))
                        }
                        Divider()
                    }
                    items(allTags) { tag ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { if (selectedTagIds.contains(tag.id)) selectedTagIds.remove(tag.id) else selectedTagIds.add(tag.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedTagIds.contains(tag.id), onCheckedChange = null); Text(tag.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showTagFilter = false }) { Text("Done") } })
    }

    if (showCategoryFilter) {
        AlertDialog(onDismissRequest = { showCategoryFilter = false }, title = { Text("Filter by Categories") }, text = {
            Box(Modifier.height(300.dp)) {
                LazyColumn {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedCategoryIds.clear() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedCategoryIds.isEmpty(), onClick = null); Text("All", modifier = Modifier.padding(start = 8.dp))
                        }
                        Divider()
                    }
                    items(allCategories) { cat ->
                        Row(modifier = Modifier.fillMaxWidth().clickable { if (selectedCategoryIds.contains(cat.id)) selectedCategoryIds.remove(cat.id) else selectedCategoryIds.add(cat.id) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = selectedCategoryIds.contains(cat.id), onCheckedChange = null); Text("${cat.name} (${cat.type})", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { showCategoryFilter = false }) { Text("Done") } })
    }

    if (showExportDialog) {
        AlertDialog(onDismissRequest = { showExportDialog = false }, title = { Text("Export On Account Data") }, text = { Text("Choose export type:") },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { showExportDialog = false; balancesLauncher.launch("party_balances_${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export Party Balances") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showExportDialog = false; txnsLauncher.launch("party_transactions_${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Export Transactions Detail") }
                }
            }, dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyTransactionsOverlay(viewModel: ExpenseViewModel, partyId: Int, partyName: String, filterTagIds: List<Int>, filterCategoryIds: List<Int>, asOfDate: String, onBack: () -> Unit) {
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val partyTransactions = remember(allTransactions, partyId, filterTagIds, filterCategoryIds, asOfDate) {
        allTransactions.filter { t ->
            (t.transaction.partyId == partyId || t.transaction.toPartyId == partyId) &&
            t.transaction.date <= asOfDate &&
            (filterTagIds.isEmpty() || (t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in filterTagIds } == true)) &&
            (filterCategoryIds.isEmpty() || t.transaction.categoryId in filterCategoryIds)
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(partyName) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") } }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            items(partyTransactions) { detail ->
                Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = detail }) { TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = true) }
            }
        }
    }
}

@Composable
fun AccountBalanceRow(balance: AccountBalance, viewModel: ExpenseViewModel, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text((balance.icon ?: "🏦") + " " + balance.name); Text(viewModel.formatAmount(balance.balance), color = if (balance.balance >= 0) Color(0xFF4CAF50) else Color.Red)
    }
    Divider()
}

@Composable
fun SubtotalRow(label: String, total: Double, viewModel: ExpenseViewModel, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.Bold); Text(viewModel.formatAmount(total), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AccountDetailView(viewModel: ExpenseViewModel, account: AccountBalance, onBack: () -> Unit, initialAsOfDate: String? = null) {
    var month by remember { mutableStateOf(if (initialAsOfDate != null) LocalDate.parse(initialAsOfDate) else LocalDate.now()) }
    var startDate by remember { mutableStateOf(month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)) }
    
    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val allAccounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    
    val filterTagIds = remember { mutableStateListOf<Int>() }
    val filterAccountIds = remember { mutableStateListOf<Int>() }
    
    var showTagFilter by remember { mutableStateOf(false) }
    var showAccountFilter by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val rawTransactions by viewModel.getAccountTransactions(account.id, startDate, endDate).collectAsState(initial = emptyList())
    
    val transactions = remember(rawTransactions, filterTagIds.toList(), filterAccountIds.toList()) {
        rawTransactions.filter { t ->
            (filterTagIds.isEmpty() || t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in filterTagIds } == true) &&
            (filterAccountIds.isEmpty() || t.transaction.accountId in filterAccountIds || t.transaction.toAccountId in filterAccountIds)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("text/csv"), onResult = { uri -> uri?.let { exportToUri(context, transactions, "CSV", it) } })
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") }
            Text(account.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            
            IconButton(onClick = { showTagFilter = true }) {
                Icon(Icons.Default.Label, "", tint = if (filterTagIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { showAccountFilter = true }) {
                Icon(Icons.Default.AccountBalance, "", tint = if (filterAccountIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            IconButton(onClick = { 
                month = month.minusMonths(1)
                startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE) 
            }) { Icon(Icons.Default.ChevronLeft, "") }
            Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), modifier = Modifier.weight(1f).clickable { showFilterDialog = true }, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { 
                month = month.plusMonths(1)
                startDate = month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)
                endDate = month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE) 
            }) { Icon(Icons.Default.ChevronRight, "") }
            IconButton(onClick = { showFilterDialog = true }) { Icon(Icons.Default.DateRange, contentDescription = "Filter") }
            IconButton(onClick = { exportLauncher.launch("${account.name.replace(" ", "_")}_${month.format(DateTimeFormatter.ofPattern("MMM_yyyy"))}.csv") }) { Icon(Icons.Default.FileDownload, contentDescription = "Export") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 0.dp)) {
            items(transactions) { detail ->
                Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = detail }) { TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = true) }
            }
        }
    }
    if (showFilterDialog) { DateRangeFilterDialog(onDismiss = { showFilterDialog = false }, onApply = { s, e -> startDate = s; endDate = e; showFilterDialog = false }) }
    if (showTagFilter) { MultiSelectFilterDialog("Filter by Tags", allTags.map { it.id to it.name }, filterTagIds, onDismiss = { showTagFilter = false }) }
    if (showAccountFilter) { MultiSelectFilterDialog("Filter by Accounts", allAccounts.map { it.id to it.name }, filterAccountIds, onDismiss = { showAccountFilter = false }) }
}

@Composable
fun ExportView(viewModel: ExpenseViewModel) {
    var format by remember { mutableStateOf("CSV") }
    val transactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument(if (format == "CSV") "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), onResult = { uri -> uri?.let { exportToUri(context, transactions, format, it) } })
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Export Data", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(16.dp))
        Row {
            RadioButton(selected = format == "CSV", onClick = { format = "CSV" }); Text("CSV", modifier = Modifier.align(Alignment.CenterVertically)); Spacer(Modifier.width(16.dp))
            RadioButton(selected = format == "Excel", onClick = { format = "Excel" }); Text("Excel (XLSX)", modifier = Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = { exportLauncher.launch("fintrack_export_${System.currentTimeMillis()}.${if (format == "CSV") "csv" else "xlsx"}") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Select Destination and Export")
        }
    }
}

fun exportToUri(context: Context, data: List<TransactionWithDetails>, format: String, uri: Uri) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.bufferedWriter().use { out ->
                if (format == "CSV") {
                    out.write("TxnNum,Date,Time,Account,ToAccount,Party,Category,Type,Amount,Note\n")
                    data.forEach { d ->
                        val t = d.transaction
                        out.write("${t.transactionNumber ?: ""},${t.date},${t.time},${d.accountName},${d.toAccountName ?: ""},${d.partyName ?: ""},${d.categoryName ?: ""},${d.categoryType ?: "transfer"},${t.amount},\"${t.note ?: ""}\"\n")
                    }
                } else {
                    out.write("TxnNum\tDate\tTime\tAccount\tToAccount\tParty\tCategory\tType\tAmount\tNote\n")
                    data.forEach { d ->
                        val t = d.transaction
                        out.write("${t.transactionNumber ?: ""}\t${t.date}\t${t.time}\t${d.accountName}\t${d.toAccountName ?: ""}\t${d.partyName ?: ""}\t${d.categoryName ?: ""}\t${d.categoryType ?: "transfer"}\t${t.amount}\t${t.note ?: ""}\n")
                    }
                }
            }
        }
        Toast.makeText(context, "Export Successful!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun QrCodeImage(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { try { val encoder = BarcodeEncoder(); encoder.encodeBitmap(content, BarcodeFormat.QR_CODE, 512, 512) } catch (e: Exception) { null } }
    bitmap?.let { androidx.compose.foundation.Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = modifier) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DateRangeFilterDialog(onDismiss: () -> Unit, onApply: (String, String) -> Unit) {
    val context = LocalContext.current
    var start by remember { mutableStateOf(LocalDate.now().minusDays(7)) }
    var end by remember { mutableStateOf(LocalDate.now()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Filter by Date") }, text = {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { DatePickerDialog(context, { _, y, m, d -> start = LocalDate.of(y, m+1, d) }, start.year, start.monthValue - 1, start.dayOfMonth).show() }.padding(8.dp)) { Text("From: ${start.format(DateTimeFormatter.ISO_DATE)}") }
            Row(modifier = Modifier.fillMaxWidth().clickable { DatePickerDialog(context, { _, y, m, d -> end = LocalDate.of(y, m+1, d) }, end.year, end.monthValue - 1, end.dayOfMonth).show() }.padding(8.dp)) { Text("To: ${end.format(DateTimeFormatter.ISO_DATE)}") }
            Spacer(Modifier.height(8.dp)); Text("Shortcuts:", style = MaterialTheme.typography.labelSmall)
            val shortcuts = listOf(
                "Today" to { start = LocalDate.now(); end = LocalDate.now() }, 
                "Yesterday" to { start = LocalDate.now().minusDays(1); end = LocalDate.now().minusDays(1) }, 
                "Last 7 Days" to { start = LocalDate.now().minusDays(7); end = LocalDate.now() }, 
                "Last 2 Weeks" to { start = LocalDate.now().minusWeeks(2); end = LocalDate.now() }, 
                "This Month" to { start = LocalDate.now().withDayOfMonth(1); end = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()) }, 
                "Last Month" to { start = LocalDate.now().minusMonths(1).withDayOfMonth(1); end = LocalDate.now().minusMonths(1).with(TemporalAdjusters.lastDayOfMonth()) }, 
                "Last 2 Months" to { start = LocalDate.now().minusMonths(2); end = LocalDate.now() }, 
                "Last 6 Months" to { start = LocalDate.now().minusMonths(6); end = LocalDate.now() }, 
                "This Year" to { start = LocalDate.now().with(TemporalAdjusters.firstDayOfYear()); end = LocalDate.now() },
                "Last Year" to { start = LocalDate.now().minusYears(1).with(TemporalAdjusters.firstDayOfYear()); end = LocalDate.now().minusYears(1).with(TemporalAdjusters.lastDayOfYear()) }
            )
            androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth()) { shortcuts.forEach { (label, action) -> AssistChip(onClick = action, label = { Text(label) }, modifier = Modifier.padding(2.dp)) } }
        }
    }, confirmButton = { Button(onClick = { onApply(start.format(DateTimeFormatter.ISO_DATE), end.format(DateTimeFormatter.ISO_DATE)) }) { Text("Apply") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListOverlay(title: String, transactions: List<TransactionWithDetails>, viewModel: ExpenseViewModel, showTxnNumber: Boolean = false, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "") } })
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            items(transactions) { detail ->
                Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = detail }) {
                    TransactionRow(detail = detail, viewModel = viewModel, showTxnNumber = showTxnNumber)
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) { if (context is Activity) return context; context = context.baseContext }
    return null
}

@Composable
fun TransactionCalendarView(month: LocalDate, transactions: List<TransactionWithDetails>, viewModel: ExpenseViewModel) {
    val dailyTotals = remember(transactions) {
        transactions.groupBy { it.transaction.date }.mapValues { entry ->
            val inc = entry.value.filter { it.categoryType == "income" }.sumOf { it.transaction.amount }
            val exp = entry.value.filter { it.categoryType == "expense" }.sumOf { it.transaction.amount }
            Pair(inc, exp)
        }
    }

    val firstDayOfMonth = month.withDayOfMonth(1)
    val lastDayOfMonth = month.with(TemporalAdjusters.lastDayOfMonth())
    
    // Sun=0, Mon=1, ..., Sat=6
    val startOffset = if (firstDayOfMonth.dayOfWeek.value == 7) 0 else firstDayOfMonth.dayOfWeek.value

    val days = (1..lastDayOfMonth.dayOfMonth).toList()
    
    val weeks = mutableListOf<List<Int?>>()
    var currentWeek = mutableListOf<Int?>()
    
    for (i in 0 until startOffset) {
        currentWeek.add(null)
    }
    
    for (day in days) {
        currentWeek.add(day)
        if (currentWeek.size == 7) {
            weeks.add(currentWeek)
            currentWeek = mutableListOf()
        }
    }
    
    if (currentWeek.isNotEmpty()) {
        while (currentWeek.size < 7) {
            currentWeek.add(null)
        }
        weeks.add(currentWeek)
    }

    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
    weeks.forEach { week ->
        Row(modifier = Modifier.fillMaxWidth().height(90.dp)) {
            week.forEach { day ->
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, Color.Gray.copy(alpha = 0.2f)),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (day != null) {
                        val dateStr = month.withDayOfMonth(day).format(DateTimeFormatter.ISO_DATE)
                        val totals = dailyTotals[dateStr]
                        
                        Column(modifier = Modifier.padding(4.dp)) {
                            Text(
                                text = day.toString(), 
                                fontSize = 12.sp, 
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            )
                            if (totals != null) {
                                Column(verticalArrangement = Arrangement.spacedBy((-2).dp)) {
                                    if (totals.second > 0) {
                                        Text(
                                            text = viewModel.formatAmount(totals.second).replace(".00", ""),
                                            color = Color(0xFFF44336),
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (totals.first > 0) {
                                        Text(
                                            text = viewModel.formatAmount(totals.first).replace(".00", ""),
                                            color = Color(0xFF4CAF50),
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}
