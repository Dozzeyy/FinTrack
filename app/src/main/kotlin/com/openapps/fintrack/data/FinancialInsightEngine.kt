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
        minorHeads: List<MinorHead>,
        isMerchantTrackerEnabled: Boolean = false
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

        // 1. Credit Score Impact Predictor
        accounts.forEach { acc ->
            val minor = minorHeads.find { it.id == acc.minorHeadId }
            if (minor?.majorHeadId == 8) { // Credit Card
                val limit = acc.creditLimit ?: 0.0
                if (limit > 0) {
                    val utilization = (abs(acc.balance) / limit) * 100
                    if (utilization > 30) {
                        insights.add(FinancialInsight(
                            "credit_util_${acc.id}",
                            "Credit Score Impact",
                            "Your credit utilization is ${utilization.toInt()}% for account - ${acc.name} which may impact your score. Keep it below 30%.",
                            InsightType.WARNING
                        ))
                    }
                }
            }
        }

        // 2. Seasonal Spending Prediction
        if (monthlyExpenses.size >= 24) {
            val targetMonth = today.plusMonths(1).monthValue
            val targetMonthName = today.plusMonths(1).month.name.lowercase().replaceFirstChar { it.uppercase() }
            
            val historicalData = monthlyExpenses.filter { 
                val d = LocalDate.parse(it.key + "-01")
                d.monthValue == targetMonth && d.year < today.year
            }.values
            
            if (historicalData.size >= 2) {
                val historicalAvg = historicalData.average()
                if (historicalAvg > avgMonthlyExp * 1.2) {
                    insights.add(FinancialInsight(
                        "seasonal_spend",
                        "Seasonal Spending Prediction",
                        "$targetMonthName typically sees ${((historicalAvg - avgMonthlyExp) / 1000).toInt()}k higher spending based on your history. Build buffer now.",
                        InsightType.TREND
                    ))
                }
            }
        }

        // 3. Windfall Allocation Recommendation
        val latestIncome = monthlyIncomes[currentMonthStr] ?: 0.0
        val last3MonthsIncomes = monthlyIncomes.values.toList().takeLast(3)
        val avg3MonthInc = if (last3MonthsIncomes.size >= 2) last3MonthsIncomes.average() else 0.0
        
        if (avg3MonthInc > 0 && latestIncome > avg3MonthInc * 1.75) {
            val windfall = latestIncome - avg3MonthInc
            insights.add(FinancialInsight(
                "windfall_alloc",
                "Windfall Recommendation",
                "You received a windfall this month. Suggested allocation: ${ (windfall * 0.5).toInt() } invest, ${ (windfall * 0.3).toInt() } debt prepayment, ${ (windfall * 0.2).toInt() } discretionary.",
                InsightType.OPPORTUNITY
            ))
        }

        // 4. Vendor Concentration Risk
        val currentMonthTxns = transactions.filter { it.transaction.date.startsWith(currentMonthStr) && it.categoryType == "expense" }
        val totalExp = currentMonthTxns.sumOf { it.transaction.amount }
        
        if (totalExp > 0) {
            val vendorGroups = if (isMerchantTrackerEnabled) {
                currentMonthTxns.groupBy { it.transaction.merchantName ?: it.partyName ?: "Others" }
            } else {
                currentMonthTxns.groupBy { it.partyName ?: "Others" }
            }
            
            vendorGroups.forEach { (vendor, txns) ->
                if (vendor != "Others") {
                    val vendorTotal = txns.sumOf { it.transaction.amount }
                    val concentration = (vendorTotal / totalExp) * 100
                    if (concentration > 30) {
                        insights.add(FinancialInsight(
                            "vendor_risk_$vendor",
                            "Vendor Concentration Risk",
                            "${concentration.toInt()}% of your spending is with $vendor. Consider diversifying vendors to reduce dependency.",
                            InsightType.WARNING
                        ))
                    }
                }
            }
        }

        // 5. Budget Run-rate Prediction (Forecasting ML)
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

        // 6. Spending Anomaly Detection (Statistical ML - Z-Score)
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

        // 7. Loan Health Analysis
        val totalIncomePrevMonth = monthlyIncomes[prevMonthStr] ?: 0.0
        val currentMonthIncome = monthlyIncomes[currentMonthStr] ?: 0.0
        
        val incomeToUse = if (totalIncomePrevMonth > 0.0) totalIncomePrevMonth else currentMonthIncome
        
        var totalLoanPaymentsMonth = 0.0
        loans.filter { !it.isClosed }.forEach { l ->
            val progress = (l.totalPrincipalRepaid / l.principalAmount) * 100
            if (progress > 90) {
                insights.add(FinancialInsight("loan_milestone_${l.id}", "Loan Milestone", "You have repaid ${progress.toInt()}% of '${l.name}'. Almost there!", InsightType.OPPORTUNITY))
            }
            totalLoanPaymentsMonth += l.installmentAmount 
        }

        if (incomeToUse > 0.0 && (totalLoanPaymentsMonth / incomeToUse) > 0.20) {
            insights.add(FinancialInsight("debt_ratio", "Debt Warning", "Your debt repayments are ${(totalLoanPaymentsMonth / incomeToUse * 100).toInt()}% of your income. Consider reducing debt.", InsightType.WARNING))
        }

        // 8. Bank Balance vs Monthly Expenses
        val bankMajorId = majorHeads.find { it.name.contains("Bank", true) }?.id
        val bankBalance = accounts.filter { a -> 
            val minor = minorHeads.find { it.id == a.minorHeadId }
            minor?.majorHeadId == bankMajorId
        }.sumOf { it.balance }

        if (bankBalance < avgMonthlyExp && avgMonthlyExp > 0) {
            insights.add(FinancialInsight("low_bank_bal", "Liquidity Alert", "Your net bank balance is below your average monthly expenses. Maintain a safety buffer.", InsightType.WARNING))
        }

        // 9. Investment Target (50% of previous month income)
        val investmentMajorId = majorHeads.find { it.name.contains("Investment", true) }?.id
        val monthInvestments = transactions.filter { t ->
            t.transaction.date.startsWith(currentMonthStr) && t.transaction.categoryId == null && t.transaction.toAccountId != null &&
            minorHeads.find { it.id == accounts.find { acc -> acc.id == t.transaction.toAccountId }?.minorHeadId }?.majorHeadId == investmentMajorId
        }.sumOf { it.transaction.amount }

        if (totalIncomePrevMonth > 0 && monthInvestments < (totalIncomePrevMonth * 0.5)) {
            insights.add(FinancialInsight("invest_target", "Investment Slump", "Your investments this month are less than 50% of last month's income.", InsightType.OPPORTUNITY))
        }

        // 10. Expense Progress Alert (25% of last month income)
        val currentMonthExp = monthlyExpenses[currentMonthStr] ?: 0.0
        if (totalIncomePrevMonth > 0 && currentMonthExp >= (totalIncomePrevMonth * 0.25)) {
            val pct = (currentMonthExp / totalIncomePrevMonth * 100).toInt()
            insights.add(FinancialInsight("exp_threshold", "Spending Pulse", "Total expenses have reached $pct% of last month's income.", InsightType.TREND))
        }

        // 11. Upcoming Large Expenses / Overdraft Warning
        val historicalLarge = transactions.filter { it.categoryType == "expense" && it.transaction.amount > (if (recentTxns.isNotEmpty()) recentTxns.map { it.transaction.amount }.average() else 0.0) * 1.5 }
            .groupBy { it.transaction.date.substring(8) }
        
        val targetDayLabel = today.plusDays(3).dayOfMonth.toString().padStart(2, '0')
        if (historicalLarge.containsKey(targetDayLabel)) {
            val expectedAmt = historicalLarge[targetDayLabel]?.first()?.transaction?.amount ?: 0.0
            val savingsMajorId = majorHeads.find { it.name.contains("Savings", true) }?.id
            val savingsBal = accounts.filter { a -> minorHeads.find { it.id == a.minorHeadId }?.majorHeadId == savingsMajorId }.sumOf { it.balance }
            
            if (savingsBal < expectedAmt) {
                insights.add(FinancialInsight("overdraft_risk", "Upcoming Large Expense", "Based on history, a large expense is expected in 3 days. Your savings balance may be low.", InsightType.WARNING))
            }
        }

        // 12. Income Variability
        if (monthlyIncomes.size >= 2) {
            val incomesSorted = monthlyIncomes.keys.sorted()
            val rollingAvg = monthlyIncomes.filter { it.key != incomesSorted.last() }.values.average()
            val latest = monthlyIncomes[incomesSorted.last()] ?: 0.0
            if (latest < rollingAvg * 0.8 && rollingAvg > 0) {
                insights.add(FinancialInsight("income_drop", "Income Variability", "Your income has dropped by more than 20% vs average. Tighten discretionary spending.", InsightType.WARNING))
            }
        }

        // 13. Spending Velocity Alert (Updated: 3mo avg income, 60% spent within 10 days of highest payday)
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

        // 14. Price Increase Detection (Recurring bills)
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

        // 15. Credit Card Working Capital Optimization
        val ccAccounts = accounts.filter { acc -> 
            minorHeads.find { it.id == acc.minorHeadId }?.majorHeadId == 8 
        }
        if (ccAccounts.size >= 2) {
            val suggestions = mutableListOf<String>()
            val daysInMonth = today.lengthOfMonth()
            var currentRangeStart = 1
            var lastBestCard = ""

            for (day in 1..daysInMonth) {
                val bestCard = ccAccounts.minByOrNull { acc: AccountBalance ->
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

        // 16. Category Correlation Insight
        val foodTxns = transactions.filter { (it.categoryName?.contains("Food", true) ?: false) || (it.categoryName?.contains("Groceries", true) ?: false) }
        val workTxns = transactions.filter { it.transaction.note?.contains("work", true) == true || (it.categoryName?.contains("Travel", true) ?: false) }
        
        val foodDays = foodTxns.map { it.transaction.date }.toSet()
        val workDays = workTxns.map { it.transaction.date }.toSet()
        val commonDays = foodDays.intersect(workDays)
        
        if (commonDays.size > 5) {
            insights.add(FinancialInsight("correlation_work_food", "Spending Pattern", "You tend to spend more on outside food on days you work late or travel for work.", InsightType.TREND))
        }

        // 17. Idle Cash Investment Alert (Bank bal > 25% of 3mo avg expense)
        val last3MonthExpsList = monthlyExpenses.values.toList().takeLast(3)
        val avg3MonthExp = if (last3MonthExpsList.isNotEmpty()) last3MonthExpsList.average() else 0.0
        
        if (avg3MonthExp > 0 && bankBalance > avg3MonthExp * 0.9) {
             insights.add(FinancialInsight("idle_cash", "Investment Opportunity", "Your bank balance exceeds 25% of your 3-month average expenses. Consider investing the surplus to earn better returns.", InsightType.OPPORTUNITY))
        }

        // 18. Emergency Fund Adequacy Score
        val emergencyFunds = accounts.filter { it.isEmergencyFund }.sumOf { it.balance }
        val avgMonthlyExpEF = if (last3MonthExpsList.size >= 2) last3MonthExpsList.average() else avgMonthlyExp
        
        if (emergencyFunds > 0 && avgMonthlyExpEF > 0) {
            val monthsCovered = emergencyFunds / avgMonthlyExpEF
            val scoreText = String.format(java.util.Locale.US, "%.1f", monthsCovered)
            insights.add(FinancialInsight(
                "ef_adequacy",
                "Emergency Fund Adequacy",
                "Your emergency fund covers $scoreText months of expenses. Recommended: 6 months.",
                if (monthsCovered < 3) InsightType.WARNING else if (monthsCovered < 6) InsightType.TREND else InsightType.OPPORTUNITY
            ))
        } else if (emergencyFunds <= 0 && avgMonthlyExpEF > 0) {
            insights.add(FinancialInsight(
                "ef_missing",
                "Emergency Fund Alert",
                "You haven't designated any accounts as an Emergency Fund. It's recommended to have 6 months of expenses saved.",
                InsightType.WARNING
            ))
        }

        return insights.distinctBy { it.id }.take(15)
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
