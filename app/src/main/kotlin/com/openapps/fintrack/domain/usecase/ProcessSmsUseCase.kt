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

import android.util.Log
import com.openapps.fintrack.data.*
import com.openapps.fintrack.domain.repository.FinanceRepository
import com.openapps.fintrack.domain.sms.GenericRegexParser
import com.openapps.fintrack.domain.sms.SmsParser
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ProcessSmsUseCase @Inject constructor(
    private val repository: FinanceRepository,
    private val addTransactionUseCase: AddTransactionUseCase
) {
    private val genericParser: SmsParser = GenericRegexParser()

    suspend operator fun invoke(body: String, sender: String, currencies: List<String>, baseCurrency: String = "INR") {
        val hash = sha256(body)
        if (repository.getSmsLogByHash(hash) != null) {
            Log.d("ProcessSmsUseCase", "Skipping duplicate SMS: $hash")
            return
        }

        val accounts = repository.getEnabledAccounts().first()
        fun findMatchingAccountId(accountLastFour: String?): Int? {
            if (accountLastFour.isNullOrBlank()) return null
            val match = accounts.find { acc ->
                val last4 = acc.last4Digits
                !last4.isNullOrBlank() && (last4.equals(accountLastFour, true) || last4.takeLast(4) == accountLastFour.takeLast(4))
            }
            return match?.id
        }

        val rules = repository.getEnabledRulesInternal()
        var ruleMatched = false

        for (rule in rules) {
            val senderMatches = rule.msgFrom == null || sender.contains(rule.msgFrom, ignoreCase = true)
            val textMatches = body.contains(rule.textContaining, ignoreCase = true)

            if (senderMatches && textMatches) {
                val parsed = genericParser.parse(body, sender, currencies)
                if (parsed != null && parsed.amount > 0) {
                    val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
                    val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    
                    val targetAccId = if (rule.accountId != null && rule.accountId != 0) rule.accountId else (findMatchingAccountId(parsed.accountLastFour) ?: accounts.firstOrNull()?.id ?: 0)

                    val lines = listOf(
                        TransactionLineData(
                            accountId = targetAccId,
                            toAccountId = rule.toAccountId,
                            categoryId = rule.categoryId,
                            amount = parsed.amount,
                            currencyCode = currencies.firstOrNull() ?: baseCurrency
                        )
                    )

                    addTransactionUseCase(
                        date = today,
                        time = now,
                        note = rule.note ?: "Auto-recorded via rule: ${rule.name}",
                        type = rule.type,
                        lines = lines,
                        tags = rule.tags?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
                        partyId = rule.partyId,
                        toPartyId = rule.toPartyId,
                        merchantName = parsed.merchantName
                    )
                    
                    Log.d("ProcessSmsUseCase", "Rule matched and transaction recorded: ${rule.name}")
                    ruleMatched = true
                    break
                }
            }
        }

        if (!ruleMatched) {
            val parsed = genericParser.parse(body, sender, currencies)
            if (parsed != null && parsed.amount > 0) {
                val matchedAccId = findMatchingAccountId(parsed.accountLastFour)
                repository.insertSmsDraft(SmsTransactionDraft(
                    date = parsed.date.toString(),
                    time = parsed.time.toString(),
                    amount = parsed.amount,
                    amountMinorUnits = (parsed.amount * 100).toLong(),
                    sender = sender,
                    body = body,
                    merchantName = parsed.merchantName,
                    accountLastFour = parsed.accountLastFour,
                    type = parsed.type,
                    accountId = matchedAccId
                ))
                Log.d("ProcessSmsUseCase", "Transaction draft saved to Review Inbox")
            }
        }

        repository.insertSmsLog(SmsLog(sender = sender, bodyHash = hash))
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
