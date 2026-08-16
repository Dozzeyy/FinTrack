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
        isMerchantTrackerEnabled: Boolean = false,
        incomeAtMonthEnd: Boolean = false
    ): List<FinancialInsight> {
        val insights = mutableListOf<FinancialInsight>()
        val today = LocalDate.now()
        val currentMonthStr = today.toString().substring(0, 7)
        val prevMonth = today.minusMonths(1)
        val prevMonthStr = prevMonth.toString().substring(0, 7)
        val monthToUseForIncomeMetrics = if (incomeAtMonthEnd) prevMonthStr else currentMonthStr

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
            if (minor?.majorHeadId == 8) { 
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
        val latestIncome = monthlyIncomes[monthToUseForIncomeMetrics] ?: 0.0
        val last3MonthsIncomes = monthlyIncomes.filterKeys { it != monthToUseForIncomeMetrics }.values.toList().takeLast(3)
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
            val onAccountLoanMajorId = majorHeads.find { it.name.contains("On Account (Loan)", true) }?.id
            val onAccountLoanMinorIds = minorHeads.filter { it.majorHeadId == onAccountLoanMajorId }.map { it.id }.toSet()
            val onAccountLoanAccountIds = accounts.filter { it.minorHeadId in onAccountLoanMinorIds }.map { it.id }.toSet()

            val vendorGroups = currentMonthTxns.mapNotNull { t ->
                val merchant = t.transaction.merchantName
                if (!merchant.isNullOrBlank()) {
                    merchant to t.transaction.amount
                } else if (t.transaction.partyId != null && (t.transaction.accountId in onAccountLoanAccountIds || t.transaction.toAccountId in onAccountLoanAccountIds)) {
                    (t.partyName ?: "Party ${t.transaction.partyId}") to t.transaction.amount
                } else {
                    null
                }
            }.groupBy({ it.first }, { it.second })
            
            vendorGroups.forEach { (vendor, amounts) ->
                val vendorTotal = amounts.sum()
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

        val ccMajorId = majorHeads.find { it.name.contains("Credit Card", true) }?.id
        val ccLiabilities = abs(accounts.filter { a -> 
            val minor = minorHeads.find { it.id == a.minorHeadId }
            minor?.majorHeadId == ccMajorId
        }.sumOf { it.balance })
        val trueBankBalance = bankBalance - ccLiabilities

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
            val latestKey = if (incomeAtMonthEnd) {
                if (incomesSorted.last() == currentMonthStr && incomesSorted.size >= 2) incomesSorted[incomesSorted.size - 2]
                else incomesSorted.last()
            } else incomesSorted.last()
            
            val rollingAvg = monthlyIncomes.filter { it.key != latestKey }.values.average()
            val latest = monthlyIncomes[latestKey] ?: 0.0
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

        // 14. Price Increase Detection (Revamped: 3-month average vs current month total)
        val last3Months = (1..3).map { today.minusMonths(it.toLong()).toString().substring(0, 7) }
        val categoryMonthlyTotals = transactions.filter { it.categoryType == "expense" }
            .groupBy { (it.categoryName ?: "Uncategorized") to it.transaction.date.substring(0, 7) }
            .mapValues { it.value.sumOf { t -> t.transaction.amount } }

        val allCategoriesWithExpenses = transactions.filter { it.categoryType == "expense" }.map { it.categoryName ?: "Uncategorized" }.distinct()

        allCategoriesWithExpenses.forEach { cat ->
            val currentTotal = categoryMonthlyTotals[cat to currentMonthStr] ?: 0.0
            val historyTotals = last3Months.map { categoryMonthlyTotals[cat to it] ?: 0.0 }.filter { it > 0 }
            
            if (historyTotals.isNotEmpty() && currentTotal > 100) {
                val avgHistory = historyTotals.average()
                if (currentTotal > avgHistory * 1.15) {
                    val pct = ((currentTotal / avgHistory - 1) * 100).toInt()
                    insights.add(FinancialInsight("price_hike_$cat", "Price Hike Detected", "Your total spending in '$cat' this month is $pct% higher than your average of last 3 months.", InsightType.ANOMALY))
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

        // 17. Idle Cash Investment Alert (True Bank bal > 25% of 3mo avg expense)
        val last3MonthExpsList = monthlyExpenses.values.toList().takeLast(3)
        val avg3MonthExp = if (last3MonthExpsList.isNotEmpty()) last3MonthExpsList.average() else 0.0
        
        if (avg3MonthExp > 0 && trueBankBalance > avg3MonthExp * 0.25) {
             insights.add(FinancialInsight("idle_cash", "Investment Opportunity", "Your net bank balance (after credit card liabilities) of ${trueBankBalance} exceeds 25% of your 3-month average expenses. Consider investing the surplus to earn better returns.", InsightType.OPPORTUNITY))
        }

        // 18. Emergency Fund Adequacy Score
        val emergencyFunds = accounts.filter { it.isEmergencyFund }.sumOf { it.balance }
        
        val last3MonthsList = (1..3).map { today.minusMonths(it.toLong()).toString().substring(0, 7) }
        val eligibleMonthsExpenses = last3MonthsList.mapNotNull { monthStr ->
            val monthTxns = transactions.filter { it.transaction.date.startsWith(monthStr) }
            val expenses = monthTxns.filter { it.categoryType == "expense" }.sumOf { it.transaction.amount }
            
            if (expenses > 0) {
                val days = monthTxns.mapNotNull { 
                    try { LocalDate.parse(it.transaction.date).dayOfMonth } catch(e: Exception) { null }
                }
                if (days.isNotEmpty() && days.min() <= 5 && days.max() >= 26) {
                    expenses
                } else null
            } else null
        }

        val avgMonthlyExpEF = if (eligibleMonthsExpenses.isNotEmpty()) eligibleMonthsExpenses.average() else avgMonthlyExp
        
        if (emergencyFunds > 0 && avgMonthlyExpEF > 0) {
            val monthsCovered = emergencyFunds / avgMonthlyExpEF
            val scoreText = String.format(java.util.Locale.US, "%.1f", monthsCovered)
            val avgExpStr = String.format(java.util.Locale.US, "%,.0f", avgMonthlyExpEF)
            val fundStr = String.format(java.util.Locale.US, "%,.0f", emergencyFunds)
            
            insights.add(FinancialInsight(
                "ef_adequacy",
                "Emergency Fund Adequacy",
                "Your emergency fund covers $scoreText months of expenses (Avg exp: $avgExpStr, Fund: $fundStr). Recommended: 6 months.",
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

        // 19. Negative Net Worth Trend
        val netWorthHistory = mutableListOf<Double>()
        val months = (0..3).map { today.minusMonths(it.toLong()).toString().substring(0, 7) }.reversed()
        
        months.forEach { month ->
            val endOfMonth = LocalDate.parse("$month-01").plusMonths(1).minusDays(1).toString()
            val assets = accounts.filter { it.type == "asset" }.sumOf { acc ->
                val currentBal = acc.balance
                val txnsAfter = transactions.filter { it.transaction.date > endOfMonth && (it.transaction.accountId == acc.id || it.transaction.toAccountId == acc.id) }
                var historicalBal = currentBal
                txnsAfter.forEach { t ->
                    if (t.transaction.toAccountId == acc.id) historicalBal -= t.transaction.amount
                    if (t.transaction.accountId == acc.id) historicalBal += t.transaction.amount
                }
                historicalBal
            }
            val liabilities = accounts.filter { it.type == "liability" }.sumOf { acc ->
                val currentBal = acc.balance
                val txnsAfter = transactions.filter { it.transaction.date > endOfMonth && (it.transaction.accountId == acc.id || it.transaction.toAccountId == acc.id) }
                var historicalBal = currentBal
                txnsAfter.forEach { t ->
                    if (t.transaction.toAccountId == acc.id) historicalBal -= t.transaction.amount
                    if (t.transaction.accountId == acc.id) historicalBal += t.transaction.amount
                }
                historicalBal
            }
            netWorthHistory.add(assets - liabilities)
        }

        if (netWorthHistory.size >= 4) {
            val isDeclining = (1..3).all { netWorthHistory[it] < netWorthHistory[it - 1] }
            val totalCashFlowLast3Months = months.takeLast(3).sumOf { m -> (monthlyIncomes[m] ?: 0.0) - (monthlyExpenses[m] ?: 0.0) }
            
            if (isDeclining && totalCashFlowLast3Months > 0) {
                insights.add(FinancialInsight(
                    "net_worth_decline",
                    "Negative Net Worth Trend",
                    "Your net worth has declined consistently for 3 months despite positive cash flow. This signals hidden leaks or asset depreciation.",
                    InsightType.WARNING
                ))
            }
        }

        // 20. Category Overspend Pattern
        budgets.filter { it.categoryType == "expense" && it.budgetAmount > 0 }.forEach { b ->
            val last3MonthsList = (1..3).map { today.minusMonths(it.toLong()).toString().substring(0, 7) }
            val catIds = b.categoryIds
            val overspendCount = last3MonthsList.count { month ->
                val monthTotal = transactions.filter { it.transaction.date.startsWith(month) && it.transaction.categoryId in catIds }.sumOf { it.transaction.amount }
                monthTotal > b.budgetAmount
            }
            if (overspendCount >= 3) {
                insights.add(FinancialInsight(
                    "overspend_pattern_${b.categoryName}",
                    "Systemic Overspending",
                    "You've exceeded your '${b.categoryName}' budget for 3 consecutive months. Consider adjusting your habits or the budget limit.",
                    InsightType.WARNING
                ))
            }
        }

        // 21. Savings Rate Trend
        if (monthlyIncomes.size >= 4) {
            val monthToEvaluate = monthToUseForIncomeMetrics
            val incomesSorted = monthlyIncomes.keys.sorted()
            
            val savingsRates = incomesSorted.map { m ->
                val inc = monthlyIncomes[m] ?: 0.0
                val exp = monthlyExpenses[m] ?: 0.0
                if (inc > 0) (inc - exp) / inc else 0.0
            }
            
            val targetIndex = incomesSorted.indexOf(monthToEvaluate)
            if (targetIndex >= 3) {
                val currentRate = savingsRates[targetIndex]
                val avg3MonthRate = savingsRates.subList(targetIndex - 3, targetIndex).average()
                
                if (currentRate < avg3MonthRate * 0.8 && avg3MonthRate > 0) {
                    insights.add(FinancialInsight(
                        "savings_rate_drop",
                        "Savings Rate Alert",
                        "Your savings rate in $monthToEvaluate (${(currentRate * 100).toInt()}%) is significantly lower than your 3-month average (${(avg3MonthRate * 100).toInt()}%).",
                        InsightType.TREND
                    ))
                }
            }
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
