package com.openapps.fintrack.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.openapps.fintrack.data.TransactionWithDetails

@Composable
fun TransactionRow(detail: TransactionWithDetails, viewModel: ExpenseViewModel, showTxnNumber: Boolean = false) {
    val transaction = detail.transaction
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (showTxnNumber && !transaction.transactionNumber.isNullOrEmpty()) {
                Text(
                    text = "Txn: ${transaction.transactionNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = detail.categoryName ?: "Transfer",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${transaction.date} | ${detail.accountName}${if (detail.toAccountName != null) " -> " + detail.toAccountName else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            if (!transaction.note.isNullOrEmpty()) {
                Text(
                    text = transaction.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }
        }
        Text(
            text = viewModel.formatAmount(transaction.amount),
            style = MaterialTheme.typography.bodyLarge,
            color = when (detail.categoryType) {
                "income" -> Color(0xFF4CAF50)
                "expense" -> Color.Red
                else -> Color(0xFF2196F3)
            }
        )
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
