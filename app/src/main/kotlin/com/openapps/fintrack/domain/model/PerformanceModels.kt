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

data class PerformanceDashboardData(
    val metrics: List<PerformanceMetricData>,
    val budgetSummaries: List<BudgetPerformanceData>,
    val budgetTrends: List<BudgetTrendData>,
    val categoryTrends: List<CategoryPerformanceData>
)

enum class MetricAggregation { SUM, AVERAGE, LAST }

data class PerformanceMetricData(
    val name: String,
    val periodValues: Map<String, Double>, // date (yyyy-MM-dd) -> value
    val unit: String = "",
    val aggregation: MetricAggregation = MetricAggregation.SUM
)

data class BudgetPerformanceData(
    val name: String,
    val duration: String,
    val budget: Double,
    val actual: Double,
    val variance: Double,
    val variancePercent: Double,
    val budgetId: Int = 0
)

data class BudgetTrendData(
    val budgetId: Int,
    val budgetName: String,
    val monthData: Map<String, BudgetPerformanceData>
)

data class CategoryPerformanceData(
    val categoryId: Int,
    val categoryName: String,
    val monthData: Map<String, CategoryMetricData>
)

data class CategoryMetricData(
    val amount: Double,
    val momChange: Double,
    val momChangePct: Double,
    val yoyAmount: Double,
    val yoyChange: Double
)
