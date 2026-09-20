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

import java.time.LocalDate

data class CcCycleInfo(
    val accountId: Int,
    val accountName: String,
    val cycleStartDate: LocalDate,
    val cycleEndDate: LocalDate,
    val dueDate: LocalDate,
    val daysRemaining: Long,
    val unbilledAmount: Long, 
    val statementBalance: Long, 
    val interestFreeDaysLeft: Long
)

data class SmartCardSuggestion(
    val bestAccountId: Int,
    val reason: String
)
