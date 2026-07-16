/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorialScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Tutorial & Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            TutorialSection(
                title = "Getting Started",
                content = "Welcome to FinTrack! This app helps you track expenses, manage budgets, and analyze your financial health locally on your device.\n\n" +
                        "1. **Accounts**: Set up your bank accounts, cash, and credit cards first.\n" +
                        "2. **Categories**: Define where your money goes (Food, Rent, etc.).\n" +
                        "3. **Transactions**: Use the '+' button to record daily spending or income."
            )

            TutorialSection(
                title = "Basic Financial Terms",
                content = "• **Asset**: Anything you own that has value (Cash, Bank Balance).\n" +
                        "• **Liability**: Money you owe (Credit Card debt, Loans).\n" +
                        "• **Networth**: Your Total Assets minus Total Liabilities.\n" +
                        "• **Budget**: A limit you set for spending in specific categories."
            )

            TutorialSection(
                title = "Advanced: Loan Setup",
                content = "Go to 'Loan & Subscriptions' to set up fixed-term loans. The app will automatically calculate the EMI (principal + interest) and remind you of due dates. You can enable 'Auto-Record' to have installments deducted from your accounts automatically."
            )

            TutorialSection(
                title = "Advanced: Credit Card Tracking",
                content = "When creating an account, select 'Credit Card' as the type. Input your credit limit and billing cycle. The app will alert you if your utilization exceeds 30%, helping you maintain a healthy credit score."
            )

            TutorialSection(
                title = "Best Practices",
                content = "• **Tags**: Use tags for temporary events like 'Vacation' or 'Project X' to track cross-category spending.\n" +
                        "• **AI Chat**: Ask our local AI to summarize your month or find specific historical transactions.\n" +
                        "• **Backups**: Regularly back up your database to your local storage or via WebDAV for safety."
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun TutorialSection(title: String, content: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )
        }
    }
}
