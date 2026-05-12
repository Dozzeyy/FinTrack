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
    val isEnabled: Boolean = true
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // income, expense
    val description: String? = null,
    val isEnabled: Boolean = true
)

@Entity(tableName = "tags")
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
    val note: String? = null
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
    val multiEntries: String? = null // catId:amount[:note]|...
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
    val toPartyId: Int? = null // To Party (for On Account Transfers)
)
