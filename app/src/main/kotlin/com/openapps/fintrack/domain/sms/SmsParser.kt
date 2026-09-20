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

package com.openapps.fintrack.domain.sms

import java.time.LocalDate
import java.time.LocalTime

data class ParsedSmsTransaction(
    val amount: Double,
    val date: LocalDate,
    val time: LocalTime,
    val merchantName: String? = null,
    val accountLastFour: String? = null,
    val type: String = "expense"
)

interface SmsParser {
    fun parse(body: String, sender: String, currencies: List<String>): ParsedSmsTransaction?
}

class GenericRegexParser : SmsParser {
    override fun parse(body: String, sender: String, currencies: List<String>): ParsedSmsTransaction? {
        val amount = extractAmount(body, currencies) ?: return null
        val type = if (body.contains("credit", ignoreCase = true) || 
                      body.contains("received", ignoreCase = true) ||
                      body.contains("refund", ignoreCase = true)) "income" else "expense"
        
        val merchant = extractMerchant(body)
        val acc = extractAccount(body)

        return ParsedSmsTransaction(
            amount = amount,
            date = LocalDate.now(),
            time = LocalTime.now(),
            merchantName = merchant,
            accountLastFour = acc,
            type = type
        )
    }

    private fun extractAmount(body: String, currencies: List<String>): Double? {
        for (curr in currencies) {
            val pattern = """(?i)${Regex.escape(curr)}\.?\s*([\d,]+\.?\d*)""".toRegex()
            val match = pattern.find(body)
            if (match != null) {
                return match.groupValues[1].replace(",", "").toDoubleOrNull()
            }
        }
        val genericPattern = """(?i)(?:spent|amt|amount|rs\.?|inr|usd)\s*([\d,]+\.?\d*)""".toRegex()
        val genericMatch = genericPattern.find(body)
        return genericMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
    }

    private fun extractMerchant(body: String): String? {
        val patterns = listOf(
            """(?i)at\s+([A-Z0-9\s*&]+?)(?:\s+on|\s+at|\s+using|\.|$)""".toRegex(),
            """(?i)to\s+([A-Z0-9\s*&]+?)(?:\s+on|\s+at|\s+using|\.|$)""".toRegex(),
            """(?i)info\*\s*([A-Z0-9\s*&]+?)(?:\s+on|\s+at|\s+using|\.|$)""".toRegex()
        )
        for (p in patterns) {
            val m = p.find(body)
            if (m != null) {
                val name = m.groupValues[1].trim()
                if (name.length in 3..30) return name
            }
        }
        return null
    }

    private fun extractAccount(body: String): String? {
        val patterns = listOf(
            """(?i)(?:acct?|a/c|card|VPA)\s*(?:ending\s+in|ending|X+|#)?\s*(\d{3,})""".toRegex(),
            """(?i)ending\s+in\s*(\d{3,})""".toRegex()
        )
        for (p in patterns) {
            val m = p.find(body)
            if (m != null) return m.groupValues[1]
        }
        return null
    }
}
