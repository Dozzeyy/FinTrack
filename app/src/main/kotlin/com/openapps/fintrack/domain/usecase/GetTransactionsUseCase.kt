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
import com.openapps.fintrack.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetTransactionsUseCase @Inject constructor(
    private val repository: FinanceRepository
) {
    operator fun invoke(): Flow<List<TransactionWithDetails>> {
        return repository.getTransactionsDetailed().map { list ->
            list.flatMap { t ->
                t.lines.map { l ->
                    TransactionWithDetails(
                        transaction = TransactionLegacy(
                            id = t.header.id,
                            date = t.header.date,
                            time = t.header.time,
                            accountId = l.line.accountId,
                            toAccountId = l.line.toAccountId,
                            categoryId = l.line.categoryId,
                            amount = l.line.amount,
                            amountMinorUnits = l.line.amountMinorUnits,
                            note = t.header.note,
                            tags = t.tags.joinToString(",") { it.id.toString() },
                            transactionNumber = t.header.transactionNumber,
                            partyId = t.header.partyId,
                            toPartyId = t.header.toPartyId,
                            subName = t.header.subName,
                            subFrequency = t.header.subFrequency,
                            amountOriginal = l.line.amountOriginal,
                            amountOriginalMinorUnits = l.line.amountOriginalMinorUnits,
                            currencyCode = l.line.currencyCode,
                            amountBase = l.line.amountBase,
                            amountBaseMinorUnits = l.line.amountBaseMinorUnits,
                            editedAt = t.header.editedAt,
                            isNegotiated = l.line.isNegotiated,
                            negotiationAmountOriginal = l.line.negotiationAmountOriginal,
                            negotiationAmountOriginalMinorUnits = l.line.negotiationAmountOriginalMinorUnits,
                            merchantName = t.header.merchantName,
                            isDiscretionary = l.line.isDiscretionary,
                            invoiceNumber = t.header.invoiceNumber,
                            dueDays = t.header.dueDays,
                            isReconciled = l.line.isReconciled,
                            reconciliationStatus = l.line.reconciliationStatus
                        ),
                        categoryName = l.category?.name,
                        categoryType = l.category?.type,
                        categoryIcon = l.category?.icon,
                        accountName = l.account.name,
                        accountIcon = l.account.icon,
                        toAccountName = l.toAccount?.name,
                        toAccountIcon = l.toAccount?.icon,
                        partyName = t.party?.name,
                        toPartyName = t.toParty?.name
                    )
                }
            }.sortedWith(compareByDescending<TransactionWithDetails> { it.transaction.date }.thenByDescending { it.transaction.time })
        }
    }
}
