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

package com.openapps.fintrack.domain.usecase

import com.openapps.fintrack.domain.model.BudgetVsActual
import com.openapps.fintrack.domain.repository.FinanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class GetBudgetPerformanceUseCase @Inject constructor(
    private val repository: FinanceRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(asOfDate: String): Flow<List<BudgetVsActual>> {
        val date = try {
            LocalDate.parse(asOfDate)
        } catch (e: Exception) {
            LocalDate.now(ZoneId.of("UTC"))
        }

        return repository.getBudgetsWithRelations().flatMapLatest { budgets ->
            if (budgets.isEmpty()) return@flatMapLatest flowOf(emptyList<BudgetVsActual>())

            val budgetFlows = budgets.map { detailed ->
                val budget = detailed.budget
                val catIds = detailed.categories.map { it.id }
                val targetAccountIds = detailed.accounts.map { it.id }
                val currentRange = getRangeForDuration(date, budget.duration)
                val prevRange = if (budget.rolloverEnabled && budget.duration == "Monthly") {
                    getRangeForDuration(date.minusMonths(1), budget.duration)
                } else null

                val currentTransactionsFlow = repository.getTransactionsByDateRange(currentRange.first, currentRange.second)
                val prevTransactionsFlow = if (prevRange != null) {
                    repository.getTransactionsByDateRange(prevRange.first, prevRange.second)
                } else flowOf(emptyList())

                combine(currentTransactionsFlow, prevTransactionsFlow) { currentTxns, prevTxns ->
                    val relevantCurrent = currentTxns.filter {
                        (it.transaction.categoryId in catIds) || (it.transaction.accountId in targetAccountIds && it.transaction.categoryId != null)
                    }
                    val actualCurrent = relevantCurrent.sumOf {
                        if (it.categoryType == "expense" || (it.transaction.accountId in targetAccountIds && it.transaction.categoryId != null))
                            it.transaction.amount
                        else -it.transaction.amount
                    }

                    var rolloverAmount = 0.0
                    if (prevRange != null) {
                        val relevantPrev = prevTxns.filter {
                            (it.transaction.categoryId in catIds) || (it.transaction.accountId in targetAccountIds && it.transaction.categoryId != null)
                        }
                        val actualPrev = relevantPrev.sumOf {
                            if (it.categoryType == "expense" || (it.transaction.accountId in targetAccountIds && it.transaction.categoryId != null))
                                it.transaction.amount
                            else -it.transaction.amount
                        }
                        rolloverAmount = (budget.amount - actualPrev).coerceAtLeast(0.0)
                    }

                    val daysInPeriod = ChronoUnit.DAYS.between(LocalDate.parse(currentRange.first), LocalDate.parse(currentRange.second)) + 1
                    val daysElapsed = ChronoUnit.DAYS.between(LocalDate.parse(currentRange.first), date).coerceAtLeast(1)
                    
                    val projectedAmount = if (daysElapsed > 2 && daysElapsed < daysInPeriod) {
                        (actualCurrent / daysElapsed) * daysInPeriod
                    } else null

                    val catNames = detailed.categories.map { it.name }.toMutableList()
                    catNames.addAll(detailed.accounts.map { it.name })

                    BudgetVsActual(
                        budgetId = budget.id,
                        categoryName = budget.name ?: catNames.joinToString(", "),
                        categoryType = if (relevantCurrent.any { it.categoryType == "expense" || it.transaction.accountId in targetAccountIds }) "expense" else "income",
                        budgetAmount = budget.amount,
                        actualAmount = actualCurrent,
                        duration = budget.duration,
                        higherIsBetter = budget.higherIsBetter,
                        rolloverEnabled = budget.rolloverEnabled,
                        rolloverAmount = rolloverAmount,
                        projectedAmount = projectedAmount,
                        categoryIds = catIds,
                        accountIds = targetAccountIds,
                        startDate = currentRange.first,
                        endDate = currentRange.second
                    )
                }
            }
            combine(budgetFlows) { it.toList() }
        }
    }

    private fun getRangeForDuration(date: LocalDate, duration: String): Pair<String, String> {
        val formatter = DateTimeFormatter.ISO_DATE
        if (duration.startsWith("CUSTOM:")) {
            val parts = duration.split(":")
            val value = parts.getOrNull(1)?.toLongOrNull() ?: 1L
            val unit = parts.getOrNull(2) ?: "month/s"

            return when (unit) {
                "day/s" -> Pair(date.minusDays(value - 1).format(formatter), date.format(formatter))
                "week/s" -> {
                    val end = date.with(java.time.DayOfWeek.SUNDAY)
                    val start = end.minusWeeks(value - 1).with(java.time.DayOfWeek.MONDAY)
                    Pair(start.format(formatter), end.format(formatter))
                }
                "month/s" -> {
                    val end = date.with(TemporalAdjusters.lastDayOfMonth())
                    val start = date.minusMonths(value - 1).with(TemporalAdjusters.firstDayOfMonth())
                    Pair(start.format(formatter), end.format(formatter))
                }
                "year/s" -> {
                    val end = date.with(TemporalAdjusters.lastDayOfYear())
                    val start = date.minusYears(value - 1).with(TemporalAdjusters.firstDayOfYear())
                    Pair(start.format(formatter), end.format(formatter))
                }
                else -> Pair(date.format(formatter), date.format(formatter))
            }
        }
        return when (duration.uppercase()) {
            "WEEKLY" -> Pair(date.with(java.time.DayOfWeek.MONDAY).format(formatter), date.with(java.time.DayOfWeek.SUNDAY).format(formatter))
            "MONTHLY" -> Pair(date.with(TemporalAdjusters.firstDayOfMonth()).format(formatter), date.with(TemporalAdjusters.lastDayOfMonth()).format(formatter))
            "YEARLY" -> Pair(date.with(TemporalAdjusters.firstDayOfYear()).format(formatter), date.with(TemporalAdjusters.lastDayOfYear()).format(formatter))
            "HALF YEARLY" -> {
                val start = if (date.monthValue <= 6) date.withMonth(1).withDayOfMonth(1) else date.withMonth(7).withDayOfMonth(1)
                val end = start.plusMonths(5).with(TemporalAdjusters.lastDayOfMonth())
                Pair(start.format(formatter), end.format(formatter))
            }
            "DAILY" -> Pair(date.format(formatter), date.format(formatter))
            else -> {
                when (duration) {
                    "Weekly" -> Pair(date.with(java.time.DayOfWeek.MONDAY).format(formatter), date.with(java.time.DayOfWeek.SUNDAY).format(formatter))
                    "Monthly" -> Pair(date.with(TemporalAdjusters.firstDayOfMonth()).format(formatter), date.with(TemporalAdjusters.lastDayOfMonth()).format(formatter))
                    "Yearly" -> Pair(date.with(TemporalAdjusters.firstDayOfYear()).format(formatter), date.with(TemporalAdjusters.lastDayOfYear()).format(formatter))
                    "Half Yearly" -> {
                        val start = if (date.monthValue <= 6) date.withMonth(1).withDayOfMonth(1) else date.withMonth(7).withDayOfMonth(1)
                        val end = start.plusMonths(5).with(TemporalAdjusters.lastDayOfMonth())
                        Pair(start.format(formatter), end.format(formatter))
                    }
                    else -> Pair(date.format(formatter), date.format(formatter))
                }
            }
        }
    }
}
