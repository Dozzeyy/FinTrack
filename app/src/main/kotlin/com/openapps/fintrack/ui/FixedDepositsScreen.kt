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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.R
import com.openapps.fintrack.data.FdDashboardItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedDepositsScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit,
    isEmbedded: Boolean = false
) {
    val fixedDeposits by viewModel.getFixedDeposits().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            if (!isEmbedded) {
                TopAppBar(
                    title = { Text(stringResource(R.string.fd_title_dashboard)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                        }
                    }
                )
            }
        }
    ) { padding ->
        val contentPadding = if (isEmbedded) PaddingValues(0.dp) else padding
        Column(modifier = Modifier.padding(contentPadding).fillMaxSize().padding(start = 16.dp, top = if (isEmbedded) 0.dp else 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (fixedDeposits.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.fd_no_active_fds), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(fixedDeposits) { fd ->
                        FdCard(fd, viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun FdCard(fd: FdDashboardItem, viewModel: ExpenseViewModel) {
    val today = LocalDate.now()
    val maturityDate = try { LocalDate.parse(fd.maturityDate) } catch (e: Exception) { null }
    val daysLeft = if (maturityDate != null) ChronoUnit.DAYS.between(today, maturityDate) else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fd.accountName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.fd_acc_last_4, fd.fdLast4 ?: "----"),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.fd_minor_head, fd.minorHeadName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val maturityStr = fd.maturityDate ?: stringResource(R.string.fd_na)
                    val maturityText = if (daysLeft != null) stringResource(R.string.fd_maturity_days_left, maturityStr, daysLeft) else stringResource(R.string.fd_maturity_date, maturityStr)
                    Text(maturityText, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.fd_initial, viewModel.formatAmount(fd.initialAmount)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.fd_outstanding, viewModel.formatAmount(fd.outstandingAmount)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
