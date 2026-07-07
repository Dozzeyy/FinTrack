/*
 * FinTrack
 * Copyright (C) 2026 Dozzeyy
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.openapps.fintrack.data

import kotlinx.serialization.Serializable

@Serializable
data class FinTrackConfigExport(
    val majorHeads: List<MajorHeadConfig>,
    val minorHeads: List<MinorHeadConfig>,
    val accounts: List<AccountConfig>,
    val categories: List<CategoryConfig>,
    val templates: List<TemplateConfig>,
    val budgets: List<BudgetConfig>,
    val tags: List<TagConfig>
)

@Serializable
data class MajorHeadConfig(val name: String, val isEnabled: Boolean)

@Serializable
data class MinorHeadConfig(val name: String, val majorHeadName: String, val isEnabled: Boolean)

@Serializable
data class AccountConfig(
    val name: String,
    val type: String,
    val openingBalance: Double,
    val description: String?,
    val isEnabled: Boolean,
    val minorHeadName: String?,
    val majorHeadName: String?,
    val creditLimit: Double?,
    val billingCycleStart: String?,
    val billingCycleEnd: String?,
    val paymentDueDate: String?
)

@Serializable
data class CategoryConfig(val name: String, val type: String, val description: String?, val isEnabled: Boolean)

@Serializable
data class TagConfig(val name: String, val isEnabled: Boolean)

@Serializable
data class TemplateConfig(
    val name: String,
    val type: String,
    val accountName: String?,
    val toAccountName: String?,
    val categoryName: String?,
    val categoryType: String?,
    val amount: Double?,
    val note: String?,
    val tags: String?, // This still contains IDs, but we can't easily map them without names
    val multiEntries: String?,
    val subName: String?,
    val subFrequency: Int?
)

@Serializable
data class BudgetConfig(
    val name: String?,
    val categoryNames: List<String>,
    val accountNames: List<String>,
    val amount: Double,
    val duration: String,
    val note: String?,
    val higherIsBetter: Boolean
)
