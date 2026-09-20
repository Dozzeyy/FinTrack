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
data class TagConfig(val name: String, val isEnabled: Boolean, val trackingType: String = "Both", val targetNumber: Double? = null)

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
    val tags: String?,
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
