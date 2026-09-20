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

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetAmount: Double,
    val targetAmountMinorUnits: Long = (targetAmount * 100).toLong(),
    val targetDate: Long?,
    val icon: String = "flag",
    val color: Int = 0xFF4CAF50.toInt(),
    val isCompleted: Boolean = false,
    val editedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

@Entity(tableName = "goal_account_allocations", primaryKeys = ["goalId", "accountId"])
data class GoalAccountAllocation(
    val goalId: Int,
    val accountId: Int,
    val allocatedAmount: Double,
    val allocatedAmountMinorUnits: Long = (allocatedAmount * 100).toLong()
)

@Entity(tableName = "goal_rules")
data class GoalRule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val goalId: Int,
    val accountId: Int,
    val amount: Double,
    val amountMinorUnits: Long = (amount * 100).toLong(),
    val frequency: String, // "Daily", "Weekly", "Monthly", "Quarterly", "Half-Yearly", "Yearly"
    val triggerDay: Int,
    val isEnabled: Boolean = true,
    val lastExecutedAt: Long = 0L
)

@Entity(tableName = "goal_transactions", primaryKeys = ["goalId", "transactionId"])
data class GoalTransaction(
    val goalId: Int,
    val transactionId: Int
)

@Entity(tableName = "goal_allocation_history")
data class GoalAllocationHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val goalId: Int,
    val accountId: Int,
    val amount: Double,
    val amountMinorUnits: Long = (amount * 100).toLong(),
    val source: String, // "Manual" or "Automated Rule"
    val timestamp: Long = System.currentTimeMillis()
)
