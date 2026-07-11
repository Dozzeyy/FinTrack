/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // asset, liability
    val openingBalance: Double = 0.0,
    val description: String? = null,
    val isEnabled: Boolean = true,
    val minorHeadId: Int? = null,
    val creditLimit: Double? = null,
    val billingCycleStart: String? = null,
    val billingCycleEnd: String? = null,
    val paymentDueDate: String? = null,
    val icon: String? = null
)

@Entity(tableName = "major_heads")
data class MajorHead(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "minor_heads")
data class MinorHead(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val majorHeadId: Int,
    val isEnabled: Boolean = true
)

@Entity(tableName = "categories", indices = [androidx.room.Index(value = ["name", "type"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // income, expense
    val description: String? = null,
    val isEnabled: Boolean = true,
    val icon: String? = null
)

@Entity(tableName = "tags", indices = [androidx.room.Index(value = ["name"], unique = true)])
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "parties")
data class Party(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val openingBalance: Double = 0.0,
    val isEnabled: Boolean = true
)

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String? = null,
    val categoryIds: String, // Comma separated category IDs
    val amount: Double,
    val duration: String, // Daily, Weekly, Monthly, Half Yearly, Yearly
    val note: String? = null,
    val higherIsBetter: Boolean = false,
    val accountIds: String? = null // Comma separated Account (Micro Head) IDs
)

@Entity(tableName = "templates")
data class Template(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // income, expense, transfer
    val accountId: Int?,
    val toAccountId: Int?,
    val categoryId: Int?,
    val amount: Double?,
    val note: String?,
    val tags: String?,
    val multiEntries: String? = null, // catId:amount[:note]|...
    val subName: String? = null,
    val subFrequency: Int? = null
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(entity = Account::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: String, // YYYY-MM-DD
    val time: String, // HH:mm
    val accountId: Int,
    val toAccountId: Int? = null, // For transfers
    val categoryId: Int?, // NULL for transfers
    val amount: Double,
    val note: String?,
    val tags: String? = null, // Comma separated tag IDs
    val transactionNumber: String? = null,
    val partyId: Int? = null, // From Party (for On Account)
    val toPartyId: Int? = null, // To Party (for On Account Transfers)
    val subName: String? = null,
    val subFrequency: Int? = null, // in months
    val amountOriginal: Double? = null,
    val currencyCode: String? = null,
    val amountBase: Double? = null,
    val editedAt: Long? = null
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
    val isStopped: Boolean = false
)

@Entity(tableName = "notebooks")
data class Notebook(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
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
    val editedAt: Long? = null
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val loanType: String, // LENDING, BORROWING
    val principalAmount: Double,
    val interestRateAnnual: Double,
    val frequency: String, // MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY
    val installmentAmount: Double,
    val disbursementDate: Long,
    val firstRepaymentDate: Long,
    val totalInterestPaid: Double = 0.0,
    val totalPrincipalRepaid: Double = 0.0,
    val outstandingBalance: Double,
    val nextDueDate: Long,
    val periodsTotal: Int,
    val periodsPassed: Int = 0,
    val accountId: Int, // Map to Account (Micro Head)
    val partyId: Int, // Map to Party (Counterparty)
    val gapMethod: String = "DAYS", // DAYS, MONTH_ODD
    val gapInterest: Double = 0.0,
    val isActualEmiDifferent: Boolean = false,
    val actualRepaymentAmount: Double = 0.0,
    val isAutoRecordEnabled: Boolean = false,
    val sourceAccountId: Int? = null,
    val tags: String? = null,
    val notes: String? = null,
    val isClosed: Boolean = false
)

@Entity(tableName = "loan_repayments")
data class LoanRepayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val amountPaid: Double,
    val principalPortion: Double,
    val interestPortion: Double,
    val paymentDate: Long,
    val transactionId: Int? = null, // Linked transaction in FinTrack
    val isScheduled: Boolean = true
)

@Entity(tableName = "subscriptions_master")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val frequency: Int, // in months
    val note: String? = null,
    val isTransfer: Boolean = false,
    val isEnabled: Boolean = true
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
    val isEnabled: Boolean = true
)
