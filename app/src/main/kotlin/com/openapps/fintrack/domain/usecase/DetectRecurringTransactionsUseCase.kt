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

import com.openapps.fintrack.domain.model.SubscriptionSuggestion
import com.openapps.fintrack.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class DetectRecurringTransactionsUseCase @Inject constructor(
    private val repository: FinanceRepository
) {
    operator fun invoke(): Flow<List<SubscriptionSuggestion>> = flow {
        val txns = repository.getAllTransactionsWithDetails().first()
        val existingSubs = repository.getAllSubscriptionsMaster().first().map { it.name.lowercase() }.toSet()
        
        val merchantTxns = txns.filter { it.categoryType == "expense" && !it.transaction.merchantName.isNullOrBlank() }
            .groupBy { it.transaction.merchantName!!.lowercase() }
        
        val suggestions = mutableListOf<SubscriptionSuggestion>()
        
        merchantTxns.forEach { (merchant, list) ->
            if (merchant in existingSubs) return@forEach
            if (list.size < 3) return@forEach
            
            val sorted = list.sortedByDescending { it.transaction.date }
            val intervals = mutableListOf<Long>()
            for (i in 0 until sorted.size - 1) {
                val d1 = LocalDate.parse(sorted[i].transaction.date)
                val d2 = LocalDate.parse(sorted[i+1].transaction.date)
                intervals.add(ChronoUnit.DAYS.between(d2, d1))
            }
            
    
            val isMonthly = intervals.all { it in 25..35 }
            if (isMonthly) {

                val amounts = sorted.map { it.transaction.amount }
                val avgAmount = amounts.average()
                val isConsistentAmount = amounts.all { Math.abs(it - avgAmount) / avgAmount < 0.05 }
                
                if (isConsistentAmount) {
                    suggestions.add(SubscriptionSuggestion(
                        merchantName = list.first().transaction.merchantName!!,
                        amount = avgAmount,
                        frequencyMonths = 1,
                        lastDate = sorted.first().transaction.date,
                        confidence = 0.9f
                    ))
                }
            }
        }
        
        emit(suggestions)
    }
}
