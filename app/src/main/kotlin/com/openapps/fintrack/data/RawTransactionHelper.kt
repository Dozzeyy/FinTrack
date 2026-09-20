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

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RawTransactionData(
    val type: String = "expense",
    val date: String = "",
    val time: String = "",
    val accountName: String? = null,
    val toAccountName: String? = null,
    val categoryName: String? = null,
    val tagNames: List<String> = emptyList(),
    val originalAmount: Double? = null,
    val currencyCode: String? = null,
    val baseAmount: Double? = null,
    val note: String? = null,
    val merchantName: String? = null,
    val isDiscretionary: Boolean = false,
    val isNegotiated: Boolean = false,
    val negotiationAmountOriginal: Double? = null,
    val partyName: String? = null,
    val toPartyName: String? = null,
    val invoiceNumber: String? = null,
    val dueDays: Int? = null,
    val subName: String? = null,
    val subFrequency: Int? = null,
    val fdLast4: String? = null,
    val fdMaturityDate: String? = null,
    val fdClearanceDetails: String? = null,
    val multiLines: List<RawTransactionLineData> = emptyList()
)

@Serializable
data class RawTransactionLineData(
    val accountName: String? = null,
    val toAccountName: String? = null,
    val categoryName: String? = null,
    val amount: Double = 0.0,
    val amountOriginal: Double? = null,
    val currencyCode: String? = null,
    val amountBase: Double? = null,
    val note: String? = null,
    val tagNames: List<String> = emptyList(),
    val isNegotiated: Boolean = false,
    val negotiationAmountOriginal: Double? = null,
    val isDiscretionary: Boolean = false,
    val fdLast4: String? = null,
    val fdMaturityDate: String? = null
)

object RawTransactionHelper {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun toJson(data: RawTransactionData): String = json.encodeToString(data)

    fun parse(jsonString: String): Result<RawTransactionData> {
        return try {
            val data = json.decodeFromString<RawTransactionData>(jsonString)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fromTransactionWithLinesAndDetails(
        details: TransactionWithLinesAndDetails,
        allTags: List<Tag> = emptyList()
    ): RawTransactionData {
        val header = details.header
        val lines = details.lines
        val headerTagNames = details.tags.map { it.name }

        val firstLine = lines.firstOrNull()
        val numLines = lines.size

        val type = when {
            numLines > 1 && (header.transactionNumber.startsWith("MEXP") || firstLine?.category?.type == "expense") -> "multi_expense"
            numLines > 1 && (header.transactionNumber.startsWith("MINC") || firstLine?.category?.type == "income") -> "multi_income"
            numLines > 1 -> "multi_expense"
            firstLine?.toAccount != null -> "transfer"
            firstLine?.category?.type == "income" -> "income"
            else -> "expense"
        }

        val multiLines = if (numLines > 1) {
            lines.map { lineDetailed ->
                val lineTagNames = lineDetailed.line.tags?.split(",")?.mapNotNull { tagIdStr ->
                    val tagId = tagIdStr.trim().toIntOrNull()
                    if (tagId != null) allTags.find { it.id == tagId }?.name ?: tagIdStr.trim()
                    else tagIdStr.trim().takeIf { it.isNotEmpty() }
                } ?: emptyList()

                RawTransactionLineData(
                    accountName = lineDetailed.account.name,
                    toAccountName = lineDetailed.toAccount?.name,
                    categoryName = lineDetailed.category?.name,
                    amount = lineDetailed.line.amount,
                    amountOriginal = lineDetailed.line.amountOriginal ?: lineDetailed.line.amount,
                    currencyCode = lineDetailed.line.currencyCode,
                    amountBase = lineDetailed.line.amountBase ?: lineDetailed.line.amount,
                    note = lineDetailed.line.note,
                    tagNames = lineTagNames,
                    isNegotiated = lineDetailed.line.isNegotiated,
                    negotiationAmountOriginal = lineDetailed.line.negotiationAmountOriginal,
                    isDiscretionary = lineDetailed.line.isDiscretionary,
                    fdLast4 = lineDetailed.line.fdLast4,
                    fdMaturityDate = lineDetailed.line.fdMaturityDate
                )
            }
        } else emptyList()

        return RawTransactionData(
            type = type,
            date = header.date,
            time = header.time,
            accountName = firstLine?.account?.name,
            toAccountName = firstLine?.toAccount?.name,
            categoryName = firstLine?.category?.name,
            tagNames = headerTagNames,
            originalAmount = firstLine?.line?.amountOriginal ?: firstLine?.line?.amount,
            currencyCode = firstLine?.line?.currencyCode,
            baseAmount = firstLine?.line?.amountBase ?: firstLine?.line?.amount,
            note = header.note ?: firstLine?.line?.note,
            merchantName = header.merchantName,
            isDiscretionary = firstLine?.line?.isDiscretionary ?: false,
            isNegotiated = firstLine?.line?.isNegotiated ?: false,
            negotiationAmountOriginal = firstLine?.line?.negotiationAmountOriginal,
            partyName = details.party?.name,
            toPartyName = details.toParty?.name,
            invoiceNumber = header.invoiceNumber,
            dueDays = header.dueDays,
            subName = header.subName,
            subFrequency = header.subFrequency,
            fdLast4 = firstLine?.line?.fdLast4,
            fdMaturityDate = firstLine?.line?.fdMaturityDate,
            fdClearanceDetails = null,
            multiLines = multiLines
        )
    }

    fun fromTransactionWithDetails(
        detail: TransactionWithDetails,
        allTags: List<Tag> = emptyList()
    ): RawTransactionData {
        val txn = detail.transaction
        val type = when {
            txn.toAccountId != null -> "transfer"
            detail.categoryType == "income" -> "income"
            else -> "expense"
        }

        val tagNames = txn.tags?.split(",")?.mapNotNull { tagIdStr ->
            val tagId = tagIdStr.trim().toIntOrNull()
            if (tagId != null) allTags.find { it.id == tagId }?.name ?: tagIdStr.trim()
            else tagIdStr.trim().takeIf { it.isNotEmpty() }
        } ?: emptyList()

        return RawTransactionData(
            type = type,
            date = txn.date,
            time = txn.time,
            accountName = detail.accountName,
            toAccountName = detail.toAccountName,
            categoryName = detail.categoryName,
            tagNames = tagNames,
            originalAmount = txn.amountOriginal ?: txn.amount,
            currencyCode = txn.currencyCode,
            baseAmount = txn.amountBase ?: txn.amount,
            note = txn.note,
            merchantName = txn.merchantName,
            isDiscretionary = txn.isDiscretionary,
            isNegotiated = txn.isNegotiated,
            negotiationAmountOriginal = txn.negotiationAmountOriginal,
            partyName = detail.partyName,
            toPartyName = detail.toPartyName,
            invoiceNumber = txn.invoiceNumber,
            dueDays = txn.dueDays,
            subName = txn.subName,
            subFrequency = txn.subFrequency,
            fdLast4 = null,
            fdMaturityDate = null,
            fdClearanceDetails = null,
            multiLines = emptyList()
        )
    }
}
