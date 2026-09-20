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
import com.openapps.fintrack.domain.model.Money
import com.openapps.fintrack.domain.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class AddTransactionUseCase @Inject constructor(
    private val repository: FinanceRepository
) {
    suspend operator fun invoke(
        date: String,
        time: String,
        note: String?,
        type: String,
        lines: List<TransactionLineData>,
        tags: List<Int> = emptyList(),
        partyId: Int? = null,
        toPartyId: Int? = null,
        subName: String? = null,
        subFrequency: Int? = null,
        merchantName: String? = null,
        invoiceNumber: String? = null,
        dueDays: Int? = null,
        updateId: Int? = null,
        clearInvoiceIds: List<Int> = emptyList(),
        baseCurrency: String = "INR",
        goalId: Int? = null,
        selectedFdCreationHeaderId: Int? = null
    ): Int {
        val prefix = when (type) {
            "income" -> "INC"
            "expense" -> "EXP"
            "transfer" -> "TNF"
            "multi_income" -> "MINC"
            "multi_expense" -> "MEXP"
            else -> "TXN"
        }

        var finalTxnNumber: String? = null
        if (updateId != null && updateId != 0) {
            val existing = repository.getTransactionsDetailed().first().find { it.header.id == updateId }
            finalTxnNumber = existing?.header?.transactionNumber
            repository.deleteClearancesByTransfer(updateId)
            repository.deleteFdClearancesByRedemption(updateId)
        }

        if (finalTxnNumber == null) {
            val lastNum = repository.getLastTransactionHeaderNumber(prefix)
            val nextSerial = (lastNum?.split("/")?.last()?.toIntOrNull() ?: 99999) + 1
            val year = try {
                LocalDate.parse(date).year
            } catch (ex: Exception) {
                LocalDate.now(ZoneId.of("UTC")).year
            }
            finalTxnNumber = "$prefix/$year/$nextSerial"
        }

        val header = TransactionHeader(
            id = updateId ?: 0,
            transactionNumber = finalTxnNumber,
            date = date,
            time = time,
            note = note,
            partyId = partyId,
            toPartyId = toPartyId,
            subName = subName,
            subFrequency = subFrequency,
            merchantName = merchantName,
            invoiceNumber = invoiceNumber,
            dueDays = dueDays,
            editedAt = System.currentTimeMillis()
        )

        val headerId = repository.insertTransactionHeader(header).toInt()
        if (goalId != null && goalId != 0) {
            repository.insertGoalTransaction(GoalTransaction(goalId, headerId))
        }
        repository.deleteTransactionLines(headerId)
        repository.deleteTransactionTags(headerId)

        val transactionLines = lines.map { data ->
            val amountMinorUnits = Money.fromDecimal(data.amountBase ?: data.amount).minorUnits
            TransactionLine(
                headerId = headerId,
                accountId = data.accountId,
                toAccountId = data.toAccountId,
                categoryId = data.categoryId,
                amount = data.amount,
                amountMinorUnits = amountMinorUnits,
                amountOriginal = data.amountOriginal ?: data.amount,
                amountOriginalMinorUnits = Money.fromDecimal(data.amountOriginal ?: data.amount).minorUnits,
                currencyCode = data.currencyCode ?: baseCurrency,
                amountBase = data.amountBase ?: data.amount,
                amountBaseMinorUnits = amountMinorUnits,
                isNegotiated = data.isNegotiated,
                negotiationAmountOriginal = data.negotiationAmountOriginal,
                negotiationAmountOriginalMinorUnits = data.negotiationAmountOriginal?.let { Money.fromDecimal(it).minorUnits },
                isDiscretionary = data.isDiscretionary,
                note = data.note,
                tags = data.tags,
                fdLast4 = data.fdLast4,
                fdMaturityDate = data.fdMaturityDate
            )
        }
        repository.insertTransactionLines(transactionLines)

        if (tags.isNotEmpty()) {
            repository.insertTransactionTags(tags.map { TransactionTag(headerId, it) })
        }

        if (type == "transfer" && selectedFdCreationHeaderId != null && selectedFdCreationHeaderId != 0) {
            val redemptionAmt = lines.firstOrNull()?.amount ?: 0.0
            repository.saveFdClearance(headerId, selectedFdCreationHeaderId, redemptionAmt)
        }

        if (type == "transfer" && clearInvoiceIds.isNotEmpty()) {
            var remainingToClear = lines.firstOrNull()?.amount ?: 0.0

            val allTxns = repository.getTransactionsDetailed().first()
            val selectedInvoices = allTxns.filter { it.header.id in clearInvoiceIds }
                .sortedWith(compareBy({ it.header.date }, { it.header.time }))

            for (invDetail in selectedInvoices) {
                if (remainingToClear <= 0.001) break

                val invTxnHeader = invDetail.header
                val invTxnLine = invDetail.lines.firstOrNull() ?: continue

                val alreadyCleared = repository.getInvoicesForParty(invTxnHeader.partyId ?: 0, "9999-12-31").first()
                    .find { it.detail.transaction.transactionNumber == invTxnHeader.transactionNumber }?.totalCleared ?: 0L

                val outstanding = (invTxnLine.line.amountMinorUnits ?: (invTxnLine.line.amount * 100).toLong()) - alreadyCleared
                if (outstanding <= 0) continue

                val remainingToClearMinor = (remainingToClear * 100).toLong()
                val toClearNowMinor = minOf(remainingToClearMinor, outstanding)

                repository.insertInvoiceClearance(
                    InvoiceClearance(
                        transferTransactionId = headerId,
                        invoiceTransactionId = invTxnHeader.id,
                        amountCleared = toClearNowMinor.toDouble() / 100.0,
                        amountClearedMinorUnits = toClearNowMinor
                    )
                )
                remainingToClear -= (toClearNowMinor.toDouble() / 100.0)
            }
        }

        return headerId
    }
}

data class TransactionLineData(
    val accountId: Int,
    val toAccountId: Int? = null,
    val categoryId: Int? = null,
    val amount: Double,
    val amountOriginal: Double? = null,
    val currencyCode: String? = null,
    val amountBase: Double? = null,
    val isNegotiated: Boolean = false,
    val negotiationAmountOriginal: Double? = null,
    val isDiscretionary: Boolean = false,
    val note: String? = null,
    val tags: String? = null,
    val fdLast4: String? = null,
    val fdMaturityDate: String? = null
)
