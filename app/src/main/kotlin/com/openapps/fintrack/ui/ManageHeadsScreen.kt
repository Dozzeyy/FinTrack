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

import androidx.compose.ui.res.stringResource
import com.openapps.fintrack.R
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.data.MajorHead
import com.openapps.fintrack.data.MinorHead

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageHeadsScreen(
    viewModel: ExpenseViewModel,
    onEditMajor: () -> Unit,
    onEditMinor: () -> Unit,
    onBack: () -> Unit
) {
    val cannotDeleteMandatoryHeadMsg = stringResource(R.string.msg_cannot_delete_mandatory_head)
    var selectedTab by remember { mutableStateOf(0) }
    val majorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    val minorHeads by viewModel.getAllMinorHeads().collectAsState(initial = emptyList())

    var showDeleteConfirm by remember { mutableStateOf<Any?>(null) }
    val context = LocalContext.current

    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text(stringResource(R.string.title_confirm_delete)) },
            text = { 
                val item = showDeleteConfirm
                val coreMajorHeadNames = listOf("Bank Accounts", "Investments", "Credit Cards", "Credit cards", "On Account (Loan)")
                val isCoreHead = item is MajorHead && coreMajorHeadNames.any { it.equals(item.name, ignoreCase = true) }
                val baseMsg = if (item is MajorHead && !item.name.equals("On Account (Loan)", ignoreCase = true)) {
                    stringResource(R.string.msg_delete_major_head_confirm)
                } else {
                    stringResource(R.string.msg_delete_header_confirm)
                }
                val msg = if (isCoreHead) "⚠️ Warning: Modifying or deleting core system Major Heads (Bank Accounts, Investments, Credit cards, On Account (Loan)) may disrupt linked features and accounts.\n\n$baseMsg" else baseMsg
                Text(msg) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        val item = showDeleteConfirm
                        if (item is MajorHead) {
                            if (item.name.equals("On Account (Loan)", ignoreCase = true)) {
                                Toast.makeText(context, cannotDeleteMandatoryHeadMsg, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.deleteMajorHeadAndRemap(item)
                            }
                        } else if (item is MinorHead) {
                            viewModel.deleteMinorHead(item)
                        }
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text(stringResource(R.string.btn_delete), color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_manage_headers)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (selectedTab == 0) {
                    viewModel.editingMajorHead = null
                    onEditMajor()
                } else {
                    viewModel.editingMinorHead = null
                    onEditMinor()
                }
            }) {
                Icon(Icons.Default.Add, stringResource(R.string.btn_add))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.label_major)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.label_minor)) })
            }

            if (selectedTab == 0) {
                Text(
                    text = "⚠️ Warning: Modifying or deleting core system Major Heads (Bank Accounts, Investments, Credit cards, On Account (Loan)) may disrupt linked features and accounts.",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    items(majorHeads) { head ->
                        HeaderItem(
                            name = head.name,
                            isEnabled = head.isEnabled,
                            onEdit = {
                                viewModel.editingMajorHead = head
                                onEditMajor()
                            },
                            onDelete = { showDeleteConfirm = head },
                            onToggle = { viewModel.toggleMajorHeadEnabled(head) }
                        )
                        Divider()
                    }
                } else {
                    items(minorHeads) { head ->
                        val majorName = majorHeads.find { it.id == head.majorHeadId }?.name ?: stringResource(R.string.label_unknown)
                        HeaderItem(
                            name = head.name,
                            nature = stringResource(R.string.label_major_colon_val, majorName),
                            isEnabled = head.isEnabled,
                            onEdit = {
                                viewModel.editingMinorHead = head
                                onEditMinor()
                            },
                            onDelete = { showDeleteConfirm = head },
                            onToggle = { viewModel.toggleMinorHeadEnabled(head) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderItem(
    name: String,
    nature: String? = null,
    isEnabled: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            if (nature != null) {
                Text(text = nature, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = isEnabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.btn_edit)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, stringResource(R.string.btn_delete)) }
        }
    }
}
