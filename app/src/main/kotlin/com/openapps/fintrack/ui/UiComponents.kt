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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import com.openapps.fintrack.data.AmortizationRow
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.openapps.fintrack.data.AccountBalance
import com.openapps.fintrack.data.RawTransactionHelper
import com.openapps.fintrack.data.Tag
import com.openapps.fintrack.data.TransactionLegacy
import com.openapps.fintrack.data.TemplateLegacy
import com.openapps.fintrack.data.TransactionWithDetails
import com.openapps.fintrack.data.PartyBalance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class TransactionWithRunningBalance(
    val detail: TransactionWithDetails,
    val runningBalance: Long?
)

@Composable
fun TransactionRow(
    detail: TransactionWithDetails, 
    viewModel: ExpenseViewModel, 
    showTxnNumber: Boolean = false,
    runningBalance: Long? = null,
    hideAccountName: Boolean = false,
    isInsideGroup: Boolean = false
) {
    val transaction = detail.transaction
    val typeColor = when (detail.categoryType) {
        "income" -> Color(0xFF4CAF50)
        "expense" -> Color.Red
        else -> Color(0xFF2196F3)
    }
    
    val backgroundColor = typeColor.copy(alpha = 0.05f)
    
    val isMultiEntry = !isInsideGroup && transaction.transactionNumber?.let { 
        it.startsWith("MEXP") || it.startsWith("MINC") 
    } == true

    val borderModifier = if (isMultiEntry) {
        Modifier.border(
            width = 2.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp)
        )
    } else Modifier

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).then(borderModifier)
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
                    (detail.categoryName ?: stringResource(R.string.label_transfer))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = headlineText + 
                               (if (detail.partyName != null) " (${detail.partyName})" else "") +
                               (if (detail.toPartyName != null) " -> ${detail.toPartyName}" else ""),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isMultiEntry) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Multi",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    
                    if (!hideAccountName) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val status = transaction.reconciliationStatus
                    val isVerified = status.equals("VERIFIED", ignoreCase = true) || (status.isBlank() && transaction.isReconciled)
                    val isVoid = status.equals("VOID", ignoreCase = true)

                    if (isVerified) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.status_verified),
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF4CAF50)
                        )
                        Spacer(Modifier.width(4.dp))
                    } else if (isVoid) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = stringResource(R.string.status_void),
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFFF44336)
                        )
                        Spacer(Modifier.width(4.dp))
                    }

                    Text(
                        text = viewModel.formatAmount(transaction.amount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isVoid) Color.Gray else typeColor,
                        textDecoration = if (isVoid) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                    )
                }
                
                if (runningBalance != null) {
                    Text(
                        text = stringResource(R.string.label_bal_colon) + viewModel.formatAmount(runningBalance),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }

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
            Text(stringResource(R.string.label_no_data_chart), color = Color.Gray)
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

    val maxAbsVal = accounts.maxOfOrNull { kotlin.math.abs(it.balance).toDouble() / 100.0 }?.coerceAtLeast(1.0) ?: 1.0

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
                        
                        val barWidthFraction = ((kotlin.math.abs(account.balance).toDouble() / 100.0) / maxAbsVal).toFloat()
                        
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
fun TagTargetBarChart(
    data: List<Pair<Tag, Double>>,
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier.fillMaxWidth().height(200.dp)
) {
    if (data.isEmpty()) return
    
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val primaryColor = MaterialTheme.colorScheme.primary
    
    val maxVal = data.maxOf { Math.max(it.first.targetNumber ?: 0.0, it.second) }.coerceAtLeast(1.0)
    
    Canvas(modifier = modifier.padding(16.dp).padding(start = 48.dp, bottom = 24.dp)) {
        val width = size.width
        val height = size.height
        val spacing = width / data.size
        val barWidth = spacing * 0.6f
        
        data.forEachIndexed { index, (tag, current) ->
            val target = tag.targetNumber ?: 1.0
            val x = index * spacing + (spacing - barWidth) / 2
            
            val targetHeight = ((target / maxVal) * height).toFloat()
            val currentHeight = ((current / maxVal) * height).toFloat().coerceAtMost(height)
            
            // Draw target bar (light)
            drawRoundRect(
                color = primaryColor.copy(alpha = 0.1f),
                topLeft = androidx.compose.ui.geometry.Offset(x, height - targetHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, targetHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
            
            // Draw current progress (water filling)
            val color = if (current >= target) Color(0xFF4CAF50) else primaryColor
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, height - currentHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, currentHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
            
            // Target line
            drawLine(
                color = Color.White,
                start = androidx.compose.ui.geometry.Offset(x, height - targetHeight),
                end = androidx.compose.ui.geometry.Offset(x + barWidth, height - targetHeight),
                strokeWidth = 2.dp.toPx()
            )

            // Label
            val textResult = textMeasurer.measure(tag.name.take(6), style = labelStyle)
            drawText(
                textLayoutResult = textResult,
                topLeft = androidx.compose.ui.geometry.Offset(x + (barWidth - textResult.size.width) / 2, height + 4.dp.toPx())
            )
            
            // Amount label on top
            val amtText = viewModel.formatAmountWhole(current)
            val amtResult = textMeasurer.measure(amtText, style = labelStyle.copy(fontSize = 8.sp))
            drawText(
                textLayoutResult = amtResult,
                topLeft = androidx.compose.ui.geometry.Offset(x + (barWidth - amtResult.size.width) / 2, height - currentHeight - amtResult.size.height - 2.dp.toPx())
            )
        }

        // Draw Y axis labels
        val yLabels = listOf(maxVal, maxVal / 2, 0.0)
        yLabels.forEach { valY ->
            val yPos = height - ((valY / maxVal) * height).toFloat()
            val labelText = if (valY >= 1000000) String.format("%.1fM", valY / 1000000) else if (valY >= 1000) String.format("%.1fk", valY / 1000) else valY.toInt().toString()
            val textResult = textMeasurer.measure(labelText, style = labelStyle)
            drawText(
                textLayoutResult = textResult,
                topLeft = androidx.compose.ui.geometry.Offset(-48.dp.toPx(), yPos - textResult.size.height / 2)
            )
        }
    }
}

@Composable
fun LineChart(
    data: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier.fillMaxWidth().height(200.dp),
    lineColor: Color = MaterialTheme.colorScheme.primary,
    pointColor: Color = MaterialTheme.colorScheme.secondary,
    showValuesAbovePoints: Boolean = false
) {
    if (data.isEmpty()) return

    val actualMin = data.minOrNull() ?: 0.0
    val actualMax = data.maxOrNull() ?: 0.0
    val rawRange = actualMax - actualMin
    val minVal = actualMin - (if (rawRange == 0.0) 1.0 else rawRange) * 0.1
    val maxVal = actualMax + (if (rawRange == 0.0) 1.0 else rawRange) * 0.1
    val range = (maxVal - minVal).coerceAtLeast(1.0)
    
    val textMeasurer = rememberTextMeasurer()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = onSurfaceColor, 
        fontSize = 10.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )

    //Width to maintain gap
    val minGap = 60.dp
    val scrollState = rememberScrollState()
    
    Box(modifier.horizontalScroll(scrollState)) {
        Canvas(modifier = Modifier
            .widthIn(min = 300.dp)
            .width(maxOf(300.dp, (data.size * 60).dp))
            .fillMaxHeight()
            .padding(horizontal = 32.dp)
            .padding(top = 32.dp, bottom = 48.dp)
        ) {
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
                    strokeWidth = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }

            // Draw points, values and labels
            points.forEachIndexed { index, point ->
                drawCircle(
                    color = pointColor,
                    radius = 3.dp.toPx(),
                    center = point
                )
                
                val valueText = if (abs(data[index]) >= 1000000) {
                    String.format(java.util.Locale.US, "%.1fM", data[index] / 1000000)
                } else if (abs(data[index]) >= 1000) {
                    String.format(java.util.Locale.US, "%.1fk", data[index] / 1000)
                } else {
                    data[index].roundToInt().toString()
                }
                
                if (showValuesAbovePoints) {
                    val valLayout = textMeasurer.measure(valueText, style = labelStyle)
                    drawText(
                        textLayoutResult = valLayout,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            point.x - valLayout.size.width / 2,
                            point.y - valLayout.size.height - 4.dp.toPx()
                        )
                    )
                } else if (index == 0 || index == data.size - 1 || data.size < 10) {

                    val valLayout = textMeasurer.measure(valueText, style = labelStyle)
                    drawText(
                        textLayoutResult = valLayout,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            -24.dp.toPx(),
                            point.y - valLayout.size.height / 2
                        )
                    )
                }

                // X-axis label
                if (labels.size > index) {
                    val labelLayout = textMeasurer.measure(labels[index], style = labelStyle)
                    drawText(
                        textLayoutResult = labelLayout,
                        topLeft = androidx.compose.ui.geometry.Offset(
                            point.x - labelLayout.size.width / 2,
                            height + 8.dp.toPx()
                        )
                    )
                }
            }
            
            // Draw baseline
            drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(0f, height),
                end = androidx.compose.ui.geometry.Offset(width, height),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryView(
    viewModel: ExpenseViewModel, 
    onOpenDrawer: (() -> Unit)? = null,
    initialCategoryId: Int? = null,
    initialAccountId: Int? = null,
    onBack: (() -> Unit)? = null,
    isEmbedded: Boolean = false
) {
    var month by remember { mutableStateOf(LocalDate.now()) }
    var startDate by remember { mutableStateOf(month.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE)) }
    var endDate by remember { mutableStateOf(month.with(TemporalAdjusters.lastDayOfMonth()).format(DateTimeFormatter.ISO_DATE)) }
    var showFilterDialog by remember { mutableStateOf(false) }
    val filterTypes = remember { mutableStateListOf<String>() }
    
    val listLabel = stringResource(R.string.label_list)
    val calendarLabel = stringResource(R.string.label_calendar)
    var viewMode by remember { mutableStateOf<String>(listLabel) } 

    val allTags by viewModel.getEnabledTags().collectAsState(initial = emptyList())
    val allAccounts by viewModel.getEnabledAccounts().collectAsState(initial = emptyList())
    
    val selectedTagIds = remember { mutableStateListOf<Int>() }
    val selectedAccountIds = remember { 
        mutableStateListOf<Int>().apply {
            if (initialAccountId != null) add(initialAccountId)
        }
    }
    val selectedCategoryIds = remember {
        mutableStateListOf<Int>().apply {
            if (initialCategoryId != null) add(initialCategoryId)
        }
    }
    
    var showTagFilterDialog by remember { mutableStateOf(false) }
    var showAccountFilterDialog by remember { mutableStateOf(false) }

    var showCrossReferenceScreen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val minorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())
    val majorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    
    val onAccountIds = remember(allAccounts, minorHeads, majorHeads) {
        val onAccountMajorId = majorHeads.find { it.name.contains("On Account", ignoreCase = true) }?.id ?: 6
        val onAccountMinorIds = minorHeads.filter { it.majorHeadId == onAccountMajorId }.map { it.id }
        allAccounts.filter { it.minorHeadId in onAccountMinorIds }.map { it.id }.toSet()
    }

    val transactions by viewModel.getFilteredTransactions(startDate, endDate).collectAsState(initial = emptyList())
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<Int>() }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val filteredTransactions = remember(transactions, filterTypes.toList(), selectedTagIds.toList(), selectedAccountIds.toList(), selectedCategoryIds.toList()) {
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

        if (selectedCategoryIds.isNotEmpty()) {
            list = list.filter { detail ->
                detail.transaction.categoryId in selectedCategoryIds
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
        if (showCrossReferenceScreen) {
            BackHandler { showCrossReferenceScreen = false }
            CrossReferenceScreen(
                viewModel = viewModel,
                onBack = { showCrossReferenceScreen = false },
                onNavigateToTransaction = { id ->
                    scope.launch {
                        val detail = viewModel.repository.getTransactionWithDetails(id)
                        if (detail != null) {
                            viewModel.selectedTransactionDetail = detail
                            showCrossReferenceScreen = false
                        }
                    }
                }
            )
        } else {
            if (onBack != null || onOpenDrawer != null) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isMultiSelectMode) {
                            IconButton(onClick = { isMultiSelectMode = false; selectedIds.clear() }) {
                                Icon(Icons.Default.Close, stringResource(R.string.btn_cancel))
                            }
                            Text(stringResource(R.string.label_num_selected, selectedIds.size), style = MaterialTheme.typography.titleMedium)
                        } else if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                            }
                            Text(stringResource(R.string.menu_transactions), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        } else if (onOpenDrawer != null) {
                            IconButton(onClick = onOpenDrawer) {
                                Icon(Icons.Default.Menu, stringResource(R.string.menu_home))
                            }
                            Text(stringResource(R.string.menu_transactions), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isMultiSelectMode) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { expanded = true }) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.label_more))
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_mark_as_verified)) },
                                    onClick = { 
                                        expanded = false
                                        viewModel.updateBulkTransactionStatus(selectedIds.toList(), "VERIFIED")
                                        isMultiSelectMode = false
                                        selectedIds.clear()
                                    },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50)) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_mark_as_void)) },
                                    onClick = { 
                                        expanded = false
                                        viewModel.updateBulkTransactionStatus(selectedIds.toList(), "VOID")
                                        isMultiSelectMode = false
                                        selectedIds.clear()
                                    },
                                    leadingIcon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFF44336)) }
                                )
                                if (!viewModel.disableTransactionDeletion) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.btn_delete), color = Color.Red) },
                                        onClick = { 
                                            expanded = false
                                            showDeleteConfirm = true 
                                        },
                                        leadingIcon = { Icon(Icons.Default.Delete, stringResource(R.string.btn_delete), tint = Color.Red) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Month Navigation
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)) {
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

                IconButton(onClick = { viewMode = if (viewMode == listLabel) calendarLabel else listLabel }) {
                    Icon(if (viewMode == listLabel) Icons.Default.CalendarMonth else Icons.Default.FormatListBulleted, contentDescription = stringResource(R.string.label_toggle_view))
                }

                IconButton(onClick = { showFilterDialog = true }) {
                    Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.label_filter))
                }

                IconButton(onClick = { showAccountFilterDialog = true }) {
                    Box {
                        Icon(
                            imageVector = Icons.Default.AccountBalance, 
                            contentDescription = stringResource(R.string.title_filter_by_accounts),
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
                    Icon(Icons.Default.FileDownload, contentDescription = stringResource(R.string.label_export))
                }
            }

            if (viewMode == listLabel) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Income", "Expense", "Transfer").forEach { type ->
                        val isSelected = filterTypes.contains(type)
                        val label = when(type) {
                            "Income" -> stringResource(R.string.label_income)
                            "Expense" -> stringResource(R.string.label_expense)
                            else -> stringResource(R.string.label_transfer)
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { 
                                if (isSelected) {
                                    filterTypes.remove(type)
                                } else {
                                    filterTypes.add(type)
                                }
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    
                    FilterChip(
                        selected = selectedTagIds.isNotEmpty(),
                        onClick = { showTagFilterDialog = true },
                        label = { Text(stringResource(R.string.label_tags), style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (selectedTagIds.isNotEmpty()) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) }
                    )
                }

                val singleAccountId = if (selectedAccountIds.size == 1) selectedAccountIds.first() else null
                
                val prevDate = try { LocalDate.parse(startDate).minusDays(1).format(DateTimeFormatter.ISO_DATE) } catch(e: Exception) { startDate }
                val openingBalances by if (singleAccountId != null) viewModel.getAccountBalances(prevDate).collectAsState(initial = emptyList()) else remember { mutableStateOf(emptyList<AccountBalance>()) }
                val openingBalance = remember(openingBalances, singleAccountId) { openingBalances.find { it.id == singleAccountId }?.balance ?: 0L }

                val transactionsWithRunningBalance = remember(filteredTransactions, openingBalance, singleAccountId) {
                    if (singleAccountId == null) {
                        filteredTransactions.map { TransactionWithRunningBalance(it, null) }
                    } else {
                        val sorted = filteredTransactions.sortedWith(compareBy({ it.transaction.date }, { it.transaction.time }, { it.transaction.id }))
                        var currentBal = openingBalance
                        sorted.map { detail ->
                            val amount = detail.transaction.amountMinorUnits ?: (detail.transaction.amount * 100).toLong()
                            val isIncoming = detail.transaction.toAccountId == singleAccountId || (detail.transaction.accountId == singleAccountId && detail.categoryType == "income")
                            val isOutgoing = detail.transaction.accountId == singleAccountId && detail.categoryType != "income"
                            
                            if (isIncoming) currentBal += amount
                            else if (isOutgoing) currentBal -= amount
                            
                            TransactionWithRunningBalance(detail, currentBal)
                        }.reversed()
                    }
                }

                if (singleAccountId != null) {
                    val additionsCount = transactionsWithRunningBalance.sumOf { item -> 
                        if (item.detail.transaction.toAccountId == singleAccountId || (item.detail.transaction.accountId == singleAccountId && item.detail.categoryType == "income")) 
                            (item.detail.transaction.amountMinorUnits ?: (item.detail.transaction.amount * 100).toLong()) else 0L 
                    }
                    val deletionsCount = transactionsWithRunningBalance.sumOf { item -> 
                        if (item.detail.transaction.accountId == singleAccountId && item.detail.categoryType != "income") 
                            (item.detail.transaction.amountMinorUnits ?: (item.detail.transaction.amount * 100).toLong()) else 0L 
                    }
                    val closingBalanceCalc = openingBalance + additionsCount - deletionsCount

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_opening), style = MaterialTheme.typography.labelSmall)
                                Text(stringResource(R.string.label_additions_plus), style = MaterialTheme.typography.labelSmall)
                                Text(stringResource(R.string.label_deletions_minus), style = MaterialTheme.typography.labelSmall)
                                Text(stringResource(R.string.label_closing), style = MaterialTheme.typography.labelSmall)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(viewModel.formatAmount(openingBalance), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(viewModel.formatAmount(additionsCount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                Text(viewModel.formatAmount(deletionsCount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Red)
                                Text(viewModel.formatAmount(closingBalanceCalc), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                val groupedEntries = remember(transactionsWithRunningBalance) {
                    val result = mutableListOf<List<TransactionWithRunningBalance>>()
                    var currentGroup = mutableListOf<TransactionWithRunningBalance>()

                    for (entry in transactionsWithRunningBalance) {
                        val txnNum = entry.detail.transaction.transactionNumber
                        val isMulti = txnNum != null && (txnNum.startsWith("MEXP") || txnNum.startsWith("MINC"))

                        if (currentGroup.isEmpty()) {
                            currentGroup.add(entry)
                        } else {
                            val lastTxnNum = currentGroup.last().detail.transaction.transactionNumber
                            if (isMulti && txnNum == lastTxnNum) {
                                currentGroup.add(entry)
                            } else {
                                result.add(currentGroup)
                                currentGroup = mutableListOf(entry)
                            }
                        }
                    }
                    if (currentGroup.isNotEmpty()) {
                        result.add(currentGroup)
                    }
                    result
                }

                val bottomPadding = if (isEmbedded) 88.dp else 16.dp
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = bottomPadding)
                ) {
                    groupedEntries.forEach { group ->
                        item {
                            val isMultiGroup = group.size > 1 || group.first().detail.transaction.transactionNumber?.let {
                                it.startsWith("MEXP") || it.startsWith("MINC")
                            } == true

                            if (isMultiGroup) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .border(
                                            width = 2.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        group.forEachIndexed { idx, entry ->
                                            var showTxnMenu by remember { mutableStateOf(false) }
                                            val isSelected = selectedIds.contains(entry.detail.transaction.id)
                                            val isLoanTxn = remember(entry.detail.transaction, onAccountIds) {
                                                entry.detail.transaction.accountId in onAccountIds || entry.detail.transaction.toAccountId in onAccountIds
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .combinedClickable(
                                                        onClick = {
                                                            if (isMultiSelectMode) {
                                                                if (isSelected) selectedIds.remove(entry.detail.transaction.id)
                                                                else selectedIds.add(entry.detail.transaction.id)
                                                                if (selectedIds.isEmpty()) isMultiSelectMode = false
                                                            } else {
                                                                viewModel.selectedTransactionDetail = entry.detail
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (!isMultiSelectMode) {
                                                                showTxnMenu = true
                                                            }
                                                        }
                                                    )
                                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(12.dp))
                                            ) {
                                                TransactionRow(
                                                    detail = entry.detail,
                                                    viewModel = viewModel,
                                                    showTxnNumber = false,
                                                    runningBalance = entry.runningBalance,
                                                    hideAccountName = singleAccountId != null,
                                                    isInsideGroup = true
                                                )

                                                DropdownMenu(expanded = showTxnMenu, onDismissRequest = { showTxnMenu = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text("Copy Raw String") },
                                                        onClick = {
                                                            showTxnMenu = false
                                                            scope.launch {
                                                                val headerDetail = viewModel.getTransactionHeaderWithLines(entry.detail.transaction.id).first()
                                                                val rawData = if (headerDetail != null) {
                                                                    RawTransactionHelper.fromTransactionWithLinesAndDetails(headerDetail, allTags)
                                                                } else {
                                                                    RawTransactionHelper.fromTransactionWithDetails(entry.detail, allTags)
                                                                }
                                                                val jsonStr = RawTransactionHelper.toJson(rawData)
                                                                clipboardManager.setText(AnnotatedString(jsonStr))
                                                                Toast.makeText(context, "Transaction Raw String copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.btn_multi_select)) },
                                                        onClick = {
                                                            isMultiSelectMode = true
                                                            selectedIds.add(entry.detail.transaction.id)
                                                            showTxnMenu = false
                                                        },
                                                        leadingIcon = { Icon(Icons.Default.Checklist, null) }
                                                    )
                                                    if (isLoanTxn) {
                                                        DropdownMenuItem(
                                                            text = { Text(stringResource(R.string.btn_cross_reference)) },
                                                            onClick = {
                                                                viewModel.loadCrossReference(entry.detail.transaction.id)
                                                                showCrossReferenceScreen = true
                                                                showTxnMenu = false
                                                            },
                                                            leadingIcon = { Icon(Icons.Default.Link, null) }
                                                        )
                                                    }
                                                }

                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Default.CheckCircle,
                                                        null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
                                                    )
                                                }
                                            }

                                            if (idx < group.size - 1) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(vertical = 2.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val entry = group.first()
                                var showTxnMenu by remember { mutableStateOf(false) }
                                val isSelected = selectedIds.contains(entry.detail.transaction.id)
                                val isLoanTxn = remember(entry.detail.transaction, onAccountIds) {
                                    entry.detail.transaction.accountId in onAccountIds || entry.detail.transaction.toAccountId in onAccountIds
                                }

                                Box(
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = {
                                                if (isMultiSelectMode) {
                                                    if (isSelected) selectedIds.remove(entry.detail.transaction.id)
                                                    else selectedIds.add(entry.detail.transaction.id)
                                                    if (selectedIds.isEmpty()) isMultiSelectMode = false
                                                } else {
                                                    viewModel.selectedTransactionDetail = entry.detail
                                                }
                                            },
                                            onLongClick = {
                                                if (!isMultiSelectMode) {
                                                    showTxnMenu = true
                                                }
                                            }
                                        )
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, RoundedCornerShape(12.dp))
                                ) {
                                    TransactionRow(
                                        detail = entry.detail,
                                        viewModel = viewModel,
                                        showTxnNumber = false,
                                        runningBalance = entry.runningBalance,
                                        hideAccountName = singleAccountId != null,
                                        isInsideGroup = false
                                    )

                                    DropdownMenu(expanded = showTxnMenu, onDismissRequest = { showTxnMenu = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Copy Raw String") },
                                            onClick = {
                                                showTxnMenu = false
                                                scope.launch {
                                                    val headerDetail = viewModel.getTransactionHeaderWithLines(entry.detail.transaction.id).first()
                                                    val rawData = if (headerDetail != null) {
                                                        RawTransactionHelper.fromTransactionWithLinesAndDetails(headerDetail, allTags)
                                                    } else {
                                                        RawTransactionHelper.fromTransactionWithDetails(entry.detail, allTags)
                                                    }
                                                    val jsonStr = RawTransactionHelper.toJson(rawData)
                                                    clipboardManager.setText(AnnotatedString(jsonStr))
                                                    Toast.makeText(context, "Transaction Raw String copied to clipboard!", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.btn_multi_select)) },
                                            onClick = {
                                                isMultiSelectMode = true
                                                selectedIds.add(entry.detail.transaction.id)
                                                showTxnMenu = false
                                            },
                                            leadingIcon = { Icon(Icons.Default.Checklist, null) }
                                        )
                                        if (isLoanTxn) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.btn_cross_reference)) },
                                                onClick = {
                                                    viewModel.loadCrossReference(entry.detail.transaction.id)
                                                    showCrossReferenceScreen = true
                                                    showTxnMenu = false
                                                },
                                                leadingIcon = { Icon(Icons.Default.Link, null) }
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                TransactionCalendarView(month, filteredTransactions, viewModel)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.title_delete_transactions)) },
            text = { Text(stringResource(R.string.msg_delete_transactions_confirm, selectedIds.size)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteTransactions(selectedIds.toList())
                    isMultiSelectMode = false
                    selectedIds.clear()
                    showDeleteConfirm = false
                }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text(stringResource(R.string.btn_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
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
            title = stringResource(R.string.title_filter_by_tags),
            items = allTags.map { it.id to it.name },
            selectedIds = selectedTagIds,
            onDismiss = { showTagFilterDialog = false }
        )
    }

    if (showAccountFilterDialog) {
        MultiSelectFilterDialog(
            title = stringResource(R.string.title_filter_by_accounts),
            items = allAccounts.map { it.id to it.name },
            selectedIds = selectedAccountIds,
            onDismiss = { showAccountFilterDialog = false }
        )
    }

    if (showCrossReferenceScreen) {
        BackHandler { showCrossReferenceScreen = false }
        CrossReferenceScreen(
            viewModel = viewModel,
            onBack = { showCrossReferenceScreen = false },
            onNavigateToTransaction = { id ->
                scope.launch {
                    val detail = viewModel.repository.getTransactionWithDetails(id)
                    if (detail != null) {
                        viewModel.selectedTransactionDetail = detail
                        showCrossReferenceScreen = false
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossReferenceScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit,
    onNavigateToTransaction: (Int) -> Unit
) {
    val details = viewModel.crossReferenceDetails
    val isLoading = viewModel.isLoadingCrossReference

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_cross_reference_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back)) }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (details.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.msg_no_cross_reference_found))
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
                items(details) { inv ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.title_original_invoice), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_amount_colon))
                                Text(viewModel.formatAmount(inv.invoiceAmount), fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_invoice_no_colon))
                                Text(inv.invoiceNumber ?: "N/A")
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_txn_no_colon))
                                Text(
                                    text = inv.invoiceTxnNumber ?: "N/A",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { onNavigateToTransaction(inv.invoiceId) }
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_date_colon))
                                Text(inv.invoiceDate)
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            Text(stringResource(R.string.label_clearance_history), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            
                            if (inv.clearingTransfers.isEmpty()) {
                                Text(stringResource(R.string.msg_no_clearing_entries), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            } else {
                                inv.clearingTransfers.forEach { transfer ->
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(
                                                text = stringResource(R.string.label_txn_no_colon) + (transfer.transferTxnNumber ?: "N/A"),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable { onNavigateToTransaction(transfer.transferTxnId) }
                                            )
                                            Text(transfer.transferDate, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(stringResource(R.string.label_applied_colon) + viewModel.formatAmount(transfer.amountApplied), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            if (transfer.otherAccountName != null) {
                                                Text(stringResource(R.string.label_acc_colon) + transfer.otherAccountName, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                    Divider(modifier = Modifier.alpha(0.2f))
                                }
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_total_cleared_colon), fontWeight = FontWeight.Bold)
                                Text(viewModel.formatAmount(inv.totalCleared), fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(stringResource(R.string.label_pending_amount_colon), fontWeight = FontWeight.Bold)
                                Text(viewModel.formatAmount(inv.pendingAmount), fontWeight = FontWeight.Bold, color = if (inv.pendingAmount > 0) Color.Red else Color.Gray)
                            }
                        }
                    }
                }
            }
        }
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
                    Text(stringResource(R.string.label_all))
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
fun AssetsLiabilitiesView(viewModel: ExpenseViewModel, isEmbedded: Boolean = false) {
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
                        transaction = com.openapps.fintrack.data.TransactionLegacy(date = asOfDate, time = "00:00", accountId = b.id, amount = b.balance.toDouble() / 100.0, amountMinorUnits = b.balance, note = context.getString(R.string.label_balance_export), categoryId = null),
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
            title = minor?.name ?: stringResource(R.string.label_minor_head),
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
            title = major?.name ?: stringResource(R.string.label_major_head),
            items = minors.map { it.name to it.balance },
            onItemClick = { name -> drillMinorId = minorBalances.find { it.name == name && it.majorHeadId == drillMajorId }?.id },
            onBack = { drillMajorId = null },
            viewModel = viewModel,
            asOfDate = asOfDate,
            microBalances = microBalances
        )
        return
    }

    val totalAssets = microBalances.filter { it.balance > 0 }.sumOf { it.balance }
    val totalLiabilities = microBalances.filter { it.balance < 0 }.sumOf { kotlin.math.abs(it.balance) }
    val netPosition = microBalances.sumOf { it.balance }
    val bottomPadding = if (isEmbedded) 88.dp else 16.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottomPadding)
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer, 
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.label_net_position),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        viewModel.formatAmount(netPosition),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.label_available_balance),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            viewModel.formatAmount(totalAssets),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.label_payable),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Red
                        )
                        Text(
                            viewModel.formatAmount(totalLiabilities),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewMode = (viewMode + 1) % 5 }) {
                    Icon(Icons.Default.Layers, stringResource(R.string.label_toggle_mode))
                }
                Text(
                    text = stringResource(R.string.label_as_of_colon, asOfDate),
                    modifier = Modifier.clickable {
                        val date = try { LocalDate.parse(asOfDate) } catch(e: Exception) { LocalDate.now() }
                        DatePickerDialog(context, { _, year, month, day ->
                            asOfDate = LocalDate.of(year, month + 1, day).format(DateTimeFormatter.ISO_DATE)
                        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }
                )
                IconButton(onClick = { exportLauncher.launch("balances_$asOfDate.csv") }) {
                    Icon(Icons.Default.FileDownload, stringResource(R.string.label_export))
                }
            }
        }

        when (viewMode) {
            0 -> { // Major only
                val pos = majorBalances.filter { it.balance > 0 }
                val neg = majorBalances.filter { it.balance <= 0 }
                if (pos.isNotEmpty()) {
                    item { Text(stringResource(R.string.label_assets), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(pos) { hb -> BalanceRow(hb.name, hb.balance, null, viewModel) { drillMajorId = hb.id } }
                }
                if (neg.isNotEmpty()) {
                    item { Text(stringResource(R.string.label_liabilities), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(neg) { hb -> BalanceRow(hb.name, hb.balance, null, viewModel) { drillMajorId = hb.id } }
                }
            }
            1 -> { // Major + Minor
                val posMajor = majorBalances.filter { it.balance > 0 }
                val negMajor = majorBalances.filter { it.balance <= 0 }
                
                if (posMajor.isNotEmpty()) {
                    item { Text(stringResource(R.string.label_assets), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    posMajor.forEach { mhb ->
                        item { Text(mhb.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 4.dp)) }
                        items(minorBalances.filter { it.majorHeadId == mhb.id }) { mihb ->
                            BalanceRow(mihb.name, mihb.balance, null, viewModel, indent = true) { drillMinorId = mihb.id }
                        }
                    }
                }
                if (negMajor.isNotEmpty()) {
                    item { Text(stringResource(R.string.label_liabilities), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
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
                    item { Text(stringResource(R.string.label_assets), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
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
                    item { Text(stringResource(R.string.label_liabilities), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
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
                    item { Text(stringResource(R.string.label_assets), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(pos) { hb -> BalanceRow(hb.name, hb.balance, null, viewModel) { drillMinorId = hb.id } }
                }
                if (neg.isNotEmpty()) {
                    item { Text(stringResource(R.string.label_liabilities), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(neg) { hb -> BalanceRow(hb.name, hb.balance, null, viewModel) { drillMinorId = hb.id } }
                }
            }
            4 -> { // Micro only
                val pos = microBalances.filter { it.balance > 0 }
                val neg = microBalances.filter { it.balance <= 0 }
                if (pos.isNotEmpty()) {
                    item { Text(stringResource(R.string.label_assets), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(pos) { hb -> BalanceRow(hb.name, hb.balance, hb.icon, viewModel) { drillMicroId = hb.id } }
                }
                if (neg.isNotEmpty()) {
                    item { Text(stringResource(R.string.label_liabilities), fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
                    items(neg) { hb -> BalanceRow(hb.name, hb.balance, hb.icon, viewModel) { drillMicroId = hb.id } }
                }
            }
        }
    }
}

@Composable
fun BalanceRow(name: String, balance: Long, icon: String?, viewModel: ExpenseViewModel, indent: Boolean = false, doubleIndent: Boolean = false, onClick: () -> Unit) {
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
    items: List<Pair<String, Long>>, 
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
                    val amt = it.transaction.amountMinorUnits ?: (it.transaction.amount * 100).toLong()
                    if (it.transaction.toAccountId == accountId || (it.transaction.accountId == accountId && it.categoryType == "income")) 
                        amt
                    else if (it.transaction.accountId == accountId)
                        -amt
                    else 0L
                }
            }.filter { it.second != 0L }
        }
    }

    val transferLabel = stringResource(R.string.label_transfer)
    val exportLabel = stringResource(R.string.label_export)

    val balancesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            val csv = StringBuilder("Item,Balance\n")
            filteredItems.forEach { csv.append("${it.first},${it.second}\n") }
            context.contentResolver.openOutputStream(it)?.use { out -> out.write(csv.toString().toByteArray()) }
            Toast.makeText(context, "$exportLabel ed", Toast.LENGTH_SHORT).show()
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
                csv.append("${t.transaction.date},${t.transaction.time},${t.accountName},${t.categoryName ?: transferLabel},${t.transaction.amount},\"${t.transaction.note ?: ""}\"\n")
            }
            context.contentResolver.openOutputStream(it)?.use { out -> out.write(csv.toString().toByteArray()) }
            Toast.makeText(context, "$exportLabel ed", Toast.LENGTH_SHORT).show()
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
            title = { Text(stringResource(R.string.title_export_data)) },
            text = { Text(stringResource(R.string.msg_export_ask)) },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false; balancesLauncher.launch("balances_${title}_$asOfDate.csv") }) { Text(stringResource(R.string.btn_balances)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false; txnsLauncher.launch("transactions_${title}_$asOfDate.csv") }) { Text(stringResource(R.string.btn_transactions)) }
            }
        )
    }

    if (showTagFilter) {
        MultiSelectFilterDialog(title = stringResource(R.string.title_filter_by_tags), items = allTags.map { it.id to it.name }, selectedIds = selectedTagIds, onDismiss = { showTagFilter = false })
    }
    if (showAccountFilter) {
        MultiSelectFilterDialog(title = stringResource(R.string.title_filter_by_accounts), items = allAccounts.map { it.id to it.name }, selectedIds = selectedAccountIds, onDismiss = { showAccountFilter = false })
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
            
            val inc = partyTxnsIn.sumOf { it.transaction.amountMinorUnits ?: (it.transaction.amount * 100).toLong() }
            val exp = partyTxnsOut.sumOf { it.transaction.amountMinorUnits ?: (it.transaction.amount * 100).toLong() }
            
            val opening = if (selectedTagIds.isEmpty() && selectedCategoryIds.isEmpty()) (p.openingBalanceMinorUnits ?: (p.openingBalance * 100).toLong()) else 0L
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
                Toast.makeText(context, context.getString(R.string.msg_export_success), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, context.getString(R.string.msg_export_success), Toast.LENGTH_SHORT).show()
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
                Text(stringResource(R.string.title_parties_on_account), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.FileDownload, stringResource(R.string.label_export)) }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Both", "Receivable", "Payable").forEach { t ->
                    val label = when(t) {
                        "Receivable" -> stringResource(R.string.label_receivable)
                        "Payable" -> stringResource(R.string.label_payable)
                        else -> stringResource(R.string.label_both)
                    }
                    FilterChip(selected = filterType == t, onClick = { filterType = t }, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                FilterChip(selected = selectedTagIds.isNotEmpty(), onClick = { showTagFilter = true }, label = { Text(stringResource(R.string.label_tags_with_count, selectedTagIds.size)) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = selectedCategoryIds.isNotEmpty(), onClick = { showCategoryFilter = true }, label = { Text(stringResource(R.string.label_categories_with_count, selectedCategoryIds.size)) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
            }
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(displayParties) { p ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { selectedPartyForHistory = Pair(p.id, p.name) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(p.name); Text(viewModel.formatAmount(p.balance), color = if (p.balance >= 0) Color(0xFF4CAF50) else Color.Red)
                    }
                    Divider()
                }
            }
        }
    }

    if (showTagFilter) {
        AlertDialog(onDismissRequest = { showTagFilter = false }, title = { Text(stringResource(R.string.title_filter_by_tags)) }, text = {
            Box(Modifier.height(300.dp)) {
                LazyColumn {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedTagIds.clear() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedTagIds.isEmpty(), onClick = null); Text(stringResource(R.string.label_all), modifier = Modifier.padding(start = 8.dp))
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
        }, confirmButton = { TextButton(onClick = { showTagFilter = false }) { Text(stringResource(R.string.btn_done)) } })
    }

    if (showCategoryFilter) {
        AlertDialog(onDismissRequest = { showCategoryFilter = false }, title = { Text(stringResource(R.string.title_filter_categories)) }, text = {
            Box(Modifier.height(300.dp)) {
                LazyColumn {
                    item {
                        Row(modifier = Modifier.fillMaxWidth().clickable { selectedCategoryIds.clear() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedCategoryIds.isEmpty(), onClick = null); Text(stringResource(R.string.label_all), modifier = Modifier.padding(start = 8.dp))
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
        }, confirmButton = { TextButton(onClick = { showCategoryFilter = false }) { Text(stringResource(R.string.btn_done)) } })
    }

    if (showExportDialog) {
        AlertDialog(onDismissRequest = { showExportDialog = false }, title = { Text(stringResource(R.string.title_export_on_account)) }, text = { Text(stringResource(R.string.msg_export_on_account_ask)) },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { showExportDialog = false; balancesLauncher.launch("party_balances_${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_export_party_balances)) }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { showExportDialog = false; txnsLauncher.launch("party_transactions_${LocalDate.now()}.csv") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_export_transactions_detail)) }
                }
            }, dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
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
fun SubtotalRow(label: String, total: Long, viewModel: ExpenseViewModel, color: Color) {
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
    
    // Get opening balance
    val prevDate = try { LocalDate.parse(startDate).minusDays(1).format(DateTimeFormatter.ISO_DATE) } catch(e: Exception) { startDate }
    val openingBalances by viewModel.getAccountBalances(prevDate).collectAsState(initial = emptyList())
    val openingBalance = remember(openingBalances, account.id) { openingBalances.find { it.id == account.id }?.balance ?: 0L }

    val transactionsWithRunningBalance = remember(rawTransactions, openingBalance, filterTagIds.toList(), filterAccountIds.toList()) {
        val filtered = rawTransactions.filter { t ->
            (filterTagIds.isEmpty() || t.transaction.tags?.split(",")?.mapNotNull { it.toIntOrNull() }?.any { it in filterTagIds } == true) &&
            (filterAccountIds.isEmpty() || t.transaction.accountId in filterAccountIds || t.transaction.toAccountId in filterAccountIds)
        }.sortedWith(compareBy({ it.transaction.date }, { it.transaction.time }, { it.transaction.id }))

        var currentBal = openingBalance
        filtered.map { detail ->
            val amount = detail.transaction.amountMinorUnits ?: (detail.transaction.amount * 100).toLong()
            val isIncoming = detail.transaction.toAccountId == account.id || (detail.transaction.accountId == account.id && detail.categoryType == "income")
            val isOutgoing = detail.transaction.accountId == account.id && detail.categoryType != "income"
            
            if (isIncoming) currentBal += amount
            else if (isOutgoing) currentBal -= amount
            
            TransactionWithRunningBalance(detail, currentBal)
        }.reversed()
    }

    val additionsTotal = remember(transactionsWithRunningBalance) { 
        transactionsWithRunningBalance.sumOf { item -> 
            if (item.detail.transaction.toAccountId == account.id || (item.detail.transaction.accountId == account.id && item.detail.categoryType == "income")) 
                (item.detail.transaction.amountMinorUnits ?: (item.detail.transaction.amount * 100).toLong()) else 0L 
        } 
    }
    val deletionsTotal = remember(transactionsWithRunningBalance) { 
        transactionsWithRunningBalance.sumOf { item -> 
            if (item.detail.transaction.accountId == account.id && item.detail.categoryType != "income") 
                (item.detail.transaction.amountMinorUnits ?: (item.detail.transaction.amount * 100).toLong()) else 0L 
        } 
    }
    val closingBalanceFinal = openingBalance + additionsTotal - deletionsTotal

    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument("text/csv"), onResult = { uri -> uri?.let { exportToUri(context, transactionsWithRunningBalance.map { it.detail }, "CSV", it) } })
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

        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.label_opening), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.label_additions_plus), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.label_deletions_minus), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.label_closing), style = MaterialTheme.typography.labelSmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(viewModel.formatAmount(openingBalance), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(viewModel.formatAmount(additionsTotal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Text(viewModel.formatAmount(deletionsTotal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.Red)
                    Text(viewModel.formatAmount(closingBalanceFinal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 0.dp)) {
            transactionsWithRunningBalance.forEach { entry ->
                item {
                    Box(modifier = Modifier.clickable { viewModel.selectedTransactionDetail = entry.detail }) { 
                        TransactionRow(
                            detail = entry.detail, 
                            viewModel = viewModel, 
                            showTxnNumber = true, 
                            runningBalance = entry.runningBalance,
                            hideAccountName = true
                        )
                    }
                }
            }
        }
    }
    if (showFilterDialog) { DateRangeFilterDialog(onDismiss = { showFilterDialog = false }, onApply = { s, e -> startDate = s; endDate = e; showFilterDialog = false }) }
    if (showTagFilter) { MultiSelectFilterDialog(stringResource(R.string.title_filter_by_tags), allTags.map { it.id to it.name }, filterTagIds, onDismiss = { showTagFilter = false }) }
    if (showAccountFilter) { MultiSelectFilterDialog(stringResource(R.string.title_filter_by_accounts), allAccounts.map { it.id to it.name }, filterAccountIds, onDismiss = { showAccountFilter = false }) }
}

@Composable
fun ExportView(viewModel: ExpenseViewModel) {
    var format by remember { mutableStateOf("CSV") }
    val transactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.CreateDocument(if (format == "CSV") "text/csv" else "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), onResult = { uri -> uri?.let { exportToUri(context, transactions, format, it) } })
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.title_export_data), style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(16.dp))
        Row {
            RadioButton(selected = format == "CSV", onClick = { format = "CSV" }); Text("CSV", modifier = Modifier.align(Alignment.CenterVertically)); Spacer(Modifier.width(16.dp))
            RadioButton(selected = format == "Excel", onClick = { format = "Excel" }); Text(stringResource(R.string.label_excel_xlsx), modifier = Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = { exportLauncher.launch("fintrack_export_${System.currentTimeMillis()}.${if (format == "CSV") "csv" else "xlsx"}") }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.btn_select_dest_export))
        }
    }
}

fun exportToUri(context: Context, data: List<TransactionWithDetails>, format: String, uri: Uri) {
    val successMsg = context.getString(R.string.msg_export_success)
    val failedMsgPrefix = context.getString(R.string.msg_export_failed_colon)

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
        Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, failedMsgPrefix.format(e.message), Toast.LENGTH_SHORT).show()
    }
}

fun exportScheduleToUri(context: Context, data: List<AmortizationRow>, uri: Uri) {

    val successMsg = context.getString(R.string.msg_export_success)
    val failedMsgPrefix = context.getString(R.string.msg_export_failed_colon)

    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.bufferedWriter(Charsets.UTF_8).use { out ->
                out.write("Period,Due Date,Opening Balance,Installment,Interest,Principal,Closing Balance\n")
                data.forEach { row ->
                    out.write("${row.period},${row.dueDate},${row.openingBalance},${row.installment},${row.interestPortion},${row.principalPortion},${row.closingBalance}\n")
                }
            }
        }
        Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, failedMsgPrefix.format(e.message), Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmortizationScheduleOverlay(
    title: String,
    schedule: List<AmortizationRow>,
    viewModel: ExpenseViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri -> uri?.let { exportScheduleToUri(context, schedule, it) } }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { exportLauncher.launch("Schedule_${title.replace(" ", "_")}.csv") }) {
                        Icon(Icons.Default.FileDownload, "Export")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
                Text("#", modifier = Modifier.width(30.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text("Date", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text("Installment", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text("Interest", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text("Balance", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(schedule) { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${row.period}", modifier = Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall)
                            Text(row.dueDate.format(DateTimeFormatter.ofPattern("dd/MM/yy")), modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
                            Text(viewModel.formatAmountWhole(row.installment), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
                            Text(viewModel.formatAmountWhole(row.interestPortion), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
                            Text(viewModel.formatAmountWhole(row.closingBalance), modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(modifier = Modifier.alpha(0.3f))
                    }
                }
            }
            
            val totalInstallment = schedule.sumOf { it.installment }
            val totalInterest = schedule.sumOf { it.interestPortion }
            
            Surface(tonalElevation = 8.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", modifier = Modifier.width(60.dp), fontWeight = FontWeight.ExtraBold)
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.title_filter_by_date)) }, text = {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { DatePickerDialog(context, { _, y, m, d -> start = LocalDate.of(y, m+1, d) }, start.year, start.monthValue - 1, start.dayOfMonth).show() }.padding(8.dp)) { Text(stringResource(R.string.label_from_colon, start.format(DateTimeFormatter.ISO_DATE))) }
            Row(modifier = Modifier.fillMaxWidth().clickable { DatePickerDialog(context, { _, y, m, d -> end = LocalDate.of(y, m+1, d) }, end.year, end.monthValue - 1, end.dayOfMonth).show() }.padding(8.dp)) { Text(stringResource(R.string.label_to_colon, end.format(DateTimeFormatter.ISO_DATE))) }
            Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.label_shortcuts), style = MaterialTheme.typography.labelSmall)
            val shortcuts = listOf(
                stringResource(R.string.label_today) to { start = LocalDate.now(); end = LocalDate.now() }, 
                stringResource(R.string.label_yesterday) to { start = LocalDate.now().minusDays(1); end = LocalDate.now().minusDays(1) }, 
                stringResource(R.string.label_last_7_days) to { start = LocalDate.now().minusDays(7); end = LocalDate.now() }, 
                stringResource(R.string.label_last_2_weeks) to { start = LocalDate.now().minusWeeks(2); end = LocalDate.now() }, 
                stringResource(R.string.label_this_month) to { start = LocalDate.now().withDayOfMonth(1); end = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth()) }, 
                stringResource(R.string.label_last_month) to { start = LocalDate.now().minusMonths(1).withDayOfMonth(1); end = LocalDate.now().minusMonths(1).with(TemporalAdjusters.lastDayOfMonth()) }, 
                stringResource(R.string.label_last_2_months) to { start = LocalDate.now().minusMonths(2); end = LocalDate.now() }, 
                stringResource(R.string.label_last_6_months) to { start = LocalDate.now().minusMonths(6); end = LocalDate.now() }, 
                stringResource(R.string.label_this_year) to { start = LocalDate.now().with(TemporalAdjusters.firstDayOfYear()); end = LocalDate.now() },
                stringResource(R.string.label_last_year) to { start = LocalDate.now().minusYears(1).with(TemporalAdjusters.firstDayOfYear()); end = LocalDate.now().minusYears(1).with(TemporalAdjusters.lastDayOfYear()) }
            )
            androidx.compose.foundation.layout.FlowRow(modifier = Modifier.fillMaxWidth()) { shortcuts.forEach { (label, action) -> AssistChip(onClick = action, label = { Text(label) }, modifier = Modifier.padding(2.dp)) } }
        }
    }, confirmButton = { Button(onClick = { onApply(start.format(DateTimeFormatter.ISO_DATE), end.format(DateTimeFormatter.ISO_DATE)) }) { Text(stringResource(R.string.btn_apply)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } })
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
            listOf(
                stringResource(R.string.label_sun),
                stringResource(R.string.label_mon),
                stringResource(R.string.label_tue),
                stringResource(R.string.label_wed),
                stringResource(R.string.label_thu),
                stringResource(R.string.label_fri),
                stringResource(R.string.label_sat)
            ).forEach { day ->
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
                                            text = viewModel.formatAmount(totals.second),
                                            color = Color(0xFFF44336),
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    if (totals.first > 0) {
                                        Text(
                                            text = viewModel.formatAmount(totals.first),
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
