/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 Bhuvan
 */

package com.openapps.fintrack.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.pow

object LoanCalculator {

    /**
     * Calculates simple interest for the gap between disbursement and first repayment (Actual/365).
     */
    fun calculateGapInterestDays(
        principal: Double,
        annualRate: Double,
        disbursementDate: LocalDate,
        firstRepaymentDate: LocalDate
    ): Double {
        val days = ChronoUnit.DAYS.between(disbursementDate, firstRepaymentDate)
        if (days <= 0) return 0.0
        return principal * (annualRate / 100.0) * (days / 365.0)
    }

    /**
     * Calculates gap interest using Bank Convention: (Full Months) + (Odd Days with 30 divisor).
     */
    fun calculateGapInterestBankConvention(
        principal: Double,
        monthlyRatePercent: Double,
        disbursementDate: LocalDate,
        firstRepaymentDate: LocalDate
    ): Double {
        if (firstRepaymentDate.isBefore(disbursementDate)) return 0.0
        
        val totalMonths = ChronoUnit.MONTHS.between(disbursementDate, firstRepaymentDate)
        val jumpedBackDate = firstRepaymentDate.minusMonths(totalMonths)
        val oddDays = ChronoUnit.DAYS.between(disbursementDate, jumpedBackDate)
        
        val fullMonthInterest = principal * (monthlyRatePercent / 100.0) * totalMonths
        val oddDaysInterest = principal * ((monthlyRatePercent / 100.0) / 30.0) * oddDays
        
        return fullMonthInterest + oddDaysInterest
    }

    /**
     * Calculates the standard periodic installment (EMI) using the "Annuity Due" approach at the start of repayment.
     * This ensures the balance reaches exactly zero when the first payment covers a disbursement gap interest.
     * @param gapInterest One-time interest fee for the period before the first repayment.
     */
    fun calculateStandardEMI(
        principal: Double,
        periodicRatePercent: Double,
        periods: Int,
        gapInterest: Double = 0.0
    ): Double {
        if (periods <= 0) return 0.0
        val r = periodicRatePercent / 100.0
        
        // At the time of first repayment (T1), the total debt is Principal + GapInterest.
        // There are 'n' payments starting immediately at T1 (Annuity Due).
        // PV_due = EMI * ((1+r)^n - 1) / (r * (1+r)^(n-1))
        
        if (r <= 0.0) return (principal + gapInterest) / periods
        
        val factor = ((1 + r).pow(periods) - 1) / (r * (1 + r).pow(periods - 1))
        return (principal + gapInterest) / factor
    }

    /**
     * Finds the periodic interest rate (in percent) that results in the target installment.
     * Considers the gap interest which itself depends on the interest rate.
     */
    fun calculatePeriodicRateFromEMI(
        principal: Double,
        emi: Double,
        periods: Int,
        disbursementDate: LocalDate,
        firstRepaymentDate: LocalDate,
        gapMethod: String,
        frequency: String
    ): Double {
        if (principal <= 0 || periods <= 0 || emi <= principal / periods) return 0.0
        
        var low = 0.0
        var high = 1.0 // 100% per period
        val tolerance = 1e-9
        
        for (i in 0 until 100) {
            val rMid = (low + high) / 2.0
            
            // Re-calculate gap interest for this specific rate
            val annualRate = rMid * 100.0 * getMultiplier(frequency)
            val monthlyRate = rMid * 100.0 * getMonthlyMultiplierAdjustment(frequency)
            
            val gapInt = if (gapMethod == "DAYS") {
                calculateGapInterestDays(principal, annualRate, disbursementDate, firstRepaymentDate)
            } else {
                calculateGapInterestBankConvention(principal, monthlyRate, disbursementDate, firstRepaymentDate)
            }
            
            val currentEMI = calculateStandardEMI(principal, rMid * 100.0, periods, gapInt)
            
            if (currentEMI < emi) {
                low = rMid
            } else {
                high = rMid
            }
            
            if (high - low < tolerance) break
        }
        
        return high * 100.0
    }

    private fun getMultiplier(frequency: String) = when(frequency) {
        "MONTHLY" -> 12.0
        "QUARTERLY" -> 4.0
        "HALF_YEARLY" -> 2.0
        "YEARLY" -> 1.0
        else -> 12.0
    }

    private fun getMonthlyMultiplierAdjustment(frequency: String) = when(frequency) {
        "MONTHLY" -> 1.0
        "QUARTERLY" -> 1.0 / 3.0
        "HALF_YEARLY" -> 1.0 / 6.0
        "YEARLY" -> 1.0 / 12.0
        else -> 1.0
    }

    /**
     * Calculates the scheduled outstanding balance up to asOfDate.
     * Accurately accounts for Gap Interest being paid out of the FIRST installment.
     */
    fun calculateOutstandingBalance(
        principal: Double,
        periodicRatePercent: Double,
        totalPeriods: Int,
        standardEMI: Double,
        gapInterest: Double,
        firstRepaymentDate: LocalDate,
        frequency: String,
        asOfDate: LocalDate = LocalDate.now(java.time.ZoneId.of("UTC"))
    ): Double {
        if (asOfDate.isBefore(firstRepaymentDate)) return principal
        
        val n = countPassedPeriods(firstRepaymentDate, asOfDate, frequency).coerceAtMost(totalPeriods)
        if (n <= 0) return principal

        val r = periodicRatePercent / 100.0
        var balance = principal

        for (i in 1..n) {
            // First payment covers the pre-calculated gap interest
            // Subsequent payments cover regular periodic interest on balance
            val interestPortion = if (i == 1) gapInterest else (balance * r)
            val principalPortion = standardEMI - interestPortion
            balance -= principalPortion
        }

        return balance.coerceAtLeast(0.0)
    }

    fun countPassedPeriods(startDate: LocalDate, asOfDate: LocalDate, frequency: String): Int {
        if (asOfDate.isBefore(startDate)) return 0
        
        val monthsBetween = ChronoUnit.MONTHS.between(startDate, asOfDate)
        return when (frequency) {
            "MONTHLY" -> monthsBetween.toInt() + 1
            "QUARTERLY" -> (monthsBetween / 3).toInt() + 1
            "HALF_YEARLY" -> (monthsBetween / 6).toInt() + 1
            "YEARLY" -> (monthsBetween / 12).toInt() + 1
            else -> monthsBetween.toInt() + 1
        }
    }

    fun getNextDate(currentDate: Long, frequency: String): Long {
        val date = Instant.ofEpochMilli(currentDate).atZone(ZoneId.of("UTC")).toLocalDate()
        val nextDate = when (frequency) {
            "MONTHLY" -> date.plusMonths(1)
            "QUARTERLY" -> date.plusMonths(3)
            "HALF_YEARLY" -> date.plusMonths(6)
            "YEARLY" -> date.plusYears(1)
            else -> date.plusMonths(1)
        }
        return nextDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    }

    fun calculatePaymentSplit(
        outstandingBalance: Double,
        periodicRatePercent: Double,
        installmentAmount: Double,
        gapInterest: Double = 0.0,
        isFirstPayment: Boolean = false
    ): Pair<Double, Double> {
        val interestPortion = if (isFirstPayment) gapInterest else (outstandingBalance * (periodicRatePercent / 100.0))
        val principalPortion = (installmentAmount - interestPortion).coerceAtMost(outstandingBalance)
        return Pair(principalPortion, interestPortion)
    }

    fun generateSchedule(
        principal: Double,
        periodicRatePercent: Double,
        totalPeriods: Int,
        installment: Double,
        gapInterest: Double,
        firstRepaymentDate: LocalDate,
        frequency: String
    ): List<AmortizationRow> {
        val schedule = mutableListOf<AmortizationRow>()
        var balance = principal
        val r = periodicRatePercent / 100.0
        var currentDate = firstRepaymentDate

        for (i in 1..totalPeriods) {
            val interest = if (i == 1) gapInterest else (balance * r)
            val principalPaid = (installment - interest).coerceAtMost(balance)
            balance -= principalPaid
            
            schedule.add(AmortizationRow(
                period = i,
                dueDate = currentDate,
                openingBalance = balance + principalPaid,
                installment = installment,
                interestPortion = interest,
                principalPortion = principalPaid,
                closingBalance = balance.coerceAtLeast(0.0)
            ))

            currentDate = when (frequency) {
                "MONTHLY" -> currentDate.plusMonths(1)
                "QUARTERLY" -> currentDate.plusMonths(3)
                "HALF_YEARLY" -> currentDate.plusMonths(6)
                "YEARLY" -> currentDate.plusYears(1)
                else -> currentDate.plusMonths(1)
            }
        }
        return schedule
    }
}

data class AmortizationRow(
    val period: Int,
    val dueDate: LocalDate,
    val openingBalance: Double,
    val installment: Double,
    val interestPortion: Double,
    val principalPortion: Double,
    val closingBalance: Double
)
