package com.openapps.fintrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ExpenseViewModel, onBack: () -> Unit, onRequireAuth: (() -> Unit) -> Unit) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            
            val themes = listOf("Light", "Dark", "OLED Dark")
            var expanded by remember { mutableStateOf(false) }
            
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Theme: ${viewModel.currentTheme}")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    themes.forEach { theme ->
                        DropdownMenuItem(
                            text = { Text(theme) },
                            onClick = {
                                viewModel.updateTheme(theme)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("App Lock (Biometrics/PIN)")
                Switch(
                    checked = viewModel.appLockEnabled, 
                    onCheckedChange = { newValue ->
                        onRequireAuth {
                            viewModel.updateAppLockEnabled(newValue)
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Regional Settings", style = MaterialTheme.typography.titleMedium)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Use Millions/Billions System")
                Switch(checked = viewModel.useMillionsSystem, onCheckedChange = { viewModel.updateNumberSystem(it) })
            }

            Spacer(Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Show Assets on Home Screen")
                Switch(checked = viewModel.showAssetsOnHome, onCheckedChange = { viewModel.updateHomeScreenView(it) })
            }

            Spacer(Modifier.height(16.dp))
            Text("Data Entry", style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Multi-Tag Selection")
                Switch(checked = viewModel.multiTagEnabled, onCheckedChange = { viewModel.updateMultiTagEnabled(it) })
            }

            Spacer(Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Auto Read Messages")
                Switch(checked = viewModel.autoReadEnabled, onCheckedChange = { viewModel.updateAutoReadEnabled(it) })
            }
            
            if (viewModel.autoReadEnabled) {
                AutoReadCriteria(viewModel)
            }
        }
    }
}

@Composable
fun AutoReadCriteria(viewModel: ExpenseViewModel) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        OutlinedTextField(
            value = viewModel.smsCurrencies,
            onValueChange = { viewModel.updateSmsCurrencies(it) },
            label = { Text("Currencies (comma separated)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = viewModel.smsKeywords,
            onValueChange = { viewModel.updateSmsKeywords(it) },
            label = { Text("Keywords (comma separated)") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(8.dp))
        Text("Match Criteria:")
        Row {
            RadioButton(selected = viewModel.smsConditionType == "OR", onClick = { viewModel.updateSmsConditionType("OR") })
            Text("A or B", modifier = Modifier.padding(start = 8.dp).alignByBaseline())
            Spacer(Modifier.width(16.dp))
            RadioButton(selected = viewModel.smsConditionType == "AND", onClick = { viewModel.updateSmsConditionType("AND") })
            Text("A and B", modifier = Modifier.padding(start = 8.dp).alignByBaseline())
        }
        Row {
            RadioButton(selected = viewModel.smsConditionType == "ONLY_A", onClick = { viewModel.updateSmsConditionType("ONLY_A") })
            Text("Only A", modifier = Modifier.padding(start = 8.dp).alignByBaseline())
            Spacer(Modifier.width(16.dp))
            RadioButton(selected = viewModel.smsConditionType == "ONLY_B", onClick = { viewModel.updateSmsConditionType("ONLY_B") })
            Text("Only B", modifier = Modifier.padding(start = 8.dp).alignByBaseline())
        }
    }
}
