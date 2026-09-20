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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.data.MajorHead
import com.openapps.fintrack.data.MinorHead

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHeadScreen(viewModel: ExpenseViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val parentMajorHeadMandatoryMsg = stringResource(R.string.msg_parent_major_head_mandatory)
    val editingMajor = viewModel.editingMajorHead
    val editingMinor = viewModel.editingMinorHead

    var selectedTab by remember { mutableStateOf(if (editingMinor != null) 1 else 0) }
    var name by remember { mutableStateOf(editingMajor?.name ?: editingMinor?.name ?: "") }
    var isEnabled by remember { mutableStateOf(editingMajor?.isEnabled ?: editingMinor?.isEnabled ?: true) }

    val majorHeads by viewModel.getAllMajorHeads().collectAsState(initial = emptyList())
    var selectedMajorId by remember { mutableStateOf(editingMinor?.majorHeadId) }
    
    LaunchedEffect(editingMinor) {
        if (editingMinor != null) {
            selectedMajorId = editingMinor.majorHeadId
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (editingMajor != null || editingMinor != null) stringResource(R.string.title_edit_header) else stringResource(R.string.title_add_header)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            if (editingMajor == null && editingMinor == null) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.label_major)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.label_minor)) })
                }
                Spacer(Modifier.height(16.dp))
            }

            val editingMajorName = editingMajor?.name.orEmpty()
            val coreMajorHeadNames = listOf("Bank Accounts", "Investments", "Credit Cards", "Credit cards", "On Account (Loan)")
            val isCoreHead = editingMajor != null && coreMajorHeadNames.any { it.equals(editingMajorName, ignoreCase = true) }
            if (isCoreHead) {
                Text(
                    text = "⚠️ Warning: Modifying or deleting core system Major Heads (Bank Accounts, Investments, Credit cards, On Account (Loan)) may disrupt linked features and accounts.",
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.label_name)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (selectedTab == 1) {
                var expanded by remember { mutableStateOf(false) }
                val majorName = majorHeads.find { it.id == selectedMajorId }?.name ?: stringResource(R.string.label_select_major_head)

                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    OutlinedTextField(
                        value = majorName,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.label_parent_major_head)) },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                    )
                    DropdownMenu(
                        expanded = expanded, 
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        majorHeads.forEach { head ->
                            DropdownMenuItem(
                                text = { Text(head.name) },
                                onClick = {
                                    selectedMajorId = head.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (name.isBlank()) return@Button
                    if (selectedTab == 0) {
                        viewModel.saveMajorHead(name, isEnabled)
                    } else {
                        if (selectedMajorId == null) {
                            Toast.makeText(context, parentMajorHeadMandatoryMsg, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.saveMinorHead(name, selectedMajorId!!, isEnabled)
                    }
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_save))
            }
        }
    }
}
