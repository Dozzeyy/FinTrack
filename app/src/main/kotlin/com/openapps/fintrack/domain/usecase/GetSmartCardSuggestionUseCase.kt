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

import com.openapps.fintrack.domain.model.SmartCardSuggestion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetSmartCardSuggestionUseCase @Inject constructor(
    private val getCcCycleInfoUseCase: GetCcCycleInfoUseCase
) {
    operator fun invoke(): Flow<SmartCardSuggestion?> {
        return getCcCycleInfoUseCase().map { cycles ->
            if (cycles.isEmpty()) return@map null
            
            val best = cycles.maxByOrNull { it.interestFreeDaysLeft }
            if (best != null) {
                SmartCardSuggestion(
                    bestAccountId = best.accountId,
                    reason = "Use '${best.accountName}' for ${best.interestFreeDaysLeft} interest-free days."
                )
            } else null
        }
    }
}
