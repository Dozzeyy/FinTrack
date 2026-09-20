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

package com.openapps.fintrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "accounts", indices = [androidx.room.Index(value = ["name", "minorHeadId"], unique = true)])
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // asset, liability
    val openingBalance: Double = 0.0,
    val openingBalanceMinorUnits: Long? = null,
    val description: String? = null,
    val isEnabled: Boolean = true,
    val minorHeadId: Int? = null,
    val creditLimit: Double? = null,
    val creditLimitMinorUnits: Long? = null,
    val billingCycleStart: String? = null,
    val billingCycleEnd: String? = null,
    val paymentDueDate: String? = null,
    val icon: String? = null,
    val isEmergencyFund: Boolean = false,
    val defaultDueDays: Int? = null,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val last4Digits: String? = null,
    val ifscCode: String? = null,
    val branchName: String? = null,
    val websiteUrl: String? = null,
    val contactPerson: String? = null,
    val minimumBalance: Double? = null,
    val minimumBalanceMinorUnits: Long? = null,
    val maturityDate: String? = null,
    val bankName: String? = null
)

@Entity(tableName = "major_heads", indices = [androidx.room.Index(value = ["name"], unique = true)])
data class MajorHead(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "minor_heads", 
    indices = [androidx.room.Index(value = ["name", "majorHeadId"], unique = true)],
    foreignKeys = [
        ForeignKey(entity = MajorHead::class, parentColumns = ["id"], childColumns = ["majorHeadId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class MinorHead(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val majorHeadId: Int,
    val isEnabled: Boolean = true,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "categories", indices = [androidx.room.Index(value = ["name", "type"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // income, expense
    val description: String? = null,
    val isEnabled: Boolean = true,
    val icon: String? = null,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "tags", indices = [androidx.room.Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isEnabled: Boolean = true,
    val trackingType: String = "Both", // Income, Expense, Both
    val targetNumber: Double? = null,
    val targetNumberMinorUnits: Long? = null,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "parties")
data class Party(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val openingBalance: Double = 0.0,
    val openingBalanceMinorUnits: Long? = null,
    val isEnabled: Boolean = true,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String? = null,
    val amount: Double,
    val amountMinorUnits: Long? = null,
    val duration: String, // Daily, Weekly, Monthly, Half Yearly, Yearly
    val note: String? = null,
    val higherIsBetter: Boolean = false,
    val rolloverEnabled: Boolean = false,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "budget_categories",
    primaryKeys = ["budgetId", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = Budget::class, parentColumns = ["id"], childColumns = ["budgetId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class BudgetCategory(
    val budgetId: Int,
    val categoryId: Int
)

@Entity(
    tableName = "budget_accounts",
    primaryKeys = ["budgetId", "accountId"],
    foreignKeys = [
        ForeignKey(entity = Budget::class, parentColumns = ["id"], childColumns = ["budgetId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class BudgetAccount(
    val budgetId: Int,
    val accountId: Int
)

@Entity(tableName = "template_headers")
data class TemplateHeader(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // income, expense, transfer
    val note: String?,
    val subName: String? = null,
    val subFrequency: Int? = null,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "template_lines",
    foreignKeys = [
        ForeignKey(entity = TemplateHeader::class, parentColumns = ["id"], childColumns = ["headerId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class TemplateLine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val headerId: Int,
    val accountId: Int?,
    val toAccountId: Int?,
    val categoryId: Int?,
    val amount: Double?,
    val amountMinorUnits: Long? = null,
    val note: String?
)

@Entity(
    tableName = "template_tags",
    primaryKeys = ["headerId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TemplateHeader::class, parentColumns = ["id"], childColumns = ["headerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class TemplateTag(
    val headerId: Int,
    val tagId: Int
)

@Entity(tableName = "templates")
data class TemplateLegacy(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // income, expense, transfer
    val accountId: Int?,
    val toAccountId: Int?,
    val categoryId: Int?,
    val amount: Double?,
    val amountMinorUnits: Long? = null,
    val note: String?,
    val tags: String?,
    val multiEntries: String? = null, // catId:amount[:note]|...
    val subName: String? = null,
    val subFrequency: Int? = null
)

@Entity(tableName = "transaction_headers", indices = [androidx.room.Index(value = ["transactionNumber"], unique = true)])
data class TransactionHeader(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val transactionNumber: String,
    val date: String, // YYYY-MM-DD
    val time: String, // HH:mm
    val note: String?,
    val partyId: Int? = null,
    val toPartyId: Int? = null,
    val subName: String? = null,
    val subFrequency: Int? = null,
    val merchantName: String? = null,
    val invoiceNumber: String? = null,
    val dueDays: Int? = null,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(
    tableName = "transaction_lines",
    foreignKeys = [
        ForeignKey(entity = TransactionHeader::class, parentColumns = ["id"], childColumns = ["headerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ]
)
data class TransactionLine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val headerId: Int,
    val accountId: Int,
    val toAccountId: Int? = null, // For transfers
    val categoryId: Int?, // NULL for transfers
    val amount: Double,
    val amountMinorUnits: Long? = null,
    val amountOriginal: Double? = null,
    val amountOriginalMinorUnits: Long? = null,
    val currencyCode: String? = null,
    val amountBase: Double? = null,
    val amountBaseMinorUnits: Long? = null,
    val isNegotiated: Boolean = false,
    val negotiationAmountOriginal: Double? = null,
    val negotiationAmountOriginalMinorUnits: Long? = null,
    val isDiscretionary: Boolean = false,
    val note: String? = null,
    val tags: String? = null,
    val isReconciled: Boolean = false,
    val reconciliationStatus: String = "PENDING",
    val fdLast4: String? = null,
    val fdMaturityDate: String? = null
)

@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["headerId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TransactionHeader::class, parentColumns = ["id"], childColumns = ["headerId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Tag::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class TransactionTag(
    val headerId: Int,
    val tagId: Int
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ]
)
data class TransactionLegacy(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // YYYY-MM-DD
    val time: String, // HH:mm
    val accountId: Int,
    val toAccountId: Int? = null, // For transfers
    val categoryId: Int?, // NULL for transfers
    val amount: Double,
    val amountMinorUnits: Long? = null,
    val note: String?,
    val tags: String? = null, // Comma separated tag IDs
    val transactionNumber: String? = null,
    val partyId: Int? = null, // From Party (for On Account)
    val toPartyId: Int? = null, // To Party (for On Account Transfers)
    val subName: String? = null,
    val subFrequency: Int? = null, // in months
    val amountOriginal: Double? = null,
    val amountOriginalMinorUnits: Long? = null,
    val currencyCode: String? = null,
    val amountBase: Double? = null,
    val amountBaseMinorUnits: Long? = null,
    val editedAt: Long = System.currentTimeMillis(),
    val isNegotiated: Boolean = false,
    val negotiationAmountOriginal: Double? = null,
    val negotiationAmountOriginalMinorUnits: Long? = null,
    val merchantName: String? = null,
    val isDiscretionary: Boolean = false,
    val invoiceNumber: String? = null,
    val dueDays: Int? = null,
    val isReconciled: Boolean = false,
    val reconciliationStatus: String = "PENDING"
)

@Entity(
    tableName = "invoice_clearances",
    foreignKeys = [
        ForeignKey(entity = TransactionHeader::class, parentColumns = ["id"], childColumns = ["transferTransactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionHeader::class, parentColumns = ["id"], childColumns = ["invoiceTransactionId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class InvoiceClearance(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val transferTransactionId: Int,
    val invoiceTransactionId: Int,
    val amountCleared: Double,
    val amountClearedMinorUnits: Long? = null
)

@Entity(tableName = "exchange_rates")
data class ExchangeRate(
    @PrimaryKey val currencyCode: String,
    val rateToBase: Double,
    val baseCurrency: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "subscription_status")
data class SubscriptionStatus(
    @PrimaryKey val subName: String,
    val isStopped: Boolean = false,
    val isAutoRecordEnabled: Boolean = false,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "notebooks")
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val type: String = "text", // text, checklist, drawing
    val notebookId: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: String? = null, // Comma separated tag IDs
    val editedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val color: Int? = null,
    val isDeleted: Boolean = false
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val loanType: String, // LENDING, BORROWING
    val principalAmount: Double,
    val principalAmountMinorUnits: Long? = null,
    val interestRateAnnual: Double,
    val frequency: String, // MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY
    val installmentAmount: Double,
    val installmentAmountMinorUnits: Long? = null,
    val disbursementDate: Long,
    val firstRepaymentDate: Long,
    val totalInterestPaid: Double = 0.0,
    val totalInterestPaidMinorUnits: Long? = null,
    val totalPrincipalRepaid: Double = 0.0,
    val totalPrincipalRepaidMinorUnits: Long? = null,
    val outstandingBalance: Double,
    val outstandingBalanceMinorUnits: Long? = null,
    val nextDueDate: Long,
    val periodsTotal: Int,
    val periodsPassed: Int = 0,
    val accountId: Int, // Map to Account (Micro Head)
    val partyId: Int, // Map to Party (Counterparty)
    val gapMethod: String = "DAYS", // DAYS, MONTH_ODD
    val gapInterest: Double = 0.0,
    val gapInterestMinorUnits: Long? = null,
    val isActualEmiDifferent: Boolean = false,
    val actualRepaymentAmount: Double = 0.0,
    val actualRepaymentAmountMinorUnits: Long? = null,
    val isAutoRecordEnabled: Boolean = false,
    val sourceAccountId: Int? = null,
    val isUpdateBank: Boolean = true,
    val tags: String? = null,
    val notes: String? = null,
    val isClosed: Boolean = false,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "loan_repayments")
data class LoanRepayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amountPaid: Double,
    val amountPaidMinorUnits: Long? = null,
    val principalPortion: Double,
    val principalPortionMinorUnits: Long? = null,
    val interestPortion: Double,
    val interestPortionMinorUnits: Long? = null,
    val paymentDate: Long,
    val transactionId: Int? = null, // Linked transaction
    val isScheduled: Boolean = true,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "subscriptions_master")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val frequency: Int, // in months
    val note: String? = null,
    val isTransfer: Boolean = false,
    val isEnabled: Boolean = true,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "rules")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val msgFrom: String? = null,
    val textContaining: String,
    val type: String, // income, expense, transfer
    val categoryId: Int? = null,
    val accountId: Int? = null,
    val toAccountId: Int? = null,
    val partyId: Int? = null,
    val toPartyId: Int? = null,
    val note: String? = null,
    val tags: String? = null,
    val isEnabled: Boolean = true,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "sms_logs", indices = [androidx.room.Index(value = ["bodyHash"], unique = true)])
data class SmsLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val bodyHash: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "sms_drafts")
data class SmsTransactionDraft(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String,
    val time: String,
    val amount: Double,
    val amountMinorUnits: Long,
    val sender: String,
    val body: String,
    val merchantName: String? = null,
    val accountLastFour: String? = null,
    val type: String = "expense", // income, expense
    val accountId: Int? = null
)

@Entity(tableName = "fd_clearances")
data class FdClearance(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val redemptionHeaderId: Int,
    val creationHeaderId: Int,
    val amountCleared: Double
)

data class FdDashboardItem(
    val lineId: Int,
    val accountName: String,
    val minorHeadName: String,
    val fdLast4: String?,
    val maturityDate: String?,
    val initialAmount: Double,
    val totalCleared: Double = 0.0,
    val outstandingAmount: Double,
    val creationHeaderId: Int = 0
)

