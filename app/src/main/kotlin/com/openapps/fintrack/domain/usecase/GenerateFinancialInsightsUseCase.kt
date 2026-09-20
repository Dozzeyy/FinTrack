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

import com.openapps.fintrack.data.FinancialInsight
import com.openapps.fintrack.data.FinancialInsightEngine
import com.openapps.fintrack.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class GenerateFinancialInsightsUseCase @Inject constructor(
    private val repository: FinanceRepository,
    private val getBudgetPerformanceUseCase: GetBudgetPerformanceUseCase
) {
    private val insightEngine = FinancialInsightEngine()

    suspend operator fun invoke(
        merchantTrackerEnabled: Boolean,
        incomeAtMonthEnd: Boolean
    ): List<FinancialInsight> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        val txns = repository.getAllTransactionsWithDetails().first()
        val balances = repository.getAccountBalances(today).first()
        val loans = repository.getAllActiveLoans().first()
        val budgets = getBudgetPerformanceUseCase(today).first()
        val majorHeads = repository.getAllMajorHeads().first()
        val minorHeads = repository.getAllMinorHeads().first()

        return insightEngine.generateInsights(
            txns,
            balances,
            budgets,
            loans,
            majorHeads,
            minorHeads,
            merchantTrackerEnabled,
            incomeAtMonthEnd
        )
    }
}
