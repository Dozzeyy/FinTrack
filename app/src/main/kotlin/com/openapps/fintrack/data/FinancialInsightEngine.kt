/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import com.openapps.fintrack.ui.BudgetVsActual
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sqrt

data class FinancialInsight(
    val id: String,
    val title: String,
    val description: String,
    val type: InsightType
)

enum class InsightType {
    WARNING, TREND, OPPORTUNITY, ANOMALY
}

class FinancialInsightEngine {

    fun generateInsights(
        transactions: List<TransactionWithDetails>,
        accounts: List<AccountBalance>,
        budgets: List<BudgetVsActual>,
        loans: List<Loan>,
        majorHeads: List<MajorHead>,
        minorHeads: List<MinorHead>
    ): List<FinancialInsight> {
        val insights = mutableListOf<FinancialInsight>()
        val today = LocalDate.now()
        val currentMonthStr = today.toString().substring(0, 7)
        val prevMonth = today.minusMonths(1)
        val prevMonthStr = prevMonth.toString().substring(0, 7)

        val monthlyExpenses = transactions.filter { it.categoryType == "expense" }
            .groupBy { it.transaction.date.substring(0, 7) }
            .mapValues { it.value.sumOf { t -> t.transaction.amount } }
        val avgMonthlyExp = if (monthlyExpenses.isNotEmpty()) monthlyExpenses.values.average() else 0.0

        val monthlyIncomes = transactions.filter { it.categoryType == "income" }
            .groupBy { it.transaction.date.substring(0, 7) }
            .mapValues { it.value.sumOf { t -> t.transaction.amount } }

        // 1. Budget Run-rate Prediction (Forecasting ML)
        budgets.forEach { b ->
            if (b.categoryType == "expense" && b.budgetAmount > 0) {
                val daysInPeriod = getDaysInDuration(b.duration)
                val daysElapsed = getDaysElapsed(b.startDate)
                if (daysElapsed > 2 && daysElapsed < daysInPeriod) {
                    val runRate = b.actualAmount / daysElapsed
                    val projected = runRate * daysInPeriod
                    if (projected > b.budgetAmount * 1.1) {
                        insights.add(FinancialInsight(
                            "budget_${b.categoryName}",
                            "Budget Warning",
                            "At your current rate, you will exceed '${b.categoryName}' budget by ${(projected - b.budgetAmount).toInt()} units.",
                            InsightType.WARNING
                        ))
                    }
                }
            }
        }

        // 2. Spending Anomaly Detection (Statistical ML - Z-Score)
        val recentTxns = transactions.take(50).filter { it.categoryType == "expense" }
        if (recentTxns.size > 10) {
            val avg = recentTxns.map { it.transaction.amount }.average()
            val stdDev = calculateStdDev(recentTxns.map { it.transaction.amount }, avg)
            
            recentTxns.take(3).forEach { t ->
                if (t.transaction.amount > avg + (2 * stdDev) && t.transaction.amount > 100) {
                    insights.add(FinancialInsight(
                        "anomaly_${t.transaction.id}",
                        "High Spending Detected",
                        "Your transaction of ${t.transaction.amount} in '${t.categoryName}' is significantly higher than your average.",
                        InsightType.ANOMALY
                    ))
                }
            }
        }

        // 3. Debt Service Ratio
        val totalIncomePrevMonth = monthlyIncomes[prevMonthStr] ?: 0.0
        val currentMonthIncome = monthlyIncomes[currentMonthStr] ?: 0.0
        val incomeToUse = if (currentMonthIncome > 0) currentMonthIncome else totalIncomePrevMonth

        val totalEMI = loans.filter { !it.isClosed }.sumOf { it.installmentAmount }
        if (incomeToUse > 0 && (totalEMI / incomeToUse) > 0.20) {
            insights.add(FinancialInsight("debt_warning", "Debt Warning", "Your loan payments are ${(totalEMI / incomeToUse * 100).toInt()}% of your income. Consider reducing debt.", InsightType.WARNING))
        }

        // 4. Liquidity Alert
        val bankMajorId = majorHeads.find { it.name.contains("Bank", true) }?.id
        val bankBalance = accounts.filter { a -> 
            val minor = minorHeads.find { it.id == a.minorHeadId }
            minor?.majorHeadId == bankMajorId
        }.sumOf { it.balance }

        if (bankBalance < avgMonthlyExp && avgMonthlyExp > 0) {
            insights.add(FinancialInsight("low_liquidity", "Liquidity Alert", "Your net bank balance is below your average monthly expenses. Maintain a safety buffer.", InsightType.WARNING))
        }

        // 5. Investment Tracking (50% of last month's income)
        val investmentMajorId = majorHeads.find { it.name.contains("Investment", true) }?.id
        val currentMonthInvestments = transactions.filter { t ->
            t.transaction.date.startsWith(currentMonthStr) && t.transaction.categoryId == null && t.transaction.toAccountId != null &&
            minorHeads.find { it.id == accounts.find { acc -> acc.id == t.transaction.toAccountId }?.minorHeadId }?.majorHeadId == investmentMajorId
        }.sumOf { it.transaction.amount }

        if (totalIncomePrevMonth > 0 && currentMonthInvestments < (totalIncomePrevMonth * 0.5)) {
            insights.add(FinancialInsight("invest_target", "Investment Slump", "Your investments this month are less than 50% of last month's income.", InsightType.OPPORTUNITY))
        }

        // 6. Spending Pulse
        val currentMonthExp = monthlyExpenses[currentMonthStr] ?: 0.0
        if (totalIncomePrevMonth > 0 && currentMonthExp >= (totalIncomePrevMonth * 0.25)) {
            val pct = (currentMonthExp / totalIncomePrevMonth * 100).toInt()
            insights.add(FinancialInsight("exp_threshold", "Spending Pulse", "Total expenses have reached $pct% of last month's income.", InsightType.TREND))
        }

        // 7. Overdraft Warning (Historical patterns)
        val targetDay = today.plusDays(3).dayOfMonth.toString().padStart(2, '0')
        val historicalLarge = transactions.filter { it.categoryType == "expense" && it.transaction.amount > (if (recentTxns.isNotEmpty()) recentTxns.map { it.transaction.amount }.average() else 0.0) * 1.5 }
            .groupBy { it.transaction.date.split("-").last() }
        
        if (historicalLarge.containsKey(targetDay)) {
            val expectedAmt = historicalLarge[targetDay]?.first()?.transaction?.amount ?: 0.0
            val liquidMajorIds = majorHeads.filter { it.name.contains("Bank", true) || it.name.contains("Cash", true) || it.name.contains("Wallet", true) }.map { it.id }
            val liquidBal = accounts.filter { a -> 
                val minor = minorHeads.find { it.id == a.minorHeadId }
                minor?.majorHeadId in liquidMajorIds 
            }.sumOf { it.balance }
            
            if (liquidBal < expectedAmt) {
                insights.add(FinancialInsight("overdraft_risk", "Upcoming Large Expense", "A large expense is expected based on history in 3 days. Your liquid balance may be low.", InsightType.WARNING))
            }
        }

        // 8. Income Variability Warning
        if (monthlyIncomes.size >= 2) {
            val incomesSorted = monthlyIncomes.keys.sorted()
            val rollingAvg = monthlyIncomes.filter { it.key != incomesSorted.last() }.values.average()
            val latest = monthlyIncomes[incomesSorted.last()] ?: 0.0
            if (latest < rollingAvg * 0.8 && rollingAvg > 0) {
                insights.add(FinancialInsight("income_drop", "Income Variability", "Your income has dropped by more than 20% vs average. Suggest tightening discretionary spending.", InsightType.WARNING))
            }
        }

        // 9. Spending Velocity Alert (Updated: 3mo avg income, 60% spent within 10 days of highest payday)
        val last3MonthsIncomeList = monthlyIncomes.values.toList().takeLast(3)
        val avg3MonthIncome = if (last3MonthsIncomeList.isNotEmpty()) last3MonthsIncomeList.average() else 0.0
        
        val lastMonthIncomeTxns = transactions.filter { it.transaction.date.startsWith(prevMonthStr) && it.categoryType == "income" }
        val highestIncomeTxn = lastMonthIncomeTxns.maxByOrNull { it.transaction.amount }
        
        if (highestIncomeTxn != null && avg3MonthIncome > 0) {
            val highestIncomeDate = LocalDate.parse(highestIncomeTxn.transaction.date)
            val tenDaysAfter = highestIncomeDate.plusDays(10)
            
            val spendInWindow = transactions.filter { 
                it.categoryType == "expense" && 
                LocalDate.parse(it.transaction.date).let { d -> !d.isBefore(highestIncomeDate) && !d.isAfter(tenDaysAfter) }
            }.sumOf { it.transaction.amount }
            
            if (spendInWindow > avg3MonthIncome * 0.6) {
                insights.add(FinancialInsight("spending_velocity_alt", "High Spending Velocity", "You've spent over 60% of your average monthly income within 10 days of your highest payday. Consider pacing.", InsightType.TREND))
            }
        }

        // 10. Price Increase Detection (Recurring bills)
        val recurringCats = transactions.filter { it.categoryType == "expense" }
            .groupBy { it.categoryName ?: "Uncategorized" }
            .filter { it.value.size >= 3 }
        
        recurringCats.forEach { (cat, txns) ->
            val sorted = txns.sortedByDescending { it.transaction.date }
            val latestAmt = sorted.first().transaction.amount
            val history = sorted.drop(1).take(3)
            if (history.size >= 2) {
                val avgHistory = history.map { it.transaction.amount }.average()
                if (latestAmt > avgHistory * 1.15 && latestAmt > 100) {
                    val pct = ((latestAmt / avgHistory - 1) * 100).toInt()
                    insights.add(FinancialInsight("price_hike_$cat", "Price Hike Detected", "Your last '$cat' bill is $pct% higher than your recent average.", InsightType.ANOMALY))
                }
            }
        }

        // 11. Credit Card Working Capital Optimization
        val ccAccounts = accounts.filter { acc -> 
            minorHeads.find { it.id == acc.minorHeadId }?.majorHeadId == 8 
        }
        if (ccAccounts.size >= 2) {
            val suggestions = mutableListOf<String>()
            val daysInMonth = today.lengthOfMonth()
            var currentRangeStart = 1
            var lastBestCard = ""

            for (day in 1..daysInMonth) {
                val bestCard = ccAccounts.minByOrNull { acc ->
                    val cycleStart = acc.billingCycleStart?.toIntOrNull() ?: 1
                    val diff = if (day >= cycleStart) day - cycleStart else day + (30 - cycleStart)
                    diff
                }?.name ?: ""

                if (bestCard != lastBestCard) {
                    if (lastBestCard.isNotEmpty()) {
                        suggestions.add("${currentRangeStart}-${day-1}: Use $lastBestCard")
                    }
                    lastBestCard = bestCard
                    currentRangeStart = day
                }
            }
            suggestions.add("$currentRangeStart-$daysInMonth: Use $lastBestCard")
            insights.add(FinancialInsight("cc_optimize", "Optimal Card Usage", "For maximum interest-free period this month:\n" + suggestions.joinToString("\n"), InsightType.OPPORTUNITY))
        }

        // 12. Category Correlation Insight
        val foodTxns = transactions.filter { (it.categoryName?.contains("Food", true) ?: false) || (it.categoryName?.contains("Groceries", true) ?: false) }
        val workTxns = transactions.filter { it.transaction.note?.contains("work", true) == true || (it.categoryName?.contains("Travel", true) ?: false) }
        
        val foodDays = foodTxns.map { it.transaction.date }.toSet()
        val workDays = workTxns.map { it.transaction.date }.toSet()
        val commonDays = foodDays.intersect(workDays)
        
        if (commonDays.size > 5) {
            insights.add(FinancialInsight("correlation_work_food", "Spending Pattern", "You tend to spend more on outside food on days you work late or travel for work.", InsightType.TREND))
        }

        // 13. Idle Cash Investment Alert (Bank bal > 25% of 3mo avg expense)
        val last3MonthExpsList = monthlyExpenses.values.toList().takeLast(3)
        val avg3MonthExp = if (last3MonthExpsList.isNotEmpty()) last3MonthExpsList.average() else 0.0
        
        if (avg3MonthExp > 0 && bankBalance > avg3MonthExp * 0.25) {
             insights.add(FinancialInsight("idle_cash", "Investment Opportunity", "Your bank balance exceeds 25% of your 3-month average expenses. Consider investing the surplus to earn better returns.", InsightType.OPPORTUNITY))
        }

        return insights.distinctBy { it.id }.take(7)
    }

    private fun calculateStdDev(numbers: List<Double>, avg: Double): Double {
        return sqrt(numbers.map { (it - avg) * (it - avg) }.average())
    }

    private fun getDaysInDuration(duration: String): Long {
        return when (duration) {
            "Weekly" -> 7
            "Monthly" -> 30
            "Yearly" -> 365
            else -> 30
        }
    }

    private fun getDaysElapsed(startDate: String): Long {
        return try {
            val start = LocalDate.parse(startDate)
            ChronoUnit.DAYS.between(start, LocalDate.now()).coerceAtLeast(1)
        } catch (e: Exception) { 1 }
    }
}
