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

package com.openapps.fintrack.domain.model

data class BudgetVsActual(
    val budgetId: Int, 
    val categoryName: String, 
    val categoryType: String, 
    val budgetAmount: Double, 
    val actualAmount: Double, 
    val duration: String, 
    val higherIsBetter: Boolean = false, 
    val rolloverEnabled: Boolean = false,
    val rolloverAmount: Double = 0.0,
    val projectedAmount: Double? = null,
    val categoryIds: List<Int> = emptyList(), 
    val accountIds: List<Int> = emptyList(), 
    val startDate: String = "", 
    val endDate: String = ""
)
