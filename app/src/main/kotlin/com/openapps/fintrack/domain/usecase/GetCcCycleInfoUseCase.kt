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

import com.openapps.fintrack.data.*
import com.openapps.fintrack.domain.model.CcCycleInfo
import com.openapps.fintrack.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class GetCcCycleInfoUseCase @Inject constructor(
    private val repository: FinanceRepository
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<CcCycleInfo>> {
        return combine(
            repository.getEnabledAccounts(),
            repository.getAllMinorHeads(),
            repository.getAllMajorHeads()
        ) { accounts, minors, majors ->
            val ccMajorId = majors.find { it.name.contains("Credit Card", true) }?.id ?: 8
            val ccMinors = minors.filter { it.majorHeadId == ccMajorId }.map { it.id }.toSet()
            
            accounts.filter { it.minorHeadId in ccMinors }.map { acc ->
                calculateCycleInfo(acc)
            }
        }.flatMapLatest { flows ->
            if (flows.isEmpty()) flowOf(emptyList())
            else combine(flows) { it.toList() }
        }
    }

    private fun calculateCycleInfo(account: Account): Flow<CcCycleInfo> {
        val today = LocalDate.now()
        val endDay = account.billingCycleEnd?.toIntOrNull() ?: 30
        val dueDays = account.paymentDueDate?.toIntOrNull() ?: 20

        var cycleEnd = try {
            val lastDay = today.lengthOfMonth()
            LocalDate.of(today.year, today.monthValue, endDay.coerceAtMost(lastDay))
        } catch (e: Exception) { today }

        if (cycleEnd.isBefore(today)) {
            cycleEnd = cycleEnd.plusMonths(1)
        }
        
        val cycleStart = cycleEnd.minusMonths(1).plusDays(1)
        val dueDate = cycleEnd.plusDays(dueDays.toLong())

        val startStr = cycleStart.format(java.time.format.DateTimeFormatter.ISO_DATE)
        val endStr = cycleEnd.format(java.time.format.DateTimeFormatter.ISO_DATE)

        return repository.getAccountTransactionsByDateRange(account.id, startStr, endStr).map { txns ->
            val unbilled = txns.sumOf { 
                val amt = it.transaction.amountMinorUnits ?: (it.transaction.amount * 100).toLong()
                if (it.transaction.toAccountId == account.id) amt else -amt
            }

            CcCycleInfo(
                accountId = account.id,
                accountName = account.name,
                cycleStartDate = cycleStart,
                cycleEndDate = cycleEnd,
                dueDate = dueDate,
                daysRemaining = ChronoUnit.DAYS.between(today, cycleEnd),
                unbilledAmount = unbilled,
                statementBalance = 0L,
                interestFreeDaysLeft = ChronoUnit.DAYS.between(today, dueDate)
            )
        }
    }
}
